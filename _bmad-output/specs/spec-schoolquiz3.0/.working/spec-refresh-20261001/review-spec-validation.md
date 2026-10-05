# Independent spec validation — 2026-10-01

**Pass 1 — Coherence: PASS. Pass 2 — Preservation: PASS. No open findings.**

Reviewed final `SPEC.md`, `contract-map.md` and `ux-constraints.md` against `.working/spec-refresh-20261001/baseline`, the complete 158-line baseline initiative memlog, current MAIN AD-1–31 and the relevant final DESIGN/EXPERIENCE/handoff contracts. HTML, application code and runtime acceptance are outside this review. Reviewer changed only this report.

## Pass 1 — Spec Law 1–6 and 8

- All five kernel fields are present; 26 unique sequential CAP IDs have both intent and success. Intents describe user outcomes; detailed architecture and adapter contracts remain in mandatory companions.
- Constraints rule out concrete divergent behavior; non-goals and testable success signals are explicit. Current-release scope excludes the separately deferred FR-56 guide and placeholders without losing its stable requirement ID.
- All 14 companion paths and relative Markdown links in the three derived files resolve. No diagram or workflow artifact was inserted into the kernel.
- Closed UX-O1/O4/O5 choices remain closed. OQ-1/OQ-3 are retired rather than reused; OQ-2/4/5 retain their original limited purposes, and OQ-6/7/8 name technical integration or separately deferred scope.
- **M1 resolved before verdict:** the initial private constraint could imply that any lower limit blocks all future starts. The final text correctly requires enabled/unexpired access and `startedCount < currentLimit`, while preserving active-attempt completion rights. This matches MAIN AD-29 and UX-16; no new product choice was needed.
- The final UX-21 clarification explicitly records unanswered only on expiry **without a timely accepted answer**; this preserves accepted answers and agrees with AD-31. Rechecked after the logged re-derivation.

## Pass 2 — preservation

| Source group | Preservation result |
| --- | --- |
| Stable kernel and journal | CAP-1–23 retained; CAP-24/25/26 add polls/private quizzes/history. Whole CAP blocks 1/5/7/8/9/10/13/15/16/18/19/20/23 are unchanged. CAP-21/22 preserve their complete baseline bodies and append only STANDARD entry prices. All 152 baseline journal records remain an exact prefix of the 195 current records. |
| Author/review/publication | Immutable title/no description, revision/evidence provenance, independent directions, unaffected evidence and per-question publication remain. One TESTING reviewer/default2, explicit translation completeness and normal-cycle justified rejection are integrated without altering review arithmetic or inventing an abandonment settlement. Curator removal reason stays separate from review return. |
| Ordinary/Arena/sync/economy | Ordinary offline unlock/local debit intent and rollback, prepared Arena recovery, retained uncertain settlement/cancel, exact authoritative proof, rating from confirmed result, full-cohort calculations, fractional entitlements, Play/refund provenance, owner/epoch isolation and non-destructive migration remain binding. No new offline gate or shared lifecycle is introduced. |
| Exams, rewards and coverage | Composition 5E+15H/20H, ordinary HARD answer time without0.75, unchanged bounded initial ACK, stable-theme carry, latest approved course, confirmed exact-revision EASY union and admission readiness remain. C1 ordinary reward worth, NEW3/10/repeated0.1, independent rounding and atomic accounted maximum remain; C2 preserves settled awards and safe nonnumeric correction states. C3/all-writer generation barriers prevent stale admission; C4 worker/signing contracts and concrete activation gates remain adopted. |
| Certificates | Every originally successful final earns its own immutable one-year issuance; latest earning order governs display, including lower passing renewal. Failure and corrected failure-to-pass issue no certificate; original pending issuance, previous claims/dates/history and out-of-order signing behavior remain protected. Rolling24/168-hour terminal-anchored cooldown and same-sitting idempotency remain. |
| Competition and online amendments | Qualification/championship terminology, three EASY attempts/top16/full cohort, dynamic prizes counted once, course-or-link admission window and earned finalist rights remain. Meeting confirmation/READY and fund conservation are retained. AD-31 original clocks/shared captured60s/anchors/retry/no-last-window coexist with explicit local ONLINE-O1/O2 integration gates. |
| New accepted UX | Poll vote-set uniqueness and respondent denominator, all current private link/limit/visibility/timeout/default rules, no-charge/single-sitting scope, separate ordinary/Premium gift actions, exact annotations and scoped operation history are covered by CAPs and binding UX-15–21/adopted sources. Private product choices are not reopened. |
| UX and acceptance | UX-1–14 IDs remain, with obsolete discovery/blanket approval and current guide readiness explicitly superseded. Final tokens/compositions/states, localization/accessibility, 120-action/full-cohort/device/Play cases, candidate identity, honest evidence statuses and owner manual acceptance remain mandatory. Documentation/HTML approval is not runtime proof. |

Wrapper-only historical workflow, obsolete discovery status and gallery-link duplication need not remain in the kernel; current source precedence and mandatory final UX retain their load-bearing content. No app tests, builds, services, financial actions or publishing were performed by this review.

Validated file SHA-256:

- `SPEC.md`: `c356b0ce96a455f6d9a66f99292c22f7591139c3f2cfd39d3bc49d4462af2dc5`
- `contract-map.md`: `8a66e8c2b4e45915e22c6720e38066351cb7ec6a2d98fd3557b7e247adc821b6`
- `ux-constraints.md`: `ced2cd51ed00b168d87bcbfd02fd7ef08a022a3fbebe02643fc858d7254c777f`
