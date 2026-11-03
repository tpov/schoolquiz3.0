---
date: 2026-04-22
author: architect-component
feature: home-and-my-quests
---

# Component-Level Decisions: home-and-my-quests

ADR entries для component-уровневых решений. Каждый ADR содержит: контекст, решение, alternatives considered, последствия.

---

## ADR-CMP-49: CascadingSyncOrchestrator — recursive + enum SyncLevel

**Status**: ACCEPTED (User Decision #49)

### Context

Spec FR#14 описывает 6-уровневую каскадную синхронизацию. Нужно решить архитектуру orchestration кода.

Три варианта рассматривались:
- (a) Один класс `CascadingSyncOrchestrator` с recursive `syncCascade(level, parentIds)`
- (b) 6 отдельных `Syncable` в `syncModule` list, state через `SyncStateRepository`
- (c) Расширить `SyncWorker.performSync` с conditional dispatch

### Decision

**Вариант (a) — recursive orchestrator с `enum class SyncLevel`**.

```kotlin
// Canonical signatures: see 06-api-contract.md:317-395 (SyncLevel enum + CascadingSyncOrchestrator)

// Behavioral pseudocode (non-authoritative — illustrates the recursive pattern):
// syncCascade(SyncLevel.Catalog, emptySet()) → cursors via SyncStateRepository
//   → catalogRepo.refreshFromRemote() → changedCatalogIds
//   → syncCascade(SyncLevel.Quest, changedCatalogIds) → ...
//   → syncCascade(SyncLevel.Question, changedLessonIds) → leaf, no recursion
// Early-exit if changedParentIds.isEmpty() at any level.
```

### Contract Shape — Settled

Четыре пункта зафиксированы как **implementation contract**:

1. **Return type**: каждый sync step возвращает `Result<Set<ParentId>>` с ID изменённых entities для передачи как `parentIds` следующему уровню. Исключение: `syncQuestions` (leaf) → `Result<Unit>`.
2. **Guest mode (null UID)**: `currentUserUid: String?` — когда `null`, Query A (квесты по authorUid) **пропускается**; запускается только Query B (публичные по visibleOn). Ссылка: `06-api-contract.md:182`.
3. **Cursor advancement**: cursor обновляется через `SyncStateRepository.setCursor(collectionId, Clock.System.now())` (sampled as `freshTime` at cascade entry) **только при успехе subtree** данного шага. При failure — cursor остаётся; следующий sync повторяет с того же cursor (upsert-by-id идемпотентен). Управление cursor — ответственность `CascadingSyncOrchestrator` для всех 6 уровней (включая catalogs). Детали: §Amendment "Cursor Advance Strategy".
4. **Retry semantics**: оркестратор немедленно возвращает `Result.failure(e)` при ошибке — без per-step внутреннего retry. WorkManager exponential backoff обеспечивает retry на уровне Worker.

### Auth & Shelves Sourcing — Locked (F1)

**Auth (UID)**:
- `CascadingSyncOrchestrator` принимает `authRepo: AuthRepository` в конструктор (9th param — см. `06-api-contract.md:341`)
- В начале `sync()`: `val currentUserUid = authRepo.currentUid()` — null = guest
- При `null` UID: Catalog sync выполняется, Query A (own quests by authorUid) **пропускается**, Query B (public quests by visibleOn) **выполняется**

**Shelves**:
- MVP: hard-coded `availableShelves = setOf("home", "arena")` — Decision #15 baseline
- Future: `UserStatsRepository.currentStats().qualification` будет использоваться для dynamic shelf set
- OQ-CMP-SHELVES: переход на dynamic shelves — отдельная задача после phase-01

**Guest behavior contract**:
- `currentUserUid == null` → Catalog sync + Query B только (public quests, `visibleOn.contains(shelf)`)
- `currentUserUid != null` → Catalog sync + Query A (own quests) + Query B (public quests) — merge, deduplicate по ID

**Почему `enum`, не `sealed class`**:
- `sealed class` с `object` entries вызывает forward-reference проблемы при `next.next` в `init` контексте Kotlin
- `enum` с `when (this)` в computed property — идиоматичен, compile-safe exhaustive
- Добавление нового уровня = добавление одного enum value + `when` case → compile error в незатронутых местах как safety net

### Alternatives Considered

**Вариант (b) — 6 отдельных Syncable**:
- (-) Каждый `Syncable` должен знать как получить parentIds от предыдущего уровня → скрытая temporal coupling
- (-) `syncModule` list жёстко завязывает порядок — runtime, не compile-time
- (-) `as Syncable` cast уже существует в `syncModule.kt:16` как tech debt; умножение на 6 — риск
- (+) Чуть проще тестировать каждый уровень изолированно

**Вариант (c) — extend SyncWorker**:
- (-) `SyncWorker` становится god-object знающим про все entity types
- (-) Нарушает SRP: Worker должен только orchestrate, не содержать sync logic
- (-) Тестировать Android `Worker` сложнее чем pure Kotlin class

### Consequences

- `CascadingSyncOrchestrator` регистрируется в Koin как `single<CascadingSyncOrchestrator> { CascadingSyncOrchestrator(...) }` без named qualifier (SSoT `06-api-contract.md §13`)
- `SyncModule.kt` использует `get<CascadingSyncOrchestrator>()` в `List<Syncable>` — без `named("cascading")` (phase-03 retrospective: `named` qualifier оказался излишним)
- Explicit typed `get<CatalogRepository>()`, `get<QuestRepository>()`, … для всех 9 параметров конструктора — dependency graph читаем статически
- Тесты orchestrator в `shared/core/sync/src/commonTest/` с fake repositories

---

## ADR-CMP-51: Decompose Component для MyQuests/HomeQuests (не AndroidX ViewModel)

**Status**: ACCEPTED (User Decision #51)

### Context

Нужен presentation-уровень для `MyQuestsScreen` и рефакторинг `HomeQuestsRoot`. Проект использует Decompose 3.x для навигации.

### Decision

**Decompose Component** — `interface MyQuestsComponent` + `class DefaultMyQuestsComponent`.

```kotlin
// Canonical signatures: see 06-api-contract.md:394-429
// MyQuestsComponent interface + DefaultMyQuestsComponent (5-param constructor)
// HomeQuestsComponent interface + DefaultHomeQuestsComponent (2-param constructor)
// MyQuestsUiState + HomeQuestsUiState data classes
```

### Alternatives Considered

**AndroidX ViewModel**:
- (-) Paradigm mix: проект использует Decompose navigation везде (`DefaultRootComponent`, `DefaultLocalTabComponent`). ViewModel создаёт второй lifecycle management path
- (-) `ViewModelProvider.Factory` пришлось бы писать отдельно или использовать Hilt (не используется в проекте)
- (-) Тестировать Decompose Component можно через `TestComponentContext` без Android instrumented — как JVM unit tests
- (+) Более знакомый Android-разработчикам паттерн

**Прямая инъекция Repository в Composable** (текущий pre-existing pattern в `CatalogGridSection`):
- (-) Нарушает `use-cases.md` invariant #2 (Screen → ViewModel only)
- (-) Не тестируется без UI framework
- (-) Закрывает возможность переиспользования логики

### Lifecycle & Retention

Три Decompose/Essenty механизма — **complementary**, не альтернативы:

| Механизм | Что решает | Phase-01 |
|----------|-----------|----------|
| `coroutineScope(Dispatchers.Main + lifecycle)` | Memory leak — Flow collection auto-cancels при destroy | ✅ для `observeMyQuests` / `observeByShelf` |
| `instanceKeeper` | Config change retention — сохраняет `selectedCatalogId` при rotation | ✅ в `DefaultMyQuestsComponent` |
| `stateKeeper` | Process death survival (SavedStateHandle-equivalent) | ❌ не нужен: transient state восстанавливается из Room |

```kotlin
private val scope = coroutineScope(Dispatchers.Main + lifecycle)
private val selectedCatalogId: Value<CatalogId?> by instanceKeeper.getOrCreate { MutableValue(null) }
// stateKeeper — не используется phase-01
```

`coroutineScope` решает **memory leak**; `instanceKeeper` решает **retention** — без него selectedCatalogId сбрасывается при rotation.

### Consequences

- **Локация**: `android/feature/quest/presentation/` — одна директория для обоих компонентов
- **Koin**: `questPresentationModule = module { factory<MyQuestsComponent> { params -> DefaultMyQuestsComponent(params.get(), get(), get(), get(), get()) } }`
- `HomeQuestsComponent` создаётся в том же модуле; рефакторинг удаляет `CatalogGridSection` из `AppShellScreen.kt:319-329`
- Lifecycle: `coroutineScope` для Flow; `instanceKeeper` для selectedCatalogId retention при rotation

---

## ADR-CMP-52: archived filter — в DAO query, не в Repository/Kotlin

**Status**: ACCEPTED (User Decision #52)

### Context

Spec FR#20 требует `observeAll()` возвращал только `!archived` каталоги. Где фильтровать?

### Decision

**DAO query уровень**: `SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC`

Применяется ко **всем** 6 DAO: `CatalogDao.observeAll()`, `QuestDao.observeMyQuests()`, `QuestDao.observeMyQuestsInCatalog()`, `SectionDao.observeByQuest()`, `ThemeDao.observeBySection()`, `LessonDao.observeByTheme()`, `QuestionDao.observeByLesson()`.

### Alternatives Considered

**Kotlin `.filter { !it.archived }` в Repository/LocalDataSource**:
- (-) Race condition: sync может upsert archived entity в Room; между upsert и `.filter` вызовом — короткое окно когда archived entity видна в Flow
- (-) Каждый `observeAll()` call выполняет лишний Kotlin traversal поверх Room результата
- (-) `Flow<List<T>>` теряет Room's reactive change notification — нужно дополнительный `distinctUntilChanged`
- (+) Нет SQL сложности

**Отдельный archived=true логический delete в Room** (хранить запись с флагом до следующего sync):
- (-) Archived записи в Room — риск показать их при race; `archived=0` query — безопаснее
- (-) Decision #47-49 (AC) явно требует физическое удаление из Room при archived=true sync

### Scope of this ADR — Orphan Clarification (Codex REJECT C3 fix)

`archived=0` DAO filter решает **visibility** архивированных entities — скрывает их от UI. Он **НЕ** решает orphaned entities (дочерние записи без parent после physical delete parent).

**OQ-ORPHAN-1 — Known Limitation (phase-01)**: когда `catalog.archived=true` → `CatalogEntity` физически удалена из Room → все `QuestEntity` строки с `catalogId=deletedCatalog` становятся orphan. DAO `WHERE archived=0` это не обнаруживает. Orphan quests остаются в Room до получения их собственного `archived=true` от сервера (требует server Invariant B — `06-api-contract.md:§12`). Стратегия orphan cleanup (FOREIGN KEY CASCADE в Room? Periodic orphan worker? UI JOIN filter?) — separate follow-up feature, не блокирует phase-01. Рекомендация: добавить `@ForeignKey(onDelete = CASCADE)` в phase-02.

### Consequences

- Все `observe*` queries в DAO содержат `AND archived = 0`
- Физическое удаление при archived=true (через `deleteById`) — separate path от observeAll
- `upsertByIdIfNewerVersion` не показывает archived в UI даже если upsert произошёл раньше delete
- Orphan rows — known phase-01 limitation, задокументировано в OQ-ORPHAN-1

---

## ADR-CMP-53: CatalogEntity extension strategy (extend vs V2 table)

**Status**: ACCEPTED (User Decision #53)

### Context

Нужно добавить 4 поля в существующий `CatalogEntity`. Два пути: extend существующую entity или создать `CatalogEntityV2`.

### Decision

**Extend existing `CatalogEntity`** + schema version bump 1→2 + `fallbackToDestructiveMigration()`.

### Alternatives Considered

**`CatalogEntityV2` новая таблица**:
- (-) `AppDatabase.catalogDao()` — что возвращает? `CatalogDao` (для V1) или `CatalogDaoV2`? Нужно переименовывать
- (-) Mapper chain усложняется: `CatalogEntityV2 ↔ Catalog` + удалить `CatalogEntity ↔ Catalog` mapers
- (-) Все тесты с `CatalogEntity` сломаются — не "7 тестов обновить named args", а полностью переписать

**`@Migration(1, 2)` с ADD COLUMN**:
- (-) Нужно писать SQL migration: `ALTER TABLE catalogs ADD COLUMN version INTEGER NOT NULL DEFAULT 1`
- (-) 4 такие колонки + `Room.createTable` для 5 новых таблиц = ~40 строк migration SQL
- (-) Риск ошибки в migration SQL в pre-production; `fallbackToDestructiveMigration()` проще и надёжнее
- (+) Не теряет данные (актуально для production)
- Decision: **pre-production допустимо** (spec NFR#6, Decision #26)

### Blast Radius

Lost data on destructive migration:
- **UserStatsEntity** (17 fields): uid (PK), nickname, avatarUrl, hasPremium, streakDays, stars (Long), nolics (Long), standardHearts, goldHearts, gold (Long), currentSkill, testerLevel, moderatorLevel, sponsorLevel, translatorLevel, adminLevel, developerLevel
- **CatalogEntity**: всё (re-fetched на next sync)

**Acceptable because**: bootstrap `OneTimeWorkRequest` (`AppApplication.kt:76`) auto-refetches on every start; кратковременный empty state (~2-10s) → полное восстановление. Beta-пользователей < 10, нет production-пользователей при phase-01 ship (spec NFR#6, Decision #26).

**Alternative rejected**: `@Migration(1, 2)` — нужно ~40 строк SQL для 4 новых колонок + 5 новых таблиц + риск ошибки в migration SQL; pre-production это излишне.

**Production gate**: заменить `fallbackToDestructiveMigration()` на `@Migration(1, 2)` перед production release.

### Consequences

- `CatalogEntity` получает 4 поля с Kotlin default values → 7 existing tests обновляются с named args (минимальный diff)
- `AppDatabase.version = 2`, добавить `fallbackToDestructiveMigration()`
- `AppDatabaseSchemaValidationTest` (`androidTest`) — добавить тест для schema v2 как regression gate перед production release
- Blast radius documented выше — acceptable pre-production

---

## ADR-CMP-55: commonTest для data layer tests

**Status**: ACCEPTED (User Decision #55)

### Context

Проект имеет два паттерна test source sets:
- `shared/core/catalog/data/src/commonTest/` — catalog pattern
- `shared/feature/app-shell/data/src/jvmTest/` — app-shell pattern (legacy)

### Decision

**`commonTest`** для всех новых data layer модулей (quest/section/theme/lesson/question data).

### Alternatives Considered

**`jvmTest`** (app-shell legacy pattern):
- (-) Тесты не запускаются на Android target (только JVM)
- (-) Два паттерна в одном проекте создают mental overhead
- (-) `commonTest` → KMP-pure, тесты переиспользуемы на обоих targets

**Отдельный `androidTest`** для DAO тестов:
- (+) Позволяет использовать реальный Android Room in-memory
- (-) Требует подключённого устройства / emulator для каждого run
- (-) Catalog pattern использует `commonTest` с fake DAO — достаточно для unit coverage
- Decision: Room DAO boundary tests (real Room in-memory) остаются в `androidTest` (как `AppDatabaseSchemaValidationTest`); бизнес-логика repository — в `commonTest`

### CI Task Gates

```bash
# Все KMP unit tests (commonTest + jvmTest):
./gradlew allTests

# Room DAO tests (требуется device/emulator):
./gradlew :shared:core:persistence:connectedAndroidTest

# UI invariants (QuestCard, StarRating):
./gradlew :android:core:designsystem:test
```

### Consequences

- Все новые data test files в `src/commonTest/kotlin/`
- DAO-level tests (real in-memory Room) — `src/androidTest/` как `*DaoBoundaryTest` — опционально в phase-01

---

## ADR-CMP-50: `?v={version}` URL — placement и поле

**Status**: ACCEPTED (User Decision #50)

### Context

Нужно реализовать cache-busting для Coil при обновлении картинки. Где добавлять `?v=N` суффикс и какое поле version использовать?

### Decision

**Placement**: в `*RepositoryImpl.kt` после `storageUrlResolver(path)` вызова, с safe append (Firebase Storage download URLs могут уже содержать `?token=...`):
```kotlin
val resolvedUrl = storageUrlResolver(entity.picturePath)
val cacheBustedUrl = appendCacheVersion(resolvedUrl, entity.version)

private fun appendCacheVersion(url: String, version: Long): String =
    if (url.contains("?")) "$url&v=$version" else "$url?v=$version"
```

**Version поле**: `Quest.version` (всей сущности) — MVP.
**Over-invalidation caveat**: любой `Quest.version` bump (title, visibleOn и т.д.) триггерит image refetch — accepted cost. Granular `pictureVersion: Long` — TODO follow-up (не блокирует phase-01).
**TODO-DEFERRED**: отдельное `pictureVersion: Long` поле для granular invalidation. Эта оптимизация — отдельная задача.

### Alternatives Considered

**В `StorageUrlResolver` сигнатуре**: `suspend operator fun invoke(path: String, version: Long): String`
- (-) Ломает существующий `StorageUrlResolver` contract и все существующие usages
- (-) `StorageUrlResolver` — infrastructure concern (Storage URL resolution), не versioning concern

**В `*DtoMapper`**:
- (-) Dto не знает про Coil cache strategy — нарушение разделения ответственности

**`pictureVersion` сразу (не deferred)**:
- (-) Требует изменения Firestore schema + server logic для инкрементирования только при upload
- (+) Более точная инвалидация — не ломает cache при изменении title/visibleOn
- Decision: MVP использует `version` — достаточно для correctness; оптимизация deferred

### Consequences

- `CatalogRepositoryImpl` и `QuestRepositoryImpl` добавляют `?v=$version` после `storageUrlResolver(path)`
- Coil 3.4.0 (ADR-HLA-06, Decision #43) кеширует по full URL — `?v=N` → другой cache key → refetch
- `picturePath=null` → `storageUrlResolver` не вызывается → `?v=` не применяется

---

## ADR-CMP-TC: TypeConverter placement — shared/core/persistence

**Status**: ACCEPTED

### Context

`Quest.visibleOn: Set<String>` требует `@TypeConverter` для Room. Где размещать?

### Decision

`StringSetConverter` в `shared/core/persistence` — рядом с `AppDatabase`.

### Alternatives Considered

**`shared/feature/quest/data`**:
- (-) `AppDatabase` объявлен в `shared/core/persistence`. `@TypeConverters(StringSetConverter::class)` на `AppDatabase` — Room требует converter быть в compile classpath `AppDatabase`. Если converter в `quest/data` — `persistence` module не видит его напрямую (reverse dependency)
- (-) `persistence` depends on `quest/data` → circular dependency

**Отдельный `shared/core/persistence/converters` package**:
- (+) Группировка всех converters — good practice при росте
- Decision: в phase-01 только один converter → отдельный package premature. При добавлении второго — refactor

### Annotation: @ProvidedTypeConverter (web-researcher finding 2026-04-22)

Room KMP не использует Java reflection для создания converter instance. Поэтому:
- `StringSetConverter` аннотируется `@ProvidedTypeConverter`
- `AppDatabase` сохраняет `@TypeConverters(StringSetConverter::class)` — для compile-time type check
- Room builder передаёт instance явно: `.addTypeConverter(StringSetConverter())`
- Без `@ProvidedTypeConverter` — runtime crash на KMP JVM target

### Phase-01 Prerequisites (backend-dev owned)

Обязательные изменения до того как DAO/entity phase-01 tasks запустятся:

- **P1**: `shared/core/persistence/build.gradle.kts` — добавить `add("kspJvm", libs.room.compiler)` (BD-1 в `05-prior-art.md`)
- **P2**: `PersistenceModule.kt` Room.builder — добавить `.addTypeConverter(StringSetConverter())`
- **P3**: `AppDatabase.kt` — добавить `@TypeConverters(StringSetConverter::class)` на class level

**Dependency order**: P1 → P2 → P3 → затем dao/entity phase-01 tasks. Без P1 — KSP не генерирует Room код. Без P3 — compile error. Без P2 — runtime crash.

### Consequences

- `StringSetConverter.kt` в `shared/core/persistence/src/commonMain/`
- Room builder: `.addTypeConverter(StringSetConverter())` — required, не optional
- При расширении (например `Map<String, Any>` converter) — добавить в тот же файл или создать `converters/` package

---

## ADR-CMP-FAKE: Fake blueprint standard для 6 repositories

**Status**: ACCEPTED

### Context

Проект имеет устойчивый pattern для fakes (`FakeCatalogRepository`, `FakeCatalogDao`). Нужно унифицировать для 5 новых repositories.

### Decision

Единый стандарт fake blueprint:

```kotlin
class Fake{Entity}Repository : {Entity}Repository {
    private val _store = MutableStateFlow<Map<{Entity}Id, {Entity}>>(emptyMap())

    // Test helpers
    fun seed(vararg entities: {Entity}) { _store.value = entities.associateBy { it.id } }
    fun snapshot(): List<{Entity}> = _store.value.values.toList()
    fun isEmpty(): Boolean = _store.value.isEmpty()

    // Refresh tracking
    var refreshCalls = 0
    var lastRefreshParentIds: Set<*>? = null
    private var nextRefreshResult: Result<Unit> = Result.success(Unit)
    fun setNextRefreshFailure(t: Throwable) { nextRefreshResult = Result.failure(t) }
    fun simulateRemoteAdd(entity: {Entity}) { _store.update { it + (entity.id to entity) } }
    fun simulateRemoteDelete(id: {Entity}Id) { _store.update { it - id } }

    // Interface implementation
    override fun observeBy{Parent}(parentId: {Parent}Id): Flow<List<{Entity}>> =
        _store.map { map -> map.values.filter { it.{parentId} == parentId && !it.archived }.sortedBy { it.order } }

    override suspend fun getById(id: {Entity}Id): {Entity}? = _store.value[id]

    // Canonical: see 06-api-contract.md:202-205 — returns Result<Set<{Child}Id>>, not Result<Unit>
    override suspend fun refreshByParents(parentIds: Set<{Parent}Id>, cursor: Long): Result<Set<{Child}Id>> {
        refreshCalls++
        lastRefreshParentIds = parentIds
        return nextRefreshResult.map { emptySet() }  // override setNextRefreshResult for non-empty changed sets
    }
}
```

**Ключевые свойства**:
- `_store: MutableStateFlow<Map>` — reactive, не `MutableList`
- `seed()`, `snapshot()` — test setup/assertion helpers
- `refreshCalls` counter — для проверки "был ли вызван refreshByParents"
- `setNextRefreshFailure()` — для error path тестов
- `simulateRemoteAdd/Delete` — для testing cascade recursion (без реального sync)

**Существующие fakes для проверки**: `FakeCatalogRepository.kt:47` в `shared/core/catalog/domain/src/commonTest/fake/` — canonical reference.

**FakeAuthRepository** (Walking Skeleton, Decision #42):
```kotlin
// Canonical AuthRepository interface: see 06-api-contract.md:232-241
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

**FakeSyncStateRepository**:
```kotlin
// Canonical SyncStateRepository interface: see 06-api-contract.md:244-260
class FakeSyncStateRepository : SyncStateRepository {
    private val cursors = mutableMapOf<String, Long>()
    private val pendingCascades = mutableMapOf<String, PendingCascade>()

    override suspend fun getCursor(collectionId: String): Long = cursors[collectionId] ?: 0L
    override suspend fun setCursor(collectionId: String, value: Long) { cursors[collectionId] = value }
    override suspend fun markCascadeInProgress(parentId: String, parentType: String, pendingChildIds: Set<String>) {
        pendingCascades["$parentType:$parentId"] = PendingCascade(parentId, parentType, pendingChildIds)
    }
    override suspend fun markCascadeCompleted(parentId: String, parentType: String) {
        pendingCascades.remove("$parentType:$parentId")
    }
    override suspend fun getPendingCascades(): List<PendingCascade> = pendingCascades.values.toList()
}
```

### Minimum Contract vs Repo-specific Helpers

**Minimum contract** (обязателен для всех 6 fakes):
- `_store: MutableStateFlow<Map<Id, Domain>>` — reactive backing
- `seed(vararg entities)` — test setup
- `snapshot(): List<Domain>` — assertion helper
- Call counters для write methods (`refreshCalls`, etc.)
- `setNextRefreshFailure(t: Throwable)` — error path testing

**Repo-specific helpers** (разрешены в конкретных fakes, НЕ в abstract base):
- `FakeQuestRepository.simulateQueryAB(ownQuests: List<Quest>, publicQuests: List<Quest>)` — для Query A/B merge scenarios (тесты deduplicate logic)
- `FakeSyncStateRepository.resetAll()` — для process death simulation тестов
- `FakeSyncStateRepository.setCursorCalls: MutableList<Pair<String, Long>>` — call tracking for cursor advancement verification
- Rule: blueprint задаёт **minimum API**; конкретные fakes **расширяют** под конкретные test needs. Нет abstract base class — только convention.

---

## ADR-CMP-NAV-45: Destination.OpenQuestCreate — push семантика

**Status**: ACCEPTED (User Decision #45, #47)

### Context

FAB в `MyQuestsScreen` нажимает → нужна navigation к `QuestCreateRoot`. `Navigator.goTo` принимает `Destination`, не `LocalConfig`.

### Decision

1. `Destination.OpenQuestCreate` — новый `data object` в `Destination.kt` (sealed)
2. `AppShellTransitions.navigate()` добавляет exhaustive when case `OpenQuestCreate -> onOpenQuestCreate(state)`
3. `onOpenQuestCreate(state)` — отдельный handler (не reuse `onOpenDesignCatalog`):
   - Guard: если `state.localState.stack.active == LocalConfig.QuestCreateRoot` → no-op (Decision #47)
   - Иначе: `setLocalStack(NavStack.push(QuestCreateRoot))`

```kotlin
// AppShellTransitions.kt — добавить:
fun onOpenQuestCreate(state: AppShellState): AppShellState {
    if (state.localState.stack.active == LocalConfig.QuestCreateRoot) return state // re-tap guard
    return state.copy(
        activeTab = Tab.LOCAL,
        localState = state.localState.copy(
            stack = NavStack.push(state.localState.stack, LocalConfig.QuestCreateRoot)
        )
    )
}
```

**Atomic constraint** (Decision #48): `Destination.kt` и `AppShellTransitions.kt` меняются в одном PR — добавление `OpenQuestCreate` в sealed ломает exhaustive when в Transitions → compile error как safety net.

### Alternatives Considered

**`Destination.Push(config: TabConfig)`** (generic push mechanism):
- (+) Более гибкий — любой `LocalConfig` можно пушить
- (-) Неясная семантика: push это side-effect навигации, не domain event
- (-) Тяжелее в exhaustive when — нужно match по `config` тип

### Consequences

- `Labels.kt:85-95` exhaustive when добавляет `LocalConfig.QuestCreateRoot -> "Создание квеста"`
- `AppShellScreen.LocalTabContent` when добавляет `LocalConfig.QuestCreateRoot -> UnderConstructionScreen("Создание квеста в разработке")`
- 5 domain test scenarios (41a-41e) в `AppShellTransitionsTest` покрывают push semantics + guard

---

---

## ADR-CMP-56: Cascade Parent-Id Cross-Feature Imports — One-Way Allowed

**Status**: ACCEPTED (architect-reviewer finding M1, phase-02)

### Context

`SectionRepositoryImpl` (in `shared/feature/section/data`) needs `QuestId` from `shared/feature/quest/domain` to map `questIds: Set<QuestId>` parameter. Similarly:
- `ThemeRepositoryImpl` needs `SectionId` from `shared/feature/section/domain`
- `LessonRepositoryImpl` needs `ThemeId` from `shared/feature/theme/domain`

These are cross-feature imports in the data layer: `section/data → quest/domain`, `theme/data → section/domain`, `lesson/data → theme/domain`.

### Decision

**Allowed as one-directional cascade pattern**. The import direction strictly follows the cascade hierarchy: `question → lesson → theme → section → quest → catalog`. No reverse or bidirectional imports.

```
question/data → lesson/domain (for LessonId)
lesson/data   → theme/domain  (for ThemeId)
theme/data    → section/domain (for SectionId)
section/data  → quest/domain   (for QuestId)
quest/data    → catalog/domain (for CatalogId)
```

This is an extension of ADR-HMQ-06 ("One-Way Cross-Feature Dependency Direction") from the Walking Skeleton domain layer to the data layer. The same directional rule applies.

### Rationale

- `refreshByParents(parentIds: Set<ParentId>)` API requires the parent Id type from the parent domain module.
- Alternative (wrapping in `Set<String>` at interface boundary) would force string-to-Id re-wrapping in callers, leaking stringly-typed API into domain contracts.
- Parent-feature's domain module is a compile-only dependency (Id types are value classes); no runtime coupling beyond type-safety.

### Guard

Any reverse import (`quest/data → section/domain`, `section/data → theme/domain`, etc.) is a **blocker**. Detected by:
```bash
grep -rE "^import .*\.feature\.(section|theme|lesson|question)\.domain\." \
  shared/feature/quest/data/src/ --include="*.kt"
# Expected: no matches
```

### Alternatives Considered

**Shared `core/content-ids` module with all Id types**:
- (-) Overengineering for 5 value classes
- (-) Every data module depends on core/content-ids instead of its natural parent domain
- (+) Zero cross-feature coupling in data layer

**`Set<String>` at refreshByParents boundary**:
- (-) Stringly-typed API loses compile-time safety
- (-) Callers must wrap/unwrap Id types manually
- (-) Already violates Walking Skeleton contracts (interfaces use typed IDs)

### Consequences

- `section/data/build.gradle.kts` declares `implementation(project(":shared:feature:quest:domain"))`
- `theme/data/build.gradle.kts` declares `implementation(project(":shared:feature:section:domain"))`
- `lesson/data/build.gradle.kts` declares `implementation(project(":shared:feature:theme:domain"))`
- `question/data/build.gradle.kts` declares `implementation(project(":shared:feature:lesson:domain"))`
- Bidirectional coupling remains a blocker; one-directional cascade is by-design

---

## Open Questions

- **OQ-CMP-1**: RESOLVED — auth sourcing locked in ADR-CMP-49 "Auth & Shelves Sourcing" section: `authRepo.currentUid()` вызывается в начале `sync()`. `availableShelves` = MVP hard-coded `setOf("home", "arena")`. `UserStatsRepository.currentStats()` signature — не блокирует phase-01 (shelves не dynamic в MVP).
- **OQ-CMP-2**: RESOLVED — locked в ADR-CMP-49: null UID → Query A skip, Query B continues (public quests). Закреплено в Contract Shape #2.
- **OQ-CMP-SHELVES**: Dynamic shelf set через `UserStatsRepository.currentStats().qualification` — future feature после phase-01. MVP = hard-coded `{"home","arena"}`.

---

# High-Level Architectural Decisions

*Добавлено architect-high-level. Уровень: модульные границы, data flow, system-wide tradeoffs.*

---

## ADR-HMQ-01: Flat Firestore Collections vs Subcollections

**Status**: Accepted (Decision #2 / #16)

**Context**: 6-уровневую иерархию контента можно моделировать как subcollections (`catalogs/{id}/quests/{id}/…`) или как плоские top-level collections с parent-id полями.

**Decision**: Плоские top-level collections (`catalogs`, `quests`, `sections`, `themes`, `lessons`, `questions`). Связь через parent-id поля.

**Rationale**: Экран "Мои квесты" требует cross-catalog фильтра `authorUid==me` по всем квестам пользователя. При subcollections это невозможно одним Firestore query — нужен fan-out O(N каталогов). Плоская коллекция позволяет `quests.where('authorUid', '==', uid).get()` напрямую (FR#21).

Потомки (sections/themes/lessons/questions) тоже плоские по той же причине: cascade sync использует `questId IN changedIds` запрос — это cross-quest запрос. При subcollections нужен fan-out O(N quests) запросов, что недопустимо для batched cascade. Flat + `parentId IN changedIds` = один запрос с batch ≤30.

**Alternatives Considered**:
- Subcollections (`catalogs/{id}/quests/{id}…`): **отвергнуто** — fan-out O(N) для cross-catalog query; неприемлемо для производительности и Firebase cost. Security inheritance не помогает с cross-collection queries.
- Collection-group queries (Firestore `collectionGroup('quests').where('authorUid','==',uid)`): **рассматривалось** — решает cross-catalog проблему для верхнего уровня, но не решает cascade: `sections` нужны для конкретных quest IDs, не для всех sections глобально. Collection-group не поддерживает `parentId IN [ids]` с разными parent documents. Также: collection-group требует отдельных более дорогих security rules и не сочетается с cursor delta-pull. **Отвергнуто** для всей иерархии.

**Tradeoffs**: (+) Cross-catalog query одним запросом; (+) cascade batch via `parentId IN ids`; (+) simple per-collection indexes; (-) orphan risk при parent delete; (-) no automatic security inheritance — каждая collection имеет отдельные security rules.

---

## ADR-HMQ-02: Dual Firestore Query (A + B) для Quests

**Status**: Accepted (Decision #40)

**Context**: Нужно получить квесты: (a) `authorUid==me` ИЛИ (b) `visibleOn array-contains-any shelves`. Firebase SDK не поддерживает OR между array-contains и equality filter в одном query.

**Decision**: Два независимых параллельных Firestore query + client-side merge + dedupe по id.

```
Query A: quests.where('authorUid','==',uid).where('catalogId','in',changedCatalogIds).where('lastModifiedAt','>',cursor)
Query B: quests.where('visibleOn','array-contains-any',shelves).where('lastModifiedAt','>',cursor)
         [БЕЗ catalogId фильтра — Firebase ограничение: array-contains-any + where-in несовместимы]
         Клиент локально фильтрует: catalogId in changedCatalogIds
```

**Alternatives Considered**:
- Один OR query: **не поддерживается** Firebase SDK — нет OR между разными полями с array filter.
- Fan-out по one catalogId каждый: **отвергнуто** — O(N каталогов × shards) reads, квадратичный Firebase cost.

**Tradeoffs**: (+) Получаем и "свои" и "публичные" в одном sync; (-) Query B возвращает потенциально лишние docs (клиент их отбрасывает локально); (-) Два round-trips (но параллельно).

**Bootstrap / Cold-Start Amplification — Governing Bound (Option B)**:

После process death или первого запуска cursor сбрасывается в 0 (spec `0-spec.md:1168`). Query B без `catalogId` фильтра сканирует ВСЕ публичные квесты — потенциально неограниченный public scan.

**Accepted bound**: Query B при bootstrap (`cursor == 0`) выполняется с явным `.limit(1000)`. Если Firestore возвращает ровно 1000 документов — реализация логирует `WARNING "Bootstrap Query B hit limit — possible truncation; schedule follow-up sync"` и планирует повторный low-priority background sync. Firebase billing alert: >10 000 reads/day как production monitoring gate.

Rationale выбора Option B над альтернативами:
- Option A (`.limit(500)` жёстко): риск silent truncation для крупных каталогов без warning — меняет функциональную корректность
- Option C (accept без bounds): именно то, что Skeptic V2 отверг как "ungoverned"

Mitigation: `contentsVersion` early-exit минимизирует downstream cascade. `.limit(1000)` + warning log + billing alert = governed MVP bound; pagination — post-MVP follow-up.

---

## ADR-HMQ-03: `lastModifiedAt` Cursor vs `version` Cursor для Delta-Sync

**Status**: Accepted (Decision #15 superseded by #31; ADR-0004 Amendment 2026-04-21)

**Context**: Delta-sync требует курсора "что изменилось с момента последнего sync". Кандидаты: `version: Long` (монотонный per-entity) или `lastModifiedAt: Long` (serverTimestamp, сортируемый по времени).

**Decision**: `lastModifiedAt` — cursor для delta-pull. `version` — upsert monotonicity guard.

```kotlin
// Delta-pull cursor:
collection.whereGreaterThan("lastModifiedAt", storedCursor)  // Firestore native Timestamp comparison
// Upsert guard:
if (dto.version > local.version) { upsert() }  // protects from stale server response
```

**Alternatives Considered**:
- Cursor только по `version`: **отвергнуто** — version не сравнимы как глобальный offset коллекции (Entity A version=5, Entity B version=2; нельзя сказать "всё с version > X после последнего sync").
- Firestore real-time listeners: **отвергнуто** для background sync — pull-based дешевле и проще для фонового WorkManager.

**Tradeoffs**: (+) `lastModifiedAt > cursor` — единый скалярный cursor для всей коллекции; (+) `version` защищает от stale response; (-) два поля нужно синхронизировать серверу; (-) clock drift риск (минимален для Firestore managed serverTimestamp).

**Timestamp Precision / Tie-Loss Caveat**: `lastModifiedAt` хранится как Long millis (1ms precision). При одновременных writes к нескольким documents в одной millisecond (например Invariant B fan-out), они получат одинаковый cursor value. Если бы cursor был `max(lastModifiedAt)` returned docs — следующий pull с `lastModifiedAt > cursor` (строго больше) мог бы пропустить эти документы. **Этот риск устранён** Amendment "Cursor Advance Strategy" (03-decisions.md:804): cursor = `Clock.System.now()` (sampled at cascade entry) — всегда ≥ `max(lastModifiedAt)` всех returned docs, поэтому следующий pull подхватит concurrent writes того же ms. Альтернатива с tie-breaker (cursor = (millis, docId)) не требуется. **Принято как is** — Clock.System.now() strategy eliminates this edge case.

---

## ADR-HMQ-04: Server Invariants A/B — Hard Backend Dependency

**Status**: Accepted (Decision #54)

**Context**: Cascading sync работает только если сервер при любом write инкрементирует `lastModifiedAt+version+contentsVersion` у **предков** (Invariant A — upward propagation) и при изменении visibility/archived у **потомков** (Invariant B — downward cascade). Без этого клиент не получает nested изменения через cursor.

**Decision**: Документированная hard backend dependency. Cloud Function / admin-tool реализуется вне scope этой client-фичи.

**Deployment Gate**: Если Cloud Function для Invariant A/B **НЕ** задеплоена к моменту phase-01 release, фича шипится как **catalog-only sync** — вложенные сущности (quests/sections/themes/lessons/questions) не получают инкрементальных обновлений сверх первичной загрузки. AC #34 и AC #58 (spec `0-spec.md:1136`, `0-spec.md:1175`) в этом случае НЕ ПРОХОДЯТ — требуется явное решение team-lead/PM перед release.

**Backend Owner & Artifact**:
- Owner: backend/Firebase team
- Artifact: Cloud Function `onDocumentWritten` trigger на все 6 коллекций
- Gate condition: Function deployed + smoke test passed (manual check: write question → verify ancestor `lastModifiedAt` bumped within 5s)

**Alternatives Considered**:
- Client-side propagation: **отвергнуто** — нарушение security rules (клиент не может write к protected сущностям); race conditions; violates Firebase security model.
- Full-scan каждый sync (без cursor): **отвергнуто** — O(N) reads при любом sync; уничтожает смысл delta pull; неприемлемо для Firebase cost.
- Manual parent propagation (admin tool): **acceptable MVP mitigation** — spec `0-spec.md:592` упоминает как вариант. Допустимо для seed data pre-population, не для production writes.

**Tradeoffs**: (+) Client code прост и безопасен; (+) security — только сервер modifies server-managed fields; (-) **catalog-only sync** без backend (quests/sections/lessons/questions не обновляются инкрементально); (-) параллельный backend track требует координации.

---

## ADR-HMQ-05: Walking Skeleton Preservation в Phase-01

**Status**: Accepted (Invariant #6; spec §Walking Skeleton)

**Context**: Domain код для 5 feature-модулей сгенерирован на spec-фазе через `domain-designer` с зелёными JVM тестами. Phase-01 должен интегрировать инфраструктуру вокруг этого domain.

**Decision**: Phase-01 — **integration mode** (не rewrite domain). Backend-dev добавляет data-layer поверх существующих domain interfaces. Test-dev добавляет integration тесты, не дублирует pure domain tests. Произвольное изменение business rules после spec approval = architectural mismatch → эскалация.

**Exception — Narrowly-Scoped Domain Amendments**: Если grounding phase доказала противоречие в spec (как в данной фиче: navigation API, auth source, QuestRepository placeholder), narrowly-scoped domain amendment **допускается** при условии: (1) зафиксирован как Open Question в design phase отчёте; (2) team-lead одобряет перед impl; (3) затрагивает только signature/поле, не business invariant. Silent rewrite без одобрения — по-прежнему blocker.

**Alternatives Considered**:
- Реализовать domain заново в phase-01: **отвергнуто** — нарушение Invariant #6; потеря зелёных тестов как safety net.
- Заморозить skeleton полностью даже при grounded contradiction: **отвергнуто** — превращает "approved" в "unfixable"; несовместимо с grounding phase purpose.

---

## ADR-HMQ-06: One-Way Cross-Feature Dependency Direction

**Status**: Accepted (Invariant #3; verified by cross-feature scanner)

**Context**: 5 новых feature-модулей связаны через Id-type references. Нужно зафиксировать разрешённое направление зависимостей.

**Decision**: Строго одностороннее: `question → lesson → theme → section → quest → catalog`. Запрещён любой reverse или bidirectional import.

**Rationale**: Bidirectional coupling — blocker по Invariant #3. One-way cascade == one-way Gradle dependency graph. Verified by research scanner: нет bidirectional в Walking Skeleton (`build.gradle.kts:11-14` каждого модуля).

**Scope Clarification**: One-way rule верифицирован для Walking Skeleton domain modules. Опасный coupling появится в data, DI, и integration code — не в уже-линейном skeleton. Поэтому правило распространяется на **все слои**: запрещены обратные зависимости в `data/`, `platform/firebase/*`, `di/` и `android/feature/*`. Разрешённые composition layers: `apps/android-next/di/` (DI root), `shared/core/sync/` (integration orchestration). Только здесь допустимо иметь множественные feature imports.

**Alternatives Considered**:
- Shared `core/content` модуль со всеми Id types: **отвергнуто** — overengineering для 5 value classes; parent-id явно выражает содержательную иерархию.
- `catalog/core` хранит все Id types: **отвергнуто** — catalog — feature domain, не место для cross-feature infrastructure.

### Scope Clarification (ADR-HMQ-06 amendment)

`android/feature/app-shell/presentation` — designated UI composition shell, responsible for rendering
child feature components inside its tab/stack routing. Therefore allowed to directly depend on sibling
`android/feature/*/presentation` modules (e.g. `quest/presentation` for MyQuestsContent / HomeQuestsContent).

This exception applies ONLY to `app-shell/presentation`. All other `android/feature/*/presentation`
modules retain one-way cascade enforcement per ADR-HMQ-06.

Verified: reverse import check (quest/presentation → app-shell/presentation) is still blocker.

---

## ADR-HMQ-09: AuthRepository — Новый Interface vs Extension UserStatsRepository

**Status**: Accepted (Decision #42)

**Context**: `MyQuestsComponent` нужен Firebase Auth UID. Кандидаты: добавить метод в `UserStatsRepository` или создать новый `AuthRepository` interface.

**Decision**: Новый `AuthRepository` interface в `shared/feature/app-shell/domain/repository/`.

**Rationale**: `UserStatsRepository` имеет 4 fakes (commonTest, presentation/fake, 2 inline в KoinModuleWiringTest) — добавление метода ломает все 4. `UserStats` domain model не содержит `uid` — расширение нарушает SRP. Новый interface: SRP-clean, cross-feature reusable (create-quest, profile, sign-in/out), не ломает existing fakes. `AuthRepositoryImpl` оборачивает существующий `authUidFlow` из `AppApplication.kt:41`.

**Guest Identity Semantic Split**: В production коде два identity sources с разной семантикой:
- `AuthRepository.observeUid()` → `null` = Firebase не аутентифицирован (гость)
- `UserStatsRepositoryImpl` maps guest to uid=`"_local"` (grounding `2-grounding.md:27`)

**Правило**: sync logic использует **только** `AuthRepository.observeUid() == null` для guest check. `"_local"` uid — внутренний артефакт `UserStatsRepositoryImpl`, не должен попадать в quest queries или `refreshFromRemote(currentUserUid)`. Смешивание двух источников в sync logic запрещено — `null` = skip Query A, не `"_local"`.

**Alternatives Considered**:
- Добавить в UserStatsRepository: **отвергнуто** — ломает 4 fakes; UserStats ≠ Auth (SRP violation).
- `authUidFlow` lambda напрямую через Koin: **отвергнуто** — lambda в Koin не типобезопасна; нет интерфейса для переиспользования.

---

## ADR-HMQ-10: Canonical Domain Test Scenario Count — 58, не 50

**Status**: Accepted (design phase correction)

**Context**: `0-spec.md:1094` AC#4 говорит "all 50 Domain Test Scenarios". Фактически в spec определено 58 сценариев.

**Decision**: Canonical test scenario count = **58**. AC#4 в `0-spec.md:1094` содержит устаревшее число.

**Rationale**: Сценарии были расширены в ходе добавления Decisions #42, #45, #46:
- Scenarios 34a-e (5 scenarios): AuthRepository (Decision #42)
- Scenarios 41a-e (5 scenarios): OpenQuestCreate navigation (Decision #45/#46)
- Scenarios 45a-c (3 scenarios): reactive auth observe patterns

**Action Required**: `0-spec.md:1094` AC#4 должен быть обновлён до "all 58". Это out-of-scope для design phase (spec edit requires PM/team-lead approval). Запрос: explicit spec edit перед plan phase или в plan phase как first step.

**~~OQ-HMQ-3 CLOSED~~**: `0-spec.md:1094` AC#4 обновлён team-lead с "50" на "58". Spec и design docs согласованы.

**Alternatives Considered**:
- Оставить "50" в spec, использовать "58" только в `04-testing.md`: **отвергнуто** — несогласованный SSoT; reviewer не может верифицировать coverage без ручного счёта.

---

## Amendment — Cursor Advance Strategy (phase-03)

**Status**: ACCEPTED (phase-03 implementation)

Actual implementation: `setCursor(level, Clock.System.now())` after each successful cascade level (conservative).
Spec originally specified in ADR-CMP-49 §3: `setCursor(level, max(dto.lastModifiedAt))` (precise).

**Rationale for deviation:**
- `Clock.now()` is trivially monotonic; no need to iterate DTOs for `max(lastModifiedAt)`
- Safety property unchanged: items modified between `freshTime` sample and fetch completion are re-fetched on next sync — never missed
- Trade-off: minor over-fetch next cycle vs precise cursor tracking — accepted for MVP simplicity
- Items are de-duplicated by `upsertByIdIfNewerVersion` version-guard at DB level

**Status confirmed (cross-fix-pass #2)**: Clock.now() strategy is canonical for **all 6 levels** including `catalogs`. Catalog cursor advance moved from `CatalogRepositoryImpl` to `CascadingSyncOrchestrator` (subtree-atomic, same pattern as all other levels). `CatalogRepositoryImpl` retains `syncStateRepo` for `getCursor()` read only; `setCursor()` is orchestrator responsibility. Spec `0-spec.md:86` and `02-behavior.md:60` updated to match.

---

## Nested Content Visibility MVP — Firestore Rules (cross-fix-pass #2)

**Decision**: Option C — any-auth read for nested collections (sections/themes/lessons/questions).

**Status**: ACCEPTED MVP (2026-04-24)

**Context**: nested content Firestore rules previously allowed `read: if request.auth != null` without
verifying parent quest visibility. This creates a theoretical information-leak: an authenticated user
who knows a `sectionId` directly can read it regardless of whether the parent quest is private.

**Chosen approach** (Option C):
- Firestore rules remain `allow read: if request.auth != null` for nested collections.
- Quest-level visibility (`authorUid` / `visibleOn`) IS enforced server-side at the `/quests/` rule.
- Client-side enforcement: `CascadingSyncOrchestrator` only fetches nested content for quests
  the user has access to. Direct ID-based reads of nested content without quest discovery are
  not a realistic attack vector for MVP (requires out-of-band knowledge of nested document IDs).

**Alternatives rejected**:
- Option A (parent `get()` in rules): 1-2 extra read ops per document fetch — unacceptable
  performance cost on nested subtree queries (100+ documents per sync cascade).
- Option B (denormalize `questIsPublic` into nested docs): correct long-term solution but requires
  Cloud Function fan-out on quest write — out of scope for current MVP.

**Post-MVP**: implement Option B. Cloud Function trigger on `quest.visibleOn` / `quest.archived`
change bumps nested documents' `questIsPublic` + `questAuthorUid` fields (natural extension of
Invariant B downward cascade trigger). Rules updated to check these denormalized fields.

**Reference**: `06-api-contract.md §16`, `firestore.rules:32-40`.

---

## High-Level Open Questions

- **OQ-HMQ-1**: Orphan cleanup — quests orphan в Room после удаления catalog. UI показывает с placeholder caption "Unknown catalog" или фильтрует? Spec Journey 6 упоминает "остаются с placeholder caption 'Unknown'", но `QuestCard` design это не закрывает явно. Не блокирует phase-01 (orphan quests просто появятся с null catalogName).
- **OQ-HMQ-2**: Tombstone retention — ADR-0004 говорит 30 дней. `archived=true` Firestore records — физически удаляются server-janitor через 30 дней или живут вечно? Client-side не зависит от этого (client обрабатывает `archived=true` → local delete при sync). Открытый server-concern.
- ~~**OQ-HMQ-3**: Spec test count~~ — **CLOSED**. `0-spec.md:1094` AC#4 обновлён team-lead.
