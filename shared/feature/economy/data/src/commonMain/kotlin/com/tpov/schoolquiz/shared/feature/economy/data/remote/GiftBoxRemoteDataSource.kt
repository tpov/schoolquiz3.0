package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward

interface GiftBoxRemoteDataSource {
    suspend fun openGiftBox(): GiftBoxReward
}
