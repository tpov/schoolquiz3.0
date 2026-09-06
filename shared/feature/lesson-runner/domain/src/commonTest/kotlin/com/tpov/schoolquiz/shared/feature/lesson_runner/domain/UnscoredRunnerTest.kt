package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.chargeClaimsOrEmpty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.claimCharge
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnscoredRunnerTest {
    private val public = redactedSingleChoiceContent(difficulty = "HARD")
    private fun rawState(count: Int = 2) = makeReadyState(
        mode = Difficulty.HARD,
        playOrder = (0 until count).map { RunnerQuestion.Redacted(QuestionId("q$it"), it, it, public) },
        codeAnswer = null,
        questionStartedAtMs = 1000L,
    )

    @Test fun rawAnswerAdvancesWithoutLocalScoreOrHistoryRow() {
        val state = rawState()
        val answer = UserAnswer.SingleChoiceAnswer(OptionId("opaque-option"))
        val next = submitAnswer(state, answer, nowMs = 1250L)
        assertEquals(state.attemptId, next.attemptId)
        assertNull(next.codeAnswer)
        assertTrue(next.answers.isEmpty())
        assertEquals(answer, next.rawAnswers.single().answer)
        assertEquals(250L, next.rawAnswers.single().durationMs)
        assertEquals(1, next.indexInPool)
    }

    @Test fun rawTimeoutWithoutInputOmitsAnswerAndStillServesQuestion() = runTest {
        val next = autoAnswerOnTimeout(rawState(1), randomSeed = 7, nowMs = 2000L)
        assertTrue(next.rawAnswers.isEmpty())
        assertNull(next.codeAnswer)
        val repo = FakeLessonAttemptRepository()
        val result = CompleteAttemptUseCase(repo, FakeClock())(next)
        assertIs<RunnerState.PendingCompletion>(result)
        assertEquals(listOf("q0"), result.intent.served.map { it.questionId.value })
        assertTrue(repo.savedAttempts.isEmpty())
    }

    @Test fun rawTimeoutPreservesPartialSelectionRatherThanGuessingCorrectCount() {
        val draft = UserAnswerDraft.MultipleChoiceDraft(setOf(OptionId("option-b")))
        val state = rawState().copy(currentDraftAnswer = draft)
        val next = autoAnswerOnTimeout(state, randomSeed = 1, nowMs = 2000L)
        assertEquals(UserAnswer.MultipleChoiceAnswer(draft.selected), next.rawAnswers.single().answer)
        assertTrue(next.rawAnswers.single().wasTimeout)
        assertEquals(next, autoAnswerOnTimeout(state, randomSeed = 999, nowMs = 2000L))
    }

    @Test fun rawOrderingAndFillKeepOpaqueIds() {
        val ordering = UserAnswer.OrderingAnswer(listOf(OptionId("ri-2"), OptionId("ri-0")))
        val fill = UserAnswer.FillBlankAnswer(mapOf(BlankId("blank-a") to CandidateId("rc-4")))
        val next = submitAnswer(submitAnswer(rawState(), ordering, 2000), fill, 3000)
        assertEquals(listOf(ordering, fill), next.rawAnswers.map { it.answer })
        assertNull(next.codeAnswer)
    }

    @Test fun mixedPlayOrderIsEntirelyRawEvenForKeyedQuestion() {
        val state = rawState().copy(playOrder = listOf(
            makeRunnerQuestion(id = "q0", codeAnswerIndex = 0, content = singleChoiceContent(difficulty = Difficulty.HARD)),
            RunnerQuestion.Redacted(QuestionId("q1"), 1, 1, public),
        ))
        val next = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), 2000L)
        assertNull(next.codeAnswer)
        assertTrue(next.answers.isEmpty())
        assertEquals(1, next.rawAnswers.size)
    }

    @Test fun ordinaryRawAttemptCannotClaimPlasmaOrBuildFakeAbortDigits() {
        val state = rawState()
        assertFailsWith<IllegalArgumentException> { claimCharge(state, ChargeClaimMask.Claim.PLASMA_SKIP) }
        assertFailsWith<IllegalArgumentException> { buildCodeAnswerOnAbort(state) }
        assertEquals("..", state.chargeClaimsOrEmpty().raw)
    }

    @Test fun completeAndAbortKeepStartIdentityAndDistinctOutcomeWithoutScoredAttempt() = runTest {
        val repo = FakeLessonAttemptRepository()
        val state = rawState()
        val completed = assertIs<RunnerState.PendingCompletion>(CompleteAttemptUseCase(repo, FakeClock())(state))
        val aborted = assertIs<RunnerState.PendingAbort>(AbortAttemptUseCase(repo, FakeClock())(state))
        assertEquals(state.attemptId, completed.intent.identity.id)
        assertEquals(state.attemptId, aborted.intent.identity.id)
        assertEquals(AttemptCompletionKind.COMPLETED, completed.intent.completionKind)
        assertEquals(AttemptCompletionKind.ABORTED, aborted.intent.completionKind)
        assertEquals(0, repo.saveCallCount)
        assertTrue(repo.savedAttempts.isEmpty())
    }

    @Test fun failedRawSaveIsNotACompletedAttempt() = runTest {
        val repo = FakeLessonAttemptRepository().apply { saveResult = Result.failure(IllegalStateException("disk")) }
        val result = CompleteAttemptUseCase(repo, FakeClock())(rawState())
        assertIs<RunnerState.PendingSaveFailed>(result)
        assertTrue(repo.rawIntents.isEmpty() && repo.savedAttempts.isEmpty())
    }

    @Test fun rawSaveCancellationPropagates() = runTest {
        val repo = FakeLessonAttemptRepository().apply { saveResult = Result.failure(CancellationException("stop")) }
        assertFailsWith<CancellationException> { CompleteAttemptUseCase(repo, FakeClock())(rawState()) }
    }

    @Test fun publicTimerUsesVisibleContentAndTreatsEmptyImageAsAbsent() {
        val content = public.copy(imageUrl = "")
        assertEquals(computeTimer(public.copy(imageUrl = null), Difficulty.HARD, TimerCoefficients.Default),
            computeTimer(content, Difficulty.HARD, TimerCoefficients.Default))
    }
}
