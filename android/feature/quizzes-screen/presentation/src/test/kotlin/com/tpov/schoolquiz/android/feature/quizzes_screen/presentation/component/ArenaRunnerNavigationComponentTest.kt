package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeEconomyRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonUnlockQueue
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.StubCatalogRepository
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.RetirePublicQuestUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunnerNavigationComponentTest {
    private val lifecycles = mutableListOf<LifecycleRegistry>()

    @After
    fun tearDown() {
        lifecycles.forEach { lifecycle ->
            lifecycle.stop()
            lifecycle.destroy()
        }
        lifecycles.clear()
    }

    @Test
    fun `same offered route creates one fresh runner and never starts from catalogue construction`() {
        val entry = EntryStub(preparedState())
        val requests = mutableListOf<ArenaRunnerRequest>()
        val component =
            buildComponent(entry) { _, request ->
                requests += request
                RunnerStub()
            }

        component.openCourseArena()
        assertEquals(0, requests.size)

        component.openArenaRunner(STAMP, TICKET_ID, fresh = true)
        component.openArenaRunner(STAMP, TICKET_ID, fresh = true)

        assertEquals(1, requests.size)
        assertEquals(STAMP, requests.single().fresh?.session)
        assertEquals(STAMP, requests.single().initiatingSession)
        assertIs<QuizzesChild.LessonRunner>(component.childStack.value.active.instance)
    }

    @Test
    fun `resume route retains its live epoch but has no Fresh authority`() {
        val entry = EntryStub(resumableState())
        val requests = mutableListOf<ArenaRunnerRequest>()
        val component =
            buildComponent(entry) { _, request ->
                requests += request
                RunnerStub()
            }

        component.openCourseArena()
        component.openArenaRunner(STAMP, TICKET_ID, fresh = false)

        assertNull(requests.single().fresh)
        assertEquals(STAMP, requests.single().initiatingSession)
    }

    @Test
    fun `back close and breadcrumb exit stay delegated to Arena runner cancellation`() {
        val entry = EntryStub(preparedState())
        val runner = RunnerStub()
        val backDispatcher = BackDispatcher()
        val component = buildComponent(entry, backDispatcher) { _, _ -> runner }

        component.openCourseArena()
        component.openArenaRunner(STAMP, TICKET_ID, fresh = true)
        backDispatcher.back()
        component.dismissQuizzes()
        component.popToLevel(0)

        assertEquals(3, runner.crossButtonTaps)
        assertIs<QuizzesChild.LessonRunner>(component.childStack.value.active.instance)
    }

    @Test
    fun `return refreshes only the entry frame that launched the runner`() {
        val entry = EntryStub(preparedState())
        val component = buildComponent(entry) { _, _ -> RunnerStub() }

        component.openCourseArena()
        component.openArenaRunner(STAMP, TICKET_ID, fresh = true)
        component.popCurrentChild()

        assertEquals(listOf<ArenaEntryEvent>(ArenaEntryEvent.Load(STAMP, ArenaEntryPage.TICKETS)), entry.events)
    }

    @Test
    fun `return after ABA session change does not refresh the new entry frame`() {
        val entry = EntryStub(preparedState())
        val component = buildComponent(entry) { _, _ -> RunnerStub() }

        component.openCourseArena()
        component.openArenaRunner(STAMP, TICKET_ID, fresh = true)
        entry.state.value = entry.state.value.copy(session = STAMP.copy(generation = 2))
        component.popCurrentChild()

        assertTrue(entry.events.isEmpty())
    }

    @Test
    fun `StateKeeper restore creates ResumeOnly Arena request without ordinary runner`() {
        val savedState = StateKeeperDispatcher(null)
        val original =
            buildComponent(
                entry = EntryStub(preparedState()),
                stateKeeper = savedState,
            ) { _, _ -> RunnerStub() }
        original.openCourseArena()
        original.openArenaRunner(STAMP, TICKET_ID, fresh = true)

        val restoredRequests = mutableListOf<ArenaRunnerRequest>()
        var ordinaryCreates = 0
        val restored =
            buildComponent(
                entry = EntryStub(preparedState()),
                stateKeeper = StateKeeperDispatcher(savedState.save()),
                ordinaryRunnerFactory =
                    LessonRunnerComponentFactory { _, _, _, _, _ ->
                        ordinaryCreates++
                        error("Ordinary runner must not be restored for an Arena route")
                    },
            ) { _, request ->
                restoredRequests += request
                RunnerStub()
            }

        assertIs<QuizzesChild.LessonRunner>(restored.childStack.value.active.instance)
        assertEquals(0, ordinaryCreates)
        assertEquals(1, restoredRequests.size)
        assertEquals("owner", restoredRequests.single().ownerUid)
        assertEquals(TICKET_ID, restoredRequests.single().ticketId)
        assertNull(restoredRequests.single().fresh)
        assertNull(restoredRequests.single().initiatingSession)
    }

    private fun buildComponent(
        entry: EntryStub,
        backDispatcher: BackDispatcher = BackDispatcher(),
        stateKeeper: StateKeeperDispatcher = StateKeeperDispatcher(null),
        ordinaryRunnerFactory: LessonRunnerComponentFactory =
            LessonRunnerComponentFactory { _, _, _, _, _ ->
                error("Not expected")
            },
        runnerFactory: ArenaRunnerComponentFactory,
    ): DefaultQuizzesComponent {
        val lifecycle = LifecycleRegistry()
        lifecycles += lifecycle
        lifecycle.resume()
        val context =
            DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
                backHandler = backDispatcher,
            )
        val questRepository = FakeQuestRepository()
        return DefaultQuizzesComponent(
            componentContext = context,
            questRepository = questRepository,
            sectionRepository = FakeSectionRepository(),
            themeRepository = FakeThemeRepository(),
            lessonRepository = FakeLessonRepository(),
            lessonAttemptRepository = FakeLessonAttemptRepository(),
            authRepository = FakeAuthRepository(),
            economyRepository = FakeEconomyRepository(),
            queueLessonUnlock = FakeLessonUnlockQueue(),
            questionRepository = FakeQuestionRepository(),
            catalogRepository = StubCatalogRepository(),
            setPublicQuestShelf = SetPublicQuestShelfUseCase(questRepository),
            retirePublicQuest = RetirePublicQuestUseCase(questRepository),
            lessonRunnerFactory = ordinaryRunnerFactory,
            privateQuizRunnerFactory = { _, _ -> error("Not expected") },
            lessonEarningsFactory =
                { _, _, _ -> throw UnsupportedOperationException("not under test") },
            arenaCatalogFactory = ArenaLessonCatalogComponentFactory { CatalogStub(entry) },
            arenaRunnerFactory = runnerFactory,
            mainContext = Dispatchers.Unconfined,
        )
    }

    private fun preparedState() = ArenaEntryUiState(session = STAMP, tickets = listOf(entry()))

    private fun resumableState() =
        ArenaEntryUiState(
            session = STAMP,
            tickets = listOf(entry(state = ArenaLocalEntryState.RESUME_OR_RECONCILE)),
        )

    private fun entry(state: ArenaLocalEntryState = ArenaLocalEntryState.PREPARED_SNAPSHOT) =
        ArenaLocalEntry(
            ticketId = TICKET_ID,
            header =
                ArenaTicketHeader(
                    schemaVersion = 1,
                    ticketId = TICKET_ID,
                    attemptId = "attempt",
                    ownerUid = STAMP.ownerUid,
                    requestId = "request",
                    mode = ArenaEntryMode.SELECTED,
                    difficulty = Difficulty.EASY,
                    lessonId = "lesson",
                    lessonVersion = 1,
                    status = ArenaTicketStatus.RESERVED,
                    priceNolics = 1,
                    lifeCost = 1,
                    issuedAtMs = 1,
                    dayId = "2026-09-08",
                ),
            state = state,
            localDecision = ArenaLocalDecision.NONE,
        )

    private class CatalogStub(
        override val entry: ArenaEntryComponent,
    ) : ArenaLessonCatalogComponent {
        override val state = MutableStateFlow(ArenaLessonCatalogUiState(session = STAMP))

        override fun onRefresh() = Unit

        override fun onLoadMore() = Unit
    }

    private class EntryStub(initial: ArenaEntryUiState) : ArenaEntryComponent {
        override val state = MutableStateFlow(initial)
        val events = mutableListOf<ArenaEntryEvent>()

        override fun onEvent(event: ArenaEntryEvent) {
            events += event
        }
    }

    private class RunnerStub : LessonRunnerRootComponent {
    override val needsTermsConsent = kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun acceptTerms() = Unit
        override val uiState = MutableStateFlow<RunnerUiState>(RunnerUiState.Loading)
        override val events: Flow<RunnerEvent> = emptyFlow()
        override val comments = MutableStateFlow<List<LessonComment>>(emptyList())
        override val isHardMode = false
        var crossButtonTaps = 0

        override fun onAnswer(answer: UserAnswerDraft) = Unit

        override fun onDraftChanged(draft: UserAnswerDraft) = Unit

        override fun onTimeout() = Unit

        override fun onContinue() = Unit

        override fun onExit() = Unit

        override fun onCrossButtonTap() {
            crossButtonTaps++
        }

        override fun onCrossConfirmed() = Unit

        override fun onCrossCancelled() = Unit

        override fun onSubmitRating(rating: Int) = Unit

        override fun onSyncResult() = Unit

        override fun onFinish() = Unit

        override fun onRunAgain() = Unit

        override fun onNextLesson() = Unit

        override fun hintRequested() = false

        override fun onPostComment(text: String) = Unit

        override fun onBack() = Unit
    }

    private companion object {
        val STAMP = ArenaSessionStamp("owner", 1)
        const val TICKET_ID = "ticket"
    }
}
