---
title: '4.22 proposal-error-report — «Сообщить об ошибке» становится предложением (CAP-6/27, UX-23)'
type: 'feature'
created: '2026-10-28'
status: 'in-progress'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: 'd074873e867896ec5061530a15b12204170711d1'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Ядро предложений 4.21 приземлило запись REPORT и её одобрение (пометка урока `correctionRequestedAtMs`), но у игрока нет входа: старый путь «Сообщить об ошибке» (4.9, мутация `lesson_report.SUBMIT_ERROR` в inbox) остался без UI после удаления ReviewQueue-экранов в 4.19, и ни одна поверхность не создаёт REPORT-предложение. CAP-6 требует: приватный отчёт (текст про весь урок + опционально свой вариант) — это CAP-27-предложение, проходит проверки и доходит до автора в «Предложениях»; отчёты, доставленные до перемены, остаются читаемыми. UX-23: вход после урока, оканчивается «Предложить автору», показывает плату до отправки.

**Approach:** Вход «Сообщить об ошибке» на экране результата урока (lesson-runner, `ResultContent` — сразу после прохождения, рядом с обсуждением): диалог с текстом про весь урок, опциональным «Своим вариантом» (свободный текст — форма REPORT в 4.21), строкой платы из цитаты (`quoteLessonProposalFee`, kind REPORT — «Бесплатно») и кнопкой «Предложить автору». Подача — отложенная мутация `quest_authoring.SUBMIT_PROPOSAL` (приёмник 4.21): доменная модель + use case в quest-authoring, outbox-writer с ключом AD-2, клиентский парсер цитаты со сторожем версии вью. Приватность — по построению: тело REPORT не несёт ни вопросов, ни ответов (сервер 4.21 отвергает лишние поля), видимость записи решает сервер (вью 4.25). Прежние отчёты (`lesson_error_reports` + inbox-вью) не тронуты и остаются читаемыми — старая мутация живёт для старых клиентов.

**Зависимости:** 4.21 done (d074873e: приёмка REPORT, цитата, решение). Deviation от build order не требуется (4.21 закрыт).

## Boundaries & Constraints

**Always:**
- proposalId назначается в момент нажатия и не меняется (AD-2), ключ мутации выводится из него; повтор того же ключа возвращает сохранённое (приёмник 4.21).
- Тело: `{proposalId, kind: "REPORT", target: {lessonId}, text, ownVariant?}` — ровно поля парсера 4.21; лишнее поле — несовпадение контракта, в очередь не попадает (писатель строит payload по белому списку).
- Цитата до отправки: view `lesson-proposal-fee-quote-v1`, строгий парсер со сторожем версии; имя callable — `quoteLessonProposalFee` (сентинел читает functions/index.js).
- Тексты: текст отчёта ≤2000 символов (граница парсера 4.21, `MAX_REPORT_TEXT_CHARS`), трим на клиенте; границы дублирует use case (отказ до постановки в очередь).
- Никаких выдуманных статусов на клиенте: подача кладёт намерение в очередь и подтверждает «Отправляется» — исход проверки и решение автора приходят вью 4.25.

**Never:**
- Никаких изменений сервера приёмки REPORT, воркера, решения (4.21); никаких новых коллекций и правил.
- Никаких изменений inbox-мира 4.9 (`lesson_error_reports`, `inbox-views.js`, старая мутация `lesson_report.SUBMIT_ERROR` остаётся).
- Никаких поверхностей «Предложений» (4.25), никаких правок копии (4.23) и целевых путей (4.24).
- Никаких правок quest-create (это 4.24).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Вход после урока | игрок завершил урок | кнопка «Сообщить об ошибке» на экране результата открывает диалог; плата «Бесплатно» из цитаты | цитата недоступна → строка платы «—», отправка не блокируется (плата сервер сочтёт сам) |
| Подача с текстом и вариантом | оба поля валидны | намерение в очереди (operation SUBMIT_PROPOSAL), диалог закрыт с подтверждением | пустой текст — кнопка неактивна |
| Границы текста | >2000 символов | отказ use case до очереди | счётчик/подпись границы в диалоге |
| Повтор ключа | тот же proposalId уехал дважды | приёмник возвращает сохранённое, платы нет (0 у REPORT и так) | нет |
| Сервер отказал (урок снят) | PROPOSAL_TARGET_NOT_PLACED | запись уходит в карантин владельца фичи (механика outbox) | пользователь видит отказ отправки |
| Старые отчёты | inbox открывают | приватные отчёты 4.9 читаются как прежде (сюита inbox-views зелёная) | нет |

## Code Map

- `shared/feature/quest-authoring/domain`: `model/ErrorReportProposal.kt` (намерение: id, ownerUid, requestedAtMs, command {lessonId, text, ownVariant?}); `repository/LessonProposalRepository.kt` (интерфейс: queueErrorReportProposal); `use_case/SubmitErrorReportProposalUseCase.kt` (трим, границы ≤2000, mint id "lesson-proposal", постановка в очередь); `use_case/QuoteLessonProposalFeeUseCase.kt`; DI-фабрики в `QuestAuthoringDomainModule`.
- `shared/feature/quest-authoring/data`: `outbox/ProposalOutboxWriter.kt` (operation `SUBMIT_PROPOSAL`, payload по белому списку REPORT, entityRef `quest_authoring:proposal:<id>`, без локальной половины — AD-25); `LessonProposalRepositoryImpl` (OutboxStore); `remote/LessonProposalQuoteRemoteDataSource.kt` + `mapper/LessonProposalQuoteWireParser.kt` (view-сторож, границы); DI в data-модуле.
- `platform/firebase`: `FirebaseLessonProposalQuoteRemoteDataSource.kt` (callable `quoteLessonProposalFee`) + DI-binding; тест `LessonProposalQuoteCallableNamesTest` — сентинел читает `functions/index.js` (образец QuestReviewDecisionsCallableNamesTest).
- `android/feature/lesson-runner/presentation`: зависимость `:shared:feature:quest-authoring:domain`; на `ResultContent` — строка-действие «Сообщить об ошибке» (рядом с DiscussionRow), `ErrorReportDialog` (текст, сворачиваемое поле «Свой вариант», строка платы, кнопка «Предложить автору», состояние «Отправляется…»); DI через `LessonRunnerPresentationModule` (use case из Koin); строки ×3 локали (values, values-ru, values-uk); компонент-тест диалога (валидация границ, подтверждение подачи, неактивная кнопка при пустом тексте).
- Сентинел провода: jvmTest в quest-authoring data `ProposalReportWireSentinelTest` — читает `functions/lesson-proposals.js` и сверяет ключи payload писателя с белым списком парсера REPORT (по образцу OutboxOperationsRegistryTest).
- Do not touch: `functions/*` (кроме ничего), inbox-мир, `mvp-isolation.test.js`, quest-create (4.24), Decisions (4.25).

## Tasks & Acceptance

**Execution:**
- [x] Domain: модели, репозиторий, use cases (подача, цитата), DI.
- [x] Data: outbox-writer + репозиторий, транспорт цитаты + строгий парсер, DI.
- [x] Platform: Firebase-транспорт цитаты + сентинел имён.
- [x] Presentation: вход на экране результата, диалог, строки ×3, DI, компонент-тест.
- [x] Сентинел провода REPORT-payload; гейты.

**Acceptance Criteria:**
- Given игрок завершил урок, когда он открывает «Сообщить об ошибке», вводит текст про весь урок и (по желанию) свой вариант и жмёт «Предложить автору», то в outbox ложится ровно одна запись `quest_authoring.SUBMIT_PROPOSAL` с payload `{proposalId, kind: "REPORT", target: {lessonId}, text, ownVariant?}`, а диалог подтверждает отправку.
- Given диалог открыт, когда данные цитаты приходят, то строка платы показывает значение `quoteLessonProposalFee` (для REPORT — «Бесплатно» при тарифе 0); при недоступности цитаты показывается «—» и отправка остаётся возможной.
- Given текст длиннее границы парсера 4.21 (2000), когда игрок жмёт отправить, то use case отказывает до постановки в очередь, и запись в outbox не появляется.
- Given inbox открывают после перемены, когда читают старые приватные отчёты, то они читаются как прежде (мир 4.9 не тронут — гейт сюиты inbox-views в functions npm test).

## Implementation Notes

- Baseline d074873e (4.21 done). Волна: соседи коммитят в ту же ветку — только pathspec-коммиты, общие файлы (index.js) не трогаю вовсе.
- «Свой вариант» в REPORT — свободный текст по форме 4.21 (не редактор вопроса): структурированную правку вопроса несёт COPY_EDIT (4.23).
- Размещение входа — экран результата урока (ResultContent), рядом с существующей строкой обсуждения: это единственная поверхность «после урока» в приложении.

## Spec Change Log

- 2026-10-28, спека создана, status in-progress; история заявлена после приземления 4.21 соседним прогоном (d074873e).
- 2026-10-28, implementation loopback: (1) `AuthRepository.currentUid()` оказался suspend — чтение uid перенесено внутрь корутины подачи, параметр компонента `suspend () -> String?`. (2) Payload REPORT адресует цель вложенно `target: {lessonId}` — по форме парсера 4.21, не голым `lessonId`; форма заперта проволочным сентинелом. (3) Арена и превью-компоненты (LessonRunnerScreen preview, ArenaRunnerInstance, стабы quizzes-screen/RunFakeComponent) получили честные нули новых членов интерфейса — мёртвых ручек нет.
- 2026-10-28, verification: gradle (JAVA_HOME=/opt/jdks/jdk-27, sgw) — :shared:feature:quest-authoring:domain:jvmTest, :shared:feature:quest-authoring:data:jvmTest (включая новый ProposalReportWireSentinelTest), :android:feature:lesson-runner:presentation:testDebugUnitTest (включая новый ErrorReportProposalPresentationTest 5/5), :platform:firebase:testDebugUnitTest (включая новый LessonProposalQuoteCallableNamesTest 2/2) — зелёные; ktlint по файлам истории чист (красный модуля — предсуществующие строки HEAD: ArenaRunHost, ArenaRunnerView, DI-блок согласия, ExamRunContent — и чужие незакоммиченные правки соседей). functions npm test на живом дереве 137/139: два красных (entity-version, arena-lifecycle-views-handler) — чужие незакоммиченные правки functions/index.js (готовый к коммиту lessonVersion/ready-ack-мир соседа); моих правок в functions/ нет; прицельно сюиты предложений 80/80 зелёные (lesson-proposals, proposal-names-sentinel, review-pipeline, review-queue-worker, quest-review-fee-burn, quest-review-model-outcome, mvp-isolation, review-fee-quote). :android:feature:quizzes-screen:presentation:testDebugUnitTest не запускается — сорсет сломан чужим незакоммиченным NoirArenaEntryContentTest (не мой файл); стаб-правки истории синтаксически зеркалят компилируемый превью-компонент.
- 2026-10-28, boundary: коммит собран временным индексом — build.gradle.kts разделён с чужими незакоммиченными Robolectric-ханками (epic 9.2), вошёл только ханк зависимости.

## Design Notes

- Почему outbox, а не callable: приёмник 4.21 — мутация очереди (AD-3/AD-6), идемпотентность ключом; REPORT не платит и не ждёт сети.
- Почему цитата нужна и для бесплатного REPORT: UX-23 требует показывать плату до отправки у каждого входа; для 4.23/4.24 тот же транспорт переиспользуется, и тариф не должен быть вшит в клиент.
- Почему «Свой вариант» — текст: CAP-6 («опционально с собственным вариантом») и форма 4.21; разбор варианта содержанием занимается конвейер (текст едёт модели данными), и редактор вопроса тут не нужен.

## Verification

**Commands:**
- (заполняется по гейтам)

**Manual checks (if no CLI):**
- None beyond the suites.
