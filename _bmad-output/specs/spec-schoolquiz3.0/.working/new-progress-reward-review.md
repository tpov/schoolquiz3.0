# Confirmed new-progress reward formula — independent review

Date: 2026-09-30
Reviewer: renewal_review
Verdict: PASS — scoped coherence, preservation and architecture review. No blocking findings remain.

This review covers the latest explicit confirmation of the formerly pending formula, not the previous open-formula state.

## Findings

- Both nolics and XP replace only the ordinary new-progress coefficient2 with theme3 or final10. The already-accounted final portion retains0.1. Ordinary weighted marginal tariff bands remain; calculations do not become raw-percent multiplication. Difficulty and allocated-time factors remain separate, as do currency conversion and the pending exact integration/rounding contract.
- For the all-HARD final, effective coefficients are NEW30 and REPEATED0.3 on their respective weighted portions before time/currency conversion. There is no extra whole-reward mode factor or uniform30 applied to the entire attempt.
- Durable reward-accounted maximum is per user/assessment and independent of the latest certificate and theme display. The 90→70→90 sequence pays no new-progress bonus again; at95 only the portion above90 is new. A lower latest certificate, renewal, expiry or another sitting cannot reset accounted progress.
- The final exam protocol now explicitly commits the single payout result/receipt with the updated accounted maximum atomically, including a rounded-zero settlement. Retry reuses that receipt. This closes the wording gap between already-paid and already-accounted progress and agrees with the main spec and architecture AD-36.
- Theme payout stays first-pass-only. Failed unpaid theme attempts do not consume accounted progress or reduce the later first-pass award; admission and its stable user/theme payout record retain their existing atomic boundary. Final eligibility still covers every terminal attempt including failure, without an invented positive minimum.
- Rolling theme24h/final168h cooldowns, original terminal/recovery semantics, linear server theme stars, default percent pass threshold and immutable latest-earned certificate policy are unchanged. Failed finals issue no certificate while remaining reward-eligible.
- Q-EX-6 now retains only actual mixed reward base, allocated-time normalization, currency rounding and shared integration fixtures. The coefficient placement, new/repeated split, history reference and payout eligibility are no longer falsely listed as unapproved.
- Stable sequences remain main CAP-1–23, exam CAP-1–21, main AD-1–24 and exam AD-1–37. Unrelated question IDs and every-screen content confirmation are preserved. Main embedded HTML SPEC matches after trimming only surrounding blank lines; UX constraints are byte-identical to baseline.

## Evidence and limits

Reviewed current main SPEC/contract-map/HTML, exam SPEC/protocol and architecture spines/solution/epic companions against their saved narrow baselines. Read-only inspection of functions/lesson-reward.js:33–103 confirms the existing weighted tariff bands, ordinary new2/repeated0.1, HARD3, allocated-time factor and currency conversion boundaries reused by this design. This is source-reference evidence, not proof that the new assessment formula is implemented.

No code was edited or executed, no application tests/builds or runtime operations were performed, and no public-site acceptance is claimed. Mixed reward integration details and other remaining product/screen questions still need their own closure; this PASS concerns the narrow confirmed formula amendment.

## Structural checks and reviewed hashes

```json
{
  "main_CAPs": {
    "count": 23,
    "stable": true
  },
  "exam_CAPs": {
    "count": 21,
    "stable": true
  },
  "architecture-schoolquiz3.0-2026-09-29": {
    "count": 24,
    "stable": true,
    "AD-14_unchanged": true,
    "AD-16_unchanged": true
  },
  "architecture-schoolquiz3.0-2026-08-29": {
    "count": 37,
    "stable": true,
    "AD-18_unchanged": true,
    "AD-19_unchanged": true,
    "AD-28_unchanged": true,
    "AD-29_unchanged": true,
    "AD-37_unchanged": true
  },
  "embedded_MAIN_matches": true,
  "remaining_QEX": [
    "Q-EX-2",
    "Q-EX-4",
    "Q-EX-5",
    "Q-EX-6",
    "Q-EX-7"
  ],
  "main_UX_unchanged": true,
  "sha256": {
    "_bmad-output/specs/spec-schoolquiz3.0/SPEC.md": "41924bf5c3fdb9e3a535a6659d2d19f4298f70f3fbc63b1c9f6d4312ebe2d974",
    "_bmad-output/specs/spec-schoolquiz3.0/contract-map.md": "18527c0db3b7853492c89365a5a2e850c50adbbcd21d0bf148e6108ce34273b5",
    "_bmad-output/specs/spec-schoolquiz3.0/SPEC-REVIEW.html": "dad052eb4d7f8cb5e988f939b5e104ec1994a00a5a43b3529809ffccd2af162e",
    "_bmad-output/specs/spec-theme-exams/SPEC.md": "1b396f247a0aa6982730a875e1dd151ce9dd3ab597743e2bc3fe62bbaa52470e",
    "_bmad-output/specs/spec-theme-exams/exam-protocol.md": "972596114c1b73729796f65d6677a2c9a63362473a5f31106f469a7113bcc1ad",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md": "b59804147d5e34ca45edc974766e428e6416a1fb31190282c941502ab0ae37fd",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md": "d9d01ab36825af98b879a3dcfafdeafc48a8d766adb5802d8f342638799f9cab",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md": "0db22197afd46b9756c7f8188818249dfeed20b6ffa6854cc4e544d2610a081b",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md": "1bcc707300bd9b938602130b93d04fb2e48f5f247d98025412fed062078c6699"
  }
}
```
