#!/usr/bin/env node
"use strict";

const fs = require("fs");
const http = require("http");
const crypto = require("crypto");
const path = require("path");
const {spawn, spawnSync} = require("child_process");
const admin = require("firebase-admin");
const {remote} = require("webdriverio");

const E2E_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(E2E_ROOT, "../..");
const PROJECT_ID = process.env.SCHOOLQUIZ_FIREBASE_PROJECT_ID || "school-quiz-89336951";
const DEFAULT_SERVICE_ACCOUNT =
  "/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json";
const SERVICE_ACCOUNT_PATH = process.env.SCHOOLQUIZ_FIREBASE_SERVICE_ACCOUNT || DEFAULT_SERVICE_ACCOUNT;
const PACKAGE_NAME = "com.tpov.schoolquiz.next";
const APP_ACTIVITY = "com.tpov.schoolquiz.apps.android_next.MainActivity";
const APPIUM_HOST = process.env.APPIUM_HOST || "127.0.0.1";
const APPIUM_PORT = Number(process.env.APPIUM_PORT || 4723);
const APPIUM_SYSTEM_PORT = optionalNumber(process.env.APPIUM_SYSTEM_PORT);
const APPIUM_URL = `http://${APPIUM_HOST}:${APPIUM_PORT}`;
const PREFIX = safeId(process.env.APPIUM_E2E_PREFIX || `appium_sync_rating_${Date.now()}`);
const FIXTURE_PREFIX = safeId(process.env.APPIUM_E2E_FIXTURE_PREFIX || PREFIX);
const FIXTURE_KIND = safeId(process.env.APPIUM_E2E_FIXTURE_KIND || "seeded-public").toLowerCase();
const PROFILE_ROLE = safeId(process.env.APPIUM_E2E_PROFILE_ROLE || "developer").toLowerCase();
const APP_PATH = path.resolve(
  REPO_ROOT,
  process.env.APP_PATH || "apps/android-next/build/outputs/apk/debug/android-next-debug.apk",
);

const shouldBuild = !envBool("SKIP_BUILD", false);
const shouldClearAppData = envBool("CLEAR_APP_DATA", true);
const shouldKeepFixture = envBool("KEEP_E2E_FIXTURE", false);
const shouldKeepFailedFixture = envBool("KEEP_FAILED_FIXTURE", true);
const shouldCaptureSteps = envBool("CAPTURE_STEPS", true);
const shouldSeedPublicFixture = !envBool("SKIP_PUBLIC_SEED", false);
const shouldVerifyServerSync = envBool("VERIFY_SERVER_SYNC", true);
const shouldVerifyRatingAggregate = envBool("VERIFY_RATING_AGGREGATE", true);
const HARD_RESULT_REWARD_MULTIPLIER = 2;
const NOLICS_PERCENT_STEP = 10;
const QUEST_RATING_QUALIFICATION_POINTS_PER_STAR = 10;

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
  });

async function main() {
  if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    throw new Error(`Service account file not found: ${SERVICE_ACCOUNT_PATH}`);
  }

  const fixture = makeFixture(FIXTURE_PREFIX);
  const artifactDir = path.join(E2E_ROOT, "artifacts", PREFIX);
  fs.mkdirSync(artifactDir, {recursive: true});

  const serviceAccount = require(SERVICE_ACCOUNT_PATH);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: PROJECT_ID,
  });
  const db = admin.firestore();
  db.settings({ignoreUndefinedProperties: true});

  const serial = process.env.ANDROID_SERIAL || findPixelSerial();
  let appiumProcess = null;
  let driver = null;
  let failed = true;

  console.log(`Appium sync/rating E2E prefix: ${PREFIX}`);
  console.log(`Fixture prefix: ${FIXTURE_PREFIX}`);
  console.log(`Device serial: ${serial}`);
  console.log(`Fixture kind: ${FIXTURE_KIND}`);
  console.log(`Profile role: ${PROFILE_ROLE}`);
  if (APPIUM_SYSTEM_PORT) console.log(`UiAutomator2 systemPort: ${APPIUM_SYSTEM_PORT}`);
  console.log(`Artifacts: ${artifactDir}`);

  try {
    if (shouldSeedPublicFixture) {
      await seedPublicFixture(db, fixture);
    } else {
      await assertPublicFixtureExists(db, fixture);
    }
    if (shouldBuild) buildApp();
    installApp(serial);
    prepareDevice(serial);
    if (shouldClearAppData) clearAppData(serial);

    appiumProcess = await maybeStartAppium();
    driver = await createDriver(serial);
    await runArenaRatingFlow(driver, db, serial, fixture, artifactDir);
    failed = false;
    console.log("OK Appium sync/rating E2E passed");
  } finally {
    if (driver) await safeDeleteSession(driver);
    if (appiumProcess) stopProcess(appiumProcess);

    if (!shouldSeedPublicFixture) {
      console.log(`Keeping external Firestore fixture: ${fixture.questTitle}`);
    } else if (shouldKeepFixture || (failed && shouldKeepFailedFixture)) {
      console.log(`Keeping Firestore fixture for inspection: ${fixture.questTitle}`);
    } else {
      await cleanupPublicFixture(db, fixture);
      await cleanupUserFixture(db, fixture);
      console.log(`Cleaned Firestore fixture: ${fixture.questTitle}`);
    }

    await closeFirestore(db);
    await admin.app().delete();
  }
}

async function runArenaRatingFlow(driver, db, serial, fixture, artifactDir) {
  await bringAppToForeground(driver, serial);
  await waitAnyText(driver, ["Интернет", "Домашние квесты", "Арена"], 70000);
  await screenshot(driver, artifactDir, "01-home");

  if (!(await existsText(driver, "Арена"))) {
    await tapText(driver, "Интернет");
  }
  const internetLanding = await waitAnyText(
    driver,
    ["Арена", "Курсы", "Квалификации", "Профиль", "Анонимный профиль"],
    30000,
  );
  if (["Квалификации", "Профиль", "Анонимный профиль"].includes(internetLanding)) {
    await grantDeveloperAccessAndOpenArena(driver, db, serial, fixture, artifactDir);
  }
  await waitAnyText(driver, ["Арена", "Курсы"], 30000);
  await tapText(driver, fixture.questTitle, {scroll: true, timeoutMs: 70000});
  await screenshot(driver, artifactDir, "02-quest-visible-on-arena");

  await tapText(driver, fixture.sectionTitle, {scroll: true});
  await tapText(driver, fixture.themeTitle, {scroll: true});
  await waitLocalLessonSynced(serial, fixture.lessonId, artifactDir, 240000);
  await tapText(driver, fixture.lessonTitle, {scroll: true, timeoutMs: 240000});

  await waitText(driver, fixture.easyQuestionText, 70000);
  await answerCurrentSingleChoice(driver, fixture.easyCorrect);
  await screenshot(driver, artifactDir, "03-easy-question-answered");
  await waitText(driver, "Поздравляем! Сложные вопросы доступны", 20000);
  await screenshot(driver, artifactDir, "04-easy-perfect-result");

  await waitText(driver, "Оцените урок", 10000);
  await tapDescription(driver, "3 звезды");
  await waitGoneText(driver, "Оцените урок", 15000);
  await assertLocalRatingSubmitted(serial, fixture.lessonId, artifactDir);
  await screenshot(driver, artifactDir, "05-rating-submitted");

  await tapText(driver, "Завершить");
  await waitText(driver, fixture.lessonTitle, 30000);
  await waitText(driver, "Легкий", 30000);
  await tapText(driver, "Легкий");
  await waitText(driver, "Сложный", 10000);
  await screenshot(driver, artifactDir, "06-hard-mode-unlocked");

  await tapText(driver, fixture.lessonTitle, {scroll: true});
  await waitText(driver, fixture.hardQuestionText, 70000);
  await answerCurrentSingleChoice(driver, fixture.hardCorrect);
  await screenshot(driver, artifactDir, "07-hard-question-answered");
  await waitText(driver, "100% сложные! Вы прошли урок полностью", 20000);
  await screenshot(driver, artifactDir, "08-hard-perfect-result");

  if (shouldVerifyServerSync) {
    await syncOutboxAndVerifyServer(driver, db, serial, fixture, artifactDir);
  }
}

async function bringAppToForeground(driver, serial) {
  prepareDevice(serial);
  runOptional("adb", ["-s", serial, "shell", "cmd", "statusbar", "collapse"]);
  runOptional("adb", [
    "-s",
    serial,
    "shell",
    "am",
    "start",
    "-n",
    `${PACKAGE_NAME}/${APP_ACTIVITY}`,
  ]);
  try {
    await driver.activateApp(PACKAGE_NAME);
  } catch (error) {
    console.warn(`WARN activateApp failed: ${error.message}`);
  }
  await driver.pause(2500);
}

async function grantDeveloperAccessAndOpenArena(driver, db, serial, fixture, artifactDir) {
  const uid = await waitCurrentFirebaseUid(serial, 70000, artifactDir);
  fixture.userUid = uid;
  await seedRoleUserStats(db, uid, PROFILE_ROLE);
  console.log(`Seeded ${PROFILE_ROLE} profile for anonymous uid: ${uid}`);

  await openDrawer(driver);
  await screenshot(driver, artifactDir, "02a-internet-locked-before-profile-sync");
  await tapText(driver, "Синхронизация", {scroll: true});
  await waitText(driver, "Арена", 70000);
  await screenshot(driver, artifactDir, "02b-arena-unlocked-after-profile-sync");
  await tapText(driver, "Арена");
}

async function answerCurrentSingleChoice(driver, correctText) {
  await tapText(driver, correctText, {timeoutMs: 30000});
  await driver.pause(600);
  await tapScreen(driver, 0.5, 0.55);
}

async function openDrawer(driver) {
  if (await existsText(driver, "Синхронизация")) return;
  await tapDescription(driver, "Open menu");
  await waitText(driver, "Синхронизация", 10000);
}

async function syncOutboxAndVerifyServer(driver, db, serial, fixture, artifactDir) {
  await tapText(driver, "Завершить");
  await waitAnyText(driver, [fixture.lessonTitle, "Легкий", "Сложный", "Арена"], 30000);
  await returnToRootForSync(driver);
  await screenshot(driver, artifactDir, "09-before-result-sync");
  await openDrawer(driver);
  await tapText(driver, "Синхронизация", {scroll: true});
  const outbox = await waitLocalOutboxSent(serial, fixture.lessonId, artifactDir);
  await assertServerOutboxUploaded(db, fixture, outbox);
  await assertServerProfileProgress(db, outbox);
  await assertLocalProfileProgress(serial, outbox, artifactDir);
  if (shouldVerifyRatingAggregate) {
    await invokeRatingAggregationAndVerify(db, fixture, outbox);
  }
  await screenshot(driver, artifactDir, "10-server-sync-complete");
}

async function returnToRootForSync(driver) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    if ((await existsText(driver, "Синхронизация")) || (await existsDescription(driver, "Open menu"))) {
      return;
    }
    await driver.back();
    await driver.pause(900);
  }
  await dumpOnFailure(driver, "missing-open-menu-for-sync");
  throw new Error("Could not return to a screen with the app drawer for manual sync");
}

async function createDriver(serial) {
  const capabilities = {
    platformName: "Android",
    "appium:automationName": "UiAutomator2",
    "appium:udid": serial,
    "appium:deviceName": serial,
    "appium:appPackage": PACKAGE_NAME,
    "appium:appActivity": APP_ACTIVITY,
    "appium:appWaitActivity": APP_ACTIVITY,
    "appium:noReset": true,
    "appium:autoGrantPermissions": true,
    "appium:disableWindowAnimation": true,
    "appium:newCommandTimeout": 240,
  };
  if (APPIUM_SYSTEM_PORT) {
    capabilities["appium:systemPort"] = APPIUM_SYSTEM_PORT;
  }

  return remote({
    protocol: "http",
    hostname: APPIUM_HOST,
    port: APPIUM_PORT,
    path: "/",
    logLevel: process.env.WDIO_LOG_LEVEL || "warn",
    capabilities,
  });
}

async function maybeStartAppium() {
  if (envBool("APPIUM_EXTERNAL_SERVER", false)) {
    await waitForAppium(20000);
    return null;
  }

  const binary = path.join(E2E_ROOT, "node_modules", ".bin", "appium");
  if (!fs.existsSync(binary)) {
    throw new Error("Appium binary not found. Run: cd e2e/appium && npm install");
  }
  const args = [
    "--address",
    APPIUM_HOST,
    "--port",
    String(APPIUM_PORT),
    "--base-path",
    "/",
    "--log-level",
    process.env.APPIUM_LOG_LEVEL || "warn",
  ];
  console.log(`\n$ ${binary} ${args.join(" ")}`);
  const child = spawn(binary, args, {
    cwd: E2E_ROOT,
    env: process.env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.on("data", (chunk) => process.stdout.write(`[appium] ${chunk}`));
  child.stderr.on("data", (chunk) => process.stderr.write(`[appium] ${chunk}`));
  await waitForAppium(60000);
  return child;
}

async function waitForAppium(timeoutMs) {
  await waitUntil(async () => {
    try {
      const status = await httpGetJson(`${APPIUM_URL}/status`);
      return Boolean(status && status.value);
    } catch (_error) {
      return false;
    }
  }, timeoutMs, `Appium server at ${APPIUM_URL}/status`);
}

function httpGetJson(url) {
  return new Promise((resolve, reject) => {
    const request = http.get(url, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`HTTP ${response.statusCode}: ${body}`));
          return;
        }
        resolve(JSON.parse(body));
      });
    });
    request.on("error", reject);
    request.setTimeout(1000, () => request.destroy(new Error("timeout")));
  });
}

async function waitText(driver, text, timeoutMs = 30000) {
  return waitElement(driver, text, {timeoutMs});
}

async function waitGoneText(driver, text, timeoutMs = 10000) {
  await waitUntil(async () => !(await existsText(driver, text)), timeoutMs, `text to disappear: ${text}`);
}

async function waitAnyText(driver, texts, timeoutMs = 30000) {
  let found = null;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const text of texts) {
      if (await existsText(driver, text)) return text;
    }
    await driver.pause(350);
  }
  await dumpOnFailure(driver, `missing-${slug(texts.join("-"))}`);
  throw new Error(`Timed out waiting for any text: ${texts.join(", ")}`);
}

async function tapText(driver, text, options = {}) {
  let lastError = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const element = await waitElement(driver, text, options);
    try {
      await clickElementCenter(driver, element);
      console.log(`tap text: ${text}`);
      return;
    } catch (error) {
      lastError = error;
      if (!isStaleElementError(error)) throw error;
      await driver.pause(150);
    }
  }
  throw lastError;
}

async function tapDescription(driver, description, timeoutMs = 15000) {
  const selector = `android=new UiSelector().description("${escapeUiSelector(description)}")`;
  const element = await waitUntilElement(driver, selector, timeoutMs, `description: ${description}`);
  await clickElementCenter(driver, element);
  console.log(`tap description: ${description}`);
}

async function clickElementCenter(driver, element) {
  try {
    await element.click();
    return;
  } catch (error) {
    if (!isRecoverableClickError(error)) {
      throw error;
    }
  }
  const location = await element.getLocation();
  const size = await element.getSize();
  await driver.execute("mobile: clickGesture", {
    x: Math.round(location.x + size.width / 2),
    y: Math.round(location.y + size.height / 2),
  });
}

function isRecoverableClickError(error) {
  const message = String(error && error.message ? error.message : error);
  return /not clickable|could not be clicked|unknown error/i.test(message);
}

function isStaleElementError(error) {
  const message = String(error && error.message ? error.message : error);
  return /stale element|do not exist in DOM anymore/i.test(message);
}

async function waitElement(driver, text, options = {}) {
  const timeoutMs = options.timeoutMs || 30000;
  const scroll = options.scroll !== false;
  const exactSelector = `android=new UiSelector().text("${escapeUiSelector(text)}")`;
  const containsSelector = `android=new UiSelector().textContains("${escapeUiSelector(text)}")`;

  const deadline = Date.now() + timeoutMs;
  let lastScrollAt = 0;
  while (Date.now() < deadline) {
    const exact = await maybeElement(driver, exactSelector);
    if (exact) return exact;
    const contains = await maybeElement(driver, containsSelector);
    if (contains) return contains;
    if (scroll && Date.now() - lastScrollAt > 1800) {
      await scrollTextIntoView(driver, text);
      lastScrollAt = Date.now();
    }
    await driver.pause(350);
  }
  await dumpOnFailure(driver, `missing-${slug(text)}`);
  throw new Error(`Timed out waiting for text: ${text}`);
}

async function waitUntilElement(driver, selector, timeoutMs, description) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const element = await maybeElement(driver, selector);
    if (element) return element;
    await driver.pause(250);
  }
  await dumpOnFailure(driver, `missing-${slug(description)}`);
  throw new Error(`Timed out waiting for ${description}`);
}

async function existsText(driver, text) {
  const selector = `android=new UiSelector().text("${escapeUiSelector(text)}")`;
  return Boolean(await maybeElement(driver, selector));
}

async function existsDescription(driver, description) {
  const selector = `android=new UiSelector().description("${escapeUiSelector(description)}")`;
  return Boolean(await maybeElement(driver, selector));
}

async function maybeElement(driver, selector) {
  try {
    const element = await driver.$(selector);
    return (await element.isExisting()) ? element : null;
  } catch (_error) {
    return null;
  }
}

async function scrollTextIntoView(driver, text) {
  const selector =
    `android=new UiScrollable(new UiSelector().scrollable(true)).scrollTextIntoView("${escapeUiSelector(text)}")`;
  try {
    const element = await driver.$(selector);
    return (await element.isExisting()) ? element : null;
  } catch (_error) {
    return null;
  }
}

async function tapScreen(driver, xRatio, yRatio) {
  const size = await driver.getWindowSize();
  await driver.execute("mobile: clickGesture", {
    x: Math.round(size.width * xRatio),
    y: Math.round(size.height * yRatio),
  });
}

async function screenshot(driver, artifactDir, name) {
  if (!shouldCaptureSteps) return;
  const file = path.join(artifactDir, `${name}.png`);
  try {
    await driver.saveScreenshot(file);
    console.log(`screenshot: ${file}`);
  } catch (error) {
    const message = `Screenshot skipped: ${error.message}`;
    fs.writeFileSync(path.join(artifactDir, `${name}.screenshot-error.txt`), message);
    console.warn(`WARN ${message}`);
  }
}

async function dumpOnFailure(driver, name) {
  const dir = path.join(E2E_ROOT, "artifacts", PREFIX);
  fs.mkdirSync(dir, {recursive: true});
  try {
    await driver.saveScreenshot(path.join(dir, `${name}.png`));
  } catch (_error) {
    // ignore diagnostic failures
  }
  try {
    fs.writeFileSync(path.join(dir, `${name}.xml`), await driver.getPageSource());
  } catch (_error) {
    // ignore diagnostic failures
  }
}

async function waitUntil(check, timeoutMs, description) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await check()) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Timed out waiting for ${description}`);
}

async function seedPublicFixture(db, fixture) {
  const now = Date.now();
  const timestamp = admin.firestore.Timestamp.fromMillis(now);
  const documents = {
    [`quests/${fixture.questId}`]: {
      id: fixture.questId,
      catalogId: fixture.catalogId,
      authorUid: fixture.authorUid,
      title: fixture.questTitle,
      picturePath: null,
      visibleOn: ["arena"],
      averageRating: null,
      averageRatingCount: 0,
      version: now,
      contentsVersion: now,
      lastModifiedAt: timestamp,
      archived: false,
    },
    [`sections/${fixture.sectionId}`]: {
      id: fixture.sectionId,
      questId: fixture.questId,
      title: fixture.sectionTitle,
      order: 0,
      version: now,
      contentsVersion: now,
      lastModifiedAt: timestamp,
      archived: false,
    },
    [`themes/${fixture.themeId}`]: {
      id: fixture.themeId,
      sectionId: fixture.sectionId,
      title: fixture.themeTitle,
      order: 0,
      version: now,
      contentsVersion: now,
      lastModifiedAt: timestamp,
      archived: false,
    },
    [`lessons/${fixture.lessonId}`]: {
      id: fixture.lessonId,
      themeId: fixture.themeId,
      title: fixture.lessonTitle,
      order: 0,
      version: now,
      contentsVersion: now,
      lastModifiedAt: timestamp,
      archived: false,
    },
    [`questions/${fixture.easyQuestionId}`]: questionDocument(fixture, "easy", now, timestamp),
    [`questions/${fixture.hardQuestionId}`]: questionDocument(fixture, "hard", now, timestamp),
  };

  for (const [type, id] of [
    ["quest", fixture.questId],
    ["section", fixture.sectionId],
    ["theme", fixture.themeId],
    ["lesson", fixture.lessonId],
    ["question", fixture.easyQuestionId],
    ["question", fixture.hardQuestionId],
  ]) {
    documents[`catalogs/${fixture.catalogId}/sync_changes/${type}_${id}`] = {type, id, changedAtMs: now};
  }
  documents[`lesson_content/${fixture.lessonId}/sync_changes/${fixture.easyQuestionId}`] = {
    type: "question",
    id: fixture.easyQuestionId,
    changedAtMs: now,
  };
  documents[`lesson_content/${fixture.lessonId}/sync_changes/${fixture.hardQuestionId}`] = {
    type: "question",
    id: fixture.hardQuestionId,
    changedAtMs: now,
  };

  const batch = db.batch();
  for (const [docPath, data] of Object.entries(documents)) {
    batch.set(db.doc(docPath), data, {merge: true});
  }
  await batch.commit();
  console.log(`Seeded Firestore fixture: ${fixture.questTitle}`);
}

async function assertPublicFixtureExists(db, fixture) {
  const requiredPaths = [
    `quests/${fixture.questId}`,
    `sections/${fixture.sectionId}`,
    `themes/${fixture.themeId}`,
    `lessons/${fixture.lessonId}`,
    `questions/${fixture.easyQuestionId}`,
    `questions/${fixture.hardQuestionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/quest_${fixture.questId}`,
    `catalogs/${fixture.catalogId}/sync_changes/section_${fixture.sectionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/theme_${fixture.themeId}`,
    `catalogs/${fixture.catalogId}/sync_changes/lesson_${fixture.lessonId}`,
    `catalogs/${fixture.catalogId}/sync_changes/question_${fixture.easyQuestionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/question_${fixture.hardQuestionId}`,
    `lesson_content/${fixture.lessonId}/sync_changes/${fixture.easyQuestionId}`,
    `lesson_content/${fixture.lessonId}/sync_changes/${fixture.hardQuestionId}`,
  ];
  const missing = [];
  for (const docPath of requiredPaths) {
    if (!(await db.doc(docPath).get()).exists) missing.push(docPath);
  }
  if (missing.length > 0) {
    throw new Error(`External Firestore fixture is incomplete:\n${missing.join("\n")}`);
  }
  console.log(`Using external Firestore fixture: ${fixture.questTitle}`);
}

async function seedRoleUserStats(db, uid, role) {
  const profile = roleProfile(role);
  const now = Date.now();
  await writeRoleProfile(db, uid, profile, now);
}

async function writeRoleProfile(db, uid, profile, now = Date.now()) {
  const userDocument = {
    uid,
    nickname: profile.nickname,
    status: profile.status,
    avatarUrl: null,
    knownLanguages: ["ru"],
    createdAtMs: now,
    updatedAtMs: now,
    hasPremium: false,
    streakDays: 0,
    stars: 0,
    pointsNolics: 0,
    standardHearts: 5,
    goldHearts: 0,
    gold: 0,
    pointsSkill: profile.skillPoints,
    skillPoints: profile.skillPoints,
    tester: profile.testerLevel,
    moderator: profile.moderatorLevel,
    sponsor: profile.sponsorLevel,
    translater: profile.translatorLevel,
    admin: profile.adminLevel,
    developer: profile.developerLevel,
    qualification: {
      sponsorLevel: profile.sponsorLevel,
      testerLevel: profile.testerLevel,
      translatorLevel: profile.translatorLevel,
      moderatorLevel: profile.moderatorLevel,
      adminLevel: profile.adminLevel,
      developerLevel: profile.developerLevel,
    },
  };
  const trustedProfile = {
    uid,
    nickname: profile.nickname,
    status: profile.status,
    knownLanguages: ["ru"],
    createdAtMs: now,
    updatedAtMs: now,
    reviewReputation: 0,
    sponsorLevel: profile.sponsorLevel,
    testerLevel: profile.testerLevel,
    translatorLevel: profile.translatorLevel,
    moderatorLevel: profile.moderatorLevel,
    adminLevel: profile.adminLevel,
    developerLevel: profile.developerLevel,
  };
  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), userDocument, {merge: true});
  batch.set(db.doc(`profiles/${uid}`), trustedProfile, {merge: true});
  await batch.commit();
}

function roleProfile(role) {
  const arenaSkill = 3000;
  const roleLevel = 100;
  const base = {
    nickname: `Appium ${role}`,
    status: "VALIDATED",
    skillPoints: arenaSkill,
    sponsorLevel: 0,
    testerLevel: 0,
    translatorLevel: 0,
    moderatorLevel: 0,
    adminLevel: 0,
    developerLevel: 0,
  };
  switch (role) {
    case "anonymous":
      return {...base, nickname: "Appium Anonymous", status: "ANONYMOUS", skillPoints: arenaSkill};
    case "registered":
    case "participant":
      return {...base, nickname: "Appium Participant", status: "REGISTERED"};
    case "tester":
      return {...base, testerLevel: roleLevel};
    case "moderator":
      return {...base, moderatorLevel: roleLevel};
    case "sponsor":
      return {...base, sponsorLevel: roleLevel};
    case "translator":
    case "translater":
      return {...base, translatorLevel: roleLevel};
    case "admin":
      return {...base, adminLevel: roleLevel};
    case "full_access":
      return {
        ...base,
        nickname: "Appium Full Access",
        skillPoints: 10000,
        sponsorLevel: roleLevel,
        testerLevel: roleLevel,
        translatorLevel: roleLevel,
        moderatorLevel: roleLevel,
        adminLevel: roleLevel,
        developerLevel: roleLevel,
      };
    case "developer":
    default:
      return {...base, nickname: "Appium Developer", developerLevel: roleLevel};
  }
}

function questionDocument(fixture, difficulty, version, timestamp) {
  const isHard = difficulty === "hard";
  const id = isHard ? fixture.hardQuestionId : fixture.easyQuestionId;
  const text = isHard ? fixture.hardQuestionText : fixture.easyQuestionText;
  const correct = isHard ? fixture.hardCorrect : fixture.easyCorrect;
  const wrong = isHard ? fixture.hardWrong : fixture.easyWrong;
  return {
    id,
    lessonId: fixture.lessonId,
    text,
    payload: JSON.stringify({
      type: "SingleChoice",
      id,
      difficulty: isHard ? "HARD" : "EASY",
      text,
      imageUrl: null,
      options: [
        {id: "correct", text: correct},
        {id: "wrong", text: wrong},
      ],
      correctOptionId: "correct",
    }),
    language: "ru",
    languageLevel: 1,
    order: isHard ? 1 : 0,
    version,
    lastModifiedAt: timestamp,
    archived: false,
  };
}

async function cleanupPublicFixture(db, fixture) {
  const paths = [
    `quests/${fixture.questId}`,
    `sections/${fixture.sectionId}`,
    `themes/${fixture.themeId}`,
    `lessons/${fixture.lessonId}`,
    `questions/${fixture.easyQuestionId}`,
    `questions/${fixture.hardQuestionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/quest_${fixture.questId}`,
    `catalogs/${fixture.catalogId}/sync_changes/section_${fixture.sectionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/theme_${fixture.themeId}`,
    `catalogs/${fixture.catalogId}/sync_changes/lesson_${fixture.lessonId}`,
    `catalogs/${fixture.catalogId}/sync_changes/question_${fixture.easyQuestionId}`,
    `catalogs/${fixture.catalogId}/sync_changes/question_${fixture.hardQuestionId}`,
    `lesson_content/${fixture.lessonId}/sync_changes/${fixture.easyQuestionId}`,
    `lesson_content/${fixture.lessonId}/sync_changes/${fixture.hardQuestionId}`,
  ];
  const batch = db.batch();
  for (const docPath of paths) {
    batch.delete(db.doc(docPath));
  }
  await batch.commit();
}

async function cleanupUserFixture(db, fixture) {
  const uids = [fixture.userUid, fixture.aggregateUid].filter(Boolean);
  if (uids.length === 0) return;
  const batch = db.batch();
  for (const uid of uids) {
    batch.delete(db.doc(`users/${uid}`));
    batch.delete(db.doc(`profiles/${uid}`));
  }
  await batch.commit();
}

async function assertLocalRatingSubmitted(serial, lessonId, artifactDir) {
  const deadline = Date.now() + 15000;
  let lastCount = 0;
  while (Date.now() < deadline) {
    lastCount = readLocalRatingCount(serial, lessonId, artifactDir);
    if (lastCount >= 1) {
      console.log(`OK local rating row exists for lesson ${lessonId}`);
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 750));
  }
  throw new Error(`Expected local rating row for ${lessonId}, got ${lastCount}`);
}

async function waitLocalLessonSynced(serial, lessonId, artifactDir, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastState = {lessonCount: 0, syncCursors: []};
  while (Date.now() < deadline) {
    try {
      lastState = readLocalLessonSyncState(serial, lessonId, artifactDir);
      if (lastState.lessonCount >= 1) {
        console.log(`OK local lesson synced: ${lessonId}`);
        return;
      }
    } catch (error) {
      console.warn(`WARN local lesson sync state unavailable yet: ${error.message}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  throw new Error(
    `Expected local lesson to sync for ${lessonId}; ` +
      `lessonCount=${lastState.lessonCount}, cursors=${JSON.stringify(lastState.syncCursors)}`,
  );
}

function readLocalLessonSyncState(serial, lessonId, artifactDir) {
  const dbPath = pullLocalDatabase(serial, artifactDir);
  const lessonRows = querySqliteRows(
    dbPath,
    `SELECT COUNT(*) FROM lessons WHERE id = '${escapeSql(lessonId)}';`,
  );
  const cursorRows = querySqliteRows(
    dbPath,
    `
    SELECT collectionId, cursor
    FROM sync_state
    WHERE collectionId = 'catalogs' OR collectionId LIKE 'catalog_sync:%'
    ORDER BY collectionId ASC;
    `,
  );
  return {
    lessonCount: Number(lessonRows[0]?.[0] || 0),
    syncCursors: cursorRows.map((row) => ({id: row[0], cursor: numberOrNull(row[1])})),
  };
}

function readLocalRatingCount(serial, lessonId, artifactDir) {
  const dbPath = pullLocalDatabase(serial, artifactDir);
  const sql =
    `SELECT COUNT(*) FROM lesson_rating_submitted_local WHERE lesson_id = '${escapeSql(lessonId)}';`;
  const result = spawnSync("sqlite3", [dbPath, sql], {
    cwd: REPO_ROOT,
    encoding: "utf8",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(result.stderr || "sqlite3 failed while checking local rating");
  }
  const count = Number(result.stdout.trim());
  return Number.isFinite(count) ? count : 0;
}

async function waitLocalOutboxSent(serial, lessonId, artifactDir) {
  const deadline = Date.now() + 120000;
  let lastState = {attempts: [], ratings: []};
  while (Date.now() < deadline) {
    lastState = readLocalOutboxState(serial, lessonId, artifactDir);
    const attemptReady =
      lastState.attempts.length >= 2 &&
      lastState.attempts.every((attempt) => attempt.sentAtMs !== null);
    const ratingReady =
      lastState.ratings.length >= 1 &&
      lastState.ratings.every((rating) => rating.sentAtMs !== null);
    if (attemptReady && ratingReady) {
      console.log(
        `OK local outbox sent: attempts=${lastState.attempts.length}, ratings=${lastState.ratings.length}`,
      );
      return lastState;
    }
    const lastError = [...lastState.attempts, ...lastState.ratings]
      .map((row) => row.lastError)
      .filter(Boolean)
      .at(-1);
    if (lastError) {
      console.warn(`WARN latest local outbox error: ${lastError}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(
    `Expected local outbox to be sent for ${lessonId}; ` +
      `attempts=${lastState.attempts.length}, ratings=${lastState.ratings.length}`,
  );
}

function readLocalOutboxState(serial, lessonId, artifactDir) {
  const dbPath = pullLocalDatabase(serial, artifactDir);
  const whereLesson = `lesson_id = '${escapeSql(lessonId)}'`;
  const attemptRows = querySqliteRows(
    dbPath,
    `
    SELECT
      attempt_id, user_id, scope, IFNULL(owner_uid, ''), catalog_id, quest_id, section_id,
      theme_id, lesson_id, lesson_version, IFNULL(source_shelf, ''), IFNULL(difficulty, ''),
      percent_score, completed_at_ms, created_at_ms, IFNULL(sent_at_ms, ''), IFNULL(last_error, '')
    FROM lesson_result_attempt_outbox
    WHERE ${whereLesson}
    ORDER BY created_at_ms ASC;
    `,
  );
  const ratingRows = querySqliteRows(
    dbPath,
    `
    SELECT
      rating_id, user_id, scope, IFNULL(owner_uid, ''), catalog_id, quest_id, section_id,
      theme_id, lesson_id, lesson_version, IFNULL(source_shelf, ''), rating, rated_at_ms,
      created_at_ms, IFNULL(sent_at_ms, ''), IFNULL(last_error, '')
    FROM quest_rating_outbox
    WHERE ${whereLesson}
    ORDER BY created_at_ms ASC;
    `,
  );
  return {
    attempts: attemptRows.map((row) => ({
      attemptId: row[0],
      userId: row[1],
      scope: row[2],
      ownerUid: row[3] || null,
      catalogId: row[4],
      questId: row[5],
      sectionId: row[6],
      themeId: row[7],
      lessonId: row[8],
      lessonVersion: numberOrNull(row[9]),
      sourceShelf: row[10],
      difficulty: row[11],
      percentScore: numberOrNull(row[12]),
      completedAtMs: numberOrNull(row[13]),
      createdAtMs: numberOrNull(row[14]),
      sentAtMs: numberOrNull(row[15]),
      lastError: row[16] || null,
    })),
    ratings: ratingRows.map((row) => ({
      ratingId: row[0],
      userId: row[1],
      scope: row[2],
      ownerUid: row[3] || null,
      catalogId: row[4],
      questId: row[5],
      sectionId: row[6],
      themeId: row[7],
      lessonId: row[8],
      lessonVersion: numberOrNull(row[9]),
      sourceShelf: row[10],
      rating: numberOrNull(row[11]),
      ratedAtMs: numberOrNull(row[12]),
      createdAtMs: numberOrNull(row[13]),
      sentAtMs: numberOrNull(row[14]),
      lastError: row[15] || null,
    })),
  };
}

async function assertServerOutboxUploaded(db, fixture, outbox) {
  await waitUntil(async () => {
    for (const attempt of outbox.attempts) {
      const ref = db
        .collection(scopedCollection("result_events", attempt.scope))
        .doc(eventBucketId(attempt.completedAtMs, attempt.attemptId))
        .collection("events")
        .doc(safeDocId(attempt.attemptId));
      if (!(await ref.get()).exists) return false;
    }
    for (const rating of outbox.ratings) {
      const contentKey = questContentKey(rating.scope, rating.ownerUid, rating.catalogId, rating.questId);
      const eventRef = db
        .collection(scopedCollection("rating_events", rating.scope))
        .doc(eventBucketId(rating.ratedAtMs, rating.ratingId))
        .collection("events")
        .doc(safeDocId(rating.ratingId));
      const submissionRef = db
        .collection(scopedCollection("quest_rating_submissions", rating.scope))
        .doc(contentKey)
        .collection("ratings")
        .doc(hashHex(rating.userId));
      const dirtyRef = db.collection(scopedCollection("quest_rating_dirty", rating.scope)).doc(contentKey);
      if (!(await eventRef.get()).exists) return false;
      if (!(await submissionRef.get()).exists) return false;
      if (!(await dirtyRef.get()).exists) return false;
    }
    return true;
  }, 45000, "server result/rating outbox documents");
  console.log(
    `OK server outbox uploaded for ${fixture.questId}: ` +
      `attempts=${outbox.attempts.length}, ratings=${outbox.ratings.length}`,
  );
}

async function assertServerProfileProgress(db, outbox) {
  const progress = expectedProgress(outbox);
  await waitUntil(async () => {
    const snapshot = await db.doc(`users/${progress.userId}`).get();
    if (!snapshot.exists) return false;
    const data = snapshot.data() || {};
    return (
      Number(data.pointsSkill) >= progress.expectedSkillPoints &&
      Number(data.pointsNolics) >= progress.expectedNolics
    );
  }, 45000, "server user progress fields");
  console.log(
    `OK server user progress updated: ` +
      `uid=${progress.userId}, skill>=${progress.expectedSkillPoints}, nolics>=${progress.expectedNolics}`,
  );
}

async function assertLocalProfileProgress(serial, outbox, artifactDir) {
  const progress = expectedProgress(outbox);
  await waitUntil(async () => {
    const dbPath = pullLocalDatabase(serial, artifactDir);
    const uid = escapeSql(progress.userId);
    const statsRows = querySqliteRows(
      dbPath,
      `SELECT currentSkill, nolics FROM user_stats WHERE uid = '${uid}';`,
    );
    const profileRows = querySqliteRows(
      dbPath,
      `SELECT skillPoints, nolics FROM user_profiles WHERE uid = '${uid}';`,
    );
    if (statsRows.length === 0 || profileRows.length === 0) return false;
    return (
      Number(statsRows[0][0]) >= progress.expectedSkillPoints &&
      Number(statsRows[0][1]) >= progress.expectedNolics &&
      Number(profileRows[0][0]) >= progress.expectedSkillPoints &&
      Number(profileRows[0][1]) >= progress.expectedNolics
    );
  }, 60000, "local profile progress after sync");
  console.log(
    `OK local profile sync pulled progress: ` +
      `uid=${progress.userId}, skill>=${progress.expectedSkillPoints}, nolics>=${progress.expectedNolics}`,
  );
}

function expectedProgress(outbox) {
  const attempts = outbox.attempts || [];
  const userId = attempts[0] && attempts[0].userId;
  if (!userId) throw new Error("Cannot verify profile progress without attempt userId");
  const baseProfile = roleProfile(PROFILE_ROLE);
  const reward = attempts.reduce(
    (sum, attempt) => {
      const item = resultReward(attempt);
      return {
        skillPoints: sum.skillPoints + item.skillPoints,
        nolics: sum.nolics + item.nolics,
      };
    },
    {skillPoints: 0, nolics: 0},
  );
  return {
    userId,
    expectedSkillPoints: baseProfile.skillPoints + reward.skillPoints,
    expectedNolics: reward.nolics,
  };
}

function resultReward(attempt) {
  const percent = Math.max(0, Math.min(Number(attempt.percentScore) || 0, 100));
  const multiplier =
    String(attempt.difficulty || "").toUpperCase() === "HARD" ? HARD_RESULT_REWARD_MULTIPLIER : 1;
  return {
    skillPoints: percent * multiplier,
    nolics: Math.floor(percent / NOLICS_PERCENT_STEP) * multiplier,
  };
}

async function invokeRatingAggregationAndVerify(db, fixture, outbox) {
  const rating = outbox.ratings[0];
  if (!rating) throw new Error("Cannot verify rating aggregate without a rating row");
  const aggregateUid = `${safeId(fixture.questId)}_aggregate_admin`;
  fixture.aggregateUid = aggregateUid;
  await writeRoleProfile(
    db,
    aggregateUid,
    {
      ...roleProfile("full_access"),
      nickname: "Appium Aggregate Admin",
      developerLevel: 101,
      skillPoints: 10000,
    },
  );
  const idToken = await signInAsCustomUid(aggregateUid);
  const result = await callFirebaseCallable("aggregateQuestRatingsNow", idToken, {limit: 20});
  const contentKey = questContentKey(rating.scope, rating.ownerUid, rating.catalogId, rating.questId);
  const questRef = rating.scope === "private"
    ? db.doc(`users/${rating.ownerUid}/catalogs/${rating.catalogId}/quests/${rating.questId}`)
    : db.collection("quests").doc(rating.questId);
  const expectedQualificationDelta = questRatingQualificationDelta(rating.rating);
  await waitUntil(async () => {
    const aggregate = await db
      .collection(scopedCollection("quest_rating_aggregates", rating.scope))
      .doc(contentKey)
      .get();
    const quest = await questRef.get();
    if (!aggregate.exists || !quest.exists) return false;
    const aggregateData = aggregate.data() || {};
    const questData = quest.data() || {};
    const targetUid = rating.scope === "private" ? rating.ownerUid : questData.authorUid;
    if (!targetUid) return false;
    const user = await db.doc(`users/${targetUid}`).get();
    const profile = await db.doc(`profiles/${targetUid}`).get();
    const userData = user.data() || {};
    const profileData = profile.data() || {};
    return (
      Number(aggregateData.averageRating) === Number(rating.rating) &&
      Number(aggregateData.averageRatingCount) >= 1 &&
      aggregateData.qualificationTargetUid === targetUid &&
      aggregateData.qualificationField === "developer" &&
      Number(aggregateData.qualificationScore) >= expectedQualificationDelta &&
      Number(userData.developer) >= expectedQualificationDelta &&
      Number(profileData.developerLevel) >= expectedQualificationDelta &&
      Number(questData.averageRating) === Number(rating.rating) &&
      Number(questData.averageRatingCount) >= 1
    );
  }, 45000, "quest rating aggregate and author qualification");
  console.log(`OK server rating aggregate updated for ${fixture.questId}: ${JSON.stringify(result)}`);
}

function questRatingQualificationDelta(rating) {
  return (Number(rating) - 2) * QUEST_RATING_QUALIFICATION_POINTS_PER_STAR;
}

async function signInAsCustomUid(uid) {
  const apiKey = process.env.SCHOOLQUIZ_FIREBASE_WEB_API_KEY || readFirebaseWebApiKey();
  const customToken = await admin.auth().createCustomToken(uid);
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
    {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({token: customToken, returnSecureToken: true}),
    },
  );
  const body = await response.json();
  if (!response.ok) {
    throw new Error(`Custom token sign-in failed: ${JSON.stringify(body)}`);
  }
  return body.idToken;
}

async function callFirebaseCallable(functionName, idToken, data) {
  const response = await fetch(
    `https://us-central1-${PROJECT_ID}.cloudfunctions.net/${functionName}`,
    {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${idToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({data}),
    },
  );
  const body = await response.json();
  if (!response.ok || body.error) {
    throw new Error(`${functionName} failed: ${JSON.stringify(body)}`);
  }
  return body.result;
}

function readFirebaseWebApiKey() {
  const googleServicesPath = path.join(REPO_ROOT, "apps/android-next/google-services.json");
  const googleServices = JSON.parse(fs.readFileSync(googleServicesPath, "utf8"));
  return googleServices.client[0].api_key[0].current_key;
}

function pullLocalDatabase(serial, artifactDir) {
  const dbPath = path.join(artifactDir, "schoolquiz.db");
  pullRunAsFile(serial, "databases/schoolquiz.db", dbPath, false);
  pullRunAsFile(serial, "databases/schoolquiz.db-wal", `${dbPath}-wal`, true);
  pullRunAsFile(serial, "databases/schoolquiz.db-shm", `${dbPath}-shm`, true);
  return dbPath;
}

function querySqliteRows(dbPath, sql) {
  const separator = "\u001f";
  const result = spawnSync("sqlite3", ["-separator", separator, dbPath, sql], {
    cwd: REPO_ROOT,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(result.stderr || "sqlite3 failed while reading local outbox");
  }
  return result.stdout
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => line.split(separator));
}

function numberOrNull(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function scopedCollection(base, scope) {
  return `${base}_${normalizeScope(scope)}`;
}

function normalizeScope(scope) {
  return scope === "private" ? "private" : "public";
}

function questContentKey(scope, ownerUid, catalogId, questId) {
  return hashHex([normalizeScope(scope), ownerUid || "", catalogId, questId].join("\u0000"));
}

function eventBucketId(timeMs, stableId) {
  const date = new Date(Math.max(0, Number(timeMs) || Date.now()))
    .toISOString()
    .slice(0, 10)
    .replace(/-/g, "");
  return `${date}_${hashHex(stableId).slice(0, 2)}`;
}

function hashHex(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function safeDocId(value) {
  return `${hashHex(value).slice(0, 12)}_${String(value || "")
    .replace(/[^A-Za-z0-9_.-]/g, "_")
    .slice(0, 80)}`;
}

function pullRunAsFile(serial, remotePath, localPath, optional) {
  const result = spawnSync(
    "adb",
    ["-s", serial, "exec-out", "run-as", PACKAGE_NAME, "sh", "-c", `cat ${remotePath} 2>/dev/null`],
    {cwd: REPO_ROOT, encoding: "buffer", maxBuffer: 1024 * 1024 * 50},
  );
  if (result.error) throw result.error;
  if (result.status !== 0 || result.stdout.length === 0) {
    if (optional) return;
    throw new Error(`Could not pull ${remotePath} via run-as`);
  }
  fs.writeFileSync(localPath, result.stdout);
}

async function waitCurrentFirebaseUid(serial, timeoutMs, artifactDir) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const uid = readCurrentFirebaseUid(serial);
    if (uid) return uid;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  if (artifactDir) writeAuthDiagnostics(serial, artifactDir);
  throw new Error("Firebase anonymous uid was not found in app auth storage");
}

function readCurrentFirebaseUid(serial) {
  const result = spawnSync(
    "adb",
    [
      "-s",
      serial,
      "exec-out",
      "run-as",
      PACKAGE_NAME,
      "sh",
      "-c",
      "cat shared_prefs/com.google.firebase.auth.api.Store.*.xml 2>/dev/null",
    ],
    {cwd: REPO_ROOT, encoding: "utf8", maxBuffer: 1024 * 1024 * 2},
  );
  if (result.error || result.status !== 0) return null;
  const byTokenResponse = result.stdout.match(/GET_TOKEN_RESPONSE\.([A-Za-z0-9_-]+)/);
  if (byTokenResponse) return byTokenResponse[1];
  const byUserInfo = result.stdout.match(/userId\\&quot;:\\&quot;([^\\&]+)/);
  return byUserInfo ? byUserInfo[1] : null;
}

function writeAuthDiagnostics(serial, artifactDir) {
  const result = spawnSync(
    "adb",
    [
      "-s",
      serial,
      "exec-out",
      "run-as",
      PACKAGE_NAME,
      "sh",
      "-c",
      "ls -la shared_prefs 2>/dev/null; echo ---; cat shared_prefs/*.xml 2>/dev/null",
    ],
    {cwd: REPO_ROOT, encoding: "utf8", maxBuffer: 1024 * 1024 * 4},
  );
  const body = result.error ? String(result.error.stack || result.error) : result.stdout + result.stderr;
  fs.writeFileSync(path.join(artifactDir, "auth-diagnostics.txt"), body || "No auth diagnostics");
}

function buildApp() {
  run("./gradlew", [":apps:android-next:assembleDebug", "--no-configuration-cache", "--max-workers=2"]);
}

function installApp(serial) {
  if (!fs.existsSync(APP_PATH)) {
    throw new Error(`APK not found: ${APP_PATH}`);
  }
  run("adb", ["-s", serial, "install", "-r", "-d", APP_PATH]);
}

function prepareDevice(serial) {
  run("adb", ["-s", serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"]);
  run("adb", ["-s", serial, "shell", "wm", "dismiss-keyguard"]);
  runOptional("adb", ["-s", serial, "shell", "settings", "put", "system", "screen_off_timeout", "600000"]);
  runOptional("adb", ["-s", serial, "shell", "svc", "power", "stayon", "true"]);
}

function clearAppData(serial) {
  const result = spawnSync("adb", ["-s", serial, "shell", "pm", "clear", PACKAGE_NAME], {
    cwd: REPO_ROOT,
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status === 0) return;

  console.warn(`WARN pm clear failed on ${serial}; falling back to uninstall/install`);
  run("adb", ["-s", serial, "uninstall", PACKAGE_NAME]);
  installApp(serial);
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

function runOptional(command, args) {
  const result = spawnSync(command, args, {
    cwd: REPO_ROOT,
    stdio: "ignore",
    env: process.env,
  });
  if (result.error) {
    console.warn(`WARN optional command failed: ${command} ${args.join(" ")}: ${result.error.message}`);
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

function makeFixture(prefix) {
  if (FIXTURE_KIND === "review-public") {
    return {
      catalogId: process.env.APPIUM_E2E_CATALOG_ID || "courses",
      authorUid: `${prefix}_owner`,
      questId: `${prefix}_quest`,
      sectionId: `${prefix}_section`,
      themeId: `${prefix}_theme`,
      lessonId: `${prefix}_lesson`,
      easyQuestionId: `${prefix}_question`,
      hardQuestionId: `${prefix}_hard_question`,
      questTitle: `Pixel live review ${prefix}`,
      sectionTitle: `Pixel section ${prefix}`,
      themeTitle: `Pixel theme ${prefix}`,
      lessonTitle: `Pixel lesson ${prefix}`,
      easyQuestionText: "Сколько будет два плюс два?",
      hardQuestionText: "Сколько будет три плюс три?",
      easyCorrect: "Четыре",
      easyWrong: "Пять",
      hardCorrect: "Четыре",
      hardWrong: "Пять",
    };
  }
  return {
    catalogId: "courses",
    authorUid: `${prefix}_author`,
    questId: `${prefix}_quest`,
    sectionId: `${prefix}_section`,
    themeId: `${prefix}_theme`,
    lessonId: `${prefix}_lesson`,
    easyQuestionId: `${prefix}_easy_question`,
    hardQuestionId: `${prefix}_hard_question`,
    questTitle: `Appium Quest ${prefix}`,
    sectionTitle: `Appium Section ${prefix}`,
    themeTitle: `Appium Theme ${prefix}`,
    lessonTitle: `Appium Lesson ${prefix}`,
    easyQuestionText: `Easy Appium Question ${prefix}`,
    hardQuestionText: `Hard Appium Question ${prefix}`,
    easyCorrect: `Easy Correct ${prefix}`,
    easyWrong: `Easy Wrong ${prefix}`,
    hardCorrect: `Hard Correct ${prefix}`,
    hardWrong: `Hard Wrong ${prefix}`,
  };
}

function safeDeleteSession(driver) {
  return driver.deleteSession().catch((error) => {
    console.warn(`WARN deleteSession failed: ${error.message}`);
  });
}

function stopProcess(child) {
  if (child.exitCode != null) return;
  child.kill("SIGTERM");
  child.stdout?.destroy();
  child.stderr?.destroy();
  setTimeout(() => {
    if (child.exitCode == null) child.kill("SIGKILL");
  }, 2000).unref();
  child.unref();
}

async function closeFirestore(db) {
  if (typeof db.terminate !== "function") return;
  try {
    await Promise.race([
      db.terminate(),
      new Promise((resolve) => setTimeout(resolve, 2000)),
    ]);
  } catch (error) {
    console.warn(`WARN Firestore terminate failed: ${error.message}`);
  }
}

function escapeUiSelector(value) {
  return String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function escapeSql(value) {
  return String(value).replace(/'/g, "''");
}

function safeId(value) {
  return String(value).replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
}

function slug(value) {
  return safeId(value).toLowerCase() || "diagnostic";
}

function envBool(name, defaultValue) {
  const value = process.env[name];
  if (value == null || value === "") return defaultValue;
  return /^(1|true|yes|y)$/i.test(value);
}

function optionalNumber(value) {
  if (value == null || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
