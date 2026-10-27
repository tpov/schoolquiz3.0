"use strict";

// Цитата платы для диалога подачи (история 4.11): та же формула, что платит приёмка, и строгая
// форма тела. Модуль чистый — Firestore не нужен.

const assert = require("node:assert/strict");
const test = require("node:test");
const {DEFAULT_TARIFFS, normalizeReviewFeeTariffs} = require("./review-fee");
const {parseReviewFeeQuoteRequest, quoteReviewFee} = require("./review-fee-quote");

function parse(payload) {
  return parseReviewFeeQuoteRequest(payload);
}

test("body: questId and a positive question count are required", () => {
  assert.deepEqual(parse({questId: "quest-1", questionCount: 7}), {ok: true, questId: "quest-1", questionCount: 7});
  for (const bad of [null, {}, {questId: "quest-1"}, {questionCount: 7}, {questId: "", questionCount: 1},
    {questId: "a/b", questionCount: 1}, {questId: ".", questionCount: 1}, {questId: "quest-1", questionCount: 0},
    {questId: "quest-1", questionCount: -3}, {questId: "quest-1", questionCount: 2.5},
    {questId: "quest-1", questionCount: "7"}]) {
    const result = parse(bad);
    assert.equal(result.ok, false, `expected refusal for ${JSON.stringify(bad)}`);
    assert.equal(typeof result.reason, "string");
  }
});

test("initial quote: flat tariff regardless of question count", () => {
  const tariffs = normalizeReviewFeeTariffs(null);
  const quote = quoteReviewFee({tariffs, priorCycle: false, questionCount: 12});
  assert.deepEqual(quote, {
    view: "review-fee-quote-v1",
    priorCycle: false,
    tariff: "initial",
    feeNolics: DEFAULT_TARIFFS.initialSubmissionNolics,
    tariffRevision: tariffs.revision,
  });
});

test("recheck quote: per-question tariff times the count", () => {
  const tariffs = normalizeReviewFeeTariffs({initialSubmissionNolics: 1200, recheckQuestionNolics: 150, revision: 4});
  const quote = quoteReviewFee({tariffs, priorCycle: true, questionCount: 7});
  assert.deepEqual(quote, {
    view: "review-fee-quote-v1",
    priorCycle: true,
    tariff: "recheck",
    feeNolics: 150 * 7,
    tariffRevision: 4,
  });
});

test("quote refuses a zero-question recheck instead of quoting zero", () => {
  const tariffs = normalizeReviewFeeTariffs(null);
  assert.throws(() => quoteReviewFee({tariffs, priorCycle: true, questionCount: 0}), RangeError);
});

test("quote carries the configured revision, not a client guess", () => {
  const tariffs = normalizeReviewFeeTariffs({revision: 9});
  const quote = quoteReviewFee({tariffs, priorCycle: false, questionCount: 1});
  assert.equal(quote.tariffRevision, 9);
});
