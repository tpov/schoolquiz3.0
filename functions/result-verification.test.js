"use strict";

const assert = require("assert");
const {recomputePercentScore, isWellFormedCodeAnswer} = require("./result-verification");

/**
 * Reference values are derived from computePercentScore in RunnerLogic.kt:
 *   nonZero = codeAnswer.filter { it != '0' }
 *   sum     = nonZero.sumOf { (it.digitToInt() - 1) * 100 / 8 }   // Int division
 *   result  = sum / nonZero.length                                 // Int division
 * Both divisions truncate, which is exactly where a naive JS port would drift and start
 * rejecting honest attempts.
 */

function testPerDigitTruncationMatchesKotlin() {
  // (d - 1) * 100 / 8 with Int division: 0, 12, 25, 37, 50, 62, 75, 87, 100
  const expectedPerDigit = {1: 0, 2: 12, 3: 25, 4: 37, 5: 50, 6: 62, 7: 75, 8: 87, 9: 100};
  for (const [digit, expected] of Object.entries(expectedPerDigit)) {
    assert.strictEqual(
      recomputePercentScore(digit),
      expected,
      `digit ${digit} should score ${expected}`,
    );
  }
}

function testAverageAlsoTruncates() {
  // (0 + 12) / 2 = 6
  assert.strictEqual(recomputePercentScore("12"), 6);
  // (0 + 100) / 2 = 50
  assert.strictEqual(recomputePercentScore("19"), 50);
  // (12 + 100) / 2 = 56
  assert.strictEqual(recomputePercentScore("29"), 56);
  // (12 + 87 + 100) / 3 = 199 / 3 = 66 (not 66.33)
  assert.strictEqual(recomputePercentScore("289"), 66);
}

function testUnshownQuestionsAreIgnored() {
  // '0' means the question was never shown and must not dilute the average.
  assert.strictEqual(recomputePercentScore("0090"), 100);
  assert.strictEqual(recomputePercentScore("900000000"), 100);
  assert.strictEqual(recomputePercentScore("0900000009"), 100);
}

function testEmptyAndAllZeroScoreZero() {
  assert.strictEqual(recomputePercentScore(""), 0);
  assert.strictEqual(recomputePercentScore("0"), 0);
  assert.strictEqual(recomputePercentScore("00000"), 0);
}

function testPerfectAndFailedRuns() {
  // A full 20-question perfect run.
  assert.strictEqual(recomputePercentScore("9".repeat(20)), 100);
  // A full 20-question run of wrong answers.
  assert.strictEqual(recomputePercentScore("1".repeat(20)), 0);
}

function testFabricatedScoreDoesNotMatchItsCodeAnswer() {
  // The exploit this check exists for: claim 100 while the answers say otherwise.
  assert.notStrictEqual(recomputePercentScore("1".repeat(20)), 100);
  // An empty codeAnswer can never justify a positive score.
  assert.strictEqual(recomputePercentScore(""), 0);
}

function testWellFormedCodeAnswer() {
  assert.ok(isWellFormedCodeAnswer(""));
  assert.ok(isWellFormedCodeAnswer("0123456789"));
  assert.ok(!isWellFormedCodeAnswer("9a9"));
  assert.ok(!isWellFormedCodeAnswer("9.9"));
  assert.ok(!isWellFormedCodeAnswer("-9"));
  assert.ok(!isWellFormedCodeAnswer("९")); // non-ASCII digit
}

testPerDigitTruncationMatchesKotlin();
testAverageAlsoTruncates();
testUnshownQuestionsAreIgnored();
testEmptyAndAllZeroScoreZero();
testPerfectAndFailedRuns();
testFabricatedScoreDoesNotMatchItsCodeAnswer();
testWellFormedCodeAnswer();

console.log("result-verification tests passed");
