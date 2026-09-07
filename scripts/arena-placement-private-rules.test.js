"use strict";

// Private DEMO only. Retains fresh fixtures, including on failure; no cleanup or foreign writes.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
assert.equal(process.env.FIREBASE_CONFIG, undefined);
const admin = require("/repo/functions/node_modules/firebase-admin");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `ap_rules_${randomUUID().replaceAll("-", "")}`;
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
  const services = await hub.json();
  assert.equal(services.auth.port, 9099); assert.equal(services.firestore.port, 8080);
  const [owner, outsider, appAdmin] = await Promise.all([account(), account(), account()]);
  const placement = db.doc(`lesson_placements/${runId}`);
  const history = placement.collection("history").doc(`${runId}_decision`);
  const batch = db.batch();
  batch.create(placement, {lessonId: runId, authorUid: owner.uid, shelf: "review", syntheticRun: runId,
    lastRejection: {code: "SYNTHETIC_REFUSAL", reason: "Private synthetic moderation detail", actorUid: appAdmin.uid}});
  batch.create(history, {actorUid: appAdmin.uid, mutationId: `${runId}_mutation`, status: "REJECTED",
    reason: "Private synthetic moderation detail", fromShelf: "review", targetShelf: "arena", syntheticRun: runId});
  batch.create(db.doc(`profiles/${appAdmin.uid}`), {uid: appAdmin.uid, adminLevel: 100, syntheticRun: runId});
  await batch.commit();
  const refs = [placement, history];
  const before = await db.getAll(...refs);
  for (const [role, actor] of [["author", owner], ["otherPlayer", outsider], ["appAdmin", appAdmin], ["anonymous", null]]) {
    for (const ref of refs) {
      for (const method of ["GET", "PATCH", "DELETE"]) {
        const url = `http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${ref.path}`;
        const response = await fetch(url, {method,
          headers: {"content-type": "application/json", ...(actor ? {authorization: `Bearer ${actor.token}`} : {})},
          ...(method === "PATCH" ? {body: JSON.stringify({fields: {shelf: {stringValue: "home"}}})} : {}),
          signal: AbortSignal.timeout(15_000)});
        await response.arrayBuffer();
        assert.equal(response.status, 403, `${role} ${method} ${ref.path} must be denied`);
        checks.push({role, method, path: ref.path, status: response.status});
      }
    }
  }
  const after = await db.getAll(...refs);
  before.forEach((doc, index) => {
    assert.deepEqual(after[index].data(), doc.data());
    assert.ok(after[index].updateTime.isEqual(doc.updateTime), "Denied probes must leave exact source records unchanged");
  });
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, checks,
    createdAuthAccounts: 3, createOnlyFixtureDocuments: 3, fixtureDeleted: false,
    sourceDataAndUpdateTimeUnchanged: true,
    mountedRulesSha256: sha(readFileSync("/repo/firestore.rules"))}, null, 2));
}
main().catch(error => {
  console.error(JSON.stringify({status: "FAIL", runId, message: error.message, checks}));
  process.exitCode = 1;
});
