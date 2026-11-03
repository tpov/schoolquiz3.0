## Phase 1: Settings App Version Footer UI Slice

### Goal

Add a passive settings footer that displays app build metadata as `v<versionName> (<versionCode>)`, pinned to the visible bottom of the settings viewport, while preserving existing drawer footer/About/developer-mode behavior.

### Scope

- Thread `BuildConfig.VERSION_CODE` from `apps/android-next` through existing app-shell Compose parameters into `DesignSettingsScreen`.
- Keep app `BuildConfig` reads in the app module only.
- Render a centered, small, low-emphasis, display-only settings footer.
- Reserve bottom scroll padding so the pinned footer does not cover the last design setting.
- Update preview and existing app-shell androidTest call sites.
- Add/adjust tests for label, display-only semantics, pinned bounds, stale call-sites, and drawer unchanged behavior.
- Do not add domain/data/storage/network/Koin/Gradle dependency changes.

### Layer (domain | useCase | adapter | controller | ui)

ui

### Dependencies: phases_ref: [phase-01] or none

none

### Role Inputs: backend/frontend/tests with filename or none

- backend: none
- frontend: `frontend.md`
- tests: `tests.md`

### Review Tags: security-review always; concurrency-review only if touches coroutines/Flow/shared mutable state/lifecycle callbacks; if phase complex — explicitly tag complex.

- security-review
- complex

No concurrency-review: the phase threads immutable Compose parameters and renders passive text. It must not alter existing `LaunchedEffect`, Flow collection, channels, shared mutable state, lifecycle callbacks, or coroutine behavior.

### Diagnostics Hints: expected failure signals; suggested debugger triggers; device/backend prerequisites.

- Expected failure signals: compile errors from stale required Compose parameters; androidTest compile failures in `AppShellScreenTest`; UI test bounds assertion failures if footer is a scroll item or overlaps settings content; grep guard failures for library `BuildConfig`, settings footer click handlers, `onVersionTap`, Koin access, or debug gating.
- Suggested debugger triggers: attach `diagnostics`/`code-analyst` if Gradle compile reports unresolved parameters in `AppShellScreen`, `AppShellContent`, `LocalTabContent`, or `DesignSettingsScreen`; inspect Compose hierarchy if footer node exists but bounds are not near the container bottom.
- Device/backend prerequisites: no backend, Firebase, network, storage, or logged-in user required. `assembleDebugAndroidTest` does not require a connected device; manual UX smoke or connected tests require an Android device/emulator.

### Pattern Invariants: existing patterns with file:line references; no code blocks.

- App generated build metadata is read only in app composition: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:41-44`; app-shell documents that library modules cannot access app `BuildConfig` directly at `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:107-110`.
- Compose screens remain view functions receiving props/callbacks and must not resolve Koin/repositories/platform APIs directly: `AGENTS.md:12-17`, `docs/invariants.md:17-23`.
- Existing app-shell → local/settings render dependency is one-way and must not gain a reverse import: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:63`, `docs/invariants.md:25-31`.
- Settings currently owns the full-size background/scroll content and should own the pinned footer sibling: `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78`.
- `SchoolQuizDesignBackground` content is `BoxScope`, enabling a non-scroll aligned footer sibling: `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/SchoolQuizDesign.kt:169-200`.
- Low-emphasis settings text uses MaterialTheme `onSurface.copy(alpha = ...)`: `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:80-92`, `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:139-145`.
- Drawer version behavior is interactive and must remain separate: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt:73-96`, `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:318-337`.

### State Matrix Coverage: N/A if State Matrix N/A.

N/A — `0-spec.md` and `02-behavior.md` state that the feature has no state matrix.

### Domain Contract Coverage: N/A if Feature Domain Contract N/A.

N/A — Feature Domain Contract is N/A. No domain skeleton, use case, repository, Koin binding, storage, or data adapter is required.

### Traceability: table Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation.

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|---|---|---|---|---|---|
| Problem 1: Pinned display-only app version footer in settings | App metadata source: `apps/android-next/build.gradle.kts:14-17`, `:33-35`; app composition boundary: `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt:40-50`; app-shell UI boundary: `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:121-127`, `:461-466`; settings UI: `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt:48-78`; preview/test: `DesignSettingsScreen.kt:298-305`, `AppShellScreenTest.kt:254-260`; drawer guard: `DrawerContent.kt:25-35`, `DrawerFooter.kt:40-96`, `DefaultRootComponent.kt:318-337` | App root composition `MainActivity.onCreate`; settings drawer click; `NavigatorImpl.goTo`; domain route resolution to `LocalConfig.SettingsRoot`; Decompose local child stack; `AppShellScreen.LocalTabContent`; settings preview; deep-link path is guarded N/A because current deep-link implementation does not route to settings | REST/WebSocket/push/Firebase/Room/storage/domain contracts N/A; library modules must not read app `BuildConfig`; settings footer must not reuse drawer `onVersionTap`; no new navigation, state, DI, storage, or events | Add required `appVersionCode: Int` to app-shell/settings Compose signatures per `06-api-contract.md:37-68`, `:70-100`, `:102-128`, `:130-155`; pass `BuildConfig.VERSION_CODE` from MainActivity per `06-api-contract.md:24-33`; render footer as pinned non-scroll `DesignSettingsScreen` child with exact label and no input modifiers | Grep call sites from grounding; `./gradlew :apps:android-next:assembleDebug --no-configuration-cache`; `./gradlew :apps:android-next:assembleRelease --no-configuration-cache`; `./gradlew test --no-configuration-cache`; `./gradlew assembleDebugAndroidTest --no-configuration-cache`; `./gradlew ciCheck --no-configuration-cache`; manual settings/drawer smoke |

### New Files

none

### Modified Files

- `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt`
- `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt`
- `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt`
- `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt`

### Deleted Files

none

### Cross-Phase Dependencies

**Consumed from previous phases** (types/Flows/Channels/DI bindings):
- none

**Provided for next phases**:
- none

**Temporary stubs** (TODO markers created for compile safety):
- none

**Required cleanup in this phase** (from earlier temp stubs):
- none

### DI Bindings; if none — explicitly none / Koin verify not applicable.

none / Koin verify not applicable

### Options Considered

| Критерий | Option A (recommended): one atomic UI vertical slice | Option B: split app-shell propagation and settings footer into separate phases |
|---|---|---|
| Complexity | medium: 3 modules but one data path | higher: intermediate incomplete API state |
| Test cost | low: one existing androidTest surface plus compile/grep gates | medium: duplicate compile gates and temporary expectations |
| Refactor cost если неверно | small: revert one narrow parameter-threading/UI slice | medium: rollback across partial phases |
| Coupling с external SDK | low: existing Compose/BuildConfig only | low: same SDKs |
| Risk of stale call sites | low: required parameters changed atomically | higher: phase boundary may require defaults or temporary stubs |
| Product shippability | complete after phase-01 | incomplete until second phase |

**Recommended: Option A**

**Rationale:** The source-breaking signature changes, call-site updates, pinned UI, and tests are one indivisible user-visible slice. Required parameters intentionally make stale call sites fail fast, so temporary defaults or stubs would weaken the design.

**Rejected Option B:** Splitting propagation from rendering would require either an intermediate unused parameter flow or a temporary default/stub. That adds review overhead and creates no independently releasable behavior for this small UI-only feature.

### Acceptance Criteria: phase AC checklist, covering 0-spec.md AC.

- [ ] Settings screen shows existing design settings plus centered display-only footer at the visible bottom of the settings viewport, not as a final short-list row.
- [ ] Given `appVersionName = "0.1.0"` and `appVersionCode = 1`, the footer displays exactly `v0.1.0 (1)`.
- [ ] Footer uses small MaterialTheme typography and low-emphasis grey/on-surface color, centered horizontally.
- [ ] Footer is available in debug and release and is not gated by `isDebugBuild`, `BuildConfig.DEBUG`, or `canSeeDesignCatalog`.
- [ ] Footer remains available offline/fresh install/logout/process death because it depends only on generated app build metadata.
- [ ] Tapping/long-pressing the settings footer causes no navigation, dialog, developer-mode action, storage write, analytics/event, or state mutation.
- [ ] Drawer footer label, drawer About dialog, and repeated-tap developer-mode behavior are unchanged and remain drawer-owned.
- [ ] No stale `DesignSettingsScreen`, `AppShellScreen`, `AppShellContent`, or `LocalTabContent` call sites remain.
- [ ] No domain/data/storage/network/Koin/Gradle dependency changes are introduced.

### Tests Required: concrete scenarios in given/when/then, NOT JUnit/Kotlin code.

- `settings_footer_exact_label`: given settings UI receives `appVersionName = "0.1.0"` and `appVersionCode = 1`, when the settings route/screen is rendered, then the displayed footer text is exactly `v0.1.0 (1)`.
- `settings_footer_display_only_semantics`: given the settings footer is rendered, when test semantics for `v0.1.0 (1)` are inspected, then it has no click action and no long-click/pointer input behavior; existing clickable matches in settings remain only style option rows.
- `settings_footer_pinned_visible_bottom_bounds`: given the settings route/screen is rendered in a fixed viewport, when root/container and footer bounds are measured, then the footer bottom is within a small tolerance of the visible settings container bottom and the footer top is in the lower viewport region, proving it is not merely a final `LazyColumn` item.
- `app_shell_required_version_code_stale_call_site_compile`: given `appVersionCode` is required in canonical signatures, when `grep` and Gradle compile run, then all `AppShellScreen`, `AppShellContent`, `LocalTabContent`, and `DesignSettingsScreen` call sites thread both version values with no default/stale call sites.
- `drawer_version_behavior_unchanged_guard`: given the drawer is opened after SCH-2, when drawer footer/About/dev-mode code paths are inspected or smoke-tested, then drawer footer remains `v<versionName>` using existing `onVersionTap` and About behavior; settings footer does not call `onVersionTap`.
- `debug_release_visibility_guard`: given debug and release variants compile, when the settings route is rendered, then the settings footer is not conditioned on `isDebugBuild`, `BuildConfig.DEBUG`, `canSeeDesignCatalog`, user stats, auth state, network state, or storage state.

### Validation

| Command / Check | Expected result |
|---|---|
| `./gradlew ciCheck --no-configuration-cache` | Canonical local gate passes. |
| `grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared` | Declaration, production call, and preview call all include required version props; no stale call sites. |
| `grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared` | Declaration, `MainActivity`, and `AppShellScreenTest` include required `appVersionCode`; no stale call sites. |
| `grep -RIn --include='*.kt' 'AppShellContent(' android/feature/app-shell apps` | Private call/signature thread `appVersionName` and `appVersionCode`. |
| `grep -RIn --include='*.kt' 'LocalTabContent(' android/feature/app-shell apps` | Private call/signature thread `appVersionName` and `appVersionCode`. |
| `grep -RIn --include='*.kt' 'BuildConfig' android/feature/app-shell android/feature/local/settings` | No app `BuildConfig` read is introduced in library modules; documentation comments may remain. |
| `grep -RIn --include='*.kt' -E 'isDebugBuild|BuildConfig.DEBUG|canSeeDesignCatalog' android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt` | Settings footer is not gated by debug/design-catalog branches. |
| `grep -RIn --include='*.kt' 'onVersionTap' android/feature/local/settings android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt` | No settings footer `onVersionTap`; existing app-shell drawer wiring remains drawer-owned. |
| `grep -RIn --include='*.kt' -E 'clickable|combinedClickable|pointerInput|onLongClick|SemanticsActions\\.OnClick' android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui` | Interaction primitives in settings belong to design-style option rows/tests only, not to the footer. |
| `grep -RIn --include='*.kt' -E 'getKoin\\(|koinInject\\(|inject<' android/feature/local/settings android/feature/app-shell` | No new Koin resolution in Compose screens/settings footer path. |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | Debug app compile validates generated `BuildConfig.VERSION_CODE` wiring. |
| `./gradlew :apps:android-next:assembleRelease --no-configuration-cache` | Release app compile validates footer is not debug-only and generated metadata wiring compiles for release. |
| `./gradlew test --no-configuration-cache` | Android/app JVM tests pass. |
| `./gradlew assembleDebugAndroidTest --no-configuration-cache` | Instrumented test APK compiles, including updated `AppShellScreenTest` call site and footer assertions. |

### Handoff Notes

- Do not create `backend.md`: Role Inputs backend is `none` because the phase has no backend/data/domain/DI/scaffold work.
- Do not add Gradle dependencies. Prefer existing `android/feature/app-shell/presentation/src/androidTest/.../AppShellScreenTest.kt` for Compose UI coverage because app-shell already has androidTest Compose UI dependencies.
- Do not add defaults for `appVersionName` or `appVersionCode`; required parameters are the stale-call-site guard.
- Do not reuse `DrawerFooter` or wire settings footer to `onVersionTap`.
