package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Spec scenarios 55-57: lessonVersion fixation at attempt start and rating submit.
 *
 * Business Rule 17: attempt.lessonVersion = lesson.version at StartLessonAttemptUseCase invocation.
 * Business Rule 25: rating.lessonVersion = lesson.version at submit time (fresh read).
 */
class LessonVersionTest {

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

    private val lessonId = LessonId("lesson1")

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questionRepo,
        lessonRepository = lessonRepo,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
    )

    private val completeUseCase = CompleteAttemptUseCase(
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

    private fun setupLesson(version: Long) {
        val lesson = makeLesson(id = "lesson1", version = version)
        lessonRepo.addLesson(lesson)
    }

    private fun setupQuestion() {
        val q = makeQuestion(id = "q1", lessonId = "lesson1", order = 0)
        val content = singleChoiceContent(difficulty = Difficulty.EASY)
        parser.addResponse(q.payload, content)
        questionRepo.addQuestion(q)
    }

    // ── Test 55 ───────────────────────────────────────────────────────────────

    @Test
    fun `given lesson version 5 when StartLessonAttemptUseCase then attempt lessonVersion 5`() = runTest {
        // Spec scenario #55: attempt.lessonVersion == lesson.version at start
        setupLesson(version = 5L)
        setupQuestion()

        val readyState = startUseCase(lessonId, Difficulty.EASY)
        assertIs<RunnerState.Ready>(readyState)
        assertEquals(5L, readyState.lessonVersion, "Spec scenario #55: lessonVersion snapshot = 5")

        // Complete to get Attempt
        val finalState = makeReadyState(
            lessonId = "lesson1",
            lessonVersion = readyState.lessonVersion,
            codeAnswer = CodeAnswer("9"),
            eligibleSize = 1,
            playOrder = emptyList(),
            indexInPool = 1,
        )
        val completed = completeUseCase(finalState) as RunnerState.Completed
        assertEquals(5L, completed.attempt.lessonVersion, "Spec scenario #55: saved attempt.lessonVersion == 5")
    }

    // ── Test 56 ───────────────────────────────────────────────────────────────

    @Test
    fun `given lesson version changes during session then attempt preserves start version`() = runTest {
        // Spec scenario #56: version=5 at start; lesson updated to version=6 during session; attempt.lessonVersion=5
        setupLesson(version = 5L)
        setupQuestion()

        val readyState = startUseCase(lessonId, Difficulty.EASY) as RunnerState.Ready
        assertEquals(5L, readyState.lessonVersion)

        // Simulate lesson version update (sync during session)
        lessonRepo.addLesson(makeLesson(id = "lesson1", version = 6L))

        // RunnerState.Ready still holds the old lessonVersion (snapshot)
        val finalState = makeReadyState(
            lessonId = "lesson1",
            lessonVersion = readyState.lessonVersion,
            codeAnswer = CodeAnswer("9"),
            eligibleSize = 1,
            playOrder = emptyList(),
            indexInPool = 1,
        )
        val completed = completeUseCase(finalState) as RunnerState.Completed
        assertEquals(5L, completed.attempt.lessonVersion, "Spec scenario #56: lessonVersion = 5 (snapshot), not 6")
    }

    // ── Test 57 ───────────────────────────────────────────────────────────────

    @Test
    fun `given lesson version 7 when rating submitted then rating lessonVersion 7`() = runTest {
        // Spec scenario #57: rating.lessonVersion = fresh lesson.version at submit time
        setupLesson(version = 7L)

        val result = submitRatingUseCase("user1", lessonId, rating = 2)
        assertEquals(true, result.isSuccess, "submit should succeed")

        val submitted = ratingRepo.submittedRatings.first()
        assertEquals(7L, submitted.lessonVersion, "Spec scenario #57: rating.lessonVersion = 7 (fresh read)")
    }
}
