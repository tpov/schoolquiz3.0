"use strict";

/**
 * Конвейер проверки (AD-32): чистая машина состояний одного квеста на проверке (история 4.12).
 *
 * Модуль без Firestore и без модели: воркер ведёт по нему документ `review_pipeline/{submissionId}`
 * как единственный возобновляемый чекпойнт, а неизменяемые вердикты пишет отдельными документами.
 * Идентичность задания — (submission, lesson, stage, attempt); повтор задания вердикта не плодит.
 *
 * Правила сбоя (AD-8/AD-32): неверный формат ответа модели возвращается ей именованной ошибкой,
 * не более трёх попыток, затем урок получает REVIEW_DELAYED — не REJECTED по качеству и не
 * угаданный вердикт. Сбой транспорта модели — не попытка вовсе: урок остаётся на очереди.
 */

const SCHEMA_VERSION = 1;

/** Этапы конвейера в порядке исполнения (AD-32: «Слова» → «Факты» → перевод, 4.15). */
const STAGE_PLAN = Object.freeze(["WORDS", "FACTS", "TRANSLATE"]);

const MAX_ATTEMPTS = 3;

const STAGE = Object.freeze({
  PENDING: "PENDING",
  DONE: "DONE",
  DELAYED: "DELAYED",
  // Перевод неприменим: урок с FAIL вопросом возвращается автору, переводить нечего (4.15).
  SKIPPED: "SKIPPED",
});

/** Этапы, без которых перевод не запускается: судить можно только прошедшее (AD-32). */
const TRANSLATE_PREREQUISITES = Object.freeze(["WORDS", "FACTS"]);

/** Вердикты существуют только у проверочных этапов; перевод пишет свою запись (4.15). */
const CHECK_STAGES = Object.freeze(["WORDS", "FACTS"]);

const QUEST_STATUS = Object.freeze({
  RUNNING: "RUNNING",
  REVIEWED: "QUEST_REVIEWED",
  DELAYED: "REVIEW_DELAYED",
});

/** Имя документа вердикта: детерминировано идентичностью задания, идемпотентность — само имя. */
function verdictDocumentId({submissionId, lessonId, stage, attempt}) {
  for (const [name, value] of [["submissionId", submissionId], ["lessonId", lessonId], ["stage", stage], ["attempt", attempt]]) {
    if (typeof value !== "string" && typeof value !== "number") {
      throw new RangeError(`verdict identity ${name} is missing`);
    }
    if (String(value).includes("/")) throw new RangeError(`verdict identity ${name} contains a separator`);
  }
  const attemptNumber = Number(attempt);
  if (!Number.isInteger(attemptNumber) || attemptNumber < 1) {
    throw new RangeError(`verdict attempt must be a positive integer, got ${JSON.stringify(attempt)}`);
  }
  if (!CHECK_STAGES.includes(stage)) throw new RangeError(`unknown check stage ${stage}`);
  return `${submissionId}_${lessonId}_${stage}_${attemptNumber}`;
}

/** Свежее состояние конвейера: все уроки — все этапы PENDING, включая перевод (4.15). */
function createPipelineState({submissionId, ownerUid, questId, questTitle, categoryPath, revision, lessonIds}) {
  if (!submissionId || !ownerUid || !questId) throw new RangeError("pipeline identity is required");
  if (!Array.isArray(lessonIds) || lessonIds.length === 0) throw new RangeError("pipeline needs lessons");
  const lessons = {};
  for (const lessonId of lessonIds) {
    lessons[lessonId] = {
      overall: "PENDING",
      stages: Object.fromEntries(STAGE_PLAN.map((stage) => [stage, freshStage()])),
    };
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    submissionId,
    ownerUid,
    questId,
    questTitle: String(questTitle || ""),
    categoryPath: String(categoryPath || ""),
    revision: Number(revision) || 0,
    status: QUEST_STATUS.RUNNING,
    lessons,
    createdAtMs: 0,
    updatedAtMs: 0,
  };
}

function freshStage() {
  return {state: STAGE.PENDING, attempts: 0, errors: [], verdictId: null};
}

/**
 * Следующее задание конвейера — или его итог.
 *
 * Порядок: уроки как в заявке, внутри урока — план этапов. Задание живо, пока этап PENDING и
 * попыток не исчерпано; DELAYED и SKIPPED этапы пропускаются. Перевод требует завершённых
 * проверок: урок с задержанной или непройденной проверкой переводом не занимается. Урок без
 * живых заданий получает итог.
 */
function nextJob(state) {
  for (const [lessonId, lesson] of Object.entries(state.lessons)) {
    for (const stage of STAGE_PLAN) {
      const stageState = lesson.stages[stage];
      if (!stageState) throw new RangeError(`unknown stage ${stage} in lesson ${lessonId}`);
      if (stage === "TRANSLATE" &&
        !TRANSLATE_PREREQUISITES.every((prerequisite) =>
          lesson.stages[prerequisite] && lesson.stages[prerequisite].state === STAGE.DONE)) {
        continue;
      }
      if (stageState.state === STAGE.PENDING && stageState.attempts < MAX_ATTEMPTS) {
        return {lessonId, stage, attempt: stageState.attempts + 1};
      }
    }
  }
  return {done: true, status: questStatus(state)};
}

function questStatus(state) {
  const lessons = Object.values(state.lessons);
  const delayed = lessons.some((lesson) =>
    STAGE_PLAN.some((stage) => lesson.stages[stage].state === STAGE.DELAYED));
  if (delayed) return QUEST_STATUS.DELAYED;
  // SKIPPED перевод — завершён без перевода: урок с FAIL вопросом вернётся автору (4.16).
  const settled = (stageState) => stageState.state === STAGE.DONE || stageState.state === STAGE.SKIPPED;
  const allDone = lessons.every((lesson) =>
    STAGE_PLAN.every((stage) => settled(lesson.stages[stage])));
  return allDone ? QUEST_STATUS.REVIEWED : QUEST_STATUS.RUNNING;
}

/**
 * Именованная ошибка линтера в лентописи этапа: попытка потрачена, задание вернётся (пока
 * попытки есть) или урок задержится. Сюда же едут именованные отказы самого задания.
 */
function applyStageError(state, lessonId, stage, attempt, error) {
  const stageState = requireStage(state, lessonId, stage);
  const nextAttempt = requireAttempt(attempt);
  if (nextAttempt !== stageState.attempts + 1) {
    throw new RangeError(`stage ${stage} expects attempt ${stageState.attempts + 1}, got ${nextAttempt}`);
  }
  const exhausted = nextAttempt >= MAX_ATTEMPTS;
  stageState.attempts = nextAttempt;
  stageState.errors.push(String(error));
  stageState.state = exhausted ? STAGE.DELAYED : STAGE.PENDING;
  return state;
}

/** Вердикт записан: этап закрыт, конвейер помнит имя документа. */
function applyVerdict(state, lessonId, stage, verdictId) {
  const stageState = requireStage(state, lessonId, stage);
  stageState.state = STAGE.DONE;
  stageState.verdictId = String(verdictId);
  return state;
}

/**
 * Перевод неприменим (4.15): в уроке есть FAIL вопрос, воркер не вызывает модель. Отдельное
 * состояние, а не DONE с флагом: итог конвейера обязан отличать «переведено» от «не переводится»,
 * не изобретая вердиктоподобную запись об уроке, который автор переработает.
 */
function applyStageSkip(state, lessonId, stage) {
  const stageState = requireStage(state, lessonId, stage);
  if (stageState.state === STAGE.DONE) return state;
  stageState.state = STAGE.SKIPPED;
  return state;
}

/**
 * Неизменяемая запись перевода (4.15): связывает ревизию, модель, версию промпта, определённый
 * язык источника и собранные документы переведённых вопросов по каждому целевому языку.
 */
function buildTranslationRecord({submissionId, lessonId, attempt, revision, model, promptVersion,
  detectedLanguage, questions}) {
  const languages = Object.keys(questions || {});
  if (languages.length === 0) throw new RangeError("translation must name target languages");
  for (const language of languages) {
    if (!Array.isArray(questions[language]) || questions[language].length === 0) {
      throw new RangeError(`translation for ${language} must name questions`);
    }
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    submissionId,
    lessonId,
    stage: "TRANSLATE",
    attempt: requireAttempt(attempt),
    revision: Number(revision) || 0,
    model: String(model),
    promptVersion: String(promptVersion),
    detectedLanguage: String(detectedLanguage),
    questions,
  };
}

/**
 * Неизменяемая запись вердикта: модель, этап, версия промпта, источники, основания и
 * по-вопросные исходы — всё, что «Решения» (4.17) обязаны показать без чтения чего-либо ещё.
 */
function buildVerdictRecord({submissionId, lessonId, stage, attempt, revision, model, promptVersion,
  questionVerdicts, sources, rationale}) {
  if (!Array.isArray(questionVerdicts) || questionVerdicts.length === 0) {
    throw new RangeError("verdict must name per-question outcomes");
  }
  for (const verdict of questionVerdicts) {
    if (!verdict || typeof verdict.questionId !== "string" || !verdict.questionId) {
      throw new RangeError("every question outcome needs a questionId");
    }
    if (verdict.verdict !== "PASS" && verdict.verdict !== "FAIL") {
      throw new RangeError(`question ${verdict.questionId} verdict must be PASS or FAIL`);
    }
    if (typeof verdict.reason !== "string" || !verdict.reason.trim()) {
      throw new RangeError(`question ${verdict.questionId} needs a reason`);
    }
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    submissionId,
    lessonId,
    stage,
    attempt: requireAttempt(attempt),
    revision: Number(revision) || 0,
    model: String(model),
    promptVersion: String(promptVersion),
    sources: (sources || []).map(String),
    rationale: String(rationale || ""),
    questionVerdicts: questionVerdicts.map((verdict) => ({
      questionId: verdict.questionId,
      verdict: verdict.verdict,
      reason: verdict.reason,
    })),
  };
}

/** Имя документа перевода: та же идентичность задания, отдельная коллекция (4.15). */
function translationDocumentId({submissionId, lessonId, attempt}) {
  for (const [name, value] of [["submissionId", submissionId], ["lessonId", lessonId], ["attempt", attempt]]) {
    if (typeof value !== "string" && typeof value !== "number") {
      throw new RangeError(`translation identity ${name} is missing`);
    }
    if (String(value).includes("/")) throw new RangeError(`translation identity ${name} contains a separator`);
  }
  const attemptNumber = Number(attempt);
  if (!Number.isInteger(attemptNumber) || attemptNumber < 1) {
    throw new RangeError(`translation attempt must be a positive integer, got ${JSON.stringify(attempt)}`);
  }
  return `${submissionId}_${lessonId}_TRANSLATE_${attemptNumber}`;
}

function requireStage(state, lessonId, stage) {
  const lesson = state.lessons[lessonId];
  if (!lesson) throw new RangeError(`unknown lesson ${lessonId}`);
  const stageState = lesson.stages[stage];
  if (!stageState) throw new RangeError(`unknown stage ${stage}`);
  return stageState;
}

function requireAttempt(attempt) {
  const value = Number(attempt);
  if (!Number.isInteger(value) || value < 1) {
    throw new RangeError(`attempt must be a positive integer, got ${JSON.stringify(attempt)}`);
  }
  return value;
}

module.exports = {
  CHECK_STAGES,
  MAX_ATTEMPTS,
  QUEST_STATUS,
  SCHEMA_VERSION,
  STAGE,
  STAGE_PLAN,
  TRANSLATE_PREREQUISITES,
  applyStageError,
  applyStageSkip,
  applyVerdict,
  buildTranslationRecord,
  buildVerdictRecord,
  createPipelineState,
  nextJob,
  questStatus,
  translationDocumentId,
  verdictDocumentId,
};
