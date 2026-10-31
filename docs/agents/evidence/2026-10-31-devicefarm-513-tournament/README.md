# Device Farm · джорни 5.13 (организатор турнира) — v3: организаторская половина зелёная повторно, второй синк работает, но «Active events» не появился: второй синк устройства сам затёр роли нулями (31.10)

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
