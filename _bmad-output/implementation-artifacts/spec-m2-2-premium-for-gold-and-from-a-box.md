---
title: 'M2.2 — Premium for gold and from a box'
type: 'feature'
created: '2026-10-11'
status: 'done'
baseline_commit: '3c93ad5f'
review: 'thorough'
review_source: 'pinned'
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
followup_review_recommended: true
deferred:
  - summary: >-
      A gold-quoted premium row shows no term: `days` is parsed and carried but rendered nowhere,
      so the player cannot see what the gold buys before tapping.
    evidence: |-
      The row's title comes from the string resource and its description from the store record
      (spec's own rules); a gold-only row has no store record and the matrix never promised a
      term display. Adding the copy is a product wording decision (what the row should say),
      not a correction this change owes.
    location: >-
      GetShopCatalogUseCase.premium / the shop row rendering
    severity: low
  - summary: >-
      The gold path's catch-up seam (stale premium history forcing the checkpoint-and-retry
      through a gold buy) is not exercised end to end.
    evidence: |-
      The two-phase checkpoint flow is pinned by premium-shop.test.js's buy() helper shape and
      the engine's advance by its own suites; the composite (stale history + gold buy) needs a
      stale-history fixture worth adding when the renewal work next touches this harness.
    location: >-
      functions/premium-shop.test.js (buy harness)
    severity: low
  - summary: >-
      The balance purchase path emits no funnel events, so gold premium purchases never enter
      the purchase analytics.
    evidence: |-
      Pre-existing: the balance path was analytics-less before this story (heart slots ride it
      unpinned too); this story routed a revenue item through it. Instrumenting it properly
      decides where "completed" lives for a synchronous balance purchase — an analytics design
      decision, not a regression of this change.
    location: >-
      DefaultShopComponent.purchase (balance path)
    severity: medium
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

## Review Triage Log

### 2026-10-23 — Review pass (acceptance, fresh four-lens pass over the committed change set at HEAD; 2026-10-11 rows used as prior context)

- verdicts: 21 findings — high 0, medium 3, low 12, false 6, maybe-false 0
- findings:
  - `[low]` `[reject]` [blind] Shelf position contradicts «to the shelf's front» — the premium row sits third (after the currencies, before the packs) by design; the drift is in the spec's Code Map wording, and the fix would edit this build's spec.
  - `[false]` `[reject]` [blind] `ShopMessage.PremiumActivated` referenced but never defined — it is defined and rendered by M2.1's change (ShopMessage.kt:47, ShopView.kt:779), in tree at this story's baseline; the lens saw only this diff.
  - `[low]` `[patch]` [blind] The repository's «Премиум активирован» is a dead copy source — the component unconditionally replaces `purchase.message` with `PremiumActivated`; fixed: the misleading branch removed (falls to the generic else), one copy source remains.
  - `[low]` `[defer]` [blind] A gold-only premium row shows no term (`days` parsed, never rendered) — real, but the matrix never promised a term display and the wording is a product decision; deferred.
  - `[low]` `[patch]` [blind] `PremiumGoldOffer` KDoc claims the device «называет sku обратно» — the purchase carries only `itemId=PREMIUM`; fixed: the KDoc now states the real contract (the server re-resolves the single sellable offer at purchase time).
  - `[false]` `[reject]` [blind] Duplicated offer derivation invites drift — both wrappers sit on the shared `premiumSaleOffer(…, "GOLD")` kernel; they answer different questions (enumerate vs resolve-exactly-one), the invariant lives in the shared helper.
  - `[false]` `[reject]` [blind] The millisecond idempotency window invites a double charge — carried from the prior pass's rejected row: by the model every callable invocation is a purchase; NFR2 covers one invocation's replay, and the same-millisecond double call answers the settled state (pinned by test).
  - `[low]` `[defer]` [blind] The catch-up checkpoint branch never fires in tests (no stale-history fixture) — the flow shape is pinned by the harness and the engine by its own suites; the composite fixture deferred.
  - `[low]` `[reject]` [blind] `premiumGoldOffers: []` in the default JSON is inert — the file is the documented duplication of initial values (read as DEFAULTS); the served list is always derived, and the real knob is `premiumProducts` (Ask First names it).
  - `[low]` `[patch]` [blind] Huge `days` survives parsing clipped to Int.MAX, contradicting the drop-whole rule — fixed: a term outside 1..Int.MAX drops the row whole; a parsing test pins it.
  - `[false]` `[reject]` [blind] No already-premium scenario in the matrix — the grant goes through the premium-economy engine whose lot-stacking semantics are the defined behavior (and M2.3's renewal work pins it); nothing is undefined here.
  - `[low]` `[reject]` [blind] Spec task list still says «functions 100 suites» — the fix edits this build's spec; cosmetic.
  - `[false]` `[reject]` [blind] New `PurchaseRefusalCode` entries have no producer — they round-trip on both ends (server emits them in purchase-verification tests; PurchaseVerificationMappingTest asserts every entry maps from the wire) — verified by the verification-gap lens.
  - `[low]` `[reject]` [blind] The default JSON lost its trailing newline — cosmetic.
  - `[false]` `[reject]` [edge] A single sellable gold offer for a non-30d sku pairs the wrong copy — unreachable within the owner's frozen model: premium_1d/7d/365d are decreed never recreated; the operator's table is the instrument and the decree keeps other skus out.
  - `[low]` `[patch]` [edge] Same row as the huge-days parsing fix above (the edge lens's guard sketch) — grouped with it; one fix.
  - `[low]` `[patch]` [edge] A same-millisecond replay against a changed table escapes as anonymous INTERNAL — `CONFLICTING_REPLAY`/`SOURCE_ALREADY_GRANTED` from premium-grants were outside the callable's translation map; fixed: both map to `failed-precondition` (reachable only in a perfect storm, but the named-refusal contract should not depend on that).
  - `[medium]` `[defer]` [edge] The balance path emits no funnel events — gold premium purchases never enter the analytics; pre-existing hole (the path was analytics-less before this story), instrumentation design deferred; severity honest-graded medium because a revenue path now rides it.
  - `[medium]` `[patch]` [vg] The `applyShopPurchase` callable was never executed by any test (the suite that appears to cover it runs a drifted hand-copy) — fixed: a handler-level test pins the gold buy (debit, response shape, document write) and both named wire refusals (short wallet → `failed-precondition`, non-selling table → `invalid-argument`).
  - `[medium]` `[patch]` [vg] `EconomyRepositoryImpl`'s PREMIUM allowlist adoption is unobserved — reverting it turned every gate green while bricking the gold row client-side; fixed: a data-module test pins `purchase(PREMIUM)` riding the gate to the remote data source (and a non-listed item refused before any call).
  - `[low]` `[patch]` [vg→other] The buy() copy has drifted from the callable body it mirrors — the callable-level test above removes the copy's monopoly on this surface; the copy itself stays as the module harness (its drift noted here).

## Auto Run Result

Status: done — acceptance pass complete; six patch entries applied and verified; three items deferred with recorded evidence; nothing routed back.

### Summary of this pass
- Fresh four-lens thorough review over the story's committed change set — d59a0299 + 4aa0cb30 — verified at HEAD; the 2026-10-11 pass served as prior triage context.
- 21 findings: 0 high, 3 medium, 12 low, 6 false → 6 patched entries, 3 deferred, 10 rejected with recorded refutations.
- Review-fix changes landed with this acceptance: the callable-level test for `applyShopPurchase` (gold buy end to end + both named wire refusals — the surface the module tests only mirrored); the translation map learned `CONFLICTING_REPLAY`/`SOURCE_ALREADY_GRANTED`; impossible `days` now drop a gold-offer row whole instead of surviving clipped; the repository's dead premium copy removed; the `PremiumGoldOffer` KDoc states the real wire contract; the data-module test pins PREMIUM's adoption through the remote-purchase gate.

### Verification performed
- `cd functions && node economy-handler.test.js` — OK (with the new callable test); `node premium-shop.test.js` — 7/7.
- `cd functions && node run-tests.js` at HEAD — 128/129; the one failure is the pre-existing `mvp-isolation` LAN-address objection (unrelated, documented under spec-m2-1's pass).
- `sgw ./gradlew -Dorg.gradle.java.installations.paths=/opt/jdks/jdk-27 :shared:feature:economy:data:allTests :platform:firebase:testDebugUnitTest --no-configuration-cache` — green with the new data-module and parsing tests (first run exposed a pre-existing failure: the epic-8 session's `45f7e9c8` drifted `arena-tickets.js` without re-pinning the fixture SHA — this session's gate did not include `:platform:firebase`; re-pinned test-only per the 2026-10-20 precedent, all 333 payload tests green).
- Full Kotlin gate at HEAD (before patches): BUILD SUCCESSFUL — economy domain/data, billing, presentation, assembleMvp (see spec-m2-1's pass for the same-run record).

### Follow-up review recommendation
- true — two medium entries were patched this pass. Unverified risk: the new callable test pins the response and the gold/hasPremium writes; the full persisted-field set (streakDays, stars, hearts, plasma) is still pinned only by the drifted buy() copy in premium-shop.test.js.

### Residual risks
- Gold premium purchases remain invisible to the purchase funnel until the balance path gets its analytics design (deferred, medium).
- The operator dependency stands: without a single sellable `goldPrice` row in `configs/economy`, the shelf sells premium through Play only (tracked owner task, unchanged).
