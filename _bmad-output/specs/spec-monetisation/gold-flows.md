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
| Market sale | the settling transfer | seller receives the listed price **minus commission** | Selling to a second account you control, to launder gold in |
| Leaderboard payout | server, once per season | **boxes** — table below | Rank manipulation; double payout on retry |
| Referral settlement | server, once per season | **boxes** — 50 for six qualifying invites, plus 1% of referrals' openings | Throwaway accounts; a chain paying a player for their own openings |

**Gifts are not on this table.** They are manual grants by the developer — a bug bounty, a goodwill
credit — and stay outside the client entirely.

## Sinks

| Sink | Debited by | Note |
|---|---|---|
| Market purchase | the settling transfer | Buyer pays **exactly the listed price** |
| Commission | the settling transfer | The 5%. Leaves circulation — this is the house's revenue |
| Plasma slots | server, per `spec-charges` CAP-9 | Ladder **1 / 2 / 3 gold** by slot owned — six for all three. **One-time:** a bought slot then refills itself every 24 h |
| Premium | server | Gold price independent of the money price |
| Offer auction bid | server, at auction close | Winning bid leaves circulation; losing bids refunded |

## One sink is one-time

**Plasma is bought once.** Six gold buys all three slots and they refill themselves from then on, so
it bounds how much gold a player ever needs for plasma. The sinks that keep drawing gold are the
market commission, premium renewals and auction bids.

## The commission

**5%, taken out of the listed price** — ratifying the shipped `splitSalePrice`.

The buyer pays exactly the price on the lot. The house takes 5% of it and the seller receives the
rest. The commission is destroyed rather than credited anywhere, which is what makes it a
transaction fee on gold that was, at some point, bought with real money.

**The rounding rule is load-bearing.** The remainder goes to the seller, so commission and seller
share always add back to the price exactly. Rounding the two independently would mint or destroy a
unit of gold on every other sale — invisible per trade, impossible to reconcile in aggregate.

This is the only revenue line here that is not a store purchase, and the only place besides the
auction where gold both moves and leaves circulation.

## Season payouts — leaderboard and referral

A season is **three months**, chosen over monthly so the prize is worth a push and there is time to
spend it. Both payouts settle at season close, and both pay **boxes**.

### Leaderboard — top 100

Ranks are half-open and cover 1–100 with no overlap and no gap. The dictated bands collided at 50,
30 and 5; these do not.

| Rank | Boxes |
|---|---|
| 1st | **500** and a trophy |
| 2nd | 250 |
| 3rd | 125 |
| 4–5 | 70 |
| 6–10 | 50 |
| 11–30 | 25 |
| 31–50 | 15 |
| 51–100 | 5 |

The amounts preserve the owner's own gold ratios — 100:50:25:14:10:5:3:1 — scaled five-fold into
boxes, so the shape of the prize curve is unchanged and only its currency moved.

**What a season costs:** 2 315 boxes across 100 players, injecting roughly **44 gold**, **71
premium-days** and **3.4M nolics**. The gold figure is what makes paying in boxes safe: a hundred
players sharing 44 gold cannot move a paid economy.

**The trade, stated plainly.** 500 boxes is worth about **9.6 gold**, where the previous first prize
was 100. Paying in boxes makes the prize feel far bigger and be worth roughly a tenth as much in
gold — while being much richer in nolics and premium days, and more interesting to open. That was
the owner's reason for choosing boxes, and it is the right reason; it is recorded here so nobody
later "fixes" the table back to gold without knowing what it costs.

For scale: honest play earns 182 boxes a season, so first place is about **2.7 seasons** of farming.

### Referral

Two mechanisms, both in boxes, both from the legacy design:

| Mechanism | Reward |
|---|---|
| Invite **six** players who each open **10 boxes** | **50 boxes**, once |
| Ongoing, uncapped | **1%** of every box opened by anyone registered under the link — one level, no chain |

Openings, not grants, because the server sees openings — which is also what the legacy
`ReferalRemote.openBoxInSeason` field counts.

**The six must qualify: 10 opened boxes each.** Six throwaway accounts must not be worth 50 boxes,
and ten openings is roughly five days of honest play per account — enough friction to make the farm
not worth running for what is, in gold terms, about one gold.

## Invariants

**Only three of the sinks above recur.** The market commission, premium and auction bids drain gold
on an ongoing basis. Plasma does not: six gold buys three slots once, and they refill free from
then on, so it is an acquisition trigger rather than a long-run drain — see the open question in
`SPEC.md`.

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
