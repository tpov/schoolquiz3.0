package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Транспортная половина контракта UX-O7 (история 8.1 эпика `epic-social-features`, полная
 * редакция: `_bmad-output/mvp-candidate/epic-social-features/ux-o7-poll-contract.md`).
 *
 * Голос — мутация с теми же правилами identity, что и остальные (контракт §4, AD-6/AD-7),
 * поэтому его инварианты закрепляются здесь, на общем транспорте, до любых записей голосов:
 * ключ рождается из тройки идентичности серия/revision/вопрос и не меняется (§2), retry
 * пересылает замороженный набор под тем же ключом — retry не второй голос (§6.1), повтор
 * известного ключа возвращает сохранённую квитанцию без сетевого вызова (§6.1, AD-6).
 * Имя операции `polls.SUBMIT_VOTE` и членство в `permanentReceiptOperations` — истории 8.2
 * вместе с серверной половиной и гейтом `OutboxOperationsRegistryTest`; здесь операция
 * фигурирует литералом ровно в том виде, который контракт фиксирует.
 */
class UxO7PollVoteIdentityContractTest {
    private val uid = "participant-1"
    private var now = 40L * 24 * 60 * 60 * 1000
    private var session = OutboxSession(uid, 1)

    /** Идентичность голоса §2: тройка серия/revision/вопрос в sourceId, серия в entity_ref. */
    private val voteOperation = "polls.SUBMIT_VOTE"
    private val voteSourceId = "series-poll-77-r2-q1-u-participant-1"
    private val voteEntityRef = "polls.SERIES:poll-77"
    private val voteBody = """{"seriesId":"poll-77","revisionId":2,"questionIndex":1,""" +
        """"selectedOptionIds":["opt-a","opt-c"]}"""


    /** Поправка §2 (2026-10-18, two-device прогон 8.4): участник — часть намерения. Два
     *  голосующих за один вопрос выводят РАЗНЫЕ ключи (иначе идемпотентность отказывает второму
     *  аккаунту), а ретрай того же участника — тот же ключ. */
    @Test
    fun voteKeyIsPerParticipant() {
        val otherSourceId = "series-poll-77-r2-q1-u-participant-2"
        val mine = OutboxOperations.mutationKey(voteOperation, voteSourceId)
        val theirs = OutboxOperations.mutationKey(voteOperation, otherSourceId)
        assertTrue(mine != theirs, "different participants must not share a mutation key")
        assertEquals(mine, OutboxOperations.mutationKey(voteOperation, voteSourceId))
    }

    private fun voteRecord(id: Long = 1L) = OutboxRecord(
        id = id,
        mutationId = OutboxOperations.mutationKey(voteOperation, voteSourceId),
        ownerUid = uid,
        operation = voteOperation,
        payload = voteBody,
        state = OutboxState.WAITING,
        createdAtMs = now,
        entityRef = voteEntityRef,
        expectedVersion = 12L,
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

    // ── §2: ключ рождается из тройки идентичности и не меняется ─────────────────────────────

    @Test
    fun `vote key is derived from the series-revision-question triple and never varies`() {
        val first = OutboxOperations.mutationKey(voteOperation, voteSourceId)
        // Тот же вызов позже — тот же ключ: retry, рестарт и повторный вход не разводят intent
        // на два ключа (§6.1: retry — не второй голос).
        repeat(3) { assertEquals(first, OutboxOperations.mutationKey(voteOperation, voteSourceId)) }
        assertEquals("polls-SUBMIT_VOTE-$voteSourceId", first)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")), "серверный charset ключа: $first")

        // Другая ось идентичности — другой ключ: чужой вопрос, чужая revision и чужая серия
        // не дедуплицируются в один голос, и наоборот — одна тройка не разъезжается на два.
        val otherQuestion = OutboxOperations.mutationKey(voteOperation, "series-poll-77-r2-q2")
        val otherRevision = OutboxOperations.mutationKey(voteOperation, "series-poll-77-r3-q1")
        val otherSeries = OutboxOperations.mutationKey(voteOperation, "series-poll-78-r2-q1")
        assertTrue(
            setOf(first, otherQuestion, otherRevision, otherSeries).size == 4,
            "каждая тройка — свой ключ",
        )

        // Пустое намерение отвергается: у голоса без идентичности вопроса честного ключа нет.
        assertFailsWith<IllegalArgumentException> { OutboxOperations.mutationKey(voteOperation, " ") }
    }

    // ── §2/§4: форма строки очереди — один набор, серия в entity_ref, NFR4 ──────────────────

    @Test
    fun `vote record carries one choice set and the series ref in the operation namespace`() =
        runTest {
            val store = FakeOutboxStore()
            val stored = store.enqueue(voteRecord(id = 0L))

            // Namespace-правило NFR4: операция и ссылка живут в одном пространстве polls.
            OutboxNamespace.requireSameNamespace(stored.operation, stored.entityRef)
            assertEquals(voteOperation, stored.operation)
            assertEquals(voteEntityRef, stored.entityRef)
            // Тело — один замороженный набор: список optionId, не отдельные голоса-отметки (§2).
            assertTrue(stored.payload.contains("\"selectedOptionIds\""))
            assertEquals(OutboxOperations.mutationKey(voteOperation, voteSourceId), stored.mutationId)
        }

    // ── §6.1: retry пересылает замороженный набор под тем же ключом ─────────────────────────

    @Test
    fun `retry resends the frozen choice set under the same key`() =
        runTest {
            val store = FakeOutboxStore()
            val row = voteRecord()
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
                    Result.success(MutationResponse("""{"accepted":true,"seriesId":"poll-77"}"""))
                }
            }

            // Попытка 1: сети нет. Identity в хранилище не трогается — только расписание.
            assertEquals(1, engine(store, receipts, transport).drain(uid).retried)
            store.all.single().let { stored ->
                assertEquals(row.mutationId, stored.mutationId)
                assertEquals(row.ownerUid, stored.ownerUid)
                assertEquals(row.operation, stored.operation)
                assertEquals(row.payload, stored.payload, "набор заморожен до dispatch (AD-6)")
                assertEquals(row.entityRef, stored.entityRef)
                assertEquals(row.expectedVersion, stored.expectedVersion)
                assertEquals(row.createdAtMs, stored.createdAtMs)
                assertEquals(1, stored.attemptCount)
            }

            // Попытка 2: тот же набор под тем же ключом — не «изменённый голос».
            now += 2 * 60 * 60 * 1000
            assertEquals(1, engine(store, receipts, transport).drain(uid).sent)

            assertEquals(2, sends.size)
            val (first, second) = sends
            assertEquals(first.mutationId, second.mutationId)
            assertEquals(first.payload, second.payload)
            assertEquals(first.createdAtMs, second.createdAtMs)
            // Принятый голос зафиксирован одной квитанцией; строка очереди ушла.
            assertEquals(row.mutationId, receipts.find(uid, row.mutationId)?.mutationId)
            assertTrue(store.all.isEmpty())
        }

    // ── §6.1: повтор известного ключа возвращает ту же квитанцию без сети ───────────────────

    @Test
    fun `repeat of a known vote key returns the same receipt without a network call`() =
        runTest {
            val row = voteRecord().copy(createdAtMs = 0) // «старая» запись: возраст не решает
            val stored =
                MutationReceipt(
                    ownerUid = row.ownerUid,
                    mutationId = row.mutationId,
                    operation = row.operation,
                    entityRef = row.entityRef,
                    payloadJson = """{"accepted":true,"seriesId":"poll-77"}""",
                    receivedAtMs = 0,
                )
            val receipts = FakeMutationReceiptStore()
            receipts.persist(stored) // crash между persist квитанции и удалением строки
            val store = FakeOutboxStore().also { it.seed(row) }
            val neverSend = MutationTransport { error("a known vote key must not re-send") }

            // Второй участник в знаменателе не появляется: повтор возвращает принятое как есть.
            assertEquals(1, engine(store, receipts, neverSend).drain(uid).sent)
            assertTrue(store.all.isEmpty())
            assertEquals(stored, receipts.find(uid, row.mutationId))
        }
}
