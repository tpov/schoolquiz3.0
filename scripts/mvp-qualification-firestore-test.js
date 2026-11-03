"use strict";

// Trusted Admin-SDK harness, NOT HTTP/Auth/scheduler coverage. Default is a write-free plan.
const assert = require("node:assert/strict");
const {randomUUID, createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const args = process.argv.slice(2);
assert.ok(args.every(a => ["--case=small", "--case=large", "--execute"].includes(a)), "Unknown harness option");
const selected = args.includes("--case=large") ? "large" : "small";
assert.ok(!(args.includes("--case=small") && args.includes("--case=large")));
const count = selected === "small" ? 2 : 167;
const plan = {kind: "TRUSTED_FIRESTORE_SDK_NOT_HTTP", case: selected, playerCount: count, rounds: 3,
  attempts: count * 3, createOnlySeedDocuments: count + 89, approximateAttemptDerivedDocuments: count * 3 * 26,
  maxWrittenPaths: selected === "small" ? 400 : 14000, authAccounts: 0, cleanup: false,
  privateProject: "demo-schoolquiz-mvp", privateHost: "schoolquiz-mvp-firebase:8080",
  note: "Large requires separate approval after small PASS. No execution without --execute and exact QS_APPROVED_CASE."};
if (!args.includes("--execute")) {
  console.log(JSON.stringify({status: "PLAN_ONLY_NO_CONNECTION", plan}, null, 2));
} else {
  assert.equal(process.env.QS_APPROVED_CASE, selected, "Separate case approval required");
  assert.equal(process.env.GCLOUD_PROJECT, plan.privateProject);
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, plan.privateHost);
  assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Never use production credentials");
  assert.equal(process.env.FIREBASE_CONFIG, undefined, "Do not inherit another Firebase application");
  execute().catch(error => { console.error(JSON.stringify({status: "HARNESS_UNCAUGHT", code: error.code, message: error.message})); process.exitCode = 1; });
}

async function execute() {
  const harnessSha256 = createHash("sha256").update(readFileSync(__filename)).digest("hex");
  const hashes = Object.fromEntries(Object.entries({
    // Re-pin 2026-10-18 (story 5.12 rig run): course-tournament-controller.js changed after the
    // 5.4 pin (stories 5.5-5.8 landed worker CLOSE/ADVANCE_MATCH + settlement in the controller);
    // HEAD at re-pin = the epic baseline d9cd05b0, worktree clean. The pin guards that the
    // mounted rig sources are the reviewed ones.
    "course-tournament-controller.js": "ed9692fc908f5fae507e050d1b08595ef40221f43ffc242a0e2d2b2f5ca0992b",
    "tournament-qualification-scan.js": "683b1c007a3426f2e346ef839c77738dd53f40ef61dcced6095e47ed6ce89cb8",
    "tournament-qualification.js": "778d3e67174f2a09fece7ce587694747772d9abc69287a97886819b7758b9adb",
    "tournament-ranking.js": "0ca1ad03006cadd95c0a6caa914a1f74a2ae762715d3cb2d04aec3f5243ace78",
  }).map(([file, expected]) => {
    const actual = createHash("sha256").update(readFileSync(`/repo/functions/${file}`)).digest("hex");
    assert.equal(actual, expected, `Unreviewed mounted source: ${file}`); return [file, actual];
  }));
  const admin = require("/repo/functions/node_modules/firebase-admin");
  const {createCourseTournamentController} = require("/repo/functions/course-tournament-controller");
  const {TOURNAMENT_PATHS: P, qualificationSessionId} = require("/repo/functions/tournament-service");
  const {SOURCE_BANKS, PREPARATIONS} = require("/repo/functions/tournament-source-bank");
  const {SESSION_OPERATIONS, VERIFIED_RUNS} = require("/repo/functions/tournament-session");
  const {QUALIFICATION_RANKINGS} = require("/repo/functions/tournament-qualification-freeze");
  const {advanceQualificationScan, readCompleteQualificationManifest, calculateAndPublishQualificationRanking, scanPath} = require("/repo/functions/tournament-qualification-scan");
  const {buildQualificationRankingSnapshot} = require("/repo/functions/tournament-qualification");
  const {tournamentDigest: digest, canonicalTournamentJson: canonical} = require("/repo/functions/tournament-bank");
  const {LEDGER_PATH} = require("/repo/functions/currency-ledger");
  const app = admin.initializeApp({projectId: plan.privateProject}, `qualification-harness-${randomUUID()}`);
  const raw = app.firestore();
  const runId = `qs_${randomUUID().replaceAll("-", "")}`; const id = suffix => `${runId}_${suffix}`;
  const names = Object.fromEntries(["catalog", "course", "section", "theme", "lesson", "submission", "event", "author", "prepare", "create"].map(n => [n, id(n)]));
  const players = Array.from({length: count}, (_, i) => id(`p${String(i).padStart(3, "0")}`));
  const owners = new Set([names.author, ...players]);
  const eventPath = `${P.events}/${names.event}`; const rankPath = `${QUALIFICATION_RANKINGS}/${names.event}`;
  const admissions = players.flatMap((uid, i) => [1, 2, 3].map(roundNumber => {
    const attemptId = id(`attempt_${i}_${roundNumber}`);
    return {uid, roundNumber, attemptId, sessionId: qualificationSessionId(attemptId),
      entryId: digest([uid, roundNumber]), operationId: id(`start_${i}_${roundNumber}`)};
  }));
  const sessions = new Set(admissions.map(row => row.sessionId));
  const operationPaths = new Set([names.create, ...admissions.map(row => row.operationId)].map(op => `${P.operations}/${digest(op)}`));
  const sessionOperationPaths = new Set();
  const reserveId = JSON.stringify(["tournament", names.event, "reserve"]);
  const fundingPath = `${P.funding}/${digest(reserveId)}`;
  const preparationPath = `${PREPARATIONS}/${digest(names.prepare)}`;
  const sourceBankPath = `${SOURCE_BANKS}/bank_${digest(names.prepare)}`;
  const writtenPaths = new Set(); const committedPaths = new Set(); const queryMetadata = new WeakMap();
  const trace = {transactions: 0, queryReads: 0, pendingQueries: 0, pendingCursorQueries: 0, injectedAborts: [], scanCalls: 0};
  const wallStarted = Date.now(); const runtimeBudget = selected === "small" ? 300_000 : 1_200_000;
  let logicalBase = wallStarted; let wallBase = wallStarted;
  const now = () => logicalBase + Date.now() - wallBase;
  const moveOwnClock = value => { logicalBase = value; wallBase = Date.now(); };
  let phase = "target-preflight"; let fault = null; let scanRoot = null;
  const checks = [];
  function progress(stage, extra = {}) { phase = stage; console.log(JSON.stringify({stage, runId, ...extra})); }
  function passed(stage, extra) { checks.push(stage); progress(stage, extra); }
  function budget() { assert.ok(Date.now() - wallStarted < runtimeBudget, "Harness wall budget exhausted; retain own fixture and report pending"); }
  function scopeWrite(path, value) {
    const parts = path.split("/");
    const ownEvent = path === eventPath || path.startsWith(`${eventPath}/`) || path.startsWith(`${P.privateForms}/${names.event}/`);
    const ownSession = [P.sessions, VERIFIED_RUNS].includes(parts[0]) && sessions.has(parts[1]);
    const ownAccount = ["users", "premium_accounts"].includes(parts[0]) && owners.has(parts[1]);
    const ownLedger = path.startsWith(`${LEDGER_PATH}/`) && owners.has(value?.uid) && path === `${LEDGER_PATH}/${value.id}` && value.id.startsWith(`${value.uid}_`);
    // Story 5.3 CREATE mints the competition-link digest document (course_tournament_links/{eventId});
    // this scope allowlist predates that story — the event's own link document is fixture-owned.
    const exact = [rankPath, preparationPath, sourceBankPath, fundingPath, `${P.links}/${names.event}`].includes(path) || operationPaths.has(path) || sessionOperationPaths.has(path);
    assert.ok(ownEvent || ownSession || ownAccount || ownLedger || exact, `Out-of-fixture write refused: ${path}`);
    if ([P.operations, SESSION_OPERATIONS].includes(parts[0])) assert.ok(owners.has(value.uid), "Operation owner must be synthetic");
    if (value?.eventId !== undefined) assert.equal(value.eventId, names.event, "Cross-event record refused");
    writtenPaths.add(path); assert.ok(writtenPaths.size <= plan.maxWrittenPaths, "Harness write-footprint budget exceeded");
  }
  function observedQuery(query, metadata) {
    const result = {
      where: (...a) => observedQuery(query.where(...a), {...metadata, filters: [...metadata.filters, a]}),
      orderBy: (...a) => observedQuery(query.orderBy(...a), {...metadata, order: a}),
      startAfter: (...a) => observedQuery(query.startAfter(...a), {...metadata, cursor: a}),
      limit: n => observedQuery(query.limit(n), {...metadata, limit: n}),
      doc: key => query.doc(key),
      get: async () => { recordQuery(metadata); return query.get(); },
    };
    queryMetadata.set(result, {query, metadata}); return result;
  }
  function recordQuery(meta) {
    trace.queryReads++;
    if (scanRoot && meta.path === `${scanRoot}/rows`) {
      assert.deepEqual(meta.filters, [["status", "==", "PENDING"]]); assert.deepEqual(meta.order, ["__name__"]);
      assert.ok(Number.isInteger(meta.limit) && meta.limit > 0 && meta.limit <= 50);
      trace.pendingQueries++; if (meta.cursor) trace.pendingCursorQueries++;
    }
  }
  const db = {
    doc: path => raw.doc(path),
    collection: path => observedQuery(raw.collection(path), {path, filters: []}),
    runTransaction: body => raw.runTransaction(async transaction => {
      budget(); trace.transactions++; const writes = [];
      const tx = {
        get: async ref => { const observed = queryMetadata.get(ref); if (observed) recordQuery(observed.metadata); return transaction.get(observed?.query || ref); },
        getAll: (...refs) => transaction.getAll(...refs),
        create: (ref, value) => { scopeWrite(ref.path, value); writes.push({path: ref.path, value}); transaction.create(ref, value); },
        set: (ref, value, options) => { scopeWrite(ref.path, value); writes.push({path: ref.path, value}); transaction.set(ref, value, options); },
      };
      const result = await body(tx);
      const matched = writes.some(({path, value}) => fault === "CHUNK" ? path.includes("/chunks/") :
        fault === "PROOF" ? path.includes("/rows/") && value.status === "VERIFIED" : fault === "PUBLISH" && path === rankPath);
      if (fault && matched) { const kind = fault; fault = null; trace.injectedAborts.push(kind); const error = new Error(`Injected ${kind} callback abort`); error.code = "HARNESS_ABORT"; throw error; }
      // This tracks candidate paths, not a claim that a retry callback already committed them.
      return {result, paths: writes.map(row => row.path)};
    }).then(({result, paths}) => { paths.forEach(path => committedPaths.add(path)); return result; }),
  };
  async function read(path) { return (await raw.doc(path).get()).data(); }
  async function collectionRows(path, limit) {
    const result = await raw.collection(path).orderBy("__name__").limit(limit + 1).get();
    assert.ok(result.size <= limit, "Unexpected extra fixture documents"); return result.docs;
  }
  function controller() {
    return createCourseTournamentController({db, nowMs: now, isCurator: async () => false,
      loadLessonContext: async (tx, lessonId) => {
        assert.equal(lessonId, names.lesson);
        const paths = [`lessons/${lessonId}`, `themes/${names.theme}`, `sections/${names.section}`, `quests/${names.course}`,
          `catalogs/${names.catalog}`, `admin/review/lessons/${lessonId}`,
          ...["TESTING", "LOGIC"].map(kind => `admin/review/lessons/${lessonId}/reviews/${id(kind)}`)];
        const values = await tx.getAll(...paths.map(path => db.doc(path))); assert.ok(values.every(doc => doc.exists));
        const [lesson, theme, section, course, catalog, review, testing, logic] = values.map(doc => doc.data());
        assert.equal(lesson.themeId, names.theme); assert.equal(theme.sectionId, names.section); assert.equal(section.questId, names.course);
        assert.equal(course.catalogId, names.catalog); assert.equal(course.authorUid, names.author); assert.equal(catalog.questType, "COURSE");
        assert.equal(review.submissionId, names.submission); assert.equal(review.status, "PUBLISHED");
        assert.equal(lesson.reviewSubmissionId, review.submissionId); assert.equal(review.ownerUid, names.author); assert.equal(review.questId, names.course);
        for (const [record, kind] of [[testing, "TESTING"], [logic, "LOGIC"]]) {
          assert.equal(record.kind, kind); assert.equal(record.acceptedByServer, true); assert.equal(record.submissionId, review.submissionId);
          assert.equal(record.score, 3); assert.notEqual(record.reviewerUid, names.author);
        }
        assert.notEqual(testing.reviewerUid, logic.reviewerUid);
        return {lessonId, authorUid: names.author, catalogId: names.catalog, questId: names.course, sectionId: names.section,
          themeId: names.theme, questType: "COURSE", published: true, reviewApproved: true, contentVersion: lesson.version};
      }});
  }
  function assertStatusSafe(result) {
    for (const key of ["ranking", "forms", "freezeId", "manifestDigest", "verifiedRuns", "uid", "sessionId"]) assert.equal(Object.hasOwn(result, key), false);
  }
  const scan = () => advanceQualificationScan({db, eventId: names.event, now: now(), pageSize: 2, maxPages: 1, nowMs: now, deadlineAtMs: now() + 30_000});
  const calculate = () => calculateAndPublishQualificationRanking({db, eventId: names.event, now: now(), nowMs: now,
    maxVerifiedRuns: 501, deadlineAtMs: now() + 120_000, minRemainingBeforeSolveMs: 60_000});
  async function expectedAbort(kind, operation) {
    fault = kind; await assert.rejects(operation, error => error.code === "HARNESS_ABORT"); assert.equal(fault, null);
  }
  async function financialSnapshot() {
    const paths = [...writtenPaths].filter(path => path.startsWith("users/") || path.startsWith("premium_accounts/") ||
      path.startsWith(`${LEDGER_PATH}/`) || path.startsWith(`${P.funding}/`)).sort();
    const rows = [];
    for (let i = 0; i < paths.length; i += 100) {
      const chunk = paths.slice(i, i + 100); const docs = await raw.getAll(...chunk.map(path => raw.doc(path)));
      docs.forEach((doc, index) => rows.push([chunk[index], doc.exists ? doc.data() : null]));
    }
    return digest(rows);
  }
  try {
    const hub = await fetch("http://schoolquiz-mvp-firebase:4400/emulators", {signal: AbortSignal.timeout(15000)});
    assert.equal(hub.status, 200); const services = await hub.json(); assert.equal(services.firestore.port, 8080);
    progress("verified-private-demo-before-writes", {case: selected, plan, harnessSha256, mountedSourceSha256: hashes});
    const seedTime = now();
    const wallet = {boxCount: 0, nolics: 0, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
      lifePointsUpdatedAtMs: seedTime, plasmaPointsUpdatedAtMs: seedTime, testFixture: runId};
    const seeds = [...owners].map(uid => [`users/${uid}`, {...wallet, uid, boxCount: uid === names.author ? 100 : 0}]);
    seeds.push([`catalogs/${names.catalog}`, {questType: "COURSE", version: 1, testFixture: runId}],
      [`quests/${names.course}`, {catalogId: names.catalog, authorUid: names.author, version: 1, visibleOn: ["archive"], archived: true, testFixture: runId}],
      [`sections/${names.section}`, {questId: names.course, version: 1, testFixture: runId}],
      [`themes/${names.theme}`, {sectionId: names.section, version: 1, testFixture: runId}],
      [`lessons/${names.lesson}`, {themeId: names.theme, version: 1, reviewSubmissionId: names.submission, reviewApprovedAtMs: seedTime - 1, testFixture: runId}],
      [`admin/review/lessons/${names.lesson}`, {ownerUid: names.author, questId: names.course, submissionId: names.submission, status: "PUBLISHED", testFixture: runId}],
      ...["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/${names.lesson}/reviews/${id(kind)}`, {kind, reviewerUid: id(`reviewer_${kind}`),
        acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: names.submission, testFixture: runId}]));
    for (let i = 0; i < 80; i++) {
      const questionId = id(`q${String(i).padStart(3, "0")}`); const difficulty = i < 60 ? "EASY" : "HARD";
      seeds.push([`questions/${questionId}`, {lessonId: names.lesson, version: 1, difficulty, language: "en", testFixture: runId,
        payload: JSON.stringify({id: questionId, type: "SingleChoice", difficulty, text: "Synthetic question: select A.",
          options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"})}]);
    }
    assert.equal(seeds.length, plan.createOnlySeedDocuments);
    const preflightPaths = [...seeds.map(([path]) => path), eventPath, rankPath, preparationPath, sourceBankPath, fundingPath,
      ...operationPaths, ...admissions.flatMap(a => [`${P.sessions}/${a.sessionId}`, `${VERIFIED_RUNS}/${a.sessionId}`])];
    for (let i = 0; i < preflightPaths.length; i += 100) assert.ok((await raw.getAll(...preflightPaths.slice(i, i + 100).map(path => raw.doc(path)))).every(doc => !doc.exists), "Fresh namespace collision; refuse all seeding");
    const batch = raw.batch();
    for (const [path, value] of seeds) { assert.ok(path.includes(runId)); assert.equal(value.testFixture, runId); batch.create(raw.doc(path), value); }
    await batch.commit(); seeds.forEach(([path]) => { writtenPaths.add(path); committedPaths.add(path); });
    passed("create-only-owned-seed", {documents: seeds.length});

    const source = {kind: "COURSE", id: names.course}; const prepareRequest = {requestId: names.prepare, source};
    const prepared = await controller().prepare(names.author, prepareRequest); assert.deepEqual(prepared.counts, {easy: 60, hard: 20, total: 80});
    const startsAtMs = now() + 60_000;
    const createRequest = {kind: "CREATE", operationId: names.create, eventId: names.event, source: {...source, snapshotId: prepared.snapshotId},
      startsAtMs, fundedBoxes: 12, prizes: {listVersion: 1, rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3},
        {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]}};
    const created = await controller().operate(names.author, createRequest); assert.equal(created.status, "CREATED");
    assert.equal((await read(`users/${names.author}`)).boxCount, 88);
    const financialBefore = await financialSnapshot();
    assert.deepEqual(await controller().operate(names.author, createRequest), created); assert.equal(await financialSnapshot(), financialBefore);
    moveOwnClock(startsAtMs);
    // Story 5.3 admission gate: every cohort member needs a server-attested download record
    // for the event's course before its START can pass planAdmission (Admin writes, rules stay denied).
    for (const a of admissions) {
      const ref = db.collection(P.downloads).doc(digest([a.uid, names.course]));
      const stored = (await ref.get()).exists ? (await ref.get()).data() : null;
      if (!stored) await ref.set({schemaVersion: 1, uid: a.uid, courseId: names.course, downloadedAtMs: startsAtMs});
    }
    let firstStarted;
    for (let i = 0; i < admissions.length; i++) {
      budget(); const a = admissions[i]; const result = await controller().operate(a.uid,
        {kind: "START_QUALIFICATION", operationId: a.operationId, eventId: names.event, roundNumber: a.roundNumber, attemptId: a.attemptId});
      assert.equal(result.session.sessionId, a.sessionId); if (!i) firstStarted = result;
      if ((i + 1) % 100 === 0) progress("admitting-own-sessions", {admitted: i + 1});
    }
    const event = await read(eventPath); const cutoff = event.qualification.endsAtMs;
    const freezeId = `freeze_${digest([names.event, cutoff, digest(event.qualificationForms.map(({formId, fingerprint}) => ({formId, fingerprint})))])}`;
    scanRoot = scanPath(names.event, freezeId);
    admissions.forEach(a => sessionOperationPaths.add(`${SESSION_OPERATIONS}/${digest(`freeze_advance_${digest([freezeId, a.sessionId])}`)}`));
    operationPaths.add(`${P.operations}/${digest(`ctw_close_${digest([names.event, "CLOSE_QUALIFICATION", cutoff])}`)}`);
    moveOwnClock(cutoff); passed("all-own-admissions-created-before-own-cutoff", {admissions: admissions.length});

    if (selected === "small") {
      await expectedAbort("CHUNK", scan);
      assert.equal((await read(scanRoot)).admittedCount, 0); assert.equal((await collectionRows(`${scanRoot}/chunks`, 3)).length, 0);
      assert.equal((await collectionRows(`${scanRoot}/rows`, 6)).length, 0);
      passed("actual-sdk-chunk-abort-is-atomic");
      await expectedAbort("PROOF", scan);
      const pending = db.collection(`${scanRoot}/rows`).where("status", "==", "PENDING").orderBy("__name__");
      const first = await pending.limit(1).get(); const second = await pending.startAfter(first.docs[0].id).limit(1).get();
      assert.equal(first.size, 1); assert.equal(second.size, 1); assert.ok(first.docs[0].id < second.docs[0].id);
      assert.equal((await read(scanRoot)).verifiedCount, 0); assert.equal((await read(scanRoot)).pendingCount, 2);
      const runs = await raw.collection(VERIFIED_RUNS).where("eventId", "==", names.event).limit(7).get(); assert.equal(runs.size, 1);
      passed("actual-sdk-proof-abort-preserves-settled-session-and-pending-query-cursor");
      let complete = false;
      for (let i = 0; i < 40 && !complete; i++) { budget(); const result = await scan(); trace.scanCalls++; complete = result.scanComplete; assert.equal(await read(rankPath), undefined); }
      assert.equal(complete, true); assert.equal((await read(scanRoot)).verifiedCount, 6);
      const manifest = await readCompleteQualificationManifest({db, eventId: names.event, now: now(), nowMs: now, maxVerifiedRuns: 501, deadlineAtMs: now() + 120_000});
      assert.equal(manifest.ready, true); assert.equal(manifest.verifiedRuns.length, 6);
      await expectedAbort("PUBLISH", calculate); assert.equal(await read(rankPath), undefined); assert.equal((await read(scanRoot)).status, "SCAN_COMPLETE");
      assert.equal((await read(eventPath)).bracket, undefined); assert.equal(await financialSnapshot(), financialBefore);
      passed("actual-sdk-publication-abort-leaves-no-partial-ranking-or-bracket");
    }
    let closed = false;
    for (let i = 0; i < 80 && !closed; i++) {
      budget(); const result = await controller().reconcileEvent(names.event); trace.scanCalls++; assertStatusSafe(result);
      const stored = await read(eventPath); const ranking = await read(rankPath); const head = await read(scanRoot);
      if (!ranking) { assert.equal(stored.status, "QUALIFICATION"); assert.equal(stored.bracket, undefined); }
      else { assert.equal(ranking.verifiedRunCount, admissions.length); assert.equal(head.verifiedCount, admissions.length); }
      closed = stored.status === "BRACKET";
      progress("own-controller-continuation", {call: trace.scanCalls, status: result.status, admitted: head.admittedCount, verified: head.verifiedCount, rankingPublished: !!ranking, bracket: closed});
      if (result.reason === "RESOURCE_ENVELOPE") throw Error("Approved fixture unexpectedly exceeded worker calculation envelope");
    }
    assert.equal(closed, true);
    const entries = await collectionRows(`${eventPath}/entries`, admissions.length);
    assert.deepEqual(entries.map(doc => doc.id).sort(), admissions.map(a => a.entryId).sort());
    const rows = await collectionRows(`${scanRoot}/rows`, admissions.length); assert.ok(rows.every(doc => doc.data().status === "VERIFIED"));
    const verified = await raw.collection(VERIFIED_RUNS).where("eventId", "==", names.event).limit(admissions.length + 1).get();
    assert.equal(verified.size, admissions.length); assert.deepEqual(verified.docs.map(doc => doc.id).sort(), [...sessions].sort());
    const expected = buildQualificationRankingSnapshot({eventId: names.event, forms: event.qualificationForms, endsAtMs: cutoff,
      snapshotId: freezeId, verifiedRuns: verified.docs.map(doc => doc.data()), completeThroughMs: cutoff, pendingSessionCount: 0});
    const published = await read(rankPath);
    // Since story 5.5 CLOSE publishes the frozen versioned calculation (schemaVersion 2). The LS
    // contract surfaces stay deep-equal to the rebuilt legacy snapshot (rows, counts, algorithm);
    // the input fingerprints belong to the versioned player-stream-chain basis — format-checked
    // here ONLY (regex). This rig does not pin their exact values: each rig's cohort is namespaced
    // to its own run, so no cross-rig pin is possible. The calculation rig pins its OWN event's
    // fingerprints — contract-digest and formsFingerprint equality plus an independent rebuild of
    // the player-stream chain over that run's frozen verified inputs.
    for (const key of ["ranking", "verifiedRunCount", "totalRanked", "algorithmVersion"]) assert.deepEqual(published[key], expected[key]);
    assert.equal(published.schemaVersion, 2);
    assert.match(published.verifiedRunsFingerprint, /^[a-f0-9]{64}$/); assert.match(published.formsFingerprint, /^[a-f0-9]{64}$/);
    assert.equal(expected.totalRanked, count); assert.equal(await financialSnapshot(), financialBefore);
    passed("all-admissions-retained-and-published-ranking-matches-unchanged-full-cohort-LS", {verifiedRuns: expected.verifiedRunCount, rankedPlayers: expected.totalRanked});
    assert.deepEqual(await controller().prepare(names.author, prepareRequest), prepared);
    assert.deepEqual(await controller().operate(names.author, createRequest), created);
    const first = admissions[0];
    assert.deepEqual(await controller().operate(first.uid, {kind: "START_QUALIFICATION", eventId: names.event,
      operationId: first.operationId, roundNumber: first.roundNumber, attemptId: first.attemptId}), firstStarted);
    assert.equal((await calculate()).replayed, true);
    assertStatusSafe(await controller().reconcileEvent(names.event));
    assert.deepEqual(await read(rankPath), published); assert.equal(await financialSnapshot(), financialBefore);
    assert.ok(trace.pendingQueries > 0); assert.ok(trace.pendingCursorQueries > 0);
    passed("same-operations-and-controller-replay-preserve-ranking-and-all-financial-documents");
    console.log(JSON.stringify({status: "PASS", kind: plan.kind, runId, names, case: selected, checks, trace,
      resource: {elapsedMs: Date.now() - wallStarted, heapUsedBytes: process.memoryUsage().heapUsed, rssBytes: process.memoryUsage().rss,
        peakRssKiB: process.resourceUsage().maxRSS}, createdSeedDocuments: seeds.length, distinctCommittedPaths: committedPaths.size,
      retainedFixture: true, financialDigest: financialBefore, rankingInputFingerprint: expected.verifiedRunsFingerprint, harnessSha256, mountedSourceSha256: hashes}, null, 2));
  } catch (error) {
    console.error(JSON.stringify({status: "FAIL", kind: plan.kind, runId, names, phase, code: error.code, message: error.message,
      checks, trace, distinctCommittedPaths: committedPaths.size, retainedFixture: true}, null, 2)); process.exitCode = 1;
  } finally { await app.delete(); }
}
