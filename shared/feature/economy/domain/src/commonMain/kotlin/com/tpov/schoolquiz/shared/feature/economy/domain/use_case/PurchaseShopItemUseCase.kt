package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository

class PurchaseShopItemUseCase(
    private val repository: EconomyRepository,
) {
    suspend fun execute(itemId: ShopItemId) = repository.purchase(itemId)
}
