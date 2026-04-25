---
date: 2026-04-20
feature: menu-refactor
type: decisions
layer: high-level (L1-L2 module boundaries)
author: architect-high-level
---

# Architectural Decisions: Menu Refactor

Только L1-L2 решения (модульные границы, инфраструктурный выбор, SDK). Решения на уровне классов и интерфейсов — в documents architect-component.

---

## ADR-HLA-01: QualificationLevel в `shared/core/foundation`

**Status:** Accepted (User Decision #1, 2026-04-20)

**Context:**
`spec-qualification-levels` AC #4 требует использовать `QualificationLevel.LEVEL_1.points` вместо magic number `100` в `DrawerSection.EventsSection.ActiveEvents.requiredRoles` (`DrawerSection.kt:100-103`). `DrawerSection.kt` принадлежит `feature:app-shell:domain`. Если `QualificationLevel` остаётся в `feature:qualification:domain`, то `app-shell:domain → qualification:domain` — прямой cross-feature import, запрещённый `clean-architecture.md`.

**Decision:**
Переместить `QualificationLevel.kt` и `QualificationLevelTest.kt` из `shared/feature/qualification/domain/src/commonMain/.../model/` в `shared/core/foundation/src/commonMain/.../`. Обновить package declaration: `package com.tpov.schoolquiz.shared.core.foundation`. Добавить `implementation(project(":shared:core:foundation"))` в `app-shell:domain/build.gradle.kts` и `qualification:domain/build.gradle.kts`.

**Consequences:**
- `app-shell:domain` и `qualification:domain` оба ссылаются на `core:foundation` — допустимо per clean-architecture (core = shared infra).
- `qualification:domain` сохраняет `dev_mode/` package (`TapProgress`, `RegisterTap`, `ActivateDevModeUseCase`).
- Walking Skeleton файл перемещается, не переписывается — допустимо per invariant #6.

**Alternatives Considered:**
- **Option B: Дублировать константу `100` в `app-shell:domain`** — Отклонено: нарушает spec AC #4 ("no magic numbers") и создаёт два источника правды для threshold value.
- **Option C: ADR-разрешение cross-feature import `app-shell:domain → qualification:domain`** — Отклонено: создаёт precedent размывания clean-architecture; потенциально приводит к bidirectional coupling в будущем (если qualification будет зависеть от app-shell через другой путь).

---

## ADR-HLA-02: Прямая запись `developer=100` в Room, без overlay entity

**Status:** Accepted (User Decision #2 — revert codex fix #2, 2026-04-20)

**Context:**
Walking Skeleton содержал `LocalDeveloperOverride` entity с `active: Boolean` + `activatedAtMillis: Long?`. Это "overlay" модель — разделение серверного значения и клиентского Dev Mode. Пользователь принял решение упростить: dev mode = прямая запись `developer=100` в local Room copy `UserStatsEntity`. Sync перезаписывает `developer=0` при следующем `refreshProfile()`.

**Decision:**
- **Удалить** из Walking Skeleton: `LocalDeveloperOverride.kt`, `DeveloperLevelStats.kt`, `EffectiveDeveloperLevel.kt`, `LocalDeveloperOverrideRepository.kt`, `FakeLocalDeveloperOverrideRepository.kt` и соответствующие тесты.
- `ActivateDevModeUseCase` принимает `UserStatsRepository` вместо `overlayRepo`. При `TapResult.Activated` вызывает `userStatsRepository.setLocalDeveloperLevel(100)`.
- `isVisible()` читает `stats.qualification.developer` напрямую. Нет `effectiveDeveloperLevel` merge.
- Эксплицитное исключение от ADR-0006 "клиент не пишет в profile": **только** поле `developer` в local Room cache может иметь client-written value. Все остальные поля — строго server-synced.

**Consequences:**
- Модель проще: нет отдельного Storage для overlay, нет merge logic.
- Dev mode persists между рестартами (Room сохраняется) — User Decision #6.
- Race condition возможен: sync во время активации → developer=0 overrides client-written 100. Принято: last-write-wins, edge case.
- `VisibilityTest` обновляется: параметр `overlay` удаляется из всех вызовов.
- **Escape hatch**: если User Decision #2 будет пересмотрен — `UserStatsRepository.setLocalDeveloperLevel(int)` остаётся stable seam для reintroduction overlay. 10-tap gesture и Footer Contract не меняются.

**Alternatives Considered:**
- **Overlay entity (original codex fix #2)** — Отклонено user: усложняет модель без значимой пользы для MVP. "Симплее" — прямая запись.
- **DataStore для dev mode flag** — Отклонено (User Decision #6): нет инфраструктуры DataStore в new-stack; Room уже создаётся для UserStats; два persistence слоя для одного флага — overhead.

---

## ADR-HLA-03: Центральный `AppDatabase` в `shared/core/persistence`

**Status:** Accepted (User Decision #6, 2026-04-20)

**Context:**
New-stack не имеет Room infrastructure. Dev mode (FR #10) и catalog-foundation оба требуют Room persistence. Два варианта: один центральный `AppDatabase` или per-feature databases.

**Decision:**
Создать `AppDatabase` в `shared/core/persistence/` как центральную Room database. Все entities (UserStatsEntity, CatalogEntity) регистрируются в этом database. `UserStatsDao` и `CatalogDao` предоставляются через один `AppDatabase` instance.

**Consequences:**
- Один Koin `single<AppDatabase>` — нет дублирования database instance.
- `core:persistence` становится зависимостью для `app-shell:data` и `core:catalog:data`.
- Миграции (Room `Migration` объекты) централизованы в одном месте.
- Future features (quiz persistence, settings persistence) добавляют свои entities в тот же database.
- **Current-project reasons (не только future-proofing)**: (a) Room KMP 2.7 setup overhead создаётся один раз на проект, а не per-feature; (b) 2 entities (UserStats, Catalog) не требуют storage isolation; (c) JVM тесты с единым in-memory `AppDatabase` проще координировать, чем несколько независимых databases.
- **Downside**: schema migration coordination требует cross-team awareness — добавление entity меняет `AppDatabase` (файл в `core:persistence`); owner backend-dev; схема ревьюится в design phase каждой фичи.

**Alternatives Considered:**
- **Per-feature databases** (`UserStatsDatabase` в `app-shell:data`, `CatalogDatabase` в `core:catalog:data`) — Отклонено: не масштабируется; в long run потребуется cross-database queries; нарушает "central AppDatabase" паттерн из legacy.
- **DataStore вместо Room для UserStats** — Отклонено: Room предоставляет Flow<Entity>, JOIN queries, migration support; UserStats содержит 16+ полей — DataStore Preferences неудобен.

---

## ADR-HLA-04: `SyncWorker` в `platform/android-services`; `Syncable` interface в `core:sync`

**Status:** Accepted

**Context:**
WorkManager — Android-only API, не совместим с KMP. `SyncWorker` должен жить в Android-specific модуле. При этом worker должен синхронизировать несколько сущностей (UserStats, Catalog, потенциально другие). Варианты структуры:
- (A) `SyncWorker` напрямую зависит от `feature:app-shell:domain` + `core:catalog:domain` → `platform/* → feature/*` coupling.
- (B) Определить `Syncable` interface в `core:sync`, каждый data module его реализует.

**Decision:**
Вариант B. `core:sync` определяет:
```
interface Syncable {
    suspend fun sync(): Result<Unit>
}
```
`UserStatsRepositoryImpl` (в `app-shell:data`) и `CatalogRepositoryImpl` (в `core:catalog:data`) оба реализуют `Syncable`. `SyncWorker` в `platform/android-services` зависит только от `core:sync` — получает `List<Syncable>` через Koin. `apps:android-next` (composition root) связывает impl'ы.

**Consequences:**
- `platform:android-services` зависит только от `core:sync` (нет зависимости на feature-modules из platform).
- `SyncWorker` расширяем: новые data modules добавляют `Syncable` impl без изменения `SyncWorker`.
- `apps:android-next` Koin module предоставляет `listOf<Syncable>(get<UserStatsRepositoryImpl>(), get<CatalogRepositoryImpl>())`.
- **Cross-reference**: canonical `SyncWorker` signature принимает `List<Syncable>` — см. `06-api-contract.md §14` (Syncable interface definition) + `§16` (Debate 1 resolved: Topology A accepted).

**Alternatives Considered (placement):**
- **Прямые зависимости `platform:android-services → feature:app-shell:domain + core:catalog:domain`** — Отклонено: нарушает принцип "platform не должен зависеть от feature domain". Legacy SyncWorker использовал этот паттерн, но в new-stack это создаёт нежелательный coupling.
- **SyncWorker в `shared/core/sync`** — Отклонено: WorkManager является Android-only API, не может быть в KMP commonMain. `core:sync` остаётся KMP-compatible (только interface).

**Alternatives Considered (worker topology):**
- **Topology A: Single `SyncWorker` + `List<Syncable>` (выбранный)** — Принято: (1) атомарная SyncNow семантика — dev mode deactivation + catalog refresh выполняются в одном `doWork()` вызове; (2) добавление новых entities не требует изменений в WorkManager enqueue logic; (3) single `WorkRequest` = простой manual trigger через `RootComponent.onSyncNow()`.
- **Topology B: Per-entity workers (`UserStatsSyncWorker` + `CatalogSyncWorker` отдельно)** — Отклонено: race conditions между workers при ручном SyncNow (два независимых `doWork()` = непредсказуемый порядок); усложняет `RootEvent.SyncStarted` контракт (один event для двух workers?); требует enqueue 2 workers вместо 1.
- **Topology C: Single worker с прямым inject репозиториев (без `Syncable` абстракции)** — Отклонено: для 2-entity MVP выглядит проще, но при 3+ entities потребует refactor на `Syncable` — ранняя абстракция (5 строк кода, нулевой runtime overhead) предпочтительнее reactive refactor позже.

---

## ADR-HLA-05: `RootComponent.onSyncNow()` + `RootEvent.SyncStarted` (не `Destination.SyncNow`)

**Status:** Accepted (User Decision #3, 2026-04-20)

**Context:**
`DrawerFooterAction.SyncNow` click должен enqueue WorkManager job и показать Snackbar. `Destination` sealed interface предназначен для navigation actions, не для side-effects. Нужен механизм без злоупотребления navigation.

**Decision:**
- Добавить `fun onSyncNow()` в `RootComponent` interface (`RootComponent.kt:24`).
- `DefaultRootComponent.onSyncNow()` выполняет: `workManager.enqueueUniqueWork("manual_sync", REPLACE, oneTimeSyncWorkRequest)` + `_events.trySend(RootEvent.SyncStarted)`.
- `AppShellScreen.kt` содержит `LaunchedEffect` для `rootComponent.events` — на `RootEvent.SyncStarted` показывает Snackbar.
- Снэкбар для dev mode activation аналогичен (`RootEvent.DevModeActivated`).

**Consequences:**
- Pattern симметричен существующему `onActiveTabRetap(tab)` в `RootComponent` (side-effect method, не Destination).
- `RootComponent` interface расширяется на 1 метод — backward-compatible change.
- `DefaultRootComponent` инжектирует `WorkManager` (platform dependency) через Koin.

**Alternatives Considered:**
- **`Destination.SyncNow` в sealed set** — Отклонено: семантически неверно (SyncNow — не навигационное действие); `DefaultRootComponent.onDestination()` обрабатывает navigation, не side-effects.
- **Callback `onSyncNow: () -> Unit` в DrawerFooter composable** — Отклонено: дополнительный параметр в цепочке (`AppShellScreen → DrawerContent → DrawerFooter`); менее структурировано, чем method на RootComponent.
- **`koinInject<WorkManager>()` напрямую в Composable** — Отклонено: нарушает invariant #2 (presentation не вызывает инфраструктуру напрямую, только через компонент).

---

## ADR-HLA-06: Coil 3.4.0 (`io.coil-kt.coil3`) для CatalogGrid

**Status:** Accepted (User Decision #7, 2026-04-20 — self-decided)

**Context:**
`CatalogGrid` отображает картинки каталогов (Firebase Storage paths). Нужна image loading библиотека для Compose. New-stack не использует ни Coil, ни Glide.

**Decision:**
Coil `3.4.0` (координаты `io.coil-kt.coil3`). `AsyncImage(model=pictureUrl, ...)` в `CatalogGridItem.kt`.

**Consequences:**
- Первое добавление image loading в new-stack.
- `libs.versions.toml` добавляет `coil3 = "3.4.0"` и `coil3-compose = "3.4.0"` — backend-dev owner.
- Firebase Storage URL resolution происходит в data layer (см. ADR-HLA-07), `AsyncImage` получает HTTPS URL, не `StorageReference`.

**Alternatives Considered:**
- **Glide** — Отклонено: менее нативен для Compose; legacy-библиотека; нет преимущества в new Compose-first проекте.
- **Отложить image loading** — Отклонено: `CatalogGrid` без картинок — неполный MVP; spec явно требует `picturePath` + изображения.

---

## ADR-HLA-07: Firebase Storage URL resolution в data layer (`CatalogRepositoryImpl`)

**Status:** Accepted

**Context:**
Domain `Catalog` содержит `picturePath: String?` (relative Storage path, например `"catalog-pictures/surveys.jpg"`). UI `AsyncImage` нужен HTTPS URL. Два подхода:
- (A) Resolve URL в data layer при `refreshFromRemote()` — сохранить resolved URL в `CatalogEntity.pictureUrl`.
- (B) Coil custom `Fetcher.Factory<StorageReference>` — передавать `StorageReference` напрямую в `AsyncImage`.

**Decision:**
Вариант A. `CatalogRepositoryImpl.refreshFromRemote()` при записи в Room дополнительно вызывает `FirebaseStorage.getReference(picturePath).downloadUrl.await()` и сохраняет в `CatalogEntity.pictureUrl: String?`. UI получает `pictureUrl` через `CatalogDisplayItem` presentation model — resolved в ADR-L3-03.

**Важное уточнение:** Domain `Catalog` содержит только `picturePath` (spec, Codex fix #5). Resolved `pictureUrl` — infrastructure artifact. Граница закрыта в ADR-L3-03: `CatalogDisplayItem` presentation model принадлежит `android:core:designsystem`; mapper `CatalogEntity → CatalogDisplayItem` в data/presentation layer. Формально закрывает Open Question из `0-spec-catalog-foundation.md` (picturePath → pictureUrl boundary). Детальный контекст rejection custom Fetcher — в `05-prior-art.md` (Coil issue #2551, re-fetch на recomposition).

**Consequences:**
- Domain `Catalog` остаётся чистым (нет `pictureUrl` — per Codex fix #5 + spec AC #17).
- URL resolution происходит один раз при sync, кэшируется в Room.
- Coil `AsyncImage` получает готовый HTTPS URL, нет custom fetcher.

**Alternatives Considered:**
- **Coil custom `Fetcher.Factory<StorageReference>`** — Отклонено: web research (`1-research.md:418`) выявил known issue (Coil issue #2551) — re-fetch на recomposition; Firebase StorageReference → downloadUrl = дополнительный network request на каждый recompose; митигация сложнее, чем pre-resolve.
- **URL in domain `Catalog.pictureUrl: String?`** — Отклонено: нарушает Codex fix #5 и invariant #1 (domain не содержит infrastructure artifacts).

---

## Summary

| ADR | Решение | Ключевой Rejected Alternative |
|-----|---------|-------------------------------|
| HLA-01 | `QualificationLevel` в `core:foundation` | cross-feature import — BLOCKER |
| HLA-02 | Прямая Room write (no overlay) | overlay entity — reverted by user |
| HLA-03 | Центральный `AppDatabase` в `core:persistence` | per-feature databases — не масштабируется |
| HLA-04 | `Syncable` interface в `core:sync`, `SyncWorker` в `platform:android-services` | platform → feature coupling — нарушает принципы |
| HLA-05 | `RootComponent.onSyncNow()` + `RootEvent` | `Destination.SyncNow` — семантически неверно |
| HLA-06 | Coil 3.4.0 | Glide — менее нативен для Compose |
| HLA-07 | URL resolution в data layer | Coil custom Fetcher — known re-fetch issue |

---

---

# L3 Decisions (architect-component)

*Добавлено: 2026-04-20. Автор: architect-component. Дополняет HLA-решения выше.*

---

## ADR-L3-01: Lambda injection в `ActivateDevModeUseCase` вместо прямой зависимости на `UserStatsRepository`

**Status:** Accepted

**Context:**
`ActivateDevModeUseCase` живёт в `feature:qualification:domain`. При `TapResult.Activated` ему нужно вызвать `userStatsRepository.setLocalDeveloperLevel(100)`. Но `UserStatsRepository` — интерфейс в `feature:app-shell:domain`. Прямая зависимость `qualification:domain → app-shell:domain` = cross-feature import = BLOCKER (clean-architecture.md).

Оригинальный Walking Skeleton уже применял аналогичный подход для `readCurrentDeveloperLevel: () -> Int` — KDoc объяснял запрет cross-module import.

**Decision:**
```kotlin
class ActivateDevModeUseCase(
    private val readCurrentDeveloperLevel: () -> Int,
    private val onDevModeActivated: suspend () -> Unit,
)
```
`DefaultRootComponent` (в `android:feature:app-shell:presentation`, который может зависеть от обоих domains) передаёт лямбды:
```kotlin
readCurrentDeveloperLevel = { _appShellState.value.userStats.qualification.developer }
onDevModeActivated = { userStatsRepository.setLocalDeveloperLevel(100) }
```

**Alternatives Considered:**
- **`ActivateDevModeUseCase(userStatsRepository: UserStatsRepository)`** — Отклонено: нарушает cross-feature import rule; `qualification:domain` не может импортировать `app-shell:domain`.
- **Переместить `ActivateDevModeUseCase` в `app-shell:domain`** — Отклонено: нарушает responsibility boundaries; логика тапов (TapProgress, RegisterTap FSM) принадлежит qualification domain, не app-shell domain.
- **Общий `core:dev-mode` модуль для use case** — Отклонено: over-engineering для одного use case; лямбда-injection решает проблему без введения нового модуля.

---

## ADR-L3-02: `_tapProgress` в `DefaultRootComponent`, не в Composable state

**Status:** Accepted

**Context:**
`TapProgress(count, lastTapMillis)` — состояние FSM 10-tap активации. Два варианта хранения:
- (A) `remember { mutableStateOf(TapProgress.INITIAL) }` в `DrawerFooter` composable
- (B) `MutableStateFlow<TapProgress>` в `DefaultRootComponent`

**Decision:**
Вариант B. `_tapProgress: MutableStateFlow<TapProgress>` в `DefaultRootComponent`. `DrawerFooter` вызывает `rootComponent.onVersionTap(nowMillis)`. `DefaultRootComponent.onVersionTap()` читает progress, вызывает use case, обновляет `_tapProgress`.

> **Note:** spec `0-spec-dev-mode.md` Journey #5 явно принимает сброс TapProgress при recomposition/background ("приемлемо для secret mode"). Наш более строгий инвариант — **design preference** (testability + consistency с Decompose-паттерном), не spec requirement.

**Consequences:**
- **Testability**: `DefaultRootComponent.onVersionTap()` unit-testable без Compose runtime — это primary reason.
- Симметрично с `_events: Channel<RootEvent>` и `_appShellState` — component владеет всем state/events, Composable — только view.
- State survives drawer close/reopen как side effect (spec это не требует, но и не запрещает).

**Alternatives Considered:**
- **Composable `remember` state** — Отклонено по project-specific reasons (не по spec): (1) не unit-testable без Compose runtime; (2) нарушает Decompose-паттерн "component owns all state"; (3) spec Journey #5 принимает reset, но проект предпочитает consistency.
- **`TapProgress` в `AppShellState`** — Отклонено: `AppShellState` — domain type; добавление UI-interaction state (`lastTapMillis`) в domain нарушает чистоту domain layer.

---

## ADR-L3-03: `CatalogDisplayItem` как presentation model для `pictureUrl`

**Status:** Accepted

**Context:**
Domain `Catalog` содержит `picturePath: String?` (relative Storage path). UI `AsyncImage` нужен HTTPS URL. ADR-HLA-07 решил: pre-resolve в data layer. Открытый вопрос (из HLA-07 "Важное уточнение"): как передать resolved `pictureUrl` в UI при том, что domain `Catalog` не должен содержать инфраструктурных артефактов.

**Decision:**
Ввести `CatalogDisplayItem` как presentation model в **`android:core:designsystem`** (canonical home):
```kotlin
data class CatalogDisplayItem(
    val id: CatalogId,
    val name: String,
    val pictureUrl: String?,    // resolved HTTPS URL, pre-computed in data layer
)
```

**Mapper chain ownership (canonical):**
- `CatalogEntity → Catalog` (domain): data layer — `CatalogEntityMapper.kt` в `core:catalog:data` (см. `08-storage-model.md §7.2`)
- `Catalog → CatalogDisplayItem`: presentation layer — `Catalog.toDisplayItem()` extension в `android:core:designsystem`. Маппинг тривиален: `pictureUrl` берётся из `CatalogEntity.pictureUrl` (pre-resolved в `CatalogRepositoryImpl.refreshFromRemote()` — ⇄ ADR-HLA-07). Repository эмитит `Flow<List<Catalog>>`; screen/component конвертирует в `List<CatalogDisplayItem>` перед передачей в composable.

`CatalogGrid` и `CatalogSpinner` принимают `List<CatalogDisplayItem>` как props — composable-компоненты остаются presentation-нейтральными (нет зависимости на Firebase SDK).

**Consequences:**
- Domain `Catalog` остаётся чистым (только `picturePath` — relative path; не содержит resolved HTTPS URL).
- `CatalogEntity.pictureUrl: String?` — Room-кэш resolved URL; pre-computed однажды при sync.
- `designsystem` не зависит от Firebase SDK — `CatalogDisplayItem.pictureUrl` уже строка.
- Screen ViewModels/Components — thin: только `catalog.toDisplayItem()` вызов.

**⇄ ADR-HLA-07** — URL resolution responsibility: `CatalogRepositoryImpl.refreshFromRemote()` вызывает `storageUrlResolver(picturePath)` и сохраняет результат в `CatalogEntity.pictureUrl`. `CatalogDisplayItem.pictureUrl` — downstream потребитель этого pre-resolved значения.

**Alternatives Considered:**
- **`pictureUrl` в domain `Catalog`** — Отклонено: нарушает Codex fix #5 и invariant #1 (domain не содержит infrastructure artifacts типа HTTPS URL с Firebase-специфичным форматом).
- **`CatalogDisplayItem` в `android:feature:catalog:presentation`** — Отклонено: такой модуль не существует в текущей архитектуре; `CatalogGrid`/`CatalogSpinner` — reusable composables, живут в `designsystem`; создание feature-presentation модуля только для DisplayItem — over-engineering.
- **URL resolution в ViewModel/Component прямо перед render** — Отклонено: async `downloadUrl.await()` в presentation = blocking или сложный coroutine wiring; pre-resolve в data layer чище (⇄ ADR-HLA-07).

---

## ADR-L3-04: `CatalogDao.replaceAll()` как `@Transaction`

**Status:** Accepted

**Context:**
`refreshFromRemote()` должен полностью заменить локальный список каталогов данными из Firestore. Нужна атомарность: `deleteAll()` + `insertAll()` должны выполняться как единая транзакция. Иначе возможно состояние "0 каталогов" между удалением и вставкой — Room Flow эмитит пустой список → UI мигает.

**Decision:**
```kotlin
@Transaction
suspend fun replaceAll(entities: List<CatalogEntity>) {
    deleteAll()
    insertAll(entities)
}
```
`@Transaction` в Room гарантирует атомарность. `observeAll()` Flow не эмитит промежуточное пустое состояние.

**Alternatives Considered:**
- **Две отдельные операции `deleteAll()` + `insertAll()` в `CatalogRepositoryImpl`** — Отклонено: нет атомарности на уровне Room; `observeAll()` Flow эмитит пустой список между операциями → CatalogGrid мигает.
- **`INSERT OR REPLACE` без `deleteAll()`** — Отклонено: устаревшие записи не удаляются (каталог, удалённый из Firestore, остаётся в Room). `replaceAll` = idempotent full replacement.

---

## L3 Summary

| ADR | Решение | Ключевой Rejected Alternative |
|-----|---------|-------------------------------|
| L3-01 | Lambda injection в `ActivateDevModeUseCase` | прямая зависимость `qualification → app-shell` — cross-feature BLOCKER |
| L3-02 | `_tapProgress` в `DefaultRootComponent` (testability preference, not spec req) | Composable `remember` — не unit-testable, нарушает Decompose pattern |
| L3-03 | `CatalogDisplayItem` в `android:core:designsystem`; mapper chain: Entity→Catalog (data), Catalog→DisplayItem (designsystem) | `pictureUrl` в domain — нарушает clean domain; feature-presentation модуль — over-engineering |
| L3-04 | `CatalogDao.replaceAll()` как `@Transaction` | две отдельные операции — UI мигает пустым списком |
