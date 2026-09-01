package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

/**
 * Наружу из синхронизации сегодня не выходит ничего, кроме `Log.w`. Здесь проверяется, что стало
 * выходить — и, главное, что при смене аккаунта чужие числа не остаются на экране (инвариант 8).
 */
class SyncStatusRepositoryTest {

    private class FakeStore(
        private val byUid: MutableStateFlow<Map<String, OutboxCounts>> = MutableStateFlow(emptyMap()),
    ) : OutboxStore {
        fun set(uid: String, counts: OutboxCounts) {
            byUid.value = byUid.value + (uid to counts)
        }

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> =
            byUid.map { it[ownerUid] ?: OutboxCounts() }

        override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

        override suspend fun dueRecords(ownerUid: String, nowMs: Long, limit: Int): List<OutboxRecord> = emptyList()

        override suspend fun apply(id: Long, decision: OutboxDecision) = Unit

        override suspend fun remove(id: Long) = Unit

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = 0
    }

    @Test
    fun `given pending and stuck records then they are counted apart`() = runTest {
        // Одно «ожидает» на все состояния скрыло бы и конфликт, и карантин — а это ровно те два
        // случая, где игроку нужно что-то сделать.
        val store = FakeStore()
        store.set("uid-1", OutboxCounts(waiting = 2, conflicted = 1, quarantined = 1))

        val status = InMemorySyncStatusRepository(store, flowOf("uid-1")).observeStatus().first()

        assertEquals(2, status.counts.pending)
        assertEquals(2, status.counts.stuck)
        assertTrue(status.hasPending)
        assertTrue(status.needsAttention)
    }

    @Test
    fun `given the account changes then the counts follow it, not the previous owner`() = runTest {
        val store = FakeStore()
        store.set("uid-1", OutboxCounts(waiting = 5))
        store.set("uid-2", OutboxCounts(waiting = 0))
        val uid = MutableStateFlow<String?>("uid-1")
        val repo = InMemorySyncStatusRepository(store, uid)

        assertEquals(5, repo.observeStatus().first().counts.waiting)
        uid.value = "uid-2"

        assertEquals(0, repo.observeStatus().first().counts.waiting, "чужие числа не остаются на экране")
    }

    @Test
    fun `given no account then zeroes arrive, not an empty flow`() = runTest {
        // Пустой поток оставил бы подписчика без единого значения — и он показал бы прошлое.
        val repo = InMemorySyncStatusRepository(FakeStore(), flowOf(null))

        val status = repo.observeStatus().first()

        assertEquals(OutboxCounts(), status.counts)
        assertFalse(status.hasPending)
    }

    @Test
    fun `given a failure then its type is kept, and a later success clears it`() = runTest {
        val repo = InMemorySyncStatusRepository(FakeStore(), flowOf("uid-1"))

        repo.recordFailure(SyncError.NoNetwork, atMs = 10L)
        assertEquals(SyncError.NoNetwork, repo.observeStatus().first().lastError)

        repo.recordSuccess(atMs = 20L)
        val after = repo.observeStatus().first()

        assertEquals(null, after.lastError, "починившееся не показываем как жалобу")
        assertEquals(20L, after.lastSuccessAtMs)
        assertTrue(after.hasEverSucceeded)
    }

    @Test
    fun `given the account changes then the success time and the error go with it`() = runTest {
        // Auth-scope покрывал только счётчики: время последней удачной синхронизации и причина
        // последней неудачи переживали смену аккаунта и показывались следующему игроку как свои.
        val uid = MutableStateFlow<String?>("uid-1")
        val repo = InMemorySyncStatusRepository(FakeStore(), uid)
        repo.observeStatus().first()
        repo.recordSuccess(atMs = 100L)
        repo.recordFailure(SyncError.Refused("nope"), atMs = 110L)

        uid.value = "uid-2"
        val after = repo.observeStatus().first()

        assertEquals(0L, after.lastSuccessAtMs, "чужое время синхронизации новому игроку не показываем")
        assertEquals(null, after.lastError, "и чужую ошибку тоже")
    }

    @Test
    fun `given the same account then a new subscription keeps what was recorded`() = runTest {
        // Пара к предыдущему: сброс обязан срабатывать на смене аккаунта, а не на каждой подписке.
        val repo = InMemorySyncStatusRepository(FakeStore(), MutableStateFlow<String?>("uid-1"))
        repo.observeStatus().first()
        repo.recordSuccess(atMs = 100L)

        assertEquals(100L, repo.observeStatus().first().lastSuccessAtMs)
    }

    @Test
    fun `given nothing ever synced then that is distinguishable from synced long ago`() = runTest {
        val status = InMemorySyncStatusRepository(FakeStore(), flowOf("uid-1")).observeStatus().first()

        assertFalse(status.hasEverSucceeded)
        assertEquals(0L, status.lastSuccessAtMs)
    }
}
