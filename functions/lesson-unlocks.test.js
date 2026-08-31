"use strict";

const assert = require("assert");
const {
  UNLOCK_LESSON,
  UNLOCK_HARD_MODE,
  unlockPrice,
  spendNolics,
  unlockDocId,
  readUnlocks,
  withUnlock,
} = require("./lesson-unlocks");
const {lessonUnlockPrice} = require("./lesson-reward");

const SMALL = {easyAllocatedSeconds: 720, hardAllocatedSeconds: 480};

function testEachKindIsPricedOnWhatItGrants() {
  // Buying the lesson buys both difficulties, so it costs both — and the client must open both,
  // or the price would again be for something not delivered.
  assert.strictEqual(unlockPrice(UNLOCK_LESSON, SMALL), lessonUnlockPrice(SMALL));
  // Hard mode alone, for a lesson already open, is the hard half and therefore cheaper.
  assert.strictEqual(
    unlockPrice(UNLOCK_HARD_MODE, SMALL),
    lessonUnlockPrice({easyAllocatedSeconds: 0, hardAllocatedSeconds: SMALL.hardAllocatedSeconds}),
  );
  assert.ok(unlockPrice(UNLOCK_HARD_MODE, SMALL) < unlockPrice(UNLOCK_LESSON, SMALL));
  // A bigger lesson costs more to skip.
  const big = {easyAllocatedSeconds: 2160, hardAllocatedSeconds: 1440};
  assert.strictEqual(unlockPrice(UNLOCK_LESSON, big), unlockPrice(UNLOCK_LESSON, SMALL) * 3);
}

function testUnlockPriceRejectsUnknownKinds() {
  assert.strictEqual(unlockPrice("theme-test", SMALL), null);
  assert.strictEqual(unlockPrice("", SMALL), null);
  assert.strictEqual(unlockPrice(undefined, SMALL), null);
  // Guards against a prototype key being read as a price.
  assert.strictEqual(unlockPrice("constructor", SMALL), null);
  assert.strictEqual(unlockPrice("toString", SMALL), null);
}

function testSpendChargesOnlyWhenAffordable() {
  assert.deepStrictEqual(spendNolics(10, 10), {affordable: true, nolics: 0});
  assert.deepStrictEqual(spendNolics(25, 20), {affordable: true, nolics: 5});
  assert.deepStrictEqual(spendNolics(9, 10), {affordable: false, nolics: 9});
  assert.deepStrictEqual(spendNolics(0, 10), {affordable: false, nolics: 0});
}

function testSpendNormalisesJunk() {
  assert.deepStrictEqual(spendNolics(-5, 10), {affordable: false, nolics: 0});
  assert.deepStrictEqual(spendNolics("30", "20"), {affordable: true, nolics: 10});
  assert.deepStrictEqual(spendNolics(10.9, 10), {affordable: true, nolics: 0});
  assert.deepStrictEqual(spendNolics(undefined, 10), {affordable: false, nolics: 0});
  // A free unlock is still a valid purchase, and must not go negative.
  assert.deepStrictEqual(spendNolics(5, 0), {affordable: true, nolics: 5});
}

function testDocIdSeparatesTheTwoPurchases() {
  // Buying the lesson open and buying its hard mode are different things on the same lesson;
  // sharing an id would make the second purchase look like a repeat of the first and be free.
  assert.notStrictEqual(unlockDocId(UNLOCK_LESSON, "l1"), unlockDocId(UNLOCK_HARD_MODE, "l1"));
  assert.strictEqual(unlockDocId(UNLOCK_LESSON, "l1"), "lesson:l1");
  assert.strictEqual(unlockDocId(UNLOCK_HARD_MODE, "l1"), "hardMode:l1");
}

function testReadUnlocksSurvivesEveryShapeTheFieldTakes() {
  // The field is simply absent on a player who has never bought a lesson.
  assert.deepStrictEqual(readUnlocks(undefined), []);
  assert.deepStrictEqual(readUnlocks(null), []);
  assert.deepStrictEqual(readUnlocks("lesson:l1"), []);
  assert.deepStrictEqual(readUnlocks([]), []);
  // The ordinary case: keys pass through unchanged.
  assert.deepStrictEqual(readUnlocks(["lesson:l1", "hardMode:l1"]), ["lesson:l1", "hardMode:l1"]);
  // Junk is dropped rather than coerced — a key the client cannot match unlocks nothing.
  assert.deepStrictEqual(readUnlocks(["lesson:l1", 7, null, {}, " ", ""]), ["lesson:l1"]);
  // arrayUnion cannot write a duplicate, but a hand-edited document can hold one.
  assert.deepStrictEqual(readUnlocks(["lesson:l1", "lesson:l1"]), ["lesson:l1"]);
}

function testPurchaseBalanceKeepsTheUnlocksItWasGiven() {
  // The regression this guards: the client applies the returned balance wholesale, so a balance
  // that answers "no unlocks" shuts every lesson the player has already bought.
  const owned = ["lesson:l1", "hardMode:l1"];
  assert.deepStrictEqual(withUnlock(owned, "lesson:l2"), ["lesson:l1", "hardMode:l1", "lesson:l2"]);
  // The key just bought rides back in the balance, so the lesson opens without a resync.
  assert.ok(withUnlock([], "lesson:l1").includes("lesson:l1"));
  // A repeat purchase is a no-op, and must not double the key it already holds.
  assert.deepStrictEqual(withUnlock(owned, "lesson:l1"), owned);
  // The caller's array is left alone — it is read again after the response is built.
  const before = [...owned];
  withUnlock(owned, "lesson:l3");
  assert.deepStrictEqual(owned, before);
}

testEachKindIsPricedOnWhatItGrants();
testUnlockPriceRejectsUnknownKinds();
testSpendChargesOnlyWhenAffordable();
testSpendNormalisesJunk();
testDocIdSeparatesTheTwoPurchases();
testReadUnlocksSurvivesEveryShapeTheFieldTakes();
testPurchaseBalanceKeepsTheUnlocksItWasGiven();
console.log("lesson-unlocks.test.js OK");
