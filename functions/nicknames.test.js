"use strict";

const assert = require("assert");
const {
  REASON_TOO_SHORT,
  REASON_TOO_LONG,
  REASON_UNSUPPORTED,
  REASON_BLOCKED_SYMBOL,
  REASON_BLOCKED_WORD,
  describeNickname,
  sanitizeNickname,
  canonicalNickname,
} = require("./nicknames.js");

const NO_POLICY = {blockedWords: [], blockedSymbols: []};

function reasonFor(value, policy = NO_POLICY) {
  const result = describeNickname(value, policy);
  assert.strictEqual(result.ok, false, `expected ${JSON.stringify(value)} to be rejected`);
  return result.reason;
}

// ─── length ────────────────────────────────────────────────────────────────────────────────────
assert.strictEqual(reasonFor("ab"), REASON_TOO_SHORT);
assert.strictEqual(reasonFor(""), REASON_TOO_SHORT);
assert.strictEqual(reasonFor("   "), REASON_TOO_SHORT, "whitespace is not length");
assert.strictEqual(reasonFor("x".repeat(25)), REASON_TOO_LONG);
assert.strictEqual(describeNickname("abc", NO_POLICY).ok, true, "lower bound");
assert.strictEqual(describeNickname("x".repeat(24), NO_POLICY).ok, true, "upper bound");

// ─── characters that break paths or hide text ──────────────────────────────────────────────────
assert.strictEqual(reasonFor("a\u0000bc"), REASON_UNSUPPORTED, "NUL");
assert.strictEqual(reasonFor("a\u001Fbc"), REASON_UNSUPPORTED, "unit separator");
assert.strictEqual(reasonFor("a/bc"), REASON_UNSUPPORTED, "slash");
assert.strictEqual(reasonFor("a\\bc"), REASON_UNSUPPORTED, "backslash");

// ─── the canonical form uniqueness is judged on ────────────────────────────────────────────────
assert.strictEqual(canonicalNickname("Олег"), "олег", "case folds");
// Full-width characters look different and read the same; without NFKC they would be a separate
// name, which is exactly how somebody impersonates an existing player.
assert.strictEqual(canonicalNickname("ＡＢＣ"), "abc", "full-width folds to plain");
assert.strictEqual(canonicalNickname("  a   b  "), "a b", "runs of whitespace collapse");
assert.strictEqual(
  canonicalNickname("Олег"), canonicalNickname("ОЛЕГ  "),
  "two spellings of one name must not be two names",
);

const ok = describeNickname("  Олег  Петров ", NO_POLICY);
assert.deepStrictEqual(
  {nickname: ok.nickname, canonical: ok.canonical},
  {nickname: "Олег Петров", canonical: "олег петров"},
  "the displayed name keeps its case, the canonical one does not",
);

// ─── policy ────────────────────────────────────────────────────────────────────────────────────
const policy = {blockedWords: ["дурак", "spam"], blockedSymbols: ["☠", "@"]};

assert.strictEqual(reasonFor("me@you", policy), REASON_BLOCKED_SYMBOL);
assert.strictEqual(reasonFor("dark☠lord", policy), REASON_BLOCKED_SYMBOL);
assert.strictEqual(reasonFor("большой дурак", policy), REASON_BLOCKED_WORD);
assert.strictEqual(reasonFor("ДУРАК", policy), REASON_BLOCKED_WORD, "case does not evade");
// Spacing a word out is the oldest way past a filter, so the spaceless form is checked too.
assert.strictEqual(reasonFor("s p a m", policy), REASON_BLOCKED_WORD);
assert.strictEqual(describeNickname("Олег", policy).ok, true, "a clean name still passes");

// A symbol check runs before the word check, so the reported reason is the first rule broken
// rather than whichever happens to be tested last.
assert.strictEqual(reasonFor("spam@here", policy), REASON_BLOCKED_SYMBOL);

// ─── the trimming helper the saving path still uses ────────────────────────────────────────────
assert.strictEqual(sanitizeNickname("  Олег  ", null), "Олег");
assert.strictEqual(sanitizeNickname("ab", null), null);
assert.strictEqual(sanitizeNickname("a/b", null), null);
assert.strictEqual(sanitizeNickname("", "запасной"), "запасной");

console.log("nicknames tests passed");
