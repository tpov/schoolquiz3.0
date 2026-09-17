package com.tpov.schoolquiz.shared.core.outbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Половина NFR4 про `entity_ref`: ссылка живёт в том же пространстве имён, что и операция.
 *
 * Проверять это в момент постановки — единственный момент, когда расхождение ещё видно. Дальше оно
 * выглядит как молчание: пометка не встаёт ни на одну сущность, потому что спрашивают про ссылку,
 * которую никто не клал, и отличить это от «нечего показывать» нельзя.
 */
class OutboxNamespaceTest {

    private val now = 1_700_000_000_000L

    private fun record(
        operation: String,
        entityRef: String?,
    ) = OutboxRecord(
        id = 0L,
        mutationId = "m-1",
        ownerUid = "uid-1",
        operation = operation,
        payload = "{}",
        state = OutboxState.WAITING,
        createdAtMs = now,
        entityRef = entityRef,
    )

    @Test
    fun `given a reference from another feature then the record is not queued`() = runTest {
        val store = FakeOutboxStore()

        val failure =
            assertFailsWith<IllegalArgumentException> {
                store.enqueue(record(OutboxOperations.SUBMIT_ATTEMPT, "quest.RATING:7"))
            }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("quest"), "в тексте названо пространство ссылки: $message")
        assertTrue(message.contains("lesson_runner"), "и пространство операции: $message")
        assertTrue(store.all.isEmpty(), "отвергнутая запись не оставляет строки")
    }

    @Test
    fun `given both forms of the separator then the same namespace is accepted`() = runTest {
        val store = FakeOutboxStore()

        store.enqueue(record(OutboxOperations.SUBMIT_ATTEMPT, "lesson_runner:attempt:a1"))
        store.enqueue(record(OutboxOperations.SUBMIT_REVIEW, "quest_authoring.ASSIGNMENT:7").copy(mutationId = "m-2"))

        assertEquals(2, store.all.size)
    }

    @Test
    fun `given no reference then the record is queued as before`() = runTest {
        val store = FakeOutboxStore()

        store.enqueue(record(OutboxOperations.UNLOCK_LESSON, entityRef = null))

        assertEquals(1, store.all.size, "запись без пометки законна — сущности у неё просто нет")
    }

    @Test
    fun `the namespace is the name up to the first separator, and the rest is the feature's business`() {
        assertEquals("lesson_runner", OutboxNamespace.of("lesson_runner.SUBMIT_ATTEMPT"))
        assertEquals("lesson_runner", OutboxNamespace.of("lesson_runner:attempt:a1"))
        assertEquals("quest", OutboxNamespace.of("quest.QUEST:7"))
        // Имя без разделителя — само себе пространство: ядро не додумывает за фичу.
        assertEquals("UNLOCK_LESSON", OutboxNamespace.of("UNLOCK_LESSON"))
    }

    /**
     * Вторая половина NFR4: у самой операции пространство имён обязано быть.
     *
     * Имя без разделителя — само себе пространство ([OutboxNamespace.of] возвращает его целиком),
     * то есть ни одна ссылка вида `<фича>.<что-то>` к нему подойти не может: постановка такой
     * записи упадёт, и писателю останется либо выдумать ссылку в пространстве имени операции, либо
     * обойтись без ссылки — и оставить карточку сущности без пометки «не отправлено» (AD-14).
     * Ровно так и жила разблокировка урока: единственное голое имя в списке.
     */
    @Test
    fun `every declared operation carries the namespace of the feature that owns it`() {
        val declared =
            listOf(
                OutboxOperations.UNLOCK_LESSON,
                OutboxOperations.SUBMIT_ATTEMPT,
                OutboxOperations.SUBMIT_RATING,
                OutboxOperations.SUBMIT_ARENA,
                OutboxOperations.SUBMIT_REVIEW,
                OutboxOperations.SET_SHELF,
                OutboxOperations.CANCEL_NICKNAME_LISTING,
                OutboxOperations.CANCEL_LOGO_LISTING,
            )

        val bare = declared.filter { OutboxNamespace.of(it) == it }

        assertEquals(
            emptyList(),
            bare,
            "имя операции без пространства имён владеющей фичи (NFR4): $bare",
        )
    }
}
