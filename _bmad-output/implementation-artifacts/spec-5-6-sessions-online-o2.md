---
title: 'Story 5.6 — tournament and championship sessions adopt ONLINE-O2/AD-31 per the adopted ONLINE-O1 contract (Appendix A executor)'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '0d0cec590113cddc4645e3d7a8c7eb5e3a9ef5f4'
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

**Problem:** Tournament (QUALIFICATION and MATCH) sessions still start the next question's clocks in the same operation that closed the previous one (`tournament-session.js:247-250` era behavior): no 60-second delivery window exists, no ready-ACK receipt, the pause between questions is unbounded, and AD-31's window/expiry semantics are not implemented — exactly the divergence the adopted ONLINE-O1 contract's Appendix A1–A9 enumerates and the ONLINE-O1/O2 gates require fixing before the mode counts as conformant.

**Approach:** Execute the adopted contract's implementation delta (A1–A9 are the binding change list; the contract's D1–D9 are the law): window state per session (deterministic identity `(sessionId, completedQuestionIndex)`, captured config incl. `windowSeconds = min(60, envelope remainder)`, `openedAtMs`/`expiresAtMs` persisted), delivery in the closing operation's response or on a next-question request with server-remaining time, question clocks anchored at server delivery with the `endsAtMs` clamp kept, per-question ready-ACK receipt `(sessionId, questionIndex, windowId)` (not for question 0 — start contract), window-expiry termination via the existing `FORCED_CUTOFF` state with `closedAtMs` = captured edge (sweep + at-delivery double closure), visibility requires delivery, envelope immutability untouched, scoring untouched, C/READY untouched. Compose with `functions/lesson-question-window.js` mechanics as a wrapper — that file is NOT edited.

## Boundaries & Constraints

**Always:**
- Every decision D1–D9 and appendix row A1–A9 of the adopted contract (owner adoption recorded 2026-10-16, decision 1, sha256 fa9ca506…) holds exactly; §8.4 requires re-verifying the contract's file:line pointers at this story's start — record the re-verification in the evidence note.
- Envelope `endsAtMs` never moves (structural guard stays); windows live only inside `min(60s, envelope remainder)`; the window edge closes two ways (sweep and delivery-time verdict) so delivery after the edge is impossible.
- Both championship opponents receive the same HARD form/fingerprint; per-attempt delivery moments differ but clocks never become shared.
- Meetings (PROPOSE/CONFIRM/READY) stay window-free; qualification entry rules and the round-entry economics are untouched.
- Redaction: windows, ACKs and terminations never leak question/answer material into DTOs or logs.

**Never:**
- No edits to `lesson-question-window.js`, `online-o2-contract.md`, the O1 contract, Arena/exam lifecycles; no second answer clock; no window after the last question or before question 0; no new ACK number for question 0; no changes to scoring arithmetic, prize list, admission, or meeting math; no client code.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Window opens | Question closes (timely answer or original expiry), envelope remainder ≥ 60s | Window doc/state with captured config (60s, indices, next question id, maxDuration, endsAtMs, fingerprint, openedAt/expiresAt); next question delivered in the same response | Retry of the closing operation replays identically (O2 §7) |
| Delivery anchors clocks | Delivery inside the window | `questionStartedAtMs = deliveredAtMs`; `questionEndsAtMs = min(deliveredAt + maxDuration, endsAtMs)` | Repeated delivery requests return the same question with server-remaining time, never re-anchor |
| Window near envelope edge | Closing at `endsAtMs − 10s` | `windowSeconds = 10` captured; expiry = envelope edge | Zero/negative remainder: no window, immediate termination per D4 |
| Undelivered window expires | Sweep or a delivery attempt after the edge | Attempt settles: remaining questions closed unanswered, kind `FORCED_CUTOFF`, `closedAtMs` = captured edge (two paths, one state) | Late client gets the settled response (existing settled path) |
| Ready-ACK | Delivered question 1..19 acknowledged | Idempotent receipt keyed `(sessionId, questionIndex, windowId)`; ACK never anchors or restarts deadlines | Same key same body → same receipt; same key different body → conflict |
| Question 0 | Delivered by the start contract | No window, no ready-ACK; retry returns the same clocks | — |
| Last question | Question 19 closes | No window; session settles by existing scoring | — |
| Match symmetry | Both opponents' sessions run | Same form/fingerprint; independent windows/clocks | — |
| Reconnect | Client returns mid-window or mid-question | Same question with server-remaining time; clocks never restart (existing read path + delivery state) | — |

## Code Map

- `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` — THE ADOPTED LAW: §2 current-behavior inventory (re-verify pointers per §8.4), D1–D9, §4 budget table, §5 O2 composition, Appendix A1–A9.
- `functions/tournament-session.js` — the session machine to extend: closure kinds (:227-229 era), clock seeding (:247-250 era), visibility (:142), timeliness (:210/214), envelope guard (:121), settled path (:186-192), worker advance (:299-307). Locate by symbol; lines shifted since the contract's baseline.
- `functions/lesson-question-window.js` — window mechanics to COMPOSE (states, deterministic id, captured config, verdicts) — read-only.
- `functions/tournament-service.js` — session lifecycle wiring, `sessionView`/read paths, worker sweep entry (`advanceTournamentSession`), prizeView/admission context (5.3/5.4 land).
- `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` — the adopted O2 mechanics (§2 completion events, §3 window, §4 ready-ACK, §5 clock, §7 lost ACK).
- `functions/arena` client-half precedent (shared/ ArenaOnlineO2Adapter) — server-side shape precedent lives in `functions/lesson-question-window.js`; do not touch client code.
- Tests: `functions/tournament-session.test.js`, `tournament-service.test.js` (existing session/advance suites — extend, keep green), `course-tournament-controller.test.js` (read projections through the window), `tournament-storage-contract.test.js` (if a new collection/subcollection appears, extend the deny-list check), `scripts/course-tournament-private-rules.test.js` (same).
- Evidence: `docs/agents/evidence/2026-10-16-competition-sessions-online-o2/README.md` — NEW.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-session.js` (+ `tournament-service.js` wiring) -- implement A1–A9: window state with captured config and persisted openedAt/expiresAt, delivery-anchored clocks, ready-ACK receipts, WINDOW-EXPIRY terminal via FORCED_CUTOFF with captured-edge closedAtMs, delivery-time edge verdict, visibility requiring delivery, sweep extension (bounded, AD-23).
- [ ] Tests -- extend the session/service suites with every matrix row (window open/capture/retry, edge caps, expiry termination both paths, ACK idempotence and conflict, question-0 exemption, last-question no-window, match symmetry, reconnect); storage/rules contract extensions if new paths appear.
- [ ] `docs/agents/evidence/2026-10-16-competition-sessions-online-o2/README.md` -- pointer re-verification record (§8.4), A1–A9 → what was done, test outcomes, honest notes.

**Acceptance Criteria:**
- Given a question closing with ample envelope, when the closing operation returns, then the next question is delivered with a captured 60s window and the next clocks anchored at that delivery; a retried closing operation replays the identical outcome.
- Given an undelivered window expiring before the envelope edge, when the sweep (or a late delivery attempt) runs, then the attempt settles with unanswered remainder, kind FORCED_CUTOFF, closedAtMs = the captured edge — exactly once, idempotently.
- Given a ready-ACK for a delivered question, when the same key ACKs again, then the same receipt returns; a different body under the same key conflicts; question 0 has no ACK path.
- Given the full `node run-tests.js`, then all suites pass including the new window cases; `git status --porcelain -- shared/ android/` stays empty.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 14, edge-case-hunter 7, verification-gap 0 gaps + 3 other, intent-alignment 0 defects)
- verdicts: 24 findings — high 0, medium 6, low 15, false 3
- findings:
  - `[medium]` `[patch]` `wasShown: false` persisted for a question delivered and answered in the same operation (audit integrity: a served question recorded as never shown) — verified in close(); fix applied: the in-operation delivery clears the undelivered flag before slots are written.
  - `[medium]` `[patch]` READY_ACK after the answer deadline (before any closure) recorded post-deadline readiness — fix applied: typed refusal «Ready-ACK after the answer deadline», pinned by test, terminating nothing (same-key idempotent replay still returns the stored receipt).
  - `[medium]` `[patch]` (verification-gap gap 1) the client-driven closing response's `session.window` (windowId/state/expiresAtMs — the READY_ACK key source) was never asserted; deleting the block kept all suites green — fix applied: response-window assertions in the session suite.
  - `[medium]` `[patch]` (verification-gap gap 2) the worker's window-edge `nextDueAtMs` ran unasserted; a regression would hot-loop sweeps and delay settles — fix applied: both sweep branches pinned to `stored.window.expiresAtMs`.
  - `[medium]` `[patch]` `windowSeconds` ceil could overstate the captured lifetime against the exact `expiresAtMs` (client ambiguity) — resolved by contract semantics: ceil keeps a window alive for any positive remainder; `expiresAtMs` (clamped exactly to the envelope edge) is documented as the authoritative lifetime in code, the evidence note, and the client guidance for 5.9/5.10.
  - `[medium]` `[patch]` (orchestrator bookkeeping) duplicate YAML `review`/`review_source` keys in the spec frontmatter — deduplicated, single pair verified.
  - `[low]` `[patch]` Window-state string literals in `checkedBoundaryWindow` instead of the imported WINDOW_* constants — replaced.
  - `[low]` `[patch]` Session document byte budget unexamined with the full ACK ladder — added a test: 18 receipts + window stay under MAX_DOCUMENT_JSON_BYTES.
  - `[low]` `[patch]` Backward-compatibility for pre-window sessions undocumented — evidence note added (both new fields optional; pre-deploy sessions keep old behavior until their first question closure).
  - `[low]` `[patch]` §8.4 re-verification table row self-contradicted («+закрывающая скобка» vs «без сдвига») — row reworded with the honest reading.
  - `[low]` `[patch]` After-edge READY_ACK behavior unpinned — pinned by the same new test (refusal, no termination).
  - `[low]` `[patch]` Worker sweep scheduling reads the redacted projection — the field set is now pinned by the nextDueAtMs assertions; a dropped field can no longer pass silently.
  - `[low]` `[reject]` Malformed-window reconnect reads throwing typed UNAVAILABLE — consistent with the pre-existing corrupt-session discipline (session clock malformed → SCORING_UNAVAILABLE); reachable only via externally corrupted state; no code path in this change produces it.
  - `[low]` `[reject]` `sessionView` not carrying `window` on the read path — by design: reads return the window at the response top level (asserted), sessionView is the internal planning shape.
  - `[low]` `[reject]` Spec self-references (matrix «delivered in the same response» not carrying the serverDriven split; O2 §7 cited where the O1 receipt discipline belongs; empty Implementation Notes) — fix-would-edit-this-spec findings; the conditional delivery is the executor's flagged interpretation recorded in the evidence note, the tickets comment, and this log; rejected by rule.
  - `[false]` `[reject]` «EXPIRED state written but never recognized by the validator» — refuted: `checkedBoundaryWindow` validates LIVE boundaries only; a settled session's window is history (early return), and the expiry path writes the terminal state outside that validator.


## Design Notes

The contract's Appendix A is deliberately precise — follow it row by row and record any deviation as a contract edition question rather than inventing locally. The riskiest corners: (a) the double closure (sweep vs delivery verdict) must be one idempotent state transition; (b) retry/replay of the closing operation must survive the window being open (the receipt discipline extends, not replaces); (c) MATCH sessions share the form but not the clocks — do not accidentally couple the two players' window state.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node run-tests.js` -- expected: all suites pass.
- `git status --porcelain -- shared/ android/` -- expected: empty.

**Manual checks (if no CLI):**
- Evidence README records the §8.4 pointer re-verification and maps A1–A9 to the changes.

## Auto Run Result

- Summary: tournament (QUALIFICATION and MATCH) sessions now run the adopted ONLINE-O2/AD-31
  semantics per the O1 contract's A1–A9 — window state on the session doc with deterministic
  identity and captured config (60s constant capped by the envelope remainder; persisted
  openedAt/expiresAt), client-driven closes deliver in-response while worker closes leave the
  window open for claiming, delivery anchors question clocks once with the envelope clamp kept,
  a per-question ready-ACK receipt keyed (sessionId, questionIndex, windowId) with question-0
  exemption, window-edge termination from both sweep and delivery-time verdict into the existing
  FORCED_CUTOFF state with closedAtMs = captured edge, visibility requiring delivery, envelope
  immutability and scoring untouched, meetings untouched; `lesson-question-window.js` composed
  as vocabulary, never edited.
- Files changed: `functions/tournament-session.js`, `functions/tournament-service.js`,
  `functions/course-tournament-controller.js`, three test suites (session 41→60, service 47→50,
  controller 46→49 cases), evidence README, this spec.
- Review findings breakdown: 24 findings — medium 6 (wasShown race, post-deadline ACK, response
  window coverage, sweep scheduling coverage, windowSeconds authority resolution, frontmatter)
  and low 9 patched; 3 low rejected (corrupt-state read discipline, sessionView shape, spec-edit
  rules with the interpretation flags routed to the evidence note and tickets comment); 3 false
  refuted. Deferred: none.
- Follow-up review recommendation: TRUE — the wasShown/ACK-guard/window-seconds changes are
  behavior-bearing patches applied by the orchestrator with full-suite re-verification (109/109)
  but without a fresh lens pass over the amended machine; a follow-up pass should re-examine the
  amended session state machine before 5.8 builds conservation proofs on it.
- Verification performed: full `node run-tests.js` → 109/109 suites pass after every patch
  round; §8.4 pointer re-verification recorded in the evidence README; `git status --porcelain
  -- shared/ android/` empty; frontmatter YAML single-pair verified.
- Residual risks: the serverDriven delivery split and reads-never-terminate are executor
  interpretations of the adopted contract (flagged for the owner with the adoption record, not
  silently decided); production-scale window behavior awaits the rig; pre-deploy sessions are
  tolerated (optional fields) per the evidence note.
