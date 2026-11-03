# 3.5 arena-real-offline-easy-hard — redroid rig (story 3.5, epic-arena-completion) — 2026-10-10 night

Real offline from prepared stock: **EASY и HARD попытки исполнены офлайн**, рестарт вернул ту же
попытку/исход, досылка состоялась после возврата сети; **две различимые диагностики** — «нет сети»
и «бэкенд недоступен при живом маршруте чтений». Кода не менялось, гейты градла истории не
требуют (как в 3.3/3.4 — device-половина). Игрок — текущий аккаунт рига
`ck3DaiqEd7WGcuVikiw22TyF9fS9` (UserYF9FS9). Файлы владельца (`.maestro/**`, `local.properties`)
не тронуты.

## Предметная находка: в юниверсе не было EASY-банка

Каждый урок юниверса (15 строк: ac_*-цепочки, aeu_*, lesson-UUID банк) несёт **только HARD-вопросы**;
серверный `loadEntry` отказывает `NO_QUESTIONS_FOR_DIFFICULTY` на EASY-prepare выбранного урока
(functions/arena-entry-runtime.js:128,157). Сюжетная нога «EASY attempt» была неисполнима —
до засева easy-банка.

**Засев (коммит 212f2d89, только demoarena_-пути):** `scripts/demo-fixtures/` расширен секцией
`easyBank` (манифест + seeder + verifier). Цепочка `demoarena_easy1_*` (каталог REGULAR → квест →
секция → тема → урок `demoarena_easy1_lesson_1` «Demo easy fixture 1», автор — pinned author-low
`D0h0rHZG…`), два EASY-вопроса по схеме дымовых EASY-уроков (SingleChoice с полным содержимым;
серверно EASY получает FULL_CONTENT-ключ — ветка loadEntry:157-166), рейтинг 2/1 = 42/21 делит
нижний уровень шкалы с arena-bank-low → цена 1, экстремумы 1/100 не тронуты (verifier:
юниверс 15→16 строк, bank-high по-прежнему единственный price-100). Идемпотентность: повторный
прогон seeder — created 0, verified 39. Попутно **пин рейтинга bank-low в манифесте обновлён**
на 4/2 (ratingVersion 2) — это легальная запись приложения: RATE_LESSON 2★ хитла 3.1 (receipt
`lesson_runner-SUBMIT_RATING-1cdc7ca6…`, evidence `../2026-10-09-hitl-rig-matrix/31-arena-first-pass/`);
дробь 2.0 не изменилась, цена 1 держится.

Приложение на риге увидело новую карточку: «Loaded 16 of 16 lessons», карточка «Demo easy fixture 1»,
цена 1, автор D0h0rHZG (`work/ui/ui-16-cards10.xml`).

## Подготовленный запас (онлайн, до среза)

- **EASY** `8cec4267b9d3…` — карточка Demo easy fixture 1 → Confirm preparation → **Easy** → Confirm:
  сервер `RESERVED`, SELECTED/**EASY**, price 1, lifeCost 50, dayId 2026-10-09
  (`work/server-after-prepare-easy.json`); локально UNUSED/NONE с замороженным снапшотом
  (`work/room-prepared-final.json`).
- **HARD** `c4848342091c…` — ноликов после EASY-резервации 0, второй price-1 тикет нечем оплачивать;
  использован **random-prepare** («Prepare a random lesson · 0 nolics» → **Hard** → Confirm):
  сервер выбрал урок `ac_b563a984…_lesson_1`, тикет `RESERVED`, RANDOM/**HARD**, **price 0**,
  lifeCost 50 (`work/server-prepared-stock.json`). Random-ветка — легальный путь получить HARD-тикет
  при нулевом балансе (консервация random-цены — строка 3.11).
- Попутная гигиена: брошенная smoke-попытка (см. «Честные границы» №4) была доставлена приложением
  ДО среза через SYNC NOW (keys 31→32), чтобы финансовый оракул истории не содержал посторонней
  мутации (`work/server-after-prepare-both.json` → `work/server-prepared-stock.json`).

## Диагностика №1: нет сети (полный срез)

Срез — документированный ручной аналог fixed sequence guard'а владельца (guard на redroid
неприменим: нет telephony-радио, `svc data disable` не меняет mobile_data; то же решение, что в
3.3/3.4): force-stop → сняты ровно 3 реверса (9099→19090, 8080→18090, 5001→5001) → радио-тогглы
(wifi 0, mobile_data 0 settings-уровнем) → verify: 0 реверсов, wifi 0/data 0,
**ADB_CONTROL_ALIVE** (`work/cut-transcript.txt`).

Реланч офлайн: экран арены честно показывает **«Server balance is unknown. You can still view and
recover tickets.»** (diagnostic `arena_entry_wallet_unknown`), снапшот баланса отсутствует
(`work/ui/ui-66-offline-arena.xml`). Сервер под срезом нетронут: keys 32, оба тикета RESERVED,
reservedNolics 1, reservedLifePoints 100 (`work/server-while-cut.json`).

## EASY-попытка офлайн: рестарт возвращает ту же попытку и тот же исход

1. Тикет-карточка офлайн: «Fixed reservation: 1 nolics and 50 charge points», request 84985eb6
   (совпадает с серверным), «Prepared locally. Starting requires a separate validated run.» →
   «Start this prepared attempt» (`work/ui-easy-offline-card.xml`). Раннер стартовал БЕЗ сети.
2. q1 «Which keyword declares a **mutable** binding?» → ответ **var** — **CORRECT** принята офлайн
   (`work/ui/ui-74-runner-q1.xml`, `ui-75-after-answer.xml`).
3. **Force-stop посреди попытки**: копия Room — тикет LAUNCHED, `runner_checkpoint_revision 1`,
   outbox пуст (попытка в полёте); ридер владельца `arena-e2e-attempt-progress-reader-v1`:
   фаза **FEEDBACK**, draft-ответ сохранён, checkpointSha256 5d37cef5…
   (`work/easy-checkpoint-before-restart.json`).
4. Реланч офлайн → карточка: **«Existing run: resume or reconcile; do not restart from zero.»** →
   «Open saved attempt or result» вернул **ту же попытку** (1/2, feedback-гейт с принятым ответом,
   замёрзшие часы) (`work/ui-easy-resume-card.xml`, `work/ui/ui-82/83`).
5. После гейта q2 «…read-only binding?» пошла с живым таймером — отвечена **val**, тоже CORRECT;
   завершение → квитанция **«Arena result receipt / Waiting for server confirmation. Score and
   rewards are not confirmed yet.»** (`work/ui/ui-85/86`).
6. Негативная проба: «Synchronize» на квитанции под срезом не меняет ни сервер, ни состояние —
   только attempt_count outbox-строки вырос (след неудачной доставки; сервер после пробы: keys 32,
   оба RESERVED — `work/server-after-sync-attempt.json`).
7. Pending-SETTLE доказан ридером владельца `arena-e2e-pending-settlement-reader-v1`: command
   **PENDING**, bodySha256 `9bdf678c…`, mutationIdSha256 `df0cfcd1…`, outbox **WAITING**
   (attempt_count 1 — негативная проба), **0 локальных квитанций**, 0 scored-строк
   (`work/easy-pending-proof.clean.json`).

## HARD-попытка офлайн; исход переживает рестарт

Карточка c4848342 офлайн: «Fixed reservation: **0 nolics** and 50 charge points», request c8275e8c
(совпадает) → «Start this prepared attempt» (`work/ui-hard-offline-card.xml`). q1 отвечена (val;
фидбек-гейт), q2 отвечена на живом таймере (val) → та же pending-квитанция
(`work/ui/ui-95/96e/97`). **Force-stop + реланч** → карточка «Open saved attempt or result» →
**та же квитанция «Waiting for server confirmation»** — исход попытки пережил рестарт
(`work/ui-hard-restart-card.xml`, `work/ui/ui-102/103`).

Оба pending задокументированы локально (`work/room-both-pending.json`: outbox ops 12/13 WAITING;
журнал: оба SETTLE PENDING, body_sha 9bdf678c/befca152) и ридером по каждому тикету
(`work/both-pending-reader-refusal.json` для c4848342: PENDING, bodySha256 `befca152…`,
mutationIdSha256 `e0f369d2…`, outbox WAITING, 0 квитанций). Сервер под срезом по-прежнему нетронут
(`work/server-while-cut-both.json`).

## Возврат сети: досылка и оракул ровно-один

Точный базлайн возвращён (те же 3 реверса, mobile_data 1, wifi остался 0 — как до среза;
`work/restore-transcript.txt`). Реланч: **outbox-движок доставил обе pending-мутации сам**
(SYNC NOW дополнительно прогнан для консолидации).

- **EASY 8cec4267: SETTLED, percentScore 100** — оба офлайн-ответа засчитаны сервером,
  т.е. ответ, данный ДО рестарта, доехал в теле мутации ПОСЛЕ него (ядро «restart returns the same
  attempt/outcome»); consumedNolics 1, consumedLifePoints 50 — замороженные terms;
  reward 30 skill / 3 nolics (`work/oracle-easy-mutation.json`, квитанция UI
  «Completion confirmed by the server / Verified score: 100% / code 99» — `work/ui/ui-111`).
- **RANDOM HARD c4848342: SETTLED, percentScore 50** — q2 засчитан; consumedNolics **0**
  (random-цена соблюдена сервером), consumedLifePoints 50; reward 18/2
  (`work/oracle-hard-mutation.json`, UI code 91 — `work/ui/ui-114`).
- **Оракул ровно-один (×2):** `mutation_keys` 32→**34** — ровно +2; по каждой попытке ровно одна
  completed-запись с **receipts ровно 1**; docId завершённого ключа `df0cfcd16755…`/`e0f369d2b6c1…`
  = те же sha-префиксы, что в pending-ридерах ДО доставки (один боди, одна доставка, AD-6/AD-11);
  balances: nolics 0→5 (награды), reservedNolics 1→0, reservedLife 100→0, skill 50→98
  (`work/server-after-restore.json` + oracle-файлы).
- **Локально:** outbox-строки обеих мутаций очищены (остались только чужие WAITING ops 3/4 uid
  HDb3u, attempt_count 10 — экспонат изоляции, не тронут); в `mutation_receipts` ровно по одной
  локальной квитанции на каждую мутацию (replayed:false, accepted:1) (`work/room-final.json`).

## Диагностика №2: бэкенд недоступен при живом маршруте чтений

Отдельный управляемый зонд после оракула: снят **только** реверс 5001 (functions-callables),
8080/9099 (Firestore/Auth) живы (`work/probe-cut-transcript.txt`). Реланч → арена:

- чтения кошелька работают: «**Nolics available: 5** · reserved: 0», «Server snapshot received:
  Oct 9, 2026 11:08:40 PM» — баланс известен;
- callable-поверхность недоступна: загрузка каталога отвечает собственной диагностикой
  «**Could not load the catalog. Check your connection and try again.**»
  (`arena_catalog_unavailable`, notice UNAVAILABLE экрана каталога) — `work/ui-118-probe-catalog.xml`.

Итого две РАЗЛИЧИМЫЕ диагностики: полный срез → «Server balance is unknown…» без снапшота;
живые чтения + мёртвый callable → известный баланс + отказ каталога. (Кодовая семантика:
недошедшее до сервера = NoNetwork/повтор; ответивший отказ — именованные ветви; тексты —
strings.xml quizzes-screen.) Маршрут восстановлен в точный базлайн после зонда.

## Честные границы

1. **HARD 50%, а не 100%**: ответ q1 был дан на последней секунде окна — тап пришёл на границе
   истечения, live-expiry ветка не зачла его (фидбек-гейт после q1 выглядел «замершим» экраном
   с таймером 1). Слепое вождение рига (все арена-экраны FLAG_SECURE — screencap чёрный,
   `shots/runner-screencap-black-FLAG_SECURE.png`); клейм истории (жизненный цикл тикета,
   рестарт-выживание, ровно-один) от скора не зависит. На EASY тот же путь дал 100%.
2. **HARD-нога — RANDOM-тикет (price 0)**, а не SELECTED: после EASY-резервации баланс ноликов
   был 0; случайный выбор сервера пал на `ac_b563a984…_lesson_1`. Difficulty выбора игрока
   (Hard) соблюдён; цена 0 — свойство random-ветки (консервация подтверждена сервером:
   nolicsSpent 0).
3. **Easy-банк засеян в демо-бэкенд** (`demoarena_easy1_*`) — без него EASY-нога неисполнима
   (все 15 строк юниверса HARD-only). Засев идемпотентный, верифицированный, вне pinned-доков;
   экстремумы цен 1/100 удержаны верификатором.
4. **Попутная smoke-попытка**: разведка обычного урока (поиск пути пополнения ноликов) оставила
   автозавершение брошенной EASY-попытки smoke-урока (op 11, 0%) — доставлено приложением до
   среза; HARD-варианта у smoke-уроков нет (только EASY-вопросы — «No questions available»).
   План пополнения ноликов через smoke-HARD не существует; нолики в итоге пришли наградами
   самих арен-сетлов.
5. **Срез маршрутный, не радиальный** (eth0 ОС не рвался; исключён маршрут приложения — топология
   127.0.0.1 из README владельца), guard владельца на rig неприменим (verify data=0 без
   telephony) — тот же документированный ручной аналог, что в 3.3/3.4.
6. **Локальные строки тикетов** после досылки остались `server_status RESERVED / decision SETTLE`
   (обновление строки происходит при следующем reconcile тикета); серверная правда и локальные
   квитанции — в oracle-файлах и `mutation_receipts`. UI-квитанции обеих ног показывают
   «Completion confirmed by the server» с верифицированными скорами.
7. Рейтинг (RATE_LESSON) в историю не входит — строка 3.6; экраны квитанций предлагают Rate 1/2/3,
   не использовано.

## Артефакты

`work/`: транскрипты среза/восстановления/зонда, серверные чтения (базлайн → prepare → под срезом →
после досылки → oracle-записи мутаций), копии Room (prepared / mid-attempt / easy-pending /
both-pending / final) с JSON ридеров владельца, ключи HARD-урока для слепого вождения;
`work/ui/` — дампы uiautomator ключевых состояний. `shots/` — screencap-доказательства
(чёрный раннер FLAG_SECURE, список тикетов после сетлов).

## Риг после прогона

Транспорт в точном базлайне (3 реверса, wifi 0 / mobile_data 1), приложение force-stop, контейнер
schoolquiz-emu остановлен (docker stop, Exited 137, состояние персистентно). Демо-бэкенд
(schoolquiz-demo-firebase-20261004) и хостовой functions-эмулятор (5001) не перезапускались.
Приватный каталог прогона: /root/arena35-private/run-1 (0700).
