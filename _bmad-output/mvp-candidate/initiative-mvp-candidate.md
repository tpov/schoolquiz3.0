---
type: initiative
title: "SchoolQuiz full Android MVP candidate accepted by the owner"
parent: none
covers: [CAP-1, CAP-2, CAP-3, CAP-4, CAP-5, CAP-6, CAP-7, CAP-8, CAP-9, CAP-10, CAP-11, CAP-12, CAP-13, CAP-14, CAP-15, CAP-16, CAP-17, CAP-18, CAP-19, CAP-20, CAP-21, CAP-22, CAP-23, CAP-24, CAP-25, CAP-26, CAP-27]
after: []
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# SchoolQuiz full Android MVP candidate accepted by the owner

## Description

One complete, reviewable Android MVP in which learners study, prove course knowledge and earn certificates; authors publish independently checked material; and ordinary learning, Arena, competitions, polls, private quizzes and commerce work together on one identified candidate. The spec owns the capabilities, constraints and non-goals; this initiative delivers the remaining implementation and the evidence that ends in the owner's manual acceptance. Existing brownfield work is reality to build on, not acceptance evidence.

## Outcome

The owner receives one identified candidate (code, backend, APK, schema, config, flags), traceable PASS/FAIL/NOT RUN evidence for every mandatory acceptance path, and manually accepts it — the spec's Success signal.

## Done when

1. Every mandatory PRD acceptance path and the exam protocol run together on the identified candidate, including author/review/publication, ordinary learning, Arena, tournament-to-championship, polls, private quizzes, operation history, commerce, configuration, feedback and exam-to-public-certificate.
2. Delivery is proven after offline/restart/lost response, concurrency and account switch, non-destructive upgrade, financial conservation and exact receipts, including the 120-action queue, full-cohort cases and the certificate correction/pending-signing cases.
3. No open money, data-loss or permission defect; no mandatory check left unrun; every unavailable Play/device check is explicitly listed as unfinished.
4. The owner completes the manual acceptance on real devices and records the result.

## Boundaries

Capability boundary over one Android candidate on the existing VPS backend. Not iOS/web, not public release, not transfer into the main KMP project, not the FR-56 guide — see the spec's Non-goals. Tracer path: an author publishes checked material, a learner studies it, plays an Arena attempt, pays and receives the author share, then qualifies into a tournament — owner matrix 1.

- Touch point: physical devices and Play Console inventory — consumed for runs and sandbox purchases; owner: epic-candidate-rig (inventory), epic-final-acceptance (sandbox cases)
- Touch point: demo Firebase project and containers — restored and owned by epic-candidate-rig; later epics consume it
- Touch point: VPS resources and storage — owner: the user (per owner instruction, disk/cache work stays with the owner)

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, sections Capabilities, Constraints, Non-goals, Success signal, Open questions
- constraint — same spec, section Constraints (financial integrity, server authority, AD-31 timing, RU/UK/EN accessibility)
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md (owner-approved continuation plan and acceptance matrices)
- handoff — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/HANDOFF.md (verified brownfield status, devices, known RED quiescence test)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md (AD-1–31, remaining gates)

## Notes

- Decision: epics follow the owner-approved continuation plan grouping (2026-09-28 plan §2–§5) and the spec's capability clusters; one owner per epic, contracts between epics only.
- Decision: Arena epic precedes the authoring epic, matching the owner's plan order (§2 before §3).
- Assumption: ONLINE-O2's home is the spine gate (AD-31, contract-map Remaining gates); its landing entry lives in epic-ordinary-delivery, and Arena/exam/competition adapters wait on it.
- Open question: OQ-2 (conditional unfinished-review settlement) — owner decides only before adding that outcome; parked.
- Open question: OQ-4 (Q-EX-5 signing/environment activation) — hitl entry inside epic-exams-certificates; parked until its gate.
- Open question: OQ-5 (operations/release environment inventory) — hitl entries in epic-candidate-rig and epic-final-acceptance.
- Open question: OQ-6 (ONLINE-O1 competition cutoff reconciliation) — entry inside epic-competitions.
- Open question: OQ-7 (named UX integration contracts) — distributed as entries/`unknown`s in the adopting epics per contract-map Remaining gates.
- Parked: OQ-8 / FR-56 guide — separate future task, explicitly out of this version (spec Non-goals).
- Waits on nothing: this initiative starts from the verified candidate HEAD 1e596126.
