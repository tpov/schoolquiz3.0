"use strict";

// Демонстрационные фикстуры матрицы Арены (server half истории 3.3): роли и банки по
// scripts/demo-fixtures/manifest.json. Фикстуры только готовят условия — оцениваемые
// прохождения, рейтинги и выплаты выполняет приложение.
//
// Идемпотентно: существующая seeded-фикстура сверяется с манифестом и оставляется как
// есть, отсутствующая — создаётся; расхождение — именованный отказ (условия сцены
// кто-то изменил, молча править их нельзя). Закреплённые предпосылки (экспорт-era
// цепочки ac_* и их авторы) только сверяются и никогда не пишутся. Трогаются только
// demoarena_-именованные пути и fixture-owned доки из манифеста. Никаких очисток,
// рестартов, деплоев и кредов: admin SDK против эмуляторов демо-бэкенда (правила
// безопасности на этом пути не действуют — это и есть санционированный путь засева
// условий, план §2 Шаг 4).
//
// Запуск (см. manifest.json → seeding.command):
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090 \
//   GCLOUD_PROJECT=demo-schoolquiz-mvp node scripts/demo-fixtures/seed-demo-fixtures.js

const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", auth: "127.0.0.1:19090"};

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, HOSTS.auth, "auth must be the demo backend");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only tool");

// firebase-admin живёт в functions/node_modules (в maestro-контейнере тот же файл под /repo).
const admin = require(path.join(__dirname, "..", "..", "functions", "node_modules", "firebase-admin"));

const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
assert.equal(manifest.schemaVersion, 2, "unsupported manifest schema");

admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const auth = admin.auth();

const created = [];
const verified = [];

async function existingFields(docPath) {
  const snapshot = await db.doc(docPath).get();
  return snapshot.exists ? snapshot.data() : null;
}

// Seeded-фикстура принадлежит манифесту: совпало — оставлено, расходится — отказ, нет — создано.
async function assertOrCreateDoc(docPath, expectedFields) {
  const existing = await existingFields(docPath);
  if (existing !== null) {
    for (const [key, value] of Object.entries(expectedFields)) {
      assert.deepEqual(existing[key] === undefined ? null : existing[key],
        value === undefined ? null : value, `fixture drift at ${docPath}: field ${key}`);
    }
    verified.push(docPath);
    return;
  }
  await db.doc(docPath).set({...expectedFields});
  created.push(docPath);
}

// Закреплённая предпосылка принадлежит экспорту: только сверяется, никогда не пишется.
async function assertPinnedDoc(docPath, expectedFields) {
  const existing = await existingFields(docPath);
  assert.ok(existing !== null, `pinned precondition missing: ${docPath}`);
  for (const [key, value] of Object.entries(expectedFields)) {
    assert.equal(existing[key] === undefined ? null : existing[key],
      value === undefined ? null : value, `pinned drift at ${docPath}: field ${key}`);
  }
  verified.push(docPath);
}

async function assertSeededAuthUser({uid, displayName}) {
  try {
    const user = await auth.getUser(uid);
    assert.equal(user.displayName || null, displayName || null, `fixture drift at auth:${uid}: displayName`);
    verified.push(`auth/${uid}`);
  } catch (error) {
    assert.equal(error.code, "auth/user-not-found", `auth:${uid}: unexpected auth error`);
    const request = {uid};
    if (displayName) request.displayName = displayName;
    await auth.createUser(request);
    created.push(`auth/${uid}`);
  }
}

async function assertPinnedAuthUser(uid) {
  await auth.getUser(uid).then(
    (user) => verified.push(`auth/${uid}`),
    (error) => assert.fail(`pinned auth account ${uid} missing: ${error.code}`),
  );
}

function bankRowSpecs(bank) {
  const curatorUid = manifest.roles.find((role) => role.id === "curator-m").uid;
  const {authorUid, questId, lessonVersion, reviewSubmissionId, lessonId} = {
    authorUid: bank.chain.authorUid,
    questId: bank.chain.questId,
    lessonVersion: bank.chain.lessonVersion,
    reviewSubmissionId: bank.chain.reviewSubmissionId,
    lessonId: bank.lessonId,
  };
  return [
    [`admin/review/lessons/${lessonId}`, {
      status: "PUBLISHED", ownerUid: authorUid, questId, submissionId: reviewSubmissionId,
      sourceLanguages: ["en"],
    }],
    [`admin/review/lessons/${lessonId}/reviews/${lessonId}-rec-testing`, {
      kind: "TESTING", reviewerUid: curatorUid, reviewerLevelAtSubmit: 100, score: 3,
      submissionId: reviewSubmissionId, acceptedByServer: true,
    }],
    [`admin/review/lessons/${lessonId}/reviews/${lessonId}-rec-logic`, {
      kind: "LOGIC", reviewerUid: curatorUid, reviewerLevelAtSubmit: 100, score: 3,
      submissionId: reviewSubmissionId, acceptedByServer: true,
    }],
    [`lesson_placements/${lessonId}`, {
      shelf: "arena", previousShelf: "review", authorUid,
      contentVersion: lessonVersion, placementVersion: 1,
    }],
  ];
}

async function seedRating(bank) {
  const expected = {
    ratingSum: bank.rating.ratingSum,
    ratingCount: bank.rating.ratingCount,
    ratingVersion: bank.rating.ratingVersion,
  };
  const docPath = `lesson_ratings/${bank.lessonId}`;
  if (bank.rating.seeded) return assertOrCreateDoc(docPath, expected);
  return assertPinnedDoc(docPath, {
    ...expected,
    authorUid: bank.chain.authorUid,
  });
}

async function seedRole(role) {
  if (!role.seeded) return assertPinnedAuthUser(role.uid);
  await assertSeededAuthUser({uid: role.uid, displayName: role.auth ? role.auth.displayName : null});
  if (role.profile) await assertOrCreateDoc(role.profile.doc, role.profile.fields || {});
  if (role.economy) await assertOrCreateDoc(role.economy.doc, role.economy.fields);
}

async function seedBank(bank) {
  // Закреплённые предпосылки цепочки (существуют с экспорта/08.10) — только сверяются.
  await assertPinnedDoc(`lessons/${bank.lessonId}`, {
    version: bank.chain.lessonVersion,
    themeId: bank.chain.themeId,
  });
  assert.equal(await existingFields(`lessons/${bank.lessonId}`).then((data) => data.archived === true), false,
    `pinned lesson ${bank.lessonId} must not be archived`);
  await assertPinnedDoc(`quests/${bank.chain.questId}`, {
    authorUid: bank.chain.authorUid,
    catalogId: bank.chain.catalogId,
  });
  await assertPinnedDoc(`catalogs/${bank.chain.catalogId}`, {questType: "REGULAR"});
  for (const [docPath, fields] of bankRowSpecs(bank)) await assertOrCreateDoc(docPath, fields);
  await seedRating(bank);
}

(async () => {
  for (const role of manifest.roles) await seedRole(role);
  for (const bank of manifest.banks) await seedBank(bank);
  console.log(`demo fixtures: created ${created.length}, verified ${verified.length}`);
  for (const item of created) console.log("  created", item);
  for (const item of verified) console.log("  verified", item);
})().catch((error) => {
  console.error("demo fixtures seeding refused:", error.message);
  process.exitCode = 1;
});
