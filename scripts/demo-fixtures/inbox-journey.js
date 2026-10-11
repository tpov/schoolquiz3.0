"use strict";

// Инструмент journey 4.9 inbox-private-error-reports (epic-authoring-review) против demo-backend:
// снапшоты дерева для оракула «только преднамеренные касания», живой списокInboxItems от имени
// named-uid (вид автора и вид не-автора), rules-легальная публикация комментария-фикстуры
// (публичная поверхность, any signed-in user) и replay-проба отчёта прежним ключом мутации.
// Сам приватный отчёт в этой истории идёт из UI рига — тул его не подменяет; scripted-путь
// здесь только для повторного чтения того, что уже записано (replay возвращает сохранённое).
// Запуск при поднятых эмуляторах:
//   GCLOUD_PROJECT=demo-schoolquiz-mvp FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 \
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 node scripts/demo-fixtures/inbox-journey.js <cmd> …
// Команды:
//   snapshot <file>                       — дамп дерева в JSON (форма 00-before-tree)
//   compare <before> <after>              — побайтовый диф двух снапшотов
//   inbox-as <uid> <file>                 — listInboxItems живьём (полный ответ view)
//   read-state <file> <lessonId>          — плейсмент/отчёты/комментарии/заявка/задание урока
//   seed-comment <uid> <nickname> <lessonId> <text> — комментарий через Firestore REST под
//                                            idToken uid (правила: any signed-in, allowlist полей)
//   replay-as <uid> <reportId> <lessonId> <text> — submitMutation прежним ключом отчёта

const assert = require("node:assert/strict");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const INBOX_PATH = `/${PROJECT}/us-central1/listInboxItems`;
const MUTATION_PATH = `/${PROJECT}/us-central1/submitMutation`;
const SUBMIT_ERROR_OPERATION = "lesson_report.SUBMIT_ERROR";

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Тот же перечень, что у снапшота 2026-10-10 (00-before-tree 4.8): публичные корни,
// голоса рейтингом и рекурсивные приватные деревья. Labels совпадают — compare байт-в-байт
// с тула curator-journey (эпик-4 оракулы сравнимы между сменами).
const ROOT_COLLECTIONS = [
  "quests", "sections", "themes", "lessons", "lesson_placements", "lesson_ratings",
  "quest_review_requests", "admin/review/lessons",
];

async function dumpCollection(prefix) {
  const out = {};
  const snap = await db.collection(prefix).get();
  for (const doc of snap.docs) out[doc.id] = doc.data();
  return out;
}

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
  for (const prefix of ROOT_COLLECTIONS) {
    out.collections[prefix] = await dumpCollection(prefix);
  }
  const votes = {};
  for (const parent of await db.collection("lesson_ratings").listDocuments()) {
    Object.assign(votes, await dumpCollection(`lesson_ratings/${parent.id}/votes`));
  }
  out.collections["lesson_ratings/votes"] = votes;
  Object.assign(out.collections, await dumpPrivate());
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
    if (JSON.stringify(before[key]) !== JSON.stringify(after[key])) report.changed.push(`${label}/${key}`);
  }
}

function writeJson(file, value) {
  require("node:fs").writeFileSync(file, JSON.stringify(value, null, 1));
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
  return {status: response.status, json: await response.json()};
}

async function readState(lessonId, submissionId) {
  const placement = await db.doc(`lesson_placements/${lessonId}`).get();
  const lesson = await db.doc(`lessons/${lessonId}`).get();
  const reports = await db.collection("lesson_error_reports")
    .where("lessonId", "==", lessonId).get();
  const comments = await db.collection("lessonComments")
    .where("lessonId", "==", lessonId).get();
  const request = await db.doc(`quest_review_requests/${submissionId}`).get();
  const requestRaw = request.data() || {};
  return {
    lessonId,
    submissionId,
    placement: placement.exists ? placement.data() : null,
    lessonTitle: lesson.exists ? (lesson.data() || {}).title : null,
    reports: reports.docs.map((doc) => ({id: doc.id, ...doc.data()})),
    comments: comments.docs.map((doc) => ({id: doc.id, ...doc.data()})),
    request: request.exists ? {
      status: requestRaw.status, ownerUid: requestRaw.ownerUid, draftId: requestRaw.draftId,
      publishedAtMs: requestRaw.publishedAtMs, processedAtMs: requestRaw.processedAtMs,
    } : null,
  };
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  if (cmd === "snapshot") {
    const data = await snapshot();
    writeJson(args[0], data);
    console.log(`SNAPSHOT OK ${args[0]} takenAtMs=${data.takenAtMs}`);
  } else if (cmd === "compare") {
    const before = JSON.parse(require("node:fs").readFileSync(args[0], "utf8"));
    const after = JSON.parse(require("node:fs").readFileSync(args[1], "utf8"));
    const report = {added: [], removed: [], changed: []};
    const labels = new Set([...Object.keys(before.collections), ...Object.keys(after.collections)]);
    for (const label of labels) diffValues(before.collections[label], after.collections[label], label, report);
    console.log(JSON.stringify(report, null, 1));
  } else if (cmd === "inbox-as") {
    const [uid, file] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(INBOX_PATH, {pageSize: 20}, token);
    writeJson(file, {uid, status: reply.status, reply: reply.json});
    const entries = (reply.json.result || {}).entries || [];
    console.log(`INBOX OK ${file} uid=${uid} status=${reply.status} entries=${entries.length}` +
      ` kinds=${JSON.stringify(entries.map((e) => e.kind))}`);
  } else if (cmd === "read-state") {
    const [file, lessonId, submissionId] = args;
    const data = await readState(lessonId, submissionId);
    writeJson(file, data);
    console.log(`STATE OK ${file} reports=${data.reports.length} comments=${data.comments.length}` +
      ` placement=${data.placement ? data.placement.shelf : "none"}`);
  } else if (cmd === "seed-comment") {
    const [uid, nickname, lessonId, text] = args;
    const token = await playerIdToken(uid);
    // Правила lessonComments: create для любого вошедшего, поля allowlist-ом, authorUid == uid.
    const response = await fetch(
      `http://127.0.0.1:${HOSTS.firestore}/v1/projects/${PROJECT}/databases/(default)/documents/lessonComments`,
      {method: "POST", headers: {"content-type": "application/json", authorization: `Bearer ${token}`},
        body: JSON.stringify({fields: {
          lessonId: {stringValue: lessonId},
          authorUid: {stringValue: uid},
          authorNickname: {stringValue: nickname},
          text: {stringValue: text},
          createdAt: {integerValue: String(Date.now())},
        }})},
    );
    const json = await response.json();
    assert.equal(response.status, 200, `comment create failed: ${JSON.stringify(json)}`);
    const docId = json.name.split("/").pop();
    console.log(`COMMENT OK docId=${docId} lesson=${lessonId} uid=${uid}`);
  } else if (cmd === "replay-as") {
    const [uid, reportId, lessonId, text] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(MUTATION_PATH, {
      mutationId: `lesson_report-SUBMIT_ERROR-${reportId}`,
      operation: SUBMIT_ERROR_OPERATION,
      payload: JSON.stringify({reportId, lessonId, text}),
    }, token);
    writeJson(`replay-${reportId}.json`, {uid, status: reply.status, reply: reply.json});
    console.log(`REPLAY status=${reply.status} reply=${JSON.stringify(reply.json)}`);
  } else {
    throw new Error(`unknown command: ${cmd}`);
  }
}

main().then(
  () => process.exit(0),
  (error) => {
    console.error(error);
    process.exit(1);
  },
);
