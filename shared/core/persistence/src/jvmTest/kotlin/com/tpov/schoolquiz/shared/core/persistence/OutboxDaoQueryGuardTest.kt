package com.tpov.schoolquiz.shared.core.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Страж текста запросов очереди (AD-14, NFR1).
 *
 * Читается исходник, а не поведение: у обеих проверяемых вещей поведение может остаться верным при
 * неверной форме, и заметить это будет нечем. Счётчиков по-прежнему четыре, если вместо одного
 * запроса сделать четыре, — но каждая эмиссия станет четырьмя чтениями таблицы. Пометка сущности
 * по-прежнему верна, если тащить строку целиком, — но `payload` будет читаться и разбираться на
 * каждое изменение очереди, ровно то, что NFR1 запрещает.
 *
 * Прецедент такого стража в модуле — `OwnerScopedTablesGuardTest`, `TypeConvertersPhase02Test`.
 */
class OutboxDaoQueryGuardTest {

    private val source: String by lazy {
        val file = File("src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/OutboxDao.kt")
        assertTrue("Исходник OutboxDao не найден по пути ${file.absolutePath}", file.exists())
        file.readText()
    }

    /** Текст `@Query`, стоящего прямо над функцией с этим именем. */
    private fun queryOf(function: String): String {
        val tail = """\s*,?\s*\)\s*(?:suspend\s+)?fun\s+$function\b"""
        val multiline = Regex(""""{3}(((?!"{3}).)*)"{3}$tail""", RegexOption.DOT_MATCHES_ALL)
        val single = Regex(""""([^"]*)"$tail""", RegexOption.DOT_MATCHES_ALL)
        val match = multiline.find(source) ?: single.find(source)
        assertTrue("У функции $function нет @Query — запрос перестал быть одним оператором SQL", match != null)
        return match!!.groupValues[1]
    }

    @Test
    fun `the counts are read by one query, scoped to the owner, and it never names payload`() {
        val counts = queryOf("observeCounts")

        assertEquals(
            "запрос счётчиков должен быть один: четыре запроса на одну эмиссию — четыре чтения таблицы",
            1,
            Regex("""\bSELECT\b""", RegexOption.IGNORE_CASE).findAll(counts).count(),
        )
        assertTrue(
            "счётчики обязаны быть по владельцу (AD-8): $counts",
            counts.contains("owner_uid = :ownerUid"),
        )
        assertTrue("в тексте запроса счётчиков нет payload: $counts", !counts.contains("payload"))
        assertTrue("и нет SELECT * — возвращается проекция: $counts", !counts.contains("SELECT *"))
    }

    @Test
    fun `the counts query names every state it counts, and the synchronous key is not one of them`() {
        val counts = queryOf("observeCounts")

        listOf("WAITING", "WAITING_PRECONDITION", "CONFLICT", "QUARANTINED").forEach { state ->
            assertTrue("состояние $state в запросе счётчиков не названо: $counts", counts.contains("'$state'"))
        }
        assertTrue(
            "ключ синхронной мутации в счётчики не входит (AD-2): $counts",
            !counts.contains("'SYNCHRONOUS'"),
        )
    }

    @Test
    fun `the counts query returns a projection, not the entity`() {
        val signature =
            Regex("""fun\s+observeCounts\s*\([^)]*\)\s*:\s*([^\n]+)""").find(source)?.groupValues?.get(1).orEmpty()

        assertTrue("счётчики возвращают проекцию, а не строку таблицы: $signature", signature.contains("OutboxCountsRow"))
        assertTrue("и не OutboxEntity: $signature", !signature.contains("OutboxEntity"))
    }

    @Test
    fun `the entity mark is read without payload and puts quarantine ahead of waiting`() {
        val mark = queryOf("observeByEntityRef")

        assertTrue("пометка сущности не читает payload (NFR1): $mark", !mark.contains("payload"))
        assertTrue("и не тащит строку целиком: $mark", !mark.contains("SELECT *"))
        assertTrue("пометка по владельцу (AD-8): $mark", mark.contains("owner_uid = :ownerUid"))
        val order = mark.substringAfter("ORDER BY")
        assertTrue(
            "конфликт идёт перед карантином, карантин — перед ожиданием: $order",
            order.indexOf("'CONFLICT'") in 0 until order.indexOf("'QUARANTINED'"),
        )
    }
}
