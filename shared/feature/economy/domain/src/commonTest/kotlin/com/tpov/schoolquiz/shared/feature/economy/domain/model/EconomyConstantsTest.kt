package com.tpov.schoolquiz.shared.feature.economy.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Прейскурант и лестницы цен — то, ради чего таблица вообще стала серверной. */
class EconomyConstantsTest {

    private val constants = EconomyConstants.BOOTSTRAP

    @Test
    fun `given the price list then a full tank buys two tournaments`() {
        // Соотношение и есть модель: полный бак — тысяча очков, турнир — пятьсот.
        val tank = constants.standard.maxOwned * EconomyConstants.POINTS_PER_CHARGE

        assertEquals(1000, tank)
        assertEquals(2, tank / constants.priceOf(ActivityKind.TOURNAMENT))
        assertEquals(3, tank / constants.priceOf(ActivityKind.FINAL_EXAM))
        assertEquals(30, tank / constants.priceOf(ActivityKind.ORDINARY_LESSON))
    }

    @Test
    fun `given an ordinary lesson then it still costs what it cost before`() {
        // Единственное число, унаследованное без изменения: цена урока в старом приложении.
        assertEquals(33, constants.priceOf(ActivityKind.ORDINARY_LESSON))
    }

    @Test
    fun `given every activity then it has a price`() {
        // Вид без цены — это попытка, за которую не берут ничего, то есть бесплатный турнир.
        assertTrue(ActivityKind.entries.all { constants.priceOf(it) > 0 })
    }

    @Test
    fun `given a table missing a price then it is refused rather than defaulting to free`() {
        val hole = EconomyConstants.BOOTSTRAP_PRICES - ActivityKind.TOURNAMENT

        assertFailsWith<IllegalArgumentException> { EconomyConstants(activityPrices = hole) }
    }

    @Test
    fun `given the plasma ladder then all three cost six gold in total`() {
        val plasma = constants.plasma

        assertEquals(listOf(1L, 2L, 3L), (0..2).map { plasma.slotPrice(it) })
        assertEquals(6L, (0..2).sumOf { plasma.slotPrice(it) })
    }

    @Test
    fun `given a ceiling raised past the ladder then the last rung repeats`() {
        // Иначе слот сверх лестницы стоил бы ноль — то есть поднять потолок значило бы раздать
        // заряды даром, и заметил бы это не тот, кто поднимал.
        val wideCeiling = constants.standard.copy(maxOwned = 12)

        assertEquals(20_000L, wideCeiling.slotPrice(4))
        assertEquals(20_000L, wideCeiling.slotPrice(11))
    }

    @Test
    fun `given an empty ladder then the table is refused`() {
        assertFailsWith<IllegalArgumentException> {
            constants.standard.copy(priceLadder = emptyList())
        }
    }

    @Test
    fun `given plasma then it is monetary and waits for a settled account`() {
        // Отсюда и берётся «плазма только на связи»: монетарный ресурс не расходуется вслепую.
        assertEquals(ShopCurrency.GOLD, constants.plasma.currency)
        assertTrue(constants.plasma.requiresSettledAccount)
        assertEquals(ShopCurrency.NOLICS, constants.standard.currency)
        assertTrue(!constants.standard.requiresSettledAccount, "обычный заряд тратится и офлайн")
    }

    @Test
    fun `given the bootstrap copy then it is version zero, so a real table always wins`() {
        // Настоящая таблица начинается с единицы: сравнение версий обязано отличать «ещё ничего не
        // приезжало» от «приехало и вот оно».
        assertEquals(0L, EconomyConstants.BOOTSTRAP.version)
    }
}
