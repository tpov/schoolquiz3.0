# Epic 6 Context: Theme tests, final exams and certificates

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Learners who finished ordinary course work prove knowledge under the adopted exam contract: a mixed 20-question theme test per course theme, gated by server-confirmed required-EASY coverage, and a separate 20-HARD course final whose success issues a distinct one-year certificate verifiable by anyone, signed out. Confirmed content faults are corrected accountably: settled rewards survive, denominators are recomputed safely, and a corrected failure never becomes a certificate. This closes the owner journey from exam sitting to public certificate; EXAM-TECH-1 fixtures are the shared proof across all stories.

## Stories

Not yet ticketed — story numbering is created at inception. Candidate groupings from the epic's done-when/coverage:

- Inception reconciliation: audit the existing August-spec exam implementation against October decisions (24h/168h cooldowns, 100/200 STANDARD entry, timer) before building
- Theme test: confirmed-coverage admission, sitting, timer, linear 0..3 stars, 24h cooldown, 100-point entry
- Final exam and certificates: 20-HARD sitting, atomic one-year issuance, signed-out verification, latest-earned display, 168h cooldown, 200-point entry
- Accountable corrections: append-only versions, safe denominator recalculation, preserved rewards, correction/admission race barrier
- EXAM-TECH-1 fixture parity as the shared proof
- Signing/environment activation (OQ-4/Q-EX-5) resolved with the owner at this epic's gate

## Requirements & Constraints

- Admission: server-confirmed coverage of every required EASY question in every theme lesson, from versioned per-question revision evidence unioned across ordinary attempts. Purchases, upload acknowledgements and rounded stars prove nothing; ordinary local progression stays immediate; pending results flush before admission.
- Composition: theme 5 EASY + 15 HARD; final 20 HARD from the latest approved course; distinct approved available revisions bind to the sitting; pass mark server-configured, default 60%. Final admission needs every required non-archived theme passed by stable course/theme identity; insufficient pools refuse before charging.
- Entry/cooldown: theme 100 / final 200 STANDARD points (one/two full charges); start and debit commit atomically and idempotently; PLASMA cannot pay; legacy final-300 config is drift to reconcile before real debit. Rolling per-user cooldowns from terminal closure (including timeout/abandonment): 24h theme, 168h final; recovering an open sitting costs no attempt, charge or cooldown; refusals are typed (locked, results-not-synced, cooldown, insufficient charge, pool too small).
- Timer: every question, including the theme's five EASY positions, uses ordinary HARD answer time max(5, roundHalfUp(chars*0.24)), image counted as 100 chars — never the 0.75 exam factor; first acknowledgement fixes the server deadline; client countdowns are display-only.
- Theme stars: linear percent→0..3 at tenths precision with half-up rounding, server-computed; pass/fail uses raw percent, never rounded stars.
- Rewards: theme x3 new-progress coefficient, first-pass-only per user/theme; final x10 new / x0.1 already-accounted, every terminal attempt eligible including failure, no minimum at zero. A maximum reward-accounted percentage per user/assessment is tracked separately from best theme display and the latest certificate and never resets on lower scores, renewal or zero-rounding. Worth keeps ordinary EASY/HARD values independent of the all-HARD timer; XP and nolics round independently half-up from the same exact unrounded amount.
- Certificates: only a successful terminal final authorizes issuance, atomically with terminal success, as an immutable one-year attestation (sitting, percent, pass mark, bank snapshot, own dates). The profile shows the latest earned certificate even at a lower passing score; a failed retake issues and replaces nothing; renewal before expiry follows the same rules; public verification reads a frozen snapshot, signed out.
- Corrections: only authorized confirmed-fault exclusions; original sitting evidence preserved; recompute from original slots plus cumulative exclusions via the shared scorer (nested truncation, captured pass mark); all-excluded and missing-evidence are explicit nonnumeric states, never fabricated fail/pass/zero. Settled rewards and accounted history stay untouched — no top-up, recoupment or maximum advance; a failed→passing correction issues no certificate without a new sitting.
- Privacy: no keys, correct answers, per-item verdicts, private score digits or codeAnswer in client projections, logs or diagnostics; advice must not identify a failed item.
- EXAM-TECH-1 fixtures (reward worth vs timer, independent currency rounding, accounted progress surviving lower scores/zero awards, correction/admission race barrier) must pass as the epic's shared acceptance proof.

## Technical Decisions

- Ownership: publication owns approved revisions/manifests; learning owns confirmed progress; exam owns sittings, admission, terminal outcomes and certificate issue/sign/verify; economy owns financial effects; profile consumes a read-only certificate projection. Exam is its own lifecycle, sharing with other modes only identity, redaction, idempotency and scoring arithmetic (Kotlin/server scorers match the same golden cases).
- Sitting: at most one open sitting per user/assessment; starting an open one recovers it without a second debit. Operations carry owner, account epoch, operationId and immutable bodies, so retries, lost responses and account switches preserve one sitting and one financial effect; first committed answer per question wins and identical retries return the same receipt.
- Termination/signing: a bounded server worker is the sole logical terminal writer (CAS/fencing, durable sweep progress); foreground calls only advance or read. Worker capability is separate from its wake-up adapter (production: existing Firebase Functions v2 schedule; demo: controlled driver of the same capability). Signing runs outside the transaction against frozen claims/key; fulfilment retries target the same issuance fact and CAS-publish; key rotation publishes verification material before new earnings use the new key.
- Next-question window: after a question closes (timely answer or deadline expiry) a shared server-configured 60-second connection/download window starts, frozen in the sitting; reconnect/retry never restarts it and it never alters answer time; none follows the last question. The shared ONLINE-O2 delivery/ready-ACK contract binds online-transition implementation.
- Starts atomically recheck coverage generation, bank eligibility and current correction generations inside the start/charge transaction. Final certification eligibility needs at least 12 theme tests, each with ≥100 EASY and ≥100 HARD approved available questions (publication-maintained sets, never republication increments).
- Correction/admission race barrier: a correction head publishes atomically with recomputed admission/best, or marks a durable pending generation blocking new final starts before charge; a shared theme aggregate generation covers every input-changing write so a stale materializer cannot overwrite a newer valid pass.
- Redaction: separate wire/domain types (never nullable correctness fields) — omit correct options, server-shuffle Ordering with the permutation private, omit FillBlank mappings, exclude Survey.
- Brownfield: the existing August-spec exam implementation must be reconciled against October decisions at inception; planning authorizes no runtime, service or signing activation by itself.

## UX & Interaction Patterns

- Results render server values verbatim (percent, stars, pass state, advice) with no client recalculation; advice is fixed-length even at perfect scores and never reveals a failed item; a failed theme routes back to lessons.
- Answer receipts carry no correctness-dependent colour, mark or duration.
- Availability UI distinguishes locked (unpassed content) from bought-but-unpassed access, and unavailable/pending/unknown from an elapsed cooldown; server/UTC owns deadlines, local timezone for presentation.
- Profile shows the latest earned certificate with an explicit pending-signing state; public verification works signed out.
- Screens keep RU/UK/EN, semantic Continue/Info/Back, rotation/background/recovery and typed recovery states. Every assessment, result and certificate screen's content needs separate explicit owner confirmation — approved appearance does not approve copy, data, actions or states.

## Cross-Story Dependencies

- Waits on epic 2 (ordinary delivery): confirmed EASY coverage evidence plus the shared online transition contract ONLINE-O2 (owner-accepted 2026-10-08, proven by a real transition 2026-10-09/10).
- Waits on epic 4 (authoring/review): banks are drawn from published approved questions; epic 4 has open HITL tails, so bank availability must be confirmed at inception.
- HITL gate inside the epic: OQ-4/Q-EX-5 (signing provider/algorithm, secret bindings, environment, cadence/budgets/retention) must be resolved with the owner before signing activation; the worker/signing contract itself is accepted.
- Within the epic: EXAM-TECH-1 fixtures are shared proof across theme, final, correction and recovery stories; the correction-generation barrier couples corrections with final admission; entry-charge arithmetic is shared by theme and final stories.
- Out of scope: ordinary lesson learning (epic 2), content-quality machinery beyond exam consequences (epic 4), the store (commerce epic); the UX-Noir epic restyles these journeys later.
