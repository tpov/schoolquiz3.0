package com.tpov.schoolquiz.shared.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        state.setCursor("catalog_sync:cat-1", 5_000L)
        state.setCursor(CATALOG_LIST_CURSOR_ID, 9_000L)

        ForceResync(state, RecordingSyncable()).run()

        assertEquals(0L, state.getCursor("catalog_sync:cat-1"))
        assertEquals(0L, state.getCursor(CATALOG_LIST_CURSOR_ID))
    }

    @Test
    fun `given a resync then cursors are cleared before the journals are read`() = runTest {
        // Обратный порядок оставил бы курсор от прохода, случившегося до сброса, и половина
        // журнала снова оказалась бы пропущена.
        val state = InMemorySyncStateRepository()
        state.setCursor("catalog_sync:cat-1", 5_000L)
        var cursorSeenBySync: Long? = null
        val readSide = RecordingSyncable { }

        ForceResync(
            state,
            object : Syncable {
                override suspend fun sync(): Result<Unit> {
                    cursorSeenBySync = state.getCursor("catalog_sync:cat-1")
                    return Result.success(Unit)
                }
            },
        ).run()

        assertEquals(0L, cursorSeenBySync, "чтение обязано начаться уже с обнулённого курсора")
        assertEquals(0, readSide.calls)
    }

    @Test
    fun `given the read side fails then the failure is reported, and cursors stay cleared`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("catalog_sync:cat-1", 5_000L)
        val boom = IllegalStateException("no network")

        val result = ForceResync(state, RecordingSyncable(Result.failure(boom))).run()

        assertTrue(result.isFailure)
        assertEquals(0L, state.getCursor("catalog_sync:cat-1"), "сброс уже случился и откату не подлежит")
    }

    @Test
    fun `given a resync then the read side runs exactly once`() = runTest {
        val readSide = RecordingSyncable()

        ForceResync(InMemorySyncStateRepository(), readSide).run()

        assertEquals(1, readSide.calls)
    }
}
