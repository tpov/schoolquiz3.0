"use strict";

// One-shot bridge for the individually pinned E499 record.  It has no retry,
// submit, prepare, rating, delivery, rewrite, or rollback operation.

const {createHash, randomBytes} = require("node:crypto");
const {spawn, spawnSync} = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const VERSION = "arena-legacy-recovery-wrapper-v1";
const LEGACY_READER_VERSION = "arena-legacy-settlement-recovery-v1";
const INCIDENT_ID = "arena-clock-e499-20260912";
const APP_ID = "com.tpov.schoolquiz.mvp";
const TARGET_SERIAL = "172.17.0.5:5555";
const PROJECT = "demo-schoolquiz-mvp";
const FIRESTORE_HOST = "schoolquiz-mvp-firebase:8080";
const LOCK_ROOT = "/tools/arena-e2b-device-locks";
const MAX_SNAPSHOT_BYTES = 64 * 1024 * 1024;
const FLOW_TIMEOUT_MS = 180_000;
const AUTH_STORE = /^com\.google\.firebase\.auth\.api\.Store\.[A-Za-z0-9._+=-]+\.xml$/;
const CLASSIFIER = path.resolve(__dirname, "../../../scripts/arena-e2e-legacy-settlement.js");
const FLOW = path.join(__dirname, "flows", "00-recover-one-settled-ticket.yaml");
const REQUIRED_SELECTORS = [
  "E2B_SELECTOR_INTERNET",
  "E2B_SELECTOR_ARENA_TITLE",
  "E2B_SELECTOR_SHOW_LOCAL_TICKETS",
  "E2B_SELECTOR_TICKET_LABEL_TEMPLATE",
  "E2B_SELECTOR_RECOVER_TICKET",
  "E2B_SELECTOR_TERMINAL_TICKET",
];
const EXPECTED_MAPPINGS = new Set([
  "tcp:9099:tcp:9099",
  "tcp:8080:tcp:8080",
  "tcp:5001:tcp:5001",
]);

class LegacyRecoveryError extends Error {
  constructor(code, message = code) {
    super(message);
    this.name = "LegacyRecoveryError";
    this.code = code;
  }
}

function fail(code, message) {
  throw new LegacyRecoveryError(code, message);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function safeText(value, name, max = 4096) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be safe nonempty text");
  }
  return value;
}

function hexId(value, name) {
  safeText(value, name, 64);
  if (!/^[a-f0-9]{64}$/.test(value)) fail("INVALID_ARGUMENT", name + " must be a lowercase SHA-256 ID");
  return value;
}

function regularPrivateFile(file, name) {
  let stat;
  try {
    stat = fs.lstatSync(file);
  } catch {
    fail("INPUT_NOT_FOUND", name + " is absent");
  }
  if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) {
    fail("UNSAFE_INPUT", name + " must be a private regular non-symlink file");
  }
  return stat;
}

function regularSourceFile(file, name) {
  let stat;
  try {
    stat = fs.lstatSync(file);
  } catch {
    fail("INPUT_NOT_FOUND", name + " is absent");
  }
  if (!stat.isFile() || stat.isSymbolicLink()) fail("UNSAFE_SOURCE", name + " must be a regular non-symlink file");
  return stat;
}

function privateDirectory(directory, name) {
  const requested = path.resolve(safeText(directory, name));
  let stat;
  try {
    stat = fs.lstatSync(requested);
  } catch {
    fail("INPUT_NOT_FOUND", name + " is absent");
  }
  if (!stat.isDirectory() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) {
    fail("UNSAFE_INPUT", name + " must be a private non-symlink directory");
  }
  return fs.realpathSync(requested);
}

function createOutputDirectory(root) {
  const canonicalRoot = privateDirectory(root, "output root");
  for (let index = 0; index < 8; index += 1) {
    const destination = path.join(canonicalRoot, "legacy-e499." + Date.now() + "." + randomBytes(8).toString("hex"));
    try {
      fs.mkdirSync(destination, {mode: 0o700});
      fs.chmodSync(destination, 0o700);
      return destination;
    } catch (error) {
      if (!error || error.code !== "EEXIST") throw error;
    }
  }
  fail("OUTPUT_UNAVAILABLE");
}

function writeJson(file, value) {
  const requestedParent = path.dirname(path.resolve(file));
  const directory = privateDirectory(requestedParent, "artifact directory");
  const destination = path.join(directory, path.basename(file));
  if (fs.existsSync(destination)) {
    const stat = fs.lstatSync(destination);
    if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) fail("UNSAFE_ARTIFACT");
  }
  const temporary = path.join(directory, "." + path.basename(destination) + "." + process.pid + "." + randomBytes(6).toString("hex") + ".tmp");
  const descriptor = fs.openSync(temporary, "wx", 0o600);
  try {
    fs.writeFileSync(descriptor, JSON.stringify(value, null, 2) + "\n");
    fs.fsyncSync(descriptor);
  } finally {
    fs.closeSync(descriptor);
  }
  fs.renameSync(temporary, destination);
  fs.chmodSync(destination, 0o600);
}

function readJson(file, name) {
  regularPrivateFile(file, name);
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    fail("INVALID_INPUT", name + " is not JSON");
  }
}

function contextIdentity(file) {
  const raw = readJson(file, "context");
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) fail("INVALID_CONTEXT");
  return {
    runId: safeText(raw.runId, "context.runId", 128),
    ownerUid: safeText(raw.ownerUid, "context.ownerUid", 256),
    ticketId: hexId(raw.ticketId, "context.ticketId"),
    attemptId: hexId(raw.attemptId, "context.attemptId"),
  };
}

function parseSelectors(file) {
  regularPrivateFile(file, "selectors");
  const result = {};
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const [index, line] of lines.entries()) {
    if (!line || line.startsWith("#")) continue;
    const match = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
    if (!match || !match[1].startsWith("E2B_") || Object.hasOwn(result, match[1]) || /[\r\n\u0000]/.test(match[2])) {
      fail("INVALID_SELECTORS", "invalid selectors line " + (index + 1));
    }
    result[match[1]] = match[2];
  }
  for (const key of REQUIRED_SELECTORS) {
    if (!result[key]) fail("INVALID_SELECTORS", key + " is absent");
  }
  if (result.E2B_SELECTOR_TICKET_LABEL_TEMPLATE.split("{{ticketId}}").length !== 2) {
    fail("INVALID_SELECTORS", "ticket label must contain {{ticketId}} exactly once");
  }
  return result;
}

function flowSelectors(selectors, identity) {
  return {
    E2B_SELECTOR_INTERNET: selectors.E2B_SELECTOR_INTERNET,
    E2B_SELECTOR_ARENA_TITLE: selectors.E2B_SELECTOR_ARENA_TITLE,
    E2B_SELECTOR_SHOW_LOCAL_TICKETS: selectors.E2B_SELECTOR_SHOW_LOCAL_TICKETS,
    E2B_SELECTOR_TARGET_TICKET_LABEL: selectors.E2B_SELECTOR_TICKET_LABEL_TEMPLATE.replace("{{ticketId}}", identity.ticketId),
    E2B_SELECTOR_RECOVER_TICKET: selectors.E2B_SELECTOR_RECOVER_TICKET,
    E2B_SELECTOR_TERMINAL_TICKET: selectors.E2B_SELECTOR_TERMINAL_TICKET,
  };
}

function evidenceEnvironment(source = process.env) {
  if (source.GCLOUD_PROJECT !== PROJECT || source.FIRESTORE_EMULATOR_HOST !== FIRESTORE_HOST ||
      source.GOOGLE_APPLICATION_CREDENTIALS !== undefined || source.FIREBASE_CONFIG !== undefined) {
    fail("UNSAFE_ENVIRONMENT");
  }
  const result = {...source};
  delete result.GOOGLE_APPLICATION_CREDENTIALS;
  delete result.FIREBASE_CONFIG;
  result.GCLOUD_PROJECT = PROJECT;
  result.FIRESTORE_EMULATOR_HOST = FIRESTORE_HOST;
  return result;
}

function normalized(value) {
  if (Buffer.isBuffer(value)) return {type: "buffer", base64: value.toString("base64")};
  if (value === null || ["string", "number", "boolean"].includes(typeof value)) return value;
  if (typeof value === "bigint") return {type: "bigint", value: value.toString()};
  if (Array.isArray(value)) return value.map(normalized);
  if (value && typeof value === "object") {
    const result = {};
    for (const key of Object.keys(value).sort()) result[key] = normalized(value[key]);
    return result;
  }
  fail("INVALID_ROOM_VALUE");
}

function stableJson(value) {
  return JSON.stringify(normalized(value));
}

function same(left, right) {
  return stableJson(left) === stableJson(right);
}

function normalizedTables(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail("INVALID_ROOM_SNAPSHOT");
  const result = {};
  for (const name of Object.keys(value).sort()) {
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name) || !Array.isArray(value[name])) fail("INVALID_ROOM_SNAPSHOT");
    result[name] = value[name].map(normalized).sort((left, right) => stableJson(left).localeCompare(stableJson(right)));
  }
  return result;
}

function quoteIdentifier(name) {
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) fail("INVALID_ROOM_SCHEMA");
  return "\"" + name + "\"";
}

function readRoomDatabase(dbPath) {
  let DatabaseSync;
  try {
    ({DatabaseSync} = require("node:sqlite"));
  } catch {
    fail("NODE_SQLITE_UNAVAILABLE");
  }
  let database;
  try {
    database = new DatabaseSync(dbPath, {readOnly: true});
    const names = database.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all()
      .map((row) => row.name);
    const tables = {};
    for (const name of names) tables[name] = database.prepare("SELECT * FROM " + quoteIdentifier(name)).all();
    return normalizedTables(tables);
  } catch (error) {
    if (error instanceof LegacyRecoveryError) throw error;
    fail("ROOM_SNAPSHOT_UNREADABLE");
  } finally {
    if (database) database.close();
  }
}

function fileSummary(file) {
  if (!file) return null;
  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > MAX_SNAPSHOT_BYTES) fail("UNSAFE_SNAPSHOT");
  const contents = fs.readFileSync(file);
  return {bytes: contents.length, sha256: sha256(contents)};
}

function parseMapping(line) {
  const fields = String(line).trim().split(/\s+/).filter(Boolean);
  let local;
  let remote;
  if (fields.length === 2) [local, remote] = fields;
  else if (fields.length === 3) [, local, remote] = fields;
  else fail("REVERSE_MAPPING_INVALID");
  if (!/^tcp:[1-9][0-9]{0,4}$/.test(local) || !/^tcp:[1-9][0-9]{0,4}$/.test(remote)) fail("REVERSE_MAPPING_INVALID");
  return local + ":" + remote;
}

function requireMappings(lines) {
  const actual = new Set(lines.filter(Boolean).map(parseMapping));
  if (actual.size !== EXPECTED_MAPPINGS.size || [...actual].some((entry) => !EXPECTED_MAPPINGS.has(entry))) {
    fail("UNEXPECTED_REVERSE_MAPPINGS");
  }
}

function adbResult(adb, serial, args, text = false) {
  const result = spawnSync(adb, ["-s", serial, ...args], {
    encoding: text ? "utf8" : null,
    timeout: 60_000,
    maxBuffer: MAX_SNAPSHOT_BYTES + 1,
  });
  const byteLength = Buffer.isBuffer(result.stdout) ? result.stdout.length : Buffer.byteLength(String(result.stdout || ""));
  if (result.error || result.status !== 0 || byteLength > MAX_SNAPSHOT_BYTES) fail("DEVICE_COMMAND_FAILED");
  return text ? String(result.stdout || "").replace(/\r/g, "") : Buffer.from(result.stdout || "");
}

function createDevice(adb, serial) {
  return {
    async assertReady() {
      if (adbResult(adb, serial, ["get-state"], true).trim() !== "device") fail("DEVICE_NOT_READY");
      const packages = adbResult(adb, serial, ["shell", "pm", "path", APP_ID], true);
      if (!packages.split("\n").some((line) => line.startsWith("package:"))) fail("APP_NOT_INSTALLED");
      requireMappings(adbResult(adb, serial, ["reverse", "--list"], true).split("\n"));
    },
    async stopApp() {
      adbResult(adb, serial, ["shell", "am", "force-stop", APP_ID], true);
    },
    async readFile(remote) {
      return adbResult(adb, serial, ["exec-out", "run-as", APP_ID, "cat", remote]);
    },
    async exists(remote) {
      const result = spawnSync(adb, ["-s", serial, "shell", "run-as", APP_ID, "ls", remote], {
        encoding: "utf8", timeout: 60_000, maxBuffer: 16 * 1024,
      });
      if (result.error) fail("DEVICE_COMMAND_FAILED");
      if (result.status === 0) return true;
      const output = String(result.stdout || "") + String(result.stderr || "");
      if (result.status === 1 && /No such file or directory/.test(output)) return false;
      fail("DEVICE_COMMAND_FAILED");
    },
    async authNames() {
      return adbResult(adb, serial, ["shell", "run-as", APP_ID, "ls", "shared_prefs"], true).split("\n")
        .map((entry) => entry.trim()).filter(Boolean);
    },
  };
}

function writeSensitive(destination, value) {
  if (!(Buffer.isBuffer(value) || value instanceof Uint8Array || typeof value === "string")) fail("SNAPSHOT_INVALID");
  if (Buffer.byteLength(value) > MAX_SNAPSHOT_BYTES) fail("SNAPSHOT_TOO_LARGE");
  const descriptor = fs.openSync(destination, "wx", 0o600);
  try {
    fs.writeFileSync(descriptor, value);
    fs.fsyncSync(descriptor);
  } finally {
    fs.closeSync(descriptor);
  }
  fs.chmodSync(destination, 0o600);
}

async function captureStoppedSnapshot({device, outputDir, label, readDatabase = readRoomDatabase}) {
  const rawDir = fs.mkdtempSync(path.join(outputDir, "raw-room-" + label + "."));
  fs.chmodSync(rawDir, 0o700);
  const dbPath = path.join(rawDir, "schoolquiz.db");
  const discardIncomplete = () => {
    try { fs.rmSync(rawDir, {recursive: true, force: true}); } catch (_) {}
  };
  try {
    writeSensitive(dbPath, await device.readFile("databases/schoolquiz.db"));
    for (const suffix of ["-wal", "-shm"]) {
      const remote = "databases/schoolquiz.db" + suffix;
      if (await device.exists(remote)) writeSensitive(dbPath + suffix, await device.readFile(remote));
    }
    const names = (await device.authNames()).filter((name) => AUTH_STORE.test(name));
    if (names.length !== 1) fail("AUTH_SNAPSHOT_AMBIGUOUS");
    const authPath = path.join(rawDir, "firebase-auth.xml");
    writeSensitive(authPath, await device.readFile("shared_prefs/" + names[0]));
    const tables = await readDatabase(dbPath);
    const files = {
      database: fileSummary(dbPath),
      wal: fs.existsSync(dbPath + "-wal") ? fileSummary(dbPath + "-wal") : null,
      shm: fs.existsSync(dbPath + "-shm") ? fileSummary(dbPath + "-shm") : null,
      auth: fileSummary(authPath),
    };
    return {
      dbPath,
      tables,
      summary: {
        files,
        logicalSha256: sha256(stableJson(tables)),
        tableRowCounts: Object.fromEntries(Object.keys(tables).sort().map((name) => [name, tables[name].length])),
      },
    };
  } catch (error) {
    discardIncomplete();
    throw error;
  }
}

function rows(tables, table) {
  if (!Array.isArray(tables[table])) fail("ROOM_SCHEMA_MISMATCH");
  return tables[table];
}

function one(items, predicate, code) {
  const found = items.filter(predicate);
  if (found.length !== 1) fail(code);
  return found[0];
}

function without(items, target) {
  let removed = false;
  return items.filter((item) => {
    if (!removed && same(item, target)) {
      removed = true;
      return false;
    }
    return true;
  });
}

function changed(before, after) {
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].filter((key) => !same(before[key], after[key])).sort();
}

function onlyChanges(before, after, fields, code) {
  const result = changed(before, after);
  if (result.some((key) => !fields.has(key))) fail(code);
  return result;
}

function compareTargetTicket(before, after, identity) {
  const match = (row) => row.owner_uid === identity.ownerUid && row.ticket_id === identity.ticketId && row.attempt_id === identity.attemptId;
  const oldRow = one(rows(before, "arena_ticket_records"), match, "TARGET_TICKET_AMBIGUOUS");
  const newRow = one(rows(after, "arena_ticket_records"), match, "TARGET_TICKET_AMBIGUOUS");
  if (!same(without(rows(before, "arena_ticket_records"), oldRow), without(rows(after, "arena_ticket_records"), newRow))) {
    fail("UNRELATED_ROOM_CHANGE");
  }
  const fields = onlyChanges(oldRow, newRow, new Set(["server_status", "terminal_json", "observed_at_ms"]), "TARGET_TICKET_MUTATION");
  if (oldRow.server_status !== "RESERVED" || newRow.server_status !== "SETTLED" || oldRow.terminal_json !== null ||
      typeof newRow.terminal_json !== "string" || newRow.terminal_json.length === 0 ||
      !Number.isSafeInteger(oldRow.observed_at_ms) || !Number.isSafeInteger(newRow.observed_at_ms) ||
      newRow.observed_at_ms < oldRow.observed_at_ms || !fields.includes("server_status") || !fields.includes("terminal_json")) {
    fail("TARGET_TICKET_TRANSITION");
  }
  return fields;
}

function compareTargetCommand(before, after, identity) {
  const match = (row) => row.owner_uid === identity.ownerUid && row.ticket_id === identity.ticketId &&
    row.attempt_id === identity.attemptId && row.command_kind === "SETTLE" && row.command_id === identity.attemptId;
  const oldRow = one(rows(before, "arena_command_journal"), match, "TARGET_COMMAND_AMBIGUOUS");
  const newRow = one(rows(after, "arena_command_journal"), match, "TARGET_COMMAND_AMBIGUOUS");
  if (!same(without(rows(before, "arena_command_journal"), oldRow), without(rows(after, "arena_command_journal"), newRow))) {
    fail("UNRELATED_ROOM_CHANGE");
  }
  const fields = onlyChanges(oldRow, newRow, new Set(["state", "last_failure"]), "TARGET_COMMAND_MUTATION");
  if (oldRow.state !== "PENDING" || newRow.state !== "ACKNOWLEDGED" || newRow.last_failure !== null || !fields.includes("state")) {
    fail("TARGET_COMMAND_TRANSITION");
  }
  return fields;
}

function compareProfile(before, after, identity) {
  const oldRows = before.user_profiles || [];
  const newRows = after.user_profiles || [];
  const match = (row) => row.uid === identity.ownerUid;
  const oldOwner = oldRows.filter(match);
  const newOwner = newRows.filter(match);
  if (oldOwner.length !== newOwner.length || oldOwner.length > 1 ||
      !same(oldRows.filter((row) => !match(row)), newRows.filter((row) => !match(row)))) fail("UNRELATED_ROOM_CHANGE");
  if (oldOwner.length === 0) return [];
  const fields = onlyChanges(oldOwner[0], newOwner[0], new Set(["updatedAtMs", "lifePointsUpdatedAtMs"]), "PROFILE_MUTATION");
  for (const field of fields) {
    if (!Number.isSafeInteger(oldOwner[0][field]) || !Number.isSafeInteger(newOwner[0][field]) ||
        newOwner[0][field] < oldOwner[0][field]) fail("PROFILE_TIMESTAMP_REGRESSION");
  }
  return fields;
}

function compareRoomSnapshots(beforeValue, afterValue, identity) {
  const before = normalizedTables(beforeValue);
  const after = normalizedTables(afterValue);
  const allowedTables = new Set(["arena_ticket_records", "arena_command_journal", "user_profiles"]);
  for (const name of new Set([...Object.keys(before), ...Object.keys(after)])) {
    if (!Object.hasOwn(before, name) || !Object.hasOwn(after, name)) fail("ROOM_TABLE_SET_CHANGED");
    if (!allowedTables.has(name) && !same(before[name], after[name])) fail("UNRELATED_ROOM_CHANGE");
  }
  return {
    targetRows: 2,
    ticketChangedColumns: compareTargetTicket(before, after, identity),
    commandChangedColumns: compareTargetCommand(before, after, identity),
    profileTimestampColumns: compareProfile(before, after, identity),
  };
}

function compactClassifier(value) {
  const keys = [
    "status", "readerVersion", "incidentId", "freshAcceptance", "freshAcceptanceCode",
    "permittedAction", "operationSubmissionAllowed", "documentWrites", "localWrites", "deviceCalls",
    "ticketFingerprint", "attemptFingerprint", "serverEvidenceSha256", "localProofSha256",
    "serverEconomySha256", "serverEconomyDocumentCount", "localState", "terminalSettledAtMs", "eventSettledAtMs",
  ];
  return Object.fromEntries(keys.filter((key) => value && Object.hasOwn(value, key)).map((key) => [key, value[key]]));
}

function assertEligible(value, localState) {
  if (!value || value.status !== "LEGACY_RECOVERY_ELIGIBLE" || value.readerVersion !== LEGACY_READER_VERSION ||
      value.incidentId !== INCIDENT_ID || value.freshAcceptance !== "FAIL" ||
      value.permittedAction !== "READ_SERVER_TICKET_ONLY" || value.operationSubmissionAllowed !== false ||
      value.documentWrites !== 0 || value.localWrites !== 0 || value.deviceCalls !== 0 ||
      value.localState !== localState || !/^[a-f0-9]{64}$/.test(value.serverEvidenceSha256 || "") ||
      !/^[a-f0-9]{64}$/.test(value.serverEconomySha256 || "") ||
      !Number.isSafeInteger(value.serverEconomyDocumentCount) || ![3, 5].includes(value.serverEconomyDocumentCount)) {
    fail("CLASSIFIER_NOT_ELIGIBLE");
  }
  return value;
}

async function classifier(main, config, dbPath, env) {
  try {
    return await main(["--context", config.contextPath, "--incident", config.incidentPath, "--db", dbPath], env);
  } catch (error) {
    if (error instanceof LegacyRecoveryError) throw error;
    fail(error && error.code ? "CLASSIFIER_" + error.code : "CLASSIFIER_BLOCKED");
  }
}

function state(outputDir, status, extra = {}) {
  writeJson(path.join(outputDir, "recovery-state.json"), {
    artifactVersion: 1,
    wrapperVersion: VERSION,
    status,
    ...extra,
  });
}

function result(status, config, extra = {}) {
  return {
    artifactVersion: 1,
    wrapperVersion: VERSION,
    status,
    freshAcceptance: "FAIL",
    serialSha256: sha256(config.serial),
    contextSha256: config.contextSha256,
    incidentSha256: config.incidentSha256,
    flowSha256: config.flowSha256,
    ...extra,
  };
}

function assertAuthUnchanged(before, after) {
  if (!before?.files?.auth || !after?.files?.auth || before.files.auth.sha256 !== after.files.auth.sha256 ||
      before.files.auth.bytes !== after.files.auth.bytes) {
    fail("AUTH_SNAPSHOT_CHANGED");
  }
}

async function runFlow({
  maestro, serial, selectorValues, runId, outputDir, env,
  spawnProcess = spawn, flowPath = FLOW, timeoutMs = FLOW_TIMEOUT_MS,
}) {
  regularSourceFile(flowPath, "recovery flow");
  const output = path.join(outputDir, "maestro-recovery.log");
  const testOutput = path.join(outputDir, "maestro-recovery");
  fs.mkdirSync(testOutput, {mode: 0o700});
  fs.chmodSync(testOutput, 0o700);
  const args = ["--device", serial, "--no-ansi", "test", "-e", "E2B_RUN_ID=" + runId, "-e", "E2B_APP_ID=" + APP_ID];
  for (const [key, value] of Object.entries(selectorValues)) args.push("-e", key + "=" + value);
  args.push("--test-output-dir", testOutput, flowPath);
  await new Promise((resolve, reject) => {
    const child = spawnProcess(maestro, args, {detached: true, env, stdio: ["ignore", "pipe", "pipe"]});
    const chunks = [];
    let bytes = 0;
    let timedOut = false;
    const collect = (chunk) => {
      const body = Buffer.from(chunk);
      if (bytes >= 2_000_000) return;
      const kept = body.subarray(0, 2_000_000 - bytes);
      bytes += kept.length;
      chunks.push(kept);
    };
    const timer = setTimeout(() => {
      timedOut = true;
      signalGroup(child, "SIGTERM");
      const killTimer = setTimeout(() => signalGroup(child, "SIGKILL"), 5_000);
      killTimer.unref();
    }, timeoutMs);
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
    child.once("error", () => {
      clearTimeout(timer);
      reject(new LegacyRecoveryError("MAESTRO_UNAVAILABLE"));
    });
    child.once("close", (code, signal) => {
      clearTimeout(timer);
      try { fs.writeFileSync(output, Buffer.concat(chunks), {mode: 0o600}); } catch (_) {}
      if (timedOut) return reject(new LegacyRecoveryError("FLOW_TIMEOUT"));
      if (signal) return reject(new LegacyRecoveryError("FLOW_INTERRUPTED"));
      if (code !== 0) return reject(new LegacyRecoveryError("FLOW_FAILED"));
      resolve();
    });
  });
}

function signalGroup(child, signal) {
  try { process.kill(-child.pid, signal); } catch (_) { try { child.kill(signal); } catch (_) {} }
}

async function withDeviceLock(config, operation) {
  const root = path.resolve(config.lockRoot);
  fs.mkdirSync(root, {recursive: true, mode: 0o700});
  fs.chmodSync(root, 0o700);
  const lockRoot = privateDirectory(root, "lock root");
  const lockPath = path.join(lockRoot, sha256(config.serial) + ".lock");
  const descriptor = fs.openSync(lockPath, fs.constants.O_CREAT | fs.constants.O_APPEND | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
  fs.closeSync(descriptor);
  fs.chmodSync(lockPath, 0o600);
  const child = spawn(config.flock, [
    "-n", lockPath, "sh", "-c",
    "parent_pid=$1; printf x; while kill -0 \"$parent_pid\" 2>/dev/null; do sleep 1; done",
    "arena-legacy-recovery-lock", String(process.pid),
  ], {detached: true, stdio: ["ignore", "pipe", "ignore"]});
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      signalGroup(child, "SIGTERM");
      reject(new LegacyRecoveryError("DEVICE_LOCK_UNAVAILABLE"));
    }, 5_000);
    child.stdout.once("data", (data) => {
      if (Buffer.from(data).includes(0x78)) {
        clearTimeout(timer);
        resolve();
      }
    });
    child.once("error", () => {
      clearTimeout(timer);
      reject(new LegacyRecoveryError("LOCK_HELPER_UNAVAILABLE"));
    });
    child.once("exit", (code) => {
      clearTimeout(timer);
      reject(new LegacyRecoveryError(code === 1 ? "DEVICE_LOCK_HELD" : "DEVICE_LOCK_UNAVAILABLE"));
    });
  });
  try {
    return await operation();
  } finally {
    signalGroup(child, "SIGTERM");
    if (child.exitCode === null && child.signalCode === null) {
      await new Promise((resolve) => child.once("close", resolve));
    }
  }
}

function productionDependencies(config, env) {
  const module = require(CLASSIFIER);
  if (!module || typeof module.main !== "function") fail("CLASSIFIER_UNAVAILABLE");
  const device = createDevice(config.adb, config.serial);
  return {
    device,
    classifierMain: module.main,
    captureSnapshot: (label) => captureStoppedSnapshot({device, outputDir: config.outputDir, label}),
    runFlow: () => runFlow({
      maestro: config.maestro,
      serial: config.serial,
      selectorValues: config.selectorValues,
      runId: config.context.runId,
      outputDir: config.outputDir,
      env,
    }),
    withLock: (operation) => withDeviceLock(config, operation),
  };
}

async function captureAndClassify(label, config, deps, env) {
  const capture = await deps.captureSnapshot(label);
  // Raw DB/WAL/SHM/Auth files stay in this private run directory for later audit.  The JSON
  // evidence records only their fingerprints and logical Room rows, never their contents.
  writeJson(path.join(config.outputDir, label + "-snapshot.json"), capture.summary);
  const classified = await classifier(deps.classifierMain, config, capture.dbPath, env);
  writeJson(path.join(config.outputDir, label + "-classifier.json"), compactClassifier(classified));
  return {capture, classified, tables: capture.tables, summary: capture.summary};
}

async function executeLockedRecovery(config, deps, env, writeResult) {
  let pre = null;
  let post = null;
  let phase = "PRECHECK";
  let flowStarted = false;
  try {
    await deps.device.assertReady();
    await deps.device.stopApp();
    pre = await captureAndClassify("pre", config, deps, env);
    if (pre.classified?.status === "LEGACY_RECOVERY_ELIGIBLE" && pre.classified.localState === "RECONCILED") {
      fail("ALREADY_RECONCILED");
    }
    assertEligible(pre.classified, "STALE");
    state(config.outputDir, "FLOW_STARTED", {
      serverEvidenceSha256: pre.classified.serverEvidenceSha256,
      serverEconomySha256: pre.classified.serverEconomySha256,
      serverEconomyDocumentCount: pre.classified.serverEconomyDocumentCount,
      preLocalProofSha256: pre.classified.localProofSha256,
    });
    phase = "FLOW";
    flowStarted = true;
    await deps.runFlow();
    await deps.device.stopApp();
    phase = "POSTCHECK";
    post = await captureAndClassify("post", config, deps, env);
    assertEligible(post.classified, "RECONCILED");
    if (post.classified.serverEvidenceSha256 !== pre.classified.serverEvidenceSha256) fail("SERVER_EVIDENCE_CHANGED");
    if (post.classified.serverEconomySha256 !== pre.classified.serverEconomySha256 ||
        post.classified.serverEconomyDocumentCount !== pre.classified.serverEconomyDocumentCount) {
      fail("SERVER_ECONOMY_CHANGED");
    }
    assertAuthUnchanged(pre.summary, post.summary);
    const diff = compareRoomSnapshots(pre.tables, post.tables, config.context);
    writeJson(path.join(config.outputDir, "room-diff.json"), diff);
    state(config.outputDir, "COMPLETE", {
      serverEvidenceSha256: pre.classified.serverEvidenceSha256,
      serverEconomySha256: pre.classified.serverEconomySha256,
      serverEconomyDocumentCount: pre.classified.serverEconomyDocumentCount,
      postLocalProofSha256: post.classified.localProofSha256,
    });
    return writeResult(result("RECOVERY_RECONCILED", config, {
      serverEvidenceSha256: pre.classified.serverEvidenceSha256,
      serverEconomySha256: pre.classified.serverEconomySha256,
      serverEconomyDocumentCount: pre.classified.serverEconomyDocumentCount,
      preLocalProofSha256: pre.classified.localProofSha256,
      postLocalProofSha256: post.classified.localProofSha256,
      allowedRoomChanges: diff,
    }));
  } catch (error) {
    // A failed UI flow may leave a useful final stopped snapshot.  A failed postcheck is
    // not retried automatically: it already had its one diagnostic observation attempt.
    try { await deps.device.stopApp(); } catch (_) {}
    if (flowStarted && !post && phase === "FLOW") {
      try {
        post = await captureAndClassify("interrupted-post", config, deps, env);
      } catch (_) {}
    }
    const code = error instanceof LegacyRecoveryError ? error.code : "UNEXPECTED";
    const status = flowStarted || code === "ALREADY_RECONCILED" ? "REQUIRES_REVIEW" : "BLOCKED";
    state(config.outputDir, status, {phase, errorCode: code});
    return writeResult(result(status, config, {
      errorCode: code,
      phase,
      automaticRetry: false,
      requiredAction: "MANUAL_REVIEW_REQUIRED",
      preServerEvidenceSha256: pre?.classified?.serverEvidenceSha256,
      preServerEconomySha256: pre?.classified?.serverEconomySha256,
      preServerEconomyDocumentCount: pre?.classified?.serverEconomyDocumentCount,
      diagnosticPostServerEvidenceSha256: post?.classified?.serverEvidenceSha256,
      diagnosticPostServerEconomySha256: post?.classified?.serverEconomySha256,
      diagnosticPostServerEconomyDocumentCount: post?.classified?.serverEconomyDocumentCount,
    }));
  }
}

async function executeRecovery(config, injected) {
  const env = evidenceEnvironment(config.env);
  const deps = injected || productionDependencies(config, env);
  const writeResult = (value) => {
    writeJson(path.join(config.outputDir, "result.json"), value);
    return value;
  };
  state(config.outputDir, "NEW", {
    serialSha256: sha256(config.serial),
    contextSha256: config.contextSha256,
    incidentSha256: config.incidentSha256,
    flowSha256: config.flowSha256,
  });
  try {
    // All app and snapshot actions, including failure diagnostics, remain inside this callback.
    return await deps.withLock(() => executeLockedRecovery(config, deps, env, writeResult));
  } catch (error) {
    // A shared-lock acquisition failure must never touch the app.  The persisted result is only
    // a local diagnostic and deliberately does not retry or inspect device state.
    const code = error instanceof LegacyRecoveryError ? error.code : "UNEXPECTED";
    state(config.outputDir, "BLOCKED", {phase: "LOCK", errorCode: code});
    return writeResult(result("BLOCKED", config, {
      errorCode: code,
      phase: "LOCK",
      automaticRetry: false,
      requiredAction: "MANUAL_REVIEW_REQUIRED",
    }));
  }
}

function inspectInterrupted(directory) {
  const outputDir = privateDirectory(directory, "inspection directory");
  const current = readJson(path.join(outputDir, "recovery-state.json"), "recovery state");
  const resultPath = path.join(outputDir, "result.json");
  if (current.status === "COMPLETE" && fs.existsSync(resultPath)) return readJson(resultPath, "recovery result");
  return {
    artifactVersion: 1,
    wrapperVersion: VERSION,
    status: "REQUIRES_REVIEW",
    freshAcceptance: "FAIL",
    automaticRetry: false,
    requiredAction: "MANUAL_REVIEW_REQUIRED",
  };
}

function parseArguments(argv) {
  if (argv.length === 1 && ["--help", "-h"].includes(argv[0])) return {help: true};
  if (argv.length === 2 && argv[0] === "--inspect") return {inspect: argv[1]};
  const allowed = new Set(["--serial", "--output-root", "--context", "--incident", "--selectors", "--adb", "--maestro", "--flock"]);
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!allowed.has(flag) || value === undefined || value.startsWith("--") || Object.hasOwn(values, flag)) fail("USAGE");
    values[flag] = value;
  }
  for (const flag of ["--serial", "--output-root", "--context", "--incident", "--selectors"]) {
    if (!Object.hasOwn(values, flag)) fail("USAGE");
  }
  if (values["--serial"] !== TARGET_SERIAL) fail("UNEXPECTED_DEVICE");
  return {
    serial: values["--serial"],
    outputRoot: values["--output-root"],
    contextPath: values["--context"],
    incidentPath: values["--incident"],
    selectorsPath: values["--selectors"],
    adb: values["--adb"] || "adb",
    maestro: values["--maestro"] || "maestro",
    flock: values["--flock"] || "flock",
    lockRoot: LOCK_ROOT,
  };
}

function inputHash(file, name) {
  regularPrivateFile(file, name);
  return sha256(fs.readFileSync(file));
}

function sourceHash(file, name) {
  regularSourceFile(file, name);
  return sha256(fs.readFileSync(file));
}

function usage() {
  return "node .maestro/mvp/arena-e2b/run-arena-legacy-recovery.js " +
    "--serial 172.17.0.5:5555 --output-root <private-parent> " +
    "--context <private-context.json> --incident <private-reviewed-e499.json> " +
    "--selectors <private-selectors.env> [--adb <path>] [--maestro <path>] [--flock <path>]";
}

async function main(argv = process.argv.slice(2), env = process.env) {
  const parsed = parseArguments(argv);
  if (parsed.help) {
    process.stdout.write(usage() + "\n");
    return {status: "HELP"};
  }
  if (parsed.inspect) return inspectInterrupted(parsed.inspect);
  regularPrivateFile(parsed.contextPath, "context");
  regularPrivateFile(parsed.incidentPath, "incident");
  regularSourceFile(FLOW, "recovery flow");
  const context = contextIdentity(parsed.contextPath);
  const selectors = parseSelectors(parsed.selectorsPath);
  const outputDir = createOutputDirectory(parsed.outputRoot);
  return executeRecovery({
    ...parsed,
    env,
    context,
    outputDir,
    selectorValues: flowSelectors(selectors, context),
    contextSha256: inputHash(parsed.contextPath, "context"),
    incidentSha256: inputHash(parsed.incidentPath, "incident"),
    flowSha256: sourceHash(FLOW, "recovery flow"),
  });
}

if (require.main === module) {
  main().then((value) => {
    process.stdout.write(JSON.stringify(value) + "\n");
    if (!["RECOVERY_RECONCILED", "HELP"].includes(value.status)) process.exitCode = 2;
  }).catch((error) => {
    const code = error instanceof LegacyRecoveryError ? error.code : "UNEXPECTED";
    process.stderr.write(JSON.stringify({status: "BLOCKED", wrapperVersion: VERSION, code}) + "\n");
    process.exitCode = 2;
  });
}

module.exports = {
  VERSION,
  APP_ID,
  TARGET_SERIAL,
  LegacyRecoveryError,
  parseArguments,
  evidenceEnvironment,
  sourceHash,
  flowSelectors,
  compareRoomSnapshots,
  assertEligible,
  captureStoppedSnapshot,
  runFlow,
  executeRecovery,
  inspectInterrupted,
  main,
};
