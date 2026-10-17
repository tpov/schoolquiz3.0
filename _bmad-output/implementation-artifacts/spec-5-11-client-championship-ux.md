---
title: 'Story 5.11 — championship client journeys: agreement, match play, bracket and awards'
type: 'feature'
created: '2026-10-17'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '996e171435208b0adc74c3b7d80bbbe5f46120de'
route: 'full'
route_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The championship half of the client is absent: no agreement surfaces (propose/confirm/READY with the 5.7 restore projections), no match play over the session online contract, no bracket view of the immutable published state, no results/awards rendering with per-row receipts. The 5.10 tournament module built the pattern; the meeting projections (5.7), session online semantics (5.6) and conserving awards (5.8) are the server facts it renders.

**Approach:** Extend `android/feature/tournament/presentation` with the championship journeys in the 5.10 pattern: an AgreementComponent (per-match readMeeting render: proposals/confirmations/READY per participant with the C/slot/READY boundaries from the projection; propose/confirm/READY intents with honest boundary refusals; restore on re-entry by reading), a MatchPlayComponent (the match session through the 5.9 outbox — same machine as 5.10's qualification play, MATCH kind), a BracketComponent (immutable published bracket + placements from readEvent progress), and a ResultsComponent (placements + per-row prize receipts via the funding read… rendered from readEvent escrow/placements; receipts stay server-side facts, the client shows the escrow/placement truth). Wire the World/bracket destinations of the Events tab in the 5.10 mounting pattern. Functional journeys in current conventions; NOIR polish stays epic 9.

## Boundaries & Constraints

**Always:**
- PROPOSE never implies CONFIRM; the agreed slot is immutable; the client never starts a match — only the server does; boundary refusals (≤ C, ≤ slot, [slot, slot+1h)) render from the server's typed errors and the projected boundaries.
- Match play reuses the 5.6/5.9 session machine (outbox ANSWER/ADVANCE/READY_ACK, window by expiresAtMs) — no new transport; both opponents' facts come from the server projections.
- Screens render state and emit callbacks; AD-21 states; RU/UK/EN; testTags on slot/READY/money rows; the restore projections are read-driven (no local meeting drafts).

**Never:**
- No new server surfaces (the 5.7 projections suffice); no prize/arithmetic changes; no meeting-math edits; no final NOIR (epic 9); no qualification-surface regressions (5.10 tests stay green).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Propose/confirm flow | Two players coordinate a slot | Proposals and confirmations render per author; agreed slot shown; READY per participant | Boundary refusals typed and rendered; stale-page confirm refused by the server |
| READY edges | READY before slot / in window / after window | Honest accept/refusal states from the server | — |
| Re-entry restore | Reload mid-coordination | readMeeting rebuilds states identically for both participants | — |
| Match started | Server starts (both READY) | Play surface on the match session (5.6 semantics) | — |
| Bracket view | Event in BRACKET/FINISHED | Rounds/matches from readEvent progress + matches; placements for FINISHED | — |
| Awards | FINISHED with escrow terminal | Placements + escrow truth rendered (paid/returned/remaining=0) | — |

## Code Map

- `android/feature/tournament/presentation` — the 5.10 module: component/screen/DI/test layout, `TournamentNotice` AD-21 vocabulary, mounting pattern in app-shell.
- `shared/feature/tournament/{domain,data}` — `TournamentMeetingRemoteDataSource.readMeeting` (proposals/confirmations/ready/participants/boundaries), `TournamentOperationRemoteDataSource.operate` (PROPOSE/CONFIRM/READY kinds), session outbox for MATCH play, `readEvent` progress/matches/placements/escrow.
- `functions/course-tournament-controller.js` — readMeeting projection shapes (5.7); typed boundary errors.
- Strings/test/testTag/AD-21 precedents: 5.10's module files; `ArenaEntryDialogs` for confirm-style dialogs.
- Evidence: `docs/agents/evidence/2026-10-17-championship-client/README.md` — NEW.

## Tasks & Acceptance

**Execution:**
- [x] `android/feature/tournament/presentation` -- AgreementComponent (+screen), MatchPlayComponent (reuse the 5.10 play machinery for MATCH kind), BracketComponent, ResultsComponent; strings RU/UK/EN; testTags; mounting for the championship destinations in the 5.10 pattern.
- [x] Tests -- JVM component tests per journey (agreement persistence/immutability/boundaries/restore, match play reuse, bracket/awards render from projections), wiring extended for the new destinations.
- [x] `docs/agents/evidence/2026-10-17-championship-client/README.md` -- journeys→components→tests, honest rig-visual notes.

**Acceptance Criteria:**
- Given two coordinating players, when proposals/confirmations/READY happen, then each re-entry renders the identical server facts with per-author attribution and boundary refusals render honestly.
- Given an agreed slot and both READY, when the server starts the match, then the play surface runs the session machine (window/READY_ACK) and the result flows to the bracket.
- Given a FINISHED event, when results open, then placements and the escrow truth render with terminal remaining = 0; the bracket is immutable across re-reads.
- Given the gradle gates (tournament presentation + app-shell + compile) and the Node gate, then all pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass 1 (lenses: blind-hunter 19, edge-case-hunter 14, verification-gap 3 gaps + 3 other, intent-alignment 0 defects)
- verdicts: 39 findings — high 1, medium 12, low 24, false 2
- findings (grouped; the fix round verified each claim against the code):
  - `[high]` `[patch]` FAILED_PRECONDITION remapped globally to MEETING_BOUNDARY — the QUALIFICATION journey's start refusals (OUTSIDE_QUALIFICATION_WINDOW, closed qualification) rendered a meeting message; verified via tournament-service.js refusal sites. Fixed: agreement-surface-scoped mapping + qualification test.
  - `[medium]` `[patch]` The placement slot Long→String wire fix (required for every FINISHED read) shipped without a parser test — fixed: real eventReadFinished fixture + decode/fail-closed cases.
  - `[medium]` `[patch]` Row Confirm overridable by stale numeric input; Confirm buttons never retire on an agreed slot — fixed: rows always ride their slot; rowConfirmEnabled (agreed/own-confirmed/busy); tests.
  - `[medium]` `[patch]` Window countdown fabricated an epoch-sized number when serverNowMs was absent (match play) — fixed: line hidden without server time; the identical qualification-screen defect fixed by the orchestrator post-round.
  - `[medium]` `[patch]` Async-scoping races: stale in-flight applies (admission courseId, browser generation missing, bracket detail reopen, refresh cursor loss), previous match's playSessionId/observer surviving opens, overlay flags raised on refused opens, post-refusal re-read overwriting the refusal notice, duplicated notice writers, window-edge re-read bypassing the busy guard and failing silently, late-delivery stall, meeting hasMore=null cursor loops — all verified and fixed with tests (several claims disproved as already-guarded and left).
  - `[medium]` `[patch]` Agreement UX integrity: empty-state do-nothing controls, two close semantics (system back vs on-screen), raw epoch timestamps as copy, invented seed 0, status string literals, dead strings (one wired, one removed) — fixed across locales.
  - `[medium]` `[patch]` Missing agreement tests: stale-page CONFIRM refusal and after-window READY — added.
  - `[medium]` `[defer]` Overlay-composition verification (bracket→agreement→play/results callbacks in TournamentChampionshipContent) — no JVM Compose harness exists; components on both sides are tested; introducing a harness exceeds this change.
  - `[low]` `[reject]` Extracted play machine's four state writes — no suspension point between them; intermediate states unobservable (documented).
  - `[low]` `[reject]` Unknown question type stays a silent no-op submit — flagged by the fix round for the owner list (same posture as empty selection; needs a product decision, not a one-liner).
  - `[low]` `[reject]` readMeetingInto generation captured inside the coroutine (pre-existing close-launch window) — noted for a future pass, not a review finding.
  - `[low]` `[reject]` Spec Intent ellipsis/self-retraction (per-row receipts vs escrow truth) — fix-would-edit-this-spec; as-built follows the final clause and the evidence.
  - `[low]` `[reject]` (intent-alignment, descriptive) journey-level testing below the rendered surface (no emulator); match-play recovery bounded by the server projection (readMeeting carries no session id — honest limitation recorded); world-shelf entry points displaced by the destination swap (epic 9 cleanup owner); zero functions changes (required).
  - `[false]` `[reject]` Two claimed race paths were already guarded in the delivered code (window-edge pre-check; unknown-type notice) — verified by the fix round.


## Design Notes

This story is the 5.10 pattern applied to the meeting/match projections — no new machinery. The boundary values rendered to the client come from the 5.7 projections; the UI never derives policy. Receipts stay server-side; the client shows placements + escrow truth, not fabricated per-row money lists.

## Verification

**Commands:**
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:tournament:presentation:test :android:feature:app-shell:presentation:testDebugUnitTest :apps:android-next:compileDebugKotlin --no-configuration-cache` -- expected: green.
- `cd functions && node run-tests.js` -- expected: 111/111 (no server changes expected).

**Manual checks (if no CLI):**
- Evidence README maps journeys → components → tests; rig visuals to the owner list.

## Auto Run Result

- Summary: the championship client journeys are live — agreement (propose/confirm/READY with
  per-author restore from the 5.7 projections, boundary refusals rendered honestly, retiring
  confirm buttons, readable times), match play on the extracted session machine (MATCH kind,
  window by expiresAtMs with hidden fabrication, outbox transport), bracket view of the immutable
  published state, results with placements + escrow truth — mounted on the WorldChampionship
  destination with observable wiring. One required shared-mirror fix (placement slot is a name
  string — every FINISHED read would have failed closed) now carries its own parser contract
  tests. No new server surfaces.
- Files changed: `android/feature/tournament/presentation` (4 components + screens + strings +
  34 tests), app-shell mounting + tests, `shared/feature/tournament/{domain,data}` (slot type
  fix + fixtures + parser tests), evidence README, this spec.
- Review findings breakdown: 39 findings — high 1 (FAILED_PRECONDITION global remap), medium 12,
  low 24: the fix round patched the high, 11 mediums and the actionable lows, disproved 2
  already-guarded claims, and left three flagged items (unknown-type silent submit — owner
  product decision; generation-capture window — future pass; countdown parity — fixed by the
  orchestrator post-round). Deferred: 1 (overlay-composition harness).
- Follow-up review recommendation: TRUE — rendered-surface claims are component-tested only; the
  owner rig-visual pass (FLAG_SECURE strings) is the follow-up, same as 5.10.
- Verification performed: tournament presentation 65/65; app-shell 54 tests 0 failed 2 skipped;
  tournament data jvmTest 41/41; apps compileDebugKotlin green; Node 111/111 (no server changes).
- Residual risks: rig visuals pending (owner); match-session recovery after process death bounded
  by the server projection (documented); unknown-type silent submit (owner list); orphaned
  TournamentEventScreen retained for an androidTest (epic 9 cleanup).
