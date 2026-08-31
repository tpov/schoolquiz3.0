"use strict";

const assert = require("assert");
const {
  SMALL_LESSON_SECONDS,
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
  // Tpov's anchor: one lesson passed perfectly first try, both difficulties, funds one unlock.
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

testBandsAreMarginal();
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
