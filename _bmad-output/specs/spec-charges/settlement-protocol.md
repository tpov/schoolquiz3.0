# Settlement protocol — how a charge is actually spent

Companion to `SPEC.md` (CAP-2, CAP-3, CAP-6, CAP-7, CAP-8). Defines the claim, the debit, the
offline invariant, and the overspend test.

## The shape of a claim

An attempt already carries `codeAnswer`: one digit per question in the pool, `'0'` for never
shown, `'1'`–`'9'` for the score, `'9'` for fully correct. `recomputePercentScore` in
`functions/result-verification.js` is a deliberate line-for-line mirror of `RunnerLogic.kt`, and
`isWellFormedCodeAnswer` pins the alphabet to digits.

A charge claim therefore travels **beside** it, not inside it: a parallel string of the same
length, one character per position — `.` for no claim, `S` for a standard charge, `P` for a
plasma charge. Two properties matter and both follow from keeping it separate:

- Existing attempts, existing parsers, and the Kotlin mirror are untouched. An attempt with no
  mask behaves exactly as it does today.
- The claimed positions are **not** pre-scored as `'9'` by the client. The client writes what it
  actually knows — the question was skipped, so its digit is `'0'` — and the server promotes the
  position to `'9'` only after it has taken payment. A client that writes `'9'` itself is claiming
  a correct answer it never gave, and the mask is what makes that distinguishable.

## The debit, at submission

`submitLessonResultEvents` (`functions/index.js:301`) already opens a transaction, reads the user
document, regenerates the balance from the server clock, and charges for the attempt before
writing it. Charge settlement belongs in that same transaction, on the same read.

Order of operations per attempt, inside the existing transaction:

1. Reject the batch outright if any mask is not the same length as its `codeAnswer`, or contains a
   character outside `.SP`, or claims a type against the wrong difficulty. This is a malformed
   payload, not an overspend.
2. Regenerate both balances from the stored value and stored timestamp against `Date.now()`,
   capped at the ceilings from `economy-constants.md`. Same lazy scheme as
   `regenerateLifePoints` — nothing runs while the player is away.
3. Count the claims by type. Debit what the balance can pay for, in the order the questions were
   asked, so a partial payment pays for the earliest claims rather than an arbitrary subset.
4. Promote to `'9'` **only** the positions that were paid for. An unpaid claim leaves its digit at
   `'0'` — the question is scored as unanswered, which is what it was.
5. Recompute `percentScore` from the promoted `codeAnswer`. The existing `scoreVerified` check
   compares the client's number against the server's; it now compares against the post-promotion
   figure, so a client cannot pre-inflate its percent and have the mask ratify it.
6. Store the mask, the paid count by type, and the resulting balances on the attempt record beside
   the existing `lifeCharged` flag. The attempt is stored either way, paid or not — the flag
   records which.

The return value carries the settled balances so the client can replace its local mirror outright
rather than reconciling arithmetic.

## The toll: taken at the start, returned if nothing lands

Hints and the toll settle on different clocks, and that is the whole difficulty. A hint cannot be
billed until the run ends — nobody knows how many were used before then. A toll must be taken
*before* the run starts, because its job is to refuse a run the account cannot afford; billing a
500-point tournament afterwards is not a gate.

The rule the owner set: **the server must never zero out what was not spent in the end.** An
unfinished run — abandoned, crashed, or lost to a failed sync — costs nothing.

Which splits by whether the activity is server-held.

**Offline-capable activities (ordinary lesson, arena).** The client reserves the price locally, so
it cannot start what it cannot afford and so the balance it shows is honest. The **server charges
nothing until the result arrives**, and then charges the toll and the hints together in the one
transaction it already runs. Nothing is reserved server-side, so nothing can leak: an abandoned run
simply never bills, and the client drops its local reservation.

That does make an abandoned run free — and it is worth saying plainly why that is not an exploit
worth closing. An abandoned run yields no result, no reward, no progress, and no information: an
easy hint reveals what `LEARNING` mode reveals anyway (`SessionMode.revealsCorrectAnswer`), and a
plasma skip reveals nothing at all, by design (CAP-2). Playing free buys nothing.

**Server-held activities (tournament, and the exam when it exists).** Here the server owns the
session, so it reserves at the moment the session opens — that is what makes the gate real — and
releases the reservation if the session expires without a submission.

The release needs no scheduled job. It follows the idiom already in `functions/life-points.js`:
regeneration is derived lazily on every read from a stored timestamp, so nothing has to run while
the player is away. A reservation is stored with its deadline, and any later read that finds an
expired unsettled reservation releases it before computing the balance. Identical result, no
infrastructure.

Two consequences worth stating:

- **A reservation is not a debit.** It lowers what is *available* without lowering what has been
  *spent*. Only settlement moves the second number, which is what makes "never zero out what was
  not spent" mechanically true rather than a promise.
- **The deadline is the only new tuning knob** — long enough that a slow honest run is never cut
  off, short enough that one crash does not lock a player out of their own charges. It belongs in
  the constants document with everything else.

## The offline invariant

Stated as the user stated it: *charges spent offline sit at zero until the server settles them.*

Concretely, the client keeps two numbers — the balance the server last reported, and the count of
claims made since, not yet settled. What it shows and what it allows is
`serverBalance − unsettledClaims`, floored at zero. Local regeneration may raise the first number
toward the ceiling on the same schedule the server uses, but it can never raise the *displayed*
figure above what an unsettled account is owed, because the claims are subtracted after.

This is the whole point, and it is worth being explicit about why. If the client refilled
optimistically while claims were outstanding, the replay works: spend three, wait offline for the
regeneration period, spend three more, then sync — and the server sees six claims against an
account that could hold three. Subtracting unsettled claims first makes the second spend
unavailable on the device, and the audit record in CAP-7 catches anyone who patches that out.

Plasma is stricter still (CAP-8): with any unsettled claim outstanding, plasma is unavailable
outright. Standard charges may be spent offline and settled later; a monetary resource may not.

## The overspend test

Given a batch of attempts covering a window from the earliest `completedAtMs` to now, the server
computes the maximum a type could have supplied over that window:

```
ceiling = min(maxOwned, storedBalance + floor((windowEndMs − balanceUpdatedAtMs) / regenMs))
```

If claims of that type exceed `ceiling` plus `settlement.clockSkewToleranceMs` worth of
regeneration, the surplus is unpaid — and an audit record is written.

The record must reconstruct the finding without re-reading the attempts: account, window bounds,
stored balance and its timestamp, the constants version in force, the computed ceiling, the claim
count by type, the surplus, and the attempt ids that carried it. "Обоснование" means the reason is
in the record, not derivable from it.

Two things this test must not do. It must not treat a slow sync as fraud — an honest player
offline for a week submits a week of attempts, and the window arithmetic above already covers
that, because the window is long and the regeneration ceiling grows with it. And it must not
punish twice: the surplus is already unpaid, so those questions already scored as unanswered. The
record is for an operator to read, not a second penalty.

## What the client is responsible for

Nothing that matters. It renders the balance it was told, subtracts its own unsettled claims,
refuses a spend it cannot cover locally, and reports the mask. Every one of those is a
convenience for the player. None of them is trusted: a client that skips all of it and submits an
arbitrary mask gets the same treatment as an honest one — paid for what the account can afford,
refused for the rest, recorded if the gap is impossible.
