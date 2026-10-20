# 3.9 rig session 1 — ticket issued, account isolation proven, device sees own ticket — 2026-10-20

Samsung A14; client A = com.tpov.schoolquiz.mvp (uid qopc1zUv4U2QSrpYKGgdXeXYbdi8,
premium grant from the night fixture); client B = synthetic HTTP account
rig39opp000000000000000000000001 (custom token via the auth emulator — the accepted 5.13
opponent precedent); device injection protocol: uiautomator bounds only.

## Proven today

- **A ticket issued** (rig prepareArenaEntry, HARD random): 84174aaa491c31c9…060bfe,
  lesson ac_7ad0e33f…_lesson_1, 0 nolics + lifeCost 50 (RESERVED). EASY default refused
  NO_ELIGIBLE_ARENA_LESSON — the arena is HARD-only; noted as client default finding.
- **Device A sees its own ticket** (39a-scr2.png): «Дозвіл: 84174aaa… — Зафіксований
  резерв: 0 ноликів і 50 очок заряду» after «Знайти дозволи на сервері»; the same hold
  renders in the balance card («доступно 450 · в резерві 50», 39a-tickets2.png).
- **B sees nothing and cannot steal** (HTTP): listArenaEntryTickets for B →
  {"tickets":[]} ; fetchArenaEntryTicket(A ticketId) as B → 404 NOT_FOUND (no existence
  leak). No-adoption server half PROVEN.
- 39a-tickets2.png also shows the prep-refusal history cards (undecidable-intent
  prevention: earlier EASY attempts left honest retry cards).

## Remaining for 3.9 (next scene, fully staged)

- A: start the attempt from the prepared ticket, answer, settle → exactly ONE settlement
  (server: SETTLED; ledger one movement).
- Late answer: second settle with a fresh attemptId → refused (INCONSISTENT/
  TICKET_ATTEMPT_MISMATCH), zero ledger movement; rig settle replay → stored response,
  no money.
- Same-ticket-on-two-physical-devices: still hitl (ticket says so); two-clients-on-one-
  device B variant needs the .mvpb package registered in the Firebase project (FIS_AUTH_ERROR
  today) or the HTTP opponent substitutes, as here.

## Build infra added

- apps/android-next buildType «mvpb» (initWith mvp, suffix .mvpb) + src/mvpb/google-services.json
  (copy of .mvp with renamed package — Firebase project registration still required for FIS).
