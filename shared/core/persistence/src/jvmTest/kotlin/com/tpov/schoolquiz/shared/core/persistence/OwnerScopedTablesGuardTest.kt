package com.tpov.schoolquiz.shared.core.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Страж закрытого перечня (история 6.2).
 *
 * Новая сущность с колонкой владельца, не внесённая в [OwnerScopedTables], роняет сборку с именем
 * этой сущности. Иначе перечень пополняется молча: таблица заводится, строки прежнего владельца в
 * ней остаются навсегда, и заметить это можно только по чужим данным на экране.
 *
 * Читается исходник, а не схема: перечень обязан отставать не от миграции, а от объявления
 * сущности — в тот же коммит.
 */
class OwnerScopedTablesGuardTest {

    /** Как в коде называют владельца. Из этих имён и состоит признак «таблица чья-то». */
    private val ownerColumns = setOf("uid", "user_id", "owner_uid", "ownerUid")

    private val tableName = Regex("""tableName\s*=\s*"([^"]+)"""")
    private val columnInfoName = Regex("""@ColumnInfo\s*\(\s*name\s*=\s*"([^"]+)"""")
    private val property = Regex("""\bval\s+(\w+)\s*:""")

    @Test
    fun `every entity with an owner column is named in the registry`() {
        val srcDir = File("src/commonMain")
        if (!srcDir.exists()) return

        val declared = OwnerScopedTables.ALL.associateBy { it.table }
        val missing =
            srcDir.walkTopDown()
                .filter { it.extension == "kt" }
                .mapNotNull { file ->
                    val text = file.readText()
                    val table = tableName.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
                    val owner = text.ownerColumn() ?: return@mapNotNull null
                    Triple(file.name, table, owner)
                }
                .filter { (_, table, _) -> table !in declared }
                .toList()

        assertTrue(
            "Сущность с колонкой владельца не внесена в OwnerScopedTables — строки прежнего " +
                "владельца в ней останутся навсегда: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the registry names the same owner column the entity declares`() {
        val srcDir = File("src/commonMain")
        if (!srcDir.exists()) return

        val declaredColumns =
            srcDir.walkTopDown()
                .filter { it.extension == "kt" }
                .mapNotNull { file ->
                    val text = file.readText()
                    val table = tableName.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
                    val owner = text.ownerColumn() ?: return@mapNotNull null
                    table to owner
                }
                .toMap()

        OwnerScopedTables.ALL.forEach { entry ->
            assertEquals(
                "перечень и сущность разошлись в колонке владельца для ${entry.table}",
                declaredColumns[entry.table],
                entry.ownerColumn,
            )
        }
    }

    @Test
    fun `the queue is named in the registry and cleaned by the queue operation`() {
        // Иначе страж уронил бы сборку на самой очереди: колонка владельца у неё тоже есть, а
        // снимает её другая операция.
        val outbox = OwnerScopedTables.ALL.single { it.table == "outbox" }

        assertEquals(OwnerCleanup.QUEUE, outbox.cleanedBy)
        assertTrue(
            "остальные таблицы перечня снимает вторая операция",
            OwnerScopedTables.ALL.filter { it.table != "outbox" }.all { it.cleanedBy == OwnerCleanup.TRACES },
        )
    }

    @Test
    fun `every table cleaned by the traces operation is also asked who owns its rows`() {
        // Один перечень должен и удалять, и называть. Таблица, попавшая в удаление, но забытая в
        // опросе, оставляет владельца невидимым — а невидимого не забудут: пустая очередь тогда
        // читается как «терять нечего», и строки прежнего владельца остаются на устройстве навсегда.
        val query = ownerRowsDao()?.queryBefore("ownersWithTraces") ?: return
        val missing =
            OwnerScopedTables.ALL
                .filter { it.cleanedBy == OwnerCleanup.TRACES }
                .filterNot { query.contains("FROM ${it.table}\n") || query.contains("FROM ${it.table} ") }

        assertTrue(
            "таблица перечня не спрашивается в ownersWithTraces — её владелец останется невидимым: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every named loss is asked about by its own table`() {
        // Вид данных, названный игроку, но не спрошенный у базы, превращает вопрос в неправду:
        // либо про потерю не скажут вовсе, либо скажут про несуществующую.
        val query = ownerRowsDao()?.queryBefore("namedTraces") ?: return
        val missing =
            OwnerScopedTables.ALL
                .filter { it.namedToPlayer != null }
                .filterNot { query.contains("FROM ${it.table} ") }

        assertTrue("вид данных называется игроку, но у базы про него не спрашивают: $missing", missing.isEmpty())
    }

    @Test
    fun `the queue is not asked about by the traces query`() {
        // Очередь — первая операция. Спросить про неё здесь значит посчитать её владельца дважды и
        // назвать «личным следом» то, что снимает `OutboxStore.forget`.
        val query = ownerRowsDao()?.queryBefore("ownersWithTraces") ?: return

        assertTrue("очередь спрашивают отдельно — у неё своя операция", !query.contains("FROM outbox"))
    }

    private fun ownerRowsDao(): String? {
        val file = File("src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/OwnerRowsDao.kt")
        return if (file.exists()) file.readText() else null
    }

    /** Текст `@Query`, стоящего прямо перед этой функцией. */
    private fun String.queryBefore(function: String): String {
        val at = indexOf("fun $function")
        assertTrue("в OwnerRowsDao нет $function", at > 0)
        return substring(lastIndexOf("@Query", at), at)
    }

    /** Колонка владельца этой сущности — по `@ColumnInfo(name = ...)` или по имени свойства. */
    private fun String.ownerColumn(): String? {
        val columns = columnInfoName.findAll(this).map { it.groupValues[1] }
        val properties = property.findAll(this).map { it.groupValues[1] }
        return (columns + properties).firstOrNull { it in ownerColumns }
    }
}
