"use strict";

/**
 * What an attempt pays.
 *
 * Three things decide it: how much the lesson was worth, how well it went, and how much of that
 * was new. Nothing else — no per-attempt flat fee, so a long lesson is not worth the same as a
 * short one, and no reward for showing up.
 *
 * **Worth** is the lesson's allocated time, the same quantity the timer is built from: characters
 * times the difficulty coefficient. It is the one measure of size the app already trusts, and it
 * cannot be inflated by splitting a lesson into more questions.
 *
 * **How well** runs through marginal tariff bands, the way tax brackets do. Points near the top of
 * the range are worth several times what points near the bottom are, so the difference between 90
 * and 100 is worth chasing and the difference between 5 and 15 is not much of an achievement.
 *
 * **How much was new** is the anti-grind rule. Percent taken for the first time pays double;
 * percent already earned on an earlier attempt pays a tenth. Replaying a lesson you know is not
 * forbidden and not worthless — it is simply not a way to earn.
 *
 * Pure, so it can be tested without firebase-admin.
 */

/**
 * Marginal tariff bands: [from, to, multiplier].
 *
 * Kept as bands rather than fitted to a curve on purpose. The closest simple continuous fit
 * (p + 1.65p²/100) is accurate at the top and about 40% wrong around 25%, which is exactly where
 * payments are small and a single nolic is visible.
 */
const TARIFF_BANDS = [
  [0, 25, 1],
  [25, 50, 2],
  [50, 70, 3],
  [70, 90, 4],
  [90, 100, 5],
];

/** Percent taken for the first time. */
const NEW_PERCENT_MULTIPLIER = 2;
/** Percent already earned on an earlier attempt, paid again on a repeat run. */
const REPEAT_MULTIPLIER = 0.1;
/** Hard questions are worth three times easy ones at the same allocated time. */
const HARD_TARIFF = 3;

/**
 * The reference lesson the whole scale is anchored to: twenty questions of about a hundred
 * characters, played on easy. Ten percent in the 1x band on a lesson this size pays one nolic.
 */
const SMALL_LESSON_SECONDS = 720;
/** Tariff points per nolic, from that anchor: 10 points (10% at 1x) buy one nolic. */
const POINTS_PER_NOLIC = 10;

/** Tariff points for a score, summed across the bands it spans. */
function weightedPercent(percent) {
  const p = Math.max(0, Math.min(Number(percent) || 0, 100));
  let total = 0;
  for (const [from, to, multiplier] of TARIFF_BANDS) {
    total += Math.max(0, Math.min(p, to) - from) * multiplier;
  }
  return total;
}

/** How this lesson compares to the reference one. A lesson twice as long is worth twice as much. */
function sizeFactor(allocatedSeconds) {
  const seconds = Math.max(0, Number(allocatedSeconds) || 0);
  return seconds / SMALL_LESSON_SECONDS;
}

/**
 * Tariff points an attempt earns, before size and difficulty are applied.
 *
 * Percent above the previous best is new and pays double; everything at or below it is a repeat
 * and pays a tenth. A first attempt has no previous best, so all of it is new.
 */
function attemptPoints(previousBestPercent, percent) {
  const best = Math.max(0, Math.min(Number(previousBestPercent) || 0, 100));
  const now = Math.max(0, Math.min(Number(percent) || 0, 100));
  const repeated = weightedPercent(Math.min(best, now));
  const gained = Math.max(0, weightedPercent(now) - weightedPercent(best));
  return gained * NEW_PERCENT_MULTIPLIER + repeated * REPEAT_MULTIPLIER;
}

/**
 * What one attempt pays.
 *
 * @param previousBestPercent the player's best on this lesson at this difficulty, 0 if never played
 * @param percent this attempt's score
 * @param isHard whether the attempt was played on hard
 * @param allocatedSeconds the lesson's allocated time at this difficulty
 * @return {{nolics: number, skillPoints: number}} both whole numbers; the minimum unit is 1 nolic,
 *   so a payment under half a nolic rounds to none.
 */
function attemptReward({previousBestPercent, percent, isHard, allocatedSeconds}) {
  const points = attemptPoints(previousBestPercent, percent) *
    sizeFactor(allocatedSeconds) *
    (isHard ? HARD_TARIFF : 1);
  return {
    nolics: Math.round(points / POINTS_PER_NOLIC),
    skillPoints: Math.round(points),
  };
}

/**
 * What it costs to open one lesson without earning it.
 *
 * Anchored so that one lesson passed perfectly on the first try, both difficulties, funds exactly
 * one unlock of another lesson of the same size. Because price and reward are computed from the
 * same allocated time, the scale cancels: only the ratio matters, and it is fixed by that anchor.
 */
function lessonUnlockPrice({easyAllocatedSeconds, hardAllocatedSeconds}) {
  const perfect = weightedPercent(100) * NEW_PERCENT_MULTIPLIER;
  const easy = perfect * sizeFactor(easyAllocatedSeconds);
  const hard = perfect * sizeFactor(hardAllocatedSeconds) * HARD_TARIFF;
  // At least one nolic: a door that opens for nothing is not a door.
  return Math.max(1, Math.round((easy + hard) / POINTS_PER_NOLIC));
}

/**
 * Per-question allocated time, mirroring computeTimer in RunnerLogic.kt.
 *
 * seconds = max(5, round(charsCount x k)), where charsCount counts the question text plus every
 * option, item or candidate text, and an image is worth a flat hundred characters. The two
 * coefficients are the runner's own: easy reads slower per character than hard, because a hard
 * question is meant to be thought about rather than read.
 *
 * Kept beside the reward because the reward is the only server-side reader of it. It must be
 * changed in the same commit as the Kotlin one.
 */
const TIMER_K_EASY = 0.36;
const TIMER_K_HARD = 0.24;
const IMAGE_CHARS = 100;
const MIN_QUESTION_SECONDS = 5;

/** Characters a question is worth, across every content shape. */
function questionCharsCount(content) {
  if (!content || typeof content !== "object") return 0;
  const text = String(content.text || "").length;
  const image = content.imageUrl ? IMAGE_CHARS : 0;
  const lists = [content.options, content.items, content.candidates];
  let parts = 0;
  for (const list of lists) {
    if (!Array.isArray(list)) continue;
    for (const entry of list) parts += String((entry && entry.text) || "").length;
  }
  return text + image + parts;
}

/** Allocated seconds for one question at one difficulty. */
function questionAllocatedSeconds(content, isHard) {
  const k = isHard ? TIMER_K_HARD : TIMER_K_EASY;
  return Math.max(MIN_QUESTION_SECONDS, Math.round(questionCharsCount(content) * k));
}

/**
 * Allocated seconds for a whole lesson at one difficulty.
 *
 * Only questions of that difficulty count: an easy run never shows a hard question, so a lesson
 * heavy on hard questions is not worth more on easy for having them.
 */
function lessonAllocatedSeconds(contents, isHard) {
  if (!Array.isArray(contents)) return 0;
  const wanted = isHard ? "HARD" : "EASY";
  return contents
    .filter((content) => String((content && content.difficulty) || "EASY").toUpperCase() === wanted)
    .reduce((total, content) => total + questionAllocatedSeconds(content, isHard), 0);
}

module.exports = {
  TIMER_K_EASY,
  TIMER_K_HARD,
  questionCharsCount,
  questionAllocatedSeconds,
  lessonAllocatedSeconds,
  TARIFF_BANDS,
  NEW_PERCENT_MULTIPLIER,
  REPEAT_MULTIPLIER,
  HARD_TARIFF,
  SMALL_LESSON_SECONDS,
  POINTS_PER_NOLIC,
  weightedPercent,
  sizeFactor,
  attemptPoints,
  attemptReward,
  lessonUnlockPrice,
};
