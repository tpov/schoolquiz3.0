"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const crypto = require("node:crypto");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const NOW = Date.parse("2026-08-01T00:00:00Z");

function world(extra = {}) {
  return {
    "users/author": {uid: "author", boxCount: 0},
    "profiles/curator": {adminLevel: 100}, "profiles/player": {},
    "catalogs/catalog": {questType: "REGULAR"},
    "quests/quest": {catalogId: "catalog", authorUid: "author", visibleOn: ["archive"], averageRating: 3},
    "sections/section": {questId: "quest"}, "themes/theme": {sectionId: "section"},
    "lessons/lesson": {themeId: "theme", version: 1, authorUid: "author", publishedAtMs: NOW - 2000,
      reviewSubmissionId: "submission", reviewApprovedAtMs: NOW - 1000},
    "lessons/sibling": {themeId: "theme", version: 1},
    ...extra,
  };
}
function call(method, uid, data) { return functions[method].run({auth: {uid}, data}); }
function move(targetShelf, mutationId, uid = "curator") {
  return call("setLessonPlacement", uid, {lessonId: "lesson", targetShelf, mutationId, reason: "Independent review complete"});
}
function seedReceipt(uid, attemptId, patch = {}) {
  const hash = crypto.createHash("sha256").update(attemptId).digest("hex");
  const safe = `${hash.slice(0, 12)}_${attemptId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
  const path = `result_events_public/${hash.slice(0, 2)}/events/${safe}`;
  store.documents.set(path, {userId: uid, lessonId: "lesson", activityKind: "ARENA",
    settlementReceipt: {uid, lessonId: "lesson", attemptId, status: "SETTLED", completed: true,
      settledAtMs: NOW - 100, ...patch}});
}

test("real handlers require curator and the lesson's own review approval", async () => {
  store.reset(world());
  const spoofed = await call("setLessonPlacement", "player", {uid: "curator", lessonId: "lesson",
    targetShelf: "arena", mutationId: "spoofed", reason: "claimed authority"});
  assert.equal(spoofed.result.code, "CURATOR_REQUIRED");
  // Одобрение с 4.19 доказывают поля публикации в документе урока: без них контекст арены
  // отвечает отказом, и никакая подделка заданий/записей (их больше нет) не помогает.
  for (const patch of [{reviewApprovedAtMs: null}, {reviewSubmissionId: null}]) {
    store.reset(world());
    store.documents.set("lessons/lesson", {...store.documents.get("lessons/lesson"), ...patch});
    const refused = await move("arena", "unverified");
    assert.equal(refused.result.code, "REVIEW_APPROVAL_REQUIRED");
    assert.equal(store.documents.get("users/author").boxCount, 0);
  }
});

test("real lesson promotion pays each milestone once and leaves the archive parent and sibling alone", async () => {
  store.reset(world());
  const quest = structuredClone(store.documents.get("quests/quest"));
  assert.equal((await move("arena", "a")).status, "APPLIED");
  assert.equal((await move("tournament", "t")).status, "APPLIED");
  assert.equal(store.documents.get("users/author").boxCount, 15);
  await move("retired", "retire"); await move("arena", "back"); await move("tournament", "again");
  assert.equal(store.documents.get("users/author").boxCount, 15);
  await move("home", "home"); await move("home", "same-home");
  assert.equal(store.documents.get("users/author").boxCount, 35);
  assert.equal(store.documents.get("lessons/lesson").arenaShelf, "home");
  assert.equal(store.documents.get("lessons/sibling").arenaShelf, undefined);
  assert.deepEqual(store.documents.get("quests/quest"), quest);
  assert.equal(store.documents.get("catalogs/catalog/sync_changes/lesson_lesson").type, "lesson");
});

test("archive course marker does not retire its reviewed lesson; actual retirement and archived descendants do", async () => {
  const course = {catalogId: "catalog", authorUid: "author", visibleOn: ["archive"], archived: true};
  store.reset(world({"quests/quest": course}));
  assert.equal((await move("arena", "archive-child")).status, "APPLIED");
  assert.deepEqual(store.documents.get("quests/quest"), course);
  for (const patch of [
    {"quests/quest": {...course, visibleOn: []}},
    {"themes/theme": {sectionId: "section", archived: true}},
    {"sections/section": {questId: "quest", archived: true}},
  ]) {
    store.reset(world({"quests/quest": course, ...patch}));
    const rejected = await move("arena", "unpublished-child");
    assert.equal(rejected.status, "REJECTED");
    assert.equal(store.documents.get("lesson_placements/lesson").shelf, "review");
    assert.equal(store.documents.get("lesson_placements/lesson").lastRejection.code, "LESSON_NOT_PUBLISHED");
  }
});

test("queued rating waits for settlement and changes one lesson only, with an immutable replay receipt", async () => {
  store.reset(world());
  const data = {mutationId: "rate", lessonId: "lesson", attemptId: "played", rating: 3};
  await assert.rejects(call("rateArenaLesson", "player", data), {code: "unavailable"});
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith("arena_mutations/")).length, 0);
  seedReceipt("player", "played");
  const result = await call("rateArenaLesson", "player", data);
  assert.equal(result.status, "APPLIED");
  assert.equal(store.documents.get("lesson_ratings/lesson").ratingSum, 3);
  assert.equal(store.documents.get("lessons/sibling").averageRating, undefined);
  assert.equal(store.documents.get("quests/quest").averageRating, 3);
  const clock = store.documents.get("catalogs/catalog/sync_changes/lesson_lesson").changedAtMs;
  assert.deepEqual(await call("rateArenaLesson", "player", data), result);
  assert.equal(store.documents.get("catalogs/catalog/sync_changes/lesson_lesson").changedAtMs, clock);
  await assert.rejects(call("rateArenaLesson", "player", {...data, rating: 1}), {code: "already-exists"});
  seedReceipt("author", "self");
  assert.equal((await call("rateArenaLesson", "author", {...data, mutationId: "self", attemptId: "self"})).result.code, "SELF_RATING");
});

test("box award and public placement rollback together after a failed commit", async () => {
  store.reset(world()); await move("arena", "a");
  const before = structuredClone([...store.documents]);
  store.fail = (kind, path) => kind === "commit" && path === "users/author" ? "commit lost" : null;
  await assert.rejects(move("tournament", "t"), error => {
    assert.equal(error.code, "internal");
    assert.equal(error.message, "Arena operation could not be completed");
    assert.doesNotMatch(error.message, /commit lost/);
    return true;
  });
  assert.deepEqual(structuredClone([...store.documents]), before);
  store.fail = null;
  await move("tournament", "t");
  assert.equal(store.documents.get("users/author").boxCount, 15);
});

test("lesson journal advances on repeated server milliseconds and ignores client clock and course rating", async () => {
  const originalNow = Date.now;
  Date.now = () => NOW;
  try {
    store.reset(world());
    seedReceipt("player", "played");
    const payload = {userId: "player", ratingId: "vote-1", attemptId: "played", rating: 1,
      lessonId: "lesson", catalogId: "catalog", questId: "quest", sectionId: "section", themeId: "theme",
      ratedAtMs: NOW + 10_000_000, createdAtMs: NOW + 10_000_000};
    const first = await call("submitQuestRatingEvents", "player", {ratings: [payload]});
    assert.equal(first.receipts[0].status, "APPLIED");
    assert.equal(store.documents.get("catalogs/catalog/sync_changes/lesson_lesson").changedAtMs, NOW);
    await call("submitQuestRatingEvents", "player", {ratings: [{...payload, ratingId: "vote-2", rating: 2}]});
    assert.equal(store.documents.get("catalogs/catalog/sync_changes/lesson_lesson").changedAtMs, NOW + 1);
    assert.equal(store.documents.get("lesson_ratings/lesson").ratingCount, 1);
    assert.equal(store.documents.get("lesson_ratings/lesson").ratingSum, 2);
    assert.equal(store.documents.get("quests/quest").averageRating, 3);
    assert.equal([...store.documents.keys()].filter((path) => path.startsWith("quest_rating_dirty_")).length, 0);
    const legacy = await call("submitQuestRatingEvents", "player", {ratings: [{...payload, ratingId: "old", attemptId: null}]});
    assert.equal(legacy.receipts[0].status, "LEGACY_RECORDED");
    assert.equal(store.documents.get("lesson_ratings/lesson").ratingCount, 1);
  } finally { Date.now = originalNow; }
});
