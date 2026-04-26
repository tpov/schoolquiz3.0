# Architect Pass-2

## Verdict
PARTIAL

## Per-finding

### #1 — FIXED
Evidence: §15 rebuilt as `AC#1` through `AC#39`, aligned to spec numbering: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:474), spec AC#1-39: [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:471).

### #2 — FIXED
Evidence: `SER-02` now uses `QuestList(catalogId, titles)`, no `catalogName`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:185). `SER-06` includes `lessonId`, `lessonTitle`, `titles`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:189). Matches canonical §10: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:345).

### #3 — FIXED
Evidence: LessonPlaceholder tests are only `uiState.lessonTitle` and `uiState.titles`; no `lessonId` exposure or `onBack`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:161). Canonical interface exposes only `uiState`: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:608).

### #4 — FIXED
Evidence: testing strategy separates design-system primitive-param composables from feature screens backed by Decompose `MutableValue`, while allowing `StateFlow` only for Home/MyQuests: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:18).

### #5 — FIXED
Evidence: process-death coverage is now classified as JVM `StateKeeperDispatcher` restore contract, and `ActivityScenario.recreate()` is explicitly rotation/configuration-change only: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:364), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:299).

### #6 — FIXED
Evidence: missing AC tests were added: AC#11 `MY-UI-01`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:289), AC#17 `SL-U-06/07`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:124), AC#20 `QL-U-09`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:99), AC#22 `ROT-UI-01/02`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:299), AC#23 `QL-U-10/SL-U-07`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:105), AC#13 `QC-UI-02`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:271).

### #7 — PARTIAL
Evidence: main fake snippet now declares `class FakeStackNavigation : StackNavigation<QuizzesConfig>`: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:543). But the same document still says `StackNavigation` is a concrete class and fake may be impossible: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:559), [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:612). Local Decompose 3.1.0 source shows `StackNavigation` is an interface, so that OQ is stale.

### #8 — FIXED
Evidence: `SER-10` and `SER-11` added for missing required field / unknown discriminator fallback, with wrapper guidance: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:199).

## New issues

- Stale contradiction: `OQ-04-02` must be removed or rewritten. It now conflicts with the fixed fake strategy and local Decompose source.
- Minor stale wording: Open Questions still use old `AC-22` / `AC-03` formatting outside §15: [04-testing.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/04-testing.md:613).