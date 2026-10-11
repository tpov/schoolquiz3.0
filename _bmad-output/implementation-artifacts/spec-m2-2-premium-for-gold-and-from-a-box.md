---
title: 'M2.2 — Premium for gold and from a box'
type: 'feature'
created: '2026-10-11'
status: 'review'
baseline_commit: '3c93ad5f'
review_loop_iteration: 0
story_key: '2-2-премиум-за-золото-и-из-коробки'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/mvp-candidate/owner-decisions/owner-decisions-2026-10-10-play-subscriptions.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m2-1-premium-is-one-subscription.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** premium arrives only through Play. The story's other two paths — gold and the box
drop — must reach the player through the same server mechanism as the purchased one, and the gold
price must be a server decision that changes without an app release. The box path already exists:
`gift-box-rewards` grants `datePremium` through the premium-economy engine with `SOURCE.BOX` inside
the box-opening transaction, covered by its own suites. What was missing is the gold path end to end
and the shelf that can price it.

**Approach:** the server's `premiumProducts` table learns a gold price, and everything else follows
from it. The device receives `premiumGoldOffers` (sku, days, goldPrice) in the constants whitelist —
only rows the server would actually sell, so the shelf can quote without holding a price table of
its own (NFR3). Buying is the shop callable's new `PREMIUM` item: `purchasePremiumWithGold` resolves
the single sellable offer from the table *at purchase time*, catches the premium history up,
checks the caught-up balance, grants whole days through the same premium-economy engine
(`SOURCE.GOLD`), debits gold and writes its ledger row — one transaction, a named idempotency key.
The client routes by the row's own words: a gold-quoted premium row goes to the balance path, a
store-quoted one keeps the subscription path of M2.1.

## Boundaries & Constraints

**Always:** the charge uses the table as it stands at the moment the server accepts the purchase —
never the price the shelf drew. Exactly one gold-priced enabled row sells; zero or two sell nothing
(an undecided operator is not resolved by picking the first). The grant is whole days, capped by the
table row, delivered by the same `premium-economy` engine as the Play subscription and the box, with
its audit and receipts. The device never computes or owns a gold price.

**Ask First:** rows in `premiumProducts` (the operator's table — gold sale needs `salesEnabled` and
a positive `goldPrice`). Changes to the `getEconomyConstants` response shape (adding
`premiumGoldOffers` was this story's).

**Never:** grant before the balance check passes; write premium lots outside the transaction;
second-guess the store record on the device; touch owner files (`gradle.properties`,
`local.properties`, `functions/.secret.local`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Shop opens, server quotes one offer | `premiumGoldOffers` = [one] | premium row priced in gold, sellable; the store's record (if any) rides along for description/money price | N/A |
| Table has zero or two sellable rows | any | nothing sells for gold; the row falls back to the store-only rules of M2.1 | fail-closed, no guessing |
| Player taps the gold row | balance ≥ price | gold leaves, whole days arrive, ledger row `premium_gold_purchase`, premium history advances in the same transaction | N/A |
| Player is short | balance < price | `FAILED_PRECONDITION`; no term, no ledger, no user change | named refusal |
| Table lost its offer between shelf and buy | empty offer at purchase time | `INVALID_ARGUMENT` before any account read | named refusal |
| Purchase replayed within the same millisecond | same key | the grant dedupes by its named key; the funds check keeps the second try from paying again | NFR2 |
| Box drops premium | box opening | already implemented: `datePremium` → `SOURCE.BOX` through the same engine | existing suites |

## Code Map

- `functions/premium-shop.js` — NEW: `premiumGoldSaleOffer` (exactly-one resolution),
  `purchasePremiumWithGold` (catch-up → funds check → grant → debit → ledger; free of
  firebase-admin, tested on fakes).
- `functions/economy-constants.js` — `premiumGoldOffers` derivation in `clientEconomyConstants`;
  `config/economy-constants.json` carries the empty section explicitly.
- `functions/index.js` — `applyShopPurchase` accepts `PREMIUM` and routes to the module; the slot
  path is untouched.
- `functions/currency-ledger.js` — `REASON.PREMIUM_GOLD_PURCHASE`.
- `functions/premium-grants.js` — (M2.1 follow-up) the persistence boundary accepts whole-day lots
  outside the catalogue set: a trial's 3-day lot and a box's odd-day grant must survive their first
  re-read.
- Client: `EconomyConstants.PremiumGoldOffer` + `premiumGoldOffers` (bootstrap: none),
  `FirebaseEconomyConstantsRemoteDataSource` parsing (a corrupt row drops out whole),
  `EconomyRepositoryImpl` allowlist, `GetShopCatalogUseCase.premium` (gold price when the server
  quotes exactly one offer; the row moves to the shelf's front), `DefaultShopComponent` routing by
  the row's price currency and the `PremiumActivated` message.
- Tests: `premium-shop.test.js` (offer resolution, happy path, short wallet, no offer, pricelist),
  shape inventories (`currency-ledger`, `economy-constants`), catalog and parsing tests, the
  component routing test.

## Tasks & Acceptance

**Execution:**
- [x] Server: module, callable branch, whitelist, ledger reason, box-path boundary fix.
- [x] Client: model, parsing, catalog, routing, allowlist.
- [x] Tests: functions 100 suites, domain/data/firebase/presentation gates.
- [x] `sprint-status-monetisation.yaml`: 2-2 → review.
- [ ] Owner decision (not code): a `premium_30d` row with `goldPrice` in the demo/production
  `configs/economy` — until then the shelf sells premium through Play only.

**Acceptance Criteria:**
- Given the player has gold, when premium is granted for gold, then it arrives through the same
  server engine as the purchased one, priced by the table at purchase time.
- Given the box dropped premium, then it already arrives that way (`SOURCE.BOX`), same engine.
- The gold price changes on the server without an app release; the device holds no price table.

## Verification

**Commands:**
- `cd functions && node run-tests.js`
- `sgw ./gradlew :shared:feature:economy:domain:allTests :shared:feature:economy:data:allTests :platform:firebase:testDebugUnitTest :android:feature:economy:presentation:testDebugUnitTest --no-configuration-cache`
- `sgw ./gradlew :apps:android-next:assembleMvp --no-configuration-cache`

## Code review 2026-10-11, thorough (blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

- [x] [Review][Patch] The named refusals of the gold buy died unnamed [index.js
  `applyShopPurchase`] — fixed: `purchasePremiumWithGold` throws plain coded errors
  (`FAILED_PRECONDITION` for a short wallet, `INVALID_ARGUMENT` for a table that no longer sells),
  and the callable let them escape as an anonymous INTERNAL; the box path translates its codes, and
  the shop path now does the same. The client's `CallableErrors` branches on `FAILED_PRECONDITION` —
  «недостаточно золота» доехала бы до игрока как серверная авария.
- [x] [Review][Patch] A purchase replayed within the same millisecond crashed instead of replaying
  [premium-shop.js `purchasePremiumWithGold`] — fixed: the engine answers `REPLAYED` for an
  already-issued named key, and the branch treated every non-GRANTED outcome as an impossible
  INTERNAL. The replay now answers the settled state — no second debit, no second lot, one ledger
  row (the matrix's NFR2 row; test pins it).
- [x] [Review][Defer] — none new: the operator's `goldPrice` row in `configs/economy` stays the
  story's tracked owner decision (the unchecked task above); without it the shelf sells premium
  through Play only, by design.

**Rejected**
- A client retry after a successful buy double-charges — false by the model: every callable
  invocation is a purchase; the matrix's NFR2 dedupe covers one invocation's transaction replay and
  (now) the same-millisecond double call, and the funds check bounds an accidental second buy.
- `premiumGoldSaleOffer` sorts SKUs, so the pick is order-dependent — false: exactly one sells, zero
  or two sell nothing; the sort only makes the pricelist deterministic.
- A gold-quoted row ignores the live Play subscription — false: routing by the row's own words is
  the spec's model; the store record rides along for copy, the row's currency decides the path.
- Catch-up charges could drain gold below the price before the check — false: the funds check reads
  the caught-up balance, and the debit is applied only after the grant plan agrees (`GRANTED`).
- A corrupt `premiumGoldOffers` row renders a broken price — false: parsing drops the row whole
  (`mapNotNull` plus the model's `require`), and the shelf falls back to the store record.
