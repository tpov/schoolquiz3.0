package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PremiumGoldOffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Разбор ответа сервера — теми же правилами, что и его собственный разбор документа.
 *
 * Расхождение здесь — это цена, которую устройство показало, а сервер не спишет: обе стороны
 * читают один документ, и вырождаться обязаны одинаково.
 */
class EconomyConstantsResponseParsingTest {

    private val bootstrap = EconomyConstants.BOOTSTRAP

    @Test
    fun `a ladder with one bad rung falls back whole, not shortened`() {
        // Сервер отменяет всю лестницу; укороченная здесь показывала бы другую цену за тот же слот.
        val parsed = mapOf("standard" to mapOf("priceLadder" to listOf(1000, null, 5000))).toEconomyConstants()

        assertEquals(bootstrap.standard.priceLadder, parsed.standard.priceLadder)
    }

    @Test
    fun `a negative fraction is a refusal, not a zero`() {
        // Сервер делает floor(-0.5) = -1 и отказывает; toLong() дал бы 0 и принял бы пустой потолок.
        val parsed = mapOf("standard" to mapOf("maxOwned" to -0.5)).toEconomyConstants()

        assertEquals(bootstrap.standard.maxOwned, parsed.standard.maxOwned)
    }

    @Test
    fun `a whole number in any numeric shape is read as itself`() {
        val parsed =
            mapOf(
                "version" to 7L,
                "standard" to mapOf("maxOwned" to 12, "regenMs" to 1_800_000.0, "priceLadder" to listOf(500L, 900)),
            ).toEconomyConstants()

        assertEquals(7L, parsed.version)
        assertEquals(12, parsed.standard.maxOwned)
        assertEquals(1_800_000L, parsed.standard.regenMs)
        assertEquals(listOf(500L, 900L), parsed.standard.priceLadder)
    }

    @Test
    fun `a value past Int range does not wrap into a small ceiling`() {
        val parsed = mapOf("standard" to mapOf("maxOwned" to 4_294_967_301L)).toEconomyConstants()

        assertEquals(Int.MAX_VALUE, parsed.standard.maxOwned)
    }

    @Test
    fun `gold premium offers survive the ride, a corrupt row drops out whole`() {
        // История 2.2: прейскурант показывает то, что назвал сервер. Одна битая строка выпадает,
        // а не превращается в нулевую цену на витрине.
        val parsed =
            mapOf(
                "premiumGoldOffers" to
                    listOf(
                        mapOf("sku" to "premium_30d", "days" to 30, "goldPrice" to 500L),
                        mapOf("sku" to "broken", "days" to 0, "goldPrice" to 500L),
                        mapOf("sku" to "priceless", "days" to 7),
                        "not-a-row",
                    ),
            ).toEconomyConstants()

        assertEquals(listOf(PremiumGoldOffer(sku = "premium_30d", days = 30, goldPrice = 500L)), parsed.premiumGoldOffers)
        assertEquals(emptyList<PremiumGoldOffer>(), mapOf<String, Any>().toEconomyConstants().premiumGoldOffers)
    }

    @Test
    fun `an impossible term drops the row whole instead of being clipped into a fake one`() {
        // Испорченная строка не должна выживать подменой срока: 10^12 суток, зажатые в Int.MAX,
        // выглядели бы предложением сервера, которого он не делал.
        val parsed =
            mapOf(
                "premiumGoldOffers" to
                    listOf(
                        mapOf("sku" to "premium_30d", "days" to 30, "goldPrice" to 500L),
                        mapOf("sku" to "eternal", "days" to 1_000_000_000_000L, "goldPrice" to 500L),
                        mapOf("sku" to "negative", "days" to -5L, "goldPrice" to 500L),
                    ),
            ).toEconomyConstants()

        assertEquals(listOf(PremiumGoldOffer(sku = "premium_30d", days = 30, goldPrice = 500L)), parsed.premiumGoldOffers)
    }
}
