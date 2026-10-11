package com.tpov.schoolquiz.apps.android_next.di

import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.module

/** A no-store boundary, not a fake successful Play purchase. Production keeps its real adapter. */
val isolatedMvpBillingModule =
    module {
        single<BillingRepository> { IsolatedMvpBillingRepository() }
    }

internal class IsolatedMvpBillingRepository : BillingRepository {
    override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> = unavailable()

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = flowOf(emptyList())

    override suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome = BillingOutcome.Unavailable(REASON)

    override suspend fun refreshUnsettledPurchases(): Result<Unit> = unavailable()

    override suspend fun consume(purchaseToken: String): Result<Unit> = unavailable()

    override suspend fun acknowledge(purchaseToken: String): Result<Unit> = unavailable()

    override suspend fun isAvailable(): Boolean = false

    private fun <T> unavailable(): Result<T> = Result.failure(IllegalStateException(REASON))

    private companion object {
        const val REASON = "Play purchases are disabled in the isolated MVP build"
    }
}
