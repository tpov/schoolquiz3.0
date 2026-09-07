"use strict";

const {createHash} = require("node:crypto");
const {assignLessonPrices, compareScores, POLICY_VERSION} = require("./arena-policy");

const CATALOG_VERSION = "arena-lesson-catalog-v1";
const LIMITS = Object.freeze({placements: 1000, queryDocuments: 1000, readDocuments: 12000,
  defaultPageSize: 20, pageSize: 50, responseBytes: 900000, cursorBytes: 4096});
const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const data = (snapshot) => snapshot.exists ? snapshot.data() || {} : null;
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
const optionalInteger = (value, minimum = 0) => Number.isSafeInteger(value) && value >= minimum ? value : null;
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
 * Existing runtime parity deliberately includes stale/ineligible raw Arena placements in the
 * pricing universe. Only visible cards require matching current author/version and eligibility.
 * This pre-existing pricing influence remains a release gap; do not silently price only cards.
 * Metadata eligibility is NOT proof that a particular difficulty bank can issue a ticket.
 */
function createArenaLessonCatalog({db, loadLessonContext, nowMs = Date.now}) {
  if (!db || typeof db.runTransaction !== "function" || typeof loadLessonContext !== "function" || typeof nowMs !== "function") throw new TypeError("Trusted database, context loader and clock required");

  async function list({uid, payload}) {
    if (!validId(uid)) fail("UNAUTHENTICATED");
    only(payload, ["pageSize", "cursor"]);
    const pageSize = payload.pageSize === undefined ? LIMITS.defaultPageSize : payload.pageSize;
    if (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > LIMITS.pageSize) fail("INVALID_ARGUMENT");
    const cursor = decodeCursor(payload.cursor);
    return db.runTransaction(async (transaction) => {
      // SDK retries may reuse the SAME transaction object. All counters, queues, hashes and
      // projections belong to this callback invocation, never a transaction-identity cache.
      let consumed = 0;
      let queue = Promise.resolve();
      function read(ref, maximum = LIMITS.queryDocuments) {
        // Serialize even the root loader's Promise.all reads so concurrent requests cannot
        // oversubscribe the exact budget. +1 lookahead is included in remaining read capacity.
        const pending = queue.then(async () => {
          const remaining = LIMITS.readDocuments - consumed;
          if (remaining < 1) fail("CATALOG_TOO_LARGE");
          const isQuery = typeof ref.limit === "function";
          const bound = Math.max(0, Math.min(maximum, LIMITS.queryDocuments, remaining - 1));
          const result = await transaction.get(isQuery ? ref.limit(bound + 1) : ref);
          consumed += isQuery ? Math.max(1, result.docs.length) : 1;
          if (consumed > LIMITS.readDocuments || (isQuery && result.docs.length > bound)) fail("CATALOG_TOO_LARGE");
          return result;
        });
        queue = pending;
        return pending;
      }
      const trustedTransaction = Object.freeze({get: (ref) => read(ref),
        getAll: (...refs) => Promise.all(refs.map((ref) => read(ref)))});
      const placementSnapshot = await read(db.collection("lesson_placements").where("shelf", "==", "arena"), LIMITS.placements);
      const placements = placementSnapshot.docs.map((doc) => {
        if (!validId(doc.id)) fail("CATALOG_INVALID");
        return {lessonId: doc.id, row: doc.data() || {}};
      }).sort((a, b) => compare(a.lessonId, b.lessonId));
      const aggregates = [];
      for (const placement of placements) {
        const ratingSnapshot = await read(db.doc(`lesson_ratings/${placement.lessonId}`));
        // Only a wholly absent document means unrated. An existing partial/null aggregate
        // is corruption, even though the older issuance runtime tolerates missing fields.
        const rating = ratingSnapshot.exists ? ratingSnapshot.data() :
          {ratingSum: 0, ratingCount: 0, ratingVersion: 0};
        if (!plain(rating)) fail("CATALOG_INVALID");
        aggregates.push({lessonId: placement.lessonId, ratingSum: exact(rating.ratingSum),
          ratingCount: exact(rating.ratingCount), ratingVersion: exact(rating.ratingVersion)});
      }
      let priced;
      try { priced = assignLessonPrices(aggregates); } catch (_) { fail("CATALOG_INVALID"); }
      // ALL lessons use the approved prior-inclusive fraction, including unrated score 2.
      // Unrated price 1 is a deliberate pricing exception, not a separate sorting category.
      priced.sort((a, b) => -compareScores(a.weightedScore, b.weightedScore) || compare(a.lessonId, b.lessonId));
      const placementById = new Map(placements.map((entry) => [entry.lessonId, entry.row]));
      const ratingById = new Map(aggregates.map((entry) => [entry.lessonId, entry]));
      const hash = createHash("sha256");
      hash.update(canonical({catalogVersion: CATALOG_VERSION, policyVersion: POLICY_VERSION, pricingUniverse: aggregates}));
      const lessons = [];
      let totalEligibleLessons = 0; let afterSeen = cursor === null; let hasMore = false; let cardBytes = 0;
      for (const price of priced) {
        const lessonId = price.lessonId;
        const placement = placementById.get(lessonId);
        const context = await loadLessonContext(trustedTransaction, lessonId);
        let card = null;
        if (context && context.lessonId === lessonId && context.questType === "REGULAR" &&
            context.published === true && context.reviewApproved === true && validId(context.authorUid) &&
            optionalInteger(context.contentVersion, 1) !== null && placement.authorUid === context.authorUid &&
            placement.contentVersion === context.contentVersion) {
          const lesson = data(await read(db.doc(`lessons/${lessonId}`)));
          if (lesson && lesson.archived !== true && lesson.version === context.contentVersion && lesson.themeId === context.themeId) {
            card = {lessonId, contentVersion: context.contentVersion,
              title: typeof lesson.title === "string" && lesson.title.trim() ? lesson.title : null,
              // No verified separate public profile producer is wired here: author UID only.
              authorUid: context.authorUid,
              rating: {sum: price.ratingSum, count: price.ratingCount, weightedScore: price.weightedScore,
                ratingVersion: ratingById.get(lessonId).ratingVersion}, priceNolics: price.priceNolics,
              placement: {shelf: "arena", previousShelf: ["review", "arena", "tournament", "home", "retired", "rejected"].includes(placement.previousShelf) ? placement.previousShelf : null,
                placementVersion: optionalInteger(placement.placementVersion, 1), updatedAtMs: optionalInteger(placement.updatedAtMs)}};
          }
        }
        // Hash only the complete raw pricing universe and safe public projection/eligibility.
        // Changes to review identities, private question payloads or rejection text are NOT
        // encoded. A true eligibility change is represented by card becoming null/non-null.
        const encodedCard = canonical({lessonId, eligible: card !== null, card});
        hash.update("\n"); hash.update(encodedCard);
        if (!card) continue;
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
