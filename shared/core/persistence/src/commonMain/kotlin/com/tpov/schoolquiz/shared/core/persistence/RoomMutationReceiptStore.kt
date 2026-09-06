package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMutationReceiptStore(private val dao: MutationReceiptDao) : MutationReceiptStore {
    override suspend fun persist(receipt: MutationReceipt) {
        require(receipt.ownerUid.isNotBlank() && receipt.mutationId.isNotBlank())
        dao.persist(
            MutationReceiptEntity(
                receipt.ownerUid,
                receipt.mutationId,
                receipt.operation,
                receipt.entityRef,
                receipt.payloadJson,
                receipt.receivedAtMs,
            ),
        )
    }

    override suspend fun find(
        ownerUid: String,
        mutationId: String,
    ): MutationReceipt? = dao.find(ownerUid, mutationId)?.toReceipt()

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<List<MutationReceipt>> = dao.observeEntity(ownerUid, entityRef).map { rows -> rows.map { it.toReceipt() } }
}

private fun MutationReceiptEntity.toReceipt() =
    MutationReceipt(ownerUid, mutationId, operation, entityRef, payloadJson, receivedAtMs)
