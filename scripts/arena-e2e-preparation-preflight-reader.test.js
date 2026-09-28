"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {ROOM_SCHEMA_V14, ROOM_SCHEMA_V15} = require("./arena-e2e-pending-settlement-reader");
const {
  PreparationPreflightError,
  blockedResult,
  readPreparationPreflight,
} = require("./arena-e2e-preparation-preflight-reader");

const OWNER = "preflight-owner";

function ownerHash() {
  return createHash("sha256").update(OWNER).digest("hex");
}

function token(owner, {unsigned = false} = {}) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return [encode({alg: "none"}), encode({sub: owner, user_id: owner}), unsigned ? "" : "signature"].join(".");
}

function authXml(owner, options = {}) {
  const cached = {
    type: "com.google.firebase.auth.internal.DefaultFirebaseUser",
    userInfos: [JSON.stringify({userId: owner})],
    cachedTokenState: JSON.stringify({access_token: token(owner, options), refresh_token: "refresh-token-for-test"}),
  };
  return "<?xml version=\"1.0\"?><map><string name=\"com.google.firebase.auth.FIREBASE_USER\">" +
    JSON.stringify(cached).replace(/"/g, "&quot;") + "</string></map>";
}

function rows(overrides = {}) {
  return {
    schema: {user_version: ROOM_SCHEMA_V14.version},
    roomMaster: {identityHash: ROOM_SCHEMA_V14.identityHash},
    outbox: {count: 0},
    commands: {count: 0},
    reservedTickets: {count: 0},
    orphanedAttempts: {count: 0},
    orphanedRatings: {count: 0},
    ...overrides,
  };
}

function fixture({authOwner = OWNER, queryRows = rows()} = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "arena-preparation-preflight-"));
  const db = path.join(root, "schoolquiz.db");
  const authPrefs = path.join(root, "firebase-auth.xml");
  fs.writeFileSync(db, "not-opened-by-fake-adapter", {mode: 0o600});
  fs.writeFileSync(authPrefs, authXml(authOwner), {mode: 0o600});
  return {
    context: {db, authPrefs, runId: "preflight-run-001", ownerUid: OWNER, accountRef: "retained-account"},
    adapter: {name: "fake-read-only", query: () => queryRows},
    dispose() { fs.rmSync(root, {recursive: true, force: true}); },
  };
}

function makeRoomDatabase(databasePath, schema) {
  const {DatabaseSync} = require("node:sqlite");
  const db = new DatabaseSync(databasePath);
  try {
    db.exec([
      "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);",
      "CREATE TABLE outbox (owner_uid TEXT, mutation_id TEXT);",
      "CREATE TABLE arena_command_journal (command_kind TEXT, state TEXT);",
      "CREATE TABLE arena_ticket_records (server_status TEXT);",
      "CREATE TABLE lesson_attempt_intents (operation TEXT, owner_uid TEXT, mutation_id TEXT);",
      "CREATE TABLE mutation_receipts (owner_uid TEXT, mutation_id TEXT);",
      "CREATE TABLE arena_frozen_rating_intents (owner_uid TEXT, mutation_id TEXT);",
      "PRAGMA user_version = " + schema.version + ";",
    ].join(" "));
    db.prepare("INSERT INTO room_master_table(id, identity_hash) VALUES (42, ?)").run(schema.identityHash);
  } finally {
    db.close();
  }
}

function sha256File(filePath) {
  return createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof PreparationPreflightError && error.code === code);
}

function testAcceptsExactPersistedOwnerAndQuiescentRoom() {
  const value = fixture();
  try {
    const result = readPreparationPreflight(value.context, value.adapter);
    assert.equal(result.status, "PASS");
    assert.equal(result.room.schemaVersion, ROOM_SCHEMA_V14.version);
    assert.equal(result.room.schemaIdentityHash, ROOM_SCHEMA_V14.identityHash);
    assert.equal(result.ownerUidSha256, ownerHash());
    assert.equal(JSON.stringify(result).includes(OWNER), false);
    assert.deepEqual(result.quiescence, {
      outboxRows: 0,
      unresolvedArenaCommands: 0,
      reservedArenaTickets: 0,
      orphanedSubmitIntents: 0,
      orphanedRatingIntents: 0,
    });
  } finally {
    value.dispose();
  }
}

function testAcceptsV15AndRejectsUnpairedRoomSchemas() {
  const current = fixture({queryRows: rows({
    schema: {user_version: ROOM_SCHEMA_V15.version},
    roomMaster: {identityHash: ROOM_SCHEMA_V15.identityHash},
  })});
  try {
    const result = readPreparationPreflight(current.context, current.adapter);
    assert.equal(result.room.schemaVersion, ROOM_SCHEMA_V15.version);
    assert.equal(result.room.schemaIdentityHash, ROOM_SCHEMA_V15.identityHash);
  } finally {
    current.dispose();
  }

  for (const queryRows of [
    rows({schema: {user_version: ROOM_SCHEMA_V15.version}}),
    rows({schema: {user_version: ROOM_SCHEMA_V14.version}, roomMaster: {identityHash: ROOM_SCHEMA_V15.identityHash}}),
    rows({schema: {user_version: 16}, roomMaster: {identityHash: "f".repeat(32)}}),
  ]) {
    const value = fixture({queryRows});
    try {
      throwsCode(() => readPreparationPreflight(value.context, value.adapter), "UNSUPPORTED_ROOM_SCHEMA");
    } finally {
      value.dispose();
    }
  }
}

function testRejectsInvalidAdapterOutputBeforeSchemaValidation() {
  const value = fixture({queryRows: null});
  try {
    throwsCode(() => readPreparationPreflight(value.context, value.adapter), "SQLITE_OUTPUT_INVALID");
  } finally {
    value.dispose();
  }
}

function testRuntimeReadsBothSupportedSchemasWithoutWriting() {
  try {
    require("node:sqlite");
  } catch {
    console.log("SKIP node:sqlite preparation-preflight integration: runtime unavailable");
    return;
  }
  for (const schema of [ROOM_SCHEMA_V14, ROOM_SCHEMA_V15]) {
    const value = fixture();
    try {
      fs.rmSync(value.context.db);
      makeRoomDatabase(value.context.db, schema);
      const before = sha256File(value.context.db);
      const result = readPreparationPreflight(value.context);
      assert.equal(result.room.schemaVersion, schema.version);
      assert.equal(result.room.schemaIdentityHash, schema.identityHash);
      assert.deepEqual(result.quiescence, {
        outboxRows: 0,
        unresolvedArenaCommands: 0,
        reservedArenaTickets: 0,
        orphanedSubmitIntents: 0,
        orphanedRatingIntents: 0,
      });
      assert.equal(sha256File(value.context.db), before);
    } finally {
      value.dispose();
    }
  }
}

function testRejectsDifferentPersistedOwnerBeforeRoomResult() {
  const value = fixture({authOwner: "other-owner"});
  try {
    throwsCode(() => readPreparationPreflight(value.context, value.adapter), "ACTIVE_OWNER_MISMATCH");
  } finally {
    value.dispose();
  }
}

function testAcceptsFirebaseEmulatorUnsignedJwt() {
  const value = fixture({authOwner: OWNER});
  try {
    fs.writeFileSync(value.context.authPrefs, authXml(OWNER, {unsigned: true}));
    assert.equal(readPreparationPreflight(value.context, value.adapter).status, "PASS");
  } finally {
    value.dispose();
  }
}

function testRejectsCachedIdentityWithoutRefreshCredential() {
  const value = fixture();
  try {
    const raw = fs.readFileSync(value.context.authPrefs, "utf8");
    const cached = {
      type: "com.google.firebase.auth.internal.DefaultFirebaseUser",
      userInfos: [JSON.stringify({userId: OWNER})],
      cachedTokenState: JSON.stringify({access_token: token(OWNER)}),
    };
    fs.writeFileSync(value.context.authPrefs, raw.replace(/>.*<\/string>/, ">" + JSON.stringify(cached).replace(/"/g, "&quot;") + "</string>"));
    throwsCode(() => readPreparationPreflight(value.context, value.adapter), "AUTH_PREFS_INVALID");
  } finally {
    value.dispose();
  }
}

function testRejectsAnyUnresolvedFinancialState() {
  for (const field of ["outbox", "commands", "reservedTickets", "orphanedAttempts", "orphanedRatings"]) {
    const value = fixture({queryRows: rows({[field]: {count: 1}})});
    try {
      throwsCode(() => readPreparationPreflight(value.context, value.adapter), "PREPARATION_NOT_QUIESCENT");
    } finally {
      value.dispose();
    }
  }
}

function testRedactsBlockedQuiescenceForARecoveryOperator() {
  const value = fixture({queryRows: rows({reservedTickets: {count: 2}, commands: {count: 3}})});
  try {
    let error;
    try { readPreparationPreflight(value.context, value.adapter); } catch (caught) { error = caught; }
    assert.ok(error instanceof PreparationPreflightError);
    error.context = value.context;
    const blocked = blockedResult(error);
    assert.equal(blocked.status, "BLOCKED");
    assert.deepEqual(blocked.quiescence, {
      outboxRows: 0,
      unresolvedArenaCommands: 3,
      reservedArenaTickets: 2,
      orphanedSubmitIntents: 0,
      orphanedRatingIntents: 0,
    });
    assert.equal(JSON.stringify(blocked).includes(OWNER), false);
    assert.equal(blockedResult(new Error("not a preflight error")), null);
  } finally {
    value.dispose();
  }
}

testAcceptsExactPersistedOwnerAndQuiescentRoom();
testAcceptsV15AndRejectsUnpairedRoomSchemas();
testRejectsInvalidAdapterOutputBeforeSchemaValidation();
testRuntimeReadsBothSupportedSchemasWithoutWriting();
testRejectsDifferentPersistedOwnerBeforeRoomResult();
testAcceptsFirebaseEmulatorUnsignedJwt();
testRejectsCachedIdentityWithoutRefreshCredential();
testRejectsAnyUnresolvedFinancialState();
testRedactsBlockedQuiescenceForARecoveryOperator();
console.log("arena preparation preflight reader tests: PASS");
