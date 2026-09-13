"use strict";

// Read-only gate before an Arena financial PREPARE.  It consumes only a stopped-app Room copy
// (with adjacent WAL/SHM sidecars) and the Firebase Auth SDK's stopped-app preference copy.  It
// deliberately emits hashes/counts instead of user IDs, tokens, queued bodies, or balances.

const {createHash} = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const VERSION = "arena-e2e-preparation-preflight-reader-v1";
const ROOM_SCHEMA_VERSION = 14;
const ROOM_SCHEMA_IDENTITY_HASH = "ca5439bdc37decd3f21cb843d10bb3f4";
const AUTH_USER_KEY = "com.google.firebase.auth.FIREBASE_USER";
const MAX_FILE_BYTES = 64 * 1024 * 1024;
const MAX_AUTH_PREFS_BYTES = 1024 * 1024;

class PreparationPreflightError extends Error {
  constructor(code, message = code, details = null) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

function fail(code, message, details) {
  throw new PreparationPreflightError(code, message, details);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeText(value, name, {max = 4096, documentId = false} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be safe nonempty text");
  }
  if (documentId && (value.includes("/") || value === "." || value === "..")) {
    fail("INVALID_ARGUMENT", name + " must be a document ID");
  }
  return value;
}

function parseCli(argv) {
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  const allowed = new Set(["--db", "--wal", "--shm", "--auth-prefs", "--run-id", "--owner-uid", "--account-ref"]);
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!allowed.has(flag) || value === undefined || value.startsWith("--")) fail("USAGE", "Expected named flag/value pairs only");
    const key = flag.slice(2).replace(/-([a-z])/g, (_, character) => character.toUpperCase());
    if (Object.hasOwn(values, key)) fail("USAGE", "Duplicate " + flag);
    values[key] = value;
  }
  for (const key of ["db", "authPrefs", "runId", "ownerUid", "accountRef"]) {
    if (!Object.hasOwn(values, key)) fail("USAGE", "Missing --" + key.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase()));
  }
  for (const key of ["db", "authPrefs", "wal", "shm"]) {
    if (Object.hasOwn(values, key)) safeText(values[key], key, {max: 4096});
  }
  safeText(values.runId, "runId", {max: 128, documentId: true});
  safeText(values.ownerUid, "ownerUid", {max: 256, documentId: true});
  safeText(values.accountRef, "accountRef", {max: 256});
  return values;
}

function usage() {
  return [
    "node scripts/arena-e2e-preparation-preflight-reader.js",
    "--db <schoolquiz.db> [--wal <schoolquiz.db-wal> --shm <schoolquiz.db-shm>]",
    "--auth-prefs <Firebase Auth Store XML> --run-id <id> --owner-uid <uid> --account-ref <label>",
  ].join(" ");
}

function requireRegularFile(filePath, name, maximum) {
  let stat;
  try {
    stat = fs.lstatSync(filePath);
  } catch (error) {
    if (error && error.code === "ENOENT") fail("INPUT_NOT_FOUND", name + " is absent");
    fail("INPUT_UNREADABLE", name + " cannot be inspected");
  }
  if (stat.isSymbolicLink() || !stat.isFile() || stat.size < 1 || stat.size > maximum) {
    fail("UNSAFE_INPUT", name + " must be a bounded regular non-symlink file");
  }
  return stat;
}

function optionalRegularFile(filePath, name, maximum) {
  try {
    return requireRegularFile(filePath, name, maximum);
  } catch (error) {
    if (error instanceof PreparationPreflightError && error.code === "INPUT_NOT_FOUND") return null;
    throw error;
  }
}

function prepareInput(context) {
  const dbPath = path.resolve(context.db);
  const dbStat = requireRegularFile(dbPath, "database", MAX_FILE_BYTES);
  const expectedWal = dbPath + "-wal";
  const expectedShm = dbPath + "-shm";
  if (context.wal !== undefined && path.resolve(context.wal) !== expectedWal) fail("SIDECAR_NOT_ADJACENT", "WAL must be adjacent to database");
  if (context.shm !== undefined && path.resolve(context.shm) !== expectedShm) fail("SIDECAR_NOT_ADJACENT", "SHM must be adjacent to database");
  const wal = context.wal === undefined ? optionalRegularFile(expectedWal, "WAL", MAX_FILE_BYTES) : requireRegularFile(expectedWal, "WAL", MAX_FILE_BYTES);
  const shm = context.shm === undefined ? optionalRegularFile(expectedShm, "SHM", MAX_FILE_BYTES) : requireRegularFile(expectedShm, "SHM", MAX_FILE_BYTES);
  if (shm && !wal) fail("SIDECAR_INCOMPLETE", "SHM exists without WAL");
  const authPrefsPath = path.resolve(context.authPrefs);
  const authPrefsStat = requireRegularFile(authPrefsPath, "Firebase Auth preferences", MAX_AUTH_PREFS_BYTES);
  return {
    dbPath,
    authPrefsPath,
    dbBytes: dbStat.size,
    walBytes: wal ? wal.size : 0,
    shmBytes: shm ? shm.size : 0,
    authPrefsBytes: authPrefsStat.size,
  };
}

function decodeXmlText(value) {
  let invalid = false;
  const decoded = value.replace(/&(quot|amp|lt|gt|apos);|&#([0-9]+);|&#x([0-9a-fA-F]+);/g, (_, named, decimal, hex) => {
    if (named) return ({quot: "\"", amp: "&", lt: "<", gt: ">", apos: "'"})[named];
    const codePoint = Number.parseInt(decimal || hex, decimal ? 10 : 16);
    if (!Number.isSafeInteger(codePoint) || codePoint < 0 || codePoint > 0x10ffff) {
      invalid = true;
      return "";
    }
    return String.fromCodePoint(codePoint);
  });
  if (invalid || /&(?:#?[A-Za-z0-9]+);/.test(decoded)) fail("AUTH_PREFS_INVALID", "Firebase Auth preference XML is not safely decodable");
  return decoded;
}

function authPreferenceValue(xml) {
  const escapedKey = AUTH_USER_KEY.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const matcher = new RegExp("<string\\b(?=[^>]*\\bname=\\\"" + escapedKey + "\\\")[^>]*>([\\s\\S]*?)<\\/string>", "g");
  const matches = [...xml.matchAll(matcher)];
  if (matches.length !== 1) fail("AUTH_PREFS_INVALID", "Firebase Auth preference must contain exactly one cached user");
  return decodeXmlText(matches[0][1]);
}

function jwtPayload(token) {
  if (typeof token !== "string" || token.length < 16 || token.length > 32_768) fail("AUTH_PREFS_INVALID", "cached access token is invalid");
  const parts = token.split(".");
  // Firebase Auth Emulator stores an unsigned JWT as `header.payload.`.  This reader never uses
  // it to authenticate a request; it only cross-checks the persisted Firebase user against its
  // own cached claims.  Keep both nonempty segments base64url and accept an empty third segment
  // only for that emulator representation.
  if (parts.length !== 3 || !/^[A-Za-z0-9_-]+$/.test(parts[0]) || !/^[A-Za-z0-9_-]+$/.test(parts[1]) ||
      (parts[2] !== "" && !/^[A-Za-z0-9_-]+$/.test(parts[2]))) {
    fail("AUTH_PREFS_INVALID", "cached access token is not a supported JWT representation");
  }
  let payload;
  try {
    payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
  } catch {
    fail("AUTH_PREFS_INVALID", "cached access token payload is invalid");
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) fail("AUTH_PREFS_INVALID", "cached access token payload is malformed");
  return payload;
}

function assertPersistedCurrentUser(authPrefsPath, ownerUid) {
  let raw;
  try {
    raw = fs.readFileSync(authPrefsPath, "utf8");
  } catch {
    fail("AUTH_PREFS_UNREADABLE", "Firebase Auth preferences cannot be read");
  }
  const cachedText = authPreferenceValue(raw);
  let cached;
  try {
    cached = JSON.parse(cachedText);
  } catch {
    fail("AUTH_PREFS_INVALID", "cached Firebase user is not JSON");
  }
  if (!cached || typeof cached !== "object" || Array.isArray(cached) || !Array.isArray(cached.userInfos) || cached.userInfos.length !== 1 ||
      typeof cached.cachedTokenState !== "string") {
    fail("AUTH_PREFS_INVALID", "cached Firebase user has an unsupported shape");
  }
  let userInfo;
  try {
    userInfo = typeof cached.userInfos[0] === "string" ? JSON.parse(cached.userInfos[0]) : cached.userInfos[0];
  } catch {
    fail("AUTH_PREFS_INVALID", "cached Firebase user info is invalid");
  }
  if (!userInfo || typeof userInfo !== "object" || userInfo.userId !== ownerUid) {
    fail("ACTIVE_OWNER_MISMATCH", "the stopped app does not persist the requested Firebase owner");
  }
  let tokenState;
  try {
    tokenState = JSON.parse(cached.cachedTokenState);
  } catch {
    fail("AUTH_PREFS_INVALID", "cached Firebase token state is invalid");
  }
  if (!tokenState || typeof tokenState !== "object" || Array.isArray(tokenState) ||
      typeof tokenState.refresh_token !== "string" || tokenState.refresh_token.length < 8 ||
      tokenState.refresh_token.length > 32_768 || /[\u0000-\u001f\u007f]/.test(tokenState.refresh_token)) {
    fail("AUTH_PREFS_INVALID", "cached Firebase token state cannot refresh the persisted owner");
  }
  const claims = jwtPayload(tokenState?.access_token);
  if (claims.sub !== ownerUid || claims.user_id !== ownerUid) {
    fail("ACTIVE_OWNER_MISMATCH", "cached Firebase access token does not bind the requested owner");
  }
  return {cachedUser: true, accessTokenBound: true};
}

function requireCount(row, name) {
  const value = row?.count;
  if (!Number.isSafeInteger(value) || value < 0) fail("SQLITE_OUTPUT_INVALID", name + " count is invalid");
  return value;
}

function createNodeSqliteAdapter() {
  let DatabaseSync;
  try {
    ({DatabaseSync} = require("node:sqlite"));
  } catch {
    return null;
  }
  if (typeof DatabaseSync !== "function") return null;
  return {
    name: "node:sqlite",
    query(input) {
      let database;
      try {
        database = new DatabaseSync(input.dbPath, {readOnly: true});
        return {
          schema: database.prepare("PRAGMA user_version").get(),
          roomMaster: database.prepare("SELECT identity_hash AS identityHash FROM room_master_table WHERE id = 42").get(),
          outbox: database.prepare("SELECT COUNT(*) AS count FROM outbox").get(),
          commands: database.prepare(
            "SELECT COUNT(*) AS count FROM arena_command_journal " +
            "WHERE command_kind NOT IN ('PREPARE', 'CANCEL', 'SETTLE') OR state NOT IN ('ACKNOWLEDGED', 'RESOLVED_CANCELLED')",
          ).get(),
          reservedTickets: database.prepare("SELECT COUNT(*) AS count FROM arena_ticket_records WHERE server_status = 'RESERVED'").get(),
          orphanedAttempts: database.prepare(
            "SELECT COUNT(*) AS count FROM lesson_attempt_intents i WHERE i.operation = 'lesson_runner.SUBMIT_ATTEMPT' " +
            "AND NOT EXISTS (SELECT 1 FROM mutation_receipts r WHERE r.owner_uid = i.owner_uid AND r.mutation_id = i.mutation_id) " +
            "AND NOT EXISTS (SELECT 1 FROM outbox o WHERE o.owner_uid = i.owner_uid AND o.mutation_id = i.mutation_id)",
          ).get(),
          orphanedRatings: database.prepare(
            "SELECT COUNT(*) AS count FROM arena_frozen_rating_intents i " +
            "WHERE NOT EXISTS (SELECT 1 FROM mutation_receipts r WHERE r.owner_uid = i.owner_uid AND r.mutation_id = i.mutation_id) " +
            "AND NOT EXISTS (SELECT 1 FROM outbox o WHERE o.owner_uid = i.owner_uid AND o.mutation_id = i.mutation_id)",
          ).get(),
        };
      } catch {
        fail("SQLITE_QUERY_FAILED", "Room snapshot cannot be queried as the installed C1+C3 schema");
      } finally {
        if (database) database.close();
      }
    },
  };
}

function assertQuiescent(rows) {
  if (!rows || typeof rows !== "object") fail("SQLITE_OUTPUT_INVALID", "SQLite adapter returned an invalid result");
  if (rows.schema?.user_version !== ROOM_SCHEMA_VERSION || rows.roomMaster?.identityHash !== ROOM_SCHEMA_IDENTITY_HASH) {
    fail("UNSUPPORTED_ROOM_SCHEMA", "Room snapshot is not the installed C1+C3 schema");
  }
  const quiescence = {
    outboxRows: requireCount(rows.outbox, "outbox"),
    unresolvedArenaCommands: requireCount(rows.commands, "arena command"),
    reservedArenaTickets: requireCount(rows.reservedTickets, "reserved Arena ticket"),
    orphanedSubmitIntents: requireCount(rows.orphanedAttempts, "orphaned submit intent"),
    orphanedRatingIntents: requireCount(rows.orphanedRatings, "orphaned rating intent"),
  };
  if (Object.values(quiescence).some((value) => value !== 0)) {
    fail("PREPARATION_NOT_QUIESCENT", "the stopped app has unresolved financial state; recovery is required before prepare", {quiescence});
  }
  return quiescence;
}

function readPreparationPreflight(context, adapter = createNodeSqliteAdapter()) {
  if (!adapter) fail("SQLITE_UNAVAILABLE", "node:sqlite is required for the preparation preflight reader");
  const input = prepareInput(context);
  const identity = assertPersistedCurrentUser(input.authPrefsPath, context.ownerUid);
  const quiescence = assertQuiescent(adapter.query(input));
  return {
    status: "PASS",
    readerVersion: VERSION,
    sqliteAdapter: adapter.name,
    runId: context.runId,
    ownerUidSha256: sha256(context.ownerUid),
    accountRefSha256: sha256(context.accountRef),
    room: {
      schemaVersion: ROOM_SCHEMA_VERSION,
      schemaIdentityHash: ROOM_SCHEMA_IDENTITY_HASH,
      databaseBytes: input.dbBytes,
      walBytes: input.walBytes,
      shmBytes: input.shmBytes,
    },
    auth: identity,
    quiescence,
    documentWrites: 0,
  };
}

function main(argv = process.argv.slice(2)) {
  const context = parseCli(argv);
  if (context.help) {
    process.stdout.write(usage() + "\n");
    return;
  }
  try {
    process.stdout.write(JSON.stringify(readPreparationPreflight(context)) + "\n");
  } catch (error) {
    if (error instanceof PreparationPreflightError) error.context = context;
    throw error;
  }
}

function blockedResult(error) {
  const context = error?.context;
  const quiescence = error?.details?.quiescence;
  if (!(error instanceof PreparationPreflightError) || error.code !== "PREPARATION_NOT_QUIESCENT" ||
      !context || !quiescence || typeof quiescence !== "object" || Array.isArray(quiescence)) return null;
  return {
    status: "BLOCKED",
    readerVersion: VERSION,
    runId: context.runId,
    ownerUidSha256: sha256(context.ownerUid),
    accountRefSha256: sha256(context.accountRef),
    quiescence,
    documentWrites: 0,
  };
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    const code = error instanceof PreparationPreflightError ? error.code : "UNEXPECTED";
    const blocked = blockedResult(error);
    if (blocked) process.stdout.write(JSON.stringify(blocked) + "\n");
    process.stderr.write("arena preparation preflight: " + code + "\n");
    process.exitCode = 2;
  }
}

module.exports = {
  VERSION,
  ROOM_SCHEMA_VERSION,
  ROOM_SCHEMA_IDENTITY_HASH,
  PreparationPreflightError,
  parseCli,
  assertPersistedCurrentUser,
  assertQuiescent,
  readPreparationPreflight,
  blockedResult,
};
