"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {createCourseTournamentController, WORKER_PREFIX} = require("./course-tournament-controller");
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
    assert.equal(ref.sort, "__name__"); assert.ok(Number.isInteger(ref.maximum) && ref.maximum >= 2 && ref.maximum <= 101);
    const rows = [...db.store].filter(([key, row]) => key.startsWith(`${ref.path}/`) && key.split("/").length === ref.path.split("/").length + 1 &&
      ref.filters.every(({field, op, value}) => op === "==" && row[field] === value) && (ref.after === null || key.split("/").at(-1) > ref.after))
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
  const db = pagedDb({"catalogs/catalog": {questType: "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: OWNER, version: 1, visibleOn: ["archive"], archived: true},
    "sections/section": {questId: "course", version: 1}, "themes/theme": {sectionId: "section", version: 1},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: T - 1},
    [`users/${OWNER}`]: {...wallet(), boxCount: 100}, ...Object.fromEntries(players.map(uid => [`users/${uid}`, wallet()]))});
  for (let i = 0; i < 80; i++) {
    const questionId = `q${String(i).padStart(3, "0")}`;
    db.store.set(`questions/${questionId}`, {lessonId: "lesson", version: 1, payload: JSON.stringify({id: questionId,
      type: "SingleChoice", difficulty: i < 60 ? "EASY" : "HARD", text: "A?", options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"})});
  }
  const state = {now: T}; let serial = 0;
  const controller = createCourseTournamentController({db, nowMs: () => state.now,
    isCurator: async () => false, workerBounds: options.workerBounds,
    loadLessonContext: async (tx, lessonId) => {
      const lesson = (await tx.get(db.doc(`lessons/${lessonId}`))).data();
      return {lessonId, authorUid: OWNER, catalogId: "catalog", questId: "course", sectionId: "section", themeId: "theme",
        questType: "COURSE", published: true, reviewApproved: lesson?.reviewSubmissionId === "submission", contentVersion: lesson?.version};
    }});
  const id = () => `client_operation_${String(++serial).padStart(16, "0")}`;
  const operate = (kind, uid = OWNER, fields = {}) => controller.operate(uid, {kind, operationId: id(), eventId: EVENT, ...fields});
  const prepare = () => controller.prepare(OWNER, {requestId: "preparation_request_00001", source: {kind: "COURSE", id: "course"}});
  async function create() {
    const p = await prepare();
    return operate("CREATE", OWNER, {source: {kind: "COURSE", id: "course", snapshotId: p.snapshotId}, startsAtMs: state.now,
      fundedBoxes: 12, prizes: {first: 5, second: 3, third: 1}});
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
  return {db, state, controller, id, operate, prepare, create, qualify, pending, match, agree, readyBoth};
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
  for (const [method, data] of [["prepare", {}], ["operate", {}], ["session", {}], ["readSession", {}], ["readEvent", {}], ["readMeeting", {}]]) await error(() => f.controller[method](null, data), "UNAUTHENTICATED");
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
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.status, "PENDING_SERVER_SESSIONS");
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
test("controller reports explicit freeze budget exhaustion without ranking, close or refund", async () => {
  const f = fixture(["alice", "bob", "carol"], {workerBounds: {qualificationPageSize: 1, qualificationMaxPages: 1}});
  const result = await f.qualify(); assert.equal(result.status, "SCAN_BUDGET"); assert.equal(result.done, false);
  assert.equal(result.needsLargerBudget, true); assert.equal(result.scannedSessions, 1); assert.equal(balance(f, OWNER), 88);
  assert.equal(f.db.store.has(`${QUALIFICATION_RANKINGS}/${EVENT}`), false); assert.equal(workerReceipts(f).length, 0);
});
test("missing admitted qualification session cannot silently disappear from automatic freeze", async () => {
  const f = fixture(); await f.create(); const start = await f.operate("START_QUALIFICATION", "alice", {roundNumber: 1, attemptId: "attempt_alice_00000001"});
  f.db.store.delete(`${P.sessions}/${start.session.sessionId}`); f.state.now = T + 7 * DAY;
  const result = await f.controller.reconcileEvent(EVENT); assert.equal(result.status, "PENDING_SERVER_SESSIONS");
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

(async () => { for (const {name, body} of tests) { try { await body(); } catch (e) { console.error(`FAIL ${name}`); throw e; } }
  console.log(`course-tournament-controller: ${tests.length} behavioral cases passed`); })()
  .catch(error => { console.error(error); process.exitCode = 1; });
