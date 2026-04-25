---
date: 2026-04-20
feature: menu-refactor
type: testing
author: architect-component
status: CANONICAL
---

# Test Strategy: Menu Refactor

Полная карта тестовых сценариев, фреймворков, fake-blueprint и coverage mapping.

---

## 1. Test Infrastructure

| Аспект | Выбор |
|--------|-------|
| Framework | `kotlin.test` (commonTest) для KMP domain/data модулей |
| Test runner (Android) | `androidx.test.runner.AndroidJUnitRunner` |
| Mocking | MockK `1.13.10` для сложных зависимостей |
| Coroutines | `kotlinx-coroutines-test` — `runTest`, `StandardTestDispatcher` |
| Room (instrumented) | `Room.inMemoryDatabaseBuilder().allowMainThreadQueries()` |
| Room migration | `MigrationTestHelper` в `AppDatabaseMigrationTest` |
| DI | Ручная инъекция (no Hilt/Dagger) — project convention |
| Flow testing | `.toList()`, `.take(N).toList()`, `.value` — no Turbine |

---

## 2. Walking Skeleton Reuse / Deletion

### 2.1 Файлы к УДАЛЕНИЮ (вместе с тестами)

| Test файл | Связан с | Причина удаления |
|-----------|----------|-----------------|
| `LocalDeveloperOverrideTest.kt` | `LocalDeveloperOverride.kt` | overlay удалена (ADR-HLA-02) |
| `EffectiveDeveloperLevelTest.kt` | `EffectiveDeveloperLevel.kt` | merge logic удалена |
| `FakeLocalDeveloperOverrideRepositoryTest.kt` | фейк репозитория | overlay репозиторий удалён |

### 2.2 Файлы к ПЕРЕМЕЩЕНИЮ

| Test файл | From | To | Изменения |
|-----------|------|----|-----------|
| `QualificationLevelTest.kt` | `qualification:domain/test/` | `core:foundation/commonTest/` | обновить package declaration |

### 2.3 Файлы к ОБНОВЛЕНИЮ

| Test файл | Что меняется |
|-----------|-------------|
| `ActivateDevModeUseCaseTest.kt` | полная перепись — убрать `overlayRepo`, добавить lambda-based тесты |
| `VisibilityTest.kt` | убрать параметр `overlay`, добавить superqualification сценарии + `HomeQuests` порядок |
| `RegisterTapTest.kt` | сохранить без изменений (pure FSM) |

---

## 3. Test Scenarios Coverage

**Трассировка**: 69 spec-traced scenarios (из `0-spec-*.md` Domain Test Scenarios) + 8 supplemental repository scenarios = **77 total**.

| Категория | Кол-во | Источник |
|-----------|--------|---------|
| spec-qualification-levels | 14 | `0-spec-qualification-levels.md:119-133` |
| spec-dev-mode (FSM + use case + visibility) | 25 | `0-spec-dev-mode.md` Domain Test Scenarios |
| spec-home-quests | 7 | `0-spec-home-quests.md` Domain Test Scenarios |
| spec-catalog-foundation | 23 | `0-spec-catalog-foundation.md` Domain Test Scenarios |
| **Subtotal spec-traced** | **69** | — |
| supplemental (UserStatsRepositoryImpl) | 8 | ADR-HLA-03 (central AppDatabase) + ADR-HLA-02 (dev mode Room write) |
| **TOTAL** | **77** | — |

**Supplemental justification** (8 scenarios в §3.5):

| Scenario | Требует | AC / ADR |
|----------|---------|----------|
| US-01: `observeStats uid=null → emptyFlow` | Room + auth integration | ADR-HLA-03 + dev-mode AC #16 |
| US-02: `entity=null → UserStats.EMPTY` | Room null-safety | ADR-HLA-03 |
| US-03: mapper 16 полей round-trip | Mapper correctness | ADR-HLA-03 schema |
| US-04: `setLocalDeveloperLevel` → targeted UPDATE only | ADR-HLA-02 (не full UPSERT) | ADR-HLA-02 |
| US-05: `setLocalDeveloperLevel uid=null → noop` | Null safety | ADR-HLA-02 |
| US-06: `refreshProfile()` → upsert в Room | Sync pipeline | ADR-HLA-04 Syncable contract |
| US-07: `refreshProfile uid=null → Result.failure` | Auth guard | ADR-HLA-04 |
| US-08: `refreshProfile` перезаписывает developerLevel=0 | Dev mode auto-deactivation | ADR-HLA-02 + dev-mode AC #8 |

---

### 3.1 `spec-qualification-levels` — 14 сценариев

**Модуль**: `shared/core/foundation/commonTest/`
**Файл**: `QualificationLevelTest.kt`
**Source**: `0-spec-qualification-levels.md:119-133` (verbatim spec scenarios)

| # | Scenario | Method under test | Expected |
|---|----------|------------------|----------|
| QL-01 | `LEVEL_1.points == 100` | `QualificationLevel.LEVEL_1.points` | `100` |
| QL-02 | `LEVEL_2.points == 200` | `QualificationLevel.LEVEL_2.points` | `200` |
| QL-03 | `LEVEL_3.points == 300` | `QualificationLevel.LEVEL_3.points` | `300` |
| QL-04 | `LEVEL_1.isReachedBy(99)` → false | `LEVEL_1.isReachedBy(99)` | `false` |
| QL-05 | `LEVEL_1.isReachedBy(100)` → true | `LEVEL_1.isReachedBy(100)` | `true` |
| QL-06 | `LEVEL_1.isReachedBy(500)` → true (above threshold) | `LEVEL_1.isReachedBy(500)` | `true` |
| QL-07 | `LEVEL_2.isReachedBy(100)` → false | `LEVEL_2.isReachedBy(100)` | `false` |
| QL-08 | `LEVEL_2.isReachedBy(200)` → true | `LEVEL_2.isReachedBy(200)` | `true` |
| QL-09 | `LEVEL_3.isReachedBy(200)` → false | `LEVEL_3.isReachedBy(200)` | `false` |
| QL-10 | `LEVEL_3.isReachedBy(300)` → true | `LEVEL_3.isReachedBy(300)` | `true` |
| QL-11 | `LEVEL_1.isReachedBy(-1)` → false (negative) | `LEVEL_1.isReachedBy(-1)` | `false` |
| QL-12 | `LEVEL_1.isReachedBy(0)` → false | `LEVEL_1.isReachedBy(0)` | `false` |
| QL-13 | `values()` имеет ровно 3 элемента | `QualificationLevel.entries.size` | `3` |
| QL-14 | `values().map { it.points }` == `[100, 200, 300]` (правильный порядок) | `QualificationLevel.entries.map { it.points }` | `[100, 200, 300]` |

---

### 3.2 `spec-dev-mode` — 25 сценариев (после revert codex fix #2)

#### 3.2.1 `RegisterTap` FSM — 10 сценариев

**Модуль**: `shared/feature/qualification/domain/commonTest/`
**Файл**: `RegisterTapTest.kt`

| # | Scenario | Input state | Expected result |
|---|----------|-------------|----------------|
| DM-01 | первый тап → count=1, NoChange | progress.count=0 | `TapResult.NoChange(count=1)` |
| DM-02 | второй тап в пределах 500ms → count+1 | count=1, elapsed=100ms | `TapResult.NoChange(count=2)` |
| DM-03 | тап после 500ms → Reset | count=5, elapsed=600ms | `TapResult.Reset` |
| DM-04 | ровно 500ms → не Reset (граница включена) | count=3, elapsed=500ms | `TapResult.NoChange` |
| DM-05 | 501ms → Reset | count=3, elapsed=501ms | `TapResult.Reset` |
| DM-06 | девятый тап в срок → count=10, NoChange | count=8 | `TapResult.NoChange(count=9)` |
| DM-07 | 10-й тап, developer=0 → Activated | count=9, developer=0 | `TapResult.Activated` |
| DM-08 | 10-й тап, developer=100 → AlreadyDev | count=9, developer=100 | `TapResult.AlreadyDev` |
| DM-09 | 10-й тап, developer=99 → Activated | count=9, developer=99 | `TapResult.Activated` |
| DM-10 | Reset после 10-го с задержкой | count=9, elapsed=600ms | `TapResult.Reset` |

#### 3.2.2 `ActivateDevModeUseCase` — 6 сценариев

**Модуль**: `shared/feature/qualification/domain/commonTest/`
**Файл**: `ActivateDevModeUseCaseTest.kt`

| # | Scenario | Setup | Expected |
|---|----------|-------|----------|
| DM-11 | `invoke` при Activated → вызывает `onDevModeActivated` | 10-й тап, developer=0 | `onDevModeActivated()` вызван 1 раз |
| DM-12 | `invoke` при NoChange → НЕ вызывает callback | 5-й тап | `onDevModeActivated()` не вызван |
| DM-13 | `invoke` при AlreadyDev → НЕ вызывает `onDevModeActivated` | developer=100, 10-й тап | `onDevModeActivated()` не вызван |
| DM-14 | `invoke` возвращает `TapResult` корректно | любой | return == `registerTap()` result |
| DM-15 | `readCurrentDeveloperLevel` вызывается при каждом invoke | — | вызван 1 раз per invoke |
| DM-16 | `onDevModeActivated` suspend — не блокирует FSM | delay в lambda | invoke завершается нормально |

#### 3.2.3 `Visibility` — 7-cell matrix + footer — 11 сценариев

**Модуль**: `shared/feature/app-shell/domain/commonTest/`
**Файл**: `VisibilityTest.kt`
**Source**: `0-spec-dev-mode.md:361-371` — Visibility main matrix (7 cells, все покрыты)

| # | Matrix cell (spec row) | `requiredRoles` | `developer` | Other roles | Expected `isVisible` |
|---|----------------------|-----------------|-------------|-------------|----------------------|
| DM-17 | Row 1: superqualification | `{D=100,T=100,M=100,A=100}` | 100 | любые | `true` |
| DM-18 | Row 2: normal all-satisfied | `{D=100,T=100,M=100,A=100}` | 0 | tester=100, mod=100, admin=100 | `true` |
| DM-19 | Row 3: normal partial-fail | `{D=100,T=100,M=100,A=100}` | 0 | tester=100, mod=0, admin=100 | `false` |
| DM-20 | Row 4: superqual без D в roles | `{T=100}` (нет D) | 100 | tester=0 | `true` |
| DM-21 | Row 5: normal tester≥100 | `{T=100}` (нет D) | 0 | tester=100 | `true` |
| DM-22 | Row 6: normal tester<100 | `{T=100}` (нет D) | 0 | tester=50 | `false` |
| DM-23 | Row 7: always visible | `{}` (empty map) | any (0) | any | `true` |

**Footer Contract сценарии** (из spec `0-spec-dev-mode.md` Footer Contract):

| # | Scenario | Input | Expected |
|---|----------|-------|----------|
| DM-24 | `visibleFooterActions(debug=false, developer=0)` → `[About]` only | — | `[About]` |
| DM-25 | `visibleFooterActions(debug=false, developer=100)` → all 3 | — | `[DesignCatalog, SyncNow, About]` |
| DM-26 | `visibleFooterActions(debug=true, developer=0)` → all 3 (debug bypass) | — | `[DesignCatalog, SyncNow, About]` |
| DM-27 | `visibleFooterActions` порядок стабилен: DesignCatalog → SyncNow → About | debug=false, developer=100 | строгий порядок |

---

### 3.3 `spec-home-quests` — 7 сценариев

**Модуль**: `shared/feature/app-shell/domain/commonTest/`
**Файл**: `VisibilityTest.kt` (обновить существующие тесты)

| # | Scenario | Method | Expected |
|---|----------|--------|----------|
| HQ-01 | `visibleSections(LOCAL)` первый элемент — HomeQuests | `visibleSections(Tab.LOCAL, ...)` | `result[0] is HomeQuests` |
| HQ-02 | `visibleSections(LOCAL)` содержит HomeQuests (не MyCourses) | — | `HomeQuests in result` |
| HQ-03 | `visibleSections(LOCAL)` НЕ содержит MyCourses | — | `MyCourses !in result` |
| HQ-04 | `rootOf(HomeQuests)` → `Config.HomeQuestsRoot` | `rootOf(HomeQuests)` | `Config.HomeQuestsRoot` |
| HQ-05 | `HomeQuests.requiredRoles` == пустой Map (всегда видима) | `HomeQuests.requiredRoles` | `emptyMap()` |
| HQ-06 | `DrawerSection.HomeQuests` существует как LocalSection | type check | `HomeQuests is LocalSection` |
| HQ-07 | `visibleSections(LOCAL)` порядок: HomeQuests, MyQuests, Settings | — | строгий порядок |

---

### 3.4 `spec-catalog-foundation` — 23 сценария

#### 3.4.1 Domain model — 5 сценариев

**Модуль**: `shared/core/catalog/domain/commonTest/`
**Файл**: `CatalogTest.kt`

| # | Scenario | Expected |
|---|----------|----------|
| CF-01 | `Catalog` c blank name → domain validation fails | исключение или `Result.failure` |
| CF-02 | `CatalogId("")` → невалидный ID | exception или explicit check |
| CF-03 | `Catalog.picturePath=null` → допустимо | создаётся без исключения |
| CF-04 | `CatalogId` equals по `value` | `CatalogId("a") == CatalogId("a")` |
| CF-05 | `ObserveCatalogsUseCase` делегирует к `CatalogRepository.observeAll()` | вызов делегируется |

#### 3.4.2 Room DAO boundary — 5 сценариев (instrumented)

**Модуль**: `shared/core/persistence/androidTest/`
**Файл**: `CatalogDaoTest.kt`

| # | Scenario | Expected |
|---|----------|----------|
| CF-06 | `insertAll` + `observeAll` → возвращает вставленные записи | list не пуст |
| CF-07 | `replaceAll` — атомарная замена (нет промежуточного пустого состояния) | Flow не эмитит `[]` между delete и insert |
| CF-08 | `replaceAll` удаляет устаревшие записи | старый ID отсутствует после replace |
| CF-09 | `observeAll` сортировка по id ASC | `[courses, games, school, surveys]` |
| CF-10 | `findById` — возвращает null если не найдено | `null` |

#### 3.4.3 `CatalogRepositoryImpl` — 8 сценариев

**Модуль**: `shared/core/catalog/data/commonTest/`
**Файл**: `CatalogRepositoryImplTest.kt`

| # | Scenario | Expected |
|---|----------|----------|
| CF-11 | `observeAll` — читает из Room, не из Firestore | `firebaseDataSource.fetchAll()` НЕ вызван |
| CF-12 | `refreshFromRemote` — сохраняет данные из Firestore в Room | `catalogDao.replaceAll()` вызван с правильными entities |
| CF-13 | `refreshFromRemote` — Firestore error → `Result.failure` | возвращает `Result.failure` |
| CF-14 | `refreshFromRemote` — `picturePath=null` → `pictureUrl=null` (no URL resolution) | `storageUrlResolver` не вызван |
| CF-15 | `refreshFromRemote` — `picturePath` non-null → `storageUrlResolver` вызван | resolver вызван с правильным path |
| CF-16 | `sync()` делегирует к `refreshFromRemote()` | поведение идентично |
| CF-17 | `observeAll` сортирует по `id.value ASC` | `[CatalogId("a"), CatalogId("b")]` |
| CF-18 | `getById` — возвращает Catalog для существующего ID | `Catalog.id == запрошенный` |

#### 3.4.4 Mapper round-trip — 5 сценариев

**Модуль**: `shared/core/catalog/data/commonTest/`  
**Файл**: `CatalogMapperTest.kt`

| # | Scenario | Expected |
|---|----------|----------|
| CF-19 | `CatalogEntity.toDomain()` round-trip `id` | `entity.id == domain.id.value` |
| CF-20 | `CatalogEntity.toDomain()` round-trip `name` | сохраняется |
| CF-21 | `Catalog.toEntity()` round-trip `picturePath` | `null` остаётся `null` |
| CF-22 | `DocumentSnapshot.toCatalogDto()` — blank name → `null` | возвращает `null` |
| CF-23 | `CatalogDto.toEntity()` — `picturePath=null` → `CatalogEntity.picturePath=null` | `null` preserved |

---

### 3.5 `UserStatsRepositoryImpl` — 8 сценариев

**Модуль**: `shared/feature/app-shell/data/commonTest/`
**Файл**: `UserStatsRepositoryImplTest.kt`

| # | Scenario | Expected |
|---|----------|----------|
| US-01 | `observeStats()` — uid null → `emptyFlow()` | Flow не эмитит значений |
| US-02 | `observeStats()` — Room entity null → `UserStats.EMPTY` | эмитит `UserStats.EMPTY` |
| US-03 | `observeStats()` — маппинг всех 16 полей | `toDomain()` корректно |
| US-04 | `setLocalDeveloperLevel(100)` — вызывает `updateDeveloperLevel`, не `upsert` | только targeted UPDATE |
| US-05 | `setLocalDeveloperLevel` — uid null → ничего не происходит | DAO не вызван |
| US-06 | `refreshProfile()` — Firestore success → upsert в Room | `upsert()` вызван |
| US-07 | `refreshProfile()` — uid null → `Result.failure` | failure без Firestore call |
| US-08 | `refreshProfile()` → `developerLevel` из Firestore перезаписывает локальный | `developerLevel=0` в entity |

---

## 4. Fake Blueprint

### 4.1 `FakeUserStatsDao`

```kotlin
class FakeUserStatsDao : UserStatsDao {
    private val _flow = MutableStateFlow<UserStatsEntity?>(null)
    var lastUpserted: UserStatsEntity? = null
    var updateDeveloperLevelCalls: Int = 0

    override fun observeByUid(uid: String): Flow<UserStatsEntity?> = _flow
    override suspend fun findByUid(uid: String): UserStatsEntity? = _flow.value
    override suspend fun upsert(entity: UserStatsEntity) {
        lastUpserted = entity
        _flow.value = entity
    }
    override suspend fun updateDeveloperLevel(uid: String, value: Int) {
        updateDeveloperLevelCalls++
        _flow.value = _flow.value?.copy(developerLevel = value)
    }
    fun emit(entity: UserStatsEntity?) { _flow.value = entity }
}
```

### 4.2 `FakeCatalogDao`

```kotlin
class FakeCatalogDao : CatalogDao {
    private val _flow = MutableStateFlow<List<CatalogEntity>>(emptyList())
    var replaceAllCalls: Int = 0

    override fun observeAll(): Flow<List<CatalogEntity>> = _flow
    override suspend fun findById(id: String): CatalogEntity? = _flow.value.find { it.id == id }
    override suspend fun insertAll(entities: List<CatalogEntity>) { _flow.value = entities }
    override suspend fun deleteAll() { _flow.value = emptyList() }
    override suspend fun replaceAll(entities: List<CatalogEntity>) {
        replaceAllCalls++
        _flow.value = entities
    }
}
```

### 4.3 `FakeCatalogRemoteDataSource`

**Note**: Interface = `CatalogRemoteDataSource.fetchAll(): List<CatalogDto>` (canonical в `06-api-contract.md §8`).

```kotlin
class FakeCatalogRemoteDataSource : CatalogRemoteDataSource {
    var result: Result<List<CatalogDto>> = Result.success(emptyList())
    var fetchAllCalls: Int = 0

    override suspend fun fetchAll(): List<CatalogDto> {
        fetchAllCalls++
        return result.getOrThrow()
    }
}
```

### 4.3b `FakeCatalogUrlResolver`

Для тестирования ADR-HLA-07 URL resolution в `CatalogRepositoryImpl.refreshFromRemote()`:

```kotlin
class FakeCatalogUrlResolver {
    val resolvedUrls = mutableMapOf<String, String>()
    var callCount: Int = 0

    val resolver: suspend (String) -> String = { path ->
        callCount++
        resolvedUrls[path] ?: "https://fake-storage.example.com/$path"
    }
}
```

### 4.4 `FakeFirebaseUserStatsDataSource`

```kotlin
class FakeFirebaseUserStatsDataSource {
    var result: Result<RawUserStats> = Result.success(RawUserStats())
    var fetchCalls: Int = 0

    suspend fun fetch(uid: String): RawUserStats {
        fetchCalls++
        return result.getOrThrow()
    }
}
```

---

## 5. Integration Test Plan

### 5.1 `AppDatabaseMigrationTest` (instrumented)

**Файл**: `shared/core/persistence/androidTest/AppDatabaseMigrationTest.kt`
**Framework**: `MigrationTestHelper`

| Test | Description |
|------|-------------|
| `version1_schema_valid` | Создать DB v1, проверить таблицы `user_stats` + `catalogs` существуют |
| `version1_user_stats_insert_query` | UPSERT + SELECT в `user_stats` работает без crash |
| `version1_catalog_insert_query` | `insertAll` + `observeAll` в `catalogs` возвращает данные |

### 5.2 `UserStatsDaoTest` (instrumented)

**Файл**: `shared/core/persistence/androidTest/UserStatsDaoTest.kt`

| Test | Description |
|------|-------------|
| `upsert_and_observe_by_uid` | UPSERT + Flow эмитит entity |
| `updateDeveloperLevel_targeted_update` | `updateDeveloperLevel` меняет только `developerLevel`, не другие поля |
| `upsert_replace_overwrites_developer_level` | полный UPSERT перезаписывает `developerLevel` |
| `observe_returns_null_for_unknown_uid` | `observeByUid("unknown")` → эмитит `null` |

### 5.3 Journey Integration Tests

Mapping primary journeys (из `0-spec.md` Primary User Journeys) → integration test files:

| Journey | Test file | Scope |
|---------|-----------|-------|
| Dev mode activation (10-tap) | `DevModeActivationIntegrationTest` | `DefaultRootComponent` + `ActivateDevModeUseCase` + `FakeUserStatsDao` — полный flow тапов до Room write |
| Dev mode auto-deactivation via sync | `SyncDeactivatesDevModeIntegrationTest` | `UserStatsRepositoryImpl.refreshProfile()` → `upsert(developer=0)` → Flow emits developer=0 |
| SyncNow manual trigger | `SyncNowFlowIntegrationTest` | `DefaultRootComponent.onSyncNow()` → `WorkManager.enqueueUniqueWork()` + `RootEvent.SyncStarted` emitted |
| First-launch catalog pull | `CatalogFirstFetchIntegrationTest` | Empty Room → `refreshFromRemote()` → `CatalogDao.replaceAll()` → `observeAll()` emits list |
| Warm cache (Room → UI) | `CatalogWarmCacheIntegrationTest` | Pre-populated Room → `observeAll()` emits without Firestore call |
| Offline launch | `CatalogOfflineEmptyIntegrationTest` | Empty Room + Firestore error → `observeAll()` emits emptyList, no crash |
| DesignCatalog render condition | `DesignCatalogRenderConditionTest` | `visibleFooterActions(debug=false, developer=0)` → DesignCatalog absent; `developer=100` → present (spec AC #13-14) |

---

## 6. Test Coverage Summary

| Sub-spec | Сценарии | Файлы | Walk Skel reuse |
|----------|----------|-------|----------------|
| qualification-levels | 14 | `QualificationLevelTest.kt` (moved) | MOVED |
| dev-mode (FSM) | 10 | `RegisterTapTest.kt` | PRESERVED |
| dev-mode (use case) | 6 | `ActivateDevModeUseCaseTest.kt` | REWRITTEN |
| dev-mode (visibility+footer) | 11 | `VisibilityTest.kt` | UPDATED |
| home-quests | 7 | `VisibilityTest.kt` | UPDATED |
| catalog-domain | 5 | `CatalogTest.kt` | PRESERVED |
| catalog-DAO | 5 | `CatalogDaoTest.kt` | NEW |
| catalog-repository | 8 | `CatalogRepositoryImplTest.kt` | NEW |
| catalog-mappers | 5 | `CatalogMapperTest.kt` | NEW |
| user-stats-repository (supplemental) | 8 | `UserStatsRepositoryImplTest.kt` | NEW |
| **Spec-traced subtotal** | **69** | — | — |
| **Supplemental** | **8** | — | — |
| **ИТОГО** | **77** | 10 файлов | — |

---

## 7. Test Priorities (фазы реализации)

| Приоритет | Тесты | Фаза |
|-----------|-------|------|
| P0 (блокирует фазу) | QL-01..14, DM-01..10 | Phase-01 (domain) |
| P0 | HQ-01..07 | Phase-01 |
| P1 (блокирует data) | US-01..08, CF-11..18 | Phase-02 (data) |
| P1 | CF-19..23 | Phase-02 |
| P2 (integration) | CF-06..10, AppDatabaseMigrationTest | Phase-03 (instrumented) |
| P2 | DM-11..25 (use case + visibility) | Phase-02 |
