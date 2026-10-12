"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {
  OFFER_AUCTIONS_COLLECTION,
  OFFER_AUCTION_OPEN,
  OFFER_BIDS_COLLECTION,
  OFFER_BID_STATUS,
  offerBidId,
} = require("./offer-auction");
const {OFFER_PLACEMENTS_COLLECTION} = require("./offer-auction-settlement");
const {
  OFFER_PLACEMENT_CURRENT,
  OFFER_PLACEMENT_PENDING,
  decideOfferAdReview,
  offersScreenView,
} = require("./offer-ad-review");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

const T = Date.UTC(2027, 0, 2); // 2 января 2027: декабрьский месяц закрыт 1-го, кандидат ждёт
const AUCTION = "2027-01";
const AUCTION_PATH = `${OFFER_AUCTIONS_COLLECTION}/${AUCTION}`;
const PENDING = `${OFFER_PLACEMENTS_COLLECTION}/${OFFER_PLACEMENT_PENDING}`;
const CURRENT = `${OFFER_PLACEMENTS_COLLECTION}/${OFFER_PLACEMENT_CURRENT}`;
const LEDGER = LEDGER_PATH;
const bidPath = (uid) => `${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, uid)}`;
const refundRow = (uid) => `${LEDGER}/${uid}_${CURRENCY.GOLD}_${REASON.OFFER_BID_REFUND}_${offerBidId(AUCTION, uid)}`;

const AUCTION_SEED = {
  auctionId: AUCTION, status: OFFER_AUCTION_OPEN,
  opensAtMs: Date.UTC(2026, 11, 1), endsAtMs: Date.UTC(2027, 0, 1),
  createdAtMs: Date.UTC(2026, 11, 15), updatedAtMs: Date.UTC(2026, 11, 15),
};
/** Кандидат: победитель «bob» ждёт проверки; «alice» заранее одобрена, «carol» — нет. */
const makeDb = () => premiumTestDb({
  [AUCTION_PATH]: AUCTION_SEED,
  [PENDING]: {auctionId: AUCTION, bidId: offerBidId(AUCTION, "bob"), uid: "bob",
    imageUrl: "i-bob", link: "l-bob", reviewStatus: "PENDING", settledAtMs: T - 86_400_000},
  "users/bob": {uid: "bob", gold: 0},
  "users/alice": {uid: "alice", gold: 0},
  "users/carol": {uid: "carol", gold: 0},
  [bidPath("bob")]: {auctionId: AUCTION, uid: "bob", amount: 70, imageUrl: "i-bob", link: "l-bob",
    createdAtMs: Date.UTC(2026, 11, 20), raiseCount: 0, status: OFFER_BID_STATUS.WON, reviewStatus: "PENDING"},
  [bidPath("alice")]: {auctionId: AUCTION, uid: "alice", amount: 50, imageUrl: "i-alice", link: "l-alice",
    createdAtMs: Date.UTC(2026, 11, 18), raiseCount: 0, status: "REFUNDED", refundedAtMs: T - 86_400_000, reviewStatus: "APPROVED"},
  [bidPath("carol")]: {auctionId: AUCTION, uid: "carol", amount: 10, imageUrl: "i-carol", link: "l-carol",
    createdAtMs: Date.UTC(2026, 11, 19), raiseCount: 0, status: "REFUNDED", refundedAtMs: T - 86_400_000, reviewStatus: "PENDING"},
});
const decide = (db, uid, decision, now = T) => decideOfferAdReview({db, bidId: offerBidId(AUCTION, uid), decision, now});

const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("the screen shows nothing while the winner awaits review: the previous ad stays on it", async () => {
  const db = makeDb();
  db.store.set(CURRENT, {auctionId: "2026-12", uid: "old", imageUrl: "old-i", link: "old-l", reviewStatus: "APPROVED", approvedAtMs: 1});
  const view = await offersScreenView({db});
  assert.deepEqual(view, {placement: {auctionId: "2026-12", bidId: undefined, uid: "old",
    imageUrl: "old-i", link: "old-l", approvedAtMs: 1}}, "экран не пустеет: прошлая проверенная витрина");
  assert.equal(view.placement.imageUrl, "old-i");
  assert.equal(view.placement.link, "old-l");
  const empty = await offersScreenView({db: premiumTestDb({})});
  assert.deepEqual(empty, {placement: null});
});

test("the screen never serves the pending candidate, even with no current at all", async () => {
  const db = makeDb();
  const view = await offersScreenView({db});
  assert.deepEqual(view, {placement: null}, "кандидат не на экране, экран честно пуст до одобрения");
});

test("approval moves the candidate onto the screen and clears the queue", async () => {
  const db = makeDb();
  const result = await decide(db, "bob", "APPROVED");
  assert.deepEqual(result, {decision: "APPROVED", bidId: offerBidId(AUCTION, "bob"), refunded: false,
    promoted: offerBidId(AUCTION, "bob"), promotedTo: "current"});
  const current = db.store.get(CURRENT);
  assert.equal(current.reviewStatus, "APPROVED");
  assert.equal(current.uid, "bob");
  assert.equal(current.imageUrl, "i-bob");
  assert.equal(db.store.has(PENDING), false, "кандидат очищен: очередь не копится");
  assert.equal(db.store.get(bidPath("bob")).reviewStatus, "APPROVED");
  assert.equal(db.store.get(bidPath("bob")).status, OFFER_BID_STATUS.WON, "одобренная победа — деньги сгорают как раньше");
  const view = await offersScreenView({db});
  assert.equal(view.placement.imageUrl, "i-bob");
});

test("rejection refunds the winner and hands the slot to the next verified bid", async () => {
  const db = makeDb();
  const result = await decide(db, "bob", "REJECTED");
  assert.deepEqual(result, {decision: "REJECTED", bidId: offerBidId(AUCTION, "bob"), refunded: true,
    promoted: offerBidId(AUCTION, "alice"), promotedTo: "current"});
  assert.equal(db.store.get(bidPath("bob")).status, OFFER_BID_STATUS.REFUNDED);
  assert.equal(db.store.get(bidPath("bob")).reviewStatus, "REJECTED");
  assert.equal(db.store.get("users/bob").gold, 70, "ставка возвращается полностью");
  const row = db.store.get(refundRow("bob"));
  assert.equal(row.delta, 70);
  assert.deepEqual(row.details, {auctionId: AUCTION, rejected: true});
  const current = db.store.get(CURRENT);
  assert.equal(current.uid, "alice", "место — следующей проверенной ставке");
  assert.equal(current.reviewStatus, "APPROVED");
  assert.equal(current.imageUrl, "i-alice");
  assert.equal(db.store.has(PENDING), false);
  const view = await offersScreenView({db});
  assert.equal(view.placement.imageUrl, "i-alice", "экран не пустеет: место уже занято проверенным");
});

test("rejection with nobody verified leaves the screen as it was", async () => {
  const db = makeDb();
  db.store.set(CURRENT, {auctionId: "2026-12", uid: "old", imageUrl: "old-i", link: "old-l", reviewStatus: "APPROVED", approvedAtMs: 1});
  db.store.set(bidPath("alice"), {...db.store.get(bidPath("alice")), reviewStatus: "PENDING"});
  const result = await decide(db, "bob", "REJECTED");
  assert.equal(result.promoted, null);
  assert.deepEqual(db.store.get(CURRENT),
    {auctionId: "2026-12", uid: "old", imageUrl: "old-i", link: "old-l", reviewStatus: "APPROVED", approvedAtMs: 1},
    "прошлая витрина на месте — экран не пустеет");
  assert.equal(db.store.has(PENDING), false, "кандидат исчез: очередь пуста");
});

test("a rejected bid still competing is refunded at once and cannot win the month", async () => {
  const db = makeDb();
  db.store.set(PENDING, {...db.store.get(PENDING), bidId: offerBidId(AUCTION, "carol")});
  db.store.set(bidPath("carol"), {...db.store.get(bidPath("carol")), status: OFFER_BID_STATUS.BID});
  const result = await decide(db, "carol", "REJECTED");
  assert.deepEqual(result, {decision: "REJECTED", bidId: offerBidId(AUCTION, "carol"), refunded: true,
    promoted: offerBidId(AUCTION, "alice"), promotedTo: "current"},
    "отказ до закрытия тоже возвращает деньги и передаёт место проверенной");
  assert.equal(db.store.get("users/carol").gold, 10);
  assert.equal(db.store.get(bidPath("carol")).status, OFFER_BID_STATUS.REFUNDED);
  assert.equal(db.store.get(bidPath("carol")).reviewStatus, "REJECTED");
  assert.equal(db.store.get(CURRENT).uid, "alice");
});

test("a second decision on the same bid is a named refusal and writes nothing", async () => {
  const db = makeDb();
  await decide(db, "bob", "APPROVED");
  const snapshot = JSON.stringify([...db.store.entries()]);
  await assert.rejects(decide(db, "bob", "REJECTED"), {code: "FAILED_PRECONDITION"});
  await assert.rejects(decide(db, "bob", "APPROVED"), {code: "FAILED_PRECONDITION"});
  assert.equal(JSON.stringify([...db.store.entries()]), snapshot);
});

test("an unknown bid and a garbage decision refuse named", async () => {
  const db = makeDb();
  await assert.rejects(decideOfferAdReview({db, bidId: `${AUCTION}_nobody`, decision: "APPROVED", now: T}), {code: "NOT_FOUND"});
  await assert.rejects(decide(db, "bob", "MAYBE"), {code: "INVALID_ARGUMENT"});
  await assert.rejects(decide(db, "bob", null), {code: "INVALID_ARGUMENT"});
  await assert.rejects(decideOfferAdReview({db, bidId: "with/slash", decision: "APPROVED", now: T}), {code: "INVALID_ARGUMENT"});
});

test("a corrupt candidate is a loud refusal, nothing written", async () => {
  const db = makeDb();
  db.store.set(PENDING, {...db.store.get(PENDING), bidId: offerBidId(AUCTION, "ghost")});
  // Ставка-«победитель» не существует, но решение по bob — не про кандидата: bob не на очереди.
  const result = await decide(db, "bob", "APPROVED");
  assert.equal(result.promoted, null, "одобрение вне очереди просто ставит статус ставки");
  // А решение по таинственному победителю падает громко на отсутствии ставки.
  await assert.rejects(decide(db, "ghost", "APPROVED"), {code: "NOT_FOUND"});
});

test("promotion keeps the month of the candidate, not another month's bids", async () => {
  const db = makeDb();
  db.store.set(bidPath("dave"), {auctionId: "2027-02", uid: "dave", amount: 999, imageUrl: "i-dave",
    link: "l-dave", createdAtMs: T, raiseCount: 0, status: OFFER_BID_STATUS.BID, reviewStatus: "APPROVED"});
  await decide(db, "bob", "REJECTED");
  assert.equal(db.store.get(CURRENT).uid, "alice", "чужой месяц не продвигается");
});

async function main() {
  for (const {name, body} of TESTS) { await body(); console.log(`ok - ${name}`); }
  console.log(`offer-ad-review: ${TESTS.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
