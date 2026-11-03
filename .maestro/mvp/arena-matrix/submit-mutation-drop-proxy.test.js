"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const {startDropProxy, validateConfig} = require("./submit-mutation-drop-proxy");

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.removeListener("error", reject);
      resolve(server.address().port);
    });
  });
}

function close(server) {
  return new Promise((resolve) => server.close(resolve));
}

function request(port, body, pathname = "/demo/us-central1/submitMutation", extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const client = http.request({host: "127.0.0.1", port, method: "POST", path: pathname, headers: {
      "content-type": "application/json", "content-length": Buffer.byteLength(body), ...extraHeaders,
    }}, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve({statusCode: response.statusCode, body: Buffer.concat(chunks).toString("utf8")}));
    });
    client.once("error", reject);
    client.setTimeout(3_000, () => client.destroy(new Error("request timed out")));
    client.end(body);
  });
}

async function droppedRequest(port, body, extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const client = http.request({host: "127.0.0.1", port, method: "POST", path: "/demo/us-central1/submitMutation", headers: {
      "content-type": "application/json", "content-length": Buffer.byteLength(body), ...extraHeaders,
    }}, () => reject(new Error("drop proxy unexpectedly returned a response")));
    client.once("error", (error) => resolve(error));
    client.setTimeout(3_000, () => client.destroy(new Error("request timed out")));
    client.end(body);
  });
}

async function waitFor(predicate) {
  const deadline = Date.now() + 5_000;
  while (Date.now() < deadline) {
    const result = predicate();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("timed out waiting for proxy audit");
}

async function main() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "arena-drop-proxy-"));
  fs.chmodSync(root, 0o700);
  const auditPath = path.join(root, "audit.json");
  const readyPath = path.join(root, "ready.json");
  const nonSuccessAuditPath = path.join(root, "non-success-audit.json");
  const truncatedAuditPath = path.join(root, "truncated-audit.json");
  const mutationId = "lesson_runner-SUBMIT_ATTEMPT-" + "a".repeat(64);
  const attemptId = "a".repeat(64);
  let upstreamCalls = 0;
  const upstream = http.createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      upstreamCalls += 1;
      const statusCode = Number(request.headers["x-test-upstream-status"] || 200);
      const body = JSON.stringify({result: {accepted: true, bodyBytes: Buffer.concat(chunks).length}});
      if (request.headers["x-test-upstream-abort"] === "1") {
        response.writeHead(statusCode, {"content-type": "application/json", "content-length": String(Buffer.byteLength(body) + 1)});
        response.flushHeaders();
        response.write(body);
        setTimeout(() => response.destroy(), 80);
      } else {
        response.writeHead(statusCode, {"content-type": "application/json"});
        setTimeout(() => response.end(body), 80);
      }
    });
  });
  const upstreamPort = await listen(upstream);
  let proxy;
  try {
    proxy = await startDropProxy({
      listenHost: "127.0.0.1", listenPort: 0, upstreamHost: "127.0.0.1", upstreamPort,
      targetPath: "/demo/us-central1/submitMutation", operation: "lesson_runner.SUBMIT_ATTEMPT",
      mutationId, attemptId,
    }, {auditPath, readyPath});
    const ready = JSON.parse(fs.readFileSync(readyPath, "utf8"));
    assert.equal(ready.status, "READY");
    assert.equal(ready.listenPort, proxy.address.port);
    assert.equal((fs.statSync(readyPath).mode & 0o077), 0);
    const targetBody = JSON.stringify({data: {
      mutationId, operation: "lesson_runner.SUBMIT_ATTEMPT", payload: JSON.stringify({attemptId, ticketId: "private-ticket"}),
    }});
    const nonTargetBody = JSON.stringify({data: {
      mutationId: "other-mutation", operation: "lesson_runner.SUBMIT_ATTEMPT", payload: JSON.stringify({attemptId}),
    }});

    const firstRequest = droppedRequest(proxy.address.port, targetBody);
    await waitFor(() => JSON.parse(fs.readFileSync(auditPath, "utf8")).events.some((event) => event.type === "EXACT_TARGET_SENT_UPSTREAM"));
    const concurrentError = await droppedRequest(proxy.address.port, targetBody);
    assert.ok(concurrentError.code === "ECONNRESET" || concurrentError.code === "EPIPE");
    const firstError = await firstRequest;
    assert.ok(firstError.code === "ECONNRESET" || firstError.code === "EPIPE");
    const afterDrop = await waitFor(() => {
      const audit = JSON.parse(fs.readFileSync(auditPath, "utf8"));
      return audit.status === "UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED" ? audit : null;
    });
    assert.equal(upstreamCalls, 1);
    assert.equal(afterDrop.events.filter((event) => event.type === "EXACT_TARGET_UPSTREAM_HEADERS").length, 1);
    assert.equal(afterDrop.events.filter((event) => event.type === "EXACT_TARGET_BLOCKED_AFTER_UPSTREAM_CONTACT").length, 1);
    assert.equal(JSON.stringify(afterDrop).includes(mutationId), false);
    assert.equal(JSON.stringify(afterDrop).includes(attemptId), false);
    assert.equal(JSON.stringify(afterDrop).includes("private-ticket"), false);
    assert.equal(JSON.stringify(ready).includes(mutationId), false);
    assert.equal(JSON.stringify(ready).includes(attemptId), false);
    assert.equal((fs.statSync(auditPath).mode & 0o077), 0);

    const passThrough = await request(proxy.address.port, nonTargetBody);
    assert.equal(passThrough.statusCode, 200);
    assert.equal(upstreamCalls, 2);

    const replayError = await droppedRequest(proxy.address.port, targetBody);
    assert.ok(replayError.code === "ECONNRESET" || replayError.code === "EPIPE");
    const afterReplay = JSON.parse(fs.readFileSync(auditPath, "utf8"));
    assert.equal(upstreamCalls, 2);
    assert.equal(afterReplay.events.filter((event) => event.type === "EXACT_TARGET_BLOCKED_AFTER_DROP").length, 1);
    assert.equal(afterReplay.status, "UPSTREAM_CONFIRMED_AND_DOWNSTREAM_DROPPED");

    const nonSuccessProxy = await startDropProxy({
      listenHost: "127.0.0.1", listenPort: 0, upstreamHost: "127.0.0.1", upstreamPort,
      targetPath: "/demo/us-central1/submitMutation", operation: "lesson_runner.SUBMIT_ATTEMPT",
      mutationId, attemptId,
    }, {auditPath: nonSuccessAuditPath});
    try {
      const nonSuccess = await request(nonSuccessProxy.address.port, targetBody, "/demo/us-central1/submitMutation", {"x-test-upstream-status": "409"});
      assert.equal(nonSuccess.statusCode, 409);
      assert.equal(upstreamCalls, 3);
      const replayAfterNonSuccess = await droppedRequest(nonSuccessProxy.address.port, targetBody);
      assert.ok(replayAfterNonSuccess.code === "ECONNRESET" || replayAfterNonSuccess.code === "EPIPE");
      const nonSuccessAudit = JSON.parse(fs.readFileSync(nonSuccessAuditPath, "utf8"));
      assert.equal(nonSuccessAudit.status, "EXACT_TARGET_UPSTREAM_NON_SUCCESS");
      assert.equal(nonSuccessAudit.events.filter((event) => event.type === "EXACT_TARGET_BLOCKED_AFTER_UPSTREAM_CONTACT").length, 1);
      assert.equal(upstreamCalls, 3);
    } finally {
      await nonSuccessProxy.stop();
    }

    const truncatedProxy = await startDropProxy({
      listenHost: "127.0.0.1", listenPort: 0, upstreamHost: "127.0.0.1", upstreamPort,
      targetPath: "/demo/us-central1/submitMutation", operation: "lesson_runner.SUBMIT_ATTEMPT",
      mutationId, attemptId,
    }, {auditPath: truncatedAuditPath});
    try {
      const truncatedError = await droppedRequest(truncatedProxy.address.port, targetBody, {"x-test-upstream-abort": "1"});
      assert.ok(truncatedError.code === "ECONNRESET" || truncatedError.code === "EPIPE");
      const truncatedAudit = await waitFor(() => {
        const audit = JSON.parse(fs.readFileSync(truncatedAuditPath, "utf8"));
        return audit.status === "UPSTREAM_RESPONSE_FAILED_BEFORE_DOWNSTREAM_DROP" ? audit : null;
      });
      assert.equal(truncatedAudit.events.filter((event) => event.type === "UPSTREAM_RESPONSE_COMPLETE_AFTER_DROP").length, 0);
      assert.equal(upstreamCalls, 4);
      const replayAfterTruncation = await droppedRequest(truncatedProxy.address.port, targetBody);
      assert.ok(replayAfterTruncation.code === "ECONNRESET" || replayAfterTruncation.code === "EPIPE");
      assert.equal(upstreamCalls, 4);
    } finally {
      await truncatedProxy.stop();
    }

    assert.throws(() => validateConfig({...JSON.parse(JSON.stringify({
      listenHost: "127.0.0.1", listenPort: 5101, upstreamHost: "127.0.0.1", upstreamPort: 5001,
      targetPath: "/demo/us-central1/submitMutation", operation: "lesson_runner.SUBMIT_ATTEMPT",
      mutationId, attemptId,
    })), extra: true}), /unsupported/);
    const collisionPath = path.join(root, "collision.json");
    assert.throws(() => startDropProxy({
      listenHost: "127.0.0.1", listenPort: 0, upstreamHost: "127.0.0.1", upstreamPort,
      targetPath: "/demo/us-central1/submitMutation", operation: "lesson_runner.SUBMIT_ATTEMPT",
      mutationId, attemptId,
    }, {auditPath: collisionPath, readyPath: collisionPath}), /must differ/);
    process.stdout.write("submit-mutation-drop-proxy: PASS\n");
  } finally {
    await proxy?.stop();
    await close(upstream);
    fs.rmSync(root, {recursive: true, force: true});
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
