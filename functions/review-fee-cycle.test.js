"use strict";

/**
 * Платный цикл проверки у приёмника: плата при принятии, расчёт после полного цикла (история 4.7,
 * AD-10, CAP-4).
 *
 * Сюита гоняет настоящий `submitMutation`/`submitReviewAction`/`rejectReviewSubmission` против
 * стаба Firestore и держит сквозную консервацию: списанный тариф равен сумме зачислений
 * проверяющим и сгоревшего остатка, перенос кошелька переживает цикл, манифест неизменяем,
 * повтор расчёта ничего не меняет. Публикацию (полное дерево) сюита не гоняет — терминальный
 * цикл доказан через отклонение задания (тоже полный цикл: CAP-4 «including justified
 * rejection»); живой круг публикации против demo-backend — journey и приёмка.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath;
stub.loaded = true;
stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");

const TASK_PATH = "admin/review/lessons/lesson";
const CYCLE = "review_fee_cycles/submission";
const MANIFEST = "review_fee_manifests/submission";

function world(extra = {}) {
  return {
    "profiles/author": {uid: "author", testerLevel: 0, adminLevel: 0},
    "users/author": {uid: "author", pointsNolics: 1000},
    "profiles/tester": {uid: "tester", testerLevel: 100},
    "users/tester": {uid: "tester", pointsNolics: 0},
    "profiles/judge": {uid: "judge", adminLevel: 150},
    "users/judge": {uid: "judge", pointsNolics: 0},
    [TASK_PATH]: {
      assignmentId: "submission_lesson",
      submissionId: "submission",
      ownerUid: "author",
      catalogId: "catalog",
      draftId: "draft",
      questId: "quest",
      title: "Lesson",
      targetShelf: "arena",
      status: "UNDER_REVIEW",
      generation: 1,
      sourceLanguages: ["en"],
      checks: {},
      createdAtMs: 1,
      changedAtMs: 1,
    },
    [`${TASK_PATH}/quests/quest/questions/q1`]: {id: "q1", text: "one"},
    ...extra,
  };
}

function ledgerRows() {
  return [...store.documents.entries()]
    .filter(([path]) => path.startsWith("audit/currency_ledger/entries/"))
    .map(([path, data]) => ({path, ...data}));
}

async function reject(assignmentId) {
  return functions.rejectReviewSubmission.run({auth: {uid: "judge"}, data: {assignmentId, reason: "Justified quality rejection"}});
}

function settlementState() {
  return {
    author: store.documents.get("users/author"),
    tester: store.documents.get("users/tester"),
    judge: store.documents.get("users/judge"),
    cycle: store.documents.get(CYCLE),
    manifest: store.documents.get(MANIFEST),
  };
}

test("acceptance charges the tariff once; a justified rejection settles the cycle and conserves it", async () => {
  store.reset(world());
  const accepted = await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "m1", operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify({
      submissionId: "submission",
      draftId: "draft-1",
      ownerUid: "author",
      requestedAtMs: 1,
      targetShelf: "arena",
      targetLessonIds: ["lesson"],
      draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson"]},
      draft: {id: "quest", catalogId: "catalog", title: "Квест", description: null, defaultLanguage: "ru",
        defaultDifficulty: "EASY", publicQuestId: null, createdAtMs: 1, updatedAtMs: 1},
      sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
      themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
      lessons: [{id: "lesson", draftId: "draft-1", themeId: "theme-1", title: "Lesson", order: 0}],
      questions: [{id: "q1", draftId: "draft-1", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru",
        languageLevel: 1, difficulty: "EASY", order: 0, text: "Вопрос?", imagePath: null, payload: "{}",
        updatedAtMs: 1}],
      review: {},
    })},
  });
  assert.equal(accepted.result.reviewFee.feeNolics, 1000, "первая подача квеста — первоначальный тариф");
  assert.equal(store.documents.get("users/author").pointsNolics, 0);

  // Работа цикла: TESTING проверяющим (уровень 100 → entitlement 100 десятых = 10 ноликов).
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 2,
      questionScores: [{questionId: "q1", score: 2}]},
  });

  await reject("submission_lesson");

  const state = settlementState();
  assert.equal(state.cycle.status, "SETTLED");
  assert.equal(state.cycle.outcome, "REJECTED");
  assert.ok(state.manifest, "неизменяемый манифест записан");
  assert.equal(state.manifest.feeTenths, 10000, "1000 ноликов = 10000 десятых");
  // tester: 100 десятых; judge: отклонение с уровнем max(admin 150) = 150 десятых.
  assert.deepEqual(state.manifest.units.map((u) => [u.unitId, u.levelTenths]),
    [["lesson/judge", 150], ["lesson/tester", 100]]);
  assert.equal(state.manifest.sumEntitlementTenths, 250);
  assert.equal(state.manifest.burnTenths, 9750);
  // Консервация: плата = вознаграждения + сгоревший остаток.
  assert.equal(10000, state.manifest.sumEntitlementTenths + state.manifest.burnTenths);

  assert.equal(state.tester.pointsNolics, 10, "тестер получил уровень/10 целыми ноликами");
  assert.equal(state.tester.reviewFeeCarryTenths || 0, 0);
  assert.equal(state.judge.pointsNolics, 15);
  assert.equal(state.judge.reviewFeeCarryTenths || 0, 0);
  assert.equal(state.author.pointsNolics, 0, "плата не возвращается — отклонение её не возвращает");

  const rows = ledgerRows().filter((row) => row.reason === "review_reward");
  assert.deepEqual(rows.map((row) => [row.uid, row.delta]).sort(), [["judge", 15], ["tester", 10]]);
  const chargeRow = ledgerRows().find((row) => row.reason === "review_fee");
  assert.equal(chargeRow.delta, -1000);
});

test("settlement is idempotent: replays change no wallet and rewrite no manifest", async () => {
  store.reset(world());
  await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "m1", operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify({
      submissionId: "submission", draftId: "draft-1", ownerUid: "author", requestedAtMs: 1,
      targetShelf: "arena", targetLessonIds: ["lesson"],
      draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson"]},
      draft: {id: "quest", catalogId: "catalog", title: "Квест", description: null, defaultLanguage: "ru",
        defaultDifficulty: "EASY", publicQuestId: null, createdAtMs: 1, updatedAtMs: 1},
      sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
      themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
      lessons: [{id: "lesson", draftId: "draft-1", themeId: "theme-1", title: "Lesson", order: 0}],
      questions: [{id: "q1", draftId: "draft-1", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru",
        languageLevel: 1, difficulty: "EASY", order: 0, text: "Вопрос?", imagePath: null, payload: "{}",
        updatedAtMs: 1}],
      review: {},
    })},
  });
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 3,
      questionScores: [{questionId: "q1", score: 3}]},
  });
  await reject("submission_lesson");
  const first = settlementState();

  // Повтор расчёта — через ревью уже терминальной заявки и через повторное отклонение: статус
  // цикла SETTLED превращает оба вызова в no-op (это предсуществующее поведение отклонения,
  // деньги оно не трогает — идемпотентность расчёта проверяет сравнение состояния).
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 3,
      questionScores: [{questionId: "q1", score: 3}]},
  }).catch(() => {});
  await reject("submission_lesson").catch(() => {});

  const second = settlementState();
  assert.deepEqual(second, first, "повторы не меняют ни кошельков, ни манифеста");
});

test("fractional entitlements ride the carry into the next cycle of the same reviewer", async () => {
  // Уровень 105 даёт 105 десятых: 10 целых + 5 десятых в carry. Следующий цикл того же
  // проверяющего с 5 десятыми сверху зачисляет их вместе (AD-10: oldCarry + entitlement).
  store.reset(world({
    "profiles/tester": {uid: "tester", testerLevel: 105},
    "users/author": {uid: "author", pointsNolics: 2000},
  }));
  const body = () => JSON.stringify({
    submissionId: "submission", draftId: "draft-1", ownerUid: "author", requestedAtMs: 1,
    targetShelf: "arena", targetLessonIds: ["lesson"],
    draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson"]},
    draft: {id: "quest", catalogId: "catalog", title: "Квест", description: null, defaultLanguage: "ru",
      defaultDifficulty: "EASY", publicQuestId: null, createdAtMs: 1, updatedAtMs: 1},
    sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
    themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
    lessons: [{id: "lesson", draftId: "draft-1", themeId: "theme-1", title: "Lesson", order: 0}],
    questions: [{id: "q1", draftId: "draft-1", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru",
      languageLevel: 1, difficulty: "EASY", order: 0, text: "Вопрос?", imagePath: null, payload: "{}",
      updatedAtMs: 1}],
    review: {},
  });
  await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "c1", operation: "quest_authoring.SUBMIT_ARENA", payload: body()},
  });
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 3,
      questionScores: [{questionId: "q1", score: 3}]},
  });
  await reject("submission_lesson");
  assert.equal(store.documents.get("users/tester").pointsNolics, 10);
  assert.equal(store.documents.get("users/tester").reviewFeeCarryTenths, 5,
    "пять десятых ждут следующего цикла, а не теряются");

  // Новая заявка того же квеста: тариф перепроверки, тот же проверяющий, carry + 5 десятых.
  const second = JSON.parse(body());
  second.submissionId = "submission-2";
  await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "c2", operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify(second)},
  });
  assert.equal(store.documents.get("users/author").pointsNolics, 900,
    "перепроверка одного вопроса стоит 100");
  // Новая заявка переоткрывает задание под своим submissionId: id задания = submission_урок.
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission-2_lesson", lessonId: "lesson", kind: "TESTING", score: 3,
      questionScores: [{questionId: "q1", score: 3}]},
  });
  await reject("submission-2_lesson");
  const manifest2 = store.documents.get("review_fee_manifests/submission-2");
  assert.ok(manifest2, "второй цикл рассчитан (терминальные вызовы догоняют расчёт)");
  assert.equal(manifest2.outcome, "REJECTED");
  const testerWallet = manifest2.wallets.find((wallet) => wallet.uid === "tester");
  assert.equal(testerWallet.entitlementTenths, 105);
  assert.equal(testerWallet.priorCarryTenths, 5);
  assert.equal(testerWallet.creditedNolics, 11, "oldCarry 5 + entitlement 105 = 11 целых без остатка");
  assert.equal(testerWallet.nextCarryTenths, 0);
  assert.equal(store.documents.get("users/tester").pointsNolics, 21);
});

test("an unfinished cycle settles nothing: no manifest, no burn, untouched wallets", async () => {
  store.reset(world());
  await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "m1", operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify({
      submissionId: "submission", draftId: "draft-1", ownerUid: "author", requestedAtMs: 1,
      targetShelf: "arena", targetLessonIds: ["lesson"],
      draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson"]},
      draft: {id: "quest", catalogId: "catalog", title: "Квест", description: null, defaultLanguage: "ru",
        defaultDifficulty: "EASY", publicQuestId: null, createdAtMs: 1, updatedAtMs: 1},
      sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
      themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
      lessons: [{id: "lesson", draftId: "draft-1", themeId: "theme-1", title: "Lesson", order: 0}],
      questions: [{id: "q1", draftId: "draft-1", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru",
        languageLevel: 1, difficulty: "EASY", order: 0, text: "Вопрос?", imagePath: null, payload: "{}",
        updatedAtMs: 1}],
      review: {},
    })},
  });
  // Ревью без терминала: publishSubmissionIfReady видит UNDER_REVIEW и фонд не делит (OQ-2 parked).
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "submission_lesson", lessonId: "lesson", kind: "TESTING", score: 2,
      questionScores: [{questionId: "q1", score: 2}]},
  });

  const state = settlementState();
  assert.equal(state.cycle.status, "PAID");
  assert.equal(state.manifest, undefined, "манифеста до полного цикла нет");
  assert.equal(state.tester.pointsNolics, 0, "выплат до полного цикла нет");
  assert.equal(ledgerRows().some((row) => row.reason === "review_reward"), false);
});
