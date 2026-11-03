# 3.4 arena-lost-answer-after-commit — proxy-cut submitMutation on the redroid rig (2026-10-10 night)

Story 3.4 (epic-arena-completion): **потеря ответа после серверного коммита** — proxy-cut
`submitMutation` повторяет ТОТ ЖЕ operation ID (AD-6/AD-11), ни нового списания/резерва,
retained SETTLE переживает sent/терминальные/карантинные ветви, ровно одно финальное заселение.
После 3.3 (fault-прокси и fixtures рига) и 3.1 (базлайн полного прохода) — обе закрыты 09–10.10.
Игрок — аккаунт рига `ck3DaiqEd7WGcuVikiw22TyF9fS9` (UserYF9FS9). Кода не менялось; гейтов
история не требует (device-fault прогон существующего транспорта — как device-половина 3.3).

Инструмент сбоя — владелецский `submit-mutation-drop-proxy.js` (`.maestro/mvp/arena-matrix/`,
только чтение): единственный сбой = закрыть соединение клиента ПОСЛЕ полного успешного
upstream-ответа по одному точному запросу; дальнейшие точные повторы закрываются локально;
аудит хранит только SHA-256. Конфиг (0600, приватный каталог 0700) связал loopback-listener
(ephemeral порт 35619 из ready-артефакта), upstream 127.0.0.1:5001 (host functions-эмулятор
против демо-бэкенда 18090/19090, поднят 07:22 этой смены от рабочего дерева night-impl),
путь `/demo-schoolquiz-mvp/us-central1/submitMutation`, операцию `lesson_runner.SUBMIT_ATTEMPT`,
конкретные mutationId/attemptId — извлечённые из стоп-копии Room ДО доставки.

## Ход прогона

1. **Базлайн** (`work/server-baseline.json`, `work/db-baseline/`): у ck3D ровно 3 тикета
   (aab90995…, f143f023…, 8374ce0d… — все SETTLED), `mutation_keys` 30, nolics 2,
   reservedNolics 0, skill 49. Room v15: outbox — только 2 постоянных WAITING uid A (ops 3/4).
2. **Online prepare** (`work/ui/10-11`, `work/server-after-prepare.json`): карточка цена-1
   (автор D0h0rHZG…, Player ratings: 2) → «Confirm preparation» → **Hard** → Confirm. Тикет
   **cf55e0ecec2c44da0991f1ed7c574d40e02722fe582abb7b5d45ae26a0517708** RESERVED (SELECTED/HARD,
   price 1, lifeCost 50, dayId 2026-10-09), terminal null — единственный новый; сервер: nolics
   2→1, reservedNolics 1, reservedLifePoints 50 (заморозка), **`mutation_keys` 30 — prepare без
   мутации**. Локально строка `RESERVED / UNUSED / NONE`, attempt
   `1e6b742f737f5c6c4b957d1782048d1327536354621f7c3d3c872aafeaf43cf6`
   (`work/attempt-ids.txt`, `work/room-prepared.json`).
3. **Срез + оффлайн-попытка** (`work/cut-transcript.txt`): ручной аналог fixed sequence
   (sealed-guard владельца на redroid неприменим — нет telephony-радио; как в 3.3): force-stop →
   сняты ровно 3 реверса → wifi 0 (уже), `svc data disable` (no-op, записано), mobile_data 0 →
   0 реверсов, ADB_CONTROL_ALIVE. Оффлайн-арена честно: «Server balance is unknown…»; старт
   попытки, q1 «Which keyword declares a read-only binding?» → **val** (чекпоинт принял), фидбек-
   гейт «Continue lesson» закрыл попытку поздним завершением. Квитанция «Waiting for server
   confirmation» (`receipt-offline.png`). Сервер под срезом нетронут: RESERVED, terminal null,
   keys 30 (`work/server-while-cut.json`).
4. **Pending-доказательство** (`work/pending-settlement-proof.json`, ридер владельца
   `arena-e2e-pending-settlement-reader-v1` на стоп-копии, `work/db-pending/`): journal SETTLE
   **PENDING** (body sha `ab5a283c…`), outbox **WAITING, attempt_count 0**, first intent на месте,
   **localReceiptCount 0**, scored-строк 0; `mutationIdSha256 39b0c8a4db25…`.
5. **Прокси на маршруте** (`work/proxy-route-transcript.txt`): приложение остановлено; реверсы
   9099→19090, 8080→18090 возвращены, **5001 → прокси (35619)**; радио в базлайн (mobile_data 1).
6. **Инъекция сбоя** (SYNC NOW; `work/proxy-audit.json`, `work/server-after-drop.json`): первый
   запрос ушёл наверх — **upstream 200, эффект закоммичен** — ответ сброшен клиенту,
   статус аудита **UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED**. Сервер: тикет cf55e0ec…
   **SETTLED**, terminal = моя попытка (percentScore **50**, consumedNolics 1,
   consumedLifePoints 50 — замороженные terms), **`mutation_keys` 30→31 ровно +1**,
   reservedNolics 1→0, skill 49→50. Приложение ответ не получило: outbox **WAITING**,
   attempt_count 3, **last_error «NoNetwork»** — сбой классифицирован повторимым (не карантин,
   не терминал), тело команды не менялось (`work/after-drop-local.json`, `work/db-after-drop/`),
   локальных квитанций по мутации 0.
7. **Повтор тем же operation ID** (ядро AD-6/AD-11): outbox-движок повторил доставку сам,
   **5 точных повторов закрыты прокси локально** (EXACT_TARGET_BLOCKED_AFTER_DROP×5).
   **Все 6 requestSha256 (1 отправленный + 5 заблокированных) идентичны** —
   `b7b1965e32c83ee854ead669…`: повтор байт-в-байт тот же запрос, того же mutationId —
   ни новой резервы, ни нового списания (сервер между шагами 6 и 8 не менялся, см. оракул).
8. **Финальная доставка мимо прокси** (`work/final-restore-transcript.txt`): SIGTERM прокси
   (STOPPED, статус аудита сохранён), точный базлайн маршрута (5001→5001). Релонч: ретрай
   приложения доставил retained-мутацию напрямую — сервер ответил **сохранённым результатом**
   (`{replayed}`-семантика приёмника): локальная квитанция
   **«Completion confirmed by the server»** (`receipt-settled-final.png`): 50%, код 19,
   skill +1, life 50.

## Оракул «ровно один финальный эффект»

- Сервер до/после финальной доставки (`work/server-after-drop.json` → `work/server-final.json`,
  `work/mutation-doc-final.txt`): **`mutation_keys` 31 → 31** (второго ключа нет); док моей
  мутации **ровно один**, state completed, **completedAtMs 1791582991395 не изменился**,
  **receipts ровно 1** (SETTLED, percentScore 50, codeAnswer «19», lifePointsSpent 50,
  ticketId cf55e0ec…); terminal тикета тот же; balances: nolics 1, reservedNolics 0, skill 50 —
  без изменений. (lifePoints 450→456 на сервере — естественная регенерация жизни за время
  прогона, к мутации отношения не имеет: settle только тратит жизнь.)
- Локально финально (`work/final-local-state.json`, `work/db-final/`): **outbox-строка мутации
  очищена** (доставлена один раз), **ровно 1 локальная квитанция** этой мутации, чужие ops
  uid A 3/4 не тронуты; карточка тикета «Completion confirmed by the server» при открытии
  сохранённого результата.
- Связка идентичностей: префикс серверного дока `39b0c8a4db25…` = mutationIdSha256 из
  pending-ридера ДО доставки (одна и та же мутация от намерения до записи);
  sha тела команды `ab5a283c…` не менялся на всём пути.

## Жизненный цикл retained SETTLE (вторая часть клейма)

Наблюдаемые состояния строки очереди: **WAITING (ac 0)** при срезе → **отправка** (первый
запрос дошёл до upstream, ответ потерян) → **WAITING, ac 3, last_error NoNetwork** (повторимая
ветвь, не карантин AD-28 и не терминал) → 5 заблокированных повторов под прокси → **доставлена,
строка очищена, ровно одна квитанция** — retained SETTLE пережил sent/ошибку/повторы и дал ровно
одно заселение. Карантинная ветвь в этом прогоне не участвует честно: сбой соединения класс
NoNetwork — карантин там и не должен включаться; квитанция-в-ответе уже была записана сервером.

## Честные границы

1. **Классификация «карантин» не доказана устройством** — и не должна: сбой соединения обязан
   вести в повторимую WAITING-ветвь (что и произошло, last_error NoNetwork). Ветвь карантина
   (AD-28) — предмет других ветвей SyncError; клейм истории «survives …quarantine» в части
   наблюдаемого пути покрыт тем, что retained-строка прошла сбоный цикл без потери ровно-один.
2. **Срез маршрутный, не радиальный** (как в 3.3): eth0 ОС жил; исключён маршрут приложения в
   бэкенд (для демо-топологии 127.0.0.1 это и есть транспортный сбой по README владельца).
3. **`svc data disable` на redroid no-op** (нет telephony-радио) — settings-level mobile_data 0/1,
   зафиксировано в транскриптах; sealed-guard владельца на риге неприменим (строка на владельца,
   унаследована из 3.3).
4. **Сброс ответа/повторы серверу мимо прокси** — повтор #2…#6 закрывался прокси ДО upstream по
   построению инструмента; прямая доставка после восстановления отвечает replay-семантикой
   приёмника. Это ровно сценарий владельца из README прокси («stop the sidecar, restore the
   mapping, manually deliver the retained outbox item, receipt oracle»).
5. **50% вместо 0%** — в отличие от 3.3, ответ q1 был закчекпоинчен до истечения q2 и дошёл до
   тела settle (codeAnswer «19» = q1 верен). Разница — тайминг чекпоинта, не поведение сбоя.
6. Ридер-инцидент водителя: первый SIGTERM прокси ушёл вместе с моим shell (`pkill -f` совпал с
   собственной командной строкой); сам прокси закрылся корректно (STOPPED в аудите), риг и
   данные не затронуты, восстановление маршрута переподтверждено живым чтением
   (`work/final-restore-transcript.txt`).

## Артефакты

`work/`: серверные чтения (baseline / after-prepare / while-cut / after-drop / final +
mutation-doc-final), proxy-audit.json (только хэши) + ready, транскрипты (cut / proxy-route /
final-restore), ридер pending (JSON), after-drop-local.json, final-local-state.json,
attempt-ids.txt, скриншоты (receipt-offline / receipt-after-drop / receipt-settled-final),
ui-дампы, стоп-копии Room (db-baseline / db-prepared / db-pending / db-after-drop / db-final).
Приватные каталоги прогона (конфиг прокси, сырые извлечения) — вне репозитория (0700).

## Риг после прогона

Транспорт в точном базлайне (3 реверса, wifi 0/data 1), приложение force-stop, демо-бэкенд и
functions-эмулятор не перезапускались. Контейнер schoolquiz-emu остановлен после блока проверок
(см. отчёт смены).
