package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Spec scenarios 72-74a: Failure semantics.
 * Tests that errors are handled correctly without retries or corrupt state.
 */
class FailureSemanticsTest {

    private val clock = FakeClock()
    private val authRepo = FakeAuthRepository(uid = "user1")
    private val lessonRepo = FakeLessonRepository()
    private val questionRepo = FakeQuestionRepository()
    private val parser = FakeQuestionContentParser()
    private val attemptRepo = FakeLessonAttemptRepository()
    private val ratingRepo = FakeLessonRatingRepository()
    private var idCounter = 0
    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
    private val ratingIdProvider = { userId: String, lessonId: LessonId -> RatingId("$userId:${lessonId.value}") }

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questionRepo,
        lessonRepository = lessonRepo,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
    )

    private val completeUseCase get() = CompleteAttemptUseCase(
        attemptRepository = attemptRepo,
        ratingRepository = ratingRepo,
        clock = clock,
        attemptIdProvider = attemptIdProvider,
    )

    private val submitRatingUseCase get() = SubmitLessonRatingUseCase(
        ratingRepository = ratingRepo,
        lessonRepository = lessonRepo,
        clock = clock,
        ratingIdProvider = ratingIdProvider,
    )

    // ── Test 72 ───────────────────────────────────────────────────────────────

    @Test
    fun `given save throws Room IO exception when CompleteAttemptUseCase then returns SaveFailed and no persist`() = runTest {
        // Spec scenario #72: save fails → SaveFailed state; attempt NOT saved; no retry
        val ioError = RuntimeException("Room IO failure")
        attemptRepo.saveResult = Result.failure(ioError)

        val state = makeReadyState(
            codeAnswer = CodeAnswer("9"),
            eligibleSize = 1,
            playOrder = emptyList(),
            indexInPool = 1,
        )
        val result = completeUseCase(state)

        assertIs<RunnerState.SaveFailed>(result, "Spec scenario #72: save failure → SaveFailed state")
        assertEquals(1, attemptRepo.saveCallCount, "save called once (no retry)")
        assertTrue(attemptRepo.savedAttempts.isEmpty(), "attempt NOT persisted on failure")
    }

    // ── Test 73 ───────────────────────────────────────────────────────────────

    @Test
    fun `given ratingRepo submit throws when SubmitLessonRatingUseCase then returns failure and hasSubmitted false`() = runTest {
        // Spec scenario #73: submit fails → Result.failure; local flag NOT set
        lessonRepo.addLesson(makeLesson(id = "lesson1", version = 1L))
        ratingRepo.submitResult = Result.failure(RuntimeException("network error"))

        val result = submitRatingUseCase("user1", LessonId("lesson1"), rating = 2)

        assertTrue(result.isFailure, "Spec scenario #73: submit failure → Result.failure")
        val hasSubmitted = ratingRepo.hasSubmitted("user1", LessonId("lesson1")).first()
        assertFalse(hasSubmitted, "Spec scenario #73: local flag NOT set on failure")
    }

    // ── Test 74 ───────────────────────────────────────────────────────────────

    @Test
    fun `given authRepository currentUid returns null when StartLessonAttemptUseCase then InitFailed AuthRequired`() = runTest {
        // Spec scenario #74
        authRepo.setUid(null)
        lessonRepo.addLesson(makeLesson(id = "lesson1", version = 1L))

        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result, "Spec scenario #74: null uid → InitFailed")
        assertEquals(
            InitFailureReason.AuthRequired,
            result.reason,
            "Spec scenario #74: reason = AuthRequired",
        )
    }

    // ── Test 74a ──────────────────────────────────────────────────────────────

    @Test
    fun `given lessonRepository getById returns null when StartLessonAttemptUseCase then InitFailed LessonNotFound`() = runTest {
        // Spec scenario #74a: lesson not found → InitFailed(LessonNotFound)
        // lessonRepo has no lesson for "nonexistent"
        val result = startUseCase(LessonId("nonexistent"), Difficulty.EASY)

        assertIs<RunnerState.InitFailed>(result, "Spec scenario #74a: lesson not found → InitFailed")
        assertEquals(
            InitFailureReason.LessonNotFound,
            result.reason,
            "Spec scenario #74a: reason = LessonNotFound",
        )
    }
}
