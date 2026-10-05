# Theme stars and additional assessment rewards — independent review

Date: 2026-09-30
Reviewer: renewal_review
Verdict: PASS — scoped coherence, preservation and architecture review; no blocking findings.

The reviewed version includes the latest owner clarification: assessment multipliers are additional to ordinary rewards. It supersedes the earlier partial draft that left replacement-versus-stacking open.

## Findings

- Theme stars are linear from 0 to 3, computed and returned by the server at half-up tenths precision. Existing percent pass thresholds and best-theme-result retention remain independent. Examples correctly distinguish equal displayed 1.8 stars at 59% and 60% from different pass outcomes at the existing 60% threshold.
- Both nolics and XP retain ordinary EASY x1 / HARD x3 and add theme x3 or final x10. The all-HARD final has the combined x30 coefficient at comparable base inputs, with no second accidental HARD multiplier. The mixed theme is not silently treated as wholly HARD x9.
- Q-EX-1 is resolved; Q-EX-6 retains only actual remaining reward work: mixed-base aggregation, allocated-time normalization, reward rounding and final payout eligibility/repeats/stable identity. Exact rounded amounts and payout eligibility are not inferred from the combined coefficient or from certificate issuance.
- Theme payout stays first-pass-only with its existing user/theme identity and atomic admission boundary. Retries, ordinary retakes and correction replay cannot create another theme payout or erase its prior award.
- Latest-earned certificate selection and immutable history are preserved. Both certificate architecture decision blocks are byte-identical to the baseline for this amendment.
- Main CAP-1–23, exam CAP-1–21, main AD-1–24 and exam AD-1–37 remain stable. Exam CAP-15 legitimately expands from the existing first-pass theme reward to the explicitly requested assessment-reward scope. Q-EX-2/4/5/7 and every-screen content confirmation remain.
- Main SPEC, contract-map, HTML, exam SPEC/protocol, architecture spines and exam solution/epic companions agree. Embedded HTML SPEC matches after trimming surrounding blank lines. Original UX constraints are unchanged.

## Reality evidence and limits

Read-only source inspection of functions/assessment-scoring.js:250–266 confirms existing tenths/half-up conventions, while the existing ordinary EASY 0–2 / HARD 2–3 mappings remain separate from the newly agreed theme 0–3 mapping. functions/lesson-reward.js:96–103 confirms the ordinary reward retains allocated-time and difficulty factors with integer reward rounding. This supports retaining an explicit mixed-assessment and reward-rounding contract rather than guessing a formula.

No application code was changed, built or executed. No runtime, deployment or public-site acceptance is claimed. Final payout eligibility and repeat policy remain open in this reviewed version; this PASS does not approve them or all screen contents.

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
    "certificate_unchanged": true
  },
  "architecture-schoolquiz3.0-2026-08-29": {
    "count": 37,
    "stable": true,
    "certificate_unchanged": true
  },
  "embedded_SPEC_matches": true,
  "UX_constraints_unchanged": true,
  "open_exam_question_ids": [
    "Q-EX-2",
    "Q-EX-4",
    "Q-EX-5",
    "Q-EX-6",
    "Q-EX-7"
  ],
  "sha256": {
    "_bmad-output/specs/spec-schoolquiz3.0/SPEC.md": "e2adef1dfb2446eb7772e68e8f8cf5be489c149bb62adbe3a12c5405e4b61a5e",
    "_bmad-output/specs/spec-schoolquiz3.0/contract-map.md": "1ae8ac9f17e1c85e26785cc499416d94158c496795074d595c2ef0b58652e4f5",
    "_bmad-output/specs/spec-schoolquiz3.0/SPEC-REVIEW.html": "af1faaf2927ccbbf0dfe1217112182d16545798cfd3a7299681504452155ac81",
    "_bmad-output/specs/spec-theme-exams/SPEC.md": "6ed3a3aacf1225951a4c4e21fda7af0b1c6919152f02d6837713b4ceba9bd8fd",
    "_bmad-output/specs/spec-theme-exams/exam-protocol.md": "d320c487f2cbfc7c46d37c6f478ce1c45746776ca8846b9033291daa57356976",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md": "c61a3d2354b065442141629604d115da0b04824c03f231692a35bba18caef1c0",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md": "b9cb76a70c3c6930a38ca698dda53fe602e09a2e0a697b393de865a597a10272",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md": "e1e762db33bb805ca91b30f65cb69517b4f1a20f90cdd959506f613b4b2cd56f",
    "_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md": "c3f31374f0fe3a530a018750b54882f4bd5503a86281a146ebc361d4af27aa93"
  }
}
```
