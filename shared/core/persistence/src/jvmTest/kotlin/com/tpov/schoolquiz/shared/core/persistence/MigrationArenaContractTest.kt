package com.tpov.schoolquiz.shared.core.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Миграция не ставит в очередь мутацию, которую приёмник отвергает всегда.
 *
 * Тело записи миграция собирает средствами SQLite — из тех колонок, что лежали в прежней таблице.
 * Для версионируемой операции этого мало по устройству приёмника: сверка версии читает поля из
 * `payload` ещё до обработчика (`MUTATION_VERSION_SOURCES` в `functions/index.js`), и если тело их
 * не несёт, ответом будет `invalid-argument` — то есть окончательный отказ, карантин без повторов
 * и откат локальной половины. Действие игрока при этом теряется, а «причина отказа», которую он
 * увидит, — текст, написанный для разработчика.
 *
 * Так и жил перенос заявки на арену: `quest_authoring.SUBMIT_ARENA` версионируема, а в теле,
 * собранном миграцией, объекта `draft` нет вовсе. Проверка кросс-языковая по образцу
 * `MigrationKeyContractTest`: общего кода у этих двух сторон нет и быть не может.
 */
class MigrationArenaContractTest {

    private val migrationsDir = File("src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations")
    private val receiver = File("../../../functions/index.js")

    /** Имена операций, для которых сервер сверяет версию по полям тела. */
    private fun versionedOperations(): Set<String> {
        val source = receiver.readText()
        val body =
            Regex("""const MUTATION_VERSION_SOURCES = \{(.*?)\n};""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
        assertTrue("не нашёл MUTATION_VERSION_SOURCES в ${receiver.path}", body != null)
        return Regex("""^\s{2}"?([A-Za-z0-9_.]+)"?:""", RegexOption.MULTILINE)
            .findAll(body!!)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Имена операций, которые миграции вставляют в `outbox`. */
    private fun migratedOperations(): Map<String, Set<String>> =
        migrationsDir.listFiles().orEmpty().filter { it.extension == "kt" }.associate { file ->
            val text = file.readText()
            val constants =
                Regex("""private const val (OPERATION_\w+) = "([^"]+)"""")
                    .findAll(text)
                    .associate { it.groupValues[1] to it.groupValues[2] }
            // Считается только имя, действительно подставленное в INSERT INTO `outbox`.
            val inserted =
                constants
                    .filterKeys { constant -> "'\$$constant'" in text }
                    .values
                    .toSet()
            file.name to inserted
        }

    @Test
    fun `migrations never queue a versioned operation`() {
        if (!migrationsDir.exists() || !receiver.exists()) return
        val versioned = versionedOperations()
        assertTrue("реестр версионируемых операций пуст — проверять нечего", versioned.isNotEmpty())

        val offenders =
            migratedOperations()
                .mapValues { (_, operations) -> operations intersect versioned }
                .filterValues { it.isNotEmpty() }

        assertEquals(
            "миграция ставит в очередь версионируемую операцию, а тело собирает из прежних колонок: " +
                "сверка версии читает поля, которых в нём нет, и такая запись отвергается всегда. " +
                offenders,
            emptyMap<String, Set<String>>(),
            offenders,
        )
    }

    /**
     * Заявка, которую нельзя перевезти, обязана быть снята локально — и по-человечески.
     *
     * Просто не переносить строку мало: её локальная половина — статус `REVIEW_QUEUED` — заперла бы
     * черновик навсегда, потому что открывающей его заявки больше не существует.
     */
    @Test
    fun `the arena draft is handed back to its author instead of being queued`() {
        if (!migrationsDir.exists()) return
        val text = File(migrationsDir, "Migration5to6.kt").readText()

        assertTrue(
            "миграция обязана вернуть черновик в DRAFT, иначе он заперт навсегда",
            Regex("""UPDATE\s+`quest_drafts`""").containsMatchIn(text) &&
                "'DRAFT'" in text &&
                "rejectionReason" in text,
        )
        assertTrue(
            "причина обязана быть на человеческом языке, а не текстом приёмника",
            "draft.catalogId" !in Regex("""`rejectionReason` = '([^']*)'""").find(text)?.groupValues?.get(1).orEmpty(),
        )
    }
}
