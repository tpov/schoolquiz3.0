package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable цитаты платы одинаково (история 4.11).
 *
 * Клиент стреляет по строке имени; расхождение в бою выглядит как обычный NOT_FOUND сервера.
 * Реестр читается текстом — тот же приём, что у [LifecycleCallableNamesTest].
 *
 * Имя [ReviewFeeQuoteCallableNames] внутреннее; тест живёт в том же модуле и видит его.
 */
class ReviewFeeQuoteCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `fee quote callable is registered as a read-only view`() {
        val pattern =
            Regex("""exports\.${ReviewFeeQuoteCallableNames.QUOTE} = onCall\(CALLABLE_OPTIONS, async \(request\) => \{""")
        assertTrue(
            "exports.${ReviewFeeQuoteCallableNames.QUOTE} обязан быть read-only view на CALLABLE_OPTIONS",
            pattern.containsMatchIn(source),
        )
    }

    @Test
    fun `fee quote callable is registered exactly once and never rides the mutation queue`() {
        val registrations = Regex("""exports\.${ReviewFeeQuoteCallableNames.QUOTE}\b""").findAll(source).toList()
        assertEquals("callable цитаты регистрируется ровно один раз", 1, registrations.size)
        assertEquals(
            "цитата — чтение: строки в реестре мутаций быть не должно",
            1,
            Regex(ReviewFeeQuoteCallableNames.QUOTE).findAll(source).count(),
        )
    }
}
