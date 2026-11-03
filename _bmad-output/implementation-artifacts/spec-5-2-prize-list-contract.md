---
title: 'Story 5.2 — UX-O2 contract: versioned dynamic prize list and rank/tie/technical-outcome mapping'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: 'e7129ba6ba9197c9246825b210b195b79f0461b0'
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

**Problem:** The UX-O2 gate forbids any server CREATE/reserve on a new prize list — and any payout — until one versioned contract defines the dynamic ordered prize list (CREATE/read/reserve/rank/payout over the same list version) and the definitive rank/tie/technical-outcome→place mapping with fund conservation. Today the only CREATE shape is the fixed `{first, second, third}` DTO with `maximumLiabilityBoxes = first + second + 2*third`, payouts are hardcoded per rank 1/2/3, and the bracket's two semifinal losers (`third-upper`/`third-lower` placements) have no mapping rule into a list where each row pays once and no third place is auto-duplicated (AD-17).

**Approach:** Author one versioned contract document, `prize-list-contract.md` edition 1 (same document conventions as the adopted `online-o2-contract.md` and the sibling `online-o1-timing-contract.md`: edition header, change log, gate coverage map, decisions with budget/rejected alternatives, implementation-delta appendix, owner-adoption step), that decides the list schema and versioning, the mapping for ties/absences/byes/technical outcomes/degenerate sizes, remainder/refund conservation, retry/race idempotence, and legacy-event compatibility — each decided against the actual current code with file:line pointers. No production code changes in this story: story 5.4 implements after owner adoption.

## Boundaries & Constraints

**Always:**
- Conservation is the invariant: frozen fund = sum of each row exactly once; at any point `reserved = paid + returned + remaining`; terminal `remaining = 0`; every proof must keep it (AD-17, NFR-1).
- CREATE, rank→place mapping, and payout all consume the same frozen list version; a published ranking/bracket is never recomputed under a new version.
- Legacy `{first, second, third}` events stay readable and immutable; the contract defines their projection, not their rewrite.
- Every decision explicit with rationale and rejected alternatives; divergences from current behavior land in an implementation-delta appendix with file:line pointers pinned to the baseline; a pointer re-verification step at adoption/5.4 start.
- The contract ends with a named owner-adoption step; until adoption, story 5.4 stays gated.

**Never:**
- No production/config/callable/test changes in this story (policy suites stay green untouched).
- No auto-duplication of a prize row for shared places, no payment outside the frozen list, no prize for an unplayed place that the mapping does not assign, no silent version switch mid-event.
- No edits to `online-o2-contract.md`, `online-o1-timing-contract.md`, Arena season economics, or exam rewards; no renaming of турнир/чемпионат terms; no owner-adoption claim.

## I/O & Edge-Case Matrix

The deliverable's decision table. Each row must appear in the contract as an explicit decision.

| Scenario (tension) | Input / State | Expected contract decision | Error Handling |
|---|---|---|---|
| Legacy vs new CREATE | Organizer CREATE with old `{first,second,third}` vs new ordered rows | List schema + version tag; whether legacy shape is accepted at CREATE, and how legacy events project into the new read view | Explicit; old events immutable |
| Ties in ranking | Two or more players with equal rank at a place that has one row | The exact split rule (or row-duplication rule) that conserves the fund | Explicit arithmetic; no invented money |
| Bracket's two semifinal losers | `third-upper`/`third-lower` placements and a list with one third-place row (or none) | How placements map to rows when the organizer did not duplicate the row; what the organizer must list to pay both | Explicit; no auto-duplication |
| Byes and missing rounds | 15 qualifiers (byes), 2 qualifiers (final only), places never played | Which rows are payable when a place was never contested; returned remainder rule | Explicit; terminal remaining=0 |
| Degenerate events | 0 or 1 qualifier (`CANCELLED`/`FEWER_THAN_TWO_QUALIFIERS`) | Full fund return path and receipts; no partial payouts | Explicit conservation proof |
| Technical outcomes | `FORFEIT`/`DOUBLE_OUT` match results feeding placements | Whether and how technically-resolved matches assign prize places | Explicit per the adopted matrix |
| CREATE retry / payout races | Same CREATE operation retried; concurrent payout attempts | One reserve per event, one payout per row, replay returns the same receipt | Explicit idempotence keys |

## Code Map

- `functions/tournament-policy.js` -- the fixed prize reality to replace: `validatePrizeSchedule` (:84-98, requires exactly `{first,second,third}`, error "prizes support first, second and two thirds; no fourth place", `maximumLiabilityBoxes = first + second + 2*third` ≤ `fundedBoxes`); `planPrizePayouts` (:392-414, `amount = rank===1 ? first : rank===2 ? second : third`); bracket placements `champion`/`runner-up`/`third-upper`/`third-lower` (:376-389); `resolvePlayedMatch` (:303-321); `progressBracket` (:324-389).
- `functions/tournament-service.js` -- CREATE escrow and fund movements: box escrow fields `reservedBoxes/remainingBoxes/paidBoxes/returnedBoxes` reconciled against `fundedBoxes` (:95-101, :165-195, :345); funding receipts `course_tournament_funding/{digest}` + `currency_ledger` + `users/{uid}/receipts/tournament_*` (:345-392).
- `functions/course-tournament-controller.js` -- redacted event view pins the legacy shape: `prizes: pick(value, ["first","second","third"])` (~:88-92); the new read view must be defined against this.
- `_bmad-output/specs/spec-schoolquiz3.0/contract-map.md` -- UX-O2 gate row ("Remaining gates") is the binding boundary; quote its clauses in the coverage map.
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` -- AD-17 prize rules (dynamic ordered list, fund = Σ rows once, no second third place, same version for reserve/mapping/payout, old fixed DTO not substituted).
- `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md` -- §3 ТЗ07B/C ("два третьих места, сохранение фонда при выплатах/возврате") and §4 tournament special cases (0/1/2/15/16/17 participants; conservation cases).
- `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` -- story 5.2 (this story) and 5.4 (its consumer); orchestrator flips statuses.
- `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` -- NEW file this story creates.
- `docs/agents/evidence/2026-10-16-prize-list-contract/README.md` -- NEW evidence note.
- Format precedent: `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` (adopted edition structure) and `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` (sibling gate contract with A-appendix and adoption step).

## Tasks & Acceptance

**Execution:**
- [ ] `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` -- author edition 1: list schema and version pinning; CREATE/reserve/read/payout rules over one version; the decision table above resolved explicitly with conservation arithmetic; legacy projection; coverage map quoting the UX-O2 gate clauses.
- [ ] `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` -- append implementation-delta appendix: every divergence from `validatePrizeSchedule`/`planPrizePayouts`/the redacted view/escrow reconcile named with file:line pointers on the baseline, marked as 5.4's required changes; NO-change guards where current code already conforms.
- [ ] `docs/agents/evidence/2026-10-16-prize-list-contract/README.md` -- inputs, outputs, the explicit "awaiting owner adoption" status naming 5.4 as the gated dependent.

**Acceptance Criteria:**
- Given the UX-O2 gate row, when a reader checks the coverage map, then every clause (versioned list before CREATE/reserve/payout; unique mapping of dynamic places to ties/technical outcomes; each prize once; fund conservation; retry/races; legacy compatibility; the HTML three rows as example not limit) maps to the decision(s) that satisfy it.
- Given the decision table above, when the contract is read, then every row has an explicit decision whose conservation arithmetic keeps `reserved = paid + returned + remaining` and terminal `remaining = 0` in the stated cases, including 0/1/2/15 qualifiers.
- Given the current policy implementation, when the implementation-delta appendix is checked against `tournament-policy.js`/`tournament-service.js`/`course-tournament-controller.js`, then every behavior 5.4 must change is listed with a file:line pointer, and no divergent point is implicit.
- Given the repo after the story, when the policy/session Node suites run, then they pass unchanged and `git status --porcelain -- functions/ shared/ android/` is empty, proving the no-code-change boundary held.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 12, edge-case-hunter 2, verification-gap 1 other finding + claims verified, intent-alignment 0 defects)
- verdicts: 15 findings — high 0, medium 5, low 9, false 1, maybe-false 0
- findings:
  - `[medium]` `[patch]` Duplicate YAML keys in spec frontmatter (`review`/`review_source` declared twice; last-wins parsing would blank the pinned review routing) — introduced by the orchestrator's status edit, verified by parsing; fix applied: duplicates removed, single pair remains, YAML re-validated.
  - `[medium]` `[patch]` 16/17-participant cases missing (ticket's own degenerate-size matrix names 0/1/2/15/16/17; contract §4 covered only 0/1/2/15 and D1's ">16 impossible" cited no mechanism) — verified against `rankingGate` `tournament-service.js:124` and `selectQualifiers` `tournament-policy.js:177`; fix applied: mechanism pointers in D1 and a 16-qualifier full-bracket row in §4 (17+ documented as freeze-time rejection).
  - `[medium]` `[patch]` `planPrizePayouts` revalidation coupling absent from Appendix A (`:394` revalidates frozen prizes through `validatePrizeSchedule`; after A1 reshapes the validator, legacy settlement would throw) — verified by reading `:392-398`; fix applied: new A12 row requiring a dual-form validator, extending the L3 equivalence to revalidation.
  - `[low]` `[patch]` Missing rejected alternatives in D6/D7/D8/D9/D10 despite the contract's own header promise and the spec's Always boundary — fix applied: one-line rejected alternatives added to each.
  - `[low]` `[patch]` §2 baseline fact misstated the champion rule ("при любом его статусе") while the code gates on `if (final.winnerUid)` (`:378`) — verified; fix applied: §2 corrected; D6's matrix was already accurate.
  - `[low]` `[patch]` Evidence README's NO-change guard list contradicted the appendix (A3 in both lists; mixed rows A7/A9/A11 flattened into guards) — fix applied: README names pure guards (A4/A6/A9) vs mixed rows (A7/A11) and adds A12.
  - `[low]` `[patch]` Empty-list case ambiguous ("весь фонд 0" vs D2's surplus-return; prizes omission undecided) — fix applied: D1 states a zero-liability list may still hold a deposit (all returned at finish) and the `prizes` object remains required at CREATE.
  - `[low]` `[patch]` CANCELLED reachability never stated — fix applied: D8 states `finish([], "CANCELLED", …)` is reachable only via FEWER_THAN_TWO_QUALIFIERS; no organizer mid-event cancellation exists.
  - `[low]` `[patch]` §4 invariant column header hardcoded "reserved 210" — fix applied: generic `reserved = fundedBoxes` header.
  - `[low]` `[patch]` v1 rowKey canonical serialization unspecified (5.4 would invent the digest input) — fix applied: §5 pins `key(eventId, "prize", listVersion, place, rowIndex)` in the existing `key()` discipline.
  - `[low]` `[patch]` The ticket-promised compatibility table was prose bullets — fix applied: §6 now ends with a four-column compatibility table summarizing L1–L4.
  - `[low]` `[patch]` Liability sum could exceed the safe-integer range (Σ of up to 16 safe-integer rows) — fix applied: D2 requires BigInt summation with the existing `safe()` discipline and rejection outside range.
  - `[medium]` `[patch]` (edge-case-hunter, same defect as the frontmatter row) duplicate keys — grouped, shares route; fix applied same.
  - `[medium]` `[patch]` (verification-gap, same defect as the frontmatter row) duplicate keys — grouped, shares route; fix applied same.
  - `[false]` `[reject]` "Read-consumer transition gap: 5.4's eventView projection breaks the existing client" — refuted by the brownfield inventory: no client binds the six course-tournament callables at all; the legacy shelf screens consume the separate `fetchTournamentOverview`/`tournaments/{id}` surface this contract does not touch; today's only `eventView` consumers are server tests, which are story 5.4's own scope.


## Design Notes

The contract must decide, not survey. Watch the sharpest tension first: the bracket structurally produces TWO third-place finishers (`third-upper`/`third-lower`, no bronze match), while AD-17 forbids auto-duplicating a row — the contract has to choose between organizer-side explicit rows (two rows for place 3), a split rule for one row shared by both, or another explicit mechanism, and state what happens when the organizer listed neither. The legacy `{first,second,third}` read view is a projection problem, not a migration. Reuse the sibling O1 contract's document discipline (editions, coverage map, A-appendix, adoption step) so the two gate contracts read as one family.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node --test tournament-policy.test.js tournament-service.test.js` -- expected: all pass unchanged (no production code touched).
- `git status --porcelain -- functions/ shared/ android/` -- expected: empty (story touches only `_bmad-output/` and `docs/agents/evidence/`).

**Manual checks (if no CLI):**
- The contract has an edition header, a UX-O2 coverage map, one explicit decision per tension row with conservation arithmetic, an implementation-delta appendix with file:line pointers, and a closing owner-adoption step naming 5.4 as gated.
- Every "Always" boundary above is observable in the contract text; no section promises code changes.

## Auto Run Result

- Summary: authored the UX-O2 prize-list contract (edition 1, Russian, O1/O2 document family):
  v1 schema `{listVersion: 1, rows: [{place, amountBoxes}]}` canonically frozen at CREATE with
  one version consumed by reserve/mapping/payout (D1–D3); ties structurally impossible so no
  split rule (D4); two third places paid only via two organizer rows with deterministic
  third-upper→third-lower assignment and no auto-duplication (D5); the technical-outcome matrix
  fixed as-is and execution-verified (D6); byes/unplayable places return their rows (D7);
  0/1-qualifier CANCELLED returns the full fund (D8); idempotence on existing keys (D9); legacy
  `{first,second,third}` events immutable behind a unified `listVersion: 0` projection with
  execution-verified payout equivalence (D10, §6); conservation table §4; UX-O2 coverage map §7;
  implementation-delta appendix A1–A12; owner-adoption step §9 gating story 5.4.
- Files changed: `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` (the
  contract, edition 1 + review-pass-1 amendments); `docs/agents/evidence/2026-10-16-prize-list-contract/README.md`;
  `_bmad-output/implementation-artifacts/spec-5-2-prize-list-contract.md` (this spec).
- Review findings breakdown: 15 findings — medium 5 (frontmatter duplicate keys — orchestrator
  repair; 16/17 coverage; `:394` revalidation coupling; two grouped duplicates of the first),
  low 9 (all patched: rejected alternatives, champion fact, README guards, empty-list,
  CANCELLED reachability, header, rowKey serialization, compatibility table, BigInt guard),
  false 1 (rejected: no client consumes the redacted event view today). No deferrals, no
  bad_spec, no intent_gap.
- Follow-up review recommendation: TRUE — the unverified risk is that the pass-1 patches
  (16/17 rows, A12 dual-form revalidation, BigInt guard, rowKey serialization, compatibility
  table) were applied by the orchestrator and covered by re-verification commands but not by a
  fresh lens pass over the amended text; a follow-up review pass on this done spec should
  re-examine the amended contract before owner adoption.
- Verification performed: `cd functions && node --test tournament-policy.test.js
  tournament-service.test.js` → 17/17 pass, 0 fail (before and after patches);
  `git status --porcelain -- functions/ shared/ android/` → empty; frontmatter YAML re-parsed
  (single key pair); the derivation agent additionally execution-verified the placement matrix
  and payout arithmetic against the real policy module; the verification-gap lens ground-checked
  the file:line pointers against HEAD.
- Residual risks: the contract awaits owner adoption (gates 5.4 via tickets.toml `after`);
  deliberate owner-visible decisions flagged in the evidence README (one place-3 row pays only
  `third-upper`; places outside {1,2,3} legal but never paid under the current bracket;
  deposit may exceed Σ rows).
