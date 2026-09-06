"use strict";
const assert = require("node:assert/strict");
const {test} = require("node:test");
const {STATUS, attemptFingerprint, attemptReceipt, replayFault} = require("./attempt-receipt");
const event = {userId: "u1", attemptId: "a1", lessonId: "l1", lessonVersion: 1,
  scope: "public", codeAnswer: "91", percentScore: 50};

test("fingerprint ignores map ordering but retains owner, values and array order", () => {
  assert.equal(attemptFingerprint("u1", {b: 2, a: [1, 3]}), attemptFingerprint("u1", {a: [1, 3], b: 2}));
  assert.notEqual(attemptFingerprint("u1", {a: [1, 3]}), attemptFingerprint("u1", {a: [3, 1]}));
  assert.notEqual(attemptFingerprint("u1", event), attemptFingerprint("u2", event));
  assert.throws(() => attemptFingerprint("u1", {x: Infinity}));
});

test("settled receipt contains actual costs and reward, with a stable account-scoped id", () => {
  const receipt = attemptReceipt({event, status: STATUS.SETTLED, settledAtMs: 100,
    lifePointsSpent: 150, plasmaPointsSpent: 100, reward: {skillPoints: 2, nolics: 3}});
  assert.equal(receipt.uid, "u1");
  assert.equal(receipt.attemptId, "a1");
  assert.equal(receipt.lessonId, "l1");
  assert.equal(receipt.lessonVersion, 1);
  assert.equal(receipt.schemaVersion, 1);
  assert.equal(receipt.status, "SETTLED");
  assert.equal(receipt.completed, false);
  assert.equal(receipt.settledAtMs, 100);
  assert.equal(receipt.codeAnswer, "91");
  assert.equal(receipt.percentScore, 50);
  assert.equal(receipt.lifePointsSpent, 150);
  assert.equal(receipt.plasmaPointsSpent, 100);
  assert.deepEqual(receipt.reward, {skillPoints: 2, nolics: 3});
  assert.equal(receipt.receiptId, attemptReceipt({event, status: STATUS.SETTLED, settledAtMs: 200}).receiptId);
  assert.notEqual(receipt.receiptId, attemptReceipt({event: {...event, userId: "u2"},
    status: STATUS.SETTLED, settledAtMs: 100}).receiptId);
});

test("unknown, rejected and legacy scores are null, never synthetic zero", () => {
  for (const status of [STATUS.UNSCORABLE, STATUS.REJECTED, STATUS.LEGACY]) {
    const receipt = attemptReceipt({event, status, settledAtMs: 100});
    assert.equal(receipt.codeAnswer, null);
    assert.equal(receipt.percentScore, null);
    assert.equal(receipt.lifePointsSpent, 0);
    assert.deepEqual(receipt.reward, {skillPoints: 0, nolics: 0});
    assert.throws(() => attemptReceipt({event, status, settledAtMs: 100, plasmaPointsSpent: 1}));
  }
});

test("settled score and exact integer accounting are validated", () => {
  assert.throws(() => attemptReceipt({event: {...event, percentScore: 100}, status: STATUS.SETTLED, settledAtMs: 1}));
  assert.throws(() => attemptReceipt({event, status: STATUS.SETTLED, settledAtMs: 1, lifePointsSpent: 0.5}));
});

test("replay cannot change owner, lesson, version, scope or first payload", () => {
  const stored = {...event, requestFingerprint: "same"};
  assert.equal(replayFault(stored, event, "same"), null);
  assert.equal(replayFault(stored, {...event, userId: "u2"}, "same"), "OWNER_MISMATCH");
  for (const [key, value] of [["lessonId", "l2"], ["lessonVersion", 2], ["scope", "private"]]) {
    assert.equal(replayFault(stored, {...event, [key]: value}, "same"), "ATTEMPT_MISMATCH");
  }
  assert.equal(replayFault(stored, event, "changed"), "PAYLOAD_MISMATCH");
});
