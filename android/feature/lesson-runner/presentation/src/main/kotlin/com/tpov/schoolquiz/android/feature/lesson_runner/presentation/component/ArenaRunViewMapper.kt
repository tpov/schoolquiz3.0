package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.savedArenaFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp

/** Maps already strictly read state. No new claim, live bank read, JSON or local financial result. */
internal fun arenaRunView(
    state: ArenaRunCoordinatorState,
    stamp: ArenaSessionStamp,
    foreground: Boolean,
    lives: Int?,
    showExit: Boolean,
): ArenaRunnerView {
    val notice = state.notice
    return if (notice != null) {
        ArenaRunnerView.Attention(
            if (notice == ArenaRunNotice.ACCOUNT_CHANGED) {
                ArenaRunnerStatus.ACCOUNT_CHANGED
            } else {
                ArenaRunnerStatus.LOCAL_FAILURE
            },
            busy = state.busy,
            canReload = notice != ArenaRunNotice.ACCOUNT_CHANGED && !state.busy,
        )
    } else {
        observedArenaRunView(state, stamp, foreground, lives, showExit)
    }
}

private fun observedArenaRunView(
    state: ArenaRunCoordinatorState,
    stamp: ArenaSessionStamp,
    foreground: Boolean,
    lives: Int?,
    showExit: Boolean,
): ArenaRunnerView =
    when (val current = state.observed) {
        null -> ArenaRunnerView.Attention(ArenaRunnerStatus.LOADING, busy = state.busy)
        is ArenaRunRead.Continuable -> savedQuestion(current, stamp, foreground, lives, showExit, state.busy)
        is ArenaRunRead.Prepared -> ArenaRunnerView.Attention(ArenaRunnerStatus.PREPARED_REQUIRES_EXPLICIT_START)
        ArenaRunRead.LocalAbsent -> ArenaRunnerView.Attention(ArenaRunnerStatus.LOCAL_ABSENT, canReload = !state.busy)
        is ArenaRunRead.NeedsRecovery ->
            ArenaRunnerView.Attention(
                ArenaRunnerStatus.NEEDS_RECOVERY,
                canReload = !state.busy,
            )
        is ArenaRunRead.Completing -> ArenaRunnerView.Attention(ArenaRunnerStatus.COMPLETING, busy = state.busy)
        is ArenaRunRead.DecisionPending ->
            ArenaRunnerView.Attention(
                if (current.decision == ArenaRunDecision.SETTLE) {
                    ArenaRunnerStatus.PENDING_COMPLETION
                } else {
                    ArenaRunnerStatus.PENDING_CANCEL
                },
                busy = state.busy,
            )
        is ArenaRunRead.Terminal ->
            ArenaRunnerView.Attention(
                if (current.header.status == ArenaTicketStatus.CANCELLED) {
                    ArenaRunnerStatus.CANCELLED
                } else {
                    ArenaRunnerStatus.SETTLED_AWAITING_RECEIPT
                },
                busy = state.busy,
            )
    }

private fun savedQuestion(
    current: ArenaRunRead.Continuable,
    stamp: ArenaSessionStamp,
    foreground: Boolean,
    lives: Int?,
    showExit: Boolean,
    busy: Boolean,
): ArenaRunnerView.Question {
    val checkpoint = current.checkpoint
    require(checkpoint.ownerUid == stamp.ownerUid)
    val question = arenaCheckpointToReady(checkpoint, current.binding).toQuestionUiState(lives)
    val feedback =
        if (checkpoint.phase == ArenaRunnerPhase.FEEDBACK) {
            savedArenaFeedback(
                question.questionUiState,
                requireNotNull(checkpoint.pendingFeedback).answer,
                question.revealCorrect,
            )
        } else {
            null
        }
    return ArenaRunnerView.Question(
        ArenaRunToken(
            stamp,
            checkpoint.ticketId,
            checkpoint.attemptId,
            current.revision,
            checkpoint.indexInPool,
            checkpoint.phase,
        ),
        question.copy(showExitConfirmDialog = showExit),
        feedback,
        busy,
        foreground,
    )
}
