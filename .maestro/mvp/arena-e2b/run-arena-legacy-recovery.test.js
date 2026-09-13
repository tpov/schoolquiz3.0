"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {EventEmitter} = require("node:events");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {test} = require("node:test");

const {
  TARGET_SERIAL,
  LegacyRecoveryError,
  parseArguments,
  evidenceEnvironment,
  sourceHash,
  compareRoomSnapshots,
  captureStoppedSnapshot,
  runFlow,
  executeRecovery,
  inspectInterrupted,
} = require("./run-arena-legacy-recovery");

const hash = (value) => createHash("sha256").update(value).digest("hex");
const OWNER = "retained-owner";
const TICKET = "a".repeat(64);
const ATTEMPT = "b".repeat(64);
const SERVER = "c".repeat(64);
const ECONOMY = "d".repeat(64);

test("source hash accepts a tracked 0644 flow but never a symlink", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-flow-hash-"));
  const file = path.join(root, "flow.yaml");
  try {
    fs.writeFileSync(file, "appId: retained\n", {mode: 0o644});
    fs.chmodSync(file, 0o644);
    assert.equal(sourceHash(file, "flow"), hash("appId: retained\n"));
    const link = path.join(root, "link.yaml"); fs.symlinkSync(file, link);
    assert.throws(() => sourceHash(link, "flow"), {code: "UNSAFE_SOURCE"});
  } finally { fs.rmSync(root, {recursive: true, force: true}); }
});

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function tables({terminal = false, acknowledged = false, unrelated = false, profileTimestamp = 10, profileMoney = 20} = {}) {
  return {
    arena_ticket_records: [{
      owner_uid: OWNER,
      ticket_id: TICKET,
      attempt_id: ATTEMPT,
      request_id: "original-request",
      terms_json: "{\"immutable\":true}",
      snapshot_json: "{\"frozen\":true}",
      snapshot_sha256: "d".repeat(64),
      server_status: terminal ? "SETTLED" : "RESERVED",
      terminal_json: terminal ? "{\"receipt\":\"original\"}" : null,
      local_use: "LAUNCHED",
      decision: "SETTLE",
      observed_at_ms: terminal ? 20 : 10,
      runner_checkpoint_json: "{\"checkpoint\":1}",
      runner_checkpoint_revision: 1,
    }],
    arena_command_journal: [{
      owner_uid: OWNER,
      command_kind: "SETTLE",
      command_id: ATTEMPT,
      ticket_id: TICKET,
      attempt_id: ATTEMPT,
      body_json: "{\"original\":true}",
      body_sha256: "e".repeat(64),
      state: acknowledged ? "ACKNOWLEDGED" : "PENDING",
      last_failure: null,
      created_at_ms: 1,
    }],
    user_profiles: [{
      uid: OWNER,
      nickname: "private-name",
      updatedAtMs: profileTimestamp,
      lifePointsUpdatedAtMs: profileTimestamp,
      nolics: profileMoney,
    }],
    outbox: [{
      mutation_id: "unrelated-mutation",
      owner_uid: OWNER,
      state: unrelated ? "SENT" : "WAITING",
    }],
  };
}

function eligible(localState, serverEvidence = SERVER, proof = "f".repeat(64), economy = ECONOMY, economyCount = 5) {
  return {
    status: "LEGACY_RECOVERY_ELIGIBLE",
    readerVersion: "arena-legacy-settlement-recovery-v1",
    incidentId: "arena-clock-e499-20260912",
    freshAcceptance: "FAIL",
    freshAcceptanceCode: "SETTLEMENT_ECONOMY_MISMATCH",
    permittedAction: "READ_SERVER_TICKET_ONLY",
    operationSubmissionAllowed: false,
    documentWrites: 0,
    localWrites: 0,
    deviceCalls: 0,
    ticketFingerprint: "1".repeat(16),
    attemptFingerprint: "2".repeat(16),
    serverEvidenceSha256: serverEvidence,
    serverEconomySha256: economy,
    serverEconomyDocumentCount: economyCount,
    localProofSha256: proof,
    localState,
  };
}

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-recovery-wrapper-"));
  fs.chmodSync(root, 0o700);
  const outputDir = path.join(root, "output");
  fs.mkdirSync(outputDir, {mode: 0o700});
  const events = [];
  const captures = [];
  const config = {
    serial: TARGET_SERIAL,
    outputDir,
    contextPath: path.join(root, "context.json"),
    incidentPath: path.join(root, "incident.json"),
    context: {runId: "legacy-e499", ownerUid: OWNER, ticketId: TICKET, attemptId: ATTEMPT},
    selectorValues: {
      E2B_SELECTOR_INTERNET: "Internet",
      E2B_SELECTOR_ARENA_TITLE: "Arena",
      E2B_SELECTOR_SHOW_LOCAL_TICKETS: "Show local tickets",
      E2B_SELECTOR_TARGET_TICKET_LABEL: "Ticket: " + TICKET,
      E2B_SELECTOR_RECOVER_TICKET: "Refresh this ticket from server",
      E2B_SELECTOR_TERMINAL_TICKET: "SETTLED",
    },
    contextSha256: "3".repeat(64),
    incidentSha256: "4".repeat(64),
    flowSha256: "5".repeat(64),
    env: {GCLOUD_PROJECT: "demo-schoolquiz-mvp", FIRESTORE_EMULATOR_HOST: "schoolquiz-mvp-firebase:8080"},
  };
  const before = tables();
  const after = tables({terminal: true, acknowledged: true, profileTimestamp: 11});
  function capture(label, state) {
    const rawDir = path.join(outputDir, "raw-room-" + label + "-" + captures.length);
    fs.mkdirSync(rawDir, {mode: 0o700});
    let cleaned = false;
    const record = {
      label,
      dbPath: path.join(rawDir, "schoolquiz.db"),
      tables: clone(state),
      cleanup() {
        if (!cleaned) {
          cleaned = true;
          events.push("cleanup:" + label);
          fs.rmSync(rawDir, {recursive: true, force: true});
        }
      },
      summary: {
        files: {
          database: {bytes: 1, sha256: hash("db-" + label)},
          wal: null,
          shm: null,
          auth: {bytes: 1, sha256: hash("same-auth")},
        },
        logicalSha256: hash(JSON.stringify(state)),
        tableRowCounts: {},
      },
    };
    captures.push(record);
    events.push("capture:" + label);
    return record;
  }
  let flowCalls = 0;
  let classifierCalls = 0;
  const deps = {
    device: {
      async assertReady() { events.push("ready"); },
      async stopApp() { events.push("stop"); },
    },
    async withLock(operation) {
      events.push("lock");
      try { return await operation(); } finally { events.push("unlock"); }
    },
    async captureSnapshot(label) {
      return capture(label, label === "pre" ? before : after);
    },
    async classifierMain(argv, env) {
      classifierCalls += 1;
      events.push("classifier:" + argv[argv.indexOf("--db") + 1].split("/").at(-2));
      assert.deepEqual(argv.slice(0, 5), ["--context", config.contextPath, "--incident", config.incidentPath, "--db"]);
      assert.equal(env.GCLOUD_PROJECT, "demo-schoolquiz-mvp");
      assert.equal(env.FIRESTORE_EMULATOR_HOST, "schoolquiz-mvp-firebase:8080");
      return classifierCalls === 1 ? eligible("STALE", SERVER, "6".repeat(64)) :
        eligible("RECONCILED", SERVER, "7".repeat(64));
    },
    async runFlow() {
      flowCalls += 1;
      events.push("flow");
    },
  };
  return {
    root,
    config,
    deps,
    events,
    captures,
    get flowCalls() { return flowCalls; },
    dispose() { fs.rmSync(root, {recursive: true, force: true}); },
  };
}

test("one stopped exact recovery preserves server evidence and permits only two target Room rows", async () => {
  const value = fixture();
  try {
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "RECOVERY_RECONCILED");
    assert.equal(result.freshAcceptance, "FAIL");
    assert.equal(result.serverEvidenceSha256, SERVER);
    assert.equal(result.serverEconomySha256, ECONOMY);
    assert.equal(result.serverEconomyDocumentCount, 5);
    assert.equal(value.flowCalls, 1);
    assert.deepEqual(result.allowedRoomChanges, {
      targetRows: 2,
      ticketChangedColumns: ["observed_at_ms", "server_status", "terminal_json"],
      commandChangedColumns: ["state"],
      profileTimestampColumns: ["lifePointsUpdatedAtMs", "updatedAtMs"],
    });
    assert.deepEqual(value.events, [
      "lock", "ready", "stop", "capture:pre", "classifier:raw-room-pre-0",
      "flow", "stop", "capture:post", "classifier:raw-room-post-1", "unlock",
    ]);
    const output = JSON.stringify(JSON.parse(fs.readFileSync(path.join(value.config.outputDir, "result.json"), "utf8")));
    assert.equal(output.includes(OWNER), false);
    assert.equal(output.includes(TICKET), false);
    assert.equal(fs.readdirSync(value.config.outputDir).some((name) => name.startsWith("raw-room-pre-")), true);
    assert.equal(fs.readdirSync(value.config.outputDir).some((name) => name.startsWith("raw-room-post-")), true);
  } finally {
    value.dispose();
  }
});

test("a changed server digest stops after the one flow and requires manual review", async () => {
  const value = fixture();
  try {
    let calls = 0;
    value.deps.classifierMain = async () => {
      calls += 1;
      return calls === 1 ? eligible("STALE", SERVER) : eligible("RECONCILED", "9".repeat(64));
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "SERVER_EVIDENCE_CHANGED");
    assert.equal(result.automaticRetry, false);
    assert.equal(value.flowCalls, 1);
    assert.ok(value.events.filter((entry) => entry === "stop").length >= 3);
  } finally {
    value.dispose();
  }
});

test("a changed current economy digest stops after the one flow", async () => {
  const value = fixture();
  try {
    let calls = 0;
    value.deps.classifierMain = async () => {
      calls += 1;
      return calls === 1 ? eligible("STALE") : eligible("RECONCILED", SERVER, "f".repeat(64), "8".repeat(64));
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "SERVER_ECONOMY_CHANGED");
    assert.equal(result.automaticRetry, false);
    assert.equal(value.flowCalls, 1);
  } finally {
    value.dispose();
  }
});

test("a changed stopped auth snapshot stops after the one flow", async () => {
  const value = fixture();
  try {
    const original = value.deps.captureSnapshot;
    value.deps.captureSnapshot = async (label) => {
      const capture = await original(label);
      if (label === "post") capture.summary.files.auth = {bytes: 1, sha256: hash("different-auth")};
      return capture;
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "AUTH_SNAPSHOT_CHANGED");
    assert.equal(result.automaticRetry, false);
    assert.equal(value.flowCalls, 1);
  } finally {
    value.dispose();
  }
});

test("an unrelated Room mutation is never accepted as ticket recovery", async () => {
  const value = fixture();
  try {
    const original = value.deps.captureSnapshot;
    value.deps.captureSnapshot = async (label) => {
      const capture = await original(label);
      if (label !== "pre") capture.tables.outbox[0].state = "SENT";
      return capture;
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "UNRELATED_ROOM_CHANGE");
    assert.equal(value.flowCalls, 1);
  } finally {
    value.dispose();
  }
});

test("a prior reconciled state is diagnostic only and never invokes the flow", async () => {
  const value = fixture();
  try {
    value.deps.classifierMain = async () => eligible("RECONCILED");
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "ALREADY_RECONCILED");
    assert.equal(value.flowCalls, 0);
  } finally {
    value.dispose();
  }
});

test("a held shared lock never stops the retained app", async () => {
  const value = fixture();
  try {
    value.deps.withLock = async () => { throw new LegacyRecoveryError("DEVICE_LOCK_HELD"); };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "BLOCKED");
    assert.equal(result.errorCode, "DEVICE_LOCK_HELD");
    assert.equal(value.flowCalls, 0);
    assert.deepEqual(value.events, []);
  } finally {
    value.dispose();
  }
});

test("an interrupted fake UI process gets one stopped diagnostic post and no flow retry", async () => {
  const value = fixture();
  try {
    let lockHeld = false;
    value.deps.withLock = async (operation) => {
      value.events.push("lock");
      lockHeld = true;
      try { return await operation(); } finally {
        lockHeld = false;
        value.events.push("unlock");
      }
    };
    value.deps.device.stopApp = async () => {
      assert.equal(lockHeld, true, "every stop and diagnostic snapshot must remain under the shared device lock");
      value.events.push("stop");
    };
    const original = value.deps.runFlow;
    value.deps.runFlow = async () => {
      await original();
      throw new LegacyRecoveryError("FLOW_INTERRUPTED");
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "FLOW_INTERRUPTED");
    assert.equal(value.flowCalls, 1);
    assert.equal(value.events.includes("capture:interrupted-post"), true);
    assert.equal(value.events.includes("capture:post"), false);
  } finally {
    value.dispose();
  }
});

test("a failed post classifier is not retried automatically", async () => {
  const value = fixture();
  try {
    let calls = 0;
    value.deps.classifierMain = async () => {
      calls += 1;
      if (calls === 1) return eligible("STALE");
      throw new LegacyRecoveryError("POST_READER_FAILED");
    };
    const result = await executeRecovery(value.config, value.deps);
    assert.equal(result.status, "REQUIRES_REVIEW");
    assert.equal(result.errorCode, "POST_READER_FAILED");
    assert.equal(calls, 2);
    assert.equal(value.events.includes("capture:interrupted-post"), false);
  } finally {
    value.dispose();
  }
});

test("comparison accepts only known profile timestamps and rejects profile money", () => {
  const before = tables();
  const allowed = tables({terminal: true, acknowledged: true, profileTimestamp: 11});
  assert.equal(compareRoomSnapshots(before, allowed, {ownerUid: OWNER, ticketId: TICKET, attemptId: ATTEMPT}).targetRows, 2);
  const bad = tables({terminal: true, acknowledged: true, profileMoney: 21});
  assert.throws(() => compareRoomSnapshots(before, bad, {ownerUid: OWNER, ticketId: TICKET, attemptId: ATTEMPT}),
    {code: "PROFILE_MUTATION"});
});

test("stopped snapshot uses injected device and reader, fingerprints auth, and retains private raw data", async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-capture-"));
  fs.chmodSync(root, 0o700);
  const reads = [];
  try {
    const snapshot = await captureStoppedSnapshot({
      outputDir: root,
      label: "pre",
      device: {
        async readFile(name) {
          reads.push(name);
          return Buffer.from(name);
        },
        async exists(name) { return name.endsWith("-wal"); },
        async authNames() { return ["com.google.firebase.auth.api.Store.abc.xml"]; },
      },
      async readDatabase(dbPath) {
        assert.ok(dbPath.endsWith("schoolquiz.db"));
        return tables();
      },
    });
    assert.deepEqual(reads, [
      "databases/schoolquiz.db",
      "databases/schoolquiz.db-wal",
      "shared_prefs/com.google.firebase.auth.api.Store.abc.xml",
    ]);
    assert.equal(snapshot.summary.files.auth.sha256, hash("shared_prefs/com.google.firebase.auth.api.Store.abc.xml"));
    const raw = path.dirname(snapshot.dbPath);
    assert.equal(fs.existsSync(raw), true);
    assert.equal(fs.existsSync(snapshot.dbPath), true);
    assert.equal(fs.existsSync(snapshot.dbPath + "-wal"), true);
    assert.equal(fs.existsSync(path.join(raw, "firebase-auth.xml")), true);
    assert.equal(fs.statSync(raw).mode & 0o777, 0o700);
    assert.equal(fs.statSync(snapshot.dbPath).mode & 0o777, 0o600);
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
});

test("a tracked recovery flow source is accepted and a timed-out fake process tree is terminated", async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-flow-"));
  fs.chmodSync(root, 0o700);
  const outputDir = path.join(root, "output");
  const flowPath = path.join(root, "flow.yaml");
  fs.mkdirSync(outputDir, {mode: 0o700});
  fs.writeFileSync(flowPath, "appId: com.tpov.schoolquiz.mvp\n", {mode: 0o644});
  fs.chmodSync(flowPath, 0o644);
  const signals = [];
  try {
    await assert.rejects(() => runFlow({
      maestro: "fake-maestro",
      serial: TARGET_SERIAL,
      selectorValues: {},
      runId: "legacy-e499",
      outputDir,
      env: {PATH: process.env.PATH || ""},
      flowPath,
      timeoutMs: 10,
      spawnProcess() {
        const child = new EventEmitter();
        child.pid = 999_999_999;
        child.stdout = new EventEmitter();
        child.stderr = new EventEmitter();
        child.kill = (signal) => {
          signals.push(signal);
          if (signal === "SIGTERM") setImmediate(() => child.emit("close", null, "SIGTERM"));
          return true;
        };
        return child;
      },
    }), {code: "FLOW_TIMEOUT"});
    assert.deepEqual(signals, ["SIGTERM"]);
    assert.equal(fs.statSync(flowPath).mode & 0o777, 0o644);
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
});

test("CLI rejects a different device and evidence environment rejects credentials", () => {
  assert.throws(() => parseArguments([
    "--serial", "other", "--output-root", "/private", "--context", "a", "--incident", "b", "--selectors", "c",
  ]), {code: "UNEXPECTED_DEVICE"});
  assert.throws(() => parseArguments([
    "--serial", TARGET_SERIAL, "--output-root", "/private", "--context", "a", "--incident", "b", "--selectors", "c",
    "--lock-root", "/other",
  ]), {code: "USAGE"});
  assert.throws(() => evidenceEnvironment({GOOGLE_APPLICATION_CREDENTIALS: "/secret"}), {code: "UNSAFE_ENVIRONMENT"});
  assert.throws(() => evidenceEnvironment({}), {code: "UNSAFE_ENVIRONMENT"});
  assert.throws(() => evidenceEnvironment({GCLOUD_PROJECT: "demo-schoolquiz-mvp", FIRESTORE_EMULATOR_HOST: "other:8080"}), {code: "UNSAFE_ENVIRONMENT"});
});

test("interrupted inspection is read-only and never retries", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-inspect-"));
  fs.chmodSync(root, 0o700);
  try {
    fs.writeFileSync(path.join(root, "recovery-state.json"), JSON.stringify({
      artifactVersion: 1, wrapperVersion: "x", status: "FLOW_STARTED",
    }) + "\n", {mode: 0o600});
    const result = inspectInterrupted(root);
    assert.deepEqual(result, {
      artifactVersion: 1,
      wrapperVersion: "arena-legacy-recovery-wrapper-v1",
      status: "REQUIRES_REVIEW",
      freshAcceptance: "FAIL",
      automaticRetry: false,
      requiredAction: "MANUAL_REVIEW_REQUIRED",
    });
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
});

test("error type remains structured", () => {
  const error = new LegacyRecoveryError("EXAMPLE");
  assert.equal(error.code, "EXAMPLE");
});
