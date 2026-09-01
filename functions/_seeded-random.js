"use strict";

/**
 * A deterministic `Math.random`-shaped source (mulberry32).
 *
 * `question-redaction.js` takes its shuffle indices from an injected `random` precisely so tests
 * can assert on one concrete shuffle instead of on "something moved" — a property a broken shuffle
 * satisfies too. That only holds if every suite drawing from a given seed draws the same stream,
 * and `redacted-question-fixtures.json` bakes concrete orders in by seed for the Kotlin side to
 * read. So the generator lives here, once: two copies would let an edit to one silently re-baseline
 * every fixture against a different stream, and the fixtures would still look green.
 *
 * Not a CSPRNG and not meant to be. Production draws from `crypto.randomInt`; this exists so a
 * fixture can name an order.
 */
function seeded(seed) {
  let state = seed >>> 0;
  return function random() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

module.exports = {seeded};
