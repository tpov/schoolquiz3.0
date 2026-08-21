"use strict";

/**
 * Aggregates derived from per-question answers.
 *
 * Until now nothing produced lesson statistics at all: `lessons/{id}` carries `averageRating`,
 * `ratingCount` and `top3`, the client reads them, and no server code ever wrote them — they were
 * permanently empty. These helpers turn the answer log into the two summaries the product needs.
 *
 * Pure functions, so they can be tested without firebase-admin.
 */

/** A digit of 9 means every part of the answer was right. */
const CORRECT_SCORE = 9;

/**
 * Per-question summary for one lesson: how often each question is answered, how often correctly,
 * and how long it takes. A question almost everybody fails is either badly written or sits on
 * material the lesson never taught — both worth surfacing to the author.
 *
 * @param answers rows of {questionId, score, durationMs}
 */
function summarizeLessonAnswers(answers) {
  const byQuestion = new Map();
  for (const answer of answers) {
    const questionId = String(answer.questionId || "");
    if (!questionId) continue;
    const entry = byQuestion.get(questionId) || {
      questionId,
      answerCount: 0,
      correctCount: 0,
      totalDurationMs: 0,
    };
    entry.answerCount += 1;
    if (Number(answer.score) >= CORRECT_SCORE) entry.correctCount += 1;
    entry.totalDurationMs += Math.max(0, Number(answer.durationMs) || 0);
    byQuestion.set(questionId, entry);
  }

  return [...byQuestion.values()]
    .map((entry) => ({
      questionId: entry.questionId,
      answerCount: entry.answerCount,
      correctCount: entry.correctCount,
      // Percent of answers that were fully correct.
      correctPercent: Math.round((entry.correctCount * 100) / entry.answerCount),
      averageDurationMs: Math.round(entry.totalDurationMs / entry.answerCount),
    }))
    .sort((a, b) => a.questionId.localeCompare(b.questionId));
}

/**
 * How the answers to one question were distributed across its options — what a survey is for.
 *
 * The stored payload is the serialized UserAnswer; every variant is reduced to the list of option
 * ids it picked, so one counter covers single choice, multiple choice and polls alike.
 */
function summarizeAnswerDistribution(answers) {
  const counts = new Map();
  let totalAnswers = 0;

  for (const answer of answers) {
    const picked = selectedOptionIds(answer.answerPayload);
    if (picked.length === 0) continue;
    totalAnswers += 1;
    for (const optionId of picked) {
      counts.set(optionId, (counts.get(optionId) || 0) + 1);
    }
  }

  const options = [...counts.entries()]
    .map(([optionId, count]) => ({
      optionId,
      count,
      // Share of respondents who picked this option. With multiple choice the shares can sum
      // above 100 — that is correct, one person can pick several.
      percent: Math.round((count * 100) / totalAnswers),
    }))
    .sort((a, b) => b.count - a.count || a.optionId.localeCompare(b.optionId));

  return {totalAnswers, options};
}

/** Extracts chosen option ids from a serialized UserAnswer; unknown or broken payloads yield none. */
function selectedOptionIds(payload) {
  let parsed;
  try {
    parsed = JSON.parse(String(payload || ""));
  } catch (error) {
    return [];
  }
  if (!parsed || typeof parsed !== "object") return [];

  switch (parsed.type) {
    case "single-choice":
      return parsed.selected ? [String(parsed.selected)] : [];
    case "multiple-choice":
      return Array.isArray(parsed.selected) ? parsed.selected.map(String) : [];
    case "ordering":
      return Array.isArray(parsed.order) ? parsed.order.map(String) : [];
    case "fill-blank": {
      if (!parsed.filled || typeof parsed.filled !== "object") return [];
      return Object.values(parsed.filled).filter(Boolean).map(String);
    }
    default:
      return [];
  }
}

module.exports = {
  CORRECT_SCORE,
  summarizeLessonAnswers,
  summarizeAnswerDistribution,
  selectedOptionIds,
};
