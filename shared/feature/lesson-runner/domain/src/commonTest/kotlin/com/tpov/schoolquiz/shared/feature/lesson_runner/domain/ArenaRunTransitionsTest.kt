@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionDisplay
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.toCheckpointAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunTransitionsTest {
    @Test
    fun answerLocksFeedbackWithoutClosingOrChangingTimerUntilContinue() {
        val current = current()
        val draft = UserAnswerDraft.SingleChoiceDraft(optId("B"))
        val feedback = ArenaRunTransitions.answer(current, draft)
        assertEquals(ArenaRunnerPhase.FEEDBACK, feedback.phase)
        assertEquals(0, feedback.indexInPool)
        assertEquals(current.checkpoint.deadlineMs, feedback.deadlineMs)
        assertTrue(feedback.acceptedAnswers.isEmpty())
        val next = ArenaRunTransitions.continueRun(current.with(feedback), 2000)
        assertEquals(1, next.indexInPool)
        assertEquals(draft.toCheckpointAnswer(), next.acceptedAnswers.single().answer)
        assertEquals(1000L, next.acceptedAnswers.single().durationMs)
    }

    @Test
    fun backgroundFromFeedbackKeepsLockedAnswerAndPausesOnlyNextQuestion() {
        val current = current()
        val draft = UserAnswerDraft.SingleChoiceDraft(optId("B"))
        val feedback = ArenaRunTransitions.answer(current, draft)
        val paused = ArenaRunTransitions.background(current.with(feedback), 100000)
        assertEquals(ArenaRunnerPhase.PAUSED, paused.phase)
        assertEquals(1, paused.indexInPool)
        assertEquals(draft.toCheckpointAnswer(), paused.acceptedAnswers.single().answer)
        assertTrue(!paused.acceptedAnswers.single().wasTimeout)
        assertEquals(paused, ArenaRunTransitions.background(current.with(paused), 200000))
    }

    @Test
    fun pausedExamResumeUsesExamTimerAndPreservesStartedAtSeedAndOrder() {
        val current = current(mode = SessionMode.EXAM)
        val paused = ArenaRunTransitions.background(current, 2000)
        val resumed = ArenaRunTransitions.continueRun(current.with(paused), 20000)
        val snapshot = current.binding.snapshot
        val duration =
            computeTimer(
                snapshot.questions[1].display,
                snapshot.header.difficulty,
                TimerCoefficients.Default,
                SessionMode.EXAM,
            ).seconds * 1000L
        assertEquals(20000 + duration, resumed.deadlineMs)
        assertEquals(paused.questionStartedAtMs, resumed.questionStartedAtMs)
        assertEquals(paused.seed, resumed.seed)
        assertEquals(paused.attemptId, resumed.attemptId)
        assertEquals(SessionMode.EXAM, resumed.sessionMode)
    }

    @Test
    fun easyTimeoutLocksOneGeneratedAnswerAndWaitsForContinue() {
        val current = current()
        val feedback = ArenaRunTransitions.timeout(current, 100000)
        assertEquals(ArenaRunnerPhase.FEEDBACK, feedback.phase)
        assertEquals(0, feedback.indexInPool)
        assertTrue(feedback.acceptedAnswers.isEmpty())
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.timeout(current.with(feedback), 200000) }
        val next = ArenaRunTransitions.continueRun(current.with(feedback), 200000)
        assertEquals(feedback.pendingFeedback?.answer?.toCheckpointAnswer(), next.acceptedAnswers.single().answer)
        assertEquals(1, next.indexInPool)
    }

    @Test
    fun hardTimeoutNeverInventsAnswerOrLocalScore() {
        val current = current(hard = true)
        val next = ArenaRunTransitions.timeout(current, 100000)
        assertEquals(1, next.indexInPool)
        assertEquals(ArenaRunnerPhase.ANSWERING, next.phase)
        assertTrue(next.acceptedAnswers.isEmpty())
        assertNull(arenaCheckpointToReady(next, current.binding).codeAnswer)
    }

    @Test
    fun hardTimeoutWithDraftPreservesOnlyActualInput() {
        val current = current(hard = true)
        val draft = UserAnswerDraft.SingleChoiceDraft(optId("ri-B"))
        val edited = ArenaRunTransitions.draft(current, draft)
        val next = ArenaRunTransitions.timeout(current.with(edited), 100000)
        assertEquals(draft.toCheckpointAnswer(), next.acceptedAnswers.single().answer)
        assertTrue(next.acceptedAnswers.single().wasTimeout)
        assertNull(arenaCheckpointToReady(next, current.binding).codeAnswer)
    }

    @Test
    fun hintLocksAnswerAndClaimTogetherWithoutHardBypass() {
        val current = current()
        val feedback = ArenaRunTransitions.hint(current, UserAnswerDraft.SingleChoiceDraft(optId("A")))
        assertEquals("S.", feedback.chargeClaims.raw)
        assertEquals(ArenaRunnerPhase.FEEDBACK, feedback.phase)
        val next = ArenaRunTransitions.continueRun(current.with(feedback), 2000)
        assertEquals("S.", next.chargeClaims.raw)
        assertFailsWith<IllegalArgumentException> {
            ArenaRunTransitions.hint(current(hard = true), UserAnswerDraft.SingleChoiceDraft(optId("ri-A")))
        }
    }

    @Test
    fun allQuestionTypesKeepExactLockedDraftThroughFinalCompletion() {
        val variants =
            listOf(
                singleChoiceContent() to UserAnswerDraft.SingleChoiceDraft(optId("B")),
                multipleChoiceContent(setOf("A", "C"), listOf("A", "B", "C")) to
                    UserAnswerDraft.MultipleChoiceDraft(setOf(optId("B"))),
                orderingContent() to UserAnswerDraft.OrderingDraft(listOf(optId("B"), optId("A"))),
                fillBlankContent(listOf("b1" to "x"), listOf("x", "y", "z", "u", "v")) to
                    UserAnswerDraft.FillBlankDraft(mapOf(blankId("b1") to candId("y"))),
                QuestionContent.Survey(
                    "q1",
                    Difficulty.EASY,
                    "Survey",
                    null,
                    listOf(QuestionContent.Option(optId("A"), "A"), QuestionContent.Option(optId("B"), "B")),
                ) to UserAnswerDraft.SurveyDraft(emptySet()),
            )
        for ((display, draft) in variants) {
            val current = current(displays = listOf(display))
            val feedback = ArenaRunTransitions.answer(current, draft)
            val final = ArenaRunTransitions.continueRun(current.with(feedback), 2000)
            assertEquals(ArenaRunnerPhase.COMPLETING, final.phase)
            assertEquals(2000L, final.endedAtMs)
            assertEquals(
                draft.toCheckpointAnswer(),
                arenaCheckpointCompletion(final, current.binding).answers.single().answer,
            )
        }
    }

    @Test
    fun staleOrForgedPhaseCannotEditLockedAnswerOrAdvanceFinal() {
        val current = current(displays = listOf(singleChoiceContent()))
        val draft = UserAnswerDraft.SingleChoiceDraft(optId("A"))
        val feedback = current.with(ArenaRunTransitions.answer(current, draft))
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.draft(feedback, draft) }
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.answer(feedback, draft) }
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.continueRun(current, 2000) }
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.timeout(current, 1000) }
        val final = current.with(ArenaRunTransitions.continueRun(feedback, 2000))
        assertFailsWith<IllegalArgumentException> { ArenaRunTransitions.background(final, 3000) }
    }

    private fun ArenaRunRead.Continuable.with(checkpoint: ArenaRunnerCheckpoint) = copy(checkpoint = checkpoint)

    private fun current(
        hard: Boolean = false,
        mode: SessionMode = SessionMode.LEARNING,
        displays: List<QuestionDisplay>? = null,
    ): ArenaRunRead.Continuable {
        val difficulty = if (hard) Difficulty.HARD else Difficulty.EASY
        val questions =
            displays ?: (1..2).map { index ->
                if (hard) {
                    redactedSingleChoiceContent(difficulty = "HARD").copy(id = "q$index")
                } else {
                    singleChoiceContent(text = "Question ".repeat(20)).copy(id = "q$index")
                }
            }
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), "b".repeat(64), "owner", "request", ArenaEntryMode.SELECTED,
                difficulty, "lesson", 3, ArenaTicketStatus.RESERVED, 1, 50, 100, "2026-09-08",
            )
        val snapshot =
            ArenaTicketSnapshot(
                header,
                "author",
                2,
                null,
                questions.mapIndexed { index, display ->
                    ArenaTicketQuestion("q${index + 1}", 2, index, "en", "not-parsed-by-domain", display)
                },
            )
        val binding = ArenaRunnerCheckpointBinding(snapshot, "c".repeat(64))
        val duration = computeTimer(questions.first(), difficulty, TimerCoefficients.Default, mode).seconds * 1000L
        val checkpoint = initialArenaRunnerCheckpoint(binding, ArenaInitialCheckpoint(mode, 91, 1000, 1000 + duration))
        return ArenaRunRead.Continuable(binding, checkpoint, 0)
    }
}
