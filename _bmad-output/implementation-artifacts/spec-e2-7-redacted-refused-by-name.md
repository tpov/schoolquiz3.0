---
title: 'E2.7 — A question with no answer key fails by name, not by vanishing'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: 'bec48b90'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** When redaction is switched on, the runner will meet questions whose answer key has been removed. Today it parses with `parse`, which refuses them, and `mapNotNull` throws the failure away — so a whole lesson of redacted questions arrives as "Questions are invalid", the same message a corrupt payload produces. Nobody looking at that screen, or at a support report of it, could tell the difference.

**Approach:** Parse through the entry point that reads both kinds, and refuse a redacted question by its own name. The runner still will not play one — that comes later — but the refusal now says what actually happened, and lands before the switch that makes it reachable.

## Boundaries & Constraints

**Always:** A lesson containing redacted questions must report that specifically, distinct from "the payload is broken" and from "there are no questions for this difficulty". Every user-facing string exists in all three languages the app ships — English, Russian and Ukrainian.

**Ask First:** Any change to what the runner will actually play, or to the existing failure reasons' meanings.

**Never:** Do not widen `RunnerQuestion.Valid.content`, do not let a redacted question into the pool, and do not touch scoring, the timer, or the server. This slice changes only which failure is reported.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Every question redacted | a lesson whose questions all lack their answer key | the new reason, distinct from the existing two |
| Some redacted, some playable | a mixed lesson with enough playable ones for the difficulty | plays normally on the playable ones; the redacted ones are not offered |
| Some redacted, none playable for the difficulty | mixed, but the playable ones are all the other difficulty | the new reason — the redacted ones are why this difficulty is unplayable |
| Every payload broken | malformed JSON, unknown discriminator | unchanged: "questions are invalid" |
| Redacted and broken together | some redacted, some malformed, none playable | the new reason — a removed answer key is the more specific and more actionable fact |
| No questions at all for the difficulty, none redacted | an easy-only lesson opened as hard | unchanged: "no questions available" |
| Ordinary lesson | nothing redacted | unchanged in every respect |
| Languages | the new message | present in English, Russian and Ukrainian |

</frozen-after-approval>

## Code Map

- `StartLessonAttemptUseCase.kt:54-79` -- the whole change lives here. `activeQuestions.mapNotNull { parser.parse(...).getOrNull()?.let { RunnerQuestion.Valid(...) } }` discards every failure; then priority 1 is `activeQuestions.isNotEmpty() && valids.isEmpty()` → `NoValidQuestions`, and priority 2 is an empty pool for the difficulty → `EmptyPool`. The new reason has to sit between them, and it must be decided from what the parse *returned*, not from a second parse.
- `QuestionContentParser.parseForDisplay(payload, fallbackId, fallbackText, fallbackDifficulty): Result<QuestionDisplay>` (commit `bec48b90`) -- the entry point that reads both kinds. It has a **default body** on the interface delegating to `parse`, so both `FakeQuestionContentParser`s (`shared/feature/lesson-runner/domain/src/commonTest/.../fake/`, `android/feature/lesson-runner/presentation/src/test/.../fake/`) keep compiling — but they will therefore never return a redacted value. A test that needs one has to teach a fake to.
- A redacted value is a `RedactedQuestionContent`; an ordinary one is a `QuestionContent`. They are separate hierarchies with disjoint discriminators, so telling them apart is a type check, not a field probe.
- `InitFailureReason.kt` -- `EmptyPool`, `NoValidQuestions`, `LessonNotFound`, `AuthRequired`. Domain enum; add one.
- `RunnerStateMapper.kt:169` `toUiReason()` -- maps it to `RunnerUiState.InitFailureReason`, a parallel enum in presentation. Both need the new case.
- `LessonRunnerScreen.kt:212-231` -- the `when` that picks the string, and a Back button. Exhaustive, so the compiler will point at it.
- `values/strings.xml:66`, `values-ru/strings.xml:66`, `values-uk/strings.xml:66` -- `runner_error_no_valid_questions` in all three. The new string goes beside it in each; Ukrainian is a legal requirement for this app, not a nicety.
- `EdgeCasesTest.kt:64-75` -- pins today's all-invalid-payloads → `NoValidQuestions` behaviour; it must stay green. `LessonRunnerScreenTest.kt:494-499` renders the `NoValidQuestions` state. `SurveyQuestionTest.kt` is the precedent for adding a question kind to the domain test suite.
- `RunnerQuestion.kt:23-28` -- `RunnerQuestion.Invalid` exists, is referenced nowhere, and is never constructed, though its KDoc claims such items are filtered at init. It is a ready-made slot for "reached the pool, cannot be played" — worth using rather than leaving dead, if it fits without widening anything.

## Tasks & Acceptance

**Execution:**
- [x] `InitFailureReason.kt` -- add the reason for a question whose answer key has been removed.
- [x] `StartLessonAttemptUseCase.kt` -- parse through `parseForDisplay`, keep ordinary questions exactly as today, and count the redacted ones so the refusal can name them. Order the failures per the Matrix.
- [x] `RunnerUiState.kt` and `RunnerStateMapper.kt` -- carry the new reason through to presentation.
- [x] `LessonRunnerScreen.kt` -- render it.
- [x] `values/`, `values-ru/`, `values-uk/` `strings.xml` -- the message in all three.
- [x] Domain tests -- every Matrix row, including the mixed cases; teach the domain fake to return a redacted value.

**Acceptance Criteria:**
- Given a lesson whose questions have had their answer keys removed, when it is opened, then the failure names that specifically and is distinguishable from a broken payload and from an empty pool.
- Given a lesson with both playable and redacted questions and enough playable ones, when it is opened, then it plays exactly as it does today.
- Given any lesson with nothing redacted, when it is opened, then every existing behaviour and message is unchanged.
- Given the new message, when the app runs in Russian or Ukrainian, then it is translated.

## Verification

**Commands:**
- `./gradlew :shared:feature:lesson-runner:domain:allTests --no-configuration-cache` -- green, new cases included.
- `./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache` -- green.
- `./gradlew ciCheck --no-configuration-cache` -- green apart from failures owned by the parallel session.

## Suggested Review Order

**Which failure a lesson reports**

- Entry point: the three-way classification. `QuestionDisplay` is not sealed, so the impossible branch fails loudly rather than passing as a broken payload.
  [`StartLessonAttemptUseCase.kt:174`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt#L174)

- Only redaction *in the pool being opened* explains an empty pool — counting lesson-wide made an easy-only lesson blame redaction for having no hard questions. An unreadable difficulty counts, because it might be either.
  [`StartLessonAttemptUseCase.kt:66`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt#L66)

- The order of the three failures, and why a removed answer key outranks a broken payload.
  [`StartLessonAttemptUseCase.kt:74`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt#L74)

- The reason itself, marked for deletion at the step that admits redacted questions to the pool.
  [`InitFailureReason.kt`](../../shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/InitFailureReason.kt)

**What the player reads**

- The reason-to-string choice, lifted out of the composable so the gate can assert it — inside it, a copy-paste to the neighbouring string would have shipped green.
  [`InitFailureLabels.kt`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/InitFailureLabels.kt)
