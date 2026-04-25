package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.core.sync.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeCatalogRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSectionRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.core.sync.fake.FakeUserStatsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multi-step integration tests for [CascadingSyncOrchestrator].
 *
 * Traces to AC#55-AC#58 from docs/features/home-and-my-quests/0-spec.md.
 *
 * NOTE: Depends on backend-dev implementing [CascadingSyncOrchestrator] + [SyncLevel]
 * in phase-03, and on all 7 domain module dependencies being added to build.gradle.kts.
 */
class CascadeSyncIntegrationTest {

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

    // ── AC#56 — process death → full resync is idempotent ────────────────────

    @Test
    fun `process death then full resync is idempotent`() = runTest {
        // GIVEN: first sync populates data
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(setOf(QuestId("q1")))

        // Sync #1
        val result1 = orchestrator.sync()
        assertTrue(result1.isSuccess)
        val refreshCount1 = fakeQuestRepo.refreshCallCount

        // Simulate process death: all cursors reset to 0 (SyncStateRepository cleared)
        fakeSyncState.resetAll()
        // Catalog and quest fakes retain their "server state" — same data available

        // Sync #2 (full re-sync from cursor=0)
        val result2 = orchestrator.sync()
        assertTrue(result2.isSuccess)

        // THEN: both syncs succeeded and quest repo was called again (idempotent re-fetch)
        assertEquals(refreshCount1 + 1, fakeQuestRepo.refreshCallCount)
    }

    // ── AC#57 — retry after partial fail → cursor advances on second sync ─────

    @Test
    fun `retry after partial fail then cursor advances`() = runTest {
        // GIVEN: first sync — catalog succeeds, quest fails
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshFailure(RuntimeException("network error"))

        // Sync #1 (partial: catalog ok, quest failed)
        val result1 = orchestrator.sync()
        assertTrue(result1.isFailure)
        assertEquals(0L, fakeSyncState.getCursor("quests"))

        // Second sync — quest now succeeds
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1")) // same catalogs changed
        fakeQuestRepo.setNextRefreshChanged(setOf(QuestId("q1")))

        // Sync #2 (full success)
        val result2 = orchestrator.sync()
        assertTrue(result2.isSuccess)

        // THEN: orchestrator called setCursor for quests after successful retry
        assertTrue(
            fakeSyncState.setCursorCalls.any { (col, _) -> col == "quests" },
            "orchestrator must advance quest cursor after successful sync",
        )
        assertEquals(2, fakeQuestRepo.refreshCallCount)
    }

    // ── AC#19 integration — full 6-level cascade executes ─────────────────────

    @Test
    fun `full cascade 6 levels with fakes`() = runTest {
        // GIVEN: all 6 levels have changed data
        val catalogId = CatalogId("c1")
        val questId = QuestId("q1")
        val sectionId = SectionId("s1")
        val themeId = ThemeId("t1")
        val lessonId = LessonId("l1")

        fakeCatalogRepo.nextChangedIds = setOf(catalogId)
        fakeQuestRepo.setNextRefreshChanged(setOf(questId))
        fakeSectionRepo.setNextRefreshChanged(setOf(sectionId))
        fakeThemeRepo.setNextRefreshChanged(setOf(themeId))
        fakeLessonRepo.setNextRefreshChanged(setOf(lessonId))

        // WHEN
        val result = orchestrator.sync()

        // THEN: all 6 repo levels called
        assertTrue(result.isSuccess)
        assertTrue(fakeCatalogRepo.refreshCalls >= 1, "catalogs not refreshed")
        assertTrue(fakeQuestRepo.refreshCallCount >= 1, "quests not refreshed")
        assertTrue(fakeSectionRepo.refreshCallCount >= 1, "sections not refreshed")
        assertTrue(fakeThemeRepo.refreshCallCount >= 1, "themes not refreshed")
        assertTrue(fakeLessonRepo.refreshCallCount >= 1, "lessons not refreshed")
        assertTrue(fakeQuestionRepo.refreshCallCount >= 1, "questions not refreshed")
    }

    // ── AC#58 — server invariant B: descendant cascade on visibleOn change ─────

    @Test
    fun `when server invariant B applied then client pulls descendants`() = runTest {
        // GIVEN: quest.visibleOn changed → all descendants get fresh lastModifiedAt
        // Simulated via fakes: quest has cv growth → triggers section sync
        fakeCatalogRepo.nextChangedIds = setOf(CatalogId("c1"))
        fakeQuestRepo.setNextRefreshChanged(setOf(QuestId("q1")))
        fakeSectionRepo.setNextRefreshChanged(setOf(SectionId("s1")))

        // WHEN
        orchestrator.sync()

        // THEN: section and theme refreshed (cascade propagated into descendants)
        assertEquals(1, fakeSectionRepo.refreshCallCount)
        assertEquals(1, fakeThemeRepo.refreshCallCount)
    }

    // ── AC#55 — retry with in-memory cursors intact → step 1 empty ────────────

    @Test
    fun `retry with in memory cursors intact then step1 empty on second sync`() = runTest {
        // GIVEN: first sync succeeded — catalogs fetched, no quest changes
        fakeSyncState.setCursor("catalogs", 1_000L)
        fakeCatalogRepo.nextChangedIds = emptySet() // second sync sees no changed catalogs

        // WHEN: second sync (same process, cursors intact)
        orchestrator.sync()

        // THEN: quest refresh NOT called (no changed catalogs → empty catalogIdsToSync)
        assertEquals(0, fakeQuestRepo.refreshCallCount)
    }
}
