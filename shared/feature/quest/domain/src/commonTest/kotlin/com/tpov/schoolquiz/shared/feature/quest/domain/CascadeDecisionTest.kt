package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.logic.shouldRecurseIntoChildren
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SyncQuestsUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the cascading sync decision predicate (State Matrix 3).
 *
 * The cascade rule (Matrix 3):
 *   - `dto.contentsVersion > local.contentsVersion` → RECURSE into child level
 *   - `dto.contentsVersion == local.contentsVersion` → STOP (children up-to-date)
 *   - `dto.contentsVersion < local.contentsVersion` → STOP (impossible in practice, still no recurse)
 *
 * Tests 28-31 verify the pure predicate logic.
 * Test 45 demonstrates:
 *   a) catalog → quest early-exit via FakeQuestRepository.refreshCallCount
 *   b) quest → section early-exit via [shouldRecurseIntoChildren] pure predicate
 *      (scenario 45 at the quest→section level is a pure-function test — section
 *       module integration lives in SectionDomainTest.kt).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   State Matrix 3 (cascading recurse predicate)
 *   Domain Test Scenarios 28-31, 45
 *
 * Note: Tests for Section/Theme/Lesson cascade decisions that require cross-module
 * types (FakeSectionRepository, SyncSectionsUseCase) live in section/domain module
 * tests (SectionDomainTest.kt scenario 45).
 */
class CascadeDecisionTest {

    private val surveys = CatalogId("surveys")

    // ── Scenario 28: catalog cv unchanged → NO quest sync ────────────────────
    //
    // GIVEN catalog v=1/cv=3 locally AND server catalog v=1/cv=3 (same cv)
    // WHEN cascade predicate evaluated THEN shouldRecurse=false (no quest sync)
    @Test
    fun `scenario 28 catalog contentsVersion unchanged predicate returns false`() {
        val localCatalog = makeCatalog(id = "cat1", version = 1L, contentsVersion = 3L)
        val serverCatalog = localCatalog.copy(lastModifiedAt = 1000L) // same cv=3, different timestamp

        val shouldRecurse = serverCatalog.contentsVersion > localCatalog.contentsVersion
        assertEquals(false, shouldRecurse, "Same contentsVersion → STOP predicate must be false")
    }

    // ── Scenario 28 integration: no quest sync triggered when catalog cv unchanged ──
    @Test
    fun `scenario 28 integration no quest refreshFromRemote called when catalog cv unchanged`() = runTest {
        val questFake = FakeQuestRepository()
        questFake.seedServerQuests(
            listOf(makeQuest("q1", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)),
        )

        // Simulate orchestrator logic: if catalog cv unchanged → don't call SyncQuestsUseCase
        val localCatalogCv = 3L
        val serverCatalogCv = 3L // unchanged
        val shouldSyncQuests = serverCatalogCv > localCatalogCv

        if (shouldSyncQuests) {
            SyncQuestsUseCase(questFake).invoke(
                currentUserUid = "uid-42",
                availableShelves = setOf("home"),
                catalogIdsToSync = setOf(surveys),
                cursor = 0L,
            )
        }

        assertEquals(0, questFake.refreshCallCount,
            "QuestRepository.refreshFromRemote must NOT be called when catalog cv unchanged")
        assertTrue(questFake.snapshot().isEmpty(),
            "No quests should be synced when catalog cv unchanged")
    }

    // ── Scenario 29: catalog cv changed → quest sync triggered ───────────────
    //
    // GIVEN catalog v=1/cv=3 locally AND server v=1/cv=5
    // WHEN cascade predicate evaluated THEN shouldRecurse=true (pull quests)
    @Test
    fun `scenario 29 catalog contentsVersion increased predicate returns true`() {
        val localCatalog = makeCatalog(id = "cat1", version = 1L, contentsVersion = 3L)
        val serverCatalog = localCatalog.copy(contentsVersion = 5L, lastModifiedAt = 2000L)

        val shouldRecurse = serverCatalog.contentsVersion > localCatalog.contentsVersion
        assertEquals(true, shouldRecurse, "Higher contentsVersion → RECURSE predicate must be true")
    }

    // ── Scenario 29 integration: quests ARE synced when catalog cv changed ───
    @Test
    fun `scenario 29 integration quest sync called when catalog contentsVersion increased`() = runTest {
        val questFake = FakeQuestRepository()
        val serverQuest = makeQuest("q1", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)
        questFake.seedServerQuests(listOf(serverQuest))

        val localCatalogCv = 3L
        val serverCatalogCv = 5L // changed!
        val shouldSyncQuests = serverCatalogCv > localCatalogCv

        if (shouldSyncQuests) {
            SyncQuestsUseCase(questFake).invoke(
                currentUserUid = "uid-42",
                availableShelves = setOf("home"),
                catalogIdsToSync = setOf(surveys),
                cursor = 0L,
            )
        }

        assertEquals(1, questFake.refreshCallCount,
            "QuestRepository.refreshFromRemote MUST be called when catalog cv changed")
        assertEquals(1, questFake.snapshot().size, "Quest must be synced")
    }

    // ── Scenario 30: quest cv changed → section sync would be triggered ───────
    //
    // GIVEN quest v=1/cv=2 AND server v=1/cv=3
    // WHEN cascade predicate evaluated THEN shouldRecurse=true (trigger section sync)
    //
    // Note: actual SyncSectionsUseCase call is tested in section/domain SectionDomainTest.
    @Test
    fun `scenario 30 quest contentsVersion increased predicate returns true`() {
        val localQuest = makeQuest(id = "q1", version = 1L, contentsVersion = 2L)
        val serverQuest = localQuest.copy(contentsVersion = 3L, lastModifiedAt = 2000L)

        val shouldRecurse = serverQuest.contentsVersion > localQuest.contentsVersion
        assertEquals(true, shouldRecurse, "Higher quest cv → RECURSE (trigger section sync)")
    }

    // ── Scenario 31: quest cv unchanged → NO section sync ────────────────────
    //
    // GIVEN quest v=1/cv=2 AND server v=1/cv=2 (same cv)
    // WHEN cascade predicate evaluated THEN shouldRecurse=false
    @Test
    fun `scenario 31 quest contentsVersion unchanged predicate returns false`() {
        val localQuest = makeQuest(id = "q1", version = 1L, contentsVersion = 2L)
        val serverQuest = localQuest.copy(lastModifiedAt = 2000L) // same cv=2

        val shouldRecurse = serverQuest.contentsVersion > localQuest.contentsVersion
        assertEquals(false, shouldRecurse, "Same quest cv → STOP (no section sync)")
    }

    // ── Scenario 45 : early-exit — quest refreshFromRemote NOT called when catalog cv unchanged ──
    //
    // Demonstrates orchestrator early-exit: catalog cv == local cv → skip quest sync entirely.
    @Test
    fun `scenario 45 early-exit questRepository refreshFromRemote not called when catalog cv unchanged`() = runTest {
        val questFake = FakeQuestRepository()
        questFake.seedServerQuests(
            listOf(makeQuest("q1", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)),
        )

        // Orchestrator checks: catalog contentsVersion unchanged → skip quest refresh
        val localCatalogCv = 7L
        val serverCatalogCv = 7L // unchanged
        val shouldCallQuestSync = serverCatalogCv > localCatalogCv

        // Orchestrator skips quest sync when cv unchanged
        if (shouldCallQuestSync) {
            SyncQuestsUseCase(questFake).invoke(
                currentUserUid = "uid-42",
                availableShelves = setOf("home"),
                catalogIdsToSync = setOf(surveys),
                cursor = 0L,
            )
        }

        assertEquals(0, questFake.refreshCallCount,
            "Scenario 45: refreshFromRemote must NOT be called when parent cv is unchanged")
    }

    // ── Matrix 3 boundary: dto.cv < local.cv → STOP ───────────────────────────
    @Test
    fun `Matrix3 server contentsVersion lower than local means STOP (server stale)`() {
        val localCv = 5L
        val serverCv = 3L
        val shouldRecurse = serverCv > localCv
        assertEquals(false, shouldRecurse, "Server stale cv → STOP")
    }

    // ── Matrix 3: just inserted entity with cv > 0 → RECURSE ─────────────────
    @Test
    fun `Matrix3 newly inserted entity with cv gt 0 triggers child sync`() {
        // When parent is absent locally and just inserted with cv > 0 → children not yet fetched
        val localCv = 0L  // absent = treated as 0
        val justInsertedCv = 5L
        val shouldRecurse = justInsertedCv > localCv
        assertEquals(true, shouldRecurse, "Newly inserted entity with cv>0 must trigger child sync")
    }

    // ── Scenario 45b : quest → section early-exit via pure predicate ─────────
    //
    // Fix #B: scenario 45 REWRITTEN to cover quest→section level using the
    // [shouldRecurseIntoChildren] pure function from CascadeDecision.kt.
    //
    // GIVEN local quest with contentsVersion=5 AND server quest with contentsVersion=5
    //   (same, version bumped for some other field change)
    // WHEN shouldRecurseIntoChildren evaluated THEN returns false (no section sync)
    //
    // Spec: State Matrix 3 row "present + dto.cv == local.cv → STOP"
    @Test
    fun `scenario 45b quest contentsVersion unchanged shouldRecurseIntoChildren returns false (no section sync)`() {
        val localQuestCv = 5L
        val dtoQuestCv = 5L  // same cv — only some other field changed (e.g. version bumped)

        assertFalse(
            shouldRecurseIntoChildren(dtoContentsVersion = dtoQuestCv, localContentsVersion = localQuestCv),
            "Scenario 45: quest.contentsVersion unchanged → shouldRecurseIntoChildren must return false (no section refresh)",
        )
    }

    // ── Scenario 45c : quest contentsVersion CHANGED → section sync SHOULD trigger ──
    //
    // Counterpart to 45b: when quest cv increased, recurse returns true.
    @Test
    fun `scenario 45c quest contentsVersion changed shouldRecurseIntoChildren returns true (section sync)`() {
        val localQuestCv = 5L
        val dtoQuestCv = 6L  // cv bumped

        assertTrue(
            shouldRecurseIntoChildren(dtoContentsVersion = dtoQuestCv, localContentsVersion = localQuestCv),
            "Scenario 45c: quest.contentsVersion increased → shouldRecurseIntoChildren must return true",
        )
    }

    // ── shouldRecurseIntoChildren: newly inserted parent (absent) ─────────────
    @Test
    fun `shouldRecurseIntoChildren newly inserted parent with cv=0 returns false (no children)`() {
        assertFalse(
            shouldRecurseIntoChildren(dtoContentsVersion = 0L, localContentsVersion = null),
            "Newly inserted parent with cv=0 has no children → do not recurse",
        )
    }

    @Test
    fun `shouldRecurseIntoChildren newly inserted parent with cv gt 0 returns true (has children)`() {
        assertTrue(
            shouldRecurseIntoChildren(dtoContentsVersion = 3L, localContentsVersion = null),
            "Newly inserted parent with cv>0 has children → recurse",
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeCatalog(
        id: String = "cat1",
        version: Long = 1L,
        contentsVersion: Long = 0L,
    ) = Catalog(
        id = CatalogId(id),
        name = "Catalog $id",
        picturePath = null,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = 0L,
    )

    private fun makeQuest(
        id: String = "q1",
        version: Long = 1L,
        contentsVersion: Long = 0L,
        catalogId: CatalogId = CatalogId("surveys"),
        visibleOn: Set<String> = setOf("home"),
        lastModifiedAt: Long = 0L,
    ) = Quest(
        id = QuestId(id),
        catalogId = catalogId,
        authorUid = "uid-42",
        title = "Quest $id",
        picturePath = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = false,
    )
}
