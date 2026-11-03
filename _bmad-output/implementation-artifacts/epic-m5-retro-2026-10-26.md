---
epic: 5
date: 2026-10-26
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Ретроспектива эпика 5 монетизации — «Сезон закрывается и платит»

Трек: монетизация (`sprint-status-monetisation.yaml`), project schoolquiz3.0. Не путать с
сентябрьским sync-треком (`sprint-status.yaml`, мёртвый), треком mvp-candidate и треком
commerce (ретро `epic-7-retro-2026-10-25.md` — другой эпик). Имя файла выбрано
`epic-m5-retro-2026-10-26.md`, чтобы не пересечься с соседними ретро параллельной волны.

## Epic summary

- **Эпик:** 5 «Сезоны, лидерборд, рефералка» монетизационного трека; контекст —
  `_bmad-output/implementation-artifacts/epic-5-context-monetisation.md` (входит в диапазон,
  закоммичен стори-коммитом 5.1).
- **Диапазон диффа:** `30769da9^..656948c0` (первый стори-коммит 5.1 включён через родителя;
  последний стори-коммит 5.4). Закрывающий трекерный коммит `bcd93e73` (только
  `sprint-status-monetisation.yaml`) в диапазон кода не входит и упомянут отдельно.
- **Коммиты (4, без merge; `git_evidence.py` на VPS, `--stories "5.1,5.2,5.3,5.4"`):**
  - `30769da9` 2026-10-12 10:31 — 5.1 снимок сезона;
  - `036c37c7` 2026-10-12 10:56 — 5.2 лидерборд платит коробками;
  - `58b3baf8` 2026-10-12 11:28 — 5.3 рефералка платит за шестерых и процент;
  - `656948c0` 2026-10-12 12:23 — 5.4 квитанции в магазине.
- **Статус историй:** `detect-epic --file sprint-status-monetisation.yaml --epic 5` →
  `story_count: 4`, все `done` (5-1-снимок-сезона, 5-2-лидерборд-платит-коробками,
  5-3-рефералка-платит-за-шестерых-и-процент, 5-4-игрок-узнаёт-за-что-получил),
  `pending_stories: []`, `retro_key: epic-5-retrospective: optional`. Незаконченных историй нет.
- **Цепочка баз задокументирована в спеках:** 5.1 `baseline a1957ab3` (стор. 4.3) → 5.2
  `baseline 30769da9` → 5.3 `baseline 036c37c7` → 5.4 `baseline 58b3baf8` — каждая история
  начиналась ровно с коммита предыдущей (frontmatter spec-m5-1..4).
- **Объём:** 4 коммита, ~3389 добавленных строк; ядро — `functions/economy-season.js`
  (+713/−8, три стори-коммита), `functions/economy-season.test.js` (+638), клиент 5.4
  (domain/data/platform/presentation, +825).
- **Инвентарь свидетельств.** Есть: трекер, контекст эпика, 4 спека с triage-логами ревью и
  верификацией, полный дифф (стейджен в `/tmp/epic5-monetisation.diff`, 4000 строк, прочитан
  целиком), вывод `git_evidence.py`, git-история на VPS. Нет: сессионных логов сессий билда
  (process-уроки по сессиям недоступны — анализ process-логов пропущен), живого прогона
  сезонного закрытия (внешнее действие владельца).

## Findings

Диспозиции: **fix-now** (action item) / **defer** / **accept** (записано, чтобы больше не
поднимать). Каждая находка с источником; линзы `bmad-review` исполнены по стейдженному диффу
(объявление плана: контент = дифф кода; линзы adversarial, edge-case-hunter,
verification-gap; claims — отдельно, прочитан только на шаге 4).

### Aggregate views

- **A1. Архитектурная дельта — чистая (accept).** Новый плановый дом по образцу денежного:
  чистый модуль с внедрённым `db` (`functions/economy-season.js:15-17` — «Чистый модуль:
  тестируется без firebase-admin»), тонкая обвязка `onSchedule` в `index.js` (diff hunk
  `@@ -3799 +3811`, maxInstances 1, без callable-ручек), коллекции закрыты deny-all правилами
  с содержательными комментариями (`firestore.rules`, diff hunk `@@ -104 +104`:
  `referral_awards`, `economy_seasons`). Клиент 5.4 — послойно по дом-стилю
  domain→data→platform→presentation с DI-проводкой (diff: `EconomyDomainModule`,
  `EconomyDataModule`, `FirebaseModule`, `EconomyPresentationModule`); экраны рендерят
  состояние и не трогают репозитории (`NoirShopStore.kt` — только `ShopViewState` + колбэки).
  Нарушений слоёв и циклов не найдено (derived inline из changed files; специализированный
  dep-graph инструмент не запускался — сужение записано).
- **A2. Карта дублирования — дублирование осознанное (accept).** Три обхода «страница+курсор»
  внутри `economy-season.js` (счётчики `:261-314`, лидерборд `:328-412`, рефералка
  `:529-595`) — один паттерн с именованными границами порций (`SEASON_REFERRAL_BATCH:42`,
  `SEASON_PAYOUT_BATCH:68`, `SEASON_SWEEP_PORTIONS:61`), а не три реализации одного. Копия
  `quarterWindow` арены задокументирована как намеренная (`economy-season.js:92-94`: «копия
  своя: у сезонов экономики свой домен»). 5.2/5.3 переиспользуют `validateSeason` 5.1, а не
  переизобретают валидацию. Случайного дублирования между историями не найдено.
- **A3. Рост файла — watch-item, не дефект (accept).** `economy-season.js` вырос 350 → 705
  строк за три истории (`git_evidence.py`: added 713, deleted 8, commit_count 3); 15 функций
  одного домена (кварталы, закрытие, выплаты, рефералка, sweep). Прецедент дома —
  `functions/index.js` (9193 строки до эпика). Если в дом ляжет четвёртый обход — вернуться к
  вопросу о разбиении.
- **A4. Паттерн-дивергенция — одна (fix-now устранено вне эпика).** Дублирующий импорт
  `kotlinx.coroutines.flow.flatMapLatest`, добавленный 5.4 в
  `shared/feature/economy/data/.../EconomyRepositoryImpl.kt` (дифф hunk `@@ -14,12 +16,14`
  коммита `656948c0`). На HEAD дубля нет (grep: единственный импорт, строка 18; файл последний
  раз трогал `d833cb24` 2026-10-23 — убран позднейшим проходом трека, не эпиком). Примечание:
  заявленный в 5.4 «ktlint моих модулей зелёный» (spec-m5-4:193) дубль не ловит — правило
  duplicate-imports в конфиге отсутствует/выключено.
- **A5. Сверка спека↔код — сходится по всем четырём историям (accept).**
  - Полосы = спеке: `functions/config/economy-constants.json` `seasons.leaderboardBands`
    [1,1,500]…[51,100,5]; сеттл 2315 закреплён тестом (`economy-season.test.js:2998`);
    свойство «1..100 без дыр и перекрытий» проверяется при чтении с откатом к начальной
    таблице (`economy-constants.js`, `seasonPayouts`, diff `@@ -192 +192`; тесты
    `economy-constants.test.js` `testSeasonBandsCoverEveryRankExactlyOnceOrFallBack`,
    diff `@@ +242`); раздел не уезжает клиенту (тест diff `@@ +1864`).
  - Рефералка = спеке: percent 1 / target 6 / boxes 10 / bonus 50 в том же разделе; граница
    чтения 200 с громким отказом (`economy-season.js:443-445`; тест diff `@@ +3270`); один
    floor от суммы (`economy-season.js:473`; тест diff `@@ +3133`); награда «за шестерых»
    один раз за всё время — `referral_awards/{uid}` create (`:499-502`; тест diff `@@ +3201`);
    цепочка платит на один уровень (тест diff `@@ +3240`); регистрация только при создании,
    само/мусор — named 400, мёртвая ссылка тихо (`referral-registration.js`;
    `economy-handler.test.js` diff `@@ +1924`).
  - Квоты транзакций = спеке: порции 100/50, догоняющий проход 4 квартала, маркер установки
    (`economy-season.js:42,48,57,61,68`).
  - Сервер↔клиентский контракт квитанций через границу историй 5.2/5.3 → 5.4: сервер пишет
    `receipt.season_leaderboard` {seasonId,rank,boxes,trophy} (`economy-season.js:387-395`),
    `receipt.season_referral_percent` {seasonId,openings,boxes,percent,invitees} (`:492-497`),
    `receipt.referral_bonus` {boxes,invitees,each} (`:509-514`); клиентский словарь отображает
    все три ключа с корректным порядком параметров (`EconomyReceiptText.kt:24-26,37-39`);
    строгое отображение Long/Int-полей с громким отказом на кривом документе
    (`FirebaseEconomyReceiptsDataSource.kt:74-92`; `EconomyReceiptMappingTest`). Расхождений
    ключей/параметров нет.
  - Пин поверхности переопределён с записью (`arena-lifecycle-views-handler.test.js` diff
    `@@ +1631`, новый SHA + комментарий про 5.1).

### Diff-scope review (bmad-review)

- **R1 (edge-case, accept + задокументировать).** Сдвиг атрибуции при запаздывающем обходе.
  Счётчики приглашённых снимаются значениями на момент обхода
  (`snapshotReferralCounters`, `economy-season.js:261-314`, чтение `openedBoxesTotal` на
  `:298`), а обход идёт первым тиком после закрытия квартала (расписание «every 10 minutes»,
  diff `index.js @@ +3811`). При простое планировщика снимок фиксирует и часть открытий
  следующего сезона — процент смещается между соседними сезонами. Консервация денег не
  нарушается (кумулятивный счётчик + база «свежий из существующих прошлых снимков»,
  `:459-467`; тест diff `@@ +3178` «a lost middle snapshot…»). Граничного теста
  «обход много позже endMs» нет. Диспозиция: accept как свойство дизайна кумулятивных
  разностей; задокументировать в spec-m5-1 (action item AI-5).
- **R2 (adversarial, low, defer).** Расширение семантики ошибки в `ensureUserProfile`:
  catch в diff `index.js @@ -2917 +2919` переводит ЛЮБОЙ `INVALID_ARGUMENT` из
  `upsertUserProfile` в клиентский `invalid-argument`, а не только реферальный. Серверная
  ошибка из другой логики профиля (premium advance и т.п.) замаскируется под клиентскую.
  Guard: сузить маппинг до реферального вызова или перебрасывать чужие коды как есть.
  Диспозиция: defer (низкая вероятность, нет денежного следа).
- **R3 (verification-gap, вне эпика, fix-now для трека competitions).** Гейт functions на
  HEAD (26.10) красный не из-за эпика 5: `mvp-isolation.test.js:38-45` падает на лишнем
  cleartext-хосте `192.168.31.218` в
  `apps/android-next/src/mvp/res/xml/mvp_network_security_config.xml`; хост внёс `7d8b91bd`
  (competitions, story 5.13 device proof, 2026-10-18; `git log -S`). Мой прогон на VPS:
  `node run-tests.js` → 128/129 (первый прогон; красный только mvp-isolation), повторный
  полный прогон → 127/129 (+ известный флак `arena-lifecycle-views` под нагрузкой,
  задокументирован со времён эпика 4, spec-m5-1:212-214). Action item AI-1.
- **R4 (adversarial, снято).** `validateSeasonSnapshot` проверяет ранги и дайджест, но не
  уникальность uid (`economy-season.js:153-167`); самосогласованная подделка с дублями uid
  прошла бы валидацию, однако ключ повтора `payouts/{uid}` — create (`:352-368`) платит за
  игрока один раз, а правила deny-all отсекают клиентскую подделку. Денежного следа нет —
  как дефект снято; осталось открытым вопросом (ниже).
- **Claims-проверка (шаг 4 edge-case):** все четыре утверждения из тем коммитов опровергнуть
  не удалось — «each of the top hundred takes its band once… finished season writes nothing»
  (тесты diff `@@ +2988`, `@@ +3028`), «a link registers only at account creation one level
  deep… the sixth qualifying invitee mints a one-time fifty» (тесты diff `@@ +1924`,
  `@@ +3201`), «every box opening raises the player's lifetime count in the very same
  transaction» (`box-service.js` diff `@@ +165` + тесты `@@ +317`), «since the last snapshot»
  — формулировка точно соответствует коду (база = свежий существующий прошлый снимок).

### Поведенческая проверка (behavior verification)

- Живое поведение НЕ упражнялось: `reconcileEconomySeasons` работает в проде
  school-quiz-89336951 (деплой владельца 12.10 закрыт полностью — трекер, комментарий
  epic-5: 86 функций в проде, джоба `firebase-schedule-reconcileEconomySeasons-us-central1`
  ENABLED), вызывать прод из ретро — outward-facing действие, запрещённое правилами волны.
  Первый живой прогон случится на первом календарном переломе квартала после деплоя.
- Исполнимая проверка, выполненная в ретро: полный functions-гейт на HEAD —
  `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0/functions && /root/.zcode/server/node run-tests.js'`
  → 128/129 (красный `mvp-isolation` — вне эпика, см. R3); повторный прогон → 127/129
  (добавился задокументированный флак `arena-lifecycle-views`). Сьют `economy-season`
  (35 тестов), `referral-registration` (3), `economy-handler` и `box-service` — зелёные.
- Gradle-гейты (test/allTests/detekt/ktlintCheck/ciCheck) в ретро НЕ прогонялись: одна
  тяжёлая Gradle-задача на VPS и известные средовые красные владельца (google-services.json
  только с mvp-клиентом, detekt jvm-target 27, ktlint platform:firebase/app-shell, it09e,
  compileTestKotlin profile:data — spec-m5-4:189-199). В 5.4 они тоже не прогонялись
  целиком — верификация истории была сужена до модулей economy + assembleMvp + ktlint своих
  модулей (там же). Сужение зафиксировано, не скрыто.

## Previous-retro follow-through

Предыдущего ретро монетизационного трека нет: в `sprint-status-monetisation.yaml` все
retro-ключи `epic-0-retrospective`…`epic-5-retrospective` = `optional`, секции `action_items`
в файле нет (только упоминание в шапке, строка 30). Сентябрьские ретро
(`epic-*-retro-2026-09-*.md`) — мёртвый sync-трек, `epic-7-retro-2026-10-25.md` — трек
commerce; оба вне трека. Следовать нечему — не «нет задач», а «нет предыдущего ретро».

## Action items

Предложены, не применены (ремедиация и сверка спеки — решение владельца; трекер этой волной
не редактируется — см. Assumptions):

- **AI-1** (владелец / трек competitions): развязать cleartext-хост `192.168.31.218`
  (`apps/android-next/src/mvp/res/xml/mvp_network_security_config.xml`, внесён `7d8b91bd`,
  story 5.13 device proof) с контрактом `functions/mvp-isolation.test.js:38-45` — вернуть
  конфиг или осознанно переписать пин. Гейт functions на HEAD красный из-за этого.
- **AI-2** (владелец): постановка клиентской истории «проводка глубокой ссылки рефералки»
  (Play Install Referrer → `referredByUid` в `ensureUserProfile`). Серверная дверь готова и
  закрыта тестами (`referral-registration.js`, `economy-handler.test.js`); без неё
  реферальный контур не набирает приглашённых.
- **AI-3** (владелец): наблюдать первый живой прогон `reconcileEconomySeasons` на ближайшем
  календарном переломе квартала (джоба ENABLED с 12.10): ожидание — документ сезона
  CLOSED → SETTLED, выплаты `payouts/*`, строки книги, квитанции. Ретро живой прогон не
  исполняло.
- **AI-4** (владелец): когда сняты средовые красные (google-services.json, detekt jvm-target
  27, ktlint platform:firebase/app-shell, it09e, profile:data) — прогнать полные gradle-гейты
  по поверхности эпика, закрыв сужение верификации 5.4.
- **AI-5** (владелец, при следующей правке спек): зафиксировать в spec-m5-1 свойство
  «снимок счётчиков = момент обхода, не endMs» и следствие — сдвиг атрибуции процента между
  соседними сезонами при простое планировщика (находка R1); либо осознанно принять как есть.
- **AI-6** (агент-сводящий трекера этой волны): в `sprint-status-monetisation.yaml` выставить
  `epic-5-retrospective: done`, добавить перечисленные action items с `ref` на этот документ.
  Само ретро трекер править не имело права (ограничение волны).

## Acceptance verdict

**accepted-with-open-items** — criteria: **declared** (AC четырёх спек + Requirements &
Constraints контекста эпика).

- Все 4 истории `done` (`detect-epic`: `pending_stories: []`) — машина не загнана в rejected
  незаконченностью.
- Критерии воспроизведены по коду и тестам: полосы 1–100 без дыр и сеттл 2315
  (`economy-season.test.js` diff `@@ +2988`); повторные прогоны ничего не пишут — и снимок
  (дайджест, diff `@@ +2784`), и выплаты (diff `@@ +3028`), и награда (diff `@@ +3201`);
  рефералка — процент разности снимков, один floor, один раз награда, цепочка на уровень,
  регистрация только при создании; квитанции — ключ+параметры, три локали с одинаковым
  набором строк (diff `strings.xml` @@ +157 у values/-ru/-uk), две квитанции — две строки,
  потерянное гашение показывается снова (клиент не гасит дисплей).
- Блокирующих находок нет; открытые позиции трекуются (AI-1…AI-5) → не «accepted», а
  «accepted-with-open-items».

## Open questions

- Добавить ли проверку уникальности uid в `validateSeasonSnapshot` (`economy-season.js:153-167`),
  или ключ повтора `payouts/{uid}` считается достаточным (денежного следа у дублей не найдено,
  R4)?
- Сузить ли маппинг `INVALID_ARGUMENT` в `ensureUserProfile` до реферальной валидации (R2)?
- Срок постановки клиентской истории глубокой ссылки (AI-2): ценность лидерборда/рефералки
  зависит от притока по ссылкам, а `referred == true` ставится только при создании аккаунта.

## Assumptions (headless)

- Эпик 5 выбран вызовом оркестратора (не авто-детектом); `detect-epic --file
  sprint-status-monetisation.yaml --epic 5` → story_count 4, все done, pending_stories [],
  retro_key optional. Непустого pending-списка не было.
- Машинный вердикт accepted-with-open-items отрендерен без человека (headless); человек может
  переопределить.
- `bmad-review` инвокнут по требованию воркфлоу; субагентов на платформе нет — линзы
  adversarial / edge-case-hunter / verification-gap исполнены последовательно инлайн по
  стейдженному диффу (сужение записано); claims-файл прочитан только на шаге 4.
- Живое поведение (прод-Firestore, вызов scheduled-функции) не упражнялось — outward-facing
  действия запрещены правилами волны; исполнимая проверка — functions-гейт на HEAD (см.
  Behavior verification).
- Трекер `sprint-status-monetisation.yaml` НЕ обновлялся (запрет параллельной волны): шаг
  Phase 5 `sprint_status.py update --set-retro-done --add-action …` намеренно пропущен; перенос
  ключа и action items — за агентом-сводящим (AI-6).
- Имя документа `epic-m5-retro-2026-10-26.md` дано оркестратором вместо дефолтного воркфлоу
  `epic-5-retro-2026-10-26.md` — во избежание пересечения с соседними ретро волны.
- Сессионные логи билда эпика недоступны — process-уроки по сессиям не анализировались;
  процессные выводы опираются на triage-логи спек и трекер.
