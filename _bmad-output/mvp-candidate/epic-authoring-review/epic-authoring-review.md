---
type: epic
title: "Authoring, review, publication and curation"
parent: mvp-candidate
covers: [CAP-2, CAP-3, CAP-4, CAP-5, CAP-6, CAP-27]
after: ["epic-candidate-rig", "epic-ordinary-delivery"]
assignee: ""
risk: medium
estimate: ""
estimate_basis: ""
---

# Authoring, review, publication and curation

## Description

The content pipeline to the owner's plan §3 ТЗ06: an author creates and corrects material with attribution and protected ranges (CAP-2), a queued model review gives each question a pass/fail verdict with a reason and translates passed content automatically (CAP-3), the paid review cycle charges once and burns the whole fee (CAP-4), a curator promotes/removes with reasons (CAP-5), lesson comments plus private error reports reach permitted recipients through a shared inbox that never widens readership (CAP-6), and users propose changes to other authors' material, which the author approves (CAP-27).

## Outcome

A lesson travels from author draft through independent checks to publication and curation with attributable history and conserved money — the pipeline owner matrix 1 starts with.

## Done when

1. An author creates a lesson, submits a revision, sees per-question review outcomes and publishes an approved question; a permitted edit cannot silently overwrite another author's published material (CAP-2/3).
2. A submitted quest passes the model pipeline («Слова», «Факты», linter with at most three retries, automatic translation); each question gets pass/fail with a reason; failed questions return to the author; admins audit verdicts in Decisions (CAP-3).
3. A paid review cycle, including justified rejection, charges once and burns the whole fee; retry and retained evidence create no extra paid work (CAP-4).
4. A curator transition has an authorized decision and reason; a failed replacement keeps the valid prior revision (CAP-5).
5. A private lesson-error report reaches its permitted author through the shared inbox without re-revealing questions/answers; reviewer/curator roles alone grant no access (CAP-6).
6. All three proposal entries reach the target author after checks; approval applies exactly the checked revision, a stale proposal is refused, a copy never carries secret keys (CAP-27).

## Boundaries

Authoring, review, curation, lesson discussions. Not exams (epic-exams-certificates owns EXAM contracts and corrections), not the student-side catalog (epic-ordinary-delivery), GLM is used only by the review pipeline (AD-32); GLM parsing of other content stays out of scope.

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-2..6) and Constraints (review paragraph, title/description immutability)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-3/4/10/20
- gates — _bmad-output/specs/spec-schoolquiz3.0/contract-map.md, Remaining gates (UX-O6 annotation serialization; OQ-2)
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 3 (ТЗ06A–D, TZ06C draft state)

## Notes

- Open question: OQ-2 unfinished-cycle abandonment is conditional future scope; this epic does not add the outcome.
- Unknown: ТЗ06A is an eight-file local draft — its verification status must be re-established at inception.
- Waits on epic 1 because: the candidate identity is the build base.
- Waits on epic 2 because: shared inbox and config ride the delivery foundations.
- Change 2026-10-26 (planning-artifacts/sprint-change-proposal-2026-10-26.md): human review replaced by the model pipeline; 4.10 cancelled; stories 4.11–4.26 added.
