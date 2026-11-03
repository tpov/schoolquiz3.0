"use strict";

// Read-only final proof for one explicitly named Arena run. It requires both receipts: the
// SETTLED ticket/result and the immutable RATE_LESSON/APPLIED receipt tied to the same attempt.

const {
  ArenaE2eEvidenceError,
  PROJECT,
  checkEnvironment,
  parseCli,
  readOracleEvidence,
} = require("./arena-e2e-evidence");

const USAGE = "node scripts/arena-e2e-receipt-oracle.js --run-id <id> --owner-uid <uid> --ticket-id <sha256> --attempt-id <sha256> --rating-id <id> --rating <1|2|3>";

async function main(argv = process.argv.slice(2), env = process.env) {
  const context = parseCli(argv, {requireRating: true});
  if (context.help) {
    console.log(USAGE);
    return null;
  }
  checkEnvironment(env);
  const admin = require("../functions/node_modules/firebase-admin");
  admin.initializeApp({projectId: PROJECT});
  return readOracleEvidence(admin.firestore(), context);
}

if (require.main === module) {
  main().then((result) => {
    if (result) console.log(JSON.stringify(result, null, 2));
  }).catch((error) => {
    const code = error instanceof ArenaE2eEvidenceError ? error.code : "UNEXPECTED";
    console.error(JSON.stringify({status: "FAIL", oracleVersion: "arena-e2e-receipt-oracle-v1", code, message: error.message}));
    process.exitCode = 1;
  });
}

module.exports = {USAGE, main};
