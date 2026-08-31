"use strict";

const {lessonUnlockPrice} = require("./lesson-reward");

/**
 * Buying a lesson open.
 *
 * Inside a course the lessons open one at a time: the next is shut until the previous is passed.
 * Nolics buy past that — any shut lesson, not only the next one — and separately buy a lesson's
 * hard mode, which is otherwise earned by answering all of its easy questions correctly.
 *
 * The two are priced apart because one unlock would otherwise hand over both difficulties at once.
 *
 * What buying does NOT do is earn stars. A bought lesson left unplayed keeps its zero, so the
 * chain stops exactly where it was and a whole theme cannot be bought open ahead of its test.
 *
 * Pure, so it can be tested without firebase-admin.
 */

/** Buys past the sequential gate for one lesson. */
const UNLOCK_LESSON = "lesson";
/** Buys that lesson's hard mode, normally earned by clearing its easy questions. */
const UNLOCK_HARD_MODE = "hardMode";

/**
 * The price of one unlock, in nolics, or null when the kind is not something that can be bought.
 *
 * Not a flat figure: it is computed from the lesson's allocated time, the same quantity the reward
 * is computed from, so a longer lesson costs more to skip and pays more to finish. Flat across
 * progress, though — nolics are a motivational currency rather than a traded one, and charging
 * more for a later lesson would only punish a player who came for one particular theme.
 *
 * Each kind is priced on what it grants: buying the lesson buys both difficulties and costs both;
 * buying hard mode alone, for a lesson already open, costs the hard half.
 */
function unlockPrice(kind, {easyAllocatedSeconds, hardAllocatedSeconds}) {
  if (kind === UNLOCK_LESSON) {
    // Buying a lesson buys the whole lesson, both difficulties, so it costs both. What makes that
    // fair rather than a shortcut is the payout: a bought lesson still has to be played to earn
    // anything, and playing it is what the price is measured against.
    return lessonUnlockPrice({easyAllocatedSeconds, hardAllocatedSeconds});
  }
  if (kind === UNLOCK_HARD_MODE) {
    return lessonUnlockPrice({easyAllocatedSeconds: 0, hardAllocatedSeconds});
  }
  return null;
}

/**
 * Charges `price` nolics against `balance`.
 *
 * `affordable` false leaves the balance untouched — the caller decides what that means, the same
 * shape spendLifePoints uses so the two read alike at the call site.
 */
function spendNolics(nolics, price) {
  const current = Math.max(0, Math.trunc(Number(nolics) || 0));
  const cost = Math.max(0, Math.trunc(Number(price) || 0));
  if (current < cost) return {affordable: false, nolics: current};
  return {affordable: true, nolics: current - cost};
}

/** Document id for one unlock, so a repeat purchase of the same thing is a no-op, not a charge. */
function unlockDocId(kind, lessonId) {
  return `${kind}:${lessonId}`;
}

/**
 * The unlock keys held on a user document, as a clean list of strings.
 *
 * The field is simply absent on a player who has never bought one, so its absence has to read as
 * an empty list rather than as an error. Junk entries are dropped instead of coerced: a key the
 * client cannot match against `kind:lessonId` would sit in the set forever, unlocking nothing.
 */
function readUnlocks(value) {
  if (!Array.isArray(value)) return [];
  const keys = [];
  for (const entry of value) {
    if (typeof entry !== "string") continue;
    const key = entry.trim();
    if (key && !keys.includes(key)) keys.push(key);
  }
  return keys;
}

/**
 * `owned` with `key` added.
 *
 * The write uses arrayUnion, whose result the transaction cannot read back, so the balance we
 * hand the client has to be told about the key separately — otherwise the purchase returns a
 * balance in which the lesson just paid for is still shut.
 */
function withUnlock(owned, key) {
  return owned.includes(key) ? [...owned] : [...owned, key];
}

module.exports = {
  UNLOCK_LESSON,
  UNLOCK_HARD_MODE,
  unlockPrice,
  spendNolics,
  unlockDocId,
  readUnlocks,
  withUnlock,
};
