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
  applyStageSkip,
  applyVerdict,
  buildTranslationRecord,
  buildVerdictRecord,
  createPipelineState,
  nextJob,
  questStatus,
  translationDocumentId,
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
  assert.deepEqual(STAGE_PLAN, ["WORDS", "FACTS", "TRANSLATE"]);
  assert.equal(MAX_ATTEMPTS, 3);
});

test("jobs walk lessons in order and stages in plan order, attempts counted per stage", () => {
  const pipeline = state();
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "WORDS", attempt: 1});
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "FACTS", attempt: 1});
  applyVerdict(pipeline, "l1", "FACTS", "v2");
  // Перевод — этап того же урока и идёт раньше следующего урока (4.15).
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "TRANSLATE", attempt: 1});
  applyVerdict(pipeline, "l1", "TRANSLATE", "t1");
  assert.deepEqual(nextJob(pipeline), {lessonId: "l2", stage: "WORDS", attempt: 1});
});

test("translation waits for both checks: a delayed check blocks it without attempts", () => {
  const pipeline = state(["l1"]);
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  applyStageError(pipeline, "l1", "FACTS", 1, "REVIEW_SCHEMA_NOT_JSON");
  // FACTS ещё жив (попытка 2), но перевод не предлагается, пока проверка не завершена.
  assert.deepEqual(nextJob(pipeline), {lessonId: "l1", stage: "FACTS", attempt: 2});
  applyStageError(pipeline, "l1", "FACTS", 2, "REVIEW_SCHEMA_NOT_JSON");
  applyStageError(pipeline, "l1", "FACTS", 3, "REVIEW_SCHEMA_NOT_JSON");
  assert.equal(pipeline.lessons.l1.stages.FACTS.state, "DELAYED");
  assert.deepEqual(nextJob(pipeline), {done: true, status: QUEST_STATUS.DELAYED});
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.attempts, 0);
});

test("a skipped translation settles the lesson: the quest can still be reviewed", () => {
  const pipeline = state();
  for (const lessonId of ["l1", "l2"]) {
    applyVerdict(pipeline, lessonId, "WORDS", `v-${lessonId}-w`);
    applyVerdict(pipeline, lessonId, "FACTS", `v-${lessonId}-f`);
  }
  applyStageSkip(pipeline, "l1", "TRANSLATE");
  applyVerdict(pipeline, "l2", "TRANSLATE", "t-l2");
  assert.equal(questStatus(pipeline), QUEST_STATUS.REVIEWED);
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.state, "SKIPPED");
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.attempts, 0);
});

test("a delayed translation delays the quest like a delayed check", () => {
  const pipeline = state(["l1"]);
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  applyVerdict(pipeline, "l1", "FACTS", "v2");
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    applyStageError(pipeline, "l1", "TRANSLATE", attempt, "TRANSLATION_STRUCTURE_MARKERS");
  }
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.state, "DELAYED");
  assert.deepEqual(pipeline.lessons.l1.stages.TRANSLATE.errors,
    ["TRANSLATION_STRUCTURE_MARKERS", "TRANSLATION_STRUCTURE_MARKERS", "TRANSLATION_STRUCTURE_MARKERS"]);
  assert.equal(questStatus(pipeline), QUEST_STATUS.DELAYED);
});

test("a legacy pipeline document without the TRANSLATE stage is a loud repair, not a silent skip", () => {
  const pipeline = state(["l1"]);
  delete pipeline.lessons.l1.stages.TRANSLATE;
  applyVerdict(pipeline, "l1", "WORDS", "v1");
  applyVerdict(pipeline, "l1", "FACTS", "v2");
  assert.throws(() => nextJob(pipeline), RangeError);
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
  // Проверки пройдены, но переводы ещё не решены — квест не рецензирован.
  assert.equal(questStatus(pipeline), QUEST_STATUS.RUNNING);
  applyVerdict(pipeline, "l1", "TRANSLATE", "t1");
  applyStageSkip(pipeline, "l2", "TRANSLATE");
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

test("translation document id is deterministic and refuses broken identities", () => {
  assert.equal(
    translationDocumentId({submissionId: "sub-1", lessonId: "l1", attempt: 2}),
    "sub-1_l1_TRANSLATE_2",
  );
  assert.throws(() => translationDocumentId({submissionId: "sub-1", lessonId: "l/x", attempt: 1}), RangeError);
  assert.throws(() => translationDocumentId({submissionId: "sub-1", lessonId: "l1", attempt: 0}), RangeError);
  assert.throws(() => translationDocumentId({submissionId: "sub-1", attempt: 1}), RangeError);
});

test("translation record binds revision, model, prompt, language and assembled questions", () => {
  const record = buildTranslationRecord({
    submissionId: "sub-1",
    lessonId: "l1",
    attempt: 1,
    revision: 7,
    model: "glm-4.6",
    promptVersion: "translate-v1",
    detectedLanguage: "ru",
    questions: {
      uk: [{id: "q1__uk", text: "Питання", payload: "{\"type\":\"SingleChoice\"}"}],
      en: [{id: "q1__en", text: "Question", payload: "{\"type\":\"SingleChoice\"}"}],
    },
  });
  assert.equal(record.detectedLanguage, "ru");
  assert.equal(record.stage, "TRANSLATE");
  assert.equal(record.promptVersion, "translate-v1");
  assert.deepEqual(Object.keys(record.questions).sort(), ["en", "uk"]);
  assert.throws(() => buildTranslationRecord({submissionId: "s", lessonId: "l", attempt: 1,
    model: "m", promptVersion: "p", detectedLanguage: "ru", questions: {}}), RangeError);
  assert.throws(() => buildTranslationRecord({submissionId: "s", lessonId: "l", attempt: 1,
    model: "m", promptVersion: "p", detectedLanguage: "ru", questions: {en: []}}), RangeError);
});

// ─── Сокращённый план этапов (4.21): предложения не переводятся ────────────────────────────────

function proposalState(lessonIds = ["p1"]) {
  return createPipelineState({
    submissionId: "prop-1",
    ownerUid: "proposer",
    questId: "lesson_1",
    questTitle: "Урок",
    categoryPath: "",
    revision: 3,
    lessonIds,
    stages: ["WORDS", "FACTS"],
  });
}

test("a reduced plan stores itself in the state and walks only its stages", () => {
  const pipeline = proposalState();
  assert.deepEqual(pipeline.stages, ["WORDS", "FACTS"]);
  assert.ok(pipeline.lessons.p1.stages.WORDS, "WORDS exists");
  assert.equal(pipeline.lessons.p1.stages.TRANSLATE, undefined, "no TRANSLATE stage in a proposal");
  applyVerdict(pipeline, "p1", "WORDS", "pv1");
  applyVerdict(pipeline, "p1", "FACTS", "pv2");
  assert.equal(nextJob(pipeline).done, true, "terminal right after both checks");
  assert.equal(nextJob(pipeline).status, QUEST_STATUS.REVIEWED, "no translation is waited for");
});

test("a reduced plan refuses stages outside the plan, repeats and an empty list", () => {
  assert.throws(() => createPipelineState({
    submissionId: "s", ownerUid: "o", questId: "q", lessonIds: ["l"], stages: ["WORDS", "TRANSLATE"],
  }), RangeError, "TRANSLATE without WORDS+FACTS order is out of the reduced plan");
  assert.throws(() => createPipelineState({
    submissionId: "s", ownerUid: "o", questId: "q", lessonIds: ["l"], stages: ["WORDS", "WORDS"],
  }), RangeError);
  assert.throws(() => createPipelineState({
    submissionId: "s", ownerUid: "o", questId: "q", lessonIds: ["l"], stages: [],
  }), RangeError);
  assert.throws(() => createPipelineState({
    submissionId: "s", ownerUid: "o", questId: "q", lessonIds: ["l"], stages: ["MYSTERY"],
  }), RangeError);
});

test("a delayed check in a reduced plan still delays the proposal quest", () => {
  const pipeline = proposalState();
  applyVerdict(pipeline, "p1", "WORDS", "pv1");
  applyStageError(pipeline, "p1", "FACTS", 1, "REVIEW_SCHEMA_BAD_SHAPE");
  applyStageError(pipeline, "p1", "FACTS", 2, "REVIEW_SCHEMA_BAD_SHAPE");
  applyStageError(pipeline, "p1", "FACTS", 3, "REVIEW_SCHEMA_BAD_SHAPE");
  assert.equal(nextJob(pipeline).done, true, "no live jobs remain");
  assert.equal(nextJob(pipeline).status, QUEST_STATUS.DELAYED);
});
