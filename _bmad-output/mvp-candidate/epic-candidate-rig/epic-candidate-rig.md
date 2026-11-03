---
type: epic
title: "Candidate and rig: restore, identify, measure"
parent: mvp-candidate
covers: [CAP-20]
after: []
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Candidate and rig: restore, identify, measure

## Description

The platform baseline for everything that follows: the demo backend restored from the verified export, devices inventoried, one identified candidate (code, backend, APK, schema, config, flags) installed over a populated installation without losing state, and the Maestro rig able to prove ordinary-lesson and Arena-preparation runs on a real phone. The known RED quiescence-preflight regression gets its production fix with the regression green. Spec section Capabilities, CAP-20; brownfield facts in the handoff.

## Outcome

Every later epic runs against a restored backend and one identified candidate whose update path provably preserves user state — the precondition the handoff names for any new acceptance claim.

## Done when

1. The demo Firebase container is recreated from the verified export into a new directory; the saved account, wallet, lessons and history survive a control save/restart.
2. One candidate is identified and recorded (HEAD, APK SHA-256, certificate, Room schema, config, flags) and `assembleMvp` reproduces it on the VPS.
3. Installing the candidate over the populated Samsung installation retains content, history, authentication, installation identity and pending operations (CAP-20).
4. The quiescence preflight regression is GREEN: exactly five known counters, each numeric zero, gate the run; malformed preflight stops before Maestro and network changes.
5. A device inventory (serial, Android, app, signature, DB schema, role, GMS/Play) exists; missing settings are recorded as external dependencies, not blocking others.
6. Root `ciCheck` has been run on the candidate at least once with the result recorded.

## Boundaries

Environment, tooling and candidate identity. Not feature work: ordinary and Arena journeys are owned by their epics; the rig here proves only that they can be measured. Storage/disk work stays with the owner per instruction.

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities, CAP-20
- handoff — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/HANDOFF.md, sections devices, containers, unfinished edit
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, sections 1–2 (steps 0–2)
- checkpoint — docs/agents/2026-09-27-mvp-progress-checkpoint.json

## Notes

- Unknown: saved export fitness for the Samsung candidate (handoff marks it unverified) — the restore entry verifies or stops.
- Open question: OQ-5 operations inventory (devices, toolchain/security compatibility) — this epic produces the inventory part.
- Waits on nothing: first epic.
