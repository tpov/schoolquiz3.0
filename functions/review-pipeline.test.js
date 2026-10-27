"use strict";

// Машина конвейера проверки (история 4.12): порядок этапов, лента попыток, DELAYED после трёх
// именованных ошибок, неизменяемая запись вердикта. Модуль чистый — Firestore не нужен.

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  MAX_ATTEMPTS,
  QUEST_STATUS,
  STAGE_PLAN,
  applyStageError,
  applyVerdict,
  buildVerdictRecord,
  createPipelineState,
  nextJob,
  questStatus,
  verdictDocumentId,
} = require("./review-pipeline");

function state(lessonIds = ["l1", "l2"]) {
  return createPipelineState({
    submissionId: "sub-1",
    ownerUid: "owner",
    questId: "quest-1",
    questTitle: "Квест",
    categoryPath: "Математика",
    revision: 7,
    lessonIds,
  });
}

test("plan and bounds match the contract", () => {
  assert.deepEqual(STAGE_PLAN, ["WORDS", "FACTS"]);
  assert.equal(MAX_ATTEMPTS, 3);
});

test("jobs walk lessons in order and stages in plan order, attempts counted per stage", () => {
  const pipeline = state();
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "WORDS", attempt: 1});
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "FACTS", attempt: 1});
  applyVerdict(pipeline, "l1", "FACTS", "v2");
  assert.deepEqual(nextJob(pipeline), {lessonId: "l2", stage: "WORDS", attempt: 1});
});

test("three named errors delay the stage, no fourth attempt is offered", () => {
  const pipeline = state(["l1"]);
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "WORDS", attempt});
    applyStageError(pipeline, "l1", "WORDS", attempt, "REVIEW_SCHEMA_MISSING_FIELD");
  }
  const stageState = pipeline.lessons.l1.stages.WORDS;
  assert.equal(stageState.state, "DELAYED");
  assert.equal(stageState.attempts, 3);
  assert.deepEqual(stageState.errors, [
    "REVIEW_SCHEMA_MISSING_FIELD",
    "REVIEW_SCHEMA_MISSING_FIELD",
    "REVIEW_SCHEMA_MISSING_FIELD",
  ]);
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "FACTS", attempt: 1});
  assert.equal(questStatus(pipeline), QUEST_STATUS.DELAYED);
});

test("quest is reviewed only when every lesson passed every stage", () => {
  const pipeline = state();
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  assert.equal(questStatus(pipeline), QUEST_STATUS.RUNNING);
  applyVerdict(pipeline, "l1", "FACTS", "v2");
  applyVerdict(pipeline, "l2", "WORDS", "v3");
  applyVerdict(pipeline, "l2", "FACTS", "v4");
  assert.equal(questStatus(pipeline), QUEST_STATUS.REVIEWED);
  assert.deepEqual(nextJob(pipeline), {done: true, status: QUEST_STATUS.REVIEWED});
});

test("verdict record binds identity, model, prompt, sources and per-question outcomes", () => {
  const record = buildVerdictRecord({
    submissionId: "sub-1",
    lessonId: "l1",
    stage: "WORDS",
    attempt: 2,
    revision: 7,
    model: "glm-4.6",
    promptVersion: "words-v1",
    questionVerdicts: [
      {questionId: "q1", verdict: "PASS", reason: "clean"},
      {questionId: "q2", verdict: "FAIL", reason: "forbidden phrase in text"},
    ],
    sources: ["https://example.test/a"],
    rationale: "children's audience rules",
  });
  assert.deepEqual(record, {
    schemaVersion: 1,
    submissionId: "sub-1",
    lessonId: "l1",
    stage: "WORDS",
    attempt: 2,
    revision: 7,
    model: "glm-4.6",
    promptVersion: "words-v1",
    sources: ["https://example.test/a"],
    rationale: "children's audience rules",
    questionVerdicts: [
      {questionId: "q1", verdict: "PASS", reason: "clean"},
      {questionId: "q2", verdict: "FAIL", reason: "forbidden phrase in text"},
    ],
  });
});

test("verdict record refuses unnamed outcomes and unknown verdicts", () => {
  assert.throws(() => buildVerdictRecord({submissionId: "s", lessonId: "l", stage: "WORDS", attempt: 1,
    model: "m", promptVersion: "p", questionVerdicts: []}), RangeError);
  assert.throws(() => buildVerdictRecord({submissionId: "s", lessonId: "l", stage: "WORDS", attempt: 1,
    model: "m", promptVersion: "p", questionVerdicts: [{questionId: "q1", verdict: "MAYBE", reason: "x"}]}), RangeError);
  assert.throws(() => buildVerdictRecord({submissionId: "s", lessonId: "l", stage: "WORDS", attempt: 1,
    model: "m", promptVersion: "p", questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: " "}]}), RangeError);
});

test("verdict document id is deterministic in the job identity", () => {
  assert.equal(
    verdictDocumentId({submissionId: "sub-1", lessonId: "l1", stage: "FACTS", attempt: 3}),
    "sub-1_l1_FACTS_3",
  );
  assert.throws(() => verdictDocumentId({submissionId: "sub-1", lessonId: "l/x", stage: "FACTS", attempt: 1}), RangeError);
  assert.throws(() => verdictDocumentId({submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 0}), RangeError);
  assert.throws(() => verdictDocumentId({submissionId: "sub-1", lessonId: "l1", stage: "TRANSLATE", attempt: 1}), RangeError);
});
