# Device Farm · джорни 5.13 (организатор турнира) — v2b: организаторская половина доказана живьём (31.10)

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
