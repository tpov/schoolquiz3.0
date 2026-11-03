---
phase: 03
role: backend-dev
---

# Phase-03 Backend Tasks

---

## Pattern Invariants

- `CascadingSyncOrchestrator` зависит ТОЛЬКО от domain interfaces и SyncStateRepository (нет data layer imports)
- `syncCascade` — `internal` метод; `sync()` — единственный публичный вход
- Cursor: `getCursor` в начале каждого level, `setCursor` ТОЛЬКО после `Result.success`
- Batch: orchestrator сам разбивает `parentIds > 30` на chunks
- `availableShelves = setOf("home", "arena")` — hard-coded константа в MVP

---

## 1. Create SyncLevel enum

- **Файл:** `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/SyncLevel.kt`
- **Тип:** enum class
- **Сигнатура:** `enum class SyncLevel { Catalog, Quest, Section, Theme, Lesson, Question; val next: SyncLevel?; val collectionId: String }`
- **Вход:** N/A
- **Поведение / Выход:**
  - `collectionId: String` computed property: Catalog→"catalogs", Quest→"quests", Section→"sections", Theme→"themes", Lesson→"lessons", Question→"questions"
  - `next: SyncLevel?` computed property: Catalog→Quest, Quest→Section, ..., Question→null
  - Используется в `syncCascade` для курсора: `syncStateRepo.getCursor(level.collectionId)`
- **Edge cases:**
  - `Question.next == null` — leaf, recursive call не происходит
  - Exhaustive `when` в `collectionId`/`next` — компилятор проверяет все cases
- **Canonical reference:** `06-api-contract.md` §4 SyncLevel

---

## 2. Create CascadingSyncOrchestrator

- **Файл:** `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/CascadingSyncOrchestrator.kt`
- **Тип:** class
- **Сигнатура:** `class CascadingSyncOrchestrator(catalogRepo, questRepo, sectionRepo, themeRepo, lessonRepo, questionRepo, syncStateRepo, authRepo, userStatsRepo) : Syncable`
- **Вход:** 9 parameters (все domain interfaces)
- **Поведение / Выход:**
  - `override suspend fun sync(): Result<Unit>` = `syncCascade(SyncLevel.Catalog, emptySet())`
  - `internal suspend fun syncCascade(level: SyncLevel, parentIds: Set<String>): Result<Unit>`:
    - Step Catalogs: `cursor = syncStateRepo.getCursor("catalogs")`; `changedIds = catalogRepo.refreshFromRemote()` returns changed catalog ids (as Set<String>); on success: setCursor handled by CatalogRepositoryImpl; collect `changedCatalogIds = ids with cv grew`
    - Step Quests: `cursor = syncStateRepo.getCursor("quests")`; `changedQuestIds = questRepo.refreshFromRemote(currentUserUid, availableShelves, changedCatalogIds.map{CatalogId(it)}.toSet(), cursor)` → on success `setCursor("quests", newQuestCursor)` (orchestrator sets quest cursor since QuestRepo returns IDs not manages cursor itself); recurse into sections if changedQuestIds non-empty
    - Steps Section/Theme/Lesson/Question: analogous with `refreshByParents(parentIds, cursor)`, setCursor on success
  - Batch splitting: before each `refreshByParents` call, split `parentIds` into chunks ≤30 and call multiple times, merging results
  - `currentUserUid = authRepo.currentUid()` called once at top of `sync()`
  - `availableShelves = setOf("home", "arena")` — MVP hard-coded constant
  - Early exit: if `changedParentIds.isEmpty()` at any level → stop cascade, return `Result.success(Unit)`
  - On any step failure: return `Result.failure(exception)` immediately (no retry at orchestrator level — WorkManager handles retry)
- **Edge cases:**
  - `authRepo.currentUid() == null` → guest mode: questRepo.refreshFromRemote called with `currentUserUid=null` → QuestRepositoryImpl skips Query A
  - Cursor for Quest level: QuestRepositoryImpl.refreshFromRemote takes explicit `cursor` param (not internal SyncStateRepository read) — orchestrator reads cursor, passes it, THEN calls setCursor on success. IMPORTANT: This differs from Catalog (where CatalogRepositoryImpl reads cursor internally). Consistency check required.
  - **RESOLUTION**: Per `06-api-contract.md §2.2`, QuestRepository.refreshFromRemote takes explicit `cursor: Long` param. But CatalogRepository.refreshFromRemote does NOT take cursor (reads internally per `06-api-contract.md §2.1`). Orchestrator only calls `setCursor("quests",...)` after quest step success.
  - `changedQuestIds` = IDs returned by `refreshFromRemote` where `contentsVersion grew` — this is the Result<Set<QuestId>> return value
  - For Question leaf: `refreshByParents` returns `Result<Unit>` — no cascade further
- **Depends on:** SyncLevel (task #1), all 6 Repository interfaces (domain walking skeleton + phase-02 impls via DI), SyncStateRepository, AuthRepository
- **Canonical reference:** `06-api-contract.md` §4 CascadingSyncOrchestrator; `03-decisions.md` ADR-CMP-49

---

## 3. Update SyncModule.kt

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt`
- **Тип:** Koin module update
- **Сигнатура:** `val syncModule = module { ... }` — обновлённый
- **Вход:** существующий syncModule с `List<Syncable>` + SyncStateRepository (добавлен phase-02)
- **Поведение / Выход (SSoT: `06-api-contract.md:787-797`):**
  - Добавить: `single<CascadingSyncOrchestrator> { CascadingSyncOrchestrator(get(), get(), get(), get(), get(), get(), get(), get(), get()) }` — без named qualifier (per SSoT)
  - Обновить `single<List<Syncable>>`: заменить `get<CatalogRepository>() as Syncable` на `get<CascadingSyncOrchestrator>()`
  - Результат:
    ```
    single<SyncStateRepository> { InMemorySyncStateRepository() }
    single<CascadingSyncOrchestrator> { CascadingSyncOrchestrator(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<List<Syncable>> { listOf(get<UserStatsRepository>() as Syncable, get<CascadingSyncOrchestrator>()) }
    ```
  - Добавить импорты: CascadingSyncOrchestrator, all 5 repo interfaces (quest/section/theme/lesson/question), AuthRepository
- **Edge cases:**
  - `AuthRepository` должен быть в Koin graph к этому моменту — `appShellDataModule` регистрирует AuthRepositoryImpl (phase-04). Если phase-04 не выполнена → Koin graph fail at runtime. JVM тесты не зависят от Koin.
  - Нет `named` qualifier — `single<CascadingSyncOrchestrator>` is unambiguous (only one concrete type)
  - `CascadingSyncOrchestrator` implements `Syncable` — `get<CascadingSyncOrchestrator>()` в `List<Syncable>` корректно через polymorphic resolution
- **Depends on:** phase-02 (all Repository bindings), phase-04 (AuthRepository binding — runtime dependency)
- **Canonical reference:** `06-api-contract.md:787-797` syncModule SSoT; `03-decisions.md` ADR-CMP-49 Consequences

---

## Validation Checklist

```bash
# JVM tests (no Koin dependency)
./gradlew :shared:core:sync:jvmTest

# Full build (Koin graph validated at runtime — requires phase-04 complete)
./gradlew assemble
./gradlew allTests
```
