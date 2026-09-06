"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {NOT_SHOWN, NO_VALID_ANSWER, MAX_RECORDS, FAULT, FAULT_OF, UNSCORABLE, scoreAttempt} =
  require("./attempt-scoring");
const {evaluateAnswer, computePercentScore} = require("./assessment-scoring");
const {
  restoreContent,
  translateSubmittedAnswer,
  KEY_VERSION,
  CONTENT_TYPE,
} = require("./question-redaction");
const {lessonKeyDocument, PUBLIC_HALF_REDACTED, DOCUMENT_VERSION} = require("./question-key-store");
const {seeded} = require("./_seeded-random");

/**
 * Two suites in one file, because they prove different things.
 *
 * The fixture cases below pin the *numbers*: an attempt in, a stated codeAnswer and a stated percent
 * out. The earlier version of this suite had thirty-two green cases and every one of its percent
 * assertions read `percentScore === computePercentScore(codeAnswer)` — true for any string the
 * module happens to build, and therefore blind to the module building the wrong string. It was:
 * an honest attempt of two right and three wrong scored 40 percent, and sending unparseable text
 * for the three wrong ones scored 100. Numbers that are recomputed from the output cannot catch
 * that. Numbers that are written down can.
 *
 * The hand-written cases pin the *behaviour* around them — redaction, keys, faults, budgets — and
 * they build their key documents with the real `lessonKeyDocument` rather than a literal, so a
 * change to the document's shape breaks them here instead of silently making every redacted
 * question unscorable in production.
 */
const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/**
 * The fixture lives on the Kotlin side for the same reason the scoring and redaction ones do:
 * `src/jvmTest/resources` is the only location Gradle already tracks as a test input and that
 * Kotlin can load off the classpath with no working-directory assumption. This suite takes the
 * relative path, stable because both ends are in one repository.
 */
const FIXTURE_PATH = path.join(
  __dirname,
  "../shared/core/scoring/src/jvmTest/resources/attempt-scoring-fixtures.json",
);

/**
 * Loaded from inside a test, not at module scope: a missing file or a trailing comma at module
 * scope aborts the run with a bare SyntaxError and no mention of which file was being read.
 */
let cached = null;
function fixtures() {
  if (cached) return cached;
  assert.ok(
    fs.existsSync(FIXTURE_PATH),
    `Attempt scoring fixtures missing at ${FIXTURE_PATH}. Without them every percent assertion ` +
    "here is recomputed from the module's own output, which is what let the denominator bug through.",
  );
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(FIXTURE_PATH, "utf8"));
  } catch (error) {
    assert.fail(`Could not parse ${FIXTURE_PATH}: ${error.message}`);
  }
  cached = parsed;
  return cached;
}

const json = (value) => JSON.stringify(value);
const rng = (seed) => seeded(seed === undefined ? 11 : seed);

/** Builds one fixture case's inputs. Question ids are `q0…` by position, as `served` names them. */
function caseInputs(fixture, testCase) {
  const questions = testCase.questions.map((name, at) => {
    const content = fixture.contents[name];
    assert.ok(content, `${testCase.name}: no content named ${name}`);
    return {id: `q${at}`, lessonId: "lesson-1", difficulty: "", payload: json(content)};
  });
  const served = testCase.served === null || testCase.served === undefined
    ? testCase.served
    : testCase.served.map((at) => ({codeAnswerIndex: at, questionId: `q${at}`}));
  const answers = testCase.answers.map((row, arrivedAt) => ({
    questionId: row.questionId === undefined ? `q${row.at}` : row.questionId,
    codeAnswerIndex: row.at,
    // The client's own number, always a lie in these fixtures. Nothing in the module may read it.
    score: 9,
    answerPayload: typeof row.answerPayload === "string" ? row.answerPayload : json(row.answerPayload),
    answeredAtMs: 1700000000000 + arrivedAt,
    durationMs: 4200,
    wasTimeout: false,
  }));
  return {questions, served, answers, keyDocument: testCase.keyDocument || null};
}

test("every fixture case scores exactly the codeAnswer and percent it states", () => {
  const fixture = fixtures();
  assert.ok(fixture.cases.length >= 15, `only ${fixture.cases.length} fixture cases`);

  for (const testCase of fixture.cases) {
    const result = scoreAttempt(caseInputs(fixture, testCase));
    const where = `${testCase.name}: ${testCase.why}`;

    assert.strictEqual(result.scorable, testCase.expect.scorable, `${where} — scorable`);
    assert.strictEqual(result.codeAnswer, testCase.expect.codeAnswer, `${where} — codeAnswer`);
    assert.strictEqual(result.percentScore, testCase.expect.percentScore, `${where} — percent`);
    assert.deepStrictEqual(
      result.unscorable.map((row) => ({
        questionId: row.questionId,
        codeAnswerIndex: row.codeAnswerIndex,
        reason: row.reason,
        fault: row.fault,
      })),
      testCase.expect.unscorable,
      `${where} — records`,
    );
  }
});

test("the fixture states percents that are not merely what the module computed", () => {
  // The guard against this suite drifting back into a tautology: at least one case must state a
  // percent that is neither 0 nor 100, and the stated codeAnswer must independently produce it.
  const fixture = fixtures();
  const scored = fixture.cases.filter((row) => row.expect.codeAnswer !== null);
  const partial = scored.filter((row) => row.expect.percentScore > 0 && row.expect.percentScore < 100);
  assert.ok(partial.length >= 3, `only ${partial.length} partial-percent cases`);
  for (const row of scored) {
    assert.strictEqual(
      computePercentScore(row.expect.codeAnswer),
      row.expect.percentScore,
      `${row.name}: the fixture's own codeAnswer and percent disagree`,
    );
  }
});

// ---------------------------------------------------------------------------------------------
// The denominator, stated as the property it is
// ---------------------------------------------------------------------------------------------

const SINGLE = {
  type: "SingleChoice",
  text: "Which keyword declares a read-only binding?",
  imageUrl: null,
  options: [{id: "a", text: "var"}, {id: "b", text: "val"}],
  correctOptionId: "b",
};

const ORDERING = {
  type: "Ordering",
  text: "Put the lifecycle callbacks in order",
  imageUrl: null,
  items: [
    {id: "i1", text: "onCreate"},
    {id: "i2", text: "onStart"},
    {id: "i3", text: "onResume"},
    {id: "i4", text: "onPause"},
  ],
};

const FILL_BLANK = {
  type: "FillBlank",
  text: "*** compiles to the JVM and *** does too",
  imageUrl: null,
  blanks: [{id: "b1", correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c3"}],
  candidates: [
    {id: "c1", text: "Kotlin"},
    {id: "c2", text: "Swift"},
    {id: "c3", text: "Scala"},
    {id: "c4", text: "Rust"},
    {id: "c5", text: "Go"},
  ],
  protectedTextSegments: ["Kotlin", "Scala"],
};

const MULTIPLE = {
  type: "MultipleChoice",
  text: "Which of these run on the JVM?",
  imageUrl: null,
  options: [{id: "a", text: "Kotlin"}, {id: "b", text: "Swift"}, {id: "c", text: "Scala"}],
  correctOptionIds: ["a", "c"],
};

const ANSWERS = {
  SingleChoice: {
    perfect: {type: "single-choice", selected: "b"},
    wrong: {type: "single-choice", selected: "a"},
  },
  MultipleChoice: {
    perfect: {type: "multiple-choice", selected: ["a", "c"]},
    partial: {type: "multiple-choice", selected: ["a"]},
    overreach: {type: "multiple-choice", selected: ["a", "b", "c"]},
  },
  Ordering: {
    perfect: {type: "ordering", order: ["i1", "i2", "i3", "i4"]},
    partial: {type: "ordering", order: ["i1", "i2", "i4", "i3"]},
    reversed: {type: "ordering", order: ["i4", "i3", "i2", "i1"]},
    junk: {type: "ordering", order: ["i1", "i2", "i3", "nope"]},
  },
  FillBlank: {
    perfect: {type: "fill-blank", filled: {b1: "c1", b2: "c3"}},
    partial: {type: "fill-blank", filled: {b1: "c1", b2: "c2"}},
    wrong: {type: "fill-blank", filled: {b1: "c5", b2: "c4"}},
  },
};

function question(id, payload) {
  return {id, lessonId: "lesson-1", difficulty: "", payload: typeof payload === "string" ? payload : json(payload)};
}

function answer(questionId, at, payload, answeredAtMs) {
  return {
    questionId,
    codeAnswerIndex: at,
    score: 9,
    answerPayload: typeof payload === "string" ? payload : json(payload),
    answeredAtMs: answeredAtMs === undefined ? 1700000000000 : answeredAtMs,
    durationMs: 4200,
    wasTimeout: false,
  };
}

const servedAt = (...positions) => positions.map((at) => ({codeAnswerIndex: at, questionId: `q${at}`}));

test("no route the client controls can shrink the denominator", () => {
  // The bug this module was rebuilt around, stated as a property rather than as three fixture rows.
  // Every way a client can decline to answer honestly must reach the same digit at that position.
  const pool = [0, 1, 2].map((at) => question(`q${at}`, SINGLE));
  const served = servedAt(0, 1, 2);
  const right = answer("q0", 0, ANSWERS.SingleChoice.perfect);

  const routes = {
    "answered wrongly": [right, answer("q1", 1, ANSWERS.SingleChoice.wrong), answer("q2", 2, ANSWERS.SingleChoice.wrong)],
    "unparseable payload": [right, answer("q1", 1, "{"), answer("q2", 2, "")],
    "answers omitted": [right],
    "an empty answer array": [right],
    "a shape that fits nothing": [right, answer("q1", 1, {type: "nope"}), answer("q2", 2, {})],
    "a stolen index": [right, answer("q1", 0, ANSWERS.SingleChoice.perfect), answer("q2", 9, ANSWERS.SingleChoice.perfect)],
    "no question id": [right, answer("", 1, ANSWERS.SingleChoice.perfect), answer("", 2, ANSWERS.SingleChoice.perfect)],
  };

  for (const [route, answers] of Object.entries(routes)) {
    const result = scoreAttempt({questions: pool, served, keyDocument: null, answers});
    assert.strictEqual(result.scorable, true, `${route}: became unscorable`);
    assert.strictEqual(result.codeAnswer, "911", `${route} produced ${result.codeAnswer}`);
    assert.strictEqual(result.percentScore, 33, `${route} scored ${result.percentScore}%`);
  }
});

test("a question the subset never reached stays out of the average", () => {
  const pool = [0, 1, 2].map((at) => question(`q${at}`, SINGLE));
  const reached = scoreAttempt({
    questions: pool,
    served: servedAt(0),
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect)],
  });
  assert.strictEqual(reached.codeAnswer, "900");
  assert.strictEqual(reached.percentScore, 100, "two unserved questions dragged the average down");
});

test("served-but-unanswered is '1' and unserved is '0' — the two are never confused", () => {
  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const result = scoreAttempt({questions: pool, served: servedAt(0), keyDocument: null, answers: []});
  assert.strictEqual(result.codeAnswer, `${NO_VALID_ANSWER}${NOT_SHOWN}`);
  assert.strictEqual(result.percentScore, 0);
});

test("the client's own score is never read", () => {
  const pool = [question("q0", SINGLE)];
  const claimed = [{...answer("q0", 0, ANSWERS.SingleChoice.wrong), score: 9}];
  const result = scoreAttempt({questions: pool, served: servedAt(0), keyDocument: null, answers: claimed});
  assert.strictEqual(result.codeAnswer, "1");
});

test("answers arriving in any order score identically, and the newest duplicate wins", () => {
  const pool = [0, 1, 2].map((at) => question(`q${at}`, SINGLE));
  const served = servedAt(0, 1, 2);
  const rows = [
    answer("q0", 0, ANSWERS.SingleChoice.perfect, 100),
    answer("q1", 1, ANSWERS.SingleChoice.wrong, 200),
    answer("q2", 2, ANSWERS.SingleChoice.perfect, 300),
  ];
  const straight = scoreAttempt({questions: pool, served, keyDocument: null, answers: rows});
  const reversed = scoreAttempt({questions: pool, served, keyDocument: null, answers: rows.slice().reverse()});
  assert.strictEqual(reversed.codeAnswer, straight.codeAnswer);
  assert.deepStrictEqual(reversed.unscorable, straight.unscorable, "records depend on arrival order");

  // A sync batch can deliver a re-answer before the answer it replaces. The newest by answeredAtMs
  // is the one scored, whichever order it arrived in.
  const stale = answer("q0", 0, ANSWERS.SingleChoice.wrong, 100);
  const fresh = answer("q0", 0, ANSWERS.SingleChoice.perfect, 900);
  for (const order of [[stale, fresh], [fresh, stale]]) {
    const result = scoreAttempt({questions: pool, served: servedAt(0), keyDocument: null, answers: order});
    assert.strictEqual(result.codeAnswer, "900", "the stale answer was scored");
    assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.DUPLICATE_QUESTION);
  }
});

// ---------------------------------------------------------------------------------------------
// Key documents, built by the writer that actually writes them
// ---------------------------------------------------------------------------------------------

/**
 * A lesson whose key document and public payloads both come out of one `lessonKeyDocument` call.
 *
 * Nothing here is hand-edited any more. The store used to keep only the key, so this fixture drew
 * the public halves from a second generator seeded identically and then overrode
 * `publicHalfRedacted` by hand — a fixture describing a world no exported function could build. The
 * store now states the stamp on the caller's word and hands back the half it keyed, so the fixture
 * is the store's own output and the "both halves from one call" invariant is structural rather than
 * re-derived and asserted.
 *
 * `staleDocument` is the same lesson keyed the way the catalog backfill keys it — same questions,
 * same shuffle, no published half, so the default stamp. It is what proves the guard still guards:
 * identical keys, and the scorer must still refuse them.
 */
function redactedLesson(specs, seed) {
  const questions = specs.map(({id, payload}) => question(id, payload));
  const published = lessonKeyDocument("lesson-1", questions, {
    random: rng(seed),
    publicHalfRedacted: true,
  });
  const backfilled = lessonKeyDocument("lesson-1", questions, {random: rng(seed)});

  assert.strictEqual(published.document.version, DOCUMENT_VERSION);
  assert.deepStrictEqual(
    published.document.refusals,
    [],
    "the store refused a question this fixture needs",
  );
  assert.strictEqual(published.document.publicHalfRedacted, true, "the stated stamp was not written");
  assert.strictEqual(
    backfilled.document.publicHalfRedacted,
    PUBLIC_HALF_REDACTED,
    "a caller that said nothing did not get the safe default",
  );
  // Same seed, same call sequence: the two documents differ in the stamp and in nothing else, which
  // is what makes `staleDocument` a generation mismatch rather than a different shuffle.
  assert.deepStrictEqual(backfilled.document.keys, published.document.keys);

  const publicQuestions = [];
  const keyById = new Map();
  for (const {id} of specs) {
    const stored = published.document.keys.find((entry) => entry.questionId === id);
    assert.ok(stored, `${id} has no key in the document the store built`);
    const half = published.publicPayloads.find((entry) => entry.questionId === id);
    assert.ok(half, `${id} was keyed but its public half did not come back`);
    publicQuestions.push({id, lessonId: "lesson-1", difficulty: "", payload: half.payload});
    keyById.set(id, stored.key);
  }

  return {
    questions: publicQuestions,
    keyDocument: published.document,
    staleDocument: backfilled.document,
    keyById,
  };
}

/** `key.idMap` runs re-issued → original; a client answers in the re-issued ids, so invert it. */
function asSubmitted(key, original) {
  if (!key.idMap) return {...original};
  const forward = new Map();
  for (const reissued of Object.keys(key.idMap)) forward.set(key.idMap[reissued], reissued);
  const map = (id) => (forward.has(id) ? forward.get(id) : id);
  if (key.type === CONTENT_TYPE.ORDERING) return {...original, order: original.order.map(map)};
  if (key.type === CONTENT_TYPE.FILL_BLANK) {
    const filled = {};
    for (const blankId of Object.keys(original.filled)) filled[blankId] = map(original.filled[blankId]);
    return {...original, filled};
  }
  return {...original};
}

test("a redacted question scores exactly as the unredacted original does", () => {
  const originals = {SingleChoice: SINGLE, MultipleChoice: MULTIPLE, Ordering: ORDERING, FillBlank: FILL_BLANK};
  let checked = 0;

  for (const [type, original] of Object.entries(originals)) {
    for (const seed of [1, 2, 3, 17, 99]) {
      const lesson = redactedLesson([{id: "q0", payload: original}], seed);
      const key = lesson.keyById.get("q0");

      for (const [name, payload] of Object.entries(ANSWERS[type])) {
        const submitted = asSubmitted(key, payload);
        if (key.idMap) {
          assert.notDeepStrictEqual(
            submitted,
            payload,
            `${type}/${name}/seed ${seed}: the submitted answer is the original one`,
          );
        }
        const result = scoreAttempt({
          questions: lesson.questions,
          served: servedAt(0),
          keyDocument: lesson.keyDocument,
          answers: [answer("q0", 0, submitted)],
        });
        const expected = evaluateAnswer(original, payload);

        assert.strictEqual(result.scorable, true, `${type}/${name}/seed ${seed} was not scorable`);
        assert.deepStrictEqual(result.unscorable, [], `${type}/${name}/seed ${seed}`);
        assert.strictEqual(
          result.codeAnswer,
          String(expected),
          `${type}/${name}/seed ${seed}: redacted ${result.codeAnswer}, unredacted ${expected}`,
        );
        checked += 1;
      }
    }
  }
  assert.ok(checked >= 55, `only ${checked} pairings compared`);
});

test("a whole redacted lesson scores the same attempt as its unredacted twin", () => {
  const specs = [
    {id: "q0", payload: SINGLE},
    {id: "q1", payload: MULTIPLE},
    {id: "q2", payload: ORDERING},
    {id: "q3", payload: FILL_BLANK},
  ];
  const lesson = redactedLesson(specs, 21);
  const played = [
    ANSWERS.SingleChoice.perfect,
    ANSWERS.MultipleChoice.partial,
    ANSWERS.Ordering.partial,
    ANSWERS.FillBlank.partial,
  ];
  const served = servedAt(0, 1, 2, 3);

  const redacted = scoreAttempt({
    questions: lesson.questions,
    served,
    keyDocument: lesson.keyDocument,
    answers: played.map((payload, at) => answer(`q${at}`, at, asSubmitted(lesson.keyById.get(`q${at}`), payload))),
  });
  const plain = scoreAttempt({
    questions: specs.map(({id, payload}) => question(id, payload)),
    served,
    keyDocument: null,
    answers: played.map((payload, at) => answer(`q${at}`, at, payload)),
  });

  assert.strictEqual(redacted.scorable, true);
  assert.deepStrictEqual(redacted.unscorable, []);
  assert.strictEqual(redacted.codeAnswer, plain.codeAnswer);
  assert.strictEqual(redacted.percentScore, plain.percentScore);
  assert.ok(/[2-8]/.test(plain.codeAnswer), `no partial digit in ${plain.codeAnswer}`);
});

test("an ordering answer is translated exactly once", () => {
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 8);
  const key = lesson.keyById.get("q0");
  // A partial answer is the discriminating case: both a translation skipped and one done twice
  // collapse an Ordering to the floor, so only exactly one pass can land a middling digit.
  const expected = evaluateAnswer(ORDERING, ANSWERS.Ordering.partial);
  assert.ok(expected > 1 && expected < 9, `the fixture answer is not partial: ${expected}`);

  const run = (payload) => scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: lesson.keyDocument,
    answers: [answer("q0", 0, payload)],
  }).codeAnswer;

  assert.strictEqual(run(asSubmitted(key, ANSWERS.Ordering.partial)), String(expected));
  assert.strictEqual(run(ANSWERS.Ordering.partial), "1", "the answer was scored untranslated");

  const doubled = translateSubmittedAnswer(
    translateSubmittedAnswer(asSubmitted(key, ANSWERS.Ordering.partial), key),
    key,
  );
  assert.ok(doubled.order.every((id) => id === null), "a second pass should erase every id");
  assert.notStrictEqual(evaluateAnswer(ORDERING, doubled), expected);
});

test("the backfill's generation is never applied to a published half", () => {
  // `lessonKeyDocument`'s output for a caller that keyed without publishing: publicHalfRedacted
  // false, keys describing a shuffle nobody was shown. Against a redacted payload that is our gap,
  // not a score — even though these are byte for byte the keys that would score it if the document
  // said the halves had been published. That is the whole of what the stamp is for.
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 15);
  const result = scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: lesson.staleDocument,
    answers: [answer("q0", 0, asSubmitted(lesson.keyById.get("q0"), ANSWERS.Ordering.perfect))],
  });
  assert.strictEqual(result.scorable, false);
  assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.KEY_GENERATION);
});

test("a crossed pair reassembles silently, which is why the generation flag is load-bearing", () => {
  const published = redactedLesson([{id: "q0", payload: ORDERING}], 15);
  const other = redactedLesson([{id: "q0", payload: ORDERING}], 16);
  assert.notDeepStrictEqual(published.keyById.get("q0").idMap, other.keyById.get("q0").idMap);

  const crossed = restoreContent(published.questions[0].payload, other.keyById.get("q0"));
  assert.ok(crossed !== null, "the mismatch is invisible to reassembly — hence the guard");
  assert.notDeepStrictEqual(crossed.items, ORDERING.items);
});

test("publicHalfRedacted must be exactly true for a key to be applied", () => {
  const lesson = redactedLesson([{id: "q0", payload: FILL_BLANK}], 18);
  const submitted = answer("q0", 0, asSubmitted(lesson.keyById.get("q0"), ANSWERS.FillBlank.perfect));
  for (const flag of [undefined, null, false, 0, "true", 1]) {
    const result = scoreAttempt({
      questions: lesson.questions,
      served: servedAt(0),
      keyDocument: {...lesson.keyDocument, publicHalfRedacted: flag},
      answers: [submitted],
    });
    assert.strictEqual(result.scorable, false, `publicHalfRedacted ${json(flag)} was treated as true`);
    assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.KEY_GENERATION);
  }
});

test("a key document that is not the shape the store writes refuses the attempt loudly", () => {
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 30);
  const good = lesson.keyDocument;
  const submitted = [answer("q0", 0, asSubmitted(lesson.keyById.get("q0"), ANSWERS.Ordering.perfect))];
  const run = (document) => scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: document,
    answers: submitted,
  });

  const broken = {
    // The shape that used to read as "no keys" and quietly returned an all-zero codeAnswer for
    // every hard attempt in the corpus.
    [UNSCORABLE.DOCUMENT_MALFORMED]: [
      {...good, keys: {q0: good.keys[0].key}},
      {...good, keys: [{questionId: 42, key: good.keys[0].key}]},
      {...good, keys: [{questionId: "q0", key: {...good.keys[0].key, questionId: 42}}]},
      {...good, keys: [{questionId: "q0", key: null}]},
      {...good, keys: ["nope"]},
    ],
    [UNSCORABLE.DOCUMENT_VERSION]: [{...good, version: DOCUMENT_VERSION + 98}],
    [UNSCORABLE.DOCUMENT_MISMATCHED]: [{...good, lessonId: "another-lesson"}],
  };
  for (const [reason, documents] of Object.entries(broken)) {
    for (const document of documents) {
      const result = scoreAttempt === null ? null : run(document);
      assert.strictEqual(result.scorable, false, `${reason}: ${json(document.keys)} was accepted`);
      assert.strictEqual(result.codeAnswer, null);
      assert.strictEqual(result.unscorable[0].reason, reason);
    }
  }
  assert.strictEqual(run(good).scorable, true, "the unbroken document stopped working");
});

test("a key naming another question is refused rather than applied", () => {
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 9);
  const key = lesson.keyById.get("q0");
  const crossed = {
    ...lesson.keyDocument,
    keys: [{questionId: "q0", key: {...key, questionId: "q-elsewhere"}}],
  };
  const result = scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: crossed,
    answers: [answer("q0", 0, asSubmitted(key, ANSWERS.Ordering.perfect))],
  });
  assert.strictEqual(result.scorable, false);
  assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.KEY_MISMATCHED);
  assert.strictEqual(result.unscorable[0].detail, "q-elsewhere");
});

test("two keys for one question are ambiguous, and an unknown key version is refused", () => {
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 11);
  const key = lesson.keyById.get("q0");
  const submitted = [answer("q0", 0, asSubmitted(key, ANSWERS.Ordering.perfect))];
  const run = (keys) => scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: {...lesson.keyDocument, keys},
    answers: submitted,
  });

  const twice = run([{questionId: "q0", key}, {questionId: "q0", key}]);
  assert.strictEqual(twice.unscorable[0].reason, UNSCORABLE.KEY_AMBIGUOUS);

  assert.strictEqual(key.version, KEY_VERSION, "the fixture must start from the current version");
  const bumped = run([{questionId: "q0", key: {...key, version: KEY_VERSION + 1}}]);
  assert.strictEqual(bumped.unscorable[0].reason, UNSCORABLE.KEY_VERSION);
  assert.strictEqual(bumped.unscorable[0].detail, String(KEY_VERSION + 1));

  const wrongType = redactedLesson([{id: "q0", payload: SINGLE}], 11).keyById.get("q0");
  const mismatched = run([{questionId: "q0", key: {...wrongType, questionId: "q0"}}]);
  assert.strictEqual(mismatched.unscorable[0].reason, UNSCORABLE.UNRESTORABLE);
});

// ---------------------------------------------------------------------------------------------
// Faults, pools, budgets
// ---------------------------------------------------------------------------------------------

test("every reason is classified, and a fault during the walk stops the attempt only when it is ours", () => {
  for (const reason of Object.values(UNSCORABLE)) {
    assert.ok(FAULT_OF[reason], `${reason} has no fault`);
    assert.ok(Object.values(FAULT).includes(FAULT_OF[reason]), `${reason} has an unknown fault`);
  }
  // No reason may be two things at once, which a shared wire string with the store could cause.
  assert.strictEqual(
    new Set(Object.values(UNSCORABLE)).size,
    Object.values(UNSCORABLE).length,
    "two reasons share one wire string",
  );

  // A client fault is a '1' and the attempt still scores; a server fault is no number at all.
  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const client = scoreAttempt({
    questions: pool,
    served: servedAt(0, 1),
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect), answer("q1", 1, "{")],
  });
  assert.strictEqual(client.scorable, true);
  assert.strictEqual(client.codeAnswer, "91");
  assert.strictEqual(client.unscorable[0].fault, FAULT.CLIENT);

  const server = scoreAttempt({
    questions: [question("q0", SINGLE), question("q1", "{ not json")],
    served: servedAt(0, 1),
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect), answer("q1", 1, ANSWERS.SingleChoice.perfect)],
  });
  assert.strictEqual(server.scorable, false);
  assert.strictEqual(server.codeAnswer, null);
  assert.strictEqual(server.percentScore, null);
  assert.strictEqual(server.unscorable[0].reason, UNSCORABLE.MALFORMED_QUESTION);
  assert.strictEqual(server.unscorable[0].fault, FAULT.SERVER);
});

test("an unanswered served question is not examined, so its payload cannot block the attempt", () => {
  // Nothing was scored at that position, so there is nothing we failed to score: '1' is the whole
  // truth about a question the player was shown and walked past, whatever the stored payload says.
  const result = scoreAttempt({
    questions: [question("q0", SINGLE), question("q1", "{ not json")],
    served: servedAt(0, 1),
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect)],
  });
  assert.strictEqual(result.scorable, true);
  assert.strictEqual(result.codeAnswer, "91");
  assert.deepStrictEqual(result.unscorable, []);
});

test("a client cannot convert one of our gaps into a fault of its own", () => {
  // The precedence that makes the fault split honest. Everything about the stored question is
  // settled before the answer is parsed, so posting junk for a question we cannot read still
  // refuses the attempt instead of buying a scored '1' at that position.
  const broken = [question("q0", SINGLE), question("q1", "{ not json")];
  const served = servedAt(0, 1);
  for (const suppressor of ["{", "", json({type: "nope"}), json(ANSWERS.SingleChoice.perfect)]) {
    const result = scoreAttempt({
      questions: broken,
      served,
      keyDocument: null,
      answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect), answer("q1", 1, suppressor)],
    });
    assert.strictEqual(result.scorable, false, `${json(suppressor)} suppressed a server fault`);
    assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.MALFORMED_QUESTION);
    assert.strictEqual(result.unscorable[0].fault, FAULT.SERVER);
  }

  // The same for a missing key: junk in the answer must not turn it into a client fault.
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 41);
  const noKeys = scoreAttempt({
    questions: lesson.questions,
    served: servedAt(0),
    keyDocument: {...lesson.keyDocument, keys: []},
    answers: [answer("q0", 0, "{")],
  });
  assert.strictEqual(noKeys.scorable, false);
  assert.strictEqual(noKeys.unscorable[0].reason, UNSCORABLE.KEY_MISSING);
});

test("a pool that is not a pool is named as such, rather than blamed on an answer", () => {
  const broken = {
    "two questions with one id": [question("q0", SINGLE), question("q0", SINGLE)],
    "a question with no id": [question("", SINGLE)],
    "two lessons in one pool": [question("q0", SINGLE), {...question("q1", SINGLE), lessonId: "other"}],
  };
  for (const [what, questions] of Object.entries(broken)) {
    const result = scoreAttempt({questions, served: servedAt(0), keyDocument: null, answers: []});
    assert.strictEqual(result.scorable, false, what);
    assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.POOL_MALFORMED, what);
    assert.strictEqual(result.unscorable[0].fault, FAULT.SERVER, what);
  }
});

test("a served set that does not fit the pool is refused before any position is walked", () => {
  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const broken = [
    [{codeAnswerIndex: 5, questionId: "q0"}],
    [{codeAnswerIndex: -1, questionId: "q0"}],
    [{codeAnswerIndex: 1.5, questionId: "q0"}],
    [{codeAnswerIndex: 0, questionId: ""}],
    [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 0, questionId: "q1"}],
    [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q0"}],
    ["nope"],
  ];
  for (const served of broken) {
    const result = scoreAttempt({questions: pool, served, keyDocument: null, answers: []});
    assert.strictEqual(result.scorable, false, json(served));
    assert.strictEqual(result.unscorable[0].reason, UNSCORABLE.SERVED_MALFORMED, json(served));
    // Unscorable, and the client's fault: every field of `served` comes off the device, and a
    // position outside the pool is not something the server can produce. Filed as a server fault
    // it made the crafted body free — under the server-scored payment rule our own gap means
    // nothing paid *and nothing charged*, so `codeAnswerIndex: 5` bought an uncharged attempt.
    assert.strictEqual(result.unscorable[0].fault, FAULT.CLIENT, json(served));
  }
  assert.strictEqual(FAULT_OF[UNSCORABLE.SERVED_MALFORMED], FAULT.CLIENT);
  // …and unscorable all the same: the refusal turns on the list being unreadable, not on the fault.
  // Nothing may be walked while `served` is still the reason string `readServed` handed back.
  const crafted = scoreAttempt({
    questions: pool,
    served: [{codeAnswerIndex: 999, questionId: "q0"}],
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect)],
  });
  assert.strictEqual(crafted.scorable, false);
  assert.strictEqual(crafted.codeAnswer, null);
  assert.strictEqual(crafted.percentScore, null);
  assert.deepStrictEqual(crafted.unscorable.map((record) => record.reason), [UNSCORABLE.SERVED_MALFORMED]);
  // A served question the pool does not hold is a different defect and gets its own name — and,
  // unlike the ones above, it is readable: the list is a list, the position is inside the pool, and
  // only the question behind it is absent. So it is scored rather than refused. See the next case.
  // One of the two, not both: a pool holding neither is `LESSON_MISSING` and is refused, which the
  // boundary case below owns.
  const missing = scoreAttempt({
    questions: pool,
    served: [{codeAnswerIndex: 0, questionId: "q-nowhere"}, {codeAnswerIndex: 1, questionId: "q1"}],
    keyDocument: null,
    answers: [],
  });
  assert.strictEqual(missing.unscorable[0].reason, UNSCORABLE.QUESTION_MISSING);
  assert.strictEqual(missing.scorable, true);
});

test("a pool holding none of the served questions is our gap, and is refused", () => {
  // The player-harm side of the reclassification that made `QUESTION_MISSING` the client's. A
  // lesson read from the wrong collection, or a query that came back empty, produced a pool of
  // nothing but fillers: every position walked to `'1'`, the attempt scored, and the player was
  // charged full price for a zero the server's own read had caused.
  assert.strictEqual(FAULT_OF[UNSCORABLE.LESSON_MISSING], FAULT.SERVER);

  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const answers = [
    answer("q0", 0, ANSWERS.SingleChoice.perfect),
    answer("q1", 1, ANSWERS.SingleChoice.perfect),
  ];
  // The pool `scoring-pool.js` builds when the lesson's documents came back empty: one filler per
  // served position, under ids no served entry can name.
  const fillers = [0, 1].map((at) => ({id: `/unserved/${at}`, lessonId: "lesson-1", payload: null}));
  const lost = scoreAttempt({questions: fillers, served: servedAt(0, 1), keyDocument: null, answers});

  assert.strictEqual(lost.scorable, false, "an unreadable lesson still produced a score");
  assert.strictEqual(lost.codeAnswer, null, "the player was given digits for a lesson we lost");
  assert.strictEqual(lost.percentScore, null);
  assert.deepStrictEqual(lost.unscorable, [{
    questionId: "",
    codeAnswerIndex: -1,
    reason: UNSCORABLE.LESSON_MISSING,
    fault: FAULT.SERVER,
    detail: "2",
  }]);

  // The same attempt against the real pool, for contrast: nothing about the answers changed.
  const found = scoreAttempt({questions: pool, served: servedAt(0, 1), keyDocument: null, answers});
  assert.strictEqual(found.scorable, true);
  assert.strictEqual(found.codeAnswer, "99");
});

test("all but one missing is still the client's, and still scores", () => {
  // The boundary, and the reason the two reasons are different reasons. A device can append entries
  // it was never dealt; it cannot empty a lesson. So "some are missing" keeps the treatment that is
  // safe against the invented entry — a `'1'` at that position, the attempt scored and charged —
  // and only "none of them are there" is read as our own failure.
  const pool = [question("q0", SINGLE)];
  const fillers = [1, 2].map((at) => ({id: `/unserved/${at}`, lessonId: "lesson-1", payload: null}));
  const served = [
    {codeAnswerIndex: 0, questionId: "q0"},
    {codeAnswerIndex: 1, questionId: "q-invented"},
    {codeAnswerIndex: 2, questionId: "q-also-invented"},
  ];
  const result = scoreAttempt({
    questions: [...pool, ...fillers],
    served,
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect)],
  });

  assert.strictEqual(result.scorable, true, "two invented entries cancelled the attempt");
  assert.strictEqual(result.codeAnswer, "911");
  assert.deepStrictEqual(
    result.unscorable.map((record) => [record.reason, record.fault]),
    [
      [UNSCORABLE.QUESTION_MISSING, FAULT.CLIENT],
      [UNSCORABLE.QUESTION_MISSING, FAULT.CLIENT],
    ],
  );

  // Take the one real question away and the same list becomes our gap instead.
  const emptied = scoreAttempt({
    questions: [{id: "/unserved/0", lessonId: "lesson-1", payload: null}, ...fillers],
    served,
    keyDocument: null,
    answers: [answer("q0", 0, ANSWERS.SingleChoice.perfect)],
  });
  assert.strictEqual(emptied.scorable, false);
  assert.strictEqual(emptied.unscorable[0].reason, UNSCORABLE.LESSON_MISSING);
  assert.strictEqual(emptied.unscorable[0].fault, FAULT.SERVER);
});

test("a served entry naming no question costs its position and never cancels the attempt", () => {
  // The shape this reclassification exists to price. Filed as our own gap it refused the attempt,
  // and under the server-scored payment rule a refusal means nothing paid *and nothing charged* —
  // so appending one made-up entry was strictly cheaper than playing the lesson. It is now what it
  // looks like from here: a position that was shown and produced no valid answer.
  assert.strictEqual(FAULT_OF[UNSCORABLE.QUESTION_MISSING], FAULT.CLIENT);

  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const answers = [
    answer("q0", 0, ANSWERS.SingleChoice.perfect),
    answer("q1", 1, ANSWERS.SingleChoice.perfect),
  ];
  const honest = scoreAttempt({questions: pool, served: servedAt(0, 1), keyDocument: null, answers});
  // The pool is what `scoring-pool.js` builds for the same crafted list: a filler stands at the
  // invented position, under an id no served entry can name.
  const padded = [...pool, question("/unserved/2", SINGLE)];
  const invented = scoreAttempt({
    questions: padded,
    served: [...servedAt(0, 1), {codeAnswerIndex: 2, questionId: "q-invented"}],
    keyDocument: null,
    answers,
  });

  assert.strictEqual(honest.codeAnswer, "99");
  assert.strictEqual(honest.percentScore, 100);
  assert.strictEqual(invented.scorable, true, "the invented entry refused the attempt, which is free");
  assert.strictEqual(invented.codeAnswer, `99${NO_VALID_ANSWER}`);
  assert.strictEqual(invented.percentScore, 66);
  assert.ok(invented.percentScore < honest.percentScore, "inventing an entry did not cost anything");
  assert.deepStrictEqual(invented.unscorable, [{
    questionId: "q-invented",
    codeAnswerIndex: 2,
    reason: UNSCORABLE.QUESTION_MISSING,
    fault: FAULT.CLIENT,
    detail: null,
  }]);
  // The digit is never the '0' that would drop the position from the denominator, which is the one
  // treatment that would have paid the liar rather than charged them.
  assert.strictEqual(invented.codeAnswer.includes(NOT_SHOWN), false);
});

test("records are bounded, and what did not fit is counted", () => {
  const size = MAX_RECORDS * 3;
  const pool = Array.from({length: size}, (unused, at) => question(`q${at}`, SINGLE));
  const served = Array.from({length: size}, (unused, at) => ({codeAnswerIndex: at, questionId: `q${at}`}));
  const answers = Array.from({length: size}, (unused, at) => answer(`q${at}`, at, "{"));

  const result = scoreAttempt({questions: pool, served, keyDocument: null, answers});
  assert.strictEqual(result.unscorable.length, MAX_RECORDS);
  assert.strictEqual(result.omitted, size - MAX_RECORDS);
  // Bounding what is reported must never bound what is decided: every position is still '1'.
  assert.strictEqual(result.codeAnswer, NO_VALID_ANSWER.repeat(size));
  assert.strictEqual(result.percentScore, 0);
});

test("a server fault past the record budget still stops the attempt being scored", () => {
  const size = MAX_RECORDS + 2;
  const pool = Array.from({length: size}, (unused, at) =>
    question(`q${at}`, at === size - 1 ? "{ not json" : SINGLE));
  const served = Array.from({length: size}, (unused, at) => ({codeAnswerIndex: at, questionId: `q${at}`}));
  const answers = Array.from({length: size}, (unused, at) => answer(`q${at}`, at, "{"));

  const result = scoreAttempt({questions: pool, served, keyDocument: null, answers});
  assert.ok(result.omitted > 0, "the budget was not reached, so this proves nothing");
  assert.strictEqual(result.scorable, false, "a server fault beyond the budget was forgotten");
  assert.strictEqual(result.codeAnswer, null);
});

test("missing, null and junk inputs produce a refusal rather than an exception", () => {
  for (const input of [undefined, null, {}, {questions: null, answers: "nope", served: 7, keyDocument: 7}]) {
    const result = scoreAttempt(input);
    assert.strictEqual(result.scorable, false);
    assert.strictEqual(result.codeAnswer, null);
    assert.strictEqual(result.percentScore, null);
    assert.ok(result.unscorable.some((row) => row.reason === UNSCORABLE.SERVED_UNKNOWN));
  }
});

test("every record names its question, its position, its reason and whose fault it is", () => {
  const pool = [0, 1].map((at) => question(`q${at}`, SINGLE));
  const result = scoreAttempt({
    questions: pool,
    served: servedAt(0),
    keyDocument: null,
    answers: [answer("q0", 0, "{"), answer("q1", 1, ANSWERS.SingleChoice.perfect), answer("", 0, "{")],
  });
  assert.strictEqual(result.unscorable.length, 3);
  for (const record of result.unscorable) {
    assert.deepStrictEqual(
      Object.keys(record).sort(),
      ["codeAnswerIndex", "detail", "fault", "questionId", "reason"],
    );
    assert.strictEqual(typeof record.questionId, "string");
    assert.ok(Number.isInteger(record.codeAnswerIndex));
    assert.ok(Object.values(UNSCORABLE).includes(record.reason), `unknown reason ${record.reason}`);
  }
  // One defect, one label: an answer with no id is named once, not as "unknown" and then "duplicate".
  const nameless = result.unscorable.filter((row) => row.questionId === "");
  assert.strictEqual(nameless.length, 1);
  assert.strictEqual(nameless[0].reason, UNSCORABLE.MISSING_QUESTION_ID);
});

test("scoring reads its inputs and writes to none of them", () => {
  const lesson = redactedLesson([{id: "q0", payload: ORDERING}], 25);
  const served = servedAt(0);
  const answers = [answer("q0", 0, asSubmitted(lesson.keyById.get("q0"), ANSWERS.Ordering.perfect))];
  const before = json({questions: lesson.questions, document: lesson.keyDocument, served, answers});

  scoreAttempt({questions: lesson.questions, served, keyDocument: lesson.keyDocument, answers});
  assert.strictEqual(
    json({questions: lesson.questions, document: lesson.keyDocument, served, answers}),
    before,
  );
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
  console.error(`attempt-scoring.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`attempt-scoring.test.js OK (${SUITE.length} cases)`);
}
