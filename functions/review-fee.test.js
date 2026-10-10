"use strict";

/**
 * Консервация платного цикла проверки — чистая арифметика AD-10 (история 4.7, CAP-4).
 *
 * Модуль без Firestore: сюита гоняется везде, где есть node, и держит те ветки, которые у денег
 * не имеют права на исключение — равенство «плата = вознаграждения + сгоревший остаток»,
 * целочисленность (никаких floating-point), раздачу остаточных десятых по убыванию остатка с
 * ничьей по стабильному id, фиксацию уровня первым завершённым результатом и перенос кошелька.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  CALCULATION_VERSION,
  DEFAULT_TARIFFS,
  TENTHS_PER_NOLIC,
  buildReviewUnits,
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

function record(patch) {
  return {
    id: "r1",
    submissionId: "submission",
    lessonId: "lesson",
    kind: "TESTING",
    reviewerUid: "reviewer",
    reviewerLevelAtSubmit: 100,
    score: 3,
    questionScores: [],
    language: null,
    targetReviewId: null,
    createdAtMs: 10,
    acceptedByServer: true,
    segmentResults: [],
    translatedQuestions: [],
    ...patch,
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

test("units merge directions per (lesson, reviewer) and fix the level with the first completed result", () => {
  const records = [
    // Поздний TESTING с высоким уровнем не должен перебить ранний TRANSLATION.
    record({id: "late-testing", kind: "TESTING", reviewerLevelAtSubmit: 300, createdAtMs: 30}),
    record({id: "early-translation", kind: "TRANSLATION", language: "en", reviewerLevelAtSubmit: 100,
      createdAtMs: 10, translatedQuestions: [{id: "q1__en", text: "one", language: "en", languageLevel: 100}]}),
    // Логичный уровень между ними — тоже не фикс (позднее раннего).
    record({id: "mid-logic", kind: "LOGIC", reviewerLevelAtSubmit: 200, createdAtMs: 20}),
  ];
  const task = {questions: [{id: "q1", text: "one", language: "ru"}]};
  const units = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {lesson: task},
    recordsByLessonId: {lesson: records},
    ownerUid: "author",
  });
  assert.equal(units.length, 1);
  assert.deepEqual(units[0], {
    unitId: "lesson/reviewer",
    lessonId: "lesson",
    reviewerUid: "reviewer",
    levelTenths: 100,
    evidenceRecordId: "early-translation",
    createdAtMs: 10,
  });
});

test("same-time records break the level tie by record id, and the owner never earns", () => {
  const records = [
    record({id: "zzz", reviewerUid: "reviewer", reviewerLevelAtSubmit: 200, createdAtMs: 10}),
    record({id: "aaa", reviewerUid: "reviewer", reviewerLevelAtSubmit: 300, createdAtMs: 10}),
    record({id: "own", reviewerUid: "author", reviewerLevelAtSubmit: 900, createdAtMs: 5}),
  ];
  const units = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {},
    recordsByLessonId: {lesson: records},
    ownerUid: "author",
  });
  assert.equal(units.length, 1);
  assert.equal(units[0].levelTenths, 300);
  assert.equal(units[0].evidenceRecordId, "aaa");
});

test("incomplete work creates no unit: partial translation, missing score, silent segments, foreign submissions", () => {
  const task = {questions: [{id: "q1", text: "one", language: "ru"}, {id: "q2", text: "two", language: "ru"}]};
  const records = [
    // Перевод накрыл один вопрос из двух — не завершённый результат.
    record({id: "partial", kind: "TRANSLATION", language: "en", translatedQuestions: [
      {id: "q1__en", text: "one", language: "en", languageLevel: 100},
    ]}),
    // Полный по числу, но пустой текст — содержимого нет (CAP-3).
    record({id: "blank", kind: "TRANSLATION", language: "de", translatedQuestions: [
      {id: "q1__de", text: " ", language: "de", languageLevel: 100},
      {id: "q2__de", text: "zwei", language: "de", languageLevel: 100},
    ]}),
    // TESTING без оценки — не завершён.
    record({id: "unscored", kind: "TESTING", score: null}),
    // Чужая заявка — чужой цикл.
    record({id: "foreign", submissionId: "another", score: 3}),
  ];
  const units = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {lesson: task},
    recordsByLessonId: {lesson: records},
    ownerUid: "author",
  });
  assert.deepEqual(units, []);
});

test("a record of another submission never earns in this cycle, even with the same reviewer", () => {
  const units = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {},
    recordsByLessonId: {lesson: [record({id: "retained", submissionId: "older-submission", score: 3})]},
    ownerUid: "author",
  });
  assert.deepEqual(units, [], "retained prior evidence is history, not new paid work");
});

test("justified rejection earns its judge a unit at the recorded level, once, and never to the author", () => {
  const task = {
    questions: [{id: "q1", text: "one", language: "ru"}],
    rejectedAtMs: 50,
    rejectedByUid: "judge",
    rejectedReviewerLevelAtSubmit: 150,
  };
  const units = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {lesson: task},
    recordsByLessonId: {lesson: []},
    ownerUid: "author",
  });
  assert.deepEqual(units, [{
    unitId: "lesson/judge",
    lessonId: "lesson",
    reviewerUid: "judge",
    levelTenths: 150,
    evidenceRecordId: null,
  }]);

  // У судьи есть и запись — работает запись (первый завершённый результат), отклонение юнит не дублирует.
  const withRecord = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {lesson: task},
    recordsByLessonId: {lesson: [record({reviewerUid: "judge", reviewerLevelAtSubmit: 100})]},
    ownerUid: "author",
  });
  assert.equal(withRecord.length, 1);
  assert.equal(withRecord[0].levelTenths, 100);

  // Уровень отклонения не записан — юнита нет: не выдумываем уровень из воздуха.
  const withoutLevel = buildReviewUnits({
    submissionId: "submission",
    lessonIds: ["lesson"],
    taskByLessonId: {lesson: {...task, rejectedReviewerLevelAtSubmit: null}},
    recordsByLessonId: {lesson: []},
    ownerUid: "author",
  });
  assert.deepEqual(withoutLevel, []);
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
