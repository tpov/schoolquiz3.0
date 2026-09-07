package com.tpov.schoolquiz.shared.core.persistence

/**
 * Exact immutable bytes reread inside the claim/progress/first-freeze Room transaction.
 * A stored hash alone cannot prove that the current bank or purchase terms still match the captured row.
 */
data class ArenaCheckpointBinding(
    val ownerUid: String,
    val ticketId: String,
    val attemptId: String,
    val snapshotSha256: String,
    val requestId: String,
    val termsJson: String,
    val snapshotJson: String,
)

/** Pure, required typed-codec validation; no network, mutation or permissive production implementation. */
fun interface ArenaCheckpointValidator {
    fun validate(
        binding: ArenaCheckpointBinding,
        previousJson: String?,
        candidateJson: String,
        completionPayloadJson: String?,
    )
}

internal fun validateArenaCheckpoint(
    row: ArenaTicketRecordEntity,
    candidate: String,
    previous: String?,
    validator: ArenaCheckpointValidator,
    completionPayload: String? = null,
) {
    require(candidate.encodeToByteArray().size in 1..MAX_CHECKPOINT_BYTES)
    require(row.checkpointRevision >= 0)
    validator.validate(
        ArenaCheckpointBinding(
            row.ownerUid,
            row.ticketId,
            row.attemptId,
            requireNotNull(row.snapshotSha256),
            row.requestId,
            row.termsJson,
            requireNotNull(row.snapshotJson),
        ),
        previous,
        candidate,
        completionPayload,
    )
}

private const val MAX_CHECKPOINT_BYTES = 1_048_576
