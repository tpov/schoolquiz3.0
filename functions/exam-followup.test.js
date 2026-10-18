"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");

/**
 * Покрытие фиксов follow-up-прохода ревью 3: причина таймаута между заходами sweeper'а,
 * осушение бросающих сидингов, возврат осушённого в очередь по finishSitting, испорченный
 * учтённый максимум (явная неудача, не ноль), восстановление FAILED_RECOVERABLE подписывания,
 * не-SCORED исход коррекции финала, корень serverNowMs и формы ответов обёрток.
 */

// --- Обёртки index.js требуют firebase-admin до всего остального. ---
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const adminStub = new Module(adminPath, null);
adminStub.filename = adminPath;
adminStub.loaded = true;
adminStub.exports = admin;
require.cache[adminPath] = adminStub;
const functions = require("./index");

const {makeFakeDb} = require("./_exam-test-db");
const {createExamSittings, SITTING_STATE, SLOT_STATE, TERMINAL_QUERY_TRUE} = require("./exam-sitting");
const {createExamTerminal, POISON_ATTEMPT_LIMIT, CLOSE_REASON} = require("./exam-terminal");
const {createCertificateSigning, issuanceFact, SIGNING_STATE} = require("./exam-certificates");
const {createExamCorrections, CORRECTION_STATE} = require("./exam-corrections");
const {assessmentStateId, coverageDocumentId, digest, requiredManifest} = require("./exam-coverage");

const NOW = 1_700_000_000_000;

// --- Каркас мира: одна тема с 5 EASY + 15 HARD, игрок со стандартными очками. ---
function seededWorld() {
  const rows = {};
  rows["quests/bigcourse"] = {id: "bigcourse", questType: "REGULAR", catalogId: "catalog",
    version: 7, authorUid: "author", archived: false};
  rows["sections/sec0"] = {id: "sec0", questId: "bigcourse", archived: false};
  rows["themes/th0"] = {id: "th0", sectionId: "sec0", archived: false};
  rows["lessons/les0"] = {id: "les0", themeId: "th0", version: 4, reviewApprovedAtMs: 1,
    reviewSubmissionId: "sub", archived: false};
  const questionText = "v".repeat(120);
  for (let at = 0; at < 5; at += 1) {
    rows[`questions/th0-e${at}`] = {lessonId: "les0", version: 2, difficulty: "EASY",
      payload: JSON.stringify({id: `e${at}`, type: "SingleChoice", text: questionText,
        options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"})};
  }
  for (let at = 0; at < 15; at += 1) {
    rows[`questions/th0-h${at}`] = {lessonId: "les0", version: 2, difficulty: "HARD",
      payload: JSON.stringify({id: `h${at}`, type: "SingleChoice", text: questionText,
        options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"})};
  }
  rows["users/player"] = {uid: "player", lifePoints: 10000, lifePointsUpdatedAtMs: 0,
    standardHearts: 10, nolics: 0, pointsSkill: 0, accountEpoch: 1, plasmaPoints: 0, goldHearts: 0};
  rows["configs/exam"] = {examEnabled: true, passMarkPercent: 60, nextQuestionWindowSeconds: 60,
    abandonmentGraceMs: 300000, paidTermsRevision: 1, certificateSigningKeyId: "k1"};
  const fake = makeFakeDb({initialRows: rows, nowMs: NOW});

  const loadLessonContext = async (tx, lessonId) => {
    const snapshot = await tx.get(fake.db.doc(`lessons/${lessonId}`));
    if (!snapshot.exists) return null;
    const lesson = snapshot.data() || {};
    return {lessonId, authorUid: "author", catalogId: "catalog", questId: "bigcourse",
      sectionId: "sec0", themeId: lesson.themeId, contentVersion: lesson.version,
      published: lesson.archived !== true, reviewApproved: true, questType: "REGULAR"};
  };
  const loadExamConfig = async () => ({examEnabled: true, passMarkPercent: 60,
    nextQuestionWindowSeconds: 60, abandonmentGraceMs: 300000, paidTermsRevision: 1,
    certificateSigningKeyId: "k1"});
  const sittings = createExamSittings({db: fake.db, nowMs: () => fake.clock.now,
    loadLessonContext, loadExamConfig,
    readRegeneration: async (tx, {user}) => ({caughtUp: true,
      userPatch: {lifePoints: user.lifePoints, lifePointsUpdatedAtMs: user.lifePointsUpdatedAtMs,
        hasPremium: false}}),
    writeRegeneration: () => {}});
  const terminal = createExamTerminal({db: fake.db, nowMs: () => fake.clock.now, loadExamConfig,
    log: () => {}});
  return {fake, sittings, terminal};
}

/** Сидинг в форме очереди sweeper'а: 19 ответов верных, последний слот открыт. */
function queuedSitting({uid = "player", mode = "ThemeTest", assessmentId = "th0",
  state = SITTING_STATE.OPEN, corrupt = false} = {}) {
  const slots = Array.from({length: 20}, (_, index) => ({
    index, canonicalId: `q${index}`, questionId: `q${index}`, lessonId: "les0", revision: 1,
    difficulty: mode === "ThemeTest" ? (index < 5 ? "EASY" : "HARD") : "HARD",
    shownDifficulty: mode === "ThemeTest" ? (index < 5 ? "EASY" : "HARD") : "HARD",
    worthSeconds: 30, answerSeconds: 24,
    canonicalContent: {type: "SingleChoice", text: "q", options: [{id: "a", text: "o"}],
      correctOptionId: "a"},
    key: {type: "SingleChoice"},
    state: index < 19 ? SLOT_STATE.ANSWERED : SLOT_STATE.DELIVERED,
    windowDeadlineMs: null, ack: null, answer: null, publicPayload: "{}",
  }));
  if (!corrupt) {
    slots[18].ack = {ackedAtMs: NOW - 1000, answerDeadlineMs: NOW - 500};
    slots[19].ack = {ackedAtMs: NOW - 1000, answerDeadlineMs: NOW + 60000};
  }
  return {
    schemaVersion: 1, sittingId: `sit-${uid}-${assessmentId}`, uid, mode, assessmentId,
    state, currentIndex: 19, version: 5,
    rules: {nextQuestionWindowSeconds: 60, firstAckGapSeconds: 60, passMarkPercent: 60,
      examRulesVersion: 1},
    firstAckGapDeadlineMs: NOW - 100000, abandonmentGraceMs: 300000,
    closeReasonCandidate: null, finishRequestedAtMs: null, terminalError: null,
    terminalQueryFlag: TERMINAL_QUERY_TRUE, nextDeadlineAtMs: NOW + 60000,
    bank: mode === "ThemeTest" ?
      {mode: "ThemeTest", TE: 180, TH: 360, Q: 1260, countEasy: 5, countHard: 15, ruleVersion: 1} :
      {mode: "FinalExam", TE: 0, TH: 480, Q: 1440, countEasy: 0, countHard: 20, ruleVersion: 1},
    slots: corrupt ? "not-a-list" : slots,
  };
}

test("start reply carries the root serverNowMs the wire contract promises", async () => {
  const {fake, sittings} = seededWorld();
  const questionRows = [
    ...Array.from({length: 5}, (_, at) => ({
      questionId: `th0-e${at}`, revision: 2, difficulty: "EASY", survey: false})),
    ...Array.from({length: 15}, (_, at) => ({
      questionId: `th0-h${at}`, revision: 2, difficulty: "HARD", survey: false})),
  ];
  const manifest = requiredManifest("les0", questionRows);
  fake.rows.set(`exam_coverage/${coverageDocumentId("player", "les0")}`, {schemaVersion: 1,
    uid: "player", lessonId: "les0", manifestHash: manifest.manifestHash,
    requiredCount: manifest.requiredTokens.length, complete: true, confirmedAtMs: NOW});
  const bucket = digest("player").slice(0, 2);
  fake.rows.set(`result_events_public/${bucket}/events/ordinary-0`, {userId: "player",
    lessonId: "les0", completed: true, settlementReceipt: {status: "SETTLED"},
    served: manifest.requiredTokens.slice(0, 5).map((token) => ({
      questionId: token.questionId, revision: token.revision, difficulty: "EASY"})),
    codeAnswer: "99999"});
  const start = await sittings.start({uid: "player", payload: {operationId: "op-now",
    accountEpoch: 1, themeId: "th0", shownTerms: true, acceptedTermsRevision: 1}, mode: "ThemeTest"});
  assert.equal(start.refused, undefined, JSON.stringify(start).slice(0, 200));
  assert.equal(start.serverNowMs, fake.clock.now, "корневой serverNowMs обязателен в каждой проекции");
});

test("a last-question timeout keeps its TIMEOUT reason across sweeper visits", async () => {
  const {fake, terminal} = seededWorld();
  // Последний слот просрочен: первый заход закрывает его (FINISH_REQUESTED + кандидат TIMEOUT),
  // второй — терминализует; причина обязана доехать до свидетельства закрытия.
  const sitting = queuedSitting({});
  sitting.slots[19].ack = {ackedAtMs: NOW - 1000, answerDeadlineMs: NOW - 500};
  fake.rows.set("exam_sittings/sit-1", sitting);
  fake.rows.set("exam_open_sittings/open-1", {sittingId: "sit-1", state: SITTING_STATE.OPEN});
  await terminal.sweep();
  const afterFirst = fake.rows.get("exam_sittings/sit-1");
  assert.equal(afterFirst.state, SITTING_STATE.FINISH_REQUESTED);
  assert.equal(afterFirst.terminalQueryFlag, TERMINAL_QUERY_TRUE,
    "ADVANCED не снимает флаг очереди: терминализация впереди");
  // Стаб Firestore выбрасывает документы без поля из orderBy; боевой ставит null первым.
  fake.rows.set("exam_sittings/sit-1", {...afterFirst, nextDeadlineAtMs: NOW});
  await terminal.sweep();
  const closed = fake.rows.get("exam_sittings/sit-1");
  assert.equal(closed.state, SITTING_STATE.CLOSED);
  assert.equal(closed.terminal.closeReason, CLOSE_REASON.TIMEOUT,
    "причина таймаута переживает границу заходов sweeper'а");
});

test("a sitting that throws on every visit drains after the poison limit", async () => {
  const {fake, terminal} = seededWorld();
  fake.rows.set("exam_sittings/sit-bad", queuedSitting({corrupt: true,
    state: SITTING_STATE.FINISH_REQUESTED}));
  await terminal.sweep();
  await terminal.sweep();
  assert.equal(fake.rows.get("exam_sittings/sit-bad").terminalQueryFlag, TERMINAL_QUERY_TRUE,
    "до лимита сидинг остаётся в очереди");
  await terminal.sweep();
  const drainedNow = fake.rows.get("exam_sittings/sit-bad");
  assert.equal(drainedNow.poisonAttempts, POISON_ATTEMPT_LIMIT);
  assert.equal(drainedNow.terminalQueryFlag, false, "бросающий сидинг осушается, как и POISON");
  assert.ok(drainedNow.poisonAt, "пометка осушения оставлена");
});

test("finishSitting re-queues a drained sitting so it can still reach closure", async () => {
  const {fake, sittings, terminal} = seededWorld();
  const sitting = queuedSitting({});
  fake.rows.set("exam_sittings/sit-2", {...sitting,
    terminalQueryFlag: false, poisonAttempts: 3, poisonAt: NOW});
  fake.rows.set("exam_open_sittings/open-2", {sittingId: "sit-2", state: SITTING_STATE.OPEN});
  const verdict = await sittings.finishSitting({uid: "player",
    payload: {sittingId: "sit-2"}});
  assert.equal(verdict.state, SITTING_STATE.FINISH_REQUESTED);
  assert.equal(fake.rows.get("exam_sittings/sit-2").terminalQueryFlag, TERMINAL_QUERY_TRUE,
    "пользовательский запрос закрытия возвращает осушённый сидинг в очередь");
  // Стаб Firestore выбрасывает документы без поля из orderBy; боевой ставит null первым.
  fake.rows.set("exam_sittings/sit-2", {...fake.rows.get("exam_sittings/sit-2"),
    nextDeadlineAtMs: NOW});
  await terminal.sweep();
  assert.equal(fake.rows.get("exam_sittings/sit-2").state, SITTING_STATE.CLOSED);
});

test("a corrupt accounted maximum fails the terminal commit instead of paying a full new progress", async () => {
  const {fake, terminal} = seededWorld();
  fake.rows.set("exam_sittings/sit-final", queuedSitting({mode: "FinalExam",
    assessmentId: "course", state: SITTING_STATE.FINISH_REQUESTED}));
  fake.rows.set("exam_assessment_state/" + assessmentStateId("player", "FinalExam", "course"),
    {uid: "player", mode: "FinalExam", assessmentId: "course", maxAccountedPercent: "junk"});
  await terminal.sweep();
  const poisoned = fake.rows.get("exam_sittings/sit-final");
  assert.equal(poisoned.terminalError, "CORRUPT_ACCOUNTED_PROGRESS",
    "испорченный b — явная незакрытая работа, а не ноль и не выплата");
  assert.equal(poisoned.state, SITTING_STATE.FINISH_REQUESTED, "сидинг не получает фальшивого результата");
});

test("finishSitting re-queues a drained FINISH_REQUESTED sitting too", async () => {
  const {fake, sittings, terminal} = seededWorld();
  const sitting = queuedSitting({});
  fake.rows.set("exam_sittings/sit-3", {...sitting,
    state: SITTING_STATE.FINISH_REQUESTED, finishRequestedAtMs: NOW,
    terminalQueryFlag: false, poisonAttempts: 3, poisonAt: NOW});
  fake.rows.set("exam_open_sittings/open-3", {sittingId: "sit-3",
    state: SITTING_STATE.FINISH_REQUESTED});
  const verdict = await sittings.finishSitting({uid: "player",
    payload: {sittingId: "sit-3"}});
  assert.equal(verdict.alreadyRequested, true, "нового запроса нет — закрытие уже запрошено");
  assert.equal(fake.rows.get("exam_sittings/sit-3").terminalQueryFlag, TERMINAL_QUERY_TRUE,
    "осушённый запрос закрытия возвращается в очередь, как и осушённый открытый");
  // Стаб Firestore выбрасывает документы без поля из orderBy; боевой ставит null первым.
  fake.rows.set("exam_sittings/sit-3", {...fake.rows.get("exam_sittings/sit-3"),
    nextDeadlineAtMs: NOW});
  await terminal.sweep();
  assert.equal(fake.rows.get("exam_sittings/sit-3").state, SITTING_STATE.CLOSED,
    "возвращённый в очередь сидинг доезжает до закрытия");
});

test("a corrupt user balance fails the terminal commit instead of zeroing the wallet", async () => {
  const {fake, terminal} = seededWorld();
  fake.rows.set("exam_sittings/sit-final", queuedSitting({mode: "FinalExam",
    assessmentId: "course", state: SITTING_STATE.FINISH_REQUESTED}));
  fake.rows.set("users/player", {uid: "player", nolics: "junk", pointsSkill: 0});
  await terminal.sweep();
  const poisoned = fake.rows.get("exam_sittings/sit-final");
  assert.equal(poisoned.terminalError, "CORRUPT_USER_BALANCE",
    "битый кошелёк — явная незакрытая работа, а не ноль и перезапись кошелька");
  assert.equal(fake.rows.get("users/player").nolics, "junk",
    "кошелёк своей же merge-записью не перезаписан");
});

test("a corrupt best percent fails the terminal commit instead of silently regressing the best", async () => {
  const {fake, terminal} = seededWorld();
  fake.rows.set("exam_sittings/sit-final", queuedSitting({mode: "FinalExam",
    assessmentId: "course", state: SITTING_STATE.FINISH_REQUESTED}));
  fake.rows.set("exam_assessment_state/" + assessmentStateId("player", "FinalExam", "course"),
    {uid: "player", mode: "FinalExam", assessmentId: "course", bestPercent: "junk"});
  await terminal.sweep();
  const poisoned = fake.rows.get("exam_sittings/sit-final");
  assert.equal(poisoned.terminalError, "CORRUPT_BEST_PERCENT",
    "битый лучший процент — явный отказ, а не тихая регрессия к нулю");
});

test("materialize refuses a correction head whose sitting belongs to another owner or assessment", async () => {
  const fake = makeFakeDb({initialRows: {}, nowMs: NOW});
  const sitting = queuedSitting({mode: "FinalExam", assessmentId: "course",
    state: SITTING_STATE.CLOSED});
  sitting.sittingId = "sit-corr";
  // Сидинг чужой пары пользователь/оценка: аппенд головы его не читает — материализатор обязан.
  fake.rows.set("exam_sittings/sit-corr", {...sitting, uid: "other-player",
    assessmentId: "other-course",
    terminal: {resultVersion: 2, closedAtMs: NOW, closeReason: CLOSE_REASON.COMPLETED,
      projection: {percent: 55, passed: false, starsTenths: null}}});
  const corrections = createExamCorrections({
    db: fake.db, nowMs: () => fake.clock.now,
    // Настоящий предикат index.js: авторство куратора в записи.
    authorizeExclusion: (record) => Boolean(record && record.authorizedByCuratorUid),
  });
  const applied = await corrections.appendExclusion({
    record: {correctionId: "corr-foreign", uid: "player", mode: "FinalExam",
      assessmentId: "course", sittingId: "sit-corr", slotIndex: 0,
      actorUid: "curator", reason: "confirmed fault", reviewedAtMs: NOW,
      originalDigest: null, authorizedByCuratorUid: "curator"},
  });
  assert.equal(applied.applied, true, "аппенд головы не читает сидинг и проходит");
  await assert.rejects(
    () => corrections.materialize({uid: "player", mode: "FinalExam", assessmentId: "course"}),
    (error) => error.code === "SITTING_MISMATCH",
    "пересчёт из чужого сидинга — типизированный отказ, а не тихая публикация");
});

test("fulfilCertificateSigning refuses a non-document-safe issuanceId with invalid-argument", async () => {
  store.reset({"profiles/curator": {adminLevel: 100}});
  await assert.rejects(
    () => functions.fulfilCertificateSigning.run({auth: {uid: "curator"},
      data: {issuanceId: "a/b", fence: "f"}}),
    (error) => error.code === "invalid-argument",
    "issuanceId становится путём документа: отказ до Firestore, а не посреди вызова");
});

test("a theme over the aggregate pool budget refuses typed before any charge", async () => {
  const rows = {};
  rows["quests/bigcourse"] = {id: "bigcourse", questType: "REGULAR", catalogId: "catalog",
    version: 7, authorUid: "author", archived: false};
  rows["sections/sec0"] = {id: "sec0", questId: "bigcourse", archived: false};
  rows["themes/th0"] = {id: "th0", sectionId: "sec0", archived: false};
  const questionText = "v".repeat(120);
  const payload = JSON.stringify({id: "q", type: "SingleChoice", text: questionText,
    options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"});
  // 64 урока по 200 вопросов: по-коллекционные лимиты соблюдены, сумма (12 800 > 12 000) — нет.
  for (let lesson = 0; lesson < 64; lesson += 1) {
    rows[`lessons/les-${lesson}`] = {id: `les-${lesson}`, themeId: "th0", version: 4,
      reviewApprovedAtMs: 1, reviewSubmissionId: "sub", archived: false};
    for (let question = 0; question < 200; question += 1) {
      rows[`questions/les-${lesson}-q${question}`] = {lessonId: `les-${lesson}`, version: 2,
        difficulty: "HARD", payload};
    }
  }
  rows["users/player"] = {uid: "player", lifePoints: 10000, lifePointsUpdatedAtMs: 0,
    standardHearts: 10, nolics: 0, pointsSkill: 0, accountEpoch: 1, plasmaPoints: 0, goldHearts: 0};
  rows["configs/exam"] = {examEnabled: true, passMarkPercent: 60, nextQuestionWindowSeconds: 60,
    abandonmentGraceMs: 300000, paidTermsRevision: 1, certificateSigningKeyId: "k1"};
  const fake = makeFakeDb({initialRows: rows, nowMs: NOW});
  const loadLessonContext = async (tx, lessonId) => {
    const snapshot = await tx.get(fake.db.doc(`lessons/${lessonId}`));
    if (!snapshot.exists) return null;
    const lesson = snapshot.data() || {};
    return {lessonId, authorUid: "author", catalogId: "catalog", questId: "bigcourse",
      sectionId: "sec0", themeId: lesson.themeId, contentVersion: lesson.version,
      published: lesson.archived !== true, reviewApproved: true, questType: "REGULAR"};
  };
  const loadExamConfig = async () => ({examEnabled: true, passMarkPercent: 60,
    nextQuestionWindowSeconds: 60, abandonmentGraceMs: 300000, paidTermsRevision: 1,
    certificateSigningKeyId: "k1"});
  const sittings = createExamSittings({db: fake.db, nowMs: () => fake.clock.now,
    loadLessonContext, loadExamConfig,
    readRegeneration: async (tx, {user}) => ({caughtUp: true,
      userPatch: {lifePoints: user.lifePoints, lifePointsUpdatedAtMs: user.lifePointsUpdatedAtMs,
        hasPremium: false}}),
    writeRegeneration: () => {}});
  await assert.rejects(
    () => sittings.start({uid: "player", payload: {operationId: "op-big",
      accountEpoch: 1, themeId: "th0", shownTerms: true, acceptedTermsRevision: 1},
      mode: "ThemeTest"}),
    (error) => error.code === "BANK_TOO_LARGE",
    "переполненный пул темы — типизированный отказ, а не нечитаемая ошибка транзакции");
  assert.equal(fake.rows.get("users/player").nolics, 0, "отказ до списания: кошелёк не тронут");
});

function signingWorld({signer}) {
  const fake = makeFakeDb({initialRows: {}, nowMs: NOW});
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now, signer});
  return {fake, signing};
}

function factRow({uid = "player"} = {}) {
  return issuanceFact({uid, courseId: "course", sittingId: "sit-final", resultVersion: 3,
    percent: 88, passMark: 60, issuedAtMs: NOW, keyId: "k1",
    bankSnapshot: {manifestHash: "mh", TE: 0, TH: 480}});
}

test("FAILED_RECOVERABLE signing work is claimable after the fence expires and completes", async () => {
  const {fake, signing} = signingWorld({signer: {
    available: (keyId) => keyId === "k1",
    sign: async (claims) => ({signature: `sig:${claims.sittingId}`, algorithm: "TEST-SIG"}),
    verify: () => true,
  }});
  const fact = factRow();
  fake.rows.set("exam_certificates/i1", fact);
  fake.rows.set("exam_signing_work/i1", {state: SIGNING_STATE.FAILED_RECOVERABLE,
    claimsDigest: fact.claimsDigest, fence: "old", fenceExpiresAtMs: NOW - 1, attempts: 1});
  const fence = await signing.claimFence("i1");
  assert.ok(fence, "восстановимая работа с освободившимся замком берётся в очередь снова");
  const outcome = await signing.fulfil({issuanceId: "i1", fence});
  assert.equal(outcome.fulfilled, true, JSON.stringify(outcome));
  assert.equal(fake.rows.get("exam_signing_work/i1").state, SIGNING_STATE.SIGNED);
});

test("a throwing verifier marks the work FAILED_RECOVERABLE, and the next window completes it", async () => {
  let verifyShouldThrow = true;
  const {fake, signing} = signingWorld({signer: {
    available: (keyId) => keyId === "k1",
    sign: async (claims) => ({signature: `sig:${claims.sittingId}`, algorithm: "TEST-SIG"}),
    verify: () => {
      if (verifyShouldThrow) throw new Error("key service unavailable");
      return true;
    },
  }});
  const fact = factRow();
  fake.rows.set("exam_certificates/i2", fact);
  fake.rows.set("exam_signing_work/i2", {state: SIGNING_STATE.PENDING,
    claimsDigest: fact.claimsDigest, attempts: 0});
  const fence = await signing.claimFence("i2");
  const first = await signing.fulfil({issuanceId: "i2", fence});
  assert.deepEqual({fulfilled: first.fulfilled, reason: first.reason},
    {fulfilled: false, reason: "VERIFY_FAILED"});
  assert.equal(fake.rows.get("exam_signing_work/i2").state, SIGNING_STATE.FAILED_RECOVERABLE);
  // Окно замка истекает, подписант здоров: та же работа завершается, без новой идентичности.
  verifyShouldThrow = false;
  fake.rows.set("exam_signing_work/i2",
    {...fake.rows.get("exam_signing_work/i2"), fenceExpiresAtMs: NOW - 1});
  const fence2 = await signing.claimFence("i2");
  assert.ok(fence2, "после истечения замка восстановимая работа снова берётся");
  const second = await signing.fulfil({issuanceId: "i2", fence: fence2});
  assert.equal(second.fulfilled, true);
  assert.equal(fake.rows.get("exam_signing_work/i2").state, SIGNING_STATE.SIGNED);
});

test("a non-SCORED corrected final marks the state pending instead of stamping a correction", async () => {
  const {fake} = (() => {
    const fake = makeFakeDb({initialRows: {}, nowMs: NOW});
    return {fake};
  })();
  const sitting = queuedSitting({mode: "FinalExam", assessmentId: "course",
    state: SITTING_STATE.CLOSED});
  sitting.sittingId = "sit-corr";
  fake.rows.set("exam_sittings/sit-corr", {...sitting,
    terminal: {resultVersion: 2, closedAtMs: NOW, closeReason: CLOSE_REASON.COMPLETED,
      projection: {percent: 55, passed: false, starsTenths: null}}});
  const corrections = createExamCorrections({
    db: fake.db, nowMs: () => fake.clock.now,
    // Настоящий предикат index.js: авторство куратора в записи.
    authorizeExclusion: (record) => Boolean(record && record.authorizedByCuratorUid),
  });
  for (let slot = 0; slot < 20; slot += 1) {
    const applied = await corrections.appendExclusion({
      record: {correctionId: `corr-${slot}`, uid: "player", mode: "FinalExam",
        assessmentId: "course", sittingId: "sit-corr", slotIndex: slot,
        actorUid: "curator", reason: "confirmed fault", reviewedAtMs: NOW,
        originalDigest: null, authorizedByCuratorUid: "curator"},
    });
    assert.equal(applied.applied, true, JSON.stringify(applied).slice(0, 200));
  }
  const outcome = await corrections.materialize({uid: "player", mode: "FinalExam",
    assessmentId: "course"});
  assert.equal(outcome.published, true, JSON.stringify(outcome).slice(0, 200));
  assert.equal(outcome.pending, true, "все слоты исключены: числового вердикта нет");
  const state = fake.rows.get(`exam_assessment_state/${
    assessmentStateId("player", "FinalExam", "course")}`);
  assert.equal(state.correctionPendingState, outcome.outcome.state,
    "не-SCORED исход помечает состояние ожидающим, каким бы именем его ни звала C2");
  assert.equal(state.correctedAtMs, undefined, "штамп «исправлено» без вердикта не ставится");
  assert.notEqual(state.correctionPendingState, CORRECTION_STATE.SCORED);
});

// --- Формы ответов обёрток index.js — на живом вызове, не на присылаемых фикстурах. ---
test("certificate wrapper replies carry the exact four-key shape on every branch", async () => {
  store.reset({
    "exam_certificates/i-pending": factRow({}),
    "exam_certificate_public/i-valid": {keyId: "k1", expiresAtMs: Date.now() + 86400000, courseId: "course",
      percent: 88, issuedAtMs: NOW, signature: "sig", algorithm: "TEST-SIG"},
    "exam_certificate_keys/k1": {active: true},
  });
  const missing = await functions.publicVerifyCertificate.run({data: {issuanceId: "nope"}});
  assert.deepEqual(Object.keys(missing).sort(), ["found", "issuanceId", "snapshot", "status"]);
  assert.equal(missing.snapshot, null);
  assert.equal(missing.status, "not_found");
  const pending = await functions.publicVerifyCertificate.run({data: {issuanceId: "i-pending"}});
  assert.deepEqual(Object.keys(pending).sort(), ["found", "issuanceId", "snapshot", "status"]);
  assert.equal(pending.status, "pending_signing");
  assert.equal(pending.snapshot, null);
  const valid = await functions.publicVerifyCertificate.run({data: {issuanceId: "i-valid"}});
  assert.deepEqual(Object.keys(valid).sort(), ["found", "issuanceId", "snapshot", "status"]);
  assert.equal(valid.status, "valid");
  assert.ok(valid.snapshot && valid.snapshot.signature, "найденная ветвь отдаёт снимок");
});

test("fetchMyCertificates returns the bounded profile projection shape", async () => {
  store.reset({"exam_certificates/i3": {...factRow({}), signing: {state: "PENDING"}}});
  const reply = await functions.fetchMyCertificates.run({auth: {uid: "player"}, data: {}});
  assert.deepEqual(Object.keys(reply), ["certificate"]);
  assert.deepEqual(Object.keys(reply.certificate).sort(),
    ["courseId", "expiresAtMs", "issuanceId", "issuedAtMs", "passMark", "percent", "signingState"]);
  const empty = await functions.fetchMyCertificates.run({auth: {uid: "nobody"}, data: {}});
  assert.deepEqual(empty, {certificate: null});
});

test("the real authorizeExclusion predicate lets a curator append a correction head", async () => {
  store.reset({
    "profiles/curator": {adminLevel: 100},
  });
  const reply = await functions.appendExamCorrection.run({auth: {uid: "curator"}, data: {
    correctionId: "corr-real-1", mode: "ThemeTest", ownerUid: "player", assessmentId: "th0",
    sittingId: "sit-3", slotIndex: 2, reason: "confirmed fault",
  }});
  assert.equal(reply.applied, true, JSON.stringify(reply).slice(0, 200));
  const heads = [...store.documents.keys()].filter((path) => path.startsWith("exam_correction_heads/"));
  assert.equal(heads.length, 1, `голова коррекции записана: ${heads.join(",")}`);
  // Не-куратор этим же путём отвергается до всякого чтения.
  await assert.rejects(
    () => functions.appendExamCorrection.run({auth: {uid: "player"}, data: {
      correctionId: "corr-real-2", mode: "ThemeTest", ownerUid: "player", assessmentId: "th0",
      sittingId: "sit-3", slotIndex: 3, reason: "nope"}}),
    /Curator is required|CURATOR_REQUIRED/,
  );
});
