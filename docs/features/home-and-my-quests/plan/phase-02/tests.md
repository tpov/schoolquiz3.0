---
phase: 02
role: test-dev
---

# Phase-02 Test Tasks

Тесты пишутся параллельно с реализацией. Все тесты — JVM (commonTest) с Fake repositories. Никаких Firebase emulator тестов (04-testing.md §11).

---

## Pattern Invariants

- Все data layer тесты используют Fakes (FakeRemoteDataSource, FakeLocalDataSource / FakeDao)
- Fakes в `commonTest/fake/` директории каждого data модуля
- Fake names: `FakeQuestRemoteDataSource`, `FakeSectionRemoteDataSource`, etc.
- test-dev НЕ модифицирует production code; НЕ изменяет domain Walking Skeleton
- Каждый cursor advancement сценарий проверяет вызов `setCursor` через `FakeSyncStateRepository.setCursorCalls`

---

## 1. CatalogRepositoryImplTest — update for delta-sync

- **Файл:** `shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImplTest.kt`
- **Тип:** JVM test (обновление существующего)

**Новые сценарии (добавить к существующим):**

```
when_cursor_0_then_fetchChangedSince_0_called:
  GIVEN: fakeSyncState.setCursor("catalogs", 0L)
  WHEN: repo.refreshFromRemote()
  THEN: fakeCatalogRemote.lastCursor == 0L
  [COVERS AC#7]

when_3_dtos_returned_then_all_upserted:
  GIVEN: remote returns 3 CatalogDtos(archived=false, version=1)
  WHEN: refreshFromRemote()
  THEN: fakeLocal.upsertedCount == 3
  [COVERS AC#7]

when_dto_archived_true_and_version_newer_then_deleted:
  GIVEN: local has catalog(id="c1", version=2)
  AND: remote returns dto(id="c1", version=3, archived=true)
  WHEN: refreshFromRemote()
  THEN: fakeLocal.deletedIds contains "c1"
  [COVERS AC#8]

when_dto_archived_false_absent_locally_then_inserted:
  GIVEN: local has no catalog with id="c-new"
  AND: remote returns dto(id="c-new", archived=false, version=1)
  WHEN: refreshFromRemote()
  THEN: fakeLocal.upsertedIds contains "c-new"
  [Matrix 1.2]

when_dto_version_equal_then_no_update:
  GIVEN: local has catalog(id="c1", version=5)
  AND: remote returns dto(id="c1", version=5, name="updated")
  WHEN: refreshFromRemote()
  THEN: fakeLocal.findById("c1")?.name == original  [DAO atomic check]
  [Matrix 1.5 — tested at DAO level in phase-01, but RepositoryImpl should not skip calling upsert]

when_success_then_cursor_advanced:
  GIVEN: dtos with lastModifiedAt=[100, 200, 300]
  WHEN: refreshFromRemote()
  THEN: fakeSyncState.getCursor("catalogs") == 300L
  [P4 / AC#41-42]

when_dtos_empty_then_cursor_not_advanced:
  GIVEN: remote returns emptyList
  WHEN: refreshFromRemote()
  THEN: fakeSyncState.getCursor("catalogs") == 0L (unchanged)

when_remote_throws_then_cursor_not_advanced:
  GIVEN: fakeRemote throws IOException
  WHEN: refreshFromRemote()
  THEN: result.isFailure == true AND fakeSyncState.getCursor("catalogs") == 0L
  [AC#54 — cursor not advanced on failure]
```

**Setup:** `FakeCatalogRemoteDataSource` нужен метод `lastCursor: Long`; обновить существующий Fake добавив `var lastCursor = 0L` + запись в `fetchChangedSince`.

---

## 2. QuestRepositoryImplTest (new)

- **Файл:** `shared/feature/quest/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestRepositoryImplTest.kt`
- **Тип:** JVM test (new)

**Fakes needed:**
- `FakeQuestRemoteDataSource` — хранит last call args, configurable return
- `FakeQuestLocalDataSource` — in-memory store + tracks upserts/deletes (см. Signature Card ниже)
- `FakeSyncStateRepository` — создаётся в этой фазе (см. Signature Card ниже); phase-03 ссылается на тот же файл, не дублирует

> VERIFIED: `FakeSyncStateRepository.kt` НЕ существует в Walking Skeleton (только `InMemorySyncStateRepository` — production impl + `InMemorySyncStateRepositoryTest` — contract test). Fake создаётся здесь в phase-02 (нужен QuestRepositoryImplTest) и используется phase-03 без дублирования.

### FakeSyncStateRepository (Signature Card)

- **Файл:** `shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeSyncStateRepository.kt`
- **Тип:** class implementing `SyncStateRepository`
- **Сигнатура:** `class FakeSyncStateRepository : SyncStateRepository`
- **Backing store:** `private val cursors = mutableMapOf<String, Long>()`
- **Методы:**
  - `getCursor(collectionId: String): Long` → `cursors[collectionId] ?: 0L`
  - `setCursor(collectionId: String, value: Long): Unit` → `cursors[collectionId] = value`; appends to `setCursorCalls`
  - `markCascadeInProgress(...)` / `markCascadeCompleted(...)` / `getPendingCascades()` — minimal stubs returning Unit/emptyList (phase-02 не использует; phase-03 расширяет если нужно)
  - `fun resetAll()` — `cursors.clear(); setCursorCalls.clear()`
- **Call tracking:** `val setCursorCalls = mutableListOf<Pair<String, Long>>()`
- **Canonical reference:** `04-testing.md §4 FakeSyncStateRepository`; internal (no api-contract entry)
- **Rationale:** создаётся в phase-02 (нужен для QuestRepositoryImplTest cursor advancement assertions) и переиспользуется phase-03 (CascadingSyncOrchestratorTest) без дублирования

### FakeQuestLocalDataSource (Signature Card)

- **Файл:** `shared/feature/quest/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/fake/FakeQuestLocalDataSource.kt`
- **Тип:** class implementing `QuestLocalDataSource`
- **Сигнатура:** `class FakeQuestLocalDataSource : QuestLocalDataSource`
- **Backing store:** `private val store = mutableMapOf<String, QuestEntity>()` (id → entity)
- **Методы:**
  - `observeMyQuests(authorUid: String, catalogId: String?): Flow<List<QuestEntity>>` → `MutableStateFlow<List<QuestEntity>>` backed by store
  - `upsertByIdIfNewerVersion(entity: QuestEntity): Unit` — stores if `store[entity.id]?.version ?: 0 < entity.version`; increments `upsertCallsFor[entity.id]`
  - `deleteById(id: String): Unit` → `store.remove(id)`; records in `deletedIds`
  - `findById(id: String): QuestEntity?` → `store[id]`
  - `getLocalContentsVersion(id: QuestId): Long?` → `store[id.value]?.contentsVersion`
- **Call tracking:** `val upsertCallsFor = mutableMapOf<String, Int>()`, `val deletedIds = mutableListOf<String>()`
- **Seed helper:** `fun seed(quests: List<QuestEntity>)` — pre-populate store для тестов где `localContentsVersion` важна (например `when_refresh_succeeds_then_returns_changed_quest_ids` нужен local entry с `contentsVersion=1` для сравнения с remote `contentsVersion=3`)
- **Canonical reference:** internal (no api-contract entry)

**Сценарии:**

```
when_currentUserUid_not_null_then_both_queries_called:
  GIVEN: currentUserUid="uid-a", catalogIds={c1}, cursor=0
  WHEN: repo.refreshFromRemote("uid-a", {"home","arena"}, {CatalogId("c1")}, 0L)
  THEN: fakeRemote.fetchOwnChangedCallCount == 1 AND fakeRemote.fetchPublicChangedCallCount == 1
  [AC#9]

when_currentUserUid_null_then_only_query_B_called:
  GIVEN: currentUserUid=null
  WHEN: refreshFromRemote(null, {"home","arena"}, {CatalogId("c1")}, 0L)
  THEN: fakeRemote.fetchOwnChangedCallCount == 0 AND fakeRemote.fetchPublicChangedCallCount == 1
  [ADR-CMP-49 guest mode]

when_same_quest_in_A_and_B_then_exactly_one_upserted:
  GIVEN: fetchOwnChanged returns [dto(id="q1")] AND fetchPublicChanged returns [dto(id="q1")]
  WHEN: refreshFromRemote(...)
  THEN: fakeLocal.upsertCallsFor("q1") == 1  (deduplication)
  [Matrix 1 Quest dedupe]

when_quest_archived_true_then_deleted:
  GIVEN: remote returns dto(id="q1", archived=true)
  WHEN: refreshFromRemote(...)
  THEN: fakeLocal.deletedIds contains "q1"
  [Matrix 1 Quest 1.3]

when_quest_visibleOn_empty_then_deleted:
  GIVEN: remote returns dto(id="q1", visibleOn=[], authorUid="me")
  WHEN: refreshFromRemote(...)
  THEN: fakeLocal.deletedIds contains "q1"
  [EDGE 1.9 / AC#48]

when_refresh_succeeds_then_returns_changed_quest_ids:
  GIVEN: remote returns dto(id="q1", contentsVersion=3) AND local has quest(id="q1", contentsVersion=1)
  WHEN: refreshFromRemote(...)
  THEN: result.getOrNull() contains QuestId("q1")  (for cascade trigger)

when_refresh_fails_then_returns_failure:
  GIVEN: fakeRemote.fetchPublicChanged throws IOException
  WHEN: refreshFromRemote(...)
  THEN: result.isFailure
```

---

## 3. SectionRepositoryImplTest (new)

- **Файл:** `shared/feature/section/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/section/data/SectionRepositoryImplTest.kt`

**Сценарии:**

```
when_section_absent_and_not_archived_then_inserted:
  [Matrix 2.2]

when_section_archived_and_newer_version_then_deleted:
  [Matrix 2.3]

when_section_version_equal_then_skipped:
  [Matrix 2.5]

when_refreshByParents_succeeds_then_returns_changed_section_ids:
  GIVEN: dto with contentsVersion > local.contentsVersion
  THEN: result contains SectionId(dto.id)

when_refreshByParents_fails_then_failure_result:
  GIVEN: fake throws exception
  THEN: result.isFailure
```

**ThemeRepositoryImplTest, LessonRepositoryImplTest** — идентичный pattern.

---

## 4. QuestionRepositoryImplTest (new)

- **Файл:** `shared/feature/question/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/question/data/QuestionRepositoryImplTest.kt`

```
when_question_not_archived_then_upserted:
  [Matrix 2 leaf: insert]

when_question_archived_and_newer_then_deleted:
  [Matrix 2 leaf: EDGE 2.7]

when_refreshByParents_succeeds_then_result_unit:
  THEN: result.isSuccess AND result.getOrNull() == Unit  (leaf, no Set return)

when_refreshByParents_fails_then_failure:
  GIVEN: fake throws
  THEN: result.isFailure
```

---

## 5. QuestDtoMapperTest (new)

- **Файл:** `shared/feature/quest/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/quest/data/QuestDtoMapperTest.kt`

```
toEntity_maps_all_fields:
  GIVEN: QuestDto(id="q1", catalogId="c1", ..., averageRating=2.7, visibleOn=["home","arena"])
  WHEN: toEntity(pictureUrl="https://example.com/pic.jpg")
  THEN: entity.id=="q1", entity.catalogId=="c1", entity.visibleOn==setOf("home","arena"), entity.averageRating≈2.7f

toEntity_double_to_float_precision:
  GIVEN: dto.averageRating=2.700000001  (floating point)
  WHEN: toEntity(pictureUrl=null)
  THEN: entity.averageRating is within 0.01f of 2.7f

toEntity_null_averageRating:
  GIVEN: dto.averageRating=null
  THEN: entity.averageRating==null

toEntity_pictureUrl_injected:
  GIVEN: dto.picturePath="quest-pictures/q1.jpg", pictureUrl="https://resolved.url"
  WHEN: toEntity("https://resolved.url")
  THEN: entity.pictureUrl=="https://resolved.url"
```

---

## 6. Update CatalogFirstFetchIntegrationTest + CatalogWarmCacheIntegrationTest

- **Файл:** `shared/core/catalog/data/src/commonTest/.../CatalogFirstFetchIntegrationTest.kt` (существующий)

**Обновления для совместимости с delta-sync:**
- `FakeCatalogRemoteDataSource` должен реализовать `fetchChangedSince(cursor)` вместо `fetchAll()`
- Добавить `FakeSyncStateRepository` в тестовый setup
- Существующие тесты first-fetch + warm-cache должны оставаться green с новой signature

**Добавить сценарий:**
```
delta_sync_after_first_fetch_reads_only_changed:
  GIVEN: first sync ran with cursor=0 → cursor advanced to 1000L
  WHEN: second sync runs
  THEN: fakeRemote.lastCursor == 1000L (not 0)
  [AC#20]
```

---

## 7. CatalogArchiveIntegrationTest (new — Journey 6)

- **Файл:** `shared/core/catalog/data/src/commonTest/.../CatalogArchiveIntegrationTest.kt`

```
when_catalog_archived_then_removed_from_observeAll:
  GIVEN: local has catalog(id="c1", archived=false)
  AND: remote returns dto(id="c1", archived=true, version=2)
  WHEN: refreshFromRemote() runs
  AND: observeAll().first() collected
  THEN: emits list NOT containing catalog with id="c1"
  [Journey 6 / AC#22 indirect]
```

---

## 8. MyQuestsOfflineEmptyIntegrationTest (new — Journey 3)

- **Файл:** `shared/feature/quest/data/src/commonTest/.../MyQuestsOfflineEmptyIntegrationTest.kt`

```
when_room_empty_then_observeMyQuests_emits_empty_list:
  GIVEN: FakeQuestLocalDataSource with no seeded data
  AND: no network / remote not called
  WHEN: repo.observeMyQuests("uid-a", null).first()
  THEN: emits emptyList() (no crash)
  [Journey 3 / AC#31 offline-first]
```
