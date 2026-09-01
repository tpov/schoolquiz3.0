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
 * 3. `archived` — узел скрыт. Для квеста он остаётся маркером: у квестов есть полка «Архив», и
 *    открыть его оттуда по требованию надо уметь. У остальных узлов такой полки нет, и «скрыть»
 *    совпадает с «убрать локально».
 *
 * Поля `deleted` нет и не будет: retention tombstone здесь неприменим — журнал изменений уже
 * говорит, что узел тронули, а состояние узла читается из него самого.
 *
 * Договор проверяется по исходникам, потому что нарушают его не поведением, а тем, что добавляют
 * четвёртый способ, не зная о трёх: сегодня контракт живёт только в шести файлах реализации.
 */
class DeletionSemanticsTest {

    private val repositoryRoot = File("../../..")

    private val contentRepositories =
        listOf(
            "shared/core/catalog/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImpl.kt",
            "shared/feature/quest/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRepositoryImpl.kt",
            "shared/feature/section/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/data/SectionRepositoryImpl.kt",
            "shared/feature/theme/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/theme/data/ThemeRepositoryImpl.kt",
            "shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt",
            "shared/feature/question/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/question/data/QuestionRepositoryImpl.kt",
        )

    private fun source(path: String): String {
        val file = File(repositoryRoot, path)
        assertTrue(file.exists(), "нет файла реализации: ${file.absolutePath}")
        return file.readText()
    }

    @Test
    fun `given every content repository then a node that stopped coming back is removed`() {
        // Первый способ. Без него узел, удалённый на сервере, живёт на устройстве вечно: журнал
        // сообщает, что его тронули, `refreshByIds` возвращает на один меньше — и всё.
        val missing =
            contentRepositories.filterNot { path ->
                val text = source(path)
                text.contains("requested - returned") && text.contains("deleteById")
            }

        assertEquals(emptyList(), missing, "не стирают невернувшийся идентификатор:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `given the quest repository then an empty shelf list takes the quest off the device`() {
        // Второй способ, и он только у квеста: полки есть лишь у него.
        assertTrue(
            source(contentRepositories[1]).contains("visibleOn.isEmpty()"),
            "квест, снятый со всех полок, обязан уходить с устройства",
        )
    }

    @Test
    fun `given every node below the quest then archived removes it locally`() {
        // Третий способ. У квеста он намеренно другой — архивный квест остаётся маркером для
        // полки «Архив», и это закреплено собственным тестом в его модуле.
        val below = contentRepositories.filter { it.contains("/section/") || it.contains("/theme/") ||
            it.contains("/lesson/") || it.contains("/question/") || it.contains("/catalog/") }
        val missing = below.filterNot { source(it).contains("archived") }

        assertEquals(emptyList(), missing, "не реагируют на archived:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `given the whole tree then no fourth mechanism named 'deleted' has appeared`() {
        // Четвёртый способ изобретают, не зная о трёх. Поле с таким именем — самый вероятный вид.
        val roots = listOf("shared", "platform", "functions", "firestore.rules")
        var swept = 0
        val offenders =
            roots.flatMap { root ->
                val start = File(repositoryRoot, root)
                if (!start.exists()) return@flatMap emptyList()
                val files =
                    if (start.isFile) sequenceOf(start) else start.walkTopDown().filter { it.isFile }
                files
                    .filterNot { it.path.contains("/build/") || it.path.contains("/node_modules/") }
                    .filter { it.extension in setOf("kt", "js", "rules") }
                    .filterNot { it.name == "DeletionSemanticsTest.kt" }
                    .onEach { swept++ }
                    .filter { file -> DELETED_FIELD.containsMatchIn(file.readText()) }
                    .map { it.path.removePrefix(repositoryRoot.path).trimStart('/') }
                    .toList()
            }

        // Пустой результат обязан значить «не нашли», а не «не искали»: путь до корня считается
        // от рабочей папки модуля, и ошибись он — тест молча проходил бы всегда.
        assertTrue(swept > 500, "просмотрено всего $swept файлов — путь до корня, похоже, не тот")
        assertEquals(
            emptyList(),
            offenders,
            "поле `deleted` — четвёртый способ удаления, которого AD-26 не допускает:\n" +
                offenders.joinToString("\n"),
        )
    }

    private companion object {
        /** Объявление или чтение поля с таким именем; слова вроде `deletedCount` не в счёт. */
        val DELETED_FIELD =
            Regex("""(val|var)\s+deleted\s*[:=]|["'`]deleted["'`]\s*:|\.deleted\b|\bdata\.deleted\b""")
    }
}
