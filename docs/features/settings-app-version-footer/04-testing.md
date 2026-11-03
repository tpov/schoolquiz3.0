---
date: 2026-07-26
feature: settings-app-version-footer
ticket: SCH-2
stage: design
owner: architect-component
---

# 04 — Testing Strategy: Settings App Version Footer

## 1. Scope

This feature is a client-only UI wiring change:

- Show `v<versionName> (<versionCode>)` at the visible bottom of the settings screen.
- Keep the footer small, low-emphasis, centered, pinned, and display-only.
- Pass app build metadata from `apps/android-next` through app-shell/settings UI parameters.
- Preserve existing drawer footer/About/developer-mode behavior.
- Add no domain, repository, use case, Koin, Room, storage, network, event, or navigation contract.

Research confirms the current test surface:

- `DesignSettingsScreen` currently has one production caller and one preview caller:
  - `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`
  - `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305`
- `AppShellScreen` currently has one production caller and one androidTest caller:
  - `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`
  - `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`
- App-shell has an intermediate private content function in the version propagation path:
  - `AppShellScreen` calls `AppShellContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-295`
  - `AppShellContent` signature is at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:318-326`
  - `AppShellContent` calls `LocalTabContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:338-346`
- Settings presentation test roots currently contain only `.gitkeep` files; there are no settings-specific production tests today.
- `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260` must be updated when `AppShellScreen` adds required `appVersionCode: Int`.

## 2. Test Surface Plan

| Surface | Purpose | Required coverage |
|---|---|---|
| Compile / stale call-site checks | Catch missing signature updates after adding required `appVersionCode`. | `AppShellScreen`, private `AppShellContent`, private `LocalTabContent`, `DesignSettingsScreen`, `DesignSettingsScreenPreview`, `MainActivity`, and `AppShellScreenTest` compile with the new parameter flow. |
| Direct settings Compose UI test | Validate the footer text and display-only semantics closest to the rendering owner. | Preferred coverage when settings presentation test dependencies are available or added by the implementation owner. |
| Existing app-shell androidTest | Validate existing app-shell test call site and routed settings screen behavior. | Required call-site update when `AppShellScreen` signature changes. Required fallback surface for pinned-bottom bounds assertion because app-shell already has Compose UI androidTest dependencies. |
| Code review / grep checks | Guard architecture invariants and side effects. | No app `BuildConfig` reads in library modules; no Koin/repository/storage access in settings UI; no `onVersionTap`, click, long-click, or pointer-input path from settings footer. |
| Manual UX smoke | Validate visual feel after deterministic checks pass. | Open settings, inspect footer, tap/long-press footer, then verify drawer footer behavior remains unchanged. |

## 3. Acceptance Criteria Mapping

| AC from `0-spec.md` | Required validation |
|---|---|
| 1. Settings visible bottom contains centered display-only text with both `versionName` and `versionCode`, not merely a last row floating after a short list. | Required instrumented Compose bounds assertion in settings presentation or existing app-shell androidTest: render settings with `v0.1.0 (1)`, capture root/settings container bounds and footer bounds, assert `footerBounds.bottom` is within a small tolerance such as `24.dp` of the container bottom, and assert `footerBounds.top` is in the lower viewport region so the footer is not directly after the short settings list. Code review the settings layout: footer is a pinned non-scroll child of the settings viewport, not a final `LazyColumn` item, and scroll content reserves bottom padding so the footer does not cover the final setting row. Manual UX smoke remains supplemental. |
| 2. Given `versionName = "0.1.0"` and `versionCode = 1`, the footer displays exactly `v0.1.0 (1)`. | Direct Compose UI assertion: `onNodeWithText("v0.1.0 (1)").assertIsDisplayed()`. Preview sample data should also use `appVersionName = "0.1.0"` and `appVersionCode = 1`. Production must use generated build metadata from `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`, not a hardcoded string. |
| 3. Footer text uses small typography and low-emphasis grey/on-surface color, centered horizontally. | Code review MaterialTheme-based styling: small typography such as `MaterialTheme.typography.labelSmall`, low-emphasis theme color such as `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`, center alignment, and full-width/centered footer placement. Manual UX smoke confirms the text is visually small, grey/low-emphasis, and centered. |
| 4. Offline, fresh install, logout, and process death still show the same build version footer. | Architecture and compile validation: version data comes from generated app `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE` passed through UI parameters, not from auth, network, Room, preferences, repositories, or process-local mutable state. Manual process-recreation smoke verifies the footer still renders. |
| 5. Tapping or long-pressing the settings footer causes no navigation, dialog, developer-mode action, storage write, or state mutation. | Direct Compose semantics assertion: the footer text node has no `SemanticsActions.OnClick`. Executable grep checks must inspect settings UI for `clickable`, `combinedClickable`, `pointerInput`, `onLongClick`, `SemanticsActions.OnClick`, `onVersionTap`, `DefaultRootComponent.onVersionTap`, storage, repository, or navigation callback wiring. Existing `clickable` matches in settings must belong to design-style option rows and not the footer. Manual tap/long-press smoke confirms no visible effect. |
| 6. Existing drawer footer, drawer version tap behavior, and About dialog behavior are unchanged. | Implementation should not change `DrawerContent`, `DrawerFooter`, or `DefaultRootComponent.onVersionTap` for this feature. Manual drawer smoke confirms drawer still shows its existing version label, About dialog still opens from drawer behavior, and developer-mode repeated tap behavior remains drawer-owned. Existing app-shell tests must compile. |
| 7. Relevant compile/tests show no stale `DesignSettingsScreen` / `AppShellScreen` call sites. | Run grep call-site checks and compile/test commands listed in §5. `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-295`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:318-326`, and `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:338-346` must thread version props through private `AppShellContent`; `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305` preview and `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260` androidTest call site must be updated after signature changes. |
| Debug and release visibility from functional requirements. | Static/code review must prove the settings footer is not gated by `isDebugBuild`, `BuildConfig.DEBUG`, `canSeeDesignCatalog`, or any debug-only branch. Build validation includes debug assemble and release assemble. |

## 4. Suggested Automated Tests

Direct settings Compose UI tests are the cleanest behavioral tests. Settings presentation currently has only `.gitkeep` test roots. Implementation can add the required Compose UI test dependencies through the proper owner or place coverage in the existing app-shell androidTest surface, which already has Compose UI androidTest dependencies.

### 4.1 Exact label

```kotlin
@Test
fun settingsFooter_displaysCanonicalVersionLabel() {
    composeRule.setContent {
        SchoolQuizTheme {
            DesignSettingsScreen(
                selectedStyle = SchoolQuizDesignStyle.Clean,
                onStyleSelected = {},
                appVersionName = "0.1.0",
                appVersionCode = 1,
            )
        }
    }

    composeRule.onNodeWithText("v0.1.0 (1)").assertIsDisplayed()
}
```

### 4.2 Display-only semantics

```kotlin
@Test
fun settingsFooter_isDisplayOnly() {
    composeRule.setContent {
        SchoolQuizTheme {
            DesignSettingsScreen(
                selectedStyle = SchoolQuizDesignStyle.Clean,
                onStyleSelected = {},
                appVersionName = "0.1.0",
                appVersionCode = 1,
            )
        }
    }

    composeRule
        .onNodeWithText("v0.1.0 (1)")
        .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
}
```

### 4.3 Pinned visible-bottom placement

Required instrumented Compose bounds assertion:

```kotlin
@Test
fun settingsFooter_isPinnedToVisibleBottom() {
    composeRule.setContent {
        SchoolQuizTheme {
            DesignSettingsScreen(
                selectedStyle = SchoolQuizDesignStyle.Clean,
                onStyleSelected = {},
                appVersionName = "0.1.0",
                appVersionCode = 1,
            )
        }
    }

    val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
    val footerBounds = composeRule
        .onNodeWithText("v0.1.0 (1)")
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()
    val tolerance = 24.dp

    assertThat(rootBounds.bottom - footerBounds.bottom).isAtMost(tolerance)
    assertThat(footerBounds.top).isGreaterThan(rootBounds.top + rootBounds.height * 0.70f)
}
```

This test may render `DesignSettingsScreen` directly in the settings module or render the app-shell route in `android/feature/app-shell/presentation/src/androidTest`. The assertion is mandatory for AC1; manual smoke is only a supplemental visual check.

When adding direct settings UI tests requires build/scaffold edits, that implementation work must respect repository ownership rules. The production design remains parameter-based and display-only regardless of where automated UI coverage lands.

## 5. Required Validation Commands

Run these after implementation:

```bash
grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared
grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared
grep -RIn --include='*.kt' 'AppShellContent(' android/feature/app-shell apps
grep -RIn --include='*.kt' 'LocalTabContent(' android/feature/app-shell apps
```

Expected grep outcome:

- All `DesignSettingsScreen` call sites pass `appVersionName` and required `appVersionCode`.
- All `AppShellScreen` call sites pass required `appVersionCode`.
- All `AppShellContent` call sites/signatures thread `appVersionName` and required `appVersionCode`.
- All `LocalTabContent` call sites/signatures thread `appVersionName` and required `appVersionCode`.
- `DesignSettingsScreenPreview` is updated with sample values.
- `AppShellScreenTest` is updated with a test version code.

Compile and test commands:

```bash
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew :apps:android-next:assembleRelease --no-configuration-cache
./gradlew test --no-configuration-cache
./gradlew assembleDebugAndroidTest --no-configuration-cache
./gradlew ciCheck --no-configuration-cache
```

Command intent:

- `:apps:android-next:assembleDebug` validates app-layer `BuildConfig.VERSION_CODE` wiring through app-shell/settings UI.
- `:apps:android-next:assembleRelease` validates release-flavored visibility and compile wiring when release assemble is available in the environment.
- `test` validates Android/app JVM tests.
- `assembleDebugAndroidTest` validates the existing app-shell androidTest call site after the `AppShellScreen` signature change.
- `ciCheck` is the canonical local quality gate.

## 6. Architecture Regression Checks

Use grep/code review to confirm:

```bash
grep -RIn --include='*.kt' 'BuildConfig' android/feature/app-shell android/feature/local/settings
grep -RIn --include='*.kt' -E 'isDebugBuild|BuildConfig.DEBUG|canSeeDesignCatalog' android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt
grep -RIn --include='*.kt' 'onVersionTap' android/feature/local/settings android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt
grep -RIn --include='*.kt' -E 'clickable|combinedClickable|pointerInput|onLongClick|SemanticsActions\\.OnClick' android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui
grep -RIn --include='*.kt' -E 'getKoin\(|koinInject\(|inject<' android/feature/local/settings android/feature/app-shell
```

Expected result:

- No app `BuildConfig` read is introduced in app-shell or settings library modules.
- Settings footer is not gated by `isDebugBuild`, `BuildConfig.DEBUG`, `canSeeDesignCatalog`, or any debug-only branch. Existing debug-gated design-catalog code remains separate from `LocalConfig.SettingsRoot`.
- Settings footer does not call or receive `onVersionTap`.
- Settings UI interaction primitive matches belong to existing design-style option rows and not to the version footer.
- Settings footer does not resolve Koin and does not access repositories, storage, or platform APIs.
- Any existing drawer `onVersionTap` reference remains drawer-owned and unchanged.

## 7. Manual UX Smoke

1. Build and launch `apps/android-next`.
2. Open drawer and select «Настройки».
3. Confirm the settings screen shows existing design settings plus `v0.1.0 (1)` or the current build metadata at the visible bottom.
4. Confirm the footer is horizontally centered, small, and low-emphasis grey/on-surface.
5. Tap and long-press the settings footer; confirm no navigation, dialog, developer-mode state, storage write, or visible state change.
6. Open the drawer; confirm existing drawer version footer/About/developer-mode behavior is unchanged.

## 8. Non-Coverage / N/A

- Domain tests: N/A, Feature Domain Contract is N/A.
- Repository/use-case tests: N/A, no repository or use case is introduced.
- Room/storage tests: N/A, no entity, DAO, migration, cache, SharedPreferences, or DataStore change is introduced.
- Network/Firebase tests: N/A, no network or Firebase behavior is introduced.
- Event tests: N/A, settings footer emits no events.
