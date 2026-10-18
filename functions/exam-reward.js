"use strict";

const {TARIFF_BANDS} = require("./lesson-reward");

/**
 * Выплата C1 — режимные коэффициенты поверх существующего взвешенного предельного тарифа.
 *
 * Ничего нового арифметика не изобретает: полосы тарифа, размер и сложность — те же, что у
 * обычного урока (`lesson-reward.js`); заменён только коэффициент НОВОГО прогресса для
 * экзаменационного режима. Экзаменного множителя на всю попытку не существует.
 *
 * Таблица режимов (C1; протокол §Assessment rewards):
 *
 * | Режим      | c (новое) | r (уже оплаченное) | Право на выплату                      |
 * | ---------- | --------- | ------------------ | ------------------------------------- |
 * | Обычный    | 2         | 0.1                | существующие обычные правила          |
 * | Тема       | 3         | 0                  | один первый проход на пользователя/тему |
 * | Финал      | 10        | 0.1                | каждый терминальный сидинг, включая провал |
 *
 * Заморозка базы. `TE`/`TH` — ограниченные средние пула по ОБЫЧНОМУ отведённому времени каждого
 * вопроса (EASY 0.36, HARD 0.24), а не по экзаменационному all-HARD таймеру: пять EASY-позиций
 * темы сохраняют EASY-стоимость. Считаются по всему подходящему пулу (среднее × размер набора),
 * поэтому удачный или неудачный жребий не двигает цену попытки. `B=(TE+3*TH)/720`; Q = TE+3*TH.
 *
 * Целочисленная версия 1 (проверяемая, без переполнений): `A=10*c*max(W(p)-W(b),0)+(10*r)*W(min(p,b))`,
 * `N=A*Q`; XP округляется независимо от N/7200, нолики — независимо от N/72000, оба — половину
 * вверх. Нулевая после округления выплата всё равно двигает учтённый максимум до `max(b,p)`.
 *
 * Чистый модуль: тестируется без firebase-admin; EXAM-TECH-1 фиксирует его приборами.
 */

const REWARD_RULE_VERSION = 1;

/** Режимные коэффициенты: c — новое, r — уже оплаченное. Обычный урок здесь не живёт. */
const MODE_COEFFICIENTS = Object.freeze({
  ThemeTest: Object.freeze({c: 3, r: 0}),
  FinalExam: Object.freeze({c: 10, r: 0.1}),
});

/** Знаменатель обычного якоря: SMALL_LESSON_SECONDS = 720. */
const ORDINARY_ANCHOR_SECONDS = 720;

/**
 * Взвешенный тариф W(p) — та же лестница полос, что у обычной награды.
 *
 * Продублировано транскрипцией полос, а не импортом расчёта с множителями обычного режима:
 * {@link weightedPercent} из `lesson-reward.js` возвращает тот же итог, но здешняя версия обязана
 * оставаться целочисленной и не тянуть с собой NEW/REPEAT-множители, которые здесь заменяются.
 */
function weightedTariff(percent) {
  const p = Math.max(0, Math.min(Math.floor(Number(percent) || 0), 100));
  let total = 0;
  for (const [from, to, multiplier] of TARIFF_BANDS) {
    total += Math.max(0, Math.min(p, to) - from) * multiplier;
  }
  return total;
}

/** Половина вверх для неотрицательных целых: floor((2n+d)/(2d)). */
function roundHalfUpRatio(numerator, denominator) {
  if (!Number.isSafeInteger(numerator) || numerator < 0) {
    throw new TypeError(`numerator must be a non-negative safe integer: ${numerator}`);
  }
  if (!Number.isSafeInteger(denominator) || denominator <= 0) {
    throw new TypeError(`denominator must be a positive safe integer: ${denominator}`);
  }
  return Math.floor((2 * numerator + denominator) / (2 * denominator));
}

/**
 * Ограниченное среднее пула: roundHalfUp(draw * sum / count).
 *
 * `sum` — сумма обычных отведённых секунд по ВСЕМУ подходящему пулу этой сложности, `count` —
 * число вопросов в нём; `draw` — размер набора (5/15/20). Пул меньше набора не доходит до
 * расчёта: границу набора проверяет банк раньше цены.
 */
function cappedPoolMean({draw, sum, count}) {
  if (!Number.isSafeInteger(draw) || draw <= 0) throw new TypeError(`draw must be positive: ${draw}`);
  if (!Number.isSafeInteger(sum) || sum < 0) throw new TypeError(`sum must be non-negative: ${sum}`);
  if (!Number.isSafeInteger(count) || count <= 0) throw new TypeError(`count must be positive: ${count}`);
  return roundHalfUpRatio(draw * sum, count);
}

/**
 * Основа выплаты из замороженных сумм времени пула.
 *
 * Тема: TE по 5 EASY-вопросам (обычное 0.36), TH по 15 HARD (обычное 0.24). Финал: TE=0, TH по
 * 20 HARD. Каждый вопрос пула обязан нести свой обычный минимум отведённого времени — EASY 25,
 * HARD 75 секунд, — и ограниченное среднее обязано его сохранить: тема TE>=25 и TH>=75, финал
 * TE=0 и TH>=100. Испорченная основа — отказ, а не правдоподобный ноль.
 *
 * @param params {mode, poolEasySeconds[], poolHardSeconds[]}
 * @returns {{mode, TE, TH, Q, countEasy, countHard, sumEasy, sumHard, ruleVersion}}
 */
function buildWorthBasis({mode, poolEasySeconds, poolHardSeconds}) {
  const easy = (Array.isArray(poolEasySeconds) ? poolEasySeconds : []).map((value) => {
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new TypeError(`pool easy seconds must be positive integers: ${value}`);
    }
    return value;
  });
  const hard = (Array.isArray(poolHardSeconds) ? poolHardSeconds : []).map((value) => {
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new TypeError(`pool hard seconds must be positive integers: ${value}`);
    }
    return value;
  });
  if (mode === "ThemeTest") {
    if (easy.length === 0 || hard.length === 0) {
      throw new TypeError("theme basis requires both pool difficulties");
    }
    const TE = cappedPoolMean({draw: 5, sum: easy.reduce((a, b) => a + b, 0), count: easy.length});
    const TH = cappedPoolMean({draw: 15, sum: hard.reduce((a, b) => a + b, 0), count: hard.length});
    return finalizeBasis({mode, TE, TH, countEasy: easy.length, countHard: hard.length,
      sumEasy: easy.reduce((a, b) => a + b, 0), sumHard: hard.reduce((a, b) => a + b, 0)});
  }
  if (mode === "FinalExam") {
    if (easy.length !== 0) throw new TypeError("final basis has no easy worth");
    if (hard.length === 0) throw new TypeError("final basis requires the hard pool");
    const TH = cappedPoolMean({draw: 20, sum: hard.reduce((a, b) => a + b, 0), count: hard.length});
    return finalizeBasis({mode, TE: 0, TH, countEasy: 0, countHard: hard.length,
      sumEasy: 0, sumHard: hard.reduce((a, b) => a + b, 0)});
  }
  throw new TypeError(`unknown assessment mode: ${mode}`);
}

function finalizeBasis(basis) {
  const {mode, TE, TH} = basis;
  const perQuestionMinimum = mode === "ThemeTest" ? {easy: 25, hard: 75} : {easy: 0, hard: 100};
  if (mode === "FinalExam" && TE !== 0) throw new TypeError("final basis requires TE=0");
  if (TE < perQuestionMinimum.easy || TH < perQuestionMinimum.hard) {
    throw new TypeError(`INVALID_REWARD_BASIS: ${mode} TE=${TE} TH=${TH}` +
      ` violates per-question minimums EASY>=${perQuestionMinimum.easy} HARD>=${perQuestionMinimum.hard}`);
  }
  return {...basis, Q: TE + 3 * TH, ruleVersion: REWARD_RULE_VERSION};
}

/**
 * Повторно проверяет замороженную основу перед выплатой — форму и минимумы.
 *
 * Сидинг хранит основу с момента старта; выплата обязана убедиться, что сохранилась пригодная:
 * испорченная основа останавливает расчёт явной ошибкой и никогда не доходит до калькулятора как
 * правдоподобная сумма.
 */
function validateFrozenBasis(basis) {
  if (!basis || typeof basis !== "object") throw new TypeError("frozen basis missing");
  if (basis.ruleVersion !== REWARD_RULE_VERSION) {
    throw new TypeError(`frozen basis rule version ${basis.ruleVersion} is not ${REWARD_RULE_VERSION}`);
  }
  const rechecked = finalizeBasis({
    mode: basis.mode,
    TE: basis.TE,
    TH: basis.TH,
    countEasy: basis.countEasy,
    countHard: basis.countHard,
    sumEasy: basis.sumEasy,
    sumHard: basis.sumHard,
  });
  if (rechecked.Q !== basis.Q) throw new TypeError("frozen basis Q disagrees with TE/TH");
  return basis;
}

/**
 * Синтетическая основа приборов: форма и согласованность — да, минимумы пула — нет.
 *
 * EXAM-TECH-1 содержит один `arithmeticOnly` прибор нарочно крошечной основой (TH=1), недостижимой
 * для настоящего пула из двадцати вопросов: он проверяет шов округления и учёта, а не цену. Такая
 * основа помечена в приборе как синтетическая, и этот конструктор — единственная lawful дорога
 * провести её в калькулятор: продакшн-выплата зовёт {@link validateFrozenBasis} и синтетическую
 * пометку не принимает никогда.
 */
function syntheticFixtureBasis(basis) {
  if (!basis || typeof basis !== "object") throw new TypeError("frozen basis missing");
  if (basis.ruleVersion !== REWARD_RULE_VERSION) {
    throw new TypeError(`frozen basis rule version ${basis.ruleVersion} is not ${REWARD_RULE_VERSION}`);
  }
  const Q = basis.TE + 3 * basis.TH;
  if (basis.Q !== Q) throw new TypeError("frozen basis Q disagrees with TE/TH");
  return basis;
}

/**
 * Выплата C1 по сидингу.
 *
 * @param params {mode, percent, accountedPercent, basis}
 *   `percent` — авторитетный агрегатный процент p (0..100, целое);
 *   `accountedPercent` — durable максимум уже оплаченного прогресса b для этой пары
 *   пользователь/оценка (0, если выплаты ещё не было);
 *   `basis` — заморозенная основа из {@link buildWorthBasis}.
 * @returns {{nolics, skillPoints, bNext, unroundedN, W_p, W_b, c, r, mode, ruleVersion}}
 *   Обе суммы округлены независимо от одного и того же нераскруглённого N; `bNext` — новый
 *   учтённый максимум: нулевая выплата всё равно двигает его до max(b,p).
 */
function assessmentReward({mode, percent, accountedPercent, basis, syntheticBasis}) {
  const coefficients = MODE_COEFFICIENTS[mode];
  if (!coefficients) throw new TypeError(`unknown assessment mode: ${mode}`);
  const p = Math.floor(Number(percent));
  const b = Math.floor(Number(accountedPercent) || 0);
  if (!Number.isInteger(p) || p < 0 || p > 100) throw new TypeError(`percent out of range: ${percent}`);
  if (!Number.isInteger(b) || b < 0 || b > 100) {
    throw new TypeError(`accounted percent out of range: ${accountedPercent}`);
  }
  if (syntheticBasis === true) syntheticFixtureBasis(basis);
  else validateFrozenBasis(basis);
  const W_p = weightedTariff(p);
  const W_b = weightedTariff(b);
  // A всегда целый: у r одна значащая цифра после запятой, поэтому 10*r — целое.
  const newPoints = Math.max(0, W_p - W_b);
  const repeatedPoints = Math.min(p, b) === b ? W_b : W_p;
  const A = 10 * coefficients.c * newPoints + Math.round(10 * coefficients.r) * repeatedPoints;
  const N = A * basis.Q;
  // У r одна цифра после запятой, и W целое, поэтому повторная часть в A точна. Страховка от
  // дрейфа: пересчёт P из A обязан сходиться с прямым расчётом.
  const P = A / 10;
  const directP = coefficients.c * newPoints + coefficients.r * repeatedPoints;
  if (Math.abs(P - directP) > 1e-9) throw new TypeError("coefficient placement drifted from C1");
  return {
    mode,
    ruleVersion: REWARD_RULE_VERSION,
    c: coefficients.c,
    r: coefficients.r,
    W_p,
    W_b,
    newPoints,
    repeatedPoints,
    unroundedN: N,
    nolics: roundHalfUpRatio(N, 72000),
    skillPoints: roundHalfUpRatio(N, 7200),
    bNext: Math.max(b, p),
  };
}

module.exports = {
  MODE_COEFFICIENTS,
  ORDINARY_ANCHOR_SECONDS,
  REWARD_RULE_VERSION,
  assessmentReward,
  buildWorthBasis,
  cappedPoolMean,
  roundHalfUpRatio,
  syntheticFixtureBasis,
  validateFrozenBasis,
  weightedTariff,
};
