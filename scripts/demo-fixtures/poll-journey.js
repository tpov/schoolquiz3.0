"use strict";

// Инструмент journey 8.2 poll-series-run (epic-social-features) против demo-backend:
// снапшоты дерева для оракула «только преднамеренные касания», живые listPollSeries /
// getPollQuestionState от имени named-uid (вид проголосовавшего и вид молчунa), голос
// scripted-участника через настоящий submitMutation на functions-эмуляторе хоста и replay
// прежним ключом мутации. Сам голос риг-игрока в этой истории идёт из UI рига — тул его
// не подменяет; scripted-путь здесь для второго/третьего участника (знаменатель §5) и для
// повторного чтения того, что уже записано.
// Запуск при поднятых эмуляторах:
//   GCLOUD_PROJECT=demo-schoolquiz-mvp FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 \
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 node scripts/demo-fixtures/poll-journey.js <cmd> …
// Команды:
//   snapshot <file>                       — дамп дерева в JSON (форма 00-before-tree + poll-поверхности)
//   compare <before> <after>              — побайтовый диф двух снапшотов
//   read-polls <file>                     — poll_series + poll_votes + mutation_keys голосов
//   view-as <uid> <file>                  — listPollSeries живьём (полный ответ view)
//   question-as <uid> <seriesId> <qidx> <file> — getPollQuestionState живьём
//   vote-as <uid> <keySuffix> <seriesId> <rev> <qidx> <optA,optB,…> [outfile] — submitMutation голоса
//   replay-as <uid> <keySuffix> <seriesId> <rev> <qidx> <optA,optB,…> [outfile] — тот же ключ повторно

const assert = require("node:assert/strict");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const LIST_PATH = `/${PROJECT}/us-central1/listPollSeries`;
const STATE_PATH = `/${PROJECT}/us-central1/getPollQuestionState`;
const MUTATION_PATH = `/${PROJECT}/us-central1/submitMutation`;
const VOTE_OPERATION = "polls.SUBMIT_VOTE";

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `127.0.0.1:${HOSTS.authPort}`, "auth must be the demo auth emulator");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..");
const admin = require(path.join(repoRoot, "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Перечень 00-before-tree 4.8 (публичные корни, голоса рейтингом, рекурсивные приватные деревья)
// с poll-поверхностями самой истории 8.2: серии, голоса, ключи мутаций (идемпотентность).
const ROOT_COLLECTIONS = [
  "quests", "sections", "themes", "lessons", "lesson_placements", "lesson_ratings",
  "quest_review_requests", "admin/review/lessons", "catalogs", "questions",
  "lesson_error_reports", "lessonComments", "mutation_keys", "configs",
  "admin/review/sync_changes", "poll_series", "poll_votes",
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

function voteKey(seriesId, revisionId, questionIndex, suffix) {
  const sourceId = `series-${seriesId}-r${revisionId}-q${questionIndex}`;
  return `polls-SUBMIT_VOTE-${sourceId}${suffix ? `-${suffix}` : ""}`;
}

async function readPolls() {
  const [series, votes] = await Promise.all([
    dumpCollection("poll_series"),
    dumpCollection("poll_votes"),
  ]);
  const keys = {};
  const snap = await db.collection("mutation_keys").get();
  for (const doc of snap.docs) {
    if (doc.id.includes("polls-")) keys[doc.id] = doc.data();
  }
  return {series, votes, voteMutationKeys: keys};
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  if (cmd === "snapshot") {
    const data = await snapshot();
    writeJson(args[0], data);
    console.log(`SNAPSHOT OK ${args[0]}`);
  } else if (cmd === "compare") {
    const before = JSON.parse(require("node:fs").readFileSync(args[0], "utf8"));
    const after = JSON.parse(require("node:fs").readFileSync(args[1], "utf8"));
    const report = {added: [], removed: [], changed: []};
    const labels = new Set([...Object.keys(before.collections), ...Object.keys(after.collections)]);
    for (const label of labels) {
      diffValues(before.collections[label], after.collections[label], label, report);
    }
    writeJson(args[2], report);
    console.log(`COMPARE OK added=${report.added.length} removed=${report.removed.length} changed=${report.changed.length}`);
    for (const line of [...report.added, ...report.removed.map((x) => `-${x}`), ...report.changed.map((x) => `~${x}`)]) {
      console.log(`  ${line}`);
    }
  } else if (cmd === "read-polls") {
    writeJson(args[0], await readPolls());
    console.log(`READ-POLLS OK ${args[0]}`);
  } else if (cmd === "view-as") {
    const [uid, file] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(LIST_PATH, {}, token);
    assert.equal(reply.status, 200, `listPollSeries must answer 200 for ${uid}: ${JSON.stringify(reply.json)}`);
    writeJson(file, reply.json);
    console.log(`VIEW-AS OK ${uid} entries=${(reply.json.result && reply.json.result.entries || []).length}`);
  } else if (cmd === "question-as") {
    const [uid, seriesId, questionIndex, file] = args;
    const token = await playerIdToken(uid);
    const reply = await callable(STATE_PATH, {seriesId, questionIndex: Number(questionIndex)}, token);
    assert.equal(reply.status, 200, `getPollQuestionState must answer 200 for ${uid}: ${JSON.stringify(reply.json)}`);
    writeJson(file, reply.json);
    console.log(`QUESTION-AS OK ${uid} ownVote=${reply.json.result && reply.json.result.ownVote ? "yes" : "no"} distribution=${reply.json.result && reply.json.result.distribution ? "yes" : "hidden"}`);
  } else if (cmd === "vote-as" || cmd === "replay-as") {
    const [uid, keySuffix, seriesId, revisionId, questionIndex, options, outfile] = args;
    const token = await playerIdToken(uid);
    const mutationId = voteKey(seriesId, revisionId, questionIndex, keySuffix === "-" ? "" : keySuffix);
    const payload = JSON.stringify({
      seriesId,
      revisionId: Number(revisionId),
      questionIndex: Number(questionIndex),
      selectedOptionIds: options.split(","),
      confirmedAtMs: Date.now(),
    });
    const reply = await callable(MUTATION_PATH, {mutationId, operation: VOTE_OPERATION, payload}, token);
    if (outfile) writeJson(outfile, reply);
    console.log(`${cmd.toUpperCase()} OK ${uid} key=${mutationId} status=${reply.status} replayed=${reply.json.result && reply.json.replayed}`);
    console.log(JSON.stringify(reply.json.result || reply.json, null, 1));
  } else {
    throw new Error(`unknown command: ${cmd}`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("FAIL:", error && error.message);
    process.exit(1);
  });
