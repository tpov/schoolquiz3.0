"use strict";

// The client-facing proof of story 4.9 (shared inbox, AD-20/CAP-6): both surfaces the app reaches
// — the `listInboxItems` callable and the `lesson_report.SUBMIT_ERROR` queue receiver — driven
// through the real index.js boundary over the Firestore stub. Pure pagination/projection shape is
// asserted at the same boundary, so nothing here trusts an internal function.

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

// ─── fixtures ──────────────────────────────────────────────────────────────────────────────────

const AUTHOR = "author-uid-1";
const CURATOR = "curator-uid-1";
const REPORTER = "reporter-uid-1";
const STRANGER = "stranger-uid-1";
const LESSON = "ac_0ecb_lesson_9";
const OTHER_LESSON = "ac_0ecb_lesson_7";
const MUTATION = "lesson_report.SUBMIT_ERROR";

function seed(path, data) { store.documents.set(path, JSON.parse(JSON.stringify(data))); }

// Приёмник мутаций отвечает профилю (`requireProfile`): любой вошедший с довренным профилем может
// подать отчёт — право читать его отдельно проверяет view.
for (const uid of [AUTHOR, CURATOR, REPORTER, STRANGER]) {
  seed(`profiles/${uid}`, {uid, testerLevel: 0, adminLevel: 0});
}

function placement(lessonId, authorUid) {
  seed(`lesson_placements/${lessonId}`, {
    lessonId, authorUid, questType: "REGULAR", shelf: "arena",
    previousShelf: "review", contentVersion: 1, placementVersion: 1, updatedAtMs: 1000,
  });
}

function comment(lessonId, id, nickname, text, atMs) {
  seed(`lessonComments/${id}`, {
    lessonId, authorUid: `commenter-${id}`, authorNickname: nickname, text, createdAt: atMs,
  });
}

/** The receiver around submitMutation, the way the client's outbox drain calls it. */
async function deliver(mutationId, uid, payload) {
  const result = await functions.submitMutation.run({
    auth: {uid},
    data: {mutationId, operation: MUTATION, payload},
  });
  return result;
}

async function inbox(uid, payload = {}) {
  return functions.listInboxItems.run({auth: {uid}, data: payload});
}

// ─── submission path ───────────────────────────────────────────────────────────────────────────

test("поданный отчёт адресуется разрешённому автору урока, выведенному сервером", async () => {
  placement(LESSON, AUTHOR);
  const first = await deliver("report-journey-1", REPORTER, {
    reportId: "report-journey-1", lessonId: LESSON, text: "Question two never accepts the right answer",
  });
  assert.equal(first.replayed, false);
  assert.equal(first.result.reportId, "report-journey-1");

  const stored = store.documents.get(`lesson_error_reports/report-journey-1`);
  assert.equal(stored.lessonAuthorUid, AUTHOR, "адресат — автор размещения, не из тела запроса");
  assert.equal(stored.reporterUid, REPORTER);
  assert.equal(stored.schemaVersion, 1);
});

test("повтор той же записи очереди возвращает сохранённый результат и не пишет второй документ", async () => {
  placement(LESSON, AUTHOR);
  const first = await deliver("report-replay-1", REPORTER, {
    reportId: "report-replay-1", lessonId: LESSON, text: "typo in the title",
  });
  const second = await deliver("report-replay-1", REPORTER, {
    reportId: "report-replay-1", lessonId: LESSON, text: "typo in the title",
  });
  assert.equal(first.replayed, false);
  assert.equal(second.replayed, true);
  assert.deepEqual(second.result, first.result);
  assert.equal(store.documents.has("lesson_error_reports/report-replay-1"), true);
});

test("тело с лишними полями отвергается целиком — вопрос и ответ через отчёт не проедут", async () => {
  placement(LESSON, AUTHOR);
  await assert.rejects(
    deliver("report-fields-1", REPORTER, {
      reportId: "report-fields-1", lessonId: LESSON, text: "hi",
      question: {text: "secret question"}, answer: "secret answer",
    }),
    (error) => error.code === "invalid-argument",
  );
  assert.equal(store.documents.has("lesson_error_reports/report-fields-1"), false);
});

test("урок вне размещения — окончательный отказ без записи", async () => {
  await assert.rejects(
    deliver("report-orphan-1", REPORTER, {reportId: "report-orphan-1", lessonId: "no_such_lesson", text: "hi"}),
    (error) => error.code === "failed-precondition",
  );
  assert.equal(store.documents.has("lesson_error_reports/report-orphan-1"), false);
});

test("пустой текст и превышение длины отвергаются", async () => {
  placement(LESSON, AUTHOR);
  await assert.rejects(
    deliver("report-blank-1", REPORTER, {reportId: "report-blank-1", lessonId: LESSON, text: "   "}),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    deliver("report-long-1", REPORTER, {reportId: "report-long-1", lessonId: LESSON, text: "x".repeat(1001)}),
    (error) => error.code === "invalid-argument",
  );
});

// ─── inbox reads ───────────────────────────────────────────────────────────────────────────────

test("приватный отчёт виден разрешённому автору и не виден куратору, репортёру и постороннему", async () => {
  placement(LESSON, AUTHOR);
  await deliver("report-who-1", REPORTER, {
    reportId: "report-who-1", lessonId: LESSON, text: "private whole-lesson text",
  });

  const forAuthor = await inbox(AUTHOR);
  const report = forAuthor.entries.find((entry) => entry.id === "report-who-1");
  assert.ok(report, "автор урока видит отчёт");
  assert.equal(report.kind, "private_error_report");
  assert.equal(report.lessonId, LESSON);
  assert.equal(report.readers, "author_only");
  assert.equal(report.text, "private whole-lesson text");
  // Проекция фиксирована: чужих полей (reporterUid и прочих) в проводе нет.
  assert.deepEqual(Object.keys(report).sort(),
    ["createdAtMs", "id", "kind", "lessonId", "readers", "text"]);

  for (const outsider of [CURATOR, REPORTER, STRANGER]) {
    const page = await inbox(outsider);
    assert.equal(
      page.entries.some((entry) => entry.id === "report-who-1"),
      false,
      `${outsider} не читает чужой приватный отчёт — роль и авторство подачи права не дают`,
    );
  }
});

test("публичные комментарии к своим урокам едут рядом с отчётом, чужие — нет", async () => {
  placement(LESSON, AUTHOR);
  placement(OTHER_LESSON, STRANGER);
  comment(LESSON, "c-owned-1", "Player", "typo in lesson title", 5_000);
  comment(OTHER_LESSON, "c-foreign-1", "Player", "foreign lesson comment", 6_000);

  const forAuthor = await inbox(AUTHOR);
  const entry = forAuthor.entries.find((item) => item.id === "c-owned-1");
  assert.ok(entry, "комментарий к своему уроку в общем inbox");
  assert.equal(entry.kind, "lesson_comment");
  assert.equal(entry.readers, "public");
  assert.equal(entry.lessonId, LESSON);
  assert.equal(entry.authorNickname, "Player");
  assert.equal(
    forAuthor.entries.some((item) => item.id === "c-foreign-1"),
    false,
    "чужие уроки в inbox автора не попадают",
  );
  // Публичное обсуждение существует отдельно и по-прежнему читается напрямую любым вошедшим —
  // правила не менялись; здесь проверяем только, что проекция не приватизирует комментарий.
  assert.deepEqual(Object.keys(entry).sort(),
    ["authorNickname", "createdAtMs", "id", "kind", "lessonId", "readers", "text"]);
});

test("слияние сортируется по свежести, якорь продолжает без повторов и дублей", async () => {
  // Хранилище общее на файл: предыдущие тесты оставили отчёты с «свежими» моментами Date.now().
  for (const key of [...store.documents.keys()]) {
    if (!key.startsWith("profiles/")) store.documents.delete(key);
  }
  placement(LESSON, AUTHOR);
  comment(LESSON, "c-page-1", "A", "oldest comment", 1_000);
  comment(LESSON, "c-page-2", "B", "middle comment", 3_000);
  await deliver("report-page-1", REPORTER, {reportId: "report-page-1", lessonId: LESSON, text: "newest report"});
  // Один момент с отчётом — порядок добивает id: "report-page-2" < "report-page-3" по убыванию.
  seed("lesson_error_reports/report-page-2", {
    reportId: "report-page-2", lessonId: LESSON, lessonAuthorUid: AUTHOR,
    reporterUid: REPORTER, text: "tie lower id", schemaVersion: 1, createdAtMs: 2_000,
  });
  seed("lesson_error_reports/report-page-3", {
    reportId: "report-page-3", lessonId: LESSON, lessonAuthorUid: AUTHOR,
    reporterUid: REPORTER, text: "tie higher id", schemaVersion: 1, createdAtMs: 2_000,
  });

  const firstPage = await inbox(AUTHOR, {pageSize: 3});
  assert.deepEqual(firstPage.entries.map((entry) => entry.id),
    ["report-page-1", "c-page-2", "report-page-3"], "свежие выше, при равенстве решает id по убыванию");
  assert.ok(firstPage.nextAnchor, "есть продолжение");

  const secondPage = await inbox(AUTHOR, {pageSize: 3, after: firstPage.nextAnchor});
  const seen = new Set([...firstPage.entries, ...secondPage.entries].map((entry) => entry.id));
  assert.equal(seen.size, firstPage.entries.length + secondPage.entries.length, "ни одного повтора");
  assert.deepEqual(secondPage.entries.map((entry) => entry.id), ["report-page-2", "c-page-1"]);
  assert.equal(secondPage.nextAnchor, null, "конец списка — якоря нет");
});

test("кривая форма запроса отвергается поименованным INVALID_ARGUMENT", async () => {
  await assert.rejects(inbox(AUTHOR, {pageSize: 0}), (error) => error.code === "invalid-argument");
  await assert.rejects(inbox(AUTHOR, {pageSize: 21}), (error) => error.code === "invalid-argument");
  await assert.rejects(inbox(AUTHOR, {unknownField: 1}), (error) => error.code === "invalid-argument");
  await assert.rejects(
    inbox(AUTHOR, {after: {atMs: -1, id: "x"}}),
    (error) => error.code === "invalid-argument",
  );
});

test("враждебная строка публичного комментария не роняет inbox автора", async () => {
  placement(LESSON, AUTHOR);
  store.documents.set("lessonComments/c-hostile-1", {
    lessonId: LESSON, authorNickname: 42, text: 7, createdAt: "not-a-number",
  });
  const page = await inbox(AUTHOR);
  assert.equal(page.entries.some((entry) => entry.id === "c-hostile-1"), false);
});
