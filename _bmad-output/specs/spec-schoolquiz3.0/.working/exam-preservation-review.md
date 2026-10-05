# Independent Pass 2 — theme-exams preservation

Date: 2026-09-30. Reviewer: independent input-preservation lens.
Verdict: **PASS**. Blocking findings: **0**. Required fixes: **none**.

## Scope and evidence

Compared the re-derived `spec-theme-exams/SPEC.md` and authored `exam-protocol.md` with the final 2026-08-29 exam spine, final 2026-09-29 initiative spine, the canonical amended initiative PRD/addendum, the accepted exam decision history, and the author’s `.working/spec-adoption-validation.md`. Read adopted ADR-0003 including amendments and amended ADR-0007. This is document preservation, not runtime, implementation, or deployment validation.

The kernel has exactly CAP-1 through CAP-21, in order, with 21 intent/success pairs. It retains 14 constraints, 7 non-goals and 7 explicit open questions. The metadata date is 2026-09-30. All five declared companion paths resolve to existing files: authored protocol, two adopted spines, ADR-0003 and ADR-0007. `sources: []`; neither a memlog nor a `.working` report is promoted to a source or companion. Old `brownfield`/ADR-0005 assumptions are not active companions. The remaining legacy model names inside ADR-0007 do not reopen ownership or session design: its amendment, current spines and protocol explicitly bind those choices and require implementation-time code reconciliation.

## Preservation findings

| Binding claim | Preserved contract | Result |
|---|---|---|
| Scope and draw | All theme tests plus a distinct final are current MVP; theme 5 EASY + 15 HARD, final 20 HARD; exactly 20 scored; default configured 60%; uniform approved distinct available revisions, no pretest/unscored questions/rungs/history-filtered draw. | PASS: CAP-3/16/19, constraints, protocol. |
| Admission and course evolution | Full required EASY coverage must be confirmed; stars or a perfect arbitrary subset cannot substitute. Final compares current required stable theme identities; ordinary edits carry passed themes, newly required themes need a pass, archives do not count. Active sittings remain frozen. | PASS: CAP-1/9/16/17, Q-EX-4, protocol. |
| Bank and quality | Certification is at least 12 corresponding themes, each at least 100 EASY + 100 HARD approved available distinct questions. Draw sufficiency is separate. Smaller courses still teach. Independent approval admits exact revisions immediately; failed new revisions do not remove a valid old one. Authorized quarantine can remove availability/block starts; author changes cannot voluntarily break established eligibility. | PASS: CAP-14/19, constraints, protocol. |
| Sitting and debit | Server-authoritative one recoverable sitting; bounded first ACK/deadline, first-write answer, retry-safe receipt, late/missing/abandoned answers have finite terminal outcomes. Theme/final have separate rolling 23-hour cooldown from closure. 100/200 charge units, pre-spend refusals, frozen shown terms and atomic once-only start/debit. | PASS: CAP-2/4/10/12/13/20, protocol. |
| Privacy and advice | Current redacted question only; no client keys, codeAnswer, private score digits, future bank, private session or per-item verdict. Uniform non-correctness receipts, shuffled Ordering, no Survey. Server result aggregates and safe fixed-shape advice; perfect/no-evidence results cannot leak item correctness through advice shape. | PASS: CAP-5/6/20/21, protocol. |
| Ordinary learning and purchases | Local/offline EASY progression and ordinary HARD/next lesson gate remain; no new ordinary runner. Purchased access is distinct from pass/stars/provisioning. Existing Room transaction joins unlock, provisional nolics debit and outbox; retry/restart preserve identity and quarantine rolls back visibly. Exam charge remains server-owned and requires confirmed admission. | PASS: CAP-9/11, constraints, protocol and initiative AD-7. |
| Result and reward | One best mixed result, no theme average; worse retake does not lower it, authorized correction may. Admission and result commit together. First-pass reward is once per user/theme, committed with admission, not repeated or removed by correction replay; no invented formula. | PASS: CAP-7/15/17, Q-EX-1/6, protocol. |
| Initial certificate race | Successful final terminal result, immutable logical issuance fact and durable fulfilment work commit atomically. Signing occurs outside the transaction; pending is explicit. Retry binds the same issuance ID, earning result version, claims, key and dates, including when a content-error correction happens before signature publication. | PASS: CAP-8/18, constraints, protocol and amended ADR-0007. |
| Certificate lifetime and correction | Annual certificate records original earning sitting/percent/pass mark/bank; content-error downgrade does not change signed score/claims/identity/dates/original expiry/validity. Separate corrections remain reconstructable. Profile is read-only; signed-out server verification uses frozen public claims and preserved verification material. Certificate grants no gameplay qualification. | PASS: CAP-8/18/21, protocol and adopted ADR-0007. |
| Quiet boundaries | No mandatory pretest, statistics-only punishment, proctoring/accreditation, local exams, offline verification, per-theme/section/composite certificates or inferred leaderboard/course-star/renewal/improvement policy. Gold boundary only forbids direct exam admission/attempt/retake/cooldown bypass; current shared charge/premium economy remains intact. | PASS: constraints/non-goals, Q-EX-3/7, protocol. |
| Authority, operations and evidence | Owner/account epoch and frozen operation body survive recovery; late responses cannot update a changed account. Foreground code cannot substitute for the sole server terminator or durable signer. Demo adapters do not invent production service claims. VPS-only environment and fresh candidate evidence survive adoption. | PASS: constraints, Q-EX-5, protocol and initiative spine. |

## Open questions remain honest prerequisites

Q-EX-1 mixed-star display, Q-EX-2 correction arithmetic/fixtures, Q-EX-3 annual renewal identity and optional improved retakes, Q-EX-4 versioned full-EASY proof, Q-EX-5 permitted worker/signing adapters and runbooks, Q-EX-6 reward amount/formula, and Q-EX-7 optional placements/course stars are retained. These do not grant implementers independent policy choices. Each is explicitly required before its dependent integration or surface; ownership and the invariants around it are already fixed. No unresolved product question was silently turned into a policy.

The accepted history explicitly supports the less obvious retained requirements: best-only/no-average theme display, minimal lock price/padlock purchase UI, return-to-lessons advice on failure, periodic server analysis without automatic punishment, annual re-earning, and the later approved-bank/stable-theme/certificate-immutability amendments. No superseded pretest, per-difficulty cooldown ladder, old flat purchase tariff, or stale local exam/certificate authority was resurrected.

## Artifact identity

- SPEC SHA-256: `7ab0c26ff1cefd78e51a820a6138abcafc207e469833abac7f67697c53cdacb1`.
- Protocol SHA-256: `a1d269d5c28dad36b7cb5905d6d28d7bc939c9c741e71c4eec87c8f6b1c0e67e`.
- Exam spine SHA-256: `cd975c96de7911c52be952dc9b48563318ba19643ae5a1c90612067392900716`.
- Initiative spine SHA-256: `180f3952247c42408346da1ddeddfd8e77411d48e3e9fdfa5f62d7981717709e`.

Only this reviewer note was written for this pass. No sources, companions, memlogs, code or runtime state were modified. Initiative-wide Pass 2 is separate and awaits the completed initiative kernel.
