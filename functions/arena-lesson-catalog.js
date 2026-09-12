"use strict";

const {createHash} = require("node:crypto");
const {compareScores, POLICY_VERSION} = require("./arena-policy");
const {createArenaEligibleUniverse} = require("./arena-eligible-universe");

const CATALOG_VERSION = "arena-lesson-catalog-v1";
const LIMITS = Object.freeze({placements: 1000, queryDocuments: 1000, readDocuments: 12000,
  defaultPageSize: 20, pageSize: 50, responseBytes: 900000, cursorBytes: 4096});
const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
function fail(code) { const error = new Error(code); error.code = code; throw error; }
function plain(value) { return value && Object.getPrototypeOf(value) === Object.prototype; }
function only(value, keys) {
  if (!plain(value) || Object.keys(value).some((key) => !keys.includes(key))) fail("INVALID_ARGUMENT");
}
function validId(value) {
  return typeof value === "string" && Boolean(value.trim()) && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value) && Buffer.byteLength(value) <= 1500;
}
function exact(value, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) fail("CATALOG_INVALID");
  return value;
}
function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (plain(value)) return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}
function encodeCursor(snapshot, afterLessonId) {
  return Buffer.from(canonical({v: 1, snapshot, afterLessonId})).toString("base64url");
}
function decodeCursor(value) {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string" || !value || Buffer.byteLength(value) > LIMITS.cursorBytes || !/^[A-Za-z0-9_-]+$/.test(value)) fail("INVALID_ARGUMENT");
  let cursor;
  try { cursor = JSON.parse(Buffer.from(value, "base64url").toString("utf8")); } catch (_) { fail("INVALID_ARGUMENT"); }
  only(cursor, ["v", "snapshot", "afterLessonId"]);
  if (cursor.v !== 1 || typeof cursor.snapshot !== "string" || !/^[a-f0-9]{64}$/.test(cursor.snapshot) ||
      !validId(cursor.afterLessonId) || encodeCursor(cursor.snapshot, cursor.afterLessonId) !== value) fail("INVALID_ARGUMENT");
  return cursor;
}

/** Authenticated public metadata only. Root must supply its actual independent-review/hierarchy
 * loader and map errors to callable errors. This service exposes no mutation capability to that
 * loader and never reads users, profiles, placement history, questions or private grading keys.
 *
 * The trusted universe admits only current approved, version-matched rows before price assignment.
 * Metadata eligibility is still not proof that a particular difficulty bank can issue a ticket.
 */
function createArenaLessonCatalog({db, loadLessonContext, nowMs = Date.now}) {
  if (!db || typeof db.runTransaction !== "function" || typeof loadLessonContext !== "function" || typeof nowMs !== "function") throw new TypeError("Trusted database, context loader and clock required");

  const eligibleUniverse = createArenaEligibleUniverse({db, loadLessonContext});

  async function list({uid, payload}) {
    if (!validId(uid)) fail("UNAUTHENTICATED");
    only(payload, ["pageSize", "cursor"]);
    const pageSize = payload.pageSize === undefined ? LIMITS.defaultPageSize : payload.pageSize;
    if (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > LIMITS.pageSize) fail("INVALID_ARGUMENT");
    const cursor = decodeCursor(payload.cursor);
    return db.runTransaction(async (transaction) => {
      const reader = eligibleUniverse.forTransaction(transaction);
      let universe;
      try { universe = await reader.read(); } catch (error) {
        if (error.code === "UNIVERSE_TOO_LARGE") fail("CATALOG_TOO_LARGE");
        if (error.code === "UNIVERSE_INVALID") fail("CATALOG_INVALID");
        throw error;
      }
      const priced = [...universe.rows].sort((a, b) =>
        -compareScores(a.rating.weightedScore, b.rating.weightedScore) || compare(a.lessonId, b.lessonId));
      const aggregates = universe.rows.map((row) => ({lessonId: row.lessonId, ratingSum: row.rating.sum,
        ratingCount: row.rating.count, ratingVersion: row.rating.version}));
      const hash = createHash("sha256");
      hash.update(canonical({catalogVersion: CATALOG_VERSION, policyVersion: POLICY_VERSION,
        pricingUniverse: aggregates}));
      const lessons = [];
      let totalEligibleLessons = 0; let afterSeen = cursor === null; let hasMore = false; let cardBytes = 0;
      for (const price of priced) {
        const lessonId = price.lessonId;
        const card = {lessonId, contentVersion: price.contentVersion, title: price.title,
          authorUid: price.authorUid, rating: {sum: price.rating.sum, count: price.rating.count,
            weightedScore: price.rating.weightedScore, ratingVersion: price.rating.version},
          priceNolics: price.priceNolics, placement: {shelf: "arena",
            previousShelf: price.placement.previousShelf, placementVersion: price.placement.version,
            updatedAtMs: price.placement.updatedAtMs}};
        // Hash the complete current eligible universe and its safe public projection. Review
        // identities, private payloads and rejection text remain outside the snapshot contract.
        hash.update("\n"); hash.update(canonical({lessonId, card}));
        totalEligibleLessons++;
        if (!afterSeen) {
          if (lessonId === cursor.afterLessonId) afterSeen = true;
          continue;
        }
        if (lessons.length < pageSize) {
          cardBytes += Buffer.byteLength(canonical(card));
          if (cardBytes > LIMITS.responseBytes) fail("CATALOG_TOO_LARGE");
          lessons.push(card);
        } else hasMore = true;
      }
      const snapshotFingerprint = hash.digest("hex");
      if (cursor && (cursor.snapshot !== snapshotFingerprint || !afterSeen)) fail("CATALOG_CHANGED");
      const response = {status: "OK", catalogVersion: CATALOG_VERSION, snapshotFingerprint,
        serverNowMs: exact(nowMs()), entryAvailability: "PREPARE_REQUIRED", totalEligibleLessons,
        lessons, nextCursor: hasMore ? encodeCursor(snapshotFingerprint, lessons[lessons.length - 1].lessonId) : null};
      // A quote is not a reserved price. A new selected prepare must still submit the user's
      // explicitly confirmed maxPriceNolics; this reader never creates reservations/receipts.
      if (Buffer.byteLength(canonical(response)) > LIMITS.responseBytes) fail("CATALOG_TOO_LARGE");
      return response;
    });
  }
  return Object.freeze({list});
}

module.exports = {createArenaLessonCatalog, CATALOG_VERSION, LIMITS};
