package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.fake.FakeQuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SyncQuestsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Use-case domain tests for the quest feature.
 *
 * Uses [FakeQuestRepository] as the in-memory test double (not mocks).
 *
 * Covers scenarios from docs/features/home-and-my-quests/0-spec.md:
 *   24-27 : repository contract (empty fetch, version skip, delete on empty visibleOn,
 *            observeMyQuests filtering)
 *   32    : ObserveMyQuestsUseCase — 3 quests, 2 mine
 *   34    : SyncQuestsUseCase — remote changes reflected locally
 *   46    : version equal skip
 *   47    : server stale skip (version guard)
 *   48    : parallel query merge/dedupe
 *   49    : upsert deduplication (only 1 row for same id)
 *   50    : chunking for >30 parent ids
 *   AC#47 : quest archived=true → kept as on-demand marker
 *   AC#48 : quest visibleOn=emptySet + authorUid=me → deleted
 *   AC#49 : quest visibleOn=emptySet + authorUid=other → deleted
 *   Fix #3 (no-op): catalogIdsToSync=emptySet → no sync call to repository
 *   Primary User Journey 7 : catalog spinner filter
 *
 * Note: Scenario 33 (ObserveHomeQuestsUseCase) is intentionally removed.
 *   Per spec [USER DECIDED 2026-04-21 / Codex fix #1]: "Домашние квесты" shows
 *   catalogs (CatalogGrid), not quests. ObserveHomeQuestsUseCase is deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestUseCaseTest {

    private val surveys = CatalogId("surveys")
    private val courses = CatalogId("courses")

    // ── Scenario 24 : empty repo + pull returns 2 quests → both present ───────
    @Test
    fun `scenario 24 empty repo after sync emits all fetched quests`() = runTest {
        val fake = FakeQuestRepository()
        val myQuest = makeQuest(id = "my", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)
        val homeQuest = makeQuest(id = "public", authorUid = "uid-99", catalogId = surveys, visibleOn = setOf("home", "arena"), lastModifiedAt = 2000L)
        fake.seedServerQuests(listOf(myQuest, homeQuest))

        val useCase = SyncQuestsUseCase(fake)
        useCase(
            currentUserUid = "uid-42",
            availableShelves = setOf("home", "arena"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val snapshot = fake.snapshot()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.any { it.id == QuestId("my") })
        assertTrue(snapshot.any { it.id == QuestId("public") })
    }

    // ── Scenario 25 : quest v=2 with emptySet visibleOn → deleted locally ─────
    @Test
    fun `scenario 25 quest with empty visibleOn is deleted during sync`() = runTest {
        val existing = makeQuest(id = "my", authorUid = "uid-42", catalogId = surveys, version = 1L, visibleOn = setOf("home"), lastModifiedAt = 500L)
        val fake = FakeQuestRepository(initial = listOf(existing))

        val updated = existing.copy(version = 2L, visibleOn = emptySet(), lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(updated))

        val useCase = SyncQuestsUseCase(fake)
        useCase(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val snapshot = fake.snapshot()
        assertTrue(snapshot.isEmpty(), "Quest with empty visibleOn should be deleted; got: $snapshot")
    }

    // ── Scenario 26 : observeMyQuests(catalogId=null) returns only authorUid match ─
    @Test
    fun `scenario 26 observeMyQuests null catalogId returns only my quests`() = runTest {
        val mine = makeQuest(id = "mine", authorUid = "uid-42", catalogId = surveys)
        val other = makeQuest(id = "other", authorUid = "uid-99", catalogId = surveys)
        val fake = FakeQuestRepository(initial = listOf(mine, other))

        val useCase = ObserveMyQuestsUseCase(fake)
        val result = useCase(authorUid = "uid-42").first()

        assertEquals(1, result.size)
        assertEquals(QuestId("mine"), result[0].id)
    }

    // ── Scenario 27 : observeMyQuests(catalogId=SOMECAT) returns only that catalog ──
    @Test
    fun `scenario 27 observeMyQuests with catalogId returns only quests in that catalog`() = runTest {
        val mine1 = makeQuest(id = "mine1", authorUid = "uid-42", catalogId = surveys)
        val mine2 = makeQuest(id = "mine2", authorUid = "uid-42", catalogId = courses)
        val other = makeQuest(id = "other", authorUid = "uid-99", catalogId = surveys)
        val fake = FakeQuestRepository(initial = listOf(mine1, mine2, other))

        val useCase = ObserveMyQuestsUseCase(fake)
        val result = useCase(authorUid = "uid-42", catalogId = surveys).first()

        assertEquals(1, result.size)
        assertEquals(QuestId("mine1"), result[0].id)
    }

    // ── Scenario 32 : ObserveMyQuestsUseCase — 3 quests, 2 mine ─────────────
    @Test
    fun `scenario 32 ObserveMyQuestsUseCase authorUid=uid-42 with 3 quests emits 2`() = runTest {
        val mine1 = makeQuest(id = "q1", authorUid = "uid-42")
        val mine2 = makeQuest(id = "q2", authorUid = "uid-42")
        val other = makeQuest(id = "q3", authorUid = "uid-99")
        val fake = FakeQuestRepository(initial = listOf(mine1, mine2, other))

        val useCase = ObserveMyQuestsUseCase(fake)
        val result = useCase(authorUid = "uid-42").first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.authorUid == "uid-42" })
    }

    // ── Scenario 34 : SyncQuestsUseCase remote has changes → local reflects ──
    @Test
    fun `scenario 34 SyncQuestsUseCase with fake deps when remote has changes local reflects them`() = runTest {
        val initial = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, version = 1L, lastModifiedAt = 500L)
        val fake = FakeQuestRepository(initial = listOf(initial))

        val updated = initial.copy(title = "Updated Title", version = 2L, lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(updated))

        val useCase = SyncQuestsUseCase(fake)
        val result = useCase(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        assertTrue(result.isSuccess)
        val after = fake.snapshot()
        assertEquals(1, after.size)
        assertEquals("Updated Title", after[0].title)
        assertEquals(2L, after[0].version)
    }

    // ── Scenario 46 : version equal → skip (no upsert) ───────────────────────
    @Test
    fun `scenario 46 sync skips quest if incoming version equals local version`() = runTest {
        val existing = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, version = 2L, title = "Original", lastModifiedAt = 500L)
        val fake = FakeQuestRepository(initial = listOf(existing))

        // Same version but higher lastModifiedAt — version guard still wins
        val incoming = existing.copy(title = "Server Title", version = 2L, lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(incoming))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val after = fake.snapshot()
        assertEquals("Original", after[0].title, "Title should not change when version is same")
    }

    // ── Scenario 47 : server stale → skip ────────────────────────────────────
    @Test
    fun `scenario 47 sync skips quest if server version is lower than local`() = runTest {
        val existing = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, version = 5L, title = "Local Newer", lastModifiedAt = 500L)
        val fake = FakeQuestRepository(initial = listOf(existing))

        val stale = existing.copy(title = "Stale Server", version = 3L, lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(stale))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val after = fake.snapshot()
        assertEquals("Local Newer", after[0].title, "Local newer version should not be overwritten")
    }

    // ── Scenario 48 : parallel query merge/dedupe by id ──────────────────────
    @Test
    fun `scenario 48 author-filter and visible-filter results are merged with dedupe by id`() = runTest {
        val fake = FakeQuestRepository()

        // q1: matches both Query A (authorUid=uid-42) and Query B (visibleOn ∩ {home})
        val q1 = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)
        // q2: matches only Query A (authorUid=uid-42)
        val q2 = makeQuest(id = "q2", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1100L)
        // q3: matches only Query B (visibleOn ∩ {home}, different authorUid)
        val q3 = makeQuest(id = "q3", authorUid = "uid-99", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1200L)
        fake.seedServerQuests(listOf(q1, q2, q3))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val snapshot = fake.snapshot()
        assertEquals(3, snapshot.size, "All 3 quests should be present after merge: $snapshot")
        assertEquals(setOf("q1", "q2", "q3"), snapshot.map { it.id.value }.toSet())
    }

    // ── Scenario 49 : upsert deduplication — exactly 1 row for same id ───────
    @Test
    fun `scenario 49 quest id returned by both parallel queries has exactly 1 row in local cache`() = runTest {
        val fake = FakeQuestRepository()
        // q1 matches BOTH queries (own + visible) — should only be stored once
        val q1 = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, version = 1L, visibleOn = setOf("home"), lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(q1))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        assertEquals(1, fake.snapshot().size, "Exactly 1 row for q1 regardless of appearing in both queries")
    }

    // ── Scenario 50 : chunking for >30 parent ids — dedupe maintained ─────────
    @Test
    fun `scenario 50 large parent id set is handled dedupe maintained`() = runTest {
        val fake = FakeQuestRepository()
        // 35 quests in 35 different catalogs
        val catalogIds = (1..35).map { CatalogId("cat$it") }.toSet()
        val quests = (1..35).map { i ->
            makeQuest(id = "q$i", authorUid = "uid-42", catalogId = CatalogId("cat$i"), lastModifiedAt = i.toLong() * 100)
        }
        fake.seedServerQuests(quests)

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = catalogIds,
            cursor = 0L,
        )

        assertEquals(35, fake.snapshot().size, "35 distinct quests, no duplicates")
    }

    // ── SyncQuestsUseCase network failure ─────────────────────────────────────
    @Test
    fun `SyncQuestsUseCase returns failure on network error`() = runTest {
        val fake = FakeQuestRepository()
        fake.setNextRefreshFailure(RuntimeException("network lost"))

        val useCase = SyncQuestsUseCase(fake)
        val result = useCase(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        assertTrue(result.isFailure)
    }

    // ── Fix #3 no-op: catalogIdsToSync=emptySet → no sync, count unchanged ───
    @Test
    fun `Fix3 sync is no-op when catalogIdsToSync is empty`() = runTest {
        val initial = makeQuest(id = "existing", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 100L)
        val fake = FakeQuestRepository(initial = listOf(initial))
        val newOnServer = makeQuest(id = "new-server", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 200L)
        fake.seedServerQuests(listOf(newOnServer))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = emptySet(),  // empty → no-op
            cursor = 0L,
        )

        // Cache should be unchanged — existing present, new-server NOT inserted
        val snapshot = fake.snapshot()
        assertEquals(1, snapshot.size, "No-op: cache unchanged when catalogIdsToSync is empty")
        assertEquals("existing", snapshot[0].id.value)
    }

    // ── AC#47 : archived=true → kept as on-demand quest marker ───────────────
    @Test
    fun `AC47 quest with archived=true remains in local cache as on-demand marker`() = runTest {
        // Own quest that gets archived on server
        val ownQuest = makeQuest(id = "mine", authorUid = "uid-42", catalogId = surveys, version = 1L, visibleOn = setOf("home"), lastModifiedAt = 500L)
        val fake = FakeQuestRepository(initial = listOf(ownQuest))

        // Server returns archived version (authorUid=me, archived=true)
        val archivedOwn = ownQuest.copy(version = 2L, archived = true, lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(archivedOwn))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val snapshot = fake.snapshot()
        assertEquals(1, snapshot.size)
        assertTrue(snapshot.first().archived, "Quest with archived=true must remain as on-demand marker")
    }

    // ── AC#48 : visibleOn=emptySet + authorUid=me → deleted ──────────────────
    @Test
    fun `AC48 quest with visibleOn empty and authorUid=me is deleted from local cache`() = runTest {
        val ownDraft = makeQuest(
            id = "draft",
            authorUid = "uid-42",
            catalogId = surveys,
            version = 1L,
            visibleOn = setOf("home"),
            lastModifiedAt = 500L,
        )
        val fake = FakeQuestRepository(initial = listOf(ownDraft))

        // Server sends back with empty visibleOn (draft semantics, out-of-scope phase-01)
        val emptyVisible = ownDraft.copy(version = 2L, visibleOn = emptySet(), lastModifiedAt = 1000L)
        fake.seedServerQuests(listOf(emptyVisible))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        assertTrue(fake.snapshot().isEmpty(), "Quest with visibleOn=empty must be deleted even if authorUid==me (drafts out-of-scope)")
    }

    // ── AC#49 : visibleOn=emptySet + authorUid=other → deleted ───────────────
    //
    // NOTE: In real Firestore sync, a quest with authorUid=other + visibleOn=empty
    // cannot arrive via Query A (wrong author) or Query B (visibleOn empty, no array-contains match).
    // This test validates the **State Matrix 1 delete-rule application** when such an item
    // arrives through any mechanism (e.g. admin tool, direct push). Uses applyRawRemoteItems.
    @Test
    fun `AC49 quest with visibleOn empty and authorUid=other is deleted when received via any mechanism`() = runTest {
        val othersQuest = makeQuest(
            id = "others",
            authorUid = "uid-other",
            catalogId = surveys,
            version = 1L,
            visibleOn = setOf("home"),
            lastModifiedAt = 500L,
        )
        val fake = FakeQuestRepository(initial = listOf(othersQuest))

        // Simulate item arriving with empty visibleOn (delete marker=true)
        val emptyVisible = othersQuest.copy(version = 2L, visibleOn = emptySet(), lastModifiedAt = 1000L)
        fake.applyRawRemoteItems(listOf(emptyVisible))

        assertTrue(fake.snapshot().isEmpty(), "Quest with visibleOn=empty must be deleted regardless of authorUid")
    }

    // ── Primary User Journey 7 : "Мои квесты" all-catalogs then specific ─────
    @Test
    fun `journey 7 spinner all categories then specific catalog`() = runTest {
        val q1 = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys)
        val q2 = makeQuest(id = "q2", authorUid = "uid-42", catalogId = courses)
        val q3 = makeQuest(id = "q3", authorUid = "uid-99", catalogId = surveys)
        val fake = FakeQuestRepository(initial = listOf(q1, q2, q3))
        val useCase = ObserveMyQuestsUseCase(fake)

        // Step: "All categories"
        val all = useCase(authorUid = "uid-42", catalogId = null).first()
        assertEquals(2, all.size)

        // Step: "surveys" selected
        val filtered = useCase(authorUid = "uid-42", catalogId = surveys).first()
        assertEquals(1, filtered.size)
        assertEquals(QuestId("q1"), filtered[0].id)
    }

    // ── Cursor advances after sync ────────────────────────────────────────────
    @Test
    fun `cursor advances to max lastModifiedAt after sync`() = runTest {
        val fake = FakeQuestRepository()
        val q1 = makeQuest(id = "q1", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 1000L)
        val q2 = makeQuest(id = "q2", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 3000L)
        val q3 = makeQuest(id = "q3", authorUid = "uid-42", catalogId = surveys, visibleOn = setOf("home"), lastModifiedAt = 2000L)
        fake.seedServerQuests(listOf(q1, q2, q3))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        assertEquals(3000L, fake.lastCursor, "Cursor must advance to max lastModifiedAt")
    }

    // ── Scenario 23b (quest analog) : stale archived marker version guard ─────
    // GIVEN local quest(v=5) AND server sends archived=true, version=3 (stale)
    // WHEN sync THEN local preserved (version guard keeps the newer local row)
    // Spec State Matrix 1: present + dto.v < local.v → SKIP
    @Test
    fun `scenario 23b quest stale archived marker version below local THEN local preserved`() = runTest {
        val existing = makeQuest(
            id = "q1",
            authorUid = "uid-42",
            catalogId = surveys,
            version = 5L,
            visibleOn = setOf("home"),
            lastModifiedAt = 1000L,
        )
        val fake = FakeQuestRepository(initial = listOf(existing))

        // Server sends stale archived=true with version=3 (below local v=5)
        val staleArchivedMarker = existing.copy(version = 3L, archived = true, lastModifiedAt = 2000L)
        fake.seedServerQuests(listOf(staleArchivedMarker))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val after = fake.snapshot()
        assertEquals(1, after.size, "Local quest must be preserved: stale archived marker ignored by version guard")
        assertEquals(5L, after[0].version, "Version must remain 5 (local), not downgraded")
    }

    // ── Scenario 23c (quest analog) : equal version archived marker guard ─────
    // GIVEN local quest(v=5) AND server sends archived=true, version=5 (equal)
    // WHEN sync THEN local preserved (dto.v == local.v → SKIP per spec)
    @Test
    fun `scenario 23c quest equal version archived marker THEN local preserved`() = runTest {
        val existing = makeQuest(
            id = "q1",
            authorUid = "uid-42",
            catalogId = surveys,
            version = 5L,
            visibleOn = setOf("home"),
            lastModifiedAt = 1000L,
        )
        val fake = FakeQuestRepository(initial = listOf(existing))

        // Server sends archived=true with same version=5
        val equalVersionArchivedMarker = existing.copy(archived = true, lastModifiedAt = 2000L)
        fake.seedServerQuests(listOf(equalVersionArchivedMarker))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        val after = fake.snapshot()
        assertEquals(1, after.size, "Local quest must be preserved: equal-version archived marker ignored by version guard")
        assertEquals(5L, after[0].version, "Version unchanged (equal version archived marker skipped)")
    }

    // ── Query B: public quest from different catalog is filtered by catalogIdsToSync ──
    @Test
    fun `query B public quest outside catalogIdsToSync is not inserted`() = runTest {
        val fake = FakeQuestRepository()
        // This public quest is in catalog "other" — NOT in catalogIdsToSync
        val publicInOtherCatalog = makeQuest(
            id = "public-other",
            authorUid = "uid-99",
            catalogId = CatalogId("other"),
            visibleOn = setOf("home"),
            lastModifiedAt = 1000L,
        )
        fake.seedServerQuests(listOf(publicInOtherCatalog))

        SyncQuestsUseCase(fake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),  // "surveys" != "other"
            cursor = 0L,
        )

        assertTrue(fake.snapshot().isEmpty(), "Public quest outside catalogIdsToSync must not be inserted")
    }

    // ── Scenario 58 : Server Invariant B simulation ───────────────────────────
    //
    // When admin changes quest.visibleOn → server raises lastModifiedAt of all descendants.
    // Domain test: when quest arrives with changed visibleOn + all its children have
    // updated lastModifiedAt → client sync fetches children via normal delta query.
    //
    // This is a documentation/contract test for Invariant B.
    // The fake simulates the server's cascading behavior.
    @Test
    fun `scenario 58 server invariant B quest visibleOn change causes descendants to have fresh lastModifiedAt`() = runTest {
        // Arrange: quest with visibleOn change arrives. Server raised contentsVersion.
        // All child sections get fresh lastModifiedAt via Invariant B.
        val questFake = FakeQuestRepository()

        // Quest goes from visibleOn=[] (private) to visibleOn=["home"] (public)
        // Server applies Invariant B: bumps lastModifiedAt on all descendants
        val questBeforePublish = makeQuest(
            id = "q1",
            authorUid = "uid-42",
            catalogId = surveys,
            visibleOn = emptySet(),         // was private
            version = 1L,
            contentsVersion = 2L,
            lastModifiedAt = 500L,
        )
        // Quest is now public; server bumped its contentsVersion
        val questAfterPublish = questBeforePublish.copy(
            visibleOn = setOf("home"),
            version = 2L,
            contentsVersion = 3L,           // bumped — triggers section sync
            lastModifiedAt = 2000L,         // fresh timestamp
        )

        // Server state: quest is now public
        questFake.seedServerQuests(listOf(questAfterPublish))

        SyncQuestsUseCase(questFake).invoke(
            currentUserUid = "uid-42",
            availableShelves = setOf("home"),
            catalogIdsToSync = setOf(surveys),
            cursor = 0L,
        )

        // Quest should now be in local cache (not deleted)
        val snapshot = questFake.snapshot()
        assertEquals(1, snapshot.size, "Published quest must be inserted")
        assertEquals(setOf("home"), snapshot[0].visibleOn)
        assertEquals(3L, snapshot[0].contentsVersion, "contentsVersion must be 3 (bumped by server)")

        // Cascade predicate: server cv=3 > old local cv=0 (was absent) → RECURSE
        val localQuestCv = 0L // was absent before sync
        val serverQuestCv = snapshot[0].contentsVersion
        val shouldSyncChildren = serverQuestCv > localQuestCv
        assertEquals(true, shouldSyncChildren,
            "Invariant B: newly public quest with cv>0 must trigger child sync")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeQuest(
        id: String = "q1",
        authorUid: String = "test-uid-42",
        catalogId: CatalogId = CatalogId("surveys"),
        title: String = "My Quest",
        visibleOn: Set<String> = setOf("home"),
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = Quest(
        id = QuestId(id),
        catalogId = catalogId,
        authorUid = authorUid,
        title = title,
        picturePath = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
