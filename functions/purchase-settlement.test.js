"use strict";

const assert = require("assert");
const {
  ACCESS_REFUSED_HINT,
  SETTLEMENT_OUTCOME_REFUSE,
  SETTLEMENT_OUTCOME_RESPONSE,
  SETTLEMENT_OUTCOME_UNAVAILABLE,
  SKU_NOT_SOLD_HINT,
  settlePurchase,
} = require("./purchase-settlement");
const {
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
  REASON_CONSUMED_WITHOUT_SETTLEMENT,
  REASON_PURCHASE_CANCELED,
  REASON_REQUEST_INVALID,
  REASON_SKU_MISMATCH,
  REASON_SKU_NOT_SOLD,
  REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
  REASON_TOKEN_UNKNOWN,
  STATUS_CREDITED,
  STATUS_PENDING,
  accountIdFor,
  auditRecord,
  decideSettlement,
  readProductPurchase,
  receiptRecord,
  settlementId,
  settlementRecord,
  successResponse,
} = require("./purchase-verification");
const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");

/**
 * Путь денег целиком — с Firestore-подделкой, которая запоминает, что и куда было бы написано.
 *
 * Подделка держит семантику транзакции, которая здесь важна: записи копятся и фиксируются только
 * если тело транзакции дошло до конца. Упавшая транзакция не пишет ничего — ровно так, как
 * настоящая, — и тест «гонка на create» проверяет именно это.
 */
const NOW = 1_800_000_000_000;
const UID = "player-1";
const TOKEN = "hlmbchfnkkjlbhdejkjhkkbo.AO-J1OyExampleTokenValue-1234567890";
const SKU = "gold_pack_small";
const ID = settlementId(TOKEN);
const USER_PATH = `users/${UID}`;
const SETTLEMENT_PATH = `purchase_settlements/${ID}`;
const RECEIPT_PATH = `users/${UID}/receipts/${ID}`;
const AUDIT_PATH = `purchase_audit/${ID}`;
const CONSTANTS_DOC = {version: 7, goldPacks: {gold_pack_small: 12}};

function playJson(overrides = {}) {
  return {
    kind: "androidpublisher#productPurchase",
    purchaseTimeMillis: "1799999000000",
    purchaseState: 0,
    consumptionState: 0,
    orderId: "GPA.3333-4444-5555-66666",
    acknowledgementState: 0,
    productId: SKU,
    quantity: 1,
    regionCode: "UA",
    ...overrides,
  };
}

function found(overrides = {}) {
  return {outcome: PLAY_OUTCOME_FOUND, status: 200, code: null, reason: null, purchase: playJson(overrides)};
}

function fakeDb({docs = {}, beforeTransaction = null, createFails = false} = {}) {
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
          return refs.map((ref) => snapshot(ref.path));
        },
        set(ref, data, options) {
          pending.push({op: "set", path: ref.path, data, options});
        },
        create(ref, data) {
          if (createFails) throw new Error("6 ALREADY_EXISTS: Document already exists");
          pending.push({op: "create", path: ref.path, data});
        },
      };
      const result = await body(transaction);
      writes.push(...pending);
      return result;
    },
  };
}

function fakePlay(lookup) {
  const calls = [];
  return {
    calls,
    async getProductPurchase(sku, purchaseToken) {
      calls.push({sku, purchaseToken});
      return lookup;
    },
  };
}

function fakeLog() {
  const entries = {warn: [], error: []};
  return {
    entries,
    warn: (message, context) => entries.warn.push({message, context}),
    error: (message, context) => entries.error.push({message, context}),
  };
}

/** Как index.js читает золото: `readEconomyBalance(data).gold` — целое, не меньше нуля. */
function readGold(data) {
  const parsed = Number(data && data.gold);
  return Number.isFinite(parsed) ? Math.trunc(Math.max(0, parsed)) : 0;
}

async function settle({db, play, payload = {purchaseToken: TOKEN, sku: SKU}, uid = UID}) {
  const log = fakeLog();
  const outcome = await settlePurchase({db, playApi: play, now: NOW, uid, payload, readGold, log});
  return {outcome, log, db, play};
}

/** Записи, которые транзакция обязана оставить, посчитанные тем же чистым кодом. */
function expectedWrites({goldBefore, purchaseJson = playJson(), constantsDoc = CONSTANTS_DOC}) {
  const purchase = readProductPurchase(purchaseJson);
  const constants = readEconomyConstants(constantsDoc);
  const verdict = decideSettlement({existing: null, purchase, claimedSku: SKU, uid: UID, constants});
  const goldAfter = goldBefore + verdict.credit.goldGranted;
  const response = successResponse({sku: SKU, goldGranted: verdict.credit.goldGranted, gold: goldAfter, settlementId: ID});
  const bag = {
    id: ID, uid: UID, purchase, credit: verdict.credit, goldBefore, goldAfter,
    constantsDoc: ECONOMY_CONSTANTS_DOC, now: NOW,
  };
  return {
    response,
    settlement: settlementRecord({...bag, response}),
    receipt: receiptRecord(bag),
    audit: auditRecord(bag),
  };
}

function storedSettlement() {
  return expectedWrites({goldBefore: 5}).settlement;
}

async function testAVerifiedTokenWritesExactlyFourDocumentsInOneTransaction() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 5, nolics: 3}, [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC}});
  const play = fakePlay(found());

  const {outcome, log} = await settle({db, play});

  const expected = expectedWrites({goldBefore: 5});
  assert.deepStrictEqual(outcome, {kind: SETTLEMENT_OUTCOME_RESPONSE, response: expected.response});
  assert.strictEqual(outcome.response.goldGranted, 12, "сумма из документа констант, не из начальных");
  assert.strictEqual(outcome.response.gold, 17);
  assert.deepStrictEqual(play.calls, [{sku: SKU, purchaseToken: TOKEN}]);
  assert.strictEqual(db.transactions, 1);

  assert.strictEqual(db.writes.length, 4, "ровно четыре записи");
  const byPath = Object.fromEntries(db.writes.map((write) => [write.path, write]));
  assert.deepStrictEqual(byPath[USER_PATH], {
    op: "set", path: USER_PATH, data: {uid: UID, gold: 17, updatedAtMs: NOW}, options: {merge: true},
  });
  assert.deepStrictEqual(byPath[SETTLEMENT_PATH], {op: "create", path: SETTLEMENT_PATH, data: expected.settlement});
  assert.deepStrictEqual(byPath[RECEIPT_PATH], {op: "set", path: RECEIPT_PATH, data: expected.receipt, options: undefined});
  assert.deepStrictEqual(byPath[AUDIT_PATH], {op: "set", path: AUDIT_PATH, data: expected.audit, options: undefined});
  assert.strictEqual(byPath[AUDIT_PATH].data.constantsVersion, 7);
  assert.strictEqual(JSON.stringify(db.writes).includes(TOKEN), false, "сырой токен не пишется");
  assert.deepStrictEqual(log.entries.error, []);
}

async function testAKnownTokenIsAnsweredFromTheGateWithoutAskingPlay() {
  const stored = storedSettlement();
  const db = fakeDb({docs: {[SETTLEMENT_PATH]: stored, [USER_PATH]: {gold: 999}}});
  const play = fakePlay(found());

  const {outcome} = await settle({db, play});

  assert.deepStrictEqual(outcome, {kind: SETTLEMENT_OUTCOME_RESPONSE, response: stored.response});
  assert.strictEqual(outcome.response.gold, 17, "ответ сохранённый — с балансом на момент начисления");
  assert.deepStrictEqual(play.calls, [], "Play не спрашивается");
  assert.strictEqual(db.transactions, 0, "транзакции нет");
  assert.deepStrictEqual(db.writes, []);
}

async function testATokenSettledBetweenTheGateAndTheTransactionReplaysAndWritesNothing() {
  // Два вызова с одним токеном одновременно: оба прошли предварительное чтение, первый записал.
  // Второй читает запись уже внутри транзакции и отвечает повтором, ничего не записывая.
  const stored = storedSettlement();
  const db = fakeDb({
    docs: {[USER_PATH]: {gold: 17}, [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC},
    beforeTransaction: (store) => store.set(SETTLEMENT_PATH, stored),
  });

  const {outcome} = await settle({db, play: fakePlay(found())});

  assert.deepStrictEqual(outcome, {kind: SETTLEMENT_OUTCOME_RESPONSE, response: stored.response});
  assert.strictEqual(db.transactions, 1);
  assert.deepStrictEqual(db.writes, []);
}

async function testAPendingPurchaseWritesNothingAndIsNotAnError() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 5}}});

  const {outcome, log} = await settle({db, play: fakePlay(found({purchaseState: 2}))});

  assert.deepStrictEqual(outcome, {kind: SETTLEMENT_OUTCOME_RESPONSE, response: {status: STATUS_PENDING, sku: SKU}});
  assert.deepStrictEqual(db.writes, []);
  assert.deepStrictEqual(log.entries.error, []);
}

async function testEveryRefusalMapsToTheMatrixCodeAndWritesNothing() {
  const foreign = {...storedSettlement(), uid: "someone-else"};
  const cases = [
    ["canceled", {play: found({purchaseState: 1})}, "failed-precondition", REASON_PURCHASE_CANCELED],
    ["forged", {play: {outcome: PLAY_OUTCOME_NOT_FOUND, status: 404, code: null, reason: null}}, "permission-denied", REASON_TOKEN_UNKNOWN],
    ["sku mismatch", {play: found({productId: "gold_pack_medium"})}, "invalid-argument", REASON_SKU_MISMATCH],
    ["unsold", {play: found({productId: "premium_month"}), payload: {purchaseToken: TOKEN, sku: "premium_month"}}, "invalid-argument", REASON_SKU_NOT_SOLD],
    ["another account's record", {play: found(), docs: {[SETTLEMENT_PATH]: foreign}}, "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT],
    ["another buyer", {play: found({obfuscatedExternalAccountId: accountIdFor("someone-else")})}, "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT],
    ["consumed", {play: found({consumptionState: 1})}, "failed-precondition", REASON_CONSUMED_WITHOUT_SETTLEMENT],
    ["invalid request", {play: found(), payload: {sku: SKU}}, "invalid-argument", REASON_REQUEST_INVALID],
  ];
  for (const [label, {play, payload, docs = {}}, code, reasonCode] of cases) {
    const db = fakeDb({docs: {[USER_PATH]: {gold: 5}, [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC, ...docs}});
    const fake = fakePlay(play);

    const {outcome, log} = await settle({db, play: fake, payload});

    assert.strictEqual(outcome.kind, SETTLEMENT_OUTCOME_REFUSE, label);
    assert.strictEqual(outcome.code, code, label);
    assert.strictEqual(outcome.reasonCode, reasonCode, label);
    assert.ok(typeof outcome.reason === "string" && outcome.reason, label);
    assert.strictEqual(outcome.response, undefined, `${label}: отказ не несёт ответа`);
    assert.deepStrictEqual(db.writes, [], `${label}: ничего не записано`);

    if (reasonCode === REASON_SKU_NOT_SOLD) {
      // Игрок заплатил, а таблица SKU не знает: чинится не кодом, а записью в таблицу — поэтому
      // ошибка, а не предупреждение.
      assert.strictEqual(log.entries.error.length, 1, label);
      assert.ok(log.entries.error[0].message.includes(SKU_NOT_SOLD_HINT), label);
      assert.strictEqual(log.entries.error[0].context.sku, "premium_month");
    } else {
      assert.deepStrictEqual(log.entries.error, [], `${label}: обычный отказ — предупреждение`);
      assert.strictEqual(log.entries.warn.length, 1, label);
      assert.strictEqual(log.entries.warn[0].context.reasonCode, reasonCode, label);
    }
    if (label === "another account's record" || label === "invalid request") {
      assert.deepStrictEqual(fake.calls, [], `${label}: до Play не доходит`);
    }
  }
}

async function testAnUnavailablePlayWritesNothingAndAsksTheClientToRetry() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 5}}});

  const outage = await settle({
    db, play: fakePlay({outcome: PLAY_OUTCOME_UNAVAILABLE, status: 503, code: null, reason: null}),
  });
  assert.strictEqual(outage.outcome.kind, SETTLEMENT_OUTCOME_UNAVAILABLE);
  assert.ok(outage.outcome.reason.includes("retry"));
  assert.strictEqual(db.transactions, 0, "без вердикта транзакция не начинается");
  assert.deepStrictEqual(db.writes, []);
  assert.strictEqual(outage.log.entries.warn.length, 1);
  assert.strictEqual(outage.log.entries.warn[0].context.status, 503);
  assert.deepStrictEqual(outage.log.entries.error, []);

  // Отказ в доступе — не токен виноват, а сервисный аккаунт: это ошибка владельцу, не предупреждение.
  for (const status of [401, 403]) {
    const denied = await settle({
      db: fakeDb(), play: fakePlay({outcome: PLAY_OUTCOME_UNAVAILABLE, status, code: null, reason: "forbidden"}),
    });
    assert.strictEqual(denied.outcome.kind, SETTLEMENT_OUTCOME_UNAVAILABLE, String(status));
    assert.strictEqual(denied.log.entries.error.length, 1, String(status));
    assert.ok(denied.log.entries.error[0].message.includes(ACCESS_REFUSED_HINT), String(status));
    assert.ok(denied.log.entries.error[0].message.includes("Play Console"), String(status));
    assert.deepStrictEqual(denied.db.writes, []);
  }

  // Найдено, но тело не читается: тоже повторить, не отказать.
  const odd = await settle({
    db: fakeDb(), play: fakePlay({outcome: PLAY_OUTCOME_FOUND, status: 200, code: null, reason: null, purchase: "?"}),
  });
  assert.strictEqual(odd.outcome.kind, SETTLEMENT_OUTCOME_UNAVAILABLE);
  assert.deepStrictEqual(odd.db.writes, []);
}

async function testALostRaceOnCreateIsReportedAsUnavailableAndNothingIsWritten() {
  // Между чтением и фиксацией токен рассчитал параллельный вызов: `create` падает, транзакция не
  // фиксируется, ничего не записано. Повтор прочитает запись и ответит повтором.
  const db = fakeDb({docs: {[USER_PATH]: {gold: 5}, [ECONOMY_CONSTANTS_DOC]: CONSTANTS_DOC}, createFails: true});

  const {outcome, log} = await settle({db, play: fakePlay(found())});

  assert.strictEqual(outcome.kind, SETTLEMENT_OUTCOME_UNAVAILABLE);
  assert.strictEqual(db.transactions, 1);
  assert.deepStrictEqual(db.writes, [], "упавшая транзакция не пишет ничего");
  assert.strictEqual(log.entries.error.length, 1);
  assert.ok(log.entries.error[0].message.includes("nothing was written"));
  assert.ok(log.entries.error[0].context.message.includes("ALREADY_EXISTS"));
}

async function testAMissingConstantsDocumentCreditsBootstrapAndAuditsVersionZero() {
  const db = fakeDb({docs: {[USER_PATH]: {gold: 0}}});

  const {outcome} = await settle({db, play: fakePlay(found())});

  const expected = expectedWrites({goldBefore: 0, constantsDoc: null});
  assert.deepStrictEqual(outcome.response, expected.response);
  assert.strictEqual(outcome.response.goldGranted, 10, "начальный размер");
  const audit = db.writes.find((write) => write.path === AUDIT_PATH).data;
  assert.strictEqual(audit.constantsVersion, 0);
  assert.strictEqual(audit.constantsDoc, "configs/economy");
  const settlement = db.writes.find((write) => write.path === SETTLEMENT_PATH).data;
  assert.strictEqual(settlement.status, STATUS_CREDITED);
}

(async () => {
  await testAVerifiedTokenWritesExactlyFourDocumentsInOneTransaction();
  await testAKnownTokenIsAnsweredFromTheGateWithoutAskingPlay();
  await testATokenSettledBetweenTheGateAndTheTransactionReplaysAndWritesNothing();
  await testAPendingPurchaseWritesNothingAndIsNotAnError();
  await testEveryRefusalMapsToTheMatrixCodeAndWritesNothing();
  await testAnUnavailablePlayWritesNothingAndAsksTheClientToRetry();
  await testALostRaceOnCreateIsReportedAsUnavailableAndNothingIsWritten();
  await testAMissingConstantsDocumentCreditsBootstrapAndAuditsVersionZero();
  console.log("purchase-settlement.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
