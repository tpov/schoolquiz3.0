"use strict";

const {questionKeyDocuments, REASON} = require("./question-key-store");
const {REDACTED_TYPE, REFUSAL} = require("./question-redaction");
const {canonicalQuestionId} = require("./lesson-reward");

/**
 * What redaction would do to the catalog that exists today, worked out before anything is written.
 *
 * Every question in production was written straight into `questions/{id}` by a seed script and
 * never went through publication, so the key store has never seen any of them. This module takes
 * the catalog's question documents as they are stored and answers three questions about them: which
 * key documents publication would write, which questions it would refuse and why, and what the
 * whole catalog looks like by lesson — counts by outcome, by refusal reason, by difficulty.
 * `scripts/redact-existing-questions.js` is the walk, the print and the write; this is the
 * decision, kept free of `firebase-admin` so it is gated by `npm test` beside the modules it
 * composes.
 *
 * Three rules carry it:
 *
 * 1. **The key store decides; this module counts.** Which questions get a key, which are refused
 *    and why, and what each document contains are `questionKeyDocuments`'s to say, and it is
 *    called once over the whole catalog exactly as publication calls it over one submission.
 *    Nothing here re-derives a key or a refusal. What this module adds is what the key store does
 *    not record: the archived questions it is never shown, the Surveys it deliberately says nothing
 *    about, and the payload facts a report needs — difficulty, dialect, translation.
 *
 * 2. **A Survey is what is left.** The key store's contract is that a question producing neither a
 *    key nor a refusal is a Survey and nothing else (`question-key-store.js`, decision 3). So the
 *    not-applicable count is the remainder per lesson: questions handed over, minus keys, minus
 *    refusals recorded, minus refusals that did not fit. Re-reading the payload here to spot a
 *    Survey would be a second copy of the redactor's rule, free to drift from the first.
 *
 * 3. **Nothing is guessed from a missing field — in the tally.** `lessonAllocatedSeconds` reads an
 *    absent difficulty as EASY because it has to price the lesson somehow. A report has no such
 *    need: a payload that does not say EASY or HARD is tallied as unreadable, and the tally never
 *    reads the document's own `difficulty` field, which public documents do not carry. The key
 *    path is a different matter. That field reaches the key store exactly as publication sends it
 *    — coerced through `stringValue`, as the redactor's fallback for the public half this pass
 *    discards — because the documents produced here have to be the ones publication would write.
 *
 * Two things are held back, or left alone, rather than reported as if they were settled:
 *
 * - **A lesson holding a payload that is already one of the redactor's public shapes.**
 *   `STATUS.ALREADY_REDACTED` in `question-redaction.js` says what such a question needs: publish
 *   it as it stands and leave any stored key alone. The document this module would build for the
 *   lesson carries no key for it — at best a refusal — and writing that would replace the stored
 *   key with nothing. So the lesson goes to `withheld`, by name. The decision is taken from the
 *   payload's discriminator (`REDACTED_TYPE`), not from the refusal list: a lesson whose key budget
 *   has filled records every later refusal only as a count (`omitted`), with no reason attached,
 *   and a guard that read reasons would let exactly that lesson through. Publication shares this
 *   hazard — `publicDocuments` writes the same key-only document — and that is recorded separately.
 *
 * - **`archived` is read on the question document only.** Nothing in `index.js` writes it there
 *   today — `setPublicQuestShelf` archives the quest, not its questions — so this filter honours
 *   the flag `questionRowFor` honours on the reward path and nothing more: a question under an
 *   archived quest is keyed like any other. Consulting the quest chain is deferred, and the report
 *   says so.
 */

const DIFFICULTY = {
  EASY: "EASY",
  HARD: "HARD",
  /** A payload that does not say EASY or HARD: absent, `""`, not a string, another word, not JSON. */
  UNREADABLE: "unreadable",
};

/**
 * The discriminator the `question` literal in `scripts/seed-hierarchy.js` writes. The Kotlin parser
 * reads the legacy shape by this key alone (`KotlinxSerializationQuestionContentParser.kt`,
 * `LEGACY_SINGLE_CHOICE`), and the redactor refuses it as `unknown-type` by design — which is why
 * the report counts it separately from every other unknown discriminator, and only there: a legacy
 * payload refused for some other reason (an unusable lesson id, say) is that other thing.
 */
const LEGACY_DIALECT_TYPE = "single-choice";

/** The discriminators of the redactor's own public shapes; a payload wearing one has its key elsewhere. */
const REDACTED_SHAPES = new Set(Object.values(REDACTED_TYPE));

/** Why a lesson's document is in `withheld` rather than `documents`. */
const WITHHELD = {
  ALREADY_REDACTED: REASON.ALREADY_REDACTED,
};

/** `stringValue` as `index.js` defines it: null, undefined and `""` fall back; anything else is a string. */
function stringValue(value, fallback) {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
}

/**
 * One catalog row as the key store reads it, with the document id kept beside it.
 *
 * `id`, `lessonId` and `difficulty` are coerced the way `normalizeQuestion` coerces them for
 * publication, so a document without an `id` field is keyed under its document id, as it would be
 * published. `documentId` travels separately because it is the only name an operator can look up:
 * a document whose `id` field differs from its path, or repeats another's, is refused under the
 * field's value and has to be found by the path's.
 *
 * `payload` is passed through as stored. Publication's `stringValue` would turn a missing payload
 * into `""`, which the redactor reports as `malformed-json`; handed over untouched it is reported
 * as `not-a-string`, which is what actually happened. No key is produced either way.
 */
function toQuestion(row) {
  const documentId = row && typeof row.id === "string" ? row.id : "";
  const data = row && row.data && typeof row.data === "object" && !Array.isArray(row.data) ? row.data : {};
  return {
    documentId,
    id: stringValue(data.id, documentId),
    lessonId: stringValue(data.lessonId, ""),
    difficulty: stringValue(data.difficulty, ""),
    payload: data.payload,
    archived: data.archived === true,
  };
}

/** The payload as an object, or null when it is not one. Only the report reads this; the redactor parses for itself. */
function readPayload(payload) {
  if (typeof payload !== "string") return null;
  try {
    const parsed = JSON.parse(payload);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch (error) {
    return null;
  }
}

/**
 * Read as `lessonAllocatedSeconds` reads it — case-folded — but never defaulted: that function's
 * `|| "EASY"` is the guess this report refuses to make.
 */
function difficultyBucket(source) {
  if (!source || typeof source.difficulty !== "string") return DIFFICULTY.UNREADABLE;
  const level = source.difficulty.toUpperCase();
  return level === DIFFICULTY.EASY || level === DIFFICULTY.HARD ? level : DIFFICULTY.UNREADABLE;
}

function isLegacyDialect(source) {
  return source !== null && source.type === LEGACY_DIALECT_TYPE;
}

function isRedactedShape(source) {
  return source !== null && REDACTED_SHAPES.has(source.type);
}

function emptyTally() {
  return {
    questions: 0,
    archived: 0,
    considered: 0,
    /** Keys in documents that will be written. */
    keyed: 0,
    /** Keys in documents that are withheld — produced, reported, not written. */
    keysWithheld: 0,
    notApplicable: 0,
    refused: 0,
    refusedByReason: {},
    difficulty: {[DIFFICULTY.EASY]: 0, [DIFFICULTY.HARD]: 0, [DIFFICULTY.UNREADABLE]: 0},
    /** Legacy payloads refused as `unknown-type` — never more than that reason's count. */
    legacyDialect: 0,
    /** `q1__ru` beside a `q1` in the same lesson. */
    translatedVariants: 0,
    /** An id shaped like a variant whose base is not in the lesson — `intro__part`, or an orphan. */
    variantsWithoutBase: 0,
    /** Documents to write that carry no key at all: a lesson of Surveys, or of refusals only. */
    emptyDocuments: 0,
  };
}

function countRefusal(tally, reason, by) {
  tally.refused += by;
  tally.refusedByReason[reason] = (tally.refusedByReason[reason] || 0) + by;
}

function compareLessonIds(a, b) {
  if (a.lessonId < b.lessonId) return -1;
  return a.lessonId > b.lessonId ? 1 : 0;
}

/**
 * Plans the catalog.
 *
 * @param rows the `questions` collection as read: `[{id, data}]`, one per document, `id` the
 *   document id and `data` its fields. Order is the catalog's; a lesson's questions need not be
 *   adjacent, which is the point — the caller collects every page before calling this.
 * @param options `{random}`, forwarded to the key store so a test can name one concrete shuffle.
 * @returns `{documents, withheld, refusals, summary}`.
 *   - `documents` — `path → data`, the key documents to write, straight from the key store.
 *   - `withheld` — `[{lessonId, path, reason, questionIds}]`, lessons whose document must not be
 *     written; see the module note.
 *   - `refusals` — every refusal the key store recorded, each with `lessonId`, `questionId`,
 *     `documentId` and `reason`; `index` is the question's position among the non-archived
 *     questions in catalog order. A record with `index: -1` and no ids stands for the refusals that
 *     did not fit in a full document.
 *   - `summary.overall` and `summary.lessons[]` (sorted by lesson id) — the same tally at both
 *     levels; see `emptyTally`. Each lesson also names its `document` path (with `emptyDocument`
 *     when that document holds no key), or `null` with `withheld` set when it was held back.
 */
function planCatalogRedaction(rows, options) {
  const list = Array.isArray(rows) ? rows : [];
  const random = options && typeof options.random === "function" ? options.random : undefined;

  const overall = emptyTally();
  const byLesson = new Map();
  const lessonOf = (lessonId) => {
    if (!byLesson.has(lessonId)) {
      byLesson.set(lessonId, {lessonId, document: null, emptyDocument: false, withheld: null, ...emptyTally()});
    }
    return byLesson.get(lessonId);
  };

  // Every id per lesson, archived included: a translated variant's base is a document whether or
  // not it is live, and the second pass needs the whole set to tell `q1__ru` from `intro__part`.
  const questions = list.map(toQuestion);
  const idsByLesson = new Map();
  for (const question of questions) {
    if (!idsByLesson.has(question.lessonId)) idsByLesson.set(question.lessonId, new Set());
    idsByLesson.get(question.lessonId).add(question.id);
  }

  const considered = [];
  const facts = [];
  const redactedByLesson = new Map();
  for (const question of questions) {
    const lesson = lessonOf(question.lessonId);
    lesson.questions += 1;
    overall.questions += 1;
    if (question.archived) {
      lesson.archived += 1;
      overall.archived += 1;
      continue;
    }
    lesson.considered += 1;
    overall.considered += 1;

    const source = readPayload(question.payload);
    const bucket = difficultyBucket(source);
    lesson.difficulty[bucket] += 1;
    overall.difficulty[bucket] += 1;

    const canonical = canonicalQuestionId(question.id);
    if (canonical !== question.id) {
      const field = idsByLesson.get(question.lessonId).has(canonical) ? "translatedVariants" : "variantsWithoutBase";
      lesson[field] += 1;
      overall[field] += 1;
    }
    if (isRedactedShape(source)) {
      if (!redactedByLesson.has(question.lessonId)) redactedByLesson.set(question.lessonId, []);
      redactedByLesson.get(question.lessonId).push(question.id);
    }

    facts.push({lessonId: question.lessonId, documentId: question.documentId, legacy: isLegacyDialect(source)});
    considered.push({
      documentId: question.documentId,
      id: question.id,
      lessonId: question.lessonId,
      difficulty: question.difficulty,
      payload: question.payload,
    });
  }

  const keyed = questionKeyDocuments(considered, {random});

  const reasonByIndex = new Map();
  const refusals = [];
  for (const record of keyed.refusals) {
    // The record for refusals that did not fit stands for `omitted` questions, not one; they are
    // counted from the document below, where the number lives, and it names no document.
    if (record.index < 0) {
      refusals.push({...record, documentId: ""});
      continue;
    }
    const fact = facts[record.index];
    refusals.push({...record, documentId: fact ? fact.documentId : ""});
    reasonByIndex.set(record.index, record.reason);
    countRefusal(lessonOf(record.lessonId), record.reason, 1);
    countRefusal(overall, record.reason, 1);
  }

  facts.forEach((fact, index) => {
    if (fact.legacy && reasonByIndex.get(index) === REFUSAL.UNKNOWN_TYPE) {
      lessonOf(fact.lessonId).legacyDialect += 1;
      overall.legacyDialect += 1;
    }
  });

  const documents = {};
  const withheld = [];
  for (const [path, document] of Object.entries(keyed.documents)) {
    const lesson = lessonOf(document.lessonId);
    if (document.omitted > 0) {
      countRefusal(lesson, REASON.DOCUMENT_FULL, document.omitted);
      countRefusal(overall, REASON.DOCUMENT_FULL, document.omitted);
    }
    const redacted = redactedByLesson.get(document.lessonId);
    if (redacted) {
      lesson.keysWithheld += document.keys.length;
      overall.keysWithheld += document.keys.length;
      lesson.withheld = {reason: WITHHELD.ALREADY_REDACTED, questionIds: redacted};
      withheld.push({lessonId: document.lessonId, path, reason: WITHHELD.ALREADY_REDACTED, questionIds: redacted});
      continue;
    }
    lesson.keyed += document.keys.length;
    overall.keyed += document.keys.length;
    lesson.document = path;
    lesson.emptyDocument = document.keys.length === 0;
    if (lesson.emptyDocument) {
      lesson.emptyDocuments += 1;
      overall.emptyDocuments += 1;
    }
    documents[path] = document;
  }

  for (const lesson of byLesson.values()) {
    lesson.notApplicable = lesson.considered - lesson.keyed - lesson.keysWithheld - lesson.refused;
    overall.notApplicable += lesson.notApplicable;
  }

  const lessons = [...byLesson.values()].sort(compareLessonIds);
  return {documents, withheld, refusals, summary: {overall, lessons}};
}

module.exports = {
  DIFFICULTY,
  LEGACY_DIALECT_TYPE,
  WITHHELD,
  planCatalogRedaction,
};
