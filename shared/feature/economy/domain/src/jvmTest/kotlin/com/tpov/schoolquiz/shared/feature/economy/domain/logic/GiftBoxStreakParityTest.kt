package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Серия коробок — одна формула на двух языках.
 *
 * Устройство копит серию без связи, сервер сверяет её при синхронизации: разойдись они — честный
 * игрок увидел бы коробку, которую сервер потом не признал. Набор порождён сервером и читается
 * здесь. JVM-тест, а не общий: файл с диска общий код не видит; разбор — регулярными выражениями,
 * потому что зависимости на JSON у модуля нет, а тащить её ради десятка полей дороже.
 */
class GiftBoxStreakParityTest {

    private data class Advance(val name: String, val stored: GiftBoxStreak, val nowMs: Long, val expected: GiftBoxStreak)

    private val advances: List<Advance> by lazy {
        val file = File("../../../../config/gift-box-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        val text = file.readText()
        val section = text.substring(text.indexOf("\"advances\""), text.indexOf("\"claims\""))
        Regex(
            """\{\s*"name":\s*"([^"]+)",\s*"stored":\s*\{([^}]*)},\s*"nowMs":\s*(\d+),\s*"expected":\s*\{([^}]*)}""",
        ).findAll(section).map { m ->
            Advance(
                name = m.groupValues[1],
                stored = streakOf(m.groupValues[2]),
                nowMs = m.groupValues[3].toLong(),
                expected = streakOf(m.groupValues[4]),
            )
        }.toList()
    }

    /** Поля по имени; отсутствующее — ноль, как и на сервере. Старые имена полей клиент не хранит. */
    private fun streakOf(body: String): GiftBoxStreak {
        fun field(name: String): Long =
            Regex(""""$name"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toLong() ?: 0L
        return GiftBoxStreak(
            boxCount = field("boxCount").toInt(),
            streakDays = field("boxStreakDays").toInt(),
            nextBoxAtMs = field("nextBoxAtMs"),
        )
    }

    @Test
    fun `given the shared fixtures then every visit lands where the server puts it`() {
        assertTrue(advances.size >= 6, "набор фикстур не разобран: ${advances.size}")

        val mismatches =
            advances
                // Старые имена полей (countBox…) — забота сервера: у клиента их нет.
                .filterNot { it.name.contains("старые имена") }
                .mapNotNull { case ->
                    val ours = case.stored.visited(case.nowMs)
                    if (ours == case.expected) null else "${case.name}: сервер ${case.expected}, клиент $ours"
                }

        assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))
    }
}
