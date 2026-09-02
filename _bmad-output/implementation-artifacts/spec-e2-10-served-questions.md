---
title: 'E2.10 — The client says which questions it was shown'
type: 'feature'
created: '2026-09-02'
status: 'done'
baseline_commit: '2f7f1ec1'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The server-side scorer (commit `fcacee8d`) can only be honest if it is told which questions were put to the player — a codeAnswer `'0'` means "not shown" and is excluded from the percent, and inferring "not shown" from "not submitted" let a client score 100% by staying silent about its wrong answers. Today the attempt the device sends carries only its answers; the set of questions it was shown exists on the device at completion and is thrown away.

**Approach:** The attempt body the device queues gains the list of served questions — id and position — for both a completed and an abandoned run. The server ignores the field for now; nothing about scoring, storage or the digit string changes. This is the one input the wiring step needs from the client, landed before that step so it is already in every queued attempt when the server starts reading it.

## Boundaries & Constraints

**Always:** For every attempt the device queues, the served positions are exactly the positions whose codeAnswer digit is not `'0'` — the same fact stated twice, and the tests must say so for both the completed and the abandoned path. Every served entry names a question from the attempt's pool, positions are unique, and the list is sorted by position. No schema migration: the field lives inside the outbox body, which is already JSON.

**Ask First:** Anything that changes how the codeAnswer is built, what `'0'` means, or the attempt row in the local database.

**Never:** Do not touch the server, the scoring path, the outbox engine, `LessonAttemptEntity`, or the legacy `LessonResultAttemptEvent` / `submitAttempts` path (it has no live caller — recorded separately). Do not add the field to the persisted attempt row.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Completed run | every question in the play order answered | `served` lists every play-order question with its position; equals the non-`'0'` positions |
| Abandoned run | player quits after some questions | `served` lists the **whole** play order — the abort path already writes `'1'` for the unreached ones, so they count as shown; equals the non-`'0'` positions |
| Subset of a larger pool | pool of 30, play order of 20 | 20 entries; the 10 positions outside the play order are absent and are `'0'` in the codeAnswer |
| Shape | one entry | `{questionId, codeAnswerIndex}`, matching the answer rows' field names |
| Existing consumers | the server, the queue, the attempt row | unchanged — the server's normaliser whitelists fields and drops this one today |
| Fakes | five fakes implement the repository | all keep compiling without edits |

</frozen-after-approval>

## Code Map

- `LessonResultOutboxWriter.kt:54-98` (`shared/feature/lesson-runner/data/.../outbox/`) -- builds the attempt body with `buildJsonObject`: `attemptId, userId, <context>, lessonVersion, difficulty, codeAnswer, percentScore, completedAtMs, createdAtMs, answers[]` where each answer is `{questionId, codeAnswerIndex, score, answerPayload, answeredAtMs, durationMs, wasTimeout}`. `served` goes beside `answers`, same field names for the two it shares. The body is stored in `OutboxEntity.payload` (a `String` column) — no migration.
- `LessonAttemptRepositoryImpl.kt:25-38` -- `save(attempt, answers)` → `outboxWriter.buildAttemptRow(attempt, answerEntities)` inside one Room transaction with the attempt row. The served list has to travel through here to the writer; the attempt row itself must not change.
- `LessonAttemptRepository.kt:23,34` (domain) -- `save(attempt)` and `save(attempt, answers)`, the second with a default body delegating to the first. All five fakes override only the one-argument form, so a third form with a default body keeps them compiling.
- `CompleteAttemptUseCase.kt:24` and `AbortAttemptUseCase.kt:27-30` -- both hold `state: RunnerState.Ready` and call `save(attempt, state.answers)`. `state.playOrder: List<RunnerQuestion.Valid>` carries `sourceId: QuestionId` and `codeAnswerIndex` — the served list is exactly that, on both paths.
- `RunnerLogic.kt:149-154` `buildCodeAnswerOnAbort` -- writes `'1'` for every play-order position from `indexInPool` on, so on abort the whole play order is "shown". That is why served == play order on both paths, and why the Always clause can be tested rather than trusted.
- `AnsweredQuestion.kt:22-23` -- `questionId: QuestionId`, `codeAnswerIndex: Int`; the served entry mirrors these two.
- Server: `functions/index.js:724` dispatches the body to `normalizeLessonResultAttemptEvent` (`:4236`), which builds a new object from named fields — `served` is dropped until the wiring step reads it. `functions/attempt-scoring.js` expects `served: [{codeAnswerIndex, questionId}]`.
- Tests: `LessonAttemptRepositoryImplTest.kt` and `data/src/commonTest/.../outbox/` for the writer; `shared/feature/lesson-runner/domain/src/commonTest` for the use cases (`CodeAnswerConstructionTest`, `AutoAnswerTest`, `TestFixtures.kt`).

## Tasks & Acceptance

**Execution:**
- [x] `shared/feature/lesson-runner/domain/.../model/` -- a small value for a served question: question id and position.
- [x] `LessonAttemptRepository.kt` -- a `save(attempt, answers, served)` form with a default body, so nothing else changes.
- [x] `CompleteAttemptUseCase.kt`, `AbortAttemptUseCase.kt` -- pass the play order as the served list.
- [x] `LessonAttemptRepositoryImpl.kt`, `LessonResultOutboxWriter.kt` -- carry it through and write `served` beside `answers`.
- [x] Tests -- the Matrix rows; and the Always invariant asserted against the actual codeAnswer for a completed and an abandoned run.

**Acceptance Criteria:**
- Given a completed or abandoned attempt, when its outbox body is built, then `served` positions equal the set of non-`'0'` positions in `codeAnswer`, sorted, unique, each id from the play order.
- Given the attempt row and the answer rows, when the attempt is saved, then they are byte-for-byte what they were before this change.
- Given the five repository fakes, when the modules compile, then none needed an edit.

## Verification

**Commands:**
- `./gradlew :shared:feature:lesson-runner:domain:allTests :shared:feature:lesson-runner:data:allTests --no-configuration-cache` -- green, new cases included.
- `./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache` -- green (fakes untouched).
- `./gradlew ciCheck --no-configuration-cache` -- green apart from failures owned by the parallel session.

## Suggested Review Order

**What "served" means, and where it comes from**

- Entry point: the served list is the play order — dealt, not necessarily reached. On abort the unreached positions are already `'1'` in the codeAnswer, so they count as shown; the builder sorts by position and refuses duplicates.
  [`ServedQuestion.kt:38`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/ServedQuestion.kt#L38)

- The abandoned path hands over the same list as the completed one; the receiver is `List<RunnerQuestion.Valid>`, so an `Invalid` with its placeholder index cannot reach the builder.
  [`AbortAttemptUseCase.kt:8`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt#L8)

**Where the body is frozen**

- The last cheap place to check the list against the digits beside it: every position inside the codeAnswer, every answer row present in `served`. A refused body yields `SaveFailed` and no rows at all.
  [`LessonResultOutboxWriter.kt:`](../../shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/outbox/LessonResultOutboxWriter.kt#L)

- The key is written only when a list was supplied — absent means "unknown" to the server, an empty array means "none", and the two must never be confused.
  [`LessonResultOutboxWriter.kt:109`](../../shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/outbox/LessonResultOutboxWriter.kt#L109)

- The body contract, written down where the server will look for it.
  [`0004-sync-contract.md`](../../docs/architecture/0004-sync-contract.md)
