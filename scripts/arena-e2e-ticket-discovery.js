"use strict";

// Read-only ticket discovery for the retained-installation Arena walkthrough.
//
// The first invocation captures every ticket document currently owned by the explicit account.
// The second invocation accepts a ticket only when it is both absent from that immutable baseline
// and the sole current ticket matching the exact prepared-entry contract.  In particular, this
// intentionally never ranks tickets by time: an old ticket cannot become evidence for a new run.

const {createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const {attemptDocumentId} = require("../functions/arena-tickets");
const {
  ArenaE2eEvidenceError,
  PROJECT,
  FIRESTORE_EMULATOR_HOST,
  checkEnvironment,
  assertTicket,
} = require("./arena-e2e-evidence");

const ORACLE_VERSION = "arena-e2e-ticket-discovery-v1";
const TICKETS_PATH = "arena_entry_tickets";
const MAX_OWNED_TICKETS = 256;
const USAGE = [
  "node scripts/arena-e2e-ticket-discovery.js baseline --run-id <id> --owner-uid <uid> --lesson-id <id>",
  "  --lesson-version <positive integer> --mode <SELECTED|RANDOM> --difficulty <EASY|HARD>",
  "  --frozen-price-nolics <0..100; SELECTED requires 1..100, RANDOM requires 0> > <private-baseline.json>",
  "node scripts/arena-e2e-ticket-discovery.js discover --baseline-file <private-baseline.json>",
].join("\n");

function fail(code, message = code) {
  throw new ArenaE2eEvidenceError(code, message);
}

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function safeText(value, name, {max = 256, documentId = false} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", `${name} must be a nonempty safe string`);
  }
  if (documentId && (value.includes("/") || value === "." || value === "..")) {
    fail("INVALID_ARGUMENT", `${name} must be a document ID`);
  }
  return value;
}

function exactInteger(value, name, {min = 0, max = Number.MAX_SAFE_INTEGER, code = "INVALID_ARGUMENT"} = {}) {
  const parsed = typeof value === "number" ? value :
    typeof value === "string" && /^(?:0|[1-9][0-9]*)$/.test(value) ? Number(value) : NaN;
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) {
    fail(code, `${name} must be a safe integer in range`);
  }
  return parsed;
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function baselineDigest(context, capturedAtMs, ownedTicketIds) {
  return sha256(JSON.stringify([ORACLE_VERSION, context.runId, context.ownerUid, context.lessonId,
    context.lessonVersion, context.mode, context.difficulty, context.frozenPriceNolics, capturedAtMs, ownedTicketIds]));
}

function parseNamedPairs(argv, allowed) {
  const values = {};
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
  return values;
}

function parseBaselineCli(argv) {
  const values = parseNamedPairs(argv, new Set([
    "--run-id", "--owner-uid", "--lesson-id", "--lesson-version", "--mode", "--difficulty", "--frozen-price-nolics",
  ]));
  if (values.help) return values;
  for (const key of ["runId", "ownerUid", "lessonId", "lessonVersion", "mode", "difficulty", "frozenPriceNolics"]) {
    if (!Object.hasOwn(values, key)) fail("USAGE", `Missing --${key.replace(/[A-Z]/g, (character) => `-${character.toLowerCase()}`)}`);
  }
  const context = {
    runId: safeText(values.runId, "runId", {max: 128, documentId: true}),
    ownerUid: safeText(values.ownerUid, "ownerUid", {max: 256, documentId: true}),
    lessonId: safeText(values.lessonId, "lessonId", {max: 256, documentId: true}),
    lessonVersion: exactInteger(values.lessonVersion, "lessonVersion", {min: 1}),
    mode: values.mode,
    difficulty: values.difficulty,
    frozenPriceNolics: exactInteger(values.frozenPriceNolics, "frozenPriceNolics", {min: 0, max: 100}),
  };
  if (!['SELECTED', 'RANDOM'].includes(context.mode)) fail("INVALID_ARGUMENT", "mode must be SELECTED or RANDOM");
  if (!['EASY', 'HARD'].includes(context.difficulty)) fail("INVALID_ARGUMENT", "difficulty must be EASY or HARD");
  if ((context.mode === "SELECTED" && context.frozenPriceNolics < 1) ||
      (context.mode === "RANDOM" && context.frozenPriceNolics !== 0)) {
    fail("INVALID_ARGUMENT", "frozen price is incompatible with ticket mode");
  }
  return context;
}

function parseDiscoverCli(argv) {
  const values = parseNamedPairs(argv, new Set(["--baseline-file"]));
  if (values.help) return values;
  if (!Object.hasOwn(values, "baselineFile")) fail("USAGE", "Missing --baseline-file");
  return {baselineFile: safeText(values.baselineFile, "baselineFile", {max: 4096})};
}

function parseCommand(argv) {
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  const [command, ...rest] = argv;
  if (command === "baseline") return {command, context: parseBaselineCli(rest)};
  if (command === "discover") return {command, ...parseDiscoverCli(rest)};
  fail("USAGE", "Expected baseline or discover command");
}

function canonicalOwnedTicketIds(rows, code = "INVALID_SNAPSHOT") {
  if (!Array.isArray(rows) || rows.length > MAX_OWNED_TICKETS) {
    fail("OWNED_TICKET_LIMIT", `Owned ticket query must contain at most ${MAX_OWNED_TICKETS} documents`);
  }
  const ids = rows.map((row) => {
    if (!row || typeof row.id !== "string") fail(code, "Owned ticket query returned an invalid document ID");
    return safeText(row.id, "ticket document ID", {max: 256, documentId: true});
  }).sort();
  for (let index = 1; index < ids.length; index += 1) {
    if (ids[index] === ids[index - 1]) fail(code, "Owned ticket query returned duplicate document IDs");
  }
  return ids;
}

async function readOwnedTickets(db, ownerUid) {
  if (!db || typeof db.collection !== "function") throw new TypeError("Firestore db required");
  const snapshot = await db.collection(TICKETS_PATH).where("uid", "==", ownerUid).limit(MAX_OWNED_TICKETS + 1).get();
  if (!snapshot || !Array.isArray(snapshot.docs)) fail("INVALID_SNAPSHOT", "Owned ticket query returned no documents array");
  if (snapshot.docs.length > MAX_OWNED_TICKETS) {
    fail("OWNED_TICKET_LIMIT", `Owned ticket query exceeded ${MAX_OWNED_TICKETS} documents`);
  }
  return snapshot.docs.map((document) => ({id: document.id, data: typeof document.data === "function" ? document.data() : undefined}));
}

function baselineManifest(context, ownedTicketIds, capturedAtMs) {
  const ids = canonicalOwnedTicketIds(ownedTicketIds.map((id) => ({id})), "INVALID_SNAPSHOT");
  const captured = exactInteger(capturedAtMs, "capturedAtMs", {min: 0, code: "INVALID_SNAPSHOT"});
  return {
    status: "BASELINE",
    oracleVersion: ORACLE_VERSION,
    projectId: PROJECT,
    firestoreHost: FIRESTORE_EMULATOR_HOST,
    documentWrites: 0,
    ...context,
    capturedAtMs: captured,
    ownedTicketCount: ids.length,
    ownedTicketIds: ids,
    baselineSha256: baselineDigest(context, captured, ids),
  };
}

async function captureBaseline(db, context, {now = Date.now} = {}) {
  const rows = await readOwnedTickets(db, context.ownerUid);
  return baselineManifest(context, canonicalOwnedTicketIds(rows), now());
}

function parseBaselineManifest(raw) {
  if (!isRecord(raw) || raw.status !== "BASELINE" || raw.oracleVersion !== ORACLE_VERSION ||
      raw.projectId !== PROJECT || raw.firestoreHost !== FIRESTORE_EMULATOR_HOST || raw.documentWrites !== 0) {
    fail("INVALID_BASELINE", "Baseline manifest is not from this read-only discovery tool");
  }
  let context;
  try {
    context = parseBaselineCli([
      "--run-id", raw.runId,
      "--owner-uid", raw.ownerUid,
      "--lesson-id", raw.lessonId,
      "--lesson-version", String(raw.lessonVersion),
      "--mode", raw.mode,
      "--difficulty", raw.difficulty,
      "--frozen-price-nolics", String(raw.frozenPriceNolics),
    ]);
  } catch (error) {
    if (error instanceof ArenaE2eEvidenceError) fail("INVALID_BASELINE", error.message);
    throw error;
  }
  const capturedAtMs = exactInteger(raw.capturedAtMs, "capturedAtMs", {min: 0, code: "INVALID_BASELINE"});
  if (!Array.isArray(raw.ownedTicketIds) || raw.ownedTicketCount !== raw.ownedTicketIds.length) {
    fail("INVALID_BASELINE", "Baseline must contain its complete owned ticket ID list");
  }
  let ownedTicketIds;
  try {
    ownedTicketIds = canonicalOwnedTicketIds(raw.ownedTicketIds.map((id) => ({id})), "INVALID_BASELINE");
  } catch (error) {
    if (error instanceof ArenaE2eEvidenceError) fail("INVALID_BASELINE", error.message);
    throw error;
  }
  if (JSON.stringify(ownedTicketIds) !== JSON.stringify(raw.ownedTicketIds)) {
    fail("INVALID_BASELINE", "Baseline owned ticket IDs must be unique and lexicographically sorted");
  }
  if (raw.baselineSha256 !== baselineDigest(context, capturedAtMs, ownedTicketIds)) {
    fail("INVALID_BASELINE", "Baseline digest does not bind its exact run and ticket IDs");
  }
  return {...context, capturedAtMs, ownedTicketIds, baselineSha256: raw.baselineSha256};
}

function readBaselineFile(baselineFile, readFile = readFileSync) {
  let raw;
  try {
    raw = JSON.parse(readFile(baselineFile, "utf8"));
  } catch (_) {
    fail("BASELINE_FILE_UNREADABLE", "Baseline file is absent or invalid JSON");
  }
  return parseBaselineManifest(raw);
}

function matchesExpectedTicket(ticket, context) {
  return isRecord(ticket) && ticket.uid === context.ownerUid && ticket.mode === context.mode &&
    ticket.difficulty === context.difficulty && ticket.lessonId === context.lessonId &&
    ticket.lessonVersion === context.lessonVersion && ticket.priceNolics === context.frozenPriceNolics;
}

function assertCandidateTicket(row, context) {
  if (!/^[a-f0-9]{64}$/.test(row.id)) {
    fail("INVALID_SNAPSHOT", "Fresh matching ticket has a noncanonical ticket ID");
  }
  const ticketId = row.id;
  const attemptId = attemptDocumentId(ticketId);
  const ticket = assertTicket(row.data, {ownerUid: context.ownerUid, ticketId, attemptId});
  if (!matchesExpectedTicket(ticket, context)) {
    fail("TICKET_CONTRACT_MISMATCH", "Fresh ticket does not preserve the exact prepared-entry contract");
  }
  return ticket;
}

async function discoverFreshTicket(db, manifest) {
  const rows = await readOwnedTickets(db, manifest.ownerUid);
  const currentIds = canonicalOwnedTicketIds(rows);
  const currentIdSet = new Set(currentIds);
  const missingBaselineIds = manifest.ownedTicketIds.filter((id) => !currentIdSet.has(id));
  if (missingBaselineIds.length) {
    fail("BASELINE_DRIFT", "A baseline-owned ticket disappeared before discovery");
  }
  const baselineIdSet = new Set(manifest.ownedTicketIds);
  const freshRows = rows.filter((row) => !baselineIdSet.has(row.id));
  const matchingRows = freshRows.filter((row) => matchesExpectedTicket(row.data, manifest));
  if (matchingRows.length === 0) {
    fail("FRESH_TICKET_NOT_FOUND", "No fresh ticket matches the exact prepared-entry contract");
  }
  if (matchingRows.length !== 1) {
    fail("FRESH_TICKET_AMBIGUITY", "More than one fresh ticket matches the exact prepared-entry contract");
  }
  const ticket = assertCandidateTicket(matchingRows[0], manifest);
  const ticketId = matchingRows[0].id;
  return {
    status: "CANDIDATE",
    oracleVersion: ORACLE_VERSION,
    projectId: PROJECT,
    firestoreHost: FIRESTORE_EMULATOR_HOST,
    documentWrites: 0,
    runId: manifest.runId,
    ownerUid: manifest.ownerUid,
    lessonId: manifest.lessonId,
    lessonVersion: manifest.lessonVersion,
    expectedMode: manifest.mode,
    expectedDifficulty: manifest.difficulty,
    expectedFrozenPriceNolics: manifest.frozenPriceNolics,
    baselineSha256: manifest.baselineSha256,
    baselineOwnedTicketCount: manifest.ownedTicketIds.length,
    currentOwnedTicketCount: currentIds.length,
    freshOwnedTicketIds: freshRows.map((row) => row.id).sort(),
    matchingFreshTicketIds: [ticketId],
    ticketId,
    attemptId: ticket.attemptId,
    ticketPath: `${TICKETS_PATH}/${ticketId}`,
    ticketStatus: ticket.status,
    frozenPriceNolics: ticket.priceNolics,
  };
}

async function main(argv = process.argv.slice(2), env = process.env, dependencies = {}) {
  const command = parseCommand(argv);
  if (command.help || command.context?.help) {
    console.log(USAGE);
    return null;
  }
  checkEnvironment(env);
  const admin = dependencies.admin || require("../functions/node_modules/firebase-admin");
  if (!admin.apps || admin.apps.length === 0) admin.initializeApp({projectId: PROJECT});
  const db = admin.firestore();
  if (command.command === "baseline") return captureBaseline(db, command.context, {now: dependencies.now || Date.now});
  return discoverFreshTicket(db, readBaselineFile(command.baselineFile, dependencies.readFile || readFileSync));
}

if (require.main === module) {
  main().then((result) => {
    if (result) console.log(JSON.stringify(result, null, 2));
  }).catch((error) => {
    const code = error instanceof ArenaE2eEvidenceError ? error.code : "UNEXPECTED";
    console.error(JSON.stringify({status: "FAIL", oracleVersion: ORACLE_VERSION, documentWrites: 0, code, message: error.message}));
    process.exitCode = 1;
  });
}

module.exports = {
  ORACLE_VERSION,
  TICKETS_PATH,
  MAX_OWNED_TICKETS,
  USAGE,
  parseBaselineCli,
  parseDiscoverCli,
  parseCommand,
  baselineManifest,
  captureBaseline,
  parseBaselineManifest,
  readBaselineFile,
  discoverFreshTicket,
  readOwnedTickets,
  matchesExpectedTicket,
  main,
};
