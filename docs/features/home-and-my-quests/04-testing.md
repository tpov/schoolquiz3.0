---
date: 2026-04-22
author: architect-component
feature: home-and-my-quests
---

# Test Strategy: home-and-my-quests

---

## 1. Test Tier Overview

| Tier | Framework | Location | Run command |
|------|-----------|----------|------------|
| JVM unit (domain) | kotlin.test + coroutines-test | `shared/*/domain/src/commonTest/` | `./gradlew :shared:feature:quest:domain:jvmTest` |
| JVM integration (data) | kotlin.test + coroutines-test + Fakes | `shared/*/data/src/commonTest/` | `./gradlew :shared:feature:quest:data:jvmTest` |
| JVM component (orchestrator) | kotlin.test + coroutines-test | `shared/core/sync/src/commonTest/` | `./gradlew :shared:core:sync:jvmTest` |
| Android instrumented (DAO) | AndroidJUnit4 + Room in-memory | `shared/core/persistence/src/androidTest/` | `./gradlew :shared:core:persistence:connectedAndroidTest` |
| Android instrumented (migration) | MigrationTestHelper | `shared/core/persistence/src/androidTest/` | same |
| Compose UI (design system) | JUnit4 + BrandComponentsInvariantsTest | `android/core/designsystem/src/test/` | `./gradlew :android:core:designsystem:test` |
| Component presentation (Decompose) | kotlin.test + TestComponentContext | `android/feature/quest/presentation/src/test/` | `./gradlew :android:feature:quest:presentation:test` |

---

## 2. State Matrix → Test Cases Mapping

### Matrix 1 — каждая ячейка = минимум 1 test case

| Matrix Row | Test class | Test name pattern |
|-----------|-----------|-------------------|
| 1.1 absent + delete-marker → SKIP | `CatalogRepositoryImplTest` | `when dto is archived and absent then NOT inserted` |
| 1.2 absent + non-delete → INSERT | `CatalogRepositoryImplTest` | `when dto is new and not archived then inserted` |
| 1.3 present + v > local + delete → DELETE | `CatalogRepositoryImplTest` | `when dto is archived and version newer then local deleted` |
| 1.4 present + v > local + non-delete → UPSERT | `CatalogRepositoryImplTest` | `when dto version newer then upserted` |
| 1.5 present + v == local → SKIP | `CatalogRepositoryImplTest` | `when dto version equal then skipped` |
| 1.6 present + v < local → SKIP | `CatalogRepositoryImplTest` | `when dto version older then skipped` |
| EDGE 1.7 stale tombstone → SKIP | `QuestRepositoryImplTest` | `when archived dto has stale version then NOT deleted` |
| EDGE 1.9 visibleOn=[] + owner=me → DELETE | `QuestRepositoryImplTest` | `when visibleOn empty and authorUid matches then quest deleted` |
| Quest Matrix 1 dedupe | `QuestRepositoryImplTest` | `when same quest in query A and B then Room has exactly 1 row` |

### Matrix 2 — Section/Theme/Lesson/Question

| Matrix Row | Test class | Test name pattern |
|-----------|-----------|-------------------|
| 2.2 absent + archived=false → INSERT | `SectionRepositoryImplTest` | `when section absent and not archived then inserted` |
| 2.3 present + v > local + archived=true → DELETE | `SectionRepositoryImplTest` | `when section archived and newer version then deleted` |
| 2.4 present + v > local + archived=false → UPSERT | `SectionRepositoryImplTest` | `when section version newer then upserted` |
| 2.5 v == local → SKIP | `ThemeRepositoryImplTest` | `when version equal then skipped` |
| 2.6 v < local → SKIP | `LessonRepositoryImplTest` | `when server version stale then skipped` |
| EDGE 2.7 Question delete (no cv) | `QuestionRepositoryImplTest` | `when question archived and newer then deleted` |

### Matrix 3 — Cascade recurse predicate

| Matrix Row | Test class | Test name pattern |
|-----------|-----------|-------------------|
| 3.1 absent + cv > 0 → RECURSE | `CascadeDecisionTest` | `when parent inserted and cv non-zero then recurse` |
| 3.2 absent + cv == 0 → STOP | `CascadeDecisionTest` | `when parent inserted and cv zero then stop` |
| 3.3 upserted + cv > local → RECURSE | `CascadeDecisionTest` | `when cv grew then recurse` |
| 3.4 upserted + cv == local → STOP | `CascadeDecisionTest` | `when cv unchanged then stop` |
| 3.5 skipped (version equal) → STOP | `CascadingSyncOrchestratorTest` | `when version equal then not added to changedParentIds` |
| EDGE 3.6 skipped (version older) → STOP | `CascadingSyncOrchestratorTest` | `when server version stale then stop cascade` |
| EDGE 3.7 changedParentIds empty → STOP | `CascadingSyncOrchestratorTest` | `when no changed parents then next level not fetched` |
| EDGE 3.8 batch > 30 | `CascadingSyncOrchestratorTest` | `when 31 parent ids then batched into two fetches` |

### Matrix 4 — Visibility Filter (availableShelves)

| Matrix Row | Test class | Test name pattern | Phase |
|-----------|-----------|------------------|-------|
| 4.1 baseline MVP `{"home","arena"}` | `CascadingSyncOrchestratorTest` | `when sync runs then availableShelves equals home and arena` | **phase-01** |
| 4.2 + tournament qual | N/A | N/A — future shelf expansion | future |
| 4.3 + finalist qual | N/A | N/A | future |
| 4.4 admin (all 5 shelves) | N/A | N/A | future |
| EDGE 4.5 availableShelves from UserStats.qualification | `CascadingSyncOrchestratorTest` | `when user has baseline qualification then availableShelves is home and arena` | **phase-01** |
| EDGE 4.6 guest (uid=null) → Query A skipped | `CascadingSyncOrchestratorTest` | `when uid is null then refreshFromRemote called with null uid` | **phase-01** |

---

## 3. Domain Test Scenarios Coverage Map

Все 58 Domain Test Scenarios из `0-spec.md:918-1175` → test files:

| Scenario range | Domain area | Test file | Location |
|---------------|-------------|-----------|----------|
| 1-5 | Value classes (Id invariants) | `QuestValueObjectsTest.kt` | `shared/feature/quest/domain/commonTest` |
| 5 (SectionId, etc.) | Value classes | `SectionDomainTest.kt`, `ThemeDomainTest.kt`, etc. | respective domain commonTest |
| 6-9b | Catalog invariants | `CatalogValueObjectsTest.kt` | `shared/core/catalog/domain/commonTest` |
| 10-17 | Quest invariants | `QuestValueObjectsTest.kt` | `shared/feature/quest/domain/commonTest` |
| 18-20 | Section/Theme/Lesson/Question invariants | `SectionDomainTest.kt`, etc. | respective domain commonTest |
| 21-27 | Repository contract tests (via fakes) | `CatalogRepositoryContractTest.kt`, `QuestRepositoryContractTest.kt` | domain commonTest |
| 28-31 | Cascading sync (contentsVersion predicate) | `CascadeDecisionTest.kt` | `shared/feature/quest/domain/commonTest` |
| 32-34 | Use case tests | `QuestUseCaseTest.kt` | `shared/feature/quest/domain/commonTest` |
| 34a-34e | AuthRepository (Walking Skeleton) | `AuthRepositoryContractTest.kt` | `shared/feature/app-shell/domain/commonTest` |
| 35-40 | Star rating logic | `StarRatingTest.kt` | `shared/feature/quest/domain/commonTest` |
| 41a-41e | Destination.OpenQuestCreate navigation | `AppShellTransitionsTest.kt` | `shared/feature/app-shell/domain/commonTest` |
| 41-44 | Section/Theme/Lesson/Question sync edges | `{Entity}RepositoryImplTest.kt` | `shared/feature/*/data/commonTest` |
| 45-50 | Upsert/skip/dedupe | `QuestRepositoryImplTest.kt` + `CascadingSyncOrchestratorTest.kt` | data commonTest + `shared/core/sync/commonTest` |
| 41 (AC), 42 (AC) | SyncStateRepository | `InMemorySyncStateRepositoryTest.kt` | `shared/core/sync/commonTest` |
| 43-44 (AC) | SyncStateRepository cascade tracking | `InMemorySyncStateRepositoryTest.kt` | same |
| 45a-45c | MyQuestsViewModel reactive auth | `DefaultMyQuestsComponentTest.kt` | `android/feature/quest/presentation/test` |
| 46-49 | Guest flow + archived delete | `DefaultMyQuestsComponentTest.kt` | same |
| 54-57 | Sync retry semantics | `CascadingSyncOrchestratorTest.kt` | `shared/core/sync/commonTest` |
| 58 | Server Invariant B (integration) | `CascadeSyncIntegrationTest.kt` (new) | `shared/core/sync/commonTest` |

---

## 4. Fake Blueprints — Standard Across All 6 Repositories

### FakeQuestRepository (already in Walking Skeleton: `shared/feature/quest/domain/src/commonTest/fake/`)

```kotlin
class FakeQuestRepository : QuestRepository { // Canonical signature: see 06-api-contract.md:181-186
    private val _store = MutableStateFlow<Map<QuestId, Quest>>(emptyMap())

    fun seed(vararg quests: Quest) { _store.value = quests.associateBy { it.id } }
    fun snapshot(): List<Quest> = _store.value.values.toList()
    var refreshCalls = 0
    var lastRefreshCatalogIds: Set<CatalogId>? = null
    var lastRefreshUid: String? = null
    private var nextRefreshResult: Result<Set<QuestId>> = Result.success(emptySet())
    fun setNextRefreshFailure(t: Throwable) { nextRefreshResult = Result.failure(t) }
    fun setNextRefreshChanged(ids: Set<QuestId>) { nextRefreshResult = Result.success(ids) }

    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> =
        _store.map { map ->
            map.values.filter {
                it.authorUid == authorUid && !it.archived &&
                        (catalogId == null || it.catalogId == catalogId)
            }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        _store.map { it.values.filter { q -> shelf in q.visibleOn && !q.archived } }

    override suspend fun getById(id: QuestId): Quest? = _store.value[id]

    // Canonical: currentUserUid is String? (null = guest, skip Query A)
    // Returns Result<Set<QuestId>> — changed quest IDs for cascade trigger
    override suspend fun refreshFromRemote(
        currentUserUid: String?,            // nullable — see 06-api-contract.md:182
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> {
        refreshCalls++
        lastRefreshCatalogIds = catalogIdsToSync
        lastRefreshUid = currentUserUid
        return nextRefreshResult
    }
}
```

### FakeSectionRepository (новый — `shared/feature/section/domain/src/commonTest/fake/`)

```kotlin
class FakeSectionRepository : SectionRepository { // Canonical signature: see 06-api-contract.md:190-208
    private val _store = MutableStateFlow<Map<SectionId, Section>>(emptyMap())

    fun seed(vararg sections: Section) { _store.value = sections.associateBy { it.id } }
    var refreshCalls = 0
    var lastRefreshQuestIds: Set<QuestId>? = null
    private var nextRefreshResult: Result<Set<SectionId>> = Result.success(emptySet())
    fun setNextRefreshFailure(t: Throwable) { nextRefreshResult = Result.failure(t) }
    fun setNextRefreshChanged(ids: Set<SectionId>) { nextRefreshResult = Result.success(ids) }

    override fun observeByQuest(questId: QuestId): Flow<List<Section>> =
        _store.map { map ->
            map.values.filter { it.questId == questId && !it.archived }
                .sortedBy { it.order }
        }

    override suspend fun getById(id: SectionId): Section? = _store.value[id]

    override suspend fun getLocalContentsVersion(id: SectionId): Long? = _store.value[id]?.contentsVersion

    // Returns Result<Set<SectionId>> — changed section IDs for cascade trigger to themes
    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> {
        refreshCalls++
        lastRefreshQuestIds = questIds
        return nextRefreshResult
    }
}
```

**FakeThemeRepository, FakeLessonRepository** — идентичный pattern: `refreshByParents(parentIds, cursor): Result<Set<ThemeId>>` / `Result<Set<LessonId>>`.

**FakeQuestionRepository** — leaf: `refreshByParents(lessonIds, cursor): Result<Unit>` (no cascade further). See `06-api-contract.md:214-227`.

### FakeSyncStateRepository (новый — `shared/core/sync/src/commonTest/fake/`)

```kotlin
class FakeSyncStateRepository : SyncStateRepository {
    private val cursors = mutableMapOf<String, Long>()
    private val pending = mutableMapOf<String, PendingCascade>()

    var setCursorCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun getCursor(collectionId: String): Long = cursors[collectionId] ?: 0L
    override suspend fun setCursor(collectionId: String, value: Long) {
        cursors[collectionId] = value
        setCursorCalls.add(collectionId to value)
    }
    override suspend fun markCascadeInProgress(parentId: String, parentType: String, pendingChildIds: Set<String>) {
        pending["$parentType:$parentId"] = PendingCascade(parentId, parentType, pendingChildIds)
    }
    override suspend fun markCascadeCompleted(parentId: String, parentType: String) {
        pending.remove("$parentType:$parentId")
    }
    override suspend fun getPendingCascades(): List<PendingCascade> = pending.values.toList()
}
```

### FakeAuthRepository (Walking Skeleton — `shared/feature/app-shell/domain/src/commonTest/fake/`)

```kotlin
class FakeAuthRepository(initialUid: String? = null) : AuthRepository {
    private val _uid = MutableStateFlow(initialUid)

    fun signIn(uid: String) {
        require(uid.isNotBlank()) { "uid must not be blank" }
        _uid.value = uid
    }
    fun signOut() { _uid.value = null }

    override suspend fun currentUid(): String? = _uid.value
    override fun observeUid(): Flow<String?> = _uid.asStateFlow()
}
```

---

## 5. CascadingSyncOrchestrator Integration Tests

**Location**: `shared/core/sync/src/commonTest/kotlin/.../CascadingSyncOrchestratorTest.kt`

**Setup**:
```kotlin
class CascadingSyncOrchestratorTest {
    private val fakeCatalogRepo = FakeCatalogRepository()
    private val fakeQuestRepo = FakeQuestRepository()
    private val fakeSectionRepo = FakeSectionRepository()
    private val fakeThemeRepo = FakeThemeRepository()
    private val fakeLessonRepo = FakeLessonRepository()
    private val fakeQuestionRepo = FakeQuestionRepository()
    private val fakeSyncState = FakeSyncStateRepository()
    private val fakeAuth = FakeAuthRepository(initialUid = "test-uid")

    private val fakeUserStats = FakeUserStatsRepository()

    private val orchestrator = CascadingSyncOrchestrator(
        catalogRepo = fakeCatalogRepo, questRepo = fakeQuestRepo,
        sectionRepo = fakeSectionRepo, themeRepo = fakeThemeRepo,
        lessonRepo = fakeLessonRepo, questionRepo = fakeQuestionRepo,
        syncStateRepo = fakeSyncState, authRepo = fakeAuth,
        userStatsRepo = fakeUserStats,   // 9th param — see 06-api-contract.md:341
    )
}
```

**Key tests**:

```kotlin
@Test fun `when catalog cv unchanged then quests not fetched`() = runTest {
    // AC#10, scenario 28
    fakeCatalogRepo.seedWithLocalCv(catalogId = "c1", localCv = 3L)
    fakeCatalogRepo.setNextRemoteCv(catalogId = "c1", remoteCv = 3L)  // unchanged
    orchestrator.sync()
    assertThat(fakeQuestRepo.refreshCalls).isEqualTo(0)
}

@Test fun `when catalog cv grew then quests fetched`() = runTest {
    // AC#10, scenario 29
    fakeCatalogRepo.seedWithLocalCv("c1", 3L)
    fakeCatalogRepo.setNextRemoteCv("c1", 5L)  // grew
    orchestrator.sync()
    assertThat(fakeQuestRepo.refreshCalls).isEqualTo(1)
    assertThat(fakeQuestRepo.lastRefreshCatalogIds).containsExactly(CatalogId("c1"))
}

@Test fun `when step 2 fails then questsCursor not advanced`() = runTest {
    // AC#54
    fakeSyncState.setCursor("catalogs", 1000L)
    fakeCatalogRepo.setNextRemoteChangedWithCvGrow()  // triggers quest step
    fakeQuestRepo.setNextRefreshFailure(IOException("timeout"))
    val result = orchestrator.sync()
    assertThat(result.isFailure).isTrue()
    assertThat(fakeSyncState.getCursor("quests")).isEqualTo(0L)  // not advanced
}

@Test fun `when parent ids exceed 30 then batched`() = runTest {
    // AC#50 / scenario 50
    val manyQuestIds = (1..35).map { QuestId("q$it") }.toSet()
    fakeSectionRepo.captureRefreshBatches = true
    // trigger section sync with 35 questIds
    // verify at least 2 refreshByParents calls (batches of ≤30)
    ...
}

@Test fun `process death then full resync is idempotent`() = runTest {
    // AC#56
    // Run sync once → Room has data
    orchestrator.sync()
    val firstState = fakeQuestRepo.snapshot()
    // Simulate process death: reset all cursors
    fakeSyncState.resetAll()
    // Run sync again → same data
    orchestrator.sync()
    assertThat(fakeQuestRepo.snapshot()).containsExactlyElementsIn(firstState)
}
```

---

## 6. DAO Boundary Tests (instrumented)

**Location**: `shared/core/persistence/src/androidTest/kotlin/.../QuestDaoBoundaryTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class QuestDaoBoundaryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: QuestDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.questDao()
    }

    @Test fun observeMyQuests_excludes_archived() = runTest {
        dao.upsertByIdIfNewerVersion("q1", ..., archived = false, ...)
        dao.upsertByIdIfNewerVersion("q2", ..., archived = true, ...)
        val quests = dao.observeMyQuests("uid-a").first()
        assertThat(quests.map { it.id }).containsExactly("q1")  // AC#14
    }

    @Test fun observeMyQuestsInCatalog_filters_by_catalogId() = runTest {
        // AC#15
        ...
    }

    @Test fun upsertByIdIfNewerVersion_skips_on_equal_version() = runTest {
        // AC#13
        dao.upsertByIdIfNewerVersion("q1", ..., version = 5L, title = "old", ...)
        dao.upsertByIdIfNewerVersion("q1", ..., version = 5L, title = "new", ...)  // same version
        val entity = dao.findById("q1")
        assertThat(entity?.title).isEqualTo("old")  // skipped
    }

    @Test fun upsertByIdIfNewerVersion_updates_on_higher_version() = runTest {
        dao.upsertByIdIfNewerVersion("q1", ..., version = 3L, title = "old", ...)
        dao.upsertByIdIfNewerVersion("q1", ..., version = 5L, title = "new", ...)
        assertThat(dao.findById("q1")?.title).isEqualTo("new")
    }
}
```

---

## 7. AppDatabase Schema Validation Test

**Note**: Since phase-01 uses `fallbackToDestructiveMigration()`, there is no v1→v2 incremental migration path to test. This test validates the v2 schema structure, NOT a migration path.

**Location**: `shared/core/persistence/src/androidTest/kotlin/.../AppDatabaseSchemaValidationTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaValidationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun validateSchema_v2_tables_and_columns() {
        // Create DB fresh at v2 (fallbackToDestructiveMigration — no incremental path)
        val db = helper.createDatabase(TEST_DB_NAME, 2)
        // Verify all 7 tables exist
        db.execSQL("SELECT * FROM quests LIMIT 1")
        db.execSQL("SELECT * FROM sections LIMIT 1")
        db.execSQL("SELECT * FROM themes LIMIT 1")
        db.execSQL("SELECT * FROM lessons LIMIT 1")
        db.execSQL("SELECT * FROM questions LIMIT 1")
        // Verify CatalogEntity new columns present
        db.execSQL("SELECT version, contentsVersion, lastModifiedAt, archived FROM catalogs LIMIT 1")
        db.close()
    }

    @Test fun destructive_recreate_when_version_bumped() {
        // Simulate upgrade: create v1 DB, then open as v2 — fallbackToDestructiveMigration should recreate
        val v1Db = helper.createDatabase(TEST_DB_NAME, 1)
        v1Db.close()
        // Open at v2 — Room recreates tables (destructive)
        val v2Db = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true)
        v2Db.close()
    }
}
```

---

## 8. Presentation Component Tests

**Location**: `android/feature/quest/presentation/src/test/kotlin/.../DefaultMyQuestsComponentTest.kt`

```kotlin
class DefaultMyQuestsComponentTest {

    private val fakeAuth = FakeAuthRepository()
    private val fakeQuestRepo = FakeQuestRepository()
    private val fakeCatalogRepo = FakeCatalogRepository()

    @Test fun `when guest then state is empty and isGuest=true`() = runTest {
        // AC#45, scenario 45a
        val component = buildComponent(fakeAuth)
        val states = mutableListOf<MyQuestsUiState>()
        val job = launch { component.state.collect { states.add(it) } }
        advanceUntilIdle()
        assertThat(states.last().isGuest).isTrue()
        assertThat(states.last().quests).isEmpty()
        assertThat(fakeQuestRepo.refreshCalls).isEqualTo(0)
        job.cancel()
    }

    @Test fun `mid-session login switches to quest list`() = runTest {
        // AC#45 scenario 45b
        val component = buildComponent(fakeAuth)
        fakeQuestRepo.seed(Quest(id = QuestId("q1"), authorUid = "user-A", ...))
        fakeAuth.signIn("user-A")
        advanceUntilIdle()
        assertThat(component.state.value.isGuest).isFalse()
        assertThat(component.state.value.quests).hasSize(1)
    }

    @Test fun `sign out returns to guest state and cancels quest flow`() = runTest {
        // AC#45 scenario 45c
        fakeAuth = FakeAuthRepository("user-A")
        fakeQuestRepo.seed(Quest(id = QuestId("q1"), authorUid = "user-A", ...))
        val component = buildComponent(fakeAuth)
        advanceUntilIdle()
        fakeAuth.signOut()
        advanceUntilIdle()
        assertThat(component.state.value.isGuest).isTrue()
        assertThat(component.state.value.quests).isEmpty()
    }

    @Test fun `onCatalogSelected filters quests`() = runTest {
        // Journey 7
        fakeAuth = FakeAuthRepository("user-A")
        fakeQuestRepo.seed(
            Quest(id = QuestId("q1"), authorUid = "user-A", catalogId = CatalogId("surveys"), ...),
            Quest(id = QuestId("q2"), authorUid = "user-A", catalogId = CatalogId("math"), ...),
        )
        val component = buildComponent(fakeAuth)
        component.onCatalogSelected(CatalogId("surveys"))
        advanceUntilIdle()
        assertThat(component.state.value.quests.map { it.id.value }).containsExactly("q1")
    }
}
```

---

## 9. Journey Coverage Map (11 Primary User Journeys)

> Source: `0-spec.md:642-830`. All 11 journeys from spec. Phase-01 automatable journeys get named integration/component tests; out-of-scope are marked N/A.

| # | Journey name | Test file | Phase-01 status |
|---|-------------|-----------|-----------------|
| 1 | Cold start с сетью — full cascade pull | `CatalogFirstFetchIntegrationTest.kt` (EXISTING) | ✅ in scope |
| 2 | Warm cache — instant render from Room | `CatalogWarmCacheIntegrationTest.kt` (EXISTING) | ✅ in scope |
| 3 | Offline-first — no network, cached data | `MyQuestsOfflineEmptyIntegrationTest.kt` (NEW) | ✅ in scope |
| 4 | Delta sync: catalog updated | `CatalogFirstFetchIntegrationTest.kt` (EXISTING) | ✅ in scope |
| 5 | Deep cascade: question added | `CatalogFirstFetchIntegrationTest.kt` (EXISTING) | ✅ in scope |
| 6 | Archival: catalog removed | `CatalogArchiveIntegrationTest.kt` (NEW) | ✅ in scope |
| 7 | My Quests: filter by catalog | `DefaultMyQuestsComponentTest.kt` (EXISTING) | ✅ in scope |
| 8 | My Quests: FAB → create quest | `DefaultMyQuestsComponentTest.kt` (EXISTING) | ✅ in scope |
| 9 | Guest mode — no sign-in | `OfflineEmptyIntegrationTest.kt` (NEW) | ✅ in scope |
| 10 | Partial failure / retry semantics | `PartialFailRetryTest.kt` (NEW) | ✅ in scope |
| 11 | Manual sync (dev button) | Manual smoke | Manual only |

---

## 10. BrandComponentsInvariantsTest Coverage

**Existing test**: `android/core/designsystem/src/test/.../BrandComponentsInvariantsTest.kt:24-65`

Requirements for **QuestCard.kt** and **StarRating.kt**:

| Requirement | Check | In file |
|------------|-------|---------|
| `@Preview` присутствует | Test `:54-65` scans `components/` walkTopDown | `QuestCard.kt` + `StarRating.kt` |
| No `Color(0xFF...)` hardcoded | Test `:24-35` | Use `MaterialTheme.colorScheme.primary` instead |
| No hardcoded `dp` pixel values | Convention check | Use `MaterialTheme.spacing` or design tokens |

**Required Preview composables**:

```kotlin
// QuestCard.kt — минимум 4 @Preview
// Canonical QuestDisplayItem: see 06-api-contract.md:438 (id, title, pictureUrl, averageRating, averageRatingCount)
@Preview @Composable fun QuestCardEmptyPreview() = SchoolQuizTheme { QuestCard(QuestDisplayItem(id=QuestId("1"), title="Quest", pictureUrl=null, averageRating=null, averageRatingCount=0), onClick = {}) }
@Preview @Composable fun QuestCardRatedPreview() = SchoolQuizTheme { QuestCard(QuestDisplayItem(id=QuestId("1"), title="Quest", pictureUrl="https://example.com/pic.jpg", averageRating=2.7f, averageRatingCount=42), onClick = {}) }
@Preview @Composable fun QuestCardUnratedPreview() = SchoolQuizTheme { QuestCard(QuestDisplayItem(id=QuestId("1"), title="Quest", pictureUrl=null, averageRating=0f, averageRatingCount=0), onClick = {}) }
@Preview @Composable fun QuestCardLongTitlePreview() = SchoolQuizTheme { QuestCard(QuestDisplayItem(id=QuestId("1"), title="Очень длинное название квеста которое не помещается", pictureUrl=null, averageRating=1.5f, averageRatingCount=7), onClick = {}) }

// StarRating.kt — минимум 6 @Preview
@Preview @Composable fun StarRating0Preview() = SchoolQuizTheme { StarRating(rating = 0f) }
@Preview @Composable fun StarRatingHalfPreview() = SchoolQuizTheme { StarRating(rating = 0.5f) }
@Preview @Composable fun StarRating15Preview() = SchoolQuizTheme { StarRating(rating = 1.5f) }
@Preview @Composable fun StarRating27Preview() = SchoolQuizTheme { StarRating(rating = 2.7f) }
@Preview @Composable fun StarRating30Preview() = SchoolQuizTheme { StarRating(rating = 3.0f) }
@Preview @Composable fun StarRatingNullPreview() = SchoolQuizTheme { StarRating(rating = null) }
```

---

## 10. AC Coverage Map — 58 Acceptance Criteria

| AC # | Description | Test tier | Test file |
|------|-------------|-----------|-----------|
| 1 | Catalog.kt has version/cv/lastModifiedAt/archived | Static inspect | Compile check |
| 2 | Quest.kt has all domain contract fields | Static inspect | Compile check |
| 3 | Section/Theme/Lesson/Question entities correct | Static inspect | Compile check |
| 4 | All 58 domain test scenarios green | JVM | commonTest per domain |
| 5 | Repository interfaces have observeByParent, refreshByParents | Static inspect | Compile check |
| 6 | Fakes for 6 repos in commonTest/fake/ | Static inspect | Exists in Walking Skeleton |
| 7 | refreshFromRemote with cursor=0 upserts all | JVM | `CatalogRepositoryImplTest` |
| 8 | refreshFromRemote archives delete local | JVM | `CatalogRepositoryImplTest` |
| 9 | QuestRepositoryImpl makes 2 parallel queries | JVM | `QuestRepositoryImplTest` |
| 10 | Catalog cv unchanged → quests skipped | JVM | `CascadingSyncOrchestratorTest` |
| 11 | Quest cv grew → sections pulled | JVM | `CascadingSyncOrchestratorTest` |
| 12 | AppDatabase schema v2 correct | Android instrumented | `AppDatabaseSchemaValidationTest` |
| 13 | upsertByIdIfNewerVersion skips on equal | Android instrumented | `QuestDaoBoundaryTest` |
| 14 | observeMyQuests(authorUid, null) correct filter | Android instrumented | `QuestDaoBoundaryTest` |
| 15 | observeMyQuests(authorUid, catalogId) filter | Android instrumented | `QuestDaoBoundaryTest` |
| 16 | SyncWorker runs cascading steps | Android instrumented | SyncWorker integration |
| 17 | SyncWorker network fail → retry | JVM | `CascadingSyncOrchestratorTest` |
| 18 | SyncNow dev-button works | Manual | dev mode smoke test |
| 19 | First sync all data in Room | JVM | `CascadeSyncIntegrationTest` |
| 20 | Delta sync reads only changed | JVM | `CascadeSyncIntegrationTest` |
| 21-30 | UI acceptance criteria | Compose test / Manual | `MyQuestsScreenTest`, `HomeQuestsScreenTest` |
| 31-34 | Integration AC | JVM | `CascadeSyncIntegrationTest` |
| 35-40 | Firebase rules | Manual / Firebase emulator | `FirebaseRulesTest` (optional) |
| 41-44 | SyncStateRepository | JVM | `InMemorySyncStateRepositoryTest` |
| 45-49 | Guest + archived delete | JVM + Compose | `DefaultMyQuestsComponentTest` |
| 50-53 | Nested security rules | Manual / Firebase emulator | — |
| 54-57 | Sync retry semantics | JVM | `CascadingSyncOrchestratorTest` |
| 58 | Server Invariant B integration | JVM | `CascadeSyncIntegrationTest` |

---

## 11. Firebase Emulator vs FakeFirestore

**Decision**: Phase-01 использует **Fake repositories** (JVM), не Firebase emulator.

| Option | JVM test | CI speed | Offline | Complexity |
|--------|---------|---------|---------|------------|
| Fake repositories (chosen) | ✅ | Fast | ✅ | Low |
| Firebase emulator | ❌ (Android only) | Slow (emulator startup) | ❌ | High |
| Mock Firestore | ❌ fragile | Medium | ✅ | Medium |

Firebase rules тесты (AC#35-40, #50-53) — **manual verification** или опциональный Firebase emulator test suite (вне scope phase-01).

---

## 12. Per-Module Test Structure

```
shared/core/catalog/domain/src/commonTest/
  └── CatalogValueObjectsTest.kt     — scenarios 6-9b
  └── CatalogRepositoryContractTest.kt — scenarios 21-23

shared/feature/quest/domain/src/commonTest/
  └── QuestValueObjectsTest.kt       — scenarios 1-5, 10-17
  └── QuestUseCaseTest.kt            — scenarios 32-34
  └── CascadeDecisionTest.kt         — scenarios 28-31, 3.1-3.6
  └── StarRatingTest.kt              — scenarios 35-40
  └── fake/FakeQuestRepository.kt    — shared test double

shared/feature/quest/data/src/commonTest/
  └── QuestRepositoryImplTest.kt     — data layer unit tests
  └── QuestDtoMapperTest.kt          — mapper round-trip

shared/core/sync/src/commonTest/
  └── InMemorySyncStateRepositoryTest.kt — AC#41-44
  └── CascadingSyncOrchestratorTest.kt   — AC#10-11, 54-57, scenarios 28-31
  └── CascadeSyncIntegrationTest.kt      — AC#19-20, 58
  └── fake/FakeSyncStateRepository.kt    — shared test double

shared/core/persistence/src/androidTest/
  └── AppDatabaseSchemaValidationTest.kt       — AC#12
  └── QuestDaoBoundaryTest.kt           — AC#13-15
  └── SectionDaoBoundaryTest.kt         — (similar)

android/feature/quest/presentation/src/test/
  └── DefaultMyQuestsComponentTest.kt   — AC#45-49
  └── DefaultHomeQuestsComponentTest.kt — AC#21-22

android/core/designsystem/src/test/
  └── BrandComponentsInvariantsTest.kt  — existing; QuestCard+StarRating must pass
```

---

## Open Questions

- **OQ-TEST-1**: `TestComponentContext` для Decompose Component — нужна зависимость `decompose-testutils` в `android/feature/quest/presentation/build.gradle.kts`. REQUIRES: `backend-dev` добавит dependency.
- **OQ-TEST-2**: `QuestDaoBoundaryTest` требует `Room.inMemoryDatabaseBuilder` в `androidTest`. Верификация что `kspAndroid` генерирует `AppDatabase_Impl` корректно — REQUIRES: build pass before writing tests.
