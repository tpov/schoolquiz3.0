# Epic 8.3 — private quiz authoring defaults — 2026-10-22

Spec: `_bmad-output/implementation-artifacts/spec-epic-8-social-features.md` (baseline 6ac52f2f).
Done bar per tickets.toml scope rule: code + JVM/Node tests + evidence + device-proof recipe.
Physical device proof — the owner's step (recipe below).

## What was built

- **Domain** `shared/feature/quest-authoring/domain/.../model/PrivateQuizSettings.kt` — AD-29
  defaults live in the model (limit 1, showResult false, no expiry); `validateForSave(nowMs)`
  refuses a past expiry at save time. `QuestDraft.privateQuiz: PrivateQuizSettings?` added
  (null = ordinary quest). `CreateQuestDraftCommand.privateQuiz` passes the author's choice.
- **Wire** `ArenaDraftDto.privateQuiz` + `ArenaPrivateQuizSettingsDto`; `QuestArenaOutboxWriter`
  writes `draft{}.privateQuiz` (explicit `linkExpiresAtMs: null` for "no expiry"; the key is
  absent for ordinary quests). One pipeline — no parallel submission mechanism.
- **Server** `functions/index.js`: `normalizeRequest` normalizes `draft.privateQuiz` through the
  gate's `normalizeSettings` (runtime fields `enabled`/`startedCount` cut off at the door — a
  body value can never close or reopen a link); `privateDocuments` persists `privateQuizSettings`
  on `private/{uid}/catalogs/{cat}/quests/{qid}` — resubmission updates the three author fields
  and PRESERVES `startedCount`/`enabled` (AD-29); expiry in the past refuses the submission
  (`PRIVATE_QUIZ_EXPIRY_IN_PAST`). Private submissions open no review-fee cycle and write zero
  ledger rows (AD-26/AD-29; proven by tests).
- **Manual close** — new outbox mutation `quest_authoring.CLOSE_PRIVATE_QUIZ_LINK`
  (`OutboxOperations.CLOSE_PRIVATE_QUIZ_LINK` + `PrivateQuizLinkOutboxWriter` +
  `ClosePrivateQuizLinkUseCase`); server closes in a transaction ordered against starts and
  journals the change (`private/{uid}/sync_changes`). Re-enable is not assigned: closed stays
  closed; resubmission does not reopen.
- **Form** `QuestCreateScreen.kt` + `DefaultQuestCreateComponent.kt` + `QuestCreateUiState.kt`:
  private category (recognized by the seeded technical id `private-quizzes-v1-catalog`)
  hides Розділ/Тема/Урок, shows the link-settings card (attempt limit stepper ≥1, show-result
  switch, expiry off by default with 1/7/30-day presets), one HARD questions entry, SURVEY off
  the type shelf, `hasValidStructureTitles` relaxed to quest-title-only, close-link control for
  an existing quiz. Loading an existing quiz restores SAVED settings, not defaults (AD-29).
- **Round-trip** `FirebaseQuestPrivateRemoteDataSource` reads `privateQuizSettings` into the
  snapshot; `QuestAuthoringMapper` carries settings entity↔domain; Room v18
  `Migration17to18` adds three nullable columns to `quest_drafts` (additive, AD-22).

## Gates run (from this tree)

- `docker exec schoolquiz-functions-emu sh -c 'cd /repo/functions && node private-quiz-settings.test.js'`
  → **8/8 pass** (real handlers over the Firestore stub: settings persisted with defaults;
  resubmission preserves startedCount/enabled; ordinary submission writes no settings and still
  pays the review fee; past expiry refuses before any write; malformed privateQuiz refuses;
  close-link disables new starts, keeps the counter, journals; unknown body field refuses;
  settings saved by the submission are the settings the gate opens by). Transcript shared in
  `../2026-10-22-private-quiz-negative/transcripts.txt`.
- `sync-change-clock.test.js` OK (fence updated for the new signatures; the invariant — server
  clock as an argument — unchanged).
- Gradle (via `sgw`, JDK 27): `:shared:feature:quest-authoring:domain:jvmTest`,
  `:shared:feature:quest-authoring:data:jvmTest` — green, incl. new `PrivateQuizSettingsTest`,
  `PrivateQuizSettingsWireTest`, `QuestCreateUiStatePrivateCategoryTest`; persistence jvmTest +
  instrumented `migrate_17_to_18` case added (device-run remains the owner's `connectedAndroidTest`).

## Device-proof recipe (owner, Samsung; category already seeded and visible)

1. Install the MVP APK built from this tree (`sgw ./gradlew :apps:android-next:assembleMvp`).
2. «Створення квесту» → category «Приватные квизы»: Розділ/Тема/Урок pickers disappear; the
   link-settings card shows лимит=1, показ результата=off, «без срока».
3. Create the quiz with a title only, add 2 HARD questions (no SURVEY chip, no Easy button),
   submit to arena.
4. Kill the app, reopen, load the quiz: the card shows the SAVED settings, not defaults.
5. Change limit to 3 + enable show-result, resubmit; then close the link («Закрыть ссылку»).
6. Server check: new start must refuse (LINK_DISABLED), startedCount unchanged; a Firestore
   console look at `privateQuizSettings` shows enabled=false with the counter preserved.

## Left to the owner

- The physical device pass above (HITL).

## Category seed sanity (spec Verification §)

`curl -s "http://localhost:8080/v1/projects/demo-schoolquiz-mvp/databases/(default)/documents/catalogs/private-quizzes-v1-catalog"`
→ doc `{id: private-quizzes-v1-catalog, name: «Приватные квизы», questType: REGULAR, version: 1,
archived: false}` — the form's private-category key matches the live demo backend.
