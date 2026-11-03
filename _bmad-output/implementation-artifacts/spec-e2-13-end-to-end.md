---
title: 'E2.13 — One test that plays a redacted lesson from publication to payment'
type: 'feature'
created: '2026-09-02'
status: 'done'
baseline_commit: 'c92351fb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Seven server modules now stand between a published question and a paid attempt, and every one is tested on its own or against one neighbour. No test carries a question through all of them. The whole epic rests on a single claim — a player who answers a redacted question correctly is scored exactly as if it had never been redacted — and nothing asserts it end to end. The handler that will join these modules is the first place that claim would be tested, in a file no test reaches.

**Approach:** One test that plays a lesson: publish it through redaction, store the keys the way publication stores them, take the attempt the device would send, build the pool, read the intake, score it, and check the player got what they earned. It calls the real modules only — no reimplementation, no Firestore — so it is a proof about the shipped code, not about a model of it.

## Boundaries & Constraints

**Always:** Every step goes through the real exported function. The scores it asserts are written down as literals, never derived from the code under test. The redacted run and the unredacted run of the same lesson and the same answers must agree exactly — that equality is the epic's claim and the reason this test exists.

**Ask First:** Any behaviour difference the chain reveals between a redacted and an unredacted question — that is the thing this test exists to catch, and it is a finding, not something to normalise away.

**Never:** Do not modify any module to make the chain fit; a mismatch is a finding to report. Do not touch `functions/index.js`, the client, or anything Firestore. No `firebase-admin`, no new dependencies.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| The claim | one lesson, one set of answers, played twice — once redacted, once not | identical codeAnswer and identical percent |
| Every shape | single choice, multiple choice, ordering, fill blank in one lesson | each scores what a hand-written table says it should |
| Reissued ids | the player answers an ordering and a fill blank in the ids they were shown | scored correctly — the translation back happens exactly once |
| The shuffle is real | the redacted lesson as published | the order shown differs from the answer, and the answer is not recoverable from the public half alone |
| A survey among them | one survey in the lesson | published unredacted, no key, scored on participation |
| Partial play | the player abandons after some questions | the served list is the whole play order; unreached positions score as shown-and-unanswered |
| Payment | the honest run | the attempt is payable; the same run with a lie in it is not |
| A lost question | one document deleted between publication and scoring | the attempt is refused, naming it, and nothing is paid |

</frozen-after-approval>

## Code Map

- The chain, in order, all pure and all exported: `question-redaction.js` `redact` → `question-key-store.js` `questionKeyDocuments` → `scoring-pool.js` `buildScoringPool` → `attempt-intake.js` `readSubmittedAttempt` → `attempt-scoring.js` `scoreAttempt` → `attempt-intake.js` `withServerScore` / `isPayable`.
- **The untested links** are the ones to build around: nothing joins the key store to the pool builder, and nothing carries a question from `redact` all the way to a score. `attempt-scoring.test.js` reaches back to `redact` and `question-key-store`; `scoring-pool.test.js` reaches forward to `scoreAttempt` and `attempt-intake` — the two halves meet nowhere.
- `redact(payloadJson, difficulty, {random, questionId})` → `{status, publicPayload, key, ...}`; a deterministic `random` is needed so the assertions can be literals — `functions/_seeded-random.js` is the shared generator both suites already use.
- `questionKeyDocuments(questions)` → `{documents, refusals}`, one document per lesson, `keys`/`refusals` as **lists**, `publicHalfRedacted: false` — note that constant: these keys describe a shuffle that was never published, so the test must publish the same `redact` output it keys, and say so.
- `buildScoringPool({lessonId, lessonVersion, served, documents})` → `{built, questions, missing, ...}`; positions come from `served`, and a document is `{id, lessonId, payload}`.
- `readSubmittedAttempt(data, authUid)` → the normalised attempt plus `scoringAuthority`, `served`, `servedVerified`, `paymentRule`; a hard attempt with no digits and a served list is server-scored.
- `scoreAttempt({questions, keyDocument, answers, served})` → `{scorable, codeAnswer, percentScore, unscorable}`; `FAULT_OF` splits client fault from server fault, and a lost question is a server fault that refuses the attempt.
- `UserAnswer` wire shape is kebab-case (`single-choice`, `multiple-choice`, `ordering`, `fill-blank`, `survey`) while `QuestionContent`'s is PascalCase — the fixture answers must use the answer namespace.
- `functions/question-redaction.test.js` and `functions/scoring-pool.test.js` -- the house test style, and the fixtures worth reusing rather than reinventing. `functions/package.json:8-9` -- hand-maintained chains, only `test` reaches `ciCheck`.

## Tasks & Acceptance

**Execution:**
- [x] `functions/lesson-round-trip.test.js` -- new; one lesson of four question shapes plus a survey, carried through the whole chain, with every expected digit and percent written down as a literal. Every Matrix row.
- [x] `functions/package.json` -- add the test to `test` and `lint`, leaving other sessions' entries alone.

**Acceptance Criteria:**
- Given one lesson and one set of answers, when played redacted and unredacted, then the codeAnswer and the percent are identical.
- Given the published redacted lesson, when only the public half is available, then the correct ordering and the correct candidates cannot be read off it.
- Given an honest attempt, when it reaches the end of the chain, then it is payable and the percent matches the written-down table.
- Given a question whose document is lost after publication, when the attempt is scored, then it is refused naming that question and nothing is payable.

## Verification

**Commands:**
- `cd functions && node lesson-round-trip.test.js` -- OK.
- `cd functions && npm test` -- the chain test runs with the rest; `npm run lint` -- passes with it listed.
