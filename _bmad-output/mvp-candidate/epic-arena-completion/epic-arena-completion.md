---
type: epic
title: "Arena: durable attempts, ratings, season"
parent: mvp-candidate
covers: [CAP-7, CAP-8, CAP-9, CAP-10]
after: ["epic-candidate-rig", "epic-ordinary-delivery"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Arena: durable attempts, ratings, season

## Description

The Arena path completed to the owner's plan: prepare and browse without financial effect (CAP-7), finish or cancel an attempt durably with one final outcome and strict proof (CAP-8), rate from a prior confirmed result with correct fractional author share (CAP-9), promotions and the quarterly season award paid once from the complete eligible input (CAP-10). This is the owner's plan §2: restore, the C1+C3 rig, the first full pass (2б), and the rest of the matrix.

## Outcome

A player can prepare, play, lose connectivity, resume and settle an Arena attempt with exact server receipts, and author earnings/season awards conserve funds — owner matrix 1 becomes runnable end to end.

## Done when

1. The first full pass (plan §2 step 3) passes on the candidate: SELECTED/HARD, prepare, start, saved answer, restart, same attempt, settle, rating 2, RATE_LESSON — with ticket/attempt/progress surviving restart and the two receipts (settlement + rating) consistent with the frozen ticket.
2. The remaining Arena matrix (plan §2 step 4: lost answer after commit, real offline from stock, queued rating, EASY/HARD, 20 daily randoms, 5 concurrent tickets, frozen price, cancel/settle race, A→B→A, late answers, one ticket two devices, hardware Back) runs with recorded results.
3. Ratings and prices use the full eligible set with fractional shares conserved; 5 payments of 1 credit the author 4 total with no second effect on retry.
4. The quarterly season settles from the complete eligible input with the adopted tie/no-winner rules, pays once and resumes after interruption.
5. An unknown HARD score is never zero and cancel/settle races have exactly one final effect with unresolved intent retained (CAP-8).

## Boundaries

Arena lifecycle, ratings, season. Not tournaments/championship (epic-competitions), not lesson content quality (epic-authoring-review), not wallet UI beyond Arena needs (epic-commerce owns the store).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-7..10) and Constraints (frozen prices, receipts, AD-31)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-11/12/13/23
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 2 and section 4 (money/season fixtures)
- harness — .maestro/mvp/arena-e2b/README.md, .maestro/mvp/arena-matrix/README.md

## Notes

- Unknown: the known RED quiescence preflight defect's production fix lands in epic-candidate-rig; Arena matrix runs wait on it.
- Waits on epic 1 because: restored demo accounts/fixtures and the rig are the run precondition.
- Waits on epic 2 because: online HARD next-question transitions adopt the shared ONLINE-O2 contract; offline/prepared paths do not wait.
