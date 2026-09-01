package com.tpov.schoolquiz.shared.core.outbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Разрешение конфликта — это новая мутация, а не второй заход прежней (AD-24, AD-2).
 *
 * Повтор под тем же ключом не применит ничего: сервер этот ключ уже видел и отверг. Поэтому
 * проверяется не «состояние сменилось на ЖДЁТ», а то, что получилась именно другая запись — с
 * другим ключом, с версией сервера в ожидаемой и с собственным моментом намерения.
 */
class OutboxConflictResolutionTest {

    private val conflicted =
        OutboxRecord(
            id = 42L,
            mutationId = "quest_authoring-SUBMIT_ARENA-submission-1",
            ownerUid = "author-1",
            operation = OutboxOperations.SUBMIT_ARENA,
            payload = """{"draftId":"draft-1"}""",
            state = OutboxState.CONFLICT,
            createdAtMs = 900L,
            entityRef = "quest_authoring:draft:draft-1",
            expectedVersion = 4L,
            serverVersion = 7L,
            attemptCount = 3,
            nextRetryAtMs = 1_500L,
            lastError = "VersionConflict",
        )

    @Test
    fun `given a conflict then the replacement carries a new key and the server version`() {
        val replacement = OutboxConflictResolution.replacementFor(conflicted, nowMs = 5_000L)

        assertNotEquals(conflicted.mutationId, replacement.mutationId, "тот ключ сервер уже отверг")
        assertEquals("quest_authoring-SUBMIT_ARENA-submission-1-r7", replacement.mutationId)
        assertEquals(7L, replacement.expectedVersion, "отправляем поверх версии, что на сервере")
        assertNull(replacement.serverVersion, "расхождения у новой записи ещё нет")
        assertEquals(OutboxState.WAITING, replacement.state)
        assertEquals(0, replacement.attemptCount, "накопленное прежней записью её и осталось")
        assertEquals(0L, replacement.nextRetryAtMs)
        assertNull(replacement.lastError)
        assertEquals(5_000L, replacement.createdAtMs, "момент намерения новый — возраст с нуля")
        assertEquals(0L, replacement.id, "строки ещё нет, ключ присвоит база")
    }

    @Test
    fun `given a conflict then the operation payload and entity stay verbatim`() {
        // Ядро в тело не смотрит и смысла не знает (AD-7): что означает «отправить заново»,
        // знает только фича, чей обработчик зарегистрирован под этим именем операции.
        val replacement = OutboxConflictResolution.replacementFor(conflicted, nowMs = 5_000L)

        assertEquals(conflicted.operation, replacement.operation)
        assertEquals(conflicted.payload, replacement.payload)
        assertEquals(conflicted.entityRef, replacement.entityRef)
        assertEquals(conflicted.ownerUid, replacement.ownerUid)
    }

    @Test
    fun `given the same conflict resolved twice then the key is the same`() {
        // Два нажатия и переигранная транзакция — одно намерение. Случайный ключ развёл бы его на
        // две операции, и работа автора уехала бы дважды.
        val first = OutboxConflictResolution.replacementFor(conflicted, nowMs = 5_000L)
        val second = OutboxConflictResolution.replacementFor(conflicted, nowMs = 9_000L)

        assertEquals(first.mutationId, second.mutationId)
    }

    @Test
    fun `given a conflict the server did not number then the key still differs from the old one`() {
        val numberless = conflicted.copy(serverVersion = null)

        val replacement = OutboxConflictResolution.replacementFor(numberless, nowMs = 5_000L)

        assertEquals("quest_authoring-SUBMIT_ARENA-submission-1-rnone", replacement.mutationId)
        assertNull(replacement.expectedVersion, "сверять нечем — приёмник пропустит без проверки")
    }

    @Test
    fun `given a record that is not in conflict then resolving is refused`() {
        assertFailsWith<IllegalArgumentException> {
            OutboxConflictResolution.replacementFor(conflicted.copy(state = OutboxState.WAITING), 5_000L)
        }
    }
}
