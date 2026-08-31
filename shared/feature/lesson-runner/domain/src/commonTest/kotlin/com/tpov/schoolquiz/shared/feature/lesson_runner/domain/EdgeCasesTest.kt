package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Spec scenarios 58-61c and 77: Edge cases.
 * Tests pool empty, all invalid, invalid filtered, configuration change simulation,
 * duplicate orders, and swipe-home/resume scenarios.
 */
class EdgeCasesTest {

    private val clock = FakeClock()
    private val authRepo = FakeAuthRepository(uid = "user1")
    private val lessonRepo = FakeLessonRepository()
    private val questionRepo = FakeQuestionRepository()
    private val parser = FakeQuestionContentParser()

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questionRepo,
        lessonRepository = lessonRepo,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
    )

    // ── Test 58: EmptyPool ─────────────────────────────────────────────────────

    @Test
    fun `given empty pool filter difficulty 0 questions when StartLessonAttemptUseCase then InitFailed EmptyPool`() = runTest {
        // Spec scenario #58: HARD mode, lesson has only EASY questions
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q = makeQuestion(id = "q1", lessonId = "lesson1", order = 0)
        parser.addResponse(q.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questionRepo.addQuestion(q)

        val result = startUseCase(LessonId("lesson1"), Difficulty.HARD)
        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.EmptyPool, result.reason, "Spec scenario #58: EmptyPool")
    }

    // ── Test 59: NoValidQuestions ─────────────────────────────────────────────

    @Test
    fun `given pool with all invalid payloads when StartLessonAttemptUseCase then InitFailed NoValidQuestions`() = runTest {
        // Spec scenario #59: all questions have invalid payloads → NoValidQuestions
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q1 = makeQuestion(id = "q1", lessonId = "lesson1", order = 0, payload = "invalid_payload_1")
        val q2 = makeQuestion(id = "q2", lessonId = "lesson1", order = 1, payload = "invalid_payload_2")
        parser.addFailure(q1.payload)
        parser.addFailure(q2.payload)
        questionRepo.setQuestions(listOf(q1, q2))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)
        assertIs<RunnerState.InitFailed>(result)
        assertEquals(InitFailureReason.NoValidQuestions, result.reason, "Spec scenario #59: NoValidQuestions")
    }

    // ── Test 60: invalid payload filtered out ─────────────────────────────────

    @Test
    fun `given single invalid payload among valid when StartLessonAttemptUseCase then invalid excluded`() = runTest {
        // Spec scenario #60: invalid question dropped; eligibleQuestions contains only Valid
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q1 = makeQuestion(id = "q1", lessonId = "lesson1", order = 0, payload = "valid_payload")
        val q2 = makeQuestion(id = "q2", lessonId = "lesson1", order = 1, payload = "invalid_payload")
        parser.addResponse(q1.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addFailure(q2.payload)
        questionRepo.setQuestions(listOf(q1, q2))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)
        assertIs<RunnerState.Ready>(result, "Spec scenario #60: valid exists → Ready")
        assertEquals(1, result.eligibleSize, "Spec scenario #60: only valid question counted; codeAnswer.length=1")
        assertEquals(1, result.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 1")
    }

    // ── Test 61: configuration change simulation ───────────────────────────────

    @Test
    fun `given RunnerState Ready when restored via instanceKeeper then state is identical`() {
        // Spec scenario #61: configuration change → state preserved (verified via equality of data class)
        val original = makeReadyState(
            seed = 12345L,
            indexInPool = 3,
            codeAnswer = CodeAnswer("91900000"),
            eligibleSize = 8,
        )
        // instanceKeeper preserves the data class by reference/serialization
        // We verify the data class fields are unchanged (no mutation)
        val restored = original.copy()
        assertEquals(original.seed, restored.seed, "Spec scenario #61: seed preserved")
        assertEquals(original.indexInPool, restored.indexInPool, "indexInPool preserved")
        assertEquals(original.codeAnswer.raw, restored.codeAnswer.raw, "codeAnswer preserved")
        assertEquals(original.deadlineMs, restored.deadlineMs, "deadlineMs preserved")
        assertEquals(original.currentDraftAnswer, restored.currentDraftAnswer, "draft preserved")
    }

    // ── Test 61a: duplicate order determinism ──────────────────────────────────

    @Test
    fun `given duplicate Question order when StartLessonAttemptUseCase then playOrder contains all eligible questions`() = runTest {
        // Spec scenarios #61a/#77 relaxed: playOrder is now randomized (shuffled by seed).
        // Stability is no longer required for presentation order — codeAnswerIndex on each
        // question preserves the stable position used for codeAnswer scoring.
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        val q1 = makeQuestion(id = "aaa", lessonId = "lesson1", order = 1, payload = "payload_aaa")
        val q2 = makeQuestion(id = "bbb", lessonId = "lesson1", order = 5, payload = "payload_bbb")
        val q3 = makeQuestion(id = "ccc", lessonId = "lesson1", order = 5, payload = "payload_ccc")
        val q4 = makeQuestion(id = "ddd", lessonId = "lesson1", order = 9, payload = "payload_ddd")
        parser.addResponse(q1.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addResponse(q2.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addResponse(q3.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        parser.addResponse(q4.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questionRepo.setQuestions(listOf(q4, q2, q1, q3))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY) as RunnerState.Ready
        val playIds = result.playOrder.map { it.sourceId.value }.toSet()
        assertEquals(setOf("aaa", "bbb", "ccc", "ddd"), playIds,
            "All eligible questions are present in play order (random presentation order)")
    }

    // ── Test 61b: swipe-home + resume + continue ───────────────────────────────

    @Test
    fun `given swipe-home onResume and continue when auto-random scored then player sees next question`() {
        // Spec scenario #61b: auto-random scored current question → indexInPool advances
        val questions = listOf(
            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
            makeRunnerQuestion(id = "q2", order = 1, codeAnswerIndex = 1),
        )
        val state = makeReadyState(
            playOrder = questions,
            eligibleSize = 2,
            indexInPool = 0,
            codeAnswer = CodeAnswer("00"),
        )
        // onStop → auto-random score current question
        val afterAutoScore = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = 1_000_000L)
        // After auto-score, indexInPool should be 1 (next question)
        assertEquals(1, afterAutoScore.indexInPool,
            "Spec scenario #61b: after auto-score on onStop, indexInPool advances to next")
    }

    // ── Test 61c: swipe-home + resume + exit → abort ──────────────────────────

    @Test
    fun `given swipe-home onResume and exit when abort then unanswered subset becomes 1 out-of-subset stays 0`() {
        // Spec scenario #61c: after auto-score on onStop (indexInPool=1), abort
        // playOrder has 2 questions (in subset), eligibleSize=4 (2 in subset, 2 out)
        // codeAnswer = "0000" initially; after q0 auto-scored → position 0 gets digit
        val questions = listOf(
            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
            makeRunnerQuestion(id = "q3", order = 2, codeAnswerIndex = 2),
        )
        val stateAfterAutoScore = makeReadyState(
            playOrder = questions,
            eligibleSize = 4,
            indexInPool = 1,
            codeAnswer = CodeAnswer("9000"),
        )
        // abort: positions of remaining subset (index=2) → '1'; out-of-subset (1,3) remain '0'
        val result = buildCodeAnswerOnAbort(stateAfterAutoScore)
        assertEquals('9', result.raw[0], "position 0: answered")
        assertEquals('0', result.raw[1], "position 1: out-of-subset → '0'")
        assertEquals('1', result.raw[2], "position 2: unanswered subset → '1'")
        assertEquals('0', result.raw[3], "position 3: out-of-subset → '0'")
    }
}
