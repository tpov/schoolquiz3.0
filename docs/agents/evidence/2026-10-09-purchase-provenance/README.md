# 7.7 purchase provenance / server-side purchase integrity (story 7.7, epic-commerce) — 2026-10-09

История: «Server-side purchase integrity (AD-18): authoritative Play verification with
account/app/product/order/quantity binding before any grant; pending/unverified grants
nothing; permanent token fence, cumulative immutable refund receipts, monotonic
refundableQuantity; RTDN triggers verification, not a money command; sales-off keeps
query/recovery/refunds; node/rules tests green». Аудит показал: **покупочный серверный
контур уже существует целиком** (эпохальные коммиты c5895b65 «settle Arena tickets,
premium grants and box operations atomically», fcf9c2bf «wire private event and recovery
APIs with refund fences», c7742584 «reconcile partial quantities with monotonic refund
receipts»); пробелом был только rules-контур приватности покупочной бухгалтерии — как в
4.2, deny-поведение существовало, но не было закреплено ни поведенческими проверками,
ни текстовыми якорями.

## Чек-лист истории → что уже в коде (не пересоздавалось, ссылки и закрепление)

1. **Authoritative Play verification before any grant** — `verifyPurchase` спрашивает Play
   до записи (`functions/purchase-settlement.js:105` `getProductPurchase`), решение
   принимается чистым `decideSettlement` над ответом Play (`purchase-verification.js:277`),
   начисление и запись расчёта — одна транзакция (`purchase-settlement.js:181-285`);
   multi-quantity gold дополнительно требует V2-доказательство
   (`getProductPurchaseByToken`, `purchase-settlement.js:131-174`), а конфиг, сменившийся
   между preflight и транзакцией, не может провести грант без V2 (`:196-201`).
   Закрепление: `purchase-settlement.test.js`, `purchase-verification.test.js`.
2. **Binding account/app/product/order/quantity** — аккаунт: `obfuscatedExternalAccountId`
   = sha256(uid), чужая метка → `permission-denied` `REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT`
   (`purchase-verification.js:142-149, 290-292`); приложение: packageName сверяется на
   пути и расчёта (`readRevocationFence` c packageName, `purchase-settlement.js:100`), и
   RTDN (`purchase-clawback.js:121`); продукт: `purchase.productId !== sku` → отказ
   (`purchase-verification.js:285-287`); заказ: `orderId` фиксируется в расчёте, квитанции
   и аудите (`purchase-verification.js:209, 465-488`); количество: unreadable quantity →
   отказ (`:315-318`), Premium только q=1 (`:330`).
3. **Pending/unverified grants nothing** — недоступность Play или сбой транзакции = исход
   `unavailable`, «ничего не записано, клиент повторит тем же путём»
   (`purchase-settlement.js:107-124, 289-301`); pending-покупка — терминальный отказ без
   начисления (`decideSettlement`, ветка `PURCHASE_STATE_PENDING`); RTDN-возврат до
   расчёта ставит fence и держит расчёт в `SETTLEMENT_REVOCATION_PENDING`
   (`purchase-settlement.js:307-309`).
4. **Permanent token fence** — `purchase_revocations` c `readRevocationFence` /
   `revocationSettlementGate` / `confirmedRevocation` (`functions/purchase-revocations.js`);
   fence не снимается ни владельцем, ни админом, переживает повторные сообщения
   («permanent token fence retained», `purchase-clawback.js:226-236`).
5. **Cumulative immutable refund receipts, monotonic refundableQuantity** — идентификатор
   возвратной квитанции/аудита несёт кумулятивное возвращённое количество, поэтому
   повторные частичные RTDN не перезаписывают прежние записи
   (`purchase-notifications.js:478-487` refundDocId `_q{n}`); V2-наблюдение валидируется на
   монотонность (`refundableQuantity > prior → отказ`, `purchase-revocations.js:113`),
   частичный возврат уменьшает начисление (`partialRefundGrantAdjustment`) и баланс не
   уходит в минус — недостача (shortfall) гасится следующим начислением
   (`purchase-settlement.js:239-246`). Закрепление: `purchase-partial-refund.test.js`,
   `purchase-refund-order.test.js`, `purchase-clawback.test.js`.
6. **RTDN triggers verification, not a money command** — `playPurchaseNotifications`
   (`functions/index.js:3347-3357`) передаёт сообщение в `clawBackPurchase`, который
   декодирует его только как *сигнал*, сверяет packageName, ставит pending-fence и
   пересвергает факт с Play (V2 token-only до расчёта, `getProductPurchase` после;
   `purchase-clawback.js:92-240`); недоступность Play оставляет retryable,
   нетерминальный fence (`:208-217`). Закрепление: `purchase-notifications.test.js`.
7. **Sales-off keeps query/recovery/refunds** — выведение SKU из продажи
   (`goldPerUnit === null` → `REASON_SKU_NOT_SOLD`, `purchase-verification.js:336`)
   блокирует только новое начисление: оплаченный токен остаётся непогашенным и
   воспроизводится при следующем запуске (`SKU_NOT_SOLD_HINT`,
   `purchase-settlement.js:62-65`), уже известный токен отвечается из записи расчёта до
   обращения в Play (`:94-103`), а возвраты/clawback вообще не читают таблицу продажи —
   только Play и fence. Закрепление: `purchase-verification.test.js:294-303, 399`.
8. **Идемпотентность/двойное начисление** — `settlementId` = sha256 токена
   (`purchase-verification.js:138-139`), `transaction.create` вместо `set`: параллельный
   расчёт того же токена падает и на повторе отвечает сохранённым вердиктом
   (`purchase-settlement.js:259-262`); золото пишется абсолютом, не инкрементом
   (`:10-14`).

## Дозаполнено этой историей: правила приватности покупочной бухгалтерии

`scripts/rules-emulator-test.js` не имел ни одной проверки на семью purchase_*; добавлена
сюита `testPurchaseIntegrityPrivacy()` (+22 проверки), зеркально 4.2-паттерну:

- deny чтения/записи `purchase_settlements` (игрок/админ), `purchase_revocations`
  (игрок/админ, включая попытку снять fence), `purchase_audit`, `purchase_notifications`,
  `premium_accounts` — серверная бухгалтерия, недоступная даже администратору;
- receipts: владелец читает свои (единственная клиент-читаемая часть — «вам начислено»),
  чужой/гость не читают, запись закрыта всем, включая владельца;
- текстовое закрепление пяти явных блоков закрытия (`purchase_settlements`,
  `purchase_revocations`, `purchase_notifications`, `purchase_audit`,
  `premium_accounts`) — чтобы ослабление было решением, а не случайностью.

## Gates (все зелёные)

- `gate-rules.log` — rules-раннер под эмуляторами auth+firestore: **193/193 проверок
  прошло** (171 до + 22 новых). Прогон на отдельных портах (18089/19098), конфиг
  временный `/tmp/wm/firebase-rules-test.json`, продовые `firebase.json`/порты не тронуты.
- `gate-functions.log` — полный functions-гейт без emulator env: **92 of 92 suites passed**.
- `gate-functions-demo-backend.log` — тот же гейт против живого demo-backend
  (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`, `GCLOUD_PROJECT=demo-schoolquiz-mvp`):
  **92 of 92 suites passed**.

## Scope notes

- Sandbox-подтверждение реальным Play-платежом — 7.6 (внешнее: Play Console владельца),
  на 7.7 не влияет; живой Play API в гейтах не вызывается (клиент подделка, V2-путь
  покрыт тестами).
- Демо-гигиена прогонов: rules-раннер работает на собственных эмуляторах
  (`clearFirestore` на своём projectId), functions-сюиты против demo-backend пишут только
  штатные тестовые транзакции (2.13-практика); probe-ключей/профилей не добавлено.
- AD-18-половина про старты/списания экзаменов (CAP-11/12) — отдельные истории; 7.7
  закрывает CAP-15 (покупки).
