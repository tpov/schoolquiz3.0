package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingGiftBoxOpening
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GiftBoxRepository {
    suspend fun openGiftBox(): Result<GiftBoxOpening>

    fun observePendingOpening(): Flow<PendingGiftBoxOpening?> = flowOf(null)

    /** Resume only: never creates a new intent or draws a reward. */
    suspend fun resumePendingOpening(): Result<GiftBoxOpening>? = null

    /** Explicit result dismissal. Failed/uncertain transport is not an acknowledgement. */
    suspend fun acknowledgeOpening(openingId: String): Result<Boolean> = Result.success(false)
}
