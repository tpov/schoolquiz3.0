package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class AttemptIdentityTest {
    private val clock = FakeClock()
    private val auth = FakeAuthRepository("player")
    private val lessons = FakeLessonRepository()
    private val questions = FakeQuestionRepository()
    private val parser = FakeQuestionContentParser()
    private val attempts = FakeLessonAttemptRepository()
    private var identitiesCreated = 0
    private val start = StartLessonAttemptUseCase(
        questionRepository = questions,
        lessonRepository = lessons,
        parser = parser,
        authRepository = auth,
        clock = clock,
        randomSeedProvider = { 42L },
        attemptIdProvider = { AttemptId("started-${++identitiesCreated}") },
    )
    private val complete = CompleteAttemptUseCase(attempts, clock)
    private val abort = AbortAttemptUseCase(attempts, clock)

    private fun prepareLesson() {
        lessons.addLesson(makeLesson(id = "lesson"))
        val question = makeQuestion(id = "question", lessonId = "lesson", order = 0)
        parser.addResponse(question.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questions.addQuestion(question)
    }

    @Test
    fun successfulStartCreatesOneIdentityBeforeAnyTerminalAction() = runTest {
        prepareLesson()
        val first = assertIs<RunnerState.Ready>(start(LessonId("lesson"), Difficulty.EASY))
        assertEquals(AttemptId("started-1"), first.attemptId)
        assertEquals(1, identitiesCreated)
        assertEquals(0, attempts.saveCallCount)
        assertEquals(first.attemptId, first.copy(isPaused = true).copy(isPaused = false).attemptId)

        val second = assertIs<RunnerState.Ready>(start(LessonId("lesson"), Difficulty.EASY))
        assertNotEquals(first.attemptId, second.attemptId)
        assertEquals(2, identitiesCreated)
    }

    @Test
    fun failedStartDoesNotAllocateAnIdentity() = runTest {
        auth.setUid(null)
        assertIs<RunnerState.InitFailed>(start(LessonId("lesson"), Difficulty.EASY))
        assertEquals(0, identitiesCreated)
    }

    @Test
    fun completionUsesStartedIdentityAndExplicitCompletedIntentEvenAfterSaveFailure() = runTest {
        prepareLesson()
        val ready = assertIs<RunnerState.Ready>(start(LessonId("lesson"), Difficulty.EASY))
            .copy(codeAnswer = CodeAnswer("9"), indexInPool = 1)
        attempts.saveResult = Result.failure(IllegalStateException("disk unavailable"))
        val failed = assertIs<RunnerState.SaveFailed>(complete(ready))
        assertEquals(ready.attemptId, failed.attempt.id)
        assertEquals(AttemptCompletionKind.COMPLETED, attempts.lastSave?.completionKind)

        attempts.saveResult = Result.success(Unit)
        val result = assertIs<RunnerState.Completed>(complete(ready))
        assertEquals(ready.attemptId, result.attempt.id)
        assertEquals(AttemptCompletionKind.COMPLETED, attempts.lastSave?.completionKind)
        assertEquals(1, identitiesCreated)
    }

    @Test
    fun abortUsesStartedIdentityAndExplicitAbortedIntent() = runTest {
        prepareLesson()
        val ready = assertIs<RunnerState.Ready>(start(LessonId("lesson"), Difficulty.EASY))
        val result = assertIs<RunnerState.Aborted>(abort(ready))
        assertEquals(ready.attemptId, result.attempt.id)
        assertEquals(AttemptCompletionKind.ABORTED, attempts.lastSave?.completionKind)
        assertEquals(1, identitiesCreated)
    }
}
