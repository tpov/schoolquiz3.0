"use strict";

// Read-only proof that a retained Arena attempt kept its first submitted answer
// through a process restart.  It reads an already copied Room snapshot only:
// no Firebase, adb, mutation, or source database write is allowed here.

const {createHash} = require("node:crypto");
const {spawnSync} = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const {TextDecoder} = require("node:util");
const {attemptDocumentId} = require("../functions/arena-tickets");

const VERSION = "arena-e2e-attempt-progress-reader-v1";
const TABLE = "arena_ticket_records";
const CHECKPOINT_VERSION = 1;
const CHECKPOINT_FIELDS = new Set([
  "schemaVersion",
  "ownerUid",
  "ticketId",
  "attemptId",
  "snapshotSha256",
  "sessionMode",
  "seed",
  "phase",
  "indexInPool",
  "questionStartedAtMs",
  "deadlineMs",
  "currentDraft",
  "pendingFeedback",
  "acceptedAnswers",
  "chargeClaims",
  "endedAtMs",
]);

class ArenaE2eAttemptProgressReaderError extends Error {
  constructor(code, message = code) {
    super(message);
    this.code = code;
  }
}

function fail(code, message) {
  throw new ArenaE2eAttemptProgressReaderError(code, message);
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

function safeWhole(value, name, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) {
    fail("INVALID_SNAPSHOT", name + " must be a safe integer >= " + minimum);
  }
  return value;
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
  for (const key of ["db", "runId", "ownerUid", "ticketId", "attemptId"]) {
    if (!Object.hasOwn(values, key)) fail("USAGE", "Missing " + optionName(key));
  }
  text(values.db, "db", {max: 4096});
  if (Object.hasOwn(values, "wal")) text(values.wal, "wal", {max: 4096});
  if (Object.hasOwn(values, "shm")) text(values.shm, "shm", {max: 4096});
  if (Object.hasOwn(values, "sqlite3")) text(values.sqlite3, "sqlite3", {max: 4096});
  text(values.runId, "runId", {max: 128, documentId: true});
  text(values.ownerUid, "ownerUid", {max: 256, documentId: true});
  hexId(values.ticketId, "ticketId");
  hexId(values.attemptId, "attemptId");
  if (attemptDocumentId(values.ticketId) !== values.attemptId) {
    fail("INVALID_ARGUMENT", "attemptId does not belong to ticketId");
  }
  return values;
}

function usage() {
  return [
    "node scripts/arena-e2e-attempt-progress-reader.js",
    "--db <schoolquiz.db> [--wal <schoolquiz.db-wal> --shm <schoolquiz.db-shm>]",
    "[--sqlite3 <binary>] --run-id <id> --owner-uid <uid>",
    "--ticket-id <sha256> --attempt-id <sha256>",
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
    if (error instanceof ArenaE2eAttemptProgressReaderError && error.code === "INPUT_NOT_FOUND") return null;
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
  "snapshot_sha256 AS snapshotSha256, server_status AS serverStatus, local_use AS localUse,",
  "decision, terminal_json IS NULL AS terminalIsNull,",
  "runner_checkpoint_json AS runnerCheckpointJson,",
  "runner_checkpoint_revision AS runnerCheckpointRevision",
  "FROM " + TABLE,
  "WHERE owner_uid = ? AND ticket_id = ? AND attempt_id = ?",
].join(" ");

function sqlQuote(value) {
  return "'" + value.replace(/'/g, "''") + "'";
}

function cliSql(context) {
  // Every text field is hex encoded before the CLI writes a delimiter-separated row.
  // A malformed checkpoint cannot turn an embedded newline or pipe into extra columns.
  return [
    "SELECT hex(owner_uid), hex(ticket_id), hex(attempt_id), hex(snapshot_sha256),",
    "hex(server_status), hex(local_use), hex(decision), terminal_json IS NULL,",
    "hex(runner_checkpoint_json), runner_checkpoint_revision",
    "FROM " + TABLE,
    "WHERE owner_uid = " + sqlQuote(context.ownerUid),
    "AND ticket_id = " + sqlQuote(context.ticketId),
    "AND attempt_id = " + sqlQuote(context.attemptId),
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
    const terminalIsNull = Number(fields[7]);
    const runnerCheckpointRevision = Number(fields[9]);
    if ((terminalIsNull !== 0 && terminalIsNull !== 1) || !Number.isSafeInteger(runnerCheckpointRevision)) {
      fail("SQLITE_OUTPUT_INVALID", "sqlite3 row has invalid scalar values");
    }
    return {
      ownerUid: decodeHexText(fields[0], "owner_uid"),
      ticketId: decodeHexText(fields[1], "ticket_id"),
      attemptId: decodeHexText(fields[2], "attempt_id"),
      snapshotSha256: decodeHexText(fields[3], "snapshot_sha256"),
      serverStatus: decodeHexText(fields[4], "server_status"),
      localUse: decodeHexText(fields[5], "local_use"),
      decision: decodeHexText(fields[6], "decision"),
      terminalIsNull: terminalIsNull === 1,
      runnerCheckpointJson: decodeHexText(fields[8], "runner_checkpoint_json"),
      runnerCheckpointRevision,
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
        return database.prepare(SELECT_SQL).all(context.ownerUid, context.ticketId, context.attemptId)
          .map((row) => ({...row, terminalIsNull: row.terminalIsNull === 1}));
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

function exactKeys(value, expected, code, name) {
  if (!isRecord(value)) fail(code, name + " must be an object");
  const keys = Object.keys(value).sort();
  const wanted = [...expected].sort();
  if (keys.length !== wanted.length || keys.some((key, index) => key !== wanted[index])) {
    fail(code, name + " has an unexpected schema");
  }
}

function checkpointText(value, name, {hex = false, max = 256} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("CHECKPOINT_MISMATCH", name + " is not safe text");
  }
  if (hex && !/^[a-f0-9]{64}$/.test(value)) fail("CHECKPOINT_MISMATCH", name + " is not a SHA-256 value");
  return value;
}

function parseCheckpoint(raw, context, row) {
  let checkpoint;
  try {
    checkpoint = JSON.parse(raw);
  } catch {
    fail("CHECKPOINT_MISMATCH", "runner checkpoint is not JSON");
  }
  exactKeys(checkpoint, CHECKPOINT_FIELDS, "CHECKPOINT_MISMATCH", "runner checkpoint");
  if (checkpoint.schemaVersion !== CHECKPOINT_VERSION || checkpoint.ownerUid !== context.ownerUid ||
      checkpoint.ticketId !== context.ticketId || checkpoint.attemptId !== context.attemptId ||
      checkpoint.snapshotSha256 !== row.snapshotSha256 || !["LEARNING", "EXAM"].includes(checkpoint.sessionMode) ||
      checkpoint.phase !== "FEEDBACK" || checkpoint.indexInPool !== 0 || checkpoint.endedAtMs !== null) {
    fail("CHECKPOINT_MISMATCH", "runner checkpoint does not preserve the first-answer restart state");
  }
  checkpointText(checkpoint.snapshotSha256, "checkpoint.snapshotSha256", {hex: true});
  safeWhole(checkpoint.questionStartedAtMs, "checkpoint.questionStartedAtMs");
  safeWhole(checkpoint.deadlineMs, "checkpoint.deadlineMs");
  if (!Array.isArray(checkpoint.acceptedAnswers) || checkpoint.acceptedAnswers.length !== 0 ||
      checkpoint.currentDraft === null || !isRecord(checkpoint.currentDraft) ||
      checkpoint.pendingFeedback === null) {
    fail("CHECKPOINT_MISMATCH", "runner checkpoint lacks the saved first answer");
  }
  exactKeys(checkpoint.pendingFeedback, new Set(["questionId", "codeAnswerIndex", "answer"]),
    "CHECKPOINT_MISMATCH", "pending feedback");
  checkpointText(checkpoint.pendingFeedback.questionId, "pendingFeedback.questionId");
  if (checkpoint.pendingFeedback.codeAnswerIndex !== 0 || !isRecord(checkpoint.pendingFeedback.answer) ||
      JSON.stringify(checkpoint.pendingFeedback.answer) !== JSON.stringify(checkpoint.currentDraft)) {
    fail("CHECKPOINT_MISMATCH", "runner checkpoint does not bind the saved answer to the first question");
  }
  checkpointText(checkpoint.chargeClaims, "checkpoint.chargeClaims", {max: 20});
  return {
    checkpointSchemaVersion: checkpoint.schemaVersion,
    checkpointSha256: createHash("sha256").update(raw).digest("hex"),
    phase: checkpoint.phase,
    indexInPool: checkpoint.indexInPool,
    acceptedAnswerCount: checkpoint.acceptedAnswers.length,
    currentDraftPresent: checkpoint.currentDraft !== null,
    pendingFeedbackPresent: checkpoint.pendingFeedback !== null,
    pendingFeedbackIndex: checkpoint.pendingFeedback.codeAnswerIndex,
    isCompleted: false,
  };
}

function assertCandidate(rows, context) {
  if (!Array.isArray(rows)) fail("SQLITE_OUTPUT_INVALID", "SQLite adapter did not return rows");
  if (rows.length === 0) fail("ATTEMPT_PROGRESS_NOT_FOUND", "No Arena ticket row matches the explicit run context");
  if (rows.length !== 1) fail("ATTEMPT_PROGRESS_AMBIGUOUS", "More than one Arena ticket row matches the explicit run context");
  const row = rows[0];
  if (!isRecord(row) || row.ownerUid !== context.ownerUid || row.ticketId !== context.ticketId ||
      row.attemptId !== context.attemptId || row.serverStatus !== "RESERVED" || row.localUse !== "LAUNCHED" ||
      row.decision !== "NONE" || row.terminalIsNull !== true) {
    fail("ROW_IDENTITY_MISMATCH", "Arena ticket row is not the unfinished saved run");
  }
  checkpointText(row.snapshotSha256, "row.snapshotSha256", {hex: true});
  safeWhole(row.runnerCheckpointRevision, "row.runnerCheckpointRevision", 1);
  if (typeof row.runnerCheckpointJson !== "string" || row.runnerCheckpointJson.length === 0) {
    fail("CHECKPOINT_MISSING", "Arena ticket row has no durable runner checkpoint");
  }
  return {row, progress: parseCheckpoint(row.runnerCheckpointJson, context, row)};
}

async function readAttemptProgressContext(context, {adapter = defaultAdapter(context), input = null} = {}) {
  const prepared = input || prepareInput(context);
  if (!adapter || typeof adapter.query !== "function") {
    fail("SQLITE_RUNTIME_UNAVAILABLE", "No read-only SQLite adapter is available");
  }
  const rows = await adapter.query(context, prepared);
  const {row, progress} = assertCandidate(rows, context);
  return {
    status: "CONTEXT",
    readerVersion: VERSION,
    documentWrites: 0,
    networkCalls: 0,
    deviceCalls: 0,
    storage: "Room",
    queryAdapter: adapter.name || "injected",
    runId: context.runId,
    ticketId: context.ticketId,
    attemptId: context.attemptId,
    ticketState: {
      serverStatus: row.serverStatus,
      localUse: row.localUse,
      decision: row.decision,
      checkpointRevision: row.runnerCheckpointRevision,
      snapshotSha256: row.snapshotSha256,
    },
    progress,
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
  return {...await readAttemptProgressContext(context, {input}), snapshot};
}

if (require.main === module) {
  main().then((result) => {
    if (result) console.log(JSON.stringify(result, null, 2));
  }).catch((error) => {
    const code = error instanceof ArenaE2eAttemptProgressReaderError ? error.code : "UNEXPECTED";
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
  TABLE,
  ArenaE2eAttemptProgressReaderError,
  parseCli,
  usage,
  prepareInput,
  createCliSqliteAdapter,
  createNodeSqliteAdapter,
  assertCandidate,
  readAttemptProgressContext,
  main,
};
