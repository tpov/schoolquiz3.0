"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {createHash} = require("node:crypto");
const {existsSync} = require("node:fs");
const {join} = require("node:path");
// Runs both in the separated draft tree and after the module is integrated into functions/.
const adjacentPolicy = join(__dirname, "arena-policy.js");
const policy = require(existsSync(adjacentPolicy) ? adjacentPolicy : join(__dirname, "../../arena/arena-policy.js"));
const {createArenaService, mutationDocumentId, awardDocumentId, voteDocumentId, LEDGER_PATH} = require("./arena-service");
const {MAX_MUTATION_ID_BYTES} = require("./arena-mutation-reservations");
const {seal, hash: seasonHash} = require("./arena-season-finalization");

const clone = (value) => value == null ? value : JSON.parse(JSON.stringify(value));
function resealEnvelope(value, patch) {
  const {digest, ...body} = {...value, ...patch};
  return {...body, digest: seasonHash(body)};
}
function xorBytes(left, right) {
  const output = Buffer.alloc(32);
  for (let index = 0; index < output.length; index++) output[index] = left[index] ^ right[index];
  return output;
}
function zeroXorDependency(deltas) {
  const basis = Array(256);
  for (let index = 0; index < deltas.length; index++) {
    let vector = Buffer.from(deltas[index]); let mask = 1n << BigInt(index);
    for (let bit = 0; bit < 256; bit++) {
      if ((vector[Math.floor(bit / 8)] & (1 << (bit % 8))) === 0) continue;
      const row = basis[bit];
      if (!row) { basis[bit] = {vector, mask}; vector = null; break; }
      vector = xorBytes(vector, row.vector); mask ^= row.mask;
    }
    if (vector?.every(byte => byte === 0)) {
      return Array.from({length: deltas.length}, (_, item) => item)
        .filter(item => (mask & (1n << BigInt(item))) !== 0n);
    }
  }
  assert.fail("257 SHA-256 vectors must have a non-empty GF(2) dependency");
}
function corruptMerkleNode(row) {
  const children = JSON.parse(row.childrenJson); const index = children.findIndex(child => Array.isArray(child) &&
    (child[1] === "L" || child[1] === "N"));
  assert.ok(index >= 0, "Merkle node must contain a committed sparse edge");
  const digestIndex = children[index][1] === "L" ? 3 : 4;
  const replacement = seasonHash(["test-tree-corruption", index, children[index][digestIndex]]);
  assert.notEqual(replacement, children[index][digestIndex]); children[index][digestIndex] = replacement;
  return resealEnvelope(row, {childrenJson: JSON.stringify(children)});
}

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
    p.startsWith(`${path}/`) && p.split("/").length === path.split("/").length + 1)
    .sort(([a], [b]) => Buffer.compare(Buffer.from(a), Buffer.from(b)));
  const queryVersion = (path) => JSON.stringify(children(path).map(([p, row]) => [p, row.version]));
  const snapshot = (path) => ({id: path.split("/").pop(), ref: ref(path), exists: store.has(path),
    data: () => clone(store.get(path)?.value)});
  const query = (path, nameOrder = false, after = null, maximum = Infinity) => ({
    path, kind: "query", after, maximum, key: JSON.stringify([path, nameOrder, after, maximum]),
    orderBy(field) {
      assert.equal(field, "__name__", "Arena finalizer pages only by Firestore document name");
      return query(path, true, after, maximum);
    },
    startAfter(id) {
      assert.equal(nameOrder, true, "Arena finalizer must order before applying a cursor");
      return query(path, nameOrder, id, maximum);
    },
    limit(count) {
      assert.ok(Number.isSafeInteger(count) && count > 0, "Arena finalizer needs a finite page limit");
      return query(path, nameOrder, after, count);
    },
  });
  const queryChildren = (target) => children(target.path).filter(([path]) => {
    if (target.after === null) return true;
    const id = path.split("/").pop();
    return Buffer.compare(Buffer.from(id), Buffer.from(target.after)) > 0;
  }).slice(0, target.maximum);
  const db = {
    doc: ref, collection: (path) => query(path), put,
    remove(path) { store.delete(path); version++; },
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
              queries.set(target.key, {path: target.path, version: queryVersion(target.path)});
              return {docs: queryChildren(target).map(([path]) => snapshot(path))};
            }
            reads.set(target.path, store.get(target.path)?.version || 0);
            return snapshot(target.path);
          },
          create(target, value) {
            assert.ok(writes.length < 500, "finalizer transaction exceeds Firestore write limit");
            writes.push({op: "create", path: target.path, value: clone(value)});
          },
          set(target, value, options) {
            assert.ok(writes.length < 500, "finalizer transaction exceeds Firestore write limit");
            writes.push({op: "set", path: target.path, value: clone(value), merge: options?.merge === true});
          },
        };
        const result = await body(tx);
        if (db.beforeCommitOnce) { const hook = db.beforeCommitOnce; db.beforeCommitOnce = null; await hook(db); }
        if ([...reads].some(([path, expected]) => (store.get(path)?.version || 0) !== expected) ||
            [...queries].some(([, expected]) => queryVersion(expected.path) !== expected.version)) { retries++; continue; }
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
  async function drainSeason(quarterId = "2026-Q1") {
    for (let step = 0; step < 200; step++) {
      const season = db.read(`arena_seasons/${quarterId}`);
      assert.ok(season?.finalization?.finalizationId, "test season must have a v2 finalization job");
      const result = await service.advanceSeasonFinalization({quarterId, finalizationId: season.finalization.finalizationId});
      if (["PUBLISHED", "REQUIRES_REVIEW"].includes(result.status)) return result;
    }
    assert.fail("season finalization exceeded the bounded test drain");
  }
  return {db, service, clock, completion, rate, move, drainSeason};
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

test("pre-reservation immutable Arena receipts retain their original JSON payload hash contract", async () => {
  const f = setup();
  const hash = value => createHash("sha256").update(JSON.stringify(value)).digest("hex");
  const ratePayload = {lessonId: "lesson", attemptId: "legacy-attempt", rating: 3};
  const rateReceipt = {schemaVersion: 1, mutationId: "legacy-rate", uid: "u", operation: "RATE_LESSON",
    payloadHash: hash(["RATE_LESSON", ratePayload]), decidedAtMs: Q1, status: "APPLIED", result: {legacy: true}};
  f.db.put(`arena_mutations/${mutationDocumentId("u", "legacy-rate")}`, rateReceipt);
  assert.deepEqual(await f.service.submitLessonRating({uid: "u", mutationId: "legacy-rate", ...ratePayload}), rateReceipt);
  const placementPayload = {lessonId: "lesson", targetShelf: "arena", reason: "legacy decision"};
  const placementReceipt = {schemaVersion: 1, mutationId: "legacy-placement", uid: "curator", operation: "TRANSITION_LESSON",
    payloadHash: hash(["TRANSITION_LESSON", placementPayload]), decidedAtMs: Q1, status: "APPLIED", result: {legacy: true}};
  f.db.put(`arena_mutations/${mutationDocumentId("curator", "legacy-placement")}`, placementReceipt);
  assert.deepEqual(await f.service.transitionLesson({uid: "curator", mutationId: "legacy-placement", ...placementPayload}), placementReceipt);
});

test("new Arena mutation IDs are bounded after legacy replay lookup", async () => {
  const f = setup(); const hash = value => createHash("sha256").update(JSON.stringify(value)).digest("hex");
  const legacyMutationId = "legacy-".padEnd(MAX_MUTATION_ID_BYTES + 1, "x");
  const legacyPayload = {lessonId: "lesson", attemptId: "legacy-long-attempt", rating: 3};
  const legacyReceipt = {schemaVersion: 1, mutationId: legacyMutationId, uid: "legacy", operation: "RATE_LESSON",
    payloadHash: hash(["RATE_LESSON", legacyPayload]), decidedAtMs: Q1, status: "APPLIED", result: {legacy: true}};
  f.db.put(`arena_mutations/${mutationDocumentId("legacy", legacyMutationId)}`, legacyReceipt);
  assert.deepEqual(await f.service.submitLessonRating({uid: "legacy", mutationId: legacyMutationId, ...legacyPayload}), legacyReceipt);

  const tooLongRating = "rating-".padEnd(MAX_MUTATION_ID_BYTES + 1, "x");
  const attemptId = f.completion("new", "new-long-attempt");
  await assert.rejects(() => f.service.submitLessonRating({uid: "new", mutationId: tooLongRating,
    lessonId: "lesson", attemptId, rating: 3}), error => error.code === "INVALID_ARGUMENT");
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("new", tooLongRating)}`), undefined);
  assert.equal(f.db.paths("arena_season_votes/").length, 0);

  f.clock.now = Q2;
  const tooLongFinalization = "finalize-".padEnd(MAX_MUTATION_ID_BYTES + 1, "x");
  await assert.rejects(() => f.service.finalizeSeason({uid: "curator", mutationId: tooLongFinalization, quarterAtMs: Q1}),
    error => error.code === "INVALID_ARGUMENT");
  assert.equal(f.db.read(`arena_mutation_reservations/${mutationDocumentId("curator", tooLongFinalization)}`), undefined);
  assert.equal(f.db.paths("arena_seasons/2026-Q1/finalizations/").length, 0);
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

test("ratings never manufacture curator approval; confirmed-fault removal stops new use and leaves frozen attempts intact", async () => {
  const f = setup();
  const attemptId = f.completion("player");
  const frozen = clone(f.db.read(`server_results/${attemptId}`));
  for (let voter = 0; voter < 5; voter++) await f.rate(`voter-${voter}`, 3);
  assert.equal(f.db.read("lesson_ratings/lesson").ratingCount, 5);
  assert.equal(f.db.read("lesson_placements/lesson"), undefined, "ratings alone never admit a lesson");

  await f.move("arena");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "arena");

  const retired = await f.move("retired", "retire-fault", "confirmed fault: broken answer key");
  assert.equal(retired.status, "APPLIED");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "retired");
  assert.equal(f.db.read("lesson_placements/lesson").lastReason, "confirmed fault: broken answer key");
  assert.deepEqual(f.db.read(`server_results/${attemptId}`), frozen, "removal must not rewrite frozen attempts");

  const forge = await f.service.transitionLesson({uid: "voter-0", mutationId: "forge-readmit", lessonId: "lesson",
    targetShelf: "arena", reason: "player asks it back"});
  assert.equal(forge.result.code, "CURATOR_REQUIRED");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "retired");
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
  const a = await f.service.finalizeSeason({uid: "curator", mutationId: "finalize-a", quarterAtMs: Q1});
  const b = await f.service.finalizeSeason({uid: "curator", mutationId: "finalize-b", quarterAtMs: Q1});
  assert.equal(a.status, "PENDING");
  assert.equal(b.status, "REJECTED"); assert.equal(b.result.code, "SEASON_ALREADY_FINALIZING");
  const reusedAttempt = f.completion("curator", "reused-finalize-b");
  await assert.rejects(() => f.service.submitLessonRating({uid: "curator", mutationId: "finalize-b", lessonId: "lesson",
    attemptId: reusedAttempt, rating: 3}), error => error.code === "MUTATION_REUSE");
  const drained = await f.drainSeason(); assert.equal(drained.status, "PUBLISHED");
  const initiator = f.db.read("arena_seasons/2026-Q1").finalization.requestedMutationId;
  const aReceipt = await f.service.finalizeSeason({uid: "curator", mutationId: initiator, quarterAtMs: Q1});
  const bReceipt = await f.service.finalizeSeason({uid: "curator", mutationId: "finalize-b", quarterAtMs: Q1});
  assert.equal(aReceipt.result.winner.lessonId, "lesson"); assert.equal(aReceipt.result.winner.ratingCount, 20);
  assert.equal(bReceipt.status, "REJECTED"); assert.equal(bReceipt.result.code, "SEASON_ALREADY_FINALIZING");
  assert.equal(f.db.read("users/author").boxCount, 50, "5 old + 15 + 20 + 10");
  assert.equal(f.db.read("arena_seasons/2026-Q1").status, "FINALIZED");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "home");
  assert.equal(f.db.paths(`${LEDGER_PATH}/`).map((p) => f.db.read(p)).filter((row) => row.reason === "arena_season").length, 1);
  const before = clone(aReceipt.result);
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
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "nineteen", quarterAtMs: Q1});
  assert.equal(pending.status, "PENDING");
  const drained = await f.drainSeason(); assert.equal(drained.status, "PUBLISHED");
  const result = await f.service.finalizeSeason({uid: "curator", mutationId: "nineteen", quarterAtMs: Q1});
  assert.equal(result.result.winner, null);
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read("users/author").boxCount, undefined);
});

test("resumable finalizer handles a source page with repeated attempt data and preserves the canonical latest vote", async () => {
  const f = setup(); await f.move("arena");
  const quarterId = "2026-Q1"; const rows = [];
  for (let index = 0; index < 21; index++) {
    const uid = index === 1 ? "u0" : `u${index - (index > 1 ? 1 : 0)}`;
    const attemptId = `attempt-${uid}`; const rating = index === 1 ? 3 : index === 0 ? 1 : 2;
    const vote = {ratingId: `rating-${index}`, uid, lessonId: "lesson", attemptId, rating,
      acceptedAtMs: Q1 + 10 + index, acceptedSequence: index + 1};
    const completion = {attemptId, uid, lessonId: "lesson", status: "SETTLED", completed: true, settledAtMs: Q1};
    const sourceId = `vote-${String(index).padStart(3, "0")}`;
    const source = {vote, completion, authorUidAtAcceptance: "author", activityKind: "ARENA", mutationId: `seed-${index}`};
    f.db.put(`arena_season_votes/${quarterId}/votes/${sourceId}`, source); rows.push(source);
  }
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "paged-repeat", quarterAtMs: Q1});
  assert.equal(pending.status, "PENDING");
  const finalizationId = pending.finalizationId;
  assert.equal((await f.drainSeason()).status, "PUBLISHED");
  const receipt = await f.service.finalizeSeason({uid: "curator", mutationId: "paged-repeat", quarterAtMs: Q1});
  const expected = policy.rankSeasonLessons({lessons: [f.db.read(`arena_seasons/${quarterId}/members/lesson`)],
    ratings: rows.map(row => row.vote), completions: rows.map(row => row.completion), quarterAtMs: Q1});
  assert.equal(receipt.result.winner.ratingCount, 20);
  assert.equal(receipt.result.winner.ratingSum, 41, "latest u0 rating must replace, not double count");
  assert.deepEqual(receipt.result.winner, expected.winner && {
    lessonId: expected.winner.lessonId, authorUid: expected.winner.authorUid, publishedAtMs: expected.winner.publishedAtMs,
    ratingSum: expected.winner.ratingSum, ratingCount: expected.winner.ratingCount, weightedScore: expected.winner.weightedScore,
  });
  const job = f.db.read(`arena_seasons/${quarterId}/finalizations/${finalizationId}`);
  assert.equal(job.voteCount, 21); assert.equal(job.selectionCount, 20); assert.equal(job.phase, "PUBLISHED");
  assert.ok(/^[a-f0-9]{64}$/.test(receipt.result.calculationManifestDigest));
  const frozenVotePath = f.db.paths(`arena_seasons/${quarterId}/finalizations/${finalizationId}/votes/`).at(0);
  assert.equal(Object.hasOwn(f.db.read(frozenVotePath).row, "mutationId"), false);
});

test("checkpoint map order is non-authoritative, but a broken reservation ends in review without money or a receipt", async () => {
  const f = setup(); await f.move("arena"); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "tampered-reservation", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  // Mimic an SDK returning the same checkpoint maps in a different insertion order.
  const reverse = value => Array.isArray(value) ? value.map(reverse) : value && typeof value === "object" ?
    Object.fromEntries(Object.keys(value).reverse().map(key => [key, reverse(value[key])])) : value;
  f.db.put(jobPath, reverse(f.db.read(jobPath)));
  for (let step = 0; step < 20 && f.db.read(jobPath).phase !== "PUBLISH"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  assert.equal(f.db.read(jobPath).phase, "PUBLISH");
  const reservationPath = `arena_mutation_reservations/${f.db.read(jobPath).requestDocumentId}`;
  f.db.put(reservationPath, {...f.db.read(reservationPath), payloadHash: "0".repeat(64)});
  const reviewed = await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  assert.equal(reviewed.status, "REQUIRES_REVIEW");
  assert.equal((await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId})).status,
    "REQUIRES_REVIEW");
  const retry = await f.service.finalizeSeason({uid: "curator", mutationId: "tampered-reservation", quarterAtMs: Q1});
  assert.equal(retry.status, "REQUIRES_REVIEW");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "tampered-reservation")}`), undefined);
});

test("finalization status is curator-only, bounded, and exposes no job cursor, lease, or source rows", async () => {
  const f = setup(); await f.move("arena"); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "status", quarterAtMs: Q1});
  await assert.rejects(() => f.service.getSeasonFinalizationStatus({uid: "player", quarterAtMs: Q1}),
    error => error.code === "CURATOR_REQUIRED");
  await assert.rejects(() => f.service.getSeasonFinalizationStatus({uid: "curator", quarterAtMs: Q1, phase: "PUBLISH"}),
    error => error.code === "INVALID_ARGUMENT");
  const status = await f.service.getSeasonFinalizationStatus({uid: "curator", quarterAtMs: Q1});
  assert.deepEqual(Object.keys(status).sort(), ["finalizationId", "operation", "phase", "quarterId", "schemaVersion", "status", "updatedAtMs"]);
  assert.equal(status.finalizationId, pending.finalizationId); assert.equal(status.status, "PENDING");
  assert.ok(!JSON.stringify(status).includes("cursor")); assert.ok(!JSON.stringify(status).includes("lease"));
});

test("worker path quarantines missing or corrupt finalization state without a receipt or award", async () => {
  const missing = setup(); await missing.move("arena"); missing.clock.now = Q2;
  const pending = await missing.service.finalizeSeason({uid: "curator", mutationId: "missing-job", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  missing.db.remove(jobPath);
  const quarantined = await missing.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"});
  assert.equal(quarantined.status, "REQUIRES_REVIEW");
  assert.equal(missing.db.read("arena_seasons/2026-Q1").status, "REQUIRES_REVIEW");
  assert.equal(missing.db.read(`arena_mutations/${mutationDocumentId("curator", "missing-job")}`), undefined);
  assert.equal(missing.db.paths("arena_awards/").length, 0);
  assert.equal((await missing.service.finalizeSeason({uid: "curator", mutationId: "missing-job", quarterAtMs: Q1})).status,
    "REQUIRES_REVIEW");
  const missingStatus = await missing.service.getSeasonFinalizationStatus({uid: "curator", quarterAtMs: Q1});
  assert.equal(missingStatus.status, "REQUIRES_REVIEW"); assert.equal(missingStatus.phase, "REQUIRES_REVIEW");

  const corrupt = setup(); await corrupt.move("arena"); corrupt.clock.now = Q2;
  const corruptPending = await corrupt.service.finalizeSeason({uid: "curator", mutationId: "corrupt-job", quarterAtMs: Q1});
  corrupt.db.put(`arena_seasons/2026-Q1/finalizations/${corruptPending.finalizationId}`, {schemaVersion: 1, phase: "SCAN_MEMBERS"});
  assert.equal((await corrupt.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status, "REQUIRES_REVIEW");
  assert.equal(corrupt.db.read("arena_seasons/2026-Q1").reviewCode, "FINALIZATION_JOB_REQUIRES_REPAIR");
  assert.equal(corrupt.db.paths("arena_awards/").length, 0);

  const metadata = setup(); await metadata.move("arena"); metadata.clock.now = Q2;
  const metadataPending = await metadata.service.finalizeSeason({uid: "curator", mutationId: "broken-metadata", quarterAtMs: Q1});
  const seasonPath = "arena_seasons/2026-Q1";
  metadata.db.put(seasonPath, {...metadata.db.read(seasonPath), finalization: {schemaVersion: 2}});
  assert.equal((await metadata.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status, "REQUIRES_REVIEW");
  assert.equal(metadata.db.read(seasonPath).reviewCode, "FINALIZATION_METADATA_REQUIRES_REPAIR");
  const metadataStatus = await metadata.service.getSeasonFinalizationStatus({uid: "curator", quarterAtMs: Q1});
  assert.deepEqual(metadataStatus, {schemaVersion: 2, operation: "FINALIZE_SEASON", quarterId: "2026-Q1",
    finalizationId: null, status: "REQUIRES_REVIEW", phase: "REQUIRES_REVIEW", updatedAtMs: Q2});
  assert.equal((await metadata.service.finalizeSeason({uid: "curator", mutationId: "broken-metadata", quarterAtMs: Q1})).status,
    "REQUIRES_REVIEW");
  assert.equal(metadata.db.read(`arena_mutations/${mutationDocumentId("curator", "broken-metadata")}`), undefined);

  for (const badFinalizationId of ["bad/id", "x".repeat(1501)]) {
    const malformedId = setup(); await malformedId.move("arena"); malformedId.clock.now = Q2;
    await malformedId.service.finalizeSeason({uid: "curator", mutationId: `bad-id-${badFinalizationId.length}`, quarterAtMs: Q1});
    malformedId.db.put(seasonPath, {...malformedId.db.read(seasonPath), finalization: {
      ...malformedId.db.read(seasonPath).finalization, finalizationId: badFinalizationId,
    }});
    assert.equal((await malformedId.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status,
      "REQUIRES_REVIEW");
    assert.equal(malformedId.db.read(seasonPath).reviewCode, "FINALIZATION_METADATA_REQUIRES_REPAIR");
  }
});

test("quarantine transaction does not overwrite a concurrently terminal season", async () => {
  const f = setup(); await f.move("arena"); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "fenced-repair", quarterAtMs: Q1});
  f.db.remove(`arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`);
  f.db.beforeCommitOnce = db => db.put("arena_seasons/2026-Q1", {
    ...db.read("arena_seasons/2026-Q1"), status: "FINALIZED", finalizedAtMs: Q2, result: {winner: null},
  });
  const result = await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  assert.equal(result.status, "PUBLISHED");
  assert.equal(f.db.read("arena_seasons/2026-Q1").status, "FINALIZED");
});

test("sealed impossible policy data is quarantined rather than retried as an outage", async () => {
  const f = setup(); await f.move("arena"); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "impossible-winner", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  const {checkpointDigest, ...head} = f.db.read(jobPath);
  f.db.put(jobPath, seal({...head, winner: {lessonId: "lesson", authorUid: "author", publishedAtMs: Q1,
    ratingSum: 100, ratingCount: 20, weightedScore: {numerator: 140, denominator: 40}}}));
  assert.equal((await f.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status, "REQUIRES_REVIEW");
  assert.equal(f.db.read("arena_seasons/2026-Q1").reviewCode, "FINALIZATION_JOB_REQUIRES_REPAIR");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "impossible-winner")}`), undefined);
});

test("self-hashed malformed and path-copied frozen rows are quarantined before they affect a winner", async () => {
  async function start(mutationId) {
    const f = setup(); await f.move("arena"); await f.rate("player", 3, `rate-${mutationId}`);
    f.clock.now = Q2;
    const pending = await f.service.finalizeSeason({uid: "curator", mutationId, quarterAtMs: Q1});
    const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
    for (let step = 0; step < 20 && f.db.read(jobPath).phase !== "SELECT"; step++) {
      await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
    }
    assert.equal(f.db.read(jobPath).phase, "SELECT");
    return {f, pending, jobPath};
  }

  const malformed = await start("malformed-frozen-vote");
  const malformedVotePath = malformed.f.db.paths(`${malformed.jobPath}/votes/`).at(0);
  const original = malformed.f.db.read(malformedVotePath);
  const body = {sourceId: original.sourceId, completion: original.row.completion,
    authorUidAtAcceptance: original.row.authorUidAtAcceptance};
  const row = {...body, sourceDigest: seasonHash(body)};
  malformed.f.db.put(malformedVotePath, {sourceId: row.sourceId, row, digest: row.sourceDigest});
  assert.equal((await malformed.f.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status,
    "REQUIRES_REVIEW");
  assert.equal(malformed.f.db.paths("arena_awards/").length, 0);

  const copiedVote = await start("copied-frozen-vote");
  const copiedVotePath = copiedVote.f.db.paths(`${copiedVote.jobPath}/votes/`).at(0);
  copiedVote.f.db.put(`${copiedVote.jobPath}/votes/copied-vote`, copiedVote.f.db.read(copiedVotePath));
  assert.equal((await copiedVote.f.service.advanceSeasonFinalizationForQuarter({quarterId: "2026-Q1"})).status,
    "REQUIRES_REVIEW");
  assert.equal(copiedVote.f.db.paths("arena_awards/").length, 0);

  const copiedSelected = await start("copied-selected");
  for (let step = 0; step < 20 && copiedSelected.f.db.read(copiedSelected.jobPath).phase !== "AGGREGATE"; step++) {
    await copiedSelected.f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: copiedSelected.pending.finalizationId});
  }
  assert.equal(copiedSelected.f.db.read(copiedSelected.jobPath).phase, "AGGREGATE");
  const selectedPath = copiedSelected.f.db.paths(`${copiedSelected.jobPath}/selected/`).at(0);
  copiedSelected.f.db.put(`${copiedSelected.jobPath}/selected/copied-selected`, copiedSelected.f.db.read(selectedPath));
  assert.equal((await copiedSelected.f.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(copiedSelected.f.db.paths("arena_awards/").length, 0);
});

test("a stale selected candidate is rejected even when its envelope is recomputed", async () => {
  const f = setup(); await f.move("arena");
  await f.rate("player", 1, "old-player-vote"); f.clock.now++;
  await f.rate("player", 3, "new-player-vote");
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "selected-rollback", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  for (let step = 0; step < 40 && f.db.read(jobPath).phase !== "COMPARE_SELECTION"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  assert.equal(f.db.read(jobPath).phase, "COMPARE_SELECTION");
  const oldVote = f.db.paths(`${jobPath}/votes/`).map(path => f.db.read(path).row)
    .find(row => row.rating.rating === 1);
  const member = f.db.read(f.db.paths(`${jobPath}/members/`).at(0)).member;
  const oldCandidate = {sourceId: oldVote.sourceId, rating: oldVote.rating, completion: oldVote.completion,
    authorUidAtAcceptance: oldVote.authorUidAtAcceptance, member, sourceDigest: oldVote.sourceDigest};
  const selectedPath = f.db.paths(`${jobPath}/selected/`).at(0); const selected = f.db.read(selectedPath);
  const {digest: ignored, ...body} = {...selected, candidate: oldCandidate};
  f.db.put(selectedPath, {...body, digest: seasonHash(body)});
  assert.equal((await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId})).status,
    "REQUIRES_REVIEW");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "selected-rollback")}`), undefined);
});

test("a valid partial aggregate replay is rejected before it can affect the winner", async () => {
  const f = setup(); await f.move("arena");
  for (let index = 0; index < 21; index++) await f.rate(`aggregate-replay-${index}`, 3);
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "aggregate-rollback", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  for (let step = 0; step < 100 && f.db.read(jobPath).phase !== "AGGREGATE"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  assert.equal(f.db.read(jobPath).phase, "AGGREGATE");
  await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  const aggregatePath = f.db.paths(`${jobPath}/aggregates/`).at(0); const partial = f.db.read(aggregatePath);
  assert.equal(partial.ratingCount, 5, "the first bounded aggregate page is a valid partial checkpoint");
  for (let step = 0; step < 100 && f.db.read(jobPath).phase !== "PICK_WINNER"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  assert.equal(f.db.read(jobPath).phase, "PICK_WINNER");
  assert.equal(f.db.read(aggregatePath).ratingCount, 21);
  f.db.put(aggregatePath, partial);
  assert.equal((await f.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "aggregate-rollback")}`), undefined);
});

test("a coordinated rollback of both selected or aggregate mirrors cannot pass the sealed semantic root", async () => {
  const selected = setup(); await selected.move("arena");
  // The first SELECT/VERIFY_SELECTION page sees the old player vote.  Its replacement is on the
  // second page, so both checkpoint collections contain a valid historical candidate to replay.
  for (let index = 0; index < 6; index++) {
    const uid = index === 0 || index === 5 ? "rollback-player" : `rollback-other-${index}`;
    const attemptId = `rollback-attempt-${index}`;
    const rating = index === 0 ? 1 : index === 5 ? 3 : 2;
    const vote = {ratingId: `rollback-rating-${index}`, uid, lessonId: "lesson", attemptId, rating,
      acceptedAtMs: Q1 + index + 1, acceptedSequence: index + 1};
    const completion = {attemptId, uid, lessonId: "lesson", status: "SETTLED", completed: true, settledAtMs: Q1};
    selected.db.put(`arena_season_votes/2026-Q1/votes/vote-${String(index).padStart(3, "0")}`, {
      vote, completion, authorUidAtAcceptance: "author", activityKind: "ARENA", mutationId: `rollback-seed-${index}`,
    });
  }
  selected.clock.now = Q2;
  const selectedPending = await selected.service.finalizeSeason({uid: "curator", mutationId: "paired-selected-rollback", quarterAtMs: Q1});
  const selectedJob = `arena_seasons/2026-Q1/finalizations/${selectedPending.finalizationId}`;
  for (let step = 0; step < 80 && selected.db.read(selectedJob).phase !== "SELECT"; step++) {
    await selected.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: selectedPending.finalizationId});
  }
  assert.equal(selected.db.read(selectedJob).phase, "SELECT");
  await selected.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: selectedPending.finalizationId});
  const oldWorkingSelectedPath = selected.db.paths(`${selectedJob}/selected/`).find(path =>
    selected.db.read(path).candidate.rating.uid === "rollback-player");
  const oldWorkingSelected = selected.db.read(oldWorkingSelectedPath);
  assert.equal(oldWorkingSelected.candidate.rating.rating, 1);
  for (let step = 0; step < 80 && selected.db.read(selectedJob).phase !== "VERIFY_SELECTION"; step++) {
    await selected.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: selectedPending.finalizationId});
  }
  assert.equal(selected.db.read(selectedJob).phase, "VERIFY_SELECTION");
  await selected.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: selectedPending.finalizationId});
  const oldVerifiedSelectedPath = selected.db.paths(`${selectedJob}/selected_verified/`).find(path =>
    selected.db.read(path).candidate.rating.uid === "rollback-player");
  const oldVerifiedSelected = selected.db.read(oldVerifiedSelectedPath);
  assert.equal(oldVerifiedSelected.candidate.rating.rating, 1);
  for (let step = 0; step < 80 && selected.db.read(selectedJob).phase !== "COMPARE_SELECTION"; step++) {
    await selected.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: selectedPending.finalizationId});
  }
  assert.equal(selected.db.read(selectedJob).phase, "COMPARE_SELECTION");
  const sealedSelectionRoot = selected.db.read(selectedJob).selectionContentHash;
  selected.db.put(oldWorkingSelectedPath, oldWorkingSelected);
  selected.db.put(oldVerifiedSelectedPath, oldVerifiedSelected);
  assert.equal(selected.db.read(selectedJob).selectionContentHash, sealedSelectionRoot);
  assert.equal((await selected.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(selected.db.read(selectedJob).reviewCode, "INVALID_SOURCE");
  assert.equal(selected.db.read("arena_seasons/2026-Q1").reviewCode, "INVALID_SOURCE");
  assert.equal(selected.db.paths("arena_awards/").length, 0);
  assert.equal(selected.db.read(`arena_mutations/${mutationDocumentId("curator", "paired-selected-rollback")}`), undefined);

  const aggregate = setup(); await aggregate.move("arena");
  for (let index = 0; index < 21; index++) await aggregate.rate(`paired-aggregate-${index}`, 3);
  aggregate.clock.now = Q2;
  const aggregatePending = await aggregate.service.finalizeSeason({uid: "curator", mutationId: "paired-aggregate-rollback", quarterAtMs: Q1});
  const aggregateJob = `arena_seasons/2026-Q1/finalizations/${aggregatePending.finalizationId}`;
  for (let step = 0; step < 120 && aggregate.db.read(aggregateJob).phase !== "AGGREGATE"; step++) {
    await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  }
  assert.equal(aggregate.db.read(aggregateJob).phase, "AGGREGATE");
  await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  const workingAggregatePath = aggregate.db.paths(`${aggregateJob}/aggregates/`).at(0);
  const oldWorkingAggregate = aggregate.db.read(workingAggregatePath);
  assert.equal(oldWorkingAggregate.ratingCount, 5);
  for (let step = 0; step < 120 && aggregate.db.read(aggregateJob).phase !== "VERIFY_AGGREGATES"; step++) {
    await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  }
  assert.equal(aggregate.db.read(aggregateJob).phase, "VERIFY_AGGREGATES");
  await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  const verifiedAggregatePath = aggregate.db.paths(`${aggregateJob}/aggregates_verified/`).at(0);
  const oldVerifiedAggregate = aggregate.db.read(verifiedAggregatePath);
  assert.equal(oldVerifiedAggregate.ratingCount, 5);
  for (let step = 0; step < 120 && aggregate.db.read(aggregateJob).phase !== "COMPARE_AGGREGATES"; step++) {
    await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  }
  assert.equal(aggregate.db.read(aggregateJob).phase, "COMPARE_AGGREGATES");
  const sealedAggregateRoot = aggregate.db.read(aggregateJob).aggregateContentHash;
  aggregate.db.put(workingAggregatePath, oldWorkingAggregate);
  aggregate.db.put(verifiedAggregatePath, oldVerifiedAggregate);
  assert.equal(aggregate.db.read(aggregateJob).aggregateContentHash, sealedAggregateRoot);
  assert.equal((await aggregate.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(aggregate.db.read(aggregateJob).reviewCode, "INVALID_SOURCE");
  assert.equal(aggregate.db.read("arena_seasons/2026-Q1").reviewCode, "INVALID_SOURCE");
  assert.equal(aggregate.db.paths("arena_awards/").length, 0);
  assert.equal(aggregate.db.read(`arena_mutations/${mutationDocumentId("curator", "paired-aggregate-rollback")}`), undefined);
});

test("source Merkle roots are required by aggregation, winner selection, and checkpoint audit", async () => {
  async function reach(mutationId, phase) {
    const f = setup(); await f.move("arena"); await f.rate(`merkle-${mutationId}`, 3, `rate-${mutationId}`);
    f.clock.now = Q2;
    const pending = await f.service.finalizeSeason({uid: "curator", mutationId, quarterAtMs: Q1});
    const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
    for (let step = 0; step < 180 && f.db.read(jobPath).phase !== phase; step++) {
      await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
    }
    assert.equal(f.db.read(jobPath).phase, phase, mutationId);
    return {f, pending, jobPath, mutationId};
  }
  async function expectReview(run, advance = true) {
    const result = advance ? await run.f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: run.pending.finalizationId}) :
      await run.f.drainSeason();
    assert.equal(result.status, "REQUIRES_REVIEW", run.mutationId);
    assert.equal(run.f.db.read(run.jobPath).reviewCode, "INVALID_SOURCE", run.mutationId);
    assert.equal(run.f.db.read("arena_seasons/2026-Q1").reviewCode, "INVALID_SOURCE", run.mutationId);
    assert.equal(run.f.db.paths("arena_awards/").length, 0, run.mutationId);
    assert.equal(run.f.db.read(`arena_mutations/${mutationDocumentId("curator", run.mutationId)}`), undefined, run.mutationId);
  }
  const selection = await reach("selection-tree-corruption", "AGGREGATE");
  const selectionRootPath = selection.f.db.paths(`${selection.jobPath}/selection_tree/`)
    .find(path => selection.f.db.read(path).depth === 0);
  assert.ok(selectionRootPath); selection.f.db.put(selectionRootPath, corruptMerkleNode(selection.f.db.read(selectionRootPath)));
  await expectReview(selection);

  const aggregate = await reach("aggregate-tree-corruption", "PICK_WINNER");
  const aggregateRootPath = aggregate.f.db.paths(`${aggregate.jobPath}/aggregate_tree/`)
    .find(path => aggregate.f.db.read(path).depth === 0);
  assert.ok(aggregateRootPath); aggregate.f.db.put(aggregateRootPath, corruptMerkleNode(aggregate.f.db.read(aggregateRootPath)));
  await expectReview(aggregate);

  const audit = await reach("audit-tree-corruption", "VERIFY_CHECKPOINTS");
  const auditRootPath = audit.f.db.paths(`${audit.jobPath}/selection_tree/`)
    .find(path => audit.f.db.read(path).depth === 0);
  assert.ok(auditRootPath); audit.f.db.put(auditRootPath, corruptMerkleNode(audit.f.db.read(auditRootPath)));
  await expectReview(audit, false);
});

test("an empty finished map rejects a self-sealed noncanonical Merkle root", async () => {
  const f = setup(); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "empty-tree-root", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  for (let step = 0; step < 40 && f.db.read(jobPath).phase !== "COMPARE_SELECTION"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  const job = f.db.read(jobPath); assert.equal(job.phase, "COMPARE_SELECTION"); assert.equal(job.selectionCount, 0);
  const replacementRoot = seasonHash(["self-sealed-empty-root", job.selectionContentHash]);
  assert.notEqual(replacementRoot, job.selectionContentHash);
  f.db.put(jobPath, seal({...job, selectionContentHash: replacementRoot}));
  assert.equal((await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId})).status,
    "REQUIRES_REVIEW");
  assert.equal(f.db.read("arena_seasons/2026-Q1").reviewCode, "FINALIZATION_JOB_REQUIRES_REPAIR");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "empty-tree-root")}`), undefined);
});

test("a 257-row zero-XOR replacement set cannot forge a source-derived selection tree", async () => {
  const f = setup(); await f.move("arena");
  for (let index = 0; index < 257; index++) {
    const suffix = String(index).padStart(3, "0"); const uid = `xor-user-${suffix}`;
    for (const [kind, rating, acceptedAtMs, acceptedSequence] of [["old", 1, Q1 + 100 + index, 1], ["new", 3, Q1 + 10_000 + index, 2]]) {
      const attemptId = `${kind}-attempt-${suffix}`;
      f.db.put(`arena_season_votes/2026-Q1/votes/${kind}-${suffix}`, {
        vote: {ratingId: `${kind}-rating-${suffix}`, uid, lessonId: "lesson", attemptId, rating, acceptedAtMs, acceptedSequence},
        completion: {attemptId, uid, lessonId: "lesson", status: "SETTLED", completed: true, settledAtMs: Q1},
        authorUidAtAcceptance: "author", activityKind: "ARENA", mutationId: `xor-seed-${kind}-${suffix}`,
      });
    }
  }
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "xor-257", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  for (let step = 0; step < 450 && f.db.read(jobPath).phase !== "COMPARE_SELECTION"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  assert.equal(f.db.read(jobPath).phase, "COMPARE_SELECTION");
  const frozenMember = f.db.read(f.db.paths(`${jobPath}/members/`).at(0)).member;
  const oldCandidates = new Map();
  for (const path of f.db.paths(`${jobPath}/votes/`)) {
    const row = f.db.read(path).row;
    if (!row.sourceId.startsWith("old-")) continue;
    oldCandidates.set(row.rating.uid, {sourceId: row.sourceId, rating: row.rating, completion: row.completion,
      authorUidAtAcceptance: row.authorUidAtAcceptance, member: frozenMember, sourceDigest: row.sourceDigest});
  }
  const workingByUid = new Map(); const verifiedByUid = new Map();
  for (const path of f.db.paths(`${jobPath}/selected/`)) workingByUid.set(f.db.read(path).candidate.rating.uid, path);
  for (const path of f.db.paths(`${jobPath}/selected_verified/`)) verifiedByUid.set(f.db.read(path).candidate.rating.uid, path);
  assert.equal(oldCandidates.size, 257); assert.equal(workingByUid.size, 257); assert.equal(verifiedByUid.size, 257);
  const rows = [...oldCandidates.keys()].sort().map(uid => {
    const oldCandidate = oldCandidates.get(uid); const verifiedPath = verifiedByUid.get(uid);
    const currentCandidate = f.db.read(verifiedPath).candidate;
    assert.equal(currentCandidate.rating.rating, 3);
    return {uid, oldCandidate, workingPath: workingByUid.get(uid), verifiedPath,
      delta: xorBytes(Buffer.from(seasonHash(["selection", ["lesson", uid], oldCandidate]), "hex"),
        Buffer.from(seasonHash(["selection", ["lesson", uid], currentCandidate]), "hex"))};
  });
  const subset = zeroXorDependency(rows.map(row => row.delta));
  assert.ok(subset.length > 0);
  const totalDelta = subset.reduce((total, index) => xorBytes(total, rows[index].delta), Buffer.alloc(32));
  assert.ok(totalDelta.every(byte => byte === 0), "selected replacement subset preserves the old XOR root");
  const sealedSelectionRoot = f.db.read(jobPath).selectionContentHash;
  for (const index of subset) {
    const row = rows[index];
    f.db.put(row.workingPath, resealEnvelope(f.db.read(row.workingPath), {candidate: row.oldCandidate}));
    f.db.put(row.verifiedPath, resealEnvelope(f.db.read(row.verifiedPath), {candidate: row.oldCandidate}));
  }
  assert.equal(f.db.read(jobPath).selectionContentHash, sealedSelectionRoot);
  assert.equal((await f.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(f.db.read(jobPath).reviewCode, "INVALID_SOURCE");
  assert.equal(f.db.paths("arena_awards/").length, 0);
  assert.equal(f.db.read(`arena_mutations/${mutationDocumentId("curator", "xor-257")}`), undefined);
  assert.ok(f.db.commits.every(commit => commit.length <= 500), "each bounded Merkle transaction stays below 500 writes");
});

test("a key may split inside an existing compressed Merkle edge", async () => {
  // Find deterministic SHA routes where A/B first form a branch below byte 1, then C shares
  // byte 0 but differs at byte 1.  C must split the root -> A/B compressed edge rather than be
  // mistaken for corrupt data.  Five first-page rows force that branch to be persisted before C.
  const byTwoBytes = new Map(); let pair = null;
  for (let index = 0; index < 20_000 && !pair; index++) {
    const uid = `compressed-route-${index}`;
    const route = seasonHash(["arena-season-merkle-v7", "selection", "key", ["lesson", uid]]);
    const prefix = route.slice(0, 4); const prior = byTwoBytes.get(prefix);
    if (prior && prior.route !== route) pair = {left: prior, right: {uid, route}};
    else byTwoBytes.set(prefix, {uid, route});
  }
  assert.ok(pair, "test fixture must find a shared two-byte SHA prefix");
  let split = null;
  for (let index = 20_000; index < 40_000 && !split; index++) {
    const uid = `compressed-split-${index}`;
    const route = seasonHash(["arena-season-merkle-v7", "selection", "key", ["lesson", uid]]);
    if (route.slice(0, 2) === pair.left.route.slice(0, 2) && route.slice(2, 4) !== pair.left.route.slice(2, 4)) {
      split = {uid, route};
    }
  }
  assert.ok(split, "test fixture must find a route that diverges inside the compressed edge");

  const f = setup(); await f.move("arena");
  const uids = [pair.left.uid, pair.right.uid, "compressed-filler-a", "compressed-filler-b", "compressed-filler-c", split.uid];
  for (let index = 0; index < uids.length; index++) {
    const suffix = String(index).padStart(2, "0"); const uid = uids[index]; const attemptId = `compressed-attempt-${suffix}`;
    f.db.put(`arena_season_votes/2026-Q1/votes/compressed-${suffix}`, {
      vote: {ratingId: `compressed-rating-${suffix}`, uid, lessonId: "lesson", attemptId, rating: 3,
        acceptedAtMs: Q1 + index + 1, acceptedSequence: index + 1},
      completion: {attemptId, uid, lessonId: "lesson", status: "SETTLED", completed: true, settledAtMs: Q1},
      authorUidAtAcceptance: "author", activityKind: "ARENA", mutationId: `compressed-seed-${suffix}`,
    });
  }
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "compressed-edge", quarterAtMs: Q1});
  assert.equal((await f.drainSeason()).status, "PUBLISHED");
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  assert.ok(f.db.paths(`${jobPath}/selection_tree/`).some(path => f.db.read(path).depth > 0));
  assert.ok(f.db.commits.every(commit => commit.length <= 500));
});

// This is intentionally an explicit acceptance-scale gate.  The fake store makes its 4097-key
// scan quadratic enough to obscure ordinary contract regressions on the constrained demo runner.
// Invoke with ARENA_SEASON_FINALIZATION_SCALE=1; the normal suite still parses this test but skips
// its expensive fixture.
test("a 4097-key source-derived trie remains sparse and keeps every transaction bounded", {
  skip: process.env.ARENA_SEASON_FINALIZATION_SCALE !== "1",
}, async t => {
  const f = setup(); await f.move("arena");
  const cohortSize = 4097;
  for (let index = 0; index < cohortSize; index++) {
    const suffix = String(index).padStart(4, "0"); const uid = `trie-user-${suffix}`; const attemptId = `trie-attempt-${suffix}`;
    f.db.put(`arena_season_votes/2026-Q1/votes/trie-${suffix}`, {
      vote: {ratingId: `trie-rating-${suffix}`, uid, lessonId: "lesson", attemptId, rating: 3,
        acceptedAtMs: Q1 + index + 1, acceptedSequence: index + 1},
      completion: {attemptId, uid, lessonId: "lesson", status: "SETTLED", completed: true, settledAtMs: Q1},
      authorUidAtAcceptance: "author", activityKind: "ARENA", mutationId: `trie-seed-${suffix}`,
    });
  }
  f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "trie-4097", quarterAtMs: Q1});
  const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
  for (let step = 0; step < 3_000 && f.db.read(jobPath).phase !== "COMPARE_SELECTION"; step++) {
    await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
  }
  const job = f.db.read(jobPath);
  assert.equal(job.phase, "COMPARE_SELECTION"); assert.equal(job.selectionCount, cohortSize);
  assert.equal(job.verifiedSelectionCount, cohortSize);
  const nodes = f.db.paths(`${jobPath}/selection_tree/`).map(path => f.db.read(path));
  assert.ok(nodes.length > 0 && nodes.length <= cohortSize,
    "a compressed trie has at most one internal node per distinct key, not 32 full vectors per key");
  for (const node of nodes) {
    const children = JSON.parse(node.childrenJson);
    assert.ok(children.length > 0 && children.length <= 256);
    assert.ok(children.every(edge => Array.isArray(edge) && (edge[1] === "L" || edge[1] === "N")));
  }
  const maxWrites = Math.max(...f.db.commits.map(commit => commit.length));
  assert.ok(maxWrites <= 500, "4097-key transactions stay below Firestore's write cap");
  t.diagnostic(`4097-key in-memory selection trie: ${nodes.length} branch docs; max transaction writes ${maxWrites}`);
});

test("missing frozen member, vote, selected, verified-selected, or aggregate rows cannot silently shorten a run", async () => {
  async function reach(mutationId, phase) {
    const f = setup(); await f.move("arena"); await f.rate("player", 3, `rate-${mutationId}`);
    f.clock.now = Q2;
    const pending = await f.service.finalizeSeason({uid: "curator", mutationId, quarterAtMs: Q1});
    const jobPath = `arena_seasons/2026-Q1/finalizations/${pending.finalizationId}`;
    for (let step = 0; step < 80 && f.db.read(jobPath).phase !== phase; step++) {
      await f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: pending.finalizationId});
    }
    assert.equal(f.db.read(jobPath).phase, phase);
    return {f, pending, jobPath};
  }
  const cases = [
    ["missing-member", "VERIFY_MEMBERS", "members"],
    ["missing-vote", "SELECT", "votes"],
    ["missing-selected", "COMPARE_SELECTION", "selected"],
    ["missing-verified-selected", "AGGREGATE", "selected_verified"],
    ["missing-aggregate", "PICK_WINNER", "aggregates_verified"],
  ];
  for (const [mutationId, phase, collection] of cases) {
    const run = await reach(mutationId, phase);
    run.f.db.remove(run.f.db.paths(`${run.jobPath}/${collection}/`).at(0));
    assert.equal((await run.f.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: run.pending.finalizationId})).status,
      "REQUIRES_REVIEW", mutationId);
    assert.equal(run.f.db.paths("arena_awards/").length, 0, mutationId);
  }
});

test("tampered frozen aggregate or pre-existing award marker quarantines without payment", async () => {
  const aggregate = setup(); await aggregate.move("arena");
  for (let index = 0; index < 20; index++) await aggregate.rate(`aggregate-${index}`, 3);
  aggregate.clock.now = Q2;
  const aggregatePending = await aggregate.service.finalizeSeason({uid: "curator", mutationId: "aggregate-tamper", quarterAtMs: Q1});
  const aggregateJob = `arena_seasons/2026-Q1/finalizations/${aggregatePending.finalizationId}`;
  for (let step = 0; step < 100 && aggregate.db.read(aggregateJob).phase !== "PICK_WINNER"; step++) {
    await aggregate.service.advanceSeasonFinalization({quarterId: "2026-Q1", finalizationId: aggregatePending.finalizationId});
  }
  assert.equal(aggregate.db.read(aggregateJob).phase, "PICK_WINNER");
  const aggregatePath = aggregate.db.paths(`${aggregateJob}/aggregates/`).at(0);
  aggregate.db.put(aggregatePath, {...aggregate.db.read(aggregatePath), ratingSum: 40});
  assert.equal((await aggregate.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(aggregate.db.paths("arena_awards/").length, 0);
  assert.equal(aggregate.db.read(`arena_mutations/${mutationDocumentId("curator", "aggregate-tamper")}`), undefined);

  const award = setup(); await award.move("arena");
  for (let index = 0; index < 20; index++) await award.rate(`award-${index}`, 3);
  award.clock.now = Q2;
  const key = JSON.stringify(["arena", "season", "2026-Q1"]);
  award.db.put(`arena_awards/${awardDocumentId(key)}`, {
    key, kind: "SEASON_WIN", recipientUid: "wrong-author", currency: "BOXES", amount: 10, awardedAtMs: Q2,
  });
  const awardPending = await award.service.finalizeSeason({uid: "curator", mutationId: "award-tamper", quarterAtMs: Q1});
  assert.equal((await award.drainSeason()).status, "REQUIRES_REVIEW");
  assert.equal(award.db.read("arena_seasons/2026-Q1").status, "REQUIRES_REVIEW");
  assert.equal(award.db.read(`arena_mutations/${mutationDocumentId("curator", "award-tamper")}`), undefined);
  assert.equal(award.db.read(`arena_awards/${awardDocumentId(key)}`).recipientUid, "wrong-author");
  assert.ok(awardPending.finalizationId);
});

test("a pending season reservation fences reused mutation IDs from rating and placement writes", async () => {
  const f = setup(); await f.move("arena"); f.clock.now = Q2;
  const pending = await f.service.finalizeSeason({uid: "curator", mutationId: "reserved-id", quarterAtMs: Q1});
  assert.equal(pending.status, "PENDING");
  f.completion("curator", "curator-attempt");
  await assert.rejects(() => f.service.submitLessonRating({uid: "curator", mutationId: "reserved-id", lessonId: "lesson",
    attemptId: "curator-attempt", rating: 3}), error => error.code === "MUTATION_REUSE");
  await assert.rejects(() => f.service.transitionLesson({uid: "curator", mutationId: "reserved-id", lessonId: "lesson",
    targetShelf: "tournament", reason: "must not share the reservation"}), error => error.code === "MUTATION_REUSE");
  assert.equal(f.db.read("lesson_placements/lesson").shelf, "arena");
});
