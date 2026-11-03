---
date: 2026-07-26
agent: architect-high-level
feature: settings-app-version-footer
ticket: SCH-2
relay_round: 1
opponent_draft: docs/features/settings-app-version-footer/design-drafts/component.md
---

# High-Level Relay 1 — Response to Component Draft

## 1. Component Draft Review

Read: `docs/features/settings-app-version-footer/design-drafts/component.md`.

Result: **converged**. No blocker objections to the component-level architecture. The component draft is aligned with high-level boundaries and gives the right implementation-level constraints for SCH-2.

## 2. Material Position Decisions

| Component position | High-level decision | Rationale / conditions |
|---|---|---|
| Read `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE` only in `apps/android-next/MainActivity`. | **Accept** | This preserves the app-module ownership of generated app `BuildConfig`. Library modules must receive metadata as parameters, not read app generated classes directly. |
| Thread required props through `AppShellScreen(appVersionName, appVersionCode, ...)`. | **Accept** | `AppShellScreen` is the existing app-shell Compose boundary. Adding required `appVersionCode: Int` next to `appVersionName: String` is the cleanest stale-call-site detector. |
| Thread props through private `LocalTabContent(..., appVersionName, appVersionCode, ...)`. | **Accept** | Keeps version data in the existing app → app-shell → local/settings flow. Since `LocalTabContent` is private shell rendering, this is not a domain/navigation contract change. |
| Pass props into `DesignSettingsScreen(..., appVersionName, appVersionCode, ...)`. | **Accept** | Settings owns the viewport and footer rendering. Passing primitives avoids app-shell/root-component coupling inside local settings. |
| Render footer in `DesignSettingsScreen` as pinned `BoxScope` sibling with reserved `LazyColumn` bottom padding. | **Accept** | Best satisfies "pinned/display-only bottom" without shell overlay or scroll-item ambiguity. Bottom padding is required to prevent the footer overlay from covering final scroll content. |
| Footer is plain display-only `Text`: no clickable, no `onVersionTap`, no About/dev-mode, no storage/events. | **Accept** | This is directly required by spec and protects drawer-only behavior. No callback should be introduced "just in case." |
| DI / Room / storage / domain / navigation are N/A. | **Accept** | No business logic, persistence, repository, use case, Koin, routing, or backend contract is needed. |
| Drawer footer remains unchanged. | **Accept** | Drawer footer is a separate existing interactive surface (`v<versionName>`, About dialog, version tap/dev-mode). SCH-2 adds settings display only; it does not redesign drawer behavior. |
| Use `MaterialTheme.typography.labelSmall` and low-emphasis `onSurface` color for settings footer. | **Accept with style flexibility** | Exact alpha/token can be component-level, but it must remain small, centered, theme-based, and low-emphasis. Avoid raw hardcoded grey unless existing design-system precedent requires it. |
| Prefer direct settings Compose UI tests if test deps exist; otherwise rely on compile, app-shell test update, manual/code review. | **Accept** | Test strategy should not force production over-architecture. If adding test dependencies requires scaffold/build-file edits, route through the correct implementation owner. |

## 3. `06-api-contract.md` Decision

High-level updates its previous position:

- External/backend/domain API contract: **N/A**.
- Final `06-api-contract.md`: **should be created** as a mandatory design-pack document and should record the canonical **internal Compose UI signatures** while explicitly saying there is no external/domain/server API.

Reason:

1. The design workflow treats `06-api-contract.md` as a signature SSoT.
2. `AppShellScreen` and `DesignSettingsScreen` are public Compose functions across module boundaries, even though they are not product/server/domain APIs.
3. Recording the signatures there reduces ambiguity for implementation and tests without pretending this feature has a backend/domain contract.

Recommended wording for `06-api-contract.md`:

> Backend/API/domain contract: N/A. This document only captures internal Compose UI contracts for implementation coordination.

Canonical signatures to record:

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

Notes:

- Use actual current names where they already exist: `DefaultRootComponent` and `onDesignStyleSelected`.
- `appVersionCode` should not have a default value.
- `appVersionCode` should sit immediately after `appVersionName`.
- `LocalTabContent` is private and can be documented in `01-architecture.md` / `02-behavior.md`; it does not need to be elevated as a public API, but final docs should still state that it threads both values to the `SettingsRoot` branch.

## 4. Final-Doc Assignment Proposal

Assuming lead accepts convergence, produce final docs as follows:

| Final doc / section | Primary owner | Content |
|---|---|---|
| `01-architecture.md` | `architect-high-level` lead, with component inserts | C4 L1-L2, real module dependency graph, module boundaries, existing app-shell → local/settings dependency note, plus a short C4 L3/component graph summarized from component draft. |
| `02-behavior.md` | `architect-high-level` lead, with component sequence insert | DFD: `BuildConfig -> MainActivity -> AppShellScreen -> LocalTabContent -> DesignSettingsScreen -> pinned Text`; display-only tap/long-press no-op behavior; drawer path explicitly unchanged. |
| `03-decisions.md` | shared: `architect-high-level` owns module ADRs; `architect-component` owns component ADRs | Include high-level ADRs for app metadata boundary, no new domain/storage/navigation/events, preserving one-way app-shell → settings dependency; include component ADRs for required primitive props, pinned sibling footer, display-only separation from drawer. Every ADR must include `Alternatives Considered`. |
| `04-testing.md` | `architect-component` primary, high-level review | Compile/stale-call-site checks, optional settings Compose UI tests, existing `AppShellScreenTest` update, grep checks for no `BuildConfig` in library modules/no `onVersionTap` from settings, manual UX smoke. |
| `06-api-contract.md` | `architect-component` primary, high-level approval | Mandatory short doc: external/domain/backend API = N/A; internal Compose UI signatures are SSoT. |
| `07-events.md` | `architect-high-level` or lead can create N/A stub if mandatory | N/A: no events/analytics/callbacks. Explicitly say drawer version tap remains existing behavior and settings footer emits nothing. |
| `08-storage-model.md` | `architect-high-level` or lead can create N/A stub if mandatory | N/A: no Room/SharedPreferences/DataStore/migration/persistence changes. Existing design-style preference unchanged. |

If the design pack allows omitting N/A conditional docs, then `07-events.md` and `08-storage-model.md` can be omitted with the N/A decision recorded in `03-decisions.md`. If the workflow requires numbered docs, create short N/A stubs.

## 5. Remaining Objections / Guardrails

No objections to the component draft. Guardrails to keep in final docs:

1. Do not move version metadata into domain/core provider, Koin binding, repository, or use case.
2. Do not read app `BuildConfig` from app-shell/settings library modules.
3. Do not add reverse dependency from local settings to app-shell/root component.
4. Do not render the settings footer as a final `LazyColumn` row while pinned semantics remain required.
5. Do not reuse drawer footer or attach `onVersionTap`/About/dev-mode behavior to settings.
6. Do not change drawer footer format or behavior in SCH-2.
7. Do not default `appVersionCode`; compile failures should expose stale call sites.

## 6. Open Questions

None. No spec/research ambiguity found.
