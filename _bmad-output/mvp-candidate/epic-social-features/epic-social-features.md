---
type: epic
title: "Polls, private quizzes, operation history"
parent: mvp-candidate
covers: [CAP-24, CAP-25, CAP-26]
after: ["epic-ordinary-delivery"]
assignee: ""
risk: medium
estimate: ""
estimate_basis: ""
---

# Polls, private quizzes, operation history

## Description

The October additions: an author creates poll series and participants vote once with a hidden-then-visible distribution over distinct respondents (CAP-24); an author shares a HARD-only private quiz under AD-29 rules — defaults 1 attempt/hidden/no expiry, limit changes preserve startedCount, closed links never interrupt started attempts, recovery timeout scores the whole quiz with remaining questions unanswered, current show-result governs every own result (CAP-25); and account holders inspect their income/spending while authors inspect lesson earnings through authorized read projections where pending is never credited and unlike resources are never summed (CAP-26).

## Outcome

Three new journeys work on the shared delivery/projection foundations without inventing parallel mechanisms — polls, private quizzes and history behave exactly as the settled owner decisions say.

## Done when

1. A poll series runs: 2–8 options, single/multiple choice, one vote per participant with retry never adding another, distribution hidden until the confirmed vote, multi-choice totals may exceed 100% (CAP-24).
2. A private quiz runs under AD-29: defaults on creation, atomic start against enabled/unexpired access and current limit, started attempts finish under original deadlines, timeout yields the whole-quiz result with unanswered questions, current visibility applies to past results (CAP-25).
3. Private quizzes use no charges and no deferred continuation.
4. Operation history and lesson earnings show authorized records with consistent identity/date/reason/signed amount/resource/status; filters and pagination preserve context; account switch reveals nothing foreign (CAP-26).
5. Reading history creates no financial effect.

## Boundaries

The three read/write journeys. Not the shared inbox (epic-authoring-review owns CAP-6), not the financial ledger writes (owned where money moves), not exam scoring (epic-exams-certificates).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-24..26) and Constraints (private quiz paragraph, history paragraph)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-8/20/29/30/31
- gates — _bmad-output/specs/spec-schoolquiz3.0/contract-map.md, Remaining gates (UX-O4/O7/O10/O12)
- ux — _bmad-output/planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/EXPERIENCE.md, F10/F11

## Notes

- Waits on epic 2 because: delivery, projection fencing and ledger identity are the shared foundations these journeys consume.
