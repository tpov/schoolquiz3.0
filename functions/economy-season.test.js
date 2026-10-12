"use strict";

const assert = require("assert/strict");
const {createFirestoreStub} = require("./_firestore-stub");
const {
  ECONOMY_SEASONS_COLLECTION,
  ECONOMY_SEASON_CLOSED,
  ECONOMY_SEASON_SETTLED,
  REFERRAL_AWARDS_COLLECTION,
  INSTALL_MARKER_ID,
  SEASON_CLOSE_LOOKBACK_QUARTERS,
  SEASON_PAYOUT_BATCH,
  SEASON_REFERRAL_BATCH,
  boxesForRank,
  closeEconomySeason,
  digest,
  payLeaderboard,
  payReferral,
  previousQuarter,
  quarterWindow,
  rankTopPlayers,
  snapshotReferralCounters,
  sweepEconomySeasons,
} = require("./economy-season");
const {readEconomyConstants} = require("./economy-constants");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");
const {TROPHY_SEASON_CHAMPION} = require("./trophies");

const BANDS = readEconomyConstants(null).seasons.leaderboardBands;
const payoutDoc = (db, uid, seasonId = SEASON) =>
  db.collection(`${ECONOMY_SEASONS_COLLECTION}/${seasonId}/payouts`).doc(uid).get();
const userDoc = async (db, uid) => (await db.collection("users").doc(uid).get()).data();
const payoutDocs = async (db, seasonId = SEASON) =>
  Object.fromEntries((await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${seasonId}/payouts`).get()).docs
    .map((doc) => [doc.id, doc.data()]));

// 2026-04-10 UTC: второй квартал в разгаре, первый созрел 1 апреля.
const T = Date.UTC(2026, 3, 10);
const SEASON = "2026-Q1";
const SEASON_PATH = `${ECONOMY_SEASONS_COLLECTION}/${SEASON}`;
const MARKER_PATH = `${ECONOMY_SEASONS_COLLECTION}/${INSTALL_MARKER_ID}`;
const Q1 = {id: "2026-Q1", year: 2026, quarter: 1, startMs: Date.UTC(2026, 0, 1), endMs: Date.UTC(2026, 3, 1)};

const player = (uid, skillPoints, extra = {}) => ({[uid]: {uid, skillPoints, ...extra}});
// Установка состоялась в феврале: первый квартал — уже живой сезон.
const INSTALLED_IN_Q1 = {
  [MARKER_PATH]: {schemaVersion: 1, marker: "install", quarter: Q1, createdAtMs: Date.UTC(2026, 1, 15)},
};

const makeDb = (users = {}, extra = {}) => {
  const {admin} = createFirestoreStub({
    ...INSTALLED_IN_Q1,
    ...Object.fromEntries(Object.entries(users).map(([uid, data]) => [`users/${uid}`, data])),
    ...extra.docs,
  }, extra.options);
  return admin.firestore();
};
const makeRawDb = (seed = {}, options = {}) => {
  const {admin, store} = createFirestoreStub(seed, options);
  return {db: admin.firestore(), store};
};
const seasonDoc = (db, seasonId = SEASON) => db.collection(ECONOMY_SEASONS_COLLECTION).doc(seasonId).get();
const referralDocs = async (db, seasonId = SEASON) =>
  Object.fromEntries((await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${seasonId}/referrals`).get()).docs
    .map((doc) => [doc.id, doc.data()]));

const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("a matured quarter closes once: top-100 snapshot, digest, CLOSED", async () => {
  const db = makeDb({
    ...player("alice", 500),
    ...player("bob", 300),
    ...player("carol", 400),
  });
  const result = await sweepEconomySeasons({db, now: T});
  assert.deepEqual(result.closed, [SEASON], "закрыт созревший живой квартал — первый, не текущий");
  const season = (await seasonDoc(db)).data();
  assert.equal(season.status, ECONOMY_SEASON_SETTLED,
    "снимок и обе выплаты уместились в один прогон — сезон сразу SETTLED");
  assert.equal(season.schemaVersion, 1);
  assert.equal(season.seasonId, SEASON);
  assert.deepEqual(season.quarter, Q1);
  assert.deepEqual(season.snapshot.top, [
    {rank: 1, uid: "alice", skillPoints: 500},
    {rank: 2, uid: "carol", skillPoints: 400},
    {rank: 3, uid: "bob", skillPoints: 300},
  ], "больше очков выше");
  assert.equal(season.snapshotDigest, digest(season.snapshot), "дайджест подписывает снятый снимок");
  assert.equal(season.referralWalkCursorUid, null);
});

test("the install quarter is the boundary: nothing is older than the season economy itself", async () => {
  // Чистая установка без маркера: первый прогон ставит границу и ничего старше её не закрывает.
  const {db} = makeRawDb({...player("alice", 500)});
  const result = await sweepEconomySeasons({db, now: T});
  assert.deepEqual(result.closed, [], "кварталы до установки не жили сезоном");
  const marker = (await db.collection(ECONOMY_SEASONS_COLLECTION).doc(INSTALL_MARKER_ID).get()).data();
  assert.equal(marker.marker, "install");
  assert.equal(marker.quarter.id, "2026-Q2", "граница — квартал установки");
  assert.equal((await seasonDoc(db)).exists, false, "прошлый квартал остаётся незакрытым");
  // Установка уже стоит: созревшие живые кварталы закрываются, а старше границы ход не заходит.
  const later = Date.UTC(2026, 9, 5); // начало четвёртого квартала: третий и второй созрели
  const second = await sweepEconomySeasons({db, now: later});
  assert.deepEqual(second.closed, ["2026-Q3", "2026-Q2"], "живые сезоны закрываются при созревании");
  assert.equal((await seasonDoc(db, "2026-Q2")).exists, true);
  const third = await sweepEconomySeasons({db, now: later + 60_000});
  assert.deepEqual(third.closed, []);
  assert.equal((await seasonDoc(db, "2026-Q1")).exists, false, "до установки ход не доходит никогда");
});

test("a repeat run changes nothing: snapshot, digest and stores stay put", async () => {
  const db = makeDb({...player("alice", 500)});
  await sweepEconomySeasons({db, now: T});
  const before = (await seasonDoc(db)).data();
  const result = await sweepEconomySeasons({db, now: T + 60_000});
  assert.deepEqual(result.closed, []);
  const after = (await seasonDoc(db)).data();
  assert.deepEqual(after.snapshot, before.snapshot, "снимок не переписывается");
  assert.equal(after.snapshotDigest, before.snapshotDigest);
  assert.equal(after.createdAtMs, before.createdAtMs);
  assert.deepEqual(await referralDocs(db), {}, "обход не идёт второй раз");
});

test("a tampered snapshot is a loud break, not a quietly different picture", async () => {
  const db = makeDb({...player("alice", 500)});
  await sweepEconomySeasons({db, now: T});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const season = (await ref.get()).data();
  await ref.set({...season, snapshot: {top: [{rank: 1, uid: "mallory", skillPoints: 999}]}}, {merge: false});
  await assert.rejects(
    sweepEconomySeasons({db, now: T + 60_000}),
    (error) => error.code === "INVALID_SOURCE" && /digest/.test(error.message),
    "рука, переписавшая снимок, делает сезон громко сломанным");
});

test("the running quarter is not due", async () => {
  const db = makeDb({...player("alice", 500)});
  const current = quarterWindow(T);
  assert.equal(await closeEconomySeason({db, seasonId: current.id, now: T}), "not-due");
  assert.equal((await seasonDoc(db, current.id)).exists, false);
});

test("a season shorter than a hundred players snapshots who is there, ranks 1..N", async () => {
  const db = makeDb({...player("solo", 7)});
  await sweepEconomySeasons({db, now: T});
  const season = (await seasonDoc(db)).data();
  assert.deepEqual(season.snapshot.top, [{rank: 1, uid: "solo", skillPoints: 7}]);
});

test("an empty world closes with an empty top", async () => {
  const db = makeDb({});
  const result = await sweepEconomySeasons({db, now: T});
  assert.deepEqual(result.closed, [SEASON]);
  const season = (await seasonDoc(db)).data();
  assert.deepEqual(season.snapshot.top, []);
  assert.equal(season.snapshotDigest, digest(season.snapshot));
});

test("a tie on points is broken by the smaller uid, deterministically", async () => {
  const db = makeDb({...player("nick", 100), ...player("anna", 100), ...player("zed", 100)});
  const ranked = rankTopPlayers((await db.collection("users").limit(100).get()).docs);
  assert.deepEqual(ranked.map((row) => row.uid), ["anna", "nick", "zed"]);
  await sweepEconomySeasons({db, now: T});
  const season = (await seasonDoc(db)).data();
  assert.deepEqual(season.snapshot.top.map((row) => row.uid), ["anna", "nick", "zed"]);
  assert.equal(season.snapshotDigest, digest(season.snapshot), "дайджест подписывает уже упорядоченный массив");
});

test("a corrupted player document breaks the close loudly, before anything is written", async () => {
  const db = makeDb({broken: {skillPoints: 10}});
  await assert.rejects(sweepEconomySeasons({db, now: T}), {code: "INVALID_ARGUMENT"});
  assert.equal((await seasonDoc(db)).exists, false, "ничего не записано");
});

test("referral counters are snapshotted per player once, then the walk is done", async () => {
  const db = makeDb({
    ...player("invitee-1", 5, {referred: true, referredBy: "boss", openedBoxesTotal: 12}),
    ...player("invitee-2", 6, {referred: true, referredBy: "boss", openedBoxesTotal: 3}),
    ...player("plain", 9, {openedBoxesTotal: 40}),
  });
  await sweepEconomySeasons({db, now: T});
  const walk = await referralDocs(db);
  assert.deepEqual(walk["invitee-1"], {uid: "invitee-1", referredBy: "boss", openedBoxes: 12, capturedAtMs: T});
  assert.deepEqual(walk["invitee-2"], {uid: "invitee-2", referredBy: "boss", openedBoxes: 3, capturedAtMs: T});
  assert.equal(walk.plain, undefined, "без пригласившего счётчик не снимается");
  const season = (await seasonDoc(db)).data();
  assert.equal(season.referralWalkDone, true);
  assert.equal(season.referralWalkCursorUid, "invitee-2");
});

test("no referred players: the walk closes empty and stays done", async () => {
  const db = makeDb({...player("lonely", 1)});
  await sweepEconomySeasons({db, now: T});
  assert.equal((await seasonDoc(db)).data().referralWalkDone, true);
  const walk = await snapshotReferralCounters({db, seasonId: SEASON, now: T + 60_000});
  assert.deepEqual(walk, {captured: 0, done: true}, "второй обход ничего не добавляет");
});

test("counters beyond one portion are captured: the walk resumes from its cursor", async () => {
  const users = {};
  for (let index = 0; index < SEASON_REFERRAL_BATCH + 5; index++) {
    const uid = `inv-${String(index).padStart(3, "0")}`;
    users[uid] = {uid, skillPoints: 1, referred: true, referredBy: "boss", openedBoxesTotal: index};
  }
  const db = makeDb(users);
  await sweepEconomySeasons({db, now: T});
  const walk = await referralDocs(db);
  assert.equal(Object.keys(walk).length, SEASON_REFERRAL_BATCH + 5, "хвост за пределами порции доходит");
  const season = (await seasonDoc(db)).data();
  assert.equal(season.referralWalkDone, true);
  assert.equal(walk["inv-104"].openedBoxes, 104);
});

test("a failed close rolls back whole and the next run replays it", async () => {
  const {db, store} = makeRawDb({
    ...INSTALLED_IN_Q1,
    "users/inv-a": {uid: "inv-a", skillPoints: 1, referred: true, referredBy: "boss", openedBoxesTotal: 1},
  }, {fail: {[SEASON_PATH]: "injected commit failure"}});
  await assert.rejects(sweepEconomySeasons({db, now: T}), /injected commit failure/);
  store.fail = null;
  assert.equal((await seasonDoc(db)).exists, false, "закрытие — та же транзакция, откатилось целиком");
  store.fail = null;
  const result = await sweepEconomySeasons({db, now: T + 60_000});
  assert.deepEqual(result.closed, [SEASON]);
  assert.deepEqual((await referralDocs(db))["inv-a"], {uid: "inv-a", referredBy: "boss", openedBoxes: 1, capturedAtMs: T + 60_000},
    "порция, упавшая целиком, доигрывается следующим прогоном");
});

test("a failed referral portion keeps the season and replays only the remainder", async () => {
  const {db, store} = makeRawDb({
    ...INSTALLED_IN_Q1,
    "users/inv-a": {uid: "inv-a", skillPoints: 1, referred: true, referredBy: "boss", openedBoxesTotal: 1},
    "users/inv-b": {uid: "inv-b", skillPoints: 2, referred: true, referredBy: "boss", openedBoxesTotal: 2},
  });
  await sweepEconomySeasons({db, now: T});
  assert.deepEqual(Object.keys(await referralDocs(db)).sort(), ["inv-a", "inv-b"]);
  const cursorBefore = (await seasonDoc(db)).data().referralWalkCursorUid;
  // Новый приглашённый за курсором; порция с отказом на его документе снимка откатывается целиком.
  db.collection("users").doc("inv-c").set({uid: "inv-c", skillPoints: 3, referred: true, referredBy: "boss", openedBoxesTotal: 3});
  store.fail = {[`${SEASON_PATH}/referrals/inv-c`]: "injected commit failure"};
  await assert.rejects(snapshotReferralCounters({db, seasonId: SEASON, now: T + 120_000}),
    /injected commit failure/);
  store.fail = null;
  const walk = await snapshotReferralCounters({db, seasonId: SEASON, now: T + 180_000, batchSize: 1});
  assert.deepEqual(walk, {captured: 1, done: true});
  assert.deepEqual((await referralDocs(db))["inv-c"], {uid: "inv-c", referredBy: "boss", openedBoxes: 3, capturedAtMs: T + 180_000});
  assert.equal((await seasonDoc(db)).data().referralWalkCursorUid, "inv-c", "курсор движется только вперёд");
  assert.ok(cursorBefore !== "inv-c");
});

test("a referred player without a referrer, or its own referrer, breaks the portion loudly", async () => {
  const orphan = makeDb({...player("orphan", 1, {referred: true, openedBoxesTotal: 1})});
  await assert.rejects(sweepEconomySeasons({db: orphan, now: T}), {code: "INVALID_ARGUMENT"});
  // Сезон уже закрыт — сборка счётчиков падает своей транзакцией, снимок не тронут.
  assert.equal((await seasonDoc(orphan)).data().status, ECONOMY_SEASON_CLOSED);
  assert.deepEqual(await referralDocs(orphan), {}, "порция не пишет ничего");
  const resumed = await sweepEconomySeasons({db: orphan, now: T + 60_000}).then(
    () => { throw new Error("ход не должен был пройти по испорченной строке"); },
    (error) => error);
  assert.equal(resumed.code, "INVALID_ARGUMENT", "ход не идёт, пока испорченная строка на месте");
  // Починка строки: следующий прогон доигрывает обход, ничего не сняв дважды.
  await orphan.collection("users").doc("orphan").set({referredBy: "boss"}, {merge: true});
  const recovered = await sweepEconomySeasons({db: orphan, now: T + 120_000});
  assert.deepEqual(recovered.closed, [], "закрытие не повторяется");
  assert.equal((await referralDocs(orphan))["orphan"].openedBoxes, 1, "починенная строка доигрывается");
  const narciss = makeDb({...player("narciss", 1, {referred: true, referredBy: "narciss", openedBoxesTotal: 1})});
  await assert.rejects(sweepEconomySeasons({db: narciss, now: T}), {code: "INVALID_ARGUMENT"});
});

test("quarters missed by downtime are caught up, each behind its own guard", async () => {
  // Установка в Q1, прога не работала весь Q2; теперь третий квартал.
  const late = Date.UTC(2026, 6, 2);
  const db = makeDb({...player("alice", 500)});
  const result = await sweepEconomySeasons({db, now: late});
  assert.deepEqual(result.closed, ["2026-Q2", "2026-Q1"], "созревшие живые сезоны закрываются по одному");
  const q1 = (await seasonDoc(db, "2026-Q1")).data();
  const q2 = (await seasonDoc(db, "2026-Q2")).data();
  assert.equal(q1.createdAtMs, late, "сезон закрывается первым прогоном после созревания");
  assert.equal(q2.createdAtMs, late);
  const fourth = await sweepEconomySeasons({db, now: Date.UTC(2027, 0, 5)});
  assert.deepEqual(fourth.closed, ["2026-Q4", "2026-Q3"], "ход ограничен, но каждый созревший живой квартал дожидается");
  assert.equal(fourth.closed.length <= SEASON_CLOSE_LOOKBACK_QUARTERS, true);
});

test("a corrupted season document is refused, not silently re-snapshotted", async () => {
  const db = makeDb({...player("alice", 500)});
  await sweepEconomySeasons({db, now: T});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const season = (await ref.get()).data();
  await ref.set({...season, status: "MYSTERY"}, {merge: false});
  await assert.rejects(sweepEconomySeasons({db, now: T + 60_000}), {code: "INVALID_SOURCE"});
  assert.equal((await seasonDoc(db)).data().status, "MYSTERY", "испорченное не молча чинится");
});

test("a corrupted install marker breaks the sweep loudly", async () => {
  const {db} = makeRawDb({...player("alice", 500), [MARKER_PATH]: {marker: "something-else"}});
  await assert.rejects(sweepEconomySeasons({db, now: T}), {code: "INVALID_SOURCE"});
  const shifted = makeRawDb({...player("alice", 500),
    [MARKER_PATH]: {schemaVersion: 1, marker: "install", quarter: {...Q1, startMs: Date.UTC(2025, 0, 1)}, createdAtMs: T}});
  await assert.rejects(sweepEconomySeasons({db: shifted.db, now: T}), {code: "INVALID_SOURCE"},
    "границы маркера сверяются с календарём, не только имя");
});

test("boundaries: quarter windows are calendar quarters, ids are quarter keys", async () => {
  assert.deepEqual(quarterWindow(Date.UTC(2026, 0, 1)), Q1);
  assert.deepEqual(quarterWindow(Date.UTC(2026, 11, 31, 23, 59, 59, 999)).id, "2026-Q4");
  assert.deepEqual(previousQuarter(quarterWindow(Date.UTC(2026, 0, 1))).id, "2025-Q4");
  assert.deepEqual(previousQuarter(Q1), {id: "2025-Q4", year: 2025, quarter: 4, startMs: Date.UTC(2025, 9, 1), endMs: Date.UTC(2026, 0, 1)});
  await assert.rejects(closeEconomySeason({db: makeDb({}), seasonId: "2026-Q5", now: T}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(closeEconomySeason({db: makeDb({}), seasonId: "november", now: T}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(snapshotReferralCounters({db: makeDb({}), seasonId: "2026/1", now: T}), {code: "INVALID_ARGUMENT"});
});


test("every paid rank takes its band, once, and the champion takes the trophy", async () => {
  const users = {};
  for (let rank = 1; rank <= 100; rank++) {
    const uid = `rank-${String(rank).padStart(3, "0")}`;
    users[uid] = {uid, skillPoints: 10_000 - rank};
  }
  const db = makeDb(users);
  const result = await sweepEconomySeasons({db, now: T});
  assert.deepEqual(result.closed, [SEASON]);
  assert.equal(result.paid, 100, "каждое место снимка оплачено ровно один раз");
  assert.equal(result.paidBoxes, 2315, "полный сеттл сезона стоит 2315 коробок — как в gold-flows");
  const docs = await payoutDocs(db);
  assert.equal(Object.keys(docs).length, 100);
  const expected = {1: 500, 2: 250, 3: 125, 4: 70, 5: 70, 6: 50, 10: 50,
    11: 25, 30: 25, 31: 15, 50: 15, 51: 5, 100: 5};
  for (const [rank, boxes] of Object.entries(expected)) {
    assert.equal(docs[`rank-${rank.padStart(3, "0")}`].boxes, boxes, `место ${rank} — своя полоса`);
  }
  assert.equal(docs["rank-001"].trophy, true, "первому месту — трофей");
  assert.equal(docs["rank-002"].trophy, false);
  assert.equal((await userDoc(db, "rank-001")).boxCount, 500);
  assert.deepEqual((await userDoc(db, "rank-001")).trophies, [TROPHY_SEASON_CHAMPION], "трофей — именованный значок в списке");
  assert.equal((await userDoc(db, "rank-100")).trophies, undefined, "трофей только у чемпиона");
  const season = (await seasonDoc(db)).data();
  assert.equal(season.leaderboardPaid, true);
  assert.equal(season.leaderboardCursorRank, 100);
  const ledger = (await db.collection(LEDGER_PATH).get()).docs
    .filter((doc) => doc.data().reason === REASON.SEASON_LEADERBOARD);
  assert.equal(ledger.length, 100, "каждая выплата — строка книги");
  const row = ledger.find((doc) => doc.data().uid === "rank-001").data();
  assert.equal(row.currency, CURRENCY.BOXES);
  assert.equal(row.delta, 500);
  assert.deepEqual(row.details, {seasonId: SEASON, rank: 1, trophy: true});
  const receipt = (await db.collection("users/rank-001/receipts").doc(`season_leaderboard_${SEASON}`).get()).data();
  assert.equal(receipt.messageKey, "receipt.season_leaderboard", "причина приезжает ключом, а не строкой");
  assert.deepEqual(receipt.params, {seasonId: SEASON, rank: 1, boxes: 500, trophy: true});
  assert.equal(receipt.amount, 500);
  assert.equal(receipt.currency, "BOXES");
});

test("a repeat payout changes nothing: balances, docs and receipts stay put", async () => {
  const db = makeDb({...player("gold", 900), ...player("silver", 800)});
  await sweepEconomySeasons({db, now: T});
  const before = structuredClone({
    users: [await userDoc(db, "gold"), await userDoc(db, "silver")],
    payouts: await payoutDocs(db),
    season: (await seasonDoc(db)).data(),
  });
  const again = await sweepEconomySeasons({db, now: T + 60_000});
  assert.equal(again.paid, 0);
  assert.deepEqual([await userDoc(db, "gold"), await userDoc(db, "silver")], before.users, "повтор не платит второй раз");
  assert.deepEqual(await payoutDocs(db), before.payouts);
  assert.equal((await seasonDoc(db)).data().leaderboardPaid, true);
});

test("a payout portion that falls rolls back whole and the next run continues from the cursor", async () => {
  const users = {};
  for (let index = 0; index < SEASON_PAYOUT_BATCH + 3; index++) {
    const uid = `rank-${String(index + 1).padStart(3, "0")}`;
    users[uid] = {uid, skillPoints: 10_000 - index};
  }
  const {db, store} = makeRawDb({...INSTALLED_IN_Q1, ...Object.fromEntries(
    Object.entries(users).map(([uid, data]) => [`users/${uid}`, data]))});
  await sweepEconomySeasons({db, now: T});
  let docs = await payoutDocs(db);
  assert.equal(Object.keys(docs).length, SEASON_PAYOUT_BATCH + 3, "хвост за пределами порции доходит");
  assert.equal((await seasonDoc(db)).data().leaderboardPaid, true);
  // Порция с отказом: транзакция откатывается целиком, курсор остаётся на префиксе оплаченных.
  const resumed = makeRawDb({...INSTALLED_IN_Q1, ...Object.fromEntries(
    Object.entries(users).map(([uid, data]) => [`users/${uid}`, data]))},
    {fail: {[`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts/rank-051`]: "injected commit failure"}});
  await assert.rejects(sweepEconomySeasons({db: resumed.db, now: T}), /injected commit failure/);
  resumed.store.fail = null;
  const result = await sweepEconomySeasons({db: resumed.db, now: T + 60_000});
  assert.ok(result.paid >= 3, "остаток доигрывается следующим прогоном");
  docs = await payoutDocs(resumed.db);
  assert.equal(Object.keys(docs).length, SEASON_PAYOUT_BATCH + 3);
  assert.equal((await resumed.db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON).get()).data().leaderboardPaid, true);
  assert.equal((await userDoc(resumed.db, "rank-051")).boxCount, 5, "последнее место получает свою полосу");
});

test("an empty top is paid at once, and a rank outside the table is never paid", async () => {
  const empty = makeDb({});
  const result = await sweepEconomySeasons({db: empty, now: T});
  assert.equal(result.paid, 0);
  assert.equal((await seasonDoc(empty)).data().leaderboardPaid, true, "пустой топ закрывается выплатой в ноль");
  assert.deepEqual(await payoutDocs(empty), {});
  assert.equal(boxesForRank(BANDS, 100), 5, "сотое место — последняя полоса");
  assert.equal(boxesForRank(BANDS, 101), 0, "ниже сотни таблица не платит");
  assert.equal(boxesForRank(BANDS, 1), 500);
  assert.equal(boxesForRank(BANDS, 4), 70, "границы полос полуоткрыты в пользу верхней");
  assert.equal(boxesForRank(BANDS, 5), 70);
  assert.equal(boxesForRank(BANDS, 6), 50);
  assert.throws(() => boxesForRank(BANDS, 0), {code: "INVALID_ARGUMENT"});
});

test("a payout reads the player the way the box house does: no document materialises as zero", async () => {
  // Снимок держит uid, документа игрока нет: выплата материалит документ нулевым состоянием,
  // как box-service читает игроков.
  const db = makeDb({});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const snapshot = {top: [{rank: 1, uid: "ghost", skillPoints: 900}]};
  await ref.set({schemaVersion: 1, seasonId: SEASON, quarter: Q1, status: ECONOMY_SEASON_CLOSED,
    snapshot, snapshotDigest: digest(snapshot), referralWalkCursorUid: null, createdAtMs: T}, {merge: false});
  const result = await payLeaderboard({db, seasonId: SEASON, now: T + 60_000, constants: readEconomyConstants(null)});
  assert.deepEqual(result, {paid: 1, boxes: 500, done: true});
  assert.equal((await userDoc(db, "ghost")).boxCount, 500, "документ материален и ждёт игрока");
  assert.deepEqual((await userDoc(db, "ghost")).trophies, [TROPHY_SEASON_CHAMPION]);
});

test("a payout refuses a corrupted season document before any credit", async () => {
  const db = makeDb({...player("gold", 900)});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const snapshot = {top: [{rank: 1, uid: "gold", skillPoints: 900}]};
  await ref.set({schemaVersion: 1, seasonId: SEASON, quarter: Q1, status: ECONOMY_SEASON_CLOSED,
    snapshot, snapshotDigest: digest({top: []}), referralWalkCursorUid: null, createdAtMs: T}, {merge: false});
  await assert.rejects(
    payLeaderboard({db, seasonId: SEASON, now: T + 60_000, constants: readEconomyConstants(null)}),
    {code: "INVALID_SOURCE"});
  assert.equal((await userDoc(db, "gold")).boxCount, undefined, "испорченный сезон не платит");
  assert.deepEqual(await payoutDocs(db), {});
});

test("a snapshot longer than a hundred is a loud break, not a quiet tail of zero payouts", async () => {
  const db = makeDb({});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const top = [];
  for (let rank = 1; rank <= 101; rank++) top.push({rank, uid: `rank-${rank}`, skillPoints: 1000 - rank});
  const snapshot = {top};
  await ref.set({schemaVersion: 1, seasonId: SEASON, quarter: Q1, status: ECONOMY_SEASON_CLOSED,
    snapshot, snapshotDigest: digest(snapshot), referralWalkCursorUid: null, createdAtMs: T}, {merge: false});
  await assert.rejects(
    payLeaderboard({db, seasonId: SEASON, now: T + 60_000, constants: readEconomyConstants(null)}),
    {code: "INVALID_SOURCE"});
  assert.deepEqual(await payoutDocs(db), {});
});

test("payLeaderboard without a bands table refuses loudly", async () => {
  const db = makeDb({...player("gold", 900)});
  await sweepEconomySeasons({db, now: T});
  await assert.rejects(
    payLeaderboard({db, seasonId: SEASON, now: T, constants: {}}),
    {code: "INVALID_ARGUMENT"});
});

test("the referral percent pays the season difference once, floored on the sum", async () => {
  const db = makeDb({
    ...player("boss", 1, {openedBoxesTotal: 0}),
    ...player("inv-a", 2, {referred: true, referredBy: "boss", openedBoxesTotal: 149}),
    ...player("inv-b", 3, {referred: true, referredBy: "boss", openedBoxesTotal: 51}),
  });
  await sweepEconomySeasons({db, now: T});
  // Первый сезон: база ноль (ни разу не сняты) — 1% от (149 + 51) = 2 коробки.
  const payout = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts`).doc("referral_boss").get()).data();
  assert.equal(payout.kind, "REFERRAL");
  assert.equal(payout.openings, 200);
  assert.equal(payout.boxes, 2, "floor(200 × 1%) = 2");
  assert.equal((await userDoc(db, "boss")).boxCount, 127,
    "к проценту (2) добавляется полоса лидерборда: boss — третье место (125)");
  const ledger = (await db.collection(LEDGER_PATH).get()).docs.find((doc) => doc.data().reason === REASON.SEASON_REFERRAL).data();
  assert.deepEqual(ledger.details, {seasonId: SEASON, openings: 200, percent: 1, invitees: 2});
  const receipt = (await db.collection("users/boss/receipts").doc(`season_referral_${SEASON}`).get()).data();
  assert.equal(receipt.messageKey, "receipt.season_referral_percent");
  assert.deepEqual(receipt.params, {seasonId: SEASON, openings: 200, boxes: 2, percent: 1, invitees: 2});
  // Второй сезон: разность снимков — открывший ещё 100 у первого приглашённого платит 1 коробку.
  await db.collection("users").doc("inv-a").set({openedBoxesTotal: 249}, {merge: true});
  const later = Date.UTC(2026, 6, 2);
  const result = await sweepEconomySeasons({db, now: later});
  assert.equal(result.referralSettled, 1);
  const next = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/2026-Q2/payouts`).doc("referral_boss").get()).data();
  assert.equal(next.openings, 100, "база — снимок прошлого сезона, не ноль");
  assert.equal(next.boxes, 1);
  assert.equal((await userDoc(db, "boss")).boxCount, 253,
    "127 + 1 за процент второго сезона + 125 за его полосу в лидерборде того же сезона");
});

test("a zero difference settles the season without crediting anything", async () => {
  const db = makeDb({
    ...player("boss", 1),
    ...player("inv-a", 2, {referred: true, referredBy: "boss", openedBoxesTotal: 7}),
  });
  await sweepEconomySeasons({db, now: T});
  const payout = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts`).doc("referral_boss").get()).data();
  assert.equal(payout.boxes, 0, "floor(7 × 1%) = 0 — сезон засчитан с нулём");
  assert.equal((await userDoc(db, "boss")).boxCount, 250,
    "ноль процента ничего не начислил — на счету только полоса лидерборда (второе место)");
  const repeat = await sweepEconomySeasons({db, now: T + 60_000});
  assert.equal(repeat.referralSettled, 0, "повтор не пересчитывает погашенного");
});

test("a lost middle snapshot falls back to the older one: no double pay, no lost money", async () => {
  const users = {
    ...player("boss", 1),
    ...player("inv-a", 2, {referred: true, referredBy: "boss", openedBoxesTotal: 100}),
  };
  const db = makeDb(users);
  await sweepEconomySeasons({db, now: T}); // 2026-Q1: снимок inv-a = 100, процент 1 коробка
  await db.collection("users").doc("inv-a").set({openedBoxesTotal: 160}, {merge: true});
  await sweepEconomySeasons({db, now: Date.UTC(2026, 6, 2)}); // Q2: снимок 160, процент 0 (60×1%)
  await db.runTransaction(async (transaction) => {
    transaction.delete(db.collection(`${ECONOMY_SEASONS_COLLECTION}/2026-Q2/referrals`).doc("inv-a"));
  });
  await db.collection("users").doc("inv-a").set({openedBoxesTotal: 200}, {merge: true});
  await sweepEconomySeasons({db, now: Date.UTC(2026, 9, 2)}); // Q3: снимка Q2 нет — база Q1 (100)
  const payout = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/2026-Q3/payouts`).doc("referral_boss").get()).data();
  assert.equal(payout.openings, 100, "разность от более старой базы: 200 − 100, не от нуля");
  assert.equal(payout.boxes, 1);
  const total = [...(await db.collection(LEDGER_PATH).get()).docs]
    .filter((doc) => doc.data().reason === REASON.SEASON_REFERRAL)
    .reduce((sum, doc) => sum + doc.data().delta, 0);
  assert.equal(total, 2, "за три сезона заплачено 1 + 0 + 1 — ни дважды, ни меньше");
});

test("six invitees with ten boxes each pay the fifty once, five do not", async () => {
  const six = {};
  for (let index = 0; index < 6; index++) {
    six[`inv-${index}`] = {uid: `inv-${index}`, skillPoints: 2, referred: true, referredBy: "boss",
      openedBoxesTotal: 10 + index};
  }
  const db = makeDb({...player("boss", 1), ...six});
  await sweepEconomySeasons({db, now: T});
  const award = (await db.collection(REFERRAL_AWARDS_COLLECTION).doc("boss").get()).data();
  assert.equal(award.boxes, 50);
  assert.equal(award.invitees, 6);
  assert.equal(award.each, 10);
  assert.equal((await userDoc(db, "boss")).boxCount, 100,
    "50 за шестерых плюс полоса лидерборда (седьмое место — 50); процент 75×1% = 0 коробок");
  const receipt = (await db.collection("users/boss/receipts").doc("referral_bonus").get()).data();
  assert.equal(receipt.messageKey, "receipt.referral_bonus");
  assert.deepEqual(receipt.params, {boxes: 50, invitees: 6, each: 10});
  const saved = {
    award: structuredClone(award),
    boss: structuredClone(await userDoc(db, "boss")),
  };
  const again = await sweepEconomySeasons({db, now: T + 60_000});
  assert.equal(again.referralAwarded, 0, "повтор не награждает второй раз");
  assert.deepEqual((await db.collection(REFERRAL_AWARDS_COLLECTION).doc("boss").get()).data(), award,
    "документ награды не переписывается");
  assert.deepEqual(await userDoc(db, "boss"), saved.boss);
  // Пятеро — не шестеро: награды нет, процент не тронут.
  const five = {};
  for (let index = 0; index < 5; index++) {
    five[`pal-${index}`] = {uid: `pal-${index}`, skillPoints: 2, referred: true, referredBy: "shy",
      openedBoxesTotal: 50};
  }
  const shyDb = makeDb({...player("shy", 1), ...five});
  await sweepEconomySeasons({db: shyDb, now: T});
  assert.equal((await db.collection(REFERRAL_AWARDS_COLLECTION).doc("shy").get()).exists, false);
  assert.equal((await userDoc(shyDb, "shy")).boxCount, 52,
    "процент платится (250×1% = 2) и полоса шестого места (50), награды нет");
});

test("a chain pays one level: nobody earns from openings they did not cause", async () => {
  const db = makeDb({
    ...player("top", 1),
    ...player("mid", 2, {referred: true, referredBy: "top", openedBoxesTotal: 300}),
    ...player("leaf", 3, {referred: true, referredBy: "mid", openedBoxesTotal: 300}),
  });
  await sweepEconomySeasons({db, now: T});
  const top = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts`).doc("referral_top").get()).data();
  const mid = (await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts`).doc("referral_mid").get()).data();
  assert.equal(top.openings, 300, "top платят только за mid");
  assert.equal(mid.openings, 300, "mid платят только за leaf");
  assert.equal(top.boxes, 3);
  assert.equal(mid.boxes, 3);
});

test("both payouts finished, the season settles and the sweep leaves it alone", async () => {
  const db = makeDb({
    ...player("boss", 1),
    ...player("inv-a", 2, {referred: true, referredBy: "boss", openedBoxesTotal: 200}),
  });
  const result = await sweepEconomySeasons({db, now: T});
  assert.equal((await seasonDoc(db)).data().status, ECONOMY_SEASON_SETTLED, "выплачено всё — сезон SETTLED");
  const before = structuredClone((await seasonDoc(db)).data());
  assert.deepEqual(before.status, ECONOMY_SEASON_SETTLED);
  const again = await sweepEconomySeasons({db, now: T + 60_000});
  assert.deepEqual(again, {closed: [], captured: 0, paid: 0, paidBoxes: 0, referralSettled: 0, referralBoxes: 0, referralAwarded: 0, skipped: 1});
  assert.deepEqual((await seasonDoc(db)).data(), before, "SETTLED не переписывается");
  void result;
});

test("more invitees than the read limit is a loud break, not a silent cut", async () => {
  const users = {...player("boss", 1)};
  for (let index = 0; index < 201; index++) {
    users[`crowd-${String(index).padStart(3, "0")}`] = {uid: `crowd-${String(index).padStart(3, "0")}`,
      skillPoints: 2, referred: true, referredBy: "boss", openedBoxesTotal: 10};
  }
  const db = makeDb(users);
  await assert.rejects(sweepEconomySeasons({db, now: T}), {code: "INVALID_SOURCE"},
    "переполненный пригласивший роняет расчёт громко, а не режет его молча");
  assert.equal((await db.collection(`${ECONOMY_SEASONS_COLLECTION}/${SEASON}/payouts`).doc("referral_boss").get()).exists,
    false, "испорченное не платит");
  assert.equal((await seasonDoc(db)).data().referralPaid, undefined, "ход не отмечен сделанным");
});

test("a corrupted season document refuses the referral payout too", async () => {
  const db = makeDb({...player("boss", 1),
    ...player("inv-a", 2, {referred: true, referredBy: "boss", openedBoxesTotal: 200})});
  await sweepEconomySeasons({db, now: T});
  const ref = db.collection(ECONOMY_SEASONS_COLLECTION).doc(SEASON);
  const season = (await ref.get()).data();
  await ref.set({...season, status: "MYSTERY"}, {merge: false});
  await assert.rejects(payReferral({db, seasonId: SEASON, now: T + 60_000, constants: readEconomyConstants(null)}),
    {code: "INVALID_SOURCE"});
});

(async () => {
  let failed = 0;
  for (const {name, body} of TESTS) {
    try {
      await body();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.error(`NOT OK - ${name}`);
      console.error(error);
    }
  }
  console.log(`${TESTS.length - failed}/${TESTS.length} tests passed`);
  if (failed > 0) process.exitCode = 1;
})();
