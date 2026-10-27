"use strict";

/**
 * Строгая схема по-вопросных вердиктов этапов проверки (AD-32, истории 4.13/4.14).
 *
 * Ответ модели — только JSON; линтер отказывает именованной ошибкой при любом отклонении:
 * не-JSON, лишние или отсутствующие вопросы, неизвестный вердикт, пустое основание. Урок текстом
 * модели — данные; модель судит и никогда не возвращает правок авторского текста: поля правки в
 * схеме нет вовсе, поэтому «поправить» она не может даже по собственному желанию.
 *
 * При сомнении — отказ (решение владельца D2): вердикт DOUBT приравнен к FAIL с названной
 * причиной, отдельного «может быть» в публикации не существует.
 */

function parseJsonReply(content) {
  const trimmed = String(content || "").trim();
  // Модели любят оборачивать JSON в ```-заборы: снимаем их, но только целиком, не внутри.
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/);
  const body = fenced ? fenced[1] : trimmed;
  try {
    return {ok: true, value: JSON.parse(body)};
  } catch (error) {
    return {ok: false, error: "REVIEW_SCHEMA_NOT_JSON"};
  }
}

/**
 * Разбор ответа этапа.
 *
 * @param {string} content — сырой ответ модели.
 * @param {{questions: Array, requireSources: boolean}} plan — вопросы урока (полное покрытие)
 *   и требование источников («Факты» требует, «Слова» — нет).
 * @returns {{ok: true, verdict: object} | {ok: false, error: string}}
 */
function parseVerdictReply(content, {questions, requireSources = false} = {}) {
  const parsed = parseJsonReply(content);
  if (!parsed.ok) return parsed;
  const root = parsed.value;
  if (!root || typeof root !== "object" || Array.isArray(root)) {
    return {ok: false, error: "REVIEW_SCHEMA_NOT_OBJECT"};
  }
  const entries = root.questions;
  if (!Array.isArray(entries) || entries.length === 0) {
    return {ok: false, error: "REVIEW_SCHEMA_NO_QUESTIONS"};
  }
  const byId = new Map();
  for (const entry of entries) {
    if (!entry || typeof entry !== "object") return {ok: false, error: "REVIEW_SCHEMA_BAD_ENTRY"};
    if (typeof entry.questionId !== "string" || !entry.questionId.trim()) {
      return {ok: false, error: "REVIEW_SCHEMA_QUESTION_ID_MISSING"};
    }
    if (byId.has(entry.questionId)) return {ok: false, error: "REVIEW_SCHEMA_QUESTION_DUPLICATED"};
    if (entry.verdict !== "PASS" && entry.verdict !== "FAIL") {
      return {ok: false, error: "REVIEW_SCHEMA_UNKNOWN_VERDICT"};
    }
    if (typeof entry.reason !== "string" || !entry.reason.trim()) {
      return {ok: false, error: "REVIEW_SCHEMA_REASON_MISSING"};
    }
    byId.set(entry.questionId, {
      questionId: entry.questionId,
      verdict: entry.verdict,
      reason: entry.reason.trim(),
    });
  }
  const expected = (questions || []).map((question) => String(question.id));
  const missing = expected.filter((id) => !byId.has(id));
  if (missing.length > 0) return {ok: false, error: "REVIEW_SCHEMA_QUESTION_UNCOVERED"};
  const extra = [...byId.keys()].filter((id) => !expected.includes(id));
  if (extra.length > 0) return {ok: false, error: "REVIEW_SCHEMA_QUESTION_FOREIGN"};

  const sources = root.sources;
  if (!Array.isArray(sources)) return {ok: false, error: "REVIEW_SCHEMA_SOURCES_MISSING"};
  for (const source of sources) {
    if (typeof source !== "string" || !/^https:\/\//.test(source)) {
      return {ok: false, error: "REVIEW_SCHEMA_SOURCE_NOT_HTTPS"};
    }
  }
  if (requireSources && sources.length === 0) {
    return {ok: false, error: "REVIEW_SCHEMA_SOURCES_EMPTY"};
  }
  const rationale = typeof root.rationale === "string" ? root.rationale.trim() : "";
  if (!rationale) return {ok: false, error: "REVIEW_SCHEMA_RATIONALE_MISSING"};

  return {
    ok: true,
    verdict: {questionVerdicts: [...byId.values()], sources, rationale},
  };
}

module.exports = {parseVerdictReply};
