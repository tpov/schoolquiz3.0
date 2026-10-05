---
type: epic
title: "Final NOIR experience across all journeys"
parent: mvp-candidate
covers: [CAP-17]
after: ["epic-ordinary-delivery", "epic-arena-completion", "epic-authoring-review", "epic-competitions", "epic-exams-certificates", "epic-commerce", "epic-social-features"]
assignee: ""
risk: medium
estimate: ""
estimate_basis: ""
---

# Final NOIR experience across all journeys

## Description

Every current MVP journey, including exams and certificates, implements the final DESIGN and EXPERIENCE (2026-10-01) with actionable loading/empty/failure/offline/insufficient-funds/forbidden/pending/recovery states in RU/UK/EN; shared NOIR visual semantics (AD-25), accessibility and confirmed interactions preserved; no guide or guide placeholders. Per-epic stories apply the design system as screens are touched; this epic owns the shared components and the final sweep that proves coverage.

## Outcome

All journeys look and behave as the agreed design on a real phone, with accessibility verified on actual Compose scaling and assistive operation.

## Done when

1. Every journey from epics 2–8 runs through its screens in the final DESIGN/EXPERIENCE with the required state set in RU/UK/EN.
2. Compose font scaling and assistive interaction are verified on device: text scaling does not hide values/actions, focus order and focus ring work, semantic actions exist.
3. No screen carries a guide placeholder or discovery-era artifact; final UX approval is not treated as runtime proof.
4. The design-system components (AD-25/NOIR) are the single shared implementation; no feature-local visual forks remain from the sweep.

## Boundaries

Design-system implementation and screen coverage sweep. Not new product behavior (owned by feature epics), not the FR-56 guide (non-goal), not acceptance matrices (epic-final-acceptance).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-17) and Constraints (RU/UK/EN paragraph)
- ux — _bmad-output/planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/DESIGN.md, EXPERIENCE.md and .working/implementation-handoff.md
- ux-constraints — _bmad-output/specs/spec-schoolquiz3.0/ux-constraints.md
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-25

## Notes

- Assumption: screens are restyled continuously inside feature epics where cheap; this epic owns shared components, the sweep and the accessibility proof (2026-10-03, slicing).
- Waits on epics 2–8 because: their journeys define the screens to restyle and the sweep needs them final.
