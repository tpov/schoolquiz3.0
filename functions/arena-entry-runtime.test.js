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
    "lesson_placements/lesson": {lessonId: "lesson", shelf: "arena", authorUid: "author", contentVersion: 1},
    ...Object.fromEntries(["EASY", "HARD"].flatMap((difficulty) => [0, 1].map((n) => [`questions/${difficulty}-${n}`,
      {lessonId: "lesson", version: 1, difficulty, payload: JSON.stringify({...SINGLE, difficulty})}]))),
  };
}
function eligiblePriceLesson(id, ratingSum, ratingCount, ratingVersion = 1) {
  return {
    [`catalogs/catalog-${id}`]: {questType: "REGULAR"},
    [`quests/quest-${id}`]: {catalogId: `catalog-${id}`, authorUid: `author-${id}`, visibleOn: ["archive"]},
    [`sections/section-${id}`]: {questId: `quest-${id}`},
    [`themes/theme-${id}`]: {sectionId: `section-${id}`},
    [`lessons/${id}`]: {themeId: `theme-${id}`, version: 1, reviewSubmissionId: `submission-${id}`,
      reviewApprovedAtMs: 1},
    [`lesson_placements/${id}`]: {lessonId: id, shelf: "arena", authorUid: `author-${id}`, contentVersion: 1},
    [`lesson_ratings/${id}`]: {ratingSum, ratingCount, ratingVersion},
  };
}
function putAll(rows) { for (const [path, value] of Object.entries(rows)) store.documents.set(path, value); }
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

test("ticket terminal and result receipt share one canonical settlement timestamp", async () => {
  store.reset(world());
  const ticket = await issue("canonical-settlement-time");
  const payload = completion(ticket);
  const originalNow = Date.now;
  let clock = 1_800_000_000_000;
  Date.now = () => clock++;
  try {
    const result = await submit(payload);
    const terminal = store.documents.get(`arena_entry_tickets/${ticket.ticketId}`).terminal;
    const receipt = store.documents.get(eventPath(ticket.attemptId)).settlementReceipt;
    assert.equal(terminal.settledAtMs, receipt.settledAtMs);
    assert.equal(receipt.settledAtMs, result.receipts[0].settledAtMs);
  } finally {
    Date.now = originalNow;
  }
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

test("server-selected random is free; missing review approval refuses before reserving", async () => {
  store.reset(world());
  const ticket = await issue("random", {mode: "RANDOM", lessonId: null});
  assert.equal(ticket.priceNolics, 0); assert.equal(ticket.lessonId, "lesson");
  // Одобрение живёт в документе урока (4.19): снимаем его — записей ревьюеров больше нет.
  store.documents.set("lessons/lesson",
    {...store.documents.get("lessons/lesson"), reviewApprovedAtMs: null});
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("no-review"), {code: "failed-precondition"});
  assert.deepEqual(structuredClone([...store.documents]), before);
});

test("stale placement author/version rejects selected and random preparation before any hold", async () => {
  for (const fields of [{authorUid: "other"}, {contentVersion: 2}]) {
    store.reset(world());
    store.documents.set("lesson_placements/lesson", {...store.documents.get("lesson_placements/lesson"), ...fields});
    const before = structuredClone([...store.documents]);
    await assert.rejects(issue(`stale-selected-${fields.contentVersion || "author"}`), {code: "failed-precondition"});
    await assert.rejects(issue(`stale-random-${fields.contentVersion || "author"}`, {mode: "RANDOM", lessonId: null}),
      {code: "failed-precondition"});
    assert.deepEqual(structuredClone([...store.documents]), before);
  }
});

test("corrupt eligible aggregate is unavailable and never reaches a wallet write", async () => {
  store.reset(world());
  store.documents.set("lesson_ratings/lesson", {ratingSum: 4, ratingCount: 1, ratingVersion: 1});
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("corrupt-aggregate"),
    (error) => error.code === "unavailable" && error.details.reason === "UNAVAILABLE");
  assert.deepEqual(structuredClone([...store.documents]), before);
});

test("preparation reuses the callback-local economy snapshot for its regeneration plan", async () => {
  for (const [mode, patch] of [["SELECTED", {}], ["RANDOM", {mode: "RANDOM", lessonId: null}]]) {
    store.reset(world());
    await issue(`one-economy-read-${mode}`, patch);
    assert.equal(store.operations.filter((operation) => operation.path === "configs/economy").length, 1);
  }
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
  putAll(eligiblePriceLesson("lower", 20, 20));
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

test("a full eligible index stays inside the read budget; a larger index refuses before writes", async () => {
  // 4.19: контекст урока перестал читать задания и записи ревьюеров — полный индекс размещений
  // (1000, продуктовый предел) укладывается в 12000 чтений. Переполнение продолжает отказывать
  // до всякой записи: индекс больше предела размещений отказывает по границе выборки.
  const full = {};
  for (let n = 0; n < 999; n++) Object.assign(full, eligiblePriceLesson(`capacity-${n}`, 0, 0));
  store.reset({...world(), ...full});
  const ticket = await issue("full-index");
  assert.equal(ticket.lessonId, "lesson");

  const over = {};
  for (let n = 0; n < 1001; n++) Object.assign(over, eligiblePriceLesson(`over-${n}`, 0, 0));
  for (const mode of ["SELECTED", "RANDOM"]) {
    store.reset({...world(), ...over});
    const before = structuredClone([...store.documents]);
    const payload = mode === "SELECTED" ? {
      requestId: `capacity-${mode}`, mode, lessonId: "lesson", difficulty: "EASY",
    } :
      {requestId: `capacity-${mode}`, mode, lessonId: null, difficulty: "EASY"};
    await assert.rejects(call("prepareArenaEntry", payload), error =>
      error.code === "unavailable" && error.details.reason === "ARENA_INDEX_REBUILD_REQUIRED");
    assert.deepEqual(structuredClone([...store.documents]), before);
  }
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
    putAll(eligiblePriceLesson("lower", 20, 20));
    store.documents.set("lesson_ratings/lesson", {ratingSum: 60, ratingCount: 20, ratingVersion: 7});
  };
  const ticket = await issue("repriced-on-retry");
  assert.equal(ticket.priceNolics, 100);
  assert.equal(ticket.ratingVersion, 7);
  assert.equal(store.documents.get("users/player").reservedNolics, 100);
  assert.equal(store.documents.get("users/player").nolics, 900);
  assert.equal(store.writesTo("arena_entry_tickets/").length, 1);
  assert.equal(store.operations.filter((operation) => operation.path === "configs/economy").length, 2,
    "each optimistic callback has its own reader; neither callback repeats the economy read");
});

function repriceLessonToMiddleLevel() {
  putAll({...eligiblePriceLesson("lower", 20, 20), ...eligiblePriceLesson("upper", 60, 20)});
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

function assertNoWrites(before) {
  assert.deepEqual(structuredClone([...store.documents]), before);
  assert.deepEqual(store.writes, []);
  assert.equal(store.operations.some((operation) => operation.kind === "write"), false);
}
function splitBank(difficulty, source = SINGLE, mutate = (payload) => payload) {
  const {redact} = require("./question-redaction"); const keys = [];
  for (const n of [0, 1]) {
    const id = `${difficulty}-${n}`;
    const split = redact(JSON.stringify({...source, difficulty}), difficulty, {questionId: id, random: () => 0});
    const question = store.documents.get(`questions/${id}`);
    store.documents.set(`questions/${id}`, {...question, payload: mutate(split.publicPayload)});
    keys.push({questionId: id, key: split.key});
  }
  store.documents.set("question_keys/lesson", {version: 1, lessonId: "lesson", publicHalfRedacted: true, keys});
}
test("real missing-id source stays untouched; actual EASY producer stamps trusted identity before hold", async () => {
  store.reset(world());
  const originals = [0, 1].map((n) => structuredClone(store.documents.get(`questions/EASY-${n}`)));
  for (const original of originals) assert.equal(Object.hasOwn(JSON.parse(original.payload), "id"), false);
  const ticket = await issue("actual-seeded-shape");
  for (const row of ticket.questions) {
    const content = JSON.parse(row.publicQuestion.payload);
    assert.equal(content.id, row.questionId); assert.equal(content.difficulty, "EASY");
    assert.equal(content.correctOptionId, "b"); assert.equal(content.info, SINGLE.info);
  }
  assert.deepEqual([0, 1].map((n) => store.documents.get(`questions/EASY-${n}`)), originals);
  assert.equal((await submit(completion(ticket))).receipts[0].percentScore, 100);
});
for (const difficulty of ["EASY", "HARD"]) {
  for (const patch of [{id: "foreign"}, {id: null}, {difficulty: difficulty === "EASY" ? "HARD" : "EASY"}]) {
    test(`${difficulty} embedded metadata ${JSON.stringify(patch)} rejects before wallet/checkpoint/quota writes`, async () => {
      store.reset(world());
      for (const n of [0, 1]) {
        const path = `questions/${difficulty}-${n}`; const row = store.documents.get(path);
        store.documents.set(path, {...row, payload: JSON.stringify({...SINGLE, difficulty, ...patch})});
      }
      // Even an overdue premium cursor must not be advanced for invalid content.
      store.documents.get("users/player").lifePointsUpdatedAtMs = 1;
      const before = structuredClone([...store.documents]);
      await assert.rejects(issue("conflict", {difficulty}), error => error.details?.reason === "INVALID_QUESTION");
      assertNoWrites(before);
    });
  }
  test(`${difficulty} Survey missing modern metadata is normalized and participation semantics retained`, async () => {
    const {SURVEY} = require("./_question-fixtures"); store.reset(world());
    for (const n of [0, 1]) {
      const path = `questions/${difficulty}-${n}`; const row = store.documents.get(path);
      store.documents.set(path, {...row, payload: JSON.stringify(SURVEY)});
    }
    const ticket = await issue("survey-metadata", {difficulty});
    for (const row of ticket.questions) {
      const content = JSON.parse(row.publicQuestion.payload);
      assert.equal(content.id, row.questionId); assert.equal(content.difficulty, difficulty);
      assert.equal(content.type, "Survey"); assert.equal(content.allowMultiple, false);
    }
  });
}
for (const source of Object.values(require("./_question-fixtures")).filter((value) => value && typeof value === "object" && value.type !== "Survey")) {
  test(`HARD ${source.type} ALREADY_REDACTED nested grading field rejects before every write`, async () => {
    store.reset(world());
    splitBank("HARD", source, (payload) => {
      const parsed = JSON.parse(payload); const rows = parsed.options || parsed.items || parsed.candidates;
      rows[0].grading = {answer: "secret"}; return JSON.stringify(parsed);
    });
    const before = structuredClone([...store.documents]);
    await assert.rejects(issue("nested-leak", {difficulty: "HARD"}), error => error.details?.reason === "INVALID_QUESTION");
    assertNoWrites(before);
  });
  test(`HARD ${source.type} valid ALREADY_REDACTED retains exact public bytes and IDs`, async () => {
    store.reset(world()); splitBank("HARD", source);
    const ticket = await issue("valid-frozen", {difficulty: "HARD"});
    for (const row of ticket.questions) assert.equal(row.publicQuestion.payload, store.documents.get(`questions/${row.questionId}`).payload);
  });
}
test("EASY restored redacted payload receives missing metadata from its real document, not key claims", async () => {
  store.reset(world()); splitBank("EASY");
  const ticket = await issue("easy-redacted-metadata");
  for (const row of ticket.questions) {
    const content = JSON.parse(row.publicQuestion.payload);
    assert.equal(content.id, row.questionId); assert.equal(content.difficulty, "EASY");
  }
  assert.equal((await submit(completion(ticket))).receipts[0].percentScore, 100);
});
test("HARD duplicate raw key cannot hide secret bytes before a clean last-value public schema", async () => {
  store.reset(world()); splitBank("HARD", SINGLE, (payload) => `{"options":[{"answer":"b"}],${payload.slice(1)}`);
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("duplicate-key", {difficulty: "HARD"}), error => error.details?.reason === "INVALID_QUESTION");
  assertNoWrites(before);
});
test("random HARD skips unsafe banks without consuming quota, charge hold or premium checkpoint", async () => {
  store.reset(world()); splitBank("HARD", SINGLE, (payload) => JSON.stringify({...JSON.parse(payload), info: "private answer"}));
  store.documents.get("users/player").lifePointsUpdatedAtMs = 1;
  const before = structuredClone([...store.documents]);
  await assert.rejects(issue("random-unsafe", {mode: "RANDOM", lessonId: null, difficulty: "HARD"}),
    error => error.details?.reason === "NO_ELIGIBLE_ARENA_LESSON");
  assertNoWrites(before);
});
test("old unsafe HARD issue replay and recovery fail closed; immutable hold can still be cancelled", async () => {
  store.reset(world()); const ticket = await issue("old-unsafe", {difficulty: "HARD"});
  const stored = store.documents.get(`arena_entry_tickets/${ticket.ticketId}`);
  const row = stored.questions[0]; const parsed = JSON.parse(row.publicQuestion.payload);
  row.publicQuestion.payload = JSON.stringify({...parsed, correctOptionId: "b"});
  const before = structuredClone([...store.documents]); const writes = store.writes.length;
  await assert.rejects(issue("old-unsafe", {difficulty: "HARD"}), error => error.details?.reason === "UNAVAILABLE");
  await assert.rejects(call("fetchArenaEntryTicket", {ticketId: ticket.ticketId}), {code: "unavailable"});
  assert.deepEqual(structuredClone([...store.documents]), before); assert.equal(store.writes.length, writes);
  const cancelled = await call("cancelArenaEntry", {ticketId: ticket.ticketId, requestId: "cancel-old-unsafe"});
  assert.equal(cancelled.status, "CANCELLED");
  assert.equal(store.documents.get("users/player").reservedNolics, 0);
  assert.equal(store.documents.get("users/player").reservedLifePoints, 0);
  assert.equal(store.documents.get("users/player").nolics, 1000);
  assert.equal(store.documents.get(`arena_entry_tickets/${ticket.ticketId}`).questions[0].publicQuestion.payload, row.publicQuestion.payload);
});
