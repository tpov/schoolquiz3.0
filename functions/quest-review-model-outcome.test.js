"use strict";

/**
 * Авторский исход модельной проверки (история 4.16, UX-6): от outcome воркера до статуса заявки
 * и дерева публикации. Сюита гоняет настоящий `advanceQuestReviewOutcome` против стаба Firestore:
 * задержка не публикует и фонд не делит, отказ без прошедших уроков называет «Не прошёл»,
 * публикация прошедшего состава пишет переводы вариантами __lang, чужая заявка неотличима от
 * отсутствующей. Человеческий путь этой сюиты не касается.
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

function choicePayload(id, text, answerText) {
  return JSON.stringify({
    type: "SingleChoice", id, difficulty: "EASY", text, imageUrl: null,
    options: [{id: "a", text: answerText}, {id: "b", text: "другое"}],
    correctOptionId: "a",
  });
}

function questionDocument(id, lessonId, text) {
  return {
    id, draftId: "draft", lessonId, type: "SINGLE_CHOICE", language: "ru", languageLevel: 1,
    difficulty: "EASY", order: 0, text, imagePath: null,
    payload: choicePayload(id, text, "верный"), updatedAtMs: 10,
  };
}

function translatedQuestion(id, lessonId, language, text) {
  return {
    id: `${id}__${language}`, draftId: "draft", lessonId, type: "SINGLE_CHOICE", language,
    languageLevel: 1, difficulty: "EASY", order: 0, text, imagePath: null,
    payload: choicePayload(`${id}__${language}`, text, "верный"), updatedAtMs: 10,
  };
}

/** Заявка с двумя уроками; outcome/записи — то, что положил бы воркер к терминалу. */
function world({outcome} = {}) {
  const pipeline = {
    schemaVersion: 1,
    submissionId: "sub",
    status: "QUEST_REVIEWED",
    lessons: {
      l1: {overall: "DONE", stages: {
        WORDS: {state: "DONE", attempts: 1, errors: [], verdictId: "sub_l1_WORDS_1"},
        FACTS: {state: "DONE", attempts: 1, errors: [], verdictId: "sub_l1_FACTS_1"},
        TRANSLATE: {state: "DONE", attempts: 1, errors: [], verdictId: "sub_l1_TRANSLATE_1"},
      }},
      l2: {overall: "DONE", stages: {
        WORDS: {state: "DONE", attempts: 1, errors: [], verdictId: "sub_l2_WORDS_1"},
        FACTS: {state: "DONE", attempts: 1, errors: [], verdictId: "sub_l2_FACTS_1"},
        TRANSLATE: {state: "SKIPPED", attempts: 0, errors: [], verdictId: null},
      }},
    },
    updatedAtMs: 500,
  };
  return {
    "profiles/author": {uid: "author"},
    "users/author": {uid: "author", pointsNolics: 10000},
    "catalogs/catalog": {name: "Каталог"},
    "quest_review_requests/sub": {
      submissionId: "sub", draftId: "draft", ownerUid: "author", localRevision: 3,
      requestedAtMs: 100, processedAtMs: 100, targetShelf: "arena", targetLessonIds: [],
      draftTree: {sections: ["s1"], themes: ["t1"], lessons: ["l1", "l2"]},
      draft: {id: "quest", catalogId: "catalog", title: "Квест", description: null,
        defaultLanguage: "ru", defaultDifficulty: "EASY", publicQuestId: null,
        createdAtMs: 1, updatedAtMs: 1, privateQuiz: null},
      sections: [{id: "s1", draftId: "draft", title: "Раздел", order: 0}],
      themes: [{id: "t1", draftId: "draft", sectionId: "s1", title: "Тема", order: 0}],
      lessons: [
        {id: "l1", draftId: "draft", themeId: "t1", title: "Урок 1", order: 0},
        {id: "l2", draftId: "draft", themeId: "t1", title: "Урок 2", order: 1},
      ],
      questions: [
        questionDocument("q1", "l1", "Вопрос один"),
        questionDocument("q2", "l2", "Вопрос два"),
      ],
      review: {}, status: "UNDER_REVIEW", processed: true,
      failedStages: [], questionOutcomes: [],
      ...(outcome ? {reviewOutcome: outcome} : {}),
    },
    "review_pipeline/sub": pipeline,
    "review_translations/sub_l1_TRANSLATE_1": {
      schemaVersion: 1, submissionId: "sub", lessonId: "l1", stage: "TRANSLATE", attempt: 1,
      revision: 3, model: "glm-test", promptVersion: "translate-v1", detectedLanguage: "ru",
      questions: {
        uk: [translatedQuestion("q1", "l1", "uk", "Питання перше")],
        en: [translatedQuestion("q1", "l1", "en", "Question one")],
      },
    },
    "configs/arena_review": {testingPassThreshold: 2, testingMaxScore: 3, configRevision: 1},
  };
}

const OUTCOME_REVIEWED = {
  pipelineStatus: "QUEST_REVIEWED",
  failedQuestions: [],
  detectedLanguage: "ru",
  passingLessonIds: ["l1", "l2"],
  delayedStages: [],
  reviewedAtMs: 500,
};

test("reviewed quest publishes passing lessons with translation variants and finishes PUBLISHED", async () => {
  store.reset(world({outcome: OUTCOME_REVIEWED}));
  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, true);
  const request = store.documents.get("quest_review_requests/sub");
  assert.equal(request.status, "PUBLISHED");
  assert.ok(request.publishedAtMs > 0);
  // Прошедший состав опубликован; l2 прошёл (нет FAIL), поэтому оба урока в дереве.
  assert.ok(store.documents.get("lessons/l1"));
  assert.ok(store.documents.get("lessons/l2"));
  assert.ok(store.documents.get("quests/quest"));
  // Переводы урока l1 едут вариантами __lang рядом с оригиналом.
  assert.ok(store.documents.get("questions/q1__uk"), "uk translation published");
  assert.ok(store.documents.get("questions/q1__en"), "en translation published");
  assert.equal(store.documents.get("questions/q1").text, "Вопрос один");
  const uk = store.documents.get("questions/q1__uk");
  assert.equal(uk.language, "uk");
  const ukPayload = JSON.parse(uk.payload);
  assert.equal(ukPayload.correctOptionId, "a", "answer key is the server's, not the model's");
  // Расчёт вызывается за публикацией; мир без цикла платы его не кормит — здесь важно,
  // что вызов расчёта не уронил публикацию (полный консервационный круг гоняет 4.7/4.18).
  assert.equal(store.documents.get("review_fee_cycles/sub"), undefined);
});

test("a quest without passing lessons is REJECTED with the named reason and no tree", async () => {
  store.reset(world({
    outcome: {
      pipelineStatus: "QUEST_REVIEWED",
      failedQuestions: [{lessonId: "l1", questionId: "q1", stage: "FACTS", reason: "ключ неверен"},
        {lessonId: "l2", questionId: "q2", stage: "WORDS", reason: "мат"}],
      detectedLanguage: null,
      passingLessonIds: [],
      delayedStages: [],
      reviewedAtMs: 500,
    },
  }));
  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, false);
  const request = store.documents.get("quest_review_requests/sub");
  assert.equal(request.status, "REJECTED");
  assert.equal(request.rejectionReason, "REVIEW_FAILED_QUESTIONS");
  assert.equal(store.documents.get("quests/quest"), undefined, "nothing published");
  // Расчёт отказа при отсутствии цикла платы — честный no-op (цикл открывает приёмка).
  assert.equal(store.documents.get("review_fee_cycles/sub"), undefined);
});

test("partial pass publishes only passing lessons and keeps the failed one out of the tree", async () => {
  store.reset(world({
    outcome: {
      ...OUTCOME_REVIEWED,
      failedQuestions: [{lessonId: "l2", questionId: "q2", stage: "WORDS", reason: "мат"}],
      passingLessonIds: ["l1"],
    },
  }));
  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, true);
  assert.equal(store.documents.get("quest_review_requests/sub").status, "PUBLISHED");
  assert.ok(store.documents.get("lessons/l1"), "passing lesson published");
  assert.equal(store.documents.get("lessons/l2"), undefined, "failed lesson not published");
  assert.equal(store.documents.get("questions/q2"), undefined, "failed lesson's questions stay out");
});

test("REVIEW_DELAYED marks the submission without publishing and without settling the cycle", async () => {
  store.reset(world({
    outcome: {
      pipelineStatus: "REVIEW_DELAYED",
      failedQuestions: [],
      detectedLanguage: null,
      passingLessonIds: ["l1"],
      delayedStages: [{lessonId: "l2", stage: "TRANSLATE"}],
      reviewedAtMs: 500,
    },
  }));
  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});

  assert.equal(reply.published, false);
  const request = store.documents.get("quest_review_requests/sub");
  assert.equal(request.status, "REVIEW_DELAYED");
  assert.equal(store.documents.get("quests/quest"), undefined);
  assert.equal(store.documents.get("review_fee_cycles/sub"), undefined,
    "an unfinished cycle has no final distribution (OQ-2 parked)");
});

test("a foreign submission is indistinguishable from an absent one", async () => {
  store.reset(world({outcome: OUTCOME_REVIEWED}));
  await assert.rejects(
    () => functions.advanceQuestReviewOutcome.run({auth: {uid: "intruder"}, data: {submissionId: "sub"}}),
    (error) => error.code === "not-found",
  );
  assert.equal(store.documents.get("quest_review_requests/sub").status, "UNDER_REVIEW",
    "a denied advance changes nothing");
});

test("an advance without the outcome is a no-op, and a broken outcome is data-loss", async () => {
  store.reset(world());
  const reply = await functions.advanceQuestReviewOutcome.run(
    {auth: {uid: "author"}, data: {submissionId: "sub"}});
  assert.equal(reply.published, false);
  assert.equal(store.documents.get("quest_review_requests/sub").status, "UNDER_REVIEW");

  store.reset(world({
    outcome: {pipelineStatus: "WEIRD", failedQuestions: [], detectedLanguage: null,
      passingLessonIds: [], delayedStages: [], reviewedAtMs: 500},
  }));
  await assert.rejects(
    () => functions.advanceQuestReviewOutcome.run({auth: {uid: "author"}, data: {submissionId: "sub"}}),
    (error) => error.code === "data-loss",
  );
});
