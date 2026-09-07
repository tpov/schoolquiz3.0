"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {parseQuestionPayload, normalizeModernQuestion, safeRedactedOrSurvey} = require("./arena-public-question");
const {redact} = require("./question-redaction");
const {SINGLE, MULTIPLE, ORDERING, FILL_BLANK, SURVEY, LEGACY_SINGLE_CHOICE} = require("./_question-fixtures");
const binding = {questionId: "q1", difficulty: "HARD"};
function sample(source) {
  const input = {...source, id: "q1", difficulty: "HARD"};
  if (source.type === "Survey") return {publicPayload: JSON.stringify(input), key: {kind: "PARTICIPATION"}};
  return redact(JSON.stringify(input), "HARD", {questionId: "q1", random: () => 0});
}
function accepts(payload, key) { return safeRedactedOrSurvey(payload, {...binding, gradingKey: key}); }

for (const source of [SINGLE, MULTIPLE, ORDERING, FILL_BLANK, SURVEY]) {
  test(`${source.type}: exact emitter schema passes without rewriting payload, order, IDs or key`, () => {
    const split = sample(source); const before = structuredClone(split);
    assert.equal(accepts(split.publicPayload, split.key), true);
    assert.deepEqual(split, before);
  });
  test(`${source.type}: arbitrary root private data and declared-row nested grading data are rejected`, () => {
    const {publicPayload, key} = sample(source); const clean = JSON.parse(publicPayload);
    assert.equal(accepts(JSON.stringify({...clean, futureGrading: {answer: "private"}}), key), false);
    const field = clean.options ? "options" : clean.items ? "items" : "candidates";
    clean[field][0].privateRubric = {correct: true};
    assert.equal(accepts(JSON.stringify(clean), key), false);
  });
}
test("duplicate root keys cannot retain an earlier secret, including escaped-name alias", () => {
  const {publicPayload, key} = sample(SINGLE);
  for (const name of ['"options"', '"opt\\u0069ons"']) {
    const payload = `{${name}:[{"id":"x","text":"x","correctOptionId":"b"}],${publicPayload.slice(1)}`;
    assert.equal(accepts(payload, key), false); assert.equal(parseQuestionPayload(payload), null);
  }
});
test("duplicate nested keys cannot retain a private object under an earlier scalar field", () => {
  const {publicPayload, key} = sample(SINGLE);
  const payload = publicPayload.replace('"id":"a"', '"id":{"grading":"b"},"id":"a"');
  assert.equal(accepts(payload, key), false);
});
test("JSON-looking quoted text, escapes and repeated keys in separate row objects remain legal", () => {
  const split = sample({...SINGLE, text: 'Text with \\ and "options":{}, [true, false] 😀'});
  const spaced = ` \n${JSON.stringify(JSON.parse(split.publicPayload), null, 2)}\t`;
  assert.equal(accepts(spaced, split.key), true);
});
test("missing/null/broken key or cross-question key never makes a redacted question deliverable", () => {
  const split = sample(SINGLE);
  for (const key of [null, {}, {...split.key, questionId: "other"}, {...split.key, correctOptionId: "absent"}]) {
    assert.equal(accepts(split.publicPayload, key), false);
  }
});
test("FillBlank protected segments must satisfy the emitter's existing no-answer-text rule", () => {
  const split = sample(FILL_BLANK); const content = JSON.parse(split.publicPayload);
  assert.equal(accepts(JSON.stringify({...content, protectedTextSegments: ["compiler"]}), split.key), true);
  for (const segment of ["Kotlin", "prefix KOTLIN suffix", " rust "]) {
    assert.equal(accepts(JSON.stringify({...content, protectedTextSegments: [segment]}), split.key), false);
  }
  assert.equal(accepts(JSON.stringify({...content, blanks: [{id: "b1", correctCandidateId: "c1"}]}), split.key), false);
});
test("wrong metadata, row values, duplicate row IDs and missing imageUrl are refused", () => {
  const split = sample(SINGLE); const clean = JSON.parse(split.publicPayload);
  for (const patch of [{id: "other"}, {id: null}, {difficulty: "EASY"}, {difficulty: null},
    {options: [{id: "a", text: "x"}, {id: "a", text: "y"}]}, {options: [{id: "a", text: {answer: true}}]},
    {imageUrl: undefined}]) assert.equal(accepts(JSON.stringify({...clean, ...patch}), split.key), false);
});
test("old redacted content may omit optional metadata but modern Survey cannot", () => {
  const split = sample(SINGLE); const content = JSON.parse(split.publicPayload);
  delete content.id; delete content.difficulty;
  assert.equal(accepts(JSON.stringify(content), split.key), true);
  const survey = JSON.parse(sample(SURVEY).publicPayload); delete survey.id;
  assert.equal(accepts(JSON.stringify(survey), {kind: "PARTICIPATION"}), false);
});
test("modern missing metadata is filled without mutating the real production-shaped fixture", () => {
  assert.equal(Object.hasOwn(SINGLE, "id"), false); assert.equal(Object.hasOwn(SINGLE, "difficulty"), false);
  const before = structuredClone(SINGLE);
  const normalized = normalizeModernQuestion(SINGLE, {questionId: "actual-doc", difficulty: "EASY"});
  assert.equal(normalized.id, "actual-doc"); assert.equal(normalized.difficulty, "EASY");
  assert.equal(normalized.correctOptionId, SINGLE.correctOptionId); assert.equal(normalized.info, SINGLE.info);
  assert.deepEqual(SINGLE, before);
});
test("normalization refuses existing metadata conflicts, including null and blank", () => {
  for (const patch of [{id: "other"}, {id: null}, {id: ""}, {difficulty: "HARD"}, {difficulty: null}, {difficulty: "easy"}]) {
    assert.equal(normalizeModernQuestion({...SINGLE, ...patch}, {questionId: "q1", difficulty: "EASY"}), null);
  }
});
test("legacy dialect remains unchanged rather than pretending to be a modern complete question", () => {
  const legacy = JSON.parse(LEGACY_SINGLE_CHOICE);
  assert.equal(normalizeModernQuestion(legacy, binding), legacy);
  assert.equal(accepts(LEGACY_SINGLE_CHOICE, {kind: "FULL_CONTENT"}), false);
});
test("malformed/nonobject/oversize/deep JSON fails closed without stack overflow", () => {
  for (const payload of ["{", "null", "[]", '"string"', '{"x":' + "[".repeat(70) + "0" + "]".repeat(70) + "}",
    JSON.stringify({text: "x".repeat(500_000)})]) assert.equal(parseQuestionPayload(payload), null);
});
