"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {
  PreparationPreflightError,
  ROOM_SCHEMA_IDENTITY_HASH,
  ROOM_SCHEMA_VERSION,
  readPreparationPreflight,
} = require("./arena-e2e-preparation-preflight-reader");

const OWNER = "preflight-owner";

function ownerHash() {
  return createHash("sha256").update(OWNER).digest("hex");
}

function token(owner) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return [encode({alg: "none"}), encode({sub: owner, user_id: owner}), "signature"].join(".");
}

function authXml(owner) {
  const cached = {
    type: "com.google.firebase.auth.internal.DefaultFirebaseUser",
    userInfos: [JSON.stringify({userId: owner})],
    cachedTokenState: JSON.stringify({access_token: token(owner), refresh_token: "refresh-token-for-test"}),
  };
  return "<?xml version=\"1.0\"?><map><string name=\"com.google.firebase.auth.FIREBASE_USER\">" +
    JSON.stringify(cached).replace(/"/g, "&quot;") + "</string></map>";
}

function rows(overrides = {}) {
  return {
    schema: {user_version: ROOM_SCHEMA_VERSION},
    roomMaster: {identityHash: ROOM_SCHEMA_IDENTITY_HASH},
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

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof PreparationPreflightError && error.code === code);
}

function testAcceptsExactPersistedOwnerAndQuiescentRoom() {
  const value = fixture();
  try {
    const result = readPreparationPreflight(value.context, value.adapter);
    assert.equal(result.status, "PASS");
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

function testRejectsDifferentPersistedOwnerBeforeRoomResult() {
  const value = fixture({authOwner: "other-owner"});
  try {
    throwsCode(() => readPreparationPreflight(value.context, value.adapter), "ACTIVE_OWNER_MISMATCH");
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

testAcceptsExactPersistedOwnerAndQuiescentRoom();
testRejectsDifferentPersistedOwnerBeforeRoomResult();
testRejectsCachedIdentityWithoutRefreshCredential();
testRejectsAnyUnresolvedFinancialState();
console.log("arena preparation preflight reader tests: PASS");
