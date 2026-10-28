"use strict";

// Device Farm wave (2026-10-29): операционный драйвер рига для четырёх hitl-джорни
// (9.9 / 4.26 / 4.20-partial / 5.13). Запускается НА VPS внутри контейнера
// schoolquiz-functions-emu (у него firebase-admin в /repo/functions/node_modules и
// эмуляторы на 127.0.0.1:18090/19090):
//
//   docker exec schoolquiz-functions-emu node /repo/scripts/devicefarm-rig-prep.js <cmd> ...
//
// Команды (все пишут только в демо-проект, чужие данные не трогают, свои фикстуры
// помечены testFixture и перед пересозданием удаляются только по своей метке):
//
//   await-users <expected> [maxWaitSec]  — ждать появления НОВЫХ users-документов
//       (снимок базовой линии делается на старте); на каждого нового uid печатает
//       одну JSON-строку {"event":"user","uid":...} и наращивает счётчик; выходит,
//       когда увидено <expected> новых или истёк таймаут (exit 2).
//   topup <uid> <nolics> [boxCount]      — поднять кошелёк аккаунта до величин
//       (only-increase: существующий больший баланс не уменьшается); печатает before/after.
//   seed-author-lesson <authorUid>       — опубликованный домашний квест ФИКСИРОВАННЫХ
//       названий ("DF author course/section/theme/lesson", 2 вопроса SINGLE) под
//       авторством <authorUid> — цель для трёх входов предложений 4.26; идемпотентен
//       с пересозданием (удаляются только документы с меткой df426-devicefarm).
//   setup-513 <deviceUid>                — приборка 5.13: кошелёк устройства
//       (nolics/boxCount), оппонент-аноним (аналог course-tournament-device-prep.js)
//       и опубликованный курс-источник с ФИКСИРОВАННЫМ id df513-course (80 вопросов,
//       владелец = устройство); печатает uid/token оппонента.
//   await-review-request [maxWaitSec]    — ждать новую заявку quest_review_requests
//       (status UNDER_REVIEW), затем прогнать свипы очереди проверки до терминала;
//       печатает статусы конвейера заявок и предложений.
//   await-proposals <expected> [maxWaitSec] — ждать <expected> новых предложений
//       lessonProposals, затем свипы до терминала; печатает статусы.
//   await-tournament <courseId> [maxWaitSec] — ждать событие course_tournament_events
//       с source.id == <courseId>; печатает {eventId, status, escrow}.
//
// Свипы очереди проверки (4.20/4.26): прогоняется НАСТОЯЩИЙ рабочий модуль
// functions/review-queue-worker.js — тот же код, что обязан крутить отдельный процесс
// на VPS (AD-32: «отдельный процесс, не триггер функции»). leaseMs уменьшен до
// секунды, чтобы терминал достигался за минуты прогона, а не за полчаса; сами
// вердикты/отказы код пишет сам. Модельный клиент честный: без REVIEW_MODEL_* в env
// воркер получает ровно тот отказ, который даёт readModelConfig
// (REVIEW_MODEL_ENV_MISSING) — этапы честно выжигают попытки и уходят в DELAYED.
// Живые «Слова»/«Факты» без ключа невозможны — это хвост владельца.

const assert = require("node:assert/strict");
const {spawn} = require("node:child_process");
const PROJECT = "demo-schoolquiz-mvp";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
const {writeSyncChanges} = require("/repo/functions/sync-changes");
const {completeReviewStages} = require("/repo/functions/review-stages");
const {createReviewQueueWorker} = require("/repo/functions/review-queue-worker");
const {createReviewModelClient, readModelConfig} = require("/repo/functions/review-model-client");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

const log = (record) => process.stdout.write(JSON.stringify(record) + "\n");
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const now = () => Date.now();
const argv = process.argv.slice(2);
const command = argv[0];

function honestModelClient() {
  const config = readModelConfig(process.env);
  if (config.ok) return createReviewModelClient({config: config.config});
  return {
    modelName: "unconfigured",
    judge: async () => ({ok: false, code: "REVIEW_MODEL_ENV_MISSING", message: config.reason}),
  };
}

async function sweepQueue(maxSweeps = 12) {
  const worker = createReviewQueueWorker({
    db, stages: completeReviewStages(), modelClient: honestModelClient(),
    nowMs: now, log: (record) => log({event: "worker", ...record}),
    leaseMs: 900, maxStageCallsPerSweep: 2,
  });
  for (let index = 1; index <= maxSweeps; index++) {
    const result = await worker.sweep();
    log({event: "sweep", index, ...result});
    if (!result.attempted) break;
    await sleep(1200);
  }
}

async function reportQueueState() {
  const requests = await db.collection("quest_review_requests").get();
  for (const doc of requests.docs) {
    const request = doc.data();
    const pipeline = await db.doc(`review_pipeline/${doc.id}`).get();
    const pipelineStatus = pipeline.exists ? (pipeline.data().status ?? "?") : "none";
    log({event: "request", id: doc.id, status: request.status, pipeline: pipelineStatus});
  }
}

async function reportProposals(knownIds) {
  const proposals = await db.collection("lessonProposals").get();
  for (const doc of proposals.docs) {
    if (knownIds && knownIds.has(doc.id)) continue;
    const proposal = doc.data();
    const pipeline = await db.doc(`proposal_pipeline/${doc.id}`).get();
    log({
      event: "proposal", id: doc.id, kind: proposal.kind, status: proposal.status,
      fee: proposal.fee ? proposal.fee.nolics : null,
      outcome: proposal.reviewOutcome ? proposal.reviewOutcome.pipelineStatus : null,
      pipeline: pipeline.exists ? (pipeline.data().status ?? "?") : "none",
    });
  }
}

async function userBaseline() {
  const snapshot = await db.collection("users").get();
  return new Set(snapshot.docs.map((doc) => doc.id));
}

async function cmdAwaitUsers() {
  const expected = Number(argv[1]);
  const maxWaitSec = Number(argv[2] || 900);
  const baseline = await userBaseline();
  log({event: "baseline", users: baseline.size});
  const deadline = now() + maxWaitSec * 1000;
  let seen = 0;
  while (seen < expected && now() < deadline) {
    const snapshot = await db.collection("users").get();
    for (const doc of snapshot.docs) {
      if (baseline.has(doc.id)) continue;
      baseline.add(doc.id);
      seen++;
      log({event: "user", uid: doc.id});
    }
    await sleep(2500);
  }
  if (seen < expected) {
    log({event: "timeout", seen, expected});
    process.exitCode = 2;
  }
}

async function cmdTopup() {
  const [, uid, nolicsArg, boxArg] = argv;
  const nolics = Number(nolicsArg);
  const boxCount = boxArg === undefined ? null : Number(boxArg);
  const ref = db.doc(`users/${uid}`);
  const before = (await ref.get()).data() || {};
  const update = {nolics: Math.max(Number(before.nolics || 0), nolics)};
  if (boxCount !== null) update.boxCount = Math.max(Number(before.boxCount || 0), boxCount);
  await ref.set({...update, testFixture: "df-devicefarm-2026-10-29"}, {merge: true});
  log({event: "topup", uid, before: {nolics: before.nolics || 0, boxCount: before.boxCount || 0}, after: update});
}

const DF426 = Object.freeze({
  marker: "df426-devicefarm",
  catalog: "df426-catalog", quest: "df426-quest", section: "df426-section",
  theme: "df426-theme", lesson: "df426-lesson",
  questions: ["df426-q-0", "df426-q-1"],
});

async function deleteIfOurs(path) {
  const ref = db.doc(path);
  const snapshot = await ref.get();
  if (snapshot.exists && snapshot.data().testFixture === DF426.marker) await ref.delete();
  else if (snapshot.exists) throw new Error(`refuse to overwrite foreign doc ${path}`);
}

async function cmdSeedAuthorLesson() {
  const authorUid = argv[1];
  const at = now();
  for (const path of [`catalogs/${DF426.catalog}`, `quests/${DF426.quest}`, `sections/${DF426.section}`,
    `themes/${DF426.theme}`, `lessons/${DF426.lesson}`, ...DF426.questions.map((id) => `questions/${id}`)]) {
    await deleteIfOurs(path);
  }
  const common = {version: 1, lastModifiedAt: at, archived: false, testFixture: DF426.marker};
  const rows = [
    ["catalog", DF426.catalog, {name: "DF author catalog", questType: "REGULAR", iconNames: []}],
    ["quest", DF426.quest, {catalogId: DF426.catalog, authorUid, title: "DF author course", visibleOn: ["home"]}],
    ["section", DF426.section, {questId: DF426.quest, title: "DF author section", order: 0}],
    ["theme", DF426.theme, {sectionId: DF426.section, title: "DF author theme", order: 0}],
    ["lesson", DF426.lesson, {themeId: DF426.theme, title: "DF author lesson", order: 0}],
    ...DF426.questions.map((id, order) => ["question", id, {lessonId: DF426.lesson, text: SINGLE.text,
      payload: JSON.stringify({...SINGLE, id, difficulty: "EASY"}), difficulty: "EASY",
      language: "en", languageLevel: 0, order}]),
  ];
  await db.runTransaction(async (transaction) => {
    for (const [kind, id, row] of rows) {
      transaction.create(db.doc(`${kind}s/${id}`), {...common, ...row});
      writeSyncChanges(transaction, db, {kind, id, catalogId: DF426.catalog,
        lessonId: DF426.lesson, ownerUid: null}, at);
    }
  });
  log({event: "seeded-426", authorUid, titles: {catalog: "DF author catalog", quest: "DF author course",
    section: "DF author section", theme: "DF author theme", lesson: "DF author lesson"}, ids: DF426});
}

const DF513 = Object.freeze({
  marker: "df513-devicefarm",
  catalog: "df513-catalog", course: "df513-course", section: "df513-section",
  theme: "df513-theme", lesson: "df513-lesson", submission: "df513-submission",
});

async function cmdSetup513() {
  const deviceUid = argv[1];
  const at = now();
  const walletFields = {nolics: 3000, pointsNolics: 3000, standardHearts: 5, goldHearts: 1,
    lifePoints: 500, plasmaPoints: 100, lifePointsUpdatedAtMs: at, plasmaPointsUpdatedAtMs: at};
  const device = (await db.doc(`users/${deviceUid}`).get()).data() || {};
  await db.doc(`users/${deviceUid}`).set({
    nolics: Math.max(Number(device.nolics || 0), 3000),
    boxCount: Math.max(Number(device.boxCount || 0), 100),
    testFixture: DF513.marker,
  }, {merge: true});
  if (!(await db.doc(`profiles/${deviceUid}`).get()).exists) {
    await db.doc(`profiles/${deviceUid}`).create({uid: deviceUid, testFixture: DF513.marker});
  }
  const signup = await fetch("http://127.0.0.1:19090/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo",
    {method: "POST", headers: {"content-type": "application/json"},
      body: JSON.stringify({returnSecureToken: true}), signal: AbortSignal.timeout(15000)});
  assert.equal(signup.ok, true, "auth emulator signUp failed");
  const opponent = await signup.json();
  await db.doc(`users/${opponent.localId}`).create({
    ...walletFields, boxCount: 100, uid: opponent.localId, testFixture: DF513.marker});
  await db.doc(`profiles/${opponent.localId}`).create({uid: opponent.localId, testFixture: DF513.marker});
  for (const path of [`catalogs/${DF513.catalog}`, `quests/${DF513.course}`, `sections/${DF513.section}`,
    `themes/${DF513.theme}`, `lessons/${DF513.lesson}`, `admin/review/lessons/${DF513.lesson}`]) {
    const ref = db.doc(path);
    const snapshot = await ref.get();
    if (snapshot.exists && snapshot.data().testFixture === DF513.marker) await ref.delete();
    else if (snapshot.exists) throw new Error(`refuse to overwrite foreign doc ${path}`);
  }
  const oldQuestions = await db.collection("questions").where("testFixture", "==", DF513.marker).get();
  for (const doc of oldQuestions.docs) await doc.ref.delete();
  const oldReviews = await db.collection(`admin/review/lessons/${DF513.lesson}/reviews`).get();
  for (const doc of oldReviews.docs) await doc.ref.delete();
  const common = {version: 1, lastModifiedAt: at, archived: true, testFixture: DF513.marker};
  const batch = db.batch();
  batch.create(db.doc(`catalogs/${DF513.catalog}`), {...common, questType: "COURSE"});
  batch.create(db.doc(`quests/${DF513.course}`), {...common, catalogId: DF513.catalog,
    authorUid: deviceUid, visibleOn: ["archive"]});
  batch.create(db.doc(`sections/${DF513.section}`), {...common, questId: DF513.course});
  batch.create(db.doc(`themes/${DF513.theme}`), {...common, sectionId: DF513.section});
  batch.create(db.doc(`lessons/${DF513.lesson}`), {...common, themeId: DF513.theme,
    reviewSubmissionId: DF513.submission, reviewApprovedAtMs: at - 1000});
  batch.create(db.doc(`admin/review/lessons/${DF513.lesson}`), {ownerUid: deviceUid, questId: DF513.course,
    submissionId: DF513.submission, status: "PUBLISHED", sourceLanguages: ["en"], testFixture: DF513.marker});
  for (const kind of ["TESTING", "LOGIC"]) {
    batch.create(db.doc(`admin/review/lessons/${DF513.lesson}/reviews/df513-${kind.toLowerCase()}`),
      {kind, reviewerUid: `df513-reviewer-${kind.toLowerCase()}`, acceptedByServer: true, score: 3,
        reviewerLevelAtSubmit: 100, submissionId: DF513.submission, testFixture: DF513.marker});
  }
  for (let index = 0; index < 80; index++) {
    batch.create(db.doc(`questions/df513-q-${String(index).padStart(3, "0")}`), {
      lessonId: DF513.lesson, version: 1, language: "en", testFixture: DF513.marker,
      difficulty: index < 60 ? "EASY" : "HARD",
      payload: JSON.stringify({...SINGLE, difficulty: index < 60 ? "EASY" : "HARD"}),
    });
  }
  await batch.commit();
  log({event: "setup-513", deviceUid, courseId: DF513.course,
    wallet: {nolics: Math.max(Number(device.nolics || 0), 3000), boxCount: Math.max(Number(device.boxCount || 0), 100)},
    opponentUid: opponent.localId, opponentToken: opponent.idToken, questions: 80});
}

async function cmdAwaitReviewRequest() {
  const maxWaitSec = Number(argv[1] || 900);
  const baseline = new Set((await db.collection("quest_review_requests").get()).docs.map((doc) => doc.id));
  const deadline = now() + maxWaitSec * 1000;
  let submissionId = null;
  while (now() < deadline) {
    const snapshot = await db.collection("quest_review_requests").get();
    const fresh = snapshot.docs.find((doc) => !baseline.has(doc.id));
    if (fresh) {
      submissionId = fresh.id;
      log({event: "review-request", id: submissionId, status: fresh.data().status,
        owner: fresh.data().ownerUid});
      break;
    }
    await sleep(2500);
  }
  if (!submissionId) {
    log({event: "timeout", wanted: "quest_review_requests"});
    process.exitCode = 2;
    return;
  }
  await sleep(2000);
  await sweepQueue();
  await reportQueueState();
}

async function cmdAwaitProposals() {
  const expected = Number(argv[1]);
  const maxWaitSec = Number(argv[2] || 900);
  const baseline = new Set((await db.collection("lessonProposals").get()).docs.map((doc) => doc.id));
  const deadline = now() + maxWaitSec * 1000;
  let seen = 0;
  const known = new Set();
  while (seen < expected && now() < deadline) {
    const snapshot = await db.collection("lessonProposals").get();
    for (const doc of snapshot.docs) {
      if (baseline.has(doc.id) || known.has(doc.id)) continue;
      known.add(doc.id);
      seen++;
      log({event: "proposal-seen", id: doc.id, kind: doc.data().kind});
    }
    await sleep(2500);
  }
  if (seen < expected) log({event: "partial", seen, expected});
  await sleep(2000);
  await sweepQueue();
  await reportProposals(null);
}

async function cmdAwaitTournament() {
  const courseId = argv[1];
  const maxWaitSec = Number(argv[2] || 900);
  const deadline = now() + maxWaitSec * 1000;
  while (now() < deadline) {
    const snapshot = await db.collection("course_tournament_events")
      .where("source.id", "==", courseId).get();
    if (!snapshot.empty) {
      const event = snapshot.docs[0].data();
      log({event: "tournament", eventId: snapshot.docs[0].id, status: event.status,
        escrow: event.escrow, organizer: event.organizerUid});
      return;
    }
    await sleep(2500);
  }
  log({event: "timeout", wanted: "course_tournament_events", courseId});
  process.exitCode = 2;
}

const commands = {
  "await-users": cmdAwaitUsers,
  topup: cmdTopup,
  "seed-author-lesson": cmdSeedAuthorLesson,
  "setup-513": cmdSetup513,
  "await-review-request": cmdAwaitReviewRequest,
  "await-proposals": cmdAwaitProposals,
  "await-tournament": cmdAwaitTournament,
};

if (!commands[command]) {
  console.error(`unknown command ${command}; see header of this file`);
  process.exit(1);
}
commands[command]().catch((error) => {
  log({event: "fail", message: error.message});
  process.exit(1);
}).finally(() => admin.app().delete());
