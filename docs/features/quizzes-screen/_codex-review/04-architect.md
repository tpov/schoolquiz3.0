# Architect Review — 04-testing.md

## Verdict
CONTESTED

## Findings

### Finding [BLOCKER] AC coverage table is misnumbered and does not cover AC#1-39
Evidence: [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:475), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:413)  
Issue: §15 is not aligned with canonical AC numbering. Example: spec AC#11 is “MyQuestsScreen long-press does nothing”, but §15 AC-11 says “Menu содержит Share”. Spec AC#29 is Koin DI registration, but §15 AC-29 says QuizzesConfig serialization. AC#38/39 are build/allTests gates, but §15 maps AC-39 to fake repository behavior.  
Suggested: rebuild §15 directly from `0-spec.md` AC#1-39, preserving exact numbering and adding missing tests/gates.

### Finding [BLOCKER] Tests target non-canonical `QuizzesConfig` signatures
Evidence: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:166), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:183), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:346)  
Issue: `SER-02` and pseudocode use `QuestList(catalogId, catalogName, titles)`, but canonical `QuestList` has only `catalogId` and `titles`. `SER-06` omits `lessonTitle`, while canonical `LessonPlaceholder` requires it.  
Suggested: update serialization tests to mirror §10 exactly: `QuestList(catalogId, titles)` and `LessonPlaceholder(lessonId, lessonTitle, titles)`.

### Finding [HIGH] `LessonPlaceholderComponent` tests contradict API contract
Evidence: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:150), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:611)  
Issue: tests expect `lessonId` exposure and `onBack()`, but canonical component exposes only `uiState: LessonPlaceholderUiState`. Spec AC#35 requires correct title/state, not a back API.  
Suggested: replace LP tests with `uiState.lessonTitle == config.lessonTitle` and `uiState.titles == config.titles`; back belongs to `QuizzesComponent`/screen navigation tests.

### Finding [HIGH] Compose fake strategy uses StateFlow where canonical screens use Decompose `Value`
Evidence: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:18), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:19), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:535), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:517)  
Issue: §1 says Compose screen tests use “Fake state flows”, but quizzes components expose Decompose `Value<...>` and UI reads via `subscribeAsState()`. A `StateFlow` fake will not match the canonical API.  
Suggested: use fake components backed by `MutableValue`, or test pure design-system composables with primitive params. Keep StateFlow fakes only for existing Home/MyQuest components.

### Finding [HIGH] Process death test is a StateKeeper round-trip, not ActivityScenario process death
Evidence: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:304), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:319), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:504)  
Issue: current pseudocode manually calls `StateKeeperDispatcher.save()` and recreates the component. That is useful, but it is not an ActivityScenario process-death test. `ActivityScenario.recreate()` is closer to configuration recreation than OS process death.  
Suggested: classify PD-01..05 as JVM or instrumented “StateKeeper restore contract”. Add a separate host Activity/SavedStateRegistry smoke test only if the real Android integration is in scope.

### Finding [HIGH] Missing tests for several real AC behaviors
Evidence: [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:487), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:497), [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:500), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:409)  
Issue: notable gaps include MyQuests long-press no-op, live Flow re-render with breadcrumb frozen after rename, offline cached drill-down, tap-outside menu close mapped to the wrong AC, and rotation scroll retention.  
Suggested: add explicit test IDs for these behaviors and map them to the original AC numbers.

### Finding [MEDIUM] FakeStackNavigation pseudocode is too weak for canonical constructors
Evidence: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:473), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:478), [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:546)  
Issue: pseudocode implements `StackNavigator`, but canonical constructors accept `StackNavigation<QuizzesConfig>`. Locally, `StackNavigation` is an interface, so this is fixable, but the fake must implement the full required type or constructors should narrow to `StackNavigator`.  
Suggested: either change child constructors to depend on `StackNavigator<QuizzesConfig>` or define `FakeStackNavigation : StackNavigation<QuizzesConfig>`.

### Finding [MEDIUM] Schema evolution/unknown variant is not covered
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:94), [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:97), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:161)  
Issue: §8 only tests happy-path round-trips. ADR-QS-02 documents breaking restore on unknown/renamed variants and a mitigation, but no test seam exists in the plan.  
Suggested: add serializer compatibility tests with crafted payloads for missing defaulted fields and unknown discriminator. If fallback-to-Idle is required, introduce a testable restore wrapper instead of relying directly on `childStack(serializer = ...)`.

## Strong points

- The plan correctly bans Turbine and uses `.take().toList()` / `.value`, matching project rules: [testing.md](/home/Programming/Android/schoolquiz4.0/.claude/rules/testing.md:39).
- The three `FakeQuestRepository` copies are named in §11.1, and `FakeQuestLocalDataSource` is at least called out by RX-04.
- BackCallback priority is recognized as a JVM-testable concern via fake/default back dispatcher, which matches ADR-QS-12’s manual callback design.

## Final verdict

CONTESTED. The strategy has a solid skeleton, but it cannot be treated as coverage-complete or implementation-ready until §15 is rebuilt against the real AC#1-39 and the tests are corrected to the canonical APIs in `06-api-contract.md`.