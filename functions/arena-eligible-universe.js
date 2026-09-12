"use strict";

const {assignLessonPrices} = require("./arena-policy");

const LIMITS = Object.freeze({placements: 1000, queryDocuments: 1000, readDocuments: 12000});
const SHELVES = new Set(["review", "arena", "tournament", "home", "retired", "rejected"]);

function fail(code) { const error = new Error(code); error.code = code; throw error; }
function plain(value) { return value && Object.getPrototypeOf(value) === Object.prototype; }
function compare(left, right) { return left < right ? -1 : left > right ? 1 : 0; }
function validId(value) {
  return typeof value === "string" && Boolean(value.trim()) && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value) && Buffer.byteLength(value) <= 1500;
}
function exact(value, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) fail("UNIVERSE_INVALID");
  return value;
}
function optionalInteger(value, minimum = 0) {
  return Number.isSafeInteger(value) && value >= minimum ? value : null;
}
function snapshotData(snapshot) { return snapshot.exists ? snapshot.data() : null; }

/**
 * Trusted, read-only pricing universe for the three Arena entry paths. It deliberately does not
 * claim global eligibility: callers create one reader inside one Firestore transaction callback.
 * The reader serializes all root-loader reads, including Promise.all, and memoizes its one result
 * only for that callback invocation. Never retain a reader by transaction object: Firestore may
 * reuse the object when it retries a callback against different committed state.
 */
function createArenaEligibleUniverse({db, loadLessonContext}) {
  if (!db || typeof db.collection !== "function" || typeof db.doc !== "function" ||
      typeof loadLessonContext !== "function") {
    throw new TypeError("Trusted database and lesson context loader required");
  }

  function forTransaction(transaction) {
    if (!transaction || typeof transaction.get !== "function") throw new TypeError("Firestore transaction required");
    let consumed = 0;
    let queue = Promise.resolve();
    let universe = null;
    // Firestore bills a repeated document get twice unless we avoid issuing it. The trusted root
    // loader and the post-context parity check necessarily meet at lessons/{id}; retry callbacks
    // receive a new reader, so this map is strictly callback-local. Queries are deliberately not
    // memoized: their bounded result is part of the current eligibility proof.
    const documentSnapshots = new Map();

    function read(ref, maximum = LIMITS.queryDocuments) {
      // All reads must use this wrapper. A root loader is allowed to call getAll/Promise.all, but
      // its members are sequenced here so the shared exact document budget cannot oversubscribe.
      const isQuery = typeof ref.limit === "function";
      const path = !isQuery && typeof ref.path === "string" ? ref.path : null;
      if (path && documentSnapshots.has(path)) return documentSnapshots.get(path);
      const pending = queue.then(async () => {
        const remaining = LIMITS.readDocuments - consumed;
        if (remaining < 1) fail("UNIVERSE_TOO_LARGE");
        const bound = Math.max(0, Math.min(maximum, LIMITS.queryDocuments, remaining - 1));
        const result = await transaction.get(isQuery ? ref.limit(bound + 1) : ref);
        consumed += isQuery ? Math.max(1, result.docs.length) : 1;
        if (consumed > LIMITS.readDocuments || (isQuery && result.docs.length > bound)) {
          fail("UNIVERSE_TOO_LARGE");
        }
        return result;
      });
      queue = pending;
      if (path) documentSnapshots.set(path, pending);
      return pending;
    }

    function requireRemaining(documents) {
      if (!Number.isSafeInteger(documents) || documents < 1) throw new TypeError("positive document reserve required");
      const pending = queue.then(() => {
        if (LIMITS.readDocuments - consumed < documents) fail("UNIVERSE_TOO_LARGE");
      });
      // A failed capacity check is terminal for this callback: a later call cannot bypass it by
      // starting a fresh queue after the rejected promise.
      queue = pending;
      return pending;
    }

    const trustedReader = Object.freeze({
      get: (ref) => read(ref),
      getAll: (...refs) => Promise.all(refs.map((ref) => read(ref))),
      requireRemaining,
    });

    function contextFor(lessonId, raw) {
      if (raw == null) return null;
      if (!plain(raw) || raw.lessonId !== lessonId || typeof raw.questType !== "string" ||
          typeof raw.published !== "boolean" || typeof raw.reviewApproved !== "boolean" || !validId(raw.authorUid) ||
          !validId(raw.themeId) || !Number.isSafeInteger(raw.contentVersion) || raw.contentVersion < 1) {
        fail("UNIVERSE_INVALID");
      }
      if (raw.questType !== "REGULAR" || !raw.published || !raw.reviewApproved) return null;
      return {authorUid: raw.authorUid, contentVersion: raw.contentVersion, themeId: raw.themeId};
    }

    async function build() {
      const placementSnapshot = await read(
        db.collection("lesson_placements").where("shelf", "==", "arena"),
        LIMITS.placements,
      );
      const placements = placementSnapshot.docs.map((doc) => {
        if (!validId(doc.id) || !plain(doc.data())) fail("UNIVERSE_INVALID");
        const row = doc.data();
        if (row.shelf !== "arena") fail("UNIVERSE_INVALID");
        return {lessonId: doc.id, authorUid: row.authorUid, contentVersion: row.contentVersion,
          previousShelf: SHELVES.has(row.previousShelf) ? row.previousShelf : null,
          placementVersion: optionalInteger(row.placementVersion, 1),
          updatedAtMs: optionalInteger(row.updatedAtMs)};
      }).sort((left, right) => compare(left.lessonId, right.lessonId));
      if (new Set(placements.map((row) => row.lessonId)).size !== placements.length) fail("UNIVERSE_INVALID");

      const eligible = [];
      for (const placement of placements) {
        // Missing/mismatched placement identity is stale eligibility, not trusted metadata.
        if (!validId(placement.authorUid) || !Number.isSafeInteger(placement.contentVersion) ||
            placement.contentVersion < 1) continue;
        const context = contextFor(placement.lessonId, await loadLessonContext(trustedReader, placement.lessonId));
        if (!context || context.authorUid !== placement.authorUid ||
            context.contentVersion !== placement.contentVersion) continue;
        const lesson = snapshotData(await read(db.doc(`lessons/${placement.lessonId}`)));
        if (!plain(lesson)) {
          if (lesson === null) continue;
          fail("UNIVERSE_INVALID");
        }
        if (lesson.archived === true || lesson.version !== context.contentVersion ||
            lesson.themeId !== context.themeId) continue;
        const ratingSnapshot = await read(db.doc(`lesson_ratings/${placement.lessonId}`));
        const rating = ratingSnapshot.exists ? ratingSnapshot.data() : {ratingSum: 0, ratingCount: 0, ratingVersion: 0};
        if (!plain(rating)) fail("UNIVERSE_INVALID");
        eligible.push({lessonId: placement.lessonId, authorUid: context.authorUid,
          contentVersion: context.contentVersion, ratingSum: exact(rating.ratingSum),
          ratingCount: exact(rating.ratingCount), ratingVersion: exact(rating.ratingVersion),
          title: typeof lesson.title === "string" && lesson.title.trim() ? lesson.title : null,
          placement: {previousShelf: placement.previousShelf, version: placement.placementVersion,
            updatedAtMs: placement.updatedAtMs}});
      }

      let priced;
      try {
        priced = assignLessonPrices(eligible);
      } catch (_) {
        fail("UNIVERSE_INVALID");
      }
      const eligibleById = new Map(eligible.map((entry) => [entry.lessonId, entry]));
      const rows = priced.map((price) => {
        const rating = eligibleById.get(price.lessonId);
        return Object.freeze({lessonId: price.lessonId, authorUid: rating.authorUid,
          contentVersion: rating.contentVersion, title: rating.title,
          placement: Object.freeze({...rating.placement}), rating: Object.freeze({sum: price.ratingSum,
            count: price.ratingCount, version: rating.ratingVersion,
            weightedScore: Object.freeze({...price.weightedScore})}),
          priceNolics: price.priceNolics});
      });
      const byLessonId = new Map(rows.map((row) => [row.lessonId, row]));
      return Object.freeze({rows: Object.freeze(rows),
        find: (lessonId) => typeof lessonId === "string" ? byLessonId.get(lessonId) || null : null,
        readCount: consumed});
    }

    return Object.freeze({
      // Trusted consumers may share this meter for necessary private server reads, but these raw
      // snapshots are never placed in the safe pricing-universe projection or callable response.
      get: (ref) => read(ref),
      getAll: (...refs) => Promise.all(refs.map((ref) => read(ref))),
      requireRemaining,
      read: () => {
        if (universe === null) universe = build();
        return universe;
      },
    });
  }

  return Object.freeze({forTransaction});
}

module.exports = {createArenaEligibleUniverse, LIMITS};
