"use strict";

/**
 * Prepares the live project for the six-device lifecycle run, and grants each device its role.
 *
 * Two things this fixes that nothing else writes:
 *
 *   1. `catalogs/{id}.questType`. `QuestType.kt` documents that the type lives on the catalog, but
 *      no Cloud Function, seed script or backfill ever writes it — so `FirestoreCatalogDtoMapper`
 *      always falls back to REGULAR against real data, and a course would publish to the arena.
 *   2. `configs/arena_review`. Only test harnesses write it today. Absent, `requiredLanguages`
 *      silently falls back to the quest's own source language and the translation stage never
 *      binds — which is fine for the six-device run, and is why it is seeded empty by default.
 *
 * Roles are granted here because nothing in the app can grant them (see SPEC CAP-7): the
 * qualification-granting callable is a separate workstream, so until it lands the admin SDK is
 * the only path.
 *
 * Dry run by default. Nothing is written without --apply.
 *
 *   node scripts/devices/prepare-run.js --key <service-account.json>
 *   node scripts/devices/prepare-run.js --key <sa.json> --apply
 *   node scripts/devices/prepare-run.js --key <sa.json> --apply --roles author=<uid>,tester=<uid>
 *
 * Role names: author, player, playerTwo, tester, admin, developer.
 * Get the uids with scripts/devices/uid.sh while the devices are plugged in.
 */

const admin = require("firebase-admin");

const args = process.argv.slice(2);
const APPLY = args.includes("--apply");
const valueOf = (flag) => {
  const i = args.indexOf(flag);
  return i >= 0 ? args[i + 1] : null;
};
const KEY_PATH = valueOf("--key") || process.env.GOOGLE_APPLICATION_CREDENTIALS;
const ROLES_ARG = valueOf("--roles") || "";

if (!KEY_PATH) {
  console.error("Need a service account: --key <path.json>, or GOOGLE_APPLICATION_CREDENTIALS.");
  process.exit(1);
}

/** What each device's account must hold for its part of the run. */
const ROLE_LEVELS = {
  author: {},
  player: {},
  playerTwo: {},
  tester: {testerLevel: 100},
  admin: {adminLevel: 100},
  // Above the gate, which is `> 100` for developers and `>= 100` for everyone else. Granting
  // exactly 100 here would silently do nothing.
  developer: {developerLevel: 300},
};

/** Which catalog is which kind of content. Only `courses` is a course; the rest are regular. */
const CATALOG_TYPES = {
  school: "REGULAR",
  courses: "COURSE",
  surveys: "SURVEY",
  games: "REGULAR",
  quests: "REGULAR",
};

const CATALOG_NAMES = {
  school: "Школа",
  courses: "Курсы",
  surveys: "Опросы",
  games: "Игры",
  quests: "Квесты",
};

admin.initializeApp({credential: admin.credential.cert(require(KEY_PATH))});
const db = admin.firestore();

function parseRoles(raw) {
  const roles = {};
  for (const pair of raw.split(",").map((s) => s.trim()).filter(Boolean)) {
    const [role, uid] = pair.split("=").map((s) => s.trim());
    if (!ROLE_LEVELS[role]) {
      console.error(`Unknown role "${role}". Known: ${Object.keys(ROLE_LEVELS).join(", ")}`);
      process.exit(1);
    }
    if (!uid) {
      console.error(`Role "${role}" has no uid.`);
      process.exit(1);
    }
    roles[role] = uid;
  }
  return roles;
}

async function main() {
  console.log(APPLY ? "APPLYING CHANGES" : "DRY RUN — nothing will be written");
  console.log(`Project: ${require(KEY_PATH).project_id}\n`);

  for (const [id, questType] of Object.entries(CATALOG_TYPES)) {
    const snapshot = await db.collection("catalogs").doc(id).get();
    const current = snapshot.exists ? (snapshot.data() || {}).questType : undefined;
    if (current === questType) {
      console.log(`  catalogs/${id}: already ${questType}`);
      continue;
    }
    console.log(`  catalogs/${id}: ${current || "(unset)"} -> ${questType}`);
    if (!APPLY) continue;
    await db.collection("catalogs").doc(id).set(
      {
        id,
        name: CATALOG_NAMES[id],
        questType,
        lastModifiedAt: admin.firestore.Timestamp.now(),
      },
      {merge: true},
    );
  }

  const configRef = db.doc("configs/arena_review");
  const configSnapshot = await configRef.get();
  if (configSnapshot.exists) {
    console.log("\n  configs/arena_review: present, left alone");
  } else {
    console.log("\n  configs/arena_review: absent -> seeding with no required languages");
    if (APPLY) {
      await configRef.set({requiredLanguages: [], updatedAtMs: Date.now()});
    }
  }

  const roles = parseRoles(ROLES_ARG);
  if (Object.keys(roles).length === 0) {
    console.log("\n  No --roles given, so no qualifications granted.");
  } else {
    console.log("");
    for (const [role, uid] of Object.entries(roles)) {
      const levels = ROLE_LEVELS[role];
      const shown = Object.keys(levels).length > 0 ? JSON.stringify(levels) : "(no qualification)";
      console.log(`  ${role.padEnd(11)} ${uid} -> ${shown}`);
      if (!APPLY || Object.keys(levels).length === 0) continue;
      // profiles/ is what every server-side gate reads; users/ carries the client-facing mirror
      // under its own short names, so both must move together or the menu and the callable disagree.
      await db.collection("profiles").doc(uid).set({uid, ...levels}, {merge: true});
      const mirror = {};
      if (levels.testerLevel) mirror.tester = levels.testerLevel;
      if (levels.adminLevel) mirror.admin = levels.adminLevel;
      if (levels.developerLevel) mirror.developer = levels.developerLevel;
      await db.collection("users").doc(uid).set(
        {uid, ...mirror, qualifications: mirror, qualification: {...mirror, ...levels}},
        {merge: true},
      );
    }
  }

  console.log(APPLY ? "\nDone." : "\nDry run finished. Re-run with --apply to write.");
  process.exit(0);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
