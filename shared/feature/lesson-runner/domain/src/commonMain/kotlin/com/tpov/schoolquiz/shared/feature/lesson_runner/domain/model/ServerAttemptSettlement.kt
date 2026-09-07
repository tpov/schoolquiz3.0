package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.core.scoring.computePercentScore

/** A missing response is retryable waiting, not a zero score or a server refusal. */
enum class ServerAttemptSettlementState {
    PENDING,
    SETTLED,
    UNSCORABLE,
    REJECTED,
    LEGACY_RECORDED,
    INVALID_RECEIPT,
    ACCOUNT_UNAVAILABLE,
}

data class ServerAttemptReward(
    val skillPoints: Long,
    val nolics: Long,
)

/**
 * Server evidence for the exact owner/attempt/lesson/version requested by the observer.
 * Nullable fields are unknown, never zero-filled from local scores or reward formulas.
 */
data class ServerAttemptSettlement(
    val state: ServerAttemptSettlementState,
    val wireStatus: String? = null,
    val receiptId: String? = null,
    val codeAnswer: CodeAnswer? = null,
    val percentScore: PercentScore? = null,
    val completed: Boolean? = null,
    val settledAtMs: Long? = null,
    val lifePointsSpent: Long? = null,
    val plasmaPointsSpent: Long? = null,
    val reward: ServerAttemptReward? = null,
    /** True only after the observer matched the currently signed-in owner of this attempt. */
    val ownerVerified: Boolean = false,
) {
    /** Defensive projection: even a malformed adapter cannot make partial proof look final. */
    val hasAccountingProof: Boolean
        get() =
            ownerVerified && state == ServerAttemptSettlementState.SETTLED &&
                !receiptId.isNullOrBlank() && settledAtMs.isSafeAmount() &&
                codeAnswer != null && percentScore != null && computePercentScore(codeAnswer) == percentScore &&
                lifePointsSpent.isSafeAmount() && plasmaPointsSpent.isSafeAmount() &&
                reward?.skillPoints.isSafeAmount() && reward?.nolics.isSafeAmount()

    /** SETTLED alone can prove accounting, but cannot retroactively prove an old completion. */
    val isConfirmedCompletion: Boolean
        get() = hasAccountingProof && completed == true

    val canQueueRating: Boolean
        get() = isConfirmedCompletion
}

private const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L

private fun Long?.isSafeAmount(): Boolean = this != null && this in 0..MAX_SAFE_SERVER_INTEGER
