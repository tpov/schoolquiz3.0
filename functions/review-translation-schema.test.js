"use strict";

// Схема и сборка авто-перевода (история 4.15): строгий разбор ответа модели, структурные
// инварианты (число вариантов, ключ, маркеры пропусков, защита AD-27) и дословная сборка
// документов переведённых вопросов из исходного payload.

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  APP_LANGUAGES,
  parseTranslationReply,
  targetLanguages,
  translatedQuestionId,
} = require("./review-translation-schema");

function sourceQuestions() {
  return [
    {
      id: "q1",
      draftId: "draft-1",
      lessonId: "l1",
      type: "SINGLE_CHOICE",
      language: "ru",
      languageLevel: 1,
      difficulty: "EASY",
      order: 0,
      text: "Сколько будет 2+2?",
      imagePath: null,
      payload: JSON.stringify({
        type: "SingleChoice", id: "q1", difficulty: "EASY", text: "Сколько будет 2+2?",
        imageUrl: null, options: [{id: "a", text: "три"}, {id: "b", text: "четыре"}],
        correctOptionId: "b", info: "базовое сложение",
      }),
      updatedAtMs: 99,
    },
    {
      id: "q2",
      draftId: "draft-1",
      lessonId: "l1",
      type: "FILL_BLANK",
      language: "ru",
      languageLevel: 1,
      difficulty: "HARD",
      order: 1,
      text: "Kotlin ___ JVM",
      imagePath: null,
      payload: JSON.stringify({
        type: "FillBlank", id: "q2", difficulty: "HARD", text: "Kotlin ___ JVM", imageUrl: null,
        blanks: [{id: "b1", correctCandidateId: "c1"}],
        candidates: [{id: "c1", text: "Kotlin"}, {id: "c2", text: "Scala"}],
        protectedTextSegments: ["Kotlin"], info: null,
      }),
      updatedAtMs: 99,
    },
  ];
}

function modelReply() {
  return {
    detectedLanguage: "ru",
    translations: {
      uk: [
        {questionId: "q1", text: "Скільки буде 2+2?", info: "базове додавання",
          options: [{id: "a", text: "три"}, {id: "b", text: "чотири"}]},
        {questionId: "q2", text: "Kotlin ___ JVM", candidates: [{id: "c1", text: "Kotlin"}, {id: "c2", text: "Скала"}]},
      ],
      en: [
        {questionId: "q1", text: "How much is 2+2?", info: "basic addition",
          options: [{id: "a", text: "three"}, {id: "b", text: "four"}]},
        {questionId: "q2", text: "Kotlin ___ JVM", candidates: [{id: "c1", text: "Kotlin"}, {id: "c2", text: "Scala"}]},
      ],
    },
  };
}

test("app languages are ru/uk/en; detection excludes the source from targets", () => {
  assert.deepEqual(APP_LANGUAGES, ["ru", "uk", "en"]);
  assert.deepEqual(targetLanguages("ru"), ["uk", "en"]);
  assert.deepEqual(targetLanguages("en"), ["ru", "uk"]);
});

test("valid reply assembles both languages with structure from the source", () => {
  const result = parseTranslationReply(JSON.stringify(modelReply()), {questions: sourceQuestions()});
  assert.equal(result.ok, true);
  assert.equal(result.translation.detectedLanguage, "ru");
  assert.deepEqual(Object.keys(result.translation.questions).sort(), ["en", "uk"]);

  const uk1 = result.translation.questions.uk[0];
  assert.equal(uk1.id, "q1__uk");
  assert.equal(uk1.language, "uk");
  assert.equal(uk1.type, "SINGLE_CHOICE");
  assert.equal(uk1.text, "Скільки буде 2+2?");
  assert.equal(uk1.draftId, "draft-1");
  assert.equal(uk1.updatedAtMs, 99);
  const payload1 = JSON.parse(uk1.payload);
  assert.equal(payload1.type, "SingleChoice");
  assert.deepEqual(payload1.options, [{id: "a", text: "три"}, {id: "b", text: "чотири"}]);
  // Ключ ответа — из источника, не из ответа модели.
  assert.equal(payload1.correctOptionId, "b");
  assert.equal(payload1.info, "базове додавання");

  const uk2 = result.translation.questions.uk[1];
  const payload2 = JSON.parse(uk2.payload);
  assert.equal(payload2.text, "Kotlin ___ JVM");
  assert.deepEqual(payload2.blanks, [{id: "b1", correctCandidateId: "c1"}]);
  // Защищённый термин пережил перевод дословно и в пуле, и в тексте (AD-27).
  assert.deepEqual(payload2.protectedTextSegments, ["Kotlin"]);
  assert.deepEqual(payload2.candidates, [{id: "c1", text: "Kotlin"}, {id: "c2", text: "Скала"}]);
  // Источник без пояснения — перевод тоже без пояснения (null, не выдуманный текст).
  assert.equal(payload2.info, null);
});

test("fenced JSON is unwrapped whole", () => {
  const fenced = "```json\n" + JSON.stringify(modelReply()) + "\n```";
  const result = parseTranslationReply(fenced, {questions: sourceQuestions()});
  assert.equal(result.ok, true);
});

test("translated question id follows the __lang suffix rule", () => {
  assert.equal(translatedQuestionId("q1", "en"), "q1__en");
  assert.equal(translatedQuestionId("q1__en", "en"), "q1__en");
  assert.equal(translatedQuestionId("q1__en", "uk"), "q1__en__uk");
});

function refusal(mutate, questions = sourceQuestions()) {
  const reply = modelReply();
  mutate(reply);
  return parseTranslationReply(JSON.stringify(reply), {questions});
}

test("named refusals: shell and languages", () => {
  assert.equal(parseTranslationReply("not json", {questions: sourceQuestions()}).error, "TRANSLATION_SCHEMA_NOT_JSON");
  assert.equal(parseTranslationReply("[]", {questions: sourceQuestions()}).error, "TRANSLATION_SCHEMA_NOT_OBJECT");
  assert.equal(refusal((r) => { delete r.detectedLanguage; }).error, "TRANSLATION_LANGUAGE_UNKNOWN");
  assert.equal(refusal((r) => { r.detectedLanguage = "fr"; }).error, "TRANSLATION_LANGUAGE_UNKNOWN");
  assert.equal(refusal((r) => { r.detectedLanguage = "RU"; }).error, "TRANSLATION_LANGUAGE_UNKNOWN");
  assert.equal(refusal((r) => { delete r.translations; }).error, "TRANSLATION_SCHEMA_NO_TRANSLATIONS");
  // Ключи целевых языков — ровно два остальных, без источника и без лишних.
  assert.equal(refusal((r) => { r.translations.ru = r.translations.en; }).error, "TRANSLATION_LANGUAGE_KEYS_INVALID");
  assert.equal(refusal((r) => { delete r.translations.en; }).error, "TRANSLATION_LANGUAGE_KEYS_INVALID");
  assert.equal(refusal((r) => { r.translations.uk = []; }).error, "TRANSLATION_LANGUAGE_EMPTY");
});

test("named refusals: question coverage", () => {
  assert.equal(refusal((r) => { r.translations.uk.pop(); }).error, "TRANSLATION_SCHEMA_QUESTION_UNCOVERED");
  assert.equal(refusal((r) => { r.translations.uk[1].questionId = "qX"; }).error, "TRANSLATION_SCHEMA_QUESTION_UNCOVERED");
  assert.equal(refusal((r) => {
    r.translations.uk.push({...r.translations.uk[0]});
  }).error, "TRANSLATION_SCHEMA_QUESTION_DUPLICATED");
  assert.equal(refusal((r) => {
    r.translations.uk.push({questionId: "qX", text: "x", options: []});
  }).error, "TRANSLATION_SCHEMA_QUESTION_FOREIGN");
  assert.equal(refusal((r) => { r.translations.uk[0].questionId = " "; }).error, "TRANSLATION_SCHEMA_QUESTION_ID_MISSING");
});

test("named refusals: structure keeps counts, ids, markers, info", () => {
  assert.equal(refusal((r) => { r.translations.uk[0].text = " "; }).error, "TRANSLATION_STRUCTURE_TEXT_MISSING");
  assert.equal(refusal((r) => { delete r.translations.uk[0].options; }).error, "TRANSLATION_STRUCTURE_OPTION_LIST_MISSING");
  assert.equal(refusal((r) => { r.translations.uk[0].options = [{id: "a", text: "три"}]; }).error,
    "TRANSLATION_STRUCTURE_OPTION_COUNT");
  assert.equal(refusal((r) => { r.translations.uk[0].options[1].id = "z"; }).error,
    "TRANSLATION_STRUCTURE_OPTION_ID");
  assert.equal(refusal((r) => { r.translations.uk[0].options[1].text = " "; }).error,
    "TRANSLATION_STRUCTURE_OPTION_TEXT");
  assert.equal(refusal((r) => { r.translations.uk[1].text = "Kotlin JVM"; }).error,
    "TRANSLATION_STRUCTURE_MARKERS");
  assert.equal(refusal((r) => { r.translations.uk[1].text = "Kotlin ___ ___ JVM"; }).error,
    "TRANSLATION_STRUCTURE_MARKERS");
  // info: источник без пояснения — перевод не изобретает пояснение.
  assert.equal(refusal((r) => { r.translations.uk[1].info = "нове пояснення"; }).error, "TRANSLATION_STRUCTURE_INFO");
  // info: источник с пояснением — перевод обязан нести пояснение.
  assert.equal(refusal((r) => { r.translations.uk[0].info = null; }).error, "TRANSLATION_STRUCTURE_INFO");
});

test("named refusals: protected terms survive untranslated (AD-27)", () => {
  // Кандидат-термин перевели — отказ.
  assert.equal(refusal((r) => { r.translations.uk[1].candidates[0].text = "Котлін"; }).error,
    "TRANSLATION_PROTECTED_CANDIDATE_CHANGED");
  // Защищённый термин, видимый в тексте источника, исчез из перевода — отказ.
  assert.equal(refusal((r) => { r.translations.uk[1].text = "Котлін ___ JVM"; }).error,
    "TRANSLATION_PROTECTED_SEGMENT_MISSING");
});

test("named refusals: broken source payload", () => {
  const broken = sourceQuestions();
  broken[0].payload = "{not json";
  assert.equal(parseTranslationReply(JSON.stringify(modelReply()), {questions: broken}).error,
    "TRANSLATION_SOURCE_PAYLOAD_INVALID");
  const unknown = sourceQuestions();
  unknown[0].payload = JSON.stringify({type: "Sudoku"});
  assert.equal(parseTranslationReply(JSON.stringify(modelReply()), {questions: unknown}).error,
    "TRANSLATION_SOURCE_PAYLOAD_INVALID");
  assert.equal(parseTranslationReply(JSON.stringify(modelReply()), {questions: []}).error,
    "TRANSLATION_SOURCE_PAYLOAD_INVALID");
});

test("ordering and survey entries lint by their own lists", () => {
  const questions = [
    {
      id: "q3", draftId: "d", lessonId: "l1", type: "ORDERING", language: "ru", languageLevel: 0,
      difficulty: "EASY", order: 0, text: "Порядок", imagePath: null, updatedAtMs: 1,
      payload: JSON.stringify({
        type: "Ordering", id: "q3", difficulty: "EASY", text: "Порядок", imageUrl: null,
        items: [{id: "i1", text: "первый"}, {id: "i2", text: "второй"}],
      }),
    },
    {
      id: "q4", draftId: "d", lessonId: "l1", type: "SURVEY", language: "ru", languageLevel: 0,
      difficulty: "EASY", order: 1, text: "Опрос", imagePath: null, updatedAtMs: 1,
      payload: JSON.stringify({
        type: "Survey", id: "q4", difficulty: "EASY", text: "Опрос", imageUrl: null,
        options: [{id: "a", text: "да"}, {id: "b", text: "нет"}], allowMultiple: true,
      }),
    },
  ];
  const reply = {
    detectedLanguage: "ru",
    translations: {
      uk: [
        {questionId: "q3", text: "Порядок", items: [{id: "i1", text: "перший"}, {id: "i2", text: "другий"}]},
        {questionId: "q4", text: "Опитування", options: [{id: "a", text: "так"}, {id: "b", text: "ні"}]},
      ],
      en: [
        {questionId: "q3", text: "Order", items: [{id: "i1", text: "first"}, {id: "i2", text: "second"}]},
        {questionId: "q4", text: "Survey", options: [{id: "a", text: "yes"}, {id: "b", text: "no"}]},
      ],
    },
  };
  const result = parseTranslationReply(JSON.stringify(reply), {questions});
  assert.equal(result.ok, true);
  const ordering = JSON.parse(result.translation.questions.uk[0].payload);
  assert.deepEqual(ordering.items, [{id: "i1", text: "перший"}, {id: "i2", text: "другий"}]);
  const survey = JSON.parse(result.translation.questions.uk[1].payload);
  assert.equal(survey.allowMultiple, true);
  reply.translations.uk[0].items[1].id = "i9";
  const refused = parseTranslationReply(JSON.stringify(reply), {questions});
  assert.equal(refused.error, "TRANSLATION_STRUCTURE_ITEM_ID");
});
