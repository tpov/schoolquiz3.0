package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask

/** The already wire-validated public snapshot and its journal-owned content hash, never a live lesson. */
data class ArenaRunnerCheckpointBinding(
    val snapshot: ArenaTicketSnapshot,
    val snapshotSha256: String,
)

enum class ArenaRunnerPhase { ANSWERING, FEEDBACK, PAUSED, COMPLETING }

/** An answer already selected for feedback, not yet advanced by the Continue action. */
data class ArenaPendingFeedback(
    val questionId: String,
    val codeAnswerIndex: Int,
    val answer: UserAnswerDraft,
)

/**
 * Local continuation only; this is neither launch permission nor a scored result.
 * The ticket row owns its revision, current owner lease and immutable questions separately.
 * No auth token, wallet balance, grading key, question payload or claimed numeric score is stored.
 */
@Suppress("LongParameterList")
data class ArenaRunnerCheckpoint(
    val schemaVersion: Int,
    val ownerUid: String,
    val ticketId: String,
    val attemptId: String,
    val snapshotSha256: String,
    val sessionMode: SessionMode,
    /** Only the runner's deterministic option presentation/timeout seed; never reselects questions. */
    val seed: Long,
    val phase: ArenaRunnerPhase,
    val indexInPool: Int,
    val questionStartedAtMs: Long,
    val deadlineMs: Long,
    val currentDraft: UserAnswerDraft?,
    val pendingFeedback: ArenaPendingFeedback?,
    val acceptedAnswers: List<RawAnsweredQuestion>,
    val chargeClaims: ChargeClaimMask,
    /** Chosen once with the final checkpoint and reused by every settlement retry. */
    val endedAtMs: Long?,
) {
    companion object {
        const val VERSION = 1
        const val MAX_QUESTIONS = 20
    }
}
