"use strict";

// This uses only fake ADB/Maestro/evidence binaries.  It proves composition: the online leaf is
// inside the guard's lock before the cut, the offline leaf sees the cut, raw Room copies vanish,
// and a failed preparation never changes the device transport topology.

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {spawnSync} = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const ROOT = __dirname;
const runner = path.join(ROOT, "run-arena-offline-settlement.sh");
const leaf = path.join(ROOT, "run-arena-offline-settlement-leaf.sh");
const guard = path.join(ROOT, "offline-transport-guard.js");
const serial = "offline-settlement-test-serial";
const ownerUid = "offline-test-owner";
const ticketId = "a".repeat(64);
const attemptId = createHash("sha256").update(JSON.stringify(["arena-attempt", ticketId])).digest("hex");
const certificate = "b".repeat(64);

const fakeAdbSource = String.raw`#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
const args = process.argv.slice(2);
if (args[0] !== "-s" || !args[1]) process.exit(64);
const command = args.slice(2);
state.log.push({type: "adb", command, wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
function save() { const temp = statePath + "." + process.pid + ".tmp"; fs.writeFileSync(temp, JSON.stringify(state)); fs.renameSync(temp, statePath); }
function ok(value = "") { save(); process.stdout.write(value); process.exit(0); }
function fail() { save(); process.exit(1); }
if (command[0] === "get-state") ok("device\n");
if (command[0] === "reverse" && command[1] === "--list") {
  ok(state.mappings.map((port) => "transport-id tcp:" + port + " tcp:" + port).join("\n") + (state.mappings.length ? "\n" : ""));
}
if (command[0] === "reverse" && command[1] === "--remove") {
  const port = Number(String(command[2]).slice(4));
  if (!state.mappings.includes(port)) fail();
  state.mappings = state.mappings.filter((item) => item !== port); ok();
}
if (command[0] === "reverse" && command[1] === command[2] && /^tcp:[0-9]+$/.test(command[1] || "")) {
  const port = Number(command[1].slice(4));
  if (state.mappings.includes(port)) fail();
  state.mappings.push(port); ok();
}
if (command[0] === "shell" && command[1] === "settings" && command[2] === "get" && command[3] === "global") {
  if (command[4] === "wifi_on") ok(String(state.wifi) + "\n");
  if (command[4] === "mobile_data") ok(String(state.data) + "\n");
  fail();
}
if (command[0] === "shell" && command[1] === "svc" && (command[2] === "wifi" || command[2] === "data")) {
  const field = command[2] === "wifi" ? "wifi" : "data";
  state[field] = command[3] === "enable" ? 1 : command[3] === "disable" ? 0 : state[field]; ok();
}
if (command[0] === "shell" && command[1] === "am" && command[2] === "force-stop") ok();
if (command[0] === "shell" && command[1] === "pm" && command[2] === "path") ok("package:/data/app/test/base.apk\n");
if (command[0] === "shell" && command[1] === "sha256sum" && command[2] === "/data/app/test/base.apk") ok(process.env.FAKE_APK_SHA + "  /data/app/test/base.apk\n");
if (command[0] === "exec-out" && command[1] === "run-as" && command[3] === "cat") ok("fake-room-db");
if (command[0] === "shell" && command[1] === "run-as" && command[3] === "ls" && command[4] === "shared_prefs") {
  ok("com.google.firebase.auth.api.Store.W0RFRkFVTFRd+MToxMjM0NTY3ODkwMDA6YW5kcm9pZDowMDAwMDAwMDAwMDAwMDAwMDAwMDAw.xml\n");
}
if (command[0] === "shell" && command[1] === "run-as" && command[3] === "ls") fail();
fail();
`;

const fakeFlockSource = String.raw`#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const args = process.argv.slice(2);
if (args[0] !== "-n" || !args[1]) process.exit(64);
const held = args[1] + ".held";
try { fs.mkdirSync(held, {mode: 0o700}); } catch (error) { process.exit(error && error.code === "EEXIST" ? 1 : 2); }
let closed = false;
function close() { if (closed) return; closed = true; try { fs.rmdirSync(held); } catch (_) {} process.exit(0); }
process.on("SIGTERM", close); process.on("SIGINT", close); process.stdout.write("x"); setInterval(() => {}, 1000);
`;

const fakeMaestroSource = String.raw`#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
const args = process.argv.slice(2);
const flow = args.at(-1);
state.log.push({type: "maestro", flow, wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temp = statePath + "." + process.pid + ".tmp"; fs.writeFileSync(temp, JSON.stringify(state)); fs.renameSync(temp, statePath);
if (process.env.FAKE_MUTATE_INPUT && flow.endsWith(process.env.FAKE_MUTATE_AFTER_FLOW || "00-identity-preflight.yaml")) {
  fs.appendFileSync(process.env.FAKE_MUTATE_INPUT, "\n# fake composition mutation\n");
}
if (process.env.FAKE_MAESTRO_FAIL_FLOW && flow.endsWith(process.env.FAKE_MAESTRO_FAIL_FLOW)) process.exit(9);
process.exit(0);
`;

const fakeApksignerSource = String.raw`#!/usr/bin/env node
process.stdout.write("Signer #1 certificate SHA-256 digest: " + process.env.FAKE_CERT_SHA + "\n");
`;

const fakeTicketDiscoverySource = String.raw`#!/usr/bin/env node
"use strict";
const crypto = require("node:crypto");
const args = process.argv.slice(2);
const flag = (name) => args[args.indexOf(name) + 1];
const ticketId = "a".repeat(64);
const attemptId = crypto.createHash("sha256").update(JSON.stringify(["arena-attempt", ticketId])).digest("hex");
if (args[0] === "baseline") {
  process.stdout.write(JSON.stringify({status: "BASELINE", runId: flag("--run-id")}) + "\n");
} else if (args[0] === "discover") {
  process.stdout.write(JSON.stringify({status: "CANDIDATE", ticketId, attemptId}) + "\n");
} else process.exit(64);
`;

const fakeContextReaderSource = String.raw`#!/usr/bin/env node
"use strict";
const args = process.argv.slice(2); const flag = (name) => args[args.indexOf(name) + 1];
process.stdout.write(JSON.stringify({
  status: "CONTEXT", runId: flag("--run-id"), ownerUid: flag("--owner-uid"), ticketId: flag("--ticket-id"), attemptId: flag("--attempt-id"),
  lessonId: "lesson-1", lessonVersion: 1, frozenPriceNolics: 5, mode: "SELECTED", difficulty: "HARD",
  ticketStatus: "RESERVED", settlementReceipt: null, event: null, resultEventDocumentExists: process.env.FAKE_CONTEXT_RESULT_EVENT_DOCUMENT_EXISTS === "1",
  oracleVersion: "arena-e2e-context-reader-v2", documentWrites: 0,
}) + "\n");
`;

const fakeProgressReaderSource = String.raw`#!/usr/bin/env node
"use strict";
const args = process.argv.slice(2); const flag = (name) => args[args.indexOf(name) + 1];
process.stdout.write(JSON.stringify({
  status: "CONTEXT", runId: flag("--run-id"), ticketId: flag("--ticket-id"), attemptId: flag("--attempt-id"),
  ticketState: {serverStatus: "RESERVED", localUse: "LAUNCHED", decision: "NONE", snapshotSha256: "c".repeat(64)},
}) + "\n");
`;

const fakePendingReaderSource = String.raw`#!/usr/bin/env node
"use strict";
const args = process.argv.slice(2); const flag = (name) => args[args.indexOf(name) + 1];
process.stdout.write(JSON.stringify({
  status: "CONTEXT", runId: flag("--run-id"), ticketId: flag("--ticket-id"), attemptId: flag("--attempt-id"),
  ticketState: {serverStatus: "RESERVED", localUse: "LAUNCHED", decision: "SETTLE", snapshotSha256: "c".repeat(64)},
  pendingSettlement: {commandState: "PENDING", firstIntentPresent: true, localReceiptCount: 0, localScoredAttemptRows: 0, localScoredAnswerRows: 0},
}) + "\n");
`;

const fakePreparationPreflightReaderSource = String.raw`#!/usr/bin/env node
"use strict";
const crypto = require("node:crypto");
const args = process.argv.slice(2);
const flag = (name) => args[args.indexOf(name) + 1];
const hash = (value) => crypto.createHash("sha256").update(value).digest("hex");
process.stdout.write(JSON.stringify({
  status: "PASS", readerVersion: "arena-e2e-preparation-preflight-reader-v1", sqliteAdapter: "fake-read-only",
  runId: flag("--run-id"), ownerUidSha256: hash(flag("--owner-uid")), accountRefSha256: hash(flag("--account-ref")),
  room: {schemaVersion: 14, schemaIdentityHash: "ca5439bdc37decd3f21cb843d10bb3f4", databaseBytes: 12, walBytes: 0, shmBytes: 0},
  auth: {cachedUser: true, accessTokenBound: true},
  quiescence: {outboxRows: 0, unresolvedArenaCommands: 0, reservedArenaTickets: 0, orphanedSubmitIntents: 0, orphanedRatingIntents: 0},
  documentWrites: 0,
}) + "\n");
`;

function writeExecutable(file, source) {
  fs.writeFileSync(file, source, {mode: 0o700});
}

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "arena-offline-settlement-"));
  const outputRoot = path.join(root, "results");
  const locks = path.join(root, "locks");
  fs.mkdirSync(outputRoot, {mode: 0o700});
  fs.mkdirSync(locks, {mode: 0o700});
  const candidate = path.join(root, "candidate.apk");
  fs.writeFileSync(candidate, "candidate", {mode: 0o600});
  const apkSha = createHash("sha256").update(fs.readFileSync(candidate)).digest("hex");
  const runContext = path.join(root, "run-context.env");
  fs.writeFileSync(runContext, [
    "E2B_RUN_ID=offline-run-001", "E2B_CANDIDATE_HEAD=" + "d".repeat(40), "E2B_APP_ID=com.tpov.schoolquiz.mvp",
    "E2B_APK_SHA256=" + apkSha, "E2B_APK_CERT_SHA256=" + certificate, "E2B_ACCOUNT_REF=retained-test-account",
    "E2B_OWNER_UID=" + ownerUid, "E2B_LESSON_ID=lesson-1", "E2B_LESSON_VERSION=1",
    "E2B_EXPECTED_FROZEN_PRICE_NOLICS=5", "E2B_EXPECTED_MODE=SELECTED", "E2B_EXPECTED_DIFFICULTY=HARD", "E2B_RATING=2",
  ].join("\n") + "\n", {mode: 0o600});
  const selectorKeys = [
    "E2B_SELECTOR_INTERNET", "E2B_SELECTOR_ARENA_TITLE", "E2B_SELECTOR_REFRESH_BALANCE", "E2B_SELECTOR_NOLICS_BALANCE",
    "E2B_SELECTOR_LIFE_BALANCE", "E2B_SELECTOR_LOAD_LESSONS", "E2B_SELECTOR_CATALOG_LOADED", "E2B_SELECTOR_TARGET_LESSON",
    "E2B_SELECTOR_FIXTURE_AUTHOR", "E2B_SELECTOR_FROZEN_PRICE", "E2B_SELECTOR_SELECTED_PREPARE", "E2B_SELECTOR_PREPARE_DIALOG",
    "E2B_SELECTOR_DIFFICULTY_HARD", "E2B_SELECTOR_HARD_SELECTED", "E2B_SELECTOR_PREPARE_CONFIRM", "E2B_SELECTOR_PREPARED_TICKET",
    "E2B_SELECTOR_SHOW_LOCAL_TICKETS", "E2B_SELECTOR_TICKET_LABEL_TEMPLATE", "E2B_SELECTOR_TICKET_START", "E2B_SELECTOR_TICKET_OPEN",
    "E2B_SELECTOR_QUESTION", "E2B_SELECTOR_FIRST_ANSWER", "E2B_SELECTOR_SAVED_ANSWER", "E2B_SELECTOR_RESUME_DIALOG",
    "E2B_SELECTOR_RESUME_OR_SAVED", "E2B_SELECTOR_RESUME_CONTINUE", "E2B_SELECTOR_SECOND_QUESTION", "E2B_SELECTOR_SECOND_ANSWER",
    "E2B_SELECTOR_CONTINUE_LESSON", "E2B_SELECTOR_SYNCHRONIZE", "E2B_SELECTOR_SETTLE_PENDING",
  ];
  const selectors = path.join(root, "selectors.env");
  fs.writeFileSync(selectors, selectorKeys.map((key) => key + "=" + (key === "E2B_SELECTOR_TICKET_LABEL_TEMPLATE" ? "ticket {{ticketId}}" : "x")).join("\n") + "\n", {mode: 0o600});
  const statePath = path.join(root, "adb.json");
  fs.writeFileSync(statePath, JSON.stringify({wifi: 1, data: 1, mappings: [9099, 8080, 5001], log: []}), {mode: 0o600});
  const adb = path.join(root, "adb"); const maestro = path.join(root, "maestro"); const apksigner = path.join(root, "apksigner");
  const flock = path.join(root, "flock"); const discovery = path.join(root, "discovery.js"); const context = path.join(root, "context.js");
  const progress = path.join(root, "progress.js"); const pending = path.join(root, "pending.js");
  const preparationPreflight = path.join(root, "preparation-preflight.js");
  writeExecutable(adb, fakeAdbSource); writeExecutable(maestro, fakeMaestroSource); writeExecutable(apksigner, fakeApksignerSource);
  writeExecutable(flock, fakeFlockSource); writeExecutable(discovery, fakeTicketDiscoverySource); writeExecutable(context, fakeContextReaderSource);
  writeExecutable(progress, fakeProgressReaderSource); writeExecutable(pending, fakePendingReaderSource);
  writeExecutable(preparationPreflight, fakePreparationPreflightReaderSource);
  const sealedArenaMatrix = path.join(root, "arena-matrix");
  const sealedLeaf = path.join(sealedArenaMatrix, "run-arena-offline-settlement-leaf.sh");
  const sealedFlowRoot = path.join(root, "arena-e2b", "flows");
  fs.mkdirSync(sealedArenaMatrix, {recursive: true, mode: 0o700});
  fs.mkdirSync(sealedFlowRoot, {recursive: true, mode: 0o700});
  fs.copyFileSync(leaf, sealedLeaf);
  fs.chmodSync(sealedLeaf, 0o700);
  const flowNames = [
    "00-identity-preflight.yaml", "01-selected-hard-prepare.yaml", "02-start-answer-relaunch.yaml",
    "02-resume-saved-answer-and-finish.yaml", "03-finish.yaml",
  ];
  const flowSourceRoots = [
    path.join(ROOT, "..", "arena-e2b", "flows"),
    path.join(ROOT, "..", "..", "..", "arena-e2b", "flows"),
  ];
  for (const name of flowNames) {
    const source = flowSourceRoots.map((directory) => path.join(directory, name)).find((file) => fs.existsSync(file));
    assert.ok(source, "fixture source flow is absent: " + name);
    const destination = path.join(sealedFlowRoot, name);
    fs.copyFileSync(source, destination);
    fs.chmodSync(destination, 0o600);
  }
  const javaHome = path.join(root, "java"); fs.mkdirSync(path.join(javaHome, "bin"), {recursive: true, mode: 0o700});
  writeExecutable(path.join(javaHome, "bin", "java"), "#!/usr/bin/env sh\nexit 0\n");
  return {
    root, outputRoot, locks, candidate, apkSha, runContext, selectors, statePath, adb, maestro, apksigner, flock,
    discovery, context, progress, pending, preparationPreflight, javaHome, leaf: sealedLeaf,
    identityFlow: path.join(sealedFlowRoot, "00-identity-preflight.yaml"),
    prepareFlow: path.join(sealedFlowRoot, "01-selected-hard-prepare.yaml"),
    dispose() {
      if (process.env.KEEP_OFFLINE_SETTLEMENT_FIXTURE === "1") {
        console.error("kept offline settlement fixture: " + root);
        return;
      }
      fs.rmSync(root, {recursive: true, force: true});
    }};
}

function state(value) { return JSON.parse(fs.readFileSync(value.statePath, "utf8")); }

function invoke(value, env = {}) {
  const result = spawnSync(runner, [
    "--run-id", "offline-run-001", "--candidate-head", "d".repeat(40), "--apk", value.candidate,
    "--run-context", value.runContext, "--selectors", value.selectors, "--output-root", value.outputRoot,
    "--serial", serial, "--control-channel", "adb-independent", "--guard", guard, "--leaf", value.leaf,
    "--ticket-discovery", value.discovery, "--context-reader", value.context, "--attempt-progress-reader", value.progress,
    "--pending-settlement-reader", value.pending, "--preparation-preflight-reader", value.preparationPreflight,
    "--adb", value.adb, "--maestro", value.maestro,
    "--apksigner", value.apksigner, "--java-home", value.javaHome, "--node", process.execPath,
    "--flock", value.flock, "--lock-root", value.locks,
  ], {
    encoding: "utf8", timeout: 45_000,
    env: {...process.env, FAKE_ADB_STATE: value.statePath, FAKE_APK_SHA: value.apkSha, FAKE_CERT_SHA: certificate, ...env},
  });
  return result;
}

function runOutputs(value) {
  return fs.readdirSync(value.outputRoot)
    .filter((name) => name.includes(".offline-settle."))
    .sort()
    .map((name) => path.join(value.outputRoot, name));
}

function onlyOutput(value) {
  const outputs = runOutputs(value);
  assert.equal(outputs.length, 1);
  return outputs[0];
}

function testSealedSequenceHappyPath() {
  const value = fixture();
  try {
    const outcome = invoke(value);
    assert.equal(outcome.status, 0, outcome.stderr);
    const output = onlyOutput(value);
    const handoff = JSON.parse(fs.readFileSync(path.join(output, "offline-settlement-handoff.json"), "utf8"));
    const guardResult = JSON.parse(fs.readFileSync(path.join(output, "transport-guard-result.json"), "utf8"));
    assert.equal(handoff.status, "PASS");
    assert.equal(handoff.overallStatus, "NOT_RUN");
    assert.equal(guardResult.status, "PASS");
    assert.equal(guardResult.preCut.completed, true);
    assert.equal(guardResult.cut.confirmed, true);
    assert.equal(guardResult.restore.confirmed, true);
    assert.equal(guardResult.sequenceCapabilities.preCutIssued, true);
    assert.equal(guardResult.sequenceCapabilities.offlineIssued, true);
    assert.equal(guardResult.preCut.processGroupClean, true);
    assert.equal(guardResult.child.processGroupClean, true);
    assert.equal(handoff.artifacts["transport-guard-result.json"].length, 64);
    assert.equal(handoff.artifacts["preparation-preflight.json"].length, 64);
    assert.equal(JSON.stringify(handoff).includes(ticketId), false);
    assert.equal(fs.readdirSync(output).some((name) => name.startsWith("raw-room-")), false);
    const preparationPreflight = JSON.parse(fs.readFileSync(path.join(output, "preparation-preflight.json"), "utf8"));
    assert.equal(preparationPreflight.status, "PASS");
    assert.equal(preparationPreflight.readerVersion, "arena-e2e-preparation-preflight-reader-v1");
    assert.equal(JSON.stringify(preparationPreflight).includes(ownerUid), false);
    const snapshot = state(value);
    assert.equal(snapshot.wifi, 1); assert.equal(snapshot.data, 1); assert.deepEqual(snapshot.mappings.sort(), [5001, 8080, 9099]);
    const preCut = snapshot.log.find((entry) => entry.type === "maestro" && entry.flow.endsWith("00-identity-preflight.yaml"));
    const offline = snapshot.log.find((entry) => entry.type === "maestro" && entry.flow.endsWith("02-start-answer-relaunch.yaml"));
    assert.deepEqual({wifi: preCut.wifi, data: preCut.data, mappings: preCut.mappings}, {wifi: 1, data: 1, mappings: [9099, 8080, 5001]});
    assert.deepEqual({wifi: offline.wifi, data: offline.data, mappings: offline.mappings}, {wifi: 0, data: 0, mappings: []});
    assert.equal(snapshot.log.some((entry) => entry.type === "adb" && entry.command.includes("--remove-all")), false);
  } finally { value.dispose(); }
}

function testInputMutationStopsBeforePrepare() {
  const value = fixture();
  try {
    const outcome = invoke(value, {
      FAKE_MUTATE_INPUT: value.prepareFlow,
      FAKE_MUTATE_AFTER_FLOW: "00-identity-preflight.yaml",
    });
    assert.equal(outcome.status, 2);
    const output = onlyOutput(value);
    const guardResult = JSON.parse(fs.readFileSync(path.join(output, "transport-guard-result.json"), "utf8"));
    assert.equal(guardResult.status, "FAIL");
    assert.equal(guardResult.errorCode, "PRE_CUT_CHILD_FAILED");
    assert.equal(guardResult.cut.attempted, false);
    const snapshot = state(value);
    assert.equal(snapshot.wifi, 1); assert.equal(snapshot.data, 1); assert.deepEqual(snapshot.mappings.sort(), [5001, 8080, 9099]);
    assert.equal(snapshot.log.some((entry) => entry.type === "maestro" && entry.flow.endsWith("00-identity-preflight.yaml")), true);
    assert.equal(snapshot.log.some((entry) => entry.type === "maestro" && entry.flow.endsWith("01-selected-hard-prepare.yaml")), false);
    assert.equal(fs.existsSync(path.join(output, "prepared-ticket.json")), false);
  } finally { value.dispose(); }
}

function testRecoveryRecordBlocksSecondPreparation() {
  const value = fixture();
  try {
    const first = invoke(value, {FAKE_CONTEXT_RESULT_EVENT_DOCUMENT_EXISTS: "1"});
    assert.equal(first.status, 2);
    const firstOutput = onlyOutput(value);
    assert.equal(fs.existsSync(path.join(firstOutput, "prepared-ticket.json")), true);
    const recoveryDir = path.join(value.outputRoot, "offline-settle-recovery");
    const recoveryRecords = fs.readdirSync(recoveryDir);
    assert.equal(recoveryRecords.length, 1);
    const recoveryRecord = path.join(recoveryDir, recoveryRecords[0]);
    const retained = JSON.parse(fs.readFileSync(recoveryRecord, "utf8"));
    assert.equal(retained.status, "TICKET_DISCOVERED");
    const beforeSecondRun = state(value).log;

    const second = invoke(value);
    assert.equal(second.status, 0, second.stderr);
    const outputs = runOutputs(value);
    assert.equal(outputs.length, 2);
    const secondOutput = outputs.find((output) => output !== firstOutput);
    assert.ok(secondOutput);
    const secondResult = JSON.parse(fs.readFileSync(path.join(secondOutput, "result.json"), "utf8"));
    assert.equal(secondResult.status, "NOT_RUN");
    assert.equal(fs.existsSync(path.join(secondOutput, "transport-guard-result.json")), false);
    assert.deepEqual(state(value).log, beforeSecondRun);
    assert.deepEqual(JSON.parse(fs.readFileSync(recoveryRecord, "utf8")), retained);
  } finally { value.dispose(); }
}

function testPreCutFailureDoesNotCutTransport() {
  const value = fixture();
  try {
    const outcome = invoke(value, {FAKE_MAESTRO_FAIL_FLOW: "00-identity-preflight.yaml"});
    assert.equal(outcome.status, 2);
    const output = onlyOutput(value);
    const guardResult = JSON.parse(fs.readFileSync(path.join(output, "transport-guard-result.json"), "utf8"));
    assert.equal(guardResult.status, "FAIL");
    assert.equal(guardResult.errorCode, "PRE_CUT_CHILD_FAILED");
    assert.equal(guardResult.cut.attempted, false);
    const snapshot = state(value);
    assert.equal(snapshot.wifi, 1); assert.equal(snapshot.data, 1); assert.deepEqual(snapshot.mappings.sort(), [5001, 8080, 9099]);
    assert.equal(snapshot.log.some((entry) => entry.type === "adb" && entry.command[0] === "reverse" && entry.command[1] === "--remove"), false);
  } finally { value.dispose(); }
}

function testLeafStaticSafetyContract() {
  const source = fs.readFileSync(leaf, "utf8");
  assert.equal(/reverse\s+--remove(?:\s|$)/.test(source), false);
  assert.equal(/reverse\s+--remove-all/.test(source), false);
  assert.equal(/shell\s+svc\s+(?:wifi|data)\s+(?:enable|disable)/.test(source), false);
  assert.equal(/\binstall\s+(?:-r|--)/.test(source), false);
  assert.equal(/\buninstall\b/.test(source), false);
  assert.equal(/\bshell\s+pm\s+clear\b/.test(source), false);
  assert.equal(/03-sync-pending-settlement\.yaml/.test(source), false);
  assert.equal(/04-rate-two\.yaml/.test(source), false);
  assert.equal(/source\s+.*run-arena-e2b/.test(source), false);
}

testLeafStaticSafetyContract();
testSealedSequenceHappyPath();
testPreCutFailureDoesNotCutTransport();
testInputMutationStopsBeforePrepare();
testRecoveryRecordBlocksSecondPreparation();
console.log("offline settlement composition tests: PASS");
