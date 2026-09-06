"use strict";

/**
 * Lesson-level Arena policy. No persistence, wall clock, network or random source.
 * All inputs/outputs are JSON values. BigInt is used only for exact intermediate arithmetic.
 *
 * The caller supplies authenticated identities and trusted settled-completion receipts, never
 * client assertions. The caller must commit ticket consumption, royalties, balances and award
 * keys atomically. A plan returned here is not an authorization or a database lock.
 */
const POLICY_VERSION = "lesson-arena-v1";
const MIN_SEASON_RATERS = 20;
const PRIOR_SUM = 40;
const PRIOR_COUNT = 20;
const MAX_SAFE = BigInt(Number.MAX_SAFE_INTEGER);
const TERMINAL_COMPLETION_STATES = new Set(["SETTLED", "PENDING", "ABORTED", "REJECTED"]);

function object(value, name) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${name} must be an object`);
  }
  return value;
}

function list(value, name) {
  if (!Array.isArray(value)) throw new TypeError(`${name} must be an array`);
  return value;
}

function id(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new TypeError(`${name} must be a nonblank string`);
  return value;
}

function integer(value, name, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) {
    throw new RangeError(`${name} must be a safe integer >= ${minimum}`);
  }
  return value;
}

function safeNumber(value, name) {
  if (value < 0n || value > MAX_SAFE) throw new RangeError(`${name} exceeds safe integer range`);
  return Number(value);
}

function compareIds(left, right) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function uniqueRows(rows, key, name) {
  const result = new Map();
  for (const row of rows) {
    const previous = result.get(row[key]);
    if (previous && JSON.stringify(previous) !== JSON.stringify(row)) {
      throw new RangeError(`${name}: conflicting duplicate ${row[key]}`);
    }
    result.set(row[key], row);
  }
  return result;
}

function normalizeCompletions(rows) {
  return uniqueRows(list(rows, "completions").map((raw) => {
    const row = object(raw, "completion");
    if (!TERMINAL_COMPLETION_STATES.has(row.status)) throw new RangeError("completion.status is unsupported");
    if (typeof row.completed !== "boolean") throw new TypeError("completion.completed must be boolean");
    return {
      attemptId: id(row.attemptId, "completion.attemptId"),
      uid: id(row.uid, "completion.uid"),
      lessonId: id(row.lessonId, "completion.lessonId"),
      status: row.status,
      completed: row.completed,
      settledAtMs: row.status === "SETTLED" ? integer(row.settledAtMs, "completion.settledAtMs") : null,
    };
  }), "attemptId", "completions");
}

function normalizeRatings(rows) {
  return [...uniqueRows(list(rows, "ratings").map((raw) => {
    const row = object(raw, "rating");
    const rating = integer(row.rating, "rating.rating", 1);
    if (rating > 3) throw new RangeError("rating.rating must be in 1..3");
    return {
      ratingId: id(row.ratingId, "rating.ratingId"),
      uid: id(row.uid, "rating.uid"),
      lessonId: id(row.lessonId, "rating.lessonId"),
      attemptId: id(row.attemptId, "rating.attemptId"),
      rating,
      acceptedAtMs: integer(row.acceptedAtMs, "rating.acceptedAtMs"),
      acceptedSequence: integer(row.acceptedSequence === undefined ? 0 : row.acceptedSequence, "rating.acceptedSequence"),
    };
  }), "ratingId", "ratings").values()];
}

/** Validates sufficient statistics and returns the exact, deliberately unrounded fraction. */
function weightedScore({ratingSum, ratingCount}) {
  integer(ratingSum, "ratingSum");
  integer(ratingCount, "ratingCount");
  const sum = BigInt(ratingSum);
  const count = BigInt(ratingCount);
  if (sum < count || sum > 3n * count) throw new RangeError("ratingSum is inconsistent with ratings in 1..3");
  return {
    numerator: safeNumber(sum + BigInt(PRIOR_SUM), "weighted numerator"),
    denominator: safeNumber(count + BigInt(PRIOR_COUNT), "weighted denominator"),
  };
}

/** Ascending comparison. Cross-products are BigInt, so equal rounded floats never merge levels. */
function compareScores(left, right) {
  object(left, "left score");
  object(right, "right score");
  const a = BigInt(integer(left.numerator, "left numerator")) *
    BigInt(integer(right.denominator, "right denominator", 1));
  const b = BigInt(integer(right.numerator, "right numerator")) *
    BigInt(integer(left.denominator, "left denominator", 1));
  return a < b ? -1 : a > b ? 1 : 0;
}

function normalizeWindow(window) {
  if (window == null) return null;
  object(window, "window");
  const startMs = integer(window.startMs, "window.startMs");
  const endMs = integer(window.endMs, "window.endMs");
  if (endMs <= startMs) throw new RangeError("window.endMs must follow startMs");
  return {startMs, endMs};
}

function aggregateNormalized({lessonId, authorUid, ratings, completions, window}) {
  const byUid = new Map();
  for (const rating of ratings) {
    if (rating.lessonId !== lessonId || rating.uid === authorUid) continue;
    if (window && (rating.acceptedAtMs < window.startMs || rating.acceptedAtMs >= window.endMs)) continue;
    const receipt = completions.get(rating.attemptId);
    if (!receipt || receipt.status !== "SETTLED" || !receipt.completed ||
        receipt.uid !== rating.uid || receipt.lessonId !== lessonId ||
        receipt.settledAtMs > rating.acceptedAtMs) continue;
    const previous = byUid.get(rating.uid);
    // The acceptance timestamp, not a device timestamp, defines latest. Equal timestamps have
    // a stable lexical ratingId tie-break, independent of query/input order.
    if (!previous || rating.acceptedAtMs > previous.acceptedAtMs ||
        (rating.acceptedAtMs === previous.acceptedAtMs && (rating.acceptedSequence > previous.acceptedSequence ||
          (rating.acceptedSequence === previous.acceptedSequence && compareIds(rating.ratingId, previous.ratingId) < 0)))) {
      byUid.set(rating.uid, rating);
    }
  }
  const accepted = [...byUid.values()].sort((a, b) => compareIds(a.uid, b.uid));
  const ratingSum = safeNumber(accepted.reduce((sum, row) => sum + BigInt(row.rating), 0n), "ratingSum");
  const ratingCount = accepted.length;
  return {
    lessonId,
    ratingSum,
    ratingCount,
    weightedScore: weightedScore({ratingSum, ratingCount}),
    acceptedRatingIds: accepted.map((row) => row.ratingId),
  };
}

/** One latest eligible vote per (uid, lesson), after that vote's trusted completed receipt. */
function aggregateLessonRatings({lessonId, authorUid, ratings, completions, window = null}) {
  return aggregateNormalized({
    lessonId: id(lessonId, "lessonId"),
    authorUid: id(authorUid, "authorUid"),
    ratings: normalizeRatings(ratings),
    completions: normalizeCompletions(completions),
    window: normalizeWindow(window),
  });
}

/**
 * Price uses the ORDINAL INDEX of distinct exact rated-score levels, NOT numerical distance:
 * 1 + round(99 * levelIndex / (distinctLevels - 1)). Index 0 is the lowest rated level.
 * Equal fractions share a level. Unrated lessons and a sole rated level cost 1 nolic.
 * Returns input lesson order, with priceNolics and a nullable ratedScoreLevelIndex appended.
 */
function assignLessonPrices(aggregates) {
  const rows = list(aggregates, "aggregates").map((raw) => {
    const row = object(raw, "aggregate");
    return {
      lessonId: id(row.lessonId, "aggregate.lessonId"),
      ratingSum: integer(row.ratingSum, "aggregate.ratingSum"),
      ratingCount: integer(row.ratingCount, "aggregate.ratingCount"),
      weightedScore: weightedScore(row),
    };
  });
  if (new Set(rows.map((row) => row.lessonId)).size !== rows.length) throw new RangeError("duplicate lessonId");
  const rated = rows.filter((row) => row.ratingCount > 0)
    .sort((a, b) => compareScores(a.weightedScore, b.weightedScore) || compareIds(a.lessonId, b.lessonId));
  const levels = [];
  const levelByLesson = new Map();
  for (const row of rated) {
    if (!levels.length || compareScores(levels[levels.length - 1], row.weightedScore) !== 0) {
      levels.push(row.weightedScore);
    }
    levelByLesson.set(row.lessonId, levels.length - 1);
  }
  return rows.map((row) => {
    const level = levelByLesson.get(row.lessonId);
    const denominator = BigInt(Math.max(1, levels.length - 1));
    const roundedOffset = level == null || levels.length <= 1 ? 0n :
      (198n * BigInt(level) + denominator) / (2n * denominator);
    return {...row, priceNolics: 1 + Number(roundedOffset), ratedScoreLevelIndex: level == null ? null : level};
  });
}

/** Calendar quarter containing atMs, [startMs, endMs), always UTC. */
function utcQuarterWindow(atMs) {
  integer(atMs, "atMs");
  const date = new Date(atMs);
  if (!Number.isFinite(date.getTime())) throw new RangeError("atMs is outside the Date range");
  const year = date.getUTCFullYear();
  const quarter = Math.floor(date.getUTCMonth() / 3) + 1;
  const startMs = Date.UTC(year, (quarter - 1) * 3, 1);
  const endMs = Date.UTC(year, quarter * 3, 1);
  integer(startMs, "quarter.startMs");
  integer(endMs, "quarter.endMs");
  return {id: `${year}-Q${quarter}`, year, quarter, startMs, endMs};
}

/**
 * lessons is the server's frozen eligible Arena lesson membership, not today's shelf query.
 * It supplies {lessonId, authorUid, questType, publishedAtMs}. COURSE/SURVEY never compete here.
 * Votes are selected WITHIN the quarter before latest-per-user deduplication. A later-quarter
 * vote cannot erase a previous-quarter vote. Caller finalizes only after the quarter closes.
 */
function rankSeasonLessons({lessons, ratings, completions, quarterAtMs}) {
  const quarter = utcQuarterWindow(quarterAtMs);
  const normalizedRatings = normalizeRatings(ratings);
  const normalizedCompletions = normalizeCompletions(completions);
  const seen = new Set();
  const ranked = [];
  for (const raw of list(lessons, "lessons")) {
    const lesson = object(raw, "lesson");
    const lessonId = id(lesson.lessonId, "lesson.lessonId");
    if (seen.has(lessonId)) throw new RangeError("duplicate season lessonId");
    seen.add(lessonId);
    id(lesson.questType, "lesson.questType");
    const authorUid = id(lesson.authorUid, "lesson.authorUid");
    const publishedAtMs = integer(lesson.publishedAtMs, "lesson.publishedAtMs");
    if (lesson.questType !== "REGULAR" || publishedAtMs >= quarter.endMs) continue;
    const aggregate = aggregateNormalized({
      lessonId,
      authorUid,
      ratings: normalizedRatings,
      completions: normalizedCompletions,
      window: {startMs: Math.max(quarter.startMs, publishedAtMs), endMs: quarter.endMs},
    });
    if (aggregate.ratingCount < MIN_SEASON_RATERS) continue;
    ranked.push({...aggregate, authorUid, publishedAtMs});
  }
  ranked.sort((a, b) => compareScores(b.weightedScore, a.weightedScore) ||
    (a.ratingCount > b.ratingCount ? -1 : a.ratingCount < b.ratingCount ? 1 : 0) ||
    (a.publishedAtMs < b.publishedAtMs ? -1 : a.publishedAtMs > b.publishedAtMs ? 1 : 0) ||
    compareIds(a.lessonId, b.lessonId));
  return {policyVersion: POLICY_VERSION, quarter, ranked, winner: ranked[0] || null};
}

/**
 * Exact 80/20 split, accumulated per author in fifths. Whole nolics not yet distributable stay
 * reserved, never disappear into an early rounded sink. Conservation for every call:
 * payment + previous.reservedNolics = authorCreditNolics + sinkNolics + next.reservedNolics.
 * Persist next state and both ledger movements in the SAME idempotent payment transaction.
 */
function splitAuthorRoyalty(paymentNolics, previous = {
  authorCarryFifths: 0, sinkCarryFifths: 0, reservedNolics: 0,
}) {
  integer(paymentNolics, "paymentNolics");
  object(previous, "royalty state");
  const a = integer(previous.authorCarryFifths, "authorCarryFifths");
  const s = integer(previous.sinkCarryFifths, "sinkCarryFifths");
  const reserve = integer(previous.reservedNolics, "reservedNolics");
  if (a > 4 || s > 4 || reserve > 1 || a + s !== 5 * reserve) {
    throw new RangeError("inconsistent royalty carry/reserve state");
  }
  const payment = BigInt(paymentNolics);
  const author = payment * 4n + BigInt(a);
  const sink = payment + BigInt(s);
  const nextAuthor = Number(author % 5n);
  const nextSink = Number(sink % 5n);
  const next = {
    authorCarryFifths: nextAuthor,
    sinkCarryFifths: nextSink,
    reservedNolics: (nextAuthor + nextSink) / 5,
  };
  return {
    authorCreditNolics: safeNumber(author / 5n, "author credit"),
    sinkNolics: safeNumber(sink / 5n, "sink"),
    reserveDeltaNolics: next.reservedNolics - reserve,
    next,
  };
}

function awardKey(kind, ...parts) {
  // JSON tuple encoding is collision-free even when user/content IDs contain separators.
  return JSON.stringify(["arena", kind, ...parts]);
}

function knownAwards(keys) {
  return new Set(list(keys, "awardedKeys").map((key) => id(key, "award key")));
}

/** Explicit curator permission is mandatory; season wins NEVER advance a lesson automatically. */
function planLessonAdvancement({lesson, targetShelf, curatorAuthorized, awardedKeys = []}) {
  object(lesson, "lesson");
  const lessonId = id(lesson.lessonId, "lesson.lessonId");
  const authorUid = id(lesson.authorUid, "lesson.authorUid");
  if (curatorAuthorized !== true) throw new RangeError("curator authorization required");
  if (lesson.questType !== "REGULAR") throw new RangeError("only REGULAR lessons advance on the Arena ladder");
  const transitions = {arena: {target: "tournament", boxes: 15}, tournament: {target: "home", boxes: 20}};
  const transition = Object.hasOwn(transitions, lesson.shelf) ? transitions[lesson.shelf] : null;
  if (!transition || targetShelf !== transition.target) throw new RangeError("only the next ladder step is allowed");
  const key = awardKey("milestone", lessonId, targetShelf);
  const awarded = knownAwards(awardedKeys);
  return {
    lessonId,
    fromShelf: lesson.shelf,
    targetShelf,
    reward: awarded.has(key) ? null : {
      key, kind: "MILESTONE", recipientUid: authorUid, currency: "BOXES", amount: transition.boxes,
    },
  };
}

/** Pass the finalized output of rankSeasonLessons. Award key is per season, not per winner. */
function planSeasonAward({season, awardedKeys = []}) {
  object(season, "season");
  const quarter = object(season.quarter, "season.quarter");
  const verifiedQuarter = utcQuarterWindow(integer(quarter.startMs, "quarter.startMs"));
  if (quarter.id !== verifiedQuarter.id || quarter.endMs !== verifiedQuarter.endMs) {
    throw new RangeError("invalid quarter identity");
  }
  const awarded = knownAwards(awardedKeys);
  if (season.winner == null) return null;
  const winner = object(season.winner, "season.winner");
  if (integer(winner.ratingCount, "winner.ratingCount") < MIN_SEASON_RATERS) {
    throw new RangeError("season winner needs at least 20 distinct eligible raters");
  }
  const key = awardKey("season", quarter.id);
  if (awarded.has(key)) return null;
  return {
    key,
    kind: "SEASON_WIN",
    lessonId: id(winner.lessonId, "winner.lessonId"),
    recipientUid: id(winner.authorUid, "winner.authorUid"),
    currency: "BOXES",
    amount: 10,
  };
}

module.exports = {
  POLICY_VERSION,
  MIN_SEASON_RATERS,
  weightedScore,
  compareScores,
  aggregateLessonRatings,
  assignLessonPrices,
  utcQuarterWindow,
  rankSeasonLessons,
  splitAuthorRoyalty,
  planLessonAdvancement,
  planSeasonAward,
};
