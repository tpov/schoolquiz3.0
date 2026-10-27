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
const EVENT = "event_sink_000000001";
const SESSION_OP = "tournament.SESSION_OP";
const call = (name, uid, data) => functions[name].run({auth: {uid}, data});
const drain = (uid, mutationId, operation, payload) =>
  functions.submitMutation.run({auth: {uid}, data: {mutationId, operation, payload: JSON.stringify(payload)}});
const downloadKey = (uid, courseId) => `course_tournament_downloads/${digest([uid, courseId])}`;
function world() {
  const wallet = {boxCount: 100, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
    lifePointsUpdatedAtMs: NOW, plasmaPointsUpdatedAtMs: NOW};
  return {
    "users/author": {...wallet, uid: "author"}, "users/player": {...wallet, uid: "player"},
    "profiles/author": {uid: "author"}, "profiles/player": {uid: "player"},
    [downloadKey("player", "course")]: {schemaVersion: 1, uid: "player", courseId: "course", downloadedAtMs: NOW},
    "catalogs/catalog": {questType: "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: "author", version: 1, visibleOn: ["archive"], archived: true},
    "sections/section": {questId: "course", version: 1}, "themes/theme": {sectionId: "section", version: 1},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: NOW - 1},
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
  for (const [key, item] of Object.entries(value)) {
    assert.ok(!prohibited.includes(key), `private field ${key} reached the client`);
    safe(item);
  }
}
async function startedSession() {
  const prepared = await call("prepareCourseTournamentBank", "author", {requestId: "sink_prepare_0000001",
    source: {kind: "COURSE", id: "course"}});
  await call("submitCourseTournamentOperation", "author", {kind: "CREATE", operationId: "sink_create_00000001",
    eventId: EVENT, source: {kind: "COURSE", id: "course", snapshotId: prepared.snapshotId}, startsAtMs: NOW,
    prizes: {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
      {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]}, fundedBoxes: 12});
  const started = await call("submitCourseTournamentOperation", "player", {kind: "START_QUALIFICATION",
    eventId: EVENT, operationId: "sink_start_00000001", attemptId: "attempt_sink_0000001", roundNumber: 1});
  const sessionId = started.session.sessionId;
  const view = await call("fetchCourseTournamentSession", "player", {sessionId});
  return {sessionId, questionId: view.currentQuestion.questionId};
}

test("session mutation rides the single sink: answer lands, replay returns the same receipt", () => fixed(async () => {
  const {sessionId, questionId} = await startedSession();
  const payload = {kind: "ANSWER", sessionId, operationId: "sink_answer_op_00000001", questionIndex: 0,
    questionId, answerPayload: {type: "single-choice", selected: "a"}};
  const first = await drain("player", "tournament-q0-answer-00000001", SESSION_OP, payload);
  assert.equal(first.replayed, false);
  assert.equal(first.result.status, "ANSWER_ACCEPTED");
  assert.equal(first.result.session.questionIndex, 1);
  safe(first.result);
  // The queue owns the only key: the same drain twice is the same receipt, not a second effect.
  const before = structuredClone([...store.documents]);
  const second = await drain("player", "tournament-q0-answer-00000001", SESSION_OP, payload);
  assert.equal(second.replayed, true);
  assert.deepEqual(second.result, first.result);
  const sessionDoc = store.documents.get(`course_tournament_sessions/${sessionId}`);
  assert.equal(sessionDoc.questionIndex, 1);
  assert.deepEqual([...store.documents].sort(), before.sort());
}));

test("ready-ack rides the same sink and its receipt is permanent for the same key", () => fixed(async () => {
  const {sessionId, questionId} = await startedSession();
  await drain("player", "tournament-q0-answer-00000002", SESSION_OP, {kind: "ANSWER", sessionId,
    operationId: "sink_answer_op_00000002", questionIndex: 0, questionId,
    answerPayload: {type: "single-choice", selected: "a"}});
  const windowId = store.documents.get(`course_tournament_sessions/${sessionId}`).window.windowId;
  const ackPayload = {kind: "READY_ACK", sessionId, operationId: "sink_readyack_op_0000001",
    questionIndex: 1, windowId};
  const first = await drain("player", "tournament-q1-window-00000001", SESSION_OP, ackPayload);
  assert.equal(first.replayed, false);
  assert.equal(first.result.status, "READY_ACK");
  assert.equal(first.result.readyAck.windowId, windowId);
  assert.equal(first.result.readyAck.questionIndex, 1);
  safe(first.result);
  const before = structuredClone([...store.documents]);
  const replay = await drain("player", "tournament-q1-window-00000001", SESSION_OP, ackPayload);
  assert.equal(replay.replayed, true);
  assert.deepEqual(replay.result, first.result);
  assert.deepEqual([...store.documents].sort(), before.sort());
}));

test("advance rides the same sink: an open question closes nothing and replays its receipt", () => fixed(async () => {
  const {sessionId} = await startedSession();
  // With the captured fixture clock the question is still open, so the server answers the
  // CURRENT_QUESTION no-op and stages its permanent receipt — the third kind's happy path.
  const payload = {kind: "ADVANCE", sessionId, operationId: "sink_advance_op_0000001"};
  const first = await drain("player", "tournament-q0-advance-00000001", SESSION_OP, payload);
  assert.equal(first.replayed, false);
  assert.equal(first.result.status, "CURRENT_QUESTION");
  assert.equal(first.result.session.questionIndex, 0);
  safe(first.result);
  const before = structuredClone([...store.documents]);
  const second = await drain("player", "tournament-q0-advance-00000001", SESSION_OP, payload);
  assert.equal(second.replayed, true);
  assert.deepEqual(second.result, first.result);
  assert.deepEqual([...store.documents].sort(), before.sort());
}));

test("refusals of the controller keep their structured reason through the queue", () => fixed(async () => {
  const {sessionId, questionId} = await startedSession();
  await drain("player", "tournament-q0-answer-00000003", SESSION_OP, {kind: "ANSWER", sessionId,
    operationId: "sink_answer_op_00000003", questionIndex: 0, questionId,
    answerPayload: {type: "single-choice", selected: "b"}});
  // A stale answer targets a closed question: the refusal is final and structural.
  await assert.rejects(
    drain("player", "tournament-q0-stale-00000004", SESSION_OP, {kind: "ANSWER", sessionId,
      operationId: "sink_answer_op_00000004", questionIndex: 0, questionId,
      answerPayload: {type: "single-choice", selected: "a"}}),
    (error) => {
      assert.equal(error.code, "failed-precondition");
      assert.equal(error.details.reason, "QUESTION_ALREADY_CLOSED");
      assert.equal(error.message, "Tournament operation could not be completed");
      return true;
    },
  );
  // Unknown operations stay finally rejected by the receiver before any handler runs.
  await assert.rejects(
    drain("player", "tournament-unknown-00000001", "tournament.UNKNOWN", {kind: "ANSWER", sessionId}),
    {code: "invalid-argument"},
  );
  // A key cannot silently change its frozen body: the receiver refuses the reuse.
  await assert.rejects(
    drain("player", "tournament-q0-answer-00000003", SESSION_OP, {kind: "ANSWER", sessionId,
      operationId: "sink_answer_op_00000003", questionIndex: 0, questionId,
      answerPayload: {type: "single-choice", selected: "b"}, extra: 1}),
    {code: "invalid-argument"},
  );
}));

test("the sink derives the owner from auth and never from the body", () => fixed(async () => {
  const {sessionId, questionId} = await startedSession();
  await assert.rejects(
    drain("author", "tournament-q0-answer-00000005", SESSION_OP, {kind: "ANSWER", sessionId,
      operationId: "sink_answer_op_00000005", questionIndex: 0, questionId,
      answerPayload: {type: "single-choice", selected: "a"}}),
    (error) => {
      assert.equal(error.code, "permission-denied");
      assert.equal(error.details.reason, "PERMISSION_DENIED");
      return true;
    },
  );
}));
