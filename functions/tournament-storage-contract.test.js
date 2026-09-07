"use strict";

// Configuration regression guard, not a substitute for authenticated Firestore emulator tests.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {TOURNAMENT_PATHS} = require("./tournament-service");
const {SESSION_OPERATIONS, SESSION_INGRESS, VERIFIED_RUNS} = require("./tournament-session");
const {QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {SOURCE_BANKS, PREPARATIONS} = require("./tournament-source-bank");
const {WORKER_STATE_COLLECTION, FUNCTION_TIMEOUT_MS, LEASE_MS} = require("./tournament-worker");
const rules = fs.readFileSync(path.join(__dirname, "../firestore.rules"), "utf8");
const indexes = JSON.parse(fs.readFileSync(path.join(__dirname, "../firestore.indexes.json"), "utf8"));
const privateCollections = [...Object.values(TOURNAMENT_PATHS), SESSION_OPERATIONS, SESSION_INGRESS,
  VERIFIED_RUNS, QUALIFICATION_RANKINGS, SOURCE_BANKS, PREPARATIONS, WORKER_STATE_COLLECTION];

for (const collection of privateCollections) {
  assert.match(collection, /^course_tournament_[a-z_]+$/);
  assert.ok(rules.includes(`match /${collection}/{document=**} { allow read, write: if false; }`),
    `Missing explicit private boundary: ${collection}`);
}
for (const fieldPath of ["bankJson", "proofJson"]) {
  const matches = indexes.fieldOverrides.filter(row => row.collectionGroup === SOURCE_BANKS && row.fieldPath === fieldPath);
  assert.equal(matches.length, 1);
  assert.deepEqual(matches[0].indexes, []);
}
console.log(`PASS: ${privateCollections.length} explicit tournament private boundaries; immutable bank JSON not indexed`);
const indexSource = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
assert.match(indexSource, /exports\.reconcileCourseTournaments = onSchedule\(\{/);
assert.match(indexSource, /timeoutSeconds: FUNCTION_TIMEOUT_MS \/ 1000/);
assert.ok(LEASE_MS > FUNCTION_TIMEOUT_MS);
assert.ok(!indexes.fieldOverrides.some(row => row.collectionGroup === TOURNAMENT_PATHS.events &&
  row.fieldPath === "status" && Array.isArray(row.indexes) && row.indexes.length === 0));
