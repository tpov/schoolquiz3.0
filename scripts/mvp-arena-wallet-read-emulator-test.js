"use strict";

// Synthetic accounts only. Never deletes data, deploys rules or accesses a production endpoint.
const assert = require("node:assert/strict");
const {createHash, randomUUID} = require("node:crypto");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.ok(!process.env.GOOGLE_APPLICATION_CREDENTIALS);
const admin = require("/repo/functions/node_modules/firebase-admin");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `wallet_read_${randomUUID().replaceAll("-", "")}`;
const sha = (value) => createHash("sha256").update(JSON.stringify(value)).digest("hex");
const authUids = [];
const checks = [];
const passed = (name) => { checks.push(name); console.log(JSON.stringify({check: name, status: "PASS"})); };

async function account() {
  const response = await fetch(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`, {
    method: "POST", headers: {"content-type": "application/json"},
    body: JSON.stringify({returnSecureToken: true}), signal: AbortSignal.timeout(30_000),
  });
  assert.equal(response.status, 200);
  const result = await response.json();
  assert.equal(typeof result.localId, "string");
  assert.equal(typeof result.idToken, "string");
  authUids.push(result.localId);
  return {uid: result.localId, token: result.idToken};
}

async function read(uid, token) {
  assert.ok(authUids.includes(uid));
  const response = await fetch(
    `http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/users/${encodeURIComponent(uid)}`,
    {headers: token ? {authorization: `Bearer ${token}`} : {}, signal: AbortSignal.timeout(30_000)},
  );
  return {status: response.status, body: await response.json()};
}

async function state(uid) {
  const user = await db.doc(`users/${uid}`).get();
  const ledger = await db.collection("audit/currency_ledger/entries").where("uid", "==", uid).get();
  const premium = await db.doc(`premium_accounts/${uid}`).get();
  const profile = await db.doc(`profiles/${uid}`).get();
  const plain = (doc) => ({path: doc.ref.path, exists: doc.exists, data: doc.data() ?? null,
    updated: doc.updateTime ? [doc.updateTime.seconds, doc.updateTime.nanoseconds] : null});
  return [user, premium, profile, ...ledger.docs].map(plain).sort((a, b) => a.path.localeCompare(b.path));
}

async function main() {
  const owner = await account();
  const foreign = await account();
  const missing = await account();
  const wallet = {uid: owner.uid, pointsNolics: 70, nolics: 70, reservedNolics: 30,
    lifePoints: 400, reservedLifePoints: 100, lifePointsUpdatedAtMs: 123, syntheticRun: runId};
  const batch = db.batch();
  batch.create(db.doc(`users/${owner.uid}`), wallet);
  batch.create(db.doc(`audit/currency_ledger/entries/${runId}`), {
    uid: owner.uid, currency: "nolics", delta: -30, reason: "synthetic_read_test", sourceId: runId,
  });
  await batch.commit(); // create-only, scoped to the fresh account/run; never overwrite existing state.
  const before = await state(owner.uid);
  const missingBefore = await state(missing.uid);
  const first = await read(owner.uid, owner.token);
  assert.equal(first.status, 200);
  assert.equal(first.body.fields.pointsNolics.integerValue, "70");
  assert.equal(first.body.fields.reservedNolics.integerValue, "30");
  assert.equal(first.body.fields.lifePoints.integerValue, "400");
  assert.equal(first.body.fields.reservedLifePoints.integerValue, "100");
  assert.equal(first.body.fields.lifePointsUpdatedAtMs.integerValue, "123");
  passed("owner reads exact available and held balances without regeneration");
  assert.equal((await read(owner.uid, foreign.token)).status, 403);
  passed("another authenticated owner cannot read this wallet");
  assert.equal((await read(owner.uid, null)).status, 403);
  passed("unauthenticated caller cannot read this wallet");
  assert.equal((await read(missing.uid, missing.token)).status, 404);
  assert.deepEqual(await state(missing.uid), missingBefore);
  passed("missing wallet stays absent; reading does not create a zero profile");
  for (let i = 0; i < 3; i++) assert.deepEqual(await read(owner.uid, owner.token), first);
  const after = await state(owner.uid);
  assert.deepEqual(after, before);
  passed("repeated reads leave wallet, ledger, profile and premium byte-identical");
  console.log(JSON.stringify({runId, checks: checks.length, failed: 0, authUids,
    createdPaths: [`users/${owner.uid}`, `audit/currency_ledger/entries/${runId}`],
    beforeSha256: sha(before), afterSha256: sha(after), fixtureCleanup: "retained; nothing deleted"}));
}

main().catch((error) => { console.error(error.name, error.code || "CHECK_FAILED"); process.exitCode = 1; });
