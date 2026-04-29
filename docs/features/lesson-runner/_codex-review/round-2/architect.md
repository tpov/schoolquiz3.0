# Architect Review (Round 2) — lesson-runner design

## Verdict
REJECT

## Round 1 blockers verification
- C1 ⚠ PARTIAL — `06` is fixed: provider interfaces are in domain at `06-api-contract.md:453-468`, adapter is in data at `06-api-contract.md:475-479`. But `01` still contradicts it: `01-architecture.md:653` says ``lessonRunnerDomainModule`` and `01-architecture.md:658-659` still resolves providers via `get()`. Actual source also still has `lessonRunnerDomainModule` with `randomSeedProvider = get()` at `LessonRunnerDomainModule.kt:17-25`.
- C2 ✓ FIXED — ADR-LR-09 now references `06`: `03-decisions.md:386` “Canonical interface definitions — `06-api-contract.md §LR-13a`”; no provider interface bodies remain in ADR-LR-09.
- C3 ✓ FIXED — constructor names/method refs match: `06-api-contract.md:493-512` uses `attemptRepository`, `ratingRepository`, `::next`, `::provide`; verified summary at `06-api-contract.md:519-524`.
- C4 ⚠ PARTIAL — exposure pattern matches (`01-architecture.md:393-394`, `06-api-contract.md:290-291`), but API methods do not. `01` still has `onSingleChoiceAnswer`, `onPauseDialogResume`, `onRatingSelected` at `01-architecture.md:395-409`; `06` has `onAnswer`, `onContinue`, `onSubmitRating` at `06-api-contract.md:293-301`.
- C5 ✓ FIXED — private channel/public flow: `07-events.md:37-38` “`private val _events = Channel<RunnerEvent>`” and “`override val events: Flow<RunnerEvent> = _events.receiveAsFlow()`”.
- C6 ✓ FIXED — ADR-LR-08 names `@SerialName("SingleChoice")`, `"MultipleChoice"`, `"Ordering"`, `"FillBlank"` at `03-decisions.md:362-363`; ADR-0003 defines those variants at `0003-question-schema.md:33,44,55,65`.
- C7 ✓ FIXED — impact scan is closed: `08-storage-model.md:151` says all 4 `LessonDao` queries safe; `08-storage-model.md:165` says all 4 queries are safe. No `REQUIRES` remains in `08-storage-model.md`.
- C8 ✓ FIXED — row-level tests are present: `04-testing.md:213` covers all 7 existing tables; `04-testing.md:215` adds MT-06 for production `fallbackToDestructiveMigration` removal.
- C9 ⚠ PARTIAL — `04-testing.md:18` uses `shared/core/persistence/src/androidInstrumentedTest/`, but `04-testing.md:205` still says `shared/core/persistence/src/androidTest/`; KMP data row also still says `shared/feature/lesson-runner/data/src/androidTest/` at `04-testing.md:14`.
- C10 ✓ FIXED — existing fakes are explicitly referenced as existing at `04-testing.md:36-45`; only presentation fakes are marked new at `04-testing.md:47`.
- C11 ✗ STILL WRONG — IT-09 does not cover parser/converters. `04-testing.md:195-199` only covers data repos, providers, domain adapter use cases, presentation module. `MT-05` only covers `DifficultyConverter` at `04-testing.md:214`; no `TopParticipantListConverter` or parser wiring test.
- C12 ✓ FIXED — CT-29 exists: `04-testing.md:179` “FLAG_SECURE rotation... remains set”.
- C13 ✓ FIXED — ADR-LR-12 test churn is explicit: `03-decisions.md:475-490` lists impacted files/tests and compile gate.
- C14 ✓ FIXED — risk mitigations are present and positive “zero risk” claim is gone; `03-decisions.md:427-430` lists mitigations and says the zero-risk claim was removed.
- C15 ✓ FIXED — LR-08..LR-15 each have `Risk if wrong`: `03-decisions.md:370,410,435,460,495,515,542,565`.

## NEW issues introduced in round 2
- Parser DI location drift: ADR-LR-08 says parser lives in `shared/core/question-schema` and binds in `questionSchemaModule` (`03-decisions.md:357,364`), but `06` still binds `KotlinxSerializationQuestionContentParser` inside `lessonRunnerDataModule` (`06-api-contract.md:443-444`).
- Test ranges are stale: `04-testing.md:26-28` still says `CT-01..CT-28` and `MT-01..MT-05`, but CT-29 and MT-06 now exist.
- `LessonItemUi` drift: `06-api-contract.md:412-419` uses `orderLabel: String` and `bestStarsRawTenths`; `01-architecture.md:581-589` uses nullable `orderLabel`, `subtitleCount`, and `bestStarsTenths`; `03-decisions.md:454` has another signature shape.

## SSoT spot-check
- `AttemptIdProvider` ✗ — canonical body in `06-api-contract.md:458-460`, duplicated as full diagram body in `01-architecture.md:319-330`.
- `LessonRunnerRootComponent` ✗ — canonical body in `06-api-contract.md:289-302`, incompatible full body in `01-architecture.md:391-410`.
- `RunnerUiState` ✗ — canonical body in `06-api-contract.md:324-351`, duplicated in `01-architecture.md:432-458`.
- `LessonItemUi` ✗ — canonical body in `06-api-contract.md:412-419`, duplicated/drifted in `01-architecture.md:581-589` and `03-decisions.md:454`.
- `RunnerEvent` ✓ — full event body is in `07-events.md:20-23`; other docs only reference it or expose `Flow<RunnerEvent>`.

## Recommendation
fix-loop. Focus the loop on deleting/stubbing the stale `01` API/DI bodies, aligning parser DI ownership, fixing IT-09 parser/converter coverage, and normalizing KMP instrumented test paths/ranges.