package com.tpov.schoolquiz.shared.feature.quest.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Карантин переноса на полку: откатывать нечего (AD-25), последствие — пометка (AD-28).
 * Обработчик узнаёт квест по ссылке на сущность, не разбирая тело записи.
 */
class QuestShelfQuarantineMarkTest {

    private fun record(
        operation: String = QuestShelfOutbox.OPERATION,
        entityRef: String? = "quest.QUEST:q-1",
    ) = OutboxRecord(
        id = 7L,
        mutationId = "quest-SET_SHELF-intent-1",
        ownerUid = "uid-dev",
        operation = operation,
        payload = """{"questId":"q-1","targetShelf":"arena"}""",
        entityRef = entityRef,
        state = OutboxState.QUARANTINED,
        createdAtMs = 1L,
        lastError = "Developer level is required",
    )

    @Test
    fun `given our quarantined record then the quest is marked with the server's reason`() = runTest {
        val marked = mutableListOf<Pair<QuestId, String?>>()
        val handler = QuestShelfQuarantineMark { questId, reason -> marked += questId to reason }

        handler.onQuarantined(record())

        assertEquals(listOf<Pair<QuestId, String?>>(QuestId("q-1") to "Developer level is required"), marked)
    }

    @Test
    fun `given a record of another feature then nothing is marked`() = runTest {
        val marked = mutableListOf<Pair<QuestId, String?>>()
        val handler = QuestShelfQuarantineMark { questId, reason -> marked += questId to reason }

        handler.onQuarantined(record(operation = "lesson_runner.SUBMIT_RATING", entityRef = "lesson_runner:rating:1"))
        handler.onQuarantined(record(entityRef = null))

        assertTrue(marked.isEmpty())
    }

    @Test
    fun `entity ref round trip`() {
        val ref = QuestShelfOutbox.entityRef(QuestId("q-42"))

        assertEquals("quest.QUEST:q-42", ref)
        assertEquals(QuestId("q-42"), QuestShelfOutbox.questIdOf(ref))
        assertEquals(null, QuestShelfOutbox.questIdOf("quest.QUEST:"))
        assertEquals(null, QuestShelfOutbox.questIdOf("quest_authoring:draft:q-42"))
    }
}
