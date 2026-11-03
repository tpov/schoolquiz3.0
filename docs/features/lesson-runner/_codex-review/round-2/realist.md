# Realist Review (Round 2) — lesson-runner design

## Verdict
CONTESTED — most round-1 fixes landed, but Matrix 3 wording and 01/06 component API drift are still not fully corrected.

## Round 1 fixes verified

1. ✓ FIXED — Target notice exists: `docs/features/lesson-runner/01-architecture.md:11`; module table also marks pending modules as nonexistent: `01-architecture.md:242-246`.

2. ✓ FIXED — `submitAnswer` now receives `UserAnswer`, with draft mapping before call: `02-behavior.md:367-371`; actual signature is `RunnerLogic.kt:25`, while draft is separate at `UserAnswerDraft.kt:12`.

3. ✓ FIXED — No component-side timer recompute in Sequence 2: `02-behavior.md:371-376`; actual `submitAnswer` computes `newDeadlineMs`: `RunnerLogic.kt:34-37`.

4. ✓ FIXED — `SaveError.IoFailure` is used: `02-behavior.md:385-386`; actual variants: `SaveError.kt:3-5`.

5. ✓ FIXED — Ordering edge now respects `items.size in 2..8`: `02-behavior.md:505`; actual invariant: `QuestionContent.kt:73-84`.

6. ✓ FIXED — Matrix 2 includes EASY 75 and HARD 50: `02-behavior.md:515`, `02-behavior.md:518`; actual formula: `RunnerLogic.kt:108-113`.

7. ⚠ PARTIAL — Matrix 3 now allows `rawTenths=20` without unlock, but wording says “при 100% без allShown9=true”: `02-behavior.md:527`. Actual `allShownAnswersAre9` would be true for a 100% code answer: `CodeAnswer.kt:20-21`; rounding-to-20 is possible below 100 via `computeStars`: `RunnerLogic.kt:108-113`.

8. ✓ FIXED — Requested line refs are updated: `StartLessonAttemptUseCase.kt:36` in `02-behavior.md:348`; `RunnerLogic.kt:154/136/25` in `02-behavior.md:349-350`, `02-behavior.md:393`; EmptyPool `63-65` in `02-behavior.md:572`.

9. ✓ FIXED — Constructor names now match source: docs use `attemptRepository`/`ratingRepository` at `06-api-contract.md:495-496`, `06-api-contract.md:503`, `06-api-contract.md:510`; actual constructors: `CompleteAttemptUseCase.kt:22-26`, `AbortAttemptUseCase.kt:20-23`, `SubmitLessonRatingUseCase.kt:19-24`.

10. ✓ FIXED — `RatingIdProvider` is `(userId, lessonId)`, not `.next()`: `06-api-contract.md:466-468`, wired via `::provide` at `06-api-contract.md:512`; actual use case requires `(String, LessonId) -> RatingId`: `SubmitLessonRatingUseCase.kt:23`.

11. ⚠ PARTIAL — StateFlow part is synced: `01-architecture.md:393`, `06-api-contract.md:290`. But method API is still not synced: 01 lists per-type callbacks at `01-architecture.md:395-409`, while 06 canonical uses generic `onAnswer`/`onTimeout` etc. at `06-api-contract.md:293-301`. Also the cited existing root precedent is actually public `Flow`, not `StateFlow`: `RootComponent.kt:21`, `DefaultRootComponent.kt:94`.

12. ✓ FIXED — `ratingCount: Int = 0` is in the contract: `06-api-contract.md:137`, `06-api-contract.md:155`; spec amendment confirms it: `0-spec.md:187`. Current `Lesson` lacks the field, but 01 marks it as phase-01 target: `01-architecture.md:15`, actual current model `Lesson.kt:15-45`.

## NEW high/blocker findings
- None beyond the partial round-1 fixes above.

## Medium findings
- Matrix 3 still contains a false edge-case phrase: `02-behavior.md:527` says 100% can be without `allShown9=true`; actual `CodeAnswer.allShownAnswersAre9` makes that impossible for a 100% code answer: `CodeAnswer.kt:20-21`, `RunnerLogic.kt:183-187`.

- 01 and 06 still disagree on `LessonRunnerRootComponent` method surface: per-type callbacks in `01-architecture.md:395-409` vs generic callbacks in `06-api-contract.md:293-301`.

- 06 cites `DefaultRootComponent` as a `StateFlow` public API precedent: `06-api-contract.md:314`; actual public API is `Flow<AppShellState>` at `RootComponent.kt:21` and `DefaultRootComponent.kt:94`.

- 01’s Room/data diagram conflicts with 06 canonical schema: `01-architecture.md:263-290` uses `id`, `mode`, `ratedAt`, `insertOrIgnore`, `observeHasSubmitted`; 06 uses `attemptId`, `isHard`, `submittedAt`, `upsert`, `hasSubmitted` at `06-api-contract.md:606-615`, `06-api-contract.md:587-591`.

## Spot-check matrix

| Design doc:section | Claim | Actual file:line | Verdict |
|---|---|---|---|
| `02 DFD2` | Start flow handles auth/lesson missing | `StartLessonAttemptUseCase.kt:36-41` | PASS |
| `02 DFD2` | Invalid payloads filtered; no-valid vs empty-pool split | `StartLessonAttemptUseCase.kt:46-65` | PASS |
| `02 DFD3 / Matrix 4` | Abort fills unanswered subset with `1`, out-of-subset stays `0` | `RunnerLogic.kt:169-174`, `StartLessonAttemptUseCase.kt:80` | PASS |
| `02 DFD4` | Submit rating uses attempt userId, not auth reread | `SubmitLessonRatingUseCase.kt:25-37` | PASS |
| `02 Matrix 5` | Rating prompt only on all-shown-9 and not submitted | `CompleteAttemptUseCase.kt:51-54` | PASS |
| `02 Matrix 7` | Timer floor + image bonus formula | `RunnerLogic.kt:136-147`, `RunnerLogic.kt:202-211` | PASS |
| `02 Matrix 8` | `codeAnswer.length == eligibleSize`, not subset size | `StartLessonAttemptUseCase.kt:69-80`, `RunnerState.kt:32-33` | PASS |
| `06 §LR-14` | Submit rating constructor order/params | `SubmitLessonRatingUseCase.kt:19-24` | PASS |

I did not rerun Gradle because this session is read-only; verification above is source/doc based, with existing test XML showing the lesson-runner JVM test run was green.