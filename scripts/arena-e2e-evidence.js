"use strict";

// Shared, deliberately read-only identity and receipt checks for the retained-installation Arena
// walkthrough. The executable tools import firebase-admin only in their `main` functions; this
// module stays pure so its acceptance rules can be tested without an emulator or credentials.

const {createHash} = require("node:crypto");
const {attemptDocumentId} = require("../functions/arena-tickets");
const {eventPath} = require("../functions/arena-entry-runtime");
const {mutationDocumentId} = require("../functions/arena-service");

const PROJECT = "demo-schoolquiz-mvp";
const FIRESTORE_EMULATOR_HOST = "schoolquiz-mvp-firebase:8080";
const LEDGER_PATH = "audit/currency_ledger/entries";
const MAX_LEDGER_ROWS_PER_SOURCE = 16;

class ArenaE2eEvidenceError extends Error {
  constructor(code, message = code) {
    super(message);
    this.name = "ArenaE2eEvidenceError";
    this.code = code;
  }
}

function fail(code, message) {
  throw new ArenaE2eEvidenceError(code, message);
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function text(value, name, {max = 256, documentId = false} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", `${name} must be a nonempty safe string`);
  }
  if (documentId && (value.includes("/") || value === "." || value === "..")) {
    fail("INVALID_ARGUMENT", `${name} must be a document ID`);
  }
  return value;
}

function hexId(value, name) {
  text(value, name, {max: 64, documentId: true});
  if (!/^[a-f0-9]{64}$/.test(value)) fail("INVALID_ARGUMENT", `${name} must be a lowercase SHA-256 ID`);
  return value;
}

function whole(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_SNAPSHOT", `${name} must be a safe integer >= ${min}`);
  return value;
}

function finite(value, name, min = 0, max = Number.MAX_SAFE_INTEGER) {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    fail("INVALID_SNAPSHOT", `${name} must be finite in range`);
  }
  return value;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function expectedRatingPayloadHash({lessonId, attemptId, rating}) {
  return sha256(JSON.stringify(["RATE_LESSON", {lessonId, attemptId, rating}]));
}

function parseCli(argv, {requireRating = false} = {}) {
  const values = {};
  const allowed = new Set(["--run-id", "--owner-uid", "--ticket-id", "--attempt-id", "--rating-id", "--rating"]);
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!allowed.has(flag) || value === undefined || value.startsWith("--")) {
      fail("USAGE", "Expected named flag/value pairs only");
    }
    const key = flag.slice(2).replace(/-([a-z])/g, (_, character) => character.toUpperCase());
    if (Object.hasOwn(values, key)) fail("USAGE", `Duplicate ${flag}`);
    values[key] = value;
  }
  for (const key of ["runId", "ownerUid", "ticketId", "attemptId"]) {
    if (!Object.hasOwn(values, key)) fail("USAGE", `Missing --${key.replace(/[A-Z]/g, (character) => `-${character.toLowerCase()}`)}`);
  }
  text(values.runId, "runId", {max: 128, documentId: true});
  text(values.ownerUid, "ownerUid", {max: 256, documentId: true});
  hexId(values.ticketId, "ticketId");
  hexId(values.attemptId, "attemptId");
  if (attemptDocumentId(values.ticketId) !== values.attemptId) {
    fail("INVALID_ARGUMENT", "attemptId does not belong to ticketId");
  }
  const hasRatingId = Object.hasOwn(values, "ratingId");
  const hasRating = Object.hasOwn(values, "rating");
  if (hasRatingId !== hasRating) fail("USAGE", "--rating-id and --rating must be supplied together");
  if (requireRating && !hasRatingId) fail("USAGE", "Missing --rating-id and --rating");
  if (hasRatingId) {
    text(values.ratingId, "ratingId", {max: 256});
    if (!/^[1-3]$/.test(values.rating)) fail("INVALID_ARGUMENT", "rating must be 1, 2, or 3");
    values.rating = Number(values.rating);
  }
  return values;
}

function checkEnvironment(env = process.env) {
  if (env.GCLOUD_PROJECT !== PROJECT || env.FIRESTORE_EMULATOR_HOST !== FIRESTORE_EMULATOR_HOST ||
      env.GOOGLE_APPLICATION_CREDENTIALS !== undefined) {
    fail("UNSAFE_ENVIRONMENT", "This tool only reads the private demo Firestore emulator without credentials");
  }
}

function assertTicket(ticket, context) {
  if (!isRecord(ticket)) fail("TICKET_NOT_FOUND", "Exact Arena ticket is absent");
  if (ticket.uid !== context.ownerUid || ticket.ticketId !== context.ticketId || ticket.attemptId !== context.attemptId) {
    fail("TICKET_IDENTITY_MISMATCH", "Ticket does not match the explicit owner/ticket/attempt context");
  }
  if (attemptDocumentId(ticket.ticketId) !== ticket.attemptId) {
    fail("TICKET_IDENTITY_MISMATCH", "Ticket carries a noncanonical attempt ID");
  }
  if (!["RESERVED", "SETTLED", "CANCELLED"].includes(ticket.status)) fail("INVALID_SNAPSHOT", "Unknown ticket status");
  if (!["SELECTED", "RANDOM"].includes(ticket.mode) || !["EASY", "HARD"].includes(ticket.difficulty)) {
    fail("INVALID_SNAPSHOT", "Invalid ticket mode or difficulty");
  }
  text(ticket.lessonId, "ticket.lessonId", {max: 256, documentId: true});
  text(ticket.authorUid, "ticket.authorUid", {max: 256, documentId: true});
  whole(ticket.lessonVersion, "ticket.lessonVersion", 1);
  whole(ticket.priceNolics, "ticket.priceNolics");
  whole(ticket.lifeCost, "ticket.lifeCost", 1);
  whole(ticket.issuedAtMs, "ticket.issuedAtMs");
  return ticket;
}

function settledTerminal(ticket) {
  if (ticket.status !== "SETTLED") fail("TICKET_NOT_SETTLED", "Ticket is not SETTLED");
  const terminal = ticket.terminal;
  if (!isRecord(terminal)) fail("INVALID_SNAPSHOT", "SETTLED ticket has no terminal receipt");
  if (terminal.receiptId !== `arena-settle:${ticket.ticketId}` || terminal.attemptId !== ticket.attemptId) {
    fail("SETTLEMENT_IDENTITY_MISMATCH", "Ticket terminal does not bind this ticket and attempt");
  }
  whole(terminal.settledAtMs, "ticket.terminal.settledAtMs");
  finite(terminal.percentScore, "ticket.terminal.percentScore", 0, 100);
  for (const field of ["consumedNolics", "consumedLifePoints", "authorCreditNolics", "sinkNolics", "royaltyReserveDeltaNolics"]) {
    whole(terminal[field], `ticket.terminal.${field}`);
  }
  if (terminal.consumedNolics !== ticket.priceNolics || terminal.consumedLifePoints !== ticket.lifeCost) {
    fail("SETTLEMENT_ECONOMY_MISMATCH", "Ticket terminal does not preserve frozen price and life cost");
  }
  if (terminal.authorCreditNolics + terminal.sinkNolics + terminal.royaltyReserveDeltaNolics !== ticket.priceNolics) {
    fail("SETTLEMENT_ECONOMY_MISMATCH", "Royalty split does not conserve the frozen price");
  }
  return terminal;
}

function assertEvent(event, ticket, context, {requireSettled = false} = {}) {
  if (event === null || event === undefined) {
    if (requireSettled) fail("EVENT_NOT_FOUND", "Exact Arena result event is absent");
    return null;
  }
  if (!isRecord(event)) fail("INVALID_SNAPSHOT", "Result event is malformed");
  if (event.userId !== context.ownerUid || event.ticketId !== context.ticketId || event.attemptId !== context.attemptId ||
      event.lessonId !== ticket.lessonId || event.lessonVersion !== ticket.lessonVersion || event.activityKind !== "ARENA") {
    fail("EVENT_IDENTITY_MISMATCH", "Result event does not bind the exact Arena ticket context");
  }
  const receipt = event.settlementReceipt;
  if (!isRecord(receipt)) {
    if (requireSettled) fail("SETTLEMENT_RECEIPT_MISSING", "Result event lacks a settlement receipt");
    return {event, receipt: null};
  }
  if (receipt.uid !== context.ownerUid || receipt.ticketId !== context.ticketId || receipt.attemptId !== context.attemptId ||
      receipt.lessonId !== ticket.lessonId || receipt.lessonVersion !== ticket.lessonVersion || receipt.status !== "SETTLED" ||
      receipt.completed !== true) {
    fail("EVENT_RECEIPT_MISMATCH", "Result receipt does not bind the exact completed Arena attempt");
  }
  whole(receipt.settledAtMs, "event.settlementReceipt.settledAtMs");
  finite(receipt.percentScore, "event.settlementReceipt.percentScore", 0, 100);
  for (const field of ["lifePointsSpent", "plasmaPointsSpent", "nolicsSpent"]) {
    whole(receipt[field], `event.settlementReceipt.${field}`);
  }
  if (!isRecord(receipt.reward)) fail("INVALID_SNAPSHOT", "Result receipt lacks reward details");
  whole(receipt.reward.nolics, "event.settlementReceipt.reward.nolics");
  whole(receipt.reward.skillPoints, "event.settlementReceipt.reward.skillPoints");
  return {event, receipt};
}

function ticketSummary(ticket) {
  const terminal = isRecord(ticket.terminal) ? ticket.terminal : null;
  return {
    ticketStatus: ticket.status,
    mode: ticket.mode,
    difficulty: ticket.difficulty,
    lessonId: ticket.lessonId,
    lessonVersion: ticket.lessonVersion,
    frozenPriceNolics: ticket.priceNolics,
    frozenLifeCost: ticket.lifeCost,
    issuedAtMs: ticket.issuedAtMs,
    settlementReceipt: ticket.status === "SETTLED" ? {
      receiptId: terminal.receiptId,
      attemptId: terminal.attemptId,
      settledAtMs: terminal.settledAtMs,
      percentScore: terminal.percentScore,
      consumedNolics: terminal.consumedNolics,
      consumedLifePoints: terminal.consumedLifePoints,
      authorCreditNolics: terminal.authorCreditNolics,
      sinkNolics: terminal.sinkNolics,
      royaltyReserveDeltaNolics: terminal.royaltyReserveDeltaNolics,
    } : null,
  };
}

function eventSummary(eventReceipt, path) {
  if (!eventReceipt) return null;
  return {
    path,
    status: eventReceipt.status,
    completed: eventReceipt.completed,
    receiptId: eventReceipt.receiptId,
    settledAtMs: eventReceipt.settledAtMs,
    percentScore: eventReceipt.percentScore,
    nolicsSpent: eventReceipt.nolicsSpent,
    lifePointsSpent: eventReceipt.lifePointsSpent,
    plasmaPointsSpent: eventReceipt.plasmaPointsSpent,
    reward: {nolics: eventReceipt.reward.nolics, skillPoints: eventReceipt.reward.skillPoints},
  };
}

async function readContext(db, context) {
  const ticketPath = `arena_entry_tickets/${context.ticketId}`;
  const resultPath = eventPath(context.attemptId);
  const [ticketSnapshot, eventSnapshot] = await Promise.all([
    db.doc(ticketPath).get(),
    db.doc(resultPath).get(),
  ]);
  const ticket = assertTicket(ticketSnapshot.exists ? ticketSnapshot.data() : null, context);
  const eventResult = assertEvent(eventSnapshot.exists ? eventSnapshot.data() : null, ticket, context);
  return {
    status: "CONTEXT",
    oracleVersion: "arena-e2e-context-reader-v1",
    projectId: PROJECT,
    firestoreHost: FIRESTORE_EMULATOR_HOST,
    documentWrites: 0,
    runId: context.runId,
    ownerUid: context.ownerUid,
    ticketId: context.ticketId,
    attemptId: context.attemptId,
    ...(context.ratingId ? {ratingId: context.ratingId, rating: context.rating} : {}),
    ticketPath,
    resultEventPath: resultPath,
    ...ticketSummary(ticket),
    event: eventSummary(eventResult?.receipt, resultPath),
  };
}

function ledgerRow(row, sourceId) {
  if (!isRecord(row) || row.sourceId !== sourceId || typeof row.id !== "string" || !row.id) {
    fail("INVALID_LEDGER", "Ledger row does not belong to the requested source");
  }
  text(row.uid, "ledger.uid", {max: 512});
  text(row.currency, "ledger.currency", {max: 64});
  text(row.reason, "ledger.reason", {max: 128});
  if (!Number.isSafeInteger(row.delta) || row.delta === 0) fail("INVALID_LEDGER", "Ledger delta must be a nonzero safe integer");
  whole(row.atMs, "ledger.atMs");
  return row;
}

function expectedLedgerRow({uid, currency, delta, reason, sourceId}) {
  if (delta === 0) return null;
  return {uid, currency, delta, reason, sourceId};
}

function ledgerKey(row) {
  return JSON.stringify([row.uid, row.currency, row.delta, row.reason, row.sourceId]);
}

function assertExactLedger(actual, expected, sourceId) {
  if (!Array.isArray(actual) || actual.length > MAX_LEDGER_ROWS_PER_SOURCE) {
    fail("LEDGER_AMBIGUITY", "Ledger query is not bounded for one source");
  }
  const rows = actual.map((row) => ledgerRow(row, sourceId));
  const wanted = expected.filter(Boolean);
  if (rows.length !== wanted.length) fail("LEDGER_AMBIGUITY", "Ledger has missing, duplicate, or unrelated effects");
  const counts = new Map(rows.map((row) => [ledgerKey(row), 0]));
  for (const row of rows) counts.set(ledgerKey(row), counts.get(ledgerKey(row)) + 1);
  for (const row of wanted) {
    const count = counts.get(ledgerKey(row)) || 0;
    if (count !== 1) fail("LEDGER_AMBIGUITY", "Ledger does not contain exactly one expected effect");
    counts.delete(ledgerKey(row));
  }
  if (counts.size !== 0) fail("LEDGER_AMBIGUITY", "Ledger contains an unexpected effect");
  return rows.sort((left, right) => left.id.localeCompare(right.id));
}

function expectedTicketLedger(ticket, terminal) {
  return [
    expectedLedgerRow({uid: ticket.uid, currency: "nolics", delta: -ticket.priceNolics,
      reason: "arena_ticket_reserve", sourceId: ticket.ticketId}),
    expectedLedgerRow({uid: ticket.uid, currency: "standardChargePoints", delta: -ticket.lifeCost,
      reason: "arena_ticket_reserve", sourceId: ticket.ticketId}),
    expectedLedgerRow({uid: ticket.authorUid, currency: "nolics", delta: terminal.authorCreditNolics,
      reason: "arena_author_royalty", sourceId: ticket.ticketId}),
    expectedLedgerRow({uid: `arena-sink:${ticket.authorUid}`, currency: "nolics", delta: terminal.sinkNolics,
      reason: "arena_royalty_sink", sourceId: ticket.ticketId}),
    expectedLedgerRow({uid: `arena-carry:${ticket.authorUid}`, currency: "nolics", delta: terminal.royaltyReserveDeltaNolics,
      reason: "arena_royalty_carry", sourceId: ticket.ticketId}),
  ];
}

function expectedAttemptLedger(receipt, terminal) {
  const extraStandardCharges = receipt.lifePointsSpent - terminal.consumedLifePoints;
  if (!Number.isSafeInteger(extraStandardCharges) || extraStandardCharges < 0) {
    fail("SETTLEMENT_ECONOMY_MISMATCH", "Result receipt charges less than the frozen ticket hold");
  }
  return [
    expectedLedgerRow({uid: receipt.uid, currency: "nolics", delta: receipt.reward.nolics,
      reason: "attempt_reward", sourceId: receipt.attemptId}),
    expectedLedgerRow({uid: receipt.uid, currency: "skillPoints", delta: receipt.reward.skillPoints,
      reason: "attempt_reward", sourceId: receipt.attemptId}),
    expectedLedgerRow({uid: receipt.uid, currency: "standardChargePoints", delta: -extraStandardCharges,
      reason: "charge_claim", sourceId: receipt.attemptId}),
    expectedLedgerRow({uid: receipt.uid, currency: "plasmaChargePoints", delta: -receipt.plasmaPointsSpent,
      reason: "charge_claim", sourceId: receipt.attemptId}),
  ];
}

function assertRatingReceipt(mutation, ticket, terminal, receipt, context) {
  if (!isRecord(mutation)) fail("RATING_RECEIPT_NOT_FOUND", "Exact RATE_LESSON receipt is absent");
  const expectedPayloadHash = expectedRatingPayloadHash({lessonId: ticket.lessonId, attemptId: ticket.attemptId, rating: context.rating});
  if (mutation.schemaVersion !== 1 || mutation.uid !== context.ownerUid || mutation.mutationId !== context.ratingId ||
      mutation.operation !== "RATE_LESSON" || mutation.status !== "APPLIED" || mutation.payloadHash !== expectedPayloadHash) {
    fail("RATING_RECEIPT_MISMATCH", "RATE_LESSON receipt does not bind the explicit completed attempt");
  }
  whole(mutation.decidedAtMs, "rating.decidedAtMs");
  if (mutation.decidedAtMs < terminal.settledAtMs) fail("RATING_RECEIPT_MISMATCH", "Rating predates settlement");
  if (!isRecord(mutation.result) || mutation.result.lessonId !== ticket.lessonId ||
      mutation.result.ratingId !== `arena-rating:${mutationDocumentId(context.ownerUid, context.ratingId)}` ||
      mutation.result.acceptedAtMs !== mutation.decidedAtMs) {
    fail("RATING_RECEIPT_MISMATCH", "RATE_LESSON result is not the exact immutable receipt");
  }
  if (receipt.nolicsSpent !== terminal.consumedNolics || receipt.percentScore !== terminal.percentScore ||
      receipt.settledAtMs !== terminal.settledAtMs) {
    fail("SETTLEMENT_ECONOMY_MISMATCH", "Ticket and event settlement receipts disagree");
  }
  return {expectedPayloadHash, mutation};
}

async function readOracleEvidence(db, context) {
  const ticketPath = `arena_entry_tickets/${context.ticketId}`;
  const resultPath = eventPath(context.attemptId);
  const ratingPath = `arena_mutations/${mutationDocumentId(context.ownerUid, context.ratingId)}`;
  const ledger = db.collection(LEDGER_PATH);
  const [ticketSnapshot, eventSnapshot, ratingSnapshot, ticketLedgerSnapshot, attemptLedgerSnapshot] = await Promise.all([
    db.doc(ticketPath).get(),
    db.doc(resultPath).get(),
    db.doc(ratingPath).get(),
    ledger.where("sourceId", "==", context.ticketId).limit(MAX_LEDGER_ROWS_PER_SOURCE + 1).get(),
    ledger.where("sourceId", "==", context.attemptId).limit(MAX_LEDGER_ROWS_PER_SOURCE + 1).get(),
  ]);
  const ticket = assertTicket(ticketSnapshot.exists ? ticketSnapshot.data() : null, context);
  const terminal = settledTerminal(ticket);
  const eventResult = assertEvent(eventSnapshot.exists ? eventSnapshot.data() : null, ticket, context, {requireSettled: true});
  const receipt = eventResult.receipt;
  const rating = assertRatingReceipt(ratingSnapshot.exists ? ratingSnapshot.data() : null, ticket, terminal, receipt, context);
  const ticketLedger = assertExactLedger(ticketLedgerSnapshot.docs.map((document) => document.data()),
    expectedTicketLedger(ticket, terminal), context.ticketId);
  const attemptLedger = assertExactLedger(attemptLedgerSnapshot.docs.map((document) => document.data()),
    expectedAttemptLedger(receipt, terminal), context.attemptId);
  const result = {
    status: "PASS",
    oracleVersion: "arena-e2e-receipt-oracle-v1",
    projectId: PROJECT,
    firestoreHost: FIRESTORE_EMULATOR_HOST,
    documentWrites: 0,
    runId: context.runId,
    ownerUid: context.ownerUid,
    ticketId: context.ticketId,
    attemptId: context.attemptId,
    lessonId: ticket.lessonId,
    lessonVersion: ticket.lessonVersion,
    frozenPriceNolics: ticket.priceNolics,
    ratingId: context.ratingId,
    rating: context.rating,
    ticketPath,
    resultEventPath: resultPath,
    ratingReceiptPath: ratingPath,
    settlementReceipt: ticketSummary(ticket).settlementReceipt,
    event: eventSummary(receipt, resultPath),
    ratingReceipt: {
      mutationId: rating.mutation.mutationId,
      operation: rating.mutation.operation,
      status: rating.mutation.status,
      ratingId: rating.mutation.result.ratingId,
      acceptedAtMs: rating.mutation.result.acceptedAtMs,
      payloadHash: rating.expectedPayloadHash,
    },
    ledger: {
      ticketRows: ticketLedger.map((row) => ({id: row.id, uid: row.uid, currency: row.currency, delta: row.delta,
        reason: row.reason, sourceId: row.sourceId, atMs: row.atMs})),
      attemptRows: attemptLedger.map((row) => ({id: row.id, uid: row.uid, currency: row.currency, delta: row.delta,
        reason: row.reason, sourceId: row.sourceId, atMs: row.atMs})),
    },
  };
  result.evidenceSha256 = sha256(JSON.stringify(result));
  return result;
}

module.exports = {
  PROJECT,
  FIRESTORE_EMULATOR_HOST,
  LEDGER_PATH,
  MAX_LEDGER_ROWS_PER_SOURCE,
  ArenaE2eEvidenceError,
  parseCli,
  checkEnvironment,
  expectedRatingPayloadHash,
  readContext,
  readOracleEvidence,
  assertTicket,
  settledTerminal,
  assertEvent,
  assertExactLedger,
  expectedTicketLedger,
  expectedAttemptLedger,
  eventPath,
  mutationDocumentId,
};
