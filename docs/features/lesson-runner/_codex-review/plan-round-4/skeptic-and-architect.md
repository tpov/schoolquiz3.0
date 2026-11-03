# Plan Review: lesson-runner ROUND 4

## Verdict
REJECT — several round-3 findings are still only partial/not resolved, and the round-4 patch set leaves a new phase-06 compile-gate blocker.

## Round 3 Findings — Resolution Status

1. **RESOLVED** — AC-55 `--include-dir` is gone from `docs/features/lesson-runner/plan/phase-07/overview.md:102`.

2. **RESOLVED** — phase-06 backend delete paths now use real `screen/` and `uistate/` dirs at `docs/features/lesson-runner/plan/phase-06/backend.md:204` and `:205`. Separate new blocker below: deletion scope is incomplete.

3. **RESOLVED** — overview scenarios are now GWT-shaped: phase-05 at `docs/features/lesson-runner/plan/phase-05/overview.md:98`, phase-07 at `docs/features/lesson-runner/plan/phase-07/overview.md:85`.

4. **RESOLVED** — phase-04 `RunnerEvent` includes `data object NavigateBack : RunnerEvent` at `docs/features/lesson-runner/plan/phase-04/backend.md:144`.

5. **RESOLVED** — phase-04 no longer assigns binding ownership to `quizzesPresentationModule`; phase-07 owns it at `docs/features/lesson-runner/plan/phase-07/backend.md:30` and `:37`.

6. **RESOLVED** — ADR-QS-15 status is no longer Accepted: `docs/features/quizzes-screen/03-decisions.md:499`.

7. **PARTIAL** — most design paths are hyphenated, but `docs/features/quizzes-screen/03-decisions.md:637` still uses filesystem path `android/feature/lesson_runner/presentation/src/main`.

8. **PARTIAL** — phase-03 backend refs were mostly fixed, but `docs/features/lesson-runner/plan/phase-02/backend.md:226`, `:246`, and `:266` still use `06-api-contract.md §LR-5` without line numbers.

9. **PARTIAL** — some phase-06 invariants have file:line refs, but required lines still lack them: `phase-06/backend.md:14`, `phase-06/frontend.md:10-14`, `phase-06/tests.md:10-13`.

10. **RESOLVED** — duplicate of item 4; `NavigateBack` exists at `docs/features/lesson-runner/plan/phase-04/backend.md:144`.

11. **PARTIAL** — CT-30 was added at `docs/features/lesson-runner/plan/phase-05/tests.md:35`, but stale old-contract lines remain: `CT-01..CT-29` at `:8` and old `LessonRunnerScreen(component = fakeComponent)` at `:13`.

12. **PARTIAL** — phase-07 tests use `LessonId("l1")` at `docs/features/lesson-runner/plan/phase-07/tests.md:49`, but phase-07 overview still has stale `parametersOf(ctx, "l1", Difficulty.EASY)` at `docs/features/lesson-runner/plan/phase-07/overview.md:76`.

13. **NOT RESOLVED** — no explicit `single<Clock> { Clock.System }` binding exists in `lessonRunnerDataModule`; the module cards omit it at `docs/features/lesson-runner/plan/phase-03/backend.md:192-198` and `docs/features/lesson-runner/06-api-contract.md:498-510`.

14. **RESOLVED** — duplicate of item 1; invalid `--include-dir` is removed.

## New Findings

- **Severity**: BLOCKER  
  **File:line**: `docs/features/lesson-runner/plan/phase-06/backend.md:200`  
  **Quote**: `Delete LessonPlaceholder* files (5 files)`  
  **Why**: actual code still has more `LessonPlaceholder` sources/tests than the plan deletes or updates. Examples: `LessonPlaceholderComponent.kt:5`, `DefaultLessonPlaceholderComponentTest.kt:40`, `QuizzesConfigSerializationTest.kt:119`, `DefaultLessonListComponentTest.kt:144`. Removing `QuizzesConfig.LessonPlaceholder` while leaving these tests/references will break `:android:feature:quizzes-screen:presentation:test`.  
  **Suggested fix**: expand phase-06 scope to delete/update all `LessonPlaceholder` files and tests, including `LessonPlaceholderComponent.kt`, `DefaultLessonPlaceholderComponentTest.kt`, old serialization tests, and old lesson-list navigation assertions.

- **Severity**: HIGH  
  **File:line**: `docs/features/lesson-runner/plan/phase-07/overview.md:102`  
  **Quote**: `rg "getKoin\(|koinInject\(|UseCase|Repository|Dao" android/feature/lesson-runner/presentation/src/main -g "*.kt"`  
  **Why**: this grep scans all presentation code, but phase-04 intentionally places `UseCase`/`Repository` constructor deps in the component/module. The gate will false-positive on valid code.  
  **Suggested fix**: restrict AC-55 to screen files and direct DI calls, e.g. screen-only `getKoin|koinInject|inject<` plus import checks for repository/use-case packages.

- **Severity**: HIGH  
  **File:line**: `docs/features/lesson-runner/plan/phase-02/backend.md:67`  
  **Quote**: `canonical per 06-api-contract.md:606`  
  **Why**: `06-api-contract.md:606` is `lessonRunnerPresentationModule`, not `LessonAttemptDao.upsert`; the DAO upsert is around `06-api-contract.md:644`. Similar stale refs exist at `phase-06/backend.md:147` (`:343` should be factory binding `:374`) and `phase-05/frontend.md:17` (`:377` is not `RunnerUiState.Result`).  
  **Suggested fix**: re-run the line-number pass against current `06-api-contract.md` and update all stale refs.

- **Severity**: MEDIUM  
  **File:line**: `docs/features/quizzes-screen/03-decisions.md:499`  
  **Quote**: `SUPERSEDED by ADR-QS-17 / ADR-LR-16`  
  **Why**: the requested superseded note should point to ADR-LR-16 + ADR-LR-17; the status and final note at `:502` still point planner refs to ADR-QS-17 + ADR-LR-16.  
  **Suggested fix**: change the superseded status/note to ADR-LR-16 + ADR-LR-17, and make the old core/navigation decision explicitly historical/non-implementable.

## Things Done Well

`RunnerEvent.NavigateBack` is now present in the phase-04 signature card. Factory binding ownership is much clearer and moved to phase-07 composition root. The phase-07 IT-09e test itself now uses the correct `LessonId("l1")` parameter shape. Plan files also have no fenced `kotlin`/`java`/`groovy` blocks.