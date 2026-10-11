"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {DAY_MS, SOURCE} = require("./premium-grants");
const {readEconomyConstants, ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const {LEDGER_PATH, REASON} = require("./currency-ledger");
const {premiumGrantId, writePremiumEconomy} = require("./premium-economy");
const {premiumGoldSaleOffer, purchasePremiumWithGold} = require("./premium-shop");

const T = 1_800_000_000_000;
const UID = "premium-gold-buyer";
const SKU = "premium_30d";
const USER = `users/${UID}`;
const HEADER = `premium_accounts/${UID}`;
const SOURCE_ID = `premium_gold_${UID}_${T}`;
const GRANT_ID = premiumGrantId(SOURCE.GOLD, SOURCE_ID);
const config = (premiumProducts = {[SKU]: {days: 30, salesEnabled: true, goldPrice: 500}}) => (
  {version: 21, standard: {regenMs: 100_000}, plasma: {regenMs: 100_000}, premiumProducts});
const readBalance = (data) => ({gold: data.gold || 0, hasPremium: data.hasPremium === true});

/** The callable's transaction body, verbatim except the real balance reader. */
async function buy(db, now = T, document = config()) {
  let response = null;
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(db.doc(USER));
    const storedUser = snapshot.exists ? snapshot.data() || {} : {};
    const purchased = await purchasePremiumWithGold(
      {db, transaction, uid: UID, storedUser, economy: readEconomyConstants(document), now, readBalance});
    if (!purchased.caughtUp) {
      writePremiumEconomy({db, transaction, uid: UID, plan: purchased.plan});
      return;
    }
    writePremiumEconomy({db, transaction, uid: UID, plan: purchased.plan});
    transaction.set(db.doc(USER), {
      ...purchased.plan.userPatch,
      hasPremium: purchased.balance.hasPremium,
      gold: purchased.balance.gold,
    }, {merge: true});
    response = purchased;
  });
  return response;
}

const tests = [];
function test(name, body) { tests.push({name, body}); }

test("the single gold-priced row of the table is the offer; zero or two sell nothing", () => {
  assert.deepEqual(premiumGoldSaleOffer(readEconomyConstants(config())),
    {sku: SKU, days: 30, payment: "GOLD", goldPrice: 500});
  assert.equal(premiumGoldSaleOffer(readEconomyConstants(config({}))), null);
  const two = config({
    [SKU]: {days: 30, salesEnabled: true, goldPrice: 500},
    premium_365d: {days: 365, salesEnabled: true, goldPrice: 5000},
  });
  assert.equal(premiumGoldSaleOffer(readEconomyConstants(two)), null);
  // Продажа выключена или цены нет — предложение не считается даже единственным.
  assert.equal(premiumGoldSaleOffer(readEconomyConstants(config({[SKU]: {days: 30, salesEnabled: false, goldPrice: 500}}))), null);
  assert.equal(premiumGoldSaleOffer(readEconomyConstants(config({[SKU]: {days: 30, salesEnabled: true}}))), null);
});

test("gold buys premium: the table's price leaves, the table's term arrives, both in one transaction", async () => {
  const db = premiumTestDb({[USER]: {gold: 600}, [ECONOMY_CONSTANTS_DOC]: config()});
  const result = await buy(db);
  assert.equal(result.caughtUp, true);
  assert.equal(result.balance.gold, 100);
  assert.equal(result.offer.days, 30);
  assert.equal(db.store.get(USER).gold, 100);
  assert.equal(db.store.get(USER).hasPremium, true);
  assert.equal(db.store.get(HEADER).totalRemainingMs, 30 * DAY_MS);
  const lot = [...db.store].find(([path]) => path.startsWith(`${HEADER}/lots/`));
  assert.ok(lot, "the gold lot must exist");
  assert.equal(lot[1].sourceId, SOURCE_ID);
  const ledger = [...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).map(([, row]) => row);
  assert.equal(ledger.filter((row) => row.reason === REASON.PREMIUM_GOLD_PURCHASE)
    .reduce((n, row) => n + row.delta, 0), -500);
  assert.equal(db.commits.length, 1);
});

test("without gold nothing moves: no term, no ledger, no user change", async () => {
  const db = premiumTestDb({[USER]: {gold: 499}, [ECONOMY_CONSTANTS_DOC]: config()});
  try {
    await buy(db);
    assert.fail("must refuse");
  } catch (error) {
    assert.equal(error.code, "FAILED_PRECONDITION");
  }
  assert.equal(db.store.has(HEADER), false);
  assert.equal(db.store.get(USER).gold, 499);
  assert.equal([...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).length, 0);
});

test("buying over active premium debits once and extends: max(now, end) + term", async () => {
  // История 2.3: второй покупкой игрок докупает срок, а не перезапускает его; цена — за один
  // срок, один раз. Источник выдачи назван моментом покупки, поэтому переигранная транзакция
  // узнаёт свой лот и не списывает золото второй раз.
  const db = premiumTestDb({[USER]: {gold: 1100}, [ECONOMY_CONSTANTS_DOC]: config()});
  await buy(db);
  const again = await buy(db, T + DAY_MS);
  assert.equal(again.balance.gold, 100);
  assert.equal(again.offer.days, 30);
  assert.equal(db.store.get(HEADER).totalRemainingMs, 59 * DAY_MS);
  assert.equal(db.store.get(USER).premiumUntilMs, T + 60 * DAY_MS);
  assert.equal([...db.store].filter(([path]) => path.startsWith(`${HEADER}/lots/`)).length, 2);
  const ledger = [...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).map(([, row]) => row);
  assert.equal(ledger.filter((row) => row.reason === REASON.PREMIUM_GOLD_PURCHASE)
    .reduce((n, row) => n + row.delta, 0), -1000);
});

test("a table without a gold offer refuses before any read of the account", async () => {
  const db = premiumTestDb({[USER]: {gold: 600}, [ECONOMY_CONSTANTS_DOC]: config({})});
  try {
    await buy(db, T, config({}));
    assert.fail("must refuse");
  } catch (error) {
    assert.equal(error.code, "INVALID_ARGUMENT");
  }
  assert.equal(db.store.has(HEADER), false);
  assert.equal(db.store.get(USER).gold, 600);
});

test("the same millisecond is the same purchase: no second debit, no second lot, no crash", async () => {
  // Матрица NFR2: повтор в ту же миллисекунду выводит тот же named key; грант дедуплицируется,
  // кошелёк не платит второй раз, и повтор отвечает состоянием, а не INTERNAL.
  const db = premiumTestDb({[USER]: {gold: 1100}, [ECONOMY_CONSTANTS_DOC]: config()});
  await buy(db);
  const replay = await buy(db, T);
  assert.equal(replay.balance.gold, 600, "повтор отвечает состоянием после первой покупки — списания нет");
  assert.equal([...db.store].filter(([path]) => path.startsWith(`${HEADER}/lots/`)).length, 1);
  const ledger = [...db.store].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`)).map(([, row]) => row);
  assert.equal(ledger.filter((row) => row.reason === REASON.PREMIUM_GOLD_PURCHASE)
    .reduce((n, row) => n + row.delta, 0), -500, "одна проводка покупки");
});

test("the client pricelist carries the offer: sku, term and gold price, nothing else", () => {
  const {clientEconomyConstants} = require("./economy-constants");
  const offers = clientEconomyConstants(readEconomyConstants(config())).premiumGoldOffers;
  assert.deepEqual(offers, [{sku: SKU, days: 30, goldPrice: 500}]);
  assert.deepEqual(clientEconomyConstants(readEconomyConstants(config({}))).premiumGoldOffers, []);
});

(async () => {
  let failed = 0;
  for (const {name, body} of tests) {
    try {
      await body();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.error(`✖ ${name}`);
      console.error(error);
    }
  }
  console.log(`premium-shop: ${tests.length - failed} behavioral tests passed`);
  if (failed) process.exitCode = 1;
})();
