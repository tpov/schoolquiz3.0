package com.tpov.schoolquiz.shared.feature.economy.domain.model

sealed interface GiftBoxReward {
    val amount: Long

    data class Nolics(override val amount: Long) : GiftBoxReward

    data class Gold(override val amount: Long) : GiftBoxReward

    data class Premium(override val amount: Long) : GiftBoxReward

    data class Logo(
        val itemName: String,
        override val amount: Long = 1L,
    ) : GiftBoxReward

    data class Trophy(override val amount: Long) : GiftBoxReward
}
