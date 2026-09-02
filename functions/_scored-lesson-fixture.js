"use strict";

const {seeded} = require("./_seeded-random");
const fixtures = require("./_question-fixtures");
const {keyDocumentPath, questionKeyDocuments} = require("./question-key-store");

/**
 * One published, redacted lesson and one honest play of it — the setup two suites both need.
 *
 * `attempt-wiring.test.js` drives the handler's decision functions over it and
 * `attempt-handler.test.js` drives the whole callable over it, and neither is about building a
 * lesson. What is **not** here is what the play is worth: each suite writes its own `CODE_ANSWER`
 * and percent down as literals, because a shared expectation is an expectation nobody checks — both
 * suites would agree with each other while agreeing with nothing. `lesson-round-trip.test.js`
 * derives the same digits by hand from `Scoring.kt`'s formula, and that is where they come from.
 *
 * The questions, the seed and the answers are `lesson-round-trip.test.js`'s, deliberately: the
 * digits that suite proves are the digits these two assert.
 */

const SEED = 21;
const json = JSON.stringify;

/** One question of every shape that carries an answer, plus a survey that carries none. */
function sourceQuestions(lessonId) {
  return [
    {id: "q-single", lessonId, difficulty: "HARD", payload: json(fixtures.SINGLE)},
    {id: "q-multi", lessonId, difficulty: "HARD", payload: json(fixtures.MULTIPLE)},
    {id: "q-order", lessonId, difficulty: "HARD", payload: json(fixtures.ORDERING)},
    {id: "q-blank", lessonId, difficulty: "HARD", payload: json(fixtures.FILL_BLANK)},
    {id: "q-survey", lessonId, difficulty: "HARD", payload: json(fixtures.SURVEY)},
  ];
}

/** Where `served` puts each question. Everything downstream is indexed by it. */
const AT = {single: 0, multi: 1, order: 2, blank: 3, survey: 4};

/** The re-issued ids the player is shown, for `SEED`. */
const SHOWN = {
  order: {i1: "ri-1", i2: "ri-3", i3: "ri-0", i4: "ri-2"},
  candidate: {c1: "rc-1", c2: "rc-2", c3: "rc-0"},
};

/**
 * The lesson as publication leaves it: the keys, and the public halves that came out of the same
 * `redact` call. One call, so the arrangement the world sees and the one the keys describe are the
 * same arrangement by construction.
 */
function publishLesson(lessonId) {
  const source = sourceQuestions(lessonId);
  const keyed = questionKeyDocuments(source, {random: seeded(SEED), publicHalfRedacted: true});
  const halfById = new Map(keyed.publicPayloads.map((half) => [half.questionId, half.payload]));
  return {
    lessonId,
    keyDocumentPath: keyDocumentPath(lessonId),
    keyDocument: keyed.documents[keyDocumentPath(lessonId)],
    documents: source.map((question) => ({
      id: question.id,
      lessonId,
      version: 1,
      payload: halfById.has(question.id) ? halfById.get(question.id) : question.payload,
    })),
  };
}

/** The whole play order, position by position, as the device recorded it. */
function servedQuestions(lessonId) {
  return sourceQuestions(lessonId).map((question, at) => ({
    codeAnswerIndex: at,
    questionId: question.id,
  }));
}

function answerRow(questionId, at, payload) {
  return {
    questionId,
    codeAnswerIndex: at,
    // Wrong on purpose for every question below: nothing on the server-scored path may read it,
    // and the digits that come back are what say whether anything did.
    score: 9,
    answerPayload: json(payload),
    answeredAtMs: 1_700_000_000_000 + at,
    durationMs: 4200,
    wasTimeout: false,
  };
}

/** The player's choices, in the ids the redacted lesson showed them. */
const ANSWERS = [
  answerRow("q-single", AT.single, {type: "single-choice", selected: "b"}),
  answerRow("q-multi", AT.multi, {type: "multiple-choice", selected: ["a", "b"]}),
  answerRow("q-order", AT.order, {
    type: "ordering",
    // compile, test, deploy, package — the first two in place, the last two swapped.
    order: [SHOWN.order.i1, SHOWN.order.i2, SHOWN.order.i4, SHOWN.order.i3],
  }),
  answerRow("q-blank", AT.blank, {
    type: "fill-blank",
    filled: {b1: SHOWN.candidate.c1, b2: SHOWN.candidate.c3},
  }),
  answerRow("q-survey", AT.survey, {type: "survey", selected: ["a"]}),
];

module.exports = {SEED, AT, SHOWN, ANSWERS, sourceQuestions, publishLesson, servedQuestions, answerRow};
