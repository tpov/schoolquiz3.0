"use strict";

/**
 * Авторский исход модельной проверки (AD-32, история 4.16, UX-6).
 *
 * Воркер, доведя конвейер до терминала, вычисляет исход из неизменяемых записей (вердикты и
 * переводы) и кладёт его в заявку; публикация (index.js) читает этот исход и решает — публиковать
 * прошедшие уроки, отказать или объявить задержку. Модуль чистый: Firestore не касается.
 *
 * Правила UX-6: урок с FAIL вопросом не переводится и не публикуется; прошедший урок публикуется
 * независимо от соседей; отложенный урок — «Проверка задерживается», никогда не отказ по качеству.
 */

const PIPELINE_QUEST_REVIEWED = "QUEST_REVIEWED";
const PIPELINE_REVIEW_DELAYED = "REVIEW_DELAYED";

/** Этапы, чьи вердикты могут завалить вопрос; перевод вердиктов не пишет. */
const CHECK_STAGES = Object.freeze(["WORDS", "FACTS"]);

function fail(message) {
  throw new RangeError(message);
}

/**
 * Исход заявки из записей конвейера.
 *
 * @param {object} pipeline — документ review_pipeline/{submissionId}.
 * @param {Array} verdicts — записи вердиктов этого submission (WORDS/FACTS).
 * @param {Array} translations — записи переводов этого submission.
 * @returns {object|null} — null, пока конвейер бежит; иначе
 *   {pipelineStatus, failedQuestions, detectedLanguage, passingLessonIds, delayedStages, reviewedAtMs}.
 */
function buildSubmissionOutcome({pipeline, verdicts, translations}) {
  if (!pipeline || typeof pipeline !== "object" || typeof pipeline.submissionId !== "string") {
    fail("outcome needs a pipeline record");
  }
  if (pipeline.status === "RUNNING") return null;
  if (pipeline.status !== PIPELINE_QUEST_REVIEWED && pipeline.status !== PIPELINE_REVIEW_DELAYED) {
    fail(`unknown pipeline status ${pipeline.status}`);
  }
  const verdictsByLesson = groupLatestByLesson(verdicts, pipeline.submissionId);
  const translationsByLesson = groupLatestByLesson(translations, pipeline.submissionId);

  const failedQuestions = [];
  const delayedStages = [];
  const passingLessonIds = [];
  let detectedLanguage = null;
  for (const [lessonId, lesson] of Object.entries(pipeline.lessons || {})) {
    for (const stage of CHECK_STAGES) {
      const stageState = lesson.stages && lesson.stages[stage];
      if (!stageState) fail(`pipeline lesson ${lessonId} misses stage ${stage}`);
      if (stageState.state === "DELAYED") delayedStages.push({lessonId, stage});
      const verdict = verdictsByLesson.get(`${lessonId}_${stage}`);
      if (!verdict) continue;
      for (const outcome of verdict.questionVerdicts || []) {
        if (outcome && outcome.verdict === "FAIL") {
          failedQuestions.push({lessonId, questionId: outcome.questionId, stage, reason: outcome.reason});
        }
      }
    }
    const translateState = lesson.stages && lesson.stages.TRANSLATE;
    if (!translateState) fail(`pipeline lesson ${lessonId} misses stage TRANSLATE`);
    if (translateState.state === "DELAYED") delayedStages.push({lessonId, stage: "TRANSLATE"});
    const translation = translationsByLesson.get(`${lessonId}_TRANSLATE`);
    if (translation) {
      passingLessonIds.push(lessonId);
      if (detectedLanguage === null) detectedLanguage = translation.detectedLanguage;
    }
  }
  return {
    pipelineStatus: pipeline.status,
    failedQuestions,
    detectedLanguage,
    passingLessonIds,
    delayedStages,
    reviewedAtMs: Number(pipeline.updatedAtMs) || 0,
  };
}

/** Последняя попытка каждого (урок, этап): вердикты неизменяемы, попыток несколько. */
function groupLatestByLesson(records, submissionId) {
  const latest = new Map();
  for (const record of records || []) {
    if (!record || typeof record !== "object") fail("outcome records must be objects");
    if (record.submissionId !== submissionId) continue;
    const key = `${record.lessonId}_${record.stage}`;
    const previous = latest.get(key);
    if (!previous || Number(record.attempt) > Number(previous.attempt)) latest.set(key, record);
  }
  return latest;
}

module.exports = {
  CHECK_STAGES,
  PIPELINE_QUEST_REVIEWED,
  PIPELINE_REVIEW_DELAYED,
  buildSubmissionOutcome,
};
