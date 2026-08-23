# Implementation Plan: Settings App Version Footer

## Overview

SCH-2 is a small UI-only vertical slice: pass generated app build metadata from `apps/android-next` into the existing settings screen and render a passive footer at the visible bottom of the settings viewport.

Feature Domain Contract: N/A. No domain, data, storage, network, Koin, Gradle dependency, navigation-contract, event, or backend work is planned.

## Strategy

Use one releasable UI/presentation phase. Splitting this into separate app-shell/settings/test phases would add coordination overhead without creating independently shippable behavior: the public/internal Compose signatures, call-site updates, footer layout, and stale-call-site tests must land atomically.

Because the implementation touches three Gradle modules (`apps/android-next`, `android/feature/app-shell/presentation`, `android/feature/local/settings/presentation`), the phase is tagged `complex` and includes options considered in `phase-01/overview.md`.

## Phase Dashboard

| Phase | Goal | Layer | Depends on | Role Inputs | Validation |
|---|---|---|---|---|---|
| phase-01 | Thread app version name/code into settings and render pinned display-only footer | ui | none | backend: none; frontend: `phase-01/frontend.md`; tests: `phase-01/tests.md` | `./gradlew ciCheck --no-configuration-cache`<br>`grep -RIn --include='*.kt' 'DesignSettingsScreen(' android apps shared`<br>`grep -RIn --include='*.kt' 'AppShellScreen(' android apps shared`<br>`grep -RIn --include='*.kt' 'AppShellContent(' android/feature/app-shell apps`<br>`grep -RIn --include='*.kt' 'LocalTabContent(' android/feature/app-shell apps`<br>`./gradlew :apps:android-next:assembleDebug --no-configuration-cache`<br>`./gradlew :apps:android-next:assembleRelease --no-configuration-cache`<br>`./gradlew test --no-configuration-cache`<br>`./gradlew assembleDebugAndroidTest --no-configuration-cache` |

## File Map

### Plan files

| File | Purpose |
|---|---|
| `docs/features/settings-app-version-footer/plan/README.md` | Plan dashboard, strategy, run ledger note, and vertical-slice overview. |
| `docs/features/settings-app-version-footer/plan/phase-01/overview.md` | Lead-facing phase contract, traceability, acceptance criteria, file scope, validation, and review tags. |
| `docs/features/settings-app-version-footer/plan/phase-01/frontend.md` | Frontend implementation Signature Cards for app composition, app-shell propagation, and settings UI rendering. |
| `docs/features/settings-app-version-footer/plan/phase-01/tests.md` | Test scenarios, fixtures, grep guards, and validation expectations. |

### Production/test implementation file map

| Path | Planned action |
|---|---|
| `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt` | Modify app composition call to pass `BuildConfig.VERSION_CODE` together with existing `BuildConfig.VERSION_NAME`. |
| `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt` | Modify public `AppShellScreen` and private `AppShellContent` / `LocalTabContent` parameter flow; pass both version values into settings only; preserve drawer footer behavior. |
| `android/feature/local/settings/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/local/settings/presentation/ui/DesignSettingsScreen.kt` | Modify settings screen signature, render pinned centered low-emphasis display-only footer, reserve scroll bottom padding, update preview sample data. |
| `android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt` | Modify existing app-shell androidTest call site and add/adjust scenarios for exact label, display-only semantics, pinned visible-bottom bounds, stale call-site compile coverage, and drawer unchanged guard. |

## Vertical Slice Breakdown

| Вертикаль | Рекомендуемый scope | Фазы/файлы | Входные документы | Зависит от |
|---|---|---|---|---|
| UI metadata propagation | Required `appVersionCode` from app BuildConfig into app-shell/private local renderer/settings screen, no library BuildConfig reads | `phase-01/overview.md`, `phase-01/frontend.md` | `2-grounding.md`, `01-architecture.md`, `02-behavior.md`, `03-decisions.md`, `06-api-contract.md` | none |
| Settings footer rendering | Pinned non-scroll footer inside `DesignSettingsScreen`, exact label, small/grey/centered, display-only | `phase-01/overview.md`, `phase-01/frontend.md` | `0-spec.md`, `01-architecture.md`, `02-behavior.md`, `06-api-contract.md` | UI metadata propagation in same atomic phase |
| Regression tests and guards | Existing app-shell androidTest and grep/build validation for label, semantics, bounds, stale call-sites, drawer unchanged | `phase-01/overview.md`, `phase-01/tests.md` | `04-testing.md`, `2-grounding.md` | UI metadata propagation and footer rendering in same atomic phase |

## Run Ledger Note

The named Kent role `--agent planner` was unavailable in this session (`requested subagent launch is not allowed`). This run acted manually according to `.claude/agents/planner.md`, `AGENTS.md`, `.claude/PROJECT-CONTEXT.md`, and the relevant project rules (`clean-architecture.md`, `di-patterns.md`, `domain-models.md`, `use-cases.md`, `testing.md`, `kotlin-conventions.md`).
