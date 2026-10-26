---
epic: 3
track: monetisation
date: 2026-10-26
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Ретроспектива эпика 3 (монетизация) — «Реклама и коробки»

Ретро исполнено субагентом волны 2026-10-26 по скиллу bmad-retrospective (headless).
Sprint-status и tickets.toml волной запрещено менять — все action items живут здесь и в openItems
результата; сведение трекера — за отдельным агентом после волны. Ключ `epic-3-retrospective`
в sprint-status-monetisation.yaml остаётся `optional` — ретро-запись в трекер не писана (см. Assumptions).

## Epic summary

- **Эпик:** 3 монетизационного трека (project: schoolquiz3.0, файл sprint-status-monetisation.yaml).
  Сентябрьский sprint-status.yaml, mvp-candidate и commerce-эпик 7 — не цель этого ретро.
- **Полнота:** `uv run sprint_status.py detect-epic --epic 3` по sprint-status-monetisation.yaml →
  `story_count: 4`, `pending_stories: []`, `retro_status: optional`. Все четыре истории `done`
  (трекер, строки 159–207). Гейт полноты пройден.
- **Диапазон диффа:** `814d72c3..7de873f2` — левый конец = baseline_commit спеки 3.1
  (spec-m3-1-network-confirms-the-reward.md:13, прекоммитный), правый = последний коммит эпика
  `7de873f2` (ревизия 3.2 по решению владельца 12.10). 5 коммитов, 0 мержей (git_evidence.py):
  - `b7b908a0` 10-11 23:06 — 3.1 сеть подтверждает награду (SSV, ECDSA P-256)
  - `ff5601a5` 10-11 23:18 — 3.2 пять реклам — одна коробка, потолок в транзакции
  - `22f5077c` 10-11 23:35 — 3.3 купленная и заработанная коробки — один дроп
  - `44289bed` 10-11 23:48 — 3.4 реклама не входит в урок (два дома + врата AdsGate)
  - `7de873f2` 10-12 06:27 — ревизия 3.2: бонус и реклама — один магазинный слот (решение владельца)
- **Объём** (git_evidence.py, только не-мерж-коммиты): ~1 330 добавленных / ~40 удалённых строк кода
  и тестов + 4 спеки (~935 строк) + контекст эпика + трекер. Основное: ad-reward-verification.js
  (+255), ad-reward-progress.js (+232), их тесты (+504), index.js (+68), purchase-settlement.js
  (+45), purchase-verification.js (+56/−4), economy-constants.js (+23), ads:domain Kotlin
  (~150 строк с тестами), gift-boxes.js (+20), box-service.js (+4/−2), firestore.rules (+13).
- **Инвентарь доказательств:** 4 спеки с frozen-интентами, матрицами, triage-логами и Auto Run
  Result — прочитаны полностью; epic-3-context-monetisation.md — прочитан; трекер — прочитан;
  дифф 2169 строк — прочитан полностью; git-лог и измерения — исполнены на VPS.
- **Чего нет:** сессионных логов историй 3.1–3.4 (процессный след — только Implementation Notes и
  Review Triage Log в спеках и комментарии трекера); владелец-де́йствия (консоль AdMob, SSV URL,
  SKU коробки в Play Console, живой SDK) не исполнены — аккаунт сети закрыт с 01.09 (трекер, ст. 159–163).

## Findings

Линзы bmad-review (adversarial, edge-case-hunter, verification-gap) исполнены **inline, в одном
исполнителе** — платформа волны не дала субагента-ревизора; сужение записано. Editorial-линзы к
код-диффу неприменимы. Каждая находка имеет источник; отвергнутые кандидаты (BOX-replay,
«просмотры сгорают», свежесть timestamp, порядок параметров подписи и др.) сняты проверкой кода
и не внесены.

### A. Агрегатные взгляды (через весь диапазон)

- **A1. Архитектурная дельта — чистая, аддитивная.** Новый модуль `shared:feature:ads:domain`
  без импортов и без потребителей (включён в settings.gradle.kts одной строкой; пассивность врат —
  зафиксированное решение, спека 3.4 Design Notes + triage «reject по намерению»); серверные
  модули повторяют дом-образец «чистое решение → денежный путь с внедрённым db → тонкая обвязка»
  (ad-reward-verification.js / ad-reward-progress.js / index.js:5012); обе новые коллекции закрыты
  правилами и прикрыты исходник-тестом (ad-reward-progress.test.js, «firestore rules close both…»).
  Нарушений слоёв и циклов дифф не показывает. **Disposition: accept.**
- **A2. Дублирование/сцепка — инвариант «один магазинный слот» живёт в двух писателях.**
  Решение владельца 12.10 сделало `nextBonusBoxAtMs` и `nextAdRewardBoxAtMs` одним слотом, но
  правило «занял сутки — двигай оба поля» не выделено в код: каждый из двух писателей двигает пару
  полей сам — ad-reward-progress.js:142 (выдача за просмотры двигает BONUS) и box-service.js:153
  (кнопка PREMIUM_DAILY двигает AD). Оба направления прикрыты тестами (ad-reward-progress.test.js
  «the fifth confirmed view…»; box-service.test.js «the premium button and the ad share the one
  shop box…»), но ни один тест не называет сам инвариант (равенство обоих часов после любого из
  писателей) — третья нога или переименование поля сломает сцепку молча. **Disposition: defer →
  AI-1** (хелпер в gift-boxes.js рядом с константами полей либо инвариант-тест).
- **A3. God-class/рост — index.js копится, эпик добавил мало.** index.js сегодня 9 193 строки
  (`wc -l`), эпик добавил +68 (git_evidence: files[].net) — endpoint, fetcher ключей, кэш; обвязка
  тонкая по дом-конвенции, поверхность заперта пином ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256
  (re-pin с записью в arena-lifecycle-views-handler.test.js, прецедент 2.13/4.9/8.2). Рост файла —
  довекторная структура, не дефект эпика. **Disposition: accept (распил index.js — отдельное
  решение владельца, вне эпика).**
- **A4. Паттерн-дивергенция — форма «громкого отказа» на испорченном boxCount расходится.**
  ad-путь бросает кодированный `INVALID_ARGUMENT` (ad-reward-progress.js:137, fail()),
  покупочный — голый Error без кода (purchase-settlement.js:386, storedBoxCount: «stored boxCount
  is corrupt; repair required»). Оба громкие и оба прикрыты тестами; но один и тот же класс порчи
  поля в двух писателях одного счётчика сообщает о себе по-разному. **Disposition: defer → AI-2.**
  Вторая мелочь: в самом эпике два стиля тест-раннера (progress/verification — самопрокрутка
  TESTS+main, handler — node:test); оба работают под run-tests.js, стилистика исторически смешана.
  **Disposition: accept.**
- **A5. Сверка спека↔код — текст замороженных секций отстал от постройки в двух местах.**
  (а) Спека 3.2 Code Map объявляет `planAdRewardBox({views, boxCount, nextAdBoxAtMs, nowMs})`,
  реализация не принимает `boxCount` (ad-reward-progress.js:70) — порча счётчика проверяется в
  транзакции (там же, :137). (б) Спека 3.1 не упоминает введённый кодом потолок query в 8 192
  байта (ad-reward-verification.js, readAdRewardCallback). Поведение корректно и прикрыто
  тестами; расходится литература. **Disposition: spec reconciliation → AI-4** (человек вносит
  через Spec Change Log; замороженные секции сам не правлю).

### B. Линзовые находки по диффу (пережившие триаж)

- **B1. Журнал публичного endpoint'а без границ** (ad_reward_callbacks: строка на каждый
  уникальный вызов — подтверждённый, повтор, подделка, зонд; без квот/retention/алармов) —
  уже defer в frontmatter спеки 3.1 (medium). Ретро подтверждает: операторская поверхность,
  активируется вместе с живым трафиком Google. **Disposition: остаётся defer владельца → AI-3.**
- **B2. Сопоставление ошибки по подстроке** `String(error.message).includes("ALREADY_EXISTS")`
  (ad-reward-progress.js:164) — хрупко к смене текста ошибки Firestore; это дом-прецедент
  истории 1.3 (settlement), то есть паттерн-согласованность, а не дивергенция. **Disposition:
  accept как осознанный дом-паттерн** (фиксировать в ретро, не чинить в одиночку).
- **B3. Rebuttal ревью 3.3 называет не тот механизм.** Triage спеки 3.3 отклоняет находку
  «повтор по рукописной записи вернул бы boxesGranted 0» доводом «поле ставится всегда» —
  фактически защиту даёт другое: replayResponse возвращает сохранённый `response` дословно
  (purchase-verification.js:702 `if (existing.response …) return existing.response;`), а BOX-ветка
  всегда кладёт `response` в запись расчёта. Дефекта нет — код проверен; запись сделана, чтобы
  будущие аудиты не гонялись за неверным основанием. **Disposition: accept-as-is (пояснено).**

Проверено и признано чистым (кандидаты, снятые кодом): replay-ветка купленной коробки (B3 + пин
формы записи в testABoughtBoxLands…), монотонность счётчика при блокировке потолком (тесты
«tenth view … views kept», «a crossing on the next day…»), свежесть timestamp/amount подписи
(записываются, не проверяются — прямо по спеке 3.1), подмена `&signature=` внутри контента
(решает ECDSA, тест «a tampered reward amount…»), duplicate-параметры и битое %-экранирование
(тесты есть), сканы 3.4 (токен «feature:ads» добавлен патчем ревью; SDK-скан фильтрует test-дерево).

## Behavior verification

- **Серверная половина (3.1–3.3) — исполнено этим ретро.** `node run-tests.js` на VPS
  (node v22.16.0, /root/.zcode/server/node): **128 из 129 сьютов PASS**; все 22 релевантных
  эпику сьюта зелёные на HEAD сегодня: ad-reward-handler/-progress/-verification, box-service,
  gift-boxes/-rewards/-fixtures, purchase-settlement/-verification/-clawback/-notifications/
  -partial-refund/-refund-order/-storage-contract, economy-constants/-handler/-season,
  currency-ledger, premium-economy/-purchases. Единственный провал — mvp-isolation.test.js
  (cleartext-allowlist ждёт только 204.44.66.27, а в mvp_network_security_config.xml:14 стоит ещё
  LAN-адрес 192.168.31.218) — внесён коммитом `7d8b91bd` (story 5.13 device proof,
  mvp-candidate-линия, 18.10), **вне диапазона эпика 3**; к монетизации отношения не имеет
  (передано волной синтезатору, не мой трекер).
- **Живой E2E — не исполнялось и исполнено быть не могло:** аккаунт AdMob закрыт с 01.09 (живой
  SSV-callback, настройка консоли, живой показ невозможны — трекер, ст. 159–163; спеки 3.1/3.4),
  реальная покупка недостижима без SKU в Play Console (спека 3.3), деплой в Firebase — только по
  go владельца. Серверный путь закреплён фикстурами (своя P-256-пара, подделка Firestore) —
  это и прогнано выше. Названный в спеке 3.1 followup («сквозной 500 при недоступных ключах через
  настоящий export») остаётся непроверяемым офлайн — не закрыт этим ретро.
- **Kotlin-гейты 3.4 (ads:domain allTests/detekt/ktlint, assembleMvp) этим ретро НЕ перегонялись**
  — в параллельной волне тяжёлая Gradle-задача одна, ретро не встаёт в очередь sgw; спека
  фиксирует их зелёными на закрытии истории 11.10. Отмечено как «not run», не как «clean».

## Previous-retro follow-through

В монетизационном треке предыдущего ретро нет: ключи epic-0/1/2-retrospective стоят `optional`,
файлов ретро за эпиками 1–2 этого трека не существует (файлы epic-*-retro-2026-09-*.md —
сентябрьская sync-линия, другой трек). `action_items` в sprint-status-monetisation.yaml
отсутствует как секция. Следовательно: **фоловер нечего исполнять — не «всё закрыто», а ретро в
треке впервые.**
Ретро epic-7-commerce от 25.10 — другой трек, по инструкциям волны не мой.

## Action items (предложено, не применено — трекер волной менять запрещено)

| # | Что | Владелец | Источник |
|---|-----|----------|----------|
| AI-1 | Выделить «один магазинный слот» в код: хелпер движения пары часов (nextBonusBoxAtMs + nextAdRewardBoxAtMs) в gift-boxes.js либо инвариант-тест «после PREMIUM_DAILY и после ad-выдачи оба часа равны» | dev-цикл монетизации | A2, box-service.js:153, ad-reward-progress.js:142 |
| AI-2 | Один стиль громкого отказа на испорченный boxCount у обоих писателей (кодированный INVALID_ARGUMENT vs голый Error в storedBoxCount) | dev-цикл монетизации | A4, ad-reward-progress.js:137, purchase-settlement.js:386 |
| AI-3 | Владелец-де́йствия, уже зарегистрированные в frontmatter спек: консоль AdMob + SSV URL + деплой endpoint'а (3.1), SKU коробки в Play Console + строка boxPacks в configs/economy (3.3), живой SDK/потребители врат + Android-обёртка Context→Map (3.4); туда же retention/квоты журнала ad_reward_callbacks (B1, defer спеки 3.1) | владелец (блокировано закрытым аккаунтом AdMob с 01.09) | трекер ст. 159–163, спеки 3.1/3.3/3.4 |
| AI-4 | Сверить замороженные тексты спек с постройкой: сигнатура planAdRewardBox без boxCount (спека 3.2 Code Map) и потолок query 8 192 байта (спека 3.1) — внести в Spec Change Log решений владельца | владелец/спек-владелец | A5 |
| AI-5 | Решить, пишет ли рекламная коробка игроку квитанцию (сейчас — осознанного квитанции нет: guard + строка книги, спека 3.2 Design Notes), когда доедет клиентская витрина 5.4 «За что начислено» — экран квитанций рекламную коробку не покажет | владелец (+клиентская история) | Open questions, спека 3.2 Design Notes |
| AI-6 | Отдать mvp-линии: mvp-isolation.test.js красный на HEAD из-за LAN-IP в cleartext-allowlist (коммит 7d8b91bd, story 5.13) — не монетизация, но волна должна увидеть красный гейт | трек mvp-candidate | Behavior verification |

## Acceptance verdict

**accepted-with-open-items** (критерии: declared).

- Все четыре истории `done` (detect-epic: pending_stories пуст), подтверждено коммитами b7b908a0 →
  7de873f2 и Auto Run Result спек.
- AC каждой истории названы тестами в диффе и прогнаны этим ретро на HEAD: 3.1
  (подтверждение/повтор/FOREIGN/подделка×5/UNVERIFIABLE/недоступные ключи/нечитаемые/UNKNOWN_ACCOUNT),
  3.2 (4/5/6–9/10-в-сутки/следующие сутки/стрик+реклама/испорченный boxCount/чистый план),
  3.3 (покупка в boxCount/quantity×3/первая коробка/испорченный счётчик/недостача/SKU_NOT_SOLD/
  конфликт классов/скан единственного дропа), 3.4 (матрица врат, закрытый перечень мест, сканы
  урока и SDK — гейты зелёные на закрытии, этим ретро не перегонялись, см. Behavior verification).
- Блокирующих находок нет: все пережившие триаж находки — low/defer-класс (A2, A4, A5, B1–B3).
- Открытые позиции учтены и трекаются (AI-1…AI-6): владельческие де́йствия по AdMob/Play,
  сверка текстов спек, две код-медицинские статьи, одно открытое продуктовое решение и один
  чужой трек-красный.

## Open questions

1. Квитанция рекламной коробки (AI-5): нужно ли игроку видимое «за что», и в какой истории —
   ответ меняет клиентскую витрину 5.4 и, возможно, потребует server-side строки квитанции.
2. Судьба pина EXPORT_SET (A3): пока index.js растёт, каждый новый endpoint переопределяет пин;
   при распиле index.js пин переедет — решение принять заранее, а не на десятом re-pin.
3. Поведение «одного слота» при будущих источниках коробок (лидерборд 5.2, рефералка 5.3 платят
   boxCount мимо суточных часов): эпик 3 фиксирует потолок только для серии+магазина; платные и
   сезонные источники вне потолка — подтвердить, что это и есть воля владельца на все будущие
   источники (формулировка из epic-3-context: «влезть в потолок или заменить его часть»).

## Assumptions (headless — всё решено без пользователя)

- Эпик выбран invocation'ом (эпик 3, трек монетизации, файл sprint-status-monetisation.yaml) —
  не автодетектом; detect-epic --epic 3 подтверждает: 4/4 done, pending_stories пуст.
- Ретро-документ записан под именем epic-m3-retro-2026-10-26.md (дано волной), а не дефолтным
  epic-3-retro-{date}.md — чтобы не пересечься с соседними ретро волны.
- Sprint_status.py update НЕ исполнялся: волна запрещает писать sprint-status-monetisation.yaml;
  ключ эпик-3-ретроспективы остался `optional`, action items не вписаны в трекер — переносит
  отдельный агент после волны (список — в Action items и в openItems результата).
- Линзы bmad-review исполнены inline одним исполнителем (субагент-ревизор недоступен в среде
  волны) — то же отклонение, что записано в Implementation Notes самих спек 3.1–3.4.
- Вердикт выставлен машиной без человека; human-override не было. Критерии declared (по спекам),
  не profiled.
- Живые интеграции (AdMob SSV, Play, деплой) признаны неисполнимыми в среде; behavior check
  сведён к серверному сьюту (исполнен) и Kotlin-гейтам (не перегонялись, причина записана).
