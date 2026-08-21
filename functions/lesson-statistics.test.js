"use strict";

const assert = require("assert");
const {
  summarizeLessonAnswers,
  summarizeAnswerDistribution,
  selectedOptionIds,
} = require("./lesson-statistics");

function answer(questionId, score, durationMs = 1000) {
  return {questionId, score, durationMs};
}

function payloadAnswer(payload) {
  return {answerPayload: JSON.stringify(payload)};
}

function testLessonSummaryCountsCorrectness() {
  const summary = summarizeLessonAnswers([
    answer("q1", 9),
    answer("q1", 9),
    answer("q1", 1),
    answer("q2", 1),
  ]);

  const q1 = summary.find((s) => s.questionId === "q1");
  assert.strictEqual(q1.answerCount, 3);
  assert.strictEqual(q1.correctCount, 2);
  assert.strictEqual(q1.correctPercent, 67);

  const q2 = summary.find((s) => s.questionId === "q2");
  assert.strictEqual(q2.correctPercent, 0);
}

function testPartialScoresAreNotCountedAsCorrect() {
  // Only a full 9 means the whole answer was right; a partially correct ordering is not.
  const summary = summarizeLessonAnswers([answer("q1", 8), answer("q1", 5)]);
  assert.strictEqual(summary[0].correctCount, 0);
}

function testAverageDuration() {
  const summary = summarizeLessonAnswers([
    answer("q1", 9, 1000),
    answer("q1", 9, 3000),
  ]);
  assert.strictEqual(summary[0].averageDurationMs, 2000);
}

function testUnknownDurationDoesNotBreakTheAverage() {
  // Restored sessions report 0; it must not turn into NaN or a negative number.
  const summary = summarizeLessonAnswers([
    answer("q1", 9, 0),
    answer("q1", 9, 2000),
  ]);
  assert.strictEqual(summary[0].averageDurationMs, 1000);
}

function testEmptyInput() {
  assert.deepStrictEqual(summarizeLessonAnswers([]), []);
  assert.deepStrictEqual(summarizeAnswerDistribution([]), {totalAnswers: 0, options: []});
}

function testSingleChoiceDistribution() {
  const result = summarizeAnswerDistribution([
    payloadAnswer({type: "single-choice", selected: "A"}),
    payloadAnswer({type: "single-choice", selected: "A"}),
    payloadAnswer({type: "single-choice", selected: "B"}),
  ]);

  assert.strictEqual(result.totalAnswers, 3);
  assert.deepStrictEqual(
    result.options,
    [
      {optionId: "A", count: 2, percent: 67},
      {optionId: "B", count: 1, percent: 33},
    ],
  );
}

function testMultipleChoiceSharesMayExceedHundred() {
  // One person can pick several options, so the shares are per-respondent, not a partition.
  const result = summarizeAnswerDistribution([
    payloadAnswer({type: "multiple-choice", selected: ["A", "B"]}),
    payloadAnswer({type: "multiple-choice", selected: ["A"]}),
  ]);

  assert.strictEqual(result.totalAnswers, 2);
  const total = result.options.reduce((sum, o) => sum + o.percent, 0);
  assert.ok(total > 100, `expected shares above 100, got ${total}`);
}

function testUnansweredIsNotCounted() {
  // A timeout with nothing selected must not inflate the respondent count.
  const result = summarizeAnswerDistribution([
    payloadAnswer({type: "single-choice", selected: null}),
    payloadAnswer({type: "single-choice", selected: "A"}),
  ]);

  assert.strictEqual(result.totalAnswers, 1);
  assert.strictEqual(result.options[0].count, 1);
}

function testBrokenPayloadIsIgnoredRatherThanCrashing() {
  const result = summarizeAnswerDistribution([
    {answerPayload: "not json"},
    {answerPayload: ""},
    {answerPayload: JSON.stringify({type: "unknown-format", foo: 1})},
    payloadAnswer({type: "single-choice", selected: "A"}),
  ]);

  assert.strictEqual(result.totalAnswers, 1);
}

function testFillBlankAndOrderingExtractTheirPicks() {
  assert.deepStrictEqual(
    selectedOptionIds(JSON.stringify({type: "ordering", order: ["x", "y"]})),
    ["x", "y"],
  );
  assert.deepStrictEqual(
    selectedOptionIds(JSON.stringify({type: "fill-blank", filled: {b1: "c1", b2: null}})),
    ["c1"],
  );
}

testLessonSummaryCountsCorrectness();
testPartialScoresAreNotCountedAsCorrect();
testAverageDuration();
testUnknownDurationDoesNotBreakTheAverage();
testEmptyInput();
testSingleChoiceDistribution();
testMultipleChoiceSharesMayExceedHundred();
testUnansweredIsNotCounted();
testBrokenPayloadIsIgnoredRatherThanCrashing();
testFillBlankAndOrderingExtractTheirPicks();

console.log("lesson-statistics tests passed");
