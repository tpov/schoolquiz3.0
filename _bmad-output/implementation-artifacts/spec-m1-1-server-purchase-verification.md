---
title: 'M1.1 — The server verifies a Play receipt and credits gold exactly once'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: '295dd04651e6e4b753b0a580a55b9423930667c9'
review_loop_iteration: 0
story_key: '1-1-серверная-проверка-чека'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/specs/spec-monetisation/purchase-verification.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The client can already buy `gold_pack_*` through Play Billing, but nothing on the server turns a purchase token into gold. Anything that credited on the device would be forgeable; anything that consumed before crediting would lose a paying player's money.

**Approach:** One new callable, `verifyPurchase`, that asks the Play Developer API whether the token is real, purchased and for the claimed SKU, then — in one Firestore transaction — credits gold from the server constants table and records the token as settled. The same token presented again returns the same answer and moves nothing. PENDING moves nothing and is not an error. The decision logic, record shapes and API-response reading live in a pure module tested without `firebase-admin`; the callable is thin wiring.

## Boundaries & Constraints

**Always:** Order is verify → credit → confirm; the client consumes afterwards, never the server. Balance change and the settlement record are written in the **same** transaction, keyed on the token (`purchase_settlements/{sha256(token)}`); the gold field is read and written as an absolute value, never `increment`. Gold amounts per SKU exist only in the server constants table (`goldPacks` in `config/economy-constants.json` mirrored into `configs/economy`); `getEconomyConstants` must not return that section. Every settlement writes an audit record naming uid, token id, SKU, gold granted, and `configs/economy` plus its `version`. A receipt is added to `users/{uid}/receipts/{settlementId}` as key + params, not prose. Clock and Play API client are injected. The callable gets its own `maxInstances`, not the global 1. New Node files are pure and covered by a `node`-runnable test in the existing style. Documents are in English; comments follow the surrounding file.

**Ask First:** Any npm dependency beyond declaring the already-present transitive `google-auth-library` explicitly. Any change to `functions/mutation-queue.js`, `FirebaseMutationTransport.kt`, or other files of the sync workstream. Binding a Secret Manager secret at deploy time (it would fail deploy until the owner creates it — use ADC / optional env var instead).

**Never:** Grant on the device or trust any amount from the request. Route through the deferred-mutation receiver. Consume or refund here (refunds are story 1.3; premium/boxes are epics 2/3 — their SKUs are refused as "not sold" for now). Put pack sizes in Kotlin. Write a `private/{uid}/sync_changes` entry for receipts — the client journal consumer only knows catalog node types today.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Happy path | authed uid; token unseen; Play: `purchaseState 0`, `productId == sku`, package matches; sku in `goldPacks` | one tx: `users/{uid}.gold += amount×quantity` (absolute write), settlement `CREDITED`, receipt, audit; response `{status:"CREDITED", sku, goldGranted, gold, settlementId}` | N/A |
| Replay | same token again, any number of times | nothing written; the stored response is returned verbatim | N/A |
| Pending | Play `purchaseState 2` | nothing written; `{status:"PENDING", sku}` | not an error |
| Canceled | Play `purchaseState 1` | nothing written | `failed-precondition` |
| Forged / unknown token | Play API 400/404 | nothing written | `permission-denied` |
| SKU mismatch | claimed sku ≠ Play `productId` | nothing written | `invalid-argument` |
| Unsold SKU | sku absent from `goldPacks` (incl. `premium_*`, `box_single`) | nothing written | `invalid-argument` |
| Token owned by another uid | settlement exists with a different uid | nothing written, no replay leak | `permission-denied` |
| Play API unreachable | network error / 5xx / auth failure | nothing written | `unavailable` (client retries later) |
| No auth | missing `request.auth` | — | `unauthenticated` |
| Constants doc missing | `configs/economy` absent | credit from bootstrap defaults; audit names version `0` | N/A |
| Test purchase | Play `purchaseType 0` | credited normally; audit flags `isTest: true` | N/A |
| Already consumed | Play `consumptionState 1`, settlement missing | refuse — a consumed token with no settlement is not ours to credit | `failed-precondition` |

</frozen-after-approval>

## Code Map

- `functions/purchase-verification.js` -- NEW pure module: `settlementId(token)` and `accountIdFor(uid)` (both sha256 hex; the client mirrors the latter into `obfuscatedExternalAccountId`), `validatePurchaseRequest` (strings only), `readProductPurchase(apiJson)` (normalises `purchaseState`/`consumptionState`/`purchaseType`/`quantity`/`obfuscatedExternalAccountId`; a present-but-unreadable `quantity`/`consumptionState` is `null`, never a guessed default), `purchaseFromLookup(lookup)` (Play outcome → purchase | null | unavailable), `settlementGate(existing, uid)` (owner → credited-state → replay), `decideSettlement({existing, purchase, claimedSku, uid, constants})` → `CREDIT | REPLAY | PENDING | REFUSE{code, reason, reasonCode}`, `settlementRecord`, `receiptRecord`, `auditRecord`, `successResponse`. Owns the `PLAY_OUTCOME_*` vocabulary, `SKU_PATTERN` (one definition, imported by `economy-constants.js`) and the `REASON_*` refusal codes surfaced as `HttpsError.details.reasonCode`. Modelled on `functions/mutation-queue.js` (decision + record factories) and `functions/economy-constants.js` (pure, JSON-backed defaults).
- `functions/purchase-settlement.js` -- NEW firebase-admin-free money path: `settlePurchase({db, playApi, now, uid, payload, readGold, log})` → `{kind: "response", response} | {kind: "refuse", code, reason, reasonCode} | {kind: "unavailable", reason}`. Pre-read gate (replay answered before Play), Play lookup, one transaction (`getAll` settlement + user + constants; absolute gold write; `create` on the settlement as the second double-credit guard; receipt + audit). A transaction that does not commit is logged at error level and reported as `unavailable` — nothing was written. 401/403 from Play and an SKU absent from `goldPacks` are logged at error level with the owner-facing fix. `functions/purchase-settlement.test.js` runs it against a fake Firestore that commits writes only when the transaction body completes.
- `functions/play-developer-api.js` -- NEW thin client: `GoogleAuth` from `google-auth-library` (v10.6.2, already in `node_modules`), scope `androidpublisher`, ADC only (no env-var key path; a `defineSecret` path can come once the owner creates the secret); `GET .../v3/applications/{pkg}/purchases/products/{sku}/tokens/{token}` with a 10 s timeout; 404 and 400 with a token reason (`invalidPurchaseToken`, `purchaseTokenDoesNotMatchProductId`, `productNotOwnedByUser`, `purchaseTokenNoLongerValid`) → `NOT_FOUND`, everything else → `UNAVAILABLE`. Returns `{outcome, status, code, reason}` and never an error message (gaxios messages embed the URL, the URL embeds the token). Package `com.tpov.schoolquiz`. `functions/play-developer-api.test.js` drives it through a fake `auth`.
- `functions/index.js:932` -- `getEconomyConstants` returns `...clientEconomyConstants(constants)`, the whitelist that keeps `goldPacks` on the server.
- `functions/index.js` -- `verifyPurchase` is thin: `requireAuthUid`, build deps (`db`, lazily built Play client, `Date.now()`, `readGold = readEconomyBalance(data).gold`, `logger`), call `settlePurchase`, map the outcome to `HttpsError(code, reason, {reasonCode})` / `unavailable`. `MONETARY_FUNCTION_OPTIONS = {...FUNCTION_OPTIONS, maxInstances: 2}` next to `FUNCTION_OPTIONS`; `ECONOMY_CONSTANTS_DOC` now comes from `economy-constants.js` (single definition). `requireAuthUid`, `readEconomyBalance` unchanged.
- `functions/economy-constants.js` -- `goldPacks` reading (sku → integer ≥ 1; `0`, non-integers, `null`, strings drop to the bootstrap default; an array-shaped section reads as `{}`; well-formed extra SKUs are kept), `clientEconomyConstants` whitelist, `ECONOMY_CONSTANTS_DOC`; `functions/economy-constants.test.js` -- verbatim-defaults test extended, strict-read and whitelist tests, `SKU_PATTERN` identity with the verifier.
- `config/economy-constants.json` -- add `goldPacks` (bootstrap: small 10, medium 60, large 150 — tunable server-side, the point of the table). Kotlin parity test is regex-per-field and ignores unknown keys; do not add the section to `EconomyConstants.kt`.
- `firestore.rules` -- `purchase_settlements/{id}`, `purchase_audit/{id}`: `read, write: if false`; `users/{userId}/receipts/{receiptId}`: owner read, `write: if false`.
- `functions/package.json` -- add `google-auth-library` to dependencies; add new files to `lint` and `test` scripts (shared uncommitted edits by the sync workstream on the same lines — edit in place, do not reformat).
- Read-only: `shared/feature/economy/**`, `platform/billing/**` (client is story 1.2), `functions/mutation-queue.js`.

## Tasks & Acceptance

**Execution:**
- [x] `config/economy-constants.json` -- add `goldPacks` -- single source for both sides.
- [x] `functions/economy-constants.js` + `.test.js` -- read `goldPacks` defensively; extend defaults test.
- [x] `functions/purchase-verification.js` -- pure decisions and records per the matrix.
- [x] `functions/purchase-verification.test.js` -- one test per matrix row plus id hashing and record shapes.
- [x] `functions/play-developer-api.js` -- injectable HTTP client; `functions/play-developer-api.test.js` drives it through a fake `auth` (URL, outcome per status/reason, no token in the result).
- [x] `functions/purchase-settlement.js` + `.test.js` -- the money path executable against a fake Firestore: four writes on credit, none on replay/pending/refusal/unavailable/lost race.
- [x] `functions/index.js` -- `verifyPurchase` callable (thin wiring over `settlePurchase`); whitelist `getEconomyConstants`.
- [x] `firestore.rules` -- the three collections above.
- [x] `functions/package.json` -- dependency + lint/test entries.
- [x] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `1-1-…` → `in-progress`, then `in-review`.

**Acceptance Criteria:**
- Given a verified, unseen token, when `verifyPurchase` runs, then gold, settlement, receipt and audit are written in one transaction and the response names the granted amount.
- Given the same token twice, when the second call runs, then the balance is unchanged and the response equals the first.
- Given a Play answer of pending, when the call runs, then nothing is written and the response is not an error.
- Given a forged token or a mismatched SKU, when the call runs, then nothing is credited.
- Given the constants table changes `goldPacks`, when the next purchase is verified, then the new amount applies and the audit names the new version; earlier audits keep theirs.

## Design Notes

Server-side `acknowledge` after a successful credit is **not** in scope: for consumables the client's `consume` is the acknowledgement, and Play's 3-day auto-refund of unacknowledged purchases is handled by story 1.3 as an ordinary refund. Receipts are written but not yet journalled or read — the story that shows receipts on the device owns the journal entry type.

The `gold` field in the response is the balance at settlement time and is returned verbatim on replay (frozen matrix); it is informational — the client refreshes its balance from the server after `CREDITED` rather than trusting this number (story 1.2). Refusals carry a stable `details.reasonCode`; the client localises by code, never by the English `message`. Purchases are bound to the buyer when Play reports `obfuscatedExternalAccountId`: the client sets it to `sha256(uid)` at purchase time (story 1.2); until every client does, an absent id is accepted. Play API access is ADC only — the function's runtime service account must be granted in Play Console; a key-based path was deliberately not shipped.

## Verification

**Commands:**
- `cd functions && npm run lint && npm test` -- expected: every suite prints OK, including `purchase-verification.test.js`, `purchase-settlement.test.js` and `play-developer-api.test.js`.
- `./gradlew :shared:feature:economy:domain:jvmTest --no-configuration-cache` -- expected: `EconomyConstantsParityTest` still green after the JSON gains `goldPacks`.
- `firebase emulators:exec --only firestore "true"` is optional; rules are checked by reading.

## Suggested Review Order

**Entry point — the money path as one function**

- Everything between auth and the answer; Firestore is a parameter, so the body itself is tested.
  [`purchase-settlement.js:68`](../../functions/purchase-settlement.js#L68)

- A known token is answered before Play is asked: the stored answer is the verification.
  [`purchase-settlement.js:84`](../../functions/purchase-settlement.js#L84)

- One transaction: settlement re-read (race), user, constants → decision → four writes, gold absolute.
  [`purchase-settlement.js:113`](../../functions/purchase-settlement.js#L113)

- `create`, not `set`: a token settled between read and commit fails the transaction, never double-credits.
  [`purchase-settlement.js:134`](../../functions/purchase-settlement.js#L134)

- An uncommitted transaction wrote nothing, so the answer is "retry", not "internal".
  [`purchase-settlement.js:139`](../../functions/purchase-settlement.js#L139)

**The decision (pure)**

- Spec order, refuse on first failure; the amount comes from the table, never from the request.
  [`purchase-verification.js:263`](../../functions/purchase-verification.js#L263)

- Owner first, then state: another account's token gets no answer; a non-credited settlement never replays CREDITED.
  [`purchase-verification.js:235`](../../functions/purchase-verification.js#L235)

- Play named the buyer and it is not the caller — refused; absent id passes (older clients).
  [`purchase-verification.js:276`](../../functions/purchase-verification.js#L276)

- Consumed without a settlement is not ours; unreadable state or quantity refuses rather than guesses.
  [`purchase-verification.js:294`](../../functions/purchase-verification.js#L294)

- Zero or absent in the table is "not sold": the client is never told CREDITED for nothing.
  [`purchase-verification.js:323`](../../functions/purchase-verification.js#L323)

- Play's answer normalised once; absent and malformed fields are different things.
  [`purchase-verification.js:181`](../../functions/purchase-verification.js#L181)

- FOUND with an unreadable body is a retry, not forgery.
  [`purchase-verification.js:212`](../../functions/purchase-verification.js#L212)

- The idempotency key is the token's sha256 — the raw token is stored nowhere.
  [`purchase-verification.js:85`](../../functions/purchase-verification.js#L85)

**Talking to Play**

- ADC only, 10 s timeout; the result never carries a message (gaxios messages embed the tokened URL).
  [`play-developer-api.js:72`](../../functions/play-developer-api.js#L72)

- 404 and 400-with-a-token-reason mean "not ours"; every other failure is a retry — 401/403 logged as an owner action.
  [`play-developer-api.js:101`](../../functions/play-developer-api.js#L101)

**Wiring and the table**

- The callable is thin: auth, deps, `settlePurchase`, outcome → `HttpsError(code, reason, {reasonCode})`.
  [`index.js:1815`](../../functions/index.js#L1815)

- Own instance cap, and the honest reason for it.
  [`index.js:115`](../../functions/index.js#L115)

- `goldPacks` read strictly (integers ≥ 1, array-shaped section ignored); the only place a pack has a size.
  [`economy-constants.js:98`](../../functions/economy-constants.js#L98)

- The table leaves the server as a whitelist — `goldPacks` never reaches the device.
  [`index.js:975`](../../functions/index.js#L975)

- Bootstrap sizes; tunable in `configs/economy` without a release.
  [`economy-constants.json:22`](../../config/economy-constants.json#L22)

**Rules**

- Owner reads own receipts, nobody writes; settlements and audit are closed to clients.
  [`firestore.rules:36`](../../firestore.rules#L36)
  [`firestore.rules:70`](../../firestore.rules#L70)

**Tests**

- Fake Firestore that commits only when the body completes: four writes on credit, none otherwise, lost race.
  [`purchase-settlement.test.js:1`](../../functions/purchase-settlement.test.js#L1)

- One test per matrix row plus ids, record shapes, reason codes, buyer binding.
  [`purchase-verification.test.js:1`](../../functions/purchase-verification.test.js#L1)

- URL, outcome per status and reason, no token in any result.
  [`play-developer-api.test.js:1`](../../functions/play-developer-api.test.js#L1)

- Strict pack reading, whitelist, one shared SKU pattern.
  [`economy-constants.test.js:1`](../../functions/economy-constants.test.js#L1)
