"use strict";

const {evaluateAnswer, computePercentScore} = require("./assessment-scoring");

/**
 * Экзаменационная агрегатная оценка — серверная половина экзамена.
 *
 * Обычные прохождения иногда считает устройство; экзамен считает только сервер. Каждый слот
 * сидинга прогоняется через существующий общий скорер (`assessment-scoring.js`, зеркала Kotlin
 * `Scoring.kt`), агрегат собирается здесь — и ни один ответ, проекция или диагностика не отдаёт
 * наружу ни ключа, ни правильного порядка, ни повопросного вердикта.
 *
 * Три правила, ради которых файл существует (протокол §Redaction/scoring/result, C2):
 *
 * - **Процент — через общий скорер, с вложенным усечением.** Неотвеченный и просроченный слот —
 *   цифра 1 (обычный нижний балл): он остаётся в знаменателе, как нулевой вклад. При коррекции
 *   исключённый слот уходит и из числителя, и из знаменателя — знаменатель никогда не
 *   восстанавливается по списку ответов.
 * - **Звёзды темы линейны** — `floor((30*p + 50)/100)` десятых: 0/50/100% дают 0/1.5/3 звезды,
 *   15% даёт 0.5. Обычные звёзды уроков (`computeStars`) это не задевает; проходной балл
 *   сравнивается с сырым процентом, а не с округлёнными звёздами.
 * - **Совет безопасен и фиксированной длины.** Даже при идеальном счёте список той же длины;
 *   урок, давший меньше порога поддержки, не называется никогда — провалившиеся вопросы не
 *   раскрываются даже косвенно.
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

const ANSWER_MS = 1000;

/** Правило ответного времени экзамена: ОБЫЧНОЕ HARD-правило для каждого вопроса, включая пять EASY-позиций темы. */
const TIMER_K_HARD = 0.24;
const TIMER_K_EASY_ORDINARY = 0.36;
const IMAGE_CHARS = 100;
const MIN_QUESTION_SECONDS = 5;

/**
 * Серверный проходной балл по умолчанию.
 *
 * Захватывается в сидинг при старте; коррекция сравнивает процент именно с захваченным, а не с
 * сегодняшним значением ручки.
 */
const DEFAULT_PASS_MARK_PERCENT = 60;

/** Ровно столько слотов у темы (5 EASY + 15 HARD) и у финала (20 HARD). */
const THEME_DRAW_EASY = 5;
const THEME_DRAW_HARD = 15;
const FINAL_DRAW_HARD = 20;
const SITTING_SIZE = 20;

/** Фиксированная длина совета результата — даже при идеальном счёте. */
const ADVICE_LENGTH = 3;

/**
 * Ответные секунды экзаменационного вопроса.
 *
 * ВСЕГДА hard-коэффициент, включая пять EASY-позиций темы: секунд = max(5, roundHalfUp(символов ×
 * 0.24)); картинка стоит 100 символов. Никакого examFactor 0.75 не существует. Аргумент `isHard`
 * в подписи стоит намеренно и игнорируется намеренно: место вызова обязано назвать сложность
 * показанной позиции и тем самым доказать, что на коэффициент она не влияет.
 *
 * Обычный таймер и обычная награда продолжают считать по своим правилам
 * (`questionAllocatedSeconds`); это правило — только для экзаменационных сидингов, и сервер
 * замораживает каждую величину в сидинге.
 */
function examAnswerSeconds(content, isHard) {
  if (typeof isHard !== "boolean") {
    throw new TypeError("examAnswerSeconds requires the shown difficulty as a boolean");
  }
  return answerSecondsForChars(charsCount(content));
}

function answerSecondsForChars(chars) {
  return Math.max(MIN_QUESTION_SECONDS, Math.round(chars * TIMER_K_HARD));
}

/** Обычный подсчёт символов: текст вопроса плюс каждый вариант/элемент/кандидат, картинка — 100. */
function charsCount(content) {
  if (!content || typeof content !== "object") return 0;
  const text = String(content.text || "").length;
  const image = content.imageUrl ? IMAGE_CHARS : 0;
  let parts = 0;
  for (const list of [content.options, content.items, content.candidates]) {
    if (!Array.isArray(list)) continue;
    for (const entry of list) parts += String((entry && entry.text) || "").length;
  }
  return text + image + parts;
}

/**
 * Оценка одного слота сидинга.
 *
 * Ответ отсутствует (`null`), опоздал или не распознан — цифра 1, как того требует протокол:
 * таймаут не является оплаченным пропуском и не превращается в верный ответ. Каноническое
 * содержимое и канонический ответ приходят из приватного свидетельства сидинга; красацированный
 * публичный payload сюда не попадает никогда.
 */
function scoreSlot(canonicalContent, submittedAnswer) {
  if (!submittedAnswer) return 1;
  return evaluateAnswer(canonicalContent, submittedAnswer);
}

/**
 * Цифровой код сидинга из повопросных цифр.
 *
 * `digits` — ровно по слотам, каждый 1..9 (0 означает «не ставился» и в экзамене не возникает:
 * сидинг ставит все двадцать). Код — тот же формат, что читает `computePercentScore`.
 */
function codeAnswerFromDigits(digits) {
  const list = Array.isArray(digits) ? digits : [];
  if (list.length !== SITTING_SIZE) {
    throw new TypeError(`exam sitting scores exactly ${SITTING_SIZE} slots, got ${list.length}`);
  }
  for (const digit of list) {
    if (!Number.isInteger(digit) || digit < 1 || digit > 9) {
      throw new TypeError(`slot digit out of range 1..9: ${digit}`);
    }
  }
  return list.join("");
}

/**
 * Агрегатный процент по итоговым цифрам всех слотов.
 *
 * Тот же вложенное-усечённый расчёт, что и общий скорер: сумма `floor(100*(d-1)/8)` по
 * неотвеченным-как-единицы, делённая на число слотов. Здесь знаменатель — все 20 слотов; исключения
 * коррекции считаются отдельно ({@link correctedPercent}).
 */
function sittingPercent(digits) {
  return computePercentScore(codeAnswerFromDigits(digits));
}

/**
 * Проценты C2: из исходных цифр минус накопленные исключения.
 *
 * Исключение — индекс ИСХОДНОГО слота; исключённая позиция уходит и из числителя, и из
 * знаменателя. Знаменатель никогда не выводится из списка ответов и не сокращается до нуля
 * молча: ноль — отдельное состояние `UNSCORABLE_ALL_EXCLUDED`. Порядок исключений не важен:
 * дубликаты схлопываются.
 *
 * @returns {{percent: number|null, denominator: number, state: "SCORED"|"UNSCORABLE_ALL_EXCLUDED"}}
 */
function correctedPercent(digits, excludeIndexes) {
  const list = codeAnswerFromDigits(digits);
  const excluded = new Set((Array.isArray(excludeIndexes) ? excludeIndexes : []).map((index) => {
    if (!Number.isInteger(index) || index < 0 || index >= SITTING_SIZE) {
      throw new TypeError(`exclusion index out of range: ${index}`);
    }
    return index;
  }));
  let numerator = 0;
  let denominator = 0;
  for (let index = 0; index < list.length; index += 1) {
    if (excluded.has(index)) continue;
    const digit = Number(list[index]);
    numerator += Math.trunc(((digit - 1) * 100) / 8);
    denominator += 1;
  }
  if (denominator === 0) return {percent: null, denominator: 0, state: "UNSCORABLE_ALL_EXCLUDED"};
  return {percent: Math.trunc(numerator / denominator), denominator, state: "SCORED"};
}

/**
 * Линейные звёзды темы в десятых: floor((30·p + 50)/100).
 *
 * 0/50/100% → 0/15/30; 15% → 5. Проход определяется по сырому проценту и захваченному баллу,
 * звёзды — только отображение.
 */
function themeStarsTenths(percent) {
  const p = Math.floor(Number(percent));
  if (!Number.isInteger(p) || p < 0 || p > 100) {
    throw new TypeError(`percent out of range 0..100: ${percent}`);
  }
  return Math.floor((30 * p + 50) / 100);
}

/** Прошёл ли процент захваченный проходной балл (сырой процент, не звёзды). */
function passedAgainst(percent, capturedPassMark) {
  const p = Math.floor(Number(percent));
  const mark = Math.floor(Number(capturedPassMark));
  if (!Number.isInteger(p) || p < 0 || p > 100) throw new TypeError(`percent out of range: ${percent}`);
  if (!Number.isInteger(mark) || mark < 0 || mark > 100) {
    throw new TypeError(`pass mark out of range: ${capturedPassMark}`);
  }
  return p >= mark;
}

/**
 * Безопасный совет результата: фиксированной длины, без имён уроков и без повопросных данных.
 *
 * Совет выводится ТОЛЬКО из агрегатного процента — поэтому провалившийся вопрос невозможно
 * назвать даже косвенно, а идеальный счёт получает список той же длины. Провалившаяся тема
 * маршрутизирует обратно в уроки, и совет об этом говорит, не показывая, что именно сдалось.
 */
function safeAdvice({percent, passed, mode}) {
  const p = Math.floor(Number(percent));
  if (!Number.isInteger(p) || p < 0 || p > 100) throw new TypeError(`percent out of range: ${percent}`);
  const band = p >= 90 ? "MASTERED" : p >= 60 ? "SOLID" : p >= 30 ? "DEVELOPING" : "STARTING";
  const route = passed ? (mode === "FinalExam" ? "CERTIFICATE" : "THEME_PASSED") : "BACK_TO_LESSONS";
  // Три позиции всегда: повторная коррекция или другой счёт не меняют форму списка.
  return [band, route, mode === "FinalExam" ? "FINAL_RESULT" : "THEME_RESULT"];
}

/**
 * Проекция результата сидинга — единственное, что видит клиент.
 *
 * Здесь нет ни цифр, ни ключей, ни вердиктов по слотам: только идентичность, версия результата,
 * процент, прохождение, звёзды, совет, время закрытия и доступность следующей попытки.
 *
 * @param params {sittingId, mode, assessmentId, resultVersion, percent, passed, starsTenths,
 *   advice, closedAtMs, nextAvailableAtMs|null}
 */
function resultProjection(params) {
  const advice = Array.isArray(params.advice) ? params.advice : [];
  if (advice.length !== ADVICE_LENGTH) {
    throw new TypeError(`advice must be fixed length ${ADVICE_LENGTH}`);
  }
  return {
    schemaVersion: 1,
    sittingId: params.sittingId,
    mode: params.mode,
    assessmentId: params.assessmentId,
    resultVersion: params.resultVersion,
    percent: params.percent,
    passed: params.passed,
    starsTenths: params.starsTenths,
    advice,
    closedAtMs: params.closedAtMs,
    nextAvailableAtMs: params.nextAvailableAtMs === undefined ? null : params.nextAvailableAtMs,
  };
}

module.exports = {
  ANSWER_MS,
  ADVICE_LENGTH,
  DEFAULT_PASS_MARK_PERCENT,
  FINAL_DRAW_HARD,
  SITTING_SIZE,
  THEME_DRAW_EASY,
  THEME_DRAW_HARD,
  TIMER_K_EASY_ORDINARY,
  TIMER_K_HARD,
  IMAGE_CHARS,
  MIN_QUESTION_SECONDS,
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
};
