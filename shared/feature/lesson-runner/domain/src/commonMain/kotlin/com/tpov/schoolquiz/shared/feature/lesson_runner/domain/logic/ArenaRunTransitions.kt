package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState

/** Pure candidates only. The caller must win CAS and reread before revealing any resulting UI. */
object ArenaRunTransitions {
    fun draft(
        current: ArenaRunRead.Continuable,
        answer: UserAnswerDraft,
    ): ArenaRunnerCheckpoint {
        require(current.checkpoint.phase == ArenaRunnerPhase.ANSWERING)
        return checked(current, current.checkpoint.copy(currentDraft = answer))
    }

    fun answer(
        current: ArenaRunRead.Continuable,
        answer: UserAnswerDraft,
    ): ArenaRunnerCheckpoint {
        require(current.checkpoint.phase == ArenaRunnerPhase.ANSWERING)
        return feedback(current, arenaCheckpointToReady(current.checkpoint, current.binding), answer)
    }

    /** The component additionally checks its known budget; this function neither reads nor debits a wallet. */
    fun hint(
        current: ArenaRunRead.Continuable,
        answer: UserAnswerDraft,
    ): ArenaRunnerCheckpoint {
        require(current.checkpoint.phase == ArenaRunnerPhase.ANSWERING)
        require(current.binding.snapshot.header.difficulty == Difficulty.EASY)
        val ready = arenaCheckpointToReady(current.checkpoint, current.binding)
        return feedback(current, claimCharge(ready, ChargeClaimMask.Claim.STANDARD_HINT), answer)
    }

    fun timeout(
        current: ArenaRunRead.Continuable,
        nowMs: Long,
    ): ArenaRunnerCheckpoint {
        val checkpoint = current.checkpoint
        require(checkpoint.phase == ArenaRunnerPhase.ANSWERING && nowMs >= checkpoint.deadlineMs)
        val ready = arenaCheckpointToReady(checkpoint, current.binding)
        val closed = autoAnswerOnTimeout(ready, ready.seed, nowMs)
        return if (ready.mode == Difficulty.EASY) {
            // As in ordinary feedback UI, answer timing is accepted on Continue, not on reveal.
            feedback(current, ready, closed.answers.last().answer.toCheckpointDraft())
        } else {
            advanced(current, closed, nowMs, pauseNext = false)
        }
    }

    fun continueRun(
        current: ArenaRunRead.Continuable,
        nowMs: Long,
    ): ArenaRunnerCheckpoint {
        val checkpoint = current.checkpoint
        return when (checkpoint.phase) {
            ArenaRunnerPhase.FEEDBACK -> advanced(current, acceptLocked(current, nowMs), nowMs, pauseNext = false)
            ArenaRunnerPhase.PAUSED -> resumePaused(current, nowMs)
            else -> throw IllegalArgumentException("No saved feedback or pause to continue")
        }
    }

    /** Real background, never configuration change. Closes at most the currently visible frozen slot. */
    fun background(
        current: ArenaRunRead.Continuable,
        nowMs: Long,
    ): ArenaRunnerCheckpoint {
        val checkpoint = current.checkpoint
        if (checkpoint.phase == ArenaRunnerPhase.PAUSED) return checked(current, checkpoint)
        val next =
            when (checkpoint.phase) {
                ArenaRunnerPhase.FEEDBACK -> acceptLocked(current, nowMs)
                ArenaRunnerPhase.ANSWERING ->
                    arenaCheckpointToReady(checkpoint, current.binding).let {
                        autoAnswerOnTimeout(it, it.seed, nowMs)
                    }
                else -> throw IllegalArgumentException("Not a continuable question")
            }
        return advanced(current, next, nowMs, pauseNext = true)
    }

    private fun acceptLocked(
        current: ArenaRunRead.Continuable,
        nowMs: Long,
    ): RunnerState.Ready {
        val answer = requireNotNull(current.checkpoint.pendingFeedback).answer.toCheckpointAnswer()
        return submitAnswer(arenaCheckpointToReady(current.checkpoint, current.binding), answer, nowMs)
    }

    private fun resumePaused(
        current: ArenaRunRead.Continuable,
        nowMs: Long,
    ): ArenaRunnerCheckpoint {
        val checkpoint = current.checkpoint
        val snapshot = current.binding.snapshot
        val duration =
            computeTimer(
                snapshot.questions[checkpoint.indexInPool].display,
                snapshot.header.difficulty,
                TimerCoefficients.Default,
                checkpoint.sessionMode,
            ).seconds * MILLIS_PER_SECOND
        require(nowMs in 0..Long.MAX_VALUE - duration)
        return checked(current, checkpoint.copy(phase = ArenaRunnerPhase.ANSWERING, deadlineMs = nowMs + duration))
    }

    private fun feedback(
        current: ArenaRunRead.Continuable,
        ready: RunnerState.Ready,
        answer: UserAnswerDraft,
    ): ArenaRunnerCheckpoint =
        checked(
            current,
            arenaCheckpointFromReady(
                ready,
                current.binding,
                ArenaRunnerPhase.FEEDBACK,
                ArenaPendingFeedback(ready.playOrder[ready.indexInPool].sourceId.value, ready.indexInPool, answer),
            ),
        )

    private fun advanced(
        current: ArenaRunRead.Continuable,
        ready: RunnerState.Ready,
        nowMs: Long,
        pauseNext: Boolean,
    ): ArenaRunnerCheckpoint {
        val complete = ready.indexInPool == ready.playOrder.size
        val phase =
            when {
                complete -> ArenaRunnerPhase.COMPLETING
                pauseNext -> ArenaRunnerPhase.PAUSED
                else -> ArenaRunnerPhase.ANSWERING
            }
        return checked(
            current,
            arenaCheckpointFromReady(
                ready.copy(isPaused = phase == ArenaRunnerPhase.PAUSED),
                current.binding,
                phase,
                endedAtMs = if (complete) nowMs else null,
            ),
        )
    }

    private fun checked(
        current: ArenaRunRead.Continuable,
        candidate: ArenaRunnerCheckpoint,
    ): ArenaRunnerCheckpoint = candidate.also { validateArenaRunnerTransition(current.checkpoint, it, current.binding) }

    private const val MILLIS_PER_SECOND = 1000L
}
