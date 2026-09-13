"use strict";

const assert = require("node:assert/strict");
const {spawn, spawnSync} = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const guardPath = path.join(__dirname, "offline-transport-guard.js");
const APP_ID = "com.example.offlineguard";
const SERIAL = "serial-sentinel-must-not-leak";
const NORMAL_PORTS = [9099, 8080, 5001];

const fakeAdbSource = String.raw`#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
const args = process.argv.slice(2);
if (args[0] !== "-s" || !args[1]) process.exit(64);
const command = args.slice(2);
state.log.push({type: "adb", command});
function save() {
  const temporary = statePath + "." + process.pid + ".tmp";
  fs.writeFileSync(temporary, JSON.stringify(state));
  fs.renameSync(temporary, statePath);
}
function ok(value = "") { save(); process.stdout.write(value); process.exit(0); }
function fail() { save(); process.exit(1); }
if (state.failOnceCommand === command.join(" ")) {
  state.failOnceCommand = null;
  fail();
}
if (command[0] === "get-state") ok(state.connected ? "device\n" : "offline\n");
if (command[0] === "reverse" && command[1] === "--list") {
  const prefix = state.prefixedRows ? "transport-id " : "";
  ok(state.mappings.map((port) => prefix + "tcp:" + port + " tcp:" + port).join("\n") + (state.mappings.length ? "\n" : ""));
}
if (command[0] === "reverse" && command[1] === "--remove") {
  const port = Number(String(command[2] || "").replace("tcp:", ""));
  if (!state.mappings.includes(port)) fail();
  state.mappings = state.mappings.filter((value) => value !== port);
  ok();
}
if (command[0] === "reverse" && /^tcp:[0-9]+$/.test(command[1] || "") && command[1] === command[2]) {
  const port = Number(command[1].slice(4));
  if (state.mappings.includes(port)) fail();
  state.mappings.push(port);
  ok();
}
if (command[0] === "shell" && command[1] === "settings" && command[2] === "get" && command[3] === "global") {
  if (command[4] === "wifi_on") ok(String(state.wifi) + "\n");
  if (command[4] === "mobile_data") ok(String(state.data) + "\n");
  fail();
}
if (command[0] === "shell" && command[1] === "am" && command[2] === "force-stop") ok();
if (command[0] === "shell" && command[1] === "svc" && command[2] === "wifi") {
  state.wifi = command[3] === "enable" ? 1 : command[3] === "disable" ? 0 : state.wifi;
  ok();
}
if (command[0] === "shell" && command[1] === "svc" && command[2] === "data") {
  state.data = command[3] === "enable" ? 1 : command[3] === "disable" ? 0 : state.data;
  ok();
}
fail();
`;

const fakeFlockSource = String.raw`#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const args = process.argv.slice(2);
if (args[0] !== "-n" || !args[1]) process.exit(64);
const heldDirectory = args[1] + ".fake-held";
try {
  fs.mkdirSync(heldDirectory, {mode: 0o700});
} catch (error) {
  if (error && error.code === "EEXIST") process.exit(1);
  process.exit(2);
}
let closed = false;
function close() {
  if (closed) return;
  closed = true;
  try { fs.rmdirSync(heldDirectory); } catch (_) { /* best effort */ }
  process.exit(0);
}
process.on("SIGTERM", close);
process.on("SIGINT", close);
process.stdout.write("x");
setInterval(() => {}, 1_000);
`;

const childSource = String.raw`"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
state.log.push({type: "child", wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
process.exit(Number(process.env.FAKE_CHILD_EXIT || "0"));
`;

const noisyChildSource = String.raw`"use strict";
process.stdout.write("child-output-sentinel-must-not-leak\n");
process.stderr.write("child-output-sentinel-must-not-leak\n");
process.exit(0);
`;

const mappingMutationChildSource = String.raw`"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
state.mappings.push(9199);
state.log.push({type: "mapping-mutation-child"});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
process.exit(0);
`;

const descendantSource = String.raw`"use strict";
process.on("SIGTERM", () => {});
setInterval(() => {}, 1_000);
`;

const termIgnoringChildSource = String.raw`"use strict";
const {spawn} = require("node:child_process");
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
const descendant = spawn(process.execPath, [process.env.FAKE_DESCENDANT_PATH], {stdio: "ignore"});
state.descendantPid = descendant.pid;
state.log.push({type: "term-ignoring-child", wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
process.on("SIGTERM", () => {});
setInterval(() => {}, 1_000);
`;

const waitingChildSource = String.raw`"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
state.log.push({type: "waiting-child", wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
setInterval(() => {}, 1_000);
`;

function makeFixture({mappings = NORMAL_PORTS, prefixedRows = true, failOnceCommand = null} = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "arena-offline-transport-guard-"));
  const privateDirectory = path.join(root, "private");
  const lockRoot = path.join(root, "locks");
  fs.mkdirSync(privateDirectory, {mode: 0o700});
  const statePath = path.join(root, "fake-adb-state.json");
  const fakeAdb = path.join(root, "fake-adb");
  const fakeFlock = path.join(root, "fake-flock");
  const child = path.join(root, "child.js");
  const noisyChild = path.join(root, "noisy-child.js");
  const mappingMutationChild = path.join(root, "mapping-mutation-child.js");
  const termIgnoringChild = path.join(root, "term-ignoring-child.js");
  const descendant = path.join(root, "descendant.js");
  const waitingChild = path.join(root, "waiting-child.js");
  fs.writeFileSync(statePath, JSON.stringify({connected: true, wifi: 1, data: 1, mappings, prefixedRows, failOnceCommand, log: []}), "utf8");
  fs.writeFileSync(fakeAdb, fakeAdbSource, {mode: 0o700});
  fs.writeFileSync(fakeFlock, fakeFlockSource, {mode: 0o700});
  fs.writeFileSync(child, childSource, {mode: 0o600});
  fs.writeFileSync(noisyChild, noisyChildSource, {mode: 0o600});
  fs.writeFileSync(mappingMutationChild, mappingMutationChildSource, {mode: 0o600});
  fs.writeFileSync(termIgnoringChild, termIgnoringChildSource, {mode: 0o600});
  fs.writeFileSync(descendant, descendantSource, {mode: 0o600});
  fs.writeFileSync(waitingChild, waitingChildSource, {mode: 0o600});
  return {
    root,
    privateDirectory,
    lockRoot,
    statePath,
    fakeAdb,
    fakeFlock,
    child,
    noisyChild,
    mappingMutationChild,
    termIgnoringChild,
    descendant,
    waitingChild,
    dispose() { fs.rmSync(root, {recursive: true, force: true}); },
  };
}

function readState(fixture) {
  return JSON.parse(fs.readFileSync(fixture.statePath, "utf8"));
}

function baseGuardArguments(fixture, output, {timeoutMs = "2000", flock = fixture.fakeFlock} = {}) {
  const argumentsList = [
    guardPath,
    "--serial", SERIAL,
    "--adb", fixture.fakeAdb,
    "--app-id", APP_ID,
    "--control-channel", "adb-independent",
    "--output", output,
    "--timeout-ms", timeoutMs,
    "--lock-root", fixture.lockRoot,
  ];
  if (flock) argumentsList.push("--flock", flock);
  argumentsList.push("--");
  return argumentsList;
}

function invokeGuard(fixture, childArguments, environment = {}, options = {}) {
  const output = path.join(fixture.privateDirectory, "result-" + Math.random().toString(16).slice(2) + ".json");
  const outcome = spawnSync(process.execPath, [...baseGuardArguments(fixture, output, options), ...childArguments], {
    encoding: "utf8",
    env: {...process.env, FAKE_ADB_STATE: fixture.statePath, ...environment},
    timeout: 30_000,
  });
  return {output, outcome};
}

function commandIndex(log, predicate) {
  return log.findIndex((entry) => predicate(entry));
}

function adbCommands(log, predicate) {
  return log.filter((entry) => entry.type === "adb" && predicate(entry.command));
}

function assertRestored(fixture) {
  const state = readState(fixture);
  assert.equal(state.wifi, 1);
  assert.equal(state.data, 1);
  assert.deepEqual(state.mappings.slice().sort((left, right) => left - right), NORMAL_PORTS.slice().sort((left, right) => left - right));
}

function assertNoUnsafeReverseCommand(state) {
  for (const entry of state.log.filter((candidate) => candidate.type === "adb")) {
    assert.equal(entry.command.includes("--remove-all"), false);
  }
}

function processIsGoneOrZombie(pid) {
  try {
    process.kill(pid, 0);
  } catch (error) {
    return Boolean(error && error.code === "ESRCH");
  }
  if (process.platform !== "linux") return false;
  try {
    const fields = fs.readFileSync("/proc/" + pid + "/stat", "utf8").trim().split(/\s+/);
    return fields[2] === "Z";
  } catch (error) {
    return Boolean(error && error.code === "ENOENT");
  }
}

function testHappyPathCutsAndRestoresExactTopology() {
  const fixture = makeFixture();
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(outcome.status, 0, outcome.stderr);
    const resultText = fs.readFileSync(output, "utf8");
    const result = JSON.parse(resultText);
    assert.equal(result.status, "PASS");
    assert.equal(result.preflight.passed, true);
    assert.equal(result.cut.confirmed, true);
    assert.equal(result.restore.confirmed, true);
    assert.equal(result.application.stoppedBeforeCut, true);
    assert.equal(result.application.stoppedBeforeRestore, true);
    assert.equal(result.backendWrites, 0);
    assert.equal(result.backendNetworkCalls, 0);
    assert.equal(fs.statSync(output).mode & 0o777, 0o600);
    assert.equal(resultText.includes(SERIAL), false);
    assert.equal(resultText.includes(fixture.child), false);
    const state = readState(fixture);
    const child = state.log.find((entry) => entry.type === "child");
    assert.deepEqual(child, {type: "child", wifi: 0, data: 0, mappings: []});
    const forceStops = adbCommands(state.log, (command) => command.slice(0, 3).join(" ") === "shell am force-stop");
    assert.equal(forceStops.length, 2);
    const removals = adbCommands(state.log, (command) => command[0] === "reverse" && command[1] === "--remove");
    assert.deepEqual(removals.map((entry) => entry.command[2]), ["tcp:9099", "tcp:8080", "tcp:5001"]);
    const firstRemoval = commandIndex(state.log, (entry) => entry.type === "adb" && entry.command[0] === "reverse" && entry.command[1] === "--remove");
    const wifiDisable = commandIndex(state.log, (entry) => entry.type === "adb" && entry.command.join(" ") === "shell svc wifi disable");
    const dataDisable = commandIndex(state.log, (entry) => entry.type === "adb" && entry.command.join(" ") === "shell svc data disable");
    const childIndex = commandIndex(state.log, (entry) => entry.type === "child");
    const firstRestore = commandIndex(state.log, (entry) => entry.type === "adb" && entry.command[0] === "reverse" && entry.command[1] === "tcp:9099");
    assert.ok(firstRemoval >= 0 && firstRemoval < wifiDisable && wifiDisable < dataDisable && dataDisable < childIndex && childIndex < firstRestore);
    assertRestored(fixture);
    assertNoUnsafeReverseCommand(state);
  } finally {
    fixture.dispose();
  }
}

function testChildFailureStillRestores() {
  const fixture = makeFixture();
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child], {FAKE_CHILD_EXIT: "7"});
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "CHILD_FAILED");
    assert.equal(result.restore.confirmed, true);
    assertRestored(fixture);
    assertNoUnsafeReverseCommand(readState(fixture));
  } finally {
    fixture.dispose();
  }
}

function testTwoColumnReverseRowsPass() {
  const fixture = makeFixture({prefixedRows: false});
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(outcome.status, 0, outcome.stderr);
    assert.equal(JSON.parse(fs.readFileSync(output, "utf8")).status, "PASS");
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

function testChildOutputStaysInPrivateBoundedLog() {
  const fixture = makeFixture();
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.noisyChild]);
    assert.equal(outcome.status, 0, outcome.stderr);
    const resultText = fs.readFileSync(output, "utf8");
    const childLog = fs.readFileSync(output + ".child.log", "utf8");
    assert.equal(resultText.includes("child-output-sentinel-must-not-leak"), false);
    assert.equal(String(outcome.stdout).includes("child-output-sentinel-must-not-leak"), false);
    assert.equal(String(outcome.stderr).includes("child-output-sentinel-must-not-leak"), false);
    assert.equal(childLog.includes("child-output-sentinel-must-not-leak"), true);
    assert.equal(fs.statSync(output + ".child.log").mode & 0o777, 0o600);
    assert.equal(JSON.parse(resultText).child.logTruncated, false);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

async function testTimeoutKillsProcessGroupAndRestores() {
  const fixture = makeFixture();
  try {
    const {output, outcome} = invokeGuard(
      fixture,
      [process.execPath, fixture.termIgnoringChild],
      {FAKE_DESCENDANT_PATH: fixture.descendant},
      {timeoutMs: "2000"},
    );
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "CHILD_TIMEOUT");
    assert.equal(result.child.timedOut, true);
    assert.equal(result.restore.confirmed, true);
    assertRestored(fixture);
    const descendantPid = readState(fixture).descendantPid;
    assert.ok(Number.isInteger(descendantPid) && descendantPid > 0);
    await waitFor(() => processIsGoneOrZombie(descendantPid), 15_000, "terminated descendant process group member");
  } finally {
    fixture.dispose();
  }
}

function testPartialCutFailureRestoresOnlyMissingMappings() {
  const fixture = makeFixture({failOnceCommand: "reverse --remove tcp:8080"});
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "ADB_COMMAND_FAILED");
    assert.equal(result.restore.confirmed, true);
    assert.equal(readState(fixture).log.some((entry) => entry.type === "child"), false);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

function testConcurrentMappingFailsClosedWithoutDeletingIt() {
  const fixture = makeFixture();
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.mappingMutationChild]);
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.notEqual(result.status, "PASS");
    const state = readState(fixture);
    assert.equal(state.mappings.includes(9199), true);
    assert.equal(adbCommands(state.log, (command) => command.join(" ") === "reverse --remove tcp:9199").length, 0);
  } finally {
    fixture.dispose();
  }
}

function testUnexpectedReverseMappingFailsBeforeMutation() {
  const fixture = makeFixture({mappings: [...NORMAL_PORTS, 9199]});
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "UNEXPECTED_REVERSE_MAPPINGS");
    const state = readState(fixture);
    assert.equal(state.log.some((entry) => entry.type === "child"), false);
    assert.equal(adbCommands(state.log, (command) => command[0] === "reverse" && command[1] === "--remove").length, 0);
    assert.equal(adbCommands(state.log, (command) => command.join(" ").startsWith("shell svc ")).length, 0);
    assert.equal(adbCommands(state.log, (command) => command.join(" ").startsWith("shell am force-stop")).length, 0);
    assert.deepEqual(state.mappings.slice().sort((left, right) => left - right), [...NORMAL_PORTS, 9199].sort((left, right) => left - right));
    assert.equal(state.wifi, 1);
    assert.equal(state.data, 1);
  } finally {
    fixture.dispose();
  }
}

function testUnsafeControlAcknowledgementFailsBeforeMutation() {
  const fixture = makeFixture();
  try {
    const output = path.join(fixture.privateDirectory, "result.json");
    const argumentsWithUnsafeAcknowledgement = baseGuardArguments(fixture, output).map((value) => value === "adb-independent" ? "wifi-adb" : value);
    const outcome = spawnSync(process.execPath, [...argumentsWithUnsafeAcknowledgement, process.execPath, fixture.child], {
      encoding: "utf8",
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath},
      timeout: 10_000,
    });
    assert.equal(outcome.status, 2);
    assert.equal(fs.existsSync(output), false);
    assert.deepEqual(readState(fixture).log, []);
  } finally {
    fixture.dispose();
  }
}

function testSymlinkedOutputParentFailsBeforeDeviceCalls() {
  const fixture = makeFixture();
  try {
    const linkedDirectory = path.join(fixture.root, "private-link");
    fs.symlinkSync(fixture.privateDirectory, linkedDirectory);
    const output = path.join(linkedDirectory, "result.json");
    const outcome = spawnSync(process.execPath, [...baseGuardArguments(fixture, output), process.execPath, fixture.child], {
      encoding: "utf8",
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath},
      timeout: 10_000,
    });
    assert.equal(outcome.status, 2);
    assert.equal(fs.existsSync(path.join(fixture.privateDirectory, "result.json")), false);
    assert.deepEqual(readState(fixture).log, []);
  } finally {
    fixture.dispose();
  }
}

function testSystemFlockWhenRequested() {
  if (process.env.RUN_SYSTEM_FLOCK_TEST !== "1") return;
  const fixture = makeFixture();
  try {
    const output = path.join(fixture.privateDirectory, "system-flock.json");
    const outcome = spawnSync(process.execPath, [...baseGuardArguments(fixture, output, {flock: null}), process.execPath, fixture.child], {
      encoding: "utf8",
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath},
      timeout: 10_000,
    });
    assert.equal(outcome.status, 0, outcome.stderr);
    assert.equal(JSON.parse(fs.readFileSync(output, "utf8")).status, "PASS");
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

function waitFor(predicate, timeoutMs = 15_000, description = "fixture event") {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const timer = setInterval(() => {
      try {
        if (predicate()) {
          clearInterval(timer);
          resolve();
        } else if (Date.now() >= deadline) {
          clearInterval(timer);
          reject(new Error("timed out waiting for " + description));
        }
      } catch (error) {
        clearInterval(timer);
        reject(error);
      }
    }, 20);
  });
}

function waitForExit(child, timeoutMs = 8_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("guard did not exit after signal")), timeoutMs);
    child.once("error", reject);
    child.once("exit", (code, signal) => {
      clearTimeout(timer);
      resolve({code, signal});
    });
  });
}

async function testTermInterruptStillRestores() {
  const fixture = makeFixture();
  try {
    const output = path.join(fixture.privateDirectory, "term-result.json");
    const guard = spawn(process.execPath, [...baseGuardArguments(fixture, output), process.execPath, fixture.termIgnoringChild], {
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath, FAKE_DESCENDANT_PATH: fixture.descendant},
      stdio: ["ignore", "pipe", "pipe"],
    });
    await waitFor(() => readState(fixture).log.some((entry) => entry.type === "term-ignoring-child"), 15_000, "TERM-ignoring child");
    assert.equal(guard.kill("SIGTERM"), true);
    assert.equal(guard.kill("SIGINT"), true);
    const exit = await waitForExit(guard);
    assert.equal(exit.code, 130);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "INTERRUPTED");
    assert.equal(result.restore.confirmed, true);
    assertRestored(fixture);
    assertNoUnsafeReverseCommand(readState(fixture));
  } finally {
    fixture.dispose();
  }
}

async function testContendedLockReturnsNotRunBeforeDeviceCalls() {
  const fixture = makeFixture();
  try {
    const firstOutput = path.join(fixture.privateDirectory, "first.json");
    const first = spawn(process.execPath, [...baseGuardArguments(fixture, firstOutput), process.execPath, fixture.waitingChild], {
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath},
      stdio: ["ignore", "pipe", "pipe"],
    });
    await waitFor(() => readState(fixture).log.some((entry) => entry.type === "waiting-child"), 15_000, "lock-holder child");
    const callsBeforeSecond = readState(fixture).log.length;
    const second = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(second.outcome.status, 0, second.outcome.stderr);
    const secondResult = JSON.parse(fs.readFileSync(second.output, "utf8"));
    assert.equal(secondResult.status, "NOT_RUN");
    assert.equal(secondResult.errorCode, "DEVICE_LOCK_HELD");
    assert.equal(secondResult.deviceLock.acquired, false);
    assert.equal(readState(fixture).log.length, callsBeforeSecond);
    assert.equal(first.kill("SIGTERM"), true);
    const firstExit = await waitForExit(first);
    assert.equal(firstExit.code, 130);
    assert.equal(JSON.parse(fs.readFileSync(firstOutput, "utf8")).restore.confirmed, true);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

async function main() {
  const filter = process.env.OFFLINE_GUARD_TEST_FILTER;
  const selected = filter ? new Set(filter.split(",").filter(Boolean)) : null;
  const tests = [
    ["happy", testHappyPathCutsAndRestoresExactTopology],
    ["child-failure", testChildFailureStillRestores],
    ["two-column", testTwoColumnReverseRowsPass],
    ["private-log", testChildOutputStaysInPrivateBoundedLog],
    ["timeout", testTimeoutKillsProcessGroupAndRestores],
    ["partial-cut", testPartialCutFailureRestoresOnlyMissingMappings],
    ["concurrent-mapping", testConcurrentMappingFailsClosedWithoutDeletingIt],
    ["unexpected-mapping", testUnexpectedReverseMappingFailsBeforeMutation],
    ["control-ack", testUnsafeControlAcknowledgementFailsBeforeMutation],
    ["symlink", testSymlinkedOutputParentFailsBeforeDeviceCalls],
    ["system-flock", testSystemFlockWhenRequested],
    ["repeated-signal", testTermInterruptStillRestores],
    ["lock-contention", testContendedLockReturnsNotRunBeforeDeviceCalls],
  ];
  let ran = 0;
  for (const [name, action] of tests) {
    if (selected && !selected.has(name)) continue;
    await action();
    ran += 1;
  }
  if (ran === 0) throw new Error("no offline transport guard test matched the requested filter");
  console.log("offline transport guard tests: PASS");
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
