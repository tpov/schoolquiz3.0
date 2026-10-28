"use strict";

const assert = require("assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {
  OFFER_AUCTIONS_COLLECTION,
  OFFER_AUCTION_OPEN,
  OFFER_AUCTION_SETTLING,
  OFFER_AUCTION_SETTLED,
  OFFER_BIDS_COLLECTION,
  OFFER_BID_REVIEW,
  OFFER_BID_STATUS,
  offerBidId,
  placeOfferBid,
} = require("./offer-auction");
const {
  OFFER_PLACEMENTS_COLLECTION,
  OFFER_PLACEMENT_PENDING,
  closeOfferAuction,
  followingMonthSlot,
  pickWinner,
  settleDueOfferAuctions,
} = require("./offer-auction-settlement");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

const T = Date.UTC(2026, 11, 1, 0, 5); // 1 декабря 2026, пять минут после полуночи: ноябрьский аукцион созрел
const AUCTION = "2026-12";             // месяц цели ноябрьского аукциона — он и закрывается
const AUCTION_PATH = `${OFFER_AUCTIONS_COLLECTION}/${AUCTION}`;
const NEXT = "2027-01";
const NEXT_PATH = `${OFFER_AUCTIONS_COLLECTION}/${NEXT}`;
const PENDING = `${OFFER_PLACEMENTS_COLLECTION}/${OFFER_PLACEMENT_PENDING}`;
const CURRENT = `${OFFER_PLACEMENTS_COLLECTION}/current`;
const LEDGER = LEDGER_PATH;

const AUCTION_SEED = {
  auctionId: AUCTION, status: OFFER_AUCTION_OPEN,
  opensAtMs: Date.UTC(2026, 10, 1), endsAtMs: Date.UTC(2026, 11, 1),
  createdAtMs: Date.UTC(2026, 10, 15), updatedAtMs: Date.UTC(2026, 10, 15),
};
const makeDb = (bids = {}, extra = {}) => premiumTestDb({
  [AUCTION_PATH]: {...AUCTION_SEED, ...extra.auction},
  ...Object.fromEntries(Object.keys(bids).map((uid) => [`users/${uid}`, {uid, gold: extra.gold === undefined ? 0 : extra.gold}])),
  ...Object.fromEntries(Object.entries(bids).map(([uid, amount]) => [
    `${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, uid)}`,
    {auctionId: AUCTION, uid, amount, imageUrl: `i-${uid}`, link: `l-${uid}`,
      createdAtMs: Date.UTC(2026, 10, 20), updatedAtMs: Date.UTC(2026, 10, 20), raiseCount: 0, status: OFFER_BID_STATUS.BID},
  ])),
  ...extra.docs,
});
const refundRow = (uid) => `${LEDGER}/${uid}_${CURRENCY.GOLD}_${REASON.OFFER_BID_REFUND}_${offerBidId(AUCTION, uid)}`;
const run = (db, now = T) => settleDueOfferAuctions({db, now});

const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("the month turn crowns the top bid, burns its gold and opens the next month at once", async () => {
  const db = makeDb({alice: 50, bob: 70, carol: 10});
  const result = await run(db);
  assert.deepEqual(result, {closed: 1, refunded: 2, skipped: 0});
  const auction = db.store.get(AUCTION_PATH);
  assert.equal(auction.status, OFFER_AUCTION_SETTLED);
  assert.equal(auction.winnerBidId, offerBidId(AUCTION, "bob"));
  const winnerBid = db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "bob")}`);
  assert.equal(winnerBid.status, OFFER_BID_STATUS.WON, "золото победителя сгорает: возврата не будет");
  assert.equal(db.store.get(`users/bob`).gold, 0);
  const candidate = db.store.get(PENDING);
  assert.equal(candidate.reviewStatus, "PENDING", "объявление победителя ждёт проверки (4.3)");
  assert.deepEqual(
    {auctionId: candidate.auctionId, bidId: candidate.bidId, uid: candidate.uid,
      imageUrl: candidate.imageUrl, link: candidate.link, settledAtMs: candidate.settledAtMs},
    {auctionId: AUCTION, bidId: offerBidId(AUCTION, "bob"), uid: "bob",
      imageUrl: "i-bob", link: "l-bob", settledAtMs: T});
  assert.equal(db.store.has(CURRENT), false, "показ не тронут: непроверенное не на экране");
  assert.equal(db.store.get(NEXT_PATH).status, OFFER_AUCTION_OPEN, "следующий месяц открыт в тот же момент");
  assert.equal(db.store.get(NEXT_PATH).endsAtMs, Date.UTC(2027, 1, 1));
  for (const uid of ["alice", "carol"]) {
    assert.equal(db.store.get(`users/${uid}`).gold, {alice: 50, carol: 10}[uid], "проигравшему — возврат полной суммы");
    assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, uid)}`).status, OFFER_BID_STATUS.REFUNDED);
    const row = db.store.get(refundRow(uid));
    assert.equal(row.delta, {alice: 50, carol: 10}[uid]);
    assert.deepEqual(row.details, {auctionId: AUCTION});
  }
});

test("a tie on the top amount goes to the earlier bid", async () => {
  const db = makeDb({alice: 50, bob: 50});
  db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "bob")}`).createdAtMs = Date.UTC(2026, 10, 19);
  await run(db);
  assert.equal(db.store.get(AUCTION_PATH).winnerBidId, offerBidId(AUCTION, "bob"));
  assert.equal(db.store.get(PENDING).uid, "bob");
  assert.equal(db.store.get(`users/bob`).gold, 0);
  assert.equal(db.store.get(`users/alice`).gold, 50);
});

test("a rejected or refunded bid never wins the month", async () => {
  const bids = {alice: 90, bob: 70, carol: 10};
  const db = makeDb(bids);
  const bobBid = db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "bob")}`);
  db.store.set(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "bob")}`, {...bobBid, reviewStatus: "REJECTED"});
  const aliceBid = db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`);
  db.store.set(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`, {...aliceBid, status: "REFUNDED", refundedAtMs: 1});
  await run(db);
  assert.equal(db.store.get(AUCTION_PATH).winnerBidId, offerBidId(AUCTION, "carol"),
    "отклонённая и возвращённая верхушка пропущена");
  assert.equal(db.store.get(PENDING).uid, "carol");
});

test("an equal-top bid in another month never enters this ranking", async () => {
  const db = makeDb({alice: 50}, {docs: {
    [`${OFFER_BIDS_COLLECTION}/${offerBidId(NEXT, "zoe")}`]: {auctionId: NEXT, uid: "zoe", amount: 900,
      createdAtMs: Date.UTC(2026, 10, 21), raiseCount: 0, status: OFFER_BID_STATUS.BID},
  }});
  db.store.set(`users/zoe`, {uid: "zoe", gold: 5});
  await run(db);
  assert.equal(db.store.get(PENDING).uid, "alice", "чужие ставки в рейтинг закрытого месяца не попадают");
  assert.equal(db.store.get(`users/zoe`).gold, 5);
  assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(NEXT, "zoe")}`).status, OFFER_BID_STATUS.BID);
});

test("refunds go in batches, each bid its own key: more than a transaction's worth still returns in full", async () => {
  const bids = {};
  for (let index = 0; index < 25; index++) bids[`player-${String(index).padStart(2, "0")}`] = 10 + index;
  const db = makeDb(bids);
  const transactionsBefore = db.commits.length;
  const result = await settleDueOfferAuctions({db, now: T, batchSize: 10});
  assert.equal(result.refunded, 24, "25 ставок минус победитель");
  assert.equal(db.commits.length - transactionsBefore, 6,
    "закрытие + порции 10+10+4 + пустой проход + SETTLED — каждая своей транзакцией");
  for (const uid of Object.keys(bids)) {
    const expected = uid === "player-24" ? 0 : bids[uid]; // верхняя ставка 34 — победитель, сгорает
    assert.equal(db.store.get(`users/${uid}`).gold, expected);
    assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, uid)}`).status,
      uid === "player-24" ? OFFER_BID_STATUS.WON : OFFER_BID_STATUS.REFUNDED);
  }
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_SETTLED);
});

test("a rerun is a no-op: no second refunds, no rewritten month", async () => {
  const db = makeDb({alice: 50, bob: 70});
  await run(db);
  const snapshot = JSON.stringify([...db.store.entries()]);
  const second = await run(db);
  assert.deepEqual(second, {closed: 0, refunded: 0, skipped: 1},
    "единственный открытый аукцион — свежий следующий месяц, он ещё не созрел");
  assert.equal(JSON.stringify([...db.store.entries()]), snapshot, "повторный прогон ничего не меняет");
});

test("a crash between batches resumes with the remaining BID bids only", async () => {
  const db = makeDb({alice: 50, bob: 70});
  await run(db);
  // Одна ставка «не успела»: прогон упал между порциями — статус ставки остался BID.
  db.store.set(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`, {
    ...db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`), status: OFFER_BID_STATUS.BID, refundedAtMs: undefined,
  });
  db.store.set(AUCTION_PATH, {...db.store.get(AUCTION_PATH), status: OFFER_AUCTION_SETTLING});
  const usersGold = db.store.get("users/alice").gold;
  const result = await run(db);
  assert.equal(result.refunded, 1);
  assert.equal(db.store.get("users/alice").gold, usersGold + 50, "довозврат, а не второй возврат");
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_SETTLED);
});

test("a month without bids settles quietly: the placement stays, the next month still opens", async () => {
  const db = makeDb({}, {docs: {[CURRENT]: {auctionId: "2026-11", uid: "old-winner", imageUrl: "old", link: "old", reviewStatus: "APPROVED", settledAtMs: 1}}});
  const result = await run(db);
  assert.deepEqual(result, {closed: 1, refunded: 0, skipped: 0});
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_SETTLED);
  assert.deepEqual(db.store.get(CURRENT), {auctionId: "2026-11", uid: "old-winner", imageUrl: "old", link: "old", reviewStatus: "APPROVED", settledAtMs: 1},
    "место не пустует: прошлая проверенная витрина на месте");
  assert.equal(db.store.has(PENDING), false);
  assert.equal(db.store.get(NEXT_PATH).status, OFFER_AUCTION_OPEN);
});

test("an unreviewed candidate does not survive the next month: its bid returns, not burns", async () => {
  const db = makeDb({alice: 50}, {docs: {[PENDING]: {
    auctionId: "2026-11", bidId: `${"2026-11"}_old-winner`, uid: "old-winner",
    imageUrl: "stale-i", link: "stale-l", reviewStatus: "PENDING", settledAtMs: 1,
  }}});
  db.store.set("users/old-winner", {uid: "old-winner", gold: 0});
  db.store.set(`${OFFER_BIDS_COLLECTION}/2026-11_old-winner`, {
    auctionId: "2026-11", uid: "old-winner", amount: 60, createdAtMs: 1, raiseCount: 0,
    status: OFFER_BID_STATUS.WON, reviewStatus: "PENDING",
  });
  const result = await run(db);
  assert.equal(result.closed, 1);
  assert.equal(db.store.get("users/old-winner").gold, 60,
    "непоказанное объявление не держит золото: кандидата сменили — ставка вернулась");
  assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/2026-11_old-winner`).status, OFFER_BID_STATUS.REFUNDED);
  // Ретро m4 (п.1): возврат кандидата уходит и с проверки — иначе ставка оставалась PENDING
  // и призраком доезжала до операторской очереди (offer-ad-review решает ровно PENDING).
  assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/2026-11_old-winner`).reviewStatus, OFFER_BID_REVIEW.REJECTED);
  const row = db.store.get(`${LEDGER}/old-winner_${CURRENCY.GOLD}_${REASON.OFFER_BID_REFUND}_2026-11_old-winner`);
  assert.equal(row.delta, 60);
  assert.deepEqual(row.details, {auctionId: "2026-11", stale: true});
  assert.equal(db.store.get(PENDING).uid, "alice", "новый кандидат на месте старого");
});

test("a corrupt stale candidate refuses the closure loudly and writes nothing", async () => {
  const db = makeDb({alice: 50}, {docs: {[PENDING]: {
    auctionId: "2026-12", bidId: "ghost-bid", reviewStatus: "PENDING", settledAtMs: 1,
  }}});
  await assert.rejects(run(db), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_OPEN);
  assert.equal(db.store.get("users/alice").gold, 0);
});

test("a month still running is not touched", async () => {
  const db = makeDb({alice: 50});
  await run(db, Date.UTC(2026, 10, 15));
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_OPEN);
  assert.equal(db.store.has(NEXT_PATH), false);
  assert.equal(db.store.has(PENDING), false);
});

test("the next month's document created by bids is not overwritten", async () => {
  const db = makeDb({alice: 50}, {docs: {[NEXT_PATH]: {
    auctionId: NEXT, status: OFFER_AUCTION_OPEN, opensAtMs: Date.UTC(2026, 11, 1),
    endsAtMs: Date.UTC(2027, 1, 1), createdAtMs: Date.UTC(2026, 11, 1), updatedAtMs: Date.UTC(2026, 11, 1),
  }}});
  await run(db);
  assert.equal(db.store.get(NEXT_PATH).createdAtMs, Date.UTC(2026, 11, 1), "создан ставками — не переписан");
});

test("a corrupt auction refuses loudly and writes nothing", async () => {
  const db = makeDb({alice: 50}, {auction: {endsAtMs: "yesterday"}});
  await assert.rejects(run(db), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_OPEN);
  assert.equal(db.store.get("users/alice").gold, 0);
});

test("a corrupt bid in the ranking refuses the whole closure loudly", async () => {
  const db = makeDb({alice: 50});
  db.store.set(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`, {
    ...db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(AUCTION, "alice")}`), amount: "fifty",
  });
  await assert.rejects(run(db), {code: "INVALID_ARGUMENT"});
  assert.equal(db.store.get(AUCTION_PATH).status, OFFER_AUCTION_OPEN);
});

test("a month key that is not a month key refuses loudly", () => {
  assert.throws(() => followingMonthSlot("2026-13", T), {code: "INVALID_ARGUMENT"});
  assert.throws(() => followingMonthSlot("december", T), {code: "INVALID_ARGUMENT"});
});

test("the pure helpers decide on their own", () => {
  assert.deepEqual(followingMonthSlot("2026-12", Date.UTC(2026, 11, 1)),
    {auctionId: "2027-01", opensAtMs: Date.UTC(2026, 11, 1), endsAtMs: Date.UTC(2027, 1, 1)});
  assert.deepEqual(followingMonthSlot("2026-01", Date.UTC(2026, 0, 1)),
    {auctionId: "2026-02", opensAtMs: Date.UTC(2026, 0, 1), endsAtMs: Date.UTC(2026, 2, 1)},
    "конец февральского аукциона — начало марта");
  const docs = [
    {id: "b", data: () => ({uid: "b", amount: 70, createdAtMs: 20})},
    {id: "a", data: () => ({uid: "a", amount: 70, createdAtMs: 10})},
    {id: "c", data: () => ({uid: "c", amount: 5, createdAtMs: 1})},
  ];
  assert.equal(pickWinner(docs).id, "a");
  assert.equal(pickWinner([]), null);
  assert.equal(pickWinner([{id: "x", data: () => ({uid: "", amount: 99, createdAtMs: 1})}]), null);
});

test("a bid placed after the turn lands in the next month and is not refunded by this closure", async () => {
  const db = makeDb({alice: 50});
  await run(db);
  // Декабрьская ставка 4.1: аукцион 2027-01 уже открыт — она живёт там и November её не трогает.
  db.store.set("users/zoe", {uid: "zoe", gold: 100});
  await placeOfferBid({db, uid: "zoe", amount: 30, imageUrl: "i", link: "l", now: T + 60_000});
  assert.equal(db.store.get(`${OFFER_BIDS_COLLECTION}/${offerBidId(NEXT, "zoe")}`).amount, 30);
  const snapshot = JSON.stringify([...db.store.entries()].filter(([path]) => path.includes("zoe")));
  await run(db, T + 120_000);
  assert.equal(JSON.stringify([...db.store.entries()].filter(([path]) => path.includes("zoe"))), snapshot,
    "второй прогон не трогает ставки открытого месяца");
});

async function main() {
  for (const {name, body} of TESTS) { await body(); console.log(`ok - ${name}`); }
  console.log(`offer-auction-settlement: ${TESTS.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
