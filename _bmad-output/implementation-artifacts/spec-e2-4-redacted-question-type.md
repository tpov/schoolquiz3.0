---
title: 'E2.4 — A Kotlin type for a question with its answer removed'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: '22a64a9c'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `functions/question-redaction.js` (commit `22a64a9c`) now emits four shapes — `SingleChoiceRedacted`, `MultipleChoiceRedacted`, `OrderingRedacted`, `FillBlankRedacted` — that no Kotlin type describes. Nothing holds the two sides equal, so the wire contract the whole epic rests on can drift silently. Worse, handing one to the current parser reports `"Unsupported legacy question type: SingleChoiceRedacted"`, because the enriched overload discards the accurate error and falls into the legacy branch.

**Approach:** Declare the four shapes in Kotlin as a **sibling** of `QuestionContent`, not a subtype, so `QuestionContent`'s invariants and the authoring paths that re-encode what they parse are untouched. Pin the two languages together with a fixture file both test suites read, the way `scoring-fixtures.json` already pins the scorer. Nothing decodes the new type in production yet.

## Boundaries & Constraints

**Always:** The Kotlin shape must match what the JavaScript emits exactly, field for field, and a fixture file both sides read is what proves it. `RedactedQuestionContent` is a separate sealed hierarchy — `QuestionContent` gains no new subtype and no new invariant.

**Ask First:** Any change to `QuestionContent`'s existing declarations, invariants, or wire format.

**Never:** Do not add a common supertype, `displayTexts`, or `parseAny` — that is the next slice. Do not touch the lesson runner, the authoring or review components, `Scoring.kt`, or `functions/index.js`. Do not make the existing `parse` overloads able to return a redacted value: authoring must stay structurally incapable of receiving one.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|---|---|---|
| Round trip | each of the four redacted shapes as JavaScript emits it | decodes into the matching Kotlin type and re-encodes to the same JSON |
| `info` absent | the emitter never writes `info` | decodes — the field must have a default, not be required |
| `imageUrl` present-and-null | `"imageUrl": null` | decodes; a string value decodes too |
| FillBlank blanks | `"blanks": ["b1","b2"]` — bare strings, not objects | decodes; there is no `correctCandidateId` anywhere in the shape |
| `parse` on a redacted payload | `{"type":"SingleChoiceRedacted",…}` | `Result.failure` — a redacted payload can never become a `QuestionContent` |
| The failure says what happened | same input, enriched overload with fallbacks | the message names the unregistered discriminator, **not** "Unsupported legacy question type" |
| Legacy still works | `{"type":"single-choice","options":[…],"correctIndex":0}` | unchanged — still parses through the legacy branch |
| Full payload still works | any of the five ordinary shapes | unchanged |

</frozen-after-approval>

## Code Map

- **The exact wire shapes**, captured from the shipped emitter — Kotlin must match these literally:
  - `{"type":"SingleChoiceRedacted","id","difficulty","text","imageUrl","options":[{"id","text"}]}`
  - `{"type":"MultipleChoiceRedacted", …same…}` — nothing reveals how many options are correct
  - `{"type":"OrderingRedacted", …, "items":[{"id":"ri-0","text"}]}`
  - `{"type":"FillBlankRedacted", …, "blanks":["b1"],"candidates":[{"id":"rc-0","text"}],"protectedTextSegments":[]}`
  - `info` is **never emitted**; `imageUrl` is **always** emitted, null or string; `text` is always present. `id` and `difficulty` are **conditional** — the emitter copies each only when the source payload has it, and the seed corpus keeps `id` on the wrapper document rather than inside `payload`. `difficulty` is copied **verbatim as any string**, so `""` and `"MEDIUM"` reach the wire as well as `"EASY"`/`"HARD"`; that verbatim copy is load-bearing for the economy and must not be normalised here. There is no redacted Survey — a survey has no answer to remove.
- `functions/question-redaction.js` -- the emitter, read-only. `redact(payloadJson, difficulty, {random, questionId})` → `{status, publicPayload, key, reason}`; `status: "redacted"` produces these shapes.
- `KotlinxSerializationQuestionContentParser.kt:12` -- `Json { ignoreUnknownKeys = true }`, discriminator `"type"`. `ignoreUnknownKeys` does **not** cover the discriminator, so `parse` already fails cleanly naming the unregistered subclass. `:31` is the defect: the enriched overload sees `isFailure`, **discards that exception**, calls `parseLegacy` (`:35`), which misses the only legacy case `"single-choice"` (`:46`) and reports `"Unsupported legacy question type: <name>"`. The legacy `type` key and the polymorphic discriminator are the same key — that collision is the root of it.
- `QuestionContentParser.kt:10,19-24` -- two overloads, the enriched one with a default body. `DefaultQuestCreateComponent.kt:1251` and `DefaultReviewQueueComponent.kt:565` **re-encode what they parsed** (`:755`, `:454`), so a redacted payload reaching them would rewrite the stored question. That is why `parse` must keep returning `Result<QuestionContent>`.
- `QuestionContent.kt:12-19` -- the existing sealed interface, read-only this slice.
- `QuestionContentParserTest.kt` -- the module's only test file: `kotlin.test`, triple-quoted JSON literals, one `private val parser`. `:86` asserts an unknown discriminator fails but only checks `isFailure`, so it offers no protection against the redacted type becoming parseable.
- The module has **no `jvmTest`**, and `commonTest` cannot read a file. Precedent for the pin: `shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json`, loaded off the classpath by `ScoringFixtureParityTest.kt` and by relative path from `functions/assessment-scoring.test.js`. `functions/package.json:8-9` -- hand-maintained chains; an unlisted file never runs.

## Tasks & Acceptance

**Execution:**
- [x] `shared/core/question-schema/src/commonMain/.../RedactedQuestionContent.kt` -- new; `@Serializable sealed interface` with four variants, `@SerialName` set to the emitted discriminators. `info` is not a field. No `init` invariants — this type describes what arrived, not what is valid.
- [x] `shared/core/question-schema/src/jvmTest/resources/redacted-question-fixtures.json` -- new; one case per variant verbatim as the emitter produces it, plus the Matrix's negative cases.
- [x] `shared/core/question-schema/src/jvmTest/kotlin/.../RedactedQuestionWireTest.kt` -- new; decodes each fixture into the expected type, re-encodes and compares, and asserts `parse` refuses it. Fails loudly if the fixture is missing.
- [x] `functions/question-redaction-wire.test.js` -- new; asserts the fixture still matches what `redact` emits today, so neither side can move alone.
- [x] `KotlinxSerializationQuestionContentParser.kt` -- the enriched overload must stop reporting an unregistered discriminator as a legacy failure; preserve the original exception when the payload is not legacy.
- [x] `functions/package.json` -- add the new test to the `test` chain, leaving the parallel session's entries alone.

**Acceptance Criteria:**
- Given each redacted shape as JavaScript emits it, when Kotlin decodes it, then it yields the matching type and re-encodes byte-identically.
- Given the emitter changes shape without the fixture being updated, when the suites run, then the JavaScript side fails; given Kotlin drifts, the Kotlin side fails.
- Given a redacted payload, when either `parse` overload is called, then it fails with a message naming the unregistered discriminator rather than claiming a legacy type.
- Given an ordinary or legacy payload, when parsed, then behaviour is exactly as before.

## Verification

**Commands:**
- `./gradlew :shared:core:question-schema:allTests --no-configuration-cache` -- green, the wire test executed.
- `cd functions && npm test` -- all suites pass, the wire test among them.
- `git status --short functions/index.js firestore.rules` -- nothing of mine in either.

## Spec Change Log

- **Finding:** the Code Map asserted that `id`, `difficulty` and `text` are always present in an emitted payload. Two of those three are false, and all three reviewers found it independently. Verified against the shipped emitter: with no difficulty anywhere it emits no `difficulty` key at all; a payload `difficulty` of `""` or `"MEDIUM"` is copied verbatim. A required `Difficulty` enum therefore refuses three shapes the emitter is written and tested to produce, and a required `id` refuses the public half of every seeded question.
  **Amended:** the Code Map now states which fields are conditional and that `difficulty` travels as an arbitrary string. The redacted type must be able to decode anything the emitter emits — deciding what a non-enum difficulty *means* belongs to the consumer slice, not to the type that describes the wire.
  **Known-bad state avoided:** a Kotlin type that cannot read the questions the server publishes, with a two-language harness that cannot see the gap because no fixture exercises the conditional branches.
  **KEEP:** the bilateral fixture — each case carrying the emitter's inputs (`source`, `seed`, `documentDifficulty`, `questionId`) so JavaScript re-runs `redact` and compares while Kotlin decodes and re-encodes; generating the fixture by running the emitter rather than transcribing it; ids typed as plain `String` so a re-issued `ri-0` cannot type-check against a real `OptionId`; and the negative proofs, which showed the pin can actually go red.

## Suggested Review Order

**The type that describes the wire**

- Entry point: a sibling of `QuestionContent`, never a subtype — authoring re-encodes what it parses, so a redacted payload must not be able to reach it.
  [`RedactedQuestionContent.kt:53`](../../shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionContent.kt#L53)

- `difficulty` travels as an arbitrary string because the emitter copies it verbatim; only `EASY`/`HARD` map to the enum, and that mapping is computed, not stored.
  [`RedactedQuestionContent.kt:91`](../../shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionContent.kt#L91)

- The four discriminators, which must match the emitter character for character.
  [`RedactedQuestionContent.kt:111`](../../shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionContent.kt#L111)

**The pin, and why it can go red**

- Coverage derived from the sealed hierarchy, not a hand-written list — a fifth variant with no fixture fails immediately.
  [`RedactedQuestionWireTest.kt:48`](../../shared/core/question-schema/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionWireTest.kt#L48)

- The three conditional shapes the emitter really produces: absent, `""`, and a value outside the enum.
  [`RedactedQuestionWireTest.kt:311`](../../shared/core/question-schema/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionWireTest.kt#L311)

- The leak check walks keys recursively; a substring scan failed on a question whose prose contained the word.
  [`RedactedQuestionWireTest.kt:175`](../../shared/core/question-schema/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionWireTest.kt#L175)

- A stale `reEncodesExactly: false` cannot quietly downgrade a case that byte-identity already covers.
  [`RedactedQuestionWireTest.kt:147`](../../shared/core/question-schema/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionWireTest.kt#L147)

- The JavaScript half re-runs the emitter and compares — the only direction Kotlin cannot check.
  [`question-redaction-wire.test.js`](../../functions/question-redaction-wire.test.js)

**The parser**

- An unregistered discriminator stops being reported as a legacy-format failure; only `"single-choice"` goes down that branch now.
  [`KotlinxSerializationQuestionContentParser.kt`](../../shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt)

- Both overloads must refuse every redacted payload — that refusal is the authoring guarantee.
  [`RedactedQuestionWireTest.kt:202`](../../shared/core/question-schema/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionWireTest.kt#L202)
