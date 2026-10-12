"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {
  OFFER_AUCTIONS_COLLECTION,
  OFFER_BIDS_COLLECTION,
  OFFER_AUCTION_OPEN,
  offerAuctionView,
  offerBidId,
  openOfferAuction,
  placeOfferBid,
  planOfferBid,
} = require("./offer-auction");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

const T = Date.UTC(2026, 10, 15, 12); // 15 ноября 2026, полдень UTC — месяц цели «2026-12»
const AUCTION = "2026-12";
const UID = "bid-player";
const USER = `users/${UID}`;
const AUCTION_PATH = `${OFFER_AUCTIONS_COLLECTION}/${AUCTION}`;
const BID_PATH = `${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, UID)}`;
const LEDGER = LEDGER_PATH;
const DEBIT_R0 = `${LEDGER}/${UID}_${CURRENCY.GOLD}_${REASON.OFFER_BID}_${offerBidId(AUCTION, UID)}_r0`;
const REFUND_R0 = `${LEDGER}/${UID}_${CURRENCY.GOLD}_${REASON.OFFER_BID_REFUND}_${offerBidId(AUCTION, UID)}_r0`;
const DEBIT_R1 = `${LEDGER}/${UID}_${CURRENCY.GOLD}_${REASON.OFFER_BID}_${offerBidId(AUCTION, UID)}_r1`;
const DEBIT_R2 = `${LEDGER}/${UID}_${CURRENCY.GOLD}_${REASON.OFFER_BID}_${offerBidId(AUCTION, UID)}_r2`;
const REFUND_R1 = `${LEDGER}/${UID}_${CURRENCY.GOLD}_${REASON.OFFER_BID_REFUND}_${offerBidId(AUCTION, UID)}_r1`;

const makeDb = (extra = {}) => premiumTestDb({[USER]: {uid: UID, gold: 100, ...extra}});
const bid = (db, amount, at = T, overrides = {}) => placeOfferBid({
  db, uid: UID, amount, imageUrl: "https://cdn.example.test/ad.png", link: "https://example.test",
  now: at, ...overrides,
});
const ledgerRows = (db) => [...db.store.keys()].filter((path) => path.startsWith(LEDGER));

const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("the first bid debits gold, writes the bid and the ledger row in one transaction", async () => {
  const db = makeDb();
  const response = await bid(db, 30);
  assert.deepEqual(response, {auctionId: AUCTION, amount: 30, raiseCount: 0, gold: 70});
  assert.equal(db.commits.length, 1, "списание и запись ставки — одна транзакция");
  assert.equal(db.store.get(USER).gold, 70);
  const stored = db.store.get(BID_PATH);
  assert.equal(stored.amount, 30);
  assert.equal(stored.raiseCount, 0);
  assert.equal(stored.uid, UID);
  assert.equal(stored.imageUrl, "https://cdn.example.test/ad.png");
  assert.equal(stored.status, "BID", "статус ставки — ключ возврата settlement'а 4.2; без него возврат не найдёт ставку");
  const auction = db.store.get(AUCTION_PATH);
  assert.equal(auction.status, OFFER_AUCTION_OPEN);
  assert.equal(auction.endsAtMs, Date.UTC(2026, 11, 1));
  assert.equal(auction.auctionId, AUCTION);
  const row = db.store.get(DEBIT_R0);
  assert.equal(row.delta, -30);
  assert.equal(row.reason, REASON.OFFER_BID);
  assert.equal(row.details.previousAmount, 0);
  assert.equal(ledgerRows(db).length, 1);
});

test("a short wallet is a named refusal and nothing is written", async () => {
  const db = makeDb({gold: 10});
  await assert.rejects(bid(db, 30), {code: "FAILED_PRECONDITION"});
  assert.equal(db.commits.length, 0);
  assert.equal(db.store.get(USER).gold, 10);
  assert.equal(db.store.has(BID_PATH), false);
  assert.equal(db.store.has(AUCTION_PATH), false);
  assert.equal(ledgerRows(db).length, 0);
});

test("a missing account reads as an empty wallet and refuses named", async () => {
  const db = premiumTestDb({});
  await assert.rejects(bid(db, 30), {code: "FAILED_PRECONDITION"});
  assert.equal(db.commits.length, 0);
});

test("raising the bid refunds the old amount and debits the new in the same transaction", async () => {
  const db = makeDb();
  await bid(db, 10);
  const commitsBefore = db.commits.length;
  const response = await bid(db, 15);
  assert.deepEqual(response, {auctionId: AUCTION, amount: 15, raiseCount: 1, gold: 85});
  assert.equal(db.commits.length, commitsBefore + 1, "возврат и новое списание — одна транзакция");
  assert.equal(db.store.get(USER).gold, 85);
  assert.equal(db.store.get(BID_PATH).amount, 15);
  assert.equal(db.store.get(BID_PATH).raiseCount, 1);
  assert.equal(db.store.get(BID_PATH).status, "BID", "повышение не меняет статус ставки");
  const refund = db.store.get(REFUND_R0);
  assert.equal(refund.delta, 10);
  assert.equal(refund.reason, REASON.OFFER_BID_REFUND);
  assert.deepEqual(refund.details, {raiseTo: 15, auctionId: AUCTION});
  const debit = db.store.get(DEBIT_R1);
  assert.equal(debit.delta, -15);
  assert.deepEqual(debit.details, {previousAmount: 10, auctionId: AUCTION});
  assert.equal(db.store.has(DEBIT_R0), true, "первое списание остаётся в книге");
  assert.equal(db.store.has(REFUND_R1), false, "возврат ещё не был");
});

test("every raise names its own movement: r0, r1, r2 stay distinct", async () => {
  const db = makeDb({gold: 1000});
  await bid(db, 10);
  await bid(db, 20);
  await bid(db, 25);
  assert.deepEqual(ledgerRows(db).sort(), [DEBIT_R0, DEBIT_R1, DEBIT_R2, REFUND_R0, REFUND_R1].sort());
  assert.equal(db.store.get(USER).gold, 975, "в кошельке остаётся разница к последней ставке: 1000 − 25");
  assert.equal(db.store.get(BID_PATH).raiseCount, 2);
});

test("a raise without gold for the delta refuses named and touches nothing", async () => {
  const db = makeDb({gold: 12});
  await bid(db, 10);
  const before = db.store.get(BID_PATH);
  await assert.rejects(bid(db, 30), {code: "FAILED_PRECONDITION"});
  assert.equal(db.store.get(USER).gold, 2);
  assert.equal(db.store.get(BID_PATH).amount, before.amount);
  assert.equal(db.commits.length, 1);
  assert.deepEqual(ledgerRows(db), [DEBIT_R0]);
});

test("the same amount again answers the state and writes nothing (NFR2)", async () => {
  const db = makeDb();
  await bid(db, 10);
  const snapshot = JSON.stringify([...db.store.entries()]);
  const response = await bid(db, 10);
  assert.deepEqual(response, {auctionId: AUCTION, amount: 10, raiseCount: 0, gold: 90});
  assert.equal(JSON.stringify([...db.store.entries()]), snapshot, "ни одной записи: повтор — тот же жест");
  assert.deepEqual(ledgerRows(db), [DEBIT_R0]);
});

test("lowering a bid is refused named", async () => {
  const db = makeDb();
  await bid(db, 10);
  await assert.rejects(bid(db, 5), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.get(BID_PATH).amount, 10);
  assert.equal(db.store.get(USER).gold, 90);
});

test("corrupt amounts refuse named before anything is read for money", async () => {
  for (const amount of [0, -5, 1.5, "10", NaN, null]) {
    const db = makeDb();
    await assert.rejects(bid(db, amount), {code: "INVALID_ARGUMENT"});
    assert.equal(db.commits.length, 0);
  }
});

test("a bid without its creative refuses named", async () => {
  const db = makeDb();
  await assert.rejects(bid(db, 10, T, {imageUrl: "   "}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(bid(db, 10, T, {link: ""}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(bid(db, 10, T, {imageUrl: 42}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(bid(db, 10, T, {link: "x".repeat(2049)}), {code: "INVALID_ARGUMENT"});
  assert.equal(db.commits.length, 0);
});

test("a bid into a non-open auction document refuses named and writes nothing", async () => {
  const db = makeDb();
  db.store.set(AUCTION_PATH, {auctionId: AUCTION, status: "SETTLED", opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: Date.UTC(2026, 11, 1)});
  await assert.rejects(bid(db, 10), {code: "FAILED_PRECONDITION"});
  assert.equal(db.commits.length, 0);
  assert.equal(db.store.has(BID_PATH), false);
});

test("a corrupt auction status refuses loudly as INVALID_ARGUMENT", async () => {
  const db = makeDb();
  db.store.set(AUCTION_PATH, {auctionId: AUCTION, status: 7, opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: Date.UTC(2026, 11, 1)});
  await assert.rejects(bid(db, 10), {code: "INVALID_ARGUMENT"});
  assert.equal(db.commits.length, 0);
});

test("a corrupt stored deadline refuses loudly as INVALID_ARGUMENT", async () => {
  const db = makeDb();
  db.store.set(AUCTION_PATH, {auctionId: AUCTION, status: OFFER_AUCTION_OPEN, opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: "soon"});
  await assert.rejects(bid(db, 10), {code: "INVALID_ARGUMENT"});
  assert.equal(db.commits.length, 0);
});

test("the December bid targets next January: the year rolls over", async () => {
  const db = makeDb();
  const response = await bid(db, 10, Date.UTC(2026, 11, 15));
  assert.equal(response.auctionId, "2027-01");
  assert.equal(db.store.get(`${OFFER_AUCTIONS_COLLECTION}/2027-01`).endsAtMs, Date.UTC(2027, 0, 1));
});

test("the pure plan decides on its own: amounts, raises, refusals", () => {
  const auction = {status: OFFER_AUCTION_OPEN, opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: Date.UTC(2026, 11, 1)};
  assert.deepEqual(
    planOfferBid({auction, bid: null, gold: 100, amount: 30, imageUrl: "i", link: "l", nowMs: T}),
    {kind: "BID", raiseCount: 0, previous: null, bidAmount: 30, goldAfter: 70},
  );
  assert.deepEqual(
    planOfferBid({auction, bid: {amount: 10, raiseCount: 0, createdAtMs: T}, gold: 100, amount: 15, imageUrl: "i", link: "l", nowMs: T}),
    {kind: "BID", raiseCount: 1, previous: 10, bidAmount: 15, goldAfter: 95},
  );
  assert.deepEqual(
    planOfferBid({auction, bid: {amount: 10, raiseCount: 3, createdAtMs: T}, gold: 100, amount: 10, imageUrl: "i", link: "l", nowMs: T}),
    {kind: "UNCHANGED", raiseCount: 3},
  );
  assert.throws(() => planOfferBid({auction, bid: null, gold: 100, amount: 30, imageUrl: " ", link: "l", nowMs: T}), {code: "INVALID_ARGUMENT"});
  assert.throws(() => planOfferBid({auction: null, bid: null, gold: 0, amount: 30, imageUrl: "i", link: "l", nowMs: T}), {code: "FAILED_PRECONDITION"});
});

test("the open table shows who, how much and when — sorted, without creatives", async () => {
  const db = premiumTestDb({
    "users/player-a": {uid: "player-a", gold: 100},
    "users/player-b": {uid: "player-b", gold: 100},
    "users/player-c": {uid: "player-c", gold: 100},
  });
  await placeOfferBid({db, uid: "player-a", amount: 10, imageUrl: "i-a", link: "l-a", now: T - 2000});
  await placeOfferBid({db, uid: "player-b", amount: 50, imageUrl: "i-b", link: "l-b", now: T - 1000});
  await placeOfferBid({db, uid: "player-c", amount: 50, imageUrl: "i-c", link: "l-c", now: T});
  const view = await offerAuctionView({db, now: T});
  assert.equal(view.open, true);
  assert.equal(view.auctionId, AUCTION);
  assert.deepEqual(view.bids, [
    {uid: "player-b", amount: 50, atMs: T - 1000},
    {uid: "player-c", amount: 50, atMs: T},
    {uid: "player-a", amount: 10, atMs: T - 2000},
  ]);
  for (const row of view.bids) {
    assert.equal(Object.prototype.hasOwnProperty.call(row, "imageUrl"), false);
    assert.equal(Object.prototype.hasOwnProperty.call(row, "link"), false);
  }
});

test("the table answers open:false and writes nothing while no auction exists yet", async () => {
  const db = makeDb();
  const view = await offerAuctionView({db, now: T});
  assert.deepEqual(view, {open: false, auctionId: AUCTION, endsAtMs: Date.UTC(2026, 11, 1), bids: []});
  assert.equal(db.commits.length, 0);
});

test("the table limit keeps the top of the table, not an arbitrary tail", async () => {
  const db = premiumTestDb(Object.fromEntries(
    ["top", "mid", "low"].map((name) => [`users/${name}`, {uid: name, gold: 1000}])));
  await placeOfferBid({db, uid: "low", amount: 10, imageUrl: "i", link: "l", now: T});
  await placeOfferBid({db, uid: "top", amount: 900, imageUrl: "i", link: "l", now: T});
  await placeOfferBid({db, uid: "mid", amount: 500, imageUrl: "i", link: "l", now: T});
  const view = await offerAuctionView({db, now: T, limit: 2});
  assert.deepEqual(view.bids, [
    {uid: "top", amount: 900, atMs: T},
    {uid: "mid", amount: 500, atMs: T},
  ], "обрезка хвоста не должна выбивать топ стола");
});

test("the first-bid race replays onto the auction another bettor created", async () => {
  const db = makeDb();
  const originalRun = db.runTransaction.bind(db);
  let raced = false;
  db.runTransaction = async (body) => {
    if (raced) return originalRun(body);
    raced = true;
    return originalRun(async (transaction) => {
      const originalGetAll = transaction.getAll.bind(transaction);
      transaction.getAll = async (...refs) => {
        const snapshots = await originalGetAll(...refs);
        // Аукцион создаёт конкурент в момент между чтением и записью: create обязан упасть.
        db.store.set(AUCTION_PATH, {
          auctionId: AUCTION, status: OFFER_AUCTION_OPEN,
          opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: Date.UTC(2026, 11, 1), createdAtMs: T, updatedAtMs: T,
        });
        return snapshots;
      };
      return body(transaction);
    });
  };
  const response = await bid(db, 30);
  assert.deepEqual(response, {auctionId: AUCTION, amount: 30, raiseCount: 0, gold: 70});
  assert.equal(db.store.get(USER).gold, 70);
  assert.equal(db.store.get(BID_PATH).amount, 30);
  assert.equal(ledgerRows(db).length, 1, "переигранная транзакция не пишет вторую строку");
});

test("openOfferAuction names the month window in UTC", () => {
  assert.deepEqual(openOfferAuction(Date.UTC(2026, 0, 5)), {
    auctionId: "2026-02", opensAtMs: Date.UTC(2026, 0, 1), endsAtMs: Date.UTC(2026, 1, 1),
  });
  assert.deepEqual(openOfferAuction(Date.UTC(2026, 11, 31, 23)), {
    auctionId: "2027-01", opensAtMs: Date.UTC(2026, 11, 1), endsAtMs: Date.UTC(2027, 0, 1),
  });
});

async function main() {
  for (const {name, body} of TESTS) { await body(); console.log(`ok - ${name}`); }
  console.log(`offer-auction: ${TESTS.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
