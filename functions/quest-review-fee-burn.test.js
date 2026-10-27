"use strict";

/**
 * Плата модельного цикла сгорает целиком (история 4.18, CAP-4, AD-10, AD-32).
 *
 * В модельном мире проверяющих нет: entitlements ровно нули, консервация fee = 0 + burn держится
 * манифестом. Ретро 2026-10-25 L4/п.2: чужой draft.id в заявке — именованный отказ без записей
 * под чужим именем; зеркало ноликов (pointsNolics и nolics) пишется и списанием, и расчётом.
 *
 * Сюита гоняет настоящие публичные поверхности: submitMutation (приёмка с платой),
 * advanceQuestReviewOutcome (модельный исход → статус → расчёт), submitReviewAction/reject
 * (человеческий цикл для проверки зеркала в кошельках расчёта).
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

function submissionPayload(overrides = {}) {
  return {
    submissionId: "sub",
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
    ...overrides,
  };
}

async function submit(uid, payload) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {mutationId: `m-${payload.submissionId}`, operation: "quest_authoring.SUBMIT_ARENA",
      payload: JSON.stringify(payload)},
  });
}

function world(extra = {}) {
  return {
    "profiles/author": {uid: "author"},
    "users/author": {uid: "author", pointsNolics: 1000, nolics: 1000},
    "users/tester": {uid: "tester", pointsNolics: 0, nolics: 0},
    "users/judge": {uid: "judge", pointsNolics: 0, nolics: 0},
    "profiles/tester": {uid: "tester", testerLevel: 100},
    "profiles/judge": {uid: "judge", adminLevel: 150},
    "catalogs/catalog": {name: "Каталог"},
    ...extra,
  };
}

function seedModelOutcome({pipelineStatus = "QUEST_REVIEWED", passingLessonIds = ["lesson"]} = {}) {
  store.documents.set("quest_review_requests/sub", {
    ...(store.documents.get("quest_review_requests/sub")),
    reviewOutcome: {pipelineStatus, failedQuestions: [], detectedLanguage: "ru",
      passingLessonIds, delayedStages: [], reviewedAtMs: 500},
  });
  store.documents.set("review_pipeline/sub", {
    schemaVersion: 1, submissionId: "sub", status: pipelineStatus,
    lessons: {lesson: {overall: "DONE", stages: {
      WORDS: {state: "DONE", attempts: 1, errors: [], verdictId: null},
      FACTS: {state: "DONE", attempts: 1, errors: [], verdictId: null},
      TRANSLATE: {state: "DONE", attempts: 1, errors: [], verdictId: null},
    }}},
    updatedAtMs: 500,
  });
}

function ledgerRows() {
  return [...store.documents.entries()]
    .filter(([path]) => path.startsWith("audit/currency_ledger/entries/"))
    .map(([path, data]) => ({path, ...data}));
}

test("model settlement burns the whole fee: zero units, zero wallets, conservation holds", async () => {
  store.reset(world());
  const accepted = await submit("author", submissionPayload());
  assert.equal(accepted.result.reviewFee.feeNolics, 1000);
  seedModelOutcome();

  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, true);
  const cycle = store.documents.get("review_fee_cycles/sub");
  assert.equal(cycle.status, "SETTLED");
  assert.equal(cycle.sumEntitlementTenths, 0, "the model pipeline pays no reward units");
  assert.equal(cycle.burnTenths, 10000, "the whole fee burns");
  const manifest = store.documents.get("review_fee_manifests/sub");
  assert.deepEqual(manifest.units, []);
  assert.deepEqual(manifest.wallets, []);
  assert.equal(manifest.feeTenths, manifest.sumEntitlementTenths + manifest.burnTenths,
    "conservation: fee = entitlements (0) + burned remainder");
  // Кошелёк автора не пополнен; из вознаграждений в книге нет ни строки.
  assert.equal(store.documents.get("users/author").pointsNolics, 0);
  assert.equal(ledgerRows().filter((row) => row.reason === "review_reward").length, 0,
    "no reward ledger rows without entitlements");
  // Строка платы в книге есть — деньги списались.
  assert.equal(ledgerRows().find((row) => row.reason === "review_fee").delta, -1000);
});

test("a fully failed model quest settles REJECTED and burns the whole fee", async () => {
  store.reset(world());
  await submit("author", submissionPayload());
  seedModelOutcome({passingLessonIds: []});

  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, false);
  const cycle = store.documents.get("review_fee_cycles/sub");
  assert.equal(cycle.status, "SETTLED");
  assert.equal(cycle.outcome, "REJECTED");
  assert.equal(cycle.burnTenths, 10000);
  assert.equal(cycle.sumEntitlementTenths, 0);
  assert.equal(store.documents.get("quest_review_requests/sub").status, "REJECTED");
});

test("a foreign draft id is refused by name and writes nothing under the foreign owner", async () => {
  store.reset(world({
    "review_fee_quests/quest": {questId: "quest", ownerUid: "other", catalogId: "catalog", cycles: 3},
  }));
  await assert.rejects(
    () => submit("author", submissionPayload()),
    (error) => error.code === "permission-denied" && /REVIEW_FEE_DRAFT_NOT_OWNED/.test(error.message),
  );
  assert.equal(store.documents.get("review_fee_quests/quest").cycles, 3,
    "the foreign counter is untouched");
  assert.equal(store.documents.get("review_fee_cycles/sub"), undefined,
    "no cycle is opened for a foreign id");
  assert.equal(store.documents.get("quest_review_requests/sub"), undefined,
    "the submission is not accepted without a legal charge");
});

test("the charge dual-writes the nolics mirror", async () => {
  store.reset(world());
  await submit("author", submissionPayload());
  const author = store.documents.get("users/author");
  assert.equal(author.pointsNolics, 0);
  assert.equal(author.nolics, 0, "the charge leaves both mirror fields consistent");
  assert.equal(ledgerRows().find((row) => row.reason === "review_fee").delta, -1000);
});

test("a wallet settlement dual-writes the nolics mirror too (human cycle, same code path)", async () => {
  store.reset(world({
    "admin/review/lessons/lesson": {
      assignmentId: "sub_lesson", submissionId: "sub", ownerUid: "author", catalogId: "catalog",
      draftId: "draft-1", questId: "quest", title: "Lesson", targetShelf: "arena",
      status: "UNDER_REVIEW", generation: 1, sourceLanguages: ["ru"], checks: {},
      createdAtMs: 1, changedAtMs: 1,
      questions: [{id: "q1", text: "Вопрос?", language: "ru"}],
    },
  }));
  await submit("author", submissionPayload());
  await functions.submitReviewAction.run({
    auth: {uid: "tester"},
    data: {assignmentId: "sub_lesson", lessonId: "lesson", kind: "TESTING", score: 2,
      questionScores: [{questionId: "q1", score: 2}]},
  });
  await functions.rejectReviewSubmission.run({
    auth: {uid: "judge"},
    data: {assignmentId: "sub_lesson", reason: "Justified quality rejection"},
  });

  const manifest = store.documents.get("review_fee_manifests/sub");
  assert.equal(manifest.feeTenths, manifest.sumEntitlementTenths + manifest.burnTenths,
    "the human path stays conservative");
  for (const uid of ["tester", "judge"]) {
    const user = store.documents.get(`users/${uid}`);
    const credited = manifest.wallets.find((wallet) => wallet.uid === uid).creditedNolics;
    assert.equal(user.nolics, credited, `${uid}: the settlement mirror carries the credit`);
  }
});
