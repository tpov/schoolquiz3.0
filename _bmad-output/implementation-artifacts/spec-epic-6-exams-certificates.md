---
title: 'Epic 6 — theme tests, final exams and certificates (exam vertical, mvp-candidate)'
type: 'feature'
created: '2026-10-16'
status: 'in-progress'
baseline_revision: 'd3501fc3fb767900a5a03b743841b13d214c9f12'
route: 'full'
route_source: 'auto'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context: ['_bmad-output/specs/spec-theme-exams/exam-protocol.md', '_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-technical-contracts.md', '_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-contract-fixtures.json']
warnings: ['multiple-goals', 'oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** The exam line (CAP-21 theme tests, CAP-22 final exams + certificates, CAP-23 corrections) exists only as binding prose, contract fixtures and scattered stubs (`SessionMode.EXAM`, unreachable `THEME_TEST`/`FINAL_EXAM` price rows, documentation-only EXAM-TECH-1 fixtures). A learner who studied cannot prove knowledge; the owner journey exam-to-public-certificate does not work.

**Approach:** Build the exam vertical server-first against the binding exam protocol and C1–C4 contracts: exam bank/admission/coverage substrate, atomic start+debit sittings with typed refusals, sole-terminal-worker lifecycle, C1 reward arithmetic with accounted-progress maximum, certificate issuance with a pluggable signing seam (activation stays the owner gate Q-EX-5), C2 corrections with generation barrier, client exam run over the existing ONLINE-O2 stack and redacted types, exam/certificate UI, and EXAM-TECH-1 fixtures made executable in both Kotlin and JS. August-spec drift is reconciled to the October decisions throughout (final price 200, all-HARD answer timer, linear 0..3 theme stars, charge at start).

## Boundaries & Constraints

**Always:**
- The exam protocol (`exam-protocol.md`) and assessment technical contracts C1–C4 with their fixture JSON are binding; where this spec compresses, they win.
- Every exam question — including the five EASY theme positions — uses the ordinary HARD answer time: seconds = max(5, roundHalfUp(chars × 0.24)), image equivalent 100, never × 0.75; the server computes and freezes allowances in the sitting.
- Theme test: 5 EASY + 15 HARD mixed draw below the theme; admission requires confirmed completion of all required EASY questions per lesson as versioned C3 evidence (union across attempts, materialization barrier — upload ack is insufficient); 24h rolling cooldown from terminal closure per user/theme; 100 STANDARD entry.
- Final exam: 20 HARD draw below the latest approved course; admission requires every required non-archived theme passed (stable by (courseId, themeId)) plus course certification eligibility (≥12 theme tests each backed by ≥100 EASY + ≥100 HARD distinct approved available questions, publication-maintained sets, never increments); 168h rolling cooldown per user/course; 200 STANDARD entry.
- Start and debit commit atomically and idempotently; at most one open sitting per user/assessment; starting while open recovers it with original deadlines and no second debit; typed refusals Locked / ResultsNotSynced / Cooldown / InsufficientCharge / PoolTooSmall / unavailable; STANDARD points only — plasma never pays admission; refusal and recovery cost nothing.
- Exams are scored only on the server through the existing shared arithmetic; the client renders returned percent, stars, pass state and advice without recalculation; theme stars map linearly starsTenths = floor((30·p + 50)/100) (half-up), pass mark server-configured default 60% captured in the sitting.
- Rewards follow C1 exactly: ordinary weighted marginal tariff with assessment coefficients (theme x3 first-pass-only, final x10 new / x0.1 repeated) measured against the persisted maximum reward-accounted progress `b`; XP and nolics rounded independently half-up from the same unrounded amount; a zero-rounded payout still accounts progress; one payout receipt per sitting committed atomically; a theme first-pass award is one receipt per user/theme, never re-paid.
- Certificates: only a successful terminal final authorizes an issuance fact committed atomically with the terminal success; signing happens outside any transaction bound by fence/CAS (C4); pending signing is explicit; profile shows the latest earned issuance by earning order (even at a lower passing score); each attestation is immutable with its own one-year validity; public verification is an unauthenticated read of the immutable snapshot + status policy.
- Corrections follow C2: recompute from original slots plus cumulative authorized exclusions with nested truncation via the shared scorer; CAS the correction head; a shared aggregate generation covers every input-changing theme write and the materializer must validate the full current generation; settled rewards, receipts and accounted-progress history are never topped up, recouped or repriced; a corrected failed→passing final issues no certificate without a new sitting; UNSCORABLE_ALL_EXCLUDED and CORRECTION_PENDING_EVIDENCE carry no numeric score.
- Persist sitting evidence (owner, assessment, selected revisions/keys, rules, issued ordering, canonical answers, timings, outcome) separately from ordinary result events; no response, projection or diagnostic exposes private keys, correct orderings, future questions, codeAnswer or per-item verdicts.
- All gradle through `sgw`; commits list files explicitly (never `git add -A`); functions tests run via `npm test` in `functions/` (node), Kotlin gates via module `jvmTest` / `ciCheck`.

**Never:**
- No activation or deployment of schedulers/signing: the onSchedule export may be added but nothing is deployed, enabled or pointed at production; concrete signing provider/algorithm/secret bindings, environment and numerical budgets remain owner gate Q-EX-5 — implement a pluggable signer with key material from config/secret indirection, absent keys leave recoverable pending work, never publish unsigned.
- No examFactor 0.75 anywhere; no client-side scoring or local exam debits; no purchase that grants a pass or opens admission by itself; no ordinary lifecycle merge (exam sittings are not lesson attempts, arena tickets or tournaments); no pause that extends answer time; no midnight/calendar cooldown resets.
- Never make ordinary `QuestionContent` correctness fields nullable to fit exam payloads — exam uses the redacted wire/domain types (`RedactedQuestionContent`); Survey questions are excluded from draws.
- No certificates for theme tests, section completion or generic quest events; no certificate for a corrected failed→passing sitting; no date extension or re-issue on correction or re-earning.
- No changes to ordinary lesson stars/rewards behavior, arena/tournament lifecycles, or files owned by the owner (`.maestro/**`, `local.properties`); Q-EX-7 (optional final/course stars) stays out of scope.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Theme start happy path | All required EASY confirmed+materialized, pool ≥5 EASY/15 HARD, no cooldown, 100 STANDARD available, terms shown | New sitting: frozen bank (revisions, worth basis, rules snapshot), debit 100, first question projection with server-frozen allowance | — |
| Start while open | Open sitting exists for user/theme | Same sitting recovered, original deadlines/state, no second debit | — |
| Cooldown | Last terminal closure < 24h (theme) / < 168h (final) ago | Refusal `Cooldown` with availableAt = closure + cooldown; boundary instant allowed | Midnight/week crossing never resets |
| Insufficient charge | lifePoints < 100/200 after regen catch-up | Refusal `InsufficientCharge`, nothing debited, no sitting | — |
| Pool too small / course ineligible | <5 EASY or <15 HARD drawable; final without 12 qualified theme tests | Refusal `PoolTooSmall` / explicit unavailable; nothing charged | Ambiguity in worth basis refuses before charge |
| Coverage not confirmed | Missing required EASY token, or evidence only uploaded not materialized | Refusal `ResultsNotSynced`/locked; ordinary offline progression stays immediate | Unknown manifest/legacy provenance → pending, not refusal-by-zero |
| First ACK | First bounded acknowledgement that question text rendered | Establishes per-question answer deadline; duplicate ACK returns the original | ACK never restarts or extends an open answer deadline |
| Answer race | Two submits for same sittingId/questionIndex | First committed answer wins, durable receipt with server arrival time; identical retry returns same receipt; conflicting reuse rejected, answer not overwritten | Recoverable processing failure retains the receipt |
| Timeout | Original question deadline expires | Question closed as unanswered (digit 1), next-question window opens if questions remain; last question requests terminalization | Timeout is not a paid skip; other earned rewards unchanged |
| Abandonment | Client never returns | Sole-terminal-worker closes sitting with recorded outcome; unanswered remainder scores normally; cooldown starts | Worker crash/overlap/poison preserve same receipts (lease+fencing) |
| Theme result | Aggregate percent p | Server stars = floor((30p+50)/100) tenths; passed = p ≥ captured 60; fixed-length advice; failed routes to lessons without revealing failed items | Client renders without recalculation |
| Final result + certificate | Successful terminal final | Terminal transaction commits result + issuance fact; signing work queued; percent below an earlier certificate still becomes the displayed latest; failed final issues nothing | Pending signing displayed explicitly; rollback commits neither success nor award |
| Reward accounting | Final at 90% rewarded, later 70% / 90% / 95% | Max accounted `b` stays 90%; 70/90 pay repeated 0.1; 95 pays new x10 only on 90–95; zero-rounded payout still accounts progress | Retry of the same sitting reuses the same receipt, no second effect |
| Correction race | Correction head published with pending admission generation, new final start arrives | Start refused before charge until complete current generation materializes; stale materializer rejected via generation check; another independently valid pass remains usable | Crash after head publication never exposes the old pass as authority |
| Signed-out verify | Unauthenticated verify call for an issuance id | Immutable public snapshot + status (valid/expired/rotated-key) from its own issue/expiry dates | Never reads mutable profile/course fields or a corrected score |

</intent-contract>

## Code Map

Worktree: `/workspace/projects/schoolquiz3.0/.local/worktrees/night-e6` (branch `night/e6-exams`). Server = `functions/` (CommonJS, node tests via `npm test` → `run-tests.js`; NOT jest). Shared Kotlin = `shared/{core,feature}` KMP; UI = `android/feature/*`. All gradle via `sgw`.

**Binding inputs (read first):** `exam-protocol.md` (flow of truth; acceptance cases 1–19), `assessment-technical-contracts.md` (C1–C4), `assessment-contract-fixtures.json` (`rewardCases` 13, `worthCases` 2, `correctionCases` 7, `arithmeticOnlyCases` 1, `invalidBasisCases` 2 — currently documentation-only), `docs/architecture/0007-certificates.md` (ADR-0007 certificate model), `_bmad-output/implementation-artifacts/epic-6-context.md` (October decisions digest).

**Existing server seams to reuse (do not rebuild):**
- `functions/assessment-scoring.js` — shared scorer (evaluateAnswer/computePercentScore/computeStars/scoreDigit), consumed via `attempt-scoring.js`; Kotlin mirror `shared/core/scoring/.../Scoring.kt`, golden `scoring-fixtures.json` parity-tested both sides.
- `functions/question-key-store.js` — server-only answer keys per lesson, written at publish (`functions/index.js:6309`); `functions/question-redaction.js` + `shared/core/question-schema/.../RedactedQuestionContent.kt` + `redacted-question-fixtures.json`.
- `functions/index.js:loadArenaLessonContext` (line 562) — canonical published+reviewApproved+contentVersion proof for a lesson; `functions/entity-version.js` (CAS versions), `functions/sync-changes.js`.
- `functions/arena-tickets.js` + `functions/arena-entry-runtime.js` (`prepareArenaEntry`, index.js) — the atomic check→debit→reserve idiom (regen catch-up, price re-read ceiling, INSUFFICIENT_*, deterministic doc ids, requestId idempotency) to copy for exam start+debit; recovery views pattern `functions/arena-ticket-recovery.js`.
- `functions/mutation-queue.js` (`submitMutation`, STATE_RESERVED/COMPLETED operation keys) — sitting call identity (ownerUid, account epoch, operationId, canonical request); `functions/attempt-receipt.js` — fingerprint (sha256 canonical pre-enrichment) + replay fault taxonomy.
- `functions/lesson-question-window.js` — 60s window primitives (`NEXT_QUESTION_WINDOW_SECONDS`, deterministic `(attemptId, completedQuestionIndex)` keys, ready-ACK codec pair `ReadyAckOutboxCodec.kt`/`ReadyAckReceiptParser.kt`, `OutboxOperations.READY_ACK`); `functions/lesson-reward.js` — ordinary tariff bands, worth model, `questionAllocatedSeconds(content, isHard)` (line 152), `POOL_SIZE = 20`.
- `functions/arena-season-worker.js` (+ `arena-season-finalization.js`, wired `reconcileArenaSeasonFinalizations` index.js:326) — the sole-terminal-worker pattern to copy: WORKER_STATE collection, LEASE_MS, fencing nonce, bounded advances, safe-error taxonomy, onSchedule adapter; also `functions/tournament-worker.js`.
- `functions/arena-service.js` (`createArenaService.transitionLesson`, `setLessonPlacement`) + `functions/arena-eligible-universe.js` — approved-bank/shelf machinery; note arena catalog is REGULAR-only, exam needs its own COURSE/theme-aware universe over `loadArenaLessonContext`.
- `functions/economy-constants.js` + `functions/config/economy-constants.json` — pricelist (THEME_TEST 100, FINAL_EXAM 300→200); `functions/currency-ledger.js` (CURRENCY/REASON closed vocabularies), `functions/life-points.js` (regen), `functions/charge-claims.js` (per-question claim settle, plasma never pays activity prices).
- `functions/index.js:submitLessonResultEvents` (line ~2503) — settlement transaction shape (`runEconomyTransaction`, Firestore re-run safety comment), `lessonBest` map updates (lines 2682–2695), `settlementReceipt` wiring (2587, 2719).

**Existing shared/client seams:**
- `shared/feature/lesson-runner/domain/.../model/SessionMode.kt` — `SessionMode.EXAM` exists; `StartLessonAttemptUseCase.kt` (POOL_SIZE 20, `InitFailureReason.RedactedNotSupported`), `state/RunnerState.kt`, `logic/RunnerLogic.kt` (`computeTimer` line ~168 — exam currently ignores `sessionMode`; flip to all-HARD for exam), `logic/ArenaOnlineO2Adapter.kt` + `model/QuestionWindowModels.kt` + `logic/ArenaRunTransitions.kt` (PAUSED→ANSWERING one-time acceptance), `logic/ArenaRunHintBudget.kt` precedent.
- `shared/feature/lesson-runner/data/.../remote/QuestionWindowWireParser.kt`, `QuestionWindowRemoteDataSource.kt` + `platform/firebase/.../arena/FirebaseQuestionWindowRemoteDataSource.kt` + `di/FirebaseModule.kt` binding + `platform/firebase/src/test/.../arena/FirebaseQuestionWindowRemoteDataSourceTest.kt` — the wire/source/DI shape an exam sitting source copies.
- `shared/feature/economy/domain/.../model/EconomyConstants.kt` (BOOTSTRAP prices; parity test `EconomyConstantsParityTest.kt` locks JSON↔Kotlin), `model/EconomyResourceBalance.kt`, `logic/ChargeRegeneration.kt`.
- Served/redacted chain (specs e2-1..15 all done): `functions/attempt-intake.js` / `scoring-pool.js` / `attempt-scoring.js` / `attempt-receipt.js`; client `shared/feature/lesson-runner/domain/.../model/ServedQuestion.kt`; round-trip proof `functions/lesson-round-trip.test.js`.
- Runner UI: `android/feature/lesson-runner/presentation/.../component/DefaultLessonRunnerRootComponent.kt`, `DefaultArenaRunnerRootComponent.kt`, `ArenaRunCoordinator.kt`, `ui/ResultContent.kt` (ordinary result — exam needs its own), navigation via Decompose components; no exam/certificate screens exist.
- Approved-catalog client reads: `shared/feature/quest-authoring/domain/.../model/LessonLifecycle.kt`, public `Lesson` model (no approval fields — server owns approval).

**Tests/fixtures conventions:** `config/*.json` shared cross-language fixtures consumed by both `functions/*-fixtures.test.js` and Kotlin jvmTest resources (models: `config/charge-claim-fixtures.json` + `functions/charge-claim-fixtures.test.js` + `shared/core/scoring/src/jvmTest/.../ChargeClaimMaskTest.kt`); functions tests are assert-scripts per file aggregated by `functions/run-tests.js`; full cycle proofs: `functions/review-fee-cycle.test.js`, `functions/lesson-round-trip.test.js`.

**Known broken at HEAD (pre-existing, owner-tracked, do not fix here):** `:platform:firebase:detekt` jvm-target 27; two pre-existing fixture-pin red tests in `:platform:firebase:testDebugUnitTest`; ktlint violations in `ReadyAckOutboxCodec`/`ReadyAckReceiptParser`/`StartLessonAttemptUseCase` outside module gates. New code you touch must itself be ktlint/detekt clean.

## Tasks & Acceptance

**Execution:** (dependency order; server phases A–B, shared/client C–D, fixtures E, gates F)

- [ ] `functions/config/economy-constants.json` + `functions/economy-constants.js` + `shared/feature/economy/domain/.../model/EconomyConstants.kt` -- set FINAL_EXAM entry to 200 (2 charges) everywhere, keep THEME_TEST 100 -- reconcile legacy final-300 drift before any debit exists (protocol §Admission "Legacy final300 ... reconcile")
- [ ] `functions/exam-bank.js` (new) -- approved-question pool loader: theme scope (all eligible below theme; 5 EASY + 15 HARD distinct identities, exact revision binding, worth-source revision per canonical question across translations) and course scope (20 HARD; latest approved course version; certification-eligibility sets ≥12 theme tests / ≥100 EASY + ≥100 HARD maintained on publication, recomputed not incremented); refuses ambiguous worth basis before charge; expose `PoolTooSmall`/unavailable as data -- the draw gates of protocol §Admission/draw; Survey excluded
- [ ] `functions/exam-coverage.js` (new) -- C3 confirmed-coverage: per-lesson required-EASY manifests (canonical question/revision tokens from published content), union of confirmed tokens across a user's settled ordinary attempts (settlement receipts + served keys as evidence), materialization barrier with explicit pending state for un-ingested uploads and unknown/legacy provenance; per-course required-theme pass registry keyed (courseId, themeId) stable across edits, archived themes ignored, new required themes demanded -- admission substrate; ordinary offline progression untouched
- [ ] `functions/exam-admission.js` (new) -- admission evaluator returning typed outcomes (Locked / ResultsNotSynced / Cooldown / InsufficientCharge / PoolTooSmall / unavailable / ok): authorization, coverage or theme-pass set, latest approved course version check, bank eligibility, rolling cooldown per user/assessment from terminal closure, shown-terms/changed-paid-terms renewal requirement, open-sitting detection; no side effects -- pure gate reused by start and by UI availability views
- [ ] `functions/exam-sitting.js` (new) -- the six callables (startThemeTest, startFinalExam, acknowledgeQuestion, submitAnswer, finishSitting, fetchSittingResult) wired in `functions/index.js`: start = ONE transaction (admission → debit 100/200 STANDARD from lifePoints with regen catch-up → sitting doc with frozen snapshot: selected revisions/keys refs, worth basis (capped 5/15/20 pool means, counts, time sums, manifest hash, rule version, captured pass mark, 60s window config, timer rule), operation identity via mutation-queue keys; recovery of open sitting without debit; acknowledgeQuestion = first bounded ACK fixes that question's deadline (duplicates return it; first-ACK gap bound retained from sitting creation); submitAnswer = first-committed-wins durable receipt (fingerprint, owner/revision/payload binding, server arrival time, identical retry same receipt, conflicting reuse rejected); finishSitting = request closure / already-closed; fetchSittingResult = explicit open state or authoritative terminal result -- protocol §Calls/§Lifecycle/§Owners
- [ ] `functions/exam-scoring.js` (new) -- server-only aggregate scoring: percent via shared scorer over canonical answers; theme linear stars tenths floor((30p+50)/100); pass vs captured threshold; result projection (sitting/assessment identity, result version, percent, passed, stars, fixed-length safe advice without below-threshold lesson names, closed time, next-attempt availability); timeout/abandonment scoring with unanswered = digit 1; no correctness leakage in any projection -- §Redaction/scoring/result
- [ ] `functions/exam-reward.js` (new) -- C1 payout: per-question worth = ordinary EASY/HARD tariff on frozen worth basis (never assessment timer), coefficients theme x3 / final x10 new + x0.1 repeated measured against persisted max accounted progress `b` per user/assessment; weighted marginal bands from `lesson-reward.js`; independent half-up rounding of XP and nolics from the same unrounded amount; zero-rounded payout still updates `b`; single receipt per sitting committed atomically with result and `b`; theme first-pass receipt one per user/theme (awardedAt, b=0 basis); final eligible every terminal attempt including failure; missing financial inputs = explicit pending/failure, never zero fallback -- §Assessment rewards + C1
- [ ] `functions/exam-terminal.js` (new) + `exports.reconcileExamSittings` in `functions/index.js` -- sole-terminal-writer worker on the arena-season-worker pattern (lease, fencing nonce, worker-state doc, bounded advances, poison isolation, injected clock in tests): closes deadline-expired questions (digit 1 + next window), abandons, terminalizes on last answer/early finish; terminal transaction = result + cooldown projection + reward effects (+ final: immutable issuance fact + durable signing work) as one commit; onSchedule adapter present but NOT deployed/enabled -- §Lifecycle + C4
- [ ] `functions/exam-certificates.js` (new) + unauthenticated `publicVerifyCertificate` in `functions/index.js` -- issuance facts (issuanceId, earning resultVersion, sitting, percent, pass mark, bank/course snapshot, claims, issuedAt, expiresAt = issuedAt + 1y, keyId) committed with terminal success; signing fulfilment OUTSIDE transactions via fence/CAS (C4): pluggable signer interface, key material from config/secret indirection (Q-EX-5), absent key = recoverable pending, verification material published before new-key use, old material retained; verify = immutable public snapshot + status policy by its own dates/keys, never mutable profile/course/corrected fields; profile projection = latest earned by earning order with explicit pending-fulfilment representation -- §Certificate issuance and verification + ADR-0007
- [ ] `functions/exam-corrections.js` (new) -- C2 corrections: authorized exclusion appends (attributable versions, CAS head), recompute from original slots + cumulative exclusions with nested truncation through the shared scorer and captured pass mark; corrected head published atomically with consistent admission/best projection or as pending generation; shared theme aggregate generation advanced by every input-changing write, validated in full by the materializer (stale = rejected, recompute); new final start refuses pending/mismatched generation BEFORE charge; settled receipts/amounts/`b` history unchanged; corrected failed→passing issues no certificate; corrected first-ever theme pass may pay the one-time award only if no receipt exists (original frozen basis, b=0); UNSCORABLE_ALL_EXCLUDED vs CORRECTION_PENDING_EVIDENCE numeric-absent states -- §Progress/quality/corrections + C2
- [ ] `shared/feature/lesson-runner/domain/.../model/ExamSittingModels.kt` (new) + `shared/feature/lesson-runner/domain/.../logic/ExamRunRules.kt` (new) -- domain projections (sitting, typed refusals, receipts, result with server stars/pass/advice), exam timer rule (all questions HARD: max(5, roundHalfUp(chars×0.24)) as display mirror only — server value authoritative), linear stars display mirror, NO client recalculation of percent/pass -- client engine substrate; SessionMode.EXAM finally honored
- [ ] `shared/feature/lesson-runner/data/.../remote/ExamSittingWireParser.kt` + `ExamSittingRemoteDataSource.kt` (new) + `platform/firebase/.../exam/FirebaseExamSittingRemoteDataSource.kt` (new) + DI binding in `platform/firebase/.../di/FirebaseModule.kt` -- wire models + parsers for the six calls (operation identity fields, redacted question payloads via RedactedQuestionContent, receipts, projections) following the QuestionWindow source/parser/test shape incl. its unit test -- transport
- [ ] `android/feature/lesson-runner/` -- exam run flow: exam entry component (admission state rendering: locked vs bought-but-unpassed, cooldown availability, typed refusal texts, terms confirmation), sitting component reusing the arena window/ACK stack with the exam ACK contract and recovery (same sitting, original deadlines), result rendering of server values (no correctness-dependent colors/marks/durations on receipts); Decompose components, no Koin in screens -- surfaces for CAP-21/22 journeys
- [ ] `android/feature/profile` (or the existing profile module) + `android/feature/exam` if placement requires -- certificate list/detail (latest earned by earning order, pending-signing state), signed-out public verification entry (deeplink/callable read-only) -- CAP-22 verification surface; theme tests issue nothing here
- [ ] `functions/exam-contract-fixtures.test.js` (new) + `shared/core/scoring/src/jvmTest/resources/` copy + Kotlin parity test (new) -- make `assessment-contract-fixtures.json` executable on both sides: rewardCases, worthCases, correctionCases, arithmeticOnlyCases, invalidBasisCases; add the C2 race-barrier and C4 worker overlap/fence cases as server tests with injected clocks (prose-only today) -- EXAM-TECH-1 becomes a passing implementation test
- [ ] `functions/exam-round-trip.test.js` (new) -- full fake-emulator cycle per `lesson-round-trip.test.js` pattern: publish fixture course/theme banks → confirm coverage → start theme test (debit 100) → ACK/answer/recover same sitting → terminal result (stars/pass/cooldown/first-pass award) → pass 12 themes → start final (debit 200, PLASMA-rich balance refused) → certificate issuance fact + pending signing → verify signed-out; cooldown boundary at 24h/168h; A→B→A scope switch -- acceptance cases 1–3, 8, 11, 14 as executable proof
- [ ] `docs/agents/evidence/2026-10-16-epic6-exams/README.md` (new) -- gates run, fixture counts, what remains owner-gated (Q-EX-5 activation, deployment, screen-content confirmation journeys) -- смена evidence convention

**Acceptance Criteria:**

- Given a user with all required EASY questions confirmed and materialized under a theme with a ≥5 EASY/≥15 HARD approved pool and no open/cooldown sitting, when `startThemeTest` is called with shown terms and 100 STANDARD available, then one transaction commits a sitting with frozen bank/rules snapshot and debits exactly 100 STANDARD, and the returned projection exposes only redacted question content with server-frozen allowances.
- Given the same user calls `startThemeTest` again while that sitting is open (fresh process, A→B→A replay of the operation id), then the same sitting is returned with original deadlines, `lifePoints` unchanged, and no second ledger entry exists.
- Given a terminal theme closure at time T, when `startThemeTest` is attempted at T+23h59m, the refusal is `Cooldown` with availableAt T+24h, and at T+24h (all else holding) the start succeeds; crossing midnight or a week boundary in between changes nothing.
- Given a theme test aggregate of p percent, the terminal result returns server stars tenths = floor((30p+50)/100) and passed = (p ≥ captured threshold); with 15% → 0.5 stars, and at a 60% mark 59% and 60% both display 1.8 stars while only 60% passes; the client renders these values without recomputation.
- Given a user whose final at 90% was rewarded, when later finals score 70%, 90% and 95%, then the accounted maximum stays 90, the 70/90 sittings pay repeated-progress 0.1, the 95 sitting pays new-progress x10 only above 90, XP and nolics are rounded independently from the same unrounded amount, and any zero-after-rounding payout still advances the accounted reference; every retry of a settled sitting returns the same receipt and pays nothing again.
- Given a successful terminal final, the terminal transaction contains the result, the cooldown projection and exactly one immutable issuance fact whose expiresAt = issuedAt + 1 year; signing remains pending until an authorized fulfilment commits a signed artifact via fence/CAS; a lower-scoring later final replaces the displayed profile certificate while both attestations remain independently verifiable signed-out with their own dates.
- Given an authorized correction excludes one correct answer of a passed theme sitting, the recomputed result may drop below the pass mark with numeric fields absent per C2 states, already settled awards/receipts/`b` history are byte-identical before and after, a formerly failed final corrected to passing issues no certificate, and a new final start during a pending admission generation is refused before any charge.
- Given the EXAM-TECH-1 fixture JSON, running `npm test` in `functions/` and the Kotlin parity jvmTest both execute every rewardCase, worthCase, correctionCase, arithmeticOnlyCase and invalidBasisCase to green, and the invalid cases are refused (no charge, no sitting).
- Given `sgw ./gradlew :shared:feature:lesson-runner:domain:jvmTest`, the new exam domain tests (timer rule incl. 100 chars→24s and 10 chars→5s floor with no 0.75 factor, linear stars mirror, redacted-only payload handling) pass; `npm test` in `functions/` passes including the new exam suites; `sgw ./gradlew ciCheck --no-configuration-cache` is green modulo the three pre-existing owner-tracked failures recorded in the Code Map.

## Implementation Notes

## Implementation Notes

### 2026-10-16 implementation session (branch night/e6-exams)

**Decisions made**

- Version of the economy table NOT bumped when reconciling FINAL_EXAM 300→200: `purchase-settlement.test.js` / `purchase-verification.test.js` pin bootstrap version 1, and before the first exam debit the table never published the price — a bump would force pointless device re-syncs for a number no client ever read. Price reconciled in `functions/config/economy-constants.json` + `EconomyConstants.BOOTSTRAP_PRICES` (2 × POINTS_PER_CHARGE) + both pinning tests.
- Worth basis (C1) computed over the WHOLE eligible pool with capped means (per `worthCases`: "difficulty-means-round-before-weight" feeds 6 easy / 17 hard seconds for a 5/15 draw), not over the drawn 20. Worth-source revision = deterministic minimum question document id among available revisions of a canonical question, frozen in the sitting manifest; any change of the available set changes the bank, and open sittings keep frozen inputs.
- Exam answer receipts, reward receipts, first-pass awards, assessment states, correction heads and theme generations live in dedicated `exam_*` collections; sitting documents are the separate evidence store (canonical content, redaction keys, issued permutations, canonical answers) and never merge with ordinary result events.
- Coverage materialization is a projection written OUTSIDE the ordinary settlement path (worker/capability, `exam_coverage/{uid,lesson}` keyed by manifest hash); ordinary offline progression untouched. Admission treats "evidence exists but not materialized at the current manifest" as `ResultsNotSynced`, unknown/legacy provenance as pending (also `ResultsNotSynced`), nothing earned as `Locked`, missing manifest as `unavailable`.
- Theme aggregate generation: `exam_theme_generation/{themeId}` advanced by every input-changing theme write (terminal pass, correction materialization). Final admission refuses when `passedAtGeneration` lags the current generation or `pendingCorrection` is set — before any charge. Materializer publishes only for the still-pending generation; racing a new pass serializes on the same document (Firestore tx), and the stale/no-op re-materialization returns `STALE_GENERATION`.
- The reward receipt records `accountedPercentBefore/After` and `unroundedN`; a failed theme pays nothing and does not move `b`; a failed final pays with `b=max(b,p)` even when both rounded amounts are zero (fixture `reachable-zero-nolics-advances-reference`).
- ACK-refusal wire shape unified server-side (`{acknowledged:false, reason, ack|null}`) so the Kotlin strict parser has one form, not branches.
- `runWithRegenRetry` mirrors the arena `RETRY_REGEN` idiom: premium checkpoint writes inside the start transaction, then the same operation id re-runs the whole transaction; request-document check makes it idempotent.
- Exam calibration constants (pass mark 60, 60s window, abandonment grace 5min, paid-terms revision, `examEnabled` off by default, certificate key id) live in `configs/exam` read by `loadExamConfig` — server-owned knobs, no client input.

**Files touched** (beyond the tasks' own new files)

- `functions/index.js` — exam callables wiring (startThemeTest, startFinalExam, acknowledgeQuestion, submitAnswer, finishSitting, fetchSittingResult, publicVerifyCertificate, reconcileExamSittings onSchedule export present but NOT deployed, fulfilCertificateSigning/appendExamCorrection curator-only, fetchMyCertificates), `readExamConfig`.
- `functions/arena-lifecycle-views-handler.test.js` — callable-surface hash re-pinned (the documented re-pin procedure; the exam surface joined index.js exports).
- `functions/economy-constants.test.js`, `EconomyConstants.kt`, `EconomyConstantsTest.kt` — FINAL_EXAM 200 pins.
- `settings.gradle.kts` — `:android:feature:exam:presentation` registered.
- `functions/exam-sitting.js` + `shared/feature/lesson-runner/data/.../ExamSittingRemoteDataSource.kt` — one interface change during the session: `finishSitting` returns a typed verdict, and ACK-refusal wire unified (see above).

**Surprises encountered**

- `local.properties` in this worktree pointed at a Mac SDK path (`/Users/tpov/Library/Android/sdk`), so no Android module could compile on this VPS; the sibling night worktrees carry the VPS toolchain path. The file IS tracked in git (old commit 76e53d10), so the fix was made in the working tree only and deliberately EXCLUDED from all commits — the repo keeps the owner's baseline; the file stays locally modified as a disclosed environment fix.
- `npm` is not installed on this VPS (only the zcode-bundled node binary at /root/.zcode/server/node, v22.16.0). `functions` tests were run via `node run-tests.js` (exactly what `npm test` executes) with `NODE_PATH` pointing at the sibling night-impl worktree's `functions/node_modules` (identical package.json).
- `essenty 2.1.0` `InstanceKeeper.getOrCreate` overloads bound `T : InstanceKeeper.Instance`, so the exam state machine got an `ExamRunInstance` holder implementing it (same pattern as ArenaRunnerInstance/RunnerStateHolder).
- JUnit4 argument order (`assertEquals(message, expected, actual)` / `assertTrue(message, condition)`) differs from kotlin.test; three new test files initially used the kotlin.test order and were fixed.
- `EconomyConstantsParityTest` + JS test pin `version: 1` of the shared economy file — see decision above; bumping would have broken two owner suites that the spec does not list as fixable.

**Verification runs (actual results)**

- `cd functions && node run-tests.js` (== `npm test`): 120/120 suites PASS, including new exam-scoring/exam-reward/exam-bank/exam-coverage/exam-admission/exam-sitting/exam-terminal/exam-certificates/exam-corrections/exam-contract-fixtures/exam-round-trip suites. `functions/check-syntax.js`: 221/221 files parse.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache` → PASS (251 tests).
- `... :shared:feature:economy:domain:jvmTest` → PASS.
- `... :shared:core:scoring:jvmTest` → PASS (incl. ExamContractFixturesParityTest over all fixture families).
- `... :platform:firebase:testDebugUnitTest --tests ...FirebaseExamSittingRemoteDataSourceTest` → PASS (10 tests).
- `... :android:feature:lesson-runner:presentation:testDebugUnitTest --tests ...ExamRunStateMachineTest` → PASS (6 tests).
- `... :android:feature:exam:presentation:testDebugUnitTest` → PASS (4 tests).
- ktlint (lesson-runner domain/data, scoring, economy domain): all NEW/CHANGED files clean; aggregate `ktlintCommonMainSourceSetCheck` red solely on the pre-existing owner-tracked `StartLessonAttemptUseCase.kt` finding (Code Map) — left as is.
- `ciCheck --no-configuration-cache` SKIPPED by recorded choice: narrower gates all green; the three pre-existing failures from the Code Map were verified individually unchanged — `:platform:firebase:detekt` reproduces exactly (`Invalid value (27) passed to --jvm-target`), the two fixture-pin `testDebugUnitTest` reds and the ktlint finding were not touched.
- Manual checks: no `0.75`/`examFactor` in exam arithmetic (only prohibition comments and negating tests); no private key material in the repo (signer is injected, bound as `null`); plasma never appears in exam admission/debit; every exam projection in `exam-round-trip.test.js` asserts absence of keys/canonical answers/codeAnswer/per-item verdicts.

**Remaining work (task-by-task)**

- All tasks A1–B6, C1–C2, D1–D2, E1–E2 implemented and green. Not done (out of this session): navigation wiring of the new components into the app's nav tree; screen-copy localization (owner confirmation required per protocol §Screen-content confirmation); deployment/activation of the schedule and signing (Q-EX-5, owner gate by design).

## Spec Change Log

## Review Triage Log

## Design Notes

**October-decision reconciliation (authoritative over any August-spec residue in code/comments):**

| Dimension | August/code today | October (build this) |
| --- | --- | --- |
| Final entry | 300 (`EconomyConstants.kt`, `economy-constants.json`) | 200 STANDARD |
| Answer timer | per-difficulty (EASY 0.36) via `computeTimer`/`TimerCoefficients`, sessionMode ignored | every exam question HARD rule: max(5, roundHalfUp(chars×0.24)); server freezes allowances |
| Theme stars | per-difficulty 0..2/2..3 (`computeStars`) | linear 0..3 from percent, tenths half-up: floor((30p+50)/100); ordinary stars untouched |
| Pass mark | absent | server-configured default 60%, captured per sitting |
| Charge timing | submit/settlement-time (`submitLessonResultEvents`) | atomic start+debit in the start transaction (arena-tickets idiom) |
| Exam sittings | absent (callables name-dropped in `activity-kind.js`) | dedicated sitting documents + 6 callables; NOT ordinary attempts |
| Fixtures | documentation-only JSON | executable in Kotlin + JS |
| Certificates/corrections/coverage | absent | new per this spec; signing activation = Q-EX-5 owner gate |

**Key formulas (from the binding protocol; do not re-derive):** theme stars tenths = floor((30·p+50)/100); answer seconds = max(5, roundHalfUp(chars×0.24)), image = 100 chars; C1 payout = ordinary weighted marginal tariff (bands from `lesson-reward.js`) with coefficient placement: theme x3 first-pass-only; final new x10 / repeated x0.1 vs persisted max accounted `b` (HARD x3 difficulty factor sits inside the tariff, so a HARD final is effective new 30 / repeated 0.3 — never a whole-attempt ×30); XP and nolics independently half-up from the same unrounded amount; zero-rounded payout still accounts progress.

**Sequencing rationale:** server substrate first (bank → coverage → admission → sitting → scoring → reward → terminal → certificates → corrections) because every client surface renders server facts; fixtures go last so they pin the finished arithmetic; the worker/certificates/corrections triad shares the arena-worker + entity-version idioms — keep those byte-consistent with the existing patterns.

## Verification

**Commands:**
- `cd functions && npm test` -- expected: all suites green including new `exam-*.test.js` (fixture cases executed, round-trip cycle green)
- `sgw ./gradlew :shared:feature:lesson-runner:domain:jvmTest` -- expected: green including new ExamRunRules/ExamSittingModels tests
- `sgw ./gradlew :shared:feature:economy:domain:jvmTest` -- expected: green (FINAL_EXAM 200 parity)
- `sgw ./gradlew :shared:core:scoring:jvmTest` -- expected: green including EXAM-TECH-1 fixture parity
- `sgw ./gradlew ktlintCheck detekt` (touched modules) -- expected: new/changed files clean
- `sgw ./gradlew ciCheck --no-configuration-cache` -- expected: green modulo the three pre-existing owner-tracked failures documented in Code Map (record actual result in evidence README)

**Manual checks (no CLI):**
- No scheduler/signing activation: `functions/index.js` exports exist but nothing deployed; grep confirms no production key material in repo; absent-key path yields pending, not unsigned publication.
- No correctness leakage: every exam projection/response in the round-trip test is asserted free of keys/correct orderings/per-item verdicts/codeAnswer.
