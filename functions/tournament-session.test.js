"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {redact, restoreContent, translateSubmittedAnswer} = require("./question-redaction");
const {evaluateAnswer, computePercentScore} = require("./assessment-scoring");
const {tournamentDigest: digest} = require("./tournament-bank");
const {TOURNAMENT_PATHS: P} = require("./tournament-service");
const {applyTournamentSessionOperation: apply, advanceTournamentSession, SESSION_OPERATIONS: OPS, SESSION_INGRESS: ING,
  VERIFIED_RUNS: RUNS, VERIFICATION_VERSION, readTournamentSessionOperation, planTournamentSessionOperation, writeTournamentSessionOperation} = require("./tournament-session");
const {loadSettledMatchResults, verifiedTournamentRun} = require("./tournament-session-results");

const T = 1_800_000_000_000;
const UID = "alice";
const EVENT = "event_0000000000000001";
const MATCH = "match-immutable-1";
const tests = [];
function test(name, fn) { tests.push({name, fn}); }
function contentFor(type, id) {
  const base = {type, id, difficulty: "EASY", text: `Current question ${id}`};
  if (type === "SingleChoice") return {...base, options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"};
  if (type === "MultipleChoice") return {...base, options: ["a", "b", "c", "d"].map(id => ({id, text: id})), correctOptionIds: ["a", "b"]};
  if (type === "Ordering") return {...base, items: ["i1", "i2", "i3", "i4"].map(id => ({id, text: id}))};
  return {...base, blanks: [{id: "b1", correctCandidateId: "c1"}, {id: "b2", correctCandidateId: "c2"}],
    candidates: ["c1", "c2", "c3", "c4"].map(id => ({id, text: id}))};
}
function fixture({uid = UID, kind = "QUALIFICATION", firstType = "SingleChoice", duration = 1000, overallMs = 20_000} = {}) {
  const id = `${kind === "MATCH" ? "m" : "q"}_${digest([uid, kind])}`;
  const formId = `immutable-form-${kind}`;
  const pairs = Array.from({length: 20}, (_, i) => {
    const questionId = `q-${i}`;
    const content = contentFor(i === 0 ? firstType : "SingleChoice", questionId);
    const pair = redact(JSON.stringify(content), "EASY", {questionId, random: () => 0});
    assert.equal(pair.status, "redacted");
    const publicPayload = JSON.parse(pair.publicPayload);
    return {metadata: {questionId, type: content.type, difficulty: "EASY", contentVersion: 1, contentHash: digest(publicPayload), maxDurationMs: duration},
      publicPayload, answerKey: pair.key};
  });
  const fingerprint = digest(pairs.map(q => q.metadata));
  const publicForm = {schemaVersion: 1, formId, fingerprint, difficulty: "EASY", maxDurationMs: 20 * duration,
    questions: pairs.map(q => ({...q.metadata, publicPayload: q.publicPayload}))};
  const privateForm = {schemaVersion: 1, formId, fingerprint, timingVersion: "server-timer-test-v1",
    keys: pairs.map(q => ({questionId: q.metadata.questionId, contentVersion: 1, answerKey: q.answerKey}))};
  const S = `${P.sessions}/${id}`;
  const F = `${P.events}/${EVENT}/forms/${digest(formId)}`;
  const K = `${P.privateForms}/${EVENT}/forms/${digest(formId)}`;
  const session = {schemaVersion: 1, sessionId: id, uid, eventId: EVENT, kind, ...(kind === "MATCH" ? {matchId: MATCH} : {roundNumber: 1}),
    formId, formFingerprint: fingerprint, status: "STARTED", startedAtMs: T, endsAtMs: T + overallMs,
    questionIndex: 0, questionStartedAtMs: T, questionEndsAtMs: T + Math.min(duration, overallMs)};
  const db = premiumTestDb({[S]: session, [F]: publicForm, [K]: privateForm});
  let serial = 0;
  const payload = (kind, extra = {}) => ({kind, ownerUid: uid, operationId: `session_op_${String(++serial).padStart(20, "0")}`,
    sessionId: id, formFingerprint: fingerprint, ...extra});
  const call = (body, now = T + 10, ownerUid = uid) => apply({db, uid: ownerUid, payload: body, now});
  const answer = (answerPayload = {type: "single-choice", selected: "a"}, now = current().questionStartedAtMs + 10, extra = {}) => {
    const s = current(); return call(payload("ANSWER", {questionIndex: s.questionIndex, questionId: `q-${s.questionIndex}`, answerPayload, ...extra}), now);
  };
  const advance = now => call(payload("ADVANCE"), now);
  function current() { return db.store.get(S); }
  return {db, id, uid, S, F, K, fingerprint, pairs, payload, call, answer, advance, current};
}
const slot = (f, index) => f.db.store.get(`${f.S}/answers/${String(index).padStart(2, "0")}`);
const run = f => f.db.store.get(`${RUNS}/${f.id}`);
async function complete(f, count = 20) { let result; for (let i = 0; i < count; i++) result = await f.answer(); return result; }
const originalAnswer = (f, overrides = {}) => f.payload("ANSWER", {questionIndex: 0, questionId: "q-0", answerPayload: {type: "single-choice", selected: "a"}, ...overrides});

test("server scores answer and returns only next CURRENT redacted question", async () => {
  const f = fixture(); const result = await f.answer();
  assert.equal(result.status, "ANSWER_ACCEPTED"); assert.equal(slot(f, 0).scoreDigit, 9);
  assert.equal(result.currentQuestion.questionId, "q-1"); assert.equal(result.session.questionIndex, 1);
  assert.equal(result.session.questionStartedAtMs, T + 10); assert.equal(result.session.questionEndsAtMs, T + 1010);
  assert.ok(!JSON.stringify(result).includes("correctOptionId")); assert.ok(!JSON.stringify(result).includes('"q-19"'));
  assert.equal(run(f), undefined);
  assert.equal(result.session.scoreDigits, undefined); assert.equal(result.lastClosed.scoreDigit, undefined);
});
test("completed public response contains aggregate result only, never a per-question answer oracle", async () => {
  const f = fixture({kind: "MATCH"}); const response = await complete(f);
  assert.equal(response.result.percentScore, 100);
  assert.equal(response.result.codeAnswer, undefined);
  assert.equal(response.session.scoreDigits, undefined);
  assert.equal(response.lastClosed.scoreDigit, undefined);
  assert.equal(run(f).codeAnswer, "9".repeat(20));
});
test("wrong answer commits floor digit and cannot be changed with another id", async () => {
  const f = fixture(); await f.answer({type: "single-choice", selected: "b"});
  assert.equal(slot(f, 0).scoreDigit, 1);
  await assert.rejects(f.call(originalAnswer(f), T + 20), {code: "QUESTION_ALREADY_CLOSED"});
  assert.equal(f.current().questionIndex, 1); assert.equal(slot(f, 0).scoreDigit, 1);
});
test("multiple-choice partial score is exactly the existing assessment engine result", async () => {
  const f = fixture({firstType: "MultipleChoice"}); const answer = {type: "multiple-choice", selected: ["a"]};
  await f.answer(answer);
  assert.equal(slot(f, 0).scoreDigit, evaluateAnswer(restoreContent(f.pairs[0].publicPayload, f.pairs[0].answerKey), answer));
});
test("Ordering public ids translate exactly once through the existing engine", async () => {
  const f = fixture({firstType: "Ordering"}); const key = f.pairs[0].answerKey;
  const publicId = value => Object.keys(key.idMap).find(id => key.idMap[id] === value);
  await f.answer({type: "ordering", order: key.order.map(publicId)});
  assert.equal(slot(f, 0).scoreDigit, 9);
});
test("FillBlank public ids translate exactly once through the existing engine", async () => {
  const f = fixture({firstType: "FillBlank"}); const key = f.pairs[0].answerKey;
  const answer = {type: "fill-blank", filled: Object.fromEntries(Object.entries(key.blankToCandidate).map(([blank, value]) =>
    [blank, Object.keys(key.idMap).find(id => key.idMap[id] === value)]))};
  await f.answer(answer);
  assert.equal(slot(f, 0).scoreDigit, 9);
  assert.equal(evaluateAnswer(restoreContent(f.pairs[0].publicPayload, key), translateSubmittedAnswer(answer, key)), 9);
});
test("malformed client JSON is a committed wrong answer, not an omitted denominator", async () => {
  const f = fixture(); await f.answer("{"); assert.equal(slot(f, 0).scoreDigit, 1);
  await f.advance(T + 20_000); assert.equal(run(f).percentScore, 0); assert.match(run(f).codeAnswer, /^1{20}$/);
});
test("client score/elapsed/deadline/uid/future-index fields are refused without progress", async () => {
  const f = fixture(); const before = structuredClone([...f.db.store]);
  for (const extra of [{score: 9}, {percentScore: 100}, {elapsedMs: 0}, {now: T}, {uid: UID}, {served: [0]}]) {
    await assert.rejects(f.call({...originalAnswer(f), ...extra}), {code: "INVALID_ARGUMENT"});
  }
  await assert.rejects(f.call(originalAnswer(f, {questionIndex: 1, questionId: "q-1"})), {code: "QUESTION_ALREADY_CLOSED"});
  assert.deepEqual([...f.db.store], before);
});
test("immutable response replay returns before keys, question timer or current progress", async () => {
  const f = fixture(); const request = originalAnswer(f); const first = await f.call(request);
  f.db.store.delete(f.K); const before = structuredClone([...f.db.store]);
  assert.deepEqual(await f.call(request, T + 50_000), first); assert.deepEqual([...f.db.store], before);
});
test("changed replay payload is refused after final receipt", async () => {
  const f = fixture(); const request = originalAnswer(f); await f.call(request);
  await assert.rejects(f.call({...request, answerPayload: {type: "single-choice", selected: "b"}}), {code: "CONFLICTING_REPLAY"});
});
test("account switch cannot consume session or steal an existing operation receipt", async () => {
  const f = fixture(); const request = originalAnswer(f); await f.call(request);
  await assert.rejects(f.call({...request, ownerUid: "bob"}, T + 100, "bob"), {code: "PERMISSION_DENIED"});
  await assert.rejects(f.call(f.payload("ADVANCE", {ownerUid: "bob"}), T + 100, "bob"), {code: "PERMISSION_DENIED"});
});
test("form or question binding mismatch creates no ingress or answer", async () => {
  const f = fixture(); const before = structuredClone([...f.db.store]);
  await assert.rejects(f.call(originalAnswer(f, {formFingerprint: "a".repeat(64)})), {code: "CONFLICTING_REPLAY"});
  await assert.rejects(f.call(originalAnswer(f, {questionId: "q-1"})), {code: "CONFLICTING_REPLAY"});
  assert.deepEqual([...f.db.store], before);
});
test("answer just before question deadline is accepted; exact deadline is timed out", async () => {
  const f = fixture(); await f.answer(undefined, T + 999); assert.equal(slot(f, 0).scoreDigit, 9);
  const g = fixture(); const result = await g.answer(undefined, T + 1000);
  assert.equal(result.status, "QUESTION_TIMED_OUT"); assert.equal(slot(g, 0).scoreDigit, 1); assert.equal(slot(g, 0).kind, "TIMEOUT");
});
test("late old-index answer cannot be reinterpreted as an answer to the next served question", async () => {
  const f = fixture(); const old = originalAnswer(f); await f.advance(T + 1000);
  await assert.rejects(f.call(old, T + 1001), {code: "QUESTION_ALREADY_CLOSED"});
  assert.equal(f.current().questionIndex, 1); assert.equal(slot(f, 1), undefined);
});
test("ADVANCE before deadline never restarts or extends the current question timer", async () => {
  const f = fixture(); const first = await f.advance(T + 100); const second = await f.advance(T + 800);
  assert.equal(first.session.questionStartedAtMs, T); assert.equal(second.session.questionStartedAtMs, T);
  assert.equal(second.session.questionEndsAtMs, T + 1000); assert.equal(f.current().questionIndex, 0);
});
test("offline gap times out only the shown question and serves next now under original overall cap", async () => {
  const f = fixture(); const result = await f.advance(T + 8000);
  assert.equal(f.current().questionIndex, 1); assert.equal(slot(f, 0).closedAtMs, T + 1000);
  assert.equal(result.session.questionStartedAtMs, T + 8000); assert.equal(result.session.questionEndsAtMs, T + 9000);
  assert.equal(result.session.endsAtMs, T + 20_000); assert.equal(slot(f, 1), undefined);
});
test("next question duration is shortened near immutable overall deadline", async () => {
  const f = fixture(); const result = await f.advance(T + 19_900);
  assert.equal(result.session.questionEndsAtMs, T + 20_000); assert.equal(result.session.questionStartedAtMs, T + 19_900);
});
test("overall cutoff forces all20 denominator even after one correct answered question", async () => {
  const f = fixture(); await f.answer(); const result = await f.advance(T + 20_000);
  assert.equal(result.status, "COMPLETED"); assert.equal(result.currentQuestion, null);
  assert.equal(run(f).codeAnswer, `9${"1".repeat(19)}`); assert.equal(run(f).percentScore, 5);
  assert.equal(run(f).elapsedMs, 20_000); assert.equal(run(f).servedQuestionCount, 20);
  assert.equal(slot(f, 19).wasShown, false); assert.equal(slot(f, 19).kind, "FORCED_CUTOFF");
});
test("short qualification cutoff has same engine and no later than fixed close", async () => {
  const f = fixture({overallMs: 100}); await f.answer(undefined, T + 10); await f.advance(T + 500);
  assert.equal(run(f).completedAtMs, T + 100); assert.equal(run(f).elapsedMs, 100); assert.equal(run(f).percentScore, 5);
});
test("all answers finish early with server elapsed and existing percent math", async () => {
  const f = fixture(); const result = await complete(f);
  assert.equal(result.result.percentScore, 100); assert.equal(result.result.elapsedMs, 200);
  assert.equal(run(f).codeAnswer, "9".repeat(20)); assert.equal(run(f).percentScore, computePercentScore(run(f).codeAnswer));
  assert.equal(run(f).verificationVersion, VERIFICATION_VERSION);
});
test("no re-answer after finished session even using a fresh operation id", async () => {
  const f = fixture(); await complete(f);
  await assert.rejects(f.call(originalAnswer(f)), {code: "QUESTION_ALREADY_CLOSED"});
  const result = await f.advance(T + 50_000); assert.equal(result.result.elapsedMs, 200); assert.equal(result.result.percentScore, 100);
});
test("missing key saves first server ingress but no answer, score or success receipt", async () => {
  const f = fixture(); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request, T + 100), {code: "UNAVAILABLE"});
  const pending = f.db.store.get(`${ING}/${digest(request.operationId)}`);
  assert.equal(pending.firstReceivedAtMs, T + 100); assert.equal(pending.status, "PENDING");
  assert.equal(f.current().pendingAnswerOperationId, request.operationId); assert.equal(f.current().questionIndex, 0);
  assert.equal(slot(f, 0), undefined); assert.equal(run(f), undefined); assert.equal(f.db.store.has(`${OPS}/${digest(request.operationId)}`), false);
});
test("key repair honors timely ingress after deadline without allowing replacement answer id", async () => {
  const f = fixture(); const keys = f.db.store.get(f.K); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request, T + 100), {code: "UNAVAILABLE"});
  await assert.rejects(f.call(originalAnswer(f), T + 200), {code: "ANSWER_PENDING"});
  f.db.store.set(f.K, keys); const result = await f.call(request, T + 5000);
  assert.equal(result.status, "ANSWER_ACCEPTED"); assert.equal(slot(f, 0).scoreDigit, 9); assert.equal(slot(f, 0).receivedAtMs, T + 100);
  assert.equal(result.session.questionStartedAtMs, T + 5000); assert.equal(result.session.endsAtMs, T + 20_000);
});
test("pending same id cannot mutate payload before keys repair", async () => {
  const f = fixture(); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request), {code: "UNAVAILABLE"});
  await assert.rejects(f.call({...request, answerPayload: "{}"}), {code: "CONFLICTING_REPLAY"});
});
test("ADVANCE repairing one pending answer cannot capture another account's ingress operation id", async () => {
  const f = fixture(); const keys = f.db.store.get(f.K); f.db.store.delete(f.K);
  const alice = {...originalAnswer(f), operationId: "pending_alice_00000001"};
  await assert.rejects(f.call(alice), {code: "UNAVAILABLE"});
  const g = fixture({uid: "bob"}); g.db.store.delete(g.K);
  const bob = {...originalAnswer(g), operationId: "pending_bob_000000001"};
  await assert.rejects(g.call(bob), {code: "UNAVAILABLE"});
  for (const [path, value] of g.db.store) f.db.store.set(path, value);
  f.db.store.set(f.K, keys); const before = structuredClone([...f.db.store]);
  await assert.rejects(apply({db: f.db, uid: "bob", now: T + 5000,
    payload: g.payload("ADVANCE", {operationId: alice.operationId})}), {code: "PERMISSION_DENIED"});
  assert.deepEqual([...f.db.store], before);
  await f.call(alice, T + 5000); assert.equal(slot(f, 0).scoreDigit, 9);
});
test("settled-session polling cannot capture another account's still-pending ingress id", async () => {
  const f = fixture(); f.db.store.delete(f.K);
  const alice = {...originalAnswer(f), operationId: "pending_alice_00000001"};
  await assert.rejects(f.call(alice), {code: "UNAVAILABLE"});
  const g = fixture({uid: "bob"}); await complete(g);
  for (const [path, value] of g.db.store) f.db.store.set(path, value);
  const before = structuredClone([...f.db.store]);
  await assert.rejects(apply({db: f.db, uid: "bob", now: T + 5000,
    payload: g.payload("ADVANCE", {operationId: alice.operationId})}), {code: "PERMISSION_DENIED"});
  assert.deepEqual([...f.db.store], before);
});
test("trusted worker repairs pending original answer and writes its immutable reply", async () => {
  const f = fixture(); const keys = f.db.store.get(f.K); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request, T + 100), {code: "UNAVAILABLE"}); f.db.store.set(f.K, keys);
  await advanceTournamentSession({db: f.db, sessionId: f.id, operationId: "worker_advance_00000001", now: T + 2000});
  assert.equal(slot(f, 0).scoreDigit, 9); assert.equal(f.current().pendingAnswerOperationId, undefined);
  const receipt = f.db.store.get(`${OPS}/${digest(request.operationId)}`);
  assert.deepEqual(await f.call(request, T + 10_000), receipt.response);
});
test("pending last answer repaired after overall cutoff keeps its original early finish elapsed", async () => {
  const f = fixture(); await complete(f, 19); const keys = f.db.store.get(f.K); f.db.store.delete(f.K);
  const request = f.payload("ANSWER", {questionIndex: 19, questionId: "q-19", answerPayload: {type: "single-choice", selected: "a"}});
  await assert.rejects(f.call(request, T + 200), {code: "UNAVAILABLE"}); f.db.store.set(f.K, keys);
  await f.call(request, T + 30_000); assert.equal(run(f).elapsedMs, 200); assert.equal(run(f).percentScore, 100);
});
test("pending first answer repair after cutoff preserves that answer but never extends overall clock", async () => {
  const f = fixture(); const keys = f.db.store.get(f.K); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request, T + 100), {code: "UNAVAILABLE"}); f.db.store.set(f.K, keys);
  await f.call(request, T + 30_000); assert.equal(run(f).percentScore, 5); assert.equal(run(f).elapsedMs, 20_000);
});
test("late answer cannot claim durable timely ingress while keys are missing", async () => {
  const f = fixture(); f.db.store.delete(f.K); const request = originalAnswer(f);
  await assert.rejects(f.call(request, T + 1000), {code: "UNAVAILABLE"});
  assert.equal(f.db.store.has(`${ING}/${digest(request.operationId)}`), false); assert.equal(f.current().pendingAnswerOperationId, undefined);
});
test("timeout with missing private key remains unscored and retryable, never a played zero", async () => {
  const f = fixture(); f.db.store.delete(f.K); const before = structuredClone([...f.db.store]);
  await assert.rejects(f.advance(T + 30_000), {code: "UNAVAILABLE"}); assert.deepEqual([...f.db.store], before); assert.equal(run(f), undefined);
});
test("corrupted future key, wrong version or missing public payload cannot fabricate timeout scores", async () => {
  for (const change of [f => { f.db.store.get(f.K).keys[19].answerKey.correctOptionId = "absent"; },
    f => { f.db.store.get(f.K).keys[19].contentVersion = 2; }, f => { delete f.db.store.get(f.F).questions[19].publicPayload; }]) {
    const f = fixture(); change(f); await assert.rejects(f.answer(), {code: "UNAVAILABLE"});
    assert.equal(slot(f, 0), undefined); assert.equal(run(f), undefined); assert.ok(f.current().pendingAnswerOperationId);
  }
});
test("missing public form cannot claim a slot whose question identity cannot be verified", async () => {
  const f = fixture(); f.db.store.delete(f.F);
  await assert.rejects(f.answer(), {code: "UNAVAILABLE"}); assert.equal(f.current().pendingAnswerOperationId, undefined);
});
test("committed answer/score corruption blocks settlement rather than silently rescoring", async () => {
  const f = fixture(); await f.answer(); f.db.store.get(`${f.S}/answers/00`).scoreDigit = 1;
  await assert.rejects(f.advance(T + 20_000), {code: "UNAVAILABLE"}); assert.equal(run(f), undefined);
});
test("final atomic commit failure leaves last question open and no final run or response receipt", async () => {
  const f = fixture(); await complete(f, 19); const before = structuredClone([...f.db.store]);
  f.db.failNextCommit(); await assert.rejects(f.answer(), /atomic commit failure/); assert.deepEqual([...f.db.store], before);
  await f.answer(); assert.equal(run(f).percentScore, 100);
});
test("ingress commit failure does not leave an orphan reservation", async () => {
  const f = fixture(); f.db.store.delete(f.K); const before = structuredClone([...f.db.store]); f.db.failNextCommit();
  await assert.rejects(f.answer(), /atomic commit failure/); assert.deepEqual([...f.db.store], before);
});
test("backwards server clock cannot reopen or restart a shown question", async () => {
  const f = fixture(); await f.advance(T + 500); const result = await f.advance(T + 100);
  assert.equal(result.serverNowMs, T + 500); assert.equal(result.session.questionStartedAtMs, T); assert.equal(result.session.questionEndsAtMs, T + 1000);
});
test("MATCH uses same scorer and authoritative loader remains pending until both settle", async () => {
  const f = fixture({kind: "MATCH"}); const g = fixture({uid: "bob", kind: "MATCH"});
  for (const [path, value] of g.db.store) f.db.store.set(path, value);
  const descriptors = [f.current(), g.current()];
  const load = () => f.db.runTransaction(transaction => loadSettledMatchResults({db: f.db, transaction, eventId: EVENT, matchId: MATCH, sessions: descriptors, nowMs: T + 30_000}));
  assert.equal(await load(), null); await complete(f); assert.equal(await load(), null);
  await advanceTournamentSession({db: f.db, sessionId: g.id, operationId: "worker_advance_bob_000001", now: T + 20_000});
  const results = await load(); assert.equal(results.length, 2); assert.deepEqual(results.map(row => row.percentScore), [100, 0]);
});
test("authoritative result verifier refuses forged percent, denominator, elapsed or session identity", async () => {
  const f = fixture(); await complete(f); const valid = run(f);
  for (const changed of [{percentScore: 99}, {servedQuestionCount: 19}, {elapsedMs: 1}, {uid: "bob"}, {verificationVersion: "client"}, {codeAnswer: "0".repeat(20)}]) {
    assert.throws(() => verifiedTournamentRun(f.current(), {...valid, ...changed}, f.current(), T + 30_000), {code: "UNAVAILABLE"});
  }
});
test("composable read/plan/write executes under strict all-reads-before-writes transaction", async () => {
  const f = fixture(); const payload = originalAnswer(f);
  const response = await f.db.runTransaction(async transaction => {
    const snapshot = await readTournamentSessionOperation({db: f.db, transaction, uid: UID, payload});
    const plan = planTournamentSessionOperation({snapshot, now: T + 10}); writeTournamentSessionOperation({db: f.db, transaction, plan}); return plan.response;
  });
  assert.equal(response.status, "ANSWER_ACCEPTED"); assert.equal(slot(f, 0).scoreDigit, 9);
});
test("oversized answer and nonexistent worker session are rejected without writes", async () => {
  const f = fixture(); const before = structuredClone([...f.db.store]);
  await assert.rejects(f.answer("x".repeat(20_000)), {code: "INVALID_ARGUMENT"});
  await assert.rejects(advanceTournamentSession({db: f.db, sessionId: `q_${"f".repeat(64)}`, operationId: "worker_absent_00000001", now: T}), {code: "NOT_FOUND"});
  assert.deepEqual([...f.db.store], before);
});

(async () => { for (const {name, fn} of tests) { await fn(); console.log(`ok - ${name}`); } console.log(`tournament-session: ${tests.length} behavioral tests passed`); })()
  .catch(error => { console.error(error); process.exitCode = 1; });
