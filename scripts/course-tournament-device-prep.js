"use strict";

// Story 5.13 device-proof prep against this task's private DEMO emulators only. Creates the
// fixtures the physical-device journeys need: the DEVICE account's own published course
// (organizer eligibility requires the caller to own it) and a synthetic opponent with a wallet
// (championship counterpart driven over real HTTP). Never clears a database, overwrites an
// existing fixture, or touches a production callable. Retained synthetic fixtures, uniquely
// prefixed run — same conventions as course-tournament-http.test.js.
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Real credentials are not permitted");
const deviceUid = process.argv[2];
assert.ok(typeof deviceUid === "string" && /^[a-zA-Z0-9:_-]{6,128}$/.test(deviceUid), "device uid argument required");
const admin = require("/repo/functions/node_modules/firebase-admin");
const { SINGLE } = require("/repo/functions/_question-fixtures");
admin.initializeApp({ projectId: PROJECT });
const db = admin.firestore();
const runId = `dp_${randomUUID().replaceAll("-", "")}`;
const id = (name) => `${runId}_${name}`;
const names = Object.fromEntries(["catalog", "course", "section", "theme", "lesson", "submission"]
  .map((name) => [name, id(name)]));
const now = Date.now();

async function main() {
  // The device account: wallet create-only; an app-bootstrapped wallet is kept unless the
  // CREATE deposit (12 boxes) cannot be covered — then the demo wallet is topped up and the
  // deviation is reported, never silently.
  const walletFields = { nolics: 1000, pointsNolics: 1000, standardHearts: 5, goldHearts: 1,
    lifePoints: 500, plasmaPoints: 100, lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now };
  const wallet = {};
  const deviceUser = await db.doc(`users/${deviceUid}`).get();
  if (!deviceUser.exists) {
    await db.doc(`users/${deviceUid}`).create({ ...walletFields, boxCount: 100, uid: deviceUid, testFixture: runId });
    wallet.device = "created boxCount 100";
  } else {
    const current = deviceUser.data().boxCount ?? 0;
    assert.ok(Number.isSafeInteger(current), "Existing device wallet boxCount must be an integer");
    if (current < 12) {
      await db.doc(`users/${deviceUid}`).update({ boxCount: 100, testFixture: runId });
      wallet.device = `topped-up from ${current} to 100`;
    } else {
      wallet.device = `kept existing boxCount ${current}`;
    }
  }
  if (!(await db.doc(`profiles/${deviceUid}`).get()).exists) {
    await db.doc(`profiles/${deviceUid}`).create({ uid: deviceUid, testFixture: runId });
  }

  // The opponent: a fresh anonymous Auth emulator account with its own wallet and profile.
  const signup = await fetch(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`,
    { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true }), signal: AbortSignal.timeout(15_000) });
  assert.equal(signup.ok, true, "Auth emulator signUp failed");
  const opponent = await signup.json();
  assert.equal(typeof opponent.localId, "string");
  const batch = db.batch();
  batch.create(db.doc(`users/${opponent.localId}`),
    { ...walletFields, boxCount: 100, uid: opponent.localId, testFixture: runId });
  batch.create(db.doc(`profiles/${opponent.localId}`), { uid: opponent.localId, testFixture: runId });

  // The device-owned published course (80 questions: 60 EASY + 20 HARD), hierarchy identical
  // to the HTTP-acceptance fixture so the real trusted loader accepts it.
  const rows = [
    [`catalogs/${names.catalog}`, { questType: "COURSE", version: 1, testFixture: runId }],
    [`quests/${names.course}`, { catalogId: names.catalog, authorUid: deviceUid, version: 1,
      visibleOn: ["archive"], archived: true, testFixture: runId }],
    [`sections/${names.section}`, { questId: names.course, version: 1, testFixture: runId }],
    [`themes/${names.theme}`, { sectionId: names.section, version: 1, testFixture: runId }],
    [`lessons/${names.lesson}`, { themeId: names.theme, version: 1, reviewSubmissionId: names.submission,
      reviewApprovedAtMs: now - 1000, testFixture: runId }],
    [`admin/review/lessons/${names.lesson}`, { ownerUid: deviceUid, questId: names.course,
      submissionId: names.submission, status: "PUBLISHED", sourceLanguages: ["en"], testFixture: runId }],
    ...["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${names.lesson}/reviews/${id(kind)}`, {
      kind, reviewerUid: id(`reviewer_${kind}`), acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100,
      submissionId: names.submission, testFixture: runId,
    }]),
    ...Array.from({ length: 80 }, (_, index) => [`questions/${id(`q${String(index).padStart(3, "0")}`)}`, {
      lessonId: names.lesson, version: 1, language: "en",
      difficulty: index < 60 ? "EASY" : "HARD", testFixture: runId,
      payload: JSON.stringify({ ...SINGLE, text: `${SINGLE.text} ${"Device proof synthetic fixture. ".repeat(12)}`,
        difficulty: index < 60 ? "EASY" : "HARD" }),
    }]),
  ];
  rows.forEach(([path, body]) => { assert.equal(body.testFixture, runId); batch.create(db.doc(path), body); });
  await batch.commit();

  console.log(JSON.stringify({ status: "READY", runId, projectId: PROJECT,
    fixtureNames: names, deviceUid, opponentUid: opponent.localId,
    opponentToken: opponent.idToken, wallet, createdDocuments: rows.length }, null, 2));
}

main().catch((error) => {
  console.error(JSON.stringify({ status: "FAIL", code: error.code, message: error.message }, null, 2));
  process.exitCode = 1;
}).finally(() => admin.app().delete());
