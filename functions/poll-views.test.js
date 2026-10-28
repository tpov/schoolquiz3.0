"use strict";

// The client-facing proof of story 8.2 (epic-social-features, UX-O7): the two read surfaces the
// app reaches — `listPollSeries` and `getPollQuestionState` — driven through the real index.js
// boundary over the Firestore stub, with the votes written by the real mutation receiver.
// Contract §5 lives here: one counts snapshot per identity triple, distribution hidden until the
// confirmed own vote — «скрыто» (null) и «нулевой счёт» — разные состояния (AD-8).

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("node:module");
const path = require("node:path");
const {createFirestoreStub} = require("./_firestore-stub");

const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;

const functions = require(path.join(__dirname, "index.js"));

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

const AUTHOR = "poll-author-1";
const ALICE = "poll-voter-alice";
const BOB = "poll-voter-bob";
const CAROL = "poll-voter-carol";
const DAVE = "poll-voter-dave";
const SERIES = "series-views-1";
const NOW = 1_950_000_000_000;

for (const uid of [AUTHOR, ALICE, BOB, CAROL]) {
  store.documents.set(`profiles/${uid}`, {uid});
}

const SINGLE_Q = {
  questionIndex: 0, text: "Favorite color?", mode: "SINGLE",
  options: [
    {optionId: "opt-red", text: "Red"},
    {optionId: "opt-green", text: "Green"},
    {optionId: "opt-blue", text: "Blue"},
  ],
};
const MULTI_Q = {
  questionIndex: 1, text: "Which fruits?", mode: "MULTIPLE",
  options: [
    {optionId: "f-apple", text: "Apple"},
    {optionId: "f-plum", text: "Plum"},
    {optionId: "f-pear", text: "Pear"},
  ],
};

function voteKey(questionIndex) {
  return `polls-SUBMIT_VOTE-series-${SERIES}-r1-q${questionIndex}`;
}

async function publish() {
  const result = await functions.submitMutation.run({
    auth: {uid: AUTHOR},
    data: {
      mutationId: `polls-PUBLISH_SERIES-series-${SERIES}-r1`,
      operation: "polls.PUBLISH_SERIES",
      payload: JSON.stringify({
        seriesId: SERIES, title: "Views demo", expectedRevisionId: 0,
        questions: [SINGLE_Q, MULTI_Q],
      }),
    },
  });
  assert.equal(result.result.revisionId, 1);
}

/** Каждый тест — чистый мир: профили + опубликованная серия r1. */
async function freshWorld() {
  store.reset(Object.fromEntries([AUTHOR, ALICE, BOB, CAROL, DAVE].map((uid) => [`profiles/${uid}`, {uid}])));
  await publish();
}

async function vote(uid, questionIndex, optionIds) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {
      mutationId: `${voteKey(questionIndex)}-${uid}`,
      operation: "polls.SUBMIT_VOTE",
      payload: JSON.stringify({
        seriesId: SERIES, revisionId: 1, questionIndex,
        selectedOptionIds: optionIds, confirmedAtMs: NOW,
      }),
    },
  });
}

async function listSeries(uid, payload = {}) {
  return functions.listPollSeries.run({auth: {uid}, data: payload});
}

async function questionState(uid, questionIndex) {
  return functions.getPollQuestionState.run({auth: {uid}, data: {seriesId: SERIES, questionIndex}});
}

// ─── the projection rules of §5 ─────────────────────────────────────────────────────────────────

test("published series lists with own flag and zero progress before any vote", async () => {
  await freshWorld();

  const authorView = await listSeries(AUTHOR);
  assert.equal(authorView.viewVersion, "poll-views-v1");
  assert.equal(authorView.entries.length, 1);
  const own = authorView.entries[0];
  assert.equal(own.seriesId, SERIES);
  assert.equal(own.own, true, "the author recognizes the series as their own");
  assert.equal(own.revisionId, 1);
  assert.equal(own.questionCount, 2);
  assert.equal(own.answeredCount, 0);

  const strangerView = await listSeries(ALICE);
  assert.equal(strangerView.entries[0].own, false);
  assert.equal(strangerView.entries[0].answeredCount, 0);
});

test("distribution is hidden (null) until the own confirmed vote, even while others voted", async () => {
  await freshWorld();
  await vote(BOB, 0, ["opt-red"]);

  const hidden = await questionState(ALICE, 0);
  assert.equal(hidden.ownVote, null);
  assert.equal(hidden.distribution, null, "скрыто — не нулевой счёт (AD-8): распределения нет вовсе");
  assert.equal(hidden.question.text, SINGLE_Q.text, "the question content itself is readable");
  assert.equal(hidden.question.options.length, 3);

  const selfHidden = await questionState(AUTHOR, 0);
  assert.equal(selfHidden.distribution, null, "the author has no votes of their own either");
});

test("after the own vote one snapshot arrives: per-option counts plus the respondent denominator", async () => {
  await freshWorld();
  await vote(BOB, 0, ["opt-red"]);
  await vote(ALICE, 0, ["opt-green"]);

  const snapshot = await questionState(ALICE, 0);
  assert.ok(snapshot.ownVote);
  assert.deepEqual(snapshot.ownVote.selectedOptionIds, ["opt-green"]);
  assert.deepEqual(snapshot.distribution, {
    optionCounts: {"opt-red": 1, "opt-green": 1, "opt-blue": 0},
    respondentCount: 2,
  });
});

test("multi-choice counts distinct respondents, so option sums may exceed the denominator", async () => {
  await freshWorld();
  await vote(ALICE, 1, ["f-apple", "f-plum"]);
  await vote(BOB, 1, ["f-apple"]);
  await vote(CAROL, 1, ["f-apple", "f-plum", "f-pear"]);

  const snapshot = await questionState(CAROL, 1);
  // Проценты считает презентация клиента: 100×выбравших/ответивших; apple = 3/3 = 100%,
  // plum = 2/3 ≈ 66.7% — сумма по вариантам уходит за 100, знаменатель — участники.
  assert.deepEqual(snapshot.distribution, {
    optionCounts: {"f-apple": 3, "f-plum": 2, "f-pear": 1},
    respondentCount: 3,
  });
});

test("a multi-choice vote with one option still counts its author once", async () => {
  await freshWorld();
  await vote(BOB, 1, ["f-pear"]);
  assert.equal((await listSeries(BOB)).entries[0].answeredCount, 1,
    "series progress counts questions answered, not marks");
});

test("progress and counts keep the revision identity: other-revision rows do not leak", async () => {
  await freshWorld();
  // Документ чужой revision (сервер такое не пишет — здесь он имитирует дрейф хранилища)
  // не должен попасть ни в прогресс, ни в счёты: тройка идентичности — единственная ось отбора.
  store.documents.set(`poll_votes/${SERIES}-r9-q0-${DAVE}`, {
    voteId: `${SERIES}-r9-q0-${DAVE}`, seriesId: SERIES, revisionId: 9, questionIndex: 0,
    participantUid: DAVE, selectedOptionIds: ["opt-red"], clientConfirmedAtMs: NOW,
    acceptedAtMs: NOW, schemaVersion: 1,
  });
  const snapshot = await questionState(DAVE, 0);
  assert.equal(snapshot.ownVote, null, "a vote of another revision is not this question's vote");
  assert.equal(snapshot.distribution, null);
  assert.equal((await listSeries(DAVE)).entries[0].answeredCount, 0,
    "stale-revision rows do not count toward current progress");
});

test("distribution keeps the question axis even when production ids collide across questions", async () => {
  // Продакшн-авторинг назначал одинаковые optionId на каждый вопрос (opt-1..opt-8 — до
  // клиентского фикса единственный путь из UI). Ретро эпика 8, находка 1: счёт обязан держать
  // ось вопроса независимо от коллизий id, иначе голос чужого вопроса попадает в счёт варианта
  // и в знаменатель («проценты по различным респондентам вопроса», критерий 1).
  store.reset(Object.fromEntries([AUTHOR, ALICE, BOB].map((uid) => [`profiles/${uid}`, {uid}])));
  const colliding = [
    {questionIndex: 0, text: "First?", mode: "SINGLE",
      options: [{optionId: "opt-1", text: "A"}, {optionId: "opt-2", text: "B"}]},
    {questionIndex: 1, text: "Second?", mode: "SINGLE",
      options: [{optionId: "opt-1", text: "A"}, {optionId: "opt-2", text: "B"}]},
  ];
  const published = await functions.submitMutation.run({
    auth: {uid: AUTHOR},
    data: {
      mutationId: "polls-PUBLISH_SERIES-series-collide-r1",
      operation: "polls.PUBLISH_SERIES",
      payload: JSON.stringify({seriesId: "series-collide", title: "Colliding ids", expectedRevisionId: 0, questions: colliding}),
    },
  });
  assert.equal(published.result.revisionId, 1);

  async function collideVote(uid, questionIndex, optionIds) {
    return functions.submitMutation.run({
      auth: {uid},
      data: {
        mutationId: `polls-SUBMIT_VOTE-series-collide-r1-q${questionIndex}-${uid}`,
        operation: "polls.SUBMIT_VOTE",
        payload: JSON.stringify({
          seriesId: "series-collide", revisionId: 1, questionIndex,
          selectedOptionIds: optionIds, confirmedAtMs: NOW,
        }),
      },
    });
  }
  async function collideState(uid, questionIndex) {
    return functions.getPollQuestionState.run({auth: {uid}, data: {seriesId: "series-collide", questionIndex}});
  }

  await collideVote(ALICE, 0, ["opt-1"]);
  await collideVote(BOB, 1, ["opt-2"]);

  const q0 = await collideState(ALICE, 0);
  assert.deepEqual(q0.distribution, {
    optionCounts: {"opt-1": 1, "opt-2": 0},
    respondentCount: 1,
  }, "the q0 distribution must not see the q1 vote of BOB");

  const q1 = await collideState(BOB, 1);
  assert.deepEqual(q1.distribution, {
    optionCounts: {"opt-1": 0, "opt-2": 1},
    respondentCount: 1,
  }, "the q1 distribution must not see the q0 vote of ALICE");
});

test("malformed requests refuse without touching the store", async () => {
  await freshWorld();
  const extraField = await functions.getPollQuestionState.run({
    auth: {uid: ALICE}, data: {seriesId: SERIES, questionIndex: 0, sneaky: true},
  }).then(() => null, (error) => error);
  assert.equal(extraField.code, "invalid-argument");

  const outOfRange = await functions.getPollQuestionState.run({
    auth: {uid: ALICE}, data: {seriesId: SERIES, questionIndex: 5},
  }).then(() => null, (error) => error);
  assert.equal(outOfRange.code, "invalid-argument");

  const badPageSize = await listSeries(ALICE, {pageSize: 999})
    .then(() => null, (error) => error);
  assert.equal(badPageSize.code, "invalid-argument");

  const unknownSeries = await functions.getPollQuestionState.run({
    auth: {uid: ALICE}, data: {seriesId: "series-missing", questionIndex: 0},
  }).then(() => null, (error) => error);
  assert.equal(unknownSeries.code, "not-found");
});
