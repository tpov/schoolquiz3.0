package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Читатель приватного журнала понимает обе формы записи (AD-11).
 *
 * Сервер перешёл на «документ на узел»: тело стало `{type, id, changedAtMs}`, а каталог уехал в
 * id документа. Прежняя форма держала каталог и квест в теле. Пока backfill не привёл журнал к
 * одной форме, читатель обязан понимать обе — иначе приватная синхронизация автора слепнет ровно
 * в тот момент, когда сервер выкатили раньше клиента.
 */
class PrivateQuestSyncChangeParseTest {

    @Test
    fun `new form takes the quest from the body and the catalog from the document id`() {
        val change =
            privateQuestSyncChangeOrNull(
                documentId = "cat-1_quest-9",
                bodyId = "quest-9",
                bodyCatalogId = null,
                bodyQuestId = null,
                changedAtMs = 1_700L,
            )

        assertEquals("cat-1", change?.catalogId)
        assertEquals("quest-9", change?.questId)
        assertEquals(1_700L, change?.changedAtMs)
    }

    @Test
    fun `underscores in either id do not split the pair in the wrong place`() {
        val change =
            privateQuestSyncChangeOrNull(
                documentId = "my_catalog_my_quest",
                bodyId = "my_quest",
                bodyCatalogId = null,
                bodyQuestId = null,
                changedAtMs = 5L,
            )

        // Деление по первому подчёркиванию дало бы каталог "my" и квест "catalog_my_quest".
        assertEquals("my_catalog", change?.catalogId)
        assertEquals("my_quest", change?.questId)
    }

    @Test
    fun `legacy form still reads while the journal holds both`() {
        val change =
            privateQuestSyncChangeOrNull(
                documentId = "какой-угодно-старый-id",
                bodyId = null,
                bodyCatalogId = "cat-2",
                bodyQuestId = "quest-3",
                changedAtMs = 42L,
            )

        assertEquals("cat-2", change?.catalogId)
        assertEquals("quest-3", change?.questId)
    }

    @Test
    fun `new body wins over legacy fields when a record carries both`() {
        val change =
            privateQuestSyncChangeOrNull(
                documentId = "cat-new_quest-new",
                bodyId = "quest-new",
                bodyCatalogId = "cat-old",
                bodyQuestId = "quest-old",
                changedAtMs = 7L,
            )

        assertEquals("cat-new", change?.catalogId)
        assertEquals("quest-new", change?.questId)
    }

    @Test
    fun `a record whose document id does not carry the catalog falls back instead of guessing`() {
        // id документа не кончается на "_quest-9" — каталога в нём нет. Прежних полей тоже нет:
        // выдумывать каталог нельзя, запись уходит в unreadable.
        assertNull(
            privateQuestSyncChangeOrNull(
                documentId = "quest-9",
                bodyId = "quest-9",
                bodyCatalogId = null,
                bodyQuestId = null,
                changedAtMs = 1L,
            ),
        )
    }

    @Test
    fun `a record without a time is unreadable in both forms`() {
        assertNull(
            privateQuestSyncChangeOrNull(
                documentId = "cat-1_quest-9",
                bodyId = "quest-9",
                bodyCatalogId = "cat-1",
                bodyQuestId = "quest-9",
                changedAtMs = null,
            ),
        )
    }

    @Test
    fun `blank pieces never build a change`() {
        assertNull(
            privateQuestSyncChangeOrNull(
                documentId = "_quest-9",
                bodyId = "quest-9",
                bodyCatalogId = "",
                bodyQuestId = "",
                changedAtMs = 3L,
            ),
        )
    }

    @Test
    fun `strict reading no longer accepts the old form, and tolerant reading still does`() {
        // Терпимость к прежней форме снимается тем же переключателем, что и в остальных трёх
        // журналах, и только после backfill (AD-11, AD-13).
        val legacy = { policy: JournalReadPolicy ->
            privateQuestSyncChangeOrNull(
                documentId = "какой-то-append-лог-id",
                bodyId = null,
                bodyCatalogId = "cat-1",
                bodyQuestId = "quest-9",
                changedAtMs = 1_700L,
                policy = policy,
            )
        }

        assertNull(legacy(JournalReadPolicy.STRICT))
        assertEquals("quest-9", legacy(JournalReadPolicy.TOLERANT)?.questId)
    }

    @Test
    fun `strict reading still accepts the current form`() {
        val change =
            privateQuestSyncChangeOrNull(
                documentId = "cat-1_quest-9",
                bodyId = "quest-9",
                bodyCatalogId = null,
                bodyQuestId = null,
                changedAtMs = 1_700L,
                policy = JournalReadPolicy.STRICT,
            )

        assertEquals("cat-1", change?.catalogId)
    }
}
