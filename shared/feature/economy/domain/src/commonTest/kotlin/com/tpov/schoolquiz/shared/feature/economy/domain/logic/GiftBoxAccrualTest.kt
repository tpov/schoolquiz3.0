package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Математика офлайн-заявки: зажим местного счёта до серверных потолков ДО отправки.
 *
 * Сервер пересчитает то же самое по своим часам (`boxAccrualVerdict`); эти тесты держат клиентскую
 * половину честной — испорченные часы не должны превращаться в завышенную заявку, потому что
 * излишек сервер записывает в аудит перерасхода (CAP-7), а принятый излишек честнее не становится.
 */
class GiftBoxAccrualTest {
    private val base = GiftBoxStreak(boxCount = 1, streakDays = 10, nextBoxAtMs = T + DAY_MS)

    @Test
    fun `honest offline week claims exactly seven days and seven boxes`() =
        runTest {
            val counted = base.copy(boxCount = 8, streakDays = 17, nextBoxAtMs = T + 8 * DAY_MS)

            val claim = GiftBoxAccrual.claim(base, counted, T + 7 * DAY_MS + 3 * HOUR_MS)

            assertEquals(OfflineAccrualClaim(17, 7), claim)
        }

    @Test
    fun `clock racing forward cannot claim more days than elapsed since the last visit`() =
        runTest {
            // Счёт, как будто устройство отсчитало десять дней, хотя прошло трое: часы ушли вперёд.
            val counted = base.copy(boxCount = 11, streakDays = 20, nextBoxAtMs = T + 11 * DAY_MS)

            val claim = GiftBoxAccrual.claim(base, counted, T + 3 * DAY_MS)

            assertEquals(OfflineAccrualClaim(13, 3), claim)
        }

    @Test
    fun `clock set backwards claims nothing above the base streak`() =
        runTest {
            // now раньше последнего визита: суток с последнего слова не прошло, потолок — база.
            val counted = base.copy(boxCount = 2, streakDays = 11, nextBoxAtMs = T + DAY_MS)

            val claim = GiftBoxAccrual.claim(base, counted, T - DAY_MS)

            assertEquals(OfflineAccrualClaim(10, 0), claim)
        }

    @Test
    fun `streak below ten earns no boxes however long the absence`() =
        runTest {
            val shortBase = GiftBoxStreak(boxCount = 0, streakDays = 5, nextBoxAtMs = T + DAY_MS)
            val counted = shortBase.copy(boxCount = 3, streakDays = 8, nextBoxAtMs = T + 4 * DAY_MS)

            val claim = GiftBoxAccrual.claim(shortBase, counted, T + 3 * DAY_MS + HOUR_MS)

            assertEquals(OfflineAccrualClaim(8, 0), claim)
        }

    @Test
    fun `first ever server word claims at most one day and zero boxes`() =
        runTest {
            val counted = GiftBoxStreak(boxCount = 5, streakDays = 30, nextBoxAtMs = 0L)

            val claim = GiftBoxAccrual.claim(GiftBoxStreak.NONE, counted, T)

            assertEquals(OfflineAccrualClaim(1, 0), claim)
        }

    @Test
    fun `modest count is accepted as counted, never topped up`() =
        runTest {
            val counted = base.copy(boxCount = 2, streakDays = 11, nextBoxAtMs = T + 2 * DAY_MS)

            val claim = GiftBoxAccrual.claim(base, counted, T + 5 * DAY_MS)

            assertEquals(OfflineAccrualClaim(11, 1), claim)
        }

    @Test
    fun `negative counted boxes never become a claim`() =
        runTest {
            // Счёт от другого слова сервера: разница отрицательна — коробкой не считается.
            val counted = base.copy(boxCount = 0, streakDays = 12, nextBoxAtMs = T + 3 * DAY_MS)

            val claim = GiftBoxAccrual.claim(base, counted, T + 3 * DAY_MS)

            assertEquals(0, claim.boxesEarned)
        }

    @Test
    fun `negative now is clamped to zero rather than throwing`() {
        assertEquals(OfflineAccrualClaim(10, 0), GiftBoxAccrual.claim(base, base, -1L))
    }

    @Test
    fun `negative claim fields are refused at the door`() {
        assertFailsWith<IllegalArgumentException> { OfflineAccrualClaim(-1, 0) }
        assertFailsWith<IllegalArgumentException> { OfflineAccrualClaim(0, -1) }
    }

    @Test
    fun `a day seen by the local model is never granted twice by the claim math`() =
        runTest {
            // Каждый день серии сверх базы требует собственного визита; один и тот же визит,
            // повторённый внутри суток, local моделью не считается — и в заявке не растёт.
            val afterOneVisit = base.visited(T + DAY_MS + HOUR_MS)
            val repeatedSameWindow = afterOneVisit.visited(T + DAY_MS + 2 * HOUR_MS)

            assertEquals(afterOneVisit, repeatedSameWindow)
            assertEquals(
                1,
                GiftBoxAccrual.claim(base, repeatedSameWindow, T + DAY_MS + 2 * HOUR_MS).boxesEarned,
            )
            assertTrue(
                GiftBoxAccrual.claim(base, repeatedSameWindow, T + DAY_MS + 2 * HOUR_MS).boxStreakDays >
                    base.streakDays,
            )
        }

    private companion object {
        const val T = 1_700_000_000_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24L * HOUR_MS
    }
}
