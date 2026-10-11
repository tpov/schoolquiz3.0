"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const {
  AUDIT_ACTION_PURCHASE_REFUND,
  CLAWBACK_ALREADY_REFUNDED,
  CLAWBACK_ALREADY_RECONCILED,
  CLAWBACK_CLAW_BACK,
  CLAWBACK_NOTHING_TO_TAKE,
  CLAWBACK_PLAY_DISAGREES,
  NOTIFICATION_PURCHASED,
  NOTIFICATION_TEST,
  NOTIFICATION_UNKNOWN,
  NOTIFICATION_VOID,
  PURCHASE_NOTIFICATIONS_COLLECTION,
  RECEIPT_KIND_PURCHASE_REFUNDED,
  RECEIPT_MESSAGE_GOLD_PACK_REFUNDED,
  decideClawback,
  decidePartialClawback,
  decodeNotification,
  notificationKind,
  notificationRecord,
  packageOf,
  readSubscriptionRenewal,
  refundAuditRecord,
  refundDocId,
  refundReceiptRecord,
  refundSettlementUpdate,
  settlementClawbackGate,
  tokenOf,
} = require("./purchase-notifications");
const {
  MAX_TOKEN_LENGTH,
  STATUS_CREDITED,
  STATUS_REFUNDED,
  readProductPurchase,
  settlementId,
} = require("./purchase-verification");
const {ECONOMY_CONSTANTS_DOC} = require("./economy-constants");

const NOW = 1_800_000_000_000;
const UID = "player-1";
const TOKEN = "hlmbchfnkkjlbhdejkjhkkbo.AO-J1OyExampleTokenValue-1234567890";
const SKU = "gold_pack_small";
const ID = settlementId(TOKEN);
const PACKAGE = "com.tpov.schoolquiz";
const MESSAGE_ID = "9382173817231";

/** Уведомление о возврате, каким его шлёт Play: числа числами, время — строкой. */
function voided(overrides = {}) {
  return {
    version: "1.0",
    packageName: PACKAGE,
    eventTimeMillis: "1799999900000",
    voidedPurchaseNotification: {
      purchaseToken: TOKEN,
      orderId: "GPA.3333-4444-5555-66666",
      productType: 2,
      refundType: 1,
      ...overrides,
    },
  };
}

function message(notification, overrides = {}) {
  return {
    messageId: MESSAGE_ID,
    publishTime: "2027-01-15T12:00:00.000Z",
    data: Buffer.from(JSON.stringify(notification), "utf8").toString("base64"),
    ...overrides,
  };
}

/** Запись расчёта в том виде, в каком её оставило начисление. */
function settlement(overrides = {}) {
  return {
    settlementId: ID,
    uid: UID,
    status: STATUS_CREDITED,
    sku: SKU,
    orderId: "GPA.3333-4444-5555-66666",
    quantity: 1,
    goldPerUnit: 60,
    goldGranted: 60,
    goldBefore: 0,
    goldAfter: 60,
    constantsDoc: ECONOMY_CONSTANTS_DOC,
    constantsVersion: 7,
    isTest: false,
    settledAtMs: NOW - 1000,
    ...overrides,
  };
}

function playPurchase(overrides = {}) {
  return readProductPurchase({
    kind: "androidpublisher#productPurchase",
    purchaseTimeMillis: "1799999000000",
    purchaseState: 1,
    consumptionState: 1,
    orderId: "GPA.3333-4444-5555-66666",
    productId: SKU,
    quantity: 1,
    ...overrides,
  });
}

function decide(overrides = {}) {
  return decideClawback({settlement: settlement(), purchase: playPurchase(), uid: UID, now: NOW, ...overrides});
}

function testAMessageIsReadFromItsBase64PayloadOrNotAtAll() {
  assert.deepStrictEqual(decodeNotification(message(voided())), voided());

  // Нечитаемое — это `null`, а не догадка: дальше такое сообщение записывается как нечитаемое и
  // подтверждается, потому что повторять его нечем.
  assert.strictEqual(decodeNotification(null), null, "сообщения нет");
  assert.strictEqual(decodeNotification({messageId: MESSAGE_ID}), null, "поля data нет");
  assert.strictEqual(decodeNotification({data: ""}), null, "поле пустое");
  assert.strictEqual(decodeNotification({data: 42}), null, "поле не строка");
  assert.strictEqual(decodeNotification({data: "!!!!"}), null, "не base64");
  assert.strictEqual(
    decodeNotification({data: Buffer.from("not json at all", "utf8").toString("base64")}), null, "не JSON",
  );
  assert.strictEqual(
    decodeNotification({data: Buffer.from("[1,2,3]", "utf8").toString("base64")}), null, "JSON, но не объект",
  );
  assert.strictEqual(
    decodeNotification({data: Buffer.from(JSON.stringify("строка"), "utf8").toString("base64")}), null,
    "JSON-строка — не объект",
  );
}

function testTheKindDecidesWhetherAnythingIsTakenBack() {
  // Только возврат ведёт к снятию. Покупка — это уведомление о том, что устройство и так предъявит
  // само; второй путь к деньгам не заводится.
  assert.strictEqual(notificationKind(voided()), NOTIFICATION_VOID);
  assert.strictEqual(
    notificationKind({packageName: PACKAGE, oneTimeProductNotification: {notificationType: 2, purchaseToken: TOKEN, sku: SKU}}),
    NOTIFICATION_VOID, "отменённый одноразовый товар — тот же возврат",
  );
  assert.strictEqual(
    notificationKind({packageName: PACKAGE, oneTimeProductNotification: {notificationType: 1, purchaseToken: TOKEN, sku: SKU}}),
    NOTIFICATION_PURCHASED,
  );
  assert.strictEqual(notificationKind({packageName: PACKAGE, testNotification: {version: "1.0"}}), NOTIFICATION_TEST);

  // Подписок мы не продаём, а незнакомое поле — не повод гадать.
  assert.strictEqual(notificationKind({packageName: PACKAGE, subscriptionNotification: {}}), NOTIFICATION_UNKNOWN);
  assert.strictEqual(
    notificationKind({packageName: PACKAGE, oneTimeProductNotification: {notificationType: 99}}), NOTIFICATION_UNKNOWN,
  );
  assert.strictEqual(notificationKind({packageName: PACKAGE}), NOTIFICATION_UNKNOWN);
  assert.strictEqual(notificationKind(null), NOTIFICATION_UNKNOWN);
  assert.strictEqual(notificationKind("voided"), NOTIFICATION_UNKNOWN);
}

function testTheTokenAndThePackageAreReadWithoutGuessing() {
  assert.strictEqual(tokenOf(voided()), TOKEN);
  assert.strictEqual(
    tokenOf({oneTimeProductNotification: {notificationType: 2, purchaseToken: TOKEN}}), TOKEN,
  );
  assert.strictEqual(tokenOf(voided({purchaseToken: undefined})), "", "токена нет");
  assert.strictEqual(tokenOf(voided({purchaseToken: 12345})), "", "токен не строка");
  assert.strictEqual(tokenOf(voided({purchaseToken: "a".repeat(MAX_TOKEN_LENGTH + 1)})), "", "мегабайт — не токен");
  assert.strictEqual(tokenOf(null), "");

  assert.strictEqual(packageOf(voided()), PACKAGE);
  assert.strictEqual(packageOf({packageName: "com.example.other"}), "com.example.other");
  assert.strictEqual(packageOf(null), "");
}

function testNothingIsTakenBackFromATokenThatWasNeverCredited() {
  // Возврат покупки, за которую мы ничего не давали: отбирать нечего, и это не ошибка.
  const verdict = decide({settlement: null});
  assert.strictEqual(verdict.decision, CLAWBACK_NOTHING_TO_TAKE);
  assert.strictEqual(verdict.clawback, undefined, "решение без снятия ничего не считает");
  assert.strictEqual(settlementClawbackGate(null, null).decision, CLAWBACK_NOTHING_TO_TAKE);
}

function testASettlementAlreadyRefundedIsNotRefundedTwice() {
  // Два разных сообщения об одном токене: id сообщения их не связывает, а состояние записи — да.
  const already = settlement({status: STATUS_REFUNDED});
  assert.strictEqual(decide({settlement: already}).decision, CLAWBACK_ALREADY_REFUNDED);
  assert.strictEqual(settlementClawbackGate(already, UID).decision, CLAWBACK_ALREADY_REFUNDED);
  // Именно состояние, а не ответ Play: Play к этому моменту отвечает то же самое, что и в первый раз.
  assert.strictEqual(decide({settlement: already, purchase: null}).decision, CLAWBACK_ALREADY_REFUNDED);

  // Состояние, которого не бывает, — тоже не повод снимать.
  assert.strictEqual(decide({settlement: settlement({status: "PENDING"})}).decision, CLAWBACK_NOTHING_TO_TAKE);
}

function testNothingIsTakenBackWhilePlayStillCallsThePurchaseBought() {
  // Уведомление — повод спросить, а не вердикт. Отобрать оплаченное дороже, чем не отобрать вовремя.
  const bought = decide({purchase: playPurchase({purchaseState: 0, consumptionState: 0})});
  assert.strictEqual(bought.decision, CLAWBACK_PLAY_DISAGREES);
  assert.strictEqual(bought.clawback, undefined);

  // Состояние, которое не читается, — тоже не подтверждение возврата.
  assert.strictEqual(decide({purchase: playPurchase({purchaseState: "?"})}).decision, CLAWBACK_PLAY_DISAGREES);
  assert.strictEqual(decide({purchase: playPurchase({purchaseState: undefined})}).decision, CLAWBACK_PLAY_DISAGREES);
}

function testAVoidConfirmedByPlayTakesBackWhatTheSettlementSaysWasGranted() {
  // Сумма — из записи расчёта. В уведомлении её нет, и её появление там ничего бы не изменило.
  const verdict = decide();
  assert.strictEqual(verdict.decision, CLAWBACK_CLAW_BACK);
  assert.deepStrictEqual(verdict.clawback, {
    uid: UID,
    settlementId: ID,
    sku: SKU,
    orderId: "GPA.3333-4444-5555-66666",
    goldGranted: 60,
    goldToClawBack: 60,
    quantity: 1,
    purchaseQuantity: 1,
    grantedQuantity: 1,
    priorRefundedQuantity: 0,
    refundedQuantity: 1,
    refundQuantity: 1,
    refundableQuantity: 0,
    refundDocQuantity: undefined,
    goldPerUnit: 60,
    creditedConstantsVersion: 7,
    isTest: false,
    purchaseState: 1,
    atMs: NOW,
  });

  // Токен, которого Play больше не знает, — тоже подтверждение: возврат мог стереть его из API.
  const forgotten = decide({purchase: null});
  assert.strictEqual(forgotten.decision, CLAWBACK_CLAW_BACK);
  assert.strictEqual(forgotten.clawback.purchaseState, null);
  assert.strictEqual(forgotten.clawback.goldGranted, 60);

  // Чужая запись между чтением и транзакцией: баланс пишется не тому, чью запись читали.
  assert.strictEqual(decide({uid: "someone-else"}).decision, CLAWBACK_NOTHING_TO_TAKE);
  // До того, как владелец известен, проверяется только состояние.
  assert.strictEqual(settlementClawbackGate(settlement(), null), null);
}

function testTheRecordsOfARefundNameWhatTheAuditNeeds() {
  const clawback = decide().clawback;
  const taken = {
    clawback,
    notificationId: MESSAGE_ID,
    goldBefore: 200,
    goldAfter: 140,
    shortfallAdded: 0,
    shortfallAfter: 0,
    constantsDoc: ECONOMY_CONSTANTS_DOC,
    constantsVersion: 9,
    now: NOW,
  };

  // Запись расчёта помечается, а не переписывается: доказательство выданного остаётся.
  const update = refundSettlementUpdate(taken);
  assert.strictEqual(update.status, STATUS_REFUNDED);
  assert.strictEqual(update.goldTakenBack, 60);
  assert.strictEqual(update.shortfallAdded, 0);
  assert.strictEqual(update.refundNotificationId, MESSAGE_ID);
  assert.strictEqual(update.lastRefundNotificationId, MESSAGE_ID);
  assert.strictEqual(update.refundedQuantity, 1);
  assert.strictEqual(update.lastObservedRefundableQuantity, 0);
  assert.strictEqual(update.refundRevision, 0);
  assert.strictEqual(update.refundedAtMs, NOW);
  assert.strictEqual(update.goldGranted, undefined, "суммы начисления запись не переписывает");
  assert.strictEqual(update.uid, undefined);

  // Квитанция — ключ и параметры, не текст: язык устройства сервер не знает.
  const receipt = refundReceiptRecord(taken);
  assert.strictEqual(receipt.receiptId, `${ID}_refund`);
  assert.strictEqual(receipt.receiptId, refundDocId(ID));
  assert.notStrictEqual(receipt.receiptId, ID, "квитанция о возврате ложится рядом, а не поверх");
  assert.strictEqual(receipt.kind, RECEIPT_KIND_PURCHASE_REFUNDED);
  assert.strictEqual(receipt.messageKey, RECEIPT_MESSAGE_GOLD_PACK_REFUNDED);
  assert.strictEqual(receipt.amount, -60, "движение отрицательное — его и было");
  assert.deepStrictEqual(receipt.params, {sku: SKU, gold: 60, shortfall: 0, refundQuantity: 1, refundedQuantity: 1});
  assert.strictEqual(receipt.uid, UID);
  assert.strictEqual(JSON.stringify(receipt).includes(TOKEN), false, "сырого токена в квитанции нет");

  // Аудит называет обе версии таблицы: под какой начисляли и какая действует сейчас.
  const audit = refundAuditRecord(taken);
  assert.strictEqual(audit.auditId, `${ID}_refund`);
  assert.strictEqual(audit.action, AUDIT_ACTION_PURCHASE_REFUND);
  assert.strictEqual(audit.constantsDoc, ECONOMY_CONSTANTS_DOC);
  assert.strictEqual(audit.constantsVersion, 9);
  assert.strictEqual(audit.creditedConstantsVersion, 7);
  assert.strictEqual(audit.goldGranted, 60);
  assert.strictEqual(audit.purchaseQuantity, 1);
  assert.strictEqual(audit.refundQuantity, 1);
  assert.strictEqual(audit.refundedQuantity, 1);
  assert.strictEqual(audit.goldTakenBack, 60);
  assert.strictEqual(audit.goldBefore, 200);
  assert.strictEqual(audit.goldAfter, 140);
  assert.strictEqual(audit.notificationId, MESSAGE_ID);
  assert.strictEqual(JSON.stringify(audit).includes(TOKEN), false, "сырого токена в аудите нет");

  // То же снятие, но игрок уже потратил: баланс встаёт на ноль, остаток — недостача.
  const spent = refundAuditRecord({...taken, goldBefore: 20, goldAfter: 0, shortfallAdded: 40, shortfallAfter: 40});
  assert.strictEqual(spent.goldTakenBack, 20);
  assert.strictEqual(spent.shortfallAdded, 40);
  assert.strictEqual(spent.shortfallAfter, 40);
  assert.strictEqual(refundReceiptRecord({...taken, goldBefore: 20, goldAfter: 0, shortfallAdded: 40}).params.shortfall, 40);
}

function testPartialDecisionUsesOnlyTheV2CumulativeQuantity() {
  const current = settlement({
    schemaVersion: 2,
    quantity: 5,
    purchaseQuantity: 5,
    grantedQuantity: 5,
    refundedQuantity: 2,
    lastObservedRefundableQuantity: 3,
    refundRevision: 1,
    goldPerUnit: 60,
    goldGranted: 300,
  });
  const partial = decidePartialClawback({
    settlement: current,
    uid: UID,
    now: NOW,
    refundProof: {purchaseQuantity: 5, refundedQuantity: 3},
  });
  assert.strictEqual(partial.decision, CLAWBACK_CLAW_BACK);
  assert.strictEqual(partial.clawback.goldToClawBack, 60);
  assert.strictEqual(partial.clawback.refundDocQuantity, 3);
  assert.strictEqual(
    decidePartialClawback({settlement: current, uid: UID, now: NOW,
      refundProof: {purchaseQuantity: 5, refundedQuantity: 2}}).decision,
    CLAWBACK_ALREADY_RECONCILED,
  );
  assert.strictEqual(
    decidePartialClawback({settlement: current, uid: UID, now: NOW,
      refundProof: {purchaseQuantity: 5, refundedQuantity: 1}}).decision,
    CLAWBACK_PLAY_DISAGREES,
  );
}

function testTheRecordOfAMessageSaysWhatArrivedAndHowItEnded() {
  const record = notificationRecord({
    messageId: MESSAGE_ID, notification: voided(), kind: NOTIFICATION_VOID, result: "CLAWED_BACK",
    settlementId: ID, sku: SKU, uid: UID, publishTimeMs: 1_800_000_100_000, now: NOW, detail: "confirmed",
  });

  assert.strictEqual(record.messageId, MESSAGE_ID);
  assert.strictEqual(record.kind, NOTIFICATION_VOID);
  assert.strictEqual(record.result, "CLAWED_BACK");
  assert.strictEqual(record.packageName, PACKAGE);
  assert.strictEqual(record.settlementId, ID, "id расчёта — он же хеш токена");
  assert.strictEqual(record.refundType, 1);
  assert.strictEqual(record.productType, 2);
  assert.strictEqual(record.notificationType, null, "поля другого вида уведомления нет");
  assert.strictEqual(record.eventTimeMs, 1_799_999_900_000, "int64 приезжает строкой");
  assert.strictEqual(record.publishTimeMs, 1_800_000_100_000);
  assert.strictEqual(record.recordedAtMs, NOW);
  assert.strictEqual(JSON.stringify(record).includes(TOKEN), false, "сырого токена в записи нет");

  // Нечитаемое сообщение тоже записывается: «уведомление пришло, и ничего не произошло» должно
  // отличаться от «уведомление не приходило».
  const unreadable = notificationRecord({
    messageId: MESSAGE_ID, notification: null, kind: NOTIFICATION_UNKNOWN, result: "UNREADABLE",
    publishTimeMs: 0, now: NOW, detail: "not a JSON object",
  });
  assert.strictEqual(unreadable.packageName, "");
  assert.strictEqual(unreadable.settlementId, "");
  assert.strictEqual(unreadable.eventTimeMs, 0);
  assert.strictEqual(unreadable.detail, "not a JSON object");
}

function testTheTriggerIsWiredTheWayTheSpecSays() {
  // index.js при загрузке поднимает firebase-admin, поэтому проверяется исходник — та же техника,
  // что в purchase-verification.test.js. Путь денег целиком исполняется в purchase-clawback.test.js.
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("exports.playPurchaseNotifications = onMessagePublished(");
  assert.ok(start > 0, "обработчик — подписчик топика, а не callable");
  assert.match(source, /const PLAY_RTDN_TOPIC = "play-rtdn";/);
  assert.match(source, /require\("firebase-functions\/v2\/pubsub"\)/);

  const body = source.slice(start, source.indexOf("\nexports.", start + 1));
  assert.ok(body.includes("MONETARY_FUNCTION_OPTIONS"), "свой предел инстансов, не глобальная единица");
  assert.ok(body.includes("topic: PLAY_RTDN_TOPIC"), "имя топика — константа модуля, не секрет");
  assert.ok(body.includes("retry: true"), "недоставленное сообщение обязано приехать снова");
  assert.ok(body.includes("readSubscriptionRenewal("), "продление опознаётся до возвратов (история 2.3)");
  assert.ok(body.includes("settleSubscriptionRenewal("), "продление идёт тем же расчётом, что и чек");
  assert.ok(body.includes("clawBackPurchase("), "триггер делегирует purchase-clawback.js");
  assert.ok(!body.includes("increment("), "золото — абсолютом, не increment");
  assert.ok(!body.includes("acknowledge") && !body.includes("consume"), "этот путь у Play только читает");
}

function testARenewalIsReadFromTermMovingSubscriptionTypesOnly() {
  // История 2.3: продление — тот же расчёт по другому поводу. Токен читается только из
  // subscriptionNotification с типом, способным сдвинуть срок вперёд; остальные типы, возвраты
  // и одно-товарные уведомления продлениями не являются.
  const renewed = (type, overrides = {}) => ({
    version: "1.0", packageName: PACKAGE, eventTimeMillis: String(NOW),
    subscriptionNotification: {version: "1.0", notificationType: type, purchaseToken: TOKEN, subscriptionId: "premium_30d", ...overrides},
  });
  for (const type of [1, 2, 7]) {
    assert.strictEqual(readSubscriptionRenewal(renewed(type)), TOKEN, `type ${type} двигает срок`);
  }
  for (const type of [3, 4, 5, 6, 12, 13, 20]) {
    assert.strictEqual(readSubscriptionRenewal(renewed(type)), "", `type ${type} срок не двигает`);
  }
  assert.strictEqual(readSubscriptionRenewal(voided()), "", "возврат — не продление");
  assert.strictEqual(readSubscriptionRenewal({subscriptionNotification: {notificationType: 2}}), "",
    "токена нет — читать нечего");
  assert.strictEqual(readSubscriptionRenewal({subscriptionNotification: {notificationType: 2, purchaseToken: "x".repeat(MAX_TOKEN_LENGTH + 1)}}), "",
    "токен длиннее потолка — не токен");
  assert.strictEqual(readSubscriptionRenewal(null), "");
}

function testTheCollectionIsTheOneTheRulesClose() {
  assert.strictEqual(PURCHASE_NOTIFICATIONS_COLLECTION, "purchase_notifications");
  const rules = fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8");
  const start = rules.indexOf(`match /${PURCHASE_NOTIFICATIONS_COLLECTION}/`);
  assert.ok(start > 0, "коллекция названа в правилах");
  assert.match(rules.slice(start, rules.indexOf("\n    }", start)), /allow read, write: if false;/);
}

testAMessageIsReadFromItsBase64PayloadOrNotAtAll();
testTheKindDecidesWhetherAnythingIsTakenBack();
testTheTokenAndThePackageAreReadWithoutGuessing();
testNothingIsTakenBackFromATokenThatWasNeverCredited();
testASettlementAlreadyRefundedIsNotRefundedTwice();
testNothingIsTakenBackWhilePlayStillCallsThePurchaseBought();
testAVoidConfirmedByPlayTakesBackWhatTheSettlementSaysWasGranted();
testTheRecordsOfARefundNameWhatTheAuditNeeds();
testPartialDecisionUsesOnlyTheV2CumulativeQuantity();
testTheRecordOfAMessageSaysWhatArrivedAndHowItEnded();
testTheTriggerIsWiredTheWayTheSpecSays();
testARenewalIsReadFromTermMovingSubscriptionTypesOnly();
testTheCollectionIsTheOneTheRulesClose();

console.log("purchase-notifications.test.js OK");
