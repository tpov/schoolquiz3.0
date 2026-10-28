---
title: '4.21 proposal-core — запись предложения автору (AD-33, CAP-27): три вида, checks, плата, STALE'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '75193bfa'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** CAP-27/AD-33 introduce proposals to another author's material — three entries (REPORT / COPY_EDIT / NEW_LESSON) that all pass the AD-32 checks before the target author sees them, are routed to the target owner, carry a fee by kind (0 / 100×changed questions / 1000, no payouts), and refuse to apply against an outdated base revision (STALE). Nothing of this record exists yet: the pipeline (4.12–4.15) judges only `quest_review_requests` submissions, the fee path (4.18) knows only the review cycle, and the proposal surface (author side, push) comes later (4.22–4.25).

**Approach:** One proposal record for the three kinds, AD-33 literally: `lesson_proposals/{proposalId}` with kind, server-derived target owner, base revision, kind payload, fee and a status machine. Proposals are checked by the same stage skills through the same worker process (a second candidate family inside the same lease/sweep — submissions stay first), with proposal verdicts in their own immutable collection so the admin Decisions tab (4.17) is not polluted by non-submission verdicts. Submission rides the outbox as a mutation (`quest_authoring.SUBMIT_PROPOSAL`) like the fee-carrying `SUBMIT_ARENA`; the fee is charged in the same intake transaction (AD-9/10, no payouts); the quote is a read-only callable (UX-23: the entry shows its fee before sending). The author's decision endpoint lands here with the REPORT approval semantics (marks the lesson for correction) and the uniform STALE refusal; COPY_EDIT/NEW_LESSON approval is a named interim refusal until 4.23/4.24 deliver their apply halves.

**Build-order note (owner-sanctioned):** the ticket `after` names 4.20, but the owner dispatched this wave «по эпикам сразу»: 4.20/4.26 are hitl device journeys on the owner's rig and stay blocked. The pipeline behind the checks is closed by component tests (4.12–4.18 suites, 137/137 at tip); the live validation of proposals happens later in 4.26. Sentinels and wire tests are mandatory in this story (shared names are introduced here).

## Boundaries & Constraints

**Always:**
- AD-33 record: kind REPORT | COPY_EDIT | NEW_LESSON, target lesson (REPORT/COPY_EDIT) or course node — catalog/quest/section/theme path (NEW_LESSON), baseRevision read server-side from the target document's `version`, proposer from auth (never from the body).
- Target owner is derived server-side (lesson `authorUid` / quest `authorUid`): a forged uid in the body cannot reroute. Proposing to your own material is refused (`PROPOSAL_TARGET_OWN`).
- Checks: every kind passes «Слова» and «Факты» (CAP-27) through the AD-32 stage skills and linter; proposal pipeline state is a one-lesson, WORDS+FACTS state (no TRANSLATE — proposals are not translated; the applied copy-edit revision rides the author's existing translations, recorded as a boundary).
- Fee by kind at intake, one transaction with the record creation: REPORT 0 (no wallet write, no ledger row), COPY_EDIT 100 × changed questions, NEW_LESSON 1000; tariffs from `configs/proposal_fee` (normalized, corrupt = loud refusal); insufficient balance = named final refusal with zero records; ledger row `REASON.PROPOSAL_FEE` keyed by proposalId; author decision is free.
- STALE: `baseRevision !== current target revision` at approval → named refusal `PROPOSAL_STALE`, uniform for all kinds (AD-33 «применение отказывает»); the view-side marking of stale proposals is 4.25's.
- Idempotency: proposalId is client-minted (charset `[A-Za-z0-9_-]{1,128}`); replay of the same mutation returns the stored record without a second charge (read-before-write inside the intake transaction).
- The worker's submission path stays byte-for-byte in behavior: proposals are a second family inside the same sweep/lease; submissions keep priority; budget consumed by submissions leaves proposals for the next sweep (recorded boundary — proposal checks may lag under a full submission queue).
- Sentinels: export-set hash re-pinned (three new callables); outbox registry symmetry (`quest_authoring.SUBMIT_PROPOSAL` on both sides in one commit); a names sentinel test pins the proposal vocabulary server-side and cross-reads the client `OutboxOperations` constant (wire test).

**Never:**
- No changes to the submission pipeline path (review-pipeline verdict/translation records, review_verdicts/review_translations collections, quest_review_requests), the fee cycle of submissions (review_fee_*), Decisions (4.17), inbox (4.9) — the legacy `lesson_report.SUBMIT_ERROR` handler stays for old clients (its retirement is 4.22's entry switch, not a deletion).
- No author-side section/push/proposer outcome surface (4.25), no copy builder (4.23), no target-path creation UI (4.24), no deploys, no live model calls in tests (scripted model client only).

</frozen-after-approval>

## Code Map

- **New `functions/lesson-proposals.js`** — pure module: `PROPOSALS_COLLECTION = "lesson_proposals"`, `PROPOSAL_KINDS`, `PROPOSAL_STATUS` (QUEUED → CHECK_PASSED / CHECK_FAILED / CHECK_DELAYED → APPROVED / REJECTED), `normalizeProposalTariffs` (`configs/proposal_fee`: report 0, copyEditPerQuestion 100, newLesson 1000, revision; corrupt = throw), `proposalFeeNolics({kind, changedQuestions}, tariffs)`, `parseProposalSubmissionBody` (strict per-kind body: proposalId charset, target ids charset, REPORT {text ≤2000, ownVariant? ≤2000}; COPY_EDIT {lessonId, lessonTitle, questions[], editedQuestionIds ⊆ questions, ≥1}; NEW_LESSON {catalogId, questId, sectionId, themeId, lesson {title}, questions[]}, authoring-shaped question validation: id charset, text, type, language ru/uk/en, payload string ≤32KB), `buildProposalRecord`, `proposalStaleness(proposal, currentRevision)`, `buildProposalOutcome({pipeline, verdicts})` (no TRANSLATE: failedQuestions from WORDS/FACTS verdicts, delayedStages, passingLessonIds), `buildProposalCheckSource` (proposal doc → worker source shape {lessons:[one unit], questions}).
- **`functions/review-pipeline.js`** — `createPipelineState({…, stages})`: optional ordered subset of STAGE_PLAN (default unchanged), stored as `state.stages`; `nextJob`/`questStatus` iterate `state.stages || STAGE_PLAN`. Verdict/translation record builders untouched.
- **`functions/review-queue-worker.js`** — proposal family: `PROPOSAL_PIPELINE_COLLECTION = "proposal_pipeline"`, `PROPOSAL_VERDICTS_COLLECTION = "proposal_verdicts"`; `proposalCandidates()` (status QUEUED, page, createdAtMs-then-id order), `claimProposal` (tx: create/reclaim one-lesson WORDS+FACTS state; non-QUEUED or leased → skip), `advanceProposalStep` (crisis replay by deterministic verdict name, scripted skills, linter, ≤3 attempts → proposal DELAYED), `finishProposal` (outcome → proposal doc {status CHECK_PASSED/CHECK_FAILED/CHECK_DELAYED, reviewOutcome, checkedAtMs}); `sweep()` runs proposals after submissions inside the same lease/budget, same result shape.
- **`functions/index.js`** — `MUTATION_HANDLERS["quest_authoring.SUBMIT_PROPOSAL"]` → `applyLessonProposalSubmission` (parse → idempotent read → target docs → owner derive → own-target refusal → fee plan → one tx: create-lock, wallet decrement both fields, ledger row, proposal create QUEUED); `exports.quoteLessonProposalFee` (read-only, kind+changedQuestions → fee); `exports.decideLessonProposal` (owner-only, indistinguishable not-found for чужих; CHECK_PASSED-only; STALE refusal on approve; REPORT approve → lesson `{correctionRequestedAtMs}`; COPY_EDIT/NEW_LESSON approve → `PROPOSAL_APPLY_PENDING` interim named refusal until 4.23/4.24; REJECT → {rejection {comment}}).
- **`functions/currency-ledger.js`** — `REASON.PROPOSAL_FEE: "proposal_fee"` (own reason: proposals pay for a different thing than the review cycle; folding into `review_fee` would lose the movement the book exists to show).
- **`functions/arena-lifecycle-views-handler.test.js`** — export-set hash re-pinned (quote/decide callables join; the mutation rides the queue, not the export surface).
- **New `functions/proposal-names-sentinel.test.js`** — counted scan: pins PROPOSAL_KINDS values, statuses, collection names, the mutation op name, and cross-reads `OutboxOperations.kt` for `SUBMIT_PROPOSAL` (wire test; scanned-file floor).
- **Client data row:** `shared/core/outbox/.../OutboxOperations.kt` — `SUBMIT_PROPOSAL = "quest_authoring.SUBMIT_PROPOSAL"` (+AD-3 rationale comment; not in terms-consent: proposal tariffs are not the economy consent table, same criterion as SUBMIT_ARENA); `OutboxOperationsRegistryTest.kt` declared set + comment. Writers/use cases/UI are 4.22–4.25's.

## Tasks & Acceptance

**Execution:**
- [ ] Module lesson-proposals.js + suite (tariff normalization/corruption, fee math, body validation per kind, staleness, outcome builder).
- [ ] review-pipeline.js reduced plan + suite cases.
- [ ] Worker proposal sweep + suite cases (queued→CHECK_PASSED, FAIL→CHECK_FAILED, linter exhaustion→CHECK_DELAYED, non-QUEUED skip, submission priority).
- [ ] index.js: mutation handler (intake tx, idempotency, fee, refusals), quote callable, decide callable; ledger reason; export pin.
- [ ] Client outbox row + registry test.
- [ ] Sentinel suite + gates.

**Acceptance Criteria:**
- Given a REPORT proposal body, when the mutation is applied, then a `lesson_proposals` record exists with server-derived owner, baseRevision from the lesson document, fee 0 and status QUEUED; a replay of the same proposalId returns the stored record and charges nothing.
- Given a COPY_EDIT proposal with changedQuestions=3 and a funded wallet, when intake runs, then 300 nolics leave the wallet (both mirrored fields), one `proposal_fee` ledger row exists keyed by the proposalId, and the record is QUEUED; an unfunded wallet gets `PROPOSAL_FEE_INSUFFICIENT` with zero records.
- Given a NEW_LESSON proposal targeting the proposer's own quest, when intake runs, then `PROPOSAL_TARGET_OWN` refuses with zero records.
- Given a QUEUED proposal, when the worker sweeps, then the proposal's questions are judged by the same WORDS/FACTS skills into `proposal_verdicts`, the checkpoint lives in `proposal_pipeline`, and the terminal record names CHECK_PASSED/CHECK_FAILED/CHECK_DELAYED with the outcome — while `review_verdicts` stays untouched by proposal ids.
- Given a CHECK_PASSED proposal whose target lesson version moved past baseRevision, when the owner approves, then `PROPOSAL_STALE` refuses and nothing is written; same-version approve on a REPORT marks the lesson for correction and flips the record to APPROVED.
- Given the tree, when the sentinels run, then the export set matches the re-pinned hash, the outbox registry is symmetric across client/server, and the proposal vocabulary is pinned by a counted scan.

## Implementation Notes

- Baseline 75193bfa (4.19 done). Wave precedent: spec straight to in-progress, change log is the record.
- Fee quote reuses the 4.11 pattern: the quote names the price, the intake recomputes it from the real body — the quote has no power.
- Proposal questions ride as authoring-shaped data (full payloads); the copy WITHOUT secret keys is built by 4.23's server callable before the editor ever sees the lesson — the record itself is out of the redaction business.

## Verification

**Commands (run on VPS, 2026-10-28):**
- functions: `docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"` — **139/139 suites passed** (было 137; +3 новые сюиты: `lesson-proposals`, `proposal-names-sentinel`, расширенные `review-pipeline`/`review-queue-worker`; пин REASONS в `currency-ledger.test.js` дополнен `proposal_fee`). В середине смены гейт был красный двумя чужими сюитами (`deferred-actions`, `poll-views`) — незакоммиченная работа соседа по волне (эпик-8: 5-аргументный вызов обработчика и transaction-переписывание `applyPublishPollSeries`); после их коммита 7d64d4c7 — 139/139; в финальном прогоне перед коммитом красных нет.
- gradle (`JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew … --no-configuration-cache`): `:shared:core:outbox:jvmTest` — BUILD SUCCESSFUL (реестр `OutboxOperationsRegistryTest` симметричен с `MUTATION_HANDLERS`); `:shared:core:outbox:ktlintJvmMainCheck :ktlintJvmTestCheck` — BUILD SUCCESSFUL.
- Экспорт-пин: `quoteLessonProposalFee` + `decideLessonProposal` вошли в хешируемый остаток поверхности (не в lifecycle-ENDPOINTS — у той цикла другое семейство и общее сообщение отказа); новый пин `0540294d…`, комментарий re-pin в `arena-lifecycle-views-handler.test.js` — suite зелёный.

## Spec Change Log

- 2026-10-28, in-progress: wave dispatched (4.21–4.25), 4.20/4.26 left blocked; build-order deviation recorded in Intent.
- 2026-10-28, implementation loopbacks: (1) `readCourseTarget` получает построенный `targetRef` (поля `target*` тела), а не сырое тело — путь цели сверяется по тем же четырём id, что едут в запись; (2) `advanceCheckJob` — общий проверочный шаг заявок и предложений (кризисный повтор по имени вердикта, линтер, ≤3 попыток); семьи различаются только коллекциями и записью; (3) терминал предложения атомарен: конвейер + исход + статус — одна транзакция (у заявок шов дотянут догоном, предложениям достаточно одной записи); (4) сокращённый план этапов — только префикс STAGE_PLAN (`stateStagePlan`), хранится в состоянии; legacy-записи без поля читаются полным планом; (5) экспорт-пин перепинован хешем остатка, ENDPOINTS не тронут.
- 2026-10-28, gate record: functions 139/139 (см. Verification); gradle outbox jvmTest + ktlint зелёные; сентинел `proposal-names-sentinel.test.js` — словарь видов/статусов/коллекций + cross-read `OutboxOperations.kt` (wire-тест) + counted-скан с полом 100 файлов.
