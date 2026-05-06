package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import kotlinx.coroutines.flow.Flow

interface EconomyRepository {
    fun observeBalance(): Flow<EconomyResourceBalance>

    suspend fun currentBalance(): EconomyResourceBalance

    suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult>

    suspend fun referralProgram(): ReferralProgram
}
