---
id: SPEC-schoolquiz3.0
created: '2026-09-29'
updated: '2026-09-30'
companions:
  - contract-map.md
  - ux-constraints.md
  - ../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd.md
  - ../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/addendum.md
  - ../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md
  - ../spec-theme-exams/SPEC.md
  - ../spec-theme-exams/exam-protocol.md
  - ../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md
  - ../../../AGENTS.md
  - ../../../.claude/PROJECT-CONTEXT.md
sources: []
---

# SchoolQuiz — full Android MVP

## Why

Deliver one complete, reviewable Android MVP in which learners can study, test their knowledge and earn a course certificate; authors can publish independently checked material; and learning, Arena, tournaments and purchases form reliable end-to-end journeys. “Remaining” means missing implementation or missing current acceptance, not a request to recreate existing work.

## Capabilities

- **CAP-1 — Learn through ordinary courses**
  - **intent:** A learner can navigate a course and complete an ordinary lesson using available content.
  - **success:** A ready saved question appears without an added network gate; supported offline progress, timers, Continue, Back and restart retain the ordinary lesson outcome.

- **CAP-2 — Author and revise lessons**
  - **intent:** An author can create and correct their own learning material.
  - **success:** Authorship, submitted revisions and change history remain attributable; edits follow the permitted workflow and cannot silently overwrite another author's published material.

- **CAP-3 — Review and publish approved questions**
  - **intent:** Independent reviewers can assess submitted content, and authors can see each question's review outcome.
  - **success:** All required directions examine the concrete revision; successful questions can publish independently, affected dependencies are rechecked, unaffected valid evidence is retained, and online review pauses and resumes from confirmed progress.

- **CAP-4 — Pay for a review cycle and receive earned review rewards**
  - **intent:** Authors can submit material at an agreed fee and reviewers can receive the defined reward for completed work.
  - **success:** The accepted tariff is shown and charged once; retry and retained prior evidence create no extra paid work. A completed cycle conserves fee = reward entitlements + burned remainder under the adopted exact-arithmetic contract; an unfinished cycle is not finally settled.

- **CAP-5 — Curate placement with visible reasons**
  - **intent:** A curator can promote or remove a particular lesson while permitted users see its status and history.
  - **success:** Each transition has an authorized decision and reason; ratings do not replace curator approval, a failed replacement keeps the valid prior revision, and confirmed faults prevent new use without rewriting frozen attempts.

- **CAP-6 — Discuss lessons and report errors privately**
  - **intent:** A learner can open comments or privately report a lesson error, and recipients can find relevant incoming items.
  - **success:** Both post-lesson actions exist; private free text concerns the whole lesson without re-revealing questions or answers, and the shared inbox preserves each item's original readers and lesson link.

- **CAP-7 — Choose and prepare Arena participation**
  - **intent:** A signed-in player can browse Arena, understand available resources and reserves, and obtain the selected preparation.
  - **success:** RANDOM/SELECTED, difficulty, price consent, quotas and frozen ticket terms follow the adopted contract; the global wallet distinguishes available, reserved and freshness, and browsing has no financial effect. Response loss resumes the same preparation and unresolved or damaged state still exposes recovery.

- **CAP-8 — Complete or cancel an Arena attempt durably**
  - **intent:** A player can play an authorized Arena attempt, resume it after interruption and obtain its final outcome.
  - **success:** The prepared offline path, saved answer and restart continue the same attempt; cancel/settle races have one final effect and retain unresolved intent, unknown HARD scores are not zero, and strict proof for that exact attempt confirms the result, never a local flag or vanished queue entry.

- **CAP-9 — Rate completed lessons and receive the correct author share**
  - **intent:** A player can replace a lesson rating from a prior confirmed result, while prices and author earnings reflect eligible activity.
  - **success:** One current rating per entitled player participates in the full eligible price/rating calculation; new issued terms respect the current quote, frozen terms remain unchanged, and author shares retain fractional value.

- **CAP-10 — Award promotions and the Arena season**
  - **intent:** Eligible authors can receive placement awards and the Arena's quarterly winner can receive the season award.
  - **success:** Each qualifying transition pays once; the UTC quarter uses the complete eligible input, adopted minimum/tie/no-winner rules and one resumable final award.

- **CAP-11 — Create funded tournaments and qualify participants**
  - **intent:** An organizer can create an event from eligible material and players can complete its qualification.
  - **success:** The course remains in the library, the event has sufficient frozen content and its full fund, retries reuse the event, and three distinct EASY attempts feed an immutable top16 calculated from the whole eligible cohort.

- **CAP-12 — Play tournament matches and settle the event**
  - **intent:** Qualified players can agree a match time, play their match and see the final bracket and awards.
  - **success:** Both opponents receive the same HARD form and continuous server timing; reconnection, absences, ties, byes and technical outcomes follow the adopted matrix, and payouts plus returned remainder conserve the fund.

- **CAP-13 — Compare offers and use premium benefits**
  - **intent:** A player can inspect the current store offers, compare premium durations and use earned benefits for their valid term.
  - **success:** The gold premium surface shows full prices alongside normalized comparison; active grants determine expiry and both doubled regeneration effects, including holds, overlaps and independent remaining time.

- **CAP-14 — Receive and open boxes**
  - **intent:** A player can collect boxes from entitled sources and open one for its reward.
  - **success:** Ordinary daily and premium daily remain distinct sources, other awards do not consume their limits, and an online opening survives loss/restart by returning the same stored reward.

- **CAP-15 — Purchase, restore and refund the correct grant**
  - **intent:** A buyer can receive verified goods, recover previously paid purchases and have refunds applied to the relevant purchase.
  - **success:** Pending or unverified purchases grant nothing; account/product/quantity provenance prevents duplicate or foreign grants, partial refunds affect only the original grant, and restore/refund recovery remains available when new sales are disabled.

- **CAP-16 — Use consistent current product terms**
  - **intent:** Users can see the agreed product constants and recover a current consistent configuration.
  - **success:** The permitted configuration updates coherently; failed refresh preserves known values without inventing zero, changed payable terms require fresh consent, and accepted operations keep their captured rules.

- **CAP-17 — Use the complete redesigned experience and experience-based guidance**
  - **intent:** Users can complete every MVP journey in the agreed design and receive guidance appropriate to their experience.
  - **success:** Existing and new screens, including exams and certificates, cover actionable loading, empty, failure, offline, insufficient funds, forbidden, pending and recovery states in RU/UK/EN; approved visual constraints and semantic actions are retained, while unresolved layouts and guidance thresholds remain explicit UX prerequisites.

- **CAP-18 — Deliver saved actions to an authoritative outcome**
  - **intent:** A user can recover uncertain actions after interruption without repeatedly recreating them.
  - **success:** The required 120-action case drains without repeated manual sync; stable original operations reach the authoritative result and neither cancellation nor malformed responses fabricate success, erase pending evidence or duplicate effects.

- **CAP-19 — Keep accounts, sessions and devices isolated**
  - **intent:** Users can switch accounts or devices without adopting another session's actions or private data.
  - **success:** A→B→A, a new session for the same UID, late responses and stale pages preserve ownership; one shared ticket yields at most one settlement while distinct tickets retain their own legal outcomes.

- **CAP-20 — Update an existing installation without losing user state**
  - **intent:** A user can install the new candidate over a populated installation and retain their data and unfinished work.
  - **success:** Content, history, authentication, installation identity and pending operations survive; restore does not substitute an empty environment for missing input or destroy the original export, and rollback preserves confirmed financial history.

- **CAP-21 — Prove knowledge of a course theme**
  - **intent:** A learner can take a theme test after completing its required ordinary learning and preserve earned theme progress.
  - **success:** Confirmed coverage of all required EASY questions admits a 20-scored-question test of 5 EASY plus 15 HARD; ordinary access purchase grants no pass. Bank, charge, timing, retry and cooldown follow EXAM. Passes carry for stable course/theme identities after edits, newly required themes must pass, and a first-pass reward cannot be duplicated. Theme stars follow the accepted linear 0..3 percent scale; both first-pass reward currencies carry the accepted additional x3 multiplier over ordinary rewards, with the remaining payout formula scoped in EXAM Q-EX-6.

- **CAP-22 — Earn and verify a course certificate**
  - **intent:** A learner who passes the required themes can demonstrate current course knowledge in a final exam and receive a verifiable certificate.
  - **success:** A new final uses the latest approved course and 20 HARD questions. Every successful final earns a distinct one-year attestation, including renewal before expiry; the profile displays the latest earned certificate even if its passing score is lower, while prior certificates remain unchanged in history. Failed finals issue nothing. Public verification works signed out; fulfilment retries preserve the same issuance and original claims and expiry survive content-fault correction. Final exams also award nolics and XP with the accepted additional x10 multiplier over ordinary rewards; the base and payout eligibility/repeat contract remain EXAM Q-EX-6.

- **CAP-23 — Correct confirmed question faults with accountable result history**
  - **intent:** Authorized maintainers can remove a confirmed bad question revision from future use and apply justified result corrections.
  - **success:** Signals request review rather than automatically proving error; original sitting evidence and frozen active banks remain, corrections append attributable versions and may correct admission, and issued certificates are preserved exactly on this ground.

## Constraints

- The scope is every PRD FR-1–56 and NFR-1–8 plus EXAM:CAP-1–21. The listed companions, including their detailed numbers, formulas, acceptance cases and failure matrices, are mandatory; a short capability summary does not narrow them. Qualified IDs and the scope/precedence rules are in contract-map.md.
- The finalized architecture fixes ownership and boundaries. Keep the existing KMP/Clean Architecture, Decompose/Compose/Koin conventions and separate ordinary, Arena, exam and tournament lifecycles; shared contracts do not introduce a common mode controller.
- Independent checks approve a concrete immutable content revision. Preserve valid unaffected evidence and frozen issued banks; review is online with confirmed saves and pauses on connection loss, while approved ordinary and Arena offline paths remain available. No mandatory pretest or statistical admission gate is added.
- Only confirmed server facts authorize authoritative results, grants and receipts. Preserve original operation/account identity and uncertain outcomes across retries; financial effects conserve funds, reserves and fractional entitlements. Read-only browsing and wallet inspection create no financial effect.
- Ordinary offline lesson unlock retains its provisional local debit, durable intent and visible rollback contract. It grants access, not completed EASY coverage; online exam admission and atomic start/charge require authoritative prerequisites.
- Apply server-enforced rights and mode-specific redaction to all projections. A shared inbox never widens readership; private forms, HARD/exam keys, tokens, original order IDs and private answer payloads do not leak through UI or shared logs. Existing ordinary EASY local scoring is preserved.
- Freeze accepted price, bank and rule terms. Server/UTC govern authoritative deadlines and seasons; local presentation respects time zones/DST. Ordinary pauses, prepared Arena clocks, exam acknowledgement/deadline/gap rules and continuous tournament time remain distinct.
- All applicable screens preserve RU/UK/EN, accessible semantic Continue/Info/Back and rotation/background/recovery. Render actual loading work and show a ready question immediately. The existing <100 ms input/answer criterion is not a new first-question loading SLA, throughput target or population cap.
- Preserve the passed stable course/theme identities, require new themes, and use the current approved course for each new final. Each successful final, including renewal before expiry, atomically records its own immutable certificate issuance. Display the latest earned certificate even with a lower passing score; retain earlier attestations unchanged. Failed finals issue nothing. Signing pending/retry preserves earning order, identity and dates, so delayed older fulfilment cannot displace a newer issuance.
- Use the active candidate as brownfield reality. Historical PASS, stale plans, mock Billing and an adopted architecture are not new implementation or acceptance evidence. The full acceptance and explicit unfinished prerequisites remain binding.
- Keep project work, artifacts and later operational evidence on VPS; preserve the nonempty installation and original restore input. This spec-writing run performs no build, service/session start, financial run or deploy. KMP transfer and public release require later owner decisions.
- Screen content requires separate explicit confirmation for every screen against the overall product logic, including screens whose visual design is already approved. Visual approval alone does not approve all copy, displayed data, action behavior or states; existing explicit decisions remain constraints for that review.
- Existing lesson titles cannot be changed, and lessons have no description field. Do not introduce title/description editing or a paid metadata-only submission from the earlier generic fee placeholder. Identify a real permitted non-question edit before proposing any separate scope or fee.
- Theme-test stars are 3 times aggregate percent / 100; the server uses existing tenths precision and half-up rounding (starsTenths = floor((30 * percent + 50) / 100), bounded 0..30), and the client renders the projection. Pass/fail still uses the captured percentage threshold; ordinary EASY/HARD stars and best-theme-result retention are unchanged.
- Both nolics and XP use additional assessment multipliers over the ordinary reward: theme x3 and final x10, retaining ordinary EASY x1/HARD x3. A HARD final therefore has x30 total tariff at equal base/allocated-time inputs. Existing theme payouts remain first-pass-only. EXAM Q-EX-6 still needs the mixed-assessment base/time/rounding contract and final payout eligibility/repeats before payout integration; these are not inferred from certificate issuance.

## Non-goals

- Mandatory iOS/web delivery, public release or transfer into the main KMP project as part of accepting this Android candidate.
- Telegram/group delivery, arbitrary participant DMs, general editing of someone else's published text, or a public proposed-edit workflow.
- Manual selection of tournament questions, a box for five ads, a new box type, or the superseded global two-box limit.
- Unapproved message auto-translation, developer broadcast announcements or an AI-only reviewer model; existing content translation remains part of review.
- Offline/queued exams, local exam scoring, a unified tournament/exam lifecycle, certificates for theme/section completion, or composite-course certificates.
- Proctoring, personal identity verification, accreditation, automatic invalidation or bans from one suspicious statistic, or direct gold purchase of exam admission/retakes/cooldown bypass.
- Invented fees, individual review-reward caps, certificate replacement without a newly passed final, quantitative product KPIs or new operational service-level promises.

## Success signal

- Demonstrate every mandatory PRD acceptance path and the exam protocol on one identified Android candidate: actual source/backend/APK/schema/config/flags, a populated installation, the required accounts/devices and genuine Play sandbox where specified. Author/review/publication, ordinary learning, Arena, the complete tournament event, commerce, configuration, feedback and exam-to-public-certificate journeys must work together with the agreed design.
- Prove delivery after offline/restart/lost response, concurrent and account-switch behavior, non-destructive upgrade, financial conservation and exact authoritative receipts. Include the required 120-action queue, full cohort/season size cases, certificate correction/pending-signing cases, lower-passing-score renewal with retained history, failed-retake preservation and out-of-order fulfilment and device/Play matrix; a smoke path, historical pass or fake provider does not replace them.
- The owner receives the candidate, traceable PASS/FAIL/NOT RUN/SKIPPED/INTERRUPTED evidence, acceptance/manual-check instructions and known limits, then manually accepts it. No open money, data-loss or permission defect, mandatory unrun check or missing owner result can be called full MVP acceptance. This specification is not that acceptance.

## Open questions

- **OQ-2:** If a permanently abandoned review cycle is added, how is its fee distributed for partial work? The owner decides before adding that outcome; an unfinished cycle currently has no final distribution or burn.
- **OQ-3:** Confirm the content of every screen separately against the overall product logic, then complete remaining visual design and experience-guidance copy, placement, thresholds and seen behavior before the affected UI is accepted. The settled UX subset is in ux-constraints.md; approved appearance or fixture content is not blanket screen-content approval.
- **OQ-4:** Resolve the exam's remaining Q-EX-2/4/5/6/7 at their stated gates: correction arithmetic, full-EASY evidence, permitted execution/recovery, mixed-assessment reward base/time/rounding and final eligibility/repeats, and optional theme-placement/course-star scope. Q-EX-1 linear theme stars and Q-EX-3 latest-issued renewal are settled; do not reopen them.
- **OQ-5:** Before the corresponding runtime or release stage, complete the environment/device/Play inventory, toolchain/security compatibility, known quiescence fix and operational cadence/budget/retention/recovery decisions. Their current availability or acceptance is not inferred by this document.
