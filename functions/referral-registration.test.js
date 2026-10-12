"use strict";

const assert = require("assert/strict");
const {referralFields, referralRegistration} = require("./referral-registration");

const T = 1_800_000_000_000;
const tests = [];
function test(name, body) { tests.push({name, body}); }

test("an absent referrer is optional and stays absent", () => {
  assert.equal(referralRegistration({uid: "me", referredByUid: undefined}), null);
  assert.equal(referralRegistration({uid: "me", referredByUid: null}), null);
  assert.equal(referralRegistration({uid: "me", referredByUid: ""}), null);
  assert.deepEqual(referralFields({uid: "me", referredByUid: undefined, inviterExists: true, now: T}), {});
});

test("a malformed referrer or self-invitation is a loud refusal", () => {
  for (const bad of [42, {}, [], "has/slash", ".", "..", "a".repeat(129)]) {
    assert.throws(() => referralRegistration({uid: "me", referredByUid: bad}), {code: "INVALID_ARGUMENT"});
  }
  assert.throws(
    () => referralRegistration({uid: "same-uid", referredByUid: "same-uid"}),
    {code: "INVALID_ARGUMENT"},
    "самоприглашение — громкий отказ, а не тихое поле");
  assert.throws(
    () => referralFields({uid: "me", referredByUid: "boss", inviterExists: true, now: "not-a-time"}),
    {code: "INVALID_ARGUMENT"});
});

test("a live referrer gives the direct one-level fields, a dead one gives nothing", () => {
  assert.deepEqual(
    referralFields({uid: "me", referredByUid: "boss", inviterExists: true, now: T}),
    {referred: true, referredBy: "boss", referredAtMs: T},
    "прямой уровень, без цепочки");
  assert.deepEqual(
    referralFields({uid: "me", referredByUid: "ghost", inviterExists: false, now: T}),
    {},
    "мёртвая ссылка не запирает новому игроку вход");
  assert.deepEqual(
    referralFields({uid: "me", referredByUid: "boss", inviterExists: undefined, now: T}), {});
});

(async () => {
  let failed = 0;
  for (const {name, body} of tests) {
    try {
      await body();
      console.log(`ok - ${name}`);
    } catch (error) {
      failed += 1;
      console.error(`NOT OK - ${name}`);
      console.error(error);
    }
  }
  console.log(`${tests.length - failed}/${tests.length} tests passed`);
  if (failed > 0) process.exitCode = 1;
})();
