"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {redact} = require("./question-redaction");
const {tournamentDigest: digest} = require("./tournament-bank");
const {TOURNAMENT_PATHS: P, qualificationSessionId, applyTournamentOperation} = require("./tournament-service");
const {VERIFIED_RUNS} = require("./tournament-session");
const {QUALIFICATION_RANKINGS: RANKINGS, freezeQualification} = require("./tournament-qualification-freeze");
const {buildQualificationRankingSnapshot} = require("./tournament-qualification");
const {advanceQualificationScan, readCompleteQualificationManifest, calculateAndPublishQualificationRanking, scanPath} = require("./tournament-qualification-scan");

const T = 1_800_000_000_000; const END = T + 20_000;
const EVENT = "event_scan_000000000001"; const E = `${P.events}/${EVENT}`; const R = `${RANKINGS}/${EVENT}`;
const tests = []; const test = (name, fn) => tests.push({name, fn});
const uidAt = n => `player_${String(n).padStart(4, "0")}`;
const attempt = (uid, round) => `attempt_${uid}_${round}_0000000001`;
const sid = (uid, round) => qualificationSessionId(attempt(uid, round));
const cmp = (a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b));

function fixture(playerCount = 2, roundCount = 1) {
  const initial = {};
  const forms = [1, 2, 3].map(roundNumber => {
    const formId = `qualification-form-${roundNumber}`; const keys = [];
    const questions = Array.from({length: 20}, (_, i) => {
      const questionId = `q-${roundNumber}-${i}`;
      const pair = redact(JSON.stringify({id: questionId, type: "SingleChoice", difficulty: "EASY", text: "Existing question",
        options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"}), "EASY", {questionId});
      keys.push({questionId, contentVersion: 1, answerKey: pair.key});
      const publicPayload = JSON.parse(pair.publicPayload);
      return {questionId, type: "SingleChoice", difficulty: "EASY", contentVersion: 1, contentHash: digest(publicPayload), maxDurationMs: 1000, publicPayload};
    });
    const fingerprint = digest(questions.map(({publicPayload, ...meta}) => meta));
    const form = {formId, fingerprint, difficulty: "EASY", maxDurationMs: 20_000, questions};
    initial[`${E}/forms/${digest(formId)}`] = {schemaVersion: 1, ...form};
    initial[`${P.privateForms}/${EVENT}/forms/${digest(formId)}`] = {schemaVersion: 1, formId, fingerprint, keys};
    return form;
  });
  for (let n = 0; n < playerCount; n++) for (let round = 1; round <= roundCount; round++) {
    const uid = uidAt(n); const sessionId = sid(uid, round); const form = forms[round - 1];
    initial[`${P.sessions}/${sessionId}`] = {schemaVersion: 1, sessionId, uid, eventId: EVENT, kind: "QUALIFICATION", roundNumber: round,
      formId: form.formId, formFingerprint: form.fingerprint, status: "STARTED", questionIndex: 0,
      startedAtMs: T, endsAtMs: END, questionStartedAtMs: T, questionEndsAtMs: T + 1000};
    initial[`${E}/entries/${digest([uid, round])}`] = {eventId: EVENT, uid, roundNumber: round, attemptId: attempt(uid, round), sessionId,
      formId: form.formId, formFingerprint: form.fingerprint, startedAtMs: T, status: "STARTED"};
  }
  initial[E] = {schemaVersion: 1, eventId: EVENT, organizerUid: "organizer", status: "QUALIFICATION", lastMutationAtMs: T,
    source: {kind: "COURSE", id: "course", snapshotId: "immutable-v1"}, poolFingerprint: digest("pool"), privateBankFingerprint: digest("private"),
    timingVersion: "test-timing-v1", qualification: {startsAtMs: T, endsAtMs: END, roundCount: 3, questionsPerRound: 20, attemptsPerRound: 1}, qualificationForms: forms,
    prizes: {first: 0, second: 0, third: 0}, fundedBoxes: 0, escrow: {reservedBoxes: 0, remainingBoxes: 0, paidBoxes: 0, returnedBoxes: 0}};
  const db = premiumTestDb(initial); const originalRun = db.runTransaction; const originalDoc = db.doc;
  const controls = {now: END, beforeQuery: null, beforeCommit: null, afterDocGet: null, repeatReadCallbacks: false};
  const queries = []; let serial = Promise.resolve();
  db.doc = path => { const ref = originalDoc(path); return {...ref, get: async () => {
    const result = await ref.get(); if (controls.afterDocGet) await controls.afterDocGet(path); return result;
  }}; };
  function query(path, filters = [], sort = null, after = null, maximum = null) {
    return {scanQuery: true, path, filters, sort, after, maximum,
      where: (field, op, value) => query(path, [...filters, {field, op, value}], sort, after, maximum),
      orderBy: field => query(path, filters, field, after, maximum), startAfter: cursor => query(path, filters, sort, cursor, maximum),
      limit: limit => query(path, filters, sort, after, limit), doc: id => db.doc(`${path}/${id}`),
      get: () => runQuery({path, filters, sort, after, maximum})};
  }
  async function runQuery(q) {
    assert.equal(q.sort, "__name__"); assert.ok(Number.isInteger(q.maximum) && q.maximum >= 1 && q.maximum <= 101);
    assert.ok(q.path === `${E}/entries` || q.path.endsWith("/rows"));
    assert.deepEqual(q.filters, q.path.endsWith("/rows") ? [{field: "status", op: "==", value: "PENDING"}] : []);
    queries.push({...q}); if (controls.beforeQuery) await controls.beforeQuery(q);
    const depth = q.path.split("/").length + 1;
    const rows = [...db.store].filter(([path, row]) => path.startsWith(`${q.path}/`) && path.split("/").length === depth &&
      (q.after === null || cmp(path.split("/").at(-1), q.after) > 0) && q.filters.every(f => row[f.field] === f.value))
      .sort(([a], [b]) => cmp(a, b)).slice(0, q.maximum);
    return {docs: rows.map(([path, row]) => ({id: path.split("/").at(-1), exists: true, data: () => structuredClone(row)}))};
  }
  db.collection = path => query(path);
  // Serialize shared-state transactions like the real pessimistic SDK. Preserve atomic commit
  // rollback/read-before-write assertions; this does not claim a production contention benchmark.
  db.runTransaction = body => {
    const run = () => originalRun(async original => {
      const writes = []; let wrote = false;
      const tx = {...original,
        getAll: async (...refs) => { assert.equal(wrote, false); return original.getAll(...refs); },
        get: async ref => { assert.equal(wrote, false); return ref.scanQuery ? runQuery(ref) : original.get(ref); },
        create: (ref, value) => { wrote = true; writes.push(ref.path); return original.create(ref, value); },
        set: (ref, value, opts) => { wrote = true; writes.push(ref.path); return original.set(ref, value, opts); },
      };
      let result = await body(tx);
      if (!wrote && controls.repeatReadCallbacks) result = await body(tx);
      if (controls.beforeCommit) await controls.beforeCommit(writes);
      return result;
    });
    const result = serial.then(run, run); serial = result.catch(() => {}); return result;
  };
  const context = () => scanPath(EVENT, db.store.get(E).qualificationFreezeFence.freezeId);
  const head = () => db.store.get(context());
  const rows = () => [...db.store].filter(([path]) => path.startsWith(`${context()}/rows/`));
  const advance = extra => advanceQualificationScan({db, eventId: EVENT, now: END, pageSize: 2, maxPages: 1, nowMs: () => controls.now, ...extra});
  const calculate = () => calculateAndPublishQualificationRanking({db, eventId: EVENT, now: END});
  async function drain(extra) {
    for (let i = 0; i < 100; i++) { const result = await advance(extra); if (result.scanComplete) return result; }
    throw Error("Scan did not finish within fixture bound");
  }
  return {db, forms, controls, queries, context, head, rows, advance, calculate, drain};
}

test("501 admitted sessions persist progress across calls and rank the full cohort with unchanged LS only afterward", async () => {
  const f = fixture(167, 3); let calls = 0; let lastCount = 0; let result;
  do {
    result = await f.advance({pageSize: 50}); calls++;
    assert.equal(f.db.store.has(R), false, "No partial ranking during scan");
    assert.ok(result.admittedCount >= lastCount); lastCount = result.admittedCount;
    assert.ok(result.discoveredPages <= 1 && result.repairedAttempts <= 50); assert.ok(calls <= 50);
  } while (!result.scanComplete);
  assert.ok(calls > 10); assert.equal(result.admittedCount, 501); assert.equal(result.verifiedCount, 501); assert.equal(result.pendingCount, 0);
  assert.equal(result.complete, false); assert.equal(result.status, "RANKING_PENDING");
  const entryQueries = f.queries.filter(q => q.path === `${E}/entries`);
  assert.equal(entryQueries.length, 11); assert.ok(entryQueries.slice(1).every(q => q.after !== null));
  assert.equal(f.rows().length, 501);
  const allRuns = [...f.db.store].filter(([path]) => path.startsWith(`${VERIFIED_RUNS}/`)).map(([, run]) => run);
  const expected = buildQualificationRankingSnapshot({eventId: EVENT, forms: f.forms, endsAtMs: END, snapshotId: f.head().freezeId,
    verifiedRuns: allRuns, completeThroughMs: END, pendingSessionCount: 0});
  const final = await f.calculate(); assert.equal(final.complete, true); assert.equal(final.ranking.verifiedRunCount, 501);
  assert.equal(final.ranking.totalRanked, 167); assert.deepEqual(final.ranking.ranking, expected.ranking);
  assert.equal(final.ranking.verifiedRunsFingerprint, expected.verifiedRunsFingerprint);
  assert.equal(final.ranking.scanManifestDigest, result.manifestDigest); assert.equal(f.head().status, "PUBLISHED");
});
test("scan-complete is pending calculation; repeated scan never rewrites immutable rows or chunks", async () => {
  const f = fixture(); await f.drain(); const before = structuredClone([...f.db.store].filter(([path]) => path.startsWith(f.context())));
  const repeated = await f.advance(); assert.equal(repeated.status, "RANKING_PENDING"); assert.equal(f.db.store.has(R), false);
  assert.deepEqual([...f.db.store].filter(([path]) => path.startsWith(f.context())), before);
});
test("discovery chunk plus admitted rows and cursor roll back atomically on failed commit", async () => {
  const f = fixture(3); let failed = false;
  f.controls.beforeCommit = writes => { if (!failed && writes.some(path => path.includes("/chunks/"))) { failed = true; f.db.failNextCommit(); } };
  await assert.rejects(f.advance()); assert.equal(f.head().admittedCount, 0); assert.equal(f.head().afterEntryId, null); assert.equal(f.rows().length, 0);
  await f.drain(); assert.equal(f.head().admittedCount, 3); assert.equal(f.rows().length, 3);
});
test("crash after discovery before repair resumes without dropping or duplicating admitted IDs", async () => {
  const f = fixture(5); let failed = false;
  f.controls.beforeQuery = q => { if (!failed && q.path.endsWith("/rows")) { failed = true; throw Error("synthetic repair query failure"); } };
  await assert.rejects(f.advance()); assert.equal(f.head().admittedCount, 2); assert.equal(f.head().pendingCount, 2); assert.equal(f.head().verifiedCount, 0);
  await f.drain(); assert.equal(f.head().admittedCount, 5); assert.equal(f.head().verifiedCount, 5); assert.equal(f.rows().length, 5);
});
test("concurrent scanners and repeated repair cannot count one admitted ID twice", async () => {
  const f = fixture(8); await Promise.all([f.advance(), f.advance()]); await f.drain();
  assert.equal(f.head().admittedCount, 8); assert.equal(f.head().verifiedCount, 8); assert.equal(f.head().pendingCount, 0);
  assert.equal(new Set(f.rows().map(([, row]) => row.entryId)).size, 8);
  await f.calculate(); assert.equal(f.db.store.get(R).verifiedRunCount, 8);
});
test("missing keys stay explicit pending across terminal discovery and repair after restoration", async () => {
  const f = fixture(5); const path = `${P.privateForms}/${EVENT}/forms/${digest(f.forms[0].formId)}`;
  const saved = f.db.store.get(path); f.db.store.delete(path);
  for (let i = 0; i < 5; i++) await f.advance();
  assert.equal(f.head().discoveryDone, true); assert.equal(f.head().pendingCount, 5); assert.equal(f.head().verifiedCount, 0);
  assert.equal((await f.calculate()).complete, false); assert.equal(f.db.store.has(R), false);
  assert.ok(f.rows().every(([, row]) => row.status === "PENDING" && !row.run));
  const chunks = structuredClone([...f.db.store].filter(([p]) => p.includes("/chunks/")));
  f.db.store.set(path, saved); await f.drain();
  assert.deepEqual([...f.db.store].filter(([p]) => p.includes("/chunks/")), chunks);
  assert.equal((await f.calculate()).ranking.verifiedRunCount, 5);
});
test("failed verified-row commit preserves pending counters although session settlement already happened", async () => {
  const f = fixture(); let failed = false;
  f.controls.beforeCommit = writes => { if (!failed && writes.some(path => path.includes("/rows/")) && !writes.some(path => path.includes("/chunks/"))) {
    failed = true; f.db.failNextCommit();
  }};
  await assert.rejects(f.advance()); assert.equal(f.head().pendingCount, 2); assert.equal(f.head().verifiedCount, 0);
  assert.ok([...f.db.store.keys()].some(path => path.startsWith(`${VERIFIED_RUNS}/`)));
  await f.drain(); assert.equal(f.head().verifiedCount, 2); assert.equal(f.rows().length, 2);
});
test("late admission captured before cutoff is still rejected after durable scan fence", async () => {
  const f = fixture(); await f.advance();
  await assert.rejects(applyTournamentOperation({db: f.db, uid: "late", now: T + 100, payload: {kind: "START_QUALIFICATION",
    ownerUid: "late", operationId: "late_admission_00000001", eventId: EVENT, roundNumber: 1, attemptId: "late_attempt_0000000001"}}), {code: "FAILED_PRECONDITION"});
  assert.equal([...f.db.store.keys()].filter(path => path.startsWith(`${E}/entries/`)).length, 2);
});
test("missing admitted row cannot be hidden by correct-looking totals", async () => {
  const f = fixture(); await f.drain(); f.db.store.delete(f.rows()[0][0]);
  await assert.rejects(f.calculate(), {code: "UNAVAILABLE"}); assert.equal(f.db.store.has(R), false);
});
test("repeated manifest identity cannot replace a different missing entrant even when counts match", async () => {
  const f = fixture(); await f.drain();
  const chunkPath = `${f.context()}/chunks/0000000000000000`; const chunk = f.db.store.get(chunkPath);
  chunk.entries = [chunk.entries[0], chunk.entries[0]]; chunk.lastEntryId = chunk.entries[0].entryId;
  const {chunkHash, previousChainHash, ...body} = chunk; chunk.chunkHash = digest(body);
  const head = f.head(); head.chainHash = digest([previousChainHash, chunk.chunkHash]); head.afterEntryId = chunk.lastEntryId;
  head.manifestDigest = digest({identityHash: head.identityHash, chainHash: head.chainHash, chunkCount: head.chunkCount,
    admittedCount: head.admittedCount, verifiedCount: head.verifiedCount});
  await assert.rejects(f.calculate(), {code: "UNAVAILABLE"}); assert.equal(f.db.store.has(R), false);
});
test("source identity change refuses continuing an existing scan", async () => {
  const f = fixture(4); await f.advance(); f.db.store.get(E).source.snapshotId = "changed-after-fence";
  await assert.rejects(f.advance(), {code: "UNAVAILABLE"}); assert.equal(f.db.store.has(R), false);
});
test("changed frozen admission stays a repair gap and never becomes an invented zero", async () => {
  const f = fixture(); f.controls.beforeQuery = q => { if (q.path.endsWith("/rows")) throw Error("pause before repair"); };
  await assert.rejects(f.advance()); f.controls.beforeQuery = null;
  const first = f.rows()[0][1]; f.db.store.get(`${E}/entries/${first.entryId}`).attemptId = "changed_attempt_00000001";
  for (let i = 0; i < 3; i++) await f.advance();
  assert.equal(f.head().pendingCount, 1); assert.equal(f.head().verifiedCount, 1); assert.equal(f.db.store.has(R), false);
});
test("trusted session/verified result mutation after manifest completion cannot alter published ranking", async () => {
  const f = fixture(); await f.drain(); const row = f.rows()[0][1];
  f.db.store.get(`${VERIFIED_RUNS}/${row.binding.sessionId}`).percentScore = 100;
  await assert.rejects(f.calculate(), {code: "UNAVAILABLE"}); assert.equal(f.db.store.has(R), false);
});
test("corrupt pending binding cannot advance a foreign session before the admission proof check", async () => {
  const f = fixture(); f.controls.beforeQuery = q => { if (q.path.endsWith("/rows")) throw Error("pause before repair"); };
  await assert.rejects(f.advance()); f.controls.beforeQuery = null;
  const row = f.rows()[0][1]; const foreignEvent = "foreign_event_00000001"; const foreignUid = "foreign";
  const foreignSessionId = sid(foreignUid, 1);
  const foreignSession = {...structuredClone(f.db.store.get(`${P.sessions}/${row.binding.sessionId}`)),
    sessionId: foreignSessionId, eventId: foreignEvent, uid: foreignUid};
  f.db.store.set(`${P.sessions}/${foreignSessionId}`, foreignSession);
  f.db.store.set(`${P.events}/${foreignEvent}/forms/${digest(f.forms[0].formId)}`, structuredClone(f.db.store.get(`${E}/forms/${digest(f.forms[0].formId)}`)));
  f.db.store.set(`${P.privateForms}/${foreignEvent}/forms/${digest(f.forms[0].formId)}`, structuredClone(f.db.store.get(`${P.privateForms}/${EVENT}/forms/${digest(f.forms[0].formId)}`)));
  row.binding = {...row.binding, eventId: foreignEvent, uid: foreignUid, sessionId: foreignSessionId};
  await f.advance(); assert.equal(f.db.store.get(`${P.sessions}/${foreignSessionId}`).status, "STARTED");
  assert.equal(f.db.store.has(`${VERIFIED_RUNS}/${foreignSessionId}`), false); assert.equal(f.head().pendingCount, 1);
});
test("publication receipt and PUBLISHED marker roll back together and replay the same full ranking", async () => {
  const f = fixture(); await f.drain(); let failed = false;
  f.controls.beforeCommit = writes => { if (!failed && writes.includes(R)) { failed = true; f.db.failNextCommit(); } };
  await assert.rejects(f.calculate()); assert.equal(f.db.store.has(R), false); assert.equal(f.head().status, "SCAN_COMPLETE");
  const first = await f.calculate(); const stored = structuredClone(f.db.store.get(R));
  const again = await f.calculate(); assert.equal(again.replayed, true); assert.deepEqual(first.ranking, again.ranking); assert.deepEqual(f.db.store.get(R), stored);
});
test("retrying read-only manifest transactions does not duplicate accumulated cohort rows", async () => {
  const f = fixture(7, 3); await f.drain(); f.controls.repeatReadCallbacks = true;
  const manifest = await readCompleteQualificationManifest({db: f.db, eventId: EVENT, now: END});
  assert.equal(manifest.verifiedRuns.length, 21); assert.equal((await f.calculate()).ranking.verifiedRunCount, 21);
});
test("empty terminal cohort can publish the existing zero-entrant ranking for normal cancellation", async () => {
  const f = fixture(0); const scan = await f.advance(); assert.equal(scan.scanComplete, true); assert.equal(scan.admittedCount, 0);
  const result = await f.calculate(); assert.equal(result.ranking.totalRanked, 0); assert.equal(result.ranking.verifiedRunCount, 0);
});
test("already published legacy ranking replays without inventing a new scan proof", async () => {
  const f = fixture(); const old = await freezeQualification({db: f.db, eventId: EVENT, now: END, pageSize: 2, maxPages: 5});
  assert.equal(old.complete, true); const before = structuredClone(f.db.store.get(R));
  assert.equal((await f.advance()).complete, true); assert.deepEqual((await f.calculate()).ranking, before);
  assert.equal([...f.db.store.keys()].filter(path => path.includes("/qualification_scans/")).length, 0);
});
test("deadline consumed during repair-row read starts no session work and leaves the row pending", async () => {
  const f = fixture(); f.controls.afterDocGet = path => { if (path.includes("/rows/")) f.controls.now = END + 10; };
  const result = await f.advance({deadlineAtMs: END + 10}); assert.equal(result.repairedAttempts, 0);
  assert.equal(f.head().pendingCount, 2); assert.equal(f.head().repairAfterRowId, null);
  assert.equal([...f.db.store.keys()].filter(path => path.startsWith(`${VERIFIED_RUNS}/`)).length, 0);
});

test("trusted calculation resource limit returns pending before loading any manifest chunks", async () => {
  const f = fixture(3); await f.drain(); const before = structuredClone([...f.db.store]);
  const run = f.db.runTransaction; let chunksRead = 0;
  f.db.runTransaction = body => run(tx => body({...tx, getAll: async (...refs) => {
    chunksRead += refs.filter(ref => ref.path.includes("/chunks/")).length; return tx.getAll(...refs);
  }}));
  const result = await calculateAndPublishQualificationRanking({db: f.db, eventId: EVENT, now: END, maxVerifiedRuns: 2});
  assert.deepEqual(result, {complete: false, status: "CALCULATION_RESOURCE_PENDING"});
  assert.equal(chunksRead, 0); assert.deepEqual([...f.db.store], before);
});
test("deadline and minimum pre-solve margin are exact, with no publication on an insufficient window", async () => {
  const f = fixture(2); await f.drain(); const options = {db: f.db, eventId: EVENT, now: END, maxVerifiedRuns: 2,
    nowMs: () => f.controls.now, minRemainingBeforeSolveMs: 60};
  const before = structuredClone([...f.db.store]);
  assert.equal((await calculateAndPublishQualificationRanking({...options, deadlineAtMs: END})).status, "CALCULATION_TIME_PENDING");
  assert.equal((await calculateAndPublishQualificationRanking({...options, deadlineAtMs: END + 59})).status, "CALCULATION_TIME_PENDING");
  assert.deepEqual([...f.db.store], before);
  assert.equal((await calculateAndPublishQualificationRanking({...options, deadlineAtMs: END + 60})).complete, true);
});
test("elapsed time during awaited chunk reads stops before any solve or publication", async () => {
  const f = fixture(5); await f.drain(); const before = structuredClone([...f.db.store]); const run = f.db.runTransaction;
  f.db.runTransaction = body => run(tx => body({...tx, getAll: async (...refs) => {
    const result = await tx.getAll(...refs); if (refs.some(ref => ref.path.includes("/chunks/"))) f.controls.now = END + 100; return result;
  }}));
  const result = await calculateAndPublishQualificationRanking({db: f.db, eventId: EVENT, now: END,
    nowMs: () => f.controls.now, deadlineAtMs: END + 100});
  assert.equal(result.status, "CALCULATION_TIME_PENDING"); assert.deepEqual([...f.db.store], before);
});
test("elapsed time during publication reads leaves the complete manifest but no ranking; later retry succeeds", async () => {
  const f = fixture(2); await f.drain(); const before = structuredClone([...f.db.store]); const run = f.db.runTransaction;
  f.db.runTransaction = body => run(tx => body({...tx, getAll: async (...refs) => {
    const result = await tx.getAll(...refs);
    if (refs.some(ref => ref.path === R) && refs.some(ref => ref.path === f.context())) f.controls.now = END + 100;
    return result;
  }}));
  const result = await calculateAndPublishQualificationRanking({db: f.db, eventId: EVENT, now: END,
    nowMs: () => f.controls.now, deadlineAtMs: END + 100});
  assert.equal(result.status, "CALCULATION_TIME_PENDING"); assert.deepEqual([...f.db.store], before);
  f.db.runTransaction = run; assert.equal((await f.calculate()).complete, true);
});

(async () => {
  for (const {name, fn} of tests) { try { await fn(); } catch (error) { console.error(`FAIL ${name}`); throw error; } }
  console.log(`tournament-qualification-scan: ${tests.length} behavioral cases passed`);
})().catch(error => { console.error(error); process.exitCode = 1; });
