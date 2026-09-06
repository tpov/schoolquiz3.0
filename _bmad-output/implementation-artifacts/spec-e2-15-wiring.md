---
title: 'E2.15 — The handler starts using the scorer it was built for'
type: 'feature'
created: '2026-09-03'
status: 'done'
baseline_commit: '06b8ea74'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Eight modules can now read a submitted attempt, find the questions it was played against, score it against the stored answer keys and say whether it may be paid for. Nothing calls any of them. The handler that receives attempts still trusts whatever the device claims, and one flag — the digits add up to the claimed percent — decides the charge, the reward, the profile ratings and the tournament write.

**Approach:** The handler reads an attempt through the intake, and when the attempt says the server is its scorer, scores it before the transaction opens and carries the result into it. Nothing about a device-scored attempt changes: same acceptance, same rejections, same payment. This is the switch that makes everything already built start working, and nothing more.

## Boundaries & Constraints

**Always:** A device-scored attempt keeps today's stored shape and today's rejection messages. It does **not** keep today's payment decision in every case: wiring the intake in activates `servedVerified` as a second condition beside `scoreVerified`, and today's client already sends a served list, so this reaches players on the next release. That is the point of the slice, not a side effect — but it must be logged when it bites, so a client bug cannot silently stop paying everyone. Firestore reads all happen before the first write, as the existing code already requires. What is stored for a server-scored attempt has the same shape as for a device-scored one, so everything downstream keeps working unchanged.

**Ask First:** Any change to what a device-scored attempt is paid or charged, or to the meaning of the existing verification flag for it.

**Never:** Do not flip redaction on at publication — that is a later step and this one must be invisible until it happens. Do not change the client. Do not add a dependency. Do not reach into another session's uncommitted work: this slice lives on its own branch.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Today's client | digits and a percent, easy or hard, with a served list | accepted and stored as before; paid when the digits and the served list both hold up. A served list that disagrees is kept, unpaid and logged |
| Server-scored attempt | hard, no digits, a served list | scored from the stored keys; the digits and percent it produces are stored as if the device had sent them |
| Payable | a server-scored attempt that scored cleanly | charged and paid on the same terms a verified device-scored one is |
| Not payable | a server-scored attempt the server could not score | the event is kept, nothing is charged, nothing is paid |
| A lie | a served list that disagrees with the digits | kept and marked, not paid — as the intake already decides |
| No keys | a hard attempt on a lesson whose keys were never stored | not payable, and the reason is visible to whoever looks |
| Batch of fifty | a mix of device- and server-scored attempts in one call | each decided on its own; one unscorable attempt does not affect the others |
| Reads before writes | any batch | every read the scorer needs happens before the transaction opens |

</frozen-after-approval>

## Code Map

- `functions/index.js:841` -- `normalizeLessonResultAttemptEvent(item, uid)` is the call to replace with `readSubmittedAttempt` from `attempt-intake.js`. That module reproduces today's path byte for byte and adds `scoringAuthority`, `served`, `servedVerified` and `paymentRule`; a frozen copy of today's function inside its test suite fails when the original drifts, so **that tripwire is expected to go red here and must be retired as part of this slice.**
- `functions/index.js:852` -- `readAllocatedSeconds(...)` already runs before the transaction, with the comment saying Firestore wants every read first. The key documents and the question documents belong in the same slot, batched by lesson the way that function batches.
- `functions/index.js:430` and `:2058` -- `db.collection("questions").where("lessonId", "==", lessonId)` is how a lesson's questions are read; `questionRowFor` parses the payload, which the scorer does not want — the pool builder takes raw documents.
- `functions/index.js:932-937` -- `lifeCharged = isNew && item.event.scoreVerified`, the one flag that gates the charge, the reward at `:993`, the activity counts at `:945`, and the tournament write. For a server-scored attempt the gate is the intake's `paymentRule` evaluated by `isPayable`, which needs the scoring result.
- `functions/index.js:945` -- `attemptActivityCounts(item.event.codeAnswer)` and `:993` `attemptReward({...})` read `codeAnswer` and `percentScore` off the event. `withServerScore(attempt, scoring)` fills exactly those two from the scoring result, so both keep working with no change.
- The chain to call, all pure and all exported: `attempt-intake.js` `readSubmittedAttempt` → `scoring-pool.js` `buildScoringPool` → `attempt-scoring.js` `scoreAttempt` → `attempt-intake.js` `withServerScore` / `isPayable`. `functions/lesson-round-trip.test.js` runs that chain end to end and is the reference for how the pieces fit.
- `question_keys/{lessonId}` -- one document per lesson; `keys` and `refusals` are lists. Every document written so far is stamped as not-redacted, and the scorer refuses a redacted payload against such a stamp — which is correct and means this slice changes nothing observable until publication starts stamping otherwise.
- `functions/index.js` has no logger import in this branch; a reason a hard attempt could not be scored has nowhere to go unless one is added, as the key store's publication path did.

## Tasks & Acceptance

**Execution:**
- [x] `functions/index.js` -- read attempts through the intake; for a server-scored one, read its lesson's key document and question documents in the existing pre-transaction slot, build the pool, score it, and carry the filled attempt into the transaction. The payment gate consults the intake's rule.
- [x] `functions/attempt-intake.test.js` -- retire the frozen-copy tripwire, which has done its job, and say what replaced it.
- [x] A test covering the Matrix rows that do not need Firestore -- the batch behaviour, the payment decision per kind, and that a device-scored attempt's stored shape is unchanged.

**Acceptance Criteria:**
- Given a device-scored attempt, when it is submitted, then everything stored and everything paid is identical to before this change.
- Given a hard attempt with no digits and a served list, when it is submitted, then it is scored from the stored keys and stored with digits and a percent like any other.
- Given a batch mixing both kinds and one unscorable attempt, when it is submitted, then the others are unaffected.
- Given any batch, when it is processed, then no Firestore read happens after the first write.

## Verification

**Commands:**
- `cd functions && npm test` -- the suites pass; `entity-version.test.js` is red on this branch for a reason owned elsewhere and is expected to stay so.
- `cd functions && npm run lint` -- passes.
- `node -e "require('./index.js')"` -- loads.

## Spec Change Log

- **Finding:** the frozen block claimed a device-scored attempt behaves exactly as today. Wiring the intake in makes `servedVerified` a payment condition, adds size bounds, and refuses a percent without digits — and today's client already sends a served list, so it is live on the next release. The claim was false as written.
  **Amended (frozen block, deliberately):** the Always clause now says what actually changes and requires the case to be logged; the Matrix row says the same.
  **Known-bad state avoided:** a client bug in the served list silently ending payment for every player, with nothing in the logs.
  **Second finding, worse:** a lesson the server could not read produced an all-filler pool, every position filed as the *client's* fault, and therefore a full charge for a 0%. That was my own earlier reclassification meeting this read — right for one invented entry, wrong for a whole lesson. A client can invent a few entries; it cannot make a lesson vanish. All-missing is now the server's fault and unpayable, some-missing stays the client's. The accepted trade: a served list where *every* entry is invented is now uncharged. It buys nothing — unpayable means no reward either — and the alternative charges every player whenever a real read fails.
  **KEEP:** driving the real handler over an in-memory Firestore rather than asserting on source text; the per-lesson read catch that keeps one failed lesson from losing the other forty-nine attempts; and the test runner that reports every suite instead of stopping at the first red one.

## Suggested Review Order

**Who pays when something is missing**

- A client can invent a few served entries; it cannot make a whole lesson vanish. All-missing is ours and unpayable, some-missing is theirs and charged.
  [`attempt-scoring.js:216`](../../functions/attempt-scoring.js#L216)

- "We could not read this lesson" is its own state, never an empty lesson — collapsing the two charged the player full price for a zero.
  [`index.js:451`](../../functions/index.js#L451)

- Private lessons keep their questions elsewhere; reading the public collection for them was the concrete way the collapse happened.
  [`index.js:538`](../../functions/index.js#L538)

**The gate itself**

- The one flag that decides the charge, the reward, the ratings and the tournament write.
  [`index.js:1286`](../../functions/index.js#L1286)

- A replayed attempt keeps the score it was paid for; re-scoring it against today's questions could lower a percent already banked.
  [`index.js:566`](../../functions/index.js#L566)

- The handler is driven for real over an in-memory Firestore. Asserting on source text let the whole composition be deleted with every behavioural case still green.
  [`attempt-handler.test.js`](../../functions/attempt-handler.test.js)
