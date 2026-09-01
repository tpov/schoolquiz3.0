---
title: 'E2.8 — The server scores a whole attempt from the stored key'
type: 'feature'
created: '2026-09-01'
status: 'in-progress'
baseline_commit: 'e71dc41c'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every piece of server-side scoring now exists and none of them are joined up. `assessment-scoring.js` can score one answer against one question; `question-redaction.js` can put a public half back together with its key and translate a submitted answer out of the reissued ids; `question-key-store.js` stores the keys. Nothing turns a player's submitted attempt plus a lesson's stored keys into the two numbers the rest of the server already runs on — the codeAnswer digit string and the percent.

**Approach:** One pure module that does exactly that, and nothing else. It is the piece the submit handler will call once the client stops sending a score of its own. Nothing calls it yet, so it can be got right in isolation, against the fixtures that already pin every part it composes.

## Boundaries & Constraints

**Always:** The digits it produces must be the same digits the existing scorer produces for the same answers — one implementation of the formula, composed, never re-derived. A reissued id is translated back exactly once; translating twice, or not at all, scores an ordering or fill-blank question wrongly rather than failing. An answer that cannot be scored must be reported as such, never guessed at and never silently counted as wrong.

**Ask First:** Anything that changes what a digit means, what `'0'` means in a codeAnswer, or how the percent is derived.

**Never:** Do not call this from any handler. Do not touch `functions/index.js`, the client, the Kotlin scorer, or what is stored today. Do not import `firebase-admin`. No new npm dependencies.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| A whole attempt | answers to a lesson's questions, with keys for each | a codeAnswer whose digits match what the existing scorer gives for each answer, and the percent derived from it |
| Position | answers arriving in any order | each digit lands at its own question's `codeAnswerIndex`, not at the order it arrived in |
| Not shown | a question in the pool the player never reached | `'0'` at that position — "not shown", which is what the percent already excludes |
| Ordering and fill blank | an answer submitted in reissued ids | translated back once and scored against the restored question; the same answer must not score differently from the unredacted case |
| Key missing | an answer whose question has no key | reported as unscorable, naming the question — never scored as wrong |
| Key mismatched | a key whose question id is not the one being scored | refused, not applied |
| Unredacted question | a lesson published before redaction, answered normally | scored from the payload as it stands, with no key needed |
| Nothing answered | an attempt with no answers at all | an all-`'0'` codeAnswer and a percent of zero, the same as a lesson nobody played |

</frozen-after-approval>

## Code Map

- `functions/assessment-scoring.js` -- `evaluateAnswer(content, answer)` → 1..9, `computePercentScore(codeAnswer)`, `computeStars`, `scoreDigit`. Read-only; this module composes it and must not restate any of its arithmetic. It is pinned to `Scoring.kt` by `shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json`, which both languages read.
- `functions/question-redaction.js` exports `restoreContent(publicPayload, key)` — the reassembly that physically reorders an Ordering's items, since a naive merge leaves them shuffled and scores the true answer 5 against its own key — and `translateSubmittedAnswer(answer, key)`, which maps a client's `ri-`/`rc-` ids back. Also `KEY_VERSION`, `REFUSAL` and `STATUS`. Both are the once-only step the Always clause is about.
- `functions/question-key-store.js` -- the stored document: `{id, lessonId, version, publicHalfRedacted, keys: [{questionId, key}], refusals: [{questionId, index, reason, detail}]}`. Keys and refusals are **lists**, because publication writes with merge and merge unions maps. Note `publicHalfRedacted` is `false` for everything written so far: those keys describe a shuffle that was never published, so a key must not be applied to a payload that was not redacted from the same call.
- `functions/index.js:3865` `normalizeLessonAnswers` -- what an answer looks like when it arrives: `questionId`, `codeAnswerIndex`, `score`, `answerPayload` (a `UserAnswer` JSON string), `answeredAtMs`, `durationMs`, `wasTimeout`. `codeAnswerIndex` is the position this question holds in the attempt's digit string.
- `functions/index.js:406` `attemptActivityCounts` and `:3033` `recomputePercentScore` -- the two existing consumers of a codeAnswer, for the shape this module must produce.
- `UserAnswer` wire shape: discriminator `"type"`, kebab-case — `single-choice`, `multiple-choice`, `ordering`, `fill-blank`, `survey` — while `QuestionContent`'s is PascalCase. Two namespaces under one key; reusing one set scores everything 1.
- `functions/lesson-reward.js` -- the model for a pure, `firebase-admin`-free module; `functions/package.json:8-9` -- hand-maintained `lint` and `test` chains, and only `test` reaches `ciCheck`.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/attempt-scoring.js` -- new; given a lesson's questions, its key document and an attempt's answers, produce the codeAnswer, the percent, and a record of anything unscorable. Pure, composing the existing scorer and the existing reassembly rather than restating either.
- [ ] `functions/attempt-scoring.test.js` -- new; every Matrix row, plus the property that a redacted question scored through the key gives the same digit as the same answer against the unredacted original.
- [ ] `functions/package.json` -- add the module to `lint` and the test to `test`, leaving other sessions' entries alone.

**Acceptance Criteria:**
- Given a lesson and an attempt, when it is scored, then every digit equals what `evaluateAnswer` gives for that answer and that question, and the percent equals `computePercentScore` of the assembled string.
- Given the same answers to the same questions, when one lesson is redacted and the other is not, then both score identically.
- Given an answer whose key is missing or belongs to another question, when the attempt is scored, then it is named as unscorable and no digit is invented for it.
- Given a question nobody reached, when the attempt is scored, then its position is `'0'` and the percent ignores it.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, `attempt-scoring` among them.
- `cd functions && npm run lint` -- passes with the new module listed.
- `git status --short functions/index.js` -- untouched by this slice.
