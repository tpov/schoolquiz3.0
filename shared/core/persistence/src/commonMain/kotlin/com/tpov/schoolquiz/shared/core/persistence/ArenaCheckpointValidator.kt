package com.tpov.schoolquiz.shared.core.persistence

/** Immutable binding is reread in the same Room transaction as claim/progress/freeze. */
data class ArenaCheckpointBinding(
    val ownerUid: String,
    val ticketId: String,
    val attemptId: String,
    val snapshotSha256: String,
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
        ArenaCheckpointBinding(row.ownerUid, row.ticketId, row.attemptId, requireNotNull(row.snapshotSha256)),
        previous,
        candidate,
        completionPayload,
    )
}

private const val MAX_CHECKPOINT_BYTES = 1_048_576
