"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {evaluateAnswer, computePercentScore, computeStars, scoreDigit} =
  require("./assessment-scoring");
// Read-only: the percent formula exists twice in JavaScript and the two copies are cross-checked
// at the bottom of this file. Requiring result-verification.js is not modifying it.
const {recomputePercentScore} = require("./result-verification");

/**
 * Both scorers are driven from one fixture file, and it lives on the Kotlin side.
 *
 * shared/core/scoring/src/jvmTest/resources/ is the only location Gradle already tracks as an
 * input to :shared:core:scoring:jvmTest and that Kotlin can load off the classpath with no
 * working-directory assumption. Putting it under functions/ instead would mean the Kotlin test
 * reaching up out of its module by relative path — the fragile half of the trade. So the JS suite
 * takes the relative path, which is stable because both paths are inside this one repository.
 *
 * A case added to that JSON is picked up by both suites with no edit here or in the Kotlin test.
 * A missing or empty file is a failure, never a skip: a parity harness that quietly stops
 * comparing is worse than none, because the green tick still says the two agree.
 */
const FIXTURE_PATH = path.join(
  __dirname,
  "../shared/core/scoring/src/jvmTest/resources/scoring-fixtures.json",
);

function loadFixtures() {
  assert.ok(
    fs.existsSync(FIXTURE_PATH),
    `Scoring fixtures missing at ${FIXTURE_PATH}. The Kotlin scorer and this one are only ` +
    "pinned together by that file; without it there is nothing to compare.",
  );
  return JSON.parse(fs.readFileSync(FIXTURE_PATH, "utf8"));
}

/** An array that is absent or empty means the harness would pass by testing nothing. */
function requireCases(fixtures, key) {
  const cases = fixtures[key];
  assert.ok(Array.isArray(cases), `Fixture file has no "${key}" array`);
  assert.ok(cases.length > 0, `Fixture array "${key}" is empty — nothing would be compared`);
  return cases;
}

/**
 * Runs every case before reporting, rather than throwing on the first mismatch.
 *
 * A changed constant drifts whole families of cases at once, and the useful signal is which
 * families — one assertion message naming one case hides that. A case that throws is recorded and
 * the run continues, so a single malformed fixture cannot mask the rest of the file.
 */
function checkEveryCase(label, cases, run) {
  const failures = [];
  for (const testCase of cases) {
    const name = testCase.name || "<unnamed case>";
    try {
      const problem = run(testCase);
      if (problem) failures.push(`  "${name}": ${problem}`);
    } catch (error) {
      failures.push(`  "${name}": threw ${error.message}`);
    }
  }
  assert.ok(
    failures.length === 0,
    `${label}: ${failures.length} of ${cases.length} cases failed against ${FIXTURE_PATH}\n` +
    failures.join("\n"),
  );
}

function testEvaluateAnswerFixtures(fixtures) {
  checkEveryCase("evaluateAnswer", requireCases(fixtures, "evaluateAnswer"), (testCase) => {
    const actual = evaluateAnswer(testCase.content, testCase.answer);
    return actual === testCase.expected ? null : `expected ${testCase.expected}, got ${actual}`;
  });
}

function testComputePercentScoreFixtures(fixtures) {
  // kotlinRejectsCodeAnswer marks input the CodeAnswer value class refuses to hold. It changes
  // nothing here: the server has no such wrapper and still has to produce a number.
  checkEveryCase(
    "computePercentScore",
    requireCases(fixtures, "computePercentScore"),
    (testCase) => {
      const actual = computePercentScore(testCase.codeAnswer);
      return actual === testCase.expected
        ? null
        : `codeAnswer ${JSON.stringify(testCase.codeAnswer)} expected ${testCase.expected}, ` +
          `got ${actual}`;
    },
  );
}

function testComputeStarsFixtures(fixtures) {
  checkEveryCase("computeStars", requireCases(fixtures, "computeStars"), (testCase) => {
    const actual = computeStars(testCase.percentScore, testCase.mode);
    return actual === testCase.expected
      ? null
      : `${testCase.percentScore} percent on ${testCase.mode} expected ${testCase.expected}, ` +
        `got ${actual}`;
  });
}

/**
 * The percent formula exists twice in JavaScript: here and as recomputePercentScore in
 * result-verification.js, which is what index.js:3037 actually calls today. Folding the two into
 * one is deferred to a later slice, and until then nothing but this assertion stops them drifting
 * apart — at which point the server would score an attempt by one formula and verify it by
 * another. Both are held to the same fixtures.
 */
function testTheTwoJavaScriptCopiesAgree(fixtures) {
  checkEveryCase(
    "percent formula cross-check against result-verification.js",
    requireCases(fixtures, "computePercentScore"),
    (testCase) => {
      // Both are JavaScript, so kotlinRejectsCodeAnswer is irrelevant to both: the empty
      // codeAnswer Kotlin cannot hold must still score 0 in each of them.
      const verifier = recomputePercentScore(testCase.codeAnswer);
      if (verifier !== testCase.expected) {
        return `result-verification.js recomputePercentScore returned ${verifier}, ` +
          `the fixture expects ${testCase.expected}`;
      }
      const scorer = computePercentScore(testCase.codeAnswer);
      return verifier === scorer
        ? null
        : `the two JavaScript copies disagree: verifier ${verifier}, scorer ${scorer}`;
    },
  );
}

/**
 * The guards no fixture can reach.
 *
 * Everything below is a shape QuestionContent's init blocks reject, a value the Kotlin value
 * classes make unrepresentable, or a function Kotlin keeps private. None of it can be expressed as
 * a shared fixture, so each guard was verified by deleting it and confirming the fixture suite
 * still passed — which it did, every time. That is exactly why these live here.
 */
function testGuardsNoFixtureCanReach() {
  // scoreDigit is `private` in Scoring.kt, so the parity harness structurally cannot call it.
  // Zero denominator: a MultipleChoice with no correct ids answered with nothing would otherwise
  // divide by zero and, through the clamp, score full marks for having done nothing.
  assert.strictEqual(scoreDigit(0, 0), 1, "a zero denominator must score 1, not divide");
  // The clamp: unclamped this is 21, well outside the 0..9 a Score can hold.
  assert.strictEqual(scoreDigit(5, 2), 9, "a digit above 9 must clamp to 9");

  // Kotlin's correctOptionId is non-nullable. E2's next step redacts a published question by
  // stripping the answer key, so null and absent are both shapes this module will really be given.
  const twoOptions = [{id: "o1", text: "A"}, {id: "o2", text: "B"}];
  const redacted = {type: "SingleChoice", options: twoOptions, correctOptionId: null};
  assert.strictEqual(
    evaluateAnswer(redacted, {type: "single-choice", selected: null}), 1,
    "a redacted answer key must not let an unanswered question score 9",
  );
  assert.strictEqual(
    evaluateAnswer({type: "SingleChoice", options: twoOptions}, {type: "single-choice", selected: null}), 1,
    "an absent answer key must not let an unanswered question score 9",
  );

  // SingleChoice.init requires correctOptionId to be one of the options, so this cannot be a
  // fixture. The off-list id is nulled before comparing, so it can never match itself.
  assert.strictEqual(
    evaluateAnswer(
      {type: "SingleChoice", options: twoOptions, correctOptionId: "o9"},
      {type: "single-choice", selected: "o9"},
    ), 1,
    "an off-list correct id must not be matchable by naming it",
  );

  // FillBlank.init requires every correctCandidateId to be in the pool, so this cannot be a
  // fixture either. A blank whose answer key sits outside the pool must not be credited.
  const outsidePool = {
    type: "FillBlank",
    blanks: [{id: "b1", correctCandidateId: "zz"}],
    candidates: [{id: "c1", text: "x"}],
  };
  assert.strictEqual(
    evaluateAnswer(outsidePool, {type: "fill-blank", filled: {b1: "zz"}}), 1,
    "a correct id outside the candidate pool must not be credited",
  );

  // Malformed rows fall through to a low score rather than crashing, like every other bad shape.
  assert.strictEqual(
    evaluateAnswer(
      {type: "FillBlank", blanks: [null], candidates: [{id: "c1", text: "x"}]},
      {type: "fill-blank", filled: {}},
    ), 1,
    "a null blank row must score, not throw",
  );
  assert.strictEqual(
    evaluateAnswer(
      {type: "SingleChoice", options: [{text: "no id"}], correctOptionId: "o1"},
      {type: "single-choice", selected: "o1"},
    ), 1,
    "an option row with no id must not join the valid-id set",
  );

  // CodeAnswer.kt:12 requires digits only and digitToInt() throws on anything else. Unguarded this
  // returns NaN, which survives every later step and reaches Firestore as null.
  assert.throws(() => computePercentScore("9a9"), /digits only/);
  assert.throws(() => computePercentScore("9.9"), /digits only/);
  assert.throws(() => computePercentScore("-9"), /digits only/);
  // The empty string stays legal and scores 0 — a frozen Matrix row, and a fixture case.
  assert.strictEqual(computePercentScore(""), 0);

  // PercentScore rejects anything outside 0..100 before computeStars ever runs in Kotlin.
  assert.throws(() => computeStars(1000, "EASY"), /out of range/);
  assert.throws(() => computeStars(-3, "EASY"), /out of range/);
  assert.throws(() => computeStars(50.5, "EASY"), /out of range/);
  // Difficulty has exactly two values in Kotlin. Anything else is a crafted payload, and guessing
  // EASY for it would hand out stars the mode never earned.
  assert.throws(() => computeStars(50, "MEDIUM"), /Unknown difficulty/);
  assert.throws(() => computeStars(50, undefined), /Unknown difficulty/);

  // A missing or malformed pair scores 1 rather than throwing, matching the Kotlin else branch.
  assert.strictEqual(evaluateAnswer(null, null), 1);
  assert.strictEqual(evaluateAnswer({}, {}), 1);
  assert.strictEqual(evaluateAnswer({type: "SingleChoice"}, {type: "survey", selected: []}), 1);
}

const fixtures = loadFixtures();
testEvaluateAnswerFixtures(fixtures);
testComputePercentScoreFixtures(fixtures);
testComputeStarsFixtures(fixtures);
testTheTwoJavaScriptCopiesAgree(fixtures);
testGuardsNoFixtureCanReach();

const total = fixtures.evaluateAnswer.length +
  fixtures.computePercentScore.length +
  fixtures.computeStars.length;
console.log(`assessment-scoring tests passed (${total} shared fixture cases)`);
