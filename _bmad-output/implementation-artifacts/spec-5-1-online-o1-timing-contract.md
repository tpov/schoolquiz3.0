---
title: 'Story 5.1 — ONLINE-O1 reconciliation contract: AD-31 60s window vs tournament fixed cutoffs'
type: 'feature'
created: '2026-10-16'
status: 'done'
baseline_revision: '280593004e154f7ef333a512af6c77bb48352eca'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 1
followup_review_recommended: false
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The ONLINE-O1 gate forbids enabling any competition session adapter until the shared AD-31 60-second next-question window is explicitly reconciled with the tournament's fixed timing — session `endsAtMs` (hard cutoff with `FORCED_CUTOFF`), per-question deadlines clamped into it, championship coordination deadline C = stage end − 1 h − form duration, READY window [slot, slot+1 h), and the qualification cohort cutoff — with no silent window truncation and no silent stage/session extension. Today the tournament sessions have no window at all, and the composition is undecided.

**Approach:** Author one versioned contract document, `online-o1-timing-contract.md` edition 1, that decides every composition point explicitly (decision + budget/priority rationale), composes with the adopted `online-o2-contract.md` mechanics, and carries an implementation-delta appendix mapping each decision onto the current policy code so story 5.6 can execute without re-deriving. No production code changes in this story: the contract precedes the adapter.

## Boundaries & Constraints

**Always:**
- Server/UTC authority for every clock; original question deadlines run independently of connection (AD-31).
- Every composition decision is explicit and names its time budget and priority; the contract states, per surface, what bounds what.
- The window default stays the captured 60 s server constant; event terms freeze the config they captured.
- The contract is versioned (edition header + change log) and ends with a named owner-adoption step; until adoption, dependent stories stay gated.
- Honest divergence reporting: wherever the contract changes current behavior, the implementation-delta appendix names the exact current behavior (file-level pointer) that will change.

**Never:**
- No production/config code changes, no callable changes, no test-behavior changes in this story (policy suites must stay green untouched).
- No silent truncation of the promised 60 s window and no silent extension of session endsAt, stage ends, match duration, or the qualification cohort cutoff — a bounded exception is allowed only when the contract states it as an explicit budget decision with rationale.
- No new answer time, no second answer clock, no window after the last question, no answer-clock restart (AD-31 non-negotiables).
- No edits to `online-o2-contract.md`, Arena or exam lifecycles; no renaming of турнир/чемпионат terms; no owner-adoption claim (adoption is the owner's act).

## I/O & Edge-Case Matrix

The deliverable's "I/O" is its decision table. Each row must appear in the contract as an explicit decision.

| Scenario (tension) | Input / State | Expected contract decision | Error Handling |
|---|---|---|---|
| Window vs session cutoff | Timely answer at `endsAtMs − 1 s`; 60 s window would cross `endsAtMs` | Which wins for this session kind, and what the crossing question scores (AD-31 expiry vs FORCED_CUTOFF semantics) | Explicit priority + budget rationale |
| Window vs cohort cutoff | Qualification session window crossing `qualification.endsAtMs` | Whether the cohort cutoff is absolute (attempt after cutoff never enters the cohort) or the window extends it | Explicit; cohort integrity named |
| Window vs stage/match end | Match session window crossing stage `endsAtMs` or `resultDue` | Whether the stage end absorbs windows (budget inside stage slack) or the match resolves as technical outcome per matrix | Explicit; no silent stage shift |
| Window vs question clamp | `questionEndsAtMs = min(now + q.maxDurationMs, endsAtMs)` while a previous window is open | Whether the clamp consumes window time or windows ride outside the clamp | Explicit definition of captured config |
| BACK_TO_BACK windows | Consecutive losses (expiry → window → expiry → …) inside one session | Cumulative budget rule (bounded by remaining session/stage slack, never unlimited) | Explicit cap and terminal state |
| First question | Session start under AD-31 ("first question keeps its own bounded start contract") | Named start rule for tournament sessions (existing start contract cited, no new ACK number invented) | Explicit citation |
| READY/coordination boundaries | Proposal/confirmation/READY near C under window semantics | Confirmation that C and READY math are unchanged, or an explicit adjusted formula | Explicit; AD-17 precedence |

## Code Map

- `functions/tournament-session.js` -- the timing reality to reconcile: `endsAtMs = startedAtMs + form.maxDurationMs` immutable (line ~121), per-question `questionEndsAtMs = min(now + q.maxDurationMs, endsAtMs)` (~249), timeliness window `min(questionEndsAtMs, endsAtMs)` (~210/214), `FORCED_CUTOFF` sweep at `endsAtMs` (~229), current-question visibility (~142).
- `functions/tournament-service.js` -- session creation and operation wiring — REQUIRED for every cutoff-adjacent decision: START creates the qualification session with the envelope CLAMPED to the cohort cutoff, `endsAtMs: Math.min(add(at, form.maxDurationMs), event.qualification.endsAtMs)`, under the deliberate comment "Qualification closes at its fixed cutoff, including a late-started timed-out session" (~219-220); CLOSE_QUALIFICATION loads the ranking and builds the bracket (~227-251); `makeSession` (~88-93) seeds first-question clocks from `endsAtMs`; `sessionView` (~451-454) is the redacted read projection. A D2 drafted without this file contradicts actual behavior.
- `functions/tournament-policy.js` -- constants `READY_WINDOW_MS=1h`, `QUALIFICATION_DURATION_MS=7d`, `STAGE_DURATION_MS=3d`, `QUALIFICATION_ROUNDS=3`, `QUESTIONS_PER_FORM=20`, `MAX_QUALIFIERS=16`; `buildBracket` coordination deadline `stageEndsAtMs − READY_WINDOW_MS − sharedForm.maxDurationMs` (~209); candidate/slot/ready rules (~228–300); `STARTED` match `endsAtMs = startedAtMs + c.duration`, `resultDue`.
- `functions/lesson-question-window.js` -- adopted O2 window mechanics to compose with: states `WINDOW_OPEN/DELIVERED/EXPIRED`, deterministic window id per (attempt, question index), 60 s default.
- `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` -- adopted edition (owner-adopted 2026-10-08, blob e142341e…): §2 completion events, §3 window, §4 ready-ACK, §5 answer clock, §7 lost ACK; its notes name ONLINE-O1 as this epic's separate gate. Compose with it; never amend it.
- `_bmad-output/specs/spec-schoolquiz3.0/contract-map.md` -- ONLINE-O1 gate row ("Remaining gates") is the binding boundary text; quote its clauses in the contract's coverage map.
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` -- AD-31 (lines ~250–258) and AD-17 (lines ~166+) are the settled policy inputs (C formula, READY window, server-starts-match, immutable agreed slot).
- `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` -- NEW file this story creates (edition 1, full edition).
- `docs/agents/evidence/2026-10-16-online-o1-contract/README.md` -- NEW evidence note (inputs, outputs, pending adoption).
- `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` -- story 5.1 row; orchestrator (not the dev agent) flips status on completion.

## Tasks & Acceptance

**Execution:**
- [ ] `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` -- author edition 1: coverage map of the ONLINE-O1 clauses; the decision table above resolved as explicit decisions with budget/priority rationale per surface (qualification session, championship match session, coordination C, READY window, cohort cutoff); composition rules with O2 §2–§5/§7 mechanics; captured-config statement -- this is the story's core deliverable.
- [ ] `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` -- append "Implementation delta" appendix: for every decision that diverges from current behavior, name the current behavior with its file pointer (tournament-session.js clamp/FORCED_CUTOFF; tournament-policy.js C formula/READY_WINDOW) and what 5.6 must change -- so the adapter story never re-derives.
- [ ] `docs/agents/evidence/2026-10-16-online-o1-contract/README.md` -- record inputs (gate row, AD-31/AD-17, adopted O2 blob, policy constants), outputs, and the explicit "awaiting owner adoption" status with the dependent stories it gates -- traceability for the owner's adoption decision.

**Acceptance Criteria:**
- Given the adopted online-o2-contract and AD-31, when a reader opens the reconciliation, then every row of the decision table above has one explicit decision naming what bounds what and its budget/priority rationale, and no row defers to "implementer's choice".
- Given the current policy implementation, when the implementation-delta appendix is checked against tournament-session.js and tournament-policy.js, then every behavior the contract changes is listed with a file-level pointer, and no divergent point is implicit.
- Given the contract text, when its coverage map is compared with the ONLINE-O1 gate clauses ("neither truncating the promised window nor extending global deadlines silently", "explicit time budget and priority", "other modes do not wait"), then each clause maps to the decision(s) that satisfy it.
- Given the repo after the story, when `node --test functions/` policy/session suites run, then they pass unchanged, proving the no-code-change boundary held.

## Implementation Notes

## Spec Change Log

- **2026-10-16, review pass 1 (bad_spec loopback).** Triggering finding: HIGH — the first derivation's D2 premised a qualification envelope that crosses `qualification.endsAtMs`, but START clamps the envelope to the cutoff (`functions/tournament-service.js:219-220`, deliberate comment "Qualification closes at its fixed cutoff"), so the contract misstated current behavior and its Appendix A missed a real divergence; root cause is this spec's Code Map, which omitted `functions/tournament-service.js`. Amendment: Code Map adds that file (clamped creation, CLOSE_QUALIFICATION, makeSession, sessionView) and requires every cutoff-adjacent decision to be checked against it; Design Notes marks the "days of slack" stance known-bad. Known-bad state avoided: an adopted contract whose cohort-cutoff decision contradicts actual session creation. KEEP instructions (must survive re-derivation): the one-decision-per-tension-row structure with budget/priority/rejected alternatives; a coverage map quoting the gate clauses; a captured-config section; the O2 composition mapping; the implementation-delta appendix in file:line "decision → current behavior → required 5.6 change" format; the owner-adoption appendix naming gated stories 5.5/5.6/5.7; decision directions: envelope bounds the window with the boundary cut stated as budget, stage end immovable with envelope cut resolving PLAYED (never technical), deliveredAt anchoring with full answer time from delivery, one window per completion event with undelivered-window expiry terminating the attempt, existing start contracts with no new first-ACK number, C/READY math unchanged per AD-17. Re-derivation must additionally resolve, explicitly and with rationale: (a) D2 against the actual clamp — keep it (state the consequence for late starts) or change it (record removal in the appendix); (b) cohort membership of attempts settled by WINDOW_EXPIRED or FORCED_CUTOFF (does the settled verified result count for 5.5's cohort?); (c) persisted window openedAtMs/expiresAtMs in the captured config so crash-recovery sweep recomputes expiry deterministically; (d) terminal tie-break when a window expires exactly at endsAtMs; (e) ready-ACK key semantics for the START-delivered first question (windowId component, or explicitly no ready-ACK under the start contract); (f) a line stating terminal labels are server-side facts and naming which story decides the client projection; (g) the composition appendix must state window expiry is clamped to the envelope before persist; (h) the adoption appendix or change log must require re-verifying file:line pointers at adoption/5.6 start; (i) the evidence README must not garble the no-code-change wording.

## Review Triage Log

### 2026-10-16 — Review pass 2 (lenses: blind-hunter 15, edge-case-hunter 4, verification-gap 0, intent-alignment 0 descriptive)
- verdicts: 19 findings — high 0, medium 1, low 15, false 3, maybe-false 0
- findings:
  - `[medium]` `[patch]` KEEP violation: the stage/match-end tension row lost its dedicated decision and the "envelope cut resolves PLAYED, never technical" direction from the change log — §4 deferred to "существующая матрица" without the explicit resolution — verified by reading §4 against the Spec Change Log KEEP list; fix applied: §4 match row now states the envelope-cut session resolves PLAYED via `resolvePlayedMatch` and technical outcomes remain meeting-plane causes only.
  - `[low]` `[patch]` Item (e) unresolved: ready-ACK key undefined for the START-delivered question 0 — verified against D6/A8; fix applied: D6 and A8 now state ready-ACK applies only to window-delivered questions (indices 1..19); question 0 rides the start contract with no receipt.
  - `[low]` `[patch]` Item (f) unresolved: terminal labels not stated as server-side facts, no client-projection owner — verified against D4; fix applied: D4 adds the server-side-facts line and assigns the client projection to stories 5.9/5.10 (the FORCED_CUTOFF "overload" sub-claim is the deliberate two-paths-one-state design, no separate kind).
  - `[low]` `[patch]` Item (c) partial: captured config did not require persisted openedAtMs/expiresAtMs, and the sweep could stamp observation time — verified against D3/A3; fix applied: D3 requires persisting both at window open and sweep writes `closedAtMs` = captured expiresAtMs.
  - `[low]` `[patch]` Zero-length window edge undefined (`min(60, remainder)` ≤ 0 at the envelope edge) — verified against D3; fix applied: D3 states no window opens at remainder ≤ 0 and the attempt terminates immediately per D4.
  - `[low]` `[patch]` Item (h) missing: §8 had no pointer re-verification requirement — verified; fix applied: §8 item 4 requires re-verifying file:line pointers at adoption and at 5.6 start.
  - `[low]` `[patch]` A2 could be read as permitting a windowSeconds parameter on the shared `openQuestionWindow` — verified against A2 and the O2 file's actual signature; fix applied: A2 states wrapper/parameter on the tournament adapter side, signature unchanged.
  - `[low]` `[patch]` Row→decision mapping absent (9 decisions over 7 tension rows; README claimed "exactly one decision per row") — verified against §3 and the README check; fix applied: §3 intro carries the row→decision mapping and the README check line now references it.
  - `[low]` `[patch]` Evidence README's deviation section was stale (claimed the spec's change-log/triage sections are empty and an answer to the coordinator was pending) — the sections were restored on disk immediately after this derivation started; fix applied: README section rewritten as resolved, with the item-by-item reconciliation and the applied patch list.
  - `[low]` `[reject]` Contradictory `tournament-session.js:121` pointer between spec Code Map and contract — verified: line 121 is the scoreForm envelope guard (`SCORING_UNAVAILABLE`), the contract is correct and the spec Code Map wording is loose; the fix would edit this build's spec, and the deliverable 5.6 actually reads is accurate — rejected by rule.
  - `[low]` `[reject]` Spec frontmatter `lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']`/`followup_review_recommended: false`/empty Implementation Notes "contradict" the logs — frontmatter fields are step-04-owned and set at finalize by design; Implementation Notes are agent-owned and the derivation's decisions are recorded in the contract's own change log; the fix would edit this build's spec — rejected by rule.
  - `[false]` `[reject]` "Item (b) unresolved: cohort membership of window-terminated attempts" — refuted: D2 states attempts cut at the cutoff settle via the existing FORCED_CUTOFF sweep and enter the cohort with their answered prefix "как сегодня", and A6 marks cohort scope NO-change for 5.5; the re-derivation merged the window-expiry terminal into the same state, so no separate membership question exists.
  - `[false]` `[reject]` "Item (d) unresolved: no tie-break at exactly endsAtMs" — refuted: D4's "два пути, одно состояние" collapses window expiry and the envelope edge into one terminal state (FORCED_CUTOFF, closedAtMs = captured edge); at exact endsAtMs both paths yield an identical observable outcome, so there is nothing left to tie-break.
  - `[false]` `[reject]` "Delivery-vs-sweep race unclosed; delivery trigger unnamed" — refuted: A4 requires the next-question/read path to resolve an expired window to a termination verdict, which closes the race at delivery time independently of sweep timing, and §5 §3 names the delivery surface (server delivery in the completion response or on a next-question request).

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 12, edge-case-hunter 3, verification-gap 0, intent-alignment 0 descriptive)
- verdicts: 15 findings — high 1, medium 2, low 7, false 4, maybe-false 0
- findings:
  - `[high]` `[bad_spec]` D2 premise contradicts actual session creation: START clamps the qualification envelope to `qualification.endsAtMs` (`functions/tournament-service.js:219-220`, deliberate comment), while D2 asserted the envelope crosses the boundary and Appendix A recorded no such divergence — verified by reading the code; root cause is the spec's Code Map omitting `functions/tournament-service.js`; amendment recorded in Spec Change Log, deliverables reverted for re-derivation.
  - `[medium]` `[bad_spec]` Captured window config lacked persisted openedAtMs/expiresAtMs, so crash-recovery sweep and O2 §7 expiry recompute had no deterministic input — verified against the captured list; folded into re-derivation (change-log item c).
  - `[medium]` `[bad_spec]` D2×D5 interplay unresolved: whether a WINDOW_EXPIRED-settled attempt counts as a verified cohort result — real ambiguity for 5.5's cohort rule; folded into re-derivation (change-log item b).
  - `[low]` `[bad_spec]` Terminal label ambiguous when a window expires exactly at endsAtMs (FORCED_CUTOFF vs WINDOW_EXPIRED both match) — folded into re-derivation (change-log item d).
  - `[low]` `[bad_spec]` Ready-ACK key `(sessionId, questionIndex, windowId)` undefined for the START-delivered first question (no window exists for it) — folded into re-derivation (change-log item e).
  - `[low]` `[bad_spec]` Terminal labels not stated as server-side facts nor assigned a client-projection owner (settled path reuses the COMPLETED form) — folded into re-derivation (change-log item f).
  - `[low]` `[bad_spec]` The composition appendix said window mechanics compose "unchanged" without the envelope clamp on window expiry — a naive implementation could deliver past the cutoff; folded into re-derivation (change-log item g).
  - `[low]` `[bad_spec]` No freshness rule for pinned file:line pointers (they drift as the baseline moves) — folded into re-derivation (change-log item h).
  - `[low]` `[bad_spec]` Evidence README garbled the no-code-change sentence; its secondary claim (untested lesson-question-window suite) is not a gap — acceptance scopes policy/session suites only and no-code-change is proven by git status; folded into re-derivation (change-log item i).
  - `[low]` `[reject]` Spec bookkeeping: rejected Design-Notes stance not recorded in Implementation Notes; frontmatter pre-asserts followup fields — fix would edit this build's spec (rejected by rule); the stance rejection is recorded via the bad_spec change-log entry instead.
  - `[false]` `[reject]` "No numbered decision for window-after-last-question" — refuted: the contract's boundaries explicitly list "окна после последнего вопроса" among never-introduced items; AD-31 fixes it and the contract inherits it.
  - `[false]` `[reject]` "Multi-round qualification unreconciled" — refuted: the round entry is spent by the existing start rule per the first derivation's D5, the cohort rule is per-attempt, and rounds' start policy is explicitly unchanged; attempt-merge belongs to the immutable TZ07A ranking.
  - `[false]` `[reject]` "Match resolution timing after early settlement missing" — refuted: early settlement already exists (all questions answered settles before endsAt today), bracket math is explicitly unchanged, and the new terminal only adds an early-settlement cause with the identical settled path.
  - `[false]` `[reject]` "Coverage-map claim contradicted by the back-to-back decision" — refuted: the coverage claim is about window truncation; a window that expires undelivered runs to its full min(60s, remainder) budget and terminates the attempt — no window is shortened; that decision carries its own budget/priority and rejected alternative.
  - `[low]` `[bad_spec]` (edge-case-hunter, same defect as the tie-break row above) Terminal state at exactly endsAtMs undefined — grouped with the tie-break row, shares route; folded into re-derivation (change-log item d).

## Design Notes

The contract must decide, not survey. Precedent: `online-o2-contract.md` edition 1 was written as the full binding edition with numbered sections and a change log, then owner-adopted unchanged (2.9). KNOW-BAD (review pass 1): the stance "qualification sessions have days of slack" is false — the session envelope is dense and, for qualification, CLAMPED to the cohort cutoff at creation (`tournament-service.js:219-220`, deliberate). Every cutoff-adjacent decision must start from that clamp, and every divergence from current behavior must land in Appendix A with a file pointer — including the clamp itself if the contract chooses to change it. The appendix is the bridge to 5.6: contract decision → current line behavior → required change.

## Verification

**Commands:**
- `node --test functions/tournament-policy.test.js functions/tournament-session.test.js` -- expected: all pass unchanged (no production code touched).
- `git status --porcelain -- functions/ shared/ android/` -- expected: empty (story touches only `_bmad-output/` and `docs/agents/evidence/`).

**Manual checks (if no CLI):**
- The contract has an edition header, a coverage map quoting each ONLINE-O1 clause, one decision per tension row, an implementation-delta appendix with file pointers, and a closing owner-adoption step naming the dependent stories (5.5/5.6/5.7).
- Every "Always" boundary above is observable in the contract text; no section promises code changes.

## Auto Run Result

- Summary: authored the ONLINE-O1 reconciliation contract (edition 1, Russian, O2-precedent
  structure) deciding every tension row of the ONLINE-O1 gate — envelope wins with no
  extensions (D1), cohort cutoff absolute with the creation clamp kept (D2), window capped only
  by the envelope edge with persisted captured config (D3), delivery starts clocks and an
  undelivered-window expiry terminates the attempt via the existing FORCED_CUTOFF state (D4),
  unchanged timeliness min-rules (D5), existing start contracts for question 0 (D6, no
  ready-ACK), unchanged C/READY math (D7), untouched scoring (D8), back-to-back windows bounded
  by construction (D9) — plus the budget/priority table, the O2 composition mapping, the gate
  coverage map, the A1–A9 implementation-delta appendix for story 5.6, and the owner-adoption
  step gating 5.5/5.6/5.7.
- Files changed: `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md`
  (the contract, edition 1 + review-pass-2 amendments); `docs/agents/evidence/2026-10-16-online-o1-contract/README.md`
  (evidence note, deviation resolved); `_bmad-output/implementation-artifacts/spec-5-1-online-o1-timing-contract.md`
  (this spec); `_bmad-output/implementation-artifacts/epic-5-context.md` (compiled epic context).
- Review findings breakdown: pass 1 — 15 findings (high 1, medium 2, low 7, false 4): the high
  finding (D2 premise contradicted the actual creation clamp in tournament-service.js:219-220)
  routed bad_spec; spec Code Map amended, deliverables reverted and re-derived; pass 2 — 19
  findings (medium 1, low 15, false 3): 9 patch entries applied (PLAYED direction restored,
  question-0 ready-ACK, terminal-label projection ownership, persisted window timestamps,
  zero-length window, pointer freshness, A2 signature, row→decision mapping, README
  reconciliation); 2 low rejected (fix would edit this build's spec); 3 false rejected on
  refutation; no deferrals.
- Follow-up review recommendation: false — one medium entry patched, none high; no unverified
  risk beyond the recorded owner-adoption gate (the contract is a draft awaiting the owner;
  dependents 5.5/5.6/5.7 stay gated).
- Verification performed: `node --test functions/tournament-policy.test.js
  functions/tournament-session.test.js` → 17/17 pass, 0 fail (unchanged, after every pass);
  `git status --porcelain -- functions/ shared/ android/` → empty (no production code touched);
  review-pass-2 claims verified by reading the contract against the code pointers and the
  Spec Change Log KEEP list.
- Residual risks: the contract is awaiting owner adoption (gates 5.5/5.6/5.7 via tickets.toml
  `after`); the derivation ran before the pass-1 change-log entry was restored to disk and was
  reconciled item-by-item in review pass 2 (documented in the evidence README).
