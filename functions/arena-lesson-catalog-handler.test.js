"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("node:module");
const path = require("node:path");
const fs = require("node:fs");
const SOURCE = process.env.ARENA_CATALOG_SOURCE || (fs.existsSync(path.join(__dirname, "_firestore-stub.js")) ? __dirname :
  path.resolve(__dirname, "../../../integration-edit/functions"));
const CATALOG = process.env.ARENA_CATALOG_MODULE || (fs.existsSync(path.join(__dirname, "arena-lesson-catalog.js")) ?
  path.join(__dirname, "arena-lesson-catalog.js") : path.resolve(__dirname, "../../arena-catalog/functions/arena-lesson-catalog.js"));
const CANDIDATE_INDEX = path.join(__dirname, "index.js");
const fromSource = name => require(path.join(SOURCE, name));
const {createFirestoreStub} = fromSource("_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin", {paths: [SOURCE]});
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const originalResolve = Module._resolveFilename;
let functions;
try {
  // Draft has ONLY the candidate index and this test. All existing dependencies are loaded
  // unchanged. The new catalog is real, not a stubbed factory; it receives index's real loader.
  Module._resolveFilename = function(request, parent, ...rest) {
    if (parent?.filename === CANDIDATE_INDEX && request.startsWith("./")) {
      const target = request === "./arena-lesson-catalog" ? CATALOG : path.resolve(SOURCE, request);
      return originalResolve.call(this, target, parent, ...rest);
    }
    if (parent?.filename === CATALOG && request === "./arena-policy") {
      return originalResolve.call(this, path.join(SOURCE, "arena-policy.js"), parent, ...rest);
    }
    return originalResolve.call(this, request, parent, ...rest);
  };
  functions = require(CANDIDATE_INDEX);
} finally { Module._resolveFilename = originalResolve; }
const baselineFunctions = fromSource("index");
const {publishLesson} = fromSource("_scored-lesson-fixture");
const NOW = Date.parse("2026-09-07T00:00:00Z");
const call = (payload = {}, uid = "player") => functions.listArenaLessons.run({auth: {uid}, data: payload});
function lessonWorld(lessonId = "lesson") {
  return {
    [`catalogs/catalog-${lessonId}`]: {questType: "REGULAR"},
    [`quests/quest-${lessonId}`]: {catalogId: `catalog-${lessonId}`, authorUid: "author", visibleOn: ["archive"]},
    [`sections/section-${lessonId}`]: {questId: `quest-${lessonId}`}, [`themes/theme-${lessonId}`]: {sectionId: `section-${lessonId}`},
    [`lessons/${lessonId}`]: {themeId: `theme-${lessonId}`, version: 1, title: `Title ${lessonId}`,
      reviewSubmissionId: `submission-${lessonId}`, reviewApprovedAtMs: NOW - 1},
    [`lesson_placements/${lessonId}`]: {lessonId, shelf: "arena", authorUid: "author", contentVersion: 1,
      previousShelf: "review", placementVersion: 1, updatedAtMs: NOW, lastReason: "PRIVATE_DECISION", decidedByUid: "PRIVATE_ACTOR"},
  };
}
function world() {
  const bank = publishLesson("lesson");
  return {...lessonWorld(), [bank.keyDocumentPath]: bank.keyDocument,
    ...Object.fromEntries(bank.documents.map(row => [`questions/${row.id}`, row])),
    "users/player": {nolics: 99, lifePoints: 100, reservedLifePoints: 5, reservedNolics: 2},
    "users/author": {nickname: "PRIVATE_NICKNAME", email: "PRIVATE_EMAIL"},
    "profiles/author": {nickname: "PRIVATE_PROFILE"},
    "lesson_placements/lesson/history/h": {actorUid: "PRIVATE_REVIEWER", reason: "PRIVATE_APPEAL"},
    "arena_entry_tickets/existing": {uid: "player", status: "RESERVED", priceNolics: 2},
    "currency_ledger/existing": {currency: "NOLICS", amount: 2},
  };
}
const snapshot = () => structuredClone([...store.documents]);
function noEffects(before) { assert.deepEqual(snapshot(), before); assert.equal(store.writes.length, 0); }
function safeError(error, code, reason) {
  assert.equal(error.code, code);
  assert.deepEqual(error.details, {reason});
  assert.equal(error.message, "Arena lessons could not be loaded");
  assert.ok(!JSON.stringify({message: error.message, details: error.details}).includes("PRIVATE_"));
  return true;
}

test("actual callable uses real catalog and root loader with no wallet, ticket or audit reads/writes", async () => {
  store.reset(world()); const before = snapshot();
  const result = await call();
  assert.equal(result.status, "OK"); assert.equal(result.entryAvailability, "PREPARE_REQUIRED");
  assert.equal(result.totalEligibleLessons, 1); assert.equal(result.lessons[0].lessonId, "lesson");
  assert.equal(result.lessons[0].authorUid, "author"); assert.equal(result.lessons[0].title, "Title lesson");
  assert.equal(result.lessons[0].priceNolics, 1); assert.equal(result.lessons[0].rating.count, 0);
  assert.ok(!JSON.stringify(result).includes("PRIVATE_"));
  const forbidden = ["payload", "questionId", "questions", "gradingKey", "correctOptionId", "reviewerUid", "actorUid", "lastReason", "email", "nickname"];
  const walk = value => {
    if (!value || typeof value !== "object") return;
    for (const [key, child] of Object.entries(value)) { assert.ok(!forbidden.includes(key), key); walk(child); }
  };
  walk(result); noEffects(before);
  // 4.19: журнала ревьюеров больше нет — каталог его не читает вовсе.
  assert.ok(store.operations.every(op => !op.path.startsWith("admin/review/")));
  assert.ok(store.operations.every(op => !/^(users|profiles|questions|question_keys|arena_entry_tickets|currency_ledger)\//.test(op.path) && !op.path.includes("/history/")));
});

test("a lesson without review approval is excluded rather than trusting client eligibility", async () => {
  const seed = world(); seed["lessons/lesson"].reviewApprovedAtMs = null;
  store.reset(seed); const before = snapshot();
  assert.deepEqual((await call()).lessons, []); noEffects(before);
});

test("corrupt existing rating maps to generic unavailable with CATALOG_INVALID, not score zero", async () => {
  store.reset({...world(), "lesson_ratings/lesson": {ratingSum: null, ratingCount: 0, ratingVersion: 0}});
  const before = snapshot();
  await assert.rejects(call(), error => safeError(error, "unavailable", "CATALOG_INVALID")); noEffects(before);
});

test("changed cursor maps to explicit CATALOG_CHANGED without allocating tickets or mutation receipts", async () => {
  store.reset({...world(), ...lessonWorld("second")});
  const first = await call({pageSize: 1}); assert.ok(first.nextCursor);
  store.documents.set("lessons/second", {...store.documents.get("lessons/second"), title: "New title"});
  const before = snapshot();
  await assert.rejects(call({cursor: first.nextCursor}), error => safeError(error, "failed-precondition", "CATALOG_CHANGED")); noEffects(before);
});

test("real placement overflow maps to resource-exhausted and never yields a truncated page", async () => {
  store.reset(Object.fromEntries(Array.from({length: 1001}, (_, n) => [`lesson_placements/l${n}`, {shelf: "arena"}])));
  const before = snapshot();
  await assert.rejects(call(), error => safeError(error, "resource-exhausted", "CATALOG_TOO_LARGE")); noEffects(before);
});

test("SDK failure text, document paths and details do not escape the callable", async () => {
  store.reset(world(), {fail: {"themes/theme-lesson": "PRIVATE_SDK_PATH: /private/secret-review"}});
  const before = snapshot();
  await assert.rejects(call(), error => safeError(error, "internal", "SERVER_FAILURE")); noEffects(before);
});

test("unknown SDK codes are not echoed even when uppercase or shaped like a known transport status", async () => {
  for (const code of [5, "PRIVATE_INTERNAL_STATE", "unavailable", "__proto__"]) {
    store.reset(world()); const before = snapshot();
    const runTransaction = store.db.runTransaction;
    store.db.runTransaction = async () => { throw Object.assign(new Error("PRIVATE_DATABASE_MESSAGE"), {code, details: {private: "PRIVATE_BODY"}}); };
    try { await assert.rejects(call(), error => safeError(error, "internal", "SERVER_FAILURE")); }
    finally { store.db.runTransaction = runTransaction; }
    noEffects(before);
  }
});

test("identical reads preserve snapshot and page results without any permanent read receipt", async () => {
  store.reset(world()); const before = snapshot();
  const first = await call(); const second = await call();
  assert.deepEqual({...first, serverNowMs: 0}, {...second, serverNowMs: 0}); noEffects(before);
});
