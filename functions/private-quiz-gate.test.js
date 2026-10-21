"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const path = require("node:path");

const {startPrivateQuizAttempt, normalizeSettings} = require("./private-quiz-gate");
const {createFirestoreStub} = require("./_firestore-stub");

const AUTHOR = "author-000000000001";
const PARTICIPANT = "participant-00000000001";
const QUEST_PATH = `private/${AUTHOR}/catalogs/private-quizzes-v1/quests/quest-1`;
const NOW = 1_800_000_000_000;

function seed(database, settings) {
  const data = {privateQuizSettings: {attemptLimit: 1, showResult: false, enabled: true, startedCount: 0, ...settings}};
  database.doc(QUEST_PATH).set(data);
  return data;
}

function start(database, data = {}, uid = PARTICIPANT, now = NOW) {
  return startPrivateQuizAttempt({
    db: database,
    uid,
    now,
    data: {ownerUid: AUTHOR, catalogId: "private-quizzes-v1", questId: "quest-1", startId: "start-000000001", ...data},
  });
}

test("first start consumes one attempt and stores the start under its id", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {attemptLimit: 2});

  const first = await start(database, {startId: "start-000000001"});
  assert.equal(first.attemptLimit, 2);
  assert.equal(first.participantUid, PARTICIPANT);

  const stored = (await database.doc(`${QUEST_PATH}/starts/start-000000001`).get()).data();
  assert.equal(stored.startId, "start-000000001");
  const quest = (await database.doc(QUEST_PATH).get()).data();
  assert.equal(quest.privateQuizSettings.startedCount, 1, "лимит считается один раз");
});

test("retrying the same startId returns the stored start without a second consume", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {attemptLimit: 2});

  await start(database, {startId: "start-000000001"});
  const again = await start(database, {startId: "start-000000001"});

  assert.equal(again.startedAtMs, NOW, "повтор возвращает сохранённый старт");
  const quest = (await database.doc(QUEST_PATH).get()).data();
  assert.equal(quest.privateQuizSettings.startedCount, 1, "лимит не сгорел дважды");
});

test("the limit closes the door with a named refusal", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {attemptLimit: 1, startedCount: 1});

  await assert.rejects(
    start(database, {startId: "start-000000002"}),
    {code: "ATTEMPT_LIMIT_REACHED"},
  );
});

test("a disabled link refuses before the limit is even read", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {enabled: false});

  await assert.rejects(start(database), {code: "LINK_DISABLED"});
});

test("an expired link refuses and a future expiry passes", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {linkExpiresAtMs: NOW - 1});
  await assert.rejects(start(database), {code: "LINK_EXPIRED"});

  database.doc(QUEST_PATH).set({privateQuizSettings: {attemptLimit: 1, linkExpiresAtMs: NOW + 1, enabled: true, startedCount: 0}});
  await start(database, {startId: "start-000000009"});
  const quest = (await database.doc(QUEST_PATH).get()).data();
  assert.equal(quest.privateQuizSettings.startedCount, 1, "неистёкшая ссылка стартует");
});

test("missing settings fall back to the AD-29 defaults, malformed ones refuse loudly", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const database = createFirestoreStub({}).admin.firestore();
  database.doc(QUEST_PATH).set({title: "no settings yet"});
  const first = await start(database, {startId: "start-000000003"});
  assert.equal(first.attemptLimit, 1, "дефолт AD-29: одна попытка");

  database.doc(QUEST_PATH).set({privateQuizSettings: {attemptLimit: 0}});
  await assert.rejects(start(database, {startId: "start-000000004"}), {code: "INVALID_SETTINGS"});
});

test("a foreign participant is recorded as the caller, the ownerUid names the author", async () => {
  const {createFirestoreStub} = require("./_firestore-stub");
  const stub = createFirestoreStub({});
  const database = stub.admin.firestore();
  seed(database, {attemptLimit: 2});
  const other = "participant-00000000002";

  await start(database, {startId: "start-000000001"});
  await start(database, {startId: "start-000000002"}, other);

  const stored = (await database.doc(`${QUEST_PATH}/starts/start-000000002`).get()).data();
  assert.equal(stored.participantUid, other, "второй участник стартует своим uid");
  const quest = (await database.doc(QUEST_PATH).get()).data();
  assert.equal(quest.privateQuizSettings.startedCount, 2, "лимит общий на квест, не на участника");
});

test("normalizeSettings validates field by field", () => {
  assert.deepEqual(normalizeSettings(undefined), {attemptLimit: 1, showResult: false, linkExpiresAtMs: null, enabled: true, startedCount: 0});
  assert.throws(() => normalizeSettings({attemptLimit: 0}), /attemptLimit/);
  assert.throws(() => normalizeSettings({linkExpiresAtMs: -5}), /linkExpiresAtMs/);
  assert.throws(() => normalizeSettings({startedCount: -1}), /startedCount/);
  assert.equal(normalizeSettings({showResult: true, linkExpiresAtMs: 5}).linkExpiresAtMs, 5);
});
