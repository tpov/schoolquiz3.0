---
title: 'M2.1 — Premium is one subscription'
type: 'feature'
created: '2026-10-11'
status: 'done'
baseline_commit: '764221db'
review: 'thorough'
review_source: 'pinned'
review_loop_iteration: 1
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
followup_review_recommended: true
deferred:
  - summary: >-
      A store-backed row (premium, packs) never renders its gold price: PriceLabel returns at the
      storeProductId guard before the gold-price rendering, so in the new gold-offer + unpriced-record
      scenario the row is open for purchase with no price anywhere on the card.
    evidence: |-
      The guard `if (item.id.storeProductId() != null) return` (NoirShopStore.kt PriceLabel) predates
      this diff; story 2.1's row-level survival newly exposes it. Rendering item.price there for GOLD
      rows (packs carry the inert EXTERNAL markup and must stay price-less) belongs to the 2.2
      gold-shelf surface; what would settle the everyday impact: whether the purchase confirmation
      shows the gold cost at tap time.
    location: >-
      android/feature/economy/presentation/.../view/NoirShopStore.kt (PriceLabel guard)
    severity: low
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

### 2026-10-25 — Review pass (the bad_spec re-derivation: an unpriceable subscription keeps the shelf; four fresh lenses over the uncommitted change set)

- verdicts: 20 findings — high 0, medium 5, low 9, false 6, maybe-false 0
- findings:
  - `[false]` `[reject]` [blind] purchase()'s `Unavailable` for a phase-less subscription asserted only in a comment — the SDK-bound wiring cannot run in a JVM test (the module's own doctrine: the SDK does not initialise), the decision is pinned at `preferredSubscriptionOffer`'s boundary (`an offer without pricing phases cannot open the flow`), and the `?: Unavailable` branch predates this diff.
  - `[low]` `[reject]` [blind] «Present, not missing» implemented for subscriptions only; one-time products still drop the row into the whole-query failure, and the not-sold branch is dead for packs — the products-arm failure is the deliberate `productsOrFailure` doctrine (a half-finished console must be named, not hidden), extending row survival to packs is new behavior beyond this story's intent, and the pack arm of the shared branch is defensive dead code with no user-reachable path (a pack record always carries a price or does not come back).
  - `[medium]` `[patch]` [blind] The not-sold wording and the empty price slot are asserted nowhere — deleting or inverting the branch keeps every suite green; fixed together with the vg row below: the store-row explanation decision is extracted into a pure function returning the string resource (the receiptText/EconomyReceiptTextTest pattern) and pinned by tests.
  - `[false]` `[reject]` [blind] Nullable `formattedPrice` leaks "null" into un-audited consumers — the verification-gap sweep found every production consumer inside this diff (NoirShopStore ×2, GetShopCatalogUseCase ×1); any further typed non-null use fails compilation.
  - `[low]` `[reject]` [blind] Unpriced+no-gold row keeps `ShopPrice(0L, EXTERNAL)` — the inert EXTERNAL markup is the established placeholder convention (goldPacks: «только разметка, не число для показа»), never rendered for store-backed rows and never charged; the shape predates the diff.
  - `[low]` `[reject]` [blind] EconomyDomainModule.kt import reorder smuggled in — ktlint import-order compliance in a touched module, zero behavior.
  - `[low]` `[reject]` [blind] The unpriced fixture is copy-pasted three times — per-file fixture helpers are the module's existing idiom (each file already carries its own `product(...)`); centralizing adds shared test surface for three lines.
  - `[low]` `[reject]` [blind] «Прямо сейчас» implies transience — editorial copy choice; the condition genuinely can lift when Play re-lists the offer, and the wording is committed across ×3 locales.
  - `[false]` `[reject]` [blind] Mixed test-naming conventions introduced — GetShopCatalogUseCaseTest names are camelCase throughout (`everyShopItemIdAppearsInTheCatalog`…); the new tests follow each file's own convention.
  - `[low]` `[reject]` [blind] Test name «cannot open the flow» overstates the selection-null assertion — naming taste on a real boundary test; the flow-opening wiring is SDK-bound (row 1).
  - `[false]` `[reject]` [blind] No spec/story documentation of the amended contract — the spec's own matrix already named the cannot-buy row («row says the subscription cannot be bought; flow never opens»), and this workflow records the amendment in this triage log and the Auto Run Result.
  - `[medium]` `[patch]` [edge] stateLine shows «cannot be bought» for a gold-sellable premium row — the new branch consults only the record's price, not the row's availability, so the open Buy card lies in its subtitle; fixed: not_sold fires only for a row that is not available (landed with the row-3 extraction).
  - `[low]` `[defer]` [edge] The gold price is rendered nowhere on a store-backed row — pre-existing guard (`if (item.id.storeProductId() != null) return` in PriceLabel) predates the diff; 2.1's row survival newly exposes it in the gold+unpriced scenario; belongs to the 2.2 gold-shelf surface → deferred to the frontmatter list.
  - `[false]` `[reject]` [edge] An empty-string formattedPrice would render an empty Text and stay sellable — the adapter builds a price only from a real pricing phase's `formattedPrice`; Play does not return empty price strings for a phase, and no program path shown reaches a blank-priced row.
  - `[false]` `[reject]` [edge] goldPacks mirror missing: an unpriced pack record stays buyable — unreachable through the only production adapter: one-time products without offer details drop the row (the productsOrFailure doctrine) and never come back price-less.
  - `[low]` `[reject]` [edge] A priced record with a blank description on a READY shelf falls through to «unreachable» — pre-existing catch-all predating the diff; a priced Play record with an empty description is a console-data edge, the row stays priced and buyable, and the fix adds a branch for negligible everyday impact.
  - `[medium]` `[patch]` [vg] The Play-side priced-vs-unpriced mapping is executed by no test — reverting `toStoreProduct`'s branch to the row-dropping form restores the blanked shelf with every suite green (demonstrated by the lens); fixed: the subscription phase choice lifted into an internal function over plain shapes (the productsOrFailure pattern) with no-offer / phase-less-only / priced tests.
  - `[medium]` `[patch]` [vg] The not-sold wording and the empty price slot are asserted nowhere — fixed with row 3: the explanation decision extracted and pinned (unpriced+unavailable → not_sold; unpriced+available → description/fallback; priced → unchanged fallbacks).
  - `[medium]` `[patch]` [vg] The not-sold wording contradicts the gold sales path — same defect as the edge row above; fixed there.
  - `[low]` `[defer]` [vg] The gold price is invisible while the row is open for purchase — same defect as the deferred edge row; deferred there.
  - [intent-alignment] Descriptive divergences, mapped onto the rows above: the R2-with-R3 reading confirmed (the shelf survives one broken row; no invented price); D2 (a phase-less trial is skipped, the base plan answers — a sale the final model does not name) → false: the spec's own matrix row «Trial not entitled → the first offer answers; the flow still opens» pins exactly this fallback; D3 (cannot-open guaranteed upstream of the tested surface) → row 1; D4 (rendered surface vs state surface) → rows 3/18; D5 (no operator-naming surface beyond the player row) → rejected: the player-facing state names the condition distinctly from unreachable/not-configured, and telemetry is new surface beyond the intent.

## Auto Run Result

Status: done — the 2026-10-23 bad_spec item closed: an unpriceable subscription now keeps its row (price-less) instead of blanking the shelf; the follow-up review pass patched five medium findings and deferred one low.

### Summary of this pass
- Implemented the routed-back bad_spec: `toStoreProduct` keeps a subscription Play knows but quotes no buyable offer for as a row with `formattedPrice = null` (was: dropped row → `productsOrFailure` failed the whole query); `preferredSubscriptionOffer` skips offers without pricing phases (`null` = nothing openable, so the flow never opens — closes the two grouped empty-phases entries too); `StoreProduct.formattedPrice` became `String?`; the premium row sells only via a server gold offer or a priced store record; `NoirShopStore` renders no price placeholder and explains the lock with the new `shop_state_not_sold` string ×3 locales; 7 tests across 5 files.
- Fresh four-lens thorough review (blind-hunter, edge-case-hunter, verification-gap, intent-alignment) over the uncommitted change set: 20 findings — 0 high, 5 medium, 9 low, 6 false → 5 medium patched (two groups), 1 low group deferred, 8 low rejected, 6 false rejected with recorded refutations (see the 2026-10-25 triage pass).
- Review fixes: the not-sold line now fires only on a locked row (a gold-sellable premium row over an unpriced record says the record's words, not «cannot be bought»); the store-row explanation decision extracted into the pure `storeRowExplanation`/`StoreRowExplanation` (the receiptText pattern) and pinned by 5 tests; the priced-vs-unpriced subscription mapping extracted into the pure `subscriptionStoreProduct` (the productsOrFailure pattern) and pinned by 3 tests — reverting the fix's mapping no longer passes green.

### Files changed
- `platform/billing/.../SubscriptionOffers.kt` — phase-less offers skipped in offer selection; `subscriptionStoreProduct`: the subscription row built over plain shapes, price-less when nothing is openable.
- `platform/billing/.../PlayBillingRepository.kt` — `toStoreProduct` delegates the subscription arm to `subscriptionStoreProduct`; `productsOrFailure` KDoc states the returned-price-less-row rule.
- `shared/feature/economy/domain/.../model/StoreProduct.kt` — `formattedPrice: String?` with the price-less-row contract in KDoc.
- `shared/feature/economy/domain/.../use_case/GetShopCatalogUseCase.kt` — premium row available only with a gold offer or a priced record.
- `android/feature/economy/presentation/.../view/NoirShopStore.kt` — price-less rows render no price text; `storeRowExplanation` decides the subtitle (not-sold only on locked rows).
- `strings.xml` ×3 locales — `shop_state_not_sold`.
- `shared/.../di/EconomyDomainModule.kt` — import order only (ktlint).
- Tests: `SubscriptionOffersTest` (+2), `ProductsOrFailureTest` (+1), `SubscriptionStoreProductTest` (new, 3), `GetShopCatalogUseCaseTest` (+2), `LoadStoreShelfUseCaseTest` (+1), `DefaultShopComponentGoldPackTest` (+1), `NoirShopStoreStateLineTest` (new, 5).

### Verification performed
- `cd functions && node run-tests.js` — 128/129; the one failure is `mvp-isolation.test.js` objecting to a LAN address in `mvp_network_security_config.xml` — pre-existing, unrelated (functions untouched by this change set).
- `sgw ./gradlew -Dorg.gradle.java.installations.paths=/opt/jdks/jdk-27 :platform:billing:testDebugUnitTest :android:feature:economy:presentation:testDebugUnitTest :shared:feature:economy:domain:allTests --no-configuration-cache` — BUILD SUCCESSFUL; result XMLs confirm the new suites ran and passed (SubscriptionStoreProductTest 3/3, NoirShopStoreStateLineTest 5/5, plus the +7 from the implementation).
- `sgw ./gradlew … :platform:billing:ktlintCheck :android:feature:economy:presentation:ktlintCheck :shared:feature:economy:domain:ktlintCheck --no-configuration-cache` — BUILD SUCCESSFUL.
- `sgw ./gradlew … :apps:android-next:assembleMvp --no-configuration-cache` — BUILD SUCCESSFUL (assembleDebug/`:apps:android-next:testDebugUnitTest` stay blocked by the mvp-only google-services.json, as recorded in earlier passes).
- detekt: red on `--jvm-target 25` before any sources are read — the pre-existing environmental/plugin failure recorded owner-owned in e25af346 and d9672918; not caused by this change set.
- Matrix test audit: every I/O-matrix row covered by at least one passing test — offer rows by `SubscriptionOffersTest`/`SubscriptionStoreProductTest`, settlement/state/term rows by the functions suites (purchase-verification, premium-purchases), replay/acknowledge survivability by `SettlePurchaseUseCaseTest`, the cannot-buy row by `NoirShopStoreStateLineTest`/`LoadStoreShelfUseCaseTest`/`DefaultShopComponentGoldPackTest`.

### Follow-up review recommendation
- true — five medium entries were patched this pass (first-pass rule: two or more mediums). Unverified risk: the extracted decisions are pinned as pure functions; the SDK-bound purchase wiring (offer-token → `Unavailable`) and the actual Compose rendering of the price-less row stay unexercised without a GMS device (the rig has none — the story's own rig note), and the real `subscriptionsv2` wire answer remains fixture-pinned only.

### Residual risks
- A concurrent session (arena retro workstream) was live in this tree during the pass; its uncommitted files (tournament presentation, platform/firebase, `_bmad-output` retro artifacts) are not part of this change set and were left untouched.
- The deferred gold-price gap: a store-backed row never renders its gold price (pre-existing `PriceLabel` guard), newly visible in the gold+unpriced scenario — belongs to the 2.2 gold-shelf surface (frontmatter `deferred`).
- detekt's environmental red and the mvp-isolation suite failure remain owner-owned environment items.
