"use strict";

const {createCipheriv, createDecipheriv, randomBytes} = require("node:crypto");
const {LEDGER_PATH, CURRENCY, REASON} = require("./currency-ledger");

const VIEW_VERSION = "arena-lifecycle-views-v1";
const REVIEW_SUBMISSION_VIEW_VERSION = "quest-review-submissions-v1";
const LIMITS = Object.freeze({defaultPageSize: 20, maxPageSize: 20, cursorBytes: 4096, responseBytes: 100_000});
const SHELVES = new Set(["review", "arena", "tournament", "home", "retired", "rejected"]);
const HISTORY_STATUSES = new Set(["APPLIED", "REJECTED"]);
const REJECTION_CODES = new Set([
  "UNSUPPORTED_PLACEMENT",
  "INVALID_TRANSITION",
  "LESSON_NOT_PUBLISHED",
  "REVIEW_APPROVAL_REQUIRED",
  "REVIEW_REJECTED",
]);
const FIRST_TRANSITION_REJECTION_CODES = new Set([
  "UNSUPPORTED_PLACEMENT",
  "INVALID_TRANSITION",
  "LESSON_NOT_PUBLISHED",
  "REVIEW_APPROVAL_REQUIRED",
]);
const CURSOR_VERSION = 1;
const CURSOR_NONCE_BYTES = 12;
const CURSOR_TAG_BYTES = 16;
const REVIEW_SUBMISSION_STATUSES = new Set(["PROCESSING", "UNDER_REVIEW", "PUBLISHED", "REJECTED"]);
// Stages the submission document may name as not passed; the author reads them verbatim (CAP-3).
const REVIEW_STAGES = new Set(["TESTING", "LOGIC", "TRANSLATION"]);
// A reviewer writes this explanation specifically for the author. It is not a free pass to ship
// an unbounded request document through a callable response.
const MAX_AUTHOR_REJECTION_REASON_BYTES = 1000;

class ArenaLifecycleViewError extends Error {
  constructor(code) {
    super(code);
    this.name = "ArenaLifecycleViewError";
    this.code = code;
  }
}

function fail(code) { throw new ArenaLifecycleViewError(code); }
function unavailable(condition) { if (!condition) fail("UNAVAILABLE"); }
function plain(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
function identifier(value) {
  return typeof value === "string" && value.trim().length > 0 && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value) && Buffer.byteLength(value) <= 1500;
}
function unsigned(value, minimum = 0) {
  return Number.isSafeInteger(value) && value >= minimum && value <= Number.MAX_SAFE_INTEGER;
}
function only(value, fields) {
  if (!plain(value) || Reflect.ownKeys(value).some((key) => !fields.includes(key))) fail("INVALID_ARGUMENT");
}
function canonical(value) {
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]";
  if (plain(value)) return "{" + Object.keys(value).sort().map((key) => JSON.stringify(key) + ":" + canonical(value[key])).join(",") + "}";
  return JSON.stringify(value);
}
function readRequest(input, fields) {
  only(input, ["uid", "payload"]);
  if (!identifier(input.uid)) fail("UNAUTHENTICATED");
  only(input.payload, fields);
  return input;
}
function pageSize(payload) {
  const value = payload.pageSize === undefined ? LIMITS.defaultPageSize : payload.pageSize;
  if (!unsigned(value, 1) || value > LIMITS.maxPageSize) fail("INVALID_ARGUMENT");
  return value;
}
function inputIdentifierList(value) {
  if (!Array.isArray(value) || value.length > LIMITS.maxPageSize) fail("INVALID_ARGUMENT");
  const values = value.map(item => {
    if (!identifier(item)) fail("INVALID_ARGUMENT");
    return item;
  });
  if (new Set(values).size !== values.length) fail("INVALID_ARGUMENT");
  return values.sort();
}
function safeShelf(value) { return typeof value === "string" && SHELVES.has(value) ? value : null; }
function safeReasonCode(value) { return typeof value === "string" && REJECTION_CODES.has(value) ? value : null; }
function snapshotData(snapshot) {
  unavailable(snapshot && typeof snapshot.exists === "boolean" && typeof snapshot.data === "function");
  return snapshot.exists ? snapshot.data() : null;
}
function documentId(snapshot) {
  unavailable(snapshot && identifier(snapshot.id));
  return snapshot.id;
}
function documentRows(snapshot, size) {
  unavailable(snapshot && Array.isArray(snapshot.docs) && snapshot.docs.length <= size + 1);
  return snapshot.docs;
}
function checkResponse(value) {
  if (Buffer.byteLength(JSON.stringify(value)) > LIMITS.responseBytes) fail("SOURCE_TOO_LARGE");
  return value;
}

function cursorKey(readCursorKey) {
  let raw;
  try { raw = readCursorKey(); } catch (_) { fail("UNAVAILABLE"); }
  if (Buffer.isBuffer(raw) && raw.length === 32) return Buffer.from(raw);
  if (typeof raw === "string" && /^[A-Za-z0-9_-]{43}$/.test(raw)) {
    const key = Buffer.from(raw, "base64url");
    if (key.length === 32) return key;
  }
  fail("UNAVAILABLE");
}
// Continuations carry a Firestore anchor internally, so a duplicate timestamp can resume exactly.
// Encrypt it with a server-bound key: base64url alone would expose a mutation hash or ticket ID.
function encodeCursor(scope, uid, lessonId, atMs, id, readCursorKey) {
  const nonce = randomBytes(CURSOR_NONCE_BYTES);
  const cipher = createCipheriv("aes-256-gcm", cursorKey(readCursorKey), nonce);
  const encrypted = Buffer.concat([
    cipher.update(Buffer.from(canonical({v: CURSOR_VERSION, scope, uid, lessonId, atMs, id}), "utf8")),
    cipher.final(),
  ]);
  return Buffer.concat([Buffer.from([CURSOR_VERSION]), nonce, cipher.getAuthTag(), encrypted]).toString("base64url");
}
function decodeCursor(value, scope, uid, lessonId, readCursorKey) {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string" || !value || Buffer.byteLength(value) > LIMITS.cursorBytes ||
      !/^[A-Za-z0-9_-]+$/.test(value)) fail("INVALID_ARGUMENT");
  const key = cursorKey(readCursorKey);
  let parsed;
  try {
    const raw = Buffer.from(value, "base64url");
    if (raw.length <= 1 + CURSOR_NONCE_BYTES + CURSOR_TAG_BYTES || raw[0] !== CURSOR_VERSION) fail("INVALID_ARGUMENT");
    const nonce = raw.subarray(1, 1 + CURSOR_NONCE_BYTES);
    const tag = raw.subarray(1 + CURSOR_NONCE_BYTES, 1 + CURSOR_NONCE_BYTES + CURSOR_TAG_BYTES);
    const decipher = createDecipheriv("aes-256-gcm", key, nonce);
    decipher.setAuthTag(tag);
    parsed = JSON.parse(Buffer.concat([
      decipher.update(raw.subarray(1 + CURSOR_NONCE_BYTES + CURSOR_TAG_BYTES)),
      decipher.final(),
    ]).toString("utf8"));
  } catch (_) { fail("INVALID_ARGUMENT"); }
  only(parsed, ["v", "scope", "uid", "lessonId", "atMs", "id"]);
  if (parsed.v !== CURSOR_VERSION || parsed.scope !== scope || parsed.uid !== uid || parsed.lessonId !== lessonId ||
      !unsigned(parsed.atMs) || !identifier(parsed.id)) fail("INVALID_ARGUMENT");
  return parsed;
}

function trustedContext(raw, lessonId) {
  if (raw === null || raw === undefined) return null;
  unavailable(plain(raw) && raw.lessonId === lessonId && identifier(raw.authorUid) &&
    raw.questType === "REGULAR" && unsigned(raw.contentVersion, 1) &&
    typeof raw.published === "boolean" && typeof raw.reviewApproved === "boolean");
  return {authorUid: raw.authorUid, contentVersion: raw.contentVersion,
    published: raw.published, reviewApproved: raw.reviewApproved};
}
function partialRejectionPlacement(raw, lessonId) {
  if (!plain(raw) || raw.lessonId !== lessonId || raw.shelf !== "review") return false;
  // A failed first transition is intentionally recorded for audit/history, but unlike an accepted
  // transition it only merges the immutable lesson ID, its existing REVIEW shelf and a private
  // rejection receipt. Treat exactly that legacy/current shape as no active placement.
  if (["authorUid", "questType", "previousShelf", "contentVersion", "placementVersion", "updatedAtMs"]
    .some(field => Object.prototype.hasOwnProperty.call(raw, field))) return false;
  const rejection = raw.lastRejection;
  return plain(rejection) && FIRST_TRANSITION_REJECTION_CODES.has(rejection.code) &&
    typeof rejection.reason === "string" && identifier(rejection.actorUid) && unsigned(rejection.atMs);
}
function ratingProjection(raw, lessonId) {
  const empty = {sum: 0, count: 0, average: null, version: 0, updatedAtMs: null};
  if (raw === null) return empty;
  unavailable(plain(raw) && raw.lessonId === lessonId && unsigned(raw.ratingSum) &&
    unsigned(raw.ratingCount) && raw.ratingSum >= raw.ratingCount &&
    BigInt(raw.ratingSum) <= BigInt(raw.ratingCount) * 3n &&
    unsigned(raw.ratingVersion) && unsigned(raw.ratingUpdatedAtMs));
  const average = raw.ratingCount === 0 ? null : Math.round(raw.ratingSum / raw.ratingCount * 10) / 10;
  unavailable(raw.averageRating === average);
  return {sum: raw.ratingSum, count: raw.ratingCount, average,
    version: raw.ratingVersion, updatedAtMs: raw.ratingUpdatedAtMs};
}
function currentLifecycle(lessonId, context, rawPlacement, rating) {
  const common = {lessonId, currentContentVersion: context.contentVersion,
    published: context.published, reviewApproved: context.reviewApproved, rating};
  if (rawPlacement === null || partialRejectionPlacement(rawPlacement, lessonId)) {
    return {...common, state: "REVIEW", placement: null};
  }
  unavailable(plain(rawPlacement) && rawPlacement.lessonId === lessonId &&
    rawPlacement.authorUid === context.authorUid && rawPlacement.questType === "REGULAR" &&
    safeShelf(rawPlacement.shelf) !== null && safeShelf(rawPlacement.previousShelf) !== null &&
    unsigned(rawPlacement.contentVersion, 1) && unsigned(rawPlacement.placementVersion, 1) &&
    unsigned(rawPlacement.updatedAtMs));
  // lastRejection is intentionally absent: it survives a later successful transition and cannot
  // honestly describe the current state. Rejection codes belong to the history projection below.
  return {...common, state: "PLACED", placement: {shelf: rawPlacement.shelf,
    previousShelf: rawPlacement.previousShelf, contentVersion: rawPlacement.contentVersion,
    currentVersion: rawPlacement.contentVersion === context.contentVersion,
    placementVersion: rawPlacement.placementVersion, updatedAtMs: rawPlacement.updatedAtMs}};
}
function historyEntry(snapshot) {
  const id = documentId(snapshot); const raw = snapshot.data();
  // History belongs to a lesson by its closed subcollection path. Existing rows deliberately do
  // not duplicate lessonId inside their document, so do not invent a schema requirement here.
  unavailable(plain(raw) && safeShelf(raw.fromShelf) !== null &&
    safeShelf(raw.targetShelf) !== null && HISTORY_STATUSES.has(raw.status) && unsigned(raw.decidedAtMs));
  const storedCode = raw.code === null || raw.code === undefined ? null : safeReasonCode(raw.code);
  unavailable(raw.code === null || raw.code === undefined || storedCode !== null);
  const reasonCode = storedCode || (raw.status === "APPLIED" && raw.targetShelf === "rejected" ? "REVIEW_REJECTED" : null);
  return {id, entry: {fromShelf: raw.fromShelf, targetShelf: raw.targetShelf, status: raw.status,
    reasonCode, decidedAtMs: raw.decidedAtMs}};
}
function royaltySummary(raw) {
  if (raw === null) return {totalCreditedNolics: 0, updatedAtMs: null};
  unavailable(plain(raw) && unsigned(raw.totalAuthorCreditNolics) &&
    unsigned(raw.updatedAtMs));
  return {totalCreditedNolics: raw.totalAuthorCreditNolics, updatedAtMs: raw.updatedAtMs};
}
function royaltyEntry(snapshot, uid) {
  const id = documentId(snapshot); const raw = snapshot.data();
  unavailable(plain(raw) && raw.uid === uid && raw.reason === REASON.ARENA_AUTHOR_ROYALTY &&
    raw.currency === CURRENCY.NOLICS && unsigned(raw.delta, 1) && unsigned(raw.atMs));
  return {id, entry: {creditedNolics: raw.delta, creditedAtMs: raw.atMs}};
}
function authorVisibleRejectionReason(raw) {
  if (raw === null || raw === undefined) return null;
  unavailable(typeof raw === "string" && raw.trim().length > 0 &&
    Buffer.byteLength(raw, "utf8") <= MAX_AUTHOR_REJECTION_REASON_BYTES);
  return raw.trim();
}
function reviewSubmissionEntry(snapshot, uid, expectedDraftId = null) {
  const submissionId = documentId(snapshot); const raw = snapshot.data();
  unavailable(plain(raw) && raw.ownerUid === uid && identifier(raw.draftId) &&
    REVIEW_SUBMISSION_STATUSES.has(raw.status));
  if (expectedDraftId !== null) unavailable(raw.draftId === expectedDraftId && unsigned(raw.processedAtMs));
  // Named failed stages and per-question TESTING outcomes live only while a submission can still
  // change; a terminal row keeps its history but the author's verdict is the status itself.
  const inFlight = raw.status === "PROCESSING" || raw.status === "UNDER_REVIEW";
  return {submissionId, draftId: raw.draftId, status: raw.status,
    rejectionReason: raw.status === "REJECTED" ? authorVisibleRejectionReason(raw.rejectionReason) : null,
    failedStages: inFlight ? reviewFailedStages(raw.failedStages) : [],
    questionOutcomes: inFlight ? reviewQuestionOutcomes(raw.questionOutcomes) : []};
}

function reviewFailedStages(raw) {
  if (raw === undefined || raw === null) return [];
  unavailable(Array.isArray(raw) && raw.every((stage) => REVIEW_STAGES.has(stage)));
  return [...new Set(raw)].sort();
}

function reviewQuestionOutcomes(raw) {
  if (raw === undefined || raw === null) return [];
  unavailable(Array.isArray(raw));
  const seen = new Set();
  return raw.map((outcome) => {
    unavailable(plain(outcome) && identifier(outcome.questionId) &&
      (outcome.testingScore === null || scoreAverage(outcome.testingScore)) &&
      typeof outcome.passed === "boolean");
    unavailable(seen.add(outcome.questionId));
    return {questionId: outcome.questionId, testingScore: outcome.testingScore, passed: outcome.passed};
  }).sort((a, b) => (a.questionId < b.questionId ? -1 : a.questionId > b.questionId ? 1 : 0));
}

function scoreAverage(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 3;
}

/**
 * Purpose-specific server reader for the closed lesson placement/history and royalty records.
 * It never writes or returns raw document data. The controller supplies Auth uid and the existing
 * trusted hierarchy/curator readers; payload fields cannot grant authority or widen a query.
 */
function createArenaLifecycleViews({db, loadLessonContext, isCurator, readCursorKey}) {
  if (!db || typeof db.doc !== "function" || typeof db.collection !== "function" ||
      typeof db.runTransaction !== "function" || typeof loadLessonContext !== "function" ||
      typeof isCurator !== "function" || typeof readCursorKey !== "function") {
    throw new TypeError("Trusted lifecycle dependencies required");
  }

  async function protectedRead(action) {
    try { return await action(); }
    catch (error) {
      if (error instanceof ArenaLifecycleViewError) throw error;
      fail("UNAVAILABLE");
    }
  }
  async function contextFor(tx, lessonId) {
    return trustedContext(await loadLessonContext(tx, lessonId), lessonId);
  }
  async function authorizeLesson(tx, uid, lessonId) {
    const context = await contextFor(tx, lessonId);
    if (!context) fail("NOT_FOUND");
    if (context.authorUid === uid) return context;
    if (await isCurator(tx, uid) === true) return context;
    // Do not distinguish an existing foreign lesson from a missing lesson to an ordinary client.
    fail("NOT_FOUND");
  }
  function placementQuery(query) {
    return query.orderBy("__name__", "asc");
  }
  function historyQuery(query) {
    return query.orderBy("decidedAtMs", "asc").orderBy("__name__", "asc");
  }
  function royaltyQuery(query) {
    return query.orderBy("atMs", "desc").orderBy("__name__", "desc");
  }
  async function lifecyclePage(tx, docs, size, afterLessonId, expectedOwnerUid = null) {
    const rows = []; let previous = afterLessonId || "";
    for (const doc of docs.slice(0, size)) {
      const lessonId = documentId(doc);
      unavailable(lessonId > previous); previous = lessonId;
      const context = await contextFor(tx, lessonId);
      unavailable(context);
      if (expectedOwnerUid !== null) unavailable(context.authorUid === expectedOwnerUid);
      const rating = ratingProjection(snapshotData(await tx.get(db.doc("lesson_ratings/" + lessonId))), lessonId);
      rows.push(currentLifecycle(lessonId, context, doc.data(), rating));
    }
    return rows;
  }

  async function listOwned(input) {
    const {uid, payload} = readRequest(input, ["afterLessonId", "pageSize"]);
    const size = pageSize(payload);
    if (payload.afterLessonId !== undefined && !identifier(payload.afterLessonId)) fail("INVALID_ARGUMENT");
    return protectedRead(() => db.runTransaction(async tx => {
      let query = placementQuery(db.collection("lesson_placements").where("authorUid", "==", uid));
      if (payload.afterLessonId !== undefined) query = query.startAfter(payload.afterLessonId);
      const docs = documentRows(await tx.get(query.limit(size + 1)), size);
      const entries = await lifecyclePage(tx, docs, size, payload.afterLessonId, uid);
      return checkResponse({viewVersion: VIEW_VERSION, entries,
        nextLessonId: docs.length > size ? entries.at(-1).lessonId : null});
    }));
  }

  async function listCurator(input) {
    const {uid, payload} = readRequest(input, ["afterLessonId", "pageSize"]);
    const size = pageSize(payload);
    if (payload.afterLessonId !== undefined && !identifier(payload.afterLessonId)) fail("INVALID_ARGUMENT");
    return protectedRead(() => db.runTransaction(async tx => {
      if (await isCurator(tx, uid) !== true) fail("PERMISSION_DENIED");
      let query = placementQuery(db.collection("lesson_placements"));
      if (payload.afterLessonId !== undefined) query = query.startAfter(payload.afterLessonId);
      const docs = documentRows(await tx.get(query.limit(size + 1)), size);
      const entries = await lifecyclePage(tx, docs, size, payload.afterLessonId);
      return checkResponse({viewVersion: VIEW_VERSION, entries,
        nextLessonId: docs.length > size ? entries.at(-1).lessonId : null});
    }));
  }

  async function get(input) {
    const {uid, payload} = readRequest(input, ["lessonId"]);
    if (!identifier(payload.lessonId)) fail("INVALID_ARGUMENT");
    return protectedRead(() => db.runTransaction(async tx => {
      const context = await authorizeLesson(tx, uid, payload.lessonId);
      const [placementSnapshot, ratingSnapshot] = await Promise.all([
        tx.get(db.doc("lesson_placements/" + payload.lessonId)),
        tx.get(db.doc("lesson_ratings/" + payload.lessonId)),
      ]);
      const placement = snapshotData(placementSnapshot);
      const rating = ratingProjection(snapshotData(ratingSnapshot), payload.lessonId);
      return checkResponse({viewVersion: VIEW_VERSION,
        lifecycle: currentLifecycle(payload.lessonId, context, placement, rating)});
    }));
  }

  async function listHistory(input) {
    const {uid, payload} = readRequest(input, ["lessonId", "cursor", "pageSize"]);
    if (!identifier(payload.lessonId)) fail("INVALID_ARGUMENT");
    const size = pageSize(payload); const cursor = decodeCursor(payload.cursor, "history", uid, payload.lessonId, readCursorKey);
    return protectedRead(() => db.runTransaction(async tx => {
      await authorizeLesson(tx, uid, payload.lessonId);
      let query = historyQuery(db.collection("lesson_placements/" + payload.lessonId + "/history"));
      if (cursor) query = query.startAfter(cursor.atMs, cursor.id);
      const docs = documentRows(await tx.get(query.limit(size + 1)), size);
      const rows = docs.slice(0, size).map(historyEntry);
      let previous = cursor ? [cursor.atMs, cursor.id] : null;
      for (const row of rows) {
        if (previous) unavailable(row.entry.decidedAtMs > previous[0] ||
          row.entry.decidedAtMs === previous[0] && row.id > previous[1]);
        previous = [row.entry.decidedAtMs, row.id];
      }
      const tail = rows.at(-1);
      return checkResponse({viewVersion: VIEW_VERSION, entries: rows.map(row => row.entry),
        nextCursor: docs.length > size ?
          encodeCursor("history", uid, payload.lessonId, tail.entry.decidedAtMs, tail.id, readCursorKey) : null});
    }));
  }

  async function listMyRoyalty(input) {
    const {uid, payload} = readRequest(input, ["cursor", "pageSize"]);
    const size = pageSize(payload); const cursor = decodeCursor(payload.cursor, "royalty", uid, null, readCursorKey);
    return protectedRead(() => db.runTransaction(async tx => {
      const account = royaltySummary(snapshotData(await tx.get(db.doc("arena_royalty_accounts/" + uid))));
      let query = royaltyQuery(db.collection(LEDGER_PATH).where("uid", "==", uid)
        .where("reason", "==", REASON.ARENA_AUTHOR_ROYALTY));
      if (cursor) query = query.startAfter(cursor.atMs, cursor.id);
      const docs = documentRows(await tx.get(query.limit(size + 1)), size);
      const rows = docs.slice(0, size).map(doc => royaltyEntry(doc, uid));
      let previous = cursor ? [cursor.atMs, cursor.id] : null;
      for (const row of rows) {
        if (previous) unavailable(row.entry.creditedAtMs < previous[0] ||
          row.entry.creditedAtMs === previous[0] && row.id < previous[1]);
        previous = [row.entry.creditedAtMs, row.id];
      }
      const tail = rows.at(-1);
      return checkResponse({viewVersion: VIEW_VERSION, summary: account, entries: rows.map(row => row.entry),
        nextCursor: docs.length > size ?
          encodeCursor("royalty", uid, null, tail.entry.creditedAtMs, tail.id, readCursorKey) : null});
    }));
  }

  /**
   * The author only needs the state of submissions still named by their local draft. Reading the
   * whole request collection would both leak the complete request body and make old terminal rows
   * capable of settling a later resubmission. Current submission IDs are exact document reads;
   * legacy drafts without a persisted ID get one bounded, server-ordered recovery lookup.
   */
  async function listMyQuestReviewSubmissions(input) {
    const {uid, payload} = readRequest(input, ["submissionIds", "legacyDraftIds"]);
    const submissionIds = inputIdentifierList(payload.submissionIds);
    const legacyDraftIds = inputIdentifierList(payload.legacyDraftIds);
    if (submissionIds.length + legacyDraftIds.length > LIMITS.maxPageSize) fail("INVALID_ARGUMENT");
    return protectedRead(() => db.runTransaction(async tx => {
      const entries = [];
      for (const submissionId of submissionIds) {
        const snapshot = await tx.get(db.doc("quest_review_requests/" + submissionId));
        if (!snapshot.exists) continue;
        const raw = snapshot.data();
        // A guessed foreign document is deliberately indistinguishable from an absent one.
        if (!plain(raw) || raw.ownerUid !== uid) continue;
        entries.push(reviewSubmissionEntry(snapshot, uid));
      }
      for (const draftId of legacyDraftIds) {
        const query = db.collection("quest_review_requests")
          .where("ownerUid", "==", uid).where("draftId", "==", draftId)
          .orderBy("processedAtMs", "desc").orderBy("__name__", "desc").limit(1);
        const docs = documentRows(await tx.get(query), 1);
        if (docs.length === 1) entries.push(reviewSubmissionEntry(docs[0], uid, draftId));
      }
      const deduplicated = [...new Map(entries.map(entry => [entry.submissionId, entry])).values()];
      return checkResponse({viewVersion: REVIEW_SUBMISSION_VIEW_VERSION, entries: deduplicated});
    }));
  }

  return Object.freeze({listOwned, listCurator, get, listHistory, listMyRoyalty, listMyQuestReviewSubmissions});
}

module.exports = {
  ArenaLifecycleViewError, LIMITS, VIEW_VERSION, REVIEW_SUBMISSION_VIEW_VERSION,
  MAX_AUTHOR_REJECTION_REASON_BYTES, createArenaLifecycleViews,
};
