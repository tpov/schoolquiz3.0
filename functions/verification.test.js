"use strict";

const assert = require("assert");
const {
  DECISION_APPROVED,
  DECISION_REJECTED,
  VerificationError,
  normalizeVerificationDetails,
  normalizeTelegram,
  normalizeBirthday,
  canSubmitRequest,
  normalizeDecision,
} = require("./verification.js");

const NOW = Date.parse("2026-08-22T00:00:00.000Z");

function rejects(fn, note) {
  assert.throws(fn, VerificationError, note);
}

// ─── telegram ──────────────────────────────────────────────────────────────────────────────────
// Stored without the @ so a reviewer always sees one form, whichever way it was typed.
assert.strictEqual(normalizeTelegram("@tpov_dev"), "tpov_dev");
assert.strictEqual(normalizeTelegram("  tpov_dev "), "tpov_dev");
rejects(() => normalizeTelegram("abc"), "too short");
rejects(() => normalizeTelegram("has spaces"), "spaces");
rejects(() => normalizeTelegram("привет_мир"), "non-latin");
rejects(() => normalizeTelegram(""), "empty");

// ─── birthday ──────────────────────────────────────────────────────────────────────────────────
assert.strictEqual(normalizeBirthday("1990-05-07", NOW), "1990-05-07");
rejects(() => normalizeBirthday("07.05.1990", NOW), "wrong format");
// Matches the pattern but is not a day that exists — the reason parsing is not enough on its own.
rejects(() => normalizeBirthday("2026-02-31", NOW), "impossible date");
rejects(() => normalizeBirthday("2026-13-01", NOW), "month 13");
rejects(() => normalizeBirthday("2025-08-22", NOW), "an infant");
rejects(() => normalizeBirthday("1800-01-01", NOW), "implausibly old");

// ─── the whole set ─────────────────────────────────────────────────────────────────────────────
const valid = {realName: "Олег", birthday: "1990-05-07", city: "Киев", telegram: "@tpov_dev"};
assert.deepStrictEqual(normalizeVerificationDetails(valid, NOW), {
  realName: "Олег",
  birthday: "1990-05-07",
  city: "Киев",
  telegram: "tpov_dev",
});

for (const field of ["realName", "city"]) {
  rejects(() => normalizeVerificationDetails({...valid, [field]: "   "}, NOW), `${field} blank`);
  rejects(() => normalizeVerificationDetails({...valid, [field]: "x".repeat(101)}, NOW), `${field} long`);
}
rejects(() => normalizeVerificationDetails({}, NOW), "nothing supplied");

// ─── when a request may be filed ───────────────────────────────────────────────────────────────
assert.deepStrictEqual(canSubmitRequest(null, false), {allowed: true}, "first attempt");

// A rejection is meant to be answered, so applying again is the point.
assert.deepStrictEqual(
  canSubmitRequest({status: DECISION_REJECTED}, false), {allowed: true},
  "after a rejection",
);

// Replacing a waiting request would let somebody rewrite their details while a reviewer reads them.
assert.deepStrictEqual(
  canSubmitRequest({status: "PENDING"}, false),
  {allowed: false, reason: "already-pending"},
);
assert.deepStrictEqual(
  canSubmitRequest({status: DECISION_APPROVED}, false),
  {allowed: false, reason: "already-verified"},
);
assert.deepStrictEqual(
  canSubmitRequest(null, true),
  {allowed: false, reason: "already-verified"},
  "already holds the tick",
);

// ─── decisions ─────────────────────────────────────────────────────────────────────────────────
assert.strictEqual(normalizeDecision("approved"), DECISION_APPROVED);
assert.strictEqual(normalizeDecision(" REJECTED "), DECISION_REJECTED);
rejects(() => normalizeDecision("MAYBE"), "not a decision");
rejects(() => normalizeDecision(""), "empty decision");

console.log("verification tests passed");
