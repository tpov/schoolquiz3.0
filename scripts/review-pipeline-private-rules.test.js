"use strict";

// Private DEMO-only access probes for the model review collections (AD-32, story 4.12).
// Creates three fresh Auth identities and three create-only synthetic documents; never deletes,
// resets, overwrites existing fixtures or uses Play.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
const admin = require("/repo/functions/node_modules/firebase-admin");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `rp_rules_${randomUUID().replaceAll("-", "")}`;
const sha = value => createHash("sha256").update(value).digest("hex");
const checks = [];
async function account() {
  const response = await fetch(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`, {
    method: "POST", headers: {"content-type": "application/json"},
    body: JSON.stringify({returnSecureToken: true}), signal: AbortSignal.timeout(15_000),
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(typeof result.idToken, "string");
  assert.equal(typeof result.localId, "string");
  return {uid: result.localId, token: result.idToken};
}
async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hub.status, 200);
  const running = await hub.json();
  assert.equal(running.auth.port, 9099); assert.equal(running.firestore.port, 8080);
  const [author, appAdmin] = await Promise.all([account(), account()]);
  const id = sha(runId);
  // Positive control: the mounted ruleset is live — a public-read path answers 200 to anonymous
  // while the review paths answer 403. A flat all-deny artifact would fail this control.
  const control = await fetch(`http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/catalogs`,
    {signal: AbortSignal.timeout(15_000)});
  assert.equal(control.status, 200, "mounted ruleset must allow the public catalogs read");
  checks.push({path: "catalogs", role: "anonymous", method: "GET", status: control.status});
  const targets = [
    [`review_pipeline/${id}`, {schemaVersion: 1, submissionId: id, status: "RUNNING", syntheticRun: runId}],
    [`review_verdicts/${id}`, {schemaVersion: 1, submissionId: id, stage: "WORDS", syntheticRun: runId}],
    [`review_worker_state/sweep-${id}`, {schemaVersion: 1, lease: null, syntheticRun: runId}],
  ];
  const batch = db.batch();
  for (const [path, document] of targets) batch.create(db.doc(path), document);
  batch.create(db.doc(`profiles/${appAdmin.uid}`), {uid: appAdmin.uid, adminLevel: 100, syntheticRun: runId});
  await batch.commit();
  const before = {};
  for (const [path] of targets) before[path] = (await db.doc(path).get()).data();
  for (const [path] of targets) {
    const url = `http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`;
    for (const [role, actor] of [["author", author], ["appAdmin", appAdmin], ["anonymous", null]]) {
      for (const method of ["GET", "PATCH", "DELETE"]) {
        const response = await fetch(url, {method,
          headers: {"content-type": "application/json", ...(actor ? {authorization: `Bearer ${actor.token}`} : {})},
          ...(method === "PATCH" ? {body: JSON.stringify({fields: {status: {stringValue: "PWNED"}}})} : {}),
          signal: AbortSignal.timeout(15_000)});
        await response.arrayBuffer();
        assert.equal(response.status, 403, `${path}: ${role} ${method} must be denied`);
        checks.push({path: path.split("/")[0], role, method, status: response.status});
      }
    }
  }
  for (const [path] of targets) {
    const after = (await db.doc(path).get()).data();
    assert.deepEqual(after, before[path], `${path} unchanged`);
  }
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, checks,
    createdAuthAccounts: 2, createdFixtureDocuments: targets.length + 1, fixtureDeleted: false,
    mountedRulesSha256: sha(readFileSync("/repo/firestore.rules"))}, null, 2));
}
main().catch(error => { console.error(JSON.stringify({status: "FAIL", runId, message: error.message})); process.exitCode = 1; });
