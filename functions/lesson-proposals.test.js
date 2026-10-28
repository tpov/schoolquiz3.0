"use strict";

/**
 * Предложения автору (история 4.21, AD-33/CAP-27): запись трёх видов, приёмка с платой одной
 * транзакцией, цитата, решение автора. Сюита гоняет чистый модуль и настоящие callable против
 * стаба Firestore: владелец цели выводится сервером, чужое предложение неотличимо от
 * отсутствующего, повтор ключа не платит второй раз, STALE отказывает поимённо и без записей,
 * плата несостоятельному кошельку не достаётся вовсе.
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
const {
  PROPOSALS_COLLECTION,
  PROPOSAL_STATUS,
  buildProposalCheckSource,
  buildProposalOutcome,
  buildProposalRecord,
  normalizeProposalTariffs,
  parseProposalSubmissionBody,
  proposalCheckStatus,
  proposalFeeNolics,
  proposalStaleness,
} = require("./lesson-proposals");

const PROPOSER = "proposer-uid-1";
const AUTHOR = "author-uid-1";
const STRANGER = "stranger-uid-1";
const LESSON = "lesson_1";
const QUEST = "quest_1";
const MUTATION = "quest_authoring.SUBMIT_PROPOSAL";

function question(id, lessonId, text) {
  return {
    id, lessonId, type: "SingleChoice", language: "ru", languageLevel: 0,
    difficulty: "EASY", order: 0, text, imagePath: null,
    payload: JSON.stringify({type: "SingleChoice", id, text, options: [{id: "a", text: "один"}]}),
  };
}

function reportBody(overrides = {}) {
  return {
    proposalId: "prop-report-1",
    kind: "REPORT",
    target: {lessonId: LESSON},
    text: "В уроке ошибка в формулировке второго вопроса",
    ...overrides,
  };
}

function copyEditBody(overrides = {}) {
  return {
    proposalId: "prop-copy-1",
    kind: "COPY_EDIT",
    target: {lessonId: LESSON},
    lessonTitle: "Урок 1",
    questions: [question("q1", LESSON, "Исправленный вопрос")],
    editedQuestionIds: ["q1"],
    ...overrides,
  };
}

function newLessonBody(overrides = {}) {
  return {
    proposalId: "prop-new-1",
    kind: "NEW_LESSON",
    target: {catalogId: "cat-1", questId: QUEST, sectionId: "sec-1", themeId: "theme-1"},
    lesson: {id: "new_lesson_1", title: "Чужой новый урок"},
    questions: [question("nq1", "new_lesson_1", "Новый вопрос")],
    ...overrides,
  };
}

function proposalWorld(extra = {}) {
  return {
    "profiles/proposer-uid-1": {uid: PROPOSER},
    "profiles/author-uid-1": {uid: AUTHOR},
    "profiles/stranger-uid-1": {uid: STRANGER},
    [`users/${PROPOSER}`]: {pointsNolics: 10000, nolics: 10000},
    [`lessons/${LESSON}`]: {id: LESSON, title: "Урок 1", authorUid: AUTHOR, version: 3},
    [`quests/${QUEST}`]: {id: QUEST, catalogId: "cat-1", authorUid: AUTHOR, version: 7},
    "sections/sec-1": {questId: QUEST},
    "themes/theme-1": {sectionId: "sec-1"},
    ...extra,
  };
}

async function submit(uid, body) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {mutationId: `mut-${body.proposalId}`, operation: MUTATION, payload: JSON.stringify(body)},
  });
}

async function decide(uid, data) {
  return functions.decideLessonProposal.run({auth: {uid}, data});
}

// ─── чистый модуль ─────────────────────────────────────────────────────────────────────────────

test("тарифы предложения: дефолт владельца при пустом документе, свои значения, громкий отказ", () => {
  assert.deepEqual(normalizeProposalTariffs(null),
    {reportNolics: 0, copyEditPerQuestionNolics: 100, newLessonNolics: 1000, revision: 1});
  assert.deepEqual(normalizeProposalTariffs({reportNolics: 5, copyEditPerQuestionNolics: 7, newLessonNolics: 9, revision: 2}),
    {reportNolics: 5, copyEditPerQuestionNolics: 7, newLessonNolics: 9, revision: 2});
  assert.throws(() => normalizeProposalTariffs({copyEditPerQuestionNolics: "много", revision: 1}));
  assert.throws(() => normalizeProposalTariffs({revision: 0}));
});

test("плата по виду: отчёт бесплатен, правка — тариф за изменённый вопрос, урок — плоский тариф", () => {
  const tariffs = normalizeProposalTariffs(null);
  assert.equal(proposalFeeNolics({kind: "REPORT"}, tariffs), 0);
  assert.equal(proposalFeeNolics({kind: "NEW_LESSON"}, tariffs), 1000);
  assert.equal(proposalFeeNolics({kind: "COPY_EDIT", changedQuestions: 3}, tariffs), 300);
  assert.throws(() => proposalFeeNolics({kind: "COPY_EDIT", changedQuestions: 0}, tariffs));
  assert.throws(() => proposalFeeNolics({kind: "COPY_EDIT", changedQuestions: 1.5}, tariffs));
  assert.throws(() => proposalFeeNolics({kind: "MYSTERY"}, tariffs));
});

test("тело подачи строгое: чужие виды, битые id, пустые тексты и непокрытые отметки не доезжают", () => {
  assert.equal(parseProposalSubmissionBody(reportBody()).kind, "REPORT");
  assert.equal(parseProposalSubmissionBody(reportBody({ownVariant: "Правильный ответ — b"})).payload.ownVariant,
    "Правильный ответ — b");
  assert.deepEqual(parseProposalSubmissionBody(copyEditBody()).payload.editedQuestionIds, ["q1"]);
  assert.equal(parseProposalSubmissionBody(newLessonBody()).payload.lesson.id, "new_lesson_1");

  assert.throws(() => parseProposalSubmissionBody(reportBody({proposalId: "плохой/id"})));
  assert.throws(() => parseProposalSubmissionBody({...reportBody(), kind: "MYSTERY"}));
  assert.throws(() => parseProposalSubmissionBody(reportBody({text: "   "})));
  assert.throws(() => parseProposalSubmissionBody(reportBody({text: "x".repeat(2001)})));
  assert.throws(() => parseProposalSubmissionBody(copyEditBody({editedQuestionIds: ["чужой"]})));
  assert.throws(() => parseProposalSubmissionBody(copyEditBody({editedQuestionIds: []})));
  assert.throws(() => parseProposalSubmissionBody(newLessonBody({questions: []})));
  const foreignLesson = newLessonBody();
  foreignLesson.questions[0].lessonId = LESSON;
  assert.throws(() => parseProposalSubmissionBody(foreignLesson), /does not belong/);
  const badPayload = copyEditBody();
  badPayload.questions[0].payload = "{не json";
  assert.throws(() => parseProposalSubmissionBody(badPayload), /payload/);
  const badLanguage = copyEditBody();
  badLanguage.questions[0].language = "de";
  assert.throws(() => parseProposalSubmissionBody(badLanguage), /known language/);
});

test("STALE — единое правило: расхождение ревизий устарело, совпадение свежо", () => {
  const record = buildProposalRecord({
    proposalId: "p1", kind: "REPORT", proposerUid: PROPOSER, targetOwnerUid: AUTHOR,
    target: {lessonId: LESSON}, baseRevision: 3, payload: {text: "x"},
  });
  assert.equal(proposalStaleness(record, 3), false);
  assert.equal(proposalStaleness(record, 4), true);
  assert.throws(() => proposalStaleness(record, undefined));
});

test("исход проверки предложения: PASS без FAIL, FAIL валит вид, задержка важнее отказа", () => {
  const pipeline = {
    submissionId: "prop-1", status: "QUEST_REVIEWED", updatedAtMs: 5,
    lessons: {l1: {stages: {WORDS: {state: "DONE"}, FACTS: {state: "DONE"}}}},
  };
  const verdicts = [{submissionId: "prop-1", lessonId: "l1", stage: "WORDS", attempt: 1,
    questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "чисто"}]}];
  const outcome = buildProposalOutcome({pipeline, verdicts});
  assert.deepEqual(outcome.failedQuestions, []);
  assert.deepEqual(outcome.passingLessonIds, ["l1"]);
  assert.equal(proposalCheckStatus(outcome), PROPOSAL_STATUS.CHECK_PASSED);

  const failing = buildProposalOutcome({pipeline, verdicts: [{
    submissionId: "prop-1", lessonId: "l1", stage: "FACTS", attempt: 2,
    questionVerdicts: [{questionId: "q1", verdict: "FAIL", reason: "неверный ключ"}],
  }]});
  assert.equal(failing.failedQuestions.length, 1);
  assert.equal(proposalCheckStatus(failing), PROPOSAL_STATUS.CHECK_FAILED);

  const delayedPipeline = {
    ...pipeline, status: "REVIEW_DELAYED",
    lessons: {l1: {stages: {WORDS: {state: "DONE"}, FACTS: {state: "DELAYED"}}}},
  };
  const delayed = buildProposalOutcome({pipeline: delayedPipeline, verdicts});
  assert.deepEqual(delayed.delayedStages, [{lessonId: "l1", stage: "FACTS"}]);
  assert.equal(proposalCheckStatus(delayed), PROPOSAL_STATUS.CHECK_DELAYED);

  const running = buildProposalOutcome({pipeline: {...pipeline, status: "RUNNING"}, verdicts});
  assert.equal(running, null);
});

test("тело конвейера: отчёт едет одной единицей с вариантом предложившего, правка — вопросами", () => {
  const report = buildProposalCheckSource({
    kind: "REPORT", target: {lessonId: LESSON},
    payload: {text: "текст отчёта", ownVariant: "свой вариант"},
  });
  assert.equal(report.lessons.length, 1);
  assert.equal(report.questions.length, 1);
  assert.equal(report.questions[0].id, "report");
  assert.ok(report.questions[0].text.includes("текст отчёта"));
  assert.ok(report.questions[0].text.includes("свой вариант"));

  const copy = buildProposalCheckSource({
    kind: "COPY_EDIT", target: {lessonId: LESSON}, payload: {lessonTitle: "Урок", questions: [question("q1", LESSON, "x")]},
  });
  assert.deepEqual(copy.lessons, [{id: LESSON, title: "Урок"}]);
  assert.equal(copy.questions.length, 1);

  const fresh = buildProposalCheckSource({
    kind: "NEW_LESSON", target: {questId: QUEST}, payload: {lesson: {id: "nl", title: "Новый"}, questions: []},
  });
  assert.equal(fresh.lessons[0].id, "nl");
  assert.throws(() => buildProposalCheckSource({kind: "REPORT", target: {}, payload: {text: " "}}));
});

// ─── приёмка: плата и запись одной транзакцией ─────────────────────────────────────────────────

test("отчёт принимается бесплатным: владелец и baseRevision — из документа цели, не из тела", async () => {
  store.reset(proposalWorld({
    "profiles/proposer-uid-1": {uid: PROPOSER},
    [`lessons/${LESSON}`]: {id: LESSON, authorUid: AUTHOR, version: 3},
  }));
  const result = await submit(PROPOSER, reportBody());
  assert.equal(result.replayed, false);
  assert.equal(result.result.feeNolics, 0);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-report-1`);
  assert.equal(record.kind, "REPORT");
  assert.equal(record.proposerUid, PROPOSER);
  assert.equal(record.targetOwnerUid, AUTHOR, "адресат — автор документа цели");
  assert.equal(record.target.lessonId, LESSON);
  assert.equal(record.baseRevision, 3, "база — ревизия урока на сервере");
  assert.equal(record.status, PROPOSAL_STATUS.QUEUED);
  assert.equal(record.fee.nolics, 0);
  // Бесплатная подача не пишет ни кошелёк, ни книгу.
  assert.equal(store.documents.get(`users/${PROPOSER}`).pointsNolics, 10000);
  assert.ok(![...store.documents.keys()].some((path) => path.startsWith("ledger/")));
});

test("повтор ключа возвращает сохранённую запись и не создаёт вторую", async () => {
  store.reset(proposalWorld());
  await submit(PROPOSER, reportBody());
  const replay = await submit(PROPOSER, reportBody());
  assert.equal(replay.replayed, true);
  assert.equal(replay.result.status, PROPOSAL_STATUS.QUEUED);
  const records = [...store.documents.keys()].filter((path) => path.startsWith(`${PROPOSALS_COLLECTION}/`));
  assert.equal(records.length, 1);
});

test("правка копии платит за изменённые вопросы: зеркало кошелька и строка книги", async () => {
  store.reset(proposalWorld());
  const result = await submit(PROPOSER, copyEditBody({questions: [
    question("q1", LESSON, "первый"), question("q2", LESSON, "второй"), question("q3", LESSON, "третий"),
  ], editedQuestionIds: ["q1", "q2", "q3"]}));
  assert.equal(result.result.feeNolics, 300);
  const wallet = store.documents.get(`users/${PROPOSER}`);
  assert.equal(wallet.pointsNolics, 9700, "обе половины кошелька — зеркало списания");
  assert.equal(wallet.nolics, 9700);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-copy-1`);
  assert.equal(record.fee.nolics, 300);
  assert.equal(record.fee.changedQuestions, 3);
  const ledgerRows = [...store.documents.values()].filter((doc) => doc && doc.reason === "proposal_fee");
  assert.equal(ledgerRows.length, 1);
  assert.equal(ledgerRows[0].delta, -300);
});

test("несостоятельному кошельку плата не достаётся: именованный отказ и ноль записей", async () => {
  store.reset(proposalWorld({[`users/${PROPOSER}`]: {pointsNolics: 50, nolics: 50}}));
  await assert.rejects(submit(PROPOSER, newLessonBody()), /PROPOSAL_FEE_INSUFFICIENT/);
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith(`${PROPOSALS_COLLECTION}/`)).length, 0);
  assert.equal(store.documents.get(`users/${PROPOSER}`).pointsNolics, 50);
});

test("новый урок: путь сверяется с деревом, владелец курса — из документа квеста", async () => {
  store.reset(proposalWorld());
  const result = await submit(PROPOSER, newLessonBody());
  assert.equal(result.result.feeNolics, 1000);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-new-1`);
  assert.equal(record.targetOwnerUid, AUTHOR);
  assert.deepEqual(record.target, {catalogId: "cat-1", questId: QUEST, sectionId: "sec-1", themeId: "theme-1"});
  assert.equal(record.baseRevision, 7, "база нового урока — ревизия квеста");

  const brokenPath = proposalWorld();
  brokenPath["themes/theme-1"] = {sectionId: "sec-other"};
  store.reset(brokenPath);
  await assert.rejects(submit(PROPOSER, newLessonBody()), /course path could not be found/);
});

test("предложение своему материалу не существует", async () => {
  store.reset(proposalWorld());
  await assert.rejects(submit(AUTHOR, reportBody()), /PROPOSAL_TARGET_OWN/);
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith(`${PROPOSALS_COLLECTION}/`)).length, 0);
});

test("отсутствующая или неопубликованная цель — не найдена, без записей", async () => {
  store.reset(proposalWorld({[`lessons/${LESSON}`]: {id: LESSON}}));
  await assert.rejects(submit(PROPOSER, reportBody()), /target lesson could not be found/);
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith(`${PROPOSALS_COLLECTION}/`)).length, 0);
});

// ─── цитата ────────────────────────────────────────────────────────────────────────────────────

test("цитата называет плату по виду до отправки", async () => {
  store.reset(proposalWorld());
  const report = await functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "REPORT"},
  });
  assert.equal(report.feeNolics, 0);
  const copy = await functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "COPY_EDIT", changedQuestions: 4},
  });
  assert.equal(copy.feeNolics, 400);
  assert.equal(copy.tariffRevision, 1);
  const fresh = await functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "NEW_LESSON"},
  });
  assert.equal(fresh.feeNolics, 1000);
  await assert.rejects(functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "COPY_EDIT", changedQuestions: 0},
  }), /at least one changed question/);
  await assert.rejects(functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "MYSTERY"},
  }), /proposal kind is required/);
});

test("испорченный тариф отказывает громко всем платным чтениям и подачам", async () => {
  store.reset(proposalWorld({"configs/proposal_fee": {copyEditPerQuestionNolics: -5, revision: 1}}));
  await assert.rejects(functions.quoteLessonProposalFee.run({
    auth: {uid: PROPOSER}, data: {kind: "REPORT"},
  }), /proposal_fee/);
  await assert.rejects(submit(PROPOSER, newLessonBody()), /proposal_fee/);
});

// ─── решение автора ────────────────────────────────────────────────────────────────────────────

function checkedProposal(extra = {}) {
  return {
    schemaVersion: 1,
    proposalId: "prop-decide-1",
    kind: "REPORT",
    proposerUid: PROPOSER,
    targetOwnerUid: AUTHOR,
    target: {lessonId: LESSON},
    baseRevision: 3,
    payload: {text: "текст отчёта"},
    fee: {nolics: 0, changedQuestions: 0, tariffRevision: 1, chargedAtMs: 0},
    status: PROPOSAL_STATUS.CHECK_PASSED,
    reviewOutcome: {pipelineStatus: "QUEST_REVIEWED", failedQuestions: [], passingLessonIds: [LESSON],
      delayedStages: [], reviewedAtMs: 1},
    checkedAtMs: 1,
    decidedAtMs: 0,
    createdAtMs: 1,
    updatedAtMs: 1,
    ...extra,
  };
}

async function seedChecked(extra = {}) {
  store.reset(proposalWorld({[`lesson_proposals/prop-decide-1`]: checkedProposal(extra)}));
}

test("отклонение с комментарием пишет решение и ничего больше", async () => {
  await seedChecked();
  const result = await decide(AUTHOR, {proposalId: "prop-decide-1", decision: "REJECT", comment: "не согласен"});
  assert.equal(result.status, PROPOSAL_STATUS.REJECTED);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-decide-1`);
  assert.equal(record.status, PROPOSAL_STATUS.REJECTED);
  assert.equal(record.decision.comment, "не согласен");
  assert.equal(record.decision.decidedByUid, AUTHOR);
  assert.ok(!store.documents.get(`lessons/${LESSON}`).correctionRequestedAtMs);
});

test("чужое предложение неотличимо от отсутствующего", async () => {
  await seedChecked();
  for (const uid of [PROPOSER, STRANGER]) {
    await assert.rejects(decide(uid, {proposalId: "prop-decide-1", decision: "REJECT"}),
      (error) => error.code === "not-found");
  }
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-missing", decision: "REJECT"}),
    (error) => error.code === "not-found");
});

test("решение применяется только к проверенному", async () => {
  await seedChecked({status: PROPOSAL_STATUS.QUEUED});
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "APPROVE"}),
    /PROPOSAL_NOT_ACTIONABLE/);
  await seedChecked({status: PROPOSAL_STATUS.CHECK_FAILED});
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "REJECT"}),
    /PROPOSAL_NOT_ACTIONABLE/);
});

test("STALE: одобрение по устаревшей базе отказывает поимённо и не пишет ничего", async () => {
  await seedChecked({baseRevision: 2});
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "APPROVE"}),
    /PROPOSAL_STALE/);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-decide-1`);
  assert.equal(record.status, PROPOSAL_STATUS.CHECK_PASSED, "запись не переведена в APPROVED");
  assert.ok(!record.decision, "решения при отказе не существует");
});

test("одобренный отчёт помечает урок к исправлению и завершает запись", async () => {
  await seedChecked();
  const result = await decide(AUTHOR, {proposalId: "prop-decide-1", decision: "APPROVE", comment: "спасибо"});
  assert.equal(result.status, PROPOSAL_STATUS.APPROVED);
  const lesson = store.documents.get(`lessons/${LESSON}`);
  assert.ok(lesson.correctionRequestedAtMs, "урок помечен к исправлению");
  assert.equal(lesson.title, "Урок 1", "содержимое урока решением не меняется");
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-decide-1`);
  assert.equal(record.status, PROPOSAL_STATUS.APPROVED);
  assert.equal(record.decision.comment, "спасибо");
});

test("одобренная правка копии уезжает в применение, а не в промежуточный отказ (4.23)", async () => {
  // Мир сюиты не содержит документов урока: применение честно отвечает not-found, но путь —
  // примененческий (apply), а не промежуточный отказ с его формулировкой.
  await seedChecked({kind: "COPY_EDIT"});
  // Мир сюиты содержит урок, но без вопросов: применение доходит до сборки копии и честно
  // отказывает на её форме — это примененческий путь, а не промежуточный отказ 4.21.
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "APPROVE"}),
    /edit copy needs questions/);
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-decide-1`);
  assert.equal(record.status, PROPOSAL_STATUS.CHECK_PASSED, "применение атомарно: отказ — записи нет");
});

test("форма решения: неизвестный исход, битый id и длинный комментарий отвергаются", async () => {
  await seedChecked();
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "MAYBE"}),
    /APPROVE or REJECT/);
  await assert.rejects(decide(AUTHOR, {proposalId: "", decision: "REJECT"}), /proposalId is required/);
  await assert.rejects(decide(AUTHOR, {proposalId: "prop-decide-1", decision: "REJECT",
    comment: "x".repeat(2001)}), /comment is too long/);
});
