package com.tpov.schoolquiz.shared.core.scoring

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Одна формула, два языка.
 *
 * Цена открытия урока живёт и на сервере (`functions/lesson-unlocks.js`), и здесь — иначе очередь
 * не может показать цену до отправки, а AD-3 требует, чтобы отложенное действие выводилось из
 * синхронизированного контента. Две реализации одной формулы расходятся ровно так же неизбежно,
 * как расходились три способа записи версии, поэтому набор фикстур общий: он порождён сервером и
 * читается здесь. Разъедутся — упадёт сборка.
 *
 * JVM-тест, а не общий: файл с диска общий код не видит.
 */
class UnlockPricingParityTest {

    private val fixtures: List<Case> by lazy {
        val file = File("../../../config/unlock-price-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        parse(file.readText())
    }

    private data class Case(
        val kind: String,
        val easy: Long,
        val hard: Long,
        val price: Long,
    )

    /**
     * Минимальный разбор — общей зависимости на JSON у этого модуля нет, а тащить её ради теста
     * дороже, чем прочитать четыре поля.
     */
    private fun parse(text: String): List<Case> =
        Regex(
            """\{\s*"kind":\s*"([^"]+)",\s*"easyAllocatedSeconds":\s*(\d+),""" +
                """\s*"hardAllocatedSeconds":\s*(\d+),\s*"price":\s*(\d+)\s*}""",
        ).findAll(text).map { m ->
            Case(
                kind = m.groupValues[1],
                easy = m.groupValues[2].toLong(),
                hard = m.groupValues[3].toLong(),
                price = m.groupValues[4].toLong(),
            )
        }.toList()

    @Test
    fun `given the shared fixtures then every price matches the server`() {
        assertTrue(fixtures.isNotEmpty(), "набор фикстур пуст — значит проверять нечего")

        val mismatches =
            fixtures.mapNotNull { case ->
                val kind =
                    when (case.kind) {
                        "lesson" -> UnlockPricing.Kind.LESSON
                        "hardMode" -> UnlockPricing.Kind.HARD_MODE
                        else -> error("неизвестный вид разблокировки: ${case.kind}")
                    }
                val ours = UnlockPricing.price(kind, case.easy, case.hard)
                if (ours == case.price) null else "${case.kind} ${case.easy}/${case.hard}: сервер ${case.price}, у нас $ours"
            }

        assertTrue(mismatches.isEmpty(), "формула разъехалась с серверной:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `given a lesson with no questions then it still costs something`() {
        // Дверь, которая открывается даром, — не дверь.
        assertEquals(1L, UnlockPricing.price(UnlockPricing.Kind.LESSON, 0L, 0L))
    }

    @Test
    fun `given hard mode alone then it costs less than the whole lesson`() {
        val whole = UnlockPricing.price(UnlockPricing.Kind.LESSON, 720L, 480L)
        val hardOnly = UnlockPricing.price(UnlockPricing.Kind.HARD_MODE, 720L, 480L)

        assertTrue(hardOnly < whole, "покупка одной сложности не может стоить как весь урок")
    }

    @Test
    fun `given a bigger lesson then it costs more to skip`() {
        val small = UnlockPricing.price(UnlockPricing.Kind.LESSON, 720L, 480L)
        val big = UnlockPricing.price(UnlockPricing.Kind.LESSON, 2160L, 1440L)

        assertEquals(small * 3, big, "цена растёт вместе с отведённым временем, ровно линейно")
    }
}
