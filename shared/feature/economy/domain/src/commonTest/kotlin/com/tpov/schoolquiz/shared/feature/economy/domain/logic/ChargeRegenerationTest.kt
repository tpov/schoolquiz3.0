package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ChargeRules
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Восстановление зарядов: непрерывное, без потери прогресса и без конфискации. */
class ChargeRegenerationTest {

    private val standard = ChargeRules.STANDARD_BOOTSTRAP
    private val hour = 60L * 60 * 1000
    private val perPoint = hour / EconomyConstants.POINTS_PER_CHARGE

    private fun balance(points: Int, at: Long = 0L) = ChargeBalance(points, at)

    @Test
    fun `given an hour then exactly one charge comes back`() {
        val after = balance(0).regenerated(standard, nowMs = hour)

        assertEquals(EconomyConstants.POINTS_PER_CHARGE, after.points)
        assertEquals(1, after.wholeCharges)
    }

    @Test
    fun `given part of an interval then the progress toward the next point survives`() {
        // Иначе игрок, заглядывающий каждые полминуты, не получал бы вообще ничего: каждый заход
        // сбрасывал бы недобранный остаток.
        val start = balance(0)

        val once = start.regenerated(standard, nowMs = perPoint + perPoint / 2)
        val twice = once.regenerated(standard, nowMs = 2 * perPoint)

        assertEquals(1, once.points)
        assertEquals(2, twice.points, "недобранная половина интервала обязана дожить до следующего очка")
    }

    @Test
    fun `given a full tank then idle time does not build up a backlog`() {
        // Задолженность за простой означала бы мгновенное наполнение после траты — то есть
        // бесконечные заряды у того, кто просто давно не заходил.
        val full = balance(standard.maxOwned * EconomyConstants.POINTS_PER_CHARGE, at = 0L)

        val afterAWeek = full.regenerated(standard, nowMs = 7 * 24 * hour)
        val spent = afterAWeek.spend(EconomyConstants.POINTS_PER_CHARGE).balance
        val minuteLater = spent.regenerated(standard, nowMs = 7 * 24 * hour + perPoint)

        assertEquals(EconomyConstants.POINTS_PER_CHARGE * standard.maxOwned - 99, minuteLater.points)
    }

    @Test
    fun `given a ceiling that was lowered then nothing is taken away`() {
        // Прежняя серверная реализация зажимала прочитанное потолком, то есть понижение молча
        // отбирало заряды. Купленное игроком не отбирается решением на сервере.
        val lowered = standard.copy(maxOwned = 2)
        val held = balance(5 * EconomyConstants.POINTS_PER_CHARGE, at = 0L)

        val after = held.regenerated(lowered, nowMs = 10 * hour)

        assertEquals(5, after.wholeCharges, "понижение потолка не конфискует")
    }

    @Test
    fun `given a balance above a lowered ceiling then it stops growing`() {
        // Вторая половина того же правила: не отбираем, но и не доливаем — иначе понижение потолка
        // не значило бы ничего вовсе.
        val lowered = standard.copy(maxOwned = 2)
        val held = balance(5 * EconomyConstants.POINTS_PER_CHARGE)

        assertEquals(held.points, held.regenerated(lowered, nowMs = 100 * hour).points)
        assertFalse(lowered.canBuySlot(owned = 5), "и купить ещё один слот сверх потолка нельзя")
    }

    @Test
    fun `given plasma then a whole charge takes a day`() {
        val plasma = ChargeRules.PLASMA_BOOTSTRAP

        val afterADay = balance(0).regenerated(plasma, nowMs = 24 * hour)
        val afterHalf = balance(0).regenerated(plasma, nowMs = 12 * hour)

        assertEquals(1, afterADay.wholeCharges)
        assertEquals(0, afterHalf.wholeCharges, "полсуток — это ещё не заряд")
    }

    @Test
    fun `given a clock that went backwards then nothing is granted and nothing is lost`() {
        val held = balance(250, at = 10 * hour)

        val after = held.regenerated(standard, nowMs = 5 * hour)

        assertEquals(held, after)
    }

    @Test
    fun `given a kind that does not regenerate then time changes nothing`() {
        val never = standard.copy(regenMs = 0L)

        assertEquals(0, balance(0).regenerated(never, nowMs = 1000 * hour).points)
    }

    @Test
    fun `given not enough points then the spend is refused and the balance is untouched`() {
        // Попытка может честно приехать после того, как очки потрачены в другом месте: сервер
        // остаётся источником истины и просто не платит за неё.
        val held = balance(32)

        val spend = held.spend(33)

        assertFalse(spend.affordable)
        assertEquals(held, spend.balance)
    }

    @Test
    fun `given exactly enough points then the spend goes through`() {
        val spend = balance(33).spend(33)

        assertTrue(spend.affordable)
        assertEquals(0, spend.balance.points)
    }
}
