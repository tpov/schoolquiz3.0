# Promotion and offers

Companion to `SPEC.md` (CAP-11). The offers auction, which is in scope, and the promotion roadmap
around it, which is not.

## The offers screen — in scope

A screen on the home tab, provisionally **Offers**. It carries one advertisement at a time, and
that placement is sold to players in an open gold auction.

**The cycle is rolling and monthly.** While this month's winning ad is on screen, the auction for
*next* month is already open. The slot is therefore never empty and bidding never pauses — at the
turn of the month the new winner replaces the old one and the next auction opens behind it.

```
month N     [ ad from auction N-1 on screen ]  +  [ auction for month N+1 open ]
month N+1   [ ad from auction N   on screen ]  +  [ auction for month N+2 open ]
```

**How a player buys it**

1. Opens the offers screen and chooses to bid on the coming month.
2. Supplies the ad itself — an image and a link.
3. Names a bid in gold.
4. Watches an open table of bids while that month's auction runs.
5. At the turn of the month the top bid takes the placement; every losing bid is refunded.

**How a player sees it.** Tapping the screen shows the current winner's image and link, plus the
entry point back into the auction.

**Why it is a monetisation capability and not just a feature.** The bid is paid in gold, gold is
bought with real money, and a winning bid's gold leaves circulation. The auction is therefore both
an ad placement and one of the largest gold sinks in the design — potentially larger than any shop
item, because bidders compete against each other rather than against a fixed price.

**Rules the auction must satisfy**

- Bids are visible to all bidders while the auction is open. A sealed auction on a public
  leaderboard would be a different product; the owner described an open table.
- Losing bids are refunded in full. Burning them would tax participation instead of pricing it.
- A winning bid's gold leaves circulation and is not credited to anyone.
- The advertised link is subject to review before it goes live. An unreviewed user-supplied link on
  a screen inside an education app aimed at schoolchildren is a content-moderation incident waiting
  to happen, and Google Play's promotion policy prohibits deceptive or harmful promotion regardless
  of who supplied it.
- The period is one month, and the auction for the next month always runs during the current one.

## Roadmap — recorded, explicitly not built here

The owner asked that these be captured rather than lost. They are non-goals in `SPEC.md`; this is
where they live until a later spec picks one up.

**Telegram channel auction.** Sell placement in the project's own Telegram channel the same way the
offers screen is sold.

**Chat banner auction.** A banner in chats, also auctioned. Previously listed as undecided; the
owner now frames it as a possible auction, which is why it moved from "maybe" to "recorded idea".

**Course promotion.** A long-press on a course opens a menu — edit, delete, promote. Promoting buys
placement, and the price rises as more promotions are sold. In course search, promoted courses
appear first with golden styling; everything below ranks by rating. Two candidate mechanisms: three
fixed slots at escalating prices, or an auction for the top three.

**Quest promotion.** The same mechanism applied to a player's own quest.

**Super offer.** A promotion significant enough to justify a push notification.

**Survey and interview mode.** A flat list of lessons with no theme hierarchy, used for surveys or
as a job-interview instrument: the candidate completes it and receives a link based on the result.
Promotable like anything else.

## What stays in nolics, not gold

Quest ratings and course purchases pay **nolics**. A promo code makes a course free. This matters
for the whole design: the content economy runs on the motivational currency, and gold is reserved
for the monetary layer. Mixing them would put a real-money price on writing a good quest.

## The one thing worth deciding early

Course and quest promotion would put paid placement into the discovery surface of an education
product used by schoolchildren. Whatever mechanism is chosen, the paid slots need to be visually
distinguishable as paid — the golden styling the owner described does that, and it should be
treated as a requirement rather than decoration when this leaves the roadmap.
