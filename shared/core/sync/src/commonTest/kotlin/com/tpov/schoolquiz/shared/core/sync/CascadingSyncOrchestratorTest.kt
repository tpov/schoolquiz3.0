package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.core.sync.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeUserStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [CascadingSyncOrchestrator].
 *
 * Each test uses fresh fakes constructed in [setUp]. Tests trace to spec scenarios
 * from docs/features/home-and-my-quests/0-spec.md and the phase-03 tests.md.
 *
 * NOTE: These tests depend on [CascadingSyncOrchestrator] and [SyncLevel] being
 * created by backend-dev in phase-03, and on the sync module's build.gradle.kts
 * having all 7 domain module dependencies added (catalog, quest, section, theme,
 * lesson, question, app-shell).
 */
class CascadingSyncOrchestratorTest {

    private lateinit var fakeCatalogRepo: FakeCatalogRepository
    private lateinit var fakeQuestRepo: FakeQuestRepository
    private lateinit var fakeSectionRepo: FakeSectionRepository
    private lateinit var fakeThemeRepo: FakeThemeRepository
    private lateinit var fakeLessonRepo: FakeLessonRepository
    private lateinit var fakeQuestionRepo: FakeQuestionRepository
    private lateinit var fakeSyncState: FakeSyncStateRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeUserStats: FakeUserStatsRepository
    private lateinit var orchestrator: CascadingSyncOrchestrator

    @BeforeTest
    fun setUp() {
        fakeCatalogRepo = FakeCatalogRepository()
        fakeQuestRepo = FakeQuestRepository()
        fakeSectionRepo = FakeSectionRepository()
        fakeThemeRepo = FakeThemeRepository()
        fakeLessonRepo = FakeLessonRepository()
        fakeQuestionRepo = FakeQuestionRepository()
        fakeSyncState = FakeSyncStateRepository()
        fakeAuth = FakeAuthRepository(initialUid = "test-uid")
        fakeUserStats = FakeUserStatsRepository()
        orchestrator = CascadingSyncOrchestrator(
            catalogRepo = fakeCatalogRepo,
            questRepo = fakeQuestRepo,
            sectionRepo = fakeSectionRepo,
            themeRepo = fakeThemeRepo,
            lessonRepo = fakeLessonRepo,
            questionRepo = fakeQuestionRepo,
            syncStateRepo = fakeSyncState,
            authRepo = fakeAuth,
            userStatsRepo = fakeUserStats,
        )
    }

    // ── AC#10 — catalog cv unchanged → quests not fetched (scenario 28) ────────

    @Test
    fun `when catalog cv unchanged then quests not fetched`() = runTest {
        // GIVEN: no catalogs changed
        fakeCatalogRepo.nextChangedIds = emptySet()

        // WHEN
        orchestrator.sync()

        // THEN: quest step not triggered
        assertEquals(0, fakeQuestRepo.refreshCallCount)
    }

    // ── AC#10 — catalog cv grew → quests fetched (scenario 29) ───────────────

    @Test
    fun `when catalog cv grew then quests fetched`() = runTest {
        // GIVEN: one catalog changed
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))

        // WHEN
        orchestrator.sync()

        // THEN: quest refresh called with the changed catalog ID
        assertEquals(1, fakeQuestRepo.refreshCallCount)
        assertEquals(setOf(CatalogId("c1")), fakeQuestRepo.lastRefreshCatalogIds)
    }

    // ── AC#11 — quest cv grew → sections fetched ─────────────────────────────

    @Test
    fun `when quest cv grew then sections fetched`() = runTest {
        // GIVEN: catalog changed, quest changed with cv growth
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(setOf(QuestId("q1")))

        // WHEN
        orchestrator.sync()

        // THEN: section refresh triggered
        assertEquals(1, fakeSectionRepo.refreshCallCount)
    }

    // ── Matrix 3.7 — no changed quests → sections not fetched ─────────────────

    @Test
    fun `when no changed quests then sections not fetched`() = runTest {
        // GIVEN: catalog changed but no quests changed
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(emptySet())

        // WHEN
        orchestrator.sync()

        // THEN: section step skipped
        assertEquals(0, fakeSectionRepo.refreshCallCount)
    }

    // ── AC#54 — step 2 fails → questsCursor not advanced ─────────────────────

    @Test
    fun `when step2 fails then questsCursor not advanced`() = runTest {
        // GIVEN: catalog cursor already at 1000 (CatalogRepositoryImpl-managed)
        fakeSyncState.setCursor("catalogs", 1_000L)
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshFailure(RuntimeException("timeout"))

        // WHEN
        val result = orchestrator.sync()

        // THEN
        assertTrue(result.isFailure)
        assertEquals(0L, fakeSyncState.getCursor("quests"))
    }

    // ── AC#54 positive — step 1 succeeds → catalogsCursor advanced by orchestrator ───

    @Test
    fun `when step1 succeeds then catalogsCursor advanced by orchestrator`() = runTest {
        // GIVEN: catalog cursor at 0 (no prior sync)
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))

        // WHEN
        orchestrator.sync()

        // THEN: orchestrator advances catalog cursor to freshTime (Clock.System.now())
        // after full subtree success — subtree-atomic advance (B2 fix, uniform with all levels)
        assertTrue(fakeSyncState.getCursor("catalogs") > 0L)
    }

    // ── AC#50 — batch > 30 parent IDs → batched into multiple fetches ─────────

    @Test
    fun `when 31 parent ids then batched into two fetches`() = runTest {
        // GIVEN: catalog changed, quest returns 31 changed IDs
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        val thirtyOneIds = (1..31).map { QuestId("q$it") }.toSet()
        fakeQuestRepo.setNextRefreshChanged(thirtyOneIds)
        fakeSectionRepo.captureRefreshBatches = true

        // WHEN
        orchestrator.sync()

        // THEN: section refresh called in batches of ≤30
        assertTrue(fakeSectionRepo.batchedRefreshCalls.size >= 2)
        fakeSectionRepo.batchedRefreshCalls.forEach { batch ->
            assertTrue(batch.size <= 30, "each batch must be ≤ 30, was ${batch.size}")
        }
        val allIds = fakeSectionRepo.batchedRefreshCalls.flatten().toSet()
        assertEquals(thirtyOneIds.map { it.value }.toSet(), allIds.map { it.value }.toSet())
    }

    // ── EDGE 4.6 / ADR-CMP-49 guest mode — uid null → quest refresh with null ─

    @Test
    fun `when uid null then quest refresh called with null uid`() = runTest {
        // GIVEN: user signed out → uid = null
        fakeAuth.signOut()
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))

        // WHEN
        orchestrator.sync()

        // THEN: quest refresh called with null uid (Query A skipped)
        assertEquals(1, fakeQuestRepo.refreshCallCount)
        assertEquals(null, fakeQuestRepo.lastRefreshUid)
    }

    // ── Matrix 4.1 / 4.5 — availableShelves = {"home","arena"} ───────────────

    @Test
    fun `when sync runs then availableShelves equals home and arena`() = runTest {
        // GIVEN: catalog changed
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))

        // WHEN
        orchestrator.sync()

        // THEN: quest step receives hardcoded baseline shelves
        assertEquals(setOf("home", "arena"), fakeQuestRepo.lastRefreshShelves)
    }

    // ── AC#17 — any step failure → Result.failure ─────────────────────────────

    @Test
    fun `when step fails then result failure`() = runTest {
        // GIVEN: catalog changed, quest refresh fails
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshFailure(RuntimeException("test failure"))

        // WHEN
        val result = orchestrator.sync()

        // THEN
        assertTrue(result.isFailure)
    }

    // ── CancellationException propagates (structured concurrency) ─────────────

    @Test
    fun `when cancellation exception thrown then it propagates`() = runTest {
        // GIVEN: catalog changed, quest repo throws CancellationException
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.throwCancellation = true

        // WHEN / THEN: CancellationException is NOT wrapped in Result — propagates
        assertFailsWith<CancellationException> {
            orchestrator.sync()
        }
    }

    // ── Cursor advancement for quests on success ───────────────────────────────

    @Test
    fun `when quest step succeeds then quests cursor is advanced`() = runTest {
        // GIVEN: catalog changed, no further cascade needed
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(emptySet())

        // WHEN
        val result = orchestrator.sync()

        // THEN: sync succeeded AND orchestrator called setCursor for quests
        assertTrue(result.isSuccess)
        assertTrue(
            fakeSyncState.setCursorCalls.any { (col, _) -> col == "quests" },
            "orchestrator must advance quest cursor after successful quest sync",
        )
    }

    // ── Section cursor: if quest step succeeds but section fails, quest cursor
    //    was advanced but section cursor is NOT ────────────────────────────────

    @Test
    fun `when section step fails then section cursor not advanced`() = runTest {
        // GIVEN: catalog + quest changed, section fails
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(setOf(QuestId("q1")))
        fakeSectionRepo.setNextRefreshFailure(RuntimeException("section error"))

        // WHEN
        val result = orchestrator.sync()

        // THEN: overall failure, section cursor not advanced
        assertTrue(result.isFailure)
        assertEquals(0L, fakeSyncState.getCursor("sections"))
    }
}
