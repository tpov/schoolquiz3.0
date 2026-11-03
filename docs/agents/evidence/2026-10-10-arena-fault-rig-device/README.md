# 3.3 device half — real-offline rig + prepared ticket RESERVED→SETTLE (story 3.3, epic-arena-completion) — 2026-10-10 night

Device-половина смешанной истории 3.3: срез транспорта устройства с сохранённым управлением и
проход приготовленного тикета RESERVED → (оффлайн-попытка, pending SETTLE) → SETTLED ровно одним
эффектом. Серверная половина (манифест фикстур + дроп-прокси после upstream-ACK) закрыта 09.10 —
`../2026-10-09-arena-fault-rig/`; honest отказ `ARENA_TICKET_REQUIRED` доказан там же (probe до
резервации ключа), здесь не повторялся. Игрок — текущий аккаунт рига `ck3DaiqEd7WGcuVikiw22TyF9fS9`
(UserYF9FS9). Кода не менялось; гейты градла истории отнесены к серверной половине (09.10).

## Базлайн (до prepare)

- Сервер: у ck3D ровно 2 тикета (aab909956f…, f143f023b9… — арены 3.1, оба SETTLED);
  `mutation_keys` 29; users: nolics 3, skillPoints 49, lifePoints 413, reservedNolics 0
  (`work/server-baseline.txt`, `work/server-tickets-baseline.txt`).
- Room v15 (identity `e3a1b9e1…`): 4 строки `arena_ticket_records` (2 uid A + 2 ck3D);
  outbox — только 2 WAITING-строки uid A (HDb3uVEO…, ops 3/4 — постоянный экспонат изоляции;
  под сессией ck3D честно скипаются). Копии: `work/baseline-db/`.

## Online prepare (маршрут жив)

Карточка фикстуры цена-1 (автор D0h0rHZG…, «Player ratings: 2») → «Confirm preparation» →
**Hard** → Confirm. Свежий тикет **8374ce0dae87dd0036710778a7c2cfe8605730571c73c562450ad85c3e478180**
(attempt `151497c7ba30b07d15852747b64d3357eddd6068013d109d0115599e8c939990`, request
2598baed-f2e0-479e-9ce9-41c8e7be2276): сервер `RESERVED`, terminal null, mode SELECTED,
difficulty HARD, priceNolics 1, lifeCost 50, dayId 2026-10-09 — единственный против базлайна
(`work/server-after-prepare.txt`, `work/fresh-ticket.txt`). Локально: строка
`server_status RESERVED / local_use UNUSED / decision NONE` с замороженными terms
(`work/prepared-db-schoolquiz.db`). Замечание: случайное нажатие открыло диалог «Prepare a
random lesson» — закрыт Back'ом до Confirm; лишнего тикета нет (финальный счёт ровно 3).

## Срез транспорта (real-offline с сохранённым управлением)

**Почему не sealed-guard владельца:** `offline-transport-guard.js` после `svc data disable`
требует бинарные состояния wifi=0 И data=0; на redroid нет telephony-радио — `svc data disable`
не меняет `mobile_data` (проверено живьём: 1 → 1). Guard на этой строке рига завершится FAIL
после снятия реверсов. Выполнен документированный ручной аналог с теми же инвариантами
(`work/cut-transcript.txt`):

1. базлайн: ровно 3 реверса 9099→19090, 8080→18090, 5001→5001; wifi_on=0, mobile_data=1;
2. приложение force-stop (как в fixed sequence guard'а);
3. сняты ровно эти три реверса (без `--remove-all`);
4. радио-тогглы: `svc wifi disable` (уже 0), `svc data disable` (no-op на redroid — записано),
   `settings put global mobile_data 0` (settings-уровень, честная rig-эквивалентка бинарного
   состояния);
5. verify: 0 реверсов, wifi 0 / data 0, **ADB_CONTROL_ALIVE** (управление через docker-контроль
   plane не зависит от радио — `--control-channel adb-independent` по смыслу guard'а).

Честная граница: eth0/ConnectivityManager при этом остаются подключены (ОС-сеть не рвалась);
для ЭТОГО приложения маршрута в бэкенд нет — единственный путь указывает на 127.0.0.1:9099/8080/
5001 (демо-топология из README владельца), и ноль реверсов = ноль маршрутов. Негативная проба
внутри среза: «Synchronize» на экране квитанции при срезе не меняет ни сервер, ни состояние
(attempt_count outbox-строки честно выросло до 1 — след неудачной доставки).

## Offline leaf (попытка под срезом)

Релонч оффлайн: арена честно показывает «Server balance is unknown. You can still view and
recover tickets»; тикет 8374ce0d… виден с замороженной резервацией «1 nolics and 50 charge
points» и «Prepared locally. Starting requires a separate validated run» (`work/ui-offline-*.xml`).
«Start this prepared attempt» → раннер стартовал БЕЗ сети: q1 «Which keyword declares a
read-only binding?» — тапнут **val** (grading key optionId b), раннер ушёл на 2/2. Экран q2
оказался за гейтом фидбека «Continue lesson» с замершим таймером; тап Continue закрыл попытку
поздним завершением (та же дизайнерская ветка, что цикл 2 в 3.1). Квитанция: **«Arena result
receipt / Waiting for server confirmation. Score and rewards are not confirmed yet»**
(`receipt-offline.png`).

Сервер ПОД срезом нетронут (`work/server-while-cut.txt`): тикет всё ещё `RESERVED`, terminal
null; `mutation_keys` 29 (без изменений); записей попытки 0; balances: nolics 2 (списание
резервации произошло на PREPARE), **reservedNolics 1** (заморозка держится серверно), life 450.

Доказательство замороженного pending-SETTLE ридером владельца (`work/pending-settlement-proof.txt`,
`work/pending-db/` — копия остановленного приложения): `arena-e2e-pending-settlement-reader-v1`,
Room v15/identity ok; тикет RESERVED/LAUNCHED/decision SETTLE; **commandState PENDING** с
`commandBodySha256 26854d2d…`; **outbox WAITING**, first intent на месте; **localReceiptCount 0**;
scored-строк 0. Дигесты файлов копии зафиксированы ридером.

## Восстановление и доставка

`work/restore-transcript.txt`: те же 3 реверса возвращены, mobile_data=1, wifi 0 — точный
базлайн, ADB_CONTROL_ALIVE. Релонч: outbox-ретрай приложения доставил pending-мутацию сам
(outboxNextRetryAtMs наступил после восстановления); тикет-карточка при этом честно показывала
«Result is waiting for server settlement», затем «Open saved attempt or result» → квитанция
**«Completion confirmed by the server»** (`receipt-settled.png`).

## Оракул RESERVED→SETTLE

- Сервер (`work/server-final-oracle.txt`, `work/mutation-record-mine.txt`): тикет 8374ce0d…
  **SETTLED**, terminal связан с моей попыткой (attemptId 151497c7…, consumedNolics 1,
  consumedLifePoints 50 — замороженные terms; percentScore 0, reward 0/0, receiptId
  arena-settle:8374ce0d…); **`mutation_keys` 29→30 — ровно +1**; по mutationId попытки — ровно
  одна completed-запись (uid ck3D, receipt: status SETTLED, codeAnswer "11", lifePointsSpent 50,
  ticketId 8374ce0d…); reservedNolics 1→0 (резервация погашена); nolics 2, skill 49 — 0% без
  наград, консервация по terms.
- Связка «тот же боди, одна доставка»: sha тела из pending-ридр ДО доставки (26854d2d…) =
  body_sha256 SETTLE-команды в journal ПОСЛЕ доставки; sha мутации из ридера (e53b0e4c…) =
  docId завершённого ключа на сервере. Локально после доставки: outbox-строка мутации очищена
  (доставлена один раз), чужие ops uid A 3/4 не тронуты (`work/final-local-state.txt`).
- Замеченная строка квитанции «A rating exists, but server acceptance cannot be verified» —
  локальный фрозен-интент рейтинга, рейтинг в эту историю не входит (строка 3.6).

## Честные границы

1. **0% / code 11**: оффлайн-ответ q1 (val) не доехал до тела settle — live-expiry ветка
   (q2 истёк за фидбек-гейтом в живом процессе) отправила попытку без засчитанных ответов, в
   отличие от пути чекпоинт-релоада 3.1/f143 (там q1 считался, 50%). Слепое вождение рига
   (арена-экраны без визуального тайминга); само клейм 3.3 — жизненный цикл тикета — не зависит
   от скора: RESERVED→pending→SETTLED один раз, terms соблюдены. Сохранность ответа через
   истечение — предмет 3.5/3.8.
2. **Guard владельца на риге неприменим** как есть: verify-шаг data=0 недостижим (нет
   telephony-радио). Выполнен ручной аналог с той же fixed-sequence семантикой; для
   физического устройства владельца guard остаётся эталоном.
3. **Sealed launcher'овский preparation-preflight несовместим с долго живущей установкой рига**:
   требует ноль outbox-строк вообще (включая терминальные), а на риге есть постоянные WAITING
   ops 3/4 uid A. Заменено прямым базлайном Room+сервера и ридером pending-SETTLE.
4. **Срез = маршрутный, не радиальный**: ОС-коннективность (eth0) оставалась; исключён именно
   маршрут приложения в бэкенд (ноль реверсов при топологии 127.0.0.1). Для этого демо это и
   есть транспортный сбой по определению владельца (README arena-matrix).
5. `ARENA_TICKET_REQUIRED` на устройстве не повторялся — честный отказ доказан серверной
   половиной 09.10 (probe до резервации, README `../2026-10-09-arena-fault-rig/`).

## Артефакты

`receipt-offline.png` (pending при срезе), `receipt-settled.png` (подтверждено сервером);
`work/`: транскрипты среза/восстановления, серверные чтения (базлайн / после prepare / под
срезом / финал), ui-дампы (дом → арена → диалог prepare → оффлайн-тикет → раннер), копии Room
базлайн/подготовлен/pending/финал, JSON ридера pending-SETTLE.

## Риг после прогона

Транспорт в точном базлайне (3 реверса, wifi 0/data 1), приложение force-stop, контейнер
schoolquiz-emu остановлен (Exited 137, состояние персистентно). Демо-бэкенд и хостовой
functions-эмулятор (5001) не перезапускались.
