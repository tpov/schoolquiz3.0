package com.tpov.schoolquiz.shared.core.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Как игроку показывается время последнего успешного прогона.
 *
 * Абсолютная дата, а не «12 минут назад»: относительная форма врёт ровно там, где важна правда —
 * на устройстве, чьи часы сдвинуты, и на экране, который открыли и оставили открытым. Формат
 * числовой и одинаковый во всех локалях: он состоит из цифр, и переводить в нём нечего.
 *
 * Живёт в общем коде рядом со снимком, а не в двух экранах: шторка и настройки показывают одно и
 * то же время, и разъехаться они не должны.
 */
fun formatSyncTimestamp(epochMillis: Long): String = formatSyncTimestamp(epochMillis, TimeZone.currentSystemDefault())

/** Та же формула с явной зоной — чтобы тест не зависел от зоны машины, на которой он идёт. */
internal fun formatSyncTimestamp(
    epochMillis: Long,
    timeZone: TimeZone,
): String {
    val moment = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return buildString {
        append(moment.dayOfMonth.pad2())
        append('.')
        append(moment.monthNumber.pad2())
        append('.')
        append(moment.year)
        append(' ')
        append(moment.hour.pad2())
        append(':')
        append(moment.minute.pad2())
    }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()
