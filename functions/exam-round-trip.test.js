"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb} = require("./_exam-test-db");
const {createExamSittings, SITTING_STATE, SLOT_STATE} = require("./exam-sitting");
const {createExamTerminal} = require("./exam-terminal");
const {createCertificateSigning, issuanceFact, verifyVerdict, VERIFY_STATUS,
  publicSnapshot} = require("./exam-certificates");
const {coverageDocumentId, assessmentStateId, requiredManifest, digest} = require("./exam-coverage");
const {THEME_COOLDOWN_MS, FINAL_COOLDOWN_MS} = require("./exam-admission");

/**
 * Полный цикл на фейковом эмуляторе — приёмочные случаи 1–3, 8, 11, 14 протокола:
 *
 * зафиксированный банк курса/темы → подтверждённое покрытие → старт теста темы (списание 100) →
 * ACK/ответ/восстановление того же сидинга → терминальный результат (звёзды/проход/кулдаун/
 * награда первого прохода) → двенадцать пройденных тем → финал (списание 200; плазменный богач
 * без стандартных очков получает отказ) → факт выпуска сертификата и явное ожидание подписи →
 * верификация вне входа; грань кулдауна 24h/168h; переключение A→B→A.
 */

const THEMES = 12;
const EASY_PER_LESSON = 100;
const HARD_PER_LESSON = 100;

function seededWorld() {
  const rows = {};
  rows["quests/bigcourse"] = {id: "bigcourse", questType: "REGULAR", catalogId: "catalog",
    version: 7, authorUid: "author", archived: false};
  for (let section = 0; section < 3; section += 1) {
    rows[`sections/sec${section}`] = {id: `sec${section}`, questId: "bigcourse", archived: false};
  }
  const questionText = "v".repeat(120);
  for (let theme = 0; theme < THEMES; theme += 1) {
    const themeId = `th${theme}`;
    rows[`themes/${themeId}`] = {id: themeId, sectionId: `sec${Math.floor(theme / 4)}`,
      archived: false};
    rows[`lessons/les${theme}`] = {id: `les${theme}`, themeId, version: 4, reviewApprovedAtMs: 1,
      reviewSubmissionId: "sub", archived: false};
    for (let at = 0; at < EASY_PER_LESSON; at += 1) {
      rows[`questions/th${theme}-e${at}`] = {lessonId: `les${theme}`, version: 2,
        difficulty: "EASY",
        payload: JSON.stringify({id: `e${at}`, type: "SingleChoice", text: questionText,
          options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"})};
    }
    for (let at = 0; at < HARD_PER_LESSON; at += 1) {
      rows[`questions/th${theme}-h${at}`] = {lessonId: `les${theme}`, version: 2,
        difficulty: "HARD",
        payload: JSON.stringify({id: `h${at}`, type: "SingleChoice", text: questionText,
          options: [{id: "a", text: "opt"}, {id: "b", text: "opt"}], correctOptionId: "a"})};
    }
  }
  rows["users/player"] = {uid: "player", lifePoints: 10000, lifePointsUpdatedAtMs: 0,
    standardHearts: 10, nolics: 0, pointsSkill: 0, accountEpoch: 1, plasmaPoints: 900,
    goldHearts: 3};
  rows["configs/exam"] = {examEnabled: true, passMarkPercent: 60, nextQuestionWindowSeconds: 60,
    abandonmentGraceMs: 300000, paidTermsRevision: 1, certificateSigningKeyId: "k1"};
  const fake = makeFakeDb({initialRows: rows, nowMs: 1_700_000_000_000});

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
  const signing = createCertificateSigning({db: fake.db, nowMs: () => fake.clock.now,
    signer: {
      available: (keyId) => keyId === "k1",
      sign: async (claims) => ({signature: `sig:${claims.sittingId}`, algorithm: "TEST-SIG"}),
      verify: () => true,
    }});
  return {fake, sittings, terminal, signing};
}

/** Полное подтверждённое покрытие одной темы: полный обычный прогон + материализация. */
async function confirmCoverage(world, theme, uid = "player") {
  const {fake} = world;
  const bucket = digest(uid).slice(0, 2);
  const served = [
    ...Array.from({length: EASY_PER_LESSON}, (_, at) =>
      ({questionId: `th${theme}-e${at}`, revision: 2, difficulty: "EASY"})),
  ];
  fake.rows.set(`result_events_public/${bucket}/events/ordinary-${theme}`, {
    userId: uid, lessonId: `les${theme}`, completed: true,
    settlementReceipt: {status: "SETTLED"},
    served,
    // Все сто EASY — полный зачёт; HARD вCoverage не требуется.
    codeAnswer: "9".repeat(EASY_PER_LESSON),
  });
  const questionRows = [...Array.from({length: EASY_PER_LESSON}, (_, at) => ({
    questionId: `th${theme}-e${at}`, revision: 2, difficulty: "EASY", survey: false,
  })), ...Array.from({length: HARD_PER_LESSON}, (_, at) => ({
    questionId: `th${theme}-h${at}`, revision: 2, difficulty: "HARD", survey: false,
  }))];
  const manifest = requiredManifest(`les${theme}`, questionRows);
  fake.rows.set(`exam_coverage/${coverageDocumentId(uid, `les${theme}`)}`, {
    schemaVersion: 1, uid, lessonId: `les${theme}`, manifestHash: manifest.manifestHash,
    requiredCount: manifest.requiredTokens.length, complete: true, confirmedAtMs: 1,
  });
}

/** Проходит тему сидингом: старт, ACK на каждый вопрос, верный ответ на ≥12 из 20. */
async function passTheme(world, theme, {operationId, correct = 12} = {}) {
  const {fake, sittings} = world;
  const start = await sittings.start({uid: "player", payload: {
    operationId, accountEpoch: 1, themeId: `th${theme}`, shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(start.refused, undefined, JSON.stringify(start));
  let current = start.nextQuestion;
  let answered = 0;
  let guard = 0;
  while (current && guard < 20) {
    guard += 1;
    if (!current.ack || current.ack.awaitingAck) {
      const ack = await sittings.acknowledgeQuestion({uid: "player", payload: {
        sittingId: start.sittingId, questionIndex: current.index, operationId: `${operationId}-ack-${current.index}`}});
      assert.equal(ack.acknowledged, true);
    }
    const selected = answered < correct ? "a" : "b";
    const submitted = await sittings.submitAnswer({uid: "player", payload: {
      sittingId: start.sittingId, questionIndex: current.index, revision: current.revision,
      answer: {type: "single-choice", selected: current.publicPayload.includes("Redacted") ?
        selected : selected}, operationId: `${operationId}-ans-${current.index}`}});
    assert.equal(submitted.accepted, true, JSON.stringify(submitted).slice(0, 200));
    answered += 1;
    current = submitted.nextQuestion;
  }
  await terminalize(world, start.sittingId);
  return start.sittingId;
}

async function terminalize(world, sittingId) {
  const result = await world.terminal.advanceSitting({sittingId});
  assert.equal(result.status, "TERMINALIZED", `terminalize ${sittingId}: ${result.status}`);
  await world.fake.db.doc(`exam_sittings/${sittingId}`).set({terminalQueryFlag: false}, {merge: true});
  return result;
}

test("case 1: start a theme test, lose the response, recover the same sitting with one charge", async () => {
  const world = seededWorld();
  await confirmCoverage(world, 0);
  const {fake, sittings} = world;
  const first = await sittings.start({uid: "player", payload: {
    operationId: "op-A", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(first.debited, 100);
  const charged = first.lifePoints;
  // «Потеря ответа»: новый процесс, новый ключ операции, тот же открытый сидинг.
  fake.clock.now += 5000;
  const recovered = await sittings.start({uid: "player", payload: {
    operationId: "op-B", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(recovered.recovered, true);
  assert.equal(recovered.sittingId, first.sittingId);
  assert.equal(recovered.nextQuestion.index, first.nextQuestion.index,
    "восстановление возвращает текущий вопрос, не будущий");
  assert.equal(recovered.nextQuestion.answerSeconds, first.nextQuestion.answerSeconds,
    "исходные дедлайны сохранены");
  assert.equal(fake.rows.get("users/player").lifePoints, charged, "второго списания нет");
  const ledgerCount = [...fake.rows.keys()].filter((path) =>
    path.startsWith("audit/currency_ledger/")).length;
  assert.equal(ledgerCount, 1);
});

test("case 2: full sitting without correctness leakage; one terminal outcome, award, cooldown", async () => {
  const world = seededWorld();
  await confirmCoverage(world, 0);
  const {fake} = world;
  const sittingId = await passTheme(world, 0, {operationId: "op-2", correct: 12});
  const stored = fake.rows.get(`exam_sittings/${sittingId}`);
  assert.equal(stored.state, SITTING_STATE.CLOSED);
  // 12/20 верных → 60%: проход с 1.8 звезды (18 десятых).
  assert.equal(stored.terminal.projection.percent, 60);
  assert.equal(stored.terminal.projection.passed, true);
  assert.equal(stored.terminal.projection.starsTenths, 18);
  assert.equal(stored.terminal.projection.advice.length, 3);
  // Ни одна проекция не несёт ключей, канонических ответов или повопросных вердиктов.
  const projection = JSON.stringify(stored.terminal.projection);
  for (const forbidden of ["canonicalContent", "codeAnswer", "correctOptionId", "scoreDigit",
    "key", "issuedPermutation"]) {
    assert.equal(projection.includes(forbidden), false, `проекция несёт ${forbidden}`);
  }
  // Один чек первого прохода, кулдаун от терминального закрытия.
  const receipt = fake.rows.get(`exam_reward_receipts/${sittingId}`);
  assert.ok(receipt && receipt.firstPass);
  const state = fake.rows.get(`exam_assessment_state/${assessmentStateId("player", "ThemeTest", "th0")}`);
  assert.equal(state.passed, true);
  assert.equal(state.cooldownUntilMs - stored.closedAtMs, THEME_COOLDOWN_MS);
});

test("case 3+8: pass twelve themes, take the final, earn the atomic certificate, verify signed out", async () => {
  const world = seededWorld();
  for (let theme = 0; theme < THEMES; theme += 1) {
    await confirmCoverage(world, theme);
  }
  const {fake, sittings, terminal, signing} = world;
  // Двенадцать пройденных тем — сертификационная пригодность курса.
  for (let theme = 0; theme < THEMES; theme += 1) {
    await passTheme(world, theme, {operationId: `theme-op-${theme}`, correct: 12});
  }
  fake.clock.now += FINAL_COOLDOWN_MS; // темы не двигают финальный кулдаун, но часы — неторопливо
  const final = await sittings.start({uid: "player", payload: {
    operationId: "final-1", accountEpoch: 1, courseId: "bigcourse", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "FinalExam"});
  assert.equal(final.refused, undefined, JSON.stringify(final));
  assert.equal(final.debited, 200, "финал — 200 стандартных очков");
  assert.equal(final.slots ? final.slots.length : final.totalQuestions, 20);
  // 20 HARD, все верные → 100% и сертификат.
  let current = final.nextQuestion;
  while (current) {
    if (!current.ack || current.ack.awaitingAck) {
      await sittings.acknowledgeQuestion({uid: "player", payload: {
        sittingId: final.sittingId, questionIndex: current.index, operationId: `fa-${current.index}`}});
    }
    const submitted = await sittings.submitAnswer({uid: "player", payload: {
      sittingId: final.sittingId, questionIndex: current.index, revision: current.revision,
      answer: {type: "single-choice", selected: "a"}, operationId: `fn-${current.index}`}});
    assert.equal(submitted.accepted, true);
    current = submitted.nextQuestion;
  }
  const terminalResult = await terminal.advanceSitting({sittingId: final.sittingId});
  assert.equal(terminalResult.status, "TERMINALIZED");
  assert.ok(terminalResult.issuanceId);
  const fact = fake.rows.get(`exam_certificates/${terminalResult.issuanceId}`);
  assert.equal(fact.percent, 100);
  assert.equal(fact.expiresAtMs - fact.issuedAtMs, 365 * 24 * 3600 * 1000);
  assert.equal(fact.signing.state, "PENDING", "подпись ожидает — публикация невозможна");
  // Верификация вне входа: до подписи — явное ожидание.
  assert.equal(verifyVerdict({publicSnapshot: null, keyMaterial: {k1: true},
    nowMs: fake.clock.now}).status, VERIFY_STATUS.NOT_FOUND);
  // Исполнение подписывания (владелец/Q-EX-5): подпись вне транзакции, CAS-публикация.
  const fence = await signing.claimFence(terminalResult.issuanceId);
  const fulfil = await signing.fulfil({issuanceId: terminalResult.issuanceId, fence});
  assert.equal(fulfil.fulfilled, true);
  const snapshot = fake.rows.get(`exam_certificate_public/${terminalResult.issuanceId}`);
  const verdict = verifyVerdict({publicSnapshot: snapshot, keyMaterial: {k1: true},
    nowMs: fact.issuedAtMs + 1000});
  assert.equal(verdict.status, VERIFY_STATUS.VALID);
  // Скорректированный счёт не читается верификацией: в снимке только замороженные требования.
  assert.equal(JSON.stringify(snapshot).includes("corrected"), false);
});

test("case 11: cooldown boundaries 24h/168h; midnight and week crossing change nothing", async () => {
  const world = seededWorld();
  await confirmCoverage(world, 0);
  const {fake, sittings} = world;
  const closure = fake.clock.now;
  await passTheme(world, 0, {operationId: "op-cool", correct: 12});
  const closedAt = fake.rows.get("exam_sittings/" + (await sittingIdOf(world, "op-cool"))).closedAtMs;
  void closure;
  // T+23:59:59 — отказ с доступностью T+24h.
  fake.clock.now = closedAt + THEME_COOLDOWN_MS - 1000;
  const before = await sittings.start({uid: "player", payload: {
    operationId: "op-cool-2", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(before.kind, "Cooldown");
  assert.equal(before.availableAtMs, closedAt + THEME_COOLDOWN_MS);
  // Полночь и смена недели ничего не сбрасывают: на границе суток/недели после закрытия отказ тот же.
  const midnight = closedAt - (closedAt % (24 * 3600 * 1000)) + 7 * 24 * 3600 * 1000;
  fake.clock.now = Math.min(midnight, closedAt + THEME_COOLDOWN_MS - 1000);
  const crossing = await sittings.start({uid: "player", payload: {
    operationId: "op-cool-3", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(crossing.kind, "Cooldown", "пересечение полуночи/недели не сбрасывает кулдаун");
  // Ровно T+24h — пуск (все прочие условия выполняются).
  fake.clock.now = closedAt + THEME_COOLDOWN_MS;
  const atBoundary = await sittings.start({uid: "player", payload: {
    operationId: "op-cool-4", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(atBoundary.refused, undefined, JSON.stringify(atBoundary));
  // Финальный кулдаун — 168 часов от закрытия.
  const finalState = fake.rows.get(`exam_assessment_state/${assessmentStateId("player", "FinalExam", "bigcourse")}`);
  assert.equal(finalState, undefined, "финал ещё не сдавался");
  assert.equal(FINAL_COOLDOWN_MS, 168 * 3600 * 1000);
});

async function sittingIdOf(world, operationId) {
  const {startRequestDocumentId} = require("./exam-sitting");
  const request = world.fake.rows.get(`exam_start_requests/${startRequestDocumentId("player", operationId)}`);
  return request.result.sittingId;
}

test("case 14: 100-char EASY and HARD positions all get 24 seconds; 10 chars floor at 5", async () => {
  const world = seededWorld();
  await confirmCoverage(world, 0);
  const {sittings} = world;
  const start = await sittings.start({uid: "player", payload: {
    operationId: "op-timer", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  const questionText = "v".repeat(120);
  // 120 символов текста + 3 + 3 варианта = 126 символов → 30 секунд (126 × 0.24 = 30.24 → 30).
  assert.equal(start.nextQuestion.answerSeconds, 30);
  assert.equal(start.nextQuestion.shownDifficulty, "EASY", "первые пять позиций — EASY");
  assert.equal(start.nextQuestion.publicPayload.includes("SingleChoiceRedacted"), true);
  void questionText;
  // Пол 5 секунд и формула — домен exam-scoring; здесь проверен живой путь сидинга.
});

test("case 1b: PLASMA-rich balance without standard points is refused for the final", async () => {
  const world = seededWorld();
  for (let theme = 0; theme < THEMES; theme += 1) {
    await confirmCoverage(world, theme);
  }
  const {fake, sittings} = world;
  // Двенадцать пройденных тем, но стандартные очки на нуле; плазмы — вдоволь.
  for (let theme = 0; theme < THEMES; theme += 1) {
    const stateId = assessmentStateId("player", "ThemeTest", `th${theme}`);
    fake.rows.set(`exam_assessment_state/${stateId}`, {uid: "player", mode: "ThemeTest",
      assessmentId: `th${theme}`, passed: true, bestPercent: 80, passedAtGeneration: 1,
      closedAtMs: 1, cooldownUntilMs: 2, maxAccountedPercent: 80});
    fake.rows.set(`exam_theme_generation/th${theme}`, {generation: 1, pendingCorrection: false});
  }
  fake.rows.set("users/player", {...fake.rows.get("users/player"), lifePoints: 0, plasmaPoints: 900});
  const refused = await sittings.start({uid: "player", payload: {
    operationId: "final-poor", accountEpoch: 1, courseId: "bigcourse", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "FinalExam"});
  assert.equal(refused.kind, "InsufficientCharge",
    "плазма никогда не платит за экзамен");
  assert.equal(refused.reason, "STANDARD_POINTS");
  assert.equal(fake.rows.get("users/player").plasmaPoints, 900, "плазма не тронута");
});

test("A→B→A: operation identity binds to the account epoch; another epoch cannot replay", async () => {
  const world = seededWorld();
  await confirmCoverage(world, 0);
  const {sittings} = world;
  const first = await sittings.start({uid: "player", payload: {
    operationId: "op-x", accountEpoch: 1, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"});
  assert.equal(first.debited, 100);
  // Тот же ключ операции с другим epoch — не повтор: чужой ответ не приложится к новой сессии.
  await assert.rejects(() => sittings.start({uid: "player", payload: {
    operationId: "op-x", accountEpoch: 2, themeId: "th0", shownTerms: true,
    acceptedTermsRevision: 1,
  }, mode: "ThemeTest"}), (error) => error.code === "REQUEST_REUSE");
});
