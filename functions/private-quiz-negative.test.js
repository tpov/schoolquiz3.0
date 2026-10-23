"use strict";

/**
 * История 8.5: негативное доказательство приватного квиза — «забор, а не описание».
 *
 * Чего у приватного прохода не существует, держится с двух сторон:
 *  - поведенчески: полный цикл (подача заявки, старт, прогон, финиш, тайм-аут, закрытие
 *    ссылки) не оставляет в `audit/currency_ledger/entries` ни строки и не трогает кошелёк;
 *    повтор чтения результата тоже;
 *  - статически: серверные модули приватного квиза не упоминают ни одной платной формы —
 *    ни списаний, ни заявок на заряды, ни платы проверки, ни согласия с условиями; приватные
 *    операции не входят в серверный реестр согласий.
 *
 * Падение любого случая — момент, когда кто-то впервые завёл приватные деньги, и падает он
 * с именем шва.
 */

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
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

const AUTHOR = "neg-author-1";
const PARTICIPANT = "neg-participant-1";
const CATALOG = "private-quizzes-v1";
const QUEST = "quest-neg-1";
const QUEST_PATH = `private/${AUTHOR}/catalogs/${CATALOG}/quests/${QUEST}`;
const START_ID = "start-negative01";

function call(method, uid, data) {
  return functions[method].run({auth: {uid}, data});
}

function world() {
  return {
    [`profiles/${AUTHOR}`]: {},
    [`profiles/${PARTICIPANT}`]: {},
    [`users/${AUTHOR}`]: {pointsNolics: 0, nolics: 0},
    "configs/review_fee": {revision: 1, initialSubmissionNolics: 1000, recheckQuestionNolics: 100},
    "configs/economy": {revision: 1, activityPrice: {EASY: 1, HARD: 2}, chargePerAttempt: {EASY: 5, HARD: 5}},
    [QUEST_PATH]: {
      id: QUEST,
      submissionId: "submission-1",
      version: 1,
      privateQuizSettings: {attemptLimit: 1, showResult: true, linkExpiresAtMs: null, enabled: true, startedCount: 0},
    },
    [`${QUEST_PATH}/sections/s1`]: {id: "s1", order: 0},
    [`${QUEST_PATH}/sections/s1/themes/t1`]: {id: "t1", order: 0},
    [`${QUEST_PATH}/sections/s1/themes/t1/lessons/l1`]: {id: "l1", order: 0},
    [`${QUEST_PATH}/sections/s1/themes/t1/lessons/l1/questions/q1`]: {
      id: "q1", order: 0,
      payload: JSON.stringify({
        type: "SingleChoice", text: "1+1?", imageUrl: null,
        options: [{id: "a", text: "2"}, {id: "b", text: "3"}], correctOptionId: "a",
      }),
    },
  };
}

function ledgerPaths() {
  return [...store.documents.keys()].filter((candidate) => candidate.startsWith("audit/currency_ledger/"));
}

test("a full private cycle leaves the ledger empty and both wallets untouched", async () => {
  store.reset(world());
  await call("startPrivateQuizAttempt", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  await call("submitMutation", PARTICIPANT, {
    mutationId: "neg-submit",
    operation: "private_quiz.SUBMIT_ATTEMPT",
    payload: {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
      served: [{questionId: "q1", codeAnswerIndex: 0}],
      answers: [{
        questionId: "q1", codeAnswerIndex: 0,
        answerPayload: JSON.stringify({type: "single-choice", selected: "a"}),
        answeredAtMs: 1,
      }],
    },
    // Согласие с платными условиями приватной подаче не прикладывается — и не требуется:
    // отсутствие capturedTerms не отказывает, что и есть «консент не запрашивается».
  });
  await call("readPrivateQuizResult", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  await call("readPrivateQuizStatistics", AUTHOR, {catalogId: CATALOG, questId: QUEST});
  await call("submitMutation", AUTHOR, {
    mutationId: "neg-close",
    operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
    payload: {catalogId: CATALOG, questId: QUEST},
  });

  assert.deepEqual(ledgerPaths(), [], "книга движений пуста после полного приватного цикла");
  assert.equal(store.documents.get(`users/${AUTHOR}`).pointsNolics, 0, "кошелёк автора не тронут");
  const author = store.documents.get(`users/${AUTHOR}`);
  for (const field of ["standardChargePoints", "plasmaChargePoints", "lifePoints"]) {
    assert.equal(author[field], undefined, `${field} приватный цикл не читает и не пишет`);
  }
});

// ─── статический забор ────────────────────────────────────────────────────────────────────────

const PRIVATE_SERVER_FILES = ["private-quiz-gate.js", "private-quiz-run.js"];

/** Имена, чьё появление в приватном серверном коде означает заведённые приватные деньги. */
const FORBIDDEN_MONEY_MARKERS = [
  "ledgerEntr", // запись строки книги
  "LEDGER_PATH",
  "ATTEMPT_TOLL",
  "CHARGE_CLAIM",
  "REVIEW_FEE",
  "charge-claims",
  "arena-tickets",
  "attemptReward", // награда прохождения
  "lessonUnlockPrice", // плата разблокировки
  "MUTATION_TERMS_OPERATIONS",
  "capturedTerms",
  "chargeClaims",
];

/** Код без комментариев: забор читает исполняемый текст, а не документацию к нему. */
function codeWithoutComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/^\s*\/\/.*$/gm, "");
}

test("private quiz server modules carry no money vocabulary at all", () => {
  for (const name of PRIVATE_SERVER_FILES) {
    const source = codeWithoutComments(fs.readFileSync(path.join(__dirname, name), "utf8"));
    for (const marker of FORBIDDEN_MONEY_MARKERS) {
      assert.ok(
        !source.includes(marker),
        `${name} упоминает «${marker}» — у приватного прохода появилась платная форма`,
      );
    }
  }
});

test("the private handlers in index.js carry no money vocabulary", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("/*\n * Приватный квиз после двери");
  assert.ok(start > 0, "не нашёл блок приватного прогона в index.js");
  const end = source.indexOf("/** Авторская статистика — отдельный authorized scope", start);
  assert.ok(end > start, "не нашёл конец блока приватного прогона");
  const block = codeWithoutComments(source.slice(start, end));
  // Адрес попытки легально носит имя «submission» — подачи мутаций; платной лексики в блоке нет.
  for (const marker of FORBIDDEN_MONEY_MARKERS) {
    assert.ok(!block.includes(marker), `блок приватного прогона упоминает «${marker}»`);
  }
  // И приём подачи — тоже: реестр ссылается на обработчик, тело которого не трогает деньги.
  const handlerStart = source.indexOf("async function applyPrivateQuizAttemptSubmission");
  const handlerEnd = source.indexOf("/** Авторская статистика", handlerStart);
  const handler = codeWithoutComments(source.slice(handlerStart, handlerEnd));
  assert.ok(handler.length > 0, "не нашёл обработчик подачи приватной попытки");
  for (const marker of FORBIDDEN_MONEY_MARKERS) {
    assert.ok(!handler.includes(marker), `обработчик подачи упоминает «${marker}»`);
  }
});

test("the private submission pays no review fee and opens no review fee cycle", async () => {
  store.reset(world());
  const submission = {
    submissionId: "neg-private-submission",
    draftId: "draft-1",
    requestedAtMs: 1,
    targetShelf: "arena",
    targetLessonIds: ["l1"],
    draftTree: {sections: ["s1"], themes: ["t1"], lessons: ["l1"]},
    draft: {
      id: QUEST, catalogId: CATALOG, title: "Приватный", description: null,
      defaultLanguage: "ru", defaultDifficulty: "HARD", publicQuestId: null,
      createdAtMs: 1, updatedAtMs: 1,
      privateQuiz: {attemptLimit: 1, showResult: false, linkExpiresAtMs: null},
    },
    sections: [{id: "s1", draftId: "draft-1", title: "Вопросы", order: 0}],
    themes: [{id: "t1", draftId: "draft-1", sectionId: "s1", title: "Вопросы", order: 0}],
    lessons: [{id: "l1", draftId: "draft-1", themeId: "t1", title: "Вопросы", order: 0}],
    questions: [{
      id: "q1", draftId: "draft-1", lessonId: "l1", type: "SingleChoice", language: "ru",
      languageLevel: 1, difficulty: "HARD", order: 0, text: "1+1?", imagePath: null,
      payload: JSON.stringify({
        type: "SingleChoice", text: "1+1?", imageUrl: null,
        options: [{id: "a", text: "2"}, {id: "b", text: "3"}], correctOptionId: "a",
      }),
      updatedAtMs: 1,
    }],
    review: {},
  };
  await call("submitMutation", AUTHOR, {
    mutationId: "neg-private-feed",
    operation: "quest_authoring.SUBMIT_ARENA",
    payload: submission,
    expectedVersion: 1,
  });
  const cycles = [...store.documents.keys()].filter((candidate) => candidate.startsWith("review_fee_cycles/"));
  assert.deepEqual(cycles, [], "цикл платы проверки приватной заявке не открывается");
  assert.deepEqual(ledgerPaths(), [], "подача приватного квиза не пишет в книгу");
  assert.equal(store.documents.get(`users/${AUTHOR}`).pointsNolics, 0);
});

test("no deferral exists for a private attempt: the terminal is terminal, not a resumable state", () => {
  const {ATTEMPT_REASON, ATTEMPT_STATUS, projectOwnResult} = require("./private-quiz-run");
  // Тайм-аут — терминал: продолжения в проекции нет, «резюмируемого» статуса не существует.
  assert.deepEqual(
    Object.values(ATTEMPT_REASON).sort(),
    ["finished", "timeout"],
    "у приватной попытки ровно два исхода; третий («позже») — отложенное продолжение, которого нет",
  );
  assert.deepEqual(
    Object.values(ATTEMPT_STATUS).sort(),
    ["running", "terminal"],
    "у попытки нет resumable-состояния: либо бежит, либо закрыта",
  );
  const timeout = projectOwnResult({
    attempt: {status: ATTEMPT_STATUS.TERMINAL, reason: ATTEMPT_REASON.TIMEOUT, finishedAtMs: 1, percentScore: 0},
    showResult: true,
  });
  assert.equal(timeout.reason, ATTEMPT_REASON.TIMEOUT);
  assert.equal(timeout.resumable, undefined, "терминал не несёт продолжения");
});
