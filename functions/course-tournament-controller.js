"use strict";

const {createTournamentSourceBank} = require("./tournament-source-bank");
const {TOURNAMENT_PATHS: P, normalizeTournamentRequest, applyTournamentOperation, readPlanTournamentOperation,
  writeTournamentOperation, readTournamentSession, matchSessionId} = require("./tournament-service");
const {applyTournamentSessionOperation, readTournamentSessionOperation, planTournamentSessionOperation,
  writeTournamentSessionOperation, VERIFIED_RUNS} = require("./tournament-session");
const {verifiedTournamentRun, loadSettledMatchResults} = require("./tournament-session-results");
const {loadQualificationRanking, selectQualificationCalculation} = require("./tournament-qualification-freeze");
const {advanceQualificationScan} = require("./tournament-qualification-scan");
const {advanceQualificationCalculation} = require("./tournament-qualification-calculation");
const {tournamentDigest: digest} = require("./tournament-bank");
const {progressBracket, evaluateMeeting, READY_WINDOW_MS} = require("./tournament-policy");
const {PREMIUM_CATCHUP_PASSES} = require("./premium-economy");

const CLIENT_OPERATIONS = Object.freeze(["CREATE", "START_QUALIFICATION", "PROPOSE", "CONFIRM", "READY"]);
const WORKER_PREFIX = "ctw_";
const MAX_MATCHES = 15; // Existing top16 policy, not a new entrant cap.
// Budgets bound invocations, not cohort membership. Every unfinished calculation resumes its
// own persisted phase; there is no fallback to dense LS or a size-based permanent wait.
const QUALIFICATION_EXECUTION = Object.freeze({workMs: 120_000, scanMs: 30_000});
const DEFAULT_WORKER_BOUNDS = Object.freeze({qualificationPageSize: 50, qualificationMaxPages: 1,
  qualificationCalculationMaxPages: 5, repairRetryMs: 60_000});
const TERMINAL_EVENT = new Set(["FINISHED", "CANCELLED"]);
const TERMINAL_MATCH = new Set(["PLAYED", "FORFEIT", "DOUBLE_OUT"]);
const data = (snapshot) => snapshot.exists ? snapshot.data() || {} : null;
const path = (eventId) => `${P.events}/${eventId}`;
function fail(code, message = code) { const error = new Error(message); error.code = code; throw error; }
function auth(uid) { if (typeof uid !== "string" || !uid || uid !== uid.trim() || uid.length > 128 || uid.includes("/")) fail("UNAUTHENTICATED"); return uid; }
function exact(value, min = 0) { if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT"); return value; }
function identifier(value) { if (typeof value !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(value)) fail("INVALID_ARGUMENT"); return value; }
function sessionIdentifier(value) { if (typeof value !== "string" || !/^[qm]_[a-f0-9]{64}$/.test(value)) fail("INVALID_ARGUMENT"); return value; }
function only(object, fields) {
  if (!object || Object.getPrototypeOf(object) !== Object.prototype || Object.keys(object).some((key) => !fields.includes(key))) fail("INVALID_ARGUMENT");
}
function pick(object, fields) { return Object.fromEntries(fields.filter((key) => object?.[key] !== undefined).map((key) => [key, structuredClone(object[key])])); }
function clientId(value) {
  identifier(value);
  if (value.startsWith(WORKER_PREFIX) || value.startsWith("freeze_advance_")) fail("INVALID_ARGUMENT", "Reserved server operation namespace");
  return value;
}
function matchId(value) { if (typeof value !== "string" || !value.trim() || value.length > 512) fail("INVALID_ARGUMENT"); return value; }
function eventRecord(value, eventId) {
  if (!value) fail("NOT_FOUND");
  if (value.schemaVersion !== 1 || value.eventId !== eventId || !["QUALIFICATION", "BRACKET", "FINISHED", "CANCELLED"].includes(value.status)) fail("UNAVAILABLE", "Event state requires repair");
  auth(value.organizerUid); exact(value.lastMutationAtMs);
  return value;
}
function bracketState(event) {
  const matches = event.bracket?.rounds?.flatMap((round) => round.matches);
  if (!Array.isArray(matches) || matches.length > MAX_MATCHES || matches.length < 1 || new Set(matches.map((match) => match.matchId)).size !== matches.length) fail("UNAVAILABLE", "Invalid bounded bracket");
  return {matches, progress: progressBracket({bracket: event.bracket, outcomes: event.outcomes})};
}
function safeSession(value) {
  return pick(value, ["sessionId", "eventId", "kind", "roundNumber", "matchId", "status", "questionIndex",
    "startedAtMs", "endsAtMs", "questionStartedAtMs", "questionEndsAtMs"]);
}
function safeQuestion(question) {
  if (question == null) return null;
  let payload;
  try { payload = typeof question.publicPayload === "string" ? JSON.parse(question.publicPayload) : question.publicPayload; }
  catch { fail("UNAVAILABLE", "Current public question requires repair"); }
  if (!payload || !["SingleChoiceRedacted", "MultipleChoiceRedacted", "OrderingRedacted", "FillBlankRedacted"].includes(payload.type)) fail("UNAVAILABLE", "Current public question requires repair");
  if (typeof payload.text !== "string" || (payload.imageUrl != null && typeof payload.imageUrl !== "string") ||
      (payload.id != null && typeof payload.id !== "string") || (payload.difficulty != null && typeof payload.difficulty !== "string")) fail("UNAVAILABLE");
  for (const key of ["blanks", "protectedTextSegments"]) if (payload[key] !== undefined &&
      (!Array.isArray(payload[key]) || payload[key].some((item) => typeof item !== "string"))) fail("UNAVAILABLE");
  const publicPayload = pick(payload, ["type", "id", "difficulty", "text", "imageUrl", "blanks", "protectedTextSegments"]);
  for (const key of ["options", "items", "candidates"]) if (payload[key] !== undefined) {
    if (!Array.isArray(payload[key])) fail("UNAVAILABLE");
    publicPayload[key] = payload[key].map((row) => {
      if (typeof row?.id !== "string" || typeof row.text !== "string") fail("UNAVAILABLE");
      return pick(row, ["id", "text"]);
    });
  }
  return {...pick(question, ["questionId", "type", "difficulty", "contentVersion", "maxDurationMs"]), publicPayload};
}
function safeMeeting(value) { return pick(value, ["matchId", "status", "slotStartsAtMs", "agreedAtMs", "windowEndMs", "startedAtMs", "endsAtMs", "resultDue", "winnerUid", "loserUid", "technicalForfeit"]); }
function safeProgress(value) {
  if (!value) return undefined;
  return {status: value.status,
    resolved: (value.resolved || []).map((row) => pick(row, ["matchId", "status", "winnerUid", "loserUid", "technicalForfeit"])),
    pending: (value.pending || []).map((row) => ({matchId: row.matchId, entrants: row.entrants.map((p) => pick(p, ["uid", "seed"]))})),
    placements: (value.placements || []).map((row) => pick(row, ["uid", "rank", "slot", "technicalForfeit"]))};
}
function safeEvent(value) {
  const result = {...pick(value, ["eventId", "organizerUid", "status", "cancelReason"]),
    source: pick(value.source, ["kind", "id"]),
    qualification: pick(value.qualification, ["startsAtMs", "endsAtMs", "roundCount", "questionsPerRound", "attemptsPerRound"]),
    prizes: pick(value.prizes, ["first", "second", "third"]),
    escrow: pick(value.escrow, ["reservedBoxes", "remainingBoxes", "paidBoxes", "returnedBoxes"])};
  if (value.progress) result.progress = safeProgress(value.progress);
  return result;
}
function safeResponse(value) {
  const result = pick(value, ["schemaVersion", "operationId", "status", "serverNowMs", "deadlineReached", "questionDeadlineReached"]);
  if (value.event) result.event = safeEvent(value.event);
  if (value.session) result.session = safeSession(value.session);
  if (value.sessions) result.sessions = value.sessions.map(safeSession);
  if (value.meeting) result.meeting = safeMeeting(value.meeting);
  if (value.currentQuestion !== undefined || value.question !== undefined) result.currentQuestion = safeQuestion(value.currentQuestion ?? value.question);
  if (value.lastClosed) result.lastClosed = pick(value.lastClosed, ["questionIndex", "questionId", "kind"]);
  if (value.result) result.result = pick(value.result, ["percentScore", "elapsedMs", "completedAtMs"]);
  // UX-O4/access: the minted competition link token passes to the client ONLY inside the
  // organizer's CREATE response (and that organizer's receipt replays of it). Every other
  // response shape is untouched, so no read projection ever carries the token.
  if (value.admissionLink) result.admissionLink = pick(value.admissionLink, ["token"]);
  return result;
}
function meetingContext(match, pending) { return {...match, participantUids: pending.entrants.map((row) => row.uid), formMaxDurationMs: match.sharedForm.maxDurationMs}; }
function evaluate(match, pending, meeting, now) {
  return evaluateMeeting({match: meetingContext(match, pending), proposals: meeting?.agreement ? meeting.agreement.proposals : meeting?.firstProposals || [],
    confirmations: meeting?.agreement?.confirmations || [], readySignals: meeting?.readySignals || [], nowMs: now});
}
function schedulingDeadline(match) { return exact(match.stageEndsAtMs - READY_WINDOW_MS - match.sharedForm.maxDurationMs); }

/** Construct only inside trusted index/runtime code. Never expose worker methods via a generic
 * callable dispatch. Auth uid and server clock are separate inputs, not inherited payload fields.
 */
function createCourseTournamentController({db, loadLessonContext, isCurator, nowMs = Date.now, sourceLimits,
  workerBounds: overrides = {}}) {
  if (typeof nowMs !== "function") throw new TypeError("Trusted server clock required");
  only(overrides, Object.keys(DEFAULT_WORKER_BOUNDS));
  const bounds = {...DEFAULT_WORKER_BOUNDS, ...overrides};
  Object.values(bounds).forEach((n) => exact(n, 1));
  if (bounds.qualificationPageSize > 50 || bounds.qualificationMaxPages > 10 ||
      bounds.qualificationCalculationMaxPages > 10) fail("INVALID_ARGUMENT");
  const sourceBanks = createTournamentSourceBank({db, loadLessonContext, isCurator, nowMs, ...(sourceLimits ? {limits: sourceLimits} : {})});
  const dependencies = {loadBank: sourceBanks.loadBank, loadQualificationRanking, loadSettledMatchResults};
  const now = () => exact(nowMs());
  const savedEvent = async (eventId) => eventRecord(data(await db.doc(path(identifier(eventId))).get()), eventId);

  async function prepare(uid, payload) { auth(uid); return sourceBanks.prepare({uid, payload}); }
  async function operate(uid, payload) {
    auth(uid);
    if (!CLIENT_OPERATIONS.includes(payload?.kind)) fail("PERMISSION_DENIED", "Server worker operation is not a client capability");
    if (Object.hasOwn(payload, "ownerUid")) fail("INVALID_ARGUMENT");
    clientId(payload.operationId);
    const request = normalizeTournamentRequest(uid, {...payload, ownerUid: uid});
    return safeResponse(await applyTournamentOperation({db, uid, payload: request, now: now(), dependencies}));
  }
  async function session(uid, payload) {
    auth(uid); const at = now(); // First server arrival, BEFORE asynchronous binding/key reads.
    if (!["ANSWER", "ADVANCE", "READ"].includes(payload?.kind)) fail("INVALID_ARGUMENT");
    const allowed = ["kind", "sessionId", ...(payload.kind === "READ" ? [] : ["operationId"]),
      ...(payload.kind === "ANSWER" ? ["questionIndex", "questionId", "answerPayload"] : [])];
    only(payload, allowed); sessionIdentifier(payload.sessionId);
    if (payload.kind === "READ") return readSessionAt(uid, {sessionId: payload.sessionId}, at);
    clientId(payload.operationId);
    const stored = data(await db.doc(`${P.sessions}/${payload.sessionId}`).get());
    if (!stored) fail("NOT_FOUND");
    if (stored.uid !== uid) fail("PERMISSION_DENIED");
    if (stored.sessionId !== payload.sessionId) fail("UNAVAILABLE");
    const response = await applyTournamentSessionOperation({db, uid, now: at,
      payload: {...payload, ownerUid: uid, formFingerprint: stored.formFingerprint}});
    return safeResponse(response);
  }
  async function readSession(uid, payload) { return readSessionAt(uid, payload, now()); }
  async function readSessionAt(uid, payload, at) {
    auth(uid); only(payload, ["sessionId"]); sessionIdentifier(payload.sessionId);
    return db.runTransaction(async transaction => {
      const view = await readTournamentSession({db, transaction, uid, sessionId: payload.sessionId, now: at});
      if (view.session.status === "SETTLED") {
        const [saved, run] = await transaction.getAll(db.doc(`${P.sessions}/${payload.sessionId}`), db.doc(`${VERIFIED_RUNS}/${payload.sessionId}`));
        const stored = data(saved);
        const verified = verifiedTournamentRun(stored, data(run), {sessionId: payload.sessionId, uid,
          eventId: stored.eventId, formFingerprint: stored.formFingerprint}, at);
        view.result = verified;
      }
      return safeResponse(view);
    });
  }
  async function readEvent(uid, payload) {
    auth(uid); only(payload, ["eventId"]);
    const event = await savedEvent(payload.eventId); const view = safeEvent(event);
    if (event.status === "BRACKET" || event.status === "FINISHED") {
      const state = bracketState(event); view.progress = safeProgress(state.progress);
      view.matches = state.matches.map((match) => ({...pick(match, ["matchId", "stage", "stageStartsAtMs", "stageEndsAtMs"]),
        maxDurationMs: match.sharedForm.maxDurationMs, schedulingDeadlineMs: schedulingDeadline(match)}));
    }
    return {event: view, serverNowMs: now()};
  }
  async function readMeeting(uid, payload) {
    auth(uid); only(payload, ["eventId", "matchId", "afterOfferId", "pageSize"]); identifier(payload.eventId); matchId(payload.matchId);
    const pageSize = payload.pageSize ?? 25; exact(pageSize, 1); if (pageSize > 100) fail("INVALID_ARGUMENT");
    const after = payload.afterOfferId ?? null;
    if (after !== null && !/^[a-f0-9]{64}$/.test(after)) fail("INVALID_ARGUMENT");
    const at = now();
    return db.runTransaction(async transaction => {
      const event = eventRecord(data(await transaction.get(db.doc(path(payload.eventId)))), payload.eventId);
      const state = bracketState(event); const match = state.matches.find((row) => row.matchId === payload.matchId);
      const pending = state.progress.pending.find((row) => row.matchId === payload.matchId);
      if (!match || !pending) fail("FAILED_PRECONDITION", "Read the terminal match via event view");
      if (uid !== event.organizerUid && !pending.entrants.some((row) => row.uid === uid)) fail("PERMISSION_DENIED");
      const ref = `${path(event.eventId)}/meetings/${digest(match.matchId)}`;
      const meeting = data(await transaction.get(db.doc(ref)));
      let query = db.collection(`${ref}/offers`).orderBy("__name__");
      if (after !== null) query = query.startAfter(after);
      const docs = (await transaction.get(query.limit(pageSize + 1))).docs;
      const rows = docs.slice(0, pageSize).map((doc) => {
        const row = doc.data();
        if (!pending.entrants.some((p) => p.uid === row.uid) || doc.id !== digest([row.slotStartsAtMs, row.uid])) fail("UNAVAILABLE", "Stored offer binding requires repair");
        return {offerId: doc.id, ...pick(row, ["uid", "slotStartsAtMs", "acceptedAtMs"])};
      });
      return {eventId: event.eventId, meeting: safeMeeting(evaluate(match, pending, meeting, Math.max(at, event.lastMutationAtMs))),
        schedulingDeadlineMs: schedulingDeadline(match), offers: rows, hasMore: docs.length > pageSize,
        nextOfferId: docs.length > pageSize ? rows.at(-1).offerId : null, serverNowMs: at};
    });
  }

  async function advanceSession(expected, at, deadlineAtMs) {
    if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
    return db.runTransaction(async transaction => {
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
      const stored = data(await transaction.get(db.doc(`${P.sessions}/${expected.sessionId}`)));
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
      if (!stored || ["sessionId", "uid", "eventId", "matchId"].some((key) => stored[key] !== expected[key]) || stored.kind !== "MATCH") fail("UNAVAILABLE", "Admitted match session requires repair");
      if (stored.status === "SETTLED") return {changed: false, nextDueAtMs: null};
      if (!stored.pendingAnswerOperationId && at < Math.min(stored.questionEndsAtMs, stored.endsAtMs)) return {changed: false, nextDueAtMs: Math.min(stored.questionEndsAtMs, stored.endsAtMs)};
      const operationId = `${WORKER_PREFIX}session_${digest([stored.sessionId, stored.questionIndex, stored.questionEndsAtMs,
        stored.pendingAnswerOperationId || null, at >= stored.endsAtMs ? "cutoff" : "progress"])}`;
      const payload = {kind: "ADVANCE", ownerUid: stored.uid, operationId, sessionId: stored.sessionId, formFingerprint: stored.formFingerprint};
      const snapshot = await readTournamentSessionOperation({db, transaction, uid: stored.uid, payload});
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
      const plan = planTournamentSessionOperation({snapshot, now: at});
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true, gap: !!plan.pendingRepair};
      if (plan.pendingRepair) return {changed: false, gap: true, nextDueAtMs: at + bounds.repairRetryMs};
      // A waiting poll MUST NOT acquire this permanent transition ID or mutate clocks.
      if (plan.response.status === "CURRENT_QUESTION") return {changed: false, nextDueAtMs: Math.min(stored.questionEndsAtMs, stored.endsAtMs)};
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
      writeTournamentSessionOperation({db, transaction, plan});
      return {changed: !plan.replayed, nextDueAtMs: plan.response.session.status === "SETTLED" ? null : plan.response.session.questionEndsAtMs};
    });
  }
  async function workerOperation(eventId, kind, requestedMatchId, at, deadlineAtMs = Number.MAX_SAFE_INTEGER) {
    for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
      if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
      const decision = await db.runTransaction(async transaction => {
        if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
        const saved = await transaction.get(db.doc(path(eventId)));
        if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
        const event = eventRecord(data(saved), eventId);
        if (TERMINAL_EVENT.has(event.status)) return {changed: false, terminal: true};
        if (kind === "CLOSE_QUALIFICATION" && event.status !== "QUALIFICATION") return {changed: false};
        if (kind === "ADVANCE_MATCH" && !bracketState(event).progress.pending.some((row) => row.matchId === requestedMatchId)) return {changed: false};
        const operationId = `${WORKER_PREFIX}${kind === "CLOSE_QUALIFICATION" ? "close" : "match"}_${digest([eventId, kind, requestedMatchId || event.qualification.endsAtMs])}`;
        const plan = await readPlanTournamentOperation({db, transaction, uid: event.organizerUid, now: at, dependencies,
          payload: {kind, ownerUid: event.organizerUid, operationId, eventId, ...(requestedMatchId ? {matchId: requestedMatchId} : {})}});
        if (now() >= deadlineAtMs) return {changed: false, timedOut: true};
        if (!plan.caughtUp) { writeTournamentOperation({db, transaction, plan}); return {checkpoint: true}; }
        const terminal = kind === "CLOSE_QUALIFICATION" ? ["BRACKET", "FINISHED", "CANCELLED"].includes(plan.response.status) : TERMINAL_MATCH.has(plan.response.status);
        if (!terminal) return {changed: false}; // Discard staged WAITING/RESULTS_PENDING receipt AND event clock.
        writeTournamentOperation({db, transaction, plan}); return {changed: !plan.replayed};
      });
      if (!decision.checkpoint) return decision;
    }
    return {changed: false, gap: true};
  }

  /** Authorized event discovery (story 5.3): one bounded, ordered page of redacted event
   * views, mirroring readMeeting's cursor discipline and the worker's paging query shape
   * (orderBy __name__ + startAfter + limit(n+1) for the hasMore probe). Any signed-in account
   * may browse; rows carry the same allowlist as fetchCourseTournamentEvent — never the seed,
   * fingerprints, forms, banks or the link token. Knowing an eventId is NOT admission.
   */
  async function listEvents(uid, payload) {
    auth(uid); only(payload, ["afterEventId", "pageSize"]);
    const pageSize = payload.pageSize ?? 25; exact(pageSize, 1); if (pageSize > 100) fail("INVALID_ARGUMENT");
    const after = payload.afterEventId ?? null;
    if (after !== null) identifier(after);
    let query = db.collection(P.events).where("status", "in", ["QUALIFICATION", "BRACKET"]).orderBy("__name__");
    if (after !== null) query = query.startAfter(after);
    const docs = (await query.limit(pageSize + 1).get()).docs;
    const rows = docs.slice(0, pageSize).map((doc) => safeEvent(eventRecord(doc.data(), doc.id)));
    return {events: rows, hasMore: docs.length > pageSize,
      nextEventId: docs.length > pageSize ? rows.at(-1).eventId : null, serverNowMs: now()};
  }

  /** Server-attested "this account downloaded this course" record (UX-O4/access, download
   * basis). One create-only document per (uid, courseId): repeating the call — same or
   * different operation id — returns the stored outcome and writes nothing, so the record can
   * never be forged into existence twice or shifted in time. The client-side call wiring is
   * story 5.10; this is the server half only.
   */
  async function recordDownload(uid, payload) {
    auth(uid); only(payload, ["operationId", "courseId"]); clientId(payload.operationId);
    if (typeof payload.courseId !== "string" || !payload.courseId.trim() || payload.courseId.length > 512) fail("INVALID_ARGUMENT");
    const courseId = payload.courseId;
    return db.runTransaction(async transaction => {
      const ref = db.doc(`${P.downloads}/${digest([uid, courseId])}`);
      const stored = data(await transaction.get(ref));
      if (stored) {
        if (stored.schemaVersion !== 1 || stored.uid !== uid || stored.courseId !== courseId) {
          fail("UNAVAILABLE", "Stored download record requires repair");
        }
        return {courseId: stored.courseId, downloadedAtMs: stored.downloadedAtMs, replayed: true};
      }
      const course = data(await transaction.get(db.doc(`quests/${courseId}`)));
      if (!course) fail("INVALID_ARGUMENT", "Unknown course");
      const record = {schemaVersion: 1, uid, courseId, downloadedAtMs: now(), operationId: payload.operationId};
      transaction.create(ref, record);
      return {courseId, downloadedAtMs: record.downloadedAtMs};
    });
  }

  /** SERVER WORKER capability; no uid/data dispatch and no unauthenticated public endpoint. */
  async function reconcileEvent(eventId, execution = {}) {
    only(execution, ["deadlineAtMs"]);
    identifier(eventId); const at = now();
    const deadlineAtMs = Math.min(exact(at + QUALIFICATION_EXECUTION.workMs), execution.deadlineAtMs === undefined
      ? Number.MAX_SAFE_INTEGER : exact(execution.deadlineAtMs));
    let event = await savedEvent(eventId); let transitions = 0;
    const gaps = []; const due = []; const handled = new Set();
    const budgetWaiting = () => ({eventId, done: false, status: gaps.length ? "PENDING_REPAIR" : "WAITING",
      reason: "INVOCATION_TIME", transitions, pendingRepairMatches: new Set(gaps).size, nextDueAtMs: exact(now() + bounds.repairRetryMs)});
    if (TERMINAL_EVENT.has(event.status)) return {eventId, done: true, status: event.status, transitions: 0, nextDueAtMs: null};
    if (event.status === "QUALIFICATION") {
      if (at < event.qualification.endsAtMs) return {eventId, done: false, status: "WAITING", transitions: 0, nextDueAtMs: event.qualification.endsAtMs};
      const pending = (qualificationPhase, reason, scanned) => ({eventId, done: false,
        status: qualificationPhase === "REPAIR" ? "PENDING_REPAIR" : "WAITING", qualificationPhase, reason, transitions: 0,
        // No manifest/form/ranking/player identities in worker diagnostics.
        ...(scanned ? pick(scanned, ["admittedCount", "verifiedCount", "pendingCount"]) : {}),
        nextDueAtMs: exact(now() + bounds.repairRetryMs)});
      if (now() >= deadlineAtMs) return pending("SCANNING", "INVOCATION_TIME");
      // Selection rotates the publication fence atomically, so an already-running legacy
      // publisher cannot write over this version. An already-published ranking always wins.
      const selected = await selectQualificationCalculation({db, eventId, now: at});
      if (now() >= deadlineAtMs) return pending("SCANNING", "INVOCATION_TIME");
      const scanned = selected.existingRanking ? {complete: true} : await advanceQualificationScan({db, eventId, now: at, pageSize: bounds.qualificationPageSize,
        maxPages: bounds.qualificationMaxPages, nowMs: now, deadlineAtMs: Math.min(deadlineAtMs, at + QUALIFICATION_EXECUTION.scanMs)});
      if (!scanned.complete && !scanned.scanComplete) return pending(scanned.status === "PENDING_REPAIR" ? "REPAIR" : "SCANNING", "SCAN_PENDING", scanned);
      if (!scanned.complete) {
        const published = await advanceQualificationCalculation({db, eventId, now: at, nowMs: now, deadlineAtMs,
          pageSize: bounds.qualificationPageSize, maxPages: bounds.qualificationCalculationMaxPages});
        if (!published.complete) return pending("CALCULATION_PENDING",
          published.status === "CALCULATION_PROGRESS" ? "CHECKPOINT_SAVED" :
            published.status === "CALCULATION_TIME_PENDING" ? "INVOCATION_TIME" : "WORKER_BUSY", scanned);
      }
      // A complete scan is NOT sufficient: only the immutable full-cohort ranking authorizes
      // CLOSE_QUALIFICATION. Check again after awaited reads/solve; Cloud Run timeout is not a kill.
      if (now() >= deadlineAtMs) return pending("RANKING_PUBLISHED", "INVOCATION_TIME");
      const closed = await workerOperation(eventId, "CLOSE_QUALIFICATION", null, at, deadlineAtMs);
      if (closed.changed) transitions++;
      if (closed.timedOut) return pending("RANKING_PUBLISHED", "INVOCATION_TIME");
      if (closed.gap) return {eventId, done: false, status: "PREMIUM_CHECKPOINT", transitions, nextDueAtMs: at + bounds.repairRetryMs};
      // Do not chain the independent 15-match worker phase onto a full-cohort calculation.
      event = await savedEvent(eventId);
      return {eventId, done: TERMINAL_EVENT.has(event.status), status: TERMINAL_EVENT.has(event.status) ? event.status : "WAITING",
        transitions, nextDueAtMs: TERMINAL_EVENT.has(event.status) ? null : exact(now() + bounds.repairRetryMs)};
    }
    while (handled.size < MAX_MATCHES) {
      if (now() >= deadlineAtMs) return budgetWaiting();
      event = await savedEvent(eventId);
      if (TERMINAL_EVENT.has(event.status)) return {eventId, done: true, status: event.status, transitions, nextDueAtMs: null};
      if (now() >= deadlineAtMs) return budgetWaiting();
      if (event.status !== "BRACKET") fail("UNAVAILABLE", "Qualification has not closed");
      const state = bracketState(event); const pending = state.progress.pending.find((row) => !handled.has(row.matchId));
      if (!pending) break;
      handled.add(pending.matchId);
      const match = state.matches.find((row) => row.matchId === pending.matchId);
      if (now() >= deadlineAtMs) return budgetWaiting();
      const meeting = data(await db.doc(`${path(eventId)}/meetings/${digest(match.matchId)}`).get());
      if (now() >= deadlineAtMs) return budgetWaiting();
      const evaluated = evaluate(match, pending, meeting, Math.max(at, event.lastMutationAtMs));
      try {
        if (evaluated.status === "STARTED") {
          for (const player of pending.entrants) {
            if (now() >= deadlineAtMs) return budgetWaiting();
            const next = await advanceSession({sessionId: matchSessionId(eventId, match.matchId, player.uid),
              uid: player.uid, eventId, matchId: match.matchId}, at, deadlineAtMs);
            if (next.gap) gaps.push(match.matchId);
            if (next.timedOut || now() >= deadlineAtMs) return budgetWaiting();
            if (next.nextDueAtMs !== null) due.push(next.nextDueAtMs);
          }
        } else if (evaluated.status === "WAITING_FOR_AGREEMENT") due.push(schedulingDeadline(match));
        else if (evaluated.status === "WAITING_FOR_WINDOW") due.push(evaluated.slotStartsAtMs);
        else if (evaluated.status === "WAITING_FOR_READY") due.push(evaluated.windowEndMs);
        if (now() >= deadlineAtMs) return budgetWaiting();
        const advanced = await workerOperation(eventId, "ADVANCE_MATCH", match.matchId, at, deadlineAtMs);
        if (advanced.changed) transitions++;
        if (advanced.gap) gaps.push(match.matchId);
        if (advanced.timedOut || now() >= deadlineAtMs) return budgetWaiting();
      } catch (error) {
        if (!["UNAVAILABLE", "SCORING_UNAVAILABLE", "INCONSISTENT_SESSION", "INVALID_RESULTS", "FAILED_PRECONDITION"].includes(error.code)) throw error;
        gaps.push(match.matchId); // Repair needed; never substitute an invented zero/outcome/payout.
      }
    }
    if (now() >= deadlineAtMs) return budgetWaiting();
    event = await savedEvent(eventId);
    if (TERMINAL_EVENT.has(event.status)) return {eventId, done: true, status: event.status, transitions, nextDueAtMs: null};
    if (now() >= deadlineAtMs) return budgetWaiting();
    if (gaps.length) due.push(at + bounds.repairRetryMs);
    return {eventId, done: false, status: gaps.length ? "PENDING_REPAIR" : "WAITING", transitions,
      pendingRepairMatches: [...new Set(gaps)].length, nextDueAtMs: due.length ? Math.max(at, Math.min(...due)) : at + bounds.repairRetryMs};
  }
  return {prepare, operate, session, readSession, readEvent, readMeeting, listEvents, recordDownload, reconcileEvent};
}

module.exports = {createCourseTournamentController, CLIENT_OPERATIONS, WORKER_PREFIX, MAX_MATCHES, DEFAULT_WORKER_BOUNDS, QUALIFICATION_EXECUTION};
