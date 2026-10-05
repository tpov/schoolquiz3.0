---
type: epic
title: "Theme tests, final exams and certificates"
parent: mvp-candidate
covers: [CAP-21, CAP-22, CAP-23]
after: ["epic-ordinary-delivery", "epic-authoring-review"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Theme tests, final exams and certificates

## Description

Knowledge proving to the adopted exam contract: a 20-question theme test (5 EASY + 15 HARD) after confirmed required-EASY coverage, with 24-hour cooldown, 100 STANDARD entry and linear 0..3 stars (CAP-21); a 20-HARD final over the latest approved course with one-year attestations, public verification, 7-day cooldown and 200 STANDARD entry (CAP-22); and accountable corrections that preserve settled rewards, recalculate denominators safely and never turn a corrected failure into a certificate (CAP-23). EXAM-TECH-1 fixtures are the shared proof; exam SPEC and protocol stay binding companions.

## Outcome

A learner who studied can pass a theme test and earn a verifiable course certificate, with corrections and rewards that survive every edge the EXAM contract names — owner journey exam-to-public-certificate works.

## Done when

1. A theme test runs under the adopted composition/timing/cooldown/entry rules; ordinary access purchase grants no pass; required-EASY coverage is confirmed by versioned evidence (CAP-21).
2. A successful final issues a distinct one-year attestation atomically; public verification works signed out; profile shows the latest certificate even at a lower passing score; failed finals issue nothing (CAP-22).
3. A confirmed-fault correction preserves issued certificates and settled rewards, recalculates the denominator with shared arithmetic, and a failed→passing correction issues no certificate without a new sitting (CAP-23).
4. EXAM-TECH-1 fixtures pass: reward worth independent of assessment timer, independent currency rounding, accounted progress surviving lower scores/zero awards, correction/admission race barrier.
5. Entry charges are 100/200 STANDARD points with atomic start/debit and no-charge refused start; PLASMA cannot pay admission.
6. Signing/environment activation (Q-EX-5/OQ-4) is resolved at its gate with the owner.

## Boundaries

Assessment lifecycle, certificates, corrections. Not ordinary lesson learning (epic-ordinary-delivery), not content quality machinery beyond exam consequences (epic-authoring-review), not the store (epic-commerce).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, sections Capabilities (CAP-21..23) and Constraints (theme/final paragraphs)
- companions — _bmad-output/specs/spec-theme-exams/SPEC.md and exam-protocol.md (EXAM CAPs, C1–C4 via assessment-technical-contracts.md)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-14/15/16/23/31
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 4 (assessment fixtures) and section 5 (certificate cases)

## Notes

- Open question: OQ-4/Q-EX-5 concrete signing provider/environment — hitl with the owner at this epic's gate.
- Unknown: current implementation state of the exam line (August spec) vs the October decisions (24h/168h, 100/200 entry, timer) — an inception entry reconciles drift before building.
- Waits on epic 2 because: admission needs ordinary EASY coverage evidence and the shared online window.
- Waits on epic 4 because: banks are drawn from published approved questions.
