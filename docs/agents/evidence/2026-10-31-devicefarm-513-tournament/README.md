# Device Farm · джорни 5.13 (организатор турнира) — v6: квалификационный экран сам грузит события (фикс 3cd726de доказан: условный Enter → «Start round» COMPLETED); оппонент с правильным argv QUALIFIED ×3 в событии устройства; Start round принят, но раунд убит FAILED_PRECONDITION — окно закрылось за 14 секунд оппонента (01.11)

## Волна v7 (подготовка, прогон не исполнялся) — темп оппонента параметризован (02.11)

- scripts/course-tournament-device-opponent.js: пауза ПЕРЕД раундами 2 и 3
  OPP_ROUND_PAUSE_MS (по умолчанию 60000) и пауза между ответами OPP_ANSWER_DELAY_MS
  (120, как прежде) — v6 отыграл 3 раунда за 14 с и закрыл квалификацию до тапа
  устройства (FAILED_PRECONDITION, кадр f215). 60 с на раунд держит событие в статусе
  QUALIFICATION ~3 минуты после старта оппонента — окно живёт на весь путь устройства.
- run/device-farm/watch-513-v6/watch-fast-v6.js (ops, вне Git): OPP_DELAY_MS читается из
  env (по умолчанию 90000), OPP_ROUND_PAUSE_MS пробрасывается в env оппонента.


## Прогон v6 (пакет sha256 86dbf7a4…, клиент с фиксами; app 9cad8b0e) — обе половины впервые встретились в одном прогоне; не хватило темпа оппонента (01.11)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/ce47c34f-abdd-4c7a-bd8d-ba4e4e316df9`,
  FAILED, 5.12 устройсте-минуты, окно 03:09:15–03:14:22 UTC; спека `upload:…/f54aa9bd…`
  (SUCCEEDED). Пул p10-11. Артефакты: `run/device-farm/513-tournament-v6-p11/` (кадр
  f215, 03:13 — «QUALIFIER TOURNAMENT / Qualification», Round 1/2/3 «Not started»,
  «The server refused this operation: FAILED_PRECONDITION»); приборка —
  `run/device-farm/watch-513-v6/` (лог в контейнере /tmp/watch-513-v6/watch.log).
- **Оппонент — argv исправлен** (uid и токен отдельными аргументами,
  `watch-513-v6/watch-fast-v6.js`): exit 0, download-recorded → round-settled ×3
  (`q_59025d78895b`, `q_c888d3c59d97`, `q_82632dd4277f`) → **OPPONENT_QUALIFIED** в
  событии устройства `event_99a0b50fbf6245adbbc237d9de077974` (QUALIFICATION, эскроу 10,
  organizer = устройство `MlbIa…`, CREATE 03:10:34). v5-дефект ERR_ASSERTION закрыт.
- Организаторская половина зелёная 4-й раз подряд: bank «60 easy, 20 hard» → CREATE →
  «Tournament created» → «Reserved: 10 boxes», роли пережили синки → REFRESH →
  «Qualification open» → Info → Enter → «I downloaded the course» → **«Download attested
  by the server»** → Continue (допуск доказан второй раз).
- **Фикс 3cd726de доказан живьём:** «Pick an event» грузит список сам при входе —
  условный тап Enter COMPLETED, `Assert "^Start round$" is visible... COMPLETED` (v5:
  «Nothing loaded yet», список пуст), `Tap "Start round"` COMPLETED.
- Фолл: `Assert "Submit answer"` FAILED (120 c) — сервер отказал FAILED_PRECONDITION:
  оппонент отыграл все 3 раунда за 14 секунд (старт 03:12:04, exit 03:12:18) и
  квалификация закрылась до тапа устройства (~03:12:2x). Это не бага клиента — темп
  оппонента. Правка watcher-a на следующую волну: паузы между раундами оппонента
  (держать окно открытым 4-5 минут) или старт оппонента только после «Round settled»
  устройства. Раунд 20 вопросов (val ×20 → settle) — единственный непройденный кусок.


## Прогон v5 (пакет sha256 d2164b1a…, коммит ebf8e0c9) — браузерная половина прошла до квалификации, новый фолл «Nothing loaded yet» на «Pick an event» (31.10 ночь)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/30bf922c-acf3-4d90-9c0f-10ae205f32d4`,
  FAILED, 2.21 устройсте-минуты, окно 23:07:07–23:10:22 UTC; спека `upload:…/623bbc20…`
  (SUCCEEDED, релей create-upload VPS → presigned-URL → PUT с Mac). Пул p10-11. Артефакты:
  `run/device-farm/513-tournament-v5-p11/` (кадры f001–f005); приборка —
  `run/device-farm/watch-513-v5/watch.log` (+ `opp-retry.log`).
- **Преп-гонка выиграна тёплым watcher-ом** (весь конвейер одним процессом, как v4b):
  device uid 23:08:06 → setup-513 за 4 с (23:08:10, кошелёк 3000/100, роли 100×4 — санитайз
  51eeafdb жив) → CREATE 23:08:24: `event_e66883c554604ccfb3226333a326075a`, QUALIFICATION,
  эскроу 10, organizer = устройство `tSP5AZXVTNZBldSgW4N2olT6eFX2`.
- **Правка v5 (условный тап REFRESH) отработала — браузерная половина прорвалась:**
  REFRESH COMPLETED → «Qualification open» visible (скрин 513-browser-open) → Info →
  «Enter» → «I downloaded the course» (скрин 513-admission) → тап →
  **«Download attested by the server» visible (скрин 513-download-attested — ДОПУСК
  доказан живьём впервые)** → «Continue to qualification» COMPLETED.
- **Новый фолл (кадр f005, 23:09):** экран квалификации «QUALIFIER TOURNAMENT /
  Qualification / REFRESH / **Pick an event / Nothing loaded yet**» — список событий пуст,
  условный тап «^Enter$» FAILED. В этот момент (CREATE + 36 с) окно квалификации ещё не
  открыто (по v4b окно = CREATE + 54 с) и оппонент ещё НЕ квалифицировался (ниже).
- **Оппонент — дефект watcher-a вскрыт:** in-watcher старт упал `ERR_ASSERTION`:
  контракт argv `course-tournament-device-opponent.js` = `<eventId> <courseId> <uid>
  <token>` (4 аргумента), а watch-fast (со времён v4b) передаёт «uid token» ОДНИМ
  аргументом → token=undefined (в v4b это же маскировалось ручным повтором). Ручной
  повтор с правильным argv (23:15, скрипт не менялся): download-recorded → round-settled ×3
  (`q_1e2117f02aec`, `q_96a3dc951756`, `q_1ea91b4561ec`, state=SETTLED) →
  **OPPONENT_QUALIFIED** против свежего ивента v5 — лог `watch-513-v5/opp-retry.log`.
- Владельцу: [watcher] чинить argv в watch-fast (uid и токен отдельными аргументами);
  [флоу] на экране квалификации «Pick an event» нужен условный REFRESH + ожидание окна
  (~54 с после CREATE, подтверждено v4b/v5) перед ассертами карточки — иначе список пуст
  и «Enter» не существует. Оставшийся хвост: карточка ивента → квалификация 20 вопросов.

## Прогон v4 (пакет sha256 8c1b2426…) — санитайз ролей РАБОТАЕТ: «Active events» появился;
браузер упал на «Nothing loaded yet»; оппонент квалифицировался в СВЕЖЕМ ивенте (31.10 ночь)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/efc86a8e-5aef-4ee1-9416-8515ed09e235`,
  FAILED, 5.02 устройсте-минуты, окно 22:11:36–22:17:23 UTC; спека `upload:…/9f448e72…`
  (SUCCEEDED, мост Mac→AWS). Артефакты: `run/device-farm/513-tournament-v4/` (кадры в
  `frames/`); приборка — `run/device-farm/watch-513/watch.log`.
- **Санитайз v3-бага доказан:** организаторская половина зелёная третий раз подряд
  (Events → Organize → df513-course → банк «60 easy, 20 hard» → CREATE → «Tournament
  created» → «Reserved: 10 boxes», boxCount 100→90), а **роли пережили все клиентские
  синки: users/WrPSOOnI… = tester/moderator/admin/developer 100×4 на конец прогона**
  (v3 к этому моменту уже стоял в нулях). Второй SYNC NOW после эскроу (правка v3) +
  выжившие роли → **пункт шторки «Active events» появился и был тапнут** (34 шага
  COMPLETED) — главный блокер v2b/v3 снят серверным фиксом 51eeafdb.
- **Падение (кадры f214–f271):** экран «ACTIVE EVENTS / Active tournaments / REFRESH /
  NOTHING LOADED YET / Nothing loaded yet» — список событий не заполнился сам за окно
  ассерта (120 с), `Assert that "Qualification open" is visible` FAILED. Событие при этом
  живо на сервере (status=QUALIFICATION, organizer=WrPSOOnI…, escrow 10). Флоу заморожен —
  владельцу: после входа в Active events нужен тап «REFRESH» (кнопка на экране) перед
  ассертами карточки; если и после REFRESH пусто — клиентский кандидат (пулл списка
  событий), репро на риге.
- **Оппонент — свежий ивент (закрыт хвост v3):** watch-старт оппонента через ~35 с после
  CREATE упал `FAILED_PRECONDITION` на операции после recordCourseTournamentDownload
  (скачивание записано за 615 мс, отказ — в следующей submitCourseTournamentOperation,
  85 мс: окно квалификации ещё не открыто). Ручной повтор через ~10 минут (скрипт не
  менялся): download-recorded → **round-settled ×3 → OPPONENT_QUALIFIED** против
  `event_53a75a88…` — сессии `course_tournament_sessions/q_132e7c9d4b1c…`,
  `q_534f1ec7db22…`, `q_d97143c6ff83…`, все state=SETTLED. Базовая линия+фильтр
  организатора в await-tournament v4 отработали верно (матчнуто свежее событие устройства).
- Правки следующей итерации (владельцу): [флоу] тап REFRESH на экране Active events;
  [драйвер] задержка старта оппонента (квалификация открывается позже создания события —
  точное окно выяснить по коду тарифов/статусов).

## Параллельная волна · пул p10-11 (Pixel 11) — v4 упал в преп-гонке, v4b-ретрай дошёл до «Nothing loaded yet» (31.10 ночь)

- **v4 (7a7e6e99):** Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/7a7e6e99-7a1f-4ab3-8e7c-bef40c1f824b`, FAILED, 4.84
  устройсте-минуты, окно 21:57:41–22:03:05 UTC; спека `upload:…/f1cb9074…` (мост Mac→AWS).
  Артефакты: `run/device-farm/513-tournament-v4-p11/`. **Фолл — преп-гонка, не флоу/не сервер:**
  сетап watch-513 (оппонент 21:59:08.650, батч-коммит ≈21:59:1x–2x) ПРОИГРАЛ тапу «Prepare the
  course bank» (коллбл 21:59:27.808, 721 мс — фаст-фейл): девайс читал фикстуру ПРЕДЫДУЩЕЙ
  волны (authorUid DDzQcSGs…, v3) → молчаливый SOURCE_NOT_ELIGIBLE → «Bank ready» не появился.
  Цепь при этом здорова: контрольный вызов prepare (22:22:47, правильный payload
  requestId+source{kind:COURSE}) вернул 200 `{easy:60, hard:20, total:80}`. **ops: параллельные
  препы 5.13 двух смен на одном риге взаимно уничтожают общую фикстуру df513-devicefarm**
  (сетап второй смены 22:12:52 переприсвоил course своему девайсу WrPSOOnI2U…).
- **v4b-ретрай (8cab3729):** Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/8cab3729-4bc3-4177-9010-881c1f8c63c5`, FAILED,
  3.66 устройсте-минуты, окно 22:32:4x–22:36:13 UTC. Быстрый watcher (весь конвейер в одном
  тёплом процессе: базовая линия users → setup-513 за ~2 с, лог
  `run/device-farm/watch-513-v4b/watch.log`) выиграл гонку: prepare ОК (303 мс, 22:32:46),
  CREATE ОК (406 мс, 22:32:52) → `event_980e7300…` QUALIFICATION, escrow 10, окно квалификации
  с 22:33:46; роли 100×4 пережили все синки (санитайз 51eeafdb подтверждён на выделенном пуле),
  **«Active events» появился и тапнут, фолл тот же: экран «ACTIVE EVENTS / NOTHING LOADED YET»**
  (кадры f096/f100) — и клиент НИ РАЗУ не вызвал `fetchCourseTournamentEvents` (0 исполнений в
  окне по логам functions): список наполняется только кнопкой REFRESH, тапа которой во флоу
  нет (усиление вывода секции v4 выше).
- **Оппонент свежего ивента: OPPONENT_QUALIFIED** — download-recorded → round-settled ×3
  (`q_a49aba64c31b…`, `q_e8f533bfaf11…`, `q_92fdb64408d9…`, state=SETTLED) против
  `event_980e7300…` токеном оппонента ЭТОЙ приборки. Нюанс: токен оппонента ПЕРВОЙ приборки
  (21:59) дал UNAUTHENTICATED после пересева фикстуры — против свежего ивента нужен токен
  актуального setup-513. Итого 5.13 за волну: организаторская половина + выжившие роли +
  вход в Active events + серверный цикл оппонента доказаны на четвёртом событии; остался
  один флоу-шаг (REFRESH) до браузерной половины.


## Прогон v3 (пакет sha256 ad9f3939…)

Вердикт: **FAILED/частичный PASS организаторской половины (1 прогон, 2.44 устройстве-минуты; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/a8791802-273a-4c2b-a8f6-aed4c74b5f00`,
окно 19:42:19–19:45:25 UTC)**. Спека `upload:…/e1a530bc-4913-4970-b4f7-afd1f292116e` (SUCCEEDED,
мост Mac→AWS). Артефакты: `run/device-farm/513-tournament-v3/` (maestro-stdout, testspec output,
видео, logcat, кадры). Watch-513: setup + оппонент — `run/device-farm/watch-513/watch.log`.

Пройдено живьём (33 шага COMPLETED): launch → синк → **Events → Organize → df513-course →
Prepare → «Bank ready: 60 easy, 20 hard questions» → Review → Create with this deposit →
«Tournament created» (кадр видео) → «Reserved: 10 boxes» (эскроу)** → **второй SYNC NOW после
эскроу (правка v3 отработала: шаги COMPLETED, скрин 513-post-escrow-sync)** → закрыть шторку →
Open menu. Упало: `scrollUntilVisible "Active events"` — пункта в шторке нет (кадр: Home
quests/Archive/Decisions/…/DESIGN CATALOG — без Active events).

### Сервер: событие создано заново, эскроу списан, но роли организатора занулены вторым синком

- **Новое событие реально создано устройством**: `course_tournament_events/event_b4979649c3f949eca3a2c0e311a3cd83`,
  organizer = DDzQcSGsr9avxyx0Rb3CZ4yxgv2A (это устройство), status=QUALIFICATION,
  escrow {reservedBoxes:10, remainingBoxes:10}, создано 19:44:13.245Z; boxCount организатора
  100→90 (списание эскроу). Организаторская половина доказана живьём ВТОРОЙ раз, уже с
  посевом, исправленным в смену v2 (архив только на корне).
- **Роли зануляются**: setup-513 записал tester/moderator/admin/developer=100 в 19:43:1x;
  к концу прогона в users-документе нули (update 19:44:20.690Z — через ~7 с ПОСЛЕ CREATE
  19:44:13.245Z; сам CREATE-тариф патчит только boxCount/premium-поля, merge — роли не трогает).
  Единственный писец в этом окне — второй SYNC NOW устройства: клиентский снапшот профиля не
  знал о серверных ролях и протолкнул свои нули. Диагноз v2b («Room-кэш, хватит второго
  синка») оказался неполон: второй синк не просто не подтягивает роли — он их АКТИВНО
  затирает. Пункт шторки требует TESTER+MODERATOR+ADMIN+DEVELOPER ≥ 100 → «Active events»
  не появится ни при каком числе синков.
- **Оппонент живьём**: download-recorded → round-settled ×3 (сессии q_7dfba553…, q_a59eee…,
  q_5384a2…) → **OPPONENT_QUALIFIED** — но против СТАРОГО события event_e1d97476 (v2b):
  `await-tournament df513-course` матчит по courseId без базовой линии и мгновенно взял
  вчерашний документ. Механика оппонента доказана, к свежему событию он не подключался.

### Точная правка на следующую итерацию (флоу/драйвер заморожены — в отчёт владельцу)

1. **[сервер/клиент, кандидат в реальные баги]** Клиентский синк может затереть
   admin-управляемые поля квалификации (tester/moderator/admin/developer) нулями — сервер
   принимает эти поля от клиента. Либо сервер обязан игнорировать роль-поля из клиентского
   пуша, либо клиент не должен их отправлять. Без этого приборка ролей через setup-513
   недолговечна.
2. **[watch-513]** `await-tournament` нуждается в базовой линии по времени создания
   (брать события с createdAtMs > старта смены), иначе оппонент играет со вчерашним событием.
3. **[флоу]** после правки №1 порядок «setup после первого синка → CREATE → второй синк»
   начнёт работать как задумано (второй синк подтянет роли в Room → пункт появится); текущий
   флоу больше не менять.
4. Браузерная половина (Active events → Enter → допуск → квалификация 20 вопросов) в v3 снова
   не достигнута; раунд до settle на стороне устройства не игран (оппонент отыграл свой
   серверный цикл по старому событию).

## Прогоны v2/v2b (пакет sha256 50ac4967…) — историческая секция

Вердикт v2b: **partial PASS — организаторская половина джорни доказана до эскроу включительно
на реальном Pixel 10: Events-вкладка → Organize → prepare банка (60 easy / 20 hard) → согласие →
CREATE с депозитом → «Tournament created» → «Reserved: 10 boxes»**; событие реально живёт на
сервере (status=QUALIFICATION, escrow reservedBoxes=10), и ЖИВОЙ оппонент с VPS квалифицировался
в нём (3 раунда settled). Браузерная половина («Active events» → Enter → допуск → квалификация
20 вопросов) не достигнута: пункт шторки не появился из-за клиентского кэша статистики — см.
«Причина v2b». Прогоны v1/v2 — ниже, исторические секции.

## Прогоны v2/v2b (пакет sha256 50ac4967…)

| # | Run ARN | Итог | Минуты | Где остановились |
| --- | --- | --- | --- | --- |
| v2 | `arn:…:run:69133ff9…/15015d1b-2953-476f-b50d-b58e8654ddfc` | FAILED | 4.42 | «Bank ready: 60 easy, 20 hard questions» не появилось (сид-дефект, см. ниже) |
| v2b | `arn:…:run:69133ff9…/45c2b1e6-367e-4807-a366-8c74578bdec0` | FAILED (partial PASS) | 2.94 | scroll до «Active events» — пункта нет в шторке |

Спеки `upload:…/df486545…` (обе SUCCEEDED, мост Mac→AWS). Артефакты: `run/device-farm/513-tournament-v2/`,
`run/device-farm/513-tournament-v2b/` (maestro-stdout, testspec output, видео, logcat).

## v2: почему «Prepare the course bank» молча не работал (исправлено в смене)

- Пруф: functions-лог — `prepareCourseTournamentBank` отработал 534 мс и НЕ написал ни банка,
  ни preparation; в коллекциях нет ни одного df513-документа.
- Причина в коде (`functions/tournament-source-bank.js` collect()): каталог с `archived === true`
  → SOURCE_NOT_ELIGIBLE (молча, без лога — не internal/unavailable); архивные section/theme/lesson
  просто skip. setup-513 же клал `archived: true` на ВСЮ иерархию через общий `common`.
- Fix (коммит 73d3ce41): форма рядов как в проверенном `scripts/course-tournament-device-prep.js`
  (волна 18.10): archived=true только на корне-квесте (visibleOn=["archive"] — маркер публикации),
  каталог/раздел/тема/урок без archived; текст вопроса удлинён (короткая фикстура не проходит
  безопасную редакцию). Префлайт без устройства: прямой вызов prepareCourseTournamentBank вернул
  `{counts:{easy:60,hard:20,total:80}}`.

## v2b: организаторская половина — все шаги COMPLETED живьём

launch → синк → тап по скриму → вкладка **Events** (нав-полоса v4.0.1 — правка v2 работает) →
«Organize a tournament» → ввод `df513-course` → «Prepare the course bank» → **«Bank ready: 60 easy,
20 hard questions»** → «Review prize list and deposit» → «Create with this deposit» → **«Tournament
created»** → **«Reserved: 10 boxes»** (эскроу). Сервер: `course_tournament_events/event_e1d97476…`
status=QUALIFICATION, escrow {reservedBoxes:10, remainingBoxes:10}; устройство-организатор
`E4N0rhGQ…`; роли (tester/moderator/admin/developer=100) в `users/E4N0rhGQ…` — merge из
setup-513 (коммит d0586aa1) подтверждён в базе.

Оппонент с VPS: после фикса env-ассерта `course-tournament-device-opponent.js` (коммит 175c2397;
раньше требовал хост контейнера демо-рига и падал внутри functions-emu) отыграл живьём против
созданного устройством события: download-recorded → round-settled ×3 → **OPPONENT_QUALIFIED**.

## v2b: почему «Active events» не появился (точная правка флоу — владельцу)

Кадр видео 17:56:35 (шторка, группа «Турниры»): QUALIFIER TOURNAMENT / WORLD CHAMPIONSHIP /
MINIGAMES — пункта ACTIVE EVENTS нет. Роли в users-документе есть (см. выше), но клиент читает
квалификацию из Room-кэша: `UserStatsRepositoryImpl.observeStats()` → `userStatsDao`, строка
обновляется ТОЛЬКО `refreshProfile()`, который вызывается из `sync()` (UserStatsRepositoryImpl.kt:80).
Единственный синк флоу случился ДО merge ролей (setup-513 мержит после появления аккаунта —
то есть после первого синка), повторного SYNC NOW во флоу после эскроу нет → isVisible(ActiveEvents)
ложь на устройстве. Правка флоу: между скрином эскроу и «Open menu» вставить
«Open menu → SYNC NOW → ждать Last sync» (закрыть шторку → открыть заново), затем скролл к
«Active events». После этого браузерная половина достижима: события листаются, допуск/квалификация
по 20 вопросов уже в флоу с consent-закрывашкой.

## Прогоны v1 (пакет debd5457…, 31.10 утро) — FAILED, историческая секция

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/d1f6a54f-24cb-4904-82d4-11006bc6907d`
- Спека: `upload:…/f9e878b2-b7fb-46a4-8399-4c5da30a669a` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/513-tournament/` (testspec output, видео, logcat).
- Прошли: launch → HOME QUESTS → меню → SYNC NOW → «Last sync: 31.10.2026 15:00» (скрин
  513-synced). Упало: `Tap on "QUALIFIER TOURNAMENT"... FAILED — Element not found`
  (вход через вкладку EVENTS исправлен в v2 — доказано прогонами v2/v2b выше).
