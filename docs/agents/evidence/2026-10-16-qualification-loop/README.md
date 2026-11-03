# 5.5 qualification-loop-close — 2026-10-16

Story: «Close the qualification loop on the exact calculation: CLOSE_QUALIFICATION consumes
loadQualificationRanking — select→scan→calculate→publish completion, bracket seeding of the
immutable top16, phase state preserved across interruptions, legacy publisher stays fenced once a
calculation version is selected; plan §4 size matrix through the service seam with reads/writes/
time bounds recorded». Спека: `_bmad-output/implementation-artifacts/spec-5-5-qualification-loop-close.md`
(baseline `9b7a5acfad8dfa7d8c101fc7619958d511331c46`, совпал с HEAD `9b7a5acf`).

## Статус: выполнена; production-дифф нулевой

Это proof-story: семь новых кейсов в `functions/tournament-service.test.js` (40 → 47), ни одного
изменения производственного кода — механизмы выдержали все новые случаи без gap (спека ожидала
«none or minimal»; искушений production-правкой не возникло, Design Notes спеки соблюдён).

## Входы (все прочитаны 2026-10-16)

| Вход | Где | Что взято |
| --- | --- | --- |
| Спека истории | `_bmad-output/implementation-artifacts/spec-5-5-qualification-loop-close.md` | матрица I/O (7 строк), границы Always/Never, карта кода, критерии приёмки; `context:` в шапке пуст — карта кода прочитана целиком |
| Принятый ONLINE-O1, решение D2 | `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` (принят владельцем 2026-10-16, «принимаю») | семантика cutoff когорты: клэмп создания остаётся, попытка, срезанная cutoff, оседает sweep'ом `FORCED_CUTOFF` с отвеченной частью, «после cutoff попытки не существует» (A6 «NO change») |
| План §4 (особые случаи) | `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md` §4 | 0/1/2/15/16/17 участников; 502 попытки; 502 разных игрока; 1503 попытки; 4096/4097 как test sizes, НЕ продуктовый лимит; замеры времени/памяти/reads/writes; завершение после прерываний |
| Существующие доказательства | `tournament-qualification-calculation.test.js` (17 кейсов, [502,1]/[501,3]/[4096,1]/[4097,1]), `tournament-qualification-scan.test.js`, `tournament-qualification-freeze.test.js` (18 кейсов), rig-скрипты `scripts/mvp-qualification-firestore-test.js` и `scripts/mvp-qualification-calculation-firestore-test.js` | fixture-билдеры, уже доказанные размеры расчётного слоя; полный цикл на эмуляторе остаётся за rig'ами (спека: «no rig requirements») |

## Матрица → новые кейсы (все в `functions/tournament-service.test.js`)

| Строка матрицы | Кейс | Что закреплено |
| --- | --- | --- |
| 15 квалифаеров | «15 qualifiers seed the 16-slot bracket with one bye and finish with full placements» | сетка 16 слотов, раунды [8,4,2,1] = 15 матчей, узел 1v16 — bye для seed 1 уже при CLOSE; полный цикл до FINISHED; placements {champion, runner-up, third-upper, third-lower} все PLAYED; выплаты 5/3/1/1, эскроу {12,0,10,2}, организатор 90 |
| 17 участников | «a 17-row ranking at the freeze gate is refused typed with no partial effects» | guard `ranking.length > 16` → типизированный `INVALID_RANKING`; store байт-в-байт, событие остаётся QUALIFICATION, эскроу цел. Честная оговорка: корректный снапшот-билдер усекает до 16 (полный 17-игрокный когортный расчёт закреплён расчётным слоем `calculationFixture(17, 3)` и top16-политикой) — строка матрицы закрепляет саму охрану гейта от переполненной публикации |
| Прерывание внутри CLOSE | «mid-CLOSE commit failure leaves no partial effects; the same operation id retries to a consistent bracket» | `failNextCommit` на границе транзакции CLOSE: отказ атомарен (store идентичен), повтор того же operationId достраивает BRACKET без дублей, далее квитанция реплеится байт-в-байт |
| Повторный CLOSE | «repeat CLOSE replays the exact receipt; a fresh id restates the event without a second bracket» | тот же operationId — тот же ответ, store неизменен; новый id — статус-ответ BRACKET, ровно одна новая запись (operation receipt), сетка/эскроу/формы не тронуты |
| Cutoff-пины D2 | «cutoff cohort pins D2: clamp holds, cutoff-cut attempt settles into the cohort, the cutoff itself refuses» | клэмп создания: конверт позднего старта срезан ровно на cutoff (ранний старт — на собственном крае длительности); отвеченный префикс (ANSWER, digit 9) + sweep: слоты 01..19 закрываются существующим TIMEOUT/FORCED_CUTOFF с `closedAtMs == cutoff`; старт РОВНО на cutoff отказан `OUTSIDE_QUALIFICATION_WINDOW`, ничего не двигается; обе осевшие попытки входят в когорту (totalRanked 2) и CLOSE засеивает сетку [[alice,1],[bob,2]] |
| 502 попытки (loop seam) | «502-attempt cohort closes through the versioned seam; top16 seeded within recorded budgets» | 502 игроков × 1 попытка; versioned schemaVersion-2 ранкинг, собранный собственным билдером расчётного слоя (`buildQualificationRankingSnapshot`) с реальным контрактом `selectQualificationCalculation`; CLOSE → BRACKET: provenance (totalRanked 502, versions, freezeId), top16 по местам, seeds 1..16, [8,4,2,1]; бюджеты записаны и ассертятся (ниже) |
| Legacy fencing | «legacy publication stays fenced after selection while the versioned CLOSE seam completes» | `freezeQualification` (легаси-публикатор) после выбора версии отказан типизированно: `UNAVAILABLE` «Legacy publisher is fenced from the selected calculation», store не изменён; versioned CLOSE тем временем завершает цикл |

## Recorded bounds (план §4: время, память, reads/writes)

Сим-метрики case'а «qualification-loop-seam» печатаются тестом в JSON; три прогона:

```
{"case":"qualification-loop-seam","players":502,"attempts":502,"ms":2352,"transactions":3,
 "reads":12,"maxTransactionReads":8,"maxCommitWrites":32,"commitCount":3,
 "rssBytes":156704768,"rankingFingerprint":"f7a68d2d8e5b24f60cd323d8d551ea4e7e4d0570f4f5606c5a592d14f4ac33e8"}
{"...":"...","ms":2476,...,"rssBytes":151232512,"rankingFingerprint":"f7a68d2d…"}
{"...":"...","ms":2195,...,"rssBytes":150020096,"rankingFingerprint":"f7a68d2d…"}
```

- Полный цикл 502-попытки (CREATE → select → CLOSE) — **3 транзакции**, суммарно 12 чтений,
  максимум **8 чтений/транзакция** и **32 записи/коммит** (15 матчей × 2 формы + событие + квитанция);
  ассерты в тесте: `maxTransactionReads ≤ 40`, `maxCommitWrites ≤ 80` (= MAX_STAGED_WRITES).
  Бюджеты ограничивают вызовы, не население — cap'а на размер когорты нет (план §4: «test sizes,
  НЕ продуктовый лимит»).
- Время: 2195–2476 мс на весь case вместе с фикстурой (включая построение ранкинга из 502 run-строк);
  память ~150 МБ RSS процесса сюита. Отпечаток ранкинга детерминирован (три прогона — один хеш).
- Расчётный слой (без изменений, уже был зелёным): [502,1], [501,3] = 1503 попытки, [4096,1],
  [4097,1] — с попроцессорными замерами и границами вызовов (queries ≤ 51 документ,
  ≤ 101 запись/транзакцию, ≤ 655 чтений/транзакцию) в `tournament-qualification-calculation.test.js`.
- Честная граница шва: seam-кейс потребляет schemaVersion-2 заморозку, собранную реальным
  билдером по 502 run-строкам с реальным выбранным контрактом; сам durable-конвейер
  select→scan→calculate→publish на [502,1]/1503/4096/4097 остаётся доказанным расчётным слоем,
  полный цикл на эмуляторе — rig'ами `scripts/mvp-qualification-firestore-test.js` /
  `scripts/mvp-qualification-calculation-firestore-test.js`. В unit-шве 502 зелёные полностью,
  урезания размера не потребовалось.

## Production gap

Найден **один** ложный след, который оказался не gap'ом продукта, а ошибкой первого черновика
теста: одиночная осевшая попытка легитимно не ранжируется (LS пропускает группу с <2 результатов,
`tournament-ranking.js:23`), поэтому cutoff-пин использует двух участников — ранний (край
конверта) и поздний (край cutoff). Код продукта не менялся; ожидание спеки «production diff:
zero» выполнено.

## Проверка

- `cd functions && /root/.zcode/server/node run-tests.js` (node v22.16.0) — **109 of 109 suites
  passed**, включая `tournament-service` (47 кейсов, 7 новых), policy/freeze/scan/calculation —
  без изменений и зелёные.
- `git status --porcelain -- shared/ android/` — пусто.
- Единственный изменённый файл — `functions/tournament-service.test.js`.
