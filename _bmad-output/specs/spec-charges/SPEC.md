---
id: SPEC-charges
slug: charges
derived_from: .memlog.md
date: 2026-08-31
companions:
  - economy-constants.md
  - settlement-protocol.md
  - brownfield.md
  - ../spec-monetisation/box-economy.md
  - ../spec-monetisation/gold-flows.md
  - ../spec-sync/SPEC.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate.

# Charges — the heart, retired

## Why

A heart today does two unrelated jobs and neither one cleanly. It is an *activity budget* — a
slot worth 100 life points, 33 of them charged per lesson attempt — and it is also what the
runner silently decrements when the player asks for a hint. The second job never reaches the
server at all: `hintRequested()` drops a local counter and nothing is ever told. A resource the
server does not see is a resource that cannot be sold, cannot be limited, and cannot be audited.

Charges resolve that by naming what each one is *for*. A **standard (yellow) charge** has two
sinks and only two: it pays the toll to play something, and it buys a hint on an easy question.
The toll is not one number — it is a price list per activity, the same scheme the legacy app
already used in `CoastValuesLife`, now extended and moved to the server. A **plasma charge** — the
retired gold heart — has exactly one sink: it skips a hard question, tournaments included. Plasma
never pays a toll. Both are spent against the server, which decides whether the account could have
held them.

Two consequences make this worth doing now rather than later. First, plasma is priced in gold,
and gold is the paid side of the economy — a resource bought with real money cannot be settled
by the phone that benefits from the answer. Second, every knob in the current economy is
hardcoded twice, in `functions/index.js` and again in a Kotlin companion, so retuning a price
or a ceiling costs an app release. Charges arrive with a server-owned constants table and pull
the whole heart economy into it.

## Capabilities

- **CAP-1 — Two charges, and each has a closed set of sinks.**
  - **intent:** A player holds two separately-counted charge types. A **standard** charge is spent
    on exactly two things — the toll for playing an activity, and a hint on an EASY question. A
    **plasma** charge is spent on exactly one — skipping a HARD question, tournaments included.
    Plasma never pays a toll; a standard charge never touches a hard question.
  - **success:** Playing any activity debits standard charges and leaves plasma untouched, at every
    difficulty and inside a tournament. With standard charges only, the in-question spend control
    is offered on an EASY question and refused on a HARD one; with plasma only, the reverse. An
    account holding plasma and no standard charges cannot start anything at all.

- **CAP-2 — Plasma skips the question; it never answers it.**
  - **intent:** Spending a plasma charge removes the question from the run and has it counted as
    fully correct. The player is never shown what the answer was.
  - **success:** After a plasma spend, no answer text for that question reaches the device on any
    channel, and the completed attempt scores that position as fully correct. Two accounts run by
    the same person cannot use one to learn an answer for the other.

- **CAP-3 — The server settles every spend.**
  - **intent:** A charge tap is a claim the client records, not a transaction it completes. On
    result submission the server debits the account, and only a debit it accepted turns a claimed
    position into a correct one.
  - **success:** An attempt claiming more charges than the account can pay for is stored with the
    unpaid positions scored as unanswered, not as correct; the returned percent matches what the
    server paid for, not what the client asked for. See `settlement-protocol.md`.

- **CAP-4 — Every knob is a server-owned constant.**
  - **intent:** Charge ceilings, regeneration periods, and both price ladders live in one
    server-owned document, not in code. An operator retunes the economy without an app release.
  - **success:** Raising the plasma ceiling from 3 to 4 and the first standard-charge price from
    1000 nolics to 3000 in that document changes what the shop offers and what the server accepts,
    on an unmodified client build. Full knob list in `economy-constants.md`.

- **CAP-5 — Constants reach the device on sync.**
  - **intent:** The device pulls the current constants as part of its ordinary sync and runs its
    local presentation and affordability checks against them.
  - **success:** A device that has synced since a constants change shows the new ceilings and
    prices; one that has not shows the last set it pulled and never a compiled-in default. A
    device that has never synced can still start the app.

- **CAP-6 — Offline spend parks the balance at zero.**
  - **intent:** Charges spent without a connection are held as unsettled claims, and the local
    balance stays at zero for them until the server has debited them. Local regeneration does not
    refill what the server has not yet charged for.
  - **success:** Spend every charge offline, wait out a full regeneration period offline, and the
    balance is still zero — it moves only after a sync settles the outstanding claims.

- **CAP-7 — Overspend is detected and recorded, not just refused.**
  - **intent:** When submitted claims exceed what the account could have held over the window the
    attempts cover, the server refuses the surplus and writes an audit record naming the account,
    the window, the ceiling it computed, the claim it received, and the arithmetic between them.
  - **success:** The offline replay the input describes — spend three, wait for regeneration
    offline, spend three more, then sync — produces exactly one audit record whose stated reason
    reconstructs the discrepancy without re-reading the attempts.

- **CAP-8 — Plasma is monetary-grade: no connection, no spend.**
  - **intent:** A plasma charge cannot be spent while the account carries unsettled charge claims.
    Plasma regenerates on the server and is not credited locally ahead of the server.
  - **success:** With an unsettled claim outstanding, the plasma control is unavailable and says
    why; it returns after a successful sync. A standard charge in the same state is still
    spendable.

- **CAP-9 — Charges are bought, each in its own currency.**
  - **intent:** Both charge types are bought as **slots** on an escalating ladder — standard with
    nolics, plasma with gold — and a bought slot then refills on its own. Neither purchase can
    exceed the ceiling the constants name.
    Buying is **synchronous and online-only**: the price is decided by the server at call time,
    so there is no offline queue and no optimistic local debit.
  - **success:** Buying the *n*-th slot of either type costs that type's *n*-th ladder entry, so
    all three plasma slots cost six gold in total; a purchase at the ceiling is refused with a
    stated reason and no currency leaves the account. Offline, the purchase control is unavailable
    and says why — it is never queued.

- **CAP-10 — Time breaks a percent tie.**
  - **intent:** When tournament entrants finish on equal percent, the faster completed run ranks
    higher. Elapsed time is recorded per attempt so it can be ranked on.
  - **success:** Two 100% runs on the same lesson rank in order of elapsed time. Time is a
    tie-break only: it never moves an entrant past someone with a higher percent.

- **CAP-11 — Forgeable balances are reconciled on a schedule.**
  - **intent:** Balances that a determined player could inflate — nolics, skill points, standard
    charges — are re-derived periodically from the history that should have produced them, and a
    discrepancy is surfaced rather than left to accumulate. Gold and plasma are not in scope here:
    they are guarded per operation instead, because a wrong number there is a wrong number about
    money.
  - **success:** A balance that does not follow from the account's recorded history is found by the
    sweep and reported with the gap it computed. The sweep completes without blocking play and
    without touching a monetary balance.

- **CAP-12 — A box is earned offline and opened online.**
  - **intent:** A player away from the network still accrues boxes at the rate the box economy
    already sets — the streak keeps running while offline instead of standing still. Opening one
    stays a server operation: the contents are decided and granted by the server, never by the
    device, and the open is refused while offline. **This capability owns the offline/online
    mechanic only.** Every rate, ceiling and drop is `spec-monetisation`'s, in `box-economy.md`.
  - **success:** Offline, the streak advances on schedule and the open control is unavailable with
    a stated reason. On reconnection the accrued boxes survive the sync, are checked against what
    the server's own clock says the account could have earned, and open normally — without ever
    exceeding the two-a-day ceiling `box-economy.md` sets. A device that fabricates a box count
    gains nothing: the surplus is refused at sync and recorded like any other overspend (CAP-7).

- **CAP-13 — A charge is a tank, and every activity has its own price.**
  - **intent:** A standard charge holds 100 points; points are what the server actually counts and
    the charge is what the player is shown. Each kind of activity costs its own number of points,
    read from the server's price list rather than from a constant in the build — an ordinary lesson
    costs a third of a charge, a tournament costs five whole ones.
  - **success:** Playing each of the five priced activities debits exactly the points its list
    entry names; a full tank of 10 charges pays for two tournaments and no more. Changing a price
    on the server changes what the next attempt costs, on an unmodified client. A client that
    declares a cheap activity for an expensive run is charged the real price, not the declared one.
    Prices are catalogued in `economy-constants.md`.

- **CAP-14 — A tournament is a server-held session.**
  - **intent:** A tournament run is opened on the server, which owns its question order, its clock
    and its score for the session's whole life — the same shape the exam sessions in the sibling
    theme-exams spec already take. This is what makes the run's elapsed time worth ranking on.
  - **success:** A client patched to lie about its answers, its timing or its score cannot change
    the tournament result the server returns, and the result is reproducible from server state
    alone. A tournament cannot be started without a connection.

- **CAP-15 — The toll is taken at the start and given back if the run never lands.**
  - **intent:** Playing something costs its price up front, so a player cannot start what they
    cannot afford. But an unfinished run — abandoned, crashed, or lost with a failed sync — must
    not cost anything: what was not spent in the end comes back.
  - **success:** Starting an activity immediately lowers the shown balance by its price. Abandoning
    that run, killing the app, or failing to sync leaves the account no poorer once the dust
    settles — the only permanent debits are the toll of a run that actually produced a result and
    the hints that run actually used. No background job is required for the release to happen.

- **CAP-16 — The server names the activity; the client only plays it.**
  - **intent:** What kind of activity an attempt was — and therefore what it costs — is decided by
    the server from the content the attempt names, not read from a field the device filled in.
  - **success:** An attempt whose payload declares a cheaper activity than the one actually played
    is billed the real price. Removing the declaration from the payload entirely changes nothing
    about what is charged.

## Constraints

- `codeAnswer` stays digits-only. `'0'` means the question was never shown, `'1'`–`'9'` is the
  score, `'9'` is fully correct — and `recomputePercentScore` in `functions/result-verification.js`
  is a line-for-line mirror of `RunnerLogic.kt`. A charge claim travels as a **parallel mask
  string of the same length**, never as a new digit in `codeAnswer`. Extending the alphabet
  breaks both implementations and every stored attempt.
- **The server decides what was played, not the client.** `sourceShelf` — the closest thing to an
  activity name today — is computed on the device and trusted verbatim by the server. Under a
  price list that hands the client its own price, so the kind must be re-derived server-side from
  the quest the attempt names.
- **Capability ids here are externally referenced and must never be renumbered.**
  `spec-monetisation` cites `spec-charges` CAP-3 for plasma settlement and
  `economy-constants.md` for the monetary classification. Renumbering silently breaks a sibling
  contract.
- **Regeneration must accept a premium multiplier.** `spec-monetisation` CAP-4 sells faster charge
  regeneration as a premium benefit, so neither period can be a bare constant — the multiplier is
  a knob beside them.
- **The box economy is `spec-monetisation`'s.** Two boxes a day is its ceiling and its constraint;
  nothing here may add a box source or restate a rate.
- The server clock is the only clock. Regeneration, ceilings, and the overspend window are all
  derived from server time and the stored balance timestamp; no client-supplied instant is ever
  an input to a debit.
- `configs/{configId}` is server-owned and closed to clients (`allow read, write: if false`).
  The constants must reach the device through a callable, not a direct document read.
- One source of truth for the constants. The values in `functions/index.js:93-96` and the
  mirrored companion in `UserProfile.kt` are the debt this replaces — after this work neither
  side carries its own copy.
- **A hint or a skip costs a whole charge; a toll does not.** The two sinks are quoted in
  different units on purpose. A hint and a plasma skip are indivisible — one charge, never a
  fraction. A toll is quoted in points against the 100-point tank, and an ordinary lesson at 33
  points is deliberately a third of a charge. Any design that forces the two onto one unit breaks
  either the price list or the hint.
- Plasma must not become an answer channel. No design may deliver the correct answer to the
  device as a consequence of a plasma spend, including as data the UI merely declines to draw.
- Renaming is wire-level. `standardHearts` and `goldHearts` are stored on live accounts; existing
  balances survive the rename with their values intact.

## Non-goals

- **No answer reveal.** The alternative the input raised — a server script that checks the account
  and hands back the answer — is out of scope. It is the design that leaks content to farm
  accounts, which is the thing the skip mechanic exists to prevent.
- **No new currency.** Nolics and gold are the two purses; charges are goods bought with them.
- **No rework of the tournament rating algorithm.** `pairwise-percent-least-squares-v1` stands.
  Tournaments gain a server-held session (CAP-14) and the ordering gains a duration tie-break
  (CAP-10); how the rating itself is computed does not change.
- **No client-side anti-cheat.** The client is not asked to detect, refuse, or report tampering.
  It reports what happened; the server decides what it is worth.
- **No moderation workflow.** CAP-7 writes the audit record. What an operator does with it —
  review queue, sanction, appeal — is a separate piece of work.
- **No real-money purchase path for plasma.** Plasma is bought with gold; how gold is acquired is
  unchanged.

## Success signal

An operator changes the plasma ceiling and the standard-charge price ladder in one server
document, and every synced device honours both within a sync cycle, with no build and no release.
In the same week, the offline replay — spend, wait, spend again, then connect — is refused at
settlement and lands as a single audit record that explains itself, while an honest player who
simply lost their connection mid-lesson has every charge they spent settled correctly on
reconnection.

## Assumptions

- The existing escalating nolics ladder `[1000, 2000, 5000, 10000, 20000]` carries over as the
  standard-charge ladder, extended to whatever ceiling the config names. The input said prices
  are tunable but respecified only the plasma side.
- The audit record from CAP-7 is a server-only Firestore collection, unreadable by clients, in the
  style of the existing `admin/` namespace. The input said "помечаю в папочку и обоснование"
  without naming a location.
- The reconciliation sweep for motivational resources (CAP-11) is a scheduled function in the
  style of the existing `reconcileQuestReviewDaily` / `aggregateQuestRatingsDaily`, not new
  infrastructure. The input said "раз в месяц".
- "Обычный заряд даёт подсказку" is read as the existing hint affordance, now debited through
  settlement rather than locally. The input did not describe a new kind of hint.

## Open Questions

### Needs a matching edit in a sibling spec

1. **`spec-monetisation` says plasma costs 10 gold; here it is a 1/2/3 ladder.** Not a
   disagreement about design any more — the owner has settled the ladder — but three places in
   monetisation still state a flat 10 (`gold-flows.md:30`, `brownfield.md:16`,
   `box-economy.md:31`), and one of them uses the figure in an argument.

   The gap is smaller than the flat-price reading suggested. Filling all three plasma slots costs
   1+2+3 = **6 gold, once**, against the 10 that bought a single non-regenerating one. Measured
   against `box-economy.md`'s ≈1 gold per 26 days of free play, that is 156 days rather than 260 —
   the sink is 40% shallower, not 90%. What does change qualitatively is that plasma now refills
   itself, so the six gold buys permanent capacity instead of one use.

   Action: update those three places in `spec-monetisation`, and let its author decide whether the
   pricing-power argument in `box-economy.md` still reads the same at six gold.

### Remaining

2. **Where do the dormant price rows leave their marker?** Pricing ships for the three playable
   activities; the theme-test and exam rows wait for modes that do not exist. Proposed: a pointer
   in `spec-theme-exams` back to this price list, a return pointer here, and a marker at the code
   site that resolves an activity kind. Confirm the shape.
3. **How long does a server-held tournament reservation live before release?** CAP-15 releases
   lazily, which needs a deadline: long enough that a slow honest run is never cut off, short
   enough that one crash does not lock a player out for a day.
4. **What does the reconciliation sweep actually compare?** CAP-11 re-derives forgeable balances
   from history. Whether the recorded history supports it has not been checked — the reward path
   writes deltas, not an auditable ledger.
5. **Does faster charge regeneration survive monetisation's own rule?** Its CAP-4 sells it as a
   premium benefit while its constraints forbid premium affecting income rates. Charges *are* an
   income rate. That tension is monetisation's to resolve, but this spec has to implement whatever
   it decides.
