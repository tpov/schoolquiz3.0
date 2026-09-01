package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Карантин терминален, и его последствие принадлежит фиче (AD-28).
 *
 * Ядро обязано сообщить о нём ровно один раз и не тронуть ни одной таблицы фичи само (AD-7,
 * NFR1). Промежуточные состояния — ожидание предусловия и конфликт версий — не терминальны, и
 * реакции не вызывают: откатывать по ним нечего.
 */
class QuarantineNotificationTest {

    private val uid = "uid-1"
    private val now = 30L * 24 * 60 * 60 * 1000

    private fun record(id: Long, operation: String) = OutboxRecord(
        id = id,
        mutationId = "m-$id",
        ownerUid = uid,
        operation = operation,
        payload = "{}",
        entityRef = "lesson_runner.ATTEMPT:$id",
        state = OutboxState.WAITING,
        createdAtMs = now,
    )

    private fun engineOver(
        store: OutboxStore,
        seen: MutableList<OutboxRecord>,
        error: SyncError,
    ) = OutboxEngine(
        store = store,
        transport = { Result.failure(SyncFailure(error)) },
        clock = { now },
        onQuarantined = { seen += it },
    )

    @Test
    fun `given a record is quarantined then the owning feature hears about it exactly once`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, "lesson_runner.SUBMIT_ATTEMPT"))
        val seen = mutableListOf<OutboxRecord>()

        engineOver(store, seen, SyncError.Refused("not enough nolics")).drain(uid)

        assertEquals(1, seen.size, "the feature must hear about a quarantined record once, not zero or twice")
        val heard = seen.single()
        assertEquals(1L, heard.id)
        assertEquals("lesson_runner.SUBMIT_ATTEMPT", heard.operation)
        assertEquals("lesson_runner.ATTEMPT:1", heard.entityRef)
        assertEquals(OutboxState.QUARANTINED, heard.state)
        assertTrue(
            heard.lastError.orEmpty().contains("not enough nolics"),
            "the feature decides what to roll back, so it needs the reason: was '${heard.lastError}'",
        )
    }

    @Test
    fun `given a version conflict then no quarantine reaction fires`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(2, "quest.SET_SHELF"))
        val seen = mutableListOf<OutboxRecord>()

        engineOver(store, seen, SyncError.VersionConflict(serverVersion = 9L)).drain(uid)

        assertTrue(seen.isEmpty(), "a conflict waits for the player; there is nothing to roll back yet")
    }

    @Test
    fun `given the server says the precondition has not arrived then no quarantine reaction fires`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(3, "lesson.UNLOCK"))
        val seen = mutableListOf<OutboxRecord>()

        engineOver(store, seen, SyncError.PreconditionNotMet).drain(uid)

        assertTrue(seen.isEmpty(), "'not yet' is retried, not quarantined")
    }

    @Test
    fun `given no network then no quarantine reaction fires`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(4, "review.SUBMIT"))
        val seen = mutableListOf<OutboxRecord>()

        engineOver(store, seen, SyncError.NoNetwork).drain(uid)

        assertTrue(seen.isEmpty(), "no network is the most ordinary retry there is")
    }
}
