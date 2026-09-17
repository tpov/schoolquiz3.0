package com.tpov.schoolquiz.shared.core.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Способов удалить узел ровно три, и четвёртый не появляется (AD-26).
 *
 * 1. Идентификатор, не вернувшийся из `refreshByIds`, — сущности больше нет, стираем локально.
 * 2. Пустой `visibleOn` — квест снят со всех полок, убираем с устройства.
 * 3. `archived` — узел скрыт. Строка и связанный с ней прогресс остаются в базе, из выборок узел
 *    уходит фильтром самого запроса (AC 7.6). У квеста `archived` вдобавок маркер полки «Архив».
 *
 * **Первые три проверяются поведением, а не текстом файлов** — по одному тесту на каждый из шести
 * контентных репозиториев:
 * `CatalogDeletionSemanticsTest`, `QuestDeletionSemanticsTest`, `SectionDeletionSemanticsTest`,
 * `ThemeDeletionSemanticsTest`, `LessonDeletionSemanticsTest`, `QuestionRepositoryImplTest`.
 * Прежде здесь стоял поиск подстрок, и он проходил бы, даже если ветку удаления убрать совсем:
 * слово `archived` встречалось в тех же файлах в фильтрах показа, к удалению отношения не имеющих.
 *
 * Здесь остаётся ровно то, что поведением не проверяется: четвёртый способ изобретают, добавляя
 * поле, — и увидеть это можно только просмотром всего дерева. Поле `deleted` не вводится: retention
 * tombstone здесь неприменим — журнал изменений уже говорит, что узел тронули, а состояние узла
 * читается из него самого.
 */
class DeletionSemanticsTest {

    private val repositoryRoot = File("../../..")

    @Test
    fun `given the whole tree then no fourth mechanism named 'deleted' has appeared`() {
        // Четвёртый способ изобретают, не зная о трёх. Поле с таким именем — самый вероятный вид.
        // Клиент, сервер и скрипты — целиком: AC 7.6 говорит про весь код чтения и все модели, а
        // сид-скрипты и бэкфиллы пишут те же документы, что сервер. Прежде `android`, `apps` и
        // `scripts` в обход не входили, и поле, заведённое в UI-модели или в сиде, здесь не ловилось.
        val roots = listOf("shared", "platform", "functions", "firestore.rules", "android", "apps", "scripts")
        var swept = 0
        val sweptByRoot = mutableMapOf<String, Int>()
        val offenders =
            roots.flatMap { root ->
                val start = File(repositoryRoot, root)
                if (!start.exists()) return@flatMap emptyList()
                val files =
                    if (start.isFile) sequenceOf(start) else start.walkTopDown().filter { it.isFile }
                files
                    .filterNot { it.path.contains("/build/") || it.path.contains("/node_modules/") }
                    .filter { it.extension in setOf("kt", "js", "rules") }
                    // Только то, что приложение и сервер действительно пишут и читают. Фейк в
                    // тесте волен называть свою бухгалтерию как угодно — `db.deleted` в фейке
                    // Firestore не механизм удаления узла, а список того, что фейк стёр у себя.
                    // Четвёртый способ появляется в рабочем коде, и там он и ловится.
                    .filterNot { it.isTestSource() }
                    .onEach {
                        swept++
                        sweptByRoot[root] = (sweptByRoot[root] ?: 0) + 1
                    }
                    .filter { file -> DELETED_FIELD.containsMatchIn(file.readText()) }
                    .map { it.path.removePrefix(repositoryRoot.path).trimStart('/') }
                    .toList()
            }

        // Пустой результат обязан значить «не нашли», а не «не искали»: путь до корня считается
        // от рабочей папки модуля, и ошибись он — тест молча проходил бы всегда. Порог пересчитан
        // под расширенный состав корней: сегодня просматривается около 1350 файлов.
        assertTrue(swept > 1_200, "просмотрено всего $swept файлов — путь до корня, похоже, не тот")
        // Именно те три корня, которых в обходе не было: молчаливый ноль по любому из них снова
        // сделал бы защиту пустой ровно там, где её отсутствие не заметят.
        val unswept = listOf("android", "apps", "scripts").filter { (sweptByRoot[it] ?: 0) == 0 }
        assertEquals(emptyList(), unswept, "не просмотрены совсем: " + unswept.joinToString())
        assertEquals(
            emptyList(),
            offenders,
            "поле `deleted` — четвёртый способ удаления, которого AD-26 не допускает:\n" +
                offenders.joinToString("\n"),
        )
    }

    private fun File.isTestSource(): Boolean =
        name.endsWith("Test.kt") ||
            name.endsWith(".test.js") ||
            TEST_SOURCE_DIRS.any { path.contains(it) }

    private companion object {
        val TEST_SOURCE_DIRS =
            listOf("/src/test/", "/src/commonTest/", "/src/jvmTest/", "/src/androidTest/", "/src/androidInstrumentedTest/")

        /** Объявление или чтение поля с таким именем; слова вроде `deletedCount` не в счёт. */
        val DELETED_FIELD =
            Regex("""(val|var)\s+deleted\s*[:=]|["'`]deleted["'`]\s*:|\.deleted\b|\bdata\.deleted\b""")
    }
}
