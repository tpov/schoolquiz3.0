---
title: 'E2.1 — A server-side scorer pinned to Kotlin by a shared fixture set'
type: 'feature'
created: '2026-08-31'
status: 'done'
baseline_commit: '9fd88af9bb458d29960d141630944ff4d9608c2b'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing on the server has ever scored an answer. Every "correct" number in Firestore is whatever the device claimed; the only check is that the claimed digits sum to the claimed percent (`index.js:3033`). E2 takes the answer key off the device, which makes a server scorer mandatory — and it must agree with `Scoring.kt` digit-for-digit, where Kotlin `Int` division makes silent drift the default outcome.

**Approach:** Write `functions/assessment-scoring.js` as a literal mirror of `Scoring.kt`, and pin both languages to one JSON fixture file each side reads from its own test suite. Nothing calls the new code yet — this step builds the implementation and the harness that keeps the two honest.

## Boundaries & Constraints

**Always:** Mirror Kotlin integer semantics literally — `Math.floor` at every division, including the nested `den/2` inside `scoreDigit`. The fixture file is the single source of truth: a new case must need no edit on either side beyond the JSON. A missing fixture is a test **failure**, never a skip.

**Ask First:** Anything that looks like a Kotlin bug (`Ordering` has no uniqueness invariant on item ids; `Survey` ignores `allowMultiple`). Mirror it and flag it — never "fix" it on one side.

**Never:** Do not call the new functions from any handler, change what is stored, or touch `result-verification.js`, `index.js`, `firestore.rules`, `lesson-statistics.js`, the Kotlin scorer, or client code. No new npm dependencies — the suite is plain `node file.test.js` with `assert`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|---|---|---|
| Digit→percent map | codeAnswer `"1"`..`"9"` each alone | `0,12,25,37,50,62,75,87,100` — truncated, **not** `12.5/37.5/62.5/87.5` |
| Zeros excluded | `"0090"` / `""` / `"00000"` | `100` / `0` / `0` — zero means "not shown", dropped before averaging |
| Type mismatch | `SingleChoice` content + `ordering` answer | `1` via the `else` fall-through — not an error, not `0` |
| Unknown option id | `SingleChoice`, `selected:"opt-99"` | `1` — the id is nulled before comparing |
| MultipleChoice partial | 3 correct, picks 2 correct + 1 wrong | `scoreDigit(2, 4)` — denominator is `picked ∪ correct` |
| Ordering not a permutation | wrong length / duplicate / foreign id | `1` immediately, no positional scoring |
| FillBlank | 2 of 3 blanks right | `scoreDigit(2, 3)` — denominator is `blanks.size`; extra `filled` keys ignored |
| Survey participation | any valid option id selected | `9`; empty or all-invalid → `1` |
| Stars at bounds | percent `0 / 50 / 100` | EASY `0 / 10 / 20`, HARD `20 / 25 / 30` tenths |

</frozen-after-approval>

## Code Map

- `shared/core/scoring/src/commonMain/kotlin/.../Scoring.kt` -- **the spec**, read-only. `evaluateAnswer` `:18-61`, `computeStars` `:67-77`, `computePercentScore` `:81-86`, `scoreDigit` `:92-96`.
- Integer traps: `scoreDigit` `:94` = `(num*8 + floor(den/2))/den + 1` then `coerceIn(1,9)`; stars `:71-72` = `(p*20+50)/100` and `20 + (p*10+50)/100`; percent `:84` = `((digit-1)*100)/8` — multiply first, **no** rounding constant, unlike `:94`; `:85` = `sum / nonZeroCount`, truncating. `functions/result-verification.js:24-25` shows the required `Math.floor` discipline.
- **Two discriminator namespaces, both under key `"type"`**: `UserAnswer` kebab-case (`UserAnswer.kt:19,23,27,31,36`) — `single-choice`, `multiple-choice`, `ordering`, `fill-blank`, `survey`; `QuestionContent` PascalCase (`QuestionContent.kt:45,69,95,123,144`) — `SingleChoice`, … Reusing one set scores everything `1`.
- Id value classes serialize as **bare strings** (`OptionId.kt:5-11`) — `selected` / `order` / `filled` values are plain JSON strings, not wrappers.
- Fixtures must write `imageUrl` explicitly (`null` is fine) — no default in any variant (`QuestionContent.kt:50,74,100,128,149`); `info`, `allowMultiple`, `protectedTextSegments` do have defaults. Answers take no extra keys.
- Invariants that reject a hand-written fixture (`QuestionContent.kt:56-171`): options/items `size in 2..8`; `MultipleChoice.correctOptionIds.size >= 2`; `FillBlank.blanks.size in 1..3` and `candidates.size` **exactly 5 or 10**; every id non-blank.
- `commonTest` **cannot** read a file (no okio/kotlinx-io in the catalog); `jvmTest` can and exists by convention (`buildSrc/.../KmpLibraryConventionPlugin.kt:17-18`), though `shared/core/scoring` has only `commonMain`/`commonTest` today. `kotlinx-serialization-json` is already on the test classpath via `scoring/build.gradle.kts:14`.
- Precedent for a file-reading jvmTest: `shared/core/persistence/src/jvmTest/.../TypeConvertersPhase02Test.kt:30` — but do **not** copy its `:31` silent-skip-if-missing idiom.
- `functions/package.json:9` (`test`) is a hand-maintained `&&` chain, no globs; it is what `build.gradle.kts:39-46,55` runs inside `ciCheck`. A new test file is invisible until appended.

## Tasks & Acceptance

**Execution:**
- [x] `shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json` -- new; arrays `evaluateAnswer` / `computePercentScore` / `computeStars`, each case carrying a `name`. Lives under test resources so Gradle tracks it as a `jvmTest` input and Kotlin loads it off the classpath — no working-directory assumption. Covers every Matrix row plus all nine digits.
- [x] `functions/assessment-scoring.js` -- new; `evaluateAnswer` over the five shapes, `computePercentScore`, `computeStars`, `scoreDigit`. Pure, no `firebase-admin`.
- [x] `functions/assessment-scoring.test.js` -- new; drives every fixture case, names the failing one. Reads the JSON at `path.join(__dirname, "../shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json")`, with a comment on why it lives there.
- [x] `shared/core/scoring/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/scoring/ScoringFixtureParityTest.kt` -- new; decodes each case's `content` and `answer`, calls the real functions, names the failing case, fails if the resource is absent.
- [x] `functions/package.json` -- append the new test to the `test` chain and the new module to `lint`.

**Acceptance Criteria:**
- Given the fixture file, when `npm test` and `:shared:core:scoring:jvmTest` both run, then every case is exercised by both sides and both pass.
- Given a case appended to the JSON only, when both suites run, then both pick it up with no code edit on either side.
- Given one constant is deliberately altered in `assessment-scoring.js` or in `Scoring.kt`, when both suites run, then exactly that side goes red.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, `assessment-scoring` among them.
- `./gradlew :shared:core:scoring:jvmTest --no-configuration-cache` -- green, parity test executed (not skipped).
- `./gradlew ciCheck --no-configuration-cache` -- green, with `:shared:core:scoring:jvmTest` and `:functionsTest` both in the task graph.

## Suggested Review Order

**The scorer, and where the two languages can drift**

- Entry point: the two discriminator namespaces that share the key `"type"` — reuse one set and everything scores 1.
  [`assessment-scoring.js:38`](../../functions/assessment-scoring.js#L38)

- The one formula built on Kotlin `Int` division; `Math.trunc` at both steps, plus the clamp Kotlin gets from `Score`.
  [`assessment-scoring.js:103`](../../functions/assessment-scoring.js#L103)

- The five-branch dispatch and its `else`: a mismatched pair scores 1, never 0, never an error.
  [`assessment-scoring.js:200`](../../functions/assessment-scoring.js#L200)

- Guards a null answer key, which is exactly the shape E2's redaction step will produce.
  [`assessment-scoring.js:110`](../../functions/assessment-scoring.js#L110)

- Denominator is `picked ∪ correct`; unknown ids are discarded, not counted as wrong picks.
  [`assessment-scoring.js:124`](../../functions/assessment-scoring.js#L124)

- Length check then set check: a padded order is rejected before any positional scoring.
  [`assessment-scoring.js:139`](../../functions/assessment-scoring.js#L139)

- Denominator is `blanks.size`; a `Map` so a blank named `constructor` reads as absent.
  [`assessment-scoring.js:160`](../../functions/assessment-scoring.js#L160)

- Digit-to-percent truncates with no rounding constant — 12, not 12.5.
  [`assessment-scoring.js:238`](../../functions/assessment-scoring.js#L238)

- Range guard standing in for the `PercentScore` invariant Kotlin gets from its type.
  [`assessment-scoring.js:261`](../../functions/assessment-scoring.js#L261)

**The pin — one file, two readers**

- The fixture contract: what each field means and which invariants a hand-written case must satisfy.
  [`scoring-fixtures.json:2`](../../shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json#L2)

- Kotlin decodes each case inside the loop, so a fixture rejected by an init block is named, not fatal.
  [`ScoringFixtureParityTest.kt:140`](../../shared/core/scoring/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/scoring/ScoringFixtureParityTest.kt#L140)

- Loads off the test classpath — no working directory, and Gradle tracks the file as an input.
  [`ScoringFixtureParityTest.kt:155`](../../shared/core/scoring/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/scoring/ScoringFixtureParityTest.kt#L155)

- The JS half resolves the same file by relative path; a missing file fails rather than skips.
  [`assessment-scoring.test.js:26`](../../functions/assessment-scoring.test.js#L26)

- Both halves report every mismatch at once — a changed constant drifts families of cases, not one.
  [`assessment-scoring.test.js:55`](../../functions/assessment-scoring.test.js#L55)

**Coverage the shared fixtures structurally cannot carry**

- Shapes `QuestionContent` rejects at decode, so they can only be asserted on the JS side.
  [`assessment-scoring.test.js:141`](../../functions/assessment-scoring.test.js#L141)

- Holds the older `recomputePercentScore` to the same fixtures until the two copies are folded.
  [`assessment-scoring.test.js:113`](../../functions/assessment-scoring.test.js#L113)

**Gate wiring**

- The hand-maintained chains — a test file not listed here never runs in `ciCheck`.
  [`package.json:8`](../../functions/package.json#L8)
