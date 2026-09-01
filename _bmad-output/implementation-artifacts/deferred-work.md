# Deferred work

Goals split out of a larger intent, kept so nothing is quietly dropped.

- source_spec: none
  summary: Publish hard questions without their correct answer, keeping the key in a server-only store, and teach the client a redacted question type that QuestionContent's invariants cannot be forced to accept.
  evidence: Split from E2. Independently shippable — the store can be written and left unread, and the redacted type can exist before anything produces one. Steps 2-4 of e2-plan.md.

- source_spec: none
  summary: Make a hard lesson attempt deferred — no score until the server returns one — and give the runner and the lesson list a state for a result that has not arrived.
  evidence: Split from E2. The largest slice: Attempt.codeAnswer and percentScore become nullable across five modules, costing a Room migration and breaking roughly forty tests in one commit. Steps 5, 7 of e2-plan.md.

- source_spec: none
  summary: Turn redaction on at publication, return the server-computed score and lesson-level advice to the client, and backfill the existing seeded catalog which never went through the publication path.
  evidence: Split from E2. The seeded library writes questions/{id} directly, so flipping redaction on changes nothing that already exists — the backfill is its own deliverable. Steps 8-10 of e2-plan.md.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: Fold `functions/result-verification.js` into `functions/assessment-scoring.js` — delete it, repoint the `index.js:33-37` require and its three call sites (`:406`, `:3023`, `:3033`), update both hand-maintained lists in `functions/package.json`.
  evidence: Split from spec-e2-1 at CHECKPOINT 1 on token budget. It is an independent refactor with no dependency on the new scorer and could merge as its own PR. Until it lands, `recomputePercentScore` and the new `computePercentScore` are two implementations of the same formula living side by side on the server — the fixture set covers only the new one.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: Complete the hand-maintained `lint` list in `functions/package.json:8` — `tournament-ranking.js`, `logos.js` and `logo-images.js` are absent, and no `*.test.js` is ever syntax-checked.
  evidence: Noticed during spec-e2-1 investigation; unrelated to scoring. `npm run lint` runs at deploy time only (`firebase.json:16` predeploy); `ciCheck` never calls it, so the gap is invisible until someone deploys.

---

## Синк E2 — отложено при реализации очереди

**Пересоздание базы одной чистой схемой (AD-16, AD-17).** Спайн разрешает пересоздать локальную
базу, раз живых установок нет, и сделать это разом с заведением очереди и удалением
`contentsVersion`. При реализации выбрана обычная миграция `3 → 4`, добавляющая таблицу `outbox`.

Причина — оговорка самого AD-16: пересоздавать можно только при пустой очереди и опубликованных
черновиках, потому что неопубликованный черновик квеста, расписание повторов и история ответов
серверного двойника не имеют. Добавление таблицы этой опасности не несёт вовсе, а расчистка
недостоверной истории миграций к работе очереди отношения не имеет и может идти отдельно.

Что осталось несделанным из AD-16 и AD-17:
- фиктивная история миграций (`1.json` совпадает с `2.json` по `identityHash`) не расчищена;
- тест `runMigrationsAndValidate` на новую миграцию не написан — в репозитории этого вызова
  по-прежнему нет ни разу;
- удаление `contentsVersion` в порядке «правила, сервер, клиент» (AD-13) не начато.

**Переезд трёх существующих очередей на общую таблицу (AD-4, AD-5).** Движок и таблица готовы,
но `lesson_result_attempt_outbox`, `quest_rating_outbox` и `quest_arena_submission_outbox` пока
живут своей жизнью. До переезда сохраняется живой дефект: одна отвергнутая запись результата
навсегда глушит отправку оценок (`LessonResultSync.kt:45`).

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: The JavaScript half of the parity gate skips itself — `build.gradle.kts:45` guards `functionsTest` with `onlyIf { file("functions/node_modules").isDirectory }`, and the repo has no `.github/workflows` at all.
  evidence: On a clean checkout without `npm install`, `./gradlew ciCheck` goes green having never run the JS scorer — the exact "quietly stops comparing, but the green tick still says the two agree" failure both new files were written to prevent. Pre-existing since `9fd88af9`; surfaced by two of the three E2.1 reviewers.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: Two Kotlin scoring quirks are now pinned as expected behaviour by fixture cases and need a decision — `QuestionContent.Ordering` has no uniqueness invariant on item ids (`QuestionContent.kt:135`), so two items sharing an id let a repeated answer pass the permutation check and score 9; and `evaluateAnswer` never reads `Survey.allowMultiple` (`Scoring.kt:52-58`), so several picks in a single-pick survey still score full marks.
  evidence: The spec's "Ask First" clause required these to be mirrored and flagged, not fixed. They are recorded only inside two fixture `name` strings marked FLAGGED, so fixing either in Kotlin will now read as breaking the parity suite unless the fixture is updated in the same change.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: `shared/core/scoring/src/commonTest` now duplicates the fixture set — `PercentScoreComputeTest` repeats codeAnswers `"9999"`, `"5555"`, `"9050"` verbatim and `StarsComputeTest` overlaps six `computeStars` cases — and both still carry comments pointing at `RunnerLogic.kt` as the scorer's home, as do `result-verification.js:11-12` and `result-verification.test.js:7`.
  evidence: The scorer moved to `shared/core/scoring/Scoring.kt` in `76e53d10`; those references are stale. Two Kotlin sources of truth for the same arithmetic will drift, and the stale pointers send the next reader to the wrong file.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-1-server-scorer.md`
  summary: No `isWellFormedQuestionContent` / `isWellFormedUserAnswer` validator ships alongside the scorer, so the first caller has nothing to call before scoring an untrusted payload.
  evidence: `QuestionContent`'s init blocks enforce ten invariants on the Kotlin side (`options.size in 2..8`, `correctOptionIds.size >= 2`, `candidates.size == 5 || 10`, unique ids, `correctOptionId in options`); the JS mirror enforces none of them by design, pairing with a validator the way `isWellFormedCodeAnswer` pairs with `recomputePercentScore`. That validator does not exist yet, and the parity harness cannot cover malformed content because Kotlin's decoder rejects it before it can become a fixture.
