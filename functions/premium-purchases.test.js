"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {DAY_MS, SOURCE, LEGACY_GRANT_ID} = require("./premium-grants");
const {readEconomyConstants, premiumSaleOffer, ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const {settlementId, accountIdFor, SHORTFALL_FIELD, PLAY_OUTCOME_FOUND, PLAY_OUTCOME_NOT_FOUND, PLAY_OUTCOME_UNAVAILABLE} = require("./purchase-verification");
const {settlePurchase, settleSubscriptionRenewal} = require("./purchase-settlement");
const {clawBackPurchase} = require("./purchase-clawback");
const {refundDocId} = require("./purchase-notifications");
const {premiumGrantId, readPremiumEconomy, planPremiumEconomy, writePremiumEconomy} = require("./premium-economy");
const {LEDGER_PATH} = require("./currency-ledger");

const T = 1_800_000_000_000;
const UID = "premium-buyer";
const PACKAGE = "com.tpov.schoolquiz";
const SKU = "owner.configured_membership"; // Test fixture, deliberately not a production default.
const TOKEN = "valid-test-purchase-token-1234567890";
const ID = settlementId(TOKEN);
// Срок, который фейковый Play отвечает на subscriptionsv2: грант называется токеном и сроком
// (история 2.3), так что грант и документы срока выводятся из него же.
const EXPIRY = T + 365 * DAY_MS;
const TERM_ID = `${ID}_${EXPIRY}`;
const USER = `users/${UID}`;
const HEADER = `premium_accounts/${UID}`;
const LOT = `${HEADER}/lots/`;
const SETTLEMENT = `purchase_settlements/${ID}`;
const RECEIPT = `users/${UID}/receipts/${TERM_ID}`;
const AUDIT = `purchase_audit/${TERM_ID}`;
const GRANT_ID = premiumGrantId(SOURCE.PLAY, TERM_ID);
const config = (days = 1, extra = {}) => ({version: 20, standard: {regenMs: 100_000}, plasma: {regenMs: 100_000},
  premiumProducts: {[SKU]: {days, salesEnabled: true}}, ...extra});
const user = () => ({gold: 40, [SHORTFALL_FIELD]: 11, standardHearts: 5, goldHearts: 1,
  lifePoints: 0, plasmaPoints: 0, lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T});
const makeDb = (document = config()) => premiumTestDb({[USER]: user(), [ECONOMY_CONSTANTS_DOC]: document});
const readGold = (data) => data.gold || 0;
function fakeLog() { const entries = []; return {entries, warn: (...args) => entries.push(args), error: (...args) => entries.push(args)}; }
function fakePlay(overrides = {}) {
  const calls = [];
  return {calls, packageName: PACKAGE, async getProductPurchase(sku, token) {
    calls.push({sku, token});
    return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
      purchaseState: 0, consumptionState: 0, productId: sku, quantity: 1,
      purchaseTimeMillis: String(T - 1000), orderId: "GPA.1111-2222-3333-44444",
      acknowledgementState: 0, obfuscatedExternalAccountId: accountIdFor(UID), ...overrides,
    }};
  }, async getSubscriptionPurchaseV2(token) {
    calls.push({token});
    // Срок на год вперёд: грант режется потолком товарной строки таблицы, так что сам срок в
    // этих тестах задаёт конфиг, а не фейк Play. Продление (история 2.3) подставляет больший
    // срок тем же токеном — читая `expiry` из opts.
    const expiry = overrides.expiry === undefined ? EXPIRY : overrides.expiry;
    return {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {
      subscriptionState: 2, startTime: new Date(T - 1000).toISOString(),
      latestOrderId: "GPA.1111-2222-3333-44444", regionCode: "UA",
      externalAccountIdentifiers: {obfuscatedExternalAccountId: accountIdFor(UID)},
      lineItems: [{productId: SKU, expiryTime: new Date(expiry).toISOString()}],
    }};
  }};
}
function buy(db, now = T, opts = {}) {
  return settlePurchase({db, playApi: fakePlay(), now, uid: UID,
    payload: {sku: SKU, purchaseToken: TOKEN}, readGold, log: fakeLog(), ...opts});
}
function notification(token = TOKEN, messageId = "premium-refund-message") {
  const value = {version: "1.0", packageName: PACKAGE, eventTimeMillis: String(T),
    voidedPurchaseNotification: {purchaseToken: token, orderId: "GPA.1111-2222-3333-44444", productType: 2, refundType: 1}};
  return {messageId, publishTime: new Date(T).toISOString(), data: Buffer.from(JSON.stringify(value)).toString("base64")};
}
function refund(db, now = T, opts = {}) {
  return clawBackPurchase({db, playApi: fakePlay({purchaseState: 1, consumptionState: 1}), now,
    message: notification(), readGold, log: fakeLog(), ...opts});
}
async function otherGrant(db, source, sourceId, days) {
  await db.runTransaction(async (transaction) => {
    const [u, c] = await transaction.getAll(db.doc(USER), db.doc(ECONOMY_CONSTANTS_DOC));
    const operation = {kind: "GRANT", source, sourceId, days};
    const snapshot = await readPremiumEconomy({db, transaction, uid: UID, operation});
    const plan = planPremiumEconomy({snapshot, userData: u.data(), constants: readEconomyConstants(c.data()), now: T, operation});
    assert.equal(plan.caughtUp, true);
    writePremiumEconomy({db, transaction, uid: UID, plan});
    transaction.set(db.doc(USER), plan.userPatch, {merge: true});
  });
}
const tests = [];
function test(name, body) { tests.push({name, body}); }

test("no implicit SKU/price, exact duration set and fail-closed new-sale offers", () => {
  assert.deepEqual(readEconomyConstants(null).premiumProducts, {});
  assert.equal(premiumSaleOffer(readEconomyConstants(null), SKU, "PLAY"), null);
  const values = readEconomyConstants({premiumProducts: {
    [SKU]: {days: 7, salesEnabled: true},
    bad_days: {days: 2, salesEnabled: true, goldPrice: 10},
    string_days: {days: "7", salesEnabled: true, goldPrice: 10},
    disabled: {days: 1, goldPrice: 10},
    string_price: {days: 1, salesEnabled: true, goldPrice: "10"},
    priced: {days: 365, salesEnabled: true, goldPrice: 123},
  }});
  assert.deepEqual(premiumSaleOffer(values, SKU, "PLAY"), {sku: SKU, days: 7, payment: "PLAY"});
  assert.equal(premiumSaleOffer(values, SKU, "GOLD"), null);
  assert.equal(premiumSaleOffer(values, "bad_days", "PLAY"), null);
  assert.equal(premiumSaleOffer(values, "string_days", "PLAY"), null);
  assert.equal(premiumSaleOffer(values, "disabled", "PLAY"), null);
  assert.equal(premiumSaleOffer(values, "string_price", "GOLD"), null);
  assert.deepEqual(premiumSaleOffer(values, "priced", "GOLD"), {sku: "priced", days: 365, payment: "GOLD", goldPrice: 123});
  const ambiguous = readEconomyConstants({premiumProducts: {gold_pack_small: {days: 1, salesEnabled: true}}});
  assert.equal(premiumSaleOffer(ambiguous, "gold_pack_small", "PLAY"), null);
});

test("all four configured Play durations settle atomically without changing gold/debt", async () => {
  for (const days of [1, 7, 30, 365]) {
    const db = makeDb(config(days));
    const result = await buy(db);
    assert.equal(result.kind, "response");
    assert.equal(result.response.status, "CREDITED");
    assert.equal(result.response.productKind, "PREMIUM");
    assert.equal(result.response.premiumGrantedMs, days * DAY_MS);
    assert.equal(result.response.premiumUntilMs, T + days * DAY_MS);
    assert.equal(result.response.premiumGrantId, GRANT_ID);
    assert.equal(result.response.goldGranted, 0);
    assert.equal(db.store.get(USER).gold, 40);
    assert.equal(db.store.get(USER)[SHORTFALL_FIELD], 11);
    assert.equal(db.store.get(HEADER).totalRemainingMs, days * DAY_MS);
    assert.equal(db.store.get(LOT + GRANT_ID).sourceId, TERM_ID);
    assert.equal(db.store.get(SETTLEMENT).premiumDays, days);
    assert.equal(db.store.get(SETTLEMENT).subscriptionExpiryMs, EXPIRY);
    assert.equal(db.store.get(RECEIPT).currency, "PREMIUM_MS");
    assert.equal(db.store.get(AUDIT).premiumGrantedMs, days * DAY_MS);
    const ledger = [...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).map(([, row]) => row);
    assert.equal(ledger.filter((row) => row.currency === "premiumMs").reduce((n, row) => n + row.delta, 0), days * DAY_MS);
    assert.ok(!JSON.stringify([...db.store]).includes(TOKEN), "raw token must never be persisted");
    assert.ok(!Array.isArray(db.store.get(USER).lots), "lots must not grow user document");
    assert.equal(db.commits.length, 1);
  }
});

test("sales off does not block verified paid receipt; removed config does not block known replay/refund", async () => {
  const db = makeDb(config(7, {premiumProducts: {[SKU]: {days: 7, salesEnabled: false}}}));
  assert.equal((await buy(db)).kind, "response");
  db.store.set(ECONOMY_CONSTANTS_DOC, {});
  // Повтор рассчитанного премиум-токена больше не отвечает до Play (история 2.3): свежий срок —
  // единственный способ узнать продление. Но конфиг ему не нужен: без таблицы маршрут уводит на
  // товарный endpoint, там токен подписки не находится — и решение отвечает сохранённым ответом.
  const noConfig = {packageName: PACKAGE, async getProductPurchase() {
    return {outcome: PLAY_OUTCOME_NOT_FOUND, status: 404};
  }};
  const replay = await buy(db, T + 100, {playApi: noConfig});
  assert.deepEqual(replay.response, db.store.get(SETTLEMENT).response);
  const returned = await refund(db, T + 100);
  assert.equal(returned.kind, "CLAWED_BACK");
  assert.equal(returned.premiumRevokedMs, 7 * DAY_MS - 100);
});

test("renewal extends instead of replacing: later expiry, same token, one grant per term", async () => {
  const db = makeDb(config(7));
  assert.equal((await buy(db)).response.premiumUntilMs, T + 7 * DAY_MS);
  const renewedAt = T + DAY_MS;
  const renewedExpiry = EXPIRY + 30 * DAY_MS;
  const result = await buy(db, renewedAt, {playApi: fakePlay({expiry: renewedExpiry})});
  assert.equal(result.kind, "response");
  // max(сейчас, окончание) + длительность: лот поверх активного, а не вместо него.
  assert.equal(result.response.premiumUntilMs, T + 14 * DAY_MS);
  const renewalGrantId = premiumGrantId(SOURCE.PLAY, `${ID}_${renewedExpiry}`);
  assert.equal(result.response.premiumGrantId, renewalGrantId);
  assert.equal(db.store.get(HEADER).totalRemainingMs, 13 * DAY_MS);
  assert.equal(db.store.get(HEADER).nextSequence, 2);
  const record = db.store.get(SETTLEMENT);
  assert.equal(record.subscriptionExpiryMs, renewedExpiry);
  assert.equal(record.subscriptionRenewals, 1);
  assert.equal(record.premiumGrantId, renewalGrantId);
  // Квитанция и аудит срока — рядом, не поверх первого.
  assert.equal(db.store.get(`users/${UID}/receipts/${TERM_ID}`).createdAtMs, T);
  assert.equal(db.store.get(`users/${UID}/receipts/${ID}_${renewedExpiry}`).amount, 7 * DAY_MS);
  assert.equal(db.store.get(`purchase_audit/${ID}_${renewedExpiry}`).premiumDays, 7);
  // Тот же срок ещё раз — повтор: ни лота, ни квитанции, ни движения книги.
  const writes = db.commits.flat().length;
  const replay = await buy(db, renewedAt + 100, {playApi: fakePlay({expiry: renewedExpiry})});
  assert.deepEqual(replay.response, record.response);
  assert.equal(db.commits.flat().length, writes);
  assert.equal(db.store.get(HEADER).nextSequence, 2);
});

test("unknown paid SKU is unconsumed with explicit config diagnostic, can retry after config repair", async () => {
  const db = makeDb({});
  const log = fakeLog();
  const result = await buy(db, T, {log});
  assert.equal(result.kind, "refuse"); // Existing gold-only API error shape intentionally preserved.
  assert.equal(result.reasonCode, "SKU_NOT_SOLD");
  assert.equal(db.store.has(SETTLEMENT), false);
  assert.equal(db.store.has(HEADER), false);
  assert.match(JSON.stringify(log.entries), /stays unconsumed/);
  db.store.set(ECONOMY_CONSTANTS_DOC, config());
  assert.equal((await buy(db)).response.status, "CREDITED");
});

test("premium subscription settles as itself and a conflicting class fails without zero-value settlement", async () => {
  // Подписка приходит только из своего endpoint'а и всегда quantity=1 по форме API: товарная
  // форма ответа о премиуме — отказ без срока (покрыт в purchase-verification.test.js), а не
  // угадывание. Осталось честным: премиум-SKU, влезший и в goldPacks, не продаётся вовсе.
  const db = makeDb();
  const settled = await buy(db);
  assert.equal(settled.response.status, "CREDITED");
  const ambiguous = makeDb(config(1, {goldPacks: {[SKU]: 50}}));
  assert.equal((await buy(ambiguous)).reasonCode, "SKU_NOT_SOLD");
  assert.equal(ambiguous.store.has(SETTLEMENT), false);
});

test("refund revokes only unused exact Play grant; BOX/GOLD/other Play preserved with both prior regen benefits", async () => {
  const db = makeDb();
  await buy(db);
  await otherGrant(db, SOURCE.BOX, "box-receipt", 7);
  await otherGrant(db, SOURCE.GOLD, "gold-receipt", 30);
  await buy(db, T, {payload: {sku: SKU, purchaseToken: "another-valid-purchase-token-1234567890"}});
  const protectedLots = [...db.store].filter(([path]) => path.startsWith(LOT) && path !== LOT + GRANT_ID);
  const result = await refund(db, T + 600);
  assert.equal(result.kind, "CLAWED_BACK");
  assert.equal(result.premiumRevokedMs, DAY_MS - 600);
  assert.equal(result.premiumConsumedMs, 600);
  assert.equal(result.premiumUntilMs, T + 600 + 38 * DAY_MS);
  assert.equal(result.goldTakenBack, 0);
  assert.equal(result.shortfall, 11);
  assert.equal(db.store.get(USER).lifePoints, 1);
  assert.equal(db.store.get(USER).plasmaPoints, 1);
  assert.equal(db.store.get(USER).gold, 40);
  assert.equal(db.store.get(USER)[SHORTFALL_FIELD], 11);
  for (const [path, value] of protectedLots) assert.deepEqual(db.store.get(path), value);
  assert.equal(db.store.get(SETTLEMENT).status, "REFUNDED");
  assert.equal(db.store.get(SETTLEMENT).premiumConsumedMs, 600);
  assert.equal(db.store.get(`users/${UID}/receipts/${refundDocId(ID)}`).amount, -(DAY_MS - 600));
  const writes = db.commits.length;
  assert.equal((await refund(db, T + 600)).replayed, true);
  assert.equal(db.commits.length, writes);
  const recheck = await refund(db, T + 600, {message: notification(TOKEN, "different-rtdn-id")});
  assert.equal(recheck.kind, "ALREADY_REFUNDED");
  assert.equal(db.store.get(LOT + GRANT_ID).revokedMs, DAY_MS - 600);
});

test("legacy future provenance survives purchase and later exhausted-lot refund", async () => {
  const db = makeDb();
  db.store.set(USER, {...user(), premiumUntilMs: T + 500, hasPremium: true});
  await buy(db);
  const returned = await refund(db, T + DAY_MS + 1000);
  assert.equal(returned.premiumRevokedMs, 0);
  assert.equal(returned.premiumConsumedMs, DAY_MS);
  assert.equal(db.store.get(USER).hasPremium, false);
  assert.equal(db.store.get(LOT + LEGACY_GRANT_ID).durationMs, 500);
  assert.equal(db.store.get(LOT + LEGACY_GRANT_ID).active, false);
});

test("commit failure never leaves an orphan entitlement, receipt, debit or notification", async () => {
  const db = makeDb();
  const before = structuredClone([...db.store]);
  db.failNextCommit();
  assert.equal((await buy(db)).kind, "unavailable");
  assert.deepEqual([...db.store], before);
  await buy(db);
  const credited = structuredClone([...db.store]);
  db.failNextCommit();
  await assert.rejects(refund(db, T + 500), /atomic commit failure/);
  assert.deepEqual([...db.store], credited);
  assert.equal((await refund(db, T + 500)).kind, "CLAWED_BACK");
});

test("receipt race rechecks ownership/status before creating a second entitlement", async () => {
  const winner = makeDb();
  await buy(winner);
  const db = makeDb();
  db.beforeNextTransaction((store) => { for (const [path, value] of winner.store) store.set(path, structuredClone(value)); });
  const result = await buy(db);
  assert.deepEqual(result.response, winner.store.get(SETTLEMENT).response);
  assert.equal(db.store.get(HEADER).nextSequence, 1);
  assert.equal(db.commits.flat().length, 0);
});

test("settlement-to-grant mismatch cannot revoke another Play purchase", async () => {
  const db = makeDb();
  await buy(db);
  await otherGrant(db, SOURCE.PLAY, "different-receipt", 1);
  db.store.set(SETTLEMENT, {...db.store.get(SETTLEMENT), premiumGrantId: premiumGrantId(SOURCE.PLAY, "different-receipt")});
  const before = structuredClone([...db.store]);
  await assert.rejects(refund(db, T + 100), /provenance disagree/);
  assert.deepEqual([...db.store].filter(([path]) => !path.startsWith("purchase_revocations/")), before);
  assert.equal(db.store.get(`purchase_revocations/${ID}`).state, "PENDING_VERIFICATION");
});

test("renewal notification rides the record to the same settlement", async () => {
  // RTDN не знает ни uid, ни SKU — оба живут в записи расчёта; дальше работает тот же
  // settlePurchase без единой уступки (история 2.3).
  const db = makeDb(config(7));
  await buy(db);
  const later = EXPIRY + 30 * DAY_MS;
  const log = fakeLog();
  const outcome = await settleSubscriptionRenewal({db, playApi: fakePlay({expiry: later}), now: T + DAY_MS,
    purchaseToken: TOKEN, readGold, log});
  assert.equal(outcome.kind, "response");
  assert.equal(outcome.response.premiumUntilMs, T + 14 * DAY_MS);
  // Токен никогда не начислялся: признано, записей нет, повторов не просит.
  const stranger = makeDb(config(7));
  const ignored = await settleSubscriptionRenewal({db: stranger, playApi: fakePlay(), now: T,
    purchaseToken: "never-settled-token-1234567890", readGold, log});
  assert.equal(ignored.kind, "ignored");
  assert.equal(stranger.store.has(HEADER), false);
  assert.match(JSON.stringify(log.entries), /never settled/);
  // Play молчит — вердикта нет, сообщение приедет снова (index.js бросает по unavailable).
  const silent = await settleSubscriptionRenewal({db,
    playApi: {packageName: PACKAGE,
      async getSubscriptionPurchaseV2() { return {outcome: PLAY_OUTCOME_UNAVAILABLE, status: 503}; }},
    now: T, purchaseToken: TOKEN, readGold, log});
  assert.equal(silent.kind, "unavailable");
});

async function longHistory() {
  const db = makeDb();
  await buy(db);
  const first = db.store.get(LOT + GRANT_ID);
  // Fixed-size persisted lots, produced by the same issue-order schema, without 299 setup commits.
  for (let sequence = 1; sequence < 300; sequence++) {
    const sourceId = `gold-fixture-receipt-${sequence}`;
    const grantId = premiumGrantId(SOURCE.GOLD, sourceId);
    db.store.set(LOT + grantId, {...first, grantId, source: SOURCE.GOLD, sourceId, sequence});
  }
  db.store.set(HEADER, {...db.store.get(HEADER), nextSequence: 300, totalRemainingMs: 300 * DAY_MS});
  db.store.set(USER, {...db.store.get(USER), premiumUntilMs: T + 300 * DAY_MS});
  return db;
}

test("four-pass catch-up stops before purchase issuance, retry eventually credits without a sales cap", async () => {
  const db = await longHistory();
  const token = "pending-valid-purchase-token-1234567890";
  const now = T + 299 * DAY_MS + DAY_MS / 2;
  const payload = {sku: SKU, purchaseToken: token};
  const first = await buy(db, now, {payload});
  assert.equal(first.kind, "unavailable");
  assert.match(first.reason, /durable progress/);
  assert.equal(db.store.get(HEADER).asOfMs, T + 256 * DAY_MS);
  assert.equal(db.store.has(`purchase_settlements/${settlementId(token)}`), false);
  const second = await buy(db, now, {payload});
  assert.equal(second.response.status, "CREDITED");
  assert.equal(second.response.premiumUntilMs, now + 1.5 * DAY_MS);
  assert.equal(db.store.get(HEADER).nextSequence, 301);
  assert.ok(db.queries.every((q) => q.maximum === 65));
  assert.ok(db.commits.every((commit) => commit.length < 80));
});

test("four-pass catch-up never acknowledges a stranded refund, redelivery finishes exact expired target", async () => {
  const db = await longHistory();
  const now = T + 299 * DAY_MS + DAY_MS / 2;
  await assert.rejects(refund(db, now), /durable progress/);
  assert.equal(db.store.has("purchase_notifications/premium-refund-message"), false);
  assert.equal(db.store.get(SETTLEMENT).status, "CREDITED");
  assert.equal(db.store.get(HEADER).asOfMs, T + 256 * DAY_MS);
  const result = await refund(db, now);
  assert.equal(result.kind, "CLAWED_BACK");
  assert.equal(result.premiumRevokedMs, 0);
  assert.equal(result.premiumConsumedMs, DAY_MS);
  assert.equal(result.premiumUntilMs, T + 300 * DAY_MS);
  assert.equal(db.store.get(SETTLEMENT).status, "REFUNDED");
  assert.ok(db.store.has("purchase_notifications/premium-refund-message"));
  assert.ok(db.queries.every((q) => q.maximum === 65));
  assert.ok(db.commits.every((commit) => commit.length < 80));
});

async function main() {
  for (const {name, body} of tests) { await body(); process.stdout.write(`ok - ${name}\n`); }
  process.stdout.write(`premium-purchases: ${tests.length} behavioral tests passed\n`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
