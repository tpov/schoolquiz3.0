"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {makeFakeDb, seedHierarchy} = require("./_exam-test-db");
const {
  collectDifficultyPool,
  collectPool,
  courseCertification,
  createExamBank,
  drawFinalSet,
  drawThemeSet,
  parseQuestionRow,
  poolGate,
  buildSittingManifest,
  worthBasisFor,
} = require("./exam-bank");

const SINGLE = (id, text, difficulty) => ({
  id, type: "SingleChoice", text, difficulty,
  options: [{id: "a", text: "x".repeat(20)}, {id: "b", text: "y".repeat(20)}],
  correctOptionId: "a",
});

function questionDoc(id, {lessonId = "lesson", difficulty = "EASY", version = 1, archived, language,
  survey, text} = {}) {
  const content = survey ?
    {id, type: "Survey", text: "s", difficulty, options: [{id: "a", text: "opt"}]} :
    SINGLE(id, text === undefined ? `question ${id}` : text, difficulty);
  return {lessonId, payload: JSON.stringify(content), difficulty, version,
    ...(archived === undefined ? {} : {archived}), ...(language === undefined ? {} : {language})};
}

function fakeLoader(db) {
  return async (tx, lessonId) => {
    const snapshot = await tx.get(db.doc(`lessons/${lessonId}`));
    if (!snapshot.exists) return null;
    const lesson = snapshot.data() || {};
    return {lessonId, authorUid: "author", catalogId: "catalog", questId: "course", sectionId: "section",
      themeId: lesson.themeId, questType: "REGULAR", contentVersion: lesson.version,
      published: lesson.archived !== true, reviewApproved: true, nextChangeAtMs: 1};
  };
}

function rowsToDocs(rows) {
  return new Map(rows.map(([id, doc]) => [`questions/${id}`, doc]));
}

/** Сырые документы → строки `parseQuestionRow`, как их видит банк. */
function parseRows(rows) {
  return rows.map((doc, at) => parseQuestionRow({
    id: doc.payload ? JSON.parse(doc.payload).id : `qdoc${at}`,
    data: () => doc,
  })).filter(Boolean);
}

test("parseQuestionRow drops archived, unparsable and non-object payloads", () => {
  const db = makeFakeDb({initialRows: {
    "questions/a": questionDoc("a"),
    "questions/b": questionDoc("b", {archived: true}),
    "questions/c": {lessonId: "lesson", payload: "{oops", difficulty: "EASY", version: 1},
  }});
  const tx = {get: async (ref) => makeSnapshot(db, ref.path)};
  const snapshot = {docs: ["a", "b", "c"].map((id) => makeSnapshot(db, `questions/${id}`))};
  const parsed = snapshot.docs.map((doc) => parseQuestionRow(doc)).filter(Boolean);
  assert.equal(parsed.length, 1);
  assert.equal(parsed[0].questionId, "a");
});

function makeSnapshot(db, path) {
  const raw = db.rows.get(path);
  return {id: path.split("/").at(-1), exists: raw !== undefined, data: () => (raw === undefined ? undefined : structuredClone(raw))};
}

test("translations collapse to one canonical identity with a deterministic worth source", () => {
  const rows = [
    {...questionDoc("q1__en", {version: 4}), lessonId: "lesson"},
    {...questionDoc("q1__ru", {version: 7}), lessonId: "lesson"},
    questionDoc("q2", {version: 2}),
  ];
  const pool = collectPool(parseRows(rows));
  assert.equal(pool.easy.length, 2, "q1__en и q1__ru — один канонический вопрос");
  const canonical = pool.easy.find((entry) => entry.canonicalId === "q1");
  assert.equal(canonical.questionId, "q1__en", "источник стоимости — минимальный документ");
  assert.equal(canonical.revision, 4);
});

test("surveys and archived rows never reach the pool", () => {
  const rows = [
    questionDoc("s1", {survey: true}),
    questionDoc("a1", {archived: true}),
    questionDoc("h1", {difficulty: "HARD"}),
  ];
  const pool = collectPool(parseRows(rows));
  assert.equal(pool.easy.length, 0);
  assert.equal(pool.hard.length, 1);
});

test("pool gates refuse before any charge semantics apply", () => {
  const small = collectPool(parseRows([questionDoc("e1"), questionDoc("h1")]));
  assert.equal(poolGate("ThemeTest", small), false, "1 EASY + 1 HARD — не тема");
  const themeRows = [
    ...Array.from({length: 5}, (_, at) => questionDoc(`e${at}`)),
    ...Array.from({length: 15}, (_, at) => questionDoc(`h${at}`, {difficulty: "HARD"})),
  ];
  assert.equal(poolGate("ThemeTest", collectPool(parseRows(themeRows))), true);
  const nineteenHard = Array.from({length: 19}, (_, at) => questionDoc(`f${at}`, {difficulty: "HARD"}));
  assert.equal(poolGate("FinalExam", collectPool(parseRows(nineteenHard))), false);
  assert.equal(poolGate("FinalExam", collectPool(parseRows([
    ...nineteenHard, questionDoc("f20", {difficulty: "HARD"})]))), true);
});

test("theme draw picks exactly five EASY and fifteen HARD distinct identities", () => {
  const rows = [
    ...Array.from({length: 30}, (_, at) => questionDoc(`e${at}`)),
    ...Array.from({length: 40}, (_, at) => questionDoc(`h${at}`, {difficulty: "HARD"})),
  ];
  const pool = collectPool(parseRows(rows));
  const rank = (key) => key.split("").reverse().join("");
  const drawn = drawThemeSet(pool, rank);
  assert.equal(drawn.easy.length, 5);
  assert.equal(drawn.hard.length, 15);
  const ids = new Set([...drawn.easy, ...drawn.hard].map((entry) => entry.canonicalId));
  assert.equal(ids.size, 20);
  // Жребий от ранга: другой ранг — другой набор; тот же ранг — тот же.
  const again = drawThemeSet(pool, rank);
  assert.deepEqual(again.easy.map((entry) => entry.canonicalId), drawn.easy.map((entry) => entry.canonicalId));
  assert.throws(() => drawThemeSet(collectPool([questionDoc("e0")]), rank), (error) => error.code === "POOL_TOO_SMALL");
});

test("final draw takes twenty hard questions only", () => {
  const rows = Array.from({length: 25}, (_, at) => questionDoc(`f${at}`, {difficulty: "HARD"}));
  const drawn = drawFinalSet(collectPool(parseRows(rows)), (key) => key);
  assert.equal(drawn.easy.length, 0);
  assert.equal(drawn.hard.length, 20);
});

test("sitting manifest freezes slot identities, worth seconds and answer seconds", () => {
  const rows = [
    questionDoc("e1"), questionDoc("e2"), questionDoc("e3"), questionDoc("e4"), questionDoc("e5"),
    ...Array.from({length: 15}, (_, at) => questionDoc(`h${at}`, {difficulty: "HARD"})),
  ];
  const pool = collectPool(parseRows(rows));
  const drawn = drawThemeSet(pool, (key) => key);
  const manifest = buildSittingManifest({mode: "ThemeTest", drawn});
  assert.equal(manifest.slots.length, 20);
  assert.equal(manifest.slots[0].shownDifficulty, "EASY");
  assert.equal(manifest.slots[5].shownDifficulty, "HARD");
  assert.equal(manifest.slots.every((slot) => slot.revision >= 1), true);
  assert.equal(manifest.manifestHash.length, 64);
  // Хеш не зависит от приватных содержимых — только идентичности и числа.
  const again = buildSittingManifest({mode: "ThemeTest", drawn});
  assert.equal(again.manifestHash, manifest.manifestHash);
});

test("worth basis over the whole pool does not move with the draw", () => {
  const rows = [
    ...Array.from({length: 10}, (_, at) => questionDoc(`e${at}`, {text: "x".repeat(at + 1)})),
    ...Array.from({length: 20}, (_, at) => questionDoc(`h${at}`, {difficulty: "HARD",
      text: "y".repeat(at + 1)})),
  ];
  const pool = collectPool(parseRows(rows));
  const basis = worthBasisFor("ThemeTest", pool);
  assert.ok(basis.TE >= 25 && basis.TH >= 75);
  const rankA = (key) => key;
  const rankB = (key) => key.split("").reverse().join("");
  const setA = drawThemeSet(pool, rankA);
  const setB = drawThemeSet(pool, rankB);
  const sumA = [...setA.easy, ...setA.hard].reduce((total, entry) => total + entry.worthSeconds, 0);
  const sumB = [...setB.easy, ...setB.hard].reduce((total, entry) => total + entry.worthSeconds, 0);
  // Основа считается по пулу, поэтому удачный жребий цену не двигает: суммы наборов могут
  // отличаться, средние пула — нет.
  assert.equal(basis.sumEasy, pool.easy.reduce((total, entry) => total + entry.worthSeconds, 0));
  assert.notEqual(sumA, sumB);
});

test("certification recomputes from theme pools and never increments", () => {
  const qualified = Array.from({length: 12}, (_, at) => ({themeId: `t${at}`,
    easyDistinct: 100, hardDistinct: 100}));
  assert.equal(courseCertification(qualified).eligible, true);
  assert.equal(courseCertification(qualified.slice(0, 11)).eligible, false, "11 тем мало");
  const weak = qualified.slice(0, 11).concat([{themeId: "t11", easyDistinct: 99, hardDistinct: 100}]);
  assert.equal(courseCertification(weak).eligible, false, "тема с 99 EASY не считается");
});

test("themePool reads approved lessons below the theme and their questions", async () => {
  const rows = [
    questionDoc("e1"), questionDoc("e2"), questionDoc("e3"), questionDoc("e4"), questionDoc("e5"),
    ...Array.from({length: 15}, (_, at) => questionDoc(`h${at}`, {difficulty: "HARD"})),
  ];
  const initial = {
    ...seedHierarchy({}),
    ...Object.fromEntries(rows.map((doc, at) => [`questions/q${at}`, doc])),
    "lessons/lesson2": {id: "lesson2", themeId: "theme", version: 1, reviewApprovedAtMs: 1,
      reviewSubmissionId: "s2", archived: false},
  };
  const {db} = makeFakeDb({initialRows: initial});
  const bank = createExamBank({db, loadLessonContext: fakeLoader(db)});
  const pool = await db.runTransaction(async (tx) => bank.themePool(tx, "theme"));
  assert.equal(pool.lessons.length, 2, "оба урока темы одобрены");
  assert.equal(pool.pool.easy.length, 5);
  assert.equal(pool.pool.hard.length, 15);
});

test("coursePool sums themes and certification for the final", async () => {
  const themeA = [
    ...Array.from({length: 100}, (_, at) => questionDoc(`ae${at}`, {lessonId: "lessonA"})),
    ...Array.from({length: 100}, (_, at) => questionDoc(`ah${at}`, {lessonId: "lessonA", difficulty: "HARD"})),
  ];
  const initial = {
    [`quests/course`]: {id: "course", questType: "REGULAR", catalogId: "catalog", version: 3,
      authorUid: "author", archived: false},
    "sections/sA": {id: "sA", questId: "course", archived: false},
    "sections/sB": {id: "sB", questId: "course", archived: false},
    "themes/tA": {id: "tA", sectionId: "sA", archived: false},
    "themes/tB": {id: "tB", sectionId: "sB", archived: false},
    "lessons/lessonA": {id: "lessonA", themeId: "tA", version: 1, reviewApprovedAtMs: 1,
      reviewSubmissionId: "s", archived: false},
    ...Object.fromEntries(themeA.map((doc, at) => [`questions/aq${at}`, {...doc, lessonId: "lessonA"}])),
  };
  const {db} = makeFakeDb({initialRows: initial});
  const bank = createExamBank({db, loadLessonContext: fakeLoader(db)});
  const pool = await db.runTransaction(async (tx) => bank.coursePool(tx, "course"));
  assert.equal(pool.themeIds.length, 2);
  assert.equal(pool.themeSummaries.length, 2);
  assert.equal(pool.certification.eligible, false, "две темы не дотягивают до двенадцати");
  assert.equal(pool.pool.hard.length, 100);
});

test("collectDifficultyPool is a pure canonical view", () => {
  const rows = [
    {...questionDoc("q1__en", {version: 1}), difficulty: "HARD"},
    {...questionDoc("q1__de", {version: 9}), difficulty: "HARD"},
  ];
  const pool = collectDifficultyPool(parseRows(rows), "HARD");
  assert.equal(pool.length, 1);
  assert.equal(pool[0].questionId, "q1__de", "источник стоимости — минимальный идентификатор документа");
  assert.equal(pool[0].revision, 9);
});
