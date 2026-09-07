"use strict";

// Structural guard only; authenticated emulator probes verify effective access separately.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {PURCHASE_REVOCATIONS_COLLECTION} = require("./purchase-revocations");
const rules = fs.readFileSync(path.join(__dirname, "../firestore.rules"), "utf8");
assert.equal(PURCHASE_REVOCATIONS_COLLECTION, "purchase_revocations");
assert.ok(rules.includes(`match /${PURCHASE_REVOCATIONS_COLLECTION}/{document=**} { allow read, write: if false; }`));
const indexes = JSON.parse(fs.readFileSync(path.join(__dirname, "../firestore.indexes.json"), "utf8"));
assert.ok(!indexes.fieldOverrides.some(row => row.collectionGroup === PURCHASE_REVOCATIONS_COLLECTION && row.ttl === true),
  "Revocation fences are permanent, not expiring request deduplication");
console.log("PASS: permanent purchase revocations have an explicit private storage boundary");
