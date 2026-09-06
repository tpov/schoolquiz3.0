package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward

interface GiftBoxRemoteDataSource {
    /** Reads the full persisted submitMutation envelope; transport is owned solely by OutboxEngine. */
    fun openingFromReceipt(
        payloadJson: String,
        ownerUid: String,
        openingId: String,
    ): GiftBoxReceipt
}

data class GiftBoxReceipt(
    val openingId: String,
    val ownerUid: String,
    val status: GiftBoxReceiptStatus,
    val reward: GiftBoxReward?,
)

enum class GiftBoxReceiptStatus { OPENED, NO_BOXES }
