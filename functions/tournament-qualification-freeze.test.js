"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {redact} = require("./question-redaction");
const {tournamentDigest: digest} = require("./tournament-bank");
const {applyTournamentOperation, qualificationSessionId, TOURNAMENT_PATHS: P} = require("./tournament-service");
const {VERIFIED_RUNS} = require("./tournament-session");
const {QUALIFICATION_RANKINGS: RANKINGS, fenceQualification, selectQualificationCalculation,
  freezeQualification, loadQualificationRanking, readQualificationEntryPage} = require("./tournament-qualification-freeze");
const {VERSIONS} = require("./tournament-qualification-compact");

const T = 1_800_000_000_000;
const EVENT = "event_0000000000000001";
const E = `${P.events}/${EVENT}`;
const attempt = uid => `attempt_${uid}_0000000001`;
const sid = uid => qualificationSessionId(attempt(uid));
const tests = [];
function test(name, fn) { tests.push({name, fn}); }
function fixture(players = ["alice", "bob"]) {
  const data = {};
  const forms = [1, 2, 3].map(roundNumber => {
    const formId = `qualification-form-${roundNumber}`;
    const keys = [];
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
    data[`${E}/forms/${digest(formId)}`] = {schemaVersion: 1, ...form};
    data[`${P.privateForms}/${EVENT}/forms/${digest(formId)}`] = {schemaVersion: 1, formId, fingerprint, keys};
    return form;
  });
  for (const uid of players) {
    const sessionId = sid(uid);
    data[`${P.sessions}/${sessionId}`] = {schemaVersion: 1, sessionId, uid, eventId: EVENT, kind: "QUALIFICATION", roundNumber: 1,
      formId: forms[0].formId, formFingerprint: forms[0].fingerprint, status: "STARTED", questionIndex: 0,
      startedAtMs: T, endsAtMs: T + 20_000, questionStartedAtMs: T, questionEndsAtMs: T + 1000};
    data[`${E}/entries/${digest([uid, 1])}`] = {eventId: EVENT, uid, roundNumber: 1, attemptId: attempt(uid), sessionId,
      formId: forms[0].formId, formFingerprint: forms[0].fingerprint, startedAtMs: T, status: "STARTED"};
  }
  data[E] = {schemaVersion: 1, eventId: EVENT, organizerUid: "organizer", status: "QUALIFICATION", lastMutationAtMs: T,
    source: {kind: "COURSE", id: "course", snapshotId: "immutable-v1"}, poolFingerprint: digest("pool"),
    qualification: {startsAtMs: T, endsAtMs: T + 20_000, roundCount: 3, questionsPerRound: 20, attemptsPerRound: 1}, qualificationForms: forms,
    prizes: {first: 0, second: 0, third: 0}, fundedBoxes: 0, escrow: {reservedBoxes: 0, remainingBoxes: 0, paidBoxes: 0, returnedBoxes: 0}};
  const db = premiumTestDb(data);
  const queries = [];
  const control = {onPage: null};
  // Outside-transaction paginated query double. Money/session transactions retain the original
  // strict fake; this addition asserts the actual server query's filters, limit and cursor.
  function query(path, filters = [], sort = null, after = null, maximum = null) {
    return {where: (field, op, value) => query(path, [...filters, {field, op, value}], sort, after, maximum),
      orderBy: field => query(path, filters, field, after, maximum), startAfter: cursor => query(path, filters, sort, cursor, maximum),
      limit: limit => query(path, filters, sort, after, limit), doc: id => db.doc(`${path}/${id}`),
      async get() {
        assert.equal(path, `${E}/entries`); assert.equal(sort, "__name__"); assert.ok(maximum >= 2 && maximum <= 101);
        assert.deepEqual(filters, []);
        queries.push({after, maximum});
        if (control.onPage) await control.onPage(queries.length);
        const rows = [...db.store].filter(([key]) => key.startsWith(`${path}/`) && key.split("/").length === 4 &&
          (after === null || key.split("/").at(-1) > after)).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).slice(0, maximum);
        return {docs: rows.map(([key, row]) => ({id: key.split("/").at(-1), data: () => structuredClone(row)}))};
      }};
  }
  db.collection = path => query(path);
  return {db, forms, queries, control, freeze: (extra = {}) => freezeQualification({db, eventId: EVENT, now: T + 20_000, pageSize: 2, maxPages: 5, ...extra})};
}

test("worker fences admissions, settles timed-out sessions and publishes only complete existing LS", async () => {
  const f = fixture(); const result = await f.freeze();
  assert.equal(result.complete, true); assert.equal(result.ranking.complete, true); assert.equal(result.ranking.verifiedRunCount, 2);
  assert.equal(result.ranking.totalRanked, 2); assert.deepEqual(result.ranking.ranking.map(row => row.uid), ["alice", "bob"]);
  assert.equal(f.db.store.get(E).lastMutationAtMs, T + 20_000);
  for (const uid of ["alice", "bob"]) {
    const run = f.db.store.get(`${VERIFIED_RUNS}/${sid(uid)}`);
    assert.equal(run.percentScore, 0); assert.equal(run.codeAnswer, "1".repeat(20)); assert.equal(run.completedAtMs, T + 20_000);
  }
});
test("fence closes even an in-flight admission using a captured pre-cutoff server time", async () => {
  const f = fixture(); await fenceQualification({db: f.db, eventId: EVENT, now: T + 20_000});
  await assert.rejects(applyTournamentOperation({db: f.db, uid: "late", now: T + 100,
    payload: {kind: "START_QUALIFICATION", ownerUid: "late", operationId: "late_admission_00000001", eventId: EVENT,
      roundNumber: 1, attemptId: "late_attempt_0000000001"}}), {code: "FAILED_PRECONDITION"});
  assert.equal([...f.db.store.keys()].filter(path => path.includes("/entries/")).length, 2);
});
test("truncated operational scan cannot publish complete ranking; larger budget safely retries", async () => {
  const f = fixture(["alice", "bob", "carol", "dave", "erin"]);
  const partial = await f.freeze({maxPages: 1});
  assert.equal(partial.complete, false); assert.equal(partial.reason, "SCAN_BUDGET"); assert.equal(partial.scannedSessions, 2);
  assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  const final = await f.freeze(); assert.equal(final.complete, true); assert.equal(final.ranking.verifiedRunCount, 5);
  assert.ok(f.queries.some(row => row.after !== null));
});
test("missing keys/pending server sessions never become zero-ranked entries or a complete freeze", async () => {
  const f = fixture(); const path = `${P.privateForms}/${EVENT}/forms/${digest(f.forms[0].formId)}`;
  const keys = f.db.store.get(path); f.db.store.delete(path);
  const incomplete = await f.freeze(); assert.equal(incomplete.complete, false); assert.equal(incomplete.pendingCount, 2);
  assert.equal(incomplete.reason, "PENDING_SERVER_SESSIONS"); assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  assert.equal([...f.db.store.keys()].filter(path => path.startsWith(`${VERIFIED_RUNS}/`)).length, 0);
  f.db.store.set(path, keys); assert.equal((await f.freeze()).complete, true);
});
test("empty terminal scan is genuinely complete with zero entrants", async () => {
  const f = fixture([]); const result = await f.freeze();
  assert.equal(result.complete, true); assert.equal(result.ranking.totalRanked, 0); assert.equal(result.ranking.scannedSessionCount, 0);
});
test("early freeze and invalid budgets fail before a fence write", async () => {
  const f = fixture(); const before = structuredClone([...f.db.store]);
  await assert.rejects(f.freeze({now: T + 19_999}), {code: "FAILED_PRECONDITION"});
  await assert.rejects(f.freeze({pageSize: 101}), {code: "INVALID_ARGUMENT"});
  await assert.rejects(f.freeze({maxPages: 0}), {code: "INVALID_ARGUMENT"}); assert.deepEqual([...f.db.store], before);
});
test("finished freeze replays its immutable snapshot without rescanning or rewriting", async () => {
  const f = fixture(); const first = await f.freeze(); const before = structuredClone([...f.db.store]); const count = f.queries.length;
  const second = await f.freeze({now: T + 50_000});
  assert.equal(second.replayed, true); assert.deepEqual(second.ranking, first.ranking); assert.equal(f.queries.length, count);
  assert.deepEqual([...f.db.store], before);
});
test("stored partial/corrupted ranking is never relabelled complete on replay", async () => {
  const f = fixture(); await f.freeze(); f.db.store.get(`${RANKINGS}/${EVENT}`).complete = false;
  await assert.rejects(f.freeze(), {code: "UNAVAILABLE"});
});
test("frozen result loader is bound to event forms and exact cutoff", async () => {
  const f = fixture(); await f.freeze();
  const load = (endsAtMs, forms) => f.db.runTransaction(transaction => loadQualificationRanking({db: f.db, transaction, eventId: EVENT, forms, endsAtMs}));
  assert.equal((await load(T + 20_000, f.forms)).complete, true);
  await assert.rejects(load(T + 20_001, f.forms), {code: "UNAVAILABLE"});
  await assert.rejects(load(T + 20_000, [{...f.forms[0], fingerprint: "changed"}, ...f.forms.slice(1)]), {code: "UNAVAILABLE"});
});
test("cross-form session and malformed query row leave freeze incomplete, not silently skipped", async () => {
  const f = fixture(); f.db.store.get(`${P.sessions}/${sid("alice")}`).formFingerprint = "wrong";
  const result = await f.freeze(); assert.equal(result.complete, false); assert.equal(result.pendingCount, 1);
  assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  const g = fixture(); delete g.db.store.get(`${E}/entries/${digest(["alice", 1])}`).sessionId;
  await assert.rejects(readQualificationEntryPage({db: g.db, eventId: EVENT, afterEntryId: null, pageSize: 2}), {code: "UNAVAILABLE"});
});

test("admitted attempt with missing session blocks complete freeze; an unadmitted session cannot rank", async () => {
  const f = fixture(); f.db.store.delete(`${P.sessions}/${sid("alice")}`);
  const result = await f.freeze(); assert.equal(result.complete, false); assert.equal(result.pendingCount, 1);
  assert.equal(result.pending[0].reason, "ADMITTED_SESSION_MISSING"); assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  const g = fixture(); g.db.store.delete(`${E}/entries/${digest(["alice", 1])}`);
  const lone = await g.freeze(); assert.equal(lone.complete, true); assert.equal(lone.ranking.scannedSessionCount, 1);
  assert.equal(lone.ranking.verifiedRunCount, 1);
});

test("page-boundary admission race retries against fence and cannot slip behind the scan cursor", async () => {
  const f = fixture(["alice", "bob", "carol"]); let attempted = false;
  f.control.onPage = async page => {
    if (page !== 2) return; attempted = true;
    await assert.rejects(applyTournamentOperation({db: f.db, uid: "late", now: T + 100,
      payload: {kind: "START_QUALIFICATION", ownerUid: "late", operationId: "race_admission_00000001", eventId: EVENT,
        roundNumber: 1, attemptId: "race_attempt_0000000001"}}), {code: "FAILED_PRECONDITION"});
  };
  const result = await f.freeze(); assert.equal(attempted, true); assert.equal(result.complete, true);
  assert.equal(result.ranking.verifiedRunCount, 3); assert.equal([...f.db.store.keys()].filter(path => path.includes("/entries/")).length, 3);
});

test("explicit version selection rotates only unpublished fence and preserves historical scan and escrow", async () => {
  const f = fixture(); const legacy = await fenceQualification({db: f.db, eventId: EVENT, now: T + 20_000});
  const historicalPath = `${E}/qualification_scans/${legacy.fence.freezeId}`;
  f.db.store.set(historicalPath, {historical: "preserved"});
  const before = structuredClone([...f.db.store]);
  const first = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 20_000});
  assert.notEqual(first.contract.freezeId, legacy.fence.freezeId); assert.deepEqual(first.contract.versions, VERSIONS);
  for (const [path, value] of before) if (path !== E) assert.deepEqual(f.db.store.get(path), value, path);
  assert.deepEqual(first.event.escrow, legacy.event.escrow); assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  const snapshot = structuredClone([...f.db.store]);
  const again = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 99_000});
  assert.deepEqual(again, first); assert.deepEqual([...f.db.store], snapshot);
  const fenced = await fenceQualification({db: f.db, eventId: EVENT, now: T + 30_000});
  assert.equal(fenced.fence.freezeId, first.contract.freezeId); assert.deepEqual([...f.db.store], snapshot);
  const queries = f.queries.length;
  await assert.rejects(f.freeze(), {code: "UNAVAILABLE"}); assert.equal(f.queries.length, queries);
});
test("legacy publication wins before selection and is returned byte-for-byte without rebinding", async () => {
  const f = fixture(); const published = await f.freeze(); const before = structuredClone([...f.db.store]);
  const selected = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 99_000});
  assert.equal(selected.contract, null); assert.deepEqual(selected.existingRanking, published.ranking);
  assert.deepEqual([...f.db.store], before); assert.equal(f.db.store.get(E).qualificationCalculation, undefined);
});
test("selection during an old freeze scan prevents its delayed legacy publication", async () => {
  const f = fixture(["alice", "bob", "carol"]); let chosen;
  f.control.onPage = async page => { if (page === 2) chosen = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 20_000}); };
  await assert.rejects(f.freeze(), {code: "UNAVAILABLE"});
  assert.ok(chosen); assert.equal(f.db.store.has(`${RANKINGS}/${EVENT}`), false);
  assert.equal(f.db.store.get(E).qualificationFreezeFence.freezeId, chosen.contract.freezeId);
  assert.deepEqual(f.db.store.get(E).escrow, {reservedBoxes: 0, remainingBoxes: 0, paidBoxes: 0, returnedBoxes: 0});
});
test("version selection commit failure rolls back the fence and contract together", async () => {
  const f = fixture(); await fenceQualification({db: f.db, eventId: EVENT, now: T + 20_000});
  const before = structuredClone([...f.db.store]); f.db.failNextCommit();
  await assert.rejects(selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 20_000}));
  assert.deepEqual([...f.db.store], before);
  await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 20_000});
  assert.ok(f.db.store.get(E).qualificationCalculation);
});
test("versioned published result replays unchanged and corrupted version provenance is rejected", async () => {
  const f = fixture(); const selected = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 20_000});
  const c = selected.contract; const stored = {schemaVersion: 2, complete: true, eventId: EVENT, endsAtMs: c.endsAtMs,
    snapshotId: c.freezeId, freezeFenceId: c.freezeId, formsFingerprint: c.formsFingerprint,
    algorithmVersion: VERSIONS.algorithmFamily, calculationVersions: {...VERSIONS}, calculationContractDigest: c.contractDigest,
    inputFingerprintVersion: "player-stream-chain-v1", verifiedRunsFingerprint: digest("complete-verified-player-stream"),
    verifiedRunCount: 2, totalRanked: 2, ranking: [{uid: "alice", place: 1}, {uid: "bob", place: 2}]};
  f.db.store.set(`${RANKINGS}/${EVENT}`, stored); const before = structuredClone([...f.db.store]);
  assert.deepEqual((await f.freeze()).ranking, stored);
  assert.deepEqual((await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 90_000})).existingRanking, stored);
  assert.deepEqual([...f.db.store], before);
  f.db.store.get(`${RANKINGS}/${EVENT}`).calculationVersions.roundingVersion = "wrong";
  await assert.rejects(f.freeze(), {code: "UNAVAILABLE"});
});

(async () => { for (const {name, fn} of tests) { await fn(); console.log(`ok - ${name}`); } console.log(`tournament-qualification-freeze: ${tests.length} behavioral tests passed`); })()
  .catch(error => { console.error(error); process.exitCode = 1; });
