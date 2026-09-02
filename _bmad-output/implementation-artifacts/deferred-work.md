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
- ~~принудительный ресинк (AD-30)~~ — **сделан**: `ForceResync` обнуляет курсоры и запускает чтение
  с начала журналов, очередь записи не трогает;
- **приведение сид-скриптов к продовой схеме id (AD-11)** — повторный прогон сида по-прежнему
  растит коллекцию.

- source_spec: none
  summary: Поднять targetSdk и compileSdk до 36 и разобрать поведенческие изменения Android 15 и 16.
  evidence: Отделено от эпика 0 монетизации. Независимо выпускаемо и независимо ломаемо: миграция targetSdk меняет поведение всего приложения, а рабочее дерево сейчас несёт 223 незакоммиченных файла — при поломке будет невозможно отличить свою регрессию от чужой. Делать на чистом дереве, отдельным коммитом. История 0.3 в epics-monetisation.md.

- source_spec: none
  summary: The lesson runner calls the player's spendable resource `lives` and `hearts` in code (`RunnerUiState.lives`, `stateHolder.livesRemainingHearts`, `hintRequested()`), but the product concept is a charge — the string it renders is `runner_figure_lives` = "Charges" (`android/feature/lesson-runner/presentation/src/main/res/values/strings.xml:51`).
  evidence: The user corrected the terminology directly. A name that means one thing in the code and another in the product is how the hint bug got written: the guard reads as "does the player have a heart left" rather than "is there a charge to spend and anything to spend it on". Renaming it through the runner would make that class of mistake harder.

**Синк E4 — первая операция переехала в очередь.** Реестр `MUTATION_HANDLERS` был пуст, а
клиентского транспорта не существовало: приёмник, движок и хранилище стояли собранными и никуда
не подключёнными. Теперь связаны: `FirebaseMutationTransport` шлёт в единственный `submitMutation`,
`OutboxSyncable` крутит очередь вместе с обычной синхронизацией, а разблокировка урока
зарегистрирована как первая отложимая операция — её тело вынесено в `applyLessonUnlock` и общее
для прямого вызова и очереди, иначе цена по двум дорогам однажды разошлась бы.
`firestore.rules` закрыл `mutation_keys` от клиента: запись оттуда позволила бы объявить чужую
операцию выполненной.

Не переехали остальные из AD-3: действие ревьюера, перенос квеста на полку, снятие лота. Каждая —
свой обработчик на сервере и своя реакция на карантин.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-5-answer-key-written.md`
  summary: Nothing pins that publication actually emits a key document — delete the one line in `publicDocuments` that calls the key store and every check in the repository still passes.
  evidence: No test requires `functions/index.js`; the module's own suite tests the module, not its call site. The right harness exists — `scripts/review-pipeline-e2e.js:412` already asserts what a publish produced — but it publishes `payload: "{}"`, which the redactor classifies `unknown-type`, so even running it could never produce a key. It also sits in no gate: `scripts/package.json`'s test script is `exit 1` and `ciCheck` does not reference it.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-5-answer-key-written.md`
  summary: A lesson that disappears from a submission keeps its previous generation of answer keys forever — `{merge: true}` never deletes, and no document is emitted for a lesson with no questions, so nothing clears the old one.
  evidence: Per-lesson replacement only works while the lesson is still being published. The public question documents behave the same way today (never deleted), so this matches existing behaviour rather than adding a gap — but a key outliving its question is a stored answer with nothing to answer.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-5-answer-key-written.md`
  summary: `isStorableFieldName` may over-refuse. It applies Firestore's *document id* rules to *field names*; the Admin SDK backtick-escapes field names outside `/^[_a-zA-Z][_a-zA-Z0-9]*$/`, so a blank id like `b.1` or `b/1` is probably storable and is currently discarded.
  evidence: Only the empty name and the `__…__` ban look like real field-name rules. Over-refusing costs a question its key with no re-derivation path, though it does show up as a visible refusal rather than a failed batch. Needs an emulator check before the ban is narrowed.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-5-answer-key-written.md`
  summary: The publish batch has no chunking and no question-count validation, and every published question already costs four writes in it — so Firestore's 500-write cap breaks publication somewhere near 125 questions today, silently and with an opaque error.
  evidence: `commitOperations` (`functions/index.js:2289`) exists and chunks at 450, but it commits progressively, which would destroy the atomicity that lets a payload and its key be written together. Keeping the key document per-lesson rather than per-question avoided making this worse, but the underlying ceiling is unguarded and unnoticed.

**Синк E6 — смена аккаунта больше не теряет очередь.** `AccountSwitchGuard` сливает очередь до
`signInWithCredential`, а не после: после него прежнего `uid` уже не узнать, а запись принадлежит
тому, кто её создал, и под новым аккаунтом не отправится никогда. Не удалось слить — вход всё
равно происходит, но исход отличается (`SWITCHED_WITH_UNSENT`), и игрок видит словами, что часть
последних действий не сохранится. Строки добавлены на всех трёх языках.

Не сделано из AD-8: сброс личных курсоров чтения и удаление локальных строк прежнего владельца
после переключения. Требует решения, какие именно курсоры считать личными, — записано в открытых
вопросах спайна как Deferred 5.

**Синк E10 — снят блокер правил.** `firestore.rules` не сопоставлял подколлекции под `users/{uid}`
и catch-all в файле нет, поэтому под текущими правилами клиент не мог записать push-токен вовсе —
AD-20 отмечал это прямо. Путь `users/{uid}/devices/{token}` открыт владельцу на запись и закрыт
на чтение: Admin SDK правил не подчиняется, а игроку список своих устройств пока нигде не
показывается.

Проверить правила автоматически не вышло: `scripts/rules-emulator-test.js` требует поднятого
эмулятора, и сам файл в этот момент правила другая сессия. Остальное из E10 — приём токена на
клиенте, отправка с сервера — не начато и идёт последним по AD-21.


**Синк E5 — состояние синхронизации дошло до экрана.** Строка в настройках рядом с каденциями:
всё отправлено / ждёт отправки N / требуют внимания N, с пояснением, сколько изменилось на сервере
и сколько отклонено. Требующее внимания подсвечено опасным цветом, потому что само оно не
рассосётся. Строки на трёх языках.

Осталось из AD-14: пометка конкретной сущности как неотправленной (`entity_ref` в записи есть, но
результат урока на своём экране не помечается) и показ состояния в шторке — спайн оставляет
размещение за UX (Deferred 5).

**Синк: у ресинка появилась кнопка.** Операция была написана и никем не вызывалась. Теперь она в
настройках, под подтверждением — не потому, что можно что-то потерять (ресинк лечит только сторону
чтения и неотправленные действия не трогает), а потому, что это перекачка всего содержимого, и
нажать её случайно на мобильном интернете обидно. Строки на трёх языках.

- source_spec: `_bmad-output/implementation-artifacts/spec-hint-charge-before-check.md`
  summary: A charge currently buys a guaranteed-correct answer on a HARD question. `spec-charges/SPEC.md:47` says a charge is spent on exactly two things — the toll for playing, and a hint on an **EASY** question — but there is no difficulty term anywhere in the hint flow: `hintEnabled` (`LessonRunnerScreen.kt:356`) does not have one, and `QuestionTypeContent` is not even given `isHard`.
  evidence: On hard questions `revealCorrect` is false so no verdict banner appears, which hides it — but the hint still selects and submits the correct answer. Hard answers are what gate stars and certification, so this is an economy hole, not a cosmetic gap. Left out of the hint slice deliberately: its frozen Ask First reserves "any change to what a hint costs" for the user, and closing this removes something players can do today.

- source_spec: `_bmad-output/implementation-artifacts/spec-hint-charge-before-check.md`
  summary: `QuestionUiState` defaults every correct-answer field to empty (`null`, `emptySet()`, `emptyList()`, `emptyMap()`), so a question with no answer key is indistinguishable from one whose answer is legitimately empty.
  evidence: This is the root cause the hint fix works around rather than removes — the UI has to re-derive "is there an answer here" from empty collections. It becomes load-bearing once redacted questions reach the runner, since a redacted question is precisely one with no answer key; the E2 slice that widens the runner to a common supertype should give that state a name instead.

**Синк 7-1 — сиды перестали растить журнал.** Сервер пишет документ на узел с идентификатором без
времени (`{тип}_{id}`), а шесть сид- и бэкфилл-скриптов писали `{время}-{тип}-{id}`: каждый
повторный прогон создавал новые документы вместо перезаписи существующих, коллекция журнала росла
неограниченно, и клиент вычитывал одно и то же изменение столько раз, сколько раз запускали сид.
Формы сведены в общий модуль `scripts/sync-change-id.js` с тестом-стражем.

Третья часть AD-11 — «запись в журнал обязательна при каждом изменении узла» — проверена. На
сервере (`functions/index.js`) чисто: каждая запись в `quests` идёт в паре с записью в журнал, и
публикация пишет журнал для всех пяти типов. А вот `scripts/bump-all-content.js` поднимал версию
у всего содержимого и **не писал в журнал ничего**: подъём был невидим, у всех уже
синхронизировавшихся содержимое оставалось прежним навсегда. Исправлено; цепочка узел → каталог
вынесена в `scripts/content-catalog-index.js` с тестом, а узлы с оборванной цепочкой не
приписываются чужому каталогу, а называются вслух.

**Синк 7-2 — сиды пишут вопрос в оба журнала.** Сервер пишет изменение вопроса и в журнал каталога,
и в журнал содержимого урока, потому что вопрос читается обоими путями. Четыре сид-скрипта писали
только в урочный: клиент, синхронизирующий каталог, о таких вопросах не узнавал вовсе.
`backfill-sync-changes.js` делал правильно и не менялся.

Не сделано из AD-11/7-2: серверная сторона не проверена на предмет узлов, читаемых больше чем из
двух журналов, — сегодня их два, но правило сформулировано шире.

---

## Ревью синка: 25 подтверждённых находок из 44

Прогон четырёх независимых линз с отдельной попыткой опровергнуть каждую находку. 19 отвергнуто.

**Исправлено сразу (мои, подтверждённые):**

1. **Постраничный запрос журнала падал всегда.** `startAfter(changedAtMs, "")` — Firestore
   достраивает из второго значения путь документа и на пустой строке бросает
   `IllegalArgumentException`. Курсор нигде не хранит вторую половину пары, поэтому пустой `docId`
   приходил в КАЖДОМ первом запросе каждого каталога: контент не синхронизировался вообще, а
   исключение молча становилось `Result.failure`. Теперь при пустом `docId` идёт `startAt` —
   миллисекунда перечитывается заново, что дешевле потери.
2. **Троттлинг уничтожал мутацию.** Cloud Run под нагрузкой отдаёт 429, SDK превращает его в
   `RESOURCE_EXHAUSTED`, а он стоял в списке отказов → карантин без повторов → откат локального
   изменения по AD-28. Переведён в повторяемую ветвь.
3. **«Последняя ошибка» была недостижима.** `drain` по контракту не бросает, поэтому проход всегда
   записывался успехом, и при любом числе неудач игрок видел свежее время удачной синхронизации.
   Теперь успех — это когда ждать больше нечего.
4. Пустое утверждение в собственном тесте ресинка; фейк создавался и не использовался.

**Исправлено вторым заходом:**

5. **Один упавший каталог обрывал весь проход.** Выход по первой неудаче означал, что каталог,
   стоящий в списке после сломанного, не синхронизируется никогда, — а порядок там произвольный.
   Теперь пробуются все, наружу отдаётся первая неудача.
6. **Сброс курсоров затирался параллельным проходом.** Курсоры монотонны — `setCursor` берёт
   максимум, — поэтому проход, начавшийся до сброса, дописывал своё старое значение уже после, и
   ресинк молча не делал ничего. Отменить монотонность нельзя, она нужна ровно для своего случая,
   поэтому сброс и чтение просто не пересекаются: одни ворота на приложение (`SyncGate`).
7. **Auth-scope покрывал только счётчики.** Время последней удачной синхронизации и причина
   последней неудачи переживали смену аккаунта и показывались следующему игроку как свои.

**Исправлено третьим заходом:**

8. **Транспорт нарушал собственный контракт.** Ловил два конкретных типа, а обещал «неудача
   возвращается, а не бросается»: любое третье исключение вылетало наверх и обрывало проход,
   унося соседние записи. Теперь ловится широко, наружу проходит только отмена корутины.
9. **«Перечитать всё» ничего не сообщало и отменялось поворотом экрана.** Исход теперь доходит до
   состояния синхронизации, а сама работа живёт в области процесса: отменённая на середине, она
   оставляла курсоры обнулёнными и половину журнала непрочитанной.

**Осталось из подтверждённых:**

- **вторая половина курсора не хранится.** `SyncCursor` — пара «время + id документа» (AD-31), но
  через `SyncStateRepository` переживает перезапуск только время: `sync_state.cursor` — одна
  колонка `INTEGER`. Внутри прохода пара жива в памяти и журнал читается строго; после перезапуска
  чтение возвращается к началу той же миллисекунды.

  Потери данных нет — источник берёт `startAt(changedAtMs)`, когда id пуст, то есть перечитывает,
  а не перескакивает. Цена — повторная работа, и она ограничена: `MAX_PAGES_PER_RUN` = 50. Порог,
  за которым это становится не тратой, а тупиком: больше 200 записей журнала с одной и той же
  миллисекундой — тогда страница целиком помещается внутрь одного таймстемпа, и каждый новый
  запуск читает ту же первую страницу. Полная перепубликация большого курса одним пакетом — как
  раз такой случай.

  Не сделано сейчас: нужна колонка `cursorDocId`, миграция и парная монотонность (сравнивать надо
  пару, а не число). Модуль `shared/core/persistence` в этот момент занят другой сессией — там
  идёт своя миграция под `server_version` (AD-24). Две миграции в одну версию базы из двух сессий
  не сойдутся; делать после того, как та работа сядет.

*Закрыто с момента прошлой записи:* таймаут доехал до всех 19 вызовов (`28bfa848`, покрытие
пиннит тест по исходникам модуля); `UnlockPricing` вызывается — замок в списке уроков показывает
цену, посчитанную из вопросов урока (`0e8c2a53`); строка состояния и диалог ресинка покрыты
(`c116216c`); заявка на арену больше не запирает черновик подавленной вставкой (`ae630afe`).


**Защитный fallback у читателя журнала — вторая половина 7-6.** `toSyncNodeType` в
`FirebaseCatalogSyncChangeRemoteDataSource` терпит и множественное, и единственное число, а запись,
которую не удалось разобрать, молча пропускает. AD-11 разрешает снять эту терпимость только после
того, как backfill приведёт уже записанное к одной форме — иначе читатель упадёт на первой старой
записи. Истории 7-1…7-3 (одна форма документа, запись в каждый журнал, парный курсор) ещё
in-progress, поэтому снятие ждёт их.

Сигнал, по которому можно будет узнать момент, **сделан** (`8216609a`): страница журнала несёт
число непонятых записей, проход их суммирует, и они выходят наружу через `SyncStatus` рядом с
остальным (AD-14). Терпимость снимается, когда это число устойчиво держится на нуле после
backfill.

Оговорка: считает пока только каталожный читатель. `FirebaseLessonContentSyncChangeRemoteDataSource`
не переопределяет `fetchPage` и идёт через реализацию по умолчанию, которая отдаёт ноль, — журнал
урока свои пропуски не показывает. Три формы документа живут в каталожных журналах, поэтому это
не срочно, но снимать fallback у читателя урока без своего счётчика нельзя.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-6-question-display.md`
  summary: `RedactedQuestionContent` has no invariants by design, so a corrupted store could yield a question with an empty option list that renders as unanswerable with nothing reporting it.
  evidence: Deliberate — that type describes what arrived, not what is valid, and the emitter enforces a minimum of two rows so it cannot produce one. Only a payload written outside the emitter could. The slice that moves the runner onto the supertype needs a named failure for it rather than an empty screen.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-6-question-display.md`
  summary: A redacted question's `id` is null for effectively the whole seed corpus, and nothing in the new seam says how the runner will get one.
  evidence: The seed corpus keeps the question id on the wrapper document, never inside `payload`; the answer key records it as `questionId`, and the parser deliberately does not apply `fallbackId` to a redacted payload. Answers and progress are keyed by question id, so the runner slice has to source it from the document.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-6-question-display.md`
  summary: The e2 plan's Step 4 and Step 5 are written against names and types that no longer exist — `parseAny`, a non-null `id`, a non-null `difficulty`, and an `info` member on the supertype. Step 5 as written will not compile.
  evidence: The delivered shape diverged for reasons found in the code: the two hierarchies mean different types by `difficulty`, and the seed corpus has no `id` inside the payload. The plan is a planning artifact and was not updated; whoever picks up Step 5 should read the spec, not the plan.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-6-question-display.md`
  summary: `RunnerLogic.computeCharsCount` is exercised by no test for four of its five shapes — every case in `TimerComputeTest` and `SessionModeTest` builds a `SingleChoice`.
  evidence: Found while pinning the character count on the supertype. The private function is reachable only through `computeTimer`, so the MultipleChoice, Ordering, FillBlank and Survey branches of the formula that sets every lesson timer are unasserted. The slice that moves the runner onto the supertype deletes that function; until then the gap stands.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-6-question-display.md`
  summary: The runner slice must treat an unreadable difficulty as EASY when deciding pool membership, because that is what the server pays. `QuestionDisplay.difficultyOrNull` deliberately does **not** do this — it reports what the wire says.
  evidence: `lesson-reward.js:199` is `String(content.difficulty || "EASY").toUpperCase()`, so a question with an absent or empty difficulty is allocated time and priced as easy on the server. If the client instead puts it in no pool, the server pays for a question the player was never shown. The split is deliberate: the supertype describes the payload faithfully — the spec's frozen Matrix requires "reports unknown", and an E2.4 test pins verbatim carriage — while the server-parity rule belongs where the pool is chosen, and can be tested there.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-7-redacted-refused-by-name.md`
  summary: `dedupeTranslatedVariants` runs before the payload is parsed and always prefers the canonical variant, so a redacted canonical question hides a playable translation of itself.
  evidence: Pre-existing — today an unparseable canonical drops the question entirely and its translation is never considered. It matters more once redaction is live, though step 9 redacts each `{id}__{lang}` variant separately, so in practice both would be withheld together. Fixing it means deduping after the parse, which moves where `codeAnswerIndex` is assigned.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-7-redacted-refused-by-name.md`
  summary: A lesson whose questions arrived without their answers is still priced and still sold. `ObserveLessonUnlockPricesUseCase.kt:72` already parses through `parseForDisplay` and counts a redacted question's characters toward the price, so a player can see a price, spend nolics to unlock, enter, and only then be told the questions have no answers.
  evidence: Nothing on the lesson card marks it beforehand, and the failure screen's only control is Back. Found while adding the named refusal, which makes the dead end legible without making it avoidable.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-7-redacted-refused-by-name.md`
  summary: `RunnerQuestion.Invalid` stays dead. It is referenced nowhere and never constructed, and its `parseError: String` describes a payload that failed to parse — a redacted payload parses fine, so populating it would mean inventing a message.
  evidence: Considered and rejected for this slice: nothing consumes `Invalid`, so constructing them would leave it functionally dead anyway, and the refusal needs a count rather than a per-question record. Its KDoc is now wrong twice over — it claims such items are filtered at init, and it describes a two-way world that is three-way after this change.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-8-server-scores-an-attempt.md`
  summary: A server-scored aborted attempt would score far higher than the client's. `buildCodeAnswerOnAbort` (`RunnerLogic.kt:149-154`) writes `'1'` into the positions of questions that were shown and abandoned, but produces no answer row for them — so a server given only the answers list writes `'0'` there instead, and `'0'` is dropped from the percent's denominator while `'1'` counts in it.
  evidence: Measured: one correct answer then two abandoned questions is `"911"` → **33%** on the client and `"900"` → **100%** on the server. Blocking for the slice that makes the server the scorer: it needs either the abandoned positions sent explicitly, or a stated rule for them. `scoreAttempt` deliberately does not guess.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-8-server-scores-an-attempt.md`
  summary: The submit handler must hand `scoreAttempt` the same eligible pool the attempt was actually played against; a mismatched pool is reported as out-of-range rather than mis-scored.
  evidence: The codeAnswer's length is the pool size and each digit's position is its question's `codeAnswerIndex`, so a pool that differs from the one the client played shifts every later digit and changes the percent's denominator. Growing the string to fit an out-of-range index would silently do exactly that, which is why it is refused instead.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-9-catalog-redaction-plan.md`
  summary: The "both halves from one `redact` call" invariant could be removed outright by making the shuffle deterministic per question — a seed derived from a server-side secret and the question id (an HMAC), so any call at any time produces the same permutation.
  evidence: Today the shuffle draws fresh entropy, which is why keys written before a payload is redacted describe a permutation that was never published, and why the backfill's key-only mode must be superseded by a both-halves rewrite. A secret-keyed seed is unpredictable to clients (the earlier objection to `Math.random` was recoverable state, not determinism itself) and reproducible on the server. It introduces a secret to manage, so it is an architecture-spine decision, not a slice.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-9-catalog-redaction-plan.md`
  summary: A republish of a lesson that holds an already-redacted payload would write a `keys` list without that question's key — publication assigns each per-lesson document whole into a merge batch, and list fields replace whole on republish — so the stored key for a redacted question is cleared by the next publish of its lesson.
  evidence: Latent today: nothing writes redacted payloads yet. Found because the backfill planner withholds such lessons deliberately, which makes it stricter than the publication path it is documented as mirroring. The fix belongs in `publicDocuments`: carry forward the existing key for any question whose payload is already redacted, which requires the publish path to read `question_keys` before it writes.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-9-catalog-redaction-plan.md`
  summary: The backfill does not reconcile against what `question_keys` already holds — a dry run cannot say which documents are new, which will be replaced, or which existing documents (lessons since deleted, or with every question archived) will be left behind as orphans.
  evidence: The script never reads the key collection. "The second run replaces each lesson's document" holds only for lessons still present; a lesson that vanished between runs keeps its stale keys forever, the same gap already recorded for publication.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-9-catalog-redaction-plan.md`
  summary: `scripts/` tests are in no gate — `scripts/package.json`'s `test` runs two suites by hand and `ciCheck` wires only `functions` `npm test` — so the backfill script's walk, dry-run gate and write can only be verified by an emulator session nobody automates.
  evidence: The pure planner is gated through `functions/`; the shell is not. A `scriptsTest` Exec task beside `functionsTest`, plus an in-memory fake Firestore in the style of `scripts/content-catalog-index.test.js`, would close it.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-9-catalog-redaction-plan.md`
  summary: Archival lives on the quest document, not the question — `setPublicQuestShelf` sets `archived` on `quests/{id}` only, and publication and every seed script write `archived: false` on questions — so a question-level filter skips almost nothing, and questions under an archived quest are keyed and counted as live.
  evidence: The backfill honours the question flag because `questionRowFor` on the reward path reads the same field, and says in its report that quest-level archival is not consulted. Making the count mean something needs the quest → section → theme → lesson chain resolved before planning; the same chain is what a later "which lessons no longer exist" reconciliation needs, so the two belong together.

- source_spec: `_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md`
  summary: Owner runbook before the first real purchase can settle — link Play to the Firebase project and grant the function's service account.
  evidence: `verifyPurchase` authenticates to the Play Developer API with the Cloud Functions runtime service account (ADC). Nothing works until the owner does, in order — (1) Play Console → Setup → API access → link Google Cloud project `school-quiz-89336951`; (2) Play Console → Users and permissions → invite the runtime service account of the 2nd-gen functions (default `762375057396-compute@developer.gserviceaccount.com`; confirm on the deployed function in Cloud Console) with "View financial data" and "Manage orders and subscriptions" for `com.tpov.schoolquiz`; (3) enable "Google Play Android Developer API" in that Cloud project; (4) create consumable in-app products `gold_pack_small`, `gold_pack_medium`, `gold_pack_large` with prices — gold amounts come from the server table `configs/economy.goldPacks` (bootstrap 10 / 60 / 150). Until (1)–(3) are done every verification answers `unavailable` and the purchase stays unconsumed; the wiring logs an error naming this cause on 401/403.
- source_spec: `_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md`
  summary: Receipts cannot be marked as read by the device — `users/{uid}/receipts` is owner-read only.
  evidence: ADM-3 wants the client to show a receipt and mark it read; the rule is `write: if false`, so marking needs a narrow owner-write rule on a `readAtMs` field or a callable. Belongs to the story that renders receipts (epic 5, 5.4).
- source_spec: `_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md`
  summary: No Firestore rules test harness exists — the new `receipts` / `purchase_settlements` / `purchase_audit` rules are verified by reading only.
  evidence: `functions/package.json` has no `@firebase/rules-unit-testing`; no rules test anywhere in the repo. One emulator-backed suite would cover every server-owned collection at once.
- source_spec: `_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md`
  summary: Monetary callables have neither App Check enforcement nor a per-user rate limit.
  evidence: Any authenticated account can call `verifyPurchase` with random tokens and burn Play Developer API quota (and Firestore reads) until legitimate purchases answer `unavailable`. Same posture as every other callable today; the money path is where it costs most. Decide project-wide (enforceAppCheck + a per-uid attempt counter in the settlement pre-read).
- source_spec: `_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md`
  summary: Story 1.2 must set `setObfuscatedAccountId(sha256(uid))` on every purchase flow so the server can bind a token to its buyer.
  evidence: The server refuses a token whose `obfuscatedExternalAccountId` names another account and accepts an absent id only for older clients; once every client sets it, the server check can be tightened to "required".

- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 5.5)
  summary: Четыре прямых callable (`setPublicQuestShelf`, `submitReviewAction`, `cancelNicknameListing`, `cancelLogoListing`) живы на сервере и обходят ключ идемпотентности; на клиенте `submitReviewAction` оставлен ради чужого инструментального теста.
  evidence: Разбор эпика 5: по прямому `submitReviewAction` двойное начисление репутации по-прежнему возможно — AD-6 не закрыт, пока они не сняты. `deferred-actions.test.js` даже требует их существования (`callableBlock`). На клиенте `ReviewAssignmentRemoteDataSource.submitReviewAction` держит только `apps/.../LiveFirebaseReviewWorkflowInstrumentedTest.kt` параллельной сессии. Снять все четыре с сервера и с клиента после переезда того теста на `submitMutation`, страж в тесте перевернуть на «callable нет».
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (эпик 5, разбор)
  summary: Окно между эффектом тела и записью завершения ключа: инстанс, умерший между ними, оставляет резерв, и через таймаут повтор выполняет тело второй раз.
  evidence: `submitMutation` пишет `completionRecord` отдельной записью после `resolved.handler`. Для снятия лота второе выполнение отвечает `not-found` → клиент уводит в карантин → «уже продан» на снятом лоте. Для ревью закрыто детерминированным `reviewId` из ключа; для остальных тел — либо тело само узнаёт свой эффект по ключу, либо завершение ключа пишется в той же транзакции, что и эффект (передавать `transaction` в обработчики — заметный рефакторинг приёмника).
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (эпик 5, разбор)
  summary: `FakeOutboxStore` продублирован четыре раза, потому что у `shared/core/outbox` нет модуля test-fixtures.
  evidence: Копии в `shared/feature/quest/data/.../fake/`, `shared/feature/internet/profile/data/.../outbox/`, private в `QuestAuthoringRepositoryImplTest.kt` и `ReviewAssignmentRepositoryImplTest.kt`; канонический — в `shared/core/outbox/src/commonTest`. Правило testing.md запрещает дубли при наличии канонического, но commonTest другим модулям недоступен. Нужен `shared/core/outbox/test-fixtures` по образцу `android/feature/quest/test-fixtures` — правка scaffold, `backend-dev`.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (эпик 5, разбор)
  summary: Мелочи из разбора эпика 5, не блокирующие: `applySetPublicQuestShelf` не сверяет владельца квеста; любая причина карантина снятия лота рисуется как «уже продан»; обработчики карантина без локальной половины не отличимы в рантайме от забытых.
  evidence: `functions/index.js` `applySetPublicQuestShelf` — существующий долг (полку ставит уровень разработчика, не автор). `ListingCancelQueue.observe` маппит `QUARANTINED` в `REFUSED` без чтения `lastError` — permission-denied и invalid-argument выглядят как продажа. `QuestShelfQuarantineMark` / `ListingCancelQuarantine` — `Unit` в `onQuarantined`, KDoc обещает различимость, которой нет; хватит лога с `operation` и `entity_ref`.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-10-served-questions.md`
  summary: `LessonResultRemoteDataSource.submitAttempts` and `LessonResultAttemptEvent` (with `FirebaseLessonResultRemoteDataSource.toCallableMap`) appear to have no live caller since the result path moved onto the unified outbox — the attempt now travels as `lesson_runner.SUBMIT_ATTEMPT` with the JSON body `LessonResultOutboxWriter` builds, dispatched server-side through `MUTATION_HANDLERS`.
  evidence: A repo-wide search finds `submitAttempts(` only on the interface and its Firebase implementation. A second wire shape for the same event is a drift hazard: a field added to one and not the other is invisible until the dead path is revived. Confirm and delete, or route the outbox through it.

- source_spec: `_bmad-output/specs/spec-charges/SPEC.md` (CAP-11, история 12; открытый вопрос 4)
  summary: Sweep подделываемых балансов невозможен без журнала начислений — сначала ledger, потом сверка.
  evidence: `functions/index.js` `writeUserProgressDelta` пишет только `FieldValue.increment` в `users/{uid}`; событие попытки хранит вход награды, но не сумму (`attemptReward` не сохраняется); `generateGiftBoxReward` — случайный бросок без записи; `buyStandardHeart`/`buyGoldHeart` не журналируются. Историю, из которой можно пересчитать нолики, никто не писал. Прежде чем сравнивать, нужен append-only `ledger/{uid}/entries`, который пишет каждая транзакция, трогающая нолики, skill или заряды.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 5.3, NFR4)
  summary: Операция разблокировки урока называется `UNLOCK_LESSON` без пространства имён фичи, тогда как все остальные — `<фича>.<ИМЯ>`.
  evidence: `OutboxOperations.kt` — `UNLOCK_LESSON = "UNLOCK_LESSON"` рядом с `lesson_runner.SUBMIT_ATTEMPT`, `quest.SET_SHELF` и т.д.; сервер зарегистрирован под тем же голым именем. NFR4 требует, чтобы имя операции называло владеющую фичу. Живых установок нет — переименовать в `lesson_runner.UNLOCK_LESSON` на обеих сторонах одним коммитом, страж `OutboxOperationsRegistryTest` поймает расхождение.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-10-served-questions.md`
  summary: Translated-variant ids reach the wire unresolved — `dedupeTranslatedVariants` can put `q1__uk` into the play order when the canonical is absent locally, so `served.questionId` (and, pre-existing, the answer rows) name the variant, and the server-side scorer matches ids exactly against the pool it is handed.
  evidence: **Resolved while specifying E2.11:** no canonicalisation is needed. Each translated variant is its own question document with its own key entry (the key store keys `q07__ru` beside `q07`), so a served `q1__uk` matches its own key. What the wiring step must do instead is build the scoring pool from `served` — the dealt ids at their positions, padded with `'0'` — rather than from the lesson's full document list, because the client's play order was deduped and the server's document list is not.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-10-served-questions.md`
  summary: Attempts queued before this change — and the rows `Migration5to6` lifts from the old `lesson_result_attempt_outbox` — carry no `served`, which the server-side scorer reads as `SERVED_UNKNOWN` and refuses.
  evidence: Those rows still carry the client's `codeAnswer`, so they need no `served` at all. The wiring step must treat "absent `served` with a present `codeAnswer`" as the legacy client-scored path rather than as a refusal, and only require `served` when `codeAnswer` is absent. Related: the server's `readServed` documents an empty list as "opened and closed again", while the client's abort path sends the whole play order with `'1'`s — the two descriptions of an abandoned run must be reconciled before scoring is switched on.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (эпик 10, разбор)
  summary: `DeviceTokenSwitchEmulatorTest` не может пройти даже с эмулятором: читает `users/{uid}/devices` с клиента, а правило для `devices` — `allow read: if false`.
  evidence: `apps/android-next/src/androidTest/.../push/DeviceTokenSwitchEmulatorTest.kt` (`devicesOf`); `firestore.rules:29`. Читать документы устройств в тесте надо через REST эмулятора с `Authorization: Bearer owner`, а сценарий вести через `signInAs`, а не повторять шаги руками. Тест компилируется и в gate не входит.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 10.3, Open Questions)
  summary: Событие `qualification.AUTHOR_RATING_CHANGED` шлётся при любой ненулевой дельте рейтинга автора, включая прежнего адресата при переносе, — порог и состав не решены, риск потока уведомлений.
  evidence: `writeRatingQualificationDelta` возвращает `changedUids`, `aggregateDirtyQuestRatingDoc` шлёт всем. Решение продуктовое: слать только при смене уровня квалификации (ADR-0006), а не при каждой дельте.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 10.5, Open Questions)
  summary: `detachDeviceToken` не доказывает, что вызывающий владеет токеном: любой аутентифицированный клиент, знающий чужой токен, снимет чужой документ; лимита частоты нет.
  evidence: `functions/push-notifications.js` `detachDeviceToken` проверяет только auth и «не свой uid». Владение доказуемо через подписанный сервером nonce в документе устройства либо через сверку токена с тем, что клиент зарегистрировал под своим uid.
- source_spec: `_bmad-output/planning-artifacts/epics-sync.md` (Story 10.2, AC «контракты в shared/core/sync»)
  summary: SDK-free контракты push (`PushNotifier`, `DeviceTokenSource`, `DeviceTokenStore`, `DeviceTokenRelease`, `ForeignTokenDetacher`) лежат в `platform/firebase/.../push/PushContracts.kt`, а не в `shared/core/sync/src/commonMain`, как требует AC.
  evidence: Единственная реализация сегодня — Android/Firebase, и `auth/FirebaseGoogleSignInRepository` импортирует `DeviceTokenRelease` внутри того же модуля. Перенос механический (пакет + импорты в ~8 файлах + зависимость `platform/firebase` от `shared/core/sync`); делать вместе с iOS-стороной или при первом втором потребителе. Задокументированное отклонение от AC.
- source_spec: `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-31/ARCHITECTURE-SPINE.md` (AD-9)
  summary: Доменного контракта времени в `shared/core` нет; `pushModule(nowMs = ...)` — четвёртая лямбда-копия «единого источника» рядом с `SyncModule` (три).
  evidence: `AppApplication.kt` и `SyncModule.kt` передают `{ System.currentTimeMillis() }` в каждый модуль отдельно. AD-9 требует один инжектируемый контракт; завести `shared/core/time` (`interface Clock`/`TimeSource`) и одно связывание в composition root.

- source_spec: `_bmad-output/specs/spec-charges/SPEC.md` (CAP-13; ревью ядер, линза паритета)
  summary: `StartLessonAttemptUseCase.canonicalQuestionId` считает суффикс языком по `isLetter()`, а сервер — только по `[A-Za-z-]`; `q1__ру` схлопывается в раннере и не схлопывается на сервере.
  evidence: Та же расходимость в `LessonAllocatedSeconds.kt` уже устранена и пиннится фикстурой «кириллический суффикс — не язык»; копия в раннере осталась — модуль `shared/feature/lesson-runner/domain` правит другая сессия. Одна строка и тест; делать, когда модуль освободится, и вынести правило в одно место (`shared/core/scoring`), чтобы третьей копии не появилось.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-11-attempt-intake.md`
  summary: Eight helpers (`stringValue`, `numberValue`, `normalizeContentEvent`, `normalizeLessonAnswers`, …) are copied verbatim from `functions/index.js` into `functions/attempt-intake.js`, held equal by a test that cuts their source text out of `index.js` and compares it.
  evidence: The copy exists because `index.js` cannot be required in a plain node test (it initialises firebase-admin on load) and because that file is under constant edit by a parallel session. The right fix is a small pure module both files require; it touches `index.js`, so it waits for the wiring step. Until then the source-text test is the tripwire, and it depends on column-0 brace formatting.

- source_spec: `_bmad-output/implementation-artifacts/spec-e2-11-attempt-intake.md`
  summary: What the queue does when the intake rejects a replayed body is undecided — a queued `lesson_runner.SUBMIT_ATTEMPT` whose `served` cannot be read would be `invalid-argument` on every replay.
  evidence: The intake reserves `invalid-argument` for a body that cannot be read at all (a lie is kept and marked instead), so a rejection is final by construction; the queue engine has to treat it as quarantine (AD-28), not retry. One sentence in the wiring step and a queue-level test settle it.
