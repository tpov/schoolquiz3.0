"use strict";

/**
 * Консервация платного цикла проверки — чистая арифметика AD-10 (история 4.7, CAP-4).
 *
 * Модуль без Firestore: сюита гоняется везде, где есть node, и держит те ветки, которые у денег
 * не имеют права на исключение — вырожденную консервацию «fee = 0 + burn» (4.18/4.19: писателей
 * наград у конвейера нет), целочисленность (никаких floating-point), раздачу остаточных десятых
 * по убыванию остатка с ничьей по стабильному id и перенос кошелька.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  CALCULATION_VERSION,
  DEFAULT_TARIFFS,
  TENTHS_PER_NOLIC,
  buildSettlementManifest,
  normalizeReviewFeeTariffs,
  reviewFeeNolics,
  settleReviewFeeTenths,
  walletCreditForEntitlement,
} = require("./review-fee");

function unit(unitId, levelTenths) {
  const [lessonId, reviewerUid] = unitId.split("/");
  return {
    unitId,
    lessonId,
    reviewerUid,
    levelTenths,
    evidenceRecordId: `${unitId}-record`,
    createdAtMs: 1,
  };
}


test("tariffs default to the owner-accepted prices and refuse broken values loudly", () => {
  assert.deepEqual(normalizeReviewFeeTariffs(null), {
    initialSubmissionNolics: 1000,
    recheckQuestionNolics: 100,
    revision: 1,
  });
  assert.equal(normalizeReviewFeeTariffs({recheckQuestionNolics: 40}).recheckQuestionNolics, 40);
  assert.throws(() => normalizeReviewFeeTariffs({initialSubmissionNolics: -1}), RangeError);
  assert.throws(() => normalizeReviewFeeTariffs({initialSubmissionNolics: 10.5}), RangeError);
  assert.throws(() => normalizeReviewFeeTariffs({initialSubmissionNolics: "1000"}), RangeError);
});

test("fee: initial full submission 1000, recheck 100 per question, never per stage or reviewer", () => {
  const tariffs = normalizeReviewFeeTariffs(null);
  assert.equal(reviewFeeNolics({tariffs, priorCycle: false, questionCount: 5}), 1000);
  assert.equal(reviewFeeNolics({tariffs, priorCycle: true, questionCount: 3}), 300);
  assert.equal(reviewFeeNolics({tariffs, priorCycle: true, questionCount: 1}), DEFAULT_TARIFFS.recheckQuestionNolics);
  assert.throws(() => reviewFeeNolics({tariffs, priorCycle: false, questionCount: 0}), RangeError);
});

test("ΣLi below the fee: every level is paid whole, the remainder burns", () => {
  const result = settleReviewFeeTenths({
    feeTenths: 1000,
    units: [unit("lesson/alpha", 100), unit("lesson/beta", 200)],
  });
  assert.deepEqual(result.allocations.map((a) => a.entitlementTenths), [100, 200]);
  assert.equal(result.sumEntitlementTenths, 300);
  assert.equal(result.burnTenths, 700);
  assert.equal(1000, result.sumEntitlementTenths + result.burnTenths);
});

test("ΣLi equal to the fee: everything is paid, nothing burns", () => {
  const result = settleReviewFeeTenths({
    feeTenths: 300,
    units: [unit("lesson/alpha", 100), unit("lesson/beta", 200)],
  });
  assert.equal(result.sumEntitlementTenths, 300);
  assert.equal(result.burnTenths, 0);
});

test("ΣLi above the fee: proportional floors, leftover tenths descend by remainder", () => {
  // 100 десятых на уровни 700 и 600 (сумма 1300): floor(70000/1300)=53 (остаток 1100),
  // floor(60000/1300)=46 (остаток 200). Один остаточный десятый уходит большему остатку — alpha.
  const result = settleReviewFeeTenths({
    feeTenths: 100,
    units: [unit("lesson/alpha", 700), unit("lesson/beta", 600)],
  });
  assert.deepEqual(result.allocations.map((a) => [a.unitId, a.entitlementTenths, a.remainderTenths]), [
    ["lesson/alpha", 54, 1100],
    ["lesson/beta", 46, 200],
  ]);
  assert.equal(result.burnTenths, 0);
  assert.equal(result.sumEntitlementTenths, 100);
});

test("remainder tie is broken by the stable unit id ascending", () => {
  // Фонд 101, уровни 300/300: доли по 50, остатки по 300 — ничья, лишний десятый уходит
  // лексикографически первому unitId.
  const result = settleReviewFeeTenths({
    feeTenths: 101,
    units: [unit("lesson/zeta", 300), unit("lesson/alpha", 300)],
  });
  const byId = new Map(result.allocations.map((a) => [a.unitId, a.entitlementTenths]));
  assert.deepEqual([byId.get("lesson/alpha"), byId.get("lesson/zeta")], [51, 50]);
  assert.equal(result.sumEntitlementTenths, 101);
  assert.equal(result.burnTenths, 0);
});

test("no units: the whole fund burns; zero fee pays nothing and burns nothing", () => {
  const empty = settleReviewFeeTenths({feeTenths: 1000, units: []});
  assert.equal(empty.burnTenths, 1000);
  assert.equal(empty.sumEntitlementTenths, 0);

  const free = settleReviewFeeTenths({feeTenths: 0, units: [unit("lesson/alpha", 200)]});
  assert.deepEqual(free.allocations.map((a) => a.entitlementTenths), [0]);
  assert.equal(free.burnTenths, 0);
});

test("products stay exact far beyond the floating-point safe range", () => {
  // Произведения 1e10 × 6e9 = 6e19 перерастают 2^53 на три порядка: деление в числах с плавающей
  // запятой потеряло бы десятые. Сами доли остаются в безопасном диапазоне Number.
  const feeTenths = 10000000000;
  const units = [unit("lesson/big", 6000000000), unit("lesson/small", 5000000000)];
  const result = settleReviewFeeTenths({feeTenths, units});
  assert.ok(feeTenths * 6000000000 > Number.MAX_SAFE_INTEGER, "the product really leaves the safe range");
  const total = 11000000000n;
  const bigShare = Number((BigInt(feeTenths) * 6000000000n) / total);
  const bigRemainder = Number((BigInt(feeTenths) * 6000000000n) % total);
  const smallShare = Number((BigInt(feeTenths) * 5000000000n) / total);
  const smallRemainder = Number((BigInt(feeTenths) * 5000000000n) % total);
  assert.equal(feeTenths - bigShare - smallShare, 1, "exactly one leftover tenth to distribute");
  assert.ok(bigRemainder > smallRemainder, "the larger remainder earns the leftover in this fixture");
  const byId = new Map(result.allocations.map((a) => [a.unitId, a.entitlementTenths]));
  assert.equal(byId.get("lesson/big"), bigShare + 1);
  assert.equal(byId.get("lesson/small"), smallShare);
  assert.equal(result.sumEntitlementTenths + result.burnTenths, feeTenths);
  assert.equal(result.burnTenths, 0);
});

test("conservation holds across a deterministic sweep of fees and level mixes", () => {
  let seed = 0x2f6e2b1;
  const next = (modulus) => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed % modulus;
  };
  for (let step = 0; step < 200; step += 1) {
    const feeTenths = next(400);
    const unitCount = 1 + next(6);
    const units = Array.from({length: unitCount}, (_, index) => unit(`lesson/u${index}`, next(700)));
    const result = settleReviewFeeTenths({feeTenths, units});
    assert.equal(result.sumEntitlementTenths + result.burnTenths, feeTenths, `step ${step}`);
    assert.ok(result.burnTenths >= 0, `step ${step}: burn is non-negative`);
    for (const allocation of result.allocations) {
      assert.ok(allocation.entitlementTenths >= 0, `step ${step}: entitlement is non-negative`);
      assert.ok(allocation.entitlementTenths <= Math.max(allocation.levelTenths, feeTenths),
        `step ${step}: entitlement never beats both the level and the fund`);
    }
  }
});

test("wallet credit floors whole nolics and keeps the carry", () => {
  assert.deepEqual(walletCreditForEntitlement({priorCarryTenths: 0, entitlementTenths: 205}),
    {creditedNolics: 20, nextCarryTenths: 5});
  assert.deepEqual(walletCreditForEntitlement({priorCarryTenths: 5, entitlementTenths: 205}),
    {creditedNolics: 21, nextCarryTenths: 0});
  assert.deepEqual(walletCreditForEntitlement({priorCarryTenths: 9, entitlementTenths: 0}),
    {creditedNolics: 0, nextCarryTenths: 9});
  assert.throws(() => walletCreditForEntitlement({priorCarryTenths: -1, entitlementTenths: 0}), RangeError);
});

test("manifest records the calculation version and refuses to enshrine broken conservation", () => {
  const units = [unit("lesson/alpha", 100), unit("lesson/beta", 50)];
  const feeTenths = 1000;
  const plan = settleReviewFeeTenths({feeTenths, units});
  const manifest = buildSettlementManifest({
    submissionId: "submission",
    ownerUid: "author",
    questId: "quest",
    outcome: "PUBLISHED",
    settledAtMs: 12345,
    feeNolics: feeTenths / TENTHS_PER_NOLIC,
    tariff: "initial",
    tariffRevision: 3,
    units,
    allocations: plan.allocations,
    wallets: [{
      uid: "alpha",
      priorCarryTenths: 0,
      creditedNolics: 10,
      nextCarryTenths: 0,
    }, {
      uid: "beta",
      priorCarryTenths: 4,
      creditedNolics: 5,
      nextCarryTenths: 4,
    }],
  });
  assert.equal(manifest.calculationVersion, CALCULATION_VERSION);
  assert.equal(manifest.feeTenths, 1000);
  assert.equal(manifest.sumEntitlementTenths, 150);
  assert.equal(manifest.burnTenths, 850);
  assert.deepEqual(manifest.wallets.map((w) => [w.uid, w.entitlementTenths]), [["alpha", 100], ["beta", 50]]);

  // Кошелёк, чья арифметика разошлась с carry-уравнением, роняет манифест, а не попадает в историю.
  assert.throws(() => buildSettlementManifest({
    submissionId: "submission", ownerUid: "author", questId: "quest", outcome: "PUBLISHED",
    settledAtMs: 1, feeNolics: 100, tariff: "initial", tariffRevision: 1,
    units, allocations: plan.allocations,
    wallets: [{uid: "alpha", priorCarryTenths: 0, creditedNolics: 99, nextCarryTenths: 0}],
  }), RangeError);

  // Выплаты больше фонда не существуют.
  assert.throws(() => buildSettlementManifest({
    submissionId: "submission", ownerUid: "author", questId: "quest", outcome: "REJECTED",
    settledAtMs: 1, feeNolics: 1, tariff: "initial", tariffRevision: 1,
    units, allocations: plan.allocations,
    wallets: [{uid: "alpha", priorCarryTenths: 0, creditedNolics: 10, nextCarryTenths: 0}],
  }), RangeError);
});
