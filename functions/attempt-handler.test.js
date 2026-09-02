"use strict";

const assert = require("assert");
const Module = require("module");

const {createFirestoreStub} = require("./_firestore-stub");
const {ANSWERS, publishLesson, servedQuestions} = require("./_scored-lesson-fixture");
const {POINTS_PER_CHARGE} = require("./economy-constants");

/**
 * `submitLessonResultEvents` end to end, over a store, with nothing stubbed inside `index.js`.
 *
 * `attempt-wiring.test.js` proves each decision the handler makes by cutting the function that
 * makes it out of the source and running it. That is the right shape for a table of branches and it
 * has one hole it cannot close: **nothing there checks that the handler calls any of them.** Delete
 * the line that scores every attempt and `item.payable` is `undefined` for every attempt in the
 * batch — nothing charged, nothing rewarded, no rating counted, no tournament row written, for
 * device-scored attempts too — and every case in that suite stays green. A `String.indexOf` on the
 * handler's source text is the only thing that would have noticed, and it notices the wrong things:
 * it fails when the call is split across two lines and passes when the call is dead.
 *
 * So this suite loads `index.js` for real. `firebase-admin` is replaced in the module cache before
 * the require, with an in-memory store (`_firestore-stub.js`) rather than a set of expectations, and
 * the callable is driven through `onCall`'s own `.run`. What is asserted is what came out: the
 * document handed to `transaction.set`, the balance the player was left with, and the order the
 * reads and writes actually happened in.
 *
 * One process, one `index.js`, one store — so the store is reset between cases rather than rebuilt.
 */

// ─── The store, installed before index.js is loaded ─────────────────────────────────────────────

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stubModule = new Module(adminPath, null);
stubModule.filename = adminPath;
stubModule.loaded = true;
stubModule.exports = admin;
require.cache[adminPath] = stubModule;

// `index.js` calls admin.initializeApp() and admin.firestore() while it loads, so this require has
// to come after the line above and nothing may require firebase-admin before it.
const functions = require("./index.js");

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

// ─── The world ──────────────────────────────────────────────────────────────────────────────────

const UID = "player-1";
const LESSON = "lesson-handler";

/** 9, 4, 5, 5, 9 — (100 + 37 + 50 + 50 + 100) / 5, truncated. See `lesson-round-trip.test.js`. */
const CODE_ANSWER = "94559";
const PERCENT = 67;

/**
 * What one attempt costs, written down.
 *
 * Every kind is priced the same so that the price does not depend on which quest the lesson
 * resolves to: no `lessons/{id}` document is seeded, so the quest is unknown, and `activityPrice`
 * charges an unknown kind at the most expensive known rate. One number, whichever way it goes.
 */
const LIFE_COST = 40;

/** Full at five hearts, as `maxLifePoints` counts them. Written down for the same reason. */
const FULL_TANK = 500;

const PUBLISHED = publishLesson(LESSON);
const SERVED = servedQuestions(LESSON);

function world(extra) {
  return {
    [`profiles/${UID}`]: {uid: UID},
    "configs/economy": {
      activityPrices: {
        ORDINARY_LESSON: LIFE_COST,
        ARENA: LIFE_COST,
        THEME_TEST: LIFE_COST,
        FINAL_EXAM: LIFE_COST,
        TOURNAMENT: LIFE_COST,
      },
      // The overspend audit writes its own documents and is not what these cases are about.
      auditEnabled: false,
    },
    [`users/${UID}`]: {
      uid: UID,
      lifePoints: FULL_TANK,
      lifePointsUpdatedAtMs: Date.now(),
      standardHearts: 5,
    },
    [PUBLISHED.keyDocumentPath]: PUBLISHED.keyDocument,
    ...Object.fromEntries(PUBLISHED.documents.map((document) => [
      `questions/${document.id}`,
      {...document, archived: false},
    ])),
    ...(extra || {}),
  };
}

function baseBody(overrides) {
  const body = {
    userId: UID,
    attemptId: "attempt-1",
    catalogId: "catalog-1",
    questId: "quest-1",
    sectionId: "section-1",
    themeId: "theme-1",
    lessonId: LESSON,
    lessonVersion: 1,
    // Not a tournament shelf, so no leaderboard is recalculated after the transaction.
    sourceShelf: "arena",
    difficulty: "HARD",
    answers: ANSWERS,
    completedAtMs: 1_700_000_005_000,
    createdAtMs: 1_700_000_000_000,
    ...(overrides || {}),
  };
  for (const key of Object.keys(body)) if (body[key] === undefined) delete body[key];
  return body;
}

const serverScoredBody = (overrides) => baseBody({served: SERVED, ...(overrides || {})});
const deviceScoredBody = (overrides) =>
  baseBody({codeAnswer: CODE_ANSWER, percentScore: PERCENT, served: SERVED, ...(overrides || {})});

async function submit(attempts, extraWorld) {
  store.reset(world(extraWorld));
  const result = await functions.submitLessonResultEvents.run({auth: {uid: UID}, data: {attempts}});
  return result;
}

/** The stored result event for one attempt, found by the id the handler wrote into it. */
function storedAttempt(attemptId) {
  for (const [path, data] of store.documents) {
    if (path.startsWith("result_events") && data.attemptId === attemptId) return {path, data};
  }
  return null;
}

// ═══ The switch is actually thrown ══════════════════════════════════════════════════════════════

test("one call, one device-scored and one server-scored attempt, both stored and both paid for", async () => {
  const result = await submit([
    deviceScoredBody({attemptId: "device-1"}),
    serverScoredBody({attemptId: "server-1"}),
  ]);

  assert.strictEqual(result.accepted, 2);

  // Both charged. This is the assertion that fails when the scoring step is deleted: without it
  // `payable` is undefined for every attempt, nothing is charged, and the tank stays full.
  assert.strictEqual(result.lifePoints, FULL_TANK - 2 * LIFE_COST);
  assert.strictEqual(store.documents.get(`users/${UID}`).lifePoints, FULL_TANK - 2 * LIFE_COST);

  const device = storedAttempt("device-1");
  const server = storedAttempt("server-1");
  assert.ok(device && server, "an attempt was not stored");
  assert.strictEqual(device.data.lifeCharged, true);
  assert.strictEqual(server.data.lifeCharged, true);

  // The server scored the hard attempt from the stored keys, and stored the digits it produced as
  // if the device had sent them. The device's own `score: 9` on every answer row is not what came
  // out, which is the whole reason the handler asks the scorer.
  assert.strictEqual(server.data.codeAnswer, CODE_ANSWER);
  assert.strictEqual(server.data.percentScore, PERCENT);
  assert.strictEqual(server.data.scoringAuthority, "server");
  assert.strictEqual(server.data.chargeClaims, ".....");

  // The device-scored one is stored exactly as it was sent, and judged on its own claims.
  assert.strictEqual(device.data.codeAnswer, CODE_ANSWER);
  assert.strictEqual(device.data.percentScore, PERCENT);
  assert.strictEqual(device.data.scoringAuthority, "client");
  assert.strictEqual(device.data.scoreVerified, true);
  assert.strictEqual(device.data.expectedPercentScore, PERCENT);
});

test("the document handed to transaction.set is the one the schema says", async () => {
  await submit([serverScoredBody({attemptId: "server-1"})]);
  const write = store.writes.find((entry) => entry.path.startsWith("result_events"));
  assert.ok(write, "no result event was written");
  assert.strictEqual(write.merge, true);

  assert.deepStrictEqual(Object.keys(write.data).slice().sort(), [
    "answers",
    "attemptId",
    "activityKind",
    "catalogId",
    "chargeClaims",
    "chargeClaimsUnpaid",
    "codeAnswer",
    "completedAtMs",
    "createdAtMs",
    "declaredSourceShelf",
    "difficulty",
    "lessonContentKey",
    "lessonId",
    "lessonVersion",
    "lifeCharged",
    "lifeCost",
    "ownerUid",
    "percentScore",
    "plasmaChargesPaid",
    "questContentKey",
    "questId",
    "receivedAtMs",
    "resolvedQuestId",
    "schemaVersion",
    "scope",
    "scoringAuthority",
    "sectionId",
    "served",
    "settledCodeAnswer",
    "settledPercentScore",
    "sourceShelf",
    "standardChargesPaid",
    "themeId",
    "userId",
  ].sort());

  // Nothing the handler decides by reaches the document.
  for (const field of ["payable", "paymentRule", "servedVerified"]) {
    assert.ok(!(field in write.data), `${field} was written to result_events`);
  }
  // And `served` does, because it is the only record of which question sat at which position.
  assert.deepStrictEqual(write.data.served, SERVED);
});

test("every read this call needs happens before its first write", async () => {
  // Firestore refuses a read after the first write of a transaction, and the pool, the keys and the
  // replay check are all reads. Asserted on a run rather than on where a line sits in a file.
  await submit([
    deviceScoredBody({attemptId: "device-1"}),
    serverScoredBody({attemptId: "server-1"}),
  ]);
  assert.deepStrictEqual(store.readsAfterFirstWrite(), []);

  // And the reads that made the scoring possible really were issued.
  const reads = store.operations.filter((operation) => operation.kind === "read").map((o) => o.path);
  assert.ok(reads.includes(PUBLISHED.keyDocumentPath), `keys not read: ${reads.join(", ")}`);
  assert.ok(reads.includes(`questions?lessonId==${LESSON}`), "questions not read");
  // One question query for the whole batch, not one for the reward and one for the scorer.
  assert.strictEqual(reads.filter((path) => path === `questions?lessonId==${LESSON}`).length, 1);
});

test("an attempt the server could not score is stored, and costs the player nothing", async () => {
  // The lesson's questions are gone. Every served position is a filler, which used to score as a
  // fully answered run of '1's — charged, at zero percent, for a lesson the server failed to read.
  const withoutQuestions = {};
  store.reset(world());
  for (const path of [...store.documents.keys()]) {
    if (!path.startsWith("questions/")) withoutQuestions[path] = store.documents.get(path);
  }
  store.reset(withoutQuestions);
  const result = await functions.submitLessonResultEvents.run({
    auth: {uid: UID},
    data: {attempts: [serverScoredBody({attemptId: "server-1"})]},
  });

  assert.strictEqual(result.accepted, 1);
  assert.strictEqual(result.lifePoints, FULL_TANK, "the player was charged for our own gap");
  assert.deepStrictEqual(result.reward, {skillPoints: 0, nolics: 0});

  const stored = storedAttempt("server-1");
  assert.ok(stored, "the event was not kept");
  assert.strictEqual(stored.data.lifeCharged, false);
  assert.strictEqual(stored.data.codeAnswer, "");
  assert.strictEqual(stored.data.percentScore, 0);
});

test("a redelivered server-scored attempt keeps the score it was paid on", async () => {
  // The queue redelivers, and the second delivery would otherwise be re-scored against today's
  // questions. Here the lesson has been republished in between — one question withdrawn — so a
  // re-score would fall to 94159 over a percent the player was already paid at.
  await submit([serverScoredBody({attemptId: "server-1"})]);
  const first = storedAttempt("server-1");
  assert.strictEqual(first.data.codeAnswer, CODE_ANSWER);
  assert.strictEqual(first.data.lifeCharged, true);
  const chargedTo = store.documents.get(`users/${UID}`).lifePoints;

  // Same store, questions changed underneath, same body again.
  store.documents.delete("questions/q-order");
  store.operations = [];
  store.writes = [];
  await functions.submitLessonResultEvents.run({
    auth: {uid: UID},
    data: {attempts: [serverScoredBody({attemptId: "server-1"})]},
  });

  const second = storedAttempt("server-1");
  assert.strictEqual(second.data.codeAnswer, CODE_ANSWER, "the replay was re-scored over its score");
  assert.strictEqual(second.data.percentScore, PERCENT);
  assert.strictEqual(second.data.lifeCharged, true, "the replay rewrote whether it had been paid");
  assert.strictEqual(store.documents.get(`users/${UID}`).lifePoints, chargedTo, "charged twice");
});

test("a replay the advisory read could not check is still not re-scored over", async () => {
  // The pre-transaction check is advisory and can fail — a deadline, a permission blip — and then
  // the attempt is scored afresh exactly as it was before that read existed. What decides is the
  // transaction's own snapshot, and the write keeps the score already on file rather than the one
  // just computed. Without that second guard this case stores 57 over a 67 the player was paid.
  await submit([serverScoredBody({attemptId: "server-1"})]);
  const event = storedAttempt("server-1");
  assert.strictEqual(event.data.percentScore, PERCENT);

  // The lesson loses a question, so a fresh score would be 94159 at 57 percent…
  store.documents.delete("questions/q-order");
  // …and the advisory read of this event fails, once, so nothing stops the handler computing it.
  let seen = 0;
  store.fail = (kind, path) => {
    if (kind === "read" && path === event.path && seen++ === 0) return "4 DEADLINE_EXCEEDED";
    return null;
  };
  await functions.submitLessonResultEvents.run({
    auth: {uid: UID},
    data: {attempts: [serverScoredBody({attemptId: "server-1"})]},
  });
  store.fail = null;

  const after = storedAttempt("server-1");
  assert.strictEqual(after.data.codeAnswer, CODE_ANSWER, "a replay was re-scored over its score");
  assert.strictEqual(after.data.percentScore, PERCENT);
  assert.notStrictEqual(PERCENT, 57, "the two scores are the same, so this case proves nothing");
});

test("the replay guard is scoped to the attempts the server scores", async () => {
  // Deliberately not applied to a device-scored replay. There the digits come from the body, and a
  // resubmission of the same attemptId overwrites the stored ones — which is exactly what happened
  // before this slice, and changing it would change what a device-scored attempt is stored as. It
  // is not free money either way: `isNew` is false, so nothing is charged and nothing is paid.
  await submit([deviceScoredBody({attemptId: "device-1"})]);
  assert.strictEqual(storedAttempt("device-1").data.percentScore, PERCENT);
  const chargedTo = store.documents.get(`users/${UID}`).lifePoints;

  await functions.submitLessonResultEvents.run({
    auth: {uid: UID},
    data: {attempts: [deviceScoredBody({attemptId: "device-1", codeAnswer: "99999", percentScore: 100})]},
  });

  const after = storedAttempt("device-1");
  assert.strictEqual(after.data.codeAnswer, "99999", "today's device-scored replay stopped writing");
  assert.strictEqual(after.data.percentScore, 100);
  assert.strictEqual(after.data.lifeCharged, true, "the replay rewrote whether the first was paid");
  assert.strictEqual(store.documents.get(`users/${UID}`).lifePoints, chargedTo, "charged twice");
});

test("a device-scored attempt whose served list disagrees is kept, marked and not charged", async () => {
  const result = await submit([deviceScoredBody({attemptId: "device-1", served: SERVED.slice(0, 4)})]);

  assert.strictEqual(result.accepted, 1);
  assert.strictEqual(result.lifePoints, FULL_TANK, "a refused attempt was charged for");
  const stored = storedAttempt("device-1");
  assert.strictEqual(stored.data.lifeCharged, false);
  assert.strictEqual(stored.data.scoreVerified, true, "the percent still follows from the digits");
  assert.strictEqual(stored.data.codeAnswer, CODE_ANSWER, "the event was not kept for analysis");
  assert.strictEqual(stored.data.served.length, 4, "the disputed list was not kept");
});

test("an empty tank stores the attempt and records that it was not charged", async () => {
  store.reset(world({[`users/${UID}`]: {
    uid: UID,
    lifePoints: 0,
    lifePointsUpdatedAtMs: Date.now(),
    standardHearts: 5,
  }}));
  const result = await functions.submitLessonResultEvents.run({
    auth: {uid: UID},
    data: {attempts: [serverScoredBody({attemptId: "server-1"})]},
  });

  assert.strictEqual(result.accepted, 1);
  const stored = storedAttempt("server-1");
  assert.strictEqual(stored.data.lifeCharged, false);
  // Scored all the same: offline-first, so the attempt is real even when it cannot be paid for.
  assert.strictEqual(stored.data.codeAnswer, CODE_ANSWER);
  assert.strictEqual(stored.data.percentScore, PERCENT);
});

test("one attempt on a lesson nobody can read leaves the rest of the batch alone", async () => {
  const result = await submit([
    deviceScoredBody({attemptId: "device-1"}),
    serverScoredBody({attemptId: "server-lost", lessonId: "lesson-that-is-not-there"}),
    serverScoredBody({attemptId: "server-1"}),
  ]);

  assert.strictEqual(result.accepted, 3);
  // Two charged, not three and not none.
  assert.strictEqual(result.lifePoints, FULL_TANK - 2 * LIFE_COST);
  assert.strictEqual(storedAttempt("device-1").data.lifeCharged, true);
  assert.strictEqual(storedAttempt("server-1").data.lifeCharged, true);
  assert.strictEqual(storedAttempt("server-lost").data.lifeCharged, false);
  assert.strictEqual(storedAttempt("server-lost").data.codeAnswer, "");
});

test("the charge is one whole activity price, and the balance is written back", async () => {
  // The number, not just its direction: a gate that charged the wrong amount would satisfy every
  // "was it charged" assertion above.
  await submit([serverScoredBody({attemptId: "server-1"})]);
  const user = store.documents.get(`users/${UID}`);
  assert.strictEqual(FULL_TANK - user.lifePoints, LIFE_COST);
  assert.strictEqual(LIFE_COST % POINTS_PER_CHARGE !== 0 || LIFE_COST > 0, true);
  assert.strictEqual(storedAttempt("server-1").data.lifeCost, LIFE_COST);
  // The best-so-far the reward is paid on improvement against.
  assert.strictEqual(user.lessonBest[`${LESSON}:HARD`], PERCENT);
});

// ────────────────────────────────────────────────────────────────────────────────────────────────

async function run() {
  let failures = 0;
  for (const [name, fn] of SUITE) {
    try {
      await fn();
    } catch (error) {
      failures += 1;
      console.error(`FAIL ${name}`);
      console.error(`  ${error.stack}`);
    }
  }
  if (failures > 0) {
    console.error(`attempt-handler.test.js: ${failures} of ${SUITE.length} cases failed`);
    process.exitCode = 1;
  } else {
    console.log(`attempt-handler.test.js OK (${SUITE.length} cases)`);
  }
}

run();
