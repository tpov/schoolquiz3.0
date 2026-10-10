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
  const response = await fetch(urlPath, {
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
  const task = tasks.docs[0];
  const assignmentId = task.id;
  const lessonId = (task.data() || {}).lessonId;
  assert.equal(typeof lessonId, "string", "task must name its lesson");
  const token = await playerIdToken(reviewerUid);

  const testing = await callable(SUBMIT_REVIEW_PATH,
    {assignmentId, lessonId, kind: "TESTING", score, language: null}, token);
  assert.equal(testing.status, 200, `TESTING review must be accepted: ${JSON.stringify(testing.json)}`);
  assert.equal(testing.json.result.published, false, "TESTING alone must not publish");
  console.log(`REVIEW TESTING score=${score} -> aggregate ${JSON.stringify(testing.json.result.aggregate)}`);

  const logic = await callable(SUBMIT_REVIEW_PATH,
    {assignmentId, lessonId, kind: "LOGIC", score, language: null}, token);
  assert.equal(logic.status, 200, `LOGIC review must be accepted: ${JSON.stringify(logic.json)}`);
  console.log(`REVIEW LOGIC score=${score} -> aggregate ${JSON.stringify(logic.json.result.aggregate)}`);
  assert.equal(logic.json.result.published, true, "LOGIC passing the threshold must publish the submission");
  console.log(`REVIEW OK submission ${submissionId} published=${logic.json.result.published}`);
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
  } else if (cmd === "status") {
    await statusOf(args[0]);
  } else if (cmd === "catalog") {
    await catalog();
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
