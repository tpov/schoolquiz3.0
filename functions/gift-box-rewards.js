"use strict";

/** Extracted unchanged outcomes/ranges from index.js:5032–5117, logos:222–231 and trophies.js.
 * Entropy is captured once on the SERVER before a transaction, never supplied by the client.
 * Reusing the same draws across Firestore retries prevents response-dependent rerolls. The
 * committed opening receipt is the permanent authority for later network retries.
 */
const crypto = require("node:crypto");
const {pickGiftBoxTrophy} = require("./trophies");

const GIFT_BOX_LOGO_NAMES = Object.freeze([
  "Golden Crown Logo", "Diamond Star Logo", "Phoenix Wings Logo", "Dragon Scale Logo",
  "Crystal Orb Logo", "Thunder Bolt Logo", "Mystic Eye Logo", "Royal Shield Logo",
]);

function createGiftBoxEntropy() {
  return Array.from({length: 8}, () => Number(crypto.randomBytes(8).readBigUInt64BE() >> 11n) / 2 ** 53);
}

function generateGiftBoxReward(ownedTrophies, entropy) {
  if (!Array.isArray(entropy) || entropy.length !== 8 || Array.from(entropy).some((n) => typeof n !== "number" || !Number.isFinite(n) || n < 0 || n >= 1)) {
    throw new TypeError("Gift box requires eight trusted uniform draws in [0,1)");
  }
  let cursor = 0;
  const roll = () => entropy[cursor++];
  const integer = (min, max) => min + Math.floor(roll() * (max - min + 1));
  const first = roll();
  const second = roll();
  if (first > 0.05) return {type: "addNolics", amount: integer(500, 2000)};
  if (second <= 0.95) {
    switch (integer(0, 2)) {
      case 0: return {type: "addNolics", amount: integer(7000, 13000)};
      case 1: return integer(0, 1) === 0 ? {type: "addNolics", amount: integer(7000, 13000)} : {type: "addGold", amount: 1};
      case 2: return {type: "datePremium", amount: 86400};
      default: throw new Error("Unreachable gift box mid tier");
    }
  }
  switch (integer(0, 4)) {
    case 0: return {type: "logo", amount: 1, itemName: GIFT_BOX_LOGO_NAMES[integer(0, GIFT_BOX_LOGO_NAMES.length - 1)]};
    case 1: {
      const trophy = integer(0, 1) === 0 ? null : pickGiftBoxTrophy(ownedTrophies, (length) => integer(0, length - 1));
      return trophy ? {type: "trophy", amount: 1, itemName: trophy} : {type: "addGold", amount: integer(10, 20)};
    }
    case 2: return {type: "addGold", amount: integer(10, 20)};
    case 3: return {type: "datePremium", amount: 86400 * 30};
    case 4: return {type: "addNolics", amount: integer(50000, 200000)};
    default: throw new Error("Unreachable gift box rare tier");
  }
}

module.exports = {GIFT_BOX_LOGO_NAMES, createGiftBoxEntropy, generateGiftBoxReward};
