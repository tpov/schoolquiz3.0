"use strict";

/**
 * Инварианты заявки на арену у приёмника (SPEC Constraints, история 4.5).
 *
 * Заявка — единственный путь черновика к публикации, поэтому именно здесь держатся три
 * ограничения автора:
 *
 * — заголовок уже существующего урока неизменен: публикация пишет `{merge: true}`, и заголовок
 *   с провода затёр бы публичный молча; клиентского пути переименовать урок нет, так что
 *   расхождение — тело, собранное вопреки правилу, и отказ приходит до всякой записи;
 *
 * — описание не существует: публичные документы квеста и урока строятся без ключа
 *   `description` вовсе (проверка исходником — построитель рядом с записью);
 *
 * — деньги платного цикла (AD-10, история 4.7): принятие заявки списывает тариф ровно один раз
 *   (повтор приёмки не платит второй раз), отказ оставляет кошелёк нетронутым, денег на тариф не
 *   хватает — окончательный отказ до всякой записи. Тарифа «за метаданные» не появляется: платит
 *   заявка с вопросами по тарифу из `configs/review_fee`, и никакого другого повода списания
 *   подача не изобретает.
 *
 * Прогон поведенческий: настоящий `submitMutation` против стаба Firestore, как в
 * `arena-handler.test.js`.
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

const PUBLISHED_TITLE = "Урок о падежах";

function world(extra = {}) {
  return {
    "profiles/author": {uid: "author", testerLevel: 0, adminLevel: 0},
    "users/author": {uid: "author", boxCount: 2, lifePoints: 300, goldCoins: 7, pointsNolics: 1500},
    "lessons/lesson-1": {themeId: "theme-1", title: PUBLISHED_TITLE, version: 2, archived: false},
    ...extra,
  };
}

function submissionBody(lessonTitle) {
  return {
    submissionId: "submission-1",
    draftId: "draft-1",
    ownerUid: "author",
    requestedAtMs: 1,
    targetShelf: "arena",
    targetLessonIds: ["lesson-1"],
    draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson-1"]},
    draft: {
      id: "quest-1",
      catalogId: "catalog",
      title: "Квест",
      description: null,
      defaultLanguage: "ru",
      defaultDifficulty: "EASY",
      publicQuestId: null,
      createdAtMs: 1,
      updatedAtMs: 1,
    },
    sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
    themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
    lessons: [{id: "lesson-1", draftId: "draft-1", themeId: "theme-1", title: lessonTitle, order: 0}],
    questions: [
      {
        id: "question-1",
        draftId: "draft-1",
        lessonId: "lesson-1",
        type: "SINGLE_CHOICE",
        language: "ru",
        languageLevel: 1,
        difficulty: "EASY",
        order: 0,
        text: "Вопрос?",
        imagePath: null,
        payload: "{}",
        updatedAtMs: 1,
      },
    ],
    review: {
      isTested: false,
      testingScore: null,
      isLogicReviewed: false,
      logicScore: null,
      isTranslationReviewed: false,
      translationScore: null,
      translatedLanguages: {},
    },
  };
}

function submit(mutationId, body) {
  return functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId, operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify(body)},
  });
}

test("a submission that renames an existing lesson is refused before any write", async () => {
  store.reset(world());
  await assert.rejects(
    submit("rename-1", submissionBody("Совсем другой заголовок")),
    (error) => error.code === "failed-precondition" && /LESSON_TITLE_IMMUTABLE/.test(error.message),
  );

  assert.equal(store.documents.get("quest_review_requests/submission-1"), undefined,
    "заявка не создана");
  assert.equal(store.documents.get("private/author/catalogs/catalog/quests/quest-1"), undefined,
    "приватное дерево не начато");
  assert.equal(store.documents.get("admin/review/lessons/lesson-1"), undefined,
    "задание ревью не открыто");
  assert.equal(store.documents.get("users/author").lifePoints, 300,
    "кошелёк автора не тронут — плата не доходит до refusal-before-write");
});

test("an accepted submission charges the initial tariff exactly once (paid cycle, AD-10)", async () => {
  store.reset(world());
  const result = await submit("resubmit-1", submissionBody(PUBLISHED_TITLE));

  assert.equal(result.replayed, false);
  assert.equal(result.result.reviewFee.feeNolics, 1000);
  assert.equal(result.result.reviewFee.tariff, "initial");
  const request = store.documents.get("quest_review_requests/submission-1");
  assert.ok(request, "заявка записана целиком");
  assert.equal(request.status, "UNDER_REVIEW");
  assert.equal(request.draft.title, "Квест");
  assert.equal(store.documents.get("users/author").pointsNolics, 500,
    "тариф первоначальной подачи списан при принятии");
  assert.equal(store.documents.get("users/author").lifePoints, 300);
  const cycle = store.documents.get("review_fee_cycles/submission-1");
  assert.ok(cycle, "оплаченный цикл зафиксирован");
  assert.equal(cycle.status, "PAID");
  assert.equal(cycle.feeNolics, 1000);
  assert.ok(store.documents.get("review_fee_quests/quest-1"), "квест помнит оплаченную подачу");
  assert.ok(
    [...store.documents.keys()].some((path) => path.startsWith("audit/currency_ledger/entries/")),
    "плата видна в книге денег",
  );

  // Повтор приёмки той же заявки платит ровно один раз.
  await functions.submitMutation.run({
    auth: {uid: "author"},
    data: {mutationId: "resubmit-1", operation: "quest_authoring.SUBMIT_ARENA", payload: JSON.stringify(submissionBody(PUBLISHED_TITLE))},
  });
  assert.equal(store.documents.get("users/author").pointsNolics, 500, "повтор платы не существует");
});

test("a submission without money for the tariff is refused before any write", async () => {
  store.reset(world({"users/author": {uid: "author", boxCount: 2, lifePoints: 300, pointsNolics: 999}}));
  await assert.rejects(
    submit("poor-1", submissionBody(PUBLISHED_TITLE)),
    (error) => error.code === "failed-precondition" && /REVIEW_FEE_INSUFFICIENT/.test(error.message),
  );
  assert.equal(store.documents.get("quest_review_requests/submission-1"), undefined,
    "заявка без платы не принята");
  assert.equal(store.documents.get("review_fee_cycles/submission-1"), undefined);
  assert.ok(
    ![...store.documents.keys()].some((path) => path.startsWith("mutation_keys/")),
    "окончательный отказ освобождает ключ",
  );
});

test("a resubmission of an already-paid quest pays the recheck tariff per question", async () => {
  store.reset(world({"review_fee_quests/quest-1": {questId: "quest-1", cycles: 1}}));
  const result = await submit("recheck-1", submissionBody(PUBLISHED_TITLE));
  assert.equal(result.result.reviewFee.tariff, "recheck");
  assert.equal(result.result.reviewFee.feeNolics, 100,
    "один вопрос заявки — один тариф перепроверки");
  assert.equal(store.documents.get("users/author").pointsNolics, 1400);
});

test("a first submission of a new lesson needs no title comparison to pass", async () => {
  store.reset(world({"lessons/lesson-1": undefined}));
  const result = await submit("first-1", submissionBody(PUBLISHED_TITLE));

  assert.equal(result.replayed, false);
  assert.ok(store.documents.get("quest_review_requests/submission-1"));
});

test("a questionless submission is refused and releases the key", async () => {
  store.reset(world());
  const body = submissionBody(PUBLISHED_TITLE);
  body.questions = [];
  await assert.rejects(
    submit("empty-1", body),
    (error) => error.code === "invalid-argument" && /no questions/.test(error.message),
  );
  assert.ok(
    ![...store.documents.keys()].some((path) => path.startsWith("mutation_keys/")),
    "отказ тела не оставляет резерва ключа",
  );
});

test("public documents are built without a description key", async () => {
  // Проверка исходником: построитель публичных документов и сама запись — одно место, и ключ
  // `description` не имеет права появиться ни в одном публичном узле (SPEC: lessons have no
  // description). Приватная копия автора описания не публикует — ей ключ разрешён.
  const source = require("node:fs").readFileSync(require("node:path").join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("function publicDocuments(");
  assert.ok(start > 0, "publicDocuments найден в index.js");
  const end = source.indexOf("\nfunction ", start + 10);
  const body = source.slice(start, end);
  assert.equal(body.includes("description"), false,
    "публичные документы не строятся с описанием");
});
