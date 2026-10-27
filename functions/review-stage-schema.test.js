"use strict";

// Строгая схема вердиктов (4.13/4.14): полный отказ при любом отклонении формы — линтер
// отвечает за то, чтобы угаданный или кривой ответ модели никогда не стал вердиктом.

const assert = require("node:assert/strict");
const test = require("node:test");
const {parseVerdictReply} = require("./review-stage-schema");

const QUESTIONS = [{id: "q1"}, {id: "q2"}];

function reply(overrides = {}) {
  return JSON.stringify({
    questions: [
      {questionId: "q1", verdict: "PASS", reason: "clean"},
      {questionId: "q2", verdict: "FAIL", reason: "forbidden phrase"},
    ],
    sources: [],
    rationale: "checked",
    ...overrides,
  });
}

const GOOD = {
  questions: QUESTIONS,
  requireSources: false,
};

test("a well-formed reply parses into verdict records", () => {
  const result = parseVerdictReply(reply(), GOOD);
  assert.equal(result.ok, true);
  assert.deepEqual(result.verdict.questionVerdicts, [
    {questionId: "q1", verdict: "PASS", reason: "clean"},
    {questionId: "q2", verdict: "FAIL", reason: "forbidden phrase"},
  ]);
  assert.deepEqual(result.verdict.sources, []);
  assert.equal(result.verdict.rationale, "checked");
});

test("fenced JSON is unwrapped whole, garbage is a named refusal", () => {
  const fenced = "```json\n" + reply() + "\n```";
  assert.equal(parseVerdictReply(fenced, GOOD).ok, true);
  assert.equal(parseVerdictReply("не JSON вовсе", GOOD).error, "REVIEW_SCHEMA_NOT_JSON");
});

test("every deviation is a named refusal, never a partial verdict", () => {
  const bad = [
    ["not json", "REVIEW_SCHEMA_NOT_JSON"],
    [JSON.stringify([]), "REVIEW_SCHEMA_NOT_OBJECT"],
    [JSON.stringify({rationale: "r"}), "REVIEW_SCHEMA_NO_QUESTIONS"],
    [reply({questions: [{verdict: "PASS", reason: "x"}]}), "REVIEW_SCHEMA_QUESTION_ID_MISSING"],
    [reply({questions: [{questionId: "q1", verdict: "PASS", reason: "x"}, {questionId: "q1", verdict: "FAIL", reason: "y"}]}), "REVIEW_SCHEMA_QUESTION_DUPLICATED"],
    [reply({questions: [{questionId: "q1", verdict: "MAYBE", reason: "x"}, {questionId: "q2", verdict: "FAIL", reason: "y"}]}), "REVIEW_SCHEMA_UNKNOWN_VERDICT"],
    [reply({questions: [{questionId: "q1", verdict: "PASS", reason: " "}, {questionId: "q2", verdict: "FAIL", reason: "y"}]}), "REVIEW_SCHEMA_REASON_MISSING"],
    [reply({questions: [{questionId: "q1", verdict: "PASS", reason: "x"}]}), "REVIEW_SCHEMA_QUESTION_UNCOVERED"],
    [JSON.stringify({
      questions: [
        {questionId: "q1", verdict: "PASS", reason: "x"},
        {questionId: "q2", verdict: "FAIL", reason: "y"},
        {questionId: "q9", verdict: "PASS", reason: "z"},
      ],
      sources: [], rationale: "r",
    }), "REVIEW_SCHEMA_QUESTION_FOREIGN"],
    [reply({sources: "none"}), "REVIEW_SCHEMA_SOURCES_MISSING"],
    [reply({sources: ["ftp://example.test/a"]}), "REVIEW_SCHEMA_SOURCE_NOT_HTTPS"],
    [reply({rationale: ""}), "REVIEW_SCHEMA_RATIONALE_MISSING"],
  ];
  for (const [content, error] of bad) {
    assert.equal(parseVerdictReply(content, GOOD).error, error, `expected ${error} for ${content}`);
  }
});

test("facts mode demands at least one https source, words mode does not", () => {
  assert.equal(parseVerdictReply(reply(), {questions: QUESTIONS, requireSources: true}).error,
    "REVIEW_SCHEMA_SOURCES_EMPTY");
  const withSource = reply({sources: ["https://example.test/a"]});
  assert.equal(parseVerdictReply(withSource, {questions: QUESTIONS, requireSources: true}).ok, true);
});
