package com.tpov.schoolquiz.platform.firebase.auth

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Explicit test storage; production must bind the durable Room implementation. */
internal open class FakeMutationReceiptStore : MutationReceiptStore {
    private val receipts = MutableStateFlow<List<MutationReceipt>>(emptyList())

    override suspend fun persist(receipt: MutationReceipt) {
        val previous = find(receipt.ownerUid, receipt.mutationId)
        check(previous == null || previous.copy(receivedAtMs = receipt.receivedAtMs) == receipt)
        if (previous == null) receipts.value = receipts.value + receipt
    }

    override suspend fun find(
        ownerUid: String,
        mutationId: String,
    ): MutationReceipt? = receipts.value.firstOrNull { it.ownerUid == ownerUid && it.mutationId == mutationId }

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ) = receipts.map { rows -> rows.filter { it.ownerUid == ownerUid && it.entityRef == entityRef } }
}
