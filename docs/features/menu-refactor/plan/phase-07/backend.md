---
phase: 07
role: backend-dev
---

# Phase 07 — Backend Tasks

## Pattern Invariants

- `DefaultRootComponent` не вызывает Firebase напрямую — только через `UserStatsRepository` интерфейс. Canonical: `DefaultRootComponent.kt:63` — `_appShellState` и state flow через repository; `DefaultRootComponent.kt:122` — `scope.launch` delegating to use cases.
- `_tapProgress` сбрасывается в `TapProgress.initial` при `TapResult.Activated` и `TapResult.AlreadyDev`. Canonical: ADR-L3-02 (`03-decisions.md:218-250`). Pattern: `_appShellState = MutableStateFlow(...)` в `DefaultRootComponent.kt:63` — аналогичный MutableStateFlow field.
- `onSyncNow()` — НЕ suspend; `workManager.enqueueUniqueWork()` sync операция, `_events.trySend()` non-blocking. Canonical: `DefaultRootComponent.kt:83-84` — `_events = Channel<RootEvent>(Channel.BUFFERED)` + `override val events = _events.receiveAsFlow()`. `trySend` = non-blocking enqueue.
- `appShellPresentationModule` обновляется добавлением `userStatsRepository = get()` и `workManager = get()` в factory лямбду. Canonical: существующий Koin module pattern в `appShellPresentationModule` — смотреть структуру перед изменением.
- `android:core:designsystem` зависит от `shared:core:catalog:domain` — однонаправленная зависимость (android → shared:core), разрешена clean-architecture. Canonical: `.claude/rules/clean-architecture.md` — `core (if exists) → nothing`; android может зависеть от shared:core.

---

## 1. UPDATE DefaultRootComponent — constructor params

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** class update
- **Сигнатура:** `class DefaultRootComponent(componentContext: ComponentContext, initUseCase: InitializeAppShellUseCase, navigateUseCase: NavigateUseCase, observeUseCase: ObserveAppShellStateUseCase, retapUseCase: OnTabRetapUseCase, userStatsRepository: UserStatsRepository, workManager: WorkManager) : RootComponent, ComponentContext by componentContext`
- **Вход:** добавить два новых параметра конструктора: `userStatsRepository: UserStatsRepository` (из `app-shell:domain`) и `workManager: WorkManager` (из AndroidX WorkManager); оба `private val`
- **Поведение / Выход:**
  - Существующие параметры (`initUseCase`, `navigateUseCase`, `observeUseCase`, `retapUseCase`) не меняются
  - `WorkManager` живёт в presentation (Android-only) — не в domain
  - Koin factory передаёт оба зависимости через `get()`
- **Edge cases:**
  - `WorkManager` — Android-specific import; `DefaultRootComponent` живёт в `android:feature:app-shell:presentation` → импорт разрешён
  - Не добавлять `ActivateDevModeUseCase` в конструктор — создаётся внутри как property (lambda injection pattern, ADR-L3-01)
- **Depends on:** Phase 03 (`UserStatsRepository` новые методы), Phase 06 (`WorkManager` singleton в Koin)
- **Canonical reference:** `06-api-contract.md §6`
- **Rationale:** ADR-L3-02 — component owns state (`_tapProgress`); WorkManager как Android dependency живёт в presentation, не пересекает в domain

---

## 2. CREATE _tapProgress field в DefaultRootComponent

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** property (MutableStateFlow)
- **Сигнатура:** `private val _tapProgress = MutableStateFlow(TapProgress.initial)`
- **Вход:** `TapProgress.initial` — initial/companion value из `qualification:domain` (`TapProgress` data class)
- **Поведение / Выход:**
  - Хранит текущий прогресс 10-тапового жеста активации dev mode
  - Сбрасывается в `TapProgress.initial` при `TapResult.Activated` и `TapResult.AlreadyDev` (AC фазы)
  - Обновляется при `TapResult.NoChange` → `result.updatedProgress` и при `TapResult.Reset` → `TapProgress(count = 1, lastTapMillis = nowMillis)`
- **Edge cases:**
  - Не `StateIn` — internal state, Composable не читает `_tapProgress` напрямую (pattern invariant: component owns state, UI не подписывается)
  - Thread safety: обновляется только внутри `scope.launch { }` — однопоточно через component scope
- **Depends on:** `TapProgress` из `shared:feature:qualification:domain`
- **Canonical reference:** `06-api-contract.md §6`, `07-events.md L3.1`
- **Rationale:** `_tapProgress` в DefaultRootComponent (не в Composable remember) — unit-testable без Compose runtime; consistent с Decompose pattern "component owns all state"

---

## 3. CREATE activateDevModeUseCase property в DefaultRootComponent

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** property (ActivateDevModeUseCase)
- **Сигнатура:** `private val activateDevModeUseCase = ActivateDevModeUseCase(readCurrentDeveloperLevel = { _appShellState.value.userStats.qualification.developer }, onDevModeActivated = { userStatsRepository.setLocalDeveloperLevel(QualificationLevel.LEVEL_1.points) })`
- **Вход:** два лямбда-аргумента:
  - `readCurrentDeveloperLevel: () -> Int` — синхронный читатель из `_appShellState.value` (не suspend)
  - `onDevModeActivated: suspend () -> Unit` — suspend запись через `UserStatsRepository.setLocalDeveloperLevel`
- **Поведение / Выход:**
  - При `TapResult.Activated` лямбда `onDevModeActivated` записывает `developer=100` в Room через repository
  - Нет прямого импорта `qualification:domain → app-shell:domain` — BLOCKER предотвращён
  - `QualificationLevel.LEVEL_1.points` (= 100) из `shared:core:foundation` — импорт доступен в presentation
- **Edge cases:**
  - `_appShellState` может не содержать актуальных stats при первом запуске (guest stats) — `readCurrentDeveloperLevel()` вернёт `0`, `TapResult.AlreadyDev` не будет triggered, активация пойдёт по нормальному пути
  - `onDevModeActivated` — suspend, вызывается внутри `scope.launch { }` в `onVersionTap()`
- **Depends on:** Phase 01 (`ActivateDevModeUseCase` rewrite), Phase 03 (`UserStatsRepository.setLocalDeveloperLevel`), core:foundation (`QualificationLevel`)
- **Canonical reference:** `06-api-contract.md §6`, `06-api-contract.md §2.2`
- **Rationale:** Lambda injection (ADR-L3-01) — единственный способ подключить `ActivateDevModeUseCase` без cross-feature import BLOCKER

---

## 4. IMPLEMENT onVersionTap() в DefaultRootComponent

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** override fun
- **Сигнатура:** `override fun onVersionTap(nowMillis: Long)`
- **Вход:** `nowMillis: Long` — текущее время в миллисекундах (caller: `System.currentTimeMillis()` из DrawerFooter)
- **Поведение / Выход:**
  - `scope.launch { ... }` — запускает coroutine в component scope
  - Вызывает `activateDevModeUseCase(_tapProgress.value, nowMillis)` → `TapResult`
  - Обновляет `_tapProgress.value` в зависимости от результата:
    - `TapResult.Activated` → `_tapProgress.value = TapProgress.initial` + `_events.trySend(RootEvent.DevModeActivated)`
    - `TapResult.AlreadyDev` → `_tapProgress.value = TapProgress.initial` + `_events.trySend(RootEvent.DevModeAlreadyActive)`
    - `TapResult.NoChange` → `_tapProgress.value = result.updatedProgress`
    - `TapResult.Reset` → `_tapProgress.value = TapProgress(count = 1, lastTapMillis = nowMillis)`
- **Edge cases:**
  - Rapid taps (< 500ms apart) — `registerTap` logic обрабатывает reset internally
  - `_events.trySend()` non-blocking (Channel.BUFFERED) — не потеряет event даже если Composable collector ещё не запущен
- **Depends on:** Tasks 2, 3 выше; Phase 03 (`RootEvent.DevModeActivated/AlreadyDev`)
- **Canonical reference:** `06-api-contract.md §6`, `07-events.md L3.1`
- **Rationale:** Component scope с `SupervisorJob()` — onVersionTap failure не падает весь component

---

## 5. IMPLEMENT onSyncNow() в DefaultRootComponent

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/feature/appshell/presentation/component/DefaultRootComponent.kt`
- **Тип:** override fun
- **Сигнатура:** `override fun onSyncNow()`
- **Вход:** нет параметров
- **Поведение / Выход:**
  - Создаёт `OneTimeWorkRequestBuilder<SyncWorker>()` с `NetworkType.CONNECTED` constraint
  - Вызывает `workManager.enqueueUniqueWork(SyncWorker.WORK_NAME_MANUAL, ExistingWorkPolicy.REPLACE, request)`
  - Вызывает `_events.trySend(RootEvent.SyncStarted)` — non-blocking
  - Метод не suspend — всё синхронно (WorkManager enqueue = sync call)
- **Edge cases:**
  - Повторный клик SyncNow → `ExistingWorkPolicy.REPLACE` отменяет предыдущую job и ставит новую
  - `SyncStarted` event эмитируется немедленно (не когда sync завершился) — `07-events.md` инвариант #3: "sync enqueued, not completed"
  - `NetworkType.CONNECTED` — если нет сети, WorkManager поставит в очередь и запустит когда появится сеть
- **Depends on:** Phase 06 (`SyncWorker.WORK_NAME_MANUAL`), Phase 03 (`RootEvent.SyncStarted`)
- **Canonical reference:** `06-api-contract.md §6`, `07-events.md L3.2`
- **Rationale:** `onSyncNow()` не suspend по design — WorkManager enqueue sync; `trySend` non-blocking

---

## 6. UPDATE appShellPresentationModule Koin

- **Файл:** `android/feature/app-shell/presentation/di/AppShellPresentationModule.kt`
- **Тип:** Koin module update
- **Сигнатура:** `val appShellPresentationModule = module { factory { (ctx: ComponentContext) -> DefaultRootComponent(componentContext = ctx, initUseCase = get(), navigateUseCase = get(), observeUseCase = get(), retapUseCase = get(), userStatsRepository = get(), workManager = get()) }; ... }`
- **Вход:** существующий `appShellPresentationModule`; добавить `userStatsRepository = get()` и `workManager = get()` в `DefaultRootComponent` factory
- **Поведение / Выход:**
  - `userStatsRepository = get()` резолвится из `appShellDataModule` (Phase 04)
  - `workManager = get()` резолвится из `syncModule` (Phase 06: `single<WorkManager> { WorkManager.getInstance(androidContext()) }`)
  - Остальные factories в модуле не меняются
- **Edge cases:**
  - Если `syncModule` не загружен до `appShellPresentationModule` — Koin runtime error. Порядок регистрации в `AppApplication.startKoin { modules(...) }` важен: `syncModule` должен быть раньше `appShellPresentationModule`
  - DI rule: нет дублирования `single<WorkManager>` — только один binding в `syncModule`
- **Depends on:** Phase 04 (`appShellDataModule`), Phase 06 (`syncModule`)
- **Canonical reference:** `06-api-contract.md §12`
- **Rationale:** Koin composition root — единственное место где все layer boundaries пересекаются

---

## 7. UPDATE android:core:designsystem/build.gradle.kts

- **Файл:** `android/core/designsystem/build.gradle.kts`
- **Тип:** build config update
- **Сигнатура:** добавить `implementation(libs.coil.compose)` + `implementation(project(":shared:core:catalog:domain"))`
- **Вход:** существующий `android:core:designsystem/build.gradle.kts`
- **Поведение / Выход:**
  - `libs.coil.compose` (Coil 3.4.0 `io.coil-kt.coil3:coil-compose`) — для `AsyncImage` в `CatalogGridItem`
  - `project(":shared:core:catalog:domain")` — для `CatalogId` и `Catalog` domain types в `CatalogDisplayItem`
  - `:shared:core:catalog:domain` — ранее добавлен в `settings.gradle.kts` (Phase 01)
- **Edge cases:**
  - Coil 3 (`io.coil-kt.coil3`) — другой artifact group от Coil 2 (`io.coil-kt`). Проверить что `libs.versions.toml` содержит Coil 3 entry; если нет — добавить `coil = "3.4.0"` + `coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }`
  - `shared:core:catalog:domain` — Android module не может зависеть от shared KMP module напрямую если shared не публикует `androidMain` artifact. Проверить что `:shared:core:catalog:domain` использует `kotlin("multiplatform")` с `androidTarget()` — confirmed per Phase 01
- **Depends on:** Phase 01 (`shared:core:catalog:domain` module created + settings.gradle.kts)
- **Canonical reference:** `06-api-contract.md §13`
- **Rationale:** `CatalogDisplayItem` живёт в `designsystem` (ADR-L3-03) — designsystem должен видеть `CatalogId` из domain
