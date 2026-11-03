#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const {spawnSync} = require("child_process");
const admin = require("../functions/node_modules/firebase-admin");

const REPO_ROOT = path.resolve(__dirname, "..");
const PROJECT_ID = "school-quiz-89336951";
const DEFAULT_SERVICE_ACCOUNT =
  "/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json";
const serviceAccountPath = process.env.SCHOOLQUIZ_FIREBASE_SERVICE_ACCOUNT || DEFAULT_SERVICE_ACCOUNT;
const prefix = process.env.REVIEW_E2E_PREFIX || `pixel_live_review_${Date.now()}`;
const catalogIdOverride = process.env.REVIEW_E2E_CATALOG_ID || null;
const keepPublicFixture = /^(1|true|yes|y)$/i.test(process.env.REVIEW_E2E_KEEP_PUBLIC || "");
const ids = liveIds(prefix, catalogIdOverride);
const assignmentId = `${ids.submissionId}_${ids.lessonId}`;

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});

async function main() {
  if (!fs.existsSync(serviceAccountPath)) {
    throw new Error(`Service account file not found: ${serviceAccountPath}`);
  }
  const serviceAccount = require(serviceAccountPath);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: PROJECT_ID,
  });
  const db = admin.firestore();
  db.settings({ignoreUndefinedProperties: true});

  const serial = process.env.ANDROID_SERIAL || findPixelSerial();
  console.log(`Pixel live review E2E prefix: ${prefix}`);
  console.log(`Pixel serial: ${serial}`);

  const originalConfig = await readOriginalConfig(db);
  try {
    await seedProfilesAndConfig(db);
    const tokens = await createCustomTokens();
    buildApks();
    installApks(serial);
    runInstrumentation(serial, tokens);
  } finally {
    await cleanup(db, originalConfig);
    await cleanupAuthUsers();
    await admin.app().delete();
  }
}

function buildApks() {
  run(
    "./gradlew",
    [
      ":apps:android-next:assembleDebug",
      ":apps:android-next:assembleDebugAndroidTest",
      "--no-configuration-cache",
      "--max-workers=2",
      "-Dorg.gradle.jvmargs=-Xmx10g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8",
    ],
  );
}

function installApks(serial) {
  run("adb", [
    "-s",
    serial,
    "install",
    "-r",
    "-d",
    "apps/android-next/build/outputs/apk/debug/android-next-debug.apk",
  ]);
  run("adb", [
    "-s",
    serial,
    "install",
    "-r",
    "apps/android-next/build/outputs/apk/androidTest/debug/android-next-debug-androidTest.apk",
  ]);
}

function runInstrumentation(serial, tokens) {
  const args = [
    "-s",
    serial,
    "shell",
    "am",
    "instrument",
    "-w",
    "-r",
    "-e",
    "class",
    "com.tpov.schoolquiz.apps.android_next.LiveFirebaseReviewWorkflowInstrumentedTest",
    "-e",
    "reviewE2ePrefix",
    prefix,
    "-e",
    "reviewE2eCatalogId",
    ids.catalogId,
    "-e",
    "reviewE2eOwnerToken",
    tokens.owner,
    "-e",
    "reviewE2eTesterWeakToken",
    tokens.testerWeak,
    "-e",
    "reviewE2eTesterStrongToken",
    tokens.testerStrong,
    "-e",
    "reviewE2eAdminToken",
    tokens.admin,
    "-e",
    "reviewE2eTranslatorWeakToken",
    tokens.translatorWeak,
    "-e",
    "reviewE2eTranslatorToken",
    tokens.translator,
    "-e",
    "reviewE2eTranslationReviewerToken",
    tokens.translationReviewer,
    "com.tpov.schoolquiz.next.test/androidx.test.runner.AndroidJUnitRunner",
  ];
  console.log(`\n$ adb ${redactInstrumentationArgs(args).join(" ")}`);
  const result = spawnSync("adb", args, {
    cwd: REPO_ROOT,
    encoding: "utf8",
    env: process.env,
    maxBuffer: 1024 * 1024 * 20,
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`adb exited with code ${result.status}`);
  }
  if (!/OK \(\d+ tests?\)/.test(result.stdout) || /FAILURES!!!/.test(result.stdout)) {
    throw new Error("Android instrumentation failed");
  }
}

function redactInstrumentationArgs(args) {
  const secretKeys = new Set([
    "reviewE2eOwnerToken",
    "reviewE2eTesterWeakToken",
    "reviewE2eTesterStrongToken",
    "reviewE2eAdminToken",
    "reviewE2eTranslatorWeakToken",
    "reviewE2eTranslatorToken",
    "reviewE2eTranslationReviewerToken",
  ]);
  return args.map((value, index) => (secretKeys.has(args[index - 1]) ? "<redacted>" : value));
}

function run(command, args) {
  console.log(`\n$ ${command} ${args.join(" ")}`);
  const result = spawnSync(command, args, {
    cwd: REPO_ROOT,
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${command} exited with code ${result.status}`);
  }
}

function findPixelSerial() {
  const result = spawnSync("adb", ["devices", "-l"], {
    cwd: REPO_ROOT,
    encoding: "utf8",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr || "adb devices failed");
  const line = result.stdout
    .split(/\r?\n/)
    .find((candidate) => /\bdevice\b/.test(candidate) && /model:Pixel/.test(candidate));
  if (!line) {
    throw new Error(`Pixel device not found in adb devices:\n${result.stdout}`);
  }
  return line.replace(/\s+device\b.*$/, "");
}

async function readOriginalConfig(db) {
  const snapshot = await db.doc("configs/arena_review").get();
  return snapshot.exists ? snapshot.data() : null;
}

async function seedProfilesAndConfig(db) {
  await db.doc("configs/arena_review").set(
    {
      requiredLanguages: ["ru", "en"],
      updatedAtMs: Date.now(),
    },
    {merge: true},
  );
  const profiles = {
    [ids.ownerUid]: profile(),
    [ids.testerWeakUid]: profile({testerLevel: 100}),
    [ids.testerStrongUid]: profile({testerLevel: 250}),
    [ids.adminUid]: profile({adminLevel: 250}),
    [ids.translatorWeakUid]: profile({translatorLevel: 90, knownLanguages: ["ru", "en"]}),
    [ids.translatorUid]: profile({translatorLevel: 180, knownLanguages: ["ru", "en"]}),
    [ids.translationReviewerUid]: profile({translatorLevel: 300, knownLanguages: ["ru", "en"]}),
  };
  const batch = db.batch();
  for (const [uid, data] of Object.entries(profiles)) {
    batch.set(db.doc(`profiles/${uid}`), data, {merge: true});
  }
  await batch.commit();
}

function profile(values = {}) {
  return {
    testerLevel: 0,
    adminLevel: 0,
    translatorLevel: 0,
    developerLevel: 0,
    knownLanguages: [],
    reviewReputation: 0,
    ...values,
  };
}

async function createCustomTokens() {
  return {
    owner: await admin.auth().createCustomToken(ids.ownerUid),
    testerWeak: await admin.auth().createCustomToken(ids.testerWeakUid),
    testerStrong: await admin.auth().createCustomToken(ids.testerStrongUid),
    admin: await admin.auth().createCustomToken(ids.adminUid),
    translatorWeak: await admin.auth().createCustomToken(ids.translatorWeakUid),
    translator: await admin.auth().createCustomToken(ids.translatorUid),
    translationReviewer: await admin.auth().createCustomToken(ids.translationReviewerUid),
  };
}

async function cleanup(db, originalConfig) {
  await deleteIfExists(db, `admin/review/sync_changes/${assignmentId}`);
  await deleteCollection(db, adminLessonPath(), "reviews");
  await deleteCollection(db, adminQuestPath(), "questions");
  await deleteIfExists(db, adminQuestPath());
  await deleteCollection(db, adminLessonPath(), "quests");
  await deleteIfExists(db, adminLessonPath());

  await deleteCollection(db, privateLessonPath(), "questions");
  await deleteIfExists(db, privateLessonPath());
  await deleteCollection(db, privateThemePath(), "lessons");
  await deleteIfExists(db, privateThemePath());
  await deleteCollection(db, privateSectionPath(), "themes");
  await deleteIfExists(db, privateSectionPath());
  await deleteCollection(db, privateQuestPath(), "sections");
  await deleteIfExists(db, privateQuestPath());
  await deleteIfExists(db, `private/${ids.ownerUid}/sync_changes/${ids.catalogId}_${ids.questId}`);
  await deleteCollection(db, `private/${ids.ownerUid}/catalogs/${ids.catalogId}`, "quests");
  await deleteIfExists(db, `private/${ids.ownerUid}/catalogs/${ids.catalogId}`);
  await deleteIfExists(db, `quest_review_requests/${ids.submissionId}`);

  if (!keepPublicFixture) {
    await cleanupPublicPublication(db);
  }

  await Promise.all(userUids().map((uid) => deleteIfExists(db, `profiles/${uid}`)));
  const configRef = db.doc("configs/arena_review");
  if (originalConfig) {
    await configRef.set(originalConfig);
  } else {
    await configRef.delete();
  }
}

async function cleanupPublicPublication(db) {
  await deleteIfExists(db, `lesson_content/${ids.lessonId}/sync_changes/${ids.questionId}`);
  await deleteIfExists(db, `lesson_content/${ids.lessonId}/sync_changes/${ids.hardQuestionId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/question_${ids.questionId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/question_${ids.hardQuestionId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/lesson_${ids.lessonId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/theme_${ids.themeId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/section_${ids.sectionId}`);
  await deleteIfExists(db, `catalogs/${ids.catalogId}/sync_changes/quest_${ids.questId}`);
  await deleteIfExists(db, `questions/${ids.questionId}`);
  await deleteIfExists(db, `questions/${ids.hardQuestionId}`);
  await deleteIfExists(db, `lessons/${ids.lessonId}`);
  await deleteIfExists(db, `themes/${ids.themeId}`);
  await deleteIfExists(db, `sections/${ids.sectionId}`);
  await deleteIfExists(db, `quests/${ids.questId}`);
}

async function cleanupAuthUsers() {
  await Promise.all(
    userUids().map(async (uid) => {
      try {
        await admin.auth().deleteUser(uid);
      } catch (error) {
        if (error && error.code !== "auth/user-not-found") throw error;
      }
    }),
  );
}

async function deleteCollection(db, parentPath, collectionId) {
  const refs = await db.doc(parentPath).collection(collectionId).listDocuments();
  await Promise.all(refs.map((ref) => ref.delete()));
}

async function deleteIfExists(db, documentPath) {
  await db.doc(documentPath).delete();
}

function userUids() {
  return [
    ids.ownerUid,
    ids.testerWeakUid,
    ids.testerStrongUid,
    ids.adminUid,
    ids.translatorWeakUid,
    ids.translatorUid,
    ids.translationReviewerUid,
  ];
}

function adminLessonPath() {
  return `admin/review/lessons/${ids.lessonId}`;
}

function adminQuestPath() {
  return `${adminLessonPath()}/quests/${ids.questId}`;
}

function privateQuestPath() {
  return `private/${ids.ownerUid}/catalogs/${ids.catalogId}/quests/${ids.questId}`;
}

function privateSectionPath() {
  return `${privateQuestPath()}/sections/${ids.sectionId}`;
}

function privateThemePath() {
  return `${privateSectionPath()}/themes/${ids.themeId}`;
}

function privateLessonPath() {
  return `${privateThemePath()}/lessons/${ids.lessonId}`;
}

function liveIds(value, catalogId) {
  return {
    ownerUid: `${value}_owner`,
    testerWeakUid: `${value}_tester_weak`,
    testerStrongUid: `${value}_tester_strong`,
    adminUid: `${value}_admin`,
    translatorWeakUid: `${value}_translator_weak`,
    translatorUid: `${value}_translator`,
    translationReviewerUid: `${value}_translation_reviewer`,
    catalogId: catalogId || `${value}_catalog`,
    questId: `${value}_quest`,
    sectionId: `${value}_section`,
    themeId: `${value}_theme`,
    lessonId: `${value}_lesson`,
    questionId: `${value}_question`,
    hardQuestionId: `${value}_hard_question`,
    submissionId: `${value}_submission`,
  };
}
