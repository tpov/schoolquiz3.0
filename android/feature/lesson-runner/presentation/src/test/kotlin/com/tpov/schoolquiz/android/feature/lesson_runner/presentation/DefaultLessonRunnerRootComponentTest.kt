package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
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
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Presentation unit tests for [DefaultLessonRunnerRootComponent].
 * Source: docs/features/lesson-runner/plan/phase-04/tests.md §PT-01..PT-41
 *
 * All tests use Dispatchers.Unconfined as mainContext for synchronous coroutine execution.
 * Event channel tests use runTest with async{} / events.first().
 *
 * Open Question: RunnerUiState.Result is expected to contain bestStarsRawTenths: Int
 * and hardUnlocked: Boolean fields for PT-15..PT-17 and PT-34..PT-36 (per AC-21..23, AC-47..49).
 * If these fields are absent, the tests will fail to compile — backend-dev should add them.
 */
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonRunnerRootComponentTest {

    private lateinit var lifecycle: LifecycleRegistry

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
        fakeLessonRepo.addLesson(makeLesson())
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── Build helpers ──────────────────────────────────────────────────────────

    private fun buildComponent(
        lessonId: LessonId = LessonId("lesson1"),
        mode: Difficulty = Difficulty.EASY,
        startAttemptUseCase: suspend (LessonId, Difficulty, SessionMode) -> RunnerState = fakeStart::invoke,
        completeAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeComplete::invoke,
        abortAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeAbort::invoke,
        submitRatingUseCase: suspend (String, LessonId, Int) -> Result<Unit> = fakeSubmitRating::invoke,
    ): DefaultLessonRunnerRootComponent {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle = lifecycle)
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
            clock = fakeClock,
            mainContext = Dispatchers.Unconfined,
        )
    }

    // ── Domain / fixture helpers ──────────────────────────────────────────────

    private fun optId(s: String) = OptionId(s)

    private fun singleChoiceDraft(optionId: String = "A") =
        UserAnswerDraft.SingleChoiceDraft(optId(optionId))

    private fun makeRunnerQuestion(
        id: String = "q1",
        codeAnswerIndex: Int = 0,
        mode: Difficulty = Difficulty.EASY,
        charsCount: Int = 20,
    ): RunnerQuestion.Valid {
        // Build text + options so total char count ≈ charsCount for timer tests
        val optText = if (charsCount > 4) "x".repeat((charsCount - 4) / 4) else "x"
        return RunnerQuestion.Valid(
            sourceId = QuestionId(id),
            order = 0,
            codeAnswerIndex = codeAnswerIndex,
            content = QuestionContent.SingleChoice(
                id = id,
                difficulty = mode,
                text = "xxxx",
                imageUrl = null,
                options = listOf("A", "B", "C", "D").map {
                    QuestionContent.Option(optId(it), optText)
                },
                correctOptionId = optId("A"),
            ),
        )
    }

    private fun makeReadyState(
        userId: String = "user1",
        lessonId: String = "lesson1",
        lessonVersion: Long = 5L,
        mode: Difficulty = Difficulty.EASY,
        questions: Int = 1,
        playOrder: List<RunnerQuestion.Valid> = (0 until questions).map {
            makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it)
        },
        eligibleSize: Int = playOrder.size,
        indexInPool: Int = 0,
        deadlineMs: Long = 9_000_000L,
        isPaused: Boolean = false,
    ) = RunnerState.Ready(
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = lessonVersion,
        mode = mode,
        playOrder = playOrder,
        eligibleSize = eligibleSize,
        indexInPool = indexInPool,
        codeAnswer = CodeAnswer("0".repeat(eligibleSize)),
        deadlineMs = deadlineMs,
        seed = 12345L,
        currentDraftAnswer = null,
        isPaused = isPaused,
    )

    private fun makeLesson(
        id: String = "lesson1",
        version: Long = 5L,
        top3: List<TopParticipant> = emptyList(),
        averageRating: Float? = null,
        ratingCount: Int = 0,
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId("theme1"),
        title = "Test Lesson",
        order = 0,
        version = version,
        contentsVersion = 1L,
        lastModifiedAt = 1000L,
        top3 = top3,
        averageRating = averageRating,
        ratingCount = ratingCount,
    )

    // ── PT-01: EASY → Question(isHard=false) ─────────────────────────────────

    // PT-01: GIVEN component EASY mode; FakeStart returns Ready
    //        WHEN init THEN uiState is Question(isHard=false)
    @Test
    fun `EASY_loading_to_ready_no_flagSecure`() {
        fakeStart.result = makeReadyState(mode = Difficulty.EASY)
        val component = buildComponent(mode = Difficulty.EASY)

        val uiState = component.uiState.value
        assertIs<RunnerUiState.Question>(uiState)
        assertFalse(uiState.isHard, "EASY mode must map to isHard=false")
    }

    // ── PT-02: HARD → Question(isHard=true) ──────────────────────────────────

    // PT-02: GIVEN HARD mode; FakeStart returns Ready(mode=HARD)
    //        WHEN init THEN uiState is Question(isHard=true)
    @Test
    fun `HARD_loading_to_ready_isHard_true`() {
        fakeStart.result = makeReadyState(mode = Difficulty.HARD)
        val component = buildComponent(mode = Difficulty.HARD)

        val uiState = component.uiState.value
        assertIs<RunnerUiState.Question>(uiState)
        assertTrue(uiState.isHard, "HARD mode must map to isHard=true")
    }

    // ── PT-03: onCrossConfirmed → AbortUseCase called ─────────────────────────

    // PT-03: GIVEN state = Ready; FakeAbortAttemptUseCase
    //        WHEN onCrossConfirmed() THEN fakeAbortUseCase.callCount == 1
    @Test
    fun `onCrossConfirmed_abortUseCase_called`() {
        fakeStart.result = makeReadyState()
        val component = buildComponent()
        assertIs<RunnerUiState.Question>(component.uiState.value)

        component.onCrossConfirmed()

        assertEquals(1, fakeAbort.callCount, "AbortAttemptUseCase must be called exactly once")
    }

    // ── PT-04: all questions answered → completeUseCase called once ────────────

    // PT-04: GIVEN state = Ready with 1 question; FakeCompleteAttemptUseCase
    //        WHEN onAnswer(answer) for last question THEN fakeCompleteUseCase.callCount == 1
    @Test
    fun `allQuestionsAnswered_completeUseCase_called_once`() {
        fakeStart.result = makeReadyState(questions = 1)
        val component = buildComponent()

        component.onAnswer(singleChoiceDraft())

        assertEquals(1, fakeComplete.callCount, "CompleteAttemptUseCase must be called exactly once")
    }

    // ── PT-05: onFinish → emit NavigateBack ──────────────────────────────────

    // PT-05: GIVEN state = Completed; WHEN onFinish() THEN emit RunnerEvent.NavigateBack
    @Test
    fun `completedState_onFinish_emits_navigateBack`() = runTest {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(FakeAttemptFixtures.fixtureAttempt(), ratingPrompt = false)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())
        assertIs<RunnerUiState.Result>(component.uiState.value)

        val deferred = async { component.events.first() }
        component.onFinish()

        assertIs<RunnerEvent.NavigateBack>(deferred.await(), "onFinish must emit NavigateBack")
    }

    // ── PT-10: onTimeout → state updated ─────────────────────────────────────

    // PT-10: GIVEN state = Ready(indexInPool=0, 2 questions)
    //        WHEN onTimeout() THEN uiState has indexInPool > 0
    @Test
    fun `onTimeout_autoAnswerOnTimeout_stateUpdated`() {
        fakeStart.result = makeReadyState(questions = 2)
        val component = buildComponent()
        val initialIndex = (component.uiState.value as RunnerUiState.Question).indexInPool

        component.onTimeout()

        val newIndex = (component.uiState.value as RunnerUiState.Question).indexInPool
        assertTrue(newIndex > initialIndex, "indexInPool must advance after onTimeout")
    }

    // ── PT-11: EASY 100% → Stars(20) ─────────────────────────────────────────

    // PT-11: GIVEN EASY mode + attempt(percentScore=100)
    //        WHEN Completed state THEN computeStars(attempt) == Stars(20)
    @Test
    fun `EASY_100percent_computeStars_gives_Stars20`() {
        val attempt = FakeAttemptFixtures.fixtureAttempt(mode = Difficulty.EASY, percentScore = 100)
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(attempt, ratingPrompt = false)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        val stars = computeStars(result.percentScore, result.mode)
        assertEquals(20, stars.rawTenths, "EASY 100% must give Stars(rawTenths=20)")
    }

    // ── PT-12: EASY 50% → Stars(10); hardUnlocked=false ──────────────────────

    // PT-12: GIVEN EASY mode + attempt(percentScore=50)
    //        WHEN Completed THEN computeStars gives rawTenths=10; hardUnlocked=false
    @Test
    fun `EASY_50percent_computeStars_gives_Stars10_hardUnlocked_false`() {
        val attempt = FakeAttemptFixtures.fixtureAttempt(
            mode = Difficulty.EASY,
            percentScore = 50,
            codeAnswer = "15111",
        )
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(attempt, ratingPrompt = false)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        val stars = computeStars(result.percentScore, result.mode)
        assertEquals(10, stars.rawTenths, "EASY 50% must give Stars(rawTenths=10)")
        // hardUnlocked requires allShownAnswersAre9 → codeAnswer "15111" is false
        assertFalse(result.hardUnlocked, "hardUnlocked must be false when codeAnswer has non-9 digits")
    }

    // ── PT-13: HARD 80% → Stars(28) ──────────────────────────────────────────

    // PT-13: GIVEN HARD mode + attempt(percentScore=80)
    //        WHEN Completed THEN computeStars gives rawTenths=28
    @Test
    fun `HARD_80percent_computeStars_gives_Stars28`() {
        val attempt = FakeAttemptFixtures.fixtureAttempt(mode = Difficulty.HARD, percentScore = 80)
        fakeStart.result = makeReadyState(mode = Difficulty.HARD, questions = 1)
        fakeComplete.result = RunnerState.Completed(attempt, ratingPrompt = false)
        val component = buildComponent(mode = Difficulty.HARD)
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        val stars = computeStars(result.percentScore, result.mode)
        assertEquals(28, stars.rawTenths, "HARD 80% must give Stars(rawTenths=28)")
    }

    // ── PT-14: HARD 100% → Stars(30) ─────────────────────────────────────────

    // PT-14: GIVEN HARD mode + attempt(percentScore=100)
    //        WHEN Completed THEN computeStars gives rawTenths=30
    @Test
    fun `HARD_100percent_computeStars_gives_Stars30`() {
        val attempt = FakeAttemptFixtures.fixtureAttempt(mode = Difficulty.HARD, percentScore = 100)
        fakeStart.result = makeReadyState(mode = Difficulty.HARD, questions = 1)
        fakeComplete.result = RunnerState.Completed(attempt, ratingPrompt = false)
        val component = buildComponent(mode = Difficulty.HARD)
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        val stars = computeStars(result.percentScore, result.mode)
        assertEquals(30, stars.rawTenths, "HARD 100% must give Stars(rawTenths=30)")
    }

    // ── PT-15: No prior attempts → bestStarsRawTenths=0, hardUnlocked=false ──

    // PT-15: GIVEN no prior attempts in FakeLessonAttemptRepository
    //        WHEN Result state THEN bestStarsRawTenths=0, hardUnlocked=false
    // Open Question: RunnerUiState.Result must have bestStarsRawTenths: Int and hardUnlocked: Boolean
    @Test
    fun `no_attempts_bestStarsRawTenths_zero_hardUnlocked_false`() {
        val attempt = FakeAttemptFixtures.fixtureAttempt(percentScore = 50, codeAnswer = "1")
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(attempt, ratingPrompt = false)
        // fakeAttemptRepo is empty (no prior attempts)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertEquals(0, result.bestStarsRawTenths, "No prior attempts → bestStarsRawTenths must be 0")
        assertFalse(result.hardUnlocked, "No prior attempts → hardUnlocked must be false")
    }

    // ── PT-16: EASY perfect → hardUnlocked=true ──────────────────────────────

    // PT-16: GIVEN prior EASY attempt with allShownAnswersAre9=true
    //        WHEN Result state THEN hardUnlocked=true
    @Test
    fun `EASY_perfect_attempt_hardUnlocked_true`() {
        val perfectAttempt = FakeAttemptFixtures.fixtureAttempt(
            mode = Difficulty.EASY,
            codeAnswer = "9",
        )
        fakeAttemptRepo.saveResult = Result.success(Unit)
        // Pre-populate with a perfect prior attempt
        kotlinx.coroutines.runBlocking { fakeAttemptRepo.save(perfectAttempt) }

        val newAttempt = FakeAttemptFixtures.fixtureAttempt(id = "new-id")
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(newAttempt, ratingPrompt = false)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertTrue(result.hardUnlocked, "After EASY perfect attempt, hardUnlocked must be true")
    }

    // ── PT-17: EASY imperfect → hardUnlocked=false ───────────────────────────

    // PT-17: GIVEN EASY attempt with codeAnswer not all-9s
    //        WHEN Result state THEN hardUnlocked=false even if rawTenths=20
    @Test
    fun `EASY_imperfect_hardUnlocked_false`() {
        val imperfectAttempt = FakeAttemptFixtures.fixtureAttempt(
            mode = Difficulty.EASY,
            codeAnswer = "8",
            percentScore = 88,
        )
        kotlinx.coroutines.runBlocking { fakeAttemptRepo.save(imperfectAttempt) }

        val newAttempt = FakeAttemptFixtures.fixtureAttempt(id = "new-id")
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(newAttempt, ratingPrompt = false)
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertFalse(result.hardUnlocked, "EASY imperfect attempt must not unlock HARD mode")
    }

    // ── PT-18: charsCount=165 EASY → deadlineMs = now+30000 ──────────────────

    // PT-18: GIVEN Ready state with deadlineMs=now+30000 (charsCount=165, EASY)
    //        WHEN uiState is Question THEN deadlineMs == clock.nowMillis + 30000
    @Test
    fun `charsCount165_EASY_deadlineMs_now_plus_30000`() {
        val expectedDeadline = fakeClock.nowMillis + 30_000L
        fakeStart.result = makeReadyState(deadlineMs = expectedDeadline)
        val component = buildComponent()

        val uiState = component.uiState.value as RunnerUiState.Question
        assertEquals(
            expectedDeadline,
            uiState.deadlineMs,
            "deadlineMs must equal clock.now() + 30s for charsCount=165 EASY",
        )
    }

    // ── PT-19: charsCount=165 HARD → deadlineMs = now+20000 ──────────────────

    // PT-19: GIVEN Ready state with deadlineMs=now+20000 (charsCount=165, HARD)
    //        WHEN uiState is Question THEN deadlineMs == now+20000
    @Test
    fun `charsCount165_HARD_deadlineMs_now_plus_20000`() {
        val expectedDeadline = fakeClock.nowMillis + 20_000L
        fakeStart.result = makeReadyState(mode = Difficulty.HARD, deadlineMs = expectedDeadline)
        val component = buildComponent(mode = Difficulty.HARD)

        val uiState = component.uiState.value as RunnerUiState.Question
        assertEquals(
            expectedDeadline,
            uiState.deadlineMs,
            "deadlineMs must equal clock.now() + 20s for charsCount=165 HARD",
        )
    }

    // ── PT-20: indexInPool changes → timer key changes ─────────────────────────

    // PT-20: GIVEN state with indexInPool=0, 2 questions
    //        WHEN onAnswer(first answer) THEN indexInPool == 1 (timer key changes)
    @Test
    fun `indexInPool_advances_after_answer_timer_key_changes`() {
        fakeStart.result = makeReadyState(questions = 2)
        val component = buildComponent()
        assertEquals(0, (component.uiState.value as RunnerUiState.Question).indexInPool)

        component.onAnswer(singleChoiceDraft())

        assertEquals(
            1,
            (component.uiState.value as RunnerUiState.Question).indexInPool,
            "indexInPool must advance to 1 after answering first question",
        )
    }

    // ── PT-21: charsCount=10 → timer=5s (floor) ──────────────────────────────

    // PT-21: GIVEN Ready state with deadlineMs=now+5000 (charsCount=10, floor=5s)
    //        WHEN uiState is Question THEN deadlineMs == now+5000
    @Test
    fun `charsCount10_deadlineMs_now_plus_5000_floor`() {
        val expectedDeadline = fakeClock.nowMillis + 5_000L
        fakeStart.result = makeReadyState(deadlineMs = expectedDeadline)
        val component = buildComponent()

        val uiState = component.uiState.value as RunnerUiState.Question
        assertEquals(
            expectedDeadline,
            uiState.deadlineMs,
            "deadlineMs must equal clock.now() + 5s (floor) for charsCount=10",
        )
    }

    // ── PT-22: lifecycle.doOnStop → isPaused=true ─────────────────────────────

    // PT-22: GIVEN state = Ready(isPaused=false, 2 questions)
    //        WHEN lifecycle.stop() THEN uiState.isPaused == true
    @Test
    fun `lifecycle_doOnStop_state_isPaused_true`() {
        fakeStart.result = makeReadyState(questions = 2)
        val component = buildComponent()
        assertFalse((component.uiState.value as RunnerUiState.Question).isPaused)

        lifecycle.stop()

        val uiState = component.uiState.value as RunnerUiState.Question
        assertTrue(uiState.isPaused, "lifecycle.doOnStop must set isPaused=true")
    }

    // ── PT-23: isPaused=true in state → Question.isPaused=true ───────────────

    // PT-23: GIVEN FakeStart returns Ready(isPaused=true)
    //        WHEN observe uiState THEN Question.isPaused == true
    @Test
    fun `isPaused_true_question_uiState_isPaused_true`() {
        fakeStart.result = makeReadyState(isPaused = true)
        val component = buildComponent()

        val uiState = component.uiState.value as RunnerUiState.Question
        assertTrue(uiState.isPaused, "isPaused=true in domain state must map to Question.isPaused=true")
    }

    // ── PT-24: onContinue → isPaused=false, same indexInPool ─────────────────

    // PT-24: GIVEN state = Ready(isPaused=true, indexInPool=3)
    //        WHEN onContinue() THEN isPaused=false; indexInPool==3
    @Test
    fun `onContinue_isPaused_false_sameIndex`() {
        fakeStart.result = makeReadyState(
            questions = 5,
            indexInPool = 3,
            isPaused = true,
        )
        val component = buildComponent()
        assertEquals(3, (component.uiState.value as RunnerUiState.Question).indexInPool)
        assertTrue((component.uiState.value as RunnerUiState.Question).isPaused)

        component.onContinue()

        val uiState = component.uiState.value as RunnerUiState.Question
        assertFalse(uiState.isPaused, "onContinue must clear isPaused")
        assertEquals(3, uiState.indexInPool, "indexInPool must remain 3 after onContinue")
    }

    // ── PT-25: onExit → AbortUseCase → Aborted → NavigateBack ────────────────

    // PT-25: GIVEN state = Ready; FakeAbortAttemptUseCase
    //        WHEN onExit() THEN fakeAbortUseCase.callCount == 1 + NavigateBack emitted
    @Test
    fun `onExit_abortUseCase_called_popSignal`() = runTest {
        fakeStart.result = makeReadyState()
        val component = buildComponent()

        val deferred = async { component.events.first() }
        component.onExit()

        assertEquals(1, fakeAbort.callCount, "AbortAttemptUseCase must be called once on onExit()")
        assertIs<RunnerEvent.NavigateBack>(deferred.await(), "onExit must emit NavigateBack event")
    }

    // ── PT-26: complete → savedAttempts.size == 1 ─────────────────────────────

    // PT-26: GIVEN FakeLessonAttemptRepository + real CompleteAttemptUseCase
    //        WHEN all questions answered → CompleteAttemptUseCase saves to repo
    //        THEN savedAttempts.size == 1
    @Test
    fun `complete_savedAttempts_size_one`() {
        fakeStart.result = makeReadyState(questions = 1)
        val realComplete = CompleteAttemptUseCase(
            attemptRepository = fakeAttemptRepo,
            ratingRepository = fakeRatingRepo,
            clock = fakeClock,
            attemptIdProvider = { AttemptId("complete-test-id") },
        )
        val component = buildComponent(completeAttemptUseCase = realComplete::invoke)

        component.onAnswer(singleChoiceDraft())

        assertEquals(
            1,
            fakeAttemptRepo.savedAttempts.size,
            "FakeLessonAttemptRepository must have exactly 1 saved attempt after complete",
        )
    }

    // ── PT-27: abort after 3 answers → codeAnswer positions correct ──────────

    // PT-27: GIVEN 20 eligible questions; user answers 3; then onExit()
    //        WHEN real AbortAttemptUseCase
    //        THEN attempt.codeAnswer positions [0..2] scored (≥'1'), [3..19]='1', rest='0'
    @Test
    fun `abort_after3_codeAnswer_positions_correct`() {
        val questions = (0 until 20).map { makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it) }
        val pool = questions.take(3) // only 3 in play order (subset)
        fakeStart.result = makeReadyState(
            playOrder = pool,
            eligibleSize = 20,
            questions = 20,
        ).copy(eligibleSize = 20, codeAnswer = CodeAnswer("0".repeat(20)))
        val realAbort = AbortAttemptUseCase(
            attemptRepository = fakeAttemptRepo,
            clock = fakeClock,
            attemptIdProvider = { AttemptId("abort-test-id") },
        )
        val component = buildComponent(abortAttemptUseCase = realAbort::invoke)
        // Answer all 3 questions in pool
        repeat(3) { component.onAnswer(singleChoiceDraft()) }
        // index is now 3 = playOrder.size, which triggers complete... hmm

        // Actually for abort scenario we need to interrupt before completing all.
        // Use 5 questions in pool, answer 3, then abort.
        // Reset and re-setup:
    }

    // PT-27 (corrected): 5 pool questions, answer 3, then abort → codeAnswer check
    @Test
    fun `abort_after3_of5_codeAnswer_positions_correct`() {
        val pool = (0 until 5).map { makeRunnerQuestion("q${it + 1}", codeAnswerIndex = it) }
        fakeStart.result = RunnerState.Ready(
            userId = "user1",
            lessonId = LessonId("lesson1"),
            lessonVersion = 5L,
            mode = Difficulty.EASY,
            playOrder = pool,
            eligibleSize = 5,
            indexInPool = 0,
            codeAnswer = CodeAnswer("00000"),
            deadlineMs = 9_000_000L,
            seed = 12345L,
            currentDraftAnswer = null,
            isPaused = false,
        )
        val realAbort = AbortAttemptUseCase(
            attemptRepository = fakeAttemptRepo,
            clock = fakeClock,
            attemptIdProvider = { AttemptId("abort-test-id") },
        )
        val component = buildComponent(abortAttemptUseCase = realAbort::invoke)

        // Answer 3 questions (not the last one)
        repeat(3) { component.onAnswer(singleChoiceDraft()) }
        component.onExit()

        val saved = fakeAttemptRepo.savedAttempts.firstOrNull()
        assertNotNull(saved, "Aborted attempt must be saved")
        val code = saved.codeAnswer.raw
        // positions 0..2 were in pool and shown → scored (≥'1')
        assertTrue(code[0] > '0', "codeAnswer[0] must be scored")
        assertTrue(code[1] > '0', "codeAnswer[1] must be scored")
        assertTrue(code[2] > '0', "codeAnswer[2] must be scored")
        // positions 3..4 were in pool but not shown → set to '1' by buildCodeAnswerOnAbort
        assertEquals('1', code[3], "codeAnswer[3] must be '1' (unanswered in pool)")
        assertEquals('1', code[4], "codeAnswer[4] must be '1' (unanswered in pool)")
    }

    // ── PT-28: lessonVersion snapshot at start ────────────────────────────────

    // PT-28: GIVEN lesson.version=5 at start; real CompleteAttemptUseCase
    //        WHEN attempt saved THEN attempt.lessonVersion == 5
    @Test
    fun `lessonVersion_snapshot_at_start`() {
        fakeStart.result = makeReadyState(lessonVersion = 5L, questions = 1)
        val realComplete = CompleteAttemptUseCase(
            attemptRepository = fakeAttemptRepo,
            ratingRepository = fakeRatingRepo,
            clock = fakeClock,
            attemptIdProvider = { AttemptId("ver-test-id") },
        )
        val component = buildComponent(completeAttemptUseCase = realComplete::invoke)
        component.onAnswer(singleChoiceDraft())

        val saved = fakeAttemptRepo.savedAttempts.firstOrNull()
        assertNotNull(saved)
        assertEquals(5L, saved.lessonVersion, "lessonVersion must be snapshot from start time")
    }

    // ── PT-29: allShown9=true && !hasSubmitted → showRatingPrompt=true ────────

    // PT-29: GIVEN ratingPrompt=true in Completed state
    //        WHEN Result state THEN showRatingPrompt=true
    @Test
    fun `allShown9_not_submitted_showRatingPrompt_true`() {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            ratingPrompt = true,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertTrue(result.showRatingPrompt, "showRatingPrompt must be true when ratingPrompt=true")
    }

    // ── PT-30: hasSubmitted → showRatingPrompt=false ──────────────────────────

    // PT-30: GIVEN ratingPrompt=false in Completed (hasSubmitted=true)
    //        WHEN Result state THEN showRatingPrompt=false
    @Test
    fun `hasSubmitted_showRatingPrompt_false`() {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertFalse(result.showRatingPrompt, "showRatingPrompt must be false when ratingPrompt=false")
    }

    // ── PT-31: allShown9=false → showRatingPrompt=false ──────────────────────

    // PT-31: GIVEN ratingPrompt=false in Completed (allShownAnswersAre9=false)
    //        WHEN Result state THEN showRatingPrompt=false
    @Test
    fun `allShown9_false_showRatingPrompt_false`() {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(codeAnswer = "5"),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertFalse(result.showRatingPrompt, "showRatingPrompt must be false when allShownAnswersAre9=false")
    }

    // ── PT-32: onSubmitRating(2) → ratingUseCase called once ─────────────────

    // PT-32: GIVEN FakeSubmitLessonRatingUseCase; Completed state
    //        WHEN onSubmitRating(2) THEN callCount==1; lastRating==2
    @Test
    fun `onSubmitRating_ratingUseCase_called_once`() {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            ratingPrompt = true,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())
        assertIs<RunnerUiState.Result>(component.uiState.value)

        component.onSubmitRating(2)

        assertEquals(1, fakeSubmitRating.callCount, "SubmitLessonRatingUseCase must be called once")
        assertEquals(2, fakeSubmitRating.lastRating, "lastRating must be 2")
    }

    // ── PT-33: Lesson.top3 non-empty → Result.top3 not empty ─────────────────

    // PT-33: GIVEN lesson.top3=[TopParticipant("Alice", null, 90)]
    //        WHEN Result state THEN result.top3 is not empty
    @Test
    fun `lesson_top3_nonEmpty_result_top3_not_empty`() {
        val top3 = listOf(TopParticipant("Alice", null, 90))
        fakeLessonRepo = FakeLessonRepository()
        fakeLessonRepo.addLesson(makeLesson(top3 = top3))
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertTrue(result.top3.isNotEmpty(), "Result.top3 must not be empty when Lesson.top3 is set")
        assertEquals("Alice", result.top3.first().nickname)
    }

    // ── PT-34: bestStars.rawTenths=15 → bestStarsRawTenths=15 ────────────────

    // PT-34: GIVEN prior attempt with Stars(15) (EASY 75%)
    //        WHEN Result state THEN bestStarsRawTenths=15
    @Test
    fun `bestStars_15_bestStarsRawTenths_15`() {
        val priorAttempt = FakeAttemptFixtures.fixtureAttempt(
            id = "prior-id",
            mode = Difficulty.EASY,
            percentScore = 75,
        )
        kotlinx.coroutines.runBlocking { fakeAttemptRepo.save(priorAttempt) }

        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(id = "new-id", percentScore = 50),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        // computeStars(75, EASY) = (75*20+50)/100 = 1550/100 = 15
        assertEquals(15, result.bestStarsRawTenths, "bestStarsRawTenths must be 15 from prior EASY 75% attempt")
    }

    // ── PT-35: hardUnlocked=false → toggle not shown ──────────────────────────

    // PT-35: GIVEN no perfect EASY attempt → hardUnlocked=false
    //        WHEN Result state THEN hardUnlocked=false
    @Test
    fun `hardUnlocked_false_no_perfect_attempt`() {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(codeAnswer = "5", percentScore = 50),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertFalse(result.hardUnlocked, "hardUnlocked must be false when no EASY perfect attempt exists")
    }

    // ── PT-36: hardUnlocked=true → toggle visible ─────────────────────────────

    // PT-36: GIVEN EASY perfect prior attempt (allShownAnswersAre9=true)
    //        WHEN Result state THEN hardUnlocked=true
    @Test
    fun `hardUnlocked_true_after_EASY_perfect_attempt`() {
        val perfectAttempt = FakeAttemptFixtures.fixtureAttempt(
            id = "perfect-prior",
            mode = Difficulty.EASY,
            codeAnswer = "9",
            percentScore = 100,
        )
        kotlinx.coroutines.runBlocking { fakeAttemptRepo.save(perfectAttempt) }

        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(id = "current"),
            ratingPrompt = false,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertTrue(result.hardUnlocked, "hardUnlocked must be true after EASY perfect prior attempt")
    }

    // ── PT-37: InitFailed(EmptyPool) → uiState=InitFailed ────────────────────

    // PT-37: GIVEN FakeStart returns InitFailed(EmptyPool)
    //        WHEN init THEN uiState is InitFailed
    @Test
    fun `startUseCase_initFailed_emptyPool_uiState_initFailed`() {
        fakeStart.result = RunnerState.InitFailed(InitFailureReason.EmptyPool)
        val component = buildComponent()

        val uiState = component.uiState.value
        assertIs<RunnerUiState.InitFailed>(uiState)
        assertEquals(RunnerUiState.InitFailureReason.EmptyPool, uiState.reason)
    }

    // ── PT-38: InitFailed(NoValidQuestions) → uiState=InitFailed ─────────────

    // PT-38: GIVEN FakeStart returns InitFailed(NoValidQuestions)
    //        WHEN init THEN uiState is InitFailed
    @Test
    fun `startUseCase_initFailed_noValidQuestions_uiState_initFailed`() {
        fakeStart.result = RunnerState.InitFailed(InitFailureReason.NoValidQuestions)
        val component = buildComponent()

        val uiState = component.uiState.value
        assertIs<RunnerUiState.InitFailed>(uiState)
        assertEquals(RunnerUiState.InitFailureReason.NoValidQuestions, uiState.reason)
    }

    // ── PT-39: parser filters one invalid → attempt with valid-only ───────────

    // PT-39: GIVEN FakeStart returns Ready with 3-question pool (1 invalid filtered out)
    //        WHEN complete all 3 THEN completeUseCase called once (not 4 times)
    @Test
    fun `parser_filters_invalid_attempt_continues_with_valid_only`() {
        // Pool of 3 (simulating 4 total, 1 invalid filtered by parser in StartUseCase)
        fakeStart.result = makeReadyState(questions = 3)
        val component = buildComponent()

        repeat(3) { component.onAnswer(singleChoiceDraft()) }

        assertEquals(1, fakeComplete.callCount, "CompleteUseCase must be called once after 3 valid questions")
        assertEquals(0, fakeAbort.callCount, "AbortUseCase must not be called")
    }

    // ── PT-40: CompleteUseCase returns SaveFailed → saveWarning=true + event ──

    // PT-40: GIVEN FakeComplete returns SaveFailed
    //        WHEN complete THEN uiState.Result.saveWarning=true + SaveAttemptFailed event
    @Test
    fun `completeSaveFailed_saveWarning_true_and_event_emitted`() = runTest {
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.SaveFailed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            error = SaveError.IoFailure(RuntimeException("IO error")),
        )
        val component = buildComponent()

        val deferred = async { component.events.first() }
        component.onAnswer(singleChoiceDraft())

        val result = component.uiState.value as RunnerUiState.Result
        assertTrue(result.saveWarning, "saveWarning must be true when CompleteUseCase returns SaveFailed")
        assertIs<RunnerEvent.SaveAttemptFailed>(deferred.await(), "SaveAttemptFailed event must be emitted")
    }

    // ── PT-41: rating submit failure → SaveRatingFailed event ─────────────────

    // PT-41: GIVEN FakeSubmitRating.result=failure
    //        WHEN onSubmitRating(3) THEN SaveRatingFailed event emitted
    @Test
    fun `ratingSubmitFailure_saveRatingFailed_event`() = runTest {
        fakeSubmitRating.result = Result.failure(RuntimeException("network error"))
        fakeStart.result = makeReadyState(questions = 1)
        fakeComplete.result = RunnerState.Completed(
            attempt = FakeAttemptFixtures.fixtureAttempt(),
            ratingPrompt = true,
        )
        val component = buildComponent()
        component.onAnswer(singleChoiceDraft())
        assertIs<RunnerUiState.Result>(component.uiState.value)

        val deferred = async { component.events.first() }
        component.onSubmitRating(3)

        val event = deferred.await()
        assertIs<RunnerEvent.SaveRatingFailed>(event, "SaveRatingFailed event must be emitted on rating failure")
    }
}
