"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  MODE_COEFFICIENTS,
  assessmentReward,
  buildWorthBasis,
  cappedPoolMean,
  roundHalfUpRatio,
  syntheticFixtureBasis,
  validateFrozenBasis,
  weightedTariff,
} = require("./exam-reward");

const THEME_BASIS = {mode: "ThemeTest", TE: 180, TH: 360, Q: 1260, countEasy: 5, countHard: 15,
  sumEasy: 180, sumHard: 360, ruleVersion: 1};
const FINAL_BASIS = {mode: "FinalExam", TE: 0, TH: 480, Q: 1440, countEasy: 0, countHard: 20,
  sumEasy: 0, sumHard: 9600, ruleVersion: 1};

test("coefficients are the accepted mode table", () => {
  assert.deepEqual(MODE_COEFFICIENTS.ThemeTest, {c: 3, r: 0});
  assert.deepEqual(MODE_COEFFICIENTS.FinalExam, {c: 10, r: 0.1});
});

test("weighted tariff matches the marginal bands and W(100)=265", () => {
  assert.equal(weightedTariff(0), 0);
  assert.equal(weightedTariff(25), 25);
  assert.equal(weightedTariff(50), 75);
  assert.equal(weightedTariff(60), 105);
  assert.equal(weightedTariff(70), 135);
  assert.equal(weightedTariff(90), 215);
  assert.equal(weightedTariff(99), 260);
  assert.equal(weightedTariff(100), 265);
});

test("half-up ratio rounds .5 up and nothing else", () => {
  assert.equal(roundHalfUpRatio(1, 2), 1);
  assert.equal(roundHalfUpRatio(3975, 10), 398, "финальный полу-нолик идёт вверх");
  assert.equal(roundHalfUpRatio(11, 2), 6);
  assert.equal(roundHalfUpRatio(9, 2), 5);
  assert.throws(() => roundHalfUpRatio(-1, 2), TypeError);
  assert.throws(() => roundHalfUpRatio(1, 0), TypeError);
});

test("the capped pool mean multiplies the whole-pool average by the draw size", () => {
  // Прибор «difficulty-means-round-before-weight»: средние округляются до взвешивания.
  assert.equal(cappedPoolMean({draw: 5, sum: 35, count: 6}), 29);
  assert.equal(cappedPoolMean({draw: 15, sum: 86, count: 17}), 76);
});

test("theme basis is ordinary worth: five EASY and fifteen HARD", () => {
  const basis = buildWorthBasis({mode: "ThemeTest",
    poolEasySeconds: [36, 36, 36, 36, 36], poolHardSeconds: new Array(15).fill(24)});
  assert.deepEqual(
    {TE: basis.TE, TH: basis.TH, Q: basis.Q},
    {TE: 180, TH: 360, Q: 1260},
  );
});

test("final basis has no easy worth and is priced by the 20-question hard mean", () => {
  const basis = buildWorthBasis({mode: "FinalExam", poolEasySeconds: [],
    poolHardSeconds: new Array(20).fill(24)});
  assert.equal(basis.TE, 0);
  assert.equal(basis.TH, 480);
  assert.equal(basis.Q, 1440);
});

test("basis below per-question minimums refuses before any payout", () => {
  // 20 вопросов по 5 секунд дают TH=100 — ровно минимум, проходят; по 4 — TH=80, отказ.
  assert.equal(buildWorthBasis({mode: "FinalExam", poolEasySeconds: [],
    poolHardSeconds: new Array(20).fill(5)}).TH, 100);
  assert.throws(() => buildWorthBasis({mode: "FinalExam", poolEasySeconds: [],
    poolHardSeconds: new Array(20).fill(4)}), /INVALID_REWARD_BASIS/);
  assert.throws(() => validateFrozenBasis({...THEME_BASIS, TE: 0, TH: 75, Q: 225}), /INVALID_REWARD_BASIS/);
  assert.throws(() => validateFrozenBasis({...FINAL_BASIS, TE: 10}), /TE=0/);
});

test("a frozen basis that disagrees with itself is refused, not silently paid", () => {
  assert.throws(() => validateFrozenBasis({...FINAL_BASIS, Q: 999}), /Q disagrees/);
  assert.throws(() => validateFrozenBasis({...FINAL_BASIS, ruleVersion: 2}), /rule version/);
  assert.equal(validateFrozenBasis(FINAL_BASIS), FINAL_BASIS);
});

test("theme first pass pays x3 on new progress only", () => {
  const reward = assessmentReward({mode: "ThemeTest", percent: 100, accountedPercent: 0,
    basis: THEME_BASIS});
  assert.deepEqual({nolics: reward.nolics, skillPoints: reward.skillPoints, bNext: reward.bNext},
    {nolics: 139, skillPoints: 1391, bNext: 100});
  // Неоплаченный провал темы не двигает учтённый максимум и не платит.
  const failed = assessmentReward({mode: "ThemeTest", percent: 0, accountedPercent: 0,
    basis: THEME_BASIS});
  assert.deepEqual({nolics: failed.nolics, skillPoints: failed.skillPoints, bNext: failed.bNext},
    {nolics: 0, skillPoints: 0, bNext: 0});
});

test("final pays every attempt: new x10 above b, repeated 0.1 below it", () => {
  // 90 → 70 и 90 — повторный прогресс; 95 — новое только над 90.
  assert.deepEqual(pick(assessmentReward({mode: "FinalExam", percent: 70, accountedPercent: 90,
    basis: FINAL_BASIS})), {nolics: 3, skillPoints: 27, bNext: 90});
  assert.deepEqual(pick(assessmentReward({mode: "FinalExam", percent: 90, accountedPercent: 90,
    basis: FINAL_BASIS})), {nolics: 4, skillPoints: 43, bNext: 90});
  assert.deepEqual(pick(assessmentReward({mode: "FinalExam", percent: 95, accountedPercent: 90,
    basis: FINAL_BASIS})), {nolics: 54, skillPoints: 543, bNext: 95});
  assert.deepEqual(pick(assessmentReward({mode: "FinalExam", percent: 0, accountedPercent: 90,
    basis: FINAL_BASIS})), {nolics: 0, skillPoints: 0, bNext: 90}, "провал финала всё же двигает b=max(b,p)");
});

test("zero after rounding still advances the accounted reference", () => {
  // Синтетическая крошечная база — законный шов приборов, не настоящая цена.
  const tiny = syntheticFixtureBasis({mode: "FinalExam", TE: 0, TH: 1, Q: 3, ruleVersion: 1});
  const reward = assessmentReward({mode: "FinalExam", percent: 100, accountedPercent: 99,
    basis: tiny, syntheticBasis: true});
  assert.deepEqual({nolics: reward.nolics, skillPoints: reward.skillPoints, bNext: reward.bNext},
    {nolics: 0, skillPoints: 0, bNext: 100});
});

test("XP and nolics round independently from the same unrounded amount", () => {
  // Прибор final-half-nolic-rounds-up: XP 3975 точно, нолики 397.5 → 398.
  const reward = assessmentReward({mode: "FinalExam", percent: 100, accountedPercent: 0,
    basis: {mode: "FinalExam", TE: 0, TH: 360, Q: 1080, countEasy: 0, countHard: 20,
      sumEasy: 0, sumHard: 7200, ruleVersion: 1}});
  assert.deepEqual({nolics: reward.nolics, skillPoints: reward.skillPoints}, {nolics: 398,
    skillPoints: 3975});
});

test("a HARD final is effective new 30 / repeated 0.3, not a whole-attempt x30", () => {
  // Обычный HARD-тариф сидит внутри базы (TH по 0.24-секундам): проверяем размещение
  // коэффициентов — newPoints×10, повтор×0.1, без единого множителя на попытку.
  const reward = assessmentReward({mode: "FinalExam", percent: 50, accountedPercent: 0,
    basis: FINAL_BASIS});
  assert.equal(reward.newPoints, weightedTariff(50));
  assert.equal(reward.repeatedPoints, 0);
  assert.equal(reward.skillPoints, Math.round((10 * weightedTariff(50) * FINAL_BASIS.Q / 720)));
});

test("retry of a settled sitting computes the same receipt numbers", () => {
  const first = assessmentReward({mode: "FinalExam", percent: 95, accountedPercent: 90,
    basis: FINAL_BASIS});
  const retry = assessmentReward({mode: "FinalExam", percent: 95, accountedPercent: 90,
    basis: FINAL_BASIS});
  assert.deepEqual(retry, first);
});

test("garbage inputs fail loudly instead of falling back to zero", () => {
  assert.throws(() => assessmentReward({mode: "ThemeTest", percent: 101, accountedPercent: 0,
    basis: THEME_BASIS}), TypeError);
  assert.throws(() => assessmentReward({mode: "ThemeTest", percent: 60, accountedPercent: -1,
    basis: THEME_BASIS}), TypeError);
  assert.throws(() => assessmentReward({mode: "OrdinaryLesson", percent: 60, accountedPercent: 0,
    basis: THEME_BASIS}), TypeError);
});

function pick({nolics, skillPoints, bNext}) {
  return {nolics, skillPoints, bNext};
}
