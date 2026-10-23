---
title: 'M2.1 — Premium is one subscription'
type: 'feature'
created: '2026-10-11'
status: 'in-progress'
baseline_commit: '764221db'
review: 'thorough'
review_source: 'pinned'
review_loop_iteration: 1
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
followup_review_recommended: true
deferred:
  - summary: >-
      Revenue analytics carry no product-type dimension, so the subscription funnel this story
      created is not measurable apart from gold packs.
    evidence: |-
      PurchaseCompleted/Failed events already name the sku string; the gap is a deliberate
      product-type dimension — a product decision to make, not a defect this change introduced.
    location: >-
      android/feature/economy/presentation/.../DefaultShopComponent.kt (analytics funnel)
    severity: low
  - summary: >-
      The real subscriptionsv2 wire answer (state names, purchaseType numbering) stays pinned by
      fixtures only.
    evidence: |-
      Confirming it needs a GMS device with a sandbox subscription; redroid has no GMS. Carried
      from the 2026-10-11 pass; this pass added the numeric-state reading test on both arms.
    location: >-
      functions/purchase-verification.js (readSubscriptionPurchaseV2)
    severity: low (unverified)
  - summary: >-
      purchase-refund-order.test.js premium leg answers the products endpoints for a subscription
      token — fixtures a real Play answer would never produce.
    evidence: |-
      The behavioral hole itself is closed (08660b67 admits SUBSCRIPTION_VOID and parks the signal
      durably); the masking fixtures remain worth replacing when story 2.3 wires real premium
      refund corroboration.
    location: >-
      functions/purchase-refund-order.test.js (premium fixtures)
    severity: low
story_key: '2-1-четыре-длительности-за-деньги'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/mvp-candidate/owner-decisions/owner-decisions-2026-10-10-play-subscriptions.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-2-client-settles-after-confirmation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-4-gold-packs-on-one-shelf.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** the story as planned said "four durations", but the owner's final model (owner decision
2026-10-11, Play Console live) replaced it: **one** subscription `premium_30d` — base plan
`premium-30d-autorenew` (monthly auto-renew, 200 UAH, active) with offer `trial-3d` (3 days free,
active). `premium_1d/7d/365d` and the old prepaid base plans are deactivated in Play Console — they
are never shown and never recreated. Nothing in the code knows this product yet: the shelf has no
premium row, settlement asks Play only at `purchases.products`, and the client's only "store, it is
done" gesture is *consume* — which would take the subscription token away from Play and kill the
renewal it is supposed to deliver.

**Approach:** the premium becomes one row of the existing shelf, sold through the settlement path of
epic 1 with the gestures subscriptions actually require. The domain learns `ShopItemId.PREMIUM` ↔
`StoreProductId.PREMIUM_30D` and an `isSubscription` fact; the store adapter opens the flow with the
offer token (the free trial preferred — it is the one offer of the final model) and answers
`acknowledge` for subscription tokens, `consume` for the rest; the server routes subscription SKUs to
`purchases.subscriptionsv2.get`, reads the different answer shape into the same decision form, and
grants premium for the whole days between now and the line item's expiry, capped by the server's own
product table (`premium_30d` → 30). The subscription is never consumed: acknowledgement is the only
"server confirmed" it understands, and the token stays with Play for the renewals.

## Boundaries & Constraints

**Always:** the price on the shelf is Play's own string — the recurring phase (the last one) of the
chosen offer, never formatted from `priceMicros` on the device (NFR4). The device holds no duration:
the term lives in Play's line item, the cap lives in the server's `premiumProducts` table (NFR3).
`premium_30d` is the only subscription of the final model; `premium_1d/7d/365d` are deactivated
products that must not appear on the shelf and must not be recreated. Grace period counts as having
the right (Play itself keeps the entitlement on); a term that has already passed grants nothing. The
purchase settles through the epic-1 path unchanged — same verify→acknowledge→refresh order, same
idempotent replay, same audit.

**Ask First:** changes to the server's `configs/economy` document (the `premiumProducts` row for
`premium_30d` must exist for routing and the cap — an operator's data change, not a code change).
Any new Play Console product. Renewal, cancellation or refund *notifications* (RTDN, story 1.3) —
this story settles the buy gesture only.

**Never:** consume a subscription token; hold a second catalogue or a parallel premium path (FR8);
grant premium from `purchaseTime` or any client-supplied duration; show a deactivated SKU; touch
other sessions' uncommitted files (`gradle.properties`, `local.properties`, `functions/.secret.local`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Buy with trial available | offer list has a free first phase | flow opens with that offer token; shelf price = its recurring phase ("200 ₴") | N/A |
| Trial not entitled | Play returns only the base plan offer | first offer answers; the flow still opens, price unchanged | named row, not silence |
| No offers at all | Play returns an empty offer list | row says the subscription cannot be bought; flow never opens | `Unavailable`, reason named |
| Settlement, subscription SKU | `premium_30d` in the server table | server asks `subscriptionsv2`, single line item carries the term | N/A |
| State ACTIVE / IN_GRACE_PERIOD | term readable | credit: whole days `floor((expiry-now)/day)`, capped at the table's 30 | N/A |
| State PENDING | payment not captured | `PENDING` outcome; nothing granted, nothing consumed | not an error |
| Term already passed | `expiry ≤ now` | refusal `SUBSCRIPTION_NOT_ACTIVE`; nothing granted | terminal refusal |
| Term unreadable / 0 or >1 line items / line item SKU mismatch | subscription answer without one matching line item | refusal `PREMIUM_EXPIRY_UNREADABLE` or SKU-mismatch; no guessing | terminal refusal |
| Replay of the same token | settled already | same saved answer, zero movement (epic-1 replay) | N/A |
| Acknowledge fails after credit | store unreachable at hand-back | survivable: Play re-delivers the token, the server replays `CREDITED`, the loop lands the gesture | logged, not shown as failure |
| Old product name arrives | `premium_7d` claimed against the deactivated console | server's table has no row → `SKU_NOT_SOLD`; the client never lists it | refusal |

## Code Map

- `shared/feature/economy/domain/.../model/ShopItemId.kt` — `PREMIUM` shelf row (one, not four).
- `shared/feature/economy/domain/.../model/StoreProduct.kt` — `PREMIUM_30D("premium_30d",
  isSubscription = true)`; `isSubscription` is the one fact the hand-back gesture needs; the
  «запись ↔ Play SKU» `when`s extend here.
- `shared/feature/economy/domain/.../repository/BillingRepository.kt` — `acknowledge(token)`: the
  subscription's "server confirmed" gesture; the contract keeps `consume` for packs only.
- `shared/feature/economy/domain/.../use_case/SettlePurchaseUseCase.kt` — `handBack`: acknowledge
  for `isSubscription` products, consume otherwise; both failures survivable by the same
  re-delivery loop.
- `platform/billing/.../SubscriptionOffers.kt` — NEW: the offer reduced to plain shapes (SDK types
  do not survive JVM tests); `preferredSubscriptionOffer` prefers the free first phase (the trial),
  falls back to Play's first offer, `null` = cannot be opened.
- `platform/billing/.../PlayBillingRepository.kt` — subscriptions asked in their own
  `ProductType.SUBS` query and merged into one shelf answer; the flow carries the chosen
  `offerToken`; tokens learned as subscriptions at buy time are acknowledged, never consumed.
- `functions/play-developer-api.js` — `getSubscriptionPurchaseV2` (`purchases.subscriptionsv2.get`),
  same outcome dictionary as products.
- `functions/purchase-verification.js` — `readSubscriptionPurchaseV2` folds the different answer
  into the product decision shape (state reduced to purchased/pending/cancelled, grace = purchased);
  `subscriptionExpiryForSku` reads the term off the single matching line item;
  `subscriptionTermDays` quantizes to whole days inside the table cap; new refusal reasons
  `SUBSCRIPTION_NOT_ACTIVE`, `PREMIUM_EXPIRY_UNREADABLE`.
- `functions/purchase-settlement.js` — routing by the class the server's own table gives the SKU:
  subscription SKUs go to the subscription endpoint, the rest to products; a SKU listed in *both*
  tables (a corrupted config) goes to the old products endpoint, where a gold-shaped token keeps its
  established retryable race protections — the decision itself is still made inside the transaction.
  (The routing read's `.data` must be *called*; the multi-quantity race test caught a bare `.data`.)
- `functions/premium-grants.js` — durations are any positive whole days now (a trial's 3-day lot is
  legal); the closed {1,7,30,365} set stays with the product table where it guards the offered SKUs.
- Presentation: `ShopMessage.PremiumActivated` ("Premium is active" ×3 locales), star icon and
  `shop_item_premium` row title, description from the store's own record.
- Tests: `SubscriptionOffersTest` (trial wins, discount is not a trial, empty = closed),
  `SettlePurchaseUseCaseTest` (acknowledge for subscriptions, consume for packs, replay),
  `UnsettledPurchaseSettlerTest` (acknowledged token leaves the queue), functions tests
  (`purchase-verification.test.js`, `premium-purchases.test.js`, `box-service.test.js` fixtures now
  answer `subscriptionsv2` for premium SKUs).

## Tasks & Acceptance

**Execution:**
- [x] Domain: `PREMIUM` row, `isSubscription`, `acknowledge` in the contract, `handBack` routing.
- [x] Store adapter: SUBS query, offer token, acknowledge.
- [x] Functions: subscriptionsv2 lookup, term-based grant, tests.
- [x] Presentation: premium row, messages, strings ×3 locales.
- [x] Gates via `sgw ./gradlew` for the touched modules; functions `node run-tests.js`.
- [x] `sprint-status-monetisation.yaml`: 2-1 → review.
- [x] Rig (HITL): APK installed and the shop shows the premium row explaining the unreachable
  store (`docs/agents/evidence/2026-10-11-premium-subscription/`); a sandbox purchase needs a
  GMS/Play device — redroid cannot open a Play flow, that part stays with the owner's device.

**Acceptance Criteria (as re-scoped by the owner's final model):**
- Given `premium_30d` exists in Play Console with its base plan and trial offer, when the player
  buys it, then premium arrives for the term Play states, through the epic-1 verification path, with
  the price shown as Play's own string.
- The subscription token is acknowledged, never consumed; the renewal keeps its token.
- Deactivated durations are not shown and cannot be settled.

## Verification

**Commands:**
- `cd functions && node run-tests.js`
- `sgw ./gradlew :shared:feature:economy:domain:allTests :shared:feature:economy:data:allTests --no-configuration-cache`
- `sgw ./gradlew :platform:billing:testDebugUnitTest :android:feature:economy:presentation:testDebugUnitTest :apps:android-next:testDebugUnitTest --no-configuration-cache`
- `sgw ./gradlew ktlintCheck detekt --no-configuration-cache` (touched modules)
- `sgw ./gradlew :apps:android-next:assembleDebug --no-configuration-cache`

## Code review 2026-10-11, thorough (blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

- [x] [Review][Patch] Every real `subscriptionsv2` answer read as an unreadable state
  [purchase-verification.js `readSubscriptionPurchaseV2`] — fixed: REST serializes the proto-enum as
  its NAME (`"SUBSCRIPTION_STATE_ACTIVE"`), the reader asked `intOrNull` and got `null`;
  `subscriptionStateFromWire` reads both the name and the number, and an unreadable value is now the
  named `PURCHASE_STATE_UNKNOWN` refusal instead of a false «purchase was canceled» — a possibly
  active subscription is not a canceled one. Fixtures across the suites switched to the wire form.
- [x] [Review][Patch] `SUBSCRIPTION_STATE_UNSPECIFIED` refused as «canceled» — fixed: the
  proto-default carries no information about the player's money; it is refused as
  `PURCHASE_STATE_UNKNOWN` like any unreadable state (test extended to the number and the name).
- [x] [Review][Patch] A license-tester sandbox subscription lost its test mark
  [purchase-verification.js] — fixed: `subscriptionsv2` numbers `purchaseType` differently (test = 1,
  products = 0) and the reader hard-coded `isTest: false`; the field is read, and the settlement,
  audit rows tag sandbox buys as tests.
- [x] [Review][Patch] A live tail shorter than a day refused a paying player
  [purchase-verification.js `subscriptionTermDays`] — fixed: the matrix refuses only at
  `expiry ≤ now`; a tail of hours quantizes to one day instead of `SUBSCRIPTION_NOT_ACTIVE`. The
  table cap still bounds the grant.
- [x] [Review][Patch] Unreadable economy constants left the settlement with no verdict to name
  [purchase-settlement.js] — fixed: the routing read is guarded (same shape as the fence read); a
  broken table yields `UNAVAILABLE` — no endpoint can be chosen without it, the client retries later.
  Test: constants get failing → unavailable, zero writes, no transaction.
- [x] [Review][Patch] A one-time product token could land in the subscription memory
  [PlayBillingRepository.kt] — fixed: only `ProductType.SUBS` query results feed
  `subscriptionTokens`; a pack token there would be acknowledged instead of consumed and stuck in
  the queue forever.
- [x] [Review][Patch] An offer with empty phases crashed the price [PlayBillingRepository.kt
  `priceOf`] — fixed: `lastOrNull() ?: return null`; an unpriceable offer is a row without a price,
  not a fallen shelf.
- [x] [Review][Patch] «Золото придёт» — ложь ожидающей подписке [DefaultShopComponent.kt,
  ShopMessage.kt, ShopView.kt, strings ×3 locales] — fixed: a pending subscription says premium
  activates when Play confirms it (`PremiumPending`), not that gold arrives.
- [x] [Review][Patch] The acknowledged gesture was invisible in the settler fake
  [UnsettledPurchaseSettlerTest.kt, SettlePurchaseUseCaseTest.kt] — fixed: acknowledged tokens get
  their own book (a swapped hand-back gesture must be visible in what the fake learned), and the
  verify→acknowledge→refresh order is pinned by the journal.

**Defer**
- [Review][Defer] The real `subscriptionsv2` wire answer (state names, `purchaseType` numbering) is
  pinned by fixtures only — confirming it against a live Play answer needs a GMS device with a
  sandbox subscription; redroid has no GMS. Stays with the owner's device (the story's rig note).

**Rejected**
- Unknown subscription state should be `UNAVAILABLE`, not a refusal — false: the answer IS readable,
  its state is just not one this server knows; the refusal is terminal but leaves no settlement
  record, so a later re-presentation after a server update re-decides with fresh Play data.
- The min-1-day tail grant lets one token be settled day after day — false: the token replays to the
  same saved answer; a greater expiry is a renewal with its own lot (M2.3's gate).
- `purchaseState: null` breaks `decideSettlement`'s switch — false: the default branch names it
  (`PURCHASE_STATE_UNKNOWN`), refusal with no movement.
- The constants guard hides real corruption forever — false: `UNAVAILABLE` is the retryable
  outcome by design; refusing would be terminal for a config problem the owner can fix in a minute.
- `ShopMessage` exhaustiveness broken elsewhere by the new case — false: compile gate green; the
  only rendering `when` is `resolvedText`, extended.
- APK gate: `assembleDebug` has no client in `google-services.json` (mvp-only json, applicationId
  `com.tpov.schoolquiz.mvp`) — environmental, not a story defect; the gate that runs in this tree is
  `assembleMvp` (green). `:apps:android-next:testDebugUnitTest` is blocked by the same json.

## Review Triage Log

### 2026-10-23 — Review pass (acceptance, fresh four-lens pass over the committed change set at HEAD; 2026-10-11 rows used as prior context)

- verdicts: 25 findings — high 0, medium 4, low 11, false 10, maybe-false 0
- findings:
  - `[false]` `[reject]` [blind] Operator dependency (configs/economy premiumProducts row) undocumented — the same change set's sprint-status-monetisation note carries it («до игрока доедет с документом configs/economy — см. epic-2»), and the spec's Ask First names it.
  - `[false]` `[reject]` [blind] Renewal interim gap not in deferred-work — M2.3 landed (d3591c6a) before any release; the interim state never reached a player; the wire-form defer row exists.
  - `[low]` `[reject]` [blind] Spec I/O matrix drifted behind review patches (min-1-day tail, PURCHASE_STATE_UNKNOWN) — real drift, but the fix edits the frozen human-owned intent block; the divergence is documented in the 2026-10-11 review section.
  - `[low]` `[reject]` [blind] `durationMsForDays` has no upper bound (days × DAY_MS past MAX_SAFE_INTEGER) — days is server-owned (operator table, term capped by the table); needs a >273,000-year value; fix adds a guard.
  - `[false]` `[reject]` [blind] Token-gesture insurance hole (cold `subscriptionTokens` set) — unreachable: the domain routes handBack by `isSubscription` unconditionally; adapter `consume()` only ever receives non-subscription tokens.
  - `[false]` `[reject]` [blind] `queryProducts` throws past the Result transport — DefaultShopComponent.kt:453-461 catches IllegalStateException by design (comment names queryProductDetails); the claimed unguarded scope.launch does not exist.
  - `[false]` `[reject]` [blind] `PurchaseOutcome.Credited.productId` ignored by the message — decideSettlement refuses a purchased SKU that mismatches the claimed one; credited product equals tapped product on every credited path.
  - `[low]` `[patch]` [blind] Numeric subscription states (ACTIVE/IN_GRACE_PERIOD) have no test — fixed: `testANumericSubscriptionStateIsReadLikeItsName` pins 2/4/1 alongside the names.
  - `[low]` `[bad_spec]` [blind] Empty-phases offer selected by the first() fallback while the price drops the row — grouped with the shelf-wide failure below (same root cause: the unpriceable subscription row).
  - `[false]` `[reject]` [blind] New server refusal codes need client parsing — reasonCode parsing exists only in quest-authoring's wire; settlement refusals reach the client as gRPC codes it already branches on.
  - `[low]` `[defer]` [blind] No product-type dimension in revenue analytics — real product-level gap; a product decision, not a defect of this change.
  - `[low]` `[reject]` [blind] Rig evidence noise and a stale suite count — cosmetic, committed artifacts, no everyday impact; fix rewrites historical artifacts.
  - `[false]` `[reject]` [blind] Story bookkeeping never closed — this pass performs the closing.
  - `[false]` `[reject]` [blind] `subscriptionExpiryMs: null` a dead field — it is the unified decision form's expiry slot; the products arm's null is intentional and the subscription arm fills the real term (premium-purchases.test.js:127).
  - `[medium]` `[patch]` [edge] handBack gestures outside the try their KDoc promises — a throwing binder call after the credit failed a settled purchase and skipped the balance refresh; fixed: the gesture moved inside the try, the fake gained `acknowledgeThrows`, and a test pins the throwing shape.
  - `[low]` `[reject]` [edge] Missing premium row routes to TOKEN_UNKNOWN, not SKU_NOT_SOLD — the designed Ask-First interim; refusal naming only, player outcome identical (refused; Play auto-refunds an unsettled token); fix adds a branch.
  - `[medium]` `[bad_spec]` [edge] One unpriceable subscription (no offers, or empty pricing phases) blanks the whole shelf — `toStoreProduct` drops the row and `productsOrFailure` (PlayBillingRepository.kt:499-510) fails the entire query on the missing id, contradicting the spec's own claim «an unpriceable offer is a row without a price, not a fallen shelf» and the matrix row «row says the subscription cannot be bought; flow never opens». Routed to the external implementer: row-level degradation + the cannot-buy row + strings ×3 + tests.
  - `[false]` `[reject]` [edge] Subscription token reaches consume() before the set is warmed — same refutation as the cold-set finding above; the domain's routing makes the path unreachable.
  - `[low]` `[defer]` [edge] `purchaseType` read with intOrNull may miss a name-form wire value — carried from the 2026-10-11 pass's deferred wire-form item; if true, tester buys settle unmarked (audit label only).
  - `[low]` `[bad_spec]` [edge] Only-offer-with-empty-phases opens the purchase flow — grouped with the shelf-wide failure below (one fix: the row without a price that cannot open).
  - `[false]` `[reject]` [edge] Result transport removed from queryProducts (deletion) — same refutation as the throw finding: the component catches the throw by design, comment-documented.
  - `[low]` `[reject]` [edge] Claim «grants whole days between now and expiry» vs max(1, …) — deliberate, review-patched and test-pinned (5f592ffa: a live tail of hours refused a paying player); the «fix» would edit the frozen matrix text.
  - `[low]` `[defer]` [vg] Refund-path fixtures answer products endpoints for a subscription token — the behavioral hole was already contained by 08660b67 (SUBSCRIPTION_VOID admission + durable parking), as the lens itself verified; the masking fixtures stay deferred until 2.3 wires real corroboration.
  - `[medium]` `[patch]` [vg] No test pins the PremiumPending branch — fixed: a component test pins a pending subscription to `PremiumPending`, closing the silent regression path to the «gold arrives» lie.
  - `[medium]` `[patch]` [vg→other] handBack throw shape uncovered (the fake returned failure, never threw) — same fix as the handBack row above; the throwing survivability is now pinned.
  - [intent-alignment] Descriptive divergences, mapped onto the rows above: D2 (wire answer pinned by fixtures) → carried defer; D3/D7 (operator table decides routing and deactivation) → false (Ask-First by design); D4 (matrix floor() vs max(1,…)) → reject (frozen text); D5 (tokens learned at refresh, not buy) → false (unreachable); D6 (NFR4 price extraction untested in SDK-bound code) → grouped with the empty-phases bad_spec entry; D8 (shared-code edits vs «path unchanged») → the intended reading (a trial's 3-day lot must survive both boundaries).

## Auto Run Result

Status: in-progress — acceptance pass complete; one bad_spec item routed back to the external implementer; patch-class fixes applied and verified here.

Blocking condition: bad_spec — a subscription whose only offer has no pricing phases (or no offers at all) drops out of the shelf query, and `productsOrFailure` (PlayBillingRepository.kt:499-510) fails the whole shelf on the missing id, contradicting the spec's own patch claim «an unpriceable offer is a row without a price, not a fallen shelf» and the matrix row «row says the subscription cannot be bought; flow never opens». Re-derivation belongs to the implementer: row-level degradation of an unpriceable subscription, the cannot-buy row, strings ×3 locales, tests.

### Summary of this pass
- Fresh four-lens thorough review (blind-hunter, edge-case-hunter, verification-gap, intent-alignment) over the story's committed change set — e86e4e6f + 72dec423 + 3c93ad5f + 5f592ffa — verified at HEAD aaf82ac7; the 2026-10-11 pass served as prior triage context.
- 25 findings: 0 high, 4 medium, 11 low, 10 false → 3 patched entries (handBack throw survivability; PremiumPending pin; numeric-state fixtures), 1 bad_spec entry sent back, 3 deferred, 10 rejected with recorded refutations.
- Review-fix changes landed with this acceptance: SettlePurchaseUseCase.handBack (gesture inside the try the KDoc promises) + `acknowledgeThrows` fake arm + throwing-survivability test; the PremiumPending component test; the numeric subscription-state test.

### Verification performed
- `cd functions && node purchase-verification.test.js` — OK (with the new numeric-state test).
- `cd functions && node run-tests.js` at HEAD — 128/129; the one failure is `mvp-isolation.test.js` objecting to a LAN address (`192.168.31.218`) present in `mvp_network_security_config.xml` — added by HITL device work after these stories; pre-existing, unrelated to this change set.
- `sgw ./gradlew -Dorg.gradle.java.installations.paths=/opt/jdks/jdk-27 … :shared:feature:economy:domain:allTests :shared:feature:economy:data:allTests :platform:billing:testDebugUnitTest :android:feature:economy:presentation:testDebugUnitTest :apps:android-next:assembleMvp --no-configuration-cache` — BUILD SUCCESSFUL at HEAD (7m 11s); domain + presentation re-run green after the patches (1m 50s).
- Environment note: buildSrc demands `jvmToolchain(27)`; JDK 27 lives at `/opt/jdks/jdk-27`, which Gradle does not scan — the installations-path flag is required on this machine.

### Follow-up review recommendation
- true — two medium entries were patched this pass. Unverified risk: survivability of a throwing hand-back is pinned only by fake-based tests; a real binder throw on a GMS device stays unexercised (the rig has no GMS — the story's own rig note).

### Residual risks
- The bad_spec shelf item: until the implementer re-derives it, a Play-side catalogue anomaly for premium can blank the whole shop shelf (all rows Unreachable) until Play's answer normalizes.
- The operator dependency stands: without the `premium_30d` row in `configs/economy`, premium buys refuse (documented, epic-2 owner task).
