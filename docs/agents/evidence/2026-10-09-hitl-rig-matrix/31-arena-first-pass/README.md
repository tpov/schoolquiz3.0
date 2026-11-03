# 3.1 arena first full pass on the redroid rig — 2026-10-09 evening

Story: SELECTED/HARD prepare → start → saved answer → restart → same attempt → settle →
rating 2 → RATE_LESSON, ticket/attempt/progress surviving restart, SETTLE+RATE_LESSON receipts
consistent with the frozen ticket (two-receipt oracle). Owner's 2026-10-09 supplement: hitl
matrix rows run on the redroid rig. Player: the rig's current demo account `ck3DaiqEd…`
(UserYF9FS9) — profile bootstrapped by the app earlier this session (see `../27-remainder/`).

## Cycle 1 (ticket `aab909956f…`, attempt `3da6a7c3…`) — expired run, settle + rating leg

- Prepare: the price-1 fixture card (author `D0h0rHZGJVoU…`, bank pinned at 1), dialog
  "Confirm preparation" → **Hard** → Confirm. Reservation frozen: **1 nolic + 50 charge points**.
  DB: `arena_ticket_records` row `RESERVED / UNUSED / NONE`, terms JSON carries
  `mode SELECTED, difficulty HARD, priceNolics 1, lifeCost 50, dayId 2026-10-09`.
- Start: ticket view "Prepared locally. Starting requires a separate validated run" →
  "Start this prepared attempt". The runner launched (screen is **not capturable — screencap
  returns black on every arena screen while uiautomator sees the semantics**; all driving below
  is semantics-driven, and the black captures are kept as evidence of the flag).
- The run expired unanswered (driving latency on the blind screen): the runner auto-completed,
  arena_command_journal got a SETTLE PENDING with `completionKind COMPLETED, answers []`, and
  outbox op 6 (`lesson_runner.SUBMIT_ATTEMPT-3da6a7c…`) went WAITING.
- The receipt screen's own **Synchronize** button drained it: server record
  `lesson_runner-SUBMIT_ATTEMPT-3da6a7c…` **completed, receipt SETTLED, percentScore 0,
  codeAnswer "11", lifePointsSpent 50, nolicsSpent 1, ticketId = the frozen ticket**,
  reward 0/0 (`server-ck3d-records-summary.jsonl` row 1). Exactly one mutation record.
- **Rating 2**: the receipt screen offers "Rate this lesson: Rate 1/2/3". Rate 2 → UI:
  "Your rating (2) is queued to send", outbox op 7 `lesson_runner.SUBMIT_RATING` WAITING.
  **Force-stop + relaunch: op 7 survived the restart**; the next SYNC NOW delivered it:
  server record `lesson_runner-SUBMIT_RATING-1cdc7ca6…` completed,
  receipt `operation RATE_LESSON, status APPLIED`, lessonId = the frozen ticket's lesson.
  Aggregate after: `lesson_ratings` doc `ratingCount 2, ratingSum 4, averageRating 2` — and the
  arena card in the UI now shows "Player ratings: 2" (screenshot of the cards list).

## Cycle 2 (ticket `f143f023b9…`, attempt `cf575a94…`) — saved answer, restart, same attempt, settle

- Prepare: same card, Hard, Confirm (second 1-nolic reservation). Ticket `RESERVED / UNUSED`.
- Start → question 1/2 rendered (semantics): options lateinit/var/val, timer 6 s at dump time →
  tapped **val** → the runner advanced (2/2) — the answer was accepted and checkpointed.
- **Restart mid-attempt**: the checkpoint read `phase PENDING_FEEDBACK` (q1 answered, feedback
  pending; `checkpoint-before-restart.json`), `am force-stop`, relaunch.
- Resume: the tickets list labels it **"Existing run: resume or reconcile; do not restart from
  zero"** → "Open saved attempt or result" returned the **same attempt** from the checkpoint.
- The q2 deadline had expired while frozen/blind (timer showed 0 on resume): dismissing the
  feedback closed the attempt as a **late completion** (checkpoint `COMPLETING`, outbox op 8) —
  its own designed branch (expired delivery closes without an answer; late completion settles
  once).
- Synchronize → server record `lesson_runner-SUBMIT_ATTEMPT-cf575a94…` completed,
  receipt **SETTLED, percentScore 50, codeAnswer "19"** (q1 correct, q2 unanswered),
  **skillPoints 18, nolics 2, lifePointsSpent 50, ticketId = frozen ticket f143…**.
  The receipt screen: "Verified score: 50% / Verified result code: 19 / Skill points awarded: 18
  / Nolics awarded: 2 / Life points used: 50".

## Two-receipt oracle

SETTLED receipts (`…-3da6a7c…` ticketId aab9…, `…-cf575a94…` ticketId f143…) and the
RATE_LESSON receipt (`…-1cdc7ca6…`, APPLIED, same lesson) are all under the owner uid, each
exactly once in `mutation_keys` (23 → 28 over the session, every step accounted: +1 ordinary
lesson, +1 live-o2 READY_ACK, +2 arena settles, +1 rating), each consistent with its frozen
ticket's terms (price 1, lifeCost 50, HARD, dayId 2026-10-09).

## Honest gaps vs the strict story letter

1. The resumed attempt completed with q2 **expired-unanswered** (late-completion branch), not
   with a second answered question — answering inside the resumed clock needs sighted timing the
   blind (FLAG_SECURE-black) rig screen cannot give; the restart-survival claim itself (same
   attempt, saved answer kept, 50% at settle) is proven.
2. The settle receipts and the rating receipt span the two frozen tickets of the same fixture
   rather than one ticket — both cycles are documented end-to-end, the pairing per ticket is
   visible in the records above.
3. `screencap` is black on all arena surfaces; visual QA of these screens needs a real device
   (or a non-secure debug surface) — flagged for the device-required list.

## Rig notes

- The runner's per-question clock does not tick while the process is dead; the deadline resumed
  and expired immediately on resume (frozen-deadline semantics, useful for 3.8).
- Tickets list order: reservations panel → tickets (old first) → lesson cards; the
  "Open saved attempt or result" affordances exist per ticket for both LAUNCHED (resume) and
  SETTLE-pending (recover) states — affordances reused later by 3.4/3.6 fault rows.
- ck3D balances after the block: 2 nolics (3 earned − 1 spent + 2 awarded), 31+18 skill points.
