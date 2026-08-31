package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import kotlinx.coroutines.flow.Flow

interface EconomyRepository {
    fun observeBalance(): Flow<EconomyResourceBalance>

    suspend fun currentBalance(): EconomyResourceBalance

    suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult>

    /**
     * Opens a lesson with nolics. The server prices it from the lesson's own allocated time and
     * charges it; buying the same thing twice is a no-op rather than a second charge.
     */
    suspend fun unlockLesson(lessonId: String, kind: LessonUnlockKind): Result<EconomyResourceBalance>

    suspend fun referralProgram(): ReferralProgram
}
