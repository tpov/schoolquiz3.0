"use strict";

// New synthetic ar_ fixtures only. No deletes, resets, deployments, external endpoints or tokens in logs.
const assert = require("node:assert/strict");
const {createHash, randomUUID} = require("node:crypto");
const fs = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const FIREBASE_HOST = "schoolquiz-mvp-firebase";
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${FIREBASE_HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${FIREBASE_HOST}:9099`);
const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
const {ticketDocumentId, cancelDocumentId, utcDayId, REASONS} = require("/repo/functions/arena-tickets");
const {redact, STATUS: REDACTION} = require("/repo/functions/question-redaction");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `ar_${randomUUID().replaceAll("-", "")}`;
const names = Object.fromEntries(["catalog", "quest", "section", "theme", "lesson", "submission"]
  .map((name) => [name, `${runId}_${name}`]));
const checks = [];
const evidence = {stateProofs: [], sourceFixtureProofs: [], createdFixturePaths: [], createdAuthUids: []};
const sha = (value) => createHash("sha256").update(value).digest("hex");
const sourceFiles = ["functions/index.js", "functions/arena-ticket-recovery.js", "functions/arena-tickets.js",
  "functions/arena-entry-runtime.js", "functions/arena-public-question.js", "functions/question-redaction.js",
  "functions/arena-policy.js", "functions/_question-fixtures.js", "firestore.indexes.json"];
const sourceHashes = () => Object.fromEntries(sourceFiles.map((path) => [path, sha(fs.readFileSync(`/repo/${path}`))]));
const sourceBefore = sourceHashes();
function done(name) { checks.push(name); console.log(JSON.stringify({check: name, status: "PASS"})); }

async function request(port, path, body, token) {
  assert.ok([9099, 5001].includes(port));
  const response = await fetch(`http://${FIREBASE_HOST}:${port}/${path}`, {method: "POST",
    headers: {"content-type": "application/json", ...(token ? {authorization: `Bearer ${token}`} : {})},
    body: JSON.stringify(body), signal: AbortSignal.timeout(60_000)});
  const json = await response.json();
  if (!response.ok || json.error) {
    const error = new Error(json.error?.message || `HTTP ${response.status}`);
    error.code = json.error?.status; error.reason = json.error?.details?.reason;
    error.httpStatus = response.status; throw error;
  }
  return json;
}
const call = (actor, method, data) => request(5001, `${PROJECT}/us-central1/${method}`, {data}, actor?.token)
  .then((value) => value.result);
async function rejected(operation, code, reason) {
  await assert.rejects(operation, (error) => error.code === code && (reason === undefined || error.reason === reason));
}
async function account() {
  const result = await request(9099, "identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo", {returnSecureToken: true});
  assert.equal(typeof result.localId, "string"); assert.equal(typeof result.idToken, "string");
  assert.ok(!evidence.createdAuthUids.includes(result.localId));
  evidence.createdAuthUids.push(result.localId);
  return {uid: result.localId, token: result.idToken};
}
function ownedFixturePath(path) {
  if (/^(users|profiles)\//.test(path)) return evidence.createdAuthUids.includes(path.split("/")[1]);
  return path.split("/").some((part) => part.startsWith(`${runId}_`));
}
async function createFixtures(rows) {
  const batch = db.batch();
  for (const [path, value] of rows) {
    assert.ok(ownedFixturePath(path), `Unowned fixture path: ${path}`);
    batch.create(db.doc(path), {...value, syntheticRecoveryRun: runId});
    evidence.createdFixturePaths.push(path);
  }
  await batch.commit(); // create preconditions reject any collision; no overwrites.
}
const quotaPath = (actor, dayId) => `arena_ticket_quotas/${sha(JSON.stringify([actor.uid, dayId]))}`;
async function ownedTicketPatch(actor, ticket, patch) {
  assert.equal(ticket.uid, actor.uid); assert.ok(ticket.requestId.startsWith(`${runId}_`));
  assert.equal(ticketDocumentId(actor.uid, ticket.requestId), ticket.ticketId);
  await db.runTransaction(async (tx) => {
    const ref = db.doc(`arena_entry_tickets/${ticket.ticketId}`); const snapshot = await tx.get(ref);
    assert.equal(snapshot.data().uid, actor.uid); assert.equal(snapshot.data().requestId, ticket.requestId);
    tx.update(ref, patch);
  });
}
async function ownerState(actor) {
  const plain = (snapshot) => ({path: snapshot.ref.path, exists: snapshot.exists,
    updateTime: snapshot.updateTime?.toDate().toISOString() || null,
    updateNanoseconds: snapshot.updateTime?.nanoseconds ?? null, data: snapshot.exists ? snapshot.data() : null});
  const [user, premium, quotas, tickets, ledger, lots, cancellations] = await Promise.all([
    db.doc(`users/${actor.uid}`).get(), db.doc(`premium_accounts/${actor.uid}`).get(),
    db.collection("arena_ticket_quotas").where("uid", "==", actor.uid).get(),
    db.collection("arena_entry_tickets").where("uid", "==", actor.uid).get(),
    db.collection("audit/currency_ledger/entries").where("uid", "==", actor.uid).get(),
    db.collection(`premium_accounts/${actor.uid}/lots`).get(),
    db.collection("arena_ticket_cancellations").where("uid", "==", actor.uid).get(),
  ]);
  return [plain(user), plain(premium), ...[quotas, tickets, ledger, lots, cancellations].flatMap((snapshot) => snapshot.docs.map(plain))]
    .sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
}
async function unchanged(actor, label, action) {
  const before = await ownerState(actor); const result = await action(); const after = await ownerState(actor);
  assert.deepEqual(after, before, `${label} changed owned money/quota/receipt documents`);
  evidence.stateProofs.push({label, uid: actor.uid, documentCount: before.length,
    beforeSha256: sha(JSON.stringify(before)), afterSha256: sha(JSON.stringify(after))});
  return result;
}
async function prepare(actor, key, difficulty = "HARD") {
  const result = await call(actor, "prepareArenaEntry", {requestId: `${runId}_${key}`, mode: "SELECTED",
    lessonId: names.lesson, difficulty, maxPriceNolics: 1});
  assert.equal(result.status, "ISSUED"); assert.equal(result.ticket.priceNolics, 1);
  assert.equal(result.ticket.uid, actor.uid); return result.ticket;
}
const get = (actor, ticket) => call(actor, "fetchArenaEntryTicket", {ticketId: ticket.ticketId});
const list = (actor, payload = {}) => call(actor, "listArenaEntryTickets", payload);
const cancel = (actor, ticket) => call(actor, "cancelArenaEntry", {ticketId: ticket.ticketId,
  requestId: `${runId}_cancel_${ticket.ticketId}`});

// Only exact documents created by createFixtures in this invocation may be altered here.
// The synthetic marker and path are reread transactionally; never patch shared/old fixtures.
async function patchFreshFixtures(rows) {
  for (const [path] of rows) {
    assert.ok(ownedFixturePath(path) && evidence.createdFixturePaths.includes(path));
    assert.ok(/^(questions|lessons|lesson_placements)\//.test(path));
  }
  await db.runTransaction(async tx => {
    const refs = rows.map(([path]) => db.doc(path)); const saved = await tx.getAll(...refs);
    for (const snapshot of saved) assert.equal(snapshot.data()?.syntheticRecoveryRun, runId);
    for (let i = 0; i < rows.length; i++) tx.update(refs[i], rows[i][1]);
  });
}
async function fixtureState(paths) {
  for (const path of paths) assert.ok(ownedFixturePath(path) && evidence.createdFixturePaths.includes(path));
  return (await db.getAll(...paths.map(path => db.doc(path)))).map(snapshot => ({path: snapshot.ref.path,
    exists: snapshot.exists, updateTime: snapshot.updateTime?.toDate().toISOString() || null,
    updateNanoseconds: snapshot.updateTime?.nanoseconds ?? null, data: snapshot.exists ? snapshot.data() : null}))
    .sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
}
async function unchangedFixtures(paths, label, action) {
  const before = await fixtureState(paths); const result = await action(); const after = await fixtureState(paths);
  assert.deepEqual(after, before, `${label} changed source fixture data or updateTime`);
  evidence.sourceFixtureProofs.push({label, paths, beforeSha256: sha(JSON.stringify(before)), afterSha256: sha(JSON.stringify(after))});
  return result;
}

async function questionBoundaryChecks(author, foreign) {
  const actor = await account(); const now = Date.now(); const dayId = utcDayId(now);
  const lessonId = `${runId}_boundary_lesson`; const submissionId = `${runId}_boundary_submission`;
  const hardIds = [0, 1].map(n => `${runId}_boundary_HARD_${n}`);
  const easyIds = [0, 1].map(n => `${runId}_boundary_EASY_${n}`);
  const splits = hardIds.map(questionId => redact(JSON.stringify({...SINGLE, difficulty: "HARD"}),
    "HARD", {questionId, random: () => 0}));
  for (const split of splits) assert.equal(split.status, REDACTION.REDACTED);
  const rows = [
    [`users/${actor.uid}`, {uid: actor.uid, boxCount: 0, nolics: 1000, pointsNolics: 1000,
      lifePoints: 1000, plasmaPoints: 300, lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now}],
    [`lessons/${lessonId}`, {themeId: names.theme, version: 1, publishedAtMs: now - 2000,
      reviewSubmissionId: submissionId, reviewApprovedAtMs: now - 1000}],
    [`admin/review/lessons/${lessonId}`, {ownerUid: author.uid, questId: names.quest, submissionId,
      status: "PUBLISHED", sourceLanguages: ["en"]}],
    ...["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/${lessonId}/reviews/${kind}`, {
      kind, reviewerUid: `${runId}_boundary_${kind}`, acceptedByServer: true, reviewerLevelAtSubmit: 100, score: 3, submissionId}]),
    ...easyIds.map(id => [`questions/${id}`, {lessonId, version: 1, difficulty: "EASY", language: "en",
      // Intentionally unchanged real SINGLE fixture: no synthetic embedded id injection.
      payload: JSON.stringify({...SINGLE, difficulty: "EASY"})}]),
    ...hardIds.map((id, index) => [`questions/${id}`, {lessonId, version: 1, difficulty: "HARD", language: "en",
      payload: splits[index].publicPayload}]),
    [`question_keys/${lessonId}`, {version: 1, lessonId, publicHalfRedacted: true,
      keys: hardIds.map((questionId, index) => ({questionId, key: splits[index].key}))}],
    [`lesson_placements/${lessonId}`, {lessonId, authorUid: author.uid, questType: "REGULAR", shelf: "arena",
      previousShelf: "review", contentVersion: 1, placementVersion: 1, updatedAtMs: now}],
  ];
  await createFixtures(rows);
  const quotaRef = db.doc(quotaPath(actor, dayId));
  await quotaRef.create({uid: actor.uid, dayId, randomIssued: 7, updatedAtMs: now, syntheticRecoveryRun: runId});
  evidence.createdFixturePaths.push(quotaRef.path);
  const issueRequest = (key, difficulty) => ({requestId: `${runId}_boundary_${key}`, mode: "SELECTED",
    lessonId, difficulty, maxPriceNolics: 1, expectedOwnerUid: actor.uid});
  const sourcePaths = rows.map(([path]) => path).filter(path => !path.startsWith("users/"));
  const beforeUser = (await db.doc(`users/${actor.uid}`).get()).data();
  const easy = await unchangedFixtures(sourcePaths, "runtime normalizes missing embedded id without rewriting source", async () => {
    const saved = await fixtureState(easyIds.map(id => `questions/${id}`));
    for (const row of saved) {
      assert.equal(Object.hasOwn(JSON.parse(row.data.payload), "id"), false);
      assert.equal(JSON.parse(row.data.payload).difficulty, "EASY");
    }
    const result = await call(actor, "prepareArenaEntry", issueRequest("easy", "EASY"));
    assert.equal(result.status, "ISSUED"); const ticket = result.ticket;
    assert.equal(ticket.uid, actor.uid); assert.equal(ticket.priceNolics, 1); assert.equal(ticket.status, "RESERVED");
    assert.deepEqual(ticket.questions.map(row => row.questionId).sort(), easyIds.slice().sort());
    for (const row of ticket.questions) {
      const content = JSON.parse(row.publicQuestion.payload);
      assert.equal(content.id, row.questionId); assert.equal(content.difficulty, "EASY");
      assert.equal(content.correctOptionId, SINGLE.correctOptionId); assert.equal(content.info, SINGLE.info);
    }
    const user = (await db.doc(`users/${actor.uid}`).get()).data();
    assert.equal(user.nolics, beforeUser.nolics - ticket.priceNolics);
    assert.equal(user.lifePoints, beforeUser.lifePoints - ticket.lifeCost);
    assert.equal(user.reservedNolics, ticket.priceNolics); assert.equal(user.reservedLifePoints, ticket.lifeCost);
    const stored = (await db.doc(`arena_entry_tickets/${ticket.ticketId}`).get()).data();
    assert.deepEqual(stored.questions.map(row => row.publicQuestion), ticket.questions.map(row => row.publicQuestion));
    return ticket;
  });
  const hardRequest = issueRequest("hard", "HARD");
  const hardResult = await call(actor, "prepareArenaEntry", hardRequest); const hard = hardResult.ticket;
  assert.equal(hardResult.status, "ISSUED"); assert.equal(hard.uid, actor.uid);
  assert.equal(hard.priceNolics, 1); assert.equal(hard.status, "RESERVED");
  await patchFreshFixtures(hardIds.map((id, index) => {
    const content = JSON.parse(splits[index].publicPayload);
    content.options[0].futurePrivate = {correctOptionId: SINGLE.correctOptionId};
    return [`questions/${id}`, {payload: JSON.stringify(content)}];
  }));
  await unchangedFixtures(sourcePaths, "invalid HARD remains unrepaired source after failed entry", () =>
    unchanged(actor, "INVALID_QUESTION precedes all wallet/quota/ticket/ledger/premium writes", async () => {
      const invalid = issueRequest("invalid", "HARD");
      await rejected(() => call(actor, "prepareArenaEntry", invalid), "FAILED_PRECONDITION", "INVALID_QUESTION");
      assert.equal((await db.doc(`arena_entry_tickets/${ticketDocumentId(actor.uid, invalid.requestId)}`).get()).exists, false);
      assert.equal((await quotaRef.get()).data().randomIssued, 7);
    }));
  done("actual runtime missing-id normalization preserves source; invalid nested HARD fails pre-hold with exact wallet/quota/updateTime preservation");

  // Emulate only this newly issued owned ticket predating the new public-boundary guard.
  const ticketRef = db.doc(`arena_entry_tickets/${hard.ticketId}`); const originalHard = (await ticketRef.get()).data();
  const unsafeQuestions = originalHard.questions.map(row => {
    const content = JSON.parse(row.publicQuestion.payload);
    content.options[0].futurePrivate = {correctOptionId: SINGLE.correctOptionId};
    return {...row, publicQuestion: {...row.publicQuestion, payload: JSON.stringify(content)}};
  });
  await ownedTicketPatch(actor, hard, {questions: unsafeQuestions});
  await unchanged(actor, "old unsafe HARD replay/GET fail closed; owner list remains header-only", async () => {
    await rejected(() => call(actor, "prepareArenaEntry", hardRequest), "UNAVAILABLE", "UNAVAILABLE");
    await rejected(() => get(actor, hard), "UNAVAILABLE", "UNAVAILABLE");
    await rejected(() => get(foreign, hard), "NOT_FOUND", "NOT_FOUND");
    const page = await list(actor);
    assert.deepEqual(page.tickets.map(row => row.ticketId).sort(), [easy.ticketId, hard.ticketId].sort());
    assert.equal(page.nextTicketId, null);
    for (const row of page.tickets) {
      assert.equal(row.uid, actor.uid); assert.equal(row.status, "RESERVED");
      assert.deepEqual(Object.keys(row).sort(), ["schemaVersion", "ticketId", "attemptId", "uid", "requestId", "mode", "difficulty",
        "lessonId", "lessonVersion", "status", "priceNolics", "lifeCost", "issuedAtMs", "dayId"].sort());
    }
  });
  const priorCancelUser = (await db.doc(`users/${actor.uid}`).get()).data();
  const refunded = await cancel(actor, hard);
  assert.equal(refunded.status, "CANCELLED"); assert.equal(refunded.terminal.receiptId, `arena-cancel:${hard.ticketId}`);
  assert.equal(refunded.terminal.releasedNolics, hard.priceNolics); assert.equal(refunded.terminal.releasedLifePoints, hard.lifeCost);
  const afterCancelUser = (await db.doc(`users/${actor.uid}`).get()).data();
  assert.equal(afterCancelUser.nolics, priorCancelUser.nolics + hard.priceNolics);
  assert.equal(afterCancelUser.lifePoints, priorCancelUser.lifePoints + hard.lifeCost);
  assert.equal(afterCancelUser.reservedNolics, priorCancelUser.reservedNolics - hard.priceNolics);
  assert.equal(afterCancelUser.reservedLifePoints, priorCancelUser.reservedLifePoints - hard.lifeCost);
  assert.equal(afterCancelUser.arenaSelectedHeldCount, priorCancelUser.arenaSelectedHeldCount - 1);
  const cancellationId = cancelDocumentId(actor.uid, `${runId}_cancel_${hard.ticketId}`);
  const cancellation = await db.doc(`arena_ticket_cancellations/${cancellationId}`).get();
  assert.deepEqual(cancellation.data().result, refunded);
  await unchanged(actor, "old unsafe cancellation replay cannot refund twice or write another receipt", async () => {
    assert.deepEqual(await cancel(actor, hard), refunded);
    assert.deepEqual((await list(actor)).tickets.map(row => row.ticketId), [easy.ticketId]);
    await rejected(() => get(actor, hard), "UNAVAILABLE", "UNAVAILABLE");
    await rejected(() => call(actor, "prepareArenaEntry", hardRequest), "UNAVAILABLE", "UNAVAILABLE");
    const stored = (await ticketRef.get()).data();
    assert.deepEqual(stored.questions, unsafeQuestions); assert.equal(stored.bundleDigest, originalHard.bundleDigest);
    const ledger = (await db.collection("audit/currency_ledger/entries").where("uid", "==", actor.uid).get()).docs
      .map(doc => doc.data()).filter(row => row.sourceId === hard.ticketId && row.reason === REASONS.CANCEL);
    assert.equal(ledger.length, 2);
    assert.equal(ledger.find(row => row.currency === "nolics")?.delta, hard.priceNolics);
    assert.equal(ledger.find(row => row.currency === "standardChargePoints")?.delta, hard.lifeCost);
    assert.equal((await quotaRef.get()).data().randomIssued, 7);
  });
  assert.equal((await cancel(actor, easy)).status, "CANCELLED");
  await unchanged(actor, "boundary account finishes without held funds", async () => {
    assert.deepEqual(await list(actor), {tickets: [], nextTicketId: null});
    const user = (await db.doc(`users/${actor.uid}`).get()).data();
    assert.equal(user.nolics, 1000); assert.equal(user.lifePoints, 1000);
    assert.equal(user.reservedNolics, 0); assert.equal(user.reservedLifePoints, 0); assert.equal(user.arenaSelectedHeldCount, 0);
  });
  await patchFreshFixtures([[`lesson_placements/${lessonId}`, {shelf: "retired"}], [`lessons/${lessonId}`, {archived: true}]]);
  done("old owned unsafe HARD is never delivered; header recovery and one exact refund remain available with no immutable rewrite");
}

async function main() {
  const hubResponse = await fetch(`http://${FIREBASE_HOST}:4400/emulators`, {signal: AbortSignal.timeout(15_000)});
  assert.equal(hubResponse.status, 200); const hub = await hubResponse.json();
  for (const [name, port] of [["auth", 9099], ["firestore", 8080], ["functions", 5001]]) assert.equal(hub[name]?.port, port);
  for (const method of ["listArenaEntryTickets", "fetchArenaEntryTicket"]) {
    await rejected(() => call(null, method, {}), "UNAUTHENTICATED");
    await rejected(() => call({token: "not-a-token"}, method, {}), "UNAUTHENTICATED");
  }
  done("target guard: private demo emulator endpoints present and unauthenticated/invalid-token calls denied before fixture writes");

  const [author, player, foreign] = await Promise.all([account(), account(), account()]);
  const now = Date.now(); const dayId = utcDayId(now);
  const rows = [
    ...[author, player, foreign].map((actor) => [`users/${actor.uid}`, {uid: actor.uid, boxCount: 0,
      nolics: 1000, pointsNolics: 1000, lifePoints: 1000, plasmaPoints: 300,
      lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now}]),
    [`catalogs/${names.catalog}`, {questType: "REGULAR"}],
    [`quests/${names.quest}`, {catalogId: names.catalog, authorUid: author.uid, visibleOn: ["archive"], archived: true}],
    [`sections/${names.section}`, {questId: names.quest}],
    [`themes/${names.theme}`, {sectionId: names.section}],
    [`lessons/${names.lesson}`, {themeId: names.theme, version: 1, publishedAtMs: now - 2000,
      reviewSubmissionId: names.submission, reviewApprovedAtMs: now - 1000}],
    [`admin/review/lessons/${names.lesson}`, {ownerUid: author.uid, questId: names.quest, submissionId: names.submission,
      status: "PUBLISHED", sourceLanguages: ["en"]}],
    ...["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${names.lesson}/reviews/${kind}`, {
      kind, reviewerUid: `${runId}_${kind}`, acceptedByServer: true, reviewerLevelAtSubmit: 100,
      score: 3, submissionId: names.submission}]),
    ...["EASY", "HARD"].flatMap((difficulty) => [0, 1].map((n) => [`questions/${runId}_${difficulty}_${n}`, {
      lessonId: names.lesson, version: 1, difficulty, language: "en", payload: JSON.stringify({...SINGLE, difficulty})}])),
    // Direct new fixture avoids touching a shared season or sync counter through a curator call.
    [`lesson_placements/${names.lesson}`, {lessonId: names.lesson, authorUid: author.uid, questType: "REGULAR", shelf: "arena",
      previousShelf: "review", contentVersion: 1, placementVersion: 1, updatedAtMs: now}],
  ];
  await createFixtures(rows);
  const quotaRef = db.doc(quotaPath(player, dayId));
  await quotaRef.create({uid: player.uid, dayId, randomIssued: 7, updatedAtMs: now, syntheticRecoveryRun: runId});
  evidence.createdFixturePaths.push(quotaRef.path);
  done("fresh ar_ content, owned accounts and quota sentinel created with non-overwrite preconditions");

  const refusedId = `${runId}_price_changed`;
  await unchanged(player, "PRICE_CHANGED before any reservation or regeneration checkpoint", async () => {
    await rejected(() => call(player, "prepareArenaEntry", {requestId: refusedId, mode: "SELECTED", lessonId: names.lesson,
      difficulty: "HARD", maxPriceNolics: 0}), "FAILED_PRECONDITION", "PRICE_CHANGED");
    assert.equal((await db.doc(`arena_entry_tickets/${ticketDocumentId(player.uid, refusedId)}`).get()).exists, false);
    assert.deepEqual(await list(player), {tickets: [], nextTicketId: null});
  });
  done("maxPriceNolics0 returns PRICE_CHANGED with no ticket, wallet debit, hold, quota or premium checkpoint change");

  const tickets = [await prepare(player, "first", "EASY"), await prepare(player, "second"), await prepare(player, "third")];
  const foreignTicket = await prepare(foreign, "foreign");
  const initialHolds = (await db.doc(`users/${player.uid}`).get()).data();
  assert.equal(initialHolds.reservedNolics, 3); assert.equal(initialHolds.arenaSelectedHeldCount, 3);
  assert.equal(initialHolds.reservedLifePoints, tickets.reduce((sum, ticket) => sum + ticket.lifeCost, 0));
  await unchanged(player, "authenticated pagination and denied foreign/payload reads", async () => {
    const sortedIds = tickets.map((ticket) => ticket.ticketId).sort(); const received = []; let afterTicketId;
    for (let pageNumber = 0; pageNumber < 4; pageNumber++) {
      const page = await list(player, {pageSize: 1, ...(afterTicketId ? {afterTicketId} : {})});
      assert.equal(page.tickets.length, 1); const row = page.tickets[0];
      assert.deepEqual(Object.keys(row).sort(), ["schemaVersion", "ticketId", "attemptId", "uid", "requestId", "mode", "difficulty",
        "lessonId", "lessonVersion", "status", "priceNolics", "lifeCost", "issuedAtMs", "dayId"].sort());
      assert.equal(row.uid, player.uid); assert.equal(row.status, "RESERVED"); assert.ok(row.requestId.startsWith(`${runId}_`));
      assert.equal(row.lessonId, names.lesson); received.push(row.ticketId);
      if (page.nextTicketId === null) break;
      assert.equal(page.nextTicketId, row.ticketId); afterTicketId = page.nextTicketId;
    }
    assert.deepEqual(received, sortedIds);
    assert.deepEqual(await list(player, {afterTicketId: "f".repeat(64)}), {tickets: [], nextTicketId: null});
    assert.deepEqual((await list(foreign)).tickets.map((ticket) => ticket.ticketId), [foreignTicket.ticketId]);
    await rejected(() => get(foreign, tickets[0]), "NOT_FOUND", "NOT_FOUND");
    await rejected(() => get(player, foreignTicket), "NOT_FOUND", "NOT_FOUND");
    await rejected(() => list(player, {uid: foreign.uid}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
    await rejected(() => list(player, {pageSize: 21}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
    await rejected(() => call(player, "fetchArenaEntryTicket", {ticketId: "../users/other"}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
    await rejected(() => call(player, "fetchArenaEntryTicket", {ticketId: tickets[0].ticketId, ownerUid: foreign.uid}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
    const first = (await get(player, tickets[0])).ticket;
    assert.equal(first.requestId, tickets[0].requestId); assert.deepEqual(first.questions, tickets[0].questions);
    assert.equal(JSON.parse(first.questions[0].publicQuestion.payload).correctOptionId, "b");
    const hard = (await get(player, tickets[1])).ticket;
    assert.equal(JSON.parse(hard.questions[0].publicQuestion.payload).correctOptionId, undefined);
  });
  done("real HTTP owner filtering, exclusive pages, bounded input and minimal SELECT wire projection; EASY/HARD frozen content preserved");

  const hard = tickets[1]; const rawHard = (await db.doc(`arena_entry_tickets/${hard.ticketId}`).get()).data();
  await ownedTicketPatch(player, hard, {futurePrivate: "ar-private-root",
    contentContext: {...rawHard.contentContext, futurePrivate: "ar-private-context"},
    questions: rawHard.questions.map((question) => ({...question, futurePrivate: "ar-private-wrapper",
      publicQuestion: {...question.publicQuestion, futurePrivate: "ar-private-public-envelope"}}))});
  await unchanged(player, "future private stored fields projection", async () => {
    const recovered = await get(player, hard); const wire = JSON.stringify(recovered);
    for (const marker of ["ar-private-", "gradingKey", "bundleDigest", "requestHash"]) assert.equal(wire.includes(marker), false);
    assert.deepEqual(recovered.ticket.questions, hard.questions);
    assert.equal((await list(player)).tickets.length, 3);
  });
  done("real stored future fields, grading keys and private fingerprints remain excluded by recovery");

  // Only this run's source is retired, without deleting content or changing shared placements.
  const retirement = db.batch();
  retirement.update(db.doc(`lesson_placements/${names.lesson}`), {shelf: "retired"});
  retirement.update(db.doc(`lessons/${names.lesson}`), {archived: true, version: 99});
  await retirement.commit();
  const cancelled = await cancel(player, hard); assert.equal(cancelled.status, "CANCELLED");
  await unchanged(player, "terminal recovery after source retirement and cancellation", async () => {
    const recovered = await get(player, hard);
    assert.equal(recovered.status, "ISSUED"); assert.equal(recovered.ticket.status, "CANCELLED");
    assert.equal(recovered.ticket.lessonVersion, 1); assert.deepEqual(recovered.ticket.questions, hard.questions);
    assert.equal(recovered.ticket.terminal.releasedNolics, hard.priceNolics);
    assert.equal(recovered.ticket.terminal.releasedLifePoints, hard.lifeCost);
    assert.equal(recovered.ticket.terminal.receiptId, `arena-cancel:${hard.ticketId}`);
    assert.deepEqual((await list(player)).tickets.map((ticket) => ticket.ticketId),
      tickets.filter((ticket) => ticket.ticketId !== hard.ticketId).map((ticket) => ticket.ticketId).sort());
    assert.equal((await quotaRef.get()).data().randomIssued, 7);
  });
  done("frozen terminal recovery survives source retirement; list omits cancelled ticket; wallet and quota unchanged by all reads");

  const race = tickets[2];
  const [snapshot, cancellation] = await Promise.all([get(player, race), cancel(player, race)]);
  assert.equal(cancellation.status, "CANCELLED");
  assert.ok(["RESERVED", "CANCELLED"].includes(snapshot.ticket.status));
  if (snapshot.ticket.status === "RESERVED") assert.equal(snapshot.ticket.terminal, null);
  else assert.equal(snapshot.ticket.terminal.receiptId, `arena-cancel:${race.ticketId}`);
  await unchanged(player, "post-race terminal reread", async () => {
    assert.equal((await get(player, race)).ticket.status, "CANCELLED");
    assert.equal((await quotaRef.get()).data().randomIssued, 7);
  });
  done("real concurrent cancel/get produces a self-consistent single-ticket snapshot and durable terminal reread");

  // Explicitly cancel only this run's remaining reservations; keep all fixture/receipt records.
  assert.equal((await cancel(player, tickets[0])).status, "CANCELLED");
  assert.equal((await cancel(foreign, foreignTicket)).status, "CANCELLED");
  for (const actor of [player, foreign]) await unchanged(actor, "final empty reservations scan", async () => {
    assert.deepEqual(await list(actor), {tickets: [], nextTicketId: null});
    const value = (await db.doc(`users/${actor.uid}`).get()).data();
    assert.equal(value.reservedNolics, 0); assert.equal(value.reservedLifePoints, 0); assert.equal(value.arenaSelectedHeldCount, 0);
  });
  done("only own remaining holds explicitly cancelled; no deletion or reset; retained fixtures have zero open reservations");
  await questionBoundaryChecks(author, foreign);
  assert.equal(checks.length, 10); // Preserve all eight original gates and both new boundaries.
  assert.equal(evidence.stateProofs.length, 11); assert.equal(evidence.sourceFixtureProofs.length, 2);
  const sourceAfter = sourceHashes(); assert.deepEqual(sourceAfter, sourceBefore, "Relevant source changed during qualification");
  console.log(JSON.stringify({status: "PASS", kind: "real HTTP callable + Firestore/Auth emulator integration",
    node: process.version, projectId: PROJECT, runId, checks, evidence, sourceBefore, sourceAfter,
    retainedSyntheticFixtures: true, productionIndexDeploymentProven: false}, null, 2));
}
main().catch((error) => {
  console.error(JSON.stringify({status: "FAIL", message: error.message, code: error.code, reason: error.reason,
    runId, checksCompleted: checks, evidence, retainedSyntheticFixtures: true}, null, 2)); process.exitCode = 1;
}).finally(() => admin.app().delete());
