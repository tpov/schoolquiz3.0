"use strict";

// DRAFT ONLY — not uploaded and intentionally write-free by default.  This is a trusted
// Admin-SDK/Firestore-emulator proof, not HTTP, Play or Auth coverage.  If reviewed, it belongs
// under /repo/scripts and may run only with an explicit approval, a reviewed source manifest,
// and the existing isolated School Quiz demo emulator.  Fixtures are retained as evidence.

const assert = require("node:assert/strict");
const {createHash, randomUUID} = require("node:crypto");
const {readFileSync} = require("node:fs");

const args = process.argv.slice(2);
assert.ok(args.every((arg) => arg === "--execute"), "Unknown harness option");

const SOURCE_FILES = Object.freeze([
  "play-developer-api.js",
  "purchase-revocations.js",
  "purchase-verification.js",
  "purchase-settlement.js",
  "purchase-notifications.js",
  "purchase-clawback.js",
]);
const PRIVATE_ROOT = "__mvp_private_harness";
const OWNER = "TZ10A_PARTIAL_REFUND_ACTUAL_SDK_V2";
const PACKAGE = "com.tpov.schoolquiz";
const T = 1_800_000_000_000;
const PLAN = Object.freeze({
  kind: "TRUSTED_FIRESTORE_SDK_NOT_HTTP",
  case: "partial-refund-race",
  sourceRoot: "/repo/functions",
  sourceFiles: SOURCE_FILES,
  privateProject: "demo-schoolquiz-mvp",
  privateHost: "schoolquiz-mvp-firebase:8080",
  authAccounts: 0,
  cleanup: false,
  retainedFixture: true,
  createOnlySeedDocuments: 3,
  maxWrittenPaths: 13,
  externalTimeoutSeconds: 180,
  note: "Requires --execute, TZ10A_APPROVED_CASE=partial-refund-race and a reviewed source manifest. " +
    "Caller must enforce an external 180-second process timeout. All source roots are redirected " +
    "below one unique, retained private-prefix document.",
});

if (!args.includes("--execute")) {
  console.log(JSON.stringify({status: "PLAN_ONLY_NO_CONNECTION", plan: PLAN}, null, 2));
} else {
  assert.equal(process.env.TZ10A_APPROVED_CASE, PLAN.case, "Separate case approval required");
  assert.equal(process.env.GCLOUD_PROJECT, PLAN.privateProject, "Only the existing private demo project is allowed");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, PLAN.privateHost,
    "Only the existing private Firestore emulator network is allowed");
  assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "Never use production credentials");
  assert.equal(process.env.FIREBASE_CONFIG, undefined, "Do not inherit another Firebase application");
  assert.equal(process.env.TZ10A_EXTERNAL_TIMEOUT_SECONDS, String(PLAN.externalTimeoutSeconds),
    "Caller must declare the external process timeout used for this run");
  execute().catch((error) => {
    console.error(JSON.stringify({
      status: "HARNESS_UNCAUGHT",
      code: error && error.code,
      message: error && error.message,
      retainedFixture: true,
    }));
    process.exitCode = 1;
  });
}

async function execute() {
  assert.ok(__filename.startsWith("/repo/scripts/"),
    "Harness must execute from the reviewed /repo/scripts mount, not a copied source tree");
  const sourceHashes = reviewedMountedSources();
  const admin = require("/repo/functions/node_modules/firebase-admin");
  const {settlePurchase} = require("/repo/functions/purchase-settlement");
  const {clawBackPurchase} = require("/repo/functions/purchase-clawback");
  const {accountIdFor, settlementId, PLAY_OUTCOME_FOUND} = require("/repo/functions/purchase-verification");
  const {refundDocId} = require("/repo/functions/purchase-notifications");
  const {LEDGER_PATH} = require("/repo/functions/currency-ledger");
  const harnessSha256 = sha256(readFileSync(__filename));
  const runId = "tz10a_prr_" + randomUUID().replaceAll("-", "");
  const uid = "tz10a-prr-user-" + runId;
  const token = "tz10a-prr-token-" + runId + "-12345678901234567890";
  const sku = "gold_pack_small";
  const id = settlementId(token);
  const refundId = refundDocId(id, 3);
  const app = admin.initializeApp({projectId: PLAN.privateProject}, "tz10a-partial-race-" + runId);
  const raw = app.firestore();
  const base = raw.collection(PRIVATE_ROOT).doc(runId);
  const physical = (relativePath) => base.path + "/" + relativePath;
  const expectedPaths = new Set([
    base.path,
    physical("configs/economy"),
    physical("users/" + uid),
    physical("purchase_settlements/" + id),
    physical("purchase_revocations/" + id),
    physical("purchase_notifications/race-one"),
    physical("purchase_notifications/race-two"),
    physical("users/" + uid + "/receipts/" + id),
    physical("purchase_audit/" + id),
    physical(LEDGER_PATH + "/" + uid + "_gold_gold_pack_" + id),
    physical("users/" + uid + "/receipts/" + refundId),
    physical("purchase_audit/" + refundId),
    physical(LEDGER_PATH + "/" + uid + "_gold_purchase_refund_" + refundId),
  ]);
  const guard = writeGuard({base, expectedPaths, uid, runId});
  const diagnostics = [];
  let phase = "preflight";

  try {
    // This read happens before even the marker is written.  A collision aborts without any seed,
    // and the only seed API used below is DocumentReference.create.
    await assertAllAbsent(raw, [...expectedPaths]);
    phase = "create-only-owned-seed";
    await createSeed(base, {owner: OWNER, runId, testFixture: runId, createdAtMs: T}, guard);
    const race = twoReaderGate(base.path);
    const db = mappedFirestore(raw, base, guard, race);
    await createSeed(raw.doc(physical("configs/economy")), {
      version: 1, goldPacks: {[sku]: 60}, premiumProducts: {}, testFixture: runId,
    }, guard);
    await createSeed(raw.doc(physical("users/" + uid)), {
      uid, gold: 20, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
      lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T, testFixture: runId,
    }, guard);

    phase = "credit-five-units";
    const play = fakePlay({uid, token, sku, account: accountIdFor(uid), outcomeFound: PLAY_OUTCOME_FOUND});
    const log = diagnosticLog(diagnostics);
    const readGold = (data) => data.gold || 0;
    const credited = await settlePurchase({
      db, playApi: play, now: T, uid, payload: {sku, purchaseToken: token}, readGold, log,
    });
    assert.equal(credited.kind, "response");
    assert.equal(credited.response.goldGranted, 300);

    phase = "simultaneous-partial-rtdn";
    race.arm();
    const results = await Promise.all([
      clawBackPurchase({db, playApi: play, now: T + 1, message: partialMessage(token, "race-one"), readGold, log}),
      clawBackPurchase({db, playApi: play, now: T + 1, message: partialMessage(token, "race-two"), readGold, log}),
    ]);
    assert.deepEqual(results.map((result) => result.kind).sort(), ["CLAWED_BACK", "RECORDED"]);
    assert.equal(race.arrivals(), 2, "both actual Firestore transaction callbacks must read before either writes");

    phase = "assert-retained-evidence";
    const user = (await raw.doc(physical("users/" + uid)).get()).data();
    const settlement = (await raw.doc(physical("purchase_settlements/" + id)).get()).data();
    assert.equal(user.gold, 140);
    assert.deepEqual(pick(settlement, [
      "status", "purchaseQuantity", "grantedQuantity", "refundedQuantity", "refundRevision",
    ]), {
      status: "CREDITED", purchaseQuantity: 5, grantedQuantity: 5, refundedQuantity: 3, refundRevision: 1,
    });
    const confirmedRetainedDocumentCount = await assertAllPresent(raw, [...expectedPaths]);
    assert.deepEqual([...guard.attemptedPaths()].sort(), [...expectedPaths].sort(),
      "The run must attempt only and all declared private fixture paths");
    console.log(JSON.stringify({
      status: "PASS",
      kind: PLAN.kind,
      case: PLAN.case,
      runId,
      retainedFixture: true,
      attemptedDistinctWritePaths: guard.attemptedPaths().size,
      confirmedRetainedDocumentCount,
      sourceSha256: sourceHashes,
      harnessSha256,
      diagnostics,
      result: {goldBefore: 20, goldGranted: 300, refundedGold: 180, goldAfter: 140, refundedQuantity: 3},
    }, null, 2));
  } catch (error) {
    console.error(JSON.stringify({
      status: "FAIL",
      kind: PLAN.kind,
      case: PLAN.case,
      runId,
      phase,
      code: error && error.code,
      message: error && error.message,
      retainedFixture: true,
      attemptedDistinctWritePaths: guard.attemptedPaths().size,
      sourceSha256: sourceHashes,
      diagnostics,
    }, null, 2));
    process.exitCode = 1;
  } finally {
    // Deliberately no recursiveDelete / cleanup.  The uniquely prefixed fixture is evidence.
    await app.delete();
  }
}

function reviewedMountedSources() {
  const manifestPath = process.env.TZ10A_SOURCE_MANIFEST;
  assert.ok(manifestPath, "TZ10A_SOURCE_MANIFEST is required for execution");
  const reviewed = JSON.parse(readFileSync(manifestPath, "utf8"));
  assert.deepEqual(Object.keys(reviewed).sort(), [...SOURCE_FILES].sort(),
    "Source manifest must pin exactly the reviewed TZ10A production files");
  const hashes = {};
  for (const file of SOURCE_FILES) {
    assert.match(reviewed[file], /^[a-f0-9]{64}$/, "Invalid reviewed SHA-256 for " + file);
    const actual = sha256(readFileSync("/repo/functions/" + file));
    assert.equal(actual, reviewed[file], "Unreviewed mounted source: " + file);
    hashes[file] = actual;
  }
  return hashes;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

async function assertAllAbsent(raw, paths) {
  for (let index = 0; index < paths.length; index += 100) {
    const snapshots = await raw.getAll(...paths.slice(index, index + 100).map((path) => raw.doc(path)));
    assert.ok(snapshots.every((snapshot) => !snapshot.exists), "Fresh private namespace collision; refuse all writes");
  }
}

async function assertAllPresent(raw, paths) {
  let count = 0;
  for (let index = 0; index < paths.length; index += 100) {
    const snapshots = await raw.getAll(...paths.slice(index, index + 100).map((path) => raw.doc(path)));
    assert.ok(snapshots.every((snapshot) => snapshot.exists),
      "Expected retained fixture document is missing after a reported PASS");
    count += snapshots.length;
  }
  return count;
}

async function createSeed(ref, value, guard) {
  guard.check(ref, value, {seed: true});
  await ref.create(value);
}

function writeGuard({base, expectedPaths, uid, runId}) {
  const written = new Set();
  const limit = PLAN.maxWrittenPaths;
  function check(ref, value, {seed = false} = {}) {
    assert.ok(ref && typeof ref.path === "string", "Write guard requires a document reference");
    assert.ok(expectedPaths.has(ref.path), "Out-of-fixture write refused: " + ref.path);
    assert.ok(ref.path === base.path || ref.path.startsWith(base.path + "/"),
      "Write must remain below this run's unique private prefix");
    if (ref.path === base.path) {
      assert.equal(seed, true, "Private marker may only be created by the seed phase");
      assert.deepEqual(pick(value, ["owner", "runId"]), {owner: OWNER, runId});
    } else if (value && typeof value === "object" && Object.hasOwn(value, "uid")) {
      assert.equal(value.uid, uid, "Cross-account fixture write refused");
    }
    written.add(ref.path);
    assert.ok(written.size <= limit, "Harness write-footprint budget exceeded");
  }
  return {check, attemptedPaths: () => new Set(written)};
}

function mappedFirestore(raw, base, guard, raceGate) {
  const nativeReferences = new WeakMap();
  function wrapRef(native) {
    const wrapper = {
      get: native.get.bind(native),
      collection: (name) => wrapCollection(native.collection(segment(name))),
      get path() { return native.path; },
      // The exercised server paths must write in a transaction.  A direct mutation cannot bypass
      // the declared-path guard through a raw DocumentReference.
      set: () => { throw new Error("Direct fixture writes are forbidden; use the guarded transaction"); },
      create: () => { throw new Error("Direct fixture writes are forbidden; use the guarded transaction"); },
      update: () => { throw new Error("Direct fixture writes are forbidden; use the guarded transaction"); },
      delete: () => { throw new Error("Direct fixture writes are forbidden; use the guarded transaction"); },
    };
    nativeReferences.set(wrapper, native);
    return Object.freeze(wrapper);
  }
  function wrapCollection(native) {
    return Object.freeze({doc: (id) => wrapRef(native.doc(segment(id)))});
  }
  function unwrap(ref) {
    const native = nativeReferences.get(ref);
    assert.ok(native, "Production code received a reference outside the private mapped database");
    return native;
  }
  return {
    collection: (name) => wrapCollection(base.collection(segment(name))),
    doc: (relativePath) => wrapRef(privateDoc(base, relativePath)),
    runTransaction: (body) => raw.runTransaction(async (native) => {
      const tx = {
        get: (ref) => native.get(unwrap(ref)),
        getAll: async (...refs) => {
          const refsNative = refs.map(unwrap);
          const snapshots = await native.getAll(...refsNative);
          await raceGate.afterReconciliationRead(refsNative);
          return snapshots;
        },
        create: (ref, value) => {
          const target = unwrap(ref); guard.check(target, value); native.create(target, value);
        },
        set: (ref, value, options) => {
          const target = unwrap(ref); guard.check(target, value); native.set(target, value, options);
        },
        update: (ref, value) => {
          const target = unwrap(ref); guard.check(target, value); native.update(target, value);
        },
        delete: (ref) => {
          const target = unwrap(ref); guard.check(target, null); native.delete(target);
        },
      };
      return body(tx);
    }),
  };
}

function segment(value) {
  const text = String(value);
  assert.match(text, /^[a-zA-Z0-9_-]+$/, "Collection or document id must be one safe segment");
  return text;
}

function privateDoc(base, relativePath) {
  const parts = String(relativePath).split("/");
  assert.equal(parts.length % 2, 0, "Mapped document path must have collection/document pairs");
  let ref = base.collection(segment(parts[0])).doc(segment(parts[1]));
  for (let index = 2; index < parts.length; index += 2) {
    ref = ref.collection(segment(parts[index])).doc(segment(parts[index + 1]));
  }
  return ref;
}

function twoReaderGate(basePath) {
  let armed = false;
  let arrivals = 0;
  let release;
  const bothReaders = new Promise((resolve) => { release = resolve; });
  return {
    arm() { armed = true; },
    arrivals: () => arrivals,
    async afterReconciliationRead(refs) {
      if (!armed || arrivals >= 2 || !isSettlementReconciliationRead(refs, basePath)) return;
      arrivals += 1;
      if (arrivals === 2) release();
      await withTimeout(bothReaders, 10_000, "Two reconciliation transactions did not read one snapshot");
    },
  };
}

function isSettlementReconciliationRead(refs, basePath) {
  const paths = refs.map((ref) => ref.path);
  return refs.length === 4 && ["purchase_settlements", "users", "configs", "purchase_revocations"].every((collection) =>
    paths.some((path) => path.startsWith(basePath + "/" + collection + "/")));
}

function fakePlay({token, sku, account, outcomeFound}) {
  let v2Calls = 0;
  const twoRefundLookups = twoPartyBarrier();
  return {
    packageName: PACKAGE,
    async getProductPurchase(requestedSku, requestedToken) {
      assert.equal(requestedSku, sku);
      assert.equal(requestedToken, token);
      return {outcome: outcomeFound, status: 200, purchase: {
        productId: sku, purchaseState: 0, consumptionState: 0, quantity: 5,
        orderId: "GPA.tz10a-race", purchaseTimeMillis: String(T), obfuscatedExternalAccountId: account,
      }};
    },
    async getProductPurchaseByToken(requestedToken) {
      assert.equal(requestedToken, token);
      v2Calls += 1;
      if (v2Calls > 1) await twoRefundLookups.arrive();
      return {outcome: outcomeFound, status: 200, purchase: {
        orderId: "GPA.tz10a-race", obfuscatedExternalAccountId: account,
        purchaseStateContext: {purchaseState: "PURCHASED"},
        productLineItem: [{productId: sku, productOfferDetails: {
          quantity: 5, refundableQuantity: v2Calls === 1 ? 5 : 2,
        }}],
      }};
    },
  };
}

function twoPartyBarrier() {
  let arrivals = 0;
  let release;
  const both = new Promise((resolve) => { release = resolve; });
  return {
    async arrive() {
      arrivals += 1;
      if (arrivals === 2) release();
      await withTimeout(both, 10_000, "Both fake V2 refund lookups did not arrive");
    },
  };
}

function partialMessage(token, messageId) {
  return {
    messageId,
    publishTime: new Date(T).toISOString(),
    data: Buffer.from(JSON.stringify({
      packageName: PACKAGE,
      eventTimeMillis: String(T),
      voidedPurchaseNotification: {
        purchaseToken: token, orderId: "GPA.tz10a-race", productType: 2, refundType: 2,
      },
    })).toString("base64"),
  };
}

function withTimeout(promise, milliseconds, message) {
  // This is a diagnostic gate only; Promise.race cannot cancel an Admin-SDK operation.  The
  // invoking shell must impose PLAN.externalTimeoutSeconds around the entire Node process.
  let timer;
  const timed = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), milliseconds);
  });
  return Promise.race([promise, timed]).finally(() => clearTimeout(timer));
}

function diagnosticLog(entries) {
  return {
    warn: (message, context) => entries.push(redactedDiagnostic("warn", message, context)),
    error: (message, context) => entries.push(redactedDiagnostic("error", message, context)),
  };
}

function redactedDiagnostic(level, message, context) {
  const safe = {};
  for (const key of ["settlementId", "sku", "outcome", "status", "code", "reason", "reasonCode"]) {
    const value = context && context[key];
    if (typeof value === "string") safe[key] = value.slice(0, 160);
    else if (typeof value === "number" || value === null) safe[key] = value;
  }
  // Source messages are classified, but keeping just the category makes the test artifact safe
  // even if a future dependency ever includes a request URL or token in an exception message.
  const category = String(message || "").split(":", 1)[0].replace(/[^a-zA-Z0-9_.-]/g, "").slice(0, 80);
  return {level, category, context: safe};
}

function pick(value, keys) {
  return Object.fromEntries(keys.map((key) => [key, value[key]]));
}
