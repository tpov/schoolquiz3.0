# Independent Pass 1 — coherence review

Date: 2026-09-30. Verdict: **PASS** for Spec Law 1–6 and 8. No mandatory correction found. Preservation Law 7 is reserved for the separate independent pass.

Reviewed read-only: SPEC.md (156 lines), contract-map.md (89), ux-constraints.md (59), directly referenced PRD FR-35/36 and exam protocol timer boundaries; verified local companion/reference targets exist. No code edits, builds, runtime, installs, HTML rendering, upstream edits or operational actions. Only this report was written.

## Reviewed identity

- SPEC.md SHA-256: a59409f37c045bf63301dc078ba582697a9f140cfb71f781ae13c90a757bfe70
- contract-map.md SHA-256: 98943d800f40d1b435994ed061e59e59b529379046edcbb993d33271053c217d
- ux-constraints.md SHA-256: a50805ef26a0d25e46a4bfed8cac98cad54ce02a351f5a7ff146f676cf26c925

## Spec Law checks

| Law | Result and evidence |
| --- | --- |
| 1 — intent and success for every capability | PASS. Exactly 23 distinct CAP-1–23 blocks, each with one intent and one success. Success clauses describe observable outcomes; detailed acceptance is explicitly mandatory through adopted PRD/exam content. |
| 2 — intent WHAT, not implementation HOW | PASS. Intents state learner/author/reviewer/player/buyer/maintainer outcomes. Protocol mechanisms and formulas live in success/constraints or adopted companions, not implementation recipes in intents. |
| 3 — constraints bend decisions | PASS. Boundaries constrain lifecycle ownership, offline access vs online exam charge, revision/proof/privacy, timing, migration, evidence and owner content confirmation. They resolve likely competing implementations. |
| 4 — explicit non-goals | PASS. Concrete exclusions cover platforms/release, arbitrary DMs/editing, superseded boxes, unapproved AI/translation, offline exams, shared mode lifecycle, wrong certificate triggers and invented commercial policy. |
| 5 — concrete test/demo success | PASS. One identified full Android candidate, actual required end-to-end journeys, nonempty installation, financial proof, 120-action case, whole-cohort cases, device/Play evidence and owner manual result. Mandatory NOT RUN cannot be called complete. |
| 6 — stable unique IDs | PASS. Sequential unique MVP CAP-1–23; qualified EXAM CAP-1–21 retain a separate namespace. FR/NFR/AD IDs remain intact. UX-1–14 are separate constraints, not replacement capabilities. |
| 8 — lean actionable prose | PASS. Kernel and two companions are concise for the initiative. Tables map coverage/precedence without repeating full formulas. Historical documents remain adopted content within stated scope; history/process is not assigned as extra user-facing product functionality. |

The kernel contains all five required fields (Why, Capabilities, Constraints, Non-goals, Success signal) plus explicit Open questions. OQ-1/2 are gated paid-submission decisions; OQ-3 is owner-confirmed UX work; OQ-4 points to the single exam feature contract; OQ-5 preserves unperformed operational/compatibility prerequisites. None is silently resolved by fixture data or a claimed runtime PASS.

## Links, scope and UX

- All 10 frontmatter companion targets and all 9 approved-visual reference targets exist in the VPS candidate. This verifies path resolution only, not rendering or renewed visual approval.
- Every FR-1–56 is mapped; all eight NFRs have a cross-cutting entry. Intentional EXAM overlap is stated rather than creating competing feature owners.
- PROJECT-CONTEXT adoption is explicitly scoped to standing architecture/testing conventions. Timestamp cursor, package/stack/environment seeds and nested any-auth note cannot override current authoritative contracts or authorize operations.
- Old feature SPECs are not blanket-adopted. Current scopes, immutable IDs and resolved overrides are explicit; no indirect market/referral/old-price expansion is introduced.
- Top32 is closed: MVP CAP-11 and UX-11 retain top16 per PRD FR-36. HTML literal Top32 has no product authority. averagePercent does not determine order or qualification.
- UX-13 is only the targeted green Home-style World CTA. It does not approve the whole World screen, invent progress or reopen other visual choices.
- UX-14, the bold companion opening, kernel constraint, OQ-3 and contract map consistently require separate explicit content confirmation for every screen, even visually approved ones. Existing explicit product decisions remain constraints; visuals/fixtures do not waive rights, paid admission or pending/recovery.
- Cutoff remains world-r2 plus the direct September 30 content-confirmation instruction; no further upstream UX changes were chased.

## Nonblocking wording observation

SPEC constraints phrase “continuous tournament/exam timing” can be made more precise as “continuous tournament clocks and mode-specific exam deadlines/gap budgets”. The adopted exam protocol lines58–60 already states first-ACK deadline, separate waiting gap and no pause extension, and the kernel separately forbids lifecycle unification; therefore this is not an active contradiction or a required change for PASS.
