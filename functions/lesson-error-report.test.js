"use strict";

/**
 * Повторная подача отчёта об ошибке — только владельцу записи (ретро 2026-10-25, L5).
 *
 * reportId выбирает клиент: кто первым занял id, тот и владеет его содержимым. Повтор под тем же
 * id обязан отвечать сохранённой записью только тому, кто эту запись подал, и только про тот же
 * урок: чужой повтор раньше показывал ложный успех («отчёт доставлен»), которым подменялся
 * настоящий. Сюита гоняет настоящий callable против стаба Firestore: свежая запись, идемпотентный
 * повтор владельца, отказ чужому и отказ своему id на чужой урок — без единой лишней записи.
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath;
stub.loaded = true;
stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");

const REPORTER = "reporter-uid-1";
const SQUATTER = "squatter-uid-1";
const LESSON = "lesson_1";
const OTHER_LESSON = "lesson_2";
const OPERATION = "lesson_report.SUBMIT_ERROR";

function reportWorld(extra = {}) {
  return {
    "profiles/reporter-uid-1": {uid: REPORTER},
    "profiles/squatter-uid-1": {uid: SQUATTER},
    "lesson_placements/lesson_1": {authorUid: "author-uid-1"},
    "lesson_placements/lesson_2": {authorUid: "author-uid-2"},
    ...extra,
  };
}

function reportBody(overrides = {}) {
  return {reportId: "report-1", lessonId: LESSON, text: "Опечатка в формулировке второго вопроса",
    ...overrides};
}

async function submit(uid, mutationId, body) {
  return functions.submitMutation.run({
    auth: {uid},
    data: {mutationId, operation: OPERATION, payload: JSON.stringify(body)},
  });
}

test("свежий отчёт пишется с автором подачи и адресатом из документа размещения", async () => {
  store.reset(reportWorld());
  const result = await submit(REPORTER, "mut-1", reportBody());
  assert.equal(result.replayed, false);
  const stored = store.documents.get("lesson_error_reports/report-1");
  assert.equal(stored.reporterUid, REPORTER);
  assert.equal(stored.lessonId, LESSON);
  assert.equal(stored.lessonAuthorUid, "author-uid-1");
  assert.equal(stored.text, "Опечатка в формулировке второго вопроса");
});

test("повтор владельца под тем же id возвращает ту же запись и не пишет вторую", async () => {
  store.reset(reportWorld());
  const first = await submit(REPORTER, "mut-1", reportBody());
  const replay = await submit(REPORTER, "mut-2", reportBody());
  assert.equal(replay.result.reportId, "report-1");
  assert.equal(replay.result.createdAtMs, first.result.createdAtMs, "запись та же, момент не перезаписан");
  assert.equal(replay.result.lessonId, LESSON);
  const stored = store.documents.get("lesson_error_reports/report-1");
  assert.equal(stored.reporterUid, REPORTER, "автор записи не сменился");
});

test("чужой reportId отвечает именованным отказом, а не ложным успехом", async () => {
  store.reset(reportWorld());
  await submit(REPORTER, "mut-1", reportBody());

  await assert.rejects(submit(SQUATTER, "mut-2", reportBody()), /REPORT_ID_NOT_OWNED/);

  const stored = store.documents.get("lesson_error_reports/report-1");
  assert.equal(stored.reporterUid, REPORTER, "запись остаётся за владельцем");
  assert.equal(store.documents.get("lesson_error_reports/report-1").text,
    "Опечатка в формулировке второго вопроса", "содержимое не подменено");
});

test("свой reportId на другой урок — отказ: id привязан к своей записи", async () => {
  store.reset(reportWorld());
  await submit(REPORTER, "mut-1", reportBody());

  await assert.rejects(
    submit(REPORTER, "mut-2", reportBody({lessonId: OTHER_LESSON, text: "Другой урок, тот же id"})),
    /REPORT_ID_NOT_OWNED/,
  );

  const stored = store.documents.get("lesson_error_reports/report-1");
  assert.equal(stored.lessonId, LESSON, "привязка записи не перезаписана");
  assert.equal(
    [...store.documents.keys()].filter((path) => path.startsWith("lesson_error_reports/")).length,
    1,
    "второй записи под тем же id не появилось",
  );
});
