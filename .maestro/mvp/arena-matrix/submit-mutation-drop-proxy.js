#!/usr/bin/env node
"use strict";

// A deliberately narrow, audit-first test proxy. It is not a general purpose reverse proxy.
// It may drop one exact, successful submitMutation response only after the upstream function
// has answered. Every other request is forwarded unchanged; later matching retries are blocked
// locally until the test runner restores the normal adb reverse mapping.

const {createHash, randomBytes} = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const MAX_DEFAULT_BODY_BYTES = 1_048_576;
const DEFAULT_UPSTREAM_TIMEOUT_MS = 30_000;
const PRIVATE_MODE_MASK = 0o077;
const CONFIG_KEYS = new Set([
  "listenHost", "listenPort", "upstreamHost", "upstreamPort", "targetPath",
  "operation", "mutationId", "attemptId", "maxBodyBytes",
]);

const sha256 = (value) => createHash("sha256").update(value).digest("hex");

function fail(message) {
  throw new Error(`submit-mutation-drop-proxy: ${message}`);
}

function requireSafeString(value, name, expression, maxLength = 256) {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength || !expression.test(value)) {
    fail(`invalid ${name}`);
  }
  return value;
}

function requirePort(value, name) {
  if (!Number.isInteger(value) || value < 1 || value > 65_535) fail(`invalid ${name}`);
  return value;
}

function requireListenPort(value) {
  if (!Number.isInteger(value) || value < 0 || value > 65_535) fail("invalid listenPort");
  return value;
}

function validateConfig(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail("config must be an object");
  for (const key of Object.keys(value)) if (!CONFIG_KEYS.has(key)) fail("config contains an unsupported key");
  const config = {
    listenHost: requireSafeString(value.listenHost, "listenHost", /^(?:127\.0\.0\.1|localhost)$/),
    // Port zero asks the OS for a fresh local port. The caller must read it from the
    // private ready artifact before it changes adb reverse.
    listenPort: requireListenPort(value.listenPort),
    upstreamHost: requireSafeString(value.upstreamHost, "upstreamHost", /^[A-Za-z0-9][A-Za-z0-9.-]*$/),
    upstreamPort: requirePort(value.upstreamPort, "upstreamPort"),
    targetPath: requireSafeString(value.targetPath, "targetPath", /^\/[A-Za-z0-9._~!$&'()*+,;=:@/%-]*$/),
    operation: requireSafeString(value.operation, "operation", /^[A-Za-z0-9._-]+$/),
    mutationId: requireSafeString(value.mutationId, "mutationId", /^[A-Za-z0-9:_-]+$/),
    attemptId: requireSafeString(value.attemptId, "attemptId", /^[a-f0-9]{64}$/),
    maxBodyBytes: value.maxBodyBytes === undefined ? MAX_DEFAULT_BODY_BYTES : value.maxBodyBytes,
  };
  if (!Number.isInteger(config.maxBodyBytes) || config.maxBodyBytes < 1_024 || config.maxBodyBytes > MAX_DEFAULT_BODY_BYTES) {
    fail("invalid maxBodyBytes");
  }
  return Object.freeze(config);
}

function requirePrivateRegularFile(file, description) {
  if (typeof file !== "string" || !path.isAbsolute(file)) fail(`${description} path must be absolute`);
  const metadata = fs.lstatSync(file);
  if (!metadata.isFile() || metadata.isSymbolicLink()) fail(`${description} must be a regular file`);
  if ((metadata.mode & PRIVATE_MODE_MASK) !== 0) fail(`${description} permissions are not private`);
}

function readPrivateConfig(file) {
  requirePrivateRegularFile(file, "config");
  const raw = fs.readFileSync(file, "utf8");
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_) {
    fail("config is not valid JSON");
  }
  return {config: validateConfig(parsed), sha256: sha256(raw)};
}

function requireNewArtifact(file, description) {
  if (typeof file !== "string" || !path.isAbsolute(file)) fail(`${description} path must be absolute`);
  if (fs.existsSync(file)) fail(`${description} already exists`);
  const directory = path.dirname(file);
  const metadata = fs.lstatSync(directory);
  if (!metadata.isDirectory() || metadata.isSymbolicLink()) fail(`${description} parent is not a directory`);
  if ((metadata.mode & PRIVATE_MODE_MASK) !== 0) fail(`${description} parent permissions are not private`);
}

function writePrivateJson(destination, value) {
  const temporary = `${destination}.tmp-${process.pid}-${randomBytes(8).toString("hex")}`;
  fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, {mode: 0o600, flag: "wx"});
  fs.renameSync(temporary, destination);
  fs.chmodSync(destination, 0o600);
}

function readBody(request, maxBodyBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > maxBodyBytes) {
        request.destroy();
        reject(new Error("request body exceeds limit"));
        return;
      }
      chunks.push(chunk);
    });
    request.once("aborted", () => reject(new Error("client aborted request")));
    request.once("error", reject);
    request.once("end", () => resolve(Buffer.concat(chunks)));
  });
}

function isExactTarget(request, body, config) {
  if (request.method !== "POST" || request.url !== config.targetPath) return false;
  let outer;
  try {
    outer = JSON.parse(body.toString("utf8"));
  } catch (_) {
    return false;
  }
  const data = outer?.data;
  if (!data || typeof data !== "object" || Array.isArray(data)) return false;
  if (data.operation !== config.operation || data.mutationId !== config.mutationId || typeof data.payload !== "string") return false;
  try {
    const payload = JSON.parse(data.payload);
    return payload?.attemptId === config.attemptId;
  } catch (_) {
    return false;
  }
}

function requestOptions(request, body, config) {
  const headers = {...request.headers};
  for (const header of ["connection", "keep-alive", "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade"]) {
    delete headers[header];
  }
  headers.host = `${config.upstreamHost}:${config.upstreamPort}`;
  headers["content-length"] = String(body.length);
  return {
    host: config.upstreamHost,
    port: config.upstreamPort,
    method: request.method,
    path: request.url,
    headers,
  };
}

function responseHeaders(headers) {
  const forwarded = {...headers};
  for (const header of ["connection", "keep-alive", "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade"]) {
    delete forwarded[header];
  }
  return forwarded;
}

function createAudit(config, configSha256) {
  return {
    artifactVersion: 1,
    status: "WAITING_FOR_EXACT_TARGET",
    configSha256,
    target: {
      path: config.targetPath,
      operation: config.operation,
      mutationIdSha256: sha256(config.mutationId),
      attemptIdSha256: sha256(config.attemptId),
    },
    events: [],
  };
}

function startDropProxy(configInput, options) {
  const config = validateConfig(configInput);
  if (!options || typeof options !== "object" || Array.isArray(options)) fail("options must be an object");
  const auditPath = options?.auditPath;
  const readyPath = options?.readyPath;
  const configSha256 = options?.configSha256 ?? sha256(JSON.stringify(configInput));
  requireNewArtifact(auditPath, "audit");
  if (readyPath !== undefined) {
    requireNewArtifact(readyPath, "ready artifact");
    if (path.resolve(auditPath) === path.resolve(readyPath)) fail("ready artifact must differ from audit");
  }

  // Once a matching request reaches upstream, never forward another matching request.
  // A timeout or a non-2xx response cannot prove that the server did not commit it.
  // This makes a failed fault injection safe to abandon rather than accidentally turn
  // into a duplicate financial operation.
  let exactTargetState = "WAITING_FOR_EXACT_TARGET";
  let stopped = false;
  const activeUpstreams = new Set();
  let server;
  const audit = createAudit(config, configSha256);
  const persist = () => writePrivateJson(auditPath, audit);
  const event = (type, fields = {}) => {
    audit.events.push({atMs: Date.now(), type, ...fields});
    persist();
  };
  let auditWriteFailed = false;
  const failClosedForAudit = (response, upstream) => {
    if (auditWriteFailed) return;
    auditWriteFailed = true;
    stopped = true;
    if (upstream) upstream.destroy();
    for (const active of activeUpstreams) active.destroy();
    if (response && !response.destroyed) response.destroy();
    server?.closeAllConnections?.();
    server?.close(() => {});
  };
  const tryEvent = (type, fields, response, upstream) => {
    try {
      event(type, fields);
      return true;
    } catch (_) {
      failClosedForAudit(response, upstream);
      return false;
    }
  };

  server = http.createServer(async (request, response) => {
    let body;
    try {
      body = await readBody(request, config.maxBodyBytes);
    } catch (error) {
      if (!tryEvent("REQUEST_REJECTED", {reason: "BODY_UNREADABLE"}, response)) return;
      if (!response.destroyed) response.destroy(error);
      return;
    }
    const exactTarget = isExactTarget(request, body, config);
    if (exactTarget && exactTargetState !== "WAITING_FOR_EXACT_TARGET") {
      if (!tryEvent(
        exactTargetState === "UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED"
          ? "EXACT_TARGET_BLOCKED_AFTER_DROP"
          : "EXACT_TARGET_BLOCKED_AFTER_UPSTREAM_CONTACT",
        {requestSha256: sha256(body)},
        response,
      )) return;
      if (!response.destroyed) response.destroy();
      return;
    }
    if (exactTarget) {
      exactTargetState = "EXACT_TARGET_IN_FLIGHT";
      audit.status = exactTargetState;
      if (!tryEvent("EXACT_TARGET_SENT_UPSTREAM", {requestSha256: sha256(body)}, response)) return;
    }
    let upstream;
    try {
      upstream = http.request(requestOptions(request, body, config));
    } catch (_) {
      if (exactTarget && !stopped) {
        exactTargetState = "EXACT_TARGET_UPSTREAM_ERROR";
        audit.status = exactTargetState;
        tryEvent("EXACT_TARGET_UPSTREAM_ERROR", {requestSha256: sha256(body)}, response);
      }
      if (!response.destroyed) response.destroy();
      return;
    }
    activeUpstreams.add(upstream);
    const releaseUpstream = () => activeUpstreams.delete(upstream);
    upstream.once("close", releaseUpstream);
    upstream.setTimeout(DEFAULT_UPSTREAM_TIMEOUT_MS, () => upstream.destroy(new Error("upstream timeout")));
    let receivedResponse = false;
    upstream.once("response", (upstreamResponse) => {
      receivedResponse = true;
      if (stopped) {
        upstreamResponse.resume();
        return;
      }
      if (!exactTarget) {
        response.writeHead(upstreamResponse.statusCode ?? 502, responseHeaders(upstreamResponse.headers));
        upstreamResponse.once("error", () => {
          if (!response.destroyed) response.destroy();
        });
        upstreamResponse.pipe(response);
        return;
      }
      const statusCode = upstreamResponse.statusCode ?? 502;
      if (statusCode < 200 || statusCode >= 300) {
        exactTargetState = "EXACT_TARGET_UPSTREAM_NON_SUCCESS";
        audit.status = exactTargetState;
        if (!tryEvent("EXACT_TARGET_UPSTREAM_NON_SUCCESS", {statusCode, requestSha256: sha256(body)}, response, upstream)) return;
        response.writeHead(statusCode, responseHeaders(upstreamResponse.headers));
        upstreamResponse.pipe(response);
        return;
      }
      const responseHash = createHash("sha256");
      let responseBytes = 0;
      let responseTerminal = false;
      if (!tryEvent("EXACT_TARGET_UPSTREAM_HEADERS", {statusCode, requestSha256: sha256(body)}, response, upstream)) return;
      upstreamResponse.on("data", (chunk) => {
        responseHash.update(chunk);
        responseBytes += chunk.length;
      });
      const responseFailedBeforeDrop = () => {
        if (stopped || responseTerminal) return;
        responseTerminal = true;
        exactTargetState = "UPSTREAM_RESPONSE_FAILED_BEFORE_DOWNSTREAM_DROP";
        audit.status = exactTargetState;
        if (!tryEvent("UPSTREAM_RESPONSE_FAILED_BEFORE_DOWNSTREAM_DROP", {statusCode}, response, upstream)) return;
        if (!response.destroyed) response.destroy();
      };
      upstreamResponse.once("error", responseFailedBeforeDrop);
      upstreamResponse.once("aborted", responseFailedBeforeDrop);
      upstreamResponse.once("close", responseFailedBeforeDrop);
      upstreamResponse.once("end", () => {
        if (stopped || responseTerminal) return;
        responseTerminal = true;
        exactTargetState = "UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED";
        audit.status = exactTargetState;
        if (!tryEvent("UPSTREAM_RESPONSE_COMPLETE_AFTER_DROP", {
          statusCode,
          responseBytes,
          responseSha256: responseHash.digest("hex"),
        }, response, upstream)) return;
        if (!response.destroyed) response.destroy();
      });
      upstreamResponse.resume();
    });
    upstream.once("error", () => {
      if (!receivedResponse && exactTarget && !stopped) {
        exactTargetState = "EXACT_TARGET_UPSTREAM_ERROR";
        audit.status = exactTargetState;
        if (!tryEvent("EXACT_TARGET_UPSTREAM_ERROR", {requestSha256: sha256(body)}, response, upstream)) return;
      }
      if (!response.destroyed) response.destroy();
    });
    upstream.end(body);
  });
  server.on("clientError", (_error, socket) => socket.destroy());

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(config.listenPort, config.listenHost, () => {
      server.removeListener("error", reject);
      const address = server.address();
      if (!address || typeof address === "string") return reject(new Error("proxy did not bind TCP"));
      audit.listen = {host: config.listenHost, port: address.port};
      if (!tryEvent("READY", {})) return reject(new Error("submit-mutation-drop-proxy: audit persistence failed"));
      if (readyPath !== undefined) {
        try {
          writePrivateJson(readyPath, {
            artifactVersion: 1,
            status: "READY",
            auditSha256: sha256(fs.readFileSync(auditPath)),
            listenPort: address.port,
          });
        } catch (_) {
          failClosedForAudit();
          return reject(new Error("submit-mutation-drop-proxy: ready artifact persistence failed"));
        }
      }
      resolve({
        address,
        auditPath,
        stop: async () => {
          if (stopped) return;
          stopped = true;
          if (audit.status === "WAITING_FOR_EXACT_TARGET" || audit.status === "EXACT_TARGET_IN_FLIGHT") {
            audit.status = "STOPPED_WITHOUT_DROP";
          }
          tryEvent("STOPPED", {});
          server.closeAllConnections?.();
          for (const upstream of activeUpstreams) upstream.destroy();
          await new Promise((done) => server.close(done));
        },
      });
    });
  });
}

function parseArguments(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--help") return {help: true};
    if (!new Set(["--config", "--audit", "--ready-file"]).has(token) || values.has(token)) fail("invalid arguments");
    const value = argv[++index];
    if (!value || value.startsWith("--")) fail("invalid arguments");
    values.set(token, value);
  }
  if (!values.has("--config") || !values.has("--audit")) fail("--config and --audit are required");
  return {configPath: values.get("--config"), auditPath: values.get("--audit"), readyPath: values.get("--ready-file")};
}

async function main() {
  const args = parseArguments(process.argv.slice(2));
  if (args.help) {
    process.stdout.write("Usage: submit-mutation-drop-proxy.js --config <private.json> --audit <new-private.json> [--ready-file <new-private.json>]\n");
    return;
  }
  const loaded = readPrivateConfig(args.configPath);
  const proxy = await startDropProxy(loaded.config, {
    auditPath: args.auditPath,
    readyPath: args.readyPath,
    configSha256: loaded.sha256,
  });
  let shuttingDown = false;
  const shutdown = async () => {
    if (shuttingDown) return;
    shuttingDown = true;
    await proxy.stop();
    process.exitCode = 0;
  };
  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);
}

if (require.main === module) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 2;
  });
}

module.exports = {readPrivateConfig, startDropProxy, validateConfig};
