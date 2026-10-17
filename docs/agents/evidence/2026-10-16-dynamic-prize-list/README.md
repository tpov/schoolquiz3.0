# 5.4 server-dynamic-prize-list — 2026-10-16

Story: «server CREATE/reserve/read/payout over the adopted versioned prize list (UX-O2
implementation)» — спека
`_bmad-output/implementation-artifacts/spec-5-4-server-dynamic-prize-list.md` (route full/pinned,
`context: []` — карта кода спеки прочитана как список входов), baseline
`f9ab7e9c1ff63d5a401bfd7c435297ac297e9fef` (HEAD совпал с `baseline_revision` спеки на момент
начала). Исполняемый закон — принятый владельцем договор
`_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` (издание 1, sha256
`366860da…`, решение 2 в `owner-decisions-2026-10-16.md`): его Приложение A1–A12 и есть список
изменений. Клиентский код (`shared/`, `android/`) не тронут.

## Что построено

| Кусок | Где | Суть |
| --- | --- | --- |
| Валидатор v1 (CREATE) | `functions/tournament-policy.js` — `validatePrizeSchedule` | принимает только `{listVersion: 1, rows}`; строка ровно `{place, amountBoxes}`, place 1..16 (`MAX_QUALIFIERS`), amount ≥ 0 safe integer, строк ≤ 16; пустой список легален (liability 0); канонизация стабильной сортировкой `(place, порядок подачи)`; legacy-DTO отвергается типированной ошибкой, называющей форму v1 (L4) |
| Двухформенная ревалидация | тот же файл — `validateStoredPrizeSchedule` | settlement-вход принимает замороженный v1-список ИЛИ хранимый `{first, second, third}` (дискриминатор — наличие `listVersion`); liability считается суммой строк по BigInt с отказом вне safe-range — для legacy это `first+second+2*third` структурно, через проекцию (A12, L3) |
| Проекция чтения | тот же файл — `prizeListProjection` + `prizeView` в `tournament-service.js` | единая красная форма `{listVersion, rows, maximumLiabilityBoxes}` для обеих хранимых форм: legacy → `listVersion: 0` и строки `[{1,first},{2,second},{3,third},{3,third}]` (проекция, не хранение); v1 → `listVersion: 1` и замороженные строки; идемпотентна (eventView-вывод, повторно редактируемый контроллером, проходит насквозь); поля DTO наружу не идут |
| Выплаты | тот же файл — `planPrizePayouts` | назначение по `(place, row-index)` × фиксированный порядок финишистов (`PRIZE_PLACE_SLOTS`: place 3 — `third-upper`, затем `third-lower`, порядок пушей `progressBracket`); j-й платящий финишист места получает j-ю строку; финишист без строки и строка без финишиста остаются в возврате; `payoutId` v1 = канонический `key(eventId, "prize", 1, place, rowIndex)`; охраны слота/ранга, дубликатов slot/uid, явный boolean `technicalForfeit`, пропуск технических placement'ов (не платят и не занимают строк) и нулевые суммы — без изменений (A6) |
| Legacy-settlement | тот же файл, внутри `planPrizePayouts` | хранимый DTO исполняется через listVersion-0 проекцию с вчерашними slot-ключами `key(eventId, "prize", slot)` и теми же суммами (A9/L3); слоты ДОС платят ровно как `:406` старого кода; миграции хранимых событий нет (L1) |
| CREATE-заморозка | `functions/tournament-service.js` | `normalizeTournamentRequest` прогоняет `prizes` через v1-валидатор и несёт в запросе уже каноническую форму (отпечаток операции покрывает канон — переупорядоченные строки того же списка дают replay, а не CONFLICTING_REPLAY); `createEventPlan` вмораживает `{listVersion: 1, rows}` + `maximumLiabilityBoxes` в документ события; escrow как раньше резервирует весь депозит, излишек возвращается через существующий `unusedReturnBoxes` путь; ни один путь не пишет `prizes` после CREATE |
| Redaction | `functions/course-tournament-controller.js` — `safeEvent` | пин `["first","second","third"]` заменён единой проекцией `prizeView`; `readEvent`, `listEvents` и все ответы идут через неё; на событиях без `prizes` (синтетические документы discovery-фикстур) сохранено прежнее толерантное поведение `{}` |

Сознательно НЕ менялось (NO-change-охраны договора): матрица мест и BYE-семантика
`progressBracket` (A4), механика квитанций/pre-check/`transaction.create` и ключи возврата (A7),
`assertEscrow` и intact-only финализация `finish` (A11), repeat-CREATE гварды (`ALREADY_EXISTS`,
`CONFLICTING_REPLAY`), идемпотентность на существующих ключах (D9).

## Карта Приложения A (что сделано по каждой строке)

- **A1 (схема v1)** — сделано: `validatePrizeSchedule` = валидатор v1 c канонизацией; конверт
  CREATE (`source/startsAtMs/prizes/fundedBoxes`) не менялся.
- **A2 (liability/достаточность)** — сделано: `maximumLiabilityBoxes = Σ amountBoxes` (BigInt,
  safe-range отказ); текст отказа «insufficient prize fund» сохранён; баланс организатора не
  тронут.
- **A3 (заморозка одной версии)** — сделано: канонический `{listVersion: 1, rows}` замораживается
  в `event.prizes` при CREATE; пишущих путей после CREATE нет (проверено чтением `finish`).
- **A4 (матрица мест)** — NO change: `progressBracket` не тронут байт-в-байт.
- **A5 (назначение строк и выплаты)** — сделано: `(place, row-index)` × фиксированный порядок
  финишистов; `rowKey` v1 в `payoutId`; сортировка выплат следует каноническому порядку строк.
- **A6 (охраны выплат)** — NO change: слоты/ранги, дубликаты, `technicalForfeit`-skip, нулевые
  суммы — сохранены; изменилась только идентичность `payoutId` для v1.
- **A7 (квитанции и ключи)** — NO change механики: v1-выплата несёт новый `rowKey` в `sourceId`;
  legacy-ключи (slot) сохранены для хранимых событий и не пересекаются (чужие `eventId`).
- **A8 (проекция чтения)** — сделано: `safeEvent`/`eventView` отдают `{listVersion, rows,
  maximumLiabilityBoxes}` для обеих форм; DTO наружу не отдаётся.
- **A9 (legacy-settlement)** — сделано через проекцию L3: суммы совпадают с сегодняшними
  (регрессионный тест на исполненном кейсе 100/50/30 → paid 210, обе трети по 30, slot-ключи);
  хранимые документы не мигрировались.
- **A10 (граница принятия)** — сделано: legacy-DTO в новых CREATE отклоняется; места вне {1,2,3}
  легальны и возвращаются (покрыто тестом).
- **A11 (эскроу и терминация)** — NO change формул; источник обязательств внутри переключён на
  A1/A5.
- **A12 (двухформенная ревалидация)** — сделано: `validateStoredPrizeSchedule` + вызов внутри
  `planPrizePayouts`; liability проекции = `first+second+2*third` (тест).

## Миграция фикстур (механическая, эквивалентность `{f,s,t}` → строки `[{1,f},{2,s},{3,t},{3,t}]`)

- `functions/tournament-policy.test.js` — фикстура `prizes` переведена на v1 (liability 200/200
  сохранён); старый «fund»-тест разделён на набор v1-CREATE/двухформенных/projection/выплатных
  тестов (итого 26 кейсов).
- `functions/tournament-service.test.js` — `PRIZES`-константа, все CREATE-фикстуры и тест
  «missing prices» переведены; отказ legacy-DTO теперь проверяет сообщение v1-валидатора;
  underfunded переведён на v1 (fundedBoxes 9 < 10).
- `functions/course-tournament-controller.test.js` — `create()` хелпер на v1-строках.
- `functions/course-tournament-handler.test.js` — `create()`/`world()` CREATE на v1-строках.
- `functions/tournament-source-bank.test.js` — два прямых CREATE-payload'а переведены (иначе
  падали бы на L4 до STALE_PREPARATION/успешного CREATE).
- `scripts/course-tournament-http.test.js` — `createRequest` переведён (hash-записи этого рига
  вычисляются в рантайме и не ассертятся — перепина не требует).
- `scripts/mvp-qualification-firestore-test.js` — `createRequest` переведён; перепин inline-SHA-256
  `course-tournament-controller.js` (`60f1dff8…` → `f1ce23d0e9fdea3d3c7ea72765bd7c593e7a3fbcd92c2711b96b061813561c9f`).
- `scripts/mvp-qualification-calculation-firestore-test.js` — `createRequest` переведён; хеши
  этого рига читаются из внешнего `QS_SOURCE_MANIFEST` (вне репозитория) — при следующем риг-прогоне
  манифест нужно перегенерировать для `course-tournament-controller.js` и `tournament-service.js`
  (см. «Осталось»).
- `functions/tournament-qualification-scan.test.js` / `tournament-qualification-freeze.test.js`
  содержат legacy `prizes: {first: 0, second: 0, third: 0}` в хранимых event-документах — оставлены
  как есть сознательно: это хранимая legacy-форма (L1, легальна вечно), CREATE-путь эти документы
  не валидирует.

## Проверка

- `cd functions && /root/.zcode/server/node run-tests.js` — 109/109 наборов зелёные (полная
  батарея; Node v22.16.0). Внутри: policy 26 кейсов, service 40, controller 46, handler 7,
  source-bank 49.
- Новые матричные кейсы: policy — v1-канонизация/границы строк/BigInt-переполнение Σ, L4-отказ
  legacy-DTO с сообщением о форме v1, двухформенная ревалидация, projection без утечки DTO,
  обе третьи по каноническому rowKey, одна третья строка, строка без финишиста, места вне {1,2,3}
  возвращаются, нулевые строки, legacy-settlement 100/50/30 → 210 c slot-ключами и техническим
  третьим; service — заморозка канонической формы при CREATE + replay переупорядоченных строк +
  `ALREADY_EXISTS` без второго резерва, legacy-settlement 4 игроков → суммы [5,3,1,1], slot-ключи
  квитанций, проекция чтения `listVersion: 0` без полей DTO; controller — readEvent/listEvents
  проекция обеих форм без утечки.
- `node --check` на всех трёх изменённых rig-скриптах — парсятся.
- `git status --porcelain -- shared/ android/` — пусто.
- Консервация (инвариант `reserved = paid + returned + remaining`, терминальный `remaining = 0`)
  покрыта исполнением: полная выплата 200/200, частичная 125/105, отмена 210 → возврат 210,
  излишек депозита 12 → возврат 2 при paid 10 (service-тесты и §4 договора).

## Осталось / риски (честно)

Правки review pass 1 (2026-10-16, оркестратор):
- Терпимость чтения восстановлена: `prizeView`/`safeEvent` опускают не-объектные/отсутствующие
  призы (как старый `pick`), повреждённый объект призов даёт типовой `UNAVAILABLE` («Event
  prizes require repair»); `prizeView` идемпотентен поверх уже отредактированного view
  (liability берётся с события или из проекции); `maximumLiabilityBoxes` валидируется.
- Ключ выплаты: фактическая форма — плоский массив `["tournament", eventId, "prize",
  listVersion, place, rowIndex]` (rowKey развёрнут в элементах; legacy — 4 элемента со slot).
  Прозвище `rowKey` в текстах договора означает эту развёрнутую форму.
- «createEventPlan» определён в `tournament-policy.js` (заморозка при CREATE — через policy),
  в `tournament-service.js` — только вызов; прежняя строка таблицы вводила в заблуждение.
- Перепин в `mvp-qualification-firestore-test.js` коснулся и предсуществующего устаревшего
  пина `tournament-qualification-scan.js` (d1519936… → 683b1c00…; файл менялся в 45ab9d82 без
  перепина — риг падал бы на нём до эмулятора).
- Окно клиентской совместимости не требуется: ни один выпущенный клиент не привязан к этим
  callable (brownfield: биндингов нет); проекцию L2 потребляют истории 5.9/5.10.
- Сообщение L4 различает формы: объект без listVersion, но с first/second/third —
  «fixed form is not accepted»; прочие — просто «prizes must be {listVersion: 1, rows}».

1. **Риг-прогоны не исполнялись**: `course-tournament-http.test.js` и оба `mvp-qualification-*`
   требуют приватный demo-эмулятор (`schoolquiz-mvp-firebase`), `/repo`-монтирование и — для
   calculation-ригa — внешний `QS_SOURCE_MANIFEST`; по правилам каталога проекты боевые
   эмуляторы/сессии во время подготовки исходников не поднимаются. Фикстуры переведены, файлы
   парсятся, inline-перепин сделан; манифест calculation-ригa нужно перегенерировать при
   следующем прогоне (иначе ассерт «Unreviewed mounted source» честно отвергнет новые
   `course-tournament-controller.js`/`tournament-service.js`).
2. **Порядок выплат внутри третьих мест изменился для legacy-проекции**: старый код сортировал
   `(rank, slot)` (`third-lower` раньше `third-upper`), единый код сортирует по каноническому
   порядку строк (`third-upper` = row 0 раньше). Договор пинит только суммы (L3: «любое
   расхождение сумм — регрессия»); суммы, ключи и квитанции идентичны. Отмечено здесь как
   story-caused divergence в порядке следования effects.
3. **Порядок присвоения строк техническому случаю**: `technicalForfeit`-placement сегодня никогда
   не создаётся (D6), а охрана-пропуск сохранена; реализация трактует его как «не платит и не
   занимает строку» (следующий финишист фиксированного порядка берёт следующую строку) — покрыто
   policy-тестом, но это интерпретация защитной ветки, не исполненный сценарий baseline.
4. Места >3 в строках легальны и всегда возвращаются при текущем формате сетки top16 — следствие
   D7, уже зафиксированное договором как вещь, которую владелец может пересмотреть только новой
   редакцией издания.
5. Клиентские поверхности (отображение `rows`, подписи мест, локализация) — истории 5.9/5.10;
   сервер теперь отдаёт только проекцию, старый DTO-вид отключён для ВСЕХ событий, включая
   хранимые legacy (L2 — договорное требование).
