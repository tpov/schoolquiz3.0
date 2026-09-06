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
const uid = "box-player";
function world() {
  const now = Date.now();
  return {[`users/${uid}`]: {uid, nickname: "BoxPlayer", boxCount: 2, boxStreakDays: 2,
    nextBoxAtMs: now + 86_400_000, gold: 0, nolics: 0, pointsNolics: 0}, [`profiles/${uid}`]: {uid}};
}
function call(name, data, owner = uid) { return functions[name].run({auth: {uid: owner}, data}); }

test("real opening requires persistent owner-bound id and never trusts client reward", async () => {
  store.reset(world());
  await assert.rejects(call("openGiftBox", {}), {code: "permission-denied"});
  assert.equal(store.writes.length, 0);
  await assert.rejects(call("openGiftBox", {ownerUid: uid}), {code: "invalid-argument"});
  const intent = {ownerUid: uid, openingId: "opening-id-00000001", reward: {type: "addGold", amount: 999_999_999},
    entropy: Array(8).fill(0)};
  const opened = await call("openGiftBox", intent);
  assert.equal(opened.status, "OPENED");
  assert.equal(opened.ownerUid, uid);
  assert.equal(opened.openedBoxes, 1);
  assert.ok(opened.reward.amount < 999_999_999);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
  const after = structuredClone([...store.documents]);
  // Simulate a lost response: the client keeps its original id and asks again.
  assert.deepEqual(await call("openGiftBox", intent), opened);
  assert.deepEqual(structuredClone([...store.documents]), after);
  await assert.rejects(call("openGiftBox", intent, "other-account"), {code: "permission-denied"});
  await assert.rejects(call("openGiftBox", {...intent, ownerUid: "other-account"}, "other-account"), {code: "permission-denied"});
});

test("opening failure rolls back box, reward, premium, ledger and receipt together", async () => {
  store.reset(world());
  const before = structuredClone([...store.documents]);
  const intent = {ownerUid: uid, openingId: "opening-id-00000002"};
  store.fail = (kind, path) => kind === "commit" && path.startsWith("box_operations/") ? "commit failed" : null;
  await assert.rejects(call("openGiftBox", intent), /commit failed/);
  assert.deepEqual(structuredClone([...store.documents]), before);
  store.fail = null;
  assert.equal((await call("openGiftBox", intent)).status, "OPENED");
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
});

test("queued opening and direct recovery share the permanent financial receipt", async () => {
  store.reset(world());
  const payload = {ownerUid: uid, openingId: "opening-id-00000003"};
  const direct = await call("openGiftBox", payload);
  const response = await call("submitMutation", {mutationId: "economy-OPEN_GIFT_BOX-opening-id-00000003",
    operation: "economy.OPEN_GIFT_BOX", payload: JSON.stringify(payload)});
  assert.deepEqual(response.result, direct);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 1);
});

test("profile refresh preserves offline anchor; daily premium is a separate once-per-day server claim", async () => {
  store.reset(world());
  const original = store.documents.get(`users/${uid}`);
  store.documents.set(`users/${uid}`, {...original, nextBoxAtMs: Date.now() - 1000,
    boxStreakDays: 9, premiumUntilMs: Date.now() + 86_400_000});
  const anchor = store.documents.get(`users/${uid}`).nextBoxAtMs;
  await call("ensureUserProfile", {});
  assert.equal(store.documents.get(`users/${uid}`).nextBoxAtMs, anchor);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 2);
  const daily = await call("submitGiftBoxOperation", {kind: "PREMIUM_DAILY", ownerUid: uid, operationId: "daily-id-0000000001"});
  assert.equal(daily.status, "CLAIMED");
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 3);
  assert.equal(store.documents.get(`users/${uid}`).nextBoxAtMs, anchor);
  const again = await call("submitGiftBoxOperation", {kind: "PREMIUM_DAILY", ownerUid: uid, operationId: "daily-id-0000000002"});
  assert.equal(again.status, "DAILY_LIMIT");
  const visit = await call("submitGiftBoxOperation", {kind: "VISIT", ownerUid: uid, operationId: "visit-id-0000000001"});
  assert.equal(visit.earnedBoxes, 1);
  assert.equal(store.documents.get(`users/${uid}`).boxCount, 4);
});
