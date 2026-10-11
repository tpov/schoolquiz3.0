"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {DAY_MS, SOURCE} = require("./premium-grants");
const {premiumGrantId} = require("./premium-economy");
const {applyBoxOperation, readBoxOperation, planBoxOperation, writeBoxOperation,
  boxOperationId, BONUS_CLOCK_FIELD, BOX_OVERCLAIM_PATH} = require("./box-service");
const {ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const {LEDGER_PATH, CURRENCY} = require("./currency-ledger");
const {SHORTFALL_FIELD, PLAY_OUTCOME_FOUND, settlementId, accountIdFor} = require("./purchase-verification");
const {settlePurchase} = require("./purchase-settlement");
const {clawBackPurchase} = require("./purchase-clawback");
const {planGoldCredit} = require("./gold-credit");
const {GIFT_BOX_TROPHY_NAMES, TROPHY_VERIFIED} = require("./trophies");

const T = 1_800_000_000_000;
const UID = "box-player";
const USER = `users/${UID}`;
const HEADER = `premium_accounts/${UID}`;
const OPENING = "persistent-box-opening-0001";
const PACKAGE = "com.tpov.schoolquiz";
const TOKEN = "valid-box-test-premium-play-token-1234567890";
const SKU = "configured.box_test.premium";
const config = {version: 30, standard: {regenMs: 100_000}, plasma: {regenMs: 100_000},
  premiumProducts: {[SKU]: {days: 1, salesEnabled: false}}};
const draws = (...values) => [...values, ...Array(8 - values.length).fill(0)];
const COMMON = draws(0.9, 0, 0);
const PREMIUM = draws(0.01, 0.9, 0.9);
const GOLD20 = draws(0.01, 0.99, 0.5, 0.999999);
const user = (extra = {}) => ({gold: 8, [SHORTFALL_FIELD]: 0, nolics: 11, pointsNolics: 11,
  standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
  lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T,
  boxCount: 2, boxStreakDays: 4, nextBoxAtMs: T + DAY_MS, ...extra});
const makeDb = (extra = {}) => premiumTestDb({[USER]: user(extra), [ECONOMY_CONSTANTS_DOC]: config});
const open = (id = OPENING, ownerUid = UID) => ({kind: "OPEN", openingId: id, ownerUid});
const operation = (kind, id, extra = {}) => ({kind, operationId: id, ownerUid: UID, ...extra});
const run = (db, payload = open(), now = T, entropy = COMMON, uid = UID) => applyBoxOperation({db, uid, payload, now, entropy});
const rows = (db, currency) => [...db.store].filter(([path, value]) => path.startsWith(`${LEDGER_PATH}/`) && value.currency === currency).map(([, value]) => value);
const recordPath = (id) => `box_operations/${boxOperationId(id)}`;
const log = {warn() {}, error() {}};
function play(state = 0) {
  return {packageName: PACKAGE, async getProductPurchase(sku) {
    return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {productId: sku, purchaseState: state,
      consumptionState: state === 0 ? 0 : 1, quantity: 1, purchaseTimeMillis: String(T), orderId: "GPA.1111-2222-3333-44444"}};
  }, async getSubscriptionPurchaseV2() {
    return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
      subscriptionState: state === 0 ? "SUBSCRIPTION_STATE_ACTIVE" : "SUBSCRIPTION_STATE_EXPIRED", startTime: new Date(T).toISOString(),
      latestOrderId: "GPA.1111-2222-3333-44444", regionCode: "UA",
      externalAccountIdentifiers: {obfuscatedExternalAccountId: accountIdFor(UID)},
      lineItems: [{productId: SKU, expiryTime: new Date(T + 365 * 86_400_000).toISOString()}]}};
  }};
}
async function buyPremium(db, now = T, token = TOKEN) {
  const result = await settlePurchase({db, uid: UID, payload: {sku: SKU, purchaseToken: token}, now, playApi: play(), readGold: (u) => u.gold || 0, log});
  assert.equal(result.response?.status, "CREDITED");
  return result;
}
async function refundPremium(db, now) {
  const value = {version: "1.0", packageName: PACKAGE, eventTimeMillis: String(now),
    voidedPurchaseNotification: {purchaseToken: TOKEN, productType: 2, refundType: 1,
      orderId: "GPA.1111-2222-3333-44444"}};
  return clawBackPurchase({db, playApi: play(1), now, readGold: (u) => u.gold || 0, log,
    message: {messageId: "box-test-premium-refund", publishTime: new Date(now).toISOString(), data: Buffer.from(JSON.stringify(value)).toString("base64")}});
}
const tests = [];
function test(name, body) { tests.push({name, body}); }

test("lost response replays exact reward, permanent receipt and once-only box/nolics movement", async () => {
  const db = makeDb();
  const first = await run(db);
  assert.equal(first.ownerUid, UID);
  assert.deepEqual(first.reward, {type: "addNolics", amount: 500});
  assert.equal(first.boxCount, 1);
  assert.equal(db.store.get(USER).nolics, 511);
  assert.equal(db.store.get(USER).pointsNolics, 511);
  assert.ok(db.store.has(recordPath(OPENING)));
  assert.ok(db.store.has(`users/${UID}/receipts/box_${boxOperationId(OPENING)}`));
  const saved = structuredClone([...db.store]);
  const replay = await run(db, open(), T + 5 * DAY_MS, PREMIUM);
  assert.deepEqual(replay, first);
  assert.deepEqual([...db.store], saved);
  assert.equal(rows(db, CURRENCY.BOXES).reduce((n, r) => n + r.delta, 0), -1);
  assert.equal(rows(db, CURRENCY.NOLICS).reduce((n, r) => n + r.delta, 0), 500);
});

test("same id cannot switch account or operation kind, including account-rebound retries", async () => {
  const db = makeDb();
  await run(db);
  db.store.set("users/second-account", user({boxCount: 99}));
  const before = structuredClone([...db.store]);
  await assert.rejects(run(db, open(), T, COMMON, "second-account"), {code: "PERMISSION_DENIED"});
  await assert.rejects(run(db, open(OPENING, "second-account"), T, COMMON, "second-account"), {code: "PERMISSION_DENIED"});
  await assert.rejects(run(db, operation("VISIT", OPENING)), {code: "CONFLICTING_REPLAY"});
  assert.deepEqual([...db.store], before);
});

test("missing permanent id, malformed claims, untrusted owner and ads path fail before effects", async () => {
  const db = makeDb();
  const before = structuredClone([...db.store]);
  await assert.rejects(run(db, {kind: "OPEN", ownerUid: UID}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(run(db, operation("AD_REWARD", "unsupported-ad-operation")), {code: "INVALID_ARGUMENT"});
  await assert.rejects(run(db, operation("OFFLINE_STREAK", "invalid-offline-claim", {base: {}, claimed: {}})), {code: "INVALID_ARGUMENT"});
  await assert.rejects(run(db, open("short")), {code: "INVALID_ARGUMENT"});
  assert.deepEqual([...db.store], before);
});

test("rolling daily visits grant only from day10, once per day; missed day resets without loss", async () => {
  const db = makeDb({boxCount: 0, boxStreakDays: 0, nextBoxAtMs: 0});
  for (let day = 0; day < 10; day++) await run(db, operation("VISIT", `daily-visit-operation-${day}`), T + day * DAY_MS);
  assert.equal(db.store.get(USER).boxCount, 1);
  assert.equal(db.store.get(USER).boxStreakDays, 10);
  await run(db, operation("VISIT", "same-day-visit-operation"), T + 9 * DAY_MS + 1000);
  assert.equal(db.store.get(USER).boxCount, 1);
  await run(db, operation("VISIT", "daily-visit-operation-9"), T + 10 * DAY_MS);
  assert.equal(db.store.get(USER).boxStreakDays, 10, "old id replay cannot count another visit");
  await run(db, operation("VISIT", "next-day-visit-operation"), T + 10 * DAY_MS);
  assert.equal(db.store.get(USER).boxCount, 2);
  await run(db, operation("VISIT", "after-absence-operation"), T + 14 * DAY_MS);
  assert.equal(db.store.get(USER).boxCount, 2);
  assert.equal(db.store.get(USER).boxStreakDays, 1);
});

test("honest offline week uses existing verdict, stale base cannot double-credit another device", async () => {
  const db = makeDb({boxCount: 1, boxStreakDays: 10, nextBoxAtMs: T + DAY_MS});
  const base = {boxStreakDays: 10, nextBoxAtMs: T + DAY_MS};
  const payload = operation("OFFLINE_STREAK", "offline-week-operation", {base, claimed: {boxStreakDays: 17, boxesEarned: 7}});
  const first = await run(db, payload, T + 7 * DAY_MS + 1000);
  assert.equal(first.earnedBoxes, 7);
  assert.equal(first.boxCount, 8);
  assert.equal(first.nextBoxAtMs, T + 8 * DAY_MS);
  assert.deepEqual(await run(db, payload, T + 9 * DAY_MS), first);
  const stale = await run(db, {...payload, operationId: "other-device-old-base"}, T + 9 * DAY_MS);
  assert.equal(stale.status, "STALE_BASE");
  assert.equal(stale.boxCount, 8);
  await assert.rejects(run(db, {...payload, claimed: {boxStreakDays: 20, boxesEarned: 10}}, T + 9 * DAY_MS), {code: "CONFLICTING_REPLAY"});
});

test("offline overclaim is capped and journaled, never minted as client requested", async () => {
  const db = makeDb({boxCount: 0, boxStreakDays: 10, nextBoxAtMs: T + DAY_MS});
  const payload = operation("OFFLINE_STREAK", "offline-fabrication-operation", {
    base: {boxStreakDays: 10, nextBoxAtMs: T + DAY_MS}, claimed: {boxStreakDays: 50, boxesEarned: 40},
  });
  const result = await run(db, payload, T + 3 * DAY_MS + 1000);
  assert.equal(result.earnedBoxes, 3);
  const audit = [...db.store].filter(([path]) => path.startsWith(`${BOX_OVERCLAIM_PATH}/`));
  assert.equal(audit.length, 1);
  assert.equal(audit[0][1].surplusBoxes, 37);
  assert.equal(rows(db, CURRENCY.BOXES).reduce((n, row) => n + row.delta, 0), 3);
});

test("first offline word starts at server now, not Unix epoch, and cannot bank phantom decades", async () => {
  const db = makeDb({boxCount: 0, boxStreakDays: 0, nextBoxAtMs: 0});
  const first = await run(db, operation("OFFLINE_STREAK", "first-offline-operation", {
    base: {boxStreakDays: 0, nextBoxAtMs: 0}, claimed: {boxStreakDays: 10000, boxesEarned: 10000},
  }));
  assert.equal(first.boxStreakDays, 1);
  assert.equal(first.nextBoxAtMs, T + DAY_MS);
  assert.equal(first.boxCount, 0);
  const again = await run(db, operation("OFFLINE_STREAK", "next-offline-operation", {
    base: {boxStreakDays: 1, nextBoxAtMs: T + DAY_MS}, claimed: {boxStreakDays: 10000, boxesEarned: 10000},
  }), T + 1000);
  assert.equal(again.boxCount, 0);
  assert.equal(again.boxStreakDays, 1);
});

test("premium daily bonus uses one separate rolling slot, independent of streak, without ads", async () => {
  const db = makeDb({boxCount: 0, boxStreakDays: 9, nextBoxAtMs: T, premiumUntilMs: T + 3 * DAY_MS});
  const base = await run(db, operation("VISIT", "streak-tenth-day-operation"));
  assert.equal(base.earnedBoxes, 1);
  const bonus = await run(db, operation("PREMIUM_DAILY", "first-premium-daily-box"));
  assert.equal(bonus.boxCount, 2);
  assert.equal(bonus.nextBoxAtMs, T + DAY_MS);
  assert.equal(bonus.nextBonusBoxAtMs, T + DAY_MS);
  assert.equal((await run(db, operation("PREMIUM_DAILY", "second-premium-daily-box"), T + 1000)).status, "DAILY_LIMIT");
  assert.equal((await run(db, operation("PREMIUM_DAILY", "tomorrow-premium-daily-box"), T + DAY_MS)).earnedBoxes, 1);
  assert.equal(db.store.get(USER).boxCount, 3);
  assert.equal(db.store.get(USER).boxStreakDays, 10, "premium bonus does not advance streak");
});

test("sticky premium flag grants no daily box; exact expiry denies claim", async () => {
  const db = makeDb({boxCount: 0, hasPremium: true});
  assert.equal((await run(db, operation("PREMIUM_DAILY", "sticky-premium-operation"))).status, "PREMIUM_REQUIRED");
  assert.equal(db.store.get(USER).boxCount, 0);
  const expires = makeDb({boxCount: 0, premiumUntilMs: T + 1000});
  await run(expires, operation("VISIT", "bootstrap-premium-clock"));
  assert.equal((await run(expires, operation("PREMIUM_DAILY", "expired-premium-operation"), T + 1000)).status, "PREMIUM_REQUIRED");
});

test("refund preserves already claimed bonus; repurchase does not reset its daily slot", async () => {
  const db = makeDb({boxCount: 0});
  await buyPremium(db);
  await run(db, operation("PREMIUM_DAILY", "paid-premium-daily-box"));
  await refundPremium(db, T + 1000);
  assert.equal(db.store.get(USER).boxCount, 1);
  assert.equal(db.store.get(USER)[BONUS_CLOCK_FIELD], T + DAY_MS);
  assert.equal((await run(db, operation("PREMIUM_DAILY", "refunded-premium-daily-box"), T + 1000)).status, "PREMIUM_REQUIRED");
  await buyPremium(db, T + 2000, "new-valid-box-premium-token-1234567890");
  assert.equal((await run(db, operation("PREMIUM_DAILY", "repurchased-premium-daily-box"), T + 2000)).status, "DAILY_LIMIT");
  assert.equal((await run(db, open(), T + 2000)).status, "OPENED");
});

test("premium from opening is a distinct BOX lot; Play refund cannot take it", async () => {
  const db = makeDb({boxCount: 1});
  await buyPremium(db);
  const opened = await run(db, open(), T + 600, PREMIUM);
  assert.equal(opened.reward.amount, 86400);
  assert.equal(db.store.get(USER).lifePoints, 1);
  assert.equal(db.store.get(USER).plasmaPoints, 1);
  const sourceId = boxOperationId(OPENING);
  const giftPath = `${HEADER}/lots/${premiumGrantId(SOURCE.BOX, sourceId)}`;
  assert.equal(db.store.get(giftPath).sourceId, sourceId);
  assert.equal(db.store.get(giftPath).durationMs, DAY_MS);
  await refundPremium(db, T + 1000);
  assert.equal(db.store.get(USER).premiumUntilMs, T + 1000 + DAY_MS);
  assert.equal(db.store.get(giftPath).revokedMs, 0);
  assert.equal(db.store.get(giftPath).consumedMs, 0);
});

test("an empty opening never issues its preselected premium roll, and old refusal id stays final", async () => {
  const db = makeDb({boxCount: 0});
  const refused = await run(db, open(), T, PREMIUM);
  assert.equal(refused.status, "NO_BOXES");
  assert.equal(db.store.get(HEADER).totalRemainingMs, 0);
  db.store.set(USER, {...db.store.get(USER), boxCount: 1});
  assert.deepEqual(await run(db, open(), T + 1000, PREMIUM), refused);
  assert.equal(db.store.get(USER).boxCount, 1);
});

test("gold drops clear refund debt first and journal only actual spendable gold increase", async () => {
  for (const shortfall of [30, 15, 0]) {
    const db = makeDb({[SHORTFALL_FIELD]: shortfall});
    const result = await run(db, open(), T, GOLD20);
    const cleared = Math.min(shortfall, 20);
    assert.equal(result.reward.amount, 20);
    assert.equal(result.goldCredit.shortfallCleared, cleared);
    assert.equal(db.store.get(USER).gold, 8 + 20 - cleared);
    assert.equal(db.store.get(USER)[SHORTFALL_FIELD], shortfall - cleared);
    assert.equal(rows(db, CURRENCY.GOLD).reduce((n, row) => n + row.delta, 0), 20 - cleared);
  }
  assert.throws(() => planGoldCredit({gold: 0, shortfall: 0, gross: 0.5}), /exact nonnegative/);
  assert.throws(() => planGoldCredit({gold: Number.MAX_SAFE_INTEGER, shortfall: 0, gross: 1}), /exceeds/);
});

test("named trophy ownership is preserved, verified never drops, complete collection gets shipped gold fallback", async () => {
  const db = makeDb({trophies: [TROPHY_VERIFIED, ...GIFT_BOX_TROPHY_NAMES.slice(1)]});
  const one = await run(db, open(), T, draws(0.01, 0.99, 0.3, 0.9, 0.99));
  assert.equal(one.reward.itemName, GIFT_BOX_TROPHY_NAMES[0]);
  assert.ok(db.store.get(USER).trophies.includes(TROPHY_VERIFIED));
  const two = await run(db, open("another-trophy-opening"), T, draws(0.01, 0.99, 0.3, 0.9, 0));
  assert.deepEqual(two.reward, {type: "addGold", amount: 10});
});

test("failed commit rolls back box, reward, premium, receipt and ledger together", async () => {
  const db = makeDb();
  const before = structuredClone([...db.store]);
  db.failNextCommit();
  await assert.rejects(run(db, open(), T, PREMIUM), /atomic commit failure/);
  assert.deepEqual([...db.store], before);
  assert.equal((await run(db, open(), T, PREMIUM)).status, "OPENED");
  assert.equal(db.store.get(USER).boxCount, 1);
  assert.equal(db.store.get(HEADER).totalRemainingMs, DAY_MS);
});

test("composable read/plan/write commits with caller's combined user patch and never reads after writes", async () => {
  const db = makeDb();
  const result = await db.runTransaction(async (transaction) => {
    const snapshot = await readBoxOperation({db, transaction, uid: UID, payload: open(), entropy: COMMON});
    const plan = planBoxOperation({snapshot, now: T});
    writeBoxOperation({db, transaction, plan});
    transaction.set(db.doc(USER), {...plan.userPatch, integrationMarker: "combined"}, {merge: true});
    return plan.response;
  });
  assert.equal(result.status, "OPENED");
  assert.equal(db.store.get(USER).integrationMarker, "combined");
});

test("long premium catch-up cannot spend box or expose a reward before final atomic opening", async () => {
  const db = makeDb({boxCount: 1});
  db.store.set(HEADER, {schemaVersion: 1, asOfMs: T, totalRemainingMs: 300 * DAY_MS, nextSequence: 300,
    legacyBootstrap: {atMs: T, premiumUntilMs: 0, grantId: null}, chargeRates: {standard: 1000, plasma: 1000}});
  for (let sequence = 0; sequence < 300; sequence++) {
    const sourceId = `gold-fixture-${sequence}`;
    const grantId = premiumGrantId(SOURCE.GOLD, sourceId);
    db.store.set(`${HEADER}/lots/${grantId}`, {grantId, source: SOURCE.GOLD, sourceId, sequence, active: true,
      issuedAtMs: T, durationMs: DAY_MS, consumedMs: 0, revokedMs: 0, refundedAtMs: null});
  }
  const now = T + 299 * DAY_MS + DAY_MS / 2;
  await assert.rejects(run(db, open(), now, PREMIUM), {code: "UNAVAILABLE"});
  assert.equal(db.store.get(USER).boxCount, 1);
  assert.equal(db.store.has(recordPath(OPENING)), false);
  assert.equal(db.store.get(HEADER).asOfMs, T + 256 * DAY_MS);
  const result = await run(db, open(), now, PREMIUM);
  assert.equal(result.status, "OPENED");
  assert.equal(result.boxCount, 0);
  assert.equal(result.premiumUntilMs, T + 301 * DAY_MS);
  assert.ok(db.queries.every((query) => query.maximum === 65));
  assert.ok(db.commits.every((commit) => commit.length < 80));
});

async function main() {
  for (const {name, body} of tests) { await body(); console.log(`ok - ${name}`); }
  console.log(`box-service: ${tests.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
