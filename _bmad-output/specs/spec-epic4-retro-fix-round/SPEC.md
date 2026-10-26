---
id: SPEC-epic4-retro-fix-round
companions:
  - fix-catalog.md
  - ../../mvp-candidate/epic-authoring-review/RETROSPECTIVE.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Epic-4 retro fix-round

## Why

A **pain to solve**, routed by the epic-4 retrospective (2026-10-25, commit `1e793122`): the authoring/review pipeline shipped with verified deploy-time, money-integrity, and receiver-validation gaps — a functions deploy today demotes in-flight lessons (L2), the wallet's balance mirror drifts from the ledger on every paid review cycle (L3), the tariff can be keyed to a foreign author's quest id (L4), a live-documented wire-form hazard still crashes the curator receipt (L1), and an error report can be silently substituted at submission (L5). These block safe deploys and the resumption of story 4.10's rig work; the owner accepted the epic with these findings routed as owned fix-now items.

## Capabilities

- **CAP-1**
  - **intent:** A functions deploy does not demote lessons already through TESTING (pre-4.6 records without per-question scores stay passed).
  - **success:** A test with a legacy TESTING record (score present, `questionScores: []`) on a question-bearing task keeps `isTested: true` and the READY path when a rebuild triggers.
- **CAP-2**
  - **intent:** Fee charge and settlement leave both wallet balance fields (`pointsNolics` and the `nolics` mirror) consistent with the ledger.
  - **success:** After a charge and after a settlement, tests show both fields equal to the expected balance.
- **CAP-3**
  - **intent:** The review-fee tariff decision uses only this author's payment history.
  - **success:** A submission naming another author's quest id is refused by a named code and writes nothing under the foreign id (test).
- **CAP-4**
  - **intent:** Lifecycle view parsers accept every integer form the Android callable SDK actually delivers.
  - **success:** Parser tests covering non-Long numeric JSON (the live-4.3-documented shape) pass for every long field of the receipt.
- **CAP-5**
  - **intent:** A replayed error report answers success only to its filer.
  - **success:** The same reportId replayed under a different uid (or different lessonId) is refused; the honest filer's replay still returns the stored result (tests).
- **CAP-6**
  - **intent:** A curator transition retried after an unknown outcome reuses its original idempotency key (AD-2).
  - **success:** A component test asserts one and the same mutationId across a failed attempt and its retry; success clears the pending key.
- **CAP-7**
  - **intent:** Editor undo steps correspond to actual content changes only.
  - **success:** Component tests: a guard-refused add/remove adds no history step; undo after a typing burst restores pre-burst content.
- **CAP-8**
  - **intent:** Stored segment rows without explicit decisions are surfaced decision-less, never as manufactured ones.
  - **success:** A legacy segment row round-trips decision-less (or flagged) and is treated as such by scoring — never "correct".
- **CAP-9**
  - **intent:** The transition endpoint enforces the reason byte ceiling the client and the reject path already assume.
  - **success:** An oversized reason is refused by a named code; a 1000-byte reason is still accepted (test).
- **CAP-10**
  - **intent:** The review-outcomes projection actually rejects repeated question ids.
  - **success:** A projection payload with a duplicated questionId is refused; distinct ids pass (test).

## Constraints

- Loud, named refusals — never silent clamping or fallback — on the fee and receiver guard paths (binds CAP-3/5/9/10).
- No manufactured data: fixes must not rewrite or backfill stored review history (binds CAP-1/8); guards live on the read/write paths, no migration jobs over stored documents.
- Gates at head: `functions` `node run-tests.js` green with the new pinning suites (the `mvp-isolation` red is foreign, owned elsewhere); touched Kotlin modules green on jvmTest + ktlint.

## Non-goals

- Story 4.10's unblock build (draft-selection UX decision, rig wallet economics).
- LOGIC/threshold knob semantics (L6) — awaits the owner's gate-or-split decision.
- Translation self-review rule (L12) — owner question.
- Inbox truncation signals (L13) and all consolidations A2–A6 (parser kits, fakes, rig drivers, index.js extraction).

## Success signal

One committed build lands all ten fixes with their pinning tests, the functions gate is green at head, and replaying the retro's L2 scenario (legacy record + aggregate rebuild) no longer demotes the lesson — the next deploy is safe for in-flight review cycles.

## Assumptions

- The owner's 2026-10-25 acceptance covers the epic, not these defects; this round is authorized by the retro's fix-now routing alone.
- Scope is retro action items 1–8 plus the small pre-existing guard (item 8); items 9–12 stay out.

## Open Questions

- CAP-3 refusal shape for a foreign `draft.id`: hard refuse (default) or price-as-initial without a cross-owner write?
- reportId entropy: even with the CAP-5 check, client-derived ids stay predictable — derive them server-side? (Non-blocking for the guard.)
