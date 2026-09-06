package com.tpov.schoolquiz.apps.android_next

import com.tpov.schoolquiz.apps.android_next.di.IsolatedMvpBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.model.BillingOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedMvpBillingTest {
    @Test
    fun isolatedStoreNeverFabricatesOrConsumesARealPurchase() =
        runTest {
            val store = IsolatedMvpBillingRepository()
            assertFalse(store.isAvailable())
            assertTrue(store.loadProducts(setOf(StoreProductId.GOLD_PACK_SMALL)).isFailure)
            assertTrue(store.purchase(StoreProductId.GOLD_PACK_SMALL, "buyer") is BillingOutcome.Unavailable)
            assertTrue(store.consume("unrelated-real-token").isFailure)
            assertTrue(store.refreshUnsettledPurchases().isFailure)
            assertTrue(store.observeUnsettledPurchases().first().isEmpty())
        }
}
