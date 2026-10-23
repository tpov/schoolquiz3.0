"use strict";

/**
 * История 8.6: собственная история операций и «Начисления» авторского урока (AD-30/UX-20).
 *
 * Проверяется против настоящих view TZ06B-семьи (`arena-lifecycle-views.js`): поля — ровно
 * контракт O10, порядок — (atMs, id) по убыванию, страница ограничена и продолжается
 * шифрованным курсором; pending едет отдельным списком и никогда внутри записей; чужие строки
 * не читаются ни при каком теле; чтение не пишет ни в книгу, ни в журналы.
 *
 * Книга пишется той же заглушкой, что и везде: строки кладутся в `store.documents` прямо —
 * семья view читает их теми же запросами, что и в проде.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
// Курсор шифруется секретом, который defineSecret читает из окружения до загрузки index.js
// (приём arena-lifecycle-views-handler.test.js).
process.env.ARENA_LIFECYCLE_CURSOR_SECRET = Buffer.alloc(32, 11).toString("base64url");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath;
stub.loaded = true;
stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const {CURRENCY, LEDGER_PATH, REASON} = require("./currency-ledger");

const UID = "history-owner-1";
const OTHER = "history-stranger-1";
const LESSON = "lesson-history-1";
const T0 = Date.parse("2026-10-01T00:00:00Z");

function call(method, uid, data) {
  return functions[method].run({auth: {uid}, data});
}

function ledgerRow(id, patch = {}) {
  return {
    id,
    uid: UID,
    currency: CURRENCY.NOLICS,
    delta: 12,
    reason: REASON.ATTEMPT_REWARD,
    sourceId: `source-${id}`,
    atMs: T0 + 1000,
    ...patch,
  };
}

function world(extra = {}) {
  const docs = {
    [`profiles/${UID}`]: {},
    [`profiles/${OTHER}`]: {},
    [`lesson_placements/${LESSON}`]: {authorUid: UID, shelf: "arena"},
    "arena_entry_tickets/ticket-open-1": {
      uid: UID,
      status: "RESERVED",
      issuedAtMs: T0 + 5000,
      priceNolics: 7,
      lifeCost: 2,
    },
    "arena_entry_tickets/ticket-other": {
      uid: OTHER,
      status: "RESERVED",
      issuedAtMs: T0 + 6000,
      priceNolics: 99,
      lifeCost: 0,
    },
  };
  return {...docs, ...extra};
}

function seedRows(rows) {
  for (const row of rows) store.documents.set(`${LEDGER_PATH}/${row.id}`, row);
}

test("own history returns the permitted fields only, newest first, with pending outside entries", async () => {
  store.reset(world());
  seedRows([
    ledgerRow("row-old", {atMs: T0 + 1000}),
    ledgerRow("row-new", {atMs: T0 + 3000, delta: -4, reason: REASON.ATTEMPT_TOLL,
      details: {lessonId: LESSON}}),
    ledgerRow("row-mid", {atMs: T0 + 2000, currency: CURRENCY.GOLD}),
  ]);
  const reply = await call("listMyOperationHistory", UID, {pageSize: 20});
  assert.deepEqual(
    reply.entries.map((entry) => entry.id),
    ["row-new", "row-mid", "row-old"],
    "новые выше",
  );
  for (const entry of reply.entries) {
    assert.deepEqual(
      Object.keys(entry).sort(),
      ["atMs", "currency", "delta", "id", "lessonId", "reason", "status"],
      `поля записи — контракт O10, и ничего сверх: ${Object.keys(entry).sort()}`,
    );
    assert.equal(entry.status, "CONFIRMED");
  }
  assert.equal(reply.entries[0].lessonId, LESSON, "привязка к уроку доезжает, когда она есть");
  assert.equal(reply.entries[1].lessonId, null, "строки без привязки читаются пустотой, не домыслом");
  // Pending — отдельно от entries: чужой билет не виден, свой виден, доходом не считается.
  assert.equal(reply.pending.length, 2, "свой билет даёт две строки удержания: нолики и заряды");
  assert.ok(reply.pending.every((row) => row.status === "PENDING"));
  assert.ok(reply.pending.every((row) => !reply.entries.some((entry) => entry.id === row.ticketId)));
  assert.equal(reply.nextCursor, null, "одна страница — продолжения нет");
});

test("pagination is bounded and continues strictly after the cursor", async () => {
  store.reset(world());
  seedRows([
    ledgerRow("row-a", {atMs: T0 + 1000}),
    ledgerRow("row-b", {atMs: T0 + 2000}),
    ledgerRow("row-c", {atMs: T0 + 3000}),
  ]);
  const first = await call("listMyOperationHistory", UID, {pageSize: 2});
  assert.deepEqual(first.entries.map((entry) => entry.id), ["row-c", "row-b"]);
  assert.ok(first.nextCursor, "продолжение названо");

  const second = await call("listMyOperationHistory", UID, {pageSize: 2, cursor: first.nextCursor});
  assert.deepEqual(second.entries.map((entry) => entry.id), ["row-a"]);
  assert.equal(second.nextCursor, null);
  // Pending — часть формы каждой страницы (§2): удержание живо, пока билет не разрешён.
  assert.equal(second.pending.length, 2, "открытый билет виден и на странице продолжения");
});

test("a foreign uid sees none of it: rows, tickets and the lesson are out of reach", async () => {
  store.reset(world());
  seedRows([ledgerRow("row-mine", {atMs: T0 + 1000})]);
  const reply = await call("listMyOperationHistory", OTHER, {pageSize: 20});
  assert.deepEqual(reply.entries, [], "чужих строк не существует");
  assert.deepEqual(
    reply.pending.map((row) => row.ticketId),
    ["ticket-other"],
    "свой билет чужим не становится: виден только свой, чужой — нет",
  );

  await assert.rejects(
    call("listMyLessonEarnings", OTHER, {lessonId: LESSON, pageSize: 20}),
    (error) => {
      assert.match(error.message, /could not be loaded/);
      return true;
    },
    "чужой урок не отличим от отсутствующего",
  );
});

test("lesson earnings select the author royalty rows of that lesson only", async () => {
  store.reset(world());
  seedRows([
    ledgerRow("royalty-other-lesson", {reason: REASON.ARENA_AUTHOR_ROYALTY, delta: 5,
      details: {lessonId: "lesson-other"}, atMs: T0 + 9000}),
    ledgerRow("royalty-target-2", {reason: REASON.ARENA_AUTHOR_ROYALTY, delta: 3,
      details: {lessonId: LESSON}, atMs: T0 + 2000}),
    ledgerRow("royalty-target-1", {reason: REASON.ARENA_AUTHOR_ROYALTY, delta: 4,
      details: {lessonId: LESSON}, atMs: T0 + 8000}),
    ledgerRow("reward-not-royalty", {details: {lessonId: LESSON}, atMs: T0 + 7000}),
  ]);
  const reply = await call("listMyLessonEarnings", UID, {lessonId: LESSON, pageSize: 20});
  assert.deepEqual(
    reply.entries.map((entry) => entry.id),
    ["royalty-target-1", "royalty-target-2"],
    "повод и урок называет сама строка; чужой урок и не-авторские поводы не едут",
  );
  assert.deepEqual(reply.pending, [], "у урока нет pending — форма едина");
  assert.equal(reply.nextCursor, null);
});

test("reading history writes nothing: no ledger rows, no journal, no receipts", async () => {
  store.reset(world());
  seedRows([ledgerRow("row-read-only", {atMs: T0 + 1000})]);
  const before = new Map(store.documents);
  await call("listMyOperationHistory", UID, {pageSize: 20});
  await call("listMyLessonEarnings", UID, {lessonId: LESSON, pageSize: 20});
  assert.equal(store.documents.size, before.size, "чтение не создаёт документов");
  assert.deepEqual(ledgerPaths(), [`${LEDGER_PATH}/row-read-only`], "книга не растёт от чтения");
});

function ledgerPaths() {
  return [...store.documents.keys()].filter((candidate) => candidate.startsWith(LEDGER_PATH));
}
