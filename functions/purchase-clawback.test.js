"use strict";

const assert = require("assert");
const {
  CLAWBACK_OUTCOME_CLAWED_BACK,
  CLAWBACK_OUTCOME_IGNORED,
  CLAWBACK_OUTCOME_RECORDED,
  CLAWBACK_OUTCOME_UNREADABLE,
  PLAY_DISAGREES_HINT,
  clawBackPurchase,
} = require("./purchase-clawback");
const {
  CLAWBACK_ALREADY_REFUNDED,
  CLAWBACK_NOTHING_TO_TAKE,
  CLAWBACK_PLAY_DISAGREES,
  NOTIFICATION_VOID,
  refundDocId,
} = require("./purchase-notifications");
const {
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
  SHORTFALL_FIELD,
  STATUS_CREDITED,
  STATUS_REFUNDED,
  settlementId,
} = require("./purchase-verification");
const {ACCESS_REFUSED_HINT} = require("./purchase-settlement");
const {ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

/**
 * Путь возврата целиком — с той же Firestore-подделкой, что и у начисления: записи копятся и
 * фиксируются только если тело транзакции дошло до конца. Упавшая транзакция не пишет ничего.
 */
const NOW = 1_800_000_000_000;
const UID = "player-1";
const TOKEN = "hlmbchfnkkjlbhdejkjhkkbo.AO-J1OyExampleTokenValue-1234567890";
const SKU = "gold_pack_small";
const PACKAGE = "com.tpov.schoolquiz";
const MESSAGE_ID = "9382173817231";
const ID = settlementId(TOKEN);
const USER_PATH = `users/${UID}`;
const SETTLEMENT_PATH = `purchase_settlements/${ID}`;
const RECEIPT_PATH = `users/${UID}/receipts/${refundDocId(ID)}`;
const AUDIT_PATH = `purchase_audit/${refundDocId(ID)}`;
const NOTIFICATION_PATH = `purchase_notifications/${MESSAGE_ID}`;
const REVOCATION_PATH = `purchase_revocations/${ID}`;
const LEDGER_ENTRY_PATH = `${LEDGER_PATH}/${UID}_${CURRENCY.GOLD}_${REASON.PURCHASE_REFUND}_${ID}`;
const CONSTANTS_DOC = {version: 9, goldPacks: {gold_pack_small: 12}};

function voidedNotification(overrides = {}) {
  return {
    version: "1.0",
    packageName: PACKAGE,
    eventTimeMillis: "1799999900000",
    voidedPurchaseNotification: {
      purchaseToken: TOKEN,
      orderId: "GPA.3333-4444-5555-66666",
      productType: 2,
      refundType: 1,
    },
    ...overrides,
  };
}

function message(notification, overrides = {}) {
  return {
    messageId: MESSAGE_ID,
    publishTime: "2027-01-15T12:00:00.000Z",
    data: notification === null ? undefined : Buffer.from(JSON.stringify(notification), "utf8").toString("base64"),
    ...overrides,
  };
}

/** Запись расчёта в том виде, в каком её оставило начисление. */
function creditedSettlement(overrides = {}) {
  return {
    settlementId: ID,
    uid: UID,
    status: STATUS_CREDITED,
    sku: SKU,
    orderId: "GPA.3333-4444-5555-66666",
    quantity: 1,
    goldPerUnit: 60,
    goldGranted: 60,
    goldBefore: 140,
    goldAfter: 200,
    constantsDoc: ECONOMY_CONSTANTS_DOC,
    constantsVersion: 7,
    isTest: false,
    settledAtMs: NOW - 1000,
    response: {status: STATUS_CREDITED, sku: SKU, goldGranted: 60, gold: 200, settlementId: ID},
  };
}

function playJson(overrides = {}) {
  return {
    kind: "androidpublisher#productPurchase",
    purchaseTimeMillis: "1799999000000",
    purchaseState: 1,
    consumptionState: 1,
    orderId: "GPA.3333-4444-5555-66666",
    productId: SKU,
    quantity: 1,
    ...overrides,
  };
}

function found(overrides = {}) {
  return {outcome: PLAY_OUTCOME_FOUND, status: 200, code: null, reason: null, purchase: playJson(overrides)};
}

function fakeDb({docs = {}, beforeTransaction = null, transactionFails = false} = {}) {
  const store = new Map(Object.entries(docs));
  const writes = [];
  let transactions = 0;

  const snapshot = (docPath) => {
    const data = store.get(docPath);
    return {exists: data !== undefined, data: () => data};
  };
  const docRef = (docPath) => ({
    path: docPath,
    async get() {
      return snapshot(docPath);
    },
    async set(data, options) {
      writes.push({op: "set", path: docPath, data, options});
      store.set(docPath, options?.merge ? {...store.get(docPath), ...data} : data);
    },
    collection(name) {
      return collectionRef(`${docPath}/${name}`);
    },
  });
  const collectionRef = (collectionPath) => ({
    doc(id) {
      return docRef(`${collectionPath}/${id}`);
    },
  });

  return {
    writes,
    get transactions() {
      return transactions;
    },
    collection: collectionRef,
    doc: docRef,
    async runTransaction(body) {
      transactions += 1;
      if (beforeTransaction) beforeTransaction(store);
      const pending = [];
      const transaction = {
        async getAll(...refs) {
          assert.strictEqual(pending.length, 0, "all reads precede writes");
          return refs.map((ref) => snapshot(ref.path));
        },
        set(ref, data, options) {
          if (transactionFails) throw new Error("10 ABORTED: Too much contention");
          pending.push({op: "set", path: ref.path, data, options});
        },
      };
      const result = await body(transaction);
      writes.push(...pending);
      for (const write of pending) store.set(write.path,
        write.options?.merge ? {...store.get(write.path), ...write.data} : write.data);
      return result;
    },
  };
}

function fakePlay(lookup) {
  const calls = [];
  return {
    packageName: PACKAGE,
    calls,
    async getProductPurchase(sku, purchaseToken) {
      calls.push({sku, purchaseToken});
      return lookup;
    },
    async getProductPurchaseByToken(purchaseToken) {
      calls.push({purchaseToken});
      return {outcome: PLAY_OUTCOME_FOUND, purchase: {orderId: "GPA.3333-4444-5555-66666",
        productLineItem: [{productId: SKU}], purchaseStateContext: {purchaseState: "CANCELLED"}}};
    },
  };
}

function fakeLog() {
  const entries = {warn: [], error: []};
  return {
    entries,
    warn: (text, context) => entries.warn.push({message: text, context}),
    error: (text, context) => entries.error.push({message: text, context}),
  };
}

/** Как index.js читает золото: `readEconomyBalance(data).gold` — целое, не меньше нуля. */
function readGold(data) {
  const parsed = Number(data && data.gold);
  return Number.isFinite(parsed) ? Math.trunc(Math.max(0, parsed)) : 0;
}

async function clawBack({db, play = fakePlay(found()), msg = message(voidedNotification())}) {
  const log = fakeLog();
  const outcome = await clawBackPurchase({db, playApi: play, now: NOW, message: msg, readGold, log});
  return {outcome, log, db, play};
}

function byPath(writes) {
  return Object.fromEntries(writes.map((write) => [write.path, write]));
}

async function testAVoidedPurchaseTakesTheGoldBackAndMarksTheSettlementInOneTransaction() {
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 200, nolics: 3},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const {outcome, log, play} = await clawBack({db});

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_CLAWED_BACK);
  assert.strictEqual(outcome.goldTakenBack, 60);
  assert.strictEqual(outcome.shortfall, 0);
  assert.deepStrictEqual(play.calls, [{sku: SKU, purchaseToken: TOKEN}], "SKU берётся из записи расчёта");
  assert.strictEqual(db.transactions, 2, "durable ingress fence, then atomic economic refund");

  assert.strictEqual(db.writes.length, 8, "pending fence; confirmed fence with balance, receipt and audit");
  const written = byPath(db.writes);
  assert.deepStrictEqual(written[USER_PATH].data, {
    uid: UID, gold: 140, [SHORTFALL_FIELD]: 0, updatedAtMs: NOW,
  });
  assert.strictEqual(written[SETTLEMENT_PATH].data.status, STATUS_REFUNDED);
  assert.strictEqual(written[SETTLEMENT_PATH].data.goldTakenBack, 60);
  assert.deepStrictEqual(written[SETTLEMENT_PATH].options, {merge: true}, "запись о начислении остаётся");
  assert.strictEqual(written[SETTLEMENT_PATH].data.goldGranted, undefined);
  assert.strictEqual(written[RECEIPT_PATH].data.amount, -60);
  assert.notStrictEqual(RECEIPT_PATH, `users/${UID}/receipts/${ID}`, "квитанция ложится рядом, не поверх");
  assert.strictEqual(written[AUDIT_PATH].data.constantsDoc, ECONOMY_CONSTANTS_DOC);
  assert.strictEqual(written[AUDIT_PATH].data.constantsVersion, 9, "версия таблицы на момент возврата");
  assert.strictEqual(written[AUDIT_PATH].data.creditedConstantsVersion, 7, "и та, под которой начисляли");
  assert.strictEqual(written[LEDGER_ENTRY_PATH].data.delta, -60);
  assert.strictEqual(written[LEDGER_ENTRY_PATH].data.reason, REASON.PURCHASE_REFUND);
  assert.strictEqual(written[NOTIFICATION_PATH].data.result, CLAWBACK_OUTCOME_CLAWED_BACK);
  assert.strictEqual(written[NOTIFICATION_PATH].data.kind, NOTIFICATION_VOID);
  assert.strictEqual(JSON.stringify(db.writes).includes(TOKEN), false, "сырой токен не пишется");
  assert.deepStrictEqual(log.entries.error, []);
}

async function testGoldAlreadySpentStopsAtZeroAndTheRestIsRecordedAsOwed() {
  // Баланс не уходит в минус (ADM-19): невозвращаемое становится недостачей, которую погасит
  // следующее начисление.
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 20},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const {outcome} = await clawBack({db});

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_CLAWED_BACK);
  assert.strictEqual(outcome.goldTakenBack, 20);
  assert.strictEqual(outcome.shortfall, 40);
  const written = byPath(db.writes);
  assert.deepStrictEqual(written[USER_PATH].data, {uid: UID, gold: 0, [SHORTFALL_FIELD]: 40, updatedAtMs: NOW});
  assert.strictEqual(written[LEDGER_ENTRY_PATH].data.delta, -20, "книга пишет настоящее движение");
  assert.strictEqual(written[LEDGER_ENTRY_PATH].data.details.goldGranted, 60, "а выданное названо рядом");
  assert.strictEqual(written[LEDGER_ENTRY_PATH].data.details.shortfallAdded, 40);
  assert.strictEqual(written[AUDIT_PATH].data.shortfallAfter, 40);
  assert.strictEqual(written[RECEIPT_PATH].data.params.shortfall, 40);
}

async function testASecondRefundOnAnAccountThatAlreadyOwesAddsToTheDebt() {
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 0, [SHORTFALL_FIELD]: 15},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const {outcome} = await clawBack({db});

  assert.strictEqual(outcome.shortfall, 75, "долги складываются, а не заменяют друг друга");
  const written = byPath(db.writes);
  assert.deepStrictEqual(written[USER_PATH].data, {uid: UID, gold: 0, [SHORTFALL_FIELD]: 75, updatedAtMs: NOW});
  assert.strictEqual(written[LEDGER_ENTRY_PATH], undefined, "баланс не двигался — движения не было");
  assert.strictEqual(db.writes.length, 7);
}

async function testTheSameMessageAgainWritesNothingAndAnswersTheSame() {
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 140},
      [SETTLEMENT_PATH]: {...creditedSettlement(), status: STATUS_REFUNDED},
      [NOTIFICATION_PATH]: {messageId: MESSAGE_ID, result: CLAWBACK_OUTCOME_CLAWED_BACK},
    },
  });

  const {outcome, play} = await clawBack({db});

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_CLAWED_BACK, "тот же ответ");
  assert.strictEqual(outcome.replayed, true);
  assert.deepStrictEqual(db.writes, [], "ничего не записано");
  assert.deepStrictEqual(play.calls, [], "Play не спрашивается");
  assert.strictEqual(db.transactions, 0);
}

async function testASecondNotificationAboutTheSameTokenTakesNothingBackTwice() {
  // Разные id сообщений, один токен: сторож — состояние записи расчёта, а не ключ сообщения.
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 140},
      [SETTLEMENT_PATH]: {...creditedSettlement(), status: STATUS_REFUNDED},
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const {outcome, play} = await clawBack({db, msg: message(voidedNotification(), {messageId: "another-message"})});

  assert.strictEqual(outcome.kind, CLAWBACK_ALREADY_REFUNDED);
  assert.deepStrictEqual(play.calls, [], "уже возвращённое не перепроверяется");
  assert.strictEqual(db.writes.length, 2, "confirmed fence upgrade plus notification");
  assert.strictEqual(db.writes[1].path, "purchase_notifications/another-message");
  assert.strictEqual(db.writes[1].data.result, CLAWBACK_ALREADY_REFUNDED);
  assert.strictEqual(db.transactions, 1);
}

async function testARaceThatRefundsBetweenTheReadAndTheTransactionTakesNothingBack() {
  // Два сообщения об одном токене приехали одновременно: оба прошли предварительное чтение, первое
  // записало. Второе читает запись уже внутри транзакции и не снимает ничего.
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 140},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
    beforeTransaction: (store) => store.set(SETTLEMENT_PATH, {...creditedSettlement(), status: STATUS_REFUNDED}),
  });

  const {outcome} = await clawBack({db});

  assert.strictEqual(outcome.kind, CLAWBACK_ALREADY_REFUNDED);
  assert.strictEqual(db.transactions, 1);
  assert.strictEqual(db.writes.length, 2, "confirmed fence upgrade plus notification; no monetary effect");
  assert.strictEqual(db.writes[1].path, NOTIFICATION_PATH);
}

async function testATokenThatWasNeverCreditedIsRecordedAndNothingElse() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 200}}});

  const {outcome, play} = await clawBack({db});

  assert.strictEqual(outcome.kind, CLAWBACK_NOTHING_TO_TAKE);
  assert.deepStrictEqual(play.calls, [{purchaseToken: TOKEN}], "token-only V2 resolves SKU-less RTDN");
  assert.strictEqual(db.writes.length, 3, "pending fence then confirmed fence and notice");
  assert.strictEqual(db.writes[2].path, NOTIFICATION_PATH);
  assert.strictEqual(db.writes[2].data.result, CLAWBACK_NOTHING_TO_TAKE);
  assert.strictEqual(db.transactions, 2);
}

async function testNothingIsTakenBackWhilePlayStillReportsThePurchaseAsBought() {
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 200},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const log = fakeLog();
  await assert.rejects(clawBackPurchase({db, playApi: fakePlay(found({purchaseState: 0, consumptionState: 0})),
    now: NOW, message: message(voidedNotification()), readGold, log}), /redeliver/);
  assert.strictEqual(db.writes.length, 1, "pending fence only; no monetary effect or terminal memoization");
  assert.strictEqual(db.writes[0].path, REVOCATION_PATH);
  assert.strictEqual(db.writes[0].data.state, "PENDING_VERIFICATION");
  // Уведомление против ответа Play — расхождение, которое разбирает человек, а не код.
  assert.strictEqual(log.entries.error.length, 1);
  assert.ok(log.entries.error[0].message.includes(PLAY_DISAGREES_HINT));
}

async function testAnUnknownTokenIsProofEnoughToTakeTheGoldBack() {
  // Возврат может стереть покупку из API. Play, не знающий токена, — подтверждение, а не отказ.
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 200},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
  });

  const {outcome} = await clawBack({
    db, play: fakePlay({outcome: PLAY_OUTCOME_NOT_FOUND, status: 404, code: null, reason: null}),
  });

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_CLAWED_BACK);
  assert.strictEqual(byPath(db.writes)[USER_PATH].data.gold, 140);
}

async function testAnUnreachablePlayWritesNothingAndAsksForTheMessageAgain() {
  const docs = {
    [USER_PATH]: {gold: 200},
    [SETTLEMENT_PATH]: creditedSettlement(),
    [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
  };

  const outage = fakeDb({docs});
  await assert.rejects(
    clawBack({db: outage, play: fakePlay({outcome: PLAY_OUTCOME_UNAVAILABLE, status: 503, code: null, reason: null})}),
    /redeliver/,
    "без вердикта сообщение не подтверждается",
  );
  assert.strictEqual(outage.writes.length, 1, "only pending token fence, no acknowledged notification");
  assert.strictEqual(outage.writes[0].path, REVOCATION_PATH);
  assert.strictEqual(outage.transactions, 1);

  // Отказ в доступе — не покупка виновата, а сервисный аккаунт: ошибка владельцу, не предупреждение.
  for (const status of [401, 403]) {
    const denied = fakeDb({docs});
    const log = fakeLog();
    await assert.rejects(clawBackPurchase({
      db: denied,
      playApi: fakePlay({outcome: PLAY_OUTCOME_UNAVAILABLE, status, code: null, reason: "forbidden"}),
      now: NOW, message: message(voidedNotification()), readGold, log,
    }));
    assert.strictEqual(log.entries.error.length, 1, String(status));
    assert.ok(log.entries.error[0].message.includes(ACCESS_REFUSED_HINT), String(status));
    assert.strictEqual(denied.writes.length, 1, String(status));
    assert.strictEqual(denied.writes[0].path, REVOCATION_PATH);
  }
}

async function testAFailedTransactionWritesNothingAndTheMessageComesBack() {
  const db = fakeDb({
    docs: {
      [USER_PATH]: {gold: 200},
      [SETTLEMENT_PATH]: creditedSettlement(),
      [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC,
    },
    transactionFails: true,
  });
  const log = fakeLog();

  await assert.rejects(clawBackPurchase({
    db, playApi: fakePlay(found()), now: NOW, message: message(voidedNotification()), readGold, log,
  }), /ABORTED/);

  assert.strictEqual(db.transactions, 1);
  assert.deepStrictEqual(db.writes, [], "упавшая транзакция не пишет ничего");
  assert.strictEqual(log.entries.error.length, 1);
  assert.ok(log.entries.error[0].message.includes("nothing was written"));
}

async function testATestNotificationAndANewPurchaseAreRecordedAndNothingElse() {
  const cases = [
    ["test", {packageName: PACKAGE, testNotification: {version: "1.0"}}],
    ["purchased", {
      packageName: PACKAGE,
      oneTimeProductNotification: {version: "1.0", notificationType: 1, purchaseToken: TOKEN, sku: SKU},
    }],
    ["subscription", {packageName: PACKAGE, subscriptionNotification: {notificationType: 3}}],
  ];
  for (const [label, notification] of cases) {
    const db = fakeDb({docs: {[USER_PATH]: {gold: 200}, [SETTLEMENT_PATH]: creditedSettlement()}});
    const play = fakePlay(found());

    const {outcome} = await clawBack({db, play, msg: message(notification)});

    assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_RECORDED, label);
    assert.strictEqual(db.writes.length, 1, label);
    assert.strictEqual(db.writes[0].path, NOTIFICATION_PATH, label);
    assert.deepStrictEqual(play.calls, [], `${label}: Play не спрашивается`);
    assert.strictEqual(db.transactions, 0, label);
  }
}

async function testAnUnreadableMessageIsRecordedOnceAndAcknowledged() {
  // Не подтвердить нечитаемое значит просить Pub/Sub слать его снова каждые несколько секунд.
  const cases = [
    ["no data", message(null)],
    ["not base64", message(null, {data: "!!!!"})],
    ["not json", message(null, {data: Buffer.from("hello", "utf8").toString("base64")})],
    ["not an object", message(null, {data: Buffer.from("[1,2]", "utf8").toString("base64")})],
  ];
  for (const [label, msg] of cases) {
    const db = fakeDb({docs: {[USER_PATH]: {gold: 200}}});

    const {outcome, log} = await clawBack({db, msg});

    assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_UNREADABLE, label);
    assert.strictEqual(db.writes.length, 1, label);
    assert.strictEqual(db.writes[0].data.result, CLAWBACK_OUTCOME_UNREADABLE, label);
    assert.strictEqual(log.entries.warn.length, 1, label);
    assert.deepStrictEqual(log.entries.error, [], `${label}: мусор в топике — не авария`);
  }

  // Возврат без токена читается, но снимать по нему нечего.
  const noToken = fakeDb({docs: {[USER_PATH]: {gold: 200}}});
  const {outcome} = await clawBack({
    db: noToken, msg: message({packageName: PACKAGE, voidedPurchaseNotification: {orderId: "GPA.1"}}),
  });
  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_UNREADABLE);
  assert.strictEqual(noToken.writes.length, 1);
}

async function testANotificationForAnotherPackageIsIgnored() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 200}, [SETTLEMENT_PATH]: creditedSettlement()}});

  const {outcome, log, play} = await clawBack({
    db, msg: message(voidedNotification({packageName: "com.example.other"})),
  });

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_IGNORED);
  assert.deepStrictEqual(db.writes, [], "чужая настройка не заводит у нас документов");
  assert.deepStrictEqual(play.calls, []);
  assert.strictEqual(log.entries.warn.length, 1);
  assert.deepStrictEqual(log.entries.error, []);
}

async function testAMessageWithoutAnIdIsRefusedRatherThanKeyedOnNothing() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 200}}});
  const log = fakeLog();

  const outcome = await clawBackPurchase({
    db, playApi: fakePlay(found()), now: NOW, message: message(voidedNotification(), {messageId: undefined}),
    readGold, log,
  });

  assert.strictEqual(outcome.kind, CLAWBACK_OUTCOME_UNREADABLE);
  assert.deepStrictEqual(db.writes, []);
  assert.strictEqual(log.entries.error.length, 1);
}

(async () => {
  await testAVoidedPurchaseTakesTheGoldBackAndMarksTheSettlementInOneTransaction();
  await testGoldAlreadySpentStopsAtZeroAndTheRestIsRecordedAsOwed();
  await testASecondRefundOnAnAccountThatAlreadyOwesAddsToTheDebt();
  await testTheSameMessageAgainWritesNothingAndAnswersTheSame();
  await testASecondNotificationAboutTheSameTokenTakesNothingBackTwice();
  await testARaceThatRefundsBetweenTheReadAndTheTransactionTakesNothingBack();
  await testATokenThatWasNeverCreditedIsRecordedAndNothingElse();
  await testNothingIsTakenBackWhilePlayStillReportsThePurchaseAsBought();
  await testAnUnknownTokenIsProofEnoughToTakeTheGoldBack();
  await testAnUnreachablePlayWritesNothingAndAsksForTheMessageAgain();
  await testAFailedTransactionWritesNothingAndTheMessageComesBack();
  await testATestNotificationAndANewPurchaseAreRecordedAndNothingElse();
  await testAnUnreadableMessageIsRecordedOnceAndAcknowledged();
  await testANotificationForAnotherPackageIsIgnored();
  await testAMessageWithoutAnIdIsRefusedRatherThanKeyedOnNothing();
  console.log("purchase-clawback.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
