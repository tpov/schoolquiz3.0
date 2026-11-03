## Verdict: REJECT

## Findings by severity

### BLOCKER (план не может быть принят)
- [Architect/Realist] Phase-04 central contract drift: `docs/features/app-shell-menu/plan/phase-04/frontend.md:198` says `val result: TransitionResult = initUseCase()`, but approved Walking Skeleton contract `shared/feature/app-shell/domain/src/commonMain/kotlin/.../InitializeAppShellUseCase.kt:17-23` returns `AppShellState`, and even architecture shows the needed wrap at `docs/features/app-shell-menu/01-architecture.md:394-395` (`val initialState = initUseCase(); applyResult(..., TransitionResult(initialState))`). Impact: phase-04 cannot compile as written, and `docs/features/app-shell-menu/plan/phase-04/tests.md:33-40` doubles down with impossible `FakeInitializeAppShellUseCase : InitializeAppShellUseCase(...)`. Fix: rewrite phase-04 frontend/tests to treat `InitializeAppShellUseCase` as returning `AppShellState`, then wrap explicitly into `TransitionResult(initialState)`.
- [Skeptic/Realist] Phase-05 is blocked by mappings that the plan only defers to phase-06, and part of them are not assigned anywhere: `docs/features/app-shell-menu/plan/phase-05/frontend.md:253` uses `state.activeSection?.displayName`, `:348` uses `screen.config.displayName`, while only `Tab.displayName`/`Tab.icon` are actually defined at `:360-372`. README still defers `DrawerSection.displayName`/`icon` to phase-06 at `docs/features/app-shell-menu/plan/README.md:173`, phase-06 only vaguely notes them at `docs/features/app-shell-menu/plan/phase-06/frontend.md:155`, and no phase defines `TabConfig.displayName` or `DrawerFooterAction.displayName` despite `phase-06/frontend.md:192` using it. Impact: `phase-05` validation `compileDebugKotlin` is not achievable as sequenced. Fix: move all title/icon/display mappings needed by `AppShellScreen` into phase-05 or earlier, explicitly covering `DrawerSection`, `TabConfig`, and `DrawerFooterAction`.
- [Realist] AC 30 validation is not wired in the real repo: plan requires `./gradlew detekt ktlintCheck` in `docs/features/app-shell-menu/plan/phase-07/overview.md:107` and `phase-07/tests.md:84`, but root build logic `build.gradle.kts:1-6` only registers `clean`, and project-wide search shows `detekt`/`ktlint` applied only in `legacy/*`, not current modules. Impact: final phase validation cannot run as written, so the plan cannot be executed end-to-end. Fix: add an explicit phase task to configure/apply `detekt` and `ktlint` for current modules, or change AC/validation strategy before approval.

### HIGH (нужно исправить перед approval)
- [Architect] DS contract drift across phase-02/03. `docs/features/app-shell-menu/plan/phase-02/frontend.md:55` sets `DarkSurface = 0xFF121212`, but spec AC 14 requires `surface #242429` at `docs/features/app-shell-menu/0-spec.md:769`; shape plan at `phase-02/frontend.md:85-91` and `phase-02/overview.md:74-75` yields `small=4, medium=8, large=12`, while API contract requires `extraSmall=4, small=8, medium=12, large=16, extraLarge=24` at `docs/features/app-shell-menu/06-api-contract.md:392-394`. Phase-03 then diverges from published DS surface: `BrandProgressBar` lacks `color` param (`phase-03/frontend.md:156-160` vs `06-api-contract.md:409`), `BrandCircleIconButton` lacks the promised 1dp stroke and simplified signature (`phase-03/frontend.md:213-219` vs `06-api-contract.md:410`), `CategoryIcon` lacks `tint` param (`phase-03/frontend.md:264-268` vs `06-api-contract.md:411`). Impact: approved DS/API contract will not match implementation plan. Fix: sync phase-02/03 files to the published API contract before implementation.
- [Architect/Skeptic] `About` footer action violates the spec contract. `docs/features/app-shell-menu/plan/phase-06/frontend.md:195-203` routes `DrawerFooterAction.About` to `Destination.SelectSection(LocalSection.Settings)`, but spec explicitly says `About` tap is MVP out of scope and must not change domain state at `docs/features/app-shell-menu/0-spec.md:426-430`. Impact: plan introduces unauthorized navigation behavior. Fix: handle `About` locally in UI (snackbar/dialog/version sheet) or escalate a new `Destination.OpenAbout` ADR instead of hijacking `Settings`.
- [Skeptic] Phase-05 overview claims concrete UI/FSM coverage that tests file explicitly punts. `docs/features/app-shell-menu/plan/phase-05/overview.md:38-41,89-126` treats `AppShellScreenTest` as real hamburger/drawer coverage, but `docs/features/app-shell-menu/plan/phase-05/tests.md:82-92` says the core test is a placeholder and defers “full assertion” to later. Impact: journeys 7-9 and Drawer visibility FSM are effectively uncovered at plan level, despite the overview presenting them as covered. Fix: either add a real fake `RootComponent`-backed instrumented test set in phase-05, or downgrade overview claims and move explicit coverage to phase-07 with concrete tests.
- [Realist] Phase-01 overview contains an impossible acceptance criterion for its own dependency graph. `docs/features/app-shell-menu/plan/phase-01/overview.md:123` requires `firebaseModule + appShellDataModule + appShellPresentationModule`, but `docs/features/app-shell-menu/plan/phase-01/tests.md:307-308` correctly says presentation wiring only arrives in phase-04. Impact: phase-01 approval criteria cannot be satisfied as written. Fix: keep phase-01 Koin scope to `UserStatsDataSource` + `UserStatsRepository`, and move full-stack Koin resolution to phase-07.
- [Architect] AC 20 has no owning phase. Spec requires nullable badge surface at `docs/features/app-shell-menu/0-spec.md:781` and `BadgeContent` is part of the approved contract at `docs/features/app-shell-menu/0-spec.md:425`, but a plan-wide search finds no `badge: BadgeContent?`, no wrapper API, and no test owner. Impact: one acceptance criterion is simply unplanned. Fix: assign badge surface to phase-05/06 explicitly, with `NavigationBarItem`/`NavigationDrawerItem` adapters or wrappers and at least compile-level tests.
- [Skeptic] Placeholder screen contract is underimplemented in the plan. `docs/features/app-shell-menu/plan/phase-05/frontend.md:111-145` renders icon + title only, while spec AC 13 requires title plus subtitle “Скоро здесь будет...” at `docs/features/app-shell-menu/0-spec.md:767`. Impact: AC 13 cannot pass even if phase-05 compiles. Fix: add subtitle to `UnderConstructionScreen` contract and include it in tests/acceptance text.
- [Realist] `BuildConfig.VERSION_NAME` source is wrong in phase-06. `docs/features/app-shell-menu/plan/phase-06/frontend.md:281-287` pulls `BuildConfig.VERSION_NAME` inside `android/feature/app-shell/presentation`, but only app module `apps/android-next/build.gradle.kts:8-12` defines `versionName`. Impact: drawer version label is under-specified and likely non-compilable/non-portable in the library module. Fix: pass version name from app layer into `AppShellScreen`, or define an explicit provider/API for version text.

### MEDIUM (улучшения, не блокирующие)
- [Dependency graph] README and per-phase dependency docs are not fully synchronized. README says phase-07 depends on all previous phases at `docs/features/app-shell-menu/plan/README.md:38`, but `docs/features/app-shell-menu/plan/phase-07/overview.md:63-68` omits phase-06 entirely; README graph also does not show the direct `phase-03 -> phase-06` edge even though `phase-06/overview.md:72-74` still needs phase-03 assets. Impact: reviewers and implementers can mis-sequence final integration. Fix: sync README graph/table and phase-07 dependencies.
- [Coverage] Phase Matrix sections do not explicitly cover all approved tables. Across phase overviews, Back / Re-tap / Drawer visibility / SectionVisibility appear, but I found no explicit Matrix Coverage entry for Tab switch FSM or Drawer section switch FSM. Impact: auditability is weaker than the spec/testing docs intend. Fix: add those two tables to phase-04 and/or phase-06 State Matrix sections.
- [Open question hygiene] OQ-PLAN-5 is stale. README still asks to “verify” `visibleFooterActions(isDebugBuild)` at `docs/features/app-shell-menu/plan/README.md:174`, but it already exists in current Walking Skeleton at `shared/feature/app-shell/domain/src/commonMain/kotlin/.../Visibility.kt:142-144`. Impact: unnecessary escalation path in phase-06. Fix: close OQ-PLAN-5 and replace it with a note that the function is already present.

### LOW (nit)
- [Realist] README file map has at least one wrong modified path: `docs/features/app-shell-menu/plan/README.md:137` points to `apps/android-next/src/main/.../AppShellScreen.kt`, while planned screen ownership everywhere else is `android/feature/app-shell/presentation/.../AppShellScreen.kt`. Impact: dashboard noise. Fix: correct the file map entry.
- [Validation realism] `docs/features/app-shell-menu/plan/phase-07/overview.md:109-110` uses `adb install app-debug.apk` without a path and launches `.AppActivity`, but current manifest declares `.MainActivity` at `apps/android-next/src/main/AndroidManifest.xml:6-8`. Impact: manual smoke snippet is not runnable as written. Fix: use the actual APK output path and `.MainActivity`.

## Coverage audit
- 30 AC coverage: 29/30 mapped. Unmapped: `AC 20` (`badge: BadgeContent? = null` has no owning phase).
- 7 AC sub-items (23a-g): 6/7 mapped. Under-specified: `23g` at integration boundary; no phase explicitly plans a hidden-section no-op assertion despite `04-testing.md:250`.
- 5 FSM tables: spec actually enumerates 6 tables; 4/6 are explicitly covered in phase Matrix Coverage. Missing explicit Matrix Coverage: Tab switch FSM, Drawer section switch FSM.
- 45 domain scenarios: covered by Walking Skeleton; no duplicate expected.
- D1-D3: mapped to phase-01? `Y` (though phase-01 overview should stop pulling presentation wiring into D2).
- 17 journeys: 14/17 explicitly covered. Uncovered at phase-plan level: Journey 7 (edge swipe open), Journey 8 (scrim close), Journey 9 (swipe close).
- 7 grounding cards linked in at least one Traceability table: Problem 1 `Y`, Problem 2 `Y`, Problem 3 `Y`, Problem 4 `Y`, Problem 5 `Y`, Problem 6 `Y`, Problem 7 `Y`.

## Dependency graph consistency
phase-01 <- phase-02 <- phase-03  
phase-01, phase-02 <- phase-04  
phase-03, phase-04 <- phase-05  
phase-03, phase-05 <- phase-06  
ALL <- phase-07  

Совпадает с README dashboard? `N` — README table says `ALL <- phase-07`, but `phase-07/overview.md` omits phase-06; README graph also misses the direct `phase-03 -> phase-06` dependency.

## Validation commands
- `./gradlew :shared:feature:app-shell:domain:jvmTest` — realistic `Y`
- `./gradlew :shared:feature:app-shell:data:jvmTest` — realistic `Y`
- `./gradlew :shared:feature:app-shell:data:compileCommonMainKotlinMetadata` — realistic `Y`
- `./gradlew :apps:android-next:test` — realistic `Y`
- `./gradlew :apps:android-next:assembleDebug` — realistic `Y`
- `./gradlew :android:core:designsystem:compileDebugKotlin` — realistic `Y`
- `./gradlew :android:core:designsystem:lint` — realistic `Y`
- `./gradlew :android:core:designsystem:assembleDebug` — realistic `Y`
- `./gradlew :android:core:designsystem:connectedDebugAndroidTest` — realistic `Y`, but only with emulator/device
- `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — realistic `Y`
- `./gradlew :android:feature:app-shell:presentation:test` — realistic `Y`
- `./gradlew :android:feature:app-shell:presentation:connectedDebugAndroidTest` — realistic `Y`, but only with emulator/device
- `./gradlew assembleDebugAndroidTest` — weak/contested: root-scoped and not phase/module-specific
- `./gradlew test` — realistic `Y`, but very broad for a phase gate
- `./gradlew detekt ktlintCheck` — realistic `N` in current repo; tasks are not wired for active modules
- `adb install app-debug.apk` — realistic `N`; output path not specified
- `adb shell am start -n com.tpov.schoolquiz.next/.AppActivity` — realistic `N`; manifest activity is `.MainActivity`
- `find .../Navigator.kt`, `grep ...lightColorScheme`, `grep ...kotlin-serialization` — realistic `Y`

## Open Questions status
- OQ-PLAN-3 — `Y`: reflected in phase-01 overview/handoff/backend and matches real repo gap (`:shared:core:stats` absent today).
- OQ-PLAN-4 — `PARTIAL`: reflected in phase-06, but incomplete because phase-05 already needs mappings and the plan never allocates `TabConfig` / `DrawerFooterAction` labels.
- OQ-PLAN-5 — `STALE`: reflected in README/tests, but already resolved in current skeleton (`Visibility.kt` has `visibleFooterActions(isDebugBuild)`).

## Summary
- Исправить central phase-04 contract drift around `InitializeAppShellUseCase`.
- Перенести/явно описать все label/icon/title mappings до phase-05 compile gate.
- Добавить реальную конфигурацию `detekt`/`ktlint` или убрать несуществующие финальные validation-команды.
- Синхронизировать DS tokens/public wrapper signatures с `06-api-contract.md` и spec AC 14.
- Убрать незаконный `About -> Settings` navigation stub.
- Привести coverage claims к реальности: badge API, Journeys 7-9, Tab switch/Drawer section switch matrix, phase-05 AppShellScreen tests.
