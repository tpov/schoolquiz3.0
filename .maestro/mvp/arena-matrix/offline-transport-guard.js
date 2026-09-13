"use strict";

// Runs one bounded child command while the MVP device is genuinely cut off from its demo
// backend. The application uses 127.0.0.1 through adb reverse, so disabling Wi-Fi/data alone is
// insufficient: this guard temporarily removes only the three known emulator mappings and always
// restores the verified preflight topology before it returns.

const {createHash, randomBytes} = require("node:crypto");
const {spawn, spawnSync} = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const VERSION = "arena-offline-transport-guard-v2";
const REVERSE_PORTS = Object.freeze([9099, 8080, 5001]);
const MAX_OUTPUT_BYTES = 64 * 1024;
const CONTROL_CHANNEL_ACK = "adb-independent";
const DEFAULT_CHILD_TIMEOUT_MS = 10 * 60 * 1000;
const MAX_CHILD_TIMEOUT_MS = 60 * 60 * 1000;
const DEFAULT_LOCK_ROOT = "/tools/arena-e2b-device-locks";
const LOCK_READY_TIMEOUT_MS = 5_000;
const CHILD_TERM_GRACE_MS = 2_000;
const MAX_CHILD_LOG_BYTES = 64 * 1024;
const MAX_SEQUENCE_PLAN_BYTES = 64 * 1024;
// Every ADB mutation is bounded.  `spawnSync` is intentionally used so a successful mutation is
// observed before the next one starts, but an unresponsive bridge must never strand the process
// past this deadline and skip its restoration/quarantine decision.
const DEFAULT_ADB_TIMEOUT_MS = 10_000;
const MIN_ADB_TIMEOUT_MS = 100;
const MAX_ADB_TIMEOUT_MS = 60_000;

class OfflineTransportGuardError extends Error {
  constructor(code, message = code) {
    super(message);
    this.code = code;
  }
}

function fail(code, message) {
  throw new OfflineTransportGuardError(code, message);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function requireOwnScriptHash(expected) {
  if (!expected) return;
  const script = process.argv[1];
  try {
    const stat = fs.lstatSync(script);
    if (!stat.isFile() || stat.isSymbolicLink() || sha256(fs.readFileSync(script)) !== expected) {
      fail("SELF_HASH_MISMATCH", "guard source does not match the sealed input lock");
    }
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    fail("SELF_HASH_MISMATCH", "guard source cannot be verified");
  }
}

function safeText(value, name, {max = 4096} = {}) {
  if (typeof value !== "string" || value.length === 0 || value.length > max || /[\u0000-\u001f\u007f]/.test(value)) {
    fail("INVALID_ARGUMENT", name + " must be safe nonempty text");
  }
  return value;
}

function safeSerial(value) {
  safeText(value, "serial", {max: 256});
  if (!/^[A-Za-z0-9._:-]+$/.test(value)) fail("INVALID_ARGUMENT", "serial has an unsupported shape");
  return value;
}

function safeAppId(value) {
  safeText(value, "app id", {max: 512});
  if (!/^[A-Za-z_][A-Za-z0-9_.]*$/.test(value)) fail("INVALID_ARGUMENT", "app id has an unsupported shape");
  return value;
}

function parseTimeout(value) {
  if (!/^[1-9][0-9]*$/.test(value)) fail("INVALID_ARGUMENT", "timeout must be a positive integer");
  const timeout = Number(value);
  if (!Number.isSafeInteger(timeout) || timeout > MAX_CHILD_TIMEOUT_MS) {
    fail("INVALID_ARGUMENT", "timeout is outside the supported range");
  }
  return timeout;
}

function parsePlanTimeout(value, name) {
  if (!Number.isSafeInteger(value) || value < 1 || value > MAX_CHILD_TIMEOUT_MS) {
    fail("SEQUENCE_PLAN_INVALID", name + " must be a supported positive integer");
  }
  return value;
}

function parseAdbTimeout(value) {
  if (!/^[1-9][0-9]*$/.test(value)) fail("INVALID_ARGUMENT", "adb timeout must be a positive integer");
  const timeout = Number(value);
  if (!Number.isSafeInteger(timeout) || timeout < MIN_ADB_TIMEOUT_MS || timeout > MAX_ADB_TIMEOUT_MS) {
    fail("INVALID_ARGUMENT", "adb timeout is outside the supported range");
  }
  return timeout;
}

function safeAbsolutePath(value, name) {
  safeText(value, name);
  if (!path.isAbsolute(value)) fail("INVALID_ARGUMENT", name + " must be absolute");
  return path.resolve(value);
}

function usage() {
  return [
    "node .maestro/mvp/arena-matrix/offline-transport-guard.js",
    "--serial <adb-serial> --adb <adb-binary> --app-id <application-id>",
    "--control-channel adb-independent --output <new-private-result.json>",
    "[--timeout-ms <1..3600000>] [--adb-timeout-ms <100..60000>] [--self-sha256 <sha256>] [--lock-root <private-lock-directory>] [--flock <binary>] -- <command> [argument ...]",
    "or: --sequence-plan <new-private-sequence.json> (without -- or --timeout-ms)",
  ].join(" ");
}

function parseArguments(argv) {
  if (argv.length === 1 && argv[0] === "--help") return {help: true};
  const separator = argv.indexOf("--");
  if (separator >= 0 && argv.lastIndexOf("--") !== separator) fail("USAGE", "Only one command separator is allowed");
  const options = separator >= 0 ? argv.slice(0, separator) : argv;
  if (options.length % 2 !== 0) fail("USAGE", "Expected named option/value pairs before --");
  const allowed = new Set(["--serial", "--adb", "--app-id", "--control-channel", "--output", "--timeout-ms", "--adb-timeout-ms", "--self-sha256", "--lock-root", "--flock", "--sequence-plan"]);
  const values = {};
  for (let index = 0; index < options.length; index += 2) {
    const flag = options[index];
    const value = options[index + 1];
    if (!allowed.has(flag) || value === undefined || value.startsWith("--") || Object.hasOwn(values, flag)) {
      fail("USAGE", "Invalid or duplicate option");
    }
    values[flag] = value;
  }
  for (const flag of ["--serial", "--adb", "--app-id", "--control-channel", "--output"]) {
    if (!Object.hasOwn(values, flag)) fail("USAGE", "Missing required option");
  }
  const serial = safeSerial(values["--serial"]);
  const adb = safeText(values["--adb"], "adb");
  const appId = safeAppId(values["--app-id"]);
  if (values["--control-channel"] !== CONTROL_CHANNEL_ACK) {
    fail("CONTROL_CHANNEL_UNACKNOWLEDGED", "an adb-independent control channel must be explicitly acknowledged");
  }
  const output = safeAbsolutePath(values["--output"], "output");
  const lockRoot = Object.hasOwn(values, "--lock-root")
    ? safeAbsolutePath(values["--lock-root"], "lock root")
    : DEFAULT_LOCK_ROOT;
  const flock = Object.hasOwn(values, "--flock") ? safeText(values["--flock"], "flock binary") : "flock";
  const timeoutMs = Object.hasOwn(values, "--timeout-ms")
    ? parseTimeout(values["--timeout-ms"])
    : DEFAULT_CHILD_TIMEOUT_MS;
  const adbTimeoutMs = Object.hasOwn(values, "--adb-timeout-ms")
    ? parseAdbTimeout(values["--adb-timeout-ms"])
    : DEFAULT_ADB_TIMEOUT_MS;
  const selfSha256 = Object.hasOwn(values, "--self-sha256") ? values["--self-sha256"] : null;
  if (selfSha256 !== null && !/^[a-f0-9]{64}$/.test(selfSha256)) fail("INVALID_ARGUMENT", "self SHA-256 must be lowercase hexadecimal");
  const hasPlan = Object.hasOwn(values, "--sequence-plan");
  if (hasPlan && (separator >= 0 || Object.hasOwn(values, "--timeout-ms"))) {
    fail("USAGE", "sequence-plan owns both commands and timeouts");
  }
  if (!hasPlan && (separator < 0 || separator === argv.length - 1)) {
    fail("USAGE", "A nonempty child command after -- is required");
  }
  const sequencePlan = hasPlan ? readSequencePlan(values["--sequence-plan"]) : null;
  const command = hasPlan ? sequencePlan.whileCut.argv : argv.slice(separator + 1);
  for (const value of command) safeText(value, "child command argument", {max: 16_384});
  return {
    serial,
    adb,
    appId,
    output,
    childLog: output + ".child.log",
    preCutLog: hasPlan ? output + ".pre-cut.log" : null,
    lockRoot,
    flock,
    timeoutMs: hasPlan ? sequencePlan.whileCut.timeoutMs : timeoutMs,
    adbTimeoutMs,
    selfSha256,
    command,
    preCutCommand: hasPlan ? sequencePlan.preCut.argv : null,
    preCutTimeoutMs: hasPlan ? sequencePlan.preCut.timeoutMs : null,
    sequencePlanSha256: hasPlan ? sequencePlan.sha256 : null,
  };
}

function canonicalExistingDirectory(directory) {
  let canonical;
  try {
    canonical = fs.realpathSync(directory);
  } catch {
    fail("PATH_UNAVAILABLE", "path cannot be inspected");
  }
  let current = canonical;
  while (true) {
    let stat;
    try {
      stat = fs.lstatSync(current);
    } catch {
      fail("PATH_UNAVAILABLE", "path cannot be inspected");
    }
    if (stat.isSymbolicLink()) fail("PATH_SYMLINK", "canonical path may not contain a symbolic link");
    const parent = path.dirname(current);
    if (parent === current) return canonical;
    current = parent;
  }
}

function requirePrivateOutputPath(outputPath) {
  const parent = path.dirname(outputPath);
  let parentStat;
  try {
    parentStat = fs.lstatSync(parent);
  } catch {
    fail("OUTPUT_PARENT_UNAVAILABLE", "output parent cannot be inspected");
  }
  const canonicalParent = canonicalExistingDirectory(parent);
  if (!parentStat.isDirectory() || parentStat.isSymbolicLink() || (parentStat.mode & 0o077) !== 0) {
    fail("OUTPUT_PARENT_UNSAFE", "output parent must be a private non-symlink directory");
  }
  const canonicalParentStat = fs.lstatSync(canonicalParent);
  if (!canonicalParentStat.isDirectory() || (canonicalParentStat.mode & 0o077) !== 0) {
    fail("OUTPUT_PARENT_UNSAFE", "canonical output parent must be private");
  }
  try {
    fs.lstatSync(outputPath);
    fail("OUTPUT_EXISTS", "output path must be new");
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    if (!error || error.code !== "ENOENT") fail("OUTPUT_UNSAFE", "output path cannot be inspected");
  }
  const canonicalOutput = path.join(canonicalParent, path.basename(outputPath));
  try {
    fs.lstatSync(canonicalOutput);
    fail("OUTPUT_EXISTS", "canonical output path must be new");
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    if (!error || error.code !== "ENOENT") fail("OUTPUT_UNSAFE", "canonical output path cannot be inspected");
  }
  return canonicalOutput;
}

function requirePrivateRegularFile(filePath, name) {
  const resolved = safeAbsolutePath(filePath, name);
  const parent = path.dirname(resolved);
  let parentStat;
  let descriptor;
  try {
    parentStat = fs.lstatSync(parent);
  } catch {
    fail("SEQUENCE_PLAN_UNAVAILABLE", name + " parent cannot be inspected");
  }
  if (!parentStat.isDirectory() || parentStat.isSymbolicLink() || (parentStat.mode & 0o077) !== 0) {
    fail("SEQUENCE_PLAN_UNSAFE", name + " parent must be a private non-symlink directory");
  }
  const canonicalParent = canonicalExistingDirectory(parent);
  if ((fs.lstatSync(canonicalParent).mode & 0o077) !== 0) {
    fail("SEQUENCE_PLAN_UNSAFE", name + " canonical parent must be private");
  }
  try {
    descriptor = fs.openSync(resolved, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(descriptor);
    if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0 || stat.size < 1 || stat.size > MAX_SEQUENCE_PLAN_BYTES) {
      fail("SEQUENCE_PLAN_UNSAFE", name + " must be a bounded private regular file");
    }
    const canonical = fs.realpathSync(resolved);
    if (canonical !== path.join(canonicalParent, path.basename(resolved))) {
      fail("SEQUENCE_PLAN_UNSAFE", name + " must not resolve through a link");
    }
    return fs.readFileSync(descriptor, "utf8");
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    fail("SEQUENCE_PLAN_UNAVAILABLE", name + " cannot be read safely");
  } finally {
    if (descriptor !== undefined) {
      try { fs.closeSync(descriptor); } catch (_) { /* best effort */ }
    }
  }
}

function exactKeys(value, keys, name) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail("SEQUENCE_PLAN_INVALID", name + " must be an object");
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail("SEQUENCE_PLAN_INVALID", name + " has an unexpected schema");
  }
}

function parsePlanChild(value, name) {
  exactKeys(value, ["argv", "timeoutMs"], name);
  if (!Array.isArray(value.argv) || value.argv.length === 0 || value.argv.length > 64) {
    fail("SEQUENCE_PLAN_INVALID", name + ".argv must be a bounded nonempty array");
  }
  for (const argument of value.argv) safeText(argument, name + ".argv item", {max: 16_384});
  return {argv: value.argv, timeoutMs: parsePlanTimeout(value.timeoutMs, name + ".timeoutMs")};
}

function readSequencePlan(filePath) {
  const contents = requirePrivateRegularFile(filePath, "sequence plan");
  let value;
  try {
    value = JSON.parse(contents);
  } catch {
    fail("SEQUENCE_PLAN_INVALID", "sequence plan is not JSON");
  }
  exactKeys(value, ["artifactVersion", "preCut", "whileCut"], "sequence plan");
  if (value.artifactVersion !== 1) fail("SEQUENCE_PLAN_INVALID", "sequence plan artifact version is unsupported");
  return {
    preCut: parsePlanChild(value.preCut, "sequence plan preCut"),
    whileCut: parsePlanChild(value.whileCut, "sequence plan whileCut"),
    sha256: sha256(contents),
  };
}

function writePrivateJson(outputPath, value) {
  const temporary = outputPath + ".tmp." + process.pid;
  let descriptor;
  try {
    descriptor = fs.openSync(temporary, "wx", 0o600);
    fs.writeFileSync(descriptor, JSON.stringify(value, null, 2) + "\n", "utf8");
    fs.fsyncSync(descriptor);
    fs.closeSync(descriptor);
    descriptor = undefined;
    // link() fails if another process has claimed the destination since validation; rename() would not.
    fs.linkSync(temporary, outputPath);
    fs.unlinkSync(temporary);
    fs.chmodSync(outputPath, 0o600);
  } catch (error) {
    if (descriptor !== undefined) {
      try { fs.closeSync(descriptor); } catch (_) { /* best effort */ }
    }
    try { fs.unlinkSync(temporary); } catch (_) { /* best effort */ }
    if (error instanceof OfflineTransportGuardError) throw error;
    fail("OUTPUT_WRITE_FAILED", "could not persist the private transport result");
  }
}

function issueSequenceCapability(output, config, phase) {
  if (!config.sequencePlanSha256) return null;
  const file = requirePrivateOutputPath(output + "." + phase + ".capability.json");
  const nonce = randomBytes(32).toString("hex");
  const value = {
    artifactVersion: 1,
    guardVersion: VERSION,
    guardPid: process.pid,
    phase,
    nonce,
    serialSha256: sha256(config.serial),
    appId: config.appId,
    sequencePlanSha256: config.sequencePlanSha256,
  };
  writePrivateJson(file, value);
  return {file, nonce, sha256: sha256(JSON.stringify(value))};
}

function runAdb(adb, serial, args, timeoutMs = DEFAULT_ADB_TIMEOUT_MS) {
  const result = spawnSync(adb, ["-s", serial, ...args], {
    encoding: "utf8",
    maxBuffer: MAX_OUTPUT_BYTES,
    timeout: timeoutMs,
    killSignal: "SIGKILL",
  });
  if (result.error || result.signal || result.status !== 0) {
    fail("ADB_COMMAND_FAILED", "an adb transport command failed");
  }
  return String(result.stdout || "").replace(/\r/g, "").trim();
}

function requireDeviceReady(adb, serial, timeoutMs) {
  if (runAdb(adb, serial, ["get-state"], timeoutMs) !== "device") {
    fail("DEVICE_NOT_READY", "adb target is not ready");
  }
}

function expectedMappings() {
  return new Set(REVERSE_PORTS.map((port) => "tcp:" + port + ":tcp:" + port));
}

function parseReverseMapping(line) {
  const fields = line.trim().split(/\s+/).filter(Boolean);
  let local;
  let remote;
  if (fields.length === 2) {
    [local, remote] = fields;
  } else if (fields.length === 3) {
    [, local, remote] = fields;
  } else {
    fail("REVERSE_MAPPING_INVALID", "adb reverse list has an unexpected mapping shape");
  }
  if (!/^tcp:[1-9][0-9]{0,4}$/.test(local) || !/^tcp:[1-9][0-9]{0,4}$/.test(remote)) {
    fail("REVERSE_MAPPING_INVALID", "adb reverse list has a non-TCP mapping");
  }
  return local + ":" + remote;
}

function readMappings(adb, serial, timeoutMs) {
  const output = runAdb(adb, serial, ["reverse", "--list"], timeoutMs);
  if (output === "") return [];
  const mappings = output.split("\n").filter(Boolean).map(parseReverseMapping);
  if (new Set(mappings).size !== mappings.length) fail("REVERSE_MAPPING_INVALID", "adb reverse list contains duplicates");
  return mappings.sort();
}

function requireExactMappings(mappings) {
  const expected = expectedMappings();
  if (mappings.length !== expected.size || mappings.some((mapping) => !expected.has(mapping))) {
    fail("UNEXPECTED_REVERSE_MAPPINGS", "adb reverse mappings differ from the isolated MVP topology");
  }
}

function readToggle(adb, serial, key, timeoutMs) {
  const value = runAdb(adb, serial, ["shell", "settings", "get", "global", key], timeoutMs);
  if (value !== "0" && value !== "1") fail("TOGGLE_STATE_UNKNOWN", "device transport toggle is not a binary state");
  return value;
}

function preflight(adb, serial, timeoutMs) {
  requireDeviceReady(adb, serial, timeoutMs);
  const wifi = readToggle(adb, serial, "wifi_on", timeoutMs);
  const data = readToggle(adb, serial, "mobile_data", timeoutMs);
  const mappings = readMappings(adb, serial, timeoutMs);
  if (wifi !== "1" || data !== "1") fail("DEVICE_NOT_ONLINE", "device must begin with Wi-Fi and data enabled");
  requireExactMappings(mappings);
  return {wifiEnabled: true, dataEnabled: true, reverseMappingCount: mappings.length};
}

function stopApplication(adb, serial, appId, timeoutMs) {
  runAdb(adb, serial, ["shell", "am", "force-stop", appId], timeoutMs);
  requireDeviceReady(adb, serial, timeoutMs);
}

function cutTransport(adb, serial, timeoutMs, requireLock = null) {
  const checkLock = () => {
    if (typeof requireLock === "function") requireLock();
  };
  checkLock();
  for (const port of REVERSE_PORTS) {
    runAdb(adb, serial, ["reverse", "--remove", "tcp:" + port], timeoutMs);
    checkLock();
    requireDeviceReady(adb, serial, timeoutMs);
    checkLock();
  }
  runAdb(adb, serial, ["shell", "svc", "wifi", "disable"], timeoutMs);
  checkLock();
  requireDeviceReady(adb, serial, timeoutMs);
  checkLock();
  runAdb(adb, serial, ["shell", "svc", "data", "disable"], timeoutMs);
  checkLock();
  requireDeviceReady(adb, serial, timeoutMs);
  checkLock();
  return requireCutTransport(adb, serial, timeoutMs);
}

function requireCutTransport(adb, serial, timeoutMs) {
  requireDeviceReady(adb, serial, timeoutMs);
  const wifi = readToggle(adb, serial, "wifi_on", timeoutMs);
  const data = readToggle(adb, serial, "mobile_data", timeoutMs);
  const mappings = readMappings(adb, serial, timeoutMs);
  if (wifi !== "0" || data !== "0" || mappings.length !== 0) {
    fail("TRANSPORT_CUT_NOT_CONFIRMED", "the device still has a backend transport path");
  }
  return {wifiDisabled: true, dataDisabled: true, reverseMappingCount: 0};
}

function restoreTransport(adb, serial, appId, timeoutMs) {
  const failures = [];
  try {
    // A cold process cannot reconnect while the original backend route is being restored.
    stopApplication(adb, serial, appId, timeoutMs);
  } catch (_) {
    failures.push("force-stop");
  }
  let currentMappings = null;
  try {
    currentMappings = new Set(readMappings(adb, serial, timeoutMs));
  } catch (_) {
    failures.push("reverse-list");
  }
  if (currentMappings) {
    for (const port of REVERSE_PORTS) {
      const mapping = "tcp:" + port + ":tcp:" + port;
      if (currentMappings.has(mapping)) continue;
      try {
        runAdb(adb, serial, ["reverse", "tcp:" + port, "tcp:" + port], timeoutMs);
        requireDeviceReady(adb, serial, timeoutMs);
      } catch (_) {
        failures.push("reverse");
      }
    }
  }
  for (const args of [["shell", "svc", "wifi", "enable"], ["shell", "svc", "data", "enable"]]) {
    try {
      runAdb(adb, serial, args, timeoutMs);
      requireDeviceReady(adb, serial, timeoutMs);
    } catch (_) {
      failures.push("toggle");
    }
  }
  if (failures.length !== 0) fail("TRANSPORT_RESTORE_FAILED", "could not restore every transport control");
  const wifi = readToggle(adb, serial, "wifi_on", timeoutMs);
  const data = readToggle(adb, serial, "mobile_data", timeoutMs);
  const mappings = readMappings(adb, serial, timeoutMs);
  if (wifi !== "1" || data !== "1") fail("TRANSPORT_RESTORE_FAILED", "network toggles did not return to enabled");
  requireExactMappings(mappings);
  return {appStoppedBeforeRestore: true, wifiEnabled: true, dataEnabled: true, reverseMappingCount: mappings.length};
}

function signalProcessGroup(child, signal) {
  if (!child || !Number.isInteger(child.pid) || child.pid <= 0) return false;
  try {
    process.kill(-child.pid, signal);
    return true;
  } catch (error) {
    if (error && error.code === "ESRCH") return false;
    try {
      child.kill(signal);
      return true;
    } catch (_) {
      return false;
    }
  }
}

function openPrivateChildLog(logPath) {
  try {
    return fs.openSync(logPath, "wx", 0o600);
  } catch (_) {
    fail("CHILD_LOG_UNAVAILABLE", "could not create the private child log");
  }
}

function processGroupHasLiveMember(child) {
  // `kill(-pgid, 0)` reports a group containing only zombies as present.  A zombie cannot issue
  // a device command or retain a network route, so treat it as drained while its reaper catches
  // up. Prefer Linux /proc because the minimal controlled image deliberately has no `ps`; use a
  // bounded `ps` snapshot on other POSIX systems and retain the conservative kill probe only if
  // neither source is available.
  try {
    const group = String(child.pid);
    for (const entry of fs.readdirSync("/proc", {withFileTypes: true})) {
      if (!entry.isDirectory() || !/^[1-9][0-9]*$/.test(entry.name)) continue;
      let stat;
      try {
        stat = fs.readFileSync(path.join("/proc", entry.name, "stat"), "utf8");
      } catch (_) {
        continue;
      }
      const close = stat.lastIndexOf(")");
      if (close < 0) continue;
      const fields = stat.slice(close + 2).trim().split(/\s+/);
      // /proc/<pid>/stat after `comm)` starts at state, then ppid, then pgrp.
      if (fields.length >= 3 && fields[2] === group && fields[0] !== "Z" && fields[0] !== "X") return true;
    }
    return false;
  } catch (_) {
    // /proc is absent on Darwin and some hardened hosts.
  }
  try {
    const snapshot = spawnSync("ps", ["-eo", "pid=,pgid=,stat="], {
      encoding: "utf8",
      maxBuffer: MAX_OUTPUT_BYTES,
      timeout: 1_000,
      killSignal: "SIGKILL",
    });
    if (!snapshot.error && !snapshot.signal && snapshot.status === 0) {
      const group = String(child.pid);
      const rows = String(snapshot.stdout || "").split("\n");
      return rows.some((row) => {
        const fields = row.trim().split(/\s+/);
        return fields.length >= 3 && fields[1] === group && !fields[2].startsWith("Z");
      });
    }
  } catch (_) {
    // Fall through to the portable, fail-closed signal probe.
  }
  return null;
}

function processGroupExists(child) {
  if (!child || !Number.isInteger(child.pid) || child.pid <= 0) return false;
  const live = processGroupHasLiveMember(child);
  if (live !== null) return live;
  try {
    process.kill(-child.pid, 0);
    return true;
  } catch (error) {
    if (error && error.code === "ESRCH") return false;
    // EPERM still proves that the process group exists; fail closed during cleanup instead of
    // assuming a child has disappeared merely because it cannot be signalled.
    return true;
  }
}

function waitForProcessGroupExit(child, timeoutMs) {
  return new Promise((resolve) => {
    const deadline = Date.now() + timeoutMs;
    const poll = () => {
      if (!processGroupExists(child)) return resolve(true);
      if (Date.now() >= deadline) return resolve(false);
      setTimeout(poll, 25);
    };
    poll();
  });
}

async function cleanChildProcessGroup(child) {
  if (!child || !Number.isInteger(child.pid) || child.pid <= 0 || !processGroupExists(child)) {
    return {processGroupClean: true, processGroupTerminated: false, processGroupKilled: false};
  }
  const termSent = signalProcessGroup(child, "SIGTERM");
  if (await waitForProcessGroupExit(child, CHILD_TERM_GRACE_MS)) {
    return {processGroupClean: true, processGroupTerminated: termSent, processGroupKilled: false};
  }
  const killSent = signalProcessGroup(child, "SIGKILL");
  const processGroupClean = await waitForProcessGroupExit(child, CHILD_TERM_GRACE_MS);
  return {processGroupClean, processGroupTerminated: termSent, processGroupKilled: killSent};
}

function runChild(command, timeoutMs, childLogPath, onChild) {
  return new Promise((resolve) => {
    let logFd;
    try {
      logFd = openPrivateChildLog(childLogPath);
    } catch (error) {
      resolve({
        started: false,
        exitCode: null,
        signal: null,
        timedOut: false,
        logBytes: 0,
        logTruncated: false,
        logWriteFailed: false,
        logUnavailable: error instanceof OfflineTransportGuardError,
        terminationSignalFailed: false,
        processGroupClean: false,
      });
      return;
    }
    let settled = false;
    let timedOut = false;
    let forceKillTimer = null;
    let timeout = null;
    let child;
    let logBytes = 0;
    let logTruncated = false;
    let logWriteFailed = false;
    let terminationSignalFailed = false;
    let terminationRequested = false;
    const closeLog = () => {
      try { fs.fsyncSync(logFd); } catch (_) { logWriteFailed = true; }
      try { fs.closeSync(logFd); } catch (_) { logWriteFailed = true; }
    };
    const finish = async (value) => {
      if (settled) return;
      settled = true;
      if (timeout) clearTimeout(timeout);
      // Do not clear a pending hard-kill merely because the leader exited: a descendant can keep
      // the detached process group alive with stdio ignored.  The group is always drained before
      // the next sequence leaf or transport restoration.
      const processGroup = await cleanChildProcessGroup(child);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      closeLog();
      onChild(null);
      resolve({
        ...value,
        timedOut,
        logBytes,
        logTruncated,
        logWriteFailed,
        logUnavailable: false,
        terminationSignalFailed,
        ...processGroup,
      });
    };
    const terminate = () => {
      if (terminationRequested || settled) return;
      terminationRequested = true;
      if (!signalProcessGroup(child, "SIGTERM")) terminationSignalFailed = true;
      forceKillTimer = setTimeout(() => {
        if (!settled && !signalProcessGroup(child, "SIGKILL")) terminationSignalFailed = true;
      }, CHILD_TERM_GRACE_MS);
    };
    const capture = (chunk) => {
      const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
      const available = Math.max(0, MAX_CHILD_LOG_BYTES - logBytes);
      const toWrite = Math.min(available, bytes.length);
      if (toWrite > 0) {
        try {
          fs.writeSync(logFd, bytes, 0, toWrite);
          logBytes += toWrite;
        } catch (_) {
          logWriteFailed = true;
          terminate();
        }
      }
      if (toWrite < bytes.length) logTruncated = true;
    };
    try {
      child = spawn(command[0], command.slice(1), {
        detached: true,
        stdio: ["ignore", "pipe", "pipe"],
      });
    } catch (_) {
      void finish({started: false, exitCode: null, signal: null});
      return;
    }
    child.stdout.on("data", capture);
    child.stderr.on("data", capture);
    child.once("error", () => { void finish({started: false, exitCode: null, signal: null}); });
    child.once("close", (exitCode, signal) => { void finish({started: true, exitCode, signal}); });
    onChild({child, terminate});
    timeout = setTimeout(() => {
      timedOut = true;
      terminate();
    }, timeoutMs);
  });
}

function childFailureCode(child, prefix = "") {
  if (child.logUnavailable) return prefix + "CHILD_LOG_UNAVAILABLE";
  if (child.logWriteFailed) return prefix + "CHILD_LOG_WRITE_FAILED";
  if (child.terminationSignalFailed) return prefix + "CHILD_TERMINATION_FAILED";
  if (child.processGroupClean !== true) return prefix + "CHILD_PROCESS_GROUP_REMAINS";
  if (child.timedOut) return prefix + "CHILD_TIMEOUT";
  if (child.signal) return null;
  if (child.started && child.exitCode === 0) return null;
  return prefix + (child.started ? "CHILD_FAILED" : "CHILD_START_FAILED");
}

function prepareLockRoot(lockRoot) {
  try {
    fs.mkdirSync(lockRoot, {recursive: true, mode: 0o700});
    const canonicalRoot = canonicalExistingDirectory(lockRoot);
    const stat = fs.lstatSync(lockRoot);
    if (!stat.isDirectory() || stat.isSymbolicLink()) fail("LOCK_ROOT_UNSAFE", "lock root is not a real directory");
    fs.chmodSync(canonicalRoot, 0o700);
    if ((fs.lstatSync(canonicalRoot).mode & 0o077) !== 0) fail("LOCK_ROOT_UNSAFE", "lock root is not private");
    return canonicalRoot;
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    fail("LOCK_ROOT_UNAVAILABLE", "could not prepare the private device lock root");
  }
}

function createLockFile(lockPath) {
  let descriptor;
  try {
    descriptor = fs.openSync(lockPath, fs.constants.O_CREAT | fs.constants.O_APPEND | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
    const stat = fs.fstatSync(descriptor);
    if (!stat.isFile()) fail("LOCK_FILE_UNSAFE", "device lock is not a regular file");
    fs.fchmodSync(descriptor, 0o600);
  } catch (error) {
    if (error instanceof OfflineTransportGuardError) throw error;
    fail("LOCK_FILE_UNAVAILABLE", "could not create the device lock file");
  } finally {
    if (descriptor !== undefined) {
      try { fs.closeSync(descriptor); } catch (_) { /* best effort */ }
    }
  }
}

function waitForChildClose(child, timeoutMs) {
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(true);
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(false), timeoutMs);
    child.once("close", () => {
      clearTimeout(timer);
      resolve(true);
    });
  });
}

async function acquireDeviceLock(lockRoot, serial, flock) {
  const canonicalRoot = prepareLockRoot(lockRoot);
  const lockPath = path.join(canonicalRoot, sha256(serial) + ".lock");
  createLockFile(lockPath);
  let holder;
  try {
    holder = spawn(flock, [
      "-n",
      lockPath,
      "sh",
      "-c",
      "parent_pid=$1; printf x; while kill -0 \"$parent_pid\" 2>/dev/null; do sleep 1; done",
      "offline-transport-lock",
      String(process.pid),
    ], {
      detached: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
  } catch (_) {
    fail("LOCK_HELPER_UNAVAILABLE", "could not start the device lock helper");
  }
  holder.stderr.resume();
  const lock = {
    holder,
    held: true,
    released: false,
    onLost: null,
  };
  return new Promise((resolve, reject) => {
    let settled = false;
    const rejectWith = (code) => {
      if (settled) return;
      settled = true;
      signalProcessGroup(holder, "SIGTERM");
      reject(new OfflineTransportGuardError(code));
    };
    const readyTimer = setTimeout(() => rejectWith("DEVICE_LOCK_UNAVAILABLE"), LOCK_READY_TIMEOUT_MS);
    holder.stdout.on("data", (data) => {
      if (settled || !Buffer.from(data).includes(0x78)) return;
      settled = true;
      clearTimeout(readyTimer);
      resolve(lock);
    });
    holder.once("error", () => rejectWith("LOCK_HELPER_UNAVAILABLE"));
    holder.once("exit", (code) => {
      if (!settled) {
        clearTimeout(readyTimer);
        rejectWith(code === 1 ? "DEVICE_LOCK_HELD" : "DEVICE_LOCK_UNAVAILABLE");
        return;
      }
      if (!lock.released) {
        lock.held = false;
        if (typeof lock.onLost === "function") lock.onLost();
      }
    });
  });
}

function requireHeldLock(lock) {
  if (!lock || !lock.held || lock.released || lock.holder.exitCode !== null || lock.holder.signalCode !== null) {
    if (lock) lock.held = false;
    fail("DEVICE_LOCK_LOST", "the exclusive device lock is no longer held");
  }
}

async function releaseDeviceLock(lock) {
  if (!lock || lock.released) return;
  lock.released = true;
  if (lock.holder.exitCode !== null || lock.holder.signalCode !== null) return;
  signalProcessGroup(lock.holder, "SIGTERM");
  if (await waitForChildClose(lock.holder, CHILD_TERM_GRACE_MS)) return;
  signalProcessGroup(lock.holder, "SIGKILL");
  if (!await waitForChildClose(lock.holder, CHILD_TERM_GRACE_MS)) {
    fail("DEVICE_LOCK_RELEASE_FAILED", "could not release the device lock helper");
  }
}

async function execute(config) {
  requireOwnScriptHash(config.selfSha256);
  const output = requirePrivateOutputPath(config.output);
  const childLog = requirePrivateOutputPath(output + ".child.log");
  const preCutLog = config.preCutCommand ? requirePrivateOutputPath(config.preCutLog) : null;
  if (process.platform !== "linux" && process.platform !== "darwin") {
    fail("PROCESS_GROUP_UNSUPPORTED", "the offline guard requires POSIX process groups");
  }
  let activeTerminator = null;
  let interruptedSignal = null;
  const onSignal = (signal) => {
    interruptedSignal = interruptedSignal || signal;
    if (typeof activeTerminator === "function") activeTerminator();
  };
  const onSigint = () => onSignal("SIGINT");
  const onSigterm = () => onSignal("SIGTERM");
  const onSighup = () => onSignal("SIGHUP");
  const onSigquit = () => onSignal("SIGQUIT");
  process.on("SIGINT", onSigint);
  process.on("SIGTERM", onSigterm);
  process.on("SIGHUP", onSighup);
  process.on("SIGQUIT", onSigquit);

  const result = {
    artifactVersion: 1,
    guardVersion: VERSION,
    status: "INTERRUPTED",
    serialSha256: sha256(config.serial),
    controlChannel: "ADB_INDEPENDENT_ACKNOWLEDGED",
    backendWrites: 0,
    backendNetworkCalls: 0,
    sequencePlanSha256: config.sequencePlanSha256,
    sequenceCapabilities: {preCutIssued: false, offlineIssued: false},
    deviceLock: {acquired: false, heldThroughChild: false, released: false},
    preflight: {passed: false},
    preCut: {
      requested: Boolean(config.preCutCommand),
      completed: false,
      started: false,
      exitCode: null,
      signal: null,
      timedOut: false,
      logBytes: 0,
      logTruncated: false,
      logWriteFailed: false,
    },
    preCutBaseline: {rechecked: false},
    application: {
      stoppedBeforePreCut: false,
      stoppedAfterPreCutFailure: false,
      stoppedBeforeCut: false,
      stoppedBeforeRestore: false,
    },
    cut: {attempted: false, confirmed: false},
    child: {
      started: false,
      exitCode: null,
      signal: null,
      logBytes: 0,
      logTruncated: false,
      logWriteFailed: false,
    },
    offlineIntegrity: {verifiedAfterChild: false},
    restore: {attempted: false, confirmed: false},
    errorCode: null,
  };
  let transportTouched = false;
  let lock = null;
  let lockLost = false;
  let resultWriteFailed = false;
  try {
    lock = await acquireDeviceLock(config.lockRoot, config.serial, config.flock);
    lock.onLost = () => {
      lockLost = true;
      if (typeof activeTerminator === "function") activeTerminator();
    };
    result.deviceLock.acquired = true;
    requireHeldLock(lock);
    const start = preflight(config.adb, config.serial, config.adbTimeoutMs);
    result.preflight = {passed: true, ...start};
    let mayCut = !interruptedSignal;
    if (config.preCutCommand && mayCut) {
      // The leaf's first operation is a stopped-app snapshot.  Stopping before it launches also
      // prevents a retained sender from delivering an older command during an online pre-cut.
      requireHeldLock(lock);
      stopApplication(config.adb, config.serial, config.appId, config.adbTimeoutMs);
      result.application.stoppedBeforePreCut = true;
      const capability = issueSequenceCapability(output, config, "pre-cut");
      if (!capability) fail("SEQUENCE_CAPABILITY_UNAVAILABLE", "the sequence pre-cut capability was not issued");
      result.sequenceCapabilities.preCutIssued = true;
      const preCutCommand = [...config.preCutCommand, "--guard-capability", capability.file, "--guard-nonce", capability.nonce];
      const preCut = await runChild(preCutCommand, config.preCutTimeoutMs, preCutLog, (active) => {
        activeTerminator = active ? active.terminate : null;
      });
      result.preCut = {requested: true, completed: false, ...preCut};
      activeTerminator = null;
      if (lockLost) {
        result.status = "FAIL";
        result.errorCode = "DEVICE_LOCK_LOST";
        mayCut = false;
      } else if (interruptedSignal) {
        result.status = "INTERRUPTED";
        mayCut = false;
      } else {
        const preCutError = childFailureCode(preCut, "PRE_CUT_");
        if (preCutError) {
          result.status = "FAIL";
          result.errorCode = preCutError;
          mayCut = false;
        } else if (preCut.signal) {
          result.status = "INTERRUPTED";
          mayCut = false;
        } else {
          requireHeldLock(lock);
          const baseline = preflight(config.adb, config.serial, config.adbTimeoutMs);
          result.preCut = {...result.preCut, completed: true};
          result.preCutBaseline = {rechecked: true, ...baseline};
        }
      }
    }
    if (!mayCut) {
      // A failed online leaf can have launched the app.  Quiesce it while the same exclusive
      // lock remains held; do not make any device mutation after the lock is lost.
      if (config.preCutCommand && !lockLost) {
        try {
          requireHeldLock(lock);
          stopApplication(config.adb, config.serial, config.appId, config.adbTimeoutMs);
          result.application.stoppedAfterPreCutFailure = true;
        } catch (error) {
          result.status = "FAIL";
          result.errorCode = error instanceof OfflineTransportGuardError ? error.code : "APPLICATION_STOP_FAILED";
        }
      }
      if (interruptedSignal && result.status !== "FAIL") result.status = "INTERRUPTED";
    } else {
      stopApplication(config.adb, config.serial, config.appId, config.adbTimeoutMs);
      result.application.stoppedBeforeCut = true;
      // Recheck after stopping the process so a concurrent route change cannot become an offline pass.
      preflight(config.adb, config.serial, config.adbTimeoutMs);
      requireHeldLock(lock);
      transportTouched = true;
      result.cut = {attempted: true, ...cutTransport(config.adb, config.serial, config.adbTimeoutMs, () => requireHeldLock(lock)), confirmed: true};
      // `cutTransport` contains bounded synchronous ADB calls.  Probe the lock holder directly
      // immediately after it returns and once more at the last point before spawning the leaf.
      requireHeldLock(lock);
      if (interruptedSignal) {
        result.status = "INTERRUPTED";
      } else {
        requireHeldLock(lock);
        let offlineCommand = config.command;
        if (config.sequencePlanSha256) {
          const capability = issueSequenceCapability(output, config, "offline");
          if (!capability) fail("SEQUENCE_CAPABILITY_UNAVAILABLE", "the sequence offline capability was not issued");
          result.sequenceCapabilities.offlineIssued = true;
          offlineCommand = [...config.command, "--guard-capability", capability.file, "--guard-nonce", capability.nonce];
        }
        const child = await runChild(offlineCommand, config.timeoutMs, childLog, (active) => {
          activeTerminator = active ? active.terminate : null;
        });
        result.child = child;
        activeTerminator = null;
        if (lockLost) {
          result.status = "FAIL";
          result.errorCode = "DEVICE_LOCK_LOST";
        } else {
          requireHeldLock(lock);
          requireCutTransport(config.adb, config.serial, config.adbTimeoutMs);
          result.deviceLock.heldThroughChild = true;
          result.offlineIntegrity.verifiedAfterChild = true;
        }
        if (result.status === "FAIL") {
          // A lost lock is already conclusive; retain it rather than reclassifying the child outcome.
        } else if (interruptedSignal) {
          result.status = "INTERRUPTED";
        } else {
          const childError = childFailureCode(child);
          if (childError) {
            result.status = "FAIL";
            result.errorCode = childError;
          } else if (child.signal) {
            result.status = "INTERRUPTED";
          } else {
            result.status = "PASS";
          }
        }
      }
    }
  } catch (error) {
    const code = error instanceof OfflineTransportGuardError ? error.code : "UNEXPECTED";
    result.status = code === "DEVICE_LOCK_HELD" ? "NOT_RUN" : (interruptedSignal ? "INTERRUPTED" : "FAIL");
    result.errorCode = code;
  } finally {
    if (transportTouched && !lockLost) {
      result.restore.attempted = true;
      try {
        requireHeldLock(lock);
        result.restore = {attempted: true, ...restoreTransport(config.adb, config.serial, config.appId, config.adbTimeoutMs), confirmed: true};
        result.application.stoppedBeforeRestore = true;
      } catch (error) {
        result.status = "FAIL";
        result.errorCode = error instanceof OfflineTransportGuardError ? error.code : "TRANSPORT_RESTORE_FAILED";
      }
    } else if (transportTouched && lockLost) {
      // Never restore a partial/full cut after losing exclusivity: another guarded operation may
      // already own the device.  Leave it quarantined for a human-held recovery lock instead of
      // racing an unverified network restoration.
      result.restore = {attempted: false, confirmed: false, skippedDueToLockLoss: true};
    }
    if (lockLost) {
      result.status = "FAIL";
      result.errorCode = "DEVICE_LOCK_LOST";
    } else if (interruptedSignal && result.status === "PASS") {
      result.status = "INTERRUPTED";
    }
    try {
      await releaseDeviceLock(lock);
      if (lock && !lockLost) result.deviceLock.released = true;
    } catch (error) {
      result.status = "FAIL";
      result.errorCode = error instanceof OfflineTransportGuardError ? error.code : "DEVICE_LOCK_RELEASE_FAILED";
    }
    try {
      writePrivateJson(output, result);
    } catch (_) {
      resultWriteFailed = true;
    } finally {
      process.removeListener("SIGINT", onSigint);
      process.removeListener("SIGTERM", onSigterm);
      process.removeListener("SIGHUP", onSighup);
      process.removeListener("SIGQUIT", onSigquit);
    }
  }
  if (resultWriteFailed) {
    process.stderr.write("offline transport guard: result persistence failed\n");
    return 2;
  }
  if (result.status === "PASS") return 0;
  if (result.status === "NOT_RUN") return 0;
  return result.status === "INTERRUPTED" ? 130 : 2;
}

async function main(argv = process.argv.slice(2)) {
  const config = parseArguments(argv);
  if (config.help) {
    process.stdout.write(usage() + "\n");
    return 0;
  }
  return execute(config);
}

if (require.main === module) {
  main().then((code) => {
    process.exitCode = code;
  }).catch((error) => {
    const code = error instanceof OfflineTransportGuardError ? error.code : "UNEXPECTED";
    process.stderr.write("offline transport guard: " + code + "\n");
    process.exitCode = 2;
  });
}

module.exports = {
  VERSION,
  OfflineTransportGuardError,
  parseArguments,
  parseReverseMapping,
  preflight,
  cutTransport,
  restoreTransport,
  execute,
};
