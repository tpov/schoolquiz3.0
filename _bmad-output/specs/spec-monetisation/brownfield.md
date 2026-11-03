# Brownfield

Companion to `SPEC.md` (CAP-8). What already exists, so this work extends it instead of building a
second economy beside it.

## The shop shelf already anticipates most of the intent

`GetShopCatalogUseCase` ships seven items today. Three of them are the ones the owner described,
already modelled and merely switched off:

| Existing item | Price today | State | Relation to this spec |
|---|---|---|---|
| `AD_REWARD_BOX` | 5 `ADS` | `isAvailable = false` | **This is "5 ads per box".** Enable, do not redesign |
| `DONATE_GOOGLE_PLAY` | 0 `EXTERNAL` | `isAvailable = false` | The donation product. Needs SKUs and enabling |
| `NICKNAME_MARKET` | 0 `FREE` | available | The door to the market. Free by design — the names inside cost gold |
| `GOLD_HEART` | 10 `GOLD` | available | Plasma charge. A gold sink already in place. **Shipped price — `spec-charges` CAP-9 replaces it with a 1 / 2 / 3 slot ladder** |
| `STANDARD_HEART_SLOT` | ladder in `NOLICS` | available | Not part of this spec; governed by `spec-charges` |
| `QUIZ_SLOT` | 1000 `NOLICS` | `isAvailable = false` | Not part of this spec |
| `REFERRAL_PROGRAM` | 0 `FREE` | available | Not a purchase |

`ShopCurrency` already carries `NOLICS`, `GOLD`, `ADS`, `EXTERNAL`, `FREE` — the five kinds of
price this spec needs. No new currency is required.

## What was added this week

- `BillingRepository`, `StoreProduct`, `StoreProductId`, `BillingPurchase`, `BillingOutcome` in
  `shared/feature/economy/domain`.
- `PlayBillingRepository` in `platform/billing` — real Play Billing v7: connection handling,
  product query, purchase flow, consume, and re-delivery of unsettled purchases.
- `shared/core/analytics` — the funnel events CAP-9 needs, including `shop_opened`,
  `purchase_started`, `purchase_completed`, `purchase_failed` and `rewarded_ad_completed`.

`StoreProductId` currently lists only the three gold packs. CAP-1 widens it to boxes, premium and
donation tiers.

## The box economy is already implemented, and was not documented

`functions/index.js` carries both halves and neither was written down anywhere else:

- `generateGiftBoxReward` (line 3094) — the full drop table, transcribed into `box-economy.md`.
- `advanceGiftBoxState` (line 3215) with `GIFT_BOX_STREAK_TARGET_DAYS = 10` — the daily-streak
  accrual that grants a box a day from the tenth consecutive visit.

Premium already drops from boxes (a day in the mid tier, a month in the rare tier), so "premium can
drop from a box" is shipped behaviour rather than new scope.

## The referral model exists in legacy

`legacy/.../ReferalRemote.kt` carries `name, icon, allOpenBox, openBoxInSeason` and the author's own
note that a server function should walk referrals and credit the referrer per boxes opened in a
season. That is the mechanism CAP-12 formalises; only the rate is undecided.

## What is missing

- The Cloud Function of `purchase-verification.md`. Nothing verifies a receipt today.
- Products in Play Console. Until they exist `loadProducts` returns an empty list, correctly.
- AdMob. Not wired at all — it needs the owner's app id, and a placeholder id crashes the app at
  startup.
- The market's transfer and commission settlement. `NICKNAME_MARKET` opens a screen; the
  server-side transfer this spec calls for is not implemented.
- Shop and purchase funnel events are defined but not yet emitted. Lesson events already are.

## Inherited contracts this spec must not contradict

- `spec-charges/economy-constants.md` — the server-owned constants table in `configs/{configId}`,
  the currency classification that makes gold monetary, and the rules that a lowered ceiling never
  confiscates and a price change is never retroactive.
- `spec-charges` CAP-3 — the server settles every charge spend. Plasma bought with gold inherits
  that path rather than adding one.
- Existing Cloud Functions `buyStandardHeart` and `buyGoldHeart` in `functions/index.js`, and the
  established `configs/nickname_policy` and `configs/arena_review` pattern for server-owned config.

## One naming note

The product calls its unique names and logos "NFT" in the UI. There is no blockchain: they are
rows the server owns, and `platform/crypto` is an empty stub with two `.gitkeep` files. The name is
branding, not a technical claim — flagged as an open question in `SPEC.md` because it invites Play
policy scrutiny and sets an expectation the product does not meet.
