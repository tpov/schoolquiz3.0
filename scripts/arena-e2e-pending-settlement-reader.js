"use strict";

// Read-only proof of one frozen, pending Arena SETTLE delivery. It reads only a copied Room
// database (and its adjacent WAL/SHM sidecars). It never opens adb, Firebase, or the source DB
// for writing, and its result intentionally excludes bodies, owner IDs, answers, and errors.
// It accepts only explicitly paired Room versions and identity hashes. Unknown or cross-paired
// schemas are rejected rather than silently being treated as equivalent evidence.

const {createHash} = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const {attemptDocumentId} = require("../functions/arena-tickets");

const VERSION = "arena-e2e-pending-settlement-reader-v1";
const SUBMIT_ATTEMPT = "lesson_runner.SUBMIT_ATTEMPT";
const ROOM_SCHEMA_V14 = Object.freeze({
  version: 14,
  identityHash: "ca5439bdc37decd3f21cb843d10bb3f4",
});
const ROOM_SCHEMA_V15 = Object.freeze({
  version: 15,
  identityHash: "e3a1b9e13aff4304afeba070b8895b6c",
});
const LEGACY_ROOM_SCHEMA = ROOM_SCHEMA_V14;
const SUPPORTED_ROOM_SCHEMAS = Object.freeze([ROOM_SCHEMA_V14, ROOM_SCHEMA_V15]);
const MAX_BODY_BYTES = 500_000;
const MAX_SNAPSHOT_BYTES = 1_000_000;

function identifyRoomSchema(version, identityHash) {
  if (!Number.isSafeInteger(version)) return null;
  return SUPPORTED_ROOM_SCHEMAS.find((schema) => schema.version === version && schema.identityHash === identityHash) || null;
}

class PendingSettlementReaderError extends Error {
  constructor(code, message = code) {
    super(message);
    this.code = code;
  }
}

function fail(code, message) {
  throw new PendingSettlementReaderError(code, message);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function safeText(value, name, {max = 256, documentId = false} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be safe nonempty text");
  }
  if (documentId && (value.includes("/") || value === "." || value === "..")) {
    fail("INVALID_ARGUMENT", name + " must be a document ID");
  }
  return value;
}

function hexId(value, name) {
  safeText(value, name, {max: 64, documentId: true});
  if (!/^[a-f0-9]{64}$/.test(value)) fail("INVALID_ARGUMENT", name + " must be a lowercase SHA-256 ID");
  return value;
}

function safeWhole(value, name, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) fail("INVALID_SNAPSHOT", name + " must be a safe integer");
  return value;
}

function optionName(key) {
  return "--" + key.replace(/[A-Z]/g, (character) => "-" + character.toLowerCase());
}

function parseCli(argv) {
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  const allowed = new Set(["--db", "--wal", "--shm", "--run-id", "--owner-uid", "--ticket-id", "--attempt-id"]);
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
  safeText(values.db, "db", {max: 4096});
  if (Object.hasOwn(values, "wal")) safeText(values.wal, "wal", {max: 4096});
  if (Object.hasOwn(values, "shm")) safeText(values.shm, "shm", {max: 4096});
  safeText(values.runId, "runId", {max: 128, documentId: true});
  safeText(values.ownerUid, "ownerUid", {max: 256, documentId: true});
  hexId(values.ticketId, "ticketId");
  hexId(values.attemptId, "attemptId");
  if (attemptDocumentId(values.ticketId) !== values.attemptId) {
    fail("INVALID_ARGUMENT", "attemptId does not belong to ticketId");
  }
  return values;
}

function usage() {
  return [
    "node scripts/arena-e2e-pending-settlement-reader.js",
    "--db <schoolquiz.db> [--wal <schoolquiz.db-wal> --shm <schoolquiz.db-shm>]",
    "--run-id <id> --owner-uid <uid> --ticket-id <sha256> --attempt-id <sha256>",
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
  if (stat.isSymbolicLink() || !stat.isFile()) fail("UNSAFE_INPUT", name + " must be a regular non-symlink file");
  return stat;
}

function optionalRegularFile(filePath, name) {
  try {
    return requireRegularFile(filePath, name);
  } catch (error) {
    if (error instanceof PendingSettlementReaderError && error.code === "INPUT_NOT_FOUND") return null;
    throw error;
  }
}

function resolveExactSidecar(dbPath, supplied, suffix, name) {
  const expected = dbPath + suffix;
  if (supplied !== undefined && path.resolve(supplied) !== expected) {
    fail("SIDECAR_NOT_ADJACENT", name + " must be adjacent to the database");
  }
  const stat = supplied === undefined ? optionalRegularFile(expected, name) : requireRegularFile(expected, name);
  return stat ? {path: expected, bytes: stat.size} : null;
}

function prepareInput(context) {
  const dbPath = path.resolve(context.db);
  const dbStat = requireRegularFile(dbPath, "database");
  const wal = resolveExactSidecar(dbPath, context.wal, "-wal", "WAL sidecar");
  const shm = resolveExactSidecar(dbPath, context.shm, "-shm", "SHM sidecar");
  if (shm && !wal) fail("SIDECAR_INCOMPLETE", "SHM sidecar is present without the matching WAL sidecar");
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

function expectedMutationId(attemptId) {
  return SUBMIT_ATTEMPT.replace(".", "-") + "-" + attemptId;
}

const TICKET_SQL = [
  "SELECT owner_uid AS ownerUid, ticket_id AS ticketId, attempt_id AS attemptId,",
  "snapshot_sha256 AS snapshotSha256, server_status AS serverStatus, local_use AS localUse,",
  "decision, terminal_json IS NULL AS terminalIsNull, snapshot_json AS snapshotJson,",
  "runner_checkpoint_json IS NOT NULL AS checkpointPresent, runner_checkpoint_revision AS runnerCheckpointRevision",
  "FROM arena_ticket_records WHERE owner_uid = ? AND ticket_id = ? AND attempt_id = ?",
].join(" ");

const COMMAND_SQL = [
  "SELECT owner_uid AS ownerUid, command_kind AS commandKind, command_id AS commandId,",
  "ticket_id AS ticketId, attempt_id AS attemptId, body_json AS bodyJson, body_sha256 AS bodySha256,",
  "state, last_failure IS NOT NULL AS lastFailurePresent, created_at_ms AS createdAtMs",
  "FROM arena_command_journal WHERE owner_uid = ? AND ticket_id = ? AND command_kind = 'SETTLE'",
].join(" ");

const OUTBOX_SQL = [
  "SELECT mutation_id AS mutationId, owner_uid AS ownerUid, operation, payload, entity_ref AS entityRef,",
  "expected_version IS NULL AS expectedVersionIsNull, server_version IS NULL AS serverVersionIsNull,",
  "state, attempt_count AS attemptCount, next_retry_at_ms AS nextRetryAtMs, created_at_ms AS createdAtMs,",
  "last_error IS NOT NULL AS lastErrorPresent",
  "FROM outbox WHERE mutation_id = ?",
].join(" ");

const INTENT_SQL = [
  "SELECT attempt_id AS attemptId, owner_uid AS ownerUid, mutation_id AS mutationId, operation, payload,",
  "entity_ref AS entityRef, expected_version IS NULL AS expectedVersionIsNull",
  "FROM lesson_attempt_intents WHERE attempt_id = ?",
].join(" ");

// A receipt is a delivery acknowledgement by mutation identity, not merely by the current
// owner row. The production recovery path also treats any receipt under this mutation ID as
// proof that the sender must not be recreated, so a cross-owner corrupt row must fail closed.
const RECEIPT_SQL = "SELECT COUNT(*) AS count FROM mutation_receipts WHERE mutation_id = ?";
const SCORED_SQL = [
  "SELECT (SELECT COUNT(*) FROM lesson_attempts WHERE attempt_id = ?) AS attemptRows,",
  "(SELECT COUNT(*) FROM question_answers WHERE attempt_id = ?) AS answerRows",
].join(" ");
const ROOM_MASTER_TABLE_SQL = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table'";
const ROOM_IDENTITY_SQL = "SELECT identity_hash AS identityHash FROM room_master_table WHERE id = 42";

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
        const mutationId = expectedMutationId(context.attemptId);
        const roomMasterTables = database.prepare(ROOM_MASTER_TABLE_SQL).all();
        // Do not query a missing table: represent it as an unsupported schema instead of
        // degrading the evidence result into an opaque SQLite error.
        const roomIdentity = roomMasterTables.length === 1 && roomMasterTables[0].name === "room_master_table"
          ? database.prepare(ROOM_IDENTITY_SQL).all()
          : [];
        return {
          schema: database.prepare("PRAGMA user_version").all(),
          roomMasterTables,
          roomIdentity,
          tickets: database.prepare(TICKET_SQL).all(context.ownerUid, context.ticketId, context.attemptId),
          commands: database.prepare(COMMAND_SQL).all(context.ownerUid, context.ticketId),
          outbox: database.prepare(OUTBOX_SQL).all(mutationId),
          intents: database.prepare(INTENT_SQL).all(context.attemptId),
          receipts: database.prepare(RECEIPT_SQL).all(mutationId),
          scored: database.prepare(SCORED_SQL).all(context.attemptId, context.attemptId),
        };
      } catch (_) {
        fail("SQLITE_QUERY_FAILED", "SQLite query failed");
      } finally {
        if (database) database.close();
      }
    },
  };
}

function requireSingle(rows, name, notFoundCode, ambiguousCode) {
  if (!Array.isArray(rows)) fail("SQLITE_OUTPUT_INVALID", name + " is not a row list");
  if (rows.length === 0) fail(notFoundCode, name + " is absent");
  if (rows.length !== 1) fail(ambiguousCode, name + " is ambiguous");
  if (!isRecord(rows[0])) fail("SQLITE_OUTPUT_INVALID", name + " is not a row");
  return rows[0];
}

function exactBoolean(value, name) {
  if (value === true || value === 1) return true;
  if (value === false || value === 0) return false;
  fail("SQLITE_OUTPUT_INVALID", name + " is not boolean");
}

function exactKeys(value, expected, name) {
  if (!isRecord(value)) fail("PAYLOAD_MISMATCH", name + " is not an object");
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  if (actual.length !== wanted.length || actual.some((key, index) => key !== wanted[index])) {
    fail("PAYLOAD_MISMATCH", name + " has an unexpected schema");
  }
}

function payloadText(value, name, max = 8_192) {
  if (typeof value !== "string" || value.length === 0 || Buffer.byteLength(value, "utf8") > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("PAYLOAD_MISMATCH", name + " is not safe text");
  }
  return value;
}

function payloadWhole(value, name, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) fail("PAYLOAD_MISMATCH", name + " is not a safe integer");
  return value;
}

function validateAnswers(value) {
  if (!Array.isArray(value) || value.length > 128) fail("PAYLOAD_MISMATCH", "answers is not bounded array");
  const ids = new Set();
  for (const answer of value) {
    exactKeys(answer, new Set(["questionId", "codeAnswerIndex", "answerPayload", "answeredAtMs", "durationMs", "wasTimeout"]), "answer");
    const questionId = payloadText(answer.questionId, "answer.questionId");
    const index = payloadWhole(answer.codeAnswerIndex, "answer.codeAnswerIndex");
    payloadText(answer.answerPayload, "answer.answerPayload", 64 * 1024);
    payloadWhole(answer.answeredAtMs, "answer.answeredAtMs");
    payloadWhole(answer.durationMs, "answer.durationMs");
    if (typeof answer.wasTimeout !== "boolean") fail("PAYLOAD_MISMATCH", "answer.wasTimeout is not boolean");
    const key = questionId + "\u0000" + index;
    if (ids.has(key)) fail("PAYLOAD_MISMATCH", "answers contains a duplicate question");
    ids.add(key);
  }
  return ids;
}

function validateServed(value, answerIds) {
  if (!Array.isArray(value) || value.length > 128) fail("PAYLOAD_MISMATCH", "served is not bounded array");
  const ids = new Set();
  for (const served of value) {
    exactKeys(served, new Set(["questionId", "codeAnswerIndex"]), "served row");
    const key = payloadText(served.questionId, "served.questionId") + "\u0000" +
      payloadWhole(served.codeAnswerIndex, "served.codeAnswerIndex");
    if (ids.has(key)) fail("PAYLOAD_MISMATCH", "served contains a duplicate question");
    ids.add(key);
  }
  for (const answerId of answerIds) {
    if (!ids.has(answerId)) fail("PAYLOAD_MISMATCH", "answer is absent from served");
  }
}

function payloadBinding(body, context) {
  if (Buffer.byteLength(body, "utf8") > MAX_BODY_BYTES) fail("PAYLOAD_MISMATCH", "SETTLE body exceeds the codec limit");
  let value;
  try {
    value = JSON.parse(body);
  } catch {
    fail("PAYLOAD_MISMATCH", "SETTLE body is not JSON");
  }
  const baseKeys = new Set([
    "ownerUid", "userId", "ticketId", "attemptId", "lessonId", "lessonVersion", "difficulty", "scope",
    "sourceShelf", "completionKind", "completedAtMs", "createdAtMs", "chargeClaims", "answers", "served",
  ]);
  const contextKeys = new Set(["catalogId", "questId", "sectionId", "themeId"]);
  const keys = Object.keys(value || {});
  const hasContentContext = [...contextKeys].some((key) => keys.includes(key));
  exactKeys(value, hasContentContext ? new Set([...baseKeys, ...contextKeys]) : baseKeys, "SETTLE body");
  if (value.ownerUid !== context.ownerUid || value.userId !== context.ownerUid || value.ticketId !== context.ticketId ||
      value.attemptId !== context.attemptId || value.scope !== "public" || value.sourceShelf !== "arena" ||
      value.completionKind !== "COMPLETED") {
    fail("PAYLOAD_MISMATCH", "SETTLE body does not bind the exact Arena attempt");
  }
  payloadText(value.lessonId, "lessonId");
  payloadWhole(value.lessonVersion, "lessonVersion", 1);
  if (!["EASY", "HARD"].includes(value.difficulty)) fail("PAYLOAD_MISMATCH", "difficulty is invalid");
  const completedAtMs = payloadWhole(value.completedAtMs, "completedAtMs");
  if (payloadWhole(value.createdAtMs, "createdAtMs") !== completedAtMs) fail("PAYLOAD_MISMATCH", "completion timestamps diverge");
  const allowedClaims = value.difficulty === "HARD" ? /^[.P]+$/ : /^[.S]+$/;
  if (typeof value.chargeClaims !== "string" || value.chargeClaims.length === 0 || value.chargeClaims.length > 128 ||
      !allowedClaims.test(value.chargeClaims)) fail("PAYLOAD_MISMATCH", "chargeClaims is invalid for difficulty");
  if (hasContentContext) {
    for (const key of contextKeys) payloadText(value[key], key);
  }
  validateServed(value.served, validateAnswers(value.answers));
}

function assertPendingSettlement(rows, context) {
  if (!isRecord(rows)) fail("SQLITE_OUTPUT_INVALID", "SQLite adapter did not return an object");
  const schema = requireSingle(rows.schema, "Room schema", "SQLITE_OUTPUT_INVALID", "SQLITE_OUTPUT_INVALID");
  const schemaVersion = safeWhole(schema.user_version, "schema.user_version", 1);
  const master = requireSingle(rows.roomMasterTables, "Room master table", "UNSUPPORTED_ROOM_SCHEMA", "SQLITE_OUTPUT_INVALID");
  if (master.name !== "room_master_table") {
    fail("UNSUPPORTED_ROOM_SCHEMA", "Room master table is not supported");
  }
  const roomIdentity = requireSingle(rows.roomIdentity, "Room identity", "UNSUPPORTED_ROOM_SCHEMA", "SQLITE_OUTPUT_INVALID");
  const roomSchema = identifyRoomSchema(schemaVersion, roomIdentity.identityHash);
  if (!roomSchema) fail("UNSUPPORTED_ROOM_SCHEMA", "Room schema version and identity are not an approved pair");
  const ticket = requireSingle(rows.tickets, "ticket", "TICKET_NOT_FOUND", "TICKET_AMBIGUOUS");
  if (ticket.ownerUid !== context.ownerUid || ticket.ticketId !== context.ticketId || ticket.attemptId !== context.attemptId ||
      ticket.serverStatus !== "RESERVED" || ticket.localUse !== "LAUNCHED" || ticket.decision !== "SETTLE" ||
      !exactBoolean(ticket.terminalIsNull, "ticket.terminalIsNull") ||
      !exactBoolean(ticket.checkpointPresent, "ticket.checkpointPresent")) {
    fail("TICKET_STATE_MISMATCH", "ticket is not a pending Arena settlement");
  }
  hexId(ticket.snapshotSha256, "ticket.snapshotSha256");
  const snapshot = payloadText(ticket.snapshotJson, "ticket.snapshotJson", MAX_SNAPSHOT_BYTES);
  if (sha256(snapshot) !== ticket.snapshotSha256) fail("TICKET_STATE_MISMATCH", "ticket snapshot hash is not intact");
  safeWhole(ticket.runnerCheckpointRevision, "ticket.runnerCheckpointRevision", 1);

  const command = requireSingle(rows.commands, "SETTLE command", "COMMAND_NOT_FOUND", "COMMAND_AMBIGUOUS");
  if (command.ownerUid !== context.ownerUid || command.commandKind !== "SETTLE" || command.commandId !== context.attemptId ||
      command.ticketId !== context.ticketId || command.attemptId !== context.attemptId || command.state !== "PENDING") {
    fail("COMMAND_MISMATCH", "SETTLE command does not bind the exact pending ticket");
  }
  const body = safeText(command.bodyJson, "command.bodyJson", {max: 500_000});
  hexId(command.bodySha256, "command.bodySha256");
  if (sha256(body) !== command.bodySha256) fail("COMMAND_MISMATCH", "SETTLE command body hash is not intact");
  exactBoolean(command.lastFailurePresent, "command.lastFailurePresent");
  safeWhole(command.createdAtMs, "command.createdAtMs");
  payloadBinding(body, context);

  const mutationId = expectedMutationId(context.attemptId);
  const expectedEntityRef = "lesson_runner:attempt:" + context.attemptId;
  const outbox = requireSingle(rows.outbox, "outbox row", "OUTBOX_NOT_FOUND", "OUTBOX_AMBIGUOUS");
  if (outbox.mutationId !== mutationId || outbox.ownerUid !== context.ownerUid || outbox.operation !== SUBMIT_ATTEMPT ||
      outbox.payload !== body || outbox.entityRef !== expectedEntityRef || !exactBoolean(outbox.expectedVersionIsNull, "outbox.expectedVersionIsNull") ||
      !exactBoolean(outbox.serverVersionIsNull, "outbox.serverVersionIsNull") || !["WAITING", "WAITING_PRECONDITION"].includes(outbox.state) ||
      outbox.createdAtMs !== command.createdAtMs) {
    fail("OUTBOX_MISMATCH", "outbox does not retain the frozen SETTLE delivery");
  }
  safeWhole(outbox.attemptCount, "outbox.attemptCount");
  safeWhole(outbox.nextRetryAtMs, "outbox.nextRetryAtMs");
  safeWhole(outbox.createdAtMs, "outbox.createdAtMs");
  exactBoolean(outbox.lastErrorPresent, "outbox.lastErrorPresent");

  const intent = requireSingle(rows.intents, "attempt intent", "INTENT_NOT_FOUND", "INTENT_AMBIGUOUS");
  if (intent.attemptId !== context.attemptId || intent.ownerUid !== context.ownerUid || intent.mutationId !== mutationId ||
      intent.operation !== SUBMIT_ATTEMPT || intent.payload !== body || intent.entityRef !== expectedEntityRef ||
      !exactBoolean(intent.expectedVersionIsNull, "intent.expectedVersionIsNull")) {
    fail("INTENT_MISMATCH", "first attempt intent differs from frozen SETTLE delivery");
  }

  const receipt = requireSingle(rows.receipts, "receipt count", "SQLITE_OUTPUT_INVALID", "SQLITE_OUTPUT_INVALID");
  if (safeWhole(receipt.count, "receipt.count") !== 0) fail("RECEIPT_ALREADY_PRESENT", "a local mutation receipt already exists");
  const scored = requireSingle(rows.scored, "scored row count", "SQLITE_OUTPUT_INVALID", "SQLITE_OUTPUT_INVALID");
  const attemptRows = safeWhole(scored.attemptRows, "scored.attemptRows");
  const answerRows = safeWhole(scored.answerRows, "scored.answerRows");
  if (attemptRows !== 0 || answerRows !== 0) fail("SCORED_ROWS_PRESENT", "scored rows already exist for the pending attempt");

  return {
    ticket,
    command,
    outbox,
    mutationId,
    schemaVersion: roomSchema.version,
    schemaIdentityHash: roomSchema.identityHash,
    attemptRows,
    answerRows,
  };
}

async function readPendingSettlementContext(context, {adapter = createNodeSqliteAdapter(), input = null} = {}) {
  const prepared = input || prepareInput(context);
  if (!adapter || typeof adapter.query !== "function") fail("SQLITE_RUNTIME_UNAVAILABLE", "node:sqlite is unavailable");
  const checked = assertPendingSettlement(await adapter.query(context, prepared), context);
  return {
    status: "CONTEXT",
    readerVersion: VERSION,
    documentWrites: 0,
    networkCalls: 0,
    deviceCalls: 0,
    storage: "Room",
    queryAdapter: adapter.name || "injected",
    runId: context.runId,
    bindingSha256: sha256(context.ownerUid + "\u0000" + context.ticketId + "\u0000" + context.attemptId),
    roomSchemaVersion: checked.schemaVersion,
    roomSchemaIdentityHash: checked.schemaIdentityHash,
    ticketState: {
      serverStatus: checked.ticket.serverStatus,
      localUse: checked.ticket.localUse,
      decision: checked.ticket.decision,
      checkpointRevision: checked.ticket.runnerCheckpointRevision,
      snapshotSha256: checked.ticket.snapshotSha256,
    },
    pendingSettlement: {
      commandState: checked.command.state,
      commandBodySha256: checked.command.bodySha256,
      commandLastFailurePresent: exactBoolean(checked.command.lastFailurePresent, "command.lastFailurePresent"),
      mutationIdSha256: sha256(checked.mutationId),
      outboxState: checked.outbox.state,
      outboxAttemptCount: checked.outbox.attemptCount,
      outboxNextRetryAtMs: checked.outbox.nextRetryAtMs,
      outboxLastErrorPresent: exactBoolean(checked.outbox.lastErrorPresent, "outbox.lastErrorPresent"),
      firstIntentPresent: true,
      localReceiptCount: 0,
      localScoredAttemptRows: checked.attemptRows,
      localScoredAnswerRows: checked.answerRows,
    },
  };
}

function inputSummary(input) {
  return {
    database: {file: path.basename(input.dbPath), bytes: input.dbBytes, sha256: sha256File(input.dbPath)},
    wal: input.wal && {file: path.basename(input.wal.path), bytes: input.wal.bytes, sha256: sha256File(input.wal.path)},
    shm: input.shm && {file: path.basename(input.shm.path), bytes: input.shm.bytes, sha256: sha256File(input.shm.path)},
  };
}

async function main(argv = process.argv.slice(2)) {
  const context = parseCli(argv);
  if (context.help) {
    console.log(usage());
    return null;
  }
  const input = prepareInput(context);
  return {...await readPendingSettlementContext(context, {input}), snapshot: inputSummary(input)};
}

if (require.main === module) {
  main().then((result) => {
    if (result) console.log(JSON.stringify(result, null, 2));
  }).catch((error) => {
    const code = error instanceof PendingSettlementReaderError ? error.code : "UNEXPECTED";
    console.error(JSON.stringify({
      status: "FAIL", readerVersion: VERSION, documentWrites: 0, networkCalls: 0, deviceCalls: 0,
      code, message: error instanceof PendingSettlementReaderError ? error.message : "unexpected reader failure",
    }));
    process.exitCode = 1;
  });
}

module.exports = {
  VERSION,
  ROOM_SCHEMA_V14,
  ROOM_SCHEMA_V15,
  LEGACY_ROOM_SCHEMA,
  SUPPORTED_ROOM_SCHEMAS,
  identifyRoomSchema,
  PendingSettlementReaderError,
  parseCli,
  usage,
  prepareInput,
  createNodeSqliteAdapter,
  assertPendingSettlement,
  readPendingSettlementContext,
  main,
};
