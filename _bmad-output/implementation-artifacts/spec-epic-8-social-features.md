---
title: 'Epic 8 (social features) — finish 8.3, build 8.4/8.5/8.6: private quizzes under AD-29 and history/earnings projections'
type: 'feature'
created: '2026-10-22'
status: 'in-progress'
baseline_revision: '6ac52f2f9c44f0d63818af3dc77552d1e8ea437d'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '_bmad-output/mvp-candidate/epic-social-features/8.3-form-adaptation-plan.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md'
  - '_bmad-output/specs/spec-schoolquiz3.0/ux-constraints.md'
  - 'docs/agents/evidence/2026-10-19-epic7-commerce-client/README.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Stories 8.1–8.2 (polls, CAP-24) are done; the epic's remaining scope is not built: the authoring form does not expose AD-29 private-quiz settings (attemptLimit/showResult/expiry) and does not adapt to a flat HARD-only set for «Приватные квизы» (8.3); a private quiz cannot RUN (the start-gate callable exists but no attempt flow consumes it — 8.4); nothing proves the AD-29 negatives (no charges, no deferral — 8.5); and account holders/authors have no operation-history/earnings read projection (8.6).

**Approach:** Finish 8.3 by carrying PrivateQuizSettings through the existing SUBMIT_ARENA pipeline into the private quest document and adapting the existing authoring form (no parallel submission mechanism). Build 8.4 on the already-built `startPrivateQuizAttempt` gate plus the existing ONLINE-O2 runner machinery: gate as the only door, single continuous sitting, server-recomputed result, showResult gating the participant's own-result projection. Prove 8.5 by absence tests (behavioral + static fences, `premium-neutrality.test.js` style). Build 8.6 contract-first: write the own-ledger projection contract (gates UX-O10/O12) BEFORE any adapter, then one authorized read projection serving drawer history and per-lesson «Начисления», reusing the TZ06B views family and cursor patterns. HITL device proofs (8.3 authoring screen, 8.4 two-device+timeout, 8.6 account A→B→A) are prepared as recipes and handed to the owner — code+JVM/Node tests remain this session's Done bar (tickets.toml scope rule).

## Boundaries & Constraints

**Always:**
- AD-29 defaults on private authoring: attemptLimit 1, showResult false, no expiry; loading an existing quiz keeps its saved settings.
- Attempt start stays atomic and idempotent: enabled && unexpired && startedCount < currentLimit, then startedCount+1; a retry of the same startId never spends the limit twice (already in the gate — keep it).
- Limit changes preserve startedCount and enabled runtime state; a limit increase admits only the remaining difference; a decrease never interrupts a started attempt; closing/expiring the link refuses only NEW starts.
- showResult is read at every own-result read/retry with its CURRENT value; false = completion confirmation only (no counts, no percent, no question scores); true = own result only. Author statistics remain available regardless (separate uid-scoped authorized read).
- Recovery is technical only, within original deadlines; when the window ends the attempt terminates with reason timeout and scores the WHOLE frozen quiz with remaining questions (including unissued) unanswered; a timed skip is never a paid-correct answer.
- Private quizzes touch no STANDARD/PLASMA charges, no toll, no tickets, no review fee, no deferred continuation, no replacement fee, no pass/fail threshold; private ops never enter termsConsentOperations.
- 8.6: one authorized read projection for own history AND author lesson earnings; records carry identity/date/reason/signed amount/resource/status; date groups, All/Income/Expense filters, expandable records and bounded continued pagination preserve context; pending is never inside confirmed income; unlike resources are never summed; account switch (owner/epoch) reveals nothing foreign; reading creates no financial effect.
- Contract-before-adapter for 8.6: the own-ledger projection contract (permitted fields, bounded order, cursors) is written and committed before the client adapter is built (UX-O10/O12 gate wording: «…before corresponding real actions»).
- Every commit updates `tickets.toml` statuses and drops evidence under `docs/agents/evidence/<date>-<topic>/` (epic-7 precedent).
- All UI text RU/UK/EN via string resources; typed states (loading/empty/error/unknown/pending vs confirmed) everywhere; server-side authorization/redaction — UI hiding never substitutes for it.

**Never:**
- No new exam/Arena lifecycle inheritance for private quizzes; no shared mode controller (AD-29).
- No guest/password access to private quizzes; no link auto-renewal/rotation/re-enable (не назначено).
- No vote/right-to-change-vote work (polls done; §2 sourceId amendment awaits owner nod — do not touch poll code).
- No financial-ledger WRITES from 8.6 (history is read-only projection; `audit/currency_ledger/entries` stays server-write-only).
- No changes to: consent chain (`submitMutation` terms verdict/re-arm, `OutboxConsentRequeue`), settlement replay (`SettlementAlreadyRecorded`, sync-changes mapping), live tournament code (5.13 in flight), `changedAtMs` server-authoring invariants, review-fee cycle.
- No new Gradle/daemon memory limits (AGENTS.md: builds only via `sgw`, one heavy task at a time).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Private authoring form opens | Category «Приватные квизы» selected | Hierarchy pickers (Розділ/Тема/Урок) hidden; attempt-limit (≥1), show-result toggle, expiry («без срока» default) shown; all questions HARD, SURVEY excluded | Validation refusals mirror QuestDraft init{} style |
| Existing private quiz loaded for edit | Doc has settings + startedCount>0 | Form shows saved settings; resubmission updates settings fields but preserves startedCount/enabled server-side | Local/remote conflict goes through existing PrivateQuestMerge (SAVE/CONFLICT/KEEP_LOCAL) |
| Start attempt (happy) | enabled, unexpired, startedCount<limit | Gate transaction creates starts/{startId} record, startedCount+1, runner enters single continuous sitting | — |
| Start retry | Same startId resent (offline duplicate) | Stored start record returned; limit NOT consumed twice | failed-precondition only for door refusals |
| Door refusals | disabled / now≥expiry / startedCount≥limit | LINK_DISABLED / LINK_EXPIRED / ATTEMPT_LIMIT_REACHED; no attempt begins; closed link never interrupts an already-started attempt | Refusal surfaced as typed state on entry screen |
| Limit raised 1→3 after 1 start | startedCount=1 | Two more starts admitted; startedCount preserved | — |
| showResult=false read own result | Participant finished (or retries read) | Completion confirmation only: no counts/percent/questionScores — on EVERY read incl. past attempts | Unknown statistics never rendered as zero |
| showResult=true | Current setting true | Own result with counts/percent shown | Author statistics unaffected by the flag |
| Recovery timeout | Recovery window ends mid-attempt | Attempt terminal, reason timeout; whole-quiz score over frozen quiz with remaining (incl. unissued) unanswered; no paid-correct effect for skips | Terminal result is permanent receipt material, not a deferral |
| Charge absence | Any private flow (entry/hints/skips/timeout) | ZERO rows in audit/currency_ledger/entries; no ATTEMPT_TOLL/CHARGE_CLAIM/REVIEW_FEE; consent never requested | Absence tests assert the fence statically + behaviorally |
| History page read | Drawer «История операций» | Date-grouped records, identity/date/reason/signed amount/resource/status; All/Income/Expense filters; expandable record; bounded page with continuation cursor | Empty/loading/error distinct; never a fake zero income |
| Pending vs confirmed | Unsettled claim/ticket present | Pending shown as pending, excluded from confirmed income sums | Pending never credited as income |
| Account switch A→B→A | Owner opens history after switch | Only current owner's records; stale responses cut off (epoch fence) | Foreign records never rendered |

</intent-contract>

## Code Map

- `functions/private-quiz-gate.js` (+ `.test.js`, 8 cases) -- ALREADY BUILT start gate: `startPrivateQuizAttempt({db,uid,data,now})`, req `{ownerUid, catalogId, questId, startId}`, idempotent via `starts/{startId}`, `normalizeSettings` (defaults AD-29), refusals LINK_DISABLED/LINK_EXPIRED/ATTEMPT_LIMIT_REACHED; registered `functions/index.js:154,533-546` as callable. MISSING: no attempt-session/submit flow consumes it; zero Kotlin call sites.
- `functions/index.js` -- mutation handler map ~1924; `applyArenaSubmission` 2343 → `processArenaRequest` 5270 → `writePrivateHierarchyToBatch` 5628 → `privateDocuments` 6382, private quest doc write ~6397 (fields list there; add `privateQuiz` settings here); `privateQuestPath` helper 8406 (`private/{uid}/catalogs/{cat}/quests/{qid}`); consent enforcement 2390-2540 (do-not-touch); charges settled ~2960-3000 (ATTEMPT_TOLL + CHARGE_CLAIM); `MUTATION_TERMS_OPERATIONS` 1999.
- `android/feature/quest-authoring/presentation/.../screen/QuestCreateScreen.kt` -- authoring form («Створення квесту»); `SectionPathSpinner` ~263, `ThemePathSpinner` ~272 + lesson picker = hierarchy to hide for private category.
- `android/feature/quest-authoring/presentation/.../component/DefaultQuestCreateComponent.kt` -- 1776 lines; `submitNodeToArena` ~220, resubmit ~303, catalog select ~905, QuestType shelf branch ~1659 (extend for private); `uistate/QuestCreateUiState.kt` (`hasValidStructureTitles` must relax for private).
- `android/feature/app-shell/presentation/.../component/DefaultRootComponent.kt` -- `QuestCreateContent` child factory 160/310-312 (currently Placeholder — wire real component if not yet).
- `shared/feature/quest-authoring/domain/.../model/QuestDraft.kt`, `QuestAuthoringBundle.kt`, `DraftQuestionType.kt` -- draft model + init{} validation pattern (mirror for PrivateQuizSettings validation); flat HARD-only maps to `defaultDifficulty`.
- `shared/feature/quest-authoring/data/.../outbox/QuestArenaOutboxWriter.kt` -- `row()`/`toPayload()` builds SUBMIT_ARENA JSON; add privateQuiz settings to `draft{}` here; `data/remote/QuestArenaSubmissionRequest.kt` (`ArenaDraftDto`) + mapper.
- `shared/feature/quest-authoring/data/.../PrivateQuestMerge.kt`, `data/sync/QuestPrivateSync.kt`, `data/remote/QuestPrivateRemoteDataSource.kt` -- participant/author delivery of private quest snapshots `{serverRevision, request}`; per-owner cursor id pattern `private_quests:{uid}`; load-existing path `domain/use_case/OpenQuestDraftUseCase.kt`, `GetActiveQuestDraftUseCase.kt`.
- `shared/feature/lesson-runner/domain/.../use_case/StartLessonAttemptUseCase.kt`, `logic/ArenaOnlineO2Adapter.kt` (L13,38), `logic/RunnerLogic.kt` (computeTimer 158, abort-code 149-154), `model/ArenaEntryLocalState.kt` (NEEDS_RECOVERY states) -- ONLINE-O2 single-sitting timing to reuse for the private run; `data/repository/ArenaEntryRepositoryImpl.kt` (callable entry pattern, epoch recheck L211-235).
- `functions/attempt-scoring.js:40-60` -- served-unanswered='1', never-served='0'; extend input for private timeout (all fixed questions scored, unissued unanswered). Timeout-terminal precedent: `functions/exam-terminal.js` CLOSE_REASON.TIMEOUT L49,114. Result recompute: `functions/attempt-intake.js` → `result-verification.js` (`recomputePercentScore`), op `lesson_runner.SUBMIT_ATTEMPT` index.js:1911, 2756.
- `android/feature/lesson-runner/presentation/state/RunnerUiState.kt:41` (`Result`), `ui/ResultContent.kt` -- client result projection; showResult=false suppresses counts/percent/questionScores server-side AND in this projection, keeps completion confirmation.
- `functions/arena-lifecycle-views.js` -- TZ06B views family (DONE, story 4.2: commits 3dca0ef1 + client 1db295c4): `protectedRead` L275 uid-scoping, `listMyRoyalty` ~383-405 = cursor pattern to reuse (`startAfter(cursor.atMs, cursor.id)`, `limit(size+1)`, uid-bound encode/decode); `lesson-statistics.js` helpers exist unwired.
- `functions/currency-ledger.js` -- `LEDGER_PATH="audit/currency_ledger/entries"` L27; entry schema L164-184 (`id` derived idempotency, `uid`, `currency`, `delta` signed, `reason`, `sourceId`, `atMs`, `details`); CURRENCY/REASON constants L39-60. 8.6 reads this; 8.5 asserts zero rows.
- `shared/core/sync/.../SyncCursor.kt` ((changedAtMs,docId) lexicographic, SYNC_PAGE_SIZE 200), `SyncJournal.kt` (bounded pages) -- paging reuse for the own-ledger projection.
- `shared/core/outbox/.../AccountSwitchGuard.kt`, `OwnerTraces` (per-owner cursors L132-140) -- epoch fencing pattern for history reads; `OutboxOperations.kt` (termsConsentOperations L79; registry tests `OutboxOperationsRegistryTest` = absence-test pattern).
- `android/feature/app-shell/presentation/.../ui/drawer/DrawerSectionList.kt` (+ DrawerContent/DrawerSection) -- drawer shell for «История операций» entry; `android/feature/economy/presentation/` has Shop screens only (history screen is NEW, reusing drawer + TZ06B client-half patterns from 1db295c4).
- `functions/private-catalog-seed.js` + `docs/agents/evidence/2026-10-20-private-quiz-category-seed/` -- category seed (VERIFIED LIVE 2026-10-22: catalogs/private-quizzes-v1-catalog, «Приватные квизы», REGULAR).
- `docs/agents/evidence/2026-10-19-epic7-commerce-client/README.md` -- gates: functions node tests in container `schoolquiz-functions-emu` (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp`), plain `cd functions && node run-tests.js` for pure suites.
- `_bmad-output/mvp-candidate/epic-social-features/tickets.toml` -- statuses to update per commit; NOTE: its «TZ06B (4.2) — ready (не выполнен)» remark is STALE (4.2 done 2026-10-09) — correct it when touching 8.6.

## Tasks & Acceptance

**Execution (story order; update tickets.toml + evidence dir with each story):**

8.3 — private quiz authoring defaults (finish):
- [ ] `shared/feature/quest-authoring/domain/.../model/QuestDraft.kt` -- add `PrivateQuizSettings {attemptLimit>=1=1, showResult=false, linkExpiresAtMs?=null}` with init{} validation (limit integer ≥1; expiry null or in the future at save time), sunk into the draft for the private category -- AD-29 defaults live in the domain model, not the UI.
- [ ] `shared/feature/quest-authoring/data/.../outbox/QuestArenaOutboxWriter.kt` + `data/remote/QuestArenaSubmissionRequest.kt` (ArenaDraftDto + mapper) -- carry `privateQuiz` settings in the `draft{}` payload for private-category submissions -- single pipeline, no parallel mechanism.
- [ ] `android/feature/quest-authoring/presentation/.../QuestCreateScreen.kt` + `DefaultQuestCreateComponent.kt` + `QuestCreateUiState.kt` -- private-category branch: hide Розділ/Тема/Урок pickers (flat HARD-only set, SURVEY excluded), show attempt-limit/show-result/expiry controls with AD-29 defaults; for an EXISTING quiz add the manual close (enabled=false) control; relax `hasValidStructureTitles` for private -- the 8.3 device-proof gap named in tickets.toml.
- [ ] `functions/index.js` (`privateDocuments` ~6397) -- persist `privateQuiz {attemptLimit, showResult, linkExpiresAtMs, enabled:true, startedCount:0}` on the private quest doc via the gate's `normalizeSettings`; on RESUBMISSION of an existing private quest update settings fields but PRESERVE runtime `startedCount`/`enabled` (merge, not overwrite) -- limit changes preserve startedCount (AD-29).
- [ ] `shared/feature/quest-authoring/data/.../PrivateQuestMerge.kt` / `QuestPrivateSync.kt` / `OpenQuestDraftUseCase.kt` -- round-trip: loading an existing private quiz restores saved settings into the form -- «loading an existing quiz keeps its saved settings».
- [ ] `functions/private-quiz-gate.test.js` (extend) + new `functions/private-quiz-settings.test.js` -- settings persisted with defaults; resubmission preserves startedCount/enabled; validation refusals (limit 0, past expiry at save); sync returns settings to author/participant readers.
- [ ] `shared/feature/quest-authoring/domain` jvmTest + `android/feature/quest-authoring/presentation` unit tests -- settings validation; UI-state for private category (pickers hidden, controls present, defaults).
- [ ] `docs/agents/evidence/<date>-private-quiz-authoring/` -- evidence + DEVICE-PROOF RECIPE for the owner (Samsung, category seeded): form adapted, defaults correct, reload preserves settings, close-link refuses new starts.

8.4 — private quiz run: limits and visibility:
- [ ] `functions/private-quiz-gate.js` (+ `.test.js`) -- extend the start record into the run contract: after a passing door, the returned record carries the frozen quest identity + attempt deadlines basis; ensure deadline fields derive from the original start (original question deadline runs through disconnection; no extension on recovery) -- the gate is the only door; original intent fixed before dispatch (AD-6).
- [ ] `shared/feature/lesson-runner/domain` (new use-case/repository alongside `StartLessonAttemptUseCase.kt` / `ArenaEntryRepositoryImpl.kt` pattern) -- private-quiz entry: call `startPrivateQuizAttempt`, map refusals to typed entry states (LINK_DISABLED/LINK_EXPIRED/ATTEMPT_LIMIT_REACHED), on success run the quiz through the existing ONLINE-O2 runner (ArenaOnlineO2Adapter timing, question windows) with the delivered private snapshot -- no shared mode controller; inherits no exam fee/timer/pass-fail.
- [ ] `functions/attempt-scoring.js` / `attempt-intake.js` (+ tests) -- private timeout path: when the recovery window ends, terminate with reason timeout and score the WHOLE frozen quiz (all fixed questions; remaining incl. unissued = unanswered '0'); skips never count as paid-correct -- AD-29 timeout rule; exam-terminal.js precedent.
- [ ] `functions/` result read path + `android/feature/lesson-runner/presentation` (RunnerUiState.Result / ResultContent.kt) -- showResult gate: CURRENT setting governs every own result read/retry incl. past attempts; false → completion confirmation only (no counts/percent/questionScores, never fake zeros); true → own result only; author statistics read stays available under the uid-scoped callable pattern (`arena-lifecycle-views.js` `protectedRead`) -- server-side authorization, UI hiding never substitutes.
- [ ] `functions/private-quiz-run.test.js` (new, node, emulator) -- end-to-end against the demo-backend shape: start→limit consume→retry idempotent→finish→own-result visibility for both flag values; limit raise 1→3 admits 2 more; close link mid-attempt lets the started attempt finish; timeout scores whole quiz.
- [ ] `android/feature/lesson-runner` + entry-screen unit tests -- refusal states rendered; completion-only result rendering when showResult=false.
- [ ] `docs/agents/evidence/<date>-private-quiz-run/` -- evidence + DEVICE-PROOF RECIPE (two devices + timeout case).

8.5 — negative proof (no device needed):
- [ ] `functions/private-quiz-negative.test.js` (new) -- behavioral: across submission/start/run/timeout, `audit/currency_ledger/entries` gains ZERO rows; no ATTEMPT_TOLL/CHARGE_CLAIM/REVIEW_FEE reasons; consent never requested for private ops -- asserts the no-charges rule against the real flow.
- [ ] `functions/private-quiz-negative.test.js` (static fence, `premium-neutrality.test.js` style) -- static assertion that private-quiz server files import no charge/fee/ticket modules -- «забор, а не описание».
- [ ] `shared/core/outbox/src/jvmTest/.../OutboxOperationsRegistryTest.kt` (extend) -- private-quiz ops (if any new op is introduced) are NOT in termsConsentOperations and agree bidirectionally with the server registry -- poll SUBMIT_POLL_VOTE precedent.
- [ ] `shared/feature/lesson-runner/domain` jvmTest -- no deferred-continuation type/state is introduced for private quizzes; terminal timeout is a terminal result, not a resumable state -- AD-31 boundary.
- [ ] `docs/agents/evidence/<date>-private-quiz-negative/` -- evidence.

8.6 — operation history and earnings read projections:
- [ ] `_bmad-output/mvp-candidate/epic-social-features/ux-o10-own-ledger-projection-contract.md` (new, CONTRACT FIRST) -- permitted fields (identity/date/reason/signed amount/resource/status), bounded order + lexicographic cursors (atMs,id), pending-vs-confirmed distinction, resources never summed, no money commands, redaction; mirror the ux-o7-poll-contract.md format and cite gate wording UX-O8/O10/O12 -- «O10 fixes permitted fields and bounded continuation before adapters».
- [ ] `functions/arena-lifecycle-views.js` (+ new `functions/own-history-views.test.js`) -- own-history view callable: uid-scoped read over `audit/currency_ledger/entries` (+ pending claim/ticket sources), `startAfter(atMs,id)` bounded pages with continuation, permitted fields only; author per-lesson «Начисления» reuses `listMyRoyalty` + lesson-scoped filter -- one authorized projection family, no new ledger facts.
- [ ] `shared/feature/economy` (or sibling module following TZ06B client half from 1db295c4) -- projection client: repo + paging over the view callables, per-owner cursor ids + `AccountSwitchGuard`/epoch recheck (switch cuts off stale responses) -- reading creates no financial effect.
- [ ] `android/feature/app-shell/presentation/.../ui/drawer/DrawerSectionList.kt` + new history screen (RU/UK/EN strings) -- «История операций» drawer entry: date groups, Все/Начисления/Расходы filters, expandable record (signed amount, resource icon/color, status), distinct loading/empty/error; per-lesson «Начисления» entry from my-quests lesson surface -- UX-20 / F9 contract.
- [ ] JVM/Android unit tests -- projection mapping (pending excluded from confirmed income, resources not summed), filter/group logic, empty/error states; node view tests -- pagination bounds, cursor continuation, uid scoping (foreign uid → forbidden/empty per contract), unauthenticated refusal.
- [ ] `docs/agents/evidence/<date>-history/` -- evidence + DEVICE-PROOF RECIPE (account A→B→A reveals nothing foreign).
- [ ] `_bmad-output/mvp-candidate/epic-social-features/tickets.toml` -- fix stale TZ06B remark (4.2 done: 3dca0ef1, 1db295c4) and set story statuses as they close.

**Acceptance Criteria:**
- Given the private category in «Створення квесту», when the author opens the form, then hierarchy pickers are hidden, attempt-limit/show-result/expiry controls show with defaults 1/false/«без срока», and saving+reloading the quiz restores the saved settings.
- Given a private quiz with limit 1 already started once, when another start is attempted, then the entry shows the typed limit-reached state and no attempt session exists.
- Given a started attempt and the link then closed or expired, when the participant continues, then the attempt finishes under its original identity and deadlines, while a NEW start is refused.
- Given showResult=false, when the participant reads any own result (fresh or past, online or retry), then only completion confirmation is shown — no counts, percent or question scores, and nothing renders as a fake zero — while the author's statistics callable still returns full stats.
- Given the recovery window expires mid-attempt, when the attempt terminates, then the terminal result scores the whole frozen quiz with remaining questions unanswered, no ledger rows exist for the whole flow, and skips are not counted as correct.
- Given the drawer entry «История операций», when the user opens it, then records are date-grouped with filters and expansion per UX-20, pending records are visible but excluded from confirmed income, resources are never summed together, and paging continues with bounded pages.
- Given account A opens history, then switches to B, then back to A, then at every point only the current owner's records render and no foreign record or stale page survives the switch.
- Given the whole epic's flows, when `cd functions && node run-tests.js` and the listed `sgw ./gradlew ... jvmTest/testDebugUnitTest` targets run, then all suites are green.

</intent-contract>

## Implementation Notes

_(empty at planning)_

## Spec Change Log

_(empty)_

## Review Triage Log

_(empty)_

## Design Notes

- **Settings ride the existing pipeline.** SUBMIT_ARENA already writes the private quest doc (`privateDocuments`); settings are new fields on that doc + `draft{}` payload. No new submission op, no parallel write path (AD-6/AD-8 reuse).
- **The gate stays the only door.** `startPrivateQuizAttempt` (affb8b18) is kept as-is semantically (idempotent `starts/{startId}`, refusals); 8.4 consumes it from Kotlin and extends the returned record into the run contract instead of adding a second entry point.
- **Timeout scoring reuses attempt-scoring.** Served-unanswered='1' / never-served='0' already exists; the private timeout path feeds the whole frozen question set as the scoring input (exam-terminal CLOSE_REASON.TIMEOUT precedent) rather than inventing a new scorer.
- **8.6 is read-only over epic-2 facts.** The ledger (`audit/currency_ledger/entries`) and pending claim/ticket sources already exist; history is a projection with TZ06B's authorization + cursor patterns (`listMyRoyalty`, `protectedRead`, `SyncCursor`). The contract document lands BEFORE any adapter (gate requirement).
- **Corrections vs stale notes.** TZ06B/story 4.2 is DONE (not "ready") — 8.6's epic-4 dependency is satisfied. The demo-backend category seed is LIVE (verified 2026-10-22 via Firestore REST), so 8.3's remaining gap is purely the form adaptation + device proof. Polls §2 `sourceId` amendment (uid in sourceId) remains OPEN awaiting the owner's nod — normative as amended; poll code is out of scope.
- **HITL split.** This spec's Done bar = code + JVM/Node tests + evidence + device-proof recipes (tickets.toml scope rule); physical device proofs (8.3 authoring screen, 8.4 two-device+timeout, 8.6 A→B→A) are the owner's acceptance steps after dispatch.

## Verification

**Commands:**
- `sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest :android:feature:quest-authoring:presentation:testDebugUnitTest` -- expected: all green (8.3).
- `sgw ./gradlew :shared:feature:lesson-runner:domain:jvmTest :shared:feature:lesson-runner:data:jvmTest` -- expected: all green (8.4/8.5 client half).
- `sgw ./gradlew :shared:core:outbox:jvmTest` -- expected: all green incl. registry absence tests (8.5).
- `cd functions && node run-tests.js` -- expected: all suites green incl. new private-quiz tests; emulator-dependent suites inside container `schoolquiz-functions-emu` (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp`, host network) per docs/agents/evidence/2026-10-19-epic7-commerce-client/README.md.
- Category seed sanity: `curl -s "http://localhost:8080/v1/projects/demo-schoolquiz-mvp/databases/(default)/documents/catalogs/private-quizzes-v1-catalog"` -- expected: doc with name «Приватные квизы».

**Manual checks (if no CLI):**
- Gradle discipline: every gradle invocation through `sgw` (global flock); never raise daemon memory (AGENTS.md).
- Evidence dirs contain command transcripts, not claims; tickets.toml statuses updated in the same commits they close.
