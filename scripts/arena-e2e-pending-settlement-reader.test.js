"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {attemptDocumentId} = require("../functions/arena-tickets");
const {
  PendingSettlementReaderError,
  ROOM_SCHEMA_V14,
  ROOM_SCHEMA_V15,
  SUPPORTED_ROOM_SCHEMAS,
  parseCli,
  prepareInput,
  createNodeSqliteAdapter,
  assertPendingSettlement,
  readPendingSettlementContext,
} = require("./arena-e2e-pending-settlement-reader");

const ownerUid = "owner-sentinel-must-not-leak";
const ticketId = "a".repeat(64);
const attemptId = attemptDocumentId(ticketId);
const mutationId = "lesson_runner-SUBMIT_ATTEMPT-" + attemptId;
const entityRef = "lesson_runner:attempt:" + attemptId;
const answerSecret = "answer-sentinel-must-not-leak";
const snapshotSecret = "snapshot-sentinel-must-not-leak";
const context = {runId: "arena-pending-run-001", ownerUid, ticketId, attemptId};

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function testSupportedSchemasMatchGeneratedRoomSchemas() {
  const schemaDirectory = path.join(__dirname, "../shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase");
  const generated = [14, 15].map((version) => {
    const schema = JSON.parse(fs.readFileSync(path.join(schemaDirectory, version + ".json"), "utf8"));
    return {version: schema.database.version, identityHash: schema.database.identityHash};
  });
  assert.deepEqual(SUPPORTED_ROOM_SCHEMAS, generated);
}

function settlementBody(overrides = {}) {
  return JSON.stringify({
    ownerUid,
    userId: ownerUid,
    ticketId,
    attemptId,
    lessonId: "lesson-sentinel-must-not-leak",
    lessonVersion: 7,
    difficulty: "HARD",
    scope: "public",
    sourceShelf: "arena",
    completionKind: "COMPLETED",
    completedAtMs: 10,
    createdAtMs: 10,
    chargeClaims: "P",
    answers: [{
      questionId: "question-sentinel-must-not-leak",
      codeAnswerIndex: 0,
      answerPayload: answerSecret,
      answeredAtMs: 10,
      durationMs: 1,
      wasTimeout: false,
    }],
    served: [{questionId: "question-sentinel-must-not-leak", codeAnswerIndex: 0}],
    ...overrides,
  });
}

function rows(overrides = {}) {
  const body = overrides.body || settlementBody();
  const snapshotJson = "{\"snapshot\":\"" + snapshotSecret + "\"}";
  const command = {
    ownerUid,
    commandKind: "SETTLE",
    commandId: attemptId,
    ticketId,
    attemptId,
    bodyJson: body,
    bodySha256: sha256(body),
    state: "PENDING",
    lastFailurePresent: 0,
    createdAtMs: 10,
    ...(overrides.command || {}),
  };
  const outbox = {
    mutationId,
    ownerUid,
    operation: "lesson_runner.SUBMIT_ATTEMPT",
    payload: body,
    entityRef,
    expectedVersionIsNull: 1,
    serverVersionIsNull: 1,
    state: "WAITING",
    attemptCount: 0,
    nextRetryAtMs: 0,
    createdAtMs: 10,
    lastErrorPresent: 0,
    ...(overrides.outbox || {}),
  };
  const intent = {
    attemptId,
    ownerUid,
    mutationId,
    operation: "lesson_runner.SUBMIT_ATTEMPT",
    payload: body,
    entityRef,
    expectedVersionIsNull: 1,
    ...(overrides.intent || {}),
  };
  return {
    schema: [{user_version: 14, ...(overrides.schema || {})}],
    roomMasterTables: [{name: "room_master_table", ...(overrides.roomMasterTable || {})}],
    roomIdentity: [{identityHash: ROOM_SCHEMA_V14.identityHash, ...(overrides.roomIdentity || {})}],
    tickets: [{
      ownerUid,
      ticketId,
      attemptId,
      snapshotSha256: sha256(snapshotJson),
      serverStatus: "RESERVED",
      localUse: "LAUNCHED",
      decision: "SETTLE",
      terminalIsNull: 1,
      snapshotJson,
      checkpointPresent: 1,
      runnerCheckpointRevision: 3,
      ...(overrides.ticket || {}),
    }],
    commands: [command],
    outbox: [outbox],
    intents: [intent],
    receipts: [{count: 0, ...(overrides.receipt || {})}],
    scored: [{attemptRows: 0, answerRows: 0, ...(overrides.scored || {})}],
  };
}

function fakeInput() {
  return {dbPath: "/static/schoolquiz.db", dbBytes: 1, wal: null, shm: null};
}

function rejectsCode(action, code) {
  return assert.rejects(action, (error) => error instanceof PendingSettlementReaderError && error.code === code);
}

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof PendingSettlementReaderError && error.code === code);
}

function testCliRequiresOneBoundRun() {
  const args = [
    "--db", "schoolquiz.db",
    "--run-id", context.runId,
    "--owner-uid", ownerUid,
    "--ticket-id", ticketId,
    "--attempt-id", attemptId,
  ];
  assert.deepEqual(parseCli(args), {...context, db: "schoolquiz.db"});
  throwsCode(() => parseCli(args.slice(0, -2)), "USAGE");
  throwsCode(() => parseCli([...args, "--run-id", "second-run"]), "USAGE");
  const wrongAttempt = [...args];
  wrongAttempt[9] = "c".repeat(64);
  throwsCode(() => parseCli(wrongAttempt), "INVALID_ARGUMENT");
}

async function testProjectsOnlySafePendingFields() {
  const adapter = {name: "fake-read-only", query: () => rows()};
  const result = await readPendingSettlementContext(context, {adapter, input: fakeInput()});
  assert.equal(result.status, "CONTEXT");
  assert.equal(result.documentWrites, 0);
  assert.equal(result.networkCalls, 0);
  assert.equal(result.deviceCalls, 0);
  assert.equal(result.storage, "Room");
  assert.match(result.bindingSha256, /^[a-f0-9]{64}$/);
  assert.equal(result.roomSchemaVersion, 14);
  assert.equal(result.roomSchemaIdentityHash, ROOM_SCHEMA_V14.identityHash);
  assert.equal(result.ticketState.decision, "SETTLE");
  assert.equal(result.pendingSettlement.outboxState, "WAITING");
  assert.equal(result.pendingSettlement.localReceiptCount, 0);
  const serialized = JSON.stringify(result);
  for (const secret of [ownerUid, answerSecret, snapshotSecret, settlementBody(), entityRef, mutationId]) {
    assert.equal(serialized.includes(secret), false, "safe context leaks private content");
  }
}

function testAcceptsExactV15SchemaPair() {
  const result = assertPendingSettlement(rows({
    schema: {user_version: ROOM_SCHEMA_V15.version},
    roomIdentity: {identityHash: ROOM_SCHEMA_V15.identityHash},
  }), context);
  assert.equal(result.schemaVersion, ROOM_SCHEMA_V15.version);
  assert.equal(result.schemaIdentityHash, ROOM_SCHEMA_V15.identityHash);
}

function testRejectsBrokenLinkage() {
  const cases = [
    [rows({schema: {user_version: 15}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({schema: {user_version: 15}, roomIdentity: {identityHash: ROOM_SCHEMA_V14.identityHash}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({schema: {user_version: 14}, roomIdentity: {identityHash: ROOM_SCHEMA_V15.identityHash}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({schema: {user_version: 16}, roomIdentity: {identityHash: "f".repeat(32)}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({roomMasterTable: {name: "other_table"}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({roomIdentity: {identityHash: "different-room-schema"}}), "UNSUPPORTED_ROOM_SCHEMA"],
    [rows({ticket: {snapshotSha256: "0".repeat(64)}}), "TICKET_STATE_MISMATCH"],
    [rows({ticket: {decision: "NONE"}}), "TICKET_STATE_MISMATCH"],
    [rows({ticket: {terminalIsNull: 0}}), "TICKET_STATE_MISMATCH"],
    [rows({command: {commandId: "c".repeat(64)}}), "COMMAND_MISMATCH"],
    [rows({command: {bodySha256: "0".repeat(64)}}), "COMMAND_MISMATCH"],
    [rows({outbox: {state: "QUARANTINED"}}), "OUTBOX_MISMATCH"],
    [rows({outbox: {payload: "different"}}), "OUTBOX_MISMATCH"],
    [rows({intent: {payload: "different"}}), "INTENT_MISMATCH"],
    [rows({receipt: {count: 1}}), "RECEIPT_ALREADY_PRESENT"],
    [rows({scored: {answerRows: 1}}), "SCORED_ROWS_PRESENT"],
    [rows({body: settlementBody({attemptId: "c".repeat(64)})}), "PAYLOAD_MISMATCH"],
    [rows({body: settlementBody({difficulty: "EASY", chargeClaims: "P"})}), "PAYLOAD_MISMATCH"],
    [rows({body: settlementBody({difficulty: "HARD", chargeClaims: "S"})}), "PAYLOAD_MISMATCH"],
  ];
  for (const [candidate, code] of cases) {
    throwsCode(() => assertPendingSettlement(candidate, context), code);
  }
  assert.equal(assertPendingSettlement(rows({outbox: {state: "WAITING_PRECONDITION", attemptCount: 2, nextRetryAtMs: 15}}), context).outbox.state,
    "WAITING_PRECONDITION");
  throwsCode(() => assertPendingSettlement({...rows(), commands: []}, context), "COMMAND_NOT_FOUND");
  throwsCode(() => assertPendingSettlement({...rows(), outbox: [rows().outbox[0], rows().outbox[0]]}, context), "OUTBOX_AMBIGUOUS");
}

function makeFixtureDatabase(directory, {foreignReceipt = false, roomMaster = true, schema = ROOM_SCHEMA_V14} = {}) {
  let DatabaseSync;
  try {
    ({DatabaseSync} = require("node:sqlite"));
  } catch {
    return null;
  }
  const databasePath = path.join(directory, "schoolquiz.db");
  const db = new DatabaseSync(databasePath);
  try {
    db.exec([
      "CREATE TABLE arena_ticket_records (owner_uid TEXT, ticket_id TEXT, attempt_id TEXT, snapshot_sha256 TEXT,",
      "server_status TEXT, local_use TEXT, decision TEXT, terminal_json TEXT, snapshot_json TEXT,",
      "runner_checkpoint_json TEXT, runner_checkpoint_revision INTEGER);",
      "CREATE TABLE arena_command_journal (owner_uid TEXT, command_kind TEXT, command_id TEXT, ticket_id TEXT,",
      "attempt_id TEXT, body_json TEXT, body_sha256 TEXT, state TEXT, last_failure TEXT, created_at_ms INTEGER);",
      "CREATE TABLE outbox (mutation_id TEXT, owner_uid TEXT, operation TEXT, payload TEXT, entity_ref TEXT,",
      "expected_version INTEGER, server_version INTEGER, state TEXT, attempt_count INTEGER, next_retry_at_ms INTEGER, created_at_ms INTEGER, last_error TEXT);",
      "CREATE TABLE lesson_attempt_intents (attempt_id TEXT, owner_uid TEXT, mutation_id TEXT, operation TEXT,",
      "payload TEXT, entity_ref TEXT, expected_version INTEGER);",
      "CREATE TABLE mutation_receipts (owner_uid TEXT, mutation_id TEXT);",
      "CREATE TABLE lesson_attempts (attempt_id TEXT);",
      "CREATE TABLE question_answers (attempt_id TEXT);",
      ...(roomMaster ? [
        "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);",
        "INSERT INTO room_master_table(id, identity_hash) VALUES(42, '" + schema.identityHash + "');",
      ] : []),
      "PRAGMA user_version = " + schema.version + ";",
    ].join(" "));
    const candidate = rows();
    const ticket = candidate.tickets[0];
    const command = candidate.commands[0];
    const outbox = candidate.outbox[0];
    const intent = candidate.intents[0];
    db.prepare("INSERT INTO arena_ticket_records VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(
      ticket.ownerUid, ticket.ticketId, ticket.attemptId, ticket.snapshotSha256, ticket.serverStatus,
      ticket.localUse, ticket.decision, null, ticket.snapshotJson, "{\"checkpoint\":\"private-checkpoint\"}", ticket.runnerCheckpointRevision,
    );
    db.prepare("INSERT INTO arena_command_journal VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(
      command.ownerUid, command.commandKind, command.commandId, command.ticketId, command.attemptId,
      command.bodyJson, command.bodySha256, command.state, null, command.createdAtMs,
    );
    db.prepare("INSERT INTO outbox VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(
      outbox.mutationId, outbox.ownerUid, outbox.operation, outbox.payload, outbox.entityRef, null, null,
      outbox.state, outbox.attemptCount, outbox.nextRetryAtMs, outbox.createdAtMs, null,
    );
    db.prepare("INSERT INTO lesson_attempt_intents VALUES (?, ?, ?, ?, ?, ?, ?)").run(
      intent.attemptId, intent.ownerUid, intent.mutationId, intent.operation, intent.payload, intent.entityRef, null,
    );
    if (foreignReceipt) {
      db.prepare("INSERT INTO mutation_receipts VALUES (?, ?)").run("other-owner", mutationId);
    }
  } finally {
    db.close();
  }
  return databasePath;
}

function sha256File(filePath) {
  return createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

async function testRuntimeAdapterIsReadOnlyWhenAvailable() {
  const adapter = createNodeSqliteAdapter();
  if (!adapter) {
    console.log("SKIP node:sqlite integration: runtime unavailable");
    return;
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-pending-settlement-reader-"));
  try {
    const databasePath = makeFixtureDatabase(directory);
    const input = prepareInput({...context, db: databasePath});
    const before = sha256File(databasePath);
    const result = await readPendingSettlementContext(context, {adapter, input});
    assert.equal(result.status, "CONTEXT");
    assert.equal(result.queryAdapter, "node:sqlite");
    assert.equal(sha256File(databasePath), before);
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

async function testRuntimeAcceptsV15DatabaseReadOnlyWhenAvailable() {
  const adapter = createNodeSqliteAdapter();
  if (!adapter) {
    console.log("SKIP node:sqlite v15 integration: runtime unavailable");
    return;
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-pending-settlement-reader-v15-"));
  try {
    const databasePath = makeFixtureDatabase(directory, {schema: ROOM_SCHEMA_V15});
    const input = prepareInput({...context, db: databasePath});
    const before = sha256File(databasePath);
    const result = await readPendingSettlementContext(context, {adapter, input});
    assert.equal(result.roomSchemaVersion, ROOM_SCHEMA_V15.version);
    assert.equal(result.roomSchemaIdentityHash, ROOM_SCHEMA_V15.identityHash);
    assert.equal(sha256File(databasePath), before);
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

async function testRuntimeRejectsReceiptForSameMutationFromAnotherOwner() {
  const adapter = createNodeSqliteAdapter();
  if (!adapter) {
    console.log("SKIP node:sqlite foreign-receipt integration: runtime unavailable");
    return;
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-pending-settlement-reader-receipt-"));
  try {
    const databasePath = makeFixtureDatabase(directory, {foreignReceipt: true});
    const input = prepareInput({...context, db: databasePath});
    await rejectsCode(
      () => readPendingSettlementContext(context, {adapter, input}),
      "RECEIPT_ALREADY_PRESENT",
    );
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

async function testRuntimeRejectsV14DatabaseWithoutCandidateRoomIdentity() {
  const adapter = createNodeSqliteAdapter();
  if (!adapter) {
    console.log("SKIP node:sqlite Room-identity integration: runtime unavailable");
    return;
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-pending-settlement-reader-schema-"));
  try {
    const databasePath = makeFixtureDatabase(directory, {roomMaster: false});
    const input = prepareInput({...context, db: databasePath});
    await rejectsCode(
      () => readPendingSettlementContext(context, {adapter, input}),
      "UNSUPPORTED_ROOM_SCHEMA",
    );
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

(async () => {
  testSupportedSchemasMatchGeneratedRoomSchemas();
  testCliRequiresOneBoundRun();
  await testProjectsOnlySafePendingFields();
  testAcceptsExactV15SchemaPair();
  testRejectsBrokenLinkage();
  await testRuntimeAdapterIsReadOnlyWhenAvailable();
  await testRuntimeAcceptsV15DatabaseReadOnlyWhenAvailable();
  await testRuntimeRejectsReceiptForSameMutationFromAnotherOwner();
  await testRuntimeRejectsV14DatabaseWithoutCandidateRoomIdentity();
  console.log("arena-e2e-pending-settlement-reader.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
