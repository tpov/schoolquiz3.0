# Phase 1 Tests — Settings App Version Footer UI Slice

## Scope

Add or adjust tests for a UI-only change. No domain/data/repository/use-case/storage/network/Koin tests are required because Feature Domain Contract is N/A.

Do not add Gradle dependency changes for this phase. Prefer the existing app-shell androidTest surface:

- `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt`

Settings presentation test roots currently contain only `.gitkeep` files, and direct settings Compose UI tests would require dependency/scaffold decisions outside SCH-2’s constraints.

## Required Scenarios

### settings_footer_exact_label

- Given the settings route or settings screen is rendered with `appVersionName = "0.1.0"` and `appVersionCode = 1`.
- When the Compose tree is queried.
- Then a displayed node exists with exact text `v0.1.0 (1)`.
- Fixture notes: render through existing app-shell androidTest route if direct settings test dependencies are unavailable.

### settings_footer_display_only_semantics

- Given the footer text `v0.1.0 (1)` is displayed.
- When its semantics are inspected.
- Then it has no click action and no long-click action.
- And grep/code review confirms settings footer code has no `clickable`, `combinedClickable`, `pointerInput`, `onLongClick`, `onVersionTap`, navigation callback, dialog callback, storage call, analytics/event emission, repository call, or Koin resolution.
- Edge case: existing `DesignStyleOption` rows remain clickable and are not findings.

### settings_footer_pinned_visible_bottom_bounds

- Given the settings route or settings screen is rendered in a fixed viewport.
- When root/settings container bounds and footer bounds are measured.
- Then the footer bottom is within a small tolerance of the visible settings container bottom.
- And the footer top is in the lower viewport region, proving it is pinned to the viewport rather than appearing directly after the short settings list.
- And the last design-style option remains reachable/readable because scroll content reserves bottom padding.

### app_shell_required_version_code_stale_call_site_compile

- Given `appVersionCode` is a required parameter with no default on `AppShellScreen` and `DesignSettingsScreen`.
- When grep call-site checks and Gradle compile run.
- Then all known `AppShellScreen`, `AppShellContent`, `LocalTabContent`, and `DesignSettingsScreen` declaration/call sites thread both version values.
- And `DesignSettingsScreenPreview` passes sample values.
- And `AppShellScreenTest` passes deterministic test values.

### drawer_version_behavior_unchanged_guard

- Given the drawer footer behavior predates SCH-2.
- When implementation is reviewed and drawer is smoke-tested.
- Then `DrawerContent`, `DrawerFooter`, and `DefaultRootComponent.onVersionTap` behavior is unchanged.
- And drawer footer remains interactive/drawer-owned.
- And settings footer does not reuse `DrawerFooter` and does not call `onVersionTap`.

### debug_release_visibility_guard

- Given debug and release variants compile.
- When the settings route is rendered.
- Then the settings footer is not conditioned on `isDebugBuild`, `BuildConfig.DEBUG`, `canSeeDesignCatalog`, user stats, auth state, network state, or storage state.

## Fakes / Fixtures

- Reuse existing `AppShellScreenTest` fixture style: `DefaultRootComponent` with fake `UserStatsRepository`, fake `SyncScheduler`, fake quest/home/quizzes components, and `SchoolQuizTheme`.
- If a test needs the settings route active, use existing app-shell domain/navigation APIs or a test component setup that reaches `LocalConfig.SettingsRoot`; do not add production test hooks for this feature.
- Use deterministic version values:
  - exact-label scenarios: `appVersionName = "0.1.0"`, `appVersionCode = 1`;
  - compile-only existing screen tests may use `appVersionName = "test"`, `appVersionCode = 1`.

## Edge Cases

- Empty or unusual `versionName` is not a business rule for this phase; the UI should still format the received values without fetching fallback metadata.
- Negative or non-monotonic version codes are outside UI responsibility; app Gradle owns generated values.
- Offline, logged-out, fresh install, and process death require no special fakes because metadata is static app build data.
- Existing settings style-card click behavior must remain unchanged.
- Existing drawer About dialog/developer-mode repeated tap path must remain unchanged.

## Grep Guards

Run and inspect:

| Check | Expected result |
|---|---|
| `grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared` | All declaration/call sites include required version props. |
| `grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared` | All declaration/call sites include required `appVersionCode`. |
| `grep -RIn --include='*.kt' 'AppShellContent(' android/feature/app-shell apps` | Private call/signature thread both version values. |
| `grep -RIn --include='*.kt' 'LocalTabContent(' android/feature/app-shell apps` | Private call/signature thread both version values. |
| `grep -RIn --include='*.kt' 'BuildConfig' android/feature/app-shell android/feature/local/settings` | No library module reads app BuildConfig directly. |
| `grep -RIn --include='*.kt' 'onVersionTap' android/feature/local/settings android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt` | No settings footer wiring to drawer tap behavior. |
| `grep -RIn --include='*.kt' -E 'clickable|combinedClickable|pointerInput|onLongClick|SemanticsActions\\.OnClick' android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui` | Interaction primitives are limited to existing design controls/tests, not the footer. |
| `grep -RIn --include='*.kt' -E 'getKoin\\(|koinInject\\(|inject<' android/feature/local/settings android/feature/app-shell` | No Koin resolution is introduced in Compose screens/settings footer path. |

## Validation Commands

| Command | Expected result |
|---|---|
| `./gradlew ciCheck --no-configuration-cache` | Canonical local gate passes. |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | Debug app compiles with generated version-code wiring. |
| `./gradlew :apps:android-next:assembleRelease --no-configuration-cache` | Release app compiles and footer is not debug-only by construction. |
| `./gradlew test --no-configuration-cache` | Android/app JVM tests pass. |
| `./gradlew assembleDebugAndroidTest --no-configuration-cache` | Instrumented test APK compiles, including updated app-shell androidTest call sites and footer assertions. |

## Manual Smoke

1. Build and launch the app.
2. Open drawer and select «Настройки».
3. Confirm existing design settings remain visible.
4. Confirm footer text appears as the current build metadata, for example `v0.1.0 (1)`.
5. Confirm footer is centered, low-emphasis, small, and pinned to the visible settings bottom.
6. Tap and long-press the settings footer; confirm no navigation, dialog, developer-mode change, storage write, snackbar, or visible state change.
7. Open the drawer; confirm drawer footer/About/repeated-tap behavior remains unchanged.
