"use strict";

const assert = require("node:assert/strict");
const {
  DAY_MS, PREMIUM_DAYS, SOURCE, LEGACY_GRANT_ID,
  durationMsForDays, createPremiumState, advancePremium, issuePremium,
  refundPlayPremium, bootstrapLegacyPremium, regeneratePremiumPoints,
} = require("./premium-grants");

const T0 = 1_700_000_000_000;
const tests = [];
function test(name, run) { tests.push({name, run}); }
function request(id, source = SOURCE.PLAY, days = 1) {
  return {grantId: id, sourceId: `receipt:${id}`, source, days};
}
function issue(state, id, source = SOURCE.PLAY, days = 1, now = state.asOfMs) {
  return issuePremium(state, request(id, source, days), now);
}
function fresh(now = T0) { return createPremiumState(now); }
function lot(state, id) { return state.lots.find((entry) => entry.grantId === id); }
function fails(code, callback) { assert.throws(callback, (error) => error.code === code); }
function roundTrip(value) { return JSON.parse(JSON.stringify(value)); }

test("all four catalogue durations grant exact milliseconds for PLAY, GOLD and BOX", () => {
  for (const source of [SOURCE.PLAY, SOURCE.GOLD, SOURCE.BOX]) {
    for (const days of PREMIUM_DAYS) {
      const result = issue(fresh(), "first", source, days);
      assert.equal(result.entitlement.remainingMs, days * DAY_MS);
      assert.equal(result.entitlement.premiumUntilMs, T0 + days * DAY_MS);
      assert.equal(result.entitlement.hasPremium, true);
      assert.equal(lot(result.state, "first").sourceId, "receipt:first");
      assert.equal(lot(result.state, "first").issuedAtMs, T0);
    }
  }
});

test("expiry is inactive at its exact boundary, even if persistence has a stale true flag", () => {
  const state = issue(fresh(), "p").state;
  state.hasPremium = true;
  assert.equal(advancePremium(state, T0 + DAY_MS - 1).entitlement.remainingMs, 1);
  const expired = advancePremium(state, T0 + DAY_MS);
  assert.equal(expired.entitlement.hasPremium, false);
  assert.equal(expired.entitlement.premiumUntilMs, 0);
  assert.equal(lot(expired.state, "p").consumedMs, DAY_MS);
  assert.deepEqual(advancePremium(expired.state, T0 + 2 * DAY_MS).elapsed.activeIntervals, []);
});

test("FIFO consumes issue order rather than lexical grant ids, including simultaneous grants", () => {
  let state = issue(fresh(), "z-first").state;
  state = issue(state, "a-second", SOURCE.BOX, 7).state;
  const next = advancePremium(state, T0 + DAY_MS + 123);
  assert.equal(lot(next.state, "z-first").consumedMs, DAY_MS);
  assert.equal(lot(next.state, "a-second").consumedMs, 123);
  assert.equal(next.entitlement.premiumUntilMs, T0 + 8 * DAY_MS);
});

test("issuing while active extends only after elapsed time has been consumed", () => {
  const state = issue(fresh(), "p", SOURCE.PLAY, 7).state;
  const next = issue(state, "gift", SOURCE.BOX, 1, T0 + DAY_MS);
  assert.equal(lot(next.state, "p").consumedMs, DAY_MS);
  assert.equal(next.entitlement.remainingMs, 7 * DAY_MS);
  assert.equal(next.entitlement.premiumUntilMs, T0 + 8 * DAY_MS);
});

test("issuing after an inactive gap starts now without backdating premium", () => {
  const old = issue(fresh(), "old").state;
  const next = issue(old, "new", SOURCE.GOLD, 1, T0 + 3 * DAY_MS);
  assert.deepEqual(next.elapsed.activeIntervals, [{fromMs: T0, toMs: T0 + DAY_MS}]);
  assert.equal(next.entitlement.premiumUntilMs, T0 + 4 * DAY_MS);
  assert.equal(lot(next.state, "new").consumedMs, 0);
});

test("grant replay advances time but never adds entitlement, including after expiry", () => {
  const initial = issue(fresh(), "p").state;
  const replay = issue(initial, "p", SOURCE.PLAY, 1, T0 + 123);
  assert.equal(replay.outcome, "REPLAYED");
  assert.equal(replay.state.lots.length, 1);
  assert.equal(replay.entitlement.remainingMs, DAY_MS - 123);
  const late = issue(replay.state, "p", SOURCE.PLAY, 1, T0 + 5 * DAY_MS);
  assert.equal(late.entitlement.hasPremium, false);
  assert.equal(late.state.lots.length, 1);
});

test("conflicting replay and same source with a changed grant id are refused", () => {
  const state = issue(fresh(), "p").state;
  const before = roundTrip(state);
  for (const change of [{days: 7}, {source: SOURCE.GOLD}, {sourceId: "other"}]) {
    fails("CONFLICTING_REPLAY", () => issuePremium(state, {...request("p"), ...change}, T0 + 1));
  }
  fails("SOURCE_ALREADY_GRANTED", () => issuePremium(state, {...request("other"), sourceId: "receipt:p"}, T0));
  assert.deepEqual(state, before, "failed transitions do not mutate caller state");
});

test("partial PLAY refund preserves used time and every other gift or purchase", () => {
  let state = issue(fresh(), "play", SOURCE.PLAY, 7).state;
  state = issue(state, "box", SOURCE.BOX, 1).state;
  state = issue(state, "gold", SOURCE.GOLD, 30).state;
  state = issue(state, "other-play", SOURCE.PLAY, 365).state;
  const refunded = refundPlayPremium(state, "play", T0 + 2 * DAY_MS);
  assert.equal(refunded.revokedNowMs, 5 * DAY_MS);
  assert.equal(lot(refunded.state, "play").consumedMs, 2 * DAY_MS);
  assert.equal(lot(refunded.state, "play").revokedMs, 5 * DAY_MS);
  for (const id of ["box", "gold", "other-play"]) assert.deepEqual(lot(refunded.state, id), lot(state, id));
  assert.equal(refunded.entitlement.remainingMs, 396 * DAY_MS);
  assert.deepEqual(refunded.elapsed.activeIntervals, [{fromMs: T0, toMs: T0 + 2 * DAY_MS}]);
  assert.equal("debt" in refunded, false);
});

test("refunding a queued PLAY lot keeps the currently consumed gift untouched", () => {
  let state = issue(fresh(), "gift", SOURCE.BOX, 7).state;
  state = issue(state, "play", SOURCE.PLAY, 30).state;
  const refunded = refundPlayPremium(state, "play", T0 + DAY_MS);
  assert.equal(refunded.revokedNowMs, 30 * DAY_MS);
  assert.equal(lot(refunded.state, "gift").consumedMs, DAY_MS);
  assert.equal(refunded.entitlement.remainingMs, 6 * DAY_MS);
});

test("refund after complete use cannot take time from a later grant", () => {
  let state = issue(fresh(), "old").state;
  state = issue(state, "gift", SOURCE.BOX, 7).state;
  const refunded = refundPlayPremium(state, "old", T0 + 2 * DAY_MS);
  assert.equal(refunded.revokedNowMs, 0);
  assert.equal(lot(refunded.state, "old").consumedMs, DAY_MS);
  assert.equal(lot(refunded.state, "gift").consumedMs, DAY_MS);
  assert.equal(refunded.entitlement.remainingMs, 6 * DAY_MS);
});

test("duplicate refund and purchase replay after refund never revoke or grant twice", () => {
  const state = issue(fresh(), "p").state;
  const first = refundPlayPremium(state, "p", T0 + 10);
  const duplicate = refundPlayPremium(first.state, "p", T0 + 20);
  assert.equal(duplicate.outcome, "REPLAYED");
  assert.equal(duplicate.revokedNowMs, 0);
  assert.equal(lot(duplicate.state, "p").refundedAtMs, T0 + 10);
  assert.equal(lot(duplicate.state, "p").revokedMs, DAY_MS - 10);
  const retryPurchase = issue(duplicate.state, "p", SOURCE.PLAY, 1, T0 + 30);
  assert.equal(retryPurchase.outcome, "REPLAYED");
  assert.equal(retryPurchase.entitlement.hasPremium, false);
});

test("unknown and non-PLAY refunds fail closed without touching state", () => {
  for (const source of [SOURCE.GOLD, SOURCE.BOX]) {
    const state = issue(fresh(), "gift", source).state;
    fails("NOT_PLAY_GRANT", () => refundPlayPremium(state, "gift", T0 + 1));
    fails("GRANT_NOT_FOUND", () => refundPlayPremium(state, "unknown", T0 + 1));
    assert.equal(lot(state, "gift").consumedMs, 0);
  }
});

test("backwards clocks do not consume twice, extend expiry or resurrect expired premium", () => {
  const issued = issue(fresh(), "p").state;
  const advanced = advancePremium(issued, T0 + 100);
  const backwards = advancePremium(advanced.state, T0 - 100);
  assert.deepEqual(backwards.state, advanced.state);
  assert.deepEqual(backwards.elapsed, {fromMs: T0 + 100, toMs: T0 + 100, activeIntervals: []});
  const gift = issue(backwards.state, "gift", SOURCE.BOX, 1, T0 - 500);
  assert.equal(lot(gift.state, "gift").issuedAtMs, T0 + 100);
  assert.equal(gift.entitlement.premiumUntilMs, T0 + 2 * DAY_MS);
  const expired = advancePremium(issued, T0 + 2 * DAY_MS);
  assert.equal(advancePremium(expired.state, T0).entitlement.hasPremium, false);
});

test("LEGACY bootstrap preserves exactly the future suffix, once, with stable provenance", () => {
  const first = bootstrapLegacyPremium(fresh(), {premiumUntilMs: T0 + 12345, hasPremium: false}, T0);
  const legacy = lot(first.state, LEGACY_GRANT_ID);
  assert.equal(legacy.source, SOURCE.LEGACY);
  assert.equal(legacy.sourceId, LEGACY_GRANT_ID);
  assert.equal(legacy.durationMs, 12345);
  const replay = bootstrapLegacyPremium(first.state, {premiumUntilMs: T0 + 365 * DAY_MS}, T0 + 100);
  assert.equal(replay.outcome, "REPLAYED");
  assert.equal(replay.entitlement.remainingMs, 12245);
  assert.equal(replay.state.lots.length, 1);
  fails("NOT_PLAY_GRANT", () => refundPlayPremium(first.state, LEGACY_GRANT_ID, T0));
});

test("absent/expired legacy timestamp and sticky true never create premium, including migration replay", () => {
  for (const premiumUntilMs of [undefined, null, 0, T0 - 1, T0]) {
    const first = bootstrapLegacyPremium(fresh(), {premiumUntilMs, hasPremium: true}, T0);
    assert.equal(first.entitlement.hasPremium, false);
    assert.equal(first.state.lots.length, 0);
    assert.notEqual(first.state.legacyBootstrap, null);
    const replay = bootstrapLegacyPremium(first.state, {premiumUntilMs: T0 + DAY_MS}, T0);
    assert.equal(replay.entitlement.hasPremium, false);
  }
});

test("migration runs before new grants and cannot re-import the new expiry mirror", () => {
  const migrated = bootstrapLegacyPremium(fresh(), {premiumUntilMs: T0 + 100}, T0).state;
  const bought = issue(migrated, "play").state;
  const retried = bootstrapLegacyPremium(bought, {premiumUntilMs: T0 + DAY_MS + 100}, T0);
  assert.equal(retried.entitlement.remainingMs, DAY_MS + 100);
  fails("LEGACY_MUST_BE_FIRST", () => bootstrapLegacyPremium(issue(fresh(), "p").state, {premiumUntilMs: T0 + DAY_MS}, T0));
  fails("INVALID_ARGUMENT", () => issue(fresh(), "legacy", SOURCE.LEGACY));
  fails("INVALID_ARGUMENT", () => issue(fresh(), LEGACY_GRANT_ID));
});

test("JSON round-trip retains every lot field, refunded marker, migration and replay behaviour", () => {
  let state = bootstrapLegacyPremium(fresh(), {premiumUntilMs: T0 + 2}, T0).state;
  state = issue(state, "p", SOURCE.PLAY, 7).state;
  state = refundPlayPremium(state, "p", T0 + 9).state;
  const restored = roundTrip(state);
  for (const field of ["schemaVersion", "asOfMs"]) assert.equal(restored[field], state[field]);
  for (const field of ["atMs", "premiumUntilMs", "grantId"]) assert.equal(restored.legacyBootstrap[field], state.legacyBootstrap[field]);
  for (let i = 0; i < state.lots.length; i++) {
    for (const field of ["grantId", "source", "sourceId", "issuedAtMs", "durationMs", "consumedMs", "revokedMs", "refundedAtMs"]) {
      assert.equal(restored.lots[i][field], state.lots[i][field]);
    }
  }
  assert.equal(issue(restored, "p", SOURCE.PLAY, 7).outcome, "REPLAYED");
  assert.equal(refundPlayPremium(restored, "p", T0 + 9).outcome, "REPLAYED");
});

test("invalid input, malformed persistence and overflow fail rather than coerce", () => {
  for (const bad of [-1, 0.5, NaN, Infinity, "1", Number.MAX_SAFE_INTEGER + 1]) {
    fails("INVALID_ARGUMENT", () => createPremiumState(bad));
  }
  // Целые сутки любого срока законны: подписка продаёт срок до даты Play (история 2.1), триал
  // даёт лот не из товарного набора. Незаконны — нецелые, нулевые и отрицательные.
  for (const bad of [0, 1.5, "7", -30]) fails("INVALID_ARGUMENT", () => durationMsForDays(bad));
  for (const good of [1, 2, 3, 7, 30, 365]) {
    if (durationMsForDays(good) !== good * 86_400_000) throw new Error(`days ${good} mis-multiplied`);
  }
  fails("OVERFLOW", () => issue(fresh(Number.MAX_SAFE_INTEGER - 1), "p"));
  const state = issue(fresh(), "p").state;
  for (const mutate of [
    (s) => { s.lots[0].consumedMs = DAY_MS + 1; },
    (s) => { s.lots[0].revokedMs = 1; },
    (s) => { s.lots.push({...s.lots[0]}); },
    (s) => { s.lots[0].issuedAtMs = T0 + 1; },
    (s) => { s.schemaVersion = 2; },
    (s) => { delete s.lots[0].refundedAtMs; },
  ]) {
    const broken = roundTrip(state);
    mutate(broken);
    fails("INVALID_STATE", () => advancePremium(broken, T0));
  }
});

test("regeneration weights actual premium before expiry, not today's false status", () => {
  const migrated = bootstrapLegacyPremium(fresh(0), {premiumUntilMs: 10}, 0).state;
  const advanced = advancePremium(migrated, 30);
  assert.equal(advanced.entitlement.hasPremium, false);
  assert.deepEqual(regeneratePremiumPoints({points: 0, updatedAtMs: 0, remainderMs: 0}, advanced.elapsed, 100, 10),
    {points: 4, updatedAtMs: 30, remainderMs: 0});
});

test("persistence cannot claim elapsed use before issue or out of FIFO order", () => {
  let state = issue(fresh(), "first").state;
  state = issue(state, "second", SOURCE.BOX).state;
  const outOfOrder = roundTrip(state);
  outOfOrder.asOfMs += 10;
  outOfOrder.lots[1].consumedMs = 1;
  fails("INVALID_STATE", () => advancePremium(outOfOrder, outOfOrder.asOfMs));
  const beforeIssue = roundTrip(state);
  beforeIssue.lots[0].consumedMs = 1;
  fails("INVALID_STATE", () => advancePremium(beforeIssue, T0));
  const migrated = bootstrapLegacyPremium(fresh(), {premiumUntilMs: T0 + 5}, T0).state;
  migrated.lots[0].sourceId = "not-the-migration";
  fails("INVALID_STATE", () => advancePremium(migrated, T0));
});

test("identifiers are mandatory and state is immutable on successful transitions", () => {
  for (const bad of [undefined, null, 1, "", " ", " padded", "x".repeat(1025)]) {
    fails("INVALID_ARGUMENT", () => issuePremium(fresh(), {...request("p"), grantId: bad}, T0));
    fails("INVALID_ARGUMENT", () => issuePremium(fresh(), {...request("p"), sourceId: bad}, T0));
  }
  const initial = issue(fresh(), "p").state;
  Object.freeze(initial.lots[0]);
  Object.freeze(initial.lots);
  Object.freeze(initial);
  const advanced = advancePremium(initial, T0 + 100);
  assert.equal(lot(initial, "p").consumedMs, 0);
  assert.equal(lot(advanced.state, "p").consumedMs, 100);
  assert.equal(issue(initial, "gift", SOURCE.BOX).state.lots.length, 2);
  assert.equal(refundPlayPremium(initial, "p", T0).entitlement.hasPremium, false);
});

test("new premium never doubles the preceding non-premium idle gap", () => {
  const issued = issue(fresh(0), "p", SOURCE.PLAY, 1, 20);
  let balance = regeneratePremiumPoints({points: 0, updatedAtMs: 0, remainderMs: 0}, issued.elapsed, 100, 10);
  assert.equal(balance.points, 2);
  const next = advancePremium(issued.state, 30);
  balance = regeneratePremiumPoints(balance, next.elapsed, 100, 10);
  assert.equal(balance.points, 4);
});

test("refund settles old premium benefit first, and following time is normal rate", () => {
  const issued = issue(fresh(0), "p");
  const refunded = refundPlayPremium(issued.state, "p", 5);
  let balance = regeneratePremiumPoints({points: 0, updatedAtMs: 0, remainderMs: 0}, refunded.elapsed, 100, 10);
  assert.equal(balance.points, 1);
  balance = regeneratePremiumPoints(balance, advancePremium(refunded.state, 15).elapsed, 100, 10);
  assert.equal(balance.points, 2);
});

test("fractional point work survives rate boundaries and is partition invariant", () => {
  const initial = bootstrapLegacyPremium(fresh(0), {premiumUntilMs: 9}, 0).state;
  const one = advancePremium(initial, 25);
  const balance = {points: 0, updatedAtMs: 0, remainderMs: 0};
  const whole = regeneratePremiumPoints(balance, one.elapsed, 100, 7);
  const a = advancePremium(initial, 4);
  const b = advancePremium(a.state, 11);
  const c = advancePremium(b.state, 25);
  let split = balance;
  for (const part of [a, b, c]) split = regeneratePremiumPoints(split, part.elapsed, 100, 7);
  assert.deepEqual(split, whole);
  assert.deepEqual(split, {points: 4, updatedAtMs: 25, remainderMs: 6});
});

test("cap never confiscates owned points or banks excess time; disabled regen accrues nothing", () => {
  const elapsed = {fromMs: 0, toMs: 100, activeIntervals: [{fromMs: 0, toMs: 100}]};
  assert.deepEqual(regeneratePremiumPoints({points: 12, updatedAtMs: 0, remainderMs: 0}, elapsed, 10, 10),
    {points: 12, updatedAtMs: 100, remainderMs: 0});
  assert.deepEqual(regeneratePremiumPoints({points: 9, updatedAtMs: 0, remainderMs: 5}, elapsed, 10, 10),
    {points: 10, updatedAtMs: 100, remainderMs: 0});
  assert.deepEqual(regeneratePremiumPoints({points: 1, updatedAtMs: 0, remainderMs: 5}, elapsed, 10, 0),
    {points: 1, updatedAtMs: 100, remainderMs: 0});
});

test("regen rejects gaps, overlapping intervals and incompatible cursors", () => {
  const balance = {points: 0, updatedAtMs: 0, remainderMs: 0};
  fails("CURSOR_MISMATCH", () => regeneratePremiumPoints(balance, {fromMs: 1, toMs: 10, activeIntervals: []}, 10, 10));
  for (const activeIntervals of [
    [{fromMs: 0, toMs: 11}],
    [{fromMs: 0, toMs: 5}, {fromMs: 4, toMs: 6}],
    [{fromMs: 5, toMs: 5}],
  ]) fails("INVALID_ARGUMENT", () => regeneratePremiumPoints(balance, {fromMs: 0, toMs: 10, activeIntervals}, 10, 10));
  fails("INVALID_ARGUMENT", () => regeneratePremiumPoints({...balance, remainderMs: 10}, {fromMs: 0, toMs: 10, activeIntervals: []}, 10, 10));
});

test("large elapsed work uses exact arithmetic without serialising BigInt", () => {
  const end = Number.MAX_SAFE_INTEGER;
  const elapsed = {fromMs: 0, toMs: end, activeIntervals: [{fromMs: 0, toMs: end}]};
  const balance = regeneratePremiumPoints({points: 0, updatedAtMs: 0, remainderMs: 0}, elapsed, end, end);
  assert.deepEqual(balance, {points: 2, updatedAtMs: end, remainderMs: 0});
  assert.deepEqual(roundTrip(balance), balance);
});

test("advance and regeneration are invariant across many deterministic partitions", () => {
  for (let seed = 1; seed <= 40; seed++) {
    const duration = 1 + seed * 11;
    const end = 1000 + seed;
    let state = bootstrapLegacyPremium(fresh(0), {premiumUntilMs: duration}, 0).state;
    const once = advancePremium(state, end);
    const initialBalance = {points: 0, updatedAtMs: 0, remainderMs: 0};
    const expected = regeneratePremiumPoints(initialBalance, once.elapsed, 10000, 17);
    let balance = initialBalance;
    let time = 0;
    while (time < end) {
      time = Math.min(end, time + 1 + ((time + seed * 31) % 97));
      const part = advancePremium(state, time);
      balance = regeneratePremiumPoints(balance, part.elapsed, 10000, 17);
      state = part.state;
    }
    assert.deepEqual(state, once.state);
    assert.deepEqual(balance, expected);
  }
});

for (const {name, run} of tests) {
  try { run(); } catch (error) { error.message = `${name}: ${error.message}`; throw error; }
}
console.log(`premium-grants: ${tests.length} behavioural tests passed`);
