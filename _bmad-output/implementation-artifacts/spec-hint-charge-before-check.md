---
title: 'The hint stops charging for nothing'
type: 'bugfix'
created: '2026-09-01'
status: 'done'
baseline_commit: '13369a93'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** On three of the four question types the hint spends a charge before checking there is anything to reveal. On multiple choice it is worse than showing nothing: it submits the empty correct set, so the player pays a charge **and** gets the question marked wrong. Only single choice checks first. The hint button is also offered whenever the player has a charge, with no test that the question can be hinted at all.

**Approach:** Build the hint answer first; if there is nothing to show, the button is not offered and no charge is spent. One place decides whether a hint exists, and all four types ask it.

## Boundaries & Constraints

**Always:** A charge is spent only when the player is actually shown the answer. "There is a hint to give" is one decision, made in one place, for all four types. A question type whose correct answer is empty or missing is not hintable.

**Ask First:** Any change to what a hint costs, or to what it does once it fires — this is about paying for nothing, not about rebalancing.

**Never:** Do not change the charge economy, the scoring path, or the survey rule that a survey has no hint. Do not widen this into the redacted-question work.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|---|---|---|
| Single choice, answer known | a charge available | button offered; one charge spent; the correct option is selected and revealed |
| Multiple choice, answer known | a charge available | button offered; one charge spent; the correct set is submitted |
| Multiple choice, correct set empty | a charge available | **button not offered; no charge spent; nothing submitted** — today it charges and submits an empty answer, marking the question wrong |
| Ordering, correct order empty | a charge available | button not offered; no charge spent — today it charges and shows nothing |
| Fill blank, no known blanks | a charge available | button not offered; no charge spent — today it charges and leaves the draft empty |
| Single choice, no correct id | a charge available | button not offered — today the button is offered and does nothing when pressed |
| Survey | a charge available | button not offered, as today |
| No charges left | correct answer known | button not offered, as today |
| Feedback already shown | any | button not offered, as today |

</frozen-after-approval>

## Code Map

- `LessonRunnerScreen.kt:352` -- `val hintEnabled = livesAvailable && feedback == null && qState !is QuestionUiState.Survey`. It asks whether the player *can pay*, never whether there is anything to buy. This is where the missing term belongs.
- The four handlers, and the ordering that is the bug:
  - `:405` single choice -- `if (correctId != null && component.hintRequested())` — correct: the check short-circuits before the spend.
  - `:438` multiple choice -- `if (component.hintRequested()) submitWith(qState.correctIds)` — spends, then submits whatever `correctIds` holds, including an empty set.
  - `:502` ordering -- `if (component.hintRequested() && qState.correctOrderIds.isNotEmpty())` — spends, then finds nothing to show.
  - `:579` fill blank -- `if (!component.hintRequested()) return@FillBlankContent` — spends, then builds an empty draft.
- `DefaultLessonRunnerRootComponent.kt:255-263` `hintRequested()` -- decrements and returns true, or returns false when there is nothing left. It is a *spend*, not a query, despite reading like one at the call sites. Note the field is named `lives`/`livesRemainingHearts` in code while the product calls it a charge (`strings.xml:51` renders `runner_figure_lives` as "Charges").
- `HintAnswer.kt` `buildHintDraft(qState)` -- already the null-safe version of all four handlers, and already used by tests, but **only by tests**: production re-implements the logic inline at each site and lost the null-safety at three. It returns null for Survey and for a single choice with no correct id, but still returns a draft for an empty multiple-choice set, an empty ordering and an empty fill-blank map — so it is the right place for the decision and is not yet sufficient for it.
- `QuestionUiState.kt:14,24,44,55` -- every correct-answer field defaults to empty (`null`, `emptySet()`, `emptyList()`, `emptyMap()`), which is why a question with no answer key renders a hint button rather than failing.
- `RevealDigitAndHintTest.kt` -- the only `buildHintDraft` coverage, in the presentation module's `src/test`. `DefaultLessonRunnerRootComponentTest.kt:983,1001` -- `hintRequested_spendsOneLife` and `hintRequested_refused_whenNoLives` pin the spend itself; they must stay green.
- `LessonRunnerScreenTest.kt` (androidTest) -- Compose UI tests for this screen exist and are compiled by `ciCheck`.

## Tasks & Acceptance

**Execution:**
- [x] `HintAnswer.kt` -- return null whenever there is nothing to reveal, not only for a survey: an empty correct set, an empty order, and a fill blank with no resolvable blanks all mean "no hint".
- [x] `LessonRunnerScreen.kt` -- add the missing term to `hintEnabled`, and route all four handlers through the one decision so each spends only after the answer is in hand. The single-choice site already has the right shape; the other three adopt it.
- [x] `HintAnswer` tests -- cover the empty cases per the Matrix.
- [x] Component or UI test -- a hintable question spends exactly one charge; an unhintable one spends none and submits nothing.

**Acceptance Criteria:**
- Given a question whose correct answer is unknown or empty, when the player looks at it, then no hint button is offered and no charge can be spent on it.
- Given a multiple-choice question with an empty correct set, when the hint is unreachable, then nothing is submitted — the player is not marked wrong for a hint they never got.
- Given a hintable question of any type, when the hint fires, then exactly one charge is spent and the answer is shown, as today.
- Given a survey, no charges, or feedback already on screen, then the button is not offered, exactly as today.

## Verification

**Commands:**
- `./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache` -- green, including the existing charge-spend tests.
- `./gradlew ciCheck --no-configuration-cache` -- green apart from failures owned by the parallel session.

## Suggested Review Order

**Who decides there is a hint to sell**

- Entry point: the enablement decision, pure and tested on the JVM — the wiring fix is otherwise pinned only by instrumented tests the gate compiles but never runs.
  [`HintAnswer.kt:22`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/HintAnswer.kt#L22)

- The one reachable case: a template with fewer gaps than declared blanks yields a partial answer. Both halves matter — every gap must resolve, and the key must hold nothing beyond them, because the domain grades over every declared blank.
  [`HintAnswer.kt:99`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/HintAnswer.kt#L99)

- The verdict now comes from the answer that was submitted; it used to compare the answer key with itself and always read perfect.
  [`HintAnswer.kt:122`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/HintAnswer.kt#L122)

**Where the charge is spent**

- One place holds the per-question spent flag, read inside the lambda — recomposition is too late to stop a same-frame second tap.
  [`LessonRunnerScreen.kt:357`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt#L357)

- Each type builds its own draft type, so wiring a handler to the wrong one is a compile error rather than a lit button that does nothing.
  [`LessonRunnerScreen.kt:301`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt#L301)
