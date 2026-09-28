"use strict";

const assert = require("node:assert/strict");
const {test} = require("node:test");
const {createHash} = require("node:crypto");
const {attemptDocumentId} = require("../functions/arena-tickets");
const {attemptFingerprint, attemptReceipt} = require("../functions/attempt-receipt");
const {LEGACY_ROOM_SCHEMA, ROOM_SCHEMA_V15} = require("./arena-e2e-pending-settlement-reader");
const {assertSettlementReceipt, ArenaE2eEvidenceError, LEDGER_PATH, eventPath} = require("./arena-e2e-evidence");
const {FIX_COMMIT, assessLegacySettlement: assess, serverDigest, readServerSnapshot, readLocalProof,
  readServerEconomyEvidence, checkSdkEnvironment, mutationPath} = require("./arena-e2e-legacy-settlement");
const hash = (value) => createHash("sha256").update(value).digest("hex");
const operation = "lesson_runner.SUBMIT_ATTEMPT";
const assessLegacySettlement = input => assess(input, input.reviewedEvidenceSha256);

function fixture() {
  const context = {runId: "old-run", ownerUid: "owner", ticketId: "a".repeat(64)};
  context.attemptId = attemptDocumentId(context.ticketId);
  const {ticketId, attemptId, ownerUid} = context;
  const mutationId = "lesson_runner-SUBMIT_ATTEMPT-" + attemptId;
  const entityRef = "lesson_runner:attempt:" + attemptId;
  const payload = {ownerUid, userId: ownerUid, ticketId, attemptId, lessonId: "lesson", lessonVersion: 7,
    difficulty: "HARD", completionKind: "COMPLETED", answers: [], chargeClaims: "."};
  const event = {userId: ownerUid, ticketId, attemptId, lessonId: "lesson", lessonVersion: 7,
    activityKind: "ARENA", completed: true, codeAnswer: "9", percentScore: 100, receivedAtMs: 111,
    requestFingerprint: attemptFingerprint(ownerUid, payload)};
  const receipt = {...attemptReceipt({event, status: "SETTLED", settledAtMs: 111, lifePointsSpent: 50,
    reward: {nolics: 6, skillPoints: 62}}), ticketId, nolicsSpent: 1};
  event.settlementReceipt = receipt;
  event.settlementResponse = {accepted: 1, reward: receipt.reward, lifePoints: 500, receipts: [receipt]};
  const ticket = {uid: ownerUid, ticketId, attemptId, mode: "SELECTED", difficulty: "HARD", lessonId: "lesson",
    lessonVersion: 7, authorUid: "author", priceNolics: 1, lifeCost: 50, issuedAtMs: 1, status: "SETTLED",
    terminal: {receiptId: `arena-settle:${ticketId}`, attemptId, settledAtMs: 100, percentScore: 100,
      consumedNolics: 1, consumedLifePoints: 50, authorCreditNolics: 0, sinkNolics: 0, royaltyReserveDeltaNolics: 1}};
  const mutation = {uid: ownerUid, mutationId, operation, state: "completed", result: event.settlementResponse,
    requestFingerprint: attemptFingerprint(ownerUid, {operation, payload, expectedVersion: null})};
  const ledger = (id, uid, currency, delta, reason, sourceId) => ({id, uid, currency, delta, reason, sourceId, atMs: 111});
  const snapshot = {ticket, event, mutation, ticketRows: [
    ledger("reserve-money", ownerUid, "nolics", -1, "arena_ticket_reserve", ticketId),
    ledger("reserve-life", ownerUid, "standardChargePoints", -50, "arena_ticket_reserve", ticketId),
    ledger("carry", "arena-carry:author", "nolics", 1, "arena_royalty_carry", ticketId),
  ], attemptRows: [
    ledger("reward-money", ownerUid, "nolics", 6, "attempt_reward", attemptId),
    ledger("reward-skill", ownerUid, "skillPoints", 62, "attempt_reward", attemptId),
  ]};
  const bodyJson = JSON.stringify(payload);
  const local = {schemaVersion: LEGACY_ROOM_SCHEMA.version, identityHash: LEGACY_ROOM_SCHEMA.identityHash, outboxCount: 0,
    tickets: [{ownerUid, ticketId, attemptId, serverStatus: "RESERVED", localUse: "LAUNCHED", decision: "SETTLE"}],
    commands: [{ownerUid, ticketId, attemptId, commandId: attemptId, commandKind: "SETTLE", state: "PENDING",
      bodyJson, bodySha256: hash(bodyJson)}],
    intents: [{ownerUid, attemptId, mutationId, operation, entityRef, expectedVersion: null, payload: bodyJson}],
    receipts: [{ownerUid, mutationId, operation, entityRef, payloadJson: JSON.stringify({replayed: false,
      result: event.settlementResponse})}]};
  const incident = {schemaVersion: 1, incidentId: "clock-before-fix", kind: "PRE_CANONICAL_SETTLEMENT_CLOCK",
    fixCommit: FIX_COMMIT, ownerSha256: hash(ownerUid), ticketSha256: hash(ticketId), attemptSha256: hash(attemptId),
    terminalSettledAtMs: 100, eventSettledAtMs: 111, serverEvidenceSha256: serverDigest(context, snapshot)};
  return {context, snapshot, local, incident, reviewedEvidenceSha256: incident.serverEvidenceSha256};
}

test("production default refuses a self-authenticating incident file for an unreviewed record", () => {
  assert.throws(() => assess(fixture()), {code: "INCIDENT_NOT_REVIEWED"});
});

test("a supplied demo environment cannot hide the SDK process environment", () => {
  const safe = {GCLOUD_PROJECT: "demo-schoolquiz-mvp", FIRESTORE_EMULATOR_HOST: "schoolquiz-mvp-firebase:8080"};
  assert.doesNotThrow(() => checkSdkEnvironment(safe, {...safe}));
  for (const runtime of [{}, {...safe, FIRESTORE_EMULATOR_HOST: "production:443"},
    {...safe, GOOGLE_APPLICATION_CREDENTIALS: "/private/key"}, {...safe, FIREBASE_CONFIG: "{}"}]) {
    assert.throws(() => checkSdkEnvironment(safe, runtime), {code: "UNSAFE_ENVIRONMENT"});
  }
});

test("current economy evidence binds payer, author, premium headers and royalty carry without any write", async () => {
  const {context, snapshot} = fixture();
  const store = new Map([["users/owner", {nolics: 7}], ["users/author", {nolics: 8}],
    ["arena_royalty_accounts/author", {reservedNolics: 1}]]);
  const paths = [];
  const db = {doc: path => ({get: async () => { paths.push(path); return {exists: store.has(path), data: () => store.get(path)}; }})};
  const before = await readServerEconomyEvidence(db, context, snapshot.ticket);
  assert.equal(before.serverEconomyDocumentCount, 5);
  assert.deepEqual(paths.sort(), ["users/owner", "users/author", "premium_accounts/owner", "premium_accounts/author", "arena_royalty_accounts/author"].sort());
  assert.deepEqual(await readServerEconomyEvidence(db, context, snapshot.ticket), before);
  store.get("arena_royalty_accounts/author").reservedNolics++;
  assert.notEqual((await readServerEconomyEvidence(db, context, snapshot.ticket)).serverEconomySha256, before.serverEconomySha256);
  await assert.rejects(readServerEconomyEvidence(db, context, {...snapshot.ticket, authorUid: "else/where"}), {code: "INVALID_ACCOUNT_ID"});
});

test("pinned historical record permits only local recovery, strict acceptance still fails", () => {
  const input = fixture();
  const preimage = JSON.stringify(input);
  const result = assessLegacySettlement(input);
  assert.equal(result.status, "LEGACY_RECOVERY_ELIGIBLE");
  assert.equal(result.freshAcceptance, "FAIL");
  assert.equal(result.permittedAction, "READ_SERVER_TICKET_ONLY");
  assert.equal(result.operationSubmissionAllowed, false);
  assert.equal(result.documentWrites, 0);
  assert.equal(result.localWrites, 0);
  assert.equal(result.deviceCalls, 0);
  assert.equal(result.localState, "STALE");
  assert.equal(result.terminalSettledAtMs, 100);
  assert.equal(result.eventSettledAtMs, 111);
  assert.equal(JSON.stringify(input), preimage);
  assert.throws(() => assertSettlementReceipt(input.snapshot.ticket, input.snapshot.ticket.terminal,
    input.snapshot.event.settlementReceipt), {code: "SETTLEMENT_ECONOMY_MISMATCH"});
  for (const privateValue of [input.context.ownerUid, input.context.ticketId, input.context.attemptId,
    "answers", "lifePoints", "payloadJson", "codeAnswer"]) assert.equal(JSON.stringify(result).includes(privateValue), false);
});

test("historical recovery rejects the exact current v15 Room schema pair", () => {
  const input = fixture();
  input.local.schemaVersion = ROOM_SCHEMA_V15.version;
  input.local.identityHash = ROOM_SCHEMA_V15.identityHash;
  assert.throws(() => assessLegacySettlement(input), {code: "LOCAL_PROOF_INVALID"});
});

test("post-recovery proof keeps the identical server digest and a distinct local proof", () => {
  const input = fixture();
  const before = assessLegacySettlement(input);
  input.local.tickets[0].serverStatus = "SETTLED";
  input.local.commands[0].state = "ACKNOWLEDGED";
  const after = assessLegacySettlement(input);
  assert.equal(after.localState, "RECONCILED");
  assert.equal(before.serverEvidenceSha256, after.serverEvidenceSha256);
  assert.notEqual(before.localProofSha256, after.localProofSha256);
  assert.equal(after.freshAcceptance, "FAIL");
});

const corruptions = {
  "missing incident": (x) => { x.incident = null; },
  "different owner incident": (x) => { x.incident.ownerSha256 = hash("other"); },
  "different ticket incident": (x) => { x.incident.ticketSha256 = hash("other"); },
  "different attempt incident": (x) => { x.incident.attemptSha256 = hash("other"); },
  "different version": (x) => { x.incident.fixCommit = "f".repeat(40); },
  "changed immutable server snapshot": (x) => { x.snapshot.event.extraField = true; },
  "another timestamp is not a tolerance": (x) => { x.snapshot.ticket.terminal.settledAtMs++; },
  "current exact timestamp is not legacy": (x) => { x.snapshot.ticket.terminal.settledAtMs = 111; },
  "opposite timestamp direction": (x) => { x.snapshot.ticket.terminal.settledAtMs = 112; },
  "mismatched money": (x) => { x.snapshot.event.settlementReceipt.nolicsSpent++; },
  "mismatched score": (x) => { x.snapshot.ticket.terminal.percentScore = 80; },
  "missing ledger row": (x) => { x.snapshot.ticketRows.pop(); },
  "duplicate ledger row": (x) => { x.snapshot.attemptRows.push({...x.snapshot.attemptRows[0], id: "second"}); },
  "foreign receipt": (x) => { x.local.receipts[0].ownerUid = "other"; },
  "multiple local receipts": (x) => { x.local.receipts.push({...x.local.receipts[0]}); },
  "pending delivery still exists": (x) => { x.local.outboxCount = 1; },
  "wrong Room identity": (x) => { x.local.identityHash = "other"; },
  "future Room schema": (x) => { x.local.schemaVersion = 15; },
  "different intent body": (x) => { x.local.intents[0].payload = JSON.stringify({}); },
  "corrupt journal body hash": (x) => { x.local.commands[0].bodySha256 = "f".repeat(64); },
  "wrong local operation": (x) => { x.local.receipts[0].operation = "lesson_runner.SUBMIT_RATING"; },
  "wrong entity reference": (x) => { x.local.receipts[0].entityRef = "lesson_runner:attempt:other"; },
  "local receipt not event response": (x) => { x.local.receipts[0].payloadJson = '{"result":{"accepted":0}}'; },
  "mutation not completed": (x) => { x.snapshot.mutation.state = "reserved"; },
  "wrong queue fingerprint": (x) => { x.snapshot.mutation.requestFingerprint = "f".repeat(64); },
  "wrong event fingerprint": (x) => { x.snapshot.event.requestFingerprint = "f".repeat(64); },
  "wrong lifecycle state": (x) => { x.local.tickets[0].serverStatus = "CANCELLED"; },
};
for (const [name, mutate] of Object.entries(corruptions)) test("fail closed: " + name, () => {
  const input = fixture();
  mutate(input);
  assert.throws(() => assessLegacySettlement(input), ArenaE2eEvidenceError);
});

test("server reader resolves exact paths and bounded sources, exposes no writer", async () => {
  const {context, snapshot} = fixture();
  const paths = [], queries = [];
  const id = "lesson_runner-SUBMIT_ATTEMPT-" + context.attemptId;
  const wantedMutationPath = `mutation_keys/${hash(id).slice(0, 12)}_${id.slice(0, 80)}`;
  assert.equal(mutationPath(context), wantedMutationPath);
  const docs = new Map([[`arena_entry_tickets/${context.ticketId}`, snapshot.ticket],
    [eventPath(context.attemptId), snapshot.event], [wantedMutationPath, snapshot.mutation]]);
  const wrap = (value) => ({exists: value !== undefined, data: () => value});
  const db = {doc(path) { paths.push(path); return {get: async () => wrap(docs.get(path))}; },
    collection(path) { assert.equal(path, LEDGER_PATH); return {
      where(field, op, source) { assert.equal(field, "sourceId"); assert.equal(op, "=="); return {
        limit(n) { queries.push({source, n}); return {get: async () => ({docs:
          (source === context.ticketId ? snapshot.ticketRows : snapshot.attemptRows).map(wrap)})}; },
      }; },
    }; }};
  assert.deepEqual(await readServerSnapshot(db, context), snapshot);
  assert.deepEqual(paths.sort(), [...docs.keys()].sort());
  assert.deepEqual(queries, [{source: context.ticketId, n: 17}, {source: context.attemptId, n: 17}]);
});

test("real SQLite read binds every field and rejects foreign duplicate receipt without changing DB", () => {
  const {DatabaseSync} = require("node:sqlite");
  const fs = require("node:fs");
  const path = require("node:path");
  const os = require("node:os");
  const input = fixture();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-settlement-test-"));
  const dbPath = path.join(dir, "proof.db");
  const db = new DatabaseSync(dbPath);
  try {
    db.exec("PRAGMA user_version=14; CREATE TABLE room_master_table (id INTEGER, identity_hash TEXT);" +
      "CREATE TABLE arena_ticket_records (owner_uid TEXT,ticket_id TEXT,attempt_id TEXT,server_status TEXT,local_use TEXT,decision TEXT);" +
      "CREATE TABLE arena_command_journal (owner_uid TEXT,ticket_id TEXT,attempt_id TEXT,command_kind TEXT,command_id TEXT,state TEXT,body_json TEXT,body_sha256 TEXT);" +
      "CREATE TABLE lesson_attempt_intents (owner_uid TEXT,attempt_id TEXT,mutation_id TEXT,operation TEXT,payload TEXT,entity_ref TEXT,expected_version INTEGER);" +
      "CREATE TABLE mutation_receipts (owner_uid TEXT,mutation_id TEXT,operation TEXT,entity_ref TEXT,payload_json TEXT);" +
      "CREATE TABLE outbox (mutation_id TEXT);");
    db.prepare("INSERT INTO room_master_table VALUES (42,?)").run(LEGACY_ROOM_SCHEMA.identityHash);
    const t = input.local.tickets[0], c = input.local.commands[0], i = input.local.intents[0], r = input.local.receipts[0];
    db.prepare("INSERT INTO arena_ticket_records VALUES (?,?,?,?,?,?)").run(t.ownerUid,t.ticketId,t.attemptId,t.serverStatus,t.localUse,t.decision);
    db.prepare("INSERT INTO arena_command_journal VALUES (?,?,?,?,?,?,?,?)").run(c.ownerUid,c.ticketId,c.attemptId,c.commandKind,c.commandId,c.state,c.bodyJson,c.bodySha256);
    db.prepare("INSERT INTO lesson_attempt_intents VALUES (?,?,?,?,?,?,?)").run(i.ownerUid,i.attemptId,i.mutationId,i.operation,i.payload,i.entityRef,i.expectedVersion);
    db.prepare("INSERT INTO mutation_receipts VALUES (?,?,?,?,?)").run(r.ownerUid,r.mutationId,r.operation,r.entityRef,r.payloadJson);
    const before = hash(fs.readFileSync(dbPath));
    const actual = readLocalProof(input.context, {dbPath});
    assert.deepEqual(JSON.parse(JSON.stringify(actual)), input.local);
    assert.equal(assessLegacySettlement({...input, local: actual}).localState, "STALE");
    assert.equal(hash(fs.readFileSync(dbPath)), before);
    db.prepare("INSERT INTO mutation_receipts VALUES (?,?,?,?,?)").run("foreign",r.mutationId,r.operation,r.entityRef,r.payloadJson);
    assert.throws(() => assessLegacySettlement({...input, local: readLocalProof(input.context, {dbPath})}),
      {code: "LOCAL_PROOF_AMBIGUOUS"});
    db.exec("PRAGMA user_version=" + ROOM_SCHEMA_V15.version);
    db.prepare("UPDATE room_master_table SET identity_hash=? WHERE id=42").run(ROOM_SCHEMA_V15.identityHash);
    assert.throws(() => readLocalProof(input.context, {dbPath}), {code: "LOCAL_PROOF_INVALID"});
  } finally { db.close(); fs.rmSync(dir, {recursive: true}); }
});
