"use strict";

/**
 * The pre-swap intake's `normalizeLessonAnswers`, and the four helpers it reads through.
 *
 * `attempt-intake.js` copies eight `index.js` helpers verbatim rather than importing them, because
 * `index.js` cannot be required without `firebase-admin` and the intake must stay pure.
 * `attempt-intake.test.js` pins each copy to its original by comparing source text, and seven of
 * the eight are still live in `index.js`, so the pin is a pin to running code.
 *
 * `normalizeLessonAnswers` is the eighth and is no longer one of them: once
 * `applyLessonResultEvents` started reading bodies through `readSubmittedAttempt`, nothing in
 * `index.js` called it. Keeping a dead declaration there purely so a test could point at it makes
 * the pin a pin to nothing and invites someone to delete it as unused — taking the pin with it. So
 * the canonical text lives here instead, in a fixture named for what it is.
 *
 * **Do not edit.** Two things hold this file honest, both in `attempt-intake.test.js`: the four
 * helpers below are still compared byte for byte against `index.js`, where they are live, so this
 * file cannot drift from the server; and `normalizeLessonAnswers` here is compared byte for byte
 * against `attempt-intake.js`'s copy, which is the one that actually runs. Changing the shape of a
 * submitted answer means changing `attempt-intake.js` and then refreshing this file to match — in
 * that order, deliberately, with the fixtures re-checked.
 *
 * Everything here is pure and loads with nothing but `node`.
 */

function stringValue(value, fallback = "") {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
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

module.exports = {stringValue, numberValue, listMaps, nonNegativeEventTime, normalizeLessonAnswers};
