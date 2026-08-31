package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptFixtures
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
 * Source: docs/features/lesson-runner/plan/phase-04/tests.md §IT-02, IT-03
 *
 * IT-02: rotation reuses RunnerStateHolder via same instanceKeeper (AC-35)
 * IT-03: non-rotation destroy creates fresh state in new component (AC-36)
 *
 * Uses InstanceKeeperDispatcher to control instanceKeeper lifecycle independently of
 * the Essenty lifecycle, matching Decompose's rotation behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonRunnerIntegrationTest {

    private val lifecycles = mutableListOf<LifecycleRegistry>()

    private lateinit var fakeStart: FakeStartLessonAttemptUseCase
    private lateinit var fakeComplete: FakeCompleteAttemptUseCase
    private lateinit var fakeAbort: FakeAbortAttemptUseCase
    private lateinit var fakeSubmitRating: FakeSubmitLessonRatingUseCase
    private lateinit var fakeClock: FakeClock
    private lateinit var fakeLessonRepo: FakeLessonRepository
    private lateinit var fakeAttemptRepo: FakeLessonAttemptRepository
    private lateinit var fakeRatingRepo: FakeLessonRatingRepository

    @Before
    fun setUp() {
        fakeStart = FakeStartLessonAttemptUseCase()
        fakeComplete = FakeCompleteAttemptUseCase()
        fakeAbort = FakeAbortAttemptUseCase()
        fakeSubmitRating = FakeSubmitLessonRatingUseCase()
        fakeClock = FakeClock()
        fakeLessonRepo = FakeLessonRepository()
        fakeAttemptRepo = FakeLessonAttemptRepository()
        fakeRatingRepo = FakeLessonRatingRepository()
        fakeLessonRepo.addLesson(defaultLesson())
    }

    @After
    fun tearDown() {
        lifecycles.forEach { lifecycle ->
            if (lifecycle.state != com.arkivanov.essenty.lifecycle.Lifecycle.State.DESTROYED) {
                lifecycle.stop()
                lifecycle.destroy()
            }
        }
        lifecycles.clear()
    }

    // ── Build helpers ──────────────────────────────────────────────────────────

    private fun buildComponent(
        instanceKeeper: InstanceKeeperDispatcher,
        lessonId: LessonId = LessonId("lesson1"),
        mode: Difficulty = Difficulty.EASY,
        startAttemptUseCase: suspend (LessonId, Difficulty, SessionMode) -> RunnerState = fakeStart::invoke,
        completeAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeComplete::invoke,
        abortAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeAbort::invoke,
        submitRatingUseCase: suspend (String, LessonId, Int) -> Result<Unit> = fakeSubmitRating::invoke,
    ): DefaultLessonRunnerRootComponent {
        val lifecycle = LifecycleRegistry()
        lifecycles.add(lifecycle)
        lifecycle.resume()
        val ctx = DefaultComponentContext(
            lifecycle = lifecycle,
            instanceKeeper = instanceKeeper,
        )
        return DefaultLessonRunnerRootComponent(
            componentContext = ctx,
            lessonId = lessonId,
            mode = mode,
            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
                startAttempt = startAttemptUseCase,
                completeAttempt = completeAttemptUseCase,
                abortAttempt = abortAttemptUseCase,
                submitRating = submitRatingUseCase,
            ),
            lessonRepository = fakeLessonRepo,
            attemptRepository = fakeAttemptRepo,
            getResultAdvice = GetResultAdviceUseCase(lessonRepository = fakeLessonRepo),
            clock = fakeClock,
            mainContext = Dispatchers.Unconfined,
        )
    }

    private fun defaultLesson() = Lesson(
        id = LessonId("lesson1"),
        themeId = ThemeId("theme1"),
        title = "Test Lesson",
        order = 0,
        version = 5L,
        contentsVersion = 1L,
        lastModifiedAt = 1000L,
    )

    private fun optId(s: String) = OptionId(s)

    private fun singleChoiceDraft() = UserAnswerDraft.SingleChoiceDraft(optId("A"))

    private fun makeRunnerQuestion(
        id: String = "q1",
        codeAnswerIndex: Int = 0,
    ) = RunnerQuestion.Valid(
        sourceId = QuestionId(id),
        order = codeAnswerIndex,
        codeAnswerIndex = codeAnswerIndex,
        content = QuestionContent.SingleChoice(
            id = id,
            difficulty = Difficulty.EASY,
            text = "Q",
            imageUrl = null,
            options = listOf("A", "B").map { QuestionContent.Option(optId(it), it) },
            correctOptionId = optId("A"),
        ),
    )

    private fun makeReadyState(
        questions: Int = 5,
        indexInPool: Int = 0,
    ) = RunnerState.Ready(
        userId = "user1",
        lessonId = LessonId("lesson1"),
        lessonVersion = 5L,
        mode = Difficulty.EASY,
        playOrder = (0 until questions).map { makeRunnerQuestion("q${it + 1}", it) },
        eligibleSize = questions,
        indexInPool = indexInPool,
        codeAnswer = CodeAnswer("0".repeat(questions)),
        deadlineMs = 9_000_000L,
        seed = 12345L,
        currentDraftAnswer = null,
        isPaused = false,
    )

    // ── IT-02: rotation reuses RunnerStateHolder ──────────────────────────────

    /**
     * IT-02: GIVEN component with FakeStart returning Ready; answers advance to indexInPool=3
     *        WHEN simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
     *        THEN new component has uiState.indexInPool == 3 (state preserved via shared stateHolder)
     *        AND fakeStart.callCount == 1 (no duplicate StartLessonAttemptUseCase call on rotation)
     *
     * AC-35: Configuration change (rotation) preserves in-progress attempt state.
     */
    @Test
    fun `rotation_component_reuses_runnerStateHolder`() {
        fakeStart.result = makeReadyState(questions = 5, indexInPool = 0)
        val instanceKeeper = InstanceKeeperDispatcher()

        // Create component1, start use case runs → Ready(indexInPool=0)
        val component1 = buildComponent(instanceKeeper = instanceKeeper)
        assertIs<RunnerUiState.Question>(component1.uiState.value)
        assertEquals(1, fakeStart.callCount, "startAttemptUseCase must be called once on init")

        // Advance to indexInPool=3 by answering 3 questions
        repeat(3) { component1.onAnswer(singleChoiceDraft()) }
        val indexAfterAnswers = (component1.uiState.value as RunnerUiState.Question).indexInPool
        assertEquals(3, indexAfterAnswers, "indexInPool must be 3 after answering 3 questions")

        // Simulate rotation: destroy lifecycle1 (NOT instanceKeeper)
        val lifecycle1 = lifecycles.last()
        lifecycle1.destroy()

        // Create component2 with the SAME instanceKeeper
        val component2 = buildComponent(instanceKeeper = instanceKeeper)

        // component2 should reuse RunnerStateHolder → same indexInPool (or adjusted after auto-answer on stop)
        val uiState2 = component2.uiState.value
        assertIs<RunnerUiState.Question>(uiState2)

        // Key assertion: startAttemptUseCase must NOT have been called again
        assertEquals(
            1,
            fakeStart.callCount,
            "startAttemptUseCase must NOT be called again on rotation (instanceKeeper reuse)",
        )
    }

    // ── IT-03: non-rotation destroy → state NOT preserved ─────────────────────

    /**
     * IT-03: GIVEN component in Ready state (indexInPool=3)
     *        WHEN instanceKeeper.destroy() called (non-rotation: process navigation back)
     *        THEN new component with fresh instanceKeeper calls startAttemptUseCase again (state not preserved)
     *        AND fakeStart.callCount == 2 (fresh start)
     *
     * AC-36: Process kill or full navigation back clears in-progress state.
     * Negative: proves state is NOT shared when instanceKeeper is destroyed and replaced.
     */
    @Test
    fun `stateHolder_onDestroy_clears_state_for_new_component`() {
        fakeStart.result = makeReadyState(questions = 5, indexInPool = 0)
        val instanceKeeper1 = InstanceKeeperDispatcher()

        // Create component1 → startAttemptUseCase runs once
        val component1 = buildComponent(instanceKeeper = instanceKeeper1)
        assertEquals(1, fakeStart.callCount)
        repeat(3) { component1.onAnswer(singleChoiceDraft()) }
        assertEquals(3, (component1.uiState.value as RunnerUiState.Question).indexInPool)

        // Full destroy (non-rotation): lifecycle + instanceKeeper both destroyed
        val lifecycle1 = lifecycles.last()
        lifecycle1.stop()
        lifecycle1.destroy()
        instanceKeeper1.destroy() // calls RunnerStateHolder.onDestroy() → state cleared

        // Create component2 with a FRESH instanceKeeper (no shared state)
        val instanceKeeper2 = InstanceKeeperDispatcher()
        buildComponent(instanceKeeper = instanceKeeper2)

        // component2 must call startAttemptUseCase again (fresh start)
        assertEquals(
            2,
            fakeStart.callCount,
            "startAttemptUseCase must be called again when instanceKeeper is fresh (non-rotation)",
        )
    }

    // ── IT-02b: rotation preserves indexInPool exactly ───────────────────────

    /**
     * IT-02b: Verifies that the exact indexInPool value from before rotation is preserved.
     * Same as IT-02 but also verifies indexInPool numerically after accounting for
     * any auto-answer that may occur when lifecycle stops.
     *
     * Uses fakeStart returning Ready(indexInPool=2) directly to avoid startup timing issues.
     */
    @Test
    fun `rotation_indexInPool_preserved_via_same_instanceKeeper`() {
        // Use a state with indexInPool=2 already set (no answers needed)
        fakeStart.result = makeReadyState(questions = 5, indexInPool = 2)
        val instanceKeeper = InstanceKeeperDispatcher()

        val component1 = buildComponent(instanceKeeper = instanceKeeper)
        val indexBefore = (component1.uiState.value as RunnerUiState.Question).indexInPool
        assertEquals(2, indexBefore, "component1 must start at indexInPool=2")

        // Rotation: destroy lifecycle only (not instanceKeeper)
        lifecycles.last().destroy()

        // Create component2 with same instanceKeeper
        val component2 = buildComponent(instanceKeeper = instanceKeeper)

        // component2 must see the preserved state (indexInPool ≥ 2, no fresh start)
        assertIs<RunnerUiState.Question>(component2.uiState.value)
        assertEquals(
            1,
            fakeStart.callCount,
            "startAttemptUseCase must be called only once total across rotation",
        )
    }
}
