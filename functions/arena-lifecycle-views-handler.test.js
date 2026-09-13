"use strict";

// Exercises the candidate through its real callable boundary.  The lifecycle reader's pure-suite
// proves pagination and individual projections; this suite proves the thing a client actually
// reaches: Auth uid is authoritative, errors are generic, and index.js never accidentally wires a
// raw placement/history/royalty document to the wire.

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const test = require("node:test");
const Module = require("node:module");
const path = require("node:path");
const {createFirestoreStub} = require("./_firestore-stub");

const CANDIDATE_DIR = __dirname;
const CANDIDATE_INDEX = path.join(CANDIDATE_DIR, "index.js");
const CANDIDATE_LIFECYCLE = path.join(CANDIDATE_DIR, "arena-lifecycle-views.js");
// In its permanent location this test lives in functions/, so the ordinary npm test is entirely
// self-contained.  An explicit override is only for composing the staged candidate with a source
// mirror while it is being reviewed outside that directory.
const SOURCE = process.env.ARENA_LIFECYCLE_SOURCE || CANDIDATE_DIR;
// Test-only key; production receives the same-shaped value through a bound Firebase secret.
process.env.ARENA_LIFECYCLE_CURSOR_SECRET = Buffer.alloc(32, 11).toString("base64url");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin", {paths: [SOURCE]});
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;

// The candidate has only index.js and its new reader staged beside this test.  All unchanged
// modules deliberately resolve to the authoritative source, so this remains a composition test
// rather than a copied implementation.
const originalResolve = Module._resolveFilename;
let functions;
try {
  Module._resolveFilename = function(request, parent, ...rest) {
    if (parent?.filename === CANDIDATE_INDEX && request.startsWith("./")) {
      const target = request === "./arena-lifecycle-views" ? CANDIDATE_LIFECYCLE : path.join(SOURCE, request);
      return originalResolve.call(this, target, parent, ...rest);
    }
    if (parent?.filename === CANDIDATE_LIFECYCLE && request === "./currency-ledger") {
      return originalResolve.call(this, path.join(CANDIDATE_DIR, "currency-ledger.js"), parent, ...rest);
    }
    if (parent?.filename === CANDIDATE_INDEX &&
      (request === "firebase-admin" || request.startsWith("firebase-functions"))) {
      return require.resolve(request, {paths: [SOURCE]});
    }
    return originalResolve.call(this, request, parent, ...rest);
  };
  functions = require(CANDIDATE_INDEX);
} finally {
  Module._resolveFilename = originalResolve;
}

const NOW = Date.parse("2026-09-12T12:00:00Z");
const ENDPOINTS = Object.freeze([
  "listOwnedLessonLifecycle",
  "listCuratorLessonLifecycle",
  "getLessonLifecycle",
  "listLessonLifecycleHistory",
  "listMyArenaRoyalty",
  "listMyQuestReviewSubmissions",
]);
// SHA-256 of JSON.stringify(sorted accepted non-lifecycle index.js export names).  The snapshot
// includes the explicitly reviewed Arena season finalization callables and its scheduler. Keeping
// it frozen catches an accidental deletion or unrelated new callable, unlike comparing this
// module to itself when SOURCE defaults to __dirname.
const ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256 = "c6582f3aca2e920a58d871da0ff611a60054351638b4801aa186aa251785b029";
function exportSetHash(names) {
  return createHash("sha256").update(JSON.stringify([...names].sort())).digest("hex");
}

function call(name, uid, data) {
  return functions[name].run({auth: uid === undefined ? undefined : {uid}, data});
}

function safeError(error, code, reason) {
  assert.equal(error.code, code);
  assert.deepEqual(error.details, {reason});
  assert.equal(error.message, "Lesson lifecycle could not be loaded");
  const publicError = JSON.stringify({message: error.message, details: error.details});
  for (const privateText of ["PRIVATE", "secret", "credentials", "/private/"]) {
    assert.equal(publicError.includes(privateText), false, privateText);
  }
  return true;
}

function hierarchy(lessonId, authorUid, patch = {}) {
  const ids = {catalog: `catalog-${lessonId}`, quest: `quest-${lessonId}`,
    section: `section-${lessonId}`, theme: `theme-${lessonId}`, submission: `submission-${lessonId}`};
  return {
    [`catalogs/${ids.catalog}`]: {questType: "REGULAR"},
    [`quests/${ids.quest}`]: {catalogId: ids.catalog, authorUid, visibleOn: ["archive"]},
    [`sections/${ids.section}`]: {questId: ids.quest},
    [`themes/${ids.theme}`]: {sectionId: ids.section},
    [`lessons/${lessonId}`]: {themeId: ids.theme, version: 1, publishedAtMs: NOW - 100,
      reviewSubmissionId: ids.submission, reviewApprovedAtMs: NOW - 10},
    [`admin/review/lessons/${lessonId}`]: {ownerUid: authorUid, questId: ids.quest,
      submissionId: ids.submission, status: "PUBLISHED", sourceLanguages: ["en"]},
    [`admin/review/lessons/${lessonId}/reviews/testing-${lessonId}`]: {kind: "TESTING",
      reviewerUid: `PRIVATE_TESTER_${lessonId}`, acceptedByServer: true, score: 3,
      reviewerLevelAtSubmit: 100, submissionId: ids.submission},
    [`admin/review/lessons/${lessonId}/reviews/logic-${lessonId}`]: {kind: "LOGIC",
      reviewerUid: `PRIVATE_LOGIC_${lessonId}`, acceptedByServer: true, score: 3,
      reviewerLevelAtSubmit: 100, submissionId: ids.submission},
    [`catalogs/${ids.catalog}/sync_changes/lesson_${lessonId}`]: {changedAtMs: NOW},
    ...patch,
  };
}

function placement(lessonId, authorUid, patch = {}) {
  return {lessonId, authorUid, questType: "REGULAR", shelf: "arena", previousShelf: "review",
    contentVersion: 1, placementVersion: 1, updatedAtMs: NOW - 1,
    lastRejection: {code: "PRIVATE_REJECTION", reason: "PRIVATE_RATIONALE", actorUid: "PRIVATE_ACTOR"},
    futurePrivate: "PRIVATE_PLACEMENT", ...patch};
}
function partialRejection(lessonId) {
  return {lessonId, shelf: "review", lastRejection: {
    code: "REVIEW_APPROVAL_REQUIRED", reason: "PRIVATE_RATIONALE",
    actorUid: "curator", atMs: NOW - 1,
  }, futurePrivate: "PRIVATE_PARTIAL_PLACEMENT"};
}
function rating(lessonId, patch = {}) {
  return {lessonId, authorUid: "PRIVATE_RATING_AUTHOR", ratingSum: 5, ratingCount: 2,
    averageRating: 2.5, ratingVersion: 4, ratingUpdatedAtMs: NOW - 2,
    futurePrivate: "PRIVATE_RATING", ...patch};
}

function history(atMs, patch = {}) {
  return {fromShelf: "review", targetShelf: "arena", status: "APPLIED", code: null,
    decidedAtMs: atMs, actorUid: "PRIVATE_ACTOR", mutationId: "PRIVATE_MUTATION",
    reason: "PRIVATE_RATIONALE", futurePrivate: "PRIVATE_HISTORY", ...patch};
}

function royalty(uid, atMs, delta, patch = {}) {
  return {uid, currency: "nolics", reason: "arena_author_royalty", delta, atMs,
    sourceId: "PRIVATE_TICKET", details: {payerUid: "PRIVATE_PAYER", attemptId: "PRIVATE_ATTEMPT"},
    futurePrivate: "PRIVATE_LEDGER", ...patch};
}
function reviewSubmission(ownerUid, draftId, status, processedAtMs, patch = {}) {
  return {ownerUid, draftId, status, processedAtMs, processed: true,
    questions: [{id: "PRIVATE_QUESTION", payload: "PRIVATE_ANSWER"}],
    draft: {title: "PRIVATE_DRAFT"}, review: {futurePrivate: "PRIVATE_REVIEW"},
    lastError: "PRIVATE_ERROR", ...patch};
}

function world() {
  return {
    "profiles/author": {uid: "author"},
    "profiles/curator": {uid: "curator", adminLevel: 100},
    "profiles/outsider": {uid: "outsider", adminLevel: 0},
    ...hierarchy("lesson-owned-a", "author"),
    ...hierarchy("lesson-owned-b", "author"),
    ...hierarchy("lesson-foreign", "other"),
    ...hierarchy("lesson-partial-rejection", "author"),
    "lesson_placements/lesson-owned-a": placement("lesson-owned-a", "author"),
    "lesson_placements/lesson-owned-b": placement("lesson-owned-b", "author", {shelf: "tournament", previousShelf: "arena"}),
    "lesson_placements/lesson-foreign": placement("lesson-foreign", "other"),
    "lesson_placements/lesson-partial-rejection": partialRejection("lesson-partial-rejection"),
    "lesson_ratings/lesson-owned-a": rating("lesson-owned-a"),
    "lesson_placements/lesson-owned-a/history/history-a": history(NOW - 20),
    "lesson_placements/lesson-owned-a/history/history-b": history(NOW - 20,
      {targetShelf: "rejected"}),
    "lesson_placements/lesson-owned-a/history/history-c": history(NOW - 10,
      {status: "REJECTED", code: "REVIEW_APPROVAL_REQUIRED"}),
    "arena_royalty_accounts/author": {totalAuthorCreditNolics: 13, updatedAtMs: NOW,
      authorCarryFifths: 4, sinkCarryFifths: 1, reservedNolics: 1, totalPaymentsNolics: 17,
      totalSinkNolics: 3, futurePrivate: "PRIVATE_ACCOUNT"},
    "audit/currency_ledger/entries/royalty-a": royalty("author", NOW - 30, 1),
    "audit/currency_ledger/entries/royalty-b": royalty("author", NOW - 20, 2),
    "audit/currency_ledger/entries/royalty-c": royalty("author", NOW - 20, 3),
    "audit/currency_ledger/entries/not-royalty": royalty("author", NOW - 1, 99, {reason: "attempt_reward"}),
    "audit/currency_ledger/entries/foreign-royalty": royalty("other", NOW - 1, 77),
    "quest_review_requests/submission-published": reviewSubmission("author", "draft-published", "PUBLISHED", NOW - 20),
    "quest_review_requests/submission-rejected": reviewSubmission("author", "draft-rejected", "REJECTED", NOW - 10,
      {rejectionReason: "Correct the unsupported question format"}),
    "quest_review_requests/submission-foreign": reviewSubmission("other", "draft-foreign", "PUBLISHED", NOW - 5),
    "quest_review_requests/legacy-old": reviewSubmission("author", "draft-legacy", "REJECTED", NOW - 30,
      {rejectionReason: "Old author-visible reason"}),
    "quest_review_requests/legacy-current": reviewSubmission("author", "draft-legacy", "UNDER_REVIEW", NOW - 1),
  };
}

function before() {
  const state = structuredClone([...store.documents]);
  store.operations = []; store.writes = [];
  return state;
}

function unchanged(state) {
  assert.deepEqual([...store.documents], state);
  assert.deepEqual(store.writes, []);
  assert.equal(store.operations.some(operation => operation.kind === "write"), false);
}

function assertNoPrivate(value) {
  const wire = JSON.stringify(value);
  for (const token of ["PRIVATE_", "lastRejection", "actorUid", "authorUid", "mutationId", "reason\"",
    "sourceId", "details", "authorCarryFifths", "sinkCarryFifths", "reservedNolics",
    "totalPaymentsNolics", "totalSinkNolics", "reviewerUid", "submissionId"]) {
    assert.equal(wire.includes(token), false, token);
  }
}
function assertNoRawReviewRequest(value) {
  const wire = JSON.stringify(value);
  for (const token of ["PRIVATE_", "\"ownerUid\"", "questions", "\"draft\"", "\"review\"",
    "processedAtMs", "lastError", "payload"]) {
    assert.equal(wire.includes(token), false, token);
  }
}

test("TZ06B lifecycle readers preserve the accepted callable surface", () => {
  const actual = Object.keys(functions).sort();
  assert.deepEqual(actual.filter(name => ENDPOINTS.includes(name)), [...ENDPOINTS].sort());
  assert.equal(exportSetHash(actual.filter(name => !ENDPOINTS.includes(name))), ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256);
  for (const endpoint of ENDPOINTS) assert.equal(typeof functions[endpoint], "function", endpoint);
  for (const endpoint of ["listArenaLessons", "prepareArenaEntry", "setLessonPlacement", "rateArenaLesson",
    "fetchArenaEntryTicketStatus", "submitCourseTournamentOperation"]) {
    assert.equal(typeof functions[endpoint], "function", endpoint);
  }
});

test("every lifecycle callable rejects missing Auth before a source read or effect", async () => {
  store.reset(world()); const state = before();
  for (const endpoint of ENDPOINTS) {
    await assert.rejects(functions[endpoint].run({data: {lessonId: "lesson-owned-a"}}),
      error => safeError(error, "unauthenticated", "UNAUTHENTICATED"));
    await assert.rejects(functions[endpoint].run({auth: {uid: ""}, data: {lessonId: "lesson-owned-a"}}),
      error => safeError(error, "unauthenticated", "UNAUTHENTICATED"));
  }
  assert.deepEqual(store.operations, []); unchanged(state);
});

test("payload authority spoofing and malformed cursor/body fail at the callable boundary before reads", async () => {
  store.reset(world()); const state = before();
  const invalid = [
    ["listOwnedLessonLifecycle", {pageSize: 1, uid: "curator"}],
    ["listOwnedLessonLifecycle", {pageSize: 1, ownerUid: "other"}],
    ["listCuratorLessonLifecycle", {pageSize: 1, curatorAuthorized: true}],
    ["getLessonLifecycle", {lessonId: "lesson-owned-a", uid: "curator"}],
    ["listLessonLifecycleHistory", {lessonId: "lesson-owned-a", cursor: "bad="}],
    ["listLessonLifecycleHistory", {lessonId: "lesson-owned-a", ownerUid: "other"}],
    ["listMyArenaRoyalty", {uid: "other"}],
    ["listMyArenaRoyalty", {cursor: "a".repeat(4097)}],
    ["listMyQuestReviewSubmissions", {submissionIds: [], legacyDraftIds: [], uid: "other"}],
  ];
  for (const [endpoint, data] of invalid) {
    await assert.rejects(call(endpoint, "author", data),
      error => safeError(error, "invalid-argument", "INVALID_ARGUMENT"));
  }
  assert.deepEqual(store.operations, []); unchanged(state);
});

test("actual callable enforces author/curator scope, hides foreign lesson existence, and projects no raw private field", async () => {
  store.reset(world()); const state = before();
  const owned = await call("listOwnedLessonLifecycle", "author", {pageSize: 1});
  assert.equal(owned.entries.length, 1); assert.equal(owned.entries[0].lessonId, "lesson-owned-a");
  assert.equal(owned.nextLessonId, "lesson-owned-a"); assertNoPrivate(owned);
  const ownedTail = await call("listOwnedLessonLifecycle", "author", {pageSize: 1, afterLessonId: owned.nextLessonId});
  assert.deepEqual(ownedTail.entries.map(row => row.lessonId), ["lesson-owned-b"]); assertNoPrivate(ownedTail);

  const curator = await call("listCuratorLessonLifecycle", "curator", {pageSize: 20});
  assert.deepEqual(curator.entries.map(row => row.lessonId),
    ["lesson-foreign", "lesson-owned-a", "lesson-owned-b", "lesson-partial-rejection"]);
  assert.deepEqual(curator.entries.at(-1).placement, null);
  assertNoPrivate(curator);
  await assert.rejects(call("listCuratorLessonLifecycle", "author", {pageSize: 1}),
    error => safeError(error, "permission-denied", "PERMISSION_DENIED"));

  const own = await call("getLessonLifecycle", "author", {lessonId: "lesson-owned-a"});
  assert.equal(own.lifecycle.lessonId, "lesson-owned-a"); assertNoPrivate(own);
  assert.deepEqual(own.lifecycle.rating, {sum: 5, count: 2, average: 2.5, version: 4, updatedAtMs: NOW - 2});
  const partial = await call("getLessonLifecycle", "author", {lessonId: "lesson-partial-rejection"});
  assert.deepEqual(partial.lifecycle.placement, null); assert.equal(partial.lifecycle.state, "REVIEW");
  assertNoPrivate(partial);
  const curatorForeign = await call("getLessonLifecycle", "curator", {lessonId: "lesson-foreign"});
  assert.equal(curatorForeign.lifecycle.lessonId, "lesson-foreign"); assertNoPrivate(curatorForeign);
  for (const uid of ["author", "outsider"]) {
    await assert.rejects(call("getLessonLifecycle", uid, {lessonId: "lesson-foreign"}),
      error => safeError(error, "not-found", "NOT_FOUND"));
  }

  const firstHistory = await call("listLessonLifecycleHistory", "author", {lessonId: "lesson-owned-a", pageSize: 1});
  assert.deepEqual(firstHistory.entries, [{fromShelf: "review", targetShelf: "arena", status: "APPLIED",
    reasonCode: null, decidedAtMs: NOW - 20}]);
  assert.equal(typeof firstHistory.nextCursor, "string"); assertNoPrivate(firstHistory);
  const secondHistory = await call("listLessonLifecycleHistory", "author", {
    lessonId: "lesson-owned-a", pageSize: 1, cursor: firstHistory.nextCursor,
  });
  assert.deepEqual(secondHistory.entries, [{fromShelf: "review", targetShelf: "rejected", status: "APPLIED",
    reasonCode: "REVIEW_REJECTED", decidedAtMs: NOW - 20}]);
  assertNoPrivate(secondHistory);
  await assert.rejects(call("listLessonLifecycleHistory", "outsider", {lessonId: "lesson-owned-a"}),
    error => safeError(error, "not-found", "NOT_FOUND"));

  const firstRoyalty = await call("listMyArenaRoyalty", "author", {pageSize: 2});
  assert.deepEqual(firstRoyalty.summary, {totalCreditedNolics: 13, updatedAtMs: NOW});
  assert.deepEqual(firstRoyalty.entries, [{creditedNolics: 3, creditedAtMs: NOW - 20},
    {creditedNolics: 2, creditedAtMs: NOW - 20}]);
  assert.equal(typeof firstRoyalty.nextCursor, "string"); assertNoPrivate(firstRoyalty);
  const ownEmptyRoyalty = await call("listMyArenaRoyalty", "outsider", {});
  assert.deepEqual(ownEmptyRoyalty, {viewVersion: "arena-lifecycle-views-v1",
    summary: {totalCreditedNolics: 0, updatedAtMs: null}, entries: [], nextCursor: null});
  assertNoPrivate(ownEmptyRoyalty);

  const outcomes = await call("listMyQuestReviewSubmissions", "author", {
    submissionIds: ["submission-rejected", "submission-published"], legacyDraftIds: [],
  });
  assert.deepEqual(outcomes, {viewVersion: "quest-review-submissions-v1", entries: [
    {submissionId: "submission-published", draftId: "draft-published", status: "PUBLISHED", rejectionReason: null},
    {submissionId: "submission-rejected", draftId: "draft-rejected", status: "REJECTED",
      rejectionReason: "Correct the unsupported question format"},
  ]});
  assertNoRawReviewRequest(outcomes);
  const legacyOutcome = await call("listMyQuestReviewSubmissions", "author", {
    submissionIds: [], legacyDraftIds: ["draft-legacy"],
  });
  assert.deepEqual(legacyOutcome.entries, [
    {submissionId: "legacy-current", draftId: "draft-legacy", status: "UNDER_REVIEW", rejectionReason: null},
  ]);
  assertNoRawReviewRequest(legacyOutcome);
  const foreignOutcome = await call("listMyQuestReviewSubmissions", "author", {
    submissionIds: ["submission-foreign"], legacyDraftIds: [],
  });
  assert.deepEqual(foreignOutcome.entries, []);
  assertNoRawReviewRequest(foreignOutcome);
  unchanged(state);
});

test("lifecycle context stays bounded when a lesson has a large historic review journal", async () => {
  const seeded = world();
  for (let index = 0; index < 64; index += 1) {
    seeded[`admin/review/lessons/lesson-owned-a/reviews/old-${index}`] = {
      reviewerUid: `PRIVATE_OLD_${index}`, acceptedByServer: true, score: 3,
    };
  }
  store.reset(seeded); const state = before();
  const result = await call("getLessonLifecycle", "author", {lessonId: "lesson-owned-a"});
  assert.equal(result.lifecycle.lessonId, "lesson-owned-a"); assertNoPrivate(result);
  assert.equal(store.operations.some(operation => operation.path.endsWith("/reviews")), false);
  assert.equal(store.operations.length, 8);
  unchanged(state);
});

test("backend and corrupt-source failures become generic unavailable errors without fallback reads or writes", async () => {
  store.reset(world(), {fail: {"lesson_placements/lesson-owned-a": "PRIVATE credentials /private/lifecycle"}});
  const state = before();
  await assert.rejects(call("getLessonLifecycle", "author", {lessonId: "lesson-owned-a"}),
    error => safeError(error, "unavailable", "UNAVAILABLE"));
  assert.equal(store.operations.some(operation =>
    operation.kind === "read" && operation.path === "lesson_placements/lesson-owned-a"), true);
  unchanged(state);

  store.reset(world());
  store.documents.set("lesson_placements/lesson-owned-a/history/history-a", history(NOW - 20,
    {code: "PRIVATE_CODE"}));
  const corrupted = before();
  await assert.rejects(call("listLessonLifecycleHistory", "author", {lessonId: "lesson-owned-a"}),
    error => safeError(error, "unavailable", "UNAVAILABLE"));
  unchanged(corrupted);
});
