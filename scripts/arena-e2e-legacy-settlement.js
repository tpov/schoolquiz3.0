"use strict";

// Operator-only diagnosis of a pinned historical incident, NOT an acceptance oracle.
// No epsilon, receipt rewrite, mutation delivery, or exemption for subsequent runs exists here.
const {createHash} = require("node:crypto");
const {
  ArenaE2eEvidenceError, PROJECT, LEDGER_PATH, MAX_LEDGER_ROWS_PER_SOURCE,
  checkEnvironment, parseCli, assertTicket, settledTerminal, assertEvent,
  assertExactLedger, expectedTicketLedger, expectedAttemptLedger, eventPath,
} = require("./arena-e2e-evidence");
const {attemptFingerprint} = require("../functions/attempt-receipt");
const {prepareInput, ROOM_SCHEMA_IDENTITY_HASH} = require("./arena-e2e-pending-settlement-reader");

const VERSION = "arena-legacy-settlement-recovery-v1";
const FIX_COMMIT = "8dc782eab4bc85ecbea4ebe72c4c00755932beba";
// Reviewed live e499 incident, 2026-09-13. This binds original identities, clocks, result,
// permanent event and completed mutation plus exact ledger; a CLI-supplied file cannot add
// another incident. A new reviewed record needs an explicit source change and review.
const REVIEWED_SERVER_EVIDENCE_SHA256 = "4677fe2ad288781debde105558645d9e26038e58fd86c09c5239692c3381e278";
const OPERATION = "lesson_runner.SUBMIT_ATTEMPT";
const hash = (value) => createHash("sha256").update(value).digest("hex");
function fail(code) { throw new ArenaE2eEvidenceError(code); }
function same(a, b) {
  if (a === undefined || b === undefined) return false;
  return attemptFingerprint("comparison", a) === attemptFingerprint("comparison", b);
}
function one(rows) { if (!Array.isArray(rows) || rows.length !== 1) fail("LOCAL_PROOF_AMBIGUOUS"); return rows[0]; }
function json(value) {
  if (typeof value !== "string" || Buffer.byteLength(value) > 1_000_000) fail("LOCAL_PROOF_INVALID");
  try { return JSON.parse(value); } catch { fail("LOCAL_PROOF_INVALID"); }
}
function mutationId(context) { return "lesson_runner-SUBMIT_ATTEMPT-" + context.attemptId; }
function mutationPath(context) {
  const id = mutationId(context);
  return `mutation_keys/${hash(id).slice(0, 12)}_${id.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
}
function serverDigest(context, snapshot) {
  return attemptFingerprint(context.ownerUid, {ticket: snapshot.ticket, event: snapshot.event,
    mutation: snapshot.mutation,
    ticketRows: [...snapshot.ticketRows].sort((a, b) => a.id.localeCompare(b.id)),
    attemptRows: [...snapshot.attemptRows].sort((a, b) => a.id.localeCompare(b.id))});
}

// The optional second argument is a trusted-code test dependency, never a CLI input.
function assessLegacySettlement({context, snapshot, local, incident}, reviewedServerEvidenceSha256 = REVIEWED_SERVER_EVIDENCE_SHA256) {
  // Reuse the strict oracle's identity, frozen economy and EXACT ledger checks. Only the
  // independently pinned pair of clock values is classified separately, never normalized.
  const ticket = assertTicket(snapshot.ticket, context);
  const terminal = settledTerminal(ticket);
  const {receipt} = assertEvent(snapshot.event, ticket, context, {requireSettled: true});
  const event = snapshot.event;
  if (terminal.percentScore !== receipt.percentScore || terminal.consumedNolics !== receipt.nolicsSpent ||
      event.completed !== true || event.percentScore !== receipt.percentScore ||
      event.codeAnswer !== receipt.codeAnswer || event.receivedAtMs !== receipt.settledAtMs ||
      terminal.settledAtMs <= ticket.issuedAtMs || receipt.settledAtMs <= terminal.settledAtMs) {
    fail("NOT_LEGACY_CLOCK_ONLY");
  }
  assertExactLedger(snapshot.ticketRows, expectedTicketLedger(ticket, terminal), context.ticketId);
  assertExactLedger(snapshot.attemptRows, expectedAttemptLedger(receipt, terminal), context.attemptId);
  if (event.settlementResponse?.accepted !== 1 || event.settlementResponse?.receipts?.length !== 1 ||
      !same(event.settlementResponse.receipts[0], receipt) ||
      !same(event.settlementResponse.reward, receipt.reward)) fail("RESPONSE_RECEIPT_MISMATCH");

  if (local.schemaVersion !== 14 || local.identityHash !== ROOM_SCHEMA_IDENTITY_HASH ||
      local.outboxCount !== 0) fail("LOCAL_PROOF_INVALID");
  const row = one(local.tickets);
  const command = one(local.commands);
  const intent = one(local.intents);
  const savedReceipt = one(local.receipts);
  for (const item of [row, command, intent, savedReceipt]) {
    if (item.ownerUid !== context.ownerUid) fail("LOCAL_OWNER_MISMATCH");
  }
  const before = row.serverStatus === "RESERVED" && command.state === "PENDING";
  const after = row.serverStatus === "SETTLED" && command.state === "ACKNOWLEDGED";
  if ((!before && !after) || row.ticketId !== context.ticketId || row.attemptId !== context.attemptId ||
      row.localUse !== "LAUNCHED" || row.decision !== "SETTLE" ||
      command.ticketId !== context.ticketId || command.attemptId !== context.attemptId ||
      command.commandId !== context.attemptId || command.commandKind !== "SETTLE" ||
      intent.attemptId !== context.attemptId || intent.expectedVersion !== null) fail("LOCAL_STATE_MISMATCH");
  for (const item of [intent, savedReceipt]) {
    if (item.mutationId !== mutationId(context) || item.operation !== OPERATION ||
        item.entityRef !== `lesson_runner:attempt:${context.attemptId}`) fail("LOCAL_RECEIPT_MISMATCH");
  }
  const payload = json(intent.payload);
  if (payload.ownerUid !== context.ownerUid || payload.userId !== context.ownerUid ||
      payload.ticketId !== context.ticketId || payload.attemptId !== context.attemptId ||
      payload.lessonId !== ticket.lessonId || payload.lessonVersion !== ticket.lessonVersion ||
      payload.difficulty !== ticket.difficulty || payload.completionKind !== "COMPLETED" ||
      !same(json(command.bodyJson), payload) || hash(command.bodyJson) !== command.bodySha256 ||
      event.requestFingerprint !== attemptFingerprint(context.ownerUid, payload)) fail("LOCAL_PAYLOAD_MISMATCH");
  if (!same(json(savedReceipt.payloadJson).result, event.settlementResponse)) fail("LOCAL_RECEIPT_MISMATCH");
  const mutation = snapshot.mutation;
  if (mutation?.uid !== context.ownerUid || mutation.mutationId !== mutationId(context) ||
      mutation.operation !== OPERATION || mutation.state !== "completed" ||
      mutation.requestFingerprint !== attemptFingerprint(context.ownerUid,
        {operation: OPERATION, payload, expectedVersion: null}) ||
      !same(mutation.result, event.settlementResponse)) fail("MUTATION_RECEIPT_MISMATCH");

  const digest = serverDigest(context, snapshot);
  if (digest !== reviewedServerEvidenceSha256) fail("INCIDENT_NOT_REVIEWED");
  // The incident file is an operator-reviewed, exact snapshot binding, not a wildcard policy.
  // Creating it does not make a failed E2B result pass. Unknown incidents remain blocked.
  if (incident?.schemaVersion !== 1 || incident.kind !== "PRE_CANONICAL_SETTLEMENT_CLOCK" ||
      incident.fixCommit !== FIX_COMMIT || !/^[a-zA-Z0-9_-]{1,100}$/.test(incident.incidentId || "") ||
      incident.ownerSha256 !== hash(context.ownerUid) || incident.ticketSha256 !== hash(context.ticketId) ||
      incident.attemptSha256 !== hash(context.attemptId) || incident.serverEvidenceSha256 !== digest ||
      incident.terminalSettledAtMs !== terminal.settledAtMs || incident.eventSettledAtMs !== receipt.settledAtMs) {
    fail("INCIDENT_NOT_PINNED");
  }
  return {
    status: "LEGACY_RECOVERY_ELIGIBLE", readerVersion: VERSION, incidentId: incident.incidentId,
    freshAcceptance: "FAIL", freshAcceptanceCode: "SETTLEMENT_ECONOMY_MISMATCH",
    permittedAction: "READ_SERVER_TICKET_ONLY", operationSubmissionAllowed: false,
    documentWrites: 0, localWrites: 0, deviceCalls: 0,
    ticketFingerprint: hash(context.ticketId).slice(0, 16),
    attemptFingerprint: hash(context.attemptId).slice(0, 16),
    serverEvidenceSha256: digest, localProofSha256: attemptFingerprint("local-proof", local),
    localState: before ? "STALE" : "RECONCILED",
    terminalSettledAtMs: terminal.settledAtMs, eventSettledAtMs: receipt.settledAtMs,
  };
}

async function readServerSnapshot(db, context) {
  const ledger = db.collection(LEDGER_PATH);
  const [ticket, event, mutation, ticketRows, attemptRows] = await Promise.all([
    db.doc(`arena_entry_tickets/${context.ticketId}`).get(),
    db.doc(eventPath(context.attemptId)).get(), db.doc(mutationPath(context)).get(),
    ledger.where("sourceId", "==", context.ticketId).limit(MAX_LEDGER_ROWS_PER_SOURCE + 1).get(),
    ledger.where("sourceId", "==", context.attemptId).limit(MAX_LEDGER_ROWS_PER_SOURCE + 1).get(),
  ]);
  const data = (doc) => doc.exists ? doc.data() : null;
  return {ticket: data(ticket), event: data(event), mutation: data(mutation),
    ticketRows: ticketRows.docs.map(data), attemptRows: attemptRows.docs.map(data)};
}

// Separate from the immutable incident pin: current account state must not be frozen to an
// old balance, but the recovery wrapper compares this exact snapshot before/after its UI read.
async function readServerEconomyEvidence(db, context, ticket) {
  const ids = [...new Set([context.ownerUid, ticket.authorUid])].sort();
  if (ids.some(id => typeof id !== "string" || !id || id.includes("/") || id.length > 128)) fail("INVALID_ACCOUNT_ID");
  const paths = ids.flatMap(id => [`users/${id}`, `premium_accounts/${id}`]);
  paths.push(`arena_royalty_accounts/${ticket.authorUid}`);
  const rows = await Promise.all(paths.map(async path => {
    const snap = await db.doc(path).get();
    return [path, snap.exists ? snap.data() : null];
  }));
  return {serverEconomySha256: attemptFingerprint("legacy-recovery-economy-v1", rows),
    serverEconomyDocumentCount: rows.length};
}

function readLocalProof(context, input) {
  const {DatabaseSync} = require("node:sqlite");
  const db = new DatabaseSync(input.dbPath, {readOnly: true});
  try {
    const schemaVersion = db.prepare("PRAGMA user_version").get().user_version;
    if (schemaVersion !== 14) fail("LOCAL_PROOF_INVALID");
    const identityHash = db.prepare("SELECT identity_hash FROM room_master_table WHERE id=42").get()?.identity_hash;
    if (identityHash !== ROOM_SCHEMA_IDENTITY_HASH) fail("LOCAL_PROOF_INVALID");
    const id = mutationId(context);
    return {schemaVersion, identityHash,
      tickets: db.prepare("SELECT owner_uid AS ownerUid, ticket_id AS ticketId, attempt_id AS attemptId, " +
        "server_status AS serverStatus, local_use AS localUse, decision FROM arena_ticket_records " +
        "WHERE ticket_id=? LIMIT 2").all(context.ticketId),
      commands: db.prepare("SELECT owner_uid AS ownerUid, ticket_id AS ticketId, attempt_id AS attemptId, " +
        "command_kind AS commandKind, command_id AS commandId, state, body_json AS bodyJson, body_sha256 AS bodySha256 " +
        "FROM arena_command_journal WHERE ticket_id=? AND command_kind='SETTLE' LIMIT 2").all(context.ticketId),
      intents: db.prepare("SELECT owner_uid AS ownerUid, attempt_id AS attemptId, mutation_id AS mutationId, " +
        "operation, payload, entity_ref AS entityRef, expected_version AS expectedVersion " +
        "FROM lesson_attempt_intents WHERE attempt_id=? LIMIT 2").all(context.attemptId),
      receipts: db.prepare("SELECT owner_uid AS ownerUid, mutation_id AS mutationId, operation, entity_ref AS entityRef, " +
        "payload_json AS payloadJson FROM mutation_receipts WHERE mutation_id=? LIMIT 2").all(id),
      outboxCount: db.prepare("SELECT COUNT(*) AS n FROM outbox WHERE mutation_id=?").get(id).n,
    };
  } finally { db.close(); }
}

function checkSdkEnvironment(env, runtime = process.env) {
  checkEnvironment(env);
  // firebase-admin reads process.env, not the validation dependency supplied to main().
  // Both must point to the same private emulator before the SDK is even initialized.
  checkEnvironment(runtime);
  if (env.FIREBASE_CONFIG !== undefined || runtime.FIREBASE_CONFIG !== undefined) fail("UNSAFE_ENVIRONMENT");
}

async function main(argv = process.argv.slice(2), env = process.env) {
  if (argv.length !== 6 || argv[0] !== "--context" || argv[2] !== "--incident" || argv[4] !== "--db") {
    fail("USAGE"); // --context private.json --incident reviewed-incident.json --db stopped-copy.db
  }
  checkSdkEnvironment(env);
  const fs = require("node:fs");
  const read = (file) => {
    const stat = fs.lstatSync(file);
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 1_000_000) fail("UNSAFE_INPUT");
    return json(fs.readFileSync(file, "utf8"));
  };
  const raw = read(argv[1]);
  const context = parseCli(["--run-id", raw.runId, "--owner-uid", raw.ownerUid,
    "--ticket-id", raw.ticketId, "--attempt-id", raw.attemptId], {allowRating: false});
  const incident = read(argv[3]);
  const input = prepareInput({db: argv[5]});
  const files = [input.dbPath, input.wal?.path, input.shm?.path].filter(Boolean);
  const before = files.map((file) => hash(fs.readFileSync(file)));
  const local = readLocalProof(context, input);
  const admin = require("../functions/node_modules/firebase-admin");
  const app = admin.initializeApp({projectId: PROJECT}, VERSION);
  try {
    const snapshot = await readServerSnapshot(app.firestore(), context);
    if (!same(before, files.map((file) => hash(fs.readFileSync(file))))) fail("LOCAL_SNAPSHOT_CHANGED");
    const assessment = assessLegacySettlement({context, snapshot, local, incident});
    return {...assessment, ...await readServerEconomyEvidence(app.firestore(), context, snapshot.ticket)};
  } finally { await app.delete(); }
}

if (require.main === module) main().then((result) => console.log(JSON.stringify(result, null, 2))).catch((error) => {
  console.error(JSON.stringify({status: "BLOCKED", readerVersion: VERSION, documentWrites: 0,
    code: error instanceof ArenaE2eEvidenceError ? error.code : "UNEXPECTED"}));
  process.exitCode = 1;
});
module.exports = {VERSION, FIX_COMMIT, REVIEWED_SERVER_EVIDENCE_SHA256, assessLegacySettlement, readServerSnapshot, readServerEconomyEvidence, readLocalProof,
  serverDigest, mutationPath, checkSdkEnvironment, main};
