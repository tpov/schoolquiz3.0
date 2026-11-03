---
phase: phase-07
feature: app-shell-menu
date: 2026-04-18
---

# Phase-07: MainActivity Wiring + Koin Startup + SystemBack

## Goal

Подключить все собранные компоненты в `MainActivity`: `defaultComponentContext()`, `get<DefaultRootComponent>(parametersOf(ctx))`, `setContent { SchoolQuizTheme { AppShellScreen(rootComponent, appVersionName = BuildConfig.VERSION_NAME) } }` (H8: `appVersionName` передаётся из app layer, не используется `BuildConfig.VERSION_NAME` в library модулях), обработка `RootEvent.SystemBack` и `onNewIntent` deep link hook. После фазы — APK запускается на Android 8.0+, AppShellScreen видим, AC 28-30 выполнены.

## Scope

- `MainActivity.kt` — полная реализация (`defaultComponentContext`, Koin `get { parametersOf(ctx) }`, `lifecycleScope + repeatOnLifecycle`, `onNewIntent`)
- Lint/detekt/ktlint check (AC 30)
- Manual smoke test: launch APK на Android 8.0+ (AC 28-29)

## Layer

apps (entry point wiring)

## Role Inputs

- `backend.md` (MainActivity — AppCompatActivity lifecycle rules, scaffold ownership)
- `tests.md`

## Review Tags

- `concurrency-review`: `lifecycleScope.launch { repeatOnLifecycle(STARTED) { rootComponent.events.collect {} } }` — lifecycle-aware collection, проверка что collect не происходит в STOPPED state

## State Matrix Coverage

| FSM | Строки | Coverage |
|-----|--------|----------|
| Cold Start FSM | Entry: `MainActivity.onCreate` → `DefaultRootComponent init{}` | Integration smoke test |
| Back FSM step 4 | `RootEvent.SystemBack` → `moveTaskToBack(true)` | `KoinModuleWiringTest` + manual smoke test |
| Deep link hook | `onNewIntent` → `rootComponent.onDeepLink(DeepLink(uri))` | compile check (MVP stub) |

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 1: Walking Skeleton integration | backend-dev | `apps/android-next/MainActivity.kt:9` | `defaultComponentContext()` — правильный API (OQ-COMP-3 resolved) | Заменить stub `setContentView(TextView)` на Decompose + Compose entry | `./gradlew :apps:android-next:assembleDebug` |
| Problem 5: apps/android-next stub | backend-dev | `apps/android-next/MainActivity.kt:9`, `AppApplication.kt` | `startKoin` уже в AppApplication (phase-01) — MainActivity только создаёт component | `defaultComponentContext()` + `get<DefaultRootComponent>(parametersOf(ctx))` | assembleDebug + manual launch |
| Problem 2: Compose compiler + lifecycle | backend-dev | `lifecycle-runtime-ktx`, `lifecycle-runtime-compose` | `lifecycleScope + repeatOnLifecycle(STARTED)` — не просто `lifecycleScope.launch` | Lifecycle-aware Flow collection per `.claude/rules/lifecycle.md` | `./gradlew :apps:android-next:assembleDebug` |

## New Files

none (AppApplication.kt создан в phase-01)

## Modified Files

```
apps/android-next/src/main/java/.../AppApplication.kt  (verify all 3 modules in startKoin — финальная проверка)
apps/android-next/src/main/java/.../MainActivity.kt    (full implementation)
```

## Deleted Files

none

## Dependencies

- Phase-01 MUST complete (AppApplication, Koin modules, build.gradle.kts Compose deps, detekt+ktlint setup)
- Phase-02 MUST complete (SchoolQuizTheme)
- Phase-04 MUST complete (DefaultRootComponent, AppShellPresentationModule)
- Phase-05 MUST complete (AppShellScreen with appVersionName param)
- Phase-06 MUST complete (DrawerContent, DrawerFooter with AlertDialog About, BrandDrawerItem)

## Acceptance Criteria

1. `./gradlew :apps:android-next:assembleDebug` — BUILD SUCCESSFUL
2. APK запускается на Android 8.0+ (minSdk = 26), `AppShellScreen` видим (AC 28)
3. Back button работает: drawer closes → pop → switch LOCAL → exit (AC 29 — 4-step FSM)
4. `defaultComponentContext()` используется (НЕ `DefaultComponentContext(lifecycle, stateKeeper)`) — OQ-COMP-3
5. `lifecycleScope.launch { repeatOnLifecycle(STARTED) { events.collect {} } }` — lifecycle-aware
6. `onDestroy()` НЕ содержит business actions (`moveTaskToBack` / `endComponent` / kill signals) — per `.claude/rules/lifecycle.md`
7. AC 30: `./gradlew detekt ktlintCheck` — no new violations
8. `AppShellScreen(rootComponent, appVersionName = BuildConfig.VERSION_NAME)` — version name передаётся из app layer (H8 fix). `BuildConfig.VERSION_NAME` вызывается только в `MainActivity`, не в library modules.
9. Manual smoke journeys 7/8/9: edge swipe, scrim close, swipe close работают на non-SHOP tab (AC 29 part — gesturesEnabled=true).
10. Manual smoke: About footer dialog отображает версию без навигации в Settings (H3 fix).
11. Full-stack Koin wiring test green: `firebaseModule (test) + appShellDataModule + appShellPresentationModule` резолвят `DefaultRootComponent` (H5 fix — тест в phase-07 расширяет KoinModuleWiringTest).

## REQUIRES Status

- **OQ-COMP-2 DEFERRED**: `essentyLifecycle()` extension import — `defaultComponentContext()` используется per OQ-COMP-3 resolution (автоматически wires lifecycle). Ручной `LifecycleRegistry()` НЕ нужен в MainActivity.

## Tests Required

```
koin_full_stack_wiring:
  given startKoin { modules(testDataSourceModule, appShellDataModule, appShellPresentationModule) }
  when get<DefaultRootComponent>(parametersOf(testCtx()))
  then no MissingPropertyException, DefaultRootComponent returned

system_back_event_received:
  given DefaultRootComponent, at LOCAL root state, drawer closed
  when onDestination(Back) called multiple times to reach FSM step 4
  then RootEvent.SystemBack emitted on events Flow

deep_link_stub_no_crash:
  given DefaultRootComponent
  when onDeepLink(DeepLink("schoolquiz://test"))
  then no exception (MVP stub)
```

## Validation

```bash
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache

# Full-stack Koin wiring test (H5 — all 3 modules):
./gradlew :apps:android-next:test --no-configuration-cache

# L2 fix: correct APK path and activity name
adb install apps/android-next/build/outputs/apk/debug/android-next-debug.apk
adb shell am start -n com.tpov.schoolquiz.next/.MainActivity
# Expected: AppShellScreen with NavBar + hamburger visible

# Manual smoke: journeys 7-9 (H4 — edge swipe / scrim / swipe close)
# Journey 7: edge swipe open (gesturesEnabled=true on non-SHOP tab) — drag from left edge
# Journey 8: scrim close (tap outside drawer) — drawer closes, domain state updated
# Journey 9: swipe close (drag drawer to left) — same as scrim
```

## Handoff Notes

- После phase-07 — feature полностью завершена. Running app с full AppShell: NavigationBar, ModalNavigationDrawer, DrawerHeader с UserStats, per-tab sections, DesignCatalog в debug mode.
- Все AC 1-30 проверяемы на device/emulator.
- Future work: deep link URL patterns, `@Serializable` Config state-saving, Light theme, Badges data.
