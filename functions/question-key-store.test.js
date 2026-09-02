"use strict";

const assert = require("assert");
const {
  KEY_COLLECTION,
  DOCUMENT_VERSION,
  PUBLIC_HALF_REDACTED,
  MAX_DOCUMENT_BYTES,
  MAX_DOCUMENT_ID_BYTES,
  REASON,
  keyDocumentPath,
  isStorableFieldName,
  isStorableDocumentId,
  lessonKeyDocument,
  questionKeyDocuments,
} = require("./question-key-store");
const {KEY_VERSION, CONTENT_TYPE, REFUSAL, restoreAnswer} = require("./question-redaction");
const {seeded} = require("./_seeded-random");

/**
 * The Matrix from `spec-e2-5-answer-key-written.md`, minus the two rows that need Firestore.
 *
 * "the payload is byte-identical" and "the public document has the same ten fields" are properties
 * of `publicDocuments`, which this module never sees — it is handed questions and hands back one
 * document. What is provable here is everything the document itself promises: a key per splittable
 * question, nothing at all for a Survey, a named reason for every refusal, one document per lesson,
 * and nothing in it that Firestore would throw out.
 */
const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/**
 * A fresh generator per test, never a shared one.
 *
 * `seeded` is stateful: one instance at module scope would be drawn from by every case in file
 * order, so inserting a test — or reordering two — silently re-baselines the shuffle every later
 * case sees. That is the hazard `_seeded-random.js`'s own docstring warns about, and it is
 * invisible when it happens, because the cases still pass against the new stream.
 */
const opts = (seed) => ({random: seeded(seed === undefined ? 7 : seed)});

/**
 * The stored half alone.
 *
 * `lessonKeyDocument` returns `{document, publicPayloads}` — what Firestore is handed, and the
 * public halves the stored keys describe, both out of one `redact` call per question. Most cases
 * below are about the document; the cases about the pairing call `lessonKeyDocument` itself.
 */
const keyDocument = (...args) => lessonKeyDocument(...args).document;

/** Firestore's own hard limit on one document. MAX_DOCUMENT_BYTES budgets underneath it. */
const FIRESTORE_DOCUMENT_LIMIT = 1048576;

const json = (value) => JSON.stringify(value);

/** Wraps a payload the way `normalizeQuestion` leaves it: flat fields beside a JSON string. */
function question(id, lessonId, payload, difficulty) {
  return {
    id,
    lessonId,
    type: payload && payload.type ? payload.type : "",
    // `normalizeQuestion` runs every question through `stringValue`, so an absent difficulty
    // arrives as "" and never as undefined. Defaulting to that here rather than to a level keeps
    // the fixtures honest about what publication actually sends.
    difficulty: difficulty === undefined ? "" : difficulty,
    payload: typeof payload === "string" ? payload : json(payload),
  };
}

// The payloads live in _question-fixtures.js so catalog-redaction-plan.test.js proves its planner
// against the very questions this suite proves the key store against.
const {SINGLE, MULTIPLE, ORDERING, FILL_BLANK, SURVEY} = require("./_question-fixtures");

const keyOf = (document, questionId) => {
  const entry = document.keys.find((item) => item.questionId === questionId);
  return entry ? entry.key : null;
};
const refusalOf = (document, questionId) =>
  document.refusals.find((item) => item.questionId === questionId) || null;
const reasonOf = (document, questionId) => {
  const record = refusalOf(document, questionId);
  return record ? record.reason : null;
};

// --------------------------------------------------------------------------------------------
// Matrix: "A lesson is published | questions of the four splittable types | one key document per
// lesson, holding a key per question id".

test("every splittable type contributes its key to the one lesson document", () => {
  const document = keyDocument("lesson-1", [
    question("q1", "lesson-1", SINGLE),
    question("q2", "lesson-1", MULTIPLE),
    question("q3", "lesson-1", ORDERING),
    question("q4", "lesson-1", FILL_BLANK),
  ], opts());

  assert.strictEqual(document.id, "lesson-1");
  assert.strictEqual(document.lessonId, "lesson-1");
  assert.strictEqual(document.version, DOCUMENT_VERSION);
  assert.deepStrictEqual(document.refusals, []);
  assert.strictEqual(document.omitted, 0);
  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["q1", "q2", "q3", "q4"]);

  // Each key is the answer, in the question's own vocabulary, and stamped with the question it
  // belongs to — a key applied to the wrong question is a wrong answer scored as a right one.
  assert.strictEqual(keyOf(document, "q1").correctOptionId, "b");
  assert.deepStrictEqual(keyOf(document, "q2").correctOptionIds, ["a", "c"]);
  assert.deepStrictEqual(keyOf(document, "q3").order, ["i1", "i2", "i3", "i4"]);
  assert.deepStrictEqual({...keyOf(document, "q4").blankToCandidate}, {b1: "c1", b2: "c2"});
  for (const id of ["q1", "q2", "q3", "q4"]) {
    assert.strictEqual(keyOf(document, id).questionId, id, `key for ${id} names another question`);
    assert.strictEqual(keyOf(document, id).version, KEY_VERSION, `key for ${id} is unversioned`);
  }
  assert.strictEqual(keyOf(document, "q1").type, CONTENT_TYPE.SINGLE_CHOICE);
  assert.strictEqual(keyOf(document, "q4").type, CONTENT_TYPE.FILL_BLANK);
});

test("the shuffle mapping in a key is a complete permutation of the question's own ids", () => {
  // idMap is the only field the shuffle produces, and the only one that has to survive a round
  // trip: lose or duplicate an entry and the reissued id a player's answer arrives under maps to
  // nothing, or to the wrong row. Asserted per type because they re-issue under different prefixes.
  const document = keyDocument("lesson-1", [
    question("q-ord", "lesson-1", ORDERING),
    question("q-fb", "lesson-1", FILL_BLANK),
  ], opts());

  const ordering = keyOf(document, "q-ord");
  assert.deepStrictEqual(Object.keys(ordering.idMap), ["ri-0", "ri-1", "ri-2", "ri-3"]);
  assert.deepStrictEqual(Object.values(ordering.idMap).slice().sort(), ["i1", "i2", "i3", "i4"]);
  // The mapping is a bijection: as many distinct originals out as rows in.
  assert.strictEqual(new Set(Object.values(ordering.idMap)).size, ORDERING.items.length);
  // And the canonical order is kept in the question's own ids, not the reissued ones.
  assert.deepStrictEqual(restoreAnswer(ordering), {type: CONTENT_TYPE.ORDERING, order: ["i1", "i2", "i3", "i4"]});
  for (const id of ordering.order) assert.ok(!id.startsWith("ri-"), `order leaked a reissued id: ${id}`);

  const fillBlank = keyOf(document, "q-fb");
  assert.deepStrictEqual(Object.keys(fillBlank.idMap), ["rc-0", "rc-1", "rc-2"]);
  assert.deepStrictEqual(Object.values(fillBlank.idMap).slice().sort(), ["c1", "c2", "c3"]);
  assert.strictEqual(new Set(Object.values(fillBlank.idMap)).size, FILL_BLANK.candidates.length);
  assert.deepStrictEqual(
    {...restoreAnswer(fillBlank).blankToCandidate},
    {b1: "c1", b2: "c2"},
    "the key alone must spell out its own answer",
  );
});

test("a caller that says nothing gets the safe stamp: the payload beside these keys went out whole", () => {
  // The shuffle these keys record was drawn for a public half that was not published, so nothing
  // has been shown it. Without this field a later reader cannot tell such a key from one written
  // alongside a matching redacted payload, and would translate answers against the wrong
  // permutation — scoring wrong answers correct.
  const document = keyDocument("lesson-1", [question("q1", "lesson-1", ORDERING)], opts());
  assert.strictEqual(document.publicHalfRedacted, PUBLIC_HALF_REDACTED);
  assert.strictEqual(document.publicHalfRedacted, false);
  assert.ok("publicHalfRedacted" in document, "the generation marker must be present, not implied");
  const {documents} = questionKeyDocuments([question("q1", "lesson-1", SINGLE)], opts());
  assert.strictEqual(documents["question_keys/lesson-1"].publicHalfRedacted, false);
});

test("the stamp is the caller's statement, and only a literal true is one", () => {
  // Whether the published half was redacted is a fact about the caller's own write, and no amount
  // of looking at the questions can tell this module which half went out. So it is stated, and
  // every statement that is not exactly `true` falls back to the safe answer — a truthy string or a
  // `1` from some future config reader must not be able to claim a generation by accident. The two
  // errors are not symmetric: a wrong `false` is refused loudly at scoring time (`KEY_GENERATION`),
  // a wrong `true` scores wrong answers correct and says nothing.
  const questions = [question("q1", "lesson-1", ORDERING)];
  const stamped = (publicHalfRedacted) =>
    keyDocument("lesson-1", questions, {...opts(), publicHalfRedacted}).publicHalfRedacted;

  assert.strictEqual(stamped(true), true);
  for (const stated of [false, undefined, null, 0, 1, "true", "yes", {}, []]) {
    assert.strictEqual(stamped(stated), false, `${json(stated)} claimed a generation`);
  }

  // And the same statement, through the function publication actually calls.
  const {documents} = questionKeyDocuments(questions, {...opts(), publicHalfRedacted: true});
  assert.strictEqual(documents["question_keys/lesson-1"].publicHalfRedacted, true);
  // Everything else about the two documents is the same, so the stamp is the only thing that moved.
  assert.deepStrictEqual(
    {...documents["question_keys/lesson-1"], publicHalfRedacted: false},
    keyDocument("lesson-1", questions, opts()),
  );
});

// --------------------------------------------------------------------------------------------
// Matrix: "Halves come out together | the store called for a lesson | it returns the public half it
// keyed for each question, so a caller cannot publish a different one".

test("the public half of every stored key comes back with it", () => {
  const questions = [
    question("q1", "lesson-1", SINGLE),
    question("q2", "lesson-1", MULTIPLE),
    question("q3", "lesson-1", ORDERING),
    question("q4", "lesson-1", FILL_BLANK),
    question("q-survey", "lesson-1", SURVEY),
  ];
  const {document, publicPayloads} = lessonKeyDocument("lesson-1", questions, opts());

  // One per stored key, in key order. The survey has no key and so no half: there is nothing to
  // replace, and the caller publishes what it already had.
  assert.deepStrictEqual(
    publicPayloads.map((half) => half.questionId),
    document.keys.map((entry) => entry.questionId),
  );
  assert.deepStrictEqual(publicPayloads.map((half) => half.questionId), ["q1", "q2", "q3", "q4"]);

  // Each half is a redacted shape with the answer gone, and — the point of returning them at all —
  // it describes the very permutation the key beside it records. `redact` draws a fresh shuffle per
  // call, so a caller that produced these itself would be publishing an arrangement these keys do
  // not describe, and `restoreContent` would reassemble the wrong question or none at all.
  const halfOf = (id) => JSON.parse(publicPayloads.find((half) => half.questionId === id).payload);
  assert.strictEqual(halfOf("q1").type, "SingleChoiceRedacted");
  assert.strictEqual(halfOf("q1").correctOptionId, undefined, "the answer was published");
  assert.strictEqual(halfOf("q2").correctOptionIds, undefined, "the answers were published");

  const itemTexts = new Map(ORDERING.items.map((item) => [item.id, item.text]));
  for (const item of halfOf("q3").items) {
    assert.strictEqual(
      item.text,
      itemTexts.get(keyOf(document, "q3").idMap[item.id]),
      `${item.id} carries the text of a row the key maps elsewhere`,
    );
  }
  const candidateTexts = new Map(FILL_BLANK.candidates.map((row) => [row.id, row.text]));
  for (const candidate of halfOf("q4").candidates) {
    assert.strictEqual(
      candidate.text,
      candidateTexts.get(keyOf(document, "q4").idMap[candidate.id]),
      `${candidate.id} carries the text of a candidate the key maps elsewhere`,
    );
  }

  // The halves are not in the document. They are the other half of the write, not part of this one,
  // and storing them would put the published payload inside the server-only collection twice over.
  assert.ok(!("publicPayloads" in document), "the halves were written into the key document");
});

test("a question whose key was refused gets no public half either", () => {
  // The pairing is with the *stored* key, not with the split. A redacted half published for a key
  // that was never filed is a question nobody can score — `KEY_MISSING`, our gap, nothing paid — so
  // a refusal takes the half down with it and the caller publishes the payload it already had.
  const questions = [
    question("q1", "lesson-1", ORDERING),
    // A blank id Firestore will not take as a field name: the split succeeds, the key does not fit.
    question("q2", "lesson-1", {
      ...FILL_BLANK,
      blanks: [{id: "b.1", correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c2"}],
    }),
    // Nothing to split at all.
    question("q3", "lesson-1", {...SINGLE, correctOptionId: "zzz"}),
    // A second claim on an id already keyed.
    question("q1", "lesson-1", MULTIPLE),
  ];
  const {document, publicPayloads} = lessonKeyDocument("lesson-1", questions, opts());

  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["q1"]);
  assert.deepStrictEqual(publicPayloads.map((half) => half.questionId), ["q1"]);
  assert.deepStrictEqual(
    document.refusals.map((record) => record.reason),
    [REASON.UNSTORABLE_FIELD_NAME, REFUSAL.DANGLING_CORRECT_OPTION, REASON.DUPLICATE_QUESTION_ID],
  );
});

test("the halves come back from the flat call too, named by lesson", () => {
  const {documents, publicPayloads} = questionKeyDocuments([
    question("a1", "lesson-1", SINGLE),
    question("b1", "lesson-2", ORDERING),
    question("a2", "lesson-1", MULTIPLE),
    // A lesson id Firestore will not take: refused before any key is built, so no half either.
    question("c1", "les/son", SINGLE),
  ], opts());

  assert.deepStrictEqual(
    publicPayloads.map((half) => [half.lessonId, half.questionId]),
    [["lesson-1", "a1"], ["lesson-1", "a2"], ["lesson-2", "b1"]],
  );
  // Grouped by lesson exactly as `documents` is, and one half per key across every lesson.
  const keyed = Object.values(documents).flatMap((document) => document.keys.length);
  assert.strictEqual(publicPayloads.length, keyed.reduce((sum, count) => sum + count, 0));
});

test("the document is addressed by lesson, in the server-only collection", () => {
  assert.strictEqual(KEY_COLLECTION, "question_keys");
  assert.strictEqual(keyDocumentPath("lesson-1"), "question_keys/lesson-1");
  // Two segments: Firestore rejects an odd path, and a typo here fails the whole publish batch.
  assert.strictEqual(keyDocumentPath("lesson-1").split("/").length, 2);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Survey in the lesson | a survey among them | no key entry for it — it has no answer —
// and no refusal recorded either".

test("a Survey leaves no trace at all", () => {
  const document = keyDocument("lesson-1", [
    question("q1", "lesson-1", SINGLE),
    question("q-survey", "lesson-1", SURVEY),
  ], opts());

  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["q1"]);
  assert.deepStrictEqual(document.refusals, []);
  assert.strictEqual(keyOf(document, "q-survey"), null);
  assert.strictEqual(reasonOf(document, "q-survey"), null);
});

test("a lesson of nothing but surveys still writes its document", () => {
  // Empty lists are not nothing: on a republish they are what clears the previous generation.
  const document = keyDocument("lesson-1", [question("q-survey", "lesson-1", SURVEY)], opts());
  assert.deepStrictEqual(document.keys, []);
  assert.deepStrictEqual(document.refusals, []);
  assert.strictEqual(document.lessonId, "lesson-1");
});

// --------------------------------------------------------------------------------------------
// Matrix: "A question cannot be split | dangling correct id, malformed rows, legacy dialect | no
// key entry, and the question id and reason are recorded in the same document".

test("every kind of refusal is recorded by id and by reason", () => {
  const cases = [
    ["q-dangling", {...SINGLE, correctOptionId: "zzz"}, REFUSAL.DANGLING_CORRECT_OPTION],
    ["q-rows", {...SINGLE, options: [{id: "a"}, {id: "b", text: "val"}]}, REFUSAL.INVALID_OPTIONS],
    ["q-one-row", {...SINGLE, options: [{id: "b", text: "val"}]}, REFUSAL.INVALID_OPTIONS],
    ["q-legacy", {type: "single", text: "legacy dialect", answers: ["b"]}, REFUSAL.UNKNOWN_TYPE],
    ["q-empty-correct", {...MULTIPLE, correctOptionIds: []}, REFUSAL.EMPTY_CORRECT_OPTIONS],
    ["q-dup-items", {...ORDERING, items: [{id: "i1", text: "a"}, {id: "i1", text: "b"}]},
      REFUSAL.DUPLICATE_ITEM_IDS],
    ["q-dangling-blank", {...FILL_BLANK, blanks: [{id: "b1", correctCandidateId: "nope"}]},
      REFUSAL.DANGLING_CORRECT_CANDIDATE],
    ["q-bad-difficulty", {...SINGLE, difficulty: 3}, REFUSAL.INVALID_DIFFICULTY],
  ];
  const document = keyDocument(
    "lesson-1",
    cases.map(([id, payload]) => question(id, "lesson-1", payload)),
    opts(),
  );

  assert.deepStrictEqual(document.keys, [], "a refused question must not contribute a key");
  cases.forEach(([id, , reason], at) => {
    const record = refusalOf(document, id);
    assert.ok(record, `no refusal recorded for ${id}`);
    assert.strictEqual(record.reason, reason, `wrong reason for ${id}`);
    // Reason and detail are separate fields: a caller must never have to parse one out of the
    // other, and an author-supplied detail may itself contain any character a reason uses.
    assert.strictEqual(record.detail, null, `${id} should carry no detail`);
    assert.strictEqual(record.index, at, `${id} should point at its own position`);
  });
});

test("a payload that is not JSON at all is refused, not thrown", () => {
  const document = keyDocument("lesson-1", [
    question("q-malformed", "lesson-1", "{not json"),
    question("q-empty", "lesson-1", ""),
    question("q-array", "lesson-1", "[1, 2]"),
    question("q-ok", "lesson-1", SINGLE),
  ], opts());

  assert.strictEqual(reasonOf(document, "q-malformed"), REFUSAL.MALFORMED_JSON);
  assert.strictEqual(reasonOf(document, "q-empty"), REFUSAL.MALFORMED_JSON);
  assert.strictEqual(reasonOf(document, "q-array"), REFUSAL.NOT_AN_OBJECT);
  // One unreadable payload must not cost the questions beside it their keys.
  assert.ok(keyOf(document, "q-ok"), "a good question lost its key to a bad neighbour");
});

test("a question with no id is refused rather than filed under nothing", () => {
  const document = keyDocument("lesson-1", [question("", "lesson-1", SINGLE)], opts());
  assert.deepStrictEqual(document.keys, []);
  assert.strictEqual(reasonOf(document, ""), REASON.MISSING_QUESTION_ID);
});

test("a refusal with no question id is still findable, by position", () => {
  // Ids come from a client-submitted draft through stringValue, so several refusals can share the
  // empty string. Without the position they are one indistinguishable heap and nobody can go and
  // look at the question that caused any of them.
  const document = keyDocument("lesson-1", [
    question("q-ok", "lesson-1", SINGLE),
    null,
    undefined,
    7,
    question("", "lesson-1", SINGLE),
  ], opts());

  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["q-ok"]);
  assert.deepStrictEqual(document.refusals.map((record) => record.index), [1, 2, 3, 4]);
  for (const record of document.refusals) {
    assert.strictEqual(record.questionId, "");
    assert.strictEqual(typeof record.reason, "string");
    assert.notStrictEqual(record.reason, "");
  }
  assert.strictEqual(document.refusals[3].reason, REASON.MISSING_QUESTION_ID);
});

test("positions point at the submission, not at the lesson's slice of it", () => {
  // questionKeyDocuments splits one flat list across lessons, so a position counted inside a
  // lesson names a different question than the one that failed.
  const {documents} = questionKeyDocuments([
    question("q0", "lesson-a", SINGLE),
    question("q1", "lesson-b", SINGLE),
    question("q2", "lesson-b", {...SINGLE, correctOptionId: "zzz"}),
  ], opts());
  const refusals = documents["question_keys/lesson-b"].refusals;
  assert.deepStrictEqual(refusals.map((record) => [record.index, record.questionId]), [[2, "q2"]]);
});

test("every refusal stored in a document also comes back for the caller to log", () => {
  // The document is written to a collection the rules deny to every client, and there is no admin
  // surface over it. If a refusal only ever reaches the document, "a refusal is never silent" is
  // not true — nobody is listening. These returned records are what index.js hands to the logger.
  const {documents, refusals} = questionKeyDocuments([
    question("q-ok", "lesson-a", SINGLE),
    question("q-bad", "lesson-a", {...SINGLE, correctOptionId: "zzz"}),
    question("q-legacy", "lesson-b", {type: "single", text: "legacy", answers: ["b"]}),
  ], opts());

  const stored = [];
  for (const [path, document] of Object.entries(documents)) {
    for (const record of document.refusals) stored.push([path, record]);
  }
  assert.strictEqual(stored.length, 2, "the fixture must actually produce refusals");
  assert.strictEqual(refusals.length, stored.length, "a stored refusal that is not reported is silent");

  for (const [path, record] of stored) {
    const reported = refusals.find((item) => item.questionId === record.questionId);
    assert.ok(reported, `${record.questionId} was stored but never reported`);
    assert.strictEqual(reported.reason, record.reason);
    assert.strictEqual(reported.index, record.index);
    assert.strictEqual(reported.detail, record.detail);
    // And it names the document to go and look at, which the stored record cannot.
    assert.strictEqual(keyDocumentPath(reported.lessonId), path);
  }
  assert.deepStrictEqual(
    refusals.map((record) => [record.lessonId, record.questionId, record.reason]),
    [["lesson-a", "q-bad", REFUSAL.DANGLING_CORRECT_OPTION],
      ["lesson-b", "q-legacy", REFUSAL.UNKNOWN_TYPE]],
  );
});

test("an already-redacted payload is recorded, because this document is not where its answer is", () => {
  const document = keyDocument("lesson-1", [
    question("q-done", "lesson-1", {
      type: "SingleChoiceRedacted",
      text: "already split",
      options: [{id: "a", text: "var"}, {id: "b", text: "val"}],
    }),
  ], opts());
  assert.deepStrictEqual(document.keys, []);
  assert.strictEqual(reasonOf(document, "q-done"), REASON.ALREADY_REDACTED);
});

test("a second question claiming an id already taken is refused, not silently first-wins", () => {
  // Ids come from a client-submitted draft, so nothing upstream makes them unique. Keeping both
  // would leave which answer a lookup finds decided by array order.
  const document = keyDocument("lesson-1", [
    question("q1", "lesson-1", SINGLE),
    question("q1", "lesson-1", MULTIPLE),
    question("q2", "lesson-1", SINGLE),
  ], opts());

  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["q1", "q2"]);
  assert.strictEqual(keyOf(document, "q1").type, CONTENT_TYPE.SINGLE_CHOICE, "the first must win");
  const record = refusalOf(document, "q1");
  assert.strictEqual(record.reason, REASON.DUPLICATE_QUESTION_ID);
  assert.strictEqual(record.index, 1, "the refusal must name the duplicate, not the original");
});

test("a duplicate id is not shared across lessons", () => {
  // Keys are looked up inside one lesson's document, so the same id in two lessons is not a clash.
  const {documents} = questionKeyDocuments([
    question("q1", "lesson-a", SINGLE),
    question("q1", "lesson-b", SINGLE),
  ], opts());
  assert.strictEqual(documents["question_keys/lesson-a"].keys.length, 1);
  assert.strictEqual(documents["question_keys/lesson-b"].keys.length, 1);
  assert.deepStrictEqual(documents["question_keys/lesson-b"].refusals, []);
});

// --------------------------------------------------------------------------------------------
// Task: "Rejects a key whose field names Firestore cannot store, rather than emitting a document
// that fails to write." Plus the document id, which reaches db.doc() and can fail the whole batch.

test("field names Firestore rejects are recognised as such", () => {
  for (const name of ["b1", "c-2", "ri-0", "rc-10", "blank 1", "__proto", "proto__", "__"]) {
    assert.ok(isStorableFieldName(name), `${name} should be storable`);
  }
  for (const name of ["", "a.b", "a/b", "__proto__", "__id__", "__x__"]) {
    assert.ok(!isStorableFieldName(name), `${name} should be rejected`);
  }
  for (const value of [null, undefined, 7, {}]) {
    assert.ok(!isStorableFieldName(value), `${String(value)} should be rejected`);
  }
});

test("document ids Firestore rejects are recognised, and they are not the same set", () => {
  for (const id of ["les-1", "a.b", "___", "..a", "a..", "x".repeat(MAX_DOCUMENT_ID_BYTES)]) {
    assert.ok(isStorableDocumentId(id), `${id.slice(0, 20)} should be storable`);
  }
  for (const id of ["", ".", "..", "a/b", "__proto__", "__id__", "x".repeat(MAX_DOCUMENT_ID_BYTES + 1)]) {
    assert.ok(!isStorableDocumentId(id), `${id.slice(0, 20)} should be rejected`);
  }
  // Bytes, not characters: a multi-byte id is longer than it looks to Firestore.
  assert.ok(!isStorableDocumentId("я".repeat(MAX_DOCUMENT_ID_BYTES)));
  for (const value of [null, undefined, 7, {}]) {
    assert.ok(!isStorableDocumentId(value), `${String(value)} should be rejected`);
  }
  // A `.` is legal in a document id and illegal in a field name. Pinning the difference, because
  // sharing one predicate between the two would silently loosen or tighten one of them.
  assert.ok(isStorableDocumentId("a.b") && !isStorableFieldName("a.b"));
});

test("a lesson id Firestore cannot take costs that lesson its keys, not the whole publish", () => {
  // db.doc(`question_keys/${lessonId}`) throws on any of these, and a throw while the batch is
  // being assembled fails every question in the submission.
  for (const lessonId of ["..", ".", "a/b", "__x__", "y".repeat(MAX_DOCUMENT_ID_BYTES + 1)]) {
    const {documents, refusals} = questionKeyDocuments([
      question("q-bad", lessonId, SINGLE),
      question("q-ok", "lesson-ok", SINGLE),
    ], opts());

    assert.deepStrictEqual(Object.keys(documents), ["question_keys/lesson-ok"]);
    const record = refusals.find((item) => item.questionId === "q-bad");
    assert.ok(record, `no refusal for lesson id ${lessonId.slice(0, 12)}`);
    assert.strictEqual(record.reason, REASON.UNUSABLE_LESSON_ID);
    assert.strictEqual(record.index, 0);
    assert.ok(typeof record.detail === "string" && record.detail.length > 0);
  }
});

test("a blank id Firestore cannot store costs that question its key, not the whole write", () => {
  // Blank ids are the author's, so they arrive in whatever shape the authoring tool allowed; a
  // FillBlank key puts them in field-name position, which is where Firestore has opinions.
  for (const blankId of ["b.1", "b/1", "__proto__"]) {
    const payload = {
      ...FILL_BLANK,
      blanks: [{id: blankId, correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c2"}],
    };
    const document = keyDocument("lesson-1", [
      question("q-bad-blank", "lesson-1", payload),
      question("q-ok", "lesson-1", SINGLE),
    ], opts());

    assert.deepStrictEqual(
      document.keys.map((entry) => entry.questionId),
      ["q-ok"],
      `key for blank id ${blankId} was emitted anyway`,
    );
    const record = refusalOf(document, "q-bad-blank");
    assert.strictEqual(record.reason, REASON.UNSTORABLE_FIELD_NAME);
    // The offending name is its own field, not spliced into the reason: it is author-supplied and
    // can contain any separator a caller might try to split on.
    assert.strictEqual(record.detail, blankId, `detail does not name ${blankId}`);
  }
});

test("an author-supplied detail is cut before it is stored or logged", () => {
  const blankId = `b.${"x".repeat(5000)}`;
  const document = keyDocument("lesson-1", [
    question("q-long", "lesson-1", {
      ...FILL_BLANK,
      blanks: [{id: blankId, correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c2"}],
    }),
  ], opts());
  const record = refusalOf(document, "q-long");
  assert.strictEqual(record.reason, REASON.UNSTORABLE_FIELD_NAME);
  assert.ok(record.detail.length < 300, `detail was stored whole (${record.detail.length} chars)`);
});

test("no key that is emitted carries a field name Firestore would throw out", () => {
  // The guard has to hold over whole documents, not just the maps somebody remembered to check.
  const document = keyDocument("lesson-1", [
    question("q1", "lesson-1", SINGLE),
    question("q2", "lesson-1", MULTIPLE),
    question("q3", "lesson-1", ORDERING),
    question("q4", "lesson-1", FILL_BLANK),
  ], opts());

  const walk = (value) => {
    if (Array.isArray(value)) return value.forEach(walk);
    if (!value || typeof value !== "object") return;
    for (const name of Object.keys(value)) {
      assert.ok(isStorableFieldName(name), `emitted an unstorable field name: ${name}`);
      walk(value[name]);
    }
  };
  walk(document);
});

// --------------------------------------------------------------------------------------------
// Decision 1's other half: one document per lesson trades the 500-write cap for the 1 MiB
// document cap, and the key list is what grows.

/** A question whose key alone is about 100 KB: twenty correct option ids of five thousand chars. */
function heavyMultipleChoice(tag) {
  const options = [];
  for (let index = 0; index < 20; index += 1) {
    options.push({id: `${tag}-${index}-${"x".repeat(5000)}`, text: "opt"});
  }
  return {
    type: "MultipleChoice",
    text: "heavy",
    imageUrl: null,
    options,
    correctOptionIds: options.map((option) => option.id),
  };
}

test("a lesson too large for one document refuses by name instead of failing the batch", () => {
  const questions = [];
  for (let index = 0; index < 14; index += 1) {
    questions.push(question(`q${index}`, "lesson-1", heavyMultipleChoice(`t${index}`)));
  }
  const document = keyDocument("lesson-1", questions, opts());

  assert.ok(document.keys.length > 0, "the ceiling must not refuse everything");
  assert.ok(document.keys.length < questions.length, "the ceiling must actually bite");
  // Everything past the ceiling is refused by name, so no question disappears without a record.
  assert.strictEqual(document.keys.length + document.refusals.length, questions.length);
  for (const record of document.refusals) {
    assert.strictEqual(record.reason, REASON.DOCUMENT_FULL);
  }
  // And the document that comes out actually fits Firestore, which is the point of the exercise.
  // Asserted against the real 1 MiB limit rather than the budget: the budget counts entry payloads
  // and not the array and field-name overhead around them, so it is the headroom between the two
  // that has to hold, and only this assertion notices if the budget is raised past it.
  assert.ok(
    Buffer.byteLength(JSON.stringify(document), "utf8") < FIRESTORE_DOCUMENT_LIMIT,
    "the document would be rejected by Firestore",
  );
  assert.ok(MAX_DOCUMENT_BYTES < FIRESTORE_DOCUMENT_LIMIT, "the budget must leave headroom");
});

test("refusals are charged to the same budget, and what will not fit is counted", () => {
  // Otherwise a lesson escapes the document limit by refusing its way past it — the failure mode
  // the ceiling exists to prevent, arriving through the mechanism meant to prevent it.
  const questions = [];
  for (let index = 0; index < 8; index += 1) {
    questions.push(question(`q${index}`, "lesson-1", heavyMultipleChoice(`t${index}`)));
  }
  for (let index = 0; index < 4000; index += 1) {
    questions.push(question(`bad${index}`, "lesson-1", "{not json"));
  }
  const document = keyDocument("lesson-1", questions, opts());

  assert.ok(document.omitted > 0, "records that did not fit must be counted");
  assert.ok(document.refusals.length < 4000, "refusals cannot all have fit");
  assert.strictEqual(
    document.keys.length + document.refusals.length + document.omitted,
    questions.length,
    "every question must be either keyed, refused, or counted as omitted",
  );

  // And the caller hears about the omission, since the document alone is read by nobody.
  const {refusals} = questionKeyDocuments(questions, opts());
  const summary = refusals.find((record) => record.index === -1);
  assert.ok(summary, "an omission must be reported to the caller");
  assert.strictEqual(summary.reason, REASON.DOCUMENT_FULL);
  assert.ok(
    Buffer.byteLength(JSON.stringify(document), "utf8") < FIRESTORE_DOCUMENT_LIMIT,
    "a document full of refusals must still fit",
  );
});

// --------------------------------------------------------------------------------------------
// Matrix: "Republish | a lesson published twice | keys are replaced, not merged with the previous
// generation".

test("keys and refusals are lists, which is what makes a republish a replacement", () => {
  // The publish batch writes every public document with {merge: true}, and merge recurses into
  // maps: keyed by question id, a second generation would union with the first and leave the
  // answer to a deleted question sitting beside the answer to the one that replaced it. Firestore
  // replaces an array whole, so the shape is the mechanism — assert it, or a later refactor to a
  // map passes every other case in this file.
  const first = keyDocument("lesson-1", [
    question("q1", "lesson-1", SINGLE),
    question("q-gone", "lesson-1", MULTIPLE),
  ], opts());
  const second = keyDocument("lesson-1", [question("q1", "lesson-1", MULTIPLE)], opts());

  assert.ok(Array.isArray(first.keys) && Array.isArray(first.refusals));
  assert.ok(Array.isArray(second.keys) && Array.isArray(second.refusals));
  assert.deepStrictEqual(second.keys.map((entry) => entry.questionId), ["q1"]);
  // Same path both times, so the second write lands on the first.
  assert.strictEqual(keyDocumentPath(first.lessonId), keyDocumentPath(second.lessonId));
  // And the surviving question's answer is the new one, not the one it had before.
  assert.strictEqual(keyOf(second, "q1").type, CONTENT_TYPE.MULTIPLE_CHOICE);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Batch cost | a submission of ~120 questions | publishes exactly as it does today — the
// write count per question must not rise".

test("a submission adds one document per lesson, whatever the question count", () => {
  const questions = [];
  for (let index = 0; index < 120; index += 1) {
    const lessonId = `lesson-${index % 4}`;
    questions.push(question(`q${index}`, lessonId, index % 5 === 0 ? SURVEY : SINGLE));
  }
  const {documents, refusals} = questionKeyDocuments(questions, opts());

  assert.strictEqual(Object.keys(documents).length, 4, "one key document per lesson, not per question");
  assert.deepStrictEqual(Object.keys(documents).sort(), [
    "question_keys/lesson-0",
    "question_keys/lesson-1",
    "question_keys/lesson-2",
    "question_keys/lesson-3",
  ]);
  assert.deepStrictEqual(refusals, []);
  // Every question that has an answer still has its key; the surveys are simply absent.
  const total = Object.values(documents).reduce((sum, document) => sum + document.keys.length, 0);
  assert.strictEqual(total, questions.filter((item) => item.type !== "Survey").length);
  for (const document of Object.values(documents)) {
    for (const entry of document.keys) {
      assert.strictEqual(entry.key.questionId, entry.questionId);
    }
  }
});

test("questions are filed under their own lesson, never pooled", () => {
  const {documents} = questionKeyDocuments([
    question("q1", "lesson-a", SINGLE),
    question("q2", "lesson-b", MULTIPLE),
    question("q3", "lesson-a", ORDERING),
  ], opts());

  assert.deepStrictEqual(
    documents["question_keys/lesson-a"].keys.map((entry) => entry.questionId),
    ["q1", "q3"],
  );
  assert.deepStrictEqual(
    documents["question_keys/lesson-b"].keys.map((entry) => entry.questionId),
    ["q2"],
  );
});

test("input that is not a list is nothing to publish, not a crash", () => {
  // publicDocuments hands over whatever normalizeReviewRequest produced, and listMaps has been
  // wrong before. Throwing here fails the entire publish batch, which is the one outcome this
  // module promises never to cause.
  for (const value of [null, undefined, {}, 7, "questions", true]) {
    assert.deepStrictEqual(
      questionKeyDocuments(value, opts()),
      {documents: {}, publicPayloads: [], refusals: []},
      `questionKeyDocuments(${String(value)}) should be empty, not thrown`,
    );
    const document = keyDocument("lesson-1", value, opts());
    assert.deepStrictEqual(document.keys, []);
    assert.deepStrictEqual(document.refusals, []);
  }
});

test("a question with no lesson id is refused, not filed under nothing", () => {
  const {documents, refusals} = questionKeyDocuments([
    question("q1", "", SINGLE),
    question("q2", "lesson-a", SINGLE),
  ], opts());
  assert.deepStrictEqual(Object.keys(documents), ["question_keys/lesson-a"]);
  assert.deepStrictEqual(refusals.map((record) => [record.questionId, record.reason]),
    [["q1", REASON.UNUSABLE_LESSON_ID]]);
});

test("nothing to publish is no document, not an empty one", () => {
  assert.deepStrictEqual(questionKeyDocuments([], opts()), {documents: {}, publicPayloads: [], refusals: []});
});

// --------------------------------------------------------------------------------------------
// The shape publication actually sends.

test("a question shaped exactly as normalizeQuestion leaves it still gets a key", () => {
  // Every other case here names a difficulty and injects a seeded shuffle. Publication does
  // neither: normalizeQuestion runs difficulty through stringValue, so it arrives as "" for any
  // payload whose document has no such field, and publicDocuments passes no options at all, so
  // the shuffle comes from crypto.randomInt. That is the branch a real published question takes,
  // and until this case it was the one branch never exercised.
  const normalized = {
    id: "qst-1",
    draftId: "draft-1",
    lessonId: "les-1",
    type: "SingleChoice",
    language: "ru",
    languageLevel: 0,
    difficulty: "",
    order: 0,
    text: "Which keyword declares a read-only binding?",
    imagePath: null,
    payload: json(SINGLE),
    updatedAtMs: 0,
  };
  const {documents, refusals} = questionKeyDocuments([normalized]);

  assert.deepStrictEqual(refusals, []);
  const document = documents["question_keys/les-1"];
  assert.ok(document, "no document for a question publication would actually send");
  assert.deepStrictEqual(document.keys.map((entry) => entry.questionId), ["qst-1"]);
  assert.strictEqual(keyOf(document, "qst-1").correctOptionId, "b");

  // The unseeded shuffle path too, since Ordering and FillBlank are the types that use it.
  const ordering = questionKeyDocuments([
    {...normalized, id: "qst-2", type: "Ordering", payload: json(ORDERING)},
  ]).documents["question_keys/les-1"];
  const key = keyOf(ordering, "qst-2");
  assert.deepStrictEqual(key.order, ["i1", "i2", "i3", "i4"]);
  assert.deepStrictEqual(Object.values(key.idMap).slice().sort(), ["i1", "i2", "i3", "i4"]);

  // And the per-lesson entry point with no options object at all, which is what a caller reaching
  // past questionKeyDocuments would do. Reading `options.random` off undefined throws here, and a
  // throw during batch assembly fails the whole publish.
  const bare = keyDocument("les-1", [normalized]);
  assert.deepStrictEqual(bare.refusals, []);
  assert.strictEqual(keyOf(bare, "qst-1").correctOptionId, "b");
  assert.strictEqual(keyDocument("les-1", [normalized], undefined).keys.length, 1);
  assert.strictEqual(keyDocument("les-1", [normalized], {}).keys.length, 1);
});

test("the document's difficulty decides nothing about whether a question gets a key", () => {
  // resolveDifficulty only shapes the public half, which this slice discards, so the fallback
  // cannot make or break a key. What the payload's own difficulty can do is refuse the question
  // outright when it is present and not a string — that branch is real and is what is asserted.
  const withDifficulty = {...SINGLE, difficulty: "HARD"};
  const withoutDifficulty = {...SINGLE};
  assert.ok(!("difficulty" in withoutDifficulty), "the fixture must genuinely lack the field");
  assert.ok("difficulty" in withDifficulty, "the fixture must genuinely carry the field");

  for (const fallback of ["", "HARD", undefined, null, 3]) {
    const document = keyDocument("lesson-1", [
      question("q-has", "lesson-1", withDifficulty, fallback),
      question("q-lacks", "lesson-1", withoutDifficulty, fallback),
      question("q-bad", "lesson-1", {...SINGLE, difficulty: 3}, fallback),
    ], opts());

    assert.ok(keyOf(document, "q-has"), `a payload with a difficulty lost its key (fallback ${fallback})`);
    assert.ok(keyOf(document, "q-lacks"), `a payload without one lost its key (fallback ${fallback})`);
    assert.strictEqual(reasonOf(document, "q-bad"), REFUSAL.INVALID_DIFFICULTY);
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
  console.error(`question-key-store.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`question-key-store.test.js OK (${SUITE.length} cases)`);
}
