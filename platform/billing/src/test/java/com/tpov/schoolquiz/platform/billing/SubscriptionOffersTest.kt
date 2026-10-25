package com.tpov.schoolquiz.platform.billing

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SubscriptionOffersTest {
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
    fun `the trial offer wins over the bare base plan`() {
        assertSame(trial, preferredSubscriptionOffer(listOf(basePlan, trial)))
    }

    @Test
    fun `without a trial the first offer answers`() {
        assertSame(basePlan, preferredSubscriptionOffer(listOf(basePlan)))
    }

    @Test
    fun `a free first phase is what makes an offer a trial`() {
        val discount = SubscriptionOfferShape(
            offerToken = "token-discount",
            phases = listOf(
                PricePhaseShape(100_000_000L, "100 ₴", "UAH"),
                PricePhaseShape(200_000_000L, "200 ₴", "UAH"),
            ),
        )
        // Скидка — не триал: ноль первой фазы решает, потому что триал финальной модели бесплатен.
        assertSame(discount, preferredSubscriptionOffer(listOf(discount, basePlan)))
    }

    @Test
    fun `a subscription without offers cannot be opened`() {
        assertNull(preferredSubscriptionOffer(emptyList()))
    }

    @Test
    fun `an offer without pricing phases cannot open the flow`() {
        // Покупать из оффера нечего: поток из него не открывается, и цена с него не читается.
        assertNull(preferredSubscriptionOffer(listOf(emptyPhases)))
    }

    @Test
    fun `an offer without pricing phases is skipped for one that can open`() {
        // Пустой список фаз у первого оффера не прячет живой базовый план за ним.
        assertSame(basePlan, preferredSubscriptionOffer(listOf(emptyPhases, basePlan)))
    }
}
