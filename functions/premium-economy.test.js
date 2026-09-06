"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {readEconomyConstants} = require("./economy-constants");
const {DAY_MS, SOURCE, LEGACY_GRANT_ID} = require("./premium-grants");
const {premiumGrantId, readPremiumEconomy, planPremiumEconomy, writePremiumEconomy, PREMIUM_LOT_BATCH} = require("./premium-economy");
const {LEDGER_PATH, CURRENCY} = require("./currency-ledger");

const UID = "premium-test";
const USER = `users/${UID}`;
const HEADER = `premium_accounts/${UID}`;
const LOT = `${HEADER}/lots/`;
const T = 1_800_000_000_000;
const constants = readEconomyConstants({standard: {regenMs: 100_000}, plasma: {regenMs: 100_000}});
const user = (extra = {}) => ({standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
  lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T, ...extra});
const grant = (source, sourceId, days = 1) => ({kind: "GRANT", source, sourceId, days});
const refund = (id) => ({kind: "REFUND", grantId: premiumGrantId(SOURCE.PLAY, id)});

async function step(db, now, operation = {kind: "ADVANCE"}, rules = constants) {
  return db.runTransaction(async (transaction) => {
    const [u] = await transaction.getAll(db.doc(USER));
    const snapshot = await readPremiumEconomy({db, transaction, uid: UID, operation});
    const plan = planPremiumEconomy({snapshot, userData: u.data(), constants: rules, now, operation});
    writePremiumEconomy({db, transaction, uid: UID, plan});
    transaction.set(db.doc(USER), plan.userPatch, {merge: true});
    return plan;
  });
}
const tests = [];
function test(name, body) { tests.push({name, body}); }

test("sticky legacy bool never fabricates premium; bootstrap is permanent", async () => {
  const db = premiumTestDb({[USER]: user({hasPremium: true})});
  await step(db, T);
  assert.equal(db.store.get(USER).hasPremium, false);
  assert.equal(db.store.get(HEADER).totalRemainingMs, 0);
  db.store.set(USER, {...db.store.get(USER), premiumUntilMs: T + DAY_MS});
  await step(db, T + 1);
  assert.equal(db.store.get(USER).premiumUntilMs, 0);
  assert.equal([...db.store.keys()].filter((key) => key.startsWith(LOT)).length, 0);
});

test("legacy future-only bootstrap retains exhausted tombstone through later purchase/refund", async () => {
  const db = premiumTestDb({[USER]: user({premiumUntilMs: T + 1000})});
  await step(db, T);
  await step(db, T + 2000);
  assert.equal(db.store.get(`${LOT}${LEGACY_GRANT_ID}`).active, false);
  await step(db, T + 2000, grant(SOURCE.PLAY, "p"));
  const result = await step(db, T + 2500, refund("p"));
  assert.equal(result.revokedNowMs, DAY_MS - 500);
  assert.equal(result.premiumUntilMs, 0);
  assert.equal(db.store.get(`${LOT}${LEGACY_GRANT_ID}`).durationMs, 1000);
  assert.equal(db.store.get(HEADER).legacyBootstrap.premiumUntilMs, T + 1000);
});

test("exact Play refund preserves consumed time and later BOX/GOLD/PLAY gifts, without debt", async () => {
  const db = premiumTestDb({[USER]: user({gold: 70, purchaseRefundShortfallGold: 9})});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  await step(db, T, grant(SOURCE.BOX, "box", 7));
  await step(db, T, grant(SOURCE.GOLD, "gold", 30));
  await step(db, T, grant(SOURCE.PLAY, "p2", 365));
  const before = new Map(db.store);
  const result = await step(db, T + 600, refund("p"));
  assert.equal(result.revokedNowMs, DAY_MS - 600);
  assert.equal(result.grant.consumedMs, 600);
  assert.equal(result.premiumUntilMs, T + 600 + (7 + 30 + 365) * DAY_MS);
  assert.equal(result.userPatch.lifePoints, 1);
  assert.equal(result.userPatch.plasmaPoints, 1);
  assert.equal(result.userPatch.lifeRegenRemainderMs, 200);
  assert.equal(db.store.get(USER).gold, 70);
  assert.equal(db.store.get(USER).purchaseRefundShortfallGold, 9);
  for (const [source, id] of [[SOURCE.BOX, "box"], [SOURCE.GOLD, "gold"], [SOURCE.PLAY, "p2"]]) {
    const path = LOT + premiumGrantId(source, id);
    assert.deepEqual(db.store.get(path), before.get(path));
  }
  const replay = await step(db, T + 600, refund("p"));
  assert.equal(replay.outcome, "REPLAYED");
  assert.equal(replay.revokedNowMs, 0);
});

test("grant replay is idempotent and conflicting duration rejected without writes", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.GOLD, "g", 7));
  const replay = await step(db, T + 10, grant(SOURCE.GOLD, "g", 7));
  assert.equal(replay.outcome, "REPLAYED");
  assert.equal(replay.premiumUntilMs, T + 7 * DAY_MS);
  const before = structuredClone([...db.store]);
  await assert.rejects(step(db, T + 20, grant(SOURCE.GOLD, "g", 30)), {code: "CONFLICTING_REPLAY"});
  assert.deepEqual([...db.store], before);
});

test("both balances get x2 only until expiry, then normal rate; no cap banking", async () => {
  const db = premiumTestDb({[USER]: user({premiumUntilMs: T + 600})});
  await step(db, T);
  const result = await step(db, T + 1000);
  assert.deepEqual(result.elapsed.activeIntervals, [{fromMs: T, toMs: T + 600}]);
  assert.equal(result.userPatch.lifePoints, 1);
  assert.equal(result.userPatch.lifeRegenRemainderMs, 600);
  assert.equal(result.userPatch.plasmaPoints, 1);
  assert.equal(result.userPatch.plasmaRegenRemainderMs, 600);
  await step(db, T + 1400);
  assert.equal(db.store.get(USER).lifePoints, 2);
  assert.equal(db.store.get(USER).plasmaPoints, 2);
  await step(db, T + DAY_MS);
  assert.equal(db.store.get(USER).lifePoints, 500);
  assert.equal(db.store.get(USER).plasmaPoints, 100);
  assert.equal(db.store.get(USER).lifeRegenRemainderMs, 0);
});

test("no-refund debt and no loss of already delivered regen after removing last premium", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  await step(db, T + 500, refund("p"));
  assert.equal(db.store.get(USER).lifePoints, 1);
  await step(db, T + 1500);
  assert.equal(db.store.get(USER).lifePoints, 2);
  assert.equal(db.store.get(USER).plasmaPoints, 2);
  assert.equal(db.store.get(USER).hasPremium, false);
});

test("backwards clock cannot add premium or advance either charge cursor", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  const result = await step(db, T - 1000);
  assert.equal(result.header.asOfMs, T);
  assert.equal(result.premiumUntilMs, T + DAY_MS);
  assert.equal(result.userPatch.lifePoints, 0);
});

test("changed regen rate settles old gap first, preserves earned work and does not block a refund", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  await step(db, T + 450);
  assert.equal(db.store.get(USER).lifeRegenRemainderMs, 900);
  const faster = readEconomyConstants({standard: {regenMs: 40_000}, plasma: {regenMs: 40_000}});
  const result = await step(db, T + 550, refund("p"), faster);
  assert.equal(result.userPatch.lifePoints, 1); // 1100 old work -> one point, 100 work remains.
  assert.equal(result.userPatch.plasmaPoints, 1);
  assert.equal(result.userPatch.lifeRegenRemainderMs, 100);
  assert.deepEqual(result.header.chargeRates, {standard: 400, plasma: 400});
  await step(db, T + 850, {kind: "ADVANCE"}, faster);
  assert.equal(db.store.get(USER).lifePoints, 2);
  assert.equal(db.store.get(USER).plasmaPoints, 2);
});

test("offline ticket reservations occupy charge capacity and are never overwritten by regeneration", async () => {
  const db = premiumTestDb({[USER]: user({reservedLifePoints: 300, reservedPlasmaPoints: 60})});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  const result = await step(db, T + DAY_MS);
  assert.equal(result.userPatch.lifePoints, 200);
  assert.equal(result.userPatch.plasmaPoints, 40);
  assert.equal(db.store.get(USER).reservedLifePoints, 300);
  assert.equal(db.store.get(USER).reservedPlasmaPoints, 60);
  assert.equal(result.userPatch.lifeRegenRemainderMs, 0);
  assert.equal(result.userPatch.plasmaRegenRemainderMs, 0);
});

test("unknown/non-Play refund and mismatched charge cursor fail atomically", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.BOX, "b"));
  await assert.rejects(step(db, T, refund("missing")), {code: "GRANT_NOT_FOUND"});
  await assert.rejects(step(db, T, {kind: "REFUND", grantId: premiumGrantId(SOURCE.BOX, "b")}), {code: "NOT_PLAY_GRANT"});
  db.store.set(USER, {...db.store.get(USER), plasmaPointsUpdatedAtMs: T + 1});
  const before = structuredClone([...db.store]);
  await assert.rejects(step(db, T + 100), {code: "CURSOR_MISMATCH"});
  assert.deepEqual([...db.store], before);
});

test("read budget does not cap issue or strand queued-target refunds", async () => {
  const db = premiumTestDb({[USER]: user()});
  for (let n = 0; n < 70; n++) await step(db, T, grant(SOURCE.PLAY, `p${n}`));
  const first = await step(db, T + 200, refund("p69"));
  assert.equal(first.caughtUp, true);
  assert.equal(first.revokedNowMs, DAY_MS);
  assert.equal(first.header.totalRemainingMs, 69 * DAY_MS - 200);
  const result = await step(db, T + 68 * DAY_MS, refund("p68"));
  assert.equal(result.caughtUp, false);
  assert.equal(result.header.asOfMs, T + 64 * DAY_MS);
  assert.equal(db.store.get(LOT + premiumGrantId(SOURCE.PLAY, "p68")).refundedAtMs, null);
  const final = await step(db, T + 68 * DAY_MS, refund("p68"));
  assert.equal(final.caughtUp, true);
  assert.equal(final.revokedNowMs, DAY_MS);
  assert.equal(final.header.totalRemainingMs, 0);
  assert.ok(db.queries.every((q) => q.maximum === PREMIUM_LOT_BATCH + 1));
  assert.ok(db.commits.every((commit) => commit.length < 80));
});

test("commit failure leaves header/lots/both balances/ledger unchanged", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  const before = structuredClone([...db.store]);
  db.failNextCommit();
  await assert.rejects(step(db, T + 600, refund("p")), /atomic commit failure/);
  assert.deepEqual([...db.store], before);
});

test("premium ledger equals remaining entitlement; regen ledger equals both actual balances", async () => {
  const db = premiumTestDb({[USER]: user()});
  await step(db, T, grant(SOURCE.PLAY, "p"));
  await step(db, T + 700, refund("p"));
  const rows = [...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).map(([, value]) => value);
  assert.equal(rows.filter((row) => row.currency === "premiumMs").reduce((n, row) => n + row.delta, 0), 0);
  for (const [currency, field] of [[CURRENCY.STANDARD_CHARGE_POINTS, "lifePoints"], [CURRENCY.PLASMA_CHARGE_POINTS, "plasmaPoints"]]) {
    assert.equal(rows.filter((row) => row.currency === currency).reduce((n, row) => n + row.delta, 0), db.store.get(USER)[field]);
  }
});

async function main() {
  for (const {name, body} of tests) { await body(); process.stdout.write(`ok - ${name}\n`); }
  process.stdout.write(`premium-economy: ${tests.length} behavioral tests passed\n`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });

module.exports = {step};
