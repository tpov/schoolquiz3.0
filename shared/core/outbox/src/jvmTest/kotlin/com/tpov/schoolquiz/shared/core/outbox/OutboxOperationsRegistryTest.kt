package com.tpov.schoolquiz.shared.core.outbox

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Клиент и приёмник называют операции одинаково.
 *
 * Имя операции — часть контракта (AD-6): незнакомую приёмник отвергает окончательно, и такая
 * запись уходит в карантин навсегда, а локальное изменение остаётся расходиться с сервером. Ловить
 * это в бою нечем — отказ выглядит как обычный отказ сервера.
 *
 * Ловушка тут не гипотетическая. В `functions/index.js` идентификатор `UNLOCK_LESSON` уже занят:
 * он импортирован из `lesson-unlocks.js` и означает вид покупки, строку `"lesson"`. Ключ реестра
 * пишется голым именем, то есть литералом `"UNLOCK_LESSON"`, и совпадает с клиентским — но всякая
 * естественная «уборка» вида `[UNLOCK_LESSON]: handler` молча зарегистрировала бы обработчик под
 * `"lesson"`, и каждая отложенная разблокировка стала бы неизвестной операцией.
 *
 * Поэтому реестр читается текстом. JVM-тест, а не общий: файл с диска общий код не видит.
 */
class OutboxOperationsRegistryTest {

    private val source: String by lazy {
        val file = File("../../../functions/index.js")
        assertTrue(file.exists(), "нет приёмника мутаций: ${file.absolutePath}")
        file.readText()
    }

    /** Ключи объектного литерала `MUTATION_HANDLERS`, в кавычках и без. */
    private val registered: Set<String> by lazy {
        val body =
            Regex("""const MUTATION_HANDLERS = \{(.*?)\n};""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue(body != null, "не нашёл реестр MUTATION_HANDLERS в functions/index.js")
        Regex("""^\s{2}"?([A-Za-z0-9_.]+)"?:""", RegexOption.MULTILINE)
            .findAll(body!!)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Всё, что клиент умеет ставить в очередь. */
    private val declared =
        setOf(
            OutboxOperations.UNLOCK_LESSON,
            OutboxOperations.SUBMIT_ATTEMPT,
            OutboxOperations.SUBMIT_RATING,
            OutboxOperations.SUBMIT_ARENA,
        )

    @Test
    fun `given every operation the client can queue then the server has a handler for it`() {
        val orphans = declared - registered

        assertEquals(
            emptySet(),
            orphans,
            "эти операции клиент отправит, а приёмник отвергнет окончательно: $orphans\n" +
                "реестр знает: $registered",
        )
    }

    @Test
    fun `given every handler on the server then the client declares its name`() {
        // Обработчик, чьего имени клиент не знает, — либо забытое переименование, либо операция,
        // которую некому поставить в очередь. Оба случая стоит увидеть до боя.
        val unreachable = registered - declared

        assertEquals(emptySet(), unreachable, "обработчики, до которых клиенту не дотянуться: $unreachable")
    }

    @Test
    fun `given the registry then no key is spelled as a bare identifier the file redefines`() {
        // `UNLOCK_LESSON:` — литерал, и работает; `[UNLOCK_LESSON]:` — импортированная константа
        // со значением "lesson", и не работает. Разница в двух скобках.
        val computed = Regex("""^\s{2}\[[A-Za-z0-9_.]+]:""", RegexOption.MULTILINE)
            .findAll(source.substringAfter("const MUTATION_HANDLERS = {").substringBefore("\n};"))
            .map { it.value.trim() }
            .toList()

        assertEquals(emptyList(), computed, "ключ реестра, посчитанный из переменной: $computed")
    }
}
