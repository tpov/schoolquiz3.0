"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  createArenaSeasonWorker, WORKER_STATE_COLLECTION, WORKER_STATE_PATH: STATE,
  ACTIVE_STATUS, MAX_FINALIZATIONS, MAX_ADVANCES_PER_FINALIZATION, FUNCTION_TIMEOUT_MS, LEASE_MS,
} = require("./arena-season-worker");

const T = 1_800_000_000_000;
const quarterId = index => `2026-Q${index + 1}`;

function gate() {
  let release; const promise = new Promise(resolve => { release = resolve; });
  return {promise, release};
}
function fixture(count = 5) {
  const store = new Map(); const state = {now: T, calls: [], logs: [], queries: []};
  for (let index = 0; index < count; index++) store.set(`arena_seasons/${quarterId(index)}`, {status: ACTIVE_STATUS, private: "never inspect"});
  store.set("arena_seasons/old", {status: "FINALIZED", private: "not work"});
  const snapshot = path => ({id: path.split("/").at(-1), exists: store.has(path), data: () => structuredClone(store.get(path))});
  const doc = path => ({path, get: async () => snapshot(path)});
  const db = {
    doc,
    collection(path) {
      function query(filters = [], ordered = false, after = null, maximum = null) {
        return {
          where(field, op, value) { return query([...filters, {field, op, value}], ordered, after, maximum); },
          orderBy(field) { return query(filters, field, after, maximum); },
          startAfter(value) { return query(filters, ordered, value, maximum); },
          limit(value) { return query(filters, ordered, after, value); },
          async get() {
            assert.equal(path, "arena_seasons"); assert.deepEqual(filters, [{field: "status", op: "==", value: ACTIVE_STATUS}]);
            assert.equal(ordered, "__name__"); assert.ok(maximum >= 2 && maximum <= MAX_FINALIZATIONS + 1);
            state.queries.push({after, maximum});
            const docs = [...store.entries()].filter(([entry, value]) => entry.startsWith("arena_seasons/") &&
              entry.split("/").length === 2 && value.status === ACTIVE_STATUS &&
              (after === null || Buffer.compare(Buffer.from(entry.split("/").at(-1)), Buffer.from(after)) > 0))
              .sort(([left], [right]) => Buffer.compare(Buffer.from(left), Buffer.from(right))).slice(0, maximum)
              .map(([entry]) => ({id: entry.split("/").at(-1), data: () => { throw new Error("query row is not authority"); }}));
            return {docs};
          },
        };
      }
      return query();
    },
    async runTransaction(body) {
      const pending = [];
      const tx = {
        get: async ref => snapshot(ref.path),
        set: (ref, value) => pending.push([ref.path, structuredClone(value)]),
      };
      const result = await body(tx);
      for (const [path, value] of pending) store.set(path, value);
      return result;
    },
  };
  const worker = (extra = {}) => createArenaSeasonWorker({db, nowMs: () => state.now,
    // Most fixture tests isolate paging/fairness with one trusted step. A dedicated test below
    // covers the production multi-step batch bound.
    maxAdvancesPerFinalization: 1,
    log: row => state.logs.push(structuredClone(row)), advanceFinalization: async ({quarterId: id, deadlineAtMs}) => {
      state.calls.push({id, deadlineAtMs}); return {status: "SCAN_VOTES", quarterId: id, finalizationId: `job-${id}`};
    }, ...extra});
  return {store, state, worker, sweep: () => worker().sweep()};
}

test("server-only sweep pages finalizing seasons, stores a cursor, and never reads query payload", async () => {
  const f = fixture(); const result = await f.sweep();
  assert.equal(result.status, "PROCESSED"); assert.equal(result.attempted, 3); assert.equal(result.failed, 0);
  assert.deepEqual(f.state.calls.map(row => row.id), [0, 1, 2].map(quarterId));
  assert.equal(f.store.get(STATE).cursorAfterId, quarterId(2)); assert.equal(f.store.get(STATE).lease, null);
  assert.equal(WORKER_STATE_COLLECTION, "arena_season_worker_state");
  assert.ok(f.state.calls.every(row => row.deadlineAtMs === T + FUNCTION_TIMEOUT_MS));
});

test("cursor resumes then wraps without scanning historical terminal seasons", async () => {
  const f = fixture(); await f.sweep(); const second = await f.sweep();
  assert.equal(second.attempted, 2); assert.equal(second.wrapped, true); assert.equal(f.store.get(STATE).cursorAfterId, null);
  assert.deepEqual(f.state.calls.map(row => row.id), [0, 1, 2, 3, 4].map(quarterId));
  assert.ok(f.state.queries.every(query => query.maximum === MAX_FINALIZATIONS + 1));
});

test("one live sweep lease excludes a second invocation and stale safe errors do not leak", async () => {
  const f = fixture(1); const entered = gate(); const finish = gate();
  const first = f.worker({advanceFinalization: async ({quarterId: id, deadlineAtMs}) => {
    f.state.calls.push({id, deadlineAtMs}); entered.release(); await finish.promise;
    throw {code: "PRIVATE_SOURCE", message: "private value", stack: "private stack"};
  }}).sweep();
  await entered.promise; const held = f.store.get(STATE).lease.ownerNonce;
  const second = await f.sweep(); assert.equal(second.status, "BUSY"); assert.equal(f.store.get(STATE).lease.ownerNonce, held);
  finish.release(); const completed = await first;
  assert.equal(completed.status, "PROCESSED_WITH_ERRORS"); assert.deepEqual(f.state.logs[0], {
    component: "arena-season-sweep", code: "FINALIZATION_FAILED", seasonReference: f.state.logs[0].seasonReference,
  });
  assert.match(f.state.logs[0].seasonReference, /^[a-f0-9]{64}$/); assert.ok(!JSON.stringify(f.state.logs).includes("private"));
});

test("time budget and trusted operational limits cannot be widened by a caller", async () => {
  const f = fixture(); const limited = f.worker({advanceFinalization: async ({quarterId: id, deadlineAtMs}) => {
    f.state.calls.push({id, deadlineAtMs}); f.state.now += FUNCTION_TIMEOUT_MS;
    return {status: "SCAN_VOTES", quarterId: id, finalizationId: `job-${id}`};
  }});
  const result = await limited.sweep({uid: "client", maxFinalizations: 999, deadlineAtMs: Number.MAX_SAFE_INTEGER});
  assert.equal(result.status, "TIME_BUDGET"); assert.equal(result.attempted, 1);
  for (const options of [{maxFinalizations: 0}, {maxFinalizations: 4}, {maxAdvancesPerFinalization: 0},
    {maxAdvancesPerFinalization: MAX_ADVANCES_PER_FINALIZATION + 1}, {leaseMs: FUNCTION_TIMEOUT_MS}, {functionTimeoutMs: 0},
    {statePath: "arena_season_worker_state/"}, {statePath: "other_state/sweep"}, {statePath: "arena_season_worker_state/a/b"}]) {
    assert.throws(() => f.worker(options), {code: "INVALID_ARGUMENT"});
  }
  assert.ok(LEASE_MS > FUNCTION_TIMEOUT_MS);
});

test("bounded batches eliminate one-minute-per-page delay for cohorts above 501 rows", async () => {
  const f = fixture(1); let remaining = 502; const worker = f.worker({maxAdvancesPerFinalization: MAX_ADVANCES_PER_FINALIZATION,
    advanceFinalization: async ({quarterId: id, deadlineAtMs}) => {
      f.state.calls.push({id, deadlineAtMs});
      remaining--;
      return {status: remaining === 0 ? "PUBLISHED" : "SCAN_VOTES", quarterId: id, finalizationId: `job-${id}`};
    }});
  let sweeps = 0; let terminal = 0; let advanceCalls = 0;
  while (!terminal && sweeps < 20) {
    const result = await worker.sweep(); sweeps++; terminal += result.terminal; advanceCalls += result.advanceCalls;
    if (!terminal) assert.equal(result.advanceCalls, MAX_ADVANCES_PER_FINALIZATION);
  }
  assert.equal(terminal, 1); assert.equal(advanceCalls, 502);
  assert.equal(sweeps, Math.ceil(502 / MAX_ADVANCES_PER_FINALIZATION));
  assert.ok(sweeps < 20, "the bounded worker batch, not a one-minute cron tick, drives page progress");
});
