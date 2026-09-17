package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Ответ очереди про одну сущность (AD-14): что показывать на её экране.
 *
 * Две вещи, которых у ответа до сих пор не было: явное «неотправленного нет» вместо пустоты и
 * порядок, в котором терминальное перевешивает ожидание.
 */
class OutboxEntityAnswerTest {

    private val now = 1_700_000_000_000L
    private val ref = "lesson_runner:attempt:a1"

    private fun record(
        mutationId: String,
        state: OutboxState = OutboxState.WAITING,
        createdAtMs: Long = now,
        entityRef: String? = ref,
    ) = OutboxRecord(
        id = 0L,
        mutationId = mutationId,
        ownerUid = "uid-1",
        operation = OutboxOperations.SUBMIT_ATTEMPT,
        payload = "{}",
        state = state,
        createdAtMs = createdAtMs,
        entityRef = entityRef,
    )

    @Test
    fun `given nothing is queued for the reference then the answer says so, and is not emptiness`() = runTest {
        val store = FakeOutboxStore()

        val answer = store.observeEntity("uid-1", "lesson_runner:attempt:absent").first()

        assertEquals(OutboxEntitySync.None, answer)
    }

    @Test
    fun `given a waiting record and a quarantined one then the answer is quarantine`() = runTest {
        val store = FakeOutboxStore()
        // Карантинная легла раньше, ждущая позже: по свежести выиграла бы ждущая, и терминальное
        // состояние спряталось бы за ней.
        store.seed(
            record("m-old", state = OutboxState.QUARANTINED, createdAtMs = now).copy(id = 1L),
            record("m-new", state = OutboxState.WAITING, createdAtMs = now + 1_000L).copy(id = 2L),
        )

        val answer = store.observeEntity("uid-1", ref).first()

        assertTrue(answer is OutboxEntitySync.Queued)
        assertEquals(OutboxState.QUARANTINED, answer.state)
        assertEquals(1L, answer.recordId)
    }

    @Test
    fun `given a conflict alongside quarantine then the conflict still comes first`() = runTest {
        val store = FakeOutboxStore()
        store.seed(
            record("m-conflict", state = OutboxState.CONFLICT, createdAtMs = now).copy(id = 1L),
            record("m-quarantined", state = OutboxState.QUARANTINED, createdAtMs = now + 1_000L).copy(id = 2L),
        )

        val answer = store.observeEntity("uid-1", ref).first()

        assertTrue(answer is OutboxEntitySync.Queued)
        assertEquals(OutboxState.CONFLICT, answer.state, "по конфликту игроку решать — он вперёд всех")
    }

    @Test
    fun `given the pass could not deliver then the run summary names the branch, not a text`() = runTest {
        val store = FakeOutboxStore()
        store.seed(record("m-1").copy(id = 1L))
        val engine =
            OutboxEngine(
                store = store,
                transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) },
                clock = { now },
                onQuarantined = NoLocalEffect(),
            )

        val summary = engine.drain("uid-1")

        assertEquals(1, summary.retried)
        assertEquals(SyncError.NoNetwork, summary.lastError, "ветвь названа целиком, а не текстом (AD-15)")
    }
}
