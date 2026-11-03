---
title: 'A hint is an easy-question affordance, as the charge spec has always said'
type: 'bugfix'
created: '2026-09-02'
status: 'done'
baseline_commit: 'e057d51c'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `spec-charges/SPEC.md:47` states the charge has "exactly two sinks: the toll for playing an activity, and a hint on an EASY question." The runner has no difficulty term anywhere in the hint flow, so a charge also buys a guaranteed-correct answer on a hard question. It is hidden rather than visible: on hard the verdict banner is suppressed, so nothing on screen announces it, but the correct answer is still selected and submitted. Hard answers are what gate stars, the hard unlock and certification.

**Approach:** The hint is offered only where the spec says it exists. The rule joins the one decision that already answers "is there a hint to sell", so no call site can forget it.

## Boundaries & Constraints

**Always:** The rule lives in the single pure decision that already governs the hint, and is asserted by a test the gate runs. On easy questions nothing about the hint changes — same cost, same reveal, same states.

**Ask First:** Any change to what a hint costs, or to the toll — this closes a documented gap, it does not reprice anything.

**Never:** Do not touch scoring, the charge economy, the server, or the redaction work. Do not add a new player-facing message: an unavailable hint already renders the same way when the player has no charges.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Easy, answer known, charge available | any of the four question types | unchanged — the button is offered, one charge is spent, the answer is shown |
| Hard, answer known, charge available | any type | the button is not offered and no charge can be spent on it |
| Hard, no answer to reveal | any type | not offered, as on easy |
| Easy, no charges / feedback shown / survey | — | unchanged |
| The decision | the pure hint-availability function | difficulty is one of its terms, alongside charges, feedback and whether an answer exists |

</frozen-after-approval>

## Code Map

- `_bmad-output/specs/spec-charges/SPEC.md:29,47` -- the rule being implemented, in the product's own words: two sinks, and the hint one is an easy-question affordance. `:208-212` — a hint costs a whole charge, indivisible.
- `HintAnswer.kt` `isHintAvailable(qState, charges, feedbackShown)` -- the pure decision, extracted in commit `0582392a` precisely so the gate could assert it; `HintAvailabilityTest` in `src/test` is where its cases live. The difficulty term belongs here.
- `LessonRunnerScreen.kt:133-136` -- `isHard` is already computed on screen, from `RunnerUiState.Question.isHard` with `component.isHardMode` as the fallback; `RunnerUiState.kt:22` carries it. Nothing new has to be plumbed.
- `LessonRunnerScreen.kt` `rememberHintControl` and the four handlers -- each spends only after the typed draft is in hand (commit `0582392a`); the difficulty term must gate them through the same single decision rather than being repeated at four sites.
- `RunnerUiState.kt:24` `revealCorrect` defaults true and is false on hard — that is why this gap is invisible today: the verdict banner is suppressed while the answer is still submitted. It is not a substitute for the rule.
- `DefaultLessonRunnerRootComponent.kt` `hintRequested()` -- the spend itself; unchanged. `strings.xml` in all three locales -- untouched, per the Never clause.

## Tasks & Acceptance

**Execution:**
- [x] `HintAnswer.kt` -- difficulty becomes a term of `isHintAvailable`, with the spec's sentence quoted where the rule is written down.
- [x] `LessonRunnerScreen.kt` -- pass the difficulty the screen already knows into that decision; no call site gains a check of its own.
- [x] `HintAvailabilityTest` -- the Matrix rows, including that easy behaviour is untouched.

**Acceptance Criteria:**
- Given a hard question and a charge, when the player looks at it, then no hint is offered and no charge can be spent on it, for every question type.
- Given an easy question, when the hint is used, then everything about it is exactly as before.
- Given the pure decision, when a difficulty is not supplied, then it cannot be called at all — the rule is not defaulted away.

## Verification

**Commands:**
- `./gradlew :android:feature:lesson-runner:presentation:test --no-configuration-cache` -- green, including the existing charge-spend tests.

## Suggested Review Order

- The rule, where the one decision already lived: `isHard` has no default, so a call site that forgets difficulty does not compile. The KDoc quotes the charge spec's own sentence, and says why `revealCorrect` was never this rule — it hid the gap rather than closing it.
  [`HintAnswer.kt:30`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/HintAnswer.kt#L30)

- The screen passes the difficulty it already had; no handler gained a check of its own, so there is one place to get this wrong rather than four.
  [`LessonRunnerScreen.kt:360`](../../android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt#L360)
