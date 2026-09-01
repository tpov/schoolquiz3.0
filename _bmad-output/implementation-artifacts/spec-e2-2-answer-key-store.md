---
title: 'E2.2 — Splitting a published question from its answer key'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: 'cd623650'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Published questions are world-readable (`firestore.rules:127`, `allow read: if true`) and carry the whole answer key inside `payload`. The key also survives a naive strip: Ordering's correct answer *is* the array order, and the authoring component mints ids positionally — `ord-0…` in the right order, `cand-0..cand-(n-1)` for the correct candidates — so shuffling an array while keeping its ids leaks the answer again.

**Approach:** A pure module that splits a payload into a public half and a key half, with the tests that prove the split is both complete and reversible. Nothing calls it yet; wiring it into publication is a separate slice. This isolates the logic that has to be right from the write path that has to be careful.

## Boundaries & Constraints

**Always:** Redaction is scheme-agnostic — re-issue whatever ids are present, never parse an ordinal out of an id. The seed corpus uses `a`/`b`/`c`, `i1..i4`, `c1..c5`, so any `opt-`/`ord-`/`cand-` assumption is wrong. `difficulty` and every `text` — the question's and every option, item and candidate's — must survive in the public half: `questionCharsCount` and `lessonAllocatedSeconds` count them, and losing them collapses rewards and unlock prices. A payload the module does not recognise yields no key and no change, never an exception.

**Ask First:** Any key shape that cannot reconstruct the original answer exactly, or any change that would make a public half unreadable by the current client.

**Never:** Do not call the module from anywhere. Do not touch `functions/index.js`, `firestore.rules`, the client, the Kotlin schema, or `assessment-scoring.js`. Do not import `firebase-admin` — the module must stay unit-testable like `lesson-reward.js`. No backfill.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|---|---|---|
| SingleChoice | payload with `correctOptionId` | key holds `correctOptionId`; public half keeps every option id and text, drops the key and `info` |
| MultipleChoice | 2 correct of 5 | key holds `correctOptionIds`; the public half must not reveal **how many** are correct |
| Ordering | items in author order | items shuffled and re-issued `ri-0…`; key holds the canonical id order plus the id map |
| FillBlank | 3 blanks, 10 candidates | candidates shuffled and re-issued `rc-0…`; blanks stay in original order as bare ids; key holds blank→candidate plus the id map |
| Survey | any survey payload | no key at all — `Survey` has no correct-answer field to store |
| Economy fields | any type | `difficulty`, question `text` and every option/item/candidate `text` are identical before and after |
| Seed-corpus ids | options `a`,`b`,`c`; items `i1..i4`; candidates `c1..c5` | redacts correctly — no path assumes an authoring prefix |
| Unrecognised payload | legacy `{"type":"single-choice","options":["a","b"],"correctIndex":0}`, malformed JSON, unknown `type` | no key, payload returned untouched, no throw |

</frozen-after-approval>

## Code Map

- `functions/assessment-scoring.js` (committed, `cd623650`) -- reads `correctOptionId`, `correctOptionIds`, the `items` **array order**, and `blanks[].correctCandidateId`. Read-only here. Note `scoreOrdering:139-152` takes the correct order from the array order of `content.items` — this is why a redacted Ordering must not be able to reach it.
- `functions/lesson-reward.js:135` `questionCharsCount` -- reads `content.text`, `content.imageUrl` (truthy only), and the `text` of every `options` / `items` / `candidates` entry. `:180` `lessonAllocatedSeconds` reads `content.difficulty`, uppercased, and also drops `archived` rows and dedupes translated variants (`q1__ru`). This is why the public half keeps the lists and their texts.
- `shared/core/question-schema/.../QuestionContent.kt:17` -- `difficulty` is a required field of the sealed interface, so it lives **inside** the payload JSON. `:46-60` `Survey` has no correct-answer field. `:124-136` `Ordering` requires only `items.size in 2..8`, so a shuffled Ordering still deserializes cleanly — nothing in the schema stops it.
- `.../KotlinxSerializationQuestionContentParser.kt:12` -- `Json { ignoreUnknownKeys = true }`. A marker field beside the old discriminator is silently swallowed, which is why the redacted shape needs its own `type`. `:82` -- the legacy `{"type":"single-choice","options":[<strings>],"correctIndex":N}` shape is the concrete unrecognised-payload case.
- `DefaultQuestCreateComponent.kt:1147` -- Ordering items are `ord-$index` in the correct order. `:1176-1182` -- FillBlank correct candidates are `cand-0..cand-(n-1)`, positionally first. Ids are monotonic but **not contiguous** — a blank editor row is dropped after indexing.
- `FillBlankAuthoring.kt:100-104,277` -- `protectedTextSegments` can hold a correct answer verbatim: `***word***` markup marks a blank as protected. `QuestionContentParserTest.kt:76` is a real fixture pairing `c1 → "Kotlin"` with `protectedTextSegments: ["Kotlin"]`.
- `scripts/seed-bulk/data/courses/english-tech/_helpers.js:19-23` -- the seed corpus uses `a`/`b`/`c`, `i1..i4`, `c1..c5`, `b1..b3`, on thousands of published questions.
- `functions/lesson-reward.js` and `functions/lesson-reward.test.js` -- the model for a pure, `firebase-admin`-free module and its plain-`node`/`assert` test style.
- `functions/package.json:8-9` -- hand-maintained `lint` and `test` chains, no globs. A file not listed never runs; only `test` reaches `ciCheck` (`build.gradle.kts:39-46,55`). The `mutation-queue` entries there belong to a parallel session.

## Tasks & Acceptance

**Execution:**
- [x] `functions/question-redaction.js` -- new; `redact(payloadJson, difficulty, {random})` -> `{publicPayload, key}` across the five types, plus `restoreAnswer(key)`. Pure, no `firebase-admin`. The public half carries its **own discriminator** — `SingleChoiceRedacted`, `MultipleChoiceRedacted`, `OrderingRedacted`, `FillBlankRedacted` — never the source one. Unrecognised input returns the payload unchanged and no key; nothing throws.
- [x] `functions/question-redaction.test.js` -- new; every Matrix row, the two properties (key + public half reconstruct the answer exactly; `questionCharsCount` and `lessonAllocatedSeconds` unchanged), and the cross-module assertions in Design Notes.
- [x] `functions/package.json` -- add the module to `lint` and the test to `test`, leaving the parallel session's entries alone.

**Acceptance Criteria:**
- Given a redacted public half of any type, when it is passed to `evaluateAnswer` in `functions/assessment-scoring.js`, then it scores the floor — for Ordering specifically, submitting the displayed sequence must not score 9.
- Given a question of each type, when the key is combined with the public half, then the original answer is recoverable exactly — and from the public half alone it is not, including the count of correct answers for MultipleChoice.
- Given a FillBlank whose `protectedTextSegments` contains a correct candidate's text, when redacted, then that text does not appear in the public half.
- Given a payload whose correct id names an option that does not exist, or an empty `correctOptionIds`, or a null/non-object row in any list, when redacted, then it is refused whole — no key, payload untouched, nothing thrown.
- Given an already-redacted payload, when redacted again, then it is a no-op.
- Given a payload using the seed-corpus id schemes, when redacted, then it redacts correctly, and `questionCharsCount` and `lessonAllocatedSeconds` return what they returned before.

## Spec Change Log

- **Finding:** a redacted Ordering scored *backwards*. `scoreOrdering` (`assessment-scoring.js:139-152`) derives the correct order from the array order of `content.items`, and the public half kept `"type":"Ordering"` with the items shuffled — so submitting the displayed sequence scored 9 while the true answer scored 5. Verified directly, and independently by two reviewers. The same root cause let a redacted payload be indistinguishable from a real one everywhere.
  **Amended:** the public half now carries its own discriminator (`*Redacted`), which is what `e2-plan.md` Step 2 specified and what the first spec failed to carry over. That one change makes "a redacted payload reaches the scorer" structurally impossible rather than merely unlikely, and `assessment-scoring.js` needs no edit — a type that does not match falls through its `else` branch to the floor. Also added: `protectedTextSegments` must not republish an answer's text; dangling or empty correct ids are refused whole; malformed rows are refused rather than thrown on; a second pass is a no-op; and the acceptance now names the cross-module check that would have caught this.
  **Known-bad state avoided:** publishing questions where not answering scores full marks and answering correctly does not.
  **KEEP — these worked and must survive re-derivation:**
  - Scheme-agnostic redaction: re-issue whatever ids are present, never parse an ordinal out of an id.
  - Ids re-issued **along the shuffled order**, so the public list reads `ri-0, ri-1, …` ascending. Assigning them along the original order moves the leak into the id sequence instead of removing it.
  - The public half built from an allow-list of known-good fields, not by deleting known-bad ones. An unknown field is not proven safe, so it is not published.
  - FillBlank's refusal doctrine — "redacting half of something is worse than not redacting it" — now extended to the option types rather than dropped.
  - `restoreAnswer(key)` as an executable proof that a key is complete.
  - Injectable randomness, so tests are deterministic across seeds.
  - Mutation-testing the module and reporting which mutants were killed.

## Design Notes

The redacted shape is a different type, not a stripped one. A boolean marker beside `"type":"SingleChoice"` would be swallowed by `ignoreUnknownKeys = true`, and a stripped `SingleChoice` then falls into the legacy branch and dies as `"Unsupported legacy question type"`. A distinct discriminator makes both directions impossible by construction.

Two cross-module assertions belong in the test file, because the module's whole claim is about what another module reads:

```js
const {evaluateAnswer} = require("./assessment-scoring");
const {publicPayload, key} = redact(orderingJson, "HARD", {random: seeded(7)});
const pub = JSON.parse(publicPayload);
// The displayed sequence must not pay full marks.
assert.strictEqual(evaluateAnswer(pub, {type: "ordering", order: pub.items.map((i) => i.id)}), 1);
// And the key must be usable: assembled the way the write path will, the true answer scores 9.
assert.strictEqual(evaluateAnswer(assemble(pub, key), {type: "ordering", order: key.order}), 9);
```

`assemble` is the test's own helper standing in for the future write path — for Ordering it must physically reorder `items` by `key.order`, since a naive object merge leaves them shuffled and scores the true answer 5.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, `question-redaction` among them.
- `cd functions && npm run lint` -- passes with the new module listed.
- `git status --short functions/index.js firestore.rules` -- unchanged by this slice; both belong to a parallel session right now.

## Suggested Review Order

**What makes a redacted question safe**

- Entry point: the four `*Redacted` discriminators. A redacted payload is a different type, not a stripped one, so no scorer or parser can mistake it for a real question.
  [`question-redaction.js:70`](../../functions/question-redaction.js#L70)

- The outcome the caller branches on — refusing to split must not look like having nothing to split, or the write path publishes an answer key.
  [`question-redaction.js:80`](../../functions/question-redaction.js#L80)

- The shuffle that may never return the canonical order: a two-item Ordering would otherwise publish its answer half the time.
  [`question-redaction.js:186`](../../functions/question-redaction.js#L186)

- Ordering has no key field to remove — the array order *is* the answer, so the split is the shuffle plus the id re-issue.
  [`question-redaction.js:330`](../../functions/question-redaction.js#L330)

- The one place an answer survived as plain text after the candidates were shuffled; matched by containment, not equality.
  [`question-redaction.js:361`](../../functions/question-redaction.js#L361)

**What must not change**

- A present-but-empty `difficulty` is a value: treat it as absent and the question silently moves between the easy and hard pools.
  [`question-redaction.js:244`](../../functions/question-redaction.js#L244)

- Whole-or-nothing refusal, and why a list of fewer than two rows is refused rather than published.
  [`question-redaction.js:210`](../../functions/question-redaction.js#L210)

**Putting the halves back**

- The public entry point and its three-way answer.
  [`question-redaction.js:463`](../../functions/question-redaction.js#L463)

- Reassembly lives in the module, not in the test — a naive merge leaves Ordering shuffled and scores the true answer 5 against its own key.
  [`question-redaction.js:582`](../../functions/question-redaction.js#L582)

- A client answers in re-issued ids; this is what turns them back.
  [`question-redaction.js:661`](../../functions/question-redaction.js#L661)

- Pairing check — a key from one question applied to another's half would otherwise be undetectable.
  [`question-redaction.js:549`](../../functions/question-redaction.js#L549)

**Tests**

- Self-registering suite, so a new case cannot be written and then silently never run.
  [`question-redaction.test.js:21`](../../functions/question-redaction.test.js#L21)

- Asserts the default entropy is not `Math.random`, whose state is recoverable from the orders this module publishes.
  [`question-redaction.test.js:701`](../../functions/question-redaction.test.js#L701)
