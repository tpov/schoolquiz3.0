"use strict";

const assert = require("assert");
const {
  STATUS,
  KEY_VERSION,
  redact,
  restoreAnswer,
  restoreContent,
  translateSubmittedAnswer,
} = require("./question-redaction");
const {evaluateAnswer} = require("./assessment-scoring");
const {questionCharsCount, lessonAllocatedSeconds} = require("./lesson-reward");
// Shared with question-redaction-wire.test.js: a fixture's `seed` refers to this exact stream, so
// the generator cannot be allowed to exist twice. See _seeded-random.js.
const {seeded} = require("./_seeded-random");

/**
 * The suite registers itself.
 *
 * A list of calls maintained by hand at the bottom of the file is a list that eventually disagrees
 * with the file: a test that is written but never called passes forever, and nothing says so.
 */
const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);


/** An object whose keys may be anything, including `__proto__` — see the module's toPlainMap. */
function plainMap(entries) {
  const map = {};
  for (const [key, value] of entries) {
    Object.defineProperty(map, key, {value, enumerable: true, writable: true, configurable: true});
  }
  return map;
}

const json = (value) => JSON.stringify(value);
const split = (payload, difficulty, seed) =>
  redact(json(payload), difficulty, {random: seeded(seed === undefined ? 7 : seed)});
const publicHalf = (payload, difficulty, seed) => JSON.parse(split(payload, difficulty, seed).publicPayload);

// -- Fixtures. Ids follow the seed corpus (`_helpers.js:19-23`), not the authoring component, so a
// -- rule that reads an ordinal out of an id fails here rather than in production.

const SINGLE = {
  type: "SingleChoice",
  id: "q-sc",
  difficulty: "EASY",
  text: "Which keyword declares a read-only binding?",
  imageUrl: null,
  options: [{id: "a", text: "var"}, {id: "b", text: "val"}, {id: "c", text: "lateinit"}],
  correctOptionId: "b",
  info: "val is the read-only one",
};

const MULTIPLE = {
  type: "MultipleChoice",
  id: "q-mc",
  difficulty: "HARD",
  text: "Which of these run on the JVM?",
  imageUrl: null,
  options: [
    {id: "a", text: "Kotlin"},
    {id: "b", text: "Swift"},
    {id: "c", text: "Scala"},
    {id: "d", text: "Rust"},
    {id: "e", text: "Elm"},
  ],
  correctOptionIds: ["a", "c"],
  info: "Kotlin and Scala compile to bytecode",
};

const ORDERING = {
  type: "Ordering",
  id: "q-ord",
  difficulty: "HARD",
  text: "Put the build steps in order",
  imageUrl: null,
  items: [
    {id: "i1", text: "compile"},
    {id: "i2", text: "test"},
    {id: "i3", text: "package"},
    {id: "i4", text: "deploy"},
  ],
  info: "compile comes first",
};

/** Two items is legal (`items.size in 2..8`) and is where a fair shuffle is the identity half the time. */
const ORDERING_PAIR = {
  ...ORDERING,
  id: "q-ord-2",
  items: [{id: "i1", text: "compile"}, {id: "i2", text: "deploy"}],
};

const FILL_BLANK = {
  type: "FillBlank",
  id: "q-fb",
  difficulty: "EASY",
  text: "___ compiles to bytecode, ___ to machine code, and ___ to JavaScript.",
  imageUrl: null,
  blanks: [
    {id: "b1", correctCandidateId: "c1"},
    {id: "b2", correctCandidateId: "c3"},
    {id: "b3", correctCandidateId: "c5"},
  ],
  candidates: [
    {id: "c1", text: "Kotlin"},
    {id: "c2", text: "Perl"},
    {id: "c3", text: "Rust"},
    {id: "c4", text: "Haskell"},
    {id: "c5", text: "Elm"},
    {id: "c6", text: "Ada"},
    {id: "c7", text: "Nim"},
    {id: "c8", text: "Zig"},
    {id: "c9", text: "Lua"},
    {id: "c10", text: "Tcl"},
  ],
  info: "Kotlin, Rust, Elm",
};

const SURVEY = {
  type: "Survey",
  id: "q-sv",
  difficulty: "EASY",
  text: "Which editor do you use?",
  imageUrl: null,
  options: [{id: "a", text: "IntelliJ"}, {id: "b", text: "VS Code"}],
  allowMultiple: false,
  info: "no right answer",
};

const ALL_SCORED = [SINGLE, MULTIPLE, ORDERING, FILL_BLANK];

// --------------------------------------------------------------------------------------------
// The Matrix, row by row.
// --------------------------------------------------------------------------------------------

test("SingleChoice keeps its options and surrenders the pointer", () => {
  const {status, publicPayload, key} = split(SINGLE, "EASY");
  const pub = JSON.parse(publicPayload);

  assert.strictEqual(status, STATUS.REDACTED);
  assert.strictEqual(pub.type, "SingleChoiceRedacted");
  assert.deepStrictEqual(pub.options, SINGLE.options);
  assert.strictEqual(pub.correctOptionId, undefined);
  assert.strictEqual(pub.info, undefined);
  assert.deepStrictEqual(key, {
    version: KEY_VERSION,
    questionId: "q-sc",
    type: "SingleChoice",
    correctOptionId: "b",
  });
});

test("MultipleChoice hides how many are correct", () => {
  const two = split(MULTIPLE, "HARD").publicPayload;
  const three = split({...MULTIPLE, correctOptionIds: ["a", "c", "e"]}, "HARD").publicPayload;

  // Not "the count is absent" — the two public halves are the same bytes, so the count cannot be
  // recovered by any reading of one of them.
  assert.strictEqual(two, three);
  assert.strictEqual(JSON.parse(two).type, "MultipleChoiceRedacted");
  assert.deepStrictEqual(split(MULTIPLE, "HARD").key.correctOptionIds, ["a", "c"]);
});

test("Ordering is shuffled and re-issued", () => {
  const {publicPayload, key} = split(ORDERING, "HARD");
  const pub = JSON.parse(publicPayload);

  assert.strictEqual(pub.type, "OrderingRedacted");
  // Ids run ascending along the shuffled order — numbering along the original order would move the
  // leak into the id sequence rather than remove it.
  assert.deepStrictEqual(pub.items.map((item) => item.id), ["ri-0", "ri-1", "ri-2", "ri-3"]);
  assert.deepStrictEqual(
    pub.items.map((item) => item.text).slice().sort(),
    ORDERING.items.map((item) => item.text).slice().sort(),
  );
  assert.deepStrictEqual(key.order, ["i1", "i2", "i3", "i4"]);
  assert.deepStrictEqual(pub.items.map((item) => key.idMap[item.id]).slice().sort(), ["i1", "i2", "i3", "i4"]);
  // No original id survives anywhere in the published bytes.
  for (const item of ORDERING.items) assert.ok(!publicPayload.includes(`"${item.id}"`));
});

test("FillBlank shuffles its candidates and keeps the blank order", () => {
  const {publicPayload, key} = split(FILL_BLANK, "EASY");
  const pub = JSON.parse(publicPayload);

  assert.strictEqual(pub.type, "FillBlankRedacted");
  // Blanks are bare ids in the author's order: that order lines them up with the markers in `text`
  // and says nothing about which candidate fills which.
  assert.deepStrictEqual(pub.blanks, ["b1", "b2", "b3"]);
  assert.deepStrictEqual(
    pub.candidates.map((row) => row.id),
    Array.from({length: 10}, (_, index) => `rc-${index}`),
  );
  assert.deepStrictEqual({...key.blankToCandidate}, {b1: "c1", b2: "c3", b3: "c5"});
  assert.deepStrictEqual(
    pub.candidates.map((row) => key.idMap[row.id]).slice().sort(),
    FILL_BLANK.candidates.map((row) => row.id).slice().sort(),
  );
  assert.strictEqual(pub.info, undefined);
  assert.ok(!publicPayload.includes("correctCandidateId"));
});

test("a Survey has no key to take", () => {
  const payload = json(SURVEY);
  const result = redact(payload, "EASY", {random: seeded(7)});
  // Nothing is graded, so there is nothing to keep and nothing to hide.
  assert.strictEqual(result.status, STATUS.NOT_APPLICABLE);
  assert.strictEqual(result.key, null);
  assert.strictEqual(result.publicPayload, payload);
});

test("the economy fields are identical before and after", () => {
  for (const original of ALL_SCORED) {
    const pub = publicHalf(original, "EASY");

    assert.strictEqual(pub.difficulty, original.difficulty, `difficulty for ${original.type}`);
    assert.strictEqual(pub.text, original.text, `text for ${original.type}`);
    assert.strictEqual(pub.imageUrl, original.imageUrl, `imageUrl for ${original.type}`);

    // Every option / item / candidate text survives, as a multiset — the lists are reordered.
    const texts = (content) => [content.options, content.items, content.candidates]
      .filter(Array.isArray)
      .flatMap((rows) => rows.map((row) => row.text))
      .sort();
    assert.deepStrictEqual(texts(pub), texts(original), `texts for ${original.type}`);

    // The two readers that spend this: the reward's size factor and the unlock price.
    assert.strictEqual(
      questionCharsCount(pub),
      questionCharsCount(original),
      `questionCharsCount for ${original.type}`,
    );
    for (const isHard of [false, true]) {
      assert.strictEqual(
        lessonAllocatedSeconds([{id: original.id, content: pub}], isHard),
        lessonAllocatedSeconds([{id: original.id, content: original}], isHard),
        `lessonAllocatedSeconds(${isHard}) for ${original.type}`,
      );
    }
  }
});

test("an image still counts for its hundred characters", () => {
  const withImage = {...SINGLE, imageUrl: "gs://bucket/q-sc.png"};
  const pub = publicHalf(withImage, "EASY");
  assert.strictEqual(pub.imageUrl, withImage.imageUrl);
  assert.strictEqual(questionCharsCount(pub), questionCharsCount(withImage));
});

test("the seed corpus id schemes redact correctly", () => {
  // The published corpus uses a/b/c, i1..i4, c1..c5, b1..b3 — no authoring prefix anywhere. These
  // fixtures already use it; this asserts the five-candidate variant and the payload shape the
  // seeder actually writes, which carries no `id` field at all (`_helpers.js:6-16`).
  const seedShaped = {
    type: "FillBlank",
    difficulty: "HARD",
    text: "A ___ compiles ahead of time.",
    imageUrl: null,
    blanks: [{id: "b1", correctCandidateId: "c4"}],
    candidates: [
      {id: "c1", text: "script"},
      {id: "c2", text: "notebook"},
      {id: "c3", text: "macro"},
      {id: "c4", text: "compiler"},
      {id: "c5", text: "shell"},
    ],
    info: null,
  };
  const {publicPayload, key} = redact(json(seedShaped), "HARD", {random: seeded(3)});
  const pub = JSON.parse(publicPayload);

  assert.strictEqual(pub.id, undefined);
  assert.strictEqual(pub.difficulty, "HARD");
  assert.deepStrictEqual(pub.blanks, ["b1"]);
  assert.deepStrictEqual({...key.blankToCandidate}, {b1: "c4"});
  // No document id was handed in and the payload has none, so there is nothing to stamp.
  assert.strictEqual(key.questionId, null);
  assert.strictEqual(questionCharsCount(pub), questionCharsCount(seedShaped));
  assert.strictEqual(
    lessonAllocatedSeconds([{id: "q", content: pub}], true),
    lessonAllocatedSeconds([{id: "q", content: seedShaped}], true),
  );

  const orderingPub = publicHalf({...ORDERING, id: undefined}, "HARD", 11);
  assert.deepStrictEqual(orderingPub.items.map((row) => row.id), ["ri-0", "ri-1", "ri-2", "ri-3"]);
});

test("an unrecognised payload comes back untouched, and refused", () => {
  const cases = [
    // The concrete legacy shape (`KotlinxSerializationQuestionContentParser.kt:82`).
    ['{"type":"single-choice","options":["a","b"],"correctIndex":0}', "unknown-type"],
    ["{not json", "malformed-json"],
    ["", "malformed-json"],
    ['{"type":"Telepathy","options":[]}', "unknown-type"],
    ['{"options":[{"id":"a","text":"x"}],"correctOptionId":"a"}', "unknown-type"],
    ["null", "not-an-object"],
    ["42", "not-an-object"],
    ['["SingleChoice"]', "not-an-object"],
    ['{"type":null}', "unknown-type"],
  ];
  for (const [payload, reason] of cases) {
    const result = redact(payload, "EASY", {random: seeded(7)});
    // Refused, not "fine": the legacy shape still carries `correctIndex`, and an unknown
    // discriminator is a payload nobody here has read.
    assert.strictEqual(result.status, STATUS.REFUSED, `status for ${payload}`);
    assert.strictEqual(result.reason, reason, `reason for ${payload}`);
    assert.strictEqual(result.key, null, `key for ${payload}`);
    assert.strictEqual(result.publicPayload, payload, `payload for ${payload}`);
  }

  // Non-string input is not a payload at all, and still must not throw.
  for (const value of [undefined, null, 42, {type: "SingleChoice"}, []]) {
    const result = redact(value, "EASY");
    assert.strictEqual(result.status, STATUS.REFUSED);
    assert.strictEqual(result.reason, "not-a-string");
    assert.strictEqual(result.key, null);
    assert.strictEqual(result.publicPayload, value);
  }
});

// --------------------------------------------------------------------------------------------
// The two properties.
// --------------------------------------------------------------------------------------------

test("the answer is recoverable from the key alone", () => {
  assert.deepStrictEqual(restoreAnswer(split(SINGLE, "EASY").key), {
    type: "SingleChoice",
    correctOptionId: SINGLE.correctOptionId,
  });
  assert.deepStrictEqual(restoreAnswer(split(MULTIPLE, "HARD").key), {
    type: "MultipleChoice",
    correctOptionIds: MULTIPLE.correctOptionIds,
  });
  assert.deepStrictEqual(restoreAnswer(split(ORDERING, "HARD").key), {
    type: "Ordering",
    order: ORDERING.items.map((item) => item.id),
  });
  const fillBlank = restoreAnswer(split(FILL_BLANK, "EASY").key);
  assert.strictEqual(fillBlank.type, "FillBlank");
  assert.deepStrictEqual(
    {...fillBlank.blankToCandidate},
    Object.fromEntries(FILL_BLANK.blanks.map((blank) => [blank.id, blank.correctCandidateId])),
  );

  // A key it does not recognise yields nothing rather than a half-answer.
  for (const value of [null, undefined, {}, {type: "Survey"}, {type: "Ordering"}, 7]) {
    assert.strictEqual(restoreAnswer(value), null);
  }
  // And the copy is defensive: mutating what came back must not reach into the key.
  const key = split(ORDERING, "HARD").key;
  restoreAnswer(key).order.push("i9");
  assert.deepStrictEqual(key.order, ["i1", "i2", "i3", "i4"]);
});

test("the public half alone does not carry the answer", () => {
  for (const original of ALL_SCORED) {
    const {publicPayload} = split(original, "EASY");
    for (const field of ["correctOptionId", "correctOptionIds", "correctCandidateId", "info"]) {
      assert.ok(!publicPayload.includes(field), `${field} leaked from ${original.type}`);
    }
  }
  // Ordering and FillBlank re-issue their ids, so the key's own vocabulary is absent too.
  const ordering = split(ORDERING, "HARD");
  assert.ok(!ordering.publicPayload.includes("idMap"));
  for (const id of Object.values(ordering.key.idMap)) {
    assert.ok(!ordering.publicPayload.includes(`"${id}"`), `${id} leaked`);
  }
});

test("a protected segment never republishes an answer, even in context", () => {
  // The live fixture pairs c1 -> "Kotlin" with protectedTextSegments: ["Kotlin"]
  // (`QuestionContentParserTest.kt:76`). The segments are lifted out of the author's prose, so an
  // answer arrives punctuated and in context — an equality test lets every one of those through.
  const protectedFixture = {
    ...FILL_BLANK,
    protectedTextSegments: ["Kotlin", "Kotlin,", "the Kotlin language", " rust ", "ELM", "bytecode"],
  };
  const {publicPayload} = split(protectedFixture, "EASY");
  const pub = JSON.parse(publicPayload);

  assert.deepStrictEqual(pub.protectedTextSegments, ["bytecode"]);
  for (const answer of ["Kotlin", "Rust", "Elm"]) {
    // Exactly one occurrence left: the candidate row, whose text the economy requires.
    const occurrences = publicPayload.toLowerCase().split(answer.toLowerCase()).length - 1;
    assert.strictEqual(occurrences, 1, `${answer} appears ${occurrences} times`);
  }

  // A payload with no such field does not grow one.
  assert.strictEqual(publicHalf(FILL_BLANK, "EASY").protectedTextSegments, undefined);
  // An empty list stays an empty list rather than disappearing.
  assert.deepStrictEqual(
    publicHalf({...FILL_BLANK, protectedTextSegments: []}, "EASY").protectedTextSegments,
    [],
  );
  // A candidate with no text must not make every segment look like an answer.
  const blankTexted = {
    ...FILL_BLANK,
    candidates: FILL_BLANK.candidates.map((row) => (row.id === "c1" ? {id: "c1", text: ""} : row)),
    protectedTextSegments: ["bytecode"],
  };
  assert.deepStrictEqual(publicHalf(blankTexted, "EASY").protectedTextSegments, ["bytecode"]);
});

// --------------------------------------------------------------------------------------------
// The cross-module assertions: what the scorer makes of both halves.
// --------------------------------------------------------------------------------------------

test("a redacted payload scores the floor", () => {
  const single = publicHalf(SINGLE, "EASY");
  assert.strictEqual(evaluateAnswer(single, {type: "single-choice", selected: "b"}), 1);

  const multiple = publicHalf(MULTIPLE, "HARD");
  assert.strictEqual(evaluateAnswer(multiple, {type: "multiple-choice", selected: ["a", "c"]}), 1);

  // The one that was actually wrong before the discriminator: submitting the displayed sequence.
  const ordering = publicHalf(ORDERING, "HARD");
  assert.strictEqual(
    evaluateAnswer(ordering, {type: "ordering", order: ordering.items.map((item) => item.id)}),
    1,
  );
  // And the true answer against the redacted half is no better — neither reading pays.
  assert.strictEqual(evaluateAnswer(ordering, {type: "ordering", order: ["i1", "i2", "i3", "i4"]}), 1);

  const fillBlank = publicHalf(FILL_BLANK, "EASY");
  assert.strictEqual(
    evaluateAnswer(fillBlank, {type: "fill-blank", filled: {b1: "rc-0", b2: "rc-1", b3: "rc-2"}}),
    1,
  );
});

test("the key and the public half score full together", () => {
  const single = split(SINGLE, "EASY");
  assert.strictEqual(
    evaluateAnswer(restoreContent(single.publicPayload, single.key), {
      type: "single-choice",
      selected: single.key.correctOptionId,
    }),
    9,
  );

  const multiple = split(MULTIPLE, "HARD");
  assert.strictEqual(
    evaluateAnswer(restoreContent(multiple.publicPayload, multiple.key), {
      type: "multiple-choice",
      selected: multiple.key.correctOptionIds,
    }),
    9,
  );

  const ordering = split(ORDERING, "HARD");
  const restoredOrdering = restoreContent(ordering.publicPayload, ordering.key);
  assert.strictEqual(
    evaluateAnswer(restoredOrdering, {type: "ordering", order: ordering.key.order}),
    9,
  );
  // The restored question is the original question again, items in the author's order and all —
  // this is the step a naive merge skips, scoring the true answer 5 against its own key.
  assert.deepStrictEqual(restoredOrdering.items, ORDERING.items);

  const fillBlank = split(FILL_BLANK, "EASY");
  const restoredFillBlank = restoreContent(fillBlank.publicPayload, fillBlank.key);
  assert.strictEqual(
    evaluateAnswer(restoredFillBlank, {type: "fill-blank", filled: {...fillBlank.key.blankToCandidate}}),
    9,
  );
  assert.deepStrictEqual(restoredFillBlank.blanks, FILL_BLANK.blanks);
  assert.deepStrictEqual(
    restoredFillBlank.candidates.slice().sort((a, b) => a.id.localeCompare(b.id)),
    FILL_BLANK.candidates.slice().sort((a, b) => a.id.localeCompare(b.id)),
  );
});

test("restoreContent takes a parsed half and refuses a mismatched pair", () => {
  const ordering = split(ORDERING, "HARD");
  // A string or an already-parsed object, either way.
  assert.deepStrictEqual(
    restoreContent(JSON.parse(ordering.publicPayload), ordering.key),
    restoreContent(ordering.publicPayload, ordering.key),
  );

  const other = split({...ORDERING, id: "q-other"}, "HARD");
  // Question A's key against question B's half — the whole reason questionId is stamped.
  assert.strictEqual(restoreContent(other.publicPayload, ordering.key), null);
  // A key written under a different contract must not be read under this one.
  assert.strictEqual(
    restoreContent(ordering.publicPayload, {...ordering.key, version: KEY_VERSION + 1}),
    null,
  );
  // Crossed types, an id the map does not know, a half that is not one of ours.
  const single = split(SINGLE, "EASY");
  assert.strictEqual(restoreContent(single.publicPayload, ordering.key), null);
  assert.strictEqual(restoreContent('{"type":"SingleChoice"}', single.key), null);
  assert.strictEqual(restoreContent("{not json", single.key), null);
  assert.strictEqual(restoreContent(ordering.publicPayload, null), null);
  const holed = JSON.parse(ordering.publicPayload);
  holed.items[0].id = "ri-99";
  assert.strictEqual(restoreContent(holed, ordering.key), null);
});

test("a submitted answer is translated out of the re-issued ids", () => {
  const ordering = split(ORDERING, "HARD");
  const pub = JSON.parse(ordering.publicPayload);
  const content = restoreContent(pub, ordering.key);

  // A client that drags the displayed rows into the right sequence sends `ri-` ids; scoring them
  // means translating first, and this is where that lives rather than at each call site.
  const displayedCorrect = pub.items
    .map((item) => item.id)
    .slice()
    .sort((a, b) => ordering.key.order.indexOf(ordering.key.idMap[a]) -
      ordering.key.order.indexOf(ordering.key.idMap[b]));
  const submitted = translateSubmittedAnswer({type: "ordering", order: displayedCorrect}, ordering.key);
  assert.deepStrictEqual(submitted.order, ordering.key.order);
  assert.strictEqual(evaluateAnswer(content, submitted), 9);

  // An id the map does not know becomes null, which cannot collide with a real id.
  const stale = translateSubmittedAnswer({type: "ordering", order: ["ri-0", "ri-9", "ri-1", "ri-2"]}, ordering.key);
  assert.ok(stale.order.includes(null));
  assert.strictEqual(evaluateAnswer(content, stale), 1);

  const fillBlank = split(FILL_BLANK, "EASY");
  const fillBlankPub = JSON.parse(fillBlank.publicPayload);
  const byOriginal = new Map(fillBlankPub.candidates.map((row) => [fillBlank.key.idMap[row.id], row.id]));
  const filled = {
    b1: byOriginal.get("c1"),
    b2: byOriginal.get("c3"),
    b3: byOriginal.get("c5"),
  };
  const translated = translateSubmittedAnswer({type: "fill-blank", filled}, fillBlank.key);
  assert.deepStrictEqual({...translated.filled}, {b1: "c1", b2: "c3", b3: "c5"});
  assert.strictEqual(evaluateAnswer(restoreContent(fillBlankPub, fillBlank.key), translated), 9);

  // Choice types are handed back as they came — their option ids were never re-issued.
  const single = split(SINGLE, "EASY");
  const passed = translateSubmittedAnswer({type: "single-choice", selected: "b"}, single.key);
  assert.deepStrictEqual(passed, {type: "single-choice", selected: "b"});
  assert.strictEqual(evaluateAnswer(restoreContent(single.publicPayload, single.key), passed), 9);

  // Junk in, floor out — never a throw.
  assert.strictEqual(translateSubmittedAnswer(null, single.key), null);
  assert.strictEqual(translateSubmittedAnswer({}, null), null);
  assert.strictEqual(translateSubmittedAnswer({}, {type: "Survey"}), null);
  assert.deepStrictEqual(translateSubmittedAnswer({type: "ordering"}, ordering.key).order, []);
  assert.deepStrictEqual({...translateSubmittedAnswer({type: "fill-blank"}, fillBlank.key).filled}, {});
});

// --------------------------------------------------------------------------------------------
// Refusal, statuses, idempotence, and the injected randomness.
// --------------------------------------------------------------------------------------------

test("a payload that cannot be split completely is refused whole", () => {
  const refused = [
    ["dangling-correct-option", {...SINGLE, correctOptionId: "zz"}],
    ["missing-correct-option", {...SINGLE, correctOptionId: undefined}],
    ["empty-correct-options", {...MULTIPLE, correctOptionIds: []}],
    ["dangling-correct-option", {...MULTIPLE, correctOptionIds: ["a", "zz"]}],
    ["dangling-correct-option", {...MULTIPLE, correctOptionIds: ["a", 3]}],
    ["missing-correct-options", {...MULTIPLE, correctOptionIds: undefined}],
    ["invalid-options", {...SINGLE, options: [{id: "a", text: "x"}, null]}],
    ["invalid-options", {...SINGLE, options: [{id: "a", text: "x"}, "b"]}],
    ["invalid-options", {...SINGLE, options: [{id: "a", text: "x"}, {id: "b"}]}],
    ["invalid-options", {...SINGLE, options: [{id: 1, text: "x"}, {id: "b", text: "y"}]}],
    // A one-row list is all answer, and there is no shuffle of one row.
    ["invalid-options", {...SINGLE, options: [{id: "b", text: "val"}]}],
    ["invalid-items", {...ORDERING, items: [{id: "i1", text: "compile"}]}],
    ["invalid-candidates", {
      ...FILL_BLANK,
      candidates: [{id: "c1", text: "Kotlin"}],
      blanks: [{id: "b1", correctCandidateId: "c1"}],
    }],
    ["invalid-items", {...ORDERING, items: [{id: "i1", text: "x"}, null]}],
    ["duplicate-item-ids", {...ORDERING, items: [{id: "i1", text: "x"}, {id: "i1", text: "y"}]}],
    ["invalid-items", {...ORDERING, items: []}],
    ["invalid-candidates", {...FILL_BLANK, candidates: [{id: "c1", text: "x"}, null]}],
    ["duplicate-candidate-ids", {
      ...FILL_BLANK,
      candidates: [{id: "c1", text: "x"}, {id: "c1", text: "y"}],
      blanks: [{id: "b1", correctCandidateId: "c1"}],
    }],
    ["invalid-blanks", {...FILL_BLANK, blanks: [null]}],
    ["dangling-correct-candidate", {...FILL_BLANK, blanks: [{id: "b1", correctCandidateId: "c99"}]}],
    ["duplicate-blank-ids", {
      ...FILL_BLANK,
      blanks: [{id: "b1", correctCandidateId: "c1"}, {id: "b1", correctCandidateId: "c3"}],
    }],
    ["invalid-blanks", {...FILL_BLANK, blanks: []}],
    ["invalid-protected-segments", {...FILL_BLANK, protectedTextSegments: [3]}],
    ["invalid-protected-segments", {...FILL_BLANK, protectedTextSegments: "Kotlin"}],
    ["missing-text", {...SINGLE, text: 42}],
    ["missing-text", {...SINGLE, text: undefined}],
    ["invalid-image-url", {...SINGLE, imageUrl: 7}],
    ["invalid-difficulty", {...SINGLE, difficulty: 2}],
  ];
  for (const [reason, payload] of refused) {
    const encoded = json(payload);
    const result = redact(encoded, "EASY", {random: seeded(7)});
    // The status is the point: the payload still holds its answer, so the caller must not publish
    // it. The old shape said only `key: null`, which a Survey says too.
    assert.strictEqual(result.status, STATUS.REFUSED, `status for ${reason}`);
    assert.strictEqual(result.reason, reason, `reason for ${reason}`);
    assert.strictEqual(result.key, null, `key kept for ${reason}`);
    assert.strictEqual(result.publicPayload, encoded, `payload changed for ${reason}`);
  }
});

test("a second pass is a no-op, and says so distinctly", () => {
  for (const original of ALL_SCORED) {
    const first = split(original, "EASY");
    const second = redact(first.publicPayload, "EASY", {random: seeded(9)});
    // Not `refused`: a caller that stores a non-null key and clears a null one would wipe the
    // stored key on every republish if these two were the same status.
    assert.strictEqual(second.status, STATUS.ALREADY_REDACTED, `${original.type} redacted twice`);
    assert.strictEqual(second.reason, null);
    assert.strictEqual(second.key, null);
    assert.strictEqual(second.publicPayload, first.publicPayload);
  }
});

test("the four outcomes are told apart", () => {
  assert.deepStrictEqual(
    [
      split(SINGLE, "EASY").status,
      split(SURVEY, "EASY").status,
      redact(split(SINGLE, "EASY").publicPayload, "EASY").status,
      split({...SINGLE, correctOptionId: "zz"}, "EASY").status,
    ],
    [STATUS.REDACTED, STATUS.NOT_APPLICABLE, STATUS.ALREADY_REDACTED, STATUS.REFUSED],
  );
  // Only a refusal carries a reason, and only a success carries a key.
  assert.strictEqual(split(SINGLE, "EASY").reason, null);
  assert.ok(split(SINGLE, "EASY").key);
  assert.strictEqual(split(SURVEY, "EASY").reason, null);
});

test("an empty difficulty is a value, not an absence", () => {
  // `parseQuestionPayload` merges {...fallback, ...parsed}, so the payload's "" already wins over
  // the document field, and lessonAllocatedSeconds reads "" || "EASY" as EASY. Publishing "HARD"
  // here would move the question between pools and change its reward, its price and its timer.
  const empty = {...SINGLE, difficulty: ""};
  const pub = publicHalf(empty, "HARD");
  assert.strictEqual(pub.difficulty, "");
  for (const isHard of [false, true]) {
    assert.strictEqual(
      lessonAllocatedSeconds([{id: "q", content: pub}], isHard),
      lessonAllocatedSeconds([{id: "q", content: empty}], isHard),
      `lessonAllocatedSeconds(${isHard}) for an empty difficulty`,
    );
  }

  // The payload's own value wins whenever it has one.
  assert.strictEqual(publicHalf(SINGLE, "HARD").difficulty, "EASY");
  // The argument fills only a genuinely absent field — losing it would default a hard question to
  // EASY and zero its reward.
  assert.strictEqual(publicHalf({...SINGLE, difficulty: undefined}, "HARD").difficulty, "HARD");
  assert.strictEqual(publicHalf({...SINGLE, difficulty: null}, "HARD").difficulty, "HARD");
  // With nothing on either side the field stays absent rather than being invented.
  assert.strictEqual(publicHalf({...SINGLE, difficulty: undefined}, "").difficulty, undefined);
  assert.strictEqual(publicHalf({...SINGLE, difficulty: undefined}, undefined).difficulty, undefined);
});

test("the shuffle never publishes the canonical order", () => {
  // Two items is legal and is where a fair shuffle is the identity half the time — one published
  // question in two would otherwise show its answer in the order it was drawn in. Asserted across
  // many seeds, because a single fixed seed proves nothing about the other 10,000.
  for (let seed = 0; seed < 250; seed += 1) {
    for (const fixture of [ORDERING_PAIR, ORDERING]) {
      const {publicPayload, key} = split(fixture, "HARD", seed);
      const shown = JSON.parse(publicPayload).items.map((item) => key.idMap[item.id]);
      assert.notDeepStrictEqual(shown, key.order, `canonical order published at seed ${seed}`);
    }
    const fillBlank = split(FILL_BLANK, "EASY", seed);
    const candidates = JSON.parse(fillBlank.publicPayload).candidates
      .map((row) => fillBlank.key.idMap[row.id]);
    assert.notDeepStrictEqual(
      candidates,
      FILL_BLANK.candidates.map((row) => row.id),
      `canonical candidates published at seed ${seed}`,
    );
  }
  // Even a source that clamps to the identity every single time cannot force it.
  const stuck = redact(json(ORDERING_PAIR), "HARD", {random: () => 1});
  const stuckShown = JSON.parse(stuck.publicPayload).items.map((item) => stuck.key.idMap[item.id]);
  assert.notDeepStrictEqual(stuckShown, stuck.key.order);
  assert.deepStrictEqual(stuckShown.slice().sort(), ["i1", "i2"]);
});

test("the shuffle is the injected one, and the default is not Math.random", () => {
  const a = split(ORDERING, "HARD", 7);
  const b = split(ORDERING, "HARD", 7);
  assert.strictEqual(a.publicPayload, b.publicPayload);
  assert.deepStrictEqual(a.key, b.key);

  // Different seeds must actually reach the output, or "injectable randomness" is decorative.
  const orders = new Set(
    [1, 2, 3, 4, 5, 6, 7, 8].map((seed) => publicHalf(ORDERING, "HARD", seed).items.map((i) => i.text).join()),
  );
  assert.ok(orders.size > 1, "the seed does not reach the shuffle");

  // Left to itself the module draws from crypto.randomInt: every shuffle it publishes is an
  // observed output of the generator, and Math.random's state is recoverable from those.
  const drawn = new Set();
  for (let run = 0; run < 40; run += 1) {
    const unseeded = redact(json(ORDERING), "HARD");
    assert.strictEqual(unseeded.status, STATUS.REDACTED);
    assert.deepStrictEqual(unseeded.key.order, ["i1", "i2", "i3", "i4"]);
    drawn.add(JSON.parse(unseeded.publicPayload).items.map((item) => item.text).join());
  }
  assert.ok(drawn.size > 1, "the default source does not vary");

  // Varying is not the property that matters — Math.random varies too. The property is that it is
  // never reached, so the published orders reveal nothing about a recoverable generator state.
  const realRandom = Math.random;
  let mathRandomCalls = 0;
  Math.random = () => {
    mathRandomCalls += 1;
    return realRandom();
  };
  try {
    redact(json(ORDERING), "HARD");
    redact(json(FILL_BLANK), "EASY");
  } finally {
    Math.random = realRandom;
  }
  assert.strictEqual(mathRandomCalls, 0, "the default shuffle source reached Math.random");
});

test("a misbehaving random source loses nothing", () => {
  // Out of range, negative, and not a number at all: a shuffle that indexed on these would drop a
  // row out of a published question rather than fail loudly.
  for (const random of [() => 1, () => 1.9, () => -3, () => NaN, () => 0]) {
    const {publicPayload, key} = redact(json(FILL_BLANK), "EASY", {random});
    const pub = JSON.parse(publicPayload);
    assert.strictEqual(pub.candidates.length, FILL_BLANK.candidates.length);
    assert.deepStrictEqual(
      pub.candidates.map((row) => key.idMap[row.id]).slice().sort(),
      FILL_BLANK.candidates.map((row) => row.id).slice().sort(),
    );
    assert.strictEqual(questionCharsCount(pub), questionCharsCount(FILL_BLANK));
    assert.strictEqual(evaluateAnswer(restoreContent(pub, key), {
      type: "fill-blank",
      filled: {...key.blankToCandidate},
    }), 9);
  }
  // A non-function `random` falls back to the default rather than throwing.
  assert.ok(redact(json(ORDERING), "HARD", {random: "nope"}).key);
  assert.ok(redact(json(ORDERING), "HARD", {}).key);
});

test("the key names its question and its contract", () => {
  // The document id is authoritative: the seed corpus payloads carry no `id` of their own.
  const {key} = redact(json({...SINGLE, id: undefined}), "EASY", {
    random: seeded(7),
    questionId: "questions/abc",
  });
  assert.strictEqual(key.questionId, "questions/abc");
  assert.strictEqual(key.version, KEY_VERSION);
  // The payload's own id is the fallback.
  assert.strictEqual(split(SINGLE, "EASY").key.questionId, "q-sc");
  // Handed neither, it says so rather than guessing.
  assert.strictEqual(redact(json({...SINGLE, id: undefined}), "EASY").key.questionId, null);
  for (const original of ALL_SCORED) {
    assert.strictEqual(split(original, "EASY").key.version, KEY_VERSION);
  }
});

test("an id that is also an object property name survives", () => {
  // "__proto__" assigned onto a plain object sets no own property, so a key built by assignment
  // would silently lose this blank's answer and publish an unscoreable question.
  const hostile = {
    ...FILL_BLANK,
    blanks: [
      {id: "__proto__", correctCandidateId: "c1"},
      {id: "constructor", correctCandidateId: "c3"},
      {id: "toString", correctCandidateId: "c5"},
    ],
  };
  const {publicPayload, key} = split(hostile, "EASY");
  const pub = JSON.parse(publicPayload);
  // The expectation has to be built the same careful way, for the same reason: written as an object
  // literal, `__proto__: "c1"` sets no own property either.
  const expected = plainMap([["__proto__", "c1"], ["constructor", "c3"], ["toString", "c5"]]);

  assert.deepStrictEqual(pub.blanks, ["__proto__", "constructor", "toString"]);
  assert.deepStrictEqual({...key.blankToCandidate}, expected);
  assert.deepStrictEqual(Object.keys(key.blankToCandidate), ["__proto__", "constructor", "toString"]);
  // And it survives the round trip through Firestore's only serialisation.
  assert.deepStrictEqual({...JSON.parse(json(key)).blankToCandidate}, expected);
  assert.strictEqual(
    evaluateAnswer(restoreContent(pub, key), {type: "fill-blank", filled: {...key.blankToCandidate}}),
    9,
  );
});

test("nothing throws on anything", () => {
  const junk = [
    undefined, null, 0, -1, NaN, true, "", " ", "{", "}", "[]", "[[]]", '"x"', "undefined",
    json({type: "SingleChoice"}),
    json({type: "Ordering", items: "i1,i2"}),
    json({type: "FillBlank", blanks: {b1: "c1"}, candidates: []}),
    json({type: "MultipleChoice", options: null, correctOptionIds: null}),
    json({type: "SingleChoice", options: [], correctOptionId: "a", text: "t"}),
    json({type: "Survey"}),
  ];
  for (const value of junk) {
    const result = redact(value, undefined, {random: seeded(1)});
    assert.ok(result && typeof result.status === "string", `no status for ${String(value)}`);
    restoreAnswer(result.key);
    restoreContent(result.publicPayload, result.key);
    translateSubmittedAnswer({type: "ordering", order: ["ri-0"]}, result.key);
  }
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
  console.error(`question-redaction.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`question-redaction.test.js OK (${SUITE.length} cases)`);
}
