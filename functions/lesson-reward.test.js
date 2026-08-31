"use strict";

const assert = require("assert");
const {
  SMALL_LESSON_SECONDS,
  POOL_SIZE,
  canonicalQuestionId,
  questionCharsCount,
  questionAllocatedSeconds,
  lessonAllocatedSeconds,
  weightedPercent,
  attemptPoints,
  attemptReward,
  lessonUnlockPrice,
} = require("./lesson-reward");

/** The reference lesson the scale is anchored to. */
const SMALL = SMALL_LESSON_SECONDS;
/** Its hard counterpart: same questions, the tighter hard coefficient (0.24 against 0.36). */
const SMALL_HARD = 480;

function testBandsAreMarginal() {
  assert.strictEqual(weightedPercent(0), 0);
  assert.strictEqual(weightedPercent(10), 10); // 10 x 1
  assert.strictEqual(weightedPercent(25), 25); // 25 x 1
  assert.strictEqual(weightedPercent(50), 75); // + 25 x 2
  assert.strictEqual(weightedPercent(70), 135); // + 20 x 3
  assert.strictEqual(weightedPercent(90), 215); // + 20 x 4
  assert.strictEqual(weightedPercent(100), 265); // + 10 x 5
}

function testBandsClampRatherThanThrow() {
  assert.strictEqual(weightedPercent(-10), 0);
  assert.strictEqual(weightedPercent(1000), 265);
  assert.strictEqual(weightedPercent(undefined), 0);
}

function testTheAnchorHolds() {
  // The whole scale rests on this: ten percent in the 1x band, on the reference lesson, first
  // attempt. Doubling for new percent makes it two nolics, so ten percent at plain tariff is one.
  const {nolics} = attemptReward({
    previousBestPercent: 0, percent: 10, isHard: false, allocatedSeconds: SMALL,
  });
  assert.strictEqual(nolics, 2);
  assert.strictEqual(attemptPoints(0, 10), 20);
}

function testSizeScalesThePayment() {
  const small = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: SMALL,
  });
  const double = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: SMALL * 2,
  });
  assert.strictEqual(double.nolics, small.nolics * 2);
  // A lesson with no allocated time is worth nothing, rather than worth a flat fee.
  assert.strictEqual(
    attemptReward({previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: 0}).nolics,
    0,
  );
}

function testHardPaysTripleAtTheSameSize() {
  const easy = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: SMALL,
  });
  const hard = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: true, allocatedSeconds: SMALL,
  });
  assert.strictEqual(hard.nolics, easy.nolics * 3);
}

function testOnlyNewPercentPaysDouble() {
  // 0 -> 60 on a first attempt: all of it is new.
  const first = attemptPoints(0, 60);
  assert.strictEqual(first, weightedPercent(60) * 2);

  // 60 -> 100 later: forty points are new and doubled, the first sixty are a repeat at a tenth.
  const improved = attemptPoints(60, 100);
  const expected = (weightedPercent(100) - weightedPercent(60)) * 2 + weightedPercent(60) * 0.1;
  assert.strictEqual(improved, expected);
}

function testRepeatingAKnownLessonIsNotAWayToEarn() {
  const perfectFirst = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: SMALL,
  }).nolics;
  const repeat = attemptReward({
    previousBestPercent: 100, percent: 100, isHard: false, allocatedSeconds: SMALL,
  }).nolics;
  // Something, so practising is not punished; a twentieth of the first pass, so it is not a faucet.
  assert.ok(repeat > 0, "a repeat should still pay something");
  assert.ok(repeat * 15 < perfectFirst, `repeat ${repeat} is too close to first pass ${perfectFirst}`);
}

function testFallingShortOfYourBestPaysOnlyTheRepeatRate() {
  // Scoring 40 when the best is 100 earns nothing new; it is a repeat of the forty.
  const points = attemptPoints(100, 40);
  assert.strictEqual(points, weightedPercent(40) * 0.1);
}

function testUnlockPriceEqualsOnePerfectFirstPass() {
  // The anchor as stated: one lesson passed perfectly first try, both difficulties, funds one
  // unlock of both halves. Note that unlockPrice(UNLOCK_LESSON) charges only the easy half now,
  // because that is all opening a lesson grants — so in practice a perfect lesson funds more than
  // one lesson unlock. The primitive below still holds the ratio the anchor fixed.
  const easy = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: false, allocatedSeconds: SMALL,
  }).nolics;
  const hard = attemptReward({
    previousBestPercent: 0, percent: 100, isHard: true, allocatedSeconds: SMALL_HARD,
  }).nolics;
  const price = lessonUnlockPrice({
    easyAllocatedSeconds: SMALL, hardAllocatedSeconds: SMALL_HARD,
  });
  assert.strictEqual(price, easy + hard);
}

function testUnlockPriceNeverFree() {
  assert.strictEqual(lessonUnlockPrice({easyAllocatedSeconds: 0, hardAllocatedSeconds: 0}), 1);
}

function testUnlockPriceGrowsWithTheLesson() {
  const short = lessonUnlockPrice({easyAllocatedSeconds: SMALL, hardAllocatedSeconds: SMALL_HARD});
  const long = lessonUnlockPrice({
    easyAllocatedSeconds: SMALL * 3, hardAllocatedSeconds: SMALL_HARD * 3,
  });
  assert.strictEqual(long, short * 3);
}

// --- allocated time: what a player would actually be asked ---

const EASY_Q = {difficulty: "EASY", text: "x".repeat(100), options: []};
const HARD_Q = {difficulty: "HARD", text: "x".repeat(100), options: []};

function rows(n, content, idPrefix = "q") {
  return Array.from({length: n}, (_, i) => ({id: `${idPrefix}${i}`, content}));
}

function testCharsCountMatchesTheRunner() {
  // computeCharsCount in RunnerLogic.kt: question text + every option/item/candidate text,
  // plus a flat hundred for an image.
  assert.strictEqual(questionCharsCount({text: "abcde"}), 5);
  assert.strictEqual(questionCharsCount({text: "abcde", imageUrl: "https://x"}), 105);
  assert.strictEqual(
    questionCharsCount({text: "ab", options: [{text: "cd"}, {text: "e"}]}),
    5,
  );
  assert.strictEqual(questionCharsCount({text: "ab", items: [{text: "cde"}]}), 5);
  assert.strictEqual(questionCharsCount({text: "ab", candidates: [{text: "cde"}]}), 5);
  assert.strictEqual(questionCharsCount(null), 0);
}

function testPerQuestionSecondsMatchTheTimer() {
  // seconds = max(5, round(chars x k)), k = 0.36 easy / 0.24 hard.
  assert.strictEqual(questionAllocatedSeconds({text: "x".repeat(100)}, false), 36);
  assert.strictEqual(questionAllocatedSeconds({text: "x".repeat(100)}, true), 24);
  // The floor bites on anything tiny.
  assert.strictEqual(questionAllocatedSeconds({text: "x"}, false), 5);
}

function testOnlyTheAskedDifficultyCounts() {
  const mixed = [...rows(3, EASY_Q, "e"), ...rows(4, HARD_Q, "h")];
  assert.strictEqual(lessonAllocatedSeconds(mixed, false), 3 * 36);
  assert.strictEqual(lessonAllocatedSeconds(mixed, true), 4 * 24);
}

function testTranslatedVariantsCollapseToOne() {
  // The regression this guards: a lesson translated three ways holds three documents per question,
  // the runner shows one, and counting all three trebled both the reward and the unlock price.
  const one = [{id: "q1", content: EASY_Q}];
  const translated = [
    {id: "q1", content: EASY_Q},
    {id: "q1__ru", content: EASY_Q},
    {id: "q1__en", content: EASY_Q},
  ];
  assert.strictEqual(lessonAllocatedSeconds(translated, false), lessonAllocatedSeconds(one, false));
  // A double underscore that is not a language tag is part of the id, not a variant marker.
  const notALanguage = [{id: "q1", content: EASY_Q}, {id: "q1__2024", content: EASY_Q}];
  assert.strictEqual(lessonAllocatedSeconds(notALanguage, false), 2 * 36);
}

function testCanonicalIdRules() {
  assert.strictEqual(canonicalQuestionId("q1__ru"), "q1");
  assert.strictEqual(canonicalQuestionId("q1__pt-br"), "q1");
  assert.strictEqual(canonicalQuestionId("q1"), "q1");
  assert.strictEqual(canonicalQuestionId("q1__2024"), "q1__2024");
  assert.strictEqual(canonicalQuestionId("__ru"), "__ru");
  assert.strictEqual(canonicalQuestionId(undefined), "");
}

function testArchivedQuestionsAreWorthNothing() {
  const withArchived = [
    {id: "q1", content: EASY_Q},
    {id: "q2", content: EASY_Q, archived: true},
  ];
  assert.strictEqual(lessonAllocatedSeconds(withArchived, false), 36);
}

function testWorthStopsGrowingPastThePool() {
  // The runner draws POOL_SIZE questions however many the lesson holds, so worth caps there.
  const exactly = lessonAllocatedSeconds(rows(POOL_SIZE, EASY_Q), false);
  const double = lessonAllocatedSeconds(rows(POOL_SIZE * 2, EASY_Q), false);
  assert.strictEqual(exactly, POOL_SIZE * 36);
  assert.strictEqual(double, exactly);
}

function testSplittingIntoTinyQuestionsStopsPayingPastThePool() {
  // A hundred characters as one question, against the same text cut into fifty. The floor still
  // makes the split worth more up to the pool, but past it the cap holds the line.
  const whole = lessonAllocatedSeconds([{id: "q", content: {difficulty: "EASY", text: "x".repeat(100)}}], false);
  const split = lessonAllocatedSeconds(
    Array.from({length: 50}, (_, i) => ({id: `s${i}`, content: {difficulty: "EASY", text: "xx"}})),
    false,
  );
  assert.strictEqual(whole, 36);
  assert.strictEqual(split, POOL_SIZE * 5);
}

function testEmptyAndJunkInputs() {
  assert.strictEqual(lessonAllocatedSeconds([], false), 0);
  assert.strictEqual(lessonAllocatedSeconds(null, false), 0);
  assert.strictEqual(lessonAllocatedSeconds([null, undefined], false), 0);
  // A row that is the content itself, with no wrapper, still counts.
  assert.strictEqual(lessonAllocatedSeconds([{id: "q1", ...EASY_Q}], false), 36);
}


testBandsAreMarginal();
testCharsCountMatchesTheRunner();
testPerQuestionSecondsMatchTheTimer();
testOnlyTheAskedDifficultyCounts();
testTranslatedVariantsCollapseToOne();
testCanonicalIdRules();
testArchivedQuestionsAreWorthNothing();
testWorthStopsGrowingPastThePool();
testSplittingIntoTinyQuestionsStopsPayingPastThePool();
testEmptyAndJunkInputs();
testBandsClampRatherThanThrow();
testTheAnchorHolds();
testSizeScalesThePayment();
testHardPaysTripleAtTheSameSize();
testOnlyNewPercentPaysDouble();
testRepeatingAKnownLessonIsNotAWayToEarn();
testFallingShortOfYourBestPaysOnlyTheRepeatRate();
testUnlockPriceEqualsOnePerfectFirstPass();
testUnlockPriceNeverFree();
testUnlockPriceGrowsWithTheLesson();
console.log("lesson-reward.test.js OK");
