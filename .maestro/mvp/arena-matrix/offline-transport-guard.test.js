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
const commandText = command.join(" ");
state.log.push({type: "adb", command});
function save() {
  const temporary = statePath + "." + process.pid + ".tmp";
  fs.writeFileSync(temporary, JSON.stringify(state));
  fs.renameSync(temporary, statePath);
}
function wait(milliseconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, milliseconds);
}
function dropFakeLockWhenRequested() {
  if (state.dropLockAfterCommand !== commandText) return;
  state.dropLockAfterCommand = null;
  save();
  const holderPid = Number(fs.readFileSync(statePath + ".fake-flock-pid", "utf8").trim());
  if (!Number.isInteger(holderPid) || holderPid <= 0) process.exit(65);
  process.kill(holderPid, "SIGTERM");
  const releasedPath = statePath + ".fake-flock-released";
  const deadline = Date.now() + 1_000;
  while (!fs.existsSync(releasedPath) && Date.now() < deadline) wait(10);
  if (!fs.existsSync(releasedPath)) process.exit(66);
}
function ok(value = "") {
  dropFakeLockWhenRequested();
  const shouldHang = state.hangOnceCommand === commandText;
  if (shouldHang) state.hangOnceCommand = null;
  save();
  if (shouldHang) for (;;) wait(1_000);
  else { process.stdout.write(value); process.exit(0); }
}
function fail() { save(); process.exit(1); }
function requireDescendantStoppedBeforeRestore() {
  if (!state.assertDescendantStoppedBeforeRestore) return;
  if (command[0] !== "reverse" || !/^tcp:[0-9]+$/.test(command[1] || "") || command[1] !== command[2]) return;
  const pid = state.descendantPid;
  if (!Number.isInteger(pid) || pid <= 0) return;
  try {
    process.kill(pid, 0);
    // Linux retains a killed descendant briefly as a zombie.  It cannot execute or keep the
    // transport alive, so distinguish it from a genuinely surviving process before flagging a
    // restore race.  Darwin has no /proc, where the ordinary signal check stays conservative.
    try {
      const stat = fs.readFileSync("/proc/" + pid + "/stat", "utf8");
      const close = stat.lastIndexOf(")");
      if (close >= 0 && stat.slice(close + 2, close + 3) === "Z") return;
    } catch (_) { /* non-Linux or process reaped between probes */ }
    state.restoreWhileDescendantAlive = true;
    fail();
  } catch (error) {
    if (!error || error.code !== "ESRCH") throw error;
  }
}
if (state.failOnceCommand === commandText) {
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
  requireDescendantStoppedBeforeRestore();
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
const statePath = process.env.FAKE_ADB_STATE;
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
  if (statePath) {
    try { fs.writeFileSync(statePath + ".fake-flock-released", String(process.pid), {mode: 0o600}); } catch (_) { /* best effort */ }
  }
  try { fs.rmdirSync(heldDirectory); } catch (_) { /* best effort */ }
  process.exit(0);
}
process.on("SIGTERM", close);
process.on("SIGINT", close);
if (statePath) fs.writeFileSync(statePath + ".fake-flock-pid", String(process.pid), {mode: 0o600});
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

const preCutChildSource = String.raw`"use strict";
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
state.log.push({type: "pre-cut", wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
process.exit(Number(process.env.FAKE_PRE_CUT_EXIT || "0"));
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
const fs = require("node:fs");
if (process.env.FAKE_DESCENDANT_READY_PATH) fs.writeFileSync(process.env.FAKE_DESCENDANT_READY_PATH, String(process.pid), {mode: 0o600});
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

const exitingParentWithDescendantSource = String.raw`"use strict";
const {spawn} = require("node:child_process");
const fs = require("node:fs");
const statePath = process.env.FAKE_ADB_STATE;
const state = JSON.parse(fs.readFileSync(statePath, "utf8"));
const descendant = spawn(process.execPath, [process.env.FAKE_DESCENDANT_PATH], {stdio: "ignore"});
const readyPath = process.env.FAKE_DESCENDANT_READY_PATH;
const shared = new Int32Array(new SharedArrayBuffer(4));
const deadline = Date.now() + 2_000;
while (readyPath && !fs.existsSync(readyPath) && Date.now() < deadline) Atomics.wait(shared, 0, 0, 10);
if (readyPath && !fs.existsSync(readyPath)) process.exit(69);
state.descendantPid = descendant.pid;
state.descendantReadyPath = readyPath || null;
state.descendantReadySeen = readyPath ? fs.existsSync(readyPath) : null;
state.log.push({type: "exiting-parent-with-descendant", wifi: state.wifi, data: state.data, mappings: state.mappings.slice()});
const temporary = statePath + "." + process.pid + ".tmp";
fs.writeFileSync(temporary, JSON.stringify(state));
fs.renameSync(temporary, statePath);
process.exit(0);
`;

function makeFixture({
  mappings = NORMAL_PORTS,
  wifi = 1,
  data = 1,
  prefixedRows = true,
  failOnceCommand = null,
  hangOnceCommand = null,
  dropLockAfterCommand = null,
  assertDescendantStoppedBeforeRestore = false,
} = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "arena-offline-transport-guard-"));
  const privateDirectory = path.join(root, "private");
  const lockRoot = path.join(root, "locks");
  fs.mkdirSync(privateDirectory, {mode: 0o700});
  const statePath = path.join(root, "fake-adb-state.json");
  const fakeAdb = path.join(root, "fake-adb");
  const fakeFlock = path.join(root, "fake-flock");
  const child = path.join(root, "child.js");
  const preCutChild = path.join(root, "pre-cut-child.js");
  const noisyChild = path.join(root, "noisy-child.js");
  const mappingMutationChild = path.join(root, "mapping-mutation-child.js");
  const termIgnoringChild = path.join(root, "term-ignoring-child.js");
  const descendant = path.join(root, "descendant.js");
  const waitingChild = path.join(root, "waiting-child.js");
  const exitingParentWithDescendant = path.join(root, "exiting-parent-with-descendant.js");
  fs.writeFileSync(statePath, JSON.stringify({
    connected: true,
    wifi,
    data,
    mappings,
    prefixedRows,
    failOnceCommand,
    hangOnceCommand,
    dropLockAfterCommand,
    assertDescendantStoppedBeforeRestore,
    log: [],
  }), "utf8");
  fs.writeFileSync(fakeAdb, fakeAdbSource, {mode: 0o700});
  fs.writeFileSync(fakeFlock, fakeFlockSource, {mode: 0o700});
  fs.writeFileSync(child, childSource, {mode: 0o600});
  fs.writeFileSync(preCutChild, preCutChildSource, {mode: 0o600});
  fs.writeFileSync(noisyChild, noisyChildSource, {mode: 0o600});
  fs.writeFileSync(mappingMutationChild, mappingMutationChildSource, {mode: 0o600});
  fs.writeFileSync(termIgnoringChild, termIgnoringChildSource, {mode: 0o600});
  fs.writeFileSync(descendant, descendantSource, {mode: 0o600});
  fs.writeFileSync(waitingChild, waitingChildSource, {mode: 0o600});
  fs.writeFileSync(exitingParentWithDescendant, exitingParentWithDescendantSource, {mode: 0o600});
  return {
    root,
    privateDirectory,
    lockRoot,
    statePath,
    fakeAdb,
    fakeFlock,
    child,
    preCutChild,
    noisyChild,
    mappingMutationChild,
    termIgnoringChild,
    descendant,
    waitingChild,
    exitingParentWithDescendant,
    initialWifi: wifi,
    initialData: data,
    dispose() { fs.rmSync(root, {recursive: true, force: true}); },
  };
}

function readState(fixture) {
  return JSON.parse(fs.readFileSync(fixture.statePath, "utf8"));
}

function baseGuardArguments(fixture, output, {timeoutMs = "2000", adbTimeoutMs = null, flock = fixture.fakeFlock} = {}) {
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
  if (adbTimeoutMs !== null) argumentsList.push("--adb-timeout-ms", adbTimeoutMs);
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

function writeSequencePlan(fixture, {preCut, whileCut, preCutTimeoutMs = 2_000, whileCutTimeoutMs = 2_000}) {
  const plan = path.join(fixture.privateDirectory, "sequence-" + Math.random().toString(16).slice(2) + ".json");
  fs.writeFileSync(plan, JSON.stringify({
    artifactVersion: 1,
    preCut: {argv: preCut, timeoutMs: preCutTimeoutMs},
    whileCut: {argv: whileCut, timeoutMs: whileCutTimeoutMs},
  }), {mode: 0o600});
  return plan;
}

function invokeSequenceGuard(fixture, plan, environment = {}, options = {}) {
  const output = path.join(fixture.privateDirectory, "sequence-result-" + Math.random().toString(16).slice(2) + ".json");
  const base = baseGuardArguments(fixture, output, options);
  assert.equal(base.at(-1), "--");
  const argumentsList = [];
  for (let index = 0; index < base.length - 1; index += 1) {
    if (base[index] === "--timeout-ms") {
      index += 1;
      continue;
    }
    argumentsList.push(base[index]);
  }
  argumentsList.push("--sequence-plan", plan);
  const outcome = spawnSync(process.execPath, argumentsList, {
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
  assert.equal(state.wifi, fixture.initialWifi);
  assert.equal(state.data, fixture.initialData);
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

function testSequenceKeepsOneLockAcrossOnlinePreCutAndOfflineChild() {
  const fixture = makeFixture();
  try {
    const plan = writeSequencePlan(fixture, {
      preCut: [process.execPath, fixture.preCutChild],
      whileCut: [process.execPath, fixture.child],
    });
    const {output, outcome} = invokeSequenceGuard(fixture, plan);
    assert.equal(outcome.status, 0, outcome.stderr);
    const resultText = fs.readFileSync(output, "utf8");
    const result = JSON.parse(resultText);
    assert.equal(result.status, "PASS");
    assert.equal(result.preCut.requested, true);
    assert.equal(result.preCut.completed, true);
    assert.equal(result.preCutBaseline.rechecked, true);
    assert.match(result.sequencePlanSha256, /^[a-f0-9]{64}$/);
    assert.equal(resultText.includes(fixture.preCutChild), false);
    const state = readState(fixture);
    assert.deepEqual(state.log.find((entry) => entry.type === "pre-cut"), {
      type: "pre-cut", wifi: 1, data: 1, mappings: NORMAL_PORTS,
    });
    assert.deepEqual(state.log.find((entry) => entry.type === "child"), {
      type: "child", wifi: 0, data: 0, mappings: [],
    });
    const preCutIndex = commandIndex(state.log, (entry) => entry.type === "pre-cut");
    const removalIndex = commandIndex(state.log, (entry) => entry.type === "adb" && entry.command[0] === "reverse" && entry.command[1] === "--remove");
    const offlineIndex = commandIndex(state.log, (entry) => entry.type === "child");
    assert.ok(preCutIndex >= 0 && preCutIndex < removalIndex && removalIndex < offlineIndex);
    assertRestored(fixture);
    assertNoUnsafeReverseCommand(state);
  } finally {
    fixture.dispose();
  }
}

function testSequencePreCutFailureDoesNotTouchTransport() {
  const fixture = makeFixture();
  try {
    const plan = writeSequencePlan(fixture, {
      preCut: [process.execPath, fixture.preCutChild],
      whileCut: [process.execPath, fixture.child],
    });
    const {output, outcome} = invokeSequenceGuard(fixture, plan, {FAKE_PRE_CUT_EXIT: "7"});
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "PRE_CUT_CHILD_FAILED");
    assert.equal(result.preCut.completed, false);
    assert.equal(result.cut.attempted, false);
    assert.equal(result.restore.attempted, false);
    const state = readState(fixture);
    assert.equal(state.log.some((entry) => entry.type === "child"), false);
    assert.equal(adbCommands(state.log, (command) => command[0] === "reverse" && command[1] === "--remove").length, 0);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

function testSequencePreCutBaselineDriftFailsClosedBeforeCut() {
  const fixture = makeFixture();
  try {
    const plan = writeSequencePlan(fixture, {
      preCut: [process.execPath, fixture.mappingMutationChild],
      whileCut: [process.execPath, fixture.child],
    });
    const {output, outcome} = invokeSequenceGuard(fixture, plan);
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "UNEXPECTED_REVERSE_MAPPINGS");
    assert.equal(result.cut.attempted, false);
    const state = readState(fixture);
    assert.equal(state.log.some((entry) => entry.type === "child"), false);
    assert.equal(state.mappings.includes(9199), true);
    assert.equal(adbCommands(state.log, (command) => command.join(" ") === "reverse --remove tcp:9199").length, 0);
  } finally {
    fixture.dispose();
  }
}

function testSequencePreCutTimeoutDoesNotTouchTransport() {
  const fixture = makeFixture();
  try {
    const plan = writeSequencePlan(fixture, {
      preCut: [process.execPath, fixture.waitingChild],
      whileCut: [process.execPath, fixture.child],
      preCutTimeoutMs: 2_000,
    });
    const {output, outcome} = invokeSequenceGuard(fixture, plan);
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "PRE_CUT_CHILD_TIMEOUT");
    assert.equal(result.cut.attempted, false);
    assert.equal(adbCommands(readState(fixture).log, (command) => command[0] === "reverse" && command[1] === "--remove").length, 0);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

function testSequenceSymlinkedPlanFailsBeforeDeviceCalls() {
  const fixture = makeFixture();
  try {
    const realPlan = writeSequencePlan(fixture, {
      preCut: [process.execPath, fixture.preCutChild],
      whileCut: [process.execPath, fixture.child],
    });
    const linkedPlan = path.join(fixture.privateDirectory, "sequence-link.json");
    fs.symlinkSync(realPlan, linkedPlan);
    const {output, outcome} = invokeSequenceGuard(fixture, linkedPlan);
    assert.equal(outcome.status, 2);
    assert.equal(fs.existsSync(output), false);
    assert.deepEqual(readState(fixture).log, []);
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

function testRadioOffBaselineStillCutsAndRestoresExactTopology() {
  const fixture = makeFixture({wifi: 0, data: 1});
  try {
    const {output, outcome} = invokeGuard(fixture, [process.execPath, fixture.child]);
    assert.equal(outcome.status, 0, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "PASS");
    assert.equal(result.preflight.wifiEnabled, false);
    assert.equal(result.preflight.dataEnabled, true);
    assert.equal(result.cut.confirmed, true);
    assert.equal(result.restore.confirmed, true);
    assert.equal(result.restore.wifiEnabled, false);
    assert.equal(result.restore.dataEnabled, true);
    const log = readState(fixture).log.filter((entry) => entry.type === "adb").map((entry) => entry.command.join(" "));
    assert.equal(log.includes("shell svc wifi disable"), false);
    assert.equal(log.includes("shell svc wifi enable"), false);
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

function testHungAdbIsBoundedAndRestores() {
  const fixture = makeFixture({hangOnceCommand: "reverse --remove tcp:8080"});
  try {
    const startedAt = Date.now();
    const {output, outcome} = invokeGuard(
      fixture,
      [process.execPath, fixture.child],
      {},
      {adbTimeoutMs: "500", timeoutMs: "5000"},
    );
    const elapsedMs = Date.now() - startedAt;
    assert.equal(outcome.status, 2, outcome.stderr);
    assert.ok(elapsedMs < 10_000, "hung adb was not bounded: " + elapsedMs + "ms");
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "ADB_COMMAND_FAILED");
    assert.equal(result.restore.attempted, true);
    assert.equal(result.restore.confirmed, true);
    assert.equal(readState(fixture).log.some((entry) => entry.type === "child"), false);
    assertRestored(fixture);
  } finally {
    fixture.dispose();
  }
}

async function testNormalRootExitDrainsSurvivingDescendantBeforeRestore() {
  const fixture = makeFixture({assertDescendantStoppedBeforeRestore: true});
  try {
    const readyPath = path.join(fixture.privateDirectory, "normal-root-descendant-ready");
    const {output, outcome} = invokeGuard(
      fixture,
      [process.execPath, fixture.exitingParentWithDescendant],
      {FAKE_DESCENDANT_PATH: fixture.descendant, FAKE_DESCENDANT_READY_PATH: readyPath},
      {timeoutMs: "5000"},
    );
    assert.equal(outcome.status, 0, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "PASS");
    assert.equal(result.child.processGroupClean, true);
    assert.equal(result.child.processGroupTerminated, true);
    assert.equal(fs.existsSync(readyPath), true, "the root did not leave a started descendant");
    const state = readState(fixture);
    assert.notEqual(state.restoreWhileDescendantAlive, true);
    const descendantPid = state.descendantPid;
    assert.ok(Number.isInteger(descendantPid) && descendantPid > 0);
    await waitFor(() => processIsGoneOrZombie(descendantPid), 15_000, "descendant left after a successful root child");
    assertRestored(fixture);
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

async function testNamedSignalInterruptStillRestores(signal) {
  const fixture = makeFixture();
  try {
    const output = path.join(fixture.privateDirectory, signal.toLowerCase() + "-result.json");
    const guard = spawn(process.execPath, [...baseGuardArguments(fixture, output), process.execPath, fixture.waitingChild], {
      env: {...process.env, FAKE_ADB_STATE: fixture.statePath},
      stdio: ["ignore", "pipe", "pipe"],
    });
    await waitFor(() => readState(fixture).log.some((entry) => entry.type === "waiting-child"), 15_000, signal + " target child");
    assert.equal(guard.kill(signal), true);
    const exit = await waitForExit(guard);
    assert.equal(exit.code, 130);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "INTERRUPTED");
    assert.equal(result.restore.confirmed, true);
    assert.equal(result.child.processGroupClean, true);
    assertRestored(fixture);
    assertNoUnsafeReverseCommand(readState(fixture));
  } finally {
    fixture.dispose();
  }
}

async function testHupInterruptStillRestores() {
  await testNamedSignalInterruptStillRestores("SIGHUP");
}

async function testQuitInterruptStillRestores() {
  await testNamedSignalInterruptStillRestores("SIGQUIT");
}

function testLockLossAfterTransportCutBlocksChildAndSkipsRestore() {
  const fixture = makeFixture({dropLockAfterCommand: "shell svc data disable"});
  try {
    const {output, outcome} = invokeGuard(
      fixture,
      [process.execPath, fixture.child],
      {},
      {adbTimeoutMs: "1000", timeoutMs: "5000"},
    );
    assert.equal(outcome.status, 2, outcome.stderr);
    const result = JSON.parse(fs.readFileSync(output, "utf8"));
    assert.equal(result.status, "FAIL");
    assert.equal(result.errorCode, "DEVICE_LOCK_LOST");
    assert.equal(result.restore.attempted, false);
    assert.equal(result.restore.confirmed, false);
    assert.equal(result.restore.skippedDueToLockLoss, true);
    const state = readState(fixture);
    assert.equal(state.log.some((entry) => entry.type === "child"), false, "the offline child started after lock loss");
    assert.equal(state.wifi, 0);
    assert.equal(state.data, 0);
    assert.deepEqual(state.mappings, []);
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
    ["sequence-happy", testSequenceKeepsOneLockAcrossOnlinePreCutAndOfflineChild],
    ["sequence-precut-failure", testSequencePreCutFailureDoesNotTouchTransport],
    ["sequence-drift", testSequencePreCutBaselineDriftFailsClosedBeforeCut],
    ["sequence-precut-timeout", testSequencePreCutTimeoutDoesNotTouchTransport],
    ["sequence-symlink", testSequenceSymlinkedPlanFailsBeforeDeviceCalls],
    ["child-failure", testChildFailureStillRestores],
    ["two-column", testTwoColumnReverseRowsPass],
    ["radio-baseline", testRadioOffBaselineStillCutsAndRestoresExactTopology],
    ["private-log", testChildOutputStaysInPrivateBoundedLog],
    ["timeout", testTimeoutKillsProcessGroupAndRestores],
    ["hung-adb", testHungAdbIsBoundedAndRestores],
    ["normal-root-descendant", testNormalRootExitDrainsSurvivingDescendantBeforeRestore],
    ["partial-cut", testPartialCutFailureRestoresOnlyMissingMappings],
    ["concurrent-mapping", testConcurrentMappingFailsClosedWithoutDeletingIt],
    ["unexpected-mapping", testUnexpectedReverseMappingFailsBeforeMutation],
    ["control-ack", testUnsafeControlAcknowledgementFailsBeforeMutation],
    ["symlink", testSymlinkedOutputParentFailsBeforeDeviceCalls],
    ["system-flock", testSystemFlockWhenRequested],
    ["repeated-signal", testTermInterruptStillRestores],
    ["hup", testHupInterruptStillRestores],
    ["quit", testQuitInterruptStillRestores],
    ["lock-contention", testContendedLockReturnsNotRunBeforeDeviceCalls],
    ["lock-loss-after-cut", testLockLossAfterTransportCutBlocksChildAndSkipsRestore],
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
