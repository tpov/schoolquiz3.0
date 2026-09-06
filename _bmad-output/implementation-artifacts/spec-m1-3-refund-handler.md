---
title: 'M1.3 — A refund takes back what it paid for, exactly once'
type: 'feature'
created: '2026-09-03'
status: 'in-progress'
baseline_commit: '4614edca4ec9f53ed7dad5c695edd8e6dced2ffc'
review_loop_iteration: 0
story_key: '1-3-обработчик-возвратов-и-отзывов'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `verifyPurchase` credits gold for a Play receipt, and nothing ever takes it back. Google refunds a purchase without asking the app, and Play auto-refunds anything the player never consumed after three days. Today every one of those leaves the gold on the account — a refund is a free gold generator, and the cheapest exploit in the product.

**Approach:** Play's real-time developer notifications arrive on a Pub/Sub topic. A handler decodes each one, and for a void or a cancellation confirms with the Play Developer API that the purchase really is no longer purchased before it takes anything back. The claw-back is one transaction: gold down, the settlement marked refunded, a receipt, an audit record and a ledger row. Gold never goes negative — what cannot be taken back becomes a **shortfall**, which the next credit pays off before any of it reaches the balance.

## Boundaries & Constraints

**Always:** Nothing is taken back on a notification alone — Play must confirm the purchase is not in the purchased state, or the token be unknown to it. A claw-back and everything recording it are one transaction, keyed so a redelivered notification takes back nothing the second time; the settlement record's own state is the guard, not just the message id. Gold is read and written as an absolute value inside the transaction, never `increment`, and is never negative (ADM-19): the unrecoverable remainder is a shortfall on the account, and `settlePurchase` clears it before crediting. Every movement of gold — the credit as well as the claw-back — writes a `currency-ledger` row, so the money side stops being the one place the ledger cannot see. Receipts are a key plus params, never prose. Audit records name the constants document and its version. Decisions and record shapes live in pure modules tested with `node`; the trigger and the transaction are thin wiring. Comments follow the surrounding files (Russian in `functions/`).

**Ask First:** Widening the topic's IAM, or anything that makes the handler reachable other than by Play. Changing how `verifyPurchase` decides a credit. Touching another workstream's uncommitted files (`AppApplication.kt`, `functions/attempt-intake.js`, `push-notifications.js`).

**Never:** Consume, acknowledge or refund anything through the Play API — this handler only reads. Let a claw-back drive gold below zero. Trust the notification body for the amount; the amount is what the settlement record says was granted. Add client code (showing a refund to the player is story 5.4's receipt screen).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Voided, gold still there | settlement `CREDITED` 60; balance 200; Play: not purchased | one tx: balance 140, settlement `REFUNDED`, receipt, audit, ledger row −60; no shortfall | N/A |
| Voided, gold spent | granted 60; balance 20 | balance 0, shortfall 40 recorded | N/A |
| Shortfall then credit | shortfall 40; next verified pack grants 60 | balance +20, shortfall 0, ledger records the full 60 and the 40 cleared | N/A |
| Redelivered notification | same message again | nothing written; the same answer | not an error |
| Two notifications, one void | different message ids, same token | claw-back happens once — the settlement is already `REFUNDED` | not an error |
| Never credited | no settlement for the token | nothing written, recorded as seen | not an error |
| Play disagrees | Play still reports `purchaseState 0` | nothing taken back; logged at error level | not an error |
| Play unreachable | API 5xx / network | nothing written; the message is retried by Pub/Sub | throw |
| Test notification | `testNotification` present | recorded, nothing else | N/A |
| New purchase notification | `oneTimeProductNotification` type 1 | recorded, nothing else — the device presents the token itself | N/A |
| Malformed message | no `data`, bad base64, not JSON, unknown shape | recorded as unreadable, no retry storm | logged, acked |
| Wrong package | `packageName` is not ours | ignored, logged | not an error |

</frozen-after-approval>

## Code Map

- `functions/purchase-notifications.js` -- NEW pure: `decodeNotification(message)` (base64 `data` → object, `null` when unreadable), `notificationKind(...)` → `VOID | PURCHASED | TEST | UNKNOWN`, `tokenOf(...)`, `decideClawback({settlement, purchase, uid, now})` → `CLAW_BACK | ALREADY_REFUNDED | NOTHING_TO_TAKE | PLAY_DISAGREES`, and the record factories `refundSettlementUpdate`, `refundReceiptRecord`, `refundAuditRecord`, `notificationRecord`. Shape and voice: `purchase-verification.js`.
- `functions/purchase-clawback.js` -- NEW money path with `db` injected, mirroring `purchase-settlement.js:68` (`clawBackPurchase({db, playApi, now, message, readGold, log})` → `{kind}`). One transaction: settlement re-read (race), user, constants; absolute gold write; `transaction.set` on the settlement update; receipt under `users/{uid}/receipts`; audit; ledger row.
- `functions/purchase-verification.js` -- add `STATUS_REFUNDED`, `settlementGate` already refuses a non-`CREDITED` settlement (M1.2) — keep that; add `goldAfterClawback(balance, granted)` → `{gold, shortfall}` pure, and `SHORTFALL_FIELD`.
- `functions/purchase-settlement.js:113` -- inside the existing transaction, clear the shortfall before crediting (`granted − shortfall`, floored at zero, remainder stays), and write the `GOLD_PACK` ledger row that is missing today.
- `functions/currency-ledger.js` -- add `REASON.PURCHASE_REFUND`; `currency-ledger.test.js` -- extend the closed-list test.
- `functions/index.js:2186` -- `exports.playPurchaseNotifications = onMessagePublished({...MONETARY_FUNCTION_OPTIONS, topic: PLAY_RTDN_TOPIC}, …)` from `firebase-functions/v2/pubsub`; topic name a module constant (`play-rtdn`), no secret. Reuse `playDeveloperApi()`, `readEconomyBalance`.
- `firestore.rules` -- `purchase_notifications/{messageId}`: `read, write: if false`, beside `purchase_settlements`.
- `functions/package.json` -- the two new modules and their tests in `lint` and `test` (other sessions edit the same lines — append, never reformat).
- Read-only: `platform/**`, `shared/**`, `apps/**` — no client work in this story.

## Tasks & Acceptance

**Execution:**
- [x] `functions/purchase-notifications.js` + `.test.js` -- decoding, kinds and decisions per the matrix.
- [x] `functions/purchase-clawback.js` + `.test.js` -- the money path against a fake Firestore, as `purchase-settlement.test.js` does.
- [x] `functions/purchase-verification.js` (+ test) -- refunded status, shortfall arithmetic.
- [x] `functions/purchase-settlement.js` (+ test) -- clear the shortfall on credit; write the gold-pack ledger row.
- [x] `functions/currency-ledger.js` (+ test) -- the refund reason.
- [x] `functions/index.js` -- the Pub/Sub trigger, thin.
- [x] `firestore.rules`, `functions/package.json`.
- [x] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `1-3-…` → `in-progress`, then `review`.

**Acceptance Criteria:**
- Given a credited purchase Play reports as voided, when the notification arrives, then the gold is taken back and the settlement is marked refunded in the same transaction.
- Given the same notification twice, when the second arrives, then nothing is written.
- Given the player has already spent the gold, when the claw-back runs, then the balance stops at zero and the remainder is recorded as owed.
- Given an account that owes, when the next purchase is verified, then what is owed is cleared first and only the rest reaches the balance.
- Given Play still reports the purchase as bought, when the notification claims otherwise, then nothing is taken back.

## Verification

**Commands:**
- `cd functions && npm run lint && npm test` -- expected: every suite prints OK, including the two new ones.
- Manual: the topic and its subscription do not exist yet, so the trigger is verified by reading and by the module tests; note this in the report.
