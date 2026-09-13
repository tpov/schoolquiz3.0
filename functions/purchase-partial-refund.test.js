"use strict";

const assert = require("node:assert/strict");
const {test} = require("node:test");
const path = require("node:path");

const source = process.env.PURCHASE_SOURCE_DIR || __dirname;
const {settlePurchase} = require(path.join(source, "purchase-settlement"));
const {clawBackPurchase} = require(path.join(source, "purchase-clawback"));
const {premiumTestDb} = require(path.join(source, "premium-test-db"));
const {accountIdFor, PLAY_OUTCOME_FOUND, PLAY_OUTCOME_UNAVAILABLE, settlementId} =
  require(path.join(source, "purchase-verification"));
const {refundDocId} = require(path.join(source, "purchase-notifications"));
const {pendingRevocation, partiallyConfirmedRevocation} = require(path.join(source, "purchase-revocations"));

const T = 1_800_000_000_000;
const UID = "partial-refund-buyer";
const PACKAGE = "com.tpov.schoolquiz";
const TOKEN = "partial-refund-test-token-1234567890123456789";
const ID = settlementId(TOKEN);
const SKU = "gold_pack_small";
const USER = `users/${UID}`;
const SETTLEMENT = `purchase_settlements/${ID}`;
const FENCE = `purchase_revocations/${ID}`;
const log = {warn() {}, error() {}};
const readGold = (data) => data.gold || 0;

function fixture() {
  return premiumTestDb({
    [USER]: {gold: 20, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
      lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T},
    "configs/economy": {version: 1, goldPacks: {[SKU]: 60}, premiumProducts: {}},
  });
}

function v1(quantity = 5, state = 0) {
  return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
    productId: SKU,
    purchaseState: state,
    consumptionState: 0,
    quantity,
    orderId: "GPA.partial-fixture-order",
    purchaseTimeMillis: String(T),
    obfuscatedExternalAccountId: accountIdFor(UID),
  }};
}

function v2({quantity = 5, refundableQuantity = quantity, sku = SKU, orderId = "GPA.partial-fixture-order",
  accountId = accountIdFor(UID), state = "PURCHASED"} = {}) {
  const purchase = {
    orderId,
    obfuscatedExternalAccountId: accountId,
    productLineItem: [{productId: sku, productOfferDetails: {quantity, refundableQuantity}}],
  };
  if (state !== null) purchase.purchaseStateContext = {purchaseState: state};
  return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase};
}

function api({one = v1(), two = v2()} = {}) {
  return {
    packageName: PACKAGE,
    async getProductPurchase() { return one; },
    async getProductPurchaseByToken() { return two; },
  };
}

function partialMessage(messageId = "partial-refund-notice") {
  return {
    messageId,
    publishTime: new Date(T).toISOString(),
    data: Buffer.from(JSON.stringify({
      packageName: PACKAGE,
      eventTimeMillis: String(T),
      voidedPurchaseNotification: {
        purchaseToken: TOKEN,
        orderId: "GPA.partial-fixture-order",
        productType: 2,
        refundType: 2,
      },
    })).toString("base64"),
  };
}

function fullMessage(messageId = "full-refund-notice") {
  const decoded = JSON.parse(Buffer.from(partialMessage(messageId).data, "base64").toString("utf8"));
  decoded.voidedPurchaseNotification.refundType = 1;
  return {...partialMessage(messageId), data: Buffer.from(JSON.stringify(decoded)).toString("base64")};
}

function buy(db, play = api()) {
  return settlePurchase({db, playApi: play, now: T, uid: UID, payload: {sku: SKU, purchaseToken: TOKEN}, readGold, log});
}

function refund(db, play = api({two: v2({refundableQuantity: 3})}), message = partialMessage()) {
  return clawBackPurchase({db, playApi: play, now: T + 1, message, readGold, log});
}

function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return {promise, resolve};
}

test("partial refund before credit fences the token and credits only V2 refundable quantity", async () => {
  const db = fixture();
  await refund(db);

  const fence = db.store.get(FENCE);
  assert.equal(fence.state, "PARTIAL_RECONCILED");
  assert.deepEqual(fence.partial, {
    sku: SKU,
    orderId: "GPA.partial-fixture-order",
    purchaseQuantity: 5,
    refundableQuantity: 3,
    refundedQuantity: 2,
    observedAtMs: T + 1,
    notificationId: "partial-refund-notice",
    obfuscatedExternalAccountId: accountIdFor(UID),
  });
  assert.equal(JSON.stringify(fence).includes(TOKEN), false, "the permanent fence stores only the token hash document id");
  assert.equal(db.store.get(USER).gold, 20, "a refund-before-credit has no clawback to make");

  const result = await buy(db, api({two: v2({refundableQuantity: 3})}));
  assert.equal(result.response.goldGranted, 180);
  assert.equal(db.store.get(USER).gold, 200);
  assert.deepEqual(
    pick(db.store.get(SETTLEMENT), ["status", "quantity", "purchaseQuantity", "grantedQuantity", "refundedQuantity", "lastObservedRefundableQuantity", "refundRevision", "goldGranted"]),
    {status: "CREDITED", quantity: 3, purchaseQuantity: 5, grantedQuantity: 3, refundedQuantity: 2,
      lastObservedRefundableQuantity: 3, refundRevision: 0, goldGranted: 180},
  );
});

test("a partial RTDN racing a held credit is observed before the credit transaction and cannot grant stale full quantity", async () => {
  const db = fixture();
  const started = deferred();
  const release = deferred();
  const held = {
    packageName: PACKAGE,
    async getProductPurchase() { started.resolve(); await release.promise; return v1(); },
    async getProductPurchaseByToken() { return v2({refundableQuantity: 3}); },
  };
  const pendingBuy = buy(db, held);
  await started.promise;
  try {
    await refund(db, held);
  } finally {
    release.resolve();
  }
  const result = await pendingBuy;
  assert.equal(result.response.goldGranted, 180);
  assert.equal(db.store.get(USER).gold, 200);
  assert.equal(db.store.get(SETTLEMENT).quantity, 3);
});

test("a new multi-quantity grant uses V2 remaining quantity even without an RTDN, and replay calls neither Play API", async () => {
  const db = fixture();
  const calls = {v1: 0, v2: 0};
  const verified = {
    packageName: PACKAGE,
    async getProductPurchase() { calls.v1 += 1; return v1(); },
    async getProductPurchaseByToken() { calls.v2 += 1; return v2({refundableQuantity: 3}); },
  };
  const first = await buy(db, verified);
  assert.equal(first.response.goldGranted, 180);
  assert.equal(db.store.get(USER).gold, 200);
  assert.deepEqual(pick(db.store.get(SETTLEMENT), ["schemaVersion", "purchaseQuantity", "grantedQuantity", "refundedQuantity"]),
    {schemaVersion: 2, purchaseQuantity: 5, grantedQuantity: 3, refundedQuantity: 2});
  assert.deepEqual(calls, {v1: 1, v2: 1});

  const noPlay = {
    packageName: PACKAGE,
    async getProductPurchase() { throw new Error("credited replay must not re-read V1"); },
    async getProductPurchaseByToken() { throw new Error("credited replay must not re-read V2"); },
  };
  const replay = await buy(db, noPlay);
  assert.deepEqual(replay.response, first.response);
});

test("a new multi-quantity grant fails retryably on unavailable or mismatching V2 proof, without writes", async () => {
  const cases = [
    ["outage", {outcome: PLAY_OUTCOME_UNAVAILABLE}],
    ["wrong SKU", v2({sku: "other_sku"})],
    ["wrong order", v2({orderId: "GPA.other"})],
    ["wrong account", v2({accountId: accountIdFor("other-buyer")})],
    ["wrong original quantity", v2({quantity: 4, refundableQuantity: 4})],
    ["pending V2", v2({state: "PENDING"})],
    ["malformed V2", {outcome: PLAY_OUTCOME_FOUND, purchase: {productLineItem: []}}],
  ];
  for (const [label, two] of cases) {
    const db = fixture();
    const result = await buy(db, api({two}));
    assert.equal(result.kind, "unavailable", label);
    assert.equal(db.store.get(USER).gold, 20, label);
    assert.equal(db.store.has(SETTLEMENT), false, label);
    assert.equal(db.commits.length, 0, label);
  }
});

test("a new multi-quantity grant with zero V2 remaining quantity is refused without a grant", async () => {
  const db = fixture();
  const result = await buy(db, api({two: v2({refundableQuantity: 0})}));
  assert.equal(result.kind, "refuse");
  assert.equal(db.store.get(USER).gold, 20);
  assert.equal(db.store.has(SETTLEMENT), false);
  assert.equal(db.commits.length, 0);
});

test("a SKU enabled after V2 preflight cannot receive an unverified multi-quantity V1 grant", async () => {
  const db = fixture();
  // At preflight this is an invalid conflicting SKU, so no V2 lookup is warranted.  The
  // transaction sees a later repaired gold-only config; it must retry, not fall back to V1 q5.
  db.store.set("configs/economy", {
    version: 1,
    goldPacks: {[SKU]: 60},
    premiumProducts: {[SKU]: {days: 7, salesEnabled: false}},
  });
  let v2Calls = 0;
  db.beforeNextTransaction((store) => store.set("configs/economy", {
    version: 2, goldPacks: {[SKU]: 60}, premiumProducts: {},
  }));
  const result = await buy(db, {
    packageName: PACKAGE,
    async getProductPurchase() { return v1(); },
    async getProductPurchaseByToken() { v2Calls += 1; return v2(); },
  });
  assert.equal(result.kind, "unavailable");
  assert.equal(v2Calls, 0, "preflight did not classify the conflicting SKU as a gold grant");
  assert.equal(db.store.get(USER).gold, 20);
  assert.equal(db.store.has(SETTLEMENT), false);
});

test("a fresher partial fence between V2 read and transaction can only lower the multi-quantity grant", async () => {
  const db = fixture();
  let v2Read = false;
  const signal = {kind: "ONE_TIME_PARTIAL_VOID", orderId: "GPA.partial-fixture-order", sku: "", eventTimeMs: T};
  const pending = pendingRevocation({id: ID, packageName: PACKAGE, messageId: "race-notice", signal, now: T});
  const fence = partiallyConfirmedRevocation(pending, {
    now: T,
    messageId: "race-notice",
    observation: {
      sku: SKU, orderId: "GPA.partial-fixture-order", purchaseQuantity: 5,
      refundableQuantity: 2, refundedQuantity: 3, observedAtMs: T, notificationId: "race-notice",
      obfuscatedExternalAccountId: accountIdFor(UID),
    },
  });
  db.beforeNextTransaction((store) => {
    assert.equal(v2Read, true, "the fence arrives after external V2 read but before atomic grant");
    store.set(FENCE, fence);
  });
  const result = await buy(db, {
    packageName: PACKAGE,
    async getProductPurchase() { return v1(); },
    async getProductPurchaseByToken() { v2Read = true; return v2({refundableQuantity: 3}); },
  });
  assert.equal(result.response.goldGranted, 120);
  assert.equal(db.store.get(USER).gold, 140);
  assert.deepEqual(pick(db.store.get(SETTLEMENT), ["quantity", "purchaseQuantity", "grantedQuantity", "refundedQuantity"]),
    {quantity: 2, purchaseQuantity: 5, grantedQuantity: 2, refundedQuantity: 3});
});

test("concurrent distinct partial notifications apply one cumulative delta and preserve separate evidence", async () => {
  const db = fixture();
  assert.equal((await buy(db)).response.goldGranted, 300);
  const firstAtLookup = deferred();
  const secondAtLookup = deferred();
  const releaseFirst = deferred();
  const firstCommitted = deferred();
  let lookups = 0;
  const concurrentApi = {
    packageName: PACKAGE,
    async getProductPurchase() { return v1(); },
    async getProductPurchaseByToken() {
      lookups += 1;
      if (lookups === 1) {
        firstAtLookup.resolve();
        await releaseFirst.promise;
        return v2({refundableQuantity: 2});
      }
      secondAtLookup.resolve();
      await firstCommitted.promise;
      return v2({refundableQuantity: 2});
    },
  };
  const first = refund(db, concurrentApi, partialMessage("partial-one"));
  await firstAtLookup.promise;
  const second = refund(db, concurrentApi, partialMessage("partial-two"));
  await secondAtLookup.promise;
  releaseFirst.resolve();
  const firstOutcome = await first;
  firstCommitted.resolve();
  const secondOutcome = await second;
  const outcomes = [firstOutcome, secondOutcome];

  assert.deepEqual(outcomes.map((outcome) => outcome.kind).sort(), ["CLAWED_BACK", "RECORDED"]);
  assert.equal(db.store.get(USER).gold, 140, "three units × 60 are removed once");
  const settlement = db.store.get(SETTLEMENT);
  assert.deepEqual(pick(settlement, ["status", "refundedQuantity", "lastObservedRefundableQuantity", "refundRevision"]),
    {status: "CREDITED", refundedQuantity: 3, lastObservedRefundableQuantity: 2, refundRevision: 1});
  const refundId = refundDocId(ID, 3);
  assert.ok(db.store.has(`users/${UID}/receipts/${refundId}`));
  assert.ok(db.store.has(`purchase_audit/${refundId}`));
  assert.equal([...db.store.values()].filter((entry) => entry && entry.sourceId === refundId).length, 1,
    "the ledger source is the cumulative refund operation, not the token alone");
  const commits = db.commits.length;
  const replay = await refund(db, api({two: v2({refundableQuantity: 2})}), partialMessage("partial-one"));
  assert.equal(replay.replayed, true, "same Pub/Sub id is answered before another V2 request or write");
  assert.equal(db.commits.length, commits);
});

test("V2 outage or mismatch leaves a pending fence and makes no balance, receipt or audit change", async () => {
  const cases = [
    ["outage", {outcome: PLAY_OUTCOME_UNAVAILABLE}],
    ["wrong sku", v2({sku: "other_sku"})],
    ["wrong order", v2({orderId: "GPA.other"})],
    ["wrong owner", v2({accountId: accountIdFor("other-buyer")})],
    ["partial signal with fully refunded V2 quantity", v2({refundableQuantity: 0})],
    ["partial signal with V2 quantity beyond the original", v2({refundableQuantity: 6})],
    ["missing V2 purchase state", v2({state: null})],
    ["pending V2 purchase state", v2({state: "PENDING"})],
    ["cancelled V2 purchase state", v2({state: "CANCELLED"})],
    ["multiple items", {outcome: PLAY_OUTCOME_FOUND, purchase: {
      orderId: "GPA.partial-fixture-order", productLineItem: [
        {productId: SKU, productOfferDetails: {quantity: 5, refundableQuantity: 3}},
        {productId: SKU, productOfferDetails: {quantity: 5, refundableQuantity: 3}},
      ],
    }}],
    ["remaining quantity increases after reconciliation", v2({refundableQuantity: 4})],
  ];
  for (const [label, two] of cases) {
    const db = fixture();
    assert.equal((await buy(db)).response.goldGranted, 300);
    if (label === "remaining quantity increases after reconciliation") {
      await refund(db, api({two: v2({refundableQuantity: 3})}), partialMessage("first-partial"));
    }
    await assert.rejects(refund(db, api({two}), partialMessage(`bad-${label}`)), /redeliver/);
    assert.equal(db.store.get(FENCE).state, "PENDING_VERIFICATION", label);
    assert.equal(db.store.get(SETTLEMENT).status, "CREDITED", label);
    const expectedGold = label === "remaining quantity increases after reconciliation" ? 200 : 320;
    assert.equal(db.store.get(USER).gold, expectedGold, label);
  }
});

test("a final full refund after a partial removes only the remaining units and receives a new operation id", async () => {
  const db = fixture();
  await buy(db);
  await refund(db, api({two: v2({refundableQuantity: 2})}), partialMessage("partial-three"));
  assert.equal(db.store.get(USER).gold, 140);

  const fullApi = api({one: v1(5, 1)});
  await refund(db, fullApi, fullMessage());
  const settlement = db.store.get(SETTLEMENT);
  assert.deepEqual(pick(settlement, ["status", "refundedQuantity", "lastObservedRefundableQuantity", "refundRevision"]),
    {status: "REFUNDED", refundedQuantity: 5, lastObservedRefundableQuantity: 0, refundRevision: 2});
  assert.equal(db.store.get(USER).gold, 20);
  assert.ok(db.store.has(`users/${UID}/receipts/${refundDocId(ID, 3)}`));
  assert.ok(db.store.has(`users/${UID}/receipts/${refundDocId(ID, 5)}`));
});

test("a legacy one-shot settlement upgrades atomically on its first partial and never overwrites cumulative evidence", async () => {
  const db = fixture();
  // This precisely models the old schema: no additive quantity fields, but an already credited
  // multi-quantity gold purchase.  A full-only refund must retain its historic key; the first
  // partial is the transition point to immutable quantity-specific operations.
  db.store.set(SETTLEMENT, {
    settlementId: ID, uid: UID, status: "CREDITED", sku: SKU, orderId: "GPA.partial-fixture-order",
    quantity: 5, goldPerUnit: 60, goldGranted: 300, constantsVersion: 1, isTest: false,
  });
  db.store.set(USER, {...db.store.get(USER), gold: 320});

  await refund(db, api({two: v2({refundableQuantity: 3})}), partialMessage("legacy-partial-two"));
  let settlement = db.store.get(SETTLEMENT);
  assert.deepEqual(pick(settlement, ["schemaVersion", "purchaseQuantity", "grantedQuantity", "refundedQuantity", "refundRevision"]),
    {schemaVersion: 2, purchaseQuantity: 5, grantedQuantity: 5, refundedQuantity: 2, refundRevision: 1});
  await refund(db, api({two: v2({refundableQuantity: 2})}), partialMessage("legacy-partial-three"));
  await refund(db, api({one: v1(5, 1)}), fullMessage("legacy-full-five"));

  settlement = db.store.get(SETTLEMENT);
  assert.deepEqual(pick(settlement, ["status", "schemaVersion", "refundedQuantity", "refundRevision"]),
    {status: "REFUNDED", schemaVersion: 2, refundedQuantity: 5, refundRevision: 3});
  assert.equal(db.store.get(USER).gold, 20, "2 + 1 + 2 units are removed exactly once");
  for (const quantity of [2, 3, 5]) {
    const refundId = refundDocId(ID, quantity);
    assert.ok(db.store.has(`users/${UID}/receipts/${refundId}`), `receipt q${quantity}`);
    assert.ok(db.store.has(`purchase_audit/${refundId}`), `audit q${quantity}`);
    assert.equal([...db.store.values()].filter((entry) => entry && entry.sourceId === refundId).length, 1,
      `one ledger source q${quantity}`);
  }
  assert.equal(db.store.has(`users/${UID}/receipts/${refundDocId(ID)}`), false,
    "the legacy full-only key is not reused after a partial transition");
});

test("malformed additive provenance fails closed; a full refund keeps its frozen quantity if V1 reports zero", async () => {
  const malformed = fixture();
  await buy(malformed);
  malformed.store.set(SETTLEMENT, {...malformed.store.get(SETTLEMENT), grantedQuantity: 6, goldGranted: 360});
  await assert.rejects(refund(malformed, api({two: v2({refundableQuantity: 3})}), partialMessage("bad-grant-size")), /redeliver/);
  assert.equal(malformed.store.get(USER).gold, 320);
  assert.equal(malformed.store.get(FENCE).state, "PENDING_VERIFICATION");

  const zeroQuantity = fixture();
  await buy(zeroQuantity);
  // Full V1 cancellation determines the state, but all financial quantity comes from the frozen
  // settlement. A malformed V1 quantity must therefore neither change the amount nor prevent the
  // proven full reversal from retaining correct historic quantity evidence.
  await refund(zeroQuantity, api({one: v1(0, 1)}), fullMessage("full-v1-zero-quantity"));
  assert.equal(zeroQuantity.store.get(USER).gold, 20);
  assert.equal(zeroQuantity.store.get(SETTLEMENT).refundedQuantity, 5);
});

function pick(value, keys) {
  return Object.fromEntries(keys.map((key) => [key, value[key]]));
}
