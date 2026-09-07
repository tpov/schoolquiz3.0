"use strict";

// DRAFT revision 3. Root chooses execution. Fixed synthetic owner; Firestore read-only RPCs only.
// No Admin SDK, Auth, Functions, dynamic URL, seed, financial operation or document write.
// Only change from revision 2: the three fixed documents are read with :batchGet inside the same
// read-only transaction instead of documents.get?transaction=, which this emulator never answers.
// No parser rule was relaxed; the emitted snapshot shape, scopes, owner checks and limits are equal.
const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {readFileSync, statSync} = require("node:fs");
const PROJECT = "demo-schoolquiz-mvp";
const HOST = "schoolquiz-mvp-firebase:8080";
const OWNER = "g6AmO03kszB0XBm9mBRBQOnm1ZmO";
const DB_NAME = `projects/${PROJECT}/databases/(default)`;
const DOCUMENTS = `${DB_NAME}/documents`;
const ORIGIN = `http://${HOST}/v1/`;
const VERSION = "arena-catalog-ui-oracle-v1";
const MAX_REQUEST_BYTES = 4096;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const MAX_TOTAL_BYTES = 12 * 1024 * 1024;
const MAX_EVIDENCE_BYTES = 1024 * 1024;
const MAX_REQUESTS = 20;
const MAX_DOCUMENTS = 1200;
const MAX_MS = 120000;
let phase = "arguments";
const SOURCES = Object.freeze({
  "functions/index.js": "7cad5bd6cb8b1d648112b063c67c418b8ff2464ada89a733fbfcf562882416ec",
  "functions/arena-lesson-catalog.js": "3ef0ef385a2ef44c7735408cd448f4fb6b73b53530e932a6289a4744ca8cc1d3",
  "functions/arena-tickets.js": "9aec302a4335801fd0e5705ee58fd8ff62c0edb8df6835e36e9cbe5d92f24229",
  "functions/arena-service.js": "e7dc2dd2fa1aaa984616ff728569f2a9260b36ea66c73abaaab52bdc56a238d0",
  "functions/premium-economy.js": "2bf97715d08077037290fb21281b59751c115f26f7309e01dad001e80737428f",
  "functions/currency-ledger.js": "ea5f922a2e8ba6304e1843904de739a4d11a4e26923bf2137597474e6ce9a0da",
  "scripts/mvp-smoke-receipt-oracle.js": "74051a3dedcda51a878181135e9fd79577d27341568246df8832e38f70a984f8",
});
const FIXED_DOCS = [`users/${OWNER}`, `premium_accounts/${OWNER}`, `arena_royalty_accounts/${OWNER}`];
// Every collection/owner field below comes from the pinned writer, not a collection-group guess.
const QUERIES = Object.freeze([
  {path: "arena_entry_tickets", field: "uid", value: OWNER, maximum: 100},
  {path: "arena_ticket_quotas", field: "uid", value: OWNER, maximum: 100},
  {path: "arena_ticket_cancellations", field: "uid", value: OWNER, maximum: 100},
  {path: "audit/currency_ledger/entries", field: "uid", value: OWNER, maximum: 200},
  // Royalties may credit an author/sink/carry UID, but the ticket producer binds payerUid.
  {path: "audit/currency_ledger/entries", field: "details.payerUid", value: OWNER, maximum: 100},
  {path: "mutation_keys", field: "uid", value: OWNER, maximum: 100},
  {path: "arena_mutations", field: "uid", value: OWNER, maximum: 100},
  {path: "arena_awards", field: "recipientUid", value: OWNER, maximum: 100},
  {path: `premium_accounts/${OWNER}/lots`, field: null, value: null, maximum: 100},
]);
const sha = value => createHash("sha256").update(value).digest("hex");
const plain = value => value !== null && typeof value === "object" && !Array.isArray(value) &&
  Object.getPrototypeOf(value) === Object.prototype;
function only(value, allowed, required = allowed) {
  assert.ok(plain(value), "INVALID_SHAPE");
  assert.ok(Object.keys(value).every(key => allowed.includes(key)), "UNKNOWN_FIELD");
  assert.ok(required.every(key => Object.hasOwn(value, key)), "MISSING_FIELD");
}
function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (plain(value)) return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  assert.ok(value === null || ["boolean", "string", "number"].includes(typeof value), "INVALID_CANONICAL_VALUE");
  if (typeof value === "number") assert.ok(Number.isFinite(value), "NONFINITE_JSON");
  return Object.is(value, -0) ? "-0" : JSON.stringify(value);
}
function timestamp(value) {
  assert.equal(typeof value, "string", "INVALID_TIME");
  assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/, "INVALID_TIME");
  assert.ok(Number.isFinite(Date.parse(value)), "INVALID_TIME");
  const [base, fractional = ""] = value.slice(0, -1).split(".");
  assert.equal(new Date(`${base}Z`).toISOString().slice(0, 19), base, "INVALID_TIME");
  return `${base}.${fractional.padEnd(9, "0")}Z`;
}
function fields(value, depth = 0) {
  assert.ok(plain(value) && depth <= 32, "INVALID_FIELDS");
  for (const entry of Object.values(value)) wireValue(entry, depth + 1);
}
function wireValue(value, depth) {
  assert.ok(plain(value) && Object.keys(value).length === 1 && depth <= 32, "INVALID_WIRE_VALUE");
  const [kind, item] = Object.entries(value)[0];
  switch (kind) {
    case "nullValue": assert.equal(item, null); break;
    case "booleanValue": assert.equal(typeof item, "boolean"); break;
    case "integerValue":
      assert.equal(typeof item, "string"); assert.match(item, /^-?(0|[1-9][0-9]*)$/);
      assert.ok(BigInt(item) >= -(2n ** 63n) && BigInt(item) < 2n ** 63n); break;
    case "doubleValue":
      assert.ok((typeof item === "number" && Number.isFinite(item)) || ["NaN", "Infinity", "-Infinity"].includes(item)); break;
    case "timestampValue": timestamp(item); break;
    case "stringValue": assert.equal(typeof item, "string"); break;
    case "bytesValue": assert.equal(typeof item, "string"); assert.match(item, /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/); break;
    case "referenceValue": assert.equal(typeof item, "string"); assert.ok(item.startsWith(`${DOCUMENTS}/`)); break;
    case "geoPointValue":
      only(item, ["latitude", "longitude"]);
      assert.ok(Number.isFinite(item.latitude) && Math.abs(item.latitude) <= 90);
      assert.ok(Number.isFinite(item.longitude) && Math.abs(item.longitude) <= 180); break;
    case "arrayValue":
      only(item, ["values"], []); assert.ok(item.values === undefined || Array.isArray(item.values));
      for (const entry of item.values || []) wireValue(entry, depth + 1); break;
    case "mapValue": only(item, ["fields"], []); fields(item.fields === undefined ? {} : item.fields, depth + 1); break;
    default: assert.fail("UNKNOWN_WIRE_TYPE");
  }
}
function document(raw, expectedPath) {
  only(raw, ["name", "fields", "createTime", "updateTime"], ["name", "createTime", "updateTime"]);
  assert.equal(raw.name, `${DOCUMENTS}/${expectedPath}`, "WRONG_DOCUMENT");
  const values = raw.fields === undefined ? {} : raw.fields;
  fields(values); timestamp(raw.createTime); timestamp(raw.updateTime);
  return {path: expectedPath, exists: true, createTime: raw.createTime, updateTime: raw.updateTime,
    fieldsSha256: sha(canonical(values))};
}
const absent = path => ({path, exists: false, createTime: null, updateTime: null, fieldsSha256: null});
function valueAt(rawFields, path) {
  const parts = path.split("."); let cursor = rawFields;
  for (const [index, part] of parts.entries()) {
    const entry = cursor?.[part];
    if (index === parts.length - 1) return entry?.stringValue;
    cursor = entry?.mapValue?.fields;
  }
  return undefined;
}
function queryBody(spec, transaction) {
  const bits = spec.path.split("/"); const collectionId = bits.pop();
  const structuredQuery = {from: [{collectionId}], limit: spec.maximum + 1};
  if (spec.field) structuredQuery.where = {fieldFilter: {
    field: {fieldPath: spec.field}, op: "EQUAL", value: {stringValue: spec.value},
  }};
  return {endpoint: `${DOCUMENTS}${bits.length ? `/${bits.join("/")}` : ""}:runQuery`,
    body: {structuredQuery, transaction}};
}
// A done marker can end an empty or nonempty stream; no entry may follow done=true.
function* queryEntries(reply, maximum) {
  assert.ok(Array.isArray(reply) && reply.length >= 1 && reply.length <= maximum + 2, "INVALID_QUERY_RESPONSE");
  let done = false;
  for (const entry of reply) {
    assert.equal(done, false, "QUERY_ENTRY_AFTER_DONE");
    only(entry, ["document", "readTime", "skippedResults", "done"], ["readTime"]);
    if (Object.hasOwn(entry, "done")) assert.equal(typeof entry.done, "boolean", "INVALID_QUERY_DONE");
    done = entry.done === true;
    yield entry;
  }
}
// batchGet answers with one entry per requested document, in unspecified order, each either found
// or missing. Every requested path must appear exactly once and no other path may appear. readTime
// must match the snapshot the queries already fixed, so a batched read cannot widen the snapshot.
function batchEntries(reply, readTime) {
  assert.ok(Array.isArray(reply) && reply.length === FIXED_DOCS.length, "INVALID_BATCH_RESPONSE");
  assert.equal(typeof readTime, "string", "INVALID_BATCH_READ_TIME");
  const seen = new Map();
  for (const entry of reply) {
    only(entry, ["found", "missing", "readTime", "transaction"], ["readTime"]);
    assert.equal(timestamp(entry.readTime), readTime, "INCONSISTENT_READ_TIME");
    assert.ok(Object.hasOwn(entry, "found") !== Object.hasOwn(entry, "missing"), "INVALID_BATCH_ENTRY");
    const missing = Object.hasOwn(entry, "missing");
    const name = missing ? entry.missing : (plain(entry.found) ? entry.found.name : undefined);
    assert.equal(typeof name, "string", "INVALID_BATCH_ENTRY");
    assert.ok(name.startsWith(`${DOCUMENTS}/`), "WRONG_BATCH_DOCUMENT");
    const path = name.slice(DOCUMENTS.length + 1);
    assert.ok(FIXED_DOCS.includes(path), "WRONG_BATCH_DOCUMENT");
    assert.ok(!seen.has(path), "DUPLICATE_BATCH_DOCUMENT");
    seen.set(path, missing ? null : entry.found);
  }
  assert.ok(FIXED_DOCS.every(path => seen.has(path)), "MISSING_BATCH_DOCUMENT");
  return FIXED_DOCS.map(path => [path, seen.get(path)]);
}
function validateEvidence(value) {
  assert.equal(value.status, "SNAPSHOT"); assert.equal(value.oracleVersion, VERSION);
  assert.equal(value.projectId, PROJECT); assert.equal(value.ownerUid, OWNER);
  assert.equal(value.firestoreHost, HOST); assert.equal(value.documentWrites, 0);
  assert.equal(value.consistentReadOnlyTransaction, true); timestamp(value.readTime);
  assert.deepEqual(value.sourceHashes, SOURCES); assert.match(value.scriptSha256, /^[a-f0-9]{64}$/);
  assert.equal(value.scriptSha256, sha(readFileSync(__filename)), "ORACLE_REVISION_CHANGED");
  assert.deepEqual(value.queryScopes, QUERIES);
  assert.ok(Array.isArray(value.documents) && value.documents.length <= MAX_DOCUMENTS);
  assert.deepEqual(value.fixedPaths, FIXED_DOCS);
  assert.equal(value.documents.length, new Set(value.documents.map(row => row.path)).size);
  for (const row of value.documents) {
    only(row, ["path", "exists", "createTime", "updateTime", "fieldsSha256"]);
    assert.ok(FIXED_DOCS.includes(row.path) || QUERIES.some(spec =>
      row.path.startsWith(`${spec.path}/`) && !row.path.slice(spec.path.length + 1).includes("/")));
    if (row.exists) { assert.match(row.fieldsSha256, /^[a-f0-9]{64}$/); timestamp(row.createTime); timestamp(row.updateTime); }
    else { assert.deepEqual(row, absent(row.path)); assert.ok(FIXED_DOCS.includes(row.path)); }
  }
  assert.deepEqual(value.documents.map(row => row.path), value.documents.map(row => row.path).sort());
  assert.ok(FIXED_DOCS.every(path => value.documents.some(row => row.path === path)));
  assert.equal(value.queryResults.length, QUERIES.length);
  for (const [i, result] of value.queryResults.entries()) {
    only(result, ["scope", "count", "paths"]); assert.deepEqual(result.scope, QUERIES[i]);
    assert.ok(Number.isSafeInteger(result.count) && result.count >= 0 && result.count <= QUERIES[i].maximum);
    assert.equal(result.paths.length, result.count); assert.equal(new Set(result.paths).size, result.count);
    assert.deepEqual(result.paths, [...result.paths].sort());
    assert.ok(result.paths.every(path => path.startsWith(`${QUERIES[i].path}/`) &&
      !path.slice(QUERIES[i].path.length + 1).includes("/") &&
      value.documents.some(row => row.path === path && row.exists)));
  }
  assert.ok(value.documents.every(row => FIXED_DOCS.includes(row.path) ||
    value.queryResults.some(result => result.paths.includes(row.path))));
  assert.equal(value.stateSha256, sha(canonical({documents: value.documents, queryResults: value.queryResults})));
}
function checkEnvironment() {
  assert.equal(process.env.GCLOUD_PROJECT, PROJECT);
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOST);
  for (const name of ["GOOGLE_APPLICATION_CREDENTIALS", "FIREBASE_CONFIG", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY"]) {
    assert.equal(process.env[name], undefined, "UNEXPECTED_CREDENTIAL_OR_PROXY_ENV");
  }
  assert.equal(process.env.ARENA_UI_ORACLE_APPROVED, VERSION);
  // This acknowledgement is supplied only after root actually inspects private Docker networking.
  assert.equal(process.env.ARENA_UI_PRIVATE_NETWORK_VERIFIED, "schoolquiz-mvp-private");
  assert.equal(sha(readFileSync(__filename)), process.env.ARENA_UI_ORACLE_SHA256);
  for (const [path, digest] of Object.entries(SOURCES)) assert.equal(sha(readFileSync(`/repo/${path}`)), digest, "SOURCE_DRIFT");
}
async function snapshot() {
  phase = "preflight";
  checkEnvironment();
  const started = Date.now(); const metrics = {requests: 0, responseBytes: 0, documents: 0};
  let transaction = null; let readTime = null;
  function deadline() { assert.ok(Date.now() - started <= MAX_MS, "READ_BUDGET_EXPIRED"); }
  async function request(endpoint, body, missingAllowed = false, releasing = false) {
    assert.ok(endpoint.startsWith(`${DB_NAME}/`), "WRONG_ENDPOINT");
    if (!releasing) deadline();
    assert.ok(++metrics.requests <= MAX_REQUESTS, "REQUEST_LIMIT");
    const serialized = body === null ? null : JSON.stringify(body);
    assert.ok(serialized === null || Buffer.byteLength(serialized) <= MAX_REQUEST_BYTES, "REQUEST_TOO_LARGE");
    const response = await fetch(`${ORIGIN}${endpoint}`, {method: body === null ? "GET" : "POST",
      redirect: "error", signal: AbortSignal.timeout(15000),
      headers: {authorization: "Bearer owner", "content-type": "application/json"},
      ...(serialized === null ? {} : {body: serialized})});
    assert.ok(response.body, "MISSING_RESPONSE_BODY");
    const reader = response.body.getReader(); const chunks = []; let bytes = 0;
    try {
      for (;;) {
        const chunk = await reader.read(); if (chunk.done) break;
        bytes += chunk.value.byteLength; metrics.responseBytes += chunk.value.byteLength;
        assert.ok(bytes <= MAX_RESPONSE_BYTES && metrics.responseBytes <= MAX_TOTAL_BYTES, "RESPONSE_LIMIT");
        chunks.push(chunk.value);
      }
    } finally { await reader.cancel().catch(() => {}); }
    const decoded = JSON.parse(new TextDecoder("utf-8", {fatal: true}).decode(Buffer.concat(chunks)));
    if (!releasing) deadline();
    if (response.status === 404 && missingAllowed) {
      assert.equal(decoded?.error?.status, "NOT_FOUND", "UNEXPECTED_ABSENCE"); return null;
    }
    assert.equal(response.status, 200, "READ_RPC_FAILED"); assert.equal(decoded?.error, undefined, "READ_RPC_FAILED");
    return decoded;
  }
  const records = new Map(); const queryResults = [];
  function remember(row) {
    if (records.has(row.path)) assert.deepEqual(records.get(row.path), row, "INCONSISTENT_SNAPSHOT");
    else records.set(row.path, row);
    assert.ok(records.size <= MAX_DOCUMENTS, "DOCUMENT_LIMIT");
  }
  try {
    phase = "begin-read-only-transaction";
    const begin = await request(`${DOCUMENTS}:beginTransaction`, {options: {readOnly: {}}});
    only(begin, ["transaction"]); assert.equal(typeof begin.transaction, "string");
    assert.match(begin.transaction, /^[A-Za-z0-9+/=_-]+$/); assert.ok(begin.transaction.length <= 2048);
    transaction = begin.transaction; // Never logged, persisted or accepted from CLI.
    for (const spec of QUERIES) {
      phase = `read-${spec.path}-${spec.field || "all-owner-lots"}`;
      const query = queryBody(spec, transaction); const reply = await request(query.endpoint, query.body);
      const paths = [];
      for (const entry of queryEntries(reply, spec.maximum)) {
        const normalized = timestamp(entry.readTime);
        if (readTime === null) readTime = normalized;
        else assert.equal(normalized, readTime, "INCONSISTENT_READ_TIME");
        assert.ok(entry.skippedResults === undefined || entry.skippedResults === 0, "SKIPPED_RESULTS");
        if (entry.document === undefined) continue;
        assert.ok(plain(entry.document), "INVALID_QUERY_DOCUMENT");
        const prefix = `${DOCUMENTS}/${spec.path}/`; const name = entry.document.name;
        assert.equal(typeof name, "string"); assert.ok(name.startsWith(prefix), "WRONG_QUERY_PATH");
        const docId = name.slice(prefix.length); assert.ok(docId && !docId.includes("/"), "WRONG_QUERY_PATH");
        if (spec.field) assert.equal(valueAt(entry.document.fields, spec.field), spec.value, "WRONG_QUERY_OWNER");
        const path = `${spec.path}/${docId}`; remember(document(entry.document, path)); paths.push(path);
      }
      assert.ok(paths.length <= spec.maximum, "QUERY_OVERFLOW");
      assert.equal(new Set(paths).size, paths.length, "DUPLICATE_QUERY_DOCUMENT");
      queryResults.push({scope: spec, count: paths.length, paths: paths.sort()});
    }
    phase = "read-fixed-documents-batch";
    // Revision 3. The emulator never answers documents.get with a transaction parameter: it hangs
    // with no HTTP status at all, which is what stopped revision 2 at phase read-users/<owner>.
    // Measured on this exact emulator by arena-fixed-user-transport-probe-r3.json:
    // get-with-transaction -> TimeoutError after 8002 ms and no status; batchGet-with-transaction ->
    // 200 in 25 ms for the same document under the same handle; get without a transaction -> 200.
    // :batchGet is the documented transactional read, so the read scope and owner set are unchanged.
    const batch = await request(`${DOCUMENTS}:batchGet`,
      {documents: FIXED_DOCS.map(path => `${DOCUMENTS}/${path}`), transaction});
    for (const [path, found] of batchEntries(batch, readTime)) {
      remember(found === null ? absent(path) : document(found, path));
    }
    assert.ok(records.get(`users/${OWNER}`).exists, "SYNTHETIC_OWNER_MISSING");
    const documents = [...records.values()].sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
    metrics.documents = documents.length;
    phase = "validate-snapshot";
    const result = {status: "SNAPSHOT", oracleVersion: VERSION, projectId: PROJECT, ownerUid: OWNER,
      firestoreHost: HOST, documentWrites: 0, consistentReadOnlyTransaction: true, readTime,
      scriptSha256: sha(readFileSync(__filename)), sourceHashes: SOURCES,
      fixedPaths: FIXED_DOCS, queryScopes: QUERIES, documents, queryResults,
      stateSha256: sha(canonical({documents, queryResults})), metrics};
    validateEvidence(result); checkEnvironment();
    return result;
  } finally {
    // Release ONLY our read-only transaction. No document delete, rollback of an app operation or commit.
    if (transaction !== null) {
      const released = await request(`${DOCUMENTS}:rollback`, {transaction}, false, true);
      only(released, []);
    }
  }
}
function loadEvidence(path) {
  assert.ok(statSync(path).isFile() && statSync(path).size <= MAX_EVIDENCE_BYTES, "INVALID_EVIDENCE_FILE");
  const value = JSON.parse(readFileSync(path, "utf8")); validateEvidence(value); return value;
}
function compare(before, after) {
  validateEvidence(before); validateEvidence(after);
  assert.equal(before.scriptSha256, after.scriptSha256, "ORACLE_REVISION_CHANGED");
  assert.ok(timestamp(after.readTime) > timestamp(before.readTime), "NON_ADVANCING_SNAPSHOTS");
  const all = new Set([...before.documents, ...after.documents].map(row => row.path));
  const old = new Map(before.documents.map(row => [row.path, row]));
  const next = new Map(after.documents.map(row => [row.path, row]));
  const changedPaths = [...all].filter(path => canonical(old.get(path) || null) !== canonical(next.get(path) || null)).sort();
  const equal = before.stateSha256 === after.stateSha256;
  return {status: equal ? "UNCHANGED_SCOPED_STATE" : "SCOPED_STATE_CHANGED", oracleVersion: VERSION,
    projectId: PROJECT, ownerUid: OWNER, beforeReadTime: before.readTime, afterReadTime: after.readTime,
    beforeSha256: before.stateSha256, afterSha256: after.stateSha256, changedPaths,
    beforeDocuments: before.documents.length, afterDocuments: after.documents.length,
    documentWrites: 0, attribution: "NONE: concurrent sync, regeneration or another device may change this owner"};
}
async function main() {
  const [mode, ...args] = process.argv.slice(2);
  if (mode === "snapshot" && args.length === 0) {
    const result = await snapshot(); const output = JSON.stringify(result, null, 2);
    assert.ok(Buffer.byteLength(output) <= MAX_EVIDENCE_BYTES, "EVIDENCE_LIMIT"); console.log(output);
  } else if (mode === "compare" && args.length === 2) {
    const result = compare(loadEvidence(args[0]), loadEvidence(args[1])); console.log(JSON.stringify(result, null, 2));
    if (result.status !== "UNCHANGED_SCOPED_STATE") process.exitCode = 1;
  } else assert.fail("Use snapshot, or compare BEFORE_JSON AFTER_JSON");
}
if (require.main === module) main().catch(() => {
  console.error(JSON.stringify({status: "FAIL_CLOSED", oracleVersion: VERSION, projectId: PROJECT,
    ownerUid: OWNER, phase, documentWrites: 0, rawErrorOmitted: true})); process.exitCode = 1;
});
module.exports = {canonical, timestamp, fields, document, absent, queryBody, queryEntries, batchEntries,
  validateEvidence, compare, SOURCES, FIXED_DOCS, QUERIES, OWNER, PROJECT, HOST, VERSION};
