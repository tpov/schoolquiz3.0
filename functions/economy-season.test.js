"use strict";

const assert = require("assert/strict");
const {createFirestoreStub} = require("./_firestore-stub");
const {
  ECONOMY_SEASONS_COLLECTION,
  ECONOMY_SEASON_CLOSED,
  INSTALL_MARKER_ID,
  SEASON_CLOSE_LOOKBACK_QUARTERS,
  SEASON_REFERRAL_BATCH,
  closeEconomySeason,
  digest,
  previousQuarter,
  quarterWindow,
  rankTopPlayers,
  snapshotReferralCounters,
  sweepEconomySeasons,
} = require("./economy-season");

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
  assert.equal(season.status, ECONOMY_SEASON_CLOSED);
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
