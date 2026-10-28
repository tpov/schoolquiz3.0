---
title: '4.25 proposals-author-side — «Предложения» в «Решениях»: права, push, решение автора, исход предложившему (UX-22, CAP-27, AD-33)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: 'e51cfe5fd29efebb3075e51f1554c7d0a9bbc040'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Записи предложений 4.21 существуют, но никто их не читает: у автора цели нет раздела «Предложения» (вкладка «Решения» 4.17 показывает не-админу пустой placeholder), решения (`decideLessonProposal`) нечем нажать, предложивший не видит исход, push-события не зарегистрированы. UX-22: авторы видят только «Предложения» по своим урокам и курсам; CAP-27: автор одобряет или отклоняет с комментарием, предложивший видит итог, push приходит автору.

**Approach:** Сервер: rights-scoped вью `fetchQuestProposals` (view `quest-proposals-v1`) с двумя «полками» — `incoming` (uid — владелец цели: только CHECK_PASSED/APPROVED/REJECTED; проваленные и задержанные проверки автору не показываются никогда — они до него не доезжают) и `outgoing` (uid — предложивший: все статусы, включая исход проверки); строки строгие (битая запись — строка-метка unreadable, AD-10), курсор по id документа, границы ограничены; STALE вычисляет сервер сравнением baseRevision с текущей версией цели. На той же странице чтения — догон расчёта платы: у терминальных записей манифест `review_proposal_fee_manifests/{proposalId}` создаётся однократно (fee = 0 вознаграждений + burn всего фонда — модель 4.18). Push: два события в реестре — `quest_authoring.QUEST_PROPOSAL_RECEIVED` (автору цели при CHECK_PASSED; шлёт воркер через внедрённый нотификатор) и `quest_authoring.QUEST_PROPOSAL_DECIDED` (предложившему при решении; шлёт `decideLessonProposal`), сентинел клиент/сервер уже стоит (PushEventsRegistryTest — двусторонний). Клиент: раздел «Предложения» на вкладке «Решения» — админ видит переключатель «Вердикты / Предложения», не-админ (серверный отказ роли) — только «Предложения»; списки 64dp со статус-тегами и меткой «Устарело», деталь с телом (текст отчёта и вариант; вопросы правки/нового урока предпросмотром), ✓/✗ с комментарием для владельца на CHECK_PASSED (кнопки неактивны на устаревшем), исход и комментарий автора для предложившего. Сентинелы: имена callable (Platform-тест читает functions/index.js), пин экспортов перепинован, реестр событий push двусторонне покрыт существующим тестом.

**Зависимости:** 4.21 done (записи, решение, STALE, плата), 4.22 done (e51cfe5f — клиентская инфраструктура подачи). 4.23 — соседняя смена в полёте (сервер COPY_EDIT); файлов её не трогаю; вью читает COPY_EDIT-записи по их уже существующей форме (payload.questions/editedQuestionIds из 4.21).

## Boundaries & Constraints

**Always:**
- Права решает сервер: строка отдаётся только владельцу цели или предложившему; чужой id неотличим от пустой страницы. Owner-полка не отдаёт CHECK_FAILED/CHECK_DELAYED/QUEUED никогда.
- Решение — существующий `decideLessonProposal` (4.21): только владелец, только CHECK_PASSED, STALE отказывает до записи; клиент не дублирует право, а прячет кнопки (stale/не-владелец/не-CHECK_PASSED).
- Догон манифеста — create-однократность; не-терминальные записи фонд не делят (OQ-2 parked по аналогии).
- Вью ограничена: страница ≤50 строк, тексты вопросов в предпросмотре ≤500 символов, REPORT-текст ≤2000 (уже граница парсера), ответ ≤100KB.
- Push несёт повод, не данные (AD-20): три поля, сущность `quest_authoring.PROPOSAL:<id>`.

**Never:**
- Никаких изменений приёмки, машины статусов, решения сверх добавления push-вызова; никаких правок 4.23-файлов соседа (lesson-copy-edit*, ветка COPY_EDIT в его коммите).
- Никаких новых коллекций и правил; никаких деплоев и живых вызовов модели.
- Никаких поверхностей подачи (4.22 done) и целевых путей (4.24).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Автор открывает «Предложения» | есть CHECK_PASSED к его уроку | строка входящей полки с телом отчёта и ✓/✗ | нет |
| Автор — входящая полка | есть CHECK_FAILED к его уроку | строки нет (до него не доезжало) | нет |
| Предложивший — исходящая полка | CHECK_FAILED | строка со статусом «Не прошло проверку» и причинами по вопросам | нет |
| Устаревшее входящее | baseRevision ≠ версии урока | метка «Устарело», ✓/✗ скрыты (сервер всё равно откажет PROPOSAL_STALE) | нет |
| Решение с комментарием | владелец, CHECK_PASSED | decideLessonProposal пишет решение, авторский комментарий виден предложившему, push DECIDED | чужое = not-found |
| Догон платы | терминальная строка без манифеста | манифест создан ровно один раз (fee = 0 + burn); повтор чтения — replayed | нет |
| Битая запись | shape не сошёлся | строка-метка unreadable, страница продолжается (AD-10) | нет |
| Не-админ открывает вкладку | отказ роли вердиктов | вкладка показывает только «Предложения» | серверный отказ — не сбой |

## Code Map

- **New `functions/quest-proposals-view.js`** — чистая половина: `PROPOSALS_VIEW_VERSION`, границы, `parseQuestProposalsRequest({box, limit, cursor})`, `rowForProposal(proposal, viewerRole, currentRevision)` (строгий разбор, unreadable-метка, stale, отсечение статусов по полке), `mergeProposalRows` (курсор по docId); db-половина: `readCurrentRevision(db, proposal)` (lessons/{lessonId} или themes/{themeId}), `settleProposalFeeCycle(db, proposalId, proposal, now)` (манифест create-однократно, расчёт 4.18: units=[], burn=фонд, calculationVersion 1).
- **New `functions/quest-proposals-view.test.js`** — полки и отсечения статусов, unreadable, stale, границы страницы, манифест (создание/replay/не-терминальный), права на строках.
- **`functions/index.js`** — `exports.fetchQuestProposals` (auth+профиль, два bounded-запроса, сортировка в памяти, догон манифестов на терминальных строках, версии целей батчем); `decideLessonProposal` += `notifyUser(QUEST_PROPOSAL_DECIDED, proposalId, proposerUid)` после записи решения (REJECT и APPROVED); экспорт-пин перепинован.
- **`functions/push-notifications.js`** — PUSH_EVENTS += QUEST_PROPOSAL_RECEIVED / QUEST_PROPOSAL_DECIDED (`quest_authoring.PROPOSAL:<id>`); серверный тест реестра обновлён, клиентский PushEventsRegistryTest накрывает двусторонне.
- **`functions/review-queue-worker.js`** — опциональная зависимость `notifyProposalReceived(proposalId, proposal)` (по умолчанию null): вызов после терминальной транзакции CHECK_PASSED; сбой пуша — журнал, не терминал. **`scripts/review-queue-worker-run.js`** — проводка на sendPushToUser.
- **Клиент** — `shared/feature/quest-authoring/domain`: модели QuestProposal/QuestProposalBox/QuestProposalPage/QuestProposalDecisionState, `QuestProposalsRepository`, `FetchQuestProposalsUseCase`, `DecideQuestProposalUseCase`, DI; `data`: строгий парсер `quest-proposals-v1` + `QuestProposalsRepositoryImpl` + DI; `platform/firebase`: `FirebaseQuestProposalsRemoteDataSource` + `QuestProposalsCallableNamesTest` (читает functions/index.js); presentation: DecisionsUiState += предложения (полка, строки, деталь, решение), DecisionsComponent += 6 методов, DefaultDecisionsComponent += загрузка/решение, DecisionsView += списки/детали/переключатель (админ) и proposals-only (не-админ), строки ×3 локалей; PushEvents += 2 события + строки уведомлений ×3.
- **Сентинелы**: `proposal-names-sentinel.test.js` += имя callable и view-строка; пин экспортов; PushEventsRegistryTest (двусторонний, существующий).

## Tasks & Acceptance

**Execution:**
- [ ] Server: вью + догон манифестов + сюита; callable + push DECIDED; воркер-нотификатор + раннер; реестр событий; пин экспортов; сентинел.
- [ ] Client: домен/данные/платформа/парсер/транспорт/сентинел; Decisions-компонент/вью/строки ×3; push-события и строки ×3.
- [ ] Гейты: functions npm test (прицельно на живом дереве при чужих правках), gradle затронутых модулей, ktlint файлов истории.

**Acceptance Criteria:**
- Given входящее предложение к уроку автора, когда автор открывает «Предложения», то видит строку (вид, заголовок цели, статус-тег, дата), в детали — тело (текст отчёта и вариант / вопросы правки предпросмотром), и ✓/✗ с комментарием доступно только на CHECK_PASSED и не-устаревшем; решение уезжает decideLessonProposal и предложившему приходит push.
- Given предложивший, когда его предложение провалило проверку, то в исходящей полке виден статус и причины по вопросам; владелец цели эту запись не видит вовсе.
- Given терминальная запись без манифеста, когда страница читается, то манифест платы создаётся ровно один раз с нулевыми вознаграждениями и полным сгоранием; повторные чтения не создают второго.
- Given не-админ, когда вкладка «Решения» открывается, то вердиктов нет (серверный отказ), а раздел «Предложения» показывает его полки; админ получает переключатель «Вердикты / Предложения».

## Implementation Notes

- Baseline e51cfe5f (4.22 done). Соседняя смена 4.23 в полёте в functions/index.js — мои правки index.js хунко-разделимы (новый callable + одна строка push в decide); коммит — временным индексом с моими хунками поверх HEAD.
- Статусы в вью — словарь 4.21 (PROPOSAL_STATUS); CHECK_PASSED в incoming = «ждёт вашего решения».

## Spec Change Log

- 2026-10-28, спека создана, status in-progress; история заявлена после коммитов 4.21 (d074873e) и 4.22 (e51cfe5f); 4.23 — соседняя смена (spec-4-23 in-progress).
- 2026-10-28, implementation loopbacks: (1) init-блок компонента читал proposalsList до её объявления — декларации полок подняты над init (порядок инициализации Kotlin). (2) Строгий парсер в тесте получает «транспортные» значения (примитивы/карты, как Firebase), не JsonElement. (3) `unreadable` в модели — конструкторный флаг, а не вычисление по пустому id (метка AD-10 проходит насквозь). (4) Решение одноразовое и на клиенте: повтор по решённой записи не уходит в приёмник. (5) Мой ханк reorder импортов DefaultDecisionsComponent стёр блок — восстановлен из HEAD + мои добавки; неправильный ErrorReportSubmissionState-импорт из 4.22-мира удалён. (6) Вью: суффикс «Устарело» вынесен в extension-хелпер — правило multiline-expression.
- 2026-10-28, verification: сервер — node --test quest-proposals-view (10/10) + proposal-names-sentinel (+view-контракт) + review-queue-worker (включая новый notify-кейс: CHECK_PASSED уведомляет владельца, CHECK_FAILED и креш нотификатора терминал не ломают) + push-notifications + lesson-proposals + review-pipeline + mvp-isolation = 78/78 на живом дереве; единственный красный — entity-version, чужие незакоммиченные правки index.js (4.23 lessonVersion-apply в полёте), моих правок functions/index.js сверх хунков 4.25 нет. Гейты клиента (sgw, JDK 27): quest-authoring domain+data jvmTest (включая QuestProposalsWireParserTest), platform:firebase testDebugUnitTest (QuestProposalsCallableNamesTest), quest-authoring presentation testDebugUnitTest (DecisionsProposalsComponentTest 5/5 + старая сюита 4.17 зелёная) — зелёные; ktlint файлов истории чист (красный модуля — предсуществующая корзина QuestCreate*, владелец). Экспорт-пин перепинован по HEAD+мои ханки (f843a920: 108 экспортов = HEAD + fetchQuestProposals; чужой buildLessonEditCopy в пин не входит — его перепиновывает коммит 4.23).
- 2026-10-28, boundary: коммит собран временным индексом — functions/index.js разделён с незакоммиченной работой соседа по 4.23 (вошли только ханки 4.25: require, fetchQuestProposals, два notifyUser в decide).

## Design Notes

- Почему две полки, а не одна лента: правила видимости статусов у владельца и предложившего различны, и смешанная лента либо текла бы лишним фильтром в запросе, либо требовала трёх запросов; полки — по одной ограниченной выборке на сторону.
- Почему догон манифеста на чтении, а не в воркере: воркер уже доказал свой единственный долг — вердиктную правду; деньги сходятся там же, где их видят люди (модель догонов 4.7/4.16).
- Почему push RECEIVED шлёт воркер: терминал проверки пишется только им; событие — повод синхронизироваться, данные автор прочитает во вью.

## Verification

**Commands:**
- (заполняется по гейтам)
