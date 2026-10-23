"use strict";

/**
 * История 8.3: настройки приватной ссылки едут существующим конвейером SUBMIT_ARENA.
 *
 * Три вещи, которые здесь зажаты против реальных обработчиков `index.js` (не против обещаний):
 *  - подача приватного квеста пишет `privateQuizSettings` в документ квеста с дефолтами AD-29,
 *    а подача неприватного — не пишет настроек вовсе;
 *  - перезаявка обновляет три авторских поля (лимит, видимость, срок) и СОХРАНЯЕТ рантайм —
 *    `enabled` и `startedCount` остаются серверными (AD-29: изменение лимита сохраняет счётчик,
 *    перезаявка не открывает закрытую ссылку);
 *  - ручное закрытие ссылки (`quest_authoring.CLOSE_PRIVATE_QUIZ_LINK`) выключает дверь и
 *    отказывает новым стартам гейта, не трогая счётчик; срок в прошлом на сохранении отказывает.
 *
 * Ноль строк в книге движений от приватной подачи — поведение, разделяемое с 8.5, здесь оно
 * прикреплено к самой подаче: приватная заявка плате проверки не подлежит (AD-26/AD-29).
 *
 * Плацдарм — заглушка Firestore и перехват `firebase-admin` в кэше модулей: тот же приём, что
 * `arena-handler.test.js`, — обработчики исполняются настоящие, только база стоит в памяти.
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

const AUTHOR = "author-private-1";
const CATALOG = "private-quizzes-v1";
const QUEST = "quest-private-1";
const QUEST_PATH = `private/${AUTHOR}/catalogs/${CATALOG}/quests/${QUEST}`;
// Часы теста — от настоящих: срок ссылки на сохранении проверяется «в будущем», и
// зафиксированная дата устаревает раньше самого теста.
const NOW = Date.now() - 60_000;

function world(extra = {}) {
  return {
    [`profiles/${AUTHOR}`]: {},
    // Участники гейта и закрытия — доверенные профили: дверь стартует после requireProfile.
    "profiles/participant-1": {},
    "profiles/participant-2": {},
    "profiles/participant-x": {},
    [`catalogs/${CATALOG}`]: {questType: "REGULAR"},
    ...extra,
  };
}

function call(method, uid, data) {
  return functions[method].run({auth: {uid}, data});
}

/** Заявка приватного квеста: два вопроса, настройки в draft{} — как их собирает QuestArenaOutboxWriter. */
function privateSubmission(patch = {}) {
  const draft = {
    id: QUEST,
    catalogId: CATALOG,
    title: "Приватный квиз",
    description: null,
    defaultLanguage: "ru",
    defaultDifficulty: "HARD",
    publicQuestId: null,
    createdAtMs: NOW - 1000,
    updatedAtMs: NOW - 500,
    privateQuiz: {attemptLimit: 1, showResult: false, linkExpiresAtMs: null},
    ...patch.draft,
  };
  return {
    submissionId: patch.submissionId || "submission-private-1",
    draftId: "draft-1",
    requestedAtMs: NOW - 500,
    targetShelf: "arena",
    targetLessonIds: ["lesson-1"],
    draftTree: {sections: ["section-1"], themes: ["theme-1"], lessons: ["lesson-1"]},
    draft,
    sections: [{id: "section-1", draftId: "draft-1", title: "Раздел", order: 0}],
    themes: [{id: "theme-1", draftId: "draft-1", sectionId: "section-1", title: "Тема", order: 0}],
    lessons: [{id: "lesson-1", draftId: "draft-1", themeId: "theme-1", title: "Урок", order: 0}],
    questions: [
      {
        id: "q-1", draftId: "draft-1", lessonId: "lesson-1", type: "SingleChoice", language: "ru",
        languageLevel: 1, difficulty: "HARD", order: 0, text: "2+2?", imagePath: null,
        payload: JSON.stringify({type: "SingleChoice", text: "2+2?", imageUrl: null,
          options: [{id: "a", text: "3"}, {id: "b", text: "4"}], correctOptionId: "b"}),
        updatedAtMs: NOW - 500,
      },
      {
        id: "q-2", draftId: "draft-1", lessonId: "lesson-1", type: "SingleChoice", language: "ru",
        languageLevel: 1, difficulty: "HARD", order: 1, text: "2+3?", imagePath: null,
        payload: JSON.stringify({type: "SingleChoice", text: "2+3?", imageUrl: null,
          options: [{id: "a", text: "5"}, {id: "b", text: "6"}], correctOptionId: "a"}),
        updatedAtMs: NOW - 500,
      },
    ],
    review: {},
  };
}

async function submit(uid, submission) {
  return call("submitMutation", uid, {
    mutationId: `mut-${submission.submissionId}`,
    operation: "quest_authoring.SUBMIT_ARENA",
    payload: submission,
  });
}

function settings() {
  return store.documents.get(QUEST_PATH).privateQuizSettings;
}

test("a private submission persists the settings with AD-29 runtime defaults and writes no ledger row", async () => {
  store.reset(world());
  const result = await submit(AUTHOR, privateSubmission());
  assert.equal(result.replayed, false);

  const saved = settings();
  assert.deepEqual(
    {attemptLimit: saved.attemptLimit, showResult: saved.showResult, linkExpiresAtMs: saved.linkExpiresAtMs},
    {attemptLimit: 1, showResult: false, linkExpiresAtMs: null},
    "авторские поля сохранены как прислано",
  );
  assert.equal(saved.enabled, true, "новая ссылка рождается включённой");
  assert.equal(saved.startedCount, 0, "новая ссылка не имеет стартов");

  // Приватная подача не платит проверки и не двигает балансы: книга пуста.
  const ledger = [...store.documents.keys()].filter((path) => path.startsWith("audit/currency_ledger/"));
  assert.equal(ledger.length, 0, `приватная подача не пишет в книгу, а пишет: ${ledger.join(", ")}`);
  const user = store.documents.get(`users/${AUTHOR}`);
  assert.equal(user ? user.pointsNolics : undefined, undefined, "кошелёк автора не тронут");
});

test("an ordinary (non-private) submission writes no settings and still pays the review fee cycle", async () => {
  store.reset(world({
    "configs/review_fee": {revision: 1, initialSubmissionNolics: 10, recheckQuestionNolics: 1},
    [`users/${AUTHOR}`]: {pointsNolics: 100, nolics: 100},
  }));
  const submission = privateSubmission();
  delete submission.draft.privateQuiz;
  submission.questions = submission.questions.slice(0, 1);
  const result = await submit(AUTHOR, submission);
  assert.equal(result.replayed, false);

  const quest = store.documents.get(QUEST_PATH);
  assert.equal(quest.privateQuizSettings, undefined, "неприватная заявка настроек не пишет");
  assert.ok(store.documents.has(`review_fee_cycles/${submission.submissionId}`), "плата публичной подачи не отменена");
});

test("a resubmission updates the author fields and preserves enabled/startedCount runtime state", async () => {
  store.reset(world({
    [QUEST_PATH]: {
      id: QUEST,
      privateQuizSettings: {attemptLimit: 1, showResult: false, linkExpiresAtMs: null, enabled: true, startedCount: 1},
    },
    "configs/review_fee": {revision: 1, initialSubmissionNolics: 10, recheckQuestionNolics: 1},
    [`users/${AUTHOR}`]: {pointsNolics: 1000, nolics: 1000},
  }));
  const first = privateSubmission({submissionId: "submission-private-2"});
  await submit(AUTHOR, first);
  // Второй подачей автор поднимает лимит до 3 и открывает результат — одной стартовавшей
  // попытки это не касается, а счётчик остаётся.
  const second = privateSubmission({
    submissionId: "submission-private-3",
    draft: {privateQuiz: {attemptLimit: 3, showResult: true, linkExpiresAtMs: NOW + 86_400_000}},
  });
  await call("submitMutation", AUTHOR, {
    mutationId: "mut-second",
    operation: "quest_authoring.SUBMIT_ARENA",
    payload: second,
  });
  const saved = settings();
  assert.deepEqual(
    {attemptLimit: saved.attemptLimit, showResult: saved.showResult, linkExpiresAtMs: saved.linkExpiresAtMs},
    {attemptLimit: 3, showResult: true, linkExpiresAtMs: NOW + 86_400_000},
    "авторские поля обновлены",
  );
  assert.equal(saved.startedCount, 1, "перезаявка сохраняет счётчик стартов (AD-29)");
  assert.equal(saved.enabled, true, "перезаявка сохраняет состояние ссылки");

  // Закрытая вручную ссылка перезаявкой не открывается: reopen не назначен (UX-16).
  store.documents.get(QUEST_PATH).privateQuizSettings.enabled = false;
  await call("submitMutation", AUTHOR, {
    mutationId: "mut-third",
    operation: "quest_authoring.SUBMIT_ARENA",
    payload: privateSubmission({submissionId: "submission-private-4"}),
  });
  assert.equal(settings().enabled, false, "перезаявка не реанимирует закрытую ссылку");
  assert.equal(settings().startedCount, 1);
});

test("an expiry in the past refuses the submission before any write", async () => {
  store.reset(world());
  const before = new Map(store.documents);
  await assert.rejects(
    submit(AUTHOR, privateSubmission({
      draft: {privateQuiz: {attemptLimit: 1, showResult: false, linkExpiresAtMs: NOW - 1}},
    })),
    (error) => {
      assert.match(error.message, /PRIVATE_QUIZ_EXPIRY_IN_PAST/);
      return true;
    },
  );
  assert.deepEqual([...store.documents.keys()], [...before.keys()], "отказ не оставил документов");
});

test("a malformed privateQuiz in the draft refuses loudly", async () => {
  store.reset(world());
  await assert.rejects(
    submit(AUTHOR, privateSubmission({draft: {privateQuiz: {attemptLimit: 0}}})),
    /attemptLimit/,
  );
  await assert.rejects(
    submit(AUTHOR, privateSubmission({draft: {privateQuiz: "yes"}})),
    /draft.privateQuiz must be an object/,
  );
});

test("the manual close disables the link for new starts, keeps the counter, and journals the change", async () => {
  store.reset(world({
    [QUEST_PATH]: {
      id: QUEST,
      privateQuizSettings: {attemptLimit: 2, showResult: true, linkExpiresAtMs: null, enabled: true, startedCount: 1},
    },
  }));
  const result = await call("submitMutation", AUTHOR, {
    mutationId: "mut-close",
    operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
    payload: {catalogId: CATALOG, questId: QUEST},
  });
  assert.deepEqual(result.result, {closed: true});

  const saved = settings();
  assert.equal(saved.enabled, false, "ссылка закрыта");
  assert.equal(saved.startedCount, 1, "закрытие не трогает счётчик");
  assert.equal(saved.attemptLimit, 2, "закрытие не трогает лимит");

  // Дверь действительно закрыта для нового участника, а уже начатая попытка не при чём.
  await assert.rejects(
    call("startPrivateQuizAttempt", "participant-x", {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: "start-close-0001",
    }),
    (error) => {
      // Вызываемый оборачивает отказ двери в failed-precondition с человеческим текстом;
      // код двери назван в модуле, текст — единственное, что доезжает по проводу.
      assert.match(error.message, /link is disabled/);
      return true;
    },
  );

  // Повтор того же ключа — сохранённый ответ, второй записи журнала нет.
  const again = await call("submitMutation", AUTHOR, {
    mutationId: "mut-close",
    operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
    payload: {catalogId: CATALOG, questId: QUEST},
  });
  assert.equal(again.replayed, true);
  const journalPath = `private/${AUTHOR}/sync_changes/${CATALOG}_${QUEST}`;
  assert.ok(store.documents.has(journalPath), "закрытие донесено журналом приватного квеста");

  // Чужой квест закрыть нельзя: не-found, а не отказ с утечкой существования чужого.
  await assert.rejects(
    call("submitMutation", "stranger", {
      mutationId: "mut-close-other",
      operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
      payload: {catalogId: CATALOG, questId: QUEST},
    }),
    (error) => {
      assert.equal(error.code, "not-found");
      return true;
    },
  );
});

test("the close link refuses an unknown body field instead of silently ignoring it", async () => {
  store.reset(world({
    [QUEST_PATH]: {id: QUEST, privateQuizSettings: {attemptLimit: 1, enabled: true, startedCount: 0}},
  }));
  await assert.rejects(
    call("submitMutation", AUTHOR, {
      mutationId: "mut-close-bad",
      operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
      payload: {catalogId: CATALOG, questId: QUEST, enabled: true},
    }),
    /Unsupported close-link fields: enabled/,
  );
  assert.equal(settings().enabled, true, "отказ формы ничего не закрыл");
});

test("settings saved by the submission are the settings the gate opens the door by", async () => {
  // Круг замкнут: что сохранилось тем и открывается. Лимит 1 из заявки расходуется первым
  // стартом, второй отказывает лимитом, а не «не найдено» — документ и дверь читают одно поле.
  store.reset(world());
  await submit(AUTHOR, privateSubmission({
    draft: {privateQuiz: {attemptLimit: 1, showResult: false, linkExpiresAtMs: null}},
  }));

  await call("startPrivateQuizAttempt", "participant-1", {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: "start-roundtrip-01",
  });
  await assert.rejects(
    call("startPrivateQuizAttempt", "participant-2", {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: "start-roundtrip-02",
    }),
    (error) => {
      assert.match(error.message, /attempts of this private quiz are used/);
      return true;
    },
  );
  assert.equal(settings().startedCount, 1, "лимит из настроек заявки, а не дефолт гейта");
});
