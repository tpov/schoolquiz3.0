"use strict";

/**
 * Журнал ревью: страница и адресация задания — на фейке Firestore, без сети.
 *
 * До этих правок callable читал коллекцию `admin/review/sync_changes` целиком (`.get()` без
 * `limit`), понимал только скалярный `cursorMs` и требовал `lessonId` в теле записи журнала. Первое
 * означало ответ без потолка, второе — потерю записей, записанных в одну миллисекунду, третье —
 * что задание, записанное прежней версией сервера или пережившее приведение журнала к AD-11,
 * перестаёт находиться совсем.
 */

const assert = require("assert");
const {
  ADMIN_REVIEW_LESSONS,
  DEFAULT_PAGE_SIZE,
  MAX_PAGE_SIZE,
  assignmentIdSplits,
  readCursor,
  readLimit,
  readReviewAssignmentChanges,
  resolveReviewAssignment,
  resolveReviewAssignments,
} = require("./review-journal");
const {ADMIN_REVIEW_SYNC_CHANGES, syncChangeDocument} = require("./sync-changes");

const T0 = 1_756_800_000_000;

/** Метка «упорядочить по id документа» — здесь ею служит сам объект, как `FieldPath.documentId()`. */
const DOCUMENT_ID = {__name__: true};

/**
 * Firestore в памяти: `doc(path).get()`, `collection(path)` с `where`/`orderBy`/`startAfter`/`limit`.
 *
 * Считает чтения коллекции, чтобы тест мог утверждать не только результат, но и то, что страница
 * действительно ограничена запросом, а не отрезана после полной выборки.
 */
function fakeFirestore(initial) {
  const store = new Map(Object.entries(initial));
  const reads = [];
  const parentOf = (path) => path.slice(0, path.lastIndexOf("/"));
  const idOf = (path) => path.slice(path.lastIndexOf("/") + 1);
  const snapshotOf = (path) => ({
    id: idOf(path),
    exists: store.has(path),
    ref: {path},
    data: () => (store.has(path) ? {...store.get(path)} : undefined),
  });

  function query(collectionPath, state) {
    const next = (patch) => query(collectionPath, {...state, ...patch});
    return {
      where(field, op, value) {
        assert.ok(op === "==" || op === ">", `фейк знает только '==' и '>', получил ${op}`);
        return next({filters: state.filters.concat([{field, op, value}])});
      },
      orderBy(field) {
        return next({order: state.order.concat([field])});
      },
      startAfter(...values) {
        return next({startAfter: values});
      },
      limit(count) {
        return next({limit: count});
      },
      async get() {
        reads.push({collectionPath, ...state});
        let docs = [...store.keys()]
          .filter((path) => parentOf(path) === collectionPath)
          .map(snapshotOf);
        for (const filter of state.filters) {
          docs = docs.filter((doc) => {
            const value = filter.field === "__name__" ? doc.id : (doc.data() || {})[filter.field];
            return filter.op === "==" ? value === filter.value : value > filter.value;
          });
        }
        const key = (doc) => state.order.map((field) => (typeof field === "string" ? (doc.data() || {})[field] : doc.id));
        if (state.order.length > 0) {
          docs.sort((a, b) => {
            const left = key(a);
            const right = key(b);
            for (let index = 0; index < left.length; index += 1) {
              if (left[index] < right[index]) return -1;
              if (left[index] > right[index]) return 1;
            }
            return 0;
          });
        }
        if (state.startAfter) {
          docs = docs.filter((doc) => {
            const left = key(doc);
            for (let index = 0; index < state.startAfter.length; index += 1) {
              if (left[index] > state.startAfter[index]) return true;
              if (left[index] < state.startAfter[index]) return false;
            }
            return false;
          });
        }
        if (state.limit !== null) docs = docs.slice(0, state.limit);
        return {docs, empty: docs.length === 0};
      },
    };
  }

  return {
    doc(path) {
      return {path, get: async () => snapshotOf(path)};
    },
    collection(path) {
      return query(path, {filters: [], order: [], startAfter: null, limit: null});
    },
    reads,
  };
}

function journal(entries) {
  return Object.fromEntries(
    entries.map(([docId, data]) => [`${ADMIN_REVIEW_SYNC_CHANGES}/${docId}`, data]),
  );
}

// ─── страница ─────────────────────────────────────────────────────────────────────────────────

async function testThePageIsCutByTheQueryAndSaysWhetherThereIsMore() {
  const db = fakeFirestore(journal(
    [1, 2, 3, 4, 5].map((n) => [`sub-1_l-${n}`, syncChangeDocument("review_assignment", `sub-1_l-${n}`, T0 + n)]),
  ));

  const first = await readReviewAssignmentChanges(db, {cursorMs: 0, limit: 2}, {documentId: DOCUMENT_ID});

  assert.deepStrictEqual(first.changes.map((change) => change.id), ["sub-1_l-1", "sub-1_l-2"]);
  assert.strictEqual(first.hasMore, true);
  // Именно запрос отрезал страницу: до кода дошли ровно две записи, а не все пять.
  assert.strictEqual(db.reads[0].limit, 2);
}

async function testTheLastPageSaysThereIsNoMore() {
  const db = fakeFirestore(journal([
    ["sub-1_l-1", syncChangeDocument("review_assignment", "sub-1_l-1", T0 + 1)],
  ]));

  const page = await readReviewAssignmentChanges(db, {cursorMs: 0, limit: 200}, {documentId: DOCUMENT_ID});

  assert.strictEqual(page.hasMore, false);
  assert.deepStrictEqual(page.changes, [{docId: "sub-1_l-1", changedAtMs: T0 + 1, id: "sub-1_l-1"}]);
}

async function testTwoEntriesInOneMillisecondAreBothRead() {
  // Строгое `>` по одному времени теряло вторую из них навсегда (AD-31).
  const db = fakeFirestore(journal([
    ["sub-1_l-a", syncChangeDocument("review_assignment", "sub-1_l-a", T0)],
    ["sub-1_l-b", syncChangeDocument("review_assignment", "sub-1_l-b", T0)],
  ]));

  const first = await readReviewAssignmentChanges(db, {cursorMs: T0 - 1, limit: 1}, {documentId: DOCUMENT_ID});
  assert.deepStrictEqual(first.changes.map((change) => change.id), ["sub-1_l-a"]);

  const second = await readReviewAssignmentChanges(
    db,
    {cursorMs: T0 - 1, cursor: {changedAtMs: T0, docId: "sub-1_l-a"}, limit: 1},
    {documentId: DOCUMENT_ID},
  );
  assert.deepStrictEqual(second.changes.map((change) => change.id), ["sub-1_l-b"]);
}

async function testTheScalarCursorStillWorksForAClientThatSendsOnlyIt() {
  const db = fakeFirestore(journal([
    ["sub-1_l-1", syncChangeDocument("review_assignment", "sub-1_l-1", T0 + 1)],
    ["sub-1_l-2", syncChangeDocument("review_assignment", "sub-1_l-2", T0 + 2)],
  ]));

  const page = await readReviewAssignmentChanges(db, {cursorMs: T0 + 1}, {documentId: DOCUMENT_ID});

  assert.deepStrictEqual(page.changes.map((change) => change.id), ["sub-1_l-2"]);
}

async function testBothJournalBodyFormsNameTheSameAssignment() {
  const db = fakeFirestore(journal([
    // Прежняя форма журнала ревью: лишние поля, урок в теле.
    ["sub-1_l-1", {id: "sub-1_l-1", assignmentId: "sub-1_l-1", lessonId: "l-1", changedAtMs: T0 + 1}],
    // Форма AD-11.
    ["sub-1_l-2", syncChangeDocument("review_assignment", "sub-1_l-2", T0 + 2)],
  ]));

  const page = await readReviewAssignmentChanges(db, {cursorMs: 0}, {documentId: DOCUMENT_ID});

  assert.deepStrictEqual(page.changes.map((change) => change.id), ["sub-1_l-1", "sub-1_l-2"]);
  assert.deepStrictEqual(page.changes.map((change) => change.docId), ["sub-1_l-1", "sub-1_l-2"]);
}

function testTheRequestedPageSizeIsBoundedAndTheCursorPairWins() {
  assert.strictEqual(readLimit({}), DEFAULT_PAGE_SIZE);
  assert.strictEqual(readLimit({limit: 50}), 50);
  assert.strictEqual(readLimit({limit: 0}), 1);
  assert.strictEqual(readLimit({limit: 10_000}), MAX_PAGE_SIZE);
  assert.strictEqual(readLimit({limit: "нет"}), DEFAULT_PAGE_SIZE);

  assert.deepStrictEqual(
    readCursor({cursorMs: T0 - 1, cursor: {changedAtMs: T0, docId: "d"}}),
    {changedAtMs: T0, docId: "d"},
  );
  // Пустой id документа — «эта миллисекунда ещё не читана»: адресуется скаляром, не парой.
  assert.deepStrictEqual(readCursor({cursor: {changedAtMs: T0, docId: ""}}), {changedAtMs: T0 - 1, docId: null});
  assert.deepStrictEqual(readCursor({cursorMs: 5}), {changedAtMs: 5, docId: null});
  assert.deepStrictEqual(readCursor({}), {changedAtMs: 0, docId: null});
}

// ─── адресация задания ────────────────────────────────────────────────────────────────────────

const LESSON_TASK = {id: "l-1", submissionId: "sub-1", ownerUid: "u-1", questId: "q-1"};

async function testTheAssignmentIsFoundByItsOwnField() {
  const db = fakeFirestore({
    [`${ADMIN_REVIEW_LESSONS}/l-1`]: {...LESSON_TASK, assignmentId: "sub-1_l-1"},
  });

  const hit = await resolveReviewAssignment(db, "sub-1_l-1");

  assert.strictEqual(hit.lessonId, "l-1");
  assert.strictEqual(hit.source, "assignmentId");
  assert.strictEqual(hit.heal, null);
  assert.ok(hit.lesson && hit.lesson.exists, "снимок урока уже прочитан — второй раз читать нечего");
}

async function testAnAssignmentWrittenByThePreviousServerIsStillFound() {
  // Документ урока без `assignmentId`, журнал ещё в прежней форме: урок называет запись журнала.
  const db = fakeFirestore({
    [`${ADMIN_REVIEW_LESSONS}/l-1`]: {...LESSON_TASK},
    ...journal([["sub-1_l-1", {id: "sub-1_l-1", assignmentId: "sub-1_l-1", lessonId: "l-1", changedAtMs: T0}]]),
  });

  const hit = await resolveReviewAssignment(db, "sub-1_l-1");

  assert.strictEqual(hit.lessonId, "l-1");
  assert.strictEqual(hit.source, "journal");
  assert.deepStrictEqual(hit.heal, {path: `${ADMIN_REVIEW_LESSONS}/l-1`, data: {assignmentId: "sub-1_l-1"}});
}

async function testAnAssignmentSurvivesTheJournalBackfillThatRemovesLessonId() {
  // Журнал уже приведён к `{type, id, changedAtMs}`, поле на задании ещё не проставлено — то самое
  // окно между прогоном скрипта и деплоем, из-за которого рецензенты слепли (AD-13).
  const db = fakeFirestore({
    [`${ADMIN_REVIEW_LESSONS}/l-1`]: {...LESSON_TASK},
    ...journal([["sub-1_l-1", syncChangeDocument("review_assignment", "sub-1_l-1", T0)]]),
  });

  const hit = await resolveReviewAssignment(db, "sub-1_l-1");

  assert.strictEqual(hit.lessonId, "l-1");
  assert.strictEqual(hit.source, "split");
  assert.deepStrictEqual(hit.heal, {path: `${ADMIN_REVIEW_LESSONS}/l-1`, data: {assignmentId: "sub-1_l-1"}});
}

async function testUnderscoresOnBothSidesOfTheAssignmentIdDoNotConfuseTheSplit() {
  const db = fakeFirestore({
    [`${ADMIN_REVIEW_LESSONS}/lesson_1`]: {id: "lesson_1", submissionId: "sub_1"},
    ...journal([["sub_1_lesson_1", syncChangeDocument("review_assignment", "sub_1_lesson_1", T0)]]),
  });

  const hit = await resolveReviewAssignment(db, "sub_1_lesson_1");

  assert.strictEqual(hit.lessonId, "lesson_1");
}

async function testALessonOfAnotherSubmissionIsNotMistakenForThisAssignment() {
  const db = fakeFirestore({
    // Тот же урок, но заявка другая: обе половины обязаны совпасть, не одна.
    [`${ADMIN_REVIEW_LESSONS}/l-1`]: {id: "l-1", submissionId: "sub-2"},
  });

  assert.strictEqual(await resolveReviewAssignment(db, "sub-1_l-1"), null);
}

async function testResolvingManyReturnsTheDebtToStampTheFieldOnce() {
  const db = fakeFirestore({
    [`${ADMIN_REVIEW_LESSONS}/l-1`]: {...LESSON_TASK},
    [`${ADMIN_REVIEW_LESSONS}/l-2`]: {id: "l-2", submissionId: "sub-1", assignmentId: "sub-1_l-2"},
    ...journal([["sub-1_l-1", syncChangeDocument("review_assignment", "sub-1_l-1", T0)]]),
  });

  const {resolved, heals} = await resolveReviewAssignments(db, ["sub-1_l-1", "sub-1_l-2", "sub-1_l-missing"]);

  assert.deepStrictEqual(resolved.map((hit) => hit.lessonId), ["l-1", "l-2"]);
  assert.deepStrictEqual(resolved.map((hit) => hit.source), ["split", "assignmentId"]);
  assert.deepStrictEqual(heals, [{path: `${ADMIN_REVIEW_LESSONS}/l-1`, data: {assignmentId: "sub-1_l-1"}}]);
}

function testTheSplitIsBoundedAndOrderedLeftToRight() {
  assert.deepStrictEqual(assignmentIdSplits("a_b_c"), [
    {submissionId: "a", lessonId: "b_c"},
    {submissionId: "a_b", lessonId: "c"},
  ]);
  assert.deepStrictEqual(assignmentIdSplits("no-separator"), []);
  assert.deepStrictEqual(assignmentIdSplits("_leading"), []);
  assert.deepStrictEqual(assignmentIdSplits("trailing_"), []);
  assert.ok(assignmentIdSplits("a_b_c_d_e_f_g_h_i_j_k_l").length <= 8);
}

(async () => {
  await testThePageIsCutByTheQueryAndSaysWhetherThereIsMore();
  await testTheLastPageSaysThereIsNoMore();
  await testTwoEntriesInOneMillisecondAreBothRead();
  await testTheScalarCursorStillWorksForAClientThatSendsOnlyIt();
  await testBothJournalBodyFormsNameTheSameAssignment();
  testTheRequestedPageSizeIsBoundedAndTheCursorPairWins();
  await testTheAssignmentIsFoundByItsOwnField();
  await testAnAssignmentWrittenByThePreviousServerIsStillFound();
  await testAnAssignmentSurvivesTheJournalBackfillThatRemovesLessonId();
  await testUnderscoresOnBothSidesOfTheAssignmentIdDoNotConfuseTheSplit();
  await testALessonOfAnotherSubmissionIsNotMistakenForThisAssignment();
  await testResolvingManyReturnsTheDebtToStampTheFieldOnce();
  testTheSplitIsBoundedAndOrderedLeftToRight();
  console.log("review-journal.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
