---
type: epic
title: "Ordinary learning and durable delivery"
parent: mvp-candidate
covers: [CAP-1, CAP-16, CAP-18, CAP-19]
after: ["epic-candidate-rig"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Ordinary learning and durable delivery

## Description

The ordinary journey is the tracer bullet: a learner navigates a course and completes an ordinary lesson on cached or fresh content, offline progress, timers, Continue/Back/restart retain the outcome (CAP-1); configuration refreshes coherently without inventing zeros (CAP-16); the outbox drains a 120-action case without repeated manual sync and uncertain outcomes stay uncertain (CAP-18); account and device switches never adopt another session's work (CAP-19). This epic also lands the shared online next-question contract ONLINE-O2 (AD-31) that Arena, exam and competition adapters adopt.

## Outcome

A learner can trust one ordinary lesson end to end on a real phone — offline, after restart, after an account round-trip — and every later mode reuses the same delivery/ACK contract instead of inventing its own.

## Done when

1. An ordinary lesson completes offline from prepared stock, and after restart the same attempt/outcome is shown; a ready saved question appears without an added network gate (CAP-1).
2. The 120-action queue drains to authoritative results without repeated manual sync; a cancelled or malformed response fabricates neither success nor duplicated effects (CAP-18).
3. An A→B→A account switch and a new session for the same UID preserve ownership: no foreign actions, private data or double settlement (CAP-19).
4. A config refresh failure keeps known values and never shows zero; changed payable terms demand fresh consent (CAP-16).
5. The ONLINE-O2 contract (delivery/ready-ACK receipt, identity, retry, relation to answer-clock start) is written, adopted and proven by at least one online next-question transition.
6. Update-over-populated-install acceptance cases for this journey pass on the candidate (with epic-candidate-rig).

## Boundaries

Ordinary lessons, delivery durability, shared timing contract. Not Arena/exam/competition adapters (they consume ONLINE-O2 in their epics), not content authoring (epic-authoring-review).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, sections Capabilities (CAP-1, CAP-16, CAP-18, CAP-19) and Constraints (AD-31 timing paragraph)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-6/7/8/21/22/24/31
- gates — _bmad-output/specs/spec-schoolquiz3.0/contract-map.md, Remaining gates (ONLINE-O2, UX-O10/O12)
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 5 (matrices 3, 5)

## Notes

- Decision: the ONLINE-O2 landing entry lives in this epic; other epics name it in `after` (2026-10-03, slicing).
- Waits on epic 1 because: candidate, backend and rig are the measurement precondition.
