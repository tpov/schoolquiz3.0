"use strict";

const {evaluateAnswer, computePercentScore} = require("./assessment-scoring");
const {
  CONTENT_TYPE,
  KEY_VERSION,
  REDACTED_TYPE,
  restoreContent,
  translateSubmittedAnswer,
} = require("./question-redaction");
const {DOCUMENT_VERSION, REASON: KEY_STORE_REASON} = require("./question-key-store");

/**
 * Scores one whole attempt: a lesson's questions, what was actually put to the player, the stored
 * answer keys and the player's answers in — a codeAnswer and a percent out.
 *
 * Every part of this already exists. `assessment-scoring.js` scores one answer against one
 * question; `question-redaction.js` reassembles a public half with its key and rewrites a submitted
 * answer out of the re-issued ids; `question-key-store.js` is the document those keys live in.
 * Nothing turned the three into the two numbers the rest of the server runs on, so every caller
 * that wanted them would have composed the three for itself — and composed them differently.
 *
 * Nothing calls it yet. The submit handler starts calling it when the client stops sending a score
 * of its own; until then it is pure, free of `firebase-admin`, and testable with plain `node`.
 *
 * ---
 *
 * **The denominator is the whole problem, and the answers cannot supply it.**
 *
 * `'0'` means "this question was never put to the player", and `computePercentScore` *drops* those
 * positions rather than averaging them in as zeros. So whoever decides which positions are `'0'`
 * decides what the score is divided by. An earlier draft of this module inferred that from the
 * answers it was handed — a position with no usable answer became `'0'` — and it was wrong in the
 * most expensive direction available. An honest attempt of two right and three wrong scored
 * `99111`, 40 percent. Sending `answerPayload: "{"` for the three wrong ones scored `99000`, **100
 * percent**. Simply omitting them scored `99000` too, with nothing recorded at all. In the module
 * whose entire purpose is to stop taking the client's word, the client still chose its own
 * denominator.
 *
 * That is not fixable by validating answers harder, because whether a question was *served* is not
 * a fact any answer can carry: an unanswered question and a question never shown look identical
 * from the answers alone. So it is an input. `served` says which positions were put to the player
 * and which question sat at each, and from it:
 *
 * - a position not in `served` is `'0'` — never shown, and rightly excluded from the percent;
 * - a served position with no valid answer is `'1'` — shown, not answered, and counted;
 * - only a served position with a valid answer gets a scored digit.
 *
 * `'1'` for served-but-unanswered is also what the client itself writes when a player abandons a
 * run (`RunnerLogic.kt:149-154`, `buildCodeAnswerOnAbort`). The earlier draft wrote `'0'` there and
 * disagreed with the client by 33 percent against 100 on the same attempt.
 *
 * ---
 *
 * **A client's fault and a server's fault are different outcomes.**
 *
 * A malformed payload, an index that disagrees with what was served, a duplicate claim — those are
 * a served question that produced no valid answer, which is exactly what `'1'` means. But a missing
 * key, a crossed key, an unreadable stored payload or a question type nothing here can score are
 * *our* gaps, and neither digit is honest about them: `'1'` punishes the player for our failure and
 * `'0'` pays them for it. So the attempt comes back `scorable: false` with `codeAnswer` and
 * `percentScore` both null, and the caller decides — the way `question-key-store.js` returns its
 * refusals rather than acting on them. There is no number to be accidentally paid on.
 *
 * ---
 *
 * Four further decisions carry the file:
 *
 * 1. **It composes, it does not re-derive.** Every digit comes out of `evaluateAnswer`, the percent
 *    out of `computePercentScore`, the reassembly out of `restoreContent`, the id rewriting out of
 *    `translateSubmittedAnswer`. There is no arithmetic here at all and there must not be:
 *    `assessment-scoring.js` is pinned to `Scoring.kt` by a shared fixture, and a second rounding
 *    decision taken here would be pinned to nothing.
 *
 * 2. **`served` places the digit; the client's `codeAnswerIndex` never does.** `normalizeLessonAnswers`
 *    clamps that field with `Math.max(0, …)`, so a missing or negative one arrives as `0` and would
 *    otherwise claim position 0 ahead of the question genuinely sitting there. Placement comes from
 *    the served record matched by question id, and an answer whose claimed index disagrees with
 *    where its question was served is refused as a client fault — it cannot steal a position, and it
 *    cannot lower the denominator either, because its position is still served and still `'1'`.
 *
 * 3. **A key is applied only to a payload that needs one, and only when the document says both
 *    halves came from one `redact` call.** The trigger is the payload's own discriminator. The
 *    inverse is checked too: a payload that still carries its answer, sitting in a lesson whose key
 *    document claims `publicHalfRedacted: true`, means an answer is world-readable in a lesson
 *    believed redacted — and this module is the only place positioned to notice.
 *
 * 4. **Translated exactly once, and the question is judged before the answer is.** `restoreContent`
 *    moves the question into original ids and `translateSubmittedAnswer` moves the answer the same
 *    way, each reached once on the one path that needs them; twice, or not at all, and an Ordering
 *    or FillBlank scores wrongly instead of failing. Everything about the stored question is
 *    settled first, before the answer is even parsed, because otherwise a client could convert any
 *    gap of ours into a fault of its own — and so into a scored attempt — by posting junk for the
 *    question we could not read.
 *
 * Nothing here throws. Every field of an attempt is client-controlled, and one crafted answer must
 * not be able to fail a submission. `computePercentScore` does throw on a non-digit, so the digit
 * written into the string is bounds-checked before it goes in.
 */

/** Never put to the player. `computePercentScore` drops these positions from the average. */
const NOT_SHOWN = "0";

/**
 * Put to the player, no valid answer came back. Counted in the percent, worth nothing.
 *
 * The same digit `buildCodeAnswerOnAbort` writes for a question a player walked away from, which is
 * what keeps this module and the client agreeing on an abandoned run.
 */
const NO_VALID_ANSWER = "1";

/** What `evaluateAnswer` is documented to return. Asserted, never clamped — see UNSCORABLE.SCORE. */
const MIN_DIGIT = 1;
const MAX_DIGIT = 9;

/** Client-supplied values are quoted back in a record; long ones are cut rather than carried whole. */
const MAX_DETAIL_CHARS = 200;

/**
 * How many records come back at most.
 *
 * `answers` is client-controlled and unbounded, and one record per answer with 200 characters of
 * detail apiece turns a crafted submission into megabytes held in memory and written to a log. The
 * store bounds its refusals by document bytes and counts what did not fit; there is no document
 * here, so the bound is a record count and `omitted` carries the same count.
 */
const MAX_RECORDS = 200;

/** Whose gap this is. A server fault makes the whole attempt unscorable; a client fault is a '1'. */
const FAULT = {CLIENT: "client", SERVER: "server"};

/**
 * The discriminators that mean "this payload has had its answer taken out", and the ones that mean
 * it has not.
 *
 * Both are derived from the exported maps rather than spelled out again: they are the wire
 * contract, and a private second copy would keep passing its own tests while a payload the redactor
 * now emits fell through to being scored as though it still carried its answer.
 */
const PUBLIC_TYPES = new Set(Object.values(REDACTED_TYPE));
const SOURCE_TYPES = new Set(Object.values(CONTENT_TYPE));
/** The four that carry an answer. A Survey is scored on participation and has nothing to hide. */
const ANSWER_BEARING_TYPES = new Set(
  Object.values(CONTENT_TYPE).filter((type) => type !== CONTENT_TYPE.SURVEY),
);

/**
 * Why an answer, or a whole attempt, could not be scored.
 *
 * Named constants for the same reason `question-key-store.js`'s `REASON` is: these are read by
 * whoever is looking at an attempt that scored lower than the player expected, and a caller
 * comparing against a typo'd literal matches nothing and reports "no problems".
 */
const UNSCORABLE = {
  // ----- the client's gap: a served question that produced no valid answer -----
  /** An answer carrying no question id. Shares its wire string with the store's own name for this. */
  MISSING_QUESTION_ID: KEY_STORE_REASON.MISSING_QUESTION_ID,
  /** An answer to a question that was never put to this player in this attempt. */
  NOT_SERVED: "not-served",
  /** A second answer to a question already answered. The newest by `answeredAtMs` is the one kept. */
  DUPLICATE_QUESTION: "duplicate-question",
  /** The answer's `answerPayload` is not a JSON object. */
  MALFORMED_ANSWER: "malformed-answer",
  /** The answer claims a position other than the one its question was served at. */
  INDEX_DISAGREES: "index-disagrees",

  // ----- our gap: the attempt cannot be scored at all -----
  /** No `served` was supplied, so there is no denominator and no honest percent to return. */
  SERVED_UNKNOWN: "served-unknown",
  /** `served` was supplied but is not a list of `{codeAnswerIndex, questionId}` inside the pool. */
  SERVED_MALFORMED: "served-malformed",
  /** The pool has a question with no id, or two questions with one id. */
  POOL_MALFORMED: "pool-malformed",
  /** A question named by `served` is not in the pool. */
  QUESTION_MISSING: "question-missing",
  /** The question's stored `payload` is not a JSON object. */
  MALFORMED_QUESTION: "malformed-question",
  /** A stored `type` neither `QuestionContent` nor `redact` produces. Nothing here can score it. */
  UNRECOGNISED_CONTENT: "unrecognised-content",
  /** A payload still carrying its answer in a lesson whose key document says it was redacted. */
  ANSWER_LEAKED: "answer-leaked",
  /** The key document is not the shape `lessonKeyDocument` writes — `keys` is not a list, say. */
  DOCUMENT_MALFORMED: "document-malformed",
  /** The key document's own shape version is one this module does not know how to read. */
  DOCUMENT_VERSION: "document-version",
  /** The key document belongs to a different lesson than the pool does. */
  DOCUMENT_MISMATCHED: "document-mismatched",
  /** A redacted payload with no key filed for it. */
  KEY_MISSING: "key-missing",
  /** Two key entries filed under one question id; which one is the answer is unanswerable. */
  KEY_AMBIGUOUS: "key-ambiguous",
  /** A key stamped with a version this module does not know how to read. */
  KEY_VERSION: "key-version",
  /** A key that names a different question than the one it is filed under. */
  KEY_MISMATCHED: "key-mismatched",
  /** The document says its public half was never redacted, and the payload is redacted anyway. */
  KEY_GENERATION: "key-generation",
  /** The key and the public half did not reassemble — crossed ids, a missing field, a bad shape. */
  UNRESTORABLE: "unrestorable",
  /**
   * The submitted answer could not be rewritten into the question's original ids.
   *
   * Unreachable through this module's own path: `restoreContent` returns null first for every key
   * whose type `translateSubmittedAnswer` would reject. Declared, and kept, so the module stays
   * total rather than depending on that argument staying true.
   */
  UNTRANSLATABLE: "untranslatable",
  /**
   * `evaluateAnswer` returned something outside 1..9.
   *
   * Unreachable by that module's contract, and kept for the same reason as the one above: a
   * non-digit reaching the string would make `computePercentScore` throw, turning a scoring anomaly
   * into a failed submission. Refused rather than clamped — a clamp would invent a digit.
   */
  SCORE: "score-out-of-range",
};

/**
 * Whose gap each reason is.
 *
 * A separate table rather than a field on each reason, so that adding a reason without classifying
 * it is a missing key rather than a silent default — and `attempt-scoring.test.js` asserts every
 * reason appears here. A wrong default is the expensive kind of mistake in this file: a server
 * fault misfiled as a client fault charges the player `'1'` for our own missing key.
 */
const FAULT_OF = {
  [UNSCORABLE.MISSING_QUESTION_ID]: FAULT.CLIENT,
  [UNSCORABLE.NOT_SERVED]: FAULT.CLIENT,
  [UNSCORABLE.DUPLICATE_QUESTION]: FAULT.CLIENT,
  [UNSCORABLE.MALFORMED_ANSWER]: FAULT.CLIENT,
  [UNSCORABLE.INDEX_DISAGREES]: FAULT.CLIENT,

  [UNSCORABLE.SERVED_UNKNOWN]: FAULT.SERVER,
  [UNSCORABLE.SERVED_MALFORMED]: FAULT.SERVER,
  [UNSCORABLE.POOL_MALFORMED]: FAULT.SERVER,
  [UNSCORABLE.QUESTION_MISSING]: FAULT.SERVER,
  [UNSCORABLE.MALFORMED_QUESTION]: FAULT.SERVER,
  [UNSCORABLE.UNRECOGNISED_CONTENT]: FAULT.SERVER,
  [UNSCORABLE.ANSWER_LEAKED]: FAULT.SERVER,
  [UNSCORABLE.DOCUMENT_MALFORMED]: FAULT.SERVER,
  [UNSCORABLE.DOCUMENT_VERSION]: FAULT.SERVER,
  [UNSCORABLE.DOCUMENT_MISMATCHED]: FAULT.SERVER,
  [UNSCORABLE.KEY_MISSING]: FAULT.SERVER,
  [UNSCORABLE.KEY_AMBIGUOUS]: FAULT.SERVER,
  [UNSCORABLE.KEY_VERSION]: FAULT.SERVER,
  [UNSCORABLE.KEY_MISMATCHED]: FAULT.SERVER,
  [UNSCORABLE.KEY_GENERATION]: FAULT.SERVER,
  [UNSCORABLE.UNRESTORABLE]: FAULT.SERVER,
  [UNSCORABLE.UNTRANSLATABLE]: FAULT.SERVER,
  [UNSCORABLE.SCORE]: FAULT.SERVER,
};

/**
 * Client-supplied text, cut to something a record and a log line can both carry.
 *
 * Deliberately not `question-key-store.js`'s `detailOf`, and named differently so the two are not
 * mistaken for each other: that one returns null for anything that is not a string, because every
 * detail it quotes is author-supplied text. The details here include numbers — a key version, a
 * claimed index — and dropping those would leave the record saying a version was wrong without
 * saying which.
 */
function quote(value) {
  if (value === null || value === undefined) return null;
  const text = String(value);
  return text.length <= MAX_DETAIL_CHARS ? text : `${text.slice(0, MAX_DETAIL_CHARS)}…`;
}

/** Collects records under a fixed budget, counting what did not fit rather than dropping it. */
function createRecorder() {
  const records = [];
  let omitted = 0;
  let serverFaults = 0;
  return {
    add(questionId, codeAnswerIndex, reason, detail) {
      const fault = FAULT_OF[reason];
      if (fault === FAULT.SERVER) serverFaults += 1;
      if (records.length >= MAX_RECORDS) {
        omitted += 1;
        return;
      }
      records.push({
        questionId,
        codeAnswerIndex,
        reason,
        fault,
        detail: detail === undefined ? null : detail,
      });
    },
    // Counted rather than read off `records`, so a server fault past the budget still stops the
    // attempt being scored. Budgeting must bound what is reported, never what is decided.
    get blocked() {
      return serverFaults > 0;
    },
    get records() {
      return records;
    },
    get omitted() {
      return omitted;
    },
  };
}

/**
 * Reads a JSON object out of a stored string, or hands back an object as it stands.
 *
 * Arrays and JSON scalars are rejected along with unparseable text: `"[]"` and `"null"` both parse,
 * and both would reach `evaluateAnswer` as a shape it scores 1 rather than as the malformed payload
 * they are.
 */
function parseObject(value) {
  let parsed = value;
  if (typeof parsed === "string") {
    try {
      parsed = JSON.parse(parsed);
    } catch (error) {
      return null;
    }
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
  return parsed;
}

/**
 * The pool by question id, plus the one lesson it belongs to.
 *
 * A question with no id cannot be named by `served` and a duplicate id makes "which question sits
 * here" unanswerable, so both are refused rather than resolved — the caller handed over a pool that
 * is not a pool, and blaming an answer for it would point at the wrong thing. More than one lesson
 * in the pool is the same defect wearing a different hat: the positions would be a merge of two
 * attempts' worth of questions.
 *
 * @returns `{byId, lessonId}` or a reason string.
 */
function readPool(questions) {
  const byId = new Map();
  const lessons = new Set();
  for (const question of questions) {
    const id = question && typeof question.id === "string" ? question.id : "";
    if (id === "" || byId.has(id)) return UNSCORABLE.POOL_MALFORMED;
    byId.set(id, question);
    const lessonId = question && typeof question.lessonId === "string" ? question.lessonId : "";
    if (lessonId !== "") lessons.add(lessonId);
  }
  if (lessons.size > 1) return UNSCORABLE.POOL_MALFORMED;
  return {byId, lessonId: lessons.size === 1 ? [...lessons][0] : ""};
}

/**
 * What was put to the player, checked and put in position order.
 *
 * @param served `[{codeAnswerIndex, questionId}]` — one entry per question actually shown, whatever
 *   the player then did with it. This is the attempt's subset (`playOrder`), not the eligible pool:
 *   a question the subset never reached was not served and is `'0'`.
 * @returns a list sorted by position, or a reason string. An empty list is legal and means nothing
 *   was shown — a lesson opened and closed again.
 */
function readServed(served, poolSize) {
  if (!Array.isArray(served)) return UNSCORABLE.SERVED_UNKNOWN;
  const positions = new Set();
  const ids = new Set();
  const list = [];
  for (const entry of served) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) return UNSCORABLE.SERVED_MALFORMED;
    const at = entry.codeAnswerIndex;
    const questionId = entry.questionId;
    if (!Number.isInteger(at) || at < 0 || at >= poolSize) return UNSCORABLE.SERVED_MALFORMED;
    if (typeof questionId !== "string" || questionId === "") return UNSCORABLE.SERVED_MALFORMED;
    if (positions.has(at) || ids.has(questionId)) return UNSCORABLE.SERVED_MALFORMED;
    positions.add(at);
    ids.add(questionId);
    list.push({codeAnswerIndex: at, questionId});
  }
  // Position order, so the walk below — and therefore the records it produces — does not depend on
  // the order the caller happened to assemble the subset in.
  list.sort((left, right) => left.codeAnswerIndex - right.codeAnswerIndex);
  return list;
}

/**
 * The key document's `keys` by question id, plus the ids it names more than once.
 *
 * Anything that is not the shape `lessonKeyDocument` writes is refused whole rather than skipped
 * entry by entry. Skipping was the earlier draft's mistake: `keys` arriving as a map instead of a
 * list read as "no keys", and every redacted question came back unscorable at `'0'` — an all-zero
 * codeAnswer at 0 percent for every hard attempt in the corpus, reported as if the keys had simply
 * never been written.
 *
 * @returns `{byQuestionId, ambiguous}` or a reason string.
 */
function readKeys(keyDocument) {
  if (keyDocument === null) return {byQuestionId: new Map(), ambiguous: new Set()};
  if (keyDocument.version !== DOCUMENT_VERSION) return UNSCORABLE.DOCUMENT_VERSION;
  if (!Array.isArray(keyDocument.keys)) return UNSCORABLE.DOCUMENT_MALFORMED;

  const byQuestionId = new Map();
  const ambiguous = new Set();
  for (const entry of keyDocument.keys) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) return UNSCORABLE.DOCUMENT_MALFORMED;
    if (typeof entry.questionId !== "string" || entry.questionId === "") {
      return UNSCORABLE.DOCUMENT_MALFORMED;
    }
    const key = entry.key;
    if (!key || typeof key !== "object" || Array.isArray(key)) return UNSCORABLE.DOCUMENT_MALFORMED;
    // A key's own stamp is `null` when `redact` had no id to take — the seed corpus payloads carry
    // none. Anything that is neither a string nor absent is a document nobody in this repo writes,
    // and letting it through is how a crossed key gets applied: the pairing check below compares
    // strings, and a number slips past a comparison written to skip what it cannot judge.
    if (key.questionId !== null && key.questionId !== undefined && typeof key.questionId !== "string") {
      return UNSCORABLE.DOCUMENT_MALFORMED;
    }
    if (byQuestionId.has(entry.questionId)) {
      ambiguous.add(entry.questionId);
      continue;
    }
    byQuestionId.set(entry.questionId, key);
  }
  return {byQuestionId, ambiguous};
}

/**
 * Resolves a served question into something scorable, and the key its answer must be rewritten with.
 *
 * Everything about the *question* is decided here, before anything about the answer is looked at.
 * The order is load-bearing: an unreadable stored payload is our gap, and if a malformed answer were
 * checked first, a client could convert any server fault into a client fault — and a scored attempt
 * — simply by posting junk for the question we could not read. Our gaps must not be suppressible by
 * the party they would otherwise refuse.
 *
 * @returns `{content, key}` — `key` null when the payload carries its own answer — or
 *   `{reason, detail}`.
 */
function readQuestion(questionId, question, keys, publicHalfRedacted) {
  const content = parseObject(question.payload);
  if (content === null) return {reason: UNSCORABLE.MALFORMED_QUESTION};

  if (!PUBLIC_TYPES.has(content.type)) {
    // A type neither namespace knows would fall through to `evaluateAnswer`'s floor and charge the
    // player for a question nothing here can read.
    if (!SOURCE_TYPES.has(content.type)) {
      return {reason: UNSCORABLE.UNRECOGNISED_CONTENT, detail: quote(content.type)};
    }
    // The inverse generation mismatch. The document says both halves came from one `redact` call,
    // yet this payload still holds its answer — so the answer to a question in a lesson believed
    // redacted is sitting in a world-readable document. Nothing else is positioned to see it.
    if (publicHalfRedacted && ANSWER_BEARING_TYPES.has(content.type)) {
      return {reason: UNSCORABLE.ANSWER_LEAKED, detail: quote(content.type)};
    }
    return {content, key: null};
  }

  if (keys.ambiguous.has(questionId)) return {reason: UNSCORABLE.KEY_AMBIGUOUS};
  const key = keys.byQuestionId.get(questionId);
  if (key === undefined) return {reason: UNSCORABLE.KEY_MISSING};
  if (key.version !== KEY_VERSION) return {reason: UNSCORABLE.KEY_VERSION, detail: quote(key.version)};
  // The key's own stamp against the document id it is filed under, which always exists.
  // `restoreContent` makes the same comparison against the payload's `id`, which often does not.
  if (typeof key.questionId === "string" && key.questionId !== "" && key.questionId !== questionId) {
    return {reason: UNSCORABLE.KEY_MISMATCHED, detail: quote(key.questionId)};
  }
  if (!publicHalfRedacted) return {reason: UNSCORABLE.KEY_GENERATION};

  const restored = restoreContent(content, key);
  if (restored === null) return {reason: UNSCORABLE.UNRESTORABLE};
  return {content: restored, key};
}

/**
 * The answers by question id, newest first, with everything unusable recorded.
 *
 * Newest by `answeredAtMs` rather than first by arrival: a sync batch can be reordered between the
 * device and here, and taking whichever copy arrived first would score a stale answer and refuse
 * the newer one. Ties fall back to arrival position so the choice is total.
 */
function readAnswers(answers, servedIds, records) {
  const byQuestionId = new Map();
  const ordered = answers
    .map((answer, arrivedAt) => ({answer, arrivedAt}))
    .sort((left, right) => {
      const leftAt = Number.isFinite(left.answer && left.answer.answeredAtMs) ? left.answer.answeredAtMs : 0;
      const rightAt = Number.isFinite(right.answer && right.answer.answeredAtMs) ? right.answer.answeredAtMs : 0;
      return leftAt === rightAt ? left.arrivedAt - right.arrivedAt : leftAt - rightAt;
    });

  for (const {answer} of ordered) {
    const questionId = answer && typeof answer.questionId === "string" ? answer.questionId : "";
    // One defect, one label. An answer with no id cannot be matched to anything, and calling it
    // "unknown question" and then "duplicate" for a second one names the same hole twice.
    if (questionId === "") {
      records.add("", -1, UNSCORABLE.MISSING_QUESTION_ID, null);
      continue;
    }
    if (!servedIds.has(questionId)) {
      records.add(questionId, -1, UNSCORABLE.NOT_SERVED, null);
      continue;
    }
    // The newest wins; the copy it replaces is named rather than dropped.
    if (byQuestionId.has(questionId)) {
      records.add(questionId, -1, UNSCORABLE.DUPLICATE_QUESTION, null);
    }
    byQuestionId.set(questionId, answer);
  }
  return byQuestionId;
}

/**
 * Scores a whole attempt.
 *
 * @param input `{questions, served, keyDocument, answers}`.
 *
 *   - `questions` — **the attempt's eligible pool**: the lesson's questions filtered to the
 *     attempt's difficulty and sorted by `(order, sourceId)`, exactly as
 *     `StartLessonAttemptUseCase.kt:93-95` assembled it when the attempt was played. Its length is
 *     the length of the codeAnswer (`CodeAnswer.kt:5`) and each question's position in it is that
 *     question's `codeAnswerIndex`. Any other pool — unsorted, unfiltered, or from a later edition
 *     of the lesson — silently shifts every digit, so a mismatch shows up as `served` naming a
 *     question the pool does not hold rather than as a wrong score.
 *   - `served` — `[{codeAnswerIndex, questionId}]`, one per question actually put to the player.
 *     Required: without it there is no denominator and no honest percent, so an attempt missing it
 *     comes back unscorable rather than scored against whatever the answers implied.
 *   - `keyDocument` — the lesson's `question_keys` document, or null for a lesson that has none.
 *     `version`, `lessonId`, `keys` and `publicHalfRedacted` are read; `refusals` is a record for
 *     people, not an input to scoring.
 *   - `answers` — the attempt's answers, as `normalizeLessonAnswers` leaves them. `questionId`,
 *     `answerPayload` and `answeredAtMs` are read, and `codeAnswerIndex` is checked against
 *     `served` but never used to place a digit. The client's own `score` is **not** read at all:
 *     taking the answer key off the device is pointless if the score that came with it is believed.
 *
 * @returns `{scorable, codeAnswer, percentScore, unscorable, omitted}`.
 *   - `scorable: false` means a gap on our side — a missing key, an unreadable payload, a pool or a
 *     document that is not what it claims. `codeAnswer` and `percentScore` are then **null**: there
 *     is no number that is honest about it in either direction, and the caller decides what to do
 *     with an attempt the server could not score.
 *   - `unscorable` holds `{questionId, codeAnswerIndex, reason, fault, detail}` per problem, in
 *     served-position order followed by the answers that matched no served question, capped at
 *     `MAX_RECORDS` with `omitted` counting the rest.
 */
function scoreAttempt(input) {
  const questions = Array.isArray(input && input.questions) ? input.questions : [];
  const answers = Array.isArray(input && input.answers) ? input.answers : [];
  const raw = input && input.keyDocument;
  const keyDocument = raw && typeof raw === "object" && !Array.isArray(raw) ? raw : null;
  const records = createRecorder();

  const pool = readPool(questions);
  const served = readServed(input && input.served, questions.length);
  const keys = readKeys(keyDocument);
  for (const [value, id] of [[pool, ""], [served, ""], [keys, ""]]) {
    if (typeof value === "string") records.add(id, -1, value, null);
  }
  // A pool, a subset or a document that is not what it claims makes every position below
  // meaningless, so the walk is not attempted at all rather than reported position by position.
  if (records.blocked) return refused(records);

  if (keyDocument !== null && pool.lessonId !== "" && keyDocument.lessonId !== pool.lessonId) {
    records.add("", -1, UNSCORABLE.DOCUMENT_MISMATCHED, quote(keyDocument.lessonId));
    return refused(records);
  }
  const publicHalfRedacted = keyDocument !== null && keyDocument.publicHalfRedacted === true;

  const servedIds = new Set(served.map((entry) => entry.questionId));
  const byQuestionId = readAnswers(answers, servedIds, records);
  const digits = new Array(questions.length).fill(NOT_SHOWN);

  for (const {codeAnswerIndex: at, questionId} of served) {
    // Served means shown, and shown means counted. Every branch below that fails to produce a digit
    // leaves this '1' in place — never the '0' that would drop the question from the denominator.
    digits[at] = NO_VALID_ANSWER;

    const question = pool.byId.get(questionId);
    if (question === undefined) {
      records.add(questionId, at, UNSCORABLE.QUESTION_MISSING, null);
      continue;
    }
    const answer = byQuestionId.get(questionId);
    // Shown and never answered: already '1', and the stored question is not examined at all. There
    // is nothing we failed to score, so a broken payload here is not a gap this attempt ran into.
    if (answer === undefined) continue;

    const resolved = readQuestion(questionId, question, keys, publicHalfRedacted);
    if (resolved.reason) {
      records.add(questionId, at, resolved.reason, resolved.detail);
      continue;
    }
    if (answer.codeAnswerIndex !== at) {
      records.add(questionId, at, UNSCORABLE.INDEX_DISAGREES, quote(answer.codeAnswerIndex));
      continue;
    }
    const submitted = parseObject(answer.answerPayload);
    if (submitted === null) {
      records.add(questionId, at, UNSCORABLE.MALFORMED_ANSWER, null);
      continue;
    }
    // Translated exactly once, here, on the one path that needs it.
    const translated = resolved.key === null
      ? submitted
      : translateSubmittedAnswer(submitted, resolved.key);
    if (translated === null) {
      records.add(questionId, at, UNSCORABLE.UNTRANSLATABLE, null);
      continue;
    }
    const digit = evaluateAnswer(resolved.content, translated);
    if (!Number.isInteger(digit) || digit < MIN_DIGIT || digit > MAX_DIGIT) {
      records.add(questionId, at, UNSCORABLE.SCORE, quote(digit));
      continue;
    }
    digits[at] = String(digit);
  }

  if (records.blocked) return refused(records);
  const codeAnswer = digits.join("");
  return {
    scorable: true,
    codeAnswer,
    percentScore: computePercentScore(codeAnswer),
    unscorable: records.records,
    omitted: records.omitted,
  };
}

/** An attempt the server could not score. No number, in either direction. */
function refused(records) {
  return {
    scorable: false,
    codeAnswer: null,
    percentScore: null,
    unscorable: records.records,
    omitted: records.omitted,
  };
}

module.exports = {
  NOT_SHOWN,
  NO_VALID_ANSWER,
  MAX_RECORDS,
  FAULT,
  FAULT_OF,
  UNSCORABLE,
  scoreAttempt,
};
