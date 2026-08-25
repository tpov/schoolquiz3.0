"use strict";

/**
 * Pure verification helpers for submitted lesson results.
 *
 * The game is offline-first: the client plays without a connection and uploads attempts on sync.
 * The server cannot replay the session, but it can check that the numbers add up — the attempt
 * must be self-consistent. Keeping this logic free of firebase-admin makes it testable.
 */

/**
 * Mirror of computePercentScore in RunnerLogic.kt
 * (shared/feature/lesson-runner/domain/.../logic/RunnerLogic.kt).
 *
 * codeAnswer digits: '0' = the question was not shown, '1'..'9' = score for that answer.
 * Kotlin does Int division at both steps, so both divisions floor here as well — otherwise
 * honest attempts would be rejected over rounding (e.g. digit '2' is 12, not 12.5).
 */
function recomputePercentScore(codeAnswer) {
  const shown = String(codeAnswer)
    .split("")
    .filter((char) => char !== "0");
  if (shown.length === 0) return 0;
  const sum = shown.reduce((acc, char) => acc + Math.floor(((Number(char) - 1) * 100) / 8), 0);
  return Math.floor(sum / shown.length);
}

/** codeAnswer must be digits only; anything else means a crafted or corrupted payload. */
function isWellFormedCodeAnswer(codeAnswer) {
  return /^[0-9]*$/.test(String(codeAnswer));
}

/**
 * What one attempt adds to the activity ratings.
 *
 * Read off the same digits the score is: anything but '0' is a question that was actually put to
 * the player, and '9' is the only digit that means they got it fully right. Counting them here
 * rather than trusting a client-sent tally keeps the ratings as hard to forge as the score.
 */
function attemptActivityCounts(codeAnswer) {
  const digits = String(codeAnswer).split("");
  const shown = digits.filter((char) => char !== "0");
  return {
    questions: shown.length,
    correct: shown.filter((char) => char === "9").length,
  };
}

module.exports = {
  recomputePercentScore,
  isWellFormedCodeAnswer,
  attemptActivityCounts,
};
