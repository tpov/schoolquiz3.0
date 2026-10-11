package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreShelf
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository

/**
 * Asks the store what it sells and at what price, for the whole shelf at once.
 *
 * The store is the only source of both the price and its formatting (NFR4), so the shelf re-asks on
 * every shop open rather than keeping the answers: a price cached from yesterday is a price the
 * store no longer charges. The answer is folded into three named endings so the screen can explain
 * a locked row instead of leaving it silently grey — "the store is unreachable" and "nothing is
 * configured" are different problems with different owners.
 */
class LoadStoreShelfUseCase(
    private val billing: BillingRepository,
) {
    suspend operator fun invoke(): StoreShelf =
        billing.loadProducts(StoreProductId.entries.toSet()).fold(
            onSuccess = { products ->
                if (products.isEmpty()) StoreShelf.NotConfigured else StoreShelf.Ready(products)
            },
            onFailure = { StoreShelf.Unreachable },
        )
}
