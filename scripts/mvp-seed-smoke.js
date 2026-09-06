"use strict";

// Synthetic UI smoke data, NOT proof of the author/reviewer publication workflow. This writes
// only to the task's isolated demo project and creates a marked fixture once, never overwrites it.
const assert = require("node:assert/strict");
const {createRequire} = require("node:module");
const fromFunctions = createRequire(require.resolve("../functions/package.json"));
const {initializeApp, deleteApp} = fromFunctions("firebase-admin/app");
const {getFirestore, Timestamp} = fromFunctions("firebase-admin/firestore");
const {SINGLE} = require("../functions/_question-fixtures");
const {writeSyncChanges} = require("../functions/sync-changes");
const PROJECT = "demo-schoolquiz-mvp";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "schoolquiz-mvp-firebase:8080");
const app = initializeApp({projectId: PROJECT});
const db = getFirestore(app);
const fixtureId = "mvp-smoke-v1";
const ids = Object.fromEntries(["catalog", "quest", "section", "theme", "lesson"].map(kind => [kind, `${fixtureId}-${kind}`]));

async function seed() {
  const now = Date.now();
  const common = {version: 1, lastModifiedAt: Timestamp.fromMillis(now), archived: false, testFixture: fixtureId};
  const rows = [
    ["catalog", {id: ids.catalog, name: "MVP checks", questType: "REGULAR", iconNames: []}],
    ["quest", {id: ids.quest, catalogId: ids.catalog, authorUid: `${fixtureId}-author`, title: "MVP smoke course", visibleOn: ["home"]}],
    ["section", {id: ids.section, questId: ids.quest, title: "MVP section", order: 0}],
    ["theme", {id: ids.theme, sectionId: ids.section, title: "MVP theme", order: 0}],
    ["lesson", {id: ids.lesson, themeId: ids.theme, title: "MVP quick lesson", order: 0}],
    ...[0, 1].map(order => ["question", {id: `${fixtureId}-q-${order}`, lessonId: ids.lesson,
      text: SINGLE.text, payload: JSON.stringify({...SINGLE, difficulty: "EASY"}), difficulty: "EASY",
      language: "en", languageLevel: 0, order}]),
  ];
  const created = await db.runTransaction(async transaction => {
    const marker = db.doc(`test_fixtures/${fixtureId}`);
    const previous = await transaction.get(marker);
    if (previous.exists) {
      assert.equal(previous.data().fixtureId, fixtureId);
      return false;
    }
    for (const [kind, row] of rows) {
      transaction.create(db.doc(`${kind}s/${row.id}`), {...common, ...row});
      writeSyncChanges(transaction, db, {kind, id: row.id, catalogId: ids.catalog,
        lessonId: ids.lesson, ownerUid: null}, now);
    }
    transaction.create(marker, {fixtureId, projectId: PROJECT, createdAtMs: now, ids,
      purpose: "Synthetic non-Arena UI smoke only; not publication acceptance"});
    return true;
  });
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, created, fixtureId, ids}));
}
seed().catch(error => { console.error(error.message); process.exitCode = 1; }).finally(() => deleteApp(app));
