"use strict";

// Story 5.10 client projection: readEvent carries the published qualification ranking rows.
//
// The rows are the ones the server already froze (course_tournament_rankings), redacted to the
// client allowlist {uid, place, ratingPercent, averagePercent} — place authoritative, percentages
// display-only, nothing recomputed. An event without a retained ranking projects no ranking key
// at all, listEvents rows never carry one, and an event whose frozen document is missing or
// fails the publication gate fails closed (UNAVAILABLE), like bracket state — never a silently
// empty leaderboard.

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {createCourseTournamentController} = require("./course-tournament-controller");
const {TOURNAMENT_PATHS: P} = require("./tournament-service");
const {QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {tournamentDigest: digest} = require("./tournament-bank");

const T = 1_800_000_000_000; const DAY = 86400000; const OWNER = "author"; const EVENT = "event_000000000000001";
const E = `${P.events}/${EVENT}`; const tests = [];
function test(name, body) { tests.push({name, body}); }
const clone = structuredClone;
const RANKING_DOC = `${QUALIFICATION_RANKINGS}/${EVENT}`;
const PRIZES = {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
  {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]};

function pagedDb(initial) {
  const db = premiumTestDb(initial); const collection = db.collection; const run = db.runTransaction;
  function snapshot(ref) {
    assert.equal(ref.sort, "__name__"); assert.ok(Number.isInteger(ref.maximum) && ref.maximum >= 1 && ref.maximum <= 101);
    const rows = [...db.store].filter(([key, row]) => key.startsWith(`${ref.path}/`) && key.split("/").length === ref.path.split("/").length + 1 &&
      ref.filters.every(({field, op, value}) => op === "==" ? row[field] === value : op === "in" ? Array.isArray(value) && value.includes(row[field]) : false) && (ref.after === null || key.split("/").at(-1) > ref.after))
      .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).slice(0, ref.maximum);
    return {docs: rows.map(([key, row]) => ({id: key.split("/").at(-1), exists: true, data: () => clone(row)}))};
  }
  function extend(ref, after = null) {
    return {...ref, after, where: (...args) => extend(ref.where(...args), after), orderBy: (...args) => extend(ref.orderBy(...args), after),
      limit: (...args) => extend(ref.limit(...args), after), startAfter: (cursor) => extend(ref, cursor), get: async () => snapshot({...ref, after})};
  }
  db.collection = (...args) => extend(collection(...args));
  db.runTransaction = (body) => run(async tx => {
    let written = false;
    return body({...tx,
      get: async ref => { assert.equal(written, false, "read after write"); return ref.sort === "__name__" ? snapshot(ref) : tx.get(ref); },
      getAll: async (...refs) => { assert.equal(written, false, "read after write"); return tx.getAll(...refs); },
      create: (...args) => { written = true; return tx.create(...args); }, set: (...args) => { written = true; return tx.set(...args); },
    });
  });
  return db;
}

function fixture(players = ["alice", "bob"]) {
  const wallet = () => ({boxCount: 0, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
    lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T});
  const downloads = Object.fromEntries(players.map(uid =>
    [`course_tournament_downloads/${digest([uid, "course"])}`, {schemaVersion: 1, uid, courseId: "course", downloadedAtMs: T}]));
  const db = pagedDb({"catalogs/catalog": {questType: "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: OWNER, version: 1, visibleOn: ["archive"], archived: true},
    "sections/section": {questId: "course", version: 1}, "themes/theme": {sectionId: "section", version: 1},
    "lessons/lesson": {themeId: "theme", version: 1, reviewSubmissionId: "submission", reviewApprovedAtMs: T - 1},
    [`users/${OWNER}`]: {...wallet(), boxCount: 100}, ...Object.fromEntries(players.map(uid => [`users/${uid}`, wallet()])), ...downloads});
  for (let i = 0; i < 80; i++) {
    const questionId = `q${String(i).padStart(3, "0")}`;
    db.store.set(`questions/${questionId}`, {lessonId: "lesson", version: 1, payload: JSON.stringify({id: questionId,
      type: "SingleChoice", difficulty: i < 60 ? "EASY" : "HARD", text: "A?", options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"})});
  }
  const state = {now: T}; let serial = 0;
  const controller = createCourseTournamentController({db, nowMs: () => state.now,
    isCurator: async () => false,
    loadLessonContext: async (tx, lessonId) => {
      const lesson = (await tx.get(db.doc(`lessons/${lessonId}`))).data();
      return {lessonId, authorUid: OWNER, catalogId: "catalog", questId: "course", sectionId: "section", themeId: "theme",
        questType: "COURSE", published: true, reviewApproved: lesson?.reviewSubmissionId === "submission", contentVersion: lesson?.version};
    }});
  const id = () => `client_operation_${String(++serial).padStart(16, "0")}`;
  const operate = (kind, uid = OWNER, fields = {}) => controller.operate(uid, {kind, operationId: id(), eventId: EVENT, ...fields});
  async function createEvent() {
    const prepared = await controller.prepare(OWNER, {requestId: `preparation_request_${String(++serial).padStart(16, "0")}`,
      source: {kind: "COURSE", id: "course"}});
    return operate("CREATE", OWNER, {source: {kind: "COURSE", id: "course", snapshotId: prepared.snapshotId},
      startsAtMs: state.now, fundedBoxes: 12, prizes: PRIZES});
  }
  async function qualify() {
    await createEvent();
    for (const uid of players) await operate("START_QUALIFICATION", uid, {roundNumber: 1, attemptId: `attempt_${uid}_00000000001`});
    state.now = T + 7 * DAY;
    return controller.reconcileEvent(EVENT);
  }
  return {db, state, controller, qualify, createEvent, operate};
}

function assertSafe(value) {
  const denied = new Set(["answerKey", "keys", "correctOptionId", "correctOptionIds", "correctCandidateId", "blankToCandidate", "idMap",
    "formFingerprint", "fingerprint", "contentHash", "privateBankFingerprint", "poolFingerprint", "bankHash", "proofHash", "bankJson", "proofJson", "serverSeed", "codeAnswer", "scoreDigits", "scoreDigit",
    "sessionId", "percentScore", "elapsedMs", "startedAtMs", "completedAtMs"]);
  function walk(row) { if (!row || typeof row !== "object") return; for (const [key, item] of Object.entries(row)) { assert.ok(!denied.has(key), `private field ${key}`); walk(item); } }
  walk(value);
}
const error = (fn, code) => assert.rejects(fn, e => { assert.equal(e.code, code); return true; });

test("readEvent carries the frozen ranking rows within the client allowlist", async () => {
  const f = fixture(); await f.qualify();
  assert.equal(f.db.store.get(E).status, "BRACKET");
  const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  const ranking = view.event.qualificationRanking;
  assert.ok(ranking, "a ranked event projects its ranking");
  assert.equal(ranking.totalRanked, 2);
  assert.deepEqual(ranking.rows.map(row => row.place), [1, 2]);
  assert.deepEqual(ranking.rows.map(row => row.uid).sort(), ["alice", "bob"]);
  for (const row of ranking.rows) {
    assert.deepEqual(Object.keys(row).filter(key => !["uid", "place", "ratingPercent", "averagePercent"].includes(key)), [],
      "ranking rows stay within the redacted allowlist");
  }
  assertSafe(view);
});

test("listEvents rows never carry a ranking projection", async () => {
  const f = fixture(); await f.qualify();
  const page = await f.controller.listEvents("viewer", {});
  assert.ok(page.events.length >= 1);
  for (const row of page.events) assert.equal("qualificationRanking" in row, false, "browse rows stay ranking-free");
});

test("an event without a retained ranking projects no ranking at all", async () => {
  const f = fixture();
  await f.createEvent();
  const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.equal("qualificationRanking" in view.event, false);
  assertSafe(view);
});

test("a missing frozen document fails closed with UNAVAILABLE", async () => {
  const f = fixture(); await f.qualify();
  f.db.store.delete(RANKING_DOC);
  await error(() => f.controller.readEvent("viewer", {eventId: EVENT}), "UNAVAILABLE");
});

test("a truncated frozen ranking fails closed instead of reading short", async () => {
  const f = fixture(); await f.qualify();
  const ranking = clone(f.db.store.get(RANKING_DOC));
  ranking.ranking = ranking.ranking.slice(0, 1);
  f.db.store.set(RANKING_DOC, ranking);
  await error(() => f.controller.readEvent("viewer", {eventId: EVENT}), "UNAVAILABLE");
});

test("a ranking row carrying private run material never leaves through the projection", async () => {
  const f = fixture(); await f.qualify();
  const ranking = clone(f.db.store.get(RANKING_DOC));
  ranking.ranking = ranking.ranking.map(row => ({...row, sessionId: `q_${"a".repeat(64)}`}));
  f.db.store.set(RANKING_DOC, ranking);
  const view = await f.controller.readEvent("viewer", {eventId: EVENT});
  assert.ok(view.event.qualificationRanking.rows.every(row => !("sessionId" in row)),
    "the projection picks only allowlisted row keys");
  assertSafe(view);
});

(async () => {
  let failed = 0;
  for (const {name, body} of tests) {
    try { await body(); console.log(`ok - ${name}`); } catch (error) { failed++; console.error(`not ok - ${name}`); console.error(error); }
  }
  if (failed) { console.error(`${failed} of ${tests.length} failed`); process.exitCode = 1; } else { console.log(`all ${tests.length} passed`); }
})();
