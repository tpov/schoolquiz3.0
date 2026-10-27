"use strict";

// Исход модельной проверки (история 4.16, UX-6): чистое вычисление из записей конвейера —
// статусы, заваленные вопросы с уроком/этапом/причиной, прошедшие уроки, язык источника.

const assert = require("node:assert/strict");
const test = require("node:test");
const {buildSubmissionOutcome} = require("./review-outcome");

function pipeline(status, lessons) {
  return {
    schemaVersion: 1,
    submissionId: "sub-1",
    status,
    lessons,
    updatedAtMs: 500,
  };
}

const stage = (state, verdictId = null) => ({state, attempts: 1, errors: [], verdictId});

test("a running pipeline has no outcome yet", () => {
  const running = pipeline("RUNNING", {
    l1: {overall: "PENDING", stages: {
      WORDS: stage("DONE", "v1"), FACTS: stage("PENDING"), TRANSLATE: stage("PENDING"),
    }},
  });
  assert.equal(buildSubmissionOutcome({pipeline: running, verdicts: [], translations: []}), null);
});

test("unknown pipeline status is a loud repair", () => {
  assert.throws(() => buildSubmissionOutcome({
    pipeline: pipeline("WEIRD", {}),
    verdicts: [],
    translations: [],
  }), RangeError);
  assert.throws(() => buildSubmissionOutcome({pipeline: null, verdicts: [], translations: []}), RangeError);
});

test("reviewed quest: passing lessons, no failures, detected language from the first translation", () => {
  const reviewed = pipeline("QUEST_REVIEWED", {
    l1: {overall: "DONE", stages: {WORDS: stage("DONE", "v-w1"), FACTS: stage("DONE", "v-f1"), TRANSLATE: stage("DONE", "t1")}},
    l2: {overall: "DONE", stages: {WORDS: stage("DONE", "v-w2"), FACTS: stage("DONE", "v-f2"), TRANSLATE: stage("DONE", "t2")}},
  });
  const outcome = buildSubmissionOutcome({
    pipeline: reviewed,
    verdicts: [
      {submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 1,
        questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "clean"}]},
      {submissionId: "sub-1", lessonId: "l1", stage: "FACTS", attempt: 1,
        questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "true"}]},
    ],
    translations: [
      {submissionId: "sub-1", lessonId: "l2", stage: "TRANSLATE", attempt: 1, detectedLanguage: "uk"},
      {submissionId: "sub-1", lessonId: "l1", stage: "TRANSLATE", attempt: 1, detectedLanguage: "ru"},
    ],
  });
  assert.equal(outcome.pipelineStatus, "QUEST_REVIEWED");
  assert.deepEqual(outcome.passingLessonIds, ["l1", "l2"]);
  assert.deepEqual(outcome.failedQuestions, []);
  assert.equal(outcome.detectedLanguage, "ru", "lesson order of the pipeline, not of the reply");
  assert.equal(outcome.reviewedAtMs, 500);
});

test("failed questions carry lesson, question, stage and reason (UX-6 expansion)", () => {
  const reviewed = pipeline("QUEST_REVIEWED", {
    l1: {overall: "DONE", stages: {WORDS: stage("DONE", "v-w"), FACTS: stage("DONE", "v-f"), TRANSLATE: stage("SKIPPED")}},
  });
  const outcome = buildSubmissionOutcome({
    pipeline: reviewed,
    verdicts: [
      {submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 1,
        questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "clean"}]},
      {submissionId: "sub-1", lessonId: "l1", stage: "FACTS", attempt: 2,
        questionVerdicts: [
          {questionId: "q1", verdict: "FAIL", reason: "ключ неверен: столица Франции — Париж"},
          {questionId: "q2", verdict: "PASS", reason: "true"},
        ]},
      // Попытка 1 того же этапа: исход читает последнюю, а не первую.
      {submissionId: "sub-1", lessonId: "l1", stage: "FACTS", attempt: 1,
        questionVerdicts: [{questionId: "q9", verdict: "FAIL", reason: "устаревшая попытка"}]},
    ],
    translations: [],
  });
  assert.deepEqual(outcome.passingLessonIds, []);
  assert.deepEqual(outcome.failedQuestions,
    [{lessonId: "l1", questionId: "q1", stage: "FACTS", reason: "ключ неверен: столица Франции — Париж"}]);
});

test("delayed quest names the delayed stages and keeps whatever passed", () => {
  const delayed = pipeline("REVIEW_DELAYED", {
    l1: {overall: "PENDING", stages: {WORDS: stage("DONE", "v-w"), FACTS: stage("DONE", "v-f"), TRANSLATE: stage("DONE", "t1")}},
    l2: {overall: "PENDING", stages: {
      WORDS: stage("DONE", "v-w2"), FACTS: {state: "DELAYED", attempts: 3, errors: ["x"], verdictId: null},
      TRANSLATE: stage("PENDING"),
    }},
  });
  const outcome = buildSubmissionOutcome({
    pipeline: delayed,
    verdicts: [
      {submissionId: "sub-1", lessonId: "l2", stage: "WORDS", attempt: 1,
        questionVerdicts: [{questionId: "q2", verdict: "FAIL", reason: "мат в формулировке"}]},
    ],
    translations: [
      {submissionId: "sub-1", lessonId: "l1", stage: "TRANSLATE", attempt: 1, detectedLanguage: "en"},
    ],
  });
  assert.equal(outcome.pipelineStatus, "REVIEW_DELAYED");
  assert.deepEqual(outcome.delayedStages, [{lessonId: "l2", stage: "FACTS"}]);
  assert.deepEqual(outcome.passingLessonIds, ["l1"]);
  assert.deepEqual(outcome.failedQuestions,
    [{lessonId: "l2", questionId: "q2", stage: "WORDS", reason: "мат в формулировке"}]);
  assert.equal(outcome.detectedLanguage, "en");
});

test("records of other submissions do not leak into the outcome", () => {
  const reviewed = pipeline("QUEST_REVIEWED", {
    l1: {overall: "DONE", stages: {WORDS: stage("DONE", "v-w"), FACTS: stage("DONE", "v-f"), TRANSLATE: stage("DONE", "t1")}},
  });
  const outcome = buildSubmissionOutcome({
    pipeline: reviewed,
    verdicts: [
      {submissionId: "other", lessonId: "l1", stage: "WORDS", attempt: 9,
        questionVerdicts: [{questionId: "qX", verdict: "FAIL", reason: "чужая заявка"}]},
    ],
    translations: [
      {submissionId: "other", lessonId: "l1", stage: "TRANSLATE", attempt: 1, detectedLanguage: "fr"},
    ],
  });
  assert.deepEqual(outcome.failedQuestions, []);
  assert.equal(outcome.detectedLanguage, null);
  // Перевод чужой заявки не делает урок прошедшим: прошедшим урок делает запись этого submission.
  assert.deepEqual(outcome.passingLessonIds, []);
});
