"use strict";

const assert = require("assert");
const Module = require("module");

const {createFirestoreStub} = require("./_firestore-stub");

/**
 * Серия опросов end-to-end (история 8.2 эпика `epic-social-features`, UX-O7) — поверх настоящего
 * кода. Публикация и голос идут через единственный приёмник мутаций (AD-6/AD-7) над
 * in-memory-хранилищем, как у ready-ACK: ключ рождается из тройки идентичности (§2), повтор
 * известного ключа возвращает сохранённое (§6.1), уникальность участник×вопрос серверная и
 * переживает новый ключ (§3), голос живёт только в опубликованной revision (§6.2).
 */

// ─── The store, installed before index.js is loaded ─────────────────────────────────────────────

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stubModule = new Module(adminPath, null);
stubModule.filename = adminPath;
stubModule.loaded = true;
stubModule.exports = admin;
require.cache[adminPath] = stubModule;

const functions = require("./index.js");

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

// ─── The world ──────────────────────────────────────────────────────────────────────────────────

const AUTHOR = "poll-author";
const VOTER = "poll-voter-1";
const NOW = 1_900_000_000_000;

const SERIES = "series-e2e-1";

const SINGLE_Q = {
  questionIndex: 0,
  text: "Favorite color?",
  mode: "SINGLE",
  options: [
    {optionId: "opt-red", text: "Red"},
    {optionId: "opt-green", text: "Green"},
    {optionId: "opt-blue", text: "Blue"},
  ],
};
const MULTI_Q = {
  questionIndex: 1,
  text: "Which fruits do you like?",
  mode: "MULTIPLE",
  options: [
    {optionId: "f-apple", text: "Apple"},
    {optionId: "f-plum", text: "Plum"},
    {optionId: "f-pear", text: "Pear"},
  ],
};

function draft(title, questions, expectedRevisionId) {
  return {seriesId: SERIES, title, expectedRevisionId, questions};
}

function publishKey(expectedRevisionId) {
  // Как у Kotlin-писателя: ключ из серии и ожидаемой следующей revision.
  return `polls-PUBLISH_SERIES-series-${SERIES}-r${expectedRevisionId + 1}`;
}

function voteKey(seriesId, revisionId, questionIndex) {
  return `polls-SUBMIT_VOTE-series-${seriesId}-r${revisionId}-q${questionIndex}`;
}

async function withFixedClock(atMs, fn) {
  const realNow = Date.now;
  Date.now = () => atMs;
  try {
    return await fn();
  } finally {
    Date.now = realNow;
  }
}

function submit(uid, mutationId, operation, payload) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {mutationId, operation, payload: JSON.stringify(payload)},
  });
}

async function publish(uid, expected, questions, title) {
  const body = draft(title || "Demo poll", questions, expected);
  return withFixedClock(NOW, () =>
    submit(uid, publishKey(expected), "polls.PUBLISH_SERIES", body));
}

function voteDocId(seriesId, revisionId, questionIndex, uid) {
  return `poll_votes/${seriesId}-r${revisionId}-q${questionIndex}-${uid}`;
}

function seriesDoc() {
  return store.documents.get(`poll_series/${SERIES}`);
}

function resetWorld() {
  store.reset({[`profiles/${AUTHOR}`]: {uid: AUTHOR}, [`profiles/${VOTER}`]: {uid: VOTER}});
}

function httpsErrorOf(promise) {
  return promise.then(
    () => null,
    (error) => error,
  );
}

// ─── Publish: единый draft становится опубликованной revision ───────────────────────────────────

test("publish creates the series with revision 1 through the real receiver", async () => {
  resetWorld();
  const result = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(result.replayed, false);
  assert.deepStrictEqual(result.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});

  const stored = seriesDoc();
  assert.ok(stored, "the series document exists");
  assert.strictEqual(stored.authorUid, AUTHOR, "the author is the authenticated uid, not the body");
  assert.strictEqual(stored.revisionId, 1);
  assert.strictEqual(stored.revision.questions[0].mode, "SINGLE");
  assert.strictEqual(stored.revision.questions.length, 2, "no correctness/difficulty fields exist");
  assert.strictEqual(stored.revision.publishedAtMs, NOW);
});

test("the repeated publish key replays the stored revision without running the handler", async () => {
  resetWorld();
  const first = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const replay = await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(replay.replayed, true, "the known key returns the stored result (AD-6)");
  assert.deepStrictEqual(replay.result, first.result);
  assert.strictEqual(seriesDoc().revisionId, 1, "no second revision");
});

test("republishing identical content is a no-op, not a reopened revision", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  // Новое намерение (новый ключ: другой ожидаемый базис), но то же содержание — новая revision
  // переоткрыла бы голосование с нуля, чего §6.2 не делает.
  const repeat = await publish(AUTHOR, 1, [SINGLE_Q, MULTI_Q]);
  assert.strictEqual(repeat.replayed, false);
  assert.deepStrictEqual(repeat.result, {seriesId: SERIES, revisionId: 1, questionCount: 2});
  assert.strictEqual(seriesDoc().revisionId, 1);
  assert.strictEqual(seriesDoc().revision.publishedAtMs, NOW, "the published moment did not move");
});

test("a real content edit bumps the revision; a stale basis refuses by name", async () => {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
  const edited = await withFixedClock(NOW + 1000, () => publish(AUTHOR, 1, [SINGLE_Q]));
  assert.strictEqual(edited.result.revisionId, 2, "the new identity is revision 2 (§6.2)");
  assert.strictEqual(seriesDoc().revision.questions.length, 1);

  const stale = await httpsErrorOf(withFixedClock(NOW + 2000, () =>
    // Ключ-проба (не тот, что вывел бы писатель из ожидаемого базиса): гейт ключей тут молчит,
    // и семантическую сверку базиса держит сам обработчик — поименованный конфликт.
    submit(AUTHOR, `${publishKey(0)}-probe`, "polls.PUBLISH_SERIES", draft("Demo poll", [SINGLE_Q], 0))));
  assert.strictEqual(stale.code, "failed-precondition");
  assert.ok(stale.message.includes("POLL_SERIES_REVISION_CONFLICT"), stale.message);
});

// ─── Vote: один подтверждённый набор на участника на вопрос ─────────────────────────────────────

async function worldWithPublishedSeries() {
  resetWorld();
  await publish(AUTHOR, 0, [SINGLE_Q, MULTI_Q]);
}

test("a confirmed vote is accepted once through the real receiver", async () => {
  await worldWithPublishedSeries();
  const voted = await withFixedClock(NOW + 2000, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW + 1500}));
  assert.strictEqual(voted.replayed, false);
  assert.deepStrictEqual(voted.result, {
    seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], acceptedAtMs: NOW + 2000,
  });

  const doc = store.documents.get(voteDocId(SERIES, 1, 0, VOTER));
  assert.ok(doc, "the vote document exists under the deterministic id");
  assert.strictEqual(doc.participantUid, VOTER);
  assert.deepStrictEqual(doc.selectedOptionIds, ["opt-blue"]);
  assert.strictEqual(doc.clientConfirmedAtMs, NOW + 1500, "the frozen fixation moment rides the body");
  assert.strictEqual(doc.acceptedAtMs, NOW + 2000, "the acceptance moment is the server's");
});

test("the repeated vote key replays the receipt without a second vote", async () => {
  await worldWithPublishedSeries();
  const payload = {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW};
  const first = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE", payload));
  const replay = await withFixedClock(NOW + 9000, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE", payload));
  assert.strictEqual(replay.replayed, true);
  assert.deepStrictEqual(replay.result, first.result);
  assert.ok(store.documents.has(voteDocId(SERIES, 1, 0, VOTER)), "exactly one vote document");
});

test("a vote under a NEW key (reinstall) hits the same uniqueness and never adds a second vote", async () => {
  await worldWithPublishedSeries();
  const first = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW}));

  // Переустановка/второе устройство: новый ключ операции, изменённый набор — уникальность §3
  // сильнее нового ключа: участник остаётся один раз, принятый набор не меняется.
  const second = await withFixedClock(NOW + 5000, () =>
    submit(VOTER, `${voteKey(SERIES, 1, 0)}-fresh`, "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW + 4000}));
  assert.strictEqual(second.replayed, false, "a new key is a new operation run");
  assert.deepStrictEqual(second.result, first.result, "the stored set comes back, not the new one");

  const votes = [...store.documents.keys()].filter((path) => path.startsWith("poll_votes/"));
  assert.strictEqual(votes.length, 1, `exactly one vote document, got: ${votes.join(", ")}`);
});

test("a changed body under the known vote key is a conflict, not a changed vote", async () => {
  await worldWithPublishedSeries();
  await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW}));
  const conflict = await httpsErrorOf(withFixedClock(NOW + 200, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-blue"], confirmedAtMs: NOW})));
  assert.strictEqual(conflict.code, "invalid-argument");
  assert.ok(conflict.message.includes("cannot be reused for another operation or payload"), conflict.message);
  assert.deepStrictEqual(
    store.documents.get(voteDocId(SERIES, 1, 0, VOTER)).selectedOptionIds, ["opt-red"]);
});

test("multi-choice accepts a set; single-choice refuses two options by contract", async () => {
  await worldWithPublishedSeries();
  const multi = await withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: ["f-apple", "f-pear"], confirmedAtMs: NOW}));
  assert.deepStrictEqual(multi.result.selectedOptionIds, ["f-apple", "f-pear"]);

  const singleTwo = await httpsErrorOf(withFixedClock(NOW + 200, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red", "opt-blue"], confirmedAtMs: NOW})));
  assert.strictEqual(singleTwo.code, "invalid-argument");
  assert.ok(singleTwo.message.includes("SINGLE"), singleTwo.message);
});

test("votes outside the published identity refuse by name", async () => {
  await worldWithPublishedSeries();
  const unknownOption = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-pink"], confirmedAtMs: NOW})));
  assert.strictEqual(unknownOption.code, "invalid-argument");

  const staleRevision = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 9, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 9, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW})));
  assert.strictEqual(staleRevision.code, "failed-precondition");
  assert.ok(staleRevision.message.includes("POLL_SERIES_REVISION_MISMATCH"), staleRevision.message);

  const unknownSeries = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey("series-none", 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: "series-none", revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW})));
  assert.strictEqual(unknownSeries.code, "failed-precondition");
  assert.ok(unknownSeries.message.includes("POLL_SERIES_NOT_FOUND"), unknownSeries.message);
});

test("extra body fields refuse before any write (the excluded never reaches the base)", async () => {
  await worldWithPublishedSeries();
  const extra = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 0), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 0, selectedOptionIds: ["opt-red"], confirmedAtMs: NOW, token: "x"})));
  assert.strictEqual(extra.code, "invalid-argument");
  assert.ok(extra.message.includes("Unsupported vote fields"), extra.message);
  assert.strictEqual(store.documents.has(voteDocId(SERIES, 1, 0, VOTER)), false);
});

test("an empty or duplicated option set refuses", async () => {
  await worldWithPublishedSeries();
  const empty = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: [], confirmedAtMs: NOW})));
  assert.strictEqual(empty.code, "invalid-argument");
  const dupes = await httpsErrorOf(withFixedClock(NOW + 100, () =>
    submit(VOTER, voteKey(SERIES, 1, 1), "polls.SUBMIT_VOTE",
      {seriesId: SERIES, revisionId: 1, questionIndex: 1, selectedOptionIds: ["f-apple", "f-apple"], confirmedAtMs: NOW})));
  assert.strictEqual(dupes.code, "invalid-argument");
  assert.strictEqual([...store.documents.keys()].filter((p) => p.startsWith("poll_votes/")).length, 0);
});

// ─── The suite runner (same shape as the other index.js suites) ──────────────────────────────────

(async () => {
  let failed = 0;
  for (const [name, fn] of SUITE) {
    try {
      await fn();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.log(`not ok - ${name}`);
      console.log(String(error && error.stack || error));
    }
  }
  if (failed > 0) {
    console.log(`${failed} case(s) failed`);
    process.exit(1);
  }
})();
