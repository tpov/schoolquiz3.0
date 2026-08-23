---
date: 2026-07-26
feature: settings-app-version-footer
ticket: SCH-2
stage: design
---

# 03 — Decisions: Settings App Version Footer

## Decision Index

| ADR | Status | Summary |
|---|---|---|
| ADR-SCH2-01 | Accepted | App module remains the source of build version metadata. |
| ADR-SCH2-02 | Accepted | Settings screen owns the pinned footer layout. |
| ADR-SCH2-03 | Accepted | Settings footer is display-only and separate from drawer footer behavior. |
| ADR-SCH2-04 | Accepted | No domain/data/DI/storage/events/backend work. |
| ADR-SCH2-05 | Accepted | Preserve existing one-way app-shell to local/settings dependency; add no new cross-feature dependency. |

## ADR-SCH2-01 — App module remains the source of build version metadata

**Status:** Accepted

### Decision

Read generated app `VERSION_NAME` and `VERSION_CODE` only from `apps/android-next`, then pass them through Compose parameters to the settings UI. The app-shell and settings library modules must not read app generated `BuildConfig` directly.

Canonical internal Compose UI signatures are documented in `06-api-contract.md` § Internal Compose UI signatures. This ADR records ownership and dependency direction only.

### Rationale

- App Gradle config already defines `versionCode` and `versionName` at `apps/android-next/build.gradle.kts:16-17`.
- BuildConfig generation is already enabled at `apps/android-next/build.gradle.kts:33-34`.
- `MainActivity` is already the root app composition boundary and currently passes `BuildConfig.VERSION_NAME` and `BuildConfig.DEBUG` at `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`.
- App-shell source already documents that library modules cannot access app `BuildConfig` directly at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`.
- Passing static display values keeps Compose screens as view functions and preserves the presentation invariant.

### Alternatives Considered

1. **Read app `BuildConfig` directly in app-shell or settings presentation.** Rejected because these are library modules and the existing boundary explicitly says app `BuildConfig` is passed from the app layer.
2. **Introduce a shared/core app-info provider or domain model.** Rejected because SCH-2 has Feature Domain Contract N/A and no business rule; a provider would add a second source of truth and unnecessary DI surface.
3. **Pass a preformatted version label from the app layer.** Rejected because the settings UI contract needs both fields available for canonical formatting and tests; preformatting would hide missing `versionCode` propagation.
4. **Hardcode `v0.1.0 (1)` in settings.** Rejected because release metadata must follow generated build values.

### Consequences

- `VERSION_CODE` must be added next to existing `VERSION_NAME` propagation.
- The version-code parameter should be required so stale call sites fail during compilation, including production app composition, private shell render hops, settings preview, and the app-shell androidTest caller at `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:254-260`.
- Private shell content rendering must also receive the required version props from `AppShellScreen` and forward them to local tab rendering.
- The drawer can keep receiving only the existing version-name value because SCH-2 does not alter drawer display.

## ADR-SCH2-02 — Settings screen owns the pinned footer layout

**Status:** Accepted

### Decision

Render the version footer inside `DesignSettingsScreen` as a non-scroll sibling pinned to the bottom center of the settings viewport. Keep the existing settings controls in the scroll content and reserve bottom padding so the footer does not cover the final item.

For this ADR, "settings viewport" means the `DesignSettingsScreen` root after app-shell applies `Modifier.padding(paddingValues)` for scaffold insets at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`. The footer is aligned to that padded settings root, not to raw device screen bounds and not under the app-shell bottom bar.

### Rationale

- The product requirement says the version belongs at the bottom of the settings screen, not as a global shell overlay.
- Current `DesignSettingsScreen` owns the settings body and uses `SchoolQuizDesignBackground` plus a full-size `LazyColumn` at `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78`.
- `SchoolQuizDesignBackground` exposes `BoxScope` content, enabling an independently aligned footer sibling at `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`.
- Existing settings text already uses MaterialTheme low-emphasis patterns at `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, so no new design-system primitive is required.
- App-shell already supplies scaffold padding to the settings branch at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`, so the pinned footer should respect that padded container.

### Alternatives Considered

1. **Append the footer as the last `LazyColumn` item.** Rejected because the footer could appear directly after a short list rather than pinned to the visible bottom of the viewport.
2. **Render the footer as a route-specific shell overlay.** Rejected because shell UI would gain settings-specific layout responsibility and inset/overlap concerns.
3. **Create a nested settings `Scaffold` with a bottom bar.** Rejected because app-shell already owns the outer scaffold; a second scaffold for passive text adds layout complexity without product value.
4. **Create a reusable design-system footer component.** Rejected because research found no broader app-version footer primitive need; local settings rendering is sufficient.

### Consequences

- The settings preview at `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:298-305` must be updated with sample version values.
- Layout implementation must include bottom padding in scroll content.
- Footer alignment must target the padded settings root provided by the existing shell scaffold inset flow.
- Visual style should remain small, centered, and low-emphasis using MaterialTheme.

## ADR-SCH2-03 — Settings footer is display-only and separate from drawer footer behavior

**Status:** Accepted

### Decision

The settings footer is passive text. It must not be clickable, long-clickable, pointer-input driven, or connected to About/developer-mode behavior. The existing drawer footer remains the only current version surface with drawer About and repeated-tap behavior.

### Rationale

- Spec asks to show version text in settings and explicitly defines the settings footer as display-only.
- Existing drawer footer text is clickable at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-81`.
- Existing drawer About dialog lives at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:85-96`.
- Existing root version-tap logic documented in grounding/behavior remains out of scope.
- Reusing drawer behavior would violate the settings no-side-effect acceptance criterion.

### Alternatives Considered

1. **Reuse `DrawerFooter` in settings.** Rejected because it is interactive and owns drawer-specific About/developer-mode behavior.
2. **Add hidden repeated-tap developer-mode activation to settings too.** Rejected because it expands scope and duplicates an existing drawer-owned hidden behavior.
3. **Add an `onVersionClick` callback with a no-op implementation.** Rejected because it creates an attractive nuisance and weakens the display-only contract.
4. **Change drawer footer to also show `versionCode`.** Rejected as scope creep; SCH-2 adds a settings footer and preserves drawer behavior.

### Consequences

- Settings footer implementation must avoid click, long-click, pointer-input, and callback wiring.
- Grep/code review should verify no settings path calls the drawer version-tap path.
- Drawer footer format and About/developer-mode behavior remain unchanged.

## ADR-SCH2-04 — No domain/data/DI/storage/events/backend work

**Status:** Accepted

### Decision

SCH-2 is app-layer parameter threading plus Compose rendering only. It does not add or change domain, data, repositories, use cases, Koin modules, Room, SharedPreferences/DataStore, Firebase, network, migrations, analytics, or event contracts.

### Rationale

- Spec marks Feature Domain Contract as N/A.
- Build version metadata is generated static app metadata, not user/session/network/storage state.
- Research found no backend, storage, domain rules, repositories, Koin bindings, server contracts, migrations, or web research need.
- Grounding confirms REST API, WebSocket, push payload, Firebase, Room, storage, and domain contract are N/A.

### Alternatives Considered

1. **Add a use case or repository for app version display.** Rejected because there is no business logic, IO, or state to abstract.
2. **Add Koin binding for app version metadata.** Rejected because static build constants can be passed at composition boundary; DI would add binding uniqueness risk and boilerplate.
3. **Persist version metadata in preferences/database.** Rejected because generated build metadata is the source of truth and persistence would become stale duplicate state.
4. **Emit analytics/events for footer impression or taps.** Rejected because no analytics requirement exists and the settings footer has no tap behavior.

### Consequences

- `07-events.md` is not required for SCH-2.
- `08-storage-model.md` is not required for SCH-2.
- `06-api-contract.md` is created by the component architect only to capture internal Compose UI signatures while stating external/domain/backend API is N/A.
- Validation focuses on compile/call-site coverage, UI behavior, and preservation of existing drawer behavior.

## ADR-SCH2-05 — Preserve existing one-way app-shell to local/settings dependency; add no new cross-feature dependency

**Status:** Accepted

### Decision

Use the existing parent-to-child render path from app-shell presentation to local settings presentation. Do not add a reverse dependency from local settings to app-shell, the app module, root implementation, navigation internals, or other feature modules.

### Rationale

- App-shell already renders local settings for `LocalConfig.SettingsRoot` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:461-466`.
- Current shell path is `AppShellScreen` calling private `AppShellContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:287-295`, `AppShellContent` declared at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:318-326`, then `AppShellContent` calling `LocalTabContent` at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:338-346`.
- Current `LocalTabContent` signature lives in app-shell at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:422-430`.
- Research records app-shell to local/settings as an existing direct composition dependency and found no reverse local/settings to app-shell dependency.
- Cross-feature invariants forbid bidirectional feature coupling.
- Passing primitive display props down the existing render path does not create a new feature dependency.

### Alternatives Considered

1. **Have local settings import app-shell/root implementation to fetch version values.** Rejected because it creates reverse coupling and violates the view-function boundary.
2. **Move the settings screen into app-shell.** Rejected because local settings already owns the screen and the requested change is local UI rendering.
3. **Introduce a shared core UI contract solely for this footer.** Rejected because core should not learn feature-specific UI needs for one local footer.
4. **Push build metadata into shell/local child-stack or route config.** Rejected because version metadata is static display UI data and should not contaminate navigation/domain route data.

### Consequences

- Implementation should only extend the existing app → app-shell → private shell content renderer → local/settings parameter flow.
- No new Gradle dependency is needed.
- No reverse imports from local settings to app-shell/app should appear in production code.

## Conditional Document Decisions

| Document | Decision |
|---|---|
| `07-events.md` | Not required. The settings footer emits no event and has no callback. Existing drawer version-tap behavior is preserved outside SCH-2. |
| `08-storage-model.md` | Not required. No storage, migration, cache, sync cursor, preferences, or persistence changes are introduced. |

## Web Prior-Art Decision

Web research is not required for SCH-2. The feature introduces no new SDK, platform API, library behavior, server behavior, or external integration. Existing local BuildConfig propagation and Compose/MaterialTheme patterns are sufficient.
