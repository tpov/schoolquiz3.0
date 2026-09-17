package com.tpov.schoolquiz.shared.core.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Страж модели снимка: поля-суммы в ней запрещены (AD-14, критерий истории 4.1).
 *
 * Одно число «ожидает» на все состояния скрывает и конфликт, и карантин — ровно те два случая,
 * где игроку есть что делать. Пока такое поле существует, его рано или поздно покажут: оно короче
 * четырёх и выглядит удобнее. Поэтому запрет стоит на модели, а не на экране.
 *
 * Читается исходник, а не рефлексия: свойство с телом `get() = ...` в общем коде рефлексией не
 * отличить от вычисляемого признака, а запрещено здесь именно сложение состояний. Прецедент
 * файлового стража — `TypeConvertersPhase02Test` и `OwnerScopedTablesGuardTest`.
 */
class SyncStatusModelGuardTest {

    private val model = File(
        "src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/SyncStatus.kt",
    )

    /** Имена, под которыми сумма уже жила в модели и была снята разбором эпика 4. */
    private val bannedNames = listOf("hasPending", "needsAttention")

    /** Слагаемые: числа по состояниям очереди и две суммы над ними. */
    private val stateCounts =
        listOf("waiting", "waitingPrecondition", "conflicted", "quarantined", "pending", "stuck")

    @Test
    fun `the snapshot model declares no field summing the queue states`() {
        if (!model.exists()) return // запуск вне каталога модуля
        val body = model.readText().declarationsOnly()

        val offenders = bannedNames.filter { body.contains("val $it") }

        assertTrue(
            offenders.isEmpty(),
            "поле-сумма в модели снимка запрещено критерием 4.1 — числа по состояниям " +
                "показываются раздельно: $offenders",
        )
    }

    @Test
    fun `no property in the snapshot model adds two state counts together`() {
        if (!model.exists()) return
        val body = model.readText().declarationsOnly()

        val sums =
            Regex("""val\s+(\w+)[^\n]*get\(\)\s*=\s*([^\n]+)""")
                .findAll(body)
                .filter { match ->
                    val expression = match.groupValues[2]
                    expression.contains("+") &&
                        stateCounts.count { expression.contains("counts.$it") } >= 1
                }
                .map { it.groupValues[1] }
                .toList()

        assertTrue(sums.isEmpty(), "свойство модели складывает состояния очереди: $sums")
    }

    @Test
    fun `the snapshot model reads no precomputed sum off the counts`() {
        if (!model.exists()) return
        val body = model.readText().declarationsOnly()

        val used = listOf("counts.pending", "counts.stuck").filter { body.contains(it) }

        assertTrue(
            used.isEmpty(),
            "модель снимка читает сумму по состояниям — считать их вместе некому: $used",
        )
    }

    /** Без комментариев: запрет на поле, а не на слово о нём. */
    private fun String.declarationsOnly(): String =
        lineSequence()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .filterNot { it.trimStart().startsWith("/*") }
            .joinToString("\n")
}
