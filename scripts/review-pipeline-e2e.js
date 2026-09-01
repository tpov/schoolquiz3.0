"use strict";

/**
 * Multi-role scenarios: one quest travelling from an author through the review stages to
 * publication, seen by every kind of account the app can have.
 *
 * Companion to scripts/rules-emulator-test.js, and deliberately different in kind: that suite
 * proves the access boundary holds, this one proves the pipeline routes work to the right people
 * and settles results honestly. Both are needed — rules can be perfect while the routing sends a
 * translator a logic task, and routing can be perfect while the door stands open.
 *
 * Run through the emulator:
 *   firebase emulators:exec --only auth,firestore,functions "node scripts/review-pipeline-e2e.js"
 */

const assert = require("assert");
const admin = require("../functions/node_modules/firebase-admin");
const callableHandlers = require("../functions/index.js");

const PROJECT_ID = process.env.GCLOUD_PROJECT || "demo-schoolquiz";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error("FIRESTORE_EMULATOR_HOST is required. Run through firebase emulators:exec.");
}
if (admin.apps.length === 0) admin.initializeApp({projectId: PROJECT_ID});
const db = admin.firestore();

const QUALIFIED = 100;
const LESSON_ID = "les-review-1";
const QUEST_ID = "quest-review-1";
const CATALOG_ID = "cat-review";

/**
 * Source language is ru and the config demands en, which is what makes the translation stages
 * meaningful: a translator has real work to do, and the level gap decides who may review it.
 */
const ACTORS = {
  author: {},
  player: {},
  // A second ordinary player, kept clean of `player`'s attempt history: what an attempt pays
  // depends on the best already set on that lesson, so scenarios about anything other than the
  // anti-grind rule need a player who has not played it.
  playerTwo: {},
  tester: {testerLevel: QUALIFIED},
  translatorA: {translatorLevel: QUALIFIED, knownLanguages: ["ru", "en"]},
  translatorB: {translatorLevel: 200, knownLanguages: ["ru", "en"]},
  moderator: {moderatorLevel: QUALIFIED},
  seniorModerator: {moderatorLevel: 200},
  topModerator: {moderatorLevel: 301},
  admin: {adminLevel: QUALIFIED},
  developer: {developerLevel: 101, knownLanguages: ["ru", "en"]},
  // isReviewer() and every function gate use `> 100`, never `>= 100`. This account sits exactly on
  // the line and must be treated as an ordinary user.
  developerEdge: {developerLevel: QUALIFIED},
  collector: {},
  // Owns the name a full-width lookalike is checked against. It has to be an account that has
  // never worn a name: seeded profiles carry a nickname nobody could have been handed, which the
  // claims model reads as a name already chosen — and a second name is bought, not renamed into.
  lookalike: {},
  applicant: {},
  applicantTwo: {},
  trader: {},
  buyer: {},
  squatter: {},
};

const DAY_MS = 24 * 60 * 60 * 1000;

const passed = [];

/**
 * Assignment ids are `${submissionId}_${lessonId}`, and the lookup goes through
 * admin/review/sync_changes. Discovering it the way a client does — through the changes feed —
 * beats hardcoding the shape, and exercises that endpoint on the way.
 */
let assignmentId = null;

async function scenario(label, body) {
  await body();
  passed.push(label);
  console.log(`  ✓ ${label}`);
}

/**
 * Signed in with Google unless told otherwise. The provider used to be irrelevant, but verification
 * distinguishes an anonymous container for statistics from an account belonging to somebody.
 */
function call(name, uid, data, provider = "google.com") {
  const handler = callableHandlers[name];
  assert.ok(handler && typeof handler.run === "function", `Missing callable handler ${name}`);
  return handler.run({
    auth: uid ? {uid, token: {firebase: {sign_in_provider: provider}}} : undefined,
    data: data || {},
  });
}

async function callFails(name, uid, data, expectedCode, provider) {
  try {
    await call(name, uid, data, provider);
  } catch (error) {
    const code = String((error && error.code) || "").toUpperCase().replace(/-/g, "_");
    assert.strictEqual(code, expectedCode, `${name}: ожидался ${expectedCode}, получен ${code}`);
    return;
  }
  throw new Error(`${name} неожиданно выполнился успешно`);
}

async function seedActors() {
  for (const [uid, profile] of Object.entries(ACTORS)) {
    await db.collection("users").doc(uid).set({
      uid,
      nickname: uid,
      standardHearts: 5,
      lifePoints: 500,
      lifePointsUpdatedAtMs: Date.now(),
    }, {merge: true});
    await db.collection("profiles").doc(uid).set({
      uid,
      nickname: uid,
      testerLevel: 0,
      translatorLevel: 0,
      moderatorLevel: 0,
      adminLevel: 0,
      developerLevel: 0,
      knownLanguages: ["ru"],
      ...profile,
    }, {merge: true});
  }
  await db.doc("configs/arena_review").set({requiredLanguages: ["en"], updatedAtMs: Date.now()});
}

function submissionPayload(submissionId, overrides = {}) {
  const now = Date.now();
  const lessonId = overrides.lessonId || LESSON_ID;
  const questId = overrides.questId || QUEST_ID;
  const draftId = overrides.draftId || "draft-review-1";
  return {
    submissionId,
    draftId,
    ownerUid: "author",
    localRevision: 1,
    requestedAtMs: now,
    targetShelf: "arena",
    targetLessonIds: [lessonId],
    status: "PENDING",
    processed: false,
    draft: {
      id: questId,
      catalogId: CATALOG_ID,
      title: "Проверочный квест",
      description: "Для сквозного прогона",
      defaultLanguage: "ru",
      defaultDifficulty: "EASY",
      publicQuestId: null,
      createdAtMs: now,
      updatedAtMs: now,
    },
    sections: [{id: `sec-${draftId}`, draftId, title: "Раздел", order: 0}],
    themes: [
      {id: `thm-${draftId}`, draftId, sectionId: `sec-${draftId}`, title: "Тема", order: 0},
    ],
    lessons: [
      {id: lessonId, draftId, themeId: `thm-${draftId}`, title: "Урок", order: 0},
    ],
    questions: [
      {
        id: `qst-${draftId}`,
        draftId,
        lessonId,
        type: "SingleChoice",
        language: "ru",
        languageLevel: 0,
        difficulty: "EASY",
        order: 0,
        text: "Столица Франции?",
        imagePath: null,
        payload: "{}",
        updatedAtMs: now,
      },
    ],
    review: {},
  };
}

/** Waits for the onDocumentCreated trigger rather than calling the handler directly. */
async function waitForProcessed(submissionId, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const snapshot = await db.collection("quest_review_requests").doc(submissionId).get();
    const data = snapshot.data() || {};
    if (data.lastError) throw new Error(`триггер упал: ${data.lastError}`);
    if (data.processed === true) return data;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  throw new Error(`триггер не обработал ${submissionId} за ${timeoutMs} мс`);
}

async function tasksFor(uid) {
  const result = await call("fetchReviewAssignments", uid, {ids: [assignmentId]});
  const assignment = result.assignments[0];
  return assignment ? [...assignment.taskKinds].sort() : [];
}

// ─── Конвейер ревью ────────────────────────────────────────────────────────────────────────────

async function testSubmission() {
  await scenario("автор отправляет квест, триггер раскладывает его по хранилищам", async () => {
    await db.collection("quest_review_requests").doc("sub-1").set(submissionPayload("sub-1"));
    const processed = await waitForProcessed("sub-1");
    assert.strictEqual(processed.status, "UNDER_REVIEW");

    const adminTask = await db.doc(`admin/review/lessons/${LESSON_ID}`).get();
    assert.ok(adminTask.exists, "задача ревью не создана");

    const privateQuest = await db
      .doc(`private/author/catalogs/${CATALOG_ID}/quests/${QUEST_ID}`)
      .get();
    assert.ok(privateQuest.exists, "приватная копия автора не создана");

    const changes = await call("fetchReviewAssignmentChanges", "developer", {cursorMs: 0});
    assert.strictEqual(changes.changes.length, 1, "лента изменений не показала новую задачу");
    assignmentId = changes.changes[0].id;
  });
}

async function testRoleRouting() {
  await scenario("тестер видит только проверку содержания", async () => {
    assert.deepStrictEqual(await tasksFor("tester"), ["TESTING"]);
  });

  await scenario("этапы идут по очереди: логика закрыта, пока нет проверки содержания", async () => {
    // The pipeline is sequential, not parallel: openTasksFor opens exactly one stage at a time.
    // An admin is qualified for both TESTING and LOGIC, yet sees only TESTING while the lesson is
    // untested. Pinned deliberately — the ordering is what stops logic being signed off on content
    // nobody has read yet.
    assert.deepStrictEqual(await tasksFor("admin"), ["TESTING"]);
  });

  await scenario("разработчик тоже идёт по очереди, а не в обход", async () => {
    assert.deepStrictEqual(await tasksFor("developer"), ["TESTING"]);
  });

  await scenario("разработчик ровно 100 не получает ничего", async () => {
    assert.deepStrictEqual(await tasksFor("developerEdge"), []);
  });

  await scenario("модератор не участвует в ревью (права только на чат)", async () => {
    assert.deepStrictEqual(await tasksFor("moderator"), []);
  });

  await scenario("игрок без квалификаций не получает задач", async () => {
    assert.deepStrictEqual(await tasksFor("player"), []);
  });

  await scenario("аккаунт без доверенного профиля получает отказ", async () => {
    await callFails("fetchReviewAssignments", "stranger", {ids: [LESSON_ID]}, "NOT_FOUND");
  });

  await scenario("игрок не может сдать проверку, на которую не имеет прав", async () => {
    await callFails(
      "submitReviewAction",
      "player",
      {assignmentId, lessonId: LESSON_ID, kind: "TESTING", score: 3},
      "PERMISSION_DENIED",
    );
  });

  await scenario("тестер не может сдать проверку логики", async () => {
    await callFails(
      "submitReviewAction",
      "tester",
      {assignmentId, lessonId: LESSON_ID, kind: "LOGIC", score: 3},
      "PERMISSION_DENIED",
    );
  });

  /**
   * The author is barred from their own quest even when qualified — the level is granted for the
   * duration of one scenario and taken back. A developer is the one exemption, by the owner's
   * explicit rule, and the earlier developer scenarios already cover that side.
   */
  await scenario("автор с квалификацией не проверяет свой собственный квест", async () => {
    await db.collection("profiles").doc("author").set({testerLevel: QUALIFIED}, {merge: true});
    const assignments = await call("fetchReviewAssignments", "author", {ids: [LESSON_ID]});
    assert.strictEqual(
      (assignments.assignments || []).length, 0,
      "своя задача не должна предлагаться автору",
    );
    await callFails(
      "submitReviewAction",
      "author",
      {assignmentId, lessonId: LESSON_ID, kind: "TESTING", score: 3},
      "PERMISSION_DENIED",
    );
    await db.collection("profiles").doc("author").set({testerLevel: 0}, {merge: true});
  });
}

async function testReviewStages() {
  /**
   * The pass mark is 2 on the 1..3 scale. Before it existed, any score published — a reviewer
   * scoring 1 approved the quest exactly as one scoring 3 did. Run on its own submission so it
   * does not consume the reviewers' one attempt on the main quest.
   */
  await scenario("единица не публикует, отказ возвращает квест автору", async () => {
    const low = {lessonId: "les-low-1", questId: "quest-low-1", draftId: "draft-low-1"};
    await db.collection("quest_review_requests").doc("sub-low").set(
      submissionPayload("sub-low", low),
    );
    await waitForProcessed("sub-low");
    const lowAssignment = `sub-low_${low.lessonId}`;

    await call("submitReviewAction", "tester", {
      assignmentId: lowAssignment, lessonId: low.lessonId, kind: "TESTING", score: 1,
    });
    await call("submitReviewAction", "admin", {
      assignmentId: lowAssignment, lessonId: low.lessonId, kind: "LOGIC", score: 1,
    });
    const afterScores = await db.collection("quest_review_requests").doc("sub-low").get();
    assert.notStrictEqual(
      (afterScores.data() || {}).status, "PUBLISHED",
      "квест с единицами не должен публиковаться",
    );
    assert.ok(
      !(await db.collection("quests").doc(low.questId).get()).exists,
      "квест с единицами не должен появиться в публичной коллекции",
    );

    await callFails(
      "rejectReviewSubmission", "admin", {assignmentId: lowAssignment}, "INVALID_ARGUMENT",
    );
    await callFails(
      "rejectReviewSubmission", "player",
      {assignmentId: lowAssignment, reason: "не нравится"}, "PERMISSION_DENIED",
    );
    await call("rejectReviewSubmission", "admin", {
      assignmentId: lowAssignment, reason: "Один вопрос и тот без вариантов",
    });
    const rejected = await db.collection("quest_review_requests").doc("sub-low").get();
    assert.strictEqual((rejected.data() || {}).status, "REJECTED");
    assert.strictEqual(
      (rejected.data() || {}).rejectionReason, "Один вопрос и тот без вариантов",
      "причина отказа не доехала до автора",
    );
  });

  await scenario("тестер сдаёт проверку содержания", async () => {
    await call("submitReviewAction", "tester", {
      assignmentId, lessonId: LESSON_ID, kind: "TESTING", score: 3,
    });
    const task = await db.doc(`admin/review/lessons/${LESSON_ID}`).get();
    assert.strictEqual((task.data() || {}).isTested, true, "проверка содержания не записалась");
  });

  await scenario("после проверки содержания админу открывается логика", async () => {
    assert.deepStrictEqual(await tasksFor("admin"), ["LOGIC"]);
    assert.deepStrictEqual(await tasksFor("tester"), [], "тестеру логика недоступна");
  });

  await scenario("админ сдаёт проверку логики", async () => {
    await call("submitReviewAction", "admin", {
      assignmentId, lessonId: LESSON_ID, kind: "LOGIC", score: 3,
    });
    const task = await db.doc(`admin/review/lessons/${LESSON_ID}`).get();
    assert.strictEqual((task.data() || {}).isLogicReviewed, true, "проверка логики не записалась");
  });

  await scenario("после двух проверок переводчику открывается перевод", async () => {
    assert.deepStrictEqual(await tasksFor("translatorA"), ["TRANSLATION"]);
  });

  await scenario("переводчик сдаёт перевод", async () => {
    await call("submitReviewAction", "translatorA", {
      assignmentId,
      lessonId: LESSON_ID,
      kind: "TRANSLATION",
      score: 3,
      language: "en",
      translatedQuestions: [
        {
          id: "qst-review-1-en",
          draftId: "draft-review-1",
          lessonId: LESSON_ID,
          type: "SingleChoice",
          language: "en",
          languageLevel: QUALIFIED,
          difficulty: "EASY",
          order: 0,
          text: "Capital of France?",
          imagePath: null,
          payload: "{}",
          updatedAtMs: Date.now(),
        },
      ],
    });
    const task = await db.doc(`admin/review/lessons/${LESSON_ID}`).get();
    const translated = (task.data() || {}).translatedLanguages || {};
    assert.ok(translated.en >= QUALIFIED, `перевод не зачтён: ${JSON.stringify(translated)}`);
  });

  await scenario("свой перевод переводчик ревьюить не может", async () => {
    // Level gap is exactly TRANSLATION_REVIEW_LEVEL_GAP: at 100 against a translation made at 100
    // the threshold is 0, so nothing is offered. Otherwise a translator would rubber-stamp himself.
    assert.deepStrictEqual(await tasksFor("translatorA"), []);
  });

  await scenario("переводчик уровнем выше получает ревью перевода", async () => {
    assert.deepStrictEqual(await tasksFor("translatorB"), ["TRANSLATION_REVIEW"]);
  });
}

async function testPublication() {
  await scenario("квест публикуется и становится виден всем", async () => {
    const published = await call("processPendingArenaRequests", "developer", {limit: 10});
    assert.ok(published, "обработка не вернула результат");

    const request = await db.collection("quest_review_requests").doc("sub-1").get();
    assert.strictEqual((request.data() || {}).status, "PUBLISHED", "заявка не переведена в PUBLISHED");

    const quest = await db.collection("quests").doc(QUEST_ID).get();
    assert.ok(quest.exists, "публичный квест не создан");
    assert.ok(
      (quest.data() || {}).visibleOn.length > 0,
      "у опубликованного квеста пустой visibleOn — его никто не увидит",
    );
  });

  await scenario("содержание опубликованного квеста доступно по плоским коллекциям", async () => {
    const lesson = await db.collection("lessons").doc(LESSON_ID).get();
    assert.ok(lesson.exists, "урок не выложен в публичную коллекцию");
    const questions = await db.collection("questions").where("lessonId", "==", LESSON_ID).get();
    assert.ok(questions.size > 0, "вопросы не выложены");
  });
}

// ─── Результаты и жизни ────────────────────────────────────────────────────────────────────────

/** Mirrors a real client: percentScore is always derived from codeAnswer, never invented. */
function codeAnswerForPercent(percent) {
  const whole = Math.max(0, Math.min(100, Math.round(percent)));
  return "9".repeat(whole) + "1".repeat(100 - whole);
}

function attemptFor(uid, attemptId, percent, overrides) {
  const completedAtMs = Date.parse("2026-06-01T10:00:00.000Z");
  return {
    userId: uid,
    attemptId,
    scope: "public",
    catalogId: CATALOG_ID,
    questId: QUEST_ID,
    sectionId: "sec-review-1",
    themeId: "thm-review-1",
    lessonId: LESSON_ID,
    lessonVersion: 1,
    sourceShelf: "arena",
    difficulty: "EASY",
    codeAnswer: codeAnswerForPercent(percent),
    percentScore: Math.round(percent),
    completedAtMs,
    createdAtMs: completedAtMs - 1000,
    ...overrides,
  };
}

async function testResults() {
  await scenario("честный результат засчитывается и списывает жизни", async () => {
    const before = (await db.collection("users").doc("player").get()).data().lifePoints;
    const result = await call("submitLessonResultEvents", "player", {
      attempts: [attemptFor("player", "attempt-honest", 75)],
    });
    assert.strictEqual(result.accepted, 1);
    assert.ok(result.lifePoints < before, `жизни не списались: было ${before}, стало ${result.lifePoints}`);
  });

  await scenario("подделанный процент не приносит награды", async () => {
    // codeAnswer says 10%, the payload claims 100%. An honest client cannot produce this.
    const forged = attemptFor("player", "attempt-forged", 10, {percentScore: 100});
    const result = await call("submitLessonResultEvents", "player", {attempts: [forged]});
    assert.strictEqual(
      result.reward.skillPoints, 0,
      "за подделанный результат начислены очки",
    );
  });

  await scenario("повторная отправка того же результата не начисляет второй раз", async () => {
    // Run by playerTwo, who has never played this lesson. The subject here is the attempt id, not
    // the anti-grind rule: `player` already holds a best of 75 on it, and against that best a 60%
    // run is entirely repeated percent, paid at a tenth and rounded to nothing — the first
    // submission would earn zero and the repeat would be indistinguishable from it.
    const attempt = attemptFor("playerTwo", "attempt-once", 60);
    const first = await call("submitLessonResultEvents", "playerTwo", {attempts: [attempt]});
    const second = await call("submitLessonResultEvents", "playerTwo", {attempts: [attempt]});
    assert.ok(first.reward.skillPoints > 0, "первая отправка не начислила очки");
    assert.strictEqual(second.reward.skillPoints, 0, "повтор начислил очки второй раз");
  });

  await scenario("на исчерпанных жизнях попытка не оплачивается", async () => {
    await db.collection("users").doc("tester").set(
      {lifePoints: 0, lifePointsUpdatedAtMs: Date.now(), standardHearts: 5},
      {merge: true},
    );
    const result = await call("submitLessonResultEvents", "tester", {
      attempts: [attemptFor("tester", "attempt-broke", 90)],
    });
    assert.strictEqual(result.reward.skillPoints, 0, "награда выдана при нулевых жизнях");
  });

  await scenario("чужой результат под своим именем не принимается", async () => {
    await callFails(
      "submitLessonResultEvents",
      "player",
      {attempts: [attemptFor("author", "attempt-impersonated", 80)]},
      "PERMISSION_DENIED",
    );
  });
}

// ─── Коробки и ежедневная серия ────────────────────────────────────────────────────────────────

/**
 * The daily streak is the one place where gold could be inflated, because "a day passed" is the
 * kind of fact a device could otherwise assert for itself. It cannot here: nextBoxAtMs is stored
 * server-side and compared against the server clock, and no callable accepts a timestamp. These
 * scenarios hold that shut.
 */

async function setBoxState(uid, state) {
  await db.collection("users").doc(uid).set(state, {merge: true});
}

async function boxStateOf(uid) {
  const data = (await db.collection("users").doc(uid).get()).data() || {};
  return {boxCount: data.boxCount || 0, boxStreakDays: data.boxStreakDays || 0};
}

async function testGiftBoxes() {
  await scenario("серия не растёт, пока не прошли сутки", async () => {
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 3, nextBoxAtMs: Date.now() + DAY_MS,
    });
    await call("ensureUserProfile", "collector", {});
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 0, boxStreakDays: 3});
  });

  await scenario("сутки спустя серия растёт, но коробки ещё нет", async () => {
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 3, nextBoxAtMs: Date.now() - 1000,
    });
    await call("ensureUserProfile", "collector", {});
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 0, boxStreakDays: 4});
  });

  await scenario("на десятом заходе выдаётся коробка", async () => {
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 9, nextBoxAtMs: Date.now() - 1000,
    });
    await call("ensureUserProfile", "collector", {});
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 1, boxStreakDays: 10});
  });

  await scenario("больше одной коробки в день не выдаётся", async () => {
    // The previous scenario left nextBoxAtMs a day out. Calling again changes nothing.
    await call("ensureUserProfile", "collector", {});
    await call("ensureUserProfile", "collector", {});
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 1, boxStreakDays: 10});
  });

  await scenario("подсунутое клиентом время и счётчики игнорируются", async () => {
    // A tampered client can send whatever it likes in the payload; none of it is read.
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 9, nextBoxAtMs: Date.now() + DAY_MS,
    });
    await call("ensureUserProfile", "collector", {
      now: Date.now() + 400 * DAY_MS,
      nextBoxAtMs: 0,
      boxCount: 99,
      boxStreakDays: 99,
    });
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 0, boxStreakDays: 9});
  });

  await scenario("пропуск дней серию не обнуляет", async () => {
    // Current behaviour, pinned rather than endorsed: the streak counts ten separate days at least
    // a day apart, not ten consecutive ones. Coming back after a year continues where it stopped.
    // If "серия заходов" is meant to break on a missed day, this is the place to change it.
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 5, nextBoxAtMs: Date.now() - 400 * DAY_MS,
    });
    await call("ensureUserProfile", "collector", {});
    assert.deepStrictEqual(await boxStateOf("collector"), {boxCount: 0, boxStreakDays: 6});
  });

  await scenario("коробку нельзя открыть, когда её нет", async () => {
    await setBoxState("collector", {
      boxCount: 0, boxStreakDays: 10, nextBoxAtMs: Date.now() + DAY_MS,
    });
    await callFails("openGiftBox", "collector", {}, "FAILED_PRECONDITION");
  });

  await scenario("открытие списывает ровно одну коробку", async () => {
    await setBoxState("collector", {
      boxCount: 2, boxStreakDays: 10, nextBoxAtMs: Date.now() + DAY_MS,
    });
    const reward = await call("openGiftBox", "collector", {});
    assert.ok(reward, "открытие не вернуло награду");
    assert.strictEqual((await boxStateOf("collector")).boxCount, 1);
  });
}

// ─── Подтверждение аккаунта ────────────────────────────────────────────────────────────────────

/**
 * The ladder is anonymous -> registered -> validated. The last step is the only one a machine
 * cannot take: a person reads what somebody says about themselves, reaches them on telegram, and
 * decides. These scenarios cover who may file, who may decide, and what the decision changes.
 */

const DETAILS = {realName: "Олег", birthday: "1990-05-07", city: "Киев", telegram: "@tpov_dev"};

async function userDoc(uid) {
  return (await db.collection("users").doc(uid).get()).data() || {};
}

async function testVerification() {
  await scenario("анонимный аккаунт заявку подать не может", async () => {
    await callFails(
      "submitVerificationRequest", "applicant", DETAILS, "FAILED_PRECONDITION", "anonymous",
    );
  });

  await scenario("данные проверяются: непохожий телеграм и несуществующая дата", async () => {
    await callFails(
      "submitVerificationRequest", "applicant", {...DETAILS, telegram: "abc"}, "INVALID_ARGUMENT",
    );
    await callFails(
      "submitVerificationRequest", "applicant", {...DETAILS, birthday: "2026-02-31"}, "INVALID_ARGUMENT",
    );
  });

  await scenario("вошедший через Google подаёт заявку", async () => {
    const result = await call("submitVerificationRequest", "applicant", DETAILS);
    assert.strictEqual(result.status, "PENDING");
    const stored = (await db.collection("verification_requests").doc("applicant").get()).data();
    // The @ is stripped so a reviewer always sees one form.
    assert.strictEqual(stored.telegram, "tpov_dev");
    assert.strictEqual(stored.processed, false);
  });

  await scenario("вторую заявку поверх ожидающей подать нельзя", async () => {
    await callFails("submitVerificationRequest", "applicant", DETAILS, "FAILED_PRECONDITION");
  });

  await scenario("заявку видят админ и разработчик", async () => {
    for (const uid of ["admin", "developer"]) {
      const result = await call("fetchVerificationRequests", uid, {});
      const uids = result.requests.map((entry) => entry.uid);
      assert.ok(uids.includes("applicant"), `${uid} не увидел заявку: ${uids}`);
    }
  });

  await scenario("проверяющие по содержанию к персональным данным не допускаются", async () => {
    for (const uid of ["tester", "translatorA", "moderator", "player", "developerEdge"]) {
      await callFails("fetchVerificationRequests", uid, {}, "PERMISSION_DENIED");
    }
  });

  await scenario("решение выносит только админ или разработчик", async () => {
    await callFails(
      "decideVerification", "tester", {uid: "applicant", decision: "APPROVED"}, "PERMISSION_DENIED",
    );
  });

  await scenario("сам себя подтвердить нельзя", async () => {
    await call("submitVerificationRequest", "admin", DETAILS);
    await callFails(
      "decideVerification", "admin", {uid: "admin", decision: "APPROVED"}, "PERMISSION_DENIED",
    );
  });

  await scenario("подтверждение выдаёт галочку и переводит статус", async () => {
    await call("decideVerification", "admin", {uid: "applicant", decision: "APPROVED"});
    const user = await userDoc("applicant");
    assert.ok(user.trophies.includes("verified"), `галочки нет: ${JSON.stringify(user.trophies)}`);
    assert.strictEqual(user.verifiedByUid, "admin");
    const profile = (await db.collection("profiles").doc("applicant").get()).data();
    assert.strictEqual(profile.status, "VALIDATED");
  });

  await scenario("подтверждённый заявку больше не подаёт", async () => {
    await callFails("submitVerificationRequest", "applicant", DETAILS, "FAILED_PRECONDITION");
  });

  await scenario("решённую заявку второй раз не решить", async () => {
    await callFails(
      "decideVerification", "developer", {uid: "applicant", decision: "REJECTED"},
      "FAILED_PRECONDITION",
    );
  });

  await scenario("отказ статуса не меняет и допускает повторную подачу", async () => {
    await call("submitVerificationRequest", "applicantTwo", DETAILS);
    await call("decideVerification", "developer", {
      uid: "applicantTwo", decision: "REJECTED", reason: "Телеграм не отвечает",
    });

    const user = await userDoc("applicantTwo");
    assert.ok(!(user.trophies || []).includes("verified"), "отказ выдал галочку");
    const stored = (await db.collection("verification_requests").doc("applicantTwo").get()).data();
    assert.strictEqual(stored.rejectionReason, "Телеграм не отвечает");

    // A rejection is meant to be answered, not to be a dead end.
    const retry = await call("submitVerificationRequest", "applicantTwo", DETAILS);
    assert.strictEqual(retry.status, "PENDING");
  });

  await scenario("квалификация сама по себе больше не делает аккаунт подтверждённым", async () => {
    // This used to be automatic: any qualification meant VALIDATED, so a tester counted as checked
    // without anyone having looked at them.
    const result = await call("ensureUserProfile", "tester", {});
    assert.notStrictEqual(result.status, "VALIDATED", "тестер снова стал подтверждённым сам собой");
  });
}

// ─── Занятость ника ────────────────────────────────────────────────────────────────────────────

/**
 * Uniqueness was already enforced when saving; what was missing is telling somebody while they
 * type. The check is a hint and not a reservation, and it must never say who holds a taken name.
 */

async function availability(uid, nickname, provider) {
  return call("checkNicknameAvailability", uid, {nickname}, provider);
}

async function testNicknameAvailability() {
  await db.doc("configs/nickname_policy").set({
    blockedWords: ["дурак"],
    blockedSymbols: ["@"],
  });

  await scenario("свободное имя показывается свободным", async () => {
    const result = await availability("player", "СовсемНовыйНик");
    assert.strictEqual(result.available, true);
    assert.strictEqual(result.reason, null);
  });

  await scenario("занятое чужим показывается занятым и без владельца", async () => {
    await call("updateUserNickname", "author", {nickname: "ЗанятоеИмя"});
    const result = await availability("player", "ЗанятоеИмя");
    assert.strictEqual(result.available, false);
    assert.strictEqual(result.reason, "taken");
    // Returning the owner would make this a nickname-to-account directory.
    assert.ok(
      !JSON.stringify(result).includes("author"),
      `в ответе просочился владелец: ${JSON.stringify(result)}`,
    );
  });

  await scenario("собственное имя занятым не выглядит", async () => {
    const result = await availability("author", "ЗанятоеИмя");
    assert.strictEqual(result.available, true);
    assert.strictEqual(result.reason, "yours");
  });

  await scenario("регистр и полноширинные знаки занятость не обходят", async () => {
    for (const variant of ["занятоеимя", "ЗАНЯТОЕИМЯ", "  ЗанятоеИмя  "]) {
      const result = await availability("player", variant);
      assert.strictEqual(result.available, false, `${variant} прошло как свободное`);
    }
    await call("updateUserNickname", "lookalike", {nickname: "abcdef"});
    const wide = await availability("player", "ａｂｃｄｅｆ");
    assert.strictEqual(wide.available, false, "полноширинный двойник прошёл как свободный");
  });

  await scenario("правила ника отвечают кодом, а не отказом", async () => {
    assert.strictEqual((await availability("player", "ab")).reason, "too-short");
    assert.strictEqual((await availability("player", "x".repeat(25))).reason, "too-long");
    assert.strictEqual((await availability("player", "большой дурак")).reason, "blocked-word");
    assert.strictEqual((await availability("player", "me@you")).reason, "blocked-symbol");
  });

  await scenario("без входа проверить нельзя", async () => {
    await callFails("checkNicknameAvailability", null, {nickname: "ЧтоУгодно"}, "UNAUTHENTICATED");
  });

  await scenario("подсказка не бронирует имя", async () => {
    // Free a moment ago, claimed by somebody else before the save — the save must still refuse.
    const before = await availability("player", "Гонка");
    assert.strictEqual(before.available, true);
    await call("updateUserNickname", "applicant", {nickname: "Гонка"});
    await callFails("updateUserNickname", "player", {nickname: "Гонка"}, "ALREADY_EXISTS");
  });
}

// ─── Владение никами и витрина NFT ─────────────────────────────────────────────────────────────

/**
 * A name is held, not rented: switching away from one no longer surrenders it. One account owns
 * several and wears exactly one. The first self-chosen name is free, further ones cost gold, and
 * names change hands through the shop — between verified accounts only.
 */

async function goldOf(uid) {
  return numberOr((await db.collection("users").doc(uid).get()).data(), "gold");
}

function numberOr(data, field) {
  return Number((data || {})[field] || 0);
}

async function ownerOf(nickname) {
  const snapshot = await db.collection("nickname_claims").where("canonical", "==", nickname.toLowerCase()).get();
  return snapshot.empty ? null : (snapshot.docs[0].data() || {}).uid;
}

async function verify(uid) {
  await db.collection("users").doc(uid).set(
    {trophies: ["verified"], gold: 100}, {merge: true},
  );
}

async function testNicknameOwnership() {
  await db.doc("configs/nickname_policy").set({
    blockedWords: ["дурак"],
    blockedSymbols: ["@"],
    extraNicknamePrice: 2,
    saleCommissionPercent: 10,
  });

  await scenario("первое выбранное имя достаётся бесплатно", async () => {
    await db.collection("users").doc("trader").set({gold: 10}, {merge: true});
    const result = await call("claimNickname", "trader", {nickname: "ПервыйНик"});
    assert.strictEqual(result.charged, 0);
    assert.strictEqual(await goldOf("trader"), 10, "за первое имя списали золото");
  });

  await scenario("второе имя стоит золота", async () => {
    // Priced by nicknameAskingPrice, the same rule the neighbouring scenarios already assume: a
    // name of ordinary length costs one gold once the account has a name of its own. The knob in
    // configs/nickname_policy is not what decides it — claimNickname never reads that field.
    const result = await call("claimNickname", "trader", {nickname: "ВторойНик"});
    assert.strictEqual(result.charged, 1);
    assert.strictEqual(await goldOf("trader"), 9);
  });

  await scenario("переключение активного не отбирает прежнее имя", async () => {
    // This is the behaviour that changed: a name used to be surrendered the moment you switched.
    await call("setActiveNickname", "trader", {nickname: "ПервыйНик"});
    assert.strictEqual(await ownerOf("первыйник"), "trader");
    assert.strictEqual(await ownerOf("второйник"), "trader", "прежнее имя освободилось");
    const owned = await call("fetchOwnedNicknames", "trader", {});
    assert.strictEqual(owned.nicknames.filter((n) => !n.generated).length, 2);
    assert.strictEqual(owned.nicknames.find((n) => n.active).nickname, "ПервыйНик");
  });

  await scenario("на чужое и несуществующее имя переключиться нельзя", async () => {
    await callFails("setActiveNickname", "buyer", {nickname: "ПервыйНик"}, "PERMISSION_DENIED");
    await callFails("setActiveNickname", "trader", {nickname: "НетТакого"}, "PERMISSION_DENIED");
  });

  await scenario("смена ника не списывает золото молча, а требует покупки", async () => {
    const before = await goldOf("trader");
    await callFails("updateUserNickname", "trader", {nickname: "ТретийНик"}, "FAILED_PRECONDITION");
    assert.strictEqual(await goldOf("trader"), before, "золото ушло без явной покупки");
  });

  await scenario("без золота имя не купить", async () => {
    await db.collection("users").doc("squatter").set({gold: 0}, {merge: true});
    await call("claimNickname", "squatter", {nickname: "БесплатныйСквоттер"});
    await callFails("claimNickname", "squatter", {nickname: "ЕщёОдин"}, "FAILED_PRECONDITION");
    assert.strictEqual(await ownerOf("ещёодин"), null, "имя закрепилось без оплаты");
  });
}

async function testNicknameMarket() {
  await scenario("неподтверждённый продавать не может", async () => {
    await callFails(
      "listNicknameForSale", "trader", {nickname: "ВторойНик", price: 5}, "PERMISSION_DENIED",
    );
  });

  await scenario("активное имя выставить нельзя", async () => {
    await verify("trader");
    await callFails(
      "listNicknameForSale", "trader", {nickname: "ПервыйНик", price: 5}, "FAILED_PRECONDITION",
    );
  });

  await scenario("чужое имя выставить нельзя", async () => {
    await verify("buyer");
    await callFails(
      "listNicknameForSale", "buyer", {nickname: "ВторойНик", price: 5}, "PERMISSION_DENIED",
    );
  });

  await scenario("своё неактивное имя выставляется", async () => {
    await call("listNicknameForSale", "trader", {nickname: "ВторойНик", price: 10});
    const listings = await call("fetchNicknameListings", "buyer", {});
    const lot = listings.listings.find((entry) => entry.nickname === "ВторойНик");
    assert.ok(lot, "лот не появился на витрине");
    assert.strictEqual(lot.price, 10);
    // A seller is shown by the name they wear, never by uid.
    assert.ok(!JSON.stringify(lot).includes("trader"), `в лоте просочился uid: ${JSON.stringify(lot)}`);
  });

  await scenario("неподтверждённый купить не может", async () => {
    await db.collection("users").doc("squatter").set({gold: 100}, {merge: true});
    await callFails("buyListedNickname", "squatter", {nickname: "ВторойНик"}, "PERMISSION_DENIED");
  });

  await scenario("свой собственный лот не купить", async () => {
    await callFails("buyListedNickname", "trader", {nickname: "ВторойНик"}, "FAILED_PRECONDITION");
  });

  await scenario("при нехватке золота сделка не проходит и ничего не меняется", async () => {
    await db.collection("users").doc("buyer").set({gold: 3}, {merge: true});
    await callFails("buyListedNickname", "buyer", {nickname: "ВторойНик"}, "FAILED_PRECONDITION");
    assert.strictEqual(await ownerOf("второйник"), "trader", "имя ушло без оплаты");
    assert.strictEqual(await goldOf("buyer"), 3);
  });

  await scenario("покупка переносит имя, списывает и платит за вычетом комиссии", async () => {
    await db.collection("users").doc("buyer").set({gold: 50}, {merge: true});
    const sellerBefore = await goldOf("trader");

    const receipt = await call("buyListedNickname", "buyer", {nickname: "ВторойНик"});
    assert.deepStrictEqual(
      {paid: receipt.paid, commission: receipt.commission}, {paid: 10, commission: 1},
    );
    assert.strictEqual(await ownerOf("второйник"), "buyer", "имя не перешло");
    assert.strictEqual(await goldOf("buyer"), 40, "с покупателя списали не ту сумму");
    // The commission leaves circulation rather than landing in somebody's pocket.
    assert.strictEqual(await goldOf("trader"), sellerBefore + 9, "продавцу пришло не то");
  });

  await scenario("проданный лот исчезает с витрины", async () => {
    const listings = await call("fetchNicknameListings", "buyer", {});
    assert.ok(!listings.listings.some((entry) => entry.nickname === "ВторойНик"));
    await callFails("buyListedNickname", "buyer", {nickname: "ВторойНик"}, "NOT_FOUND");
  });

  await scenario("снятый лот купить нельзя", async () => {
    await call("claimNickname", "trader", {nickname: "НаПродажу"});
    await call("setActiveNickname", "trader", {nickname: "ПервыйНик"});
    await call("listNicknameForSale", "trader", {nickname: "НаПродажу", price: 4});
    await call("cancelNicknameListing", "trader", {nickname: "НаПродажу"});
    await callFails("buyListedNickname", "buyer", {nickname: "НаПродажу"}, "NOT_FOUND");
    assert.strictEqual(await ownerOf("напродажу"), "trader");
  });

  await scenario("чужой лот снять нельзя", async () => {
    await call("listNicknameForSale", "trader", {nickname: "НаПродажу", price: 4});
    await callFails("cancelNicknameListing", "buyer", {nickname: "НаПродажу"}, "PERMISSION_DENIED");
  });
}


// ─── Жалобы, вердикты и баны ───────────────────────────────────────────────────────────────────

async function testReportsAndBans() {
  await scenario("жалобу подаёт любой, но не на себя и не дважды", async () => {
    await db.collection("lessonComments").doc("c-spam").set({
      lessonId: LESSON_ID, authorUid: "author", authorNickname: "author",
      text: "Реклама", createdAt: Date.now(),
    });
    await callFails("submitReport", "player", {targetType: "USER", targetId: "player", reason: "я"},
      "INVALID_ARGUMENT");
    await callFails("submitReport", "player", {targetType: "COMMENT", targetId: "c-spam"},
      "INVALID_ARGUMENT");
    const filed = await call("submitReport", "player",
      {targetType: "COMMENT", targetId: "c-spam", reason: "реклама"});
    assert.strictEqual(filed.status, "OPEN");
    await callFails("submitReport", "player",
      {targetType: "COMMENT", targetId: "c-spam", reason: "ещё раз"}, "ALREADY_EXISTS");
  });

  await scenario("решает только модератор, и не свою жалобу", async () => {
    const reportId = "comment_c-spam_player";
    await callFails("decideReport", "tester", {reportId, decision: "UPHOLD"}, "PERMISSION_DENIED");
    await callFails("decideReport", "player", {reportId, decision: "UPHOLD"}, "PERMISSION_DENIED");
  });

  await scenario("подтверждённая жалоба даёт жалобщику плюс, отклонённая — минус", async () => {
    const before = await reputationOf("player");
    await call("decideReport", "moderator",
      {reportId: "comment_c-spam_player", decision: "UPHOLD"});
    const afterUphold = await reputationOf("player");
    assert.strictEqual(afterUphold, before + 3, "за подтверждённую жалобу не начислено");

    await db.collection("lessonComments").doc("c-fine").set({
      lessonId: LESSON_ID, authorUid: "author", authorNickname: "author",
      text: "Нормальный", createdAt: Date.now(),
    });
    await call("submitReport", "player",
      {targetType: "COMMENT", targetId: "c-fine", reason: "не нравится"});
    await call("decideReport", "moderator",
      {reportId: "comment_c-fine_player", decision: "REJECT"});
    assert.strictEqual(await reputationOf("player"), afterUphold - 3, "за отклонённую не списано");
  });

  await scenario("срок бана зависит от уровня выдавшего", async () => {
    const ban = async (moderatorUid, commentId) => {
      await db.collection("lessonComments").doc(commentId).set({
        lessonId: LESSON_ID, authorUid: "squatter", authorNickname: "squatter",
        text: "Плохо", createdAt: Date.now(),
      });
      await call("submitReport", "collector",
        {targetType: "COMMENT", targetId: commentId, reason: "оскорбление"});
      const result = await call("decideReport", moderatorUid,
        {reportId: `comment_${commentId}_collector`, decision: "UPHOLD_AND_BAN"});
      await db.collection("users").doc("squatter").set({
        bannedAtMs: admin.firestore.FieldValue.delete(),
        bannedUntilMs: admin.firestore.FieldValue.delete(),
      }, {merge: true});
      return result.banUntilMs;
    };
    const week = await ban("moderator", "c-ban-week");
    const month = await ban("seniorModerator", "c-ban-month");
    const forever = await ban("topModerator", "c-ban-forever");
    const day = 24 * 60 * 60 * 1000;
    assert.ok(week > Date.now() + 6 * day && week < Date.now() + 8 * day, `сотка дала ${week}`);
    assert.ok(month > Date.now() + 29 * day, `двухсотка дала ${month}`);
    assert.strictEqual(forever, null, "триста и выше должны банить навсегда");
  });

  await scenario("модератора банит только тот, кто на 100 выше", async () => {
    await db.collection("lessonComments").doc("c-mod").set({
      lessonId: LESSON_ID, authorUid: "moderator", authorNickname: "moderator",
      text: "Спорно", createdAt: Date.now(),
    });
    await call("submitReport", "collector",
      {targetType: "COMMENT", targetId: "c-mod", reason: "нарушение"});
    await callFails("decideReport", "moderator",
      {reportId: "comment_c-mod_collector", decision: "UPHOLD_AND_BAN"}, "PERMISSION_DENIED");
    const result = await call("decideReport", "seniorModerator",
      {reportId: "comment_c-mod_collector", decision: "UPHOLD_AND_BAN"});
    assert.strictEqual(result.bannedUid, "moderator");
    await db.collection("users").doc("moderator").set({
      bannedAtMs: admin.firestore.FieldValue.delete(),
      bannedUntilMs: admin.firestore.FieldValue.delete(),
    }, {merge: true});
  });

  await scenario("старший оценивает решение модератора", async () => {
    const before = await reputationOf("moderator");
    await callFails("reviewReportDecision", "moderator",
      {reportId: "comment_c-spam_player", agrees: true}, "PERMISSION_DENIED");
    await call("reviewReportDecision", "seniorModerator",
      {reportId: "comment_c-spam_player", agrees: true});
    assert.strictEqual(await reputationOf("moderator"), before + 3, "согласие не начислило");

    await call("reviewReportDecision", "seniorModerator",
      {reportId: "comment_c-fine_player", agrees: false});
    assert.strictEqual(await reputationOf("moderator"), before, "несогласие не списало");
  });

  await scenario("модератор снимает комментарий, текст сохраняется как улика", async () => {
    await callFails("removeLessonComment", "player", {commentId: "c-spam"}, "PERMISSION_DENIED");
    await call("removeLessonComment", "moderator", {commentId: "c-spam", reason: "реклама"});
    assert.ok(
      !(await db.collection("lessonComments").doc("c-spam").get()).exists,
      "комментарий не удалён",
    );
    const removals = await db.collection("admin").doc("moderation")
      .collection("comment_removals").get();
    assert.ok(
      removals.docs.some((doc) => (doc.data() || {}).text === "Реклама"),
      "текст удалённого комментария не сохранён",
    );
  });
}

async function reputationOf(uid) {
  const snapshot = await db.collection("profiles").doc(uid).get();
  return Number((snapshot.data() || {}).reviewReputation) || 0;
}

async function main() {
  await seedActors();
  await testSubmission();
  await testRoleRouting();
  await testReviewStages();
  await testPublication();
  await testReportsAndBans();
  await testResults();
  await testGiftBoxes();
  await testVerification();
  await testNicknameAvailability();
  await testNicknameOwnership();
  await testNicknameMarket();
  console.log(`review pipeline e2e: ${passed.length} сценариев прошло`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
  });
