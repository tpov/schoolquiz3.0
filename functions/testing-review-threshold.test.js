"use strict";

/**
 * Порог TESTING-ревью (история 4.6, epic-authoring-review): один независимый ревью на каждый
 * вопрос ревизии, порог — из `configs/arena_review` (по умолчанию 2 из 1..3), результат привязан
 * к ревизии конфига и поколению заданий, проваленная стадия названа по имени.
 *
 * Приёмник живой: `submitReviewAction` на файрстор-заглушке. Публикацию (полное дерево) сюита не
 * гоняет — решение о пороге видно по статусу задания и его checks; живой круг publishing
 * доказан против demo-backend в journey.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");

const TASK_PATH = "admin/review/lessons/lesson";

function world(extra = {}) {
  return {
    "profiles/tester": {testerLevel: 100},
    "profiles/reviewer": {adminLevel: 100},
    "profiles/author": {},
    [TASK_PATH]: {
      assignmentId: "submission_lesson",
      submissionId: "submission",
      ownerUid: "author",
      catalogId: "catalog",
      draftId: "draft",
      questId: "quest",
      title: "Lesson",
      targetShelf: "arena",
      status: "UNDER_REVIEW",
      generation: 1,
      sourceLanguages: ["en"],
      checks: {},
      createdAtMs: 1,
      changedAtMs: 1,
    },
    [`${TASK_PATH}/quests/quest/questions/q1`]: {id: "q1", text: "one"},
    [`${TASK_PATH}/quests/quest/questions/q2`]: {id: "q2", text: "two"},
    ...extra,
  };
}

function testingAction(score, patch = {}) {
  return {
    assignmentId: "submission_lesson",
    lessonId: "lesson",
    kind: "TESTING",
    score,
    questionScores: [
      {questionId: "q1", score},
      {questionId: "q2", score},
    ],
    ...patch,
  };
}

async function submit(uid, data) {
  return functions.submitReviewAction.run({auth: {uid}, data});
}

function taskDoc() {
  return store.documents.get(TASK_PATH);
}

function reset(extra) {
  store.reset(world(extra));
}

test("default threshold 2: every-question score 3 with LOGIC 3 readies the lesson and binds config revision and generation", async () => {
  reset({"configs/arena_review": {configRevision: 4}});
  const testing = await submit("tester", testingAction(3));
  assert.equal(testing.aggregate.isTested, true);
  assert.deepEqual(testing.aggregate.questionOutcomes, [
    {questionId: "q1", testingScore: 3, passed: true},
    {questionId: "q2", testingScore: 3, passed: true},
  ]);
  assert.equal(testing.aggregate.appliedConfigRevision, 4);
  assert.equal(testing.aggregate.assignmentGeneration, 1);
  assert.equal(taskDoc().status, "UNDER_REVIEW", "LOGIC is still missing");

  const logic = await submit("reviewer", {assignmentId: "submission_lesson", lessonId: "lesson", kind: "LOGIC", score: 3});
  assert.equal(logic.aggregate.isLogicReviewed, true);
  assert.equal(taskDoc().status, "READY_FOR_PUBLICATION");
});

test("score 1 under the default threshold fails TESTING by name, without any reviewer reason", async () => {
  reset();
  const testing = await submit("tester", testingAction(1));
  assert.equal(testing.aggregate.isTested, false);
  assert.deepEqual(
    testing.aggregate.questionOutcomes.map((o) => o.passed),
    [false, false],
  );
  assert.equal(taskDoc().status, "UNDER_REVIEW");
  // Именованный провал стадии живёт в задании: failedStages считает проверка, не ревьюер.
  const failed = JSON.stringify(taskDoc().checks.questionOutcomes);
  assert.ok(failed.includes("q1") && failed.includes("q2"), "outcomes name the questions");
});

test("config threshold 1 admits score 1; the result carries the applied config revision", async () => {
  reset({"configs/arena_review": {testingPassScore: 1, configRevision: 7}});
  const testing = await submit("tester", testingAction(1));
  assert.equal(testing.aggregate.isTested, true);
  assert.equal(testing.aggregate.appliedConfigRevision, 7);
  assert.equal(taskDoc().status, "UNDER_REVIEW");
});

test("config threshold 3 refuses score 2", async () => {
  reset({"configs/arena_review": {testingPassScore: 3}});
  const testing = await submit("tester", testingAction(2));
  assert.equal(testing.aggregate.isTested, false);
  assert.deepEqual(testing.aggregate.questionOutcomes.map((o) => o.passed), [false, false]);
});

test("out-of-range config threshold refuses loudly instead of clamping", async () => {
  reset({"configs/arena_review": {testingPassScore: 5}});
  await assert.rejects(
    () => submit("tester", testingAction(3)),
    (error) => error.code === "failed-precondition" && /testingPassScore/.test(error.message),
  );
});

test("TESTING without per-question scores is refused: the review must cover every question", async () => {
  reset();
  await assert.rejects(
    () => submit("tester", {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 3}),
    (error) => error.code === "invalid-argument" && /cover every question/.test(error.message),
  );
});

test("partial coverage names the missing question", async () => {
  reset();
  await assert.rejects(
    () => submit("tester", testingAction(3, {questionScores: [{questionId: "q1", score: 3}]})),
    (error) => error.code === "invalid-argument" && /q2/.test(error.message),
  );
});

test("unknown and duplicated question ids are refused", async () => {
  reset();
  await assert.rejects(
    () => submit("tester", testingAction(3, {questionScores: [{questionId: "q1", score: 3}, {questionId: "qX", score: 3}]})),
    (error) => error.code === "invalid-argument" && /unknown question/.test(error.message),
  );
  await assert.rejects(
    () => submit("tester", testingAction(3, {questionScores: [{questionId: "q1", score: 3}, {questionId: "q1", score: 2}]})),
    (error) => error.code === "invalid-argument" && /duplicates/.test(error.message),
  );
});

test("question score outside 1..3 is refused", async () => {
  reset();
  await assert.rejects(
    () => submit("tester", testingAction(3, {questionScores: [{questionId: "q1", score: 4}, {questionId: "q2", score: 3}]})),
    (error) => error.code === "invalid-argument" && /1\.\.3/.test(error.message),
  );
});

test("mixed per-question scores: the failing question is named in its outcome", async () => {
  reset();
  const testing = await submit("tester", testingAction(3, {
    questionScores: [{questionId: "q1", score: 3}, {questionId: "q2", score: 1}],
  }));
  assert.equal(testing.aggregate.isTested, false);
  assert.deepEqual(testing.aggregate.questionOutcomes, [
    {questionId: "q1", testingScore: 3, passed: true},
    {questionId: "q2", testingScore: 1, passed: false},
  ]);
});

test("the author cannot test their own lesson (independence is per review, not per question)", async () => {
  reset();
  await assert.rejects(
    () => submit("author", testingAction(3)),
    (error) => error.code === "permission-denied",
  );
  assert.equal(store.documents.get(`${TASK_PATH}/reviews/submission_lesson_testing`), undefined, "no record written");
});

test("unqualified tester is refused", async () => {
  reset({"profiles/nobody": {}});
  await assert.rejects(
    () => submit("nobody", testingAction(3)),
    (error) => error.code === "permission-denied",
  );
});

/**
 * Перевод и сегментные решения (вторая половина 4.6): язык засчитывает только запись, где у
 * каждого вопроса есть содержание; решение по сегменту — явное correct/error, молчаливый
 * `accepted` проводом запрещён, а решение остаётся в записи.
 */
function translationWorld(extra = {}) {
  const base = world();
  return {
    ...base,
    "configs/arena_review": {requiredLanguages: ["uk"]},
    "profiles/translator": {translatorLevel: 100, knownLanguages: ["en", "uk"]},
    "profiles/segreviewer": {translatorLevel: 200, knownLanguages: ["en", "uk"]},
    [TASK_PATH]: {
      ...base[TASK_PATH],
      checks: {isTested: true, testingScore: 3, isLogicReviewed: true, logicScore: 3},
    },
    ...extra,
  };
}

function translatedQuestion(id, text) {
  return {id, lessonId: "lesson", type: "QA", language: "uk", text, payload: "{}", updatedAtMs: 1};
}

test("a translation with content on every question credits the language", async () => {
  reset(translationWorld());
  const translation = await submit("translator", {
    assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION", language: "uk",
    translatedQuestions: [translatedQuestion("q1", "один"), translatedQuestion("q2", "два")],
  });
  assert.deepEqual(translation.aggregate.translatedLanguages, {uk: 100});
});

test("missing text is never auto-approved: a blank question does not credit the language", async () => {
  reset(translationWorld());
  const translation = await submit("translator", {
    assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION", language: "uk",
    translatedQuestions: [translatedQuestion("q1", "один"), translatedQuestion("q2", "   ")],
  });
  assert.deepEqual(translation.aggregate.translatedLanguages, {},
    "the language stays untranslated while a question carries no content");
});

async function submitTranslation() {
  // Реальный порядок цикла на чистом задании: тестирование и логика закрывают допуск перевода,
  // запись перевода открывает язык сегментному ревью.
  reset(translationWorld({
    [TASK_PATH]: {...translationWorld()[TASK_PATH], checks: {}},
  }));
  await submit("tester", testingAction(3));
  await submit("reviewer", {assignmentId: "submission_lesson", lessonId: "lesson", kind: "LOGIC", score: 3});
  await submit("translator", {
    assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION", language: "uk",
    translatedQuestions: [translatedQuestion("q1", "один"), translatedQuestion("q2", "два")],
  });
}

test("a silent accepted boolean is not a segment decision: the wire requires correct/error", async () => {
  reset(translationWorld());
  await submitTranslation();
  await assert.rejects(
    () => submit("segreviewer", {
      assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION_REVIEW", language: "uk",
      segmentResults: [{questionId: "q1", segmentKey: "text", accepted: true}],
    }),
    (error) => error.code === "invalid-argument" && /explicit decision/.test(error.message),
  );
});

test("an explicit decision outside correct/error is refused", async () => {
  reset(translationWorld());
  await submitTranslation();
  await assert.rejects(
    () => submit("segreviewer", {
      assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION_REVIEW", language: "uk",
      segmentResults: [{questionId: "q1", segmentKey: "text", decision: "maybe"}],
    }),
    (error) => error.code === "invalid-argument" && /"correct" or "error"/.test(error.message),
  );
});

test("explicit segment decisions are stored with their decision and score the translation", async () => {
  reset(translationWorld());
  await submitTranslation();
  const review = await submit("segreviewer", {
    assignmentId: "submission_lesson", lessonId: "lesson", kind: "TRANSLATION_REVIEW", language: "uk",
    segmentResults: [
      {questionId: "q1", segmentKey: "text", decision: "correct"},
      {questionId: "q2", segmentKey: "text", decision: "error"},
    ],
  });
  assert.equal(review.aggregate.translationScore, 50);
  const stored = [...store.documents.entries()]
    .find(([path, doc]) => path.startsWith(`${TASK_PATH}/reviews/`) && doc.kind === "TRANSLATION_REVIEW");
  assert.ok(stored, "the translation review record exists");
  assert.deepEqual(stored[1].segmentResults, [
    {questionId: "q1", segmentKey: "text", decision: "correct", accepted: true},
    {questionId: "q2", segmentKey: "text", decision: "error", accepted: false},
  ]);
});
