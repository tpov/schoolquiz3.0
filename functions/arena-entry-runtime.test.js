"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {eventPath} = require("./arena-entry-runtime");
const {SINGLE} = require("./_question-fixtures");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const uid = "player";
function world() {
  const now = Date.now();
  return {
    "users/player": {uid, standardHearts: 10, goldHearts: 3, lifePoints: 1000, plasmaPoints: 300,
      lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now, nolics: 1000, pointsNolics: 1000},
    "users/author": {nolics: 0, pointsNolics: 0}, "profiles/player": {uid},
    "profiles/curator": {adminLevel: 100},
    "catalogs/catalog": {questType: "REGULAR"},
    "quests/quest": {catalogId: "catalog", authorUid: "author", visibleOn: ["archive"]},
    "sections/section": {questId: "quest"}, "themes/theme": {sectionId: "section"},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: now - 1000},
    "lesson_placements/lesson": {lessonId: "lesson", shelf: "arena", authorUid: "author"},
    "admin/review/lessons/lesson": {ownerUid: "author", questId: "quest", submissionId: "submission",
      status: "PUBLISHED", sourceLanguages: ["en"]},
    "admin/review/lessons/lesson/reviews/tester": {kind: "TESTING", reviewerUid: "tester",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
    "admin/review/lessons/lesson/reviews/logic": {kind: "LOGIC", reviewerUid: "reviewer",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
    ...Object.fromEntries(["EASY", "HARD"].flatMap((difficulty) => [0, 1].map((n) => [`questions/${difficulty}-${n}`,
      {lessonId: "lesson", version: 1, difficulty, payload: JSON.stringify({...SINGLE, difficulty})}]))),
  };
}
function call(name, data, owner = uid) { return functions[name].run({auth: {uid: owner}, data}); }
async function issue(requestId, patch = {}) {
  return (await call("prepareArenaEntry", {requestId, mode: "SELECTED", lessonId: "lesson", difficulty: "EASY", ...patch})).ticket;
}
function completion(ticket, patch = {}) {
  return {ticketId: ticket.ticketId, attemptId: ticket.attemptId, ownerUid: uid, lessonId: ticket.lessonId,
    lessonVersion: ticket.lessonVersion, difficulty: ticket.difficulty, completionKind: "COMPLETED",
    answers: ticket.questions.map((q, codeAnswerIndex) => ({questionId: q.questionId, codeAnswerIndex,
      answerPayload: JSON.stringify({type: "single-choice", selected: "b"}), answeredAtMs: Date.now(), durationMs: 1})), ...patch};
}
function submit(payload, owner = uid) { return call("submitLessonResultEvents", {attempts: [payload]}, owner); }

test("prepare and cancel bind the new client's expected Auth owner before any financial effects", async () => {
  store.reset(world());
  const initial = structuredClone([...store.documents]);
  for (const expectedOwnerUid of [null, "", "invalid/path", 7]) {
    await assert.rejects(issue("malformed-owner", {expectedOwnerUid}), {code: "invalid-argument"});
    assert.deepEqual(structuredClone([...store.documents]), initial);
  }
  await assert.rejects(issue("switched-owner", {expectedOwnerUid: "author"}), error =>
    error.code === "permission-denied" && error.details.reason === "ACCOUNT_CHANGED");
  assert.deepEqual(structuredClone([...store.documents]), initial);
  const ticket = await issue("bound-owner", {expectedOwnerUid: uid, maxPriceNolics: 1});
  const reserved = structuredClone([...store.documents]);
  await assert.rejects(call("cancelArenaEntry", {ticketId: ticket.ticketId, requestId: "bound-cancel",
    expectedOwnerUid: "author"}), error => error.code === "permission-denied" && error.details.reason === "ACCOUNT_CHANGED");
  assert.deepEqual(structuredClone([...store.documents]), reserved);
  assert.equal((await call("cancelArenaEntry", {ticketId: ticket.ticketId, requestId: "bound-cancel",
    expectedOwnerUid: uid})).status, "CANCELLED");
});

test("real endpoints reserve trusted price/charges and settle from the frozen bank after editing and retirement", async () => {
  store.reset(world());
  const ticket = await issue("frozen", {priceNolics: 0, lifeCost: 0, uid: "author"});
  assert.equal(ticket.uid, uid); assert.equal(ticket.lifeCost, 50); assert.equal(ticket.priceNolics, 1);
  assert.equal(store.documents.get("users/player").reservedLifePoints, 50);
  assert.equal(store.documents.get("users/player").lifePoints, 950);
  assert.equal(ticket.questions[0].gradingKey, undefined); assert.equal(ticket.bundleDigest, undefined);
  assert.equal(JSON.parse(ticket.questions[0].publicQuestion.payload).correctOptionId, "b");
  store.documents.set("lessons/lesson", {themeId: "theme", version: 9});
  store.documents.set("lesson_placements/lesson", {shelf: "retired"});
  for (const q of ticket.questions) store.documents.delete(`questions/${q.questionId}`);
  const payload = completion(ticket, {codeAnswer: "11", percentScore: 0, catalogId: "forged"});
  const result = await submit(payload);
  const receipt = result.receipts[0];
  assert.equal(receipt.status, "SETTLED"); assert.equal(receipt.completed, true); assert.equal(receipt.percentScore, 100);
  assert.equal(receipt.lifePointsSpent, 50); assert.equal(receipt.nolicsSpent, 1);
  const user = store.documents.get("users/player");
  assert.equal(user.lifePoints, 950); assert.equal(user.reservedLifePoints, 0); assert.equal(user.reservedNolics, 0);
  assert.equal(user.nolics, 999 + receipt.reward.nolics);
  assert.equal(store.documents.get(eventPath(ticket.attemptId)).catalogId, "catalog");
  const state = structuredClone([...store.documents]);
  assert.deepEqual(await submit(payload), result);
  assert.deepEqual(structuredClone([...store.documents]), state);
  await assert.rejects(submit({...payload, answers: []}), {code: "already-exists"});
});

test("five one-nolic accepted runs conserve author four, sink one, with separate independent holds", async () => {
  store.reset(world());
  const tickets = [];
  for (let n = 0; n < 5; n++) tickets.push(await issue(`fifth-${n}`));
  assert.equal(store.documents.get("users/player").reservedNolics, 5);
  for (const ticket of tickets) await submit(completion(ticket, {answers: []}));
  assert.equal(store.documents.get("users/author").nolics, 4);
  const royalty = store.documents.get("arena_royalty_accounts/author");
  assert.equal(royalty.totalPaymentsNolics, 5); assert.equal(royalty.totalSinkNolics, 1);
  assert.equal(royalty.reservedNolics, 0);
  assert.equal(store.documents.get("users/player").nolics, 995);
});

test("HARD plasma is additional to the held activity cost; unpaid skip never drops the denominator", async () => {
  for (const plasma of [0, 100]) {
    store.reset(world());
    store.documents.set("users/player", {...store.documents.get("users/player"), plasmaPoints: plasma});
    const ticket = await issue(`plasma-${plasma}`, {difficulty: "HARD"});
    assert.equal(ticket.questions[0].gradingKey, undefined);
    assert.equal(JSON.parse(ticket.questions[0].publicQuestion.payload).correctOptionId, undefined);
    const payload = completion(ticket);
    payload.answers = payload.answers.slice(1); payload.chargeClaims = "P.";
    const receipt = (await submit(payload)).receipts[0];
    assert.equal(receipt.percentScore, plasma ? 100 : 50);
    assert.equal(receipt.codeAnswer, plasma ? "99" : "19");
    assert.equal(receipt.lifePointsSpent, 50); assert.equal(receipt.plasmaPointsSpent, plasma);
    assert.equal(store.documents.get("users/player").plasmaPoints, 0);
  }
});

test("cancel and settle are mutually exclusive; another owner or attempt cannot spend the hold", async () => {
  store.reset(world());
  const ticket = await issue("cancelled");
  await assert.rejects(submit(completion(ticket), "intruder"));
  await assert.rejects(submit(completion(ticket, {attemptId: "other"})), {code: "already-exists"});
  await call("cancelArenaEntry", {ticketId: ticket.ticketId, requestId: "cancel"});
  await assert.rejects(submit(completion(ticket)), {code: "failed-precondition"});
  assert.equal(store.documents.get("users/player").nolics, 1000);
  assert.equal(store.documents.get("users/author").nolics, 0);
  assert.equal(store.documents.has(eventPath(ticket.attemptId)), false);
  const accepted = await issue("accepted");
  const payload = completion(accepted);
  await submit(payload);
  const state = structuredClone(store.documents.get("users/player"));
  const cancellation = await call("cancelArenaEntry", {ticketId: accepted.ticketId, requestId: "too-late"});
  assert.equal(cancellation.status, "SETTLED");
  assert.deepEqual(structuredClone(store.documents.get("users/player")), state);
});

test("failed economic commit rolls back result, ticket, payer, author and ledger; replay can finish", async () => {
  store.reset(world());
  const ticket = await issue("atomic"); const payload = completion(ticket);
  const before = structuredClone([...store.documents]);
  store.fail = (kind, path) => kind === "commit" && path === eventPath(ticket.attemptId) ? "commit lost" : null;
  await assert.rejects(submit(payload), /commit lost/);
  assert.deepEqual(structuredClone([...store.documents]), before);
  store.fail = null;
  assert.equal((await submit(payload)).receipts[0].status, "SETTLED");
});

test("missing or mismatched frozen key remains retryable with its hold, not a fabricated zero score", async () => {
  store.reset(world());
  const ticket = await issue("key", {difficulty: "HARD"});
  const path = `arena_entry_tickets/${ticket.ticketId}`;
  const frozen = structuredClone(store.documents.get(path));
  const broken = structuredClone(frozen); broken.questions[0].gradingKey.version = 999;
  store.documents.set(path, broken);
  const before = structuredClone([...store.documents]);
  const payload = completion(ticket, {answers: []});
  await assert.rejects(submit(payload), {code: "unavailable"});
  assert.deepEqual(structuredClone([...store.documents]), before);
  store.documents.set(path, frozen);
  assert.equal((await submit(payload)).receipts[0].percentScore, 0);
});

test("server-selected random is free; missing independent approval refuses before reserving", async () => {
  store.reset(world());
  const ticket = await issue("random", {mode: "RANDOM", lessonId: null});
  assert.equal(ticket.priceNolics, 0); assert.equal(ticket.lessonId, "lesson");
  store.documents.delete("admin/review/lessons/lesson/reviews/tester");
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("no-review"), {code: "failed-precondition"});
  assert.deepEqual(structuredClone([...store.documents]), before);
});

test("a reviewed Arena lesson may belong to a course kept in the on-demand archive", async () => {
  store.reset(world());
  store.documents.set("quests/quest", {...store.documents.get("quests/quest"), archived: true});
  const ticket = await issue("archive-course");
  assert.equal(ticket.lessonId, "lesson");
  assert.equal((await submit(completion(ticket))).receipts[0].status, "SETTLED");
  assert.equal(store.documents.get("quests/quest").archived, true);
});

test("new Arena attempts cannot bypass ticket payments through the old result endpoint; old history is preserved only", async () => {
  store.reset(world());
  const payload = {attemptId: "without-ticket", userId: uid, lessonId: "lesson", lessonVersion: 1,
    catalogId: "catalog", questId: "quest", sectionId: "section", themeId: "theme", scope: "public",
    difficulty: "EASY", codeAnswer: "99", percentScore: 100, answers: [], completionKind: "COMPLETED",
    served: [0, 1].map((codeAnswerIndex) => ({codeAnswerIndex, questionId: `EASY-${codeAnswerIndex}`}))};
  const before = structuredClone([...store.documents]);
  await assert.rejects(submit(payload), {code: "failed-precondition"});
  assert.deepEqual(structuredClone([...store.documents]), before);
  delete payload.completionKind;
  const result = await submit(payload);
  assert.equal(result.receipts[0].status, "LEGACY_RECORDED");
  assert.equal(result.receipts[0].completed, false);
  assert.equal(result.receipts[0].percentScore, null);
  assert.equal(result.receipts[0].lifePointsSpent, 0);
  assert.equal(result.reward.nolics, 0);
  assert.equal(store.documents.get("users/player").nolics, 1000);
});

test("selected top level costs 100 and price remains frozen after ratings change", async () => {
  store.reset(world());
  store.documents.set("lesson_placements/lower", {lessonId: "lower", shelf: "arena"});
  store.documents.set("lesson_ratings/lower", {ratingSum: 20, ratingCount: 20, ratingVersion: 1});
  store.documents.set("lesson_ratings/lesson", {ratingSum: 60, ratingCount: 20, ratingVersion: 7});
  const ticket = await issue("top-price");
  assert.equal(ticket.priceNolics, 100); assert.equal(ticket.ratingVersion, 7);
  assert.equal(store.documents.get("users/player").reservedNolics, 100);
  store.documents.set("lesson_ratings/lesson", {ratingSum: 20, ratingCount: 20, ratingVersion: 8});
  const receipt = (await submit(completion(ticket, {answers: []}))).receipts[0];
  assert.equal(receipt.nolicsSpent, 100);
  assert.equal(store.documents.get("users/author").nolics, 80);
  assert.equal(store.documents.get("users/player").nolics, 900);
});

test("a retried issue rereads withdrawal on the same Transaction object and creates no hold", async () => {
  store.reset(world());
  store.retryTransactionOnce = () => {
    store.documents.set("lesson_placements/lesson", {lessonId: "lesson", shelf: "retired"});
  };
  await assert.rejects(issue("withdrawn-on-retry"), {code: "failed-precondition"});
  assert.equal(store.documents.get("users/player").nolics, 1000);
  assert.equal(store.documents.get("users/player").lifePoints, 1000);
  assert.equal(store.writesTo("arena_entry_tickets/").length, 0);
});

test("a retried issue freezes the newly committed price, not the abandoned transaction's cache", async () => {
  store.reset(world());
  store.retryTransactionOnce = () => {
    store.documents.set("lesson_placements/lower", {lessonId: "lower", shelf: "arena"});
    store.documents.set("lesson_ratings/lower", {ratingSum: 20, ratingCount: 20, ratingVersion: 1});
    store.documents.set("lesson_ratings/lesson", {ratingSum: 60, ratingCount: 20, ratingVersion: 7});
  };
  const ticket = await issue("repriced-on-retry");
  assert.equal(ticket.priceNolics, 100);
  assert.equal(ticket.ratingVersion, 7);
  assert.equal(store.documents.get("users/player").reservedNolics, 100);
  assert.equal(store.documents.get("users/player").nolics, 900);
  assert.equal(store.writesTo("arena_entry_tickets/").length, 1);
});

function repriceLessonToMiddleLevel() {
  store.documents.set("lesson_placements/lower", {lessonId: "lower", shelf: "arena"});
  store.documents.set("lesson_placements/upper", {lessonId: "upper", shelf: "arena"});
  store.documents.set("lesson_ratings/lower", {ratingSum: 20, ratingCount: 20, ratingVersion: 1});
  store.documents.set("lesson_ratings/upper", {ratingSum: 60, ratingCount: 20, ratingVersion: 1});
  store.documents.set("lesson_ratings/lesson", {ratingSum: 40, ratingCount: 20, ratingVersion: 7});
}

test("a reused Transaction accepts its new authoritative price within the explicitly confirmed ceiling", async () => {
  store.reset(world());
  store.retryTransactionOnce = repriceLessonToMiddleLevel;
  const ticket = await issue("ceiling-retry-within", {maxPriceNolics: 51});
  assert.equal(ticket.priceNolics, 51);
  assert.equal(ticket.ratingVersion, 7);
  assert.equal(store.documents.get("users/player").reservedNolics, 51);
  assert.equal(store.documents.get("users/player").nolics, 949);
  assert.equal(store.documents.get("users/player").reservedLifePoints, 50);
  assert.equal(store.documents.get("users/player").lifePoints, 950);
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith("arena_entry_tickets/")).length, 1);
});

test("a reused Transaction refuses repricing above confirmation without committing its abandoned hold or receipt", async () => {
  store.reset(world());
  let externalState;
  store.retryTransactionOnce = () => {
    repriceLessonToMiddleLevel();
    externalState = structuredClone([...store.documents]);
  };
  await assert.rejects(issue("ceiling-retry-above", {maxPriceNolics: 50}),
    (error) => error.code === "failed-precondition" && error.details.reason === "PRICE_CHANGED");
  assert.deepEqual(structuredClone([...store.documents]), externalState);
  assert.equal([...store.documents.keys()].some((path) => path.startsWith("arena_entry_tickets/")), false);
  assert.equal([...store.documents.keys()].some((path) => path.startsWith("audit/currency_ledger/entries/")), false);
  const ticket = await issue("new-confirmation-after-reprice", {maxPriceNolics: 51});
  assert.equal(ticket.priceNolics, 51);
  assert.equal(store.documents.get("users/player").reservedNolics, 51);
});

test("callable ceiling replay is immutable after rating changes, while no-ceiling legacy replay still works", async () => {
  store.reset(world());
  const confirmed = await issue("confirmed-replay", {maxPriceNolics: 1});
  const legacy = await issue("legacy-replay");
  repriceLessonToMiddleLevel();
  const before = structuredClone([...store.documents]);
  assert.deepEqual(await issue("confirmed-replay", {maxPriceNolics: 1}), confirmed);
  assert.deepEqual(await issue("legacy-replay"), legacy);
  for (const [requestId, patch] of [["confirmed-replay", {maxPriceNolics: 51}], ["confirmed-replay", {}],
    ["legacy-replay", {maxPriceNolics: 1}]]) {
    await assert.rejects(issue(requestId, patch),
      (error) => error.code === "already-exists" && error.details.reason === "REQUEST_REUSE");
  }
  assert.deepEqual(structuredClone([...store.documents]), before);
});

test("callable zero ceiling spends nothing and random never accepts a selected-price confirmation", async () => {
  store.reset(world());
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("zero-confirmation", {maxPriceNolics: 0}),
    (error) => error.code === "failed-precondition" && error.details.reason === "PRICE_CHANGED");
  await assert.rejects(issue("random-with-ceiling", {mode: "RANDOM", lessonId: null, maxPriceNolics: 0}),
    (error) => error.code === "invalid-argument" && error.details.reason === "INVALID_ARGUMENT");
  assert.deepEqual(structuredClone([...store.documents]), before);
});

test("EASY can restore an already split trusted bank and still receives authoritative scoring", async () => {
  const {redact} = require("./question-redaction");
  store.reset(world());
  const keys = [];
  for (const n of [0, 1]) {
    const id = `EASY-${n}`;
    const question = store.documents.get(`questions/${id}`);
    const split = redact(question.payload);
    store.documents.set(`questions/${id}`, {...question, payload: split.publicPayload});
    keys.push({questionId: id, key: split.key});
  }
  store.documents.set("question_keys/lesson", {version: 1, lessonId: "lesson", publicHalfRedacted: true, keys});
  const ticket = await issue("restored-easy");
  assert.equal(JSON.parse(ticket.questions[0].publicQuestion.payload).correctOptionId, "b");
  assert.equal((await submit(completion(ticket))).receipts[0].percentScore, 100);
});
