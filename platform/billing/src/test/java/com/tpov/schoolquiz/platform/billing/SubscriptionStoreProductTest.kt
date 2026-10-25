package com.tpov.schoolquiz.platform.billing

import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The subscription row the shelf keeps: priced by the recurring phase of the chosen offer — or kept
 * without a price when Play names no buyable offer. Dropping the row instead would read as a
 * missing SKU and fail the whole shelf query (see productsOrFailure).
 */
class SubscriptionStoreProductTest {
    private val trial = SubscriptionOfferShape(
        offerToken = "token-trial",
        phases = listOf(
            PricePhaseShape(0L, "0 ₴", "UAH"),
            PricePhaseShape(200_000_000L, "200 ₴", "UAH"),
        ),
    )
    private val basePlan = SubscriptionOfferShape(
        offerToken = "token-base",
        phases = listOf(PricePhaseShape(200_000_000L, "200 ₴", "UAH")),
    )

    /** Оффер без фаз цены: offerToken есть, покупать из него нечего. */
    private val emptyPhases = SubscriptionOfferShape(
        offerToken = "token-empty",
        phases = emptyList(),
    )

    @Test
    fun `the price is the recurring phase of the chosen offer`() {
        val product = subscriptionStoreProduct(StoreProductId.PREMIUM_30D, "Premium", "Подписка", listOf(basePlan, trial))

        // Триал выигрывает выбор, и полка показывает его платную фазу, а не ноль триала.
        assertEquals("200 ₴", product.formattedPrice)
        assertEquals(200_000_000L, product.priceMicros)
        assertEquals("UAH", product.currency)
    }

    @Test
    fun `a subscription without offers keeps its row without a price`() {
        val product = subscriptionStoreProduct(StoreProductId.PREMIUM_30D, "Premium", "Подписка", emptyList())

        assertEquals(StoreProductId.PREMIUM_30D, product.id)
        assertNull(product.formattedPrice)
        assertEquals("Подписка", product.description)
    }

    @Test
    fun `offers without pricing phases leave the row without a price`() {
        val product = subscriptionStoreProduct(StoreProductId.PREMIUM_30D, "Premium", "Подписка", listOf(emptyPhases))

        assertNull(product.formattedPrice)
    }
}
