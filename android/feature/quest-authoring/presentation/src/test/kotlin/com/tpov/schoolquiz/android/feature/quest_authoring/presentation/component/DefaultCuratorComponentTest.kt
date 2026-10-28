package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.R
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.TransitionConfirmation
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.UiMessage
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleRating
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListCuratorLifecycleUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListLessonHistoryUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.TransitionLessonUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Поверхность куратора: чтение отказывает честно (permission denied — отдельное состояние),
 * переход ждёт причину и держит один ключ мутации до квитанции (AD-2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCuratorComponentTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `curator page loads entries and history follows selection`() = runTest(testScheduler) {
        val history =
            listOf(
                LessonHistoryEntry(LessonShelf.REVIEW, LessonShelf.ARENA, LessonHistoryStatus.APPLIED, null, 1L),
            )
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")), history = history)
        val component = buildComponent(fakes)

        assertEquals(LessonLifecycleState.REVIEW, component.state.value.entries.single().state)

        component.onLessonSelected("lesson-1")

        assertEquals("lesson-1", component.state.value.selectedLessonId)
        assertEquals(history, component.state.value.history)
        assertTrue(fakes.historyCalls.single().second == "lesson-1")
    }

    @Test
    fun `permission refused becomes a dedicated state, not an error banner`() = runTest(testScheduler) {
        val fakes =
            FakeUseCases(
                curatorResult = Result.failure(SyncFailure(SyncError.Refused("permission-denied"))),
            )
        val component = buildComponent(fakes)

        assertTrue(component.state.value.permissionDenied)
        assertNull(component.state.value.errorMessage)
    }

    @Test
    fun `transition without a reason is refused locally`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)
        openMenuAndRequest(component, "lesson-1", LessonShelf.ARENA)

        component.onTransitionClick(LessonShelf.ARENA)

        assertEquals(UiMessage.Res(R.string.qa_curator_reason_required), component.state.value.errorMessage)
        assertTrue(fakes.transitionCalls.isEmpty())
    }

    @Test
    fun `applied transition closes the intent and re-reads the page`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)
        openMenuAndRequest(component, "lesson-1", LessonShelf.ARENA)
        component.onReasonChanged("admit after review")

        component.onTransitionClick(LessonShelf.ARENA)

        val (uid, command, mutationId) = fakes.transitionCalls.single()
        assertEquals("uid-a", uid)
        assertEquals(LessonShelf.ARENA, command.targetShelf)
        assertEquals("admit after review", command.reason)
        assertEquals("transition-1", mutationId)
        assertEquals(2, fakes.curatorCalls.size)
        assertTrue(component.state.value.lastDecision is LessonTransitionDecision.Applied)
        assertEquals(false, component.state.value.isTransitioning)
    }

    @Test
    fun `reason is required by the use case too`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)
        openMenuAndRequest(component, "lesson-1", LessonShelf.ARENA)
        component.onReasonChanged("   ")

        component.onTransitionClick(LessonShelf.ARENA)

        assertEquals(UiMessage.Res(R.string.qa_curator_reason_required), component.state.value.errorMessage)
        assertTrue(fakes.transitionCalls.isEmpty())
    }

    @Test
    fun `short tap opens the lesson without any transition surface`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)

        component.onLessonSelected("lesson-1")

        assertEquals("lesson-1", component.state.value.selectedLessonId)
        assertNull(component.state.value.menuLessonId)
        assertNull(component.state.value.pendingConfirmation)
        assertTrue(fakes.transitionCalls.isEmpty())
    }

    @Test
    fun `long press opens the menu for its lesson and dismissal closes it`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)

        component.onLessonMenuRequested("lesson-1")
        assertEquals("lesson-1", component.state.value.menuLessonId)

        component.onMenuDismissed()
        assertNull(component.state.value.menuLessonId)
        assertTrue(fakes.transitionCalls.isEmpty())
    }

    @Test
    fun `a menu item assigns the target and confirmation is a separate step`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)
        component.onLessonMenuRequested("lesson-1")

        component.onTransitionRequested(LessonShelf.RETIRED)

        assertNull(component.state.value.menuLessonId)
        assertEquals(TransitionConfirmation("lesson-1", LessonShelf.RETIRED), component.state.value.pendingConfirmation)
        assertTrue(fakes.transitionCalls.isEmpty(), "nothing is sent before the confirmation")

        component.onConfirmationDismissed()
        assertNull(component.state.value.pendingConfirmation)
        assertTrue(fakes.transitionCalls.isEmpty())
    }

    @Test
    fun `confirmation sends the transition for the menu lesson, not the selected one`() = runTest(testScheduler) {
        val fakes = FakeUseCases(
            ownedEntries = listOf(lifecycleEntry("lesson-1"), lifecycleEntry("lesson-2")),
        )
        val component = buildComponent(fakes)
        component.onLessonSelected("lesson-1")
        component.onReasonChanged("retire after confirmed fault")
        component.onLessonMenuRequested("lesson-2")
        component.onTransitionRequested(LessonShelf.RETIRED)

        component.onTransitionClick(LessonShelf.RETIRED)

        val (uid, command, mutationId) = fakes.transitionCalls.single()
        assertEquals("lesson-2", command.lessonId, "the decision follows the menu lesson")
        assertEquals(LessonShelf.RETIRED, command.targetShelf)
        assertEquals("uid-a", uid)
        assertEquals("transition-1", mutationId)
        assertNull(component.state.value.pendingConfirmation, "a receipt closes the confirmation")
    }

    @Test
    fun `confirm without a pending confirmation sends nothing`() = runTest(testScheduler) {
        val fakes = FakeUseCases(ownedEntries = listOf(lifecycleEntry("lesson-1")))
        val component = buildComponent(fakes)
        component.onLessonSelected("lesson-1")
        component.onReasonChanged("orphan confirm")

        component.onTransitionClick(LessonShelf.ARENA)

        assertTrue(fakes.transitionCalls.isEmpty())
        assertNull(component.state.value.errorMessage)
    }

    @Test
    fun `a failed transition keeps the page as it was`() = runTest(testScheduler) {
        val fakes = FakeUseCases(
            ownedEntries = listOf(lifecycleEntry("lesson-1")),
            transitionResult = Result.failure(SyncFailure(SyncError.Refused("INVALID_TRANSITION"))),
        )
        val component = buildComponent(fakes)
        openMenuAndRequest(component, "lesson-1", LessonShelf.HOME)
        component.onReasonChanged("attempted skipped rung")

        component.onTransitionClick(LessonShelf.HOME)

        assertEquals(1, fakes.curatorCalls.size, "the refusal must not re-read the page")
        assertEquals(listOf(lifecycleEntry("lesson-1")), component.state.value.entries, "the prior revision stays visible")
        assertNull(component.state.value.lastDecision)
        assertNull(component.state.value.pendingConfirmation)
        assertEquals(false, component.state.value.isTransitioning)
        assertEquals(UiMessage.Raw(SyncError.Refused("INVALID_TRANSITION").toString()), component.state.value.errorMessage)
    }

    @Test
    fun `a retry after a failed transition rides the same mutation key`() = runTest(testScheduler) {
        // Ретро 2026-10-25, L7 (AD-2): транспортный исход не закрывает намерение — повтор едет
        // под тем же ключом, и решённый-таки переход ответит той же квитанцией, а не вторым.
        val fakes = FakeUseCases(
            ownedEntries = listOf(lifecycleEntry("lesson-1")),
            transitionResult = Result.failure(SyncFailure(SyncError.Refused("INVALID_TRANSITION"))),
        )
        val component = buildComponent(fakes)
        openMenuAndRequest(component, "lesson-1", LessonShelf.HOME)
        component.onReasonChanged("attempted skipped rung")
        component.onTransitionClick(LessonShelf.HOME)

        openMenuAndRequest(component, "lesson-1", LessonShelf.HOME)
        component.onTransitionClick(LessonShelf.HOME)

        assertEquals(2, fakes.transitionCalls.size, "the retry is sent again")
        assertEquals("transition-1", fakes.transitionCalls[0].third, "первая попытка — первый ключ")
        assertEquals(
            "transition-1",
            fakes.transitionCalls[1].third,
            "ретрай не минтит новый ключ: намерение живо до квитанции",
        )
    }

    private fun openMenuAndRequest(
        component: DefaultCuratorComponent,
        lessonId: String,
        target: LessonShelf,
    ) {
        component.onLessonMenuRequested(lessonId)
        component.onTransitionRequested(target)
    }

    private fun buildComponent(fakes: FakeUseCases): DefaultCuratorComponent {
        lifecycle = LifecycleRegistry()
        val authRepository = FakeAuthRepository("uid-a")
        return DefaultCuratorComponent(
            componentContext = DefaultComponentContext(lifecycle),
            authRepository = authRepository,
            listCurator = ListCuratorLifecycleUseCase(fakes),
            listHistory = ListLessonHistoryUseCase(fakes),
            transitionLesson = TransitionLessonUseCase(fakes),
            idProvider = SequentialIdProvider(),
        ).also { lifecycle.resume() }
    }

    private fun lifecycleEntry(lessonId: String) =
        LessonLifecycleEntry(
            lessonId = lessonId,
            currentContentVersion = 1L,
            published = true,
            reviewApproved = true,
            rating = LessonLifecycleRating(0, 0, null, 0, null),
            state = LessonLifecycleState.REVIEW,
            placement = null,
        )

    private class SequentialIdProvider : QuestAuthoringIdProvider {
        private var counter = 0
        override fun nextId(prefix: String): String = "$prefix-${++counter}"
    }

    private class FakeAuthRepository(
        private val uid: String?,
    ) : AuthRepository {
        private val flow = MutableStateFlow(uid)
        override fun observeUid(): Flow<String?> = flow

        override suspend fun currentUid(): String? = flow.value
    }

    private class FakeUseCases(
        private val ownedEntries: List<LessonLifecycleEntry> = emptyList(),
        private val history: List<LessonHistoryEntry> = emptyList(),
        private val curatorResult: Result<LessonLifecyclePage>? = null,
        private val transitionResult: Result<LessonTransitionDecision>? = null,
    ) : com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository {
        val curatorCalls = mutableListOf<Array<String?>>()
        val historyCalls = mutableListOf<Pair<String, String>>()
        val transitionCalls = mutableListOf<Triple<String, LessonTransitionCommand, String>>()

        private fun page(): LessonLifecyclePage = LessonLifecyclePage(ownedEntries, null)

        override suspend fun listOwned(
            ownerUid: String,
            afterLessonId: String?,
            pageSize: Int,
        ): Result<LessonLifecyclePage> = curatorResult ?: Result.success(page())

        override suspend fun listCurator(
            ownerUid: String,
            afterLessonId: String?,
            pageSize: Int,
        ): Result<LessonLifecyclePage> {
            curatorCalls.add(arrayOf(ownerUid, afterLessonId))
            return curatorResult ?: Result.success(page())
        }

        override suspend fun listHistory(
            ownerUid: String,
            lessonId: String,
            cursor: String?,
            pageSize: Int,
        ): Result<LessonHistoryPage> {
            historyCalls.add(ownerUid to lessonId)
            return Result.success(LessonHistoryPage(history, null))
        }

        override suspend fun listRoyalty(
            ownerUid: String,
            cursor: String?,
            pageSize: Int,
        ): Result<LessonRoyaltyPage> = throw UnsupportedOperationException()

        override suspend fun transition(
            ownerUid: String,
            command: LessonTransitionCommand,
            mutationId: String,
        ): Result<LessonTransitionDecision> {
            transitionCalls.add(Triple(ownerUid, command, mutationId))
            return transitionResult ?: Result.success(
                LessonTransitionDecision.Applied(command.lessonId, LessonShelf.REVIEW, command.targetShelf, 1L),
            )
        }
    }
}
