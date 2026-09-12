"use strict";

const assert = require("node:assert/strict");
const {
  ArenaE2eEvidenceError,
  LEDGER_PATH,
  parseCli,
  readContext,
  readOracleEvidence,
  expectedRatingPayloadHash,
  mutationDocumentId,
} = require("./arena-e2e-evidence");
const {attemptDocumentId} = require("../functions/arena-tickets");

const ownerUid = "player";
const ticketId = "a".repeat(64);
const attemptId = attemptDocumentId(ticketId);
const ratingId = "arena-e2e-rating-001";
const context = {runId: "arena-e2e-run-001", ownerUid, ticketId, attemptId, ratingId, rating: 2};

function ticket() {
  return {
    schemaVersion: 1,
    uid: ownerUid,
    ticketId,
    attemptId,
    requestId: "request-001",
    mode: "SELECTED",
    difficulty: "HARD",
    lessonId: "lesson-001",
    lessonVersion: 3,
    authorUid: "author",
    priceNolics: 5,
    lifeCost: 50,
    issuedAtMs: 10,
    status: "SETTLED",
    terminal: {
      receiptId: `arena-settle:${ticketId}`,
      attemptId,
      settledAtMs: 20,
      percentScore: 75,
      consumedNolics: 5,
      consumedLifePoints: 50,
      authorCreditNolics: 4,
      sinkNolics: 1,
      royaltyReserveDeltaNolics: 0,
    },
  };
}

function event() {
  return {
    userId: ownerUid,
    ticketId,
    attemptId,
    lessonId: "lesson-001",
    lessonVersion: 3,
    activityKind: "ARENA",
    settlementReceipt: {
      receiptId: "attempt-receipt-001",
      uid: ownerUid,
      ticketId,
      attemptId,
      lessonId: "lesson-001",
      lessonVersion: 3,
      status: "SETTLED",
      completed: true,
      settledAtMs: 20,
      percentScore: 75,
      nolicsSpent: 5,
      lifePointsSpent: 150,
      plasmaPointsSpent: 100,
      reward: {nolics: 2, skillPoints: 3},
    },
  };
}

function ratingReceipt() {
  const receiptId = mutationDocumentId(ownerUid, ratingId);
  return {
    schemaVersion: 1,
    uid: ownerUid,
    mutationId: ratingId,
    operation: "RATE_LESSON",
    payloadHash: expectedRatingPayloadHash({lessonId: "lesson-001", attemptId, rating: 2}),
    decidedAtMs: 25,
    status: "APPLIED",
    result: {lessonId: "lesson-001", ratingId: `arena-rating:${receiptId}`, acceptedAtMs: 25},
  };
}

function ledger(id, fields) {
  return {id, atMs: 20, ...fields};
}

function ticketLedger() {
  return [
    ledger("ticket-nolics", {uid: ownerUid, currency: "nolics", delta: -5, reason: "arena_ticket_reserve", sourceId: ticketId}),
    ledger("ticket-life", {uid: ownerUid, currency: "standardChargePoints", delta: -50, reason: "arena_ticket_reserve", sourceId: ticketId}),
    ledger("ticket-author", {uid: "author", currency: "nolics", delta: 4, reason: "arena_author_royalty", sourceId: ticketId}),
    ledger("ticket-sink", {uid: "arena-sink:author", currency: "nolics", delta: 1, reason: "arena_royalty_sink", sourceId: ticketId}),
  ];
}

function attemptLedger() {
  return [
    ledger("attempt-nolics", {uid: ownerUid, currency: "nolics", delta: 2, reason: "attempt_reward", sourceId: attemptId}),
    ledger("attempt-skill", {uid: ownerUid, currency: "skillPoints", delta: 3, reason: "attempt_reward", sourceId: attemptId}),
    ledger("attempt-life", {uid: ownerUid, currency: "standardChargePoints", delta: -100, reason: "charge_claim", sourceId: attemptId}),
    ledger("attempt-plasma", {uid: ownerUid, currency: "plasmaChargePoints", delta: -100, reason: "charge_claim", sourceId: attemptId}),
  ];
}

function fakeDb({ticketData = ticket(), eventData = event(), ratingData = ratingReceipt(), ticketRows = ticketLedger(), attemptRows = attemptLedger()} = {}) {
  const calls = [];
  const docs = new Map([
    [`arena_entry_tickets/${ticketId}`, ticketData],
    [require("./arena-e2e-evidence").eventPath(attemptId), eventData],
    [`arena_mutations/${mutationDocumentId(ownerUid, ratingId)}`, ratingData],
  ]);
  const snapshot = (data) => ({exists: data !== undefined, data: () => data});
  return {
    calls,
    doc(path) {
      return {get: async () => { calls.push({kind: "get", path}); return snapshot(docs.get(path)); }};
    },
    collection(path) {
      assert.equal(path, LEDGER_PATH);
      return {
        where(field, operator, sourceId) {
          assert.equal(field, "sourceId"); assert.equal(operator, "==");
          return {
            limit(maximum) {
              return {
                get: async () => {
                  calls.push({kind: "query", path, sourceId, maximum});
                  const rows = sourceId === ticketId ? ticketRows : sourceId === attemptId ? attemptRows : [];
                  return {docs: rows.map((data) => ({data: () => data}))};
                },
              };
            },
          };
        },
      };
    },
  };
}

function throwsCode(action, code) {
  assert.throws(action, (error) => error instanceof ArenaE2eEvidenceError && error.code === code);
}

async function rejectsCode(action, code) {
  await assert.rejects(action, (error) => error instanceof ArenaE2eEvidenceError && error.code === code);
}

function testCliRequiresExplicitBoundIdsAndRatingPair() {
  const args = ["--run-id", context.runId, "--owner-uid", ownerUid, "--ticket-id", ticketId, "--attempt-id", attemptId,
    "--rating-id", ratingId, "--rating", "2"];
  assert.deepEqual(parseCli(args, {requireRating: true}), context);
  throwsCode(() => parseCli(args.slice(0, -2), {requireRating: true}), "USAGE");
  throwsCode(() => parseCli([...args, "--run-id", "second"], {requireRating: true}), "USAGE");
  throwsCode(() => parseCli([...args.slice(0, -1), "4"], {requireRating: true}), "INVALID_ARGUMENT");
  const mismatchedTicket = [...args];
  mismatchedTicket[5] = "b".repeat(64);
  throwsCode(() => parseCli(mismatchedTicket, {requireRating: true}), "INVALID_ARGUMENT");
}

async function testContextReaderReadsOnlyExactTicketAndEvent() {
  const db = fakeDb();
  const result = await readContext(db, context);
  assert.equal(result.status, "CONTEXT");
  assert.equal(result.documentWrites, 0);
  assert.equal(result.runId, context.runId);
  assert.equal(result.frozenPriceNolics, 5);
  assert.equal(result.event.status, "SETTLED");
  assert.deepEqual(db.calls.map((call) => call.kind), ["get", "get"]);
  assert.equal(db.calls[0].path, `arena_entry_tickets/${ticketId}`);
  assert.match(db.calls[1].path, /^result_events_public\/[a-f0-9]{2}\/events\//);
}

async function testOracleRequiresAllExactProofsAndUsesOnlyReads() {
  const db = fakeDb();
  const result = await readOracleEvidence(db, context);
  assert.equal(result.status, "PASS");
  assert.equal(result.documentWrites, 0);
  assert.equal(result.ratingReceipt.status, "APPLIED");
  assert.equal(result.ratingReceipt.payloadHash, expectedRatingPayloadHash({lessonId: "lesson-001", attemptId, rating: 2}));
  assert.equal(result.ledger.ticketRows.length, 4);
  assert.equal(result.ledger.attemptRows.length, 4);
  assert.deepEqual(db.calls.map((call) => call.kind).sort(), ["get", "get", "get", "query", "query"]);
  assert.deepEqual(db.calls.filter((call) => call.kind === "query").map((call) => call.sourceId).sort(), [attemptId, ticketId].sort());
}

async function testOracleRejectsOldOrDifferentRatingReceipt() {
  const stale = ratingReceipt();
  stale.payloadHash = expectedRatingPayloadHash({lessonId: "lesson-001", attemptId: "f".repeat(64), rating: 2});
  await rejectsCode(() => readOracleEvidence(fakeDb({ratingData: stale}), context), "RATING_RECEIPT_MISMATCH");
}

async function testOracleRejectsDuplicateOrUnexpectedLedgerEffects() {
  const duplicate = [...ticketLedger(), {...ticketLedger()[0], id: "ticket-nolics-duplicate"}];
  await rejectsCode(() => readOracleEvidence(fakeDb({ticketRows: duplicate}), context), "LEDGER_AMBIGUITY");
  const unexpected = [...attemptLedger(), ledger("attempt-unexpected", {
    uid: ownerUid, currency: "nolics", delta: 1, reason: "attempt_reward", sourceId: attemptId,
  })];
  await rejectsCode(() => readOracleEvidence(fakeDb({attemptRows: unexpected}), context), "LEDGER_AMBIGUITY");
}

async function testOracleRejectsMismatchedEventIdentity() {
  const wrong = event();
  wrong.ticketId = "b".repeat(64);
  await rejectsCode(() => readOracleEvidence(fakeDb({eventData: wrong}), context), "EVENT_IDENTITY_MISMATCH");
}

(async () => {
  testCliRequiresExplicitBoundIdsAndRatingPair();
  await testContextReaderReadsOnlyExactTicketAndEvent();
  await testOracleRequiresAllExactProofsAndUsesOnlyReads();
  await testOracleRejectsOldOrDifferentRatingReceipt();
  await testOracleRejectsDuplicateOrUnexpectedLedgerEffects();
  await testOracleRejectsMismatchedEventIdentity();
  console.log("arena-e2e-tools.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
