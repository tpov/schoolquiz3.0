"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb} = require("./_exam-test-db");
const {
  CORRECTION_STATE,
  correctedProjection,
  correctionRecord,
  correctionReplayKey,
  createExamCorrections,
  cumulativeExclusions,
  recomputeResult,
  safeNoScoreAdvice,
} = require("./exam-corrections");
const {EXAM_RULES_VERSION} = require("./exam-sitting");

const DIGITS_60 = [...new Array(12).fill(9), ...new Array(8).fill(1)];

/** Сидинг с замороженными исходными цифрами — как его хранит свидетельство. */
function sittingWithDigits(digits, {mode = "ThemeTest", passMark} = {}) {
  return {
    sittingId: "s1",
    uid: "player",
    mode,
    assessmentId: mode === "ThemeTest" ? "theme" : "course",
    state: "CLOSED",
    terminal: {resultVersion: 1},
    rules: {examRulesVersion: EXAM_RULES_VERSION,
      passMarkPercent: passMark === undefined ? 60 : passMark},
    slots: digits.map((digit, index) => ({
      index,
      scoreDigit: digit,
      state: digit === 1 ? "EXPIRED" : "ANSWERED",
    })),
  };
}

function correctionsWorld({initialRows = {}, nowMs = 1_700_000_000_000} = {}) {
  const fake = makeFakeDb({initialRows, nowMs});
  const corrections = createExamCorrections({
    db: fake.db, nowMs: () => fake.clock.now,
    authorizeExclusion: (record) => record && record.authorized === true,
  });
  return {fake, corrections};
}

function record(over = {}) {
  // Сырой вход: authorizeExclusion судит по нему, correctionRecord нормализует внутри.
  return {
    correctionId: "c1",
    uid: "player",
    mode: "ThemeTest",
    assessmentId: "theme",
    sittingId: "s1",
    slotIndex: 0,
    actorUid: "curator",
    reason: "confirmed faulty revision",
    reviewedAtMs: 5,
    authorized: true,
    ...over,
  };
}

test("recompute: exclusion of a correct answer can drop a pass, with the captured mark", () => {
  const sitting = sittingWithDigits(DIGITS_60);
  const before = recomputeResult({sitting, exclusions: []});
  assert.deepEqual({percent: before.percent, passed: before.passed, starsTenths: before.starsTenths},
    {percent: 60, passed: true, starsTenths: 18});
  const after = recomputeResult({sitting, exclusions: [0]});
  assert.deepEqual({percent: after.percent, passed: after.passed, starsTenths: after.starsTenths,
    denominator: after.denominator},
    {percent: 57, passed: false, starsTenths: 17, denominator: 19});
  // Другая пара исключений поднимает провал — но сертификата это не создаёт (проверка ниже).
  const raised = recomputeResult({sitting, exclusions: [11, 12]});
  assert.deepEqual({percent: raised.percent, passed: raised.passed},
    {percent: 61, passed: true});
});

test("recompute order independence and duplicate collapse", () => {
  const sitting = sittingWithDigits(DIGITS_60);
  assert.equal(recomputeResult({sitting, exclusions: [0, 5]}).percent,
    recomputeResult({sitting, exclusions: [5, 0]}).percent);
  assert.equal(recomputeResult({sitting, exclusions: [2, 2, 3]}).percent,
    recomputeResult({sitting, exclusions: [2, 3]}).percent);
});

test("unanswered stays in the denominator; all-excluded is numeric-absent", () => {
  const digits = [9, ...new Array(19).fill(1)];
  const sitting = sittingWithDigits(digits);
  const partial = recomputeResult({sitting, exclusions: Array.from({length: 18}, (_, at) => at + 2)});
  assert.deepEqual({denominator: partial.denominator, percent: partial.percent,
    passed: partial.passed, starsTenths: partial.starsTenths},
    {denominator: 2, percent: 50, passed: false, starsTenths: 15});
  const all = recomputeResult({sitting, exclusions: Array.from({length: 20}, (_, at) => at)});
  assert.equal(all.state, CORRECTION_STATE.UNSCORABLE_ALL_EXCLUDED);
  assert.equal(all.percent, null);
  assert.equal(all.passed, null);
  assert.equal(all.starsTenths, null);
  assert.equal(all.advice.length, 3, "совет фиксированной длины даже без числа");
});

test("missing or corrupt evidence is pending, never a shorter denominator", () => {
  const corrupt = sittingWithDigits([...DIGITS_60.slice(0, 19), 0]);
  const pending = recomputeResult({sitting: corrupt, exclusions: []});
  assert.deepEqual({state: pending.state, percent: pending.percent},
    {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE, percent: undefined});
  const noSlots = recomputeResult({sitting: {sittingId: "s", slots: []}, exclusions: []});
  assert.equal(noSlots.state, CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE);
  const noMark = recomputeResult({sitting: sittingWithDigits(DIGITS_60, {passMark: null}),
    exclusions: []});
  assert.equal(noMark.state, CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE);
  assert.equal(safeNoScoreAdvice("FinalExam").length, 3);
});

test("corrupted slot digits or wrong pass-mark shape stay pending (C2 evidence rules)", () => {
  const sitting = sittingWithDigits(DIGITS_60);
  sitting.rules.passMarkPercent = 60.5;
  const verdict = recomputeResult({sitting, exclusions: []});
  assert.equal(verdict.state, CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE);
});

test("appendExclusion: authorized append CASes the head; unauthorized refuses", async () => {
  const {fake, corrections} = correctionsWorld();
  const first = await corrections.appendExclusion({record: record()});
  assert.equal(first.applied, true);
  const head = fake.rows.get([...fake.rows.keys()].find((path) => path.startsWith("exam_correction_heads/")));
  console.error("HEAD_KEYS", JSON.stringify(head));
  assert.deepEqual(head.cumulativeExclusions, [0]);
  assert.equal(head.headVersion, 1);
  await assert.rejects(() => corrections.appendExclusion({record: record({correctionId: "c2",
    authorized: false})}), (error) => error.code === "NOT_AUTHORIZED");
  const second = await corrections.appendExclusion({record: record({correctionId: "c2", slotIndex: 5})});
  assert.equal(second.head.headVersion, 2);
  assert.deepEqual(second.head.cumulativeExclusions, [0, 5]);
  // Поколение темы помечено ожидающим: новый финал увидит барьер.
  const generation = fake.rows.get("exam_theme_generation/theme");
  assert.equal(generation.pendingCorrection, true);
});

test("identical correctionId replays the same effect; conflicting body is rejected", async () => {
  const {fake, corrections} = correctionsWorld();
  await corrections.appendExclusion({record: record()});
  const replay = await corrections.appendExclusion({record: record()});
  assert.deepEqual({applied: replay.applied, replay: replay.replay}, {applied: false, replay: true});
  const heads = [...fake.rows.keys()].filter((path) => path.startsWith("exam_correction_heads/"));
  assert.equal(fake.rows.get(heads[0]).headVersion, 1, "переигрывание голову не двигает");
  await assert.rejects(() => corrections.appendExclusion({record: record({slotIndex: 9})}),
    (error) => error.code === "CORRECTION_REUSE");
});

test("expectedHeadVersion: CAS conflict when the head moved", async () => {
  const {corrections} = correctionsWorld();
  await corrections.appendExclusion({record: record()});
  await assert.rejects(() => corrections.appendExclusion({record: record({correctionId: "c2",
    slotIndex: 4}), expectedHeadVersion: 0}),
  (error) => error.code === "HEAD_CONFLICT");
  const ok = await corrections.appendExclusion({record: record({correctionId: "c2", slotIndex: 4}),
    expectedHeadVersion: 1});
  assert.equal(ok.applied, true);
});

test("materialize applies the corrected admission/best projection in the same transaction", async () => {
  const sitting = sittingWithDigits(DIGITS_60);
  const {assessmentStateId} = require("./exam-coverage");
  const {fake, corrections} = correctionsWorld({
    initialRows: {
      "exam_sittings/s1": sitting,
      "exam_theme_generation/theme": {generation: 3, pendingCorrection: true},
      [`exam_assessment_state/${assessmentStateId("player", "ThemeTest", "theme")}`]: {
        uid: "player", mode: "ThemeTest", assessmentId: "theme", passed: true,
        bestPercent: 60, passedAtGeneration: 3, maxAccountedPercent: 60,
      },
    },
  });
  await corrections.appendExclusion({record: record()});
  const published = await corrections.materialize({uid: "player", mode: "ThemeTest",
    assessmentId: "theme"});
  assert.equal(published.published, true);
  assert.equal(published.outcome.passed, false, "исключение верной девятки роняет проход");
  assert.equal(published.projection.corrected, true);
  assert.equal(published.projection.originalResultVersion, 1);
  // Поколение продвинуто и очищено от ожидания.
  const generation = fake.rows.get("exam_theme_generation/theme");
  assert.equal(generation.pendingCorrection, false);
  assert.equal(generation.generation, 4);
  // Согласованная проекция допуска: пересборка rоняет проход и снимает метку поколения.
  const state = fake.rows.get(`exam_assessment_state/${assessmentStateId("player", "ThemeTest", "theme")}`);
  assert.equal(state.passed, false, "коррекция может снять проход");
  assert.equal(state.bestPercent, 57);
  assert.equal(state.passedAtGeneration, null, "устаревшая метка снята, не оставлена ожиданием");
  assert.equal(state.maxAccountedPercent, 60, "деньги и учтённый максимум не тронуты");
  // Повторная материализация без новой коррекции — устаревшая: публикаций не бывает дважды.
  const stale = await corrections.materialize({uid: "player", mode: "ThemeTest",
    assessmentId: "theme"});
  assert.deepEqual({published: stale.published, reason: stale.reason},
    {published: false, reason: "STALE_GENERATION"});
});

test("another independently valid pass survives the recompute with the new stamp", async () => {
  const sitting = sittingWithDigits(DIGITS_60);
  // Второй закрытый сидинг той же темы с самостоятельным проходом 80% (16 из 20 верных).
  const other = sittingWithDigits([...new Array(16).fill(9), ...new Array(4).fill(1)], {});
  other.sittingId = "s2";
  other.uid = "player";
  // Терминальный писатель всегда хранит проекцию — usable latest result второго сидинга.
  other.terminal = {resultVersion: 1, projection: {percent: 80, passed: true}};
  const {assessmentStateId} = require("./exam-coverage");
  const {fake, corrections} = correctionsWorld({
    initialRows: {
      "exam_sittings/s1": sitting,
      "exam_sittings/s2": other,
      "exam_theme_generation/theme": {generation: 3, pendingCorrection: true},
      [`exam_assessment_state/${assessmentStateId("player", "ThemeTest", "theme")}`]: {
        uid: "player", mode: "ThemeTest", assessmentId: "theme", passed: true,
        bestPercent: 80, passedAtGeneration: 3, maxAccountedPercent: 80,
      },
    },
  });
  await corrections.appendExclusion({record: record()});
  const published = await corrections.materialize({uid: "player", mode: "ThemeTest",
    assessmentId: "theme"});
  assert.equal(published.published, true);
  const state = fake.rows.get(`exam_assessment_state/${assessmentStateId("player", "ThemeTest", "theme")}`);
  assert.equal(state.passed, true, "другой валидный проход остаётся достаточным");
  assert.equal(state.bestPercent, 80);
  assert.equal(state.passedAtGeneration, 4, "проход перемаркирован опубликованным поколением");
});

test("a new terminal theme pass racing a correction serializes on the generation", async () => {
  const sitting = sittingWithDigits(DIGITS_60);
  const {fake, corrections} = correctionsWorld({
    initialRows: {
      "exam_sittings/s1": sitting,
      "exam_theme_generation/theme": {generation: 3, pendingCorrection: true},
    },
  });
  await corrections.appendExclusion({record: record()});
  // Пока коррекция ждала, терминальный писатель провёл новый валидный проход темы: поколение
  // продвинулось, ожидание коррекции осталось (писатель его не снимает).
  fake.rows.set("exam_theme_generation/theme", {generation: 4, pendingCorrection: true});
  const published = await corrections.materialize({uid: "player", mode: "ThemeTest",
    assessmentId: "theme"});
  // Материализатор публикует строго ПОСЛЕ гонки: поколение 5 — сериализация, не потеря.
  assert.equal(published.published, true);
  assert.equal(published.generation, 5);
  assert.equal(fake.rows.get("exam_theme_generation/theme").generation, 5);
  assert.equal(fake.rows.get("exam_theme_generation/theme").pendingCorrection, false);
});

test("corrected failed→passing projection creates no certificate entitlement marker", () => {
  const sitting = sittingWithDigits([...new Array(11).fill(9), ...new Array(9).fill(1)]);
  const outcome = recomputeResult({sitting, exclusions: [11, 12]});
  assert.equal(outcome.passed, true, "61% — исправленный проход");
  const projection = correctedProjection({sitting, resultVersion: 2, outcome, correctedAtMs: 9});
  assert.equal(projection.corrected, true);
  assert.equal(JSON.stringify(projection).includes("certificate"), false,
    "коррекция не создаёт порядка заработка для сертификата");
  assert.equal(projection.originalResultVersion, 1, "история сохранена рядом");
});

test("corrected first-ever theme pass keeps the one-award rule at the plan level", () => {
  // Условие выплаты: (a) чека первого прохода нет, (b) база — ИСХОДНАЯ замороженная, (c) b=0.
  // Механику платящего терминала проверяет exam-terminal; здесь — инвариант решения.
  const originalBank = {mode: "ThemeTest", TE: 180, TH: 360, Q: 1260, countEasy: 5, countHard: 15,
    sumEasy: 180, sumHard: 360, ruleVersion: 1};
  const correctedSitting = sittingWithDigits([...new Array(11).fill(9), ...new Array(9).fill(1)]);
  const outcome = recomputeResult({sitting: correctedSitting, exclusions: [11, 12]});
  assert.equal(outcome.passed, true);
  // Терминальный план платит при отсутствии чека: тот же planReward, исходная база, b=0.
  const {planReward} = require("./exam-terminal");
  const plan = planReward({sitting: {mode: "ThemeTest", bank: originalBank}, percent: outcome.percent,
    passed: true, assessmentState: {maxAccountedPercent: 0}, hasFirstPassAward: false});
  assert.equal(plan.pays, true);
  assert.equal(plan.firstPass, true);
  assert.equal(plan.W_b, 0, "b=0 до разовой награды");
  // Существующий чек — второй раз не платит.
  const again = planReward({sitting: {mode: "ThemeTest", bank: originalBank}, percent: outcome.percent,
    passed: true, assessmentState: {maxAccountedPercent: 60}, hasFirstPassAward: true});
  assert.equal(again.pays, false);
});

test("cumulative exclusions address original slots and validate range", () => {
  const records = [record(), record({correctionId: "c2", slotIndex: 7})];
  assert.deepEqual(cumulativeExclusions(records), [0, 7]);
  assert.throws(() => cumulativeExclusions([record({correctionId: "c3", slotIndex: 20})]),
    (error) => error.code === "CORRUPT_EXCLUSION");
  assert.throws(() => correctionRecord(record({correctionId: "c4", slotIndex: -1})),
    (error) => error.code === "INVALID_ARGUMENT");
});

test("replay key ignores execution-time bookkeeping but binds the decision body", () => {
  const base = record();
  const same = record({reviewedAtMs: 99});
  assert.equal(correctionReplayKey(same), correctionReplayKey(base),
    "поздняя метка пересмотра не делает тело другим");
  const different = record({slotIndex: 3});
  assert.notEqual(correctionReplayKey(different), correctionReplayKey(base));
});
