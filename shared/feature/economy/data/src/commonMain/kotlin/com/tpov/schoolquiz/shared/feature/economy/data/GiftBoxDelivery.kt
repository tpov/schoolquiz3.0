package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine

/** The account-switch gate and delivery path must be the same engine used by background sync. */
interface GiftBoxDelivery {
    suspend fun <T> withoutPasses(block: suspend () -> T): T

    suspend fun drain(ownerUid: String)
}

class OutboxGiftBoxDelivery(private val engine: OutboxEngine) : GiftBoxDelivery {
    override suspend fun <T> withoutPasses(block: suspend () -> T): T = engine.withoutPasses(block)

    override suspend fun drain(ownerUid: String) {
        engine.drain(ownerUid)
    }
}
