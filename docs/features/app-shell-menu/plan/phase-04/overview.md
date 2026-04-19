---
phase: phase-04
feature: app-shell-menu
date: 2026-04-18
---

# Phase-04: Decompose Integration Layer

## Goal

Создать `DefaultRootComponent`, 4 tab components, `NavigatorImpl`, screen components и `AppShellPresentationModule` в `android/feature/app-shell/presentation`. После фазы — presentation module компилируется и `DefaultRootComponentTest` (JVM, TestComponentContext) зелёный.

## Scope

- `android/feature/app-shell/presentation/build.gradle.kts` — apply compose library plugin, добавить domain/navigation/designsystem/Decompose/Koin deps
- `DefaultRootComponent.kt` — implements `RootComponent`, `ComponentContext by ctx`, 4 StackNavigation, coroutine scope, ObserveAppShellStateUseCase wiring
- `NavigatorImpl.kt` — implements `Navigator`
- Tab component interfaces + default impls (4 файла)
- Screen component sealed interfaces (4 файла)
- `AppShellPresentationModule.kt` — Koin bindings
- Update `AppApplication.kt` (apps/android-next) — добавить `appShellPresentationModule`

## Layer

presentation (Decompose integration)

## Role Inputs

- `frontend.md`
- `tests.md`

## Review Tags

- `concurrency-review`: `DefaultRootComponent` содержит `MutableStateFlow<AppShellState>`, `Channel<RootEvent>` (shared mutable state), coroutine scope с lifecycle binding, `BackCallback` registration (async lifecycle callback), `ObserveAppShellStateUseCase` Flow collection in `init {}`

## State Matrix Coverage

| FSM | Строки | Coverage |
|-----|--------|----------|
| Back 4-step FSM | R1-R4 | `DefaultRootComponentTest.kt` — тесты для всех 4 ступеней |
| RetapOutcome FSM | R1 (POP_TO_ROOT), R2 (NO_OP) | `DefaultRootComponentTest.kt` |
| Cold Start FSM | R1 (initUseCase → default state) | `DefaultRootComponentTest.kt` cold start test |
| DrawerGuard (SHOP no drawer) | R1 | `DefaultRootComponentTest.kt` open drawer SHOP no-op |
| Tab switch FSM | all rows: SwitchTab → NavigateUseCase → new activeTab | `DefaultRootComponentTest.kt` `go_to_switch_tab_changes_active_tab` + `select_section_cross_tab_auto_switches` |
| Drawer section switch FSM | all 4 rows: SelectSection → NavigateUseCase → new activeSection; cross-tab auto-switch; hidden section no-op (AC 23g) | `DefaultRootComponentTest.kt` `select_section_cross_tab_auto_switches` + `go_to_select_section_hidden_section_no_op` |
| SectionVisibility | не прямая — через NavigateUseCase (Walking Skeleton) | hidden-section guard verified via `go_to_select_section_hidden_section_no_op` |

## Domain Contract Coverage

Phase-04 реализует Walking Skeleton integration contract для presentation:
- `RootComponent` interface (domain) → `DefaultRootComponent` (presentation): полное соответствие
- 5 use cases инжектируются через Koin factory
- `ObserveAppShellStateUseCase` вызывается с provider lambda `{ _state.value }` (ADR-LEAD-02)

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 1: Walking Skeleton integration | frontend-dev | `android/feature/app-shell/presentation/` — пусто | domain не переписывается; `DefaultRootComponent` — новый класс в presentation | Создать `DefaultRootComponent` implements `RootComponent` interface из domain | `./gradlew :android:feature:app-shell:presentation:test` |
| Problem 4: Navigator interface missing | frontend-dev | `Navigator.kt` создан в phase-01 в domain/navigation/ | `NavigatorImpl` делегирует в `RootComponent.onDestination()` | Создать `NavigatorImpl` implements `Navigator` | compile check |
| Problem 2: Compose compiler gap | frontend-dev | `android/feature/app-shell/presentation/build.gradle.kts` | apply schoolquiz.android.compose.library | Update build.gradle.kts | `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` |
| Problem 5: apps/android-next stub | frontend-dev | `AppApplication.kt` — в phase-01 добавлен без `appShellPresentationModule` | добавить module в startKoin | Добавить `appShellPresentationModule` в AppApplication.startKoin | assembleDebug |
| Problem 6: Feature module dependencies missing | frontend-dev | `android/feature/app-shell/presentation/build.gradle.kts:9-12` | presentation → domain OK; presentation → navigation OK; presentation → designsystem OK | Add all deps | `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` |

## New Files

```
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/DefaultRootComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/NavigatorImpl.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/tab/LocalTabComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/tab/InternetTabComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/tab/EventsTabComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/component/tab/ShopTabComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/screen/LocalScreenComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/screen/InternetScreenComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/screen/EventsScreenComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/screen/ShopScreenComponent.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/di/AppShellPresentationModule.kt
android/feature/app-shell/presentation/src/test/kotlin/.../presentation/DefaultRootComponentTest.kt
```

## Modified Files

```
android/feature/app-shell/presentation/build.gradle.kts
apps/android-next/src/main/java/.../AppApplication.kt  (добавить appShellPresentationModule в startKoin)
```

## Deleted Files

none

## Dependencies

- Phase-01 MUST complete (Navigator.kt, RootComponent.kt в domain; AppApplication.kt; Koin modules)
- Phase-02 MUST complete (SchoolQuizTheme для presentation dependency graph)

## Acceptance Criteria

1. `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — BUILD SUCCESSFUL
2. `./gradlew :android:feature:app-shell:presentation:test` — `DefaultRootComponentTest` зелёный
3. `DefaultRootComponent` реализует `RootComponent` interface из domain/navigation
4. `ObserveAppShellStateUseCase` вызывается с `{ _state.value }` (ADR-LEAD-02) — не с captured state
5. Все `childStack(serializer = null, ...)` — state-saving deferred per ADR-LEAD-01
6. **Stateful fields reset**: при повторном `DefaultRootComponent.init {}` (lifecycle restart) новые coroutine scope стартуют чисто; `_state` и `_events` не содержат данных от предыдущей lifecycle (Essenty lifecycle manages this)
7. `appShellPresentationModule` добавлен в `AppApplication.startKoin`
8. Нет прямых Decompose imports в `Navigator.kt` / `RootComponent.kt` (domain файлы — pure Kotlin)

## Tests Required

```
cold_start_state_equals_init_use_case_result:
  given DefaultRootComponent with FakeInitializeAppShellUseCase returning customState
  when component initialized
  then appShellState first emission == customState

init_use_case_throws_then_fallback_state:
  given DefaultRootComponent with FakeInitializeAppShellUseCase throwing
  when component initialized
  then appShellState first emission == AppShellState.fallback(UserStats.guest())

observe_stats_emits_then_navigation_preserved:
  given DefaultRootComponent, FakeUserStatsRepository emitting new stats
  when onDestination(SwitchTab(INTERNET)) then stats emit
  then appShellState.activeTab still INTERNET (navigation preserved, ADR-LEAD-02)

go_to_switch_tab_changes_active_tab:
  given DefaultRootComponent in LOCAL state
  when onDestination(SwitchTab(INTERNET))
  then appShellState.activeTab == INTERNET

back_with_drawer_open_closes_drawer:
  given DefaultRootComponent, state.isDrawerOpen == true
  when onDestination(Back)
  then appShellState.isDrawerOpen == false, activeTab unchanged

back_with_non_empty_backStack_pops:
  given DefaultRootComponent, pushed local stack with extra entry
  when onDestination(Back)
  then appShellState.localState.navStack is popped
  [DEFERRED to phase-05+: MVP domain transitions in current Walking Skeleton never create
  non-empty backStack — Destination.Push not implemented, tabs are single-screen roots.
  Back FSM R2 (non-empty stack pop) testable when child screens with pushable destinations
  are added. Domain FSM logic covered in shared/.../domain/commonTest/PrimaryUserJourneyTest.kt]

back_on_LOCAL_root_emits_system_back:
  given DefaultRootComponent, LOCAL tab at root, drawer closed
  when onDestination(Back)
  then events flow emits RootEvent.SystemBack

retap_with_backStack_returns_POP_TO_ROOT:
  given DefaultRootComponent, LOCAL tab with non-empty backStack
  when onActiveTabRetap(LOCAL)
  then RetapOutcome == POP_TO_ROOT
  [DEFERRED to phase-05+: same reason as back_with_non_empty_backStack_pops above.
  RetapOutcome R1 POP_TO_ROOT testable when push destinations exist.]

retap_at_root_returns_NO_OP:
  given DefaultRootComponent, LOCAL tab at root
  when onActiveTabRetap(LOCAL)
  then RetapOutcome == NO_OP

open_design_catalog_switches_to_LOCAL:
  given DefaultRootComponent
  when onDestination(OpenDesignCatalog)
  then appShellState.activeTab == LOCAL, localState.navStack.active == LocalConfig.DesignCatalogRoot

select_section_cross_tab_auto_switches:
  given DefaultRootComponent, activeTab == LOCAL
  when onDestination(SelectSection(InternetSection.Profile))
  then appShellState.activeTab == INTERNET

open_drawer_shop_no_op:
  given DefaultRootComponent, activeTab == SHOP
  when onDestination(OpenDrawer)
  then appShellState.isDrawerOpen == false (SHOP has no drawer)
```

## REQUIRES Status

- **OQ-COMP-1 DEFERRED**: `StackNavigation.replaceAll(vararg C)` signature verification — плanner помечает DEFERRED. Frontend-dev реализующий `DefaultRootComponent.syncStack` должен:
  1. Проверить Decompose 3.1.0 источники или javadoc для `StackNavigation.replaceAll`
  2. Если `replaceAll(vararg C)` не существует — fallback: `nav.navigate { newList.dropLast(1).forEach { push(it) }; replaceAll(listOf(newList.last()))` или `nav.replaceAll(newList)`
  3. Зафиксировать найденную сигнатуру в комментарии в `DefaultRootComponent.syncStack`

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:compileDebugKotlin --no-configuration-cache
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
```

## Handoff Notes

- Phase-05 (AppShellScreen) зависит от phase-04: `DefaultRootComponent`, `RootComponent` interface, tab components, `NavigatorImpl` — все должны существовать
- Phase-07 (MainActivity wiring) зависит от phase-04: `DefaultRootComponent` должен быть доступен через Koin `factory<RootComponent> { (ctx) -> get<DefaultRootComponent>(parametersOf(ctx)) }`
