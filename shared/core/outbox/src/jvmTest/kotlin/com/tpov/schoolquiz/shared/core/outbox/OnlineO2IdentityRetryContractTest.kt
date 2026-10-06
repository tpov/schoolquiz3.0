package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Клиентская половина контракта ONLINE-O2 §6 (identity/retry), история 2.5 эпика
 * `epic-ordinary-delivery` (полная редакция: `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md`).
 *
 * Ready-ACK — мутация с теми же правилами identity, что и остальные (§1), поэтому его инварианты
 * закрепляются здесь, на общем транспорте: ключ рождается вместе с намерением и не меняется
 * (AD-6), retry пересылает замороженное тело под тем же ключом, повтор известного ключа возвращает
 * ту же квитанцию без повторного сетевого вызова и без возрастного/TTL-отката. Движок сам готовый
 * ready-ACK не создаёт — его ставит история 2.6; здесь закреплено то, чему будущая мутация
 * обязана соответствовать и что уже гарантировано транспортом.
 */
class OnlineO2IdentityRetryContractTest {
    private val uid = "owner"
    private var now = 30L * 24 * 60 * 60 * 1000
    private var session = OutboxSession(uid, 1)

    /** Мутация перехода по форме §6: sourceId несёт identity попытки и позицию вопроса. */
    private val readyAckSourceId = "attempt-7-q3-window-1"
    private val readyAckOperation = OutboxOperations.SUBMIT_ATTEMPT
    private val readyAckEntityRef = "lesson_runner.LESSON:lesson-1"

    private fun readyAckRecord(id: Long = 1L) = OutboxRecord(
        id = id,
        mutationId = OutboxOperations.mutationKey(readyAckOperation, readyAckSourceId),
        ownerUid = uid,
        operation = readyAckOperation,
        payload = "{\"questionIndex\":3,\"windowId\":\"window-1\"}",
        state = OutboxState.WAITING,
        createdAtMs = now,
        entityRef = readyAckEntityRef,
        expectedVersion = 41L,
    )

    private fun engine(
        store: FakeOutboxStore,
        receipts: MutationReceiptStore = FakeMutationReceiptStore(),
        transport: MutationTransport = MutationTransport { Result.success(MutationResponse("{}")) },
    ) = OutboxEngine(
        store,
        transport,
        clock = { now },
        onQuarantined = NoLocalEffect(),
        receipts = receipts,
        sessions = { session },
    )

    // ── §6.3, строка 1: ключ рождается с намерением и не меняется ────────────────────────────

    @Test
    fun `mutation key is born with the intent and never varies`() {
        val first = OutboxOperations.mutationKey(readyAckOperation, readyAckSourceId)
        // Тот же вызов позже (другой проход, другой день, другая попытка отправки) — тот же ключ.
        repeat(3) { assertEquals(first, OutboxOperations.mutationKey(readyAckOperation, readyAckSourceId)) }
        // Другое намерение — другой ключ: чужая позиция вопроса или чужая попытка не дедуплицируются
        // в один эффект, и наоборот — та же пара не разъезжается на два ключа.
        val otherQuestion = OutboxOperations.mutationKey(readyAckOperation, "attempt-7-q4-window-1")
        val otherOperation = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, readyAckSourceId)
        assertTrue(first != otherQuestion && first != otherOperation && otherQuestion != otherOperation)
    }

    // ── §6.3, строка 2: серверный charset ключа и отказ от пустого намерения ─────────────────

    @Test
    fun `mutation key stays inside the server key charset`() {
        val key = OutboxOperations.mutationKey(readyAckOperation, readyAckSourceId)
        assertTrue(key.matches(Regex("[A-Za-z0-9_-]+")), "key must fit the server charset: $key")
        // Подчёрк пространства имён остаётся: дефис появляется только на месте точки-разделителя
        // (OutboxOperations.mutationKey меняет именно её) — формат зафиксирован и для миграции 5 → 6.
        assertEquals("lesson_runner-SUBMIT_ATTEMPT-$readyAckSourceId", key)
        // Пустое намерение отвергается, а не coerced в ключ: у вымывания источника нет честного ключа.
        assertFailsWith<IllegalArgumentException> { OutboxOperations.mutationKey(readyAckOperation, " ") }
        assertFailsWith<IllegalArgumentException> { OutboxOperations.resolutionKey(" ", 1L) }
    }

    // ── §6.3, строка 3: retry пересылает замороженное тело под тем же ключом ─────────────────

    @Test
    fun `retry resends the frozen body under the same key`() =
        runTest {
            val store = FakeOutboxStore()
            val row = readyAckRecord()
            store.seed(row)
            val receipts = FakeMutationReceiptStore()
            val sends = mutableListOf<OutboxRecord>()
            var failFirst = true
            val transport = MutationTransport { sent ->
                sends += sent
                if (failFirst) {
                    failFirst = false
                    Result.failure(SyncFailure(SyncError.NoNetwork))
                } else {
                    Result.success(MutationResponse("{\"accepted\":true,\"deadlineAtMs\":7000}"))
                }
            }

            // Попытка 1: сети нет. Identity в хранилище не трогается — меняется только расписание.
            assertEquals(1, engine(store, receipts, transport).drain(uid).retried)
            store.all.single().let { stored ->
                assertEquals(row.mutationId, stored.mutationId)
                assertEquals(row.ownerUid, stored.ownerUid)
                assertEquals(row.operation, stored.operation)
                assertEquals(row.payload, stored.payload)
                assertEquals(row.entityRef, stored.entityRef)
                assertEquals(row.expectedVersion, stored.expectedVersion)
                assertEquals(row.createdAtMs, stored.createdAtMs)
                assertEquals(1, stored.attemptCount)
            }

            // Попытка 2: срок подошёл, отправка повторяется тем же телом под тем же ключом.
            now += 2 * 60 * 60 * 1000
            assertEquals(1, engine(store, receipts, transport).drain(uid).sent)

            assertEquals(2, sends.size)
            val (first, second) = sends
            assertEquals(first.mutationId, second.mutationId)
            assertEquals(first.ownerUid, second.ownerUid)
            assertEquals(first.operation, second.operation)
            assertEquals(first.payload, second.payload)
            assertEquals(first.entityRef, second.entityRef)
            assertEquals(first.expectedVersion, second.expectedVersion)
            assertEquals(first.createdAtMs, second.createdAtMs)
            // Авторитетный ответ принят один раз и держит identity мутации; очередь пуста.
            assertEquals(row.mutationId, receipts.find(uid, row.mutationId)?.mutationId)
            assertTrue(store.all.isEmpty())
        }

    // ── §6.3, строка 4: повтор известного ключа возвращает ту же квитанцию ───────────────────

    @Test
    fun `repeat of a known key returns the same receipt without a network call`() =
        runTest {
            val row = readyAckRecord().copy(createdAtMs = 0) // «старая» запись: возраст не решает
            val stored =
                MutationReceipt(
                    ownerUid = row.ownerUid,
                    mutationId = row.mutationId,
                    operation = row.operation,
                    entityRef = row.entityRef,
                    payloadJson = "{\"accepted\":true,\"deadlineAtMs\":7000}",
                    receivedAtMs = 0,
                )
            val receipts = FakeMutationReceiptStore()
            receipts.persist(stored) // crash между persist квитанции и удалением строки
            val store = FakeOutboxStore().also { it.seed(row) }
            val neverSend = MutationTransport { error("a known key must not re-send") }

            assertEquals(1, engine(store, receipts, neverSend).drain(uid).sent)
            assertTrue(store.all.isEmpty())
            // Тело квитанции байт-в-байт то же, второй записи под этим ключом нет.
            assertEquals(stored, receipts.find(uid, row.mutationId))
            assertEquals(listOf(stored), receipts.observeEntity(uid, readyAckEntityRef).first())
        }

    // ── §6.3, строка 8 (механизм): постоянный receipt не рвётся возрастом или лимитом попыток ─

    @Test
    fun `permanent receipt operations never age into quarantine`() {
        val policy = OutboxPolicy()
        val ancient = readyAckRecord().copy(createdAtMs = 0, attemptCount = 99)
        assertFalse(policy.isExpired(ancient, now + OutboxLimits().maxAgeMs))
        val failure = policy.onFailure(ancient, SyncError.NoNetwork, now)
        assertEquals(OutboxState.WAITING, failure.state)
    }
}
