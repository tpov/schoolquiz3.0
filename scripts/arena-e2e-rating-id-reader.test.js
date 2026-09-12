"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {
  ArenaE2eRatingIdReaderError,
  OPERATION,
  parseCli,
  prepareInput,
  createCliSqliteAdapter,
  createNodeSqliteAdapter,
  readRatingIdContext,
} = require("./arena-e2e-rating-id-reader");
const {attemptDocumentId} = require("../functions/arena-tickets");

const ownerUid = "player";
const ticketId = "a".repeat(64);
const attemptId = attemptDocumentId(ticketId);
const lessonId = "lesson-001";
const ratingId = "b".repeat(64);
const createdAtMs = 1_756_000_000_000;
const context = {
  runId: "arena-e2e-run-001",
  ownerUid,
  ticketId,
  attemptId,
  lessonId,
  rating: 2,
};

function payload(overrides = {}) {
  return {
    ratingId,
    ticketId,
    attemptId,
    userId: ownerUid,
    scope: "public",
    ownerUid: null,
    catalogId: "catalog-001",
    questId: "quest-001",
    sectionId: "section-001",
    themeId: "theme-001",
    lessonId,
    lessonVersion: 1,
    sourceShelf: "arena",
    rating: 2,
    ratedAtMs: createdAtMs,
    createdAtMs,
    ...overrides,
  };
}

function row(overrides = {}) {
  const rawPayload = Object.hasOwn(overrides, "payload") ? overrides.payload : JSON.stringify(payload());
  return {
    ownerUid,
    ticketId,
    attemptId,
    lessonId,
    ratingId,
    mutationId: "lesson_runner-SUBMIT_RATING-" + ratingId,
    operation: OPERATION,
    entityRef: "lesson_runner:rating:" + ratingId,
    payload: rawPayload,
    createdAtMs,
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
  return assert.rejects(action, (error) => error instanceof ArenaE2eRatingIdReaderError && error.code === code);
}

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof ArenaE2eRatingIdReaderError && error.code === code);
}

function testCliRequiresAllBoundIdentifiers() {
  const args = [
    "--db", "schoolquiz.db",
    "--run-id", context.runId,
    "--owner-uid", ownerUid,
    "--ticket-id", ticketId,
    "--attempt-id", attemptId,
    "--lesson-id", lessonId,
    "--rating", "2",
  ];
  assert.deepEqual(parseCli(args), {...context, db: "schoolquiz.db"});
  throwsCode(() => parseCli(args.slice(0, -2)), "USAGE");
  throwsCode(() => parseCli([...args, "--rating", "2"]), "USAGE");
  const wrongAttempt = [...args];
  wrongAttempt[9] = "c".repeat(64);
  throwsCode(() => parseCli(wrongAttempt), "INVALID_ARGUMENT");
}

async function testReadsExactlyOneBoundIntentThroughInjectedReadOnlyAdapter() {
  const calls = [];
  const adapter = {
    name: "fake-read-only",
    query(receivedContext, input) {
      calls.push({receivedContext, input});
      return [row()];
    },
  };
  const result = await readRatingIdContext(context, {adapter, input: fakeInput()});
  assert.equal(result.status, "CONTEXT");
  assert.equal(result.queryAdapter, "fake-read-only");
  assert.equal(result.ratingId, ratingId);
  assert.equal(result.mutationId, "lesson_runner-SUBMIT_RATING-" + ratingId);
  assert.equal(result.entityRef, "lesson_runner:rating:" + ratingId);
  assert.equal(result.rating, 2);
  assert.equal(result.documentWrites, 0);
  assert.equal(result.networkCalls, 0);
  assert.equal(result.deviceCalls, 0);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].receivedContext, context);
  assert.deepEqual(calls[0].input, fakeInput());
}

async function testRejectsAbsenceAmbiguityAndOldOrMalformedBindings() {
  const adapterFor = (rows) => ({name: "fake", query: () => rows});
  await rejectsCode(
    () => readRatingIdContext(context, {adapter: adapterFor([]), input: fakeInput()}),
    "RATING_INTENT_NOT_FOUND",
  );
  await rejectsCode(
    () => readRatingIdContext(context, {adapter: adapterFor([row(), row()]), input: fakeInput()}),
    "RATING_INTENT_AMBIGUOUS",
  );
  const oldTicket = row({ticketId: "d".repeat(64)});
  await rejectsCode(
    () => readRatingIdContext(context, {adapter: adapterFor([oldTicket]), input: fakeInput()}),
    "ROW_IDENTITY_MISMATCH",
  );
  const wrongPayload = row({payload: JSON.stringify(payload({attemptId: "e".repeat(64)}))});
  await rejectsCode(
    () => readRatingIdContext(context, {adapter: adapterFor([wrongPayload]), input: fakeInput()}),
    "PAYLOAD_MISMATCH",
  );
  const wrongMutation = row({mutationId: "lesson_runner-SUBMIT_RATING-not-the-rating"});
  await rejectsCode(
    () => readRatingIdContext(context, {adapter: adapterFor([wrongMutation]), input: fakeInput()}),
    "ROW_IDENTITY_MISMATCH",
  );
}

function makeFixtureDatabase(directory) {
  const databasePath = path.join(directory, "schoolquiz.db");
  const {DatabaseSync} = require("node:sqlite");
  const db = new DatabaseSync(databasePath);
  try {
    db.exec([
      "CREATE TABLE arena_frozen_rating_intents (",
      "owner_uid TEXT NOT NULL, ticket_id TEXT NOT NULL, attempt_id TEXT NOT NULL,",
      "lesson_id TEXT NOT NULL, rating_id TEXT NOT NULL, mutation_id TEXT NOT NULL,",
      "operation TEXT NOT NULL, payload TEXT NOT NULL, entity_ref TEXT NOT NULL,",
      "created_at_ms INTEGER NOT NULL, PRIMARY KEY(owner_uid, ticket_id)",
      ");",
    ].join(" "));
    const candidate = row();
    db.prepare([
      "INSERT INTO arena_frozen_rating_intents",
      "(owner_uid, ticket_id, attempt_id, lesson_id, rating_id, mutation_id, operation, payload, entity_ref, created_at_ms)",
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ].join(" ")).run(
      candidate.ownerUid,
      candidate.ticketId,
      candidate.attemptId,
      candidate.lessonId,
      candidate.ratingId,
      candidate.mutationId,
      candidate.operation,
      candidate.payload,
      candidate.entityRef,
      candidate.createdAtMs,
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
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "arena-rating-id-reader-"));
  try {
    const databasePath = makeFixtureDatabase(directory);
    const input = prepareInput({...context, db: databasePath});
    const before = sha256(databasePath);
    const nodeResult = await readRatingIdContext(context, {adapter: nodeAdapter, input});
    assert.equal(nodeResult.ratingId, ratingId);
    assert.equal(sha256(databasePath), before, "node:sqlite read mutated the Room copy");

    const sqlite3 = "/usr/bin/sqlite3";
    if (fs.existsSync(sqlite3)) {
      const cliResult = await readRatingIdContext(
        context,
        {adapter: createCliSqliteAdapter(sqlite3), input},
      );
      assert.equal(cliResult.ratingId, ratingId);
      assert.equal(sha256(databasePath), before, "sqlite3 -readonly mutated the Room copy");
    } else {
      console.log("SKIP sqlite3-cli integration: binary unavailable");
    }
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

(async () => {
  testCliRequiresAllBoundIdentifiers();
  await testReadsExactlyOneBoundIntentThroughInjectedReadOnlyAdapter();
  await testRejectsAbsenceAmbiguityAndOldOrMalformedBindings();
  await testRuntimeAdaptersAreReadOnlyWhenAvailable();
  console.log("arena-e2e-rating-id-reader.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
