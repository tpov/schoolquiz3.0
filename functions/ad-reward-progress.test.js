"use strict";

const assert = require("assert/strict");
const crypto = require("node:crypto");
const {premiumTestDb} = require("./premium-test-db");
const {
  AD_REWARD_KEY_UNKNOWN,
  REASON_BAD_USER_ID,
  REASON_MISSING_TRANSACTION_ID,
  REASON_UNKNOWN_ACCOUNT,
  REASON_UNPARSEABLE,
  adRewardCallbackId,
  adRewardConfirmationId,
  readAdRewardCallback,
} = require("./ad-reward-verification");
const {AD_REWARD_CALLBACKS_COLLECTION, AD_REWARD_CONFIRMATIONS_COLLECTION, AD_REWARD_VIEWS_FIELD,
  AD_REWARD_OUTCOME_ACKNOWLEDGED, AD_REWARD_OUTCOME_UNAVAILABLE, applyAdReward} = require("./ad-reward-progress");
const {AD_BOX_CLOCK_FIELD, AD_VIEWS_PER_BOX, BONUS_CLOCK_FIELD, DAY_MS} = require("./gift-boxes");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

const T = 1_800_000_000_000;
const UID = "box-player";
const USER = `users/${UID}`;
const TRANSACTION_ID = "0123456789abcdef";
const CONTENT = `ad_network=1953547073528090325&ad_unit=ca-app-pub-1/unit&reward_amount=1&reward_item=coin` +
  `&timestamp=${T}&transaction_id=${TRANSACTION_ID}&user_id=${UID}`;

const {privateKey, publicKey} = crypto.generateKeyPairSync("ec", {namedCurve: "prime256v1"});
const PUBLIC_PEM = publicKey.export({type: "spki", format: "pem"});
const KEYS = {keys: [{keyId: 101, pem: PUBLIC_PEM}]};
const KEYS_BY_ID = (id) => ({keys: [{keyId: id, pem: PUBLIC_PEM}]});

function sign(content, key = privateKey) {
  return crypto.sign("sha256", Buffer.from(content, "utf8"), key).toString("base64");
}
/** Честный подписанный просмотр; каждый вызов — своя транзакция сети. */
function reward({user = UID, transaction = TRANSACTION_ID, keyId = 101, amount = "1"} = {}) {
  const content = `ad_network=1953547073528090325&ad_unit=ca-app-pub-1/unit&reward_amount=${amount}` +
    `&reward_item=coin&timestamp=${T}&transaction_id=${transaction}&user_id=${user}`;
  return `${content}&signature=${encodeURIComponent(sign(content))}&key_id=${keyId}`;
}
const makeDb = (extra = {}) => premiumTestDb({[USER]: {uid: UID, gold: 8, boxCount: 2, ...extra}});
const noKeys = async () => null;
const throwingKeys = async () => { throw new Error("key server down"); };
const log = {warn() {}, error() {}};
const guardPath = (transaction) => `${AD_REWARD_CONFIRMATIONS_COLLECTION}/${adRewardConfirmationId(transaction)}`;
const callbackPath = (rawQuery) => `${AD_REWARD_CALLBACKS_COLLECTION}/${adRewardCallbackId(readAdRewardCallback(rawQuery))}`;
const run = (db, rawQuery, fetchKeys = null, now = T) => applyAdReward({db, rawQuery, fetchKeys: fetchKeys || (async () => KEYS), now, log});

const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("a confirmed view credits one progress step, the guard and the log row in one transaction", async () => {
  const db = makeDb();
  const rawQuery = reward();
  const result = await run(db, rawQuery);
  assert.equal(result.outcome, AD_REWARD_OUTCOME_ACKNOWLEDGED);
  assert.equal(result.verdict, "CONFIRMED");
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 1);
  const guard = db.store.get(guardPath(TRANSACTION_ID));
  assert.equal(guard.transactionId, TRANSACTION_ID);
  assert.equal(guard.userId, UID);
  assert.equal(guard.confirmedAtMs, T);
  const row = db.store.get(callbackPath(rawQuery));
  assert.equal(row.verdict, "CONFIRMED");
  assert.equal(row.rewardItem, "coin");
  assert.equal(row.rewardTimestampMs, T);
});

test("the same delivery again is a replay: no second step, the guard is untouched", async () => {
  const db = makeDb();
  const rawQuery = reward();
  await run(db, rawQuery);
  const replay = await run(db, rawQuery);
  assert.equal(replay.verdict, "REPLAYED");
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 1);
  const guard = db.store.get(guardPath(TRANSACTION_ID));
  assert.equal(guard.confirmedAtMs, T);
  assert.equal(db.store.get(callbackPath(rawQuery)).verdict, "REPLAYED");
});

test("every confirmed view of a new transaction moves the counter: 1, 2, 3", async () => {
  const db = makeDb();
  await run(db, reward({transaction: "tx-00000001"}));
  await run(db, reward({transaction: "tx-00000002"}));
  await run(db, reward({transaction: "tx-00000003"}));
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 3);
  assert.equal(Object.keys(db.store.get(USER)).includes("nextBoxAtMs"), false);
});

test("the same transaction id presented by another account is foreign, not a replay", async () => {
  const db = makeDb();
  await run(db, reward());
  const intruder = reward({user: "other-player"});
  const result = await run(db, intruder, async () => KEYS_BY_ID(101));
  assert.equal(result.verdict, "FOREIGN");
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 1);
  assert.equal(db.store.has("users/other-player"), false);
  assert.equal(db.store.get(guardPath(TRANSACTION_ID)).userId, UID);
});

test("five forged deliveries leave the progress alone and each one on record", async () => {
  const db = makeDb();
  for (let attempt = 0; attempt < 5; attempt++) {
    // Своя транзакция сети на попытку: у каждой подделки свой контент, своя подпись и своя строка.
    const content = reward({transaction: `tx-forged-000${attempt}`}).replace("&signature=", "&signature=AAAA");
    const result = await run(db, content);
    assert.equal(result.verdict, "FORGED");
  }
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], undefined); // ничего не начисляло — поля нет
  assert.equal(db.store.has(guardPath("tx-forged-0000")), false);
  assert.equal([...db.store.values()].filter((value) => value && value.verdict === "FORGED").length, 5);
});

test("an unknown key id after the forced refresh is unverifiable and on record", async () => {
  const db = makeDb();
  const fetches = [];
  const rawQuery = reward({keyId: 777});
  const result = await run(db, rawQuery, async (force) => { fetches.push(force); return force ? KEYS_BY_ID(555) : KEYS_BY_ID(101); });
  assert.equal(result.outcome, AD_REWARD_OUTCOME_ACKNOWLEDGED);
  assert.equal(result.verdict, "UNVERIFIABLE");
  assert.deepEqual(fetches, [false, true]);
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], undefined); // ничего не начисляло — поля нет
  assert.equal(db.store.get(callbackPath(rawQuery)).verdict, "UNVERIFIABLE");
});

test("unreachable key server answers unavailable: nothing recorded, Google may retry", async () => {
  const db = makeDb();
  const result = await run(db, reward(), throwingKeys);
  assert.equal(result.outcome, AD_REWARD_OUTCOME_UNAVAILABLE);
  assert.equal(result.verdict, AD_REWARD_KEY_UNKNOWN);
  assert.equal([...db.store.keys()].filter((path) => path.startsWith(AD_REWARD_CALLBACKS_COLLECTION)).length, 0);
  assert.equal(db.store.has(guardPath(TRANSACTION_ID)), false);
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], undefined); // ничего не начисляло — поля нет
});

test("a key server that answers garbage twice is unavailable, not a crash", async () => {
  const db = makeDb();
  const result = await run(db, reward(), async () => ({not: "a key document"}));
  assert.equal(result.outcome, AD_REWARD_OUTCOME_ACKNOWLEDGED);
  assert.equal(result.verdict, "UNVERIFIABLE");
});

test("unreadable requests are named and recorded without touching progress", async () => {
  const db = makeDb();
  // Подпись есть, транзакции нет: разбор дошёл до решения, а решения без транзакции не бывает.
  const missing = await run(db, `ad_unit=unit&reward_item=coin&signature=${encodeURIComponent("AAAA")}&key_id=101`);
  assert.equal(missing.verdict, "UNUSABLE");
  assert.equal(missing.reason, REASON_MISSING_TRANSACTION_ID);
  const badUser = await run(db, reward({user: "bad/uid"}));
  assert.equal(badUser.verdict, "UNUSABLE");
  assert.equal(badUser.reason, REASON_BAD_USER_ID);
  const unparseable = await run(db, "");
  assert.equal(unparseable.verdict, "UNUSABLE");
  assert.equal(unparseable.reason, REASON_UNPARSEABLE);
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], undefined); // ничего не начисляло — поля нет
  const rows = [...db.store.values()].filter((value) => value && value.verdict === "UNUSABLE");
  assert.equal(rows.length, 3);
  assert.deepEqual(rows.map((row) => row.reason).sort(), [REASON_BAD_USER_ID, REASON_MISSING_TRANSACTION_ID, REASON_UNPARSEABLE].sort());
});

test("a signed confirmation for an account that does not exist credits nobody", async () => {
  const db = makeDb();
  const rawQuery = reward({user: "ghost-player"});
  const result = await run(db, rawQuery);
  assert.equal(result.verdict, "UNUSABLE");
  assert.equal(result.reason, REASON_UNKNOWN_ACCOUNT);
  assert.equal(db.store.has("users/ghost-player"), false);
  assert.equal(db.store.has(guardPath(TRANSACTION_ID)), false);
  assert.equal(db.store.get(callbackPath(rawQuery)).reason, REASON_UNKNOWN_ACCOUNT);
});

test("a corrupt progress field fails loudly instead of guessing", async () => {
  const db = makeDb({[AD_REWARD_VIEWS_FIELD]: "garbage"});
  await assert.rejects(run(db, reward({transaction: "tx-00000009"})), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.has(guardPath("tx-00000009")), false);
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], "garbage");
});

test("a confirmed view never moves gold, boxes or anything but its own counter", async () => {
  const db = makeDb();
  await run(db, reward());
  const user = db.store.get(USER);
  assert.equal(user.gold, 8);
  assert.equal(user.boxCount, 2);
  assert.equal(user.updatedAtMs, T);
});

test("firestore rules close both ad-reward collections to every client", () => {
  // Как purchase-notifications.test.js: правила — исходник, и проверяются исходником же.
  const rules = require("fs").readFileSync(require("path").join(__dirname, "..", "firestore.rules"), "utf8");
  for (const collection of [AD_REWARD_CONFIRMATIONS_COLLECTION, AD_REWARD_CALLBACKS_COLLECTION]) {
    const block = rules.indexOf(`match /${collection}/{document=**}`);
    assert.notEqual(block, -1, collection);
    const body = rules.slice(block, rules.indexOf("\n    }", block));
    assert.match(body, /allow read, write: if false;/, collection);
  }
});

test("four confirmed views earn nothing: the box waits for the fifth", async () => {
  const db = makeDb();
  for (let index = 1; index <= 4; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 4);
  assert.equal(db.store.get(USER).boxCount, 2);
  assert.equal(db.store.get(USER)[AD_BOX_CLOCK_FIELD], undefined);
});

test("the fifth confirmed view issues exactly one box, clock and ledger in the same transaction", async () => {
  const db = makeDb();
  for (let index = 1; index <= 5; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  const user = db.store.get(USER);
  assert.equal(user[AD_REWARD_VIEWS_FIELD], 5);
  assert.equal(user.boxCount, 3);
  assert.equal(user[AD_BOX_CLOCK_FIELD], T + DAY_MS);
  assert.equal(user[BONUS_CLOCK_FIELD], T + DAY_MS,
    "магазинная коробка, взятая за просмотры, закрыта и премиальной кнопке");
  const rows = [...db.store.entries()].filter(([path]) => path.startsWith(`${LEDGER_PATH}/`))
    .map(([, value]) => value).filter((entry) => entry.currency === CURRENCY.BOXES);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].delta, 1);
  assert.equal(rows[0].reason, REASON.GIFT_BOX);
  assert.equal(rows[0].details.source, "AD");
  assert.equal(rows[0].details.views, 5);
});

test("views six to nine earn nothing again", async () => {
  const db = makeDb();
  for (let index = 1; index <= 9; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  assert.equal(db.store.get(USER).boxCount, 3);
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 9);
});

test("the tenth view in the same day hits the ceiling: no box, the clock untouched, views kept", async () => {
  const db = makeDb();
  for (let index = 1; index <= 10; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  const user = db.store.get(USER);
  assert.equal(user.boxCount, 3);
  assert.equal(user[AD_REWARD_VIEWS_FIELD], 10);
  assert.equal(user[AD_BOX_CLOCK_FIELD], T + DAY_MS);
});

test("a next-day confirmation that is not a crossing still issues nothing", async () => {
  const db = makeDb();
  for (let index = 1; index <= 10; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  // Одиннадцатый просмотр на следующие сутки: часы истекли, но пересечения нет — коробки нет.
  await run(db, reward({transaction: "tx-view-00000015"}), null, T + DAY_MS + 1);
  const user = db.store.get(USER);
  assert.equal(user[AD_REWARD_VIEWS_FIELD], 11);
  assert.equal(user.boxCount, 3);
});

test("a crossing on the next day after five more views issues exactly one box", async () => {
  const db = makeDb();
  for (let index = 1; index <= 10; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  for (let index = 11; index <= 15; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}), null, T + DAY_MS + 1);
  }
  const user = db.store.get(USER);
  assert.equal(user[AD_REWARD_VIEWS_FIELD], 15);
  assert.equal(user.boxCount, 4);
  assert.equal(user[AD_BOX_CLOCK_FIELD], T + DAY_MS + 1 + DAY_MS);
});

test("the streak's box for the day does not block the ad box: two sources, two boxes, one ceiling", async () => {
  // Стрик уже выдал свою коробку за сутки (boxCount 3 — из box-service VISIT); рекламная
  // выдаёт свою: у источников раздельные часы, вместе — не больше двух.
  const db = makeDb({boxCount: 3, boxStreakDays: 11, nextBoxAtMs: T + DAY_MS});
  for (let index = 1; index <= 5; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  const user = db.store.get(USER);
  assert.equal(user.boxCount, 4);
  assert.equal(user[AD_BOX_CLOCK_FIELD], T + DAY_MS);
});

test("a corrupt boxCount fails the issuance loudly and writes nothing", async () => {
  const db = makeDb({boxCount: "garbage"});
  for (let index = 1; index <= 4; index++) {
    await run(db, reward({transaction: `tx-view-000000${index}`}));
  }
  await assert.rejects(run(db, reward({transaction: "tx-view-00000005"})), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.get(USER).boxCount, "garbage");
  assert.equal(db.store.get(USER)[AD_REWARD_VIEWS_FIELD], 4);
  assert.equal(db.store.has(guardPath("tx-view-00000005")), false);
});

test("the plan is pure: crossing and free clock decide, everything else declines", () => {
  const {planAdRewardBox} = require("./ad-reward-progress");
  assert.deepEqual(planAdRewardBox({views: 4, nextAdBoxAtMs: 0, nowMs: T}), {issueBox: false, nextAdBoxAtMs: 0});
  assert.deepEqual(planAdRewardBox({views: 5, nextAdBoxAtMs: 0, nowMs: T}), {issueBox: true, nextAdBoxAtMs: T + DAY_MS});
  assert.deepEqual(planAdRewardBox({views: 5, nextAdBoxAtMs: T + 1, nowMs: T}), {issueBox: false, nextAdBoxAtMs: T + 1});
  assert.deepEqual(planAdRewardBox({views: 10, nextAdBoxAtMs: T + 1, nowMs: T + DAY_MS + 1}), {issueBox: true, nextAdBoxAtMs: T + DAY_MS + 1 + DAY_MS});
});

async function main() {
  for (const {name, body} of TESTS) { await body(); console.log(`ok - ${name}`); }
  console.log(`ad-reward-progress: ${TESTS.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
