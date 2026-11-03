---
title: 'M1.5 — The shop funnel is visible'
type: 'feature'
created: '2026-10-11'
status: 'done'
baseline_commit: '3f311586'
review_loop_iteration: 1
story_key: '1-5-воронка-магазина-видна'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-2-client-settles-after-confirmation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-4-gold-packs-on-one-shelf.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** the funnel events exist as a contract (`shared/core/analytics`: `ShopOpened`,
`PurchaseStarted`, `PurchaseCompleted`, `PurchaseFailed`) and the Firebase tracker is wired into the
app, but nothing emits any of them. The owner cannot answer "does the shop convert?" — the one
question monetisation exists to answer.

**Approach:** emit where each step actually happens, so the order is guaranteed by construction
rather than by discipline. The shop component emits `ShopOpened` when it is created and
`PurchaseStarted` the moment a pack purchase is about to open Play; the one settlement path
(`SettlePurchaseUseCase`) emits `PurchaseCompleted` only after the server answers `CREDITED` —
which covers both the shop-driven purchase and one the settler recovered after a process death, and
cannot fire before the server fact. The shop component emits `PurchaseFailed` for every non-credit
ending of a shop-driven attempt (closed sheet, server refusal, offline, no account) with a stable
machine reason. Nothing else emits purchase events, so nothing can double-count.

## Boundaries & Constraints

**Always:** the completion event carries the price the **store** reported (`priceMicros`,
`currency` from `BillingPurchase`), never a client-computed amount (NFR4). The failure reason is a
stable code (`cancelled`, a refusal code, `no_network`, `no_account`, `error`), never English text
or a user-facing sentence (AD-15). `Pending` emits nothing: money has not moved, and the settler
emits the completion when it lands. A second caller waiting on an in-flight token never emits —
only the settlement that ran the verification does, so a replay cannot double-count. Analytics
never changes the outcome: the tracker contract says implementations do not throw, and no call site
branches on whether tracking happened.

**Ask First:** any change to `platform/firebase`, `platform/billing`, or `shared/core/analytics`
itself. A second emission point for any of the four events.

**Never:** emit `PurchaseCompleted` from the presentation layer (the server fact belongs to the
settlement path, and a second emitter would double-count recovered purchases). Emit analytics from
Compose screens. Log or send the raw purchase token in an event. Touch other sessions' uncommitted
files.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Shop opens | component created | `ShopOpened` once per component | N/A |
| Pack tapped | available row | `PurchaseStarted(sku)` before Play is opened | N/A |
| Credited, shop-driven | verify → `CREDITED` | `PurchaseCompleted(sku, store price, currency)`, before the caller's result returns | N/A |
| Credited, recovered | settler settles a re-delivered purchase | same event, same path — the funnel does not lose the purchase nobody saw | N/A |
| Waiting caller | second settle of an in-flight token | no event — the owner's settlement already emitted | N/A |
| Pending | `PENDING` from store or server | no event; completion comes later from the settler | not an error |
| Cancelled | player closed the sheet | `PurchaseFailed(sku, "cancelled")` | N/A |
| Server refused | `PurchaseRefusedException(code)` | `PurchaseFailed(sku, code)` | N/A |
| Offline before Play | `SyncFailure(NoNetwork)` | `PurchaseFailed(sku, "no_network")` | N/A |
| Any other failure | exception | `PurchaseFailed(sku, "error")` | never a raw message |

</frozen-after-approval>

## Code Map

- `shared/feature/economy/domain/build.gradle.kts` — add `:shared:core:analytics` (a core contract,
  same level as `:shared:core:network`).
- `shared/feature/economy/domain/.../use_case/SettlePurchaseUseCase.kt` — `analytics:
  AnalyticsTracker` constructor param; on `CREDITED`, track `PurchaseCompleted` with the store
  price from the purchase, inside the settlement that ran the verification.
- `shared/feature/economy/domain/di/EconomyDomainModule.kt` — `analytics = get<AnalyticsTracker>()`
  for the settlement single (resolved at resolution time; the app always binds a tracker, and the
  wiring test keeps requiring one).
- `android/feature/economy/presentation/.../component/DefaultShopComponent.kt` — `analytics` dep;
  `ShopOpened` in init; `PurchaseStarted` before the buy call; `PurchaseFailed` per the matrix.
- `android/feature/economy/presentation/.../di/EconomyPresentationModule.kt` — `analytics = get()`.
- Tests: `SettlePurchaseUseCaseTest` gains a recording tracker (completed exactly on credit, with
  the store price; nothing on pending/refusal/failure/replay); `DefaultShopComponentGoldPackTest`
  gains funnel assertions; `CancelListingTest` passes a no-op; `KoinModuleWiringTest` gains the
  analytics stub where it resolves the settlement.

## Tasks & Acceptance

**Execution:**
- [ ] Domain: analytics dep, settlement emission, DI.
- [ ] Presentation: component emissions, DI, test updates.
- [ ] Gates via `sgw ./gradlew` for the touched modules.
- [ ] `sprint-status-monetisation.yaml`: 1-5 → review.

**Acceptance Criteria:**
- Given the player opened the shop and completed a purchase, then `shop_opened`, `purchase_started`,
  `purchase_completed` arrived in that order.
- The completion event fires after the server confirms the credit, never after Play's answer.
- Given an attempt ended without a purchase, then `purchase_failed` carries a stable reason.
- Conversion is computable from events alone, without extra work.

## Verification

**Commands:**
- `sgw ./gradlew :shared:feature:economy:domain:allTests :android:feature:economy:presentation:testDebugUnitTest --no-configuration-cache`
- `sgw ./gradlew :shared:feature:economy:domain:ktlintCheck :android:feature:economy:presentation:ktlintCheck --no-configuration-cache`
- `sgw ./gradlew :apps:android-next:assembleDebug --no-configuration-cache` (боевой
  `google-services.json` подкладывается на время сборки — в worktree лежит demo-конфиг под MVP)

### Review Findings

Code review 2026-10-11, thorough (blind-hunter + edge-case-hunter + verification-gap + intent-alignment).

- [x] [Review][Patch] `ShopOpened` fired at app start, not at shop open [DefaultRootComponent.kt] —
  fixed: the shop component is built lazily, so its creation (and the event) is the first
  composition of the shop tab; the funnel counts shop sessions, not app launches. A root-component
  test pins factory-not-invoked-at-construction.
- [x] [Review][Patch] Funnel events were tracked inside the `_state.update` lambda
  [DefaultShopComponent.kt] — fixed: events are computed outside the update; a failed CAS retry can
  no longer re-emit an outcome.
- [x] [Review][Patch] `funnelCreditedTokens` was mutated outside the guard mutex
  [SettlePurchaseUseCase.kt] — fixed: the add runs under `guard`, like the in-flight map.
- [x] [Review][Patch] `no_account` reason was unreachable [BuyGoldPackUseCase.kt,
  DefaultShopComponent.kt] — fixed: a typed `NoAccountException` (still an `IllegalStateException`,
  so existing assertions hold) carries the signed-out ending, and `failureReason` names it
  `no_account`; a component test pins the event.
- [x] [Review][Patch] The offline failure path reached the funnel unasserted
  [DefaultShopComponentGoldPackTest.kt] — fixed: the offline test asserts the exact three-event list
  with `no_network`.
- [x] [Review][Patch] The credited test pinned the store price with placeholder zeros
  [SettlePurchaseUseCaseTest.kt, PurchaseFakes.kt] — fixed: the fake takes non-default
  price/currency and the test proves the price is copied, not hardcoded.
- [x] [Review][Patch] The waiting-caller test exercised the sequential replay
  [SettlePurchaseUseCaseTest.kt] — fixed: renamed to what it pins (a replayed token emits once);
  the in-flight-waiter property holds by construction.
- [x] [Review][Patch] The recovered leg asserted no funnel anywhere
  [UnsettledPurchaseSettlerTest.kt] — fixed: the recovery test records the settlement's completion
  event.
- [x] [Review][Defer] An offline failure during verification (money already taken) is funnelled as
  failed and later completed — one purchase, two events — deferred: distinguishing pre-Play from
  post-Play failure needs a domain signal the buy contract does not carry; owner call on funnel
  semantics.
- [x] [Review][Defer] A re-delivered different SKU mispairs `PurchaseStarted` and
  `PurchaseCompleted` per SKU — deferred: reporting the requested SKU would lie about what was
  credited, and token correlation is forbidden by the frozen boundary.
- [x] [Review][Defer] Presentation DI (`analytics = get()`) is resolved by no test — deferred:
  blocked by the known-red it09e harness (recorded under 1-4).

**Rejected**
- ktlint import-ordering fails the story's test files — false: the module gates are green on the
  current tree.
- Missing `AnalyticsTracker` binding crashes resolution — false: production binds the Firebase
  tracker, and the wiring test binds an explicit NoOp stub whose comment states the contract.
- Zero-price completion skews revenue sums — false: emitting the store's own report (zero for
  subscriptions today) is the NFR4 behavior; dropping the event would hide premium conversions.
- Post-restart re-emission double-counts purchases — false as a defect: the code documents it as
  the honest trade («честнее потери»); uniqueness is per process by design.
- `PayableTermsCodec` blank-line removal is drive-by churn — false: the same ktlint drift
  documented in 1-4 (commit e606c9a4).
- Recording tracker duplicated across source sets — rejected low: the presentation source set
  cannot see domain `commonTest` fakes (the fixture gap already deferred under M1.2).
- Unbounded growth of the credited-token set — rejected low: short strings, thousands per process
  lifetime; an eviction policy would add complexity for nothing.
- No sequential-replay test exists — false: the renamed test is exactly that coverage.
- Non-pack store rows emitting the funnel — false by the intent's own principle: emit where the
  step happens; this is the one path that opens Play.
