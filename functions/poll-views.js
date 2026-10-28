"use strict";

/**
 * Разрешённые проекции опросов (UX-O7, история 8.2 эпика `epic-social-features`).
 *
 * Две поверхности чтения — и ни одна не расширяет правило контракта §5:
 *  - `list` — опубликованные серии с собственным прогрессом читателя. Прогресс — число
 *    вопросов текущей revision, на которые участник уже ответил; авторские серии едут с той
 *    же проекцией и пометкой `own` (административных полей вью не добавляет).
 *  - `questionState` — один вопрос серии: содержание + собственный принятый голос + счёты.
 *    Счёты приходят одним снапшотом (счёт по варианту + знаменатель) и только после
 *    собственного принятого голоса: до него `distribution: null` — «скрыто», что не то же
 *    самое, что нулевые счёты (AD-8). Проценты вью не считает: формула
 *    `100 × выбравших вариант / ответивших на вопрос` — презентация клиента (§5), а знаменатель
 *    — участники, не отметки, поэтому сумма процентов multi-choice может превышать 100.
 *
 * Идентичность — тройка серия/revision/вопрос (контракт §2): счёты и уникальность живут
 * внутри одной тройки, новая revision — новая идентичность со своими счетами с нуля.
 * Коллекции серверные (правила закрыты наглухо); документы писал приёмник, и кривой документ —
 * серверный баг: падаем громко (UNAVAILABLE), а не молча пропускаем.
 */

const VIEW_VERSION = "poll-views-v1";
const LIMITS = Object.freeze({
  defaultPageSize: 20,
  maxPageSize: 50,
  maxSeries: 50,
  // Скан голосов участника и голосов серии — по одному равенству (без составных индексов),
  // потолок держит чтение конечным; выход за потолок — громкий отказ, а не обрезанная правда.
  maxVoteScan: 500,
  responseBytes: 100_000,
});
const MODE_SINGLE = "SINGLE";
const MODE_MULTIPLE = "MULTIPLE";
const MODES = Object.freeze([MODE_SINGLE, MODE_MULTIPLE]);
const ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

class PollViewError extends Error {
  constructor(code) {
    super(code);
    this.name = "PollViewError";
    this.code = code;
  }
}

function fail(code) { throw new PollViewError(code); }
function unavailable(condition) { if (!condition) fail("UNAVAILABLE"); }
function plain(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    [Object.prototype, null].includes(Object.getPrototypeOf(value));
}
function identifier(value) {
  return typeof value === "string" && value.trim().length > 0 && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value) && Buffer.byteLength(value) <= 1500;
}
function unsigned(value, minimum = 0) {
  return Number.isSafeInteger(value) && value >= minimum && value <= Number.MAX_SAFE_INTEGER;
}
function only(value, fields) {
  if (!plain(value) || Reflect.ownKeys(value).some((key) => !fields.includes(key))) fail("INVALID_ARGUMENT");
}
function readRequest(input, fields) {
  only(input, ["uid", "payload"]);
  if (!identifier(input.uid)) fail("UNAUTHENTICATED");
  only(input.payload, fields);
  return input;
}
function snapshotData(snapshot) {
  unavailable(snapshot && typeof snapshot.exists === "boolean" && typeof snapshot.data === "function");
  return snapshot.exists ? snapshot.data() : null;
}
function checkResponse(value) {
  if (Buffer.byteLength(JSON.stringify(value)) > LIMITS.responseBytes) fail("SOURCE_TOO_LARGE");
  return value;
}

/** Документ серии писал приёмник: дрейф формы — серверный баг, а не чужая строка. */
function readSeries(snapshot) {
  const raw = snapshotData(snapshot);
  unavailable(plain(raw) && identifier(raw.seriesId) && typeof raw.authorUid === "string" &&
    typeof raw.title === "string" && unsigned(raw.revisionId, 1) && plain(raw.revision) &&
    Array.isArray(raw.revision.questions) && raw.revision.questions.length > 0 &&
    unsigned(raw.revision.publishedAtMs, 1));
  const questions = raw.revision.questions.map(readQuestion);
  unavailable(questions.every((question, index) => question.questionIndex === index));
  return {
    seriesId: raw.seriesId,
    authorUid: raw.authorUid,
    title: raw.title,
    revisionId: raw.revisionId,
    publishedAtMs: raw.revision.publishedAtMs,
    questions,
  };
}

function readQuestion(raw) {
  unavailable(plain(raw) && unsigned(raw.questionIndex) && typeof raw.text === "string" &&
    MODES.includes(raw.mode) && Array.isArray(raw.options) &&
    raw.options.length >= 2 && raw.options.length <= 8);
  const seen = new Set();
  const options = raw.options.map((option) => {
    unavailable(plain(option) && ID_PATTERN.test(option.optionId) &&
      typeof option.text === "string" && option.text.trim().length > 0);
    unavailable(!seen.has(option.optionId));
    seen.add(option.optionId);
    return {optionId: option.optionId, text: option.text};
  });
  return {questionIndex: raw.questionIndex, text: raw.text, mode: raw.mode, options};
}

function readOwnVote(raw) {
  unavailable(plain(raw) && Array.isArray(raw.selectedOptionIds) && raw.selectedOptionIds.length > 0 &&
    unsigned(raw.acceptedAtMs, 1));
  return {selectedOptionIds: raw.selectedOptionIds.slice(), acceptedAtMs: raw.acceptedAtMs};
}

/**
 * Один снапшот счётов (§5): счёт по каждому варианту вопроса + знаменатель — участники,
 * ответившие на вопрос тройки идентичности. Отметки не участники: multi-choice сумма
 * счетов может превышать знаменатель.
 *
 * Ось вопроса обязательна: источник запроса — все голоса серии, а продакшн-тела могли
 * назначать одинаковые optionId на разные вопросы, поэтому без равенства `questionIndex`
 * голос чужого вопроса попадал бы и в счёт варианта, и в знаменатель (ретро эпика 8,
 * находка 1). Поле пишет приёмник (`index.js`, applyPollVote) — кривой документ, как и
 * прежде, серверный баг: UNAVAILABLE, а не молчаливый пропуск.
 */
function countsFor(votes, question) {
  const known = new Set(question.options.map((option) => option.optionId));
  const optionCounts = Object.fromEntries(question.options.map((option) => [option.optionId, 0]));
  let respondentCount = 0;
  for (const vote of votes) {
    unavailable(plain(vote) && unsigned(vote.revisionId, 1) && unsigned(vote.questionIndex) &&
      Array.isArray(vote.selectedOptionIds));
    if (vote.revisionId !== question.revisionId || vote.questionIndex !== question.questionIndex) continue;
    let counted = false;
    for (const optionId of vote.selectedOptionIds) {
      if (!known.has(optionId)) continue;
      optionCounts[optionId] += 1;
      counted = true;
    }
    if (counted) respondentCount += 1;
  }
  return {optionCounts, respondentCount};
}

/**
 * Читатель опросов. Контроллер подаёт Auth uid; поля запроса власть не выдают.
 * Чтение вне транзакции: серия и голоса независимы, снапшот считается на прочитанном.
 */
function createPollViews({db}) {
  if (!db || typeof db.collection !== "function") {
    throw new TypeError("Trusted poll dependencies required");
  }

  async function list(input) {
    const {uid, payload} = readRequest(input, ["pageSize"]);
    const value = payload.pageSize === undefined ? LIMITS.defaultPageSize : payload.pageSize;
    if (!unsigned(value, 1) || value > LIMITS.maxPageSize) fail("INVALID_ARGUMENT");

    const seriesRows = await db.collection("poll_series").limit(LIMITS.maxSeries).get();
    const series = [];
    for (const doc of seriesRows.docs) {
      const entry = readSeries(doc);
      series.push({
        seriesId: entry.seriesId,
        title: entry.title,
        revisionId: entry.revisionId,
        questionCount: entry.questions.length,
        own: entry.authorUid === uid,
        updatedAtMs: entry.publishedAtMs,
      });
    }
    series.sort((a, b) => (b.updatedAtMs - a.updatedAtMs) || (a.seriesId < b.seriesId ? 1 : a.seriesId > b.seriesId ? -1 : 0));

    const voteRows = await db.collection("poll_votes").where("participantUid", "==", uid)
      .limit(LIMITS.maxVoteScan).get();
    // Потолок достигнут — progressed мог обрезаться; обрезанный прогресс не снапшот (AD-8).
    if (voteRows.docs.length >= LIMITS.maxVoteScan) fail("SOURCE_TOO_LARGE");
    const answeredBySeries = new Map();
    for (const doc of voteRows.docs) {
      const raw = snapshotData(doc);
      unavailable(plain(raw) && identifier(raw.seriesId) && unsigned(raw.revisionId, 1));
      answeredBySeries.set(`${raw.seriesId}\u0000${raw.revisionId}`,
        (answeredBySeries.get(`${raw.seriesId}\u0000${raw.revisionId}`) || 0) + 1);
    }
    const entries = series
      .slice(0, value)
      .map((entry) => ({
        ...entry,
        answeredCount: Math.min(answeredBySeries.get(`${entry.seriesId}\u0000${entry.revisionId}`) || 0,
          entry.questionCount),
      }));
    return checkResponse({viewVersion: VIEW_VERSION, entries});
  }

  async function questionState(input) {
    const {uid, payload} = readRequest(input, ["seriesId", "questionIndex"]);
    if (!ID_PATTERN.test(String(payload.seriesId))) fail("INVALID_ARGUMENT");
    if (!unsigned(payload.questionIndex)) fail("INVALID_ARGUMENT");

    const snapshot = await db.collection("poll_series").doc(payload.seriesId).get();
    if (!snapshot.exists) fail("NOT_FOUND");
    const series = readSeries(snapshot);
    const question = series.questions[payload.questionIndex];
    if (question === undefined || question.questionIndex !== payload.questionIndex) {
      fail("INVALID_ARGUMENT");
    }

    // Своё «не отправлено» различимо от «нет»: детерминированный id той же схемы, что у приёмника.
    const voteId = `${series.seriesId}-r${series.revisionId}-q${question.questionIndex}-${uid}`;
    const ownRaw = snapshotData(await db.collection("poll_votes").doc(voteId).get());
    const ownVote = ownRaw ? readOwnVote(ownRaw) : null;

    let distribution = null;
    if (ownVote) {
      const voteRows = await db.collection("poll_votes").where("seriesId", "==", series.seriesId)
        .limit(LIMITS.maxVoteScan).get();
      // Потолок достигнут — за ним могут быть неотсчитанные голоса; усечённый снапшот
      // изображал бы чужую правду (AD-8), поэтому громкий отказ.
      if (voteRows.docs.length >= LIMITS.maxVoteScan) fail("SOURCE_TOO_LARGE");
      const votes = voteRows.docs.map((doc) => {
        const raw = snapshotData(doc);
        unavailable(plain(raw));
        return raw;
      });
      distribution = countsFor(votes, {...question, revisionId: series.revisionId});
    }

    return checkResponse({
      viewVersion: VIEW_VERSION,
      seriesId: series.seriesId,
      title: series.title,
      own: series.authorUid === uid,
      revisionId: series.revisionId,
      questionCount: series.questions.length,
      questionIndex: question.questionIndex,
      question,
      ownVote,
      distribution,
    });
  }

  return Object.freeze({list, questionState});
}

module.exports = {
  VIEW_VERSION,
  LIMITS,
  MODE_SINGLE,
  MODE_MULTIPLE,
  PollViewError,
  createPollViews,
};
