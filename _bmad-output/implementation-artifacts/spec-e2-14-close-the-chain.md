---
title: 'E2.14 — Closing the chain: a redacted answer can be scored, and a lost question cannot be free'
type: 'bugfix'
created: '2026-09-02'
status: 'done'
baseline_commit: 'c92351fb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The first test to carry a question through every module found the chain does not join. A player who answers a redacted question correctly scores nothing: the key store stamps every document "the public half was not redacted" as a hard-coded constant, and the scorer refuses any key wearing that stamp. It is a dead end no module could see alone. The same test found the exploit one door down: a served entry naming a question that does not exist is treated as our loss, and our loss means nothing paid **and nothing charged** — so one invented entry buys a free hard attempt.

**Approach:** The stamp becomes a fact the caller states rather than a constant, and the store hands back the public halves it keyed so both come from one call. A question that cannot be found is scored as shown-and-unanswered instead of refusing the attempt — which makes inventing one cost the player rather than pay them, and costs an honest player one question in the rare case we really did lose it.

## Boundaries & Constraints

**Always:** A key must never be applied to a payload it was not made from — that guard stays, it only stops being a constant. The public half a caller publishes and the key it stores come from one `redact` call. A shape a client can send must never make an attempt cheaper than answering honestly would.

**Ask First:** Any change to what an attempt costs or pays beyond the two above.

**Never:** Do not touch `functions/index.js`, the client, or Firestore. Do not weaken the guard into "score it anyway". No `firebase-admin`, no new dependencies.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| The chain closes | a redacted question answered correctly, keyed and published from one call | scored exactly as the unredacted question is |
| The guard still guards | a key applied to a payload from a different call | still refused — this is the leak the stamp exists to stop |
| Backfill keeps its stamp | keys written without publishing a redacted half | stamped as before; a later scorer still refuses them |
| Halves come out together | the store called for a lesson | it returns the public half it keyed for each question, so a caller cannot publish a different one |
| Invented served entry | a served id no document has | that position scores as shown-and-unanswered; the attempt still scores, and the invented entry lowers the percent |
| A genuinely lost question | a document deleted after publication | the same — the position counts against the player, and the loss is recorded for whoever reads it |
| Nothing else moves | every other attempt shape | identical codeAnswer, percent and payability to before |

</frozen-after-approval>

## Code Map

- `functions/question-key-store.js:98` -- `PUBLIC_HALF_REDACTED = false`, a module constant written into every document. It must become a caller-stated fact: publication says the half it publishes is redacted, the catalog backfill says it is not. Default to the safe answer, so a caller that says nothing gets today's behaviour.
- `functions/question-key-store.js` `questionKeyDocuments(questions, options)` -- calls `redact` internally and **discards `publicPayload`**, so no caller can obtain the half that matches the key it stored. It must return them. This is the second half of the same defect: the shuffle is drawn fresh per call, so halves paired by a second call never match.
- `functions/attempt-scoring.js:480` -- refuses a redacted payload whose document says `false`, as `KEY_GENERATION`, a **server** fault. Correct and unchanged; the fix is upstream.
- `functions/attempt-scoring.js` `UNSCORABLE.QUESTION_MISSING` and `FAULT_OF` -- today a server fault, and a server fault refuses the whole attempt. That is the exploit: adding one served entry costs the player nothing and cancels the charge. It becomes a scored position — shown, no valid answer — still recorded in `unscorable` so a real loss is visible to whoever reads it.
- `functions/scoring-pool.js` -- already reports a served question with no document in `missing`, and already keeps its position with a filler; only the scorer's treatment changes.
- **This corrects an earlier decision of my own.** `spec-e2-12-scoring-pool.md`'s change log argued a lost question must refuse rather than become a `'0'` that rewards or a `'1'` that punishes. That weighed fairness to the player and missed the incentive: refusing also cancels the charge, so the shape is worth *more* to a dishonest client than answering. `'1'` costs an honest player one question in a rare case and costs a dishonest one every question they invent.
- `functions/catalog-redaction-plan.js` -- the other caller of the store; it publishes no halves and must keep the old stamp.
- `functions/lesson-round-trip.test.js` -- the test that found both; it currently overrides the stamp by hand to get past the dead end, and must stop needing to. `functions/_seeded-random.js` -- the shared generator the fixtures pin the shuffle with.

## Tasks & Acceptance

**Execution:**
- [x] `functions/question-key-store.js` -- the stamp becomes a caller-stated option defaulting to today's value; the public halves are returned alongside the documents.
- [x] `functions/attempt-scoring.js` -- a question the pool cannot supply scores as shown-and-unanswered and is recorded, instead of refusing the attempt.
- [x] `functions/question-key-store.test.js`, `functions/attempt-scoring.test.js`, `functions/catalog-redaction-plan.test.js`, `functions/scoring-pool.test.js` -- the Matrix rows, and the existing expectations updated where they pinned the old behaviour.
- [x] `functions/lesson-round-trip.test.js` -- stop overriding the stamp; assert the chain closes end to end with no hand-editing of any module's output.

**Acceptance Criteria:**
- Given a redacted question keyed and published from one call, when a correct answer is scored, then the digits equal the unredacted run's exactly, with nothing overridden by the test.
- Given a key applied to a payload from a different call, when scored, then it is still refused.
- Given an attempt with one invented served entry, when scored, then it scores lower than the same attempt without it — never higher, and never free.
- Given every other attempt shape in the existing suites, when scored, then nothing changed.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, including the round trip with no override.
- `cd functions && npm run lint` -- passes.

## Suggested Review Order

- The stamp is now the caller's statement, and only a literal `true` claims the redacted generation. The asymmetry is deliberate: a wrong `false` is refused loudly at scoring time, a wrong `true` marks wrong answers correct in silence.
  [`question-key-store.js:57`](../../functions/question-key-store.js#L57)

- The store hands back the halves it keyed, one per stored key. The shuffle is drawn fresh per call, so halves paired by a second call would never match the keys — a caller could not have got this right on its own.
  [`question-key-store.js:73`](../../functions/question-key-store.js#L73)

- A question the pool cannot supply is the client's fault now, not ours. As ours it refused the attempt, and refusing cancels the charge — so one invented served entry bought a free hard attempt. This reverses a call made in E2.12 and says why there.
  [`attempt-scoring.js:275`](../../functions/attempt-scoring.js#L275)

- The test that found both, and now proves the chain closes with nothing hand-edited.
  [`lesson-round-trip.test.js`](../../functions/lesson-round-trip.test.js)
