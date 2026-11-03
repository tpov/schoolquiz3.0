"use strict";
const Module = require("node:module");
const {randomUUID} = require("node:crypto");
const {createFirestoreStub} = require("/workspace/projects/schoolquiz3.0/.local/worktrees/e5-fixpack/functions/_firestore-stub.js");
const {SINGLE} = require("/workspace/projects/schoolquiz3.0/.local/worktrees/e5-fixpack/functions/_question-fixtures.js");
const {admin, store} = createFirestoreStub({});
const {createRequire} = require("node:module");
const req = createRequire("/workspace/projects/schoolquiz3.0/.local/worktrees/e5-fixpack/functions/index.js");
const adminPath = req.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const NOW = Date.now();
const functions = (() => { const o = Date.now; Date.now = () => NOW; try { return req("/workspace/projects/schoolquiz3.0/.local/worktrees/e5-fixpack/functions/index.js"); } finally { Date.now = o; } })();
const runId = `probe_${randomUUID().replaceAll("-", "")}`;
const call = (name, uid, data) => functions[name].run({auth: {uid}, data});
(async () => {
  const author = {uid: "ProbeAuthorUid0000000000000A"}; const player = {uid: "ProbePlayerUid0000000000000B"};
  const wallet = {boxCount: 100, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
    lifePointsUpdatedAtMs: NOW, plasmaPointsUpdatedAtMs: NOW};
  const rows = [
    ["users/ProbeAuthorUid0000000000000A", wallet], ["users/ProbePlayerUid0000000000000B", wallet],
    ["profiles/ProbeAuthorUid0000000000000A", {uid: "ProbeAuthorUid0000000000000A"}], ["profiles/ProbePlayerUid0000000000000B", {uid: "ProbePlayerUid0000000000000B"}],
    ["catalogs/cat", {questType: "COURSE", version: 1}],
    ["quests/course", {catalogId: "cat", authorUid: "ProbeAuthorUid0000000000000A", version: 1, visibleOn: ["archive"], archived: true}],
    ["sections/sec", {questId: "course", version: 1}], ["themes/th", {sectionId: "sec", version: 1}],
    ["lessons/le", {themeId: "th", version: 1, reviewSubmissionId: "sub", reviewApprovedAtMs: NOW - 1}],
    ["admin/review/lessons/le", {ownerUid: "ProbeAuthorUid0000000000000A", questId: "course", submissionId: "sub", status: "PUBLISHED", sourceLanguages: ["en"]}],
    ...["TESTING", "LOGIC"].map(k => [`admin/review/lessons/le/reviews/${k}`, {kind: k, reviewerUid: k, acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "sub"}]),
    ...Array.from({length: 80}, (_, i) => [`questions/q${i}`, {lessonId: "le", version: 1, language: "en", difficulty: i < 60 ? "EASY" : "HARD", payload: JSON.stringify({...SINGLE, difficulty: i < 60 ? "EASY" : "HARD"})}]),
  ];
  store.reset(Object.fromEntries(rows));
  console.log("step: prepare");
  const prepared = await call("prepareCourseTournamentBank", "ProbeAuthorUid0000000000000A", {requestId: "prepare_probe_000001", source: {kind: "COURSE", id: "course"}});
  console.log("step: create");
  const created = await call("submitCourseTournamentOperation", "ProbeAuthorUid0000000000000A", {kind: "CREATE", operationId: "create_probe_0000001", eventId: "event_probe_000001",
    source: {kind: "COURSE", id: "course", snapshotId: prepared.snapshotId}, startsAtMs: NOW,
    prizes: {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}]}, fundedBoxes: 12});
  const token = created.admissionLink.token;
  
  console.log("step: start");
  const started = await call("submitCourseTournamentOperation", "ProbePlayerUid0000000000000B", {kind: "START_QUALIFICATION", eventId: "event_probe_000001",
    operationId: "start_probe_00000001", attemptId: "attempt_probe_000001", roundNumber: 1, linkToken: token});
  console.log("START ok, question:", started.currentQuestion?.questionId, "session:", started.session?.sessionId);
  const view = await call("fetchCourseTournamentSession", "ProbePlayerUid0000000000000B", {sessionId: started.session.sessionId});
  const payload = {kind: "ANSWER", sessionId: started.session.sessionId, operationId: "sink_answer_000000001",
    questionIndex: 0, questionId: view.currentQuestion.questionId, answerPayload: {type: "single-choice", selected: "b"}};
  try {
    const delivered = await call("submitMutation", "ProbePlayerUid0000000000000B", {mutationId: "probe_mut_answer_0001",
      operation: "tournament.SESSION_OP", payload: JSON.stringify(payload)});
    console.log("SINK ANSWER ok, status:", delivered.result.status, "window:", JSON.stringify(delivered.result.session?.window));
    const ack = {kind: "READY_ACK", sessionId: started.session.sessionId, operationId: "sink_ack_0000000001",
      questionIndex: delivered.result.session.window.nextQuestionIndex, windowId: delivered.result.session.window.windowId};
    try {
      const acked = await call("submitMutation", "ProbePlayerUid0000000000000B", {mutationId: "probe_mut_ack_00000001",
        operation: "tournament.SESSION_OP", payload: JSON.stringify(ack)});
      console.log("SINK ACK ok:", acked.result.status, JSON.stringify(acked.result.readyAck));
    } catch (e) { console.log("SINK ACK FAIL:", e.code, e.message, "reason:", e.reason, JSON.stringify(e.details || {})); }
  } catch (e) { console.log("SINK ANSWER FAIL:", e.code, e.message, "reason:", e.reason, JSON.stringify(e.details || {})); }
})().catch((e) => { console.error("PROBE FAIL:", e.code, e.message, JSON.stringify(e.details || {})); console.error(e.stack); process.exit(1); });
