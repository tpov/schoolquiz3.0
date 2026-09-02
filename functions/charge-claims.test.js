"use strict";

const assert = require("assert");
const {
  FAULT_BAD_CHAR,
  FAULT_LENGTH_MISMATCH,
  FAULT_SKIP_ON_ANSWERED,
  FAULT_WRONG_DIFFICULTY,
  askedOrder,
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

function testTheCeilingFollowsTheSlotsTheAccountActuallyOwns() {
  // Считать по одному `maxOwned` — значит не обвинить того, кто заявил больше, чем его слоты
  // вмещают: купил один слот, заявил три, а потолок из таблицы говорит «три, всё в порядке».
  const oneSlot = overspendVerdict({
    claimed: 3, storedPoints: POINTS_PER_CHARGE, storedUpdatedAtMs: 0, windowEndMs: 0,
    rules: DEFAULTS.plasma, ownedSlots: 1, clockSkewToleranceMs: 0, pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(oneSlot.ceiling, 1);
  assert.strictEqual(oneSlot.surplus, 2);

  // Без указания слотов поведение прежнее — потолок из таблицы.
  const unspecified = overspendVerdict({
    claimed: 3, storedPoints: POINTS_PER_CHARGE, storedUpdatedAtMs: 0, windowEndMs: 0,
    rules: DEFAULTS.plasma, clockSkewToleranceMs: 0, pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(unspecified.ceiling, 1, "в базе лежал один заряд, восстановиться не успело");
}

function testPremiumIsNotAuditedForWhatTheServerItselfPaid() {
  // Премиум восстанавливается вдвое быстрее — за сутки два заряда, а не один. Считать потолок по
  // обычному периоду значило бы записать в перерасход то, что сервер только что начислил.
  const day = 24 * HOUR;
  const audited = overspendVerdict({
    claimed: 2, storedPoints: 0, storedUpdatedAtMs: 0, windowEndMs: day,
    rules: {...DEFAULTS.plasma, maxOwned: 3}, ownedSlots: 3,
    regenMs: DEFAULTS.plasma.regenMs / 2, clockSkewToleranceMs: 0, pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(audited.regenerated, 2);
  assert.strictEqual(audited.surplus, 0);
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

function testTheAskedOrderComesFromTheClockNotFromThePosition() {
  // Раннер тасует набор: позиция в строке порядку показа не соответствует, а платить надо за
  // самые ранние заявки.
  const answers = [
    {codeAnswerIndex: 3, answeredAtMs: 10},
    {codeAnswerIndex: 0, answeredAtMs: 30},
    {codeAnswerIndex: 2, answeredAtMs: 20},
  ];
  assert.deepStrictEqual(askedOrder(answers), [3, 2, 0]);
  assert.deepStrictEqual(askedOrder(null), [], "без ответов порядок неизвестен");

  // И этот порядок действительно решает, кому достанется единственный заряд.
  const settled = settleClaims("P.PP", "0000", 0, 1, askedOrder(answers));
  assert.deepStrictEqual(settled.paid, [3]);
}

testTheAskedOrderComesFromTheClockNotFromThePosition();
testAPaidSkipBecomesFullyCorrect();
testAnUnpaidSkipStaysUnansweredWhichIsWhatItWas();
testTheEarliestClaimsArePaidFirst();
testTheOrderQuestionsWereAskedDecidesWhoGetsPaid();
testAHintTakesPaymentButKeepsTheDigitThePlayerAnswered();
testAClientThatScoredItsOwnSkipIsRefused();
testTheWrongKindForTheDifficultyIsRefused();
testAMalformedMaskIsRefusedNotPartlyPaid();
function testFractionsAreNotDroppedTwice() {
  // Полтора заряда в базе и ещё шесть десятых натекло — вместе два целых, и две заявки честны.
  // Порознь округлив, потолок вышел бы в один, и честного игрока записали бы как перерасход.
  const verdict = overspendVerdict({
    claimed: 2,
    storedPoints: 150,
    storedUpdatedAtMs: 0,
    windowEndMs: Math.floor(DEFAULTS.standard.regenMs * 0.6),
    rules: DEFAULTS.standard,
    clockSkewToleranceMs: 0,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.ceiling, 2);
  assert.strictEqual(verdict.surplus, 0);
}

function testTheTankIsTheSlotsOwnedNotTheMaximum() {
  // Лестница продаёт слоты по одному: аккаунт с тремя слотами не мог держать десять.
  const verdict = overspendVerdict({
    claimed: 5,
    storedPoints: 0,
    storedUpdatedAtMs: 0,
    windowEndMs: 100 * HOUR,
    rules: DEFAULTS.standard,
    ownedSlots: 3,
    clockSkewToleranceMs: 0,
    pointsPerCharge: POINTS_PER_CHARGE,
  });
  assert.strictEqual(verdict.ceiling, 3);
  assert.strictEqual(verdict.surplus, 2);
}

testFractionsAreNotDroppedTwice();
testTheTankIsTheSlotsOwnedNotTheMaximum();
testTheOfflineReplayIsCaughtOnce();
testASlowSyncIsNotFraud();
testClockSkewToleranceIsMeasuredInRegenerationNotInCharges();
testABalanceAboveTheCeilingCountsAsHeld();
testTheCeilingFollowsTheSlotsTheAccountActuallyOwns();
testPremiumIsNotAuditedForWhatTheServerItselfPaid();
testTheRecordReconstructsTheFindingWithoutTheAttempts();

console.log("charge-claims.test.js OK");
