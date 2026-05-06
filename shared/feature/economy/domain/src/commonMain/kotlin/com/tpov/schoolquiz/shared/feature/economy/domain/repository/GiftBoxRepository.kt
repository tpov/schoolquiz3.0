package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening

interface GiftBoxRepository {
    suspend fun openGiftBox(): Result<GiftBoxOpening>
}
