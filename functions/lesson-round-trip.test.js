"use strict";

const assert = require("assert");

const {seeded} = require("./_seeded-random");
const fixtures = require("./_question-fixtures");
const {STATUS, redact} = require("./question-redaction");
const {PUBLIC_HALF_REDACTED, keyDocumentPath, questionKeyDocuments} = require("./question-key-store");
const {buildScoringPool} = require("./scoring-pool");
const {FAULT, NO_VALID_ANSWER, NOT_SHOWN, UNSCORABLE, scoreAttempt} = require("./attempt-scoring");
const {
  PAYMENT_RULE,
  SCORING_AUTHORITY,
  isPayable,
  readSubmittedAttempt,
  withServerScore,
} = require("./attempt-intake");

/**
 * One lesson, carried through every module between publication and payment.
 *
 * Seven modules stand between a published question and a paid attempt, and each of them is tested
 * on its own or against one neighbour: `attempt-scoring.test.js` reaches back to `redact` and
 * `question-key-store`, `scoring-pool.test.js` reaches forward to `scoreAttempt` and
 * `attempt-intake`, and the two halves meet nowhere. The epic rests on a single claim — a player
 * who answers a redacted question correctly is scored exactly as if it had never been redacted —
 * and until this file nothing asserted it end to end.
 *
 * So this suite plays a lesson twice. Once redacted: the questions are split, the keys are stored
 * the way publication stores them, the public halves are what the device is dealt, and the answers
 * come back in the re-issued ids the player was shown. Once unredacted: the same lesson, the same
 * physical choices, in the questions' own ids, with no key document at all. The two runs must
 * produce the same codeAnswer and the same percent. That equality is the whole point.
 *
 * Every step goes through the real exported function — `redact`, `questionKeyDocuments`,
 * `buildScoringPool`, `readSubmittedAttempt`, `scoreAttempt`, `withServerScore`, `isPayable`.
 * Nothing here reimplements a scorer, a shuffle or a mapper, and nothing here touches Firestore.
 * Every digit and every percent below is written down as a literal: a test that asserted "the score
 * is whatever this chain produces" would pass for every broken chain there is.
 *
 * ---
 *
 * **Nothing here is hand-edited, and that is the point of this revision.**
 *
 * The first version of this file could not close the chain. `question-key-store.js` hard-coded
 * `PUBLIC_HALF_REDACTED = false` — not a parameter, a constant — and kept only the key from each
 * `redact` call; `attempt-scoring.js` correctly refuses every redacted payload whose document says
 * `false`. Each module was right on its own and together they were a dead end: no exported function
 * in this directory could produce a lesson that scored. So this suite overrode `publicHalfRedacted`
 * by hand and rebuilt the public halves from a second generator seeded identically, and pinned the
 * dead end as a finding.
 *
 * Both halves of that are now the store's job. `questionKeyDocuments` takes `publicHalfRedacted`
 * from its caller — publication states what it publishes; the catalog backfill says nothing and
 * keeps the safe default — and hands back the public half beside every key it stored, out of the
 * one `redact` call the key came from. `publish()` below therefore writes the store's own output,
 * and `redactedRun` scores the store's own document. No field of any module's output is overridden
 * anywhere in this file.
 *
 * The old finding survives as a guard rather than a dead end: `a lesson keyed without publishing`
 * below builds the backfill's document from the same questions and the same shuffle and asserts the
 * scorer still refuses it. That refusal is what stops a key describing a permutation nobody was
 * shown from being applied to one they were.
 */

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

const LESSON = "lesson-round-trip";
const UID = "player-1";

/**
 * The seed both halves are drawn from.
 *
 * Chosen so that no Ordering item and no FillBlank candidate keeps its original position — a
 * shuffle that happened to fix one row would weaken "the order shown differs from the answer"
 * without failing anything.
 */
const SEED = 21;

const json = JSON.stringify;

/**
 * The lesson: one question of every shape that carries an answer, plus a survey that carries none.
 *
 * The payloads are `_question-fixtures.js`'s, the same ones `question-key-store.test.js` and
 * `catalog-redaction-plan.test.js` are proved against, so this suite and those are talking about
 * the same questions. `difficulty` sits on the document rather than in the payload, as it does for
 * a published question, which is why the public halves below carry `"difficulty":"HARD"`.
 */
const SOURCE = [
  {id: "q-single", lessonId: LESSON, difficulty: "HARD", payload: json(fixtures.SINGLE)},
  {id: "q-multi", lessonId: LESSON, difficulty: "HARD", payload: json(fixtures.MULTIPLE)},
  {id: "q-order", lessonId: LESSON, difficulty: "HARD", payload: json(fixtures.ORDERING)},
  {id: "q-blank", lessonId: LESSON, difficulty: "HARD", payload: json(fixtures.FILL_BLANK)},
  {id: "q-survey", lessonId: LESSON, difficulty: "HARD", payload: json(fixtures.SURVEY)},
];

/** Where `served` puts each question. Written down, because everything downstream is indexed by it. */
const AT = {single: 0, multi: 1, order: 2, blank: 3, survey: 4};

/**
 * The public halves, byte for byte, for `SEED`.
 *
 * Written out whole rather than spot-checked: these strings are what the world gets to read, and
 * the assertion that matters is that nothing in them names the answer. A field that started leaking
 * would have to be added here by hand before this suite would go green again.
 */
const PUBLIC_HALF = {
  "q-single": '{"type":"SingleChoiceRedacted","difficulty":"HARD",' +
    '"text":"Which keyword declares a read-only binding?","imageUrl":null,' +
    '"options":[{"id":"a","text":"var"},{"id":"b","text":"val"},{"id":"c","text":"lateinit"}]}',
  "q-multi": '{"type":"MultipleChoiceRedacted","difficulty":"HARD",' +
    '"text":"Which of these run on the JVM?","imageUrl":null,' +
    '"options":[{"id":"a","text":"Kotlin"},{"id":"b","text":"Swift"},' +
    '{"id":"c","text":"Scala"},{"id":"d","text":"Rust"}]}',
  "q-order": '{"type":"OrderingRedacted","difficulty":"HARD",' +
    '"text":"Put the build steps in order","imageUrl":null,' +
    '"items":[{"id":"ri-0","text":"package"},{"id":"ri-1","text":"compile"},' +
    '{"id":"ri-2","text":"deploy"},{"id":"ri-3","text":"test"}]}',
  "q-blank": '{"type":"FillBlankRedacted","difficulty":"HARD",' +
    '"text":"___ compiles to bytecode and ___ to machine code.","imageUrl":null,' +
    '"blanks":["b1","b2"],"candidates":[{"id":"rc-0","text":"Elm"},{"id":"rc-1","text":"Kotlin"},' +
    '{"id":"rc-2","text":"Rust"}],"protectedTextSegments":[]}',
  // A survey has no answer to take, so it is published exactly as it was authored.
  "q-survey": json(fixtures.SURVEY),
};

/**
 * The re-issued ids, as the player sees them, for `SEED`.
 *
 * The answers below are written in these ids rather than computed from the key, so a shuffle that
 * moved would show up as a wrong score here instead of the answers quietly following it.
 */
const SHOWN = {
  // ri-0 package(i3), ri-1 compile(i1), ri-2 deploy(i4), ri-3 test(i2)
  order: {i1: "ri-1", i2: "ri-3", i3: "ri-0", i4: "ri-2"},
  // rc-0 Elm(c3), rc-1 Kotlin(c1), rc-2 Rust(c2)
  candidate: {c1: "rc-1", c2: "rc-2", c3: "rc-0"},
};

/**
 * What each answer below is worth, by hand, from `Scoring.kt`'s formula — never read out of the
 * code under test.
 *
 * - single    the right option                                            -> 9
 * - multi     one of the two right options picked, one wrong one with it:
 *             scoreDigit(1, 1 + 1 + 1) = trunc((8 + 1) / 3) + 1           -> 4
 * - order     two of four items in place: scoreDigit(2, 4)
 *             = trunc((16 + 2) / 4) + 1                                   -> 5
 * - blank     one of two blanks filled correctly: scoreDigit(1, 2)
 *             = trunc((8 + 1) / 2) + 1                                    -> 5
 * - survey    took part                                                   -> 9
 */
const DIGIT = {single: "9", multi: "4", order: "5", blank: "5", survey: "9"};

/** The whole play, and its percent: (100 + 37 + 50 + 50 + 100) / 5, truncated. */
const CODE_ANSWER = "94559";
const PERCENT = 67;

/** The same play abandoned after three questions: the last two are shown-and-unanswered. */
const PARTIAL_CODE_ANSWER = "94511";
const PARTIAL_PERCENT = 37;

// -------------------------------------------------------------------------------------------
// Publication
// -------------------------------------------------------------------------------------------

/**
 * Publishes the lesson: the key document as `questionKeyDocuments` writes it, and the public halves
 * it handed back with it.
 *
 * One call. The store splits each question once and returns both halves, so the arrangement the
 * world is shown and the arrangement the keys describe are the same arrangement by construction —
 * there is no second `redact` call here to draw a different one. Because these halves *are*
 * published, the call states so, and the document goes out stamped `publicHalfRedacted: true`.
 *
 */
function publish() {
  const keyed = questionKeyDocuments(SOURCE, {
    random: seeded(SEED),
    publicHalfRedacted: true,
  });
  const keyDocument = keyed.documents[keyDocumentPath(LESSON)];
  const halfById = new Map(keyed.publicPayloads.map((half) => [half.questionId, half.payload]));

  const documents = SOURCE.map((question) => ({
    id: question.id,
    lessonId: LESSON,
    version: 1,
    // The half the store keyed, where it keyed one. A question it took no answer from — the survey
    // — has nothing to replace, so it is published exactly as authored, which is what publication
    // already does for every question today.
    payload: halfById.has(question.id) ? halfById.get(question.id) : question.payload,
  }));

  return {keyDocument, documents, publicPayloads: keyed.publicPayloads, refusals: keyed.refusals};
}

/**
 * The same lesson keyed the way the catalog backfill keys it: the same questions and the same
 * shuffle, no `publicHalfRedacted` stated, nothing published.
 *
 * The distinction is the whole of the guard. This document's keys are byte for byte the ones
 * `publish` writes; what differs is that nobody has been shown the arrangement they describe.
 */
function keyWithoutPublishing() {
  return questionKeyDocuments(SOURCE, {random: seeded(SEED)}).documents[keyDocumentPath(LESSON)];
}

/** The lesson as it stands today: payloads written out whole, answers and all. */
function publishUnredacted() {
  return SOURCE.map((question) => ({
    id: question.id,
    lessonId: LESSON,
    version: 1,
    payload: question.payload,
  }));
}

const keyFor = (keyDocument, questionId) => {
  const entry = keyDocument.keys.find((row) => row.questionId === questionId);
  return entry === undefined ? null : entry.key;
};

// -------------------------------------------------------------------------------------------
// The attempt
// -------------------------------------------------------------------------------------------

/** The whole play order, position by position, as the device recorded it. */
const SERVED = SOURCE.map((question, at) => ({codeAnswerIndex: at, questionId: question.id}));

function answer(questionId, at, payload) {
  return {
    questionId,
    codeAnswerIndex: at,
    // The device's own number, deliberately wrong for every question below. Nothing on the
    // server-scored path may read it, and the codeAnswer these produce says whether anything did.
    score: 9,
    answerPayload: json(payload),
    answeredAtMs: 1700000000000 + at,
    durationMs: 4200,
    wasTimeout: false,
  };
}

/** The player's choices, in the ids they were shown on the redacted lesson. */
const REDACTED_ANSWERS = [
  answer("q-single", AT.single, {type: "single-choice", selected: "b"}),
  answer("q-multi", AT.multi, {type: "multiple-choice", selected: ["a", "b"]}),
  answer("q-order", AT.order, {
    type: "ordering",
    // compile, test, deploy, package — the first two in place, the last two swapped.
    order: [SHOWN.order.i1, SHOWN.order.i2, SHOWN.order.i4, SHOWN.order.i3],
  }),
  answer("q-blank", AT.blank, {
    type: "fill-blank",
    filled: {b1: SHOWN.candidate.c1, b2: SHOWN.candidate.c3},
  }),
  answer("q-survey", AT.survey, {type: "survey", selected: ["a"]}),
];

/** The same choices, on the unredacted lesson, in the questions' own ids. */
const PLAIN_ANSWERS = [
  answer("q-single", AT.single, {type: "single-choice", selected: "b"}),
  answer("q-multi", AT.multi, {type: "multiple-choice", selected: ["a", "b"]}),
  answer("q-order", AT.order, {type: "ordering", order: ["i1", "i2", "i4", "i3"]}),
  answer("q-blank", AT.blank, {type: "fill-blank", filled: {b1: "c1", b2: "c3"}}),
  answer("q-survey", AT.survey, {type: "survey", selected: ["a"]}),
];

/** A hard attempt as the device posts it: what it was dealt and what came back, and no score. */
function submission(overrides) {
  return {
    userId: UID,
    attemptId: "attempt-1",
    catalogId: "catalog-1",
    questId: "quest-1",
    sectionId: "section-1",
    themeId: "theme-1",
    lessonId: LESSON,
    lessonVersion: 1,
    difficulty: "HARD",
    served: SERVED,
    answers: REDACTED_ANSWERS,
    completedAtMs: 1700000005000,
    createdAtMs: 1700000000000,
    ...(overrides || {}),
  };
}

/**
 * The chain, in order, with nothing between the steps.
 *
 * intake -> pool -> scorer -> the attempt as it would be stored. This is the composition the submit
 * handler will make, and the reason this file exists is that the handler is where it would
 * otherwise first be written.
 */
function play(documents, keyDocument, overrides) {
  const attempt = readSubmittedAttempt(submission(overrides), UID);
  const pool = buildScoringPool({
    lessonId: attempt.lessonId,
    lessonVersion: attempt.lessonVersion,
    served: attempt.served,
    documents,
  });
  const scoring = scoreAttempt({
    questions: pool.questions,
    keyDocument,
    answers: attempt.answers,
    served: attempt.served,
  });
  return {
    attempt,
    pool,
    scoring,
    stored: withServerScore(attempt, scoring),
    payable: isPayable(attempt, scoring),
  };
}

/**
 * The redacted lesson, published as this suite publishes it and keyed as the store keys it.
 *
 * `documents` overrides the lesson's published questions; everything else overrides the body the
 * device posts.
 */
function redactedRun(overrides) {
  const published = publish();
  const {documents, ...body} = overrides || {};
  // The store's own document, unmodified. See the header.
  return play(
    documents === undefined ? published.documents : documents,
    published.keyDocument,
    body,
  );
}

/** The same lesson with nothing taken out of it, and so no key document to apply. */
function unredactedRun(overrides) {
  return play(publishUnredacted(), null, {answers: PLAIN_ANSWERS, ...(overrides || {})});
}

// -------------------------------------------------------------------------------------------
// The claim
// -------------------------------------------------------------------------------------------

test("the same lesson and the same answers score the same redacted as unredacted", () => {
  const redacted = redactedRun();
  const plain = unredactedRun();

  assert.strictEqual(redacted.scoring.scorable, true, "the redacted run did not score");
  assert.strictEqual(plain.scoring.scorable, true, "the unredacted run did not score");
  assert.strictEqual(redacted.scoring.codeAnswer, CODE_ANSWER);
  assert.strictEqual(plain.scoring.codeAnswer, CODE_ANSWER);
  assert.strictEqual(redacted.scoring.percentScore, PERCENT);
  assert.strictEqual(plain.scoring.percentScore, PERCENT);
  assert.deepStrictEqual(
    redacted.scoring.unscorable,
    [],
    "the redacted run reported a problem the unredacted run did not have",
  );
  assert.deepStrictEqual(plain.scoring.unscorable, []);
});

test("every shape scores what the written-down table says", () => {
  const {scoring} = redactedRun();
  const digits = scoring.codeAnswer.split("");

  assert.strictEqual(digits[AT.single], DIGIT.single, "single choice");
  assert.strictEqual(digits[AT.multi], DIGIT.multi, "multiple choice");
  assert.strictEqual(digits[AT.order], DIGIT.order, "ordering");
  assert.strictEqual(digits[AT.blank], DIGIT.blank, "fill blank");
  assert.strictEqual(digits[AT.survey], DIGIT.survey, "survey");
  // Every answer above claims `score: 9`. Three of the five digits are not 9, so the claim was not
  // read — which is the whole reason the server scores at all.
  assert.strictEqual(scoring.codeAnswer, CODE_ANSWER);
});

// -------------------------------------------------------------------------------------------
// Publication
// -------------------------------------------------------------------------------------------

test("publication produces the public halves and the keys that describe them", () => {
  const {keyDocument, documents, publicPayloads, refusals} = publish();

  assert.deepStrictEqual(refusals, [], "a question was published without a key");
  // One half per stored key, in key order, and none for the survey — publishing a redacted half for
  // a key that was never stored would leave a question nobody can score.
  assert.deepStrictEqual(
    publicPayloads.map((half) => half.questionId),
    ["q-single", "q-multi", "q-order", "q-blank"],
  );
  assert.ok(publicPayloads.every((half) => half.lessonId === LESSON));
  assert.strictEqual(keyDocument.publicHalfRedacted, true, "the caller published, and said so");
  assert.strictEqual(keyDocument.lessonId, LESSON);
  assert.deepStrictEqual(keyDocument.refusals, []);
  assert.strictEqual(keyDocument.omitted, 0);
  // The survey is the one question with no answer to take, so it produces neither a key nor a
  // refusal — the store's own rule, and the reason a missing key here is not a hole.
  assert.deepStrictEqual(
    keyDocument.keys.map((entry) => entry.questionId),
    ["q-single", "q-multi", "q-order", "q-blank"],
  );

  for (const document of documents) {
    assert.strictEqual(document.payload, PUBLIC_HALF[document.id], `public half of ${document.id}`);
  }
});

test("both halves came from one shuffle", () => {
  const {keyDocument, documents} = publish();
  const payloadOf = (id) => JSON.parse(documents.find((row) => row.id === id).payload);

  // Structural now — the store splits once and returns both halves — and still asserted, because
  // the pairing is what everything downstream rests on: a re-issued id carrying the text of a
  // different row means every Ordering and FillBlank scores against the wrong permutation.
  const orderKey = keyFor(keyDocument, "q-order");
  const orderTexts = new Map(fixtures.ORDERING.items.map((item) => [item.id, item.text]));
  for (const item of payloadOf("q-order").items) {
    assert.strictEqual(
      item.text,
      orderTexts.get(orderKey.idMap[item.id]),
      `${item.id} carries the text of a row the key maps elsewhere`,
    );
  }

  const blankKey = keyFor(keyDocument, "q-blank");
  const candidateTexts = new Map(fixtures.FILL_BLANK.candidates.map((row) => [row.id, row.text]));
  for (const candidate of payloadOf("q-blank").candidates) {
    assert.strictEqual(
      candidate.text,
      candidateTexts.get(blankKey.idMap[candidate.id]),
      `${candidate.id} carries the text of a candidate the key maps elsewhere`,
    );
  }
});

test("the public half does not give the answer away", () => {
  const {keyDocument, documents} = publish();
  const payloadOf = (id) => JSON.parse(documents.find((row) => row.id === id).payload);

  const single = payloadOf("q-single");
  assert.strictEqual(single.correctOptionId, undefined, "the right option is published");
  // `info` is the author's prose and routinely names the answer; the fixture's does.
  assert.strictEqual(single.info, undefined, "the author's note is published");

  const multi = payloadOf("q-multi");
  assert.strictEqual(multi.correctOptionIds, undefined, "the right options are published");
  assert.deepStrictEqual(
    Object.keys(multi).sort(),
    ["difficulty", "imageUrl", "options", "text", "type"],
    "a field that could carry how many options are correct is published",
  );

  // The order shown is not the order the key records, so reading the list top to bottom is wrong.
  const shown = payloadOf("q-order").items.map((item) => keyFor(keyDocument, "q-order").idMap[item.id]);
  assert.deepStrictEqual(shown, ["i3", "i1", "i4", "i2"]);
  assert.notDeepStrictEqual(shown, keyFor(keyDocument, "q-order").order);
  assert.deepStrictEqual(keyFor(keyDocument, "q-order").order, ["i1", "i2", "i3", "i4"]);

  const blank = payloadOf("q-blank");
  // Bare ids: which candidate fills which blank is not in the published half at all.
  assert.deepStrictEqual(blank.blanks, ["b1", "b2"]);
  assert.deepStrictEqual(
    blank.candidates.map((row) => keyFor(keyDocument, "q-blank").idMap[row.id]),
    ["c3", "c1", "c2"],
  );
  // The fixture's protected segment is the answer to b1 spelled out; it must not survive.
  assert.deepStrictEqual(fixtures.FILL_BLANK.protectedTextSegments, ["Kotlin"]);
  assert.deepStrictEqual(blank.protectedTextSegments, []);
});

test("playing the public half in the order it is shown scores the floor", () => {
  // The strongest form of "the answer is not recoverable from the public half alone": a player who
  // takes the published arrangement at face value gets the lowest digit there is, on both shapes.
  const shownOrder = ["ri-0", "ri-1", "ri-2", "ri-3"];
  const byPosition = {b1: "rc-0", b2: "rc-1"};
  const {scoring} = redactedRun({
    answers: [
      answer("q-order", AT.order, {type: "ordering", order: shownOrder}),
      answer("q-blank", AT.blank, {type: "fill-blank", filled: byPosition}),
    ],
  });

  assert.strictEqual(scoring.scorable, true);
  assert.strictEqual(scoring.codeAnswer.charAt(AT.order), NO_VALID_ANSWER, "the shown order scored");
  assert.strictEqual(scoring.codeAnswer.charAt(AT.blank), NO_VALID_ANSWER, "filling by position scored");
});

test("a survey is published whole, keyed nowhere, and scored on participation", () => {
  const outcome = redact(json(fixtures.SURVEY), "HARD", {questionId: "q-survey", random: seeded(SEED)});
  assert.strictEqual(outcome.status, STATUS.NOT_APPLICABLE);
  assert.strictEqual(outcome.key, null);
  assert.strictEqual(outcome.publicPayload, json(fixtures.SURVEY), "the survey was altered");

  const {keyDocument} = publish();
  assert.strictEqual(keyFor(keyDocument, "q-survey"), null, "the survey was given a key");
  assert.strictEqual(
    keyDocument.refusals.some((record) => record.questionId === "q-survey"),
    false,
    "the survey was recorded as a refusal",
  );

  // Scored beside four redacted questions, in a lesson whose document says its halves are redacted.
  assert.strictEqual(redactedRun().scoring.codeAnswer.charAt(AT.survey), DIGIT.survey);
});

// -------------------------------------------------------------------------------------------
// Re-issued ids
// -------------------------------------------------------------------------------------------

test("the translation back out of the re-issued ids happens exactly once", () => {
  // Once is the only count that scores. Never, and the answer is compared to a permutation it does
  // not belong to; twice, and every id resolves to null. Both land on the floor digit, so scoring
  // these two shapes correctly is the assertion.
  const {scoring} = redactedRun();
  assert.strictEqual(scoring.codeAnswer.charAt(AT.order), DIGIT.order);
  assert.strictEqual(scoring.codeAnswer.charAt(AT.blank), DIGIT.blank);

  // The other direction: an answer already in the question's own ids is a stale or crafted one, and
  // the re-issued ids are the only ones the player could have been shown.
  const stale = redactedRun({
    answers: [
      answer("q-order", AT.order, {type: "ordering", order: ["i1", "i2", "i4", "i3"]}),
      answer("q-blank", AT.blank, {type: "fill-blank", filled: {b1: "c1", b2: "c3"}}),
    ],
  });
  assert.strictEqual(stale.scoring.codeAnswer.charAt(AT.order), NO_VALID_ANSWER);
  assert.strictEqual(stale.scoring.codeAnswer.charAt(AT.blank), NO_VALID_ANSWER);
});

// -------------------------------------------------------------------------------------------
// The pool
// -------------------------------------------------------------------------------------------

test("the pool is filled from served, position by position", () => {
  const {pool, attempt} = redactedRun();

  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.strictEqual(attempt.paymentRule, PAYMENT_RULE.SERVER_SCORED);
  assert.strictEqual(attempt.codeAnswer, null);
  assert.strictEqual(attempt.percentScore, null);
  assert.deepStrictEqual(attempt.served, SERVED);

  assert.strictEqual(pool.built, true);
  assert.deepStrictEqual(pool.missing, []);
  assert.strictEqual(pool.versionDrift, null);
  assert.deepStrictEqual(
    pool.questions.map((question) => question.id),
    ["q-single", "q-multi", "q-order", "q-blank", "q-survey"],
  );
  // Narrowed to the three fields the scorer reads — no `order`, no `archived`, no `version`.
  assert.deepStrictEqual(Object.keys(pool.questions[0]).sort(), ["id", "lessonId", "payload"]);
});

test("a player who abandons the run is charged for what was shown and nothing else", () => {
  // The served list is the whole play order, so the two questions the player never reached are
  // shown-and-unanswered: '1', counted in the percent, not the '0' that would drop them from it.
  const redacted = redactedRun({answers: REDACTED_ANSWERS.slice(0, 3)});
  const plain = unredactedRun({answers: PLAIN_ANSWERS.slice(0, 3)});

  assert.strictEqual(redacted.scoring.codeAnswer, PARTIAL_CODE_ANSWER);
  assert.strictEqual(redacted.scoring.percentScore, PARTIAL_PERCENT);
  assert.strictEqual(plain.scoring.codeAnswer, PARTIAL_CODE_ANSWER);
  assert.strictEqual(plain.scoring.percentScore, PARTIAL_PERCENT);
  assert.strictEqual(redacted.scoring.codeAnswer.charAt(AT.blank), NO_VALID_ANSWER);
  assert.strictEqual(redacted.scoring.codeAnswer.charAt(AT.survey), NO_VALID_ANSWER);
  assert.strictEqual(
    redacted.scoring.codeAnswer.includes(NOT_SHOWN),
    false,
    "a question the player was dealt was dropped from the denominator",
  );
  // Abandoning is not a fault of anyone's; the attempt still pays.
  assert.strictEqual(redacted.payable, true);
});

// -------------------------------------------------------------------------------------------
// Payment
// -------------------------------------------------------------------------------------------

test("the honest run is payable and is stored with the score the server computed", () => {
  const {stored, payable} = redactedRun();

  assert.strictEqual(payable, true);
  assert.strictEqual(stored.payable, true);
  assert.strictEqual(stored.codeAnswer, CODE_ANSWER);
  assert.strictEqual(stored.percentScore, PERCENT);
  assert.strictEqual(stored.scoringAuthority, SCORING_AUTHORITY.SERVER);
});

test("a lie about where an answer was dealt costs the liar the digit and nothing else", () => {
  // The multiple choice answer claims the fill-blank's position. `served` places the digit, so the
  // claim cannot steal a position and cannot lower the denominator either — it just fails to score.
  const moved = REDACTED_ANSWERS.map((entry, index) =>
    (index === AT.multi ? {...entry, codeAnswerIndex: AT.blank} : entry));
  const {scoring, payable} = redactedRun({answers: moved});

  assert.strictEqual(scoring.codeAnswer, "91559");
  assert.strictEqual(scoring.percentScore, 60);
  assert.deepStrictEqual(scoring.unscorable, [{
    questionId: "q-multi",
    codeAnswerIndex: AT.multi,
    reason: UNSCORABLE.INDEX_DISAGREES,
    fault: FAULT.CLIENT,
    detail: String(AT.blank),
  }]);
  assert.strictEqual(payable, true, "a client's own fault stopped the attempt being paid");
});

test("a served list naming a question that does not exist costs the liar, and never pays", () => {
  // The device claims a sixth question it was never dealt. Read with `payment-rule: server-scored`
  // in hand: our own gap means nothing paid *and nothing charged*, so while this was filed as a
  // server fault the invented entry bought an uncharged hard attempt — a shape strictly cheaper
  // than playing the lesson. It is now a sixth shown-and-unanswered position: the attempt scores,
  // the percent falls, and the player is charged for the run they actually took.
  const invented = [...SERVED, {codeAnswerIndex: 5, questionId: "q-never-existed"}];
  const {scoring, stored, payable} = redactedRun({served: invented});
  const honest = redactedRun();

  assert.strictEqual(scoring.scorable, true);
  assert.strictEqual(scoring.codeAnswer, `${CODE_ANSWER}${NO_VALID_ANSWER}`);
  // (100 + 37 + 50 + 50 + 100 + 0) / 6, truncated — against 67 for the same play without the lie.
  assert.strictEqual(scoring.percentScore, 56);
  assert.ok(
    scoring.percentScore < honest.scoring.percentScore,
    "inventing a served entry did not cost the player anything",
  );
  assert.strictEqual(payable, true, "the invented entry cancelled the charge, which is the exploit");
  assert.strictEqual(stored.payable, true);
  assert.strictEqual(stored.percentScore, 56);
  // Still named, by id and position, so a loss that is genuinely ours stays visible.
  assert.deepStrictEqual(scoring.unscorable, [{
    questionId: "q-never-existed",
    codeAnswerIndex: 5,
    reason: UNSCORABLE.QUESTION_MISSING,
    fault: FAULT.CLIENT,
    detail: null,
  }]);
});

test("a device that scores the run itself is judged on its own claim, not on the server's", () => {
  // The other kind of lie the chain can be told: the same play, submitted with digits and a percent
  // attached. Intake reads that as device-scored and never asks the scorer at all, so the only
  // thing standing between the claim and payment is whether it agrees with itself.
  const body = {
    ...submission({answers: PLAIN_ANSWERS}),
    codeAnswer: CODE_ANSWER,
    percentScore: PERCENT,
  };
  const honest = readSubmittedAttempt(body, UID);
  assert.strictEqual(honest.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(honest.paymentRule, PAYMENT_RULE.DEVICE_SCORED);
  assert.strictEqual(honest.scoreVerified, true);
  assert.strictEqual(honest.servedVerified, true);
  assert.strictEqual(isPayable(honest, null), true);

  const liar = readSubmittedAttempt({...body, percentScore: 100}, UID);
  assert.strictEqual(liar.scoreVerified, false);
  assert.strictEqual(liar.expectedPercentScore, PERCENT);
  assert.strictEqual(isPayable(liar, null), false);
});

// -------------------------------------------------------------------------------------------
// A lost question
// -------------------------------------------------------------------------------------------

test("a question lost between publication and scoring costs that question and no more", () => {
  const {documents} = publish();
  const lost = documents.filter((document) => document.id !== "q-order");
  const {pool, scoring, stored, payable} = redactedRun({documents: lost});

  // The position stays: shrinking the pool would slide every later question one place left.
  assert.deepStrictEqual(pool.missing, [{questionId: "q-order", codeAnswerIndex: AT.order}]);
  assert.strictEqual(pool.questions.length, SOURCE.length);
  assert.deepStrictEqual(pool.questions[AT.blank].id, "q-blank");
  assert.deepStrictEqual(pool.questions[AT.survey].id, "q-survey");

  // The honest player's side of the same reclassification, and the price the epic accepted for it.
  // A document we really did lose costs its own position — '1', shown and unanswered — because at
  // the scorer this input is indistinguishable from the invented entry above, and only one of the
  // two treatments is safe against that one. `pool.missing` is where the loss stays visible: the
  // builder is handed the lesson's documents and can tell them apart, and it reports rather than
  // refusing.
  assert.strictEqual(scoring.scorable, true);
  assert.strictEqual(
    scoring.codeAnswer,
    CODE_ANSWER.slice(0, AT.order) + NO_VALID_ANSWER + CODE_ANSWER.slice(AT.order + 1),
  );
  assert.strictEqual(scoring.codeAnswer, "94159");
  // (100 + 37 + 0 + 50 + 100) / 5, truncated. Every question but the lost one scores as it did.
  assert.strictEqual(scoring.percentScore, 57);
  assert.deepStrictEqual(scoring.unscorable, [{
    questionId: "q-order",
    codeAnswerIndex: AT.order,
    reason: UNSCORABLE.QUESTION_MISSING,
    fault: FAULT.CLIENT,
    detail: null,
  }]);

  assert.strictEqual(payable, true);
  assert.strictEqual(stored.payable, true);
  assert.strictEqual(stored.codeAnswer, "94159");
  assert.strictEqual(stored.percentScore, 57);
});

// -------------------------------------------------------------------------------------------
// The guard between the two generations
// -------------------------------------------------------------------------------------------

const refusedGeneration = (questionId, codeAnswerIndex) => ({
  questionId,
  codeAnswerIndex,
  reason: UNSCORABLE.KEY_GENERATION,
  fault: FAULT.SERVER,
  detail: null,
});

test("a lesson keyed without publishing keeps the old stamp, and is still refused", () => {
  // The catalog backfill's shape: keys harvested from payloads that went out whole. A caller that
  // states nothing gets `PUBLIC_HALF_REDACTED`, and the scorer refuses every redacted payload whose
  // document says so. The default has to be this one — claiming `false` wrongly costs a scored
  // attempt, loudly; claiming `true` wrongly scores wrong answers correct, silently.
  assert.strictEqual(PUBLIC_HALF_REDACTED, false, "the safe default moved");
  const backfilled = keyWithoutPublishing();
  assert.strictEqual(backfilled.publicHalfRedacted, false);

  // Same questions, same seed, so these are byte for byte the keys that score the lesson when the
  // document says the halves were published. Only the stamp differs, and only the stamp refuses.
  const published = publish();
  assert.deepStrictEqual(backfilled.keys, published.keyDocument.keys);

  const {scoring, payable} = play(published.documents, backfilled, {});
  assert.strictEqual(scoring.scorable, false);
  assert.strictEqual(payable, false);
  assert.deepStrictEqual(scoring.unscorable, [
    refusedGeneration("q-single", AT.single),
    refusedGeneration("q-multi", AT.multi),
    refusedGeneration("q-order", AT.order),
    refusedGeneration("q-blank", AT.blank),
  ]);
  // The survey is the one question that survives, because it never needed a key.
  assert.strictEqual(
    scoring.unscorable.some((record) => record.questionId === "q-survey"),
    false,
  );
});

test("a key from a different call is refused rather than applied", () => {
  // The leak the stamp exists to stop. `redact` draws a fresh shuffle every call, so a key from one
  // call describes a permutation the half from another call was never arranged in — and
  // `restoreContent` reassembles the pair anyway, without complaint, producing a question whose
  // rows have swapped meanings. Nothing downstream can notice. The stamp is what catches it: the
  // second call published nothing, so its document says so, and the scorer stops there.
  const published = publish();
  const otherCall = questionKeyDocuments(SOURCE, {random: seeded(SEED + 1)});
  const strangersKeys = otherCall.documents[keyDocumentPath(LESSON)];
  assert.notDeepStrictEqual(
    strangersKeys.keys,
    published.keyDocument.keys,
    "the two calls drew the same shuffle, so this case proves nothing",
  );

  const {scoring, payable} = play(published.documents, strangersKeys, {});
  assert.strictEqual(scoring.scorable, false);
  assert.strictEqual(payable, false);
  assert.deepStrictEqual(scoring.unscorable, [
    refusedGeneration("q-single", AT.single),
    refusedGeneration("q-multi", AT.multi),
    refusedGeneration("q-order", AT.order),
    refusedGeneration("q-blank", AT.blank),
  ]);

  // And the reason a stamp rather than a checksum is not enough on its own: told the crossed keys
  // belong to these halves, the chain scores — wrongly, silently, and payably. The guard is the
  // only thing between those two outcomes, which is why it is never weakened into "score it anyway".
  const lied = play(published.documents, {...strangersKeys, publicHalfRedacted: true}, {});
  assert.strictEqual(lied.scoring.scorable, true);
  assert.notStrictEqual(lied.scoring.codeAnswer, CODE_ANSWER, "a crossed pair scored identically");
});

// -------------------------------------------------------------------------------------------

test("playing the lesson reads its inputs and writes to none of them", () => {
  const before = json({SOURCE, SERVED, REDACTED_ANSWERS, PLAIN_ANSWERS});
  redactedRun();
  unredactedRun();
  assert.strictEqual(json({SOURCE, SERVED, REDACTED_ANSWERS, PLAIN_ANSWERS}), before);
});

let failures = 0;
for (const [name, fn] of SUITE) {
  try {
    fn();
  } catch (error) {
    failures += 1;
    console.error(`FAILED: ${name}`);
    console.error(error.message);
  }
}
if (failures > 0) {
  console.error(`lesson-round-trip.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`lesson-round-trip.test.js OK (${SUITE.length} cases)`);
}
