# Локальная кампания · джорни 5.13 (организатор турнира → квалификация → раунд) — PARTIAL: организаторская половина + браузерная половина + допуск зеленые живьём (47 шагов, «Download attested by the server» впервые в кампаниях), оппонент с паузами 60 с/раунд отработал идеально (OPPONENT_QUALIFIED); старт раунда устройством отказан — КОРЕНЬ всех волн найден точно: download-record пишется с ЛОКАЛЬНЫМ courseId клиента, а гейт допуска читает его по source.id события (03.11–04.11)

## Прогон

- Устройство A14, mvp-APK 02.11, свежий аккаунт `MSgstRlp007DV7J72IlCx7TrVxD0`.
  Вотчер `run/device-farm/watch-513-v7/watch-fast-v7.js` (VPS): setup-513 за 4 с
  после users-документа (кошелёк 3000/100, роли 100×4, курс-источник df513-course,
  оппонент-аноним) → CREATE события устройства → оппонент с задержкой 90 с и
  паузами 60 с/раунд (OPP_ROUND_PAUSE_MS=60000 — параметризация волны v7).
- Флоу `.maestro/mvp/513-tournament-organizer.yaml`: 47 шагов COMPLETED — синк →
  Events → Organize → df513-course → «Bank ready: 60 easy, 20 hard questions» →
  согласие → CREATE → «Tournament created» → «Reserved: 10 boxes» → пост-эскроу
  синк → «Active events» (роли пережили синки — санитайз 51eeafdb жив) → REFRESH →
  «Qualification open» → Info → Enter → «I downloaded the course» →
  **«Download attested by the server»** → «Continue to qualification» →
  «Start round» visible (скрин `screens/513-qualification-rounds-failed-precondition.png`).

## Корень отказа (точнее некуда — по документам рига)

- Оппонент: 3 раунда settled (`q_77b33b53…` и др.), **OPPONENT_QUALIFIED, exit 0** —
  темп 60 с/раунд держал окно квалификации открытым несколько минут (v6-блокер
  «оппонент быстрый» СНЯТ параметризацией).
- Событие: `event_9b3389c13f97442f873a53d209ec1b81`, status=QUALIFICATION,
  `qualification.startsAtMs` = CREATE+47 с (окно ОТКРЫТО в момент тапа),
  endsAtMs = +7 дней, escrow 10.
- «The server refused this operation: FAILED_PRECONDITION» на старте раунда:
  `admissions/<digest(uid)>` события **НЕ СУЩЕСТВУЕТ**, entries — только оппонента.
  Гейт `START_QUALIFICATION` (tournament-service.js:276) строит admission из
  download-record по ключу `digest([uid, event.source.id])` = `[uid, "df513-course"]`.
- **Фактический download-record устройства** лежит под
  courseId **`ct_796451cb…_course`** — ЛОКАЛЬНЫМ id курса клиента
  (TournamentDownloadRequest.courseId клиент берёт из локального каталога), а
  событие ссылается на серверный `df513-course`. Гейт не находит запись →
  NO_ADMISSION_BASIS → FAILED_PRECONDITION (клиент показывает общий код).
- **Это объясняет отказы «Submit answer» всех DF-волн v4b–v6** (приписывавшиеся
  скорости оппонента/окну): mismatch courseId у любого устройства, где локальный
  id курса не совпадает с серверным id фикстуры.

## Точная правка владельцу

- **[client]** `TournamentDownloadRequest` должен нести eventId/source.id события
  (контекст есть на экране допуска), либо сервер `recordCourseTournamentDownload`
  обязан верифицировать courseId против события-источника и писать запись под
  source.id. Одна строка в use-case + переустановка APK — джорни закрывается
  (всё остальное — вотчер, оппонент, флоу, серверные гейты — уже зелёные).
- Опционально [server]: отражать reason `NO_ADMISSION_BASIS` в клиенте вместо
  общего FAILED_PRECONDITION (ветки именованных причин уже заложены — комментарий
  L2 в tournament-service.js).

## Артефакты

- Флоу: `.maestro/mvp/513-tournament-organizer.yaml` (без правок в этой кампании —
  селекторы точны, фолл не флоу-виновен).
- Вотчер: `run/device-farm/watch-513-v7/` (VPS) — лог с таймлайном CREATE/оппонента.
- Скрин: `screens/513-qualification-rounds-failed-precondition.png`.

## Вердикт

**PARTIAL**: организаторский маршрут + допуск доказаны глубже всех волн; остался
один отказ со строгим корнем (mismatch courseId download-record'а) — правка
клиентская, решение за владельцем (см. выше).
