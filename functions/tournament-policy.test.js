"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  READY_WINDOW_MS, QUALIFICATION_DURATION_MS, STAGE_DURATION_MS,
  preflightPool, validatePrizeSchedule, createEventPlan, qualificationForms, planQualificationAttempt,
  selectQualifiers, buildBracket, planMeetingProposal, evaluateMeeting, resolvePlayedMatch,
  progressBracket, planPrizePayouts,
} = require("./tournament-policy");

const source = {kind: "COURSE", id: "course", snapshotId: "snapshot-1", reviewed: true, published: true};
const prizes = {first: 100, second: 50, third: 25}; // Test fixture amounts, not policy defaults.
function pool(easy = 60, hard = 20) {
  return [
    ...Array.from({length: easy}, (_, i) => ({questionId: `e${i}`, difficulty: "EASY"})),
    ...Array.from({length: hard}, (_, i) => ({questionId: `h${i}`, difficulty: "HARD"})),
  ].map((q) => ({...q, type: "SingleChoice", sourceId: "course", reviewed: true, published: true,
    contentVersion: 1, contentHash: `hash-${q.questionId}`, maxDurationMs: 1000}));
}
function event(questions = pool()) {
  return createEventPlan({eventId: "event", source, questions, startsAtMs: 1000, prizes, fundedBoxes: 200});
}
function ranking(count) {
  return Array.from({length: count}, (_, i) => ({uid: `player-${i + 1}`, place: i + 1,
    ratingPoints: 1000 - i}));
}
function bracket(count) { return buildBracket({event: event(), ranking: ranking(count), serverSeed: "server-secret"}); }
function match() {
  return {matchId: "match", participantUids: ["a", "b"], stageStartsAtMs: 0,
    stageEndsAtMs: STAGE_DURATION_MS, formMaxDurationMs: 20000};
}
function agreement(m = match(), slotStartsAtMs = 1000) {
  return {
    proposals: [planMeetingProposal({match: m, uid: "a", slotStartsAtMs, nowMs: 0})],
    confirmations: [{uid: "a", slotStartsAtMs, acceptedAtMs: 10},
      {uid: "b", slotStartsAtMs, acceptedAtMs: 20}],
  };
}
function played(pending) {
  return resolvePlayedMatch({matchId: pending.matchId, entrants: pending.entrants,
    results: pending.entrants.map((p) => ({uid: p.uid, percentScore: 80, elapsedMs: 1000}))});
}

test("preflight requires reviewed published course or admitted Arena source and enough eligible questions", () => {
  assert.equal(preflightPool({source, questions: pool()}).easyQuestions.length, 60);
  assert.throws(() => preflightPool({source, questions: pool(59, 20)}), /60/);
  assert.throws(() => preflightPool({source, questions: pool(60, 19)}), /20/);
  assert.throws(() => preflightPool({source: {...source, published: false}, questions: pool()}), /reviewed published/);
  assert.throws(() => preflightPool({source: {...source, kind: "ARENA_LESSON"}, questions: pool()}), /admitted/);
  assert.equal(preflightPool({source: {...source, kind: "ARENA_LESSON", admitted: true}, questions: pool()})
    .source.kind, "ARENA_LESSON");
  const surveyInstead = pool(); surveyInstead[0].type = "Survey";
  assert.throws(() => preflightPool({source, questions: surveyInstead}), /60/);
  const unreviewed = pool(); unreviewed[0].reviewed = false;
  assert.throws(() => preflightPool({source, questions: unreviewed}), /60/);
  assert.throws(() => preflightPool({source, questions: [...pool(), pool()[0]]}), /duplicate questionId/);
  const foreign = pool(); foreign[0].sourceId = "other";
  assert.throws(() => preflightPool({source, questions: foreign}), /another source/);
});

test("three common seeded EASY forms are deterministic and use 60 distinct immutable question references", () => {
  const questions = pool(90, 40);
  const e = event(questions);
  const a = qualificationForms({event: e, serverSeed: "secret"});
  const b = qualificationForms({event: event([...questions].reverse()), serverSeed: "secret"});
  assert.deepEqual(a, b);
  assert.equal(a.length, 3);
  assert.ok(a.every((f) => f.questions.length === 20 && f.difficulty === "EASY" && f.maxDurationMs === 20000));
  assert.equal(new Set(a.flatMap((f) => f.questions.map((q) => q.questionId))).size, 60);
  assert.notDeepEqual(a, qualificationForms({event: e, serverSeed: "different-secret"}));
  const before = JSON.stringify(e);
  questions[0].contentHash = "mutated-after-snapshot";
  assert.equal(JSON.stringify(e), before);
  assert.ok(a.every((f) => f.questions.every((q) => q.contentVersion === 1 && q.contentHash.startsWith("hash-"))));
  assert.equal(e.qualification.endsAtMs - e.qualification.startsAtMs, QUALIFICATION_DURATION_MS);
});

test("qualification permits one attempt per round, including abort; retries are deterministic", () => {
  const e = event(); const input = {event: e, uid: "u", roundNumber: 1, attemptId: "a", nowMs: 1000};
  const first = planQualificationAttempt(input);
  assert.equal(first.allowed, true);
  const aborted = {...first.attempt, status: "ABORTED"};
  assert.equal(planQualificationAttempt({...input, existingAttempts: [aborted]}).replay, true);
  assert.equal(planQualificationAttempt({...input, attemptId: "new", existingAttempts: [aborted]}).reason, "ROUND_ALREADY_USED");
  assert.equal(planQualificationAttempt({...input, roundNumber: 2, attemptId: "b", existingAttempts: [aborted]}).allowed, true);
  assert.equal(planQualificationAttempt({...input, nowMs: 999}).allowed, false);
  assert.equal(planQualificationAttempt({...input, nowMs: e.qualification.endsAtMs}).allowed, false);
  assert.equal(planQualificationAttempt({...input, nowMs: e.qualification.endsAtMs - 1}).allowed, true);
  assert.throws(() => planQualificationAttempt({...input, roundNumber: 4}), /three rounds/);
  assert.throws(() => planQualificationAttempt({...input, uid: "other", existingAttempts: [aborted]}), /already bound/);
});

test("top16 uses authoritative LS places without recalculating ratings", () => {
  const rows = ranking(20).reverse().map((r) => ({...r, ratingPoints: r.place * 1000}));
  const selected = selectQualifiers(rows);
  assert.equal(selected.length, 16);
  assert.equal(selected[0].uid, "player-1");
  assert.equal(selected[15].uid, "player-16");
  assert.deepEqual(selected.map((p) => p.seed), Array.from({length: 16}, (_, i) => i + 1));
  assert.throws(() => selectQualifiers([{uid: "a", place: 1}, {uid: "b", place: 1}]), /duplicate/);
});

test("standard bracket places top1/top2 in opposite halves, has no bronze game and 3-day stages", () => {
  const b = bracket(16);
  assert.equal(b.rounds.length, 4);
  assert.deepEqual(b.rounds.map((r) => r.matches.length), [8, 4, 2, 1]);
  assert.deepEqual(b.rounds[0].matches.map((m) => [m.leftSeed, m.rightSeed]),
    [[1, 16], [8, 9], [4, 13], [5, 12], [2, 15], [7, 10], [3, 14], [6, 11]]);
  b.rounds.forEach((r, i) => {
    assert.equal(r.endsAtMs - r.startsAtMs, STAGE_DURATION_MS);
    if (i) assert.equal(r.startsAtMs, b.rounds[i - 1].endsAtMs);
    r.matches.forEach((m) => {
      assert.equal(m.sharedForm.questions.length, 20);
      assert.ok(m.sharedForm.questions.every((q) => q.difficulty === "HARD"));
      assert.equal(new Set(m.sharedForm.questions.map((q) => q.questionId)).size, 20);
      assert.equal(m.coordinationDeadlineMs + READY_WINDOW_MS + m.sharedForm.maxDurationMs, m.stageEndsAtMs);
    });
  });
  // A 20-question HARD bank is explicitly allowed, so global non-repetition is not promised.
  assert.deepEqual(new Set(b.rounds[0].matches[0].sharedForm.questions.map((q) => q.questionId)),
    new Set(b.rounds[0].matches[1].sharedForm.questions.map((q) => q.questionId)));
  assert.deepEqual(b, bracket(16));
});

test("zero/one qualifier cancels; tiny entrant counts finish with automatic byes and no fake losers", () => {
  for (const count of [0, 1]) {
    const b = bracket(count);
    assert.equal(b.status, "CANCELLED");
    assert.deepEqual(progressBracket({bracket: b}).placements, []);
  }
  for (const count of [2, 3, 5, 15]) {
    const b = bracket(count); const outcomes = [];
    let progress = progressBracket({bracket: b});
    for (let i = 0; i < 5 && progress.status !== "FINISHED"; i++) {
      assert.ok(progress.pending.length);
      outcomes.push(...progress.pending.map(played));
      progress = progressBracket({bracket: b, outcomes});
    }
    assert.equal(progress.status, "FINISHED");
    assert.equal(progress.placements.find((p) => p.rank === 1).uid, "player-1");
    assert.equal(progress.placements.filter((p) => p.rank === 3).length, count === 2 ? 0 : count === 3 ? 1 : 2);
    assert.equal(new Set(progress.placements.map((p) => p.uid)).size, progress.placements.length);
  }
});

test("meeting cutoff: one offers -> other forfeits; both offer without agreement -> double out", () => {
  const m = match();
  const cutoff = m.stageEndsAtMs - READY_WINDOW_MS - m.formMaxDurationMs;
  const a = planMeetingProposal({match: m, uid: "a", slotStartsAtMs: cutoff, nowMs: 0});
  const b = planMeetingProposal({match: m, uid: "b", slotStartsAtMs: cutoff - 1, nowMs: 0});
  assert.equal(evaluateMeeting({match: m, proposals: [a], nowMs: cutoff - 1}).status, "WAITING_FOR_AGREEMENT");
  const one = evaluateMeeting({match: m, proposals: [a], nowMs: cutoff});
  assert.equal(one.status, "FORFEIT"); assert.equal(one.winnerUid, "a"); assert.equal(one.loserUid, "b");
  assert.equal(evaluateMeeting({match: m, proposals: [a, b], nowMs: cutoff}).status, "DOUBLE_OUT");
  assert.equal(evaluateMeeting({match: m, nowMs: cutoff}).status, "DOUBLE_OUT");
  assert.throws(() => planMeetingProposal({match: m, uid: "a", slotStartsAtMs: cutoff + 1, nowMs: 0}), /window/);
  assert.throws(() => planMeetingProposal({match: m, uid: "a", slotStartsAtMs: cutoff, nowMs: cutoff}), /window/);
});

test("mutual confirmation is required; fake clock starts only after both ready and never pauses", () => {
  const m = match(); const agreed = agreement(m);
  const clock = {now: 100};
  const run = (extra = {}) => evaluateMeeting({match: m, ...agreed, nowMs: clock.now, ...extra});
  assert.equal(run().status, "WAITING_FOR_WINDOW");
  clock.now = 1000;
  assert.equal(run().status, "WAITING_FOR_READY");
  const readySignals = [{uid: "a", acceptedAtMs: 1100}, {uid: "b", acceptedAtMs: 1200}];
  clock.now = 1199;
  assert.equal(run({readySignals}).status, "WAITING_FOR_READY");
  clock.now = 1200;
  const started = run({readySignals});
  assert.equal(started.status, "STARTED"); assert.equal(started.startedAtMs, 1200);
  assert.equal(started.endsAtMs, 21200); assert.equal(started.resultDue, false);
  clock.now = 21200;
  const later = run({readySignals});
  assert.equal(later.endsAtMs, started.endsAtMs); assert.equal(later.resultDue, true);
  const onlyOneConfirmed = run({confirmations: [agreed.confirmations[0]], readySignals});
  assert.equal(onlyOneConfirmed.status, "WAITING_FOR_AGREEMENT");
});

test("one-hour agreed window is half-open: absent one loses, both absent are out", () => {
  const m = match(); const agreed = agreement(m); const end = 1000 + READY_WINDOW_MS;
  const readySignals = [{uid: "a", acceptedAtMs: 1000}, {uid: "b", acceptedAtMs: end}];
  assert.equal(evaluateMeeting({match: m, ...agreed, readySignals, nowMs: end - 1}).status, "WAITING_FOR_READY");
  const result = evaluateMeeting({match: m, ...agreed, readySignals, nowMs: end});
  assert.equal(result.status, "FORFEIT"); assert.equal(result.winnerUid, "a");
  assert.equal(evaluateMeeting({match: m, ...agreed, nowMs: end}).status, "DOUBLE_OUT");
  const cutoff = m.stageEndsAtMs - READY_WINDOW_MS - m.formMaxDurationMs;
  const lastSlot = agreement(m, cutoff);
  const atLastMoment = evaluateMeeting({match: m, ...lastSlot,
    readySignals: [{uid: "a", acceptedAtMs: cutoff}, {uid: "b", acceptedAtMs: cutoff + READY_WINDOW_MS - 1}],
    nowMs: cutoff + READY_WINDOW_MS - 1});
  assert.ok(atLastMoment.endsAtMs < m.stageEndsAtMs);
});

test("first mutually confirmed slot is fixed despite later alternative agreements", () => {
  const m = match();
  const first = agreement(m, 1000); const later = agreement(m, 2000);
  later.confirmations = later.confirmations.map((c) => ({...c, acceptedAtMs: c.acceptedAtMs + 50}));
  const result = evaluateMeeting({match: m,
    proposals: [...later.proposals, ...first.proposals], confirmations: [...later.confirmations, ...first.confirmations], nowMs: 100});
  assert.equal(result.slotStartsAtMs, 1000);
  assert.throws(() => evaluateMeeting({match: m, proposals: first.proposals,
    confirmations: [{uid: "outsider", slotStartsAtMs: 1000, acceptedAtMs: 10}], nowMs: 100}), /participant/);
});

test("played result compares percent, then elapsed time, then qualification seed", () => {
  const entrants = [{uid: "a", seed: 1}, {uid: "b", seed: 16}];
  const decide = (a, b) => resolvePlayedMatch({matchId: "m", entrants, results: [{uid: "a", ...a}, {uid: "b", ...b}]});
  assert.equal(decide({percentScore: 90, elapsedMs: 500}, {percentScore: 89, elapsedMs: 1}).winnerUid, "a");
  assert.equal(decide({percentScore: 90, elapsedMs: 500}, {percentScore: 90, elapsedMs: 499}).winnerUid, "b");
  assert.equal(decide({percentScore: 90, elapsedMs: 500}, {percentScore: 90, elapsedMs: 500}).winnerUid, "a");
  assert.throws(() => decide({percentScore: 101, elapsedMs: 1}, {percentScore: 0, elapsedMs: 1}), /100/);
});

test("double-outs propagate to empty nodes; technical losers never receive second or third", () => {
  const b = bracket(4); const initial = progressBracket({bracket: b});
  const first = initial.pending[0]; const second = initial.pending[1];
  const technical = {matchId: first.matchId, status: "FORFEIT", winnerUid: first.entrants[0].uid, loserUid: first.entrants[1].uid};
  const outcomes = [technical, played(second)];
  let progress = progressBracket({bracket: b, outcomes});
  outcomes.push(played(progress.pending[0]));
  progress = progressBracket({bracket: b, outcomes});
  assert.equal(progress.status, "FINISHED");
  assert.equal(progress.placements.filter((p) => p.rank === 3).length, 1);
  assert.ok(progress.placements.every((p) => p.uid !== technical.loserUid));
  assert.equal(progress.placements.find((p) => p.rank === 1).uid, technical.winnerUid);
  const double = initial.pending.map((m) => ({matchId: m.matchId, status: "DOUBLE_OUT", winnerUid: null, loserUid: null}));
  const empty = progressBracket({bracket: b, outcomes: double});
  assert.equal(empty.status, "FINISHED"); assert.deepEqual(empty.placements, []);
  assert.equal(empty.resolved[empty.resolved.length - 1].status, "EMPTY");
  const two = bracket(2); const pair = progressBracket({bracket: two}).pending[0];
  const final = progressBracket({bracket: two, outcomes: [{matchId: pair.matchId, status: "FORFEIT",
    winnerUid: pair.entrants[0].uid, loserUid: pair.entrants[1].uid}]});
  assert.deepEqual(final.placements.map((p) => p.rank), [1]);
});

test("terminal outcomes are immutable, belong to the match, and cannot precede feeder matches", () => {
  const b = bracket(4); const first = progressBracket({bracket: b}).pending[0]; const result = played(first);
  assert.throws(() => progressBracket({bracket: b, outcomes: [result, {...result, winnerUid: result.loserUid}]}), /conflicting/);
  assert.throws(() => progressBracket({bracket: b, outcomes: [{...result, winnerUid: "outsider"}]}), /participants/);
  assert.throws(() => progressBracket({bracket: b, outcomes: [{...result, matchId: b.rounds[1].matches[0].matchId}]}), /feeder/);
  const reordered = {loserUid: result.loserUid, winnerUid: result.winnerUid, status: result.status, matchId: result.matchId};
  assert.deepEqual(progressBracket({bracket: b, outcomes: [result, reordered]}),
    progressBracket({bracket: b, outcomes: [result]}));
});

test("double-out feeds a bye without inventing another played match or a losing participant", () => {
  const b = bracket(4); const pending = progressBracket({bracket: b}).pending;
  const real = played(pending[0]);
  const progress = progressBracket({bracket: b, outcomes: [real,
    {matchId: pending[1].matchId, status: "DOUBLE_OUT", winnerUid: null, loserUid: null}]});
  assert.equal(progress.status, "FINISHED");
  assert.equal(progress.resolved[progress.resolved.length - 1].status, "BYE");
  assert.deepEqual(progress.placements.map((p) => p.rank), [1, 3]);
  assert.equal(progress.placements[0].uid, real.winnerUid);
  assert.equal(progress.placements[1].uid, real.loserUid);
});

test("fund validates two thirds, rejects fourth place, conserves paid plus unused and keeps payout IDs stable", () => {
  assert.equal(validatePrizeSchedule(prizes, 200).maximumLiabilityBoxes, 200);
  assert.throws(() => validatePrizeSchedule(prizes, 199), /insufficient/);
  assert.throws(() => validatePrizeSchedule({...prizes, fourth: 1}, 201), /fourth/);
  const placements = [
    {uid: "a", rank: 1, slot: "champion", technicalForfeit: false},
    {uid: "b", rank: 2, slot: "runner-up", technicalForfeit: false},
    {uid: "c", rank: 3, slot: "third-upper", technicalForfeit: false},
    {uid: "d", rank: 3, slot: "third-lower", technicalForfeit: false},
  ];
  const full = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements});
  assert.equal(full.paidBoxes, 200); assert.equal(full.unusedReturnBoxes, 0);
  assert.deepEqual(full, planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: [...placements].reverse()}));
  const partial = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 230,
    placements: placements.map((p) => ({...p, technicalForfeit: p.rank === 2 || p.slot === "third-lower"}))});
  assert.equal(partial.paidBoxes, 125); assert.equal(partial.unusedReturnBoxes, 105);
  assert.equal(partial.paidBoxes + partial.unusedReturnBoxes, partial.fundedBoxes);
  const cancelled = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: []});
  assert.equal(cancelled.unusedReturnBoxes, 200);
  const differentWinner = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200,
    placements: [{...placements[0], uid: "replacement"}]});
  assert.equal(differentWinner.payouts[0].payoutId, full.payouts[0].payoutId, "slot ID cannot mint a new award on recalculation");
  assert.throws(() => planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200,
    placements: [{uid: "a", rank: 4, slot: "fourth", technicalForfeit: false}]}), /third/);
});

test("outputs are JSON-serializable; helpers preserve inputs and reject unsafe arithmetic", () => {
  const e = event(); const before = JSON.stringify(e); const b = buildBracket({event: e, ranking: ranking(4), serverSeed: "s"});
  const output = {event: e, bracket: b, forms: qualificationForms({event: e, serverSeed: "s"}),
    progress: progressBracket({bracket: b}), prizes: planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: []})};
  assert.deepEqual(JSON.parse(JSON.stringify(output)), output); assert.equal(JSON.stringify(e), before);
  assert.throws(() => createEventPlan({eventId: "e", source, questions: pool(), startsAtMs: Number.MAX_SAFE_INTEGER,
    prizes, fundedBoxes: 200}), /safe integer/);
  assert.throws(() => validatePrizeSchedule({first: Number.MAX_SAFE_INTEGER, second: 1, third: 0}, Number.MAX_SAFE_INTEGER),
    /safe integer/);
  const long = pool(); long.filter((q) => q.difficulty === "HARD").forEach((q) => { q.maxDurationMs = STAGE_DURATION_MS; });
  assert.throws(() => buildBracket({event: event(long), ranking: ranking(2), serverSeed: "s"}), /does not fit/);
  assert.throws(() => evaluateMeeting({match: {...match(), formMaxDurationMs: STAGE_DURATION_MS - READY_WINDOW_MS}, nowMs: 0}),
    /scheduling time/);
});
