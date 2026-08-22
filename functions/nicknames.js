"use strict";

/**
 * The rules a nickname must satisfy, in one place and without throwing.
 *
 * They used to live inside a validator that raised an HttpsError the moment something was wrong,
 * which suits saving and suits nothing else. Telling somebody their name is taken while they type
 * needs an answer, not an exception — and writing that check separately would mean two sets of
 * rules that drift apart, so the saving path now goes through this too.
 *
 * Reasons are stable codes rather than sentences: the caller decides what to say and in which
 * language, and a test can pin which rule fired instead of matching prose.
 */

const MIN_LENGTH = 3;
const MAX_LENGTH = 24;

/** Control characters and slashes: they break document paths and hide the real text. */
const UNSUPPORTED_PATTERN = /[\u0000-\u001F\/\\]/;

const REASON_TOO_SHORT = "too-short";
const REASON_TOO_LONG = "too-long";
const REASON_UNSUPPORTED = "unsupported-characters";
const REASON_BLOCKED_SYMBOL = "blocked-symbol";
const REASON_BLOCKED_WORD = "blocked-word";

/** Trims and collapses runs of whitespace, so "a  b" and "a b" are not two different names. */
function tidyNickname(value) {
  return String(value === null || value === undefined ? "" : value)
    .trim()
    .replace(/\s+/g, " ");
}

/**
 * The form uniqueness is judged on. Compatibility-normalised so full-width "ＡＢＣ" cannot sit
 * beside "abc" as a separate name, and lowercased so case alone never makes a name free.
 */
function canonicalNickname(value) {
  // Normalised before the whitespace is collapsed, not after: NFKC turns some exotic spaces into
  // ordinary ones, and collapsing first would leave them standing.
  return String(value === null || value === undefined ? "" : value)
    .normalize("NFKC")
    .trim()
    .replace(/\s+/g, " ")
    .toLowerCase();
}

function describeNickname(value, policy) {
  const nickname = tidyNickname(value);
  if (nickname.length < MIN_LENGTH) return {ok: false, reason: REASON_TOO_SHORT};
  if (nickname.length > MAX_LENGTH) return {ok: false, reason: REASON_TOO_LONG};
  if (UNSUPPORTED_PATTERN.test(nickname)) return {ok: false, reason: REASON_UNSUPPORTED};

  const blockedSymbols = (policy && policy.blockedSymbols) || [];
  const blockedWords = (policy && policy.blockedWords) || [];

  const normalized = nickname.normalize("NFKC");
  if (blockedSymbols.some((symbol) => symbol && normalized.includes(symbol))) {
    return {ok: false, reason: REASON_BLOCKED_SYMBOL};
  }

  // Checked against the spaceless form as well, so "b a d" cannot smuggle "bad" past the filter.
  const canonical = canonicalNickname(nickname);
  const compact = canonical.replace(/\s+/g, "");
  const hitsBlockedWord = blockedWords.some((word) => {
    if (!word) return false;
    const compactWord = word.replace(/\s+/g, "");
    return canonical.includes(word) || (compactWord && compact.includes(compactWord));
  });
  if (hitsBlockedWord) return {ok: false, reason: REASON_BLOCKED_WORD};

  return {ok: true, nickname, canonical};
}

/** Kept for the callers that only want the cleaned text, with a fallback when it will not do. */
function sanitizeNickname(value, fallback) {
  const nickname = tidyNickname(value);
  if (nickname.length < MIN_LENGTH || nickname.length > MAX_LENGTH) return fallback;
  if (UNSUPPORTED_PATTERN.test(nickname)) return fallback;
  return nickname;
}

module.exports = {
  MIN_LENGTH,
  MAX_LENGTH,
  REASON_TOO_SHORT,
  REASON_TOO_LONG,
  REASON_UNSUPPORTED,
  REASON_BLOCKED_SYMBOL,
  REASON_BLOCKED_WORD,
  describeNickname,
  sanitizeNickname,
  canonicalNickname,
};
