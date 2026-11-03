---
phase: 07
name: Presentation Integration
complexity: complex
---

# Phase 07: Presentation Integration

## Goal

Интегрировать все domain и data изменения в presentation layer: обновить `DefaultRootComponent` (tap progress, ActivateDevModeUseCase, onVersionTap, onSyncNow, WorkManager injection), добавить `SnackbarHost` + `LaunchedEffect` в `AppShellScreen`, обновить `DrawerFooter` (tap handler, SyncNow branch), обновить `DrawerContent` (userStats pass-through), создать `CatalogDisplayItem` + `CatalogSpinner` + `CatalogGrid` в designsystem, обновить `DesignCatalogRoot` condition.

## Scope

- UPDATE `DefaultRootComponent` — добавить `_tapProgress`, `activateDevModeUseCase`, `onVersionTap()`, `onSyncNow()`, `WorkManager` injection, `UserStatsRepository` injection
- UPDATE `AppShellScreen` — добавить `SnackbarHostState`, `LaunchedEffect(rootComponent)` для events, обновить `Scaffold` с `snackbarHost`, обновить `DesignCatalogRoot` condition
- UPDATE `DrawerContent` — пробросить `userStats` в `DrawerFooter`
- UPDATE `DrawerFooter` — новые параметры `userStats`, `onVersionTap`, `onSyncNow`, clickable version text, `when(action)` с SyncNow branch, `visibleFooterActions(isDebugBuild, userStats)` вызов
- UPDATE `Labels.kt` — добавить `SyncNow.displayName = "Синхронизация"` (и `SyncNow.icon`)
- CREATE `CatalogDisplayItem` data class + `Catalog.toDisplayItem()` ext в `android:core:designsystem`
- CREATE `CatalogSpinner` Composable в `android:core:designsystem`
- CREATE `CatalogGrid` + `CatalogGridItem` Composables в `android:core:designsystem`
- UPDATE `android:core:designsystem/build.gradle.kts` — добавить Coil 3 + `core:catalog:domain` deps
- UPDATE `appShellPresentationModule` Koin — добавить `WorkManager` + `UserStatsRepository` params

## Layer

presentation + ui

## Role Inputs

- `backend.md` — DefaultRootComponent + Koin updates
- `frontend.md` — Compose UI updates
- `tests.md`

## Dependencies

phases_ref: [phase-01, phase-03, phase-04, phase-05, phase-06]
- Phase 03: `RootComponent.onVersionTap/onSyncNow`, `RootEvent` variants, `DrawerFooterAction.SyncNow`
- Phase 04: `UserStatsRepository.setLocalDeveloperLevel`
- Phase 05: `CatalogDisplayItem` chain (indirectly)
- Phase 06: `WorkManager` singleton

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 2: DrawerFooter.Text("v$versionName") без clickable | frontend-dev | `DrawerFooter.kt:59` | ADR-L3-02 (_tapProgress в component) | добавить Modifier.clickable + onVersionTap callback | ручной тест: 10 тапов → snackbar |
| Problem 2: AppShellScreen нет SnackbarHostState | frontend-dev | `AppShellScreen.kt:129-141` | 07-events.md | добавить SnackbarHostState + LaunchedEffect + snackbarHost slot | ручной тест: dev mode → snackbar |
| Problem 5: DrawerFooter.when(action) нет SyncNow branch | frontend-dev | `DrawerFooter.kt:49-57` | ADR-HLA-05 | добавить SyncNow → `onSyncNow()` | compile green |
| Problem 4: DesignCatalogRoot condition не учитывает developer | frontend-dev | `AppShellScreen.kt:255` | spec AC #13-14 | `isDebugBuild || stats.developer >= LEVEL_1.points` | release + dev mode → DesignCatalog visible |

## New Files

- `android/core/designsystem/src/main/.../model/CatalogDisplayItem.kt`
- `android/core/designsystem/src/main/.../components/CatalogSpinner.kt`
- `android/core/designsystem/src/main/.../components/CatalogGrid.kt`

## Modified Files

- `android/feature/app-shell/presentation/src/main/.../component/DefaultRootComponent.kt` — major additions
- `android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt` — SnackbarHost + LaunchedEffect + condition update
- `android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerContent.kt` — userStats pass-through
- `android/feature/app-shell/presentation/src/main/.../ui/drawer/DrawerFooter.kt` — new params + tap + SyncNow
- `android/feature/app-shell/presentation/src/main/.../ui/Labels.kt` — SyncNow displayName + icon
- `android/feature/app-shell/presentation/di/AppShellPresentationModule.kt` — WorkManager + UserStatsRepository injection
- `android/core/designsystem/build.gradle.kts` — Coil 3 + catalog:domain deps

## Deleted Files

none

## Acceptance Criteria

- [ ] `DefaultRootComponent` реализует `onVersionTap(nowMillis)` + `onSyncNow()` (Phase 03 interface stubs заменены реализацией)
- [ ] `_tapProgress: MutableStateFlow<TapProgress>` в `DefaultRootComponent`, сбрасывается при Activated/AlreadyDev
- [ ] 10 тапов по версии в release build → snackbar "Режим разработчика включён" + все секции меню видимы
- [ ] `DrawerFooter.SyncNow` branch → `onSyncNow()` → `WorkManager.enqueueUniqueWork("manual_sync")` + snackbar "Синхронизация запущена"
- [ ] `DesignCatalogRoot` condition: `isDebugBuild || state.userStats.qualification.developer >= QualificationLevel.LEVEL_1.points`
- [ ] `CatalogSpinner` — `ExposedDropdownMenuBox` + prepend "Все категории" (null selectedId)
- [ ] `CatalogGrid` — `LazyVerticalGrid(GridCells.Fixed(2))` + `AsyncImage` из Coil 3
- [ ] Новые stateful fields в DefaultRootComponent (`_tapProgress`) сбрасываются при `onVersionTap` → Activated/AlreadyDev result
- [ ] `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` GREEN (все TODO stubs из Phase 03 заменены)

## State Matrix Coverage

Flow 1 (10-Tap Dev Mode Activation) из `02-behavior.md` — полный flow реализован.
Flow 3 (SyncNow Manual Trigger) — полный flow реализован.
Flow 5 (Drawer Rendering with Superqualification) — условие для DesignCatalogRoot.

## Domain Contract Coverage

`DefaultRootComponent.onVersionTap` реализует `RootComponent.onVersionTap` contract (ADR-L3-01 lambda injection wiring).

## Pattern Invariants

- `DefaultRootComponent` не вызывает Firebase напрямую — только через `UserStatsRepository` интерфейс
- `_tapProgress` сбрасывается в `TapProgress.INITIAL` при `TapResult.Activated` и `TapResult.AlreadyDev` (per 07-events.md L3.1 инварианты)
- `DrawerFooter` НЕ создаёт coroutines напрямую — только вызывает callbacks (onVersionTap, onSyncNow)
- `LaunchedEffect(rootComponent)` — ключ `rootComponent`, не `Unit`, для предотвращения дублирования collectors при recomposition
- `CatalogDisplayItem.pictureUrl` = HTTPS URL (pre-resolved) — `AsyncImage` не нужен custom Fetcher
- Composable компоненты `CatalogSpinner`, `CatalogGrid` принимают `List<CatalogDisplayItem>` — нет зависимости на Firebase SDK

### Options Considered

| Критерий | Option A (recommended): _tapProgress в DefaultRootComponent | Option B: remember в DrawerFooter composable |
|----------|-----------------------------------------------------------|---------------------------------------------|
| Testability | unit-testable без Compose runtime | требует Compose test infrastructure |
| Decompose pattern | consistent (component owns state) | нарушает "component owns all state" |
| Spec compliance | spec Journey #5 принимает reset — оба варианта OK | OK |
| Complexity | немного больше boilerplate | проще но тестировать сложнее |

**Recommended: Option A**

**Rationale:** ADR-L3-02 — testability + Decompose pattern consistency. Spec Journey #5 принимает reset на recomposition (секретный режим), но проект предпочитает consistency.

**Rejected Option B:** не unit-testable без Compose runtime; нарушает Decompose pattern "component owns all state".

## Tests Required

Параллельно:

- `DevModeActivationIntegrationTest`: given `DefaultRootComponent` с `FakeUserStatsDao`, when `onVersionTap()` × 10, then `FakeUserStatsDao.updateDeveloperLevelCalls == 1` + `RootEvent.DevModeActivated` emitted
- `SyncNowFlowIntegrationTest`: given `DefaultRootComponent` с `FakeWorkManager`, when `onSyncNow()`, then `WorkManager.enqueueUniqueWork("manual_sync")` called + `RootEvent.SyncStarted` emitted
- `DesignCatalogRenderConditionTest`: given `visibleFooterActions(debug=false, developer=0)`, then `DesignCatalog absent`; given `developer=100`, then `DesignCatalog present`

**Fake WorkManager** может потребоваться — или тестировать через captured calls.

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN — все TODO stubs заменены реальной реализацией, нет compile errors |
| `./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache` | GREEN |
| `./gradlew :android:core:designsystem:test --no-configuration-cache` | GREEN |
| Ручной: release build + 10 тапов на версию | snackbar "Режим разработчика включён" |
| Ручной: SyncNow → snackbar "Синхронизация запущена" | работает |
| Ручной: LOCAL tab drawer | `[Домашние квесты, Мои квесты, Настройки]` |

## Handoff Notes

После Phase 07 разблокирована Phase 08 (Firebase Rules + Integration Tests) — полный stack готов для end-to-end тестирования.
