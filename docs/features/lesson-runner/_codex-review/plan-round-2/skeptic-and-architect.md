# Plan Review: lesson-runner ROUND 2

## Verdict
REJECT — several round-1 fixes are only partial, and the fix-loop introduced new compile/runtime blockers around navigation events, Koin wiring, stale ADRs, and DAO tests.

## Round 1 Findings — Resolution Status

- **BLOCKER 1 — core/navigation cycle: PARTIAL**
  - Fixed in phase plan: `phase-04/backend.md:18-20`, `phase-04/backend.md:47-66`, `phase-06/backend.md:46-54`.
  - Still contradicted by accepted design: `docs/features/quizzes-screen/03-decisions.md:507`, `:538` still says `LessonRunnerRootComponent` lives in `android/core/navigation/`.

- **BLOCKER 2 — QuizzesScreen cross-feature import: PARTIAL**
  - New one-way direction is documented in `phase-06/backend.md:162-169` and Gradle dep in `phase-06/backend.md:176-184`.
  - But ADR-QS-15 still says no `quizzes-screen → lesson-runner/presentation` import at `docs/features/quizzes-screen/03-decisions.md:545`.

- **BLOCKER 3 — DAO API mismatch: PARTIAL**
  - Fixed in core backend cards: `phase-02/backend.md:67-89`, `phase-03/backend.md:45`, `:67-68`.
  - Still stale elsewhere: `phase-03/backend.md:10-11`, `phase-02/tests.md:92`, `:104-115`, `phase-03/tests.md:123-131`.

- **BLOCKER 4 — Provider interfaces missing: PARTIAL**
  - Interfaces are added correctly in `phase-01/backend.md:151-199` under `domain/provider/`; overview lists them at `phase-01/overview.md:71-73`.
  - But phase-03 Koin adapter now has constructor-signature drift. See new finding #3.

- **HIGH 5 — FLAG_SECURE on Result: RESOLVED**
  - `phase-05/frontend.md:17` and `:29` use `state.attempt.mode == Difficulty.HARD`.

- **HIGH 6 — AC-55..65: PARTIAL**
  - README maps AC-55..65 at `README.md:74-84`.
  - But some claimed gates are missing or wrong-path: AC-56 uses `android/feature/lesson_runner` at `README.md:75`; AC-55/58 claim phase-07 validation, but `phase-07/overview.md:87-99` does not include those greps.

- **ТЗ lens — phase-06 Options Considered: RESOLVED**
  - Three options present at `phase-06/overview.md:143-163`.

- **ТЗ lens — canonical refs line-number format: NOT RESOLVED**
  - Still uses section refs, e.g. `phase-04/backend.md:106`, `:128`, `phase-03/backend.md:142`, `phase-06/backend.md:37`.

- **ТЗ lens — Pattern Invariants file:line refs: NOT RESOLVED**
  - Some refs exist, e.g. `phase-06/overview.md:168`.
  - But many invariants have no `file:line`: `phase-06/frontend.md:10-14`, `phase-06/tests.md:10-12`, `phase-06/backend.md:10-16`.

- **ТЗ lens — overview Tests Required GWT format: NOT RESOLVED**
  - Still not consistently `given/when/then`: `phase-05/overview.md:97-100`, `phase-07/overview.md:83-85`, `phase-02/overview.md:95`, `:99-100`.

- **ТЗ lens — README module count: RESOLVED**
  - `README.md:92-95` says 3 new Gradle modules.

- **ТЗ lens — `screen/` and `uistate/` paths: PARTIAL**
  - Existing code uses `screen/` and `uistate/`; phase-06 overview matches at `phase-06/overview.md:62-63`.
  - But phase-06 backend still says `.../model/LessonItemUi.kt` at `phase-06/backend.md:86` and stale delete paths `.../ui`, `.../state` at `:199-200`.

## New Findings (introduced by fix-loop)

- **Severity: BLOCKER**
  - **File:line:** `phase-04/backend.md:159`, `:222`; `07-events.md:20-23`; `phase-06/backend.md:162`
  - **Quote:** “`RunnerEvent.NavigateBack`”; event contract only defines `SaveAttemptFailed` and `SaveRatingFailed`; `QuizzesScreen` adds `LessonRunnerScreen(child.component)`.
  - **Why:** The plan removed `StackNavigation` from the runner, but did not add a real navigation callback/event. `onExit()` / `onFinish()` cannot pop, and `RunnerEvent.NavigateBack` would not compile.
  - **Suggested fix:** Add canonical `RunnerEvent.NavigateBack` to `07-events.md` + `06-api-contract.md` and handle it from the Quizzes host, or change `LessonRunnerScreen(component, onNavigateBack)` and wire `navigation.pop()` in phase-06.

- **Severity: BLOCKER**
  - **File:line:** `phase-06/backend.md:143-144`; `phase-07/backend.md:24-31`
  - **Quote:** “`lessonRunnerFactory = get<LessonRunnerComponentFactory>()`” / phase-07 only adds four modules.
  - **Why:** Nothing in phase-07 actually registers `single<LessonRunnerComponentFactory>`, despite `06-api-contract.md:345-349`. Koin will fail resolving `DefaultQuizzesComponent`.
  - **Suggested fix:** Add the app-level `LessonRunnerComponentFactory` binding in phase-07 and add an IT that resolves `get<LessonRunnerComponentFactory>()`.

- **Severity: BLOCKER**
  - **File:line:** `phase-03/backend.md:215-217`
  - **Quote:** `CompleteAttemptUseCase(lessonAttemptRepository = get(), attemptIdProvider = ..., clock = get())`
  - **Why:** Actual constructors require `attemptRepository`, `ratingRepository`, `clock`, `attemptIdProvider` for complete; submit also requires `lessonRepository` and `clock` (`06-api-contract.md:536-557`; source confirms this).
  - **Suggested fix:** Replace adapter signatures with the exact canonical block from `06-api-contract.md:526-557`.

- **Severity: BLOCKER**
  - **File:line:** `docs/features/quizzes-screen/03-decisions.md:507`, `:538`, `:545`
  - **Quote:** “`LessonRunnerComponentFactory` ... in `android/core/navigation/`”; “`quizzes-screen/presentation` не импортирует `lesson-runner/presentation`”.
  - **Why:** ADR-QS-15 remains accepted and directly contradicts ADR-LR-16/17, reintroducing the original circular/core-boundary blocker.
  - **Suggested fix:** Mark ADR-QS-15 superseded by ADR-QS-17/ADR-LR-16 or rewrite it to the new one-way dependency.

- **Severity: HIGH**
  - **File:line:** `phase-06/overview.md:127`, `:130`; `README.md:75`
  - **Quote:** `android/feature/lesson_runner`
  - **Why:** Module paths use hyphen (`lesson-runner`), not underscore. These validation greps either fail or miss the module, weakening AC-56.
  - **Suggested fix:** Use `android/feature/lesson-runner/presentation/src/main` for filesystem greps; keep underscores only inside Kotlin package paths.

- **Severity: HIGH**
  - **File:line:** `phase-02/tests.md:92`, `:104-115`; `phase-03/tests.md:123-131`
  - **Quote:** `dao.insert(entity)`; `OnConflictStrategy.IGNORE`; `hasSubmitted ... 1 else 0`
  - **Why:** Test blueprints still target the rejected DAO API. Implementing them literally will not compile against `upsert(): Long` and `Flow<Boolean>`.
  - **Suggested fix:** Rename fake/test methods to `upsert`, use REPLACE semantics, and model `hasSubmitted` as `Flow<Boolean>`.

## Cross-cutting risks
The plan now has SSoT drift: README/API/phase backend/design ADRs do not describe the same architecture. Several handoff notes say “phase-07 does X,” but phase-07 does not actually own those tasks. Validation commands also mix module paths and package paths.

## Things Done Well
No fenced Kotlin/Java/Groovy plan blocks were introduced. Provider interfaces are placed in the right domain package. Phase-06 now has real options considered, and the `FLAG_SECURE` result-state fix is correct.