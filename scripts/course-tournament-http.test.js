"use strict";

// Actual HTTP acceptance against this task's private DEMO emulators only. Never clears a database,
// overwrites an existing fixture, rewrites expected replies, or changes the server clock.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Real credentials are not permitted");
const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
const {TOURNAMENT_PATHS: P} = require("/repo/functions/tournament-service");
const {tournamentDigest: digest} = require("/repo/functions/tournament-bank");
const {SOURCE_BANKS, PREPARATIONS} = require("/repo/functions/tournament-source-bank");
const {LEDGER_PATH} = require("/repo/functions/currency-ledger");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `ct_${randomUUID().replaceAll("-", "")}`;
const id = (name) => `${runId}_${name}`;
const names = Object.fromEntries(["catalog", "course", "section", "theme", "lesson", "submission", "event"]
  .map((name) => [name, id(name)]));
const endpoints = ["prepareCourseTournamentBank", "submitCourseTournamentOperation", "submitCourseTournamentSession",
  "fetchCourseTournamentEvent", "fetchCourseTournamentSession", "fetchCourseTournamentMeeting",
  "recordCourseTournamentDownload", "fetchCourseTournamentEvents"];
const checks = [];
const evidence = {runId, projectId: PROJECT, fixtureNames: names, retainedSyntheticFixtures: true};
let stage = "preflight";
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
function progress(name, extra = {}) { stage = name; console.log(JSON.stringify({stage, runId, ...extra})); }
function passed(name) { checks.push(name); progress(name); }

async function post(url, body, token, retryTransport = true) {
  const serialized = JSON.stringify(body); // Retries preserve the exact original request.
  for (let attempt = 0; ; attempt++) {
    let response;
    try {
      response = await fetch(url, {method: "POST", headers: {"content-type": "application/json",
        ...(token ? {authorization: `Bearer ${token}`} : {})}, body: serialized, signal: AbortSignal.timeout(30_000)});
    } catch (error) {
      // A retry never changes the operation id or data. No broad retry of a server business error.
      if (!retryTransport || attempt >= 2 || !["TypeError", "TimeoutError"].includes(error.name)) throw error;
      await delay(500 * (attempt + 1)); continue;
    }
    const raw = await response.text();
    let json;
    try { json = JSON.parse(raw); } catch {
      if (retryTransport && attempt < 2 && [502, 503, 504].includes(response.status)) {
        await delay(500 * (attempt + 1)); continue;
      }
      throw new Error(`Non-JSON HTTP ${response.status}`);
    }
    if (!response.ok || json.error) {
      const error = new Error(json.error?.message || `HTTP ${response.status}`);
      error.code = json.error?.status;
      error.reason = json.error?.details?.reason;
      error.httpStatus = response.status;
      const transient = error.code === "UNAVAILABLE" && error.reason === undefined;
      if (retryTransport && attempt < 2 && transient) { await delay(500 * (attempt + 1)); continue; }
      throw error;
    }
    return json;
  }
}
function call(actor, endpoint, data) {
  assert.ok(endpoints.includes(endpoint), "Only the scoped course tournament callables");
  return post(`http://${HOST}:5001/${PROJECT}/us-central1/${endpoint}`, {data}, actor?.token).then((json) => json.result);
}
async function account(role) {
  // Only a fresh anonymous Auth emulator account; a lost signup response is not retried.
  const json = await post(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`,
    {returnSecureToken: true}, undefined, false);
  assert.equal(typeof json.localId, "string"); assert.equal(typeof json.idToken, "string");
  return {role, uid: json.localId, token: json.idToken};
}
async function denied(operation, code, reason) {
  await assert.rejects(operation, (error) => {
    assert.equal(error.code, code);
    if (reason !== undefined) assert.equal(error.reason, reason);
    return true;
  });
}
function safe(value) {
  const blocked = new Set(["answerKey", "keys", "correctOptionId", "correctOptionIds", "correctCandidateId",
    "codeAnswer", "scoreDigits", "scoreDigit", "questions", "qualificationForms", "forms", "sharedForm",
    "bankJson", "proofJson", "serverSeed", "fingerprint", "formFingerprint", "contentHash", "privateBankFingerprint",
    "proofHash", "bankHash", "poolFingerprint"]);
  function visit(row) {
    if (!row || typeof row !== "object") return;
    for (const [key, child] of Object.entries(row)) { assert.ok(!blocked.has(key), `Private wire field: ${key}`); visit(child); }
  }
  visit(value);
}
async function ownedDocument(path) { return (await db.doc(path).get()).data(); }
async function privateClientRead(actor, path) {
  const response = await fetch(`http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`,
    {headers: {authorization: `Bearer ${actor.token}`}, signal: AbortSignal.timeout(15_000)});
  await response.arrayBuffer();
  assert.equal(response.status, 403, "Synthetic private document must not be readable with a client token");
}

async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hub.status, 200);
  const running = await hub.json();
  assert.equal(running.auth.port, 9099); assert.equal(running.firestore.port, 8080); assert.equal(running.functions.port, 5001);
  evidence.mountedSourceShas = Object.fromEntries(["index.js", "course-tournament-controller.js", "tournament-service.js", "tournament-source-bank.js"]
    .map((file) => [file, createHash("sha256").update(readFileSync(`/repo/functions/${file}`)).digest("hex")]));
  progress("target-verified-private-demo");
  for (const endpoint of endpoints) await denied(() => call(null, endpoint, {}), "UNAUTHENTICATED");
  passed("all-six-HTTP-callables-deny-missing-auth-before-fixture-writes");

  const [author, player] = await Promise.all([account("author"), account("player")]);
  evidence.syntheticAccounts = {authorUid: author.uid, playerUid: player.uid}; // Never print tokens.
  const now = Date.now();
  const wallet = {boxCount: 100, nolics: 1000, pointsNolics: 1000, standardHearts: 5, goldHearts: 1,
    lifePoints: 500, plasmaPoints: 100, lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now, testFixture: runId};
  const rows = [
    ...[author, player].flatMap((actor) => [[`users/${actor.uid}`, {...wallet, uid: actor.uid}],
      [`profiles/${actor.uid}`, {uid: actor.uid, testFixture: runId}]]),
    [`catalogs/${names.catalog}`, {questType: "COURSE", version: 1, testFixture: runId}],
    [`quests/${names.course}`, {catalogId: names.catalog, authorUid: author.uid, version: 1,
      visibleOn: ["archive"], archived: true, testFixture: runId}],
    [`sections/${names.section}`, {questId: names.course, version: 1, testFixture: runId}],
    [`themes/${names.theme}`, {sectionId: names.section, version: 1, testFixture: runId}],
    [`lessons/${names.lesson}`, {themeId: names.theme, version: 1, reviewSubmissionId: names.submission,
      reviewApprovedAtMs: now - 1000, testFixture: runId}],
    [`admin/review/lessons/${names.lesson}`, {ownerUid: author.uid, questId: names.course, submissionId: names.submission,
      status: "PUBLISHED", sourceLanguages: ["en"], testFixture: runId}],
    ...["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${names.lesson}/reviews/${id(kind)}`, {
      kind, reviewerUid: id(`reviewer_${kind}`), acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100,
      submissionId: names.submission, testFixture: runId,
    }]),
    ...Array.from({length: 80}, (_, index) => [`questions/${id(`q${String(index).padStart(3, "0")}`)}`, {
      lessonId: names.lesson, version: 1, language: "en", difficulty: index < 60 ? "EASY" : "HARD", testFixture: runId,
      // A longer legitimate synthetic prompt keeps this HTTP/ownership check away from a tiny
      // question timeout. The real server still calculates every timer from the frozen text.
      payload: JSON.stringify({...SINGLE, text: `${SINGLE.text} ${"Synthetic integration fixture. ".repeat(12)}`,
        difficulty: index < 60 ? "EASY" : "HARD"}),
    }]),
  ];
  const batch = db.batch();
  rows.forEach(([path, body]) => { assert.equal(body.testFixture, runId); batch.create(db.doc(path), body); });
  await batch.commit();
  evidence.createdFixtureDocuments = rows.length;
  passed("fresh-accounts-and-uniquely-prefixed-verified-80-question-course-created-with-create-only");

  const source = {kind: "COURSE", id: names.course};
  const preparation = {requestId: id("prepare"), source};
  const prepared = await call(author, "prepareCourseTournamentBank", preparation);
  assert.deepEqual(prepared.counts, {easy: 60, hard: 20, total: 80}); safe(prepared);
  assert.deepEqual(await call(author, "prepareCourseTournamentBank", preparation), prepared);
  const storedBank = await ownedDocument(`${SOURCE_BANKS}/${prepared.snapshotId}`);
  assert.equal(storedBank.ownerUid, author.uid);
  const bank = JSON.parse(storedBank.bankJson);
  assert.equal(bank.questions.length, 80); assert.equal(new Set(bank.questions.map((q) => q.questionId)).size, 80);
  assert.equal(bank.questions.every((q) => q.questionId.startsWith(runId)), true);
  evidence.prepared = prepared;
  passed("actual-preparation-and-replay-freeze-exactly60EASY-plus20HARD-from-the-verified-course");

  const createRequest = {kind: "CREATE", operationId: id("create"), eventId: names.event,
    source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: Date.now() + 60_000,
    prizes: {first: 5, second: 3, third: 1}, fundedBoxes: 12};
  const created = await call(author, "submitCourseTournamentOperation", createRequest);
  assert.equal(created.status, "CREATED"); safe(created);
  assert.equal(created.event.organizerUid, author.uid);
  assert.equal((await ownedDocument(`users/${author.uid}`)).boxCount, 88);
  assert.deepEqual(created.event.escrow, {reservedBoxes: 12, remainingBoxes: 12, paidBoxes: 0, returnedBoxes: 0});
  const reserveSourceId = JSON.stringify(["tournament", names.event, "reserve"]);
  const fundingPath = `${P.funding}/${digest(reserveSourceId)}`;
  const funding = await ownedDocument(fundingPath);
  assert.equal(funding.eventId, names.event); assert.equal(funding.uid, author.uid);
  assert.equal(funding.kind, "RESERVE"); assert.equal(funding.delta, -12);
  const ledgerRows = await db.collection(LEDGER_PATH).where("details.eventId", "==", names.event).get();
  assert.equal(ledgerRows.size, 1);
  const ledger = ledgerRows.docs[0].data();
  assert.equal(ledger.uid, author.uid); assert.equal(ledger.currency, "boxes"); assert.equal(ledger.delta, -12);
  const beforeReplay = await ownedDocument(`users/${author.uid}`);
  assert.deepEqual(await call(author, "submitCourseTournamentOperation", createRequest), created);
  assert.deepEqual(await ownedDocument(`users/${author.uid}`), beforeReplay);
  assert.deepEqual(await ownedDocument(fundingPath), funding);
  const afterReplayLedger = await db.collection(LEDGER_PATH).where("details.eventId", "==", names.event).get();
  assert.equal(afterReplayLedger.size, 1); assert.deepEqual(afterReplayLedger.docs[0].data(), ledger);
  evidence.creation = created; evidence.reserveFunding = funding; evidence.reserveLedger = ledger;
  passed("real-CREATE-reserves12BOXES-once-with-immutable-funding-ledger-and-identical-replay");

  let view = await call(player, "fetchCourseTournamentEvent", {eventId: names.event});
  safe(view); assert.equal(view.event.status, "QUALIFICATION");
  assert.deepEqual(view.event.source, source);
  assert.equal(view.event.qualification.startsAtMs, createRequest.startsAtMs);
  assert.equal(view.event.qualification.endsAtMs - view.event.qualification.startsAtMs, 7 * 24 * 60 * 60 * 1000);
  evidence.eventView = view;
  for (const kind of ["CLOSE_QUALIFICATION", "ADVANCE_MATCH", "reconcileEvent"]) {
    await denied(() => call(author, "submitCourseTournamentOperation", {kind, eventId: names.event,
      operationId: id(`forbidden_${kind}`)}), "PERMISSION_DENIED", "PERMISSION_DENIED");
  }
  await denied(() => call(author, "submitCourseTournamentOperation", {kind: "START_QUALIFICATION", eventId: names.event,
    operationId: id("owner_spoof"), ownerUid: player.uid, attemptId: id("spoof_attempt"), roundNumber: 1}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
  // No fabricated/time-warped bracket: a qualification event has no meeting to expose yet.
  await denied(() => call(player, "fetchCourseTournamentMeeting", {eventId: names.event, matchId: id("no_match")}),
    "UNAVAILABLE", "UNAVAILABLE");
  evidence.meetingCoverage = "Actual authenticated qualification-state refusal; no positive bracket meeting or clock override";
  passed("safe-event-view-worker-capability-owner-spoof-denials-and-real-meeting-precondition");

  for (const actor of [author, player]) {
    for (const path of [`${P.events}/${names.event}`, `${SOURCE_BANKS}/${prepared.snapshotId}`,
      `${PREPARATIONS}/${digest(preparation.requestId)}`, fundingPath]) await privateClientRead(actor, path);
  }
  passed("actual-Firestore-rules-deny-both-clients-direct-event-bank-preparation-and-escrow-reads");

  const pollDeadline = Date.now() + 90_000;
  progress("waiting-for-real-qualification-start", {startsAtMs: createRequest.startsAtMs});
  while (view.serverNowMs < createRequest.startsAtMs) {
    assert.ok(Date.now() < pollDeadline, "Real qualification start was not reached within bounded polling");
    await delay(Math.min(2000, Math.max(100, createRequest.startsAtMs - view.serverNowMs)));
    view = await call(player, "fetchCourseTournamentEvent", {eventId: names.event});
  }
  // Story 5.3 admission gate: the player attests the course download before START (UX-O4/access).
  const download = await call(player, "recordCourseTournamentDownload",
    {operationId: id("download"), courseId: names.course});
  assert.equal(download.courseId, names.course);
  const startRequest = {kind: "START_QUALIFICATION", eventId: names.event, operationId: id("start"),
    attemptId: id("attempt"), roundNumber: 1};
  const started = await call(player, "submitCourseTournamentOperation", startRequest);
  safe(started); assert.equal(started.session.kind, "QUALIFICATION");
  assert.ok(started.session.startedAtMs >= createRequest.startsAtMs);
  assert.deepEqual(await call(player, "submitCourseTournamentOperation", startRequest), started);
  const sessionId = started.session.sessionId;
  const session = await call(player, "fetchCourseTournamentSession", {sessionId});
  safe(session); assert.equal(session.currentQuestion.publicPayload.type, "SingleChoiceRedacted");
  await denied(() => call(author, "fetchCourseTournamentSession", {sessionId}), "PERMISSION_DENIED", "PERMISSION_DENIED");
  await privateClientRead(player, `${P.sessions}/${sessionId}`);
  const answerRequest = {kind: "ANSWER", sessionId, operationId: id("answer"), questionIndex: 0,
    questionId: session.currentQuestion.questionId, answerPayload: null};
  const answered = await call(player, "submitCourseTournamentSession", answerRequest);
  safe(answered); assert.equal(answered.session.questionIndex, 1);
  assert.deepEqual(await call(player, "submitCourseTournamentSession", answerRequest), answered);
  await denied(() => call(author, "submitCourseTournamentSession", answerRequest), "PERMISSION_DENIED", "PERMISSION_DENIED");
  await denied(() => call(player, "submitCourseTournamentSession", {...answerRequest, nowMs: 0}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
  const finalSession = await ownedDocument(`${P.sessions}/${sessionId}`);
  assert.equal(finalSession.uid, player.uid); assert.equal(finalSession.questionIndex, 1);
  assert.equal((await db.collection(`${P.sessions}/${sessionId}/answers`).get()).size, 1);
  assert.equal((await ownedDocument(`users/${author.uid}`)).boxCount, 88);
  evidence.sessionStart = started; evidence.firstQuestionView = session; evidence.answerReply = answered;
  evidence.sessionId = sessionId;
  passed("HTTP-qualification-owner-binding-raw-answer-replay-and-single-answer-write-without-grading-leaks");

  console.log(JSON.stringify({status: "PASS", checks, evidence}, null, 2));
}
main().catch((error) => {
  console.error(JSON.stringify({status: "FAIL", stage, code: error.code, reason: error.reason,
    message: error.message, checksCompleted: checks, evidence}, null, 2));
  process.exitCode = 1;
}).finally(() => admin.app().delete());
