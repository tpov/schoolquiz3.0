"use strict";

// Private demo probe. It creates only its own retained fixtures and never clears a database,
// restarts an emulator, deploys code, uses credentials, or touches an existing lesson.
const assert = require("node:assert/strict");
const {createHash, randomUUID} = require("node:crypto");
const {readFileSync} = require("node:fs");
const Module = require("node:module");

const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
const MAX_AUTH = 2;
const MAX_CREATED_DOCS = 28;
const MAX_HTTP_REQUESTS = 48;
const MAX_OWN_STATE_ROWS = 32;
const MAX_PREFLIGHT_READS = 9975;
const MAX_PREFLIGHT_PLACEMENTS = 998;
const sha = value => createHash("sha256").update(value).digest("hex");

assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
assert.equal(process.env.FIREBASE_CONFIG, undefined);
assert.equal(process.env.AC_RUN_APPROVED, "arena-eligible-universe-live-v1");
assert.equal(process.env.AC_PRIVATE_NETWORK_VERIFIED, "schoolquiz-mvp-private");
assert.equal(sha(readFileSync(__filename)), process.env.AC_EXPECTED_SCRIPT_SHA256);

const EXPECTED_SOURCE = Object.freeze({
  "functions/index.js": "7cad5bd6cb8b1d648112b063c67c418b8ff2464ada89a733fbfcf562882416ec",
  "functions/arena-entry-runtime.js": "2b755cb990a68d8e66fdd183736aa9b1b261fa7e4c99371e686bd42740fe1053",
  "functions/arena-tickets.js": "0d76d7f551a40c278b24014ab42834d94e9d03ba3ca7a005bd712396f200331f",
  "functions/arena-lesson-catalog.js": "ab1bd8754fd822c321ad50844af64b419b41a3e36cb25a6523da4f35b339f8a5",
  "functions/arena-eligible-universe.js": "51c9ea3bc30a066ccf151ab870ee3309f026567d545642f77489e1179c3f57f9",
  "functions/arena-policy.js": "dad7678911bf2d4839f4d3f334ea420ffeb95d69ae3cd63855527f31aed6dc36",
  "functions/_question-fixtures.js": "9e6bfb9ebdf6e5ebc81ccee7b4b654444e919695ede5d67a578d7e82a11f114b",
});
function sourceHashes() {
  return Object.fromEntries(Object.keys(EXPECTED_SOURCE).map(file => [file, sha(readFileSync(`/repo/${file}`))]));
}
function checkSources() { assert.deepEqual(sourceHashes(), EXPECTED_SOURCE, "Reviewed source changed; stop without probing"); }
checkSources();

const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `aeu_${randomUUID().replaceAll("-", "")}`;
const names = Object.fromEntries(["catalog", "quest", "section", "theme"].map(key => [key, `${runId}_${key}`]));
const targetLessonId = `${runId}_target`;
const staleLessonId = `${runId}_stale`;
const accounts = [];
const plannedPaths = new Set();
const checks = [];
let httpRequests = 0;
let phase = "preflight";

// Executes the exact current catalog loader with a read-only transaction facade. This is a
// diagnostic only: source registration is inert, snapshots expose no write references, and no
// callable, trigger, deployment, container, or emulator lifecycle operation is reachable here.
function makeReadOnlyDiagnostic() {
  const nativeRefs = new WeakMap();
  let bootstrapping = true; let initializeCalls = 0; let settingsCalls = 0; let rootLoader;
  let lastAttempt = null; let attempts = 0;
  const readableDoc = path => /^(lesson_ratings|lessons|themes|sections|quests|catalogs)\/[^/]+$/.test(path) ||
    /^admin\/review\/lessons\/[^/]+$/.test(path) || path === "configs/arena_review" ||
    /^catalogs\/[^/]+\/sync_changes\/lesson_[^/]+$/.test(path);
  const readableQuery = path => path === "lesson_placements" || /^admin\/review\/lessons\/[^/]+\/reviews$/.test(path);
  function ref(native, path, isQuery, filters = [], bound = null) {
    const exposed = isQuery ? {
      where: (...args) => ref(native.where(...args), path, true, [...filters, args], bound),
      limit: maximum => { assert.ok(Number.isSafeInteger(maximum) && maximum >= 1 && maximum <= 1001);
        return ref(native.limit(maximum), path, true, filters, maximum); },
    } : {id: native.id, path};
    const wrapper = Object.freeze(exposed); nativeRefs.set(wrapper, {native, path, isQuery, filters, bound}); return wrapper;
  }
  const readOnlyDb = Object.freeze({
    doc: path => ref(db.doc(path), path, false),
    collection: path => ref(db.collection(path), path, true),
    settings: value => { assert.equal(bootstrapping, true); assert.deepEqual(value, {ignoreUndefinedProperties: true}); settingsCalls++; },
    runTransaction: async body => {
      assert.equal(bootstrapping, false, "Unexpected top-level RPC refused");
      return db.runTransaction(async transaction => {
        const measured = {consumed: 0, rawPlacements: null, callbackAttempt: ++attempts};
        const get = async wrapper => {
          const item = nativeRefs.get(wrapper); assert.ok(item, "Unknown diagnostic reference refused");
          assert.ok(item.isQuery ? readableQuery(item.path) : readableDoc(item.path), "Diagnostic read outside catalog loader scope");
          if (item.isQuery) assert.ok(item.bound !== null, "Unbounded diagnostic query refused");
          if (item.path === "lesson_placements") assert.deepEqual(item.filters, [["shelf", "==", "arena"]]);
          const result = await transaction.get(item.native);
          measured.consumed += item.isQuery ? Math.max(1, result.docs.length) : 1;
          assert.ok(measured.consumed <= 12000, "Diagnostic read budget exceeded");
          if (item.path === "lesson_placements") {
            assert.equal(measured.rawPlacements, null); measured.rawPlacements = result.docs.length;
          }
          const snapshot = doc => Object.freeze({id: doc.id, exists: doc.exists, data: () => doc.data()});
          return item.isQuery ? Object.freeze({docs: result.docs.map(snapshot)}) : snapshot(result);
        };
        const result = await body(Object.freeze({get, getAll: (...refs) => Promise.all(refs.map(get))}));
        lastAttempt = measured; return result;
      }, {readOnly: true});
    },
  });
  const namespace = require(require.resolve("firebase-admin/firestore", {paths: ["/repo/functions"]}));
  const accessor = Object.assign(() => readOnlyDb, {Timestamp: namespace.Timestamp, FieldValue: namespace.FieldValue, FieldPath: namespace.FieldPath});
  const adminFacade = Object.freeze({firestore: Object.freeze(accessor), initializeApp: () => {
    assert.equal(bootstrapping, true); initializeCalls++; return undefined;
  }});
  const register = (...args) => { assert.equal(bootstrapping, true); assert.equal(typeof args.at(-1), "function"); return Object.freeze({inertRegistration: true}); };
  class InertHttpsError extends Error { constructor(code, message, details) { super(message); this.code = code; this.details = details; } }
  const inertModules = {
    "firebase-admin": adminFacade,
    "firebase-functions/v2/https": {onCall: register, onRequest: register, HttpsError: InertHttpsError},
    "firebase-functions/v2/firestore": {onDocumentCreated: register},
    "firebase-functions/v2/pubsub": {onMessagePublished: register},
    "firebase-functions/v2/scheduler": {onSchedule: register},
    "firebase-functions/logger": {info: () => assert.fail("Unexpected diagnostic logging"), warn: () => assert.fail("Unexpected diagnostic logging")},
  };
  const catalog = require("/repo/functions/arena-lesson-catalog");
  const actualFactory = catalog.createArenaLessonCatalog; const actualLoad = Module._load;
  assert.equal(require.cache[require.resolve("/repo/functions/index")], undefined, "Index must be captured once in fresh process");
  try {
    catalog.createArenaLessonCatalog = args => {
      assert.equal(rootLoader, undefined); assert.equal(args.db, readOnlyDb);
      rootLoader = args.loadLessonContext; return actualFactory(args);
    };
    Module._load = function(request, parent, ...rest) {
      return Object.hasOwn(inertModules, request) ? inertModules[request] : actualLoad.call(this, request, parent, ...rest);
    };
    require("/repo/functions/index");
  } finally { Module._load = actualLoad; catalog.createArenaLessonCatalog = actualFactory; }
  assert.equal(initializeCalls, 1); assert.equal(settingsCalls, 1); assert.equal(typeof rootLoader, "function");
  assert.equal(attempts, 0); bootstrapping = false;
  const service = actualFactory({db: readOnlyDb, loadLessonContext: rootLoader});
  return async uid => {
    lastAttempt = null; attempts = 0; checkSources();
    const result = await service.list({uid, payload: {pageSize: 1}});
    assert.ok(lastAttempt && Number.isSafeInteger(lastAttempt.rawPlacements));
    return {result, ...lastAttempt};
  };
}

function ownPath(path) {
  return path.split("/").some(part => part.startsWith(`${runId}_`)) ||
    /^(users|profiles)\//.test(path) && accounts.some(account => path === `users/${account.uid}` || path === `profiles/${account.uid}`);
}
function requireOwnPath(path) { assert.ok(ownPath(path), "Fixture path is outside this run"); }
function httpBudget() { assert.ok(httpRequests < MAX_HTTP_REQUESTS, "HTTP request budget exhausted"); httpRequests++; }

async function post(port, endpoint, body, token = null) {
  assert.ok(port === 5001 || port === 9099);
  httpBudget();
  const response = await fetch(`http://${HOST}:${port}/${endpoint}`, {
    method: "POST", redirect: "error", headers: {"content-type": "application/json", ...(token ? {authorization: `Bearer ${token}`} : {})},
    body: JSON.stringify(body), signal: AbortSignal.timeout(30000),
  });
  let json;
  try { json = await response.json(); } catch (_) { json = {}; }
  if (!response.ok || json.error) {
    const error = new Error("Callable probe failed");
    error.probeCode = typeof json.error?.status === "string" ? json.error.status : "UNKNOWN";
    error.probeReason = typeof json.error?.details?.reason === "string" ? json.error.details.reason : null;
    error.httpStatus = response.status;
    throw error;
  }
  assert.equal(response.status, 200);
  return json;
}
async function account() {
  assert.ok(accounts.length < MAX_AUTH, "Auth attempt budget exhausted");
  const result = await post(9099, "identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo", {returnSecureToken: true});
  assert.equal(typeof result.localId, "string"); assert.equal(typeof result.idToken, "string");
  const actor = {uid: result.localId, token: result.idToken}; accounts.push(actor); return actor;
}
async function call(actor, method, data) {
  const result = await post(5001, `${PROJECT}/us-central1/${method}`, {data}, actor.token);
  assert.ok(Object.hasOwn(result, "result"), "Callable result envelope missing"); return result.result;
}

async function listAll(actor) {
  const cards = []; const seen = new Set(); let cursor = null; let fingerprint = null; let total = null;
  for (let page = 0; page < 20; page++) {
    const value = await call(actor, "listArenaLessons", {pageSize: 50, ...(cursor ? {cursor} : {})});
    assert.equal(value.status, "OK"); assert.ok(Array.isArray(value.lessons));
    if (fingerprint === null) { fingerprint = value.snapshotFingerprint; total = value.totalEligibleLessons; }
    else { assert.equal(value.snapshotFingerprint, fingerprint); assert.equal(value.totalEligibleLessons, total); }
    for (const card of value.lessons) {
      assert.equal(typeof card.lessonId, "string"); assert.ok(!seen.has(card.lessonId), "Duplicate catalog card");
      seen.add(card.lessonId); cards.push(card);
    }
    if (value.nextCursor === null) {
      assert.equal(cards.length, total); return {fingerprint, total, cards};
    }
    assert.equal(typeof value.nextCursor, "string"); cursor = value.nextCursor;
  }
  assert.fail("Catalog pagination exceeded bounded probe pages");
}

function userRow(uid, now) {
  return {uid, nolics: 1000, pointsNolics: 1000, lifePoints: 1000, plasmaPoints: 300,
    standardHearts: 10, goldHearts: 3, reservedNolics: 0, reservedLifePoints: 0, arenaSelectedHeldCount: 0,
    lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now};
}
function lessonRows({lessonId, authorUid, placementAuthorUid, title, rating, now}) {
  const submissionId = `${lessonId}_submission`;
  const rows = [
    [`lessons/${lessonId}`, {themeId: names.theme, title, version: 1, publishedAtMs: now - 2000,
      reviewSubmissionId: submissionId, reviewApprovedAtMs: now - 1000}],
    [`admin/review/lessons/${lessonId}`, {ownerUid: authorUid, questId: names.quest, submissionId,
      status: "PUBLISHED", sourceLanguages: ["en"]}],
    ...["TESTING", "LOGIC"].map(kind => [`admin/review/lessons/${lessonId}/reviews/${lessonId}_${kind}`, {
      kind, reviewerUid: `${lessonId}_${kind}`, acceptedByServer: true, reviewerLevelAtSubmit: 100,
      score: 3, submissionId,
    }]),
    [`lesson_placements/${lessonId}`, {lessonId, shelf: "arena", authorUid: placementAuthorUid, contentVersion: 1,
      previousShelf: "review", placementVersion: 1, updatedAtMs: now}],
    [`lesson_ratings/${lessonId}`, {ratingSum: rating.sum, ratingCount: rating.count, ratingVersion: 1}],
  ];
  for (const number of [0, 1]) rows.push([`questions/${lessonId}_question_${number}`, {
    lessonId, version: 1, difficulty: "HARD", language: "en", payload: JSON.stringify({...SINGLE, difficulty: "HARD"}),
  }]);
  return rows;
}
async function createOnly(rows) {
  assert.ok(rows.length > 0 && rows.length <= MAX_CREATED_DOCS);
  assert.equal(new Set(rows.map(([path]) => path)).size, rows.length, "Duplicate fixture path");
  for (const [path] of rows) { requireOwnPath(path); assert.ok(!plannedPaths.has(path), "Fixture path reused"); }
  const existing = await db.getAll(...rows.map(([path]) => db.doc(path)));
  assert.ok(existing.every(snapshot => !snapshot.exists), "Create-only fixture collision");
  checkSources();
  const batch = db.batch();
  for (const [path, data] of rows) {
    plannedPaths.add(path); batch.create(db.doc(path), {...data, syntheticEligibleUniverseRun: runId});
  }
  await batch.commit();
}
function savedStateDoc(snapshot) {
  return {path: snapshot.ref.path, exists: snapshot.exists, data: snapshot.exists ? snapshot.data() : null};
}
async function ownedStateRows(query) {
  const snapshot = await query.limit(MAX_OWN_STATE_ROWS + 1).get();
  assert.ok(snapshot.docs.length <= MAX_OWN_STATE_ROWS, "Owned state exceeded this probe's bounded snapshot");
  return snapshot.docs.map(savedStateDoc).sort((left, right) => left.path.localeCompare(right.path));
}
async function userSnapshot(actor) {
  const [user, premium, tickets, ledger, quotas, cancellations, lots] = await Promise.all([
    db.doc(`users/${actor.uid}`).get(),
    db.doc(`premium_accounts/${actor.uid}`).get(),
    ownedStateRows(db.collection("arena_entry_tickets").where("uid", "==", actor.uid)),
    ownedStateRows(db.collection("audit/currency_ledger/entries").where("uid", "==", actor.uid)),
    ownedStateRows(db.collection("arena_ticket_quotas").where("uid", "==", actor.uid)),
    ownedStateRows(db.collection("arena_ticket_cancellations").where("uid", "==", actor.uid)),
    ownedStateRows(db.collection(`premium_accounts/${actor.uid}/lots`)),
  ]);
  return {user: savedStateDoc(user), premium: savedStateDoc(premium), tickets, ledger, quotas, cancellations, lots};
}
async function prepareSelected(actor, requestId, price) {
  const result = await call(actor, "prepareArenaEntry", {requestId, mode: "SELECTED", lessonId: targetLessonId,
    difficulty: "HARD", maxPriceNolics: price, expectedOwnerUid: actor.uid});
  assert.equal(result.status, "ISSUED"); assert.equal(result.ticket.lessonId, targetLessonId);
  assert.equal(result.ticket.priceNolics, price); return result.ticket;
}
async function cancel(actor, ticket, requestId) {
  const result = await call(actor, "cancelArenaEntry", {ticketId: ticket.ticketId, requestId, expectedOwnerUid: actor.uid});
  assert.equal(result.status, "CANCELLED");
}
async function expectStaleRejection(actor, price) {
  let caught;
  try {
    await call(actor, "prepareArenaEntry", {requestId: `${runId}_selected_stale`, mode: "SELECTED", lessonId: staleLessonId,
      difficulty: "HARD", maxPriceNolics: price, expectedOwnerUid: actor.uid});
  } catch (error) { caught = error; }
  assert.ok(caught, "Stale selected entry must be refused");
  assert.equal(caught.probeCode, "FAILED_PRECONDITION"); assert.equal(caught.probeReason, "LESSON_NOT_ELIGIBLE");
}

async function main() {
  const hub = await fetch(`http://${HOST}:4400/emulators`, {redirect: "error", signal: AbortSignal.timeout(15000)});
  assert.equal(hub.status, 200); const services = await hub.json();
  for (const [name, port] of [["auth", 9099], ["functions", 5001], ["firestore", 8080]]) assert.equal(services[name]?.port, port);
  checks.push("fixed private demo emulator endpoints");

  phase = "read-only-capacity-preflight";
  const diagnostic = await makeReadOnlyDiagnostic()(`${runId}_diagnostic`);
  assert.equal(diagnostic.result.status, "OK");
  assert.ok(diagnostic.rawPlacements <= MAX_PREFLIGHT_PLACEMENTS,
    "Existing arena placement count leaves no bounded room for both create-only fixtures");
  assert.ok(diagnostic.consumed <= MAX_PREFLIGHT_READS,
    "Existing catalog read count leaves no bounded entry reserve for both create-only fixtures");
  checks.push("exact catalog loader read-only preflight left bounded capacity for two fixtures");

  phase = "accounts-and-baseline";
  const [author, player] = await Promise.all([account(), account()]);
  const baseline = await listAll(player); checks.push("full baseline catalog pagination before fixture creation");

  phase = "seed-current-target";
  const now = Date.now();
  await createOnly([
    [`users/${author.uid}`, userRow(author.uid, now)], [`profiles/${author.uid}`, {uid: author.uid}],
    [`users/${player.uid}`, userRow(player.uid, now)], [`profiles/${player.uid}`, {uid: player.uid}],
    [`catalogs/${names.catalog}`, {questType: "REGULAR"}],
    [`quests/${names.quest}`, {catalogId: names.catalog, authorUid: author.uid, visibleOn: ["archive"], archived: true}],
    [`sections/${names.section}`, {questId: names.quest}], [`themes/${names.theme}`, {sectionId: names.section}],
    ...lessonRows({lessonId: targetLessonId, authorUid: author.uid, placementAuthorUid: author.uid,
      title: "Eligible universe target", rating: {sum: 2, count: 1}, now}),
  ]);
  assert.ok(plannedPaths.size <= MAX_CREATED_DOCS); checks.push("one current reviewed target and playable HARD bank created once");

  phase = "baseline-selected";
  const beforeStale = await listAll(player);
  const targetBefore = beforeStale.cards.find(card => card.lessonId === targetLessonId);
  assert.ok(targetBefore); assert.ok(Number.isSafeInteger(targetBefore.priceNolics) && targetBefore.priceNolics >= 1);
  assert.equal(beforeStale.total, baseline.total + 1);
  const frozenPrice = targetBefore.priceNolics;
  const firstTicket = await prepareSelected(player, `${runId}_selected_before`, frozenPrice);
  await cancel(player, firstTicket, `${runId}_cancel_before`);
  checks.push("current target catalog quote and selected frozen ticket agree before stale injection");

  phase = "inject-stale-high";
  await createOnly(lessonRows({lessonId: staleLessonId, authorUid: author.uid, placementAuthorUid: `${runId}_wrong_author`,
    title: "Stale placement must not be priced", rating: {sum: 3, count: 1}, now: now + 1}));
  assert.ok(plannedPaths.size <= MAX_CREATED_DOCS); checks.push("playable independently reviewed stale-author placement created once");

  phase = "stale-parity";
  const afterStale = await listAll(player);
  const targetAfter = afterStale.cards.find(card => card.lessonId === targetLessonId);
  assert.equal(afterStale.fingerprint, beforeStale.fingerprint, "Stale raw placement must not alter the safe catalog fingerprint");
  assert.equal(afterStale.total, beforeStale.total); assert.equal(afterStale.cards.some(card => card.lessonId === staleLessonId), false);
  assert.equal(targetAfter?.priceNolics, frozenPrice);
  const secondTicket = await prepareSelected(player, `${runId}_selected_after`, frozenPrice);
  await cancel(player, secondTicket, `${runId}_cancel_after`);
  const beforeRefusal = await userSnapshot(player);
  await expectStaleRejection(player, frozenPrice);
  assert.deepEqual(await userSnapshot(player), beforeRefusal, "Stale selected refusal must not create a ticket or financial effect");
  checks.push("stale placement leaves catalog fingerprint and selected quote unchanged; direct stale selection is atomic refusal");

  phase = "random-sample";
  const random = await call(player, "prepareArenaEntry", {requestId: `${runId}_random`, mode: "RANDOM", lessonId: null,
    difficulty: "HARD", expectedOwnerUid: player.uid});
  assert.equal(random.status, "ISSUED"); assert.equal(random.ticket.mode, "RANDOM"); assert.equal(random.ticket.priceNolics, 0);
  const safeIds = new Set(afterStale.cards.map(card => card.lessonId));
  assert.ok(safeIds.has(random.ticket.lessonId)); assert.notEqual(random.ticket.lessonId, staleLessonId);
  await cancel(player, random.ticket, `${runId}_cancel_random`);
  const finalUser = await userSnapshot(player);
  assert.equal(finalUser.user.data?.reservedNolics || 0, 0);
  assert.equal(finalUser.user.data?.reservedLifePoints || 0, 0);
  assert.equal(finalUser.user.data?.arenaSelectedHeldCount || 0, 0);
  checks.push("random sample returns a current catalog member, never the stale placement, and leaves no held balance after cancel");

  checkSources();
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, scriptSha256: sha(readFileSync(__filename)),
    sourceHashes: sourceHashes(), retainedFixtures: plannedPaths.size, createdAuthAccounts: accounts.length,
    frozenPrice, httpRequests, checks, randomMembershipProof: "sample-only; source/unit proof covers all random ordering"}, null, 2));
}

main().catch(error => {
  const safeCodes = new Set(["FAILED_PRECONDITION", "UNAVAILABLE", "INTERNAL", "UNKNOWN"]);
  const safeReasons = new Set(["LESSON_NOT_ELIGIBLE", "UNAVAILABLE", "ARENA_INDEX_REBUILD_REQUIRED"]);
  console.error(JSON.stringify({status: "FAIL", phase, runId, errorKind: error.name === "AssertionError" ? "ASSERTION" : "PROBE",
    code: safeCodes.has(error.probeCode) ? error.probeCode : null,
    reason: safeReasons.has(error.probeReason) ? error.probeReason : null,
    plannedFixturePaths: [...plannedPaths].sort(), authAttempts: accounts.length, httpRequests}, null, 2));
  process.exitCode = 1;
}).finally(() => admin.app().delete());
