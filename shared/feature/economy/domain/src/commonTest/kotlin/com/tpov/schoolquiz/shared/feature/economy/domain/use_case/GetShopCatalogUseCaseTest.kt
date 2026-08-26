package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCurrency
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetShopCatalogUseCaseTest {
    private val catalog = GetShopCatalogUseCase().execute(EconomyResourceBalance.guest())

    /**
     * Every id reaches the shelf.
     *
     * The catalogue is a hand-written list, so a new [ShopItemId] can be added, wired to a screen
     * and still never appear — the failure looks like a missing feature rather than a missing line.
     */
    @Test
    fun everyShopItemIdAppearsInTheCatalog() {
        assertEquals(ShopItemId.entries.toSet(), catalog.map { it.id }.toSet())
    }

    @Test
    fun catalogHasNoDuplicates() {
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
    }

    /** The two rows that open a screen instead of buying something: free, and always open. */
    @Test
    fun doorsAreFreeAndAvailable() {
        listOf(ShopItemId.REFERRAL_PROGRAM, ShopItemId.NICKNAME_MARKET).forEach { id ->
            val item = catalog.single { it.id == id }
            assertTrue(item.isAvailable, "$id must stay reachable")
            val price = assertNotNull(item.price, "$id must carry a price to render")
            assertEquals(ShopCurrency.FREE, price.currency)
            assertEquals(0L, price.amount)
        }
    }

    /**
     * The catalogue is data, not copy: every word the reader sees is picked by the screen from
     * [ShopItemId], so no wording may travel inside the domain model.
     */
    @Test
    fun catalogCarriesNoDisplayStrings() {
        catalog.forEach { item ->
            assertEquals("", item.title, "${item.id} must not carry a title")
            assertEquals("", item.description, "${item.id} must not carry a description")
            assertNull(item.unavailableReason, "${item.id} must not carry an availability reason")
        }
    }
}
