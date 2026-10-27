"use strict";

// Скиллы этапов «Слова», «Факты» и «Перевод» (4.13–4.15): версия промпта, конверт данных урока,
// полный контракт воркера и линтер по строгой схеме.

const assert = require("node:assert/strict");
const test = require("node:test");
const {STAGE_PLAN} = require("./review-pipeline");
const {completeReviewStages, reviewStages} = require("./review-stages");
const stageWords = require("./stage-words");
const stageFacts = require("./stage-facts");
const stageTranslate = require("./stage-translate");
const {wrapLessonData} = require("./review-model-client");

const CONTEXT = {
  questTitle: "Квест",
  categoryPath: "Математика",
  lessonId: "l1",
  lessonTitle: "Урок 1",
  questions: [{id: "q1", text: "2+2?"}],
};

function workerContract(stage) {
  assert.equal(typeof stage.systemPrompt, "function");
  assert.equal(typeof stage.userPrompt, "function");
  assert.equal(typeof stage.lint, "function");
  assert.equal(typeof stage.promptVersion, "string");
  assert.ok(stage.promptVersion.length > 0);
}

function validReply(extra = {}) {
  return JSON.stringify({
    questions: [{questionId: "q1", verdict: "PASS", reason: "clean"}],
    sources: [],
    rationale: "ok",
    ...extra,
  });
}

test("registry covers the whole stage plan — a new stage without a skill breaks the test, not the queue", () => {
  assert.deepEqual(Object.keys(reviewStages).sort(), [...STAGE_PLAN].sort());
  assert.equal(completeReviewStages(), reviewStages);
});

test("words skill: full worker contract, versioned prompt, lesson as data, no search tool", () => {
  workerContract(stageWords);
  assert.equal(stageWords.promptVersion, "words-v1");
  const system = stageWords.systemPrompt();
  assert.match(system, /детск/);
  assert.match(system, /сомнени[ея]/);
  assert.match(system, /НИКОГДА не исправля/);
  const user = stageWords.userPrompt(CONTEXT);
  assert.ok(user.includes(wrapLessonData(CONTEXT)), "lesson travels inside the data envelope");
  const lint = stageWords.lint(validReply(), CONTEXT);
  assert.equal(lint.ok, true);
  assert.deepEqual(lint.verdict.questionVerdicts, [{questionId: "q1", verdict: "PASS", reason: "clean"}]);
  assert.equal(stageWords.lint(validReply({sources: ["https://example.test/a"]}), CONTEXT).ok, true,
    "words does not require sources but tolerates them");
  assert.equal(typeof stageWords.tools, "undefined", "words never asks for search");
});

test("facts skill: full worker contract, judge-over-checklist prompt, mandatory sources, search tool", () => {
  workerContract(stageFacts);
  assert.equal(stageFacts.promptVersion, "facts-v1");
  const system = stageFacts.systemPrompt();
  assert.match(system, /поиск/);
  assert.match(system, /сомнени[ея]/);
  assert.match(system, /не правишь текст автора/);
  assert.match(system, /однозначность/);
  const user = stageFacts.userPrompt(CONTEXT);
  assert.ok(user.includes(wrapLessonData(CONTEXT)));
  assert.equal(stageFacts.lint(validReply(), CONTEXT).error, "REVIEW_SCHEMA_SOURCES_EMPTY",
    "a fact verdict without sources is refused");
  const sourced = stageFacts.lint(validReply({sources: ["https://example.test/math"]}), CONTEXT);
  assert.equal(sourced.ok, true);
  assert.deepEqual(sourced.verdict.sources, ["https://example.test/math"]);
  const tools = stageFacts.tools();
  assert.deepEqual(tools, [{type: "web_search", web_search: {enable: true, search_result: true}}]);
});

test("doubt is expressed as FAIL with a named reason — the schema has no third verdict", () => {
  const doubt = JSON.stringify({
    questions: [{questionId: "q1", verdict: "FAIL", reason: "сомнение в факте: 2+2 при переполнении разрядности"}],
    sources: ["https://example.test/x"],
    rationale: "doubt rejects",
  });
  const result = stageFacts.lint(doubt, CONTEXT);
  assert.equal(result.ok, true);
  assert.equal(result.verdict.questionVerdicts[0].verdict, "FAIL");
});

const TRANSLATE_CONTEXT = {
  questTitle: "Квест",
  categoryPath: "Математика",
  lessonId: "l1",
  lessonTitle: "Урок 1",
  questions: [{
    id: "q1", draftId: "d", lessonId: "l1", type: "SINGLE_CHOICE", language: "ru",
    languageLevel: 0, difficulty: "EASY", order: 0, text: "2+2?", imagePath: null, updatedAtMs: 1,
    payload: JSON.stringify({
      type: "SingleChoice", id: "q1", difficulty: "EASY", text: "2+2?", imageUrl: null,
      options: [{id: "a", text: "три"}, {id: "b", text: "четыре"}], correctOptionId: "b",
    }),
  }],
};

const TRANSLATE_REPLY = JSON.stringify({
  detectedLanguage: "ru",
  translations: {
    uk: [{questionId: "q1", text: "Скільки буде 2+2?", options: [{id: "a", text: "три"}, {id: "b", text: "чотири"}]}],
    en: [{questionId: "q1", text: "How much is 2+2?", options: [{id: "a", text: "three"}, {id: "b", text: "four"}]}],
  },
});

test("translate skill: full worker contract, versioned prompt, lesson as data, no tools", () => {
  workerContract(stageTranslate);
  assert.equal(stageTranslate.promptVersion, "translate-v1");
  const system = stageTranslate.systemPrompt();
  assert.match(system, /переводчик/);
  assert.match(system, /protectedTextSegments/);
  assert.match(system, /НЕ переводят/);
  assert.match(system, /detectedLanguage/);
  const user = stageTranslate.userPrompt(TRANSLATE_CONTEXT);
  assert.ok(user.includes(wrapLessonData(TRANSLATE_CONTEXT)), "lesson travels inside the data envelope");
  const lint = stageTranslate.lint(TRANSLATE_REPLY, TRANSLATE_CONTEXT);
  assert.equal(lint.ok, true);
  assert.equal(lint.translation.detectedLanguage, "ru");
  assert.equal(lint.translation.questions.uk[0].id, "q1__uk");
  const broken = JSON.parse(TRANSLATE_REPLY);
  broken.detectedLanguage = "de";
  assert.equal(stageTranslate.lint(JSON.stringify(broken), TRANSLATE_CONTEXT).error,
    "TRANSLATION_LANGUAGE_UNKNOWN");
  assert.equal(typeof stageTranslate.tools, "undefined", "translation needs no tools");
});
