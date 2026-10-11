"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const {AD_REWARD_CALLBACKS_COLLECTION} = require("./ad-reward-progress");

const UID = "box-player";
const URL_BASE = "https://us-central1-school-quiz-89336951.cloudfunctions.net/adMobRewardConfirmed";

/** Подделка ответа v2 onRequest: запоминает статус и тело, ничего не пишет в сеть. */
function fakeResponse() {
  return {
    statusCode: 0,
    body: null,
    status(code) { this.statusCode = code; return this; },
    send(body) { this.body = body; return this; },
  };
}

test("the endpoint answers 200 and records an unusable row without touching the key server", async () => {
  store.reset({});
  const response = fakeResponse();
  await functions.adMobRewardConfirmed({url: `${URL_BASE}?ad_unit=unit&reward_item=coin`}, response);
  assert.equal(response.statusCode, 200);
  assert.equal(response.body, "OK");
  // Путь нечитаемого запроса короче пути ключей: fetchKeys не вызывался, сеть не тронута.
  const rows = [...store.documents.values()].filter((value) => value && value.verdict === "UNUSABLE");
  assert.equal(rows.length, 1);
  assert.equal(rows[0].reason, "UNPARSEABLE");
});

test("a request without a query at all is acknowledged junk, not a crash", async () => {
  store.reset({});
  const response = fakeResponse();
  await functions.adMobRewardConfirmed({url: URL_BASE}, response);
  assert.equal(response.statusCode, 200);
  assert.equal([...store.documents.values()].filter((value) => value && value.verdict === "UNUSABLE").length, 1);
});
