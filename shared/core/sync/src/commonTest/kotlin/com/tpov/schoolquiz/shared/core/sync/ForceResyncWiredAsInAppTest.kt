package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.fake.EmptyOutboxStore
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncRunTraceStore
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ресинк собран ровно так, как его собирает composition root (`SyncModule.kt`): читающая сторона —
 * настоящий [CatalogSyncListOrchestrator], и ворота у них ОДНИ.
 *
 * Прежние тесты ресинка подсовывали фейковый [Syncable] и свои ворота по умолчанию, поэтому
 * вложенный проход в них не возникал вовсе. В приложении он возникает всегда: `ForceResync.run()`
 * заходит в ворота, а `orchestrator.sync()` внутри заходит в них же — и `Mutex`, не знающий
 * владельца, вешал бы игрока навсегда уже ПОСЛЕ того, как все курсоры удалены.
 */
class ForceResyncWiredAsInAppTest {

    private val catalogId = CatalogId("cat-1")

    /**
     * Исход ресинка записывает тот же [SyncRun], что и у планового прогона, — как в графе
     * приложения. Свой записи у ресинка нет: собрать его без владельца решения нельзя.
     */
    private fun syncRun(): SyncRun =
        SyncRun(
            status =
                DefaultSyncStatusRepository(
                    store = EmptyOutboxStore(),
                    trace = FakeSyncRunTraceStore(),
                    currentUidFlow = flowOf("uid-1"),
                    currentUidProvider = { "uid-1" },
                ),
        ) { 1_700_000_000_000L }

    private fun orchestrator(
        gate: SyncGate,
        state: SyncStateRepository,
        catalogRepo: FakeCatalogRepository,
    ) = CatalogSyncListOrchestrator(
        catalogRepo = catalogRepo,
        questRepo = FakeQuestRepository(),
        sectionRepo = FakeSectionRepository(),
        themeRepo = FakeThemeRepository(),
        lessonRepo = FakeLessonRepository(),
        questionRepo = FakeQuestionRepository(),
        syncStateRepo = state,
        syncChangeRemote = FakeCatalogJournal(),
        gate = gate,
    )

    @Test
    fun `given the app wiring then the resync finishes instead of hanging on its own gate`() = runTest {
        val gate = SyncGate()
        val state = InMemorySyncStateRepository()
        state.setCursor(catalogSyncCursorId(catalogId), SyncCursor(5_000L, "quest_q-9"))
        state.setCursor(CATALOG_LIST_CURSOR_ID, SyncCursor(9_000L))
        val catalogRepo = FakeCatalogRepository().apply { nextChangedIds = setOf(catalogId) }
        val resync = ForceResync(state, orchestrator(gate, state, catalogRepo), gate, syncRun())

        val outcome = withTimeoutOrNull(HANG_TIMEOUT_MS) { resync.run() }

        assertNotNull(outcome, "ресинк повис на воротах — и повис уже после того, как курсоры удалены")
        assertTrue(outcome.isSuccess)
        assertEquals(1, catalogRepo.refreshCalls, "читающая сторона обязана отработать, а не ждать саму себя")
    }

    @Test
    fun `given a resync has run then the scheduled pass still gets through the gate`() = runTest {
        // Повисший проход не отпускает ворота, и следом за ним встаёт плановая синхронизация —
        // каталоги, квесты, уроки и вопросы перестают обновляться совсем.
        val gate = SyncGate()
        val state = InMemorySyncStateRepository()
        val catalogRepo = FakeCatalogRepository().apply { nextChangedIds = setOf(catalogId) }
        val readSide = orchestrator(gate, state, catalogRepo)

        withTimeoutOrNull(HANG_TIMEOUT_MS) { ForceResync(state, readSide, gate, syncRun()).run() }
        val scheduled = withTimeoutOrNull(HANG_TIMEOUT_MS) { readSide.sync() }

        assertNotNull(scheduled, "ворота остались закрытыми навсегда")
        assertTrue(scheduled.isSuccess)
    }

    private companion object {
        /** Виртуальное время: `runTest` проматывает его сразу, как только всё встало. */
        const val HANG_TIMEOUT_MS = 5_000L
    }
}
