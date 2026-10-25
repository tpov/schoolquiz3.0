package com.tpov.schoolquiz.android.feature.economy.presentation.view

import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.android.feature.economy.presentation.component.StoreShelfStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Строка объяснения магазина не лжёт: товар без цены на закрытом ряде — «нельзя купить», на
 * открытом (за золото от сервера) — словами записи; у записи с ценой и без записи — прежние
 * слова о состоянии полки.
 */
class NoirShopStoreStateLineTest {

    private fun priced(description: String = "Подписка") =
        StoreProduct(
            id = StoreProductId.PREMIUM_30D,
            title = "Premium",
            description = description,
            formattedPrice = "200 ₴",
            priceMicros = 200_000_000L,
            currency = "UAH",
        )

    private fun unpriced(description: String = "Подписка") =
        StoreProduct(
            id = StoreProductId.PREMIUM_30D,
            title = "Premium",
            description = description,
            formattedPrice = null,
            priceMicros = 0L,
            currency = "",
        )

    @Test
    fun `an unpriced record on a locked row says the item cannot be bought`() {
        assertEquals(
            StoreRowExplanation.Resource(R.string.shop_state_not_sold),
            storeRowExplanation(unpriced(), available = false, shelf = StoreShelfStatus.READY),
        )
    }

    @Test
    fun `an unpriced record on a row the server sells for gold speaks with the record's words`() {
        assertEquals(
            StoreRowExplanation.Words("Подписка"),
            storeRowExplanation(unpriced(), available = true, shelf = StoreShelfStatus.READY),
        )
    }

    @Test
    fun `a priced record speaks with its own words on every row state`() {
        assertEquals(
            StoreRowExplanation.Words("Подписка"),
            storeRowExplanation(priced(), available = true, shelf = StoreShelfStatus.READY),
        )
        assertEquals(
            StoreRowExplanation.Words("Подписка"),
            storeRowExplanation(priced(), available = false, shelf = StoreShelfStatus.READY),
        )
    }

    @Test
    fun `a record without words falls through to the shelf state`() {
        assertEquals(
            StoreRowExplanation.Resource(R.string.shop_state_store_loading),
            storeRowExplanation(priced(description = ""), available = true, shelf = StoreShelfStatus.LOADING),
        )
        assertEquals(
            StoreRowExplanation.Resource(R.string.shop_state_store_not_configured),
            storeRowExplanation(priced(description = "  "), available = true, shelf = StoreShelfStatus.NOT_CONFIGURED),
        )
    }

    @Test
    fun `no record at all answers with the shelf state`() {
        assertEquals(
            StoreRowExplanation.Resource(R.string.shop_state_store_loading),
            storeRowExplanation(null, available = false, shelf = StoreShelfStatus.LOADING),
        )
        assertEquals(
            StoreRowExplanation.Resource(R.string.shop_state_store_unreachable),
            storeRowExplanation(null, available = false, shelf = StoreShelfStatus.UNAVAILABLE),
        )
    }
}
