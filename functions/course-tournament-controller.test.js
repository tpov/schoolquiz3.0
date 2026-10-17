"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {createCourseTournamentController, WORKER_PREFIX, QUALIFICATION_EXECUTION} = require("./course-tournament-controller");
const {TOURNAMENT_PATHS: P, matchSessionId} = require("./tournament-service");
const {SESSION_OPERATIONS, SESSION_INGRESS, VERIFIED_RUNS} = require("./tournament-session");
const {QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {tournamentDigest: digest} = require("./tournament-bank");
const {READY_WINDOW_MS} = require("./tournament-policy");

const T = 1_800_000_000_000; const DAY = 86400000; const OWNER = "author"; const EVENT = "event_000000000000001";
const E = `${P.events}/${EVENT}`; const tests = [];
function test(name, body) { tests.push({name, body}); }
const clone = structuredClone;
function pagedDb(initial) {
  const db = premiumTestDb(initial); const collection = db.collection; const run = db.runTransaction;
  function snapshot(ref) {
    assert.equal(ref.sort, "__name__"); assert.ok(Number.isInteger(ref.maximum) && ref.maximum >= 1 && ref.maximum <= 101);
    const rows = [...db.store].filter(([key, row]) => key.startsWith(`${ref.path}/`) && key.split("/").length === ref.path.split("/").length + 1 &&
      ref.filters.every(({field, op, value}) => op === "==" ? row[field] === value : op === "in" ? Array.isArray(value) && value.includes(row[field]) : false) && (ref.after === null || key.split("/").at(-1) > ref.after))
      .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).slice(0, ref.maximum);
    return {docs: rows.map(([key, row]) => ({id: key.split("/").at(-1), exists: true, data: () => clone(row)}))};
  }
  function extend(ref, after = null) {
    return {...ref, after, where: (...args) => extend(ref.where(...args), after), orderBy: (...args) => extend(ref.orderBy(...args), after),
      limit: (...args) => extend(ref.limit(...args), after), startAfter: (cursor) => extend(ref, cursor), get: async () => snapshot({...ref, after})};
  }
  db.collection = (...args) => extend(collection(...args));
  db.runTransaction = (body) => run(async tx => {
    let written = false;
    return body({...tx,
      get: async ref => { assert.equal(written, false, "read after write"); return ref.sort === "__name__" ? snapshot(ref) : tx.get(ref); },
      getAll: async (...refs) => { assert.equal(written, false, "read after write"); return tx.getAll(...refs); },
      create: (...args) => { written = true; return tx.create(...args); }, set: (...args) => { written = true; return tx.set(...args); },
    });
  });
  return db;
}
function fixture(players = ["alice", "bob"], options = {}) {
  const wallet = () => ({boxCount: 0, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
    lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T});
  // Story 5.3: every player holds a server-attested download record for the fixture course so
  // qualification STARTs keep working; the admission tests below delete records explicitly.
  const downloads = Object.fromEntries(players.map(uid =>
    [`course_tournament_downloads/${digest([uid, "course"])}`, {schemaVersion: 1, uid, courseId: "course", downloadedAtMs: T}]));
  const db = pagedDb({"catalogs/catalog": {questType: "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: OWNER, version: 1, visibleOn: ["archive"], archived: true},
    "sections/section": {questId: "course", version: 1}, "themes/theme": {sectionId: "section", version: 1},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: T - 1},
    [`users/${OWNER}`]: {...wallet(), boxCount: 100}, ...Object.fromEntries(players.map(uid => [`users/${uid}`, wallet()])), ...downloads});
  for (let i = 0; i < 80; i++) {
    const questionId = `q${String(i).padStart(3, "0")}`;
    db.store.set(`questions/${questionId}`, {lessonId: "lesson", version: 1, payload: JSON.stringify({id: questionId,
      type: "SingleChoice", difficulty: i < 60 ? "EASY" : "HARD", text: "A?", options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"})});
  }
  const state = {now: T}; let serial = 0;
  const restart = () => createCourseTournamentController({db, nowMs: () => state.now,
    isCurator: async () => false, workerBounds: options.workerBounds,
    loadLessonContext: async (tx, lessonId) => {
      const lesson = (await tx.get(db.doc(`lessons/${lessonId}`))).data();
      return {lessonId, authorUid: OWNER, catalogId: "catalog", questId: "course", sectionId: "section", themeId: "theme",
        questType: "COURSE", published: true, reviewApproved: lesson?.reviewSubmissionId === "submission", contentVersion: lesson?.version};
    }});
  const controller = restart();
  const id = () => `client_operation_${String(++serial).padStart(16, "0")}`;
  const operate = (kind, uid = OWNER, fields = {}) => controller.operate(uid, {kind, operationId: id(), eventId: EVENT, ...fields});
  const prepare = () => controller.prepare(OWNER, {requestId: "preparation_request_00001", source: {kind: "COURSE", id: "course"}});
  async function create() {
    const p = await prepare();
    // Story 5.4: CREATE carries the adopted v1 prize list ({5,3,1} → two place-3 rows, liability 10).
    return operate("CREATE", OWNER, {source: {kind: "COURSE", id: "course", snapshotId: p.snapshotId}, startsAtMs: state.now,
      fundedBoxes: 12, prizes: {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
        {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]}});
  }
  async function qualify() {
    await create();
    for (const uid of players) await operate("START_QUALIFICATION", uid, {roundNumber: 1, attemptId: `attempt_${uid}_00000000001`});
    state.now = T + 7 * DAY;
    return controller.reconcileEvent(EVENT);
  }
  function pending() { return db.store.get(E).progress.pending[0]; }
  function match() { return db.store.get(E).bracket.rounds.flatMap(row => row.matches).find(row => row.matchId === pending().matchId); }
  async function agree() {
    const m = match(); const pair = pending().entrants.map(row => row.uid); state.now = m.stageStartsAtMs + 1000;
    const slotStartsAtMs = state.now + 4000;
    await operate("PROPOSE", pair[0], {matchId: m.matchId, slotStartsAtMs});
    for (const uid of pair) await operate("CONFIRM", uid, {matchId: m.matchId, slotStartsAtMs});
    return {match: m, pair, slotStartsAtMs};
  }
  async function readyBoth() {
    const agreed = await agree(); state.now = agreed.slotStartsAtMs;
    await operate("READY", agreed.pair[0], {matchId: agreed.match.matchId}); state.now++;
    await operate("READY", agreed.pair[1], {matchId: agreed.match.matchId});
    return {...agreed, sessions: agreed.pair.map(uid => matchSessionId(EVENT, agreed.match.matchId, uid))};
  }
  return {db, state, controller, restart, id, operate, prepare, create, qualify, pending, match, agree, readyBoth};
}
function balance(f, uid) { return f.db.store.get(`users/${uid}`).boxCount; }
function assertSafe(value) {
  const denied = new Set(["answerKey", "keys", "correctOptionId", "correctOptionIds", "correctCandidateId", "blankToCandidate", "idMap",
    "formFingerprint", "fingerprint", "contentHash", "privateBankFingerprint", "poolFingerprint", "bankHash", "proofHash", "bankJson", "proofJson", "serverSeed", "codeAnswer", "scoreDigits", "scoreDigit"]);
  function walk(row) { if (!row || typeof row !== "object") return; for (const [key, item] of Object.entries(row)) { assert.ok(!denied.has(key), `private field ${key}`); walk(item); } }
  walk(value);
}
const error = (fn, code) => assert.rejects(fn, e => { assert.equal(e.code, code); return true; });
function workerReceipts(f) { return [...f.db.store].filter(([key, row]) => (key.startsWith(P.operations) || key.startsWith(SESSION_OPERATIONS)) && row.operationId.startsWith(WORKER_PREFIX)); }

test("public runtime derives owner and returns safe create/event views", async () => {
  const f = fixture(); const prepared = await f.prepare(); assert.deepEqual(Object.keys(prepared).sort(), ["counts", "snapshotId", "timingVersion"]);
  assertSafe(await f.create()); const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.equal(view.event.organizerUid, OWNER); assertSafe(view); assert.equal(balance(f, OWNER), 88);
});
test("all client methods require verified Auth", async () => {
  const f = fixture();
  for (const [method, data] of [["prepare", {}], ["operate", {}], ["session", {}], ["readSession", {}], ["readEvent", {}],
    ["readMeeting", {}], ["listEvents", {}], ["recordDownload", {}]]) await error(() => f.controller[method](null, data), "UNAUTHENTICATED");
});
test("clients including organizer cannot dispatch worker capabilities or claim authority", async () => {
  const f = fixture(); await f.create();
  for (const kind of ["CLOSE_QUALIFICATION", "ADVANCE_MATCH", "reconcileEvent"]) await error(() => f.operate(kind), "PERMISSION_DENIED");
  for (const field of ["ownerUid", "now", "score", "formFingerprint"]) await error(() => f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001", [field]: OWNER}), "INVALID_ARGUMENT");
});
test("reserved worker namespaces cannot be preempted by player operations", async () => {
  const f = fixture(); await f.create();
  for (const operationId of ["ctw_match_reserved0000", "freeze_advance_reserved0000"]) await error(() => f.operate("START_QUALIFICATION", "alice", {operationId, roundNumber: 1, attemptId: "attempt_alice_00000001"}), "INVALID_ARGUMENT");
});
test("qualification start/read shows only own current question and no fingerprint", async () => {
  const f = fixture(); await f.create(); const started = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  assertSafe(started); const view = await f.controller.session("alice", {kind: "READ", sessionId: started.session.sessionId});
  assert.equal(view.currentQuestion.questionId.length, 4); assertSafe(view);
  await error(() => f.controller.readSession("bob", {sessionId: started.session.sessionId}), "PERMISSION_DENIED");
});
test("answer operation derives stored form binding; retry returns original ack without advancing", async () => {
  const f = fixture(); await f.create(); const started = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const sessionId = started.session.sessionId; const view = await f.controller.readSession("alice", {sessionId});
  const request = {kind: "ANSWER", operationId: f.id(), sessionId, questionId: view.currentQuestion.questionId, questionIndex: 0,
    answerPayload: {type: "single-choice", selected: "a"}};
  const first = await f.controller.session("alice", request); f.state.now += 50_000;
  assert.deepEqual(await f.controller.session("alice", request), first); assertSafe(first);
  assert.equal(first.session.questionIndex, 1);
  await error(() => f.controller.session("alice", {...request, formFingerprint: "a".repeat(64)}), "INVALID_ARGUMENT");
  await error(() => f.controller.session("bob", request), "PERMISSION_DENIED");
  await error(() => f.controller.session("alice", {...request, answerPayload: null}), "CONFLICTING_REPLAY");
});
test("late different-ID answer cannot be interpreted as next question", async () => {
  const f = fixture(); await f.create(); const started = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const sessionId = started.session.sessionId; const view = await f.controller.readSession("alice", {sessionId});
  const request = {kind: "ANSWER", operationId: f.id(), sessionId, questionId: view.currentQuestion.questionId, questionIndex: 0, answerPayload: null};
  await f.controller.session("alice", request);
  await error(() => f.controller.session("alice", {...request, operationId: f.id()}), "QUESTION_ALREADY_CLOSED");
});
test("client session scores, clocks, worker names and authority are refused", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const payload = {kind: "ADVANCE", operationId: f.id(), sessionId: start.session.sessionId};
  for (const field of ["ownerUid", "nowMs", "percentScore", "elapsedMs"]) await error(() => f.controller.session("alice", {...payload, [field]: 1}), "INVALID_ARGUMENT");
  for (const operationId of ["ctw_session_reserved0000", "freeze_advance_reserved0000"]) await error(() => f.controller.session("alice", {...payload, operationId}), "INVALID_ARGUMENT");
});
test("waiting qualification reconciliation leaves no permanent advance receipt", async () => {
  const f = fixture(); await f.create(); const before = clone([...f.db.store]);
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.status, "WAITING"); assert.equal(result.nextDueAtMs, T + 7 * DAY);
  assert.deepEqual([...f.db.store], before);
});
test("empty qualification cancels/refunds with no player polling and replay is stable", async () => {
  const f = fixture([]); const result = await f.qualify(); assert.equal(result.done, true); assert.equal(result.status, "CANCELLED");
  assert.equal(balance(f, OWNER), 100); const before = clone([...f.db.store]);
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.deepEqual([...f.db.store], before);
});
test("one-player qualification settles its timeout before cancellation/refund", async () => {
  const f = fixture(["alice"]); const result = await f.qualify(); assert.equal(result.status, "CANCELLED");
  assert.equal([...f.db.store].filter(([key]) => key.startsWith(`${VERIFIED_RUNS}/`)).length, 1); assert.equal(balance(f, OWNER), 100);
});
test("qualification freeze settles all admitted sessions and creates bracket without player polling", async () => {
  const f = fixture(); const result = await f.qualify(); assert.equal(result.done, false); assert.equal(f.db.store.get(E).status, "BRACKET");
  assert.equal(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`).verifiedRunCount, 2);
  assert.equal(workerReceipts(f).length, 1); assert.ok(result.nextDueAtMs > f.state.now);
  assertSafe(await f.controller.readEvent("alice", {eventId: EVENT}));
});
test("repeated waiting match reconciliation never memoizes WAITING or RESULTS_PENDING", async () => {
  const f = fixture(); await f.qualify(); const before = clone(workerReceipts(f));
  await f.controller.reconcileEvent(EVENT); await f.controller.reconcileEvent(EVENT);
  assert.deepEqual(workerReceipts(f), before);
  const ready = await f.readyBoth(); await f.controller.reconcileEvent(EVENT);
  assert.deepEqual(workerReceipts(f), before);
  assert.equal(f.db.store.get(`${P.sessions}/${ready.sessions[0]}`).questionIndex, 0);
});
test("no agreement deadline produces automatic double-out and exact unused refund", async () => {
  const f = fixture(); await f.qualify(); const m = f.match(); f.state.now = m.stageEndsAtMs - READY_WINDOW_MS - m.sharedForm.maxDurationMs;
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.done, true); assert.equal(balance(f, OWNER), 100);
  assert.equal(f.db.store.get(E).outcomes.at(-1).status, "DOUBLE_OUT");
});
test("one timely proposer wins technical forfeit at coordination cutoff without other polling", async () => {
  const f = fixture(); await f.qualify(); const m = f.match(); const player = f.pending().entrants[0].uid; f.state.now = m.stageStartsAtMs + 1;
  await f.operate("PROPOSE", player, {matchId: m.matchId, slotStartsAtMs: f.state.now + 1000});
  f.state.now = m.stageEndsAtMs - READY_WINDOW_MS - m.sharedForm.maxDurationMs;
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, player), 5); assert.equal(balance(f, OWNER), 95);
});
test("one-hour no-show automatically pays only winner and refunds unawarded runner-up", async () => {
  const f = fixture(); await f.qualify(); const a = await f.agree(); f.state.now = a.slotStartsAtMs;
  await f.operate("READY", a.pair[0], {matchId: a.match.matchId}); f.state.now += READY_WINDOW_MS;
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, a.pair[0]), 5);
  assert.equal(balance(f, a.pair[1]), 0); assert.equal(balance(f, OWNER), 95);
});
test("agreed slot with neither ready double-outs at one-hour boundary", async () => {
  const f = fixture(); await f.qualify(); const a = await f.agree(); f.state.now = a.slotStartsAtMs + READY_WINDOW_MS;
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, OWNER), 100);
});
test("worker advances expired shown questions once, without extending overall deadline", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth(); const original = clone(f.db.store.get(`${P.sessions}/${a.sessions[0]}`));
  f.state.now = original.questionEndsAtMs; await f.controller.reconcileEvent(EVENT);
  assert.equal(f.db.store.get(`${P.sessions}/${a.sessions[0]}`).questionIndex, 1);
  assert.equal(f.db.store.get(`${P.sessions}/${a.sessions[0]}`).endsAtMs, original.endsAtMs);
  const count = workerReceipts(f).length; await f.controller.reconcileEvent(EVENT); assert.equal(workerReceipts(f).length, count);
});
test("match sessions settle forced full20 at cutoff, then verified ranking/payout happens without polling", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth(); f.state.now = f.db.store.get(`${P.sessions}/${a.sessions[0]}`).endsAtMs;
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.done, true);
  for (const sessionId of a.sessions) { const run = f.db.store.get(`${VERIFIED_RUNS}/${sessionId}`); assert.equal(run.codeAnswer, "1".repeat(20)); assert.equal(run.percentScore, 0); }
  assert.equal(balance(f, a.pair[0]), 5); assert.equal(balance(f, a.pair[1]), 3); assert.equal(balance(f, OWNER), 92);
  assertSafe(await f.controller.readSession(a.pair[0], {sessionId: a.sessions[0]}));
  const before = clone([...f.db.store]); await f.controller.reconcileEvent(EVENT); assert.deepEqual([...f.db.store], before);
});
test("missing match key never fabricates zero or memoizes final worker ID; repair later succeeds", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth(); const keyPath = `${P.privateForms}/${EVENT}/forms/${digest(a.match.sharedForm.formId)}`;
  const key = f.db.store.get(keyPath); f.db.store.delete(keyPath); f.state.now = f.db.store.get(`${P.sessions}/${a.sessions[0]}`).endsAtMs;
  const first = await f.controller.reconcileEvent(EVENT); assert.equal(first.status, "PENDING_REPAIR"); assert.equal(first.done, false);
  assert.equal(f.db.store.has(`${VERIFIED_RUNS}/${a.sessions[0]}`), false); assert.equal(balance(f, OWNER), 88);
  assert.equal(workerReceipts(f).filter(([, row]) => row.operationId.startsWith("ctw_match_")).length, 0);
  f.db.store.set(keyPath, key); f.state.now += 1000;
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, OWNER), 92);
});
test("missing qualification keys keep freeze incomplete and escrow reserved until repaired", async () => {
  const f = fixture(); await f.create(); await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const event = f.db.store.get(E); const keyPath = `${P.privateForms}/${EVENT}/forms/${digest(event.qualificationForms[0].formId)}`;
  const key = f.db.store.get(keyPath); f.db.store.delete(keyPath); f.state.now = T + 7 * DAY;
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.status, "PENDING_REPAIR");
  assert.equal(f.db.store.get(E).status, "QUALIFICATION"); assert.equal(balance(f, OWNER), 88);
  f.db.store.set(keyPath, key); assert.equal((await f.controller.reconcileEvent(EVENT)).status, "CANCELLED"); assert.equal(balance(f, OWNER), 100);
});
test("readMeeting exposes paged later offers, not only first proposal, with participant authorization", async () => {
  const f = fixture(); await f.qualify(); const m = f.match(); const pair = f.pending().entrants.map(row => row.uid); f.state.now = m.stageStartsAtMs + 10;
  const slots = [100, 200, 300].map(offset => f.state.now + offset);
  for (const slotStartsAtMs of slots) await f.operate("PROPOSE", pair[0], {matchId: m.matchId, slotStartsAtMs});
  const request = {eventId: EVENT, matchId: m.matchId, pageSize: 2};
  const first = await f.controller.readMeeting(pair[1], request); const second = await f.controller.readMeeting(pair[1], {...request, afterOfferId: first.nextOfferId});
  assert.equal(first.hasMore, true); assert.equal(second.hasMore, false);
  assert.deepEqual([...first.offers, ...second.offers].map(row => row.slotStartsAtMs).sort(), slots); assertSafe(first); assertSafe(second);
  await error(() => f.controller.readMeeting("viewer", request), "PERMISSION_DENIED");
});
test("discovery pages bounded ordered events with a working cursor and redacts every row", async () => {
  const f = fixture();
  for (const eventId of ["event_listing_00000001", "event_listing_00000002", "event_listing_00000003"]) {
    f.db.store.set(`${P.events}/${eventId}`, {schemaVersion: 1, eventId, organizerUid: OWNER, status: "QUALIFICATION",
      lastMutationAtMs: T, serverSeed: "must-not-leak", source: {kind: "COURSE", id: "course"},
      qualification: {startsAtMs: T, endsAtMs: T + 7 * DAY}});
  }
  await f.create();
  const first = await f.controller.listEvents("viewer", {pageSize: 2});
  assert.equal(first.hasMore, true); assert.equal(first.nextEventId, "event_listing_00000001");
  assert.deepEqual(first.events.map(row => row.eventId), [EVENT, "event_listing_00000001"]);
  const second = await f.controller.listEvents("viewer", {pageSize: 2, afterEventId: first.nextEventId});
  assert.equal(second.hasMore, false); assert.equal(second.nextEventId, null);
  assert.deepEqual(second.events.map(row => row.eventId), ["event_listing_00000002", "event_listing_00000003"]);
  assertSafe(first); assertSafe(second);
  assert.ok(!JSON.stringify([first, second]).includes("must-not-leak"));
  const rest = await f.controller.listEvents("viewer", {});
  assert.deepEqual(rest.events.map(row => row.eventId).sort(), [EVENT, "event_listing_00000001", "event_listing_00000002", "event_listing_00000003"].sort());
  assert.equal(rest.hasMore, false);
  await error(() => f.controller.listEvents("viewer", {pageSize: 101}), "INVALID_ARGUMENT");
  await error(() => f.controller.listEvents("viewer", {pageSize: 0}), "INVALID_ARGUMENT");
  await error(() => f.controller.listEvents("viewer", {afterEventId: "short"}), "INVALID_ARGUMENT");
  await error(() => f.controller.listEvents("viewer", {afterEventId: 5}), "INVALID_ARGUMENT");
  await error(() => f.controller.listEvents("viewer", {pageSize: 2, unexpected: 1}), "INVALID_ARGUMENT");
});
test("download records are create-only server attestations, idempotent on repeat", async () => {
  const f = fixture();
  f.db.store.set("quests/other-course", {catalogId: "catalog", authorUid: OWNER, version: 1, visibleOn: ["archive"], archived: true});
  const first = await f.controller.recordDownload("alice", {operationId: f.id(), courseId: "other-course"});
  assert.deepEqual(first, {courseId: "other-course", downloadedAtMs: T});
  const second = await f.controller.recordDownload("bob", {operationId: f.id(), courseId: "other-course"});
  const repeat = await f.controller.recordDownload("alice", {operationId: f.id(), courseId: "other-course"});
  assert.deepEqual(repeat, {...first, replayed: true}); assert.equal(second.courseId, "other-course"); assert.ok(second.downloadedAtMs === T);
  await error(() => f.controller.recordDownload("alice", {operationId: f.id(), courseId: "no-such-course"}), "INVALID_ARGUMENT");
  const records = [...f.db.store].filter(([key]) => key.startsWith("course_tournament_downloads/"));
  assert.equal(records.length, 4); // two seeded player records + one per (alice, bob) x other-course
  for (const courseId of ["", " ".repeat(1), "x".repeat(513), 5]) await error(() => f.controller.recordDownload("alice", {operationId: f.id(), courseId}), "INVALID_ARGUMENT");
  await error(() => f.controller.recordDownload("alice", {operationId: "ctw_match_reserved0000", courseId: "course"}), "INVALID_ARGUMENT");
  await error(() => f.controller.recordDownload("alice", {operationId: f.id(), courseId: "course", uid: "bob"}), "INVALID_ARGUMENT");
  // The seeded attested record for the fixture course admits alice's first qualification round.
  await f.create();
  const started = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  assert.equal(started.session.roundNumber, 1); assertSafe(started);
});
test("the organizer's link token admits a player without any download record; reads never carry it", async () => {
  const f = fixture();
  f.db.store.delete(`course_tournament_downloads/${digest(["alice", "course"])}`);
  const created = await f.create();
  const token = created.admissionLink?.token;
  assert.ok(/^[a-f0-9]{48}$/.test(token));
  await error(() => f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"}), "PERMISSION_DENIED");
  await error(() => f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001", linkToken: "f".repeat(48)}), "PERMISSION_DENIED");
  const started = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001", linkToken: token});
  assertSafe(started); assert.equal(Object.hasOwn(started, "admissionLink"), false);
  const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.equal(Object.hasOwn(view, "admissionLink"), false); assertSafe(view);
  const list = await f.controller.listEvents("viewer", {});
  assert.equal(Object.hasOwn(list, "admissionLink"), false); assertSafe(list);
});
test("event reads project both prize forms through one shape and never leak the legacy DTO", async () => {
  const f = fixture(); await f.create();
  const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.deepEqual(view.event.prizes, {listVersion: 1, maximumLiabilityBoxes: 10,
    rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}, {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]});
  // A stored pre-adoption event reads through the same unified projection, DTO fields suppressed.
  const listedV1 = await f.controller.listEvents("viewer", {pageSize: 10});
  assert.equal(listedV1.events[0].prizes.listVersion, 1);
  assert.ok(!JSON.stringify(listedV1).includes("serverSeed"));
  const stored = f.db.store.get(E); stored.prizes = {first: 5, second: 3, third: 1}; stored.maximumLiabilityBoxes = 10;
  const legacy = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.deepEqual(legacy.event.prizes, {listVersion: 0, maximumLiabilityBoxes: 10,
    rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}, {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]});
  assert.ok(!["first", "second", "third"].some((name) => Object.hasOwn(legacy.event.prizes, name)));
  assertSafe(legacy);
  // Discovery rows flow through the same projection.
  const list = await f.controller.listEvents("viewer", {});
  assert.equal(list.events.find((row) => row.eventId === EVENT).prizes.listVersion, 0);
});
test("read safe view suppresses even unexpected grading fields nested in stored redacted options", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const stored = f.db.store.get(`${P.sessions}/${start.session.sessionId}`); const form = f.db.store.get(`${E}/forms/${digest(stored.formId)}`);
  form.questions[0].publicPayload.options[0].correctOptionId = "secret"; form.questions[0].publicPayload.correctOptionId = "secret";
  assertSafe(await f.controller.readSession("alice", {sessionId: stored.sessionId}));
  form.questions[0].publicPayload.blanks = [{correctCandidateId: "secret"}];
  await error(() => f.controller.readSession("alice", {sessionId: stored.sessionId}), "UNAVAILABLE");
});
test("source retirement after CREATE does not strand autonomous qualification close", async () => {
  const f = fixture(); await f.create();
  for (const uid of ["alice", "bob"]) await f.operate("START_QUALIFICATION", uid, {roundNumber: 1, attemptId: `attempt_${uid}_00000000001`});
  f.db.store.get("quests/course").visibleOn = []; f.db.store.get("lessons/lesson").reviewSubmissionId = "revoked";
  f.state.now = T + 7 * DAY; await f.controller.reconcileEvent(EVENT); assert.equal(f.db.store.get(E).status, "BRACKET");
});

test("first server answer arrival is captured before asynchronous binding read and survives key repair", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const sessionId = start.session.sessionId; const view = await f.controller.readSession("alice", {sessionId});
  const stored = f.db.store.get(`${P.sessions}/${sessionId}`); const keyPath = `${P.privateForms}/${EVENT}/forms/${digest(stored.formId)}`;
  const keys = f.db.store.get(keyPath); f.db.store.delete(keyPath); f.state.now = T + 100;
  const document = f.db.doc; let delayed = false;
  f.db.doc = p => { const ref = document(p); return {...ref, get: async () => {
    if (p === `${P.sessions}/${sessionId}` && !delayed) { delayed = true; f.state.now = T + 20000; }
    return ref.get();
  }}; };
  const request = {kind: "ANSWER", operationId: f.id(), sessionId, questionIndex: 0, questionId: view.currentQuestion.questionId,
    answerPayload: {type: "single-choice", selected: "a"}};
  await error(() => f.controller.session("alice", request), "UNAVAILABLE");
  assert.equal(f.db.store.get(`${SESSION_INGRESS}/${digest(request.operationId)}`).firstReceivedAtMs, T + 100);
  f.db.store.set(keyPath, keys); assertSafe(await f.controller.session("alice", request));
  const slot = f.db.store.get(`${P.sessions}/${sessionId}/answers/00`);
  assert.equal(slot.scoreDigit, 9); assert.equal(slot.receivedAtMs, T + 100);
});
test("controller checkpoints a bounded scan and resumes instead of permanently exhausting the first page", async () => {
  const f = fixture(["alice", "bob", "carol"], {workerBounds: {qualificationPageSize: 1, qualificationMaxPages: 1}});
  const result = await f.qualify(); assert.equal(result.status, "WAITING"); assert.equal(result.done, false);
  assert.equal(result.qualificationPhase, "SCANNING"); assert.equal(result.admittedCount, 1); assert.equal(balance(f, OWNER), 88);
  assert.equal(f.db.store.has(`${QUALIFICATION_RANKINGS}/${EVENT}`), false); assert.equal(workerReceipts(f).length, 0);
  for (let i = 0; i < 6 && f.db.store.get(E).status === "QUALIFICATION"; i++) await f.restart().reconcileEvent(EVENT);
  assert.equal(f.db.store.get(E).status, "BRACKET"); assert.equal(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`).verifiedRunCount, 3);
});
test("missing admitted qualification session cannot silently disappear from automatic freeze", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  f.db.store.delete(`${P.sessions}/${start.session.sessionId}`); f.state.now = T + 7 * DAY;
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.status, "PENDING_REPAIR");
  assert.equal(result.pendingCount, 1); assert.equal(balance(f, OWNER), 88);
});
test("both players can finish early; worker uses real verified scores before deadline and only aggregate is public", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth();
  for (let player = 0; player < 2; player++) {
    let view = await f.controller.readSession(a.pair[player], {sessionId: a.sessions[player]});
    for (let i = 0; i < 20; i++) {
      f.state.now++;
      view = await f.controller.session(a.pair[player], {kind: "ANSWER", operationId: f.id(), sessionId: a.sessions[player],
        questionIndex: i, questionId: view.currentQuestion.questionId, answerPayload: {type: "single-choice", selected: player ? "b" : "a"}});
      assertSafe(view);
    }
    assert.equal(view.result.percentScore, player ? 0 : 100);
  }
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, a.pair[0]), 5);
  assert.equal(balance(f, a.pair[1]), 3); assert.equal(balance(f, OWNER), 92);
});
test("missing match session remains repairable; no fabricated replacement or terminal receipt", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth(); const saved = f.db.store.get(`${P.sessions}/${a.sessions[0]}`);
  f.db.store.delete(`${P.sessions}/${a.sessions[0]}`); f.state.now = saved.endsAtMs;
  const waiting = await f.controller.reconcileEvent(EVENT); assert.equal(waiting.status, "PENDING_REPAIR"); assert.equal(balance(f, OWNER), 88);
  assert.equal(f.db.store.has(`${P.sessions}/${a.sessions[0]}`), false);
  f.db.store.set(`${P.sessions}/${a.sessions[0]}`, saved); assert.equal((await f.controller.reconcileEvent(EVENT)).done, true);
});
test("atomic payout failure preserves escrow and permanent worker ID succeeds on retry", async () => {
  const f = fixture(); await f.qualify(); const m = f.match(); f.state.now = m.stageEndsAtMs;
  const run = f.db.runTransaction; let injected = false;
  f.db.runTransaction = body => run(tx => body({...tx, set: (ref, value, options) => {
    if (!injected && ref.path === E && value.status === "FINISHED") { injected = true; f.db.failNextCommit(); }
    return tx.set(ref, value, options);
  }}));
  await assert.rejects(() => f.controller.reconcileEvent(EVENT), /injected atomic commit/);
  assert.equal(balance(f, OWNER), 88); assert.equal(f.db.store.get(E).escrow.remainingBoxes, 12);
  assert.equal(workerReceipts(f).filter(([, row]) => row.operationId.startsWith("ctw_match_")).length, 0);
  assert.equal((await f.controller.reconcileEvent(EVENT)).done, true); assert.equal(balance(f, OWNER), 100);
});
test("all16 qualifiers can reach terminal bracket with no agreement and no player polling, in bounded15 matches", async () => {
  const players = Array.from({length: 16}, (_, i) => `player${String(i).padStart(2, "0")}`);
  const f = fixture(players); await f.qualify(); const event = f.db.store.get(E);
  f.state.now = Math.max(...event.bracket.rounds.map(row => row.matches[0].stageEndsAtMs));
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.done, true); assert.equal(balance(f, OWNER), 100);
  assert.ok(result.transitions <= 15); assert.equal(f.db.store.get(E).outcomes.length, 8);
});
test("read-only reconnect cannot pause/restart expired question or expose its next question", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const sessionId = start.session.sessionId; const original = clone(f.db.store.get(`${P.sessions}/${sessionId}`));
  f.state.now = original.questionEndsAtMs + 100;
  const view = await f.controller.readSession("alice", {sessionId});
  assert.equal(view.currentQuestion, null); assert.equal(view.questionDeadlineReached, true);
  assert.deepEqual(f.db.store.get(`${P.sessions}/${sessionId}`), original); assertSafe(view);
});

// Story 5.6: ONLINE-O1/O2 window — worker never delivers, the read projection resolves a live
// window, and the sweep settles an expired one at its captured edge.
test("worker close leaves the window open; readSession delivers it with anchored clocks and redacted view", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth();
  const id = a.sessions[0];
  f.state.now = f.db.store.get(`${P.sessions}/${id}`).questionEndsAtMs;
  const firstPass = await f.controller.reconcileEvent(EVENT); // worker closes q0, opens the window undelivered
  const stored = f.db.store.get(`${P.sessions}/${id}`);
  assert.equal(stored.window.state, "OPEN"); assert.equal(stored.window.delivery, null);
  // The sweep reschedules at the window edge, never at the stale question clock (no hot loops).
  assert.equal(firstPass.nextDueAtMs, stored.window.expiresAtMs);
  assert.equal(stored.questionIndex, 1);
  const receiptsAfterClose = workerReceipts(f).length;
  const waitingPass = await f.controller.reconcileEvent(EVENT); // a waiting worker pass stages nothing new
  assert.equal(workerReceipts(f).length, receiptsAfterClose);
  assert.equal(waitingPass.nextDueAtMs, stored.window.expiresAtMs);
  f.state.now += 5000;
  const view = await f.controller.readSession(a.pair[0], {sessionId: id});
  assert.equal(view.currentQuestion.questionId.length, 4);
  assert.equal(view.session.questionStartedAtMs, f.state.now);
  assert.equal(view.window.state, "DELIVERED");
  assertSafe(view);
  const anchored = f.state.now;
  f.state.now += 1000;
  const again = await f.controller.readSession(a.pair[0], {sessionId: id});
  assert.equal(again.session.questionStartedAtMs, anchored); // never re-anchored
  assertSafe(again);
});

test("the sweep settles a match session whose window expired undelivered, at the captured edge", async () => {
  const f = fixture(); await f.qualify(); const a = await f.readyBoth();
  const id = a.sessions[0];
  f.state.now = f.db.store.get(`${P.sessions}/${id}`).questionEndsAtMs;
  await f.controller.reconcileEvent(EVENT);
  const edge = f.db.store.get(`${P.sessions}/${id}`).window.expiresAtMs;
  f.state.now = edge + 1;
  const result = await f.controller.reconcileEvent(EVENT);
  assert.equal(result.done, true); // both sessions settle, the match resolves through the bracket
  const run = f.db.store.get(`${VERIFIED_RUNS}/${id}`);
  assert.equal(run.completedAtMs, edge); // the captured edge, never the sweep moment
  const forced = f.db.store.get(`${P.sessions}/${id}/answers/01`);
  assert.equal(forced.kind, "FORCED_CUTOFF");
  assert.equal(forced.closedAtMs, edge);
  assert.equal(forced.wasShown, false);
});

test("ready-ACK through the public session callable receipts idempotently and redacted", async () => {
  const f = fixture(); await f.create();
  const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  const sessionId = start.session.sessionId;
  const view = await f.controller.readSession("alice", {sessionId});
  await f.controller.session("alice", {kind: "ANSWER", operationId: f.id(), sessionId, questionIndex: 0,
    questionId: view.currentQuestion.questionId, answerPayload: {type: "single-choice", selected: "a"}});
  const windowId = f.db.store.get(`${P.sessions}/${sessionId}`).window.windowId;
  const ack = {kind: "READY_ACK", operationId: f.id(), sessionId, questionIndex: 1, windowId};
  const first = await f.controller.session("alice", ack);
  assert.equal(first.status, "READY_ACK");
  assert.equal(first.readyAck.windowId, windowId);
  assert.equal(first.readyAck.questionIndex, 1);
  assertSafe(first);
  assert.deepEqual((await f.controller.session("alice", {...ack, operationId: f.id()})).readyAck, first.readyAck);
  await error(() => f.controller.session("alice", {...ack, operationId: f.id(), questionIndex: 0}), "INVALID_ARGUMENT");
  await error(() => f.controller.session("bob", {...ack, operationId: f.id()}), "PERMISSION_DENIED");
  await error(() => f.controller.session("alice", {kind: "READY_ACK", operationId: f.id(), sessionId, questionIndex: 1, windowId: "win_000000000000", extra: 1}), "INVALID_ARGUMENT");
});

async function admitRounds(f, players, rounds) {
  await f.create();
  for (const uid of players) for (let roundNumber = 1; roundNumber <= rounds; roundNumber++) {
    await f.operate("START_QUALIFICATION", uid, {roundNumber, attemptId: `attempt_${uid}_${roundNumber}_0000000001`});
  }
  f.state.now = T + 7 * DAY;
}
function scanHead(f) { return [...f.db.store].find(([key]) => key.startsWith(`${E}/qualification_scans/`) && key.split("/").length === 4)?.[1]; }
function assertWorkerSafe(result) {
  assertSafe(result);
  for (const key of ["ranking", "forms", "freezeId", "manifestDigest", "verifiedRuns", "uid", "sessionId", "snapshotId"]) assert.equal(Object.hasOwn(result, key), false);
}
test("real controller continues 501 admitted sessions across process restarts and publishes exactly one full-cohort ranking", async () => {
  const players = Array.from({length: 167}, (_, i) => `many${String(i).padStart(3, "0")}`);
  const f = fixture(players); await admitRounds(f, players, 3); let calls = 0; let priorCount = 0;
  while (f.db.store.get(E).status === "QUALIFICATION" && calls < 60) {
    const result = await f.restart().reconcileEvent(EVENT); calls++; assertWorkerSafe(result);
    const head = scanHead(f); assert.ok(head.admittedCount >= priorCount); priorCount = head.admittedCount;
    if (f.db.store.get(E).status === "QUALIFICATION") {
      assert.equal(f.db.store.has(`${QUALIFICATION_RANKINGS}/${EVENT}`), false);
      assert.equal(f.db.store.get(E).bracket, undefined); assert.equal(workerReceipts(f).length, 0);
    }
  }
  assert.ok(calls > 10 && calls < 60); assert.equal(f.db.store.get(E).status, "BRACKET");
  const ranking = f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`);
  assert.equal(ranking.verifiedRunCount, 501); assert.equal(ranking.totalRanked, 167); assert.equal(ranking.ranking.length, 16);
  assert.equal(scanHead(f).admittedCount, 501); assert.equal(scanHead(f).verifiedCount, 501);
  assert.equal(workerReceipts(f).length, 1); assert.equal(balance(f, OWNER), 88);
  const frozen = clone(ranking); await f.restart().reconcileEvent(EVENT);
  assert.deepEqual(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`), frozen); assert.equal(workerReceipts(f).length, 1);
});
test("502 verified admissions complete through persisted calculation without exclusion or a size wait", async () => {
  const players = Array.from({length: 251}, (_, i) => `over${String(i).padStart(3, "0")}`);
  const f = fixture(players); await admitRounds(f, players, 2); let result;
  let saved = false;
  for (let i = 0; i < 60 && f.db.store.get(E).status === "QUALIFICATION"; i++) {
    result = await f.restart().reconcileEvent(EVENT); assertWorkerSafe(result);
    saved ||= result.reason === "CHECKPOINT_SAVED";
    assert.notEqual(result.reason, "RESOURCE_ENVELOPE");
  }
  assert.equal(saved, true); assert.equal(scanHead(f).admittedCount, 502); assert.equal(scanHead(f).verifiedCount, 502);
  assert.equal(scanHead(f).status, "PUBLISHED"); assert.equal(f.db.store.get(E).status, "BRACKET");
  const rank = f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`);
  assert.equal(rank.schemaVersion, 2); assert.equal(rank.verifiedRunCount, 502); assert.equal(rank.totalRanked, 251);
  assert.equal(rank.ranking.length, 16); assert.equal(workerReceipts(f).length, 1); assert.equal(balance(f, OWNER), 88);
  const before = clone(rank); await f.restart().reconcileEvent(EVENT);
  assert.deepEqual(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`), before);
});
test("scanComplete plus one calculation page is not a published ranking or completion receipt", async () => {
  const f = fixture(["alice", "bob"], {workerBounds: {qualificationCalculationMaxPages: 1}});
  await admitRounds(f, ["alice", "bob"], 1);
  const result = await f.controller.reconcileEvent(EVENT);
  assert.equal(result.qualificationPhase, "CALCULATION_PENDING"); assert.equal(result.reason, "CHECKPOINT_SAVED"); assertWorkerSafe(result);
  assert.equal(scanHead(f).status, "SCAN_COMPLETE"); assert.equal(f.db.store.has(`${QUALIFICATION_RANKINGS}/${EVENT}`), false);
  assert.equal(workerReceipts(f).length, 0); assert.equal(balance(f, OWNER), 88);
  for (let i = 0; i < 6 && f.db.store.get(E).status === "QUALIFICATION"; i++) await f.restart().reconcileEvent(EVENT);
  assert.equal(f.db.store.get(E).status, "BRACKET");
});
test("deadline consumed by initial awaited event read starts no scan or calculation", async () => {
  const f = fixture(); await admitRounds(f, ["alice", "bob"], 1); const before = clone([...f.db.store]);
  const deadlineAtMs = f.state.now + 100; const doc = f.db.doc;
  f.db.doc = p => { const ref = doc(p); return {...ref, get: async () => { const result = await ref.get(); if (p === E) f.state.now = deadlineAtMs; return result; }}; };
  const result = await f.controller.reconcileEvent(EVENT, {deadlineAtMs});
  assert.equal(result.reason, "INVOCATION_TIME"); assert.deepEqual([...f.db.store], before);
});
test("published ranking survives failed close transaction and restart reuses it without duplicate escrow receipt", async () => {
  const f = fixture(); await admitRounds(f, ["alice", "bob"], 1);
  const run = f.db.runTransaction; let injected = false;
  f.db.runTransaction = body => run(tx => body({...tx, set: (ref, value, options) => {
    if (!injected && ref.path === E && value.status === "BRACKET") { injected = true; f.db.failNextCommit(); }
    return tx.set(ref, value, options);
  }}));
  await assert.rejects(() => f.controller.reconcileEvent(EVENT), /injected atomic commit/);
  const rank = clone(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`)); assert.equal(rank.complete, true);
  assert.equal(f.db.store.get(E).status, "QUALIFICATION"); assert.equal(workerReceipts(f).length, 0); assert.equal(balance(f, OWNER), 88);
  await f.restart().reconcileEvent(EVENT); assert.equal(f.db.store.get(E).status, "BRACKET");
  assert.deepEqual(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`), rank); assert.equal(workerReceipts(f).length, 1);
});
test("worker bounds limit pages rather than product cohort size", async () => {
  assert.throws(() => fixture([], {workerBounds: {qualificationMaxVerifiedRuns: 501}}), {code: "INVALID_ARGUMENT"});
  assert.throws(() => fixture([], {workerBounds: {qualificationPageSize: 51}}), {code: "INVALID_ARGUMENT"});
  assert.throws(() => fixture([], {workerBounds: {qualificationCalculationMaxPages: 11}}), {code: "INVALID_ARGUMENT"});
  const f = fixture(["alice", "bob"], {workerBounds: {qualificationCalculationMaxPages: 1}});
  const result = await f.qualify(); assert.equal(result.reason, "CHECKPOINT_SAVED"); assert.equal(result.verifiedCount, 2);
});
test("repair cursor reaches other forms while one is missing and never ranks the incomplete subset", async () => {
  const players = ["alice", "bob", "carol"];
  const f = fixture(players, {workerBounds: {qualificationPageSize: 1}}); await f.create();
  for (let i = 0; i < players.length; i++) await f.operate("START_QUALIFICATION", players[i],
    {roundNumber: i + 1, attemptId: `attempt_${players[i]}_00000000001`});
  const form = f.db.store.get(E).qualificationForms[0]; const keyPath = `${P.privateForms}/${EVENT}/forms/${digest(form.formId)}`;
  const key = f.db.store.get(keyPath); f.db.store.delete(keyPath); f.state.now = T + 7 * DAY;
  let result;
  for (let i = 0; i < 10; i++) result = await f.restart().reconcileEvent(EVENT);
  assert.equal(result.status, "PENDING_REPAIR"); assert.equal(result.admittedCount, 3);
  assert.equal(result.verifiedCount, 2); assert.equal(result.pendingCount, 1); assert.equal(scanHead(f).discoveryDone, true);
  assert.equal(f.db.store.has(`${QUALIFICATION_RANKINGS}/${EVENT}`), false); assert.equal(workerReceipts(f).length, 0);
  f.db.store.set(keyPath, key);
  for (let i = 0; i < 4 && f.db.store.get(E).status === "QUALIFICATION"; i++) await f.restart().reconcileEvent(EVENT);
  // The unchanged LS model does not rank singleton groups; retaining all three is distinct
  // from inventing rankability. This full-cohort result therefore follows normal cancellation.
  assert.equal(f.db.store.get(E).status, "CANCELLED"); assert.equal(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`).verifiedRunCount, 3);
  assert.equal(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`).totalRanked, 0);
});
test("deadline consumed by close reads preserves published ranking but writes no bracket or permanent close receipt", async () => {
  const f = fixture(); await admitRounds(f, ["alice", "bob"], 1); const deadlineAtMs = f.state.now + 120_000;
  const run = f.db.runTransaction;
  f.db.runTransaction = body => run(tx => body({...tx, get: async ref => {
    const result = await tx.get(ref);
    if (ref.path.startsWith(`${P.operations}/`)) f.state.now = deadlineAtMs;
    return result;
  }}));
  const result = await f.controller.reconcileEvent(EVENT, {deadlineAtMs});
  assert.equal(result.qualificationPhase, "RANKING_PUBLISHED"); assert.equal(result.reason, "INVOCATION_TIME");
  assert.equal(f.db.store.get(E).status, "QUALIFICATION"); assert.equal(f.db.store.get(`${QUALIFICATION_RANKINGS}/${EVENT}`).complete, true);
  assert.equal(workerReceipts(f).length, 0); assert.equal(balance(f, OWNER), 88);
  f.db.runTransaction = run; await f.restart().reconcileEvent(EVENT); assert.equal(f.db.store.get(E).status, "BRACKET");
});
test("bracket deadline consumed by first meeting read starts no next match and performs no monetary or clock writes", async () => {
  const f = fixture(["alice", "bob", "carol", "dave"]); await f.qualify();
  f.state.now = Math.max(...f.db.store.get(E).bracket.rounds.map(round => round.matches[0].stageEndsAtMs));
  const before = clone([...f.db.store]); const deadlineAtMs = f.state.now + 10; const doc = f.db.doc; let meetingsRead = 0;
  f.db.doc = p => { const ref = doc(p); return {...ref, get: async () => {
    const result = await ref.get(); if (p.startsWith(`${E}/meetings/`)) { meetingsRead++; f.state.now = deadlineAtMs; } return result;
  }}; };
  const result = await f.controller.reconcileEvent(EVENT, {deadlineAtMs});
  assert.equal(result.status, "WAITING"); assert.equal(result.reason, "INVOCATION_TIME"); assert.equal(result.done, false);
  assert.equal(meetingsRead, 1); assert.equal(result.transitions, 0); assert.ok(result.nextDueAtMs > f.state.now); assertWorkerSafe(result);
  assert.deepEqual([...f.db.store], before);
});
test("expired bracket session and payout reads stage no writes and preserve an already observed repair gap", async () => {
  for (const scenario of ["session", "payout", "repair"]) {
    const f = fixture(); await f.qualify(); const a = await f.readyBoth();
    f.state.now = f.db.store.get(`${P.sessions}/${a.sessions[0]}`).endsAtMs;
    if (scenario === "payout") for (let i = 0; i < 2; i++) await f.controller.session(a.pair[i],
      {kind: "ADVANCE", operationId: f.id(), sessionId: a.sessions[i]});
    if (scenario === "repair") f.db.store.delete(`${P.privateForms}/${EVENT}/forms/${digest(a.match.sharedForm.formId)}`);
    const before = clone([...f.db.store]); const deadlineAtMs = f.state.now + 10; const run = f.db.runTransaction;
    const blockedPath = scenario === "payout" ? null : `${P.sessions}/${a.sessions[scenario === "repair" ? 1 : 0]}`;
    let triggerCount = 0;
    f.db.runTransaction = body => run(tx => body({...tx, get: async ref => {
      const result = await tx.get(ref);
      if (ref.path === blockedPath || scenario === "payout" && ref.path.startsWith(`${P.operations}/`)) { triggerCount++; f.state.now = deadlineAtMs; }
      return result;
    }}));
    const result = await f.controller.reconcileEvent(EVENT, {deadlineAtMs});
    assert.equal(result.status, scenario === "repair" ? "PENDING_REPAIR" : "WAITING");
    assert.equal(result.reason, "INVOCATION_TIME"); assert.equal(result.done, false); assert.equal(triggerCount, 1);
    assert.equal(result.pendingRepairMatches, scenario === "repair" ? 1 : 0); assert.equal(result.transitions, 0);
    assert.deepEqual([...f.db.store], before, scenario); assertWorkerSafe(result);
  }
});

(async () => { for (const {name, body} of tests) { try { await body(); } catch (e) { console.error(`FAIL ${name}`); throw e; } }
  console.log(`course-tournament-controller: ${tests.length} behavioral cases passed`); })()
  .catch(error => { console.error(error); process.exitCode = 1; });
