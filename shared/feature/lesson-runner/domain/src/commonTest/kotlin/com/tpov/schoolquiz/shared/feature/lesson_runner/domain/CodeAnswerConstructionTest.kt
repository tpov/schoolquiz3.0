package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Spec scenarios 13-16: CodeAnswer construction — length, non-zero positions, mode filtering.
 * Tests full pipeline: StartLessonAttemptUseCase → RunnerState.Ready.codeAnswer.
 */
class CodeAnswerConstructionTest {

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

    private fun addEasyQuestion(id: String, lessonId: String = "lesson1", order: Int = 0) {
        val q = makeQuestion(id = id, lessonId = lessonId, order = order, payload = "easy_$id")
        parser.addResponse(q.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questionRepo.addQuestion(q)
    }

    private fun addHardQuestion(id: String, lessonId: String = "lesson1", order: Int = 0) {
        val q = makeQuestion(id = id, lessonId = lessonId, order = order, payload = "hard_$id")
        parser.addResponse(q.payload, singleChoiceContent(difficulty = Difficulty.HARD))
        questionRepo.addQuestion(q)
    }

    // ── Test 13: 5 EASY questions ─────────────────────────────────────────────

    @Test
    fun `given lesson with 5 EASY questions when EASY attempt then codeAnswer length 5 and all in pool`() = runTest {
        // Spec scenario #13: eligible=5 < poolSize=20 → all 5 in subset; codeAnswer.length=5
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        (1..5).forEach { addEasyQuestion("q$it", order = it) }

        val state = startUseCase(LessonId("lesson1"), Difficulty.EASY) as RunnerState.Ready

        assertEquals(5, state.eligibleSize, "Spec scenario #13: eligibleSize = 5")
        assertEquals(5, state.codeAnswer.raw.length, "Spec scenario #13: codeAnswer.length = 5")
        assertEquals(5, state.playOrder.size, "Spec scenario #13: all 5 in pool (min(20,5)=5)")

        // Answer all 5 questions to verify no '0' after completion
        var currentState = state
        repeat(5) {
            val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
            currentState = submitAnswer(currentState, answer, nowMs = 1_000_000L)
        }
        assertTrue(
            currentState.codeAnswer.raw.none { it == '0' },
            "Spec scenario #13: нет '0' after all 5 shown and answered",
        )
    }

    // ── Test 14: 50 EASY questions → 20 in pool, 30 out ─────────────────────

    @Test
    fun `given lesson with 50 EASY questions when EASY attempt then codeAnswer length 50 with 20 non-zero after completion`() = runTest {
        // Spec scenario #14: eligible=50, poolSize=20 → 20 in subset, 30 '0'
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        (1..50).forEach { addEasyQuestion("q${it}", order = it) }

        val state = startUseCase(LessonId("lesson1"), Difficulty.EASY) as RunnerState.Ready

        assertEquals(50, state.eligibleSize, "Spec scenario #14: eligibleSize = 50")
        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #14: codeAnswer.length = 50")
        assertEquals(20, state.playOrder.size, "Spec scenario #14: subset = 20")

        // Simulate answering all 20 subset questions
        var currentState = state
        repeat(20) {
            val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
            currentState = submitAnswer(currentState, answer, nowMs = 1_000_000L)
        }

        val nonZero = currentState.codeAnswer.raw.count { it != '0' }
        val zeros = currentState.codeAnswer.raw.count { it == '0' }
        assertEquals(20, nonZero, "Spec scenario #14: exactly 20 non-zero digits (shown questions)")
        assertEquals(30, zeros, "Spec scenario #14: 30 '0' digits (out-of-subset)")
    }

    // ── Test 15: 50 EASY + 50 HARD, EASY attempt ─────────────────────────────

    @Test
    fun `given lesson with 50 EASY and 50 HARD when EASY attempt then codeAnswer length 50 mode EASY`() = runTest {
        // Spec scenario #15: EASY attempt filters only EASY questions; codeAnswer.length = 50
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        (1..50).forEach { addEasyQuestion("e$it", order = it) }
        (1..50).forEach { addHardQuestion("h$it", order = it + 50) }

        val state = startUseCase(LessonId("lesson1"), Difficulty.EASY) as RunnerState.Ready

        assertEquals(50, state.eligibleSize, "Spec scenario #15: only EASY counted → eligibleSize=50")
        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #15: codeAnswer.length=50")
        assertEquals(Difficulty.EASY, state.mode, "Spec scenario #15: mode=EASY")
    }

    // ── Test 16: 50 EASY + 50 HARD, HARD attempt ─────────────────────────────

    @Test
    fun `given lesson with 50 EASY and 50 HARD when HARD attempt then codeAnswer length 50 mode HARD`() = runTest {
        // Spec scenario #16: HARD attempt filters only HARD questions; codeAnswer.length = 50
        lessonRepo.addLesson(makeLesson(id = "lesson1"))
        (1..50).forEach { addEasyQuestion("e$it", order = it) }
        (1..50).forEach { addHardQuestion("h$it", order = it + 50) }

        val state = startUseCase(LessonId("lesson1"), Difficulty.HARD) as RunnerState.Ready

        assertEquals(50, state.eligibleSize, "Spec scenario #16: only HARD counted → eligibleSize=50")
        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #16: codeAnswer.length=50")
        assertEquals(Difficulty.HARD, state.mode, "Spec scenario #16: mode=HARD")
    }
}
