package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncRunTraceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    private val trace = FakeSyncRunTraceStore()

    /** Так же, как в графе приложения: пишущая сторона спрашивает uid у того же источника. */
    private fun repository(
        store: OutboxStore,
        uid: Flow<String?>,
        currentUid: suspend () -> String?,
    ) = DefaultSyncStatusRepository(store, trace, uid, currentUid)

    private fun repository(store: OutboxStore, uid: String?) =
        repository(store, flowOf(uid)) { uid }

    private fun repository(store: OutboxStore, uid: MutableStateFlow<String?>) =
        repository(store, uid) { uid.value }

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

        override fun observeEntity(ownerUid: String, entityRef: String): Flow<OutboxEntitySync> =
            flowOf(OutboxEntitySync.None)

        override suspend fun findByEntityRef(ownerUid: String, entityRef: String): OutboxRecord? = null

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = 0

        /** Вторая операция AD-8: после согласия на смену аккаунта записи прежнего владельца снимаются. */
        override suspend fun forget(ownerUid: String) {
            byUid.value = byUid.value - ownerUid
        }
    }

    @Test
    fun `given pending and stuck records then they are counted apart`() = runTest {
        // Одно «ожидает» на все состояния скрыло бы и конфликт, и карантин — а это ровно те два
        // случая, где игроку нужно что-то сделать.
        val store = FakeStore()
        store.set("uid-1", OutboxCounts(waiting = 2, conflicted = 1, quarantined = 1))

        val status = repository(store, "uid-1").observeStatus().first()

        assertEquals(2, status.counts.waiting)
        assertEquals(1, status.counts.conflicted)
        assertEquals(1, status.counts.quarantined)
        assertEquals(0, status.counts.waitingPrecondition)
    }

    @Test
    fun `given the account changes then the counts follow it, not the previous owner`() = runTest {
        val store = FakeStore()
        store.set("uid-1", OutboxCounts(waiting = 5))
        store.set("uid-2", OutboxCounts(waiting = 0))
        val uid = MutableStateFlow<String?>("uid-1")
        val repo = repository(store, uid)

        assertEquals(5, repo.observeStatus().first().counts.waiting)
        uid.value = "uid-2"

        assertEquals(0, repo.observeStatus().first().counts.waiting, "чужие числа не остаются на экране")
    }

    @Test
    fun `given no account then zeroes arrive, not an empty flow`() = runTest {
        // Пустой поток оставил бы подписчика без единого значения — и он показал бы прошлое.
        val repo = repository(FakeStore(), null)

        val status = repo.observeStatus().first()

        assertEquals(OutboxCounts(), status.counts)
        assertEquals(0L, status.lastSuccessAtMs)
        assertNull(status.lastError)
    }

    @Test
    fun `given a failure then its type is kept, and a later success clears it`() = runTest {
        val repo = repository(FakeStore(), "uid-1")

        repo.recordFailure(SyncRunKind.CONTENT, SyncError.NoNetwork, atMs = 10L)
        assertEquals(SyncError.NoNetwork, repo.observeStatus().first().lastError)

        repo.recordSuccess(SyncRunKind.CONTENT, atMs = 20L)
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
        val repo = repository(FakeStore(), uid)
        repo.observeStatus().first()
        repo.recordSuccess(SyncRunKind.CONTENT, atMs = 100L)
        repo.recordFailure(SyncRunKind.CONTENT, SyncError.Refused("nope"), atMs = 110L)

        uid.value = "uid-2"
        val after = repo.observeStatus().first()

        assertEquals(0L, after.lastSuccessAtMs, "чужое время синхронизации новому игроку не показываем")
        assertEquals(null, after.lastError, "и чужую ошибку тоже")
    }

    @Test
    fun `given the same account then a new subscription keeps what was recorded`() = runTest {
        // Пара к предыдущему: сброс обязан срабатывать на смене аккаунта, а не на каждой подписке.
        val repo = repository(FakeStore(), MutableStateFlow<String?>("uid-1"))
        repo.observeStatus().first()
        repo.recordSuccess(SyncRunKind.CONTENT, atMs = 100L)

        assertEquals(100L, repo.observeStatus().first().lastSuccessAtMs)
    }

    @Test
    fun `given nothing ever synced then that is distinguishable from synced long ago`() = runTest {
        val status = repository(FakeStore(), "uid-1").observeStatus().first()

        assertFalse(status.hasEverSucceeded)
        assertEquals(0L, status.lastSuccessAtMs)
    }

    @Test
    fun `given the journal had records nobody could read then that is said out loud`() = runTest {
        // Пропуск не оставлял следа вовсе: `mapNotNull` отбрасывал непонятую запись, и «backfill
        // закончен» не следовало ни из чего (AD-11). Заодно это честность перед игроком: узел,
        // о котором сказали, а прочитать не смогли, остался прежним.
        val repo = repository(FakeStore(), "uid-1")

        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 4)

        val status = repo.observeStatus().first()
        assertEquals(4, status.unreadableChanges)
        assertTrue(status.hasUnreadableChanges)
        assertEquals(OutboxCounts(), status.counts, "непонятая запись журнала — не запись очереди")
    }

    @Test
    fun `given four journals then their unreadable counts add up instead of overwriting`() = runTest {
        // Число одно, а журналов три. Без имени журнала последний доложивший затирал бы
        // остальных, и два журнала показывали бы чужой ноль (AD-11).
        val repo = repository(FakeStore(), "uid-1")

        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 1)
        repo.recordUnreadableChanges(SyncJournals.LESSON_CONTENT, 2)
        repo.recordUnreadableChanges(SyncJournals.PRIVATE_QUESTS, 3)

        assertEquals(6, repo.observeStatus().first().unreadableChanges)
    }

    @Test
    fun `given one journal is clean then the others keep their counts`() = runTest {
        // Чистый проход каталогов не означает, что журнал урока прочитан.
        val repo = repository(FakeStore(), "uid-1")
        repo.recordUnreadableChanges(SyncJournals.LESSON_CONTENT, 5)

        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 0)

        assertEquals(5, repo.observeStatus().first().unreadableChanges)
    }

    @Test
    fun `given a later clean run then the previous unreadable count is gone`() = runTest {
        // Замещает, а не копит: накопление за всё время росло бы и после того, как backfill всё
        // починил, и сигнал перестал бы что-либо значить.
        val repo = repository(FakeStore(), "uid-1")
        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 4)

        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 0)

        assertFalse(repo.observeStatus().first().hasUnreadableChanges)
    }

    @Test
    fun `given the account changes then the unreadable count goes with it`() = runTest {
        val uid = MutableStateFlow<String?>("uid-1")
        val repo = repository(FakeStore(), uid)
        repo.observeStatus().first()
        repo.recordUnreadableChanges(SyncJournals.CATALOGS, 4)

        uid.value = "uid-2"

        assertEquals(0, repo.observeStatus().first().unreadableChanges)
    }

    @Test
    fun `given the process died then the success time comes back with a new repository`() = runTest {
        // Главное в истории 4.2: след описывает аккаунт, а не сеанс. Пока он жил полем репозитория,
        // холодный старт говорил «синхронизации ещё не было» игроку, у которого она прошла минуту
        // назад — и это ровно то молчание, ради которого состояние выводится наружу.
        repository(FakeStore(), "uid-1").recordSuccess(SyncRunKind.CONTENT, atMs = 1_700L)

        val afterRestart = repository(FakeStore(), "uid-1").observeStatus().first()

        assertEquals(1_700L, afterRestart.lastSuccessAtMs, "след прогона не пережил перезапуск")
        assertTrue(afterRestart.hasEverSucceeded)
    }

    @Test
    fun `given the process died then the last error branch comes back too`() = runTest {
        repository(FakeStore(), "uid-1").recordFailure(SyncRunKind.CONTENT, SyncError.NoNetwork, atMs = 10L)

        val afterRestart = repository(FakeStore(), "uid-1").observeStatus().first()

        assertEquals(SyncError.NoNetwork, afterRestart.lastError)
    }

    @Test
    fun `given a run under one account then the trace is written under its key`() = runTest {
        // Строка на владельца: без ключа след был бы общим, и следующий игрок увидел бы чужое
        // время своим (инвариант 8).
        repository(FakeStore(), "uid-1").recordSuccess(SyncRunKind.CONTENT, atMs = 500L)

        assertEquals("uid-1", trace.lastWrittenOwner)
        assertEquals(SyncRunTrace(), trace.traceOf("uid-2"), "чужой ключ пуст")
    }

    @Test
    fun `given the profile run went through then the content run keeps its own branch`() = runTest {
        // Расписания разведены эпиком 9, а след у них был один: профильный прогон, прошедший
        // после упавшего контентного, снимал его ветвь и ставил поверх неё своё время успеха.
        // Игрок видел «синхронизировались только что» над содержимым, которое не приезжало.
        val repo = repository(FakeStore(), "uid-1")
        repo.recordFailure(SyncRunKind.CONTENT, SyncError.NoNetwork, atMs = 10L)

        repo.recordSuccess(SyncRunKind.PROFILE, atMs = 20L)

        val after = repo.observeStatus().first()
        assertEquals(SyncError.NoNetwork, after.lastError, "чужой успех не чинит контентную ошибку")
        assertEquals(0L, after.lastSuccessAtMs, "содержимое не приезжало — времени успеха нет")
        assertEquals(20L, after.profileLastSuccessAtMs, "а профиль приехал, и это своё число")
        assertNull(after.profileLastError)
    }

    @Test
    fun `given the content run went through then the failed profile run keeps its own branch`() = runTest {
        // Обратная сторона: контентный успех не имеет права объявить профиль обновившимся.
        val repo = repository(FakeStore(), "uid-1")
        repo.recordFailure(SyncRunKind.PROFILE, SyncError.Refused("нет прав"), atMs = 10L)

        repo.recordSuccess(SyncRunKind.CONTENT, atMs = 20L)

        val after = repo.observeStatus().first()
        assertEquals(SyncError.Refused("нет прав"), after.profileLastError)
        assertEquals(0L, after.profileLastSuccessAtMs)
        assertEquals(20L, after.lastSuccessAtMs)
        assertNull(after.lastError)
    }

    @Test
    fun `given a manual resync went through then it answers for itself and not for the schedule`() = runTest {
        // «Перечитать всё» читает каталог и только его: очередь не отправляется, профиль не
        // обновляется. Его успех — ответ на нажатие кнопки, а не отчёт планового прогона.
        val repo = repository(FakeStore(), "uid-1")
        repo.recordFailure(SyncRunKind.CONTENT, SyncError.NoNetwork, atMs = 10L)

        repo.recordSuccess(SyncRunKind.RESYNC, atMs = 30L)

        val after = repo.observeStatus().first()
        assertEquals(SyncError.NoNetwork, after.lastError, "ресинк не чинил очередь — и ветвь не снимает")
        assertEquals(0L, after.lastSuccessAtMs)
        assertEquals(30L, after.resyncLastSuccessAtMs, "но игрок видит, что перечитывание прошло")
        assertTrue(after.hasResyncEverSucceeded)
    }

    @Test
    fun `given no account then every kind of run reads as zeroes`() = runTest {
        val status = repository(FakeStore(), null).observeStatus().first()

        assertEquals(0L, status.profileLastSuccessAtMs)
        assertNull(status.profileLastError)
        assertEquals(0L, status.resyncLastSuccessAtMs)
        assertNull(status.resyncLastError)
    }

    @Test
    fun `given there is no account then the run is attributed to nobody`() = runTest {
        // Записать след гостя под пустым ключом значило бы показать это время первому вошедшему.
        repository(FakeStore(), null).recordSuccess(SyncRunKind.CONTENT, atMs = 900L)

        assertNull(trace.lastWrittenOwner)
    }
}
