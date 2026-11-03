"use strict";
/* Proper premium fixture: issue a REAL GRANT lot through the server own code.
 * Mirrors applyBoxOperation: readPremiumEconomy -> planPremiumEconomy(GRANT) ->
 * writePremiumEconomy + userPatch merge, one transaction, honest ledger.
 * Usage: node premium-grant-fixture.js <uid> <days> <sourceId>
 */
const path = require("node:path");
const admin = require(path.join("/repo", "functions", "node_modules", "firebase-admin"));
const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy} = require("/repo/functions/premium-economy.js");
const {readEconomyConstants} = require("/repo/functions/economy-constants.js");

admin.initializeApp({projectId: "demo-schoolquiz-mvp"});
const db = admin.firestore();
const [uid, daysArg, sourceId] = process.argv.slice(2);
const days = Number(daysArg);
if (!uid || !Number.isInteger(days) || ![1, 7, 30, 365].includes(days) || !sourceId) {
  console.error("usage: premium-grant-fixture.js <uid> <days:1|7|30|365> <sourceId>"); process.exit(2);
}
(async () => {
  const constantsDoc = await db.doc("configs/economy").get()
    .catch(() => null);
  let constants;
  if (constantsDoc && constantsDoc.exists) {
    constants = readEconomyConstants(constantsDoc.data(), constantsDoc.updateTime ? constantsDoc.updateTime.toMillis() : 0);
  } else {
    constants = readEconomyConstants(null);
  }
  const now = Date.now();
  const plan = await db.runTransaction(async (transaction) => {
    const userSnap = await transaction.get(db.collection("users").doc(uid));
    const userData = userSnap.data() || {};
    const operation = {kind: "GRANT", source: "GOLD", sourceId, days};
    const snapshot = await readPremiumEconomy({db, transaction, uid, operation});
    const planned = planPremiumEconomy({snapshot, userData, constants, now, operation});
    if (planned.caughtUp) {
      writePremiumEconomy({db, transaction, uid, plan: planned});
      transaction.set(db.collection("users").doc(uid), {uid, ...planned.userPatch}, {merge: true});
    }
    return planned;
  });
  console.log("outcome:", plan.outcome, "caughtUp:", plan.caughtUp,
    "premiumUntilMs:", plan.premiumUntilMs, "totalRemaining:", plan.header.totalRemainingMs,
    "grantedMs:", plan.premiumGrantedMs);
  if (!plan.caughtUp) { console.error("NOT caught up: retry the same sourceId"); process.exit(1); }
  await admin.app().delete();
})().catch((e) => { console.error("ERR", e.message); process.exit(1); });
