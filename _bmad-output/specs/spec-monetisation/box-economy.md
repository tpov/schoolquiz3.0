# Box economy

Companion to `SPEC.md` (CAP-3). What a box pays and how fast boxes arrive.

**This is shipped behaviour, not a proposal.** Every number here was read out of
`functions/index.js` — `generateGiftBoxReward` (line 3094) and `advanceGiftBoxState` (line 3215).
It is recorded here because the drop table is the single most load-bearing number in the whole
economy and nothing outside the function documented it.

## The drop table

Two rolls decide the tier. 95% of boxes never leave the first one.

| Tier | Probability | Outcome |
|---|---|---|
| **Common** | 95% | 500–2 000 nolics |
| **Mid** | 4.75% | equal thirds: 7 000–13 000 nolics · a coin-flip between **1 gold** and 7 000–13 000 nolics · **1 day premium** |
| **Rare** | 0.25% | equal fifths: a logo · a trophy, or **10–20 gold** if the collection is complete · **10–20 gold** · **30 days premium** · 50 000–200 000 nolics |

## What that means in gold

| Path | Chance | Gold |
|---|---|---|
| Mid tier, gold branch, coin lands on 1 | 0.79% | 1 |
| Rare tier, trophy branch, collection complete | 0.025% | 10–20 |
| Rare tier, gold branch | 0.05% | 10–20 |

Expected value ≈ **0.019 gold per box** — about **one gold per 53 boxes**.

That number is the economy's pricing power. At the honest ceiling of two boxes a day, a player
earns roughly one gold every 26 days, and any new box source has to be measured against that rather
than added freely.

What that buys has changed, and the change is qualitative. Under `spec-charges` CAP-9 plasma is
bought as **slots on a 1 / 2 / 3 ladder — six gold for all three, once** — and a bought slot then
refills itself every 24 h. Six gold is about **156 days**, five months, of free play. The old flat
price of 10 was 260 days for a *single non-regenerating* charge: 260 days per use.

So plasma is no longer what makes gold worth buying on an ongoing basis, because after the unlock
it costs nothing per use. What it is now is an **acquisition trigger** — five months of waiting, or
a small pack today — and it is arguably the better one, since six gold is an amount a player can
picture buying where ten for one consumable read as a punishment. The ongoing drain has to come
from the three sinks that still recur: the market commission, premium, and auction bids.

## Premium from boxes

Premium is not only a purchase. The mid tier pays a **day** of it at 1.58%, the rare tier a
**month** at 0.05%. So the free player meets premium occasionally by luck, which is the intended
on-ramp to buying it.

## How boxes accrue

- A daily visit advances a streak counter, at most once per 24 hours.
- From the **tenth** consecutive day (`GIFT_BOX_STREAK_TARGET_DAYS = 10`), every further daily visit
  grants **one box**.
- Five rewarded ads grant **one box**, via the existing `AD_REWARD_BOX` shelf item.

**Ceiling: two boxes per day.** One from the streak, one from ads.

## The anti-cheat position

A script that opens the app daily and watches ads is possible and is bounded by the same ceiling as
an honest player — two boxes a day, worth about 0.04 gold. The economics of farming are therefore
worse than the economics of buying, which is the strongest anti-cheat there is. Detection and
banning handle the rest.

The rule this implies: **any new box source must fit under the two-a-day ceiling or replace part of
it.** A third daily box from a new mechanic devalues every gold price in the shop at once, and the
devaluation is invisible until someone recomputes this table.

## What is not decided

- Whether the leaderboard pays boxes instead of gold. If it does, a season payout has to be
  expressed against this table, because a hundred boxes is a very different prize from 100 gold —
  by expected value it is about two gold, not a hundred.
- The premium box multiplier: whether premium doubles boxes, grants several per five ads, or
  removes the ad requirement. The owner leans to several at once, with a ×5 bonus during beta.
