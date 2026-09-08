package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead

/** Close the locked answer, but do not start displaying the next slot while its CAS is still in flight. */
fun advanceArenaFeedbackToPause(
    current: ArenaRunRead.Continuable,
    nowMs: Long,
): ArenaRunnerCheckpoint {
    require(current.checkpoint.phase == ArenaRunnerPhase.FEEDBACK)
    return ArenaRunTransitions.background(current, nowMs)
}

/** HARD timeout closes one slot; EASY still waits at saved feedback until the player's Continue. */
fun timeoutArenaQuestionToPause(
    current: ArenaRunRead.Continuable,
    nowMs: Long,
): ArenaRunnerCheckpoint {
    require(current.checkpoint.phase == ArenaRunnerPhase.ANSWERING && nowMs >= current.checkpoint.deadlineMs)
    return if (current.binding.snapshot.header.difficulty == Difficulty.HARD) {
        ArenaRunTransitions.background(current, nowMs)
    } else {
        ArenaRunTransitions.timeout(current, nowMs)
    }
}

/**
 * Host-only automatic presentation after its own foreground advance. No clock reset or new question start.
 * Never invoke automatically for a restored PAUSED route: that requires ordinary explicit Resume instead.
 */
fun revealPreparedArenaQuestion(current: ArenaRunRead.Continuable): ArenaRunnerCheckpoint {
    require(current.checkpoint.phase == ArenaRunnerPhase.PAUSED)
    return current.checkpoint.copy(phase = ArenaRunnerPhase.ANSWERING).also {
        validateArenaRunnerTransition(current.checkpoint, it, current.binding)
    }
}
