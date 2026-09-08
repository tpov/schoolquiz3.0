@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.advanceArenaFeedbackToPause
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.revealPreparedArenaQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.timeoutArenaQuestionToPause
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
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
import kotlin.test.assertTrue

class ArenaPresentationTransitionsTest {
    @Test
    fun foregroundAdvanceKeepsNextPausedUntilSecondCasAndDoesNotGiveExtraTime() {
        for (mode in SessionMode.entries) {
            val initial = initial(mode = mode)
            val locked = ArenaRunTransitions.answer(initial, UserAnswerDraft.SingleChoiceDraft(optId("B")))
            val next = advanceArenaFeedbackToPause(initial.copy(checkpoint = locked), 2000)
            assertEquals(ArenaRunnerPhase.PAUSED, next.phase)
            assertEquals(1, next.indexInPool)
            val revealed = revealPreparedArenaQuestion(initial.copy(checkpoint = next))
            assertEquals(next.copy(phase = ArenaRunnerPhase.ANSWERING), revealed)
            assertEquals(2000L, revealed.acceptedAnswers.single().answeredAtMs)
            assertEquals(2000L, revealed.questionStartedAtMs)
        }
    }

    @Test
    fun explicitResumeAfterBackgroundStillResetsDeadlineAndKeepsStartedAt() {
        val initial = initial(mode = SessionMode.EXAM)
        val locked = ArenaRunTransitions.answer(initial, UserAnswerDraft.SingleChoiceDraft(optId("B")))
        val next = advanceArenaFeedbackToPause(initial.copy(checkpoint = locked), 2000)
        val resumed = ArenaRunTransitions.continueRun(initial.copy(checkpoint = next), 90000)
        assertTrue(resumed.deadlineMs > next.deadlineMs)
        assertEquals(next.questionStartedAtMs, resumed.questionStartedAtMs)
        assertEquals(SessionMode.EXAM, resumed.sessionMode)
    }

    @Test
    fun hardTimeoutWithoutInputClosesOnlyOneSlotAndLeavesNextPaused() {
        val initial = initial(hard = true)
        val next = timeoutArenaQuestionToPause(initial, initial.checkpoint.deadlineMs)
        assertEquals(1, next.indexInPool)
        assertEquals(ArenaRunnerPhase.PAUSED, next.phase)
        assertTrue(next.acceptedAnswers.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            timeoutArenaQuestionToPause(initial.copy(checkpoint = next), Long.MAX_VALUE)
        }
    }

    @Test
    fun easyTimeoutKeepsFeedbackRatherThanAdvancingWithoutContinue() {
        val initial = initial()
        val next = timeoutArenaQuestionToPause(initial, initial.checkpoint.deadlineMs)
        assertEquals(ArenaRunnerPhase.FEEDBACK, next.phase)
        assertEquals(0, next.indexInPool)
    }

    @Test
    fun presentationCannotPauseAnUnansweredSlotOrRevealAnAlreadyAnsweringSlot() {
        val initial = initial()
        assertFailsWith<IllegalArgumentException> { advanceArenaFeedbackToPause(initial, 2000) }
        assertFailsWith<IllegalArgumentException> { revealPreparedArenaQuestion(initial) }
        assertFailsWith<IllegalArgumentException> { timeoutArenaQuestionToPause(initial, 0) }
    }

    private fun initial(
        hard: Boolean = false,
        mode: SessionMode = SessionMode.LEARNING,
    ): ArenaRunRead.Continuable {
        val difficulty = if (hard) Difficulty.HARD else Difficulty.EASY
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), "b".repeat(64), "owner", "request", ArenaEntryMode.RANDOM,
                difficulty, "lesson", 1, ArenaTicketStatus.RESERVED, 0, 50, 100, "2026-09-08",
            )
        val questions =
            (1..2).map { index ->
                val display =
                    if (hard) {
                        redactedSingleChoiceContent(difficulty = "HARD").copy(id = "q$index")
                    } else {
                        singleChoiceContent().copy(id = "q$index")
                    }
                ArenaTicketQuestion("q$index", 1, index - 1, "en", "not-parsed-here", display)
            }
        val binding =
            ArenaRunnerCheckpointBinding(
                ArenaTicketSnapshot(header, "author", 1, null, questions),
                "c".repeat(64),
            )
        val duration =
            computeTimer(
                questions.first().display,
                difficulty,
                TimerCoefficients.Default,
                mode,
            ).seconds * 1000L
        val checkpoint = initialArenaRunnerCheckpoint(binding, ArenaInitialCheckpoint(mode, 91, 1000, 1000 + duration))
        return ArenaRunRead.Continuable(binding, checkpoint, 0)
    }
}
