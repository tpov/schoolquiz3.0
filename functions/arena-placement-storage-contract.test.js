"use strict";

// Source guard only. Effective author/player/admin/anonymous access is tested on the emulator.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const rules = fs.readFileSync(path.join(__dirname, "../firestore.rules"), "utf8");
assert.ok(rules.includes("match /lesson_placements/{document=**} { allow read, write: if false; }"),
  "Both placement lastRejection and its history are private server records, not public cards");
assert.ok(!rules.includes("match /lesson_placements/{lessonId}"), "Do not restore the old broad authenticated read");
const indexes = JSON.parse(fs.readFileSync(path.join(__dirname, "../firestore.indexes.json"), "utf8"));
assert.ok(!indexes.fieldOverrides.some(row => ["lesson_placements", "history"].includes(row.collectionGroup) && row.ttl === true),
  "Moderation and placement history must not expire as request deduplication");
console.log("PASS: raw lesson placement and moderation history have a private storage boundary");
