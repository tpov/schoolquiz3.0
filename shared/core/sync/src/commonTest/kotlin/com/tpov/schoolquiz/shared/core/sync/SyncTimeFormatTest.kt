package com.tpov.schoolquiz.shared.core.sync

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Время последнего успеха показывается датой и временем, а не сырым числом.
 *
 * Тест держит именно форму: экран шторки и группа настроек показывают результат этой функции, и
 * если она однажды отдаст «1756800000000», оба экрана покажут это игроку.
 */
class SyncTimeFormatTest {

    @Test
    fun `moment is shown as day, month, year and time, never as a raw number`() {
        val utc = TimeZone.UTC

        val shown = formatSyncTimestamp(epochMillis = 1_756_800_000_000L, timeZone = utc)

        assertEquals("02.09.2025 08:00", shown)
    }

    @Test
    fun `single-digit day, month, hour and minute keep their leading zero`() {
        // Без дополнения «1.9.2025 8:5» читается как обрывок, а не как время.
        val utc = TimeZone.UTC

        val shown = formatSyncTimestamp(epochMillis = 1_756_713_900_000L, timeZone = utc)

        assertEquals("01.09.2025 08:05", shown)
    }
}
