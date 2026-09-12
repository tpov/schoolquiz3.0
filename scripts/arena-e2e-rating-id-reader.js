"use strict";

// Read-only extraction of the random client rating ID retained by the Arena route.  This is
// deliberately local: it never opens Firebase, starts an adb command, creates files, or retries
// a mutation.  The caller supplies a static Room database snapshot and the already-bound Arena
// owner/ticket/attempt context.

const {createHash} = require("node:crypto");
const {spawnSync} = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const {TextDecoder} = require("node:util");
const {attemptDocumentId} = require("../functions/arena-tickets");

const VERSION = "arena-e2e-rating-id-reader-v1";
const OPERATION = "lesson_runner.SUBMIT_RATING";
const ENTITY_PREFIX = "lesson_runner:rating:";
const MUTATION_PREFIX = "lesson_runner-SUBMIT_RATING-";
const TABLE = "arena_frozen_rating_intents";

class ArenaE2eRatingIdReaderError extends Error {
  constructor(code, message = code) {
    super(message);
    this.code = code;
  }
}

function fail(code, message) {
  throw new ArenaE2eRatingIdReaderError(code, message);
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function text(value, name, {max = 256, documentId = false} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be a nonempty safe string");
  }
  if (documentId && (value.includes("/") || value === "." || value === "..")) {
    fail("INVALID_ARGUMENT", name + " must be a document ID");
  }
  return value;
}

function hexId(value, name) {
  text(value, name, {max: 64, documentId: true});
  if (!/^[a-f0-9]{64}$/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be a lowercase SHA-256 ID");
  }
  return value;
}

function whole(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) {
    fail("INVALID_SNAPSHOT", name + " must be a safe integer >= " + min);
  }
  return value;
}

function rating(value, name) {
  if (!/^[1-3]$/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be 1, 2, or 3");
  }
  return Number(value);
}

function optionName(key) {
  return "--" + key.replace(/[A-Z]/g, (character) => "-" + character.toLowerCase());
}

function parseCli(argv) {
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  const allowed = new Set([
    "--db",
    "--wal",
    "--shm",
    "--sqlite3",
    "--run-id",
    "--owner-uid",
    "--ticket-id",
    "--attempt-id",
    "--lesson-id",
    "--rating",
  ]);
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!allowed.has(flag) || value === undefined || value.startsWith("--")) {
      fail("USAGE", "Expected named flag/value pairs only");
    }
    const key = flag.slice(2).replace(/-([a-z])/g, (_, character) => character.toUpperCase());
    if (Object.hasOwn(values, key)) fail("USAGE", "Duplicate " + flag);
    values[key] = value;
  }
  for (const key of ["db", "ownerUid", "ticketId", "attemptId", "lessonId", "rating"]) {
    if (!Object.hasOwn(values, key)) fail("USAGE", "Missing " + optionName(key));
  }
  text(values.db, "db", {max: 4096});
  if (Object.hasOwn(values, "wal")) text(values.wal, "wal", {max: 4096});
  if (Object.hasOwn(values, "shm")) text(values.shm, "shm", {max: 4096});
  if (Object.hasOwn(values, "sqlite3")) text(values.sqlite3, "sqlite3", {max: 4096});
  if (Object.hasOwn(values, "runId")) text(values.runId, "runId", {max: 128, documentId: true});
  text(values.ownerUid, "ownerUid", {max: 256, documentId: true});
  hexId(values.ticketId, "ticketId");
  hexId(values.attemptId, "attemptId");
  if (attemptDocumentId(values.ticketId) !== values.attemptId) {
    fail("INVALID_ARGUMENT", "attemptId does not belong to ticketId");
  }
  text(values.lessonId, "lessonId", {max: 256, documentId: true});
  values.rating = rating(values.rating, "rating");
  return values;
}

function usage() {
  return [
    "node scripts/arena-e2e-rating-id-reader.js",
    "--db <schoolquiz.db> [--wal <schoolquiz.db-wal> --shm <schoolquiz.db-shm>]",
    "[--sqlite3 <binary>] [--run-id <id>]",
    "--owner-uid <uid> --ticket-id <sha256> --attempt-id <sha256>",
    "--lesson-id <id> --rating <1|2|3>",
  ].join(" ");
}

function requireRegularFile(filePath, name) {
  let stat;
  try {
    stat = fs.lstatSync(filePath);
  } catch (error) {
    if (error && error.code === "ENOENT") fail("INPUT_NOT_FOUND", name + " is absent");
    fail("INPUT_UNREADABLE", name + " cannot be inspected");
  }
  if (stat.isSymbolicLink() || !stat.isFile()) {
    fail("UNSAFE_INPUT", name + " must be a regular non-symlink file");
  }
  return stat;
}

function optionalRegularFile(filePath, name) {
  try {
    return requireRegularFile(filePath, name);
  } catch (error) {
    if (error instanceof ArenaE2eRatingIdReaderError && error.code === "INPUT_NOT_FOUND") return null;
    throw error;
  }
}

function resolveExactSidecar(dbPath, supplied, suffix, name) {
  const expected = dbPath + suffix;
  if (supplied !== undefined && path.resolve(supplied) !== expected) {
    fail("SIDECAR_NOT_ADJACENT", name + " must be named next to the database as " + path.basename(expected));
  }
  const stat = supplied === undefined
    ? optionalRegularFile(expected, name)
    : requireRegularFile(expected, name);
  return stat ? {path: expected, bytes: stat.size} : null;
}

function prepareInput(context) {
  const dbPath = path.resolve(context.db);
  const dbStat = requireRegularFile(dbPath, "database");
  const wal = resolveExactSidecar(dbPath, context.wal, "-wal", "WAL sidecar");
  const shm = resolveExactSidecar(dbPath, context.shm, "-shm", "SHM sidecar");
  if (shm && !wal) {
    fail("SIDECAR_INCOMPLETE", "SHM sidecar is present without the matching WAL sidecar");
  }
  return {dbPath, dbBytes: dbStat.size, wal, shm};
}

function sha256File(filePath) {
  const hash = createHash("sha256");
  const file = fs.openSync(filePath, "r");
  try {
    const buffer = Buffer.allocUnsafe(64 * 1024);
    for (;;) {
      const bytes = fs.readSync(file, buffer, 0, buffer.length, null);
      if (bytes === 0) break;
      hash.update(buffer.subarray(0, bytes));
    }
  } finally {
    fs.closeSync(file);
  }
  return hash.digest("hex");
}

const SELECT_SQL = [
  "SELECT owner_uid AS ownerUid, ticket_id AS ticketId, attempt_id AS attemptId,",
  "lesson_id AS lessonId, rating_id AS ratingId, mutation_id AS mutationId,",
  "operation, entity_ref AS entityRef, payload, created_at_ms AS createdAtMs",
  "FROM " + TABLE,
  "WHERE owner_uid = ? AND ticket_id = ? AND attempt_id = ? AND lesson_id = ?",
  "AND operation = '" + OPERATION + "'",
].join(" ");

function sqlQuote(value) {
  return "'" + value.replace(/'/g, "''") + "'";
}

function cliSql(context) {
  // Hex encoding makes each line parseable even if a corrupt payload contains a tab or newline.
  return [
    "SELECT hex(owner_uid), hex(ticket_id), hex(attempt_id), hex(lesson_id),",
    "hex(rating_id), hex(mutation_id), hex(operation), hex(entity_ref), hex(payload), created_at_ms",
    "FROM " + TABLE,
    "WHERE owner_uid = " + sqlQuote(context.ownerUid),
    "AND ticket_id = " + sqlQuote(context.ticketId),
    "AND attempt_id = " + sqlQuote(context.attemptId),
    "AND lesson_id = " + sqlQuote(context.lessonId),
    "AND operation = " + sqlQuote(OPERATION),
    ";",
  ].join(" ");
}

function decodeHexText(value, name) {
  if (!/^(?:[a-fA-F0-9]{2})*$/.test(value)) fail("SQLITE_OUTPUT_INVALID", name + " is not hexadecimal UTF-8");
  try {
    return new TextDecoder("utf-8", {fatal: true}).decode(Buffer.from(value, "hex"));
  } catch {
    fail("SQLITE_OUTPUT_INVALID", name + " is not valid UTF-8");
  }
}

function cliRows(stdout) {
  if (typeof stdout !== "string") fail("SQLITE_OUTPUT_INVALID", "sqlite3 did not return text");
  const lines = stdout.split(/\r?\n/).filter((line) => line.length > 0);
  return lines.map((line) => {
    const fields = line.split("|");
    if (fields.length !== 10) fail("SQLITE_OUTPUT_INVALID", "sqlite3 row has an unexpected column count");
    const createdAtMs = Number(fields[9]);
    if (!Number.isSafeInteger(createdAtMs)) fail("SQLITE_OUTPUT_INVALID", "sqlite3 created_at_ms is not a safe integer");
    return {
      ownerUid: decodeHexText(fields[0], "owner_uid"),
      ticketId: decodeHexText(fields[1], "ticket_id"),
      attemptId: decodeHexText(fields[2], "attempt_id"),
      lessonId: decodeHexText(fields[3], "lesson_id"),
      ratingId: decodeHexText(fields[4], "rating_id"),
      mutationId: decodeHexText(fields[5], "mutation_id"),
      operation: decodeHexText(fields[6], "operation"),
      entityRef: decodeHexText(fields[7], "entity_ref"),
      payload: decodeHexText(fields[8], "payload"),
      createdAtMs,
    };
  });
}

function createCliSqliteAdapter(sqlite3 = "sqlite3") {
  return {
    name: "sqlite3-cli",
    query(context, input) {
      const result = spawnSync(
        sqlite3,
        ["-readonly", "-bail", "-noheader", "-separator", "|", input.dbPath, cliSql(context)],
        {encoding: "utf8", maxBuffer: 1024 * 1024},
      );
      if (result.error) {
        const code = result.error.code === "ENOENT" ? "SQLITE_RUNTIME_UNAVAILABLE" : "SQLITE_QUERY_FAILED";
        fail(code, "sqlite3 could not be started");
      }
      if (result.status !== 0) {
        fail("SQLITE_QUERY_FAILED", String(result.stderr || "sqlite3 failed").trim() || "sqlite3 failed");
      }
      return cliRows(result.stdout);
    },
  };
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
    query(context, input) {
      let database;
      try {
        database = new DatabaseSync(input.dbPath, {readOnly: true});
        return database.prepare(SELECT_SQL).all(
          context.ownerUid,
          context.ticketId,
          context.attemptId,
          context.lessonId,
        );
      } catch (error) {
        fail("SQLITE_QUERY_FAILED", error && error.message ? error.message : "node:sqlite query failed");
      } finally {
        if (database) database.close();
      }
    },
  };
}

function defaultAdapter(context) {
  if (context.sqlite3) return createCliSqliteAdapter(context.sqlite3);
  return createNodeSqliteAdapter() || createCliSqliteAdapter();
}

function requirePayloadKeys(payload) {
  const expected = [
    "attemptId",
    "catalogId",
    "createdAtMs",
    "lessonId",
    "lessonVersion",
    "ownerUid",
    "questId",
    "ratedAtMs",
    "rating",
    "ratingId",
    "sectionId",
    "sourceShelf",
    "themeId",
    "ticketId",
    "userId",
    "scope",
  ].sort();
  const actual = Object.keys(payload).sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail("PAYLOAD_MISMATCH", "Arena rating payload has an unexpected schema");
  }
}

function parsePayload(raw) {
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    fail("PAYLOAD_MISMATCH", "Arena rating payload is not JSON");
  }
  if (!isRecord(payload)) fail("PAYLOAD_MISMATCH", "Arena rating payload is not an object");
  requirePayloadKeys(payload);
  return payload;
}

function assertPayload(payload, row, context) {
  if (payload.ratingId !== row.ratingId || payload.ticketId !== context.ticketId ||
      payload.attemptId !== context.attemptId || payload.userId !== context.ownerUid ||
      payload.lessonId !== context.lessonId || payload.rating !== context.rating ||
      payload.scope !== "public" || payload.ownerUid !== null || payload.sourceShelf !== "arena") {
    fail("PAYLOAD_MISMATCH", "Arena rating payload does not bind the explicit run context");
  }
  for (const key of ["catalogId", "questId", "sectionId", "themeId"]) {
    text(payload[key], "payload." + key, {max: 256, documentId: true});
  }
  whole(payload.lessonVersion, "payload.lessonVersion", 1);
  whole(payload.ratedAtMs, "payload.ratedAtMs");
  whole(payload.createdAtMs, "payload.createdAtMs");
  if (payload.ratedAtMs !== row.createdAtMs || payload.createdAtMs !== row.createdAtMs) {
    fail("PAYLOAD_MISMATCH", "Arena rating timestamps do not match the retained intent");
  }
  return payload;
}

function assertCandidate(rows, context) {
  if (!Array.isArray(rows)) fail("SQLITE_OUTPUT_INVALID", "SQLite adapter did not return rows");
  if (rows.length === 0) fail("RATING_INTENT_NOT_FOUND", "No rating intent matches the explicit run context");
  if (rows.length !== 1) fail("RATING_INTENT_AMBIGUOUS", "More than one rating intent matches the explicit run context");
  const row = rows[0];
  if (!isRecord(row)) fail("SQLITE_OUTPUT_INVALID", "SQLite row is malformed");
  if (row.ownerUid !== context.ownerUid || row.ticketId !== context.ticketId ||
      row.attemptId !== context.attemptId || row.lessonId !== context.lessonId ||
      row.operation !== OPERATION) {
    fail("ROW_IDENTITY_MISMATCH", "SQLite row does not bind the explicit run context");
  }
  hexId(row.ratingId, "row.ratingId");
  if (row.mutationId !== MUTATION_PREFIX + row.ratingId) {
    fail("ROW_IDENTITY_MISMATCH", "rating mutation ID does not derive from rating ID");
  }
  if (row.entityRef !== ENTITY_PREFIX + row.ratingId) {
    fail("ROW_IDENTITY_MISMATCH", "rating entity reference does not derive from rating ID");
  }
  whole(row.createdAtMs, "row.createdAtMs");
  if (typeof row.payload !== "string" || row.payload.length === 0) {
    fail("PAYLOAD_MISMATCH", "Arena rating payload is absent");
  }
  const payload = assertPayload(parsePayload(row.payload), row, context);
  return {row, payload};
}

async function readRatingIdContext(context, {adapter = defaultAdapter(context), input = null} = {}) {
  const prepared = input || prepareInput(context);
  if (!adapter || typeof adapter.query !== "function") {
    fail("SQLITE_RUNTIME_UNAVAILABLE", "No read-only SQLite adapter is available");
  }
  const rows = await adapter.query(context, prepared);
  const {row, payload} = assertCandidate(rows, context);
  return {
    status: "CONTEXT",
    readerVersion: VERSION,
    documentWrites: 0,
    networkCalls: 0,
    deviceCalls: 0,
    storage: "Room",
    queryAdapter: adapter.name || "injected",
    ...(context.runId ? {runId: context.runId} : {}),
    ownerUid: context.ownerUid,
    ticketId: context.ticketId,
    attemptId: context.attemptId,
    lessonId: context.lessonId,
    lessonVersion: payload.lessonVersion,
    ratingId: row.ratingId,
    mutationId: row.mutationId,
    entityRef: row.entityRef,
    rating: context.rating,
    ratedAtMs: payload.ratedAtMs,
    createdAtMs: row.createdAtMs,
    payloadSha256: createHash("sha256").update(row.payload).digest("hex"),
  };
}

function inputSummary(input) {
  return {
    database: {
      file: path.basename(input.dbPath),
      bytes: input.dbBytes,
      sha256: sha256File(input.dbPath),
    },
    wal: input.wal && {
      file: path.basename(input.wal.path),
      bytes: input.wal.bytes,
      sha256: sha256File(input.wal.path),
    },
    shm: input.shm && {
      file: path.basename(input.shm.path),
      bytes: input.shm.bytes,
      sha256: sha256File(input.shm.path),
    },
  };
}

async function main(argv = process.argv.slice(2)) {
  const context = parseCli(argv);
  if (context.help) {
    console.log(usage());
    return null;
  }
  const input = prepareInput(context);
  const snapshot = inputSummary(input);
  return {...await readRatingIdContext(context, {input}), snapshot};
}

if (require.main === module) {
  main().then((result) => {
    if (result) console.log(JSON.stringify(result, null, 2));
  }).catch((error) => {
    const code = error instanceof ArenaE2eRatingIdReaderError ? error.code : "UNEXPECTED";
    console.error(JSON.stringify({
      status: "FAIL",
      readerVersion: VERSION,
      documentWrites: 0,
      networkCalls: 0,
      deviceCalls: 0,
      code,
      message: error && error.message ? error.message : String(error),
    }));
    process.exitCode = 1;
  });
}

module.exports = {
  VERSION,
  OPERATION,
  ArenaE2eRatingIdReaderError,
  parseCli,
  usage,
  prepareInput,
  createCliSqliteAdapter,
  createNodeSqliteAdapter,
  assertCandidate,
  readRatingIdContext,
  main,
};
