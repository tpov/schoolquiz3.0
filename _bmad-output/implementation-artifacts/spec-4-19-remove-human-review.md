---
title: '4.19 remove-human-review — удалить код живых проверяющих (UX-17)'
type: 'refactor'
created: '2026-10-27'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '05020bc8'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** The model pipeline (4.12–4.18) owns the review: verdicts, translation, author outcome, fee burn, admin Decisions. The human-reviewer world it replaced still ships in full — reviewer screens, star scores and the 1..3 threshold, per-segment translation review, reviewer assignment generation/journal, reviewer reward units — dead weight that UX-17 orders removed («there are no reviewer screens, star ratings, threshold or segment translation review, and their code is removed»).

**Approach:** Delete the reviewer world on both sides. Server: the four reviewer callables (`submitReviewAction`, `fetchReviewAssignments`, `fetchReviewAssignmentChanges`, `rejectReviewSubmission`), the nightly assignment reconcile schedule, the `quest_authoring.SUBMIT_REVIEW` queue handler, assignment generation at submission intake, the human publish/settle branches, and the reward-unit builders in `review-fee.js` (the fee charge, burn and conservation arithmetic stay untouched — «fee burn code stays»). Client: the ReviewQueue screen family, assignment domain/data/sync/outbox, Room entities (DB v19 migration drops the two tables), the Firebase transport, the drawer section and strings; the wire stops carrying the author's star-summary `review` field (question content and its `languageLevel` stay). Decisions (4.17) is the review surface that remains. Every deleted file is listed below (testing.md: «Never delete test files unless listed…»); a negative whole-tree scan test locks the removed names out.

## Boundaries & Constraints

**Always:**
- testing.md: every deleted file listed explicitly in this spec's Deleted Files section; deleted suites named in the report.
- The removal rule («никаких упоминаний ролей живых проверяющих») is locked by a counted negative scan (testing.md «Отрицательное утверждение о всём дереве»): the removed shared names may not reappear in `functions/` or the client source trees; the scan counts scanned files and fails on a suspiciously small count.
- Callable-name sentinels stay honest: the export-set hash pin is re-pinned (pattern of 4.11/4.16/4.17); the outbox registry sentinel (`OutboxOperationsRegistryTest` ↔ `MUTATION_HANDLERS`) drops `SUBMIT_REVIEW` on both sides; `deferred-actions.test.js` drops its `SUBMIT_REVIEW` row.
- Legacy data renders honestly: lifecycle view readers for in-flight `failedStages` (TESTING/LOGIC/TRANSLATION) and `questionOutcomes` stay — they read documents that exist; the writers die.
- Fee: charge, tariffs, quote, burn settlement and the conservation manifest stay byte-for-byte in behavior; model-world settlement was already units=[] (4.18) and the human branch was the only other writer of units.

**Never:**
- No curator transitions (4.8), submission queue/intake, private inbox (4.9), views/push infrastructure, Decisions tab (4.17), `requireVerifier` admin gate, or fee-burn code changes.
- No profile API shape changes: `readQualification` keeps serving `testerLevel`/`translatorLevel` — a legacy profile surface consumed by the profile feature and stats; its only quest-review consumer was the removed reviewer world. `addReviewerReputation` stays (moderation reports pay through it, +3/−3).
- No live model calls, no device journey (4.20), no proposals (4.21+), no deploys.

</frozen-after-approval>

## Deleted Files (testing.md — explicit list)

**functions (5):**
1. `functions/testing-review-threshold.test.js` — 4.6 stars/threshold/segment suite (18 tests over `submitReviewAction`).
2. `functions/review-fee-cycle.test.js` — human fee cycle end-to-end (5 tests via `submitReviewAction`/`rejectReviewSubmission`).
3. `functions/review-journal.js` — assignment journal page + assignment resolve/heal chain.
4. `functions/review-journal.test.js` — its suite.
5. `functions/review-journal-wire.test.js` — source-wire guard for the journal and assignment lookup.

**domain (13):** `shared/feature/quest-authoring/domain/src/commonMain/.../domain/model/ReviewAssignment.kt`, `ReviewAssignmentKind.kt`, `ReviewAssignmentSyncState.kt`, `ReviewChecks.kt`, `ReviewQuestion.kt`, `ReviewQuestionScore.kt`, `ReviewSegmentDecision.kt`, `SubmitReviewActionCommand.kt`, `ReviewActionSubmission.kt`; `domain/repository/ReviewAssignmentRepository.kt`; `domain/use_case/ObserveReviewAssignmentsUseCase.kt`, `ObserveReviewAssignmentSyncUseCase.kt`, `SubmitReviewActionUseCase.kt`.

**data (6 + 2 tests):** `data/remote/ReviewAssignmentRemoteDataSource.kt` (interface + DTOs), `data/ReviewAssignmentLocalDataSource.kt`, `data/ReviewAssignmentRepositoryImpl.kt`, `data/sync/ReviewAssignmentSync.kt`, `data/outbox/SubmitReviewOutboxWriter.kt`, `data/outbox/ReviewActionQuarantine.kt`; tests `data/ReviewAssignmentRepositoryImplTest.kt`, `data/sync/ReviewAssignmentSyncTest.kt`.

**persistence (4 + 1 test):** `shared/core/persistence/.../ReviewAssignmentDao.kt`, `ReviewAssignmentEntity.kt`, `ReviewAssignmentQuestionEntity.kt`, `ReviewAssignmentWithQuestions.kt`; `src/androidInstrumentedTest/.../ReviewAssignmentDaoTest.kt`.

**platform/firebase (2 + 1 test):** `quest_authoring/FirebaseReviewAssignmentRemoteDataSource.kt`, `quest_authoring/ReviewAssignmentChangePage.kt`; test `quest_authoring/ReviewAssignmentChangePageTest.kt`.

**presentation (6 + 2 tests):** `component/ReviewQueueComponent.kt`, `DefaultReviewQueueComponent.kt`, `PlaceholderReviewQueueComponent.kt`, `screen/ReviewQueueScreen.kt`, `screen/ReviewQueueView.kt`, `uistate/ReviewQueueUiState.kt`; tests `component/DefaultReviewQueueComponentTest.kt` (src/test), `screen/ReviewQueueViewTest.kt` (src/androidTest).

**app (1):** `apps/android-next/src/androidTest/.../LiveFirebaseReviewWorkflowInstrumentedTest.kt` — live human-review journey (submit → assignment → tester scores → segment review → publish).

Total: 43 files deleted.

## Code Map

- `functions/index.js` — remove exports `submitReviewAction`, `fetchReviewAssignments`, `fetchReviewAssignmentChanges`, `rejectReviewSubmission`, `reconcileQuestReviewDaily` (+ `applySubmitReviewAction`, `reconcileChangedReviewLessons`, the whole reviewer helper block: `toAdminLessonTasks`, `adminDocuments`, `toAssignmentDto`, `availableTasks`, `canSubmit`, `translationTargets`, `openTasksFor*`, `isStageOpenForSubmit`, `rebuildAggregate`, `testingQuestionOutcomes`, `lessonReviewStatus`, `failedReviewStages`, `lessonPassed`, `scoreAggregate`, `translatedLanguages`, `requiredLanguages`, `actionToRecord`, `translationReviewerDeltas`, `newReviewerDeltas`, `deltaKey`, `readAdminReviewLessonTask(s)ByIds`, `stampAssignmentIds`, `adminLessonSnapshotToTask`, `readReviewRecords`, `readArenaReviewConfig`, `normalizeSubmitAction`, `normalizeSegmentDecision`, `validatedTestingQuestionScores`, `normalizeReviewRecord`, `reviewToChecks`, `checksToCallableMap`, `reviewRecordToDocument`, `levelFor`, `getSourceQuestionCount`, `availableLanguages`, `hasTestingResult`, `hasLogicResult`, `stagePassed`, `isReadyForTranslation`, `hasTesting/LogicQualification`, `emptyTranslationTargets`, `initialFailedStages`, admin path helpers, `MUTATION_HANDLERS` SUBMIT_REVIEW row). `processArenaRequest` stops generating assignments. `publishSubmissionIfReady` drops the human branch (no `reviewOutcome` → stays UNDER_REVIEW awaiting the worker). `settleReviewFeeCycle` drops the reviewer-units branch (units always `[]`, wallets always `[]`). `loadArenaLessonContext`/`loadArenaLifecycleContext`: `reviewApproved` now proven by the lesson doc (`reviewSubmissionId` + `reviewApprovedAtMs`) — the admin-task cross-check dies with the task world; this also unblocks arena admission for model-published lessons (latent 4.16 gap, recorded in the change log). Submission wire drops the `review` field (parse, request doc, private doc).
- `functions/review-fee.js` — delete `buildReviewUnits`, `rejectionUnit`, `recordWinsLevel`, `completedRecord` (+ module-local `carriesContent`, `sourceQuestionCount` if unused after); keep tariffs/quote arithmetic, `settleReviewFeeTenths`, `walletCreditForEntitlement`, `buildSettlementManifest`; doc comment updated honestly (no reviewer units — the model cycle burns the whole fund, 4.18).
- `functions/review-fee.test.js` — drop the 5 unit-builder tests (merge/tie/incomplete/foreign-submission/rejection-judge); keep arithmetic/conservation/wallet/manifest tests.
- `functions/quest-review-fee-burn.test.js` — drop the human-cycle mirror case; keep the 4 model-world cases.
- `functions/deferred-actions.test.js` — drop the `SUBMIT_REVIEW` row.
- `functions/arena-lifecycle-views-handler.test.js` — export-set hash re-pinned (5 exports fewer).
- `functions/arena-request-document.test.js` — request-document parity re-pinned without `review`.
- **New** `functions/remove-human-review-scan.test.js` — counted negative scan over `functions/*.js` + client source roots for the removed shared names (`submitReviewAction`, `fetchReviewAssignments`, `fetchReviewAssignmentChanges`, `rejectReviewSubmission`, `reconcileQuestReviewDaily`, `quest_authoring.SUBMIT_REVIEW`, `testingPassScore`, `buildReviewUnits`, `TRANSLATION_REVIEW`, `segmentResults`, `review_assignment`, `ReviewAssignment`, `ReviewQueue`, `section_review_queue`, `qa_review_`), skipping `._*` and `build/`; asserts scanned-file count above a floor; excludes only itself.
- Client edits: `OutboxOperations.kt` (drop `SUBMIT_REVIEW`) + registry/producers/namespace tests; `SyncModule.kt` (quarantine row, sync cursor list, `ReviewAssignmentSync` wiring); `FirebaseModule.kt` binding; `NamedReadFailures.kt` (+ its two tests, `SyncRunFakes`) review delegates; `QuestAuthoringMapper.kt` assignment mappings; `QuestAuthoringDataModule.kt`/`DomainModule.kt`/`PresentationModule.kt` unbinds; `QuestArenaSubmissionRequest.kt` + mapper + mapper test drop `review`/`ArenaReviewDto`; `AppDatabase.kt` v19 + **new** `Migration18to19.kt` (DROP TABLE ×2) + `PersistenceModule.kt` + `OwnerRowsDao.kt` + `OwnerTracesPersistenceTest.kt`; app-shell `DrawerSection.kt`, `TabConfig.kt`, `Visibility.kt`, `AppShellTransitions.kt`, `Labels.kt`, `AppShellScreen.kt`, `DefaultRootComponent.kt`, `AppShellPresentationModule.kt`, `VisibilityTest.kt`, strings ×3 (`section_review_queue`); quest-authoring strings ×3 (`qa_review_*`, `qa_filter_*`, `qa_error_submit_review_failed`); `Question.kt` comment reworded off the reviewer role; `QuestDraft.kt`/`QuestDraftEntity.kt` rejection-reason comments reworded (field stays — model refusals write it).
- Room schema `schemas/…/19.json` lands generated at compile and is committed.

## Tasks & Acceptance

**Execution:**
- [ ] Server: delete files 1–5; strip index.js (callables, schedule, queue handler, helpers, human branches, wire field); strip review-fee.js builders; re-pin suites.
- [ ] Client: delete files 6–41; edits per Code Map; Room v19 + migration + schema.
- [ ] Scans/sentinels: remove-human-review-scan.test.js green; export pin, outbox registry, deferred-actions, request-document parity re-pinned.
- [ ] Gates: functions `npm test` (docker) — not fewer green suites than 140 minus the deleted ones (each named); gradle touched modules via `sgw` (JDK 27); ktlint on touched source sets.

**Acceptance Criteria:**
- Given the tree after removal, when the scan runs, then none of the removed shared names appear in `functions/` or client sources, and the scan proves it searched (counted files above floor).
- Given a submission without `reviewOutcome`, when `publishSubmissionIfReady` runs, then it stays UNDER_REVIEW (worker-owned) and no reviewer records are read.
- Given a settled model cycle, when the manifest is written, then entitlements are zero and the whole fee burns — same as 4.18, now the only path.
- Given an old client with a queued `SUBMIT_REVIEW` record, when it reaches the server, then the operation is refused as unknown (queue quarantine), not silently dropped — the op is gone from both registries symmetrically.

## Implementation Notes

- Baseline 05020bc8 (4.17 done). Autonomous wave, 4.17 precedent: spec straight to in-progress, change log is the record.
- `reviewApproved` redefinition: the lesson document is the only remaining writer-side proof; the human publish path wrote the same fields, so legacy published lessons stay approved; model-published lessons become admissible (they were not before — admin task never flipped to PUBLISHED).
- The `review` wire field: author's star-summary (`testingScore`/`logicScore`/`translationScore`/`translatedLanguages` max-levels). Nothing in the model world reads it; the multilingual question content itself rides `questions[]` and stays.
- `apps` push string «Your reviewer level has been updated» (qualification family, epic-10 push copy) — out of scope, recorded here as a known non-quest-review mention.

## Spec Change Log

- 2026-10-27, done: коммит 60d5484a (117 файлов, +3041/−9302), запушен на night/e7-commerce. 4.20/4.26 не тронуты; 4.21 в этой смене не бралась.
- 2026-10-27, implementation loopbacks: (1) `loadArenaLessonContext`/`loadArenaLifecycleContext` (server) — «reviewApproved» переопределён полями публикации в документе урока (`reviewSubmissionId` + `reviewApprovedAtMs`); админ-задания и записи ревью больше не читаются, и это закрыло латентную дыру 4.16: модельно опубликованные уроки не были admissible для арены, потому что задание никогда не переходило в PUBLISHED. (2) `publishSubmissionIfReady` удалена целиком — после удаления человеческой ветки и журнального reconcile у неё не осталось вызывающих; модельную доводку делает `advanceModelReviewedSubmission`. (3) Модельный воркер сохраняет имя `createReviewQueueWorker` (4.12 slug) и статус черновика автора `REVIEW_QUEUED` — скан ловит имена поверхности экрана (`ReviewQueueComponent`/`ReviewQueueRoot`/…), а не слово целиком. (4) Курсоры и Kind-реестры: `SyncJournals` сокращён до трёх журналов (review-журнал сервера удалён из `sync-changes.js` вместе с kind `review_assignment` и админ-путями), `reviewAssignmentSyncCursorId` исчез, `OwnerScopedTables`/`OwnerRowsDao` потеряли две таблицы. (5) Поле `review` (авторская звёздная самодекларация) снято с провода: `requestToDocument`/`normalizeRequest`/`privateDocuments`, `QuestArenaSubmissionRequest`, `FirebaseQuestPrivateRemoteDataSource.toArenaReviewDto`, маппер подачи. (6) Импорт `MAX_AUTHOR_REJECTION_REASON_BYTES` из lifecycle-вью больше не нужен серверу (приёмник отклонения удалён); view оставил его себе — он валидирует легаси-документы. (7) Полные сиды `admin/review/...` вычищены из стабов арена-тестов; парные арена-тесты перепинены на новую семантику одобрения (полем документа урока).
- 2026-10-27, gate record: functions `npm test` — 137/137 suites (было 140: удалены `testing-review-threshold`, `review-fee-cycle`, `review-journal.test`/`review-journal-wire`; добавлен `remove-human-review-scan`); export-set hash re-pinned (5 callables + schedule и обработчик очереди ушли); deferred-actions/outbox-registry/arena-request-document парности перепинованы. Детали gradle-гейтов — в Verification ниже.

## Verification

**Commands (run on VPS, 2026-10-27):**
- functions: `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` — **137/137 suites passed** (было 140/140; −3: `testing-review-threshold`, `review-fee-cycle`, `review-journal` (+ его wire-страж); +1: `remove-human-review-scan`). mvp-isolation зелёный.
- gradle (`JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew … --no-configuration-cache`): `:shared:core:persistence:jvmTest` (включая MigrationCoverage + новый migrate_18_to_19-тест; AppleDouble `._Migration18to19.kt` от SMB-записи удалён — он валил SourceScan), `:shared:core:outbox:jvmTest`, `:shared:core:sync:jvmTest`, `:shared:core:question-schema:jvmTest`, `:shared:feature:quest-authoring:domain:jvmTest`, `:shared:feature:quest-authoring:data:jvmTest`, `:shared:feature:app-shell:domain:jvmTest`, `:platform:firebase:testDebugUnitTest`, `:android:feature:quest-authoring:presentation:testDebugUnitTest`, `:android:feature:app-shell:presentation:testDebugUnitTest`, `:apps:android-next:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- ktlint: `:shared:core:outbox:ktlintJvmMainCheck`, `:shared:core:persistence:ktlintJvmMainCheck`, `:shared:core:sync:ktlintJvmMainCheck`, `:shared:core:question-schema:ktlintCommonMainCheck`, `:shared:feature:quest-authoring:data:ktlintCommonMainCheck`, `:shared:feature:app-shell:domain:ktlintCommonMainCheck`, `:platform:firebase:ktlintDebugSourceSetCheck`, `:android:feature:app-shell:presentation:ktlintDebugSourceSetCheck`, `:android:feature:quest-authoring:presentation:ktlintDebugSourceSetCheck`, `:apps:android-next:ktlintDebugSourceSetCheck` — нарушители только пять предсуществующих файлов (`QuestAuthoringMapper` (3:1 imports, 94/97 multiline — позиции существовавшего кода), `QuestReviewOutcomeRepositoryImpl`, `PrivateQuizLinkOutboxWriter`, `InboxWireParser`, `LessonErrorReportOutboxWriter`) — та же корзина wrapper 9.8.0, что в 4.16/4.17; `DefaultReviewQueueComponent` (2 нарушения) исчез вместе с файлом. Ни один файл этой истории нарушений не имеет.
- Room schema `19.json` сгенерирован компиляцией и закоммичен (27 entities, review-таблиц нет).
- detekt — предсуществующий toolchain-wide jvm-target 27 (владелец); бейслайны `shared/feature/quest-authoring/data/detekt-baseline.xml` и `android/feature/quest-authoring/presentation/detekt-baseline.xml` держат протухшие записи об удалённых классах — чистка при первом запуске detekt (open item).
