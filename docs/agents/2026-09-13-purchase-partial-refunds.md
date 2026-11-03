# ТЗ-10A — quantity-aware возвраты: отчёт с доказательствами

Дата фиксации: 13 сентября 2026. Это ограниченный серверный срез для multi-quantity
one-time purchases. Он не является приёмкой Play, Android-клиента или всего ТЗ-10.

## Точка состояния и границы

- Авторитетный репозиторий: `/srv/schoolquiz-mvp-20260906/integration`.
- Снимок данного отчёта: `e80e24843f30ed84b752d6597e702203bf16e2d5`.
- Исходный purchase preimage, от которого был начат срез: `8fc7af452e6727fec5487ef35e4e388359e14b8d`.
  Последующий `e80e248` — отдельный Arena-коммит; purchase-файлы в нём не менялись.
- Проверенный срез содержит девять изменённых отслеживаемых файлов, один новый Node test и
  отдельный Admin-SDK harness. На момент сбора нижеприведённых доказательств они ещё не были
  закоммичены; этот отчёт включается в отдельный purchase-коммит вместе с ними. Не было deploy,
  Play-запросов, Auth-аккаунтов в SDK harness, удаления или очистки данных.
- После targeted-проверок полный functions gate на замороженных исходниках завершился:
  86/86 suites PASS на Node 22.23.1, 533,714 ms; syntax PASS, 18,910 ms. SHA-манифест
  всех корневых JS/package файлов до и после совпал. Эти новые логи отдельно находятся в
  `/srv/schoolquiz-mvp-tools-20260906/tz06c/architecture-gates-e5WkJZto`.

Неизменяемая копия всех полученных логов и манифестов:

`/srv/schoolquiz-mvp-tools-20260906/tz06c/tz10a-actual-sdk-race-20260913.5OFvCu`

Копирование выполнено `cp -a` в новый каталог `mktemp -d`; перед и после копирования
отсортированные SHA-256 всех файлов совпали. В архиве 14 файлов, 19,851 байт.

## Изменённые source/test файлы

| Файл | SHA-256 рабочего дерева при HEAD `e80e248` |
| --- | --- |
| `functions/play-developer-api.js` | `c0c0c1b3455a67d92d1b0d38be626f6ec0b1cc2065a745ad17dbb0c5ebb6813d` |
| `functions/purchase-revocations.js` | `02c5fd41646046b0454707d9062ff5fe88533d15ee44571ca4eb443fb035882e` |
| `functions/purchase-verification.js` | `5df7e1db34a8a7377087890d099af34c33243d7af6714e46c3cca1e1f333efa0` |
| `functions/purchase-settlement.js` | `c02395e9226f97a26364161efc7d0fe7e096af7a40989a5b24c93700f985668b` |
| `functions/purchase-notifications.js` | `f4e5fe807db2aaf5c17aa486fa561931c5788db0d5e4ea13d1fd08b304a936d4` |
| `functions/purchase-clawback.js` | `a699913e2c01dd15a63a4f4f28a3b08a927298d66c9c8c6448bd1b6de96b2da8` |
| `functions/play-developer-api.test.js` | `fdccfc23e5104b6c7168bbcac20d4b4d3d33d3154817fe28bd7d3adee4a5c94d` |
| `functions/purchase-notifications.test.js` | `528dff5f5ff704d68e7affb3a0b8b545982741256fa713a64293b2779a605bd0` |
| `functions/purchase-refund-order.test.js` | `a6aa83ebc8fb60742daf8d79894438a90449d14b8de779acea96aa7d92d060d9` |
| `functions/purchase-partial-refund.test.js` (новый) | `f8fb255b67284f10ed59daf962b2f0a538297c35993b54724d7b04913a10e6e6` |

Tracked diff по первым девяти файлам: `+776/-48`; новый behavioral test — 412 строк.
Проверка пробелов для отслеживаемых девяти файлов: clean.

Инструмент реального Firestore-race не относится к production surface:

| Файл | SHA-256 |
| --- | --- |
| `scripts/mvp-purchase-partial-refund-firestore-test.js` (новый harness, 432 строки) | `2ec9431af6ca5747240a7d4310a913ceca0020039e0ced8c214dae697b27477e` |

Полные машинные манифесты: `current-tz10a-10files-sha256.txt`,
`current-sdk-harness-sha256.txt` и `final-inventory.txt` в сохранённом evidence-каталоге.

## Что реализовано в ограниченном срезе

1. ProductPurchaseV2 читается строго для одного line item и связывается с V1 по SKU, order,
   account marker и исходному quantity. Для нового gold grant с V1 quantity больше единицы V2
   proof обязателен; неточный, pending, cancelled, malformed или недоступный ответ не даёт
   начисления.
2. `refundableQuantity` используется как оставшееся количество. Уже пришедший partial RTDN
   создаёт монотонный fence; в транзакции grant может только уменьшиться до более свежего
   подтверждённого остатка.
3. Partial refund ведётся кумулятивно. Schema-1 settlement при первом partial переводится в
   schema-2 вместе с отдельными immutable receipt/audit/ledger документами. Следующий partial
   или final full refund использует новый cumulative quantity key, а не перезаписывает старый.
4. Одноразовый legacy full-refund key сохранён для совместимости. Premium quantity больше единицы
   остаётся явным unsupported route; прежняя quantity-1 premium семантика не менялась.

## Контракт провайдера

- [Google Play RTDN reference](https://developer.android.com/google/play/billing/rtdn-reference):
  для voided one-time purchase `refundType=1` означает полный refund, а
  `refundType=2` — quantity-based partial refund.
- [Google Play ProductPurchaseV2 reference](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2):
  `productLineItem.productOfferDetails.quantity` описывает купленное количество,
  `refundableQuantity` — количество, ещё не возвращённое/доступное к возврату.

RTDN не принимается как денежная величина сам по себе: он запускает проверку V2. Для positive
partial remainder код требует V2 `PURCHASED`; ответ PENDING/CANCELLED/absent не превращается в
списание. Full cancellation остаётся отдельным путём.

## Выполненные проверки

Все следующие запуски работали в контейнере `schoolquiz-mvp-firebase`, Node
`v22.23.1`, с `/repo` mounted read-only. Production-source hash сверялся до Admin-SDK запуска.

| Проверка | Результат и точное время | Лог |
| --- | --- | --- |
| Targeted Node behavioural suite | PASS, 48/48, Node test runner `1997.696481 ms`; файлы исполнялись последовательно с `--test-concurrency=1` | `node22-targeted-48.tap` |
| Legacy pure runner: purchase verification | PASS; runner не печатает собственный duration | `node22-purchase-verification.log` |
| Legacy pure runner: purchase notifications | PASS; runner не печатает собственный duration | `node22-purchase-notifications.log` |
| Admin-SDK harness default mode | PASS, `PLAN_ONLY_NO_CONNECTION` | `node22-plan-only-r2.log` |
| Admin-SDK partial-refund race, попытка 1 | FAIL за 3 секунды внешнего запуска; причина только в harness: literal `FOUND` вместо экспортируемого `PLAY_OUTCOME_FOUND === "found"` | `node22-actual-sdk-race.log` |
| Admin-SDK partial-refund race, попытка 2 | PASS за 9 секунд внешнего запуска | `node22-actual-sdk-race-r2.log` |

Два legacy runner запускались одной удалённой командой после основной 48-test suite; измеренный
внешний wall time этой команды был 2.1 секунды, включая SSH/container overhead. Это не
подменяется in-process duration: сами старые runner-файлы времени не выводят.

### Реальный Firestore-race

Harness требует ровно:

- `GCLOUD_PROJECT=demo-schoolquiz-mvp`;
- `FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080`;
- отсутствующие `GOOGLE_APPLICATION_CREDENTIALS` и `FIREBASE_CONFIG`;
- reviewed SHA manifest шести production-файлов;
- явный `--execute`, отдельное одобрение case и внешний
  `timeout --signal=TERM --kill-after=10s 180s`.

Внутренние десятисекундные barriers — только диагностика: они не объявлены отменой Admin-SDK
операции. Верхняя граница процесса обеспечивалась внешним `timeout`.

Все production collection roots были перенаправлены под уникальный
`__mvp_private_harness/tz10a_prr_*` prefix. Seed использовал только create после preflight
всех ожидаемых путей; direct writes из production adapter запрещены; transaction guard допускает
только 13 заранее объявленных private paths. Harness не создаёт Auth-аккаунтов и не делает
cleanup: fixture остаётся evidence.

- Попытка 1 сохранилась отдельно. До денег дошли только три synthetic fixture paths
  (marker, private config и private user); settlement, receipt, audit, ledger и refund effect не
  создавались. Это не дефект production-кода.
- Попытка 2: fake V1/V2 Play подтвердил quantity 5, два независимых partial RTDN были
  синхронизированы перед Firestore transaction write. Результат: initial grant 300, один
  cumulative refund 180 за quantity 3, final gold 140, `attemptedDistinctWritePaths=13`,
  `confirmedRetainedDocumentCount=13`, diagnostics пусты. Receipt, audit и ledger для
  cumulative q3 существуют.

## Оставшееся и ограничения

- **Old ACKed purchases.** Existing known credited settlement replay обходится без нового Play
  lookup; это сохраняет безопасный replay уже известной операции. Но этот срез не использовал
  реальный исторический ACKed/consumed purchase и не выполнял migration/repair старых данных.
  Unknown consumed token по-прежнему не может безопасно получить новый grant без отдельного
  доказательства/repair.
- **Клиент.** Kotlin, BillingClient, UI витрины, restore flow, account switching, потеря ответа
  на устройстве и обновление непустой установки не запускались и не менялись.
- **Real Play.** Не было Play Console/Sandbox покупки, acknowledgement, возврата, Pub/Sub RTDN
  доставки или credentials. Fake Play в harness проверяет только server transaction/retry
  семантику и не заменяет эти проверки.
- **Остальная матрица ТЗ-10.** Premium partial-use, несколько независимых real grants,
  real alternate-account recovery и реальный HTTP/Play путь требуют отдельных доказательств.
  Отдельный свежий private-rules probe также PASS: GET/PATCH/DELETE запрещены buyer,
  appAdmin и anonymous, fixture и updateTime неизменны. Его лог — `purchase-private-rules.log`
  в каталоге общего gate. Он создал два новых demo Auth-аккаунта и два create-only документа;
  это отдельная проверка прав, не Play. Исторические зелёные прогоны не суммируются с ней.

Итог: bounded server-only evidence для quantity-aware gold partial refunds положительное; полной
приёмки ТЗ-10A/ТЗ-09/ТЗ-10B и ручной Play-приёмки нет.
