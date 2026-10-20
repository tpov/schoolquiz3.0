# 3.9 CLOSED (device + rig sessions 1-7) — 2026-10-21

Client A = Samsung .mvp (uid qopc1z...); opponent = synthetic HTTP account (5.13 precedent).

## Proven end-to-end
1. Ticket issued (prepare, HARD random), hold 0 nolics + 50 charge points, visible in the
   device UI with the exact frozen reserve (session 1).
2. Synthetic opponent: owned-list EMPTY; foreign ticket fetch by exact id -> 404 NOT_FOUND
   (no existence leak) - no adoption of another session work, server half PROVEN (session 1).
3. UI wiring: the prepared ticket starts from the LOCAL permissions card button
   (session 5); the local lesson card refuses honestly (arena questions ride the ticket,
   session 3); the rig-issued ticket (no local record) fail-closes the attempt door
   honestly (session 2).
4. Full play on device: attempt 2/2 questions answered, completion -> pending Arena
   receipt (session 5).
5. Settlement delivered via the shared lesson-result path (submitLessonResultEvents with
   the Room checkpoint payload + lesson fields): SETTLED exactly once - ticket 8c206849
   SETTLED; receipt attempt:ba80ca18..., lifePointsSpent 50, percentScore 0 (honest empty
   answers), reward 0.
6. Late answer / second attempt on a settled ticket refused: replay of the same settle ->
   the STORED receipt, no new effect; a fresh attemptId on the settled ticket ->
   409 TICKET_ATTEMPT_MISMATCH. One shared ticket yields at most one settlement PROVEN.

Payload sent is attached (settle-att.json).

## Honest gaps
- The receipt-screen sync button does not deliver the pending settle (outbox drain
  defect) - separate client defect; the settle traveled through the shared result path.
- Same ticket on two PHYSICAL devices: hitl tail; the refusal sides are proven by the
  owner-mismatch checks.
- The rig ticket 84174aaa stays RESERVED (holds 50) - cancel via cancelArenaEntry when
  the account returns to play, or leave as fixture residue.

## ROOT CAUSE of the settle delivery defect (found 2026-10-21)

Device outbox row: lesson_runner.SUBMIT_ATTEMPT, state=CONFLICT, last_error=TermsConsentRequired.
The server gates SUBMIT_ATTEMPT behind fresh payable-terms consent (MUTATION_TERMS_OPERATIONS);
the client HAS the full consent machinery (EconomyTermsConsentGate: capturedTerms/verdict/
acceptCurrentTerms — acceptCurrentTerms returns waiting outbox rows to sending), but
**acceptCurrentTerms is not wired to any UI**: no screen calls it, so the player can never
consent and every paid mutation (all of Arena) silently dies in CONFLICT.

Fix scope (next client session): consent UI flow — on TermsConsentRequired show the terms
dialog, acceptCurrentTerms(), re-enqueue manual sync; plus JVM tests and gates. This is the
7.4-style wiring fix, half a session.

## Session 7 addendum (2026-10-21, later) — requeue tail diagnosis, honest stop

- The requeue SQL criterion is CORRECT: UPDATE outbox SET state=WAITING WHERE
  state=CONFLICT AND operation IN (lesson_runner.SUBMIT_ATTEMPT) — the device row matches.
- Fresh Room pull after the accept tap: the row is STILL CONFLICT and no consent store
  file appeared in the app dir → acceptCurrentTerms did NOT complete on-device. Most
  likely the suspend accept-lambda threw silently (the host scope has no exception
  handler) or the finger/injection tap missed the dialog button — the two are not yet
  separated.
- Next: debug (mvp) logging around the consent store write, or a tiny coroutine exception
  handler in ArenaRunHost.acceptTerms, then re-run the dialog accept with exact
  uiautomator bounds. The JVM test already proves the wiring contract.
