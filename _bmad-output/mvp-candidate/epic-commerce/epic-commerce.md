---
type: epic
title: "Store, premium, boxes, purchases and refunds"
parent: mvp-candidate
covers: [CAP-13, CAP-14, CAP-15]
after: ["epic-candidate-rig", "epic-ordinary-delivery"]
assignee: ""
risk: high
estimate: ""
estimate_basis: ""
---

# Store, premium, boxes, purchases and refunds

## Description

Commerce to the owner's plan §3 ТЗ09/ТЗ10: compare offers and use premium with doubled regeneration effects over valid terms (CAP-13); receive and open boxes where the ten-day streak gift is automatic, the Premium gift is an explicit no-ad claim, and both keep their own limits over common stock (CAP-14); purchase/restore/refund with provenance so pending or unverified purchases grant nothing, partial refunds touch only the original grant, and restore stays available when sales are disabled (CAP-15). Gold prices are server-owned; money prices are Play-owned; unconfigured products are explicitly unavailable. Dependency audit (ТЗ10B) lands here as its own commit.

## Outcome

A player can buy, restore and be refunded exactly the right grant on a real Play sandbox, and gifts/premium behave per the adopted rules — owner matrix 4 becomes runnable.

## Done when

1. The store shows full prices with normalized premium comparison; active grants determine expiry and both doubled regeneration effects, including holds and overlaps (CAP-13).
2. Ten-day streak grant is automatic and independent of Premium; Premium gift needs an explicit claim without an ad; one source/retry cannot consume or duplicate the other (CAP-14).
3. An online box opening survives loss/restart by returning the same stored reward.
4. Real Play sandbox purchase/restore/refund cases pass: grant/refund in both orders and concurrently, partially used premium refund, foreign-account and duplicate grants rejected, restore with sales disabled (CAP-15, matrix 6).
5. Unconfigured products are visibly unavailable, never silently purchasable.
6. ТЗ10B dependency audit lands as a separate commit with current Node/HTTP/Firebase/rules checks green.

## Boundaries

Commerce surfaces and purchase integrity. Not wallet projections/history (epic-social-features owns reading), not Arena pricing (epic-arena-completion), not Play Console setup (touch point — inventory in epic-candidate-rig, sandbox cases here and in epic-final-acceptance).

## References

- spec — _bmad-output/specs/spec-schoolquiz3.0/SPEC.md, section Capabilities (CAP-13..15) and Constraints (gifts paragraph)
- architecture — _bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md, AD-18/19/22
- gates — _bmad-output/specs/spec-schoolquiz3.0/contract-map.md, Remaining gates (UX-O8/O12 receipts)
- plan — _bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md, section 3 (ТЗ09/ТЗ10) and section 4 (purchase fixtures)

## Notes

- Open question: OQ-5 device/Play inventory gates real sandbox cases; unavailable Play checks stay explicitly unfinished per the spec's success signal.
- Waits on epic 1 because: purchases need the identified candidate and device inventory.
- Waits on epic 2 because: CAP-16 config consistency and receipt foundations are prerequisites.
