---
title: '4.18 review-fee-burn-and-integrity — плата модельного цикла сгорает целиком; целостность кошелька'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '77bdb0fe'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Settlement still pays reviewer entitlements from review records — the model pipeline has no reviewers, so a model quest would settle with units from stale human-review history. Retro 2026-10-25 L4/п.2 (HIGH): (a) `pointsNolics`/`nolics` mirror not dual-written in fee charge and settlement, as every other money path does; (b) `planReviewFeeCharge` keys the tariff on `review_fee_quests/{draft.id}`/`quests/{draft.id}` with no owner comparison — a hostile submission pays 100×N recheck instead of 1000 on a foreign id and writes its cycle counter onto a foreign document.

**Approach:** Settlement of a model outcome (`request.reviewOutcome` present) reads no human review records: units = [], entitlements = 0, the whole fee burns; the manifest pins the conservation identity. The charge refuses a foreign draft id by name (`REVIEW_FEE_DRAFT_NOT_OWNED`) by comparing the prior-cycle docs' owner/author to `request.ownerUid`. Both fee paths dual-write the nolics mirror (`pointsNolics` + `nolics`), same shape as the lesson-unlock charge.

## Boundaries & Constraints

**Always:**
- Tariffs unchanged (initial 1000, recheck 100×N, `configs/review_fee`); charged once at acceptance (4.7 machinery untouched).
- Conservation: fee = entitlements (0) + burned remainder, pinned by the immutable manifest.
- Human-world cycles (no reviewOutcome) keep their settlement path until 4.19 removes it.

**Never:**
- No new fee states; REVIEW_DELAYED still does not settle (OQ-2 parked, 4.16).
- No client code.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Model settlement | cycle PAID + outcome, terminal status | SETTLED; units [], wallets [], burn = whole fee; no reward ledger rows | No error expected |
| Rejected model quest | outcome passing [] | status REJECTED; cycle settled REJECTED, whole fee burns | No error expected |
| Foreign draft id | review_fee_quests/{id}.ownerUid ≠ submitter (or quests/{id}.authorUid) | `permission-denied REVIEW_FEE_DRAFT_NOT_OWNED`; no cycle, no writes under the foreign id | Submission not accepted |
| Charge mirror | acceptance | users/{uid}.pointsNolics = nolics = balance − fee; one ledger row | No error expected |
| Settlement mirror (wallets) | human cycle with entitlements | credited users get pointsNolics and nolics consistent with the manifest | No error expected |
| Replay | settled cycle | replayed: true, first settlement stands | No error expected |

## Code Map

- `functions/index.js` — `planReviewFeeCharge` ownership gate; `writeReviewFeeChargeToTransaction` dual-write; `settleReviewFeeCycle` model branch (`modelOutcome` → skip records/tasks reads, units = []) + settlement wallet dual-write.
- `functions/quest-review-fee-burn.test.js` (new) — burn/conservation/foreign-refusal/mirror/replay over public surfaces (`submitMutation`, `advanceQuestReviewOutcome`, `submitReviewAction`, `rejectReviewSubmission`).
- Do not touch: `review-fee.js` math (settleReviewFeeTenths already yields burn = fee for zero units), tariffs, 4.7 suite expectations.

## Tasks & Acceptance

**Execution:**
- [x] Ownership gate + named refusal.
- [x] Charge and settlement mirror dual-writes.
- [x] Model settlement burns the whole fee (zero reads of the human world).
- [x] Suite over public surfaces (5 cases).

**Acceptance Criteria:**
- Given a model quest with an open PAID cycle, when it reaches PUBLISHED or REJECTED, then the cycle settles with zero entitlements, burn = the whole fee, and the manifest proves the conservation identity.
- Given a submission naming another author's quest id, when acceptance plans the charge, then it is refused by name and nothing is written under the foreign id.
- Given any fee movement, when it lands, then both balance mirror fields agree with the ledger.

## Implementation Notes

- Workflow deviation sanctioned by the run mandate (autonomous wave; spec straight to done with the change log as the record).

## Spec Change Log

- 2026-10-27, verification: `node quest-review-fee-burn.test.js` 5/5; `review-fee-cycle.test.js` 5/5 (human path untouched); `quest-review-model-outcome.test.js` 6/6; full functions gate `npm test` — see tickets.toml comment for the final count.

## Review Triage Log

## Design Notes

- Why the model branch lives in settlement and not in `buildReviewUnits`: the human path stays byte-identical until 4.19 deletes it; the branch is one guard plus an empty-units settlement, so the conservation math is reused, not forked.

## Verification

**Commands:**
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` — full functions gate.

**Manual checks (if no CLI):**
- None beyond the suites.
