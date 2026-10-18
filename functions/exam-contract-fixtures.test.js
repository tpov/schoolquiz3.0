"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {assessmentReward, buildWorthBasis, validateFrozenBasis, syntheticFixtureBasis,
  weightedTariff} = require("./exam-reward");
const {correctedPercent, passedAgainst, themeStarsTenths} = require("./exam-scoring");

/**
 * EXAM-TECH-1 становится исполняющимся тестом.
 *
 * Контрактные приборы (`assessment-contract-fixtures.json`) — оракул C1/C2: взвешенные полосы,
 * независимое округление валют, нулевая выплата, двигающая учтённый максимум, первый проход темы
 * после неоплаченных провалов, финал 90→70→90→95, коррекции в обе стороны, неотвеченный
 * знаменатель, вложенное усечение, все-исключены, захваченный порог. Каждый случай выполняется
 * здесь по имени; расхождение — красная сборка, а не примечание в документе.
 */

const FIXTURES = require("../_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/assessment-contract-fixtures.json");

/** Согласная прибору замороженная основа: TE/TH из случая, число слотов по режиму. */
function frozenBasis(c) {
  return {
    mode: c.mode,
    TE: c.TE,
    TH: c.TH,
    Q: c.TE + 3 * c.TH,
    countEasy: c.mode === "ThemeTest" ? 5 : 0,
    countHard: 20,
    sumEasy: 0,
    sumHard: c.TH * 20,
    ruleVersion: 1,
  };
}

test("contract version under test", () => {
  assert.equal(FIXTURES.contractVersion, "EXAM-TECH-1");
});

test(`weightedTariff: all ${FIXTURES.weightedTariff.length} endpoints`, () => {
  for (const endpoint of FIXTURES.weightedTariff) {
    assert.equal(weightedTariff(endpoint.percent), endpoint.weighted,
      `W(${endpoint.percent}) = ${endpoint.weighted}`);
  }
  assert.equal(weightedTariff(100), 265);
});

test(`rewardCases: all ${FIXTURES.rewardCases.length} pay exactly the contract amounts`, () => {
  for (const c of FIXTURES.rewardCases) {
    const reward = assessmentReward({mode: c.mode, percent: c.p, accountedPercent: c.b,
      basis: frozenBasis(c)});
    assert.deepEqual(
      {nolics: reward.nolics, skillPoints: reward.skillPoints, bNext: reward.bNext},
      c.expected,
      `${c.id}: p=${c.p} b=${c.b} TE=${c.TE} TH=${c.TH}`,
    );
  }
});

test(`worthCases: all ${FIXTURES.worthCases.length} price the pool, not the draw or the timer`, () => {
  for (const c of FIXTURES.worthCases) {
    const basis = buildWorthBasis({mode: "ThemeTest",
      poolEasySeconds: c.easySeconds, poolHardSeconds: c.hardSeconds});
    assert.deepEqual({TE: basis.TE, TH: basis.TH, Q: basis.Q}, c.expected, c.id);
  }
});

test(`correctionCases: all ${FIXTURES.correctionCases.length} recompute through the shared scorer`, () => {
  for (const c of FIXTURES.correctionCases) {
    if (c.expected.state === "UNSCORABLE_ALL_EXCLUDED") {
      const all = correctedPercent(c.digits, c.exclude);
      assert.equal(all.state, "UNSCORABLE_ALL_EXCLUDED", c.id);
      assert.equal(all.percent, null);
      assert.equal(all.denominator, 0);
      continue;
    }
    const recomputed = correctedPercent(c.digits, c.exclude);
    assert.equal(recomputed.denominator, c.expected.denominator, c.id);
    assert.equal(recomputed.percent, c.expected.percent, c.id);
    assert.equal(passedAgainst(recomputed.percent, c.capturedPassMark), c.expected.passed,
      `${c.id}: passed against captured ${c.capturedPassMark}`);
    assert.equal(themeStarsTenths(recomputed.percent), c.expected.starsTenths, c.id);
  }
});

test(`arithmeticOnlyCases: ${FIXTURES.arithmeticOnlyCases.length} synthetic rounding seam`, () => {
  for (const c of FIXTURES.arithmeticOnlyCases) {
    assert.equal(c.arithmeticOnly, true, "синтетический прибор помечен как арифметический");
    // Крошечная основа недостижима настоящим пулу — конструктор приборов помечает это явно.
    const reward = assessmentReward({mode: c.mode, percent: c.p, accountedPercent: c.b,
      basis: syntheticFixtureBasis(frozenBasis(c)), syntheticBasis: true});
    assert.deepEqual(
      {nolics: reward.nolics, skillPoints: reward.skillPoints, bNext: reward.bNext},
      c.expected, c.id);
    // А настоящая проверка основы её отвергает: синтетика живёт только в приборах.
    assert.throws(() => validateFrozenBasis(frozenBasis(c)), /INVALID_REWARD_BASIS/, c.id);
  }
});

test(`invalidBasisCases: ${FIXTURES.invalidBasisCases.length} refuse with no payout or reference update`, () => {
  for (const c of FIXTURES.invalidBasisCases) {
    assert.throws(() => validateFrozenBasis(frozenBasis(c)),
      /INVALID_REWARD_BASIS/, `${c.id}: ${c.expected}`);
  }
});

test("zero-rounded payout still advances the accounted reference (C1 seam)", () => {
  const c = FIXTURES.arithmeticOnlyCases[0];
  const reward = assessmentReward({mode: c.mode, percent: c.p, accountedPercent: c.b,
    basis: syntheticFixtureBasis(frozenBasis(c)), syntheticBasis: true});
  assert.equal(reward.nolics, 0);
  assert.equal(reward.skillPoints, 0);
  assert.equal(reward.bNext, 100, "ноль после округления не обнуляет учтённый максимум");
});

test("the first theme pass after unpaid failures pays once with b=0", () => {
  const c = FIXTURES.rewardCases.find((row) => row.id === "theme-first-pass-after-unpaid-failures");
  const reward = assessmentReward({mode: c.mode, percent: c.p, accountedPercent: c.b,
    basis: frozenBasis(c)});
  assert.deepEqual({nolics: reward.nolics, skillPoints: reward.skillPoints, bNext: reward.bNext},
    c.expected);
});
