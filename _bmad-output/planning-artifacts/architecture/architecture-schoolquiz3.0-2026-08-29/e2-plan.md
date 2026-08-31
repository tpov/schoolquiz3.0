# E2 — Hard answers stop reaching the device

**Gate for every step:** `./gradlew ciCheck --no-configuration-cache` **and** `cd functions && npm test`. Note up front: nothing wires the second into the first — `build.gradle.kts:26-34` (`ciCheck`) does not touch `functions/`, and `functions/package.json:9` is a hand-maintained file list not referenced by any predeploy hook (`"lint"` at `:8` is the only predeploy). Step 1 fixes that or the "shared fixture set run on both sides in CI" of AD-7 has no harness.

---

## Step 1 — `functions/assessment-scoring.js`, the one JS scorer, with a cross-language fixture set

**Changes.** New `functions/assessment-scoring.js` mirroring `shared/core/scoring/.../Scoring.kt` exactly: `evaluateAnswer(content, answer)` over all five ADR-0003 shapes, `computePercentScore`, `computeStars`, plus `isWellFormedCodeAnswer` and `attemptActivityCounts` absorbed from `result-verification.js:19-47`. `result-verification.js` becomes a re-export shim (or is deleted and `index.js`'s three call sites repointed — `index.js:3009`, `:3019`, `:406`). Nothing calls the new `evaluateAnswer` yet.

Integer-math traps to mirror literally: `scoreDigit` is `(num*8 + den/2)/den + 1` with Kotlin **Int** division (`Scoring.kt:92-95`) — JS needs `Math.floor` at both steps, exactly as `result-verification.js:24-25` already does for percent, and `computeStars`'s `(p*20+50)/100` / `20 + (p*10+50)/100` (`Scoring.kt:70-73`) likewise. AD-37 exists because this is where Kotlin and JS drift.

New `functions/fixtures/scoring-fixtures.json`: content + answer + expected digit, covering all five types, the `else -> Score(1)` fall-through (`Scoring.kt:59`), the empty-codeAnswer→0 branch (`Scoring.kt:83`), and both star formulas at 0/50/100.

**Files.** `functions/assessment-scoring.js` (new), `functions/assessment-scoring.test.js` (new), `functions/fixtures/scoring-fixtures.json` (new), `functions/result-verification.js`, `functions/index.js`, `functions/package.json` (add both to the `lint` and `test` string lists — they are hardcoded, `:8-9`), `shared/core/scoring/src/commonTest/.../SharedFixtureParityTest.kt` (new, reads the same JSON), root `build.gradle.kts` (add an `Exec` task running `npm test` in `functions/` and hang it off `ciCheck`).

**Verified by.** `npm test` (new file in the list); `./gradlew ciCheck` now green including the functions suite; the parity test fails if either side changes alone.

---

## Step 2 — Server-only answer-key store, written and unread

**Changes.** New pure module `functions/question-redaction.js`: `redact(payloadJson, difficulty)` → `{publicPayload, key}` per type. No `firebase-admin` import, so it is unit-testable like `lesson-reward.js`.

Wire shape — the redacted payload uses a **different `type` discriminator** (`SingleChoiceRedacted`, `MultipleChoiceRedacted`, `OrderingRedacted`, `FillBlankRedacted`), not a `redacted: true` flag beside the old one. `KotlinxSerializationQuestionContentParser.kt:12` configures `Json { ignoreUnknownKeys = true }`, so a boolean marker is silently swallowed and a stripped `SingleChoice` would land on the *legacy* branch at `:45-48` and die as `"Unsupported legacy question type: SingleChoice"`. A distinct discriminator makes "redacted decodes as `QuestionContent`" structurally impossible in both directions.

Per type, keep / drop / key:

| Type | Public payload keeps | Drops | `question_keys` holds |
|---|---|---|---|
| SingleChoice | id, difficulty, text, imageUrl, **all options with ids and texts** | `correctOptionId`, `info` | `correctOptionId` |
| MultipleChoice | same | `correctOptionIds` **including its size**, `info` | `correctOptionIds` |
| Ordering | id, difficulty, text, imageUrl, `items` **shuffled and re-id'd** `ri-0..ri-n` | the canonical order, `info` | `itemOrder` (canonical ids, author's order) + `idMap` |
| FillBlank | id, difficulty, text, imageUrl, `blanks` as bare ids **in original order**, `candidates` shuffled and re-id'd `rc-0..`, `protectedTextSegments` | each `blanks[].correctCandidateId`, `info` | `blankToCandidate` + `idMap` |
| Survey | — | — | — (no answer; see open question) |

Three non-obvious constraints, each with a live reader:
- `difficulty` **must stay inside the payload**. `parseQuestionPayload` (`index.js:1236-1244`) merges `{...fallback, ...parsed}` and its `data.difficulty` fallback is always `""` for a published doc — `publicDocuments` never writes the field (`index.js:2344-2355`). Lose it and `lessonAllocatedSeconds` (`lesson-reward.js:162-168`) defaults everything to EASY, zeroing every hard reward and collapsing the `unlockLesson` price (`index.js:1255-1260`).
- **Option / item / candidate texts must survive.** `questionCharsCount` (`lesson-reward.js:137-147`) sums them; it is the reward's size factor and the client timer's basis (`RunnerLogic.kt:120-127`).
- **Ids must be reissued, not just shuffled.** `DefaultQuestCreateComponent.kt:1147` names ordering items `ord-$index` in the *correct* order and `:1173-1183` names the correct fill-blank candidates `cand-0..cand-(n-1)`, positionally first. Shuffling the array while keeping the ids leaks the answer to a lexicographic sort.

Publication side: in the same batch as `index.js:2105`, `publicDocuments` (`index.js:2343-2355`) additionally writes `question_keys/{questionId}` and adds `difficulty` and `type` as fields on the public question document. **The payload is still written verbatim in this step.** `firestore.rules` gets no `match /question_keys/{id}` — default deny — plus a comment saying so deliberately, beside the `allow read: if true` on `questions` at `:133`.

**Files.** `functions/question-redaction.js`, `functions/question-redaction.test.js`, `functions/index.js` (`publicDocuments`, ~`:2343`), `functions/package.json`, `firestore.rules`.

**Verified by.** `npm test`; a publish through the emulator produces `question_keys/{id}` rows and a `difficulty` field, with `questions/{id}.payload` byte-identical to before. Client untouched, `ciCheck` unaffected.

---

## Step 3 — `difficulty` reaches the client as a document field

**Changes.** `QuestionDto` (`shared/feature/question/data/.../dto/QuestionDto.kt`), `FirestoreQuestionDtoMapper.kt:11`, `QuestionEntity` (`shared/core/persistence/.../QuestionEntity.kt:25-35`) and the `Question` domain model gain a nullable `difficulty`. `Migration3to4` + checked-in `schemas/4.json` — the DB is at `version = 3` (`AppDatabase.kt:35`) with hand-written migrations under `src/androidMain/.../migrations/`, and a jvmTest forbids `fallbackToDestructiveMigration` in production androidMain.

`StartLessonAttemptUseCase.kt:76` still filters on parsed content; the column is the fallback that turns "payload would not parse" from an invisible `InitFailed(EmptyPool)` (`:77-79`) into something diagnosable.

**Files.** `QuestionDto.kt`, `FirestoreQuestionDtoMapper.kt`, `QuestionEntity.kt`, `shared/core/persistence/src/androidMain/.../migrations/Migration4to5.kt`… (`Migration3to4`), `PersistenceModule.kt` (`.addMigrations(...)`), `schemas/4.json`, the question repository mapper.

**Verified by.** `ciCheck`; new DAO instrumented/JVM test that an old row reads back with `difficulty == null`, a new row round-trips.

---

## Step 4 — `RedactedQuestionContent` and `QuestionDisplay` in `shared/core/question-schema`

**Changes.** New sealed `RedactedQuestionContent` (four variants — no Survey), mirroring the wire shape above. It is **not** a `QuestionContent` subtype: `QuestionContent`'s invariants stay exactly as written (`QuestionContent.kt:84`, `:110-114`, `:165-167`), per the spine's Consistency Conventions.

New supertype in the same module, implemented by both:

```kotlin
interface QuestionDisplay {
    val id: String
    val difficulty: Difficulty
    val text: String
    val imageUrl: String?
    val info: String?          // always null on redacted
    val displayTexts: List<String>   // option / item / candidate texts, for the timer
}
```

`displayTexts` is legitimately a display concern and it is what makes `computeCharsCount` (`RunnerLogic.kt:160-170`) provably match `questionCharsCount` (`lesson-reward.js:141-147`), which already reads `options ?? items ?? candidates` uniformly.

`QuestionContentParser` (`QuestionContentParser.kt:9-25`) gains `parseAny(payload, fallbackId, fallbackText, fallbackDifficulty): Result<QuestionDisplay>`. Existing `parse` is untouched and keeps returning `Result<QuestionContent>` — authoring (`SaveDraftQuestionUseCase.kt:22`, `DefaultQuestCreateComponent.kt:1264`, `DefaultReviewQueueComponent.kt:453`) must never see a redacted payload and its signature says so.

Nothing consumes `parseAny` yet.

**Files.** `shared/core/question-schema/src/commonMain/.../RedactedQuestionContent.kt` (new), `QuestionDisplay.kt` (new), `QuestionContent.kt` (implement the interface, add `displayTexts`), `QuestionContentParser.kt`, `KotlinxSerializationQuestionContentParser.kt`, commonTest.

**Verified by.** `ciCheck`; new tests: each redacted variant round-trips; `parse()` on a redacted payload returns `Result.failure` (unknown discriminator, not the legacy branch); `parseAny` on a full payload still yields the full type; a `RedactedQuestionContent` cannot be passed to `evaluateAnswer` (compile-level, asserted by the code not existing).

---

## Step 5 — The runner carries redacted content and refuses to score it

**Changes.** `RunnerQuestion.Valid.content` (`RunnerQuestion.kt:20`) widens from `QuestionContent` to `QuestionDisplay`. This is the forcing function: `RunnerLogic.kt:37` (`evaluateAnswer`), `RunnerLogic.kt:162-170` (`computeCharsCount`), `RunnerLogic.kt:175`/`:200+` (`generateRandomAnswer` / `generateTimeoutAnswer`) and `RunnerStateMapper.kt:49` all stop compiling and each must be answered explicitly.

- `submitAnswer` (`RunnerLogic.kt:30-82`) takes the digit from a new `scoreOrNull(content, answer): Score?` — `null` for redacted. When null it records the `AnsweredQuestion` and leaves the `codeAnswer` position at `'0'`… **no**: `'0'` already means "not shown" (`CodeAnswer.kt`, `Scoring.kt:82-85`), so a `'0'` there produces a self-consistent, server-verified 0%. Redacted positions are written into a **separate** `unscoredIndices` set on `RunnerState.Ready`, and the codeAnswer string is not touched.
- `StartLessonAttemptUseCase.kt:54-68` uses `parseAny`. **In this step a redacted question is still refused**, but by a named `InitFailureReason.RedactedNotSupported`, not by `mapNotNull` silently dropping it.
- `RunnerStateMapper.kt` gains a redacted branch per type; `correctOptionId` (`:62`), `correctIds` (`:95`), `correctOrderIds` (`:118`), `correctCandidateIdsByBlankIndex` (`:144-145`) are simply not populated — `QuestionUiState.kt:14,24,44,55` already default them.
- `LessonRunnerScreen.kt:351` — `hintEnabled` gains `&& state.revealCorrect`. Today it is `livesAvailable && feedback == null && qState !is Survey`, with no difficulty term, and the handlers at `:404/:437/:501/:578` play the correct answer out of the UI state *after* `component.hintRequested()` has already spent a heart.

**Files.** `RunnerQuestion.kt`, `RunnerLogic.kt`, `StartLessonAttemptUseCase.kt`, `RunnerState.kt`, `InitFailureReason.kt`, `RunnerStateMapper.kt`, `LessonRunnerScreen.kt`, `HintAnswer.kt`, both `FakeQuestionContentParser`s, `TestFixtures.kt`.

**Verified by.** `ciCheck`; new `RunnerStateMapper` test file (there is none in that module today) asserting each redacted type maps to a `QuestionUiState` with no correct answer; a test that the hint is disabled and no heart is spent when `revealCorrect` is false. All existing tests stay green — nothing is redacted yet.

---

## Step 6 — Fix the pre-existing hard-play dead-end

**Changes.** Verified at HEAD, not a consequence of E2: `FeedbackOverlay` returns early on `feedbackDigit == null` (`LessonRunnerScreen.kt:613`), its tap layer is the **only** caller of `submitFeedbackNow` (`:306` → `:246-251`), and that is the only call site of `component.onAnswer` in the file (`:250`; the only other hit is the preview no-op at `:900`). `revealDigit()` returns null whenever `revealCorrect` is false (`AnswerFeedback.kt:73`), and `revealCorrect` is false for every HARD question (`SessionMode.kt:30-31`). The `onTimeout` fallback is guarded by `if (feedback == null)` (`:276`) and cannot fire once feedback is set. **A hard lesson currently stalls on question one.**

Render the acknowledgement layer whenever `feedback != null`, with the verdict banner conditional on the digit — E10's "deliberately identical acknowledgement", arriving here because without it none of steps 7-9 can be exercised.

**Files.** `LessonRunnerScreen.kt` (`FeedbackOverlay`, `QuestionStateContent`).

**Verified by.** `ciCheck`; new `LessonRunnerScreenTest` case: render a hard question, answer it, tap, assert the second question renders. Confirm on a device before treating the diagnosis as settled.

---

## Step 7 — The deferred attempt: a result that has no score yet

**Changes.** `Attempt.codeAnswer` and `Attempt.percentScore` (`Attempt.kt:19-20`) become nullable, with an `init` invariant that null is legal only when `mode == HARD`. Consequences, each a real call site:

- `CompleteAttemptUseCase.kt:30,51` and `AbortAttemptUseCase.kt:27-28` branch on mode: EASY unchanged; HARD builds an `Attempt` with no digits and no percent, and `ratingPrompt = false` (`allShownAnswersAre9` is unknowable).
- `computeBestStars` (`RunnerLogic.kt:98-101`) **skips unscored attempts**. Without this a pending hard attempt reads `computeStars(0, HARD) = Stars(20)` — two stars on the lesson card, before the server has seen anything.
- `computeHardUnlocked` (`RunnerLogic.kt:107-108`) already filters `mode == EASY`; leave it alone (`DefaultLessonListComponent.kt:159-160` depends on it and the SPEC's tightening of it is out of E2's scope).
- `LessonAttemptEntity.kt:23-24` columns become nullable, plus `server_scored_at`. `LessonResultSyncOutboxEntity.kt:30-31` likewise. Migration + `schemas/5.json`.
- `LessonResultOutboxWriter.kt:66-67` writes nulls for a pending hard row.
- `LessonAttemptRepositoryImpl.buildRepetitionStates` (`:47-63`) advances SM-2 from `answered.score`; hard answers have none. **Open question below** — the plan's default is that hard questions do not enter the schedule in E2.
- `RunnerUiState.Result` (`RunnerUiState.kt:35-65`): `percentScore` nullable, `currentAttemptStarsRawTenths`/`bestStarsRawTenths`/`advice` nullable, `questionScores` empty for hard. `DefaultLessonRunnerRootComponent.buildResultUiState` (`:392-429`) branches; `triggerComplete` (`:346-373`) fires `AnalyticsEvent.LessonFinished` without percent/stars for hard (or defers it — open question).
- `ResultContent.kt:320` (the 64sp figure) and `:113-115`/`:572-586` (the reward strip, recomputed on the device from the percent) get a pending treatment. Filling them with zeros is the failure mode: it renders as a genuine 0% run.
- `LessonItemCard.kt:114-119` distinguishes "awaiting score" from zero — the comment two lines above already argues exactly this for ratings.

**Files.** `Attempt.kt`, `CompleteAttemptUseCase.kt`, `AbortAttemptUseCase.kt`, `RunnerLogic.kt`, `LessonAttemptEntity.kt`, `LessonResultSyncOutboxEntity.kt`, migration + `schemas/5.json`, `LessonAttemptRepositoryImpl.kt`, `LessonResultOutboxWriter.kt`, `RunnerUiState.kt`, `DefaultLessonRunnerRootComponent.kt`, `ResultContent.kt`, `LessonItemUi.kt`, `LessonItemCard.kt`, and the fixture set: `FakeAttemptFixtures.kt:12-30`, `FakeCompleteAttemptUseCase.kt:6-10`, `DefaultLessonRunnerRootComponentTest.kt:141-192`, `LessonRunnerScreenTest.kt:69-92`, `LessonSequentialGateTest.kt:101-102`.

**Verified by.** `ciCheck`. New tests: a completed hard attempt has no percent; the result screen renders the pending treatment, not 0%; `questionScores` is empty; a pending hard attempt does not move `bestStarsRawTenths`.

---

## Step 8 — The return channel, and the server becomes the hard scorer

**Changes, client.** `LessonResultRemoteDataSource.submitAttempts` (`LessonResultRemoteDataSource.kt:55`) returns `List<AttemptScoringResult>` instead of `Unit`. `FirebaseLessonResultRemoteDataSource.kt:13-19` parses the callable response it currently `.await()`s and discards. `LessonResultSync.syncAttempts` (`:34-47`) writes each result back into `lesson_attempts` by `attemptId` before `markAttemptsSent` — the batch is up to 50 (`:123`) and mixes easy (nothing to write) and hard.

The runner component observes its own attempt row and swaps the pending result for the scored one. A `SyncScheduler` joins `DefaultLessonRunnerRootComponent`'s constructor (`:55-77` — it has none today) so a finished hard lesson requests a one-off sync; the periodic worker is `1L to TimeUnit.DAYS` (`SyncWorker.kt:21`).

**Changes, server.** `normalizeLessonResultAttemptEvent` (`index.js:2996-3033`) accepts an attempt whose `codeAnswer`/`percentScore` are absent **only** when `difficulty === "HARD"` — the current code hard-rejects both (`:3005`, `:3009`). It carries a `scoringAuthority` of `"client"` or `"server"`. `normalizeLessonAnswers` (`:2982-2994`) stops being permissive for server-scored attempts: today "a malformed row is dropped rather than failing the whole attempt", which inverts from safe to fatal once those rows *are* the score.

The `question_keys` read joins `readAllocatedSeconds` in the pre-transaction slot (`index.js:323-325`, already commented "Firestore wants every read before the first write"), batched by lesson the same way. `assessment-scoring.js` un-maps the reissued Ordering/FillBlank ids **exactly once** (AD-32), scores each answer, assembles the `codeAnswer` and derives the percent, then attaches them to `item.event` before the transaction opens — so `attemptReward`, the `lessonBest` monotone write (`:390-403`), `attemptActivityCounts` (`:406`) and `writeTournamentAttemptToTransaction` (`:410`) all keep working untouched.

`scoreVerified` (`:3029`) is vacuously true for a server-scored attempt. Replace the gate at `:367` with an explicit predicate — `scoreVerified` for client attempts, `scorable` (keys found for every answered question, answer count matches) for server ones. **See open question.**

`submitLessonResultEvents` returns `results: [{attemptId, percentScore, advice}]` alongside `{accepted, reward, lifePoints}` (`:438`). Advice is derived server-side; `resultAdvice` (`ResultAdvice.kt:36-46`) currently counts codeAnswer digits below `'5'` on the client and must not survive for hard.

**Files.** `LessonResultRemoteDataSource.kt`, `FirebaseLessonResultRemoteDataSource.kt`, `LessonResultSync.kt`, `LessonAttemptDao.kt`, `DefaultLessonRunnerRootComponent.kt`, `GetResultAdviceUseCase.kt`, `functions/index.js`, `functions/assessment-scoring.js`, `functions/assessment-scoring.test.js`.

**Verified by.** `npm test` (a synthetic hard attempt with keys scores the same digits the Kotlin fixture set produces); `ciCheck` (sync writes the returned percent back; a mixed batch leaves easy rows alone); emulator round trip.

---

## Step 9 — Flip redaction on at publication

**Changes.** `publicDocuments` (`index.js:2348`) writes `redact(...).publicPayload` when `question.difficulty === "HARD"`, byte-identical passthrough for EASY. It runs **after** `requestWithPublishedQuestions` (`index.js:2123-2143`) merges reviewer translations, so each `{id}__{lang}` variant gets its own key row. `privateDocuments` (`:2217`), `adminDocuments` (`:2263`), `questionToDocument` (`:3381`) and `toAssignmentDto` (`:2409`) stay unredacted — the LOGIC review stage exists to check the key, and `adminLessonSnapshotToTask` (`:2785-2793`) reads those documents back to rebuild the task publication then publishes.

`StartLessonAttemptUseCase` drops `RedactedNotSupported` and admits redacted questions into the hard pool.

**Files.** `functions/index.js`, `StartLessonAttemptUseCase.kt`.

**Verified by.** `npm test`; an emulator publish where `questions/{id}` for a hard question contains no `correctOptionId`/`correctOptionIds`/`correctCandidateId`/`info` and `question_keys/{id}` contains them; a full hard lesson played offline against the emulator and scored on sync; an easy lesson diffed as byte-identical.

---

## Step 10 — Backfill and the seed scripts

**Changes.** `scripts/seed-bulk/seed-bulk-quests.js:199` writes `db.doc('questions/${question.id}')` directly with a full payload, and `scripts/seed-hierarchy.js:90` does the same with the **legacy** `{type:'single-choice', options:[…], correctIndex:2}` dialect. Every crypto-sm / german / business / english-tech question in the database today bypassed `publishSubmissionIfReady` entirely, and `writePublicHierarchyToBatch` is `{merge: true}` (`index.js:2278`), so step 9 changes nothing that already exists.

Both scripts route through `question-redaction.js` and write `question_keys` rows. New one-shot `scripts/redact-existing-questions.js` walks `questions/{id}`, handles both payload dialects, and has a dry-run mode that reports counts before writing. The legacy branch matters: a redactor keyed on the ADR-0003 field names finds no `correctOptionId` in a legacy payload and emits it verbatim (`KotlinxSerializationQuestionContentParser.kt:56-100` still accepts that shape).

**Files.** `scripts/seed-bulk/seed-bulk-quests.js`, `scripts/seed-hierarchy.js`, `scripts/redact-existing-questions.js` (new), `functions/question-redaction.js` (legacy dialect), `functions/question-redaction.test.js`.

**Verified by.** `npm test` with legacy fixtures; dry-run against the emulator reporting zero unredacted hard questions afterwards; a seeded course re-imported and played.

---

# Traps, merged, worst first

1. **The whole live catalog bypassed publication, so step 9 alone redacts nothing that exists.** `seed-bulk-quests.js:199` and `seed-hierarchy.js:90` write `questions/{id}` directly; `index.js:2278` is merge-only and seeded content has no `quest_review_requests` row to re-publish. Without step 10 the epic's done-condition is false on every question in production.
2. **Ordering does not throw, it scores wrong — and shuffling without reissuing ids leaks the answer completely.** `Ordering.init` (`QuestionContent.kt:132-136`) checks only id, text and `items.size in 2..8`; there is no answer field and no id-uniqueness check, so a shuffled payload is a structurally perfect `Ordering`. `Scoring.kt:34` takes `content.items.map { it.id }` *as* the correct order. And `DefaultQuestCreateComponent.kt:1147` names items `ord-$index` in the correct order, so a shuffle that keeps the ids is one lexicographic sort from the answer. Two failures, one type, neither visible to any existing test.
3. **`scoreVerified` gates four unrelated systems and E2 makes it meaningless.** `lifeCharged = isNew && scoreVerified` (`index.js:365-367`) gates the life charge, the skill/nolics reward (`:387-405`), the profile radar's activity ratings (`:406-409`) and the tournament attempt write (`:410-411`). Left alone, hard play silently stops paying and stops appearing in tournaments; naively removed, it silently changes tournament standings.
4. **`else -> Score(1)` is a subject-less `when` and the compiler will never flag it.** `Scoring.kt:59`. Seven `when (content)` sites across the tree are exhaustive and would break the build on a new variant (`RunnerLogic.kt:162`, `:175`; `RunnerStateMapper.kt:49`; `QuestionContentMapping.kt:7`; `DefaultQuestCreateComponent.kt:1264`; `DefaultReviewQueueComponent.kt:459`, `:504`) — which reads as "the compiler has this covered". The one function that decides the score is the one it does not cover. Keep the redacted type out of the hierarchy (step 4) and this branch stays unreachable.
5. **An all-zero codeAnswer is a self-consistent, server-*verified* 0% — and renders as two stars.** `CodeAnswer("0000")` passes its own invariants; `computePercentScore` returns 0 (`Scoring.kt:83`); `recomputePercentScore` returns 0 (`result-verification.js:23`); `scoreVerified` is therefore **true**, the life point is charged and `lessonBest` is written. Then `computeStars(0, HARD) = 20 + 0 = Stars(20)` (`Scoring.kt:72`) — two stars on the lesson card for a run nobody scored. This is why step 5 keeps redacted positions out of the codeAnswer entirely and step 7 makes `computeBestStars` skip them.
6. **Hard lesson play dead-ends at the first answer today, before E2 touches anything.** `LessonRunnerScreen.kt:613` returns before rendering the tap layer whose `onSkip` is the only path to `component.onAnswer` (`:250`, `:306`). Anyone scoping E2 as "add a deferred result state" is scoping against a flow that does not run.
7. **Difficulty lives only inside the payload.** No `difficulty` on `QuestionEntity.kt:25-35`, on `QuestionDto`, or on the published document (`index.js:2344-2355`). A redaction that breaks parsing makes hard questions *invisible* — `mapNotNull` drops them (`StartLessonAttemptUseCase.kt:54`), the filter never learns they existed (`:76`), and the lesson reports `InitFailed(EmptyPool)` (`:77-79`). Server-side the same field drives `lessonAllocatedSeconds` (`lesson-reward.js:166`), so losing it zeroes every hard reward and floors the unlock price.
8. **`info` is a prose answer key and no redaction list mentions it.** Every variant carries it (`QuestionContent.kt:54,77,103,130,153`), it renders to the player (`RunnerStateMapper.kt:55,71,88,115,136`), and authored content restates the correct option almost verbatim — e.g. `scripts/seed-bulk/data/courses/crypto-sm/lessons/1-1-1.js:57-65`, key `'b'`, info = option b in words. Neither the spine's Consistency Conventions nor `exam-protocol.md:48-59` names it.
9. **FillBlank leaks through candidate ordinals, and its `blanks` list cannot be dropped.** `DefaultQuestCreateComponent.kt:1173-1183` pairs `blank-N` with `cand-N` and builds the candidate list correct-answers-first, so stripping `correctCandidateId` leaves the key as `cand-0..cand-(n-1)`. And removing `blanks` wholesale — the natural reading of `exam-protocol.md:57` — kills rendering: `RunnerStateMapper.kt:183-195` pairs `text.split("___")` segments against `blanks[idx]` by index.
10. **Stripping option texts changes money.** `questionCharsCount` (`lesson-reward.js:137-147`) sums `text` + every `options`/`items`/`candidates` entry; it drives the reward's size factor, the `unlockLesson` price (`index.js:1257-1260`) and the client timer (`RunnerLogic.kt:126`). Redaction is field-removal on the key only, never a rebuild of a minimal payload.
11. **MultipleChoice's cardinality is both a leak and a live dependency.** `correctOptionIds.size >= 2` (`QuestionContent.kt:110`); knowing N cuts a 5-option guess space from 31 subsets to 10. But `generateRandomAnswer` (`RunnerLogic.kt:180`) and `generateTimeoutAnswer` (`:226`) both size the auto-answer from that count, and `LessonRunnerScreen.kt:734` repeats it. Publishing it leaks; withholding it changes what a timed-out hard question uploads. There is no option that leaves behaviour unchanged.
12. **The hint spends a life to reveal the answer and has no difficulty gate.** `LessonRunnerScreen.kt:351`; handlers at `:404`, `:437`, `:501`, `:578`; `HintAnswer.kt:14-24`. Post-redaction it either no-ops or auto-submits an empty draft — after taking the heart.
13. **`questionScores` is a dormant AD-6 violation with a comment inviting someone to render it.** Declared at `RunnerUiState.kt:51-55` ("the raw material of the accuracy chart"), populated at `DefaultLessonRunnerRootComponent.kt:423`, read by no composable — only two `@Preview` literals. A per-question verdict list.
14. **A second payload dialect that a field-name redactor passes through untouched.** `parseLegacy` (`KotlinxSerializationQuestionContentParser.kt:56-100`) accepts `{type:'single-choice', options:['a',…], correctIndex: 2}` with no `correctOptionId` and no `difficulty`; `seed-hierarchy.js:67-71` writes exactly that into `questions/{id}`.
15. **A HARD Survey is authorable, redacts to nothing, and scores full marks for any answer.** `Survey` carries `difficulty` like every other variant (`QuestionContent.kt:48`); `Scoring.kt:52-57` gives `Score(9)` for any valid pick. The SPEC and `ARCHITECTURE-SPINE.md:305` exclude surveys from every *draw*; nothing excludes them from hard *lesson* play, which is what E2 changes. No seed builder emits one, so it will not surface in testing.
16. **`answers` is optional and defaults to empty.** `listMaps` returns `[]` for a missing array (`index.js:3020`), and `LessonResultSync.kt:19,64-76` has a nullable `answerDao` returning `emptyList()`. An older client, or any path where the answer rows were pruned, sends digits and no answers — score that server-side and it is a hard 0% with a full charge.
17. **Rebuilding `codeAnswer` server-side needs the client's exact pool arithmetic.** Its length is `eligibleSize` — every question of that difficulty in the lesson, not the 20 played (`StartLessonAttemptUseCase.kt:82-94`, `POOL_SIZE = 20` at `:119`) — and each position is the index after `dedupeTranslatedVariants` (`:123-142`) and a `(order, sourceId)` sort. `'0'` is excluded from the percent denominator, so the width is load-bearing. Today the server gets the width free from the client's string.
18. **The batch is the unit of submission, the result is per attempt.** `syncAttempts` sends 50 and marks all sent all-or-nothing (`LessonResultSync.kt:35-46`); an easy attempt in the same batch must not have a percent written over the one it computed.
19. **Redaction moves the leak rather than sealing it.** `firestore.rules:57` grants `/private/{userId}/{document=**}` and `:64` grants `/admin/**` to `isReviewer()`, satisfied by `translatorLevel >= 100` or `testerLevel >= 100` (`:195-204`) — and `index.js:2217`/`:2263` write the unredacted payloads into exactly those trees. After E2 the complete answer key to every course is a plain SDK read for any qualified reviewer. Out of E2's stated scope; must be named, not discovered.
20. **Two schema changes, two hand-written migrations.** `AppDatabase.kt:35` is at version 3 with `exportSchema = true` and explicit `.addMigrations(Migration1to2, Migration2to3)`; a jvmTest fails the build if `fallbackToDestructiveMigration` appears in production androidMain. Steps 3 and 7 each cost a migration plus a checked-in schema JSON.
21. **A pending hard result held only in `RunnerStateHolder` evaporates on a navigation pop** (`RunnerStateHolder.kt:45` resets to `Loading`). It survives rotation via InstanceKeeper, not a back press — which is exactly what a player waiting for a score will do.
22. **There is a third scoring implementation, in Compose.** `AnswerFeedback.revealDigit()` (`AnswerFeedback.kt:63-113`) reimplements the digit formula in the UI layer, and its null-ness doubles as the "can the player advance" signal (trap 6). AD-7 says one Kotlin implementation; E1 moved the domain one and left this behind.
23. **`maxInstances: 1` on every function, and the file has outgrown its own comment.** `index.js:59-74` justifies the cap for "28 functions"; `grep -c '^exports\.'` returns 38. E2 adds per-answer scoring and a second pre-transaction read to the hot path, and E4/E6 add five more callables on top.

---

# Open — for the owner, not for me

1. **Where does the redacted type live, and does E2 create `shared/core/online-session`?** `ARCHITECTURE-SPINE.md:303` and the Structural Seed put it in `shared/core/online-session`; that module is not in `settings.gradle.kts:26-40` and is E4's to build, and `epic-breakdown.md:22-27` runs E2 and E4 in parallel with no dependency between them. The plan above puts it in `shared/core/question-schema` (hard *lesson* play has no online session) and lets E4 import it. Confirm, or reorder E2 behind E4.
2. **`exam-protocol.md` contradicts the spine, and one of the contradictions is E2's.** It says (`:59-61`) "redaction happens server-side on the stored payload. The client-side parser must accept a redacted payload… the one change the exam forces on `shared/core/question-schema`" — the spine's Consistency Conventions say the redacted question is its own type and `QuestionContent` is never made nullable. The same file is also stale on callable names (`startThemeExam`/`nextExamQuestion` vs the spine's `startThemeTest`/`acknowledgeQuestion`) and on the pass rule (100% easy + 50% hard, `:84-99`, vs one mixed sitting at 60%). Both are listed as canonical companions. Which governs?
3. **What replaces `scoreVerified` as the gate on paying?** SPEC open question 3 and the spine's Deferred list both punt it; E2 forces it, because a server-scored attempt matches itself by construction. The four things it gates are not one decision (`index.js:387-412`).
4. **Is a HARD Survey legal?** Refuse it at publication, refuse it at pool build, or accept that a hard lesson made half of surveys is a free pass.
5. **Do hard questions leave the SM-2 schedule?** `LessonAttemptRepositoryImpl.kt:47-63` advances repetition from the per-answer digit. AD-6 forbids returning per-question correctness — but AD-6 is written about *sittings*, and this is lesson play. Either hard questions drop out of spaced repetition, or the server returns per-question scores for hard lessons and AD-6's scope is stated explicitly.
6. **Ordering's permutation is decided once, at publication, so it is static across every player and every sitting.** Two players comparing screens see the same order; "the answer is third, first, fourth, second" is shareable exactly as the plaintext key was. AD-32 assumes a per-sitting shuffle at the `online-session` boundary; offline lesson play cannot have one. Accept the weaker property here, or make hard lesson play fetch its ordering questions online?
7. **What happens to the reward strip on the hard result card?** `ResultContent.kt:113-115,572-586` recomputes experience and nolics from the percent, in the UI layer, in parallel with `attemptReward` on the server. Does the server's response carry the real figures, or does the strip disappear for hard?
8. **When does `AnalyticsEvent.LessonFinished` fire for a hard run** (`DefaultLessonRunnerRootComponent.kt:351-358`) — at completion without percent and stars, or when the score lands? The funnel's definition of "finished" has to pick one.
9. **Is `info` redacted for hard questions?** It is a teaching aid the player is meant to read, and in the current catalog it is also the answer in prose.
10. **Backfill policy for the existing library** — redact in place with `scripts/redact-existing-questions.js`, re-publish everything through the review pipeline, or accept the current content as answer-bearing and apply E2 only to new publications.
11. **Does E2 close the reviewer read of `/private/**` and `/admin/**`, or name it and move on?** (Trap 19.)

**Where the sweeps disagreed:** one sweep concluded a redacted payload "fails `parse` → the hard pool vanishes as `EmptyPool`"; another concluded Ordering "parses cleanly and scores the shuffle as the answer". Both are correct, for different types — merged as trap 2 and trap 7. One sweep alone reported the hard-play dead-end (trap 6); I confirmed it by reading `LessonRunnerScreen.kt:250,276,306,613`, `AnswerFeedback.kt:73` and `SessionMode.kt:30-31`, but it deserves a device check before step 6 is scoped.

---

# Largest step

**Step 7.** It is the only step that changes a non-nullable field on a type read across five modules — `Attempt.codeAnswer` and `Attempt.percentScore` (`Attempt.kt:19-20`) are consumed by `computeBestStars`, `observeAllStatsByUser`, the outbox writer, the result screen and the lesson list — and it does so while also costing a hand-written Room migration with a checked-in schema JSON. The test cost dominates: the mandatory-score assumption is not in two tests, it is in the shared fixtures (`FakeAttemptFixtures.kt:12-30` defaults `codeAnswer = "9"`, `percentScore = 100`; `FakeCompleteAttemptUseCase.kt:6-10` returns a scored attempt by default; `DefaultLessonRunnerRootComponentTest.kt:167-192`; `LessonRunnerScreenTest.kt:69-92`), so roughly forty tests break in one commit and there is no way to split the break across two green steps.

Step 8 is the riskiest, not the largest: it is a wire-format change with a new server scorer behind it, but its blast radius is three client files and one function.