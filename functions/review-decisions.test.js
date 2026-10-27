"use strict";

/**
 * «Решения» админов (история 4.17, UX-22): вью по-вопросных вердиктов модели и отметки ✓/✗
 * с комментарием. Сюита гоняет настоящие callable против стаба Firestore: строки соединяют
 * вердикт с заявкой и отметками, фильтр честен по состоянию отметки, отметка живёт отдельно
 * от неизменяемого вердикта и перезаписывается повтором, чужие и битые входы получают
 * именованный отказ без записей, не-админ не получает ни данных, ни права писать отметки.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath;
stub.loaded = true;
stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const {
  DECISIONS_VIEW_VERSION,
  MARKS_COLLECTION,
  markDocumentId,
} = require("./review-decisions");

const VERDICT_ID = "sub_lesson_WORDS_1";

function verdictRecord(overrides = {}) {
  return {
    schemaVersion: 1,
    submissionId: "sub",
    lessonId: "lesson",
    stage: "WORDS",
    attempt: 1,
    revision: 3,
    model: "glm-test",
    promptVersion: "words-v1",
    sources: ["https://example.org/source"],
    rationale: "основание проверки",
    questionVerdicts: [
      {questionId: "q1", verdict: "PASS", reason: "чисто"},
      {questionId: "q2", verdict: "FAIL", reason: "запрещённое слово"},
    ],
    ...overrides,
  };
}

function submissionDocument() {
  return {
    submissionId: "sub",
    ownerUid: "author",
    status: "UNDER_REVIEW",
    draft: {id: "quest", catalogId: "catalog", title: "Квест"},
    lessons: [{id: "lesson", title: "Урок"}],
    questions: [
      {id: "q1", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru", text: "Вопрос один", payload: "{\"options\":[]}"},
      {id: "q2", lessonId: "lesson", type: "SINGLE_CHOICE", language: "ru", text: "Вопрос два", payload: "{\"options\":[]}"},
    ],
  };
}

function world(extra = {}) {
  return {
    "profiles/admin": {uid: "admin", adminLevel: 150},
    "profiles/plain": {uid: "plain"},
    "profiles/author": {uid: "author"},
    [`review_verdicts/${VERDICT_ID}`]: verdictRecord(),
    "quest_review_requests/sub": submissionDocument(),
    ...extra,
  };
}

async function fetchDecisions(uid, data = {}) {
  return functions.fetchReviewDecisions.run({auth: {uid}, data});
}

test("admin reads question-verdict rows joined with the submission and mark state", async () => {
  store.reset(world());
  const page = await fetchDecisions("admin");
  assert.equal(page.viewVersion, DECISIONS_VIEW_VERSION);
  assert.equal(page.entries.length, 2);
  const first = page.entries[0];
  assert.equal(first.unreadable, false);
  assert.equal(first.verdictId, VERDICT_ID);
  assert.equal(first.questTitle, "Квест");
  assert.equal(first.lessonTitle, "Урок");
  assert.equal(first.stage, "WORDS");
  assert.equal(first.attempt, 1);
  assert.equal(first.revision, 3);
  assert.equal(first.model, "glm-test");
  assert.equal(first.promptVersion, "words-v1");
  assert.deepEqual(first.sources, ["https://example.org/source"]);
  assert.equal(first.rationale, "основание проверки");
  assert.equal(first.questionId, "q1");
  assert.equal(first.verdict, "PASS");
  assert.equal(first.reason, "чисто");
  assert.equal(first.question.text, "Вопрос один");
  assert.equal(first.mark, null);
  assert.equal(page.hasMore, false);
});

test("filters follow the mark state: UNMARKED default, VALID and INVALID narrow the page", async () => {
  store.reset(world());
  const marked = await functions.markReviewDecision.run({
    auth: {uid: "admin"},
    data: {verdictId: VERDICT_ID, questionId: "q2", mark: "INVALID", comment: "модель ошиблась"},
  });
  assert.equal(marked.mark, "INVALID");
  assert.equal(marked.comment, "модель ошиблась");

  const unmarked = await fetchDecisions("admin", {filter: "UNMARKED"});
  assert.deepEqual(unmarked.entries.map((entry) => entry.questionId), ["q1"]);

  const invalid = await fetchDecisions("admin", {filter: "INVALID"});
  assert.deepEqual(invalid.entries.map((entry) => entry.questionId), ["q2"]);
  assert.equal(invalid.entries[0].mark.mark, "INVALID");
  assert.equal(invalid.entries[0].mark.comment, "модель ошиблась");
  assert.equal(invalid.entries[0].mark.adminUid, "admin");

  const valid = await fetchDecisions("admin", {filter: "VALID"});
  assert.deepEqual(valid.entries, []);
});

test("re-marking overwrites the same identity: one document, last decision stands", async () => {
  store.reset(world());
  await functions.markReviewDecision.run({
    auth: {uid: "admin"}, data: {verdictId: VERDICT_ID, questionId: "q1", mark: "VALID"},
  });
  const second = await functions.markReviewDecision.run({
    auth: {uid: "admin"},
    data: {verdictId: VERDICT_ID, questionId: "q1", mark: "INVALID", comment: "пересмотрел"},
  });
  assert.equal(second.mark, "INVALID");
  const markDocs = [...store.documents.keys()].filter((path) => path.startsWith(`${MARKS_COLLECTION}/`));
  assert.equal(markDocs.length, 1);
  assert.equal(markDocs[0], `${MARKS_COLLECTION}/${markDocumentId(VERDICT_ID, "q1")}`);
  // Вердикт не тронут: отметка живёт отдельно от неизменяемой записи.
  assert.deepEqual(store.documents.get(`review_verdicts/${VERDICT_ID}`), verdictRecord());
  const page = await fetchDecisions("admin", {filter: "INVALID"});
  assert.deepEqual(page.entries.map((entry) => entry.questionId), ["q1"]);
});

test("mark refusals are named and write nothing", async () => {
  store.reset(world());
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "admin"},
      data: {verdictId: "sub_lesson_WORDS_9", questionId: "q1", mark: "VALID"},
    }),
    (error) => error.code === "not-found",
  );
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "admin"},
      data: {verdictId: VERDICT_ID, questionId: "q9", mark: "VALID"},
    }),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "admin"},
      data: {verdictId: VERDICT_ID, questionId: "q1", mark: "MAYBE"},
    }),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "admin"},
      data: {verdictId: VERDICT_ID, questionId: "q1", mark: "VALID", comment: "д".repeat(2001)},
    }),
    (error) => error.code === "invalid-argument",
  );
  assert.deepEqual([...store.documents.keys()].filter((path) => path.startsWith(`${MARKS_COLLECTION}/`)), []);
});

test("a corrupt verdict record keeps its trace as an unreadable row and refuses marks loudly", async () => {
  store.reset(world({
    [`review_verdicts/${VERDICT_ID}`]: {schemaVersion: 1, submissionId: "sub", broken: true},
  }));
  const page = await fetchDecisions("admin");
  assert.equal(page.entries.length, 1);
  assert.equal(page.entries[0].unreadable, true);
  assert.equal(page.entries[0].verdictId, VERDICT_ID);
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "admin"},
      data: {verdictId: VERDICT_ID, questionId: "q1", mark: "VALID"},
    }),
    (error) => error.code === "data-loss",
  );
});

test("a verdict without its submission still audits: rows keep verdict truth, preview degrades", async () => {
  store.reset(world());
  store.documents.delete("quest_review_requests/sub");
  const page = await fetchDecisions("admin");
  assert.equal(page.entries.length, 2);
  assert.equal(page.entries[0].questTitle, "");
  assert.equal(page.entries[0].lessonTitle, "");
  assert.equal(page.entries[0].question, null);
  assert.equal(page.entries[0].verdict, "PASS");
});

test("paging is bounded and the verdict-id cursor never repeats a row", async () => {
  const extraVerdicts = {};
  for (let index = 0; index < 3; index += 1) {
    extraVerdicts[`review_verdicts/sub_lesson${index}_FACTS_1`] = verdictRecord({
      stage: "FACTS",
      lessonId: `lesson${index}`,
    });
  }
  store.reset(world(extraVerdicts));
  const first = await fetchDecisions("admin", {limit: 3});
  assert.equal(first.entries.length, 3);
  assert.equal(first.hasMore, true);
  assert.equal(typeof first.nextCursor.verdictId, "string");
  // Страница остановилась посреди записи — курсор называет последний рассмотренный вопрос.
  assert.notEqual(first.nextCursor.afterQuestionId, null);
  const second = await fetchDecisions("admin", {limit: 60, cursor: first.nextCursor});
  const seenFirst = new Set(first.entries.map((entry) => `${entry.verdictId}:${entry.questionId}`));
  for (const entry of second.entries) {
    assert.equal(seenFirst.has(`${entry.verdictId}:${entry.questionId}`), false);
  }
  assert.equal(second.entries.length + first.entries.length, 8);
  assert.equal(second.hasMore, false);
  // Третья страница после исчерпания пуста и говорит правду: больше ничего нет.
  const third = await fetchDecisions("admin", {cursor: second.nextCursor});
  assert.deepEqual(third.entries, []);
  assert.equal(third.hasMore, false);
});

test("non-admins get nothing: read and mark are both refused by the verifier gate", async () => {
  store.reset(world());
  await assert.rejects(
    fetchDecisions("plain"),
    (error) => error.code === "permission-denied",
  );
  await assert.rejects(
    functions.markReviewDecision.run({
      auth: {uid: "plain"}, data: {verdictId: VERDICT_ID, questionId: "q1", mark: "VALID"},
    }),
    (error) => error.code === "permission-denied",
  );
  assert.deepEqual([...store.documents.keys()].filter((path) => path.startsWith(`${MARKS_COLLECTION}/`)), []);
});

test("bad pages are refused by name, not silently defaulted; the ceiling clamps instead", async () => {
  store.reset(world());
  await assert.rejects(fetchDecisions("admin", {filter: "EVERYTHING"}), (error) => error.code === "invalid-argument");
  await assert.rejects(fetchDecisions("admin", {limit: 0}), (error) => error.code === "invalid-argument");
  await assert.rejects(
    fetchDecisions("admin", {cursor: {verdictId: "a/b"}}),
    (error) => error.code === "invalid-argument",
  );
  // Потолок ограничивает, а не отказывает: широкая просьба читается страницей потолка.
  const clamped = await fetchDecisions("admin", {limit: 500});
  assert.equal(clamped.entries.length <= 60, true);
});
