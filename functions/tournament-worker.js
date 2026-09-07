"use strict";

const {randomBytes, createHash} = require("node:crypto");
const {TOURNAMENT_PATHS} = require("./tournament-service");

const WORKER_STATE_COLLECTION = "course_tournament_worker_state";
const WORKER_STATE_PATH = `${WORKER_STATE_COLLECTION}/sweep`;
const ACTIVE_STATUSES = Object.freeze(["QUALIFICATION", "BRACKET"]);
const MAX_EVENTS = 3;
const FUNCTION_TIMEOUT_MS = 540_000;
const LEASE_MS = 660_000;
const REPAIR_STATUSES = new Set(["PENDING_REPAIR", "PREMIUM_CHECKPOINT", "SCAN_BUDGET", "PENDING_SERVER_SESSIONS"]);
const TERMINAL_STATUSES = new Set(["FINISHED", "CANCELLED"]);
const SAFE_ERROR_CODES = new Set(["UNAVAILABLE", "NOT_FOUND", "INVALID_ARGUMENT", "FAILED_PRECONDITION", "PERMISSION_DENIED",
  "SOURCE_NOT_ELIGIBLE", "STALE_PREPARATION", "INVALID_SOURCE", "INVALID_BANK", "INVALID_RANKING", "INVALID_RESULTS",
  "INCONSISTENT_SESSION", "INCONSISTENT_ESCROW", "INSUFFICIENT_BOXES", "RESOURCE_EXHAUSTED", "SCORING_UNAVAILABLE",
  "UNAUTHENTICATED", "ALREADY_EXISTS", "CONFLICTING_REPLAY"]);
function fail(code, message = code) { const error = new Error(message); error.code = code; throw error; }
function exact(value, min = 0) { if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT"); return value; }
function add(a, b) { return exact(a + b); }
const data = snapshot => snapshot.exists ? snapshot.data() || {} : null;
function documentId(value) {
  if (typeof value !== "string" || !value || value.includes("/") || [".", ".."].includes(value) || Buffer.byteLength(value) > 1500) fail("UNAVAILABLE", "Worker cursor is invalid");
  return value;
}
function stateRecord(raw) {
  if (!raw) return {schemaVersion: 1, cursorAfterId: null, cycle: 0, lease: null};
  if (raw.schemaVersion !== 1 || (raw.cursorAfterId !== null && typeof raw.cursorAfterId !== "string") ||
      !Number.isSafeInteger(raw.cycle) || raw.cycle < 0) fail("UNAVAILABLE", "Worker state requires repair");
  if (raw.cursorAfterId !== null) documentId(raw.cursorAfterId);
  if (raw.lease !== null && (!raw.lease || !/^[a-f0-9]{48}$/.test(raw.lease.ownerNonce || "") ||
      !Number.isSafeInteger(raw.lease.acquiredAtMs) || raw.lease.acquiredAtMs < 0 ||
      !Number.isSafeInteger(raw.lease.expiresAtMs) || raw.lease.expiresAtMs <= raw.lease.acquiredAtMs)) fail("UNAVAILABLE", "Worker lease requires repair");
  return raw;
}
function safeCode(error) {
  if (SAFE_ERROR_CODES.has(error?.code)) return error.code;
  return "RECONCILE_FAILED";
}

/** Server-only scheduler capability. Construct in index with the actual controller's
 * reconcileEvent; sweep() accepts no uid, request body or client-selected operational bounds.
 */
function createTournamentWorker({db, reconcileEvent, nowMs = Date.now,
  log = record => console.warn(JSON.stringify(record)), maxEvents = MAX_EVENTS,
  functionTimeoutMs = FUNCTION_TIMEOUT_MS, leaseMs = LEASE_MS}) {
  if (!db || typeof db.runTransaction !== "function" || typeof reconcileEvent !== "function" ||
      typeof nowMs !== "function" || typeof log !== "function") throw new TypeError("Trusted scheduler dependencies required");
  exact(maxEvents, 1); exact(functionTimeoutMs, 1); exact(leaseMs, 1);
  if (maxEvents > MAX_EVENTS || leaseMs <= functionTimeoutMs) fail("INVALID_ARGUMENT", "At most 3 events; lease must outlive function timeout");
  const ref = db.doc(WORKER_STATE_PATH);
  const now = () => exact(nowMs());
  function report(eventId, code) {
    // Never log source banks, keys, answers, error.message/stack, query payloads or lease nonce.
    const record = {component: "course-tournament-sweep", code,
      ...(eventId !== null ? {eventReference: createHash("sha256").update(eventId).digest("hex")} : {})};
    try { log(record); } catch { /* Diagnostic sink failure must not undo fair cursor progress. */ }
  }
  function owns(state, nonce, at) { return state.lease?.ownerNonce === nonce && state.lease.expiresAtMs > at; }
  async function acquire(nonce) {
    return db.runTransaction(async transaction => {
      const state = stateRecord(data(await transaction.get(ref))); const at = now();
      if (state.lease && state.lease.expiresAtMs > at) return {acquired: false, retryAtMs: state.lease.expiresAtMs};
      const lease = {ownerNonce: nonce, acquiredAtMs: at, expiresAtMs: add(at, leaseMs)};
      transaction.set(ref, {...state, lease, updatedAtMs: at});
      return {acquired: true, cursorAfterId: state.cursorAfterId};
    });
  }
  async function checkpoint(nonce, cursorAfterId, wrap = false) {
    if (cursorAfterId !== null) documentId(cursorAfterId);
    return db.runTransaction(async transaction => {
      const state = stateRecord(data(await transaction.get(ref))); const at = now();
      if (!owns(state, nonce, at)) return false;
      transaction.set(ref, {...state, cursorAfterId, cycle: wrap ? add(state.cycle, 1) : state.cycle, updatedAtMs: at});
      return true;
    });
  }
  async function release(nonce) {
    return db.runTransaction(async transaction => {
      const state = stateRecord(data(await transaction.get(ref))); const at = now();
      if (!owns(state, nonce, at)) return false;
      transaction.set(ref, {...state, lease: null, updatedAtMs: at});
      return true;
    });
  }
  async function authorized(nonce) { return owns(stateRecord(data(await ref.get())), nonce, now()); }
  async function page(cursorAfterId) {
    let query = db.collection(TOURNAMENT_PATHS.events).where("status", "in", ACTIVE_STATUSES).orderBy("__name__");
    if (cursorAfterId !== null) query = query.startAfter(cursorAfterId);
    const snapshot = await query.limit(maxEvents + 1).get();
    if (!snapshot || !Array.isArray(snapshot.docs) || snapshot.docs.length > maxEvents + 1) fail("UNAVAILABLE", "Malformed active-event page");
    let previous = cursorAfterId;
    for (const document of snapshot.docs) {
      const id = documentId(document.id);
      if (previous !== null && Buffer.compare(Buffer.from(id), Buffer.from(previous)) <= 0) fail("UNAVAILABLE", "Active-event cursor did not advance");
      previous = id;
    }
    // Do not inspect or trust organizerUid/source/clock on query rows. Reconcile loads authority.
    return {eventIds: snapshot.docs.slice(0, maxEvents).map(doc => doc.id), hasMore: snapshot.docs.length > maxEvents};
  }

  async function sweep() {
    const startedAtMs = now(); const stopAtMs = add(startedAtMs, functionTimeoutMs);
    const nonce = randomBytes(24).toString("hex");
    let acquired;
    try { acquired = await acquire(nonce); }
    catch {
      // Acquisition can fail before the main sweep's try/finalization path. Do not let a
      // provider's message reach the scheduler SDK logger. An uncertain committed lease
      // remains safe until expiry; do not guess ownership or clear somebody else's lease.
      report(null, "SWEEP_STORAGE_FAILED");
      fail("UNAVAILABLE", "Tournament sweep storage unavailable; retry safely after lease expiry or release");
    }
    if (!acquired.acquired) return {status: "BUSY", attempted: 0, retryAtMs: acquired.retryAtMs};
    let cursorAfterId = acquired.cursorAfterId;
    let attempted = 0; let failed = 0; let completedEvents = 0; let wrapped = false; let queryCount = 0;
    let status = "IDLE"; let failure = null;
    try {
      let next = await page(cursorAfterId); queryCount++;
      if (!next.eventIds.length && cursorAfterId !== null) {
        // An empty tail (including deleted/finished former cursor) ends that cycle. Reset durably
        // BEFORE the sole allowed head query; a crash simply resumes at the head next invocation.
        if (!await checkpoint(nonce, null, true)) status = "LEASE_LOST";
        else { cursorAfterId = null; wrapped = true; next = await page(null); queryCount++; }
      }
      if (status !== "LEASE_LOST") {
        for (let index = 0; index < next.eventIds.length; index++) {
          if (now() >= stopAtMs) { status = "TIME_BUDGET"; break; }
          if (!await authorized(nonce)) { status = "LEASE_LOST"; break; }
          // The authorization read itself may consume the remaining request budget. A
          // Cloud Run timeout is not a guarantee that JavaScript execution has stopped.
          if (now() >= stopAtMs) { status = "TIME_BUDGET"; break; }
          const eventId = next.eventIds[index]; let eventError = null;
          attempted++;
          try {
            const result = await reconcileEvent(eventId);
            if (!result || result.eventId !== eventId || typeof result.done !== "boolean" ||
                (result.done ? !TERMINAL_STATUSES.has(result.status) : result.status !== "WAITING" && !REPAIR_STATUSES.has(result.status))) {
              eventError = "INVALID_RECONCILE_RESULT";
            } else if (result.done) completedEvents++;
            else if (REPAIR_STATUSES.has(result.status)) eventError = result.status;
          } catch (error) { eventError = safeCode(error); }
          if (eventError) failed++;
          const endOfCycle = index === next.eventIds.length - 1 && !next.hasMore;
          const cursor = endOfCycle ? null : eventId;
          // CAS after EACH attempt, including a corrupt event or failed controller. A hard crash
          // before this point repeats that one event; controller receipts make it at-least-once.
          if (!await checkpoint(nonce, cursor, endOfCycle)) {
            if (eventError) report(eventId, eventError);
            status = "LEASE_LOST"; break;
          }
          cursorAfterId = cursor; wrapped = wrapped || endOfCycle;
          if (eventError) report(eventId, eventError);
          status = failed ? "PROCESSED_WITH_ERRORS" : "PROCESSED";
        }
      }
    } catch (error) {
      failure = error; report(null, "SWEEP_STORAGE_FAILED");
    }
    let leaseReleased = false;
    try { leaseReleased = await release(nonce); }
    catch (error) { failure ||= error; report(null, "SWEEP_RELEASE_FAILED"); }
    if (failure) fail("UNAVAILABLE", "Tournament sweep storage unavailable; retry safely after lease expiry or release");
    if (!leaseReleased) status = "LEASE_LOST";
    return {status, attempted, failed, completedEvents, queryCount, wrapped, cursorAfterId, leaseReleased};
  }
  return {sweep};
}

module.exports = {createTournamentWorker, WORKER_STATE_COLLECTION, WORKER_STATE_PATH,
  ACTIVE_STATUSES, MAX_EVENTS, FUNCTION_TIMEOUT_MS, LEASE_MS};
