"use strict";

/**
 * Редактируемая копия урока для «Предложить правку» (AD-33, история 4.23, CAP-27).
 *
 * Копию собирает сервер из опубликованных вопросов: для вопросов, которые redact() умеет
 * разделить, копия получает публичную половину (SingleChoiceRedacted/…/FillBlankRedacted) —
 * без ключа; ключ отбрасывается здесь же и ни в какую запись не попадает. Survey (ответа нет)
 * и уже-редактированные едут как есть; отказ redact() (легаси-диалект, неизвестный тип) едет
 * запертым в том виде, в котором уже открыт миру.
 *
 * Граница честности: секретные ключи HARD, Арены и экзаменов живут в документах
 * `question_keys` (сервер-only) — этот модуль их не читает вовсе, поэтому в копию они попасть
 * не могут. Публичный payload не-redactable-диалектов несёт ответ всему миру уже сегодня
 * (красакция публикации ещё не включена — publicDocuments пишет payload как есть); копия не
 * добавляет к этому ничего: запертый вопрос обязан доехать до применения байт-в-байт.
 *
 * Модуль чистый: Firestore и модель не трогает.
 */

const {redact, STATUS} = require("./question-redaction");

/** Версия конверта копии: строгий клиентский парсер читает только её. */
const EDIT_COPY_VIEW = "lesson-edit-copy-v1";

function fail(message) {
  const error = new Error(message);
  error.code = "COPY_EDIT_INVALID";
  throw error;
}

function nonBlank(value) {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

/** Тело запроса копии: адрес урока. Больше в копии ничего не заказывается. */
function parseEditCopyRequest(payload) {
  const body = payload && typeof payload === "object" && !Array.isArray(payload) ? payload : {};
  const lessonId = nonBlank(body.lessonId);
  if (lessonId === null || lessonId.includes("/")) fail("lessonId is required");
  return {lessonId};
}

/**
 * Копия урока: редактируемые вопросы — публичной половиной без ключа, запертые — как
 * опубликованы. Ровно то, что редактор предложившего покажет и что вернётся в предложении.
 */
function buildEditCopy({lesson, questions}) {
  if (!lesson || typeof lesson !== "object") fail("edit copy needs the lesson document");
  const lessonId = nonBlank(lesson.id);
  if (lessonId === null) fail("edit copy lesson needs an id");
  if (!Array.isArray(questions) || questions.length === 0) fail("edit copy needs questions");
  const rows = questions.map((question) => copyRow(question));
  return {
    view: EDIT_COPY_VIEW,
    lessonId,
    lessonTitle: nonBlank(lesson.title) || "",
    baseRevision: Number.isSafeInteger(lesson.version) && lesson.version > 0 ? lesson.version : 0,
    questions: rows,
  };
}

function copyRow(question) {
  if (!question || typeof question !== "object") fail("edit copy question must be an object");
  const id = nonBlank(question.id);
  if (id === null) fail("edit copy question needs an id");
  if (typeof question.payload !== "string" || question.payload.trim() === "") {
    fail(`question ${id} payload is missing`);
  }
  // Сложность для публичной половины: у опубликованного документа поля difficulty может не быть
  // (publicDocuments её не пишет), а внутри payload она есть всегда — читаем из него.
  let inner = {};
  try {
    inner = JSON.parse(question.payload) || {};
  } catch (_) {
    inner = {};
  }
  const difficulty = nonBlank(question.difficulty) || nonBlank(inner.difficulty) || "EASY";
  const split = redact(question.payload, difficulty, {questionId: id});
  if (split.status === STATUS.REDACTED) {
    return row(question, id, split.publicPayload, true, difficulty);
  }
  if (split.status === STATUS.NOT_APPLICABLE || split.status === STATUS.ALREADY_REDACTED) {
    // Survey ответа не имеет; уже-редактированный ключа не несёт. Оба едут запертыми:
    // отмечать верный ответ в них нечем, правка — предмет новой подачи, не копии.
    return row(question, id, question.payload, false, difficulty);
  }
  // Отказ redact(): ключ остаётся внутри payload. Он уже публичен; копия едет запертой и
  // байт-в-байт, применение правки такого вопроса невозможно.
  return row(question, id, question.payload, false, difficulty);
}

function row(question, id, payload, editable, difficulty) {
  return {
    id,
    lessonId: nonBlank(question.lessonId) || "",
    type: nonBlank(question.type) || "",
    language: nonBlank(question.language) || "",
    languageLevel: Number.isSafeInteger(question.languageLevel) ? question.languageLevel : 0,
    difficulty,
    order: Number.isSafeInteger(question.order) ? question.order : 0,
    text: nonBlank(question.text) || "",
    imagePath: question.imagePath === undefined ? null : question.imagePath,
    payload,
    editable,
  };
}

/**
 * Предложение против базы: идентификаторы — только из урока (чужой id — инъекция в дерево),
 * запертые вопросы — байт-в-байт с базой. Возвращает вопросы к записи; нарушение — именованный
 * отказ, а не молчаливая правка.
 */
function validateEditProposalAgainstBase({baseQuestions, proposalQuestions}) {
  if (!Array.isArray(baseQuestions)) fail("apply needs the base questions");
  if (!Array.isArray(proposalQuestions) || proposalQuestions.length === 0) {
    fail("apply needs the proposal questions");
  }
  const baseById = new Map(baseQuestions.map((question) => [question && question.id, question]));
  const rows = [];
  for (const question of proposalQuestions) {
    if (!question || typeof question !== "object" || !nonBlank(question.id)) {
      fail("proposal question needs an id");
    }
    const base = baseById.get(question.id);
    if (!base) fail(`PROPOSAL_QUESTION_FOREIGN: ${question.id} is not part of the lesson`);
    if (base.editable === false &&
      (question.payload !== base.payload || question.text !== base.text)) {
      fail(`PROPOSAL_LOCKED_QUESTION_EDITED: ${question.id} is locked in the copy`);
    }
    rows.push({...question, lessonId: base.lessonId || question.lessonId});
  }
  return rows;
}

/** Вопросы к записи: предложившему принадлежит только отмеченный ответ и его правки. */
function mergedQuestionsForApply({baseQuestions, proposalQuestions}) {
  const validated = validateEditProposalAgainstBase({baseQuestions, proposalQuestions});
  const changedQuestionIds = [];
  for (const question of validated) {
    const base = baseQuestions.find((row) => row.id === question.id);
    if (!base || base.payload !== question.payload || base.text !== question.text) {
      changedQuestionIds.push(question.id);
    }
  }
  if (changedQuestionIds.length === 0) fail("PROPOSAL_NOTHING_CHANGED");
  return {questions: validated, changedQuestionIds};
}

module.exports = {
  EDIT_COPY_VIEW,
  buildEditCopy,
  mergedQuestionsForApply,
  parseEditCopyRequest,
  validateEditProposalAgainstBase,
};
