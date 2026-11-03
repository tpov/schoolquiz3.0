"use strict";

// Read-only baseline for story 3.7 (arena-quotas-concurrency) against the demo backend:
// both probe players' economy docs, today's random-quota doc, the SELECTED held counters,
// RESERVED tickets and the priced-universe rows the probes depend on. Writes nothing.
//
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp \
//   node scripts/demo-fixtures/arena-quotas-baseline-read.js

const assert = require("node:assert/strict");
const {readFileSync, writeFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:18090", "firestore must be the demo backend");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only read");

const admin = require(path.join(__dirname, "..", "..", "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

const RIG_PLAYER = "ck3DaiqEd7WGcuVikiw22TyF9fS9";
const PLAYER_A = "demoarena_player_a";
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
const BANK_LOW = manifest.banks.find((entry) => entry.id === "arena-bank-low").lessonId;
const BANK_HIGH = manifest.banks.find((entry) => entry.id === "arena-bank-high").lessonId;

const dayId = new Date().toISOString().slice(0, 10);
const {createHash} = require("node:crypto");
const canonical = (value) => {
  if (value === null || typeof value !== "object" || Array.isArray(value)) return value;
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
};
const quotaDocId = (uid) => createHash("sha256").update(JSON.stringify(canonical([uid, dayId]))).digest("hex");

function economy(doc) {
  if (!doc.exists) return null;
  const data = doc.data();
  return {pointsNolics: data.pointsNolics ?? data.nolics ?? null, lifePoints: data.lifePoints ?? null,
    reservedNolics: data.reservedNolics ?? null, reservedLifePoints: data.reservedLifePoints ?? null,
    arenaSelectedHeldCount: data.arenaSelectedHeldCount ?? null,
    lifePointsUpdatedAtMs: data.lifePointsUpdatedAtMs ?? null};
}

(async () => {
  const out = {story: "3.7 arena-quotas baseline (read-only)", utcNow: new Date().toISOString(), dayId, players: {}, quotas: {}, ratings: {}, tickets: {}};

  for (const [name, uid] of [["rigPlayer", RIG_PLAYER], ["playerA", PLAYER_A]]) {
    const user = await db.doc(`users/${uid}`).get();
    out.players[name] = {uid, economy: economy(user)};
    const quota = await db.doc(`arena_ticket_quotas/${quotaDocId(uid)}`).get();
    out.quotas[name] = quota.exists ? {dayId: quota.data().dayId, randomIssued: quota.data().randomIssued} : {doc: "absent"};
    const tickets = await db.collection("arena_entry_tickets").where("uid", "==", uid).get();
    out.tickets[name] = {total: tickets.size,
      reserved: tickets.docs.filter((doc) => doc.data().status === "RESERVED").map((doc) => doc.data().ticketId)};
  }
  for (const [name, lessonId] of [["bank-low", BANK_LOW], ["bank-high", BANK_HIGH]]) {
    const rating = await db.doc(`lesson_ratings/${lessonId}`).get();
    out.ratings[name] = rating.exists ?
      {ratingSum: rating.data().ratingSum, ratingCount: rating.data().ratingCount, ratingVersion: rating.data().ratingVersion} :
      null;
  }
  const constants = await db.doc("economy_constants/global").get();
  out.economyConstantsDoc = constants.exists ? Object.keys(constants.data()) : "absent";

  writeFileSync(path.join(__dirname, "..", "..", "docs", "agents", "evidence", "2026-10-10-arena-quotas", "baseline-read.json"),
    `${JSON.stringify(out, null, 1)}\n`);
  console.log(JSON.stringify(out, null, 1));
})().catch((error) => { console.error(error); process.exitCode = 1; });
