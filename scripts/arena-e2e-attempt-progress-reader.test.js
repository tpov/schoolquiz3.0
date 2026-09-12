"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {
  ArenaE2eAttemptProgressReaderError,
  parseCli,
  prepareInput,
  createCliSqliteAdapter,
  createNodeSqliteAdapter,
  readAttemptProgressContext,
} = require("./arena-e2e-attempt-progress-reader");
const {attemptDocumentId} = require("../functions/arena-tickets");

const ownerUid = "owner-sentinel-must-not-leak";
const ticketId = "a".repeat(64);
const attemptId = attemptDocumentId(ticketId);
const snapshotSha256 = "b".repeat(64);
const questionId = "question-sentinel-must-not-leak";
const answerSecret = "answer-sentinel-must-not-leak";
const context = {runId: "arena-e2e-run-001", ownerUid, ticketId, attemptId};

function checkpoint(overrides = {}) {
  const answer = {kind: "choice", selected: answerSecret};
  return {
    schemaVersion: 1,
    ownerUid,
    ticketId,
    attemptId,
    snapshotSha256,
    sessionMode: "LEARNING",
    seed: 42,
    phase: "FEEDBACK",
    indexInPool: 0,
    questionStartedAtMs: 1_756_000_000_000,
    deadlineMs: 1_756_000_060_000,
    currentDraft: answer,
    pendingFeedback: {questionId, codeAnswerIndex: 0, answer},
    acceptedAnswers: [],
    chargeClaims: "NN",
    endedAtMs: null,
    ...overrides,
  };
}

function row(overrides = {}) {
  return {
    ownerUid,
    ticketId,
    attemptId,
    snapshotSha256,
    serverStatus: "RESERVED",
    localUse: "LAUNCHED",
    decision: "NONE",
    terminalIsNull: true,
    runnerCheckpointJson: JSON.stringify(checkpoint()),
    runnerCheckpointRevision: 1,
    ...overrides,
  };
}

function fakeInput() {
  return {dbPath: "/static/schoolquiz.db", dbBytes: 1, wal: null, shm: null};
}

function sha256(filePath) {
  return createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function rejectsCode(action, code) {
  return assert.rejects(action, (error) => error instanceof ArenaE2eAttemptProgressReaderError && error.code === code);
}

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof ArenaE2eAttemptProgressReaderError && error.code === code);
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

async function testProjectsOnlySafeProgressFields() {
  const adapter = {name: "fake-read-only", query: () => [row()]};
  const result = await readAttemptProgressContext(context, {adapter, input: fakeInput()});
  assert.equal(result.status, "CONTEXT");
  assert.equal(result.documentWrites, 0);
  assert.equal(result.networkCalls, 0);
  assert.equal(result.deviceCalls, 0);
  assert.equal(result.storage, "Room");
  assert.equal(result.ticketId, ticketId);
  assert.equal(result.attemptId, attemptId);
  assert.deepEqual(result.progress, {
    checkpointSchemaVersion: 1,
    checkpointSha256: createHash("sha256").update(JSON.stringify(checkpoint())).digest("hex"),
    phase: "FEEDBACK",
    indexInPool: 0,
    acceptedAnswerCount: 0,
    currentDraftPresent: true,
    pendingFeedbackPresent: true,
    pendingFeedbackIndex: 0,
    isCompleted: false,
  });
  assert.equal(Object.hasOwn(result.progress, "currentDraft"), false);
  assert.equal(Object.hasOwn(result.progress, "pendingFeedback"), false);
  const serialized = JSON.stringify(result);
  for (const secret of [ownerUid, questionId, answerSecret, row().runnerCheckpointJson]) {
    assert.ok(!serialized.includes(secret), "safe context leaks " + secret);
  }
}

async function testRejectsAbsentAmbiguousAndInvalidRows() {
  const adapterFor = (rows) => ({name: "fake", query: () => rows});
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor([]), input: fakeInput()}),
    "ATTEMPT_PROGRESS_NOT_FOUND",
  );
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor([row(), row()]), input: fakeInput()}),
    "ATTEMPT_PROGRESS_AMBIGUOUS",
  );
  for (const badRow of [
    row({serverStatus: "SETTLED"}),
    row({localUse: "UNUSED"}),
    row({decision: "SETTLE"}),
    row({terminalIsNull: false}),
  ]) {
    await rejectsCode(
      () => readAttemptProgressContext(context, {adapter: adapterFor([badRow]), input: fakeInput()}),
      "ROW_IDENTITY_MISMATCH",
    );
  }
  await rejectsCode(
    () => readAttemptProgressContext(context, {
      adapter: adapterFor([row({snapshotSha256: "not-a-hash"})]),
      input: fakeInput(),
    }),
    "CHECKPOINT_MISMATCH",
  );
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor([row({runnerCheckpointRevision: 0})]), input: fakeInput()}),
    "INVALID_SNAPSHOT",
  );
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor([row({runnerCheckpointRevision: -1})]), input: fakeInput()}),
    "INVALID_SNAPSHOT",
  );
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor([row({runnerCheckpointJson: ""})]), input: fakeInput()}),
    "CHECKPOINT_MISSING",
  );
}

async function testRejectsMalformedOrMismatchedCheckpoint() {
  const adapterFor = (candidate) => ({name: "fake", query: () => [candidate]});
  const malformed = row({runnerCheckpointJson: "not-json"});
  await rejectsCode(
    () => readAttemptProgressContext(context, {adapter: adapterFor(malformed), input: fakeInput()}),
    "CHECKPOINT_MISMATCH",
  );
  for (const badCheckpoint of [
    checkpoint({ownerUid: "other"}),
    checkpoint({ticketId: "c".repeat(64)}),
    checkpoint({attemptId: "d".repeat(64)}),
    checkpoint({snapshotSha256: "e".repeat(64)}),
    checkpoint({phase: "ANSWERING"}),
    checkpoint({indexInPool: 1}),
    checkpoint({acceptedAnswers: [{}]}),
    checkpoint({currentDraft: null}),
    checkpoint({pendingFeedback: null}),
    checkpoint({endedAtMs: 1}),
    {...checkpoint(), unexpected: true},
  ]) {
    await rejectsCode(
      () => readAttemptProgressContext(context, {
        adapter: adapterFor(row({runnerCheckpointJson: JSON.stringify(badCheckpoint)})),
        input: fakeInput(),
      }),
      "CHECKPOINT_MISMATCH",
    );
  }
  const mismatchedDraft = checkpoint();
  mismatchedDraft.pendingFeedback.answer = {kind: "choice", selected: "other"};
  await rejectsCode(
    () => readAttemptProgressContext(context, {
      adapter: adapterFor(row({runnerCheckpointJson: JSON.stringify(mismatchedDraft)})),
      input: fakeInput(),
    }),
    "CHECKPOINT_MISMATCH",
  );
}

function makeFixtureDatabase(directory) {
  const databasePath = path.join(directory, "schoolquiz.db");
  const {DatabaseSync} = require("node:sqlite");
  const db = new DatabaseSync(databasePath);
  try {
    db.exec([
      "CREATE TABLE arena_ticket_records (",
      "owner_uid TEXT NOT NULL, ticket_id TEXT NOT NULL, attempt_id TEXT NOT NULL,",
      "request_id TEXT NOT NULL, terms_json TEXT NOT NULL, snapshot_json TEXT, snapshot_sha256 TEXT,",
      "server_status TEXT NOT NULL, terminal_json TEXT, local_use TEXT NOT NULL, decision TEXT NOT NULL,",
      "observed_at_ms INTEGER NOT NULL, runner_checkpoint_json TEXT, runner_checkpoint_revision INTEGER NOT NULL,",
      "PRIMARY KEY(owner_uid, ticket_id)",
      ");",
    ].join(" "));
    const candidate = row();
    db.prepare([
      "INSERT INTO arena_ticket_records",
      "(owner_uid, ticket_id, attempt_id, request_id, terms_json, snapshot_json, snapshot_sha256,",
      "server_status, terminal_json, local_use, decision, observed_at_ms, runner_checkpoint_json, runner_checkpoint_revision)",
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ].join(" ")).run(
      candidate.ownerUid,
      candidate.ticketId,
      candidate.attemptId,
      "request-001",
      "{}",
      "{}",
      candidate.snapshotSha256,
      candidate.serverStatus,
      null,
      candidate.localUse,
      candidate.decision,
      1,
      candidate.runnerCheckpointJson,
      candidate.runnerCheckpointRevision,
    );
  } finally {
    db.close();
  }
  return databasePath;
}

async function testRuntimeAdaptersAreReadOnlyWhenAvailable() {
  const nodeAdapter = createNodeSqliteAdapter();
  if (!nodeAdapter) {
    console.log("SKIP node:sqlite integration: runtime unavailable");
    return;
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-attempt-progress-reader-"));
  try {
    const databasePath = makeFixtureDatabase(directory);
    const input = prepareInput({...context, db: databasePath});
    const before = sha256(databasePath);
    const nodeResult = await readAttemptProgressContext(context, {adapter: nodeAdapter, input});
    assert.equal(nodeResult.progress.phase, "FEEDBACK");
    assert.equal(sha256(databasePath), before, "node:sqlite read mutated the Room copy");

    const sqlite3 = process.env.SQLITE3_BIN || "/usr/bin/sqlite3";
    if (fs.existsSync(sqlite3)) {
      const cliResult = await readAttemptProgressContext(
        context,
        {adapter: createCliSqliteAdapter(sqlite3), input},
      );
      assert.equal(cliResult.progress.pendingFeedbackPresent, true);
      assert.equal(sha256(databasePath), before, "sqlite3 -readonly mutated the Room copy");
    } else {
      console.log("SKIP sqlite3-cli integration: binary unavailable");
    }
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

(async () => {
  testCliRequiresOneBoundRun();
  await testProjectsOnlySafeProgressFields();
  await testRejectsAbsentAmbiguousAndInvalidRows();
  await testRejectsMalformedOrMismatchedCheckpoint();
  await testRuntimeAdaptersAreReadOnlyWhenAvailable();
  console.log("arena-e2e-attempt-progress-reader.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
