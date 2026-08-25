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
  nicknamePricing,
  splitSalePrice,
  DEFAULT_EXTRA_NICKNAME_PRICE,
  DEFAULT_SALE_COMMISSION_PERCENT,
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

// ─── цены и комиссия ───────────────────────────────────────────────────────────────────────────
// The gold economy is not calibrated yet, so both knobs come from config rather than from code.
assert.deepStrictEqual(nicknamePricing(null), {
  extraNicknamePrice: DEFAULT_EXTRA_NICKNAME_PRICE,
  saleCommissionPercent: DEFAULT_SALE_COMMISSION_PERCENT,
});
assert.deepStrictEqual(
  nicknamePricing({extraNicknamePrice: 5, saleCommissionPercent: 25}),
  {extraNicknamePrice: 5, saleCommissionPercent: 25},
);
// Nonsense in the config falls back rather than pricing something at NaN.
assert.strictEqual(nicknamePricing({extraNicknamePrice: -1}).extraNicknamePrice, DEFAULT_EXTRA_NICKNAME_PRICE);
assert.strictEqual(nicknamePricing({saleCommissionPercent: 500}).saleCommissionPercent, DEFAULT_SALE_COMMISSION_PERCENT);
assert.strictEqual(nicknamePricing({extraNicknamePrice: 0}).extraNicknamePrice, 0, "free is a valid price");

// The halves must always add back up to the price. Rounding each independently would mint or
// destroy a coin on every other sale — invisible per trade, impossible to reconcile in aggregate.
for (const price of [0, 1, 3, 7, 10, 99, 100, 12345]) {
  for (const percent of [0, 1, 10, 33, 50, 100]) {
    const split = splitSalePrice(price, percent);
    assert.strictEqual(
      split.commission + split.sellerGets, split.total,
      `${price} at ${percent}% does not reconcile: ${JSON.stringify(split)}`,
    );
    assert.ok(split.commission >= 0 && split.sellerGets >= 0, "no negative half");
  }
}
assert.deepStrictEqual(splitSalePrice(100, 10), {total: 100, commission: 10, sellerGets: 90});
// A price too small for the percentage rounds the commission to nothing rather than to a coin the
// buyer never paid.
assert.deepStrictEqual(splitSalePrice(7, 10), {total: 7, commission: 0, sellerGets: 7});
assert.deepStrictEqual(splitSalePrice(-5, 10), {total: 0, commission: 0, sellerGets: 0});


// ── prices ──────────────────────────────────────────────────────────────────
{
  const {nicknameAskingPrice, splitSalePrice, MIN_LISTING_PRICE} = require("./nicknames.js");

  // Short names are the scarce ones and never come free, not even as an account's first.
  assert.strictEqual(nicknameAskingPrice("abc", false), 10);
  assert.strictEqual(nicknameAskingPrice("abcd", false), 10);
  assert.strictEqual(nicknameAskingPrice("abcd", true), 10);

  // Five is the awkward middle: plentiful enough to be cheap, short enough not to be given away.
  assert.strictEqual(nicknameAskingPrice("abcde", false), 1);

  // A first long name is free — charging to stop being UserH9A4W2 would charge for signing up.
  assert.strictEqual(nicknameAskingPrice("abcdef", false), 0);
  assert.strictEqual(nicknameAskingPrice("abcdef", true), 1);

  assert.strictEqual(nicknameAskingPrice("", false), 0);
  assert.strictEqual(MIN_LISTING_PRICE, 1);

  // The house takes nothing it cannot round to a coin, so cheap lots pay their seller in full.
  assert.strictEqual(splitSalePrice(1, 10).commission, 0);
  assert.strictEqual(splitSalePrice(9, 10).commission, 0);
  assert.strictEqual(splitSalePrice(10, 10).commission, 1);
  assert.strictEqual(splitSalePrice(25, 10).commission, 2);
  assert.strictEqual(splitSalePrice(100, 10).commission, 10);

  // Both halves always add back to the price: rounding each apart would mint or burn a coin.
  for (const price of [1, 7, 10, 13, 99, 1234]) {
    const split = splitSalePrice(price, 10);
    assert.strictEqual(split.commission + split.sellerGets, price);
  }
}

console.log("nicknames tests passed");
