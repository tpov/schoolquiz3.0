"use strict";

/**
 * The server-side scorer: a literal mirror of Scoring.kt
 * (shared/core/scoring/src/commonMain/.../Scoring.kt).
 *
 * Until now nothing on the server has ever scored an answer — every "correct" in Firestore was
 * whatever the device claimed, checked only for internal consistency (result-verification.js).
 * Taking the answer key off the device makes a server scorer mandatory, and a second
 * implementation of a formula is a second set of rounding decisions unless it is written as a
 * transcription rather than a re-derivation.
 *
 * Kotlin's `Int` division truncates toward zero, so every division here goes through Math.trunc.
 * Math.floor would agree only while both operands stay non-negative, and this module is handed
 * whatever the client posted. The nested `denominator / 2` inside scoreDigit is truncated for the
 * same reason and no other: Scoring.kt:94 says so. It is not load-bearing — for an odd denominator
 * the half it drops can never push the outer division across an integer, so removing it changes no
 * result — but a mirror that keeps only the divisions it has judged to matter has started
 * re-deriving, and re-deriving is the thing this file exists to avoid.
 *
 * The two implementations are held together by
 * shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json, which both test suites read.
 *
 * Scores here are plain numbers, not the Kotlin value classes: a score digit is 1..9, a percent is
 * 0..100, star tenths are 0..30. Kotlin gets those bounds for free from CodeAnswer, PercentScore
 * and Stars, whose `require` blocks run before the formula ever does. JavaScript has no such
 * wrapper, so the few bounds that keep a crafted payload from producing a nonsense number are
 * asserted here directly — see computePercentScore and computeStars.
 *
 * Nothing calls this module yet. It is pure by design and must stay free of firebase-admin.
 */

/**
 * QuestionContent discriminators (PascalCase) and UserAnswer discriminators (kebab-case) share the
 * key "type" but are two different namespaces. Reusing one set scores everything 1, silently, so
 * both are named here rather than spelled out at each comparison.
 */
const CONTENT_TYPE = {
  SINGLE_CHOICE: "SingleChoice",
  MULTIPLE_CHOICE: "MultipleChoice",
  ORDERING: "Ordering",
  FILL_BLANK: "FillBlank",
  SURVEY: "Survey",
};

const ANSWER_TYPE = {
  SINGLE_CHOICE: "single-choice",
  MULTIPLE_CHOICE: "multiple-choice",
  ORDERING: "ordering",
  FILL_BLANK: "fill-blank",
  SURVEY: "survey",
};

const MIN_SCORE = 1;
const MAX_SCORE = 9;
const MIN_PERCENT = 0;
const MAX_PERCENT = 100;

const DIGITS_ONLY = /^[0-9]*$/;

/**
 * Ids serialize as bare strings, so a list of {id, text} rows collapses straight into a Set.
 * A row carrying no string id is dropped rather than contributing `undefined` to the valid set,
 * where it could be matched by an answer that omitted its own id.
 */
function idSet(rows) {
  const ids = new Set();
  for (const row of toArray(rows)) {
    if (row && typeof row.id === "string") ids.add(row.id);
  }
  return ids;
}

function toArray(value) {
  return Array.isArray(value) ? value : [];
}

/** Kotlin's Set<OptionId> arrives as a JSON array; duplicates in it collapse the same way. */
function toSet(value) {
  return new Set(toArray(value));
}

/** Counts matches over anything iterable — Sets included, which arrays' own .filter is not. */
function countWhere(values, predicate) {
  let count = 0;
  let index = 0;
  for (const value of values) {
    if (predicate(value, index)) count += 1;
    index += 1;
  }
  return count;
}

/**
 * Mirror of scoreDigit in Scoring.kt:92-96 — integer round-half-up.
 *
 * digit = (numerator * 8 + denominator / 2) / denominator + 1, coerced into 1..9.
 *
 * Both divisions truncate in Kotlin. The outer one is the one that decides the digit; the inner
 * `denominator / 2` is transcribed rather than simplified, and dropping it would change no result
 * for any pair this can be called with. It stays because Scoring.kt has it.
 */
function scoreDigit(numerator, denominator) {
  if (denominator === 0) return MIN_SCORE;
  const digit = Math.trunc((numerator * 8 + Math.trunc(denominator / 2)) / denominator) + 1;
  return Math.min(MAX_SCORE, Math.max(MIN_SCORE, digit));
}

/** Mirror of the SingleChoice branch, Scoring.kt:20-24. */
function scoreSingleChoice(content, answer) {
  // Kotlin's correctOptionId is non-nullable, so it cannot be absent there. Here it can: E2 redacts
  // a published question by stripping the answer key, and a redacted payload reaching this function
  // would otherwise compare null-to-null and hand out full marks for an unanswered question.
  const correctOptionId = content.correctOptionId;
  if (correctOptionId === null || correctOptionId === undefined) return MIN_SCORE;

  const validIds = idSet(content.options);
  // An id the question does not offer is nulled before comparing, so it can never match.
  const selected = validIds.has(answer.selected) ? answer.selected : null;
  return selected === correctOptionId ? MAX_SCORE : MIN_SCORE;
}

/** Mirror of the MultipleChoice branch, Scoring.kt:25-32. */
function scoreMultipleChoice(content, answer) {
  const validIds = idSet(content.options);
  const correctIds = toSet(content.correctOptionIds);
  const picked = new Set([...toSet(answer.selected)].filter((id) => validIds.has(id)));

  const correctPicked = countWhere(picked, (id) => correctIds.has(id));
  const wrongPicked = countWhere(picked, (id) => !correctIds.has(id));
  const missed = countWhere(correctIds, (id) => !picked.has(id));

  // The denominator is the union of what was picked and what was correct, so a wrong pick costs
  // the same as a missed one.
  return scoreDigit(correctPicked, correctPicked + wrongPicked + missed);
}

/** Mirror of the Ordering branch, Scoring.kt:33-43. */
function scoreOrdering(content, answer) {
  const correctOrder = toArray(content.items).map((item) => item && item.id);
  const order = toArray(answer.order);

  // Kotlin compares the two as sets after comparing list lengths. Both terms carry weight: the
  // length term is the only thing rejecting an order that repeats an id to run long, since the set
  // of such an order still matches. QuestionContent.Ordering has no uniqueness invariant on item
  // ids, so a question whose items share an id has a set smaller than its list — and an answer
  // repeating that id still passes as a permutation. Mirrored, not fixed.
  const answerIds = new Set(order);
  const correctIds = new Set(correctOrder);
  const isValidPerm = order.length === correctOrder.length &&
    answerIds.size === correctIds.size &&
    [...answerIds].every((id) => correctIds.has(id));
  if (!isValidPerm) return MIN_SCORE;

  const matched = countWhere(correctOrder, (id, index) => order[index] === id);
  return scoreDigit(matched, correctOrder.length);
}

/** Mirror of the FillBlank branch, Scoring.kt:44-51. */
function scoreFillBlank(content, answer) {
  const validCandidates = idSet(content.candidates);
  // A Map rather than raw property access: a blank id such as "constructor" must read as absent,
  // the way a Kotlin Map lookup does.
  const filled = new Map(Object.entries(answer.filled || {}));
  const blanks = toArray(content.blanks);

  const filledCorrect = countWhere(blanks, (blank) => {
    // A malformed row is never credited, but it still counts toward the denominator below, which
    // is `blanks.size` in Kotlin. That keeps a crafted payload scoring low rather than high.
    if (!blank || typeof blank !== "object") return false;
    const candidate = filled.has(blank.id) ? filled.get(blank.id) : null;
    return candidate !== null && candidate !== undefined &&
      validCandidates.has(candidate) &&
      candidate === blank.correctCandidateId;
  });

  // Keys for blanks the question does not have are never looked at: the denominator is blanks.size.
  return scoreDigit(filledCorrect, blanks.length);
}

/** Mirror of the Survey branch, Scoring.kt:52-58. */
function scoreSurvey(content, answer) {
  const validIds = idSet(content.options);
  // A survey has no right answer and is scored on participation alone. allowMultiple is not read
  // here — Kotlin ignores it too, so picking several options in a single-pick survey still scores
  // full marks. Mirrored, not fixed.
  const selected = toArray(answer.selected);
  return selected.some((id) => validIds.has(id)) ? MAX_SCORE : MIN_SCORE;
}

/**
 * Mirror of evaluateAnswer in Scoring.kt:18-61. Returns a score digit in 1..9.
 *
 * Both the content type and the answer type must match; anything else — a mismatched pair, an
 * unknown discriminator, a missing object — falls through to 1, exactly as Kotlin's `else` branch
 * does. That is deliberately not an error: the client can post an answer shape that no longer fits
 * the question, and the attempt is still scored. A malformed row inside an otherwise well-formed
 * question follows the same rule and scores low rather than throwing.
 */
function evaluateAnswer(content, answer) {
  const contentType = content && content.type;
  const answerType = answer && answer.type;

  if (contentType === CONTENT_TYPE.SINGLE_CHOICE && answerType === ANSWER_TYPE.SINGLE_CHOICE) {
    return scoreSingleChoice(content, answer);
  }
  if (contentType === CONTENT_TYPE.MULTIPLE_CHOICE && answerType === ANSWER_TYPE.MULTIPLE_CHOICE) {
    return scoreMultipleChoice(content, answer);
  }
  if (contentType === CONTENT_TYPE.ORDERING && answerType === ANSWER_TYPE.ORDERING) {
    return scoreOrdering(content, answer);
  }
  if (contentType === CONTENT_TYPE.FILL_BLANK && answerType === ANSWER_TYPE.FILL_BLANK) {
    return scoreFillBlank(content, answer);
  }
  if (contentType === CONTENT_TYPE.SURVEY && answerType === ANSWER_TYPE.SURVEY) {
    return scoreSurvey(content, answer);
  }
  return MIN_SCORE;
}

/**
 * Mirror of computePercentScore in Scoring.kt:81-86. Returns a percent in 0..100.
 *
 * '0' means the question was never put to the player, so those positions are dropped rather than
 * averaged in as zeros. Note the per-digit formula multiplies before dividing and carries no
 * rounding constant — unlike scoreDigit — so digit '2' is 12 and not 13.
 *
 * Non-digit input throws. CodeAnswer.kt:12 requires every char in '0'..'9' and digitToInt() throws
 * on anything else, so Kotlin can never reach the formula with junk; here it would quietly produce
 * NaN, which survives every arithmetic step after it and lands in Firestore as null. An empty
 * string stays legal and scores 0 — Kotlin rejects that one at the wrapper instead, which is the
 * one place the two genuinely differ.
 *
 * recomputePercentScore in result-verification.js is the same arithmetic; this copy exists so the
 * new scorer stands alone and the verifier keeps its own contract.
 */
function computePercentScore(codeAnswer) {
  const raw = String(codeAnswer);
  if (!DIGITS_ONLY.test(raw)) {
    throw new Error(`codeAnswer must contain digits only, got ${JSON.stringify(raw)}`);
  }
  const nonZero = raw.split("").filter((char) => char !== "0");
  if (nonZero.length === 0) return 0;
  const sum = nonZero.reduce((acc, char) => acc + Math.trunc(((Number(char) - 1) * 100) / 8), 0);
  return Math.trunc(sum / nonZero.length);
}

/**
 * Mirror of computeStars in Scoring.kt:69-75. Returns star tenths; the UI renders tenths / 10.
 *
 * EASY spans 0..20 tenths, HARD starts at 20 and spans 20..30 — a hard run is worth at least two
 * stars however it goes. The +50 before the /100 is the round-half-up constant.
 *
 * Both arguments are bounded here because Kotlin bounds them one layer up and this function is the
 * whole layer: PercentScore rejects anything outside 0..100 before computeStars is called, and
 * Difficulty is a two-value enum so a third value cannot exist. Unbounded, a percent of 1000 would
 * return 200 tenths from a function documented to top out at 30, and an unrecognised mode would
 * quietly pay EASY tenths for a run that claimed to be something else.
 */
function computeStars(percentScore, mode) {
  if (!Number.isInteger(percentScore) || percentScore < MIN_PERCENT || percentScore > MAX_PERCENT) {
    throw new Error(`percentScore out of range: expected an integer in 0..100, got ${percentScore}`);
  }
  if (mode === "EASY") return Math.trunc((percentScore * 20 + 50) / 100);
  if (mode === "HARD") return 20 + Math.trunc((percentScore * 10 + 50) / 100);
  throw new Error(`Unknown difficulty: ${mode}`);
}

module.exports = {
  evaluateAnswer,
  computePercentScore,
  computeStars,
  scoreDigit,
};
