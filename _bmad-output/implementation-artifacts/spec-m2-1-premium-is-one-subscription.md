---
title: 'M2.1 — Premium is one subscription'
type: 'feature'
created: '2026-10-11'
status: 'review'
baseline_commit: '764221db'
review_loop_iteration: 0
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
- [ ] Rig (HITL): build on the rig; a sandbox purchase needs Play Store + the license tester on the
  device — report honestly what the rig could and could not verify.

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
