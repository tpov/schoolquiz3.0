# 3.6 arena-queued-rating — redroid rig (story 3.6, epic-arena-completion) — 2026-10-10 night

Rating Queued: **рейтинг 2 из ранее подтверждённого результата пережил рестарт**, явная досылка
доставила RATE_LESSON по тому же владельцу/уроку/зачтённой попытке, **квитанция рейтинга отделена
от квитанции сеттлмента**, изменённый рейтинг **не переигрывается** (клиентская половина —
замороженный первый рейтинг и терминальное принятое состояние; серверная — ровно одна запись и
байт-стабильный агрегат). Кода не менялось, гейты градла истории не требуются (как 3.3/3.4/3.5 —
device-половина). Игрок — текущий аккаунт рига `ck3DaiqEd7WGcuVikiw22TyF9fS9` (UserYF9FS9).
Файлы владельца (`.maestro/**`, `local.properties`) не тронуты; срез транспорта не применялся —
весь прогон онлайн, 3 реверса базлайна (9099→19090, 8080→18090, 5001→5001) на месте до остановки.

## Исходное состояние (базлайны)

- Сервер: `mutation_keys` **34**, RATE_LESSON-записей у игрока **1** (рейтинг 3.1 по уроку
  `ac_0ecb34ae…_lesson_1`), агрегат целевого урока `demoarena_easy1_lesson_1` — **ratingSum 2 /
  ratingCount 1 / ratingVersion 1** (сид), голосов (`votes`) **0**; баланс 5 nolics, skill 98
  (`server-baseline.json`).
- Room (`room-baseline.json`): 8 тикетов; для урока `demoarena_easy1_lesson_1` нет ни
  `arena_frozen_rating_intents`, ни маркера `lesson_rating_submitted_local` — рейтингуемо; в outbox
  только чужие WAITING ops 3/4 (uid HDb3u, экспонат изоляции).
- Подтверждённый результат: EASY-тикет **`8cec4267b9d3fbc746ab77accc4bedb061dbc4f6289a9a6f395bd296ff68abc9`**,
  попытка **`c9a8ee137848214ede938973582d5b2cffa79231a2477cac0419bd81164b647a`** — SETTLED 100%,
  код 99, награда 30/3 (сеттлмент ночи 3.5, кнопки Rate на квитанции не использованы — там и
  написано в honest notes 3.5).

## Очередь: рейтинг 2 из подтверждённого результата

Карточка тикета 8cec4267 (Arena reservations → Show local tickets) → «Open saved attempt or
result» → квитанция «Completion confirmed by the server / Verified score: 100% / code 99» с
«Rate this lesson: Rate 1/2/3» (`ui/22-receipt.xml`). **Rate 2** → квитанция показывает
«**Your rating (2) is queued to send.**» с кнопкой «Send queued rating» (`ui/23-queued.xml`);
force-stop немедленно.

- Room (`room-queued.json`): outbox op 14 `lesson_runner-SUBMIT_RATING-9eb0a9ba9ac54478…` **WAITING,
  attempt_count 0**, без ошибок; строка `arena_frozen_rating_intents` (rating_id
  `9eb0a9ba9ac54478344c2dbfb5eaeabbf0d0d6d620d086cc115d885e07186485`, payload с rating 2,
  attemptId c9a8ee13…, lessonId demoarena_easy1_lesson_1); маркер урока создан; **локальной
  квитанции рейтинга нет**.
- Сервер (`server-while-queued.json`): `mutation_keys` **34** (без изменений), агрегат **2/1 v1**,
  голосов 0 — рейтинг **не доставлен**, находится в очереди.

## Рестарт: очередь выжила

`am force-stop` → реланч → Arena reservations → Show local tickets → та же квитанция 8cec4267:
«**Your rating (2) is queued to send.**» + «Send queued rating» (`ui/71-receipt-restarted.xml`) —
очередь и замороженный payload пережили смерть процесса. Байт-стабильность intent'а
до/после доставки проверена сравнением снапшотов (`payload` идентичен, тот же rating_id/mutation_id).

## Явная досылка: RATE_LESSON по тому же владельцу/уроку/зачтённой попытке

Кнопка «**Send queued rating**» на самой квитанции (явный sync этого экрана) → UI сменился на
«**Your rating (2) was accepted.**» (`ui/72-after-send.xml`).

Сервер (`server-after-delivery.json`):

- `mutation_keys` **34→35** — ровно +1; новая запись `e0030bcee313…` =
  `lesson_runner-SUBMIT_RATING-9eb0a9ba…`: wrapper `state completed`, `accepted 1`, внутренняя
  квитанция **operation RATE_LESSON, status APPLIED**, payloadHash `27a0fe50…`;
- **голос** `lesson_ratings/demoarena_easy1_lesson_1/votes/3ee134da…`: uid ck3D…, lessonId
  demoarena_easy1_lesson_1, **attemptId c9a8ee13…** (та же зачтённая попытка), rating **2**,
  completion встроен: **SETTLED, completed, settledAtMs 1791586977886** — в точности сеттлмент 3.5;
  acceptedSequence 2, seasonId 2026-Q4 (голос ушёл и в `arena_season_votes`);
- агрегат: **ratingSum 4 / ratingCount 2 / averageRating 2 / ratingVersion 2** (сид-голос + наш);
- карточка каталога «Demo easy fixture 1» в UI показывает «Arena rating: 2.00 / **Player ratings:
  2**» (`ui/105-c.xml`; до рейтинга карточки этого урока показывали 1).

Room (`room-delivered.json`): outbox-строка рейтинга **слита** (остались только чужие ops 3/4);
в `mutation_receipts` появилась ровно одна локальная квитанция рейтинга
(`lesson_runner:rating:9eb0a9ba…`, envelope `replayed: false`, внутри та же квитанция с тем же
payloadHash `27a0fe50…`); intent и маркер на месте.

## Квитанция рейтинга ≠ квитанция сеттлмента

Пара записей одного тикета/попытки (`server-settlement-record.json` + `server-after-delivery.json`):
сеттлмент `df0cfcd16755…` = `lesson_runner-SUBMIT_ATTEMPT-c9a8ee13…` (SETTLED, percentScore 100,
награды 30/3) и рейтинг `e0030bcee313…` = `lesson_runner-SUBMIT_RATING-9eb0a9ba…` (APPLIED,
RATE_LESSON, ratingId arena-rating:be4f7c1c…) — **разные docId, разные операции, разные статусы**;
локально — две отдельные строки `mutation_receipts` с разными entity_ref
(`lesson_runner:attempt:c9a8ee13…` vs `lesson_runner:rating:9eb0a9ba…`).

## No-replay: изменённый рейтинг не переигрывается

1. **Клиентская половина.** После доставки приложение force-stop → реланч → та же квитанция:
   «Your rating (2) was accepted.» — **кнопок Rate 1/2/3 больше нет и кнопки досылки нет**
   (`ui/80-receipt-accepted.xml`): UI не даёт пути изменить/переиграть рейтинг. Это терминальное
   состояние замороженного первого рейтинга (`ArenaFrozenRatingRepositoryImpl`: первый ввод
   сохраняется с маркером урока, повторные submit возвращают существующее состояние без постановки
   в очередь). Бонус-экспонат: квитанция урока, уже оценённого в 3.1 (тикет aab9…), показывает
   «A rating exists, but server acceptance cannot be verified.» без кнопок Rate
   (`ui/65-receipt-restart.xml`) — второй рейтинг на урок невозможен из UI в принципе.
2. **Серверная половина.** Финальное чтение после всех блужданий (второй рестарт, каталог)
   (`server-final.json`): `mutation_keys` по-прежнему **35**, RATE_LESSON-записей у игрока
   по-прежнему **2** (3.1 + эта), агрегат и голос **байт-в-байт равны** моменту доставки —
   второй мутации нет, повторной доставки нет, эффект ровно один.
3. Замороженный payload (rating 2, attemptId, lessonId) идентичен в снапшотах до и после
   доставки — «изменённого рейтинга» в конвейере не существует.

## Честные границы

1. **Изменение рейтинга не вводилось вторым вводом** — UI в состояниях Queued/Accepted кнопок
   Rate не показывает (изменить рейтинг из приложения нельзя вовсе); «no replay of the changed
   rating» доказан как отсутствие самого канала переигрывания (клиент) и ровно-один на сервере.
   Серверно замена голоса возможна только новой мутацией (replacesLatest), которую клиент не
   создаёт; прямой серверный зонд заменой в прогон не входил — он изменил бы агрегат вне истории
   приложения.
2. **screencap чёрный** на всех арена-экранах (FLAG_SECURE) — как в 3.1/3.5; визуальный контроль
   квитанций остаётся device-строкой.
3. **Второй аккаунт/два устройства** не участвуют (это 3.9); изоляция рейтингов по владельцу
   покрыта гейтами владельца в коде и не пере-доказывалась.
4. **Дрейф lifePoints у ck3D** (401→455→480 между чтениями при неизменных mutation_keys) —
   регенерация жизни демо-бэкенда на чтениях кошелька, к рейтингу отношения не имеет; отмечено,
   чтобы финансовый след истории читался корректно (мутаций этого дрейфа нет).
5. Список локальных тикетов и каталог листаются по одной карточке за свайп — навигация медленная,
   но вся семантика uiautomator доступна; дампы ключевых состояний в `work/ui/`.

## Артефакты

`work/`: серверные чтения (базлайн → в очереди → после доставки → финал; пара
сеттлмент/рейтинг), снапшоты Room (базлайн / в очереди / после доставки) с JSON-ридерами;
`work/ui/` — дампы uiautomator: квитанция с Rate-кнопками, queued, queued после рестарта,
accepted, accepted после ещё одного рестарта, карточка урока с Player ratings 2, чужая
квитанция с «A rating exists…». Приватный каталог прогона: /root/arena36-private/run-1 (0700),
инструменты — копии arena35 (srvread36/roomquery36 расширяют srvread/roomquery на
lesson_ratings/votes и rating-таблицы; только чтение).

## Риг после прогона

Транспорт ни разу не резался: 3 реверса базлайна на месте (проверено перед остановкой),
приложение force-stop, контейнер schoolquiz-emu остановлен (docker stop, Exited 137, состояние
персистентно). Демо-бэкенд (schoolquiz-demo-firebase-20261004) и хостовой functions-эмулятор
(5001) не перезапускались и не переконфигурировались.

## Полные идентификаторы (ридерам)

- игрок: `ck3DaiqEd7WGcuVikiw22TyF9fS9` (UserYF9FS9)
- тикет: `8cec4267b9d3fbc746ab77accc4bedb061dbc4f6289a9a6f395bd296ff68abc9`
- попытка: `c9a8ee137848214ede938973582d5b2cffa79231a2477cac0419bd81164b647a`
- урок: `demoarena_easy1_lesson_1`
- мутация рейтинга: `lesson_runner-SUBMIT_RATING-9eb0a9ba9ac54478344c2dbfb5eaeabbf0d0d6d620d086cc115d885e07186485`
- мутация сеттлмента: `lesson_runner-SUBMIT_ATTEMPT-c9a8ee137848214ede938973582d5b2cffa79231a2477cac0419bd81164b647a`
- ratingId серверного голоса: `arena-rating:be4f7c1c3c018dffd5c36ddc6c0d39b97ce51b37620c685010d4dd9295af7dc4`
