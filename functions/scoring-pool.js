"use strict";

const {UNSCORABLE} = require("./attempt-scoring");

/**
 * Builds the pool one submitted attempt is scored against.
 *
 * `scoreAttempt` wants a flat list of questions whose *length* is the length of the codeAnswer and
 * whose entries are keyed by `id`. What the server has is a lesson's question documents out of
 * Firestore — a different shape, a different membership, and no positions at all. Nothing turned
 * one into the other, so the handler that calls the scorer would have written that translation
 * inline, in a file no test reaches. `serverScoringFor` in `index.js` calls this instead.
 *
 * ---
 *
 * **`served` is the authority on position, and nothing else is allowed to be.**
 *
 * The device built the list it played from: it dropped archived questions, deduped translated
 * variants, filtered by difficulty, sorted by `(order, sourceId)` and then numbered the result
 * (`StartLessonAttemptUseCase.kt:93-95`). Every input to that decision can have moved since — a
 * question archived, retitled, republished at a new `order`, a translation added. Re-running the
 * sort here against today's documents would produce a list that looks right and is off by one, and
 * an off-by-one pool does not fail: it scores question B's answer against question A and returns a
 * number. So the sort is not re-run. `served` already recorded the decision, position by position,
 * and this module only fills those positions in.
 *
 * That is why there is no difficulty filter here, no `archived` check and no canonicalisation of
 * `q1__uk` down to `q1`. Each of the three is a client decision `served` already carries, and each
 * would silently take a question away from a player who was shown it. A question that was served is
 * scored whatever its document now says.
 *
 * ---
 *
 * **The pool is as long as the last served position, and no longer.**
 *
 * `readServed` bounds every position by `questions.length` and `digits` is that long, so the pool
 * must hold at least `maxServedPosition + 1` entries or a genuinely served position is refused as
 * `SERVED_MALFORMED` — filed against the client for a gap that is ours. Positions the player never
 * reached are `'0'` whatever sits at them, so those entries need no question at all and get an
 * unservable filler instead. A document the attempt never dealt is therefore absent from the pool
 * entirely; its position, if it has one below the last served, reads `'0'` either way.
 *
 * A consequence worth stating: the codeAnswer that comes back is as long as the last served
 * position, which for a sparse attempt is shorter than the string the device itself built over the
 * whole eligible pool. The percent is unaffected — `computePercentScore` drops `'0'` positions from
 * the average, and the positions the two strings differ over are exactly the unserved ones.
 *
 * ---
 *
 * **A served question with no document is reported, never dropped.**
 *
 * Leaving its position out and shrinking the pool would slide every later question one place left.
 * Leaving the position in but taking the question out of `served` would make its digit `'0'` —
 * "the player was never shown this" — which is a better story for us than for them. So the position
 * stays, the id is reported, and `scoreAttempt` reaches its own `QUESTION_MISSING` for it: a scored
 * position — shown, no valid answer — named by id and position. Every other served question is
 * still in the pool at its own position, so nothing about the rest of the attempt depends on the one
 * we could not find.
 *
 * The `missing` list this returns is where a genuine loss is actually visible. The scorer is handed
 * `served` and a pool and cannot tell a document that went away from an id the device made up; this
 * module is handed the lesson's documents and can. It reports; it does not judge — a refusal here
 * would take the whole attempt down for one absent document, which is the outcome an invented entry
 * was buying.
 *
 * ---
 *
 * **Refused rather than resolved.** Two documents sharing an id, or documents from more than one
 * lesson, both make "which question sits here" unanswerable — and `readPool` would refuse the
 * result anyway, as a bare `POOL_MALFORMED` naming nothing. Refused here, by name, with the
 * offending id or lesson quoted, so the person reading it can go and look.
 *
 * Pure. No `firebase-admin`, no Firestore read, no handler: the documents arrive as an argument.
 */

/**
 * Why a pool could not be built.
 *
 * The two that describe an input `scoreAttempt` also judges take its wire strings rather than
 * spelling them out again — a caller switching on `reason` must not have to know which of the two
 * modules refused. The rest are this module's own, in the same vocabulary as
 * `question-key-store.js`'s `REASON`: named constants because they are read by whoever is looking
 * at an attempt that did not score, and a typo'd literal matches nothing and reports "no problems".
 */
const REASON = {
  /** No `served` list, so there is nothing to say which question sat where. */
  SERVED_UNKNOWN: UNSCORABLE.SERVED_UNKNOWN,
  /**
   * `served` is not the shape `attempt-intake.js` validates: an entry that is not an object, a
   * position that is not an integer inside the accepted range, a blank id, a repeated position or
   * a repeated id. `detail` is the offending value.
   *
   * Re-checked rather than trusted even though intake checks it first, because the position is what
   * sizes the array below: an unbounded one turns a crafted body into an allocation, and a repeated
   * one silently loses a question. This is the whole of "re-checks only what it needs".
   */
  SERVED_MALFORMED: UNSCORABLE.SERVED_MALFORMED,
  /** The attempt named no lesson, so no document can be confirmed to belong to it. */
  UNUSABLE_LESSON_ID: "unusable-lesson-id",
  /** A document whose `lessonId` is not the attempt's. `detail` is the lesson it claims. */
  WRONG_LESSON: "wrong-lesson",
  /** Two documents claiming one id. `detail` is the id. */
  DUPLICATE_DOCUMENT: "duplicate-document",
  /**
   * An entry that is not a question document — not an object, or carrying no id. `detail` is its
   * position in the list handed over.
   *
   * Refused rather than passed over, because the alternative is worse than it looks: a document
   * that lost its `id` on the way in is a document that is *present*, and skipping it would report
   * the question it holds as one we served and lost. A caller whose mapping drops a field for one
   * document is a caller whose mapping cannot be trusted for the others either.
   */
  MALFORMED_DOCUMENT: "malformed-document",
};

/**
 * The last position this module will build a pool up to.
 *
 * Kept equal to `attempt-intake.js`'s `MAX_SERVED_POSITION` — and pinned to it by
 * `scoring-pool.test.js` — rather than imported, because that module pulls in `firebase-functions`
 * for its `HttpsError` and this one is meant to load with nothing but `node`.
 */
const MAX_SERVED_POSITION = 999;

/**
 * What a filler entry's id starts with.
 *
 * A Firestore document id cannot contain `/`, so no real question can ever be filed under one of
 * these — which matters, because a filler that collided with a served id would hand the scorer a
 * `payload: null` for a question the player actually answered and turn a crafted `served` entry
 * into a server fault, and a server fault into an uncharged attempt. The prefix makes the collision
 * impossible for any id that came out of the `questions` collection; the loop in `fillerId` closes
 * the rest of the gap for an id that was merely *claimed* by a crafted `served` list.
 */
const UNSERVED_ID_PREFIX = "/unserved/";

/** Values are quoted back in a refusal; long ones are cut rather than carried whole. */
const MAX_DETAIL_CHARS = 200;

/**
 * A value as a refusal can carry it.
 *
 * Numbers are kept, unlike `question-key-store.js`'s `detailOf`: the details here include a
 * position and a list index, and dropping those would leave a refusal saying an entry was wrong
 * without saying which one.
 */
function detailOf(value) {
  if (value === null || value === undefined) return null;
  const text = String(value);
  return text.length <= MAX_DETAIL_CHARS ? text : `${text.slice(0, MAX_DETAIL_CHARS)}…`;
}

/** A pool that could not be built. Same keys as a built one, so a caller reads one shape. */
function refused(reason, detail) {
  return {
    built: false,
    questions: null,
    missing: [],
    versionDrift: null,
    reason,
    detail: detailOf(detail),
  };
}

/**
 * An id for a position no question was served at, distinct from everything already in the pool.
 *
 * The prefix rules out every id Firestore could have produced; the suffix loop rules out the one
 * remaining case, a `served` entry that named `/unserved/3` itself. `readPool` refuses a duplicate
 * id outright, so "distinct" here is not a nicety — a collision would refuse the whole attempt.
 */
function fillerId(at, taken) {
  let id = `${UNSERVED_ID_PREFIX}${at}`;
  while (taken.has(id)) id += "_";
  taken.add(id);
  return id;
}

/**
 * Builds the pool for one attempt.
 *
 * @param input `{lessonId, lessonVersion, served, documents}`.
 *
 *   - `lessonId` — the attempt's lesson, from `attempt-intake.js`. Every document must belong to it.
 *   - `lessonVersion` — the attempt's, for the drift record only. Never a reason to refuse: the
 *     served ids are what places a digit, and a question republished since is still the question
 *     that was put to the player.
 *   - `served` — `[{codeAnswerIndex, questionId}]` as `validateServed` leaves it. An empty list is
 *     legal and yields an empty pool, which `scoreAttempt` scores as its own "nothing was shown".
 *   - `documents` — the lesson's question documents, as
 *     `db.collection("questions").where("lessonId", "==", lessonId)` returns them, mapped to plain
 *     objects. `id`, `lessonId`, `payload` and `version` are the only fields read. `archived` is
 *     deliberately not one of them, and neither is anything inside `payload`.
 *
 * @returns `{built, questions, missing, versionDrift, reason, detail}`.
 *   - `questions` — feed straight to `scoreAttempt` as its `questions`. `null` when refused.
 *   - `missing` — `[{questionId, codeAnswerIndex}]`, one per served question whose document is
 *     gone, in position order. Bounded by `served`, which intake bounds; no budget of its own.
 *   - `versionDrift` — `{attempt, documents}` when a document that went into the pool carries a
 *     `version` other than the attempt's `lessonVersion`, else `null`. A record for a person
 *     reading a surprising score, never an input to one: in this repo `version` is assigned per
 *     document path (`entity-version.js`), so a lesson's counter and a question's counter are
 *     different sequences and a difference between them is expected rather than alarming. What it
 *     is good for is saying *which* generation of the documents an attempt was scored against.
 *   - `reason`/`detail` — why it was refused, or `null` when it was not.
 */
function buildScoringPool(input) {
  const lessonId = input && typeof input.lessonId === "string" ? input.lessonId : "";
  if (lessonId === "") return refused(REASON.UNUSABLE_LESSON_ID, input && input.lessonId);

  const served = input && input.served;
  if (!Array.isArray(served)) return refused(REASON.SERVED_UNKNOWN, null);
  const documents = input && Array.isArray(input.documents) ? input.documents : [];

  // The documents first, so a pool that is not a pool is refused before a single position is
  // decided. A caller handed a broken fetch gets one reason naming one document, not twenty
  // "question missing" records pointing at questions that are sitting right there.
  const byId = new Map();
  for (let at = 0; at < documents.length; at += 1) {
    const document = documents[at];
    if (!document || typeof document !== "object" || Array.isArray(document)) {
      return refused(REASON.MALFORMED_DOCUMENT, at);
    }
    const id = typeof document.id === "string" ? document.id : "";
    if (id === "") return refused(REASON.MALFORMED_DOCUMENT, at);
    if (byId.has(id)) return refused(REASON.DUPLICATE_DOCUMENT, id);
    // An absent or blank `lessonId` is refused with the rest: a document that cannot be placed in
    // this lesson must not supply the payload a served position is scored against, and every
    // document `questionToDocument` writes carries one.
    if (document.lessonId !== lessonId) return refused(REASON.WRONG_LESSON, document.lessonId);
    byId.set(id, document);
  }

  // Then `served`, which decides both the positions and the length.
  const positions = new Map();
  const claimed = new Set();
  let size = 0;
  for (const entry of served) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      return refused(REASON.SERVED_MALFORMED, null);
    }
    const at = entry.codeAnswerIndex;
    const questionId = entry.questionId;
    if (!Number.isInteger(at) || at < 0 || at > MAX_SERVED_POSITION) {
      return refused(REASON.SERVED_MALFORMED, at);
    }
    if (typeof questionId !== "string" || questionId === "") {
      return refused(REASON.SERVED_MALFORMED, questionId);
    }
    if (positions.has(at)) return refused(REASON.SERVED_MALFORMED, at);
    if (claimed.has(questionId)) return refused(REASON.SERVED_MALFORMED, questionId);
    positions.set(at, questionId);
    claimed.add(questionId);
    if (at + 1 > size) size = at + 1;
  }

  // Every served id is reserved before the first filler is named, including the ids of questions
  // whose documents are gone: a filler taking one of those would put a `payload: null` under an id
  // `served` still names, and the scorer would call our lost question a malformed one.
  const taken = new Set(claimed);
  const questions = new Array(size);
  const missing = [];
  const versions = new Set();

  for (let at = 0; at < size; at += 1) {
    const questionId = positions.get(at);
    const document = questionId === undefined ? undefined : byId.get(questionId);
    if (document === undefined) {
      if (questionId !== undefined) missing.push({questionId, codeAnswerIndex: at});
      questions[at] = {id: fillerId(at, taken), lessonId, payload: null};
      continue;
    }
    // Reduced to the three fields the scorer reads, rather than passed through whole. A document
    // carries `order` and `archived` and a `payload` with a difficulty inside it, and every one of
    // those is a client decision `served` already made — narrowing here is what stops a later
    // reader of this pool from being tempted by them.
    questions[at] = {
      id: document.id,
      lessonId: document.lessonId,
      payload: document.payload === undefined ? null : document.payload,
    };
    if (Number.isFinite(document.version)) versions.add(document.version);
  }

  const attemptVersion = input && Number.isFinite(input.lessonVersion) ? input.lessonVersion : null;
  const drifted = attemptVersion === null
    ? []
    : [...versions].filter((version) => version !== attemptVersion).sort((left, right) => left - right);

  return {
    built: true,
    questions,
    missing,
    versionDrift: drifted.length === 0 ? null : {attempt: attemptVersion, documents: drifted},
    reason: null,
    detail: null,
  };
}

module.exports = {
  REASON,
  MAX_SERVED_POSITION,
  UNSERVED_ID_PREFIX,
  buildScoringPool,
};
