"use strict";

/**
 * Строгая схема и сборка авто-перевода (AD-32, история 4.15; защищённые диапазоны — AD-27).
 *
 * Ответ модели — только JSON с текстами: detectedLanguage ∈ {ru, uk, en} и переводы вопросов на
 * остальные два языка приложения. Идентификаторы в ответе — ключи выравнивания, которые линтер
 * сверяет с источником поимённо и позиционно; структуру выходного документа строит сборщик из
 * исходного payload вопроса, поэтому ключ ответа, число вариантов и идентичность пропусков в
 * переводе — собственность сервера, а не модели.
 *
 * Защищённые сегменты FillBlank (код/терминология, AD-27) модель вообще не возвращает: сборщик
 * копирует их дословно, а линтер отказывает имени, если перевод потерял защищённый термин там,
 * где источник его нёс (в тексте или в пуле кандидатов).
 */

/** Языки приложения; источник определяется моделью, поля языка у автора нет (история 4.15). */
const APP_LANGUAGES = Object.freeze(["ru", "uk", "en"]);

/** Маркер пропуска в runtime-тексте FillBlank (см. RunnerStateMapper: text.split("___")). */
const BLANK_MARKER = "___";

const CONTENT_TYPES = Object.freeze({
  SURVEY: "Survey",
  SINGLE_CHOICE: "SingleChoice",
  MULTIPLE_CHOICE: "MultipleChoice",
  ORDERING: "Ordering",
  FILL_BLANK: "FillBlank",
});

function parseJsonReply(content) {
  const trimmed = String(content || "").trim();
  // Заборы ```json снимаются только целиком — как в схеме вердиктов этапов проверки.
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/);
  const body = fenced ? fenced[1] : trimmed;
  try {
    return {ok: true, value: JSON.parse(body)};
  } catch (error) {
    return {ok: false, error: "TRANSLATION_SCHEMA_NOT_JSON"};
  }
}

function targetLanguages(detectedLanguage) {
  return APP_LANGUAGES.filter((language) => language !== detectedLanguage);
}

function markerCount(text) {
  return String(text || "").split(BLANK_MARKER).length - 1;
}

function equalsCaseInsensitive(left, right) {
  return String(left).toLowerCase() === String(right).toLowerCase();
}

/**
 * Разбор ответа этапа перевода и сборка переведённых документов вопросов.
 *
 * @param {string} content — сырой ответ модели.
 * @param {Array} questions — вопросы урока из тела заявки (payload — JSON-строка ADR-0003).
 * @returns {{ok: true, translation: {detectedLanguage: string, questions: Object<string, Array>}} |
 *   {ok: false, error: string}} — questions сгруппированы по целевому языку.
 */
function parseTranslationReply(content, {questions} = {}) {
  const parsed = parseJsonReply(content);
  if (!parsed.ok) return parsed;
  const root = parsed.value;
  if (!root || typeof root !== "object" || Array.isArray(root)) {
    return {ok: false, error: "TRANSLATION_SCHEMA_NOT_OBJECT"};
  }
  if (typeof root.detectedLanguage !== "string" ||
    !APP_LANGUAGES.includes(root.detectedLanguage.toLowerCase()) ||
    root.detectedLanguage !== root.detectedLanguage.toLowerCase()) {
    return {ok: false, error: "TRANSLATION_LANGUAGE_UNKNOWN"};
  }
  const detectedLanguage = root.detectedLanguage;
  const targets = targetLanguages(detectedLanguage);
  if (!root.translations || typeof root.translations !== "object" ||
    Array.isArray(root.translations)) {
    return {ok: false, error: "TRANSLATION_SCHEMA_NO_TRANSLATIONS"};
  }
  const replyKeys = Object.keys(root.translations).sort();
  const expectedKeys = [...targets].sort();
  if (replyKeys.length !== expectedKeys.length ||
    replyKeys.some((key, index) => key !== expectedKeys[index])) {
    return {ok: false, error: "TRANSLATION_LANGUAGE_KEYS_INVALID"};
  }

  const sources = [];
  for (const question of questions || []) {
    let payload;
    try {
      payload = JSON.parse(String(question.payload || ""));
    } catch (error) {
      return {ok: false, error: "TRANSLATION_SOURCE_PAYLOAD_INVALID"};
    }
    if (!payload || typeof payload !== "object" || !Object.values(CONTENT_TYPES).includes(payload.type)) {
      return {ok: false, error: "TRANSLATION_SOURCE_PAYLOAD_INVALID"};
    }
    sources.push({question, payload});
  }
  if (sources.length === 0) return {ok: false, error: "TRANSLATION_SOURCE_PAYLOAD_INVALID"};

  const assembled = {};
  for (const language of targets) {
    const entries = root.translations[language];
    if (!Array.isArray(entries) || entries.length === 0) {
      return {ok: false, error: "TRANSLATION_LANGUAGE_EMPTY"};
    }
    const byId = new Map();
    for (const entry of entries) {
      if (!entry || typeof entry !== "object") return {ok: false, error: "TRANSLATION_SCHEMA_BAD_ENTRY"};
      if (typeof entry.questionId !== "string" || !entry.questionId.trim()) {
        return {ok: false, error: "TRANSLATION_SCHEMA_QUESTION_ID_MISSING"};
      }
      if (byId.has(entry.questionId)) return {ok: false, error: "TRANSLATION_SCHEMA_QUESTION_DUPLICATED"};
      byId.set(entry.questionId, entry);
    }
    const expectedIds = sources.map((source) => source.question.id);
    const missing = expectedIds.filter((id) => !byId.has(id));
    if (missing.length > 0) return {ok: false, error: "TRANSLATION_SCHEMA_QUESTION_UNCOVERED"};
    const foreign = [...byId.keys()].filter((id) => !expectedIds.includes(id));
    if (foreign.length > 0) return {ok: false, error: "TRANSLATION_SCHEMA_QUESTION_FOREIGN"};

    const documents = [];
    for (const source of sources) {
      const entry = byId.get(source.question.id);
      const check = lintQuestionEntry(entry, source);
      if (!check.ok) return check;
      documents.push(assembleTranslatedQuestion(source, entry, language));
    }
    assembled[language] = documents;
  }

  return {ok: true, translation: {detectedLanguage, questions: assembled}};
}

/** Текстовые правила одного вопроса: текст, маркеры пропусков, списки по типу, защита AD-27. */
function lintQuestionEntry(entry, source) {
  const {payload} = source;
  if (typeof entry.text !== "string" || !entry.text.trim()) {
    return {ok: false, error: "TRANSLATION_STRUCTURE_TEXT_MISSING"};
  }
  if (payload.type === CONTENT_TYPES.FILL_BLANK &&
    markerCount(entry.text) !== markerCount(payload.text)) {
    return {ok: false, error: "TRANSLATION_STRUCTURE_MARKERS"};
  }
  const sourceInfo = payload.info === undefined || payload.info === null ? null : payload.info;
  const replyInfo = entry.info === undefined || entry.info === null ? null : entry.info;
  if (sourceInfo === null) {
    if (replyInfo !== null) return {ok: false, error: "TRANSLATION_STRUCTURE_INFO"};
  } else if (typeof replyInfo !== "string" || !replyInfo.trim()) {
    return {ok: false, error: "TRANSLATION_STRUCTURE_INFO"};
  }

  if (payload.type === CONTENT_TYPES.SURVEY ||
    payload.type === CONTENT_TYPES.SINGLE_CHOICE ||
    payload.type === CONTENT_TYPES.MULTIPLE_CHOICE) {
    const check = lintTextList(entry.options, payload.options, "OPTION");
    if (!check.ok) return check;
  } else if (payload.type === CONTENT_TYPES.ORDERING) {
    const check = lintTextList(entry.items, payload.items, "ITEM");
    if (!check.ok) return check;
  } else if (payload.type === CONTENT_TYPES.FILL_BLANK) {
    const check = lintTextList(entry.candidates, payload.candidates, "CANDIDATE");
    if (!check.ok) return check;
    const segments = Array.isArray(payload.protectedTextSegments) ? payload.protectedTextSegments : [];
    // Защищённый термин в пуле остаётся дословным: перевод кандидата-термина — именованный отказ.
    for (let index = 0; index < payload.candidates.length; index++) {
      const sourceText = payload.candidates[index].text;
      if (segments.some((segment) => equalsCaseInsensitive(segment, sourceText)) &&
        entry.candidates[index].text !== sourceText) {
        return {ok: false, error: "TRANSLATION_PROTECTED_CANDIDATE_CHANGED"};
      }
    }
    // Защищённый термин, видимый в тексте источника, виден и в переводе — тем же словом.
    for (const segment of segments) {
      if (String(payload.text || "").includes(segment) && !entry.text.includes(segment)) {
        return {ok: false, error: "TRANSLATION_PROTECTED_SEGMENT_MISSING"};
      }
    }
  }
  return {ok: true};
}

/** Список {id, text} по типу: то же число, те же id в том же порядке, тексты непусты. */
function lintTextList(replyList, sourceList, kind) {
  if (!Array.isArray(replyList)) {
    return {ok: false, error: `TRANSLATION_STRUCTURE_${kind}_LIST_MISSING`};
  }
  if (replyList.length !== sourceList.length) {
    return {ok: false, error: `TRANSLATION_STRUCTURE_${kind}_COUNT`};
  }
  for (let index = 0; index < sourceList.length; index++) {
    const reply = replyList[index];
    if (!reply || typeof reply !== "object" || reply.id !== sourceList[index].id) {
      return {ok: false, error: `TRANSLATION_STRUCTURE_${kind}_ID`};
    }
    if (typeof reply.text !== "string" || !reply.text.trim()) {
      return {ok: false, error: `TRANSLATION_STRUCTURE_${kind}_TEXT`};
    }
  }
  return {ok: true};
}

/** Переведённый документ вопроса: структура — из источника, тексты — из проверенного ответа. */
function assembleTranslatedQuestion(source, entry, language) {
  const {question, payload} = source;
  const translated = {...payload, text: entry.text.trim()};
  if (payload.info !== undefined && payload.info !== null) {
    translated.info = String(entry.info).trim();
  }
  if (Array.isArray(payload.options)) {
    translated.options = payload.options.map((option, index) =>
      ({...option, text: entry.options[index].text.trim()}));
  }
  if (Array.isArray(payload.items)) {
    translated.items = payload.items.map((item, index) => ({...item, text: entry.items[index].text.trim()}));
  }
  if (Array.isArray(payload.candidates)) {
    translated.candidates = payload.candidates.map((candidate, index) =>
      ({...candidate, text: entry.candidates[index].text.trim()}));
  }
  // protectedTextSegments, blanks, correct*, allowMultiple, difficulty, imageUrl, id — уже в
  // {...payload} и ответом не перезаписываются: защита AD-27 переживает перевод дословно.
  return {
    id: translatedQuestionId(question.id, language),
    draftId: question.draftId,
    lessonId: question.lessonId,
    type: question.type,
    language,
    languageLevel: question.languageLevel,
    difficulty: question.difficulty,
    order: question.order,
    text: translated.text,
    imagePath: question.imagePath === undefined ? null : question.imagePath,
    payload: JSON.stringify(translated),
    updatedAtMs: question.updatedAtMs,
  };
}

/** Суффикс языка по образцу индексного translatedQuestionId: q1 → q1__en. */
function translatedQuestionId(sourceQuestionId, language) {
  const suffix = `__${language}`;
  return sourceQuestionId.endsWith(suffix) ? sourceQuestionId : `${sourceQuestionId}${suffix}`;
}

module.exports = {
  APP_LANGUAGES,
  BLANK_MARKER,
  CONTENT_TYPES,
  parseTranslationReply,
  targetLanguages,
  translatedQuestionId,
};
