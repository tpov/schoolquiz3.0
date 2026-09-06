"use strict";

const assert = require("node:assert/strict");
const {buildQualificationRankingSnapshot: build} = require("./tournament-qualification");
const {calculateTournamentLeaderboard, ALGORITHM_VERSION} = require("./tournament-ranking");
const forms = [1, 2, 3].map(n => ({formId: `form-${n}`, fingerprint: `public-fingerprint-${n}`, maxDurationMs: 1200}));
const eventId = "event-for-ranking-test";
const endsAtMs = 100_000;
const row = (uid, form, percentScore, i = 0) => ({uid, eventId, formId: form.formId, formFingerprint: form.fingerprint,
  sessionId: `session-${uid}-${form.formId}`, verificationVersion: "server-score-v1", status: "SETTLED",
  startedAtMs: 1000, completedAtMs: 1100 + i, elapsedMs: 100 + i, percentScore});
const runs = [row("alice", forms[0], 90), row("bob", forms[0], 60), row("bob", forms[1], 80), row("carol", forms[1], 70),
  row("alice", forms[2], 80), row("carol", forms[2], 60)];
const input = () => ({eventId, forms, endsAtMs, snapshotId: "immutable-snapshot", verifiedRuns: structuredClone(runs), completeThroughMs: endsAtMs, pendingSessionCount: 0});

const snapshot = build(input());
const direct = calculateTournamentLeaderboard(forms.map(form => ({groupId: form.formId,
  results: runs.filter(run => run.formId === form.formId).map(run => ({userId: run.uid, percent: run.percentScore,
    elapsedMs: run.elapsedMs, completedAtMs: run.completedAtMs}))})));
assert.deepEqual(snapshot.ranking, direct.leaderboard.map(row => ({uid: row.userId, place: row.place})));
assert.equal(snapshot.algorithmVersion, ALGORITHM_VERSION);
assert.equal(snapshot.verifiedRunCount, runs.length);
assert.deepEqual(build({...input(), verifiedRuns: [...runs].reverse()}), snapshot);

for (const bad of [{completeThroughMs: undefined}, {completeThroughMs: endsAtMs - 1}, {pendingSessionCount: 1}, {pendingSessionCount: undefined}]) {
  assert.throws(() => build({...input(), ...bad}), /complete cutoff/);
}
assert.throws(() => build({...input(), verifiedRuns: [...runs, runs[0]]}), /Multiple entries/);
assert.throws(() => build({...input(), verifiedRuns: [runs[0], {...runs[1], sessionId: runs[0].sessionId}]}), /session identity/);
for (const bad of [{eventId: "other"}, {formFingerprint: "other"}, {status: "STARTED"}, {elapsedMs: 101},
  {completedAtMs: endsAtMs + 1}, {percentScore: 100.5}, {percentScore: -1}, {verificationVersion: ""}]) {
  assert.throws(() => build({...input(), verifiedRuns: [{...runs[0], ...bad}]}), /bound, settled server result/);
}
const solo = build({...input(), verifiedRuns: [runs[0]]});
assert.equal(solo.totalRanked, 0, "Existing LS ignores singleton comparison groups; helper must not invent new ranking semantics");
assert.ok(!JSON.stringify(snapshot).includes("Infinity"));
console.log("tournament-qualification: LS parity, immutable-order fingerprint, complete-cutoff/pending guard, duplicate entries/session ids, result bindings and singleton semantics passed");
