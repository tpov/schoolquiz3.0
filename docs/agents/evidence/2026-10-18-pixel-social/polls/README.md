# Poll series run (8.4) — device proof on TWO phones — 2026-10-18 late evening

Devices: Pixel 10 Pro (UserG9FY6E, uid VLqW…) + Samsung A14 SM-A145F (fresh install of the
same 4.0.0-isolated build, new anonymous account UserOVDSRF), both over the live
device→VPS transport. Evidence shots 01–09 in this directory.

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

## What FAILED first — real defect (was blocking 8.4 completion)

The Samsung's vote never landed: the outbox attempt returned «Unknown», and the server's
idempotency gate denied it with **"mutationId belongs to another account"**
(functions/mutation-queue.js gateVerdict → belongsTo → DECISION_DENIED).

Root cause: the vote idempotency key had no respondent dimension.
- Client: `PollOutboxWriters.voteSourceId` = `series-<id>-r<rev>-q<index>` and
  `OutboxOperations.mutationKey = operation + "-" + sourceId` — so EVERY participant voting on
  the same question derived the SAME mutationId.
- Server: the first delivery reserved that key under the first participant's uid; every other
  participant was then denied as a foreign replay.
- Consequence: «percentages over distinct respondents per question» (AD-28/UX-O7 §3) could
  never exceed one respondent through the app.

## RESOLUTION (same night) — fix applied and verified on two devices

The vote sourceId now carries the respondent:
`series-<id>-r<rev>-q<idx>-u<uid>` (PollOutboxWriters.voteSourceId; UX-O7 §2 amended in the
contract with a pending-owner note; contract test UxO7PollVoteIdentityContractTest gained a
per-participant regression, PollOutboxWritersTest updated). Rebuilt APK sha256 9d0908c0…,
installed fresh on the Samsung (pm clear → new uid dJgxj3m64t…, UserOVDSRF).

Result (09-two-participants-50-50.png): the Samsung voted blue with its OWN key —
`mutation_keys` shows TWO completed SUBMIT_VOTE entries with different uids — and the question
screen reads «Ваш голос принят: red 50% / blue 50%, Ответило: 2». Retry dedup stays per
participant by the same key formula (§6.1). 8.4 → done, pending the owner's nod on the §2
wording.
