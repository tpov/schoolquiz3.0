---
title: 'E8 social retro fixes: per-question poll option ids and poll-series ownership'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
baseline_revision: '75193bfa97038eeb7ec3f5665bc274c91b03774e'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: ['multiple-goals']
deferred:
  - summary: >-
      Транзакционная атомарность applyPublishPollSeries (две конкурентных публикации поверх одной
      ревизии) не демонстрируется выполняемым тестом: in-memory заглушка однопоточна.
    evidence: |-
      Ревью-линзы (blind-hunter/edge-case claims-check/verification-gap): откат обёртки
      db.runTransaction к read-then-set не ломает ни один тест набора — заглушка гоняет тело
      транзакции синхционно без контеншна. Поведение корректно по построению (семантика
      runTransaction — контракт Firestore SDK), поимённая ветка конфликта зажата тестом
      «stale basis refuses by name». Смирило бы: строгий эмулятор Firestore с реальным
      контеншном или прод-наблюдение первого конкурентного издания.
    location: functions/index.js:2407
    severity: medium (unverified)
  - summary: >-
      Ветка повтора намерения на уровне обработчика (current == expected + 1 при том же
      содержании) не покрыта тестом — предсуществующая, перенесена в транзакцию без изменений.
    evidence: |-
      Прочитан poll-vote-handler.test.js целиком: повтор ключа проверяется на уровне гейта
      (replayed=true, обработчик не зовётся), семантическая ветка +1 без нового ключа не прогоняется. Дешёвый тест: публикация expected=0 с тем же содержанием поверх существующей
      r1 → возврат r1 без второй ревизии.
    location: functions/index.js:2438
    severity: low
  - summary: >-
      Публикация на отсутствующую серию с expectedRevisionId > 0 — поименованный конфликт не
      покрыт тестом (предсуществующая ветка).
    evidence: |-
      В poll-vote-handler.test.js отказ POLL_SERIES_REVISION_CONFLICT проверяется только для
      существующей серии (probe-ключ); ветка «current 0 (series does not exist)» не прогоняется.
    location: functions/index.js:2477
    severity: low
  - summary: >-
      sameContent сравнивает вопросы через JSON.stringify — чувствительно к порядку ключей
      (предсуществующее, направление ошибки безопасное: лишняя ревизия, не потерянная правка).
    evidence: |-
      Функция перенесена в транзакцию дословно; расхождение порядка ключей даёт «разное
      содержание» → ревизию+1 вместо no-op — переоткрытие голосования с нуля, заметимо автору.
    location: functions/index.js:2443
    severity: low
  - summary: >-
      Дата публикации внутри транзакции: `const now = Date.now()` стоит до `db.runTransaction` —
      ретрай транзакции (реальный SDK, контеншн) коммитит revision с меткой первой попытки.
    evidence: |-
      Follow-up ревью (2026-10-28, blind-hunter/edge-case): паттерн перенесён в транзакцию
      дословно из до-фиксного кода (now и раньше захватывался один раз вне записи); семантика
      ретрая — контракт SDK. Последствие — слегка устаревшие publishedAtMs/updatedAtMs у
      победившей после ретрая публикации; потери данных нет, порядок и идентичность страдают
      только в микросекундном окне конкурентного издания.
    location: >-
      functions/index.js — applyPublishPollSeries, строка `const now` перед runTransaction
    severity: low
  - summary: >-
      Потолок списка серий (усыновлённая находка 5): на 50 опубликованных сериях `list`
      навсегда SOURCE_TOO_LARGE для всех читателей — глобальный обрыв доступности до чистки;
      масштаб — вопрос владельца.
    evidence: |-
      Follow-up ревью (2026-10-28, edge-case): поведение предписано самим ретро (рецепт
      находки 5 — «громкий SOURCE_TOO_LARGE при усечении, как у сканов голосов») и зажато
      тестом потолка; природа та же, что у голосового капа ~499 (находка 6 / открытый вопрос
      «Масштаб опросов»), только достигается авторскими действиями (50 серий).
    location: >-
      functions/poll-views.js:164
    severity: low

---

<intent-contract>

## Intent

**Problem:** Ретро эпика 8 (epic-social-features, 2026-10-25) дало вердикт rejected по двум блокирующим находкам. (1) `countsFor` считает распределение и знаменатель без оси вопроса (`functions/poll-views.js:122-138`, фильтр только по `revisionId`), а единственный продакшн-путь авторинга назначает одинаковые optionId на каждый вопрос (`PollUseCases.kt:159`, `opt-1..opt-8`) — в любой UI-серии с >1 вопросом голос чужого вопроса попадает в `optionCounts` и `respondentCount`; «проценты по различным респондентам вопроса» (критерий 1) нарушены. (2) `applyPublishPollSeries` не проверяет владение серией (`functions/index.js:2375-2454`) — любой аутентифицированный аккаунт перезаписывает чужую серию и переписывает `authorUid` на себя; против UX-O7 и «ownership follows facts».

**Approach:** Двумя точечными правками по рецептам ретро (action items 1–2). Клиент: `PublishPollSeriesUseCase` генерирует optionId, уникальные на вопрос серии. Сервер: (а) `countsFor` добавляет равенство `questionIndex` к фильтру `revisionId` — ось тройки идентичности восстанавливается для всех уже хранимых голосов независимо от коллизий id; (б) публикация существующей серии — только автору (`stored.authorUid === uid`), а чтение-сравнение-запись обёрнуто в `db.runTransaction` по образцу `applyPollVote`.

## Boundaries & Constraints

**Always:**
- Правка серверного счёта не меняет форму документов и ответов вью: `optionCounts`/`respondentCount` остаются теми же полями, меняется только отбор строк.
- Отказ чужой публикации — поимённый `permission-denied` в формах проекта («… belongs to another account», `index.js:2707`); семейство веток no-op/конфликта/создания сохраняет прежние коды (`failed-precondition` + `POLL_SERIES_REVISION_CONFLICT`).
- Новые optionId клиента укладываются в серверный чарсет `[A-Za-z0-9_-]{1,64}` (`isPollId`/`ID_PATTERN`).
- Существующие тесты с непересекающимися id остаются зелёными без правки утверждений.

**Never:**
- Не трогать находки ретро вне 1–2: очереди старого ключа (4), потолок списка серий (5), кап скана голосов (6), outbox-наблюдение UI (7), resume-индекс (8), гигиена §2 (9) — у них свои action items.
- Не трогать action items 7–8 (индексы против прода, device-пруфы) — hitl/владелец.
- Не менять статусы историй в `_bmad-output/mvp-candidate/epic-social-features/tickets.toml` (фикс-пак, не пере-приёмка); не деплоить.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Мульти-серия с одинаковыми id (продакшн-форма) | Серия из 2 вопросов, у обоих `opt-1/opt-2`; голоса на q0 и q1 | Распределение q0 видит только голоса q0; знаменатель q0 — только респонденты q0 | No error expected |
| Чужая публикация | Не-автор шлёт `PUBLISH_SERIES` с существующим `seriesId` | `permission-denied`; `authorUid` и `revisionId` документа не меняются, записи нет | Окончательный отказ |
| Чужой no-op | Не-автор присылает содержание, идентичное хранимому | `permission-denied` (публикация — право автора, «совпадение содержания» чужой руке ответа не даёт) | Окончательный отказ |
| Конкурентные издания | Два издания читают одинаковый `currentRevisionId` | Транзакция: второе видит обновлённую ревизию и получает `POLL_SERIES_REVISION_CONFLICT`, тихой перезаписи нет | Поимённый конфликт |
| Своя переиздание/no-op | Автор, то же содержание / тот же базис | Прежние ветки: no-op без новой revision; повтор ключа — replay | No error expected |

</intent-contract>

## Code Map

- `functions/poll-views.js:122-138` -- `countsFor`: цикл по голосам серии; фильтр `vote.revisionId !== question.revisionId` на `:128`; сюда добавляется ось `questionIndex` (+ валидация поля в `unavailable` на `:127`). Вызов на `:220` уже передаёт `question.questionIndex`.
- `functions/index.js:2375-2454` -- `applyPublishPollSeries`: чтение `:2397` вне транзакции, ветки no-op `:2409-2417`, бамп `:2418-2429`, создание `:2443-2453`. Сюда — проверка владения и обёртка в `db.runTransaction`.
- `functions/index.js:2463-2547` -- `applyPollVote`: образец транзакции (`:2522`) и форма документа голоса (`questionIndex` на `:2530`) — источник для валидации в `countsFor`.
- `functions/index.js:2553-2555`, `:2707`, `:3425` -- канонические формы `permission-denied` проекта.
- `shared/feature/internet/social/domain/src/commonMain/kotlin/.../use_case/PollUseCases.kt:151-168` -- `toQuestion`: `PollOption(optionId = "opt-${optionIndex + 1}")` на `:159` — единственное место генерации optionId; `index` (вопрос) уже в сигнатуре.
- `functions/poll-views.test.js` -- вью-набор через настоящий `index.js` + заглушку; фикстуры `:37-52` с непересекающимися id (то, чего в проде не бывает) — не менять, добавить продакшн-тест с одинаковыми id.
- `functions/poll-vote-handler.test.js` -- e2e-набор публикации/голоса через приёмник, харнесс `publish(uid, expected, questions, title)` `:94-98` — дом для тестов владения.
- `shared/feature/internet/social/domain/src/commonTest/` -- пустой source set, зависимости уже в `build.gradle.kts` (coroutines-test); конвенция теста — как `SetPublicQuestShelfUseCaseTest.kt` (kotlin.test + fake repository).
- `.local/worktrees/**` -- чужие worktrees соседей по волне; не читать как актуальный код, не править.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/poll-views.js` -- в `countsFor`: валидировать `unsigned(vote.questionIndex)` рядом с `revisionId` на `:127` и добавить `vote.questionIndex !== question.questionIndex` в пропуск `:128` -- ось вопроса (action item 1, дефект критерия 1).
- [ ] `functions/index.js` -- `applyPublishPollSeries`: обернуть чтение-сравнение-запись в `db.runTransaction` (`transaction.get` вместо `seriesRef.get`, `transaction.set` вместо `seriesRef.set`), для существующей серии проверять `stored.authorUid === uid`, иначе `HttpsError("permission-denied", "Poll series belongs to another account")` до всех веток -- находка 2 + транзакционная обёртка action item 2 по образцу `applyPollVote`.
- [ ] `PollUseCases.kt` -- `toQuestion`: optionId = `"q${index}-opt-${optionIndex + 1}"` -- клиентская генерация, уникальная на вопрос (пункт задачи; снимает коллизию в источнике).
- [ ] `functions/poll-views.test.js` -- добавьте тест: двухвопросная серия с продакшн-id `opt-1/opt-2` на обоих вопросах (через настоящий приёмник), голоса на q0 и q1 → распределение q0 не видит голос q1 (RED до правки `countsFor`) -- RED-предложение ретро.
- [ ] `functions/poll-vote-handler.test.js` -- тесты: чужая публикация существующей серии → `permission-denied` без записи; чужой no-op с тем же содержанием → `permission-denied`; ветки автора (создание/no-op/бамп) остаются прежними -- находка 2.
- [ ] `shared/feature/internet/social/domain/src/commonTest/.../PublishPollSeriesOptionIdsTest.kt` -- новый тест: multi-вопросный draft даёт optionId, попарно различные между вопросами одного тела публикации, каждый под серверный чарсет -- клиентская половина находки 1.

**Acceptance Criteria:**
- Given опубликованная двухвопросная серия с одинаковыми optionId на вопросах (продакшн-форма до фикса клиента), when голосуют два разных участника — по одному на q0 и q1, and участник q0 читает состояние q0, then `respondentCount == 1` и `optionCounts` содержат только голос q0.
- Given серия с `authorUid: A`, when B публикует в неё (любое содержание, включая идентичное хранимому), then `permission-denied`, документ серии не меняется ни одним полем.
- Given два конкурентных издания поверх одного `currentRevisionId`, when оба доходят до обработчика, then ровно одна новая revision, вторая получает `POLL_SERIES_REVISION_CONFLICT`.
- Given multi-вопросный draft из UI, when `PublishPollSeriesUseCase` собирает тело, then optionId двух разных вопросов не пересекаются и каждый матчится `[A-Za-z0-9_-]{1,64}`.

## Implementation Notes

- Субагентный шаг воркфлоу в этом харнессе недоступен (нет инструмента запуска вложенных субагентов): внешний динамический воркфлоу запустил этого агента с передачей спеки как единственного источника правды — реализация выполнена им напрямую, без отклонений от спеки.
- RED-доказательство находки 1: копия functions/ в /tmp с poll-views.js от HEAD (75193bfa) — новый тест падает (`not ok 7 - distribution keeps the question axis…`, 7 остальных зелёные); с фиксом — 8/8.
- `functions/index.js` разделён с соседями по волне (в том же файле незакоммиченные 4.21 lesson-proposals и ретро-правки эпика 2 F8/F9/F10): в коммит вошли только два хунка фикса (`@@ -2368,6` KDoc и `@@ -2394,63` тело) через приватный временный индекс (`git read-tree HEAD` → `git apply --cached` → `write-tree`/`commit-tree`/`update-ref` с CAS); общий индекс и чужие staged-удаления не тронуты.
- ktlint поймал `${index}` в шаблоне optionId (redundant curly braces, PollUseCases.kt:161) — исправлено на `$index`, модульный ktlintCheck и allTests перегнаны зелёными.
- node на VPS: CLI npm отсутствует; `npm test` = `node run-tests.js` (bundled node v22.16.0, соответствует engines node 22). `npm run lint` (check-syntax.js) падает только на AppleDouble-мусоре `._*` от SMB-записей с Mac — свои четыре удалены, чужие (`._lesson-proposals.js`, `._review-pipeline.js`, `._currency-ledger.js`, `._mutation-terms.js` и др.) оставлены соседям.
- ktlint toolchain-wide красный по `apps/android-next/build.gradle.kts:65-66` — предсуществующее (строки коммичены эпохой 5.13, `ed16fc81`), не из этого фикса, не чинилось.
- Journey-инструмент `scripts/demo-fixtures/poll-journey.js` продолжает строить ключи и тела по старой формуле — action item 6 ретро (test-dev), не тронуто.
- Патч ревью (87e8fef7): сентинел-тест громкого отказа для документа голоса без questionIndex. Первая редакция теста падала — читатель без собственного голоса не доходит до счёта (§5: distribution скрыт), отказ не возникал; исправлено голосом ALICE — снапшот считается только после ownVote. Сюит в functions стало 139 против 137 на старте волны: две сюиты соседей (lesson-proposals, proposal-names-sentinel) приехали в дерево посреди волны, обе зелёные.

## Auto Run Result

Status: done

**Summary.** Закрыты обе блокирующие находки ретро эпика 8 (2026-10-25, rejected). Находка 1 (критерий 1): `countsFor` считает по полной тройке идентичности (revisionId + questionIndex, `functions/poll-views.js:136-138`), клиент генерирует optionId с осью вопроса `q<index>-opt-<n>` (`PollUseCases.kt:161`) — «проценты по различным респондентам вопроса» выполняются и для уже хранимых голосов, и для новых UI-серий. Находка 2: публикация/перезапись существующей серии — только автору (`stored.authorUid === uid`, permission-denied до всех веток, включая no-op), чтение-сравнение-запись в `db.runTransaction` по образцу `applyPollVote` (`functions/index.js:2403-2487`); заодно закрыта транзакционная механика находки 3 (рецепт action item 2 предписывал их вместе).

**Files changed.**
- `functions/poll-views.js` — ось вопроса в countsFor + KDoc.
- `functions/index.js` — только два хунка фикса (KDoc + тело applyPublishPollSeries: владение + транзакция); файл разделён с незакоммиченной работой соседей, вошли два хунка через временный индекс.
- `shared/.../domain/use_case/PollUseCases.kt` — генерация optionId по оси вопроса.
- `functions/poll-views.test.js` — продакшн-коллизия id (RED→GREEN доказан) + сентинел громкого отказа (патч ревью).
- `functions/poll-vote-handler.test.js` — чужая публикация/чужой no-op/создание чужим аккаунтом.
- `shared/.../domain/src/commonTest/.../PublishPollSeriesOptionIdsTest.kt` — новый, уникальность optionId между вопросами + чарсет.
- Спека — этот файл.

**Review findings breakdown.** 14 находок 4 линз: medium 3 / low 10 / false 1 / maybe-false 0. Патч: 1 группа (сентинел громкого отказа, low, коммит 87e8fef7). Deferred: 4 (атомарность вне досягаемости заглушки — medium unverified; три предсуществующие ветки/сравнение без тестов — low). Rejected: 1 low (косметика тест-хелперов) + 1 false (дрейф authorUid недостижим — оба писателя пишут authorUid). followup_review_recommended: false (запатчена одна low-группа, medium отложены с обоснованием).

**Verification.** RED-доказательство: poll-views.js от HEAD 75193bfa в изолированной копии — новый тест красный (not ok 7), с фиксом 8/8. `functions npm test` = `node run-tests.js` (node v22.16.0, CLI npm на VPS нет): 137/137 на фикс-коммите, 139/139 после патча и приезда двух сюит соседей. `sgw` (JAVA_HOME=/opt/jdks/jdk-27): `:shared:feature:internet:social:domain:allTests` + `:shared:core:outbox:allTests` BUILD SUCCESSFUL, новый тест зелёный в jvmTest и testDebugUnitTest (tests=1 failures=0); ktlint: `:shared:feature:internet:social:domain:ktlintCheck` зелёный (после исправления `${index}`→`$index`, пойманного линтом), toolchain-ktlint красный только по предсуществующему `apps/android-next/build.gradle.kts:65-66` (коммичено эпохой 5.13, ed16fc81 — не из этого изменения). Сентинелы: mvp-isolation, deferred-actions, arena-request-document, remove-human-review-scan — зелёные внутри npm test; сентинел реестра outbox — зелёный в gradle. Матрица I/O: строки 1,2,3,5 покрыты выполняемыми тестами; строка 4 (конкурентные издания) — конфликтная ветка зажата probe-тестом, истинный контеншн за пределами заглушки — deferred (medium unverified).

**Residual risks.** (1) Атомарность публикации под реальным контеншном не демонстрировалась — закрыть строгим эмулятором до первого боевого сезона опросов. (2) Существующие в dev-базах серии с коллидирующими optionId считаются корректно сервером (ось вопроса), но их id-семантика осталась старой — прод-базы нет, миграции не требуют. (3) Девиации от воркфлоу: субагенты воркфлоу в этом харнессе недоступны — линзы и реализация исполнены самим агентом по методикам линз; итоговое «дерево чисто» неприменимо буквально к общей волне — чужие M/D/?? остаются по праву соседей, все файлы этого изменения закоммичены.

**Follow-up pass (2026-10-28, adoption of sibling WIP).** Спека перевызвана задачей волны как «in-progress», фактически найдена done с закоммиченным фикс-паком (d2071203+87e8fef7+7d64d4c7, перевёрстка ретро 8a370756: rejected → accepted-with-open-items) — судьба WIP: принят/переделан = «задел уже в истории; незакоммиченный хвост принят с исправлением». Хвост соседа — открытые пункты перевёрстки 5/8/9 + гигиена ключа голоса (см. change log) — усыновлён, исправлен (детерминизм теста потолка), догейтован и закоммичен отдельным коммитом поверх HEAD. Гейты на базе «HEAD + adoption» (git archive в /tmp на VPS + overlay трёх файлов + symlink node_modules): functions `node run-tests.js` (bundled node v22.16.0) — 140/140 сюит, включая сентинелы mvp-isolation, deferred-actions, arena-request-document, remove-human-review-scan; scoped-прогон двух poll-сюит в рабочем дереве — 11/11 (узел требует index.js цели, работа 4.23 в дереве тестам poll-мира не мешает). Gradle (sgw, JAVA_HOME=/opt/jdks/jdk-27): `:shared:feature:internet:social:domain:allTests` + `ktlintCheck` — BUILD SUCCESSFUL (exit 0); для собственного исполненного доказательства — свежий `cleanJvmTest jvmTest` BUILD SUCCESSFUL, XML: PublishPollSeriesOptionIdsTest tests=1 failures=0 errors=0. ktlint модуля — UP-TO-DATE на неизменённых закоммиченных исходниках (исполненный зелёный — в предыдущих прогонах тех же входов). Не гейталось здесь: `:shared:core:outbox` (дрейф соседей 02:06 в рабочем дереве никем не гейтовался — чужая работа; сентинел реестра outbox в закоммиченном состоянии был зелёным в перевёрстке), toolchain-ktlint (предсуществующая корзина), device-пруфы и индексы (hitl, вне фикс-пака). Ревью-проход: 14 первичных находок — carried; 6 новых — low 3 / false 3; патчей нет; defer ×2 дописаны во frontmatter.

## Spec Change Log

### 2026-10-28 — Adoption of sibling WIP (follow-up pass)

- Триггер: задача волны — принять полуготовый задел остановленной соседней сессии. Фактическое состояние дерева: сам фикс-пак уже закоммичен соседом (`d2071203`+`87e8fef7`+`7d64d4c7`, перевёрстка ретро `8a370756`), а незакоммиченным остался хвост открытых пунктов перевёрстки (mtime 01:37–01:43 после перевёрстки 01:07): находка 5 (громкий потолок списка серий + тест), открытые пункты 8–9 (тест ретрая-конфликта на хуке `retryTransactionOnce` заглушки; три теста предсуществующих веток публикации), гигиена ключа голоса (`voteKey` по форме §2 `…-u-<uid>`, как строит Kotlin-писатель — сверено с `PollVoteKeyRepair.kt:39`).
- Принято с исправлением: тест потолка переведён с реальных часов на шаг `Date.now` по n — при равных метках сортировка разрешает ничью убыванием seriesId («series-cap-9» лексикографически новее «series-cap-49»), и утверждение о голове списка было гонкой на реальных часах.
- Известное плохое состояние, которого избегали: чужой MM-дрейф `functions/index.js` (стейджированный откат части 4.25 + правки 4.23 lesson-copy-edit) не тронут и не закоммичен — усыновлены только три poll-файла; гейт функций гонялся на базе «HEAD + adoption» (git archive + overlay + symlink node_modules), не на смешанном дереве.
- Границы: первичные фикс-коммиты d2071203/87e8fef7 соблюдают Never-пункт (находки вне 1–2 не тронуты); усыновление — отдельный последующий коммит по открытым пунктам перевёрстки, спека фикс-пака не переписывается задним числом.

## Review Triage Log

### 2026-10-28 — Review pass
- verdicts: 14 findings — high 0, medium 3, low 10, false 1, maybe-false 0
- lenses: blind-hunter (7), edge-case-hunter (5, включая claims-check), verification-gap (2, pre-verified), intent-alignment (0 — дескриптивный отчёт: обе трактовки намерения перечислены, реализована ретро-верная; расхождение с буквальной скобкой задачи (только клиентская генерация для находки 1) закрыто тем, что ретро определяет находку 1 как дефект вью — серверный фильтр и есть её рецепт, действие 1)
- findings:
  - `[medium]` `[defer]` Транзакционная атомарность публикации не демонстрируется выполняемым тестом (blind-hunter BH1; edge-case EC1 claims-check: строка матрицы «конкурентные издания»; verification-gap VG2) — заглушка однопоточна, откат обёртки к read-then-set тесты не ловит; поведение корректно по построению (семантика runTransaction — контракт SDK), ветка конфликта зажата probe-тестом; отложено с указанием, что смирило бы (строгий эмулятор с контеншном).
  - `[low]` `[patch]` Новая громкая ветка countsFor (документ голоса без questionIndex → UNAVAILABLE) не зажата тестом (blind-hunter BH7; edge-case EC2; verification-gap VG1, pre-verified Regression gap) — патч применён: тест «a vote document without the question axis refuses loudly» в poll-views.test.js (коммит 87e8fef7), poll-views 9/9, functions 139/139.
  - `[low]` `[defer]` Ветка current == expected + 1 (повтор намерения после протухшего резерва) без теста (blind-hunter BH2; edge-case EC4) — предсуществующая, перенесена дословно; дешёвый тест назван в deferred.
  - `[low]` `[defer]` Публикация на отсутствующую серию с expected > 0 — конфликт без теста (blind-hunter BH3; edge-case EC3) — предсуществующая ветка.
  - `[low]` `[defer]` sameContent через JSON.stringify чувствителен к порядку ключей (blind-hunter BH4; edge-case EC5) — предсуществующее; направление ошибки безопасное (лишняя ревизия, не потерянная правка).
  - `[low]` `[reject]` Дублирование collide*-хелперов в poll-views.test.js вместо параметризации общих (blind-hunter BH5) — тест-внутренняя косметика, повседневного вреда нет, исправление = рефакторный шум.
  - `[false]` Дрейф-документ серии без authorUid дал бы легитимному автору permission-denied (blind-hunter BH6) — опровержение: оба пути записи poll_series в этом же обработчике пишут authorUid: uid, чужих писателей нет (правила коллекции закрыты); недостижимое состояние.

### 2026-10-28 — Follow-up review pass (spec re-dispatched done; adopted sibling WIP included)

- basis: дифф 75193bfa..87e8fef7 (семь файлов фикс-пака) + оверлей усыновлённой работы (functions/poll-views.js, functions/poll-views.test.js, functions/poll-vote-handler.test.js против HEAD; файл: /tmp/sq-e8-review.diff на VPS). Все 14 находок первичного прохода сверены с логом выше: location+claim совпадают, код читается как описано — вердикты и маршруты несутся (carried) без повторной проверки.
- verdicts: 6 findings — high 0, medium 0, low 3, false 3, maybe-false 0
- findings:
  - `[low]` `[defer]` `const now = Date.now()` захвачен до `db.runTransaction` — ретрай транзакции коммитит с меткой первой попытки (blind-hunter; edge-case) — предсуществующий паттерн, в транзакцию перенесён дословно; записано в deferred.
  - `[low]` `[defer]` Потолок списка серий — глобальный обрыв доступности на 50 сериях для всех читателей (edge-case, adoption) — поведение предписано рецептом находки 5 ретро и зажато тестом; масштаб — открытый вопрос владельца («Масштаб опросов»); записано в deferred.
  - `[low]` `[reject]` Гигиена ключа голоса неполна: `poll-views.test.js` держит старую форму ключа без `-u-<uid>`, усыновлена правка только в poll-vote-handler.test.js (blind-hunter) — обе формы биндятся per-uid корректно, сюиты зелёные; правка = косметический рефактор проходящих тестов.
  - `[false]` `[reject]` countsFor валидирует questionIndex до пропуска по revisionId — кривая «чужеревизионная» строка уронила бы весь снапшот (edge-case) — ситуация недостижима: поле пишут все версии приёмника эпика, а громкий отказ для документа мимо писателя — задокументированный контракт (сентинел оси вопроса).
  - `[false]` `[reject]` Дифференциал permission-denied/успех «раскрывает» существование чужой серии (blind-hunter) — опубликованные серии публичны в listPollSeries для всех, раскрывать нечего.
  - `[false]` `[reject]` Тест потолка публикует 50 серий через настоящий приёмник — избыточный вес (edge-case) — ~50 мс в сюите 1.4 с, наблюдаемого вреда нет.
- patches: нет (детерминизм теста потолка — шаг часов по n — внесён при усыновлении до ревью; отражён в change log).
- followup_review_recommended: false (followup-проход: high не патчился, патчей нет).

## Design Notes

- Формат `q${index}-opt-${n}` выбран, а не глобальный счётчик: сохраняет позицию варианта в id (читаемость journey-инструментов и логов), остаётся детерминированным (перепубликация того же содержания даёт те же id — важный no-op ветке `sameContent`), и укладывается в лимит: худший случай `q49-opt-8` (9 символов).
- Проверка владения ставится до веток no-op: «совпадение содержания» возвращает хранимое только автору; чужому — отказ. Иначе чужая рука получала бы успешный ответ на операцию публикации чужой серии.
- `countsFor` продолжает доверять форме документа голоса («документы писал приёмник»): `questionIndex` пишется сервером (`index.js:2530`), отсутствие поля — `UNAVAILABLE`, а не молчаливый пропуск.

## Verification

**Commands:**
- `cd functions && npm test` (VPS) -- expected: весь набор зелёный, включая новые тесты; известное зелёное состояние 137+ прогонов не ухудшено.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:internet:social:domain:allTests'` -- expected: BUILD SUCCESSFUL, новый commonTest зелёный.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew ktlintCheck` -- expected: только предсуществующая корзина (пять файлов quest-authoring:data + wrapper 9.8.0); файлы фикса в корзину не попадают.
- Сентинелы внутри npm test (`mvp-isolation`, outbox-registry, deferred-actions, arena-request-document, remove-human-review-scan) -- expected: зелёные, пины не тронуты.
