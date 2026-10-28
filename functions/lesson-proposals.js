"use strict";

/**
 * Предложение автору (AD-33, история 4.21, CAP-27): одна запись для трёх входов.
 *
 * Proposal = (kind REPORT | COPY_EDIT | NEW_LESSON, целевой урок или узел курса, base revision,
 * proposer, payload). Все виды проходят этапы AD-32 («Слова», «Факты») до показа автору; маршрут —
 * владельцу целевого урока или курса (выводит сервер из документа цели, не из тела); плата по виду
 * 0 / 100 × изменённые вопросы / 1000 без выплат; предложение по устаревшей редакции — STALE и не
 * применяется.
 *
 * Модуль чистый: Firestore и модель не трогает. Сюда сходятся форма тела подачи, тарифы, машина
 * статусов и исход проверки предложений; приёмка и решение автора живут в index.js, конвейер
 * проверки предложений — в review-queue-worker.js (вторая семья того же свипа).
 *
 * Правила честности:
 *   — владельца цели выводит сервер из документа цели; подставленный в тело чужой uid адресацию
 *     не меняет (тот же мотив, что у отчёта об ошибке, AD-20);
 *   — чужая цель неотличима от отсутствующей: отказ права — not-found, не «чужое»;
 *   — предложение своему материалу не существует (CAP-27: «другого автора») — именованный отказ;
 *   — base revision читает сервер из документа цели в момент подачи; сверку с текущей редакцией
 *     делает решение автора, а не подача;
 *   — проверка предложений не пишет в мир заявок: свои конвейер, вердикты и статус — вкладка
 *     «Решения» (4.17) остаётся про вердикты заявок.
 */

const PROPOSALS_COLLECTION = "lesson_proposals";

/** Тарифы платы за предложение — документ configs/proposal_fee. */
const PROPOSAL_TARIFFS_DOC = "configs/proposal_fee";

const PROPOSAL_KINDS = Object.freeze(["REPORT", "COPY_EDIT", "NEW_LESSON"]);

/** Машина статусов: очередь проверки → исход проверки → решение автора. */
const PROPOSAL_STATUS = Object.freeze({
  QUEUED: "QUEUED",
  CHECK_PASSED: "CHECK_PASSED",
  CHECK_FAILED: "CHECK_FAILED",
  CHECK_DELAYED: "CHECK_DELAYED",
  APPROVED: "APPROVED",
  REJECTED: "REJECTED",
});

const SCHEMA_VERSION = 1;

/** Границы тела: тексты отчёта, вопросы предложения, отметки изменённости. */
const MAX_REPORT_TEXT_CHARS = 2000;
const MAX_LESSON_TITLE_CHARS = 200;
const MAX_QUESTIONS_PER_PROPOSAL = 64;
const MAX_QUESTION_TEXT_CHARS = 4000;
const MAX_QUESTION_PAYLOAD_BYTES = 32 * 1024;
const MAX_LANGUAGE_LEVEL = 10;
const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const LANGUAGES = Object.freeze(["ru", "uk", "en"]);

function fail(message) {
  const error = new Error(message);
  error.code = "PROPOSAL_INVALID";
  throw error;
}

function nonBlank(value) {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

function nonNegativeInteger(value) {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function docId(value, name) {
  const id = nonBlank(value);
  if (id === null || !ID_PATTERN.test(id)) fail(`${name} must be a document id`);
  return id;
}

/**
 * Тарифы предложения из `configs/proposal_fee` — или принятые владельцем по умолчанию (AD-33:
 * 0 / 100 × изменённые вопросы / 1000). Испорченный тариф отказывает громко всем подачам:
 * тихое возвращение к умолчанию выглядело бы ценой, которой владельцем не принято.
 */
function normalizeProposalTariffs(data) {
  const source = data && typeof data === "object" && !Array.isArray(data) ? data : null;
  if (!source) return {reportNolics: 0, copyEditPerQuestionNolics: 100, newLessonNolics: 1000, revision: 1};
  const whole = (value, fallback) => {
    const parsed = typeof value === "number" ? value : Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < 0) {
      throw new RangeError(`proposal tariff ${JSON.stringify(value)} is not a whole number`);
    }
    return parsed;
  };
  const revision = whole(source.revision, 0);
  if (revision < 1) throw new RangeError("proposal tariff revision must be a positive integer");
  return {
    reportNolics: whole(source.reportNolics, 0),
    copyEditPerQuestionNolics: whole(source.copyEditPerQuestionNolics, 0),
    newLessonNolics: whole(source.newLessonNolics, 0),
    revision,
  };
}

/**
 * Плата предложения: по виду, без выплат (AD-33). Отчёт бесплатен; правка копии — тариф за
 * каждый изменённый вопрос; новый урок — плоский тариф. Решение автора бесплатно.
 */
function proposalFeeNolics({kind, changedQuestions}, tariffs) {
  if (!PROPOSAL_KINDS.includes(kind)) fail(`unknown proposal kind ${kind}`);
  if (kind === "REPORT") return tariffs.reportNolics;
  if (kind === "NEW_LESSON") return tariffs.newLessonNolics;
  const changed = nonNegativeInteger(changedQuestions);
  if (changed === null || changed < 1) fail("COPY_EDIT fee needs at least one changed question");
  return tariffs.copyEditPerQuestionNolics * changed;
}

/** Вопрос предложения — авторской формы: тело подачи несёт его ровно как редактор собрал. */
function parseProposalQuestion(raw, lessonId, fallbackId) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) fail("proposal question must be an object");
  const id = docId(raw.id || fallbackId, "question.id");
  if (docId(raw.lessonId, "question.lessonId") !== lessonId) {
    fail(`question ${id} does not belong to the proposal lesson`);
  }
  const type = nonBlank(raw.type);
  if (type === null || type.length > 64) fail(`question ${id} needs a type`);
  if (!LANGUAGES.includes(raw.language)) fail(`question ${id} needs a known language`);
  const languageLevel = nonNegativeInteger(raw.languageLevel);
  if (languageLevel === null || languageLevel > MAX_LANGUAGE_LEVEL) {
    fail(`question ${id} languageLevel is out of range`);
  }
  const difficulty = nonBlank(raw.difficulty);
  if (difficulty === null || difficulty.length > 16) fail(`question ${id} needs a difficulty`);
  const order = nonNegativeInteger(raw.order);
  if (order === null) fail(`question ${id} order must be a whole number`);
  const text = nonBlank(raw.text);
  if (text === null || text.length > MAX_QUESTION_TEXT_CHARS) fail(`question ${id} text is missing or too long`);
  if (typeof raw.payload !== "string" || raw.payload.trim() === "" ||
    Buffer.byteLength(raw.payload, "utf8") > MAX_QUESTION_PAYLOAD_BYTES) {
    fail(`question ${id} payload is missing or too large`);
  }
  try {
    const parsed = JSON.parse(raw.payload);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      fail(`question ${id} payload must be a JSON object`);
    }
  } catch (_) {
    fail(`question ${id} payload must be JSON`);
  }
  const imagePath = raw.imagePath === undefined ? null : raw.imagePath;
  if (imagePath !== null && typeof imagePath !== "string") fail(`question ${id} imagePath must be a string or null`);
  return {
    id,
    lessonId,
    type,
    language: raw.language,
    languageLevel,
    difficulty,
    order,
    text,
    imagePath,
    payload: raw.payload,
  };
}

function parseProposalQuestions(raw, lessonId) {
  if (!Array.isArray(raw) || raw.length === 0) fail("proposal needs at least one question");
  if (raw.length > MAX_QUESTIONS_PER_PROPOSAL) fail("too many questions in one proposal");
  return raw.map((question, index) => parseProposalQuestion(question, lessonId, `question-${index}`));
}

/** Тело подачи предложения — строгое, по видам; всё, что не названо, не доезжает до записи. */
function parseProposalSubmissionBody(payload) {
  const body = payload && typeof payload === "object" && !Array.isArray(payload) ? payload : null;
  if (!body) fail("proposal submission needs a body");
  const proposalId = docId(body.proposalId, "proposalId");
  if (!PROPOSAL_KINDS.includes(body.kind)) fail("proposal kind is required");
  const base = {proposalId, kind: body.kind};
  if (body.kind === "REPORT") {
    const targetLessonId = docId(body.target && body.target.lessonId, "target.lessonId");
    const text = nonBlank(body.text);
    if (text === null || text.length > MAX_REPORT_TEXT_CHARS) fail("report text is missing or too long");
    const ownVariant = nonBlank(body.ownVariant);
    if (ownVariant !== null && ownVariant.length > MAX_REPORT_TEXT_CHARS) fail("report own variant is too long");
    return {...base, targetLessonId,
      payload: {text, ...(ownVariant !== null ? {ownVariant} : {})}};
  }
  if (body.kind === "COPY_EDIT") {
    const targetLessonId = docId(body.target && body.target.lessonId, "target.lessonId");
    const lessonTitle = nonBlank(body.lessonTitle);
    if (lessonTitle === null || lessonTitle.length > MAX_LESSON_TITLE_CHARS) fail("copy edit needs the lesson title");
    const questions = parseProposalQuestions(body.questions, targetLessonId);
    const questionIds = new Set(questions.map((question) => question.id));
    if (!Array.isArray(body.editedQuestionIds) || body.editedQuestionIds.length === 0) {
      fail("copy edit must name at least one changed question");
    }
    const editedQuestionIds = [];
    for (const id of body.editedQuestionIds) {
      const edited = docId(id, "editedQuestionId");
      if (!questionIds.has(edited)) fail(`edited question ${edited} is not part of the proposal`);
      editedQuestionIds.push(edited);
    }
    return {...base, targetLessonId,
      payload: {lessonTitle, questions, editedQuestionIds}};
  }
  // NEW_LESSON: целевой путь в чужом курсе — каталог, квест, раздел, тема.
  const target = body.target && typeof body.target === "object" ? body.target : {};
  const targetCatalogId = docId(target.catalogId, "target.catalogId");
  const targetQuestId = docId(target.questId, "target.questId");
  const targetSectionId = docId(target.sectionId, "target.sectionId");
  const targetThemeId = docId(target.themeId, "target.themeId");
  const lesson = body.lesson && typeof body.lesson === "object" ? body.lesson : {};
  const lessonId = docId(lesson.id, "lesson.id");
  const lessonTitle = nonBlank(lesson.title);
  if (lessonTitle === null || lessonTitle.length > MAX_LESSON_TITLE_CHARS) fail("new lesson needs a title");
  const questions = parseProposalQuestions(body.questions, lessonId);
  return {...base, targetCatalogId, targetQuestId, targetSectionId, targetThemeId,
    payload: {lesson: {id: lessonId, title: lessonTitle}, questions}};
}

/**
 * Запись предложения как документ. Плата, статус и исход проверки дописывает приёмка/воркер —
 * здесь только то, что выводится из тела и цели.
 */
function buildProposalRecord({proposalId, kind, proposerUid, targetOwnerUid, target, baseRevision, payload}) {
  return {
    schemaVersion: SCHEMA_VERSION,
    proposalId,
    kind,
    proposerUid,
    targetOwnerUid,
    target,
    baseRevision: nonNegativeInteger(baseRevision) === null ? fail("base revision must be a whole number") : baseRevision,
    payload,
    fee: null,
    status: PROPOSAL_STATUS.QUEUED,
    reviewOutcome: null,
    checkedAtMs: 0,
    decidedAtMs: 0,
    createdAtMs: 0,
    updatedAtMs: 0,
  };
}

/**
 * STALE (AD-33): предложение по устаревшей редакции не применяется. Единое правило для всех
 * видов: решение автора сверяет baseRevision с текущей редакцией цели.
 */
function proposalStaleness(proposal, currentRevision) {
  if (!proposal || typeof proposal !== "object") fail("staleness needs a proposal");
  const base = nonNegativeInteger(proposal.baseRevision);
  const current = nonNegativeInteger(currentRevision);
  if (base === null || current === null) fail("staleness needs whole revisions");
  return base !== current;
}

/**
 * Исход проверки предложения: та же правда, что у заявок (review-outcome), но без перевода —
 * предложения не переводятся. PASS без FAIL вопросов и без задержек; FAIL вопрос валит вид;
 * DELAYED этап — задержка, не отказ по качеству.
 */
function buildProposalOutcome({pipeline, verdicts}) {
  if (!pipeline || typeof pipeline !== "object" || pipeline.submissionId === undefined) {
    fail("proposal outcome needs a pipeline record");
  }
  if (pipeline.status === "RUNNING") return null;
  if (pipeline.status !== "QUEST_REVIEWED" && pipeline.status !== "REVIEW_DELAYED") {
    fail(`unknown pipeline status ${pipeline.status}`);
  }
  const latest = new Map();
  for (const record of verdicts || []) {
    if (!record || typeof record !== "object") fail("outcome records must be objects");
    const key = `${record.lessonId}_${record.stage}`;
    const previous = latest.get(key);
    if (!previous || Number(record.attempt) > Number(previous.attempt)) latest.set(key, record);
  }
  const failedQuestions = [];
  const delayedStages = [];
  const passingLessonIds = [];
  for (const [lessonId, lesson] of Object.entries(pipeline.lessons || {})) {
    let failed = false;
    for (const [stage, stageState] of Object.entries(lesson.stages || {})) {
      if (stageState.state === "DELAYED") delayedStages.push({lessonId, stage});
      const verdict = latest.get(`${lessonId}_${stage}`);
      if (!verdict) continue;
      for (const outcome of verdict.questionVerdicts || []) {
        if (outcome && outcome.verdict === "FAIL") {
          failedQuestions.push({lessonId, questionId: outcome.questionId, stage, reason: outcome.reason});
          failed = true;
        }
      }
    }
    if (!failed && delayedStages.every((entry) => entry.lessonId !== lessonId)) passingLessonIds.push(lessonId);
  }
  return {
    pipelineStatus: pipeline.status,
    failedQuestions,
    passingLessonIds,
    delayedStages,
    reviewedAtMs: Number(pipeline.updatedAtMs) || 0,
  };
}

/** Итог проверки в статус записи: FAIL вопрос — вид не прошёл; задержка — не отказ. */
function proposalCheckStatus(outcome) {
  if (!outcome) fail("check status needs an outcome");
  if (outcome.delayedStages.length > 0) return PROPOSAL_STATUS.CHECK_DELAYED;
  if (outcome.failedQuestions.length > 0) return PROPOSAL_STATUS.CHECK_FAILED;
  return PROPOSAL_STATUS.CHECK_PASSED;
}

/**
 * Тело конвейера из записи предложения: один урок-единица и его вопросы. Отчёт проверяется
 * как одна единица «report» — текст отчёта и вариант едут модели данными, как урок.
 */
function buildProposalCheckSource(proposal) {
  if (!proposal || typeof proposal !== "object") fail("check source needs a proposal");
  const target = proposal.target && typeof proposal.target === "object" ? proposal.target : {};
  const kind = proposal.kind;
  if (kind === "REPORT") {
    const text = proposal.payload && typeof proposal.payload.text === "string" ? proposal.payload.text : "";
    if (!text.trim()) fail("report proposal lost its text");
    const variant = proposal.payload && typeof proposal.payload.ownVariant === "string"
      ? proposal.payload.ownVariant : null;
    const unitId = target.lessonId;
    return {
      lessons: [{id: unitId, title: "Отчёт об ошибке"}],
      questions: [{
        id: "report",
        lessonId: unitId,
        type: "REPORT",
        language: "ru",
        languageLevel: 0,
        difficulty: "EASY",
        order: 0,
        text: variant ? `${text}\n\nВариант предложившего:\n${variant}` : text,
        imagePath: null,
        payload: "{}",
      }],
    };
  }
  if (kind === "COPY_EDIT") {
    const title = proposal.payload && typeof proposal.payload.lessonTitle === "string"
      ? proposal.payload.lessonTitle : "";
    return {
      lessons: [{id: target.lessonId, title}],
      questions: (proposal.payload && proposal.payload.questions) || [],
    };
  }
  if (kind === "NEW_LESSON") {
    const lesson = proposal.payload && proposal.payload.lesson;
    return {
      lessons: [{id: lesson && lesson.id, title: lesson && lesson.title}],
      questions: (proposal.payload && proposal.payload.questions) || [],
    };
  }
  fail(`unknown proposal kind ${kind}`);
}

/** Ключ конвейера предложения: своя коллекция, тот же формат идентичности (submission-слот). */
function proposalPipelinePath(proposalId) {
  return `proposal_pipeline/${docId(proposalId, "proposalId")}`;
}

/** Цитата платы предложения (UX-23: вход показывает плату до отправки). Показ — не власть. */
const PROPOSAL_QUOTE_VIEW = "lesson-proposal-fee-quote-v1";

function quoteProposalFee({kind, changedQuestions}, tariffs) {
  return {
    view: PROPOSAL_QUOTE_VIEW,
    kind,
    feeNolics: proposalFeeNolics({kind, changedQuestions}, tariffs),
    tariffRevision: tariffs.revision,
  };
}

module.exports = {
  ID_PATTERN,
  LANGUAGES,
  MAX_QUESTIONS_PER_PROPOSAL,
  MAX_REPORT_TEXT_CHARS,
  PROPOSALS_COLLECTION,
  PROPOSAL_KINDS,
  PROPOSAL_QUOTE_VIEW,
  PROPOSAL_STATUS,
  PROPOSAL_TARIFFS_DOC,
  SCHEMA_VERSION,
  buildProposalCheckSource,
  buildProposalOutcome,
  buildProposalRecord,
  normalizeProposalTariffs,
  parseProposalSubmissionBody,
  proposalCheckStatus,
  proposalFeeNolics,
  proposalPipelinePath,
  proposalStaleness,
  quoteProposalFee,
};
