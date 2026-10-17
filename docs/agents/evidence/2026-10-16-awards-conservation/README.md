# 5.8 awards-conservation — 2026-10-16

Story: «Awards, ranking and remainder conservation end-to-end through the bracket: payouts and
returns across a full bracket to FINISHED with terminal `remaining = 0`, technical-outcome
matrices paying per the adopted mapping, two-place-3 rows paid live through a played bracket, and
crash/retry races at the settlement boundary with exactly-once effects». Спека:
`_bmad-output/implementation-artifacts/spec-5-8-awards-conservation.md` (baseline
`0fb65256ac2dedc2b82767def82e3a92c9046a35` — совпал с HEAD на старте смены).

## Статус: выполнена; production-дифф нулевой

Proof-story в дисциплине 5.5: шесть новых кейсов в `functions/tournament-service.test.js`
(51 → 57), один симметричный чистый якорь в `functions/tournament-policy.test.js` (28 → 29),
ни одного изменения производственного кода — механизм (5.4 payouts + 5.5 escrow-reconcile +
D9-ключи) выдержал все случаи матрицы без gap. Гипотеза Design Notes спеки («окно сбоя коммита
внутри settlement — receipt staged vs escrow staged») проверена честным зондом и гэпа не
вскрыла: выплата, возврат, квитанции, ledger и эскроу живут в одной транзакции.

## Входы (все прочитаны 2026-10-16)

| Вход | Где | Что взято |
| --- | --- | --- |
| Спека истории | `_bmad-output/implementation-artifacts/spec-5-8-awards-conservation.md` | матрица I/O (7 строк), Always/Never, карта кода, критерии приёмки; `context:` в шапке пуст — карта кода прочитана целиком |
| Принятый prize-list контракт (5.2) | `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md`, издание 1 (принят владельцем 2026-10-16) | матрица технических исходов D6 (6 строк), назначение строк D5 (j-й финишист ← j-я строка места), D7 (невоспроизводимые места возвращаются), D9 (детерминированные ключи `["tournament", eventId, "prize", rowKey]`), §4-инвариант `reserved = paid + returned + remaining`, терминальный `remaining = 0` |
| Записи tickets 5.2/5.4 | `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` | статус-записи принятых механизмов, на которые опирается 5.8 |
| Существующие пины | `tournament-policy.test.js` (5.4: place-3 порядок, нулевые строки, D7-места, legacy-слоты), `tournament-service.test.js` (5.4/5.5/5.6/5.7: эскроу, replay-дисциплина, 15-квалификантов, сбои коммита CLOSE/FORFEIT) | якоря, которые 5.8 дополняет, не дублируя |

## Матрица спеки → кейсы (сервис-шов, `functions/tournament-service.test.js`)

| Строка матрицы | Кейс | Что закреплено (эскроу = {reserved, remaining, paid, returned}) |
| --- | --- | --- |
| Полный игранный сет (4/16, две строки места 3) | НОВЫЙ «16 qualifiers play all 15 matches to FINISHED; every row pays exactly once with its own receipt»; существующие «automatic semifinal/final progression…» (4 игрока) и «15 qualifiers seed the 16-slot bracket…» (5.5) | полная сетка без byes: 15/15 матчей PLAYED; placements {champion, runner-up, third-upper, third-lower}; выплаты 5/3/1/1; эскроу {12, 0, 10, 2}; ровно 6 funding-квитанций: reserve + четыре v1 row-ключа `(1, place, index)` + unused-return — множество ключей совпадает точно, дубль ключа невозможен (`transaction.create` + pre-check) |
| Финал FORFEIT | НОВЫЙ «a FORFEIT final pays the present champion and both played thirds; the absent runner-up's row returns»; существующий «one ready player wins by no-show…» (2 игрока) | D6 «финал FORFEIT»: явившийся чемпион платится (5), отсутствующему раннер-апу места нет вовсе, оба проигравших PLAYED-полуфиналов получают свои строки (1+1); возврат = неназначенная строка места 2 (3) + излишек депозита (2); эскроу {12, 0, 7, 5} |
| Полуфинал DOUBLE_OUT | НОВЫЙ «a DOUBLE_OUT semifinal sends the survivor to a BYE final; the vanished slot's row returns»; чистый якорь НОВЫЙ «technical-outcome matrices pay per D6…» в policy-тестах; существующий «technical semifinal loser receives no bronze…» (полуфинал FORFEIT, 3 игрока) | D6 «полуфинал DOUBLE_OUT»: выживший доходит до финала, финал разрешается BYE в том же узле — событие завершается тем же ADVANCE_MATCH; placements [champion, third-lower]; строка исчезнувшего слота и строка раннер-апа возвращаются; эскроу {12, 0, 6, 6}; симметричный случай FORFEIT-полуфинала уже был зелёным |
| Сбой коммита внутри settlement | НОВЫЙ «played-final settlement commit failure is atomic; the same id retries exactly once to the uninterrupted terminal state»; существующие «payout commit failure cannot split escrow…» (FORFEIT-settlement) и «mid-CLOSE commit failure…» (5.5) | честный зонд Design Notes: `failNextCommit` на оседающей транзакции игранного финала (4 выплаты + возврат + квитанции + ledger + эскроу в одном коммите); отказ атомарен (store байт-в-байт), повтор того же operationId достраивает ровно один раз; финансовый терминал сравнен с непрерывным двойником (см. ниже) — идентичен; replay квитанции байт-в-байт; свежий id после FINISHED — рестatement без вторых эффектов |
| Повторный settlement после FINISHED | существующий «forged result binding… verifies played final pays first+second once» (replay + funding-счётчик), усилен НОВЫМ кейсом сбоя (fresh-id рестatement + `money()` без дельты) | существующая D9-дисциплина: ответ идентичен, store байт-в-байт, двойной выплаты нет |
| Нулевая строка и излишек депозита | НОВЫЙ «a zero row at a played place pays nothing, consumes its position and the surplus returns at finish» | живой сет 4 игроков: строка {place 2, 0} съедает позицию раннер-апа без эффекта и без квитанции (ключа `(1,2,0)` нет), асимметричные строки места 3 маппятся по индексу (1 и 2); излишек 12 возвращается при финише; эскроу {20, 0, 8, 12}, 5 квитанций |
| Замороженная версия | НОВЫЙ «payouts consume only the CREATE-frozen rows; the stored list and its per-row receipts never move»; существующий «CREATE freezes the canonically sorted v1 list…» (ALREADY_EXISTS на новый список) | список с различными суммами строк ({1:5},{2:3},{3:1},{3:2}) оплачивается точно по замороженным строкам (dave←1, carol←2 — доказательство row-index-маппинга), `event.prizes` байт-стабилен через settlement, projection несёт свой liability 11, каждая квитанция несёт ровно сумму своей строки под каноническим ключом; эскроу {15, 0, 11, 4} |

Покрытие 2-игровых случаев (финал PLAYED / FORFEIT / DOUBLE_OUT) — существующие кейсы
«forged result binding…», «one ready player wins by no-show…», «neither ready means DOUBLE_OUT…»:
эскроу {12,0,8,4} / {12,0,5,7} / полный возврат {12,0,0,12} — все с терминальным remaining = 0.

## Терминальные эскроу-состояния (инвариант §4: reserved = paid + returned + remaining)

| Кейс | paid | returned | remaining |
| --- | --- | --- | --- |
| 16 игроков, всё PLAYED (новый) | 10 | 2 | 0 |
| 4 игрока, всё PLAYED (5.4) | 10 | 2 | 0 |
| 15 игроков, всё PLAYED (5.5) | 10 | 2 | 0 |
| Финал FORFEIT, 4 игрока (новый) | 7 | 5 | 0 |
| Финал FORFEIT, 2 игрока (5.6-эра) | 5 | 7 | 0 |
| Полуфинал DOUBLE_OUT, 4 игрока (новый) | 6 | 6 | 0 |
| Финал DOUBLE_OUT, 2 игрока | 0 | 12 | 0 |
| Сбой коммита + retry (новый) | 10 | 2 | 0 (идентичен непрерывному двойнику) |
| Нулевая строка + излишек (новый) | 8 | 12 | 0 |
| Замороженные строки + излишек (новый) | 11 | 4 | 0 |
| CANCELLED (<2 квалификантов, 5.4) | 0 | 12 | 0 |
| Legacy stored DTO (5.4) | 10 | 2 | 0 |

Каждая строка матрицы закрывает уравнение; ни один кейс не платит дважды и не оставляет
застрявших средств (квитанции RESERVE/PRIZE/UNUSED_RETURN проверяются по ключам и суммам).

## Зонд сбоя коммита: что именно сравнено

Один и тот же бракет прогоняется дважды независимыми фикстурами с одинаковыми operationId,
server-seed и временами; оседающая транзакция финала несёт 4 PRIZE-эффекта + UNUSED_RETURN +
квитанции `course_tournament_funding/*` + ledger + пользовательские receipts + финализацию эскроу.

- `failNextCommit` → «atomic commit failure»; store байт-в-байт равен снимку до отказа — окно
  «receipt staged vs escrow staged» не существует вне транзакции.
- Повтор того же payload достраивает settlement ровно один раз; финансовый терминал
  (эскроу, статусы, boxCount всех пятерых, все funding/ledger-строки с временами, placements)
  deep-equal терминалу непрерывного двойника.
- Повтор после успеха — replay квитанции (ответ идентичен, store не меняется); свежий
  operationId — рестатмент FINISHED без второй квитанции.

Честная граница сравнения: CREATE минтирует случайный admission-токен (`randomBytes`), поэтому
целstore-сравнение между двумя независимыми фикстурами невозможно по построению — cross-run
сравняется финансовое состояние (money()), внутри одной фикстуры атомарность и replay
закреплены байт-в-байт. Случайность токена не влияет на деньги: в денежных эффектах его нет.

## Замечания по порядку драйва (не продуктовые ограничения)

DOUBLE_OUT-полуфинал в харнессе оседает после игранного полуфинала: технический ADVANCE
переводит `lastMutationAtMs` к дедлайну координации стадии, после чего согласить слот второго
полуфинала уже нельзя — это существующее правило UX-O3 (подтверждения принимаются до C),
поэтому и в продукции согласование выжившего полуфинала естественно завершается до записи
технического исхода. Брекет-математика (`progressBracket`) порядок не ограничивает.

## Production gap

Не найден. Ожидание спеки «Production code — only for a proven gap; otherwise zero production
diff» выполнено: изменены только два тестовых файла. Пины 5.4 (slot-ключи legacy, проекция
чтения, канонизация списка) остались зелёными без правок — замороженная версия потребляется
маппингом и выплатой, более новый список посреди события нечитаем по построению (единственный
источник — `event.prizes`, записанный при CREATE).

## Проверка

- `cd functions && /root/.zcode/server/node run-tests.js` (node v22.x) — **109 of 109 suites
  passed**, включая `tournament-service` (57 кейсов, 6 новых) и `tournament-policy`
  (29 кейсов, 1 новый).
- `node check-syntax.js` — 200 of 200 files parse.
- `git status --porcelain -- shared/ android/` — пусто.
- Стена времени: вся сюита `tournament-service` (включая полный 16-игрокный цикл и
  502-когорту 5.5) — ~10 с; отдельный 16-игрокный кейс — 15 сыгранных матчей через
  существующие харнесс-хелперы, бюджетов на случай 5.8 спека не требует.
