package com.tpov.schoolquiz.shared.feature.economy.domain.logic

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ActivityKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Вид активности — одно правило на двух языках.
 *
 * Сервер выводит его по своим документам и по нему списывает; клиент повторяет правило по полкам,
 * которые у него уже есть, чтобы назвать цену до запуска. Разойдись они — игрок увидит одно число,
 * а спишется другое, и объяснить это будет нечем.
 *
 * JVM-тест, а не общий: файл с диска общий код не видит. Разбор минимальный — общей зависимости на
 * JSON у этого модуля нет, а тащить её ради четырёх полей дороже, чем прочитать их.
 */
class ActivityKindParityTest {

    private data class Case(
        val name: String,
        val visibleOn: List<String>,
        val isPrivate: Boolean,
        val shelves: List<String>,
        val kind: String,
    )

    private val fixtures: List<Case> by lazy {
        val file = File("../../../../config/activity-kind-fixtures.json")
        assertTrue(file.exists(), "нет общего набора фикстур: ${file.absolutePath}")
        parse(file.readText())
    }

    private fun parse(text: String): List<Case> =
        Regex(
            """"name":\s*"([^"]*)",\s*"visibleOn":\s*\[([^]]*)],\s*"isPrivate":\s*(true|false),""" +
                """\s*"shelves":\s*\[([^]]*)],\s*"kind":\s*"([A-Z_]+)"""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(text).map { m ->
            Case(
                name = m.groupValues[1],
                visibleOn = strings(m.groupValues[2]),
                isPrivate = m.groupValues[3].toBoolean(),
                shelves = strings(m.groupValues[4]),
                kind = m.groupValues[5],
            )
        }.toList()

    private fun strings(body: String): List<String> =
        Regex(""""((?:[^"\\]|\\.)*)"""").findAll(body).map { it.groupValues[1] }.toList()

    @Test
    fun `given the shared fixtures then every kind matches the server`() {
        assertTrue(fixtures.isNotEmpty(), "набор фикстур пуст — значит проверять нечего")

        val mismatches =
            fixtures.mapNotNull { case ->
                val ours = ActivityKindRule.of(case.visibleOn, isPrivate = case.isPrivate)
                if (ours == ActivityKind.valueOf(case.kind)) null else "${case.name}: сервер ${case.kind}, клиент $ours"
            }

        assertEquals(emptyList(), mismatches, "клиент и сервер называют вид по-разному:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `given a shelf with an invisible byte-order mark then it is still that shelf`() {
        // `String.prototype.trim` на сервере режет BOM, `Char.isWhitespace` в Kotlin — нет. Полка
        // с ним была бы турниром для сервера и обычным уроком здесь, то есть 500 очков против 33.
        assertEquals(ActivityKind.TOURNAMENT, ActivityKindRule.of(listOf("\uFEFFtournament")))
        assertEquals(listOf("home"), ActivityKindRule.shelvesOf(listOf(" \uFEFF HOME \uFEFF ")))
    }

    @Test
    fun `given the shared fixtures then the shelves are ordered the same way`() {
        // Порядок и есть правило: первая полка — самая дорогая, и по ней считается цена.
        val mismatches =
            fixtures.mapNotNull { case ->
                val ours = ActivityKindRule.shelvesOf(case.visibleOn)
                if (ours == case.shelves) null else "${case.name}: сервер ${case.shelves}, клиент $ours"
            }

        assertEquals(emptyList(), mismatches, mismatches.joinToString("\n"))
    }
}
