package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class ShopPurchaseResult(
    val itemId: ShopItemId,
    val balance: EconomyResourceBalance,
    val message: String,
)
