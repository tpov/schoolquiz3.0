"use strict";

const assert = require("assert");
const {
  FAULT_BAD_CHAR,
  FAULT_LENGTH_MISMATCH,
  FAULT_SKIP_ON_ANSWERED,
  FAULT_WRONG_DIFFICULTY,
  countClaims,
  noClaims,
  overspendRecord,
  overspendVerdict,
  settleClaims,
  validateClaimMask,
} = require("./charge-claims");
const {DEFAULTS, POINTS_PER_CHARGE} = require("./economy-constants");

const HOUR = 60 * 60 * 1000;

function testAPaidSkipBecomesFullyCorrect() {
  const settled = settleClaims("..P.", "9905", 0, 1);
  assert.strictEqual(settled.codeAnswer, "9995");
  assert.deepStrictEqual(settled.paid, [2]);
  assert.strictEqual(settled.plasmaChargesPaid, 1);
}

function testAnUnpaidSkipStaysUnansweredWhichIsWhatItWas() {
  // Неоплаченная заявка не наказывает второй раз: вопрос просто засчитан как неотвеченный.
  const settled = settleClaims("..P.", "9905", 0, 0);
  assert.strictEqual(settled.codeAnswer, "9905");
  assert.deepStrictEqual(settled.unpaid, [2]);
}

function testTheEarliestClaimsArePaidFirst() {
  const settled = settleClaims("P.PP", "0000", 0, 2);
  assert.deepStrictEqual(settled.paid, [0, 2]);
  assert.deepStrictEqual(settled.unpaid, [3]);
  assert.strictEqual(settled.codeAnswer, "9090");
}

function testTheOrderQuestionsWereAskedDecidesWhoGetsPaid() {
  const settled = settleClaims("P.PP", "0000", 0, 1, [3, 2, 0]);
  assert.deepStrictEqual(settled.paid, [3]);
  assert.strictEqual(settled.codeAnswer, "0009");
}

function testAHintTakesPaymentButKeepsTheDigitThePlayerAnswered() {
  const settled = settleClaims("S...", "7999", 3, 0);
  assert.strictEqual(settled.codeAnswer, "7999");
  assert.strictEqual(settled.standardChargesPaid, 1);
}

function testAClientThatScoredItsOwnSkipIsRefused() {
  // Клиент заявляет ответ, которого не давал, — и маска делает это различимым.
  assert.strictEqual(validateClaimMask("P...", "9111", "HARD"), FAULT_SKIP_ON_ANSWERED);
}

function testTheWrongKindForTheDifficultyIsRefused() {
  assert.strictEqual(validateClaimMask("S...", "0111", "HARD"), FAULT_WRONG_DIFFICULTY);
  assert.strictEqual(validateClaimMask("P...", "0111", "EASY"), FAULT_WRONG_DIFFICULTY);
}

function testAMalformedMaskIsRefusedNotPartlyPaid() {
  assert.strictEqual(validateClaimMask("P..", "0111", "HARD"), FAULT_LENGTH_MISMATCH);
  assert.strictEqual(validateClaimMask("P.X.", "0111", "HARD"), FAULT_BAD_CHAR);
  assert.strictEqual(validateClaimMask("P.P.", "0905", "HARD"), null);
  assert.strictEqual(validateClaimMask(noClaims(4), "1234", "EASY"), null);
  // Попытка без маски ведёт себя ровно как сегодня.
  assert.strictEqual(validateClaimMask("", "", "EASY"), null);
  assert.deepStrictEqual(countClaims("S.SP"), {standard: 2, plasma: 1});
}

function testTheOfflineReplayIsCaughtOnce() {
  // Сценарий из постановки: потратить три, переждать восстановление офлайн, потратить три ещё,
  // синхронизироваться. Аккаунт держит три; за сутки восстановился один.
  const verdict = overspendVerdict({
    claimed: 6,
    storedPoints: 3 * POINTS_PER_CHARGE,
    storedUpdatedAtMs: 0,
    windowEndMs: 24 * HOUR,
    rules: DEFAULTS.plasma,
    clockSkewToleranceMs: 0,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.held, 3);
  assert.strictEqual(verdict.regenerated, 1);
  assert.strictEqual(verdict.ceiling, 3, "потолок ограничен maxOwned");
  assert.strictEqual(verdict.surplus, 3);
}

function testASlowSyncIsNotFraud() {
  // Неделя офлайн — длинное окно, и потолок растёт с ним: семь заявок за семь дней честны.
  const verdict = overspendVerdict({
    claimed: 7,
    storedPoints: 0,
    storedUpdatedAtMs: 0,
    windowEndMs: 7 * 24 * HOUR,
    rules: {...DEFAULTS.plasma, maxOwned: 10},
    clockSkewToleranceMs: 0,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.surplus, 0);
}

function testClockSkewToleranceIsMeasuredInRegenerationNotInCharges() {
  const verdict = overspendVerdict({
    claimed: 2,
    storedPoints: POINTS_PER_CHARGE,
    storedUpdatedAtMs: 0,
    windowEndMs: 0,
    rules: DEFAULTS.standard,
    clockSkewToleranceMs: DEFAULTS.standard.regenMs,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.skewAllowance, 1);
  assert.strictEqual(verdict.surplus, 0);
}

function testABalanceAboveTheCeilingCountsAsHeld() {
  // Понижение потолка не конфискует — и здесь то, что аккаунт держал, не объявляется подделкой.
  const verdict = overspendVerdict({
    claimed: 5,
    storedPoints: 5 * POINTS_PER_CHARGE,
    storedUpdatedAtMs: 0,
    windowEndMs: 0,
    rules: {...DEFAULTS.plasma, maxOwned: 3},
    clockSkewToleranceMs: 0,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.surplus, 0);
}

function testTheRecordReconstructsTheFindingWithoutTheAttempts() {
  const verdict = overspendVerdict({
    claimed: 6, storedPoints: 300, storedUpdatedAtMs: 0, windowEndMs: 24 * HOUR,
    rules: DEFAULTS.plasma, clockSkewToleranceMs: 0, pointsPerCharge: POINTS_PER_CHARGE,
  });
  const record = overspendRecord({
    uid: "u1", chargeKind: "plasma", windowStartMs: 1000, windowEndMs: 24 * HOUR,
    storedPoints: 300, storedUpdatedAtMs: 0, constantsVersion: 4, verdict,
    attemptIds: ["a1", "a2", ""], recordedAtMs: 5,
  });
  assert.strictEqual(record.id, `u1_PLASMA_${24 * HOUR}`);
  assert.deepStrictEqual(record.attemptIds, ["a1", "a2"]);
  assert.strictEqual(record.constantsVersion, 4);
  assert.ok(record.reason.includes("излишек 3"), record.reason);
  for (const field of ["held", "regenerated", "ceiling", "allowance", "claimed", "surplus"]) {
    assert.strictEqual(record[field], verdict[field], `в записи нет ${field}`);
  }
}

testAPaidSkipBecomesFullyCorrect();
testAnUnpaidSkipStaysUnansweredWhichIsWhatItWas();
testTheEarliestClaimsArePaidFirst();
testTheOrderQuestionsWereAskedDecidesWhoGetsPaid();
testAHintTakesPaymentButKeepsTheDigitThePlayerAnswered();
testAClientThatScoredItsOwnSkipIsRefused();
testTheWrongKindForTheDifficultyIsRefused();
testAMalformedMaskIsRefusedNotPartlyPaid();
testTheOfflineReplayIsCaughtOnce();
testASlowSyncIsNotFraud();
testClockSkewToleranceIsMeasuredInRegenerationNotInCharges();
testABalanceAboveTheCeilingCountsAsHeld();
testTheRecordReconstructsTheFindingWithoutTheAttempts();

console.log("charge-claims.test.js OK");
