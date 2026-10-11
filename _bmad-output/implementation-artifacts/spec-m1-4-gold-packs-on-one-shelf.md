---
title: 'M1.4 — Gold packs on one shelf'
type: 'feature'
created: '2026-10-11'
status: 'done'
baseline_commit: '5b0ddeb3'
review_loop_iteration: 1
story_key: '1-4-паки-золота-на-одной-полке'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-2-client-settles-after-confirmation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** the client can buy a gold pack and settle it (stories M1.1–M1.2), but no shelf offers
the packs: `GetShopCatalogUseCase` does not know them, the shop screen has no way to price them, and
`DONATE_GOOGLE_PLAY` is the only external-currency row — locked, with no store behind it. A player
who wants to pay today has no button to press.

**Approach:** the packs become entries of the one existing shelf. The domain learns the three
`ShopItemId` rows and a thin `LoadStoreShelfUseCase` that asks `BillingRepository.loadProducts` for
the store's own records; `GetShopCatalogUseCase` renders pack rows from those records, carrying the
Play price **string** verbatim. The shop component loads the shelf when the shop opens, merges it
with the balance-driven catalog, and routes a pack tap to `BuyGoldPackUseCase` (the settlement path
from M1.2) instead of the gold-priced `PurchaseShopItemUseCase`. When the store cannot be reached or
has not been configured, every pack row says so in words instead of sitting silently locked.

## Boundaries & Constraints

**Always:** the price shown is `StoreProduct.formattedPrice` as the store formatted it — the device
never formats a price from `priceMicros` and never converts currency (NFR4). The device holds no
pack size: how much gold a SKU grants lives in the server's `goldPacks` table only (NFR3); the
fallback row titles name the packs small/medium/large, never an amount. Pack rows are entries of the
existing `ShopCatalogItem` list — no second catalog, no parallel shelf (FR8/CAP-8). Titles and
wording are resolved in the UI from resources (fallback) or from the store's own record; the domain
keeps carrying data, not copy. A pack purchase goes through `BuyGoldPackUseCase` unchanged — same
offline refusal, same buyer tag, same verify→consume→refresh order.

**Ask First:** any change to `functions/`, `platform/billing`, or another feature's module. A new
Gradle dependency. Changing the `PurchaseShopItemUseCase` path or the server constants.

**Never:** compute or display a gold amount for a pack on the client; format `priceMicros` on the
device; store the product list in a database (it is session state — the store re-answers on every
shop open); emit analytics (story 1.5); touch other sessions' uncommitted files (`gradle.properties`,
`local.properties`, `functions/.secret.local`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Shop opens, store answers | `loadProducts` → 3 products | shelf shows 3 pack rows, each with the Play title and the Play price string verbatim; rows available | N/A |
| Shop opens, store dead | `loadProducts` → failure | 3 pack rows still listed, locked, each line explains "Google Play недоступен"; no crash | row state, not silence |
| Shop opens, console empty | `loadProducts` → success, 0 products | rows locked with "товары ещё не выставлены в Google Play" | owner action, named |
| Store answers after balance | products arrive while a balance emission rebuilds items | merge keeps both: packs priced, slot ladder current | last writer wins per field, not per list |
| Pack tapped | available pack row | `BuyGoldPackUseCase(sku)`; row busy while in flight | N/A |
| Credited | `PurchaseOutcome.Credited(goldGranted)` | message names the server-reported amount; balance comes from the refresh, not the field | N/A |
| Pending | `PurchaseOutcome.Pending` | "платёж обрабатывается" — purchase stays in Play's queue | not an error |
| Cancelled | `PurchaseOutcome.Cancelled` | "покупка отменена" — nothing consumed, nothing queued | not an error |
| Refused | `PurchaseOutcome.Refused(code)` | message carries the machine code; nothing consumed | code preserved |
| Offline / no account | `BuyGoldPackUseCase` fails before Play opens | failure reaches the message line via the existing `errorDetail` wording | named reason |

</frozen-after-approval>

## Code Map

- `shared/feature/economy/domain/.../model/ShopItemId.kt` — `GOLD_PACK_SMALL/MEDIUM/LARGE` entries,
  wire names `GOLD_PACK_*` (they are catalog records; the Play SKU stays in `StoreProductId`).
- `shared/feature/economy/domain/.../model/StoreProduct.kt` — `StoreProductId.shopItem(): ShopItemId`
  and `ShopItemId.storeProductId(): StoreProductId?` — the one «запись ↔ Play SKU» correspondence;
  both `when`s live here so epic 2 extends exactly one file.
- `shared/feature/economy/domain/.../model/ShopCatalogItem.kt` — `storeProduct: StoreProduct?`:
  the store's own record for the row (title, description, price string). `null` — the row is not
  sold through the store or the store has not answered yet.
- `shared/feature/economy/domain/.../model/StoreShelf.kt` — NEW sealed `Ready(products)` /
  `NotConfigured` / `Unreachable`.
- `shared/feature/economy/domain/.../use_case/LoadStoreShelfUseCase.kt` — NEW: asks the store for
  all `StoreProductId`s, folds the answer into `StoreShelf`. Empty success = the console has no
  products (NotConfigured); a failure = the store could not be asked (Unreachable).
- `shared/feature/economy/domain/.../use_case/GetShopCatalogUseCase.kt` — `execute(balance,
  storeProducts: Collection<StoreProduct> = emptyList())`; three pack rows after `GOLD_HEART`:
  available only when the store quoted the product, `price = ShopPrice(0, EXTERNAL)` (the renderable
  price travels in `storeProduct.formattedPrice`), `storeProduct` attached.
- `shared/feature/economy/domain/di/EconomyDomainModule.kt` — bind `LoadStoreShelfUseCase`.
- `android/feature/economy/presentation/.../component/ShopViewState.kt` — `storeShelf:
  StoreShelfStatus` (`LOADING/READY/UNAVAILABLE/NOT_CONFIGURED`) so a row can say why it is locked.
- `android/feature/economy/presentation/.../component/ShopMessage.kt` — `GoldCredited(gold)`,
  `PurchasePending`, `PurchaseCancelled`, `PurchaseRefused(code)`.
- `android/feature/economy/presentation/.../component/DefaultShopComponent.kt` — deps
  `loadStoreShelf`, `buyGoldPack`; loads the shelf once per component creation, keeps the products
  for every balance-driven catalog rebuild; routes pack taps to `BuyGoldPackUseCase` and folds
  `PurchaseOutcome` into messages.
- `android/feature/economy/presentation/.../view/NoirShopStore.kt` — pack rows in `DISPLAY_ORDER`
  after `GOLD_HEART`; title = Play title, fallback resource; price label = the Play price string for
  a quoted row, nothing for an unquoted one; state lines explain loading/unreachable/not-configured.
- `android/feature/economy/presentation/.../view/ShopView.kt` — `resolvedText` branches for the four
  new messages.
- `android/feature/economy/presentation/.../di/EconomyPresentationModule.kt` — pass the two new deps.
- `strings.xml` (values, values-ru, values-uk) — fallback pack titles, store-state lines, purchase
  outcome messages. Ukrainian is a legal requirement, not a nicety (NFR8).
- Tests: `GetShopCatalogUseCaseTest` (pack rows priced from the store record, locked without one, no
  gold amount anywhere on the row), `LoadStoreShelfUseCaseTest` (domain, fakes), new
  `DefaultShopComponentGoldPackTest` (shelf merge, purchase routing, outcome messages).

## Tasks & Acceptance

**Execution:**
- [ ] Domain models + `LoadStoreShelfUseCase` + catalog merge + DI.
- [ ] Presentation: state, messages, component routing, store view, strings ×3 locales.
- [ ] Tests: catalog rows, shelf loader, component behaviour.
- [ ] Gates via `sgw ./gradlew` for the touched modules.
- [ ] `sprint-status-monetisation.yaml`: 1-4 → in-progress → review.

**Acceptance Criteria:**
- Given products exist in Play Console, when the shop opens, then the packs are shown as entries of
  the existing shelf with the price string from Play.
- The device holds neither a pack size nor a currency conversion.
- Given the store is unreachable or unconfigured, then the pack rows explain why instead of
  unexplained emptiness.

## Verification

**Commands:**
- `sgw ./gradlew :shared:feature:economy:domain:allTests :android:feature:economy:presentation:testDebugUnitTest --no-configuration-cache`
- `sgw ./gradlew :shared:feature:economy:domain:ktlintCheck :android:feature:economy:presentation:ktlintCheck --no-configuration-cache`
- `sgw ./gradlew :apps:android-next:assembleDebug --no-configuration-cache`

### Review Findings

Code review 2026-10-11, thorough (blind-hunter + edge-case-hunter + verification-gap + intent-alignment).

- [x] [Review][Patch] Uncaught store exception crashed the shop screen [DefaultShopComponent.kt] —
  fixed: the shelf question folds a throwing adapter (`queryProducts` throws `IllegalStateException`
  on a non-OK `queryProductDetails`) into `StoreShelf.Unreachable`; adapter-throw test added.
- [x] [Review][Patch] A future paid record compiled into an unpurchasable row
  [StoreProduct.kt:storeProductId] — fixed: the `when` is exhaustive; a new paid id without a store
  product now fails compilation instead of shipping as a forever-locked row.
- [x] [Review][Patch] Catalog test paired rows and store records by position
  [GetShopCatalogUseCaseTest.kt] — fixed: pairs are matched by id, not by list order.
- [x] [Review][Patch] Offline pack-tap failure path was untested
  [DefaultShopComponentGoldPackTest.kt] — fixed: offline test asserts the `Failure` message reaches
  the message line and Play is never opened.
- [x] [Review][Defer] Console configured partially or empty reads «Google Play недоступен» on every
  pack row; the `NotConfigured` ending is unreachable in production [PlayBillingRepository.kt
  `productsOrFailure`] — deferred: needs an owner decision on adapter semantics (Ask First:
  platform/billing); the console is fully configured today.
- [x] [Review][Defer] Koin wiring of `LoadStoreShelfUseCase` / the `ShopComponent` factory is
  verified by no test [EconomyDomainModule.kt] — deferred: the wiring harness is known-red (it09e);
  add resolution assertions when it09e is fixed.
- [x] [Review][Defer] The rendered shelf (price string, store title, lock lines) has no UI test
  [NoirShopStore.kt] — deferred: needs a compose-ui-test dependency in the module (spec Ask First).
- [x] [Review][Defer] DONATE_GOOGLE_PLAY lock line («waiting for the billing module») is stale next
  to live store rows [NoirShopStore.kt] — deferred: the donate flow is owner intent.
- [x] [Review][Defer] it09e `KoinModuleWiringTest` known-red was documented in sprint status but not
  recorded in deferred-work — deferred: pre-existing, recorded now.

**Rejected**
- READY-but-unquoted row shows «недоступен» — false: `productsOrFailure` fails the whole load when
  any requested SKU is missing, so a Ready shelf has every pack quoted; the `else` line under READY
  is unreachable.
- `StoreShelf.Unreachable` KDoc's «did not return every requested product» has no code behind it —
  false: partial answers arrive as adapter failures, which is exactly that semantic.
- Raw refusal code in the player message — false: the spec matrix prescribes «message carries the
  machine code».
- `GOLD_PACK_*` wire names lack a server counterpart — false: the server settles by `playSku`; wire
  names are client catalog records.
- `title=""`/`description=""` placeholders — false: every catalog row carries empty copy (domain
  holds data, not copy; the view resolves wording).
- Blank `formattedPrice` renders an empty price label — false: Play fills `formattedPrice` for
  returned products; not reachable.
- Credited purchase with a stale balance — false: the balance arrives via the server-refresh flow
  (the M1.2 mechanism, unchanged here).
- «Цены появятся, когда он ответит» over-promises — rejected low: reopening the shop re-asks; an
  auto-retry would add a new dependency.
- Test fakes duplicated instead of shared — rejected low: a restructure with marginal harm.
- Locale terms пак/набір/pack diverge — rejected low: a copy decision; each locale is internally
  consistent.
- Same icon for all three packs — rejected low: design, not a defect.
- TermsConsentGate whitespace edit — false: a deliberate ktlint fix (commit e606c9a4).
