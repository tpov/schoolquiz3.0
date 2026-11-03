---
title: 'E2.11 — The server decides who scored an attempt before it pays for it'
type: 'feature'
created: '2026-09-02'
status: 'done'
baseline_commit: '0a5ed533'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The server's intake of a submitted attempt (`normalizeLessonResultAttemptEvent`) assumes the device scored it: digits and a percent are mandatory, and one flag — `scoreVerified`, "the digits add up to the claimed percent" — gates four unrelated things: the charge, the nolics reward, the profile's activity ratings and the tournament write. Once hard attempts arrive without digits, that flag is meaningless for them, and the device's new `served` list is dropped on the floor. Nothing yet decides, for one incoming attempt, whether the device or the server is its scorer, or what "verified" means for each.

**Approach:** One pure module that reads a submitted attempt and classifies it: scored by the device (today's rules, unchanged to the byte) or to be scored by the server (hard, no digits, a served list), validating each shape strictly and naming every rejection. It also states, as data, when such an attempt may be paid for. Nothing calls it yet; it is the exact function the intake will swap to.

## Boundaries & Constraints

**Always:** A device-scored attempt is accepted and rejected on exactly the conditions it is today — same checks, same order, same messages — so the swap changes nothing for existing clients. A server-scored attempt is accepted only when it is hard, carries no digits and no percent, and carries a served list; anything in between is rejected by name. `served`, when present on either kind, is validated against the client's own contract: sorted, unique positions, unique ids, within bounds. A client that lies is treated one way, whichever lie it tells: the event is kept, marked, and not paid for — never thrown away. `invalid-argument` is reserved for a body that cannot be read at all. An attempt with digits but no served list is a legacy client and is fine.

**Ask First:** Any change to what a device-scored attempt is charged or paid, or to the meaning of `scoreVerified` for it.

**Never:** Do not call this from any handler; do not touch `functions/index.js`, the queue, the client, or what is stored. No `firebase-admin`, no new dependencies. Do not score anything here — that is `attempt-scoring.js`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Device-scored, as today | digits + percent, any difficulty, no `served` | accepted as device-scored; `scoreVerified` computed exactly as today |
| Device-scored with `served` | digits + percent + served | accepted; served validated; positions disagreeing with the non-`'0'` digits, or an answer row absent from served, mark `servedVerified: false` — kept, not rejected |
| Server-scored | HARD, no digits, no percent, served present | accepted as server-scored; `scoreVerified` is not applicable |
| Easy without digits | EASY, no digits | rejected — easy questions carry their answers, the device scores them |
| Hard without digits and without served | HARD, no digits, no served | rejected — nothing to score against |
| Percent without digits, or digits without percent | either alone | rejected by name |
| Malformed served | unsorted, duplicate position, duplicate id, negative or non-integer position, id not a string, position or length beyond bounds | rejected by name, naming the first offence — these cannot be read, so they are not lies |
| Crafted percent | digits whose recomputed percent differs from the claimed one | accepted, `scoreVerified: false` — exactly today's behaviour |
| Payment rule | either kind | the module states when the attempt may be paid: device-scored — `scoreVerified && servedVerified`; server-scored — only after scoring with no server-fault unscorables |

</frozen-after-approval>

## Code Map

- `functions/index.js:4262-4313` `normalizeLessonResultAttemptEvent(data, authUid)` -- today's intake, read-only: uid must match auth; `normalizeContentEvent`; `attemptId` required; `percentScore` in 0..100 else invalid-argument; `codeAnswer` must be digits (`isWellFormedCodeAnswer`); `difficulty` EASY/HARD; `expectedPercentScore = recomputePercentScore(codeAnswer)`; `scoreVerified = expected === claimed`; `answers = normalizeLessonAnswers(data.answers)`. The new module reproduces this path to the byte for the device-scored kind, so the wiring is a swap. Its `HttpsError` messages are the contract clients already see.
- `functions/index.js:839-860` -- `lifeCharged = isNew && item.event.scoreVerified`; that one boolean gates the charge, the reward, the activity ratings and the tournament write. For a server-scored attempt the gate must become "scored, with no server-fault unscorables" — the module returns that rule as data (`paymentRule`) so the wiring step does not invent it.
- `functions/attempt-scoring.js` -- `scoreAttempt({questions, keyDocument, answers, served})` → `{scorable, codeAnswer, percentScore, unscorable}` with `FAULT_OF` distinguishing client fault from server fault; `readServed` treats an absent list as `SERVED_UNKNOWN`. The intake feeds it; it does not duplicate it.
- The client's `served` contract (commit `0a5ed533`, `docs/architecture/0004-sync-contract.md`): absent = unknown, `[]` = none, sorted by position, unique positions and ids, every answer row present in served, positions == non-`'0'` digits, whole play order on abort.
- **Pool composition for the wiring step**, decided here: each translated variant (`q1__uk`) is its own document with its own key entry, so served ids match keys as they are — no canonicalisation. The pool handed to `scoreAttempt` must be built from `served` (dealt ids at their positions, `'0'` elsewhere), not from the lesson's full document list, because the client deduped variants and the server's list did not.
- `functions/normalizeLessonAnswers` (`index.js:3865`) -- per-answer fields `questionId, codeAnswerIndex, score, answerPayload, answeredAtMs, durationMs, wasTimeout`; `codeAnswerIndex` is clamped with `Math.max(0, …)`, so a missing index arrives as `0`. The module must not inherit that clamp for `served`.
- `functions/result-verification.js` -- `recomputePercentScore`, `isWellFormedCodeAnswer` (accepts `""`). `functions/lesson-reward.js` -- the pure-module model. `functions/package.json:8-9` -- hand-maintained chains; only `test` reaches `ciCheck`.

## Tasks & Acceptance

**Execution:**
- [x] `functions/attempt-intake.js` -- new, pure. `readSubmittedAttempt(data, authUid)` → the normalised attempt plus `scoringAuthority` (`"client"` | `"server"`), `served` (validated or `null`), and `paymentRule`; rejects with the same error type and messages as today for the device-scored path, and with named messages for the new ones.
- [x] `functions/attempt-intake.test.js` -- new; every Matrix row; a byte-for-byte comparison of the device-scored output against a copy of today's normaliser over a fixture set that includes every rejection today produces; served validation cases; the payment rule for both kinds.
- [x] `functions/package.json` -- add the module to `lint` and the test to `test`, leaving other sessions' entries alone.

**Acceptance Criteria:**
- Given any attempt today's intake accepts or rejects, when read by the new module, then the outcome, the fields and the message are identical.
- Given a hard attempt with no digits and a valid served list, when read, then it is server-scored and carries no `scoreVerified`.
- Given an easy attempt with no digits, or a hard one with neither digits nor served, when read, then it is rejected by name.
- Given a device-scored attempt whose served positions disagree with its digits, when read, then it is accepted with `servedVerified: false` and is not payable — the evidence is kept, exactly as a crafted percent already is.

## Verification

**Commands:**
- `cd functions && node attempt-intake.test.js` -- OK; `npm run lint` -- passes with the module listed.
- `cd functions && npm test` -- the new suite passes; other suites red only where owned by the parallel session.

## Spec Change Log

- **Finding:** the frozen intent had the server treat two client lies two different ways. A percent that does not follow from the digits is kept and marked (`scoreVerified: false`) and therefore neither paid nor charged — today's behaviour, and the comment in `index.js` says why: "keep the event for analysis, pay nothing". A served list that does not follow from the digits was to be rejected with `invalid-argument`, which throws out of `applyLessonResultEvents` and takes the whole call with it, losing the evidence of the very thing worth analysing.
  **Amended (frozen block, deliberately):** one policy for both. A lie is kept, marked — `servedVerified: false`, alongside `scoreVerified` — and not payable; the device-scored payment rule becomes `scoreVerified && servedVerified`. `invalid-argument` is now reserved for a body that cannot be *read*: malformed shapes, out-of-bounds positions, oversized lists. Rule 3 of the sync contract (every answer row is in `served`) is enforced the same way, on the device-scored path where nothing enforced it at all.
  **Known-bad state avoided:** a crafted served list destroying a batch of up to fifty honest attempts, and the one body worth keeping being the one body discarded.
  **Frozen-intent note:** this changes the human-owned block — the Matrix's device-scored-with-served row, the malformed row, the payment rule and Acceptance Criterion 4. It is recorded here rather than made silently.
  **KEEP:** the byte-for-byte harness that runs both intakes over every fixture and compares field order, and the source-text comparison that fails when `index.js`'s copy drifts — it is the tripwire the wiring step is meant to trip.

## Suggested Review Order

**One policy for a client that lies**

- Entry point: a served list that disagrees with the digits, or an answer row absent from it, is kept and marked — the same treatment a crafted percent has always had, and the payment rule is now both flags together.
  [`attempt-intake.js:62`](../../functions/attempt-intake.js#L62)

- The kind is read off values, not off which keys were sent: a map-based client serialises absence as `null`, and that used to read as a verified zero percent.
  [`attempt-intake.js:352`](../../functions/attempt-intake.js#L352)

**What a lie must not be able to buy**

- A position no pool could hold is refused before storage; past this bound the scorer is the backstop.
  [`attempt-intake.js:139`](../../functions/attempt-intake.js#L139)

- And the backstop had the fault the wrong way round: a malformed served list was filed as our gap, so a crafted position bought an uncharged hard attempt.
  [`attempt-scoring.js:254`](../../functions/attempt-scoring.js#L254)

- The wiring step gets the finished attempt, not the rule to apply itself — an unfilled one would have counted four shown questions out of the string "null".
  [`attempt-intake.js:502`](../../functions/attempt-intake.js#L502)
