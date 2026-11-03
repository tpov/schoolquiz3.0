# Paid catalogue

Companion to `SPEC.md` (CAP-1, CAP-3, CAP-4, CAP-8). Every product sold for real money, and the
shop entry each one attaches to.

Prices are **not** in this table and never in the client. Play owns the money price; the server
owns the gold price and the grant. What is fixed here is the SKU, the product type and the effect.

## Real-money products

| SKU | Play product type | ShopItemId | Effect | Grant decided by |
|---|---|---|---|---|
| `gold_pack_small` | consumable | `GOLD_PACK_SMALL` *(new)* | Credits gold | server constants |
| `gold_pack_medium` | consumable | `GOLD_PACK_MEDIUM` *(new)* | Credits gold | server constants |
| `gold_pack_large` | consumable | `GOLD_PACK_LARGE` *(new)* | Credits gold | server constants |
| `box_single` | consumable | `BOX_PURCHASE` *(new)* | Grants one box opening | server drop table |
| `premium_day` | consumable | `PREMIUM_DAY` *(new)* | 1 day of premium | server |
| `premium_week` | consumable | `PREMIUM_WEEK` *(new)* | 7 days of premium | server |
| `premium_month` | consumable | `PREMIUM_MONTH` *(new)* | 30 days of premium | server |
| `premium_year` | consumable | `PREMIUM_YEAR` *(new)* | 365 days of premium | server |
| `donate_tier_1..n` | consumable | `DONATE_GOOGLE_PLAY` *(exists)* | Nothing but thanks | — |

`DONATE_GOOGLE_PLAY` already exists with `ShopCurrency.EXTERNAL` and `isAvailable = false`. It is
the one shelf entry that needs enabling rather than creating.

## Products bought with gold

| ShopItemId | Status | Effect |
|---|---|---|
| `GOLD_HEART` | exists | One plasma charge |
| `PREMIUM_*` | new | Premium of the matching duration, at a gold price the server sets independently of the money price |
| market items | via `NICKNAME_MARKET` | A unique name or logo, bought from another player |

## Products bought with ads

| ShopItemId | Status | Price | Effect |
|---|---|---|---|
| `AD_REWARD_BOX` | exists, `isAvailable = false` | 5 `ShopCurrency.ADS` | One box opening |

The shipped item is already priced at five ads, which is exactly the owner's "5 реклам за бокс".
Enabling it, not redesigning it, is the work.

## Rules the catalogue must satisfy

- **One shelf.** `GetShopCatalogUseCase` returns paid and unpaid items together. A screen that
  reads products from anywhere else is a defect, not a shortcut.
- **A box grants the same thing however it was opened.** Purchase and five-ads converge on one
  server call. If the two ever read different tables, an operator tuning one silently unbalances
  the other.
- **Availability is server-driven, not compiled in.** `isAvailable` reflects what the server says
  is sellable now, so a product can be pulled without an app release.
- **Consumables, not entitlements, for anything repeatable.** Gold packs and boxes must be
  consumed after credit or the account can never buy them again.
- **Premium extends, never double-charges.** Buying premium while holding it adds its duration to
  the remaining time. Premium is timed, not a permanent unlock, which is why it ships as four
  consumable durations rather than a subscription — a box can also drop a day or a month of it, and
  a subscription cannot be granted that way.
- **Premium never buys advantage.** Cosmetic and personal benefits only: golden nickname in chat and
  on leaderboards, box multipliers, faster charge regeneration, sponsor-qualification points. No
  effect on scores, rankings or income rates.

## Ad surfaces

| Placement | Format | Grants |
|---|---|---|
| Box unlock | rewarded video | 1 of the 5 needed for a box |
| Offers screen | player-supplied image and link, won at gold auction | nothing — it is a placement, not a reward |

Nothing renders during a lesson attempt. The offers screen is detailed in
`promotion-and-offers.md`; everything else on that page's roadmap — course promotion, Telegram and
chat-banner auctions, push super-offers, the survey mode — is recorded there and out of scope here.
