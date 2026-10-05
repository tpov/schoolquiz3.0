---
type: epic
title: "Final acceptance of the identified candidate"
parent: mvp-candidate
covers: []
after: ["epic-ordinary-delivery", "epic-arena-completion", "epic-authoring-review", "epic-competitions", "epic-exams-certificates", "epic-commerce", "epic-social-features", "epic-ux-noir"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Final acceptance of the identified candidate

## Description

The assembly the spec's Success signal demands: ONE identified candidate where code, running backend, installed APK, schema, config and flags correspond; fresh common checks including root `ciCheck`; the owner's six acceptance matrices; and the owner's manual acceptance with traceable PASS/FAIL/NOT RUN/SKIPPED/INTERRUPTED evidence, acceptance/manual-check instructions and known limits. Historical green results do not stack; "implemented" without evidence is not acceptance.

## Outcome

The owner manually accepts (or rejects with listed defects) one candidate that provably contains every mandatory journey — the initiative's terminal signal.

## Done when

1. A single candidate is recorded (code, backend, APK + checksum/certificate, schema, config, flags) and all six owner matrices run on it: authoring→Arena→payout→tournament; archive course→tournament→match→prizes; Home smoke; boxes/premium→purchase→lost answer→restart→restore; 120-action queue + update + account isolation + two devices; real Play sandbox purchase/restore/refund.
2. `ACCEPTANCE.md` and `MANUAL-CHECK.md` exist with evidence links, honest statuses and explicit unfinished/unavailable items.
3. No open money, data-loss or permission defect; every unavailable Play/device check is listed as unfinished rather than passed.
4. The owner completes the manual acceptance and records the result.

## Boundaries

Assembly and evidence only: no new features, no silent scope cuts. Any defect found here returns to its owning epic as a bug ticket. Covers is empty by design — every line of Done when cites the spec's Success signal section.

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Success signal
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 5 (final acceptance, artifacts, matrices)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-24

## Notes

- Decision: covers is empty intentionally; the spec's Success signal section is the requirement source for every check here (template rule for an epic with no parent ids).
- Open question: OQ-5 operational cadence/budget/retention/recovery decisions — owner decisions surface at this epic's gate.
- Waits on epics 2–9 because: acceptance assembles their accepted outcomes; it starts nothing itself.
