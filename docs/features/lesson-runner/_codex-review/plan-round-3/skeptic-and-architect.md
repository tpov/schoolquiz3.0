# Plan Review: lesson-runner ROUND 3

## Verdict
REJECT — several round-2 findings remain partial, and the fix loop introduced new contract contradictions around `RunnerUiState.Result` and `LessonRunnerComponentFactory` binding ownership.

## Round 2 Findings — Resolution Status

1. **BLOCKER 3 — DAO API in test blueprints: RESOLVED**  
   Evidence: `phase-02/tests.md:92,104-105,115`; `phase-03/tests.md:123,130-131` use `upsert`, REPLACE semantics, and `Flow<Boolean>`.

2. **HIGH 6 — AC-55..65 mapping: PARTIAL**  
   Hyphen paths and AC-55/56/58 greps exist: `README.md:87-90`, `phase-07/overview.md:101-111`. But AC-55 only scans `**/screen/**/*.kt`, while lesson-runner UI is planned under `.../ui/LessonRunnerScreen.kt` at `phase-05/frontend.md:23`, so the final gate misses the main Compose files.

3. **ТЗ paths — phase-06 `uistate/` and `screen/`: RESOLVED**  
   Evidence: `phase-06/overview.md:62-63,70-72`; `phase-06/backend.md:90,160,207-208`.

4. **Canonical refs format: PARTIAL**  
   Most refs were converted, but `phase-03/backend.md:76` still says `06-api-contract.md:635 (§LR-16 LessonRatingLocalDao)`.

5. **Pattern Invariants file:line refs: PARTIAL**  
   Backend improved, but frontend/tests still lack real `file:line` refs: `phase-06/frontend.md:10-14`, `phase-06/tests.md:10-13`. README also admits this residual issue at `README.md:165`.

6. **Overview Tests Required GWT format: RESOLVED**  
   Evidence: `phase-02/overview.md:95-106`, `phase-05/overview.md:98-103`, `phase-07/overview.md:85-92`.

7. **Navigation A2 hybrid: PARTIAL**  
   Main backend/frontend/host wiring is present: `phase-04/backend.md:37-42,144,172-178`, `phase-05/frontend.md:25,30`, `phase-06/backend.md:76,166`. But `phase-04/tests.md:84-88,114-118` still expects `FakeNavigator` / `navigation.pop()` instead of asserting `RunnerEvent.NavigateBack`.

8. **`LessonRunnerComponentFactory` binding + IT-09h: PARTIAL**  
   Phase-07 adds it: `phase-07/backend.md:30`, `phase-07/tests.md:64-68`. However accepted ADR-LR-20 says the opposite: binding lives in `lessonRunnerPresentationModule`, not `AppApplication`, and App binding would be duplicate: `03-decisions.md:757,769`.

9. **`lessonRunnerDomainKoinAdapter` constructors: RESOLVED**  
   Evidence: `phase-03/backend.md:215-218` matches canonical `06-api-contract.md:560-590`, including provider method refs.

10. **ADR-QS-15 SUPERSEDED noted in plan refs: PARTIAL**  
   Good in `phase-06/backend.md:16`, but stale live reference remains in `phase-06/overview.md:49`.

11. **Hyphen filesystem grep paths everywhere: PARTIAL**  
   Plan paths are mostly fixed, but `01-architecture.md:190,194` still uses filesystem path `shared/feature/lesson_runner/...` instead of `shared/feature/lesson-runner/...`.

12. **Round-1 carryover checks: see below.**

## Round 1 Resolved Items — Still Resolved?

- **HIGH 5 / FLAG_SECURE:** CONTESTED. Phase-05 now uses `state.mode == Difficulty.HARD` via ADR-LR-19 (`phase-05/frontend.md:17,29`), not `state.attempt.mode`. That can be fine, but phase-04/API still describe `Result(val attempt: Attempt...)`, creating a contract mismatch.
- **phase-06 Options Considered:** still resolved; 3 options at `phase-06/overview.md:154-174`.
- **README module count:** still resolved; 3 new Gradle modules at `README.md:105-108`.

## New Findings

- **Severity: BLOCKER**  
  **File:line:** `phase-04/backend.md:100`; `phase-05/frontend.md:29`; `06-api-contract.md:408-412`; `03-decisions.md:726,741`  
  **Quote:** `data class Result(val attempt: Attempt, ...)` vs `use state.mode == Difficulty.HARD`  
  **Why:** Phase-05 expects flat `RunnerUiState.Result` fields, while phase-04/API still instruct implementers to create `Result(attempt: Attempt)`. Following both cannot compile cleanly, and the old shape contradicts ADR-LR-19’s PII-minimization decision.  
  **Suggested fix:** Update `06-api-contract.md` and phase-04 backend/tests to the flat Result contract, or explicitly supersede ADR-LR-19 and revert phase-05.

- **Severity: BLOCKER**  
  **File:line:** `phase-07/backend.md:30,37`; `03-decisions.md:757,769`  
  **Quote:** “Добавить `single<LessonRunnerComponentFactory>` binding в `startKoin`” vs “не должны добавлять ... в `AppApplication.kt`”  
  **Why:** The accepted ADR and phase plan prescribe opposite binding owners. Implementing both risks duplicate Koin bindings; following only one leaves the other doc as false SSoT.  
  **Suggested fix:** Pick one binding owner. Then update `03-decisions.md`, `06-api-contract.md:374-381`, phase-04, phase-07, README, and IT-09h together.

- **Severity: HIGH**  
  **File:line:** `phase-04/tests.md:84-88,114-118`  
  **Quote:** “FakeNavigator” / “navigation.pop() called” / “navigation popped”  
  **Why:** The component no longer owns navigation. These test blueprints will push test-dev toward a removed constructor dependency.  
  **Suggested fix:** Rewrite PT-05/PT-25 to collect `component.events` and assert `RunnerEvent.NavigateBack`; add explicit `onCrossConfirmed()` and `onBack()` NavigateBack tests.

- **Severity: HIGH**  
  **File:line:** `phase-07/overview.md:103-105`; `phase-05/frontend.md:23`  
  **Quote:** `-g "**/screen/**/*.kt"` while `LessonRunnerScreen.kt` is in `.../ui/`  
  **Why:** AC-55’s final architecture gate misses the lesson-runner Compose files.  
  **Suggested fix:** Scan both `**/screen/**/*.kt` and `**/ui/**/*.kt`, or scan all presentation UI files while excluding component/di packages.

## Cross-cutting risks
The plan has SSoT drift between phase docs, `06-api-contract.md`, and newer ADRs. The biggest pattern: later ADRs were appended, but phase files and canonical blocks were not updated atomically.

## Things Done Well
DAO test blueprints are now aligned, A2 navigation is mostly present in the implementation plan, CT-30 is documented, phase-06 uses real `screen/` and `uistate/` paths, and the fenced Kotlin/Java/Groovy grep returns no matches.