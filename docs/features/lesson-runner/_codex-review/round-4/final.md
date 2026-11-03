# Final Design Review (Round 4) — lesson-runner

## Verdict
REJECT — Round 3 blockers are closed, but one HIGH test-design drift remains.

## Round 3 blocker closure
| ID | Status | Evidence (file:line) |
|---|---|---|
| B1 | ✓ FIXED | `02-behavior.md:385-386`, `02-behavior.md:477-481`, `02-behavior.md:557` use `IoFailure`; no `IOError` remains. |
| B2 | ✓ FIXED | `01-architecture.md:617`; canonical parser binding is `questionSchemaModule` at `06-api-contract.md:449-453`. |
| B3 | ✓ FIXED | Data module is repo/providers only at `01-architecture.md:600-619`; use-case adapter split at `01-architecture.md:622-667`, `06-api-contract.md:483-524`. |
| B4 | ✓ FIXED | All four modules registered in order at `01-architecture.md:689-696` and `06-api-contract.md:559-565`. |
| B5 | ✓ FIXED | `bestStarsRawTenths: Int` at `02-behavior.md:237`; SSoT at `06-api-contract.md:413-421`. |
| B6 | ✓ FIXED | Open questions are marked resolved at `01-architecture.md:703-718`. |
| B7 | ✓ FIXED | Factory boundary aligned to `android/core/navigation` at `01-architecture.md:75` and `03-decisions.md:337-340`. |
| B8 | ✓ FIXED | `TopParticipant` has `@Serializable` at `06-api-contract.md:117-125`; converter uses `.serializer()` at `06-api-contract.md:663-668`. |

## NEW issues (round 4)
- [BLOCKER] None.
- [HIGH] `04-testing.md` fake use-case blueprints are stale against the Walking Skeleton: `FakeCompleteAttemptUseCase` returns `Result<Attempt>` and `FakeAbortAttemptUseCase` returns `Unit` at `04-testing.md:59-70`, but real use cases return `RunnerState` at `CompleteAttemptUseCase.kt:22-28,54` and `AbortAttemptUseCase.kt:20-25,49`. Presentation tests built from this doc will not model production behavior correctly.
- [MEDIUM] `01-architecture.md` presentation Koin snippet omits `clock = get()` at `01-architecture.md:672-684`, while canonical `06-api-contract.md:543-554` includes it.
- [MEDIUM] `01-architecture.md:618` says `lessonRunnerDataModule` resolves `QuestionContentParser`; canonical text says `lessonRunnerDomainKoinAdapter` resolves it via `questionSchemaModule` at `06-api-contract.md:457`.
- [MEDIUM] `01-architecture.md` has stale section references: `RunnerUiState` points to `§LR-12` at `01-architecture.md:482` but SSoT is `§LR-10`; `LessonItemUi` points to `§LR-10` at `01-architecture.md:573` but SSoT is `§LR-12`.

## Cross-doc SSoT spot-check (8 items)
| Type | SSoT location | Other doc reference | Verdict |
|---|---|---|---|
| LessonRunnerRootComponent | `06-api-contract.md:284-315` | `01-architecture.md:379-382`, `03-decisions.md:337-342` | PASS |
| LessonItemUi | `06-api-contract.md:408-421` | `02-behavior.md:237`, `03-decisions.md:462-463`, `01-architecture.md:556-573` | PARTIAL: fields align, section refs stale |
| TopParticipant | `06-api-contract.md:112-125` | `03-decisions.md:180-212`, `08-storage-model.md:72` | PASS |
| AttemptIdProvider | `06-api-contract.md:466-468` | `03-decisions.md:392-404` | PASS |
| RandomSeedProvider | `06-api-contract.md:470-472` | `03-decisions.md:392-404` | PASS |
| RatingIdProvider | `06-api-contract.md:474-476` | `03-decisions.md:392-404` | PASS |
| Lesson.ratingCount | `0-spec.md:187`, `06-api-contract.md:138-156` | `08-storage-model.md:64-71`, `03-decisions.md:557-569` | PASS |
| QuestionContent SerialNames/parser | `03-decisions.md:361-364` | `01-architecture.md:363`, binding at `06-api-contract.md:449-453` | PASS |

## Final spot-check matrix (5 fresh items)
| Claim | File:line | Verdict |
|---|---|---|
| DI registration order matches 06 §LR-15 | `01-architecture.md:689-696`, `06-api-contract.md:559-565` | PASS |
| State Matrix rows trace to code location + test ID | `02-behavior.md:501-576` | PASS |
| AC coverage map covers AC 1-65 | `04-testing.md:237-307` | PASS |
| Migration safety: DAO scan + MT-04/06/07 | `08-storage-model.md:144-165`, `04-testing.md:215-218` | PASS |
| KMP test dirs use `androidInstrumentedTest`; Android presentation uses `androidTest` | `04-testing.md:14-18`, `04-testing.md:150`, `04-testing.md:207` | PASS |

## Recommendation
fix-loop-round-4