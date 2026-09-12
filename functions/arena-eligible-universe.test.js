"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {createFirestoreStub} = require("./_firestore-stub");
const {createArenaEligibleUniverse, LIMITS} = require("./arena-eligible-universe");

function world(id, patch = {}) {
  return {
    [`lesson_placements/${id}`]: {shelf: "arena", authorUid: `author-${id}`, contentVersion: 1},
    [`lessons/${id}`]: {themeId: `theme-${id}`, version: 1, ...patch.lesson},
    [`lesson_ratings/${id}`]: {ratingSum: 0, ratingCount: 0, ratingVersion: 1, ...patch.rating},
  };
}

function loader(overrides = {}, calls = []) {
  return async (reader, lessonId) => {
    calls.push({reader, lessonId});
    if (Object.hasOwn(overrides, lessonId)) return overrides[lessonId];
    // This deliberate Promise.all proves that root-loader fan-out is metered through reader.get.
    await reader.getAll({path: `unused/${lessonId}/a`}, {path: `unused/${lessonId}/b`});
    return {lessonId, authorUid: `author-${lessonId}`, contentVersion: 1, themeId: `theme-${lessonId}`,
      questType: "REGULAR", published: true, reviewApproved: true};
  };
}

async function read(seed, context = {}) {
  const {store} = createFirestoreStub(seed);
  const calls = [];
  const universe = createArenaEligibleUniverse({db: store.db, loadLessonContext: loader(context, calls)});
  let result;
  await store.db.runTransaction(async (tx) => {
    const reader = universe.forTransaction(tx);
    result = await reader.read();
    assert.strictEqual(await reader.read(), result, "one callback shares exactly one universe promise");
  });
  return {result, store, calls, universe};
}

test("only current independently approved regular public lessons enter the priced universe", async () => {
  const seed = {...world("current", {rating: {ratingSum: 3, ratingCount: 1}}), ...world("stale-version"),
    ...world("stale-author"), ...world("archived", {lesson: {archived: true}}), ...world("review-revoked")};
  const {result, store} = await read(seed, {
    "stale-version": {lessonId: "stale-version", authorUid: "author-stale-version", contentVersion: 2,
      themeId: "theme-stale-version", questType: "REGULAR", published: true, reviewApproved: true},
    "stale-author": {lessonId: "stale-author", authorUid: "other", contentVersion: 1,
      themeId: "theme-stale-author", questType: "REGULAR", published: true, reviewApproved: true},
    "review-revoked": {lessonId: "review-revoked", authorUid: "author-review-revoked", contentVersion: 1,
      themeId: "theme-review-revoked", questType: "REGULAR", published: true, reviewApproved: false},
  });
  assert.deepEqual(result.rows.map((row) => row.lessonId), ["current"]);
  assert.equal(result.rows[0].priceNolics, 1);
  assert.equal(store.writes.length, 0);
  assert.ok(store.operations.every((operation) => operation.kind === "read"));
});

test("equal exact scores share a price and excluded rows do not influence eligible pricing", async () => {
  const equal = {...world("a", {rating: {ratingSum: 2, ratingCount: 1}}),
    ...world("b", {rating: {ratingSum: 4, ratingCount: 2}})};
  const baseline = await read(equal);
  const contaminated = await read({...equal, ...world("excluded-high", {rating: {ratingSum: 60, ratingCount: 20}})}, {
    "excluded-high": {lessonId: "excluded-high", authorUid: "wrong", contentVersion: 1,
      themeId: "theme-excluded-high", questType: "REGULAR", published: true, reviewApproved: true},
  });
  assert.deepEqual(baseline.result.rows.map((row) => [row.lessonId, row.priceNolics]), [["a", 1], ["b", 1]]);
  assert.deepEqual(contaminated.result.rows.map((row) => [row.lessonId, row.priceNolics]),
    [["a", 1], ["b", 1]]);
  assert.equal(contaminated.result.find("excluded-high"), null);
});

test("truncated placement query and corrupt rating fail closed", async () => {
  const tooMany = Object.assign({}, ...Array.from({length: LIMITS.placements + 1}, (_, n) => world(`l${n}`)));
  const {store} = createFirestoreStub(tooMany);
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: loader()});
  await assert.rejects(
    store.db.runTransaction((tx) => service.forTransaction(tx).read()),
    {code: "UNIVERSE_TOO_LARGE"},
  );
  await assert.rejects(read({...world("bad", {rating: {ratingSum: 4, ratingCount: 1}})}), {code: "UNIVERSE_INVALID"});
});

test("root loader fan-out shares the 12000-document cap", async () => {
  const {store} = createFirestoreStub(world("lesson"));
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: async (reader) => {
    await reader.getAll(...Array.from({length: LIMITS.readDocuments}, (_, n) => ({path: `unused/${n}`})));
    return null;
  }});
  await assert.rejects(
    store.db.runTransaction((tx) => service.forTransaction(tx).read()),
    {code: "UNIVERSE_TOO_LARGE"},
  );
  assert.equal(store.operations.length, LIMITS.readDocuments, "parallel loader reads never oversubscribe the meter");
});

test("document reads deduplicate only within one callback while queries remain current", async () => {
  const {store} = createFirestoreStub({...world("a"), ...world("b")});
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: async (reader, lessonId) => {
    await reader.getAll(store.db.doc(`lessons/${lessonId}`), store.db.doc("configs/arena_review"));
    return {lessonId, authorUid: `author-${lessonId}`, contentVersion: 1, themeId: `theme-${lessonId}`,
      questType: "REGULAR", published: true, reviewApproved: true};
  }});
  await store.db.runTransaction((tx) => service.forTransaction(tx).read());
  assert.equal(store.operations.filter((operation) => operation.path === "configs/arena_review").length, 1);
  assert.equal(store.operations.filter((operation) => operation.path === "lessons/a").length, 1);
  assert.equal(store.operations.filter((operation) => operation.path === "lessons/b").length, 1);
});

test("a complete 1000-row common review graph fits only through callback-local document deduplication", async () => {
  const seed = {};
  for (let n = 0; n < LIMITS.placements; n++) {
    const lessonId = `lesson-${n}`;
    Object.assign(seed, world(lessonId));
    seed[`themes/theme-${lessonId}`] = {sectionId: `section-${lessonId}`};
    seed[`sections/section-${lessonId}`] = {questId: `quest-${lessonId}`};
    seed[`quests/quest-${lessonId}`] = {catalogId: `catalog-${lessonId}`};
    seed[`catalogs/catalog-${lessonId}`] = {questType: "REGULAR"};
    seed[`admin/review/lessons/${lessonId}`] = {status: "PUBLISHED"};
    seed[`admin/review/lessons/${lessonId}/reviews/testing`] = {accepted: true};
    seed[`admin/review/lessons/${lessonId}/reviews/logic`] = {accepted: true};
  }
  const {store} = createFirestoreStub(seed);
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: async (reader, lessonId) => {
    await reader.getAll(
      store.db.doc(`lessons/${lessonId}`), store.db.doc(`themes/theme-${lessonId}`),
      store.db.doc(`sections/section-${lessonId}`), store.db.doc(`quests/quest-${lessonId}`),
      store.db.doc(`catalogs/catalog-${lessonId}`), store.db.doc(`admin/review/lessons/${lessonId}`),
      store.db.collection(`admin/review/lessons/${lessonId}/reviews`), store.db.doc("configs/arena_review"),
      store.db.doc(`catalogs/catalog-${lessonId}/sync_changes/lesson_${lessonId}`),
    );
    return {lessonId, authorUid: `author-${lessonId}`, contentVersion: 1, themeId: `theme-${lessonId}`,
      questType: "REGULAR", published: true, reviewApproved: true};
  }});
  let result;
  await store.db.runTransaction(async (tx) => { result = await service.forTransaction(tx).read(); });
  assert.equal(result.rows.length, LIMITS.placements);
  assert.equal(result.readCount, 11_001);
  assert.equal(store.operations.filter((operation) => operation.path === "configs/arena_review").length, 1);
});

test("concurrent universe reads share a single build and expose immutable safe values", async () => {
  const {store} = createFirestoreStub(world("lesson", {rating: {ratingSum: 3, ratingCount: 1}}));
  let contextCalls = 0;
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: async (reader, lessonId) => {
    contextCalls++;
    await reader.getAll({path: "metadata/a"}, {path: "metadata/b"});
    return {lessonId, authorUid: "author-lesson", contentVersion: 1, themeId: "theme-lesson",
      questType: "REGULAR", published: true, reviewApproved: true};
  }});
  await store.db.runTransaction(async (tx) => {
    const reader = service.forTransaction(tx);
    const [left, right] = await Promise.all([reader.read(), reader.read()]);
    assert.strictEqual(left, right);
    assert.strictEqual(left.find("lesson"), left.rows[0]);
    assert.throws(() => { left.rows.push({}); }, TypeError);
    assert.throws(() => { left.find("lesson").priceNolics = 99; }, TypeError);
    assert.throws(() => { left.rows[0].rating.weightedScore.numerator = 0; }, TypeError);
    assert.equal(left.find("lesson").priceNolics, 1);
    assert.equal(left.rows[0].rating.weightedScore.numerator, 43);
  });
  assert.equal(contextCalls, 1);
});

test("eligible IDs use deterministic codepoint order, never locale collation", async () => {
  const seed = Object.assign({}, ...["a", "A", "_", "-"].map((id) => world(id)));
  const {result} = await read(seed);
  assert.deepEqual(result.rows.map((row) => row.lessonId), ["-", "A", "_", "a"]);
});

test("a reused transaction object gets a new callback reader and never reuses abandoned context", async () => {
  const {store} = createFirestoreStub(world("lesson"));
  const contexts = [];
  const service = createArenaEligibleUniverse({db: store.db, loadLessonContext: async (reader, lessonId) => {
    contexts.push(reader);
    await reader.get({path: "metadata/retry-local"});
    return {lessonId, authorUid: "author-lesson", contentVersion: 1, themeId: "theme-lesson",
      questType: "REGULAR", published: true, reviewApproved: true};
  }});
  let calls = 0;
  store.retryTransactionOnce = () => {
    store.documents.set("lesson_placements/lesson", {shelf: "arena", authorUid: "author-lesson", contentVersion: 2});
  };
  await store.db.runTransaction(async (tx) => {
    calls++;
    const reader = service.forTransaction(tx);
    const result = await reader.read();
    if (calls === 1) assert.equal(result.rows.length, 1);
    else assert.equal(result.rows.length, 0);
  });
  assert.equal(calls, 2);
  assert.equal(contexts.length, 2);
  assert.notStrictEqual(contexts[0], contexts[1]);
  assert.equal(store.operations.filter((operation) => operation.path === "metadata/retry-local").length, 2);
});
