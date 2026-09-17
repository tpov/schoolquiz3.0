package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.sync.fake.EmptyOutboxStore
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncRunTraceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * AD-30. Сегодня разошедшиеся курсор и база чинятся только переустановкой: `setCursor` монотонен,
 * и курсор, ушедший вперёд локальных данных, назад не возвращается.
 */
class ForceResyncTest {

    private class RecordingSyncable(
        private val outcome: Result<Unit> = Result.success(Unit),
        private val onSync: () -> Unit = {},
    ) : Syncable {
        var calls = 0

        override suspend fun sync(): Result<Unit> {
            calls++
            onSync()
            return outcome
        }
    }

    @Test
    fun `given cursors have run ahead then a resync puts them back to the beginning`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("catalog_sync:cat-1", SyncCursor(5_000L, "quest_q-9"))
        state.setCursor(CATALOG_LIST_CURSOR_ID, SyncCursor(9_000L))

        ForceResync(state, RecordingSyncable(), syncRun = syncRun).run()

        // Обе половины пары: время и id документа. Сброс только времени оставил бы id, и первая же
        // страница началась бы «после документа», которого в начале журнала нет.
        assertEquals(SyncCursor.BEGINNING, state.getCursor("catalog_sync:cat-1"))
        assertEquals(SyncCursor.BEGINNING, state.getCursor(CATALOG_LIST_CURSOR_ID))
    }

    @Test
    fun `given a resync then cursors are cleared before the journals are read`() = runTest {
        // Обратный порядок оставил бы курсор от прохода, случившегося до сброса, и половина
        // журнала снова оказалась бы пропущена.
        val state = InMemorySyncStateRepository()
        state.setCursor("catalog_sync:cat-1", SyncCursor(5_000L, "quest_q-9"))
        var cursorSeenBySync: SyncCursor? = null

        ForceResync(
            state,
            object : Syncable {
                override suspend fun sync(): Result<Unit> {
                    cursorSeenBySync = state.getCursor("catalog_sync:cat-1")
                    return Result.success(Unit)
                }
            },
            syncRun = syncRun,
        ).run()

        assertEquals(SyncCursor.BEGINNING, cursorSeenBySync, "чтение обязано начаться уже с обнулённого курсора")
    }

    @Test
    fun `given the read side fails then the failure is reported, and cursors stay cleared`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("catalog_sync:cat-1", SyncCursor(5_000L, "quest_q-9"))
        val boom = IllegalStateException("no network")

        val result = ForceResync(state, RecordingSyncable(Result.failure(boom)), syncRun = syncRun).run()

        assertTrue(result.isFailure)
        assertEquals(SyncCursor.BEGINNING, state.getCursor("catalog_sync:cat-1"), "сброс уже случился и откату не подлежит")
    }

    // ── Исход ресинка решает не он сам ────────────────────────────────────────

    private val now = 1_700_000_000_000L
    private val earlier = 1_600_000_000_000L
    private val uid = "uid-1"

    private val trace = FakeSyncRunTraceStore()

    private val status =
        DefaultSyncStatusRepository(
            store = EmptyOutboxStore(),
            trace = trace,
            currentUidFlow = flowOf(uid),
            currentUidProvider = { uid },
        )

    private val syncRun = SyncRun(status = status) { now }

    @Test
    fun `given a scheduled run failed then a resync that went through does not report a success`() = runTest {
        // «Перечитать всё» перечитывает каталог — и только его: очередь не отправляется, профиль
        // не обновляется. Пока ресинк писал успех сам, удавшееся перечитывание объявляло прогон
        // прошедшим целиком: свежее время вставало поверх ветви упавшего планового прогона, и
        // ошибка, которая не починилась, исчезала с экрана — а игрок оставался с непереданной
        // очередью и уверенностью, что всё уехало.
        trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))
        val brokenRun = RecordingSyncable(Result.failure(SyncFailure(SyncError.NoNetwork)))
        syncRun.run(SyncRunKind.CONTENT, listOf(brokenRun))

        val outcome = ForceResync(InMemorySyncStateRepository(), RecordingSyncable(), syncRun = syncRun).run()

        assertTrue(outcome.isSuccess, "перечитывание прошло — это правда")
        val snapshot = status.observeStatus().first()
        assertEquals(SyncError.NoNetwork, snapshot.lastError, "ошибка не починилась — снимать её нечем")
        assertEquals(earlier, snapshot.lastSuccessAtMs, "планового прогона не было — свежего времени нет")
        assertEquals(now, snapshot.resyncLastSuccessAtMs, "а у самого перечитывания ответ есть, и он свой")
    }

    @Test
    fun `given a resync failed then its branch is its own and the scheduled success stays`() = runTest {
        trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))

        val outcome =
            ForceResync(
                InMemorySyncStateRepository(),
                RecordingSyncable(Result.failure(SyncFailure(SyncError.NoNetwork))),
                syncRun = syncRun,
            ).run()

        assertTrue(outcome.isFailure)
        val snapshot = status.observeStatus().first()
        assertEquals(SyncError.NoNetwork, snapshot.resyncLastError, "ветвь, а не текст исключения (AD-15)")
        assertEquals(earlier, snapshot.lastSuccessAtMs, "плановый прогон когда-то прошёл — это не отменилось")
        assertNull(snapshot.lastError, "и жаловаться от его имени ресинку нечем")
    }

    @Test
    fun `given a resync then the read side runs exactly once`() = runTest {
        val readSide = RecordingSyncable()

        ForceResync(InMemorySyncStateRepository(), readSide, syncRun = syncRun).run()

        assertEquals(1, readSide.calls)
    }
}
