# Plan Review: lesson-runner

## Verdict
REJECT — the plan leaves compile-blocking architecture questions unresolved and several phase files drift from the canonical API contract.

## Lens 1 — Sequencing Findings

- **Severity**: BLOCKER  
  **File:line**: [README.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/README.md:101), [phase-04/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-04/backend.md:38)  
  **Quote**: “`core/navigation → lesson-runner/presentation` dep” / “поднять как Open Question”  
  **Why**: `android/core/navigation` cannot depend on a feature presentation module; `lesson-runner/presentation` would also need `core/navigation` to implement the interface, creating a Gradle cycle.  
  **Suggested fix**: Resolve before Phase-04. Move the whole contract state to a proper API/boundary module, or invert rendering so `core/navigation` never imports `RunnerUiState` / `RunnerEvent`.

- **Severity**: BLOCKER  
  **File:line**: [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:160), [clean-architecture.md](/home/Programming/Android/schoolquiz4.0/.claude/rules/clean-architecture.md:55)  
  **Quote**: “Import `LessonRunnerScreen` из `android/feature/lesson-runner/presentation`”  
  **Why**: Project rules explicitly forbid `android/feature/A/presentation → android/feature/B/presentation`. Combined with Phase-04’s `StackNavigation<QuizzesConfig>` in lesson-runner, this risks bidirectional presentation coupling.  
  **Suggested fix**: Remove direct screen import. Use a boundary/factory/slot owned outside sibling feature presentation, and make `DefaultLessonRunnerRootComponent` navigation-agnostic.

- **Severity**: BLOCKER  
  **File:line**: [phase-02/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-02/backend.md:67), [phase-03/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-03/backend.md:66), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:578)  
  **Quote**: Plan uses `insert(...)` / `hasSubmitted(...): Int`; API contract uses `upsert(...): Long` / `Flow<Boolean>`.  
  **Why**: Data implementation and tests will not compile against the canonical DAO/repository contract. Actual `LessonRatingRepository` also returns `Flow<Boolean>`.  
  **Suggested fix**: Align Phase-02/03 with `06-api-contract.md`: `upsert`, `OnConflictStrategy.REPLACE`, `hasSubmitted(): Flow<Boolean>`, and update all tests/fakes.

- **Severity**: BLOCKER  
  **File:line**: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/06-api-contract.md:463), [phase-03/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-03/backend.md:132)  
  **Quote**: “`class DefaultAttemptIdProvider : AttemptIdProvider`”  
  **Why**: `AttemptIdProvider`, `RandomSeedProvider`, and `RatingIdProvider` are required by ADR/API, but no phase creates the domain provider interfaces. The workspace currently has no `domain/provider/` files.  
  **Suggested fix**: Add provider interfaces as explicit Phase-01/03 new files with Signature Cards, or change ADR-LR-09/API to avoid wrapper interfaces.

- **Severity**: HIGH  
  **File:line**: [phase-05/frontend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-05/frontend.md:28), [phase-04/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-04/backend.md:93)  
  **Quote**: `state is RunnerUiState.Result && ???`  
  **Why**: HARD result screen cannot reliably keep `FLAG_SECURE` because `RunnerUiState.Result` has no explicit `isHard`/`mode` field in the plan. This can violate AC-28/29.  
  **Suggested fix**: Specify `state.attempt.mode == Difficulty.HARD` or add `isHard` to `RunnerUiState.Result`; update API contract and CT coverage.

- **Severity**: HIGH  
  **File:line**: [README.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/README.md:61), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1121)  
  **Quote**: “AC-55..65 … verify in `0-spec.md`”  
  **Why**: AC-55..65 are real acceptance criteria, not leftovers. AC-56 is actively contradicted by Phase-04/06 import plans.  
  **Suggested fix**: Expand the README AC map for AC-55..65 and add explicit validation greps/build gates per AC.

## Lens 2 — Plan As ТЗ Findings

- **Severity**: BLOCKER  
  **File:line**: [phase-06/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/overview.md:4)  
  **Quote**: `tag: complex`  
  **Why**: Phase-06 is marked complex and touches multiple modules, but has no “Options Considered” section. This violates the plan lens for complex phases.  
  **Suggested fix**: Add options for the integration strategy, especially how `QuizzesScreen` renders lesson-runner without sibling presentation imports.

- **Severity**: HIGH  
  **File:line**: [phase-04/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-04/backend.md:42), [planner.md](/home/Programming/Android/schoolquiz4.0/.claude/agents/planner.md:66)  
  **Quote**: `Canonical reference: 06-api-contract.md §LR-9`  
  **Why**: Planner requires concrete `06-api-contract.md:NN` line refs. Most public type references use section labels, making executor drift likely.  
  **Suggested fix**: Replace all public canonical refs with line references, e.g. `06-api-contract.md:284`.

- **Severity**: HIGH  
  **File:line**: [phase-06/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/backend.md:8), [plan-review-lens.md](/home/Programming/Android/schoolquiz4.0/.claude/skills/adversarial-review/references/plan-review-lens.md:80)  
  **Quote**: Pattern invariants list has no `file:line` references.  
  **Why**: The lens requires invariants to point to existing project patterns. Several phase files state rules without verifiable examples.  
  **Suggested fix**: Add concrete refs such as `DefaultQuizzesComponent.kt:117`, `DefaultRootComponent.kt:113`, `PersistenceModule.kt:24`.

- **Severity**: HIGH  
  **File:line**: [phase-06/overview.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-06/overview.md:103)  
  **Quote**: `PT-15: no attempts → LessonItemUi(...)`  
  **Why**: “Tests Required” in overview files are not consistently given/when/then. The tests files are better, but the phase overview still violates the ТЗ lens.  
  **Suggested fix**: Rewrite overview test bullets as `test_name: given X, when Y, then Z`.

- **Severity**: MEDIUM  
  **File:line**: [phase-04/backend.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/phase-04/backend.md:49)  
  **Quote**: `LessonRunnerComponentFactory.kt` canonical ref is ADR-only.  
  **Why**: This is a public cross-module type, but it has no concrete `06-api-contract.md` entry/line.  
  **Suggested fix**: Add `LessonRunnerComponentFactory` to `06-api-contract.md` or mark it internal only if it stops crossing module boundaries.

## Cross-cutting Risks

- The plan repeatedly treats “raise as Open Question” as acceptable inside implementation phases. For Phase-04/06 dependency boundaries, those are not handoff questions; they are blockers.
- README synchronization is off: [README.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/plan/README.md:69) says “New Gradle Modules (2)” but lists three modules.
- Several quizzes-screen paths use `.../ui/...`, while the existing code uses `.../screen/...`; following the plan literally could create duplicate wrong-path files.

## Things Done Well

- The exact fenced-code grep returned no Kotlin/Java/Groovy matches.
- Phase-01 correctly preserves Walking Skeleton domain scope except the documented rename/import moves.
- Phase-02 does address destructive migration removal.
- Phase-07 Koin order is correct: `questionSchemaModule → lessonRunnerDataModule → lessonRunnerDomainKoinAdapter → lessonRunnerPresentationModule`.