"use strict";

/**
 * Moves the quest-rating score off the privilege field it was accidentally sharing.
 *
 * Until this migration, the daily rating aggregator wrote its running total into
 * `profiles/{uid}.developerLevel` and four mirrors on `users/{uid}` — the same field every
 * privilege gate in the product reads at `> 100`. Ten points per three-star rating meant eleven
 * ratings from eleven accounts made an author a developer, and anonymous sign-in makes those
 * accounts free. `functions/index.js` now writes `authorRatingScore` instead; this script repairs
 * the data that the old writer left behind.
 *
 * Three things happen, and the third is the one that is easy to forget:
 *
 *   1. Every `developer` / `developerLevel` value written by ratings is zeroed. There is no way to
 *      tell a farmed value from a deliberate one in the data, because nothing ever recorded who
 *      set it — so every value is treated as farmed. Pass --keep to spare specific uids.
 *   2. The nested mirrors on `users/{uid}` — `qualifications.developer`, `qualification.developer`
 *      and `qualification.developerLevel` — are zeroed too. Missing these leaves the old number
 *      readable by anything that consults the mirrors.
 *   3. `qualificationAppliedScore` is reset to 0 on every quest rating aggregate. That field is the
 *      aggregator's record of what it has already paid out; leaving it alone would make the next
 *      run compute a delta against the old payout and refuse to re-accumulate the score into
 *      `authorRatingScore`. Skip this step and the authors silently lose their reputation.
 *
 * Dry run by default. Nothing is written without --apply.
 *
 *   node scripts/migrate-author-rating-score.js --key <service-account.json>
 *   node scripts/migrate-author-rating-score.js --key <service-account.json> --apply
 *   node scripts/migrate-author-rating-score.js --key <service-account.json> --apply --keep uid1,uid2
 */

const admin = require("firebase-admin");

const args = process.argv.slice(2);
const APPLY = args.includes("--apply");
const keyIndex = args.indexOf("--key");
const KEY_PATH = keyIndex >= 0 ? args[keyIndex + 1] : process.env.GOOGLE_APPLICATION_CREDENTIALS;
const keepIndex = args.indexOf("--keep");
const KEEP = new Set(keepIndex >= 0 ? (args[keepIndex + 1] || "").split(",").filter(Boolean) : []);

if (!KEY_PATH) {
  console.error("Need a service account: --key <path.json>, or GOOGLE_APPLICATION_CREDENTIALS.");
  process.exit(1);
}

admin.initializeApp({credential: admin.credential.cert(require(KEY_PATH))});
const db = admin.firestore();

const BATCH_LIMIT = 400;

/** Runs `mutate(doc, batch)` over a whole collection, committing in batches. */
async function sweep(collectionName, mutate) {
  const snapshot = await db.collection(collectionName).get();
  let batch = db.batch();
  let pending = 0;
  let touched = 0;
  for (const doc of snapshot.docs) {
    if (!mutate(doc, batch)) continue;
    touched += 1;
    pending += 1;
    if (pending >= BATCH_LIMIT && APPLY) {
      await batch.commit();
      batch = db.batch();
      pending = 0;
    }
  }
  if (pending > 0 && APPLY) await batch.commit();
  return {scanned: snapshot.size, touched};
}

async function main() {
  console.log(APPLY ? "APPLYING CHANGES" : "DRY RUN — nothing will be written");
  if (KEEP.size > 0) console.log(`Sparing ${KEEP.size} uid(s): ${Array.from(KEEP).join(", ")}`);
  console.log("");

  const survivors = [];

  const profiles = await sweep("profiles", (doc, batch) => {
    const data = doc.data() || {};
    const level = Number(data.developerLevel) || 0;
    if (level === 0) return false;
    if (KEEP.has(doc.id)) {
      survivors.push(`${doc.id} developerLevel=${level}`);
      return false;
    }
    if (level > 100) console.log(`  profiles/${doc.id} was ABOVE the gate: ${level}`);
    batch.set(doc.ref, {developerLevel: 0}, {merge: true});
    return true;
  });
  console.log(`profiles: ${profiles.touched} zeroed of ${profiles.scanned} scanned`);

  const users = await sweep("users", (doc, batch) => {
    const data = doc.data() || {};
    const q = data.qualifications || {};
    const q2 = data.qualification || {};
    const anySet =
      (Number(data.developer) || 0) > 0 ||
      (Number(q.developer) || 0) > 0 ||
      (Number(q2.developer) || 0) > 0 ||
      (Number(q2.developerLevel) || 0) > 0;
    if (!anySet) return false;
    if (KEEP.has(doc.id)) return false;
    batch.set(
      doc.ref,
      {
        developer: 0,
        qualifications: {developer: 0},
        qualification: {developer: 0, developerLevel: 0},
      },
      {merge: true},
    );
    return true;
  });
  console.log(`users: ${users.touched} zeroed of ${users.scanned} scanned`);

  // Step 3 — without this the score never re-accumulates into the new field.
  for (const scope of ["public", "private"]) {
    const name = `quest_rating_aggregates_${scope}`;
    const aggregates = await sweep(name, (doc, batch) => {
      const applied = Number((doc.data() || {}).qualificationAppliedScore) || 0;
      if (applied === 0) return false;
      batch.set(doc.ref, {qualificationAppliedScore: 0}, {merge: true});
      return true;
    });
    console.log(`${name}: ${aggregates.touched} reset of ${aggregates.scanned} scanned`);
  }

  if (survivors.length > 0) {
    console.log("\nSpared, unchanged:");
    for (const line of survivors) console.log(`  ${line}`);
  }

  console.log(
    APPLY
      ? "\nDone. Grant the first developer level by hand now — nothing else can."
      : "\nDry run finished. Re-run with --apply to write.",
  );
  process.exit(0);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
