"use strict";

// Инструмент journey 4.3 tz06c-wire-views-to-authoring (epic-authoring-review) против
// demo-backend: снапшоты публичного/приватного дерева для оракула «чужой материал не тронут»,
// бутстрап кураторского профиля, scripted-ревью (TESTING/LOGIC) реальными callables и
// чтение результатов. Запуск при поднятых эмуляторах:
//   GCLOUD_PROJECT=demo-schoolquiz-mvp FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 \
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 node scripts/demo-fixtures/tz06c-journey.js <cmd> …
// Команды:
//   snapshot <file>                    — дамп дерева в JSON (для compare)
//   compare <before> <after>           — побайтовый диф двух снапшотов (кроме разрешённых)
//   bootstrap-curator <uid>            — profiles/{uid}.adminLevel = 100 (demo-фиксур роли)
//   review <submissionId> <reviewerUid> <score> — TESTING, затем LOGIC по заданиям заявки
//   review-questions <submissionId> <reviewerUid> <s1,s2,…> — TESTING с по-вопросными оценками
//                                        (4.6: порог виден на смешанных оценках, без LOGIC)
//   author-outcomes <submissionId> <authorUid> — listMyQuestReviewSubmissions автором живьём:
//                                        именованные стадии + по-вопросные исходы (CAP-3)
//   translate <submissionId> <translatorUid> <lang> [blankQuestionId] — TRANSLATION реальным
//                                        приёмником; blankQuestionId подаёт вопрос без текста
//                                        (missing text never auto-approved)
//   review-segments <submissionId> <reviewerUid> <lang> <qid:key:decision,…> | silent
//                                        — TRANSLATION_REVIEW с явными решениями; `silent`
//                                        шлёт старый accepted-булев и ждёт отказа провода
//   status <submissionId>              — заявка: статус, задания, агрегат проверок
//   catalog                            — listArenaLessons живьём (карточки + версии)

const assert = require("node:assert/strict");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const SUBMIT_REVIEW_PATH = `/${PROJECT}/us-central1/submitReviewAction`;
const CATALOG_PATH = `/${PROJECT}/us-central1/listArenaLessons`;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Всё, что читает снапшот. Публичное дерево целиком + плейсменты + рейтинги + приватные
// деревья всех авторов + заявки/задания ревью + mutation_keys. Пользователей/профили
// не читаем: финансовые оракулы здесь не участвуют, а профили растут от легальных
// репутационных дельт ревьюеров.
const COLLECTIONS = [
  {type: "col", p: "quests"},
  {type: "col", p: "sections"},
  {type: "col", p: "themes"},
  {type: "col", p: "lessons"},
  {type: "col", p: "lesson_placements"},
  {type: "col", p: "lesson_ratings"},
  {type: "sub", p: "lesson_ratings", sub: "votes"},
  {type: "colgroup", p: "private"},
  {type: "col", p: "quest_review_requests"},
  {type: "col", p: "admin/review/lessons"},
];

async function dumpCollection(prefix) {
  const out = {};
  const snap = await db.collection(prefix).get();
  for (const doc of snap.docs) {
    out[doc.id] = doc.data();
  }
  return out;
}

// Приватные деревья: private/{uid}/catalogs/{catalogId}/quests/{questId}/{sections,themes,lessons,…}
// — рекурсивный дамп: документы каждой коллекции + вложенные подколлекции, ключ = путь коллекции.
async function dumpCollectionRecursive(colPath, out) {
  const docs = await db.collection(colPath).get();
  for (const doc of docs.docs) {
    out[colPath] = out[colPath] || {};
    out[colPath][doc.id] = doc.data();
    for (const sub of await doc.ref.listCollections()) {
      await dumpCollectionRecursive(sub.path, out);
    }
  }
}

async function dumpPrivate() {
  const out = {};
  const owners = await db.collection("private").listDocuments();
  for (const ownerDoc of owners) {
    for (const col of await ownerDoc.listCollections()) {
      await dumpCollectionRecursive(col.path, out);
    }
  }
  return out;
}

async function snapshot() {
  const out = {takenAtMs: Date.now(), collections: {}};
  for (const spec of COLLECTIONS) {
    if (spec.type === "col") {
      out.collections[spec.p] = await dumpCollection(spec.p);
    } else if (spec.type === "sub") {
      const parents = await db.collection(spec.p).listDocuments();
      const merged = {};
      for (const parent of parents) {
        Object.assign(merged, await dumpCollection(`${spec.p}/${parent.id}/${spec.sub}`));
      }
      out.collections[`${spec.p}/${spec.sub}`] = merged;
    } else if (spec.type === "colgroup") {
      Object.assign(out.collections, await dumpPrivate());
    }
  }
  return out;
}

function diffValues(before, after, label, report) {
  const beforeKeys = new Set(Object.keys(before || {}));
  const afterKeys = new Set(Object.keys(after || {}));
  for (const key of afterKeys) {
    if (!beforeKeys.has(key)) report.added.push(`${label}/${key}`);
  }
  for (const key of beforeKeys) {
    if (!afterKeys.has(key)) {
      report.removed.push(`${label}/${key}`);
      continue;
    }
    const a = JSON.stringify(before[key]);
    const b = JSON.stringify(after[key]);
    if (a !== b) report.changed.push(`${label}/${key}`);
  }
}

function compare(before, after) {
  const report = {added: [], removed: [], changed: []};
  const labels = new Set([...Object.keys(before.collections), ...Object.keys(after.collections)]);
  for (const label of labels) {
    diffValues(before.collections[label], after.collections[label], label, report);
  }
  return report;
}

async function playerIdToken(uid) {
  const customToken = await admin.auth().createCustomToken(uid);
  const response = await fetch(
    `http://127.0.0.1:${HOSTS.authPort}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo`,
    {method: "POST", headers: {"content-type": "application/json"}, body: JSON.stringify({token: customToken, returnSecureToken: true})},
  );
  const json = await response.json();
  assert.equal(response.status, 200, "auth emulator must exchange the custom token");
  return json.idToken;
}

async function callable(urlPath, payload, token) {
  const response = await fetch(`http://127.0.0.1:${HOSTS.upstream}${urlPath}`, {
    method: "POST",
    headers: {"content-type": "application/json", authorization: `Bearer ${token}`},
    body: JSON.stringify({data: payload}),
  });
  const json = await response.json();
  return {status: response.status, json};
}

async function bootstrapCurator(uid) {
  assert.ok(/^[A-Za-z0-9]{20,128}$/.test(uid), "uid must look like a firebase uid");
  await db.doc(`profiles/${uid}`).set({adminLevel: 100, testerLevel: 0}, {merge: true});
  const doc = await db.doc(`profiles/${uid}`).get();
  assert.equal(doc.data().adminLevel, 100, "adminLevel must be 100 after bootstrap");
  console.log(`BOOTSTRAP-CURATOR OK profiles/${uid}.adminLevel=100`);
}

async function statusOf(submissionId) {
  const request = await db.collection("quest_review_requests").doc(submissionId).get();
  assert.ok(request.exists, `quest_review_requests/${submissionId} must exist`);
  const data = request.data() || {};
  const tasks = await db.collection("admin/review/lessons").where("submissionId", "==", submissionId).get();
  const taskRows = [];
  for (const task of tasks.docs) {
    const t = task.data() || {};
    taskRows.push({assignmentId: task.id, lessonId: t.lessonId, status: t.status,
      checks: {testing: (t.checks || {}).testingScore, logic: (t.checks || {}).logicScore,
        isTested: (t.checks || {}).isTested, isLogicReviewed: (t.checks || {}).isLogicReviewed},
      rejectedAtMs: t.rejectedAtMs || null});
  }
  const lesson = await db.doc(`lessons/${data.lessons && data.lessons[0] ? data.lessons[0].id : "-"}`).get();
  console.log(JSON.stringify({
    submissionId,
    status: data.status,
    ownerUid: data.ownerUid,
    questId: data.draft ? data.draft.id : null,
    tasks: taskRows,
    publishedAtMs: data.publishedAtMs || null,
    lessonDoc: lesson.exists ? {version: lesson.data().version, reviewApprovedAtMs: lesson.data().reviewApprovedAtMs || null,
      publishedAtMs: lesson.data().publishedAtMs || null} : null,
  }, null, 2));
}

async function review(submissionId, reviewerUid, score) {
  const tasks = await db.collection("admin/review/lessons").where("submissionId", "==", submissionId).get();
  assert.equal(tasks.size, 1, "the journey submission carries exactly one lesson task");
  const taskDoc = tasks.docs[0];
  // Тело задания: doc id = урок, поле assignmentId = ключ для callables (AD-11), ownerUid = автор.
  const assignmentId = taskDoc.data().assignmentId;
  const lessonId = taskDoc.data().id;
  const ownerUid = taskDoc.data().ownerUid;
  assert.equal(typeof assignmentId, "string", "task must carry its assignment id");
  assert.equal(typeof lessonId, "string", "task must carry its lesson id");
  assert.notEqual(ownerUid, reviewerUid, "the reviewer must not be the author");
  const token = await playerIdToken(reviewerUid);

  // TESTING с 4.6 оценивает каждый вопрос ревизии: questionScores покрывает вопросный состав
  // задания ровно — приёмник отказывает неполному набору (один независимый ревью на вопрос).
  // Состав задания живёт в подколлекции admin/review/lessons/{lesson}/quests/{quest}/questions.
  const questId = taskDoc.data().questId;
  const questionsSnapshot = await taskDoc.ref
    .collection("quests").doc(questId).collection("questions").get();
  const ids = questionsSnapshot.docs.map((doc) => doc.id);
  assert.ok(ids.length > 0, "task must carry its question subcollection for per-question testing");
  const questionScores = ids.map((questionId) => ({questionId, score}));
  const testing = await callable(SUBMIT_REVIEW_PATH,
    {assignmentId, lessonId, kind: "TESTING", score, questionScores, language: null}, token);
  assert.equal(testing.status, 200, `TESTING review must be accepted: ${JSON.stringify(testing.json)}`);
  assert.equal(testing.json.result.published, false, "TESTING alone must not publish");
  console.log(`REVIEW TESTING score=${score} questionScores=${questionScores.length} -> aggregate ${JSON.stringify(testing.json.result.aggregate)}`);

  const logic = await callable(SUBMIT_REVIEW_PATH,
    {assignmentId, lessonId, kind: "LOGIC", score, language: null}, token);
  assert.equal(logic.status, 200, `LOGIC review must be accepted: ${JSON.stringify(logic.json)}`);
  console.log(`REVIEW LOGIC score=${score} -> aggregate ${JSON.stringify(logic.json.result.aggregate)}`);
  assert.equal(logic.json.result.published, true, "LOGIC passing the threshold must publish the submission");
  console.log(`REVIEW OK submission ${submissionId} published=${logic.json.result.published}`);
}

// По-вопросный TESTING-ревью (4.6): оценки из CSV ровно на состав задания; приёмник сам
// отвергает неполный/чужой набор. LOGIC здесь не подаётся — исход порога читают из заявки.
async function reviewQuestions(submissionId, reviewerUid, scoresCsv) {
  const scores = scoresCsv.split(",").map((value) => Number(value));
  const task = await journeyTask(submissionId);
  assert.equal(scores.length, task.questionIds.length, "scores must cover the assignment question set");
  const token = await playerIdToken(reviewerUid);
  const response = await callable(SUBMIT_REVIEW_PATH, {
    assignmentId: task.assignmentId,
    lessonId: task.lessonId,
    kind: "TESTING",
    score: Math.min(...scores),
    questionScores: task.questionIds.map((questionId, index) => ({questionId, score: scores[index]})),
    language: null,
  }, token);
  assert.equal(response.status, 200, `TESTING must be accepted: ${JSON.stringify(response.json)}`);
  console.log(`REVIEW-QUESTIONS ${submissionId} scores=[${scores.join(",")}] -> ${JSON.stringify(response.json.result.aggregate)}`);
}

// Автор читает свои вердикты реальным вью-callable: поимённые стадии и по-вопросные исходы.
async function authorOutcomes(submissionId, authorUid) {
  const token = await playerIdToken(authorUid);
  const response = await callable(`/${PROJECT}/us-central1/listMyQuestReviewSubmissions`,
    {submissionIds: [submissionId], legacyDraftIds: []}, token);
  assert.equal(response.status, 200, `author view must answer: ${JSON.stringify(response.json)}`);
  const entry = (response.json.result.entries || []).find((row) => row.submissionId === submissionId);
  console.log(JSON.stringify(entry || {missing: true}, null, 2));
}

// Задание заявки: единственный урок + состав вопросов из подколлекции задания.
async function journeyTask(submissionId) {
  const tasks = await db.collection("admin/review/lessons").where("submissionId", "==", submissionId).get();
  assert.equal(tasks.size, 1, "the journey submission carries exactly one lesson task");
  const taskDoc = tasks.docs[0];
  const data = taskDoc.data();
  const questionsSnapshot = await taskDoc.ref
    .collection("quests").doc(data.questId).collection("questions").get();
  const questionIds = questionsSnapshot.docs.map((doc) => doc.id);
  assert.ok(questionIds.length > 0, "task must carry its question subcollection");
  return {taskDoc, data, assignmentId: data.assignmentId, lessonId: data.id, ownerUid: data.ownerUid,
    questId: data.questId, questionIds};
}

// TRANSLATION реальным приёмником: перевод строится из состава задания (текст = lang:id).
// [blankQuestionId] оставляет одному вопросу пустой текст — язык не должен засчитаться.
async function translate(submissionId, translatorUid, language, blankQuestionId) {
  const task = await journeyTask(submissionId);
  const questionsSnapshot = await task.taskDoc.ref
    .collection("quests").doc(task.questId).collection("questions").get();
  const translated = questionsSnapshot.docs.map((doc) => ({
    id: doc.id,
    lessonId: task.lessonId,
    type: doc.data().type || "QA",
    language,
    text: doc.id === blankQuestionId ? "" : `${language}:${doc.id}`,
    payload: doc.data().payload || "{}",
    updatedAtMs: Date.now(),
  }));
  const token = await playerIdToken(translatorUid);
  const response = await callable(SUBMIT_REVIEW_PATH, {
    assignmentId: task.assignmentId, lessonId: task.lessonId, kind: "TRANSLATION",
    language, translatedQuestions: translated, segmentResults: [], questionScores: [],
  }, token);
  console.log(JSON.stringify({httpStatus: response.status, aggregate: response.json.result ? response.json.result.aggregate : response.json}, null, 2));
}

// TRANSLATION_REVIEW с явными решениями по сегментам; `silent` доказывает отказ старого
// провода (accepted-булев без решения).
async function reviewSegments(submissionId, reviewerUid, language, decisionsArg) {
  const task = await journeyTask(submissionId);
  const token = await playerIdToken(reviewerUid);
  if (decisionsArg === "silent") {
    const response = await callable(SUBMIT_REVIEW_PATH, {
      assignmentId: task.assignmentId, lessonId: task.lessonId, kind: "TRANSLATION_REVIEW",
      language, segmentResults: [{questionId: task.questionIds[0], segmentKey: "text", accepted: true}],
    }, token);
    assert.equal(response.status, 400, "the silent accepted boolean must be refused by the wire");
    assert.match(JSON.stringify(response.json), /explicit decision/, "the refusal must name the explicit decision");
    console.log(`SEGMENTS-SILENT REFUSED (explicit decision required): ${JSON.stringify(response.json)}`);
    return;
  }
  const decisions = new Map(decisionsArg.split(",").map((entry) => {
    const [questionId, segmentKey, decision] = entry.split(":");
    return [`${questionId}|${segmentKey}`, decision];
  }));
  const segmentResults = task.questionIds.flatMap((questionId) =>
    ["text"].map((segmentKey) => ({
      questionId,
      segmentKey,
      decision: decisions.get(`${questionId}|${segmentKey}`),
    })),
  );
  assert.ok(segmentResults.every((segment) => segment.decision === "correct" || segment.decision === "error"),
    "every segment needs an explicit correct/error decision");
  const response = await callable(SUBMIT_REVIEW_PATH, {
    assignmentId: task.assignmentId, lessonId: task.lessonId, kind: "TRANSLATION_REVIEW",
    language, segmentResults, questionScores: [],
  }, token);
  assert.equal(response.status, 200, `TRANSLATION_REVIEW must be accepted: ${JSON.stringify(response.json)}`);
  console.log(`SEGMENTS OK -> ${JSON.stringify(response.json.result.aggregate)}`);
}

async function catalog() {
  const token = await playerIdToken("cbynDghgyOf5exjIrEH57oeOjPzf");
  const response = await callable(CATALOG_PATH, {pageSize: 50}, token);
  assert.equal(response.status, 200, "catalog must answer");
  const result = response.json.result;
  console.log(JSON.stringify({
    totalEligibleLessons: result.totalEligibleLessons,
    snapshotFingerprint: result.snapshotFingerprint,
    lessons: result.lessons.map((l) => ({lessonId: l.lessonId, contentVersion: l.contentVersion,
      title: l.title, priceNolics: l.priceNolics, authorUid: l.authorUid})),
  }, null, 2));
}

// Кураторский переход реальным callable (setLessonPlacement). Ключ мутации — намерение
// вызывающего; повтор ключа отвечает сохранённым решением сервера (AD-2).
async function transition(lessonId, curatorUid, targetShelf, reason, mutationId) {
  assert.ok(/^[A-Za-z0-9]{20,128}$/.test(curatorUid), "curator uid must look like a firebase uid");
  assert.ok(/^[A-Za-z0-9-]{6,128}$/.test(mutationId), "mutationId must be a key-shaped string");
  const token = await playerIdToken(curatorUid);
  const response = await callable(`/${PROJECT}/us-central1/setLessonPlacement`,
    {lessonId, targetShelf, reason, mutationId}, token);
  console.log(JSON.stringify({httpStatus: response.status, body: response.json}, null, 2));
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  const {writeFileSync, readFileSync} = require("node:fs");
  if (cmd === "snapshot") {
    const data = await snapshot();
    writeFileSync(args[0], JSON.stringify(data));
    console.log(`SNAPSHOT OK ${args[0]} (${Object.keys(data.collections).length} groups)`);
  } else if (cmd === "compare") {
    const before = JSON.parse(readFileSync(args[0], "utf8"));
    const after = JSON.parse(readFileSync(args[1], "utf8"));
    const report = compare(before, after);
    console.log(JSON.stringify(report, null, 2));
  } else if (cmd === "bootstrap-curator") {
    await bootstrapCurator(args[0]);
  } else if (cmd === "review") {
    await review(args[0], args[1], Number(args[2]));
  } else if (cmd === "review-questions") {
    await reviewQuestions(args[0], args[1], args[2]);
  } else if (cmd === "author-outcomes") {
    await authorOutcomes(args[0], args[1]);
  } else if (cmd === "translate") {
    await translate(args[0], args[1], args[2], args[3]);
  } else if (cmd === "review-segments") {
    await reviewSegments(args[0], args[1], args[2], args[3]);
  } else if (cmd === "status") {
    await statusOf(args[0]);
  } else if (cmd === "catalog") {
    await catalog();
  } else if (cmd === "transition") {
    await transition(args[0], args[1], args[2], args[3], args[4]);
  } else {
    throw new Error(`unknown command: ${cmd}`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("FAILED:", error.message);
    process.exit(1);
  });
