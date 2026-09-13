"use strict";

// Follow-up to the HTTP flow: no Firestore writes, no callable/scheduler execution.
// Creates one fresh anonymous DEMO Auth account and probes only synthetic document paths.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
const PRIVATE = [
  "course_tournament_events", "course_tournament_operations", "course_tournament_sessions",
  "course_tournament_keys", "course_tournament_funding", "course_tournament_session_operations",
  "course_tournament_answer_ingress", "course_tournament_verified_runs", "course_tournament_rankings",
  "course_tournament_bank_preparations", "course_tournament_source_banks", "course_tournament_worker_state",
];
const runId = `ct_rules_${randomUUID().replaceAll("-", "")}`;
const probes = [
  ...PRIVATE.map(collection => ({label: collection, path: `${collection}/${runId}`})),
  {label: "qualification-calculation-checkpoint",
    path: `course_tournament_events/${runId}/qualification_calculations/${runId}`},
  {label: "qualification-calculation-player",
    path: `course_tournament_events/${runId}/qualification_calculations/${runId}/players/${runId}`},
  {label: "qualification-calculation-session-deduplication",
    path: `course_tournament_events/${runId}/qualification_calculations/${runId}/sessions/${runId}`},
];
async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hub.status, 200);
  const running = await hub.json();
  assert.equal(running.auth.port, 9099); assert.equal(running.firestore.port, 8080);
  const signup = await fetch(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`, {
    method: "POST", headers: {"content-type": "application/json"},
    body: JSON.stringify({returnSecureToken: true}), signal: AbortSignal.timeout(15_000),
  });
  assert.equal(signup.status, 200);
  const actor = await signup.json();
  assert.equal(typeof actor.idToken, "string"); assert.equal(typeof actor.localId, "string");
  const checks = [];
  for (const {label, path} of probes) {
    const response = await fetch(`http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`, {
      headers: {authorization: `Bearer ${actor.idToken}`}, signal: AbortSignal.timeout(15_000),
    });
    await response.arrayBuffer();
    assert.equal(response.status, 403, `${label}: private document probe must be denied`);
    checks.push({pathKind: label, status: response.status});
  }
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, syntheticUid: actor.localId,
    firestoreWrites: 0, probeDocumentsExist: false, checks,
    mountedRulesSha256: createHash("sha256").update(readFileSync("/repo/firestore.rules")).digest("hex")}, null, 2));
}
main().catch((error) => { console.error(JSON.stringify({status: "FAIL", runId, message: error.message})); process.exitCode = 1; });
