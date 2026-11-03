---
title: 'Epic 8 (social features) — finish 8.3, build 8.4/8.5/8.6: private quizzes under AD-29 and history/earnings projections'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_revision: '6ac52f2f9c44f0d63818af3dc77552d1e8ea437d'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
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
- [x] `shared/feature/quest-authoring/domain/.../model/QuestDraft.kt` -- add `PrivateQuizSettings {attemptLimit>=1=1, showResult=false, linkExpiresAtMs?=null}` with init{} validation (limit integer ≥1; expiry null or in the future at save time), sunk into the draft for the private category -- AD-29 defaults live in the domain model, not the UI.
- [x] `shared/feature/quest-authoring/data/.../outbox/QuestArenaOutboxWriter.kt` + `data/remote/QuestArenaSubmissionRequest.kt` (ArenaDraftDto + mapper) -- carry `privateQuiz` settings in the `draft{}` payload for private-category submissions -- single pipeline, no parallel mechanism.
- [x] `android/feature/quest-authoring/presentation/.../QuestCreateScreen.kt` + `DefaultQuestCreateComponent.kt` + `QuestCreateUiState.kt` -- private-category branch: hide Розділ/Тема/Урок pickers (flat HARD-only set, SURVEY excluded), show attempt-limit/show-result/expiry controls with AD-29 defaults; for an EXISTING quiz add the manual close (enabled=false) control; relax `hasValidStructureTitles` for private -- the 8.3 device-proof gap named in tickets.toml.
- [x] `functions/index.js` (`privateDocuments` ~6397) -- persist `privateQuiz {attemptLimit, showResult, linkExpiresAtMs, enabled:true, startedCount:0}` on the private quest doc via the gate's `normalizeSettings`; on RESUBMISSION of an existing private quest update settings fields but PRESERVE runtime `startedCount`/`enabled` (merge, not overwrite) -- limit changes preserve startedCount (AD-29).
- [x] `shared/feature/quest-authoring/data/.../PrivateQuestMerge.kt` / `QuestPrivateSync.kt` / `OpenQuestDraftUseCase.kt` -- round-trip: loading an existing private quiz restores saved settings into the form -- «loading an existing quiz keeps its saved settings».
- [x] `functions/private-quiz-gate.test.js` (extend) + new `functions/private-quiz-settings.test.js` -- settings persisted with defaults; resubmission preserves startedCount/enabled; validation refusals (limit 0, past expiry at save); sync returns settings to author/participant readers.
- [x] `shared/feature/quest-authoring/domain` jvmTest + `android/feature/quest-authoring/presentation` unit tests -- settings validation; UI-state for private category (pickers hidden, controls present, defaults).
- [x] `docs/agents/evidence/<date>-private-quiz-authoring/` -- evidence + DEVICE-PROOF RECIPE for the owner (Samsung, category seeded): form adapted, defaults correct, reload preserves settings, close-link refuses new starts.

8.4 — private quiz run: limits and visibility:
- [x] `functions/private-quiz-gate.js` (+ `.test.js`) -- extend the start record into the run contract: after a passing door, the returned record carries the frozen quest identity + attempt deadlines basis; ensure deadline fields derive from the original start (original question deadline runs through disconnection; no extension on recovery) -- the gate is the only door; original intent fixed before dispatch (AD-6).
- [x] `shared/feature/lesson-runner/domain` (new use-case/repository alongside `StartLessonAttemptUseCase.kt` / `ArenaEntryRepositoryImpl.kt` pattern) -- private-quiz entry: call `startPrivateQuizAttempt`, map refusals to typed entry states (LINK_DISABLED/LINK_EXPIRED/ATTEMPT_LIMIT_REACHED), on success run the quiz through the existing ONLINE-O2 runner (ArenaOnlineO2Adapter timing, question windows) with the delivered private snapshot -- no shared mode controller; inherits no exam fee/timer/pass-fail.
- [x] `functions/attempt-scoring.js` / `attempt-intake.js` (+ tests) -- private timeout path: when the recovery window ends, terminate with reason timeout and score the WHOLE frozen quiz (all fixed questions; remaining incl. unissued = unanswered '0'); skips never count as paid-correct -- AD-29 timeout rule; exam-terminal.js precedent.
- [x] `functions/` result read path + `android/feature/lesson-runner/presentation` (RunnerUiState.Result / ResultContent.kt) -- showResult gate: CURRENT setting governs every own result read/retry incl. past attempts; false → completion confirmation only (no counts/percent/questionScores, never fake zeros); true → own result only; author statistics read stays available under the uid-scoped callable pattern (`arena-lifecycle-views.js` `protectedRead`) -- server-side authorization, UI hiding never substitutes.
- [x] `functions/private-quiz-run.test.js` (new, node, emulator) -- end-to-end against the demo-backend shape: start→limit consume→retry idempotent→finish→own-result visibility for both flag values; limit raise 1→3 admits 2 more; close link mid-attempt lets the started attempt finish; timeout scores whole quiz.
- [x] `android/feature/lesson-runner` + entry-screen unit tests -- refusal states rendered; completion-only result rendering when showResult=false.
- [x] `docs/agents/evidence/<date>-private-quiz-run/` -- evidence + DEVICE-PROOF RECIPE (two devices + timeout case).

8.5 — negative proof (no device needed):
- [x] `functions/private-quiz-negative.test.js` (new) -- behavioral: across submission/start/run/timeout, `audit/currency_ledger/entries` gains ZERO rows; no ATTEMPT_TOLL/CHARGE_CLAIM/REVIEW_FEE reasons; consent never requested for private ops -- asserts the no-charges rule against the real flow.
- [x] `functions/private-quiz-negative.test.js` (static fence, `premium-neutrality.test.js` style) -- static assertion that private-quiz server files import no charge/fee/ticket modules -- «забор, а не описание».
- [x] `shared/core/outbox/src/jvmTest/.../OutboxOperationsRegistryTest.kt` (extend) -- private-quiz ops (if any new op is introduced) are NOT in termsConsentOperations and agree bidirectionally with the server registry -- poll SUBMIT_POLL_VOTE precedent.
- [x] `shared/feature/lesson-runner/domain` jvmTest -- no deferred-continuation type/state is introduced for private quizzes; terminal timeout is a terminal result, not a resumable state -- AD-31 boundary.
- [x] `docs/agents/evidence/<date>-private-quiz-negative/` -- evidence.

8.6 — operation history and earnings read projections:
- [x] `_bmad-output/mvp-candidate/epic-social-features/ux-o10-own-ledger-projection-contract.md` (new, CONTRACT FIRST) -- permitted fields (identity/date/reason/signed amount/resource/status), bounded order + lexicographic cursors (atMs,id), pending-vs-confirmed distinction, resources never summed, no money commands, redaction; mirror the ux-o7-poll-contract.md format and cite gate wording UX-O8/O10/O12 -- «O10 fixes permitted fields and bounded continuation before adapters».
- [x] `functions/arena-lifecycle-views.js` (+ new `functions/own-history-views.test.js`) -- own-history view callable: uid-scoped read over `audit/currency_ledger/entries` (+ pending claim/ticket sources), `startAfter(atMs,id)` bounded pages with continuation, permitted fields only; author per-lesson «Начисления» reuses `listMyRoyalty` + lesson-scoped filter -- one authorized projection family, no new ledger facts.
- [x] `shared/feature/economy` (or sibling module following TZ06B client half from 1db295c4) -- projection client: repo + paging over the view callables, per-owner cursor ids + `AccountSwitchGuard`/epoch recheck (switch cuts off stale responses) -- reading creates no financial effect.
- [x] `android/feature/app-shell/presentation/.../ui/drawer/DrawerSectionList.kt` + new history screen (RU/UK/EN strings) -- «История операций» drawer entry: date groups, Все/Начисления/Расходы filters, expandable record (signed amount, resource icon/color, status), distinct loading/empty/error; per-lesson «Начисления» entry from my-quests lesson surface -- UX-20 / F9 contract.
- [x] JVM/Android unit tests -- projection mapping (pending excluded from confirmed income, resources not summed), filter/group logic, empty/error states; node view tests -- pagination bounds, cursor continuation, uid scoping (foreign uid → forbidden/empty per contract), unauthenticated refusal.
- [x] `docs/agents/evidence/<date>-history/` -- evidence + DEVICE-PROOF RECIPE (account A→B→A reveals nothing foreign).
- [x] `_bmad-output/mvp-candidate/epic-social-features/tickets.toml` -- fix stale TZ06B remark (4.2 done: 3dca0ef1, 1db295c4) and set story statuses as they close.

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

### 2026-10-22 — Review pass
- verdicts: 53 findings — high 4, medium 18, low 22, false 4, maybe-false 3
- findings:
  - `[high]` `[patch]` b5: failed composite start discards startId (door consumed, fetchRun failed → Refused(UNAVAILABLE); component records startId only on Ready) → re-entry mints a new startId and spends a second attempt — verified: StartPrivateQuizAttemptUseCase KDoc demands the caller hold the id until entry resolves; DefaultPrivateQuizRunnerComponent sets it only on Ready. Fix: machine owns the startId from the first door call and retries the SAME key until Ready or a door refusal.
  - `[high]` `[patch]` b6: currentStartId lives on the component while the machine survives in InstanceKeeper; recreated component's init calls openDoor with a blank id — rotation mid-run re-opens the door (new key, second attempt) or leaves a dead scope (machine's scope is the destroyed component's). Fix: machine owns startId and a retained scope; openDoor no-ops outside DoorChecking/initial.
  - `[high]` `[patch]` b7: restoreStack filters out only LessonRunner; PrivateQuizRunner survives process death in the restored stack and auto-mints a new attempt, contradicting its own KDoc (pointer-only, dropped on restore). Fix: filter QuizzesConfig.PrivateQuizRunner in restoreStack like LessonRunner (AC 36 analog).
  - `[high]` `[patch]` v2 (verification-gap, pre-verified): the two new ledger queries (uid+atMs+__name__; uid+reason+details.lessonId+atMs+__name__) have no composite indexes in firestore.indexes.json — emulator/stub do not enforce indexes, so all suites stay green while the first production call of listMyOperationHistory/listMyLessonEarnings fails FAILED_PRECONDITION. Verified against firestore.indexes.json (only the royalty shape exists). Fix: add both index entries.
  - `[medium]` `[patch]` b2: submission replay returns projectOwnResult(showResult=true) hardcoded — current showResult must govern every own read/retry (AD-29); replay under a hidden flag leaks percent/questionScores into the mutation receipt. Fix: normalize settings before the terminal check and project with settings.showResult.
  - `[medium]` `[patch]` e3: finishAttempt TIMEOUT branch scores submission.answers — a submission delivered past the deadline gets its late answers credited, violating "remaining questions unanswered" at timeout. Fix: score the timeout terminal with empty answers and pin with a test.
  - `[medium]` `[patch]` e4: previousPrivateQuizSettings is read before db.runTransaction; a gate start landing in that window increments startedCount and the resubmission transaction then clobbers it (gate's merge-set does not bump the quest version, so the version check does not catch it). Fix: read previous settings inside the transaction.
  - `[medium]` `[patch]` b4: OwnHistoryRepositoryImpl checks currentUid only before the call; a page requested under A and answered after a switch to B is parsed and rendered — KDoc claims the opposite. Fix: re-check currentUid after the call, before parsing.
  - `[medium]` `[patch]` b8: per-lesson «Начисления» has no production caller anywhere (server callable, transport, use case all built; zero UI entry) — spec task "per-lesson entry from my-quests lesson surface" not delivered; the 8.6 device recipe step 5 is unexecutable. Fix: add the lesson-surface entry opening the history screen scoped by lessonId.
  - `[medium]` `[patch]` b11: HistoryRecordCard renders raw server enums (reason/currency/status) with no string resources, no expand/collapse, no lessonId, no resource icon/color — UX-20/spec AC "expandable record" and the RU/UK/EN rule are not met on the one screen where they bind. Fix: localized labels, expandable row, lessonId line, resource color.
  - `[medium]` `[patch]` b12: loading an existing quiz converts linkExpiresAtMs to ceil(days).coerceAtLeast(1) — a sub-day expiry silently displays and resubmits as 1 day, extending the author's deadline; "loading keeps saved settings" broken for <24h expiries. Fix: keep the saved instant; render remaining time informationally; resubmit the original instant unless the author edits the control.
  - `[medium]` `[patch]` b13: DefaultOwnHistoryComponent.load replaces the whole state with OwnHistoryUiState.error() on any failure — a failed load-more wipes loaded groups and silently ends pagination (canLoadMore turns false). Fix: failure updates the existing state (error flag + isLoadingMore=false), keeping groups/cursor.
  - `[medium]` `[patch]` b14: retryability decided by error.message?.contains("OutboxStore") — string-matching an exception message; ReadFailed conflates "queue refused the intent" with "result read failed" and shows the submit-failure text for read failures. Fix: typed failure source (submit vs read) driving state and copy.
  - `[medium]` `[patch]` e15: after a failed enqueue (ReadFailed), onRefreshResult retries readResult only — the lost submission is never re-enqueued and the attempt dead-ends until the server timeout. Fix: keep the frozen submission in the machine until enqueue succeeds; refresh re-enqueues it first.
  - `[medium]` `[patch]` b1: the 8.4 device recipe (author A, participant B) is unexecutable as written — the client list is built from the signed-in user's own drafts only, so B has no discovery surface (share mechanism unassigned by AD-29). Fix: rewrite the recipe for the same signed-in account on two devices (AD-29 permits; startedCount counts all starts) and record the participant-share mechanism as an open owner question; b9 folded here — the author-statistics recipe step is restated at callable level (no statistics UI exists; the AC is callable-level and already covered by node tests).
  - `[medium]` `[patch]` v1 (verification-gap, pre-verified): nothing links the royalty producer to the earnings consumer — arena-tickets.test.js never reads details, own-history-views.test.js hand-feeds rows; deleting details.lessonId at arena-tickets.js:354 would empty «Начисления» with all suites green. Fix: one assertion on the settled royalty row's details.lessonId (or one settlement-fed own-history case).
  - `[medium]` `[patch]` v4 (verification-gap, pre-verified) — same root as b8; shares G8's fix.
  - `[medium]` `[patch]` e2/v-other1: duplicates of b2 (replay showResult) — share G4's fix.
  - `[medium]` `[patch]` e5: duplicate of b4 (fence pre-call) — shares G7's fix.
  - `[medium]` `[patch]` e6: duplicate of b12 (expiry coerce) — shares G11's fix.
  - `[medium]` `[patch]` e8: duplicate of b8 (lessonEarnings unreachable) — shares G8's fix.
  - `[medium]` `[patch]` e10: duplicate of b13 (load-more wipe) — shares G10's fix.
  - `[low]` `[patch]` b3: the wire parser requires "reason" for own results, but the server's running shape is {status:"running"} — current flows mask it (getOrNull → retry → ResultPending), yet the contract hole traps the next caller; the unit test mis-models running with a reason. Fix: lenient reason read + test modeling the real running shape.
  - `[low]` `[patch]` b9: folded into b1's recipe fix (see above).
  - `[low]` `[patch]` b10: RunnerUiState.Result.privateCompletionOnly has no production setter (the private run renders its own FinishedCard) — dead branch whose KDoc claims it carries the server's word. Fix: remove the dead field/branch/test or wire it; removal is smallest.
  - `[low]` `[patch]` b16: NEXT_QUESTION_WINDOW_MS = 60_000 re-declares the server's constant with a comment only — the unguarded-mirror pattern epic-2 retro flagged. Fix: a JVM guard test reading functions/private-quiz-run.js and pinning both constants.
  - `[low]` `[patch]` b17: QuestionTimer reads System.currentTimeMillis() while the machine runs an injected Clock, and formatDay uses TimeZone.currentSystemDefault() while grouping uses the injected zone — grouping and rendering can disagree. Fix: share the machine's clock/zone.
  - `[low]` `[patch]` b20: the drawer «История операций» section is visible signed-out, where the use case can only fail — first-run guests see an error card with a retry that cannot succeed. Fix: gate the section on session presence.
  - `[low]` `[patch]` b22: evidence README header still reports private-quiz-run 8/8 after the second pass made it 9/9 (transcripts keep the first-pass record — historical, correct). Fix: update the header line. (Its second half — spec frontmatter in-progress with unchecked boxes — is false: live workflow state, finalized in this step.)
  - `[low]` `[patch]` e7: duplicate of b16 (60s mirror) — shares G17's fix.
  - `[low]` `[patch]` e12: duplicate of b17 (timezone) — shares G18's fix.
  - `[low]` `[patch]` e13: duplicate of b3 (running parse) — shares G13's fix.
  - `[low]` `[patch]` v3 (verification-gap, pre-verified): no test drives the private-catalog entry path (isPrivateCatalog branch → observePrivateQuests rows → onPrivateQuestClick pushes QuizzesConfig.PrivateQuizRunner); existing suites only pass error-stub factories. Fix: one DefaultQuestListComponentTest case following the module's navigation-test pattern.
  - `[low]` `[patch]` v-other2: duplicate of b10 — shares G16's fix.
  - `[low]` `[patch]` v-other3: duplicate of b20 — shares G19's fix.
  - `[low]` `[patch]` v-other4: duplicate of b16 — shares G17's fix.
  - `[low]` `[patch]` i2: duplicate of b22 (evidence header lag) — shares G20's fix.
  - `[low]` `[reject]` b15: hardcoded PRIVATE_QUIZ_CATALOG_ID — the category id is fixed by the seed decision («технические IDs названием не меняются»); a client constant mirrors the repo's existing catalog-id convention; a server-driven flag was never assigned. Not worth a discovery mechanism.
  - `[low]` `[reject]` b18: onBack()/RunnerEvent.NavigateBack has no collector, but system back works via the component's backCallback pop — dead interface method only; no user-facing harm.
  - `[low]` `[reject]` b19: answers[index] = drafts[index]?.takeIf { false } is an obfuscated null write — cosmetic; a clarity nit, not a defect.
  - `[low]` `[reject]` b21: N+1 transactional collection of frozen questions — author-authored flat sets are small; restructuring storage exceeds any demonstrated harm.
  - `[low]` `[reject]` e11: stale pending card persists mid-pagination until refresh — self-heals; not worth state machinery.
  - `[low]` `[reject]` e17: readPrivateQuizStatistics reads all starts unbounded — author-scoped own data, small in practice; capping adds machinery without demonstrated harm.
  - `[low]` `[reject]` b23: client pending parser ignores the constant status field — server always sends "PENDING" on pending rows; no divergence is representable.
  - `[low]` `[reject]` b24: pending tickets fetched without a query limit per page — RESERVED tickets are few and short-lived; the lifeCost→STANDARD_CHARGE_POINTS mapping matches the exam-toll semantics.
  - `[maybe-false]` `[reject]` e1: zero-delta ledger row failing the client parse — no zero-delta writer was shown to exist (derived idempotent ids, isNew guard); if one appeared the harm would be low (one loud page error). Would settle: a produced zero-delta row.
  - `[maybe-false]` `[reject]` e9: duplicate keyed ids crashing LazyColumn after overlapping pages — no overlapping-page path was demonstrated (startAfter cursors); if-true harm low. Would settle: a concrete duplicate-emitting load sequence.
  - `[maybe-false]` `[reject]` e19: malformed saved privateQuizSettings silently dropping settings client-side — the server writer normalizes on every write; a malformed doc has no demonstrated producer. Would settle: a writer that persists a non-numeric limit.
  - `[false]` `[reject]` e14: empty-schedule finish crash — beginRun early-returns with ReadFailed before schedule is left empty, and onAnswer/onTimeout guard reachedIndex >= 0; finish() is unreachable with an empty schedule.
  - `[false]` `[reject]` e16: slash-bearing path segments forking document paths — every read/write through readRunAddress requires a start record that only the gate creates, under gate-validated ids; a crafted fork cannot coincide with one.
  - `[false]` `[reject]` e18: close-link on a quest without privateQuizSettings writes defaulted settings — the fields have no reader for a non-private quest; a harmless no-op scoped to the author's own doc.
  - `[false]` `[reject]` i1: spec frontmatter in-progress with unchecked boxes while tickets.toml says done — live workflow state of this very run; the spec is finalized (status, boxes, Auto Run Result) at this step, not a code defect.

### 2026-10-22 — Patch pass
- All 20 patch groups applied by the step-03 implementation subagent in commit 283537bc (pushed): G1 machine owns startId + retained scope + openDoor guard; restore filter drops PrivateQuizRunner; G4 replay projects current showResult; G5 timeout scores empty answers (pinned); G6 previous settings read inside the transaction; G7 post-response uid re-check; G8 «Начисления» entry (LessonEarnings child + author-path LessonList row); G9 localized expandable record card with resource dot/color and lessonId; G10 in-place load failure; G11 saved expiry instant preserved; G2 typed ReadFailed source with re-enqueue; G12 recipe rewritten same-account/two-devices + owner question + 9/9 header; G13 lenient running parse; G14 royalty details.lessonId assertion; G15 entry-path component test; G16 dead privateCompletionOnly removed; G17 window mirror guard test; G18 shared clock/zone; G19 drawer section gated on session; G20 folded into G12. Verification re-run on the patched tree: node 128/129 (only the inherited mvp-isolation pin), gradle BUILD SUCCESSFUL across all spec targets plus quizzes-screen/app-shell/lesson-runner presentation, economy data, persistence; diff rewritten to /tmp/epic8-social-features-baseline-6ac52f2f-final.diff.

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

## Auto Run Result

Status: done (code+tests bar per tickets.toml scope rule; HITL device proofs remain the owner's acceptance steps)

### Summary

Epic 8 (epic-social-features) finished in one full-route run: 8.3 private-quiz authoring under AD-29 (settings through the single SUBMIT_ARENA pipeline, flat HARD-only form, close-link mutation, Room v18); 8.4 private quiz run (start gate as the only door, frozen-run serve with server-held translation keys, single-sitting ONLINE-O2 machine, outbox submit keyed by startId, current-showResult result projection, author statistics callable); 8.5 negative proof (behavioral ledger-absence over real handlers + static money-vocabulary fences + registry fences); 8.6 history/earnings (own-ledger projection contract written first, listMyHistory/listMyLessonEarnings in the TZ06B family with scoped encrypted cursors, economy client half with post-response session fence, drawer «История операций» + per-lesson «Начисления» surfaces). Stories 8.1/8.2 (polls) were already done before dispatch.

### Commits (baseline 6ac52f2f9c44f0d63818af3dc77552d1e8ea437d)

- 45f7e9c8 — epic 8.3–8.6 full story set from this spec (134 files)
- 620b3b84 — 8.4 runnable from the UI: quizzes-screen entry → door → run → outbox submit → result (38 files)
- 287f4ada — app-shell integration-test fixture updated for the new factory param
- 283537bc — review patch pass: all 20 groups (attempt-key ownership, timeout honesty, earnings entry, session gate, indexes, …)
- e029952f, 92658ac3 — swept-in retrospective docs of adjacent wave sessions (docs-only, landed inside the run's commit stream)

### Files changed (grouped)

- functions/ — private-quiz-gate (frozen identity, machine door codes), private-quiz-run (deadlines/terminals/projections), index.js (settings persistence, run callables, SUBMIT_ATTEMPT, CLOSE_PRIVATE_QUIZ_LINK, review-fee exemption), arena-lifecycle-views (listMyHistory/listMyLessonEarnings), arena-tickets (details.lessonId provenance), own-history/gate/run/settings/negative tests, firestore.indexes.json (2 new composite indexes)
- shared/feature/quest-authoring — PrivateQuizSettings domain + validation, wire/dto/outbox payload, close-link use case, settings tests
- shared/feature/lesson-runner — entry use-case/refusals, run/submission models, wire parser (lenient running shape), repository with door-code mapping, submit outbox writer, contract tests
- android/feature — quest-authoring form adaptation (flat HARD-only, link-settings card, close link, saved-instant expiry), quizzes-screen private list + LessonEarnings child + runner route/restore, lesson-runner presentation PrivateQuizRunMachine (InstanceKeeper-owned startId/scope) + runner screen, app-shell drawer section (session-gated) + history screen (localized expandable records, shared clock/zone)
- shared/core — outbox registry (2 ops in permanent, none in terms consent, bidirectional fences), persistence Room v18 + migration + owner-scoped guards
- platform/firebase — private-quiz + own-history remote data sources with callable-names guards
- _bmad-output/mvp-candidate/epic-social-features — tickets.toml statuses (8.3–8.6 done, TZ06B remark corrected), ux-o10-own-ledger-projection-contract.md (contract-first)
- docs/agents/evidence/2026-10-22-* — four evidence dirs with transcripts and device-proof recipes

### Review findings breakdown

53 findings from 4 lenses (blind-hunter, edge-case-hunter, verification-gap, intent-alignment): high 4, medium 18, low 22, false 4, maybe-false 3. Patched: 38 findings in 20 groups (see Patch pass above; commit 283537bc). Deferred: 0. Rejected with reasons: 15 — e16 (forked doc paths unreachable: gate-created starts under gate-validated ids), e18 (close-link on non-private quest is a reader-less no-op), e14 (empty-schedule finish unreachable: beginRun early-return + reachedIndex guards), i1 (live workflow state, finalized here), b15 (catalog id constant mirrors seed decision + repo convention), b18 (dead onBack method only; system back works), b19 (cosmetic null-write), b21 (N+1 reads at author scale), e11 (stale pending self-heals), e17 (author-scoped bounded-in-practice stats read), b23 (status field constant), b24 (reserved tickets few; mapping matches toll semantics), e1/e9/e19 (maybe-false, if-true harm low; what would settle each is recorded in the triage log).

### Follow-up review recommendation

Recommended: TRUE — two high entries were patched (attempt-key/InstanceKeeper lifecycle rework in the run machine; production Firestore composite indexes). Unverified risk: the reworked machine lifecycle (rotation, process death, retry after UNAVAILABLE) and the new «Начисления» surface are exercised only by JVM/Node tests — they meet real devices only at the owner's HITL proof; the new composite indexes were never checked against an index-enforcing Firestore (stub and emulator both skip index enforcement) — first production deploy of the two callables must be watched.

### Verification performed

- `cd functions && node run-tests.js` in container schoolquiz-functions-emu: 128/129 suites green before AND after the patch pass; the single red is `mvp-isolation.test.js` — inherited from the 5.13 rig session's NSC LAN IP (baseline already carried it; no file it reads is touched by this diff).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew` — quest-authoring domain/data/presentation, lesson-runner domain/data/presentation, outbox, quizzes-screen presentation, app-shell presentation, economy data, persistence: BUILD SUCCESSFUL before and after patches (one verification fix on my side: QuizzesRootIntegrationTest fixture gained the new factory param, commit 287f4ada).
- Matrix Test Audit: all 13 I/O-matrix rows covered by tests that ran and passed (node: private-quiz-gate 8, settings 8, run 9, negative 5, own-history 5, arena-tickets; JVM: PrivateQuizSettings 5, Wire 6, EntryContract 3, RunWireContract 8, SubmitOutboxContract 4, RunMachine 4, OwnHistoryRepository 3, QuestCreateUiStatePrivateCategory 5, OutboxOperationsRegistry 6, mirror guard, names guards 3+2).
- Category seed sanity via Firestore REST: catalogs/private-quizzes-v1-catalog live («Приватные квизы», REGULAR) — the 22.10 memory note «0/49» is stale.

### Residual risks

- HITL device proofs are the owner's: 8.3 authoring screen (recipe now same-account/two-devices), 8.4 two-device + timeout, 8.6 account A→B→A; recipes in the evidence dirs.
- Participant discovery/share mechanism is unassigned by AD-29 decisions — the client lists only the signed-in owner's drafts; recorded as an open owner question in the 8.4 recipe.
- Inherited reds unrelated to this work: mvp-isolation.test.js (NSC pin), FirebaseArenaTicketRemoteDataSourceTest (documented 2026-10-19 fixture pin).
- The wave's retrospective docs (epics 1–3) committed by adjacent sessions landed inside this run's commit stream (docs-only).
- Per the workflow, the finalize commit is not pushed; the branch head 283537bc is pushed.
