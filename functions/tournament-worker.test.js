"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {TOURNAMENT_PATHS} = require("./tournament-service");
const {createTournamentWorker, WORKER_STATE_PATH: S, WORKER_STATE_COLLECTION, ACTIVE_STATUSES, FUNCTION_TIMEOUT_MS, LEASE_MS} = require("./tournament-worker");

const T = 1_800_000_000_000; const tests = [];
function test(name, body) { tests.push({name, body}); }
function gate() { let release; const promise = new Promise(resolve => { release = resolve; }); return {promise, release}; }
const eventId = index => `event_${String(index).padStart(20, "0")}`;
const eventPath = id => `${TOURNAMENT_PATHS.events}/${id}`;
function fixture(count = 7, config = {}) {
  const initial = Object.fromEntries(Array.from({length: count}, (_, index) => [eventPath(eventId(index)),
    {eventId: eventId(index), status: index % 2 ? "BRACKET" : "QUALIFICATION", secret: "must not inspect or log"}]));
  const db = premiumTestDb(initial); const state = {now: T, failQuery: false, badQuery: false, calls: [], logs: [], queries: []};
  function query(path, filters = [], sort = null, after = null, maximum = null) {
    return {where: (field, op, value) => query(path, [...filters, {field, op, value}], sort, after, maximum),
      orderBy: field => query(path, filters, field, after, maximum), startAfter: id => query(path, filters, sort, id, maximum),
      limit: n => query(path, filters, sort, after, n), async get() {
        assert.equal(path, TOURNAMENT_PATHS.events); assert.deepEqual(filters, [{field: "status", op: "in", value: ACTIVE_STATUSES}]);
        assert.equal(sort, "__name__"); assert.ok(maximum >= 2 && maximum <= 4);
        state.queries.push({path, filters, sort, after, maximum});
        if (state.failQuery) { state.failQuery = false; throw Error("private bank secret in provider error"); }
        const selected = [...db.store].filter(([p, row]) => p.startsWith(`${path}/`) && p.split("/").length === 2 &&
          ACTIVE_STATUSES.includes(row.status) && (after === null || Buffer.compare(Buffer.from(p.split("/").at(-1)), Buffer.from(after)) > 0))
          .sort(([a], [b]) => Buffer.compare(Buffer.from(a), Buffer.from(b))).slice(0, maximum);
        const docs = selected.map(([p]) => ({id: p.split("/").at(-1), data: () => { throw Error("query contents must not be used as authority"); }}));
        return {docs: state.badQuery ? [...docs, ...docs] : docs};
      }};
  }
  db.collection = path => query(path);
  const reconcile = async id => { state.calls.push(id); return {eventId: id, done: false, status: "WAITING"}; };
  const worker = extra => createTournamentWorker({db, nowMs: () => state.now, reconcileEvent: reconcile,
    log: row => state.logs.push(structuredClone(row)), ...config, ...extra});
  return {db, state, worker, sweep: () => worker().sweep()};
}
function assertOnlyStateWrites(f) { assert.ok(f.db.commits.flat().every(row => row.path === S), "worker must never write event/user/source clocks"); }
const error = (fn, code) => assert.rejects(fn, e => { assert.equal(e.code, code); return true; });

test("active ownerless page is exactly status-in/docid/limit4; at most3 callbacks and durable cursor", async () => {
  const f = fixture(); const result = await f.sweep();
  assert.equal(result.attempted, 3); assert.equal(result.queryCount, 1); assert.equal(result.status, "PROCESSED");
  assert.deepEqual(f.state.calls, [0, 1, 2].map(eventId)); assert.equal(f.db.store.get(S).cursorAfterId, eventId(2));
  assert.equal(f.db.store.get(S).lease, null); assert.equal(WORKER_STATE_COLLECTION, "course_tournament_worker_state"); assertOnlyStateWrites(f);
});
test("durable fairness progresses subsequent pages then wraps without a full-history scan", async () => {
  const f = fixture(); await f.sweep(); await f.sweep(); const third = await f.sweep();
  assert.deepEqual(f.state.calls, [0, 1, 2, 3, 4, 5, 6].map(eventId)); assert.equal(third.attempted, 1);
  assert.equal(third.wrapped, true); assert.equal(f.db.store.get(S).cursorAfterId, null); assert.equal(f.db.store.get(S).cycle, 1);
  await f.sweep(); assert.deepEqual(f.state.calls.slice(-3), [0, 1, 2].map(eventId)); assert.ok(f.state.queries.every(q => q.maximum === 4));
});
test("exactly3 rows without lookahead reset cursor for next invocation", async () => {
  const f = fixture(3); const result = await f.sweep(); assert.equal(result.attempted, 3); assert.equal(result.wrapped, true);
  assert.equal(f.db.store.get(S).cursorAfterId, null); assert.equal(result.queryCount, 1);
});
test("ended events/history are excluded even with no organizer field", async () => {
  const f = fixture(2);
  for (let i = 100; i < 1100; i++) f.db.store.set(eventPath(eventId(i)), {status: i % 2 ? "FINISHED" : "CANCELLED"});
  f.db.store.set(eventPath(eventId(1101)), {status: "OTHER"}); const result = await f.sweep();
  assert.equal(result.attempted, 2); assert.deepEqual(f.state.calls, [eventId(0), eventId(1)]); assertOnlyStateWrites(f);
});
test("empty active set is IDLE, not a claim that all past/future tournaments completed", async () => {
  const f = fixture(0); const result = await f.sweep(); assert.equal(result.status, "IDLE"); assert.equal(result.attempted, 0);
  assert.equal(result.queryCount, 1); assert.equal(Object.hasOwn(result, "done"), false); assert.equal(f.db.store.get(S).lease, null);
});
test("empty tail wraps durably then queries head at most once, even when cursor document disappeared", async () => {
  const f = fixture(2); f.db.store.set(S, {schemaVersion: 1, cursorAfterId: eventId(99), cycle: 4, lease: null});
  const result = await f.sweep(); assert.equal(result.queryCount, 2); assert.equal(result.attempted, 2); assert.equal(result.wrapped, true);
  assert.deepEqual(f.state.queries.map(row => row.after), [eventId(99), null]); assert.equal(f.db.store.get(S).cursorAfterId, null);
});
test("empty tail plus empty head terminates after2 queries without looping", async () => {
  const f = fixture(0); f.db.store.set(S, {schemaVersion: 1, cursorAfterId: eventId(99), cycle: 0, lease: null});
  const result = await f.sweep(); assert.equal(result.queryCount, 2); assert.equal(result.status, "IDLE"); assert.equal(result.attempted, 0);
});
test("new event behind current cursor is visited after the finite current cycle wraps", async () => {
  const f = fixture(5); await f.sweep(); const earlier = "aaa_new_event_0000001"; f.db.store.set(eventPath(earlier), {status: "QUALIFICATION"});
  await f.sweep(); await f.sweep(); assert.ok(f.state.calls.includes(earlier)); assert.equal(f.state.calls.indexOf(earlier), 5);
});
test("per-event exception advances cursor, logs safe code/reference and cannot starve later events", async () => {
  const f = fixture(); const visited = [];
  const w = f.worker({reconcileEvent: async id => { visited.push(id); if (id === eventId(0)) throw Object.assign(Error("answerKey secret"), {code: "INVALID_SOURCE"});
    return {eventId: id, done: false, status: "WAITING"}; }});
  const result = await w.sweep(); assert.equal(result.failed, 1); assert.equal(result.status, "PROCESSED_WITH_ERRORS");
  assert.deepEqual(visited, [0, 1, 2].map(eventId)); assert.equal(f.db.store.get(S).cursorAfterId, eventId(2));
  assert.deepEqual(Object.keys(f.state.logs[0]).sort(), ["code", "component", "eventReference"]);
  assert.equal(f.state.logs[0].code, "INVALID_SOURCE"); assert.ok(!JSON.stringify(f.state.logs).includes("answerKey"));
  await w.sweep(); assert.deepEqual(visited.slice(-3), [3, 4, 5].map(eventId));
});
test("untrusted error text/code/stack never enters log records", async () => {
  const f = fixture(1); await f.worker({reconcileEvent: async () => { throw {code: "secret token: a-b.c", message: "private answer", stack: "private stack"}; }}).sweep();
  assert.equal(f.state.logs[0].code, "RECONCILE_FAILED"); assert.ok(!JSON.stringify(f.state.logs).includes("private")); assert.ok(!JSON.stringify(f.state.logs).includes("secret"));
});
test("repair/scan-budget results remain retryable diagnostics, not completed events", async () => {
  const f = fixture(3); const statuses = ["PENDING_REPAIR", "SCAN_BUDGET", "PREMIUM_CHECKPOINT"];
  const result = await f.worker({reconcileEvent: async id => ({eventId: id, done: false, status: statuses[Number(id.slice(-1))]})}).sweep();
  assert.equal(result.failed, 3); assert.equal(result.completedEvents, 0); assert.equal(f.state.logs.length, 3); assert.equal(result.wrapped, true);
});
test("malformed/misbound controller results are errors and still advance cursor", async () => {
  const f = fixture(3); const results = [null, {done: true, status: "FINISHED", eventId: "foreign"}, {eventId: eventId(2), status: "WAITING"}];
  const result = await f.worker({reconcileEvent: async id => results[Number(id.slice(-1))]}).sweep();
  assert.equal(result.failed, 3); assert.equal(result.completedEvents, 0); assert.equal(f.db.store.get(S).cursorAfterId, null);
});
test("a throwing diagnostic sink cannot undo checkpoints or stop following events", async () => {
  const f = fixture(); const visited = [];
  const result = await f.worker({log: () => { throw Error("logger unavailable"); }, reconcileEvent: async id => { visited.push(id); throw Error("failed"); }}).sweep();
  assert.equal(result.failed, 3); assert.equal(visited.length, 3); assert.equal(f.db.store.get(S).cursorAfterId, eventId(2));
});
test("one active lease excludes a second invocation until holder releases", async () => {
  const f = fixture(); const entered = gate(); const finish = gate();
  const first = f.worker({reconcileEvent: async id => { entered.release(); await finish.promise; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  await entered.promise; const nonce = f.db.store.get(S).lease.ownerNonce; assert.match(nonce, /^[a-f0-9]{48}$/);
  const second = await f.sweep(); assert.equal(second.status, "BUSY"); assert.equal(second.attempted, 0); assert.equal(f.state.queries.length, 1);
  assert.equal(f.db.store.get(S).lease.ownerNonce, nonce); finish.release(); await first; assert.equal(f.db.store.get(S).lease, null);
});
test("lease outlasts configured request timeout; equality expiry permits takeover, not process termination", async () => {
  const f = fixture(); const entered = gate(); const finish = gate(); const running = f.worker({reconcileEvent: async id => {
    entered.release(); await finish.promise; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  await entered.promise; const lease = structuredClone(f.db.store.get(S).lease);
  assert.equal(lease.expiresAtMs - lease.acquiredAtMs, LEASE_MS); assert.ok(LEASE_MS > FUNCTION_TIMEOUT_MS);
  f.state.now = lease.expiresAtMs; assert.notEqual((await f.sweep()).status, "BUSY"); finish.release();
  assert.equal((await running).status, "LEASE_LOST");
});
test("expired old worker cannot checkpoint or release a newer still-active lease", async () => {
  const f = fixture(); const enteredOld = gate(); const finishOld = gate(); const enteredNew = gate(); const finishNew = gate();
  const old = f.worker({reconcileEvent: async id => { enteredOld.release(); await finishOld.promise; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  await enteredOld.promise; const oldNonce = f.db.store.get(S).lease.ownerNonce; f.state.now += LEASE_MS + 1;
  const current = f.worker({reconcileEvent: async id => { enteredNew.release(); await finishNew.promise; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  await enteredNew.promise; const stateBefore = structuredClone(f.db.store.get(S)); assert.notEqual(stateBefore.lease.ownerNonce, oldNonce);
  finishOld.release(); const oldResult = await old; assert.equal(oldResult.status, "LEASE_LOST"); assert.equal(oldResult.attempted, 1);
  assert.deepEqual(f.db.store.get(S), stateBefore); finishNew.release(); await current;
});
test("expiry without takeover still prohibits old checkpoints/releases and further events", async () => {
  const f = fixture(); const before = [];
  const result = await f.worker({reconcileEvent: async id => { before.push(id); f.state.now += LEASE_MS; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  assert.equal(result.status, "LEASE_LOST"); assert.equal(result.attempted, 1); assert.equal(result.leaseReleased, false);
  assert.equal(f.db.store.get(S).cursorAfterId, null); assert.ok(f.db.store.get(S).lease); assert.equal(before.length, 1);
});
test("function time budget stops launching more events but keeps completed cursor progress", async () => {
  const f = fixture(); const result = await f.worker({reconcileEvent: async id => { f.state.now += FUNCTION_TIMEOUT_MS; return {eventId: id, done: false, status: "WAITING"}; }}).sweep();
  assert.equal(result.status, "TIME_BUDGET"); assert.equal(result.attempted, 1); assert.equal(result.leaseReleased, true);
  assert.equal(f.db.store.get(S).cursorAfterId, eventId(0));
});
test("every controller receives the same trusted sweep deadline, not a fresh budget per event", async () => {
  const f = fixture(3); const calls = [];
  const result = await f.worker({reconcileEvent: async (id, execution) => {
    calls.push({id, execution});
    f.state.now += 1000;
    return {eventId: id, done: false, status: "WAITING"};
  }}).sweep();
  assert.equal(result.attempted, 3);
  assert.deepEqual(calls, [0, 1, 2].map(index => ({id: eventId(index),
    execution: {deadlineAtMs: T + FUNCTION_TIMEOUT_MS}})));
  assertOnlyStateWrites(f);
});
test("authorization read reaching the time limit starts no controller work and releases only its lease", async () => {
  for (const elapsed of [FUNCTION_TIMEOUT_MS, FUNCTION_TIMEOUT_MS + 1]) {
    const f = fixture(); const originalDoc = f.db.doc;
    f.db.doc = path => {
      const ref = originalDoc(path);
      if (path !== S) return ref;
      return {...ref, get: async () => { const snapshot = await ref.get(); f.state.now += elapsed; return snapshot; }};
    };
    const result = await f.sweep();
    assert.equal(result.status, "TIME_BUDGET"); assert.equal(result.attempted, 0);
    assert.deepEqual(f.state.calls, []); assert.equal(result.leaseReleased, true);
    assert.equal(f.db.store.get(S).cursorAfterId, null); assert.equal(f.db.store.get(S).lease, null);
    assertOnlyStateWrites(f);
  }
});
test("acquisition provider failure is sanitized before the scheduler logger and retry keeps the cursor", async () => {
  const f = fixture(); const originalRun = f.db.runTransaction; let first = true;
  f.db.runTransaction = body => {
    if (first) { first = false; return Promise.reject(Object.assign(Error("private provider message with lease nonce"), {code: "PRIVATE_PROVIDER_CODE"})); }
    return originalRun(body);
  };
  await assert.rejects(() => f.sweep(), e => {
    assert.equal(e.code, "UNAVAILABLE"); assert.equal(e.message, "Tournament sweep storage unavailable; retry safely after lease expiry or release");
    return true;
  });
  assert.deepEqual(f.state.logs, [{component: "course-tournament-sweep", code: "SWEEP_STORAGE_FAILED"}]);
  assert.equal(f.db.store.has(S), false); assert.deepEqual(f.state.calls, []);
  await f.sweep(); assert.deepEqual(f.state.calls, [0, 1, 2].map(eventId));
});
test("uncertain committed acquisition preserves the lease and resumes its cursor after expiry", async () => {
  const f = fixture(); f.db.store.set(S, {schemaVersion: 1, cursorAfterId: eventId(2), cycle: 0, lease: null});
  const originalRun = f.db.runTransaction; let first = true;
  f.db.runTransaction = async body => {
    const result = await originalRun(body);
    if (first) { first = false; throw Error("provider lost committed acquisition response"); }
    return result;
  };
  await error(() => f.sweep(), "UNAVAILABLE");
  const held = structuredClone(f.db.store.get(S)); assert.ok(held.lease); assert.equal(held.cursorAfterId, eventId(2));
  assert.equal((await f.sweep()).status, "BUSY"); assert.deepEqual(f.db.store.get(S), held);
  assert.deepEqual(f.state.calls, []); f.state.now = held.lease.expiresAtMs;
  await f.sweep(); assert.deepEqual(f.state.calls, [3, 4, 5].map(eventId));
});
test("failure after reconcile before checkpoint repeats one event safely; no skipped successors", async () => {
  const f = fixture(); const originalRun = f.db.runTransaction; let failCheckpoint = true; const applied = new Set(); const calls = [];
  f.db.runTransaction = body => originalRun(tx => body({...tx, set: (ref, row, options) => {
    if (failCheckpoint && row.cursorAfterId === eventId(0)) { failCheckpoint = false; f.db.failNextCommit(); }
    return tx.set(ref, row, options);
  }}));
  const w = f.worker({reconcileEvent: async id => { calls.push(id); applied.add(id); return {eventId: id, done: false, status: "WAITING"}; }});
  await error(() => w.sweep(), "UNAVAILABLE"); assert.equal(f.db.store.get(S).cursorAfterId, null); assert.equal(f.db.store.get(S).lease, null);
  await w.sweep(); assert.deepEqual(calls, [0, 0, 1, 2].map(eventId)); assert.equal(applied.size, 3); assert.equal(f.db.store.get(S).cursorAfterId, eventId(2));
});
test("failure after checkpoint before release preserves cursor and next holder continues after TTL", async () => {
  const f = fixture(); const run = f.db.runTransaction; let injected = false;
  f.db.runTransaction = body => run(tx => body({...tx, set: (ref, row, options) => {
    if (!injected && row.lease === null) { injected = true; f.db.failNextCommit(); } return tx.set(ref, row, options);
  }}));
  await error(() => f.sweep(), "UNAVAILABLE"); assert.equal(f.db.store.get(S).cursorAfterId, eventId(2));
  assert.equal((await f.sweep()).status, "BUSY"); f.state.now += LEASE_MS; await f.sweep();
  assert.deepEqual(f.state.calls, [0, 1, 2, 3, 4, 5].map(eventId));
});
test("query failure releases owned lease and retries same cursor without logging provider payload", async () => {
  const f = fixture(); f.state.failQuery = true; await error(() => f.sweep(), "UNAVAILABLE");
  assert.equal(f.db.store.get(S).cursorAfterId, null); assert.equal(f.db.store.get(S).lease, null);
  assert.ok(!JSON.stringify(f.state.logs).includes("secret")); await f.sweep(); assert.deepEqual(f.state.calls, [0, 1, 2].map(eventId));
});
test("failure on head query preserves durable wrap and retry starts from head directly", async () => {
  const f = fixture(2); f.db.store.set(S, {schemaVersion: 1, cursorAfterId: eventId(99), cycle: 2, lease: null});
  const run = f.db.runTransaction; let wrapped = false;
  f.db.runTransaction = body => run(tx => body({...tx, set: (ref, row, options) => {
    if (!wrapped && row.cursorAfterId === null) { wrapped = true; f.state.failQuery = true; } return tx.set(ref, row, options);
  }}));
  await error(() => f.sweep(), "UNAVAILABLE"); assert.equal(f.db.store.get(S).cursorAfterId, null);
  const result = await f.sweep(); assert.equal(result.queryCount, 1); assert.equal(result.attempted, 2);
});
test("invalid/malformed worker state fails closed without overwriting cursor or lease", async () => {
  const f = fixture(); f.db.store.set(S, {schemaVersion: 1, cursorAfterId: "../wrong/path", cycle: 0, lease: null});
  const before = structuredClone(f.db.store.get(S)); await error(() => f.sweep(), "UNAVAILABLE"); assert.deepEqual(f.db.store.get(S), before);
});
test("operational limits are trusted-only and max3/lease-after-timeout invariants are mandatory", async () => {
  const f = fixture();
  for (const limits of [{maxEvents: 0}, {maxEvents: 4}, {maxEvents: 1.5}, {leaseMs: 540000}, {leaseMs: 100}, {functionTimeoutMs: 0}]) {
    assert.throws(() => f.worker(limits), {code: "INVALID_ARGUMENT"});
  }
  const result = await f.worker({maxEvents: 1}).sweep({maxEvents: 1000, uid: "untrusted"});
  assert.equal(result.attempted, 1); assert.equal(f.state.queries[0].maximum, 2);
});
test("Unicode document ordering can checkpoint invalid lifecycle IDs instead of starving the page", async () => {
  const f = fixture(0); for (const id of ["\uE000", "😀", "z"]) f.db.store.set(eventPath(id), {status: "BRACKET"});
  const seen = []; const result = await f.worker({reconcileEvent: async id => { seen.push(id); throw Object.assign(Error("invalid id"), {code: "INVALID_ARGUMENT"}); }}).sweep();
  assert.deepEqual(seen, ["z", "\uE000", "😀"]); assert.equal(result.failed, 3); assert.equal(result.wrapped, true);
});
test("controller completion is counted only per-event; sweep never claims global completion", async () => {
  const f = fixture(4); const result = await f.worker({reconcileEvent: async id => ({eventId: id, done: true, status: "FINISHED"})}).sweep();
  assert.equal(result.completedEvents, 3); assert.equal(result.cursorAfterId, eventId(2)); assert.equal(Object.hasOwn(result, "done"), false); assertOnlyStateWrites(f);
});
test("contradictory done/status and unknown future status cannot silently count as completion", async () => {
  const f = fixture(3); const result = await f.worker({reconcileEvent: async id => ({eventId: id,
    done: id === eventId(0), status: id === eventId(0) ? "WAITING" : id === eventId(1) ? "FINISHED" : "UNKNOWN_STATE"})}).sweep();
  assert.equal(result.completedEvents, 0); assert.equal(result.failed, 3);
  assert.ok(f.state.logs.every(row => row.code === "INVALID_RECONCILE_RESULT"));
});
test("arbitrary uppercase error codes are redacted rather than becoming a private-content log channel", async () => {
  const f = fixture(1); await f.worker({reconcileEvent: async () => { throw {code: "THE_SECRET_ANSWER_IS_A"}; }}).sweep();
  assert.equal(f.state.logs[0].code, "RECONCILE_FAILED"); assert.ok(!JSON.stringify(f.state.logs).includes("SECRET"));
});
test("expired event snapshot becoming terminal is reconciled from current state, never mutated by sweep", async () => {
  const f = fixture(1); const result = await f.worker({reconcileEvent: async id => {
    f.db.store.get(eventPath(id)).status = "FINISHED";
    return {eventId: id, done: true, status: "FINISHED"};
  }}).sweep();
  assert.equal(result.completedEvents, 1); const next = await f.sweep(); assert.equal(next.status, "IDLE"); assertOnlyStateWrites(f);
});

(async () => { for (const {name, body} of tests) { try { await body(); } catch (e) { console.error(`FAIL ${name}`); throw e; } }
  console.log(`tournament-worker: ${tests.length} behavioral cases passed`); })().catch(error => { console.error(error); process.exitCode = 1; });
