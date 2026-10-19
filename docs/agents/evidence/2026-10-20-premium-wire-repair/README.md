# Premium wire repair — 7.4/7.3 night session — 2026-10-20 (UTC)

Samsung A14 (RF8W80MBR3Z) attached to the Mac; demo backend untouched-data preserved
(firestore/auth live in schoolquiz-demo-firebase container via 18090/19090, functions in
schoolquiz-functions-emu via host-network 5001, socat 5002/8080/9099 to the phone).
Account qopc1zUv4U2QSrpYKGgdXeXYbdi8 (the 2026-10-19 gift-claims device account).

## 1. The premiumUntilMs wipe — ROOT-CAUSED, not a product defect

The 2026-10-19 fixture wrote `users/<uid>.premiumUntilMs = +30d` DIRECTLY, but the account
had already been premium-migrated at its first login: `premium_accounts/<uid>` header exists
with `legacyBootstrap = {atMs: 1792402722918, premiumUntilMs: 0}` — read back from the live
demo backend. After migration the user-doc field is a DERIVED projection: every premium
advance recomputes it from header+lots (`premium-economy.js`: `premiumUntilMs =
totalRemainingMs > 0 ? ... : 0`, merged via `userPatch` by upsertUserProfile and every box
op). The entry-pass advance (14.8 min after migration) recomputed it to 0. Live-data probe:
header.asOfMs == users.updatedAtMs at wipe time; nextSequence 0; no lots.

Money-adjacent concern answered: a REAL user cannot lose premium this way — every real grant
path (purchase GRANT, box datePremium, gold shop) issues lots atomically; only direct DB
writes bypass them. The correct fixture is a GRANT lot, not the derived field.

**Proper fixture** (`wire/premium-grant-fixture.js`, issued and verified): runs the server own
pure code in one transaction — readPremiumEconomy → planPremiumEconomy(GRANT source=GOLD) →
writePremiumEconomy + userPatch merge. Result: lot `gold_a34c420f…` 30d, header
totalRemainingMs 2592000000, users.premiumUntilMs = 1795035417005.

## 2. Premium SURVIVES sync now (7.4/7.3 partial proof)

- device/74-home-1.png: after relaunch the entry pass ran (OFFLINE_STREAK→VISIT replays) and
  «Отримати подарунок» renders — premium intact after sync (the exact window that wiped it).
- Server before/after: header advanced (consumed ~81s of premium time), premiumUntilMs
  unchanged. SURVIVAL PROVEN on-device.

## 3. Server wire-contract repair: gift claims now answer `{replayed, result}`

Device finding (2026-10-19): the premium gift claim COMMITTED server-side (box_operations
PREMIUM_DAILY CLAIMED, boxCount 1→2, receipt BOX_ACCRUAL +1) but the button never settled.
Root cause: `submitGiftBoxOperation` returned the FLAT planner body, while the committed
client reader (spec epic-7 line 107: "gift-claim envelopes {replayed, result}",
GiftClaimEnvelopeMappingTest) demands the mutation envelope — every answer failed with
ENVELOPE_INVALID "Missing replay marker" → eternal retry. The server suite pinned the flat
shape (economy-handler.test.js asserted `daily.status`), so both sides were green against
OPPOSITE contracts.

Fix (server side, per the spec contract): `submitGiftBoxOperation` now returns
`{replayed: plan.replayed, result: plan.response}`; `applyBoxOperation` returns the full plan
(`box-service.js`); `openGiftBox` and the `economy.OPEN_GIFT_BOX` mutation handler unwrap
`.response` (outbox `result` body stays flat — the deepEqual "queued = direct recovery" test
now compares flat to flat). Tests updated + new replay pin: `economy-handler.test.js` asserts
`replayed:false` fresh / `replayed:true` + identical body on same-operationId retry.

Gates: functions **124/124** no-env AND **124/124** demo-backend (fresh emulators, post-restart; one arena-lifecycle-views flake under load passed 11/11 alone). Verified served wire:
`wire/wire-probe-answer.txt` — raw HTTP callable answer through auth-emulator token:
`{"result":{"replayed":false,"result":{...flat body...}}}` (outer wrapper is the SDK frame).

## 4. NEW CLIENT FINDING: claim intent never resolves even with the correct envelope

After the fix the entry re-sent the stored intent (operationId 6a2d5cf9, Room
`gift_claim_intents`) and the replay now parses — yet device/74-fresh-2/3.png: the button is
STUCK in «Забираємо подарунок…» (Claiming) across restarts, while the box DID arrive (FAB «2»,
chip «Серія: 10») via profile sync. Device DB pulled via run-as: the PREMIUM_DAILY intent row
(created 20:59:32.004Z) is still present, `mutation_receipts` has NO row for it (only the
morning outbox OPEN_GIFT_BOX), and no local settle/delete happened despite a parsed CLAIMED
replay. So `GiftClaimRepositoryImpl`/`ClaimPremiumDailyGiftUseCase` resolve-path never fires:
neither terminal-success deletion/receipt nor terminal-failure handling after the
ENVELOPE_INVALID era. Suggested next-session focus: the resolve arm of the claim loop
(intent row → receipt/settle), plus its JVM tests (undecidedCallKeepsIntent/
processDeathResumes cover resume but apparently not post-response settle).

## 5. What this proves for the tickets

- 7.4: streak journey PASS (19.10), premium-grant survival PASS (this session), premium gift
  SERVER half PASS (claim committed, receipts, limits live), premium gift CLIENT settle FAIL
  (finding §4) — button journey blocked on the client fix, NOT on the server.
- 7.3: server half PASS (19.10 audit), client surface renders server words (74-home-1),
  regeneration UI device proof still pending (needs the client settle fix first).
- Fixture recipe for future hitl sessions: use `wire/premium-grant-fixture.js <uid> <days>
  <sourceId>` — NEVER write users.premiumUntilMs directly post-migration.

## Housekeeping

- `._*.js` AppleDouble junk (SMB edits) removed from functions/ — they counted as failing
  suites in run-tests.js.
- Demo emulators container restarted (functions reload needed); firestore/auth data survived
  (separate container schoolquiz-demo-firebase).
- arena-lifecycle-views.test.js flaked once under the full demo-backend suite; passes alone
  (11/11) — pre-existing parallel-run raciness against the live emulator, unrelated to this
  change (box paths only).
