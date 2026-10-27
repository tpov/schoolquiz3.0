"use strict";

/**
 * Решения админов по вердиктам модели (AD-32, история 4.17, UX-22).
 *
 * Чистый модуль чтения и отметок: вью «Решения» собирается из трёх мест — неизменяемые записи
 * `review_verdicts` (по-вопросные исходы, модель/промпт/ревизия, источники, основания), заявки
 * `quest_review_requests` (названия квеста и урока, тело вопроса для предпросмотра) и отметки
 * админов `review_decision_marks` (✓/✗ с комментарием, отдельно от вердиктов). Обе поверхности
 * callable в index.js; право решает requireVerifier — сервер, не клиент.
 *
 * Правила честности:
 *   — запись вердикта, не прошедшая строгий разбор, не исчезает молча (AD-10): её место в странице
 *     занимает строка-метка `unreadable`, и страница продолжает собираться;
 *   — строка — вопрос внутри записи вердикта: отметка админа адресна именно вопросу, а метаданные
 *     записи (модель/промпт/попытка/источники/основание) едут с каждой её строкой одним источником;
 *   — курсор страницы — имя документа вердикта: записи не несут времени по построению, а имена
 *     детерминированы идентичностью задания, поэтому лексикографический порядок стабилен.
 */

const {createHash} = require("node:crypto");

const VERDICTS_COLLECTION = "review_verdicts";
const QUEUE_COLLECTION = "quest_review_requests";
const MARKS_COLLECTION = "review_decision_marks";

const DECISIONS_VIEW_VERSION = "quest-review-decisions-v1";

const DECISION_FILTERS = Object.freeze(["UNMARKED", "VALID", "INVALID"]);
const MARK_VALUES = Object.freeze(["VALID", "INVALID"]);

/** Строки на страницу по умолчанию и потолок: страница обязана быть ограничена (AD-31). */
const DEFAULT_ROW_LIMIT = 30;
const MAX_ROW_LIMIT = 60;

/** Вер дикт-документов на один шаг чтения: стык страниц никогда не рвёт одну запись. */
const VERDICT_PAGE_SIZE = 25;

/** Границы записи вердикта в вью: их превышение — нарушенная схема, строка уходит в unreadable. */
const MAX_QUESTIONS_PER_VERDICT = 64;
const MAX_SOURCES_PER_VERDICT = 32;
const MAX_RATIONALE_CHARS = 8000;
const MAX_REASON_CHARS = 2000;

/** Комментарий отметки: необязателен, но ограничен — это заметка аудита, не эссе. */
const MAX_MARK_COMMENT_BYTES = 2000;

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function nonBlankString(value) {
  if (typeof value !== "string" || value.trim() === "") return null;
  return value;
}

function nonNegativeInteger(value) {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

/** Фильтр страницы: три состояния отметки из UX-22; всё иное — отказ, а не молчаливый дефолт. */
function readDecisionFilter(data) {
  const body = data && typeof data === "object" ? data : {};
  const filter = nonBlankString(body.filter);
  if (filter === null) return "UNMARKED";
  if (!DECISION_FILTERS.includes(filter)) fail("INVALID_ARGUMENT", "Unknown decisions filter");
  return filter;
}

function readRowLimit(data) {
  const body = data && typeof data === "object" ? data : {};
  const asked = nonNegativeInteger(body.limit);
  if (asked === null) return DEFAULT_ROW_LIMIT;
  if (asked < 1) fail("INVALID_ARGUMENT", "Decisions limit must be positive");
  return Math.min(asked, MAX_ROW_LIMIT);
}

/**
 * Курсор страницы: имя документа вердикта и, когда страница остановилась посреди записи,
 * последний рассмотренный вопрос этой записи. Записи неизменяемы, поэтому порядок вопросов
 * в массиве стабилен между страницами: докрутка до `afterQuestionId` включительно и продолжение
 * не теряют и не повторяют ни одной строки.
 */
function readVerdictCursor(data) {
  const body = data && typeof data === "object" ? data : {};
  const pair = body.cursor && typeof body.cursor === "object" ? body.cursor : null;
  const verdictId = pair ? nonBlankString(pair.verdictId) : null;
  if (verdictId === null) return null;
  if (verdictId.includes("/")) fail("INVALID_ARGUMENT", "Decisions cursor is not a verdict id");
  const afterQuestionId = pair ? nonBlankString(pair.afterQuestionId) : null;
  if (afterQuestionId !== null && afterQuestionId.includes("/")) {
    fail("INVALID_ARGUMENT", "Decisions cursor question is not a question id");
  }
  return {verdictId, afterQuestionId};
}

/**
 * Строгий разбор записи вердикта для вью: форма — та же, что пишет buildVerdictRecord, плюс
 * границы вью. Всё, что не сошлось, — нарушенная запись: возвращается null, и её место в странице
 * занимает строка-метка unreadable, а не пропавший документ.
 */
function parseVerdictRecord(docId, raw) {
  if (!raw || typeof raw !== "object") return null;
  if (raw.schemaVersion !== 1) return null;
  const submissionId = nonBlankString(raw.submissionId);
  const lessonId = nonBlankString(raw.lessonId);
  const stage = nonBlankString(raw.stage);
  const model = nonBlankString(raw.model);
  const promptVersion = nonBlankString(raw.promptVersion);
  const rationale = typeof raw.rationale === "string" ? raw.rationale : null;
  if (!submissionId || !lessonId || !model || !promptVersion || rationale === null) return null;
  if (stage !== "WORDS" && stage !== "FACTS") return null;
  if (rationale.length > MAX_RATIONALE_CHARS) return null;
  const attempt = nonNegativeInteger(raw.attempt);
  const revision = nonNegativeInteger(raw.revision);
  if (attempt === null || attempt < 1 || revision === null) return null;
  if (!Array.isArray(raw.sources) || raw.sources.length > MAX_SOURCES_PER_VERDICT) return null;
  const sources = [];
  for (const source of raw.sources) {
    const text = nonBlankString(source);
    if (text === null || text.length > 2000) return null;
    sources.push(text);
  }
  if (!Array.isArray(raw.questionVerdicts) || raw.questionVerdicts.length === 0 ||
    raw.questionVerdicts.length > MAX_QUESTIONS_PER_VERDICT) {
    return null;
  }
  const questionVerdicts = [];
  const seenQuestions = new Set();
  for (const outcome of raw.questionVerdicts) {
    if (!outcome || typeof outcome !== "object") return null;
    const questionId = nonBlankString(outcome.questionId);
    if (questionId === null || seenQuestions.has(questionId)) return null;
    seenQuestions.add(questionId);
    const verdict = outcome.verdict === "PASS" || outcome.verdict === "FAIL" ? outcome.verdict : null;
    const reason = typeof outcome.reason === "string" && outcome.reason.trim() !== "" ? outcome.reason : null;
    if (verdict === null || reason === null || reason.length > MAX_REASON_CHARS) return null;
    questionVerdicts.push({questionId, verdict, reason});
  }
  return {docId, submissionId, lessonId, stage, attempt, revision, model, promptVersion, sources, rationale, questionVerdicts};
}

/** Вопрос заявки для предпросмотра: ровно то, что автор подал, без собранных сервером ключей. */
function submissionQuestion(submission, lessonId, questionId) {
  const questions = submission && Array.isArray(submission.questions) ? submission.questions : [];
  const question = questions.find((item) => item && item.id === questionId && item.lessonId === lessonId) || null;
  if (!question) return null;
  const text = typeof question.text === "string" ? question.text : null;
  const type = nonBlankString(question.type);
  const language = nonBlankString(question.language);
  const payload = typeof question.payload === "string" && question.payload.trim() !== "" ? question.payload : null;
  if (text === null || type === null || language === null || payload === null) return null;
  return {type, language, text, payload};
}

function lessonTitleOf(submission, lessonId) {
  const lessons = submission && Array.isArray(submission.lessons) ? submission.lessons : [];
  const lesson = lessons.find((item) => item && item.id === lessonId) || null;
  return lesson && typeof lesson.title === "string" ? lesson.title : "";
}

function questTitleOf(submission) {
  const draft = submission && submission.draft && typeof submission.draft === "object" ? submission.draft : null;
  return draft && typeof draft.title === "string" ? draft.title : "";
}

/** Строка соответствует фильтру по состоянию отметки: unreadable — навсегда «без отметки». */
function matchesFilter(filter, mark) {
  if (filter === "UNMARKED") return mark === null;
  return mark !== null && mark.mark === filter;
}

/**
 * Страница «Решений».
 *
 * @param {*} db Firestore.
 * @param {object} data тело вызова: `{filter?, cursor?: {verdictId}, limit?}`.
 * @param {object} options `{documentId}` — FieldPath.documentId() того же SDK; в тестах — метка
 *   `"__name__"`. Без него страницы по имени документа не выразить.
 * @returns {Promise<{viewVersion, entries, hasMore, nextCursor}>}
 */
async function readReviewDecisions(db, data, options = {}) {
  const documentId = options.documentId;
  if (!documentId) fail("INVALID_ARGUMENT", "Decisions paging needs the document id field");
  const filter = readDecisionFilter(data);
  const rowLimit = readRowLimit(data);
  const cursor = readVerdictCursor(data);

  const entries = [];
  const submissions = new Map();
  let lastVerdictId = cursor ? cursor.verdictId : null;
  let nextCursor = cursor;
  let hasMore = false;
  let stoppedAtBound = false;

  /** Один документ вердикта: строки по вопросам, курсор — последний рассмотренный вопрос. */
  async function consumeVerdictDoc(doc, resumeAfter) {
    const record = parseVerdictRecord(doc.id, typeof doc.data === "function" ? doc.data() : doc.data);
    if (!record) {
      if (matchesFilter(filter, null) && entries.length < rowLimit) {
        entries.push({unreadable: true, verdictId: doc.id, mark: null});
      }
      lastVerdictId = doc.id;
      nextCursor = {verdictId: doc.id, afterQuestionId: null};
      return true;
    }
    if (!submissions.has(record.submissionId)) {
      const submissionSnapshot = await db.doc(`${QUEUE_COLLECTION}/${record.submissionId}`).get();
      submissions.set(record.submissionId, submissionSnapshot && submissionSnapshot.exists
        ? submissionSnapshot.data() || {}
        : null);
    }
    const submission = submissions.get(record.submissionId);
    const marksSnapshot = await db.collection(MARKS_COLLECTION).where("verdictId", "==", doc.id).get();
    const marksByQuestion = new Map();
    for (const markDoc of (marksSnapshot && Array.isArray(marksSnapshot.docs) ? marksSnapshot.docs : [])) {
      const mark = parseMarkRecord(markDoc.data ? markDoc.data() : {});
      if (mark && mark.verdictId === doc.id) marksByQuestion.set(mark.questionId, mark);
    }
    let lastExaminedQuestionId = null;
    let docCompleted = true;
    for (const outcome of record.questionVerdicts) {
      if (resumeAfter !== null) {
        if (outcome.questionId === resumeAfter) resumeAfter = null;
        continue;
      }
      if (entries.length >= rowLimit) {
        docCompleted = false;
        break;
      }
      lastExaminedQuestionId = outcome.questionId;
      const mark = marksByQuestion.get(outcome.questionId) || null;
      if (!matchesFilter(filter, mark)) continue;
      entries.push({
        unreadable: false,
        verdictId: doc.id,
        submissionId: record.submissionId,
        questTitle: questTitleOf(submission),
        lessonId: record.lessonId,
        lessonTitle: lessonTitleOf(submission, record.lessonId),
        stage: record.stage,
        attempt: record.attempt,
        revision: record.revision,
        model: record.model,
        promptVersion: record.promptVersion,
        sources: record.sources,
        rationale: record.rationale,
        questionId: outcome.questionId,
        verdict: outcome.verdict,
        reason: outcome.reason,
        question: submissionQuestion(submission, record.lessonId, outcome.questionId),
        mark: mark ? publicMark(mark) : null,
      });
    }
    lastVerdictId = doc.id;
    nextCursor = {verdictId: doc.id, afterQuestionId: docCompleted ? null : lastExaminedQuestionId};
    return docCompleted;
  }

  // Докрутка посреди записи (4.17): сам документ курсора читается напрямую — продолжение идёт
  // уже после него, и ни один вопрос записи не теряется и не повторяется.
  if (cursor && cursor.afterQuestionId !== null) {
    const resumeSnapshot = await db.doc(`${VERDICTS_COLLECTION}/${cursor.verdictId}`).get();
    if (resumeSnapshot && resumeSnapshot.exists) {
      stoppedAtBound = !(await consumeVerdictDoc(resumeSnapshot, cursor.afterQuestionId));
    }
  }
  while (!stoppedAtBound) {
    let query = db.collection(VERDICTS_COLLECTION).orderBy(documentId).limit(VERDICT_PAGE_SIZE);
    if (lastVerdictId !== null) query = query.startAfter(lastVerdictId);
    const snapshot = await query.get();
    const docs = (snapshot && Array.isArray(snapshot.docs) ? snapshot.docs : []).filter((doc) => doc && doc.id);
    if (docs.length === 0) break;
    for (const doc of docs) {
      // Страница строк полна посреди записи: курсор запомнил последний рассмотренный вопрос,
      // следующая страница докрутит запись без потерь и повторов.
      if (!(await consumeVerdictDoc(doc, null))) {
        stoppedAtBound = true;
        break;
      }
    }
    if (stoppedAtBound) break;
    // Полная страница вердиктов означает, что за ней может стоять ещё; короткая — очередь кончилась.
    if (docs.length < VERDICT_PAGE_SIZE) break;
  }
  hasMore = stoppedAtBound;
  return {viewVersion: DECISIONS_VIEW_VERSION, entries, hasMore, nextCursor};
}

function parseMarkRecord(raw) {
  if (!raw || typeof raw !== "object") return null;
  const verdictId = nonBlankString(raw.verdictId);
  const questionId = nonBlankString(raw.questionId);
  const mark = MARK_VALUES.includes(raw.mark) ? raw.mark : null;
  const comment = typeof raw.comment === "string" ? raw.comment : "";
  const adminUid = nonBlankString(raw.adminUid);
  const updatedAtMs = nonNegativeInteger(raw.updatedAtMs);
  if (!verdictId || !questionId || mark === null || !adminUid || updatedAtMs === null) return null;
  return {verdictId, questionId, mark, comment, adminUid, updatedAtMs};
}

function publicMark(mark) {
  return {mark: mark.mark, comment: mark.comment, adminUid: mark.adminUid, updatedAtMs: mark.updatedAtMs};
}

/** Имя документа отметки: детерминировано парой (вердикт, вопрос); сами поля хранит документ. */
function markDocumentId(verdictId, questionId) {
  return createHash("sha256").update(`${verdictId}\u0000${questionId}`).digest("hex");
}

/**
 * Отметка админа: ✓/✗ с необязательным комментарием, отдельно от вердиктов.
 *
 * Вердикт обязан существовать и пройти строгий разбор, вопрос — принадлежать его вопросам:
 * отметка адресна аудиту живой записи, а не произвольной паре строк. Повторная отметка той же
 * пары перезаписывает прежнюю — последнее решение админа и есть решение.
 *
 * @param {*} db Firestore.
 * @param {object} actor `{uid, nowMs}`.
 * @param {object} data тело вызова: `{verdictId, questionId, mark, comment?}`.
 * @returns {Promise<{verdictId, questionId, mark, comment, updatedAtMs}>}
 */
async function writeReviewDecisionMark(db, actor, data) {
  const uid = nonBlankString(actor && actor.uid);
  const nowMs = nonNegativeInteger(actor && actor.nowMs);
  if (!uid || nowMs === null) fail("INVALID_ARGUMENT", "Decision mark needs an admin and a clock");
  const body = data && typeof data === "object" ? data : {};
  const verdictId = nonBlankString(body.verdictId);
  const questionId = nonBlankString(body.questionId);
  if (!verdictId || verdictId.includes("/") || !questionId || questionId.includes("/")) {
    fail("INVALID_ARGUMENT", "Decision mark needs a verdict and a question");
  }
  if (!MARK_VALUES.includes(body.mark)) fail("INVALID_ARGUMENT", "Decision mark must be VALID or INVALID");
  const comment = typeof body.comment === "string" ? body.comment.trim() : "";
  if (Buffer.byteLength(comment, "utf8") > MAX_MARK_COMMENT_BYTES) {
    fail("INVALID_ARGUMENT", "Decision comment is too long");
  }
  const verdictSnapshot = await db.doc(`${VERDICTS_COLLECTION}/${verdictId}`).get();
  const record = verdictSnapshot && verdictSnapshot.exists
    ? parseVerdictRecord(verdictId, verdictSnapshot.data ? verdictSnapshot.data() : {})
    : null;
  if (!verdictSnapshot || !verdictSnapshot.exists) {
    fail("NOT_FOUND", `Verdict ${verdictId} not found`);
  }
  if (!record) fail("DATA_LOSS", `Verdict ${verdictId} requires repair`);
  if (!record.questionVerdicts.some((outcome) => outcome.questionId === questionId)) {
    fail("INVALID_ARGUMENT", `Question ${questionId} is not part of verdict ${verdictId}`);
  }
  const mark = {
    schemaVersion: 1,
    verdictId,
    questionId,
    mark: body.mark,
    comment,
    adminUid: uid,
    updatedAtMs: nowMs,
  };
  await db.doc(`${MARKS_COLLECTION}/${markDocumentId(verdictId, questionId)}`).set(mark);
  return {verdictId, questionId, mark: mark.mark, comment: mark.comment, updatedAtMs: mark.updatedAtMs};
}

module.exports = {
  DECISIONS_VIEW_VERSION,
  DECISION_FILTERS,
  DEFAULT_ROW_LIMIT,
  MARKS_COLLECTION,
  MARK_VALUES,
  MAX_MARK_COMMENT_BYTES,
  MAX_ROW_LIMIT,
  QUEUE_COLLECTION,
  VERDICTS_COLLECTION,
  VERDICT_PAGE_SIZE,
  markDocumentId,
  parseVerdictRecord,
  readReviewDecisions,
  writeReviewDecisionMark,
};
