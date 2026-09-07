"use strict";

// Private DEMO-only access probes. Creates two fresh Auth identities and two create-only
// synthetic documents; never deletes, resets, overwrites existing fixtures or uses Play.
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
const runId = `pr_rules_${randomUUID().replaceAll("-", "")}`;
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
  const [owner, appAdmin] = await Promise.all([account(), account()]);
  const id = sha(runId);
  const ref = db.doc(`purchase_revocations/${id}`);
  const batch = db.batch();
  batch.create(ref, {syntheticRun: runId, ownerUid: owner.uid, schemaVersion: 1,
    settlementId: id, state: "PENDING_VERIFICATION"});
  batch.create(db.doc(`profiles/${appAdmin.uid}`), {uid: appAdmin.uid, adminLevel: 100, syntheticRun: runId});
  await batch.commit();
  const before = await ref.get();
  const url = `http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${ref.path}`;
  for (const [role, actor] of [["buyer", owner], ["appAdmin", appAdmin], ["anonymous", null]]) {
    for (const method of ["GET", "PATCH", "DELETE"]) {
      const response = await fetch(url, {method,
        headers: {"content-type": "application/json", ...(actor ? {authorization: `Bearer ${actor.token}`} : {})},
        ...(method === "PATCH" ? {body: JSON.stringify({fields: {state: {stringValue: "CLEARED_BY_CLIENT"}}})} : {}),
        signal: AbortSignal.timeout(15_000)});
      await response.arrayBuffer();
      assert.equal(response.status, 403, `${role} ${method} must be denied`);
      checks.push({role, method, status: response.status});
    }
  }
  const after = await ref.get();
  assert.deepEqual(after.data(), before.data());
  assert.ok(after.updateTime.isEqual(before.updateTime), "Rejected probes must not mutate the fence");
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, checks,
    createdAuthAccounts: 2, createdFixtureDocuments: 2, fixtureDeleted: false,
    beforeSha256: sha(JSON.stringify(before.data())), afterSha256: sha(JSON.stringify(after.data())),
    mountedRulesSha256: sha(readFileSync("/repo/firestore.rules"))}, null, 2));
}
main().catch(error => { console.error(JSON.stringify({status: "FAIL", runId, message: error.message})); process.exitCode = 1; });
