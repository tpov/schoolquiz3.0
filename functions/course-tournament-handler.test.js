"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("node:module");
const {createFirestoreStub} = require("./_firestore-stub");
const {SINGLE} = require("./_question-fixtures");
const {tournamentDigest: digest} = require("./tournament-bank");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const NOW = Date.parse("2026-09-07T00:00:00Z");
// The production controller captures Date.now at construction. Install the fake before index
// constructs it; replacing Date.now only inside fixed() leaves that captured clock real.
const functions = (() => {
  const original = Date.now;
  Date.now = () => NOW;
  try { return require("./index"); } finally { Date.now = original; }
})();
const EVENT = "event_handler_00000001";
const endpoints = ["prepareCourseTournamentBank", "submitCourseTournamentOperation", "submitCourseTournamentSession",
  "fetchCourseTournamentEvent", "fetchCourseTournamentSession", "fetchCourseTournamentMeeting",
  "fetchCourseTournamentEvents", "recordCourseTournamentDownload"];
const call = (name, uid, data) => functions[name].run({auth: {uid}, data});
const downloadKey = (uid, courseId) => `course_tournament_downloads/${digest([uid, courseId])}`;
function world() {
  const wallet = {boxCount: 100, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
    lifePointsUpdatedAtMs: NOW, plasmaPointsUpdatedAtMs: NOW};
  return {
    "users/author": {...wallet, uid: "author"}, "users/player": {...wallet, uid: "player"}, "users/linky": {...wallet, uid: "linky"},
    "profiles/author": {uid: "author"}, "profiles/player": {uid: "player"}, "profiles/linky": {uid: "linky"},
    // Story 5.3: the player's attested download record is what admits the START below.
    [downloadKey("player", "course")]: {schemaVersion: 1, uid: "player", courseId: "course", downloadedAtMs: NOW},
    "catalogs/catalog": {questType: "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: "author", version: 1, visibleOn: ["archive"], archived: true},
    "sections/section": {questId: "course", version: 1}, "themes/theme": {sectionId: "section", version: 1},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: NOW - 1},
    "admin/review/lessons/lesson": {ownerUid: "author", questId: "course", submissionId: "submission",
      status: "PUBLISHED", sourceLanguages: ["en"]},
    ...Object.fromEntries(["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/lesson/reviews/${kind}`, {
      kind, reviewerUid: kind, acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission",
    }])),
    ...Object.fromEntries(Array.from({length: 80}, (_, i) => [`questions/q${String(i).padStart(3, "0")}`, {
      lessonId: "lesson", version: 1, language: "en", difficulty: i < 60 ? "EASY" : "HARD",
      payload: JSON.stringify({...SINGLE, difficulty: i < 60 ? "EASY" : "HARD"}),
    }])),
  };
}
async function fixed(body) {
  const original = Date.now;
  Date.now = () => NOW;
  store.reset(world());
  try { await body(); } finally { Date.now = original; }
}
function safe(value) {
  const prohibited = ["answerKey", "keys", "correctOptionId", "codeAnswer", "scoreDigits", "scoreDigit",
    "bankJson", "proofJson", "serverSeed", "fingerprint", "formFingerprint", "contentHash"];
  if (!value || typeof value !== "object") return;
  for (const [key, item] of Object.entries(value)) { assert.ok(!prohibited.includes(key), key); safe(item); }
}
async function create() {
  const source = {kind: "COURSE", id: "course"};
  const prepared = await call("prepareCourseTournamentBank", "author", {requestId: "prepare_handler_00001", source});
  assert.deepEqual(prepared.counts, {easy: 60, hard: 20, total: 80});
  const request = {kind: "CREATE", operationId: "create_handler_000001", eventId: EVENT,
    source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: NOW,
    prizes: {first: 5, second: 3, third: 1}, fundedBoxes: 12};
  return {request, response: await call("submitCourseTournamentOperation", "author", request)};
}

test("all explicit tournament handlers require Auth before reads/writes", () => fixed(async () => {
  const before = structuredClone([...store.documents]);
  for (const name of endpoints) await assert.rejects(functions[name].run({data: {uid: "author"}}), {code: "unauthenticated"});
  assert.deepEqual([...store.documents], before);
}));

test("real root course review loader prepares an archive bank and reserves boxes once", () => fixed(async () => {
  const {request, response} = await create();
  assert.equal(response.status, "CREATED"); safe(response);
  assert.equal(response.event.organizerUid, "author");
  assert.equal(store.documents.get("users/author").boxCount, 88);
  const before = structuredClone([...store.documents]);
  assert.deepEqual(await call("submitCourseTournamentOperation", "author", request), response);
  assert.deepEqual([...store.documents], before);
  const view = await call("fetchCourseTournamentEvent", "player", {eventId: EVENT});
  assert.equal(view.serverNowMs, NOW, "constructed controller must use the fixture clock");
  assert.equal(view.event.status, "QUALIFICATION"); safe(view);
  assert.deepEqual(view.event.source, {kind: "COURSE", id: "course"});
}));

test("root wrappers do not permit owner spoofing or public worker dispatch", () => fixed(async () => {
  await create();
  for (const kind of ["CLOSE_QUALIFICATION", "ADVANCE_MATCH", "reconcileEvent"]) {
    await assert.rejects(call("submitCourseTournamentOperation", "author", {kind, eventId: EVENT,
      operationId: "worker_spoof_00000001"}), {code: "permission-denied"});
  }
  await assert.rejects(call("prepareCourseTournamentBank", "player", {
    requestId: "another_handler_00001", source: {kind: "COURSE", id: "course"}, uid: "author",
  }), {code: "invalid-argument"});
  assert.equal(store.documents.get("users/author").boxCount, 88);
}));

test("root session call derives owner/form and preserves answer replay without grading leaks", () => fixed(async () => {
  await create();
  const started = await call("submitCourseTournamentOperation", "player", {kind: "START_QUALIFICATION", eventId: EVENT,
    operationId: "start_handler_0000001", attemptId: "attempt_handler_00001", roundNumber: 1});
  assert.equal(started.session.startedAtMs, NOW);
  const sessionId = started.session.sessionId;
  const view = await call("fetchCourseTournamentSession", "player", {sessionId});
  safe(started); safe(view); assert.equal(view.currentQuestion.publicPayload.type, "SingleChoiceRedacted");
  await assert.rejects(call("fetchCourseTournamentSession", "author", {sessionId}), {code: "permission-denied"});
  const request = {kind: "ANSWER", sessionId, operationId: "answer_handler_000001", questionIndex: 0,
    questionId: view.currentQuestion.questionId, answerPayload: null};
  const answered = await call("submitCourseTournamentSession", "player", request);
  assert.equal(answered.session.questionIndex, 1); safe(answered);
  assert.deepEqual(await call("submitCourseTournamentSession", "player", request), answered);
  await assert.rejects(call("submitCourseTournamentSession", "player", {...request, nowMs: 0}), {code: "invalid-argument"});
}));

test("source preparation error is stable and contains no private content", () => fixed(async () => {
  store.documents.get("admin/review/lessons/lesson/reviews/LOGIC").submissionId = "old";
  await assert.rejects(call("prepareCourseTournamentBank", "author", {
    requestId: "invalid_handler_00001", source: {kind: "COURSE", id: "course"},
  }), error => {
    assert.equal(error.code, "failed-precondition");
    assert.equal(error.details.reason, "INSUFFICIENT_QUESTIONS");
    assert.equal(error.message, "Tournament operation could not be completed");
    return true;
  });
  assert.equal(store.documents.get("users/author").boxCount, 100);
}));

test("download recording and event discovery run as real callables with redacted pages", () => fixed(async () => {
  const recorded = await call("recordCourseTournamentDownload", "linky", {operationId: "record_handler_0001", courseId: "course"});
  assert.deepEqual(recorded, {courseId: "course", downloadedAtMs: NOW});
  assert.ok(store.documents.has(downloadKey("linky", "course")));
  // Repeating — even under another operation id — returns the stored outcome and writes nothing.
  const writes = store.writes.length;
  assert.deepEqual(await call("recordCourseTournamentDownload", "linky", {operationId: "record_handler_0002", courseId: "course"}), {...recorded, replayed: true});
  assert.equal(store.writes.length, writes);
  await assert.rejects(call("recordCourseTournamentDownload", "linky", {operationId: "record_handler_0003", courseId: 7}), {code: "invalid-argument"});
  const {response} = await create();
  assert.ok(/^[a-f0-9]{48}$/.test(response.admissionLink.token));
  const listed = await call("fetchCourseTournamentEvents", "player", {pageSize: 10});
  assert.deepEqual(listed.events.map(row => row.eventId), [EVENT]);
  assert.equal(listed.hasMore, false); assert.equal(listed.nextEventId, null); safe(listed);
  assert.equal(Object.hasOwn(listed, "admissionLink"), false);
  assert.ok(!JSON.stringify(listed).includes("serverSeed"));
  await assert.rejects(call("fetchCourseTournamentEvents", "player", {pageSize: 101}), {code: "invalid-argument"});
  await assert.rejects(call("fetchCourseTournamentEvents", "player", {afterEventId: "short"}), {code: "invalid-argument"});
}));

test("the organizer's link token admits a player with no download record and never leaks into reads", () => fixed(async () => {
  const {request, response} = await create();
  const token = response.admissionLink.token;
  assert.ok(/^[a-f0-9]{48}$/.test(token));
  const link = store.documents.get(`course_tournament_links/${EVENT}`);
  assert.ok(/^[a-f0-9]{64}$/.test(link.digest));
  await assert.rejects(call("submitCourseTournamentOperation", "linky", {kind: "START_QUALIFICATION", eventId: EVENT,
    operationId: "start_linky_0000001", attemptId: "attempt_linky_000001", roundNumber: 1}), {code: "permission-denied"});
  // Receipt replay with the same operation id is how the organizer recovers a lost token.
  const replayed = await call("submitCourseTournamentOperation", "author", request);
  assert.equal(replayed.admissionLink.token, token);
  // The organizer distributed the token out of band; the player presents it at START.
  const started = await call("submitCourseTournamentOperation", "linky", {kind: "START_QUALIFICATION", eventId: EVENT,
    operationId: "start_linky_0000001", attemptId: "attempt_linky_000001", roundNumber: 1, linkToken: token});
  assert.equal(started.session.startedAtMs, NOW); safe(started);
  assert.equal(Object.hasOwn(started, "admissionLink"), false);
  const view = await call("fetchCourseTournamentEvent", "linky", {eventId: EVENT});
  assert.equal(Object.hasOwn(view, "admissionLink"), false); safe(view);
  // The same operation id without the token is a different request and cannot replay.
  await assert.rejects(call("submitCourseTournamentOperation", "linky", {kind: "START_QUALIFICATION", eventId: EVENT,
    operationId: "start_linky_0000001", attemptId: "attempt_linky_000001", roundNumber: 1}), {code: "already-exists"});
}));
