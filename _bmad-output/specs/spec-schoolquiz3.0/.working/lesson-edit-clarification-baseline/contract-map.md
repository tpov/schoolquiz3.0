# Contract map

This initiative kernel groups the complete accepted MVP. PRD acceptance details, numbers, formulas, failure matrices and the exam feature contract remain mandatory adopted content, not optional background. The map adds no independent business rules or implementation status.

## Namespaces and owners

| Reference | Meaning |
| --- | --- |
| MVP:CAP-1–23 | Stable initiative capabilities in the sibling SPEC.md. Unqualified CAP here means MVP. |
| PRD:FR-1–56 / NFR-1–8 | Preserved canonical requirement IDs; grouping does not retire them. |
| EXAM:CAP-1–21 | Stable feature IDs in the adopted exam SPEC; they are not renumbered as initiative CAPs. |
| AD-1–24 | Final full-MVP architecture decisions. |
| EXAM:AD-1–37 | Final exam feature architecture decisions. |
| SYNC:AD / MON:ADM | Only the subject contracts explicitly imported and qualified by the full-MVP architecture. |

The originating workflows own adopted PRD, architecture and project instructions; this spec does not edit or clone them. SPEC.md, this map and ux-constraints.md are derived spec-authored content. Detailed exam behavior belongs to its own stable feature spec/protocol.

## Capability coverage

| MVP capability | Preserved requirement group | Main architecture |
| --- | --- | --- |
| CAP-1 | FR-1–4 | AD-1/2/8/21 |
| CAP-2 | FR-5 | AD-1/3/20 |
| CAP-3 | FR-6–8 | AD-3/4/20 |
| CAP-4 | FR-9–10 | AD-4/5/6/9/10 |
| CAP-5 | FR-11–12 | AD-1/3/13/20 |
| CAP-6 | FR-13–15 | AD-20/21 |
| CAP-7 | FR-16–22 | AD-5–9/11/12/21 |
| CAP-8 | FR-23–27 | AD-6–8/11/12/21 |
| CAP-9 | FR-28–30 | AD-5/9/12/13 |
| CAP-10 | FR-31–32 | AD-9/13/23 |
| CAP-11 | FR-33–36 | AD-2/3/6/9/17/20/23 |
| CAP-12 | FR-37–40 | AD-2/9/17/20/23 |
| CAP-13 | FR-41–43 | AD-5/9/18/19/21 |
| CAP-14 | FR-44–45 | AD-5/6/9/19 |
| CAP-15 | FR-46–49 | AD-6/9/18/19/22 |
| CAP-16 | FR-50–51 | AD-5/8 |
| CAP-17 | FR-52/56 | AD-20/21 |
| CAP-18 | FR-53 | AD-6–8/9/24 |
| CAP-19 | FR-54 | AD-6/8/20/24 |
| CAP-20 | FR-55 | AD-22/24 |
| CAP-21 | EXAM:CAP-1–7/9–15/17/19–21 | AD-2/3/5/6/9/14/15/23/24 |
| CAP-22 | EXAM:CAP-8/9/16/18/21 | AD-14–16/20/23/24 |
| CAP-23 | FR-6–7; EXAM:CAP-7/14/18/19/21 | AD-3/14–16/20 |

Overlap in EXAM coverage is intentional: theme, final and correction use the same feature contract, not three competing implementations.

| Cross-cutting requirement | Binding consequence |
| --- | --- |
| NFR-1 Financial integrity | Stable identities/receipts, nonnegative balances, conserved reserves/funds, fractional author/review entitlements, grant-specific refunds. |
| NFR-2 Rights/privacy | Server-authorized views, account binding, protected forms/keys and private reports; no invented offline anti-cheat guarantee. |
| NFR-3 Durability/completeness | Resume original work and full cohorts; valid proof, not local flags or empty queues; required 120-action delivery case. |
| NFR-4 Time/state | Server/UTC authoritative time with local timezone/DST presentation; preserve each mode's pause/timer semantics and unknown/pending/stale distinctions. |
| NFR-5 Localization/accessibility | RU/UK/EN and semantic actions, Back/rotation/background/restore; recovery remains accessible. |
| NFR-6 Performance/scale | Existing input/answer criterion and full-cohort resource measurements; fixture sizes are not population caps or a new loading SLA. |
| NFR-7 Compatibility | Current dependency/toolchain/security gates and non-destructive migration/rollback; old audit or architectural review is not runtime proof. |
| NFR-8 Evidence | One identified candidate, preserved failed evidence, honest test statuses and owner manual acceptance. |

## Source precedence and resolved boundaries

A later explicit owner decision supersedes the earlier statement on the same point. The finalized architecture resolves delegated technical choices; a PRD sentence saying “architect to decide” does not reopen AD-4 assignment, AD-5 configuration or AD-10 exact review distribution. Unknown policy remains an explicit question.

| Earlier text or misleading inference | Current binding interpretation |
| --- | --- |
| Full exams/certificates excluded; two-rung tests or mandatory pretest | September 29 scope amendment and current EXAM spec: mixed theme test, separate final, approved concrete revisions without mandatory pretest. |
| Generic section/QuestCompleted certificate; profile as issuer | Exam owns issue/sign/verify, profile reads a projection; initial terminal success and immutable issuance fact commit together. |
| Old proof of a changed course invalidates all passed themes | Stable passed course/theme identities carry; new required themes need a pass, latest approved course governs a new final. |
| Reviewer actions continue offline | Review pauses on loss and resumes confirmed server progress; ordinary and prepared Arena offline paths remain. |
| Blanket ban on local lesson spending | Accepted ordinary unlock + provisional debit + durable intent remains; it is not device-side exam charging or confirmed admission. |
| Timestamp-only cursor, including old PROJECT-CONTEXT shorthand | Main AD-8/SYNC AD-31: persistent lexicographic (changedAtMs, docId) across all four journals. |
| Arena intent may disappear on sent/terminal/expiry | Current exact proof/reconciliation and retained-intent rules in main AD-7/12 apply. |
| MON five-ad box or global two-box cap; Arena has no reserve | Current PRD independent reward sources, limits and server reserve/settlement govern. The old broad monetisation catalogue is not imported. |
| Old result recommendation/new-comment/next-nowhere drafts | Adopted result semantics in ux-constraints.md: next by lesson order, conditional exam surface, total comments; preserve two direct feedback actions. |
| Every UI mock value is an approved business rule | Visual references preserve appearance only; authoritative product/event rules determine numbers and eligibility. Top32 mock does not replace current MVP top16. |
| Approved appearance means approved screen content | Owner's September 30 instruction: every screen's content needs separate explicit confirmation against overall product logic; see UX-14. |
| PROJECT-CONTEXT package/stack/environment seed or historical nested any-auth note is current operational/security authority | Inspect the active candidate for seeds; PRD/NFR-2 and main AD-20 enforce current scope and private-data permissions. The context is adopted for standing architecture/testing conventions, not blanket access or permission to operate. |

Older quest/result/sync/commerce/charge specs are not adopted wholesale. Their still-binding portions are retained through the qualified architecture, current PRD/addendum and the specific UX semantics above; historical modules, migrations, scope, approvals or price seeds do not become new work by transitively opening an old file.

## Before implementation and acceptance

- Resolve OQ-1 before metadata-only paid submission and conditional OQ-2 only before adding permanent partial review-cycle settlement.
- Review the content of every screen separately; complete remaining visual and guidance choices before the affected UI is accepted. Existing functional rules constrain this review.
- Resolve each Q-EX prerequisite once with its feature owner before dependent result, correction, renewal, reward, coverage or operation work. Optional placement/course stars remain optional until explicitly selected.
- Inspect existing code and retain known unfinished work, including the quiescence preflight defect; do not recreate the superseded historical E2 extraction plan.
- Execute the adopted full acceptance matrix only in the later authorized implementation/validation stage. Play/device availability, current toolchain compatibility and fresh runtime evidence are not claimed here.
- Stories are a separate conversational breakdown; no stories.yaml is produced or implicitly approved by this kernel.

Sources are not placed in audit-only frontmatter merely to shorten this contract: the adopted PRD/addendum still carry detailed downstream requirements. Upstream process prose, inventories and workflow history are not extra product scope; their only retained effect is the explicit evidence and operational boundary.
