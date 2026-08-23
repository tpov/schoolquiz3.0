# Phase 1 Frontend Tasks — Settings App Version Footer UI Slice

## Modify MainActivity app metadata handoff

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt`
- **Action:** Modify the existing `AppShellScreen` call in app composition.
- **Target symbols:** `MainActivity.onCreate`, `AppShellScreen` call.
- **Inputs / outputs:**
  - Input: generated app `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE` from the app module.
  - Output: app-shell receives both `appVersionName` and required `appVersionCode`.
- **Edge cases:**
  - Keep `BuildConfig.DEBUG` only for existing debug-gated shell behavior.
  - Do not move BuildConfig access into app-shell/settings library modules.
  - Do not change SharedPreferences design-style persistence.
- **Canonical reference / rationale:**
  - `docs/features/settings-app-version-footer/06-api-contract.md:24-33`
  - Current call evidence: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`
  - App version fields exist at `apps/android-next/build.gradle.kts:14-17`, with BuildConfig enabled at `apps/android-next/build.gradle.kts:33-35`.

## Modify AppShellScreen public UI API and private version propagation

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt`
- **Action:** Modify the public app-shell Compose API and private content renderers to thread both version values to settings.
- **Target symbols:**
  - Public `AppShellScreen` signature; canonical target is in `docs/features/settings-app-version-footer/06-api-contract.md:37-68`.
  - Private `AppShellContent`; canonical target is in `docs/features/settings-app-version-footer/06-api-contract.md:70-100`.
  - Private `LocalTabContent`; canonical target is in `docs/features/settings-app-version-footer/06-api-contract.md:102-128`.
  - `LocalConfig.SettingsRoot` branch at `AppShellScreen.kt:461-466`.
- **Inputs / outputs:**
  - Input: required `appVersionName: String` and `appVersionCode: Int` from app layer.
  - Output: `DesignSettingsScreen` receives both values only when rendering settings.
  - Existing drawer output remains only `versionName = appVersionName`.
- **Edge cases:**
  - Do not add a default value for `appVersionCode`; compile should catch stale call sites.
  - Do not pass `appVersionCode` to drawer unless a future feature explicitly changes drawer format.
  - Do not gate settings footer by `isDebugBuild`, `canSeeDesignCatalog`, or design-catalog visibility.
  - Do not change `DefaultRootComponent`, navigation routes, child-stack config, drawer sync, snackbar/event collection, or coroutine/Flow logic.
  - Preserve the existing one-way app-shell → local/settings dependency and add no reverse import.
- **Canonical reference / rationale:**
  - `docs/features/settings-app-version-footer/06-api-contract.md:37-68`
  - `docs/features/settings-app-version-footer/06-api-contract.md:70-100`
  - `docs/features/settings-app-version-footer/06-api-contract.md:102-128`
  - Required call-site updates: `docs/features/settings-app-version-footer/06-api-contract.md:157-166`
  - Drawer preservation: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-96`

## Modify DesignSettingsScreen signature and pinned footer layout

- **Файл:** `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt`
- **Action:** Modify the settings screen public Compose API, render a pinned footer sibling, and update preview sample data.
- **Target symbols:**
  - Public `DesignSettingsScreen`; canonical target is in `docs/features/settings-app-version-footer/06-api-contract.md:130-155`.
  - Existing `LazyColumn` settings content at `DesignSettingsScreen.kt:58-76`.
  - `DesignSettingsScreenPreview` at `DesignSettingsScreen.kt:298-305`.
- **Inputs / outputs:**
  - Input: `selectedStyle`, `onStyleSelected`, required `appVersionName`, required `appVersionCode`, and existing optional `modifier`.
  - Output: existing design controls plus passive footer text formatted as `v<versionName> (<versionCode>)`.
- **Behavior / layout tasks:**
  - Keep `SchoolQuizDesignBackground(modifier = modifier.fillMaxSize())` as the settings viewport owner.
  - Render footer as a non-scroll sibling aligned to bottom center of the settings viewport, using `SchoolQuizDesignBackground` `BoxScope`.
  - Reserve enough bottom padding in the `LazyColumn` content so the footer does not cover the last design style option.
  - Use MaterialTheme small typography and low-emphasis on-surface/on-background-family color consistent with existing settings/drawer patterns.
  - Center text horizontally and keep it visually passive.
  - Update preview with sample `appVersionName = "0.1.0"` and `appVersionCode = 1`.
- **Edge cases:**
  - Do not implement the footer as a final `LazyColumn` item.
  - Do not add `clickable`, `combinedClickable`, `pointerInput`, `onLongClick`, callback parameters, `onVersionTap`, navigation, dialog, analytics, storage, Koin, repository, or platform lookup behavior.
  - Do not import app-shell/app module types into local settings.
  - Preserve `DesignStyleOption` click behavior and design-style selection semantics.
  - Keep footer visible regardless of debug/release/user/network/auth state.
- **Canonical reference / rationale:**
  - `docs/features/settings-app-version-footer/06-api-contract.md:130-155`
  - Pinned layout feasibility: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`
  - Existing settings low-emphasis text pattern: `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, `:139-145`
  - Display-only behavior design: `docs/features/settings-app-version-footer/02-behavior.md`

## Test-surface ownership note

- `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt` is test-dev-owned for this phase.
- Frontend-dev should not edit androidTest files except through lead coordination.
- Required app-shell androidTest call-site updates and footer assertions are specified in `tests.md`.
