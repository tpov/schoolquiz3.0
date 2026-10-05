# Certificate renewal amendment — independent review

Date: 2026-09-30
Reviewer: renewal_review
Scope: documentation amendment for owner decision permitting renewal before expiry and displaying the latest newly earned certificate even when its passing score is lower.

## Verdict

PASS — coherence, preservation and scoped architecture review. No blocking findings.

## Evidence

Reviewed current files against the saved renewal baselines: initiative SPEC.md, contract-map.md and SPEC-REVIEW.html; exam SPEC.md and exam-protocol.md; main architecture AD-14/16, exam architecture AD-19, solution-design.md, epic-breakdown.md E9, and ADR-0007. Canonical memo decision entries were inspected. The scope is document consistency, not implementation or runtime acceptance.

- Stable sequences preserved: initiative CAP-1–23, exam CAP-1–21, main AD-1–24 and exam AD-1–37. Initiative UX constraints are byte-identical to their baseline.
- Every successful terminal final, including early renewal, atomically records a distinct immutable issuance and durable fulfilment work. Retry of the same sitting preserves its issuance identity, claims, result version and dates; failed finals create no issuance or replacement.
- The current certificate follows authoritative earning order, not best score or asynchronous signing completion. The newest pending issuance stays explicit and late fulfilment of older signing cannot replace it. Architecture fixes server-owned total earning order at the terminal boundary.
- Each new issuance has its own fixed one-year term. Earlier certificates remain unchanged and independently verifiable; a content-fault correction cannot revoke, rewrite or extend them. Existing separate administrative status policy is preserved.
- Theme best-result and admission policies remain intact, including the separate authorized confirmed-fault correction path. The renewal rule does not import latest-result display into theme scoring.
- Q-EX-3 is resolved; Q-EX-1/2/4/5/6/7 remain with unchanged meanings. The main question reference agrees. No new scoring threshold, fee, reward formula or runtime authorization was introduced.
- HTML summary agrees with the new policy. Its embedded SPEC text is identical after trimming only surrounding blank lines. The 90% then passing 70% example does not change the existing configured default 60% threshold.

PRD/addendum retain detailed scope and existing immutable-certificate/current-course rules. The new owner decision has explicit precedence in contract-map.md; no conflicting best-certificate or annual-only restriction was found in the adopted PRD amendment sections.

## Limits

No application code, tests, build, runtime, deployment or public-site verification was performed by this reviewer. This PASS concerns this narrow document amendment; it does not close remaining product questions or approve all screen content.
