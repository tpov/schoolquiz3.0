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

**Приёмник мутаций в `functions/index.js` (E3, AD-6).** Чистый модуль `functions/mutation-queue.js`
готов, покрыт тестом и подключён к `npm test` и `npm run lint`. Сам callable-приёмник не заведён:
`index.js` в момент работы правила другая сессия, и хирургия в файле на 4166 строк параллельно с
чужими изменениями гарантированно затёрла бы их. Остаётся завести приёмник и реестр операций —
это же и есть вход в E4.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: Wire the redaction module into publication — in `publicDocuments` (`functions/index.js:2463`, question write at `:2525-2534`) write `question_keys/{questionId}` into the same batch, and add `difficulty` and `type` as fields on the public question document. The payload stays verbatim.
  evidence: Split from spec-e2-2 at CHECKPOINT 1 on token budget, and independently the safer order: a parallel session is actively editing `publishSubmissionIfReady` (`:2234`, batch at `:2260`, commit at `:2298`), having inserted ~23 lines at its head mid-investigation. Two facts the wiring must respect — the published questions come from the admin-review task documents via `requestWithPublishedQuestions` (`:2304`), not the original request; and every write is `{merge: true}` (`:2459`), so a field removed here is not removed from already-published docs.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: Add an explicit `match /question_keys/{questionId} { allow read, write: if false; }` to `firestore.rules`, beside the `allow read: if true` on `questions` (`firestore.rules:127`), with a comment saying the denial is deliberate.
  evidence: Firestore default-denies unmatched paths, so the collection is already closed and this is documentation rather than protection — which is why it can ship separately. House style to copy: `nickname_claims` (`:39`) and `configs` (`:44`), both `allow read, write: if false`. `firestore.rules` is also currently modified by the parallel session.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: SingleChoice and MultipleChoice option order leaks the answer statistically and is not shuffled — across the 5,550 four-option seed questions the correct answer sits at position 0/1/2/3 in 31.7% / 34.6% / 23.5% / 10.1% of cases, so always picking position 1 beats guessing by about ten points from the public half alone.
  evidence: Found by review of spec-e2-2. Left out of that slice because its frozen I/O Matrix says the SingleChoice public half "keeps every option id and text", which re-issuing ids would contradict — a frozen-intent change is the human's to make. The stated cost of shuffling appears not to be real: `lesson-reward.js` reads only `.text` from option rows and keys on the question id, and `RunnerStateMapper.kt:145-152` already reshuffles options for display, so nothing depends on stable option ids.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: An `Ordering` question whose items share an id is refused by the redactor, and refusal means the payload ships unredacted — with the correct order as its array order.
  evidence: `QuestionContent.Ordering` (`QuestionContent.kt:135`) enforces only `items.size in 2..8`, and `assessment-scoring.js` `scoreOrdering` explicitly documents handling duplicate ids, so such questions exist. This is the worst outcome for the one type where the answer *is* the payload. The fix is to key the id map by index rather than by id so duplicates can be handled instead of refused.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: The key's persistence shape is undecided, and Firestore rejects field names matching `__.*__` — so a `blankToCandidate` map stored as a Firestore map cannot hold a blank literally named `__proto__`.
  evidence: The module survives a JSON round trip, which its test calls "Firestore's only serialisation", but that is not the same as a Firestore map write. The wiring slice must state whether the key is stored as a map or as an escaped/array form, and test the shape actually used.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: The five content discriminators are now declared twice in JavaScript — `assessment-scoring.js:38` (not exported) and `question-redaction.js` — with nothing holding them equal, and the four `*Redacted` names have no Kotlin counterpart at all.
  evidence: The whole design rests on those strings matching. `e2-plan.md` Step 4 places `RedactedQuestionContent` in `shared/core/question-schema`; until it exists, a redacted question fails `KotlinxSerializationQuestionContentParser.parse` and `StartLessonAttemptUseCase` drops it, reporting an empty pool. Also unpinned: whether the Kotlin type declares `id`/`difficulty`/`info` as always-emitted or defaulted — kotlinx throws `MissingFieldException` for a declared property with no default even when nullable, and the JS side currently omits unresolved fields rather than emitting null.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: Two whole classes of published question still ship their answers — the legacy `{"type":"single-choice","options":[<strings>],"correctIndex":N}` dialect, which the redactor passes through by design, and HARD `Survey` questions, which have no key to remove and score full marks for any valid pick.
  evidence: `scripts/seed-hierarchy.js` writes the legacy shape into `questions/{id}` and `parseLegacy` still accepts it. `e2-plan.md` names both (traps 14 and 15) and assigns the legacy one to Step 10. A hard survey cannot be redacted at all, so the publication gate has to exclude it rather than redact it.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-2-answer-key-store.md`
  summary: The redactor does not mirror `QuestionContent`'s size and uniqueness invariants — duplicate option ids, `MultipleChoice` with a single correct id (Kotlin requires two), `candidates.size == 5 || 10`, `blanks.size in 1..3`.
  evidence: Deliberately left out of the slice: mirroring them risks over-refusing real corpus data, and refusal currently means publishing unredacted. Revisit once the refusal outcome is visible to the caller and the write path can decline to publish instead.

**Синк E9 сделан, но общий гейт им не проверить.** Каденции разведены: `enqueueManualProfileSync()`
поднимает профильный воркер, а не полный контентный список, и выбор «при запуске» в профильном
пикере теперь что-то делает. Проверено тестами модуля `platform/android-services`. Полный `ciCheck`
в этот момент падал на чужой незавершённой работе — `QuestMapper.kt` без `contentsVersion` и тесты
`quizzes-screen` без `retirePublicQuest`, — поэтому зелёным подтверждён только собственный модуль.

- source_spec: none
  summary: The hint spends a life before checking there is anything to reveal — on MultipleChoice it also submits an empty answer, scoring the question wrong. `LessonRunnerScreen.kt:437-438`, `:501-502`, `:578-579` all call `component.hintRequested()` (which decrements lives, `DefaultLessonRunnerRootComponent.kt:255-263`) before testing the answer-key field; only SingleChoice (`:403-405`) short-circuits correctly.
  evidence: Pre-existing, found while mapping the blast radius for E2 step 4. `HintAnswer.kt:14-24` `buildHintDraft` is the null-safe version of exactly this logic but is called only from tests (`RevealDigitAndHintTest.kt:127-200`); production re-implements it inline at four sites and lost the null-safety at three. Routing all four through `buildHintDraft` and gating `hintEnabled` (`LessonRunnerScreen.kt:352`) on `buildHintDraft(qState) != null` fixes the class in one move, independent of redaction.

- source_spec: none
  summary: Two `when` statements accept a widened question type silently instead of failing to compile — `Scoring.kt:59` `else -> Score(1)` and `RunnerLogic.kt:257` `else -> randomAnswer`. Every other consumer of `QuestionContent` is an exhaustive `when` with no `else`, so the compiler catches it.
  evidence: Relevant to the slice that widens the runner to a common supertype: a redacted question reaching `evaluateAnswer` would score 1/9 with no error, corrupting `percentScore`, stars and the HARD unlock. Deleting both `else` branches as part of that widening turns the whole blast radius into a compiler-checked list. Also worth knowing: `RunnerState.playOrder: List<RunnerQuestion.Valid>` (`RunnerState.kt:42`) is the containment boundary — keep redacted content out of it and the runner is untouched.

- source_spec: none
  summary: `RunnerQuestion.Invalid` (`RunnerQuestion.kt:23-28`) has zero references anywhere and is never constructed, though its KDoc claims such items are "filtered out at init".
  evidence: Found while mapping E2 step 4. It is a pre-built slot in the sealed interface for a question that reaches the pool but cannot be played — exactly what a redacted question needs — so it is worth knowing it exists and is currently dead rather than reinventing it.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-4-redacted-question-type.md`
  summary: The common supertype and the reader — a `QuestionDisplay` interface (`id`, `difficulty`, `text`, `imageUrl`, `info`, `displayTexts`) implemented by both `QuestionContent` and `RedactedQuestionContent`, plus `parseAny(payload, fallbacks): Result<QuestionDisplay>` on `QuestionContentParser`.
  evidence: Split from spec-e2-4 on token budget. The redacted type is inert without it — nothing decodes it but its own test. Three facts the slice must respect: `displayTexts` has to be exactly the option/item/candidate texts and nothing else, so `computeCharsCount` (`RunnerLogic.kt:159-170`, image constant 100) keeps returning the same number; it must be declared as a body `val` with a getter, never a constructor parameter, or it joins the wire format and the `init` invariants; and `parseAny` must have a default body on the interface, since both `FakeQuestionContentParser`s override only `parse` and an abstract method breaks them.

- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 2.5, Story 2.6)
  summary: Истории 2.5 «граница пересоздания» и 2.6 «чистая схема версии 1» отменены — база пошла настоящими миграциями до версии 5, а не одним пересозданием.
  evidence: Спайн решением AD-16 разрешал одно пересоздание локальной схемы, потому что живых установок нет. Фактически параллельная работа завела очередь миграцией и довела AppDatabase до version=5 с экспортированными схемами 1..5.json. Это строже AD-16 и лучше: история миграций перестала быть фиктивной без потери данных. Выполнять 2.6 сейчас означало бы стереть базу и отменить чужие миграции. Что из 2.6 остаётся живым: снятие колонок contentsVersion с пяти Room-сущностей и сведение трёх очередей к одной — обе работы требуют собственной миграции и своей истории. AD-16 и NFR11 подлежат пересмотру.

- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 2.8, третий и четвёртый критерий)
  summary: Откат локальной половины при карантине для трёх переехавших действий не реализован — переезжать пока нечему.
  evidence: Ядро своё сделало: реакция на карантин обязательна (умолчание снято, отсутствие не компилируется), вызывается ровно один раз, не вызывается на ожидании предусловия и конфликте, таблиц фичи не трогает — покрыто QuarantineNotificationTest. Но прохождения, оценки и заявки на арену всё ещё лежат в трёх старых очередях: их перевод на общую таблицу был историей 2.6, а она отменена вместе с пересозданием схемы. Пока перевод не сделан своей миграцией, у критерия «для каждого из трёх operation наступает откат» нет предмета. Владелец решил, что реакция одна для всех трёх — откат; это записано в AD-28 и ждёт переезда.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-4-redacted-question-type.md`
  summary: Neither `detekt` nor `ktlintCheck` covers `shared/core/question-schema` — `detekt` reports NO-SOURCE because it does not see the KMP source layout, and the module has no `ktlintCheck` task at all, since only the Android convention plugins wire ktlint.
  evidence: Found during E2.4 review; an unused import in a new file survived the gate. Both legs of `ciCheck` are inert for every file in this module, and by the same reasoning for every other `schoolquiz.kmp.library` module — which is most of `shared/core`.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-4-redacted-question-type.md`
  summary: The `onlyIf { file("functions/node_modules").isDirectory }` guard on `functionsTest` (`build.gradle.kts:44`) is now load-bearing for a two-language contract, not just for a test suite.
  evidence: Sharpens an entry already recorded for E2.1. The Kotlin half of the redacted-wire harness is pinned to the fixture file, never to the emitter — only the JavaScript half re-runs `redact` and compares. So on a checkout without `npm install`, the emitter can change shape and `ciCheck` still reports green. A reviewer confirmed the whole functions suite needs nothing outside Node builtins: it ran green in a sandbox with `node_modules` deleted, so the guard protects nothing and the task can simply run unconditionally.

**Синк E7 — сделана половина.** Журнал изменений читается страницами (AD-31): курсор стал парой
«время, id документа», Firestore-запрос продолжается через `startAfter` по этой паре, оркестратор
крутит страницы до конца с потолком на проход. Это закрывает и потерю двух записей, записанных в
одну миллисекунду, — риск, который аудит отмечал как B-6.

Не сделано:
- **вторая половина пары не сохраняется.** `sync_state` хранит только `changedAtMs`; чтобы класть
  рядом id документа, нужна колонка и миграция, а `shared/core/persistence` в этот момент
  переписывала параллельная сессия (миграции 4→5, схемы, тесты миграций). До тех пор защита от
  одной миллисекунды работает внутри страницы, но не через границу проходов;
- **принудительный ресинк (AD-30)** — операция сброса курсоров чтения, не трогающая очередь записи;
- **приведение сид-скриптов к продовой схеме id (AD-11)** — повторный прогон сида по-прежнему
  растит коллекцию.
