package com.tpov.schoolquiz.platform.billing

import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shelf is worth when the store knows only some of what it was asked about.
 *
 * A product asked for and not returned is not an empty shelf — it is a SKU that exists in the code
 * and not in Play Console, and no price can be shown for it. The rule that only fires when *every*
 * product is missing is silent in the ordinary case: a half-finished console.
 */
class ProductsOrFailureTest {

    private fun product(id: StoreProductId) =
        StoreProduct(
            id = id,
            title = id.playSku,
            description = "",
            formattedPrice = "₴100",
            priceMicros = 100_000_000L,
            currency = "UAH",
        )

    @Test
    fun `everything asked for came back`() {
        val asked = setOf(StoreProductId.GOLD_PACK_SMALL, StoreProductId.GOLD_PACK_MEDIUM)
        val returned = asked.map(::product)

        val result = productsOrFailure(asked, returned)

        assertEquals(returned, result.getOrNull())
    }

    @Test
    fun `a console that is only half configured fails, naming exactly what is missing`() {
        val asked =
            setOf(
                StoreProductId.GOLD_PACK_SMALL,
                StoreProductId.GOLD_PACK_MEDIUM,
                StoreProductId.GOLD_PACK_LARGE,
            )

        val result = productsOrFailure(asked, listOf(product(StoreProductId.GOLD_PACK_SMALL)))

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("names the medium pack: $message", message.contains("gold_pack_medium"))
        assertTrue("names the large pack: $message", message.contains("gold_pack_large"))
        assertTrue("does not name the one that came back: $message", !message.contains("gold_pack_small"))
    }

    @Test
    fun `nothing came back at all`() {
        val asked = setOf(StoreProductId.GOLD_PACK_SMALL)

        val result = productsOrFailure(asked, emptyList())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("gold_pack_small"))
    }

    /** Asking for nothing is not a misconfiguration. */
    @Test
    fun `an empty request succeeds`() {
        assertEquals(emptyList<StoreProduct>(), productsOrFailure(emptySet(), emptyList()).getOrNull())
    }
}
