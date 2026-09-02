"use strict";

const {HttpsError} = require("firebase-functions/v2/https");
const {recomputePercentScore, isWellFormedCodeAnswer} = require("./result-verification");
const {FAULT} = require("./attempt-scoring");

/**
 * Reads one submitted attempt and decides who scored it, before anything is paid for it.
 *
 * Today's intake (`normalizeLessonResultAttemptEvent` in `index.js`) assumes the device did: digits
 * and a percent are mandatory, and one flag — `scoreVerified`, "the digits add up to the claimed
 * percent" — gates the charge, the nolics reward, the activity ratings and the tournament write.
 * Hard attempts are about to arrive with no digits at all, only the list of questions the device
 * was dealt (`served`, E2.10), and for those the flag means nothing: there are no digits to add up.
 *
 * So this module reads a body and classifies it as one of two kinds:
 *
 * - **Device-scored** — the body carries `codeAnswer` and `percentScore`. Accepted and rejected on
 *   exactly the conditions today's intake uses: the same checks, in the same order, throwing the
 *   same `HttpsError` with the same message. The output carries the same fields with the same
 *   values, in the same order, so the wiring step is a swap and existing clients see nothing.
 *   Every helper the legacy path leans on is copied here verbatim rather than imported, because
 *   `index.js` cannot be required without `firebase-admin` and this module must stay pure;
 *   `attempt-intake.test.js` proves the copies still match the originals byte for byte.
 * - **Server-scored** — the body carries neither `codeAnswer` nor `percentScore`, is `HARD`, and
 *   carries `served`. Easy questions travel with their answers and the device scores them, so an
 *   easy attempt without digits is a defect, not a request. A hard attempt without digits and
 *   without `served` has nothing the server could score against.
 *
 * Which kind a body is comes from the **values** it carries, not from which keys it happens to
 * mention. A JSON `null` is how a map-based serialiser writes "absent", and reading it as "present"
 * was worth an uncharged attempt: `HARD` with `codeAnswer: null`, `percentScore: null` and a served
 * list used to be read as device-scored with `codeAnswer: ""`, `percentScore: 0`,
 * `scoreVerified: true` — a verified, payable, empty attempt. A `null` or absent `codeAnswer` and
 * `percentScore` therefore both mean "no digits"; a body that sends a real string or a real number
 * is read exactly as today, `""` and `0` included.
 *
 * Anything in between is refused by name: a percent with no digits is not a device-scored attempt
 * missing half of itself, it is a claim with nothing behind it. A body with digits but no percent
 * key at all is refused with today's own message, because today refuses it too.
 *
 * Every body this module refuses that today's intake accepts is of that one shape — no digits, read
 * by today as `codeAnswer: ""` and so as a verified empty attempt. Today accepts it three ways: a
 * missing `codeAnswer` beside a percent, and a `null` in either key, on either difficulty. No client
 * has ever sent any of them, and letting them through would leave a hole in the classification that
 * a crafted body could sit in — an empty `HARD` attempt, `scoreVerified: true`, paid and charged.
 *
 * `served`, when present on either kind, is checked against the client's own contract
 * (`docs/architecture/0004-sync-contract.md`, `ServedQuestion.kt`): a list, each entry an object
 * with a non-negative integer `codeAnswerIndex` and a non-empty string `questionId`, sorted by
 * position, no position and no id twice, and no longer than the bounds below. The first entry that
 * breaks a rule names the rejection. `codeAnswerIndex` is taken as it stands and never clamped:
 * `normalizeLessonAnswers` turns a missing answer index into `0` with `Math.max(0, …)`, and
 * inheriting that here would let an entry with no position claim position 0 ahead of the question
 * genuinely dealt there.
 *
 * ---
 *
 * **One policy for a client that lies.** A percent that does not follow from the digits is *kept
 * and marked*: the event is stored for analysis, `scoreVerified` is false, and nothing is paid or
 * charged on it. A served list that does not follow from the digits is the same class of lie and
 * now has the same consequence — `servedVerified: false` — rather than an `invalid-argument` that
 * throws out of the whole call and loses the evidence. Two rules are checked, both of them the
 * client's own (sync-contract rules 3 and 5):
 *
 * - the served positions are exactly the positions where the digit is not `'0'`;
 * - every answer row names a `(questionId, codeAnswerIndex)` pair the served list holds.
 *
 * A device-scored attempt is payable on `scoreVerified && servedVerified`. `invalid-argument` is
 * reserved for a body that cannot be *read* — a served list that is not a list, an entry that is
 * not an object, a position that is not a number, a body past the size bounds. Nothing is inferred
 * from a shape nobody can interpret, so there is nothing to keep and mark.
 *
 * ---
 *
 * **Bounds.** `served` and `codeAnswer` are client-controlled and were unbounded. A hundred
 * thousand served entries validate, are accepted, and then fail the Firestore 1 MiB write *inside*
 * the transaction that carries up to fifty attempts — one crafted body takes the whole batch with
 * it. A position is bounded too, and for a second reason: `scoreAttempt` refuses a position outside
 * the pool it is handed, but it files that refusal as `SERVED_MALFORMED`, and only the fault
 * attached to that reason decides whether the player is charged. A position of `999`, or of `1e300`
 * — `Number.isInteger` accepts it — must never reach storage in the first place.
 *
 * ---
 *
 * The output states, as data, when the attempt may be paid for (`paymentRule`), so that the wiring
 * step reads the rule rather than inventing it: a device-scored attempt on its two verification
 * flags; a server-scored one only once `scoreAttempt` has scored it with no server-fault
 * unscorables — a missing key or an unreadable stored question is our gap, and nothing may be paid
 * or charged on it. `isPayable` evaluates that rule against an attempt and its scoring result, and
 * `withServerScore` hands the wiring step the finished attempt rather than the rule alone: a
 * server-scored attempt leaves here with `codeAnswer: null` and `percentScore: null`, and a
 * consumer that passed those on would read four "shown" questions out of the string `"null"`.
 *
 * Nothing calls this yet, and nothing here scores anything — that is `attempt-scoring.js`, and the
 * `served` this module returns is the exact shape it takes. Pure, free of `firebase-admin`, and
 * testable with plain `node`.
 */

const PUBLIC_SCOPE = "public";
const PRIVATE_SCOPE = "private";

/** Who produced the digits and the percent this attempt is judged on. */
const SCORING_AUTHORITY = {
  /** The device sent `codeAnswer` and `percentScore`; the server checks that they agree. */
  CLIENT: "client",
  /** The device sent only what it was dealt; the server scores the answers against its keys. */
  SERVER: "server",
};

/**
 * When an attempt may be paid for — the charge, the reward, the activity ratings, the tournament
 * write. Named so the wiring step compares against a constant rather than a literal.
 */
const PAYMENT_RULE = {
  /**
   * Device-scored: paid when the device's two claims about itself both hold — the percent follows
   * from the digits (`scoreVerified`) and the served list follows from them too (`servedVerified`).
   */
  DEVICE_SCORED: "device-scored",
  /**
   * Server-scored: paid only after `scoreAttempt` returned `scorable: true` and recorded no
   * unscorable whose fault is the server's. Nothing is paid, and nothing is charged, on our gap.
   */
  SERVER_SCORED: "server-scored",
};

// ---------------------------------------------------------------------------------------------
// Size bounds. Every one of these is far past anything the app builds — a lesson's eligible pool
// is tens of questions, not hundreds — and exists only so that a crafted body is refused at intake
// instead of failing a Firestore write inside a fifty-attempt transaction.
// ---------------------------------------------------------------------------------------------

/** One digit per question in the attempt's eligible pool. */
const MAX_CODE_ANSWER_CHARS = 1000;
/** One entry per question actually shown, so at most one per position of the codeAnswer. */
const MAX_SERVED_ENTRIES = MAX_CODE_ANSWER_CHARS;
/** The last position a codeAnswer of the maximum length has. */
const MAX_SERVED_POSITION = MAX_CODE_ANSWER_CHARS - 1;
/** A question id is a Firestore document id; the longest this repo writes is a dozen characters. */
const MAX_QUESTION_ID_CHARS = 200;

/**
 * The rejections this module adds to today's. Each is a function so the entries that name an offending
 * position read the same as the ones that do not.
 *
 * Today's own messages are not listed here: they are the contract existing clients already see and
 * they are spelled out at the throw site exactly as `index.js` spells them.
 *
 * Every entry is a body that cannot be read. A body that *can* be read but disagrees with itself is
 * kept and marked (`scoreVerified`, `servedVerified`), never refused.
 */
const REJECTION = {
  CODE_ANSWER_REQUIRED_WITH_PERCENT: () => "codeAnswer is required when percentScore is sent",
  CODE_ANSWER_TOO_LONG: (length) =>
    `codeAnswer names ${length} positions; at most ${MAX_CODE_ANSWER_CHARS} are accepted`,
  EASY_WITHOUT_DIGITS: () => "an EASY attempt must carry codeAnswer and percentScore",
  HARD_WITHOUT_SERVED: () => "a HARD attempt without codeAnswer must carry served",
  DIFFICULTY_NOT_A_STRING: () => "difficulty must be sent as a string",
  SERVED_NOT_A_LIST: () => "served must be a list of {questionId, codeAnswerIndex}",
  SERVED_TOO_LONG: (length) => `served has ${length} entries; at most ${MAX_SERVED_ENTRIES} are accepted`,
  SERVED_ENTRY_NOT_AN_OBJECT: (index) => `served[${index}] must be an object`,
  SERVED_POSITION_INVALID: (index) => `served[${index}].codeAnswerIndex must be a non-negative integer`,
  SERVED_POSITION_TOO_LARGE: (index, position) =>
    `served[${index}].codeAnswerIndex is ${position}; the last position accepted is ${MAX_SERVED_POSITION}`,
  SERVED_ID_INVALID: (index) => `served[${index}].questionId must be a non-empty string`,
  SERVED_ID_TOO_LONG: (index) =>
    `served[${index}].questionId is longer than ${MAX_QUESTION_ID_CHARS} characters`,
  SERVED_OUT_OF_ORDER: (index, position, previous) =>
    `served[${index}] is out of order: codeAnswerIndex ${position} after ${previous}`,
  SERVED_POSITION_REPEATED: (index, position) => `served[${index}] repeats codeAnswerIndex ${position}`,
  SERVED_ID_REPEATED: (index, firstIndex) => `served[${index}] repeats the questionId of served[${firstIndex}]`,
};

function invalid(message) {
  return new HttpsError("invalid-argument", message);
}

// ---------------------------------------------------------------------------------------------
// Verbatim copies of the `index.js` helpers the legacy path reads through. Do not edit them here:
// `attempt-intake.test.js` compares each against the original's source text.
// ---------------------------------------------------------------------------------------------

function stringValue(value, fallback = "") {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
}

function nullableString(value) {
  if (value === null || value === undefined) return null;
  const text = String(value);
  return text.length > 0 ? text : null;
}

function numberValue(value, fallback) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value && typeof value.toNumber === "function") return value.toNumber();
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function listMaps(value) {
  return Array.isArray(value)
    ? value.filter((item) => item && typeof item === "object")
    : [];
}

function nonNegativeEventTime(value) {
  return Math.max(0, numberValue(value, Date.now()));
}

function normalizeScope(value) {
  return stringValue(value, PUBLIC_SCOPE) === PRIVATE_SCOPE ? PRIVATE_SCOPE : PUBLIC_SCOPE;
}

function normalizeContentEvent(data, authUid) {
  const scope = normalizeScope(data.scope);
  const ownerUid = nullableString(data.ownerUid);
  if (scope === PRIVATE_SCOPE && ownerUid !== authUid) {
    throw new HttpsError("permission-denied", "Private event ownerUid must match authenticated uid");
  }
  const catalogId = stringValue(data.catalogId);
  const questId = stringValue(data.questId);
  const sectionId = stringValue(data.sectionId);
  const themeId = stringValue(data.themeId);
  const lessonId = stringValue(data.lessonId);
  if (!catalogId || !questId || !sectionId || !themeId || !lessonId) {
    throw new HttpsError("invalid-argument", "catalogId, questId, sectionId, themeId, and lessonId are required");
  }
  return {
    userId: authUid,
    scope,
    ownerUid: scope === PRIVATE_SCOPE ? ownerUid : null,
    catalogId,
    questId,
    sectionId,
    themeId,
    lessonId,
    lessonVersion: Math.max(1, numberValue(data.lessonVersion, 1)),
    sourceShelf: stringValue(data.sourceShelf, scope === PRIVATE_SCOPE ? PRIVATE_SCOPE : "arena"),
  };
}

function normalizeLessonAnswers(value) {
  return listMaps(value)
    .map((item) => ({
      questionId: stringValue(item.questionId),
      codeAnswerIndex: Math.max(0, numberValue(item.codeAnswerIndex, 0)),
      score: Math.max(0, Math.min(9, numberValue(item.score, 0))),
      answerPayload: stringValue(item.answerPayload),
      answeredAtMs: nonNegativeEventTime(item.answeredAtMs),
      durationMs: Math.max(0, numberValue(item.durationMs, 0)),
      wasTimeout: Boolean(item.wasTimeout),
    }))
    .filter((item) => item.questionId !== "");
}

// ---------------------------------------------------------------------------------------------
// End of verbatim copies.
// ---------------------------------------------------------------------------------------------

/** `difficulty` as today reads it: absent is EASY, case is forgiven, anything else is refused. */
function readDifficulty(data) {
  const difficulty = stringValue(data.difficulty, "EASY").toUpperCase();
  if (difficulty !== "EASY" && difficulty !== "HARD") {
    throw new HttpsError("invalid-argument", "difficulty must be EASY or HARD");
  }
  return difficulty;
}

/**
 * The served list, checked against the client's contract and reduced to its two fields.
 *
 * Only shapes are judged here — everything that leaves the list uninterpretable. Whether an
 * interpretable list agrees with the digits is a different question with a different answer
 * (`servedVerified`), and it is asked afterwards.
 *
 * The walk is by index rather than `forEach`: `forEach` skips the holes of a sparse array, so
 * `[<hole>, {...}]` would reach the second entry with `index === 1` having pushed nothing, and the
 * order check would read `codeAnswerIndex` off `undefined` — a TypeError surfacing to the client as
 * an internal error instead of a named rejection.
 *
 * @returns `null` when the body carries no list (absent means unknown — a client from before
 *   E2.10, or a migrated row), otherwise `[{codeAnswerIndex, questionId}]` in the order sent, which
 *   the checks below guarantee is position order. An empty list is legal: nothing was shown.
 */
function validateServed(value) {
  if (value === undefined || value === null) return null;
  if (!Array.isArray(value)) throw invalid(REJECTION.SERVED_NOT_A_LIST());
  if (value.length > MAX_SERVED_ENTRIES) throw invalid(REJECTION.SERVED_TOO_LONG(value.length));
  const served = [];
  const firstIndexOfId = new Map();
  let previous = -1;
  for (let index = 0; index < value.length; index += 1) {
    const entry = value[index];
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      throw invalid(REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(index));
    }
    const position = entry.codeAnswerIndex;
    if (!Number.isInteger(position) || position < 0) throw invalid(REJECTION.SERVED_POSITION_INVALID(index));
    if (position > MAX_SERVED_POSITION) throw invalid(REJECTION.SERVED_POSITION_TOO_LARGE(index, position));
    const questionId = entry.questionId;
    if (typeof questionId !== "string" || questionId === "") throw invalid(REJECTION.SERVED_ID_INVALID(index));
    if (questionId.length > MAX_QUESTION_ID_CHARS) throw invalid(REJECTION.SERVED_ID_TOO_LONG(index));
    if (index > 0) {
      if (position === previous) throw invalid(REJECTION.SERVED_POSITION_REPEATED(index, position));
      if (position < previous) throw invalid(REJECTION.SERVED_OUT_OF_ORDER(index, position, previous));
    }
    if (firstIndexOfId.has(questionId)) {
      throw invalid(REJECTION.SERVED_ID_REPEATED(index, firstIndexOfId.get(questionId)));
    }
    firstIndexOfId.set(questionId, index);
    served.push({codeAnswerIndex: position, questionId});
    previous = position;
  }
  return served;
}

/**
 * The client's own invariant, on the device-scored path: the served positions are exactly the ones
 * where the digit is not `'0'` (sync-contract rule 5). Positions are unique by the time this runs,
 * so equal counts plus containment is exact equality.
 */
function servedMatchesDigits(served, codeAnswer) {
  const shown = new Set();
  for (let position = 0; position < codeAnswer.length; position += 1) {
    if (codeAnswer[position] !== "0") shown.add(position);
  }
  if (served.length !== shown.size) return false;
  return served.every((entry) => shown.has(entry.codeAnswerIndex));
}

/**
 * The client's other invariant (sync-contract rule 3), enforced nowhere until now on this path:
 * every answer row names a `(questionId, codeAnswerIndex)` pair the served list holds. An answer to
 * a question that was never dealt, or at a position other than the one it was dealt at, is the same
 * class of disagreement as a crafted percent.
 *
 * `answers` is what `normalizeLessonAnswers` left behind, so rows with no question id are already
 * gone and every index has been clamped to zero or above.
 */
function everyAnswerIsServed(answers, served) {
  const positionOfId = new Map(served.map((entry) => [entry.questionId, entry.codeAnswerIndex]));
  return answers.every((answer) => positionOfId.get(answer.questionId) === answer.codeAnswerIndex);
}

/**
 * The device-scored kind: today's path, check for check and message for message, with `served`
 * looked at only after everything today looks at, so today's rejections keep their precedence.
 *
 * @param carriesDigits whether `codeAnswer` held a value — decided by the caller, on the value and
 *   not on the key, so that a `null` written by a map-based serialiser is the absence it means.
 */
function readDeviceScoredAttempt(data, event, attemptId, carriesDigits) {
  const percentScore = numberValue(data.percentScore, null);
  if (percentScore === null || percentScore < 0 || percentScore > 100) {
    throw new HttpsError("invalid-argument", "percentScore must be in 0..100");
  }
  if (!carriesDigits) throw invalid(REJECTION.CODE_ANSWER_REQUIRED_WITH_PERCENT());
  const codeAnswer = stringValue(data.codeAnswer);
  if (!isWellFormedCodeAnswer(codeAnswer)) {
    throw new HttpsError("invalid-argument", "codeAnswer must contain digits only");
  }
  if (codeAnswer.length > MAX_CODE_ANSWER_CHARS) {
    throw invalid(REJECTION.CODE_ANSWER_TOO_LONG(codeAnswer.length));
  }
  const difficulty = readDifficulty(data);
  // The client always derives percentScore from codeAnswer (CompleteAttemptUseCase and
  // AbortAttemptUseCase are the only two paths), so an honest attempt always matches.
  // A mismatch means the payload was crafted: keep the event for analysis, pay nothing.
  const expectedPercentScore = recomputePercentScore(codeAnswer);
  const answers = normalizeLessonAnswers(data.answers);
  const served = validateServed(data.served);
  // The same treatment for the other claim the device makes about itself. No served list is not a
  // disagreement — it is a client from before E2.10, and it has nothing to disagree with.
  const servedVerified = served === null
    ? true
    : servedMatchesDigits(served, codeAnswer) && everyAnswerIsServed(answers, served);
  return {
    answers,
    ...event,
    attemptId,
    difficulty,
    codeAnswer,
    percentScore,
    expectedPercentScore,
    scoreVerified: expectedPercentScore === percentScore,
    completedAtMs: nonNegativeEventTime(data.completedAtMs),
    createdAtMs: nonNegativeEventTime(data.createdAtMs),
    scoringAuthority: SCORING_AUTHORITY.CLIENT,
    served,
    servedVerified,
    paymentRule: PAYMENT_RULE.DEVICE_SCORED,
  };
}

/**
 * The server-scored kind: hard, no digits, a served list. `codeAnswer` and `percentScore` are
 * `null` — not unknown, not yet — until the wiring step has `scoreAttempt` fill them in (see
 * `withServerScore`), and there is no `scoreVerified` and no `servedVerified` at all, because the
 * device made no claim for either to be checked against.
 *
 * `difficulty` must be a string here. Today's `stringValue` reads `["hard"]` as `"hard"`, and that
 * reading stays untouched on the device-scored path — it is what existing clients are judged by —
 * but this path is new and nothing has ever sent it a non-string.
 */
function readServerScoredAttempt(data, event, attemptId) {
  const sentDifficulty = data.difficulty;
  if (sentDifficulty !== undefined && sentDifficulty !== null && typeof sentDifficulty !== "string") {
    throw invalid(REJECTION.DIFFICULTY_NOT_A_STRING());
  }
  const difficulty = readDifficulty(data);
  if (difficulty !== "HARD") throw invalid(REJECTION.EASY_WITHOUT_DIGITS());
  if (data.served === undefined || data.served === null) throw invalid(REJECTION.HARD_WITHOUT_SERVED());
  const served = validateServed(data.served);
  const answers = normalizeLessonAnswers(data.answers);
  return {
    answers,
    ...event,
    attemptId,
    difficulty,
    codeAnswer: null,
    percentScore: null,
    completedAtMs: nonNegativeEventTime(data.completedAtMs),
    createdAtMs: nonNegativeEventTime(data.createdAtMs),
    scoringAuthority: SCORING_AUTHORITY.SERVER,
    served,
    paymentRule: PAYMENT_RULE.SERVER_SCORED,
  };
}

/**
 * Reads a submitted attempt and classifies it.
 *
 * @param data the callable's body for one attempt, as `applyLessonResultEvents` hands it over
 * @param authUid the authenticated caller
 * @returns the normalised attempt — for the device-scored kind, field for field what
 *   `normalizeLessonResultAttemptEvent` returns today — plus `scoringAuthority`, `served`
 *   (validated, or `null` when the body carried none), `servedVerified` on the device-scored kind,
 *   and `paymentRule`.
 * @throws HttpsError with today's code and message for everything today refuses, and with a
 *   `REJECTION` message for every body this module cannot read.
 */
function readSubmittedAttempt(data, authUid) {
  const userId = stringValue(data.userId, authUid);
  if (userId !== authUid) {
    throw new HttpsError("permission-denied", "Attempt userId must match authenticated uid");
  }
  const event = normalizeContentEvent(data, authUid);
  const attemptId = stringValue(data.attemptId);
  if (!attemptId) throw new HttpsError("invalid-argument", "attemptId is required");

  // On the value, not on the key: `null` is how "absent" is serialised by anything map-based, and
  // reading it as a present `""`/`0` made an empty hard attempt look verified and payable.
  const claimsPercent = data.percentScore !== undefined && data.percentScore !== null;
  const carriesDigits = data.codeAnswer !== undefined && data.codeAnswer !== null;
  if (claimsPercent || carriesDigits) {
    return readDeviceScoredAttempt(data, event, attemptId, carriesDigits);
  }
  return readServerScoredAttempt(data, event, attemptId);
}

/**
 * Whether an attempt may be paid for, by its own `paymentRule`.
 *
 * Fails closed at every step: no attempt, no rule it recognises, no scoring result, a scoring
 * result whose `unscorable` is not a list — all of them false. A malformed result is exactly the
 * case where "there were no server faults in it" cannot be established, and treating an
 * uninspectable list as an empty one would pay on it.
 *
 * @param attempt what `readSubmittedAttempt` returned
 * @param scoring what `scoreAttempt` returned for it; read only for the server-scored kind
 */
function isPayable(attempt, scoring) {
  if (!attempt || typeof attempt !== "object") return false;
  if (attempt.paymentRule === PAYMENT_RULE.DEVICE_SCORED) {
    return attempt.scoreVerified === true && attempt.servedVerified === true;
  }
  if (attempt.paymentRule === PAYMENT_RULE.SERVER_SCORED) {
    if (!scoring || typeof scoring !== "object" || scoring.scorable !== true) return false;
    if (!Array.isArray(scoring.unscorable)) return false;
    return !scoring.unscorable.some((record) => record && record.fault === FAULT.SERVER);
  }
  return false;
}

/**
 * The attempt as the wiring step should store it: the scorer's digits and percent filled in, and
 * the payment rule already evaluated into `payable`.
 *
 * A server-scored attempt leaves `readSubmittedAttempt` with `codeAnswer: null` and
 * `percentScore: null`, and today's consumers would take those at face value —
 * `attemptActivityCounts(null)` reads four "shown" questions out of the string `"null"`, and
 * `numberValue(null, 0)` reads a percent of `0` while looking like a deliberate default. So the
 * two fields are filled here, once, from the one source allowed to fill them.
 *
 * An attempt the server could not score keeps no number in either direction: `""` and `0`, which
 * are what an attempt with nothing shown has always looked like, and `payable: false` beside them.
 * A device-scored attempt passes through with its own digits untouched and only `payable` added.
 */
function withServerScore(attempt, scoring) {
  if (!attempt || typeof attempt !== "object") return null;
  const payable = isPayable(attempt, scoring);
  if (attempt.paymentRule !== PAYMENT_RULE.SERVER_SCORED) return {...attempt, payable};
  // Both numbers or neither. A result that is scorable but carries a percent that is not a number
  // is not half usable — it is a result nobody can account for, and taking the digits out of it
  // while defaulting the percent would store a codeAnswer and a percent that disagree.
  const usable = Boolean(scoring) && typeof scoring === "object" && scoring.scorable === true &&
    typeof scoring.codeAnswer === "string" &&
    typeof scoring.percentScore === "number" && Number.isFinite(scoring.percentScore);
  return {
    ...attempt,
    codeAnswer: usable ? scoring.codeAnswer : "",
    percentScore: usable ? scoring.percentScore : 0,
    payable,
  };
}

module.exports = {
  SCORING_AUTHORITY,
  PAYMENT_RULE,
  REJECTION,
  MAX_CODE_ANSWER_CHARS,
  MAX_SERVED_ENTRIES,
  MAX_SERVED_POSITION,
  MAX_QUESTION_ID_CHARS,
  readSubmittedAttempt,
  isPayable,
  withServerScore,
};
