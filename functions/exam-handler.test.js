"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});

/**
 * Поведение кураторских обёрток экзамена — на УРОВНЕ ВЫЗОВА, как у arena-handler.
 *
 * Модуль `./exam-corrections` подменяется шпионом ДО загрузки index.js: обёртка обязана звать
 * модуль с record.uid = body.ownerUid и передать expectedHeadVersion как есть; затвор куратора
 * и валидация аргументов стоят ДО всякого чтения. Неправильный маппинг владельца или перевёрнутый
 * затвор были бы зелёными без этого файла.
 */

const appendCalls = [];
const materializeCalls = [];
const fulfilCalls = [];

const correctionsPath = require.resolve("./exam-corrections");
const correctionsStub = new Module(correctionsPath, null);
correctionsStub.filename = correctionsPath;
correctionsStub.loaded = true;
correctionsStub.exports = {
  createExamCorrections: () => ({
    appendExclusion: async (args) => {
      appendCalls.push(args);
      return {applied: true, replay: false,
        head: {headVersion: 1, cumulativeExclusions: [args.record.slotIndex]}};
    },
    materialize: async (args) => {
      materializeCalls.push(args);
      return {published: true, outcome: {state: "SCORED", percent: 61, passed: true},
        projection: {corrected: true}, generation: 2};
    },
    CORRECTION_STATE: {SCORED: "SCORED"},
  }),
};
require.cache[correctionsPath] = correctionsStub;

const adminPath = require.resolve("firebase-admin");
const adminStub = new Module(adminPath, null);
adminStub.filename = adminPath;
adminStub.loaded = true;
adminStub.exports = admin;
require.cache[adminPath] = adminStub;

const functions = require("./index");

function call(method, uid, data) {
  return functions[method].run({auth: {uid}, data});
}

/** HttpsError доходит и отказом, и свёрнутым значением — нормализуем к полю code/reason. */
async function callError(method, uid, data) {
  try {
    const value = await call(method, uid, data);
    const error = value && value.result ? value.result : value;
    return {code: error.code, reason: error.details && error.details.reason};
  } catch (error) {
    return {code: error.code, reason: error.details && error.details.reason};
  }
}

const CORRECTOR = {correctionId: "c1", ownerUid: "player", mode: "ThemeTest",
  assessmentId: "theme", sittingId: "s1", slotIndex: 0, reason: "confirmed fault"};

test("non-curator is permission-denied CURATOR_REQUIRED on all three exam curator wrappers", async () => {
  store.reset({"profiles/player": {}});
  for (const [method, data] of [
    ["appendExamCorrection", CORRECTOR],
    ["materializeExamCorrection", {ownerUid: "player", mode: "ThemeTest", assessmentId: "theme"}],
    ["fulfilCertificateSigning", {issuanceId: "i1", fence: "f"}],
  ]) {
    const error = await callError(method, "player", data);
    assert.equal(error.code, "permission-denied", `${method}: ${JSON.stringify(error)}`);
    assert.equal(error.reason, "CURATOR_REQUIRED");
  }
  assert.equal(appendCalls.length, 0, "затвор стоит до всякого вызова модуля");
  assert.equal(materializeCalls.length, 0);
});

test("curator call maps record.uid to body.ownerUid and passes expectedHeadVersion through", async () => {
  store.reset({"profiles/curator": {adminLevel: 100}});
  const result = await call("appendExamCorrection", "curator",
    {...CORRECTOR, expectedHeadVersion: 3});
  assert.equal(result.applied, true, JSON.stringify(result));
  assert.equal(appendCalls.length, 1);
  assert.equal(appendCalls[0].record.uid, "player", "владелец — body.ownerUid, не вызывающий");
  assert.equal(appendCalls[0].record.actorUid, "curator", "актёр — куратор");
  assert.equal(appendCalls[0].record.authorizedByCuratorUid, "curator");
  assert.equal(appendCalls[0].expectedHeadVersion, 3, "CAS-ожидание проходит как есть");
});

test("materializeExamCorrection invokes the module with the owner identity", async () => {
  store.reset({"profiles/curator": {adminLevel: 100}});
  const result = await call("materializeExamCorrection", "curator",
    {ownerUid: "player", mode: "ThemeTest", assessmentId: "theme"});
  assert.equal(result.published, true, JSON.stringify(result));
  assert.equal(materializeCalls.length, 1);
  assert.equal(materializeCalls[0].uid, "player");
  assert.equal(materializeCalls[0].mode, "ThemeTest");
  assert.equal(materializeCalls[0].assessmentId, "theme");
});

test("invalid arguments refuse with invalid-argument before the module is called", async () => {
  store.reset({"profiles/curator": {adminLevel: 100}});
  const base = {...CORRECTOR};
  for (const patch of [
    {mode: "OrdinaryLesson"},
    {mode: undefined},
    {ownerUid: ""},
    {ownerUid: 42},
    {assessmentId: ""},
    {sittingId: ""},
    {expectedHeadVersion: -1},
    {expectedHeadVersion: "3"},
  ]) {
    const error = await callError("appendExamCorrection", "curator", {...base, ...patch});
    assert.equal(error.code, "invalid-argument",
      `${JSON.stringify(patch)}: ${JSON.stringify(error)}`);
    const error2 = await callError("materializeExamCorrection", "curator",
      {ownerUid: "player", mode: patch.mode === undefined ? undefined : patch.mode,
        assessmentId: patch.assessmentId === undefined ? "theme" : patch.assessmentId});
    if (patch.mode !== undefined || patch.assessmentId !== undefined) {
      // Проверяем те же поля на втором вызове: неизвестный режим/пустой владелец/оценка.
      if (patch.mode === "OrdinaryLesson" || patch.mode === undefined ||
          patch.ownerUid !== undefined || patch.assessmentId !== undefined) {
        assert.equal(error2.code, "invalid-argument", `materialize ${JSON.stringify(patch)}`);
      }
    }
  }
  // Ни один невалидный вызов не дошёл до модуля.
  assert.equal(appendCalls.length, 1, "после валидного вызова выше новых вызовов нет");
});
