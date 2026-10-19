"use strict";
/* AD-29 category seed: the «Приватные квизы» catalog on the demo backend.
 * Idempotent: create only if absent; an existing doc is left as is (named refusal
 * on divergence would need a full spec — this seed owns ONLY its own doc id).
 * Story 8.3 blocker removal: the authoring drawer lists catalogs, the category
 * was absent from all 53 demo docs (evidence 2026-10-18-samsung-social).
 */
const path = require("node:path");
const admin = require(path.join("/repo", "functions", "node_modules", "firebase-admin"));
admin.initializeApp({projectId: "demo-schoolquiz-mvp"});
const db = admin.firestore();
const DOC_ID = "private-quizzes-v1-catalog";
const NAME = "Приватные квизы";
(async () => {
  const ref = db.collection("catalogs").doc(DOC_ID);
  const cur = await ref.get();
  if (cur.exists) {
    const v = cur.data();
    if (v.name !== NAME) { console.error("DIVERGED:", JSON.stringify(v)); process.exit(1); }
    console.log("ALREADY SEEDED:", DOC_ID);
    return;
  }
  const now = Date.now();
  await ref.set({
    id: DOC_ID,
    name: NAME,
    questType: "REGULAR",
    version: 1,
    archived: false,
    iconNames: [],
    lastModifiedAt: { _seconds: Math.floor(now / 1000), _nanoseconds: (now % 1000) * 1e6 },
  });
  console.log("SEEDED:", DOC_ID, "->", NAME);
  await admin.app().delete();
})().catch((e) => { console.error("ERR", e.message); process.exit(1); });
