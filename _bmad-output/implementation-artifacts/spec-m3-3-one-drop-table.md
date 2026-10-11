---
title: 'M3.3 — A bought box and an earned box are the same box'
type: 'feature'
created: '2026-10-11'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
baseline_commit: 'ff5601a557de88328d8f77412e714c76c1c691ac'
story_key: '3-3-купленная-коробка-и-заработанная-одинаковы'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-1-server-purchase-verification.md'
warnings: []
deferred:
  - summary: >-
      Ключ квитанции receipt.box_pack_credited не имеет клиентской локализации — она появится
      вместе с клиентской историей коробочной витрины (полка + строки каталога).
    evidence: |-
      Сервер пишет квитанцию ключом и параметрами; устройство собирает текст на своём языке.
      Пользователь недостижим: нет ни SKU в Play Console (действие владельца), ни полки в клиенте
      (вне этой истории), поэтому квитанция не возникнет, пока не доедет клиентская история.
    location: >-
      functions/purchase-verification.js receiptRecord (BOX branch)
    severity: low
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** коробку можно заработать (серия, реклама, премиум-бонус), но купить за деньги нельзя:
таблица констант не знает коробочных SKU, и проверка чека отклонила бы такой товар как
«непродаваемый» — хотя комментарий `goldPackAmount` с самого ждал «своей таблицы» для коробок.
А гарантии честности — один дроп, второго нет, повторного открытия не бывает — сегодня держатся
на форме кода, но ни один тест их не называет.

**Approach:** коробочный SKU становится полноправным товаром расчёта по образцу золотых паков:
таблица `boxPacks` в `configs/economy` (SKU → коробок за единицу), решение расчёта знает третий
productKind BOX, грант кладёт коробки в тот же `boxCount`, из которого их берут серия, реклама и
премиум-бонус. Содержимое решает единственный `generateGiftBoxReward` внутри OPEN — таблица одна,
и тесты это называют: источник дропа ровно один, повторное открытие невозможно, NEGATIVE не
бывает. Витрина и клиентская полка — не здесь (покажется с историей 3.4/эпиком витрины); SKU в
Play Console не существует, всё закрепляется фикстурами.

## Boundaries & Constraints

**Always:** купленная коробка приходит тем же порядком эпика 1 — Play подтверждает токен, одна
транзакция пишет расчёт, квитанцию, аудит и строку книги (BOXES, новый повод BOX_PACK рядом с
GOLD_PACK), `create`-охрана от гонки. Коробки приходят в один и тот же `boxCount` — отдельного
счётчика купленных нет и не появляется. Недостача (shortfall) — золотая история: коробочный грант
её не трогает и не гасится ею. Количество покупки умножает грант, как у золота; quantity > 1 идёт
через тот же V2-доказательство. Таблица `boxPacks` — серверное знание: в белый список констант для
устройства она не попадает (как `goldPacks`). Начальные значения таблицы пусты: SKU заводит
оператор на сервере, как разрешает механизм `goldPacks` — известных коробочных товаров пока нет.

**Ask First:** изменение порядка «Play → начисление → ответ → гашение»; премильная ветка расчёта;
клиент и Kotlin (в т.ч. `EconomyConstants.BOOTSTRAP` — пустые начальные значения JSON не меняют).

**Never:** второй таблицы дропа; отдельного пути открытия для купленной коробки; начисления
коробок мимо `boxCount`; клиентского товарного ряда в этой истории.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Купленная коробка | проверенный чек, SKU из `boxPacks`, quantity 1 | `boxCount` +1, квитанция BOXES, аудит, строка книги BOXES +1 (BOX_PACK), одной транзакцией | N/A |
| Тот же токен снова | запись расчёта есть | тот же ответ, начисления нет (существующий гейт) | not an error |
| quantity 3 | проверенный чек, q=3 | +3 коробки; q>1 требует V2-доказательства, как золото | N/A |
| SKU не из таблицы | коробочного SKU нет в `boxPacks` | отказ SKU_NOT_SOLD, ноль начислений | not an error |
| SKU в двух таблицах | и в `goldPacks`, и в `boxPacks` | отказ «конфликт классов товара» | not an error |
| Коробочный SKU против подписки | premium-таблица знает тот же SKU | отказ «конфликт классов товара» | not an error |
| Игрок с недостачей | shortfall > 0, коробка куплена | коробка приходит целиком, shortfall не меняется | N/A |
| Повторное открытие | тот же `openingId` | тот же ответ, содержимое не переразыгрывается | not an error |
| Открытие пустого счётчика | `boxCount` 0 | NO_BOXES, ничего не списано | not an error |
| Второй источник дропа | grep по `generateGiftBoxReward` | ровно один вызов в box-service.js — исходник-тест | N/A |

</frozen-after-approval>

## Code Map

- `functions/economy-constants.js` -- `boxPacks(value)` по образцу `goldPacks` (:176): SKU →
  коробок за единицу, целое от единицы; пустые начальные значения (`DEFAULTS.boxPacks = {}`),
  посторонние SKU правильной формы принимаются; `readEconomyConstants` добавляет `boxPacks`;
  `clientEconomyConstants` НЕ добавляет (серверное знание, как `goldPacks`).
- `functions/config/economy-constants.json` -- пустая секция `"boxPacks": {}` — форма таблицы
  видна и серверу, и тестам; Kotlin `EconomyConstants.BOOTSTRAP` не трогается (пустое не знает).
- `functions/purchase-verification.js` -- `PRODUCT_BOX = "BOX"`, `boxPackAmount(constants, sku)`
  по образцу `goldPackAmount` (:590); `decideSettlement`: конфликт классов (премиум×золото×коробки)
  назван отказом, ветка BOX-кредита `{sku, productKind: PRODUCT_BOX, boxesPerUnit, quantity,
  boxesGranted, goldGranted: 0, isTest, constantsVersion}`; `successResponse` несёт
  `{productKind: "BOX", boxesGranted}`; `receiptRecord` — BOX-ветка (messageKey
  `receipt.box_pack_credited`, currency "BOXES", amount boxesGranted); `auditRecord` — BOX-поля
  рядом с `premiumRecordFields`.
- `functions/purchase-settlement.js` -- BOX-ветка транзакции по образцу золотой: `boxCount +N`
  абсолютом, `create` записи расчёта, квитанция, аудит, строка книги `CURRENCY.BOXES` с поводом
  `REASON.BOX_PACK`; shortfall не читается и не пишется.
- `functions/currency-ledger.js` -- `REASON.BOX_PACK = "box_pack"` (+ закрытый список в
  `currency-ledger.test.js`, как PURCHASE_REFUND в 1.3).
- Read-only: `functions/box-service.js` (OPEN не меняется вовсе), `functions/gift-box-rewards.js`,
  `shared/**`, `android/**`.
- Tests: `functions/economy-constants.test.js` (читатель boxPacks), `functions/purchase-verification.test.js`
  (решения по матрице), `functions/purchase-settlement.test.js` (транзакция покупки коробки против
  подделки Firestore — форма как у золотого пака), `functions/currency-ledger.test.js` (повод),
  `functions/box-service.test.js` -- extend: исходник-тест «generateGiftBoxReward вызывается ровно
  из box-service» (образец статики — premium-neutrality.test.js) и закрепление NO_BOXES/replay,
  если строки ещё нет.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/economy-constants.js` + JSON + `.test.js` -- таблица boxPacks -- по образцу goldPacks.
- [ ] `functions/purchase-verification.js` + `.test.js` -- BOX-класс товара: решение, ответ,
  квитанция, аудит -- по матрице.
- [ ] `functions/purchase-settlement.js` + `.test.js` -- BOX-ветка транзакции -- по матрице.
- [ ] `functions/currency-ledger.js` + `.test.js` -- повод BOX_PACK -- закрытый список.
- [ ] `functions/box-service.test.js` -- один источник дропа, повторное открытие, NO_BOXES -- исходник-тест.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `3-3-…` →
  `in-progress`, затем `review`.

**Acceptance Criteria:**
- Given коробка куплена за деньги, когда чек проверен, то коробка приходит в тот же `boxCount` и
  её содержимое выдаёт единственная таблица дропа через единственный OPEN.
- Given два игрока открыли коробку — купленную и заработанную, когда сервер выдаёт содержимое,
  то выбор между ними ничем не различается: одна и та же функция, одна и та же таблица вероятностей.
- Given кодовая база, когда её сканирует исходник-тест, то у `generateGiftBoxReward` ровно один
  вызывающий модуль.
- Given одно открытие, когда запрос повторяется с тем же `openingId`, то содержимое не
  переразыгрывается и коробка не списывается дважды.
- Given коробочных SKU нет в таблице, когда приходит чек с таким SKU, то расчёт отказывает
  SKU_NOT_SOLD и не начисляет ноль.

## Implementation Notes

- Route `full`; реализация и линзы — в этой же сессии (те же платформенные ограничения, что в 3.1).
- 2026-10-11: пустая секция `"boxPacks": {}` в общем JSON безопасна для Kotlin-паритета —
  EconomyConstantsParityTest читает только известные ему разделы; гейт
  :shared:feature:economy:domain:allTests зелёный.
- 2026-10-11: маршрут SKU класс BOX уже правильный без правок — `isSubscriptionSku` требует
  премиальной таблицы, коробочный SKU идёт товарным endpoint'ом; multi-quantity V2-доказательство
  общее с золотом (productKind !== PREMIUM) — покрыто тестом q=3 с V2-подделкой.
- 2026-10-11: существующие тесты box-service уже закрепляли replay открытия и NO_BOXES —
  добавлен только исходник-тест единственного вызывающего дропа.

## Spec Change Log

## Review Triage Log

### 2026-10-11 — Review pass (thorough: blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

Линзы в сессии (то же платформенное отклонение, что в 3.1); пол слепого охотника: N = 8 по 49 КБ
диффа — честный итог шесть находок, добивать нефиндингами не стал.

- verdicts: 6 findings — high 0, medium 0, low 1, false 5, maybe-false 0
- findings:
  - `[low]` `[defer]` Ключ квитанции `receipt.box_pack_credited` пока без клиентской локализации —
    пользователь недостижим до товарного SKU в Play Console и клиентской полки; строка заведётся
    клиентской историей витрины (см. Deferred).
  - `[false]` Ключ квитанции инлайн-литералом, а не именованной константой, как у золота —
    опровергнуто: премиальная ветка рядом использует инлайн-литерал («receipt.premium_credited»),
    стиль ближайшего соседа соблюдён.
  - `[false]` Повтор по рукописной записи расчёта без boxesGranted вернул бы 0 — опровергнуто:
    запись создаёт только этот код, поле ставится всегда; safeNumber — защита формы, не поведения.
  - `[false]` Параллельные писатели boxCount (покупка, выдача за рекламу, OPEN) потеряют счёт —
    опровергнуто: все пишут абсолютом в транзакциях, читающих тот же документ; Firestore сам
    прерывает конфликтующую транзакцию, повтор видит свежее значение.
  - `[false]` Тройной конфликт классов (SKU в трёх таблицах) проскочит парные проверки —
    опровергнуто: любая пара из трёх не-null уже запирает условие, тройка непредставима без пары.
  - `[false]` Частичный возврат коробочной покупки недоначислит — опровергнуто: количество идёт
    через тот же общий механизм (V2-доказательство + creditedQuantity), тест q=3 закрепляет
    умножение, сюита purchase-partial-refund остаётся зелёной на общей ветке.

## Design Notes

- Коробки не гасят золотую недостачу и не попадают в неё: недостача — валюта GOLD, коробки —
  BOXES; смешивать значило бы платить возвращённым золотом за коробки.
- `replayResponse` собирает ответ из полей записи без ответа — BOX-поля должны доехать туда же,
  иначе повтор по рукописной записи потеряет productKind.
- Витрина (полка, ряд с ценой Play) — клиентская работа вне этой истории: сервер готов принимать
  грант, но товара в Play Console нет, и честная полка скажет «товары не выставлены» (1.4).

## Verification

**Commands:**
- `node functions/run-tests.js` -- expected: все сьюты зелёные.
- `node functions/check-syntax.js` -- expected: чисто.

**Manual checks (no CLI):**
- SKU в Play Console не существует — путь закрепляется фикстурами; витрина — не в истории.

## Auto Run Result

Status: done
Blocking condition: none

**Summary:** купленная коробка — полноправный товар расчёта. Таблица `boxPacks` в configs/economy
(по образцу goldPacks, пустые начальные значения, на устройство не уезжает), третий класс товара
BOX в решении расчёта, грант кладёт коробки в тот же `boxCount`, из которого их берут серия,
реклама и премиум-бонус, одной транзакцией с записью расчёта, квитанцией BOXES, аудитом и строкой
книги с новым поводом BOX_PACK. Недостача коробки не касается. Содержимое решает единственный
`generateGiftBoxReward` внутри единственного OPEN — исходник-тест называет это: у дропа ровно один
вызывающий модуль; повторное открытие и пустой счётчик уже были закреплены тестами box-service.

**Files changed:**
- `functions/economy-constants.js` + `config/economy-constants.json` — таблица boxPacks (серверное знание).
- `functions/purchase-verification.js` — PRODUCT_BOX, boxPackAmount, решение/ответ/квитанция/аудит BOX.
- `functions/purchase-settlement.js` — BOX-ветка транзакции (boxCount абсолютом, книга, записи).
- `functions/currency-ledger.js` — повод BOX_PACK.
- Тесты: economy-constants (+читатель и белый список), purchase-verification (+3 решения),
  purchase-settlement (+5 транзакционных), currency-ledger (закрытый список), box-service (+скан дропа).
- Спека + `sprint-status-monetisation.yaml` — 3-3: backlog → done.

**Review findings breakdown:** 0 патчей (находок, требующих правки, не оказалось), 1 defer
(клиентская локализация ключа квитанции — придёт с клиентской историей витрины, в frontmatter),
5 отвергнутых с опровержениями. followup_review_recommended: false.

**Verification:** `node functions/run-tests.js` — 104/104 зелёные; `node functions/check-syntax.js` —
190/190; `sgw ./gradlew :shared:feature:economy:domain:allTests` — зелёные (паритет JSON↔BOOTSTRAP
не задет пустой секцией). Матрица покрыта: покупка/повтор/q3/неизвестный SKU/конфликт классов/
недостача/повторное открытие/NO_BOXES/скан дропа.

**Residual risks:** реальная покупка недостижима, пока владелец не заведёт SKU в Play Console и
`boxPacks` в таблице (механизм принят,fixtures закрепили); ключ квитанции ждёт клиентскую строку
(defer).
