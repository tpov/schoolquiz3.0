"use strict";

const assert = require("node:assert/strict");
const {test} = require("node:test");
const path = require("node:path");
const source = process.env.PURCHASE_SOURCE_DIR || __dirname;
const {settlePurchase} = require(path.join(source, "purchase-settlement"));
const {clawBackPurchase} = require(path.join(source, "purchase-clawback"));
const {premiumTestDb} = require(path.join(source, "premium-test-db"));
const {settlementId, accountIdFor, PLAY_OUTCOME_FOUND, PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE} = require(path.join(source, "purchase-verification"));
const T = 1_800_000_000_000;
const UID = "refund-order-buyer";
const PACKAGE = "com.tpov.schoolquiz";
const TOKEN = "refund-order-test-token-1234567890123456789";
const ID = settlementId(TOKEN);
const USER = `users/${UID}`;
const SETTLEMENT = `purchase_settlements/${ID}`;
const FENCE = `purchase_revocations/${ID}`;
const NOTICE = "purchase_notifications/refund-order-notice";
const log = {warn() {}, error() {}};
const readGold = (data) => data.gold || 0;
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r; }); return {promise, resolve}; };
function fixture(kind = "GOLD") {
  const sku = kind === "GOLD" ? "gold_pack_small" : "test.configured_premium";
  const db = premiumTestDb({[USER]: {gold: 20, standardHearts: 5, goldHearts: 1,
    lifePoints: 0, plasmaPoints: 0, lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T},
  "configs/economy": {version: 1, goldPacks: {[sku]: kind === "GOLD" ? 60 : 0},
    premiumProducts: kind === "PREMIUM" ? {[sku]: {days: 7, salesEnabled: false}} : {}}});
  if (kind === "PREMIUM") delete db.store.get("configs/economy").goldPacks[sku];
  return {db, sku};
}
function purchase(sku, state = 0) {
  return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {productId: sku, purchaseState: state,
    consumptionState: 0, quantity: 1, orderId: "GPA.fixture-order", purchaseTimeMillis: String(T),
    obfuscatedExternalAccountId: accountIdFor(UID)}};
}
function play(sku, state = 1) {
  return {packageName: PACKAGE, getProductPurchase: async () => purchase(sku, state),
    getProductPurchaseByToken: async () => ({outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
      orderId: "GPA.fixture-order", purchaseStateContext: {purchaseState: state === 1 ? "CANCELLED" : "PURCHASED"},
      productLineItem: [{productId: sku}]}}),
    // Подписка приезжает из своего endpoint'а и в своей форме: активная — со сроком вперёд.
    getSubscriptionPurchaseV2: async () => ({outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
      subscriptionState: state === 0 ? 2 : 6,
      startTime: new Date(T - 1000).toISOString(), latestOrderId: "GPA.fixture-order", regionCode: "UA",
      externalAccountIdentifiers: {obfuscatedExternalAccountId: accountIdFor(UID)},
      lineItems: [{productId: sku, expiryTime: new Date(T + 365 * 86_400_000).toISOString()}]}})};
}
function message(overrides = {}, messageId = "refund-order-notice") {
  return {messageId, publishTime: new Date(T).toISOString(), data: Buffer.from(JSON.stringify({
    packageName: PACKAGE, eventTimeMillis: String(T), voidedPurchaseNotification: {
      purchaseToken: TOKEN, orderId: "GPA.fixture-order", productType: 2, refundType: 1}, ...overrides})).toString("base64")};
}
function buy(db, sku, api = play(sku, 0), overrides = {}) {
  return settlePurchase({db, playApi: api, now: T, uid: UID, payload: {sku, purchaseToken: TOKEN}, readGold, log, ...overrides});
}
function refund(db, sku, api = play(sku), msg = message()) {
  return clawBackPurchase({db, playApi: api, now: T + 1, message: msg, readGold, log});
}
function noGrant(db, outcome) {
  assert.notEqual(outcome.response?.status, "CREDITED", "refund signal must fence a stale PURCHASED response");
  assert.equal(db.store.get(USER).gold, 20);
  assert.notEqual(db.store.get(SETTLEMENT)?.status, "CREDITED");
  assert.equal([...db.store.keys()].filter((key) => key.startsWith(`premium_accounts/${UID}/lots/`)).length, 0);
}

for (const kind of ["GOLD", "PREMIUM"]) {
  test(`${kind}: refund finishes while grant holds an earlier PURCHASED response`, async () => {
    const {db, sku} = fixture(kind);
    const started = deferred(), release = deferred();
    const snapshot = purchase(sku);
    // Подписка для премиума идёт к своему endpoint'у; задержка держится на нём же.
    const pending = buy(db, sku, {packageName: PACKAGE,
      async getProductPurchase() {
        if (sku.startsWith("gold_")) { started.resolve(); await release.promise; }
        return snapshot;
      },
      async getSubscriptionPurchaseV2() {
        started.resolve(); await release.promise; // Задержка держится на том запросе, к которому уходит токен.
        return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
          subscriptionState: 2, startTime: new Date(T - 1000).toISOString(),
          latestOrderId: "GPA.fixture-order", regionCode: "UA",
          externalAccountIdentifiers: {obfuscatedExternalAccountId: accountIdFor(UID)},
          lineItems: [{productId: sku, expiryTime: new Date(T + 365 * 86_400_000).toISOString()}]}};
      }});
    await started.promise;
    try { await refund(db, sku); } finally { release.resolve(); }
    const outcome = await pending;
    await refund(db, sku); // Redelivery must not turn the old NOTHING_TO_TAKE into immunity.
    noGrant(db, outcome);
  });

  test(`${kind}: grant commits between refund's old ABSENT read and its transaction`, async () => {
    const {db, sku} = fixture(kind);
    const arrived = deferred(), release = deferred();
    let held = false;
    const refundDb = {...db, collection(name) {
      const collection = db.collection(name);
      return {...collection, doc(id) {
        const ref = collection.doc(id);
        return {...ref, async get() {
          const snapshot = await ref.get();
          if (ref.path === SETTLEMENT && !held) { held = true; arrived.resolve(); await release.promise; }
          return snapshot;
        }};
      }};
    }};
    // Current implementation reads the settlement outside a transaction. A fixed adapter may
    // skip that obsolete read; the bounded transaction hook represents the same linearization.
    const originalRun = refundDb.runTransaction.bind(refundDb);
    refundDb.runTransaction = async (body) => {
      if (!held) { held = true; arrived.resolve(); await release.promise; }
      return originalRun(body);
    };
    const pending = refund(refundDb, sku);
    await arrived.promise;
    try { assert.equal((await buy(db, sku)).response.status, "CREDITED"); } finally { release.resolve(); }
    await pending;
    await refund(db, sku);
    assert.equal(db.store.get(SETTLEMENT).status, "REFUNDED");
    assert.equal(db.store.get(USER).gold, 20);
    if (kind === "PREMIUM") assert.equal(db.store.get(`premium_accounts/${UID}`).totalRemainingMs, 0);
  });

  test(`${kind}: legacy NOTHING_TO_TAKE replay upgrades missing token fence`, async () => {
    const {db, sku} = fixture(kind);
    db.store.set(NOTICE, {messageId: "refund-order-notice", result: "NOTHING_TO_TAKE", settlementId: ID});
    await refund(db, sku);
    noGrant(db, await buy(db, sku));
    assert.equal(db.store.get(FENCE).state, "CONFIRMED_REVOKED");
  });
}

test("legacy PLAY_DISAGREES replay rechecks Play and settles its existing grant", async () => {
  const {db, sku} = fixture();
  assert.equal((await buy(db, sku)).response.status, "CREDITED");
  db.store.set(NOTICE, {messageId: "refund-order-notice", result: "PLAY_DISAGREES", settlementId: ID});
  await refund(db, sku);
  assert.equal(db.store.get(SETTLEMENT).status, "REFUNDED");
  assert.equal(db.store.get(USER).gold, 20);
});

test("ordinary credit then confirmed refund remains exactly once", async () => {
  const {db, sku} = fixture();
  assert.equal((await buy(db, sku)).response.status, "CREDITED");
  await refund(db, sku);
  const gold = db.store.get(USER).gold;
  await refund(db, sku);
  assert.equal(db.store.get(USER).gold, gold);
  assert.equal(gold, 20);
  assert.equal(db.store.get(SETTLEMENT).status, "REFUNDED");
});

for (const hasSettlement of [false, true]) {
  test(`PENDING is retryable, never forfeiture; later Play confirmation resolves, settled=${hasSettlement}`, async () => {
    const {db, sku} = fixture();
    if (hasSettlement) await buy(db, sku);
    await assert.rejects(refund(db, sku, play(sku, 0)), /redeliver/);
    assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
    assert.equal(db.store.get(NOTICE), undefined, "disagreement is not a completed notification");
    assert.equal((await buy(db, sku)).kind, "unavailable");
    assert.equal(db.store.get(USER).gold, hasSettlement ? 80 : 20);
    await refund(db, sku);
    assert.equal(db.store.get(FENCE).state, "CONFIRMED_REVOKED");
    assert.equal((await buy(db, sku)).kind, "refuse");
    assert.equal(db.store.get(USER).gold, 20);
  });
}

const invalidSignals = [
  ["another package", {packageName: "com.other.app"}],
  ["subscription", {voidedPurchaseNotification: {purchaseToken: TOKEN, productType: 1, refundType: 1, orderId: "GPA.fixture-order"}}],
  ["unknown refund type", {voidedPurchaseNotification: {purchaseToken: TOKEN, productType: 2, refundType: 3, orderId: "GPA.fixture-order"}}],
  ["missing product type", {voidedPurchaseNotification: {purchaseToken: TOKEN, refundType: 1, orderId: "GPA.fixture-order"}}],
  ["missing order", {voidedPurchaseNotification: {purchaseToken: TOKEN, productType: 2, refundType: 1}}],
  ["test notification", {voidedPurchaseNotification: undefined, testNotification: {version: "1.0"}}],
  ["new purchase", {voidedPurchaseNotification: undefined, oneTimeProductNotification: {notificationType: 1, purchaseToken: TOKEN, sku: "gold_pack_small"}}],
  ["ambiguous types", {oneTimeProductNotification: {notificationType: 2, purchaseToken: TOKEN, sku: "gold_pack_small"}}],
  ["missing event clock", {eventTimeMillis: null}],
];
for (const [label, change] of invalidSignals) {
  test(`${label}: no full-revocation fence or balance change`, async () => {
    const {db, sku} = fixture();
    let calls = 0;
    await refund(db, sku, {packageName: PACKAGE, async getProductPurchase() { calls++; },
      async getProductPurchaseByToken() { calls++; }}, message(change));
    assert.equal(db.store.get(FENCE), undefined);
    assert.equal(db.store.get(USER).gold, 20);
    assert.equal(calls, 0);
  });
}

test("unreadable or missing Pub/Sub id never fences a token", async () => {
  for (const msg of [{messageId: "bad-json", data: Buffer.from("no json").toString("base64")},
    {...message(), messageId: ""}, {...message(), data: ""}]) {
    const {db, sku} = fixture();
    await refund(db, sku, play(sku), msg);
    assert.equal(db.store.get(FENCE), undefined);
  }
});

test("API outage persists pending only and redelivery does not strand the refund", async () => {
  const {db, sku} = fixture();
  const api = {packageName: PACKAGE, async getProductPurchaseByToken() { return {outcome: PLAY_OUTCOME_UNAVAILABLE}; }};
  await assert.rejects(refund(db, sku, api), /redeliver/);
  assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
  assert.equal(db.store.get(NOTICE), undefined);
  await refund(db, sku);
  assert.equal(db.store.get(FENCE).state, "CONFIRMED_REVOKED");
});

test("confirmed ownerless not-found proof needs no invented SKU", async () => {
  const {db, sku} = fixture();
  await refund(db, sku, {packageName: PACKAGE, async getProductPurchaseByToken(value) {
    assert.equal(value, TOKEN); return {outcome: PLAY_OUTCOME_NOT_FOUND, status: 404};
  }});
  assert.equal(db.store.get(FENCE).proof, "PLAY_TOKEN_NOT_FOUND");
  noGrant(db, await buy(db, sku));
});

test("known owner remains checked ahead of pending and confirmed fence", async () => {
  const {db, sku} = fixture();
  await buy(db, sku);
  await assert.rejects(refund(db, sku, play(sku, 0)), /redeliver/);
  let result = await buy(db, sku, play(sku), {uid: "other-account"});
  assert.equal(result.code, "permission-denied");
  await refund(db, sku);
  result = await buy(db, sku, play(sku), {uid: "other-account"});
  assert.equal(result.code, "permission-denied");
});

test("wrong API SKU, order or buyer never confirms or claws back a known purchase", async () => {
  for (const change of [{productId: "wrong-sku"}, {orderId: "GPA.wrong"},
    {obfuscatedExternalAccountId: accountIdFor("other-account")}, {purchaseState: 2}, {purchaseState: 99}]) {
    const {db, sku} = fixture();
    await buy(db, sku);
    const lookup = purchase(sku, 1);
    Object.assign(lookup.purchase, change);
    await assert.rejects(refund(db, sku, {packageName: PACKAGE, async getProductPurchase() { return lookup; }}), /redeliver/);
    assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
    assert.equal(db.store.get(USER).gold, 80);
    assert.equal(db.store.get(SETTLEMENT).status, "CREDITED");
  }
});

test("wrong V2 order, SKU, unknown state and unsupported line count remain pending", async () => {
  for (const change of [{orderId: "GPA.wrong"}, {productLineItem: []},
    {productLineItem: [{productId: "one"}, {productId: "two"}]}, {purchaseStateContext: {purchaseState: "PENDING"}},
    {purchaseStateContext: {purchaseState: "PURCHASE_STATE_UNSPECIFIED"}}]) {
    const {db, sku} = fixture();
    const api = play(sku);
    const lookup = await api.getProductPurchaseByToken();
    Object.assign(lookup.purchase, change);
    api.getProductPurchaseByToken = async () => lookup;
    await assert.rejects(refund(db, sku, api), /redeliver/);
    assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
    assert.equal(db.store.get(NOTICE), undefined);
  }
});

test("pending one-time cancellation uses its exact SKU, never an arbitrary different item", async () => {
  const {db, sku} = fixture();
  const msg = message({voidedPurchaseNotification: undefined,
    oneTimeProductNotification: {notificationType: 2, purchaseToken: TOKEN, sku}});
  await assert.rejects(refund(db, sku, play("different_sku"), msg), /redeliver/);
  await refund(db, sku, play(sku), msg);
  assert.equal(db.store.get(FENCE).state, "CONFIRMED_REVOKED");
});

test("failed fence commit is not acknowledged and a safe later retry succeeds", async () => {
  const {db, sku} = fixture();
  db.failNextCommit();
  await assert.rejects(refund(db, sku), /commit failure/);
  assert.equal(db.store.get(FENCE), undefined);
  assert.equal(db.store.get(NOTICE), undefined);
  await refund(db, sku);
  noGrant(db, await buy(db, sku));
});

test("failed confirmation keeps only pending fence and no acknowledged notification", async () => {
  const {db, sku} = fixture();
  const api = play(sku);
  const get = api.getProductPurchaseByToken;
  api.getProductPurchaseByToken = async (...args) => { db.failNextCommit(); return get(...args); };
  await assert.rejects(refund(db, sku, api), /commit failure/);
  assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
  assert.equal(db.store.get(NOTICE), undefined);
  await refund(db, sku);
  noGrant(db, await buy(db, sku));
});

test("permanent source marker contains no raw token or guessed owner", async () => {
  const {db, sku} = fixture();
  await refund(db, sku);
  const before = structuredClone(db.store.get(FENCE));
  await refund(db, sku, play(sku), message({}, "second-notice"));
  assert.deepEqual(db.store.get(FENCE), before);
  assert.equal(JSON.stringify(before).includes(TOKEN), false);
  assert.equal(before.uid, undefined);
});

test("a wrong RTDN order cannot claw back a known token even when lookup says not-found", async () => {
  const {db, sku} = fixture();
  await buy(db, sku);
  const msg = message({voidedPurchaseNotification: {purchaseToken: TOKEN, productType: 2, refundType: 1,
    orderId: "GPA.wrong-order"}});
  await assert.rejects(refund(db, sku, {packageName: PACKAGE,
    async getProductPurchase() { return {outcome: PLAY_OUTCOME_NOT_FOUND}; }}, msg), /redeliver/);
  assert.equal(db.store.get(USER).gold, 80);
  assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION");
});

test("a legacy message id cannot be rebound to a different token", async () => {
  const {db, sku} = fixture();
  db.store.set(NOTICE, {result: "NOTHING_TO_TAKE", settlementId: settlementId("different-token")});
  await assert.rejects(refund(db, sku), /binding disagrees/);
  assert.equal(db.store.get(FENCE), undefined);
});

test("a corrupted pending fence cannot silently become credit or a confirmed refund", async () => {
  const {db, sku} = fixture();
  db.store.set(FENCE, {schemaVersion: 1, state: "CONFIRMED_REVOKED", settlementId: ID});
  assert.equal((await buy(db, sku)).kind, "unavailable");
  await assert.rejects(refund(db, sku), /requires repair/);
  assert.equal(db.store.get(USER).gold, 20);
});
