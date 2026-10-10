package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import androidx.annotation.StringRes
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.CuratorUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListCuratorLifecycleUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonHistoryUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.TransitionLessonUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние куратора живёт сервером (TZ06B/AD-25): экран перечитывает view, а не кэширует их.
 *
 * Ключ мутации перехода рождается с намерением куратора (AD-2): пока исход не получен, повтор
 * кнопки едет под тем же ключом; решённый переход (квитанция APPLIED/REJECTED) закрывает намерение,
 * и следующая попытка — новое намерение с новым ключом.
 */
@Suppress("LongParameterList")
class DefaultCuratorComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
    private val listCurator: ListCuratorLifecycleUseCase,
    private val listHistory: ListLessonHistoryUseCase,
    private val transitionLesson: TransitionLessonUseCase,
    private val idProvider: QuestAuthoringIdProvider,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : CuratorComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val _state = MutableStateFlow(CuratorUiState())
    override val state: StateFlow<CuratorUiState> = _state.asStateFlow()

    /** Открытое намерение: цель перехода и её ключ. Закрывается квитанцией, не транспортным исходом. */
    private var pendingTransition: PendingTransition? = null

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        observeAuth()
    }

    override fun onRefreshClick() {
        reload()
    }

    override fun onLoadMoreClick() {
        val after = _state.value.entries.lastOrNull()?.lessonId ?: return
        if (_state.value.isLoadingMore) return
        loadPage(after, isMore = true)
    }

    override fun onLessonSelected(lessonId: String) {
        _state.update { it.copy(selectedLessonId = lessonId, historyIsLoading = true) }
        loadHistory(lessonId)
    }

    override fun onReasonChanged(value: String) {
        _state.update { it.copy(reasonDraft = value) }
    }

    override fun onTransitionClick(target: LessonShelf) {
        val snapshot = _state.value
        val uid = currentUid ?: return
        val lessonId = snapshot.selectedLessonId ?: return
        if (snapshot.isTransitioning) return
        val reason = snapshot.reasonDraft.trim()
        if (reason.isEmpty()) {
            _state.update { it.copy(errorMessage = UiMessage.Res(R.string.qa_curator_reason_required)) }
            return
        }
        val pending = pendingTransition?.takeIf { it.target == target } ?: PendingTransition(
            target = target,
            mutationId = idProvider.nextId(MUTATION_PREFIX),
        )
        pendingTransition = pending
        _state.update { it.copy(isTransitioning = true, lastDecision = null, errorMessage = null) }
        scope.launch {
            val result =
                transitionLesson(
                    uid,
                    LessonTransitionCommand(lessonId, target, reason),
                    pending.mutationId,
                )
            pendingTransition = null
            _state.update {
                it.copy(isTransitioning = false)
            }
            result.fold(
                onSuccess = { decision ->
                    _state.update { state ->
                        state.copy(
                            lastDecision = decision,
                            reasonDraft = "",
                            errorMessage = null,
                        )
                    }
                    // Полка могла измениться — перечитываем и список, и историю выбранного урока.
                    reload()
                    loadHistory(lessonId)
                },
                onFailure = { error ->
                    _state.update { state ->
                        state.copy(
                            lastDecision = null,
                            errorMessage = messageOf(error),
                        )
                    }
                },
            )
        }
    }

    private var currentUid: String? = null

    private fun observeAuth() {
        scope.launch {
                authRepository.observeUid().collect { uid ->
                    currentUid = uid
                    if (uid.isNullOrBlank()) {
                        _state.update {
                            CuratorUiState(
                                isLoading = false,
                                permissionDenied = false,
                                errorMessage = UiMessage.Res(R.string.qa_error_auth_not_ready),
                            )
                        }
                    } else {
                        reload()
                    }
                }
            }
    }

    private fun reload() {
        if (currentUid.isNullOrBlank()) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        loadPage(after = null, isMore = false)
    }

    private fun loadPage(
        after: String?,
        isMore: Boolean,
    ) {
        val uid = currentUid ?: return
        scope.launch {
            _state.update { it.copy(isLoadingMore = isMore) }
            val result = listCurator(uid, after, ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE)
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            permissionDenied = false,
                            errorMessage = null,
                            entries = if (isMore) state.entries + page.entries else page.entries,
                            canLoadMore = page.hasNext,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            permissionDenied = isPermissionDenied(error),
                            errorMessage = if (isPermissionDenied(error)) null else messageOf(error),
                            canLoadMore = false,
                        )
                    },
                )
            }
        }
    }

    private fun loadHistory(lessonId: String) {
        val uid = currentUid ?: return
        scope.launch {
            val result = listHistory(uid, lessonId, null, ListCuratorLifecycleUseCase.DEFAULT_PAGE_SIZE)
            _state.update { state ->
                if (state.selectedLessonId != lessonId) return@update state
                result.fold(
                    onSuccess = { page ->
                        state.copy(historyIsLoading = false, history = page.entries)
                    },
                    onFailure = { error ->
                        state.copy(historyIsLoading = false, errorMessage = messageOf(error))
                    },
                )
            }
        }
    }

    private fun isPermissionDenied(error: Throwable): Boolean = error.syncErrorOrNull() is SyncError.Refused

    private fun messageOf(error: Throwable): UiMessage =
        error.syncErrorOrNull()?.let { UiMessage.Raw(it.toString()) }
            ?: UiMessage.Res(R.string.qa_lifecycle_error_unknown)

    private data class PendingTransition(
        val target: LessonShelf,
        val mutationId: String,
    )
}

/** Подпись кнопки перехода по целевой полке. */
@StringRes
internal fun LessonShelf.transitionLabelRes(): Int =
    when (this) {
        LessonShelf.REVIEW -> R.string.qa_curator_transition_review
        LessonShelf.ARENA -> R.string.qa_curator_transition_arena
        LessonShelf.TOURNAMENT -> R.string.qa_curator_transition_tournament
        LessonShelf.HOME -> R.string.qa_curator_transition_home
        LessonShelf.RETIRED -> R.string.qa_curator_transition_retired
        LessonShelf.REJECTED -> R.string.qa_curator_transition_rejected
    }

private const val MUTATION_PREFIX = "transition"
