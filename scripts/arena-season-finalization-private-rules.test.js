"use strict";

// Private DEMO-only rule probes. The script never resets or deletes state. It creates fresh
// anonymous Auth identities and one fresh profile fixture so player, curator, admin and anonymous
// SDK identities all attempt the same server-owned finalization records.

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
const runId = `asf_rules_${randomUUID().replaceAll("-", "")}`;
const probes = [
  `arena_mutation_reservations/${runId}`,
  `arena_season_worker_state/${runId}`,
  `arena_seasons/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/members/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/votes/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/rating_ids/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/completion_ids/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/selected/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/selected_verified/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/aggregates/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/aggregates_verified/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/selection_tree/${runId}`,
  `arena_seasons/${runId}/finalizations/${runId}/aggregate_tree/${runId}`,
];

async function account() {
  const response = await fetch(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`, {
    method: "POST", headers: {"content-type": "application/json"},
    body: JSON.stringify({returnSecureToken: true}), signal: AbortSignal.timeout(15_000),
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(typeof result.idToken, "string"); assert.equal(typeof result.localId, "string");
  return {uid: result.localId, token: result.idToken};
}

async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hub.status, 200);
  const emulators = await hub.json();
  assert.equal(emulators.auth.port, 9099); assert.equal(emulators.firestore.port, 8080);
  const [player, curator, appAdmin] = await Promise.all([account(), account(), account()]);
  await db.doc(`profiles/${curator.uid}`).create({uid: curator.uid, adminLevel: 100, syntheticRun: runId});
  await db.doc(`profiles/${appAdmin.uid}`).create({uid: appAdmin.uid, developerLevel: 101, syntheticRun: runId});
  const checks = [];
  for (const [role, actor] of [["player", player], ["curator", curator], ["appAdmin", appAdmin], ["anonymous", null]]) {
    for (const path of probes) {
      for (const method of ["GET", "PATCH", "DELETE"]) {
        const response = await fetch(`http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`, {
          method, headers: {"content-type": "application/json", ...(actor ? {authorization: `Bearer ${actor.token}`} : {})},
          ...(method === "PATCH" ? {body: JSON.stringify({fields: {forged: {stringValue: runId}}})} : {}),
          signal: AbortSignal.timeout(15_000),
        });
        await response.arrayBuffer();
        assert.equal(response.status, 403, `${role} ${method} ${path} must be denied`);
        checks.push({role, method, pathKind: path.split("/").slice(0, path.startsWith("arena_seasons/") ? 4 : 2).join("/"), status: response.status});
      }
    }
  }
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, checks,
    createdAuthAccounts: 3, createdFixtureDocuments: 2, fixtureDeleted: false,
    mountedRulesSha256: createHash("sha256").update(readFileSync("/repo/firestore.rules")).digest("hex")}, null, 2));
}

main().catch(error => { console.error(JSON.stringify({status: "FAIL", runId, message: error.message})); process.exitCode = 1; });
