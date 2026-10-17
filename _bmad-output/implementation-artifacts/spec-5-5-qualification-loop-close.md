---
title: 'Story 5.5 — close the qualification loop on the exact calculation; plan §4 size matrix proven through CLOSE'
type: 'feature'
created: '2026-10-16'
status: 'done'
review: 'thorough'
review_source: 'pinned'
baseline_revision: '9b7a5acfad8dfa7d8c101fc7619958d511331c46'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The qualification machinery exists (TZ07A exact calculation: select→scan→calculate→publish phases with cursors/leases/fencing, 4096/4097-player pure-core cases) and CLOSE_QUALIFICATION consumes the frozen ranking into bracket seeding — but the loop is not proven end-to-end at the plan §4 size matrix: 15 qualifiers (bye structure through bracket seeding), 17 participants (freeze-gate rejection), and the large-cohort loop (502 attempts / 1503 attempts) are pinned only at the calculation layer or in rig scripts, not through the service seam; mid-CLOSE interruption/resume through the transaction boundary is untested; the ONLINE-O1 contract's D2 cohort-cutoff semantics ("no change; clamp kept") lack an in-repo loop-level pin; and the plan §4 bounds (reads/writes/time) are not recorded in evidence.

**Approach:** Proof-first story on the existing machinery: extend the service/controller-level suites with the missing size cases (15, 17, large-cohort loop at the service seam using the existing calculation fixtures; 502/1503 sized as the calculation layer already proves them — pin the loop seam honestly, without inflating unit budgets), the interruption/resume case at the CLOSE transaction boundary, the D2 cohort-cutoff pins, and the legacy-fencing pin; production code changes ONLY where a test exposes a real gap. Record reads/writes/time bounds in the evidence note.

## Boundaries & Constraints

**Always:**
- Published rankings/brackets are never recomputed; the legacy publisher stays fenced once a calculation version is selected; phase state (cursor/revision/lease/fencing) survives interruption and resumes without double effects.
- Cohort semantics per the adopted ONLINE-O1 contract D2: the creation clamp stays, attempts cut at the cutoff settle via the existing FORCED_CUTOFF sweep and enter the cohort with their answered prefix; nothing at the cutoff moves.
- Budgets bound invocations, not population: any large-size test must assert or record reads/writes/time bounds, never introduce a population cap.
- Every new case runs in the normal `node run-tests.js` battery (no skipped/disabled tests).

**Never:**
- No new product surfaces, no worker redesign, no calculation-constant changes (LS formula, tie-breakers, versions stay frozen); no client code; no rig requirements (the rig harnesses already exist and are recorded separately).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| 15 qualifiers | Full cohort of 15 admitted players completing the loop | Bracket seeds 16 with one bye per the existing structure; placements as for a full bracket; CLOSE→bracket green | — |
| 17 participants | Ranking of 17 reaching the freeze gate | Rejected (ranking length >16 guard) with a typed error; no bracket, no partial effects | Typed, event stays QUALIFICATION |
| 502-attempt cohort (loop seam) | Large cohort through CLOSE at the service/calculation seam | Ranking published, top16 seeded; reads/writes/time recorded in evidence (calculation-layer sizes already proven: 4096/4097, [502,1]) | Budget assertions, no caps |
| Mid-CLOSE interruption | Transaction commit failure inside CLOSE (harness `failNextCommit`) | No partial effects; retry of the same operation replays/completes; event reaches a consistent state | Atomic commit failure surfaced, then resolved on retry |
| Cutoff cohort pins | Attempt completing at cutoff−1 vs starting at cutoff | First settles into the cohort; second refused OUTSIDE_QUALIFICATION_WINDOW (existing reasons) | D2 "no change" pinned |
| Legacy fencing | Legacy publication attempt after a calculation version is selected | Fenced (existing assertLegacyQualificationPublication) | Typed refusal |
| Repeat CLOSE | Same operationId replay after success | Same response replayed, no second bracket/seed | Existing receipt discipline |

## Code Map

- `functions/tournament-service.test.js` — the service seam harness (premiumTestDb, `fixture(participants)`, `failNextCommit` :123, existing CLOSE cases :164-173, :379, 16-qualifier bracket :409, cutoff refusal :392) — the primary extension site.
- `functions/tournament-qualification-calculation.test.js` (17 cases; large sizes at the calculation layer), `tournament-qualification-scan.test.js`, `tournament-qualification-freeze.test.js` (fencing, 18 cases) — reuse their fixture builders for the large-cohort seam case.
- `functions/tournament-service.js` — CLOSE branch (loadQualificationRanking → rankingGate → buildBracket → bank check → BRACKET; CANCELLED path), operation receipt replay (:148-155), clamp at START (:219-220 era — locate by symbol).
- `functions/tournament-policy.js` — `buildBracket` (bye structure :356-361, placements), `rankingGate` (>16 guard), `planQualificationAttempt` window (:158-160).
- `functions/tournament-qualification-freeze.js` — `assertLegacyQualificationPublication`, `selectQualificationCalculation`, `loadQualificationRanking`.
- `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` — adopted D2 (cohort cutoff semantics this story pins).
- `docs/agents/evidence/2026-10-16-qualification-loop/README.md` — NEW evidence note with the recorded bounds.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-service.test.js` (+ calculation-layer helpers if the seam needs them) -- add: 15-qualifier bye-bracket loop case; 17-participant freeze rejection case; mid-CLOSE interruption/resume case (no partial effects, replay completes); cutoff cohort pins (cutoff−1 settles in, cutoff refused); repeat-CLOSE replay case.
- [ ] Large-cohort loop proof -- drive the 502-attempt cohort through CLOSE at the service/calculation seam with the existing fixture builders, recording reads/writes/time in the evidence note; if the unit harness budget cannot hold the full 502 loop, pin the largest green size at the seam, reference the calculation-layer 4096/4097 proofs and the existing rig script for the full loop, and say so honestly.
- [ ] `docs/agents/evidence/2026-10-16-qualification-loop/README.md` -- inputs (adopted O1 D2, plan §4 sizes), the matrix rows with recorded bounds, and any production gap found (expected: none or minimal).
- [ ] Production code -- only if a test exposes a real gap; every change justified in the evidence note; otherwise zero production diff.

**Acceptance Criteria:**
- Given 15 admitted qualifiers, when the full loop runs, then the bracket seeds with the documented bye structure and reaches terminal placements under the existing match rules.
- Given a 17-player ranking at the freeze gate, when CLOSE runs, then it is refused typed with no partial effects.
- Given a commit failure inside CLOSE, when the operation is retried, then the event reaches a consistent terminal state with no duplicated effects.
- Given the full `node run-tests.js`, then all suites pass with the new cases registered and running; `git status --porcelain -- shared/ android/` stays empty.

## Implementation Notes

## Spec Change Log

## Review Triage Log

### 2026-10-16 — Review pass 1 (lenses: blind-hunter 15, edge-case-hunter 1, verification-gap 0, intent-alignment 0 defects)
- verdicts: 16 findings — high 0, medium 0, low 15, false 0, maybe-false 0
- findings (all low; 14 patched, 1 rejected):
  - `[low]` `[patch]` Stale line anchor «:409 era» in the suite comment — replaced with the anchor test's name.
  - `[low]` `[patch]` 15-qualifier case asserted only the top-4 placements — added: exactly four placement rows, every eliminated player at zero boxes.
  - `[low]` `[patch]` Bye-node discipline unproven — added: forms count 3+15 (bye node stages its form structurally) with NO session for the bye match.
  - `[low]` `[reject]` Multi-bye cohorts (9/12) unproven — the plan §4 matrix names 0/1/2/15/16/17 only; the intent does not ask for intermediate sizes — out of scope by intent.
  - `[low]` `[patch]` A late ANSWER between the cutoff and the sweep was untested — added: refused (timeliness bound), core D2 hazard pinned.
  - `[low]` `[patch]` Fuzzy `percentScore > 0` and bob's zero-run unverified — replaced with exact pins (5 for one answered question of twenty; 0 for the zero-answer run).
  - `[low]` `[patch]` Identifier padding drifted across the cutoff scenario — normalized to the canonical 16-char tails.
  - `[low]` `[patch]` 502 case asserted no wall-clock budget despite «within recorded budgets» — added a generous 30 s ceiling (bounds stay recorded, not just asserted).
  - `[low]` `[patch]` (edge-case-hunter, same defect) metrics wrapper lost failed-transaction reads — added try/catch accounting.
  - `[low]` `[patch]` Top16 assertion was tautological (compared against the injected ranking) — replaced with an independently computed expectation (score desc, elapsed asc).
  - `[low]` `[patch]` Provenance round-trip partially pinned — aligned to the retained-fields set the sibling test pins (snapshotId, contract digest, fingerprint version, verifiedRunsFingerprint).
  - `[low]` `[patch]` The 17-refusal proved no recovery — added: corrected ranking closes the same event to a normal 16 bracket.
  - `[low]` `[patch]` Legacy fence exercised only at pageSize 1/maxPages 1 — moved to realistic bounds (50/10) so a misplaced fence could not pass vacuously.
  - `[low]` `[patch]` (intent-alignment, descriptive notes) narrowing choices (CLOSE-commit interruption scope; 17 as a guard-surface case; 1503/4096/4097 left at the calculation layer) — already documented in the spec and evidence note; no action.
  - `[low]` `[patch]` (intent-alignment) evidence note lives outside the diff — it is part of the change set and is committed explicitly with the test file.


## Design Notes

This is a proof story: its deliverable is certainty, not novelty. The 16-qualifier bracket case (:409) and the <2 CANCELLED case (:379) are the anchors to extend symmetrically — 15 and 17 bracket them. The interruption case should ride the harness's existing `failNextCommit` rather than new machinery. If a "gap" tempts a production change, stop and write it up first: the contract says the machinery is done, and unneeded changes here would re-open adopted ground.

## Verification

**Commands:**
- `cd functions && /root/.zcode/server/node run-tests.js` -- expected: all suites pass including the new cases.
- `git status --porcelain -- shared/ android/` -- expected: empty.

**Manual checks (if no CLI):**
- Evidence README records the size matrix with bounds and states explicitly whether any production change was needed.

## Auto Run Result

- Summary: proof-first closure of the qualification loop — seven new service-seam cases
  (15-qualifier bye bracket to FINISHED with economics; 17-row freeze-gate refusal with
  recovery; mid-CLOSE commit failure → no partial effects → same-id retry completes; repeat
  CLOSE replay and fresh-id restate; D2 cutoff cohort pins with exact scores and the late-answer
  refusal; 502-attempt cohort through the versioned seam with instrumented read/write/time
  budgets and an independent top16 expectation; legacy fencing under realistic paging) — ZERO
  production diff; the machinery held at every new case.
- Files changed: `functions/tournament-service.test.js` (40 → 47 cases), evidence note, this
  spec. Production: none.
- Review findings breakdown: 16 findings, all low — 14 patched (test hardening: exactness,
  independence, recovery, budgets, bye discipline, late-answer pin), 1 rejected (multi-bye
  sizes out of the plan §4 intent), 1 grouped. No deferrals.
- Follow-up review recommendation: false — no high or medium entries patched; the evidence
  note carries the recorded bounds (3 transactions, max 8 reads/tx, max 32 writes/commit,
  2.2–2.5 s wall clock for the 502 seam).
- Verification performed: full `node run-tests.js` → 109/109 suites pass after every patch
  round; `git status --porcelain -- shared/ android/` empty.
- Residual risks: 1503/4096/4097 sizes remain proven at the calculation layer (referenced, not
  duplicated at the seam); phase-level lease/resume interruption stays with the calculation/
  scan suites and the rig harnesses as the spec scoped.
