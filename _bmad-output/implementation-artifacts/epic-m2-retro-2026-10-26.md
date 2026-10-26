---
epic: 2
date: 2026-10-26
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Ретроспектива эпика 2 «Премиум-подписки» (монетизационный трек)

Трекер: `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml`, `project: schoolquiz3.0`, эпик 2. Спеки: `spec-m2-1-premium-is-one-subscription.md`, `spec-m2-2-premium-for-gold-and-from-a-box.md`, `spec-m2-3-renewal-not-replacement.md`, `spec-m2-4-premium-without-advantage.md`. Ретро headless (субагент параллельной волны), все допущения — в разделе Assumptions.

## Epic summary

- **Диапазон диффа:** `e86e4e6f^..02dd1134` — первый коммит истории `e86e4e6f` (2026-10-11 12:50, «monetisation(2.1): premium sells as one subscription»), последний `02dd1134` (2026-10-25, «monetisation(2.1): bad_spec closed»). В диапазоне 203 коммита, из них 15 атрибутированы историям по маркеру `monetisation(2.x)`; плюс `9de14051` (2026-10-12, «economy: golden premium pricelist», решение владельца, назван в трекере как часть эпика — атрибуцию по сабжекту не получил и в проверки историй не попал, см. находку F1 и процесс-урок P1). 2 merge в диапазоне — чужие (exam epic-6, 5.13 device-proof `ed16fc81`), оба измерены.
- **Истории:** 4 из 4 `done` (трекер, строки 88–156; `detect-epic --file sprint-status-monetisation.yaml --epic 2` → `pending_stories: []`, `story_count: 4`). Каждая закрыта четырёхлинзовым code review 2026-10-11; повторная приёмка 2026-10-23 по 2.1/2.2/2.4 (в 2.1 вернулся один `bad_spec`, закрыт `02dd1134` 2026-10-25); 2.3 повторного прохода не имела — по правилу first-pass (у неё 1 патч, `followup_review_recommended` в frontmatter не стоит) это согласовано, не пробел.
- **Инвентарь свидетельств:** 4 спеки с объявленными критериями приёмки (frozen-блоки) и журналами триажа (в 2.1 — два прохода: 25+20 находок, в 2.2 — 21, в 2.4 — 23); per-commit numstat всех 16 коммитов эпика; `git_evidence.py` (JSON: `/tmp/epic-m2-git-evidence.json` на VPS); rig-свидетельства `3c93ad5f`, `862de64f`; реестр defer'ов `deferred-work.md:538-546`; трекер (40 KB, прочитан целиком). Session logs эпика недоступны — процесс-анализ по ним не проводился, сужение зафиксировано.
- **Отсутствует / не проверяемо здесь:** живые покупки и продления (нужен GMS-девайс с sandbox-подпиской — redroid без GMS; defer зарегистрирован, `deferred-work.md:538-540`); живой RTDN-триггер в проде (противоречие в трекере, см. Open questions).
- **Churn по историям** (code-only, без docs/трекеров; из per-commit numstat): 2.1 +1458/−137 (41 файл), 2.2 +724/−111 (21), 2.3 +436/−38 (14), 2.4 +201/−21 (2 — только тест забора и .gitignore); `9de14051`: таблица +66/−3 и зеркала констант. Диапазонный `files` из git_evidence (829 путей) замусорен чужой работой — агрегаты считаны только по 16 коммитам эпика.

## Findings

### Aggregate views

**F1 (новая, fix now) — Золотой прейскурант владельца противоречит правилу «ровно одно предложение» истории 2.2: закоммиченная таблица не может продавать премиум за золото, и ни один тест этого не ловит.**
`functions/config/economy-constants.json` (HEAD, `version: 1`, коммит `9de14051`): все четыре SKU — `premium_1d/7d/30d/365d` — с `salesEnabled: true` и goldPrice 1/5/18/150. Правило 2.2 (spec-m2-2, Always, строки 76–78): «Exactly one gold-priced enabled row sells; zero or two sell nothing». `premiumGoldOffers` (`functions/economy-constants.js:296-304`) пропускает каждую включённую строку → из этой таблицы получается **4** предложения; `premiumGoldSaleOffer` (`functions/premium-shop.js:36-46`) требует ровно одно → `null` → отказ «Premium is not sold for gold»; клиент `GetShopCatalogUseCase.kt:62` берёт `singleOrNull()` из четырёх → `null` → строка живёт store-правилами 2.1. Т.е. если владелец создаст документ `configs/economy`, скопировав golden-таблицу как есть (естествальное чтение «записан прейскурант»), золотой путь останется мёртвым — fail-closed, молча. Тесты не страхуют: `premium-shop.test.js:18` fixture'ит таблицу с одним SKU; `economy-constants.test.js:185` пинит только непустоту `premiumProducts`; ни один тест не прогоняет закоммиченные дефолты через exactly-one правило. Находка вне обзоров историй: `9de14051` не имеет маркера истории, приёмки 23–25.10 верифицировали коммит-наборы `d59a0299+4aa0cb30` и `848a2989+3e263237` — конфиг владельца от 12.10 не вошёл ни в один. **Disposition: fix now** (action items A1, A2; процесс-урок P1). Сыгравшие роли — 2.1 (модель «одна подписка», 1d/7d/365d деактивированы в Play) и 2.2 (exactly-one) — ни одна сессия не видела обе стороны вместе с конфигом.

**F2 (defer) — `functions/index.js` продолжает расти как god-file, эпик добавил в него +236/−79.**
9193 строк на HEAD. 2.2: `d59a0299` +34/−20, `4aa0cb30` +65/−55 (карта перевода кодов applyShopPurchase), `d833cb24` +11/−3; 2.3: `d3591c6a` +26/−1 (роутинг playPurchaseNotifications). Денежная логика при этом вынесена в модули (`premium-shop.js` 93 строки, `purchase-settlement.js` 467, `purchase-verification.js` 940) — конвенция репо «тонкие callable-обвязки в index.js + чистые модули» соблюдена; рост — обвязка и карта переводов, у которых в index.js уже ~7 собратьев (строки 416, 470, 555, 708, …). **Disposition: defer** (свойство репо, не дефект эпика; каждое новое callable умножает паттерн).

**F3 (clean) — Architecture delta: нарушений слоёв нет.**
Эпик тронул ровно слои по конвенции AGENTS.md: `shared/feature/economy/{domain,data}`, `platform/billing`, `platform/firebase`, `android/feature/economy/presentation`, `functions/*`; Koin-проводка обновлена в истории (`IsolatedMvpBillingModule`, `KoinModuleWiringTest` в `e86e4e6f`); презентация ходит в use-case'ы (зажато компонентными тестами 2.1/2.2). Новых cross-cutting зависимостей и циклов в хунках эпика не видно.

**F4 (clean) — Pattern divergence: нет; эпик сходится к идиомам репо.**
Решения извлекаются в чистые функции с тестами по образцу `productsOrFailure`/`receiptText`: `storeRowExplanation`, `subscriptionStoreProduct` (`02dd1134`), `subscriptionTermDays`/`settlementGate` (`d3591c6a`), ядро `premiumSaleOffer` переиспользовано обеими обёртками (2.2, refutation-ряд «Duplicated offer derivation»).

**F5 (accept) — Дублирование: карта перевода кодов ошибок на каждый callable.**
Box-путь (эпик 1), shop-путь (2.2, `4aa0cb30` + карта дополнена `CONFLICTING_REPLAY`/`SOURCE_ALREADY_GRANTED` в `d833cb24`) и ~7 чужих callable'ов держат собственные reason→HttpsError-карты. Существующая конвенция; эпик добавил свои экземпляры, где важно — общее ядро (`premiumSaleOffer`) использовано. **Disposition: accept** (фоновое свойство, не возвращаться в ретро эпика).

**F6 (accept, задокументировано) — Расхождения спека↔код, уже разреcённые приёмками.**
2.1: I/O-матрица отстала от review-патчей (min-1-day-хвост `5f592ffa`, `PURCHASE_STATE_UNKNOWN`) — frozen-текст, задокументировано в triage (spec-m2-1:256-258, 277). 2.2: Code Map «to the shelf's front» против фактической третьей позиции (spec-m2-2:183). 2.4: AC «identical places» против запиненного tiebreak по имени (spec-m2-4:163). **Disposition: accept** — зафиксировано, чтобы будущие ретро не переоткрывали.

**F7 (accept) — Бейдж премиума в drawer читает синхронизированный булев.**
`DrawerHeader.kt:74` (`if (userStats.hasPremium)`) — проекция sync-границы; в `UserStats.kt:14` даты нет вовсе, пересчитать на рендере нельзя. Синк считает признак из даты (`hasPremiumAt`, `UserStatsPremiumSignTest`, `b5fa66f4`), между синками бейдж может отставать от истечения. Тот же класс «display echoes», что аудит 2.4 классифицировал как допустимый. **Disposition: accept** (косметика; опционально на будущее — донести `premiumUntilMs` до `UserStats`).

### Diff-scope review (bmad-review, линзы adversarial / edge-case / verification-gap)

Запуск inline, одним ревьюером, сужено до межисторийных швов (server: `purchase-settlement.js`, `purchase-verification.js`, `premium-shop.js`; client: `GetShopCatalogUseCase.kt`, billing, user-stats) — полные пер-исторические линзы уже записаны тремя проходами в спеках (11.10 и 23–25.10). Сужение зафиксировано.

- **Швы прослежены чисто:** роутинг endpoint'а по классу товара (`e86e4e6f`, guard нечитаемой таблицы `5f592ffa`); гейт продления `settlementGate(existing, uid, freshExpiry)` (`d3591c6a`) корректно разбирает same/later/unreadable/not-found относительно записанного срока, токен без Play-ответа отвечает повтором сохранённого; отказ терминален и не портит запись; гонка двойного RTDN сериализуется транзакцией, проигравший отвечает повтором (refutation-ряд 2.3); REPLAYED-ветка золотой покупки не списывает дважды (`4aa0cb30`); fail-closed на 2+ золотых офферах сходится на клиенте и сервере (и именно он ловит F1).
- **Edge-заметка (accept):** `settleSubscriptionRenewal` (`purchase-settlement.js:355-368`) не проверяет `productKind` записи перед перезапуском `settlePurchase` с записанным sku — при переклассификации SKU оператором решение безопасно откажет (роутинг и вердикт по текущей таблице, guard `5f592ffa`), сообщение acknowledged. Последствий сегодня нет (Play шлёт subscription-нотификации только по подпискам).
- **Verification-gap: чисто.** Путь обновления записи продления зажат (`premium-purchases.test.js:155-171`: `subscriptionRenewals === 1`, грант-ид на срок; `:333` — RTDN через запись); числовые и имя-формы состояний подписки зажаты обе (`5f592ffa`+`5463734d`); callable золотой покупки зажат на проводе (`economy-handler.test.js`, 8/8 — прогнан здесь, см. Behavior). Незакрытые поверхности — зарегистрированные defer'ы (wire-форма `subscriptionsv2` → GMS-девайс владельца, `deferred-work.md:538-540`; SDK-bound покупка → GMS).

### Процесс-наблюдения

**P1 (process lesson → action item A6):** приёмочные проходы верифицируют коммит-наборы историй; коммиты владельца без маркера истории (`9de14051`) выпадают из всех проверок. Эпик-уровень: финальный проход обязан перепрогонять выводимые серверные константы/резолверы офферов по полному change-set эпика, включая owner-decision конфиги.
**P2 (подтверждено, не находка):** повторная приёмка 23–25.10 не проходила 2.3 — согласовано с правилом first-pass (1 патч в первом проходе, `followup_review_recommended` в frontmatter 2.3 отсутствует, в отличие от 2.1/2.2/2.4).

## Behavior verification

**Эпик менял runtime-поведение; end-to-end этим ретро не прогонялся** — покупка/продление требуют GMS-девайса с sandbox-подпиской (redroid без GMS; зарегистрированный defer `deferred-work.md:538-540`), внешние действия в прод запрещены правилами среды. Что сделано вместо:

- **Прогон здесь (2026-10-26, HEAD, docker `node:22-bookworm-slim`, `--network none`, из корня репо):** `premium-neutrality.test.js` 6/6 (забор держит, включая скан `firestore.rules` — поэтому нужен маунт корня), `premium-shop.test.js` 7/7, `premium-economy.test.js` 15/15, `premium-grants.test.js` 29/29, `purchase-verification.test.js` OK, `purchase-settlement.test.js` OK, `purchase-notifications.test.js` OK, `economy-constants.test.js` OK, `economy-handler.test.js` 8/8 (включая «the shop callable sells premium for gold and names its refusals on the wire»). Итого серверные швы эпика зелёные на сегодняшнем HEAD.
- **Из записей эпика:** rig-смоук `862de64f` (2026-10-11 16:57, после патчей 2.x): собранный билд грузится, полка продаёт, премиум-строка объясняет недостижимый магазин; rig-свидетельства `3c93ad5f` (2.1) и `d3591c6a` (2.3, `docs/agents/evidence/2026-10-11-renewal-not-replacement/`). Kotlin-гейты и functions 128/129 на момент приёмок записаны в спеках (единственный красный — средовой `mvp-isolation`, не эпика).
- **Живые покупки/продления/RTDN:** не прогонялись никем — остаются за владельцем (A3, A4).

## Previous-retro follow-through

В `sprint-status-monetisation.yaml` ключа `action_items` нет (файл заканчивается на `epic-5-retrospective: optional`, строка 334), ретро по эпику 1 этого трека не проводилось (`epic-1-retrospective: optional`, документа нет) — **фоловить нечего: ни открытых экшн-айтемов, ни ретро-документа предыдущего эпика этого трека**. Ретро-документы `epic-*-retro-2026-09-*.md` принадлежат мёртвому сентябрьскому sync-треку (`sprint-status.yaml`) — вне области. Смежный пункт сентябрьского реестра `deferred-work.md:486-487` («липкий» булев `hasPremium` в `upsertUserProfile`) проверен: закрыт `c5895b65` от 2026-09-06 (`git log -S "Boolean(existingUser.hasPremium)"`), т.е. до эпика — эпику не засчитывается и не вменяется; нестиккость сегодня зажата `attempt-handler.test.js:575` («profile refresh clears sticky premium»).

## Action items

Прошедшие через Phase 4 fix-now находки и уроки. Владелец решает исполнение; в `sprint-status-monetisation.yaml` ничего не писалось (ограничение волны — сведёт отдельный агент, ключи ниже для переноса).

- **A1 (владелец; fix now, разблокирует игрокам путь 2.2):** привести golden-таблицу к правилу «ровно одно»: `salesEnabled: true` только у `premium_30d` (1d/7d/365d — `false`, они деактивированы решением по модели владельца), поправить `functions/config/economy-constants.json`; затем создать документ `configs/economy` с версией ≥2 (нота трекера, строки 82–87). Без A1 документ, созданный копированием таблицы, оставит золотую продажу мёртвой (F1).
- **A2 (dev, малый; fix now):** зажать shipped-дефолты через exactly-one правило — тест, выводящий `premiumGoldOffers(clientEconomyConstants(DEFAULTS))` и прогоняющий `premiumGoldSaleOffer` по закоммиченному конфигу (сегодня `premium-shop.test.js:18` fixture'ит один SKU, `economy-constants.test.js:185` пинит только непустоту). Закрывает возврат ловушки F1.
- **A3 (владелец; tracked defer):** GMS-девайс: sandbox-покупка + одно продление + снимок живого ответа `subscriptionsv2` в фикстуру (`deferred-work.md:538-540`, rig-нота спеки 2.1).
- **A4 (владелец/ops; fix now — сверка):** подтвердить, что RTDN-триггер `play-rtdn → playPurchaseNotifications` жив в проде: трекер 1-3 (11.10) — «деплой триггера в прод — по явному go владельца», трекер epic-5 (12.10) — «монетизационный трек деплоя закрыт, 86 функций в проде». Без триггера продления доезжают только при перепредъявлении токена клиентом.
- **A5 (dev; tracked defers, остаются открытыми):** fence v2 (инвертированный allow-list, пин сигнатуры, keyDocument-фикстура — frontmatter spec-m2-4), тест проводки маппера user-stats (`deferred-work.md:542-543`), аналитика воронки balance-пути (frontmatter spec-m2-2, medium), золотая цена на store-backed строках (frontmatter spec-m2-1).
- **A6 (процесс, владелец ретро-конвейера):** эпик-приёмка включает в верифицированный change-set некоммит-исторические коммиты (owner-decision конфиги) — урок P1/F1.

## Acceptance verdict

**accepted-with-open-items** (criteria: declared). Основания: все 4 истории `done`, `pending_stories` пуст (`detect-epic --file sprint-status-monetisation.yaml --epic 2`); критерии каждой истории объявлены в frozen-блоках спек и подтверждены свидетельствами — 2.1: матрица расчёта подписки зажата тестами, acknowledge-без-consume зажат, деактивированные SKU отвечают `SKU_NOT_SOLD`, безценная подписка не роняет полку (`02dd1134`, закрытый bad_spec); 2.2: золотой путь кодом и тестами полон (callable 8/8 на проводе), коробочный путь подтверждён существовавшими сьютами; 2.3: закон движения `max(сейчас, окончание)+длительность` зажат (premium-economy 15/15), гейт сроков и RTDN-семья зажаты, клиентский признак — из даты; 2.4: забор зелёный на сегодняшнем HEAD (6/6 здесь) и mutation-доказан по записи. Ни одного открытого блокирующего дефекта в диффе эпика. Open items — операторские зависимости и отложенные поверхности: живая wire-форма и живые покупки (A3), живой RTDN-триггер (A4), рабочий золотой прейскурант в `configs/economy` (A1/A2 — без них критерий 2.2 остаётся подтверждённым тестами, но недостижимым игроком). Не `accepted` из-за именно этих открытых операторских поверхностей; не `rejected` — незавершённых историй нет, критерии в коде подтверждены.

## Open questions

1. Живёт ли RTDN-триггер в проде (A4)? Ответ владельца меняет гарантию доставки продлений: нотификационный путь vs перепредъявление клиентом.
2. Кто сеет документ `configs/economy` — владелец в консоли или агент по явному go (внеший деплой данных)?
3. Осталась ли намеренной семантика «бейдж drawer — проекция синка» (F7/A5-класс), или донести дату до `UserStats`?

## Assumptions (headless)

- Эпик выбран вызовом (2 монетизационного трека); `sprint_status.py detect-epic --file …sprint-status-monetisation.yaml --epic 2` → `pending_stories: []`, `retro_status: optional`.
- Ретро-документ назван `epic-m2-retro-2026-10-26.md` по указанию волны (вместо дефолтного `epic-2-retro-{date}.md`), чтобы не пересечься с соседними ретро волны.
- **Sprint-status update не выполнялся**: ограничение волны запрещает редактировать трекеры (сведёт отдельный агент). `epic-2-retrospective` остался `optional`; экшн-айтемы A1–A6 перечислены здесь и в openItems результата для переноса. Вердикт читается из frontmatter этого документа.
- `git_evidence.py` запущен на VPS (`/root/.zcode/skills/bmad-retrospective/scripts/`), т.к. git-команды по правилам среды — только через ssh; диапазон `e86e4e6f^..02dd1134`.
- bmad-review прогнан inline одним ревьюером по суженной области (межисторийные швы) — субагентов в сессии нет; прецедент репо «в-сессии, платформа без субагентов». Per-исторические линзы не дублировались — три прохода уже записаны в спеках.
- Поведенческая проверка сужена до серверных сьют на HEAD (docker node:22, network none); клиент/девайс не прогонялись — GMS-зависимость, владелец.
- Session logs эпика недоступны — процесс-уроки выведены из трекера, спек и git-истории.
- Числа churn посчитаны per-commit numstat по 16 коммитам эпика (15 story + `9de14051`); диапазонный агрегат git_evidence не использовался из-за чужих коммитов в диапазоне.
