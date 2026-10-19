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

    private data class ClaimCase(
        val name: String,
        val stored: GiftBoxStreak,
        val claimedStreak: Int,
        val claimedBoxes: Int,
        val nowMs: Long,
        val allowedStreak: Int,
        val allowedBoxes: Int,
    )

    private val claims: List<ClaimCase> by lazy {
        val file = File("../../../../config/gift-box-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        val section = file.readText().substringAfter("\"claims\"")
        section
            .split(Regex("""\{\s*"name":\s*"""))
            .drop(1)
            .map { chunk ->
                fun body(from: String, to: String): String = chunk.substringAfter(from).substringBefore(to)
                fun field(body: String, name: String): Int =
                    Regex(""""$name"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toInt() ?: 0
                val stored = streakOf(body("\"stored\"", "\"claimed\""))
                val claimedBody = body("\"claimed\"", "\"nowMs\"")
                ClaimCase(
                    name = chunk.substringBefore("\"").ifEmpty { "безымянная заявка" },
                    stored = stored,
                    claimedStreak = field(claimedBody, "boxStreakDays"),
                    claimedBoxes = field(claimedBody, "boxesEarned"),
                    nowMs = Regex(""""nowMs"\s*:\s*(\d+)""").find(chunk)?.groupValues?.get(1)?.toLong() ?: 0L,
                    allowedStreak = field(chunk, "allowedStreak"),
                    allowedBoxes = field(chunk, "allowedBoxes"),
                )
            }
    }

    /**
     * Заявка устройства — те же потолки, что и приём сервера.
     *
     * Фикстуры описывают серверный вердикт по избыточным и скромным заявкам; клиент, зажавший свой
     * счёт этими же потолками ещё до отправки, обязан получить ровно разрешённое: ни коробки
     * сверх [allowedBoxes], ни дня сверх [allowedStreak] — серверу нечего вычитать и в аудит
     * перерасхода нечего писать.
     */
    @Test
    fun `given the shared claim fixtures then the device clamps its count where the server accepts it`() {
        assertTrue(claims.size >= 5, "набор заявок не разобран: ${claims.size}")

        val mismatches =
            claims.mapNotNull { case ->
                val counted =
                    case.stored.copy(
                        boxCount = case.stored.boxCount + case.claimedBoxes,
                        streakDays = case.claimedStreak,
                    )
                val ours = GiftBoxAccrual.claim(case.stored, counted, case.nowMs)
                val serverAllowed = OfflineAccrualClaim(case.allowedStreak, case.allowedBoxes)
                if (ours == serverAllowed) null else "${case.name}: сервер принимает $serverAllowed, клиент заявляет $ours"
            }

        assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))
    }
}
