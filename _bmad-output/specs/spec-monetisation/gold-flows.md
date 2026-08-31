# Gold flows

Companion to `SPEC.md` (CAP-2, CAP-5, CAP-10, CAP-12). Every way gold enters an account and every
way it leaves.

Gold is **monetary class** under `spec-charges/economy-constants.md`: per-operation server
authorisation, online only, no offline spend. That classification makes this table a security
boundary rather than a design note — each row is a place where a client could otherwise mint
currency bought with real money.

## Sources

| Source | Authorised by | Rate | Anti-cheat surface |
|---|---|---|---|
| Gold pack purchase | Play receipt verified server-side | server constants | Forged or replayed receipt — see `purchase-verification.md` |
| Box opening | shipped drop table | **≈0.019 gold per box** — see `box-economy.md` | Claiming an unearned opening; opening one box twice |
| Market sale | the settling transfer | seller receives the listed price in full | Selling to a second account you control, to launder gold in |
| Leaderboard payout | server, once per season | table below | Rank manipulation; double payout on retry |
| Referral settlement | server, once per season | **open** — proportional to referred players' box openings | A referral chain paying a player for their own openings |

**Gifts are not on this table.** They are manual grants by the developer — a bug bounty, a goodwill
credit — and stay outside the client entirely.

## Sinks

| Sink | Debited by | Note |
|---|---|---|
| Market purchase | the settling transfer | Buyer pays price **plus 5%** |
| Commission | the settling transfer | The 5%. Leaves circulation — this is the house's revenue |
| Plasma charge | server, per `spec-charges` CAP-3 | Existing `GOLD_HEART` item, 10 gold |
| Premium | server | Gold price independent of the money price |
| Offer auction bid | server, at auction close | Winning bid leaves circulation; losing bids refunded |

## The commission

**5%, paid by the buyer**, on top of the seller's listed price.

The seller receives exactly what they listed. The buyer pays 105%. The 5% is destroyed rather than
credited anywhere, which is what makes it a transaction fee on gold that was, at some point, bought
with real money.

This is the only revenue line here that is not a store purchase, and the only place besides the
auction where gold both moves and leaves circulation.

## Leaderboard payout

Once per season. **A season is three months** — chosen over monthly so the prize is worth a push
and there is time to spend what it pays.

| Rank | Reward |
|---|---|
| 1st | 100 gold **and a trophy** |
| 2nd | 50 gold |
| 3rd | 25 gold |
| 4–5 | 14 gold |
| 5–10 | 10 gold |
| 10–30 | 5 gold |
| 30–50 | 3 gold |
| 50–100 | 1 gold |

Two things are unresolved and both are in `SPEC.md`'s open questions:

- **The bands overlap at every boundary.** 50–100 and 30–50 both contain 50; 30–50 and 10–30 both
  contain 30; 4–5 and 5–10 both contain 5. Each boundary needs an owner before this can be
  implemented.
- **Gold or boxes.** The owner leans to paying boxes instead, as the more interesting reward. If
  that is chosen, the table's units change and the amounts must be recomputed — by expected value a
  hundred boxes is about two gold, not a hundred, so a naive substitution would cut first prize by
  a factor of fifty. See `box-economy.md`.

## Invariants

1. **Conservation.** Across a completed market sale, total gold in circulation falls by exactly the
   commission. Across a closed auction, by exactly the winning bid. Nothing else both creates and
   destroys.
2. **No offline movement.** Every row refuses rather than queues when the device is offline. A
   queued gold operation is an IOU the server never agreed to.
3. **One authorising operation per movement**, named in the audit record.
4. **Transfers are atomic.** A market sale moves the item and both balances together or moves
   nothing. There is no state where an item has two owners, or none.
5. **Replay-safe.** Every source is idempotent under retry — the same receipt, the same box
   opening, the same season settlement credits exactly once.

## On cash-out

Gold is not convertible to money in this scope. The owner intends a later gold-pegged project
token with exchange or withdrawal, used as a marketing instrument and sold to users.

Recorded here so the later work starts from the facts rather than rediscovering them: converting a
purchased in-app currency back to money makes the operator a money transmitter in most
jurisdictions, and boxes — a paid, random-outcome source — put gambling law on top of that in
several. Neither is a reason not to do it. Both are reasons to have the legal answer before the
code, because retrofitting a licence onto a live economy is far more expensive than designing for
one.
