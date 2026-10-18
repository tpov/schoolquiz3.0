"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  ANSWER_MS,
  ADVICE_LENGTH,
  DEFAULT_PASS_MARK_PERCENT,
  SITTING_SIZE,
  answerSecondsForChars,
  charsCount,
  codeAnswerFromDigits,
  correctedPercent,
  examAnswerSeconds,
  passedAgainst,
  resultProjection,
  safeAdvice,
  sittingPercent,
  scoreSlot,
  themeStarsTenths,
} = require("./exam-scoring");

const CONTENT = (over) => ({type: "SingleChoice", text: "q", options: [{id: "a", text: "x"}],
  correctOptionId: "a", ...over});

test("the exam answer timer is the ordinary HARD rule for every question, EASY included", () => {
  // Протокол: 100 символов — 24 секунды, 10 символов — пол 5 секунд; никакого 0.75.
  assert.equal(answerSecondsForChars(100), 24);
  assert.equal(answerSecondsForChars(10), 5);
  assert.equal(answerSecondsForChars(1000), 240);
  assert.equal(answerSecondsForChars(0), 5);
});

test("the shown difficulty never changes the exam allowance", () => {
  const question = CONTENT({text: "x".repeat(100)});
  const hard = examAnswerSeconds(question, true);
  const easy = examAnswerSeconds(question, false);
  assert.equal(hard, 24);
  assert.equal(easy, 24, "пять EASY-позиций темы отвечают по обычному HARD-правилу");
  assert.throws(() => examAnswerSeconds(question), TypeError);
});

test("the exam rule carries no 0.75 exam factor anywhere", () => {
  // 100 символов по обычному EASY-правилу дали бы 36; 0.75 от HARD дал бы 18.
  assert.notEqual(answerSecondsForChars(100), 18);
  assert.notEqual(answerSecondsForChars(100), 36);
  assert.equal(answerSecondsForChars(100), 24);
});

test("character count mirrors the ordinary convention, image is 100", () => {
  assert.equal(charsCount(CONTENT({text: "abcd", options: [{id: "a", text: "ef"}]})), 6);
  assert.equal(charsCount(CONTENT({text: "ab", imageUrl: "http://x"})), 103,
    "текст 2 + картинка 100 + вариант 1");
  assert.equal(charsCount(null), 0);
});

test("scoreSlot floors missing answers at digit 1 and scores served ones via the shared scorer", () => {
  const content = CONTENT();
  assert.equal(scoreSlot(content, null), 1);
  assert.equal(scoreSlot(content, {type: "single-choice", selected: "a"}), 9);
  assert.equal(scoreSlot(content, {type: "single-choice", selected: "b"}), 1);
});

test("codeAnswer and percent follow the shared nested truncation", () => {
  assert.equal(codeAnswerFromDigits(new Array(20).fill(9)), "9".repeat(20));
  assert.throws(() => codeAnswerFromDigits(new Array(19).fill(9)), TypeError);
  assert.throws(() => codeAnswerFromDigits(new Array(20).fill(0)), TypeError);
  assert.equal(sittingPercent(new Array(20).fill(9)), 100);
  // 12 девяток + 8 единиц = 60, как в приборах коррекции.
  const digits = [...new Array(12).fill(9), ...new Array(8).fill(1)];
  assert.equal(sittingPercent(digits), 60);
});

test("correctedPercent drops excluded slots from numerator and denominator", () => {
  const digits = [...new Array(12).fill(9), ...new Array(8).fill(1)];
  assert.deepEqual(correctedPercent(digits, []), {percent: 60, denominator: 20, state: "SCORED"});
  assert.deepEqual(correctedPercent(digits, [0]), {percent: 57, denominator: 19, state: "SCORED"});
  // Порядок исключений независим, дубликаты схлопываются: минус две девятки — 55.
  assert.equal(correctedPercent(digits, [0, 0]).percent, 57);
  assert.equal(correctedPercent(digits, [0, 5]).percent, 55);
  assert.equal(correctedPercent(digits, [5, 0]).percent, 55);
  assert.deepEqual(correctedPercent(digits, digits.map((_, at) => at)),
    {percent: null, denominator: 0, state: "UNSCORABLE_ALL_EXCLUDED"});
  assert.throws(() => correctedPercent(digits, [20]), TypeError);
  assert.throws(() => correctedPercent(digits, [-1]), TypeError);
});

test("theme stars are linear tenths with half-up endpoints", () => {
  assert.equal(themeStarsTenths(0), 0);
  assert.equal(themeStarsTenths(50), 15);
  assert.equal(themeStarsTenths(100), 30);
  assert.equal(themeStarsTenths(15), 5, "15% → 0.5 звезды");
  // При захваченных 60%: 59% и 60% показывают 1.8 звезды, но проходит только 60%.
  assert.equal(themeStarsTenths(59), 18);
  assert.equal(themeStarsTenths(60), 18);
  assert.throws(() => themeStarsTenths(101), TypeError);
  assert.throws(() => themeStarsTenths(-1), TypeError);
});

test("pass decision uses the raw percent against the captured mark", () => {
  assert.equal(passedAgainst(60, 60), true);
  assert.equal(passedAgainst(59, 60), false);
  assert.equal(passedAgainst(57, 55), true, "захваченный балл, не сегодняшний");
});

test("safe advice is fixed length, band-only, and never names lessons or items", () => {
  for (const percent of [0, 15, 59, 60, 100]) {
    for (const passed of [true, false]) {
      const advice = safeAdvice({percent, passed, mode: "ThemeTest"});
      assert.equal(advice.length, ADVICE_LENGTH);
      assert.equal(Object.freeze(advice).length, ADVICE_LENGTH);
      for (const entry of advice) {
        assert.match(entry, /^[A-Z_]+$/, "совет — только безопасные коды");
        assert.equal(entry.startsWith("lesson"), false);
      }
    }
  }
  assert.deepEqual(safeAdvice({percent: 100, passed: true, mode: "ThemeTest"}),
    safeAdvice({percent: 100, passed: true, mode: "ThemeTest"}));
});

test("result projection carries identity, server values and next availability, never digits", () => {
  const projection = resultProjection({
    sittingId: "s1", mode: "ThemeTest", assessmentId: "theme", resultVersion: 1,
    percent: 60, passed: true, starsTenths: 18,
    advice: safeAdvice({percent: 60, passed: true, mode: "ThemeTest"}),
    closedAtMs: 1000, nextAvailableAtMs: 1000 + 24 * 3600 * 1000,
  });
  const json = JSON.stringify(projection);
  assert.equal(json.includes("codeAnswer"), false);
  assert.equal(json.includes("key"), false);
  assert.equal(projection.nextAvailableAtMs, 1000 + 24 * 3600 * 1000);
  assert.equal(projection.percent, 60);
  assert.throws(() => resultProjection({sittingId: "s", mode: "ThemeTest", assessmentId: "t",
    resultVersion: 1, percent: 60, passed: true, starsTenths: 18, advice: ["a", "b"],
    closedAtMs: 1}), TypeError);
});

test("module defaults match the protocol table", () => {
  assert.equal(DEFAULT_PASS_MARK_PERCENT, 60);
  assert.equal(SITTING_SIZE, 20);
  assert.equal(ANSWER_MS, 1000);
});
