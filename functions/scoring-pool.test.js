"use strict";

const assert = require("assert");

const {REASON, MAX_SERVED_POSITION, UNSERVED_ID_PREFIX, buildScoringPool} = require("./scoring-pool");
const {NOT_SHOWN, NO_VALID_ANSWER, FAULT, UNSCORABLE, scoreAttempt} = require("./attempt-scoring");
const {computePercentScore} = require("./assessment-scoring");
const attemptIntake = require("./attempt-intake");

/**
 * What this suite is trying to catch.
 *
 * A pool builder cannot fail loudly. Every mistake available to it — a question one place left, a
 * served question quietly dropped, a filler standing where a real question should be — produces a
 * pool the scorer accepts and a number it returns. So the assertions here are written down rather
 * than recomputed: exact codeAnswer strings, exact ids at exact positions. A test that asserted
 * "the digits are whatever this pool produces" would pass for every wrong pool there is.
 */
const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

const LESSON = "lesson-1";
const json = (value) => JSON.stringify(value);

/** Three correct answers out of three options, so a partial answer has a digit of its own. */
const MULTIPLE = {
  type: "MultipleChoice",
  text: "Which of these run on the JVM?",
  imageUrl: null,
  options: [{id: "a", text: "Kotlin"}, {id: "b", text: "Swift"}, {id: "c", text: "Scala"}],
  correctOptionIds: ["a", "c"],
};

/** Digits `evaluateAnswer` returns for the three answers below, pinned so a swap is visible. */
const PERFECT = {type: "multiple-choice", selected: ["a", "c"]};
const PARTIAL = {type: "multiple-choice", selected: ["a"]};
const WRONG = {type: "multiple-choice", selected: ["b"]};
const DIGIT = {perfect: "9", partial: "5", wrong: "1"};

/** A question document as `db.collection("questions")` returns one, mapped to a plain object. */
function document(id, extra) {
  return {
    id,
    lessonId: LESSON,
    text: "Which of these run on the JVM?",
    payload: json(MULTIPLE),
    language: "en",
    languageLevel: 0,
    order: 0,
    version: 1,
    lastModifiedAt: 1700000000000,
    archived: false,
    ...(extra || {}),
  };
}

const serve = (...pairs) => pairs.map(([at, questionId]) => ({codeAnswerIndex: at, questionId}));

function answer(questionId, at, payload) {
  return {
    questionId,
    codeAnswerIndex: at,
    // The client's own number, always a lie in these cases. Nothing may read it.
    score: 9,
    answerPayload: json(payload),
    answeredAtMs: 1700000000000 + at,
    durationMs: 4200,
    wasTimeout: false,
  };
}

function build(overrides) {
  return buildScoringPool({lessonId: LESSON, served: [], documents: [], ...(overrides || {})});
}

/** The ids the pool holds, position by position, with fillers collapsed to `null`. */
function idsAt(questions) {
  return questions.map((entry) => (entry.id.startsWith(UNSERVED_ID_PREFIX) ? null : entry.id));
}

// ---------------------------------------------------------------------------------------------
// The Matrix
// ---------------------------------------------------------------------------------------------

test("an ordinary attempt: twenty served, twenty present, every position inside the pool", () => {
  const documents = Array.from({length: 20}, (unused, at) => document(`q${at}`));
  const served = documents.map((doc, at) => ({codeAnswerIndex: at, questionId: doc.id}));

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.strictEqual(pool.questions.length, 20, "the pool is as long as the last served position + 1");
  assert.deepStrictEqual(pool.missing, []);
  assert.deepStrictEqual(idsAt(pool.questions), served.map((entry) => entry.questionId));
  for (const entry of served) {
    assert.ok(entry.codeAnswerIndex < pool.questions.length, "every served position is inside the pool");
  }

  const answers = served.map((entry) => answer(entry.questionId, entry.codeAnswerIndex, PERFECT));
  const scored = scoreAttempt({questions: pool.questions, served, keyDocument: null, answers});
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(scored.codeAnswer, "9".repeat(20));
  assert.strictEqual(scored.percentScore, 100);
});

test("a sparse subset: twenty dealt out of a thirty-question lesson, the rest are '0'", () => {
  const documents = Array.from({length: 30}, (unused, at) => document(`q${at}`));
  // Two questions dealt out of every three, so the served positions are sparse and the gaps are
  // inside the range rather than after it. The last one dealt is 28.
  const dealt = Array.from({length: 30}, (unused, at) => at).filter((at) => at % 3 !== 2);
  const served = serve(...dealt.map((at) => [at, `q${at}`]));
  assert.strictEqual(served.length, 20);

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.strictEqual(pool.questions.length, 29, "the last served position is 28");
  assert.deepStrictEqual(pool.missing, []);
  assert.deepStrictEqual(
    idsAt(pool.questions),
    Array.from({length: 29}, (unused, at) => (at % 3 === 2 ? null : `q${at}`)),
  );

  const answers = served.map((entry) => answer(entry.questionId, entry.codeAnswerIndex, PERFECT));
  const scored = scoreAttempt({questions: pool.questions, served, keyDocument: null, answers});
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(scored.codeAnswer, "990".repeat(9) + "99");
  // Twenty nines and nine zeros: the zeros are dropped, so the percent is the nines' alone.
  assert.strictEqual(scored.percentScore, 100);
});

test("a question archived since the attempt is scored as it was served", () => {
  const documents = [document("q0", {archived: true}), document("q1")];
  const served = serve([0, "q0"], [1, "q1"]);

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.deepStrictEqual(idsAt(pool.questions), ["q0", "q1"]);
  assert.deepStrictEqual(pool.missing, [], "archived is not missing");
  assert.ok(
    pool.questions.every((entry) => !("archived" in entry)),
    "the flag does not travel into the pool, so nothing downstream can be tempted to read it",
  );

  const scored = scoreAttempt({
    questions: pool.questions,
    served,
    keyDocument: null,
    answers: [answer("q0", 0, PERFECT), answer("q1", 1, PARTIAL)],
  });
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(scored.codeAnswer, DIGIT.perfect + DIGIT.partial);
});

test("a served question whose document is gone is reported by id and position", () => {
  const documents = [document("alpha"), document("gamma")];
  const served = serve([0, "alpha"], [1, "lost"], [2, "gamma"]);

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true, "one lost question does not refuse the pool");
  assert.deepStrictEqual(pool.missing, [{questionId: "lost", codeAnswerIndex: 1}]);

  // The loss is confined to its own position: every other entry is what it would have been had the
  // document never gone anywhere. This is the assertion that catches a shrinking pool.
  const whole = build({served, documents: [...documents, document("lost")]});
  assert.deepStrictEqual(idsAt(pool.questions), ["alpha", null, "gamma"]);
  assert.deepStrictEqual(idsAt(whole.questions), ["alpha", "lost", "gamma"]);
  assert.strictEqual(pool.questions.length, whole.questions.length);
  assert.deepStrictEqual(pool.questions[0], whole.questions[0]);
  assert.deepStrictEqual(pool.questions[2], whole.questions[2]);

  // And the scorer names it the same way, rather than calling it a question nobody was shown.
  const answers = [answer("alpha", 0, PERFECT), answer("gamma", 2, PARTIAL)];
  const scored = scoreAttempt({questions: pool.questions, served, keyDocument: null, answers});
  assert.strictEqual(
    scored.scorable,
    false,
    "a question we served and then lost is our gap, so the caller decides rather than being handed a number",
  );
  assert.deepStrictEqual(
    scored.unscorable,
    [{questionId: "lost", codeAnswerIndex: 1, reason: UNSCORABLE.QUESTION_MISSING, fault: FAULT.SERVER, detail: null}],
    "the one lost question, by id and position, and nothing else held against the attempt",
  );
  // With the document back, the same served list and the same answers score normally — nothing
  // about the other two questions depended on the one that was gone.
  const again = scoreAttempt({questions: whole.questions, served, keyDocument: null, answers});
  assert.strictEqual(again.scorable, true);
  assert.strictEqual(again.codeAnswer, DIGIT.perfect + NO_VALID_ANSWER + DIGIT.partial);
});

test("a translated variant is its own question, not a copy of the canonical one", () => {
  const documents = [document("q1"), document("q1__uk", {language: "uk"})];
  const served = serve([0, "q1"], [1, "q1__uk"]);

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.deepStrictEqual(idsAt(pool.questions), ["q1", "q1__uk"], "no canonicalisation, no dedupe");
  assert.deepStrictEqual(pool.missing, []);

  const scored = scoreAttempt({
    questions: pool.questions,
    served,
    keyDocument: null,
    answers: [answer("q1", 0, PERFECT), answer("q1__uk", 1, WRONG)],
  });
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(
    scored.codeAnswer,
    DIGIT.perfect + DIGIT.wrong,
    "each variant is scored against its own document",
  );
});

test("a document the attempt never dealt is absent, and its position is '0'", () => {
  const documents = [document("dealt"), document("spare")];
  const served = serve([1, "dealt"]);

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.strictEqual(pool.questions.length, 2);
  assert.deepStrictEqual(idsAt(pool.questions), [null, "dealt"]);
  assert.ok(
    pool.questions.every((entry) => entry.id !== "spare"),
    "an undealt question is not in the pool at all",
  );

  const scored = scoreAttempt({
    questions: pool.questions,
    served,
    keyDocument: null,
    answers: [answer("dealt", 1, PERFECT)],
  });
  assert.strictEqual(scored.codeAnswer, NOT_SHOWN + DIGIT.perfect);
  assert.strictEqual(scored.percentScore, 100, "the unserved position is dropped, not averaged in");
});

test("documents from another lesson are refused rather than scored", () => {
  const served = serve([0, "q0"]);
  const strayed = build({served, documents: [document("q0"), document("q1", {lessonId: "lesson-2"})]});
  assert.strictEqual(strayed.built, false);
  assert.strictEqual(strayed.reason, REASON.WRONG_LESSON);
  assert.strictEqual(strayed.detail, "lesson-2");
  assert.strictEqual(strayed.questions, null);

  // A document that names no lesson cannot be placed in this one either.
  const unplaced = build({served, documents: [document("q0", {lessonId: ""})]});
  assert.strictEqual(unplaced.built, false);
  assert.strictEqual(unplaced.reason, REASON.WRONG_LESSON);
});

test("two documents sharing an id are refused, naming the id", () => {
  const refusal = build({
    served: serve([0, "q0"]),
    documents: [document("q0"), document("q1"), document("q0", {order: 4})],
  });
  assert.strictEqual(refusal.built, false);
  assert.strictEqual(refusal.reason, REASON.DUPLICATE_DOCUMENT);
  assert.strictEqual(refusal.detail, "q0");
});

test("nothing served is an empty pool and the scorer's own 'nothing was shown'", () => {
  const pool = build({served: [], documents: [document("q0"), document("q1")]});
  assert.strictEqual(pool.built, true);
  assert.deepStrictEqual(pool.questions, []);
  assert.deepStrictEqual(pool.missing, []);

  const scored = scoreAttempt({questions: pool.questions, served: [], keyDocument: null, answers: []});
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(scored.codeAnswer, "");
  assert.strictEqual(scored.percentScore, 0);
  assert.deepStrictEqual(scored.unscorable, []);
});

test("a lesson that moved on is built anyway, with the difference reported", () => {
  const documents = [document("q0", {version: 7}), document("q1", {version: 9}), document("q2", {version: 3})];
  const served = serve([0, "q0"], [1, "q1"]);

  const moved = buildScoringPool({lessonId: LESSON, lessonVersion: 3, served, documents});
  assert.strictEqual(moved.built, true, "a version difference never refuses a pool");
  assert.deepStrictEqual(idsAt(moved.questions), ["q0", "q1"]);
  assert.deepStrictEqual(
    moved.versionDrift,
    {attempt: 3, documents: [7, 9]},
    "only the versions that went into the pool, sorted, and only the ones that differ",
  );

  const settled = buildScoringPool({
    lessonId: LESSON,
    lessonVersion: 7,
    served: serve([0, "q0"]),
    documents,
  });
  assert.strictEqual(settled.versionDrift, null, "agreement is not a difference");

  const unstated = buildScoringPool({lessonId: LESSON, served, documents});
  assert.strictEqual(unstated.versionDrift, null, "no version to compare against, nothing to report");
});

// ---------------------------------------------------------------------------------------------
// The end-to-end case: the digits land under the questions `served` names
// ---------------------------------------------------------------------------------------------

test("every digit lands where served put it, whatever order the documents arrived in", () => {
  // Ids that encode nothing about position, so a builder that re-derived an order out of the
  // documents would have to invent one — and would land the digits somewhere else.
  const documents = [
    document("delta"),
    document("spare"),
    document("alpha"),
    document("gamma"),
  ];
  const served = serve([1, "gamma"], [3, "alpha"], [4, "delta"]);
  const answers = [
    answer("alpha", 3, PARTIAL),
    answer("gamma", 1, PERFECT),
    // `delta` was served and never answered: '1', counted, worth nothing.
  ];
  // Position 0 and 2 were never dealt ('0'); 1 is a perfect answer; 3 is a partial one; 4 was shown
  // and abandoned. Written down rather than recomputed.
  const EXPECTED = NOT_SHOWN + DIGIT.perfect + NOT_SHOWN + DIGIT.partial + NO_VALID_ANSWER;

  for (const order of [documents, [...documents].reverse()]) {
    const pool = buildScoringPool({lessonId: LESSON, lessonVersion: 1, served, documents: order});
    assert.strictEqual(pool.built, true);
    assert.deepStrictEqual(idsAt(pool.questions), [null, "gamma", null, "alpha", "delta"]);

    const scored = scoreAttempt({questions: pool.questions, served, keyDocument: null, answers});
    assert.strictEqual(scored.scorable, true, "the pool is one the scorer accepts");
    assert.strictEqual(scored.codeAnswer, EXPECTED);
    assert.strictEqual(scored.percentScore, computePercentScore(EXPECTED));
    assert.strictEqual(scored.percentScore, 50);
    assert.deepStrictEqual(scored.unscorable, []);
  }
});

test("the pool carries only what the scorer reads, and never the difficulty inside the payload", () => {
  const documents = [document("q0", {payload: json({...MULTIPLE, difficulty: "HARD"})})];
  const pool = build({served: serve([0, "q0"]), documents});
  assert.deepStrictEqual(
    pool.questions,
    [{id: "q0", lessonId: LESSON, payload: json({...MULTIPLE, difficulty: "HARD"})}],
    "id, lessonId and the raw payload — nothing else, and the payload untouched",
  );
});

// ---------------------------------------------------------------------------------------------
// The inputs it will not build from
// ---------------------------------------------------------------------------------------------

test("an attempt with no lesson, and a served list that is not one, are refused by name", () => {
  for (const lessonId of [undefined, null, "", 7]) {
    const refusal = buildScoringPool({lessonId, served: [], documents: []});
    assert.strictEqual(refusal.built, false, `lessonId ${json(lessonId)}`);
    assert.strictEqual(refusal.reason, REASON.UNUSABLE_LESSON_ID);
  }
  for (const served of [undefined, null, "q0", {0: "q0"}]) {
    const refusal = buildScoringPool({lessonId: LESSON, served, documents: []});
    assert.strictEqual(refusal.built, false, `served ${json(served)}`);
    assert.strictEqual(refusal.reason, REASON.SERVED_UNKNOWN);
  }
});

test("a served entry the pool cannot be sized or placed from is refused, not repaired", () => {
  const cases = [
    ["not an object", [null]],
    ["a position that is not an integer", serve([1.5, "q0"])],
    ["a negative position", serve([-1, "q0"])],
    ["a position past the accepted range", serve([MAX_SERVED_POSITION + 1, "q0"])],
    ["a blank id", serve([0, ""])],
    ["an id that is not a string", serve([0, 7])],
    ["a repeated position", serve([0, "q0"], [0, "q1"])],
    ["a repeated id", serve([0, "q0"], [1, "q0"])],
  ];
  for (const [why, served] of cases) {
    const refusal = build({served, documents: [document("q0"), document("q1")]});
    assert.strictEqual(refusal.built, false, why);
    assert.strictEqual(refusal.reason, REASON.SERVED_MALFORMED, why);
  }
  // The last position it *will* build to, so the bound is a boundary rather than a guess.
  const edge = build({served: serve([MAX_SERVED_POSITION, "q0"]), documents: [document("q0")]});
  assert.strictEqual(edge.built, true);
  assert.strictEqual(edge.questions.length, MAX_SERVED_POSITION + 1);
});

test("an entry that is not a question document is refused, naming where it sat", () => {
  for (const [why, stray] of [["null", null], ["a list", []], ["no id", {lessonId: LESSON}], ["a numeric id", {id: 7, lessonId: LESSON}]]) {
    const refusal = build({served: serve([0, "q0"]), documents: [document("q0"), stray]});
    assert.strictEqual(refusal.built, false, why);
    assert.strictEqual(refusal.reason, REASON.MALFORMED_DOCUMENT, why);
    assert.strictEqual(refusal.detail, "1", `${why} — the position in the list handed over`);
  }
});

// ---------------------------------------------------------------------------------------------
// Guards
// ---------------------------------------------------------------------------------------------

test("a crafted served id cannot take a filler's place", () => {
  // `/unserved/0` is the id position 0's filler would take. A device that names it in `served`
  // would, if the collision were allowed, put a `payload: null` under an id the served list holds —
  // and turn its own crafted body into a *server* fault, which under the payment rule means
  // nothing paid and nothing charged.
  const served = serve([2, `${UNSERVED_ID_PREFIX}0`]);
  const documents = [document(`${UNSERVED_ID_PREFIX}0`)];

  const pool = build({served, documents});
  assert.strictEqual(pool.built, true);
  assert.strictEqual(new Set(pool.questions.map((entry) => entry.id)).size, 3, "every id is distinct");
  assert.strictEqual(pool.questions[2].id, `${UNSERVED_ID_PREFIX}0`);
  assert.strictEqual(pool.questions[2].payload, json(MULTIPLE), "the served question keeps its payload");

  const scored = scoreAttempt({
    questions: pool.questions,
    served,
    keyDocument: null,
    answers: [answer(`${UNSERVED_ID_PREFIX}0`, 2, PERFECT)],
  });
  assert.strictEqual(scored.scorable, true, "no server fault is reachable from a crafted served id");
  assert.strictEqual(scored.codeAnswer, NOT_SHOWN + NOT_SHOWN + DIGIT.perfect);
});

test("the position bound is the one intake accepts, not a second opinion about it", () => {
  assert.strictEqual(
    MAX_SERVED_POSITION,
    attemptIntake.MAX_SERVED_POSITION,
    "a pool that stops short of a position intake accepts refuses an attempt intake let through",
  );
});

test("building reads its inputs and writes to none of them", () => {
  const documents = [document("q0"), document("q1")];
  const served = serve([0, "q0"], [2, "q1"]);
  const before = json({documents, served});

  buildScoringPool({lessonId: LESSON, lessonVersion: 2, served, documents});
  assert.strictEqual(json({documents, served}), before);
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
  console.error(`scoring-pool.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`scoring-pool.test.js OK (${SUITE.length} cases)`);
}
