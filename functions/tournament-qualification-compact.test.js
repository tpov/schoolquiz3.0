"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {calculateTournamentLeaderboard} = require("./tournament-ranking");
const {VERSIONS, MAX_PAGE_PLAYERS, playerCursorKey, createReduction, reducePage, solve, projectPlayer,
  compareEntries, createTop16, mergeTop16, finishTop16} = require("./tournament-qualification-compact");

const forms = ["round-a", "round-b", "round-c"];
const hash = value => createHash("sha256").update(JSON.stringify(value)).digest("hex");
const roundtrip = value => JSON.parse(JSON.stringify(value));
function player(uid, scores, elapsed = 100) {
  return {userId: uid, results: scores.flatMap((percent, i) => percent === null ? [] : [{formId: forms[i], percent,
    elapsedMs: Array.isArray(elapsed) ? elapsed[i] : elapsed,
    completedAtMs: 1000 + (Array.isArray(elapsed) ? elapsed[i] : elapsed)}])};
}
function prepare(rows) {
  return createReduction({formIds: forms, cohortFingerprint: hash(rows), expectedPlayers: rows.length,
    expectedRuns: rows.reduce((n, row) => n + row.results.length, 0)});
}
function reduce(rows, size = 17) {
  let state = prepare(rows);
  for (let i = 0; i < rows.length; i += size) state = roundtrip(reducePage(state, rows.slice(i, i + size)));
  return state;
}
function calculate(rows, size = 17) {
  const reduced = reduce(rows, size); const solved = roundtrip(solve(reduced)); let top = createTop16(solved);
  for (let i = 0; i < rows.length; i += size) top = roundtrip(mergeTop16(top, solved, rows.slice(i, i + size)));
  return {reduced, solved, top, finished: finishTop16(top), entries: rows.map(row => projectPlayer(solved, row)).filter(Boolean)};
}

// Independent certificate of the original objective: enumerate the pairwise graph and verify
// L*x=b and the zero-mean gauge by fraction cross-products. No compact masks or solver helpers.
const f = (n, d = 1n) => [BigInt(n), BigInt(d)];
const add = (a, b) => f(a[0] * b[1] + b[0] * a[1], a[1] * b[1]);
const sub = (a, b) => f(a[0] * b[1] - b[0] * a[1], a[1] * b[1]);
const eq = (a, b) => a[0] * b[1] === b[0] * a[1];
function roundByRemainder(value, scale) {
  const n = value[0] * BigInt(scale); const d = value[1];
  let units = n / d; let remainder = n % d;
  if (remainder < 0n) { units--; remainder += d; }
  return Number(units + (2n * remainder >= d ? 1n : 0n));
}
function originalPairwiseCertificate(rows, entries) {
  const byUid = new Map(entries.map(entry => [entry.userId, entry]));
  const edges = new Map(entries.map(entry => [entry.userId, new Set()]));
  const residual = new Map(entries.map(entry => [entry.userId, f(0)]));
  const rhs = new Map(entries.map(entry => [entry.userId, 0]));
  const groups = new Map(entries.map(entry => [entry.userId, 0]));
  const comparisons = new Map(entries.map(entry => [entry.userId, 0]));
  const x = uid => f(byUid.get(uid).exactRating.numerator, byUid.get(uid).exactRating.denominator);
  for (const formId of forms) {
    const group = rows.flatMap(row => row.results.filter(r => r.formId === formId).map(r => ({...r, uid: row.userId})));
    if (group.length < 2) continue;
    group.forEach(row => groups.set(row.uid, groups.get(row.uid) + 1));
    for (let i = 0; i < group.length; i++) for (let j = i + 1; j < group.length; j++) {
      const a = group[i]; const b = group[j]; const delta = sub(x(a.uid), x(b.uid));
      residual.set(a.uid, add(residual.get(a.uid), delta)); residual.set(b.uid, sub(residual.get(b.uid), delta));
      rhs.set(a.uid, rhs.get(a.uid) + a.percent - b.percent); rhs.set(b.uid, rhs.get(b.uid) + b.percent - a.percent);
      edges.get(a.uid).add(b.uid); edges.get(b.uid).add(a.uid);
      comparisons.set(a.uid, comparisons.get(a.uid) + 1); comparisons.set(b.uid, comparisons.get(b.uid) + 1);
    }
  }
  for (const [uid, residualValue] of residual) {
    assert.ok(eq(residualValue, f(rhs.get(uid))), `pairwise residual for ${uid}`);
    assert.equal(byUid.get(uid).uniqueOpponents, edges.get(uid).size);
    assert.equal(byUid.get(uid).groupsPlayed, groups.get(uid));
    assert.equal(byUid.get(uid).comparisons, String(comparisons.get(uid)));
    const entry = byUid.get(uid); const rating = x(uid);
    assert.equal(entry.ratingUnits, roundByRemainder(rating, 10_000));
    assert.equal(entry.ratingPoints, roundByRemainder(f(1000n * rating[1] + 10n * rating[0], rating[1]), 1));
  }
  const seen = new Set();
  for (const uid of edges.keys()) if (!seen.has(uid)) {
    const stack = [uid]; seen.add(uid); const component = [];
    while (stack.length) {
      const current = stack.pop(); component.push(current);
      for (const next of edges.get(current)) if (!seen.has(next)) { seen.add(next); stack.push(next); }
    }
    assert.ok(eq(component.reduce((total, next) => add(total, x(next)), f(0)), f(0)), "exact zero-mean component gauge");
    component.forEach(next => assert.equal(byUid.get(next).componentSize, component.length));
  }
}

test("zero players and singleton forms yield no fictional qualifiers", () => {
  assert.deepEqual(calculate([]).finished.ranking, []);
  const rows = [player("a", [80, null, null]), player("b", [null, 90, null]), player("c", [null, null, 100])];
  const result = calculate(rows);
  assert.equal(result.finished.verifiedRunCount, 3); assert.equal(result.finished.totalRanked, 0);
  assert.deepEqual(result.entries, []); assert.deepEqual(result.solved.components, []);
});
test("exact rational rounding fixes the approved 32-player tie without changing elapsed precedence", () => {
  const rows = Array.from({length: 32}, (_, i) => player(`u${String(i).padStart(2, "0")}`, [i === 0 ? 1 : 0, null, null], i === 31 ? 1 : 100));
  const result = calculate(rows, 7); const fast = result.entries.find(row => row.userId === "u31");
  assert.deepEqual(fast.exactRating, {numerator: "-1", denominator: "32"});
  assert.equal(fast.ratingUnits, -312); assert.equal(result.entries[0].ratingUnits, 9688);
  assert.deepEqual(result.finished.ranking.slice(0, 3), [{uid: "u00", place: 1}, {uid: "u31", place: 2}, {uid: "u01", place: 3}]);
  const legacy = calculateTournamentLeaderboard(forms.map(formId => ({groupId: formId,
    results: rows.flatMap(row => row.results.filter(r => r.formId === formId).map(r => ({...r, userId: row.userId})))})));
  assert.equal(legacy.leaderboard.find(row => row.userId === "u31").place, 32);
  assert.equal(result.finished.versions.roundingVersion, VERSIONS.roundingVersion);
  originalPairwiseCertificate(rows, result.entries);
});
test("rating-point half ties and negative rounded zero are canonical exact JSON", () => {
  const rows = Array.from({length: 20}, (_, i) => player(`p${String(i).padStart(2, "0")}`, [i ? 0 : 1, null, null]));
  const result = calculate(rows);
  assert.equal(result.entries[0].ratingPoints, 1010); assert.equal(result.entries[1].ratingPoints, 1000);
  const equal = calculate([player("a", [0, null, null]), player("b", [0, null, null])]);
  assert.equal(Object.is(equal.entries[0].ratingUnits, -0), false);
  assert.deepEqual(roundtrip(equal.entries), equal.entries);
});
test("disconnected components and an uncounted singleton retain their independent gauges", () => {
  const rows = [player("a", [95, null, null]), player("b", [15, null, null]), player("c", [null, 80, null]),
    player("d", [null, 20, null]), player("e", [null, null, 100])];
  const result = calculate(rows, 2); originalPairwiseCertificate(rows, result.entries);
  assert.equal(result.solved.components.length, 2); assert.equal(result.entries.length, 4);
  assert.equal(result.solved.components[0].minUserId, "a"); assert.equal(result.solved.components[1].minUserId, "c");
  assert.deepEqual(result.entries.map(row => row.ratingUnits), [400000, -400000, 300000, -300000]);
});
test("all seven membership masks satisfy independent pairwise equations and preserve missing rounds", () => {
  const rows = Array.from({length: 14}, (_, i) => player(`p${String(i).padStart(2, "0")}`,
    [0, 1, 2].map(g => ((i % 7 + 1) & (1 << g)) ? (i * 17 + g * 29) % 101 : null), i + 1));
  const result = calculate(rows, 3); originalPairwiseCertificate(rows, result.entries);
  assert.equal(result.solved.bins.length, 7); assert.equal(result.solved.components[0].groups.length, 3);
});
test("128 deterministic mixed cohorts satisfy exact objective and complete top16", () => {
  for (let seed = 1; seed <= 128; seed++) {
    let random = seed; const next = () => { random = (Math.imul(random, 1664525) + 1013904223) >>> 0; return random; };
    const rows = Array.from({length: 2 + seed % 19}, (_, i) => {
      const mask = 1 + next() % 7;
      return player(`u${String(i).padStart(3, "0")}`, [0, 1, 2].map(g => mask & (1 << g) ? next() % 101 : null), next() % 300);
    });
    const result = calculate(rows, 1 + seed % 13); originalPairwiseCertificate(rows, result.entries);
    assert.deepEqual(result.finished.ranking.map(row => row.uid), result.entries.sort(compareEntries).slice(0, 16).map(row => row.userId));
  }
});
test("elapsed only breaks rating/statistic ties; unknown elapsed remains behind measured", () => {
  const result = calculate([player("a", [50, 50, null], [0, 5, 0]), player("b", [50, 50, null], 0), player("c", [50, 50, null], [4, 5, 0])]);
  assert.equal(result.entries[0].averageElapsedMs, 5); assert.equal(result.entries[1].averageElapsedMs, null);
  assert.equal(result.entries[2].averageElapsedMs, 5);
  assert.deepEqual(result.finished.ranking.map(row => row.uid), ["a", "c", "b"]);
  const keys = {ratingUnits: 0, averagePercentUnits: 0, groupsPlayed: 1, uniqueOpponents: 1, averageElapsedMs: 1, userId: "z"};
  for (const field of ["ratingUnits", "averagePercentUnits", "groupsPlayed", "uniqueOpponents"]) {
    assert.ok(compareEntries({...keys, [field]: 1 + keys[field], averageElapsedMs: 100}, {...keys, userId: "a"}) < 0);
  }
});
test("complete player results can arrive in any round order with identical checkpoints", () => {
  const rows = [player("a", [0, 75, 90]), player("b", [100, 25, 10])];
  const reversed = rows.map(row => ({...row, results: [...row.results].reverse()}));
  const initial = prepare(rows);
  assert.deepEqual(reducePage(initial, rows), reducePage(initial, reversed));
});
test("private cursor encoding preserves UTF16 order and legacy Unicode component stability", () => {
  const ids = ["\uE000", "\u{1F600}", "é", "e\u0301", "a", "a\u0000"];
  assert.deepEqual([...ids].sort((a, b) => Buffer.compare(Buffer.from(playerCursorKey(a)), Buffer.from(playerCursorKey(b)))), [...ids].sort());
  assert.equal(playerCursorKey("a\u{1F600}"), "0061d83dde00");
  const rows = [player("é", [50, null, null]), player("ê", [50, null, null]),
    player("e\u0301", [null, 50, null]), player("e\u0302", [null, 50, null])].sort((a, b) => a.userId < b.userId ? -1 : 1);
  const result = calculate(rows);
  const legacy = calculateTournamentLeaderboard(forms.map(formId => ({groupId: formId,
    results: rows.flatMap(row => row.results.filter(r => r.formId === formId).map(r => ({...r, userId: row.userId})))})));
  assert.deepEqual(result.finished.ranking.map(row => row.uid), legacy.leaderboard.map(row => row.userId));
  result.entries.forEach(row => assert.equal(row.componentId, legacy.leaderboard.find(old => old.userId === row.userId).componentId));
});
test("page boundaries, JSON roundtrips and retried page callback do not change accumulators", () => {
  const rows = Array.from({length: 73}, (_, i) => player(`u${String(i).padStart(3, "0")}`, [i % 101, (2 * i) % 101, i % 2 ? null : 50]));
  const initial = prepare(rows); const before = structuredClone(initial); const page = rows.slice(0, 23);
  assert.deepEqual(reducePage(initial, page), reducePage(initial, page)); assert.deepEqual(initial, before);
  const small = calculate(rows, 1); const large = calculate(rows, MAX_PAGE_PLAYERS);
  for (const field of Object.keys(small.reduced)) assert.deepEqual(small.reduced[field], large.reduced[field], field);
  for (const field of Object.keys(small.solved)) assert.deepEqual(small.solved[field], large.solved[field], field);
  for (const field of Object.keys(small.top)) assert.deepEqual(small.top[field], large.top[field], field);
  const preTop = createTop16(small.solved); const saved = structuredClone(preTop);
  assert.deepEqual(mergeTop16(preTop, small.solved, page), mergeTop16(preTop, small.solved, page)); assert.deepEqual(preTop, saved);
});
test("incomplete reduction/projection and a replacement player cannot yield a complete ranking", () => {
  const rows = [player("a", [0, 0, 0]), player("b", [100, 100, 100])];
  assert.throws(() => solve(reducePage(prepare(rows), rows.slice(0, 1))), /Incomplete cohort/);
  const solution = solve(reduce(rows)); const top = createTop16(solution);
  assert.throws(() => finishTop16(mergeTop16(top, solution, rows.slice(0, 1))), /Incomplete or changed/);
  const replaced = [rows[0], {...rows[1], userId: "foreign"}];
  assert.throws(() => finishTop16(mergeTop16(top, solution, replaced)), /Incomplete or changed/);
  const changed = structuredClone(rows); changed[1].results[0].completedAtMs++;
  assert.throws(() => finishTop16(mergeTop16(top, solution, changed)), /Incomplete or changed/);
});
test("replaying an already committed cursor, unknown fields and malformed projection fail without mutation", () => {
  const rows = [player("a", [20, 30, 40]), player("b", [60, 70, 80])];
  const first = reducePage(prepare(rows), rows.slice(0, 1)); const saved = structuredClone(first);
  assert.throws(() => reducePage(first, rows.slice(0, 1)), /cursor/); assert.deepEqual(first, saved);
  for (const change of [row => { row.results[0].percent = 1.5; }, row => { row.results[0].weight = 2; },
    row => { row.results[1].formId = row.results[0].formId; }, row => { row.results[0].completedAtMs = 0; },
    row => { row.results[0].percent = 101; }, row => { row.userId = ""; }, row => { row.results = []; }]) {
    const malformed = structuredClone(rows[0]); change(malformed);
    assert.throws(() => reducePage(prepare(rows), [malformed]));
  }
  assert.throws(() => reducePage(prepare(rows), []), /Bounded/);
  assert.throws(() => reducePage(prepare(rows), Array(51).fill(rows[0])), /Bounded/);
  assert.throws(() => reducePage(prepare(rows), [...rows].reverse()), /cursor/);
});
test("checkpoint corruption or changed version is rejected at every public phase", () => {
  const rows = [player("a", [20, 30, 40]), player("b", [60, 70, 80])];
  const reduced = reduce(rows); const corrupt = structuredClone(reduced); corrupt.forms[0].count++;
  assert.throws(() => solve(corrupt), /Changed checkpoint/);
  const version = structuredClone(reduced); version.versions.roundingVersion = "float-again";
  assert.throws(() => solve(version), /Changed checkpoint/);
  const solution = solve(reduced); const badSolution = structuredClone(solution); badSolution.groupTotals[0].numerator = "999";
  assert.throws(() => projectPlayer(badSolution, rows[0]), /Changed checkpoint/);
  const top = createTop16(solution); const badTop = structuredClone(top); badTop.processedPlayers++;
  assert.throws(() => mergeTop16(badTop, solution, rows), /Changed checkpoint/);
  const another = calculate([player("a", [20, 30, 40]), player("b", [61, 70, 80])]).solved;
  assert.throws(() => mergeTop16(top, another, rows), /Different calculation/);
});
test("invalid frozen cohort contracts are rejected before any reduction", () => {
  const base = {formIds: forms, cohortFingerprint: hash([]), expectedPlayers: 1, expectedRuns: 1};
  for (const extra of [{formIds: ["a", "a", "c"]}, {cohortFingerprint: "unfrozen"}, {expectedPlayers: 2},
    {expectedRuns: 4}, {expectedRuns: -1}, {expectedPlayers: Number.MAX_SAFE_INTEGER + 1}]) {
    assert.throws(() => createReduction({...base, ...extra}));
  }
});
test("every unknown-elapsed result remains representable through JSON", () => {
  const result = calculate([player("a", [20, 50, 80], 0), player("b", [20, 50, 80], 0)]);
  result.entries.forEach(entry => assert.equal(entry.averageElapsedMs, null));
  assert.deepEqual(roundtrip(result.top), result.top);
  assert.deepEqual(result.finished.ranking, [{uid: "a", place: 1}, {uid: "b", place: 2}]);
});
for (const [players, rounds, label] of [[502, 1, "502 distinct players"], [501, 3, "1503 runs"],
  [4096, 1, "4096 players"], [4097, 1, "4097 players beyond prototype bound"]]) {
  test(`${label}: full input, bounded state and deterministic complete top16`, () => {
    const rows = Array.from({length: players}, (_, i) => player(`u${String(i).padStart(5, "0")}`,
      [0, 1, 2].map(g => g < rounds ? i % 101 : null), players - i));
    const start = performance.now(); const result = calculate(rows, 50);
    assert.equal(result.finished.totalRanked, players); assert.equal(result.finished.verifiedRunCount, players * rounds);
    assert.equal(result.top.entries.length, 16); assert.equal(result.reduced.bins.length, 7);
    assert.ok(result.solved.components.every(c => c.groups.length <= 3));
    assert.ok(Buffer.byteLength(JSON.stringify(result.reduced)) < 5000, "reduction state does not grow with cohort");
    assert.ok(Buffer.byteLength(JSON.stringify(result.top)) < 16000, "only top16 retained");
    const expected = [...rows].sort((a, b) => b.results[0].percent - a.results[0].percent ||
      a.results[0].elapsedMs - b.results[0].elapsedMs || a.userId.localeCompare(b.userId)).slice(0, 16).map(row => row.userId);
    assert.deepEqual(result.finished.ranking.map(row => row.uid), expected);
    const totalScore = rows.reduce((n, row) => n + row.results[0].percent, 0);
    for (const entry of result.entries) {
      const score = rows[Number(entry.userId.slice(1))].results[0].percent;
      assert.ok(eq(f(entry.exactRating.numerator, entry.exactRating.denominator), f(score * players - totalScore, players)));
    }
    console.log(JSON.stringify({case: label, players, runs: players * rounds, ms: Math.round(performance.now() - start),
      rssBytes: process.memoryUsage().rss, inputFingerprint: result.finished.inputFingerprint,
      outputFingerprint: hash(result.finished), reductionBytes: Buffer.byteLength(JSON.stringify(result.reduced))}));
  });
}
