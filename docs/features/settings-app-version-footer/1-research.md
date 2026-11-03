---
date: 2026-07-26
researcher: Kent
ticket: SCH-2
feature: settings-app-version-footer
commit: 9fa96700
branch: kmp-skillify-4.0
---

# Research: Settings App Version Footer

## Summary

`settings-app-version-footer` is a Light-tier, client-only UI wiring change. Current code already has the necessary app build metadata source: `apps/android-next/build.gradle.kts:16-17` defines `versionCode = 1` and `versionName = "0.1.0"`, and `apps/android-next/build.gradle.kts:33-34` enables app `BuildConfig` generation. The current app-shell path passes only `BuildConfig.VERSION_NAME` from `MainActivity` to `AppShellScreen`, and that value is used only by the drawer footer today (`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:121-127`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:213-221`).

`DesignSettingsScreen` currently has one production caller and one preview caller. Production renders it from the app-shell LOCAL tab branch for `LocalConfig.SettingsRoot`; the screen itself accepts only design-style props/callbacks and uses a full-size `LazyColumn` inside `SchoolQuizDesignBackground` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`).

The drawer version footer is a separate existing behavior and must stay unchanged for this feature: it formats only `v$versionName`, is clickable, opens the existing About dialog, and delegates repeated version taps to the developer-mode path (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:40-96`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:318-337`).

No backend, storage, domain rules, repositories, Koin bindings, server contracts, migrations, or web research are needed. The only implementation surface found is app-layer metadata propagation plus Compose UI rendering in app-shell/settings presentation.

## Research Run Ledger

| Work item | Agent output | Session |
|---|---|---|
| Criterion 1 — `DesignSettingsScreen` call sites | `run/agents/SCH-2-research-criterion-1-call-sites.out` | `0c6d4e03-f5d5-42f3-b25f-c81ad5edc987` |
| Criterion 2 — app-layer version path | `run/agents/SCH-2-research-criterion-2-version-path.out` | `97b0f262-6b02-4b5d-9dd5-2a9c237c5803` |
| Criterion 3 — drawer footer version behavior | `run/agents/SCH-2-research-criterion-3-drawer-footer.out` | `b3822e1f-e853-431b-98b1-7bd61ebb85ab` |
| Criterion 4 — `DesignSettingsScreen` layout | `run/agents/SCH-2-research-criterion-4-settings-layout.out` | `df53c60a-9bf8-4294-921f-0f80bc9dee14` |
| Criterion 5 — tests/preview surface | `run/agents/SCH-2-research-criterion-5-tests-preview.out` | `b85cbfdf-f54b-419a-a9cc-9ddbf37b748b` |
| Core/design-system scan | `run/agents/SCH-2-core-scan-designsystem.out` | `d4a15a26-99eb-413c-80bb-312f2742c7b0` |
| Cross-feature scan | `run/agents/SCH-2-cross-feature-scan.out` | `042ec1af-f19b-4c4f-b9a9-41187e6d9fd1` |
| Web research | Skipped | Not required: spec is client-only/internal and found no external SDK/library/platform behavior requiring official web verification. |

Note: researcher agents attempted the required GLM breadth pass, but local GLM could not run because `ZAI_API_KEY`/`GLM_API_KEY` is not configured. No unverified GLM output is included in this synthesis.

## Architecture Overview

| Layer / module | Current role in this feature |
|---|---|
| `apps/android-next` | Owns app `BuildConfig` and root composition; current `MainActivity` passes `BuildConfig.VERSION_NAME` to `AppShellScreen` (`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`). |
| `android/feature/app-shell/presentation` | Owns shell routing and imports local settings UI; current `AppShellScreen` has `appVersionName` but no version-code parameter (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-127`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`). |
| `android/feature/local/settings/presentation` | Owns `DesignSettingsScreen`; current screen is a Compose view function with design props/callbacks only (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78`). |
| `android/core/designsystem` | Provides MaterialTheme/design background primitives used by settings; no dedicated app-version footer component found (`android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt:29-45`, `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`). |
| `shared/{core,feature}` domain/data | No feature-owned business/domain/data work required; spec Feature Domain Contract is N/A (`docs/features/settings-app-version-footer/0-spec.md:32`, `docs/features/settings-app-version-footer/0-spec.md:164-166`). |

## Existing Patterns

- App-layer build metadata is passed into library UI as parameters, not read from app `BuildConfig` inside Android library modules (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`).
- Small low-emphasis version text already exists in drawer footer via `MaterialTheme.typography.labelSmall` and `onBackground.copy(alpha = 0.6f)` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-81`).
- Settings screen already uses MaterialTheme typography and `onSurface.copy(alpha = ...)` for secondary text (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:139-145`).
- Compose screens are expected to receive state/callbacks/props and not resolve Koin or repositories directly (`AGENTS.md:12-17`, `docs/invariants.md:17-23`).

## Integration Points

| Integration point | Current signature / path | Research impact |
|---|---|---|
| `MainActivity` → `AppShellScreen` | `AppShellScreen(rootComponent, appVersionName = BuildConfig.VERSION_NAME, isDebugBuild = BuildConfig.DEBUG, ...)` (`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`) | Needs version code if app-shell/settings signatures require it. |
| `AppShellScreen` → drawer | `DrawerContent(versionName = appVersionName, onVersionTap = { rootComponent.onVersionTap(...) })` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:213-221`) | Existing drawer behavior is out of scope and should remain separate. |
| `AppShellScreen` → settings | `DesignSettingsScreen(selectedStyle, onStyleSelected, modifier = Modifier.padding(paddingValues))` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`) | Needs version display data if settings screen owns footer rendering. |
| `DesignSettingsScreenPreview` | Calls `DesignSettingsScreen(selectedStyle = Clean, onStyleSelected = {})` (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`) | Needs update if new params are required. |
| `AppShellScreenTest` | Calls `AppShellScreen(rootComponent = component, appVersionName = "test")` (`android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`) | Needs update if `AppShellScreen` adds a required version-code param. |

## Findings by Search Criterion

### 1. `DesignSettingsScreen` call sites and signature impact

| Area | Finding |
|---|---|
| Declaration | `DesignSettingsScreen` accepts `selectedStyle`, `onStyleSelected`, and `modifier`; it has no version parameters today (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-52`). |
| Production caller | `AppShellScreen.LocalTabContent` renders `DesignSettingsScreen` only for `LocalConfig.SettingsRoot` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:420-466`). |
| Preview caller | `DesignSettingsScreenPreview` calls `DesignSettingsScreen` with only selected style and callback (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`). |
| Completeness check | `grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared` found exactly the declaration, the app-shell production call, and the preview call. |
| App-shell callers | `AppShellScreen` has a production caller in `MainActivity` and an androidTest caller in `AppShellScreenTest` (`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-50`, `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`). |

### 2. Current app-layer version metadata path

| Area | Finding |
|---|---|
| Source of truth | App Gradle config declares `versionCode = 1`, `versionName = "0.1.0"`, and `buildFeatures.buildConfig = true` (`apps/android-next/build.gradle.kts:14-17`, `apps/android-next/build.gradle.kts:33-35`). |
| Current runtime propagation | `MainActivity` passes `BuildConfig.VERSION_NAME` and `BuildConfig.DEBUG` into `AppShellScreen`, but does not pass `BuildConfig.VERSION_CODE` (`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`). |
| App-shell public API | `AppShellScreen` currently exposes `appVersionName: String`; no `appVersionCode`/version-code parameter exists (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-127`). |
| BuildConfig boundary | App-shell KDoc explicitly says library modules cannot access app `BuildConfig.VERSION_NAME` or `BuildConfig.DEBUG` directly (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`). |
| Direct BuildConfig usage grep | `BuildConfig` usage in app-shell/settings path is app-layer code plus KDoc/comments; the settings library does not read app `BuildConfig` directly (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:35-36`, `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:43-44`). |

### 3. Existing drawer footer version behavior to preserve

| Area | Finding |
|---|---|
| Drawer API | `DrawerContent` receives `versionName` and `onVersionTap`, then forwards both to `DrawerFooter` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt:25-35`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt:70-78`). |
| Drawer label | `DrawerFooter` renders `text = "v$versionName"` using `MaterialTheme.typography.labelSmall` and `MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-81`). |
| Drawer tap behavior | The drawer version text is clickable and calls `onVersionTap`; `AppShellScreen` wires that to `rootComponent.onVersionTap(System.currentTimeMillis())` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:77-80`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:220-221`). |
| Dev-mode path | `DefaultRootComponent.onVersionTap` runs `ActivateDevModeUseCase` and can emit `RootEvent.DevModeActivated` / `RootEvent.DevModeAlreadyActive` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:140-144`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:318-337`). |
| About dialog | Drawer `About` action is local to `DrawerFooter` and displays `Версия $versionName` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:61-69`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:85-96`). |

### 4. Current `DesignSettingsScreen` layout

| Area | Finding |
|---|---|
| Current container | `DesignSettingsScreen` wraps content in `SchoolQuizDesignBackground(modifier = modifier.fillMaxSize())` (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:53-57`). |
| Current content | The only current child is a full-size `LazyColumn` with horizontal/vertical padding, header item, and `SchoolQuizDesignStyle.entries` items (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:58-76`). |
| Existing style controls | Each style card is clickable and invokes `onStyleSelected(style)` through `DesignStyleOption` (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:66-74`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:97-113`). |
| Existing low-emphasis text style | The screen already uses MaterialTheme text styles plus `onSurface.copy(alpha = ...)` for secondary text (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:139-145`). |
| Feasible pinned layout surface | `SchoolQuizDesignBackground` accepts `content: @Composable BoxScope.() -> Unit`, so settings UI can host a non-scroll sibling/overlay in addition to the `LazyColumn` (`android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`). |
| Shell insets | App-shell `Scaffold` passes `paddingValues` through to `LocalTabContent`, and settings receives them via `modifier = Modifier.padding(paddingValues)` (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:227-281`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:333-345`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`). |

### 5. Test / preview surface

| Area | Finding |
|---|---|
| Settings preview | `DesignSettingsScreenPreview` is the settings preview surface and must be updated if the screen signature gets required version parameters (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`). |
| Settings tests | `android/feature/local/settings/presentation/src/test/java/.gitkeep` and `src/androidTest/java/.gitkeep` are the only files under settings test source roots; no settings-specific production tests were found. |
| App-shell UI test call site | `AppShellScreenTest` renders `AppShellScreen(rootComponent = component, appVersionName = "test")`; a non-default `AppShellScreen` version-code parameter would need updating there (`android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`). |
| App-shell test deps | App-shell presentation already has androidTest Compose UI dependencies (`android/feature/app-shell/presentation/build.gradle.kts:49-57`). |
| Relevant gates | Canonical gate is `./gradlew ciCheck --no-configuration-cache`; app build is `./gradlew :apps:android-next:assembleDebug --no-configuration-cache`; Android/app JVM tests are `./gradlew test --no-configuration-cache` (`AGENTS.md:6-10`, `build.gradle.kts:27-35`). Instrumented test APK build is `./gradlew assembleDebugAndroidTest --no-configuration-cache` (`.claude/PROJECT-CONTEXT.md:14-20`). |

## Core / Design-System Scan

| Area | Finding |
|---|---|
| Design-system theme | `SchoolQuizTheme` provides Material3 `MaterialTheme` color scheme, shapes, and typography; typography switches by `SchoolQuizDesignStyle` (`android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt:29-45`). |
| Footer/token reuse | No dedicated app-version/footer component was found in `android/core/designsystem`; the closest reusable pattern is MaterialTheme typography/color usage already present in settings and drawer footer (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-81`). |
| Shared-core version concepts | Existing `version` fields in shared core are database/catalog/server-sync semantics, not app build metadata (`shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt:7-35`, `shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/model/Catalog.kt:21-41`). |
| Core impact | No shared-core package needs feature-specific business logic for this footer; the spec’s Feature Domain Contract is N/A (`docs/features/settings-app-version-footer/0-spec.md:32`, `docs/features/settings-app-version-footer/0-spec.md:164-166`). |

## Cross-Feature Interactions

### Dependency Graph

| Feature A | → | Feature B | Mechanism | File:line | Documented in ADR? |
|-----------|---|-----------|-----------|-----------|---------------------|
| app-shell | → | local/settings | direct import + Gradle project dependency | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:63`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`, `android/feature/app-shell/presentation/build.gradle.kts:10-11` | NO local settings ADR yet; existing route dependency predates this footer feature. |
| apps/android-next | → | app-shell | Gradle dependency + Compose call | `apps/android-next/build.gradle.kts:38-42`, `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50` | N/A — app composition root. |
| app-shell | → | quest / quest-authoring / quizzes-screen / economy / profile / leaderboard | existing direct composition imports in app-shell | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:61-69`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:15-25`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:50-55` | Partially documented as app-shell composition exception in existing feature ADRs; not changed by this feature. |
| local/settings | → | app-shell / other feature modules | none found by source grep for `feature.local.settings` beyond package/declaration and app-shell import | `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:3`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:63` | N/A — no reverse feature import found. |

### Bidirectional Coupling Risks

The cross-feature scanner found pre-existing bidirectional feature pairs elsewhere (`app-shell ↔ quest`, `app-shell ↔ quest-authoring`, `app-shell ↔ quizzes-screen`, `quest ↔ quest-authoring`). They are not introduced by `settings-app-version-footer`; design must avoid adding a new reverse dependency from local/settings back to app-shell or any other feature. The relevant invariant is `docs/invariants.md:25-31`.

### Shared SDK Across Features

| SDK | Used by | Recommended pattern (Context7 / official docs) | Current integration |
|-----|---------|------------------------------------------------|---------------------|
| Compose / Material3 | settings presentation, app-shell presentation, other presentation modules | Web research not run: this feature does not introduce a new SDK or change SDK ownership/lifecycle. Use existing project pattern: Compose screens as view functions receiving state/props (`AGENTS.md:12-17`, `docs/invariants.md:17-23`). | `android/feature/local/settings/presentation/build.gradle.kts:9-15`, `android/feature/app-shell/presentation/build.gradle.kts:26-32`. |
| Android app `BuildConfig` | app module passes metadata to app-shell | Web research not run: no new platform API behavior; local app Gradle config and existing comments are sufficient evidence. | `apps/android-next/build.gradle.kts:14-17`, `apps/android-next/build.gradle.kts:33-35`, `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:43-44`. |

### Undocumented Patterns (blockers для design)

- No feature-to-feature reflection was found by `grep -RIn --include='*.kt' 'Class.forName\|forName(' platform android shared apps`; the only reflection hit is Firebase SDK debug-provider reflection, not a feature coupling pattern (`platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt:29-35`).
- Existing `app-shell → local/settings` direct import has no local-settings ADR yet. Because it already exists and this feature only threads values across the same route, it is a **design note**, not a new blocker; design should document that no new cross-feature dependency is added.

### Handoff to design

Cross-feature dependency summary для `settings-app-version-footer`:
- `settings-app-version-footer` импортирует: none expected; implementation should not import app-shell/other feature modules from local settings.
- `settings-app-version-footer` используется: app-shell already imports and renders `DesignSettingsScreen` for `LocalConfig.SettingsRoot`.
- Bidirectional risks: no new risk if implementation only extends existing app → app-shell → local/settings parameter flow; pre-existing unrelated bidirectional pairs must not be widened.
- Shared SDK: Compose/Material3 and app `BuildConfig` are already used locally; no web-researcher was necessary.
- Undocumented patterns: no feature-to-feature reflection; existing app-shell → local/settings import has no ADR, so design should explicitly preserve “no new cross-feature dependency” in `03-decisions.md`.

Полные детали — в `1-research.md` секция "Cross-Feature Interactions". Учитывайте эти dependencies при design. Новые cross-feature связи без документирования в `03-decisions.md` = blocker.

## State Matrix Validation

### Пропущенные условия

- None. Spec state matrix is N/A for this UI-only feature (`docs/features/settings-app-version-footer/0-spec.md:182-184`).

### Несостыковки (матрица vs код)

- None. No state matrix exists and no branchy business state was found in the settings footer scope.

### Непокрытые комбинации

- None. Offline/fresh install/logout/process death journeys rely on static app build metadata, not repository/storage/network state.

### Domain Contract Mismatches

- None. Feature Domain Contract is N/A and code research found no need for domain/data contracts (`docs/features/settings-app-version-footer/0-spec.md:164-166`).

## Conditional Documents

| Document | Needed? | Rationale |
|---|---:|---|
| `06-api-contract.md` | No | No REST/WebSocket/push/API change. |
| `07-events.md` | No | Settings footer is display-only; no new events. |
| `08-storage-model.md` | No | No DB/storage/migration/persistence change. |

## Constraints

- Do not read app `BuildConfig` from `android/feature/local/settings/presentation` or `android/feature/app-shell/presentation`; the existing app-shell boundary documents that app metadata comes from app-layer parameters (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`).
- Do not connect settings footer to drawer `onVersionTap`; drawer version text is currently interactive and feeds developer-mode activation (`android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-81`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:318-337`).
- Do not implement the settings footer as a final `LazyColumn` item if the requirement remains “pinned to visible bottom”; current settings content is full-size scroll content (`android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:58-76`).
- Do not add domain/data/storage/networking/Koin work; spec Feature Domain Contract is N/A (`docs/features/settings-app-version-footer/0-spec.md:32`, `docs/features/settings-app-version-footer/0-spec.md:164-166`).
- Do not introduce new cross-feature dependencies or reverse dependency from local/settings to app-shell; cross-feature invariant is documented in `docs/invariants.md:25-31`.

## Open Questions

- None blocking for design. The remaining implementation-level detail is the exact presentation parameter type for `versionCode` (expected to be app `BuildConfig.VERSION_CODE`-compatible) while preserving the spec constraint that `versionName` and `versionCode` come from the app layer.

## Research Conclusion

Research is complete and ready for grounding/design. The implementation shape is feasible as a local parameter-threading/UI change:

1. app layer already has `BuildConfig.VERSION_NAME` and generated `BuildConfig.VERSION_CODE`;
2. app-shell already provides an app-version parameter boundary for library modules;
3. settings screen can receive display props and render a pinned footer without touching domain/data;
4. drawer footer/dev-mode/About behavior is separate and should remain unchanged;
5. no spec delta or user question is required.
