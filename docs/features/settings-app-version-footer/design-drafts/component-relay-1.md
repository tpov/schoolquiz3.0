---
date: 2026-07-26
feature: settings-app-version-footer
role: architect-component
stage: design-relay-round-1
opponent: architect-high-level
opponent_draft: docs/features/settings-app-version-footer/design-drafts/high-level.md
---

# Component Relay Round 1 — Response to High-Level Draft

## 0. Verdict

**Converged with one documentation nuance.**

I accept the high-level architecture and module-boundary positions. There is no component-level blocker in the high-level draft.

The only nuance is `06-api-contract.md`: I accept that there is **no external/API/domain/server contract**, but I still need the final design pack to preserve the internal public Compose UI signature changes somewhere explicit, because implementation will source-break public composable functions across module boundaries.

## 1. Material High-Level Positions — Accept / Reject

| # | High-level position | Component response | Notes |
|---|---|---|---|
| 1 | C4 flow is `BuildConfig -> MainActivity -> AppShellScreen -> LocalTabContent -> DesignSettingsScreen`. | **Accept.** | This exactly matches component draft. It keeps generated app metadata at the app composition boundary and threads primitive display props down. |
| 2 | App metadata ownership stays in `apps/android-next`; no library module reads app `BuildConfig`. | **Accept.** | Required by current app-shell boundary and avoids invalid dependency from Android library modules to app generated classes. |
| 3 | Settings footer belongs to `android/feature/local/settings/presentation`. | **Accept.** | Footer is part of settings viewport. Component design should render it in `DesignSettingsScreen`, not in app-shell overlay. |
| 4 | Preserve existing `app-shell -> local/settings` dependency; add no reverse dependency. | **Accept.** | Component design must not add settings imports of app-shell/root component/navigation types. Parent passes primitive props down. |
| 5 | `07-events.md` is N/A. | **Accept.** | Footer is display-only. No analytics/event bus/domain event/tap event should be introduced. |
| 6 | `08-storage-model.md` is N/A. | **Accept.** | No Room, preferences, cache, migration, sync cursor, or persistence changes. |
| 7 | Web prior-art / web-researcher not needed. | **Accept.** | No new SDK/platform/library behavior. Existing local BuildConfig and Compose/MaterialTheme patterns are sufficient. |
| 8 | Do not implement as final `LazyColumn` item. | **Accept.** | Pinned/display-only bottom requirement needs a non-scroll sibling/overlay in the settings container with bottom padding on scroll content. |
| 9 | Do not reuse `DrawerFooter`. | **Accept.** | Drawer footer is interactive and owns About/dev-mode behavior; settings footer must be passive. |
| 10 | Do not add Component/ViewModel/use-case/Koin/repository. | **Accept.** | There is no lifecycle-owned state or business behavior. Passing props is the right component boundary. |
| 11 | Do not add tap/long-press behavior. | **Accept.** | Plain `Text`, no `clickable`, `combinedClickable`, `pointerInput`, callback, or semantics click action. |
| 12 | Do not mutate drawer footer. | **Accept.** | Drawer remains separate: `v<versionName>`, clickable, About/dev-mode unchanged. |
| 13 | High-level stance: `06-api-contract.md` N/A because no external/domain/server API. | **Accept with qualification.** | See §2. I agree if the final pack records internal UI signatures in another final doc. If the final pack uses `06-api-contract.md`, it should be a short "external API N/A + internal UI signatures" doc. |

## 2. `06-api-contract.md` Nuance

### 2.1 What I accept

I accept the high-level premise:

- No REST / WebSocket / push / Firebase / Room API.
- No repository/use-case/domain contract.
- Feature Domain Contract remains N/A.
- No event contract and no storage contract.

So if the pipeline interprets `06-api-contract.md` strictly as **external/backend/domain API**, the document may be omitted or marked N/A.

### 2.2 What must still be captured in final docs

Implementation changes two public composable function signatures used across module boundaries:

```kotlin
@Composable
fun AppShellScreen(
    rootComponent: RootComponent,
    appVersionName: String,
    appVersionCode: Int,
    isDebugBuild: Boolean,
    selectedDesignStyle: SchoolQuizDesignStyle,
    onDesignStyleChange: (SchoolQuizDesignStyle) -> Unit,
    modifier: Modifier = Modifier,
)
```

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

These are not backend/domain contracts, but they are **public UI API surface inside the app repo**. They need to be explicit so implementers update all call sites atomically and do not add defaults that hide stale wiring.

### 2.3 Recommended resolution

Either option is acceptable:

1. **Preferred if final-doc slots are flexible:** mark `06-api-contract.md` as N/A/omitted, and put the exact UI signatures in `01-architecture.md` under "Public UI API / Component Signatures".
2. **Preferred if final-doc slots are fixed:** create `06-api-contract.md` with:
   - "External API / domain contract: N/A"
   - "Internal public UI signatures" containing the two signatures above.

Component-level requirement: wherever the signatures land, final docs must say:

- `appVersionCode: Int` is required, not defaulted.
- `appVersionCode` is placed next to `appVersionName`.
- `MainActivity` passes `BuildConfig.VERSION_CODE`.
- `AppShellScreen` still passes only `appVersionName` to `DrawerContent`; drawer behavior stays unchanged.

## 3. Final-Doc Assignment / Sections Needed

I accept convergence. Suggested assignment for final design pack:

### `01-architecture.md`

Needs:

- C4 L1/L2 from high-level draft.
- C4 L3/component graph from component draft:
  - `BuildConfig`
  - `MainActivity`
  - `AppShellScreen`
  - `LocalTabContent`
  - `DesignSettingsScreen`
  - `SettingsAppVersionFooter`
  - Existing `DrawerContent` / `DrawerFooter` as preserved separate path.
- Public UI API / component signatures:
  - `AppShellScreen(... appVersionName: String, appVersionCode: Int, ...)`
  - `DesignSettingsScreen(... appVersionName: String, appVersionCode: Int, ...)`
  - `LocalTabContent(... appVersionName: String, appVersionCode: Int, ...)` as private implementation boundary.
- Explicit no-new-boundaries section:
  - no domain/data/Koin/storage/network/navigation module changes.
  - no reverse dependency from local/settings to app-shell.

### `02-behavior.md`

Needs:

- Sequence: generated `BuildConfig` values render settings footer.
- Sequence: tap/long-press on settings footer has no callback or side effect.
- Drawer behavior preserved as separate path.
- Debug/release/offline/logout/process-death rationale: static generated metadata, no runtime state.

### `03-decisions.md`

Needs ADRs with `Alternatives Considered`:

- App module remains source of version metadata; pass primitive props.
- Footer rendered inside `DesignSettingsScreen`, not shell overlay or final `LazyColumn` item.
- Settings footer display-only; do not reuse `DrawerFooter`.
- No DI / Room / storage / domain / events / backend.
- Preserve existing `app-shell -> local/settings` dependency without reverse coupling.

### `04-testing.md`

Needs acceptance-criteria coverage mapping:

- Exact label `v0.1.0 (1)`.
- Pinned visible-bottom placement.
- Small low-emphasis centered styling.
- Offline/fresh install/logout/process death.
- Display-only semantics/no click action.
- Drawer footer/About/dev-mode unchanged.
- Compile/stale-call-site checks.

Suggested validations:

```bash
grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared
grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared
./gradlew :apps:android-next:assembleDebug --no-configuration-cache
./gradlew test --no-configuration-cache
./gradlew assembleDebugAndroidTest --no-configuration-cache
./gradlew ciCheck --no-configuration-cache
```

If direct settings Compose UI tests require new Gradle test dependencies, implementation ownership for build/scaffold files must be respected.

### `06-api-contract.md`

Two acceptable final forms:

- **Omit / N/A**, if `01-architecture.md` includes the public UI signatures exactly.
- **Short hybrid doc**, if lead wants numbered docs:
  - External/backend/domain API: N/A.
  - Internal public UI API signatures: include `AppShellScreen` and `DesignSettingsScreen` signatures.

### `07-events.md`

N/A. Footer emits no events and has no tap action.

### `08-storage-model.md`

N/A. No storage, migration, cache, or persistence.

## 4. Component-Level Implementation Guardrails for Final Docs

- Do not add default values for `appVersionCode`; stale call sites should fail compilation.
- Do not pass a preformatted label from `MainActivity`; `DesignSettingsScreen` should format `v$versionName ($versionCode)` so UI tests can assert canonical rendering from fields.
- Do not add `AppVersionInfo` model/provider unless a separate future feature creates broader app-info needs.
- Use `MaterialTheme.typography.labelSmall` and low-emphasis theme color, preferably `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`.
- Use pinned sibling layout inside `SchoolQuizDesignBackground` and reserve bottom padding in `LazyColumn`.
- Do not change `DrawerContent`, `DrawerFooter`, or `DefaultRootComponent.onVersionTap` for this feature.

## 5. Open Questions

None blocking.
