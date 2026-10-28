---
title: 'M3.1 — The network confirms the reward, not the phone'
type: 'feature'
created: '2026-10-11'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: true
baseline_commit: '814d72c35963e27aaff52aec96ce153d5d3e5d29'
story_key: '3-1-награду-подтверждает-сеть-а-не-телефон'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m1-3-refund-handler.md'
warnings: []
deferred:
  - summary: >-
      Публичный SSV-endpoint пишет строку записи на каждый уникальный вызов — подтверждённый,
      повторный, поддельный или зонд без query; квот, retention и алармов на объём нет.
    evidence: |-
      Подделку нельзя отличить до проверки подписи, а запись — требование спеки (каждый callback
      остаётся доказательством); хранением такого журнала в Firestore уже живёт
      purchase_notifications (m1-3) с тем же профилем. Операторская поверхность (алерты на объём
      коллекции ad_reward_callbacks, политика retention) — не код этой истории.
    location: >-
      functions/ad-reward-progress.js:applyAdReward
    severity: medium
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** коробка за рекламу ещё не построена, и её фундамент — доверие. Сегодня ничто не мешает
начислить прогресс к коробке по событию с устройства, а событие с устройства — это редактируемый
запрос: поддельным вызовом коробку можно печатать. Эпик 3 начинается с обратного: засчитанный
просмотр существует только как подписанное подтверждение рекламной сети, пришедшее на сервер
мимо телефона.

**Approach:** HTTPS-endpoint для AdMob SSV-callback (`onRequest`, `invoker: "public"` — вызывает
Google). Верификация — по контракту SSV: подпись ECDSA P-256/SHA-256 (DER, Base64) над сырым
куском query-строки до `&signature=`, публичный ключ — по `key_id` из `https://gstatic.com/admob/reward/verifier-keys.json`
(кэш не дольше 24 часов). Подтверждение пишется один раз под ключом = идентификатор транзакции
из callback'а, прогрессу просмотра поджигает только `CONFIRMED`. Подделка, повтор и нечитаемое —
именованные записи без начисления, отвеченные 200; недоступность ключей — 500 (Google повторит),
ничего не записано. Консоль AdMob и живой SDK — не здесь: серверная половина закрепляется
фикстурами (тестовая P-256-пара, внедряемый fetcher ключей).

## Boundaries & Constraints

**Always:** верифицируется подпись, а не происхождение запроса — решение принимает только она.
Идентификатор транзакции (`transaction_id`) — единственный ключ подтверждения: повтор отвечает тем
же исходом и не двигает прогресс второй раз. `user_id` обязателен и проходит ту же проверку формы,
что uid везде на сервере; начислять можно только существующему игроку. Каждый callback попадает в
запись (write-once) — подтвердился, повторился, подделан, нечитаем: без записи не остаётся ни
одного. Прогресс — строгое целое на документе игрока, растёт на 1 за подтверждение; `increment`
запрещён (не отличает повтор). `reward_amount`/`reward_item` записываются, но не проверяются:
что именно подарила сеть, решает настройка ad unit (консоль — defer), а не сервер. Комментарии в
`functions/` — русские. Решение чистое, деньги — отдельный путь с внедрённым `db`, endpoint —
тонкая обвязка; тесты — подделка Firestore и тестовые ключи, без firebase-admin и сети.

**Ask First:** существующий денежный порядок эпика 1 (`verifyPurchase`, settlement, clawback),
коробочные операции `box-service.js` (выдача и потолок — история 3.2), клиент и любые Android/Kotlin
файлы (история 3.4), незакоммиченные `gradle.properties`, `local.properties`, `functions/.secret.local`.

**Never:** никакого клиентского callable «реклама просмотрена» — устройство не сообщает о награде,
оно вообще не участник расчёта. Никакой выдачи коробок и дневных потолков в этой истории. Никаких
секретов в репозитории; ключи сети — публичные, кэш в памяти процесса. Не отвечать 4xx на
нечитаемое и подделку — Google повторяет не-200 (5 раз по секунде), а повторы мусора не нужны.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Честный подтверждённый просмотр | подписанный Google callback, `user_id` = uid, новый `transaction_id` | 200; одна запись CONFIRMED; `adRewardViews` +1 | N/A |
| Повтор того же callback'а | тот же `transaction_id`, тот же uid | 200; ничего не записано, прогресс не двинут, исход REPLAYED | not an error |
| Тот же `transaction_id`, другой uid | подписанный, но uid не совпал с записанным | 200; запись не переписывается, исход FOREIGN | not an error |
| Поддельный вызов | известный ключ, подпись не сходится | 200; запись FORGED, прогресс не двинут | N/A |
| Неизвестный `key_id` | после одного принудительного обновления ключей ключа нет | 200; запись UNVERIFIABLE, прогресс не двинут | N/A |
| Сервер ключей недоступен | fetcher отказал или вернул не JSON | 500; ничего не записано — Google повторит свежим забором ключей | retry |
| Нечитаемый запрос | нет `signature`/`transaction_id`/`user_id`, uid кривой | 200; запись UNUSABLE с названной причиной | N/A |
| `user_id` неизвестного игрока | подписан, аккаунта нет | 200; запись UNUSABLE (`UNKNOWN_ACCOUNT`), начислять некому | not an error |
| Пятый и десятый просмотр | `adRewardViews` 4→5→… | счётчик монотонно растёт; коробку не выдаёт (3.2) | N/A |

</frozen-after-approval>

## Code Map

- `functions/ad-reward-verification.js` -- NEW pure: `readAdRewardCallback(rawQuery)` — разбор
  query (`URLSearchParams`), контент для подписи = `rawQuery.slice(0, rawQuery.indexOf("&signature="))`
  (порядок параметров не менять), поля `adNetwork/adUnit/customData/keyId/rewardAmount/rewardItem/
  signature/timestamp/transactionId/userId`, `null` при нечитаемости; `verifyAdRewardSignature({content,
  signatureBase64, publicKeyPem})` — `node:crypto` `verify("sha256", …)`; `selectVerifierKey(keysDocument,
  keyId)` — выбор из `{keys: [{keyId, pem, base64}]}`, испорченный документ → `null`; `verifyAdReward(...)`
  → `CONFIRMED_READY | SIGNATURE_INVALID | KEY_UNKNOWN`; фабрики записей `adRewardCallbackRecord`,
  `adRewardConfirmationRecord` (id = sha256(transaction_id), как `boxOperationId`). Голос —
  `purchase-verification.js` (константы вердиктов, закрытые списки).
- `functions/ad-reward-progress.js` -- NEW money path, `db` внедрён (образец: `purchase-settlement.js`,
  форма теста: `premium-test-db.js`): `applyAdReward({db, callback, keyPem, now, log})` → `{verdict}`.
  Одна транзакция: guard `ad_reward_confirmations/{sha256(transaction_id)}` (read + `create` — гонка
  повторов падает на create, как в settlement), игрок `users/{uid}`, запись callback'а
  `ad_reward_callbacks/{sha256(content+signature)}` (merge — повтор доставки пишет ту же строку),
  `adRewardViews` абсолютом. Коллекции объявлены рядом с `BOX_OPERATIONS_COLLECTION`.
- `functions/index.js` -- NEW thin endpoint: `exports.adMobRewardConfirmed = onRequest({region:
  "us-central1", maxInstances: 2, invoker: "public"}, …)` по образцу `logoImage` (index.js:3304);
  fetcher ключей — `https` GET `https://gstatic.com/admob/reward/verifier-keys.json`, кэш в памяти
  ≤24 ч + одно принудительное обновление при незнакомом `key_id`; fetcher внедряется в pure-модуль
  тестами. Ответ: 200 «OK» на всё, кроме UNAVAILABLE → 500.
- `firestore.rules` -- `match /ad_reward_confirmations/{document=**}` и
  `match /ad_reward_callbacks/{document=**}`: `allow read, write: if false;` — рядом с
  `purchase_settlements` (firestore.rules:71) и `box_operations` (:106).
- Read-only: `functions/box-service.js`, `functions/gift-boxes.js`, `shared/**`, `android/**`,
  `apps/**` — выдача коробок и клиент не в этой истории.
- Тесты (директория `functions/`, подхватываются `run-tests.js` автоматически): NEW
  `ad-reward-verification.test.js` (фикстура — `crypto.generateKeyPairSync("ec", {namedCurve:
  "prime256v1"})`, подписанные и испорченные query-строки), NEW `ad-reward-progress.test.js`
  (поддельный Firestore: подтверждение/повтор/FOREIGN/подделка/нечитаемое/недоступность).

## Tasks & Acceptance

**Execution:**
- [ ] `functions/ad-reward-verification.js` + `.test.js` -- разбор, контент подписи, верификация,
  выбор ключа, фабрики записей -- по матрице.
- [ ] `functions/ad-reward-progress.js` + `.test.js` -- транзакция подтверждения: guard create,
  запись callback'а, `adRewardViews` абсолютом -- по матрице.
- [ ] `functions/index.js` -- endpoint + fetcher ключей с кэшем -- тонко, без решений.
- [ ] `firestore.rules` -- две закрытые коллекции.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `3-1-…` →
  `in-progress`, затем `review`.

**Acceptance Criteria:**
- Given подписанный сетью callback нового просмотра, когда он приходит на endpoint, то прогресс
  растёт на 1 и подтверждение записано под идентификатором транзакции.
- Given тот же callback повторно, когда он приходит снова, то прогресс и записи не меняются.
- Given пять поддельных вызовов с устройства (плохая подпись, чужой ключ, выдуманный формат), когда
  они приходят, то ни один не двигает прогресс и каждый остался в записи FORGED/UNUSABLE.
- Given сервер ключей недоступен, когда приходит честный callback, то ничего не записано, ответ 500,
  и повтор после восстановления ключей подтверждает просмотр.
- Given клиентское событие «реклама просмотрена», когда оно происходит, то на сервере нет пути,
  которым оно начисляет прогресс: такой вызов не существует.

## Implementation Notes

- Route `full`, но реализация выполнена в этой же сессии: у платформы нет синхронного примитива
  субагента (Agent/Task), а динамические workflow требуют интерактивного подтверждения, несовместимого
  с автономным прогоном. Спека осталась единственным источником правды; свежий контекст обеспечивают
  линзы шага-04 субагентами.
- 2026-10-11: изменение поверхности index.js (новый exports.adMobRewardConfirmed) законно сдвинуло
  pin арены ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256 — re-pin с записью в комментарии
  (arena-lifecycle-views-handler.test.js), как это делали истории 2.13/4.9/8.2 до нас.
- 2026-10-11: матрица потребовала ветку UNKNOWN_ACCOUNT — подписанный callback для несуществующего
  аккаунта записывается UNUSABLE с причиной, guard не расходуется (в спеке это строка матрицы, код
  догнан до неё).
- 2026-10-11: гонка повторов guard'а (create упал на ALREADY_EXISTS → второй проход отвечает по
  записи) недостижима в последовательной подделке Firestore; закреплена последовательная семантика
  (REPLAYED по guard'у), как в истории 1.3.
- 2026-10-11: ревью исполнено линзами в этой же сессии (платформа без субагентов — см. первую
  заметку); три патча применены (экранирование, правила-тест, обвязка), один defer оформлен.

## Spec Change Log

## Review Triage Log

### 2026-10-11 — Review pass (thorough: blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

Механизм: линзы исполнены в этой сессии — платформа не дала ни примитива субагента (Agent/Task
нет), ни подтверждения запуска workflow («No interactive approval handler configured»); свежесть
контекста ограничена повторным чтением диффа на каждую линзу. Записано как отклонение, не как
defer: ревью состоялось, субагентность — свойство платформы.

- verdicts: 13 findings — high 0, medium 6, low 1, false 6, maybe-false 0
- findings:
  - `[medium]` `[patch]` Битое процента-экранирование в query («%ZZ») роняло decodeURIComponent —
    endpoint отвечал 500 и сжигал повторы Google на мусоре, вопреки строке матрицы «нечитаемое →
    200» — исправлено: разбор ловит URIError и зовёт строку нечитаемой (UNPARSEABLE, запись,
    200); тест «broken percent-escaping is an unreadable query, not a crash».
  - `[medium]` `[patch]` Оба новых правил-блока firestore.rules не проверялись никаким тестом —
    дом проверяет правила исходником рядом с модулем (purchase-notifications.test.js:417);
    добавлен исходник-тест «firestore rules close both ad-reward collections».
  - `[medium]` `[patch]` Обвязка endpoint'а (извлечение query из URL, ответ 200/500) не имела
    теста — исправлено частично в меру офлайна: чистый adRewardQueryFromUrl + прогоны настоящего
    exports.adMobRewardConfirmed против подделки Firestore (200 + запись UNUSABLE, запрос без
    query); путь 500 покрыт модулем (outcome UNAVAILABLE) и чтением — сквозной 500 требует сети,
    остаток назван в Auto Run Result.
  - `[medium]` `[defer]` Каждый уникальный вызов публичного endpoint'а (подделка, повтор, зонд)
    пишет строку в ad_reward_callbacks без квот и retention — см. deferred в frontmatter
    (операторская поверхность, не код истории).
  - `[medium]` `[defer]` То же хранилище принимает неограниченный поток подделок до проверки
    подписи — тот же корень и тот же defer.
  - `[medium]` `[defer]` Зонды URL (health-check, сканеры) пишут UNPARSEABLE-строки — тот же
    корень и тот же defer.
  - `[low]` `[reject]` https.get не следует редиректам gstatic — отвергнуто: контракт ключевого
    сервера отдаёт 200 по стабильному адресу, а окно повторов Google само пересобирает ключи;
    следование редиректам — спекулятивная сложность.
  - `[low]` `[false]` «write-once журнал» против merge-перезаписи строки при повторе доставки —
    опровергнуто: id строки стабилен, вердикт отражает последнюю доставку, доказательство не
    теряется; реальное поведение названо в комментарии модуля.
  - `[low]` `[false]` Провалившийся форс-обновление оставляет кэш до суток — опровергнуто: это
    контракт кэша (≤24 ч), форс-обновление существует, ротация ключей анонсируется.
  - `[low]` `[false]` Окно гонки guard'а непроверяемо последовательной подделкой — опровергнуто
    как дефект: закреплена последовательная семантика REPLAYED по guard'у (прецедент истории 1.3),
    записано в Implementation Notes.
  - `[low]` `[reject]` Boolean в adRewardViews молча превращается в 1 — отвергнуто: поле пишет
    только сам сервер, строгий typeof добавил бы ветку ради недостижимого.
  - `[false]` Гонка двух разных transaction_id на одном игроке теряет счёт — опровергнуто:
    Firestore сам прерывает конфликтующие read-write транзакции, повтор читает свежий счётчик.
  - `[false]` Числовые крайности key_id («1e21», «-1», мусор) — опровергнуто: отсечены
    Number.isSafeInteger/знаком до выбора ключа (тест «unusable reasons…»).

## Design Notes

- SSV-повтор Google — до пяти попыток с секундным интервалом: окно почти мгновенное, поэтому при
  недоступности ключей запись не создаётся вовсе (500), а после восстановления тот же callback
  подтверждается с нуля; «парк» на этот случай не строится — это осознанный разрыв, отложенный в
  defer ниже.
- `reward_amount`/`reward_item` и `custom_data` — доказательства в записи, не участники решения:
  отображение «за что сеть платит» на наш грант появится вместе с консолью (defer), подменить его
  подписью нельзя.
- Прогресс — монотонный счётчик без дневных полей: дневной потолок и выдача коробки придут в 3.2
  в ту же транзакцию и не потребуют миграции этого поля.

## Verification

**Commands:**
- `node functions/run-tests.js` -- expected: все сьюты зелёные, включая новые; на базе 101/101.
- `node functions/check-syntax.js` -- expected: чисто.

**Manual checks (no CLI):**
- Деплой и настройка SSV URL в консоли AdMob не выполняются (аккаунт закрыт, действие владельца) —
  endpoint проверяется чтением обвязки и тестами; отметить в отчёте.

## Auto Run Result

Status: done
Blocking condition: none

**Summary:** серверная половина истории 3.1 — SSV-подтверждение награды рекламной сетью. Endpoint
`adMobRewardConfirmed` (onRequest, invoker public) принимает callback Google, чистый модуль
разбирает query (байты до `&signature=`), выбирает публичный ключ по `key_id` (кэш ≤24 ч + один
форс-обновление при незнакомом id) и проверяет подпись ECDSA P-256/SHA-256; одна транзакция пишет
guard подтверждения под sha256(transaction_id), строку журнала callback'ов и `adRewardViews +1`
абсолютом. Повтор — REPLAYED, чужой предъявитель — FOREIGN, плохая подпись — FORGED, нечитаемое —
UNUSABLE с причиной, неизвестный аккаунт — UNUSABLE/UNKNOWN_ACCOUNT, недоступные ключи — 500 без
записи. Обе коллекции закрыты правилами. События с устройства не имеют серверного пути начисления.

**Files changed:**
- `functions/ad-reward-verification.js` — NEW pure: разбор, подпись, ключи, фабрики записей.
- `functions/ad-reward-progress.js` — NEW money path: транзакция подтверждения, guard, прогресс.
- `functions/index.js` — endpoint + fetcher ключей с кэшем (тонко).
- `functions/ad-reward-verification.test.js`, `functions/ad-reward-progress.test.js`, `functions/ad-reward-handler.test.js` — NEW: 29 тестов (подписанные фикстуры P-256, подделка Firestore, настоящая обвязка против стаба).
- `firestore.rules` — `ad_reward_confirmations`, `ad_reward_callbacks`: read/write false.
- `functions/arena-lifecycle-views-handler.test.js` — re-pin поверхности (adMobRewardConfirmed).
- `_bmad-output/implementation-artifacts/epic-3-context-monetisation.md` — NEW контекст эпика.
- `sprint-status-monetisation.yaml` — 3-1: backlog → done.

**Review findings breakdown:** 3 патча (битое процента-экранирование → нечитаемое/200 + тест;
исходник-тест правил для обеих коллекций; тест обвязки endpoint'а — 200/запись без сети + чистый
splitter URL), 1 defer (небезграничный журнал публичного endpoint'а — операторская поверхность,
в frontmatter), 6 отвергнутых находок с основаниями — полный журнал в Review Triage Log.
followup_review_recommended: true — названный непроверенный риск: сквозной ответ 500 при
недоступных ключах через настоящий export (нужна сеть; модульный исход UNAVAILABLE покрыт).

**Verification:** `node functions/run-tests.js` — 104/104 сьютов зелёные (было 101/101 на базе;
+3 новых, арена-пин переопределён и зелёный); `node functions/check-syntax.js` — 190/190 файлов
парсятся. Деплой и консоль AdMob — не выполнялись (аккаунт закрыт): живой callback владелец
настроит позже, defer вне этой истории зафиксирован в отчёте эпика.

**Residual risks:** живой трафик Google не проверялся (нет аккаунта) — контракт подписи следует
документации SSV и закреплён фикстурами собственной P-256-парой; путь 500 сквозь export офлайном
недостижим (см. выше); журнал ad_reward_callbacks без retention (defer).

### 2026-10-28 — сверка с постройкой (ретро m3, п.4; фикс-пак spec-retro-fixpack-e1-e2-e3-e6-m.md)
- Потолок длины query, названный ретро (8192), в замороженном тексте не назван; в постройке он есть: `rawQuery` длиннее 8192 символов — нечитаемый (`functions/ad-reward-verification.js:88`, запись UNPARSEABLE, ответ 200). Принятый потолок зафиксирован здесь как часть контракта.
