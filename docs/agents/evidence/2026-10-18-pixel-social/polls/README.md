# Poll series run (8.4) — device proof on TWO phones — 2026-10-18 late evening

Devices: Pixel 10 Pro (UserG9FY6E, uid VLqW…) + Samsung A14 SM-A145F (fresh install of the
same 4.0.0-isolated build d0c59bfc…, new anonymous account UserOVDSRF), both over the live
device→VPS transport. Evidence shots 01–08 in this directory.

## What PASSED

- **Authoring on device** (02, 03): «Новая серия» form → title, question text, SINGLE mode
  (no correctness/difficulty fields anywhere — per AD-28), two options → «Опубликовать» →
  «Серия отправлена, ждёт подтверждения сервера» → outbox row `polls.PUBLISH_SERIES` →
  delivered on a later drain → `poll_series/poll-kv3iqzrra7yd` on the backend (revision 1,
  options opt-1 red / opt-2 blue).
- **Discovery + pre-vote hiding** (04, 05, s4): the series appears in «Опросы» on BOTH devices
  («Ответлено: 0 из 1»); the question screen shows options with NO distribution and the exact
  hint «Распределение откроется после вашего голоса» — §5 hiding enforced.
- **Vote + confirmation + distribution reveal** (06, 07): Pixel voted red → queued → the
  mutation key `polls-SUBMIT_VOTE-series-poll-kv3iqzrra7yd-r1-q0` reached state `completed`
  (exactly-once) → the question screen flipped to «Ваш голос принят — red 100% / blue 0% —
  Ответило: 1».
- **Second participant, second account, second device** (08): the Samsung (fresh anonymous
  uid) saw the same series, selected blue, voted.

## What FAILED — real defect (blocks 8.4 completion)

The Samsung's vote never lands: the outbox attempt returns «Unknown», and the server's
idempotency gate denies it with **"mutationId belongs to another account"**
(functions/mutation-queue.js gateVerdict → belongsTo → DECISION_DENIED).

Root cause: the vote idempotency key has no respondent dimension.
- Client: `PollOutboxWriters.voteSourceId` = `series-<id>-r<rev>-q<index>` and
  `OutboxOperations.mutationKey = operation + "-" + sourceId` — so EVERY participant voting on
  the same question derives the SAME mutationId (`polls-SUBMIT_VOTE-series-poll-kv3iqzrra7yd-r1-q0`).
- Server: the first delivery reserves that key under the first participant's uid; every other
  participant is then denied as a foreign replay (which also means they can never vote, while
  the first participant's retry stays correctly deduplicated).
- Consequence: «percentages over distinct respondents per question» (AD-28/UX-O7 §3) can never
  exceed one respondent through the app. On ONE device everything passes; the second
  participant is exactly what breaks.

Fix direction (owner decision on the key contract, UX-O7 §2): include the respondent in the
vote sourceId (e.g. `series-<id>-r<rev>-q<idx>-<uid>`) — per-participant retry dedup is kept,
and the per-participant uniqueness of the vote document itself stays with the deterministic
vote doc id the receiver writes. Server retry ceilings/receipts unchanged.

## Status

8.4 stays blocked with a PRECISE defect: not a test failure — a key-contract gap that one
device cannot expose and two devices catch immediately. The one-device journey (author →
publish → hidden distribution → vote → confirmed → revealed) is fully proven and recorded.
