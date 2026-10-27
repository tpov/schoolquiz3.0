---
title: '4.16 author-review-outcome — авторский исход модельной проверки (UX-6)'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '7a408ef1'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** The pipeline (4.12–4.15) judges and translates, but the quest stalls `UNDER_REVIEW` forever: nothing advances a terminal pipeline to the author's outcome, and My Quests has no strokes, no «Не прошёл», no «Проверка задерживается», no failed-question list.

**Approach:** The worker, on a terminal pipeline, computes the outcome from immutable records (`functions/review-outcome.js`) and writes it into the submission. A new owner-checked callable `advanceQuestReviewOutcome` runs the model branch of the submission advance: REVIEW_DELAYED → «Проверка задерживается» (unfinished cycle stays unsettled, OQ-2 parked); QUEST_REVIEWED with zero passing lessons → REJECTED («Не прошёл» + `REVIEW_FAILED_QUESTIONS`); otherwise the passing lessons publish independently, with translation records riding as `__lang` question variants beside the original. The view carries the outcome fields (v2), the client settles drafts and renders UX-6 on the draft card.

## Boundaries & Constraints

**Always:**
- Publication reuses the existing helpers (hierarchy versions, prune, shelf audit, version mirror, fee settle) — the model branch is a sibling of the human branch, not a fork of the helpers.
- Failed lessons of a first submission never enter the public tree; a previously-published lesson that now fails is pruned by the same `planHierarchyPrune` rule that removes unreviewed content (AD-26).
- Translation variants enter the tree only through `review_translations` records bound by the pipeline checkpoint; answer keys are the server's own (4.15 assembler).
- `advanceQuestReviewOutcome` refuses a foreign submission with the same `not-found` as an absent one (AD-20).
- Client settles PUBLISHED/REJECTED exactly as before (AD-6 paths); REVIEW_DELAYED leaves the draft waiting and is display-only.

**Never:**
- No fee semantics change (REVIEW_DELAYED does not settle; the 4.18 zero-reward world owns settlement).
- No human-review code changes here (4.19 removes it); no admin Decisions surface (4.17).
- No live model calls; no device journey (4.20 owns the live pass).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Reviewed, all pass | outcome QUEST_REVIEWED, passing = all lessons | Publish whole tree with translations; status PUBLISHED; fee cycle settles (or no-ops without a cycle) | No error expected |
| Reviewed, some pass | FAIL questions in lesson l2 | l1 publishes with variants; l2 stays out; status PUBLISHED | No error expected |
| Reviewed, none pass | all lessons have FAILs | status REJECTED + reason `REVIEW_FAILED_QUESTIONS`; no tree writes | Cycle settles REJECTED if open |
| Delayed | outcome REVIEW_DELAYED | status REVIEW_DELAYED; nothing publishes; no settlement | Author sees «Проверка задерживается» |
| Foreign advance | uid ≠ owner | `not-found`, doc unchanged | Indistinguishable from absent |
| Broken outcome | unknown pipelineStatus/fields | `data-loss` refusal, doc unchanged | Loud, repaired by owner |
| Advance without outcome | no reviewOutcome field | no-op, published=false | For human-world rows |
| View strictness | bad stage/language/flag/over-bound list | generic `unavailable` (fail closed) | Re-read, never fabricated DTO |
| Client ready outcome | view entry outcomeReady under UNDER_REVIEW | client calls advance once, refetches, settles | Bounded single retry |

</frozen-after-approval>

## Code Map

- `functions/review-outcome.js` (new, pure) + test — outcome from pipeline+verdicts+translations: status, failedQuestions (lesson, question, stage, reason), passingLessonIds, detectedLanguage, delayedStages.
- `functions/review-queue-worker.js` — `finishQuest` writes `reviewOutcome` into the queue doc after terminal (transport failures logged, never fatal); tests for reviewed/failed/delayed outcomes.
- `functions/index.js` — `normalizeReviewOutcome` gate; `publishModelReviewedSubmission` (delayed / rejected / narrowed publish with `resolvePassingLessonsWithTranslations` reading `review_pipeline` + `review_translations`); branch at the top of `publishSubmissionIfReady`; `exports.advanceQuestReviewOutcome`; `requestToDocument`/`normalizeRequest` carry `reviewOutcome` (writer/reader pin `arena-request-document.test.js`).
- `functions/arena-lifecycle-views.js` — statuses + REVIEW_DELAYED; `modelReviewOutcomeEntry` (failedQuestions ≤200 strict, detectedLanguage ∈ ru/uk/en, outcomeReady); view version v2.
- `functions/_firestore-stub.js` — `db.getAll` and batch `delete` added (publication paths always needed them; no suite had driven the tree before).
- Client: `QuestSubmissionOutcome` + `QuestionReviewFailure` + `advanceOutcome` (data remote); platform parser v2 + strict model fields; `QuestArenaOutcomeSync` advances ready outcomes and refetches once; domain `QuestReviewOutcomeSnapshot`/`QuestionReviewFailure` + `QuestReviewOutcomeRepository` + `FetchQuestReviewOutcomesUseCase`; data impl + DI; presentation `MyQuestsUiState.reviewOutcomes` + `DraftReviewOutcomeBlock` (strokes/delayed/failed list + «Исправить вопросы») + strings ×3.
- Do not touch: fee charge/settle math (4.18), admin/review/lessons human path, `functions/mvp-isolation.test.js`.

## Tasks & Acceptance

**Execution:**
- [x] Pure outcome module + tests (6).
- [x] Worker outcome write + tests (14).
- [x] index.js model branch + callable + stub `getAll`/`delete` + end-to-end suite (6).
- [x] View v2 fields + suite (12).
- [x] Client data/platform/domain/presentation stack + tests.

**Acceptance Criteria:**
- Given a terminal pipeline, when the author's client reads the submission, then the outcome (status, failed questions with lesson/stage/reason, detected language) is visible without waiting for the nightly reconcile.
- Given a partially passing quest, when advance runs, then exactly the passing lessons publish, each failed lesson and its questions stay out of the public tree, and the author sees «Не прошёл» with the failed list and «Исправить вопросы».
- Given REVIEW_DELAYED, when advance runs, then nothing publishes, no cycle settles, and the card reads «Проверка задерживается».

## Implementation Notes

- Baseline: 4.15 commit 7a408ef1. Workflow deviation sanctioned by the run mandate (autonomous wave, spec straight to in-progress).
- Known behavior choice: a previously-published lesson that fails on resubmission is pruned from the public tree (AD-26: unreviewed edits must not remain) — recorded here for the owner; the old human world never pruned on review failure.

## Spec Change Log

- 2026-10-27, verification notes: gates as run — functions `npm test` **138/138 suites passed** (new suites: review-outcome 6, review-queue-worker 14 incl. 4 outcome cases, quest-review-model-outcome 6, arena-lifecycle-views 12 incl. model fields, handler suite re-pinned to the new export surface — same "Re-pinned (story X)" pattern as 4.11); gradle via `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew` — quest-authoring domain/data jvmTest, platform:firebase testDebugUnitTest, quest+app-shell presentation testDebugUnitTest all green; ktlint per touched source sets clean; detekt stays the pre-existing toolchain-wide jvm-target 27 red (owner's list, stories 4.6–4.9 precedent).
- 2026-10-27, implementation loopback: the Firestore stub gained `db.getAll` and batch `delete` — the publication tree write had never been driven end-to-end by a suite before this story; both mirror real SDK shapes and keep the read-before-write order recorded.

- 2026-10-27, implementation loopback: the stub gained `db.getAll` and batch `delete` — the publication tree write was never driven end-to-end in suites before this story; both additions mirror real SDK shapes and keep read/write order recorded.
- 2026-10-27, implementation loopback: view privacy check for the author outcome is targeted — the failed-question `reason` is an author field by UX-6, so the blanket noPrivate token `"reason":` does not apply to this entry; request-body tokens stay denied.

## Review Triage Log

## Design Notes

- Why advance is an explicit callable and not a view side effect: views are read-only by contract; the author's own client pulls publication forward, the nightly reconcile stays the backstop (AD-9 catch-up).
- Why the outcome lives in the submission doc and not the pipeline doc: the author's view already reads submissions with owner checks; the pipeline doc stays worker-private.

## Verification

**Commands:**
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` — full functions gate.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest :platform:firebase:testDebugUnitTest :android:feature:quest:presentation:testDebugUnitTest :android:feature:app-shell:presentation:testDebugUnitTest --no-configuration-cache'` — client gates.

**Manual checks (if no CLI):**
- None beyond the suites.
