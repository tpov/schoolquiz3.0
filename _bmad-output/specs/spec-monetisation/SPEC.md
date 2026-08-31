---
id: SPEC-monetisation
slug: monetisation
derived_from: .memlog.md
date: 2026-08-31
companions:
  - paid-catalogue.md
  - gold-flows.md
  - box-economy.md
  - promotion-and-offers.md
  - purchase-verification.md
  - brownfield.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate.

# Monetisation — the shop, the gold economy, and the house's cut

## Why

The app cannot take money. `platform/billing` was an empty module until this week, no in-app
products exist in Play Console, and the one shop item wired to real money — `DONATE_GOOGLE_PLAY` —
ships with `isAvailable = false`. Everything downstream of revenue is therefore unmeasurable and
untunable: there is no conversion rate to improve, no ARPU to compare against a benchmark, and no
way to answer whether a marketing hryvnia returns anything.

That gap is now the binding constraint on the whole plan. The market research for this product
concluded that measurement, not acquisition, is the first bottleneck, and monetisation is the half
of measurement that cannot be faked with an analytics event — either the store takes the money or
it does not.

The economy this has to serve is already built and already unusual, and the unusual parts are
where the money is. Gold is earned as well as bought, but barely: the shipped drop table pays
roughly one gold per fifty-three boxes, and boxes accrue at most two a day. That scarcity is the
product's whole pricing power — it is what makes a gold pack worth buying and what makes a 5%
commission on a player-to-player sale meaningful. Because gold moves between players, the house can
take that cut, which is a revenue line that exists only if transfer is a first-class, server-settled
operation rather than a side effect. And because gold is bought with real money, every path into a
balance is a fraud surface the server has to settle.

## Capabilities

- **CAP-1 — One paid catalogue, four product classes.**
  - **intent:** A player buys gold packs, boxes, premium, or a plain donation with real money,
    through Google Play Billing, from one catalogue the server owns. Prices shown are the store's
    own localised prices, never a figure the client computed.
  - **success:** Each product class completes a purchase end to end on a real device and the
    resulting balance change is visible after a sync. A device set to Ukraine shows hryvnia prices
    from a Ukrainian Play price tier, not a converted dollar figure, with no client-side conversion
    anywhere in the path. Catalogue detail lives in `paid-catalogue.md`.

- **CAP-2 — Gold is earned as well as bought, and every credit is server-authorised.**
  - **intent:** Gold reaches an account from four automatic paths — purchase, boxes, market sales,
    leaderboard payouts — and leaves it through four — market purchases, plasma charges, premium,
    and offer auction bids. No path credits or debits gold on the device.
  - **success:** For every source and sink in `gold-flows.md`, the client-side balance after the
    operation equals the server's balance on the next read, and an offline attempt at any of them
    is refused rather than queued. A crafted client request claiming an unauthorised gold credit
    changes no balance and lands in the audit record.

- **CAP-3 — A box opens two ways, grants one thing, and accrues at a bounded rate.**
  - **intent:** A box is either bought outright or unlocked by watching five rewarded ads, and both
    routes end in the same server-side grant. Separately, a daily-visit streak grants one box per
    day from the tenth consecutive day. Two boxes a day is the ceiling on honest play, and that
    ceiling is the anti-cheat bound.
  - **success:** Opening by ads and opening by purchase draw from the same server table, verifiable
    across many openings. Four ads grant nothing; a fifth completes it, counted from the ad SDK's
    reward callback rather than the impression. An account cannot accrue a third box in a day by any
    sequence of actions. Numbers in `box-economy.md`.

- **CAP-4 — Premium sells in four durations, arrives by four routes, and never buys advantage.**
  - **intent:** Premium exists for a day, a week, a month or a year. It arrives by real-money
    purchase, by gold, or as a box drop. Its benefits are cosmetic or personal — a golden nickname
    in chat and on leaderboards, box multipliers, faster charge regeneration — and it feeds points
    toward the sponsor qualification.
  - **success:** No premium benefit changes a score, a ranking position, or an income rate relative
    to a non-premium player. Buying premium while holding it extends rather than double-charges.
    Changing the gold price server-side takes effect without an app release.

- **CAP-5 — The market trades unique items between players, and the buyer pays the house 5%.**
  - **intent:** A player sells a unique name or logo to another player for gold. The transfer moves
    the item and the gold in one server-settled operation. The buyer pays a 5% commission on top of
    the seller's price.
  - **success:** After a completed sale the item has exactly one owner, the seller receives the full
    listed price, the buyer is debited the price plus 5%, and total gold in circulation falls by
    exactly that 5%. A sale interrupted mid-flight leaves either both sides moved or neither.

- **CAP-6 — Nothing moves until the server has verified the receipt.**
  - **intent:** A real-money purchase is verified against the store before any balance changes, and
    the client consumes the purchase only after the server confirms it credited.
  - **success:** A replayed purchase token credits exactly once. A token forged on the device
    credits nothing. A process killed between paying and crediting still credits on the next launch.
    Protocol in `purchase-verification.md`.

- **CAP-7 — Two ad placements, both deliberate.**
  - **intent:** Ads appear in exactly two places: a rewarded video that unlocks a box, and the
    offers screen the player chooses to enter. No ad interrupts a lesson.
  - **success:** No ad surface renders during a lesson attempt at any difficulty. Every rewarded
    view that advances box progress is confirmed by the SDK's reward callback.

- **CAP-8 — The paid catalogue extends the existing shop model.**
  - **intent:** New products are new `ShopItemId` entries and, where needed, new `ShopCurrency`
    values — not a second catalogue beside the existing one.
  - **success:** `GetShopCatalogUseCase` returns every purchasable thing, paid and unpaid, from one
    list. No screen reads a product from another source. Mapping in `brownfield.md`.

- **CAP-9 — The funnel is observable from the first release.**
  - **intent:** Opening the shop, starting a purchase, completing one, failing one, completing a
    rewarded ad, and running out of charges each emit a funnel event.
  - **success:** A session that opens the shop and completes a purchase produces the matching
    ordered event sequence, and the conversion rate from `shop_opened` to `purchase_completed` is
    computable without further instrumentation.

- **CAP-10 — The leaderboard pays out once a season, and a season is three months.**
  - **intent:** The top 100 of a season receive a graded reward, with first place also taking a
    trophy. Three months rather than one, so the prize is worth a push and there is time to spend
    what it pays.
  - **success:** A settlement runs once per season, pays each rank band exactly the amount in
    `gold-flows.md`, is idempotent under retry, and credits nothing to ranks below 100.

- **CAP-11 — Ad placement inside the app is auctioned for gold.**
  - **intent:** A player buys the offers screen's placement by bidding gold in an open auction. The
    winner's image and link occupy the screen for the period. Bidding spends gold, which makes the
    auction a gold sink as well as a placement mechanism.
  - **success:** Bids are visible to all bidders while the auction runs, the top bid at close takes
    the slot for the stated period, losing bids are refunded in full, and a winning bid's gold
    leaves circulation. Detail and the surrounding roadmap in `promotion-and-offers.md`.

- **CAP-12 — The referral programme pays the referrer on their referrals' box openings.**
  - **intent:** A player who brought others in is credited each season in proportion to how many
    boxes those players opened.
  - **success:** A season settlement credits each referrer once, in proportion to referred openings
    recorded that season, and a referral chain cannot pay a player for their own openings.

## Constraints

- **The client never credits or debits gold.** Gold is monetary class under
  `spec-charges/economy-constants.md`: per-operation server authorisation, online only. This rules
  out optimistic local updates, offline queues, and any client-side grant table.
- **Premium never buys competitive advantage.** A benefit is either personal and incomparable, or
  comparable and immaterial. This rules out premium affecting scores, rankings or income rates —
  the owner's own design rule, and the line that keeps the leaderboard worth winning.
- **Two boxes a day is the honest ceiling.** Any new box source must fit under it or replace part of
  it. A third daily box from a new mechanic silently devalues every gold price in the shop.
- **Pack sizes and gold prices live only on the server.** The device knows a SKU, never what it is
  worth.
- **Real-money prices are read from the store, never formatted by the client.** Play's regional
  pricing means a hardcoded hryvnia figure is wrong in most markets and wrong in Ukraine the moment
  a price tier changes.
- **Consume only after the server confirms the credit.** Consuming first loses a paying user's money
  whenever the credit fails, and Play does not return a consumed token.
- **Rewarded-ad progress counts the SDK's reward callback, not the impression.**
- **Ukrainian is a required interface language.** Article 27 part 7 of Law 2704-VIII covers mobile
  applications of businesses supplying goods and services in Ukraine, and a shop that takes money is
  exactly that.
- **Google Play forbids incentivised installs.** Paying a channel for exposure is permitted; paying
  a user for installing is not. This bounds what the offers screen may carry.
- **Every gold-moving operation is auditable**, naming the actor, the amount, and the authorising
  operation.

## Non-goals

- **Gold cash-out, in this scope.** Converting gold back to money is not built here. The owner
  intends a gold-pegged project token with exchange or withdrawal in a later phase; that is a
  separate contract, and it carries consequences named in `gold-flows.md` that whoever builds it
  should take on with open eyes.
- **Player-to-player gold transfer as a feature.** Gold moves only as payment for a unique item.
  Gold that can be sent freely is a currency exchange, not a game economy.
- **Gifts as a user-facing mechanic.** Manual grants by the developer — a bug bounty, a goodwill
  credit — stay manual and stay out of the client.
- **A third-party attribution SDK.** Play Install Referrer covers acquisition-source attribution at
  no cost and with no vendor key.
- **Blockchain, in this scope.** The market trades server-owned records. The product keeps the name
  "NFT" by the owner's decision; `platform/crypto` stays an empty stub until a later contract says
  otherwise.
- **Everything in the roadmap section of `promotion-and-offers.md`.** Course promotion, quest
  promotion, Telegram-channel auctions, chat-banner auctions, push-delivered super-offers and the
  survey/interview mode are recorded so they are not lost, and are explicitly not built here.

## Success signal

A player in Ukraine opens the shop, buys a gold pack in hryvnia at a Ukrainian price tier, sees the
gold arrive, spends it on a name in the market, and the seller receives the listed price while the
buyer paid 5% more — every balance change settled by the server, every step visible as a funnel
event. At season close the top 100 are paid once, and nobody is paid twice. On the operator's side,
the month's revenue breaks down by product class and the shop-open-to-purchase conversion rate is a
number rather than a guess.

## Assumptions

- Premium is an account-level status with an expiry, since it sells in four durations and drops from
  boxes as a day or a month.
- The "5 ads per box" figure matches the shipped `AD_REWARD_BOX` item already priced at 5 in
  `ShopCurrency.ADS`, so the owner's intent and the shipped model are the same thing.
- Boxes are the only random-outcome product.
- Losing auction bids are refunded rather than burned, since burning them would make bidding a tax
  on participation rather than a price.

## Open Questions

- The leaderboard rank bands overlap at their boundaries as dictated: 50–100 and 30–50 both contain
  50; 30–50 and 10–30 both contain 30. Which side owns each boundary?
- Does the leaderboard pay gold or boxes? The owner leans to boxes as the more interesting reward
  but has not decided, and the choice changes the payout table's units.
- What does the sponsor qualification grant at each level? Premium feeds its points, but the perks
  are undefined.
- What does the referral programme pay per box opened by a referred player, and is it capped?
- Which premium benefit ships first — the box multiplier, the golden chat nickname, or faster charge
  regeneration?
- Is the offers auction period a week or a month?
