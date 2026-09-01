"use strict";

const assert = require("assert");
const {DIFFICULTY, WITHHELD, planCatalogRedaction} = require("./catalog-redaction-plan");
const {REASON, keyDocumentPath, questionKeyDocuments} = require("./question-key-store");
const {REFUSAL, REDACTED_TYPE, CONTENT_TYPE} = require("./question-redaction");
const {SINGLE, MULTIPLE, ORDERING, FILL_BLANK, SURVEY, LEGACY_SINGLE_CHOICE} = require("./_question-fixtures");
const {seeded} = require("./_seeded-random");

/**
 * The Matrix from `spec-e2-9-catalog-redaction-plan.md`, minus the two rows that need Firestore.
 *
 * "no writes at all" and "the second run replaces each lesson's document" are properties of the
 * script, which this module never sees. What is provable here is everything the plan promises:
 * that the documents are the key store's own, over exactly the non-archived questions; that every
 * refusal is named by both the id it was refused under and the document it lives in; that a
 * Survey, an archived question, a translated variant and a legacy payload each land in the count
 * the report needs; that a full document does not lose its count or its guard; and that a lesson
 * is whole however it was paged.
 *
 * The payloads are the key store test's (`_question-fixtures.js`), so the two suites cannot
 * disagree about what a splittable question is.
 */
const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/** A fresh generator per test; see the same helper in question-key-store.test.js for why. */
const opts = (seed) => ({random: seeded(seed === undefined ? 7 : seed)});

const json = (value) => JSON.stringify(value);

/**
 * One document from `questions/{id}`. `verifyQuestionDoc` in `verify-seeded-quest.js` pins nine
 * fields for a seeded question; `publicDocuments` in `index.js` writes those plus `languageLevel`.
 * `difficulty` is in neither list — it lives inside the payload — so a row that carries it as a
 * document field is a fixture for the coercion path, not a production shape. `fields` overrides
 * any of them.
 */
function row(id, lessonId, payload, fields) {
  return {
    id,
    data: {
      id,
      lessonId,
      text: payload && payload.text ? payload.text : "",
      payload: typeof payload === "string" ? payload : json(payload),
      language: "ru",
      order: 0,
      version: 1,
      lastModifiedAt: 0,
      archived: false,
      ...fields,
    },
  };
}

/** `stringValue` as `normalizeQuestion` applies it in `index.js`. */
function stringValue(value, fallback) {
  if (value === null || value === undefined) return fallback;
  const text = String(value);
  return text.length > 0 ? text : fallback;
}

/** The same rows as `normalizeQuestion` hands them to publication, for the comparison. */
function asPublished(rows) {
  return rows
    .filter((item) => item.data.archived !== true)
    .map((item) => ({
      id: stringValue(item.data.id, item.id),
      lessonId: stringValue(item.data.lessonId, ""),
      difficulty: stringValue(item.data.difficulty, ""),
      payload: item.data.payload,
    }));
}

/** A question whose key alone is about 100 KB — the key store suite's own overflow fixture. */
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

const REDACTED_SINGLE = {type: REDACTED_TYPE[CONTENT_TYPE.SINGLE_CHOICE], text: "?", imageUrl: null, options: SINGLE.options};

const lessonOf = (plan, lessonId) => plan.summary.lessons.find((lesson) => lesson.lessonId === lessonId);
const keyIds = (document) => document.keys.map((entry) => entry.questionId);
const refusalOf = (plan, questionId) => plan.refusals.find((record) => record.questionId === questionId) || null;
const reasonOf = (plan, questionId) => {
  const record = refusalOf(plan, questionId);
  return record ? record.reason : null;
};
const withoutDocumentId = (records) => records.map(({documentId, ...record}) => record);
/** Surveys counted from the fixtures themselves, so the not-applicable assertion is not the remainder restated. */
const surveyCount = (rows) => rows.filter((item) => item.data.archived !== true && item.data.payload === json(SURVEY)).length;

/** A catalog with every outcome in it, spread over three lessons that are not adjacent. */
function mixedCatalog() {
  return [
    row("q1", "lesson-a", SINGLE),
    row("q2", "lesson-b", ORDERING),
    row("q3", "lesson-a", SURVEY),
    row("q4", "lesson-c", {...SINGLE, correctOptionId: "zzz"}),
    row("q5", "lesson-b", FILL_BLANK),
    row("q6", "lesson-a", MULTIPLE, {archived: true}),
    row("q7", "lesson-c", LEGACY_SINGLE_CHOICE),
    row("q8", "lesson-a", ORDERING),
    row("q1__ru", "lesson-a", SINGLE),
    row("q9", "lesson-c", MULTIPLE),
    // A document-level difficulty, which public documents do not carry: it reaches the key store
    // the way publication would send it, and the tally never reads it.
    row("q10", "lesson-b", SINGLE, {difficulty: "HARD"}),
    row("q11", "lesson-b", {...SINGLE, difficulty: "EASY"}, {difficulty: 3}),
  ];
}

// --------------------------------------------------------------------------------------------
// Acceptance: "each lesson's key document equals what questionKeyDocuments produces for exactly
// that lesson's non-archived questions".

test("the documents are the key store's own, over the non-archived questions in catalog order", () => {
  const rows = mixedCatalog();
  const plan = planCatalogRedaction(rows, opts());
  const expected = questionKeyDocuments(asPublished(rows), opts());

  assert.deepStrictEqual(plan.documents, expected.documents);
  assert.deepStrictEqual(withoutDocumentId(plan.refusals), expected.refusals);
  assert.deepStrictEqual(Object.keys(plan.documents).sort(), [
    keyDocumentPath("lesson-a"),
    keyDocumentPath("lesson-b"),
    keyDocumentPath("lesson-c"),
  ]);
  assert.ok(keyIds(plan.documents[keyDocumentPath("lesson-b")]).includes("q10"), "the difficulty-carrying row is keyed");
});

test("each lesson's document is what publication would write for that lesson alone", () => {
  // Choice types only: they draw no shuffle, so a lesson planned by itself and a lesson planned
  // among others must produce byte-identical documents, whatever else is in the catalog.
  const lessons = {
    "lesson-1": [row("a1", "lesson-1", SINGLE), row("a2", "lesson-1", MULTIPLE), row("a3", "lesson-1", SURVEY)],
    "lesson-2": [row("b1", "lesson-2", MULTIPLE), row("b2", "lesson-2", {...SINGLE, correctOptionId: "nope"})],
    "lesson-3": [row("c1", "lesson-3", SINGLE), row("c2", "lesson-3", SINGLE, {archived: true})],
  };
  // Interleaved on purpose, so grouping is being tested and not just the identity.
  const catalog = [lessons["lesson-1"][0], lessons["lesson-2"][0], lessons["lesson-3"][0],
    lessons["lesson-1"][1], lessons["lesson-2"][1], lessons["lesson-3"][1], lessons["lesson-1"][2]];
  const plan = planCatalogRedaction(catalog);

  // A refusal's `index` is its position in whatever list the key store was handed — the
  // submission for publication, the catalog here — so it is the one field that legitimately
  // differs between a lesson planned alone and the same lesson planned among others. The
  // whole-catalog case above compares it exactly; this one compares everything else.
  const positionless = (document) => ({
    ...document,
    refusals: document.refusals.map(({index, ...record}) => record),
  });
  for (const [lessonId, rows] of Object.entries(lessons)) {
    const path = keyDocumentPath(lessonId);
    const alone = questionKeyDocuments(asPublished(rows)).documents[path];
    assert.deepStrictEqual(positionless(plan.documents[path]), positionless(alone), `${lessonId} differs from its own publication`);
    assert.strictEqual(lessonOf(plan, lessonId).document, path);
  }
});

// --------------------------------------------------------------------------------------------
// Acceptance: "the report names every refusal with a reason and an id".
// Matrix: "Unsplittable question | dangling correct id, malformed rows, unknown or legacy dialect
// | counted and listed by reason with its id; the lesson's other questions still get keys".

test("every unsplittable question is listed by id and reason, and its neighbours are still keyed", () => {
  const plan = planCatalogRedaction([
    row("q-ok", "lesson-1", SINGLE),
    row("q-dangling", "lesson-1", {...SINGLE, correctOptionId: "zzz"}),
    row("q-rows", "lesson-1", {...SINGLE, options: "not rows"}),
    row("q-unknown", "lesson-1", {type: "Telepathy", text: "?", options: []}),
    row("q-legacy", "lesson-1", LEGACY_SINGLE_CHOICE),
    row("q-broken", "lesson-1", "{not json"),
    row("q-ok2", "lesson-1", ORDERING),
  ], opts());

  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["q-ok", "q-ok2"]);

  assert.strictEqual(reasonOf(plan, "q-dangling"), REFUSAL.DANGLING_CORRECT_OPTION);
  assert.strictEqual(reasonOf(plan, "q-rows"), REFUSAL.INVALID_OPTIONS);
  assert.strictEqual(reasonOf(plan, "q-unknown"), REFUSAL.UNKNOWN_TYPE);
  assert.strictEqual(reasonOf(plan, "q-legacy"), REFUSAL.UNKNOWN_TYPE);
  assert.strictEqual(reasonOf(plan, "q-broken"), REFUSAL.MALFORMED_JSON);
  assert.strictEqual(plan.refusals.length, 5);
  for (const record of plan.refusals) {
    assert.strictEqual(record.lessonId, "lesson-1");
    assert.ok(record.questionId !== "", "a refusal without an id names nothing");
    assert.strictEqual(record.documentId, record.questionId, "the document is where the operator will look");
    assert.ok(typeof record.reason === "string" && record.reason !== "", `no reason for ${record.questionId}`);
  }

  const lesson = lessonOf(plan, "lesson-1");
  assert.strictEqual(lesson.keyed, 2);
  assert.strictEqual(lesson.refused, 5);
  assert.strictEqual(lesson.notApplicable, 0);
  assert.deepStrictEqual(lesson.refusedByReason, {
    [REFUSAL.DANGLING_CORRECT_OPTION]: 1,
    [REFUSAL.INVALID_OPTIONS]: 1,
    [REFUSAL.UNKNOWN_TYPE]: 2,
    [REFUSAL.MALFORMED_JSON]: 1,
  });
  assert.deepStrictEqual(plan.summary.overall.refusedByReason, lesson.refusedByReason);
});

test("a refusal names the document it lives in, which is not always the id it was refused under", () => {
  const plan = planCatalogRedaction([
    // The `id` field wins, as it does in publication — but the operator opens `questions/doc-x`.
    {id: "doc-x", data: {id: "named-x", lessonId: "lesson-1", payload: "{not json", archived: false}},
    // Two documents claiming one id: the second is refused under the shared id, and only its
    // document id says which of the two it was.
    {id: "doc-1", data: {id: "same", lessonId: "lesson-1", payload: json(SINGLE), archived: false}},
    {id: "doc-2", data: {id: "same", lessonId: "lesson-1", payload: json(MULTIPLE), archived: false}},
    // No lesson id: refused before the payload is read, still by document.
    {id: "doc-orphan", data: {payload: json(SINGLE), archived: false}},
  ], opts());

  assert.deepStrictEqual(
    plan.refusals.map(({documentId, questionId, reason}) => ({documentId, questionId, reason})).sort((a, b) => (a.documentId < b.documentId ? -1 : 1)),
    [
      {documentId: "doc-2", questionId: "same", reason: REASON.DUPLICATE_QUESTION_ID},
      {documentId: "doc-orphan", questionId: "doc-orphan", reason: REASON.UNUSABLE_LESSON_ID},
      {documentId: "doc-x", questionId: "named-x", reason: REFUSAL.MALFORMED_JSON},
    ],
  );
  assert.deepStrictEqual(keyIds(plan.documents[keyDocumentPath("lesson-1")]), ["same"]);
});

test("the legacy dialect is counted only where it was refused as unknown-type, and never exceeds it", () => {
  // The `question` literal in `scripts/seed-hierarchy.js` writes this shape and the redactor
  // refuses it as `unknown-type` by design. A report that folded it into that reason would hide
  // the one refusal class with a known fix, so it has its own count — but only where that is the
  // reason: a legacy payload under an unusable lesson id was refused for the lesson id, and
  // counting it would put "of which legacy" above the unknown-type line it sits under.
  const plan = planCatalogRedaction([
    row("q-legacy-1", "lesson-1", LEGACY_SINGLE_CHOICE),
    row("q-legacy-2", "lesson-2", LEGACY_SINGLE_CHOICE),
    row("q-legacy-orphan", "", LEGACY_SINGLE_CHOICE),
    row("q-legacy-gone", "lesson-1", LEGACY_SINGLE_CHOICE, {archived: true}),
    row("q-alien", "lesson-1", {type: "Telepathy", text: "?"}),
    row("q-fine", "lesson-1", SINGLE),
  ], opts());

  const {overall} = plan.summary;
  assert.strictEqual(overall.legacyDialect, 2);
  assert.strictEqual(overall.refusedByReason[REFUSAL.UNKNOWN_TYPE], 3);
  assert.ok(overall.legacyDialect <= overall.refusedByReason[REFUSAL.UNKNOWN_TYPE]);
  assert.strictEqual(lessonOf(plan, "lesson-1").legacyDialect, 1);
  assert.strictEqual(lessonOf(plan, "lesson-2").legacyDialect, 1);
  assert.strictEqual(lessonOf(plan, "").legacyDialect, 0);
  assert.strictEqual(reasonOf(plan, "q-legacy-orphan"), REASON.UNUSABLE_LESSON_ID);
  for (const lesson of plan.summary.lessons) {
    assert.ok(lesson.legacyDialect <= (lesson.refusedByReason[REFUSAL.UNKNOWN_TYPE] || 0), lesson.lessonId);
  }
});

// --------------------------------------------------------------------------------------------
// Matrix: "Archived question | archived: true | skipped, counted as skipped, never keyed".

test("an archived question is counted as skipped and reaches neither a key nor a refusal", () => {
  const plan = planCatalogRedaction([
    row("q-live", "lesson-1", SINGLE),
    row("q-gone", "lesson-1", SINGLE, {archived: true}),
    // Archived AND unsplittable: skipped means skipped, so not even the refusal is recorded.
    row("q-gone-broken", "lesson-1", "{not json", {archived: true}),
    // A lesson with nothing but archived questions gets no document at all.
    row("q-only", "lesson-2", ORDERING, {archived: true}),
    // Only the literal boolean archives; a string or a number is a document to look at, not skip.
    row("q-stringy", "lesson-1", MULTIPLE, {archived: "true"}),
  ], opts());

  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["q-live", "q-stringy"]);
  assert.deepStrictEqual(plan.refusals, []);
  assert.strictEqual(plan.documents[keyDocumentPath("lesson-2")], undefined);

  const lesson = lessonOf(plan, "lesson-1");
  assert.strictEqual(lesson.questions, 4);
  assert.strictEqual(lesson.archived, 2);
  assert.strictEqual(lesson.considered, 2);
  assert.strictEqual(lesson.keyed, 2);
  const empty = lessonOf(plan, "lesson-2");
  assert.strictEqual(empty.archived, 1);
  assert.strictEqual(empty.considered, 0);
  assert.strictEqual(empty.document, null);
  assert.strictEqual(plan.summary.overall.archived, 3);
  assert.strictEqual(plan.summary.overall.considered, 2);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Survey | a survey among the questions | counted as not applicable, no key, not a refusal".

test("a survey is counted as not applicable, with no key and no refusal", () => {
  const rows = [
    row("q-single", "lesson-1", SINGLE),
    row("q-survey", "lesson-1", SURVEY),
    row("q-survey-2", "lesson-1", SURVEY),
    row("q-survey-gone", "lesson-1", SURVEY, {archived: true}),
    row("q-dangling", "lesson-1", {...SINGLE, correctOptionId: "zzz"}),
  ];
  const plan = planCatalogRedaction(rows, opts());

  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["q-single"]);
  assert.strictEqual(refusalOf(plan, "q-survey"), null);
  assert.strictEqual(refusalOf(plan, "q-survey-2"), null);

  const lesson = lessonOf(plan, "lesson-1");
  assert.strictEqual(lesson.considered, 4);
  assert.strictEqual(lesson.keyed, 1);
  assert.strictEqual(lesson.refused, 1);
  assert.strictEqual(surveyCount(rows), 2, "the fixture must hold two live surveys");
  assert.strictEqual(lesson.notApplicable, surveyCount(rows));
  assert.strictEqual(plan.summary.overall.notApplicable, surveyCount(rows));
});

test("a lesson of surveys alone still yields a document, and the plan says it holds no key", () => {
  // Publication would write `keys: []` for such a lesson, and so does this — but a document that
  // clears nothing and keys nothing should not pass as one of the documents the operator is
  // waiting for.
  const plan = planCatalogRedaction([
    row("s1", "lesson-s", SURVEY),
    row("s2", "lesson-s", SURVEY),
    row("r1", "lesson-r", "{not json"),
    row("k1", "lesson-k", SINGLE),
  ], opts());

  for (const lessonId of ["lesson-s", "lesson-r"]) {
    const document = plan.documents[keyDocumentPath(lessonId)];
    assert.ok(document, `${lessonId} must still have a document to write`);
    assert.deepStrictEqual(document.keys, []);
    assert.strictEqual(lessonOf(plan, lessonId).emptyDocument, true, lessonId);
  }
  assert.strictEqual(lessonOf(plan, "lesson-k").emptyDocument, false);
  assert.strictEqual(plan.summary.overall.emptyDocuments, 2);
  assert.strictEqual(Object.keys(plan.documents).length, 3);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Translated variant | q1__ru beside q1 | each is its own question with its own key entry".

test("a translated variant is its own question with its own key, and is counted as a variant", () => {
  const plan = planCatalogRedaction([
    row("q1", "lesson-1", SINGLE),
    row("q1__ru", "lesson-1", {...SINGLE, text: "Какое ключевое слово?"}),
    row("q1__en", "lesson-1", SINGLE),
    row("q2", "lesson-1", MULTIPLE),
  ], opts());

  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["q1", "q1__ru", "q1__en", "q2"]);
  for (const id of ["q1", "q1__ru", "q1__en"]) {
    const entry = document.keys.find((item) => item.questionId === id);
    assert.strictEqual(entry.key.questionId, id, `the key for ${id} is stamped with another id`);
    assert.strictEqual(entry.key.correctOptionId, "b");
  }
  assert.deepStrictEqual(plan.refusals, []);
  assert.strictEqual(lessonOf(plan, "lesson-1").translatedVariants, 2);
  assert.strictEqual(plan.summary.overall.translatedVariants, 2);
  assert.strictEqual(plan.summary.overall.variantsWithoutBase, 0);
  assert.strictEqual(lessonOf(plan, "lesson-1").keyed, 4);
});

test("an id shaped like a variant is a variant only when its base is in the same lesson", () => {
  // `canonicalQuestionId` is a rule about id shape; `intro__part` satisfies it with no
  // translation anywhere. A count presented as "translated variants" has to mean that, so the
  // base has to exist — in the lesson, archived or not, because a base is a document.
  const plan = planCatalogRedaction([
    row("q1", "lesson-1", SINGLE, {archived: true}),
    row("q1__ru", "lesson-1", SINGLE),
    row("intro__part", "lesson-1", SINGLE),
    row("q2__ru", "lesson-1", SINGLE),
    row("q2", "lesson-2", SINGLE),
    row("q3__uk", "lesson-2", SINGLE),
  ], opts());

  const first = lessonOf(plan, "lesson-1");
  assert.strictEqual(first.translatedVariants, 1, "q1__ru has its base, even archived");
  assert.strictEqual(first.variantsWithoutBase, 2, "intro__part and q2__ru have none in lesson-1");
  const second = lessonOf(plan, "lesson-2");
  assert.strictEqual(second.translatedVariants, 0);
  assert.strictEqual(second.variantsWithoutBase, 1);
  assert.strictEqual(plan.summary.overall.translatedVariants, 1);
  assert.strictEqual(plan.summary.overall.variantsWithoutBase, 3);
  // Every one of them is still keyed on its own: the count is a report line, not a decision.
  assert.strictEqual(plan.summary.overall.keyed, 5);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Difficulty | none on the document; inside the payload, possibly absent or "" |
// tallied from the payload; unreadable is its own bucket, never guessed".

test("difficulty is read from the payload alone, and what does not say EASY or HARD is unreadable", () => {
  const plan = planCatalogRedaction([
    row("q-easy", "lesson-1", {...SINGLE, difficulty: "EASY"}),
    row("q-hard", "lesson-1", {...SINGLE, difficulty: "HARD"}),
    // Case-folded, because that is how lessonAllocatedSeconds reads it — reading, not guessing.
    row("q-lower", "lesson-1", {...SINGLE, difficulty: "hard"}),
    row("q-absent", "lesson-1", SINGLE),
    row("q-empty", "lesson-1", {...SINGLE, difficulty: ""}),
    row("q-number", "lesson-1", {...SINGLE, difficulty: 3}),
    row("q-word", "lesson-1", {...SINGLE, difficulty: "MEDIUM"}),
    row("q-broken", "lesson-1", "{not json"),
    // Tallied for every question considered, a Survey included: the report is about the catalog,
    // not only about what gets a key.
    row("q-survey", "lesson-1", {...SURVEY, difficulty: "HARD"}),
    // The document's own field is not where difficulty lives on a public document, and a value
    // sitting there is never read into the tally.
    row("q-doc-field", "lesson-1", SINGLE, {difficulty: "HARD"}),
  ], opts());

  const lesson = lessonOf(plan, "lesson-1");
  assert.deepStrictEqual(lesson.difficulty, {
    [DIFFICULTY.EASY]: 1,
    [DIFFICULTY.HARD]: 3,
    [DIFFICULTY.UNREADABLE]: 6,
  });
  assert.deepStrictEqual(plan.summary.overall.difficulty, lesson.difficulty);

  // The bucket decides nothing about the key: unreadable difficulty still gets one, unless the
  // redactor itself refuses the payload.
  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["q-easy", "q-hard", "q-lower", "q-absent", "q-empty", "q-word", "q-doc-field"]);
  assert.strictEqual(reasonOf(plan, "q-number"), REFUSAL.INVALID_DIFFICULTY);
  assert.strictEqual(reasonOf(plan, "q-broken"), REFUSAL.MALFORMED_JSON);
});

// --------------------------------------------------------------------------------------------
// Matrix: "A lesson spanning pages | more questions than one query page | grouped whole before it
// is planned — a lesson is never split across two key documents".
// Acceptance: "Given a lesson whose questions span two query pages, when planned, then it yields
// one document".

test("a lesson whose questions span query pages yields one document once the pages are collected", () => {
  const PAGE_SIZE = 4;
  const catalog = [];
  for (let index = 1; index <= 6; index += 1) catalog.push(row(`a${index}`, "lesson-a", SINGLE));
  for (let index = 1; index <= 3; index += 1) catalog.push(row(`b${index}`, "lesson-b", MULTIPLE));
  const pages = [];
  for (let at = 0; at < catalog.length; at += PAGE_SIZE) pages.push(catalog.slice(at, at + PAGE_SIZE));
  assert.strictEqual(pages.length, 3);
  assert.ok(pages[0].every((item) => item.data.lessonId === "lesson-a"), "page 1 must be all lesson-a");
  assert.ok(pages[1].some((item) => item.data.lessonId === "lesson-a"), "lesson-a must spill into page 2");

  // Planned per page, the lesson is split — the outcome the walk must never hand to the write.
  const split = pages.map((page) => planCatalogRedaction(page).documents[keyDocumentPath("lesson-a")]);
  assert.strictEqual(keyIds(split[0]).length, 4);
  assert.strictEqual(keyIds(split[1]).length, 2);

  // Planned over the collected catalog, it is whole.
  const plan = planCatalogRedaction([].concat(...pages));
  assert.strictEqual(Object.keys(plan.documents).length, 2);
  assert.deepStrictEqual(keyIds(plan.documents[keyDocumentPath("lesson-a")]), ["a1", "a2", "a3", "a4", "a5", "a6"]);
  assert.deepStrictEqual(keyIds(plan.documents[keyDocumentPath("lesson-b")]), ["b1", "b2", "b3"]);
});

// --------------------------------------------------------------------------------------------
// Matrix: "Re-run | run twice with the write flag | the second run replaces each lesson's
// document, same as a republish". The write is the script's; what the plan can promise is that a
// second run produces the same documents, at the same paths, for the same questions.

test("a second run over the same catalog plans the same documents at the same paths", () => {
  const rows = mixedCatalog();
  const first = planCatalogRedaction(rows, opts(7));
  const second = planCatalogRedaction(rows, opts(7));
  assert.deepStrictEqual(second, first);

  // With a different draw, the shuffle inside an Ordering key differs and the paths, the keyed
  // ids and every count do not — a replacement, not a merge, is what makes that safe to write.
  const redrawn = planCatalogRedaction(rows, opts(8));
  assert.deepStrictEqual(Object.keys(redrawn.documents).sort(), Object.keys(first.documents).sort());
  for (const path of Object.keys(first.documents)) {
    assert.deepStrictEqual(keyIds(redrawn.documents[path]), keyIds(first.documents[path]), path);
  }
  assert.deepStrictEqual(redrawn.summary, first.summary);
  assert.notDeepStrictEqual(
    redrawn.documents[keyDocumentPath("lesson-b")],
    first.documents[keyDocumentPath("lesson-b")],
    "two draws of an Ordering shuffle should not coincide under these seeds",
  );
});

// --------------------------------------------------------------------------------------------
// The key store's ceiling. A lesson too large for one document refuses the rest by name until the
// refusals themselves stop fitting; from then on they are a count. The plan has to carry both.

test("a full document's omitted refusals are counted as questions, under document-full", () => {
  const rows = [];
  for (let index = 0; index < 8; index += 1) rows.push(row(`heavy${index}`, "lesson-big", heavyMultipleChoice(`t${index}`)));
  for (let index = 0; index < 4000; index += 1) rows.push(row(`bad${index}`, "lesson-big", "{not json"));
  rows.push(row("s1", "lesson-big", SURVEY), row("s2", "lesson-big", SURVEY), row("s3", "lesson-big", SURVEY));
  rows.push(row("small", "lesson-small", SINGLE));
  const plan = planCatalogRedaction(rows, opts());

  const document = plan.documents[keyDocumentPath("lesson-big")];
  assert.ok(document.omitted > 0, "the fixture must overflow the document");
  const recordedFull = document.refusals.filter((record) => record.reason === REASON.DOCUMENT_FULL).length;
  const recordedOther = document.refusals.length - recordedFull;

  const lesson = lessonOf(plan, "lesson-big");
  // Refused counts questions: every record in the document plus every one that did not fit.
  assert.strictEqual(lesson.refused, document.refusals.length + document.omitted);
  assert.strictEqual(lesson.refusedByReason[REASON.DOCUMENT_FULL], recordedFull + document.omitted);
  assert.strictEqual(lesson.refusedByReason[REFUSAL.MALFORMED_JSON], recordedOther);
  assert.strictEqual(lesson.keyed, document.keys.length);
  // Not applicable is asserted against the fixture, not against the remainder.
  assert.strictEqual(surveyCount(rows), 3);
  assert.strictEqual(lesson.notApplicable, surveyCount(rows));

  // The sentinel is one record for many questions, and names no document.
  const sentinels = plan.refusals.filter((record) => record.index < 0);
  assert.strictEqual(sentinels.length, 1);
  assert.strictEqual(sentinels[0].reason, REASON.DOCUMENT_FULL);
  assert.strictEqual(sentinels[0].documentId, "");
  assert.strictEqual(sentinels[0].questionId, "");
  assert.strictEqual(
    plan.summary.overall.refused,
    plan.refusals.filter((record) => record.index >= 0).length + document.omitted,
  );
  // The neighbour lesson is untouched by any of it.
  assert.deepStrictEqual(keyIds(plan.documents[keyDocumentPath("lesson-small")]), ["small"]);
});

test("a lesson holding an already-redacted payload is withheld from the write, never overwritten", () => {
  // The stored key beside such a payload came from the one redact() call that produced both
  // halves; STATUS.ALREADY_REDACTED says to leave it alone. The document planned here has only a
  // refusal for it, and writing that would replace the stored key with nothing.
  const plan = planCatalogRedaction([
    row("q-red", "lesson-w", REDACTED_SINGLE),
    row("q-fine", "lesson-w", SINGLE),
    row("q-other", "lesson-ok", SINGLE),
  ], opts());

  assert.deepStrictEqual(Object.keys(plan.documents), [keyDocumentPath("lesson-ok")]);
  assert.deepStrictEqual(plan.withheld, [{
    lessonId: "lesson-w",
    path: keyDocumentPath("lesson-w"),
    reason: WITHHELD.ALREADY_REDACTED,
    questionIds: ["q-red"],
  }]);
  assert.strictEqual(reasonOf(plan, "q-red"), REASON.ALREADY_REDACTED);

  const lesson = lessonOf(plan, "lesson-w");
  assert.strictEqual(lesson.document, null);
  assert.deepStrictEqual(lesson.withheld, {reason: WITHHELD.ALREADY_REDACTED, questionIds: ["q-red"]});
  // `keyed` is what will be written — nothing here. The key the store produced for q-fine is
  // reported as withheld, so the operator sees the cost of holding the lesson back.
  assert.strictEqual(lesson.keyed, 0);
  assert.strictEqual(lesson.keysWithheld, 1);
  assert.strictEqual(lesson.refused, 1);
  assert.strictEqual(lesson.notApplicable, 0);
  assert.strictEqual(plan.summary.overall.keyed, 1, "only lesson-ok's key counts as keyed");
  assert.strictEqual(plan.summary.overall.keysWithheld, 1);
});

test("the withheld guard reads the payload, so a full document cannot hide a redacted question", () => {
  // Past the ceiling, the key store records a refusal only as a count — no reason, no id. A guard
  // that looked for an `already-redacted` refusal would find none here and write the lesson,
  // replacing the stored key with nothing. The discriminator is in the payload regardless.
  const rows = [];
  for (let index = 0; index < 8; index += 1) rows.push(row(`heavy${index}`, "lesson-full", heavyMultipleChoice(`t${index}`)));
  for (let index = 0; index < 4000; index += 1) rows.push(row(`bad${index}`, "lesson-full", "{not json"));
  rows.push(row("q-red-late", "lesson-full", REDACTED_SINGLE));
  const plan = planCatalogRedaction(rows, opts());

  assert.strictEqual(refusalOf(plan, "q-red-late"), null, "the fixture must push the redacted question past the ceiling");
  assert.deepStrictEqual(plan.documents, {});
  assert.strictEqual(plan.withheld.length, 1);
  assert.deepStrictEqual(plan.withheld[0].questionIds, ["q-red-late"]);
  assert.strictEqual(lessonOf(plan, "lesson-full").keyed, 0);
});

// --------------------------------------------------------------------------------------------
// What the catalog can hold that publication never sends.

test("a document without an id field is keyed under its document id, as publication would", () => {
  const plan = planCatalogRedaction([
    {id: "doc-7", data: {lessonId: "lesson-1", payload: json(SINGLE), archived: false}},
    {id: "doc-8", data: {id: "named-8", lessonId: "lesson-1", payload: json(SINGLE), archived: false}},
  ], opts());
  const document = plan.documents[keyDocumentPath("lesson-1")];
  assert.deepStrictEqual(keyIds(document), ["doc-7", "named-8"]);
  assert.strictEqual(document.keys[0].key.questionId, "doc-7");
});

test("a question without a usable lesson id is refused by name and files under no document", () => {
  const plan = planCatalogRedaction([
    row("q-nolesson", "", SINGLE),
    {id: "q-missing", data: {id: "q-missing", payload: json(SINGLE), archived: false}},
    // Even a Survey: the lesson id is checked before the payload, so nothing is "not applicable".
    row("q-survey", "", SURVEY),
    row("q-fine", "lesson-1", SINGLE),
  ], opts());

  assert.deepStrictEqual(Object.keys(plan.documents), [keyDocumentPath("lesson-1")]);
  for (const id of ["q-nolesson", "q-missing", "q-survey"]) {
    assert.strictEqual(reasonOf(plan, id), REASON.UNUSABLE_LESSON_ID, id);
  }
  const orphan = lessonOf(plan, "");
  assert.strictEqual(orphan.refused, 3);
  assert.strictEqual(orphan.keyed, 0);
  assert.strictEqual(orphan.notApplicable, 0);
  assert.strictEqual(orphan.document, null);
});

test("the overall tally is the sum of the lessons, and the parts are what the fixture says they are", () => {
  const rows = mixedCatalog();
  const plan = planCatalogRedaction(rows, opts());
  const sum = (field) => plan.summary.lessons.reduce((total, lesson) => total + lesson[field], 0);
  for (const field of ["questions", "archived", "considered", "keyed", "keysWithheld", "notApplicable", "refused",
    "legacyDialect", "translatedVariants", "variantsWithoutBase", "emptyDocuments"]) {
    assert.strictEqual(plan.summary.overall[field], sum(field), field);
  }
  for (const bucket of Object.values(DIFFICULTY)) {
    const total = plan.summary.lessons.reduce((acc, lesson) => acc + lesson.difficulty[bucket], 0);
    assert.strictEqual(plan.summary.overall.difficulty[bucket], total, bucket);
  }
  // Each part against the fixture, not against each other.
  const {overall} = plan.summary;
  assert.strictEqual(overall.questions, rows.length);
  assert.strictEqual(overall.archived, rows.filter((item) => item.data.archived === true).length);
  assert.strictEqual(overall.notApplicable, surveyCount(rows));
  // q11's document-level 3 is only the fallback; its payload says EASY, so it is keyed.
  assert.strictEqual(overall.refused, 2, "q4 dangling, q7 legacy");
  assert.strictEqual(overall.keyed, rows.length - overall.archived - surveyCount(rows) - overall.refused);
  assert.strictEqual(overall.legacyDialect, 1);
  assert.strictEqual(overall.translatedVariants, 1);
  assert.deepStrictEqual(plan.summary.lessons.map((lesson) => lesson.lessonId), ["lesson-a", "lesson-b", "lesson-c"]);
});

test("nothing here throws on an empty or malformed catalog", () => {
  for (const input of [undefined, null, [], "questions", 42]) {
    const plan = planCatalogRedaction(input);
    assert.deepStrictEqual(plan.documents, {});
    assert.deepStrictEqual(plan.withheld, []);
    assert.deepStrictEqual(plan.refusals, []);
    assert.deepStrictEqual(plan.summary.lessons, []);
    assert.strictEqual(plan.summary.overall.questions, 0);
  }
  const plan = planCatalogRedaction([null, 42, {}, {id: "x"}, {id: "y", data: "not a map"}]);
  assert.strictEqual(plan.summary.overall.questions, 5);
  assert.strictEqual(plan.summary.overall.refused, 5);
  assert.strictEqual(plan.summary.overall.refusedByReason[REASON.UNUSABLE_LESSON_ID], 5);
  assert.deepStrictEqual(plan.documents, {});
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
  console.error(`catalog-redaction-plan.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`catalog-redaction-plan.test.js OK (${SUITE.length} cases)`);
}
