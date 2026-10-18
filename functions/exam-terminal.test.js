"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb, seedHierarchy} = require("./_exam-test-db");
const {
  CLOSE_REASON,
  TERMINAL_QUERY_TRUE,
  advanceExpiredSlot,
  advancePlan,
  createExamTerminal,
  planReward,
  slotDigit,
  terminalOutcome,
} = require("./exam-terminal");
const {SITTING_STATE, SLOT_STATE} = require("./exam-sitting");
const {POISON_ATTEMPT_LIMIT} = require("./exam-terminal");
const {EXAM_RULES_VERSION} = require("./exam-sitting");

const THEME_BASIS = {mode: "ThemeTest", TE: 180, TH: 360, Q: 1260, countEasy: 5, countHard: 15,
  sumEasy: 180, sumHard: 360, ruleVersion: 1};
const FINAL_BASIS = {mode: "FinalExam", TE: 0, TH: 480, Q: 1440, countEasy: 0, countHard: 20,
  sumEasy: 0, sumHard: 9600, ruleVersion: 1};

/** Сидинг в форме, которую worker встречает в очереди. */
function makeSitting({mode = "ThemeTest", state = SITTING_STATE.OPEN, currentIndex = 0,
  answers = {}, closeReason = null, uid = "player", assessmentId = "theme"} = {}) {
  const slots = Array.from({length: 20}, (_, index) => ({
    index,
    canonicalId: `q${index}`,
    questionId: `q${index}`,
    lessonId: "lesson",
    revision: 1,
    difficulty: mode === "ThemeTest" ? (index < 5 ? "EASY" : "HARD") : "HARD",
    shownDifficulty: mode === "ThemeTest" ? (index < 5 ? "EASY" : "HARD") : "HARD",
    worthSeconds: 30,
    answerSeconds: 24,
    canonicalContent: {type: "SingleChoice", text: "q", options: [{id: "a", text: "o"}],
      correctOptionId: "a"},
    key: {type: "SingleChoice"},
    state: SLOT_STATE.PENDING,
    windowDeadlineMs: null,
    ack: null,
    answer: null,
    publicPayload: "{}",
  }));
  slots[0].state = SLOT_STATE.DELIVERED;
  for (const [index, selected] of Object.entries(answers)) {
    const slot = slots[Number(index)];
    slot.state = SLOT_STATE.ANSWERED;
    slot.ack = {ackedAtMs: 1, answerDeadlineMs: 2};
    slot.answer = {canonicalAnswer: {type: "single-choice", selected}, fingerprint: `fp${index}`};
    if (Number(index) + 1 < 20 && slots[Number(index) + 1].state === SLOT_STATE.PENDING) {
      slots[Number(index) + 1].state = SLOT_STATE.DELIVERED;
    }
  }
  const now = 1_700_000_000_000;
  return {
    schemaVersion: 1, sittingId: `sitting-${uid}-${assessmentId}-${mode}`, uid,
    accountEpoch: 1, mode, assessmentId,
    state, createdAtMs: now, closedAtMs: null, closeReason,
    currentIndex, version: 1,
    rules: {examRulesVersion: EXAM_RULES_VERSION, passMarkPercent: 60,
      nextQuestionWindowSeconds: 60, firstAckGapSeconds: 60, abandonmentGraceMs: 300000,
      paidTermsRevision: 1, manifestHash: "mh"},
    bank: mode === "ThemeTest" ? THEME_BASIS : FINAL_BASIS,
    certification: null, courseVersion: null,
    slots,
    firstAckGapDeadlineMs: now + 60000,
    terminal: null,
    terminalQueryFlag: TERMINAL_QUERY_TRUE,
    nextDeadlineAtMs: null,
  };
}

function terminalWorld({initialRows = {}, nowMs = 1_700_000_000_000} = {}) {
  const fake = makeFakeDb({initialRows: {
    "users/player": {uid: "player", lifePoints: 100, nolics: 0, pointsSkill: 0},
    ...initialRows,
  }, nowMs});
  const terminal = createExamTerminal({
    db: fake.db, nowMs: () => fake.clock.now,
    loadExamConfig: async () => ({examEnabled: true, passMarkPercent: 60,
      nextQuestionWindowSeconds: 60, abandonmentGraceMs: 300000, paidTermsRevision: 1,
      certificateSigningKeyId: "test-key-1"}),
    log: () => {},
  });
  return {fake, terminal};
}

test("advancePlan: idle while the clock runs, terminalize on finish request", () => {
  const sitting = makeSitting();
  sitting.firstAckGapDeadlineMs = 1_700_000_000_000 + 60000;
  assert.deepEqual(advancePlan(sitting, 1_700_000_000_000 + 1000), {kind: "IDLE"});
  sitting.state = SITTING_STATE.FINISH_REQUESTED;
  assert.equal(advancePlan(sitting, 1_700_000_000_000 + 1000).kind, "TERMINALIZE");
});

test("expired question closes unanswered and opens the next window from the original deadline", () => {
  const sitting = makeSitting();
  const now = 1_700_000_000_000 + 120_000;
  // Истёкший вопрос закрывается неотвеченным, а цепочка окон продолжается: следующий слот
  // открывается со своим окном.
  const plan = advancePlan(sitting, now);
  assert.equal(plan.kind, "ADVANCE");
  assert.equal(plan.updates.slots[0].state, SLOT_STATE.EXPIRED);
  assert.equal(plan.updates.slots[1].windowDeadlineMs,
    sitting.firstAckGapDeadlineMs + 60 * 1000,
    "окно якорится к ИСХОДНОМУ дедлайну, не к сейчас worker'а");
  assert.equal(plan.updates.currentIndex, 1);
});

test("timeout chain: each expired slot closes with digit 1 until all twenty are done", () => {
  const sitting = makeSitting();
  let clock = 1_700_000_000_000;
  let terminalized = false;
  for (let round = 0; round < 60 && !terminalized; round += 1) {
    const plan = advancePlan(sitting, clock);
    if (plan.kind === "IDLE") {
      // Часы идут до ближайшего дедлайна: worker спит, задержка дедлайны не двигает.
      const current = sitting.slots[sitting.currentIndex];
      const bound = current.ack ? current.ack.answerDeadlineMs :
        current.index === 0 ? sitting.firstAckGapDeadlineMs : current.windowDeadlineMs;
      clock = bound + 1;
      continue;
    }
    if (plan.kind === "TERMINALIZE") { terminalized = true; break; }
    Object.assign(sitting, plan.updates);
  }
  assert.equal(terminalized, true, "цепочка таймаутов доходит до терминализации");
  assert.equal(sitting.slots.every((slot) => slot.state !== SLOT_STATE.DELIVERED), true);
  const digits = sitting.slots.map((slot) => slotDigit(slot));
  assert.equal(digits.every((digit) => digit === 1), true, "неотвеченное — цифра 1");
  const outcome = terminalOutcome(sitting, {closedAtMs: clock, closeReason: CLOSE_REASON.TIMEOUT,
    resultVersion: 1});
  assert.equal(outcome.percent, 0);
  assert.equal(outcome.passed, false);
  assert.equal(outcome.starsTenths, 0);
});

test("terminal theme commit: result, cooldown, first-pass award, one receipt, generation", async () => {
  const sitting = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), at < 12 ? "a" : "b"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  sitting.sittingId = "theme-sitting-1";
  const {assessmentStateId} = require("./exam-coverage");
  const stateId = assessmentStateId("player", "ThemeTest", "theme");
  const {fake, terminal} = terminalWorld({initialRows: {
    [`exam_sittings/theme-sitting-1`]: sitting,
  }});
  const result = await terminal.advanceSitting({sittingId: "theme-sitting-1"});
  assert.equal(result.status, "TERMINALIZED");
  // 12 из 20 верных → 60%: проход, 1.8 звезды.
  assert.equal(result.percent, 60);
  assert.equal(result.passed, true);
  const stored = fake.rows.get("exam_sittings/theme-sitting-1");
  assert.equal(stored.state, SITTING_STATE.CLOSED);
  assert.equal(stored.terminal.projection.starsTenths, 18);
  assert.equal(stored.terminal.projection.passed, true);
  assert.equal(stored.terminal.projection.percent, 60);
  const state = fake.rows.get(`exam_assessment_state/${stateId}`);
  assert.equal(state.passed, true);
  assert.equal(state.bestPercent, 60);
  assert.equal(state.cooldownUntilMs - stored.closedAtMs, 24 * 3600 * 1000, "кулдаун темы — 24 часа");
  assert.ok(state.passedAtGeneration >= 1, "поколение агрегата продвинуто проходом");
  // Первый проход: чек + единственный award-документ.
  const receipt = fake.rows.get("exam_reward_receipts/theme-sitting-1");
  assert.ok(receipt);
  assert.equal(receipt.firstPass, true);
  assert.equal(receipt.accountedPercentAfter, 60);
  const awards = [...fake.rows.keys()].filter((path) => path.startsWith("exam_first_pass_awards/"));
  assert.equal(awards.length, 1);
  const user = fake.rows.get("users/player");
  assert.ok(user.nolics > 0 && user.skillPoints > 0, "первый проход темы платит");
  const ledger = [...fake.rows.keys()].filter((path) => path.startsWith("audit/currency_ledger/"));
  assert.equal(ledger.length, 2, "нолики и очки навыка — по строке книги");
});

test("failed theme pays nothing and does not advance the accounted reference", async () => {
  const sitting = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), at < 5 ? "a" : "b"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  sitting.sittingId = "theme-sitting-fail";
  const {fake, terminal} = terminalWorld({initialRows: {
    "exam_sittings/theme-sitting-fail": sitting,
  }});
  const result = await terminal.advanceSitting({sittingId: "theme-sitting-fail"});
  assert.equal(result.status, "TERMINALIZED");
  assert.equal(result.passed, false);
  const receiptPath = "exam_reward_receipts/theme-sitting-fail";
  assert.equal(fake.rows.has(receiptPath), false, "неоплаченный провал темы не платит");
  const awards = [...fake.rows.keys()].filter((path) => path.startsWith("exam_first_pass_awards/"));
  assert.equal(awards.length, 0);
  const user = fake.rows.get("users/player");
  assert.equal(user.nolics, 0);
});

test("theme first pass pays exactly once; the retake never pays again", async () => {
  const sitting = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), "a"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  const {assessmentStateId} = require("./exam-coverage");
  const stateId = assessmentStateId("player", "ThemeTest", "theme");
  const first = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), "a"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  first.sittingId = "theme-first";
  const retake = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), "a"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  retake.sittingId = "theme-retake";
  const {fake, terminal} = terminalWorld({initialRows: {
    "exam_sittings/theme-first": first,
    "exam_sittings/theme-retake": retake,
  }});
  await terminal.advanceSitting({sittingId: "theme-first"});
  const userAfterFirst = {...fake.rows.get("users/player")};
  await terminal.advanceSitting({sittingId: "theme-retake"});
  const userAfterRetake = fake.rows.get("users/player");
  assert.equal(userAfterRetake.nolics, userAfterFirst.nolics, "ретема прохода не платит второй раз");
  const awards = [...fake.rows.keys()].filter((path) => path.startsWith("exam_first_pass_awards/"));
  assert.equal(awards.length, 1);
});

test("final commit pays every terminal attempt including failure; b=max(b,p) even at zero", async () => {
  const sitting = makeSitting({mode: "FinalExam", assessmentId: "course",
    answers: Object.fromEntries(Array.from({length: 20}, (_, at) => [String(at), at < 10 ? "a" : "b"])),
    state: SITTING_STATE.FINISH_REQUESTED});
  sitting.sittingId = "final-1";
  const {assessmentStateId} = require("./exam-coverage");
  const stateId = assessmentStateId("player", "FinalExam", "course");
  const {fake, terminal} = terminalWorld({initialRows: {
    "exam_sittings/final-1": sitting,
    [`exam_assessment_state/${stateId}`]: {maxAccountedPercent: 90, bestPercent: 90, passed: true},
  }});
  // 10 из 20 верных → p=50 < b=90: повторный прогресс 0.1, b остаётся 90.
  const result = await terminal.advanceSitting({sittingId: "final-1"});
  assert.equal(result.status, "TERMINALIZED");
  const receipt = fake.rows.get("exam_reward_receipts/final-1");
  assert.ok(receipt, "провалившийся финал всё равно прав на выплату");
  assert.equal(receipt.accountedPercentBefore, 90);
  assert.equal(receipt.accountedPercentAfter, 90);
  const state = fake.rows.get(`exam_assessment_state/${stateId}`);
  assert.equal(state.maxAccountedPercent, 90);
  // Ни факта выпуска, ни работы подписывания при провале.
  assert.equal([...fake.rows.keys()].some((path) => path.startsWith("exam_certificates/")), false);
});

test("successful terminal final commits result + cooldown + issuance fact + signing work atomically", async () => {
  const sitting = makeSitting({mode: "FinalExam", assessmentId: "course",
    answers: Object.fromEntries(Array.from({length: 20}, (_, at) => [String(at), "a"])),
    state: SITTING_STATE.FINISH_REQUESTED});
  sitting.sittingId = "final-pass";
  const {assessmentStateId} = require("./exam-coverage");
  const stateId = assessmentStateId("player", "FinalExam", "course");
  const {fake, terminal} = terminalWorld({initialRows: {
    "exam_sittings/final-pass": sitting,
  }});
  const result = await terminal.advanceSitting({sittingId: "final-pass"});
  assert.equal(result.status, "TERMINALIZED");
  assert.ok(result.issuanceId);
  const fact = fake.rows.get(`exam_certificates/${result.issuanceId}`);
  assert.equal(fact.percent, 100);
  assert.equal(fact.passMark, 60);
  assert.equal(fact.expiresAtMs - fact.issuedAtMs, 365 * 24 * 3600 * 1000, "ровно год действия");
  assert.equal(fact.signing.state, "PENDING", "подпись ожидает и не публикуется");
  const work = fake.rows.get(`exam_signing_work/${result.issuanceId}`);
  assert.equal(work.claimsDigest, fact.claimsDigest);
  const state = fake.rows.get(`exam_assessment_state/${stateId}`);
  assert.equal(state.passed, true);
  assert.equal(state.cooldownUntilMs - fact.issuedAtMs, 168 * 3600 * 1000, "финальный кулдаун — 168 часов");
  // Публичного снимка нет: неподписанная публикация невозможна.
  assert.equal(fake.rows.has(`exam_certificate_public/${result.issuanceId}`), false);
});

test("invalid frozen reward basis leaves actionable pending work, never a fake zero", async () => {
  // Проходящая тема: выплата обязана считаться, поэтому испорченная база яд.
  const sitting = makeSitting({state: SITTING_STATE.FINISH_REQUESTED, answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), "a"]))});
  sitting.sittingId = "poison-1";
  sitting.bank = {...THEME_BASIS, TH: 10, Q: 210};
  const {fake, terminal} = terminalWorld({initialRows: {"exam_sittings/poison-1": sitting}});
  const result = await terminal.advanceSitting({sittingId: "poison-1"});
  assert.equal(result.status, "POISON");
  const stored = fake.rows.get("exam_sittings/poison-1");
  assert.equal(stored.state, SITTING_STATE.FINISH_REQUESTED, "сидинг не закрыт фальшивым результатом");
  assert.equal(stored.terminalError, "INVALID_REWARD_BASIS");
});

test("worker sweep: lease, bounded page, poison isolation, and convergence", async () => {
  const pass = makeSitting({answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), "a"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  pass.sittingId = "sw-pass";
  // Яд — проходящая тема с испорченной базой: выплата не может посчитаться честно.
  // Своя тема, чтобы первый проход «sw-pass» не снимал с неё выплату.
  const poison = makeSitting({state: SITTING_STATE.FINISH_REQUESTED, assessmentId: "poison-theme",
    answers: Object.fromEntries(Array.from({length: 20}, (_, at) => [String(at), "a"]))});
  poison.sittingId = "sw-poison";
  poison.bank = {...THEME_BASIS, TE: 0};
  const healthy = makeSitting({assessmentId: "healthy-theme", answers: Object.fromEntries(
    Array.from({length: 20}, (_, at) => [String(at), at < 15 ? "a" : "b"])),
  state: SITTING_STATE.FINISH_REQUESTED});
  healthy.sittingId = "sw-healthy";
  const {fake, terminal} = terminalWorld({initialRows: {
    "exam_sittings/sw-pass": pass,
    "exam_sittings/sw-poison": poison,
    "exam_sittings/sw-healthy": healthy,
  }});
  const sweep = await terminal.sweep();
  assert.equal(sweep.attempted, 3);
  assert.equal(sweep.terminalized, 2, "ядовитая запись изолирована, остальная работа сделана");
  assert.equal(sweep.failed, 1);
  assert.equal(fake.rows.get("exam_sittings/sw-pass").state, SITTING_STATE.CLOSED);
  assert.equal(fake.rows.get("exam_sittings/sw-healthy").state, SITTING_STATE.CLOSED);
  assert.notEqual(fake.rows.get("exam_sittings/sw-poison").state, SITTING_STATE.CLOSED);
  // Аренда освобождена: второй обход возможен и уже не находит работы.
  const second = await terminal.sweep();
  assert.equal(second.attempted, 1, "только яд остался в очереди");
  // Терминированный сидинг снят с флага.
  assert.equal(fake.rows.get("exam_sittings/sw-pass").terminalQueryFlag, false);
  // Яд копится по документу: после лимита флаг снимается — ограниченная страница освобождается.
  assert.equal(fake.rows.get("exam_sittings/sw-poison").poisonAttempts, 2,
    "яд считался по документу за два обхода");
  assert.equal(second.attempted, 1, "только яд остался в очереди");
  await terminal.sweep();
  await terminal.sweep();
  const drained = fake.rows.get("exam_sittings/sw-poison");
  assert.equal(drained.poisonAttempts, POISON_ATTEMPT_LIMIT);
  assert.ok(drained.poisonAt, "пометка яда оставлена оператору");
  assert.equal(drained.terminalQueryFlag, false, "страница обхода освобождена");
  const drainedSweep = await terminal.sweep();
  assert.equal(drainedSweep.attempted, 0, "яд больше не занимает очередь");
});

test("overlapping sweeps serialize on the lease: one is BUSY", async () => {
  const sitting = makeSitting({state: SITTING_STATE.FINISH_REQUESTED});
  sitting.sittingId = "sw-busy";
  const {fake, terminal} = terminalWorld({initialRows: {"exam_sittings/sw-busy": sitting}});
  // Захватываем аренду вручную — второй sweep должен увидеть BUSY.
  await fake.db.runTransaction(async (tx) => {
    tx.set(fake.db.doc("exam_sitting_worker_state/sweep"), {lease: {ownerNonce: "other",
      acquiredAtMs: fake.clock.now, expiresAtMs: fake.clock.now + 660000}}, {merge: true});
  });
  const busy = await terminal.sweep();
  assert.equal(busy.status, "BUSY");
  assert.equal(busy.attempted, 0);
  assert.equal(fake.rows.get("exam_sittings/sw-busy").state, SITTING_STATE.FINISH_REQUESTED);
});

test("planReward: theme first pass only; final always eligible with b accounting", () => {
  const themePass = planReward({sitting: {mode: "ThemeTest", bank: THEME_BASIS}, percent: 60,
    passed: true, assessmentState: {}, hasFirstPassAward: false});
  assert.equal(themePass.pays, true);
  assert.equal(themePass.firstPass, true);
  assert.equal(themePass.bNext, 60);
  assert.equal(planReward({sitting: {mode: "ThemeTest", bank: THEME_BASIS}, percent: 60,
    passed: true, assessmentState: {}, hasFirstPassAward: true}).pays, false);
  assert.equal(planReward({sitting: {mode: "ThemeTest", bank: THEME_BASIS}, percent: 40,
    passed: false, assessmentState: {maxAccountedPercent: 0}, hasFirstPassAward: false}).bNext, 0,
    "неоплаченный провал темы не двигает учтённый максимум");
  const final = planReward({sitting: {mode: "FinalExam", bank: FINAL_BASIS}, percent: 95,
    passed: true, assessmentState: {maxAccountedPercent: 90}, hasFirstPassAward: false});
  assert.equal(final.bNext, 95);
  assert.equal(final.skillPoints, 543);
});

test("scheduler delay never extends deadlines: expiry anchor stays the original deadline", () => {
  const sitting = makeSitting();
  const originalDeadline = 1_700_000_000_000 + 60000;
  const lateWorker = originalDeadline + 500 * 1000;
  const updates = advanceExpiredSlot(sitting, 0, originalDeadline, lateWorker);
  assert.equal(updates.slots[1].windowDeadlineMs, originalDeadline + 60 * 1000,
    "окно следующего вопроса якорится к дедлайну, а не к опоздавшему сейчас");
});
