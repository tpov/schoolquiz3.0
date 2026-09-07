"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("node:module");
const path = require("node:path");
const fs = require("node:fs");
// Draft tests mount the unchanged source at /repo/functions. After integration no override is
// needed. Only module resolution is redirected; no function is extracted or copied from index.
const SOURCE = process.env.ARENA_CATALOG_SOURCE || (fs.existsSync(path.join(__dirname, "index.js")) ? __dirname :
  path.resolve(__dirname, "../../../integration-edit/functions"));
const fromSource = (name) => require(path.join(SOURCE, name));
const {createFirestoreStub} = fromSource("_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin", {paths: [SOURCE]});
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const runtime = fromSource("arena-entry-runtime");
const originalFactory = runtime.createArenaEntryRuntime;
let rootLoader;
runtime.createArenaEntryRuntime = (args) => { rootLoader = args.loadLessonContext; return originalFactory(args); };
let functions;
try { functions = fromSource("index"); } finally { runtime.createArenaEntryRuntime = originalFactory; }
assert.equal(typeof rootLoader, "function", "test must execute the actual root review/hierarchy loader");
const originalResolve = Module._resolveFilename;
let catalogModule;
try {
  Module._resolveFilename = function(request, parent, ...rest) {
    if (request === "./arena-policy" && parent?.filename === path.join(__dirname, "arena-lesson-catalog.js")) return path.join(SOURCE, "arena-policy.js");
    return originalResolve.call(this, request, parent, ...rest);
  };
  catalogModule = require("./arena-lesson-catalog");
} finally { Module._resolveFilename = originalResolve; }
const {createArenaLessonCatalog, CATALOG_VERSION, LIMITS} = catalogModule;
const {publishLesson} = fromSource("_scored-lesson-fixture");
const NOW = Date.parse("2026-09-07T00:00:00Z");
const createReader = (loadLessonContext = rootLoader) => createArenaLessonCatalog({db: store.db, loadLessonContext, nowMs: () => NOW});
const read = (payload = {}, reader = createReader()) => reader.list({uid: "player", payload});
function lessonWorld(lessonId = "lesson", patch = {}) {
  return {
    [`catalogs/catalog-${lessonId}`]: {questType: "REGULAR"},
    [`quests/quest-${lessonId}`]: {catalogId: `catalog-${lessonId}`, authorUid: "author", visibleOn: ["archive"], averageRating: 99},
    [`sections/section-${lessonId}`]: {questId: `quest-${lessonId}`},
    [`themes/theme-${lessonId}`]: {sectionId: `section-${lessonId}`},
    [`lessons/${lessonId}`]: {themeId: `theme-${lessonId}`, title: `Title ${lessonId}`, version: 1,
      reviewSubmissionId: `submission-${lessonId}`, reviewApprovedAtMs: NOW - 1, averageRating: 88, top3: [{nickname: "not-a-lesson-rating"}], ...patch},
    [`lesson_placements/${lessonId}`]: {lessonId, shelf: "arena", authorUid: "author", contentVersion: 1,
      previousShelf: "review", placementVersion: 1, updatedAtMs: NOW - 1, decidedByUid: "private-curator", lastReason: "private reason"},
    [`admin/review/lessons/${lessonId}`]: {ownerUid: "author", questId: `quest-${lessonId}`, submissionId: `submission-${lessonId}`,
      status: "PUBLISHED", sourceLanguages: ["en"]},
    ...Object.fromEntries(["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/${lessonId}/reviews/${kind}`, {
      kind, reviewerUid: kind, acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: `submission-${lessonId}`,
    }])),
  };
}
function bankWorld() {
  const bank = publishLesson("lesson");
  return {...lessonWorld(),
    "users/author": {nickname: "PRIVATE-NICKNAME", avatarUrl: "https://private.invalid/a.png", email: "private@example.invalid"},
    "profiles/author": {nickname: "PRIVATE-PROFILE", privateFlag: true},
    "users/player": {uid: "player", standardHearts: 10, goldHearts: 3, lifePoints: 1000, plasmaPoints: 300,
      lifePointsUpdatedAtMs: Date.now(), plasmaPointsUpdatedAtMs: Date.now(), nolics: 1000, pointsNolics: 1000},
    "profiles/curator": {adminLevel: 100},
    "lesson_placements/lesson/history/private": {actorUid: "secret-reviewer", reason: "private appeal", rejected: true},
    [bank.keyDocumentPath]: bank.keyDocument,
    ...Object.fromEntries(bank.documents.map(row => [`questions/${row.id}`, row])),
  };
}
function rating(lessonId, sum, count, version = 1) { return {[`lesson_ratings/${lessonId}`]: {ratingSum: sum, ratingCount: count, ratingVersion: version}}; }
function patch(path, fields) { store.documents.set(path, {...store.documents.get(path), ...fields}); }
function snapshot() { return structuredClone([...store.documents]); }
function noWrites(before) { assert.deepEqual(snapshot(), before); assert.equal(store.writes.length, 0); }
function safe(value) {
  const forbidden = new Set(["payload", "questionId", "questions", "keys", "gradingKey", "correctOptionId", "answerKey",
    "reviews", "reviewerUid", "actorUid", "decidedByUid", "mutationId", "reason", "lastReason", "lastRejection", "nickname", "avatarUrl", "email", "top3"]);
  if (!value || typeof value !== "object") return;
  for (const [key, nested] of Object.entries(value)) { assert.ok(!forbidden.has(key), `private/unapproved field ${key}`); safe(nested); }
}

test("Auth and strict request/cursor validation precede all reads and writes", async () => {
  store.reset(bankWorld()); const before = snapshot(); const service = createReader();
  for (const uid of [null, "", "x/y", 5]) await assert.rejects(service.list({uid, payload: {}}), {code: "UNAUTHENTICATED"});
  for (const payload of [null, [], {uid: "author"}, {difficulty: "HARD"}, {pageSize: 0}, {pageSize: 51},
    {pageSize: 2.5}, {cursor: "bad="}, {cursor: "a".repeat(4097)}, {cursor: Buffer.from("{}").toString("base64url")}]) {
    await assert.rejects(service.list({uid: "player", payload}), {code: "INVALID_ARGUMENT"});
  }
  assert.equal(store.operations.length, 0); noWrites(before);
});

test("actual root review loader + actual published key producer yield only safe lesson metadata", async () => {
  store.reset(bankWorld()); const before = snapshot();
  const response = await read();
  assert.equal(response.catalogVersion, CATALOG_VERSION); assert.equal(response.entryAvailability, "PREPARE_REQUIRED");
  assert.equal(response.serverNowMs, NOW); assert.equal(response.totalEligibleLessons, 1); assert.equal(response.nextCursor, null);
  assert.deepEqual(response.lessons, [{lessonId: "lesson", contentVersion: 1, title: "Title lesson", authorUid: "author",
    rating: {sum: 0, count: 0, weightedScore: {numerator: 40, denominator: 20}, ratingVersion: 0}, priceNolics: 1,
    placement: {shelf: "arena", previousShelf: "review", placementVersion: 1, updatedAtMs: NOW - 1}}]);
  safe(response); noWrites(before);
  assert.ok(store.operations.some(op => op.path === "admin/review/lessons/lesson/reviews"));
  assert.ok(store.operations.every(op => !/^(questions|question_keys|users|profiles)\//.test(op.path) && !op.path.includes("/history/")));
});

test("real placement producer output is readable without copying its audit fields", async () => {
  const seed = bankWorld(); delete seed["lesson_placements/lesson"];
  store.reset(seed);
  const decision = await functions.setLessonPlacement.run({auth: {uid: "curator"}, data: {
    mutationId: "catalog-placement", lessonId: "lesson", targetShelf: "arena", reason: "private curator rationale"}});
  assert.equal(decision.status, "APPLIED");
  const created = snapshot(); store.writes = [];
  const response = await read(); assert.equal(response.lessons.length, 1); safe(response); noWrites(created);
  assert.equal(response.lessons[0].placement.placementVersion, store.documents.get("lesson_placements/lesson").placementVersion);
});

test("all lessons including unrated prior=2 sort by exact fraction; equivalent fractions share price", async () => {
  store.reset({...lessonWorld("high"), ...rating("high", 3, 1), ...lessonWorld("a-unrated"),
    ...lessonWorld("b-mid"), ...rating("b-mid", 2, 1), ...lessonWorld("c-mid"), ...rating("c-mid", 4, 2),
    ...lessonWorld("low"), ...rating("low", 1, 1)});
  const before = snapshot(); const result = await read();
  assert.deepEqual(result.lessons.map(row => row.lessonId), ["high", "a-unrated", "b-mid", "c-mid", "low"]);
  assert.deepEqual(result.lessons.map(row => row.priceNolics), [100, 1, 51, 51, 1]); noWrites(before);
});

test("distinct rational levels remain distinct when division rounds them to the same Number", async () => {
  const n = 4_000_000_000_000_000;
  assert.equal((2 * n + 41) / (n + 20), (2 * n + 42) / (n + 20));
  store.reset({...lessonWorld("a-lower"), ...rating("a-lower", 2 * n + 1, n),
    ...lessonWorld("z-higher"), ...rating("z-higher", 2 * n + 2, n)});
  const result = await read();
  assert.deepEqual(result.lessons.map(row => [row.lessonId, row.priceNolics]), [["z-higher", 100], ["a-lower", 1]]);
});

test("pagination covers every eligible lesson once, defaults to 20 and allows 50 with deterministic replay", async () => {
  store.reset(Object.assign({}, ...Array.from({length: 55}, (_, n) => lessonWorld(`l${String(n).padStart(3, "0")}`))));
  const before = snapshot();
  const first = await read(); assert.equal(first.lessons.length, 20); assert.equal(first.totalEligibleLessons, 55);
  const second = await read({cursor: first.nextCursor, pageSize: 20});
  const third = await read({cursor: second.nextCursor, pageSize: 20});
  assert.equal(third.lessons.length, 15); assert.equal(third.nextCursor, null);
  assert.deepEqual(await read({cursor: first.nextCursor, pageSize: 20}), second);
  const ids = [...first.lessons, ...second.lessons, ...third.lessons].map(row => row.lessonId);
  assert.equal(new Set(ids).size, 55); assert.deepEqual(ids, ids.slice().sort());
  assert.equal((await read({pageSize: 50})).lessons.length, 50); noWrites(before);
});

test("cursor detects unseen pricing, display, version, membership and eligibility changes without writes", async () => {
  const seed = {...lessonWorld("a"), ...lessonWorld("b"),
    "lesson_placements/stale": {shelf: "arena"}, ...rating("stale", 3, 1)};
  const changes = [() => patch("lesson_ratings/stale", {ratingSum: 2}),
    () => patch("lessons/b", {title: "Changed title"}),
    () => patch("lesson_placements/b", {placementVersion: 2}),
    () => patch("lessons/b", {version: 2}),
    () => patch("admin/review/lessons/b", {status: "REJECTED"}),
    () => store.documents.delete("lesson_placements/stale"),
    () => { for (const [key, value] of Object.entries(lessonWorld("c"))) store.documents.set(key, value); }];
  for (const change of changes) {
    store.reset(seed); const first = await read({pageSize: 1}); change(); const before = snapshot();
    await assert.rejects(read({cursor: first.nextCursor}), {code: "CATALOG_CHANGED"}); noWrites(before);
  }
});

test("private payload and audit text do not enter the public fingerprint", async () => {
  store.reset({...bankWorld(), ...lessonWorld("second")}); const first = await read({pageSize: 1});
  patch("questions/q-single", {payload: "private-key-changed"});
  patch("lesson_placements/lesson", {lastReason: "new secret", decidedByUid: "other-curator"});
  patch("users/author", {nickname: "ANOTHER-SECRET"});
  const second = await read({cursor: first.nextCursor});
  assert.equal(second.snapshotFingerprint, first.snapshotFingerprint); safe(second);
});

test("stale raw placements keep price parity with actual prepare but never become visible cards", async () => {
  store.reset({...bankWorld(), ...rating("lesson", 2, 1),
    "lesson_placements/low": {shelf: "arena"}, ...rating("low", 1, 1),
    "lesson_placements/high": {shelf: "arena"}, ...rating("high", 3, 1)});
  const before = snapshot(); const response = await read();
  assert.deepEqual(response.lessons.map(row => [row.lessonId, row.priceNolics]), [["lesson", 51]]); noWrites(before);
  const ticket = (await functions.prepareArenaEntry.run({auth: {uid: "player"}, data: {
    requestId: "catalog-parity", mode: "SELECTED", lessonId: "lesson", difficulty: "HARD", maxPriceNolics: 51}})).ticket;
  assert.equal(ticket.priceNolics, response.lessons[0].priceNolics);
});

test("COURSE/SURVEY, stale review, unpublished and mismatched placement author/version are excluded", async () => {
  const changes = [() => patch("catalogs/catalog-lesson", {questType: "COURSE"}),
    () => patch("catalogs/catalog-lesson", {questType: "SURVEY"}),
    () => patch("lessons/lesson", {reviewSubmissionId: "stale"}),
    () => patch("lessons/lesson", {archived: true}),
    () => patch("lesson_placements/lesson", {authorUid: "someone-else"}),
    () => patch("lesson_placements/lesson", {contentVersion: 2})];
  for (const change of changes) {
    store.reset(bankWorld()); change(); const before = snapshot();
    const result = await read(); assert.equal(result.lessons.length, 0); assert.equal(result.totalEligibleLessons, 0); noWrites(before);
  }
});

test("metadata listing never invents bank availability or fills unknown title with a fake value", async () => {
  store.reset(lessonWorld("lesson", {title: null})); const before = snapshot();
  const response = await read(); assert.equal(response.lessons[0].title, null); assert.equal(response.entryAvailability, "PREPARE_REQUIRED");
  assert.ok(store.operations.every(op => !op.path.startsWith("questions"))); noWrites(before);
});

test("placement lookahead refuses 1001 rather than silently pricing/listing 1000", async () => {
  store.reset(Object.fromEntries(Array.from({length: LIMITS.placements + 1}, (_, n) => [`lesson_placements/p${n}`, {shelf: "arena"}])));
  const before = snapshot(); await assert.rejects(read(), {code: "CATALOG_TOO_LARGE"});
  assert.equal(store.operations.length, 1); noWrites(before);
});

test("catalog pricing-universe limit matches current runtime, and exactly 1000 raw rows are allowed", async () => {
  assert.equal(LIMITS.placements, runtime.MAX_ARENA_LESSONS);
  store.reset(Object.fromEntries(Array.from({length: LIMITS.placements}, (_, n) => [`lesson_placements/p${n}`, {shelf: "arena"}])));
  const before = snapshot(); const result = await read();
  assert.equal(result.totalEligibleLessons, 0); assert.deepEqual(result.lessons, []); noWrites(before);
});

test("actual root's unbounded review query is bounded with +1 and never receives truncated reviews", async () => {
  const rows = Object.fromEntries(Array.from({length: 1001}, (_, n) => [`admin/review/lessons/lesson/reviews/r${n}`, {}]));
  store.reset({...lessonWorld(), ...rows}); const before = snapshot();
  await assert.rejects(read(), {code: "CATALOG_TOO_LARGE"}); noWrites(before);
});

test("exactly 12000 document reads are permitted without an off-by-one rejection", async () => {
  store.reset(lessonWorld()); const before = snapshot();
  const service = createReader(async tx => {
    // Placement query and aggregate doc consume two reads before the trusted loader begins.
    for (let n = 0; n < LIMITS.readDocuments - 2; n++) await tx.get(store.db.doc("budget/same"));
    return null;
  });
  const result = await read({}, service); assert.equal(result.totalEligibleLessons, 0);
  assert.equal(store.operations.length, LIMITS.readDocuments); noWrites(before);
});

test("total read budget is explicit even for repeated injected reads", async () => {
  store.reset(lessonWorld()); const before = snapshot();
  const service = createReader(async tx => {
    assert.equal(tx.set, undefined); assert.equal(tx.create, undefined);
    for (let n = 0; n <= LIMITS.readDocuments; n++) await tx.get(store.db.doc("budget/same"));
    return null;
  });
  await assert.rejects(read({}, service), {code: "CATALOG_TOO_LARGE"});
  assert.equal(store.operations.length, LIMITS.readDocuments); noWrites(before);
});

test("same-Transaction optimistic retry rebuilds metadata/pricing and resets the per-callback budget", async () => {
  store.reset({...lessonWorld(), ...rating("lesson", 1, 1)}, {retryTransactionOnce: () => {
    patch("lessons/lesson", {title: "After retry"}); patch("lesson_ratings/lesson", {ratingSum: 3, ratingVersion: 2});
  }});
  let calls = 0; const txs = [];
  const service = createReader(async (tx, id) => {
    calls++; txs.push(tx);
    for (let n = 0; n < 6000; n++) await tx.get(store.db.doc("budget/same"));
    return rootLoader(tx, id);
  });
  const result = await read({}, service);
  assert.equal(calls, 2); assert.notEqual(txs[0], txs[1]);
  assert.equal(result.lessons[0].title, "After retry"); assert.equal(result.lessons[0].rating.sum, 3);
  assert.equal(result.lessons[0].rating.ratingVersion, 2); assert.ok(store.operations.length > LIMITS.readDocuments);
  assert.equal(store.writes.length, 0);
});

test("response byte ceiling refuses oversized public title instead of truncation", async () => {
  store.reset(lessonWorld("lesson", {title: "x".repeat(LIMITS.responseBytes)})); const before = snapshot();
  await assert.rejects(read(), {code: "CATALOG_TOO_LARGE"}); noWrites(before);
});

test("response ceiling includes envelope/cursor overhead, not just individual cards", async () => {
  store.reset(lessonWorld("lesson", {title: "T"}));
  const small = await read(); const smallBytes = Buffer.byteLength(JSON.stringify(small));
  patch("lessons/lesson", {title: "x".repeat(LIMITS.responseBytes - smallBytes + 2)});
  const before = snapshot();
  await assert.rejects(read(), {code: "CATALOG_TOO_LARGE"}); noWrites(before);
});

test("a syntactically valid cursor with an unknown last lesson cannot silently return an empty tail", async () => {
  store.reset(lessonWorld()); const result = await read();
  const cursor = Buffer.from(JSON.stringify({afterLessonId: "missing", snapshot: result.snapshotFingerprint, v: 1})).toString("base64url");
  const before = snapshot(); await assert.rejects(read({cursor}), {code: "CATALOG_CHANGED"}); noWrites(before);
});

test("invalid stored rating never becomes zero or a guessed price", async () => {
  for (const values of [[-1, 1], [4, 1], [1, 0], [1.5, 1], [Number.MAX_SAFE_INTEGER, 1]]) {
    store.reset({...lessonWorld(), ...rating("lesson", ...values)}); const before = snapshot();
    await assert.rejects(read(), {code: "CATALOG_INVALID"}); noWrites(before);
  }
});

for (const field of ["ratingSum", "ratingCount", "ratingVersion"]) {
  for (const corruption of ["missing", "null"]) {
    test(`existing rating document rejects ${corruption} ${field}, without an unrated fallback`, async () => {
      const row = {ratingSum: 0, ratingCount: 0, ratingVersion: 0};
      if (corruption === "missing") delete row[field]; else row[field] = null;
      store.reset({...lessonWorld(), "lesson_ratings/lesson": row}); const before = snapshot();
      await assert.rejects(read(), {code: "CATALOG_INVALID"}); noWrites(before);
    });
  }
}

test("an existing empty rating document is corruption, not an absent document", async () => {
  store.reset({...lessonWorld(), "lesson_ratings/lesson": {}}); const before = snapshot();
  await assert.rejects(read(), {code: "CATALOG_INVALID"}); noWrites(before);
});

test("a wholly absent rating document legitimately yields the unrated prior and price one", async () => {
  store.reset(lessonWorld()); const before = snapshot();
  assert.equal(store.documents.has("lesson_ratings/lesson"), false);
  const lesson = (await read()).lessons[0];
  assert.deepEqual(lesson.rating, {sum: 0, count: 0, weightedScore: {numerator: 40, denominator: 20}, ratingVersion: 0});
  assert.equal(lesson.priceNolics, 1); noWrites(before);
});

test("an existing complete zero aggregate is valid, with every integer field explicitly present", async () => {
  store.reset({...lessonWorld(), ...rating("lesson", 0, 0, 0)}); const before = snapshot();
  const lesson = (await read()).lessons[0];
  assert.equal(lesson.rating.count, 0); assert.equal(lesson.rating.sum, 0); assert.equal(lesson.rating.ratingVersion, 0);
  assert.equal(lesson.priceNolics, 1); noWrites(before);
});

test("loader failure stays a failure, and an actually empty catalog is a valid read-only response", async () => {
  store.reset(lessonWorld(), {fail: {"themes/theme-lesson": "read unavailable"}}); const before = snapshot();
  await assert.rejects(read(), /read unavailable/); noWrites(before);
  store.reset({}); const empty = await read();
  assert.equal(empty.totalEligibleLessons, 0); assert.deepEqual(empty.lessons, []); assert.equal(empty.nextCursor, null);
  assert.equal(store.writes.length, 0);
});
