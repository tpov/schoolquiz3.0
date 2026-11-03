---
phase: 05
role: test-dev
---

# Phase-05 Test Tasks

Тесты для presentation layer: `DefaultMyQuestsComponent`, `DefaultHomeQuestsComponent`, `PartialFailRetryTest` (Journey 10), `OfflineEmptyIntegrationTest` (Journey 9). Тесты пишутся ПАРАЛЛЕЛЬНО с production code.

---

## Pattern Invariants

- Все тесты в этой фазе — JVM тесты (`src/test/kotlin/...`), НЕ instrumented
- `FakeQuestRepository` и `FakeAuthRepository` — из Walking Skeleton fakes (проверить наличие в `shared/feature/quest/domain/src/commonTest/` или `shared/feature/app-shell/data/src/commonTest/`)
- `TestComponentContext` — создаётся через `DefaultComponentContext(LifecycleRegistry())` (см. OQ-TEST-1 в backend.md)
- Тесты НЕ должны зависеть от Koin — только конструктор injection с fakes
- `runTest` + `UnconfinedTestDispatcher` для синхронного Flow тестирования
- test-dev НЕ модифицирует production code — только добавляет тест файлы

---

## Fakes Blueprint

### FakeQuestRepository

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../fake/FakeQuestRepository.kt`
- **Тип:** class implementing `QuestRepository`
- **Сигнатура:** `class FakeQuestRepository : QuestRepository`
- **Backing store:** `private val store = MutableStateFlow<List<Quest>>(emptyList())`
- **Методы:**
  - `observeMyQuests(uid: String, catalogId: CatalogId?): Flow<List<Quest>>` → `store.map { list -> list.filter { q -> q.authorUid == uid && (catalogId == null || q.catalogId == catalogId) } }`
  - `fun emit(quests: List<Quest>)` — test helper для обновления store
  - `refreshFromRemote(currentUserUid: String?, availableShelves: Set<String>, catalogIdsToSync: Set<CatalogId>, cursor: Long): Result<Set<QuestId>>` → `Result.success(emptySet())` by default; override via `var nextRefreshResult: Result<Set<QuestId>>` flag (canonical signature per `06-api-contract.md §2.2 (181-186)`)
- **Call tracking:** `var observeMyQuestsCallCount = 0`, `var lastObservedUid: String? = null`, `var refreshFromRemoteCallCount = 0`

### FakeCatalogRepository (reuse from phase-03 if already exists)

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../fake/FakeCatalogRepository.kt`
- **Тип:** class implementing `CatalogRepository`
- **Сигнатура:** `class FakeCatalogRepository : CatalogRepository`
- **Backing store:** `private val store = MutableStateFlow<List<Catalog>>(emptyList())`
- **Методы:**
  - `observeAll(): Flow<List<Catalog>>` → `store.asStateFlow()`
  - `fun emit(catalogs: List<Catalog>)` — test helper

### FakeAuthRepository (reuse from phase-04 Walking Skeleton)

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/.../fake/FakeAuthRepository.kt`
- **Тип:** class implementing `AuthRepository`
- **Сигнатура:** `class FakeAuthRepository(initialUid: String? = null) : AuthRepository`
- **Backing store:** `private val uidFlow = MutableStateFlow<String?>(initialUid)`
- **Методы:**
  - `observeUid(): Flow<String?>` → `uidFlow.asStateFlow()`
  - `fun setUid(uid: String?)` — test helper

---

## 1. DefaultMyQuestsComponentTest

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponentTest.kt`

### Pattern Invariants для этого файла

- `flatMapLatest` chain начинается в `init {}` блоке, scope живёт пока `lifecycle` жив
- Тесты используют `LifecycleRegistry().apply { onCreate(); onStart() }` для управления lifecycle scope
- `testScheduler` + `UnconfinedTestDispatcher` для немедленного collect без `advanceUntilIdle()`

### Сценарии

```
scenario_45a_when_guest_then_state_empty_and_isGuest_true:
  GIVEN: FakeAuthRepository(initialUid = null)
  AND: component constructed
  WHEN: component.state collected
  THEN: state.quests.isEmpty() == true
  AND: state.isGuest == true
  AND: FakeQuestRepository.observeMyQuestsCallCount == 0
  [AC#45, Journey 7 — guest path; Pattern Invariant: если uid==null → no DB query]

scenario_45b_mid_session_login_switches_to_quest_list:
  GIVEN: FakeAuthRepository(initialUid = null)
  AND: component constructed (state.isGuest == true)
  AND: FakeQuestRepository.store = [quest1, quest2] for uid="user1"
  WHEN: fakeAuthRepo.setUid("user1")
  THEN: state.quests == [quest1, quest2] (via flatMapLatest)
  AND: state.isGuest == false
  [AC#45, scenario 45b — reactive auth switch]

scenario_45c_sign_out_returns_to_guest_state:
  GIVEN: FakeAuthRepository(initialUid = "user1")
  AND: component constructed, state.quests = [quest1]
  WHEN: fakeAuthRepo.setUid(null)
  THEN: state.quests.isEmpty() == true
  AND: state.isGuest == true
  [AC#45, scenario 45c — sign-out clears list]

scenario_catalog_filter_updates_state:
  GIVEN: FakeAuthRepository(initialUid = "user1")
  AND: FakeQuestRepository.store = [quest_cat1, quest_cat2] (different catalogIds)
  AND: component constructed (state.quests = [quest_cat1, quest_cat2])
  WHEN: component.onCatalogSelected(catalogId1)
  THEN: state.quests == [quest_cat1] only
  [Journey 7 / AC#25 — catalog spinner filter]

scenario_onCreateQuestClick_triggers_OpenQuestCreate:
  GIVEN: component constructed with mock INavigator
  WHEN: component.onCreateQuestClick()
  THEN: navigator.goTo(Destination.OpenQuestCreate) called once
  [Journey 8 / AC#29]

scenario_archived_quest_not_in_list:
  GIVEN: FakeAuthRepository(initialUid = "user1")
  AND: FakeQuestRepository.store = [active_quest, archived_quest(archived=true)]
  WHEN: state collected
  THEN: state.quests contains only active_quest
  [AC#47 — archived quests excluded]

scenario_selected_catalog_reset_on_sign_out:
  GIVEN: FakeAuthRepository(initialUid = "user1")
  AND: component.onCatalogSelected(catalogId1) called
  WHEN: fakeAuthRepo.setUid(null) (sign out)
  THEN: state.selectedCatalogId == null (reset)
  AND: state.isGuest == true
  [stateful field reset invariant — overview.md AC requirement]
```

---

## 2. DefaultHomeQuestsComponentTest

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponentTest.kt`

### Сценарии

```
scenario_observeCatalogs_emits_then_state_updated:
  GIVEN: FakeCatalogRepository with store=[catalog1, catalog2]
  AND: component constructed
  WHEN: state collected
  THEN: state.catalogs == [catalog1, catalog2]
  [AC#21 — HomeQuestsScreen shows catalogs via component state]

scenario_archived_catalog_not_in_state:
  GIVEN: FakeCatalogRepository with store=[active_catalog, archived_catalog(archived=true)]
  AND: component constructed
  WHEN: state collected
  THEN: state.catalogs contains only active_catalog
  AND: archived_catalog NOT in state.catalogs
  [AC#22 — archived catalogs excluded at DAO level; UI state reflects that]

scenario_late_catalog_emission_updates_state:
  GIVEN: FakeCatalogRepository with empty store
  AND: component constructed (state.catalogs.isEmpty() == true)
  WHEN: fakeCatalogRepo.emit([catalog1])
  THEN: state.catalogs == [catalog1]
  [reactive update — catalog added after component creation]

scenario_empty_catalog_list_shows_empty_state:
  GIVEN: FakeCatalogRepository with empty store
  AND: component constructed
  WHEN: state collected
  THEN: state.catalogs.isEmpty() == true
  AND: state.isLoading == false (not stuck in loading)
  [AC#24 equivalent for HomeQuests — no stuck loading on empty data]
```

---

## 3. QuestToDisplayItemMapperTest

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/mapper/QuestToDisplayItemTest.kt`

### Сценарии

```
scenario_quest_with_rating_maps_correctly:
  GIVEN: Quest(id=QuestId("q1"), title="Test Quest", picturePath=null, averageRating=2.7f, ratingCount=5)
  WHEN: quest.toDisplayItem()
  THEN: result.averageRating == 2.7f
  AND: result.averageRatingCount == 5
  AND: result.pictureUrl == null
  AND: result.title == "Test Quest"
  [AC#26]

scenario_quest_without_rating_maps_null:
  GIVEN: Quest(averageRating=null, ratingCount=0)
  WHEN: quest.toDisplayItem()
  THEN: result.averageRating == null
  [AC#27]

scenario_quest_with_picture_maps_url:
  GIVEN: Quest(pictureUrl="https://example.com/pic.png", picturePath="path/pic.png")
  WHEN: quest.toDisplayItem()
  THEN: result.pictureUrl == "https://example.com/pic.png" (remote URL preferred)
  [AC#28 — picturePath != null but remote URL used for Coil]
```

---

## 4. PartialFailRetryTest (Journey 10)

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/PartialFailRetryTest.kt`

**Контекст:** Journey 10 = "Retry after partial sync fail" — offline-first: квесты из кэша остаются видимыми при сетевом сбое. Тесты проверяют реактивное поведение через `FakeQuestRepository.store` (Flow), а не через явный refresh trigger.

> **Важно:** `MyQuestsComponent` (per `06-api-contract.md §6.1:402-406`) содержит только `state`, `onCatalogSelected(id: CatalogId?)`, `onCreateQuestClick()`. Метода `onRefresh()` НЕТ. Тесты НЕ вызывают несуществующие методы. Если pull-to-refresh будет добавлен в spec — это отдельная фича с изменением api-contract.

> **Примечание для test-dev:** Journey 10 тестируется через изменение `FakeQuestRepository.store` — `DefaultMyQuestsComponent` подписан на `observeMyQuests()` через `flatMapLatest`; изменение store автоматически обновляет `state`. «Сетевой сбой» в контексте компонента означает: store не меняется (нет новых данных), а компонент остаётся в стабильном состоянии.

### Сценарии

```
scenario_when_store_empty_then_state_empty_not_loading:
  GIVEN: FakeQuestRepository.store = [] (empty — simulates offline + empty cache)
  AND: FakeAuthRepository(initialUid = "user1")
  AND: component constructed
  WHEN: state collected
  THEN: state.quests.isEmpty() == true
  AND: state.isLoading == false  (not stuck in loading spinner)
  [Journey 10 basis — no spinner on empty store]

scenario_when_store_has_data_then_data_visible_before_any_sync:
  GIVEN: FakeQuestRepository.store = [quest1] (cached data from Room)
  AND: FakeAuthRepository(initialUid = "user1")
  AND: component constructed
  WHEN: state collected immediately (no sync needed)
  THEN: state.quests == [quest1]
  AND: state.isLoading == false
  [Journey 10 — offline-first: cache displayed without network]

scenario_when_store_updated_reactively_then_state_updates:
  GIVEN: FakeQuestRepository.store = [] initially
  AND: FakeAuthRepository(initialUid = "user1")
  AND: component constructed (state.quests.isEmpty() == true)
  WHEN: fakeRepo.emit([quest1, quest2])
  THEN: state.quests == [quest1, quest2]
  AND: state.isLoading == false
  [Journey 10 — retry scenario: WorkManager retry upserts to Room → Room Flow emits → component state updates]

scenario_when_store_not_mutated_on_network_fail_then_cache_preserved:
  GIVEN: FakeQuestRepository.store = [quest1] (cached data)
  AND: FakeAuthRepository(initialUid = "user1")
  AND: component constructed (state.quests == [quest1])
  WHEN: NO new emit to store (simulates: network failed, Room unchanged)
  THEN: state.quests == [quest1]  (cache preserved)
  AND: state.isLoading == false
  [Journey 10 — failed sync doesn't clear cache; component observes Room directly]
```

---

## 5. OfflineEmptyIntegrationTest (Journey 9)

- **Файл:** `android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/OfflineEmptyIntegrationTest.kt`

**Контекст:** Journey 9 = "Offline + empty cache" — device offline, local DB empty → show empty state (not loading spinner forever).

### Сценарии

```
scenario_offline_empty_cache_shows_empty_state:
  GIVEN: FakeQuestRepository.store = [] (empty cache)
  AND: FakeAuthRepository(initialUid = "user1")
  AND: component constructed (no network refresh triggered on init by default)
  WHEN: state collected immediately
  THEN: state.quests.isEmpty() == true
  AND: state.isLoading == false
  [Journey 9 — no infinite spinner, show empty state with FAB]

scenario_offline_non_empty_cache_shows_cached_data:
  GIVEN: FakeQuestRepository.store = [quest1, quest2]
  AND: FakeAuthRepository(initialUid = "user1")
  AND: no network (refreshFromRemote returns failure)
  WHEN: component constructed
  THEN: state.quests == [quest1, quest2] (from cache)
  [Journey 9 variant — cache hits show data even offline]
```

---

## 6. BrandComponentsInvariantsTest Compliance Verification

- **Файл:** `android/core/designsystem/src/test/kotlin/.../BrandComponentsInvariantsTest.kt` (EXISTING — не изменять)

**Проверка:** test-dev верифицирует что новые файлы `QuestCard.kt` и `StarRating.kt` созданы frontend-dev с соблюдением инвариантов:

```bash
# Запустить после создания frontend files:
./gradlew :android:core:designsystem:test
```

**Ожидаемый результат:** `BrandComponentsInvariantsTest` green (если QuestCard.kt + StarRating.kt имеют @Preview + нет Color(0xFF...)).

**Если тест красный:** test-dev отправляет EVIDENCE lead-у с указанием конкретного файла и строки нарушения. НЕ исправляет production code — это задача frontend-dev.

---

## Validation Commands

```bash
./gradlew :android:feature:quest:presentation:test
./gradlew :android:core:designsystem:test
./gradlew :android:feature:app-shell:presentation:test
./gradlew assemble
```
