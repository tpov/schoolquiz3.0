"use strict";

const {createHash} = require("node:crypto");

/** Pure, deterministic SERVER policy. Identity, review, clock, timing and LS rank are trusted
 * adapter inputs, not client claims. Forms contain existing question references, never new text.
 * Persist event/source snapshots and all decisions atomically/idempotently in the adapter.
 */
const DAY_MS = 86400000;
const READY_WINDOW_MS = 3600000;
const QUALIFICATION_DURATION_MS = 7 * DAY_MS;
const STAGE_DURATION_MS = 3 * DAY_MS;
const QUALIFICATION_ROUNDS = 3;
const QUESTIONS_PER_FORM = 20;
const MAX_QUALIFIERS = 16;
const MAX_SAFE = BigInt(Number.MAX_SAFE_INTEGER);
const QUESTION_TYPES = new Set(["SingleChoice", "MultipleChoice", "Ordering", "FillBlank"]);

function obj(value, name) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${name} must be an object`);
  return value;
}
function arr(value, name) {
  if (!Array.isArray(value)) throw new TypeError(`${name} must be an array`);
  return value;
}
function str(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new TypeError(`${name} must be nonblank`);
  return value;
}
function int(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) throw new RangeError(`${name} must be a safe integer >= ${min}`);
  return value;
}
function safe(value, name) {
  if (value < 0n || value > MAX_SAFE) throw new RangeError(`${name} is outside safe integer range`);
  return Number(value);
}
function add(a, b, name = "sum") {
  return safe(BigInt(int(a, name)) + BigInt(int(b, name)), name);
}
function cmp(a, b) { return a < b ? -1 : a > b ? 1 : 0; }
function key(...parts) { return JSON.stringify(["tournament", ...parts]); }
function hash(value) { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }

/** The adapter attests one reviewed, published immutable source snapshot. */
function preflightPool({source, questions}) {
  obj(source, "source");
  if (!["COURSE", "ARENA_LESSON"].includes(source.kind) || source.reviewed !== true || source.published !== true ||
      (source.kind === "ARENA_LESSON" && source.admitted !== true)) {
    throw new RangeError("source must be a reviewed published course or admitted Arena lesson");
  }
  const normalizedSource = {
    kind: source.kind, id: str(source.id, "source.id"), snapshotId: str(source.snapshotId, "source.snapshotId"),
    reviewed: true, published: true, admitted: source.kind === "ARENA_LESSON",
  };
  const seen = new Set();
  const eligible = [];
  for (const raw of arr(questions, "questions")) {
    const q = obj(raw, "question");
    const questionId = str(q.questionId, "question.questionId");
    if (seen.has(questionId)) throw new RangeError(`duplicate questionId: ${questionId}`);
    seen.add(questionId);
    if (q.type === "Survey" || q.type === "SURVEY" || q.reviewed !== true || q.published !== true) continue;
    if (!QUESTION_TYPES.has(q.type)) throw new RangeError("unsupported question type");
    if (!["EASY", "HARD"].includes(q.difficulty)) throw new RangeError("unsupported difficulty");
    if (q.sourceId !== normalizedSource.id) throw new RangeError("question belongs to another source");
    eligible.push({
      questionId, type: q.type, difficulty: q.difficulty,
      contentVersion: int(q.contentVersion, "question.contentVersion", 1),
      contentHash: str(q.contentHash, "question.contentHash"),
      // Must be computed by the server's shared timer formula, not supplied by a player.
      maxDurationMs: int(q.maxDurationMs, "question.maxDurationMs", 1),
    });
  }
  eligible.sort((a, b) => cmp(a.questionId, b.questionId));
  const easyQuestions = eligible.filter((q) => q.difficulty === "EASY");
  const hardQuestions = eligible.filter((q) => q.difficulty === "HARD");
  if (easyQuestions.length < 60) throw new RangeError("at least 60 distinct eligible EASY questions required");
  if (hardQuestions.length < 20) throw new RangeError("at least 20 distinct eligible HARD questions required");
  return {source: normalizedSource, easyQuestions, hardQuestions, poolFingerprint: hash(eligible)};
}

function validatePrizeSchedule(prizes, fundedBoxes) {
  obj(prizes, "prizes");
  if (Object.keys(prizes).some((name) => !["first", "second", "third"].includes(name))) {
    throw new RangeError("prizes support first, second and two thirds; no fourth place");
  }
  const normalized = {
    first: int(prizes.first, "prizes.first"), second: int(prizes.second, "prizes.second"),
    third: int(prizes.third, "prizes.third"),
  };
  const maximumLiabilityBoxes = safe(BigInt(normalized.first) + BigInt(normalized.second) +
    2n * BigInt(normalized.third), "prize liability");
  int(fundedBoxes, "fundedBoxes");
  if (fundedBoxes < maximumLiabilityBoxes) throw new RangeError("insufficient prize fund");
  return {prizes: normalized, fundedBoxes, maximumLiabilityBoxes};
}

function createEventPlan({eventId, source, questions, startsAtMs, prizes, fundedBoxes}) {
  const pool = preflightPool({source, questions});
  const funding = validatePrizeSchedule(prizes, fundedBoxes);
  int(startsAtMs, "startsAtMs");
  return {
    eventId: str(eventId, "eventId"), policyVersion: "course-tournament-v1", ...pool, ...funding,
    qualification: {startsAtMs, endsAtMs: add(startsAtMs, QUALIFICATION_DURATION_MS),
      roundCount: QUALIFICATION_ROUNDS, questionsPerRound: QUESTIONS_PER_FORM, attemptsPerRound: 1},
  };
}

function draw(pool, count, seed, context) {
  str(seed, "serverSeed");
  if (pool.length < count) throw new RangeError("not enough questions for form");
  return pool.map((question) => ({question, order: hash([seed, context, question.questionId])}))
    .sort((a, b) => cmp(a.order, b.order) || cmp(a.question.questionId, b.question.questionId))
    .slice(0, count).map(({question}) => ({...question}));
}

function form(formId, questions) {
  if (questions.length !== QUESTIONS_PER_FORM || new Set(questions.map((q) => q.questionId)).size !== QUESTIONS_PER_FORM) {
    throw new RangeError("form must have exactly 20 distinct questions");
  }
  const maxDurationMs = safe(questions.reduce((sum, q) => sum + BigInt(int(q.maxDurationMs, "maxDurationMs", 1)), 0n),
    "form duration");
  return {formId, difficulty: questions[0].difficulty, questions, maxDurationMs, fingerprint: hash(questions)};
}

/** Initial approved configuration: three COMMON forms per event, 60 distinct EASY questions.
 * All participants receive the same round form. Individualized qualification is not implemented.
 */
function qualificationForms({event, serverSeed}) {
  obj(event, "event");
  const selected = draw(event.easyQuestions, 60, serverSeed, key(event.eventId, "qualification", event.poolFingerprint));
  return Array.from({length: QUALIFICATION_ROUNDS}, (_, i) =>
    form(key(event.eventId, "qualification", i + 1), selected.slice(i * 20, (i + 1) * 20)));
}

/** Starting consumes this round's single entry, including a later abort. Same attempt is a replay. */
function planQualificationAttempt({event, uid, roundNumber, attemptId, nowMs, existingAttempts = []}) {
  obj(event, "event");
  str(uid, "uid");
  str(attemptId, "attemptId");
  int(roundNumber, "roundNumber", 1);
  if (roundNumber > QUALIFICATION_ROUNDS) throw new RangeError("qualification has three rounds");
  int(nowMs, "nowMs");
  const existing = arr(existingAttempts, "existingAttempts");
  for (const row of existing) {
    obj(row, "attempt");
    if (row.attemptId === attemptId &&
        (row.eventId !== event.eventId || row.uid !== uid || row.roundNumber !== roundNumber)) {
      throw new RangeError("attemptId is already bound to another entry");
    }
  }
  const used = existing.filter((row) => row.eventId === event.eventId && row.uid === uid && row.roundNumber === roundNumber);
  if (used.length > 1) throw new RangeError("multiple attempts already occupy this round");
  if (used.length) return {allowed: used[0].attemptId === attemptId, replay: used[0].attemptId === attemptId,
    reason: used[0].attemptId === attemptId ? "REPLAY" : "ROUND_ALREADY_USED", attempt: {...used[0]}};
  if (nowMs < event.qualification.startsAtMs || nowMs >= event.qualification.endsAtMs) {
    return {allowed: false, replay: false, reason: "OUTSIDE_QUALIFICATION_WINDOW", attempt: null};
  }
  return {allowed: true, replay: false, reason: "START", attempt: {
    eventId: event.eventId, uid, roundNumber, attemptId, startedAtMs: nowMs, status: "STARTED",
  }};
}

/** UX-O4/access gate. Exactly one admitted basis per (event, account), confirmed from
 * server-owned records only — a client-claimed download or a bare link string is never
 * permission. COURSE events admit via the attested download record for event.source.id OR a
 * verified competition-link digest; ARENA_LESSON events have no corresponding course to
 * download (owner-visible reading of story 5.3), so the organizer-distributed link is their
 * only basis and download records do not apply. linkTokenDigest must already have been matched
 * by the adapter against the event's stored link digest — this pure policy never sees the
 * token itself. The qualification window stays the sole time gate: outside it nothing is
 * admitted, and the adapter must not write any admission document. Precedence when both bases
 * exist is the attested download (COURSE_DOWNLOAD), then the link (LINK).
 */
function planAdmission({event, uid, nowMs, downloadRecord = null, linkTokenDigest = null}) {
  obj(event, "event");
  if (!event.source || typeof event.source !== "object") throw new TypeError("event.source must be an object");
  if (!event.qualification || typeof event.qualification !== "object") throw new TypeError("event.qualification must be an object");
  str(uid, "uid");
  int(nowMs, "nowMs");
  int(event.qualification.startsAtMs, "event.qualification.startsAtMs");
  int(event.qualification.endsAtMs, "event.qualification.endsAtMs");
  if (nowMs < event.qualification.startsAtMs || nowMs >= event.qualification.endsAtMs) {
    return {allowed: false, basis: null, reason: "OUTSIDE_QUALIFICATION_WINDOW"};
  }
  let digest = null;
  if (linkTokenDigest !== null && linkTokenDigest !== undefined) {
    if (typeof linkTokenDigest !== "string" || !/^[a-f0-9]{64}$/.test(linkTokenDigest)) {
      throw new RangeError("linkTokenDigest must be a sha-256 hex digest");
    }
    digest = linkTokenDigest;
  }
  if (event.source.kind === "COURSE") {
    if (attestedDownload(downloadRecord, uid, str(event.source.id, "event.source.id"))) {
      return {allowed: true, basis: "COURSE_DOWNLOAD", reason: "ADMITTED"};
    }
  } else if (event.source.kind !== "ARENA_LESSON") {
    throw new RangeError("event source kind has no admission basis");
  }
  if (digest) return {allowed: true, basis: "LINK", reason: "ADMITTED"};
  return {allowed: false, basis: null, reason: "NO_ADMISSION_BASIS"};
}

/** A download record admits only its own account for its own course; anything else in the
 * shared collection fails closed as "no record" rather than widening the gate.
 */
function attestedDownload(record, uid, courseId) {
  if (record === null || record === undefined) return false;
  obj(record, "downloadRecord");
  if (record.schemaVersion !== 1) throw new RangeError("downloadRecord schemaVersion must be 1");
  return str(record.uid, "downloadRecord.uid") === uid && str(record.courseId, "downloadRecord.courseId") === courseId;
}

/** Uses the supplied authoritative LS places. It never recalculates or replaces LS. */
function selectQualifiers(ranking) {
  const seenUid = new Set();
  const seenPlace = new Set();
  return arr(ranking, "ranking").map((raw) => {
    const row = obj(raw, "ranking entry");
    const uid = str(row.uid, "ranking.uid");
    const place = int(row.place, "ranking.place", 1);
    if (seenUid.has(uid) || seenPlace.has(place)) throw new RangeError("ranking has duplicate uid/place");
    seenUid.add(uid); seenPlace.add(place);
    return {uid, authoritativePlace: place};
  }).sort((a, b) => cmp(a.authoritativePlace, b.authoritativePlace)).slice(0, MAX_QUALIFIERS)
    .map((row, index) => ({...row, seed: index + 1}));
}

function seedOrder(size) {
  let order = [1, 2];
  for (let current = 4; current <= size; current *= 2) order = order.flatMap((seed) => [seed, current + 1 - seed]);
  return order;
}

function buildBracket({event, ranking, startsAtMs = event.qualification.endsAtMs, serverSeed}) {
  obj(event, "event");
  int(startsAtMs, "startsAtMs");
  if (startsAtMs < event.qualification.endsAtMs) throw new RangeError("bracket starts after qualification closes");
  const entrants = selectQualifiers(ranking);
  if (entrants.length < 2) return {eventId: event.eventId, status: "CANCELLED", reason: "FEWER_THAN_TWO_QUALIFIERS",
    entrants, rounds: []};
  let size = 2;
  while (size < entrants.length) size *= 2;
  const order = seedOrder(size);
  const rounds = [];
  for (let count = size / 2, stage = 1; count >= 1; count /= 2, stage++) {
    const stageStartsAtMs = add(startsAtMs, (stage - 1) * STAGE_DURATION_MS);
    const stageEndsAtMs = add(stageStartsAtMs, STAGE_DURATION_MS);
    const matches = Array.from({length: count}, (_, index) => {
      const matchId = key(event.eventId, "match", stage, index);
      const sharedForm = form(key(matchId, "hard-form"), draw(event.hardQuestions, 20, serverSeed,
        key(matchId, event.poolFingerprint)));
      if (sharedForm.maxDurationMs >= STAGE_DURATION_MS - READY_WINDOW_MS) {
        throw new RangeError("form does not fit inside stage after the ready window");
      }
      return {matchId, stage, index, stageStartsAtMs, stageEndsAtMs,
        coordinationDeadlineMs: stageEndsAtMs - READY_WINDOW_MS - sharedForm.maxDurationMs,
        leftSeed: stage === 1 ? order[index * 2] : null,
        rightSeed: stage === 1 ? order[index * 2 + 1] : null,
        leftMatchId: stage === 1 ? null : rounds[stage - 2].matches[index * 2].matchId,
        rightMatchId: stage === 1 ? null : rounds[stage - 2].matches[index * 2 + 1].matchId,
        sharedForm};
    });
    rounds.push({stage, startsAtMs: stageStartsAtMs, endsAtMs: stageEndsAtMs, matches});
  }
  return {eventId: event.eventId, status: "ACTIVE", entrants, size, rounds};
}

function meetingContext(match) {
  obj(match, "match");
  const participantUids = arr(match.participantUids, "participantUids").map((uid) => str(uid, "participant uid"));
  if (participantUids.length !== 2 || participantUids[0] === participantUids[1]) throw new RangeError("meeting needs two players");
  const start = int(match.stageStartsAtMs, "stageStartsAtMs");
  const end = int(match.stageEndsAtMs, "stageEndsAtMs");
  const duration = int(match.formMaxDurationMs, "formMaxDurationMs", 1);
  const deadline = end - READY_WINDOW_MS - duration;
  if (deadline <= start) throw new RangeError("form and ready window leave no scheduling time in stage");
  return {matchId: str(match.matchId, "matchId"), participantUids, start, end, duration, deadline};
}

function planMeetingProposal({match, uid, slotStartsAtMs, nowMs}) {
  const c = meetingContext(match);
  str(uid, "uid"); int(nowMs, "nowMs"); int(slotStartsAtMs, "slotStartsAtMs");
  if (!c.participantUids.includes(uid)) throw new RangeError("not a match participant");
  if (nowMs < c.start || nowMs >= c.deadline || slotStartsAtMs < nowMs || slotStartsAtMs > c.deadline) {
    throw new RangeError("proposal is outside the scheduling window");
  }
  return {uid, slotStartsAtMs, acceptedAtMs: nowMs};
}

function technicalOutcome(c, present) {
  if (present.length === 1) return {matchId: c.matchId, status: "FORFEIT", winnerUid: present[0],
    loserUid: c.participantUids.find((uid) => uid !== present[0]), technicalForfeit: true};
  return {matchId: c.matchId, status: "DOUBLE_OUT", winnerUid: null, loserUid: null, technicalForfeit: true};
}

/** Server event timestamps only. Once both are ready, start/deadline never move with later calls. */
function evaluateMeeting({match, proposals = [], confirmations = [], readySignals = [], nowMs}) {
  const c = meetingContext(match);
  int(nowMs, "nowMs");
  const offers = arr(proposals, "proposals").map((p) => {
    obj(p, "proposal");
    return planMeetingProposal({match, uid: p.uid, slotStartsAtMs: p.slotStartsAtMs, nowMs: p.acceptedAtMs});
  }).filter((p) => p.acceptedAtMs <= nowMs);
  const slots = new Map();
  for (const raw of arr(confirmations, "confirmations")) {
    const row = obj(raw, "confirmation");
    const uid = str(row.uid, "confirmation.uid");
    const at = int(row.acceptedAtMs, "confirmation.acceptedAtMs");
    const slot = int(row.slotStartsAtMs, "confirmation.slotStartsAtMs");
    if (!c.participantUids.includes(uid)) throw new RangeError("confirmation is not from participant");
    if (at > nowMs) continue;
    if (at < c.start || at >= c.deadline || at > slot ||
        !offers.some((offer) => offer.slotStartsAtMs === slot && offer.acceptedAtMs <= at)) {
      throw new RangeError("confirmation needs an existing timely proposal");
    }
    if (!slots.has(slot)) slots.set(slot, new Map());
    const byUid = slots.get(slot);
    if (!byUid.has(uid) || at < byUid.get(uid)) byUid.set(uid, at);
  }
  const agreements = [...slots.entries()].filter(([, byUid]) => byUid.size === 2)
    .map(([slot, byUid]) => ({slotStartsAtMs: slot, agreedAtMs: Math.max(...byUid.values())}))
    .sort((a, b) => cmp(a.agreedAtMs, b.agreedAtMs) || cmp(a.slotStartsAtMs, b.slotStartsAtMs));
  if (!agreements.length) {
    if (nowMs < c.deadline) return {matchId: c.matchId, status: "WAITING_FOR_AGREEMENT"};
    return technicalOutcome(c, [...new Set(offers.map((p) => p.uid))]);
  }
  const agreement = agreements[0];
  const windowEndMs = add(agreement.slotStartsAtMs, READY_WINDOW_MS);
  const readiness = new Map();
  for (const raw of arr(readySignals, "readySignals")) {
    const row = obj(raw, "ready signal");
    const uid = str(row.uid, "ready.uid");
    const at = int(row.acceptedAtMs, "ready.acceptedAtMs");
    if (!c.participantUids.includes(uid)) throw new RangeError("ready signal is not from participant");
    if (at > nowMs || at < agreement.slotStartsAtMs || at >= windowEndMs) continue;
    if (!readiness.has(uid) || at < readiness.get(uid)) readiness.set(uid, at);
  }
  if (readiness.size === 2) {
    const startedAtMs = Math.max(...readiness.values());
    const endsAtMs = add(startedAtMs, c.duration);
    return {matchId: c.matchId, status: "STARTED", ...agreement, windowEndMs, startedAtMs, endsAtMs,
      resultDue: nowMs >= endsAtMs};
  }
  if (nowMs >= windowEndMs) return {...technicalOutcome(c, [...readiness.keys()]), ...agreement, windowEndMs};
  return {matchId: c.matchId, status: nowMs < agreement.slotStartsAtMs ? "WAITING_FOR_WINDOW" : "WAITING_FOR_READY",
    ...agreement, windowEndMs};
}

/** Settled authoritative percentages and server-measured elapsed time. Seed is final tie-break. */
function resolvePlayedMatch({matchId, entrants, results}) {
  str(matchId, "matchId");
  const players = arr(entrants, "entrants");
  if (players.length !== 2) throw new RangeError("played match needs two entrants");
  const seeds = new Map(players.map((p) => [str(p.uid, "uid"), int(p.seed, "seed", 1)]));
  if (seeds.size !== 2 || new Set(seeds.values()).size !== 2) throw new RangeError("entrants must have unique uid and seed");
  const seen = new Set();
  const ranked = arr(results, "results").map((r) => {
    obj(r, "result"); const uid = str(r.uid, "result.uid");
    if (!seeds.has(uid) || seen.has(uid)) throw new RangeError("unexpected or duplicate result uid");
    seen.add(uid);
    const percent = int(r.percentScore, "percentScore");
    if (percent > 100) throw new RangeError("percentScore must be <= 100");
    return {uid, percentScore: percent, elapsedMs: int(r.elapsedMs, "elapsedMs"), seed: seeds.get(uid)};
  });
  if (ranked.length !== 2) throw new RangeError("two settled results required");
  ranked.sort((a, b) => cmp(b.percentScore, a.percentScore) || cmp(a.elapsedMs, b.elapsedMs) || cmp(a.seed, b.seed));
  return {matchId, status: "PLAYED", winnerUid: ranked[0].uid, loserUid: ranked[1].uid, technicalForfeit: false};
}

/** Propagates byes and double-outs. No bronze match: real semifinal losers share third place. */
function progressBracket({bracket, outcomes = []}) {
  obj(bracket, "bracket");
  if (bracket.status === "CANCELLED") return {status: "CANCELLED", resolved: [], pending: [], placements: []};
  const matches = bracket.rounds.flatMap((round) => round.matches);
  const validIds = new Set(matches.map((m) => m.matchId));
  const submitted = new Map();
  for (const raw of arr(outcomes, "outcomes")) {
    obj(raw, "outcome");
    const row = {
      matchId: str(raw.matchId, "outcome.matchId"), status: str(raw.status, "outcome.status"),
      winnerUid: raw.winnerUid == null ? null : str(raw.winnerUid, "outcome.winnerUid"),
      loserUid: raw.loserUid == null ? null : str(raw.loserUid, "outcome.loserUid"),
    };
    if (!validIds.has(row.matchId)) throw new RangeError("unknown match outcome");
    if (submitted.has(row.matchId) && JSON.stringify(submitted.get(row.matchId)) !== JSON.stringify(row)) {
      throw new RangeError("conflicting terminal match outcomes");
    }
    submitted.set(row.matchId, row);
  }
  const bySeed = new Map(bracket.entrants.map((p) => [p.seed, p]));
  const byUid = new Map(bracket.entrants.map((p) => [p.uid, p]));
  const resolved = new Map();
  const pending = [];
  for (const match of matches) {
    if (match.stage > 1 && (!resolved.has(match.leftMatchId) || !resolved.has(match.rightMatchId))) {
      if (submitted.has(match.matchId)) throw new RangeError("outcome precedes feeder matches");
      continue;
    }
    const left = match.stage === 1 ? bySeed.get(match.leftSeed) : byUid.get(resolved.get(match.leftMatchId).winnerUid);
    const right = match.stage === 1 ? bySeed.get(match.rightSeed) : byUid.get(resolved.get(match.rightMatchId).winnerUid);
    const entrants = [left, right].filter(Boolean);
    const outcome = submitted.get(match.matchId);
    if (entrants.length < 2) {
      if (outcome) throw new RangeError("bye/empty node cannot carry a played or forfeit outcome");
      resolved.set(match.matchId, {matchId: match.matchId, status: entrants.length ? "BYE" : "EMPTY",
        winnerUid: entrants.length ? entrants[0].uid : null, loserUid: null, technicalForfeit: false});
      continue;
    }
    if (!outcome) { pending.push({matchId: match.matchId, entrants: entrants.map((p) => ({...p}))}); continue; }
    const uids = entrants.map((p) => p.uid);
    if (outcome.status === "DOUBLE_OUT") {
      if (outcome.winnerUid != null || outcome.loserUid != null) throw new RangeError("double-out has no winner/loser placement");
    } else if (["PLAYED", "FORFEIT"].includes(outcome.status)) {
      if (!uids.includes(outcome.winnerUid) || !uids.includes(outcome.loserUid) || outcome.winnerUid === outcome.loserUid) {
        throw new RangeError("outcome participants do not match bracket");
      }
    } else throw new RangeError("outcome must be terminal");
    resolved.set(match.matchId, {matchId: match.matchId, status: outcome.status,
      winnerUid: outcome.winnerUid, loserUid: outcome.loserUid, technicalForfeit: outcome.status !== "PLAYED"});
  }
  const finalMatch = bracket.rounds[bracket.rounds.length - 1].matches[0];
  const final = resolved.get(finalMatch.matchId);
  const placements = [];
  if (final) {
    if (final.winnerUid) placements.push({uid: final.winnerUid, rank: 1, slot: "champion", technicalForfeit: false});
    if (final.status === "PLAYED") placements.push({uid: final.loserUid, rank: 2, slot: "runner-up", technicalForfeit: false});
    if (bracket.rounds.length >= 2) {
      bracket.rounds[bracket.rounds.length - 2].matches.forEach((match, index) => {
        const result = resolved.get(match.matchId);
        if (result && result.status === "PLAYED") placements.push({uid: result.loserUid, rank: 3,
          slot: index === 0 ? "third-upper" : "third-lower", technicalForfeit: false});
      });
    }
  }
  return {status: final ? "FINISHED" : "ACTIVE", resolved: [...resolved.values()], pending, placements};
}

/** Funding was escrowed elsewhere. Fixed placement-slot IDs prevent a rerun minting new payouts. */
function planPrizePayouts({eventId, prizes, fundedBoxes, placements}) {
  str(eventId, "eventId");
  const funding = validatePrizeSchedule(prizes, fundedBoxes);
  const slots = new Set(); const uids = new Set(); const payouts = [];
  const expectedRanks = {champion: 1, "runner-up": 2, "third-upper": 3, "third-lower": 3};
  for (const raw of arr(placements, "placements")) {
    const row = obj(raw, "placement"); const uid = str(row.uid, "placement.uid");
    if (!Object.hasOwn(expectedRanks, row.slot) || expectedRanks[row.slot] !== row.rank) {
      throw new RangeError("only first, second and two third-place slots are allowed");
    }
    if (slots.has(row.slot) || uids.has(uid)) throw new RangeError("duplicate placement slot/player");
    slots.add(row.slot); uids.add(uid);
    if (typeof row.technicalForfeit !== "boolean") throw new TypeError("technicalForfeit must be explicit");
    if (row.technicalForfeit) continue;
    const amount = row.rank === 1 ? prizes.first : row.rank === 2 ? prizes.second : prizes.third;
    if (amount > 0) payouts.push({payoutId: key(eventId, "prize", row.slot), uid, rank: row.rank,
      slot: row.slot, currency: "BOXES", amount});
  }
  payouts.sort((a, b) => cmp(a.rank, b.rank) || cmp(a.slot, b.slot));
  const paidBoxes = safe(payouts.reduce((sum, row) => sum + BigInt(row.amount), 0n), "paidBoxes");
  return {...funding, payouts, paidBoxes, unusedReturnBoxes: fundedBoxes - paidBoxes,
    returnId: key(eventId, "unused-fund-return")};
}

module.exports = {
  READY_WINDOW_MS, QUALIFICATION_DURATION_MS, STAGE_DURATION_MS,
  preflightPool, validatePrizeSchedule, createEventPlan, qualificationForms, planQualificationAttempt, planAdmission,
  selectQualifiers, buildBracket, planMeetingProposal, evaluateMeeting, resolvePlayedMatch,
  progressBracket, planPrizePayouts,
};
