---
title: 'M4.1 — A bid debits gold at once, and the open table shows it'
type: 'feature'
created: '2026-10-12'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
baseline_commit: '7de873f2b05df3cc07a4631dd9e4823ac001d01b'
story_key: '4-1-ставка-списывает-золото-сразу'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m3-4-ads-have-two-homes.md'
warnings: []
deferred: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** аукциона предложений не существует: поставить золото за место на экране предложений
некуда и нечем — ни записи ставки, ни списания, ни открытого стола ставок.

**Approach:** ставка живёт на сервере: callable списывает золото и записывает ставку одной
транзакцией (списание абсолютом на документе игрока + строки книги движений рядом); повышение
своей ставки — возврат прежней суммы и списание новой в той же транзакции; пока аукцион идёт,
стол ставок открыт всем участникам через читающий view. Аукцион — на следующий месяц; документ
аукциона ключуется месяцем, поэтому «ровно один открытый аукцион в каждый момент» (CAP-11)
держится структурой идентификатора.

## Boundaries & Constraints

**Always:** списание и запись ставки — одна транзакция; баланс после списания не ниже нуля,
иначе именованный отказ и ни одной записи. Повышение — refund прежней суммы и списание новой в
одной транзакции (сеть движений книги: строки возврата и нового списания, каждая со своим
уникальным sourceId). Ставка несёт объявление (изображение и ссылка) — строки, необязательные
для расчёта, но обязательные для показа позже. Открытый стол показывает кто, сколько и когда —
без creative объявления (непроверенная картинка на публичный стол не едет — это история 4.3).
Именованные отказы кодами; повтор того же размера ставки отвечает состоянием, ничего не списывая
(NFR2).

**Ask First:** закрытый список поводов книги движений (расширение — два новых повода OFFER_BID /
OFFER_BID_REFUND); форма документов users/{uid} (gold абсолютом, как соседние пути).

**Never:** никакого клиентского начисления/списания; никакого показа creative до проверки (4.3);
никаких констант аукциона в index.js или Kotlin; никаких правил-доступа к новым коллекциям
(только server + callables).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Первая ставка | аукцион открыт, золота хватает | золото −N, ставка записана, строка книги −N, одна транзакция | N/A |
| Короткий кошелёк | amount > gold | отказ FAILED_PRECONDITION, ничего не записано | named refusal |
| Повышение своей ставки | ставка 10 → 15 | одна транзакция: +10 refund-строка, −15 debit-строка, золото −5, сумма ставки 15 | N/A |
| Повышение без денег | gold < дельта | отказ FAILED_PRECONDITION, прежняя ставка и золото нетронуты | named refusal |
| Повтор той же суммы | amount = текущая ставка | состояние как есть, ноль записей, золото не тронуто | not an error |
| Ставка после конца месяца | now ≥ endsAtMs | отказ FAILED_PRECONDITION «аукцион закрыт» | named refusal |
| Испорченная сумма | 0, дробное, не число | отказ INVALID_ARGUMENT | named refusal |
| Испорченный аукцион | статус/сроки не той формы | отказ INVALID_ARGUMENT громко, ничего не записано | named refusal |
| Ставка без документа аукциона | первый игрок месяца | аукцион создаётся в той же транзакции (id = месяц) и ставка принимается | N/A |

</frozen-after-approval>

## Code Map

- `functions/offer-auction.js` -- новый чистый модуль денежного пути (образец: ad-reward-progress.js /
  premium-shop.js): константы коллекций (`offer_auctions`, `offer_bids`), `openOfferAuction(nowMs)`
  (месяц now+1, границы UTC), `planOfferBid` (чистое решение: валидация, дельта, отказы),
  `placeOfferBid({db, uid, amount, imageUrl, link, now})` (одна транзакция: аукцион при отсутствии
  создаётся, ставка пишется, золото абсолютом, строки книги), `offerAuctionView` (открытый стол).
- `functions/currency-ledger.js` -- добавить REASON.OFFER_BID и REASON.OFFER_BID_REFUND (закрытый
  список расширяется в единственном месте).
- `functions/index.js` -- тонкая обвязка: `exports.placeOfferBid` (MONETARY_CALLABLE_OPTIONS,
  requireAuthUid, перевод именованных кодов — образец: ветка premium в applyShopPurchase) и
  `exports.fetchOfferAuction` (CALLABLE_OPTIONS, чтение). Логики здесь нет.
- `firestore.rules` -- deny all для `offer_auctions/{document=**}` и `offer_bids/{document=**}`
  (образец: ad_reward_confirmations).
- Read-only: `functions/premium-shop.js` (образец именованных отказов и ключей sourceId),
  `functions/premium-test-db.js` (подделка Firestore для тестов), `functions/gift-boxes.js`.
- `functions/offer-auction.test.js` -- новый сьют: матрица + книга движений + месяц через год.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/currency-ledger.js` -- два повода OFFER_BID / OFFER_BID_REFUND -- движение без
  повода нельзя объяснить.
- [ ] `functions/offer-auction.js` -- модуль ставки: план + транзакция + view -- по матрице.
- [ ] `functions/index.js` -- placeOfferBid / fetchOfferAuction -- единственная обвязка.
- [ ] `firestore.rules` -- deny all двух коллекций -- ставки и аукцион не читаются с устройства.
- [ ] `functions/offer-auction.test.js` -- матрица, книга, год-через-декабрь -- закрепить.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- 4-1 → in-progress,
  затем review, затем done с комментарием.

**Acceptance Criteria:**
- Given открытый аукцион на следующий месяц, когда игрок ставит золото, то списание и запись
  ставки — одна транзакция (один коммит подделки), и в книге есть строка −N.
- Given у игрока уже есть ставка, когда он повышает её, то возврат прежней и списание новой —
  та же одна транзакция, две строки книги, сумма ставки обновлена.
- Given ставка сделана, когда участники читают открытый стол, то видны uid, сумма и время
  каждой ставки, отсортированные по сумме, и не видны creative объявлений.
- Given любой отказ (деньги, сумма, срок, испорченность), когда он происходит, то ответ
  именованный и ни одной записи не сделано.

## Implementation Notes

- Route `full`; реализация и линзы — в этой же сессии: платформа не дала примитива субагентов
  (те же записанные отклонения, что в 3.1–3.4); дифф перечитывается на каждую линзу.
- 2026-10-12: авто-создание документа аукциона первой ставкой — исполнение инварианта CAP-11
  «ровно один открытый аукцион в каждый момент» без операторского бутстрапа; ключ-месяц делает
  создание идемпотентным (гонка двух первых ставок — откат второй и повтор по образцу guard'а 3.1).
- 2026-10-12: уточнение строки матрицы «ставка после конца месяца» без правки заморозки: ключ
  месяца-цели делает ставку «после конца месяца» недостижимой по построению (ставка в декабре
  уходит в январский документ), поэтому отказ достижим в форме «документ месяца-цели существует
  и не OPEN» (закрыт оператором, рассчитан, часы назад) — отказ FAILED_PRECONDITION «аукцион
  закрыт» реализован и закреплён тестом именно в этой форме; буквальный guard `now >= endsAtMs`
  стоит рядом как страховка. Матрица покрыта.
- 2026-10-12: premium-test-db получил чтение ограниченных запросов вне транзакции
  (`collection.where(...).orderBy(...).limit(...).get()`), совместимо: возрастающий порядок —
  по-прежнему голое имя поля, как его читают существующие потребители подделки.

## Spec Change Log

## Review Triage Log

### 2026-10-12 — Review pass (thorough: blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

Линзы исполнены в сессии (платформа без субагентов — то же отклонение, что в 3.1–3.4, записано
в Implementation Notes); дифф перечитан на каждую линзу.

- verdicts: 7 findings — high 0, medium 3, low 1, false 3, maybe-false 0
- findings:
  - `[medium]` `[patch]` Открытый стол при >200 ставках показывал произвольное подмножество
    (запрос без порядка — Firestore отдаёт любые 200, топ стола мог быть невидим) — исправлено:
    запрос `orderBy("amount", "desc")`, композитный индекс объявлен в firestore.indexes.json
    (offer_bids: auctionId ASC, amount DESC, __name__ DESC), подделка научилась desc-порядку,
    тест «предел держит топ стола» закрепляет.
  - `[medium]` `[patch]` Перевод именованных отказов в коды провода (index.js) не был покрыт
    тестом: сломанный catch уезжал бы клиенту безымянной INTERNAL, и ни один сьют не падал бы
    (образец — патч 2.2 с тестом перевода premium-кодов) — исправлено: проводной тест в
    economy-handler.test.js: unauthenticated ×2, invalid-argument, failed-precondition
    «короткий кошелёк» на проводе, счастливая ставка через index.js и creative-free стол.
  - `[medium]` `[patch]` fetchOfferAuction без личности — та же дыра покрытия, что и выше;
    закрыта тем же проводным тестом (unauthenticated).
  - `[low]` `[reject]` Creative сохраняется непригаженным (trim проверяет, но не правит) —
    отклонено: косметика без ежедневного вреда; проверка формы ссылки/картинки при показе —
    поверхность 4.3, хранение «как прислали» честнее молчаливой правки.
  - `[false]` `codes[error.code]` в catch достаёт ключ по цепочке прототипа ("constructor") —
    опровергнуто: fail() ставит только четыре имени кода, ошибки Firestore несут имена вида
    "already-exists"/числа — ключей Object.prototype не возникает; идиома дословно соседняя
    (премиум-ветка applyShopPurchase).
  - `[false]` UNAVAILABLE после двойной гонки уходит безымянным — опровергнуто: второй проход
    create не повторяет (аукцион уже прочитан), хвост достижим лишь при дважды упавшем самом
    Firestore, где сырая ошибка честнее придуманной.
  - `[false]` Одна испорченная ставка ломает весь стол для всех — опровергнуто как дефект:
    громкий отказ на испорченном состоянии — поза дома (strictBoxState в box-service.js);
    единственный писатель — эта же валидирующая транзакция.

## Design Notes

- sourceId строк книги: списание — `{auctionId}_{uid}_r{raiseCount}` (r0 — первая ставка), возврат
  при повышении — тот же суффикс со сбрасываемым номером: refund r(n-1) уникален ровно один раз,
  повторная запись того же движения — переигранная транзакция, а не второе движение (язык
  currency-ledger.js).
- Открытый стол без creative: непроверенная картинка/ссылка на публичную поверхность не едет;
  uid+сумма+время — ровно то, что спек называет «открытый стол». Игрок не опознаётся ником —
  это решение витрины (будущая клиентская история), сервер отдаёт uid.
- Месяц — UTC-календарный: аукцион месяца M открыт весь месяц M-1, endsAtMs — начало M. Никаких
  катящихся суток: «месяц переворачивается» (4.2) — смена календарного месяца.

## Verification

**Commands:**
- `node functions/run-tests.js` -- expected: все сьюты зелёные (104/104 + новый сьют).
- `node functions/check-syntax.js` -- expected: чисто.
- `npm --prefix functions run lint` -- expected: чисто.

**Manual checks (no CLI):**
- Kotlin-поверхностей история не трогает; гейты gradle по затронутой поверхности не запускаются
  (серверная история, прецедент 3.1–3.3).

## Auto Run Result

Status: done
Blocking condition: none

**Summary:** ставка на аукционе предложений живёт на сервере: `placeOfferBid` списывает золото и
записывает ставку одной транзакцией (баланс абсолютом + строки книги движений OFFER_BID /
OFFER_BID_REFUND рядом); повышение своей ставки — возврат прежней суммы и списание новой той же
транзакцией, каждое движение книги со своим номером `r{n}`; повтор той же суммы отвечает
состоянием и не пишет ничего (NFR2). Аукцион — на следующий месяц, документ ключуется месяцем
цели (`ГГГГ-ММ`), первая ставка создаёт его сама (гонка — откат и повтор, guard 3.1). Открытый
стол ставок (`fetchOfferAuction`) показывает кто/сколько/когда по сумме, без creative; обе
коллекции закрыты правилами. Именованные отказы доходят до провода именованными.

**Files changed:**
- `functions/offer-auction.js` — новый модуль: календарный месяц UTC, чистый план ставки,
  транзакция, открытый стол (порядок по сумме, предел держит топ).
- `functions/currency-ledger.js` — поводы OFFER_BID / OFFER_BID_REFUND.
- `functions/index.js` — placeOfferBid (монетарный callable) и fetchOfferAuction; перевод кодов.
- `firestore.rules` — deny all для offer_auctions/offer_bids.
- `firestore.indexes.json` — композитный индекс открытого стола (auctionId, amount DESC).
- `functions/premium-test-db.js` — чтение ограниченных запросов вне транзакции + desc-порядок
  (совместимо: asc — прежнее голое имя поля).
- `functions/offer-auction.test.js` — 20 тестов: вся матрица, книга движений, год через декабрь,
  гонка первых ставок, предел стола.
- `functions/economy-handler.test.js` — проводной тест пары вызовов (коды + стол без creative).
- `functions/arena-lifecycle-views-handler.test.js` — пин поверхности переопределён с записью
  (4.1: два новых экспорта), по прецеденту 3.1.
- `functions/currency-ledger.test.js` — новые поводы в закрытом перечне.

**Review findings breakdown:** 3 патча (порядок открытого стола — orderBy amount DESC +
композитный индекс + тест предела; проводной тест перевода кодов для placeOfferBid и
fetchOfferAuction), 1 low отклонён (непригаженный creative — проверка показа лежит на 4.3),
3 опровергнутых с аргументами (codes-прототип, безымянный UNAVAILABLE на двойной гонке,
испорченная ставка ломает стол). followup_review_recommended: true — назван риск ниже.

**Verification performed:** `node functions/run-tests.js` — 105/105 сьютов зелёные (новый сьют
offer-auction: 20 тестов; economy-handler +1 проводной); `node functions/check-syntax.js` — 192
файла чисто; `npm --prefix functions run lint` — чисто. Матрица покрыта тестами (аудит: каждая
строка — сьют, все прошли).

**Residual risks:** композитный индекс offer_bids объявлен в firestore.indexes.json, но
фактически строится при деплое владельца — до готовности индекса fetchOfferAuction отвечает
ошибкой запроса (деплой кода и индекса идёт одной выкаткой; следить за завершением сборки
индекса в консоли). Клиентской поверхности нет — витрина аукциона остаётся будущей истории
(UX-контракт отложен спайном).

**Follow-up review risk:** композитный индекс (выше) — единственный названный непроверенный
риск.
