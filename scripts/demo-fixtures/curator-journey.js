"use strict";

// Инструмент journey 4.8 curator-transitions (epic-authoring-review) против demo-backend:
// снапшоты дерева для оракула «только преднамеренные касания», фиксация плейсментов/историй,
// замороженная попытка-фикстура, живой rating реальным callable и probe-отказ не-куратора.
// Кураторские переходы (валидные) в этой истории идут из UI рига — тул их не подменяет;
// scripted-переход здесь только как отрицательное доказательство (CURATOR_REQUIRED).
// Запуск при поднятых эмуляторах:
//   GCLOUD_PROJECT=demo-schoolquiz-mvp FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 \
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 node scripts/demo-fixtures/curator-journey.js <cmd> …
// Команды:
//   snapshot <file>                        — дамп дерева в JSON (форма 00-before-tree)
//   compare <before> <after>               — побайтовый диф двух снапшотов
//   capture <file> <lessonId>…             — плейсмент+история+урок+рейтинг названных уроков
//   catalog <playerUid> <file>             — listArenaLessons живьём (что видит игрок)
//   seed-frozen-attempt <lessonId> <tag>   — SETTLED server_results-фикстура (конвенция tz06d)
//                                            + auth-игрок; печатает {uid, attemptId}
//   rate <lessonId> <attemptId> <1..3> <uid> <mutationId> — rateArenaLesson живьём
//   transition-as <uid> <lessonId> <shelf> <reason> <mutationId> — setLessonPlacement живьём
//   history-view <curatorUid> <lessonId> <file> — listLessonLifecycleHistory живьём (видимый reason)

const assert = require("node:assert/strict");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const CATALOG_PATH = `/${PROJECT}/us-central1/listArenaLessons`;
const RATE_PATH = `/${PROJECT}/us-central1/rateArenaLesson`;
const TRANSITION_PATH = `/${PROJECT}/us-central1/setLessonPlacement`;
const HISTORY_PATH = `/${PROJECT}/us-central1/listLessonLifecycleHistory`;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Тот же перечень, что у снапшота 2026-10-10 (00-before-tree этой истории): публичные корни,
// голоса рейтингом и рекурсивные приватные деревья. Labels совпадают — compare байт-в-байт.
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

async function capture(lessonIds) {
  const out = {lessons: {}};
  for (const lessonId of lessonIds) {
    const placement = await db.doc(`lesson_placements/${lessonId}`).get();
    const lesson = await db.doc(`lessons/${lessonId}`).get();
    const rating = await db.doc(`lesson_ratings/${lessonId}`).get();
    const history = await db.collection(`lesson_placements/${lessonId}/history`).get();
    out.lessons[lessonId] = {
      placement: placement.exists ? placement.data() : null,
      lesson: lesson.exists ? lesson.data() : null,
      rating: rating.exists ? rating.data() : null,
      history: history.docs.map((doc) => ({id: doc.id, ...doc.data()})),
    };
  }
  return out;
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  if (cmd === "snapshot") {
    const data = await snapshot();
    require("node:fs").writeFileSync(args[0], JSON.stringify(data, null, 1));
    console.log(`SNAPSHOT OK ${args[0]} takenAtMs=${data.takenAtMs}`);
  } else if (cmd === "compare") {
    const before = JSON.parse(require("node:fs").readFileSync(args[0], "utf8"));
    const after = JSON.parse(require("node:fs").readFileSync(args[1], "utf8"));
    const report = {added: [], removed: [], changed: []};
    const labels = new Set([...Object.keys(before.collections), ...Object.keys(after.collections)]);
    for (const label of labels) diffValues(before.collections[label], after.collections[label], label, report);
    console.log(JSON.stringify(report, null, 1));
  } else if (cmd === "capture") {
    const data = await capture(args.slice(1));
    require("node:fs").writeFileSync(args[0], JSON.stringify(data, null, 1));
    console.log(`CAPTURE OK ${args[0]} lessons=${args.length - 1}`);
  } else if (cmd === "catalog") {
    const token = await playerIdToken(args[0]);
    const rows = [];
    let cursor;
    for (;;) {
      const payload = {pageSize: 50, ...(cursor ? {cursor} : {})};
      const reply = await callable(CATALOG_PATH, payload, token);
      assert.equal(reply.status, 200, `listArenaLessons failed: ${JSON.stringify(reply.json)}`);
      const result = reply.json.result || {};
      rows.push(...(result.lessons || []));
      if (!result.nextCursor) break;
      cursor = result.nextCursor;
    }
    require("node:fs").writeFileSync(args[1], JSON.stringify({count: rows.length,
      lessons: rows.map((row) => ({lessonId: row.lessonId, contentVersion: row.contentVersion,
        placement: row.placement, rating: row.rating}))}, null, 1));
    console.log(`CATALOG OK ${args[1]} count=${rows.length}`);
  } else if (cmd === "seed-frozen-attempt") {
    // Конвенция tz06d (SETTLED-фикстура попытки), но по адресу, который читает живой
    // loadCompletion: result_events_public/{bucket}/events/{safeDocId} с settlementReceipt.
    // Попытка — фикстура; rating/retire поверх неё идут реальными callable/UI.
    const [lessonId, tag] = args;
    const uid = `cur48${tag}player0000000`.slice(0, 28);
    const attemptId = `cur48_${tag}_attempt_${lessonId}`.slice(0, 120);
    await admin.auth().createUser({uid, displayName: `Cur48${tag}`}).catch((error) => {
      assert.equal(error.code, "auth/uid-already-exists", "unexpected auth error");
    });
    const bucket = require("node:crypto").createHash("sha256").update(attemptId).digest("hex").slice(0, 2);
    const cleanId = attemptId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80);
    const docId = require("node:crypto").createHash("sha256").update(attemptId).digest("hex").slice(0, 12) + "_" + cleanId;
    const settledAtMs = Date.now();
    const event = {
      userId: uid, attemptId, lessonId, lessonVersion: 1, activityKind: "REGULAR",
      codeAnswer: "8", percentScore: 87, completed: true, scope: "public",
      settlementReceipt: {
        schemaVersion: 1,
        receiptId: `attempt:${require("node:crypto").createHash("sha256")
          .update(JSON.stringify([uid, attemptId])).digest("hex")}`,
        attemptId, uid, lessonId, lessonVersion: 1, status: "SETTLED", completed: true,
        settledAtMs, codeAnswer: "8", percentScore: 87,
        lifePointsSpent: 0, plasmaPointsSpent: 0, reward: {skillPoints: 0, nolics: 0},
      },
      syntheticCuratorRun: "cur48",
    };
    await db.collection("result_events_public").doc(bucket).collection("events").doc(docId).create(event);
    console.log(`SEED-FROZEN-ATTEMPT OK ${JSON.stringify({uid, attemptId, eventPath: `result_events_public/${bucket}/events/${docId}`})}`);
  } else if (cmd === "rate") {
    const [lessonId, attemptId, rating, uid, mutationId] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(RATE_PATH, {lessonId, attemptId, rating: Number(rating), mutationId}, token);
    console.log(JSON.stringify(reply, null, 1));
    assert.equal(reply.status, 200, `rateArenaLesson failed: ${JSON.stringify(reply.json)}`);
  } else if (cmd === "transition-as") {
    const [uid, lessonId, shelf, reason, mutationId] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(TRANSITION_PATH, {lessonId, targetShelf: shelf, reason, mutationId}, token);
    console.log(JSON.stringify(reply, null, 1));
  } else if (cmd === "history-view") {
    const [uid, lessonId, file] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(HISTORY_PATH, {lessonId}, token);
    assert.equal(reply.status, 200, `listLessonLifecycleHistory failed: ${JSON.stringify(reply.json)}`);
    require("node:fs").writeFileSync(file, JSON.stringify(reply.json.result, null, 1));
    console.log(`HISTORY-VIEW OK ${file}`);
  } else {
    throw new Error(`unknown command: ${cmd}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
