package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance

interface EconomyRemoteDataSource {
    suspend fun purchase(request: ShopPurchaseRequest): EconomyResourceBalance

    suspend fun unlockLesson(request: LessonUnlockRequest): EconomyResourceBalance
}
