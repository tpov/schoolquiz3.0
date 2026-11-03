---
type: epic
title: "Competitions: qualification tournament to championship"
parent: mvp-candidate
covers: [CAP-11, CAP-12]
after: ["epic-ordinary-delivery", "epic-arena-completion", "epic-authoring-review"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Competitions: qualification tournament to championship

## Description

A funded competition from eligible material: the course stays in the library, content and the full fund freeze, three distinct EASY attempts feed an immutable top16 from the whole eligible cohort, and a private course admits downloaded-course users or link entrants only while qualification runs (CAP-11). Qualified players agree match times, play under the common online timing rule, and see the final bracket and awards with the fund conserved (CAP-12). Product terms: турнир = qualification, чемпионат = the complete final part. Owner plan §3 ТЗ07A/B/C, ТЗ08A–C.

## Outcome

From a published course to a settled championship with immutable qualification and a conserved fund — owner matrix 2 becomes runnable.

## Done when

1. A competition is created from an eligible course; the fund and content freeze, retries reuse the event, and the prize list counts every place once (CAP-11).
2. Qualification selects top16 from the whole eligible cohort at full size (502/1503-attempt fixtures as tests, not caps), with phase state preserved across interruptions and no infinite wait above 501 accepted attempts.
3. Championship proposals/confirmations and both READY states survive re-entry; opponents receive the same HARD form with authoritative timing.
4. Absences, ties, byes and technical outcomes follow the adopted matrix; ranking/awards/returned remainder conserve the fund against the frozen prize list (CAP-12).
5. Private-course admission by downloaded course or link closes at the server qualification interval; link never bypasses selection; earned championship rights survive expiry.
6. ONLINE-O1 (competition cutoff vs the full next-question window) is reconciled before the affected adapter is enabled.

## Boundaries

Competition server machinery and the app's tournament/championship UX. Not Arena ratings/season (epic-arena-completion), not content publication (epic-authoring-review).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-11/12) and Constraints (tournament/championship terms, prize rows)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-17/23/31
- gates — _bmad-output/specs/spec-schoolquiz3.0/contract-map.md, Remaining gates (ONLINE-O1, UX-O2/O3)
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 3 (ТЗ07/08) and section 4 (tournament fixtures)

## Notes

- Open question: OQ-6/ONLINE-O1 is this epic's gate entry; the common timing policy is settled.
- Unknown: the exact rank/tie/technical-outcome mapping contract (UX-O2) must exist before CREATE/reserve — planned as this epic's leading entry.
- Waits on epic 2 because: online match questions ride the shared window contract.
- Waits on epic 3 because: qualification consumes the Arena attempt cycle and full-cohort calculation.
- Waits on epic 4 because: competitions are created from published eligible material.
