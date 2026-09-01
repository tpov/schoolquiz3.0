package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Главное свойство цикла — он не прерывается.
 *
 * Сегодня отказ одной попытки урока навсегда глушит отправку всех оценок: исключение уходит
 * наверх до того, как до них дойдёт очередь. Ради этого дефекта эпик и существует, поэтому он
 * проверяется первым.
 */
class OutboxEngineTest {

    private val uid = "uid-1"
    private val now = 30L * 24 * 60 * 60 * 1000

    private fun record(
        id: Long,
        operation: String = "OP",
        createdAtMs: Long = now,
    ) = OutboxRecord(
        id = id,
        mutationId = "m-$id",
        ownerUid = uid,
        operation = operation,
        payload = "{}",
        state = OutboxState.WAITING,
        createdAtMs = createdAtMs,
    )

    private fun engine(
        store: OutboxStore,
        onQuarantined: QuarantineListener = QuarantineListener { },
        send: suspend (OutboxRecord) -> Result<Unit>,
    ) = OutboxEngine(
        store = store,
        transport = { send(it) },
        clock = { now },
        onQuarantined = onQuarantined,
    )

    // ── Ради чего эпик ────────────────────────────────────────────────────────

    @Test
    fun `given one record is refused then the rest still go`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record(1, "ATTEMPT"), record(2, "RATING"), record(3, "RATING"))

        val summary =
            engine(store) { r ->
                if (r.id == 1L) {
                    Result.failure(SyncFailure(SyncError.Refused("nope")))
                } else {
                    Result.success(Unit)
                }
            }.drain(uid)

        assertEquals(3, summary.examined)
        assertEquals(2, summary.sent, "соседние записи обязаны уехать")
        assertEquals(1, summary.quarantined)
        assertContentEquals(listOf(2L, 3L), store.removed)
    }

    @Test
    fun `given the transport throws nothing but keeps failing then drain still returns`() = runTest {
        // Цикл не выпускает исключение наверх ни при каких условиях.
        val store = FakeOutboxStore()
        store.seed(record(1), record(2))

        val summary = engine(store) { Result.failure(SyncFailure(SyncError.NoNetwork)) }.drain(uid)

        assertEquals(2, summary.retried)
        assertEquals(0, summary.sent)
    }

    // ── Отправленное удаляется ────────────────────────────────────────────────

    @Test
    fun `given a record is sent then it is removed, not marked`() = runTest {
        // AD-4: очередь — это очередь, а не архив.
        val store = FakeOutboxStore()
        store.seed(record(1))

        engine(store) { Result.success(Unit) }.drain(uid)

        assertContentEquals(listOf(1L), store.removed)
        assertTrue(store.all.isEmpty(), "надгробия не остаётся")
    }

    // ── Карантин отдаётся фиче ────────────────────────────────────────────────

    @Test
    fun `given a record is quarantined then the owning feature is told`() = runTest {
        // Движок не имеет права трогать таблицы фичи, поэтому обязан сообщить (AD-28).
        val store = FakeOutboxStore()
        store.seed(record(1, "UNLOCK_LESSON"))
        val told = mutableListOf<OutboxRecord>()

        engine(store, onQuarantined = { told += it }) {
            Result.failure(SyncFailure(SyncError.Refused("Not enough nolics")))
        }.drain(uid)

        assertEquals(1, told.size)
        assertEquals("UNLOCK_LESSON", told.first().operation)
        assertEquals("Not enough nolics", told.first().lastError, "причина доезжает до фичи")
    }

    @Test
    fun `given a conflict then the feature is not told it is quarantined`() = runTest {
        // Конфликт не терминален, откатывать по нему нечего (AD-28).
        val store = FakeOutboxStore()
        store.seed(record(1))
        val told = mutableListOf<OutboxRecord>()

        val summary =
            engine(store, onQuarantined = { told += it }) {
                Result.failure(SyncFailure(SyncError.VersionConflict(3L)))
            }.drain(uid)

        assertEquals(1, summary.conflicted)
        assertTrue(told.isEmpty())
        assertEquals(OutboxState.CONFLICT, store.all.first().state)
    }

    // ── Чужие записи ──────────────────────────────────────────────────────────

    @Test
    fun `given a record of another account then it is never sent`() = runTest {
        // AD-8: запись принадлежит тому uid, который её создал.
        val store = FakeOutboxStore()
        store.seed(record(1).copy(ownerUid = "someone-else"))
        var attempts = 0

        val summary = engine(store) { attempts++; Result.success(Unit) }.drain(uid)

        assertEquals(0, summary.examined)
        assertEquals(0, attempts)
    }

    // ── Неопознанная неудача ──────────────────────────────────────────────────

    @Test
    fun `given a plain exception then it is treated as unknown and retried`() = runTest {
        // Транспорт может вернуть неудачу без разобранной ошибки — это не повод падать.
        val store = FakeOutboxStore()
        store.seed(record(1))

        val summary = engine(store) { Result.failure(IllegalStateException("boom")) }.drain(uid)

        assertEquals(1, summary.retried)
        assertEquals(OutboxState.WAITING, store.all.first().state)
    }
}
