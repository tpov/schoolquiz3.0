"use strict";

// Integration-only synthetic accounts/content. Never clears a database or uses real credentials.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const PROJECT = "demo-schoolquiz-mvp";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "Only the task's demo project is allowed");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "schoolquiz-mvp-firebase:8080");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "schoolquiz-mvp-firebase:9099");
const admin = require("../functions/node_modules/firebase-admin");
const {SINGLE} = require("../functions/_question-fixtures");
const {eventPath} = require("../functions/arena-entry-runtime");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = randomUUID().replaceAll("-", "");
const names = Object.fromEntries(["catalog", "quest", "section", "theme", "lesson", "submission"]
  .map((name) => [name, `${name}-${runId}`]));
const results = [];

async function request(url, body, token) {
  const response = await fetch(url, {method: "POST", headers: {"content-type": "application/json",
    ...(token ? {authorization: `Bearer ${token}`} : {})}, body: JSON.stringify(body), signal: AbortSignal.timeout(60_000)});
  const json = await response.json();
  if (!response.ok || json.error) {
    const error = new Error(json.error?.message || `HTTP ${response.status}`);
    error.code = json.error?.status; throw error;
  }
  return json;
}
async function account() {
  const value = await request("http://schoolquiz-mvp-firebase:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo", {returnSecureToken: true});
  return {uid: value.localId, token: value.idToken};
}
function call(actor, method, data) {
  return request(`http://schoolquiz-mvp-firebase:5001/${PROJECT}/us-central1/${method}`, {data}, actor.token)
    .then((value) => value.result);
}
async function user(actor) { return (await db.doc(`users/${actor.uid}`).get()).data(); }
async function prepare(actor, key, extra = {}) {
  const result = await call(actor, "prepareArenaEntry", {requestId: `${runId}-${key}`, mode: "SELECTED",
    lessonId: names.lesson, difficulty: "HARD", ...extra});
  assert.equal(result.status, "ISSUED");
  return result.ticket;
}
function completion(actor, ticket, correct = false) {
  return {ownerUid: actor.uid, ticketId: ticket.ticketId, attemptId: ticket.attemptId,
    lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion, difficulty: ticket.difficulty,
    completionKind: "COMPLETED", answers: correct ? ticket.questions.map((question, codeAnswerIndex) => ({
      questionId: question.questionId, codeAnswerIndex, answeredAtMs: Date.now(), durationMs: 100,
      answerPayload: JSON.stringify({type: "single-choice", selected: "b"}),
    })) : []};
}
async function main() {
  const fresh = await account();
  const initialProfile = await call(fresh, "ensureUserProfile", {knownLanguages: ["en"]});
  assert.equal(initialProfile.uid, fresh.uid);
  const economy = await call(fresh, "getEconomyConstants", {});
  assert.equal(economy.unchanged, false);
  assert.equal(Number.isSafeInteger(economy.version), true);
  results.push("anonymous app startup creates its own profile and reads server constants");
  const [author, curator, player] = await Promise.all([account(), account(), account()]);
  const now = Date.now();
  const rows = [
    ...[author, curator, player].flatMap((actor) => [
      [`users/${actor.uid}`, {uid: actor.uid, boxCount: 2, nolics: 1000, pointsNolics: 1000,
        standardHearts: 10, goldHearts: 3, lifePoints: 1000, plasmaPoints: 300,
        lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now}],
      [`profiles/${actor.uid}`, {uid: actor.uid, adminLevel: actor === curator ? 100 : 0}],
    ]),
    [`catalogs/${names.catalog}`, {questType: "REGULAR"}],
    [`quests/${names.quest}`, {catalogId: names.catalog, authorUid: author.uid, visibleOn: ["archive"]}],
    [`sections/${names.section}`, {questId: names.quest}],
    [`themes/${names.theme}`, {sectionId: names.section}],
    [`lessons/${names.lesson}`, {themeId: names.theme, version: 1, publishedAtMs: now - 2000,
      reviewSubmissionId: names.submission, reviewApprovedAtMs: now - 1000}],
    [`admin/review/lessons/${names.lesson}`, {ownerUid: author.uid, questId: names.quest, submissionId: names.submission,
      status: "PUBLISHED", sourceLanguages: ["en"]}],
    ...["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${names.lesson}/reviews/${kind}`, {
      kind, reviewerUid: `${kind}-${runId}`, acceptedByServer: true, reviewerLevelAtSubmit: 100,
      score: 3, submissionId: names.submission,
    }]),
    ...[0, 1].map((n) => [`questions/q-${runId}-${n}`, {lessonId: names.lesson, version: 1, difficulty: "HARD",
      payload: JSON.stringify({...SINGLE, difficulty: "HARD"})}]),
  ];
  const batch = db.batch();
  rows.forEach(([path, value]) => batch.create(db.doc(path), value));
  await batch.commit();
  const moved = await call(curator, "setLessonPlacement", {mutationId: `admit-${runId}`, lessonId: names.lesson,
    targetShelf: "arena", reason: "Synthetic emulator acceptance fixture"});
  assert.equal(moved.status, "APPLIED"); results.push("independent review → curator placement");

  const ticket = await prepare(player, "same");
  const payload = completion(player, ticket, true);
  const submit = () => call(player, "submitLessonResultEvents", {attempts: [payload]});
  const [a, b] = await Promise.all([submit(), submit()]);
  assert.deepEqual(a, b); assert.equal(a.receipts[0].status, "SETTLED"); assert.equal(a.receipts[0].percentScore, 100);
  assert.equal(a.receipts[0].lifePointsSpent, 50); assert.equal(a.receipts[0].nolicsSpent, 1);
  const settledUser = await user(player);
  assert.equal(settledUser.nolics, 999 + a.receipts[0].reward.nolics);
  assert.equal(settledUser.reservedLifePoints, 0);
  assert.deepEqual(await submit(), a);
  assert.deepEqual(await user(player), settledUser);
  results.push("two simultaneous deliveries and lost-response replay settle once");

  const [left, right] = await Promise.all([prepare(player, "left"), prepare(player, "right")]);
  const beforePair = await user(player);
  assert.equal(beforePair.reservedNolics, 2); assert.equal(beforePair.reservedLifePoints, 100);
  await Promise.all([left, right].map((item) => call(player, "submitLessonResultEvents", {attempts: [completion(player, item)]})));
  const afterPair = await user(player);
  assert.equal(afterPair.nolics, beforePair.nolics); assert.equal(afterPair.reservedNolics, 0);
  assert.equal(afterPair.reservedLifePoints, 0);
  results.push("two different holds settle without overwriting the shared wallet/carry");

  const raced = await prepare(player, "race");
  const beforeRace = await user(player);
  const outcomes = await Promise.allSettled([
    call(player, "submitLessonResultEvents", {attempts: [completion(player, raced)]}),
    call(player, "cancelArenaEntry", {ticketId: raced.ticketId, requestId: `cancel-${runId}`}),
  ]);
  const terminal = (await db.doc(`arena_entry_tickets/${raced.ticketId}`).get()).data();
  const afterRace = await user(player);
  assert.equal(afterRace.reservedNolics, 0); assert.equal(afterRace.reservedLifePoints, 0);
  if (terminal.status === "CANCELLED") {
    assert.equal(outcomes[0].status, "rejected"); assert.equal(afterRace.nolics, beforeRace.nolics + 1);
    assert.equal((await db.doc(eventPath(raced.attemptId)).get()).exists, false);
  } else {
    assert.equal(terminal.status, "SETTLED"); assert.equal(outcomes[0].status, "fulfilled");
    assert.equal(afterRace.nolics, beforeRace.nolics);
    assert.equal((await db.doc(eventPath(raced.attemptId)).get()).data().settlementReceipt.status, "SETTLED");
  }
  results.push("real Firestore cancellation/completion race has only one financial outcome");

  const opening = {ownerUid: player.uid, openingId: `box-${runId}`};
  const boxesBefore = (await user(player)).boxCount;
  const [boxA, boxB] = await Promise.all([call(player, "openGiftBox", opening), call(player, "openGiftBox", opening)]);
  assert.deepEqual(boxA, boxB); assert.equal(boxA.status, "OPENED");
  assert.equal((await user(player)).boxCount, boxesBefore - 1);
  results.push("concurrent box opening returns one roll and one debit");

  const queuedOpening = {ownerUid: player.uid, openingId: `queued-box-${runId}`};
  const queueRequest = {mutationId: `queued-box-${runId}`, operation: "economy.OPEN_GIFT_BOX",
    payload: JSON.stringify(queuedOpening)};
  const queueBefore = (await user(player)).boxCount;
  const delivered = await call(player, "submitMutation", queueRequest);
  const replayed = await call(player, "submitMutation", queueRequest);
  assert.equal(delivered.replayed, false); assert.equal(replayed.replayed, true);
  assert.deepEqual(delivered.result, replayed.result);
  assert.equal(delivered.result.ownerUid, player.uid);
  assert.equal(delivered.result.operationId, queuedOpening.openingId);
  assert.equal((await user(player)).boxCount, queueBefore - 1);
  await assert.rejects(call(curator, "submitMutation", queueRequest), {code: "PERMISSION_DENIED"});
  results.push("actual box outbox envelope replays its permanent receipt and rejects another owner");

  const rating = {mutationId: `rate-${runId}`, lessonId: names.lesson, attemptId: ticket.attemptId, rating: 3};
  const rated = await call(player, "rateArenaLesson", rating);
  assert.equal(rated.status, "APPLIED");
  assert.deepEqual(await call(player, "rateArenaLesson", rating), rated);
  assert.equal((await db.doc(`lesson_ratings/${names.lesson}`).get()).data().ratingCount, 1);
  const replaced = await call(player, "rateArenaLesson", {...rating, mutationId: `rate-replace-${runId}`, rating: 2});
  assert.equal(replaced.status, "APPLIED");
  const aggregate = (await db.doc(`lesson_ratings/${names.lesson}`).get()).data();
  assert.equal(aggregate.ratingCount, 1); assert.equal(aggregate.ratingSum, 2);
  const authorBefore = (await user(author)).boxCount;
  for (const [step, targetShelf] of ["tournament", "home", "retired", "arena", "tournament", "home"].entries()) {
    const promoted = await call(curator, "setLessonPlacement", {mutationId: `move-${runId}-${step}`,
      lessonId: names.lesson, targetShelf, reason: "Synthetic advancement acceptance"});
    assert.equal(promoted.status, "APPLIED");
  }
  assert.equal((await user(author)).boxCount, authorBefore + 35);
  assert.deepEqual((await db.doc(`quests/${names.quest}`).get()).data().visibleOn, ["archive"]);
  results.push("latest lesson rating replaces one vote; Tournament/Home bonuses cannot be collected twice");

  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, checks: results,
    retainedSyntheticFixtures: true}, null, 2));
}
main().catch((error) => { console.error(JSON.stringify({status: "FAIL", message: error.message, code: error.code,
  checksCompleted: results}, null, 2)); process.exitCode = 1; }).finally(() => admin.app().delete());
