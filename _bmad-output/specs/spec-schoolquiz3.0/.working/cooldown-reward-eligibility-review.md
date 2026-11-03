# Cooldown and final-reward eligibility amendment — independent review

Date: 2026-09-30
Reviewer: renewal_review
Verdict: PASS — scoped coherence, preservation and architecture review. No remaining blocking findings.

## Reviewed decision boundary

The owner has settled rolling theme cooldown at 24 hours and course-final cooldown at seven days (168 hours). Every terminal final attempt is reward-eligible, including failure. Nominal assessment coefficients 3 and 10 remain, but the owner has reopened their formula placement. Neither the earlier whole-reward stacking nor the proposed new-progress replacement is treated as approved by the separate cooldown answer.

## Evidence and preservation

- Main SPEC/map/HTML, exam SPEC/protocol, both architecture spines and exam solution/epic companions agree. The independent review compared their scoped saved baselines with current files.
- Cooldowns are per user/theme and per user/course final, anchored to terminal closure including timeout/abandonment. Midnight, calendar-week and timezone resets are excluded. Recovery preserves the same open sitting, deadlines and original operation without another charge, attempt or cooldown anchor. Existing start charges, admission predicates and frozen rule snapshots are retained.
- Every terminal final attempt, including failed/repeat attempts, is eligible for nolics and XP under the eventual formula. Same-sitting retry/recovery is not a second reward event. Eligibility promises no positive minimum at zero score, and reward history/formula details remain prerequisites rather than fabricated payouts.
- Only a successful final issues a certificate. The remaining ambiguous exam SPEC wording “issues nothing” was corrected to “issues no certificate” during review. Latest-earned selection, immutable earlier attestations, one-year frozen dates and delayed-signing order remain unchanged.
- Nominal theme3/final10 is retained. Q-EX-6 now records formula placement, new/already-paid progress mechanics and mixed base/time/rounding as unresolved. It does not reopen settled final-attempt eligibility or silently approve the proposed ordinary2/theme3/final10 plus repeated0.1 calculation. The old whole-reward x30 conclusion is no longer an active settled instruction.
- Theme reward remains first-pass-only with its existing atomic user/theme payout identity. Linear server theme stars, percentage pass/fail and best-theme-result retention are preserved. Every-screen content confirmation is unchanged.
- Stable ID sequences remain main CAP-1–23, exam CAP-1–21, main AD-1–24 and exam AD-1–37. Q-EX-2/4/5/6/7 remain; Q-EX-6 is narrowed/reopened according to the actual decisions. Main certificate AD-16, exam certificate AD-19 and exam star AD-37 decision sections are byte-identical to their respective baseline sections.
- HTML embedded SPEC matches after trimming only surrounding blank lines. Main UX constraints are byte-identical to baseline. No active adopted exam cooldown of 23 hours remains in the reviewed exam/architecture companions; main precedence text retains it only as the superseded prior rule.

## Limits

This review is documentation-only. No application code, build, tests, service, financial operation or deployment was run. Current source/runtime behavior was not claimed to implement these new decisions. The reward formula remains pending owner confirmation; this PASS does not mean the product or all screens are fully agreed.

## Reviewed hashes

```json
{
  "_bmad-output/specs/spec-schoolquiz3.0/SPEC.md": "8989c1c5e22128489878b79eb855e9749844c2b8e9ce55444a49080ad6e0b73e",
  "_bmad-output/specs/spec-schoolquiz3.0/contract-map.md": "72df3cf6bcec215e1bb6e30ea78ee64870c3e7681e0ce4fbe94d9b7a2bcfd078",
  "_bmad-output/specs/spec-schoolquiz3.0/SPEC-REVIEW.html": "619a9336be45d6b2ef1bb161e512d4c22b7ede4e50dd2aed9f1bdc6a7355e720",
  "_bmad-output/specs/spec-theme-exams/SPEC.md": "b2be9e18de1fe67cf19656d622f6abe9f2ea1954cd277d185ff3a5192b9bfab1",
  "_bmad-output/specs/spec-theme-exams/exam-protocol.md": "a5c8959252d573f7eebcf1325003583c4ee4e9c25277d005698bfdd43949ce99",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md": "73b26ad943474c270fbfea6b70cd8815f1f063586349d0d847134d15802bba67",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md": "1b11a96e77a4b159ab7aef77a3f0a2da2c4aed4b68673b724970709fe527778d",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md": "1ea3a0fafd7d06e719fe9707b75ed0c5d6fd597e1c29ee4c325815a905217e06",
  "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md": "b6b8a444cd902363b7c256943842d227792e248e67d55ae8620af88c37d49b0e"
}
```
