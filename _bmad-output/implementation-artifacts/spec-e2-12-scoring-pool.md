---
title: 'E2.12 — Building the pool a submitted attempt is scored against'
type: 'feature'
created: '2026-09-02'
status: 'done'
baseline_commit: 'f08abd11'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `scoreAttempt` needs the questions an attempt was played against, keyed by id and sized so that each served position falls inside it. What the server has instead is a lesson's question documents in Firestore — a different shape, a different membership, and no positions. Nothing builds one from the other, so the handler that will call the scorer would have to invent that translation inline, in a file no test reaches.

**Approach:** A pure module that builds the scoring pool. The served list is the authority on which question sat at which position; the documents supply only the payloads. The client's ordering is never re-derived — reconstructing a sort the device performed against a catalogue that has since moved is how a scorer silently scores the wrong question.

## Boundaries & Constraints

**Always:** Positions come from `served` and nothing else. A question that was served is included whatever its document now says — archived, retitled, moved — because it was put to the player and their answer must still be scorable. A served question whose document is gone is reported, never quietly dropped: dropping it turns "we lost the question" into "the player was not shown it", which is worth more to them than to us.

**Ask First:** Anything that changes how a position is decided, or that scores a question the served list does not name.

**Never:** Do not filter by difficulty, do not dedupe translated variants, do not sort — each is a re-derivation of a client decision that `served` already recorded. Do not call this from any handler, do not touch `functions/index.js`, and do not read Firestore: documents come in as an argument. No `firebase-admin`, no new dependencies.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Ordinary attempt | served names 20 questions, all present | a pool the scorer accepts, sized so every served position is inside it |
| Sparse subset | 20 dealt out of a 30-question lesson | the same — unserved positions need no question, they are `'0'` |
| Archived since | a served question now `archived: true` | included and scorable — it was served |
| Deleted since | a served id with no document | reported by id and position; the rest of the attempt still scores |
| Translated variants | `q1` and `q1__uk` both served | both present, each on its own — no canonicalisation |
| A document not served | a lesson question the attempt never dealt | absent from the pool; its position is `'0'` |
| Wrong lesson | a document whose `lessonId` is not the attempt's | refused — a pool spanning two lessons has meaningless positions |
| Duplicate documents | two documents sharing an id | refused, naming the id |
| Nothing served | an empty served list | an empty pool, and the scorer's own "nothing was shown" outcome |
| Lesson moved on | the documents carry their own generations | built anyway; the generations are reported as context, not as an alarm — `version` is assigned per document path, so a lesson's counter and a question's are unrelated sequences and a difference is expected |

</frozen-after-approval>

## Code Map

- `functions/attempt-scoring.js` `readPool` -- the contract to satisfy: `questions` is a **flat list keyed by `id`**, not by position; a missing or duplicate id, or more than one `lessonId` across the list, is `POOL_MALFORMED`. Each entry needs `id`, `lessonId` and the raw `payload` the scorer parses. `readServed(served, questions.length)` bounds every position by the list's **length**, and the digit string is that long — so the pool must hold at least `maxServedPosition + 1` entries. Positions never served are `'0'` (`NOT_SHOWN`) regardless of what sits there, which is why unserved positions need no question at all.
- `functions/index.js:1909` `questionRowFor(doc)` -- returns `{id, archived, content}` with the payload already parsed; the scorer wants the raw payload, so the builder reads the document fields directly rather than through this helper. `index.js:421` and `:1948` show how a lesson's questions are fetched — `db.collection("questions").where("lessonId", "==", lessonId)` — which is the caller's job, not this module's.
- Public question document fields: `id, lessonId, text, payload, language, languageLevel, order, version, lastModifiedAt, archived`. **No `difficulty` field** — it lives inside `payload`, and this module must not read it: filtering by difficulty is the client decision `served` already recorded.
- `functions/attempt-intake.js` -- supplies the validated `served` (sorted, unique positions and ids, bounded) and the attempt's `lessonId` and `lessonVersion`. The builder trusts the shape and re-checks only what it needs.
- Why no re-derivation: `StartLessonAttemptUseCase` built the client's eligible set by dropping archived questions, deduping translated variants, filtering by difficulty and sorting by `(order, sourceId)`, then assigned `codeAnswerIndex` over the result. Every one of those inputs can have changed since. `served` is that decision, already made and already sent.
- `functions/question-key-store.js` -- the sibling shape for a pure module that returns work plus a record of what it could not do; `functions/lesson-reward.js` -- the house model. `functions/package.json:8-9` -- hand-maintained chains, only `test` reaches `ciCheck`.

## Tasks & Acceptance

**Execution:**
- [x] `functions/scoring-pool.js` -- new, pure. Given a lesson's question documents, the attempt's `lessonId` and its validated `served`, returns the pool `scoreAttempt` accepts plus a record of every served question whose document is missing, and of a lesson-version difference. Refuses a pool that is not a pool, naming why.
- [x] `functions/scoring-pool.test.js` -- new; every Matrix row, and an end-to-end case feeding the built pool into the real `scoreAttempt` and asserting the digits land under the questions `served` names.
- [x] `functions/package.json` -- add the module to `lint` and the test to `test`, leaving other sessions' entries alone.

**Acceptance Criteria:**
- Given a served list and the lesson's documents, when the pool is built and scored, then each digit lands at the position `served` gave that question, whatever order the documents arrived in.
- Given a served question that has since been archived, when scored, then it is scored normally.
- Given a served question whose document is gone, when the pool is built, then it is reported by id and position and the build still succeeds — unlike a wrong lesson or a duplicate id, which refuse. Scoring then refuses the attempt naming that one question: a lost document is our fault, so nothing is paid and nothing charged, and the loss is not turned into a `'0'` that would quietly reward the player nor a `'1'` that would punish them for it.
- Given documents from two lessons, or two documents sharing an id, when the pool is built, then it is refused rather than scored.

## Verification

**Commands:**
- `cd functions && node scoring-pool.test.js` -- OK; `node attempt-scoring.test.js` -- still OK.
- `cd functions && npm run lint` -- passes with the module listed.

## Spec Change Log

- **Finding:** two rows of the frozen block described outcomes the system does not have. "The attempt's other answers still score" is false through the real scorer: a served question with no document is `QUESTION_MISSING`, a **server** fault, and a server fault refuses the whole attempt — which is the established policy and the right one, since scoring the loss `'0'` quietly rewards the player and `'1'` punishes them for our gap. And the version row asked for a drift signal that cannot exist: `version` is assigned per document path, so a lesson's counter and a question's counter are unrelated sequences and a difference is normal.
  **Amended (frozen block, deliberately):** the acceptance criterion now states the real split — the *build* reports a lost document and succeeds, while *scoring* refuses the attempt naming it; and the version row says the generations are context, not an alarm.
  **Known-bad state avoided:** an implementer reading the old criterion would have made a lost question scorable, which is the one outcome that pays for a question nobody could mark.
  **Frozen-intent note:** recorded rather than changed silently.
  **KEEP:** positions taken only from `served`, with no re-derivation of the client's sort, difficulty filter, dedupe or archived check — every one of those inputs can have moved since the attempt was played. And the unservable filler at an id containing `/`, which no Firestore document id can hold, with a bump loop so a crafted `served` entry cannot claim it and convert a forged body into a server fault.

## Suggested Review Order

**Who decides which question sat where**

- Entry point: positions come from `served` alone. The client's sort, difficulty filter, dedupe and archived check are never re-derived — every input to them can have moved since the attempt was played, and re-deriving is how a scorer marks the wrong question.
  [`scoring-pool.js:37`](../../functions/scoring-pool.js#L37)

- Unserved positions get a filler under an id containing `/`, which no Firestore document id can hold — with a bump loop, so a crafted served entry cannot claim it and turn a forged body into a server fault, which is to say an uncharged attempt.
  [`scoring-pool.js:46`](../../functions/scoring-pool.js#L46)

**What is reported and what is refused**

- A served question whose document is gone keeps its position and is named. Dropping it would turn "we lost the question" into "the player was never shown it" — worth more to us than to them.
  [`scoring-pool.js:152`](../../functions/scoring-pool.js#L152)

- A pool spanning two lessons, or holding one id twice, has meaningless positions and is refused rather than scored.
  [`scoring-pool.js:95`](../../functions/scoring-pool.js#L95)

- **Superseded.** The change-log entry above argued a lost question must refuse the attempt rather than become a `'0'` that rewards or a `'1'` that punishes. That weighed fairness to the player and missed the incentive: refusing also cancels the charge, so the shape was worth *more* to a dishonest client than answering honestly — one invented `served` entry bought a free hard attempt. `spec-e2-14-close-the-chain.md` reverses it: the position scores `'1'`, which costs an honest player one question in a rare case and costs a dishonest one every question they invent. The reasoning above is kept because it is where the trade was first weighed, not because it stands.
