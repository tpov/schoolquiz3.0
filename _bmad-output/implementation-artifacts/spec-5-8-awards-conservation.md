---
title: 'Story 5.8 — awards, ranking and remainder conservation end-to-end through the bracket'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '0fb65256ac2dedc2b82767def82e3a92c9046a35'
route: 'full'
route_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The prize-list machinery (5.4), the qualification loop (5.5), the online session semantics (5.6) and the meeting projections (5.7) each conserve and pin their own slice, but the END-TO-END money story of a championship is not yet proven as a whole: payouts and returns across a full bracket to FINISHED with terminal `remaining = 0`, technical-outcome matrices paying per the adopted mapping, two-place-3 rows paid live through a played bracket, and crash/retry races at the settlement boundary (commit failure mid-payout, replayed ADVANCE_MATCH, concurrent double-spends) with exactly-once effects.

**Approach:** Proof-first like 5.5: drive full brackets to FINISHED at the service seam with the fixture machinery (2/4/16-player cases; FORFEIT and DOUBLE_OUT finals and semifinals; two third-place rows; zero-amount and surplus-deposit rows), asserting escrow reconcile (`reserved = paid + returned + remaining`, terminal `remaining = 0`), per-row receipt uniqueness (no duplicate payout keys), and the frozen-list-version consumption; race cases via the harness's `failNextCommit` and replayed operations at the settlement boundary. Production changes only if a test exposes a real gap.

## Boundaries & Constraints

**Always:**
- Conservation is asserted, not narrated: every case closes the escrow equation with terminal `remaining = 0`; every payout/return carries its own deterministic receipt; a replayed or retried settlement writes no second effect.
- The frozen prize-list version is consumed by mapping and payout; nothing reads a newer list mid-event; legacy stored events keep their slot-keyed equivalence (5.4's pins stay green).
- Only the server starts a match when both participants are READY (existing authority; no new turn-order concept).

**Never:**
- No client code; no new storage shapes or callable surfaces; no meeting/session/prize-math changes — this story proves the composition, and any gap it exposes is written up before code changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Full played bracket | 4 or 16 qualifiers, all matches PLAYED, prize list with two place-3 rows | Champion/runner-up/both thirds paid per row; escrow terminal {paid=Σ assigned, returned=unassigned rows+deposit surplus, remaining=0}; one receipt per row | — |
| FORFEIT final | Final resolved by forfeit | Winner (present player) paid per mapping; the absent player takes no prize; unassigned rows returned | — |
| DOUBLE_OUT semifinal | One semifinal DOUBLE_OUT | Survivor reaches the final (BYE resolution); the vanished slot's row returns; matrix per 5.2 D6 | — |
| Commit failure mid-settlement | `failNextCommit` inside a match-settling transaction | No partial payout/escrow effects; retry completes exactly once | Atomic failure surfaced |
| Replayed settlement | Same ADVANCE_MATCH operation replayed after FINISHED | Identical response, byte-identical store (existing receipt discipline) | — |
| Zero and surplus rows | Zero-amount row at a played place; deposit above Σ | Zero row pays nothing and consumes its position; surplus returns at finish | — |
| Frozen version | Prize list frozen at CREATE; bracket pays later | Payouts consume the CREATE-frozen rows only | — |

## Code Map

- `functions/tournament-service.test.js` — the seam harness (fixture/qualify/play/close helpers, `failNextCommit`, 16-qualifier loop case) — the primary extension site; `functions/tournament-policy.test.js` — pure payout matrix anchors to extend symmetrically.
- `functions/tournament-policy.js` — `planPrizePayouts` (place/row assignment), `resolvePlayedMatch`, `progressBracket` placements; `functions/tournament-service.js` — `finish` escrow reconcile + funding receipts; `tournament-policy.js` prize contract pins from 5.4 stay authoritative.
- `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` — the adopted conservation law (D-matrix, §4 table); `tickets.toml` 5.2/5.4 records.
- Evidence: `docs/agents/evidence/2026-10-16-awards-conservation/README.md` — NEW.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-service.test.js` (+ policy tests symmetric where pure) -- add the matrix cases: full played brackets with two-place-3 rows (4 and 16 players), FORFEIT final, DOUBLE_OUT semifinal, commit-failure mid-settlement with exactly-once retry, zero/surplus rows live, frozen-version consumption.
- [ ] `docs/agents/evidence/2026-10-16-awards-conservation/README.md` -- matrix→test mapping, escrow terminal states per case, honest notes.
- [ ] Production code -- only for a proven gap; otherwise zero production diff.

**Acceptance Criteria:**
- Given each matrix scenario, when the bracket settles to FINISHED, then the escrow equation closes with terminal `remaining = 0`, every row paid at most once with its own receipt, and no case double-pays or strands funds.
- Given a commit failure inside settlement, when the operation retries, then effects are exactly-once and the store reaches the same terminal state as an uninterrupted run.
- Given the full `node run-tests.js`, then all suites pass with the new cases registered; `git status --porcelain -- shared/ android/` stays empty.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 13, edge-case-hunter 2, verification-gap 0 gaps + 2 other, intent-alignment 0 defects)
- verdicts: 17 findings — high 0, medium 2, low 15, false 0
- findings:
  - `[medium]` `[patch]` Duplicate YAML `review`/`review_source` keys in the spec frontmatter (shadowing the pinned review) — deduplicated, single pair verified.
  - `[medium]` `[patch]` (edge-case-hunter, same defect) grouped — shares the route and the fix.
  - `[low]` `[patch]` The two pre-existing 2-player cases cited by the README's terminal table never asserted the escrow object — added deep-equals ({12,0,8,4} played final; {12,0,0,12} DOUBLE_OUT final), completing the table honestly.
  - `[low]` `[patch]` Fresh-id restatement asserted only money equality — strengthened: exactly one new operation receipt, the event's mutation stamp aside, all other store entries byte-identical.
  - `[low]` `[patch]` FORFEIT-final and DOUBLE_OUT-semifinal cases never enumerated funding receipts nor negatively pinned the absent players — added payload-level PRIZE-receipt counts with absent-player exclusions.
  - `[low]` `[patch]` Zero-row case never asserted placements — added the full placement deep-equal (bob keeps runner-up with the zero row consumed).
  - `[low]` `[patch]` `money()` helper crashed on fixtures without the named players — made participant-driven.
  - `[low]` `[patch]` README: magic owner totals undeciphered, terminal table gaps (3-player FORFEIT semifinal row absent, commit-failure row duplicative), machine-specific node path, suites-vs-cases vocabulary — README fixes applied.
  - `[low]` `[reject]` DOUBLE_OUT-final and FORFEIT-semifinal seam cases not re-provided — those matrix cells are pinned by pre-existing cases the evidence table attributes (composition per the adopted contract, re-running them would duplicate pins).
  - `[low]` `[reject]` 16-player test's captured bracket reference and inline pending lookup — the edge-case lens itself verified the captured reference is safe (bracket assigned once, read-only afterwards); cosmetic style only.
  - `[low]` `[reject]` `technicalForfeit` never varied — the flag is a preserved guard whose semantics 5.2 pinned (never produced today); no production path sets it true.
  - `[low]` `[reject]` Spec bookkeeping (unchecked tasks, empty sections) and the missing tickets.toml flip — orchestrator-owned; resolved at finalize/booking.
  - `[low]` `[reject]` (intent-alignment, descriptive) races as the sequential projection of interleaving; frozen-version mid-event update pinned by argument — the single-threaded seam cannot express true interleaving and the no-mutation-writer property is structural (nothing writes prizes after CREATE); recorded.


## Design Notes

Same discipline as 5.5: the deliverable is certainty. The one composition risk worth probing hardest is the commit-failure window inside settlement (receipt staged vs escrow staged) — the harness's failNextCommit at the settlement transaction is the honest probe. If a gap appears, stop and write it up before touching production.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node run-tests.js` -- expected: all suites pass.
- `git status --porcelain -- shared/ android/` -- expected: empty.

## Auto Run Result

- Summary: end-to-end money conservation of the championship proven at the service seam —
  16-player full played bracket with two place-3 rows and per-row receipt keys, FORFEIT-final
  and DOUBLE_OUT-semifinal matrices with absent-player exclusions pinned at receipt level,
  commit-failure atomicity with exactly-once same-id retry to the uninterrupted twin's terminal
  state, zero/surplus rows live with placements pinned, frozen-version consumption with
  byte-stable event prizes, and escrow deep-equals completing the README's terminal table.
  Zero production diff.
- Files changed: `functions/tournament-service.test.js` (51 → 57 cases),
  `functions/tournament-policy.test.js` (28 → 29), evidence README, this spec.
- Review findings breakdown: 17 findings — medium 2 (one defect, the frontmatter duplicates, in
  two rows — patched), low 15 (7 patched, 8 rejected as composition-duplicates, cosmetic, or
  preserved-guard semantics). Deferred: none.
- Follow-up review recommendation: false — one grouped medium (bookkeeping) patched; no code
  behavior changed (test-and-docs only).
- Verification performed: full `node run-tests.js` → 109/109 suites pass after every patch
  round; `git status --porcelain -- shared/ android/` empty.
- Residual risks: races remain the sequential projection (fail→retry, replay, restatement) —
  true interleaving is not expressible at this seam and is deferred to the rig/e2e story 5.12;
  frozen-version non-mutation is pinned structurally plus by argument.
