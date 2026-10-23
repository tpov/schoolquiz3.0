"use strict";

/**
 * Контракт попытки приватного квиза (AD-29/AD-31, эпик 8, истории 8.4/8.5) — чистый модуль.
 *
 * Гейт (`private-quiz-gate.js`) — единственная дверь: он тратит лимит и замораживает
 * идентичность квеста в записи старта. Всё, что после двери, живёт здесь: срок попытки,
 * приём подачи, терминал по тайм-ауту и две проекции результата (участнику — по ТЕКУЩЕЙ
 * настройке showResult, автору — всегда).
 *
 * Чего здесь нет и не должно появиться (8.5 — негативные тесты держат):
 *  - никаких списаний: ни STANDARD/PLASMA за вход, ни подсказок, ни платных пропусков; ни одной
 *    строки в `audit/currency_ledger/entries` от приватного прохода;
 *  - никакого отложенного продолжения: окно восстановления техническое и живёт в исходных
 *    дедлайнах; когда оно истекает, попытка завершается НАВСЕГДА с причиной timeout — это
 *    терминальный результат, а не resumable-состояние;
 *  - никакого pass/fail порога и награды: процент — факт, а не оценка.
 *
 * Скоринг — существующий `scoreAttempt`, никакого нового скорера (AD-2): приватный квиз —
 * плоский HARD-набор, его вопросы хранятся исходными payload'ами (с ключами) в приватном
 * дереве автора, и `scoreAttempt` умеет такое читать сам (ветка «payload несёт свой ключ»).
 */

const {scoreAttempt} = require("./attempt-scoring");
const {questionAllocatedSeconds} = require("./lesson-reward");

/** Общее серверное окно загрузки следующего вопроса (AD-31), секунд. */
const NEXT_QUESTION_WINDOW_SECONDS = 60;

/** Причины терминала. Тайм-аут — терминальный результат, попытку продолжить нельзя. */
const ATTEMPT_REASON = {
  FINISHED: "finished",
  TIMEOUT: "timeout",
};

/** Статусы попытки, как их читают обе проекции. */
const ATTEMPT_STATUS = {
  RUNNING: "running",
  TERMINAL: "terminal",
};

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function parseObject(value) {
  if (typeof value === "string") {
    try {
      value = JSON.parse(value);
    } catch (_) {
      return null;
    }
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  return value;
}

/**
 * Ответное время одного вопроса приватного квиза — обычное HARD-правило (UX-16).
 *
 * Приватный формат — плоский HARD-набор: коэффициент и минимум те же, что у HARD-попытки
 * обычного урока (`questionAllocatedSeconds(content, true)`), без экзаменационных коэффициентов
 * и без pass-fail. Часы вопроса начинаются по ONLINE-O2 (клиентский прогон), серверу нужен
 * суммарный бюджет попытки — он ниже.
 */
function privateQuestionSecondsMs(content) {
  return questionAllocatedSeconds(content, true) * 1000;
}

/**
 * Срок попытки от исходного старта: сумма HARD-времён всех вопросов замороженного набора плюс
 * окно AD-31 после каждого вопроса, кроме последнего.
 *
 * Окна — верхняя граница ожидания сети между вопросами, а не добавленное время ответа: вопрос,
 * показанный сразу, своего окна не тратит. Попытка, уложившаяся в этот бюджет, могла пройти
 * честно при любой потере связи; всё, что позже, — тайм-аут. Восстановление не продлевает
 * дедлайн: база — исходный `startedAtMs` записи старта (AD-6), пересчитывать её нельзя.
 */
function attemptDeadlineMs({startedAtMs, questions, nextQuestionWindowSeconds = NEXT_QUESTION_WINDOW_SECONDS}) {
  const list = Array.isArray(questions) ? questions : [];
  const budgetMs = list.reduce((total, question) => {
    const content = parseObject(question && question.payload);
    return total + (content ? privateQuestionSecondsMs(content) : 0);
  }, 0);
  const windows = Math.max(0, list.length - 1) * Math.max(0, nextQuestionWindowSeconds) * 1000;
  return startedAtMs + budgetMs + windows;
}

/**
 * Подача попытки из тела мутации — только named-поля, как у всех приёмников.
 *
 * Заявки на заряды у приватного прохода не существует в принципе: поле `chargeClaims` (как и
 * любой другой платной формы) в теле — несовпадение контракта, а не данные (8.5: платных
 * пропусков нет, timed skip — не платный правильный ответ).
 */
function readSubmission(data) {
  const body = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  const unknown = Object.keys(body).filter(
    (key) => !["ownerUid", "catalogId", "questId", "startId", "served", "answers"].includes(key),
  );
  if (unknown.length > 0) {
    fail("INVALID_ARGUMENT", `Unsupported submission fields: ${unknown.sort().join(", ")}`);
  }
  const ownerUid = typeof body.ownerUid === "string" ? body.ownerUid : "";
  const catalogId = typeof body.catalogId === "string" ? body.catalogId : "";
  const questId = typeof body.questId === "string" ? body.questId : "";
  const startId = typeof body.startId === "string" ? body.startId : "";
  if (!ownerUid || !catalogId || !questId || !startId) {
    fail("INVALID_ARGUMENT", "ownerUid, catalogId, questId and startId are required");
  }
  const served = readServedList(body.served);
  const answers = Array.isArray(body.answers) ? body.answers : [];
  return {ownerUid, catalogId, questId, startId, served, answers};
}

function readServedList(raw) {
  if (!Array.isArray(raw)) fail("INVALID_ARGUMENT", "served must be a list");
  const seen = new Set();
  return raw.map((entry) => {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      fail("INVALID_ARGUMENT", "served entry must be an object");
    }
    const questionId = typeof entry.questionId === "string" ? entry.questionId : "";
    const codeAnswerIndex = entry.codeAnswerIndex;
    if (!questionId || !Number.isInteger(codeAnswerIndex) || codeAnswerIndex < 0) {
      fail("INVALID_ARGUMENT", "served entry needs a questionId and a codeAnswerIndex");
    }
    if (seen.has(questionId)) fail("INVALID_ARGUMENT", "served names a question twice");
    seen.add(questionId);
    return {questionId, codeAnswerIndex};
  });
}

/**
 * Терминал попытки: вовремя поданная — FINISHED по присланной порции, просроченная — TIMEOUT
 * по ВСЕМУ замороженному квизу.
 *
 * Правило тайм-аута (AD-29): принятые вовремя ответы сохраняются, все оставшиеся вопросы,
 * включая невыданные, — «показан, без ответа» (цифра `1` из `scoreAttempt`: позиция в
 * знаменателе, ноль в процент). Процент не ограничивается отвеченной частью — знаменатель
 * весь квиз; платного эффекта у пропусков нет (их и нет: платных пропусков не существует).
 *
 * `keyDocument` всегда `null`: вопросы приватного дерева несут свои ключи, отдельного
 * хранилища ключей у приватного квиза нет.
 */
function finishAttempt({settings, questions, submission, now, deadlineMs}) {
  const list = Array.isArray(questions) ? questions : [];
  if (list.length === 0) fail("RUN_EMPTY", "The frozen quiz carries no questions");
  if (now > deadlineMs) {
    // Весь квиз как порция: каждая позиция в знаменателе, невыданные — без ответа.
    const wholeQuizServed = list.map((question, index) => ({codeAnswerIndex: index, questionId: question.id}));
    return terminal({
      reason: ATTEMPT_REASON.TIMEOUT,
      scoring: scoreAttempt({
        questions: list,
        served: wholeQuizServed,
        keyDocument: null,
        answers: submission.answers,
      }),
      finishedAtMs: deadlineMs,
    });
  }
  return terminal({
    reason: ATTEMPT_REASON.FINISHED,
    scoring: scoreAttempt({
      questions: list,
      served: submission.served,
      keyDocument: null,
      answers: submission.answers,
    }),
    finishedAtMs: now,
  });
}

/** Терминал, который можно хранить: счёт или честный отказ, а не число любой ценой. */
function terminal({reason, scoring, finishedAtMs}) {
  if (!scoring.scorable) {
    fail("RUN_UNSCORABLE", "The attempt could not be scored against the frozen quiz");
  }
  const digits = scoring.codeAnswer.split("");
  return {
    reason,
    status: ATTEMPT_STATUS.TERMINAL,
    percentScore: scoring.percentScore,
    correctCount: digits.filter((digit) => digit >= "2").length,
    questionCount: digits.length,
    questionScores: digits.map((digit) => Number(digit)),
    unscorable: scoring.unscorable,
    finishedAtMs,
  };
}

/**
 * Проекция собственного результата участнику — по ТЕКУЩЕЙ настройке showResult (AD-29).
 *
 * Настройка читается при каждом чтении/повторе, включая прошлые попытки: `false` даёт только
 * подтверждение завершения — без счёта, процента и по-вопросных цифр, и никакая неизвестная
 * статистика не рисуется нулём; `true` открывает собственный результат — счёт, процент и свои
 * по-вопросные цифры. Авторская статистика живёт отдельной проекцией и от флага не зависит.
 */
function projectOwnResult({attempt, showResult}) {
  if (!attempt) {
    return {status: ATTEMPT_STATUS.RUNNING};
  }
  const confirmation = {
    status: ATTEMPT_STATUS.TERMINAL,
    reason: attempt.reason,
    finishedAtMs: attempt.finishedAtMs,
  };
  if (showResult !== true) return confirmation;
  return {
    ...confirmation,
    percentScore: attempt.percentScore,
    correctCount: attempt.correctCount,
    questionCount: attempt.questionCount,
    questionScores: attempt.questionScores,
  };
}

/**
 * Авторская статистика по попыткам — доступна при любом showResult (AD-29, отдельный
 * authorized scope). Одна строка на попытку: исход и процент; считать чужие проценты в
 * «подтверждение завершения» участника она не едет.
 */
function projectAuthorStatistics({attempts}) {
  const rows = (Array.isArray(attempts) ? attempts : []).filter((attempt) => attempt && attempt.status === ATTEMPT_STATUS.TERMINAL);
  const scored = rows.filter((attempt) => Number.isFinite(attempt.percentScore));
  return {
    attemptLimitSummary: {
      totalAttempts: rows.length,
      finished: rows.filter((attempt) => attempt.reason === ATTEMPT_REASON.FINISHED).length,
      timedOut: rows.filter((attempt) => attempt.reason === ATTEMPT_REASON.TIMEOUT).length,
    },
    averagePercentScore:
      scored.length === 0 ?
        null :
        Math.round(scored.reduce((total, attempt) => total + attempt.percentScore, 0) / scored.length),
    attempts: rows.map((attempt) => ({
      status: attempt.status,
      reason: attempt.reason,
      percentScore: attempt.percentScore,
      finishedAtMs: attempt.finishedAtMs,
    })),
  };
}

module.exports = {
  ATTEMPT_REASON,
  ATTEMPT_STATUS,
  NEXT_QUESTION_WINDOW_SECONDS,
  attemptDeadlineMs,
  finishAttempt,
  privateQuestionSecondsMs,
  projectAuthorStatistics,
  projectOwnResult,
  readSubmission,
};
