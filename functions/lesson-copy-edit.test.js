"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {
  EDIT_COPY_VIEW,
  buildEditCopy,
  mergedQuestionsForApply,
  parseEditCopyRequest,
  validateEditProposalAgainstBase,
} = require("./lesson-copy-edit");

function choicePayload(overrides = {}) {
  return JSON.stringify({
    type: "SingleChoice",
    id: "q1",
    difficulty: "EASY",
    text: "Сколько будет 2+2?",
    imageUrl: null,
    options: [
      {id: "a", text: "три"},
      {id: "b", text: "четыре"},
    ],
    correctOptionId: "b",
    ...overrides,
  });
}

function baseQuestion(overrides = {}) {
  return {
    id: "q1",
    lessonId: "lesson-1",
    type: "SingleChoice",
    language: "ru",
    languageLevel: 0,
    difficulty: "EASY",
    order: 0,
    text: "Сколько будет 2+2?",
    payload: choicePayload(),
    version: 3,
    ...overrides,
  };
}

test("edit copy request needs a lesson id and nothing else", () => {
  assert.deepEqual(parseEditCopyRequest({lessonId: "lesson-1"}), {lessonId: "lesson-1"});
  assert.throws(() => parseEditCopyRequest({}), /lessonId is required/);
  assert.throws(() => parseEditCopyRequest({lessonId: "a/b"}), /lessonId is required/);
});

test("copy splits redactable questions into keyless public halves", () => {
  const copy = buildEditCopy({
    lesson: {id: "lesson-1", title: "Арифметика", version: 4},
    questions: [baseQuestion()],
  });
  assert.equal(copy.view, EDIT_COPY_VIEW);
  assert.equal(copy.lessonId, "lesson-1");
  assert.equal(copy.baseRevision, 4);
  const row = copy.questions[0];
  assert.equal(row.editable, true, "a modern question is editable");
  const payload = JSON.parse(row.payload);
  assert.equal(payload.type, "SingleChoiceRedacted");
  assert.equal(payload.correctOptionId, undefined, "the answer key never enters the copy");
  assert.equal(payload.options.length, 2);
});

test("locked questions ride the copy byte-for-byte and stay locked", () => {
  const legacy = baseQuestion({
    id: "q-legacy",
    payload: JSON.stringify({someLegacyDialect: true, answer: "б"}),
  });
  const survey = baseQuestion({
    id: "q-survey",
    payload: JSON.stringify({
      type: "Survey", id: "q-survey", difficulty: "EASY", text: "Как дела?",
      imageUrl: null, options: [{id: "a", text: "ок"}],
    }),
  });
  const copy = buildEditCopy({lesson: {id: "lesson-1", version: 1}, questions: [legacy, survey]});
  assert.equal(copy.questions[0].editable, false, "a refused dialect is locked");
  assert.equal(copy.questions[0].payload, legacy.payload, "locked payload is byte-for-byte");
  assert.equal(copy.questions[1].editable, false, "a survey has no answer to mark");
});

test("proposal validation refuses foreign ids and locked edits, keeps own ids", () => {
  const base = [
    {...buildEditCopy({lesson: {id: "l", version: 1}, questions: [baseQuestion()]}).questions[0]},
    {
      id: "q-locked", lessonId: "l", type: "", language: "ru", languageLevel: 0,
      difficulty: "EASY", order: 1, text: "легаси", imagePath: null,
      payload: "{\"legacy\":true}", editable: false,
    },
  ];
  assert.throws(
    () => validateEditProposalAgainstBase({baseQuestions: base, proposalQuestions: [
      {id: "q-foreign", text: "x", payload: "{}"},
    ]}),
    /PROPOSAL_QUESTION_FOREIGN/,
  );
  assert.throws(
    () => validateEditProposalAgainstBase({baseQuestions: base, proposalQuestions: [
      {id: "q-locked", text: "другой текст", payload: base[1].payload},
    ]}),
    /PROPOSAL_LOCKED_QUESTION_EDITED/,
  );
  const rows = validateEditProposalAgainstBase({baseQuestions: base, proposalQuestions: [
    {id: "q1", text: "новый текст", payload: base[0].payload},
  ]});
  assert.equal(rows[0].lessonId, "lesson-1", "the lesson id comes from the base");
});

test("merge names only the questions that actually changed and refuses a no-op", () => {
  const editable = buildEditCopy({
    lesson: {id: "l", version: 1},
    questions: [baseQuestion(), baseQuestion({id: "q2", text: "3+3?", payload: choicePayload({id: "q2", text: "3+3?"})})],
  }).questions;
  const {questions, changedQuestionIds} = mergedQuestionsForApply({
    baseQuestions: editable,
    proposalQuestions: [
      {id: "q1", text: editable[0].text, payload: choicePayload({correctOptionId: "a"})},
      {id: "q2", text: editable[1].text, payload: editable[1].payload},
    ],
    declaredEditedIds: ["q1"],
  });
  assert.deepEqual(changedQuestionIds, ["q1"], "only the edited question counts toward the fee");
  assert.equal(questions.length, 2);
  assert.throws(
    () => mergedQuestionsForApply({
      baseQuestions: editable,
      proposalQuestions: editable.map((q) => ({id: q.id, text: q.text, payload: q.payload})),
      declaredEditedIds: ["q1"],
    }),
    /PROPOSAL_NOTHING_CHANGED/,
  );
});

test("apply refuses a declared edited set that disagrees with the actual diff (L-new-1)", () => {
  const editable = buildEditCopy({
    lesson: {id: "l", version: 1},
    questions: [baseQuestion(), baseQuestion({id: "q2", text: "3+3?", payload: choicePayload({id: "q2", text: "3+3?"})})],
  }).questions;
  const changed = [
    {id: "q1", text: editable[0].text, payload: choicePayload({correctOptionId: "a"})},
    {id: "q2", text: editable[1].text, payload: choicePayload({id: "q2", text: editable[1].text, correctOptionId: "a"})},
  ];
  // Заявлено меньше факта: q2 тоже изменён, но не заявлен — плата была за один.
  assert.throws(
    () => mergedQuestionsForApply({baseQuestions: editable, proposalQuestions: changed, declaredEditedIds: ["q1"]}),
    /PROPOSAL_EDITED_SET_MISMATCH.*q2/,
  );
  // Заявлено больше факта: q2 заявлен, но едет байт-в-байт с базой.
  assert.throws(
    () => mergedQuestionsForApply({
      baseQuestions: editable,
      proposalQuestions: [changed[0], {id: "q2", text: editable[1].text, payload: editable[1].payload}],
      declaredEditedIds: ["q1", "q2"],
    }),
    /PROPOSAL_EDITED_SET_MISMATCH.*q2/,
  );
  // Пустая декларация на применении — расхождение (на подаче пустое режет парсер 4.21).
  assert.throws(
    () => mergedQuestionsForApply({baseQuestions: editable, proposalQuestions: changed, declaredEditedIds: []}),
    /PROPOSAL_EDITED_SET_MISMATCH/,
  );
  // Применение без декларации вовсе — отказ, а не молчаливая запись.
  assert.throws(
    () => mergedQuestionsForApply({baseQuestions: editable, proposalQuestions: changed}),
    /PROPOSAL_EDITED_SET_MISMATCH/,
  );
  // Совпадающие множества в другом порядке применяются; дубликаты декларации не мешают.
  const {changedQuestionIds} = mergedQuestionsForApply({
    baseQuestions: editable,
    proposalQuestions: changed,
    declaredEditedIds: ["q2", "q1", "q1"],
  });
  assert.deepEqual(changedQuestionIds, ["q1", "q2"]);
});
