package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnResume
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** Main-dispatcher confined component. A retained holder owns one manual request across rotation. */
class DefaultArenaLessonCatalogComponent(
    componentContext: ComponentContext,
    repository: ArenaCatalogRepository,
    sessions: ArenaSessionProvider,
    authRepository: AuthRepository,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, ArenaLessonCatalogComponent {
    private val holder =
        instanceKeeper.getOrCreate("arena_lesson_catalog") {
            ArenaCatalogStateHolder(repository, sessions, authRepository, mainContext)
        }

    override val state = holder.state

    init {
        holder.recheckSession()
        lifecycle.doOnResume { holder.recheckSession() }
    }

    override fun onRefresh() = holder.load(append = false)

    override fun onLoadMore() = holder.load(append = true)
}

/**
 * Only an in-memory read snapshot survives rotation. Process restore serializes the route, not
 * cards, cursors or this stamp. The source has no epoch Flow: recheck on uid events, resume,
 * before each action and around each awaited response; do not claim idle A -> B -> A detection.
 */
private class ArenaCatalogStateHolder(
    private val repository: ArenaCatalogRepository,
    private val sessions: ArenaSessionProvider,
    authRepository: AuthRepository,
    dispatcher: CoroutineDispatcher,
) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var session = sessions.current()
    private var tail: ArenaLessonCatalogPage? = null
    private var active: CatalogLoad? = null
    private var destroyed = false
    private val mutableState = MutableStateFlow(initialState(session))
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            authRepository.observeUid()
                .catch { error ->
                    if (error is CancellationException) throw error
                    reset(null, ArenaCatalogNotice.ACCOUNT_UNAVAILABLE)
                }
                .collect { recheckSession() }
        }
    }

    fun recheckSession(): ArenaSessionStamp? {
        val actual = if (destroyed) null else sessions.current()
        if (actual != session) {
            reset(
                actual,
                if (actual == null) ArenaCatalogNotice.ACCOUNT_UNAVAILABLE else ArenaCatalogNotice.ACCOUNT_CHANGED,
            )
        }
        return actual
    }

    fun load(append: Boolean) {
        val stamp = recheckSession()
        val cursor = if (append) tail?.nextCursor else null
        if (destroyed || stamp == null || active != null) return
        if (append && cursor == null) return
        val operation = CatalogLoad(stamp, ArenaLessonCatalogRequest(stamp.ownerUid, cursor = cursor), append)
        // Install ownership before launch, including for an immediate or undispatched test dispatcher.
        active = operation
        mutableState.value = mutableState.value.copy(isLoading = true, notice = null)
        // Register cancellation before any immediate-dispatch execution or reentrant observer.
        val job = scope.launch(start = CoroutineStart.LAZY) { execute(operation) }
        operation.job = job
        if (active === operation) job.start() else job.cancel()
    }

    private suspend fun execute(operation: CatalogLoad) {
        try {
            coroutineContext.ensureActive()
            if (owns(operation)) {
                val result = repository.list(operation.request)
                coroutineContext.ensureActive()
                if (owns(operation)) {
                    result.fold(
                        onSuccess = { accept(operation, it) },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            refuse(error)
                        },
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            // Repository boundary only; unexpected private text never becomes display text.
            if (owns(operation)) refuse(error)
        } finally {
            // A cancelled old request must not clear a replacement request's busy flag.
            if (active === operation) {
                active = null
                val current = mutableState.value
                val idleNotice = if (current.totalLessons == null) ArenaCatalogNotice.NOT_LOADED else null
                mutableState.value =
                    current.copy(
                        isLoading = false,
                        notice = current.notice ?: idleNotice,
                    )
            }
        }
    }

    private fun owns(operation: CatalogLoad): Boolean =
        !destroyed && recheckSession() == operation.session && active === operation

    private fun accept(
        operation: CatalogLoad,
        page: ArenaLessonCatalogPage,
    ) {
        if (page.ownerUid != operation.session.ownerUid ||
            (operation.append && page.snapshotFingerprint != tail?.snapshotFingerprint)
        ) {
            clearSnapshot(ArenaCatalogNotice.INVALID_CATALOG)
        } else {
            val lessons = if (operation.append) mutableState.value.lessons + page.lessons else page.lessons.toList()
            tail = page
            mutableState.value =
                ArenaLessonCatalogUiState(
                    lessons = lessons,
                    totalLessons = page.totalEligibleLessons,
                    isLoading = true,
                    canLoadMore = page.nextCursor != null,
                    sessionAvailable = true,
                    notice = null,
                )
        }
    }

    private fun refuse(error: Throwable) {
        when ((error as? ArenaLessonCatalogFailure)?.kind) {
            ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE ->
                clearSnapshot(ArenaCatalogNotice.ACCOUNT_UNAVAILABLE)
            ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED ->
                clearSnapshot(ArenaCatalogNotice.ACCOUNT_CHANGED)
            ArenaLessonCatalogFailureKind.CATALOG_CHANGED ->
                clearSnapshot(ArenaCatalogNotice.CATALOG_CHANGED)
            ArenaLessonCatalogFailureKind.CATALOG_TOO_LARGE ->
                clearSnapshot(ArenaCatalogNotice.TOO_LARGE)
            ArenaLessonCatalogFailureKind.INVALID_REQUEST,
            ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE,
            ArenaLessonCatalogFailureKind.CATALOG_INVALID,
            -> clearSnapshot(ArenaCatalogNotice.INVALID_CATALOG)
            ArenaLessonCatalogFailureKind.SERVER_FAILURE,
            null,
            -> mutableState.value = mutableState.value.copy(notice = ArenaCatalogNotice.UNAVAILABLE)
        }
    }

    private fun clearSnapshot(notice: ArenaCatalogNotice) {
        tail = null
        mutableState.value = initialState(session).copy(isLoading = active != null, notice = notice)
    }

    private fun reset(
        actual: ArenaSessionStamp?,
        notice: ArenaCatalogNotice,
    ) {
        val old = active
        active = null
        session = actual
        tail = null
        mutableState.value = initialState(actual).copy(notice = notice)
        old?.job?.cancel()
    }

    override fun onDestroy() {
        destroyed = true
        reset(null, ArenaCatalogNotice.ACCOUNT_UNAVAILABLE)
        scope.cancel()
    }

    private fun initialState(stamp: ArenaSessionStamp?) =
        ArenaLessonCatalogUiState(
            sessionAvailable = stamp != null,
            notice = if (stamp == null) ArenaCatalogNotice.ACCOUNT_UNAVAILABLE else ArenaCatalogNotice.NOT_LOADED,
        )
}

private class CatalogLoad(
    val session: ArenaSessionStamp,
    val request: ArenaLessonCatalogRequest,
    val append: Boolean,
) {
    var job: Job? = null
}
