package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class GiftBoxOpening(
    val reward: GiftBoxReward,
    val profileSynced: Boolean,
    val remainingBoxCount: Int?,
)
