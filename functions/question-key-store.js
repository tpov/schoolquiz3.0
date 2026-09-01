"use strict";

const {redact, STATUS} = require("./question-redaction");

/**
 * Turns a lesson's questions into the one document that holds their answers.
 *
 * `question-redaction.js` decides what a question's answer *is*. This module decides where that
 * answer goes: it gathers the keys for a whole lesson into a single server-only document, and it
 * records every question it could not take an answer from, by id and by reason.
 *
 * Pure, and free of `firebase-admin` for the same reason `lesson-reward.js` is — `index.js` has no
 * test of its own, so anything worth asserting has to live outside it. Nothing here throws: this
 * runs while a publish batch is being assembled, and one unreadable question must not be able to
 * fail the publication of every other question beside it. That is also why every guard below ends
 * in a recorded refusal rather than an exception.
 *
 * Five decisions carry this file:
 *
 * 1. **One document per lesson, not one per question.** A published question already costs four
 *    writes (its public document, two sync-change stubs and the admin question document), and the
 *    publish batch is a single `db.batch()` with no chunking, so Firestore's 500-write cap already
 *    bounds a submission at roughly 125 questions. A key document per question would make it five
 *    and drop that ceiling to about a hundred — breaking submissions that publish today. Per lesson
 *    the cost per question does not move at all, and it matches how the server already reads
 *    questions back (`where("lessonId", "==", lessonId)`).
 *
 *    The trade is the other Firestore limit: a document may not exceed 1 MiB, and per lesson the
 *    key list is the thing that grows. `MAX_DOCUMENT_BYTES` below is that ceiling made explicit —
 *    a lesson large enough to reach it refuses its remaining questions by name instead of building
 *    a document that fails the whole batch on commit.
 *
 * 2. **Lists, not maps keyed by question id.** The publish batch writes every public document with
 *    `{merge: true}`, and merge recurses into maps: a second publication of the same lesson would
 *    union its keys with the previous generation's, leaving the answer to a question that has since
 *    been rewritten sitting beside the answer to the one that replaced it. Firestore replaces an
 *    array field whole, so a list is what makes a republish a replacement. It also keeps question
 *    ids out of field-name position, where a perfectly legal document id containing a `.` would be
 *    an illegal field name.
 *
 * 3. **A refusal is recorded, never dropped.** `redact` refusing means the question's answer is
 *    still inside the payload the world can read. There is no logger in `index.js` and no
 *    `console.*` call anywhere in it, so the document is one half of making a refusal visible and
 *    `questionKeyDocuments` returning the same records for the caller to log is the other — the
 *    collection is denied to every client and has no admin surface, so a record nobody reads is not
 *    a record. A question that produced no key and no refusal line is a question that genuinely has
 *    no answer — a Survey — and nothing else.
 *
 * 4. **A key Firestore cannot store is refused, not written.** A `FillBlank` key maps blank id to
 *    candidate id, and blank ids come from the author, so they can carry a `.`, a `/`, or arrive
 *    wrapped in `__` — all illegal as Firestore field names. The lesson id is the document id and
 *    is no safer: it reaches `db.doc()` unvalidated, where `..`, a `/`, or 1,500 bytes of it throws
 *    and takes the whole batch down. Both are refused and recorded, which is the failure mode this
 *    module exists to have instead.
 *
 * 5. **The document says which generation it belongs to.** See `publicHalfRedacted`.
 *
 * ---
 *
 * **Invariant for whoever turns redaction on: both halves must come from ONE `redact` call.**
 *
 * `redact` draws a fresh shuffle every time it is invoked. The `idMap` and `order` inside an
 * `Ordering` or `FillBlank` key describe *that* permutation and no other, so calling `redact` a
 * second time to produce the public half yields a different arrangement, and `restoreContent`
 * returns null for every one of those questions — silently, at scoring time, long after publish.
 * Split once, write both halves from the single result, or do not split at all.
 *
 * This slice writes only the key, which is exactly the case that invariant warns about, so every
 * document it produces says so in `publicHalfRedacted`.
 */

/** Server-only collection. `firestore.rules` denies it read and write to every client. */
const KEY_COLLECTION = "question_keys";

/**
 * The document's own shape version, distinct from the `version` stamped inside each key.
 *
 * The two move independently: a key gains a field without this document changing at all, and this
 * document could grow a fourth list beside `keys` and `refusals` without any key changing.
 */
const DOCUMENT_VERSION = 1;

/**
 * Whether the payload published beside these keys was itself redacted.
 *
 * `false` for every document this slice writes, and it is not decoration. The keys here were
 * harvested from payloads that went out whole, so the shuffle each `Ordering` and `FillBlank` key
 * records is one nobody has been shown: `idMap` maps `ri-0…` onto original ids for an arrangement
 * that exists only inside this document. A reader that treats such a key as describing the
 * published question will translate answers against the wrong permutation.
 *
 * So this field is what lets a later reader tell the two generations apart — and what makes it
 * possible to find and rewrite this one. A key that cannot say which generation it belongs to is
 * worse than no key at all, because no key fails loudly and a mismatched one scores wrong answers
 * correct. The slice that starts publishing redacted payloads writes both halves from a single
 * `redact` call and sets this true.
 */
const PUBLIC_HALF_REDACTED = false;

/**
 * How much of the 1 MiB document limit the key list may use.
 *
 * Measured as the UTF-8 length of each entry's JSON, which is a proxy rather than Firestore's own
 * accounting — Firestore adds field-name bytes and per-value overhead this does not model. Hence
 * the headroom: roughly 15% of the limit is left unclaimed so the estimate can be wrong by a wide
 * margin and the write still lands.
 */
const MAX_DOCUMENT_BYTES = 900000;

/** Firestore's own ceiling on a document id, in bytes. */
const MAX_DOCUMENT_ID_BYTES = 1500;

/** Author-supplied values are quoted back in refusals; long ones are cut rather than stored whole. */
const MAX_DETAIL_CHARS = 200;

/**
 * Reasons this module adds to the ones `question-redaction.js` already names.
 *
 * `REFUSAL` from that module covers everything about a payload; these cover everything about
 * *storing* what came back. Spelled out as constants for the same reason: they are read by whoever
 * is looking at a refused question, and a typo in one would be invisible.
 */
const REASON = {
  /** A key whose field names Firestore would reject. `detail` is the offending name. */
  UNSTORABLE_FIELD_NAME: "unstorable-field-name",
  /** A lesson id Firestore would reject as a document id. `detail` is the offending id. */
  UNUSABLE_LESSON_ID: "unusable-lesson-id",
  /** No document id to file the key under, so nothing could ever look it up. */
  MISSING_QUESTION_ID: "missing-question-id",
  /**
   * A second question in the same lesson claiming an id already taken. Ids come from a
   * client-submitted draft, so nothing upstream makes them unique; keeping both would leave the
   * answer that wins a lookup decided by array order.
   */
  DUPLICATE_QUESTION_ID: "duplicate-question-id",
  /**
   * The payload is already one of `question-redaction.js`'s own public shapes, so there is no
   * answer left in it to take. Recorded rather than passed over in silence: unlike a Survey, this
   * question does have an answer somewhere, and this document is not it.
   */
  ALREADY_REDACTED: "already-redacted",
  /** The lesson's keys reached `MAX_DOCUMENT_BYTES`; this question's answer was not stored. */
  DOCUMENT_FULL: "document-full",
  /** `redact` refused without naming why. Unreachable today, and better named than blank. */
  UNSPECIFIED: "unspecified-refusal",
};

function keyDocumentPath(lessonId) {
  return `${KEY_COLLECTION}/${lessonId}`;
}

/**
 * Whether Firestore will accept a string as a field name.
 *
 * Empty names are rejected outright, `/` and `.` are structural separators, and anything wrapped in
 * double underscores is reserved. `__proto__` is the case that actually turns up: `toPlainMap` in
 * `question-redaction.js` goes out of its way to store it as a real own property precisely so a
 * blank named that is not lost, which means it reaches here intact and has to be caught.
 */
function isStorableFieldName(name) {
  if (typeof name !== "string" || name === "") return false;
  if (name.includes("/") || name.includes(".")) return false;
  return !/^__.*__$/.test(name);
}

/**
 * Whether Firestore will accept a string as a document id.
 *
 * A different rule set from field names, and the difference matters in both directions: a `.` is
 * legal here and illegal there, while `.` and `..` alone are path navigation and are legal there.
 * Everything in this list makes `db.doc()` throw, which in a publish batch means no question in the
 * submission gets published — the failure this module is built to replace with a refusal.
 */
function isStorableDocumentId(id) {
  if (typeof id !== "string" || id === "") return false;
  if (id.includes("/")) return false;
  if (id === "." || id === "..") return false;
  if (/^__.*__$/.test(id)) return false;
  return Buffer.byteLength(id, "utf8") <= MAX_DOCUMENT_ID_BYTES;
}

/**
 * Walks a key looking for a field name Firestore would reject, and returns the first one found.
 *
 * Recursive rather than a check of the two maps that exist today (`idMap`, `blankToCandidate`),
 * because a key that grows a third one later must not quietly stop being checked. Returns `null`
 * when everything is storable.
 */
function unstorableFieldName(value) {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = unstorableFieldName(item);
      if (found !== null) return found;
    }
    return null;
  }
  if (value && typeof value === "object") {
    for (const name of Object.keys(value)) {
      if (!isStorableFieldName(name)) return name;
      const found = unstorableFieldName(value[name]);
      if (found !== null) return found;
    }
  }
  return null;
}

/** What one entry costs the document, near enough to budget by. See MAX_DOCUMENT_BYTES. */
function measure(value) {
  return Buffer.byteLength(JSON.stringify(value), "utf8");
}

/** Author-supplied text, cut to something a document and a log line can both carry. */
function detailOf(value) {
  if (typeof value !== "string") return null;
  return value.length <= MAX_DETAIL_CHARS ? value : `${value.slice(0, MAX_DETAIL_CHARS)}…`;
}

/**
 * Builds the key document for one lesson.
 *
 * @param lessonId the lesson the questions belong to; becomes the document id
 * @param questions the questions as `normalizeQuestion` leaves them — `id`, `payload` and
 *   `difficulty` are the fields read here. `difficulty` is only a fallback for a payload that
 *   carries none of its own, and it arrives as `""` for most published questions.
 * @param options `{random, indices}`. `random` is forwarded to `redact` so a test can name one
 *   concrete shuffle. `indices` lets a caller say where each question sat in a larger list, so a
 *   refusal points at the submission rather than at this slice of it; without it, positions in
 *   `questions` are used.
 * @returns `{id, lessonId, version, publicHalfRedacted, keys, refusals, omitted}`. `keys` pairs a
 *   question id with its key; `refusals` pairs a position and question id with why there is none;
 *   `omitted` counts records that did not fit. All three are always present: empty is what clears
 *   the previous generation on a republish.
 */
function lessonKeyDocument(lessonId, questions, options) {
  const list = Array.isArray(questions) ? questions : [];
  const random = options && typeof options.random === "function" ? options.random : undefined;
  const indices = options && Array.isArray(options.indices) ? options.indices : null;
  const positionOf = (at) => (indices && Number.isInteger(indices[at]) ? indices[at] : at);

  const keys = [];
  const refusals = [];
  const claimed = new Set();
  let used = 0;
  let omitted = 0;

  const refuse = (at, questionId, reason, detail) => {
    const record = {index: positionOf(at), questionId, reason, detail: detailOf(detail)};
    const size = measure(record);
    // Refusals are charged to the same budget as keys, so a lesson cannot escape the document
    // limit by refusing its way past it. What will not fit is counted instead of dropped.
    if (used + size > MAX_DOCUMENT_BYTES) {
      omitted += 1;
      return;
    }
    used += size;
    refusals.push(record);
  };

  for (let at = 0; at < list.length; at += 1) {
    const question = list[at];
    const questionId = question && typeof question.id === "string" ? question.id : "";
    const outcome = redact(
      question ? question.payload : null,
      question ? question.difficulty : null,
      {questionId, random},
    );

    // A Survey has no answer to keep, so it produces neither a key nor a refusal. It is the one
    // status that is genuinely nothing to record, and it stays that way even when the question
    // around it is malformed in some other respect.
    if (outcome.status === STATUS.NOT_APPLICABLE) continue;

    if (outcome.status === STATUS.ALREADY_REDACTED) {
      refuse(at, questionId, REASON.ALREADY_REDACTED, null);
      continue;
    }
    if (outcome.status !== STATUS.REDACTED || !outcome.key) {
      refuse(at, questionId, outcome.reason || REASON.UNSPECIFIED, null);
      continue;
    }
    // Checked after redaction rather than before it, so a Survey with a broken id stays the one
    // thing this document says nothing about.
    if (questionId === "") {
      refuse(at, questionId, REASON.MISSING_QUESTION_ID, null);
      continue;
    }
    if (claimed.has(questionId)) {
      refuse(at, questionId, REASON.DUPLICATE_QUESTION_ID, null);
      continue;
    }
    const unstorable = unstorableFieldName(outcome.key);
    if (unstorable !== null) {
      refuse(at, questionId, REASON.UNSTORABLE_FIELD_NAME, unstorable);
      continue;
    }
    const entry = {questionId, key: outcome.key};
    const size = measure(entry);
    if (used + size > MAX_DOCUMENT_BYTES) {
      refuse(at, questionId, REASON.DOCUMENT_FULL, null);
      continue;
    }
    used += size;
    claimed.add(questionId);
    keys.push(entry);
  }

  return {
    id: lessonId,
    lessonId,
    version: DOCUMENT_VERSION,
    publicHalfRedacted: PUBLIC_HALF_REDACTED,
    keys,
    refusals,
    omitted,
  };
}

/**
 * Groups a publication's questions by lesson and returns the key documents as `path → data`, the
 * shape `publicDocuments` already speaks, alongside every refusal for the caller to log.
 *
 * The refusals come back rather than staying in the documents alone because nothing reads that
 * collection: the rules deny it to every client and there is no admin surface, so "recorded" and
 * "visible" are not the same thing. Each returned record is a stored refusal plus its `lessonId`,
 * so a log line names the document to go and look at.
 *
 * @returns `{documents, refusals}`
 */
function questionKeyDocuments(questions, options) {
  const list = Array.isArray(questions) ? questions : [];
  const byLesson = new Map();
  const refusals = [];

  for (let at = 0; at < list.length; at += 1) {
    const question = list[at];
    const lessonId = question && typeof question.lessonId === "string" ? question.lessonId : "";
    // A lesson id Firestore will not take as a document id fails `db.doc()` and with it the whole
    // publish. Refused by name here, where it costs one question its key instead of costing the
    // submission everything.
    if (!isStorableDocumentId(lessonId)) {
      refusals.push({
        lessonId,
        index: at,
        questionId: question && typeof question.id === "string" ? question.id : "",
        reason: REASON.UNUSABLE_LESSON_ID,
        detail: detailOf(lessonId),
      });
      continue;
    }
    if (!byLesson.has(lessonId)) byLesson.set(lessonId, {questions: [], indices: []});
    const bucket = byLesson.get(lessonId);
    bucket.questions.push(question);
    bucket.indices.push(at);
  }

  const documents = {};
  for (const [lessonId, bucket] of byLesson.entries()) {
    const document = lessonKeyDocument(lessonId, bucket.questions, {
      random: options && options.random,
      indices: bucket.indices,
    });
    documents[keyDocumentPath(lessonId)] = document;
    for (const record of document.refusals) refusals.push({lessonId, ...record});
    if (document.omitted > 0) {
      refusals.push({
        lessonId,
        index: -1,
        questionId: "",
        reason: REASON.DOCUMENT_FULL,
        detail: `${document.omitted} further records did not fit`,
      });
    }
  }
  return {documents, refusals};
}

module.exports = {
  KEY_COLLECTION,
  DOCUMENT_VERSION,
  PUBLIC_HALF_REDACTED,
  MAX_DOCUMENT_BYTES,
  MAX_DOCUMENT_ID_BYTES,
  REASON,
  keyDocumentPath,
  isStorableFieldName,
  isStorableDocumentId,
  lessonKeyDocument,
  questionKeyDocuments,
};
