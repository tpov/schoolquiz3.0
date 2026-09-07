"use strict";

// Read-only oracle for the dedicated synthetic UI lesson. No writes, fixture creation or RPC replay.
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const PROJECT = "demo-schoolquiz-mvp";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "schoolquiz-mvp-firebase:8080");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
const admin = require("/repo/functions/node_modules/firebase-admin");
const {CURRENCY, REASON, LEDGER_PATH} = require("/repo/functions/currency-ledger");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const lessonId = "mvp-smoke-v2-lesson";
async function main() {
  const events = await db.collectionGroup("events").where("lessonId", "==", lessonId).limit(20).get();
  assert.ok(!events.empty, "The UI has not delivered a result for this smoke lesson yet");
  assert.ok(events.size < 20, "Use an explicit next oracle scope; do not silently truncate");
  const attempts = [];
  for (const doc of events.docs) {
    assert.match(doc.ref.path, /^result_events_public\/[a-f0-9]{2}\/events\/[^/]+$/);
    const event = doc.data();
    const receipt = event.settlementReceipt;
    assert.ok(receipt, "Stored history alone is not a settlement proof");
    assert.equal(receipt.uid, event.userId);
    assert.equal(receipt.attemptId, event.attemptId);
    assert.equal(receipt.lessonId, lessonId);
    assert.equal(receipt.status, "SETTLED");
    assert.equal(receipt.completed, true);
    assert.equal(receipt.percentScore, 100);
    assert.equal(receipt.plasmaPointsSpent, 0);
    assert.ok(receipt.lifePointsSpent > 0);
    const ledger = await db.collection(LEDGER_PATH).where("sourceId", "==", receipt.attemptId).get();
    const rows = ledger.docs.map(row => ({id: row.id, ...row.data()}));
    for (const row of rows) assert.equal(row.uid, receipt.uid);
    const sum = (reason, currency) => {
      const found = rows.filter(row => row.reason === reason && row.currency === currency);
      assert.ok(found.length <= 1, "Duplicate monetary effect for one attempt");
      return found.reduce((total, row) => total + row.delta, 0);
    };
    assert.equal(sum(REASON.ATTEMPT_TOLL, CURRENCY.STANDARD_CHARGE_POINTS), -receipt.lifePointsSpent);
    assert.equal(sum(REASON.ATTEMPT_REWARD, CURRENCY.NOLICS), receipt.reward.nolics);
    assert.equal(sum(REASON.ATTEMPT_REWARD, CURRENCY.SKILL_POINTS), receipt.reward.skillPoints);
    assert.ok(rows.every(row => [REASON.ATTEMPT_TOLL, REASON.ATTEMPT_REWARD].includes(row.reason)));
    attempts.push({attemptId: receipt.attemptId, uid: receipt.uid, receiptId: receipt.receiptId,
      status: receipt.status, completed: receipt.completed, percentScore: receipt.percentScore,
      settledAtMs: receipt.settledAtMs, lifePointsSpent: receipt.lifePointsSpent,
      reward: receipt.reward, ledgerRows: rows.length,
      evidenceSha256: createHash("sha256").update(JSON.stringify({receipt, rows: rows.sort((a, b) => a.id.localeCompare(b.id))})).digest("hex")});
  }
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, lessonId, writes: 0,
    attempts: attempts.sort((a, b) => a.settledAtMs - b.settledAtMs)}, null, 2));
}
main().catch(error => { console.error(JSON.stringify({status: "FAIL", lessonId, message: error.message})); process.exitCode = 1; });
