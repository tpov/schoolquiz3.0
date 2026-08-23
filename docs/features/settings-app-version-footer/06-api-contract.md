---
date: 2026-07-26
feature: settings-app-version-footer
ticket: SCH-2
stage: design
owner: architect-component
---

# 06 — API Contract: Settings App Version Footer

## 1. External / Backend / Domain Contract

External/backend/domain API contract: **N/A**.

This feature does not add or change:

- REST, WebSocket, push, Firebase, or server payload contracts.
- Domain models, domain policies, state machines, repositories, use cases, or Feature Domain Contract.
- Koin modules, Room entities, DAOs, migrations, storage, analytics, or events.
- Navigation destinations or app-shell domain navigation contracts.

This document is the signature source of truth only for the internal Compose UI contracts needed to thread static app build metadata from `apps/android-next` into the settings UI.

## 2. App Build Metadata Source

`apps/android-next` remains the only layer that reads generated app `BuildConfig` values.

`apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44` must pass both values into `AppShellScreen`:

- `appVersionName = BuildConfig.VERSION_NAME`
- `appVersionCode = BuildConfig.VERSION_CODE`

Android library modules must not read app `BuildConfig` directly.

## 3. Canonical Internal Compose UI Signatures

### 3.1 `AppShellScreen`

Current signature reference: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:121-127`.

Canonical target signature:

```kotlin
@Composable
fun AppShellScreen(
    rootComponent: DefaultRootComponent,
    appVersionName: String,
    appVersionCode: Int,
    isDebugBuild: Boolean = false,
    selectedDesignStyle: SchoolQuizDesignStyle = SchoolQuizDesignStyle.Main,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

Contract notes:

- `appVersionName: String` has no default.
- `appVersionCode: Int` has no default.
- `appVersionCode: Int` is required and placed immediately after `appVersionName: String`.
- Existing defaults remain unchanged for:
  - `isDebugBuild: Boolean = false`
  - `selectedDesignStyle: SchoolQuizDesignStyle = SchoolQuizDesignStyle.Main`
  - `onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit = {}`
  - `modifier: Modifier = Modifier`
- Existing `DefaultRootComponent` behavior is unchanged.
- Existing drawer path remains separate: `AppShellScreen` continues to pass only `appVersionName` to drawer footer behavior.
- `AppShellScreen` passes both `appVersionName` and `appVersionCode` to private `AppShellContent`.

### 3.2 Private `AppShellContent`

Current call-site and signature references:

- `AppShellScreen` calls `AppShellContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-295`.
- `AppShellContent` signature is at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:318-326`.
- `AppShellContent` calls `LocalTabContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:338-346`.

Canonical target signature:

```kotlin
@Composable
private fun AppShellContent(
    rootComponent: DefaultRootComponent,
    state: AppShellState,
    appVersionName: String,
    appVersionCode: Int,
    paddingValues: PaddingValues,
    uiAccess: AppShellUiAccess,
    tournamentOverviewState: Map<String, TournamentOverviewLoadState>,
    selectedDesignStyle: SchoolQuizDesignStyle,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit,
)
```

Contract notes:

- `appVersionName` and `appVersionCode` are threaded from `AppShellScreen`.
- `appVersionCode: Int` is required and placed immediately after `appVersionName: String`.
- `AppShellContent` passes both values to `LocalTabContent` in the `Tab.LOCAL` branch.
- Drawer, internet tab, quizzes overlay, and other shell paths do not consume `appVersionCode`.

### 3.3 Private `LocalTabContent`

Current signature reference: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:422-430`.

Canonical target signature:

```kotlin
@Composable
private fun LocalTabContent(
    rootComponent: DefaultRootComponent,
    screen: LocalScreenComponent,
    appVersionName: String,
    appVersionCode: Int,
    paddingValues: PaddingValues,
    canSeeDesignCatalog: Boolean,
    canManagePublicShelves: Boolean,
    selectedDesignStyle: SchoolQuizDesignStyle,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit,
)
```

Contract notes:

- `appVersionName` and `appVersionCode` are threaded from `AppShellContent`.
- `appVersionCode: Int` is required and placed immediately after `appVersionName: String`.
- Only the `LocalConfig.SettingsRoot` branch consumes these values.
- No `LocalTabComponent`, app-shell domain navigation, or Decompose child-stack contract changes are introduced.

### 3.4 `DesignSettingsScreen`

Current signature reference: `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-52`.

Canonical target signature:

```kotlin
@Composable
fun DesignSettingsScreen(
    selectedStyle: SchoolQuizDesignStyle,
    onStyleSelected: (SchoolQuizDesignStyle) -> Unit,
    appVersionName: String,
    appVersionCode: Int,
    modifier: Modifier = Modifier,
)
```

Contract notes:

- `appVersionName: String` has no default.
- `appVersionCode: Int` has no default.
- `appVersionCode: Int` is required and placed immediately after `appVersionName: String`.
- Existing `modifier: Modifier = Modifier` default remains unchanged.
- `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305` preview call site must pass sample values: `appVersionName = "0.1.0"` and `appVersionCode = 1`.
- The screen formats the label as `v$appVersionName ($appVersionCode)` and renders `v0.1.0 (1)` for the sample values above.
- The footer is display-only and does not expose a click, long-click, `onVersionTap`, About, developer-mode, storage, navigation, analytics, or event callback.

## 4. Required Call-Site Updates

| Call site | Current evidence | Required update |
|---|---|---|
| `MainActivity` production app composition | `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44` passes `BuildConfig.VERSION_NAME` and `BuildConfig.DEBUG`. | Add `appVersionCode = BuildConfig.VERSION_CODE` immediately after `appVersionName = BuildConfig.VERSION_NAME`. |
| `AppShellScreen` → private `AppShellContent` | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-295` calls `AppShellContent` without version props. | Pass both `appVersionName` and `appVersionCode` to `AppShellContent`. |
| `AppShellContent` → private `LocalTabContent` | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:338-346` calls `LocalTabContent` without version props. | Pass both `appVersionName` and `appVersionCode` to `LocalTabContent`. |
| `LocalTabContent` → `DesignSettingsScreen` | `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:422-430` private signature and `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466` settings branch currently pass design props only. | Pass `appVersionName` and `appVersionCode` to `DesignSettingsScreen` in the `LocalConfig.SettingsRoot` branch. |
| `DesignSettingsScreenPreview` | `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305` calls `DesignSettingsScreen` with design props only. | Add sample `appVersionName = "0.1.0"` and `appVersionCode = 1`. |
| `AppShellScreenTest` | `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260` calls `AppShellScreen(rootComponent = component, appVersionName = "test")`. | Add a deterministic `appVersionCode` value, for example `appVersionCode = 1`. |

## 5. Explicit Non-Contracts

Do not introduce:

- `AppVersionInfo` model/provider.
- Repository, use case, policy, domain model, or Koin binding.
- Room, SharedPreferences, DataStore, migration, cache, or storage record.
- Event, analytics event, click callback, long-click callback, or `onVersionTap` contract for the settings footer.
- New navigation route, About screen, About dialog behavior, or developer-mode behavior.
- Direct app `BuildConfig` usage from `android/feature/app-shell/presentation` or `android/feature/local/settings/presentation`.

## 6. Compatibility Policy

This is an internal source-breaking UI API change inside the app repository. The source break is intentional:

- Required `appVersionCode: Int` exposes stale call sites at compile time.
- Call sites must be updated atomically with the signature changes.
- No default value should be added for `appVersionName` or `appVersionCode`.
