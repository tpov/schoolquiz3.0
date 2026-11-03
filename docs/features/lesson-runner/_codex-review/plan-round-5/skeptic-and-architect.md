# Plan Review: lesson-runner ROUND 5 (final)

## Verdict
REJECT — several round-4 items are still only partial, and the round-5 sweep found a new compile-risk blocker in phase-06 overview cleanup scope.

## Round 4 Findings — Resolution Status

1. **RESOLVED** — `quizzes-screen/03-decisions.md` no longer has `lesson_runner/presentation/src`; current refs use hyphen paths, e.g. [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:637).

2. **PARTIAL** — phase-02 refs at [phase-02/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-02/backend.md:226), `:246`, `:266` now use `06-api-contract.md:129`, but [phase-03/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-03/overview.md:124) still has `06-api-contract.md §LR-4` without a line number.

3. **PARTIAL** — some phase-06 invariants now have precedents, but several still lack file:line refs where applicable: [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:14), [phase-06/frontend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/frontend.md:10), [phase-06/tests.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/tests.md:10). Grep gates are acceptable; these are not only grep gates.

4. **RESOLVED** — [phase-05/tests.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-05/tests.md:8) says `CT-01..CT-30`, and render calls use `LessonRunnerScreen(..., onNavigateBack, onSegmentClick)` at `:13`, `:32`, `:38`.

5. **RESOLVED** — IT-09e uses `LessonId("l1")` in [phase-07/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-07/overview.md:76) and `:89`.

6. **PARTIAL** — [phase-03/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-03/backend.md:195) adds `single<Clock> { Clock.System }`, but canonical [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:499) still omits it while later Koin blocks use `clock = get()`.

7. **RESOLVED** — [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:205) now deletes `LessonPlaceholderComponent.kt`; [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:211) deletes `DefaultLessonPlaceholderComponentTest.kt`; [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:216) and `:217` update serialization/list tests.

8. **RESOLVED** — AC-55 validation is restricted to screen files in [phase-07/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-07/overview.md:102), `:103`, `:105`.

9. **RESOLVED for named refs** — DAO refs now use `:645`/`:663`; factory binding uses `:374`; `RunnerUiState.Result` uses `:408`.

10. **PARTIAL** — status line is fixed at [quizzes-screen/03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:499), but the superseded note still ends with “planner/dev refs should go to ADR-QS-17 + ADR-LR-16” at `:502`.

## New Findings (introduced by round-5 patches)

- **Severity**: BLOCKER  
  **File:line**: [phase-06/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/overview.md:21)  
  **Quote**: `DELETE: 5 LessonPlaceholder* files`  
  **Why**: overview cleanup scope still omits `LessonPlaceholderComponent.kt` and `DefaultLessonPlaceholderComponentTest.kt`, although backend.md now correctly lists them. A worker following overview can leave compile/test-breaking references.  
  **Suggested fix**: align phase-06 overview Deleted Files with backend.md: 4 production deletes, 3 test deletes, plus 2 test updates.

- **Severity**: HIGH  
  **File:line**: [phase-03/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-03/backend.md:54)  
  **Quote**: `LessonAttemptDao (Phase-02, 06-api-contract.md:606)`  
  **Why**: current `06-api-contract.md:606` is the presentation Koin module block, not the DAO. Similar stale refs remain at `phase-03/backend.md:56`, `:68`, `:74`, `phase-03/tests.md:44`, `phase-06/overview.md:140`, `phase-06/overview.md:169`.  
  **Suggested fix**: rerun the line-ref pass against current `06-api-contract.md`; use DAO refs around `:643/:645/:661/:666`, factory binding `:374`, and `LessonItemUi` `:474/:486`.

- **Severity**: HIGH  
  **File:line**: [plan/README.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/README.md:87)  
  **Quote**: `rg "getKoin\(\|koinInject\(\|UseCase\|Repository" android/feature/lesson-runner/presentation/src/main -g "*.kt"`  
  **Why**: top-level dashboard still contains the old broad AC-55 grep, while phase-07 overview has the corrected screen-only check. README also still says round-3 status, CT-29, and stale deletion map.  
  **Suggested fix**: update README status/dashboard from phase files after round-5 patches.

- **Severity**: MEDIUM  
  **File:line**: [phase-05/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-05/overview.md:88)  
  **Quote**: `CT-01..CT-29`  
  **Why**: phase-05 tests now define CT-30, but overview and `04-testing.md:310` still advertise CT-29 in places.  
  **Suggested fix**: normalize CT ranges to `CT-01..CT-30` wherever the full Compose suite is referenced.

## Residual Issues Acceptable for Implementation

- The grep for fenced `kotlin`/`kt`/`java`/`groovy` blocks returned zero matches in plan files.
- Inline signature cards remain acceptable; the problem is stale cross-reference metadata, not code embedded in plan.

## Things Done Well

The targeted phase-05 tests fix is clean. Phase-07’s AC-55 validation is now the right shape. The phase-06 backend cleanup section is much safer than round 4 and names the previously missed placeholder files/tests.