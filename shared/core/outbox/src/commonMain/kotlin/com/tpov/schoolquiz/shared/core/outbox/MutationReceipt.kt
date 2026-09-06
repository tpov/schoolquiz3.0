package com.tpov.schoolquiz.shared.core.outbox

import kotlinx.coroutines.flow.Flow

/** The complete callable result. Feature-specific fields are deliberately opaque to the queue. */
data class MutationResponse(val payloadJson: String)

/** Immutable local evidence of a successful server response; it outlives its queue record. */
data class MutationReceipt(
    val ownerUid: String,
    val mutationId: String,
    val operation: String,
    val entityRef: String?,
    val payloadJson: String,
    val receivedAtMs: Long,
)

interface MutationReceiptStore {
    /** Idempotent by owner + mutation. A different response for the same identity must fail. */
    suspend fun persist(receipt: MutationReceipt)

    suspend fun find(
        ownerUid: String,
        mutationId: String,
    ): MutationReceipt?

    fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<List<MutationReceipt>>
}

/** Account identity plus an epoch: A -> B -> A is not the same authenticated session. */
data class OutboxSession(val ownerUid: String, val generation: Long)

fun interface OutboxSessionProvider {
    fun current(): OutboxSession?
}
