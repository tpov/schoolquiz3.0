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
const {selectQualificationCalculation, freezeQualification} = require("./tournament-qualification-freeze");
const {VERSIONS} = require("./tournament-qualification-compact");
const {applyTournamentSessionOperation, advanceTournamentSession, VERIFIED_RUNS} = require("./tournament-session");

const T = 1_800_000_000_000;
const DAY = 86_400_000;
const EVENT = "event_0000000000000001";
const OWNER = "organizer";
const E = `${P.events}/${EVENT}`;
const SOURCE = {kind: "COURSE", id: "existing-course", snapshotId: "reviewed-v7"};
const SEED = "private-server-random-seed-00000000000000000000";
// Story 5.4: CREATE carries the adopted v1 prize list. {first:5, second:3, third:1} maps to two
// place-3 rows; liability 10 leaves a 2-box deposit surplus that returns at finish.
const PRIZES = {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
  {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]};
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
  // Story 5.3: every participant holds a server-attested download record for the fixture
  // course, so qualification STARTs keep exercising the entry/session discipline; the
  // admission-gate tests below delete or bypass these records explicitly.
  const downloads = Object.fromEntries(participants.map((uid) =>
    [`${P.downloads}/${digest([uid, SOURCE.id])}`, {schemaVersion: 1, uid, courseId: SOURCE.id, downloadedAtMs: T}]));
  const db = premiumTestDb({[`users/${OWNER}`]: user(100), ...Object.fromEntries(participants.map((uid) => [`users/${uid}`, user()])), ...downloads, ...extra});
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
  const create = (fields = {}) => call("CREATE", OWNER, T, {source: SOURCE, startsAtMs: T, prizes: PRIZES, fundedBoxes: 12, ...fields});
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
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: T, prizes: PRIZES, fundedBoxes: 12});
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

test("missing prices, legacy DTO, underfunded v1, untrusted authorization and absent timer fail before writes", async () => {
  for (const mutate of [f => { f.db.store.set(`users/${OWNER}`, user(1)); }, f => { f.state.bank.authorizedUid = "other"; },
    f => { delete f.state.bank.questions[0].maxDurationMs; }, f => { delete f.state.bank.timingVersion; }]) {
    const f = fixture(); mutate(f); const before = structuredClone([...f.db.store]);
    await assert.rejects(f.create()); assert.deepEqual([...f.db.store], before);
  }
  const f = fixture();
  // L4: the fixed {first, second, third} DTO is refused by the v1 validator, naming the v1 shape.
  await assert.rejects(f.create({prizes: {first: 5, second: 3}}), /listVersion: 1, rows/);
  await assert.rejects(f.create({prizes: {first: 5, second: 3, third: 2}, fundedBoxes: 11}), /listVersion: 1, rows/);
  // Underfunded v1 list keeps the existing "insufficient prize fund" refusal with no reserve.
  await assert.rejects(f.create({fundedBoxes: 9}), /insufficient prize fund/);
  assert.equal(boxes(f, OWNER), 100); assert.equal(f.db.store.has(E), false);
});

test("CREATE freezes the canonically sorted v1 list; reordered equivalent rows replay, new ids refuse", async () => {
  const f = fixture();
  const unsorted = {listVersion: 1, rows: [{place: 3, amountBoxes: 1}, {place: 1, amountBoxes: 5},
    {place: 3, amountBoxes: 1}, {place: 2, amountBoxes: 3}]};
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: T, prizes: unsorted, fundedBoxes: 12});
  const invoke = (body) => apply({db: f.db, uid: OWNER, payload: body, now: T, dependencies: f.dependencies, serverSeed: SEED});
  const created = await invoke(payload);
  // The event stores the canonically sorted frozen list; escrow reserves the whole deposit.
  assert.deepEqual(event(f).prizes, PRIZES);
  assert.equal(event(f).maximumLiabilityBoxes, 10);
  assert.deepEqual(created.event.prizes, {...PRIZES, maximumLiabilityBoxes: 10});
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 12, paidBoxes: 0, returnedBoxes: 0});
  // The fingerprint covers the canonical form, so a reordered replay of the same operation id
  // returns the original receipt instead of CONFLICTING_REPLAY.
  assert.deepEqual(await invoke({...payload, prizes: {listVersion: 1, rows: [...unsorted.rows].reverse()}}), created);
  // A new operation id on the permanent event id refuses before any staging or second reserve.
  await assert.rejects(invoke({...payload, operationId: "operation_new_0000000001"}), {code: "ALREADY_EXISTS"});
  assert.equal(boxes(f, OWNER), 88);
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${P.funding}/`)).length, 1);
});

test("stored legacy prizes settle with identical amounts, slot receipt keys and the projected read", async () => {
  const f = fixture(["alice", "bob", "carol", "dave"]); await f.create();
  // Simulate a stored pre-adoption event: the immutable legacy DTO with equivalent numbers.
  const frozenBefore = structuredClone(event(f).prizes);
  const stored = event(f); stored.prizes = {first: 5, second: 3, third: 1}; stored.maximumLiabilityBoxes = 10;
  await f.close();
  await play(f, currentMatch(f, 1, 0), "alice");
  await play(f, currentMatch(f, 1, 1), "bob");
  const result = await play(f, currentMatch(f, 2, 0), "alice");
  assert.equal(event(f).status, "FINISHED");
  assert.deepEqual(["alice", "bob", "carol", "dave"].map((uid) => boxes(f, uid)), [5, 3, 1, 1]);
  assert.equal(boxes(f, OWNER), 90);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 0, paidBoxes: 10, returnedBoxes: 2});
  // Legacy payouts keep yesterday's slot-keyed funding receipts (never v1 row keys).
  for (const slot of ["champion", "runner-up", "third-upper", "third-lower"]) {
    assert.ok(f.db.store.has(`${P.funding}/${digest(JSON.stringify(["tournament", EVENT, "prize", slot]))}`), slot);
  }
  // The read projection is the unified shape: listVersion 0 rows, no DTO field leaks.
  assert.deepEqual(result.event.prizes, {listVersion: 0, maximumLiabilityBoxes: 10,
    rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}, {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]});
  assert.ok(!["first", "second", "third"].some((name) => Object.hasOwn(result.event.prizes, name)));
  // No code path writes prizes after CREATE: the pre-adoption legacy DTO is byte-identical after settlement.
  assert.deepEqual(event(f).prizes, stored.prizes);
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

const admissionRows = (f) => [...f.db.store].filter(([path]) => path.startsWith(`${E}/admissions/`));

test("START without a server admission basis is refused as a typed gate error and stages nothing", async () => {
  const f = fixture(); await f.create();
  f.db.store.delete(`${P.downloads}/${digest(["alice", SOURCE.id])}`);
  // A download record for another course of the same account is not a basis either.
  f.db.store.set(`${P.downloads}/${digest(["alice", "other-course"])}`,
    {schemaVersion: 1, uid: "alice", courseId: "other-course", downloadedAtMs: T});
  const before = structuredClone([...f.db.store]);
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001"}),
  (error) => { assert.equal(error.code, "PERMISSION_DENIED"); assert.equal(error.message, "NO_ADMISSION_BASIS"); return true; });
  assert.deepEqual([...f.db.store], before);
  assert.equal(admissionRows(f).length, 0);
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001"}), {code: "PERMISSION_DENIED"});
  assert.deepEqual([...f.db.store], before);
});

test("the minted link token admits without any download record and never persists anywhere", async () => {
  const f = fixture();
  f.db.store.delete(`${P.downloads}/${digest(["alice", SOURCE.id])}`);
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: T, prizes: PRIZES, fundedBoxes: 12});
  const invoke = (uid, body, now = T) => apply({db: f.db, uid, payload: body, now, dependencies: f.dependencies, serverSeed: SEED});
  const created = await invoke(OWNER, payload);
  const token = created.admissionLink.token;
  assert.ok(/^[a-f0-9]{48}$/.test(token));
  assert.ok(!JSON.stringify(created.event).includes(token));
  assert.equal(f.db.store.get(`${P.links}/${EVENT}`).digest, digest(token));
  // Wrong token: refused, nothing staged. Right token: admitted via LINK, doc created once.
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001", linkToken: "f".repeat(48)}), {code: "PERMISSION_DENIED"});
  // A malformed token is a request error before any record is read.
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001", linkToken: "zzz-not-hex"}), {code: "INVALID_ARGUMENT"});
  assert.equal(admissionRows(f).length, 0);
  const started = await f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001", linkToken: token});
  assert.equal(started.status, "SESSION");
  assert.deepEqual(admissionRows(f).map(([, row]) => row),
    [{schemaVersion: 1, eventId: EVENT, uid: "alice", basis: "LINK", admittedAtMs: T + 100}]);
  // The plaintext token is carried by exactly one stored document — the organizer's own
  // operation receipt, the thing receipt replay reads back; the event, link doc and forms
  // hold no token (the link doc holds only its digest).
  assert.deepEqual([...f.db.store].filter(([, row]) => JSON.stringify(row).includes(token)).map(([path]) => path),
    [`${P.operations}/${digest(payload.operationId)}`]);
  // Receipt replay recovers the token for the organizer only; another account never can.
  assert.equal((await invoke(OWNER, payload, T + DAY)).admissionLink.token, token);
  await assert.rejects(invoke("alice", {...payload, ownerUid: "alice"}), {code: "PERMISSION_DENIED"});
});

test("admission is recorded once per (event, account) and reused; retries never re-admit", async () => {
  const f = fixture(); await f.create();
  const first = await f.call("START_QUALIFICATION", "alice", T + 100, {roundNumber: 1, attemptId: "attempt_0000000000000001"});
  assert.deepEqual(admissionRows(f).map(([, row]) => row.basis), ["COURSE_DOWNLOAD"]);
  await f.call("START_QUALIFICATION", "alice", T + 200, {roundNumber: 2, attemptId: "attempt_0000000000000002"});
  assert.equal(admissionRows(f).length, 1);
  // A lost response replayed after the window replays the attempt outcome: same session
  // back, and no new admission, entry or session document.
  const late = await f.call("START_QUALIFICATION", "alice", T + 7 * DAY + 5, {roundNumber: 1, attemptId: "attempt_0000000000000001"});
  assert.deepEqual(late.session, first.session);
  assert.equal(admissionRows(f).length, 1);
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${E}/entries/`)).length, 2);
  // A fresh attempt outside the window is refused by the window gate before any admission.
  await assert.rejects(f.call("START_QUALIFICATION", "bob", T + 7 * DAY, {roundNumber: 1, attemptId: "attempt_bob_0000000000001"}),
    {code: "FAILED_PRECONDITION"});
  assert.equal(admissionRows(f).length, 1);
});

test("ARENA_LESSON events are link-only end to end: no course download basis exists", async () => {
  const f = fixture();
  f.state.bank.source = {kind: "ARENA_LESSON", id: "arena-lesson", snapshotId: "arena-v7", reviewed: true, published: true, admitted: true};
  f.state.bank.questions.forEach((question) => { question.sourceId = "arena-lesson"; });
  const created = await f.call("CREATE", OWNER, T, {source: {kind: "ARENA_LESSON", id: "arena-lesson", snapshotId: "arena-v7"},
    startsAtMs: T, prizes: PRIZES, fundedBoxes: 12});
  const token = created.admissionLink.token;
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001", linkToken: "0".repeat(48)}), {code: "PERMISSION_DENIED"});
  // Even an attested download record for the arena source id is no basis (owner-visible reading).
  f.db.store.set(`${P.downloads}/${digest(["alice", "arena-lesson"])}`,
    {schemaVersion: 1, uid: "alice", courseId: "arena-lesson", downloadedAtMs: T});
  await assert.rejects(f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001"}), {code: "PERMISSION_DENIED"});
  const started = await f.call("START_QUALIFICATION", "alice", T + 100,
    {roundNumber: 1, attemptId: "attempt_0000000000000001", linkToken: token});
  assert.equal(started.status, "SESSION");
  assert.deepEqual(admissionRows(f).map(([, row]) => row.basis), ["LINK"]);
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
  const payload = f.payload("CREATE", OWNER, {source: SOURCE, startsAtMs: now, prizes: PRIZES, fundedBoxes: 12});
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

// Story 5.5: the plan §4 size matrix proven through the service seam. The anchors above bracket
// these: "all16 qualifiers can reach terminal bracket" and the "<2 qualifiers cancels" case are
// symmetric with 15 and 17; the large cohort reuses the calculation layer's own snapshot builder
// over 502 settled runs.
test("15 qualifiers seed the 16-slot bracket with one bye and finish with full placements", async () => {
  const people = Array.from({length: 15}, (_, i) => `player-${String(i).padStart(2, "0")}`);
  const f = fixture(people); await f.create(); await f.close();
  const bracket = event(f).bracket;
  assert.equal(bracket.status, "ACTIVE"); assert.equal(bracket.size, 16); assert.equal(bracket.entrants.length, 15);
  assert.deepEqual(bracket.rounds.map((round) => round.matches.length), [8, 4, 2, 1]);
  assert.equal(bracket.rounds.flatMap((round) => round.matches).length, 15);
  // Bye structure: seed 16 is absent, so the 1v16 node resolves as a bye for seed 1 at close.
  assert.deepEqual([bracket.rounds[0].matches[0].leftSeed, bracket.rounds[0].matches[0].rightSeed], [1, 16]);
  const bye = event(f).progress.resolved.find((row) => row.status === "BYE");
  assert.equal(bye.winnerUid, "player-00");
  assert.equal(event(f).progress.pending.some((row) => row.matchId === bracket.rounds[0].matches[0].matchId), false);
  // Every real match is played; the better seed always wins.
  while (event(f).progress.pending.length) {
    const pending = event(f).progress.pending[0];
    const match = bracket.rounds.flatMap((round) => round.matches).find((item) => item.matchId === pending.matchId);
    await play(f, match, pending.entrants.reduce((a, b) => a.seed <= b.seed ? a : b).uid);
  }
  assert.equal(event(f).status, "FINISHED");
  // Forms: 3 qualification + 15 bracket shared forms (the bye node still stages its form
  // structurally) — but no sessions and no verified runs exist for the bye match.
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${E}/forms/`)).length, 18);
  const byeMatchId = bracket.rounds[0].matches[0].matchId;
  assert.equal([...f.db.store.keys()].some((path) => path.includes(byeMatchId) && path.startsWith(`${P.sessions}/`)), false);
  // Exactly four placements exist; every eliminated player holds zero boxes.
  assert.equal(event(f).progress.placements.length, 4);
  assert.deepEqual(event(f).progress.placements.map((row) => [row.uid, row.rank, row.slot, row.technicalForfeit]),
    [["player-00", 1, "champion", false], ["player-01", 2, "runner-up", false],
     ["player-03", 3, "third-upper", false], ["player-02", 3, "third-lower", false]]);
  assert.deepEqual(["player-00", "player-01", "player-03", "player-02"].map((uid) => boxes(f, uid)), [5, 3, 1, 1]);
  assert.equal(people.filter((uid) => !["player-00", "player-01", "player-02", "player-03"].includes(uid))
    .every((uid) => boxes(f, uid) === 0), true);
  assert.equal(boxes(f, OWNER), 90);
  assert.deepEqual(event(f).escrow, {reservedBoxes: 12, remainingBoxes: 0, paidBoxes: 10, returnedBoxes: 2});
  // Reserve + four v1 row-key prize payouts + unused-fund return.
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${P.funding}/`)).length, 6);
});

test("a 17-row ranking at the freeze gate is refused typed with no partial effects", async () => {
  const people = Array.from({length: 17}, (_, i) => `player-${String(i).padStart(2, "0")}`);
  const f = fixture(people); await f.create();
  const complete = await f.dependencies.loadQualificationRanking({eventId: EVENT, forms: event(f).qualificationForms, endsAtMs: T + 7 * DAY});
  // The real builder truncates to 16; the >16 guard exists for an oversized publication.
  assert.equal(complete.ranking.length, 16); assert.equal(complete.totalRanked, 17);
  f.state.rankingOverride = {...complete, totalRanked: 17, ranking: [...complete.ranking, {uid: "player-16", place: 17}]};
  const before = structuredClone([...f.db.store]);
  await assert.rejects(f.close(), {code: "INVALID_RANKING"});
  assert.deepEqual([...f.db.store], before);
  assert.equal(event(f).status, "QUALIFICATION"); assert.equal(event(f).bracket, undefined);
  assert.equal(boxes(f, OWNER), 88);
  // Recovery: a corrected (≤16) ranking closes the same event to a normal bracket.
  f.state.rankingOverride = complete;
  const closed = await f.close();
  assert.equal(closed.status, "BRACKET");
  assert.equal(event(f).bracket.entrants.length, 16);
});

test("mid-CLOSE commit failure leaves no partial effects; the same operation id retries to a consistent bracket", async () => {
  const f = fixture(); await f.create();
  const payload = f.payload("CLOSE_QUALIFICATION", OWNER);
  const invoke = () => apply({db: f.db, uid: OWNER, payload, now: T + 7 * DAY, dependencies: f.dependencies, serverSeed: SEED});
  const before = structuredClone([...f.db.store]);
  f.db.failNextCommit();
  await assert.rejects(invoke(), /atomic commit failure/);
  assert.deepEqual([...f.db.store], before);
  assert.equal(event(f).status, "QUALIFICATION");
  assert.equal([...f.db.store.keys()].filter((path) => path.startsWith(`${E}/forms/`)).length, 3);
  const closed = await invoke();
  assert.equal(closed.status, "BRACKET");
  assert.equal(event(f).bracket.entrants.length, 2);
  const after = structuredClone([...f.db.store]);
  assert.deepEqual(await invoke(), closed);
  assert.deepEqual([...f.db.store], after);
});

test("repeat CLOSE replays the exact receipt; a fresh id restates the event without a second bracket", async () => {
  const f = fixture(); await f.create();
  const payload = f.payload("CLOSE_QUALIFICATION", OWNER);
  const invoke = () => apply({db: f.db, uid: OWNER, payload, now: T + 7 * DAY, dependencies: f.dependencies, serverSeed: SEED});
  const closed = await invoke();
  const after = structuredClone([...f.db.store]);
  assert.deepEqual(await invoke(), closed);
  assert.deepEqual([...f.db.store], after);
  const restated = await f.close();
  assert.equal(restated.status, "BRACKET");
  const added = [...f.db.store].filter(([path]) => !after.some(([existing]) => existing === path));
  assert.equal(added.length, 1); assert.ok(added[0][0].startsWith(`${P.operations}/`));
  for (const [path, value] of after) assert.deepEqual(f.db.store.get(path), value, path);
  assert.equal(event(f).bracket.rounds.flatMap((round) => round.matches).length, 1);
});

test("cutoff cohort pins D2: clamp holds, cutoff-cut attempt settles into the cohort, the cutoff itself refuses", async () => {
  const f = fixture(); await f.create();
  const cutoff = T + 7 * DAY;
  // Bob starts early: his envelope ends at its own duration edge, never reaching the cutoff.
  const early = await f.call("START_QUALIFICATION", "bob", T + 1000, {roundNumber: 1, attemptId: "attempt_b0000000000000001"});
  const late = await f.call("START_QUALIFICATION", "alice", cutoff - 600_000, {roundNumber: 1, attemptId: "attempt_a0000000000000001"});
  // D2 creation clamp: alice's envelope would cross the cutoff, so it is cut at exactly the cutoff.
  assert.equal(late.session.endsAtMs, cutoff);
  assert.ok(late.session.endsAtMs - late.session.startedAtMs < event(f).qualificationForms[0].maxDurationMs);
  assert.equal(early.session.endsAtMs, T + 1_201_000); // start + full form duration, far from the cutoff
  const answered = await applyTournamentSessionOperation({db: f.db, uid: "alice", now: cutoff - 599_000,
    payload: {kind: "ANSWER", ownerUid: "alice", operationId: "answer_a0000000000000001", sessionId: late.session.sessionId,
      formFingerprint: late.session.formFingerprint, questionIndex: 0,
      questionId: event(f).qualificationForms[0].questions[0].questionId,
      answerPayload: {type: "single-choice", selected: "a"}}});
  assert.equal(answered.status, "ANSWER_ACCEPTED");
  // Nothing at the cutoff moves: a start AT the cutoff is outside the window and stages nothing.
  const before = structuredClone([...f.db.store]);
  await assert.rejects(f.call("START_QUALIFICATION", "carol", cutoff, {roundNumber: 1, attemptId: "attempt_c0000000000000001"}),
    (error) => { assert.equal(error.code, "FAILED_PRECONDITION"); assert.equal(error.message, "OUTSIDE_QUALIFICATION_WINDOW"); return true; });
  assert.deepEqual([...f.db.store], before);
  assert.equal(admissionRows(f).length, 2);
  // The existing sweep settles each attempt at its own edge: bob at his envelope, alice cut at
  // the cohort cutoff with her answered prefix intact.
  const earlySwept = await advanceTournamentSession({db: f.db, sessionId: early.session.sessionId,
    operationId: "envelope_sweep_b00000000001", now: T + 1_202_000});
  assert.equal(earlySwept.status, "COMPLETED");
  const earlyRun = f.db.store.get(`${VERIFIED_RUNS}/${early.session.sessionId}`);
  assert.equal(earlyRun.completedAtMs, T + 1_201_000); assert.ok(earlyRun.completedAtMs < cutoff);
  const swept = await advanceTournamentSession({db: f.db, sessionId: late.session.sessionId,
    operationId: "cutoff_sweep_a00000000001", now: cutoff + 1000});
  // A late answer arriving after the cutoff but before the sweep is refused (timeliness bound).
  await assert.rejects(applyTournamentSessionOperation({db: f.db, uid: "alice", now: cutoff + 500,
    payload: {kind: "ANSWER", ownerUid: "alice", operationId: "answer_alice_late_0000001", sessionId: late.session.sessionId,
      formFingerprint: late.session.formFingerprint, questionIndex: 1,
      questionId: event(f).qualificationForms[0].questions[1].questionId,
      answerPayload: {type: "single-choice", selected: "b"}}}));
  assert.equal(swept.status, "COMPLETED");
  // One answered question of twenty: the exact score, not a threshold.
  assert.equal(swept.result.percentScore, 5);
  const zeroRun = f.db.store.get(`${VERIFIED_RUNS}/${early.session.sessionId}`);
  assert.equal(zeroRun.percentScore, 0);
  const run = f.db.store.get(`${VERIFIED_RUNS}/${late.session.sessionId}`);
  assert.equal(run.status, "SETTLED"); assert.equal(run.codeAnswer, `9${"1".repeat(19)}`);
  assert.equal(run.completedAtMs, cutoff); assert.equal(run.elapsedMs, cutoff - late.session.startedAtMs);
  assert.equal(f.db.store.get(`${P.sessions}/${late.session.sessionId}/answers/00`).kind, "ANSWER");
  const forced = f.db.store.get(`${P.sessions}/${late.session.sessionId}/answers/19`);
  assert.equal(forced.kind, "FORCED_CUTOFF"); assert.equal(forced.closedAtMs, cutoff);
  // Both settled attempts enter the cohort and seed the bracket through the same CLOSE.
  f.state.rankingOverride = buildQualificationRankingSnapshot({eventId: EVENT, forms: event(f).qualificationForms,
    endsAtMs: cutoff, snapshotId: "cutoff-cohort-pin-v1", verifiedRuns: [earlyRun, run], completeThroughMs: cutoff,
    pendingSessionCount: 0});
  const closed = await f.close();
  assert.equal(closed.status, "BRACKET");
  assert.equal(event(f).qualificationRanking.totalRanked, 2);
  assert.equal(event(f).qualificationRanking.verifiedRunCount, 2);
  assert.deepEqual(event(f).bracket.entrants.map((row) => [row.uid, row.seed]), [["alice", 1], ["bob", 2]]);
});

test("502-attempt cohort closes through the versioned seam; top16 seeded within recorded budgets", async () => {
  const started = performance.now();
  const people = Array.from({length: 502}, (_, i) => `player-${String(i).padStart(3, "0")}`);
  const f = fixture(people);
  // Budgets bound invocations, never population: per-transaction read and commit-write maxima.
  const metrics = {transactions: 0, reads: 0, maxTransactionReads: 0};
  const baseRun = f.db.runTransaction.bind(f.db);
  f.db.runTransaction = async (body) => { metrics.transactions++;
    return baseRun(async (transaction) => {
      let count = 0;
      try {
        const out = await body({...transaction,
          get: async (ref) => { count++; return transaction.get(ref); },
          getAll: async (...refs) => { count += refs.length; return transaction.getAll(...refs); }});
        metrics.maxTransactionReads = Math.max(metrics.maxTransactionReads, count); metrics.reads += count;
        return out;
      } catch (error) {
        metrics.maxTransactionReads = Math.max(metrics.maxTransactionReads, count); metrics.reads += count;
        throw error;
      }
    });
  };
  await f.create();
  const selected = await selectQualificationCalculation({db: f.db, eventId: EVENT, now: T + 7 * DAY});
  const c = selected.contract;
  const form = event(f).qualificationForms[0];
  const runs = people.map((uid, index) => ({eventId: EVENT, uid, status: "SETTLED",
    sessionId: qualificationSessionId(`attempt_${uid}_0000000001`), formId: form.formId, formFingerprint: form.fingerprint,
    verificationVersion: "server-assessment-v1", percentScore: 100 - (index % 101), elapsedMs: 1 + (index % 19_999),
    startedAtMs: T, completedAtMs: T + 1 + (index % 19_999)}));
  // The calculation layer's own published schema (schemaVersion 2 provenance, real freeze id),
  // built by its own snapshot builder: the seam consumes exactly what select→scan→calculate
  // publishes (that pipeline at [502,1] is proven in tournament-qualification-calculation.test.js).
  const ranking = {...buildQualificationRankingSnapshot({eventId: EVENT, forms: event(f).qualificationForms,
    endsAtMs: T + 7 * DAY, snapshotId: c.freezeId, verifiedRuns: runs, completeThroughMs: T + 7 * DAY, pendingSessionCount: 0}),
    schemaVersion: 2, freezeFenceId: c.freezeId, calculationVersions: {...VERSIONS}, calculationContractDigest: c.contractDigest,
    inputFingerprintVersion: "player-stream-chain-v1"};
  f.state.rankingOverride = ranking;
  const closed = await f.close();
  const ms = Math.round(performance.now() - started);
  assert.equal(closed.status, "BRACKET");
  const provenance = event(f).qualificationRanking;
  assert.equal(provenance.totalRanked, 502); assert.equal(provenance.verifiedRunCount, 502);
  assert.deepEqual(provenance.calculationVersions, VERSIONS);
  assert.equal(provenance.snapshotId, c.freezeId);
  assert.equal(provenance.calculationContractDigest, c.contractDigest);
  assert.equal(provenance.inputFingerprintVersion, "player-stream-chain-v1");
  assert.equal(provenance.verifiedRunsFingerprint, ranking.verifiedRunsFingerprint);
  const bracket = event(f).bracket;
  assert.equal(bracket.size, 16); assert.equal(bracket.entrants.length, 16);
  // Independent expectation: score desc, elapsed asc — not the injected ranking's own order.
  const expectedTop16 = [...runs].sort((a, b) => b.percentScore - a.percentScore || a.elapsedMs - b.elapsedMs)
    .slice(0, 16).map((row) => row.uid);
  assert.deepEqual(bracket.entrants.map((row) => row.uid), expectedTop16);
  assert.deepEqual(bracket.entrants.map((row) => row.seed), Array.from({length: 16}, (_, i) => i + 1));
  assert.deepEqual(bracket.rounds.map((round) => round.matches.length), [8, 4, 2, 1]);
  assert.deepEqual(event(f).progress.pending[0].entrants.map((row) => row.seed), [1, 16]);
  const commitWrites = f.db.commits.map((commit) => commit.length);
  console.log(JSON.stringify({case: "qualification-loop-seam", players: 502, attempts: 502, ms,
    transactions: metrics.transactions, reads: metrics.reads, maxTransactionReads: metrics.maxTransactionReads,
    maxCommitWrites: Math.max(...commitWrites), commitCount: commitWrites.length,
    rssBytes: process.memoryUsage().rss, rankingFingerprint: digest(ranking)}));
  assert.ok(metrics.maxTransactionReads <= 40);
  assert.ok(Math.max(...commitWrites) <= 80); // MAX_STAGED_WRITES
  assert.ok(ms < 30_000, `502 seam wall-clock budget, got ${ms}ms`);
});

test("legacy publication stays fenced after selection while the versioned CLOSE seam completes", async () => {
  const f = await versionedFixture();
  const before = structuredClone([...f.db.store]);
  await assert.rejects(freezeQualification({db: f.db, eventId: EVENT, now: T + 7 * DAY, pageSize: 50, maxPages: 10}),
    (error) => { assert.equal(error.code, "UNAVAILABLE");
      assert.equal(error.message, "Legacy publisher is fenced from the selected calculation"); return true; });
  assert.deepEqual([...f.db.store], before);
  const closed = await f.close();
  assert.equal(closed.status, "BRACKET");
  assert.equal(event(f).qualificationRanking.calculationContractDigest, f.state.rankingOverride.calculationContractDigest);
});

// Story 5.6: ONLINE-O1/O2 next-question window — read-path resolution (A4) and the D2/D3 pins.
const SESSION_NOW = T + 100;

async function startedQualification(f, attemptId, start = SESSION_NOW) {
  const started = await f.call("START_QUALIFICATION", "alice", start, {roundNumber: 1, attemptId});
  return {id: started.session.sessionId, fingerprint: started.session.formFingerprint,
    endsAtMs: started.session.endsAtMs, form: event(f).qualificationForms[0]};
}

test("mid-window reconnect delivers from the read path and never restarts the clocks", async () => {
  const f = fixture(); await f.create();
  const {id, form} = await startedQualification(f, "attempt_window_00000001");
  // The worker closes q0 at its deadline; the window opens UNDELIVERED (server-driven, AD-23).
  await advanceTournamentSession({db: f.db, sessionId: id, operationId: "worker_read_close_0000001", now: SESSION_NOW + 60_000});
  const stored = f.db.store.get(`${P.sessions}/${id}`);
  assert.equal(stored.window.state, "OPEN");
  assert.equal(stored.window.expiresAtMs, SESSION_NOW + 120_000);
  const read = now => f.db.runTransaction(transaction => readTournamentSession({db: f.db, transaction, uid: "alice", sessionId: id, now}));
  const reconnected = await read(SESSION_NOW + 65_000);
  assert.equal(reconnected.question.questionId, form.questions[1].questionId);
  assert.equal(reconnected.session.questionStartedAtMs, SESSION_NOW + 65_000);
  assert.equal(reconnected.session.questionEndsAtMs, SESSION_NOW + 125_000);
  assert.equal(reconnected.window.state, "DELIVERED");
  assert.equal(reconnected.deliveredNowMs, SESSION_NOW + 65_000);
  assert.ok(!JSON.stringify(reconnected).includes("correctOption"));
  const again = await read(SESSION_NOW + 66_000);
  assert.equal(again.session.questionStartedAtMs, SESSION_NOW + 65_000); // never re-anchored
  assert.equal(again.question.questionId, reconnected.question.questionId);
  assert.equal(f.db.store.get(`${P.sessions}/${id}`).window.delivery.deliveredAtMs, SESSION_NOW + 65_000);
});

test("an expired window read shows no question and writes nothing; the sweep settles at the edge", async () => {
  const f = fixture(); await f.create();
  const {id} = await startedQualification(f, "attempt_window_00000002");
  await advanceTournamentSession({db: f.db, sessionId: id, operationId: "worker_read_close_0000002", now: SESSION_NOW + 60_000});
  const before = structuredClone([...f.db.store]);
  const late = await f.db.runTransaction(transaction => readTournamentSession({db: f.db, transaction, uid: "alice", sessionId: id, now: SESSION_NOW + 200_000}));
  assert.equal(late.question, null); // visibility requires delivery; the edge is not a read concern
  assert.equal(late.window.state, "OPEN");
  assert.deepEqual([...f.db.store], before); // reads never close questions
  await advanceTournamentSession({db: f.db, sessionId: id, operationId: "worker_read_edge_00000001", now: SESSION_NOW + 201_000});
  const run = f.db.store.get(`${VERIFIED_RUNS}/${id}`);
  assert.equal(run.status, "SETTLED");
  assert.equal(run.completedAtMs, SESSION_NOW + 120_000); // the captured edge, inside the envelope
  assert.equal(run.percentScore, 0);
  const forced = f.db.store.get(`${P.sessions}/${id}/answers/01`);
  assert.equal(forced.kind, "FORCED_CUTOFF");
  assert.equal(forced.closedAtMs, SESSION_NOW + 120_000);
  assert.equal(forced.wasShown, false);
});

test("a window opened near the cohort cutoff is capped by the envelope and never crosses the cutoff (D2/D3)", async () => {
  const f = fixture(); await f.create();
  const cutoff = T + 7 * DAY;
  const {id} = await startedQualification(f, "attempt_cutoff_0000001", cutoff - 61_000);
  // The worker closes q0 one second before the cutoff; the captured window is that one second.
  await advanceTournamentSession({db: f.db, sessionId: id, operationId: "worker_cutoff_close_000001", now: cutoff - 1_000});
  const stored = f.db.store.get(`${P.sessions}/${id}`);
  assert.equal(stored.window.windowSeconds, 1);
  assert.equal(stored.window.expiresAtMs, cutoff);
  assert.equal(stored.window.state, "OPEN");
  const swept = await advanceTournamentSession({db: f.db, sessionId: id, operationId: "worker_cutoff_edge_000001", now: cutoff + 500});
  assert.equal(swept.status, "COMPLETED");
  const run = f.db.store.get(`${VERIFIED_RUNS}/${id}`);
  assert.equal(run.completedAtMs, cutoff); // the cohort cutoff pins the terminal edge exactly
  assert.equal(f.db.store.get(`${P.sessions}/${id}/answers/01`).closedAtMs, cutoff);
});

(async () => {
  for (const {name, body} of tests) { await body(); console.log(`ok - ${name}`); }
  console.log(`tournament-service: ${tests.length} behavioral tests passed`);
})().catch(error => { console.error(error); process.exitCode = 1; });
