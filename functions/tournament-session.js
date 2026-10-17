"use strict";

const {scoreAttempt} = require("./attempt-scoring");
const {DOCUMENT_VERSION} = require("./question-key-store");
const {restoreContent, redact} = require("./question-redaction");
const {TOURNAMENT_PATHS} = require("./tournament-service");
const {tournamentDigest: digest, canonicalTournamentJson: canonical} = require("./tournament-bank");
// ONLINE-O2 window mechanics, composed read-only (the adopted O2 module is never edited by this
// adapter): the shared 60-second constant, the window states and the deterministic windowId mint.
const {WINDOW_OPEN, WINDOW_DELIVERED, WINDOW_EXPIRED, NEXT_QUESTION_WINDOW_SECONDS, MS_PER_SECOND,
  mintWindowId} = require("./lesson-question-window");

const SESSION_OPERATIONS = "course_tournament_session_operations";
const SESSION_INGRESS = "course_tournament_answer_ingress";
const VERIFIED_RUNS = "course_tournament_verified_runs";
const VERIFICATION_VERSION = "tournament-session-assessment-v1";
const QUESTION_COUNT = 20;
const MAX_ANSWER_BYTES = 16_384;

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function gap(message) { fail("SCORING_UNAVAILABLE", message); }
function exact(value, label, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", `${label} must be an exact integer >= ${min}`);
  return value;
}
function add(a, b) { return exact(a + b, "timestamp"); }
function owner(value) {
  if (typeof value !== "string" || !value || value.length > 128 || value !== value.trim() || value.includes("/")) fail("INVALID_ARGUMENT", "Authenticated uid required");
  return value;
}
function operationId(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(value)) fail("INVALID_ARGUMENT", "Persistent operation id required");
  return value;
}
function sessionId(value) {
  if (typeof value !== "string" || !/^[qm]_[a-f0-9]{64}$/.test(value)) fail("INVALID_ARGUMENT", "Server session id required");
  return value;
}
function normalizeSessionRequest(uid, raw) {
  owner(uid);
  if (!raw || !["ANSWER", "ADVANCE", "READY_ACK"].includes(raw.kind)) fail("INVALID_ARGUMENT", "Unknown session operation");
  if (raw.ownerUid !== uid) fail("PERMISSION_DENIED", "Operation belongs to another account");
  const allowed = ["kind", "ownerUid", "operationId", "sessionId", "formFingerprint",
    ...(raw.kind === "ANSWER" ? ["questionIndex", "questionId", "answerPayload"] : []),
    ...(raw.kind === "READY_ACK" ? ["questionIndex", "windowId"] : [])];
  if (Object.keys(raw).some(key => !allowed.includes(key))) fail("INVALID_ARGUMENT", "Client scores, clocks and unsupported fields are not accepted");
  if (!/^[a-f0-9]{64}$/.test(raw.formFingerprint || "")) fail("INVALID_ARGUMENT", "Immutable form fingerprint required");
  const request = {kind: raw.kind, ownerUid: uid, operationId: operationId(raw.operationId), sessionId: sessionId(raw.sessionId), formFingerprint: raw.formFingerprint};
  if (request.kind === "ANSWER") {
    request.questionIndex = exact(raw.questionIndex, "questionIndex");
    if (request.questionIndex >= QUESTION_COUNT || typeof raw.questionId !== "string" || !raw.questionId || raw.questionId.length > 512 ||
        !Object.hasOwn(raw, "answerPayload")) fail("INVALID_ARGUMENT", "Question binding and answer payload required");
    request.questionId = raw.questionId;
    request.answerPayload = structuredClone(raw.answerPayload);
    if (Buffer.byteLength(canonical(request.answerPayload)) > MAX_ANSWER_BYTES) fail("INVALID_ARGUMENT", "Answer exceeds storage budget");
  }
  if (request.kind === "READY_ACK") {
    // A8/D6: the receipt key is (session identity, questionIndex, windowId) and exists only for
    // window-delivered questions 1..19; question 0 has no ACK path (start contract).
    request.questionIndex = exact(raw.questionIndex, "questionIndex");
    if (request.questionIndex < 1 || request.questionIndex >= QUESTION_COUNT) fail("INVALID_ARGUMENT", "Ready-ACK exists only for window-delivered questions");
    if (typeof raw.windowId !== "string" || !/^win_[a-f0-9]{12}$/.test(raw.windowId)) fail("INVALID_ARGUMENT", "Ready-ACK window identity required");
    request.windowId = raw.windowId;
  }
  return request;
}
const sessionPath = id => `${TOURNAMENT_PATHS.sessions}/${id}`;
const opPath = id => `${SESSION_OPERATIONS}/${digest(id)}`;
const ingressPath = id => `${SESSION_INGRESS}/${digest(id)}`;
const slotPath = (id, index) => `${sessionPath(id)}/answers/${String(index).padStart(2, "0")}`;
const runPath = id => `${VERIFIED_RUNS}/${id}`;
function replay(receipt, uid, request) {
  if (!receipt) return null;
  if (receipt.uid !== uid) fail("PERMISSION_DENIED", "Operation belongs to another account");
  if (receipt.schemaVersion !== 1 || receipt.operationId !== request.operationId || receipt.fingerprint !== digest(request) || !receipt.response) {
    fail("CONFLICTING_REPLAY", "Operation id is already bound to another request");
  }
  return receipt.response;
}

/** Bounded reads only. No answer, percent, elapsed or served subset is loaded from client state. */
async function readTournamentSessionOperation({db, transaction, uid, payload}) {
  const request = normalizeSessionRequest(uid, payload);
  const first = await transaction.get(db.doc(opPath(request.operationId)));
  const old = replay(first.exists ? first.data() : null, uid, request);
  if (old) return {request, replay: old};
  const saved = await transaction.get(db.doc(sessionPath(request.sessionId)));
  if (!saved.exists) fail("NOT_FOUND", "Tournament session not found");
  const session = saved.data();
  if (session.uid !== uid) fail("PERMISSION_DENIED", "Session belongs to another account");
  if (session.sessionId !== request.sessionId || session.schemaVersion !== 1 || session.formFingerprint !== request.formFingerprint) fail("CONFLICTING_REPLAY", "Session/form identity mismatch");
  const eventPath = `${TOURNAMENT_PATHS.events}/${session.eventId}`;
  const paths = [`${eventPath}/forms/${digest(session.formId)}`, `${TOURNAMENT_PATHS.privateForms}/${session.eventId}/forms/${digest(session.formId)}`,
    runPath(session.sessionId), ingressPath(request.operationId), ...Array.from({length: QUESTION_COUNT}, (_, i) => slotPath(session.sessionId, i))];
  const values = await transaction.getAll(...paths.map(path => db.doc(path)));
  const data = values.map(snap => snap.exists ? snap.data() : null);
  let pendingIngress = null; let pendingReceipt = null;
  if (session.pendingAnswerOperationId) {
    operationId(session.pendingAnswerOperationId);
    if (session.pendingAnswerOperationId === request.operationId) pendingIngress = data[3];
    else {
      const [ingress, receipt] = await transaction.getAll(db.doc(ingressPath(session.pendingAnswerOperationId)), db.doc(opPath(session.pendingAnswerOperationId)));
      pendingIngress = ingress.exists ? ingress.data() : null; pendingReceipt = receipt.exists ? receipt.data() : null;
    }
  }
  return {request, session, publicForm: data[0], privateForm: data[1], verifiedRun: data[2], ownIngress: data[3], slots: data.slice(4), pendingIngress, pendingReceipt};
}

function formBinding(snapshot) {
  const {session, publicForm: form} = snapshot;
  if (!form || form.schemaVersion !== 1 || form.formId !== session.formId || form.fingerprint !== session.formFingerprint ||
      !Array.isArray(form.questions) || form.questions.length !== QUESTION_COUNT ||
      new Set(form.questions.map(q => q?.questionId)).size !== QUESTION_COUNT || form.questions.some(q => !q || typeof q.questionId !== "string" || !q.questionId)) {
    gap("Immutable public form is missing or malformed; no answer can be attributed");
  }
  return form;
}
function scoreForm(snapshot, slots) {
  const {session, publicForm: form, privateForm: keys} = snapshot;
  if (!keys || keys.schemaVersion !== 1 || keys.formId !== session.formId || keys.fingerprint !== session.formFingerprint ||
      !Array.isArray(keys.keys) || keys.keys.length !== QUESTION_COUNT || new Set(keys.keys.map(row => row?.questionId)).size !== QUESTION_COUNT) {
    gap("Private answer-key snapshot is unavailable");
  }
  let duration = 0;
  for (const question of form.questions) {
    const key = keys.keys.find(row => row.questionId === question.questionId);
    if (!key || key.contentVersion !== question.contentVersion || question.contentHash !== digest(question.publicPayload) ||
        !Number.isSafeInteger(question.maxDurationMs) || question.maxDurationMs < 1 || question.type === "Survey" || question.type === "SURVEY") gap("Question/key/timer snapshot mismatch");
    const restored = restoreContent(question.publicPayload, key.answerKey);
    if (!restored || key.answerKey?.questionId !== question.questionId ||
        redact(JSON.stringify(restored), question.difficulty, {questionId: question.questionId, random: () => 0}).status !== "redacted") gap("Malformed private answer key");
    duration = add(duration, question.maxDurationMs);
  }
  if (duration !== form.maxDurationMs || session.endsAtMs - session.startedAtMs > duration) gap("Immutable form/session duration mismatch");
  // Give the existing engine a placeholder for EVERY served position, even a timed-out one.
  // That makes it validate every key before any digit1 is committed for a server-side timeout.
  const scored = scoreAttempt({
    questions: form.questions.map(q => ({id: q.questionId, lessonId: form.formId, payload: q.publicPayload})),
    served: form.questions.map((q, codeAnswerIndex) => ({questionId: q.questionId, codeAnswerIndex})),
    keyDocument: {version: DOCUMENT_VERSION, lessonId: form.formId, publicHalfRedacted: true,
      keys: keys.keys.map(row => ({questionId: row.questionId, key: row.answerKey}))},
    answers: form.questions.map((q, codeAnswerIndex) => ({questionId: q.questionId, codeAnswerIndex,
      answerPayload: slots[codeAnswerIndex]?.answerPayload ?? null,
      answeredAtMs: slots[codeAnswerIndex]?.receivedAtMs ?? slots[codeAnswerIndex]?.closedAtMs ?? session.startedAtMs})),
  });
  if (!scored.scorable || !/^[1-9]{20}$/.test(scored.codeAnswer)) gap("Existing assessment engine cannot score this server snapshot");
  return scored;
}
function responseFor(request, session, form, now, status, lastClosed = null, run = null) {
  // A4/D5: before a delivery there is no active question. While the boundary window is OPEN the
  // stored question clocks belong to the already-closed question, so the min-rule alone would be
  // incidental; the delivery state is required explicitly.
  const delivered = !session.window || session.window.state !== WINDOW_OPEN;
  return {schemaVersion: 1, ownerUid: session.uid, operationId: request.operationId, status,
    session: {sessionId: session.sessionId, eventId: session.eventId, formId: session.formId, formFingerprint: session.formFingerprint,
      kind: session.kind, status: session.status, questionIndex: session.questionIndex, startedAtMs: session.startedAtMs, endsAtMs: session.endsAtMs,
      questionStartedAtMs: session.questionStartedAtMs, questionEndsAtMs: session.questionEndsAtMs,
      ...(session.window ? {window: {windowId: session.window.windowId, completedQuestionIndex: session.window.completedQuestionIndex,
        nextQuestionIndex: session.window.nextQuestionIndex, windowSeconds: session.window.windowSeconds,
        openedAtMs: session.window.openedAtMs, expiresAtMs: session.window.expiresAtMs, state: session.window.state}} : {})},
    serverNowMs: now,
    currentQuestion: session.status === "STARTED" && delivered && now < Math.min(session.questionEndsAtMs, session.endsAtMs) ? form.questions[session.questionIndex] : null,
    // Other contestants may still be answering the common form. Acknowledge the slot without
    // disclosing its grading, even after this player finishes; the full proof stays server-only.
    ...(lastClosed ? {lastClosed: {questionIndex: lastClosed.questionIndex, questionId: lastClosed.questionId, kind: lastClosed.kind}} : {}),
    ...(run ? {result: {verificationVersion: run.verificationVersion, percentScore: run.percentScore,
      elapsedMs: run.elapsedMs, completedAtMs: run.completedAtMs}} : {})};
}
function responseReceipt(request, response, now) {
  return {schemaVersion: 1, uid: request.ownerUid, operationId: request.operationId, sessionId: request.sessionId,
    fingerprint: digest(request), createdAtMs: now, response};
}

// --- ONLINE-O1/O2 next-question window (Appendix A1-A4, A8; D3/D4/D5/D6) ---

/** The captured per-boundary window state (D3): identity `(sessionId, completedQuestionIndex)`
 * via the shared O2 mint, config captured once at open — the 60-second server constant capped by
 * the envelope remainder (never silently: the captured windowSeconds and the clamped edge are the
 * published facts) — and persisted openedAtMs/expiresAtMs so a crash-recovery sweep recomputes the
 * same edge. Windows exist only at boundaries 1..19: question 0 arrives by the start contract and
 * the last close settles without a window (D6, O2 §3).
 */
function openTournamentQuestionWindow({session, form, completedQuestionIndex, openedAtMs}) {
  const nextQuestion = form.questions[completedQuestionIndex + 1];
  const remainderMs = session.endsAtMs - openedAtMs; // Caller guarantees a positive remainder.
  // Ceil keeps a window alive for any positive remainder; `expiresAtMs` (clamped exactly to the
  // envelope edge) is the authoritative lifetime — windowSeconds is the coarse captured constant
  // and may overstate by under a second. A non-positive remainder never reaches this function.
  const windowSeconds = Math.min(NEXT_QUESTION_WINDOW_SECONDS, Math.ceil(remainderMs / MS_PER_SECOND));
  return {schemaVersion: 1, windowId: mintWindowId(session.sessionId, completedQuestionIndex),
    completedQuestionIndex, nextQuestionIndex: completedQuestionIndex + 1, nextQuestionId: nextQuestion.questionId,
    nextQuestionMaxDurationMs: nextQuestion.maxDurationMs, windowSeconds, formFingerprint: session.formFingerprint,
    endsAtMs: session.endsAtMs, openedAtMs, expiresAtMs: Math.min(openedAtMs + windowSeconds * MS_PER_SECOND, session.endsAtMs),
    state: WINDOW_OPEN, delivery: null, closedAtMs: null, closedReason: null};
}

/** Delivery anchors the delivered question's clocks exactly once (D4, O2 §5): the existing clamp
 * formula is kept, only `now` becomes the server delivery moment inside the window. A stored
 * window that disagrees with the served question is a server gap, never a second anchor.
 */
function deliverTournamentQuestionWindow(session, form, deliveredAtMs) {
  const window = session.window;
  const question = form.questions[session.questionIndex];
  if (!window || window.state !== WINDOW_OPEN || window.nextQuestionIndex !== session.questionIndex ||
      window.nextQuestionId !== question.questionId || window.formFingerprint !== session.formFingerprint) {
    gap("Session window state is malformed");
  }
  session.questionStartedAtMs = deliveredAtMs;
  session.questionEndsAtMs = Math.min(add(deliveredAtMs, question.maxDurationMs), session.endsAtMs);
  window.state = WINDOW_DELIVERED;
  window.delivery = {questionIndex: session.questionIndex, questionId: question.questionId, deliveredAtMs};
}

/** Validation of the stored window at the session's current boundary. STARTED sessions can carry
 * only the live boundary window (each close either replaces it or settles); anything else is a
 * server gap. The captured envelope edge must equal the session's immutable endsAtMs (D1). */
function checkedBoundaryWindow(session, form) {
  const window = session.window;
  if (!window || session.status !== "STARTED") return null; // A settled session's window is history.
  const question = form.questions[session.questionIndex];
  if (window.schemaVersion !== 1 || window.completedQuestionIndex !== session.questionIndex - 1 ||
      window.nextQuestionIndex !== session.questionIndex || window.nextQuestionId !== question.questionId ||
      window.formFingerprint !== session.formFingerprint || window.endsAtMs !== session.endsAtMs ||
      !Number.isSafeInteger(window.openedAtMs) || !Number.isSafeInteger(window.expiresAtMs) ||
      window.openedAtMs < session.startedAtMs || window.expiresAtMs <= window.openedAtMs || window.expiresAtMs > session.endsAtMs ||
      !Number.isSafeInteger(window.windowSeconds) || window.windowSeconds < 1 || window.windowSeconds > NEXT_QUESTION_WINDOW_SECONDS ||
      ![WINDOW_OPEN, WINDOW_DELIVERED].includes(window.state)) {
    gap("Session window state is malformed");
  }
  if (window.state === WINDOW_DELIVERED &&
      (!window.delivery || window.delivery.questionIndex !== session.questionIndex || window.delivery.questionId !== question.questionId ||
       !Number.isSafeInteger(window.delivery.deliveredAtMs) || window.delivery.deliveredAtMs < window.openedAtMs ||
       window.delivery.deliveredAtMs >= window.expiresAtMs || session.questionStartedAtMs !== window.delivery.deliveredAtMs)) {
    gap("Session window delivery state is malformed");
  }
  return window;
}

/** Pure bounded state machine. Missing keys yields a durable valid ingress, not an invented score.
 * `serverDriven` marks a worker/controlled-tick driver (AD-23): it closes and settles but never
 * delivers — the window stays OPEN until a client-driven operation or the read path delivers, and
 * an expired window terminates the attempt at its captured edge from either side (D4/A3/A4).
 */
function planTournamentSessionOperation({snapshot, now, serverDriven = false}) {
  exact(now, "server time");
  if (snapshot.replay) return {replayed: true, response: snapshot.replay, writes: []};
  const {request, session: saved} = snapshot;
  // Global ingress ownership applies even to a settled-session poll or a worker repairing a
  // DIFFERENT pending answer. No early return may capture another request's reserved id.
  if (snapshot.ownIngress) {
    if (snapshot.ownIngress.uid !== request.ownerUid) fail("PERMISSION_DENIED", "Ingress belongs to another account");
    if (snapshot.ownIngress.fingerprint !== digest(request)) fail("CONFLICTING_REPLAY", "Ingress id is already bound to another request");
  }
  const session = structuredClone(saved);
  const form = formBinding(snapshot);
  if (!["QUALIFICATION", "MATCH"].includes(session.kind) || !["STARTED", "SETTLED"].includes(session.status) || !Number.isSafeInteger(session.questionIndex) || session.questionIndex < 0 || session.questionIndex > QUESTION_COUNT ||
      !Number.isSafeInteger(session.startedAtMs) || !Number.isSafeInteger(session.endsAtMs) || session.startedAtMs < 0 || session.endsAtMs <= session.startedAtMs || now < session.startedAtMs) gap("Session clock/progress is malformed");
  const at = Math.max(now, exact(session.lastProgressAtMs ?? session.startedAtMs, "progress clock"));
  const priorDigits = session.scoreDigits || "";
  if (!/^[1-9]*$/.test(priorDigits) || priorDigits.length !== session.questionIndex ||
      (session.status === "SETTLED") !== (session.questionIndex === QUESTION_COUNT)) gap("Session score prefix is inconsistent");
  const slots = structuredClone(snapshot.slots);
  for (let i = 0; i < QUESTION_COUNT; i++) {
    const row = slots[i];
    if (i >= session.questionIndex) { if (row) gap("Answer exists beyond the committed prefix"); continue; }
    if (!row || row.schemaVersion !== 1 || row.uid !== session.uid || row.sessionId !== session.sessionId || row.formFingerprint !== session.formFingerprint ||
        row.questionIndex !== i || row.questionId !== form.questions[i].questionId || String(row.scoreDigit) !== priorDigits[i]) gap("Committed answer prefix is missing or inconsistent");
  }
  if (request.kind === "ANSWER" && (session.status === "SETTLED" || request.questionIndex !== session.questionIndex)) {
    fail("QUESTION_ALREADY_CLOSED", "An answer can target only the current uncommitted question; old answers never move to the next question");
  }
  if (request.kind === "ANSWER" && request.questionId !== form.questions[session.questionIndex].questionId) fail("CONFLICTING_REPLAY", "Answer is bound to a different question");
  const boundary = checkedBoundaryWindow(session, form);
  const staged = new Map();
  function stage(path, data, create = false) { staged.set(path, {path, data, create: create || staged.get(path)?.create || false}); }
  if (request.kind === "READY_ACK") {
    // A8: the ACK is a per-question receipt over an already-delivered window. It never anchors,
    // restarts or extends a deadline, and it changes no slot, clock or score (D5/O2 §4).
    const stored = session.readyAcks?.[request.questionIndex] ?? null;
    if (stored) {
      if (stored.schemaVersion !== 1 || stored.questionIndex !== request.questionIndex || stored.windowId !== request.windowId) {
        fail("CONFLICTING_REPLAY", "Ready-ACK key is already bound to another receipt");
      }
      const response = {...responseFor(request, session, form, at, "READY_ACK"), readyAck: stored};
      stage(opPath(request.operationId), responseReceipt(request, response, at), true);
      return {replayed: false, response, writes: [...staged.values()]};
    }
    if (session.status !== "STARTED" || !boundary || boundary.state !== WINDOW_DELIVERED || !boundary.delivery ||
        boundary.windowId !== request.windowId || boundary.delivery.questionIndex !== request.questionIndex) {
      fail("FAILED_PRECONDITION", "Ready-ACK does not match a delivered question");
    }
    if (at >= Math.min(session.questionEndsAtMs, session.endsAtMs)) {
      fail("FAILED_PRECONDITION", "Ready-ACK after the answer deadline");
    }
    const receipt = {schemaVersion: 1, questionIndex: request.questionIndex, windowId: boundary.windowId,
      questionId: boundary.delivery.questionId, issuedAtMs: boundary.delivery.deliveredAtMs,
      answerDeadlineMs: session.questionEndsAtMs, ackedAtMs: at};
    session.readyAcks = {...(session.readyAcks || {}), [request.questionIndex]: receipt};
    const response = {...responseFor(request, session, form, at, "READY_ACK"), readyAck: receipt};
    stage(sessionPath(session.sessionId), session);
    stage(opPath(request.operationId), responseReceipt(request, response, at), true);
    return {replayed: false, response, writes: [...staged.values()]};
  }
  const closed = [];
  const initialIndex = session.questionIndex;
  // Captured before any verdict mutates the window: the entry question was shown to the player
  // unless it is still an undelivered window question at ENTRY. A client ANSWER racing an
  // undelivered window delivers first in the same operation, so the question WAS shown; the
  // delivery flag below is re-computed after that delivery.
  let undeliveredAtEntry = !!(boundary && boundary.state === WINDOW_OPEN);
  let answerRequest = request.kind === "ANSWER" ? request : null;
  let ingress = snapshot.ownIngress;
  let firstReceivedAt = at;
  function close(kind, closedAtMs, answer = null) {
    const index = session.questionIndex;
    const row = {schemaVersion: 1, uid: session.uid, eventId: session.eventId, sessionId: session.sessionId,
      formId: session.formId, formFingerprint: session.formFingerprint, questionIndex: index, questionId: form.questions[index].questionId,
      kind, wasShown: index === initialIndex && !undeliveredAtEntry, closedAtMs, receivedAtMs: answer ? firstReceivedAt : null,
      answerOperationId: answer ? answer.operationId : null, answerPayload: answer ? answer.answerPayload : null};
    slots[index] = row; closed.push(row); session.questionIndex++;
  }
  if (session.status === "SETTLED") {
    const run = snapshot.verifiedRun;
    if (!run || run.uid !== session.uid || run.sessionId !== session.sessionId || run.formFingerprint !== session.formFingerprint || run.codeAnswer !== priorDigits) gap("Settled session has no matching verified run");
    const response = responseFor(request, session, form, at, "COMPLETED", null, run);
    stage(opPath(request.operationId), responseReceipt(request, response, at), true);
    return {replayed: false, response, writes: [...staged.values()]};
  }
  if (!Number.isSafeInteger(session.questionStartedAtMs) || !Number.isSafeInteger(session.questionEndsAtMs) ||
      session.questionStartedAtMs < session.startedAtMs || session.questionEndsAtMs <= session.questionStartedAtMs || session.questionEndsAtMs > session.endsAtMs) gap("Current question clock is malformed");

  // A3/A4/D4: resolve the boundary window before any answer/advance logic. The window edge is a
  // terminal edge — the verdict closes from the delivery side exactly as the worker sweep closes
  // from the other, both writing the same captured-edge state (closedAtMs = captured edge, never
  // the observation moment). A worker pass never delivers: before a client-driven delivery (or
  // the read path) there is no active question, and a waiting worker stages nothing.
  if (boundary && boundary.state === WINDOW_OPEN) {
    if (at >= boundary.expiresAtMs) {
      boundary.state = WINDOW_EXPIRED; boundary.closedAtMs = boundary.expiresAtMs; boundary.closedReason = "WINDOW_EXPIRED";
      while (session.questionIndex < QUESTION_COUNT) close("FORCED_CUTOFF", boundary.expiresAtMs);
    } else if (serverDriven) {
      const response = responseFor(request, session, form, at, "CURRENT_QUESTION");
      return {replayed: false, response, writes: []};
    } else {
      deliverTournamentQuestionWindow(session, form, at);
      // The entry question has now been shown by this very operation: a same-operation ANSWER
      // on the delivered question must record wasShown true (client-race case).
      undeliveredAtEntry = false;
    }
  }
  if (session.pendingAnswerOperationId) {
    const pending = snapshot.pendingIngress;
    if (!pending || snapshot.pendingReceipt || pending.schemaVersion !== 1 || pending.status !== "PENDING" ||
        pending.uid !== session.uid || pending.operationId !== session.pendingAnswerOperationId ||
        pending.fingerprint !== digest(pending.request)) gap("Pending answer ingress is missing or inconsistent");
    const normalized = normalizeSessionRequest(session.uid, pending.request);
    if (normalized.kind !== "ANSWER" || normalized.sessionId !== session.sessionId || normalized.formFingerprint !== session.formFingerprint ||
        normalized.questionIndex !== session.questionIndex || normalized.questionId !== form.questions[session.questionIndex].questionId) gap("Pending ingress no longer matches its claimed slot");
    if (request.kind === "ANSWER" && request.operationId !== pending.operationId) fail("ANSWER_PENDING", "A timely answer already owns this slot; retry its original id");
    if (request.kind === "ANSWER" && digest(request) !== pending.fingerprint) fail("CONFLICTING_REPLAY", "Pending operation id cannot change its answer");
    answerRequest = normalized; ingress = pending; firstReceivedAt = exact(pending.firstReceivedAtMs, "first server receipt time");
    if (firstReceivedAt < session.questionStartedAtMs || firstReceivedAt >= Math.min(session.questionEndsAtMs, session.endsAtMs)) gap("Pending answer was not timely");
  } else if (ingress) {
    gap("Ingress exists without its claimed slot or final receipt");
  }
  const timely = answerRequest && firstReceivedAt >= session.questionStartedAtMs && firstReceivedAt < Math.min(session.questionEndsAtMs, session.endsAtMs);
  const newIngress = timely && !ingress ? {schemaVersion: 1, uid: session.uid, operationId: answerRequest.operationId,
    sessionId: session.sessionId, fingerprint: digest(answerRequest), request: answerRequest, firstReceivedAtMs: firstReceivedAt, status: "PENDING"} : null;
  if (timely) close("ANSWER", firstReceivedAt, answerRequest);
  // A window-expiry termination above has already closed every position at the captured edge;
  // no timeout close may then fire beyond the served prefix.
  else if (session.questionIndex < QUESTION_COUNT && at >= session.questionEndsAtMs) close("TIMEOUT", session.questionEndsAtMs);
  if (at >= session.endsAtMs) while (session.questionIndex < QUESTION_COUNT) close("FORCED_CUTOFF", session.endsAtMs);
  let scored;
  try {
    scored = scoreForm(snapshot, slots);
    for (let i = 0; i < initialIndex; i++) if (scored.codeAnswer[i] !== priorDigits[i]) gap("Immutable key or committed score changed");
  } catch (error) {
    // This block reads exclusively stored question/key/score material. Engine/JSON failures are
    // server gaps too; do not replace malformed stored data with a player-fault digit.
    if (newIngress) {
      stage(ingressPath(answerRequest.operationId), newIngress, true);
      stage(sessionPath(session.sessionId), {...saved, pendingAnswerOperationId: answerRequest.operationId});
    }
    return {replayed: false, pendingRepair: true, response: null, writes: [...staged.values()]};
  }
  for (const row of closed) { row.scoreDigit = Number(scored.codeAnswer[row.questionIndex]); stage(slotPath(session.sessionId, row.questionIndex), row, true); }
  session.scoreDigits = scored.codeAnswer.slice(0, session.questionIndex);
  session.lastProgressAtMs = at;
  delete session.pendingAnswerOperationId;
  // A1/A2 (D3): a close inside the envelope opens the next question's window with the captured
  // config instead of seeding its clocks. A client-driven closing operation's response IS the
  // delivery, so the clocks anchor at `at` exactly as before; a worker close leaves the window
  // OPEN for the client to claim (D4). At the envelope edge (remainder <= 0) no window opens —
  // the envelope sweep above has already terminated the attempt (D3/D4, matrix "zero remainder").
  if (closed.length && session.questionIndex < QUESTION_COUNT && session.endsAtMs > at) {
    const opened = openTournamentQuestionWindow({session, form, completedQuestionIndex: session.questionIndex - 1, openedAtMs: at});
    if (!opened) {
      // Sub-second remainder: no waiting window exists. A client-driven close IS the delivery —
      // clocks anchor at `at` with the envelope clamp (the shortened-last-question semantics);
      // a worker close cannot be claimed, so the attempt terminates at the envelope edge (D4).
      if (!serverDriven) {
        session.questionStartedAtMs = at;
        session.questionEndsAtMs = Math.min(add(at, form.questions[session.questionIndex].maxDurationMs), session.endsAtMs);
      } else {
        while (session.questionIndex < QUESTION_COUNT) close("FORCED_CUTOFF", session.endsAtMs);
      }
    } else {
      session.window = opened;
      if (!serverDriven) deliverTournamentQuestionWindow(session, form, at);
    }
  }
  let run = null;
  if (session.questionIndex === QUESTION_COUNT) {
    if (snapshot.verifiedRun) gap("Verified run exists before session settlement");
    const completedAtMs = closed.at(-1).closedAtMs;
    session.status = "SETTLED"; session.completedAtMs = completedAtMs; session.verificationVersion = VERIFICATION_VERSION;
    run = {schemaVersion: 1, status: "SETTLED", verificationVersion: VERIFICATION_VERSION, uid: session.uid, sessionId: session.sessionId,
      eventId: session.eventId, kind: session.kind, ...(session.roundNumber ? {roundNumber: session.roundNumber} : {}),
      ...(session.matchId ? {matchId: session.matchId} : {}), formId: session.formId, formFingerprint: session.formFingerprint,
      codeAnswer: scored.codeAnswer, percentScore: scored.percentScore, startedAtMs: session.startedAtMs,
      completedAtMs, elapsedMs: completedAtMs - session.startedAtMs, settledAtMs: at, servedQuestionCount: QUESTION_COUNT};
    stage(runPath(session.sessionId), run, true);
  }
  stage(sessionPath(session.sessionId), session);
  const status = run ? "COMPLETED" : timely ? "ANSWER_ACCEPTED" : closed.length ? "QUESTION_TIMED_OUT" : "CURRENT_QUESTION";
  const response = responseFor(request, session, form, at, status, closed[0], run);
  if (timely) {
    stage(ingressPath(answerRequest.operationId), {...(ingress || newIngress), status: "COMMITTED", committedAtMs: at}, !!newIngress);
    if (answerRequest.operationId !== request.operationId) {
      const originalResponse = responseFor(answerRequest, session, form, at, status, closed[0], run);
      stage(opPath(answerRequest.operationId), responseReceipt(answerRequest, originalResponse, at), true);
    }
  }
  stage(opPath(request.operationId), responseReceipt(request, response, at), true);
  return {replayed: false, response, writes: [...staged.values()]};
}

function writeTournamentSessionOperation({db, transaction, plan}) {
  for (const write of plan.writes) {
    if (write.create) transaction.create(db.doc(write.path), write.data);
    else transaction.set(db.doc(write.path), write.data);
  }
}
/** Client mutation entry. `serverDriven` is a trusted controller input like `now`; client callables
 * never set it. */
async function applyTournamentSessionOperation({db, uid, payload, now, serverDriven = false}) {
  let plan;
  try {
    plan = await db.runTransaction(async transaction => {
      const snapshot = await readTournamentSessionOperation({db, transaction, uid, payload});
      const planned = planTournamentSessionOperation({snapshot, now, serverDriven});
      writeTournamentSessionOperation({db, transaction, plan: planned}); return planned;
    });
  } catch (error) {
    if (error.code === "SCORING_UNAVAILABLE") fail("UNAVAILABLE", "Server session/form state requires repair; retry the same operation id");
    throw error;
  }
  if (plan.pendingRepair) fail("UNAVAILABLE", "Scoring snapshot unavailable; a valid timely answer retains its server arrival. Retry the same operation id");
  return plan.response;
}

/** SERVER WORKER ONLY. Never expose as an unauthenticated callable or accept a claimed uid.
 * Server-driven (AD-23): closes, terminates expired windows and settles, but never delivers.
 */
async function advanceTournamentSession({db, sessionId: id, operationId: idempotencyId, now}) {
  sessionId(id); operationId(idempotencyId);
  const snapshot = await db.doc(sessionPath(id)).get();
  if (!snapshot.exists) fail("NOT_FOUND", "Tournament session not found");
  const session = snapshot.data();
  return applyTournamentSessionOperation({db, uid: session.uid, now, serverDriven: true,
    payload: {kind: "ADVANCE", ownerUid: session.uid, operationId: idempotencyId, sessionId: id, formFingerprint: session.formFingerprint}});
}

module.exports = {SESSION_OPERATIONS, SESSION_INGRESS, VERIFIED_RUNS, VERIFICATION_VERSION, QUESTION_COUNT, MAX_ANSWER_BYTES,
  normalizeSessionRequest, readTournamentSessionOperation, planTournamentSessionOperation, writeTournamentSessionOperation,
  applyTournamentSessionOperation, advanceTournamentSession, openTournamentQuestionWindow, deliverTournamentQuestionWindow,
  checkedBoundaryWindow};
