"use strict";

// Серверная половина fault-строки «потеря ответа после server commit» (3.3, серверная
// половина; матричная строка плана §2 Шаг 4 «Потеря ответа после server commit, повтор
// прежнего ID»). Скриптованный клиент вместо приложения: приложение и его outbox остаются
// device-половиной (hitl). Прокси работает ровно так же, как в устройстве: обрывает
// соединение ПОСЛЕ полного успешного ответа upstream по одному точному submitMutation.
//
// Что доказывает прогон:
//   1. upstream закоммитил эффект, хотя клиент ответ не получил (аудит прокси
//      UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED + запись mutation_keys completed на сервере);
//   2. повтор с тем же mutationId отвечает сохранённым результатом ({replayed: true}) и не
//      даёт второго эффекта (completedAtMs и состояние игрока неизменны, квитанция одна);
//   3. прокси больше не пропускает точный повтор (EXACT_TARGET_BLOCKED_AFTER_DROP), в том
//      числе конкурентный — вторая финансовая операция невозможна по построению.
//
// Идентификаторы мутации/попытки — свежие на каждый прогон; в публичный вывод идёт только
//Pass/FAIL и хеши, полные идентификаторы остаются в приватном каталоге прогона (0700).

const assert = require("node:assert/strict");
const {createHash, randomBytes, randomUUID} = require("node:crypto");
const {chmodSync, existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync} = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {spawn} = require("node:child_process");

const PROJECT = "demo-schoolquiz-mvp";
const HOSTS = {firestore: "127.0.0.1:18090", authPort: 19090, upstream: 5001};
const CALLABLE_PATH = `/${PROJECT}/us-central1/submitMutation`;
const OPERATION = "lesson_runner.SUBMIT_ATTEMPT";
const CALL_TIMEOUT_MS = 30000;

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOSTS.firestore, "firestore must be the demo backend");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only run");

const repoRoot = path.join(__dirname, "..", "..", "..");
const functionsDir = path.join(repoRoot, "functions");
const admin = require(path.join(functionsDir, "node_modules", "firebase-admin"));
const {payableTermsProjection} = require(path.join(functionsDir, "mutation-terms.js"));
const {readEconomyConstants} = require(path.join(functionsDir, "economy-constants.js"));
const manifest = JSON.parse(readFileSync(path.join(repoRoot, "scripts", "demo-fixtures", "manifest.json"), "utf8"));
assert.equal(manifest.schemaVersion, 2, "unsupported manifest schema");
admin.initializeApp({projectId: PROJECT});

const proxyScript = path.join(__dirname, "submit-mutation-drop-proxy.js");
const sha256 = (value) => createHash("sha256").update(value).digest("hex");

function privateRunDirectory() {
  const requested = process.argv[2];
  assert.ok(requested && path.isAbsolute(requested), "usage: submit-drop-server-fault-run.js <absolute private parent dir>");
  const metadata = lstatSync(requested);
  assert.ok(metadata.isDirectory() && !metadata.isSymbolicLink(), "parent must be a real directory");
  assert.equal(metadata.mode & 0o077, 0, "parent must be private (0700)");
  const runDir = mkdirSync(path.join(requested, `run-${new Date().toISOString().replace(/[:.]/g, "-")}`), {recursive: true});
  chmodSync(runDir, 0o700);
  return runDir;
}

async function callableFetch(port, body, idToken) {
  return fetch(`http://127.0.0.1:${port}${CALLABLE_PATH}`, {
    method: "POST",
    headers: {"content-type": "application/json", authorization: `Bearer ${idToken}`},
    body: JSON.stringify({data: body}),
    signal: AbortSignal.timeout(CALL_TIMEOUT_MS),
  });
}

async function playerIdToken(uid) {
  const customToken = await admin.auth().createCustomToken(uid);
  const response = await fetch(`http://127.0.0.1:${HOSTS.authPort}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=demo`, {
    method: "POST", headers: {"content-type": "application/json"},
    body: JSON.stringify({token: customToken, returnSecureToken: true}),
    signal: AbortSignal.timeout(CALL_TIMEOUT_MS),
  });
  assert.equal(response.status, 200, "auth emulator must exchange the custom token");
  const json = await response.json();
  assert.equal(typeof json.idToken, "string", "auth emulator returned no idToken");
  return json.idToken;
}

// Тот же приёмник ключей, что в index.js safeDocId: sha256[:12] + "_" + sanitized id.
function mutationKeyDocId(mutationId) {
  return `${sha256(mutationId).slice(0, 12)}_${mutationId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
}

async function readServerState(uid, mutationId) {
  const db = admin.firestore();
  const [keyDoc, userDoc] = await Promise.all([
    db.collection("mutation_keys").doc(mutationKeyDocId(mutationId)).get(),
    db.collection("users").doc(uid).get(),
  ]);
  return {
    key: keyDoc.exists ? keyDoc.data() : null,
    user: userDoc.exists ? userDoc.data() : null,
  };
}

async function main() {
  const runDir = privateRunDirectory();
  const db = admin.firestore();

  const player = manifest.roles.find((role) => role.id === "player-a");
  assert.ok(player, "manifest must carry player-a");

  const constantsSnapshot = await db.doc("configs/economy").get();
  const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null);
  const capturedTerms = payableTermsProjection(constants);

  const mutationId = `${OPERATION.replace(".", "-")}-${randomUUID()}`;
  const attemptId = randomBytes(32).toString("hex");
  const idToken = await playerIdToken(player.uid);

  // Транспортная строка доказывается на обычном home-уроке демо-бэкенда (mvp-smoke-v2 —
  // валидированная тест-фикстура самого бэкенда, тот же урок, что играли офлайн в hitl 2.7):
  // submitMutation и его ключ идемпотентности не зависят от урока. Арена-вариант строки
  // (подготовленный тикет, RESERVED → SETTLE с royalty) — device-половина 3.4 и 3.11.
  const attempt = {
    attemptId,
    lessonId: "mvp-smoke-v2-lesson",
    lessonVersion: 1,
    catalogId: "mvp-smoke-v2-catalog",
    questId: "mvp-smoke-v2-quest",
    sectionId: "mvp-smoke-v2-section",
    themeId: "mvp-smoke-v2-theme",
    sourceShelf: "home",
    difficulty: "EASY",
    codeAnswer: "99",
    percentScore: 100,
    completionKind: "COMPLETED",
  };

  const body = {operation: OPERATION, mutationId, payload: JSON.stringify(attempt), capturedTerms};

  // Приватные артефакты прогона: конфиг, ready, аудит — новые файлы в приватном каталоге.
  const configPath = path.join(runDir, "proxy-config.json");
  const auditPath = path.join(runDir, "proxy-audit.json");
  const readyPath = path.join(runDir, "proxy-ready.json");
  const config = {
    listenHost: "127.0.0.1", listenPort: 0,
    upstreamHost: "127.0.0.1", upstreamPort: HOSTS.upstream,
    targetPath: CALLABLE_PATH, operation: OPERATION, mutationId, attemptId,
  };
  writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`, {mode: 0o600});

  const proxyProcess = spawn(process.execPath, [
    proxyScript, "--config", configPath, "--audit", auditPath, "--ready-file", readyPath,
  ], {stdio: ["ignore", "ignore", "pipe"]});
  let proxyStderr = "";
  proxyProcess.stderr.on("data", (chunk) => { proxyStderr += chunk; });
  try {
    for (let waited = 0; waited < 10000 && !existsSync(readyPath); waited += 100) {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    assert.ok(existsSync(readyPath), `proxy did not become ready${proxyStderr ? `: ${proxyStderr.trim()}` : ""}`);
    const listenPort = JSON.parse(readFileSync(readyPath, "utf8")).listenPort;

    // 1. Точный запрос через прокси: upstream отвечает, соединение с клиентом обрывается.
    let dropError = null;
    try {
      await callableFetch(listenPort, body, idToken);
    } catch (error) {
      dropError = error;
    }
    assert.ok(dropError, "call #1 must not receive a response (the injected fault)");
    console.log(`call1 through proxy: connection dropped as designed (${dropError.cause ? dropError.cause.code : dropError.name})`);

    // 2. Эффект на сервере есть, несмотря на потерю ответа: ключ завершён.
    let state = await readServerState(player.uid, mutationId);
    for (let waited = 0; waited < 10000 && (!state.key || state.key.state !== "completed"); waited += 500) {
      await new Promise((resolve) => setTimeout(resolve, 500));
      state = await readServerState(player.uid, mutationId);
    }
    assert.ok(state.key, "mutation key missing after the dropped response");
    assert.equal(state.key.state, "completed", "upstream did not complete the mutation");
    assert.equal(state.key.termsVersion, capturedTerms.version, "settled terms version mismatch");
    const first = state;
    console.log(`server after drop: mutation_keys state=completed, completedAtMs=${state.key.completedAtMs}, receipts=${state.key.result && state.key.result.receipts ? state.key.result.receipts.length : 0}`);

    // 3. Точный повтор через прокси — обрывается локально, upstream не контактирует.
    let blockedError = null;
    try {
      await callableFetch(listenPort, body, idToken);
    } catch (error) {
      blockedError = error;
    }
    assert.ok(blockedError, "call #2 through proxy must be closed locally");

    // 4. Повтор мимо прокси, прямо upstream — тот же ключ: сохранённый ответ, без второго эффекта.
    const retryResponse = await callableFetch(HOSTS.upstream, body, idToken);
    assert.equal(retryResponse.status, 200, "direct retry must reach upstream");
    const retryJson = await retryResponse.json();
    assert.equal(retryJson.result && retryJson.result.replayed, true, "direct retry must be answered as a replay");
    const second = await readServerState(player.uid, mutationId);
    assert.equal(second.key.completedAtMs, first.key.completedAtMs, "retry changed completedAtMs (second effect)");
    assert.deepEqual(second.key.result, first.key.result, "retry changed the stored result");
    assert.equal((second.key.result.receipts || []).length, 1, "retry produced a second receipt");
    assert.deepEqual(second.user, first.user, "retry changed the player state");
    console.log(`direct retry: replayed=true, result identical, completedAtMs unchanged, one receipt`);

    // 5. Остановка прокси и вердикт аудита.
    proxyProcess.kill("SIGTERM");
    for (let waited = 0; waited < 10000 && proxyProcess.exitCode === null; waited += 100) {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    const audit = JSON.parse(readFileSync(auditPath, "utf8"));
    assert.equal(audit.status, "UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED", `proxy audit status ${audit.status}`);
    const types = audit.events.map((event) => event.type);
    for (const required of ["READY", "EXACT_TARGET_SENT_UPSTREAM", "UPSTREAM_RESPONSE_COMPLETE_AFTER_DROP", "EXACT_TARGET_BLOCKED_AFTER_DROP", "STOPPED"]) {
      assert.ok(types.includes(required), `audit is missing event ${required}`);
    }
    console.log(`proxy audit: ${audit.status}, events: ${types.join(",")}`);
    console.log(`private run dir: ${runDir}`);
    console.log(`ids (public hashes): mutationId=${sha256(mutationId).slice(0, 16)} attemptId=${sha256(attemptId).slice(0, 16)}`);
    console.log("SERVER FAULT RUN PASSED: drop after commit, replay without a second effect");
  } finally {
    if (proxyProcess.exitCode === null) proxyProcess.kill("SIGKILL");
  }
}

main().catch((error) => {
  console.error("server fault run failed:", error.message);
  process.exitCode = 1;
});
