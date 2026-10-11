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
//   snapshot <file>                       — дамп дерева в JSON (форма 00-before-tree + inbox-поверхности)
//   compare <before> <after>              — побайтовый диф двух снапшотов
//   inbox-as <uid> <file>                 — listInboxItems живьём (полный ответ view)
//   read-state <file> <lessonId>          — плейсмент/отчёты/комментарии/заявка/задание урока
//   seed-comment <uid> <nickname> <lessonId> <text> — комментарий через Firestore REST под
//                                            idToken uid (правила: any signed-in, allowlist полей)
//   replay-as <uid> <reportId> <lessonId> <text> — submitMutation прежним ключом отчёта
//   bootstrap-reviewer <uid>              — profiles/{uid}.adminLevel = 100 (demo-фикстура роли,
//                                            прецедент bootstrap-curator тула tz06c)
//   seed-lesson <prefix> <authorUid> <title> — мини-цепочка урока под inbox-journey (каталог/
//                                            quest/section/theme/lesson + 2 вопроса + placement
//                                            home), прецедент seedEasyBank сеялки 3.3
//   seed-review-task <submissionId> <lessonId> <ownerUid> — открытое задание UNDER_REVIEW (checks
//                                            пусты) + вопросная подколлекция + запись журнала
//                                            ревью; форму держат readAdminReviewLessonTasksByIds
//                                            и adminLessonSnapshotToTask приёмника

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

// Перечень 00-before-tree 4.8 (публичные корни, голоса рейтингом, рекурсивные приватные деревья)
// расширен поверхностями самой истории 4.9: приватные отчёты, публичные комментарии, ключи
// мутаций (идемпотентность приёмника), вопросы, журнал ревью, конфиги и каталоги цепочек.
// Сравнение с чужим снапшотом легально: compare берёт объединение label-ов.
const ROOT_COLLECTIONS = [
  "quests", "sections", "themes", "lessons", "lesson_placements", "lesson_ratings",
  "quest_review_requests", "admin/review/lessons", "catalogs", "questions",
  "lesson_error_reports", "lessonComments", "mutation_keys", "configs",
  "admin/review/sync_changes",
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

async function assertDocAbsent(docPath) {
  const snapshot = await db.doc(docPath).get();
  assert.ok(!snapshot.exists, `fixture path already taken: ${docPath}`);
}

function questionPayload(questionId, lessonId, order) {
  return JSON.stringify({
    type: "SingleChoice",
    text: `Inbox journey question ${order} of ${lessonId}`,
    imageUrl: null,
    options: [
      {id: "o1", text: `Correct answer of question ${order}`},
      {id: "o2", text: `Wrong answer of question ${order}`},
    ],
    correctOptionId: "o1",
    info: "Journey fixture question",
    difficulty: "HARD",
    id: questionId,
  });
}

async function seedLesson(prefix, authorUid, title) {
  assert.ok(/^[a-z0-9]{3,20}$/.test(prefix), "prefix must be a short lowercase id");
  assert.ok(/^[A-Za-z0-9_-]{1,128}$/.test(authorUid), "authorUid must look like a uid");
  const ids = {
    catalog: `${prefix}_catalog`, quest: `${prefix}_quest`, section: `${prefix}_section`,
    theme: `${prefix}_theme`, lesson: `${prefix}_lesson`,
  };
  for (const path of [`catalogs/${ids.catalog}`, `quests/${ids.quest}`, `sections/${ids.section}`,
    `themes/${ids.theme}`, `lessons/${ids.lesson}`, `lesson_placements/${ids.lesson}`]) {
    await assertDocAbsent(path);
  }
  const now = Date.now();
  await db.doc(`catalogs/${ids.catalog}`).set({questType: "REGULAR", version: 1});
  await db.doc(`quests/${ids.quest}`).set({catalogId: ids.catalog, authorUid, version: 1});
  await db.doc(`sections/${ids.section}`).set({questId: ids.quest, version: 1});
  await db.doc(`themes/${ids.theme}`).set({sectionId: ids.section, version: 1});
  // Полная форма placement (lessonId/questType/updatedAtMs) — по прецеденту сеялки 3.3: частичная
  // форма роняет lifecycle-вью, а inbox адресат читает authorUid именно отсюда.
  await db.doc(`lesson_placements/${ids.lesson}`).set({
    lessonId: ids.lesson, questType: "REGULAR", updatedAtMs: now,
    shelf: "home", previousShelf: "review", authorUid, contentVersion: 1, placementVersion: 1,
  });
  await db.doc(`lessons/${ids.lesson}`).set({
    themeId: ids.theme, title, version: 1, reviewApprovedAtMs: now, publishedAtMs: now,
  });
  for (const order of [0, 1]) {
    const questionId = `q_${prefix}_${order}`;
    const payload = questionPayload(questionId, ids.lesson, order);
    await db.doc(`questions/${questionId}`).set({
      version: 1, archived: false, id: questionId, lessonId: ids.lesson,
      text: `Inbox journey question ${order}`, difficulty: "HARD", language: "en", languageLevel: 0,
      order, payload,
    });
  }
  console.log(`SEED-LESSON OK ${ids.lesson} author=${authorUid} title="${title}"`);
  return ids;
}

async function seedReviewTask(submissionId, lessonId, ownerUid) {
  assert.ok(/^[A-Za-z0-9_-]{1,128}$/.test(submissionId), "submissionId must be a doc-safe id");
  const prefix = lessonId.replace(/_lesson$/, "");
  const assignmentId = `${submissionId}_${lessonId}`;
  const taskPath = `admin/review/lessons/${lessonId}`;
  const journalPath = `admin/review/sync_changes/${assignmentId}`;
  await assertDocAbsent(taskPath);
  await assertDocAbsent(journalPath);
  const lesson = await db.doc(`lessons/${lessonId}`).get();
  assert.ok(lesson.exists, `lesson must exist: ${lessonId}`);
  const placement = await db.doc(`lesson_placements/${lessonId}`).get();
  assert.ok(placement.exists, `lesson must stay placed for the report addressee: ${lessonId}`);
  assert.equal(placement.data().authorUid, ownerUid, "task ownerUid must match the placement author");
  // checks пусты: openTasksFor держит TESTING открытым, adminLevel 100 даёт его ревьюеру.
  const now = Date.now();
  await db.doc(taskPath).set({
    assignmentId, submissionId, ownerUid, lessonId,
    catalogId: `${prefix}_catalog`, draftId: `${prefix}_quest`, questId: `${prefix}_quest`,
    title: lesson.data().title, createdAtMs: now, changedAtMs: now, status: "UNDER_REVIEW",
    sourceLanguages: ["en"], checks: {},
  });
  for (const order of [0, 1]) {
    const publicQuestion = await db.doc(`questions/q_${prefix}_${order}`).get();
    assert.ok(publicQuestion.exists, `public question must exist: q_${prefix}_${order}`);
    await db.doc(taskPath).collection("quests").doc(`${prefix}_quest`)
      .collection("questions").doc(publicQuestion.id)
      .set({...publicQuestion.data(), draftId: submissionId, updatedAtMs: now});
  }
  // Тело записи журнала — ровно три поля (AD-11); id документа = id задания как есть.
  await db.doc(journalPath).set({type: "review_assignment", id: assignmentId, changedAtMs: now});
  console.log(`SEED-TASK OK assignmentId=${assignmentId} owner=${ownerUid}`);
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
  } else if (cmd === "bootstrap-reviewer") {
    const [uid] = args;
    assert.ok(/^[A-Za-z0-9_-]{20,128}$|^User[A-Za-z0-9]{6,}$/.test(uid), "uid must look like a firebase uid");
    await db.doc(`profiles/${uid}`).set({adminLevel: 100, testerLevel: 0}, {merge: true});
    const doc = await db.doc(`profiles/${uid}`).get();
    assert.equal(doc.data().adminLevel, 100, "adminLevel must be 100 after bootstrap");
    console.log(`BOOTSTRAP-REVIEWER OK profiles/${uid}.adminLevel=100`);
  } else if (cmd === "seed-lesson") {
    const [prefix, authorUid, ...titleParts] = args;
    const title = titleParts.join(" ");
    assert.ok(title.trim().length > 0, "lesson title is required");
    await seedLesson(prefix, authorUid, title.trim());
  } else if (cmd === "seed-review-task") {
    const [submissionId, lessonId, ownerUid] = args;
    await seedReviewTask(submissionId, lessonId, ownerUid);
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
