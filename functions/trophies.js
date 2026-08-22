"use strict";

/**
 * Trophies are named badges, not a running total.
 *
 * They started as a counter, which could say how many a player had but never which ones — so a
 * badge that has to be recognisable, like the verification tick, had nowhere to live. Naming them
 * gives every badge an identity and makes ownership a set: you either hold one or you do not, and
 * holding it twice means nothing.
 */

/** Awarded only by a human check of who somebody is. Never dropped by a gift box. */
const TROPHY_VERIFIED = "verified";

/** The pool a gift box may draw from. Deliberately excludes TROPHY_VERIFIED. */
const GIFT_BOX_TROPHY_NAMES = [
  "first_steps",
  "night_owl",
  "early_bird",
  "quick_wit",
  "steady_hand",
  "collector",
  "explorer",
  "polyglot",
];

/** Normalises whatever is stored into a clean list of names, tolerating the old numeric field. */
function trophyList(value) {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  for (const entry of value) {
    const name = String(entry || "").trim();
    if (name) seen.add(name);
  }
  return Array.from(seen).sort();
}

function hasTrophy(value, name) {
  return trophyList(value).includes(name);
}

/**
 * Picks a gift-box trophy the player does not already hold.
 *
 * Returns null once they hold the lot — the caller then owes them something else, because a box
 * that silently grants a duplicate is a box that gave nothing.
 */
function pickGiftBoxTrophy(owned, randomIndex) {
  const held = new Set(trophyList(owned));
  const available = GIFT_BOX_TROPHY_NAMES.filter((name) => !held.has(name));
  if (available.length === 0) return null;
  const index = typeof randomIndex === "function"
    ? randomIndex(available.length)
    : Math.floor(Math.random() * available.length);
  return available[Math.min(Math.max(index, 0), available.length - 1)];
}

module.exports = {
  TROPHY_VERIFIED,
  GIFT_BOX_TROPHY_NAMES,
  trophyList,
  hasTrophy,
  pickGiftBoxTrophy,
};
