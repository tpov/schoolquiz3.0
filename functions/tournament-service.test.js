"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {redact} = require("./question-redaction");
const {normalizeTournamentBank, tournamentDigest: digest} = require("./tournament-bank");
const {buildQualificationRankingSnapshot} = require("./tournament-qualification");
const {ALGORITHM_VERSION} = require("./tournament-ranking");
const {applyTournamentOperation: apply, readPlanTournamentOperation, writeTournamentOperation,
  readTournamentSession, TOURNAMENT_PATHS: P, qualificationSessionId, matchSessionId} = require("./tournament-service");
const {LEDGER_PATH, CURRENCY} = require("./currency-ledger");
const {PREMIUM_LOT_BATCH} = require("./premium-economy");
const {selectQualificationCalculation} = require("./tournament-qualification-freeze");
const {VERSIONS} = require("./tournament-qualification-compact");

const T = 1_800_000_000_000;
const DAY = 86_400_000;
const EVENT = "event_0000000000000001";
const OWNER = "organizer";
const E = `${P.events}/${EVENT}`;
const SOURCE = {kind: "COURSE", id: "existing-course", snapshotId: "reviewed-v7"};
const SEED = "private-server-random-seed-00000000000000000000";
const tests = [];
function test(name, body) { tests.push({name, body}); }
function user(boxCount = 0, extra = {}) { return {boxCount, standardHearts: 5, goldHearts: 1, lifePoints: 0,
  plasmaPoints: 0, lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T, ...extra}; }
function bankFixture() {
  const questions = Array.from({length: 80}, (_, i) => {
    const questionId = `question-${String(i).padStart(3, "0")}`;
    const difficulty = i < 60 ? "EASY" : "HARD";
    const content = {id: questionId, type: "SingleChoice", difficulty, text: `Existing question ${i}`,
      options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"};
    const pair = redact(JSON.stringify(content), difficulty, {questionId, random: () => 0});
    assert.equal(pair.status, "redacted");
    return {questionId, type: "SingleChoice", difficulty, sourceId: SOURCE.id, reviewed: true, published: true,
      contentVersion: 7, keyContentVersion: 7, publicHalfRedacted: true, maxDurationMs: 60_000,
      publicPayload: pair.publicPayload, answerKey: pair.key};
  });
  return {authorizedUid: OWNER, immutable: true, timingVersion: "shared-timer-v1", source: {...SOURCE, reviewed: true, published: true}, questions};
}
function fixture(participants = ["alice", "bob"], extra = {}) {
  const db = premiumTestDb({[`users/${OWNER}`]: user(100), ...Object.fromEntries(participants.map((uid) => [`users/${uid}`, user()])), ...extra});
  let serial = 0;
  const state = {bank: bankFixture(), participants, results: new Map(), bankLoads: 0, rankingOverride: undefined, resultOverride: undefined};
  const dependencies = {
    async loadBank() { state.bankLoads++; return structuredClone(state.bank); },
    async loadQualificationRanking({eventId, forms, endsAtMs}) {
      if (state.rankingOverride !== undefined) return state.rankingOverride;
      const runs = state.participants.flatMap((uid, index) => forms.map((form, round) => ({eventId, uid,
        sessionId: `${uid}-verified-${round}`, status: "SETTLED", formId: form.formId, formFingerprint: form.fingerprint,
        verificationVersion: "server-assessment-v1", percentScore: 100 - index, elapsedMs: 10_000 + index,
        startedAtMs: T, completedAtMs: T + 10_000 + index})));
      return buildQualificationRankingSnapshot({eventId, forms, endsAtMs, snapshotId: "complete-frozen-server-runs-v1", verifiedRuns: runs,
        completeThroughMs: endsAtMs, pendingSessionCount: 0});
    },
    async loadSettledMatchResults({matchId}) { return state.resultOverride !== undefined ? state.resultOverride : state.results.get(matchId) || null; },
  };
  const payload = (kind, ownerUid, fields = {}) => ({kind, ownerUid, operationId: `operation_${String(++serial).padStart(20, "0")}`, eventId: EVENT, ...fields});
  const call = (kind, uid, now = T, fields = {}) => apply({db, uid, now, dependencies, serverSeed: SEED, payload: payload(kind, uid, fields)});
  const create = (fields = {}) => call("CREATE", OWNER, T, {source: SOURCE, startsAtMs: T, prizes: {first: 5, second: 3, third: 1}, fundedBoxes: 12, ...fields});
  const close = () => call("CLOSE_QUALIFICATION", OWNER, T + 7 * DAY);
  return {db, state, dependencies, payload, call, create, close};
}
const boxes = (f, uid) => f.db.store.get(`users/${uid}`).boxCount;
const event = (f) => f.db.store.get(E);
const boxLedger = (f) => [...f.db.store].filter(([path, row]) => path.startsWith(`${LEDGER_PATH}/`) && row.currency === CURRENCY.BOXES).map(([, row]) => row);
function currentMatch(f, stage = 1, index = 0) { return event(f).bracket.rounds[stage - 1].matches[index]; }
async function agree(f, match, left, right, slot = match.stageStartsAtMs + 10_000) {
  await f.call("PROPOSE", left, slot - 3000, {matchId: match.matchId, slotStartsAtMs: slot});
  await f.call("CONFIRM", left, slot - 2000, {matchId: match.matchId, slotStartsAtMs: slot});
  await f.call("CONFIRM", right, slot - 1000, {matchId: match.matchId, slotStartsAtMs: slot});
  return slot;
}
async function play(f, match, winnerUid) {
  const pair = event(f).progress.pending.find((row) => row.matchId === match.matchId).entrants.map((row) => row.uid);
  const slot = await agree(f, match, ...pair, Math.max(match.stageStartsAtMs + 10_000, event(f).lastMutationAtMs + 4000));
  await f.call("READY", pair[0], slot, {matchId: match.matchId});
  const ready = await f.call("READY", pair[1], slot + 20, {matchId: match.matchId});
  assert.equal(ready.status, "STARTED");
  const start = ready.meeting.startedAtMs;
  f.state.results.set(match.matchId, pair.map((uid) => ({uid, status: "SETTLED", eventId: EVENT, matchId: match.matchId,
    sessionId: matchSessionId(EVENT, match.matchId, uid), formFingerprint: match.sharedForm.fingerprint,
    verificationVersion: "server-assessment-v1", percentScore: uid === winnerUid ? 90 : 80,
    elapsedMs: 1000, completedAtMs: start + 1000})));
  return f.call("ADVANCE_MATCH", OWNER, start + 1000, {matchId: match.matchId});
}

test("create reserves exact boxes with ledger, immutable 3x20 forms and private keys", async () => {
  const f = fixture(); const response = await f.create();
  assert.equal(response.status, "CREATED"); assert.equal(boxes(f, OWNER), 88);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 12, paidBoxes: 0, returnedBoxes: 0});
  const forms = [...f.db.store].filter(([path]) => path.startsWith(`${E}/forms/`)).map(([, row]) => row);
  assert.equal(forms.length, 3);
  assert.equal(new Set(forms.flatMap((form) => form.questions.map((q) => q.questionId))).size, 60);
  assert.ok(forms.every((form) => form.questions.length === 20 && form.maxDurationMs === 1_200_000));
  assert.ok(!JSON.stringify(forms).includes("correctOptionId"));
  assert.ok(!JSON.stringify(response).includes("serverSeed"));
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${P.privateForms}/${EVENT}/forms/`)).length, 3);
  assert.deepEqual(boxLedger(f).map((row) => row.delta), [-12]);
});

test("lost create response replays original receipt; mutated replay and account switch cannot reserve", async () => {
  const f = fixture();
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: T, prizes: {first: 5, second: 3, third: 1}, fundedBoxes: 12});
  const invoke = (uid, body, now = T) => apply({db: f.db, uid, payload: body, now, dependencies: f.dependencies, serverSeed: SEED});
  const first = await invoke(OWNER, payload);
  const after = structuredClone([...f.db.store]);
  assert.deepEqual(await invoke(OWNER, payload, T + DAY), first);
  assert.deepEqual([...f.db.store], after); assert.equal(f.state.bankLoads, 1);
  await assert.rejects(invoke(OWNER, {...payload, fundedBoxes: 13}), {code: "CONFLICTING_REPLAY"});
  await assert.rejects(invoke("alice", {...payload, ownerUid: "alice"}), {code: "PERMISSION_DENIED"});
  await assert.rejects(invoke("alice", payload), {code: "PERMISSION_DENIED"});
  assert.deepEqual([...f.db.store], after);
});

test("atomic commit failure creates no partial escrow, forms or receipt; same command can retry", async () => {
  const f = fixture(); const before = structuredClone([...f.db.store]);
  f.db.failNextCommit(); await assert.rejects(f.create(), /atomic commit failure/);
  assert.deepEqual([...f.db.store], before);
  await f.create(); assert.equal(boxes(f, OWNER), 88);
});

test("missing prices, insufficient boxes, untrusted authorization and absent timer fail before writes", async () => {
  for (const mutate of [f => { f.db.store.set(`users/${OWNER}`, user(1)); }, f => { f.state.bank.authorizedUid = "other"; },
    f => { delete f.state.bank.questions[0].maxDurationMs; }, f => { delete f.state.bank.timingVersion; }]) {
    const f = fixture(); mutate(f); const before = structuredClone([...f.db.store]);
    await assert.rejects(f.create()); assert.deepEqual([...f.db.store], before);
  }
  const f = fixture(); await assert.rejects(f.create({prizes: {first: 5, second: 3}}));
  await assert.rejects(f.create({prizes: {first: 5, second: 3, third: 2}, fundedBoxes: 11}));
  assert.equal(boxes(f, OWNER), 100); assert.equal(f.db.store.has(E), false);
});

test("bank excludes Survey and refuses <60 EASY, <20 HARD, stale keys and public answers", async () => {
  for (const mutate of [b => { b.questions[0].type = "Survey"; }, b => { b.questions.pop(); },
    b => { b.questions[0].keyContentVersion = 6; }, b => { b.questions[0].publicHalfRedacted = false; },
    b => { b.questions[0].answerKey.correctOptionId = "not-an-option"; },
    b => { const q = b.questions[0]; q.publicPayload = {...JSON.parse(q.publicPayload), correctOptionId: "a"}; }]) {
    const f = fixture(); mutate(f.state.bank); const before = structuredClone([...f.db.store]);
    await assert.rejects(f.create()); assert.deepEqual([...f.db.store], before);
  }
});

test("changing private key changes only private fingerprint, not public content hash", () => {
  const raw = bankFixture();
  const first = normalizeTournamentBank({bank: raw, requesterUid: OWNER, sourceRef: SOURCE});
  raw.questions[0].answerKey.correctOptionId = "b";
  const second = normalizeTournamentBank({bank: raw, requesterUid: OWNER, sourceRef: SOURCE});
  assert.equal(first.poolFingerprint, second.poolFingerprint);
  assert.notEqual(first.privateFingerprint, second.privateFingerprint);
});

test("all four existing assessment types retain valid paired public/private snapshots", async () => {
  const f = fixture();
  const variants = [
    {type: "MultipleChoice", options: ["a", "b", "c", "d"].map(id => ({id, text: id})), correctOptionIds: ["a", "b"]},
    {type: "Ordering", items: ["i1", "i2", "i3", "i4"].map(id => ({id, text: id}))},
    {type: "FillBlank", blanks: [{id: "b1", correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c2"}],
      candidates: ["c1", "c2", "c3", "c4"].map(id => ({id, text: id}))},
  ];
  variants.forEach((variant, i) => {
    const q = f.state.bank.questions[i];
    const content = {id: q.questionId, difficulty: q.difficulty, text: "Existing assessment", ...variant};
    const pair = redact(JSON.stringify(content), q.difficulty, {questionId: q.questionId, random: () => 0});
    assert.equal(pair.status, "redacted");
    Object.assign(q, {type: variant.type, publicPayload: pair.publicPayload, answerKey: pair.key});
  });
  await f.create();
  const forms = [...f.db.store].filter(([path]) => path.startsWith(`${E}/forms/`)).map(([, form]) => form);
  assert.deepEqual(new Set(forms.flatMap(form => form.questions.map(q => q.type))), new Set(["SingleChoice", "MultipleChoice", "Ordering", "FillBlank"]));
});

test("qualification consumes one entry per form; lost response and new-id reconnect do not reset timers", async () => {
  const f = fixture(); await f.create();
  const attemptId = "attempt_0000000000000001";
  const first = await f.call("START_QUALIFICATION", "alice", T + 100, {roundNumber: 1, attemptId});
  const second = await f.call("START_QUALIFICATION", "alice", T + 7 * DAY + 5, {roundNumber: 1, attemptId});
  assert.deepEqual(second.session, first.session);
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 1000, {roundNumber: 1, attemptId: "attempt_0000000000000002"}));
  await assert.rejects(f.call("START_QUALIFICATION", "bob", T + 1000, {roundNumber: 2, attemptId}), {code: "CONFLICTING_REPLAY"});
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${E}/entries/`)).length, 1);
});

test("late qualification session is bounded by cutoff; reconnect exposes no private or future question", async () => {
  const f = fixture(); await f.create();
  const first = await f.call("START_QUALIFICATION", "alice", T + 7 * DAY - 10, {roundNumber: 1, attemptId: "attempt_0000000000000001"});
  assert.equal(first.session.endsAtMs, T + 7 * DAY);
  const read = (uid, now) => f.db.runTransaction(transaction => readTournamentSession({db: f.db, transaction, uid, sessionId: first.session.sessionId, now}));
  const reconnect = await read("alice", T + 7 * DAY - 5);
  assert.deepEqual(reconnect.session, first.session); assert.ok(reconnect.question);
  assert.ok(!JSON.stringify(reconnect).includes("correctOption"));
  assert.equal((await read("alice", T + 7 * DAY)).question, null);
  await assert.rejects(read("bob", T + 7 * DAY - 5), {code: "PERMISSION_DENIED"});
});

test("caller scores, durations and request uid are rejected rather than trusted", async () => {
  const f = fixture(); await f.create();
  for (const forbidden of [{percentScore: 100}, {durationMs: 1}, {uid: OWNER}, {questions: []}]) {
    await assert.rejects(f.call("START_QUALIFICATION", "alice", T, {roundNumber: 1, attemptId: "attempt_0000000000000001", ...forbidden}), {code: "INVALID_ARGUMENT"});
  }
});

test("qualification close uses frozen existing LS top16 and saves one shared HARD20 form per pair", async () => {
  const people = Array.from({length: 18}, (_, i) => `player-${String(i).padStart(2, "0")}`);
  const f = fixture(people); await f.create(); await f.close();
  assert.equal(event(f).qualificationRanking.algorithmVersion, ALGORITHM_VERSION);
  assert.equal(event(f).bracket.entrants.length, 16);
  assert.deepEqual(event(f).bracket.entrants.map((row) => row.uid), people.slice(0, 16));
  assert.equal(event(f).bracket.rounds.flatMap((round) => round.matches).length, 15);
  const first = currentMatch(f);
  assert.equal(first.sharedForm.questions.length, 20); assert.equal(first.sharedForm.difficulty, "HARD");
  assert.deepEqual(event(f).progress.pending[0].entrants.map((row) => row.seed), [1, 16]);
  assert.equal(boxes(f, OWNER), 88);
});

test("missing/incomplete/wrong-algorithm ranking never cancels or refunds an active funded event", async () => {
  for (const snapshot of [null, {complete: false}, {complete: true, algorithmVersion: "client-percent"}]) {
    const f = fixture(); await f.create(); f.state.rankingOverride = snapshot;
    const before = structuredClone([...f.db.store]); await assert.rejects(f.close());
    assert.deepEqual([...f.db.store], before); assert.equal(boxes(f, OWNER), 88);
  }
});

test("changed immutable bank before close is refused with escrow intact", async () => {
  const f = fixture(); await f.create();
  f.state.bank.questions[70].answerKey.correctOptionId = "b";
  const before = structuredClone([...f.db.store]); await assert.rejects(f.close(), {code: "INVALID_BANK"});
  assert.deepEqual([...f.db.store], before);
});

test("<2 qualifiers cancels and returns exact fund once without reloading mutable bank", async () => {
  const f = fixture(["alice"]); await f.create();
  f.state.bank.immutable = false;
  const closed = await f.close();
  assert.equal(closed.status, "CANCELLED"); assert.equal(boxes(f, OWNER), 100); assert.equal(f.state.bankLoads, 1);
  await f.close(); assert.equal(boxes(f, OWNER), 100);
  assert.deepEqual(boxLedger(f).map((row) => row.delta).sort((a, b) => a - b), [-12, 12]);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 0, paidBoxes: 0, returnedBoxes: 12});
});

test("organizer close authorization and no early-close window", async () => {
  const f = fixture(); await f.create();
  await assert.rejects(f.call("CLOSE_QUALIFICATION", "alice", T + 7 * DAY), {code: "PERMISSION_DENIED"});
  await assert.rejects(f.call("CLOSE_QUALIFICATION", OWNER, T + 7 * DAY - 1), {code: "FAILED_PRECONDITION"});
});

test("old offers remain confirmable after later proposals; first mutual agreement locks the slot", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = m.stageStartsAtMs + 10_000;
  await f.call("PROPOSE", "alice", slot - 5000, {matchId: m.matchId, slotStartsAtMs: slot});
  await f.call("PROPOSE", "alice", slot - 4000, {matchId: m.matchId, slotStartsAtMs: slot + 1000});
  await f.call("CONFIRM", "alice", slot - 3000, {matchId: m.matchId, slotStartsAtMs: slot});
  const agreed = await f.call("CONFIRM", "bob", slot - 2000, {matchId: m.matchId, slotStartsAtMs: slot});
  assert.equal(agreed.status, "WAITING_FOR_WINDOW"); assert.equal(agreed.meeting.slotStartsAtMs, slot);
  await assert.rejects(f.call("PROPOSE", "bob", slot - 1000, {matchId: m.matchId, slotStartsAtMs: slot + 2000}), {code: "FAILED_PRECONDITION"});
  await assert.rejects(f.call("CONFIRM", "bob", slot - 1000, {matchId: m.matchId, slotStartsAtMs: slot + 1000}), {code: "FAILED_PRECONDITION"});
});

test("one ready player wins by no-show; technical loser gets no runner-up prize", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = await agree(f, m, "alice", "bob");
  await f.call("READY", "alice", slot, {matchId: m.matchId});
  await assert.rejects(f.call("READY", "bob", slot + 3_600_000, {matchId: m.matchId}), {code: "FAILED_PRECONDITION"});
  const result = await f.call("ADVANCE_MATCH", OWNER, slot + 3_600_000, {matchId: m.matchId});
  assert.equal(result.status, "FORFEIT"); assert.equal(event(f).status, "FINISHED");
  assert.equal(boxes(f, "alice"), 5); assert.equal(boxes(f, "bob"), 0); assert.equal(boxes(f, OWNER), 95);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 0, paidBoxes: 5, returnedBoxes: 7});
});

test("neither ready means DOUBLE_OUT and all unused boxes returned", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = await agree(f, m, "alice", "bob");
  const result = await f.call("ADVANCE_MATCH", OWNER, slot + 3_600_000, {matchId: m.matchId});
  assert.equal(result.status, "DOUBLE_OUT"); assert.equal(boxes(f, OWNER), 100);
  assert.equal(event(f).progress.placements.length, 0);
});

test("one proposer versus neither/both: coordination deadline applies existing technical policy", async () => {
  for (const count of [0, 1, 2]) {
    const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
    for (const uid of ["alice", "bob"].slice(0, count)) {
      await f.call("PROPOSE", uid, m.stageStartsAtMs + 1, {matchId: m.matchId, slotStartsAtMs: m.stageStartsAtMs + 10_000});
    }
    const result = await f.call("ADVANCE_MATCH", OWNER, m.coordinationDeadlineMs, {matchId: m.matchId});
    assert.equal(result.status, count === 1 ? "FORFEIT" : "DOUBLE_OUT");
    assert.equal(boxes(f, "alice"), count === 1 ? 5 : 0);
  }
});

test("same HARD bundle and permanent clocks survive reconnect, repeated readiness and account switch", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = await agree(f, m, "alice", "bob");
  await f.call("READY", "alice", slot, {matchId: m.matchId});
  const ready = await f.call("READY", "bob", slot + 100, {matchId: m.matchId});
  const again = await f.call("READY", "alice", slot + 1000, {matchId: m.matchId});
  assert.equal(again.meeting.startedAtMs, ready.meeting.startedAtMs); assert.equal(again.meeting.endsAtMs, ready.meeting.endsAtMs);
  const a = f.db.store.get(`${P.sessions}/${matchSessionId(EVENT, m.matchId, "alice")}`);
  const b = f.db.store.get(`${P.sessions}/${matchSessionId(EVENT, m.matchId, "bob")}`);
  assert.equal(a.formFingerprint, b.formFingerprint); assert.equal(a.startedAtMs, b.startedAtMs); assert.equal(a.endsAtMs, b.endsAtMs);
  const reconnect = await f.db.runTransaction(transaction => readTournamentSession({db: f.db, transaction, uid: "alice", sessionId: a.sessionId, now: slot + 500}));
  assert.equal(reconnect.session.startedAtMs, slot + 100); assert.ok(reconnect.question);
  const expired = await f.db.runTransaction(transaction => readTournamentSession({db: f.db, transaction, uid: "alice", sessionId: a.sessionId, now: a.questionEndsAtMs}));
  assert.equal(expired.question, null); assert.equal(expired.questionDeadlineReached, true);
  await assert.rejects(f.call("READY", OWNER, slot + 2000, {matchId: m.matchId}), {code: "PERMISSION_DENIED"});
});

test("delayed settled results stay pending even past timeout; no invented zero score or payout", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = await agree(f, m, "alice", "bob");
  await f.call("READY", "alice", slot, {matchId: m.matchId}); await f.call("READY", "bob", slot, {matchId: m.matchId});
  const result = await f.call("ADVANCE_MATCH", OWNER, slot + m.sharedForm.maxDurationMs + 1, {matchId: m.matchId});
  assert.equal(result.status, "RESULTS_PENDING"); assert.equal(event(f).status, "BRACKET");
  assert.equal(event(f).outcomes.length, 0); assert.equal(boxes(f, OWNER), 88);
});

test("forged result binding/elapsed is rejected; verified played final pays first+second once", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  const slot = await agree(f, m, "alice", "bob");
  await f.call("READY", "alice", slot, {matchId: m.matchId}); await f.call("READY", "bob", slot, {matchId: m.matchId});
  f.state.resultOverride = [{uid: "alice", percentScore: 100}, {uid: "bob", percentScore: 0}];
  const before = structuredClone([...f.db.store]);
  await assert.rejects(f.call("ADVANCE_MATCH", OWNER, slot + 500, {matchId: m.matchId}), {code: "INVALID_RESULTS"});
  assert.deepEqual([...f.db.store], before);
  f.state.resultOverride = ["alice", "bob"].map((uid) => ({uid, status: "SETTLED", eventId: EVENT, matchId: m.matchId,
    sessionId: matchSessionId(EVENT, m.matchId, uid), formFingerprint: m.sharedForm.fingerprint,
    verificationVersion: "assessment-v1", percentScore: uid === "alice" ? 90 : 80, elapsedMs: 500, completedAtMs: slot + 500}));
  const payload = f.payload("ADVANCE_MATCH", OWNER, {matchId: m.matchId});
  const invoke = () => apply({db: f.db, uid: OWNER, payload, now: slot + 500, dependencies: f.dependencies});
  const final = await invoke(); const after = structuredClone([...f.db.store]);
  assert.equal(final.status, "PLAYED"); assert.equal(boxes(f, "alice"), 5); assert.equal(boxes(f, "bob"), 3); assert.equal(boxes(f, OWNER), 92);
  assert.deepEqual(await invoke(), final); assert.deepEqual([...f.db.store], after);
  await f.call("ADVANCE_MATCH", OWNER, slot + 600, {matchId: m.matchId}); assert.equal(boxes(f, "alice"), 5);
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${P.funding}/`)).length, 4);
});

test("automatic semifinal/final progression pays two bronze places without a bronze match", async () => {
  const f = fixture(["alice", "bob", "carol", "dave"]); await f.create(); await f.close();
  await play(f, currentMatch(f, 1, 0), "alice");
  await play(f, currentMatch(f, 1, 1), "bob");
  assert.equal(event(f).progress.pending.length, 1);
  await play(f, currentMatch(f, 2, 0), "alice");
  assert.equal(event(f).status, "FINISHED");
  assert.deepEqual(["alice", "bob", "carol", "dave"].map(uid => boxes(f, uid)), [5, 3, 1, 1]);
  assert.equal(boxes(f, OWNER), 90); assert.equal(event(f).bracket.rounds.flatMap(round => round.matches).length, 3);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 0, paidBoxes: 10, returnedBoxes: 2});
});

test("technical semifinal loser receives no bronze; byes and double-outs progress automatically", async () => {
  const f = fixture(["alice", "bob", "carol"]); await f.create(); await f.close();
  assert.ok(event(f).progress.resolved.some(row => row.status === "BYE"));
  const semi = currentMatch(f, 1, 1);
  await f.call("PROPOSE", "bob", semi.stageStartsAtMs + 1, {matchId: semi.matchId, slotStartsAtMs: semi.stageStartsAtMs + 1000});
  await f.call("ADVANCE_MATCH", OWNER, semi.coordinationDeadlineMs, {matchId: semi.matchId});
  await play(f, currentMatch(f, 2, 0), "alice");
  assert.equal(boxes(f, "carol"), 0); assert.equal(boxes(f, "alice"), 5); assert.equal(boxes(f, "bob"), 3);
  assert.equal(boxes(f, OWNER), 92);
});

test("funding receipt corruption and missing winner account preserve escrow instead of partial payouts", async () => {
  const f = fixture(["alice"]); await f.create();
  const returnId = JSON.stringify(["tournament", EVENT, "unused-fund-return"]);
  f.db.store.set(`${P.funding}/${digest(returnId)}`, {bad: true});
  const before = structuredClone([...f.db.store]); await assert.rejects(f.close(), {code: "INCONSISTENT_ESCROW"});
  assert.deepEqual([...f.db.store], before);
  const g = fixture(["alice", "bob"]); await g.create(); await g.close(); const m = currentMatch(g);
  await g.call("PROPOSE", "alice", m.stageStartsAtMs + 1, {matchId: m.matchId, slotStartsAtMs: m.stageStartsAtMs + 1000});
  g.db.store.delete("users/alice"); const beforeMissing = structuredClone([...g.db.store]);
  await assert.rejects(g.call("ADVANCE_MATCH", OWNER, m.coordinationDeadlineMs, {matchId: m.matchId}), {code: "FAILED_PRECONDITION"});
  assert.deepEqual([...g.db.store], beforeMissing);
});

test("payout commit failure cannot split escrow and winner/refund balances", async () => {
  const f = fixture(); await f.create(); await f.close(); const m = currentMatch(f);
  await f.call("PROPOSE", "alice", m.stageStartsAtMs + 1, {matchId: m.matchId, slotStartsAtMs: m.stageStartsAtMs + 1000});
  const payload = f.payload("ADVANCE_MATCH", OWNER, {matchId: m.matchId});
  const invoke = () => apply({db: f.db, uid: OWNER, payload, now: m.coordinationDeadlineMs, dependencies: f.dependencies});
  const before = structuredClone([...f.db.store]); f.db.failNextCommit();
  await assert.rejects(invoke(), /atomic commit failure/); assert.deepEqual([...f.db.store], before);
  await invoke(); assert.equal(boxes(f, "alice"), 5); assert.equal(boxes(f, OWNER), 95);
  assert.equal(event(f).escrow.remainingBoxes, 0);
});

test("organizer also winning combines prize and unused return without overwriting either credit", async () => {
  const f = fixture([OWNER, "alice"], {[`users/${OWNER}`]: user(100)}); await f.create(); await f.close();
  await play(f, currentMatch(f), OWNER);
  assert.equal(boxes(f, OWNER), 97); assert.equal(boxes(f, "alice"), 3);
  assert.equal(boxLedger(f).filter(row => row.uid === OWNER).reduce((sum, row) => sum + row.delta, 0), -3);
});

test("legacy countBox funds escrow and a legacy winner keeps their preexisting boxes", async () => {
  const f = fixture();
  const organizer = user(); delete organizer.boxCount; organizer.countBox = 100;
  const winner = user(); delete winner.boxCount; winner.countBox = 10;
  f.db.store.set(`users/${OWNER}`, organizer); f.db.store.set("users/alice", winner);
  await f.create(); assert.equal(boxes(f, OWNER), 88);
  await f.close(); await play(f, currentMatch(f), "alice");
  assert.equal(boxes(f, "alice"), 15); assert.equal(boxes(f, "bob"), 3); assert.equal(boxes(f, OWNER), 92);
  assert.equal(f.db.store.get("users/alice").countBox, 10, "Canonical field takes over without destructive legacy edits");
});

test("canonical zero overrides stale legacy countBox during reservation", async () => {
  const f = fixture([], {[`users/${OWNER}`]: user(0, {countBox: 100})});
  await assert.rejects(f.create(), {code: "INSUFFICIENT_BOXES"}); assert.equal(f.db.store.has(E), false);
});

test("premium clock advances before reserve; sticky bool cannot preserve expired premium", async () => {
  const f = fixture(["alice", "bob"], {[`users/${OWNER}`]: user(100, {premiumUntilMs: T + 1000, hasPremium: true})});
  await f.create();
  assert.equal(f.db.store.get(`premium_accounts/${OWNER}`).asOfMs, T);
  assert.equal(f.db.store.get(`premium_accounts/${OWNER}`).totalRemainingMs, 1000);
  await f.close(); const m = currentMatch(f);
  await f.call("ADVANCE_MATCH", OWNER, m.coordinationDeadlineMs, {matchId: m.matchId});
  assert.equal(f.db.store.get(`premium_accounts/${OWNER}`).asOfMs, m.coordinationDeadlineMs);
  assert.equal(f.db.store.get(`users/${OWNER}`).hasPremium, false);
  assert.equal(boxes(f, OWNER), 100);
});

test("premium catch-up commits checkpoint only: no event, debit, form or receipt until caught up", async () => {
  const f = fixture();
  // Existing valid lots exceed one bounded read batch.
  const total = PREMIUM_LOT_BATCH + 2;
  // Use the premium helper itself to create schema-valid fixtures rather than inventing lot fields.
  const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy} = require("./premium-economy");
  const {readEconomyConstants} = require("./economy-constants");
  for (let i = 0; i < total; i++) {
    await f.db.runTransaction(async transaction => {
      const operation = {kind: "GRANT", source: "BOX", sourceId: `prior-${i}`, days: 1};
      const snapshot = await readPremiumEconomy({db: f.db, transaction, uid: OWNER, operation});
      const plan = planPremiumEconomy({snapshot, userData: f.db.store.get(`users/${OWNER}`), constants: readEconomyConstants(null), now: T, operation});
      writePremiumEconomy({db: f.db, transaction, uid: OWNER, plan});
      transaction.set(f.db.doc(`users/${OWNER}`), plan.userPatch, {merge: true});
    });
  }
  const now = T + total * DAY;
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: now, prizes: {first: 5, second: 3, third: 1}, fundedBoxes: 12});
  const planned = await f.db.runTransaction(async transaction => {
    const plan = await readPlanTournamentOperation({db: f.db, transaction, uid: OWNER, payload, now, dependencies: f.dependencies, serverSeed: SEED});
    writeTournamentOperation({db: f.db, transaction, plan}); return plan;
  });
  assert.equal(planned.caughtUp, false); assert.equal(boxes(f, OWNER), 100); assert.equal(f.db.store.has(E), false);
  assert.equal([...f.db.store.keys()].filter(path => path.startsWith(`${P.operations}/`)).length, 0);
  assert.equal([...f.db.store.keys()].filter(path => path.startsWith(`${P.funding}/`)).length, 0);
  const completed = await apply({db: f.db, uid: OWNER, payload, now, dependencies: f.dependencies, serverSeed: SEED});
  assert.equal(completed.status, "CREATED"); assert.equal(boxes(f, OWNER), 88);
});

test("oversized public bundle fails before any financial write", async () => {
  const f = fixture();
  for (const q of f.state.bank.questions) q.publicPayload = {...JSON.parse(q.publicPayload), text: "X".repeat(50_000)};
  const before = structuredClone([...f.db.store]);
  await assert.rejects(f.create(), {code: "RESOURCE_EXHAUSTED"}); assert.deepEqual([...f.db.store], before);
});

async function versionedFixture() {
  const f = fixture(); await f.create();
  const selected = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 7 * DAY});
  const c = selected.contract;
  const legacy = await f.dependencies.loadQualificationRanking({eventId: EVENT, forms: event(f).qualificationForms, endsAtMs: c.endsAtMs});
  f.state.rankingOverride = {...legacy, schemaVersion: 2, snapshotId: c.freezeId, freezeFenceId: c.freezeId,
    calculationVersions: {...VERSIONS}, calculationContractDigest: c.contractDigest,
    inputFingerprintVersion: "player-stream-chain-v1", verifiedRunsFingerprint: digest("complete-versioned-player-stream")};
  return f;
}
test("close consumes selected versioned ranking and retains exact provenance with the bracket", async () => {
  const f = await versionedFixture(); const expected = structuredClone(f.state.rankingOverride);
  const closed = await f.close(); assert.equal(closed.status, "BRACKET");
  const provenance = event(f).qualificationRanking;
  assert.equal(provenance.calculationContractDigest, expected.calculationContractDigest);
  assert.deepEqual(provenance.calculationVersions, VERSIONS);
  assert.equal(provenance.inputFingerprintVersion, "player-stream-chain-v1");
  assert.equal(provenance.verifiedRunsFingerprint, expected.verifiedRunsFingerprint);
  assert.equal(provenance.snapshotId, expected.snapshotId);
  assert.deepEqual(event(f).bracket.entrants.map(row => row.uid), ["alice", "bob"]);
  assert.deepEqual(f.state.rankingOverride, expected);
});
test("old or incompatible ranking cannot close selected-version event or mutate its escrow", async () => {
  for (const mutate of [r => { r.schemaVersion = 1; }, r => { r.calculationVersions.roundingVersion = "old-floating"; },
    r => { r.calculationContractDigest = digest("other-contract"); }, r => { delete r.inputFingerprintVersion; },
    r => { r.freezeFenceId = "legacy-fence"; }]) {
    const f = await versionedFixture(); mutate(f.state.rankingOverride); const before = structuredClone([...f.db.store]);
    await assert.rejects(f.close(), {code: "INVALID_RANKING"}); assert.deepEqual([...f.db.store], before);
  }
});

(async () => {
  for (const {name, body} of tests) { await body(); console.log(`ok - ${name}`); }
  console.log(`tournament-service: ${tests.length} behavioral tests passed`);
})().catch(error => { console.error(error); process.exitCode = 1; });
