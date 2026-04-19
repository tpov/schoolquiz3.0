---
phase: phase-05
feature: app-shell-menu
date: 2026-04-18
---

# Phase-05: AppShellScreen + Navigation Skeleton

## Goal

Создать `AppShellScreen` Composable (ModalNavigationDrawer + Scaffold + TopAppBar + NavigationBar + Children per-tab + Crossfade) и инфраструктуру scroll-to-top в `android/feature/app-shell/presentation`. После фазы — AppShellScreen рендерится с заглушками (UnderConstructionScreen), переключение вкладок работает, drawer открывается/закрывается.

## Scope

- `AppShellScreen.kt` — полная shell structure
- `UnderConstructionScreen.kt` — generic placeholder
- `scroll/ScrollToTopHook.kt` + `scroll/ScrollToTopRegistry.kt` — identity-aware registry (ADR-COMP-06)
- `android/core/navigation/` — Decompose Compose helpers (subscribeAsState, Children, animation) — опционально, если нужны extensions
- `android/core/navigation/build.gradle.kts` — apply compose library plugin, expose decompose-extensions-compose

## Layer

ui / presentation

## Role Inputs

- `frontend.md`
- `tests.md`

## Review Tags

- `concurrency-review`: 2 `LaunchedEffect` (snapshotFlow drawer sync, LaunchedEffect(state.isDrawerOpen) → drawerState.open()/close()), `collectAsStateWithLifecycle(rootComponent.appShellState)` (Flow collection в Compose lifecycle scope)

## State Matrix Coverage

| FSM | Строки | Coverage |
|-----|--------|----------|
| DrawerGuard (SHOP no hamburger) | R1: `!state.isShopActive` → hamburger visible | `AppShellScreenTest.kt` hamburger visibility compile-check (AC 23a); full assertion in phase-07 integration smoke |
| DrawerGuard (SHOP no-op) | проверено в phase-04 domain | |
| Back 4-step FSM | UI trigger: system back → `onDestination(Back)` через Essenty BackCallback | compile-only; full FSM coverage in phase-04 DefaultRootComponentTest |
| RetapOutcome → scroll-to-top | `NO_OP` → `registry.current(tab)?.scrollToTop()` | `ScrollToTopRegistryTest.kt` |
| Drawer visibility (edge swipe / scrim / swipe close) | gesturesEnabled + snapshotFlow sync | **compile-level placeholder in phase-05**; journeys 7-9 explicit assertion in phase-07 manual smoke (AC 29) |

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 1: Walking Skeleton integration | frontend-dev | `AppShellScreen.kt` — Compose UI подключает `rootComponent` из domain/presentation | UI только Composable — business logic в domain | `AppShellScreen(rootComponent, appVersionName)` — UI-слой (H8: appVersionName from app layer) | instrumented test |
| Problem 2: Compose compiler gap | frontend-dev | `android/core/navigation/build.gradle.kts`, `android/feature/app-shell/presentation/build.gradle.kts` | phase-01/04 уже включили Compose | navigation module: apply compose plugin | `./gradlew :android:core:navigation:compileDebugKotlin` |
| Problem 6: Feature module dependencies missing | frontend-dev | `android/core/navigation/build.gradle.kts:9-11` (нет domain dep) | navigation — pure helpers, не зависит от domain | Add decompose-extensions-compose + compose BOM | compile check |

## New Files

```
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/labels/Labels.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/AppShellScreen.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/UnderConstructionScreen.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/scroll/ScrollToTopHook.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/scroll/ScrollToTopRegistry.kt
android/feature/app-shell/presentation/src/androidTest/kotlin/.../presentation/AppShellScreenTest.kt
android/feature/app-shell/presentation/src/test/kotlin/.../presentation/ScrollToTopRegistryTest.kt
```

## Modified Files

```
android/core/navigation/build.gradle.kts  (apply compose library plugin + expose extensions)
android/feature/app-shell/presentation/src/main/kotlin/.../component/DefaultRootComponent.kt
  (tab component fields: private → internal, строки 81-84/90, требовалось для доступа из AppShellScreen)
```

## Open Questions (post-implementation)

- `frontend.md` содержит расхождение с `overview.md:89` + `06-api-contract.md:429` по AC 8 ("Недоступно" vs `displayName`). `frontend.md` требует manual sync — зафиксировано в retrospective lead-ом.

## Deleted Files

none

## Dependencies

- Phase-04 MUST complete (DefaultRootComponent, tab components, NavigatorImpl)
- Phase-03 MUST complete (DesignCatalogScreen imported in AppShellScreen)
- Phase-02 MUST complete (SchoolQuizTheme)

## Acceptance Criteria

1. `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — BUILD SUCCESSFUL
2. `AppShellScreen` содержит: `ModalNavigationDrawer` → `Scaffold` → `TopAppBar` (hamburger gated по `!state.isShopActive`) + `NavigationBar` (4 tabs via `BrandNavBarItem`) + `Children(childStack)` per-tab + `Crossfade(tween(300))` для контент-перехода
3. Drawer state sync: 2 `LaunchedEffect` — один на `snapshotFlow { drawerState.currentValue }` → `goTo(OpenDrawer/CloseDrawer)` (journeys 5/7/8), второй на `LaunchedEffect(state.isDrawerOpen)` → `drawerState.open()/close()`
4. NavBar onClick logic: `if (tab == activeTab) onActiveTabRetap(tab) else navigator.goTo(SwitchTab(tab))`
5. `RetapOutcome.NO_OP` → `registry.current(activeTab)?.scrollToTop()`
6. `ScrollToTopRegistry.unregister` identity-aware (`===`) per ADR-COMP-06
7. **Stateful field reset**: `ScrollToTopRegistry.hooks` очищается при каждом новом `remember {}` (создаётся в `AppShellScreen` через `remember { ScrollToTopRegistry() }`)
8. `DesignCatalogScreen` рендерится по `LocalConfig.DesignCatalogRoot` ТОЛЬКО при `BuildConfig.DEBUG == true`; в release — `UnderConstructionScreen("Недоступно")`
9. **AC 13**: `UnderConstructionScreen` рендерит title + subtitle "Скоро здесь будет..." per spec `0-spec.md:767`
10. **AC 20**: `BrandNavBarItem` wrapper exposes `badge: BadgeContent? = null` param (MVP всегда null per BR #15); param present in public API
11. `Labels.kt` создан в `presentation/ui/labels/` — содержит extensions для `Tab`, `DrawerSection`, `TabConfig`, `DrawerFooterAction`. Нет дублирования в `AppShellScreen.kt`
12. `AppShellScreen` принимает `appVersionName: String` — не вызывает `BuildConfig.VERSION_NAME` внутри (library module не имеет VERSION_NAME)

## Tests Required

```
hamburger_visible_on_LOCAL_tab:
  given AppShellScreen with activeTab = LOCAL
  when composed
  then hamburger icon is displayed (AC 23a — visibility FSM)

hamburger_hidden_on_SHOP_tab:
  given AppShellScreen with activeTab = SHOP
  when composed
  then hamburger icon not displayed

drawer_sync_opens_when_isDrawerOpen_true:
  given AppShellScreen, initial state isDrawerOpen = false
  when rootComponent.appShellState emits isDrawerOpen = true
  then drawerState.isOpen (AC 5 — LaunchedEffect sync)

navbar_tap_active_tab_calls_retap:
  given AppShellScreen with activeTab = LOCAL
  when LOCAL tab NavBar item tapped
  then onActiveTabRetap(LOCAL) called on component

navbar_tap_inactive_tab_calls_switch:
  given AppShellScreen with activeTab = LOCAL
  when INTERNET tab NavBar item tapped
  then navigator.goTo(SwitchTab(INTERNET)) called

scroll_registry_identity_unregister:
  given ScrollToTopRegistry, hook1 registered for LOCAL
  when hook2 registered for LOCAL, then hook1.unregister called
  then registry.current(LOCAL) == hook2 (identity prevents wrong unregister)

scroll_registry_crossfade_overlap:
  given ScrollToTopRegistry, incoming hook2 registered BEFORE outgoing hook1 unregisters
  when hook1.unregister called (hook1 != hook2)
  then registry.current(LOCAL) still == hook2
```

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:compileDebugKotlin --no-configuration-cache
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
# Instrumented (optional):
./gradlew :android:feature:app-shell:presentation:connectedDebugAndroidTest --no-configuration-cache
```

## Handoff Notes

- Phase-06 добавит реальный drawer content (DrawerHeader, DrawerSectionList, DrawerFooter) в AppShellScreen через `DrawerContent` slot
- Phase-07 подключит AppShellScreen в MainActivity через `setContent { SchoolQuizTheme { AppShellScreen(rootComponent, appVersionName = BuildConfig.VERSION_NAME) } }` (H8 fix — `appVersionName: String` mandatory param)
