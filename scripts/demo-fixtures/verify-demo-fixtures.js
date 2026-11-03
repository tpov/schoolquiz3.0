"use strict";

// Верификация демонстрационных фикстур матрицы Арены: продакшн-юниверс
// createArenaEligibleUniverse (functions/arena-eligible-universe.js) с продакшн-лоадером
// контекста урока (functions/index.js, захватывается при загрузке как в
// scripts/arena-eligible-universe-emulator-test.js) запускается против демо-бэкенда и
// обязан показать ровно банки манифеста с ценами 1 и 100. Только чтение
// (readOnly-транзакция); ничего не создаёт и не правит.
//
// Запуск (см. manifest.json → verification.command):
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 GCLOUD_PROJECT=demo-schoolquiz-mvp \
//   node scripts/demo-fixtures/verify-demo-fixtures.js

const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOST = "127.0.0.1:18090";

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOST, "firestore must be the demo backend");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only tool");

const functionsDir = path.join(__dirname, "..", "..", "functions");
const admin = require(path.join(functionsDir, "node_modules", "firebase-admin"));
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
assert.equal(manifest.schemaVersion, 2, "unsupported manifest schema");

admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

// Продакшн-лоадер контекста урока живёт в недрах index.js и не экспортируется. Загружаем
// index.js в свежем процессе, перехватывая фабрику каталога, чтобы получить тот же самый
// loadLessonContext, которым сервер считает элигибельность (у index.js нет module.exports).
const catalog = require(path.join(functionsDir, "arena-lesson-catalog.js"));
const actualFactory = catalog.createArenaLessonCatalog;
let loadArenaLessonContext = null;
catalog.createArenaLessonCatalog = (args) => {
  assert.equal(loadArenaLessonContext, null, "index must be captured once in a fresh process");
  loadArenaLessonContext = args.loadLessonContext;
  return actualFactory(args);
};
require(path.join(functionsDir, "index.js"));
assert.equal(typeof loadArenaLessonContext, "function", "production lesson context loader not captured");

const {createArenaEligibleUniverse} = require(path.join(functionsDir, "arena-eligible-universe.js"));

(async () => {
  const universe = await db.runTransaction(async (transaction) => {
    const reader = createArenaEligibleUniverse({db, loadLessonContext: loadArenaLessonContext});
    return reader.forTransaction(transaction).read();
  }, {readOnly: true});

  const expected = manifest.banks.map((bank) => ({
    id: bank.id,
    lessonId: bank.lessonId,
    authorUid: bank.chain.authorUid,
    contentVersion: bank.chain.lessonVersion,
    priceNolics: bank.expectedPriceNolics,
  }));

  const byLesson = new Map(universe.rows.map((row) => [row.lessonId, row]));
  console.log(`eligible universe: ${universe.rows.length} row(s), ${universe.readCount} trusted read(s)`);
  for (const row of universe.rows) {
    console.log(`  ${row.lessonId} price=${row.priceNolics} author=${row.authorUid} v${row.contentVersion}`);
  }
  for (const want of expected) {
    const row = byLesson.get(want.lessonId);
    assert.ok(row, `bank ${want.id} (${want.lessonId}) is not arena-eligible`);
    assert.equal(row.priceNolics, want.priceNolics, `bank ${want.id} price`);
    assert.equal(row.authorUid, want.authorUid, `bank ${want.id} author`);
    assert.equal(row.contentVersion, want.contentVersion, `bank ${want.id} contentVersion`);
  }
  // Цены относительны по всему юниверсу: 1 — нижний уровень шкалы, 100 — верхний.
  // Нижний уровень легитимно разделяется (2★ = 42/21 и «без рейтинга» = 40/20 — одна
  // дробь 2.0), поэтому минимум не обязан быть единственным; максимум уникален по
  // определению шкалы. Любая будущая фикстура, сдвинувшая экстремум, роняет проверку.
  const byPrice = new Map(universe.rows.map((row) => [row.priceNolics, row.lessonId]));
  assert.equal(byPrice.get(100), expected.find((want) => want.priceNolics === 100).lessonId,
    "bank-high must be the maximum-priced row (price 100)");

  if (manifest.easyBank) {
    const easy = manifest.easyBank;
    const authorUid = manifest.roles.find((role) => role.id === easy.authorRole).uid;
    const row = byLesson.get(easy.lessonId);
    assert.ok(row, `easy bank ${easy.lessonId} is not arena-eligible`);
    assert.equal(row.priceNolics, easy.expectedPriceNolics, "easy bank price");
    assert.equal(row.authorUid, authorUid, "easy bank author");
    assert.equal(row.contentVersion, easy.lessonVersion, "easy bank contentVersion");
    // Производственный контекст (тем же загрузчиком, которым сервер считает элигибельность):
    // урок опубликован, одобрен ревью и принадлежит REGULAR-каталогу.
    const context = await db.runTransaction(async (transaction) =>
      loadArenaLessonContext(transaction, easy.lessonId), {readOnly: true});
    assert.ok(context, "easy bank lesson context missing");
    assert.equal(context.authorUid, authorUid, "easy bank context author");
    assert.equal(context.questType, "REGULAR", "easy bank context quest type");
    assert.equal(context.published, true, "easy bank context must be published");
    assert.equal(context.reviewApproved, true, "easy bank context must be review-approved");
    assert.equal(context.contentVersion, easy.lessonVersion, "easy bank context version");
    // EASY-банк обязан нести EASY-вопросы с редактируемым полным содержимым — ровно то,
    // чего лишён каждый ac_*/aeu_* банк (там loadEntry отказывает NO_QUESTIONS_FOR_DIFFICULTY).
    const questionSnapshot = await db.collection("questions")
      .where("lessonId", "==", easy.lessonId).get();
    const questionDocs = questionSnapshot.docs.map((doc) => doc.data() || {});
    const {redact, STATUS} = require(path.join(functionsDir, "question-redaction.js"));
    assert.equal(questionDocs.length, easy.questions.length, "easy bank question count");
    for (const [index, want] of easy.questions.entries()) {
      const doc = questionDocs.find((q) => q.id === want.questionId);
      assert.ok(doc, `easy bank question ${want.questionId} missing`);
      assert.equal(doc.difficulty, "EASY", `easy bank question ${want.questionId} difficulty`);
      assert.equal(doc.order, index, `easy bank question ${want.questionId} order`);
      const split = redact(String(doc.payload), "EASY", {questionId: want.questionId});
      assert.equal(split.status, STATUS.REDACTED, `easy bank question ${want.questionId} redaction`);
      assert.equal(JSON.parse(split.publicPayload).correctOptionId, undefined,
        `easy bank question ${want.questionId} public half must not leak the grading key`);
    }
    console.log(`VERIFIED: easy bank ${easy.lessonId} is eligible at price ${row.priceNolics}, ` +
      `review-approved, with ${easy.questions.length} EASY redactable question(s)`);
  }
  console.log("VERIFIED: arena banks hold prices 1 and 100 exactly as the manifest expects");
})().catch((error) => {
  console.error("demo fixtures verification failed:", error.message);
  process.exitCode = 1;
});
