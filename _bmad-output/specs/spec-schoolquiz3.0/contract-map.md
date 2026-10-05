# Contract map

This map covers the accepted Android MVP, including the October 1 UX and subsequent owner decisions. Adopted PRD/exam details remain binding where unchanged; the precedence table below resolves older statements on the same point. Requirement IDs remain stable even when a capability is explicitly deferred. This contract is not implementation or runtime acceptance.

## Namespaces and source authority

| Reference | Meaning |
| --- | --- |
| MVP:CAP-1–26 | Initiative capabilities in SPEC.md; CAP-1–23 retain their identities, CAP-24–26 add polls, private quizzes and operation history. Unqualified CAP here means MVP. |
| PRD:FR-1–56 / NFR-1–8 | Preserved requirement IDs; FR-56 guidance is explicitly deferred from the current version. |
| EXAM:CAP-1–21 | Stable feature IDs; not renumbered as initiative capabilities. |
| AD-1–31 | Final initiative architecture, including October 1 clarifications. |
| EXAM:AD-1–37 | Final exam feature architecture; the prefix distinguishes its AD-31 from initiative AD-31. |
| SYNC:AD / MON:ADM | Only the subject contracts expressly imported by the initiative architecture. |

| Source | Authority and boundary |
| --- | --- |
| [Initiative architecture](../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md) | Ratifies the latest owner decisions and delegated architecture. Its accepted amendments supersede older PRD, UX draft or feature-spec wording on those points; unresolved integration contracts remain explicit gates. |
| Final [DESIGN](../../planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/DESIGN.md), [EXPERIENCE](../../planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/EXPERIENCE.md) and [implementation-handoff](../../planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/.working/implementation-handoff.md), updated October 1 | DESIGN owns appearance; EXPERIENCE owns accepted behavior; handoff identifies missing adapters and proof. Later explicit owner decisions govern current policies. Frozen HTML supplies visual provenance, not a competing rulebook. Detailed UX mapping is in [ux-constraints.md](ux-constraints.md). |
| [Exam architecture](../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md), [technical contracts](../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md), [exam SPEC](../spec-theme-exams/SPEC.md) and [protocol](../spec-theme-exams/exam-protocol.md) | Preserve exam composition, admission, scoring, rewards, correction, certificates and C1–C4. Later online-window, STANDARD-entry and final-UX decisions govern affected passages; an older blanket screen-content approval sentence is not a new prerequisite. |
| [PRD](../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd.md) and [addendum](../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/addendum.md) | Preserve unaffected prices, formulas, financial and safety guarantees, acceptance matrices and full scope. Their old guidance, fixed-prize, timing or UX-discovery statements do not override the explicit later decisions below. |
| AGENTS.md / PROJECT-CONTEXT / existing code and historical specs | Standing architecture/testing conventions and brownfield evidence. Old toolchain/environment seeds, historical PASS, mocked values and transitively linked scope are not current runtime proof or new requirements. |

The originating workflows own adopted files; this refresh does not edit them. SPEC.md, this map and ux-constraints.md are derived spec-authored content. A later explicit owner decision supersedes an earlier statement on the same point; delegated technical decisions do not reopen settled product choices. A new unresolved contradiction must be surfaced before dependent implementation.

User-facing competition terms are **турнир** for the first qualification part and **чемпионат** for the entire second, final bracket/match part; **соревнование** denotes both. Historical `tournament*`/`event` IDs, routes and filenames retain their technical meaning. **Приватные квизы** is the separate flat HARD-only category, not a private course competition.

## Capability coverage

| MVP capability | Preserved requirement group | Main architecture |
| --- | --- | --- |
| CAP-1 | FR-1–4 | AD-1/2/8/21/25/31 |
| CAP-2 | FR-5; accepted question editors | AD-1/3/20/27 |
| CAP-3 | FR-6–8; EXPERIENCE F9 | AD-3/4/20/26/31 |
| CAP-4 | FR-9–10 | AD-4/5/6/9/10 |
| CAP-5 | FR-11–12 | AD-1/3/13/20/21 |
| CAP-6 | FR-13–15 | AD-20/21 |
| CAP-7 | FR-16–22 | AD-5–9/11/12/21 |
| CAP-8 | FR-23–27 | AD-6–8/11/12/21 |
| CAP-9 | FR-28–30 | AD-5/9/12/13 |
| CAP-10 | FR-31–32 | AD-9/13/23 |
| CAP-11 | FR-33–36; course competition access | AD-2/3/6/9/17/20/23/31 |
| CAP-12 | FR-37–40; championship meeting/prizes | AD-2/9/17/20/23/31 |
| CAP-13 | FR-41–43 | AD-5/9/18/19/21 |
| CAP-14 | FR-44–45; ordinary/Premium gift actions | AD-5/6/9/19 |
| CAP-15 | FR-46–49 | AD-6/9/18/19/22 |
| CAP-16 | FR-50–51 | AD-5/8 |
| CAP-17 | FR-52; FR-56 retained as a separate future task | AD-20/21/24/25 |
| CAP-18 | FR-53 | AD-6–8/9/24 |
| CAP-19 | FR-54 | AD-6/8/20/24 |
| CAP-20 | FR-55 | AD-22/24 |
| CAP-21 | EXAM:CAP-1–7/9–15/17/19–21 | AD-2/3/5/6/9/14/15/23/24/31 |
| CAP-22 | EXAM:CAP-8/9/15/16/18/21 | AD-9/14–16/20/23/24/31 |
| CAP-23 | FR-6–7; EXAM:CAP-7/14/18/19/21 | AD-3/14–16/20 |
| CAP-24 | EXPERIENCE F10; standalone polls and voting | AD-1/2/6/8/20/28 |
| CAP-25 | EXPERIENCE F11; Приватные квизы | AD-1/2/6/8/20/29/31 |
| CAP-26 | EXPERIENCE F9; own operation history and author-lesson earnings | AD-8/9/20/30 |

EXAM overlap is intentional: theme, final and correction share one feature contract. New UX capabilities do not invent additional FR numbers or discard the old ones.

| Cross-cutting requirement | Binding consequence |
| --- | --- |
| NFR-1 Financial integrity | Stable identities/receipts, nonnegative balances, conserved reserves/funds, fractional author/review entitlements and grant-specific refunds. |
| NFR-2 Rights/privacy | Server-authorized views, account binding, protected forms/keys and private reports; current result visibility is enforced on read/retry. No invented offline anti-cheat guarantee. |
| NFR-3 Durability/completeness | Recover original authorized work and process full cohorts; valid proof, not local flags or empty queues; required 120-action delivery case. Recovery does not grant deferred private-quiz continuation. |
| NFR-4 Time/state | Server/UTC authority with local timezone/DST presentation; shared online next-question window AD-31 and distinct mode owners/answer clocks, plus unknown/pending/stale distinctions. |
| NFR-5 Localization/accessibility | RU/UK/EN, semantic actions, Back/rotation/background/restore and accessible recovery; verify actual Compose scaling and assistive operation. |
| NFR-6 Performance/scale | Existing input/answer criterion and full-cohort resource measurements; fixtures are not population caps or a new loading SLA. |
| NFR-7 Compatibility | Current dependency/toolchain/security gates and non-destructive migration/rollback; architectural review is not runtime proof. |
| NFR-8 Evidence | One identified candidate, preserved failure evidence, honest test statuses and final owner manual acceptance. |

## Superseded statements and current contracts

| Earlier statement or inference | Current binding interpretation |
| --- | --- |
| UX is still discovery; every screen must undergo a new blanket content-approval round | Final DESIGN/EXPERIENCE and later owner clarifications are the current design/behavior contract; former OQ-3 is retired, not reused. Do not restart accepted screen decisions or resurrect excluded mockups. Implementation, A11Y, functional states and owner candidate acceptance still require proof; fixtures do not approve business rules. Older blanket approval wording in the adopted exam SPEC/protocol is superseded on this point. |
| FR-56 guidance must ship or blocks current UI acceptance | Current version explicitly has no guide or placeholders. Texts, experience thresholds, placement, dismissal/repeat and seen-state migration belong to a separate future task before guide activation. |
| Generic metadata-only fee implies lesson title/description editing | Existing lesson titles cannot change; lessons have no description. OQ-1 is retired. A genuinely new permitted non-question edit needs explicit scope before a tariff; no zero fee is inferred. |
| TESTING scores a whole assignment, needs additional reviewers or has no agreed pass mark | AD-26: one independent reviewer per question/revision is sufficient; configurable default pass threshold 2 on scale 1–3. Bind result to applied config and assignment generation; other required checks still apply. Neither multi-reviewer quorum nor averaging different questions replaces question admission. |
| Reviewer work continues offline or a filled translation is already accepted | Review evidence saves online and resumes from confirmed progress. Each translation segment needs explicit unchecked/correct/error state and complete server validation; nonempty text is not approval. An active timed TESTING question still obeys AD-31 while evidence writes pause. |
| A rejected question cannot complete paid review, or each stage earns a separate payout | Normal cycle completion includes justified rejection for performed work; AD-10 distribution remains binding. Only adding permanent abandonment of an unfinished cycle raises conditional OQ-2/O11; an unfinished cycle is not finally distributed or burned. |
| Private-quiz defaults, link life, retrospective visibility, attempt-limit changes or timeout result remain product choices | AD-29 closes these choices: category Приватные квизы; defaults 1/hidden/no expiry; author may choose a deadline or disable early; active attempts finish within original time. Current show-result covers past results without changing stored facts. Current limit gates new starts against preserved startedCount; timeout scores the entire frozen quiz with remaining questions unanswered. Detailed accepted behavior remains in EXPERIENCE and ux-constraints. |
| Private quizzes use entry charges, charge hints/skips or leave-and-continue-later | No STANDARD/PLASMA use in an individual private quiz; no replacement fee is invented. Only technical recovery within original deadlines/AD-31 is allowed. This exception does not waive paid author submission or course-competition funding. |
| A private course competition requires an organizer whitelist; its link opens the championship | Downloaded corresponding course OR valid competition link permits admission only in server interval [qualification.startsAtMs, qualification.endsAtMs). Championship entry requires the existing qualification result; receipt retry is not a new entry, and expiry does not cancel earned championship rights. |
| Every mention of tournament means the entire event, or championship means only its last match | Use турнир → чемпионат for the two parts and соревнование for the whole. Keep technical identifiers stable. |
| Fixed first/second/two-third prize accounting remains authoritative | AD-17: dynamic ordered prize list, each row reserved/paid once; no duplicated third prize. Agreed PROPOSE/CONFIRM and each participant's READY restore from server state. Rank/tie/technical-outcome mapping and adapters remain technical gates. |
| Visual top32 or averagePercent changes qualification | Current top16 and full-cohort authoritative calculation/order remain; averagePercent is display, not a replacement for ratingPercent/place. Never substitute first501 or sample fixtures for the complete cohort. |
| Each online mode chooses a reconnect timeout or pause independently | AD-31: original answer deadline continues; a subsequent shared config window, default60s, starts at timely accepted answer or original expiry. Captured times/config survive retry; no extra answer time, reopened question, initial-ACK number or last-question delay. ONLINE-O1/O2 remain unresolved integration contracts below. Ordinary/prepared Arena offline paths retain their mode contract. |
| Exam entry can choose plasma/split or legacy final300 | Theme100/final200 STANDARD points (1/2 full charges) are accepted. Atomic start/debit, refused-start no-charge and same-open-sitting recovery remain; legacy300 is implementation drift. |
| Both assessment kinds have a 23-hour interval | Theme24h/final168h rolling from terminal closure, including failure/timeout/abandonment; no calendar reset or new debit for open-sitting recovery. |
| Theme stars or reward coefficients remain undecided, or mode coefficient multiplies the whole reward | Theme stars are linear 0..3; NEW coefficient ordinary2/theme3/final10, final already-accounted0.1, with separate difficulty/time factors. Every terminal final is reward-eligible; theme first-pass-only remains. C1 fixes ordinary worth, mixed base and independent exact currency rounding; reward-accounted maximum is separate from latest certificate and survives lower results/renewal. |
| All-HARD assessment timer changes EASY reward worth or keeps examFactor0.75 | Every assessment question uses ordinary HARD answer time without extra0.75; difficulty and 5-EASY/15-HARD or20-HARD composition remain. Reward worth keeps ordinary EASY/HARD values under C1. |
| Mixed reward arithmetic, full EASY evidence or correction economics remain product decisions | C1–C4 are adopted. C3 requires confirmed exact-revision EASY evidence; C2 preserves already settled payments and accounted history after correction. Concrete signing/environment/operations activation remains Q-EX-5. |
| Correcting a failed final to passing earns a retrospective certificate | Preserve corrected result/admission; only a new successful final earns a certificate. Original-success issuance, including signing pending, survives later correction. |
| Renewal before expiry is blocked or the best certificate remains displayed | Each newly passed final may renew; show the latest earned issuance even with a lower passing score. Old attestations/one-year terms remain immutable; failed retakes and delayed signing do not displace the latest earned entitlement. Theme best-score rules remain. |
| Exams/certificates are excluded, a pretest is mandatory, or QuestCompleted/profile issues a certificate | The MVP includes mixed theme tests, separate final and certificates. Approved concrete revisions need no mandatory pretest. Exam owns issue/sign/verify; successful final and immutable issuance commit together, profile reads the projection. |
| A changed course invalidates all passed themes; correction may temporarily authorize stale admission | Passed stable course/theme IDs carry, new required themes need a pass, latest approved course governs new finals. Correction publishes admission atomically or establishes a generation/pending barrier checked before final-start charge; stale materializers cannot restore obsolete proof. |
| No local lesson spending is allowed | Ordinary unlock + provisional debit + durable intent remains accepted; it is not device-side exam charging or confirmed EASY coverage. |
| Timestamp-only cursors or sent/terminal Arena markers prove delivery | AD-8/SYNC AD-31 use persistent lexicographic (changedAtMs, docId) across all four journals. Main AD-7/12 require exact Arena proof/reconciliation and retained uncertain intents. |
| MON five-ad boxes/global two-box cap or a single automatic gift action | Independent sources and server reserve/settlement remain. Ordinary gift after10 consecutive days is automatic without Premium; Premium gift requires its explicit claim action without an ad view. One source/retry cannot consume or duplicate the other; box opening remains online with one stored reward. |
| Result next/recommendation/unread drafts or fixture ledger imply product behavior | Accepted semantics in ux-constraints and EXPERIENCE govern: next lesson by order, conditional assessment surface and total comments with two direct feedback actions. Own history and author-lesson earnings read permitted authoritative ledger projections; pending is not credited and resources are not summed as one balance. |
| PROJECT-CONTEXT seeds, nested any-auth rules or broad historic modules are current authority | Inspect candidate seeds; main AD-20/NFR-2 enforce current private scopes. Adopt only standing conventions and expressly imported subject contracts, not obsolete migrations, prices, operational approval or the entire historical monetisation catalogue. |

All unaffected PRD/addendum financial terms, acceptance cases and failure matrices remain mandatory. UI naming and later feature additions do not relax paid review tariffs, no-refund policy, fractional payouts, Play grant/refund provenance, financial conservation, account isolation or non-destructive migration. The detailed exam reward/certificate/correction contract stays in its adopted companions.

## Remaining gates before dependent implementation

SPEC question IDs distinguish the remaining work: OQ-2 is conditional unfinished-cycle settlement; OQ-4 retains EXAM activation Q-EX-5 and optional Q-EX-7; OQ-5 covers operations/release; OQ-6 binds ONLINE-O1/O2; OQ-7 groups the named UX API/schema/receipt/admission/projection adapters; OQ-8 concerns the separate future FR-56 task only. Retired OQ-1 and OQ-3 are not reused, and closed UX-O1/O4/O5 product decisions are not new questions.

| Gate | Required result and boundary |
| --- | --- |
| ONLINE-O1 | Reconcile the full subsequent online window with existing qualification/session and championship match/stage cutoffs before enabling the affected competition adapter. Neither truncating the promised window nor extending global deadlines is silently chosen; other modes do not wait for that adapter. |
| ONLINE-O2 | One shared delivery/ready-ACK contract before independent online next-question adapters: exact authoritative completion event/receipt, identity/retry and relation to answer-clock start, with no unbounded gap or replacement clock after lost ACK. EXAM AD-3 is existing evidence, not an automatic overwrite of other lifecycles. |
| UX-O1/O4/O5 | Product choices are closed. Implement STANDARD exam debit/config reconciliation; private start/settings/link proof, preserved attempt count, current visibility fencing and full-quiz timeout result; question-level review threshold/evidence/settlement. Code and permission/concurrency recovery proof remain required. |
| UX-O2/O3 | One versioned prize-list/rank/tie/technical-outcome contract before CREATE/reserve/payout, plus permitted proposal/confirmation/READY projections and restoration before real championship actions. |
| UX-O6 | Shared protected-range/FillBlank schema, units, identity, edits/undo/revision/migration and translation/redaction contract before independently built editor/adapters. |
| UX-O7 | Vote/series/revision identity, respondent-question uniqueness, delivery/recovery and one counts snapshot before real vote writes. Retry is not a second vote; editing before submission does not grant post-vote revision. |
| UX-O8/O10/O12 | Authorized inbox/Play/exam and own-ledger projections, bounded order/cursors, separate gift-source claims and exact authoritative receipts before corresponding real actions. |
| Conditional OQ-2 / UX-O11 | Only before adding permanent unfinished-review abandonment or a genuinely permitted non-question edit: choose that new outcome/scope. Existing title immutability, no description, normal rejection payment and no fee refund remain settled. |
| Deferred O9 / FR-56; optional EXAM-Q7 | Guide is a separate future task, not a current-release blocker. Theme placement/course stars remain optional until explicitly selected; accepted theme-result stars are not reopened. |
| Q-EX-5 / OPS / RELEASE | Concrete signing/provider/key/environment bindings, cadence/budgets/retention/recovery proof, current Play/device inventory, compatibility/security gates and the known quiescence preflight fix before the authorized runtime/release stage. C1–C4 are already adopted contracts. |
| A11Y and full acceptance | Implement the final UX and verify actual Compose font scaling/assistive interaction, required recovery/state paths, financial/account/migration cases and all applicable acceptance matrices on one identified candidate. No historical PASS or mock establishes readiness; owner manual acceptance remains required. |

This refresh neither starts builds/services/financial scenarios nor publishes/deploys anything. It does not recreate superseded implementation plans or authorize main-project transfer/public release. Stories remain a separate requested breakdown; no stories.yaml is created by this map.
