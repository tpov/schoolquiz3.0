"use strict";

// Story 5.12 continuity glue for the live DEMO rig (never production): one continuous run of the
// full competition lifecycle — create (v1 prizes, escrow, funding ledger) → qualification (three
// EASY attempts per player over real HTTP, admission download before START) → explicit worker
// sweeps across the fixed cutoff (the rig's trusted logical-clock seam, the same one the accepted
// bulk harnesses drive) → bracket → meetings/agreement → matches → awards with conservation —
// plus the plan §4 degenerate sizes 0/1/2 and crash-recovery probes (operation replays, repeated
// sweeps, one injected transaction abort at the close boundary) between phases. Real time cannot
// wait seven days, so every phase after the qualification cutoff runs through the same controller
// entry points the callables use, driven by an injected monotonic clock; the real-HTTP acceptance
// surface stays with scripts/course-tournament-http.test.js. Env asserts stay fail-closed on the
// private demo rig. Writes through the trusted wrapper are scoped to THIS RUN'S namespace: the
// run's events (their private forms/links/rankings and calculation subcollections), the run's
// registered sessions and verified runs, the run's owner accounts and their owner-prefixed ledger
// entries, the run's own bank snapshot, and the owner-bound receipt collections (operations,
// session operations, answer ingress, funding, downloads, bank preparations) — each of those
// asserted to carry a synthetic owner of this run (bulk-rig precedent); the global worker cursor
// is not writable through this wrapper at all.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const {createRequire, Module} = require("node:module");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Real credentials are not permitted");
const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
const {TOURNAMENT_PATHS: P, matchSessionId, qualificationSessionId} = require("/repo/functions/tournament-service");
const {tournamentDigest: digest} = require("/repo/functions/tournament-bank");
const {SOURCE_BANKS} = require("/repo/functions/tournament-source-bank");
const {VERIFIED_RUNS, advanceTournamentSession} = require("/repo/functions/tournament-session");
const {QUALIFICATION_RANKINGS} = require("/repo/functions/tournament-qualification-freeze");
const {LEDGER_PATH} = require("/repo/functions/currency-ledger");
const {createCourseTournamentController} = require("/repo/functions/course-tournament-controller");

const PRIZES = {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
  {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]};
const CORRECT = {type: "single-choice", selected: "b"}; // SINGLE fixture's correctOptionId.
const WRONG = {type: "single-choice", selected: "a"};
const DELAY = 60_000; // The rig's qualification window opens one minute after CREATE.
const PLAYER_COUNT = 4;

const runId = `lc_${randomUUID().replaceAll("-", "")}`;
const id = (name) => `${runId}_${name}`;
const names = Object.fromEntries(["catalog", "course", "section", "theme", "lesson", "submission",
  "mainEvent", "event0", "event1", "event2", "event3"].map((name) => [name, id(name)]));
const events = [names.mainEvent, names.event0, names.event1, names.event2, names.event3];
const endpoints = ["prepareCourseTournamentBank", "submitCourseTournamentOperation",
  "submitCourseTournamentSession", "fetchCourseTournamentEvent", "fetchCourseTournamentSession",
  "recordCourseTournamentDownload"];
const checks = [];
const evidence = {runId, projectId: PROJECT, fixtureNames: names, retainedSyntheticFixtures: true};
const bounds = {httpCalls: 0, transactions: 0, writes: 0, queryReads: 0, sweeps: 0, injectedAborts: []};
const wallStarted = Date.now();
let stage = "preflight";
let fault = null;
let bankPath = null;
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
function progress(name, extra = {}) { stage = name; console.log(JSON.stringify({stage, runId, ...extra})); }
function passed(name, extra) { checks.push(name); progress(name, extra); }

async function post(url, body, token) {
  bounds.httpCalls++;
  const response = await fetch(url, {method: "POST", headers: {"content-type": "application/json",
    ...(token ? {authorization: `Bearer ${token}`} : {})}, body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000)});
  const raw = await response.text();
  let json;
  try { json = JSON.parse(raw); } catch { throw new Error(`Non-JSON HTTP ${response.status}`); }
  if (!response.ok || json.error) {
    const error = new Error(json.error?.message || `HTTP ${response.status}`);
    error.code = json.error?.status; error.reason = json.error?.details?.reason; throw error;
  }
  return json;
}
function call(actor, endpoint, data) {
  assert.ok(endpoints.includes(endpoint), "Only the scoped course tournament callables");
  return post(`http://${HOST}:5001/${PROJECT}/us-central1/${endpoint}`, {data}, actor?.token).then((json) => json.result);
}
async function account(role) {
  const json = await post(`http://${HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo`,
    {returnSecureToken: true});
  assert.equal(typeof json.localId, "string"); assert.equal(typeof json.idToken, "string");
  return {role, uid: json.localId, token: json.idToken};
}
function safe(value) {
  const blocked = new Set(["answerKey", "keys", "correctOptionId", "correctOptionIds", "correctCandidateId",
    "codeAnswer", "scoreDigits", "scoreDigit", "questions", "qualificationForms", "forms", "sharedForm",
    "bankJson", "proofJson", "serverSeed", "fingerprint", "formFingerprint", "contentHash", "privateBankFingerprint",
    "proofHash", "bankHash", "poolFingerprint"]);
  function visit(row) {
    if (!row || typeof row !== "object") return;
    for (const [key, child] of Object.entries(row)) { assert.ok(!blocked.has(key), `Private wire field: ${key}`); visit(child); }
  }
  visit(value);
}

// --- Admin SDK side: scoped wrapper db (fail-closed write scope) + the trusted logical clock ---
const app = admin.initializeApp({projectId: PROJECT});
const raw = app.firestore();
let logical = Date.now();
const nowMs = () => logical;
function advance(value) { assert.ok(value >= logical, "The rig clock only moves forward"); logical = value; }
const owners = new Set();
const sessions = new Set();
const runEvents = new Set(events);
// Receipt collections whose documents carry a synthetic owner this run must name.
const OWNER_BOUND = new Set([P.operations, "course_tournament_session_operations", "course_tournament_answer_ingress",
  P.funding, "course_tournament_downloads"]);
function scopeWrite(path, value) {
  const parts = path.split("/");
  const ownEvent = parts[0] === P.events && runEvents.has(parts[1]);
  const ownPrivateEvent = [P.privateForms, P.links].includes(parts[0]) && runEvents.has(parts[1]);
  const ownRanking = parts[0] === QUALIFICATION_RANKINGS && runEvents.has(parts[1]);
  const ownSession = [P.sessions, VERIFIED_RUNS].includes(parts[0]) ? sessions.has(parts[1])
    : ["course_tournament_session_operations", "course_tournament_answer_ingress"].includes(parts[0]);
  const ownAccount = ["users", "premium_accounts"].includes(parts[0]) && owners.has(parts[1]);
  const ownLedger = path.startsWith(`${LEDGER_PATH}/`) && owners.has(value?.uid) &&
    String(parts.at(-1)).startsWith(`${value.uid}_`);
  const ownReceipt = OWNER_BOUND.has(parts[0]);
  assert.ok(ownEvent || ownPrivateEvent || ownRanking || ownSession || ownAccount || ownLedger ||
    ownReceipt || path === bankPath, `Out-of-fixture write refused: ${path}`);
  if (value?.eventId !== undefined) assert.ok(runEvents.has(value.eventId), `Cross-event record refused: ${path}`);
  // Bulk-rig precedent: every receipt/record must name a synthetic owner of this run (funding and
  // downloads carry uid; bank preparations carry ownerUid), and the run's own bank snapshot is
  // exact-path and owner-pinned. Funding is additionally event-checked above.
  if (ownReceipt || path === bankPath) {
    assert.ok(owners.has(parts[0] === "course_tournament_bank_preparations" || path === bankPath ? value?.ownerUid : value?.uid),
      `Fixture record owner must be synthetic: ${path}`);
  }
  bounds.writes++;
}
const db = {
  doc: (path) => raw.doc(path),
  collection: (path) => { bounds.queryReads++; return raw.collection(path); },
  runTransaction: (body) => raw.runTransaction(async (transaction) => {
    bounds.transactions++;
    const writes = [];
    const tx = {
      get: async (ref) => transaction.get(ref),
      getAll: (...refs) => transaction.getAll(...refs),
      create: (ref, value) => { scopeWrite(ref.path, value); writes.push({path: ref.path, value}); transaction.create(ref, value); },
      set: (ref, value, options) => { scopeWrite(ref.path, value); writes.push({path: ref.path, value}); transaction.set(ref, value, options); },
    };
    const result = await body(tx);
    // Crash-recovery probes: abort the callback after staging; the SDK rolls the batch back.
    const receipt = (row) => row.path.startsWith(`${P.operations}/`) && typeof row.value?.operationId === "string";
    const matched = fault === "ABORT_CLOSE" && writes.some((row) => receipt(row) && row.value.operationId.startsWith("ctw_close")) ||
      fault === "ABORT_ADVANCE" && writes.some((row) => receipt(row) && row.value.operationId.startsWith("ctw_match"));
    if (matched) {
      const kind = fault; fault = null; bounds.injectedAborts.push(kind);
      const error = new Error(`Injected ${kind === "ABORT_CLOSE" ? "close" : "settlement"}-boundary abort`);
      error.code = "HARNESS_ABORT"; throw error;
    }
    return result;
  }),
};
let controllerInstance = null;
function controller() {
  if (!controllerInstance) {
    controllerInstance = createCourseTournamentController({db, nowMs, isCurator: async () => false,
      loadLessonContext: async (tx, lessonId) => {
        assert.equal(lessonId, names.lesson);
        const paths = [`lessons/${lessonId}`, `themes/${names.theme}`, `sections/${names.section}`, `quests/${names.course}`,
          `catalogs/${names.catalog}`, `admin/review/lessons/${lessonId}`,
          ...["TESTING", "LOGIC"].map((kind) => `admin/review/lessons/${lessonId}/reviews/${id(kind)}`)];
        const values = await tx.getAll(...paths.map((path) => db.doc(path))); assert.ok(values.every((doc) => doc.exists));
        const [lesson, theme, section, course, catalog, review] = values.map((doc) => doc.data());
        assert.equal(review.status, "PUBLISHED"); assert.equal(lesson.reviewSubmissionId, review.submissionId);
        assert.equal(course.catalogId, names.catalog); assert.equal(catalog.questType, "COURSE");
        return {lessonId, authorUid: review.ownerUid, catalogId: names.catalog, questId: names.course,
          sectionId: names.section, themeId: names.theme, questType: "COURSE", published: true,
          reviewApproved: true, contentVersion: lesson.version};
      }});
  }
  return controllerInstance;
}
async function read(path) { return (await raw.doc(path)).get().then((doc) => doc.data()); }
async function eventDoc(eventId) { return read(`${P.events}/${eventId}`); }
async function financialSnapshot() {
  const rows = [];
  for (const uid of [...owners].sort()) rows.push([`users/${uid}`, (await read(`users/${uid}`)) || null]);
  for (const eventId of events) {
    const ledger = await raw.collection(LEDGER_PATH).where("details.eventId", "==", eventId).get();
    ledger.docs.forEach((doc) => rows.push([`${LEDGER_PATH}/${doc.id}`, doc.data()]));
    const funding = await raw.collection(P.funding).where("eventId", "==", eventId).get();
    funding.docs.forEach((doc) => rows.push([`${P.funding}/${doc.id}`, doc.data()]));
  }
  return digest(rows);
}
function assertEscrowIntact(event) {
  const escrow = event.escrow;
  assert.equal(escrow.reservedBoxes, escrow.paidBoxes + escrow.returnedBoxes + escrow.remainingBoxes,
    `Escrow conservation violated: ${JSON.stringify(escrow)}`);
}
async function sweep(eventId) { bounds.sweeps++; return controller().reconcileEvent(eventId); }

/** Plays one 20-question session to settlement through the given transport. `final` answers WRONG
 * for the loser cohort so percent scores stay deterministic. `probeReplay` replays the first
 * answer's exact operation and requires the identical receipt. */
async function answerWholeSession(submit, readView, uid, sessionId, kind, lap) {
  let view = await readView(sessionId);
  for (let index = 0; index < 20; index++) {
    assert.notEqual(view.session.status, "SETTLED", `Settled early at ${index}`);
    assert.ok(view.currentQuestion, `No current question at index ${index}`);
    if (lap) advance(nowMs() + lap);
    const payload = {kind: "ANSWER", sessionId, operationId: id(`ans_${sessionId.slice(1, 9)}_${index}`),
      questionIndex: view.session.questionIndex, questionId: view.currentQuestion.questionId,
      answerPayload: kind === "loser" && index === 19 ? WRONG : CORRECT};
    const answered = await submit(payload);
    safe(answered);
    assert.equal(answered.status, index === 19 ? "COMPLETED" : "ANSWER_ACCEPTED");
    if (index === 0 && kind === "probe") {
      assert.deepEqual(await submit(payload), answered, "Answer replay must return the identical receipt");
    }
    view = answered;
  }
  assert.equal(view.session.status, "SETTLED"); assert.equal(view.session.questionIndex, 20);
  const run = await read(`${VERIFIED_RUNS}/${sessionId}`);
  assert.ok(run, `No verified run document for ${sessionId}`);
  assert.equal(run.status, "SETTLED");
  if (kind === "loser") assert.ok(run.percentScore < 100, `Loser percent ${run.percentScore}`);
  else assert.equal(run.percentScore, 100);
  return run;
}

/** Drives one bracket match through meeting agreement, readiness, play and an explicit sweep.
 * `financialBefore` is the caller's pinned money digest: re-asserted at entry, after the meeting
 * phase, and across an injected settlement abort; the new post-settlement digest is returned. */
async function playMatch(eventId, matchId, entrants, winnerIndex, label, kind, financialBefore) {
  const match = (await eventDoc(eventId)).bracket.rounds.flatMap((round) => round.matches)
    .find((row) => row.matchId === matchId);
  assert.ok(match, `Bracket match ${matchId} missing from the event`);
  assert.equal(await financialSnapshot(), financialBefore, "Money must be untouched entering the match");
  const deadline = match.stageEndsAtMs - 3_600_000 - match.sharedForm.maxDurationMs;
  advance(Math.max(nowMs() + 1_000, match.stageStartsAtMs + 1_000));
  const slot = nowMs() + 2_000;
  assert.ok(slot <= deadline, "Slot must fit the scheduling window");
  for (const [index, uid] of entrants.entries()) {
    const response = await controller().operate(uid, {kind: "PROPOSE", eventId,
      operationId: id(`prop_${label}_${index}`), matchId, slotStartsAtMs: slot});
    safe(response);
    if (index === 0 && kind === "probe") {
      const replay = await controller().operate(uid, {kind: "PROPOSE", eventId,
        operationId: id(`prop_${label}_0`), matchId, slotStartsAtMs: slot});
      assert.deepEqual(replay, response, "Proposal replay must be identical");
    }
  }
  for (const [index, uid] of entrants.entries()) {
    const confirmed = await controller().operate(uid, {kind: "CONFIRM", eventId,
      operationId: id(`conf_${label}_${index}`), matchId, slotStartsAtMs: slot});
    safe(confirmed);
  }
  advance(slot);
  for (const uid of entrants) sessions.add(matchSessionId(eventId, matchId, uid));
  for (const [index, uid] of entrants.entries()) {
    const ready = await controller().operate(uid, {kind: "READY", eventId,
      operationId: id(`ready_${label}_${index}`), matchId});
    safe(ready);
    assert.equal(ready.status, index === entrants.length - 1 ? "STARTED" : "WAITING_FOR_READY",
      "The last ready signal starts the match; earlier ones wait");
    sessions.add(matchSessionId(eventId, matchId, uid));
  }
  for (const [index, uid] of entrants.entries()) {
    const sessionId = matchSessionId(eventId, matchId, uid);
    await answerWholeSession((payload) => controller().session(uid, payload),
      (sid) => controller().readSession(uid, {sessionId: sid}), uid, sessionId,
      index === winnerIndex ? "winner" : "loser", 1_000);
  }
  advance(nowMs() + 1_000);
  const financialAfterMeetings = await financialSnapshot();
  assert.equal(financialAfterMeetings, financialBefore, "Meetings, readiness and match play must not move money");
  if (kind === "probe") {
    // Crash-recovery probe at the settlement boundary: the ADVANCE_MATCH receipt transaction
    // aborts after staging; the retry reuses the same permanent operation id and commits once.
    fault = "ABORT_ADVANCE";
    await assert.rejects(sweep(eventId), (error) => error.code === "HARNESS_ABORT");
    const unsettled = await eventDoc(eventId);
    assert.equal(unsettled.status, "BRACKET");
    assert.equal((unsettled.outcomes || []).some((row) => row.matchId === matchId), false,
      "An aborted settlement must not record an outcome");
    assert.equal(await financialSnapshot(), financialAfterMeetings, "An aborted settlement must not move money");
  }
  let outcome = null; let lastResult = null;
  for (let attempt = 0; attempt < 8 && !outcome; attempt++) {
    lastResult = await sweep(eventId);
    outcome = ((await eventDoc(eventId)).outcomes || []).find((row) => row.matchId === matchId);
    if (!outcome) advance(nowMs() + 60_000);
  }
  assert.ok(outcome, `Match ${matchId} did not resolve: ${JSON.stringify(lastResult)}`);
  assert.equal(outcome.status, "PLAYED"); assert.equal(outcome.winnerUid, entrants[winnerIndex]);
  assertEscrowIntact(await eventDoc(eventId));
  return financialSnapshot(); // Payouts legitimately move money; the caller pins the new baseline.
}

async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hub.status, 200);
  const running = await hub.json();
  assert.equal(running.auth.port, 9099); assert.equal(running.firestore.port, 8080);
  // Functions identity, not merely "something answers 404": the emulator's unknown-function probe
  // enumerates exactly the deployed callables (verified against this live rig), and the mounted
  // code answers an unauthenticated scoped callable with its own structured denial.
  const unknown = await fetch(`http://${HOST}:5001/${PROJECT}/us-central1/noSuchCallable`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(unknown.status, 404);
  const listed = await unknown.text();
  for (const endpoint of endpoints) {
    assert.ok(listed.includes(`us-central1-${endpoint}`), `Scoped callable not deployed on the rig: ${endpoint}`);
  }
  let identity = null;
  try { await post(`http://${HOST}:5001/${PROJECT}/us-central1/fetchCourseTournamentEvent`, {data: {}}); }
  catch (error) { identity = {code: error.code, message: error.message}; }
  assert.deepEqual(identity, {code: "UNAUTHENTICATED", message: "Authenticated uid is required"},
    "The mounted functions code must answer the scoped callable itself");
  evidence.mountedSourceShas = Object.fromEntries(["index.js", "course-tournament-controller.js", "tournament-service.js",
    "tournament-source-bank.js"].map((file) => [file, createHash("sha256").update(readFileSync(`/repo/functions/${file}`)).digest("hex")]));
  progress("target-verified-private-demo");

  // --- Fixtures: one author + four real players (Auth emulator), one verified course ---
  const author = await account("author");
  const players = [];
  for (let index = 0; index < PLAYER_COUNT; index++) players.push(await account(`player${index}`));
  for (const actor of [author, ...players]) owners.add(actor.uid);
  // d0 is the stray-credit canary: a funded, namespaced account that never joins or funds any
  // event. Its balance must still be exactly 0 at terminal check — any payout/refund path that
  // walks the owner set instead of the actual placements/funder would move it off zero.
  const synthetic = {d0: id("d0_author"), d1: id("d1_player"), d2a: id("d2_alice"), d2b: id("d2_bob")};
  Object.values(synthetic).forEach((uid) => owners.add(uid));
  const seedTime = Date.now();
  const wallet = (uid, boxCount) => ({boxCount, nolics: 1000, pointsNolics: 1000, standardHearts: 5, goldHearts: 1,
    lifePoints: 500, plasmaPoints: 100, lifePointsUpdatedAtMs: seedTime, plasmaPointsUpdatedAtMs: seedTime, uid, testFixture: runId});
  const rows = [
    ...[author, ...players].map((actor) => [`users/${actor.uid}`, wallet(actor.uid, actor.role === "author" ? 200 : 0)]),
    ...[author, ...players].map((actor) => [`profiles/${actor.uid}`, {uid: actor.uid, testFixture: runId}]),
    ...Object.entries(synthetic).map(([key, uid]) => [`users/${uid}`, wallet(uid, 0)]),
    [`catalogs/${names.catalog}`, {questType: "COURSE", version: 1, testFixture: runId}],
    [`quests/${names.course}`, {catalogId: names.catalog, authorUid: author.uid, version: 1, visibleOn: ["archive"], archived: true, testFixture: runId}],
    [`sections/${names.section}`, {questId: names.course, version: 1, testFixture: runId}],
    [`themes/${names.theme}`, {sectionId: names.section, version: 1, testFixture: runId}],
    [`lessons/${names.lesson}`, {themeId: names.theme, version: 1, reviewSubmissionId: names.submission, reviewApprovedAtMs: seedTime - 1000, testFixture: runId}],
    [`admin/review/lessons/${names.lesson}`, {ownerUid: author.uid, questId: names.course, submissionId: names.submission,
      status: "PUBLISHED", sourceLanguages: ["en"], testFixture: runId}],
    ...["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${names.lesson}/reviews/${id(kind)}`, {
      kind, reviewerUid: id(`reviewer_${kind}`), acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100,
      submissionId: names.submission, testFixture: runId}]),
    ...Array.from({length: 80}, (_, index) => [`questions/${id(`q${String(index).padStart(3, "0")}`)}`, {
      lessonId: names.lesson, version: 1, language: "en", difficulty: index < 60 ? "EASY" : "HARD", testFixture: runId,
      payload: JSON.stringify({...SINGLE, text: `${SINGLE.text} ${"Synthetic lifecycle fixture. ".repeat(12)}`,
        difficulty: index < 60 ? "EASY" : "HARD"})}]),
  ];
  const batch = raw.batch();
  rows.forEach(([path, body]) => { assert.equal(body.testFixture, runId); batch.create(raw.doc(path), body); });
  await batch.commit();
  evidence.createdFixtureDocuments = rows.length;
  passed("fresh-accounts-and-namespaced-verified-course-created-create-only");

  // --- CREATE over real HTTP: v1 prizes, escrow, funding, ledger + replay probe ---
  const source = {kind: "COURSE", id: names.course};
  const prepared = await call(author, "prepareCourseTournamentBank", {requestId: id("prepare"), source});
  assert.deepEqual(prepared.counts, {easy: 60, hard: 20, total: 80}); safe(prepared);
  bankPath = `${SOURCE_BANKS}/${prepared.snapshotId}`;
  assert.equal((await read(bankPath)).ownerUid, author.uid);
  const mainStartsAt = Date.now() + DELAY;
  const createRequest = {kind: "CREATE", operationId: id("create_main"), eventId: names.mainEvent,
    source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: mainStartsAt, prizes: PRIZES, fundedBoxes: 12};
  const created = await call(author, "submitCourseTournamentOperation", createRequest);
  assert.equal(created.status, "CREATED"); safe(created);
  assert.equal(created.event.organizerUid, author.uid);
  assert.deepEqual(created.event.escrow, {reservedBoxes: 12, remainingBoxes: 12, paidBoxes: 0, returnedBoxes: 0});
  assert.equal((await read(`users/${author.uid}`)).boxCount, 188);
  const financialAfterCreate = await financialSnapshot();
  assert.deepEqual(await call(author, "submitCourseTournamentOperation", createRequest), created,
    "CREATE replay must return the original receipt");
  assert.equal(await financialSnapshot(), financialAfterCreate, "CREATE replay must not move money twice");
  evidence.creation = {eventId: names.mainEvent, status: created.status, escrow: created.event.escrow};
  passed("real-HTTP-CREATE-reserves-12-boxes-once-with-identical-replay");

  // --- Qualification over real HTTP: admission download, three EASY attempts per player ---
  let view = await call(players[0], "fetchCourseTournamentEvent", {eventId: names.mainEvent});
  safe(view); assert.equal(view.event.status, "QUALIFICATION");
  assert.equal(typeof view.serverNowMs, "number", "The event view must carry the server clock before polling");
  const pollDeadline = Date.now() + 120_000;
  progress("waiting-for-real-qualification-start", {startsAtMs: mainStartsAt});
  while (view.serverNowMs < mainStartsAt) {
    assert.ok(Date.now() < pollDeadline, "Real qualification start not reached within bounded polling");
    await delay(Math.min(2000, Math.max(100, mainStartsAt - view.serverNowMs)));
    view = await call(players[0], "fetchCourseTournamentEvent", {eventId: names.mainEvent});
  }
  for (const [playerIndex, player] of players.entries()) {
    const download = await call(player, "recordCourseTournamentDownload",
      {operationId: id(`dl_${playerIndex}`), courseId: names.course});
    assert.equal(download.courseId, names.course);
    const httpTransport = {
      submit: (payload) => call(player, "submitCourseTournamentSession", payload),
      readView: (sessionId) => call(player, "fetchCourseTournamentSession", {sessionId}),
    };
    for (let roundNumber = 1; roundNumber <= 3; roundNumber++) {
      const startRequest = {kind: "START_QUALIFICATION", eventId: names.mainEvent,
        operationId: id(`start_${playerIndex}_${roundNumber}`),
        attemptId: id(`attempt_${playerIndex}_${roundNumber}`), roundNumber};
      const started = await call(player, "submitCourseTournamentOperation", startRequest);
      safe(started); assert.equal(started.session.kind, "QUALIFICATION");
      sessions.add(started.session.sessionId);
      if (playerIndex === 0 && roundNumber === 1) {
        assert.deepEqual(await call(player, "submitCourseTournamentOperation", startRequest), started,
          "START replay must return the original session");
        const reread = await call(player, "fetchCourseTournamentSession", {sessionId: started.session.sessionId});
        assert.deepEqual(reread.session, started.session, "Reconnect read must not restart or move clocks");
      }
      await answerWholeSession(httpTransport.submit, httpTransport.readView, player.uid,
        started.session.sessionId, playerIndex === 0 && roundNumber === 1 ? "probe" : "winner", 0);
    }
    progress("player-finished-three-attempts", {playerIndex});
  }
  const verifiedCount = (await raw.collection(VERIFIED_RUNS).where("eventId", "==", names.mainEvent).get()).size;
  assert.equal(verifiedCount, PLAYER_COUNT * 3);
  evidence.verifiedQualificationRuns = verifiedCount;
  passed("real-HTTP-qualification-three-EASY-attempts-per-player-all-settled");

  // --- Controlled tick to the fixed cutoff; explicit sweeps close qualification ---
  const cutoff = (await eventDoc(names.mainEvent)).qualification.endsAtMs;
  advance(cutoff + 1_000);
  fault = "ABORT_CLOSE"; // Crash-recovery probe: the close transaction aborts after staging.
  await assert.rejects(sweep(names.mainEvent), (error) => error.code === "HARNESS_ABORT");
  const abortedEvent = await eventDoc(names.mainEvent);
  assert.equal(abortedEvent.status, "QUALIFICATION"); assert.equal(abortedEvent.bracket, undefined);
  assert.notEqual(await read(`${QUALIFICATION_RANKINGS}/${names.mainEvent}`), undefined,
    "The aborted close must leave the already-published frozen ranking intact");
  assert.equal(await financialSnapshot(), financialAfterCreate, "The aborted close must not move money");
  let closed = false;
  for (let attempt = 0; attempt < 12 && !closed; attempt++) {
    const result = await sweep(names.mainEvent);
    closed = (await eventDoc(names.mainEvent)).status === "BRACKET";
    progress("own-controller-continuation", {attempt, status: result.status, transitions: result.transitions});
  }
  assert.equal(closed, true);
  const bracketEvent = await eventDoc(names.mainEvent);
  assert.equal(bracketEvent.bracket.status, "ACTIVE");
  // Seeding truth: entrants and pairings must BE the published frozen ranking's top order
  // (seed k = place k), not merely a conservation of the same uid set. Size-4 pairs 1v4 / 2v3.
  const financialAfterBracket = await financialSnapshot();
  assert.equal(financialAfterBracket, financialAfterCreate, "Closing qualification and seeding the bracket must not move money");
  const frozenRanking = await read(`${QUALIFICATION_RANKINGS}/${names.mainEvent}`);
  assert.ok(Array.isArray(frozenRanking.ranking) && frozenRanking.ranking.length >= 2, "The frozen ranking must be published before the bracket");
  const topUids = frozenRanking.ranking.slice(0, bracketEvent.bracket.size).map((row) => row.uid);
  assert.deepEqual(bracketEvent.bracket.entrants.map((row) => row.uid), topUids,
    "Bracket entrants must be the frozen ranking's best places in order");
  assert.deepEqual(bracketEvent.bracket.entrants.map((row) => row.seed), topUids.map((_, index) => index + 1),
    "Bracket seeds must follow the authoritative places");
  const seedUid = new Map(bracketEvent.bracket.entrants.map((row) => [row.seed, row.uid]));
  assert.deepEqual(bracketEvent.bracket.rounds[0].matches.map((match) => [seedUid.get(match.leftSeed), seedUid.get(match.rightSeed)]),
    [[topUids[0], topUids[3]], [topUids[1], topUids[2]]], "A size-4 bracket must pair 1v4 and 2v3");
  assert.deepEqual(bracketEvent.outcomes, [], "A fresh bracket carries no outcomes yet");
  assertEscrowIntact(bracketEvent);
  assert.notEqual(await read(`${QUALIFICATION_RANKINGS}/${names.mainEvent}`), undefined);
  passed("repeated-sweeps-close-qualification-once-abort-probe-proved-atomicity");

  // --- Bracket over the trusted clock: meetings, agreement, matches, awards ---
  async function nextPendingMatch() {
    const event = await eventDoc(names.mainEvent);
    if (event.status !== "BRACKET") return null;
    const played = new Set((event.outcomes || []).map((row) => row.matchId));
    for (const round of event.bracket.rounds) {
      for (const match of round.matches) {
        if (played.has(match.matchId)) continue;
        const pending = (event.progress?.pending || []).find((row) => row.matchId === match.matchId);
        if (pending) return {matchId: match.matchId, label: `s${match.stage}i${match.index}`,
          entrants: pending.entrants.map((row) => row.uid)};
      }
    }
    return null;
  }
  let matchCount = 0;
  let financial = financialAfterBracket;
  for (let next = await nextPendingMatch(); next; next = await nextPendingMatch()) {
    const winnerIndex = matchCount % 2; // Deterministic; both players answer near-perfectly.
    financial = await playMatch(names.mainEvent, next.matchId, next.entrants, winnerIndex,
      next.label, matchCount === 0 ? "probe" : "plain", financial);
    matchCount++;
    progress("match-resolved", {matchId: next.matchId, matches: matchCount});
  }
  const finishedEvent = await eventDoc(names.mainEvent);
  assert.equal(finishedEvent.status, "FINISHED", `Expected FINISHED, got ${finishedEvent.status}`);
  assertEscrowIntact(finishedEvent);
  assert.deepEqual(finishedEvent.escrow, {reservedBoxes: 12, paidBoxes: 10, returnedBoxes: 2, remainingBoxes: 0});
  assert.equal(matchCount, 3); // Two semis and the final; both semi losers place third.
  const settledMatchIds = finishedEvent.outcomes.map((row) => row.matchId);
  assert.equal(new Set(settledMatchIds).size, settledMatchIds.length,
    "Exactly one outcome per match despite the retried settlement");
  for (const placement of finishedEvent.progress.placements) {
    const row = PRIZES.rows.find((prize) => prize.place === placement.rank);
    const balance = (await read(`users/${placement.uid}`)).boxCount;
    assert.equal(balance, row ? row.amountBoxes : 0,
      `Placement ${JSON.stringify(placement)} must pay exactly its v1 row`);
  }
  const financialAfterFinish = await financialSnapshot();
  assert.equal(financialAfterFinish, financial, "The post-settlement baseline must carry through to FINISHED");
  const sweepAfterFinish = await sweep(names.mainEvent);
  assert.equal(sweepAfterFinish.done, true); assert.equal(sweepAfterFinish.status, "FINISHED");
  assert.equal(await financialSnapshot(), financialAfterFinish, "A repeated sweep must not pay twice");
  evidence.awards = {status: finishedEvent.status, escrow: finishedEvent.escrow,
    placements: finishedEvent.progress.placements};
  passed("one-run-lifecycle-FINISHED-with-conservation-and-repeat-safe-sweeps");

  // --- Degenerate sizes (plan §4): 0/1/2 participants, fully on the trusted clock ---
  // reconcileEvent signals an exhausted worker budget with reason "INVOCATION_TIME" (also
  // "SCAN_PENDING"/"CHECKPOINT_SAVED"/"WORKER_BUSY"); a wait is a recorded diagnostic, and the
  // last raw result is carried into the bounded-loop failure message.
  async function degenerate(eventId, cohort, playOut) {
    advance(nowMs() + 60_000);
    const authorBoxesBefore = (await read(`users/${author.uid}`)).boxCount;
    // The bank snapshot is owned by the real HTTP author — degenerate events reuse that snapshot,
    // so their CREATE (and its escrow) also runs as the author.
    const degCreated = await controller().operate(author.uid, {kind: "CREATE", operationId: id(`create_${eventId.slice(-6)}`),
      eventId, source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: nowMs() + DELAY, prizes: PRIZES, fundedBoxes: 12});
    assert.equal(degCreated.status, "CREATED"); safe(degCreated);
    assert.deepEqual(degCreated.event.escrow, {reservedBoxes: 12, remainingBoxes: 12, paidBoxes: 0, returnedBoxes: 0});
    const financialAfterDegCreate = await financialSnapshot();
    for (const uid of cohort) {
      await controller().recordDownload(uid, {operationId: id(`dldl_${uid.slice(-6)}`), courseId: names.course});
    }
    advance(nowMs() + DELAY + 1_000);
    for (const uid of cohort) {
      for (let roundNumber = 1; roundNumber <= 3; roundNumber++) {
        const attemptId = id(`att_${eventId.slice(-6)}_${uid.slice(-6)}_${roundNumber}`);
        sessions.add(qualificationSessionId(attemptId)); // The START transaction creates this doc.
        const started = await controller().operate(uid, {kind: "START_QUALIFICATION", eventId,
          operationId: id(`start_${eventId.slice(-6)}_${uid.slice(-6)}_${roundNumber}`),
          attemptId, roundNumber});
        safe(started); sessions.add(started.session.sessionId);
        await answerWholeSession((payload) => controller().session(uid, payload),
          (sid) => controller().readSession(uid, {sessionId: sid}), uid, started.session.sessionId, "winner", 1_000);
      }
    }
    const degCutoff = (await eventDoc(eventId)).qualification.endsAtMs;
    advance(degCutoff + 1_000);
    let degClosed = false; let lastDegResult = null;
    for (let attempt = 0; attempt < 12 && !degClosed; attempt++) {
      lastDegResult = await sweep(eventId);
      degClosed = ["BRACKET", "CANCELLED", "FINISHED"].includes((await eventDoc(eventId)).status);
      if (lastDegResult.reason === "INVOCATION_TIME") {
        progress("sweep-invocation-time-wait", {eventId, attempt, nextDueAtMs: lastDegResult.nextDueAtMs});
      }
    }
    assert.equal(degClosed, true, `Degenerate event ${eventId} did not close: ${JSON.stringify(lastDegResult)}`);
    if (!playOut) {
      const cancelled = await eventDoc(eventId);
      assert.equal(cancelled.status, "CANCELLED");
      assert.equal(cancelled.cancelReason, "FEWER_THAN_TWO_QUALIFIERS",
        `A ${cohort.length}-qualifier event must cancel with the contract reason`);
      assertEscrowIntact(cancelled);
      assert.deepEqual(cancelled.escrow, {reservedBoxes: 12, paidBoxes: 0, returnedBoxes: 12, remainingBoxes: 0},
        "A cancelled degenerate event must refund the full fund once (reserved,paid,returned,remaining)");
      assert.equal((await read(`users/${author.uid}`)).boxCount, authorBoxesBefore,
        "A cancelled degenerate event must restore the author's balance exactly");
      assert.notEqual(await financialSnapshot(), financialAfterDegCreate,
        "The refund must be a real ledger/funding movement, not a silent state revert");
      return cancelled;
    }
    return financialAfterDegCreate;
  }
  await degenerate(names.event0, [], false);
  passed("degenerate-0-cancels-with-full-refund");
  await degenerate(names.event1, [synthetic.d1], false);
  passed("degenerate-1-cancels-with-full-refund-fewer-than-two-qualifiers");

  // Size 2: the full final through agreement and play, champion + runner-up paid.
  advance(nowMs() + 60_000);
  const created2 = await controller().operate(author.uid, {kind: "CREATE", operationId: id("create_d2"),
    eventId: names.event2, source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: nowMs() + DELAY,
    prizes: PRIZES, fundedBoxes: 12});
  assert.equal(created2.status, "CREATED"); safe(created2);
  const financialAfterCreate2 = await financialSnapshot();
  for (const uid of [synthetic.d2a, synthetic.d2b]) {
    await controller().recordDownload(uid, {operationId: id(`dldl_${uid.slice(-6)}`), courseId: names.course});
  }
  advance(nowMs() + DELAY + 1_000);
  for (const uid of [synthetic.d2a, synthetic.d2b]) {
    for (let roundNumber = 1; roundNumber <= 3; roundNumber++) {
      const attemptId = id(`att_d2_${uid.slice(-6)}_${roundNumber}`);
      sessions.add(qualificationSessionId(attemptId)); // The START transaction creates this doc.
      const started = await controller().operate(uid, {kind: "START_QUALIFICATION", eventId: names.event2,
        operationId: id(`start_d2_${uid.slice(-6)}_${roundNumber}`), attemptId, roundNumber});
      safe(started); sessions.add(started.session.sessionId);
      await answerWholeSession((payload) => controller().session(uid, payload),
        (sid) => controller().readSession(uid, {sessionId: sid}), uid, started.session.sessionId, "winner", 1_000);
    }
  }
  const cutoff2 = (await eventDoc(names.event2)).qualification.endsAtMs;
  advance(cutoff2 + 1_000);
  let bracket2 = false;
  for (let attempt = 0; attempt < 12 && !bracket2; attempt++) {
    await sweep(names.event2);
    bracket2 = (await eventDoc(names.event2)).status === "BRACKET";
  }
  assert.equal(bracket2, true);
  const bracket2Event = await eventDoc(names.event2);
  assert.equal(bracket2Event.bracket.size, 2);
  // Size-2 seeding truth: the frozen ranking's top two, paired 1v2.
  const ranking2 = await read(`${QUALIFICATION_RANKINGS}/${names.event2}`);
  const top2 = ranking2.ranking.slice(0, 2).map((row) => row.uid);
  assert.deepEqual(bracket2Event.bracket.entrants.map((row) => row.uid), top2);
  const finalMatch = bracket2Event.bracket.rounds[0].matches[0];
  const finalPending = bracket2Event.progress.pending.find((row) => row.matchId === finalMatch.matchId);
  assert.ok(finalPending, "The size-2 final must be pending before play");
  const financialAfterFinal = await playMatch(names.event2, finalMatch.matchId,
    finalPending.entrants.map((row) => row.uid), 0, "d2final", "plain", financialAfterCreate2);
  const finished2 = await eventDoc(names.event2);
  assert.equal(finished2.status, "FINISHED");
  assertEscrowIntact(finished2);
  assert.deepEqual(finished2.escrow, {reservedBoxes: 12, paidBoxes: 8, returnedBoxes: 4, remainingBoxes: 0});
  assert.equal(await read(`users/${finished2.progress.placements[0].uid}`).then((user) => user.boxCount), 5);
  assert.equal(await read(`users/${finished2.progress.placements[1].uid}`).then((user) => user.boxCount), 3);
  assert.equal(financialAfterFinal, await financialSnapshot(), "No money may move after the final's settlement");
  const sweepAfterFinish2 = await sweep(names.event2);
  assert.equal(sweepAfterFinish2.done, true); assert.equal(sweepAfterFinish2.status, "FINISHED");
  passed("degenerate-2-plays-full-final-to-FINISHED-with-conservation");

  // --- Retro item 10: the queue receiver drives a real session end to end ---
  // One sink-driven qualification session over `tournament.SESSION_OP` (the app outbox's only
  // server entrance): CREATE mints the admission link (L4), the player STARTs with that token
  // and no download record, ANSWER and READY_ACK ride the single mutation sink, the delivered
  // window is left to expire, and two worker passes settle the attempt — TIMEOUT at the
  // question edge, then FORCED_CUTOFF at the captured window edge (D4: a worker close leaves
  // the next window OPEN until its sweep).
  advance(nowMs() + 60_000);
  const sinkCreate = await controller().operate(author.uid, {kind: "CREATE", operationId: id("create_sink"),
    eventId: names.event3, source: {...source, snapshotId: prepared.snapshotId}, startsAtMs: nowMs() + DELAY,
    prizes: PRIZES, fundedBoxes: 12});
  assert.equal(sinkCreate.status, "CREATED"); safe(sinkCreate);
  const linkToken = sinkCreate.admissionLink?.token;
  assert.ok(/^[a-f0-9]{48}$/.test(linkToken), "The CREATE receipt must carry the admission link token (L4)");
  assert.equal(JSON.stringify(sinkCreate.event).includes(linkToken), false,
    "The token must live in the organizer receipt only, never in the event view");
  // The sink player is a real account of this run; its balances are untouched by the phase
  // (event3 cancels with a full refund).
  const sinkPlayer = players[3];
  advance(nowMs() + DELAY + 1_000);
  const sinkAttempt = id("att_sink_1");
  sessions.add(qualificationSessionId(sinkAttempt)); // The START transaction creates this doc.
  const sinkStart = await controller().operate(sinkPlayer.uid, {kind: "START_QUALIFICATION", eventId: names.event3,
    operationId: id("start_sink_1"), attemptId: sinkAttempt, roundNumber: 1, linkToken});
  safe(sinkStart); assert.equal(sinkStart.session.kind, "QUALIFICATION");
  sessions.add(sinkStart.session.sessionId);
  const sinkSessionId = sinkStart.session.sessionId;
  // The sink is drained in-process (the callable entry point itself, `submitMutation.run`),
  // under the run's logical clock: every post-cutoff phase rides the injected clock, and the
  // mutation sink must judge answer timeliness on the SAME clock the session was seeded with.
  // The mounted functions module reuses this run's admin app (a proxy stands in for
  // initializeApp) and keeps the captured pinned clock permanently.
  const repoReq = createRequire("/repo/functions/index.js");
  const adminPath = repoReq.resolve("firebase-admin");
  const appProxy = new Proxy(admin, {get(target, prop) {
    if (prop === "initializeApp") return () => target.app();
    return target[prop];
  }});
  const adminStub = new Module(adminPath, null);
  adminStub.filename = adminPath; adminStub.loaded = true; adminStub.exports = appProxy;
  const savedAdminEntry = require.cache[adminPath];
  const pinnedNow = Date.now;
  Date.now = () => logical;
  let sinkFunctions = null;
  try { sinkFunctions = repoReq("/repo/functions/index.js"); } finally { Date.now = pinnedNow; require.cache[adminPath] = savedAdminEntry; }
  // The sink envelope is exactly what the client outbox sends: {mutationId, operation, payload}.
  const sinkSubmit = async (payload) => {
    const delivered = await sinkFunctions.submitMutation.run({auth: {uid: sinkPlayer.uid},
      data: {mutationId: id(`mut_${payload.operationId}`), operation: "tournament.SESSION_OP",
        payload: JSON.stringify(payload)}});
    assert.equal(delivered.replayed, false, "A fresh sink operation must not replay");
    safe(delivered.result);
    return delivered.result;
  };
  const sinkView = await controller().readSession(sinkPlayer.uid, {sessionId: sinkSessionId});
  safe(sinkView);
  assert.ok(sinkView.currentQuestion, "The seeded question must be served to its owner");
  const sinkAnswer = await sinkSubmit({kind: "ANSWER", sessionId: sinkSessionId, operationId: id("sink_ans_0"),
    questionIndex: 0, questionId: sinkView.currentQuestion.questionId, answerPayload: CORRECT});
  assert.equal(sinkAnswer.status, "ANSWER_ACCEPTED");
  // L1 server half: the operation's window rides NESTED in the session and this client-driven
  // close IS the delivery.
  const deliveredWindow = sinkAnswer.session.window;
  assert.ok(deliveredWindow, "A client-driven close must deliver the next window");
  assert.equal(deliveredWindow.state, "DELIVERED");
  assert.equal(deliveredWindow.nextQuestionIndex, 1);
  const sinkAck = await sinkSubmit({kind: "READY_ACK", sessionId: sinkSessionId, operationId: id("sink_ack_0"),
    questionIndex: deliveredWindow.nextQuestionIndex, windowId: deliveredWindow.windowId});
  assert.equal(sinkAck.status, "READY_ACK");
  assert.deepEqual({questionIndex: sinkAck.readyAck.questionIndex, windowId: sinkAck.readyAck.windowId},
    {questionIndex: 1, windowId: deliveredWindow.windowId}, "The ready-ACK receipt must name the served triple");
  assert.ok(sinkAck.readyAck.ackedAtMs <= sinkAck.readyAck.answerDeadlineMs);
  passed("sink-driven-READY_ACK-over-tournament-SESSION_OP-with-delivered-window");
  // Worker pass 1: the delivered q1 window expired unanswered — TIMEOUT at the question edge;
  // the worker close opens q2's window and leaves it OPEN (the sweep clock stays inside the
  // envelope so the cutoff flush cannot preempt the window path).
  advance(Math.max(deliveredWindow.expiresAtMs, sinkView.serverNowMs) + 1_000);
  assert.ok(nowMs() < sinkStart.session.endsAtMs, "The window pass must run inside the envelope");
  const windowPass = await advanceTournamentSession({db, sessionId: sinkSessionId,
    operationId: id("wadv_window"), now: nowMs()});
  assert.equal(windowPass.status, "QUESTION_TIMED_OUT");
  const openWindow = windowPass.session.window;
  assert.ok(openWindow, "A worker close must open the next window");
  assert.equal(openWindow.state, "OPEN", "A worker close leaves the next window OPEN (D4)");
  // Worker pass 2: the worker window left to expire — FORCED_CUTOFF at the captured edge.
  advance(openWindow.expiresAtMs + 1_000);
  const cutoffPass = await advanceTournamentSession({db, sessionId: sinkSessionId,
    operationId: id("wadv_cutoff"), now: nowMs()});
  assert.equal(cutoffPass.status, "COMPLETED");
  assert.equal(cutoffPass.session.status, "SETTLED");
  const sinkRun = await read(`${VERIFIED_RUNS}/${sinkSessionId}`);
  assert.equal(sinkRun.status, "SETTLED");
  assert.equal(sinkRun.percentScore, 5, "One correct of twenty: captured-edge cutoffs score zero");
  const cutoffSlot = await read(`${P.sessions}/${sinkSessionId}/answers/02`);
  assert.equal(cutoffSlot.kind, "FORCED_CUTOFF");
  assert.equal(cutoffSlot.closedAtMs, openWindow.expiresAtMs,
    "The cutoff rides the captured window edge, never the observation moment");
  passed("worker-window-left-to-expire-FORCED_CUTOFF-at-captured-edge");
  // The half-played event still closes honestly: one qualifier cancels with a full refund.
  const sinkCutoff = (await eventDoc(names.event3)).qualification.endsAtMs;
  advance(sinkCutoff + 1_000);
  let sinkClosed = false; let lastSinkResult = null;
  for (let attempt = 0; attempt < 12 && !sinkClosed; attempt++) {
    lastSinkResult = await sweep(names.event3);
    sinkClosed = (await eventDoc(names.event3)).status !== "QUALIFICATION";
  }
  assert.equal(sinkClosed, true, `The sink event did not close: ${JSON.stringify(lastSinkResult)}`);
  const sinkTerminal = await eventDoc(names.event3);
  assert.equal(sinkTerminal.status, "CANCELLED");
  assert.equal(sinkTerminal.cancelReason, "FEWER_THAN_TWO_QUALIFIERS");
  assertEscrowIntact(sinkTerminal);
  assert.deepEqual(sinkTerminal.escrow, {reservedBoxes: 12, paidBoxes: 0, returnedBoxes: 12, remainingBoxes: 0},
    "The cancelled sink event must refund the full fund once");
  passed("sink-event-cancels-with-full-refund-after-sink-driven-session");

  // --- Terminal state across the whole run ---
  for (const [index, eventId] of events.entries()) {
    const terminal = await eventDoc(eventId);
    assert.ok(["FINISHED", "CANCELLED"].includes(terminal.status), `${eventId} is ${terminal.status}`);
    assertEscrowIntact(terminal);
    assert.equal(terminal.escrow.remainingBoxes, 0, `${eventId} must settle to remaining = 0`);
    evidence[`event${index}`] = {eventId, status: terminal.status, escrow: terminal.escrow};
  }
  const authorBalance = await read(`users/${author.uid}`).then((user) => user.boxCount);
  assert.equal(authorBalance, 200 - 10 - 8,
    "Author ends at deposit minus paid prizes; degenerate cancels and surpluses refunded in full");
  const d0Balance = await read(`users/${synthetic.d0}`).then((user) => user.boxCount);
  assert.equal(d0Balance, 0, "The stray-credit canary (never an entrant, organizer or funder) must keep its exact zero balance");
  passed("terminal-remaining-zero-and-balances-conserved-across-the-whole-run");

  const resource = {elapsedMs: Date.now() - wallStarted, heapUsedBytes: process.memoryUsage().heapUsed,
    rssBytes: process.memoryUsage().rss, peakRssKiB: process.resourceUsage().maxRSS};
  console.log(JSON.stringify({status: "PASS", checks, bounds: {...bounds, resource}, evidence}, null, 2));
}

main().catch((error) => {
  console.error(JSON.stringify({status: "FAIL", stage, code: error.code, message: error.message,
    checksCompleted: checks, bounds, evidence}, null, 2));
  process.exitCode = 1;
}).finally(() => app.delete());
