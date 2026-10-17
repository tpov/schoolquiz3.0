package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailure
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailureKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Очередная половина мутаций сессии на подставном хранилище: перепроверка смены аккаунта после
 * вставки, конфликт замороженного тела под тем же ключом и отказ кодека — всё через контракт
 * [Result], без исключений наружу.
 */
class TournamentSessionOutboxRepositoryTest {
    private val sessionId = "q_" + "a".repeat(64)
    private val windowId = "win_aedbecedab4b"

    /** Хранилище с одной строкой на ключ; повтор вставки возвращает лежащую строку (AD-2). */
    private class FakeStore : OutboxStore {
        val rows = mutableMapOf<String, OutboxRecord>()
        var afterInsert: () -> Unit = {}

        override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
            afterInsert()
            return rows[record.mutationId] ?: record.copy(id = 1L).also { rows[record.mutationId] = it }
        }

        override suspend fun dueRecords(
            ownerUid: String,
            nowMs: Long,
            limit: Int,
        ): List<OutboxRecord> = emptyList()

        override suspend fun apply(
            id: Long,
            decision: OutboxDecision,
        ) = Unit

        override suspend fun remove(id: Long) = Unit

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = flowOf(OutboxCounts())

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<OutboxEntitySync> = flowOf(OutboxEntitySync.None)

        override suspend fun findByEntityRef(
            ownerUid: String,
            entityRef: String,
        ): OutboxRecord? = null

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = 0

        override suspend fun forget(ownerUid: String) = Unit
    }

    private class FakeReceipts : MutationReceiptStore {
        override suspend fun persist(receipt: com.tpov.schoolquiz.shared.core.outbox.MutationReceipt) = Unit

        override suspend fun find(
            ownerUid: String,
            mutationId: String,
        ): com.tpov.schoolquiz.shared.core.outbox.MutationReceipt? = null

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<List<com.tpov.schoolquiz.shared.core.outbox.MutationReceipt>> = flowOf(emptyList())
    }

    private fun repository(
        store: FakeStore,
        sessions: OutboxSessionProvider,
    ): TournamentSessionOutboxRepository =
        TournamentSessionOutboxRepository(
            outbox = store,
            receipts = FakeReceipts(),
            sessions = sessions,
            parser = TournamentSessionReceiptParser(
                views = TournamentSessionWireParser(
                    events = TournamentEventWireParser(),
                    meetings = TournamentMeetingWireParser(),
                ),
            ),
            clock = { 1788739200000L },
        )

    @Test
    fun `account switch between insert and recheck refuses the row as ACCOUNT_CHANGED`() = runTest {
        val store = FakeStore()
        var current = OutboxSession("player", 1)
        store.afterInsert = { current = OutboxSession("other", 2) }
        val repo = repository(store) { current }
        val outcome =
            repo.enqueue(TournamentSessionIntent.ReadyAck(sessionId, 1, windowId))
        val failure = assertIs<TournamentRemoteFailure>(outcome.exceptionOrNull())
        assertEquals(TournamentRemoteFailureKind.ACCOUNT_CHANGED, failure.kind)
        // Вставка успела случиться до перепроверки — контракт «захвати и перепроверь».
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `the same key carrying a different frozen body fails without leaving the result`() = runTest {
        val store = FakeStore()
        var current = OutboxSession("player", 1)
        val repo = repository(store) { current }
        repo.enqueue(TournamentSessionIntent.Answer(sessionId, 0, "q012", buildJsonObject { put("selected", "a") }))
            .getOrThrow()
        // Тот же слот ответа, другой ответ: ключ выводится из идентичности слота, тело заморожено.
        // Смена аккаунта под конфликтом не маскирует его под ACCOUNT_CHANGED: тело сверяется первым.
        current = OutboxSession("other", 2)
        val outcome =
            repo.enqueue(TournamentSessionIntent.Answer(sessionId, 0, "q012", buildJsonObject { put("selected", "b") }))
        val conflict = assertIs<IllegalStateException>(outcome.exceptionOrNull())
        assertTrue(conflict.message!!.contains("замороженное тело"))
    }

    @Test
    fun `a codec refusal rides the result instead of escaping as an exception`() = runTest {
        val store = FakeStore()
        val repo = repository(store) { OutboxSession("player", 1) }
        val oversized: JsonObject =
            buildJsonObject { put("type", "single-choice"); put("selected", "x".repeat(20_000)) }
        val outcome =
            repo.enqueue(TournamentSessionIntent.Answer(sessionId, 0, "q012", oversized))
        assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
        assertNull(store.rows[TournamentSessionOutboxCodec.mutationKey(
            TournamentSessionIntent.Answer(sessionId, 0, "q012", oversized),
        )])
    }

    @Test
    fun `an ordinary intent lands and replays as the lying row`() = runTest {
        val store = FakeStore()
        val repo = repository(store) { OutboxSession("player", 1) }
        val first = repo.enqueue(TournamentSessionIntent.ReadyAck(sessionId, 1, windowId)).getOrThrow()
        assertEquals(OutboxState.WAITING, first.state)
        assertEquals(
            TournamentSessionOutboxCodec.mutationKey(TournamentSessionIntent.ReadyAck(sessionId, 1, windowId)),
            first.mutationId,
        )
        assertEquals(first, repo.enqueue(TournamentSessionIntent.ReadyAck(sessionId, 1, windowId)).getOrThrow())
    }
}
