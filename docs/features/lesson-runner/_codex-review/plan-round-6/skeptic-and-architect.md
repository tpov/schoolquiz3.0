# Plan Review: lesson-runner ROUND 6

## Verdict
REJECT: phase-06 overview still has a stale deletion file map that can leave compile-breaking `LessonPlaceholder*` files behind.

## Round 5 Findings — Resolution Status

1. **PARTIAL**: `phase-06/overview.md:21` is fixed: “DELETE: 4 production + 3 test... UPDATE: 2 теста”. But the same overview still lists only 5 deleted files at `phase-06/overview.md:76-82`, while `phase-06/backend.md:204-217` lists 4 production deletes, 3 test deletes, and 2 test updates.

2. **RESOLVED**: `phase-03/backend.md:54,56,68,74` now use the correct DAO refs: `:645` for attempt upsert, `:663` for rating upsert, and `:666` for `hasSubmitted`.

3. **PARTIAL**: README AC-55 is fixed at `plan/README.md:87`, and a round-5 status exists at `plan/README.md:152`. But `plan/README.md:3` still says round-3, AC-62 still says `CT-01..CT-29` at `plan/README.md:94`, and the deleted file map remains stale at `plan/README.md:127-132`.

4. **PARTIAL**: `phase-05/overview.md:88` and `04-testing.md:310` use `CT-01..CT-30`; `plan/README.md:94` still uses `CT-01..CT-29`.

5. **RESOLVED**: `phase-03/overview.md:124` now references `06-api-contract.md:87` instead of bare `§LR-4`.

## New Findings

- **Severity**: BLOCKER  
  **File:line**: `docs/features/lesson-runner/plan/phase-06/overview.md:76-82`  
  **Quote**: Deleted Files list includes `DefaultLessonPlaceholderComponent.kt`, `LessonPlaceholderScreen.kt`, `LessonPlaceholderUiState.kt`, `FakeLessonPlaceholderComponent.kt`, `LessonPlaceholderScreenTest.kt`.  
  **Why**: This contradicts `phase-06/backend.md:204-217`. It omits `LessonPlaceholderComponent.kt` and `DefaultLessonPlaceholderComponentTest.kt`, and can leave unresolved symbols/tests after the atomic replacement.  
  **Suggested fix**: Replace the overview Deleted Files section with the exact 7 DELETE entries from `phase-06/backend.md:204-213`, and keep the 2 UPDATE tests explicit or referenced.

- **Severity**: HIGH  
  **File:line**: `docs/features/lesson-runner/plan/phase-06/overview.md:140`  
  **Quote**: `composition root per 06-api-contract.md:343`  
  **Why**: Current `06-api-contract.md:343` is the `popCurrentChild()` code fence area. The `LessonRunnerComponentFactory` Koin binding is at `06-api-contract.md:374`.  
  **Suggested fix**: Change `:343` to `:374`.

- **Severity**: HIGH  
  **File:line**: `docs/features/lesson-runner/plan/phase-06/overview.md:169`  
  **Quote**: `ephemeral UI state per 06-api-contract.md:452`  
  **Why**: Current `06-api-contract.md:452` is `val items: List<OptionUi>` inside `QuestionUiState.Ordering`. `isHardChecked` is at `06-api-contract.md:486`, with explanatory text at `:490`.  
  **Suggested fix**: Change to `06-api-contract.md:486` or `:490`.

- **Severity**: HIGH  
  **File:line**: `docs/features/lesson-runner/06-api-contract.md:476`  
  **Quote**: `.../model/LessonItemUi.kt`  
  **Why**: Plan files consistently moved `LessonItemUi.kt` to `uistate/` (`phase-06/backend.md:90`, `phase-06/overview.md:62`, README line 115). The canonical contract still says `model/`, so the SSoT disagrees with the plan.  
  **Suggested fix**: Update `06-api-contract.md:476` to `.../uistate/LessonItemUi.kt`.

- **Severity**: MEDIUM  
  **File:line**: `docs/features/lesson-runner/plan/phase-03/tests.md:44`  
  **Quote**: `Flow<Boolean> — per 06-api-contract.md:662`  
  **Why**: `:662` is the `@Insert` annotation for rating upsert; `hasSubmitted` is at `06-api-contract.md:666`.  
  **Suggested fix**: Change to `06-api-contract.md:666`.

- **Severity**: MEDIUM  
  **File:line**: `docs/features/lesson-runner/plan/phase-02/backend.md:55`  
  **Quote**: `06-api-contract.md:657 (§LR-17 rating entity)`  
  **Why**: Current `:657` is the `LessonRatingLocalDao` file line, not the rating entity. `LessonRatingSubmittedLocalEntity` starts at `06-api-contract.md:693`.  
  **Suggested fix**: Change to `06-api-contract.md:693` or `:700`.

## Things Done Well

The named phase-03 DAO refs were fixed cleanly. AC-55 is now scoped to screen files, which is the right architectural gate. `phase-05` and `04-testing.md` now agree on CT-30. The plan files also pass the fenced Kotlin/Java/Groovy check: 0 matches.