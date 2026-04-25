---
date: 2026-04-20
feature: menu-refactor
type: behavior
layer: high-level (DFD)
author: architect-high-level
---

# Behavior: Menu Refactor — Data Flow Diagrams

> **⚠️ TARGET STATE.** Этот документ описывает data flows **после phase-01 implementation** фичи `menu-refactor`. Все DFD — целевые потоки данных, не current state. Для текущего состояния см. `1-research.md` + `2-grounding.md`.

Этот документ описывает 6 ключевых data flow для feature. State Matrix взяты из spec (не переписываются), здесь — маппинг на реальные file:line.

---

## Flow 1: 10-Tap Dev Mode Activation

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant U as User
    participant DF as DrawerFooter.kt
    participant ARG as RegisterTap.kt (pure fn)
    participant DRC as DefaultRootComponent.kt
    participant UC as ActivateDevModeUseCase
    participant REPO as UserStatsRepositoryImpl
    participant DAO as UserStatsDao (Room)
    participant AS as AppShellScreen.kt
    participant SB as SnackbarHostState

    U->>DF: tap v$versionName (×10, each < 500ms)
    DF->>ARG: registerTap(progress, nowMillis, currentDeveloper)
    Note over ARG: pure FSM — см. State Matrix ниже
    ARG-->>DF: TapResult.Activated (на 10-м тапе)
    DF->>DRC: onActivateDevMode()
    DRC->>UC: invoke(progress, nowMillis)
    UC->>REPO: setLocalDeveloperLevel(100)
    REPO->>DAO: UPDATE user_stats SET developer_level=100 WHERE uid=?
    DAO-->>REPO: Unit (Room write)
    REPO->>REPO: observeStats() Flow emits new UserStats(developer=100)
    REPO-->>DRC: via ObserveAppShellStateUseCase — _appShellState.update
    DRC-->>AS: appShellState Flow emits updated state
    AS->>AS: DrawerContent re-composes (all sections visible)
    DRC->>DRC: _events.trySend(RootEvent.DevModeActivated)
    AS->>SB: LaunchedEffect: showSnackbar("Режим разработчика включён")
```

**File:line mapping:**
- `DrawerFooter.kt:59` — `Text("v$versionName")` (сейчас без clickable, добавляется в implement)
- `RegisterTap.kt:34` — `fun registerTap(...)` (Walking Skeleton, pure function)
- `DefaultRootComponent.kt:56` — `MutableStateFlow<AppShellState>` (holds state)
- `ActivateDevModeUseCase` — `qualification:domain/dev_mode/use_case/`
- `UserStatsRepository.kt:15` — interface, method `setLocalDeveloperLevel` — **добавить**
- `AppShellScreen.kt:129` — `Scaffold` (добавить `snackbarHost` параметр)

---

## Flow 2: SyncWorker Periodic Pipeline (Dev Mode Auto-Deactivation)

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant WM as WorkManager
    participant SW as SyncWorker (platform:android-services)
    participant REPO as UserStatsRepositoryImpl
    participant FB as FirebaseUserStatsDataSource
    participant FS as Firestore users/{uid}
    participant DAO as UserStatsDao (Room)

    WM->>SW: doWork() [periodic, every 1 day by default]
    SW->>SW: for each Syncable.sync() [via Koin list<Syncable>]
    SW->>REPO: refreshProfile(): Result<Unit>
    REPO->>FB: fetch() — Firestore users/{uid}.get()
    FS-->>FB: RawUserStats (developer=0)
    FB-->>REPO: RawUserStats
    REPO->>REPO: map RawUserStats → UserStatsEntity (developer_level=0)
    REPO->>DAO: UPDATE user_stats SET developer_level=0, ...all fields
    DAO-->>REPO: Unit (Room overwrite)
    REPO->>REPO: observeStats() Flow emits UserStats(developer=0)
    Note over REPO: dev mode автоматически деактивирован\n— нет явного "deactivate" call
```

**File:line mapping:**
- `SyncWorker.kt` — NEW, в `platform/android-services/` (WorkManager `CoroutineWorker`)
- `UserStatsRepository.kt:15` — добавить `suspend fun refreshProfile(): Result<Unit>`
- `UserStatsRepositoryImpl.kt:21` — существующая реализация, UPDATED для Room
- `FirebaseUserStatsDataSource.kt:28` — существующий `callbackFlow` → заменяется на одноразовый `get()` для refresh
- `UserStatsDao` — NEW в `core:persistence`
- `UserStatsEntity.kt` — NEW в `core:persistence` (см. spec `0-spec-dev-mode.md:39-65`)

**Deactivation invariant:** Нет явного вызова "deactivate overlay". Dev mode "выключается" потому что Room overwrite устанавливает `developer_level=0`, что совпадает с server value. `observeStats()` Flow эмитит UserStats с `developer=0`, и drawer re-renders.

---

## Flow 3: SyncNow Manual Trigger

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant U as User
    participant DF as DrawerFooter.kt
    participant DRC as DefaultRootComponent.kt
    participant WM as WorkManager
    participant AS as AppShellScreen.kt
    participant SB as SnackbarHostState
    participant SW as SyncWorker

    U->>DF: click DrawerFooterAction.SyncNow
    DF->>DRC: onSyncNow()
    DRC->>WM: enqueueUniqueWork("manual_sync", REPLACE, oneTimeSyncWorkRequest)
    DRC->>DRC: _events.trySend(RootEvent.SyncStarted)
    DRC-->>AS: events Flow emits RootEvent.SyncStarted
    AS->>SB: LaunchedEffect: showSnackbar("Синхронизация запущена")
    WM->>SW: doWork() [async, может быть после snackbar]
    Note over SW: Flow 2 (periodic) выполняется аналогично
```

**File:line mapping:**
- `DrawerFooter.kt:49-57` — `when (action)` — добавить `DrawerFooterAction.SyncNow` branch
- `RootComponent.kt:24` — добавить `fun onSyncNow()`
- `DefaultRootComponent.kt` — реализация `onSyncNow()` с WorkManager
- `AppShellScreen.kt` — добавить `LaunchedEffect` для `events` collection
- `RootEvent.kt:12` — добавить `data object SyncStarted : RootEvent`

---

## Flow 4: First-Launch Catalog Pull

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant VM as Screen ViewModel / Component
    participant UC as ObserveCatalogsUseCase
    participant REPO as CatalogRepositoryImpl
    participant LOCAL as CatalogLocalDataSource (Room)
    participant REMOTE as CatalogFirebaseDataSource (Firestore)
    participant FS as Firestore catalogs collection
    participant UI as CatalogGrid / CatalogSpinner

    VM->>UC: invoke()
    UC->>REPO: observeAll(): Flow<List<Catalog>>
    REPO->>LOCAL: observe() — Room Flow (empty initially)
    LOCAL-->>REPO: Flow<List<Catalog>> (emptyList)
    REPO-->>UC: emits emptyList
    UC-->>UI: loading state / empty state
    
    Note over VM: trigger first-fetch
    VM->>REPO: refreshFromRemote()
    REPO->>REMOTE: fetchAll()
    REMOTE->>FS: catalogs.get()
    FS-->>REMOTE: List<DocumentSnapshot>
    REMOTE->>REMOTE: map → List<Catalog> (sorted by id.value ASC)
    REMOTE-->>REPO: List<Catalog>
    REPO->>LOCAL: insertAll(catalogs)
    LOCAL-->>REPO: Unit
    REPO->>REPO: observeAll() Flow emits updated list
    REPO-->>UI: List<Catalog>(surveys, courses, games, school)
    UI->>UI: render CatalogGrid or CatalogSpinner
```

**File:line mapping:**
- `ObserveCatalogsUseCase.kt` — Walking Skeleton (`core:catalog:domain/use_case/`)
- `CatalogRepository.kt` — Walking Skeleton (`core:catalog:domain/repository/`)
- `CatalogRepositoryImpl.kt` — NEW (`core:catalog:data/`)
- `CatalogLocalDataSource.kt` — NEW (`core:catalog:data/`)
- `CatalogFirebaseDataSource.kt` — NEW (`platform:firebase/`)
- `CatalogSpinner.kt`, `CatalogGrid.kt` — NEW (`android:core:designsystem/`)

**Sorting invariant (Codex fix #6):** Список каталогов сортируется по `id.value ASC` на клиенте (в `CatalogRepositoryImpl`) после Firestore fetch. Гарантирует детерминизм для тестов и UI.

---

## Flow 5: Drawer Rendering with Superqualification

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant REPO as UserStatsRepositoryImpl (Room Flow)
    participant OUC as ObserveAppShellStateUseCase
    participant DRC as DefaultRootComponent._appShellState
    participant VIS as Visibility.isVisible()
    participant DC as DrawerContent.kt
    participant FOOTER as DrawerFooter.kt

    REPO->>REPO: Room emits UserStats(developer=100)
    REPO-->>OUC: observeStats() Flow
    OUC->>DRC: _appShellState.update { it.copy(userStats=...) }
    DRC-->>DC: appShellState (via AppShellScreen)
    DC->>VIS: visibleSections(Tab.LOCAL, stats)
    VIS->>VIS: isVisible(HomeQuests, stats) → developer>=100? YES → true (superqualification)
    VIS->>VIS: isVisible(MyQuests, stats) → developer>=100? YES → true
    VIS->>VIS: isVisible(Settings, stats) → emptyMap → always true
    VIS-->>DC: [HomeQuests, MyQuests, Settings]
    DC->>FOOTER: visibleFooterActions(isDebugBuild=false, stats)
    FOOTER->>FOOTER: developer>=LEVEL_1? YES → include DesignCatalog + SyncNow
    FOOTER-->>DC: [DesignCatalog, SyncNow, About]
```

**File:line mapping:**
- `Visibility.kt:50` — `isVisible()` — UPDATED (добавить OR-bypass для superqualification)
- `Visibility.kt:67` — `visibleSections(Tab.LOCAL)` — UPDATED (HomeQuests first)
- `Visibility.kt:142` — `visibleFooterActions()` — UPDATED signature + SyncNow
- `DrawerContent.kt:45` — caller передаёт `userStats` в `DrawerFooter`
- `AppShellState.kt:24` — `userStats: UserStats` уже присутствует

### State Matrix Map (Visibility)

Из `0-spec-dev-mode.md` Visibility matrix, mapped to `Visibility.kt:50`:

| Section requires | developer level | Result | Code path |
|---|---|---|---|
| `{D=100,T=100,...}` | ≥ LEVEL_1 (100) | **true** (superqualification) | `Visibility.kt:50` — OR-bypass (добавить) |
| `{D=100,T=100,...}` | < LEVEL_1 | depends on roles | `Visibility.kt:52` — existing AND-check |
| `{}` (empty) | any | **true** (always visible) | `Visibility.kt:50` — `all { }` on empty = true |

---

## Flow 6: Automatic Dev Mode Deactivation via Sync Overwrite

*Target flow — post phase-01 implementation.*

```mermaid
sequenceDiagram
    participant SW as SyncWorker.doWork()
    participant REPO as UserStatsRepositoryImpl
    participant DAO as UserStatsDao (Room)
    participant AS as AppShellScreen.kt
    participant VIS as Visibility.visibleFooterActions

    Note over SW: Dev mode active: Room.developer=100, Firestore.developer=0
    SW->>REPO: refreshProfile(): Result<Unit>
    REPO->>REPO: Firestore fetch → RawUserStats(developer=0)
    REPO->>DAO: UPDATE user_stats SET developer_level=0 (full overwrite)
    DAO-->>REPO: Unit
    REPO->>REPO: observeStats() emits UserStats(developer=0)
    REPO-->>AS: via ObserveAppShellStateUseCase → _appShellState.update
    AS->>AS: DrawerContent re-composes
    AS->>VIS: visibleFooterActions(isDebugBuild=false, stats.developer=0)
    VIS-->>AS: [About] only (DesignCatalog+SyncNow hidden)
    Note over AS: Dev mode silently deactivated — no snackbar\n(spec: "он сам отключется при синхронизации")
```

**Ключевой инвариант:** Нет отдельного "deactivate()" вызова. Deactivation — side effect стандартного sync overwrite. `refreshProfile()` перезаписывает **все** поля `UserStatsEntity`, включая `developer_level=0` (server value). Клиентский dev mode "исчезает" сам по себе.

**Error case:** Если `refreshProfile()` возвращает `Result.failure` (network error), Room НЕ обновляется, dev mode продолжает действовать до следующей успешной sync.

---

## State Matrix — Tap FSM (из spec, mapped to code)

Spec source: `0-spec-dev-mode.md` State Machine table.
Code: `RegisterTap.kt:34` (Walking Skeleton, file: `shared/feature/qualification/domain/...dev_mode/logic/RegisterTap.kt`).

| `progress.count` | elapsed vs 500ms | developer level | Output | Code return |
|---|---|---|---|---|
| 0 | N/A (first) | any | count=1, NoChange | `TapResult.NoChange` |
| 1..8 | ≤ 500ms | any | count+1, NoChange | `TapResult.NoChange` |
| 1..8 | > 500ms | any | count=1, Reset | `TapResult.Reset` |
| 9 | ≤ 500ms | < LEVEL_1.points | count=0, **Activated** | `TapResult.Activated` |
| 9 | ≤ 500ms | ≥ LEVEL_1.points | count=0, AlreadyDev | `TapResult.AlreadyDev` |
| 9 | > 500ms | any | count=1, Reset | `TapResult.Reset` |

`LEVEL_1.points = 100` — определяется в `QualificationLevel.kt` (moved to `core:foundation`).

---

## Footer Action Visibility Matrix (из spec, mapped to code)

Spec source: `0-spec-dev-mode.md` Footer Contract Full Matrix.
Code: `Visibility.visibleFooterActions()` (`Visibility.kt:142`) — signature UPDATED.

| `isDebugBuild` | `stats.developer` | Output | `visibleFooterActions(isDebugBuild, stats)` |
|---|---|---|---|
| true | any | `[DesignCatalog, SyncNow, About]` | debug bypass |
| false | ≥ 100 | `[DesignCatalog, SyncNow, About]` | superqualification |
| false | < 100 | `[About]` | no dev tools |

Order в output — всегда `[DesignCatalog?, SyncNow?, About]` (declaration order, filtered).
