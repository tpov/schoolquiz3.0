package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.core.analytics.NoOpAnalyticsTracker
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingPurchase
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseVerification
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.BuyGoldPackUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.LoadStoreShelfUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SettlePurchaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Магазин-молчун для тестов, которым store-полка безразлична: товаров не называет, покупки не ведёт.
 *
 * Живёт отдельным файлом, чтобы тесты поведения компонента не тащили друг друга за собой.
 */
internal class StubStoreBilling : BillingRepository {
    override suspend fun loadProducts(ids: Set<StoreProductId>): Result<List<StoreProduct>> =
        Result.success(emptyList())

    override fun observeUnsettledPurchases(): Flow<List<BillingPurchase>> = flowOf(emptyList())

    override suspend fun purchase(
        productId: StoreProductId,
        buyerId: String,
    ): BillingOutcome = BillingOutcome.Cancelled

    override suspend fun refreshUnsettledPurchases(): Result<Unit> = Result.success(Unit)

    override suspend fun consume(purchaseToken: String): Result<Unit> = Result.success(Unit)

    override suspend fun isAvailable(): Boolean = true
}

/** Путь покупки 1.2 над [StubStoreBilling]: до Play не доходит, сеттлер отвечает «ожидание». */
internal fun inertBuyGoldPack(billing: BillingRepository = StubStoreBilling()): BuyGoldPackUseCase =
    BuyGoldPackUseCase(
        billing = billing,
        networkMonitor =
            object : NetworkMonitor {
                override fun observeOnline(): Flow<Boolean> = flowOf(true)

                override suspend fun isOnline(): Boolean = true
            },
        settlePurchase =
            SettlePurchaseUseCase(
                billing = billing,
                verifier =
                    object : PurchaseVerifier {
                        override suspend fun verify(
                            purchaseToken: String,
                            productId: StoreProductId,
                        ): PurchaseVerification = PurchaseVerification.Pending(productId)
                    },
                balanceRefresher =
                    object : ServerBalanceRefresher {
                        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
                    },
                analytics = NoOpAnalyticsTracker,
            ),
        currentUidFlow = { flowOf(null) },
        buyerTag = { tag -> tag },
    )
