"use strict";

// Story 5.13 device-proof counterpart driver: the synthetic opponent plays its qualification
// rounds over real HTTP callables against the DEMO rig while the physical device plays the same
// event in its UI. Answers are null (honest zero percent) so ranking distinguishes real play.
// Same conventions and env asserts as the other rig scripts; scoped callables only.
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Real credentials are not permitted");
const [, , eventId, courseId, uid, token] = process.argv;
assert.equal(typeof uid, "string", "uid required");
assert.equal(typeof token, "string", "token required");
assert.equal(typeof eventId, "string", "eventId required");
assert.equal(typeof courseId, "string", "courseId required");

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const id = (name) => `dp_opp_${randomUUID().replaceAll("-", "").slice(0, 16)}_${name}`;

async function post(url, body, tok) {
  for (let attempt = 0; ; attempt++) {
    let response;
    try {
      response = await fetch(url, { method: "POST", headers: { "content-type": "application/json",
        authorization: `Bearer ${tok}` }, body: JSON.stringify(body), signal: AbortSignal.timeout(30_000) });
    } catch (error) {
      if (attempt >= 2 || !["TypeError", "TimeoutError"].includes(error.name)) throw error;
      await delay(500 * (attempt + 1)); continue;
    }
    const json = await response.json().catch(() => ({}));
    if (!response.ok || json.error) {
      const error = new Error(json.error?.message || `HTTP ${response.status}`);
      error.code = json.error?.status; error.reason = json.error?.details?.reason;
      throw error;
    }
    return json.result;
  }
}
function call(endpoint, data) {
  return post(`http://${HOST}:5001/${PROJECT}/us-central1/${endpoint}`, { data }, token);
}

async function main() {
  const download = await call("recordCourseTournamentDownload", { operationId: id("download"), courseId });
  assert.equal(download.courseId, courseId);
  console.log(JSON.stringify({ stage: "download-recorded", courseId: download.courseId }));

  for (let round = 1; round <= 3; round++) {
    const started = await call("submitCourseTournamentOperation", { kind: "START_QUALIFICATION",
      eventId, operationId: id(`start_r${round}`), attemptId: id(`attempt_r${round}`), roundNumber: round });
    assert.equal(started.session.kind, "QUALIFICATION", JSON.stringify(started).slice(0, 200));
    const sessionId = started.session.sessionId;
    let index = 0;
    let current = started.session.questionIndex === 0 ? await call("fetchCourseTournamentSession", { sessionId }) : null;
    for (; index < 20; index++) {
      const view = current ?? await call("fetchCourseTournamentSession", { sessionId });
      assert.ok(view.currentQuestion, `no question at ${index}`);
      const answered = await call("submitCourseTournamentSession", { kind: "ANSWER", sessionId,
        operationId: id(`ans_r${round}_${index}`), questionIndex: view.session.questionIndex,
        questionId: view.currentQuestion.questionId, answerPayload: null });
      assert.ok(["ANSWER_ACCEPTED", "COMPLETED"].includes(answered.status), answered.status);
      current = null;
      await delay(120);
    }
    const final = await call("fetchCourseTournamentSession", { sessionId });
    assert.equal(final.session.status, "SETTLED", JSON.stringify(final.session).slice(0, 200));
    console.log(JSON.stringify({ stage: "round-settled", round, sessionId: sessionId.slice(0, 14) }));
  }
  console.log(JSON.stringify({ status: "OPPONENT_QUALIFIED", rounds: 3 }));
}

main().catch((error) => {
  console.error(JSON.stringify({ status: "FAIL", code: error.code, reason: error.reason, message: error.message }, null, 2));
  process.exitCode = 1;
});
