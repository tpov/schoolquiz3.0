"use strict";

const assert = require("assert");
const {
  TROPHY_VERIFIED,
  GIFT_BOX_TROPHY_NAMES,
  trophyList,
  hasTrophy,
  pickGiftBoxTrophy,
} = require("./trophies.js");

// Stored values are normalised rather than trusted.
assert.deepStrictEqual(trophyList(["b", "a", "b"]), ["a", "b"], "duplicates and order");
assert.deepStrictEqual(trophyList([" a ", "", null, 0]), ["a"], "blanks and nulls dropped");
assert.deepStrictEqual(trophyList(undefined), [], "missing field");
// The field used to be a number. Anything that is not a list yields nothing rather than throwing,
// so a profile written before the change still loads.
assert.deepStrictEqual(trophyList(3), [], "legacy numeric value");

assert.strictEqual(hasTrophy(["verified"], TROPHY_VERIFIED), true);
assert.strictEqual(hasTrophy([], TROPHY_VERIFIED), false);

// The verification tick is not something a box can drop.
assert.ok(
  !GIFT_BOX_TROPHY_NAMES.includes(TROPHY_VERIFIED),
  "a gift box must never grant the verification tick",
);

// A box never hands over a duplicate: holding one badge is the same as holding it twice, so the
// reward would be silently empty.
const owned = [GIFT_BOX_TROPHY_NAMES[0]];
for (let attempt = 0; attempt < 50; attempt += 1) {
  assert.notStrictEqual(pickGiftBoxTrophy(owned), GIFT_BOX_TROPHY_NAMES[0]);
}

assert.strictEqual(
  pickGiftBoxTrophy(GIFT_BOX_TROPHY_NAMES),
  null,
  "a full collection must report that nothing is left, so the caller can pay in something else",
);

assert.strictEqual(pickGiftBoxTrophy([], () => 0), GIFT_BOX_TROPHY_NAMES[0], "injected choice");
// An index past the end is clamped rather than yielding undefined.
assert.strictEqual(pickGiftBoxTrophy([], () => 999), GIFT_BOX_TROPHY_NAMES.at(-1));

console.log("trophies tests passed");
