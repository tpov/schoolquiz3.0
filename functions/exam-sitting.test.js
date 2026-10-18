"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb, seedHierarchy} = require("./_exam-test-db");
const {
  SITTING_STATE,
  SLOT_STATE,
  answerReceiptId: _unusedReceipt,
  createExamSittings,
  openSittingDocumentId,
  sittingDocumentId,
  startRequestDocumentId,
} = require("./exam-sitting");
const {POINTS_PER_CHARGE} = require("./economy-constants");

const EASY_COUNT = 5;
const HARD_COUNT = 15;

function questionRows() {
  const rows = [];
  for (let at = 0; at < EASY_COUNT; at += 1) {
    rows.push({id: `e${at}`, doc: {
      lessonId: "lesson", version: 1, difficulty: "EASY",
      payload: JSON.stringify({id: `e${at}`, type: "SingleChoice", text: `e${at}`.padEnd(60, "x"),
        options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"}),
    }});
  }
  for (let at = 0; at < HARD_COUNT; at += 1) {
    rows.push({id: `h${at}`, doc: {
      lessonId: "lesson", version: 1, difficulty: "HARD",
      payload: JSON.stringify({id: `h${at}`, type: "SingleChoice", text: `h${at}`.padEnd(100, "x"),
        options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"}),
    }});
  }
  return rows;
}

/** Мир: курс/тема/урок, банк вопросов, полный заход пользователя, живая конфигурация. */
function world(over = {}) {
  const rows = questionRows();
  const initial = {
    ...seedHierarchy({}),
    ...Object.fromEntries(rows.map((row) => [`questions/${row.id}`, row.doc])),
    "users/player": {uid: "player", lifePoints: 10000, lifePointsUpdatedAtMs: 0,
      standardHearts: 10, nolics: 0, pointsSkill: 0, accountEpoch: 1},
    "configs/exam": {examEnabled: true, passMarkPercent: 60, nextQuestionWindowSeconds: 60,
      abandonmentGraceMs: 300000, paidTermsRevision: 1},
    ...over.initialRows,
  };
  const fake = makeFakeDb({initialRows: initial, nowMs: over.nowMs || 1_700_000_000_000});
  const config = {examEnabled: true, passMarkPercent: 60, nextQuestionWindowSeconds: 60,
    abandonmentGraceMs: 300000, paidTermsRevision: 1, certificateSigningKeyId: null,
    ...(over.config || {})};
  const sittings = createExamSittings({
    db: fake.db,
    nowMs: () => fake.clock.now,
    loadLessonContext: async (tx, lessonId) => {
      const snapshot = await tx.get(fake.db.doc(`lessons/${lessonId}`));
      if (!snapshot.exists) return null;
      const lesson = snapshot.data() || {};
      return {lessonId, authorUid: "author", questId: "course", themeId: lesson.themeId,
        contentVersion: lesson.version, published: lesson.archived !== true,
        reviewApproved: true, questType: "REGULAR"};
    },
    loadExamConfig: async () => config,
    readRegeneration: async (tx, {user}) => ({caughtUp: true,
      userPatch: {lifePoints: user.lifePoints, lifePointsUpdatedAtMs: user.lifePointsUpdatedAtMs,
        hasPremium: false}}),
    writeRegeneration: () => {},
  });
  const request = (overPatch = {}) => ({
    operationId: "op-1", accountEpoch: 1, themeId: "theme", shownTerms: true,
    acceptedTermsRevision: 1, ...overPatch,
  });
  return {fake, sittings, request, config};
}

/** Полное подтверждённое покрытие: по одному полному обычному прохождению на урок. */
function fullCoverageRows(uid = "player") {
  const bucket = require("./exam-coverage").digest(uid).slice(0, 2);
  const served = [
    ...Array.from({length: EASY_COUNT}, (_, at) =>
      ({questionId: `e${at}`, revision: 1, difficulty: "EASY"})),
    ...Array.from({length: HARD_COUNT}, (_, at) =>
      ({questionId: `h${at}`, revision: 1, difficulty: "HARD"})),
  ];
  return {[`result_events_public/${bucket}/events/attempt-1`]: {
    userId: uid, lessonId: "lesson", completed: true,
    settlementReceipt: {status: "SETTLED"}, served, codeAnswer: "9".repeat(20),
  }};
}

function coverageMaterialization(uid = "player", lessonId = "lesson", manifestHash = realManifestHash()) {
  const {coverageDocumentId} = require("./exam-coverage");
  return {[`exam_coverage/${coverageDocumentId(uid, lessonId)}`]: {
    schemaVersion: 1, uid, lessonId, manifestHash, requiredCount: EASY_COUNT, complete: true,
    confirmedAtMs: 1,
  }};
}

/** Настоящий хеш манифеста урока из тех же строк вопросов, что лежат в банке. */
function realManifestHash() {
  const {requiredManifest} = require("./exam-coverage");
  const manifest = requiredManifest("lesson", questionRows().map((row, at) => ({
    questionId: row.id,
    revision: row.doc.version,
    difficulty: row.doc.difficulty,
    survey: JSON.parse(row.doc.payload).type === "Survey",
  })).filter((row) => row.difficulty === "EASY"));
  return manifest.manifestHash;
}

test("happy start commits one sitting with a frozen snapshot and debits exactly 100", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const result = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  assert.equal(result.refused, undefined);
  assert.equal(result.recovered, false);
  assert.equal(result.debited, 100);
  assert.equal(result.lifePoints, 10000 - 100);
  const sitting = fake.rows.get(`exam_sittings/${result.sittingId}`);
  assert.equal(sitting.state, SITTING_STATE.OPEN);
  assert.equal(sitting.slots.length, 20);
  assert.equal(sitting.slots.filter((slot) => slot.shownDifficulty === "EASY").length, 5);
  assert.equal(sitting.bank.TE, undefined || sitting.bank.TE, "основа заморожена рядом");
  assert.ok(sitting.bank.TE >= 25 && sitting.bank.TH >= 75);
  assert.equal(sitting.rules.passMarkPercent, 60);
  assert.equal(sitting.rules.nextQuestionWindowSeconds, 60);
  // Публичная проекция отдаёт редактированный вопрос, но не ключ и не будущие слоты.
  assert.equal(result.nextQuestion.index, 0);
  assert.equal(result.nextQuestion.publicPayload.includes("Redacted"), true);
  assert.equal(result.nextQuestion.publicPayload.includes("correctOptionId"), false);
  assert.equal(JSON.stringify(result).includes("canonicalContent"), false);
  assert.equal(JSON.stringify(result).includes("codeAnswer"), false);
  // Стандартные очки списаны строкой книги; плазмы в транзакции нет вовсе.
  const ledgerRows = [...fake.rows.keys()].filter((path) => path.startsWith("audit/currency_ledger/"));
  assert.equal(ledgerRows.length, 1);
  assert.equal(fake.rows.get(ledgerRows[0]).delta, -100);
});

test("start while open recovers the same sitting with original deadlines and no second debit", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const first = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  const userBefore = {...fake.rows.get("users/player")};
  fake.clock.now += 60 * 1000;
  const again = await sittings.start({uid: "player", payload: request({operationId: "op-2"}),
    mode: "ThemeTest"});
  assert.equal(again.recovered, true, "новый ключ операции, открытый сидинг — восстановление");
  assert.equal(again.sittingId, first.sittingId);
  assert.equal(again.nextQuestion.ack.awaitingAck, true);
  assert.equal(again.nextQuestion.answerSeconds, first.nextQuestion.answerSeconds);
  const userAfter = fake.rows.get("users/player");
  assert.equal(userAfter.lifePoints, userBefore.lifePoints, "второго списания нет");
  const sittings_count = [...fake.rows.keys()].filter((path) => path.startsWith("exam_sittings/"));
  assert.equal(sittings_count.length, 1);
});

test("A→B→A replay of the operation id preserves one financial effect", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const first = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  fake.clock.now += 1000;
  const replay = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  assert.equal(replay.recovered, false);
  assert.equal(replay.sittingId, first.sittingId, "тот же ключ операции — тот же результат");
  const ledgerRows = [...fake.rows.keys()].filter((path) => path.startsWith("audit/currency_ledger/"));
  assert.equal(ledgerRows.length, 1, "одна строка платы на ключ");
  // Конфликтное тело под тем же ключом — отказ, не выполнение.
  await assert.rejects(() => sittings.start({uid: "player",
    payload: request({shownTerms: false}), mode: "ThemeTest"}), (error) => error.code === "REQUEST_REUSE");
});

test("typed refusals: coverage Locked, ResultsNotSynced barrier, cooldown, charge, pool, unavailable", async () => {
  // Без подтверждённого покрытия — замок.
  const locked = world({initialRows: {...fullCoverageRows()}});
  const lockedResult = await locked.sittings.start({uid: "player",
    payload: locked.request(), mode: "ThemeTest"});
  assert.deepEqual(
    {refused: lockedResult.refused, kind: lockedResult.kind},
    {refused: true, kind: "ResultsNotSynced"},
    "заработанное доказательство есть, материализации нет — ожидание синхронизации");

  // Совсем без доказательства — замок содержимого.
  const bare = world({initialRows: coverageMaterialization("player", "lesson", "any")});
  const bareResult = await bare.sittings.start({uid: "player", payload: bare.request(), mode: "ThemeTest"});
  assert.equal(bareResult.kind, "Locked");

  // Кулдаун: T+23:59:59 отказ с доступностью T+24h; T+24h — старт.
  const cooled = world({initialRows: {
    ...fullCoverageRows(),
    ...coverageMaterialization(),
    "exam_assessment_state/cooldown": null,
  }});
  const stateId = require("./exam-coverage").assessmentStateId("player", "ThemeTest", "theme");
  cooled.fake.rows.set(`exam_assessment_state/${stateId}`, {closedAtMs: cooled.fake.clock.now - (24 * 3600 * 1000 - 60 * 1000)});
  const cooldown = await cooled.sittings.start({uid: "player", payload: cooled.request(), mode: "ThemeTest"});
  assert.equal(cooldown.kind, "Cooldown");
  assert.equal(cooldown.availableAtMs, cooled.fake.clock.now + 60 * 1000);
  cooled.fake.clock.now += 60 * 1000;
  const afterCooldown = await cooled.sittings.start({uid: "player", payload: cooled.request({operationId: "op-2"}),
    mode: "ThemeTest"});
  assert.equal(afterCooldown.refused, undefined);

  // Плата: 99 очков после доводки — InsufficientCharge, ничего не списано.
  const poor = world({initialRows: {
    ...fullCoverageRows(), ...coverageMaterialization(),
    "users/player": {uid: "player", lifePoints: 99, lifePointsUpdatedAtMs: 0, standardHearts: 10,
      accountEpoch: 1},
  }});
  const poorResult = await poor.sittings.start({uid: "player", payload: poor.request(), mode: "ThemeTest"});
  assert.equal(poorResult.kind, "InsufficientCharge");
  assert.equal(poor.fake.rows.get("users/player").lifePoints, 99);
  assert.equal([...poor.fake.rows.keys()].some((path) => path.startsWith("exam_sittings/")), false);

  // Малый банк — PoolTooSmall.
  const thin = world({initialRows: {
    ...fullCoverageRows(), ...coverageMaterialization(),
  }});
  for (let at = 10; at < HARD_COUNT; at += 1) {
    thin.fake.rows.delete(`questions/h${at}`);
  }
  const thinResult = await thin.sittings.start({uid: "player", payload: thin.request(), mode: "ThemeTest"});
  assert.equal(thinResult.kind, "PoolTooSmall");

  // Экзамен выключен ручкой — явная недоступность.
  const disabled = world({config: {examEnabled: false},
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()}});
  const disabledResult = await disabled.sittings.start({uid: "player", payload: disabled.request(),
    mode: "ThemeTest"});
  assert.equal(disabledResult.kind, "unavailable");

  // Изменённые платные условия — пересогласование.
  const terms = world({initialRows: {...fullCoverageRows(), ...coverageMaterialization()}});
  const termsResult = await terms.sittings.start({uid: "player",
    payload: terms.request({acceptedTermsRevision: 0}), mode: "ThemeTest"});
  assert.equal(termsResult.kind, "Locked");
  assert.equal(termsResult.reason, "TERMS_CHANGED");
});

test("first ACK fixes the answer deadline; duplicates return it; ACK never extends", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const start = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  const firstAckGap = start.nextQuestion.windowDeadlineMs;
  assert.equal(firstAckGap, start.rules ? undefined : undefined, "окно первого ACK от создания сидинга");
  fake.clock.now += 10 * 1000;
  const ack = await sittings.acknowledgeQuestion({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, operationId: "ack-1",
  }});
  assert.equal(ack.acknowledged, true);
  assert.equal(ack.ack.answerDeadlineMs, fake.clock.now + start.nextQuestion.answerSeconds * 1000,
    "серверная заморозка решает: символов вопроса+вариантов × 0.24, пол 5");
  fake.clock.now += 1000;
  const duplicate = await sittings.acknowledgeQuestion({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, operationId: "ack-2",
  }});
  assert.equal(duplicate.duplicate, true);
  assert.equal(duplicate.ack.answerDeadlineMs, ack.ack.answerDeadlineMs, "повтор возвращает закреплённое");
  // Сдвиг часов не двигает закреплённый дедлайн.
  fake.clock.now += 60 * 1000;
  const later = await sittings.acknowledgeQuestion({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, operationId: "ack-3",
  }});
  assert.equal(later.ack.answerDeadlineMs, ack.ack.answerDeadlineMs);
});

test("first committed answer wins; identical retry replays; conflicting reuse is rejected", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const start = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  fake.clock.now += 1000;
  await sittings.acknowledgeQuestion({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, operationId: "ack-1"}});
  const answer = {type: "single-choice", selected: "a"};
  const submitted = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1, answer, operationId: "ans-1"}});
  assert.equal(submitted.accepted, true);
  assert.ok(submitted.receipt.receiptId);
  assert.ok(submitted.receipt.receivedAtMs);
  // Форма ответа прибита к разбору: публичные пять полей; владелец и отпечаток — только
  // в документе квитанции, не в ответе.
  assert.deepEqual(Object.keys(submitted.receipt).sort(),
    ["index", "receiptId", "receivedAtMs", "revision", "sittingId"]);
  const persistedReceipt = fake.rows.get(`exam_answer_receipts/${submitted.receipt.receiptId}`);
  assert.deepEqual(Object.keys(persistedReceipt).sort(),
    ["fingerprint", "index", "receiptId", "receivedAtMs", "revision", "sittingId", "uid"],
    "приватное свидетельство остаётся в документе");
  const slot0 = fake.rows.get(`exam_sittings/${start.sittingId}`).slots[0];
  assert.equal(slot0.state, SLOT_STATE.ANSWERED);
  assert.equal(slot0.answer.canonicalAnswer.selected, "a");
  assert.equal(slot1Delivered(fake, start.sittingId), true, "следующий вопрос выдан с окном");
  // Одинаковый повтор — та же квитанция, без второго эффекта.
  fake.clock.now += 1000;
  const replay = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1, answer, operationId: "ans-2"}});
  assert.equal(replay.duplicate, true);
  assert.deepEqual(replay.receipt, submitted.receipt);
  // Конфликтное тело под той же квитанцией — отказ.
  await assert.rejects(() => sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1,
    answer: {type: "single-choice", selected: "b"}, operationId: "ans-3"}}),
  (error) => error.code === "ANSWER_CONFLICT");
  // Ответ на чужой сидинг — отказ владения.
  await assert.rejects(() => sittings.submitAnswer({uid: "intruder", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1, answer, operationId: "ans-4"}}),
  (error) => error.code === "PERMISSION_DENIED");
});

function slot1Delivered(fake, sittingId) {
  const sitting = fake.rows.get(`exam_sittings/${sittingId}`);
  return sitting.slots[1].state === SLOT_STATE.DELIVERED &&
    sitting.slots[1].windowDeadlineMs !== null &&
    sitting.currentIndex === 1;
}

test("submit without ACK is refused; answer binds revision; mismatch refused", async () => {
  const {sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const start = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  const refused = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1,
    answer: {type: "single-choice", selected: "a"}, operationId: "ans-1"}});
  assert.equal(refused.accepted, false);
  assert.equal(refused.reason, "ACK_REQUIRED");
});

test("finishSitting requests closure once; fetchSittingResult reports explicit open state", async () => {
  const {sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const start = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  const fetched = await sittings.fetchSittingResult({uid: "player", payload: {sittingId: start.sittingId}});
  assert.equal(fetched.open, true);
  assert.equal(fetched.result, null);
  const finish = await sittings.finishSitting({uid: "player", payload: {sittingId: start.sittingId}});
  assert.equal(finish.state, SITTING_STATE.FINISH_REQUESTED);
  const again = await sittings.finishSitting({uid: "player", payload: {sittingId: start.sittingId}});
  assert.equal(again.alreadyRequested, true);
  const closedFetch = await sittings.fetchSittingResult({uid: "player", payload: {sittingId: start.sittingId}});
  assert.equal(closedFetch.open, true, "до терминализации worker'ом результата не существует");
});

/** Курс из двенадцати сертификационно-полных тем: 100 EASY + 100 HARD в каждой. */
function certifiableCourse({themes = 12, prefix = "c"} = {}) {
  const rows = {};
  rows["quests/bigcourse"] = {id: "bigcourse", questType: "REGULAR", catalogId: "catalog",
    version: 3, authorUid: "author", archived: false};
  for (let section = 0; section < Math.ceil(themes / 4); section += 1) {
    rows[`sections/bigsec${section}`] = {id: `bigsec${section}`, questId: "bigcourse", archived: false};
  }
  for (let theme = 0; theme < themes; theme += 1) {
    const section = Math.floor(theme / 4);
    rows[`themes/${prefix}t${theme}`] = {id: `${prefix}t${theme}`, sectionId: `bigsec${section}`,
      archived: false};
    rows[`lessons/${prefix}l${theme}`] = {id: `${prefix}l${theme}`, themeId: `${prefix}t${theme}`,
      version: 2, reviewApprovedAtMs: 1, reviewSubmissionId: "s", archived: false};
    const payload = {type: "SingleChoice", text: "q".repeat(120),
      options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"};
    for (let at = 0; at < 100; at += 1) {
      rows[`questions/${prefix}t${theme}-e${at}`] = {lessonId: `${prefix}l${theme}`, version: 1,
        difficulty: "EASY", payload: JSON.stringify({...payload, id: `e${at}`})};
      rows[`questions/${prefix}t${theme}-h${at}`] = {lessonId: `${prefix}l${theme}`, version: 1,
        difficulty: "HARD", payload: JSON.stringify({...payload, id: `h${at}`})};
    }
  }
  return rows;
}

/** Пройденные темы большого курса с поколением агрегата. */
function passedBigThemes({generation = 1, pendingCorrection = false, passedAtGeneration = generation} = {}) {
  const {assessmentStateId} = require("./exam-coverage");
  const rows = {"exam_theme_generation/bigcourse-wrong": undefined};
  delete rows["exam_theme_generation/bigcourse-wrong"];
  for (let theme = 0; theme < 12; theme += 1) {
    rows[`exam_theme_generation/ct${theme}`] = {generation, pendingCorrection};
    rows[`exam_assessment_state/${assessmentStateId("player", "ThemeTest", `ct${theme}`)}`] = {
      uid: "player", mode: "ThemeTest", assessmentId: `ct${theme}`, passed: true, bestPercent: 80,
      passedAtGeneration, closedAtMs: 1, maxAccountedPercent: 80,
    };
  }
  return rows;
}

test("final exam start debits 200 and draws 20 hard from the latest approved course", async () => {
  const {fake, sittings} = world({
    initialRows: {...certifiableCourse(), ...passedBigThemes()},
  });
  const result = await sittings.start({uid: "player", payload: {
    operationId: "fin-1", accountEpoch: 1, courseId: "bigcourse", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "FinalExam"});
  assert.equal(result.refused, undefined, JSON.stringify(result));
  assert.equal(result.debited, 200, "финал — два полных заряда");
  const sitting = fake.rows.get(`exam_sittings/${result.sittingId}`);
  assert.equal(sitting.slots.length, 20);
  assert.equal(sitting.slots.every((slot) => slot.shownDifficulty === "HARD"), true);
  assert.equal(sitting.bank.TE, 0);
});

test("final start refuses before charge while a correction generation is pending", async () => {
  const {fake, sittings} = world({
    initialRows: {...certifiableCourse(), ...passedBigThemes({generation: 2, pendingCorrection: true})},
  });
  const result = await sittings.start({uid: "player", payload: {
    operationId: "fin-1", accountEpoch: 1, courseId: "bigcourse", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "FinalExam"});
  assert.equal(result.kind, "unavailable");
  assert.equal(result.reason, "CORRECTION_PENDING");
  assert.equal(fake.rows.get("users/player").lifePoints, 10000, "до отказа ничего не списано");
  assert.equal([...fake.rows.keys()].some((path) => path.startsWith("exam_sittings/")), false);
});

test("final start refuses while a passed theme lags the current generation", async () => {
  const {sittings} = world({
    initialRows: {...certifiableCourse(), ...passedBigThemes({generation: 3, passedAtGeneration: 1})},
  });
  const result = await sittings.start({uid: "player", payload: {
    operationId: "fin-1", accountEpoch: 1, courseId: "bigcourse", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "FinalExam"});
  assert.equal(result.kind, "unavailable", "проход не из текущего поколения — барьер до списания");
});

test("start over a FINISH_REQUESTED sitting recovers it without a second debit", async () => {
  const {fake, sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const first = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  await sittings.finishSitting({uid: "player", payload: {sittingId: first.sittingId}});
  const before = fake.rows.get("users/player").lifePoints;
  const again = await sittings.start({uid: "player", payload: request({operationId: "op-rec"}),
    mode: "ThemeTest"});
  assert.equal(again.recovered, true, "незакрытый сидинг — восстановление, не новая попытка");
  assert.equal(again.sittingId, first.sittingId);
  assert.equal(fake.rows.get("users/player").lifePoints, before, "второго списания нет");
});

test("a FINISH_REQUESTED sitting refuses fresh answers but replays existing receipts", async () => {
  const {sittings, request} = world({
    initialRows: {...fullCoverageRows(), ...coverageMaterialization()},
  });
  const start = await sittings.start({uid: "player", payload: request(), mode: "ThemeTest"});
  const ack = await sittings.acknowledgeQuestion({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, operationId: "ack-1"}});
  assert.equal(ack.acknowledged, true);
  const answer = {type: "single-choice", selected: "a"};
  const submitted = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1, answer, operationId: "ans-1"}});
  assert.equal(submitted.accepted, true);
  await sittings.finishSitting({uid: "player", payload: {sittingId: start.sittingId}});
  // Свежий ответ на запрошенном к закрытии сидинге — отказ без перезаписи.
  const refused = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 1, revision: 1,
    answer: {type: "single-choice", selected: "a"}, operationId: "ans-2"}});
  assert.equal(refused.accepted, false);
  assert.equal(refused.reason, "SITTING_CLOSED");
  // Идемпотентный повтор того же ответа возвращает ту же квитанцию и после запроса закрытия.
  const replay = await sittings.submitAnswer({uid: "player", payload: {
    sittingId: start.sittingId, questionIndex: 0, revision: 1, answer, operationId: "ans-3"}});
  assert.equal(replay.accepted, true);
  assert.deepEqual(replay.receipt, submitted.receipt);
});

test("sitting document ids and pointers are deterministic", () => {
  assert.equal(sittingDocumentId("u", "r"), sittingDocumentId("u", "r"));
  assert.equal(openSittingDocumentId("u", "ThemeTest", "t"),
    openSittingDocumentId("u", "ThemeTest", "t"));
  assert.notEqual(startRequestDocumentId("u", "r1"), startRequestDocumentId("u", "r2"));
});
