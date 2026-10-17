"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  READY_WINDOW_MS, QUALIFICATION_DURATION_MS, STAGE_DURATION_MS,
  preflightPool, validatePrizeSchedule, validateStoredPrizeSchedule, prizeListProjection, createEventPlan,
  qualificationForms, planQualificationAttempt, planAdmission,
  selectQualifiers, buildBracket, planMeetingProposal, evaluateMeeting, resolvePlayedMatch,
  progressBracket, planPrizePayouts,
} = require("./tournament-policy");

const source = {kind: "COURSE", id: "course", snapshotId: "snapshot-1", reviewed: true, published: true};
// Story 5.4: the adopted v1 prize list replaces the fixed DTO. {first:100, second:50, third:25}
// maps mechanically to two place-3 rows; the liability stays 200. Test fixture, not a default.
const prizes = {listVersion: 1, rows: [{place: 1, amountBoxes: 100}, {place: 2, amountBoxes: 50},
  {place: 3, amountBoxes: 25}, {place: 3, amountBoxes: 25}]};
const legacyPrizes = {first: 100, second: 50, third: 25}; // Stored-event form (L1), never a CREATE input.
function pool(easy = 60, hard = 20, sourceId = "course") {
  return [
    ...Array.from({length: easy}, (_, i) => ({questionId: `e${i}`, difficulty: "EASY"})),
    ...Array.from({length: hard}, (_, i) => ({questionId: `h${i}`, difficulty: "HARD"})),
  ].map((q) => ({...q, type: "SingleChoice", sourceId, reviewed: true, published: true,
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

const arenaEvent = () => createEventPlan({eventId: "arena-event", questions: pool(60, 20, "lesson"),
  source: {...source, kind: "ARENA_LESSON", id: "lesson", admitted: true}, startsAtMs: 1000, prizes, fundedBoxes: 200});
function download(uid, courseId) {
  return {schemaVersion: 1, uid, courseId, downloadedAtMs: 500};
}
const DIGEST = "a".repeat(64);

test("admission confirms exactly one server-owned basis inside the qualification window", () => {
  const e = event();
  assert.deepEqual(planAdmission({event: e, uid: "u", nowMs: 1000, downloadRecord: download("u", "course")}),
    {allowed: true, basis: "COURSE_DOWNLOAD", reason: "ADMITTED"});
  assert.deepEqual(planAdmission({event: e, uid: "u", nowMs: e.qualification.endsAtMs - 1, linkTokenDigest: DIGEST}),
    {allowed: true, basis: "LINK", reason: "ADMITTED"});
  // With both bases present the attested download takes precedence; either alone admits.
  const both = planAdmission({event: e, uid: "u", nowMs: 1000, downloadRecord: download("u", "course"), linkTokenDigest: DIGEST});
  assert.deepEqual(both, {allowed: true, basis: "COURSE_DOWNLOAD", reason: "ADMITTED"});
});

test("admission refuses without a basis and never trusts foreign records or bare claims", () => {
  const e = event();
  assert.deepEqual(planAdmission({event: e, uid: "u", nowMs: 1000}),
    {allowed: false, basis: null, reason: "NO_ADMISSION_BASIS"});
  // Records for another account or another course fail closed as "no record".
  for (const record of [download("other", "course"), download("u", "other-course")]) {
    assert.equal(planAdmission({event: e, uid: "u", nowMs: 1000, downloadRecord: record}).reason, "NO_ADMISSION_BASIS");
  }
  // A client-claimed record shape (not a server attestation) cannot widen the gate either:
  // the pure policy judges only the attested (uid, courseId, schemaVersion) tuple.
  assert.throws(() => planAdmission({event: e, uid: "u", nowMs: 1000, downloadRecord: {schemaVersion: 2, uid: "u", courseId: "course"}}), /schemaVersion/);
  assert.throws(() => planAdmission({event: e, uid: "u", nowMs: 1000, linkTokenDigest: "deadbeef"}), /sha-256/);
  assert.throws(() => planAdmission({event: e, uid: "", nowMs: 1000, downloadRecord: download("u", "course")}), /nonblank/);
  assert.throws(() => planAdmission({event: {...e, source: {kind: "MYSTERY", id: "course"}}, uid: "u", nowMs: 1000, linkTokenDigest: DIGEST}), /admission basis/);
});

test("arena-lesson events are link-only: download records do not apply (owner-visible reading)", () => {
  const a = arenaEvent();
  assert.deepEqual(planAdmission({event: a, uid: "u", nowMs: 1000, linkTokenDigest: DIGEST}),
    {allowed: true, basis: "LINK", reason: "ADMITTED"});
  // No course corresponds to an Arena lesson, so even the account's own attested download
  // for the source id is not a basis; without the link the gate stays shut.
  assert.equal(planAdmission({event: a, uid: "u", nowMs: 1000, downloadRecord: download("u", "lesson")}).reason, "NO_ADMISSION_BASIS");
  assert.equal(planAdmission({event: a, uid: "u", nowMs: 1000}).reason, "NO_ADMISSION_BASIS");
});

test("the qualification window stays the sole admission time gate, half-open on endsAtMs", () => {
  const e = event();
  for (const nowMs of [999, e.qualification.endsAtMs]) {
    for (const basis of [{downloadRecord: download("u", "course")}, {linkTokenDigest: DIGEST},
      {downloadRecord: download("u", "course"), linkTokenDigest: DIGEST}]) {
      assert.deepEqual(planAdmission({event: e, uid: "u", nowMs, ...basis}),
        {allowed: false, basis: null, reason: "OUTSIDE_QUALIFICATION_WINDOW"});
    }
  }
  assert.equal(planAdmission({event: e, uid: "u", nowMs: e.qualification.startsAtMs, downloadRecord: download("u", "course")}).allowed, true);
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

// Story 5.7 (UX-O3/D7): the boundaries a client is shown refuse exactly per AD-17 —
// confirmation ≤ C and ≤ slot with a timely offer, READY discarded before the slot and from
// the window end on, accepted inside [slot, slot+1h).
test("confirmation and readiness boundaries are exact: ≤ C, ≤ slot, half-open [slot, slot+1h)", () => {
  const m = match(); const cutoff = m.stageEndsAtMs - READY_WINDOW_MS - m.formMaxDurationMs;
  const lastOffer = planMeetingProposal({match: m, uid: "a", slotStartsAtMs: cutoff, nowMs: 0});
  // Confirmation at C exactly and after C is refused; one tick earlier is the last legal instant.
  assert.throws(() => evaluateMeeting({match: m, proposals: [lastOffer],
    confirmations: [{uid: "a", slotStartsAtMs: cutoff, acceptedAtMs: cutoff}], nowMs: cutoff}), /timely proposal/);
  assert.throws(() => evaluateMeeting({match: m, proposals: [lastOffer],
    confirmations: [{uid: "a", slotStartsAtMs: cutoff, acceptedAtMs: cutoff + 1}], nowMs: cutoff + 1}), /timely proposal/);
  assert.equal(evaluateMeeting({match: m, proposals: [lastOffer],
    confirmations: [{uid: "a", slotStartsAtMs: cutoff, acceptedAtMs: cutoff - 1}], nowMs: cutoff - 1}).status, "WAITING_FOR_AGREEMENT");
  // Confirmation later than the slot is refused even well inside the coordination window.
  const earlyOffer = planMeetingProposal({match: m, uid: "a", slotStartsAtMs: cutoff - 1000, nowMs: 0});
  assert.throws(() => evaluateMeeting({match: m, proposals: [earlyOffer],
    confirmations: [{uid: "a", slotStartsAtMs: cutoff - 1000, acceptedAtMs: cutoff - 999}], nowMs: cutoff - 999}), /timely proposal/);
  assert.equal(evaluateMeeting({match: m, proposals: [earlyOffer],
    confirmations: [{uid: "a", slotStartsAtMs: cutoff - 1000, acceptedAtMs: cutoff - 1000}], nowMs: cutoff - 1000}).status, "WAITING_FOR_AGREEMENT");
  // READY before the slot is discarded (not an error), at the slot it counts, at the last
  // legal second it counts; the half-open end is pinned by the one-hour window test above.
  const agreed = agreement(m, 1000);
  assert.equal(evaluateMeeting({match: m, ...agreed, readySignals: [{uid: "a", acceptedAtMs: 999}], nowMs: 999}).status, "WAITING_FOR_WINDOW");
  assert.equal(evaluateMeeting({match: m, ...agreed, readySignals: [{uid: "a", acceptedAtMs: 1000}], nowMs: 1000}).status, "WAITING_FOR_READY");
  assert.equal(evaluateMeeting({match: m, ...agreed,
    readySignals: [{uid: "a", acceptedAtMs: 1000 + READY_WINDOW_MS - 1}], nowMs: 1000 + READY_WINDOW_MS - 1}).status, "WAITING_FOR_READY");
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

test("v1 CREATE validates rows, canonicalizes by (place, submission order) and sums liability once", () => {
  const unsorted = {listVersion: 1, rows: [{place: 3, amountBoxes: 25}, {place: 1, amountBoxes: 100},
    {place: 3, amountBoxes: 25}, {place: 2, amountBoxes: 50}]};
  assert.deepEqual(validatePrizeSchedule(unsorted, 200).prizes,
    {listVersion: 1, rows: [{place: 1, amountBoxes: 100}, {place: 2, amountBoxes: 50},
      {place: 3, amountBoxes: 25}, {place: 3, amountBoxes: 25}]});
  assert.equal(validatePrizeSchedule(prizes, 200).maximumLiabilityBoxes, 200);
  // A replay that merely reorders rows digests identically through the canonical form.
  assert.deepEqual(validatePrizeSchedule(unsorted, 200), validatePrizeSchedule(prizes, 200));
  // Empty list is a legal prizeless event: liability 0 with any deposit.
  assert.equal(validatePrizeSchedule({listVersion: 1, rows: []}, 7).maximumLiabilityBoxes, 0);
  assert.equal(validatePrizeSchedule({listVersion: 1, rows: []}, 0).fundedBoxes, 0);
});

test("v1 CREATE rejects the legacy DTO and out-of-envelope rows with typed errors", () => {
  // L4: the fixed {first, second, third} DTO is never accepted for new events.
  assert.throws(() => validatePrizeSchedule(legacyPrizes, 200), /listVersion: 1, rows/);
  assert.throws(() => validatePrizeSchedule({...legacyPrizes, fourth: 1}, 201), /listVersion: 1, rows/);
  assert.throws(() => validatePrizeSchedule({listVersion: 0, rows: []}, 0), /listVersion must be 1/);
  assert.throws(() => validatePrizeSchedule({listVersion: 2, rows: []}, 0), /listVersion must be 1/);
  assert.throws(() => validatePrizeSchedule({listVersion: 1}, 0), /rows must be an array/);
  assert.throws(() => validatePrizeSchedule({listVersion: 1, rows: [], extra: 1}, 0), /exactly listVersion and rows/);
  const row = (overrides) => ({listVersion: 1, rows: [{place: 1, amountBoxes: 1, ...overrides}]});
  assert.throws(() => validatePrizeSchedule({listVersion: 1, rows: Array.from({length: 17}, (_, i) => ({place: 1, amountBoxes: 0}))}, 0), /at most 16 rows/);
  assert.throws(() => validatePrizeSchedule(row({place: 0}), 0), />= 1/);
  assert.throws(() => validatePrizeSchedule(row({place: 17}), 0), /<= 16/);
  assert.throws(() => validatePrizeSchedule(row({place: 1.5}), 0), /safe integer/);
  assert.throws(() => validatePrizeSchedule(row({amountBoxes: -1}), 0), />= 0/);
  assert.throws(() => validatePrizeSchedule({listVersion: 1, rows: ["prize"]}, 0), /must be an object/);
  assert.throws(() => validatePrizeSchedule({listVersion: 1, rows: [{place: 1, amountBoxes: 1, label: "Gold"}]}, 0), /exactly place and amountBoxes/);
  assert.throws(() => validatePrizeSchedule(prizes, 199), /insufficient/);
  // BigInt-guarded liability: the sum of sixteen rows cannot overflow into the escrow.
  assert.throws(() => validatePrizeSchedule({listVersion: 1,
    rows: [{place: 1, amountBoxes: Number.MAX_SAFE_INTEGER}, {place: 2, amountBoxes: 1}]}, Number.MAX_SAFE_INTEGER),
    /safe integer/);
});

test("stored-event revalidation is dual-form and keeps the legacy liability formula", () => {
  assert.deepEqual(validateStoredPrizeSchedule(prizes, 200),
    {prizes, fundedBoxes: 200, maximumLiabilityBoxes: 200});
  const legacy = validateStoredPrizeSchedule(legacyPrizes, 200);
  assert.equal(legacy.maximumLiabilityBoxes, 200, "first+second+2*third via the projection sum");
  assert.deepEqual(legacy.prizes, {first: 100, second: 50, third: 25});
  assert.throws(() => validateStoredPrizeSchedule({...legacyPrizes, fourth: 1}, 201), /fourth/);
  assert.throws(() => validateStoredPrizeSchedule({first: 1, second: 1}, 0), /third/);
  assert.throws(() => validateStoredPrizeSchedule({listVersion: 2, rows: []}, 0), /listVersion must be 1/);
});

test("the read projection is one shape for both stored forms and never leaks the legacy DTO", () => {
  assert.deepEqual(prizeListProjection(prizes),
    {listVersion: 1, rows: [{place: 1, amountBoxes: 100}, {place: 2, amountBoxes: 50},
      {place: 3, amountBoxes: 25}, {place: 3, amountBoxes: 25}]});
  const projected = prizeListProjection(legacyPrizes);
  assert.deepEqual(projected, {listVersion: 0, rows: [{place: 1, amountBoxes: 100}, {place: 2, amountBoxes: 50},
    {place: 3, amountBoxes: 25}, {place: 3, amountBoxes: 25}]});
  assert.ok(!Object.keys(projected).some((name) => ["first", "second", "third"].includes(name)));
  assert.ok(!JSON.stringify(projected).includes('"first"'));
});

test("zero-amount rows consume their finisher position; mixed rows stay positional", () => {
  const rows = [{place: 1, amountBoxes: 50}, {place: 2, amountBoxes: 20}, {place: 3, amountBoxes: 0}, {place: 3, amountBoxes: 12}];
  const plan = planPrizePayouts({eventId: "e_zero", prizes: {listVersion: 1, rows}, fundedBoxes: 82,
    placements: [{uid: "champ", rank: 1, slot: "champion", technicalForfeit: false},
      {uid: "up", rank: 3, slot: "third-upper", technicalForfeit: false},
      {uid: "down", rank: 3, slot: "third-lower", technicalForfeit: false}]});
  const paid = Object.fromEntries(plan.payouts.map((row) => [row.uid, row.amount]));
  // A zero row consumes its position and pays nothing: no payout record for that finisher at all.
  assert.deepEqual(paid, {champ: 50, down: 12});
  assert.equal(plan.payouts.some((row) => row.uid === "up"), false);
});
test("fund validates the v1 list, conserves paid plus unused and keeps canonical row payout IDs stable", () => {
  assert.throws(() => validatePrizeSchedule(prizes, 199), /insufficient/);
  const placements = [
    {uid: "a", rank: 1, slot: "champion", technicalForfeit: false},
    {uid: "b", rank: 2, slot: "runner-up", technicalForfeit: false},
    {uid: "c", rank: 3, slot: "third-upper", technicalForfeit: false},
    {uid: "d", rank: 3, slot: "third-lower", technicalForfeit: false},
  ];
  const full = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements});
  assert.equal(full.paidBoxes, 200); assert.equal(full.unusedReturnBoxes, 0);
  assert.deepEqual(full.payouts.map((row) => [row.slot, row.amount]), [["champion", 100], ["runner-up", 50],
    ["third-upper", 25], ["third-lower", 25]]);
  assert.deepEqual(full.payouts.map((row) => row.payoutId), [
    JSON.stringify(["tournament", "e", "prize", 1, 1, 0]), JSON.stringify(["tournament", "e", "prize", 1, 2, 0]),
    JSON.stringify(["tournament", "e", "prize", 1, 3, 0]), JSON.stringify(["tournament", "e", "prize", 1, 3, 1])]);
  assert.deepEqual(full, planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: [...placements].reverse()}));
  const partial = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 230,
    placements: placements.map((p) => ({...p, technicalForfeit: p.rank === 2 || p.slot === "third-lower"}))});
  assert.equal(partial.paidBoxes, 125); assert.equal(partial.unusedReturnBoxes, 105);
  assert.equal(partial.paidBoxes + partial.unusedReturnBoxes, partial.fundedBoxes);
  const cancelled = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: []});
  assert.equal(cancelled.unusedReturnBoxes, 200);
  const differentWinner = planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200,
    placements: [{...placements[0], uid: "replacement"}]});
  assert.equal(differentWinner.payouts[0].payoutId, full.payouts[0].payoutId, "row ID cannot mint a new award on recalculation");
  assert.throws(() => planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200,
    placements: [{uid: "a", rank: 4, slot: "fourth", technicalForfeit: false}]}), /third/);
});

test("legacy stored events settle through the projection with yesterday's slot IDs and amounts", () => {
  // Executed-equivalence case from the contract §4/L3: 100/50/30 → paid 210, both thirds paid.
  const stored = {first: 100, second: 50, third: 30};
  const placements = [
    {uid: "a", rank: 1, slot: "champion", technicalForfeit: false},
    {uid: "b", rank: 2, slot: "runner-up", technicalForfeit: false},
    {uid: "c", rank: 3, slot: "third-upper", technicalForfeit: false},
    {uid: "d", rank: 3, slot: "third-lower", technicalForfeit: false},
  ];
  const settled = planPrizePayouts({eventId: "e", prizes: stored, fundedBoxes: 210, placements});
  assert.equal(settled.maximumLiabilityBoxes, 210);
  assert.equal(settled.paidBoxes, 210); assert.equal(settled.unusedReturnBoxes, 0);
  assert.deepEqual(settled.payouts.map((row) => [row.payoutId, row.uid, row.amount]), [
    [JSON.stringify(["tournament", "e", "prize", "champion"]), "a", 100],
    [JSON.stringify(["tournament", "e", "prize", "runner-up"]), "b", 50],
    [JSON.stringify(["tournament", "e", "prize", "third-upper"]), "c", 30],
    [JSON.stringify(["tournament", "e", "prize", "third-lower"]), "d", 30]]);
  // A technical third placement still pays the remaining one exactly prizes.third.
  const technical = planPrizePayouts({eventId: "e", prizes: stored, fundedBoxes: 210,
    placements: placements.map((p) => ({...p, technicalForfeit: p.slot === "third-upper"}))});
  assert.deepEqual(technical.payouts.map((row) => [row.slot, row.amount]),
    [["champion", 100], ["runner-up", 50], ["third-lower", 30]]);
  assert.equal(technical.paidBoxes + technical.unusedReturnBoxes, 210);
  const cancelled = planPrizePayouts({eventId: "e", prizes: stored, fundedBoxes: 210, placements: []});
  assert.equal(cancelled.unusedReturnBoxes, 210);
});

test("place-3 rows pay fixed finisher order; lone rows and unreproducible places return", () => {
  const finishers = (uids) => uids.map((uid, index) => ({uid, rank: 3,
    slot: index === 0 ? "third-upper" : "third-lower", technicalForfeit: false}));
  // Matrix: two third rows, both losers finish → third-upper row 0, third-lower row 1.
  const both = planPrizePayouts({eventId: "e", fundedBoxes: 30,
    prizes: {listVersion: 1, rows: [{place: 3, amountBoxes: 12}, {place: 3, amountBoxes: 18}]},
    placements: finishers(["c", "d"])});
  assert.deepEqual(both.payouts.map((row) => [row.slot, row.amount, row.payoutId]),
    [["third-upper", 12, JSON.stringify(["tournament", "e", "prize", 1, 3, 0])],
      ["third-lower", 18, JSON.stringify(["tournament", "e", "prize", 1, 3, 1])]]);
  assert.equal(both.unusedReturnBoxes, 0);
  // One third row, both finish → third-upper paid, second third gets nothing, nothing unreturned.
  const single = planPrizePayouts({eventId: "e", fundedBoxes: 12,
    prizes: {listVersion: 1, rows: [{place: 3, amountBoxes: 12}]}, placements: finishers(["c", "d"])});
  assert.deepEqual(single.payouts.map((row) => row.slot), ["third-upper"]);
  assert.equal(single.paidBoxes, 12); assert.equal(single.unusedReturnBoxes, 0);
  // A row without a finisher (absent/technical slot) returns; the survivor takes row 0 (§4).
  const absent = planPrizePayouts({eventId: "e", fundedBoxes: 30,
    prizes: {listVersion: 1, rows: [{place: 3, amountBoxes: 12}, {place: 3, amountBoxes: 18}]},
    placements: [{uid: "d", rank: 3, slot: "third-lower", technicalForfeit: false}]});
  assert.deepEqual(absent.payouts.map((row) => [row.slot, row.amount]), [["third-lower", 12]]);
  assert.equal(absent.unusedReturnBoxes, 18);
  // Places outside the reproducible {1,2,3} set are legal at CREATE and always return (D7).
  const exotic = planPrizePayouts({eventId: "e", fundedBoxes: 9,
    prizes: {listVersion: 1, rows: [{place: 5, amountBoxes: 9}, {place: 16, amountBoxes: 0}]}, placements: []});
  assert.deepEqual(exotic.payouts, []); assert.equal(exotic.unusedReturnBoxes, 9);
  // Zero-amount rows pay nothing and never write effects.
  const zero = planPrizePayouts({eventId: "e", fundedBoxes: 5,
    prizes: {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 0}]},
    placements: [{uid: "a", rank: 1, slot: "champion", technicalForfeit: false},
      {uid: "b", rank: 2, slot: "runner-up", technicalForfeit: false}]});
  assert.deepEqual(zero.payouts.map((row) => row.slot), ["champion"]);
  assert.equal(zero.paidBoxes, 5); assert.equal(zero.unusedReturnBoxes, 0);
});

test("outputs are JSON-serializable; helpers preserve inputs and reject unsafe arithmetic", () => {
  const e = event(); const before = JSON.stringify(e); const b = buildBracket({event: e, ranking: ranking(4), serverSeed: "s"});
  const output = {event: e, bracket: b, forms: qualificationForms({event: e, serverSeed: "s"}),
    progress: progressBracket({bracket: b}), prizes: planPrizePayouts({eventId: "e", prizes, fundedBoxes: 200, placements: []})};
  assert.deepEqual(JSON.parse(JSON.stringify(output)), output); assert.equal(JSON.stringify(e), before);
  assert.throws(() => createEventPlan({eventId: "e", source, questions: pool(), startsAtMs: Number.MAX_SAFE_INTEGER,
    prizes, fundedBoxes: 200}), /safe integer/);
  assert.throws(() => validatePrizeSchedule({listVersion: 1,
    rows: [{place: 1, amountBoxes: Number.MAX_SAFE_INTEGER}, {place: 2, amountBoxes: 1}]}, Number.MAX_SAFE_INTEGER),
    /safe integer/);
  const long = pool(); long.filter((q) => q.difficulty === "HARD").forEach((q) => { q.maxDurationMs = STAGE_DURATION_MS; });
  assert.throws(() => buildBracket({event: event(long), ranking: ranking(2), serverSeed: "s"}), /does not fit/);
  assert.throws(() => evaluateMeeting({match: {...match(), formMaxDurationMs: STAGE_DURATION_MS - READY_WINDOW_MS}, nowMs: 0}),
    /scheduling time/);
});
