"use strict";

// DRAFT: requires root's explicit execution cue and a verified private-network launcher.
// Fixed demo target, at most 2 Auth attempts / 32 own create-only docs / ONE title update.
// No financial callables, deletion, source edits, cleanup, scheduler or emulator lifecycle calls.
const assert = require("node:assert/strict");
const {createHash, randomUUID} = require("node:crypto");
const {readFileSync} = require("node:fs");
const Module = require("node:module");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase";
const MAX_AUTH = 2;
const MAX_CREATED_DOCS = 32;
const MAX_PAGES = 20;
const MAX_STATE_ROWS = 32;
const MAX_HTTP_REQUESTS = 80;
const MAX_RUN_MS = 300000;
const sha = value => createHash("sha256").update(value).digest("hex");
assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, `${HOST}:8080`);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, `${HOST}:9099`);
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined);
assert.equal(process.env.FIREBASE_CONFIG, undefined);
assert.equal(process.env.AC_RUN_APPROVED, "catalog-read-only-ac-v1");
// The launcher must actually inspect Docker network/ports first; a script inside the container
// cannot attest Docker configuration. No Docker socket is mounted or used by this harness.
assert.equal(process.env.AC_PRIVATE_NETWORK_VERIFIED, "schoolquiz-mvp-private");
const scriptSha256 = sha(readFileSync(__filename));
assert.equal(scriptSha256, process.env.AC_EXPECTED_SCRIPT_SHA256);
const EXPECTED_SOURCE = Object.freeze({
  "functions/index.js": "7cad5bd6cb8b1d648112b063c67c418b8ff2464ada89a733fbfcf562882416ec",
  "functions/arena-lesson-catalog.js": "3ef0ef385a2ef44c7735408cd448f4fb6b73b53530e932a6289a4744ca8cc1d3",
  "functions/arena-policy.js": "dad7678911bf2d4839f4d3f334ea420ffeb95d69ae3cd63855527f31aed6dc36",
  "functions/question-key-store.js": "052d0eaeebc7145f3c86545a091e22b20ac40d826e6fedc760afbb7a3d3126d4",
  "functions/question-redaction.js": "df1606e9a29f931c3cab8c3d34167ad0cf357d1099df49105ca530c39ccdf0e1",
  "functions/_question-fixtures.js": "9e6bfb9ebdf6e5ebc81ccee7b4b654444e919695ede5d67a578d7e82a11f114b",
  "firestore.rules": "36847a097949107aeeba4c93e284add6bc3bdfb1b83b474dd2e6ca87db8089b7",
  "firestore.indexes.json": "742325021fc4d297a4d915338eed1ceec27737643cee42bf2965c1fcb02e2633",
});
const sourceHashes = () => Object.fromEntries(Object.keys(EXPECTED_SOURCE).map(file => [file, sha(readFileSync(`/repo/${file}`))]));
const checkSources = () => assert.deepEqual(sourceHashes(), EXPECTED_SOURCE, "Reviewed source changed; stop without rewriting expectations");
checkSources();
const admin = require("/repo/functions/node_modules/firebase-admin");
const {SINGLE} = require("/repo/functions/_question-fixtures");
const {questionKeyDocuments} = require("/repo/functions/question-key-store");
const {compareScores} = require("/repo/functions/arena-policy");
const app = admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();
const runId = `ac_${randomUUID().replaceAll("-", "")}`;
const startedAtMs = Date.now();
const names = Object.fromEntries(["catalog", "quest", "section", "theme"].map(name => [name, `${runId}_${name}`]));
const lessonIds = [1, 2, 3].map(n => `${runId}_lesson_${n}`);
const ownLessons = new Set(lessonIds);
const accounts = [];
const plannedPaths = new Set();
const createdSnapshots = new Map();
const evidence = {checks: [], stateProofs: [], httpOutcomes: [], authAttempts: 0, httpRequests: 0,
  seedStatus: "NOT_ATTEMPTED", titleUpdates: 0, titleUpdateStatus: "NOT_ATTEMPTED"};
let phase = "preflight";
let initialColdRetryUsed = false;
function deadline() { assert.ok(Date.now() - startedAtMs <= MAX_RUN_MS, "Cooperative harness deadline exceeded"); }
function httpBudget() { deadline(); assert.ok(evidence.httpRequests < MAX_HTTP_REQUESTS, "HTTP request budget exhausted"); evidence.httpRequests++; }
function canonical(value) {
  if (value && typeof value.toJSON === "function") return canonical(value.toJSON());
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") return `{${Object.keys(value).sort().map(k => `${JSON.stringify(k)}:${canonical(value[k])}`).join(",")}}`;
  return JSON.stringify(value);
}
const stamp = time => time ? {seconds: time.seconds, nanoseconds: time.nanoseconds} : null;
const saved = doc => ({path: doc.ref.path, exists: doc.exists, createTime: stamp(doc.createTime),
  updateTime: stamp(doc.updateTime), data: doc.exists ? doc.data() : null});
function done(name, details = {}) {
  checkSources(); deadline(); evidence.checks.push(name);
  console.log(JSON.stringify({status: "PASS", check: name, ...details}));
}
function ownedPath(path) {
  if (/^(users|profiles)\//.test(path)) return path.split("/").length === 2 && accounts.some(actor => actor.uid === path.split("/")[1]);
  return path.split("/").some(part => part.startsWith(`${runId}_`));
}
function requireOwnPath(path) { assert.ok(plannedPaths.has(path) && ownedPath(path), "Path outside exact own fixture allowlist"); }

// Diagnostic only: run the SAME catalog and capture the SAME root loader by factory injection
// while requiring the complete index module. No source slicing, copied loader or handler call.
// Bootstrap has no RPC capability; exactly one initialize/settings request is intercepted.
// The already initialized demo app is reused, and all trigger registrations are inert values.
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
      // The SDK may invoke this callback again on a new OR reused transaction object.
      // Report only the successful callback's budget, never accumulate across retries.
      return db.runTransaction(async transaction => {
        const measured = {consumed: 0, rawPlacements: null, callbackAttempt: ++attempts};
        const get = async wrapper => {
          const item = nativeRefs.get(wrapper); assert.ok(item, "Unknown diagnostic reference refused");
          assert.ok(item.isQuery ? readableQuery(item.path) : readableDoc(item.path), "Diagnostic read outside catalog loader scope");
          if (item.isQuery) assert.ok(item.bound !== null, "Unbounded diagnostic query refused");
          if (item.path === "lesson_placements") assert.deepEqual(item.filters, [["shelf", "==", "arena"]]);
          deadline(); const result = await transaction.get(item.native); deadline();
          measured.consumed += item.isQuery ? Math.max(1, result.docs.length) : 1;
          assert.ok(measured.consumed <= 12000, "Diagnostic read budget exceeded");
          if (item.path === "lesson_placements") {
            assert.equal(measured.rawPlacements, null); measured.rawPlacements = result.docs.length;
          }
          // Do not pass SDK snapshot.ref (or its write methods) to index/catalog.
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
    const result = await service.list({uid, payload: {pageSize: 50}});
    assert.ok(lastAttempt && Number.isSafeInteger(lastAttempt.rawPlacements));
    return {result, ...lastAttempt};
  };
}

// Only the four fixed emulator ports can be addressed. Redirects are refused. Auth requests
// are NEVER retried: a lost successful reply must not silently create a third account.
async function post(port, endpoint, body, token, coldStart = false) {
  assert.ok(port === 5001 || port === 9099); deadline();
  const perform = () => {
    httpBudget();
    return fetch(`http://${HOST}:${port}/${endpoint}`, {method: "POST", redirect: "error",
      headers: {"content-type": "application/json", ...(token ? {authorization: `Bearer ${token}`} : {})},
      body: JSON.stringify(body), signal: AbortSignal.timeout(30000)});
  };
  let response;
  try { response = await perform(); }
  catch (error) {
    if (!coldStart || port !== 5001 || initialColdRetryUsed || error.name !== "TypeError") throw error;
    initialColdRetryUsed = true; deadline(); response = await perform();
  }
  const json = await response.json(); deadline();
  if (!response.ok || json.error) {
    // Never keep raw error messages/details (which could contain private SDK information).
    const failure = new Error("HTTP probe failed");
    failure.probeCode = typeof json.error?.status === "string" ? json.error.status : "UNKNOWN_HTTP";
    failure.probeReason = typeof json.error?.details?.reason === "string" ? json.error.details.reason : null;
    failure.genericMessage = json.error?.message === "Arena lessons could not be loaded";
    failure.httpStatus = response.status;
    throw failure;
  }
  assert.equal(response.status, 200);
  evidence.httpOutcomes.push({phase, status: 200, method: "POST", service: port === 5001 ? "catalog" : "auth"});
  return json;
}
async function call(actor, payload, coldStart = false) {
  const json = await post(5001, `${PROJECT}/us-central1/listArenaLessons`, {data: payload}, actor?.token, coldStart);
  assert.ok(json && Object.hasOwn(json, "result"), "Callable result envelope missing");
  return json.result;
}
async function rejection(action, code, reason) {
  let caught;
  try { await action(); } catch (error) { caught = error; }
  assert.ok(caught, "Expected callable refusal");
  assert.equal(caught.probeCode, code); assert.equal(caught.probeReason, reason);
  assert.equal(caught.genericMessage, true, "Callable error must use generic public text");
  evidence.httpOutcomes.push({phase, status: caught.httpStatus, code, reason});
}
async function account() {
  assert.ok(evidence.authAttempts < MAX_AUTH, "Auth attempt budget exhausted");
  evidence.authAttempts++;
  const result = await post(9099, "identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo", {returnSecureToken: true});
  assert.equal(typeof result.localId, "string"); assert.equal(typeof result.idToken, "string");
  assert.ok(result.localId && !result.localId.includes("/") && !accounts.some(a => a.uid === result.localId));
  const actor = {uid: result.localId, token: result.idToken}; accounts.push(actor); return actor;
}
async function boundedQuery(query) {
  deadline(); const rows = await query.limit(MAX_STATE_ROWS + 1).get(); deadline();
  assert.ok(rows.docs.length <= MAX_STATE_ROWS, "Unexpected owned financial history overflow"); return rows.docs.map(saved);
}
async function captureState() {
  deadline();
  const paths = [...plannedPaths]; paths.forEach(requireOwnPath);
  const fixtures = paths.length ? (await db.getAll(...paths.map(path => db.doc(path)))).map(saved) : [];
  const financial = [];
  for (const actor of accounts) {
    const docs = await db.getAll(db.doc(`users/${actor.uid}`), db.doc(`profiles/${actor.uid}`), db.doc(`premium_accounts/${actor.uid}`));
    financial.push(...docs.map(saved));
    for (const collection of ["arena_ticket_quotas", "arena_entry_tickets", "arena_ticket_cancellations",
      "audit/currency_ledger/entries", "mutation_keys", "arena_mutations"]) {
      financial.push(...await boundedQuery(db.collection(collection).where("uid", "==", actor.uid)));
    }
    financial.push(...await boundedQuery(db.collection(`premium_accounts/${actor.uid}/lots`)));
  }
  deadline();
  return {fixtures: fixtures.sort((a, b) => a.path.localeCompare(b.path)), financial: financial.sort((a, b) => a.path.localeCompare(b.path))};
}
async function unchanged(label, action) {
  const before = await captureState();
  try { return await action(); }
  finally {
    const after = await captureState(); assert.deepEqual(after, before, "Read changed owned data or exact timestamps");
    evidence.stateProofs.push({label, beforeSha256: sha(canonical(before)), afterSha256: sha(canonical(after)),
      fixtureRows: before.fixtures.length, financialRows: before.financial.length});
  }
}

function fixtureRows(author, reader) {
  const now = Date.now(); const privateMarker = `${runId}_PRIVATE`;
  const rows = [
    ...[author, reader].flatMap(actor => [
      [`users/${actor.uid}`, {uid: actor.uid, nolics: 100, pointsNolics: 100, lifePoints: 1000, plasmaPoints: 300,
        reservedNolics: 0, reservedLifePoints: 0, arenaSelectedHeldCount: 0, lifePointsUpdatedAtMs: now, plasmaPointsUpdatedAtMs: now,
        nickname: privateMarker, email: privateMarker}],
      [`profiles/${actor.uid}`, {uid: actor.uid, nickname: privateMarker}],
    ]),
    [`catalogs/${names.catalog}`, {questType: "REGULAR", version: 1}],
    [`quests/${names.quest}`, {catalogId: names.catalog, authorUid: author.uid, visibleOn: ["archive"], archived: true, version: 1}],
    [`sections/${names.section}`, {questId: names.quest, version: 1}], [`themes/${names.theme}`, {sectionId: names.section, version: 1}],
  ];
  for (const [index, lessonId] of lessonIds.entries()) {
    const submissionId = `${runId}_submission_${index}`;
    rows.push([`lessons/${lessonId}`, {themeId: names.theme, title: `Catalog fixture ${index + 1}`, version: 1,
      reviewSubmissionId: submissionId, reviewApprovedAtMs: now - 1000, publishedAtMs: now - 2000}]);
    rows.push([`admin/review/lessons/${lessonId}`, {ownerUid: author.uid, questId: names.quest, submissionId,
      status: "PUBLISHED", sourceLanguages: ["en"]}]);
    for (const kind of ["TESTING", "LOGIC"]) rows.push([`admin/review/lessons/${lessonId}/reviews/${runId}_${kind}`, {
      kind, reviewerUid: `${runId}_${kind}`, acceptedByServer: true, reviewerLevelAtSubmit: 100, score: 3, submissionId}]);
    rows.push([`lesson_placements/${lessonId}`, {lessonId, authorUid: author.uid, questType: "REGULAR", shelf: "arena",
      previousShelf: "review", contentVersion: 1, placementVersion: 1, updatedAtMs: now, decidedByUid: privateMarker,
      lastReason: privateMarker, lastRejection: {actorUid: privateMarker, reason: privateMarker}}]);
    if (index > 0) rows.push([`lesson_ratings/${lessonId}`, {lessonId, authorUid: author.uid, ratingSum: index * 2, ratingCount: index, ratingVersion: 1}]);
  }
  const questions = [0, 1].map(index => ({id: `${runId}_question_${index}`, lessonId: lessonIds[0], difficulty: "HARD",
    version: 1, order: index, language: "en", payload: JSON.stringify({...SINGLE, difficulty: "HARD"})}));
  const published = questionKeyDocuments(questions, {publicHalfRedacted: true, random: () => 0});
  assert.equal(published.refusals.length, 0); assert.equal(published.publicPayloads.length, 2);
  assert.deepEqual(Object.keys(published.documents), [`question_keys/${lessonIds[0]}`]);
  for (const question of questions) {
    const half = published.publicPayloads.find(row => row.questionId === question.id);
    assert.ok(half); assert.equal(Object.hasOwn(JSON.parse(half.payload), "correctOptionId"), false);
    rows.push([`questions/${question.id}`, {...question, payload: half.payload}]);
  }
  rows.push([`question_keys/${lessonIds[0]}`, published.documents[`question_keys/${lessonIds[0]}`]]);
  rows.push([`lesson_placements/${lessonIds[0]}/history/${runId}_private`, {actorUid: privateMarker, reason: privateMarker, status: "REJECTED"}]);
  return rows;
}
async function seed(rows) {
  assert.equal(evidence.seedStatus, "NOT_ATTEMPTED"); assert.equal(rows.length, 29);
  assert.ok(rows.length <= MAX_CREATED_DOCS && new Set(rows.map(([path]) => path)).size === rows.length);
  rows.forEach(([path]) => { assert.ok(ownedPath(path), "Foreign seed path refused"); plannedPaths.add(path); });
  const existing = await db.getAll(...rows.map(([path]) => db.doc(path)));
  assert.ok(existing.every(row => !row.exists), "Create-only fixture collision");
  checkSources(); deadline(); evidence.seedStatus = "ATTEMPTED_OUTCOME_NOT_YET_KNOWN";
  const batch = db.batch();
  for (const [path, row] of rows) { requireOwnPath(path); batch.create(db.doc(path), {...row, syntheticCatalogRun: runId}); }
  await batch.commit(); evidence.seedStatus = "COMMITTED";
  const created = await db.getAll(...rows.map(([path]) => db.doc(path)));
  for (const doc of created) {
    assert.ok(doc.exists && doc.createTime && doc.updateTime); assert.equal(doc.data().syntheticCatalogRun, runId);
    createdSnapshots.set(doc.ref.path, doc);
  }
  deadline();
}

function keys(value, allowed) { assert.ok(value && typeof value === "object" && !Array.isArray(value)); assert.deepEqual(Object.keys(value).sort(), allowed.slice().sort()); }
function integer(value, minimum = 0) { assert.ok(Number.isSafeInteger(value) && value >= minimum, "Invalid public integer"); }
function validateCard(card) {
  keys(card, ["lessonId", "contentVersion", "title", "authorUid", "rating", "priceNolics", "placement"]);
  assert.equal(typeof card.lessonId, "string"); assert.equal(typeof card.authorUid, "string"); integer(card.contentVersion, 1);
  assert.ok(card.lessonId.trim() && card.authorUid.trim() && !card.lessonId.includes("/") && !card.authorUid.includes("/"));
  assert.ok(card.title === null || (typeof card.title === "string" && card.title.trim())); integer(card.priceNolics, 1); assert.ok(card.priceNolics <= 100);
  keys(card.rating, ["sum", "count", "weightedScore", "ratingVersion"]);
  for (const key of ["sum", "count", "ratingVersion"]) integer(card.rating[key]);
  keys(card.rating.weightedScore, ["numerator", "denominator"]);
  integer(card.rating.weightedScore.numerator); integer(card.rating.weightedScore.denominator, 1);
  assert.equal(BigInt(card.rating.weightedScore.numerator), BigInt(card.rating.sum) + 40n);
  assert.equal(BigInt(card.rating.weightedScore.denominator), BigInt(card.rating.count) + 20n);
  assert.ok(BigInt(card.rating.sum) >= BigInt(card.rating.count) && BigInt(card.rating.sum) <= 3n * BigInt(card.rating.count));
  keys(card.placement, ["shelf", "previousShelf", "placementVersion", "updatedAtMs"]); assert.equal(card.placement.shelf, "arena");
  assert.ok(card.placement.previousShelf === null || ["review", "arena", "tournament", "home", "retired", "rejected"].includes(card.placement.previousShelf));
  if (card.placement.placementVersion !== null) integer(card.placement.placementVersion, 1);
  if (card.placement.updatedAtMs !== null) integer(card.placement.updatedAtMs);
  assert.ok(!JSON.stringify(card).includes(`${runId}_PRIVATE`), "Private fixture marker leaked");
}
function validatePage(page) {
  keys(page, ["status", "catalogVersion", "snapshotFingerprint", "serverNowMs", "entryAvailability", "totalEligibleLessons", "lessons", "nextCursor"]);
  assert.equal(page.status, "OK"); assert.equal(page.catalogVersion, "arena-lesson-catalog-v1");
  assert.equal(page.entryAvailability, "PREPARE_REQUIRED"); assert.match(page.snapshotFingerprint, /^[a-f0-9]{64}$/);
  integer(page.serverNowMs); integer(page.totalEligibleLessons); assert.ok(page.totalEligibleLessons <= 1000);
  assert.ok(Array.isArray(page.lessons)); page.lessons.forEach(validateCard);
  assert.ok(page.nextCursor === null || (typeof page.nextCursor === "string" && /^[A-Za-z0-9_-]+$/.test(page.nextCursor) && page.nextCursor.length <= 4096));
}
async function collectCatalog(actor) {
  const seen = new Set(); const ownCards = []; const digest = createHash("sha256");
  let cursor; let fingerprint; let total; let previous; let pages = 0;
  for (; pages < MAX_PAGES; pages++) {
    const page = await call(actor, {pageSize: 50, ...(cursor ? {cursor} : {})}); validatePage(page);
    assert.ok(page.lessons.length <= 50);
    if (fingerprint === undefined) { fingerprint = page.snapshotFingerprint; total = page.totalEligibleLessons; }
    else { assert.equal(page.snapshotFingerprint, fingerprint); assert.equal(page.totalEligibleLessons, total); }
    for (const card of page.lessons) {
      assert.ok(!seen.has(card.lessonId), "Duplicate catalog lesson across pages"); seen.add(card.lessonId);
      if (previous) {
        const scoreOrder = compareScores(previous.rating.weightedScore, card.rating.weightedScore);
        assert.ok(scoreOrder > 0 || (scoreOrder === 0 && previous.lessonId < card.lessonId), "Catalog sort changed");
      }
      previous = card; digest.update(canonical(card)); digest.update("\n");
      if (ownLessons.has(card.lessonId)) ownCards.push(card);
    }
    if (page.nextCursor === null) {
      assert.equal(seen.size, total); return {snapshotFingerprint: fingerprint, total, pages: pages + 1, ownCards, cardDigest: digest.digest("hex")};
    }
    assert.ok(page.lessons.length > 0 && page.nextCursor !== cursor, "Pagination made no progress"); cursor = page.nextCursor;
  }
  assert.fail("Catalog still has a cursor at the explicit 20-page bound; no truncated success");
}
async function deniedPrivateGet(actor, path) {
  requireOwnPath(path); httpBudget();
  const response = await fetch(`http://${HOST}:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`, {
    method: "GET", redirect: "error", headers: {authorization: `Bearer ${actor.token}`}, signal: AbortSignal.timeout(15000)});
  await response.arrayBuffer(); assert.equal(response.status, 403); deadline();
  evidence.httpOutcomes.push({phase, status: response.status, path, actorUid: actor.uid, method: "GET"});
}
async function changeOwnTitle() {
  assert.equal(evidence.titleUpdates, 0); assert.equal(evidence.titleUpdateStatus, "NOT_ATTEMPTED");
  const path = `lessons/${lessonIds[0]}`; requireOwnPath(path);
  const original = createdSnapshots.get(path); assert.ok(original?.exists); checkSources(); deadline();
  const before = await captureState();
  // Stable original preconditions survive retries. A changed/recreated document is refused,
  // not silently adopted as a new title-update target after an optimistic retry.
  evidence.titleUpdateStatus = "ATTEMPTED_OUTCOME_NOT_YET_KNOWN";
  await db.runTransaction(async transaction => {
    const ref = db.doc(path); const current = await transaction.get(ref);
    assert.ok(current.exists && current.createTime.isEqual(original.createTime) && current.updateTime.isEqual(original.updateTime));
    assert.equal(current.data().syntheticCatalogRun, runId); assert.deepEqual(current.data(), original.data());
    checkSources(); deadline(); transaction.update(ref, {title: "Catalog fixture title changed"}, {lastUpdateTime: original.updateTime});
  });
  evidence.titleUpdates = 1; evidence.titleUpdateStatus = "COMMITTED";
  const after = await captureState(); assert.deepEqual(after.financial, before.financial);
  const expectedFixtures = before.fixtures.map(row => {
    if (row.path !== path) return row;
    const changed = after.fixtures.find(candidate => candidate.path === path);
    assert.deepEqual(changed.createTime, row.createTime); assert.notDeepEqual(changed.updateTime, row.updateTime);
    return {...row, data: {...row.data, title: "Catalog fixture title changed"}, updateTime: changed.updateTime};
  });
  assert.deepEqual(after.fixtures, expectedFixtures);
  evidence.titleChange = {path, originalCreateTime: stamp(original.createTime), originalUpdateTime: stamp(original.updateTime),
    beforeSha256: sha(canonical(before)), afterSha256: sha(canonical(after)), exactOwnPrecondition: true};
}

async function main() {
  httpBudget();
  const hub = await fetch(`http://${HOST}:4400/emulators`, {redirect: "error", signal: AbortSignal.timeout(15000)});
  assert.equal(hub.status, 200); const services = await hub.json();
  for (const [name, port] of [["auth", 9099], ["functions", 5001], ["firestore", 8080]]) assert.equal(services[name]?.port, port);
  await rejection(() => call(null, {}, true), "UNAUTHENTICATED", "UNAUTHENTICATED");
  done("fixed demo services and new callable present; anonymous read denied before Auth/Firestore fixture creation");
  phase = "healthy-baseline";
  const reader = await account();
  await unchanged("healthy baseline and strict body before any fixture seed", async () => {
    await rejection(() => call(reader, {uid: "forged-owner"}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
    const baseline = await call(reader, {pageSize: 50}); validatePage(baseline);
    evidence.baseline = {snapshotFingerprint: baseline.snapshotFingerprint, totalEligibleLessons: baseline.totalEligibleLessons};
  });
  done("existing global catalog healthy before seeding; no repair of old fixtures");
  phase = "read-only-headroom";
  await unchanged("actual catalog diagnostic read budget and baseline snapshot identity", async () => {
    const diagnostic = await makeReadOnlyDiagnostic()(reader.uid); validatePage(diagnostic.result);
    assert.equal(diagnostic.result.snapshotFingerprint, evidence.baseline.snapshotFingerprint, "Catalog drift before seed");
    assert.equal(diagnostic.result.totalEligibleLessons, evidence.baseline.totalEligibleLessons);
    assert.ok(diagnostic.rawPlacements <= 997, "Insufficient raw placement headroom; no seed");
    assert.ok(diagnostic.consumed <= 11900, "Insufficient measured read headroom; no seed");
    evidence.headroom = {consumed: diagnostic.consumed, rawPlacements: diagnostic.rawPlacements,
      callbackAttempt: diagnostic.callbackAttempt, snapshotFingerprint: diagnostic.result.snapshotFingerprint,
      remainingReadBudget: 12000 - diagnostic.consumed, reservedReadBudget: 100, plannedAdditionalReads: 39};
  });
  done("same actual root loader/catalog: measured read headroom and unchanged public snapshot before seed", evidence.headroom);
  phase = "create-own-fixtures"; const author = await account();
  await seed(fixtureRows(author, reader)); done("29 fresh marked documents created once; no season, financial or shared fixture mutation");
  phase = "safe-public-pagination";
  await unchanged("public complete pagination and cross-account safe projection", async () => {
    const first = await collectCatalog(reader); const other = await collectCatalog(author);
    assert.equal(first.snapshotFingerprint, other.snapshotFingerprint); assert.equal(first.cardDigest, other.cardDigest);
    assert.deepEqual(first.ownCards, other.ownCards); assert.deepEqual(first.ownCards.map(card => card.lessonId), lessonIds);
    first.ownCards.forEach((card, index) => {
      assert.equal(card.authorUid, author.uid); assert.equal(card.title, `Catalog fixture ${index + 1}`);
      assert.equal(card.rating.sum, index * 2); assert.equal(card.rating.count, index);
    });
    assert.equal(first.ownCards[0].priceNolics, 1); assert.equal(first.ownCards[1].priceNolics, first.ownCards[2].priceNolics);
    evidence.catalog = first;
    for (const actor of [reader, author]) for (const path of [`lesson_placements/${lessonIds[0]}`,
      `lesson_placements/${lessonIds[0]}/history/${runId}_private`]) await deniedPrivateGet(actor, path);
    await rejection(() => call(reader, {pageSize: 51}), "INVALID_ARGUMENT", "INVALID_ARGUMENT");
  });
  done("full stable pagination, exact fractions/equal prices, UID-only author and private raw placement/history GET denial");
  phase = "cursor-before-change";
  const oldPage = await unchanged("capture old cursor without mutation", () => call(reader, {pageSize: 1}));
  validatePage(oldPage); assert.ok(oldPage.nextCursor);
  phase = "one-own-title-update"; await changeOwnTitle();
  phase = "stale-cursor-and-fresh-restart";
  await unchanged("stale cursor refusal and fresh restart preserve all owned state", async () => {
    await rejection(() => call(reader, {cursor: oldPage.nextCursor}), "FAILED_PRECONDITION", "CATALOG_CHANGED");
    const refreshed = await collectCatalog(reader); assert.notEqual(refreshed.snapshotFingerprint, oldPage.snapshotFingerprint);
    assert.equal(refreshed.ownCards.find(card => card.lessonId === lessonIds[0])?.title, "Catalog fixture title changed");
    evidence.refreshed = {snapshotFingerprint: refreshed.snapshotFingerprint, cardDigest: refreshed.cardDigest, total: refreshed.total, pages: refreshed.pages};
  });
  done("one exact own title CAS invalidates old cursor; fresh read changes no money, source records or receipts");
  assert.equal(accounts.length, 2); assert.equal(plannedPaths.size, 29); assert.equal(evidence.titleUpdates, 1); checkSources();
  console.log(JSON.stringify({status: "PASS", projectId: PROJECT, runId, scriptSha256, sourceHashes: sourceHashes(),
    elapsedMs: Date.now() - startedAtMs, maxRssKiB: process.resourceUsage().maxRSS,
    createdAuthUids: accounts.map(actor => actor.uid), retainedFixturePaths: [...plannedPaths].sort(),
    retainedData: true, noFinancialCalls: true, ...evidence}, null, 2));
}
main().catch(error => {
  // Deliberately omit raw messages/stacks/responses and all token-bearing account objects.
  const safeCodes = new Set(["UNAUTHENTICATED", "INVALID_ARGUMENT", "FAILED_PRECONDITION", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL"]);
  const safeReasons = new Set(["UNAUTHENTICATED", "INVALID_ARGUMENT", "CATALOG_CHANGED", "CATALOG_TOO_LARGE", "CATALOG_INVALID", "SERVER_FAILURE"]);
  console.error(JSON.stringify({status: "FAIL", phase, runId, scriptSha256, errorKind: error.name === "AssertionError" ? "ASSERTION" : "PROBE_OR_SDK_FAILURE",
    code: safeCodes.has(error.probeCode) ? error.probeCode : null, reason: safeReasons.has(error.probeReason) ? error.probeReason : null,
    createdAuthUids: accounts.map(actor => actor.uid), retainedPlannedPaths: [...plannedPaths].sort(),
    authAttempts: evidence.authAttempts, unresolvedAuthAttempts: evidence.authAttempts - accounts.length,
    httpRequests: evidence.httpRequests, seedStatus: evidence.seedStatus, titleUpdates: evidence.titleUpdates,
    titleUpdateStatus: evidence.titleUpdateStatus,
    checksCompleted: evidence.checks, retainedData: true, noCleanupAttempted: true}, null, 2));
  process.exitCode = 1;
}).finally(() => app.delete());
