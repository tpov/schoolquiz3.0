"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {existsSync} = require("node:fs");
const {join} = require("node:path");
// Runs both in the separated draft tree and after the module is integrated into functions/.
const adjacentPolicy = join(__dirname, "arena-policy.js");
const policy = require(existsSync(adjacentPolicy) ? adjacentPolicy : join(__dirname, "../../arena/arena-policy.js"));
const {createArenaService, mutationDocumentId, voteDocumentId, LEDGER_PATH} = require("./arena-service");

const clone = (value) => value == null ? value : JSON.parse(JSON.stringify(value));

/** Strict optimistic Firestore double: real committed writes, create uniqueness, transaction
 * retries on document/query conflicts, rollback on failure, and no reads after the first write.
 */
function fakeDb(initial = {}) {
  const store = new Map(); let version = 0; let attempts = 0; let retries = 0;
  const commits = [];
  const put = (path, value) => store.set(path, {value: clone(value), version: ++version});
  Object.entries(initial).forEach(([path, value]) => put(path, value));
  const ref = (path) => ({path, kind: "doc"});
  const children = (path) => [...store.entries()].filter(([p]) =>
    p.startsWith(`${path}/`) && p.split("/").length === path.split("/").length + 1).sort(([a], [b]) => a.localeCompare(b));
  const queryVersion = (path) => JSON.stringify(children(path).map(([p, row]) => [p, row.version]));
  const snapshot = (path) => ({id: path.split("/").pop(), ref: ref(path), exists: store.has(path),
    data: () => clone(store.get(path)?.value)});
  const db = {
    doc: ref, collection: (path) => ({path, kind: "query"}), put,
    read: (path) => clone(store.get(path)?.value),
    paths: (prefix = "") => [...store.keys()].filter((path) => path.startsWith(prefix)).sort(),
    commits, failNextCommit: false, beforeCommitOnce: null,
    get attempts() { return attempts; }, get retries() { return retries; },
    async runTransaction(body) {
      for (let attempt = 0; attempt < 50; attempt++) {
        attempts++;
        const reads = new Map(); const queries = new Map(); const writes = [];
        const tx = {
          async get(target) {
            assert.equal(writes.length, 0, "Firestore forbids reads after writes");
            if (target.kind === "query") {
              queries.set(target.path, queryVersion(target.path));
              return {docs: children(target.path).map(([path]) => snapshot(path))};
            }
            reads.set(target.path, store.get(target.path)?.version || 0);
            return snapshot(target.path);
          },
          create(target, value) { writes.push({op: "create", path: target.path, value: clone(value)}); },
          set(target, value, options) { writes.push({op: "set", path: target.path, value: clone(value), merge: options?.merge === true}); },
        };
        const result = await body(tx);
        if (db.beforeCommitOnce) { const hook = db.beforeCommitOnce; db.beforeCommitOnce = null; await hook(db); }
        if ([...reads].some(([path, expected]) => (store.get(path)?.version || 0) !== expected) ||
            [...queries].some(([path, expected]) => queryVersion(path) !== expected)) { retries++; continue; }
        if (db.failNextCommit) { db.failNextCommit = false; throw new Error("injected commit failure"); }
        const created = new Set();
        for (const write of writes) {
          if (write.op === "create" && (store.has(write.path) || created.has(write.path))) throw new Error("ALREADY_EXISTS");
          if (write.op === "create") created.add(write.path);
        }
        writes.forEach((write) => put(write.path,
          write.merge ? {...(store.get(write.path)?.value || {}), ...write.value} : write.value));
        commits.push(writes);
        return clone(result);
      }
      throw new Error("transaction retry limit");
    },
  };
  return db;
}

const Q1 = Date.parse("2026-01-01T00:00:00Z");
const Q2 = Date.parse("2026-04-01T00:00:00Z");
function setup(extra = {}) {
  const db = fakeDb({
    "server_contexts/lesson": {lessonId: "lesson", authorUid: "author", questType: "REGULAR", published: true,
      publishedAtMs: Q1 - 100, contentVersion: 1, reviewApproved: true},
    "server_roles/curator": {curator: true}, "users/author": {countBox: 5},
    "quests/untouched": {averageRating: 1.3}, "lessons/lesson": {title: "Lesson", version: 1}, ...extra,
  });
  const clock = {now: Q1 + 1000};
  const service = createArenaService({db, policy, nowMs: () => clock.now,
    loadLessonContext: async (tx, lessonId) => (await tx.get(db.doc(`server_contexts/${lessonId}`))).data(),
    loadCompletion: async (tx, {attemptId}) => (await tx.get(db.doc(`server_results/${attemptId}`))).data(),
    isCurator: async (tx, uid) => (await tx.get(db.doc(`server_roles/${uid}`))).data()?.curator === true,
  });
  function completion(uid, attemptId = `attempt-${uid}`, patch = {}) {
    db.put(`server_results/${attemptId}`, {uid, attemptId, lessonId: "lesson", status: "SETTLED", completed: true,
      settledAtMs: Q1, activityKind: "ARENA", ...patch});
    return attemptId;
  }
  function rate(uid, rating = 2, mutationId = `rate-${uid}`, attemptId = completion(uid)) {
    return service.submitLessonRating({uid, mutationId, lessonId: "lesson", attemptId, rating,
      acceptedAtMs: 0, curatorAuthorized: true}); // Deliberately untrusted extra fields are ignored.
  }
  function move(targetShelf, mutationId = `move-${targetShelf}`, reason = "curator decision") {
    return service.transitionLesson({uid: "curator", mutationId, lessonId: "lesson", targetShelf, reason});
  }
  return {db, service, clock, completion, rate, move};
}

test("rating requires authenticated identity, own settled completed receipt and excludes author", async () => {
  const f = setup();
  await assert.rejects(() => f.service.submitLessonRating({uid: "", mutationId: "m", lessonId: "lesson", attemptId: "a", rating: 3}),
    /authenticated uid/);
  assert.equal((await f.rate("author", 3)).result.code, "SELF_RATING");
  for (const [uid, patch] of [["pending", {status: "PENDING"}], ["abort", {completed: false}],
    ["legacy", {completed: undefined}], ["wrong-user", {uid: "other"}], ["wrong-lesson", {lessonId: "other"}]]) {
    const attemptId = f.completion(uid, `a-${uid}`, patch);
    const result = await f.rate(uid, 3, `m-${uid}`, attemptId);
    assert.equal(result.status, "REJECTED"); assert.equal(result.result.code, "COMPLETED_SETTLEMENT_REQUIRED");
  }
  assert.equal(f.db.read("lesson_ratings/lesson"), undefined);
  assert.equal(f.db.paths("arena_season_votes/").length, 0);
  assert.equal(f.db.read("users/author").boxCount, undefined);
});

test("accepted rating writes lesson aggregate, immutable season vote and mutation receipt in one commit", async () => {
  const f = setup(); const receipt = await f.rate("u", 3);
  assert.equal(receipt.status, "APPLIED");
  assert.equal(receipt.result.acceptedAtMs, f.clock.now);
  assert.deepEqual(f.db.read("lesson_ratings/lesson").weightedScore, {numerator: 43, denominator: 21});
  assert.equal(f.db.read("lessons/lesson").averageRating, 3);
  assert.equal(f.db.read("lessons/lesson").ratingCount, 1);
  assert.equal(f.db.read("lessons/lesson").version, 1, "content version must not be a rating counter");
  assert.deepEqual(f.db.read("quests/untouched"), {averageRating: 1.3});
  const paths = f.db.commits.at(-1).map((w) => w.path);
  assert.ok(paths.some((p) => p.startsWith("arena_mutations/")));
  assert.ok(paths.some((p) => p.startsWith("arena_season_votes/2026-Q1/votes/")));
  assert.ok(paths.includes("lesson_ratings/lesson"));
  const latest = f.db.read(`lesson_ratings/lesson/votes/${voteDocumentId("u")}`);
  assert.equal(latest.vote.acceptedAtMs, f.clock.now);
});

test("same mutation replays unchanged, altered retry refuses, later eligible vote replaces not increments player count", async () => {
  const f = setup(); const first = await f.rate("u", 1);
  const commitsBeforeReplay = f.db.commits.length;
  assert.deepEqual(await f.rate("u", 1), first);
  assert.equal(f.db.commits[commitsBeforeReplay].length, 0);
  await assert.rejects(() => f.rate("u", 3), (error) => error.code === "MUTATION_REUSE");
  f.clock.now++;
  await f.rate("u", 3, "new-vote");
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 1);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingSum, 3);
  assert.equal(f.db.paths("arena_season_votes/2026-Q1/votes/").length, 2);
});

test("concurrent different players retry aggregate conflict without losing a vote; same mutation commits once", async () => {
  const f = setup(); f.completion("a"); f.completion("b");
  await Promise.all([f.rate("a", 1), f.rate("b", 3)]);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 2);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingSum, 4);
  assert.ok(f.db.retries > 0);
  f.completion("c");
  const results = await Promise.all([f.rate("c", 2), f.rate("c", 2)]);
  assert.deepEqual(results[0], results[1]);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 3);
  assert.equal(f.db.paths("arena_season_votes/2026-Q1/votes/").length, 3);
});

test("non-Arena completion can rate lesson but cannot manufacture an Arena season vote", async () => {
  const f = setup(); const a = f.completion("u", "home-result", {activityKind: "ORDINARY_LESSON"});
  assert.equal((await f.rate("u", 3, "home-rating", a)).result.seasonId, null);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 1);
  assert.equal(f.db.paths("arena_season_votes/").length, 0);
});

test("transaction failure leaves no partial rating, aggregate or receipt", async () => {
  const f = setup(); f.completion("u"); f.db.failNextCommit = true;
  await assert.rejects(() => f.rate("u", 3), /injected/);
  assert.equal(f.db.read("lesson_ratings/lesson"), undefined);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("u", "rate-u")}`), undefined);
  assert.equal(f.db.paths("arena_season_votes/").length, 0);
  await f.rate("u", 3);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 1);
});

test("trusted curator role gates placements; rejection and its reason survive later admission", async () => {
  const f = setup();
  const denied = await f.service.transitionLesson({uid: "player", mutationId: "forge", lessonId: "lesson",
    targetShelf: "arena", reason: "pretend curator", curatorAuthorized: true});
  assert.equal(denied.result.code, "CURATOR_REQUIRED");
  assert.equal(f.db.read("lesson_placements/lesson"), undefined);
  const rejected = await f.move("home", "skip", "attempted skipped rung");
  assert.equal(rejected.result.code, "INVALID_TRANSITION");
  await f.move("arena");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "arena");
  assert.equal(f.db.read("lesson_placements/lesson").lastRejection.reason, "attempted skipped rung");
  const log = f.db.paths("lesson_placements/lesson/history/").map((p) => f.db.read(p));
  assert.ok(log.some((r) => r.status === "REJECTED" && r.reason === "attempted skipped rung"));
  assert.ok(log.some((r) => r.status === "APPLIED" && r.targetShelf === "arena"));
});

test("current review approval is required, rejection state is retained, COURSE cannot enter ordinary ladder", async () => {
  const f = setup();
  await f.move("rejected", "review-reject", "incorrect answers");
  f.db.put("server_contexts/lesson", {...f.db.read("server_contexts/lesson"), reviewApproved: false});
  assert.equal((await f.move("arena", "unreviewed-republish")).result.code, "REVIEW_APPROVAL_REQUIRED");
  f.db.put("server_contexts/lesson", {...f.db.read("server_contexts/lesson"), reviewApproved: true, contentVersion: 2});
  await f.move("arena", "reviewed-republish");
  assert.equal(f.db.read("lesson_placements/lesson").contentVersion, 2);
  assert.ok(f.db.read("lesson_placements/lesson").lastRejection);
  const course = setup({"server_contexts/lesson": {lessonId: "lesson", authorUid: "author", questType: "COURSE",
    published: true, publishedAtMs: 1, contentVersion: 1, reviewApproved: true}});
  assert.equal((await course.move("arena")).result.code, "LESSON_NOT_ELIGIBLE");
});

test("milestones credit 15/20 canonical BOXES exactly once across concurrent requests, leave and republish", async () => {
  const f = setup(); await f.move("arena");
  await Promise.all([f.move("tournament", "promote-a"), f.move("tournament", "promote-b")]);
  assert.equal(f.db.read("users/author").boxCount, 20, "legacy countBox 5 plus 15");
  await f.move("home");
  assert.equal(f.db.read("users/author").boxCount, 40);
  await f.move("retired"); await f.move("arena", "republish");
  const again = await f.move("tournament", "promote-again");
  assert.equal(again.result.alreadyAwarded, true);
  await f.move("home", "home-again");
  assert.equal(f.db.read("users/author").boxCount, 40);
  assert.equal(f.db.paths("arena_awards/").length, 2);
  const ledger = f.db.paths(`${LEDGER_PATH}/`).map((path) => f.db.read(path));
  assert.deepEqual(ledger.map((row) => row.delta).sort((a, b) => a - b), [15, 20]);
  assert.ok(ledger.every((row) => row.currency === "boxes" && row.reason === "arena_milestone"));
});

test("role revocation races are retried before award, and failed promotion commit writes nothing", async () => {
  const f = setup(); await f.move("arena");
  f.db.beforeCommitOnce = (db) => db.put("server_roles/curator", {curator: false});
  assert.equal((await f.move("tournament", "revoked")).result.code, "CURATOR_REQUIRED");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "arena");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  f.db.put("server_roles/curator", {curator: true}); f.db.failNextCommit = true;
  await assert.rejects(() => f.move("tournament", "failed-commit"), /injected/);
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "arena");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.paths(`${LEDGER_PATH}/`).length, 0);
});

test("ended UTC season finalizes frozen Arena membership after lesson moved away; award ten once per quarter", async () => {
  const f = setup(); await f.move("arena");
  for (let i = 0; i < 20; i++) { f.clock.now++; await f.rate(`u${i}`, 3); }
  await f.move("tournament"); await f.move("home");
  f.clock.now = Q2 - 1;
  assert.equal((await f.service.finalizeSeason({uid: "curator", mutationId: "too-early", quarterAtMs: Q1})).result.code,
    "SEASON_NOT_ENDED");
  f.clock.now = Q2;
  const [a, b] = await Promise.all([
    f.service.finalizeSeason({uid: "curator", mutationId: "finalize-a", quarterAtMs: Q1}),
    f.service.finalizeSeason({uid: "curator", mutationId: "finalize-b", quarterAtMs: Q1}),
  ]);
  assert.equal(a.result.winner.lessonId, "lesson"); assert.equal(a.result.winner.ratingCount, 20);
  assert.deepEqual(a.result, b.result);
  assert.equal(f.db.read("users/author").boxCount, 50, "5 old + 15 + 20 + 10");
  assert.equal(f.db.read("arena_seasons/2026-Q1").status, "FINALIZED");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "home");
  assert.equal(f.db.paths(`${LEDGER_PATH}/`).map((p) => f.db.read(p)).filter((row) => row.reason === "arena_season").length, 1);
  const before = clone(a.result);
  f.clock.now++;
  await f.rate("new-quarter", 1);
  const repeat = await f.service.finalizeSeason({uid: "curator", mutationId: "later-finalize", quarterAtMs: Q1});
  assert.deepEqual(repeat.result, before);
  assert.equal(f.db.read("users/author").boxCount, 50);
});

test("season cannot be finalized by a player and 19 distinct ratings award no boxes", async () => {
  const f = setup();
  for (let i = 0; i < 19; i++) await f.rate(`u${i}`, 3);
  await f.rate("u0", 3, "second-vote-same-user");
  f.clock.now = Q2;
  assert.equal((await f.service.finalizeSeason({uid: "player", mutationId: "forged-finalize", quarterAtMs: Q1})).result.code,
    "CURATOR_REQUIRED");
  const result = await f.service.finalizeSeason({uid: "curator", mutationId: "nineteen", quarterAtMs: Q1});
  assert.equal(result.result.winner, null);
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read("users/author").boxCount, undefined);
});
