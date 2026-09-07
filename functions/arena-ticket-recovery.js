"use strict";

const {ArenaTicketError, ticketDocumentId, attemptDocumentId, utcDayId} = require("./arena-tickets");
const {safeRedactedOrSurvey} = require("./arena-public-question");

const COLLECTION = "arena_entry_tickets";
const MAX_PAGE_SIZE = 20;
const MAX_QUESTIONS = 20;
const MAX_PUBLIC_BYTES = 500_000;
const TICKET_ID = /^[a-f0-9]{64}$/;
const HEADER_FIELDS = Object.freeze(["schemaVersion", "ticketId", "attemptId", "uid", "requestId",
  "mode", "difficulty", "lessonId", "lessonVersion", "authorUid", "ratingVersion", "priceNolics",
  "lifeCost", "issuedAtMs", "dayId", "status", "contentContext", "terminal"]);

function fail(code) { throw new ArenaTicketError(code); }
function unavailable(condition) { if (!condition) fail("UNAVAILABLE"); }
function object(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
function documentId(value) {
  return typeof value === "string" && value.trim().length > 0 && !value.includes("/") &&
    ![".", ".."].includes(value) && Buffer.byteLength(value) <= 1500;
}
function unsigned(value, min = 0) { return Number.isSafeInteger(value) && value >= min; }
function hexId(value) { return typeof value === "string" && TICKET_ID.test(value); }
function request(input, fields) {
  if (!object(input) || Reflect.ownKeys(input).some((key) => !["uid", "payload"].includes(key))) fail("INVALID_ARGUMENT");
  if (!documentId(input.uid)) fail("UNAUTHENTICATED");
  if (!object(input.payload) || Reflect.ownKeys(input.payload).some((key) => !fields.includes(key))) fail("INVALID_ARGUMENT");
  return input;
}
function contentContext(value) {
  if (value === null) return null;
  unavailable(object(value));
  const result = {};
  for (const field of ["catalogId", "questId", "sectionId", "themeId"]) {
    unavailable(documentId(value[field])); result[field] = value[field];
  }
  return result;
}
function header(raw, documentName, uid) {
  unavailable(object(raw) && raw.schemaVersion === 1 && raw.uid === uid && documentId(raw.requestId));
  unavailable(hexId(documentName) && raw.ticketId === documentName &&
    raw.ticketId === ticketDocumentId(uid, raw.requestId) && raw.attemptId === attemptDocumentId(documentName));
  unavailable(["RANDOM", "SELECTED"].includes(raw.mode) && ["EASY", "HARD"].includes(raw.difficulty));
  unavailable(["RESERVED", "CANCELLED", "SETTLED"].includes(raw.status));
  unavailable(documentId(raw.lessonId) && unsigned(raw.lessonVersion, 1) && documentId(raw.authorUid));
  unavailable(unsigned(raw.ratingVersion) && unsigned(raw.issuedAtMs) && raw.issuedAtMs <= 8_640_000_000_000_000);
  unavailable(raw.dayId === utcDayId(raw.issuedAtMs) && unsigned(raw.lifeCost, 1) && unsigned(raw.priceNolics));
  unavailable(raw.mode === "RANDOM" ? raw.priceNolics === 0 : raw.priceNolics >= 1 && raw.priceNolics <= 100);
  return {schemaVersion: 1, ticketId: raw.ticketId, attemptId: raw.attemptId, uid,
    requestId: raw.requestId, mode: raw.mode, difficulty: raw.difficulty,
    lessonId: raw.lessonId, lessonVersion: raw.lessonVersion, authorUid: raw.authorUid,
    ratingVersion: raw.ratingVersion, priceNolics: raw.priceNolics, lifeCost: raw.lifeCost,
    issuedAtMs: raw.issuedAtMs, dayId: raw.dayId, status: raw.status,
    contentContext: contentContext(raw.contentContext)};
}
function terminal(raw, ticket) {
  if (ticket.status === "RESERVED") { unavailable(raw === null); return null; }
  unavailable(object(raw));
  if (ticket.status === "CANCELLED") {
    unavailable(raw.receiptId === `arena-cancel:${ticket.ticketId}` && unsigned(raw.cancelledAtMs) &&
      raw.cancelledAtMs >= ticket.issuedAtMs && raw.releasedNolics === ticket.priceNolics && raw.releasedLifePoints === ticket.lifeCost);
    return {receiptId: raw.receiptId, cancelledAtMs: raw.cancelledAtMs,
      releasedNolics: raw.releasedNolics, releasedLifePoints: raw.releasedLifePoints};
  }
  unavailable(raw.receiptId === `arena-settle:${ticket.ticketId}` && raw.attemptId === ticket.attemptId &&
    unsigned(raw.settledAtMs) && raw.settledAtMs >= ticket.issuedAtMs &&
    typeof raw.percentScore === "number" && Number.isFinite(raw.percentScore) && raw.percentScore >= 0 && raw.percentScore <= 100);
  unavailable(raw.consumedNolics === ticket.priceNolics && raw.consumedLifePoints === ticket.lifeCost &&
    unsigned(raw.authorCreditNolics) && unsigned(raw.sinkNolics) && Number.isSafeInteger(raw.royaltyReserveDeltaNolics));
  unavailable(BigInt(raw.authorCreditNolics) + BigInt(raw.sinkNolics) + BigInt(raw.royaltyReserveDeltaNolics) === BigInt(ticket.priceNolics));
  return {receiptId: raw.receiptId, attemptId: raw.attemptId, settledAtMs: raw.settledAtMs,
    percentScore: raw.percentScore, consumedNolics: raw.consumedNolics, consumedLifePoints: raw.consumedLifePoints,
    authorCreditNolics: raw.authorCreditNolics, sinkNolics: raw.sinkNolics,
    royaltyReserveDeltaNolics: raw.royaltyReserveDeltaNolics};
}
function publicQuestions(rows, ticket) {
  unavailable(Array.isArray(rows) && rows.length > 0 && rows.length <= MAX_QUESTIONS);
  const seen = new Set();
  return rows.map((row, order) => {
    unavailable(object(row) && documentId(row.questionId) && unsigned(row.questionVersion, 1) && !seen.has(row.questionId));
    seen.add(row.questionId);
    const question = row.publicQuestion;
    unavailable(object(question) && question.id === row.questionId && question.version === row.questionVersion &&
      question.lessonId === ticket.lessonId && question.difficulty === ticket.difficulty && question.order === order &&
      typeof question.language === "string" && typeof question.payload === "string" &&
      question.payload.length > 0 && Buffer.byteLength(question.payload) <= MAX_PUBLIC_BYTES);
    let parsed;
    try { parsed = JSON.parse(question.payload); } catch (_) { fail("UNAVAILABLE"); }
    unavailable(object(parsed));
    if (ticket.difficulty === "HARD") unavailable(safeRedactedOrSurvey(question.payload,
      {questionId: row.questionId, difficulty: ticket.difficulty, gradingKey: row.gradingKey}));
    // This declared opaque field is the already-issued public payload, never rebuilt from live
    // questions/private grading keys. EASY intentionally retains its original offline hints.
    return {questionId: row.questionId, questionVersion: row.questionVersion,
      publicQuestion: {id: question.id, lessonId: question.lessonId, version: question.version,
        difficulty: question.difficulty, order: question.order, payload: question.payload, language: question.language}};
  });
}
function listEntry(ticket) {
  return {schemaVersion: ticket.schemaVersion, ticketId: ticket.ticketId, attemptId: ticket.attemptId,
    uid: ticket.uid, requestId: ticket.requestId, mode: ticket.mode, difficulty: ticket.difficulty,
    lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion, status: ticket.status,
    priceNolics: ticket.priceNolics, lifeCost: ticket.lifeCost, issuedAtMs: ticket.issuedAtMs, dayId: ticket.dayId};
}
async function read(operation) {
  try { return await operation(); } catch (error) {
    if (error instanceof ArenaTicketError) throw error;
    fail("UNAVAILABLE");
  }
}

/**
 * Auth uid comes only from the controller, never payload. Read-only: no wallet, regeneration,
 * quota, transaction, request creation, ticket creation or acknowledgement exists in this API.
 * List needs a collection index (uid ASC, status ASC, __name__ ASC). The raw document-ID cursor
 * is exclusive and need not still exist; each page is one query snapshot, not an atomic inventory
 * across pages. A new reservation sorting before a previous cursor appears on a fresh scan.
 */
function createArenaTicketRecovery({db}) {
  if (!db || typeof db.doc !== "function" || typeof db.collection !== "function") throw new TypeError("Firestore db required");
  async function list(input) {
    const {uid, payload} = request(input, ["afterTicketId", "pageSize"]);
    const size = payload.pageSize === undefined ? MAX_PAGE_SIZE : payload.pageSize;
    if (!unsigned(size, 1) || size > MAX_PAGE_SIZE ||
      (payload.afterTicketId !== undefined && !hexId(payload.afterTicketId))) fail("INVALID_ARGUMENT");
    return read(async () => {
      let query = db.collection(COLLECTION).where("uid", "==", uid).where("status", "==", "RESERVED")
        .orderBy("__name__", "asc").select(...HEADER_FIELDS);
      if (payload.afterTicketId !== undefined) query = query.startAfter(payload.afterTicketId);
      const snapshot = await query.limit(size + 1).get();
      unavailable(snapshot && Array.isArray(snapshot.docs) && snapshot.docs.length <= size + 1);
      let previousId = payload.afterTicketId || "";
      const entries = snapshot.docs.map((doc) => {
        unavailable(doc && typeof doc.data === "function" && hexId(doc.id) && doc.id > previousId);
        previousId = doc.id;
        const raw = doc.data(); const ticket = header(raw, doc.id, uid);
        unavailable(ticket.status === "RESERVED"); terminal(raw.terminal, ticket);
        return listEntry(ticket);
      });
      return {tickets: entries.slice(0, size), nextTicketId: entries.length > size ? entries[size - 1].ticketId : null};
    });
  }
  async function get(input) {
    const {uid, payload} = request(input, ["ticketId"]);
    if (!hexId(payload.ticketId)) fail("INVALID_ARGUMENT");
    return read(async () => {
      // Exactly one document read: ownership, frozen content and terminal state share a snapshot.
      const snapshot = await db.doc(`${COLLECTION}/${payload.ticketId}`).get();
      unavailable(snapshot && typeof snapshot.exists === "boolean");
      if (!snapshot.exists) fail("NOT_FOUND");
      unavailable(typeof snapshot.data === "function");
      const raw = snapshot.data(); unavailable(object(raw) && documentId(raw.uid));
      if (raw.uid !== uid) fail("NOT_FOUND");
      const ticket = header(raw, payload.ticketId, uid);
      const result = {status: "ISSUED", ticket: {...ticket, terminal: terminal(raw.terminal, ticket),
        questions: publicQuestions(raw.questions, ticket)}};
      unavailable(Buffer.byteLength(JSON.stringify(result)) <= MAX_PUBLIC_BYTES);
      return result;
    });
  }
  async function status(input) {
    const {uid, payload} = request(input, ["ticketId"]);
    if (!hexId(payload.ticketId)) fail("INVALID_ARGUMENT");
    return read(async () => {
      // One snapshot binds the owner/header to its permanent terminal receipt. This deliberately
      // does not inspect questions, grading keys or a live hierarchy: a damaged frozen bank must
      // not hide an already-committed cancellation. It is NOT permission to launch or an ACK.
      const snapshot = await db.doc(`${COLLECTION}/${payload.ticketId}`).get();
      unavailable(snapshot && typeof snapshot.exists === "boolean");
      if (!snapshot.exists) fail("NOT_FOUND");
      unavailable(typeof snapshot.data === "function");
      const raw = snapshot.data(); unavailable(object(raw) && documentId(raw.uid));
      if (raw.uid !== uid) fail("NOT_FOUND");
      const ticket = header(raw, payload.ticketId, uid);
      const receipt = terminal(raw.terminal, ticket);
      if (ticket.status === "SETTLED" && ticket.mode === "RANDOM") {
        // Free random tickets never touch the author's fractional royalty account.
        unavailable(receipt.authorCreditNolics === 0 && receipt.sinkNolics === 0 && receipt.royaltyReserveDeltaNolics === 0);
      }
      return {ticket: {...ticket, terminal: receipt}};
    });
  }
  return {list, get, status};
}

module.exports = {createArenaTicketRecovery, MAX_PAGE_SIZE};
