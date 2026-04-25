---
date: 2026-04-22
feature: home-and-my-quests
author: architect-high-level
---

# Behavior: Home Quests & My Quests + Cascading Catalog Sync

## DFD 1 — Cascading Sync Flow (6 levels)

От `SyncWorker.doWork()` до Room через `CascadingSyncOrchestrator.syncCascade(SyncLevel.Catalog, emptySet())`.

```mermaid
sequenceDiagram
    participant WM as WorkManager
    participant SW as SyncWorker
    participant CSO as CascadingSyncOrchestrator
    participant SSR as SyncStateRepository
    participant CR as CatalogRepository
    participant QR as QuestRepository
    participant SR as SectionRepository
    participant Room as Room DB

    WM->>SW: doWork()
    SW->>CSO: sync() [implements Syncable]
    CSO->>CSO: syncCascade(SyncLevel.Catalog, parentIds=∅)

    Note over CSO,SSR: STEP 1 — Catalogs
    CSO->>SSR: getCursor("catalogs") → catalogCursor
    CSO->>CR: refreshFromRemote()  [reads cursor internally via getCursor; does NOT set cursor]
    CR->>SSR: getCursor("catalogs") → catalogCursor [internally, read only]
    CR->>Room: upsertByIdIfNewerVersion(entity) OR deleteById(id) if archived=true
    Note over CSO,SSR: Cursor advanced by orchestrator after subtree success (B2 fix)
    CSO->>CSO: changedCatalogs = [catalogs where dto.cv > local.cv]

    alt changedCatalogs non-empty
        Note over CSO,SSR: STEP 2 — Quests (dual query A+B, batch ≤30)
        CSO->>SSR: getCursor("quests") → questCursor
        CSO->>QR: refreshFromRemote(currentUserUid, availableShelves, changedCatalogIds, questCursor)
        Note right of QR: Query A: authorUid==me & catalogId IN changedCatalogIds & lastMod > cursor<br/>Query B: visibleOn array-contains-any shelves & lastMod > cursor<br/>Merge+dedupe by id client-side
        QR->>Room: upsert OR delete (archived=true OR visibleOn.isEmpty)
        QR->>SSR: setCursor("quests", newCursor)
        CSO->>CSO: changedQuests = [quests where dto.cv > local.cv]

        alt changedQuests non-empty
            Note over CSO,SSR: STEP 3 — Sections
            CSO->>SSR: getCursor("sections") → secCursor
            CSO->>SR: refreshByParents(changedQuestIds, cursor=secCursor)
            SR->>Room: upsert OR delete per Matrix 2
            SR->>SSR: setCursor("sections", newCursor)
            Note over CSO: STEP 4 Themes → STEP 5 Lessons → STEP 6 Questions — same pattern<br/>Each recurses only where contentsVersion grew (Matrix 3)
        end
    end

    CSO-->>SW: Result.success()
    SW-->>WM: Result.success()
```

**Cursor management rules (spec FR#14):**
- `newCursor = Clock.System.now()` (sampled once as `freshTime` at cascade entry — uniform for all 6 levels)
- `setCursor` вызывается **только при успешном завершении** каждого Step
- При ошибке Step — Worker возвращает `Result.retry()`; курсор остаётся прежним → следующий retry повторит тот же Step
- `InMemorySyncStateRepository` — курсоры живут в памяти процесса; process death → все курсоры сбрасываются в 0 → следующий sync начинает с нуля (приемлемо MVP, upsert-by-id идемпотентен)

---

## DFD 2 — HomeQuests Screen Data Flow

```mermaid
flowchart LR
    Room[(Room DB\ncatalogs table\nWHERE archived=0)] -->|Flow<List<Catalog>>| CatRepo[CatalogRepositoryImpl\nobserveAll]
    CatRepo -->|Flow<List<Catalog>>| ObsUC[ObserveCatalogsUseCase\noperator invoke]
    ObsUC -->|Flow<List<Catalog>>| HQC[HomeQuestsComponent\nDefaultHomeQuestsComponent\ncoroutineScope lifecycle]
    HQC -->|StateFlow<HomeQuestsUiState>| HQS[HomeQuestsScreen\nComposable]
    HQS --> CG[CatalogGrid\ntitleMedium bold\n16dp corners\n12dp gap]
    CG -->|AsyncImage URL| Coil[Coil 3.4.0\nURL-based cache\n?v=version suffix]
    CG -->|onClick| PH[TODO placeholder\nfuture catalog detail]

    SWK[SyncWorker\nperiodic+manual] -->|upsert Room| Room
```

**Ключевые инварианты:**
- `CatalogRepository.observeAll()` → DAO query `SELECT * FROM catalogs WHERE archived = 0` (Decision #52)
- `HomeQuestsComponent` заменяет `AppShellScreen.CatalogGridSection` — pre-existing нарушение `use-cases.md` устраняется (Decision #51)
- Экран показывает **каталоги**, не квесты (Decision #32 / Codex fix #1)

---

## DFD 3 — MyQuests Screen Data Flow

```mermaid
flowchart LR
    subgraph "Auth source"
        AA[AppApplication.kt:41\nauthUidFlow\ncallbackFlow AuthStateListener]
        AR[AuthRepository\nAuthRepositoryImpl\nobserveUid: Flow<String?>]
        AA --> AR
    end

    subgraph "Quest source"
        Room2[(Room DB\nquests table\nWHERE archived=0\nauthorUid=? catalogId=?)]
        QR[QuestRepositoryImpl\nobserveMyQuests\nauthorUid: String\ncatalogId: CatalogId?]
        Room2 --> QR
    end

    subgraph "Catalog spinner source"
        Room3[(Room DB\ncatalogs table\nWHERE archived=0)]
        CatR[CatalogRepositoryImpl\nobserveAll]
        Room3 --> CatR
    end

    AR -->|Flow<String?>| MQC[MyQuestsComponent\nDefaultMyQuestsComponent\ncombine uid+catalogId+quests]
    QR -->|Flow<List<Quest>>| MQC
    CatR -->|Flow<List<Catalog>>| MQC

    MQC -->|StateFlow<MyQuestsUiState>| MQS[MyQuestsScreen\nComposable]
    MQS --> CS[CatalogSpinner\nExposedDropdownMenuBox\n"Все категории" pseudo-item]
    MQS --> LC[LazyColumn\nQuestCard × N]
    MQS --> FAB[FAB "+"\nonCreateQuestClick]
    MQS --> ES[Empty State\nif quests.isEmpty\nIcon + text + arrow to FAB]

    FAB -->|onCreateQuestClick| Nav[Navigator.goTo\nDestination.OpenQuestCreate]
    LC --> QC[QuestCard\ntitle + StarRating + AsyncImage]

    CS -->|onSelectionChanged(catalogId?)| MQC
```

**Guest behavior (Decision #39, Journey 11):**
- `AuthRepository.observeUid()` эмитит `null` → `MyQuestsUiState(quests=emptyList, isGuest=true)`
- `QuestRepository.observeMyQuests(...)` **не вызывается** при `uid == null`
- Empty state одинаков для guest и для authenticated-0-quests (без login CTA)
- Mid-session login/logout: `combine(uidFlow, catalogFlow)` реактивно обновляет state

---

## DFD 4 — Cursor Management (SyncStateRepository seam)

```mermaid
stateDiagram-v2
    [*] --> Initial: first sync / process restart

    Initial: cursor = 0 for all collections
    
    Initial --> CatalogFetch: SyncWorker starts
    CatalogFetch: getCursor("catalogs") → 0\nfetch WHERE lastModifiedAt > 0

    CatalogFetch --> CatalogSuccess: Firestore returns N docs
    CatalogFetch --> RetryWait: Firestore error

    CatalogSuccess: upsert/delete in Room\n[cursor set by orchestrator after subtree, not here]
    CatalogSuccess --> QuestFetch: if changedCatalogs with cv > local.cv

    QuestFetch: getCursor("quests") → prev value\nfetch WHERE lastModifiedAt > cursor
    QuestFetch --> QuestSuccess: Firestore returns docs
    QuestFetch --> RetryPartial: Firestore error

    QuestSuccess: upsert/delete\nsetCursor("quests", newCursor)
    QuestSuccess --> SectionFetch: if changedQuests with cv > local.cv

    RetryPartial: catalogCursor ADVANCED\nquestCursor UNCHANGED\nworker returns Result.retry()
    RetryPartial --> RetryWait

    RetryWait: WorkManager exponential backoff\n(default: 30s → 5min cap)
    RetryWait --> CatalogFetch: worker retried\n(cursor for catalogs = prev max → less to fetch)

    SectionFetch --> ThemeFetch: repeat pattern
    ThemeFetch --> LessonFetch
    LessonFetch --> QuestionFetch
    QuestionFetch --> Done

    Done: all cursors advanced\nResult.success()
    Done --> [*]
```

**Phase-01 constraint**: `InMemorySyncStateRepository` — курсоры в `MutableStateFlow<Map<String,Long>>` + `Mutex`. Process death = reset to 0. Это **acceptable MVP** (spec FR#14, Decision #36).

---

## DFD 5 — FAB Navigation (Destination.OpenQuestCreate → QuestCreateRoot)

```mermaid
sequenceDiagram
    participant U as User
    participant MQS as MyQuestsScreen
    participant MQC as MyQuestsComponent
    participant Nav as Navigator
    participant DRC as DefaultRootComponent
    participant AST as AppShellTransitions
    participant ASS as AppShellScreen

    U->>MQS: tap FAB "+"
    MQS->>MQC: onCreateQuestClick()
    MQC->>Nav: goTo(Destination.OpenQuestCreate)
    Nav->>DRC: onDestination(OpenQuestCreate)
    DRC->>AST: navigate(state, OpenQuestCreate)

    alt state.localState.stack.active == QuestCreateRoot
        Note over AST: GUARD (Decision #47): already on screen → no-op
        AST-->>DRC: state unchanged
    else
        AST->>AST: onOpenQuestCreate(state)
        Note over AST: push QuestCreateRoot ON TOP of current local stack<br/>(NOT replace like OpenDesignCatalog — Decision #45)
        AST-->>DRC: new state: stack=[MyQuestsRoot], active=QuestCreateRoot
        DRC->>DRC: syncStack(newState) → Decompose nav.navigate()
        DRC->>ASS: recompose LocalTabContent
        ASS->>ASS: when(QuestCreateRoot) → UnderConstructionScreen("Создание квеста в разработке")
    end

    U->>U: sees UnderConstructionScreen
    U->>U: taps Back
    Note over DRC: BackCallback → Destination.Back → NavStack.pop() → QuestCreateRoot removed
    DRC->>ASS: recompose → MyQuestsScreen restored
```

**Navigation constraints (Decisions #41, #45, #47, #48):**
- `Destination.OpenQuestCreate` — `data object`, аналог именования `OpenDesignCatalog`
- Семантика: **push** (не replace) — `[MyQuestsRoot, QuestCreateRoot]`; Back → `[MyQuestsRoot]`
- Guard: если `active == QuestCreateRoot` → no-op (повторный tap FAB)
- `Destination.kt` + `AppShellTransitions.kt` меняются **атомарно** (Decision #48) — добавление sealed variant ломает exhaustive when

---

## DFD 6 — MyQuestsComponent Lifecycle (Decompose + reactive auth)

```mermaid
flowchart TD
    CC[ComponentContext\nprovided by parent DefaultLocalTabComponent] --> MQC_INIT[DefaultMyQuestsComponent\ninit block]
    MQC_INIT --> SCOPE[coroutineScope\nDispatchers.Main.immediate\n+ essenty lifecycle]
    
    SCOPE --> COMBINE[combine:\nuidFlow = authRepo.observeUid\ncatalogIdFlow = MutableStateFlow&ltCatalogId?&gt\n]
    
    COMBINE -->|uid=null| GUEST_STATE[MyQuestsUiState\nquests=emptyList\nisGuest=true\n— no repo call]
    COMBINE -->|uid=String| QUEST_FLOW[QuestRepository\nobserveMyQuests\nauthorUid, catalogId?]
    
    QUEST_FLOW --> COMBINED_STATE[combine quests + catalogs\n→ MyQuestsUiState\nquests, catalogs, selectedCatalog, isGuest=false]
    GUEST_STATE --> STATE_FLOW
    COMBINED_STATE --> STATE_FLOW[StateFlow&ltMyQuestsUiState&gt]
    
    SCOPE --> CAT_FLOW[CatalogRepository\nobserveAll → catalogs for spinner]
    CAT_FLOW --> COMBINED_STATE
    
    STATE_FLOW -->|collected by| MQS[MyQuestsScreen\nComposable]

    MQC_INIT -->|onCatalogSelected| CATALOG_ID_UPDATE[catalogIdFlow.value = selectedId]
    CATALOG_ID_UPDATE --> COMBINE

    LIFECYCLE[Decompose Lifecycle\nonStop → scope cancelled\nonStart → scope recreated] --> SCOPE
```

**Decision #51**: Decompose `Component` (не AndroidX ViewModel) — consistent с existing `DefaultRootComponent`. Lifecycle через essenty, не `viewModelScope`. State retention через `StateKeeper` при конфигурационных изменениях.

---

## State Matrix Summary

Полные матрицы в `0-spec.md § State Matrix`. Краткое резюме для ориентации:

| Matrix | Применимость | Ключевое правило |
|--------|-------------|-----------------|
| Matrix 1 | Catalog, Quest | `archived=true` ИЛИ `visibleOn.isEmpty()` → DELETE local; иначе upsert если `dto.v > local.v`; skip если `dto.v <= local.v` |
| Matrix 2 | Section, Theme, Lesson, Question | `archived=true` → DELETE local; иначе upsert если `dto.v > local.v` |
| Matrix 3 | Cascade recurse predicate | Идти на уровень N+1 **только если** `dto.contentsVersion > local.contentsVersion` |
| Matrix 4 | Visibility filter | MVP: `availableShelves = {"home", "arena"}`; tournament — future gating |

---

## Error Recovery Summary

| Scenario | Behavior |
|---------|----------|
| Network error в любом Step | `Result.retry()` → WorkManager exponential backoff; курсор шага не обновляется |
| Firestore permission denied | `Result.failure()` без retry (permanent auth error) |
| Partial cascade fail (Step 1 OK, Step 2 fail) | Step 1 курсор advanced; Step 2 курсор unchanged; retry повторяет с Step 1 (дешёво: меньше данных за новый cursor); уже upserted → no-op |
| Process death mid-cascade | InMemory потерян → cursor=0 → full re-sync → upsert-by-id идемпотентен |
| `in`-filter >30 parent-ids | Client batches chunks по 30; параллельные запросы; merge+dedupe на клиенте |
| Malformed DTO (missing required field) | Mapper возвращает `null`; запись пропускается; warning logged |

---

## State Matrix Extension (architect-component) — Edge Cases + Code Locations

### Matrix 1 Extended: Catalog / Quest — with code locations and testability

Spec State Matrix 1 (`0-spec.md:1045`) — расширена edge cases и маппингом на code.

| # | local state | dto.version | delete-marker | Action | Code location | Testable? | Test scenario |
|---|------------|-------------|---------------|--------|---------------|-----------|--------------|
| 1.1 | absent | any | `archived=true` OR `visibleOn.isEmpty()` | SKIP (no insert) | `CatalogRepositoryImpl.refreshFromRemote` + `QuestRepositoryImpl.refreshFromRemote` — Matrix 1 block | ✅ JVM | implicit in scenarios 23, 25 |
| 1.2 | absent | any | false / non-empty | INSERT via `upsertByIdIfNewerVersion` | `CatalogDao.upsertByIdIfNewerVersion` | ✅ JVM | 21, 24, 44 |
| 1.3 | present, `local.v < dto.v` | > local | delete-marker=true | DELETE local via `deleteById` | `CatalogDao.deleteById` / `QuestDao.deleteById` | ✅ JVM | 23, 25, 43 |
| 1.4 | present, `local.v < dto.v` | > local | false / non-empty | UPSERT via `upsertByIdIfNewerVersion` | `*Dao.upsertByIdIfNewerVersion` | ✅ JVM | 22, 44 |
| 1.5 | present, `local.v == dto.v` | == local | any | SKIP (up-to-date) | `upsertByIdIfNewerVersion` — SQL `WHERE NOT EXISTS (... version >= :version)` | ✅ JVM | 22, 46 |
| 1.6 | present, `local.v > dto.v` | < local | any | SKIP (server stale) | same SQL guard | ✅ JVM | 47 |
| **EDGE 1.7** | present | dto.v > local.v | `archived=true` BUT dto.v < local.v (race: stale tombstone) | SKIP — version check comes FIRST | `QuestRepositoryImpl.kt` — version check before archived check | ✅ JVM | 23b (version guard for delete) |
| **EDGE 1.8** | absent | any | `visibleOn=["home"]` | INSERT (non-empty visibleOn → not delete marker) | `QuestRepositoryImpl` | ✅ JVM | 24 |
| **EDGE 1.9** | present | dto.v > local.v | `visibleOn=[]` (empty set, authorUid=me) | DELETE (draft → physical delete, Decision #48) | `QuestRepositoryImpl` | ✅ JVM | 48 |
| **EDGE 1.10** | absent | any | `archived=false`, `picturePath` non-null | INSERT + resolve `pictureUrl` via `StorageUrlResolver` + append `?v=$version` | `QuestRepositoryImpl.refreshFromRemote` | ✅ JVM (FakeStorageUrlResolver) | — |

---

### Matrix 2 Extended: Section / Theme / Lesson / Question — with edge cases

| # | local state | dto.version | dto.archived | Action | Code location | Testable? | Test scenario |
|---|------------|-------------|--------------|--------|---------------|-----------|--------------|
| 2.1 | absent | any | true | SKIP | `*RepositoryImpl` Matrix 2 block | ✅ JVM | implicit |
| 2.2 | absent | any | false | INSERT | `*Dao.upsertByIdIfNewerVersion` | ✅ JVM | 41 |
| 2.3 | present, `local.v < dto.v` | > local | true | DELETE | `*Dao.deleteById` | ✅ JVM | 43 |
| 2.4 | present, `local.v < dto.v` | > local | false | UPSERT | `*Dao.upsertByIdIfNewerVersion` | ✅ JVM | 42, 44 |
| 2.5 | present, `local.v == dto.v` | == local | any | SKIP | SQL `NOT EXISTS version >= :v` | ✅ JVM | 46 |
| 2.6 | present, `local.v > dto.v` | < local | any | SKIP (stale) | same | ✅ JVM | 47 |
| **EDGE 2.7** | present | dto.v > local.v | archived=true | DELETE — Question has no contentsVersion → no recurse after delete | `QuestionRepositoryImpl` | ✅ JVM | 43 (question analog) |
| **EDGE 2.8** | present | dto.v > local.v | archived=false, order updated | UPSERT → downstream DAO query `ORDER BY order ASC` reflects change | `SectionDao.observeByQuest` | ✅ JVM | implicit in 42 |

---

### Matrix 3 Extended: Cascade Recurse Predicate — with code locations

Pure function `shouldRecurseIntoChildren` in `CascadeDecision.kt:46` — canonical implementation.

| # | parent state | dto.cv | local.cv | Result | Code location | Testable? | Test scenario |
|---|-------------|--------|----------|--------|---------------|-----------|--------------|
| 3.1 | just inserted (absent before sync) | > 0 | null | RECURSE | `shouldRecurseIntoChildren(dto.cv, null)` → `dto.cv > 0` | ✅ JVM | implicit in 41 |
| 3.2 | just inserted | == 0 | null | STOP (leaf content, no children) | `dto.cv > 0` → false | ✅ JVM | `CascadeDecisionTest.kt` |
| 3.3 | present, upserted | > local.cv | non-null | RECURSE | `dto.cv > localCv` | ✅ JVM | 30, 45 |
| 3.4 | present, upserted | == local.cv | non-null | STOP | `dto.cv > localCv` → false | ✅ JVM | 28, 45 (inverse) |
| 3.5 | present, skipped (version equal) | any | non-null | STOP — skip means parent unchanged, no recurse | CascadingSyncOrchestrator: if SKIP → don't add to changedParentIds | ✅ JVM | 29 |
| **EDGE 3.6** | present, skipped (version older) | any | non-null | STOP — server stale, don't recurse | same | ✅ JVM | implied by 47 |
| **EDGE 3.7** | present | dto.cv > local.cv | non-null | RECURSE — но если changedQuestIds.isEmpty() | early-exit in `CascadingSyncOrchestrator.syncCascade` → `if (changedParentIds.isEmpty()) return success` | ✅ JVM | 10 (AC) |
| **EDGE 3.8** | batch of 31+ parent-ids | N/A | N/A | BATCH SPLIT → parallel Firebase requests, merge+dedupe | `CascadingSyncOrchestrator` chunkedBy(30) | ✅ JVM (FakeRemote with 31 items) | 50 |

---

### Matrix 4 Extended: Visibility Filter — with future rows marked

| # | user role | availableShelves | Query B shape | phase | Testable? |
|---|-----------|------------------|---------------|-------|-----------|
| 4.1 | baseline (MVP) | `{"home", "arena"}` | `visibleOn array-contains-any ["home", "arena"]` + `lastModifiedAt > cursor` | **phase-01** | ✅ JVM (FakeQuestRemoteDataSource) |
| 4.2 | + tournament qual | `{"home", "arena", "tournament"}` | extend array | **future** | N/A phase-01 |
| 4.3 | + finalist qual | `{"home", "arena", "tournament", "tournamentFinal"}` | extend array | **future** | N/A |
| 4.4 | admin | all 5 shelves | `["home","arena","tournament","tournamentFinal","archive"]` | **future** | N/A |
| **EDGE 4.5** | baseline, `availableShelves` computed from `UserStats.qualification` | MVP: hardcoded `{"home","arena"}` per spec FR#15 | N/A | phase-01 | ✅ JVM — test that `availableShelves` arg passed to `QuestRepository.refreshFromRemote` equals `{"home","arena"}` |
| **EDGE 4.6** | guest (uid=null) | N/A — Query A skipped entirely | only Query B with shelves | phase-01 | ✅ JVM (FakeAuth with null, verify Query A not called) |

---

## Sequence Diagrams (architect-component)

### SEQ-1: Guest → Mid-Session Login → Quest List Appears

```mermaid
sequenceDiagram
    participant AR as AuthRepository
    participant MQC as DefaultMyQuestsComponent
    participant QR as QuestRepository
    participant Room as Room DB
    participant MQS as MyQuestsScreen

    Note over AR: observeUid emits null (guest)
    AR->>MQC: null (via combine)
    MQC->>MQS: MyQuestsUiState(quests=[], isGuest=true)
    Note over MQC: QuestRepository.observeMyQuests NOT called

    Note over AR: User logs in → Firebase Auth emits uid
    AR->>MQC: "user-uid-123" (via AuthStateListener)
    MQC->>QR: observeMyQuests("user-uid-123", catalogId=null)
    QR->>Room: SELECT * FROM quests WHERE authorUid=? AND archived=0
    Room-->>QR: [Quest1, Quest2]
    QR-->>MQC: Flow emits [Quest1, Quest2]
    MQC->>MQS: MyQuestsUiState(quests=[Item1, Item2], isGuest=false)

    Note over AR: User logs out
    AR->>MQC: null
    Note over MQC: flatMapLatest cancels questRepo flow (no leak — AC 45c)
    MQC->>MQS: MyQuestsUiState(quests=[], isGuest=true)
```

**Code location**: `DefaultMyQuestsComponent.state`:
```kotlin
override val state: StateFlow<MyQuestsUiState> =
    authRepo.observeUid()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(MyQuestsUiState(quests = emptyList(), isGuest = true))
            } else {
                combine(
                    observeMyQuests(uid, selectedCatalog.value),
                    observeCatalogs(),
                    selectedCatalog,
                ) { quests, catalogs, catalogId ->
                    MyQuestsUiState(
                        quests = quests.map { it.toDisplayItem() },
                        catalogs = catalogs.map { it.toDisplayItem() },
                        selectedCatalogId = catalogId,
                        isGuest = false,
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MyQuestsUiState(isGuest = false))
```

---

### SEQ-2: Cursor Lifecycle — Successful Cascade

```mermaid
sequenceDiagram
    participant CSO as CascadingSyncOrchestrator
    participant SSR as SyncStateRepository
    participant CR as CatalogRemoteDataSource
    participant QR as QuestRemoteDataSource

    Note over SSR: Initial state: all cursors = 0

    CSO->>SSR: getCursor("catalogs") → 0L
    CSO->>CR: fetchChangedSince(cursor=0L)
    CR-->>CSO: [Catalog(id=c1, lastModifiedAt=1000, cv=3, v=1)]

    Note over CSO: catalog c1: local.cv=0 < dto.cv=3 → RECURSE
    CSO->>SSR: getCursor("quests") → 0L
    CSO->>QR: fetchMyQuestsInCatalogs(uid, [c1], 0L)
    CSO->>QR: fetchPublicQuestsForShelves(["home","arena"], 0L)
    QR-->>CSO: merge([Quest(id=q1, lastModifiedAt=2000, cv=1)], deduped)

    Note over CSO: quest q1: local.cv=null (absent) + dto.cv=1 > 0 → RECURSE
    CSO->>SSR: getCursor("sections") → 0L
    Note over CSO: ... continues through Section→Theme→Lesson→Question
    Note over CSO,SSR: After full subtree success — orchestrator advances ALL cursors to freshTime
    CSO->>SSR: setCursor("catalogs", freshTime)
    CSO->>SSR: setCursor("quests", freshTime)
    CSO->>SSR: setCursor("questions", freshTime)  [and sections/themes/lessons similarly]
```

---

### SEQ-3: Partial Fail — questsCursor NOT advanced

```mermaid
sequenceDiagram
    participant CSO as CascadingSyncOrchestrator
    participant SSR as SyncStateRepository
    participant QR as QuestRemoteDataSource

    Note over SSR: catalogsCursor=1000L (step 1 already completed in earlier run)

    CSO->>SSR: getCursor("catalogs") → 1000L
    Note over CSO: Step 1: catalogs since 1000L → nothing new → changedCatalogs=[]
    Note over CSO: changedCatalogs=[] → subtree empty → orchestrator advances catalog cursor
    CSO->>SSR: setCursor("catalogs", freshTime) [Clock.System.now() — uniform with all levels]

    alt changedCatalogs non-empty (different run where catalog was updated)
        CSO->>SSR: getCursor("quests") → 0L
        CSO->>QR: fetch (two queries)
        QR-->>CSO: FirestoreException (network timeout)
        Note over CSO: onFailure → return Result.failure()
        Note over SSR: questsCursor UNCHANGED (0L) — setCursor NOT called on failure
        CSO-->>CSO: propagate Result.retry()
    end

    Note over CSO: AC#54: catalogsCursor advanced, questsCursor not
```

**Test**: AC#54 — `CascadingSyncOrchestratorTest`:
```kotlin
@Test fun `when step 2 fails then questsCursor not advanced`() = runTest {
    fakeSyncStateRepo.setCursor("catalogs", 1000L)
    fakeQuestRemote.setNextFetchFailure(IOException("timeout"))
    // ... trigger sync with catalog that has cv change
    assertThat(fakeSyncStateRepo.getCursor("quests")).isEqualTo(0L)  // unchanged
    assertThat(fakeSyncStateRepo.getCursor("catalogs")).isEqualTo(1000L) // unchanged since no new catalogs
}
```

---

### SEQ-4: Merge/Dedupe — Query A + Query B

```mermaid
sequenceDiagram
    participant QRI as QuestRepositoryImpl
    participant DS_A as FirebaseQuestRemoteDataSource
    participant DS_B as FirebaseQuestRemoteDataSource

    Note over QRI: Two PARALLEL Firestore queries
    QRI->>DS_A: fetchMyQuestsInCatalogs(uid="me", catalogIds=[c1], cursor=0)
    QRI->>DS_B: fetchPublicQuestsForShelves(["home","arena"], cursor=0)
    DS_A-->>QRI: [Quest(id=q1, authorUid="me"), Quest(id=q2, authorUid="me")]
    DS_B-->>QRI: [Quest(id=q1, visibleOn=["home"]), Quest(id=q3, visibleOn=["arena"])]

    Note over QRI: Merge step: union by id
    Note over QRI: q1 appears in both → dedupe: keep first (same id = same entity)
    Note over QRI: final = [q1, q2, q3]

    QRI->>QRI: for each dto in [q1,q2,q3]: apply Matrix 1
    Note over QRI: q1(v=2) > local(v=1) → UPSERT
    Note over QRI: q2(v=1) absent → INSERT
    Note over QRI: q3(v=1) absent → INSERT

    Note over QRI: AC#48: dedupe by id → exactly 1 room row per quest
```

**Code pattern**:
```kotlin
// QuestRepositoryImpl.refreshFromRemote
val queryAResult = async { remote.fetchMyQuestsInCatalogs(currentUserUid, catalogIdsToSync, cursor) }
val queryBResult = async { remote.fetchPublicQuestsForShelves(availableShelves, cursor) }

val mergedById: Map<String, QuestDto> = buildMap {
    queryBResult.await()
        .filter { it.catalogId in catalogIdsToSync.map { id -> id.value } }  // local filter for B
        .forEach { put(it.id, it) }
    queryAResult.await()
        .forEach { put(it.id, it) }  // A overwrites B (same id = same dto)
}
// apply Matrix 1 to mergedById.values
```
