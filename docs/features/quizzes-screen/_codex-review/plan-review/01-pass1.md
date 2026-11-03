# Plan Review — quizzes-screen

## Verdict

REJECT

## Lens 1 — Sequencing

### Findings

#### F1.1 — Plan AC table uses non-canonical AC numbering and is incomplete [blocker]

- **File**: `docs/features/quizzes-screen/plan/README.md:126`
- **Issue**: The plan dashboard AC table does not match canonical AC#1..AC#39 from spec/testing.
- **Evidence**: Plan says `AC#1 | QuizzesScreen overlay appears on catalog tap`; canonical spec AC#1 is `GIVEN пользователь на HomeQuestsScreen... WHEN тапает каталог...` at `docs/features/quizzes-screen/0-spec.md:475`, and testing table maps AC#1 to that same behavior at `docs/features/quizzes-screen/04-testing.md:480`. Plan also omits AC#18, AC#19, AC#24, AC#28, AC#30-37, AC#39 (`plan/README.md:143-151`).
- **Suggested fix**: Replace `plan/README.md` AC table with exact AC#1..AC#39 numbering/descriptions from `0-spec.md:475-528` and phase mappings from `04-testing.md:480-518`.

#### F1.2 — Phase dependency dashboard contradicts phase files [high]

- **File**: `docs/features/quizzes-screen/plan/README.md:34`
- **Issue**: Dashboard says Phase-03 depends only on Phase-02, but Phase-03 overview/tests require Phase-01 fakes. It also says Phase-05 depends on Phase-04, while Phase-03 handoff says Phase-04 and Phase-05 can run in parallel.
- **Evidence**: `Phase-03 | ... | Phase-02` at `plan/README.md:34`; `Depends on: Phase-01 ... Phase-02` at `phase-03/overview.md:114`; `FakeQuestRepository ... from Phase-01` at `phase-03/tests.md:13`. Parallel contradiction: `Phase-05 ... Depends: Phase-02 + Phase-04` at `plan/README.md:20`, but `Phase-04 ... and Phase-05 ... can work параллельно` at `phase-03/overview.md:176`.
- **Suggested fix**: Decide whether Phase-03 truly needs Phase-01. Then sync `plan/README.md`, `phase-03/overview.md`, and handoff notes.

#### F1.3 — REQUIRES items are still delegated to implementers [blocker]

- **File**: `docs/features/quizzes-screen/plan/README.md:157`
- **Issue**: REQUIRES is treated as “verify during implementation,” which the review prompt explicitly calls a planning failure.
- **Evidence**: `REQUIRES — BackCallback.PRIORITY_OVERLAY: Verify constant availability... before Phase-03` at `plan/README.md:157`; `Frontend-dev verifies при реализации` at `phase-03/overview.md:159`; `REQUIRES verify @OptIn ... frontend-dev проверяет при реализации` at `phase-02/overview.md:141`.
- **Suggested fix**: Resolve these during planning with concrete evidence, or mark DEFERRED with rationale and explicit unblock criteria.

#### F1.4 — Breadcrumb callback plan is internally non-compilable [blocker]

- **File**: `docs/features/quizzes-screen/plan/phase-05/frontend.md:57`
- **Issue**: Child screens are planned to call `component::popToLevel`, but child component contracts do not expose `popToLevel`.
- **Evidence**: `QuestListScreen(component: QuestListComponent)` at `phase-05/frontend.md:50`, then `onSegmentClick = component::popToLevel` at `phase-05/frontend.md:57`. Canonical contract puts `popToLevel` only on `QuizzesComponent` at `06-api-contract.md:395-410`; `QuestListComponent` has only `state`, `titles`, `onQuestClick`, `onShareClick` at `06-api-contract.md:534-538`. Plan admits the problem and leaves a decision to frontend-dev at `phase-05/frontend.md:88-90`.
- **Suggested fix**: Update canonical screen signatures to accept `onBreadcrumbClick: (Int) -> Unit`, and have `QuizzesScreen` pass `component::popToLevel` to every child screen.

#### F1.5 — Invariant 3 mapper conflict is unresolved in the plan [blocker]

- **File**: `docs/features/quizzes-screen/plan/phase-04/frontend.md:216`
- **Issue**: Phase-04 tells quizzes-screen to use or choose around a mapper in `quest/presentation`, which is a forbidden cross-feature import.
- **Evidence**: Plan says `it.toDisplayItem()` comes from `android/feature/quest/presentation/.../QuestToDisplayItem.kt` and asks frontend-dev to choose an alternative at `phase-04/frontend.md:216`. Architecture forbids `quizzes-screen/presentation` importing `android/feature/quest/presentation` at `01-architecture.md:106-110`.
- **Suggested fix**: Resolve the mapper location in the plan: either add a local mapper in `quizzes-screen/presentation` or move the mapper to a core/designsystem-owned module with backend-owned Gradle changes.

#### F1.6 — Share dispatch ownership contradicts behavior docs [high]

- **File**: `docs/features/quizzes-screen/plan/phase-06/overview.md:11`
- **Issue**: Overview says implement a production body in `DefaultQuestListComponent.onShareClick`, but frontend plan says the component has no `Context` and dispatch should happen in UI, then leaves it as a frontend decision.
- **Evidence**: `Добавить DefaultQuestListComponent.onShareClick production body` at `phase-06/overview.md:11` and modified file at `phase-06/overview.md:58`; `Intent dispatch должен быть в UI layer` and `Frontend-dev decision` at `phase-06/frontend.md:69-72`. Behavior DFD shows `QuestListScreen` builds and starts the intent at `02-behavior.md:346-347`.
- **Suggested fix**: Pick one architecture in the plan, preferably the behavior-doc path: `QuestListScreen` dispatches via `LocalContext`, while component API is removed or remains a no-op only if the contract is updated.

#### F1.7 — Instrumented validation commands are not realistic enough [high]

- **File**: `docs/features/quizzes-screen/plan/README.md:32`
- **Issue**: Dashboard uses `connectedAndroidTest` without a device requirement, while phase validations often only build androidTest APKs instead of running the instrumented tests they require.
- **Evidence**: Project context says `connectedAndroidTest` requires a connected device at `.claude/PROJECT-CONTEXT.md:16`. Plan dashboard runs connected tests at `plan/README.md:32-33` and `plan/README.md:37`; Phase-05 validation only runs `assembleDebugAndroidTest` at `phase-05/overview.md:128-129`.
- **Suggested fix**: Split validation into “build instrumented APK” and “run on device/emulator,” and explicitly mark device-required commands.

## Lens 2 — Plan-as-ТЗ

### Findings

#### F2.1 — Canonical references use sections, not required file:line refs [blocker]

- **File**: `docs/features/quizzes-screen/plan/phase-03/frontend.md:34`
- **Issue**: Public type Signature Cards cite `06-api-contract.md §N` instead of exact `06-api-contract.md:NN`.
- **Evidence**: `Canonical reference: 06-api-contract.md §10` at `phase-03/frontend.md:34`; required format is `06-api-contract.md:NN` per `.claude/agents/planner.md:66-68`.
- **Suggested fix**: Replace every public-type canonical reference with exact line refs, e.g. `06-api-contract.md:337`.

#### F2.2 — Plan duplicates public API implementation details instead of deferring to SSoT [blocker]

- **File**: `docs/features/quizzes-screen/plan/phase-03/frontend.md:102`
- **Issue**: Signature Cards copy full public constructors and implementation steps from `06-api-contract.md`, creating redundant source-of-truth.
- **Evidence**: Full `DefaultQuizzesComponent(...)` constructor at `phase-03/frontend.md:102`, plus implementation bullets for `childStack`, `BackCallback`, `openQuestList`, etc. at `phase-03/frontend.md:107-117`; canonical definition already exists at `06-api-contract.md:424-450`.
- **Suggested fix**: Keep only a short inline signature and replace implementation bullets with “see `06-api-contract.md:424`.”

#### F2.3 — Test tasks contain assertion code, not only scenarios [blocker]

- **File**: `docs/features/quizzes-screen/plan/phase-02/tests.md:23`
- **Issue**: Tests include concrete assertion implementation, violating the “scenarios, not code” checklist.
- **Evidence**: `Добавить assertion: assertEquals(...)` at `phase-02/tests.md:23`; another implementation note includes `assertDoesNotExist()` and `assertEquals(...)` at `phase-06/tests.md:40`. The lens forbids `assertEquals(...)` style implementation details at `.claude/skills/adversarial-review/references/plan-review-lens.md:57-63`.
- **Suggested fix**: Rewrite as backtick test names with given/when/then only.

#### F2.4 — Pattern Invariants lack canonical file:line references [high]

- **File**: `docs/features/quizzes-screen/plan/phase-07/frontend.md:10`
- **Issue**: Pattern Invariants are prose-only and do not cite existing code examples with `file:line`.
- **Evidence**: Invariants list “never import quizzes-screen,” “quizzesComponent created first,” and overlay z-order at `phase-07/frontend.md:10-14`, but no canonical examples. Lens requires every invariant to contain a `file:line` reference at `plan-review-lens.md:80-83`.
- **Suggested fix**: Add concrete refs such as `.claude/rules/clean-architecture.md:51`, `DefaultHomeQuestsComponent.kt:33`, `BrandComponentsInvariantsTest.kt:53`.

## Summary

The plan is not implementable as-is. Top blockers: the dashboard AC numbering is not the canonical AC#1-39, unresolved REQUIRES/open architecture choices are pushed into implementation, and Phase-05 breadcrumb wiring is non-compilable against the public component contracts. The no-fenced-Kotlin grep passed, but the plan still violates the Plan-as-ТЗ lens through section-only canonical refs, duplicated public API details, and assertion-level test instructions.