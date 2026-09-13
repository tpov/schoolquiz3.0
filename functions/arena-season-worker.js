"use strict";

// Durable, server-only scheduler for bounded steps of Arena season finalizations. It accepts no
// uid, callable body, cursor, finalization ID, page limit, or wall-clock value from a client.

const {createHash, randomBytes} = require("node:crypto");

const WORKER_STATE_COLLECTION = "arena_season_worker_state";
const WORKER_STATE_PATH = `${WORKER_STATE_COLLECTION}/sweep`;
const ACTIVE_STATUS = "FINALIZING";
const MAX_FINALIZATIONS = 3;
// One finalization step owns at most PAGE_SIZE documents. Repeating a bounded number of steps
// under the already-held sweep lease avoids turning a 502-row season into one minute per page,
// while preserving fair round-robin progress between active quarters.
const MAX_ADVANCES_PER_FINALIZATION = 64;
const FUNCTION_TIMEOUT_MS = 540_000;
const LEASE_MS = 660_000;
const ADVANCE_STATUSES = new Set([
  "SCAN_MEMBERS", "VERIFY_MEMBERS", "SCAN_VOTES", "SELECT", "VERIFY_SELECTION", "COMPARE_SELECTION",
  "AGGREGATE", "VERIFY_AGGREGATES", "COMPARE_AGGREGATES", "PICK_WINNER", "VERIFY_CHECKPOINTS", "PUBLISH",
  "PUBLISHED", "REQUIRES_REVIEW", "BUSY", "TIME_BUDGET", "LEASE_LOST",
]);
const SAFE_ERROR_CODES = new Set(["UNAVAILABLE", "INVALID_SOURCE", "INVALID_ARGUMENT", "INCONSISTENT_MUTATION"]);

function fail(code, message = code) { const error = new Error(message); error.code = code; throw error; }
function exact(value, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", "Expected a safe non-negative integer");
  return value;
}
function add(left, right) {
  const result = BigInt(exact(left)) + BigInt(exact(right));
  if (result > BigInt(Number.MAX_SAFE_INTEGER)) fail("INVALID_ARGUMENT", "Worker counter overflow");
  return Number(result);
}
function documentId(value) {
  if (typeof value !== "string" || !value || value.includes("/") || value === "." || value === ".." ||
      Buffer.byteLength(value) > 1500) fail("UNAVAILABLE", "Worker cursor is invalid");
  return value;
}
function statePath(value) {
  if (typeof value !== "string") fail("INVALID_ARGUMENT", "Worker state path is invalid");
  const [collection, id, ...rest] = value.split("/");
  if (collection !== WORKER_STATE_COLLECTION || rest.length !== 0 || typeof id !== "string" || !id ||
      id.includes("/") || id === "." || id === ".." || Buffer.byteLength(id) > 1500) {
    fail("INVALID_ARGUMENT", "Worker state path is outside its private root");
  }
  return value;
}
function data(snapshot) { return snapshot && snapshot.exists ? snapshot.data() || {} : null; }
function stateRecord(raw) {
  if (!raw) return {schemaVersion: 1, cursorAfterId: null, cycle: 0, lease: null};
  if (raw.schemaVersion !== 1 || (raw.cursorAfterId !== null && typeof raw.cursorAfterId !== "string") ||
      !Number.isSafeInteger(raw.cycle) || raw.cycle < 0) fail("UNAVAILABLE", "Arena season worker state requires repair");
  if (raw.cursorAfterId !== null) documentId(raw.cursorAfterId);
  if (raw.lease !== null && (!raw.lease || !/^[a-f0-9]{48}$/.test(raw.lease.ownerNonce || "") ||
      !Number.isSafeInteger(raw.lease.acquiredAtMs) || raw.lease.acquiredAtMs < 0 ||
      !Number.isSafeInteger(raw.lease.expiresAtMs) || raw.lease.expiresAtMs <= raw.lease.acquiredAtMs)) {
    fail("UNAVAILABLE", "Arena season worker lease requires repair");
  }
  return raw;
}
function safeCode(error) { return SAFE_ERROR_CODES.has(error?.code) ? error.code : "FINALIZATION_FAILED"; }

function createArenaSeasonWorker({db, advanceFinalization, nowMs = Date.now,
  log = record => console.warn(JSON.stringify(record)), maxFinalizations = MAX_FINALIZATIONS,
  maxAdvancesPerFinalization = MAX_ADVANCES_PER_FINALIZATION,
  functionTimeoutMs = FUNCTION_TIMEOUT_MS, leaseMs = LEASE_MS, statePath: trustedStatePath = WORKER_STATE_PATH}) {
  if (!db || typeof db.runTransaction !== "function" || typeof advanceFinalization !== "function" ||
      typeof nowMs !== "function" || typeof log !== "function") throw new TypeError("Trusted Arena season worker dependencies required");
  exact(maxFinalizations, 1); exact(maxAdvancesPerFinalization, 1); exact(functionTimeoutMs, 1); exact(leaseMs, 1);
  if (maxFinalizations > MAX_FINALIZATIONS || maxAdvancesPerFinalization > MAX_ADVANCES_PER_FINALIZATION ||
      leaseMs <= functionTimeoutMs) {
    fail("INVALID_ARGUMENT", "Arena season worker bounds are invalid");
  }
  // Only server composition may inject an isolated state document for an Emulator test. The
  // public scheduler always retains WORKER_STATE_PATH; no callable can supply this value.
  const ref = db.doc(statePath(trustedStatePath));
  const now = () => exact(nowMs());
  const owns = (state, nonce, at) => state.lease?.ownerNonce === nonce && state.lease.expiresAtMs > at;
  function report(quarterId, code) {
    // Do not log a job, result, source row, cursor/lease, SDK message, or stack. The hash only
    // lets an operator correlate retries without exposing a course/lesson identity.
    const record = {component: "arena-season-sweep", code,
      ...(quarterId === null ? {} : {seasonReference: createHash("sha256").update(quarterId).digest("hex")})};
    try { log(record); } catch { /* A diagnostic sink cannot undo durable work. */ }
  }
  async function acquire(nonce) {
    return db.runTransaction(async tx => {
      const state = stateRecord(data(await tx.get(ref))); const at = now();
      if (state.lease && state.lease.expiresAtMs > at) return {acquired: false, retryAtMs: state.lease.expiresAtMs};
      tx.set(ref, {...state, lease: {ownerNonce: nonce, acquiredAtMs: at, expiresAtMs: add(at, leaseMs)}, updatedAtMs: at});
      return {acquired: true, cursorAfterId: state.cursorAfterId};
    });
  }
  async function checkpoint(nonce, cursorAfterId, wrap = false) {
    if (cursorAfterId !== null) documentId(cursorAfterId);
    return db.runTransaction(async tx => {
      const state = stateRecord(data(await tx.get(ref))); const at = now();
      if (!owns(state, nonce, at)) return false;
      tx.set(ref, {...state, cursorAfterId, cycle: wrap ? add(state.cycle, 1) : state.cycle, updatedAtMs: at});
      return true;
    });
  }
  async function release(nonce) {
    return db.runTransaction(async tx => {
      const state = stateRecord(data(await tx.get(ref))); const at = now();
      if (!owns(state, nonce, at)) return false;
      tx.set(ref, {...state, lease: null, updatedAtMs: at});
      return true;
    });
  }
  async function authorized(nonce) { return owns(stateRecord(data(await ref.get())), nonce, now()); }
  async function page(cursorAfterId) {
    let query = db.collection("arena_seasons").where("status", "==", ACTIVE_STATUS).orderBy("__name__");
    if (cursorAfterId !== null) query = query.startAfter(cursorAfterId);
    const snapshot = await query.limit(maxFinalizations + 1).get();
    if (!snapshot || !Array.isArray(snapshot.docs) || snapshot.docs.length > maxFinalizations + 1) {
      fail("UNAVAILABLE", "Arena season worker page is malformed");
    }
    let previous = cursorAfterId;
    for (const document of snapshot.docs) {
      const id = documentId(document.id);
      if (previous !== null && Buffer.compare(Buffer.from(id), Buffer.from(previous)) <= 0) {
        fail("UNAVAILABLE", "Arena season worker cursor did not advance");
      }
      previous = id;
    }
    // The query only picks work. `advanceFinalization` re-reads and validates the matching
    // season/job fence itself; the scheduler never treats a query row as publication authority.
    return {quarterIds: snapshot.docs.slice(0, maxFinalizations).map(doc => doc.id), hasMore: snapshot.docs.length > maxFinalizations};
  }
  async function sweep() {
    const startedAtMs = now(); const deadlineAtMs = add(startedAtMs, functionTimeoutMs); const nonce = randomBytes(24).toString("hex");
    let acquired;
    try { acquired = await acquire(nonce); }
    catch {
      report(null, "SWEEP_STORAGE_FAILED");
      fail("UNAVAILABLE", "Arena season sweep storage unavailable; retry safely after lease expiry or release");
    }
    if (!acquired.acquired) return {status: "BUSY", attempted: 0, retryAtMs: acquired.retryAtMs};
    let cursorAfterId = acquired.cursorAfterId; let attempted = 0; let advanceCalls = 0; let failed = 0; let terminal = 0;
    let wrapped = false; let queryCount = 0; let status = "IDLE"; let failure = null;
    try {
      let next = await page(cursorAfterId); queryCount++;
      if (!next.quarterIds.length && cursorAfterId !== null) {
        if (!await checkpoint(nonce, null, true)) status = "LEASE_LOST";
        else { cursorAfterId = null; wrapped = true; next = await page(null); queryCount++; }
      }
      if (status !== "LEASE_LOST") {
        for (let index = 0; index < next.quarterIds.length; index++) {
          if (now() >= deadlineAtMs) { status = "TIME_BUDGET"; break; }
          if (!await authorized(nonce)) { status = "LEASE_LOST"; break; }
          if (now() >= deadlineAtMs) { status = "TIME_BUDGET"; break; }
          const quarterId = next.quarterIds[index]; attempted++; let errorCode = null; let stopSweep = false;
          for (let step = 0; step < maxAdvancesPerFinalization; step++) {
            if (now() >= deadlineAtMs) { status = "TIME_BUDGET"; stopSweep = true; break; }
            if (!await authorized(nonce)) { status = "LEASE_LOST"; stopSweep = true; break; }
            if (now() >= deadlineAtMs) { status = "TIME_BUDGET"; stopSweep = true; break; }
            try {
              const result = await advanceFinalization({quarterId, deadlineAtMs}); advanceCalls++;
              if (!result || result.quarterId !== quarterId || !ADVANCE_STATUSES.has(result.status)) {
                errorCode = "INVALID_ADVANCE_RESULT"; break;
              }
              if (["PUBLISHED", "REQUIRES_REVIEW"].includes(result.status)) { terminal++; break; }
              if (result.status === "TIME_BUDGET") { status = "TIME_BUDGET"; stopSweep = true; break; }
              // A concurrent direct worker owns its own job lease; don't spin on it. The sweep
              // checkpoint still advances fairly to the next quarter and a later cycle retries.
              if (["BUSY", "LEASE_LOST"].includes(result.status)) break;
            } catch (error) { errorCode = safeCode(error); break; }
          }
          if (status === "LEASE_LOST") {
            if (errorCode) report(quarterId, errorCode);
            break;
          }
          if (errorCode) failed++;
          const endOfCycle = index === next.quarterIds.length - 1 && !next.hasMore;
          const nextCursor = endOfCycle ? null : quarterId;
          if (!await checkpoint(nonce, nextCursor, endOfCycle)) {
            if (errorCode) report(quarterId, errorCode);
            status = "LEASE_LOST"; break;
          }
          cursorAfterId = nextCursor; wrapped = wrapped || endOfCycle;
          if (errorCode) report(quarterId, errorCode);
          if (status !== "TIME_BUDGET") status = failed ? "PROCESSED_WITH_ERRORS" : "PROCESSED";
          if (stopSweep) break;
        }
      }
    } catch (error) {
      failure = error; report(null, "SWEEP_STORAGE_FAILED");
    }
    let leaseReleased = false;
    try { leaseReleased = await release(nonce); }
    catch (error) { failure ||= error; report(null, "SWEEP_RELEASE_FAILED"); }
    if (failure) fail("UNAVAILABLE", "Arena season sweep storage unavailable; retry safely after lease expiry or release");
    if (!leaseReleased) status = "LEASE_LOST";
    return {status, attempted, advanceCalls, failed, terminal, queryCount, wrapped, cursorAfterId, leaseReleased};
  }
  return {sweep};
}

module.exports = {createArenaSeasonWorker, WORKER_STATE_COLLECTION, WORKER_STATE_PATH, ACTIVE_STATUS,
  MAX_FINALIZATIONS, MAX_ADVANCES_PER_FINALIZATION, FUNCTION_TIMEOUT_MS, LEASE_MS};
