"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {tournamentDigest: digest} = require("./tournament-bank");
const {TOURNAMENT_PATHS: P} = require("./tournament-service");
const {VERIFIED_RUNS} = require("./tournament-session");
const {freezeQualification} = require("./tournament-qualification-freeze");
const {calculateAndPublishQualificationRanking} = require("./tournament-qualification-scan");
const {LEASE_MS} = require("./tournament-qualification-calculation");
const {calculationFixture, END, EVENT, E, R, uidAt} = require("./qualification-calculation-test-fixture");

const clone = value => structuredClone(value);
const canonicalProgress = head => {
  const {revision, lease, checkpointDigest, ...body} = head; return body;
};
const reseal = object => { const {checkpointDigest, ...body} = object; return {...body, checkpointDigest: digest(body)}; };
function assertSourcesUnchanged(f) {
  for (const [path, hash] of f.frozen) if (path !== f.scan) assert.equal(digest(f.db.store.get(path)), hash, path);
}
function assertCalculatorWritesOnly(f, fromCommit) {
  for (const commit of f.db.commits.slice(fromCommit)) for (const write of commit) {
    assert.ok(write.path === f.job || write.path.startsWith(`${f.job}/`) || write.path === f.scan || write.path === R, `unexpected write ${write.path}`);
  }
}

for (const size of [0, 1, 2, 15, 16, 17]) test(`full durable phases preserve complete ${size}-player cohort and funds`, async () => {
  const f = await calculationFixture(size); const commits = f.db.commits.length; const phases = new Set();
  let result; let calls = 0;
  do {
    result = await f.advance({pageSize: 7}); calls++; phases.add(f.state()?.phase);
    assert.ok(calls <= 20); if (!result.complete) assert.equal(f.ranking(), undefined);
  } while (!result.complete);
  const ranked = size >= 2 ? size : 0;
  assert.equal(result.ranking.schemaVersion, 2); assert.equal(result.ranking.verifiedRunCount, size);
  assert.equal(result.ranking.totalRanked, ranked); assert.equal(result.ranking.ranking.length, Math.min(16, ranked));
  assert.equal(f.state().phase, "PUBLISHED"); assert.equal(f.state().lease, null); assert.equal(f.db.store.get(f.scan).status, "PUBLISHED");
  for (const phase of ["REDUCE", "SOLVE", "PROJECT", "PUBLISH", "PUBLISHED"]) assert.ok(phases.has(phase), phase);
  assertSourcesUnchanged(f); assertCalculatorWritesOnly(f, commits);
  const saved = clone([...f.db.store]); const replay = await f.advance();
  assert.equal(replay.replayed, true); assert.deepEqual(replay.ranking, result.ranking); assert.deepEqual([...f.db.store], saved);
});
test("calculator accepts the manifest produced by the actual durable scanner", async () => {
  const f = await calculationFixture(3, 3, {realScan: true}); const commits = f.db.commits.length;
  const done = await f.drain(); assert.equal(done.result.ranking.verifiedRunCount, 9); assert.equal(done.result.ranking.totalRanked, 3);
  assertSourcesUnchanged(f); assertCalculatorWritesOnly(f, commits);
});
test("restart after every phase and different page sizes equal uninterrupted execution", async () => {
  const f = await calculationFixture(17, 3); const reference = await calculationFixture(17, 3);
  const done = await reference.drain({maxPages: 10, pageSize: 50});
  for (const phase of ["VALIDATE", "REDUCE", "SOLVE", "PROJECT", "PUBLISH"]) {
    await f.reach(phase);
    for (const [path, value] of f.db.store) f.db.store.set(path, JSON.parse(JSON.stringify(value)));
  }
  const recovered = await f.drain({pageSize: 3});
  for (const key of ["ranking", "verifiedRunsFingerprint", "calculationContractDigest", "calculationVersions", "totalRanked", "verifiedRunCount"]) {
    assert.deepEqual(recovered.result.ranking[key], done.result.ranking[key], key);
  }
  assertSourcesUnchanged(f);
});
for (const phase of ["VALIDATE", "REDUCE", "SOLVE", "PROJECT", "PUBLISH"]) test(`failed ${phase} commit rolls back semantic progress and retries the same page`, async () => {
  const f = await calculationFixture(3, 2); await f.reach(phase); const before = clone(canonicalProgress(f.state()));
  const playerBefore = clone(f.playerDocuments()); const scanBefore = clone(f.db.store.get(f.scan)); let failed = false;
  f.controls.beforeCommit = writes => {
    const changed = writes.find(w => w.path === f.job && w.value.phase && (w.value.phase !== phase ||
      w.value.nextChunk !== before.nextChunk || digest(w.value.reduction) !== digest(before.reduction) || digest(w.value.projection) !== digest(before.projection)));
    if (!failed && changed) { failed = true; f.db.failNextCommit(); }
  };
  await assert.rejects(f.advance()); f.controls.beforeCommit = null; assert.equal(failed, true);
  assert.deepEqual(canonicalProgress(f.state()), before); assert.deepEqual(f.playerDocuments(), playerBefore);
  assert.deepEqual(f.db.store.get(f.scan), scanBefore); assert.equal(f.ranking(), undefined);
  assert.equal((await f.drain()).result.ranking.verifiedRunCount, 6); assertSourcesUnchanged(f);
});
test("automatic transaction callback retry cannot duplicate masks, players, runs or publication", async () => {
  const f = await calculationFixture(7, 3); f.controls.repeatCallbacks = true;
  const done = await f.drain({pageSize: 3});
  assert.equal(done.result.ranking.verifiedRunCount, 21); assert.equal(done.result.ranking.totalRanked, 7);
  assert.equal(f.playerDocuments().length, 7); assert.equal(f.state().verifiedRuns, 21); assertSourcesUnchanged(f);
  assert.equal(f.db.commits.flat().filter(w => w.path === R).length, 1);
});
test("concurrent invocations have one lease owner and eventually the same single publication", async () => {
  const f = await calculationFixture(17, 3);
  const first = await Promise.all([f.advance(), f.advance(), f.advance()]);
  assert.ok(first.some(r => r.status === "CALCULATION_LEASE_HELD"));
  const done = await f.drain(); assert.equal(done.result.ranking.verifiedRunCount, 51);
  assert.equal(f.db.commits.flat().filter(w => w.path === R).length, 1); assertSourcesUnchanged(f);
});
test("expired lease is recoverable while stale worker cannot overwrite replacement ownership", async () => {
  const f = await calculationFixture(3); await f.reach("REDUCE");
  let takeover = false; const token = "simulated-new-worker";
  f.controls.beforeTransaction = () => {
    const head = f.state();
    if (!takeover && head.lease?.token && head.lease.token !== token) {
      takeover = true; f.db.store.set(f.job, reseal({...head, revision: head.revision + 1,
        lease: {token, expiresAtMs: f.controls.now + LEASE_MS}}));
    }
  };
  const result = await f.advance(); f.controls.beforeTransaction = null;
  assert.equal(takeover, true); assert.equal(result.status, "CALCULATION_LEASE_LOST");
  assert.equal(f.state().lease.token, token); assert.equal(f.state().phase, "REDUCE");
  assert.equal((await f.advance()).status, "CALCULATION_LEASE_HELD");
  f.controls.now += LEASE_MS + 1;
  assert.equal((await f.drain()).result.ranking.verifiedRunCount, 3);
});
test("deadline reached during source reads discards the page, then resumes without financial effects", async () => {
  const f = await calculationFixture(3); await f.reach("VALIDATE"); const before = clone(canonicalProgress(f.state())); let spent = false;
  const deadlineAtMs = f.controls.now + 500;
  f.controls.beforeRead = path => { if (!spent && path.startsWith(`${f.scan}/rows/`)) { spent = true; f.controls.now = deadlineAtMs; } };
  const result = await f.advance({deadlineAtMs}); f.controls.beforeRead = null;
  assert.equal(result.status, "CALCULATION_TIME_PENDING"); assert.deepEqual(canonicalProgress(f.state()), before);
  assert.equal((await f.drain()).result.ranking.verifiedRunCount, 3); assertSourcesUnchanged(f);
});
test("already-expired invocation creates no job or ranking", async () => {
  const f = await calculationFixture(2); const before = clone([...f.db.store]);
  assert.equal((await f.advance({deadlineAtMs: f.controls.now})).status, "CALCULATION_TIME_PENDING");
  assert.deepEqual([...f.db.store], before);
});
test("source identity, manifest chunk, checkpoint and counted source corruption fail closed", async () => {
  for (const mutate of [f => { f.db.store.get(E).source.snapshotId = "other"; },
    f => { f.db.store.delete(`${f.scan}/chunks/0000000000000000`); },
    f => { f.state().verifiedRuns++; }, f => { f.db.store.get(f.scan).admittedCount++; }]) {
    const f = await calculationFixture(3); await f.reach("VALIDATE"); mutate(f);
    await assert.rejects(f.advance()); assert.equal(f.ranking(), undefined);
  }
});
for (const phase of ["REDUCE", "PROJECT"]) test(`${phase} revalidates missing/changed scan, session and copied player after VALIDATE`, async () => {
  for (const mutate of [f => { f.db.store.delete(f.scanRows()[0][0]); }, f => {
    const admission = f.playerDocuments()[0][1].admissions[0]; f.db.store.get(`${VERIFIED_RUNS}/${admission.sessionId}`).percentScore = 101;
  }, f => {
    const admission = f.playerDocuments()[0][1].admissions[0]; f.db.store.get(`${P.sessions}/${admission.sessionId}`).scoreDigits = "9".repeat(20);
  }, f => {
    const [path, raw] = f.playerDocuments()[0]; raw.player.results[0].percent = raw.player.results[0].percent === 100 ? 0 : 100;
    raw.playerDigest = digest(raw.player); f.db.store.set(path, raw);
  }]) {
    const f = await calculationFixture(3, 3); await f.reach(phase); mutate(f);
    await assert.rejects(f.advance()); assert.equal(f.ranking(), undefined);
  }
});
test("deleted/substituted player projection cannot hide a missing cohort member", async () => {
  const f = await calculationFixture(3); await f.reach("REDUCE"); f.db.store.delete(f.playerDocuments()[0][0]);
  await assert.rejects(f.drain()); assert.equal(f.ranking(), undefined);
});
test("both old calculation publishers refuse a selected version without creating a ranking", async () => {
  const f = await calculationFixture(3);
  await assert.rejects(freezeQualification({db: f.db, eventId: EVENT, now: END, pageSize: 50, maxPages: 10}), {code: "UNAVAILABLE"});
  await assert.rejects(calculateAndPublishQualificationRanking({db: f.db, eventId: EVENT, now: END}), {code: "UNAVAILABLE"});
  assert.equal(f.ranking(), undefined); assert.equal((await f.drain()).result.complete, true);
});
test("malformed prior ranking cannot be reported as successful replay", async () => {
  const seed = await calculationFixture(2); const saved = clone((await seed.drain()).result.ranking);
  for (const mutate of [r => { r.ranking = []; }, r => { r.totalRanked = -1; }, r => { r.verifiedRunCount = 0; },
    r => { r.ranking[1].uid = r.ranking[0].uid; }, r => { r.ranking[0].place = 2; }, r => { r.formsFingerprint = digest("other"); }]) {
    const f = await calculationFixture(2); const bad = clone(saved); mutate(bad); f.db.store.set(R, bad);
    const before = clone([...f.db.store]); await assert.rejects(f.advance(), {code: "UNAVAILABLE"}); assert.deepEqual([...f.db.store], before);
  }
});
test("unpublished work selected under a different UID runtime cannot acquire a job", async () => {
  const f = await calculationFixture(2); const e = f.db.store.get(E); const c = e.qualificationCalculation;
  c.collation.icu = "historical-runtime"; const {contractDigest, freezeId, ...anchor} = c;
  c.contractDigest = digest(anchor); c.freezeId = `freeze_${digest(["qualification-calculation", c.contractDigest])}`;
  e.qualificationFreezeFence.freezeId = c.freezeId;
  const before = clone([...f.db.store]); await assert.rejects(f.advance(), {code: "UNAVAILABLE"}); assert.deepEqual([...f.db.store], before);
});
test("terminal event rejects unfinished acquisition but replays immutable published ranking", async () => {
  for (const status of ["BRACKET", "FINISHED", "CANCELLED"]) {
    for (const initialized of [false, true]) {
      const f = await calculationFixture(2); if (initialized) await f.reach("REDUCE");
      f.db.store.get(E).status = status; const before = clone([...f.db.store]);
      await assert.rejects(f.advance(), {code: "UNAVAILABLE"}); assert.deepEqual([...f.db.store], before);
      assert.equal(f.ranking(), undefined);
    }
    const published = await calculationFixture(2); const done = (await published.drain()).result.ranking;
    published.db.store.get(E).status = status; const before = clone([...published.db.store]);
    const replay = await published.advance(); assert.equal(replay.replayed, true);
    assert.deepEqual(replay.ranking, done); assert.deepEqual([...published.db.store], before);
  }
});

for (const [players, rounds] of [[502, 1], [501, 3], [4096, 1], [4097, 1]]) test(`durable ${players} players / ${players * rounds} runs finishes across bounded invocations`, async () => {
  const started = performance.now(); const f = await calculationFixture(players, rounds); const commits = f.db.commits.length;
  const {result, calls} = await f.drain({pageSize: 50, maxPages: 1});
  assert.equal(result.ranking.verifiedRunCount, players * rounds); assert.equal(result.ranking.totalRanked, players);
  const expected = Array.from({length: players}, (_, i) => i).sort((a, b) => b % 9 - a % 9 || a - b).slice(0, 16).map(uidAt);
  assert.deepEqual(result.ranking.ranking.map(row => row.uid), expected);
  assert.equal(calls, Math.max(1, Math.ceil(players * rounds / 50)) + 2 * Math.ceil(players / 50) + 2);
  assert.ok(f.metrics.queries.every(q => q.maximum <= 51)); assert.ok(f.metrics.maxTransactionWrites <= 101);
  assert.ok(f.metrics.maxTransactionReads <= 655); assertSourcesUnchanged(f); assertCalculatorWritesOnly(f, commits);
  console.log(JSON.stringify({case: "durable-calculation", players, runs: players * rounds, calls,
    ms: Math.round(performance.now() - started), rssBytes: process.memoryUsage().rss,
    reads: f.metrics.reads, maxTransactionReads: f.metrics.maxTransactionReads, maxTransactionWrites: f.metrics.maxTransactionWrites,
    inputFingerprint: result.ranking.verifiedRunsFingerprint, rankingFingerprint: digest(result.ranking)}));
});
