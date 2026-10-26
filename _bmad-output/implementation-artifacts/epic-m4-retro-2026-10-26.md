---
epic: 4
date: 2026-10-26
verdict: accepted-with-open-items
criteria: declared
headless: true
track: monetisation
sprint_status_file: _bmad-output/implementation-artifacts/sprint-status-monetisation.yaml
---

# Ретроспектива эпика 4 «Аукцион объявлений» — трек монетизации (epic-m4)

Ретро проведено 2026-10-26 по скиллу bmad-retrospective, headless, по трекеру
`_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` (project: schoolquiz3.0,
epic-4). Не путать с сентябрьским `epic-4-retro-2026-09-02.md` (мёртвый sync-трек) и с ретро
эпика 7 commerce от 25.10 — это другие эпики других треков.

## Epic summary

- **Эпик:** монетизация, epic-4 «Аукцион объявлений» — три серверные истории: 4.1 ставка
  списывает золото сразу (`placeOfferBid`), 4.2 месяц переворачивается — место не пустует
  (`rotateOfferAuctions` onSchedule), 4.3 объявление проходит проверку до показа
  (`decideOfferAdReview`). Спеки: spec-m4-1 / spec-m4-2 / spec-m4-3 в той же папке, все со
  статусом `done` и четырёхлинзовым review-логом от 2026-10-12.
- **Диапазон диффа:** `21761dea^..a1957ab3` — линейная цепочка без мержей:
  `7de873f2 → 21761dea (4.1) → ec069047 (4.2) → a1957ab3 (4.3)`; бейзлайны из frontmatter спек
  (`7de873f2`, `21761dea`, `ec069047`) совпадают с родителями коммитов историй (проверено
  `git log --format="%h %p %s"`). Диапазон включает первую историю (левая граница — родитель
  первого коммита).
- **Завершённость:** `sprint_status.py detect-epic --file …sprint-status-monetisation.yaml
  --epic 4` → `{"epic": 4, "story_count": 3, "pending_stories": [], "retro_status": "optional"}`.
  Все три истории `done`; незавершённых историй нет — машинный вердикт не принуждается к
  rejected.
- **Объём (git_evidence.py, 3 коммита, merges 0):** продакшн — offer-auction.js +284,
  offer-auction-settlement.js +337/−7, offer-ad-review.js +222, index.js +105, currency-ledger.js
  +10, firestore.rules +17, firestore.indexes.json +18, premium-test-db.js +27/−9; тесты —
  offer-auction.test.js +290, offer-auction-settlement.test.js +274, offer-ad-review.test.js
  +174, economy-handler.test.js +58, arena-lifecycle-views-handler.test.js +13/−6,
  currency-ledger.test.js +2; спеки/трекер/контекст — spec-m4-1..3 (+236/+217/+249),
  epic-4-context-monetisation.md +71, sprint-status-monetisation.yaml +50/−7. Каждая строка
  `files` — по не-мерж коммитам; мержей нет, `merge_files` пуст.
- **Инвентарь доказательств:** спеки с замороженным интентом, матрицами, AC и review-логами —
  есть; трекер — есть; полный дифф — есть (прочитан целиком, 4 части); гейт тестов — прогнан на
  VPS (см. Behavior verification); сессионные логи историй — **отсутствуют** (анализ процесса по
  логам пропущен, это сужение); гейт на точном бейзлайне эпика не перезапускался (рабочее дерево
  VPS грязное чужой работой, checkout не делался) — сужение зафиксировано, взяты показания на
  HEAD и записи спек о прогоне на момент закрытия (105→106→107/107).
- **Хвосты эпика, уже записанные в трекере/спеках (не дублирую находками, переношу списком):**
  операторская очередь проверки объявлений (defer владельца, frontmatter spec-m4-3), построение
  композитных индексов offer_bids при деплое (4.1/4.2), клиентская витрина аукциона — отдельная
  история (UX-контракт отложен), деплой в Firebase — действие владельца.

## Findings

Источники: полный дифф `21761dea^..a1957ab3` (прочитан целиком), текущие файлы на HEAD
(номера строк проверены `grep -n`/`awk` на VPS), спеки, review-логи в спеках. Линзы bmad-review
(adversarial, edge-case-hunter, verification-gap) исполнены в сессии последовательно — субагентов
платформа не дала (то же записанное отклонение, что в 3.1–4.3); фокус — границы историй, которых
ни одна сессия не видела с обеих сторон. Adversarial-линза требует ≥10 находок или повторной
проверки; повторная проверка выполнена, честный урожай — 8 обоснованных находок, добивать до
десятки выдумками не буду. Находки ин-сессионного ревью (7 патчей, 5 отклонений, 9 опровержений
по трём спекам) сюда не переносятся как новые — они уже в Review Triage Log спек; ниже только
то, что видно поверх них.

### Aggregate views

1. **Architecture delta — чисто.** Три новых модуля с однонаправленными зависимостями:
   `offer-auction.js` (деньги ставки) ← `offer-auction-settlement.js` (закрытие месяца,
   импортирует константы статусов) ← `offer-ad-review.js` (проверка, импортирует статусы ставок
   и размещений); все трое ← `currency-ledger.js` (поводы OFFER_BID/OFFER_BID_REFUND,
   functions/currency-ledger.js:108-115 в диффе); `index.js` — только тонкая обвязка четырёх
   экспортов (functions/index.js:4675-4708). Циклов нет, кросс-срезовых зависимостей нет,
   Kotlin не тронут. Три новые коллекции закрыты `allow read, write: if false`
   (firestore.rules, блоки /offer_auctions/ /offer_bids/ /offers/), два композитных индекса
   объявлены (firestore.indexes.json: offer_bids auctionId+amount DESC, auctionId+status).
   Покрытие запросов индексами сверено: запрос открытого стола и победителя (auctionId ==,
   orderBy amount desc) — первый индекс; порции возврата и продвижение — auctionId+status или
   равенства. Диспозиция: accept (эталонный срез).
2. **Duplication map — одна внутрилепиковая копия.** `storedGold` написана дважды внутри эпика:
   functions/offer-auction.js:66 (4.1) и functions/offer-auction-settlement.js:61 (4.2), притом
   что settlement уже импортирует из offer-auction.js константы — второй копии не требовалось.
   Находки «fail()/exactNonNegative дублируются» — НЕ дивергенция: per-module `fail` — дом
   конвенция (grep: десятки модулей), `exactNonNegative` в 5 модулях, из них
   ad-reward-progress.js:56 и economy-season.js:84 — до эпика. Диспозиция: fix-now (однострочный
   импорт), риск — дрейф двух определений.
3. **God-class / рост размера — тренд index.js.** Эпик добавил в functions/index.js +105 строк
   (обвязка четырёх экспортов) в файл, который и до эпика — точка роста проводки (тысячи строк).
   По конвенции дома логика в модулях, index.js — обвязка; эпик конвенцию держит (обвязка без
   логики), но тренд продолжается. Модули эпика компактны (222–337 строк). Диспозиция: accept
   как наблюдение; процесс-урок (постоянный рост index.js — решение владельца: вводить
   feature-точки проводки) — в action items как процессный, не как дефект эпика.
4. **Pattern divergence — гейт модератора.** Гейт `decideOfferAdReview` записан инлайн-отрицанием
   `if (profile.moderatorLevel < QUALIFIED_LEVEL && profile.developerLevel <=
   DEVELOPER_ALL_ACCESS_LEVEL) throw …` (functions/index.js:4679), тогда как установленный дом
   образец — именованная форма `const isModerator = …; const isDeveloper = …;
   if (!isModerator && !isDeveloper) throw …` (functions/index.js:5235, 5383; конвенция как
   минимум с a681181a от 2026-09-01). Семантика идентична (пускает mod ≥ 100 OR dev > 100;
   константы index.js:268-269). Диспозиция: fix-now (косметическая нормализация к именованной
   форме).
5. **Spec-to-implementation — сверка сходится, два принятых отклонения зафиксировать.**
   - AC 4.1 «видны uid, сумма и время **каждой** ставки» против `OFFER_BID_TABLE_LIMIT = 200`
     (functions/offer-auction.js:48): хвост месяца длиннее 200 ставок на стол не доезжает.
     Отклонение осознанное и задокументировано в коде («стол — верхушка, а не архив») и в патче
     ревью 4.1 (порядок держит топ). Диспозиция: accept as-is — записать, чтобы будущие ретро
     не переоткрывали; при появлении витрины решить, нужен ли курсор.
   - 4.2/4.3: ставка-победитель, заранее одобренная модератором до закрытия месяца, всё равно
     ложится кандидатом со статусом PENDING (settlement пишет кандидата безусловно — дифф
     offer-auction-settlement.js, ветка `if (winner)`) и проходит одобрение дважды. Это ровно
     frozen-спека 4.3 (матрица: «Месяц перевернулся → PENDING»), не дефект; наблюдение для
     операторской поверхности. Диспозиция: accept, решение о сквозном пропуске APPROVED — при
     появлении очереди (см. open questions).
   - Остальная сверка расхождений не нашла: Moderatore gate, поводы книги, deny-all правил,
     индексы, статусы ставок (BID/WON/REFUNDED) и проверки (PENDING/APPROVED/REJECTED) — код =
     спекам.

### Diff-scope review (линзы)

6. **[edge-case/adversarial] Возврат «просроченного кандидата» не закрывает его reviewStatus**
   — граница 4.2↔4.3. Патч ревью 4.3 («непросмотренный кандидат прошлого месяца возвращается
   следующим закрытием») пишет в ставку только `{status: REFUNDED, refundedAtMs, updatedAtMs}`
   (functions/offer-auction-settlement.js:191-205, ветка `stale && !stale.refunded`), поле
   `reviewStatus` остаётся `PENDING`. Гейт решения пускает ставку в работу
   (`reviewStatus === PENDING` — functions/offer-ad-review.js:83): модератор может «решить»
   уже возвращённую ставку — REJECTED пройдёт как guard-нон-оп (ножка возврата отсечена
   статусом денег), APPROVED пометит возвращённую ставку APPROVED (размещения нет — документ
   pending уже удалён; деньги не двигаются). Денег не теряется — это гигиена статусов: ставка
   навсегда выглядит «ждущей проверки» и попадёт призраком в будущую операторскую очередь.
   Триггер: месяц с неодобренным кандидатом, затем его возврат следующим закрытием. Guard:
   в ветке stale-возврата флипать и `reviewStatus: REJECTED` (+ тест). Диспозиция: fix-now
   (low).
7. **[adversarial] UNAVAILABLE двойной гонки уезжает безымянным** — functions/offer-auction.js:231
   `fail("UNAVAILABLE", "Offer bid did not settle; retry")`, а карта перевода проводки
   (functions/index.js:4617) знает только INVALID_ARGUMENT / FAILED_PRECONDITION / INTERNAL —
   клиент получит generic INTERNAL. Ин-сессионное ревью 4.1 опровергло это как дефект
   («достижимо лишь при дважды упавшем самом Firestore, сырая ошибка честнее придуманной»).
   Диспозиция: accept as-is — записано, чтобы будущие ретро не переоткрывали.
8. **[verification-gap] Проводной тест перевода NOT_FOUND/FAILED_PRECONDITION для
   decideOfferAdReview отсутствует** — economy-handler.test.js:162-163 закрывает
   unauthenticated ×2 и permission-denied, `NOT_FOUND` покрыт только на уровне модуля
   (offer-ad-review.test.js, «unknown bid … refuse named»). Убери `NOT_FOUND: "not-found"` из
   карты (functions/index.js:4693) — ни один сьют не упадёт, модератор получит безымянный
   INTERNAL на несуществующую ставку. Это ровно дыра, которую патч 4.1 закрыл для
   placeOfferBid/fetchOfferAuction (проводной тест перевода кодов) — для пары 4.3 её не
   завели. Проверено: сьюты offer-ad-review/economy-handler прочитаны; Symbolexport-поиск по
   NOT_FOUND в тестах проводки других покрытий не дал. Guard: два assert.rejects на проводе
   (`bidId: "nobody"` → not-found; повтор решения → failed-precondition). Диспозиция:
   fix-now (small test).
9. **[verification-gap, minor] fetchOffersScreen без непрогнанного unauthenticated-пути** —
   обвязка functions/index.js:4706-4708 (`requireAuthUid` + pass-through) не покрыта проводным
   тестом, тогда как соседняя fetchOfferAuction покрыта (economy-handler.test.js:167).
   Реалистичная регрессия мала (чтение публичной по сути витрины неаутентифицированным), но
   парность дешёвая. Диспозиция: defer (добрать в том же проводном тесте из п.8).

### Что искал и не нашёл (checked and clean)

- Сцепка статусов через истории (главный риск эпика): `status: "BID"` записи 4.1 и
  `reviewStatus: "PENDING"` зажаты утверждениями в тестах первой ставки и повышения
  (offer-auction.test.js: «статус ставки — ключ возврата settlement'а 4.2…», «статус проверки —
  гейт показа 4.3…») — патчи ревью 4.2/4.3 на месте.
- Все чтения до первой записи в трёх транзакциях (closeOfferAuction, refundOfferBatches,
  decideOfferAdReview) — сверено по диффу; ключи повтора (статусные флипы в транзакции
  начисления) на месте.
- Валидация creative: ставка валидируется при записи (offer-auction.js creative(), лимит 2048),
  кандидат валидируется до одобрения (offer-ad-review.js, цикл по uid/imageUrl/link/auctionId),
  испорченные кандидаты продвижения пропускаются, а не роняют решение; непроверенный creative
  не покидает сервер (offersScreenView отдаёт только APPROVED current; проводной тест «кандидат
  не на экране» — economy-handler.test.js:170-172).
- Возвраты: 25-ставочный тест порций (10+10+4 + пустой проход + SETTLED — по транзакции на
  порцию), довозврат после падения, чужой месяц, ставки следующего месяца не трогаются.
- Дубли экспортов в index.js — нет (grep: ровно один `exports.decideOfferAdReview`).
- ledgerEntry возвращает null только для нулевых/неопределимых движений
  (functions/currency-ledger.js:164-171) — `if (row)` в модулях эпика — паттерн дома («пустые
  отброшены»), не дыра книги.

## Behavior verification

Прогнано на VPS (ssh rnsrv), рабочее дерево HEAD `bd66776d`, functions-код дерева чист от чужих
правок (грязны только spec-e7-файл и два не-файловых артефакта волны):

- `node functions/run-tests.js` (node v22.16.0 из /root/.zcode/server/node): **128 из 129
  сьютов зелёные**. Единственное падение — `mvp-isolation.test.js` (2/3): пин
  `src/mvp/res/xml/mvp_network_security_config.xml` ожидает единственный внешний домен
  `204.44.66.27`, в дереве `192.168.31.218` (LAN-адрес более поздней HITL-риг-работы, коммиты
  32b159ad/6f96bd36/f4750e8b от 12.10 — после историй эпика). Эпик 4 Android не трогал —
  падение вне поверхности эпика; фикс пина — не действие этого ретро.
- Сьюты эпика поштучно на HEAD: `offer-auction.test.js` — 20 passed;
  `offer-auction-settlement.test.js` — 17 passed; `offer-ad-review.test.js` — 10 passed;
  `economy-handler.test.js` — зелёный (в составе 128).
- Production-косвенные показания (из трекера, epic-5 комментарий): деплой 12.10 владельцем —
  86 функций в проде, 7/7 onSchedule-джоб ENABLED, «10 мин: аукционы» — проводка
  rotateOfferAuctions подтверждена в проде после эпика. Живой переворот месяца и живая ставка —
  не упражнялись (боевые операции запрещены правилами среды; деплой-хвосты — go владельца).
- На точном бейзлайне эпика гейт не перезапускался (грязное общее дерево — checkout не делался);
  показания закрытия эпика взяты из спек: 105/105 (4.1) → 106/106 (4.2) → 107/107 (4.3),
  lint/syntax чисто.

## Previous-retro follow-through

В треке монетизации предыдущего ретро нет: ключи `epic-0…epic-3-retrospective` в
sprint-status-monetisation.yaml — все `optional`, раздела `action_items` в файле нет (файл
прочитан целиком). Сентябрьские ретро (epic-4-retro-2026-09-02.md и соседние) — другой, мёртвый
трек; ретро эпика 7 commerce от 25.10 — другой эпик. Итого: продолжать по action items
предыдущего ретро нечего — отсутствие записано, чтобы его не путали с «проверено, чисто».

## Action items

Все — **предложение** (этап Phase 4), не применено: правки трекеров этой волной запрещены,
кодовые фиксы — в обычный dev-цикл. Владелец/сводящий агент переносит в трекер.

| # | Что | Кто | Источник |
|---|-----|-----|----------|
| AI-1 | В stale-ветке возврата кандидата флипать и `reviewStatus: REJECTED` (+тест), чтобы возвращённая ставка не висела «ждущей проверки» | dev-цикл (bmad-build) по go владельца | offer-auction-settlement.js:191-205; offer-ad-review.js:83 |
| AI-2 | Проводной тест перевода NOT_FOUND/FAILED_PRECONDITION для decideOfferAdReview (по образцу патча 4.1); заодно закрыть unauthenticated fetchOffersScreen | dev-цикл | economy-handler.test.js:162-175; index.js:4693, 4706-4708 |
| AI-3 | Нормализовать гейт decideOfferAdReview к именованной форме isModerator/isDeveloper | dev-цикл (косметика) | index.js:4679 vs 5235/5383 |
| AI-4 | Убрать вторую копию storedGold из settlement — импортировать из offer-auction.js | dev-цикл (косметика) | offer-auction.js:66; offer-auction-settlement.js:61 |
| AI-5 | Владельцу: подтвердить, что деплой 12.10 выкатил firestore.indexes.json (два композитных индекса offer_bids) и firestore.rules (три deny-блока) — деплой-коммиты 74161850/77042284 говорят только про functions; без индексов fetchOfferAuction и порции возврата отвечают ошибкой запроса | владелец | трекер epic-4 комментарий; spec-m4-1/m4-2 Residual risks |
| AI-6 | Владельцу: операторская очередь проверки объявлений (defer 4.3) — до неё candidate-флоу живёт на возвратах; решить там же судьбу двойного одобрения заранее APPROVED ставки | владелец | spec-m4-3 frontmatter deferred; находка 5 (п.2) |
| AI-7 | Клиентская витрина аукциона (fetchOfferAuction/fetchOffersScreen) — завести клиентскую историю, когда появится UX-контракт (уже записано в трекере — перенести в исполнение) | владелец/планирование | трекер epic-4 комментарий; spec-m4-1 Design Notes |
| AI-8 | Процессное: тренд роста index.js (+105 строк за эпик поверх тысяч) — решить, вводить ли feature-точки проводки, пока обвязка остаётся тонкой | владелец | находка 3 |

## Acceptance verdict

**accepted-with-open-items** (критерии declared — AC каждой истории из спек, не profiled).

- Все три истории `done`; `pending_stories` пуст (detect-epic, script-backed) — rejected-принуждение не сработает.
- AC закрыты evidенцией: матрицы 4.1/4.2/4.3 покрыты сьютами (аудит соответствия в спеках;
  повторно подтверждено чтением тестов), 7 патчей четырёхлинзового ревью применены и видны в
  диффе, гейт на закрытие эпика 107/107 (запись спеки), сьюты эпика зелёные на HEAD
  (20+17+10+проводка, behavior check этого ретро).
- Открытые позиции не блокируют критерии: defer владельца (очередь проверки, индексы/правила в
  проде, витрина) + два fix-now low (AI-1, AI-2) + косметика (AI-3, AI-4) — все перечислены и
  трекаются herein.

## Open questions

- Порядок AI-1/AI-2: правки в прод-функции требуют нового деплоя — владельцу решить, ждать ли
  их до первого живого цикла аукциона (первый переворот месяца после деплоя — первый же
  1-й день месяца) или класть в следующий регулярный выкат.
- Двойное одобрение заранее APPROVED победителя (п.5): оставить как есть (frozen-спека) или
  пропускать APPROVED-ставки в candidate без повторного решения — решается только вместе с
  операторской поверхностью.

## Assumptions

Headless-прогон; всё, что решено без человека:

1. **Выбор эпика/трекера:** инвокация задала «монетизация, эпик 4»; workflow по умолчанию читает
   `sprint-status.yaml` — ретро вело `--file sprint-status-monetisation.yaml` (увязано с
   sprint_status_file в frontmatter спек m4-*). detect-epic: story_count 3, pending_stories
   пусто, retro_key `epic-4-retrospective` был `optional`.
2. **Stories-vs-sprint режим:** трекер существует → sprint mode; бейзлайны stories-формата из
   frontmatter спек использованы только для проверки родословной коммитов.
3. **Машинный вердикт** — accepted-with-open-items, выставлен без человека (незавершённых
   историй нет, критерии declared закрыты evidенцией).
4. **Все action items AI-1…AI-8** — предложения; ни один не применён ни в коде, ни в трекере.
5. **Phase 5 update трекера НЕ исполнен** (обычный шаг скилла — `sprint_status.py update
   --set-retro-done --add-action …`): волновые ограничения запрещают редактировать
   sprint-status-monetisation.yaml и tickets.toml. Сводящему агенту: отметить
   `epic-4-retrospective: done` и перенести AI-1…AI-8 в `action_items` с ref на этот документ.
6. **Ретро-документ** записан под именем `epic-m4-retro-2026-10-26.md` (вместо дефолтного
   `epic-4-retro-{date}.md`), чтобы не пересечься с сентябрьским epic-4-ретро другого трека и
   соседними ретро волны.
7. **Сессионные логи историй отсутствовали** — процесс-уроки по логам не строились; сужение
   зафиксировано в Epic summary.
8. **bmad-review исполнен в сессии** (последовательно, линза за линзой) — платформа без
   субагентов; сужение записано.
9. **Team discussion (Phase 3)** — opt-in, в headless не открывается никогда: пропущен.
