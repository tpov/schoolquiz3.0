package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak.Companion.DAY_MS
import kotlin.test.Test
import kotlin.test.assertEquals

/** Серия идёт и без связи; «подряд» значит подряд. */
class GiftBoxStreakTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun days(n: Int): GiftBoxStreak {
        var state = GiftBoxStreak.NONE
        repeat(n) { i -> state = state.visited(t0 + i * DAY_MS) }
        return state
    }

    @Test
    fun `given ten days in a row then the tenth visit is the first box`() {
        assertEquals(0, days(9).boxCount)
        assertEquals(1, days(10).boxCount)
        assertEquals(2, days(11).boxCount)
    }

    @Test
    fun `given a second visit within the day then nothing moves`() {
        val first = GiftBoxStreak.NONE.visited(t0)

        assertEquals(first, first.visited(t0 + 5 * hour))
    }

    @Test
    fun `given a missed day then the streak restarts and the boxes stay`() {
        val twelve = days(12)

        val late = twelve.visited(t0 + 12 * DAY_MS + DAY_MS + hour)

        assertEquals(1, late.streakDays)
        assertEquals(twelve.boxCount, late.boxCount, "накопленные коробки не сгорают")
    }

    @Test
    fun `given days away then one visit is one day, not the days away`() {
        val back = days(12).visited(t0 + 12 * DAY_MS + 5 * DAY_MS)

        assertEquals(1, back.streakDays)
        assertEquals(days(12).boxCount, back.boxCount)
    }
}
