---
title: 'Фикс-пак после ретро эпика 4 (authoring-review, 2026-10-28): сверка COPY_EDIT-платы с фактическим диффом + триаж action items'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: ['multiple-goals', 'oversized', 'no-subagent-capability-inline-lenses']
baseline_revision: '57ecedb8'
deferred:
  - summary: >-
      TOCTOU-гонка первой записи отчёта об ошибке: проверка существования и запись
      lesson_error_reports не в транзакции — два одновременных первых submits под одним
      reportId от разных uid могут перезаписать друг друга (last-write-wins).
    evidence: |-
      applyLessonErrorReport: reportRef.get() затем reportRef.set() вне runTransaction
      (functions/index.js:2302-2337). Новая сверка REPORT_ID_NOT_OWNED закрывает
      последовательный повтор, но не межпроцессную гонку двух первых подач.
    location: >-
      functions/index.js:2302
    severity: medium
  - summary: >-
      Key-wipe: applyCopyEditProposal передаёт в questionKeyDocuments только validated
      (подмножество правки), а ключевой документ один на урок и заменяет список keys
      целиком — применение правки стирает ключи всех неперечисленных вопросов урока.
    evidence: |-
      functions/index.js:6159-6163 (questionKeyDocuments(validated...)); document shape:
      functions/question-key-store.js:367-410 (один документ на урок, массив keys заменяется
      при merge-write). Последствие для scoring не трассировано до конца (публичные payload
      сейчас полные — redaction-гейт выключен); решение владельца: перестройка из полного
      набора урока меняет shuffles неизменённых вопросов (инвариант ONE redact call).
    location: >-
      functions/index.js:6159
    severity: high (unverified)
  - summary: >-
      Статусная несовместность видов: applyNewLessonProposal переводит proposal в APPROVED
      внутри транзакции, applyCopyEditProposal — нет; после применения правки запись остаётся
      CHECK_PASSED (повтор отказаёт по STALE — проверено тестом, второго наложения нет).
    evidence: |-
      functions/index.js:6290-6300 (NEW_LESSON ставит APPROVED) против 6128-6183 (COPY_EDIT
      не пишет proposalRef). Повтор одобрения COPY_EDIT отвечет PROPOSAL_STALE
      (lesson-proposals.test.js, «повтор одобрения после применения»).
    location: >-
      functions/index.js:6343
    severity: medium
---

<intent-contract>

## Intent

**Problem:** Ретро эпика 4 (RETROSPECTIVE-2026-10-28, коммит 57ecedb8) нашло HIGH-дыру в денежном пути (L-new-1): плата COPY_EDIT списывается по декларации клиента — `100 × payload.editedQuestionIds.length` (functions/index.js:5991), а применение одобренной правки никогда не сверяет заявленный набор с фактическим диффом: `mergedQuestionsForApply` вычисляет реально изменённые вопросы (functions/lesson-copy-edit.js:137-148), но `applyCopyEditProposal` игнорирует результат (index.js:6114-6117) — можно заявить одно изменение (заплатить 100) и провести до 64. Попутно в той же функции: применение пишет все провалидированные вопросы (churn версий/sync-changes) и мёртвый тернарник `contributorUids: firestoreTypes.FieldValue ? null : null` (index.js:6153) — L-new-2. Кроме того, ретро подтвердило шесть dev-дефектов, переносимых вторую волну (L1/L5/L7/L8/L10/L11).

**Approach:** Сервер — источник истины: в момент применения сверять заявленный набор `editedQuestionIds` с вычисленным диффом; при расхождении — именованный отказ `PROPOSAL_EDITED_SET_MISMATCH` до транзакции (плата остаётся сгоревшей — консервация не нарушается, повтор детерминирован). Применение пишет только реально изменённые вопросы. Шесть переносимых дефектов закрываются малыми правками по своим поверхностям, каждая с тестом. Остальные пункты ретро — триаж в таблице ниже (owner-пункты не кодируются).

## Boundaries & Constraints

**Always:**
- Именованные отказы во всех новых ветках (стиль дома: `PROPOSAL_*`, никаких молчаливых правок).
- Денежные инварианты: консервация (сгоревшее не возвращается), идемпотентный повтор (та же запись/тот же отказ).
- Коммиты только pathspec-ом: `git add <свои файлы> && git commit -- <свои файлы>`; staged-удаления (~6145) не трогать; счётчик сверить до/после.
- Гейты: functions `node run-tests.js` (docker node:22 на VPS, mount корня репо), gradle только через `sgw` (JAVA_HOME=/opt/jdks/jdk-27), ktlint затронутых модулей.

**Never:**
- Не менять тариф и порядок списания на приёмке (index.js:5991 остаётся декларационным — сверка замыкается на применении).
- Не трогать вход `questionKeyDocuments` в applyCopyEditProposal (validated): перестройка ключевого документа из полного набора урока меняет shuffles (инвариант «both halves from ONE redact call») — отдельная находка владельцу, не в этом пакете.
- Не деплоить, не трогать sprint-status-monetisation.yaml, чужие net-правки (social/polls + outbox, 13 файлов), untracked-скрипт арены.
- Не делать голый `git commit` / `git commit -a`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Честное применение | declared == actual (дифф по базе) | Правка применяется, пишутся только изменённые вопросы; версия урока +1 | Нет |
| Заявлено меньше факта | declared={q1}, фактический дифф={q1,q2} | Отказ `PROPOSAL_EDITED_SET_MISMATCH` ДО транзакции; ни одной записи; proposal остаётся CHECK_PASSED; плата сгоревшей | Именованный отказ |
| Заявлено больше факта | declared={q1,q2}, дифф={q1} | Тот же отказ (декларация ≠ одобренное автором) | Именованный отказ |
| Пустой список на применении | declared=[] (обход парсера) | Отказ `PROPOSAL_EDITED_SET_MISMATCH` (на подаче пустое режет парсер) | Именованный отказ |
| Повтор применения | после успешного apply повторный APPROVE | Именованный отказ без второй записи (STALE по версии урока) | Именованный отказ |
| Нет диффа | все вопросы байт-в-байт с базой | `PROPOSAL_NOTHING_CHANGED` (как раньше) | Именованный отказ |
| Replay отчёта чужим | reportId существует, stored.reporterUid ≠ uid | Отказ, ложного успеха нет (L5) | Именованный отказ |
| Replay отчёта своим | тот же uid, тот же lessonId | Сохранённая запись (как раньше) | Нет |
| Причина перехода > 1000 байт | transitionLesson(reason) | `INVALID_ARGUMENT` (L10) | Именованный отказ |
| Дубликаты в проекциях | questionOutcomes с повтором questionId / failedQuestions с повтором ключа | Проекция отказывает (L11, guard работает) | `UNAVAILABLE` |

## Retro Triage (обязательная таблица)

| Пункт ретро | Владелец | Решение в этом пакете |
|---|---|---|
| 1. L-new-1 плата/применение (HIGH) | dev | **Закрыт**: сверка declared vs actual при применении, отказ `PROPOSAL_EDITED_SET_MISMATCH`; тесты оба направления, пустой, повтор |
| 2. L-new-2 churn + мёртвый тернарник | dev | **Закрыт**: пишутся только изменённые; тернарник удалён |
| 3. L1 целые формы парсера | dev | **Закрыт**: `requiredLong`/`optionalLong` через `as? Number` (как InboxWireParser), тест Int-форм |
| 3. L5 владелец replay отчёта | dev | **Закрыт**: сверка reporterUid+lessonId, именованный отказ; новая сюита на стабе Firestore |
| 3. L7 ключ ретрая куратора | dev | **Закрыт**: pendingTransition закрывается только квитанцией; тест повторного ключа |
| 3. L8 история редактора | dev | **Закрыт**: no-op не пушится; серия текстовых правок = один шаг; тесты |
| 3. L10 byte-bound причины | dev | **Закрыт**: 1000 байт в arena-service.transitionLesson; тест |
| 3. L11 guard, который не может сработать | dev | **Закрыт**: `seen.has` до `seen.add` на обоих местах; тесты |
| 4. A2 четвёртая семья парсеров + A3 split ResultContent | dev (defer по ретро) | **defer**: «next shift touching lesson-runner presentation» — не эта волна; остаётся трекнутым |
| 5. CAP-3 нога «affected dependencies are rechecked» | owner | **owner**: заказать историю или примирить формулировку SPEC — openItems |
| 6. OQ-10 через 4.20 + два hitl-прогона (4.20/4.26) | owner | **owner**: риг — openItems |
| 7. A5b evidence-dir контракт в заголовках историй | owner/registry | **owner**: openItems |
| 8. A5c fast-tracked спеки (lenses_ran: [] на всех спеках волны) | owner/process | **owner**: требовать verification-gap линзу по денежным путям перед done-flip или принять ретро как компенсацию — openItems |
| (новое, вне ретро) key-wipe: `questionKeyDocuments(validated)` пересобирает ключевой документ урока только из подмножества правки | owner | **Зафиксировано, не кодируется** (см. Implementation Notes) |

</intent-contract>

## Code Map

- `functions/lesson-copy-edit.js` -- чистый модуль копии/применения; `mergedQuestionsForApply` (:137-148) возвращает `{questions, changedQuestionIds}`; сюда добавить параметр `declaredEditedIds` (обязательный) и отказ `PROPOSAL_EDITED_SET_MISMATCH` (наборы сравнивать как множества, дедупликация).
- `functions/index.js:6102-6172` -- `applyCopyEditProposal`: деструктурирует только `{questions: validated}` (:6114); цикл записи :6129-6144 (все validated); мёртвый тернарник :6153; возвращает `changedQuestions: validated.length` (:6171). Плата на приёмке: :5991 (не трогать).
- `functions/index.js:2278-2337` -- `applyLessonErrorReport`; ветка существующего документа :2303-2307 возвращает сохранённое без сверки автора (L5).
- `functions/lesson-proposals.test.js` -- сюита приёмки/решения на стабе `_firestore-stub.js` + `require("./index")`; паттерн миров `proposalWorld`/`checkedProposal`/`seedChecked`; сюда — применение COPY_EDIT end-to-end.
- `functions/arena-service.js:262` -- `transitionLesson`: `text(reason, "reason")` без границы; константа и тест-каркас в `arena-service.test.js` (:162 move()).
- `functions/arena-lifecycle-views.js:267,294` -- `unavailable(seen.add(...))` (Set.add возвращает Set — guard не срабатывает); `unavailable(cond)` = assert (fail при falsy).
- `shared/feature/quest-authoring/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest_authoring/data/mapper/LessonLifecycleWireParser.kt:200-223` -- `unsigned/requiredLong/optionalLong` исчерпывающе матчат `is Long/is Int`; эталон семьи — InboxWireParser.requiredLong (`as? Number` + toLong()); живой прецедент — schemaVersion :77.
- `shared/feature/quest-authoring/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/quest_authoring/data/LessonLifecycleWireParserTest.kt` -- пины wire-форм.
- `android/feature/quest-authoring/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/component/DefaultCuratorComponent.kt:132` -- `pendingTransition = null` до fold; контракт в доке :51 «закрывается квитанцией».
- `android/feature/quest-authoring/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/component/DefaultCuratorComponentTest.kt` -- `a failed transition keeps the page as it was` (~:221) расширить повторным ключом; `SequentialIdProvider`.
- `android/feature/quest-authoring/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/logic/QuestionEditorHistory.kt` -- push без различий; контракт «одна правка — один шаг истории» (QuestCreateComponent.kt:171).
- `android/feature/quest-authoring/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/component/DefaultQuestCreateComponent.kt:1536-1543` -- `updateEditable` пушит до transform.
- `functions/_firestore-stub.js` -- стаб Firestore для сюит index.js.
- Клиентский контракт тела правки: `SubmitCopyEditProposalUseCase.kt` -- в теле ТОЛЬКО изменённые вопросы авторской формы, `editedQuestionIds` — их подмножество (это делает declared==actual для честного клиента).

## Tasks & Acceptance

**Execution:**
- [ ] `functions/lesson-copy-edit.js` -- `mergedQuestionsForApply` принимает обязательный `declaredEditedIds`; сравнение множеств с диффом; расхождение — `fail("PROPOSAL_EDITED_SET_MISMATCH: …")` с перечнем лишних/недостающих id -- замыкает L-new-1 на сервере.
- [ ] `functions/index.js` -- в `applyCopyEditProposal`: передавать `proposal.payload.editedQuestionIds`; писать только изменённые (версии+sync-changes); удалить мёртвый тернарник; вернуть `changedQuestions: changedQuestionIds.length` -- L-new-1+L-new-2.
- [ ] `functions/lesson-proposals.test.js` -- применение COPY_EDIT end-to-end: честное (пишутся только изменённые, чужой вопрос не тронут), declared<actual, declared>actual, пустой declared, повтор после apply -- матрица денежного пути.
- [ ] `functions/index.js` + `functions/lesson-error-report.test.js` (новая сюита на стабе) -- в `applyLessonErrorReport` сверять `stored.reporterUid === uid` и `stored.lessonId === lessonId`, иначе именованный отказ `REPORT_ID_NOT_OWNED` -- L5.
- [ ] `functions/arena-service.js` + `functions/arena-service.test.js` -- причина перехода ≤ 1000 байт (`MAX_TRANSITION_REASON_BYTES`, комментарий связывает с MAX_AUTHOR_REJECTION_REASON_BYTES и клиентским капом) -- L10.
- [ ] `functions/arena-lifecycle-views.js` + `functions/arena-lifecycle-views.test.js` -- guard дублей через `!seen.has(...)` до `seen.add(...)` на обоих местах; тесты на дубль questionId и дубль ключа failedQuestions -- L11.
- [ ] `LessonLifecycleWireParser.kt` + тест -- `requiredLong`/`optionalLong` через `as? Number?.toLong()` с комментарием семьи; пин Int-форм для decidedAtMs/contentVersion/placementVersion/rating; строка в integer-поле по-прежнему отказ -- L1.
- [ ] `DefaultCuratorComponent.kt` + тест -- `pendingTransition = null` только в ветке решённой квитанции (onSuccess); тест: после транспортного отказа повтор едет под тем же mutationId -- L7.
- [ ] `QuestionEditorHistory.kt` + `DefaultQuestCreateComponent.kt` + тесты -- no-op transform не пушится; серия текстовых правок (text/info/optionTexts/orderingItems/fillBlankText/fillBlankDistractors; у fillBlankAnswers только .text при равных isProtected) схлопывается в один шаг с обрывом redo-ветки; структурная правка после текстовой — новый шаг -- L8.
- [ ] Спека -- статус done по завершении, Implementation Notes с фактами.

**Acceptance Criteria:**
- Given CHECK_PASSED COPY_EDIT, заявляющий 1 вопрос, с двумя реально изменёнными вопросами в payload, when автор одобряет, then отказ `PROPOSAL_EDITED_SET_MISMATCH`, ни одной записи вопросов/урока, proposal остаётся CHECK_PASSED.
- Given честное предложение (declared == actual из двух вопросов урока), when применено, then изменён только заявленный вопрос (версия второго не тронута), sync-changes только по нему, `changedQuestions` в ответе = 1.
- Given применённая правка, when повторный APPROVE, then именованный отказ и состояние дерева не меняется второй раз.
- Given все перечисленные правки, when гейты, then functions `node run-tests.js` зелёный (≥141 сюит + новые), gradle затронутых модулей через sgw/JDK27 зелёный, ktlint затронутых файлов чистый.

## Implementation Notes

- (planning) Подтверждено по коду: честный клиент кладёт в тело только изменённые вопросы (SubmitCopyEditProposalUseCase.kt:14-16), поэтому declared==actual — норма; расхождение — враждебное/битое тело.
- (planning) Найдено вне ретро (зафиксировано владельцу, НЕ кодируется здесь): `applyCopyEditProposal` передаёт в `questionKeyDocuments` только validated (подмножество правки), а ключевой документ — один на урок и заменяет список keys целиком ⇒ каждое применение правки стирает ключи всех неперечисленных вопросов урока. Перестройка из полного набора меняет shuffles неизменённых вопросов (инвариант «ONE redact call») — требует отдельного решения владельца.

## Spec Change Log

- (пусто — лупбеков не было)

## Review Triage Log

### 2026-10-28 — Review pass

- Линзы запущены inline (субагент-инструмента в сессии нет; компенсирующий контроль по прецеденту
  ретро 2026-10-25): blind-hunter (7 находок; N = min(floor(√40.9 кБ)+1, 10) = 7), edge-case-hunter
  (path-перечисление + deletion-check + claims-check), verification-gap (0 гэпов, 2 other findings),
  intent-alignment (расхождений чтений интента с диффом нет). Дифф: /tmp/e4-retro-fixes.diff (VPS).
- verdicts: 11 findings — high 2, medium 5, low 3, false 1, maybe-false 0
- findings:
  - `[medium]` `[defer]` (blind-hunter) Гонка первой записи отчёта: get-then-set вне транзакции — два одновременных первых submits под одним reportId перезапишут друг друга — verify: applyLessonErrorReport (index.js:2302-2337) не оборачивает запись в runTransaction; новая сверка закрывает последовательный повтор, не гонку. Pre-existing поверхность → deferred.
  - `[low]` `[reject]` (blind-hunter) Дублирование потолка: MAX_TRANSITION_REASON_BYTES (arena-service.js:21) vs MAX_AUTHOR_REJECTION_REASON_BYTES (index.js) без кросс-файлового сентинела — reject: константы связаны комментарием на месте использования, дрейф маловероятен, а «фикс» — новый сентинел-тест поверх прямого изменения.
  - `[high (unverified по последствию для scoring)]` `[defer]` (blind-hunter) Key-wipe: questionKeyDocuments(validated) пересобирает ключевой документ урока из подмножества правки — verify: index.js:6159 + question-key-store.js:367-410 (один документ на урок, массив keys заменяется целиком). Вне пакета по границе спеки (Never) → deferred, решение владельца.
  - `[medium]` `[defer]` (blind-hunter) После успешного apply COPY_EDIT запись остаётся CHECK_PASSED (NEW_LESSON ставит APPROVED в транзакции) — verify: decideLessonProposal:6343-6347 не пишет статус, applyCopyEditProposal — тоже; повтор отказаёт по STALE (тест «повтор одобрения после применения»), двойного наложения нет. Pre-existing → deferred (паритет видов — решение владельца).
  - `[false]` `[reject]` (blind-hunter) «Забытый» pendingTransition куратора после транспортного отказа удерживает устаревшее намерение — refuted: повтор под тот же ключ — контракт AD-2 (дока :51), повторный выбор другого урока/цели минтит новый ключ (takeIf по lessonId+target, :117-122), старое намерение закрывается только решённой квитанцией — ровно обещанное поведение.
  - `[low]` `[reject]` (blind-hunter) В versions-карте остались записи незаписываемых вопросов — reject: карта in-memory, наблюдаемого поведения нет (nextVersionAtPath читает только пути записываемых вопросов и урока).
  - `[low]` `[reject]` (blind-hunter) В lesson-error-report.test.js нет кейса «stored без reporterUid» — reject: состояние невоспроизводимо текущим писателем (reporterUid пишется всегда), а поведение безопасно по построению (stringValue(undefined)="" ≠ uid → отказ).
  - `[medium]` `[defer]` (edge-case-hunter, carried: тот же дефект, что строка 1) — TOCTOU-гонка первой записи отчёта.
  - `[high (unverified)]` `[defer]` (verification-gap, Other findings, carried: тот же дефект, что строка 3) — key-wipe подмножеством. Гэпов верификации не найдено: все изменённые поверхности покрыты тестами канонических гейтов, регрессия любой из правок роняет именованный тест (матрица L-new-1 в lesson-proposals.test.js; lesson-error-report.test.js; arena-service/arena-lifecycle-views; parser/history/curator в gradle-гейте).
  - `[medium]` `[defer]` (verification-gap, Other findings, carried) — статусная несовместность видов (строка 4).
  - `[medium]` `[defer]` (intent-alignment, carried: та же наблюдаемая несовместность, что строка 4) — «повтор идемпотентен» выполняется через STALE, а не через NOT_ACTIONABLE: семантически идемпотентно (второй записи нет, отказ именованный), статусный хвост — как в строке 4. Расхождений surface-ов между интентом и диффом не найдено: P1 реализована на сервере применения (как велел.intent), P2 — код+тесты на своих поверхностях, owner-пункты не кодировались.

- Итог: patch-записей нет, лупбеков нет; 3 группы defer (TOCTOU; key-wipe; статусный паритет) +
  4 reject. followup_review_recommended = false (заплаток нет).

## Design Notes

Выбор «отказ, а не перезачёт»: перезачёт при применении — тихое движение денег в момент решения автора и новый источник двойных возвратов на повторах; отказ детерминирован содержимым proposal+базы, повтор даёт тот же отказ, плата остаётся сгоревшей по модели 4.25 («что списано — то сгорело»). Плата на приёмке не переносится на дифф: приёмка не читает вопросов (дёшево, идемпотентно), а честный клиент и так заявляет ровно дифф.

Коалесинг текстовых правок (L8): история хранит признак последнего шага; текстовая правка после текстовой не растит стек, но обрывает redo (как всякая правка); undo/redo/clear сбрасывают признак. Определение «только текст»: b.copy(текстовые поля ← a) == a; для fillBlankAnswers — равенство text-подполей при равных isProtected.

## Verification

**Commands (на VPS, ssh rnsrv):**
- `docker run --rm -v /workspace/projects/schoolquiz3.0:/repo -w /repo/functions node:22-bookworm-slim node run-tests.js` -- expected: все сюиты PASS, счётчик ≥ 141 + новые.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:data:jvmTest :android:feature:quest-authoring:presentation:testDebugUnitTest` -- expected: BUILD SUCCESSFUL.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew ktlint quest-authoring:data ktlint quest-authoring:presentation ...` (ktlintCheck затронутых модулей) -- expected:SUCCESS (кроме известной корзины).

## Auto Run Result

Status: done (см. frontmatter). Рабочее дерево: путь-специфичные коммиты; чужие staged-удаления (~6145) и чужие net-правки (social/polls + outbox, 13 файлов) не тронуты.

### Summary

Реализован фикс-пак ретро эпика 4 (2026-10-28): (1) L-new-1 — при применении COPY_EDIT сервер
сверяет заявленный набор `editedQuestionIds` с фактическим диффом по свежей базе; расхождение —
именованный отказ `PROPOSAL_EDITED_SET_MISMATCH` до транзакции; плата остаётся сгоревшей
(консервация), повтор детерминирован. (2) L-new-2 — пишутся только реально изменённые вопросы
(версии+sync-changes), мёртвый тернарник `contributorUids` удалён, ответ называет дифф.
(3) Переносимые dev-дефекты закрыты: L1 (Number-коэрсия парсера), L5 (владелец replay отчёта),
L7 (ключ ретрая куратора), L8 (no-op/коалесценция истории редактора), L10 (byte-bound причины
перехода), L11 (работающий guard дублей). Owner-пункты (CAP-3 нога recheck, OQ-10/4.20/4.26,
evidence-dir контракт, fast-tracked спеки/A5c, A2/A3 консолидации) — в triage-таблице спеки и
openItems результата.

### Files changed

- `functions/lesson-copy-edit.js` — mergedQuestionsForApply: обязательный declaredEditedIds, сверка множеств, отказ PROPOSAL_EDITED_SET_MISMATCH.
- `functions/index.js` — applyCopyEditProposal: сверка на применении, запись подмножества, честный счёт диффа, удалён мёртвый тернарник; applyLessonErrorReport: сверка reporterUid+lessonId, отказ REPORT_ID_NOT_OWNED.
- `functions/lesson-copy-edit.test.js` — сигнатура + 5 кейсов матрицы на уровне модуля.
- `functions/lesson-proposals.test.js` — e2e применение COPY_EDIT: честное (подмножество записи), заявлено<факта, заявлено>факта, пустая декларация, повтор после apply.
- `functions/lesson-error-report.test.js` (новый) — replay-владение отчётом: 4 кейса на стабе Firestore.
- `functions/arena-service.js` + тест — byte-bound 1000 причины перехода (граница и до-записная форма).
- `functions/arena-lifecycle-views.js` + тест — guard дублей через has-before-add на обоих местах.
- `shared/.../LessonLifecycleWireParser.kt` + тест — requiredLong/optionalLong через `as? Number` (семья InboxWireParser), пин non-Long форм и отказа строке.
- `android/.../DefaultCuratorComponent.kt` + тест — pendingTransition закрывается только решённой квитанцией; ретрай едет тем же mutationId.
- `android/.../QuestionEditorHistory.kt`, `DefaultQuestCreateComponent.kt` + тесты — no-op шага не заводит; 6 typing-обработчиков коалесцируют серию набора в один шаг (redo обрывается); дискретные действия — свои шаги (контракт UndoRedoTest сохранён).
- `_bmad-output/implementation-artifacts/spec-e4-retro-fixes.md` — эта спека.

### Review findings breakdown

Линзы inline (субагентов в сессии нет; warning `no-subagent-capability-inline-lenses`).
11 findings: high 2, medium 5, low 3, false 1. Заплаток нет (patch = 0), лупбеков нет.
Deferred (3 группы, см. frontmatter `deferred`): TOCTOU-гонка первой записи отчёта (medium);
key-wipe ключевого документа подмножеством правки (high unverified — вне пакета, владельцу);
статусный паритет COPY_EDIT/NEW_LESSON после apply (medium). Rejected: 3 low (дублирование
константы потолка — связано комментарием; мёртвые записи versions-карты; отсутствующий кейс
legacy-дока — невоспроизводим) + 1 false (кураторское намерение — контракт AD-2, refuted).

### Verification performed

- `docker run --rm -v /workspace/projects/schoolquiz3.0:/repo -w /repo/functions node:22-bookworm-slim node run-tests.js` — **142 of 142 suites PASS** (141 предсуществующих + новая lesson-error-report; две итерации отладки собственных тестов до зелёного).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:data:jvmTest :android:feature:quest-authoring:presentation:testDebugUnitTest` — **BUILD SUCCESSFUL** (первый прогон поймал конфликт с контрактом DefaultQuestCreateComponentUndoRedoTest → переработка коалесценции на тип обработчика; второй — зелёный).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:data:ktlintCheck :android:feature:quest-authoring:presentation:ktlintCheck` — нарушений в моих файлах нет (presentation — 0; data — только предсуществующая корзина, все её файлы не изменены относительно HEAD, проверено git status).
- Матрица I/O спеки покрыта прогнанными тестами (аудит step-03): каждая строка имеет именованный зелёный тест.

### Residual risks

- Дефернутые находки ревью (frontmatter `deferred`) — не чинились сознательно.
- Key-wipe hazard (предсуществующий) остаётся до решения владельца.
- Живые rig-прогоны (4.20/4.26) не выполнялись — hitl, вне пакета.
