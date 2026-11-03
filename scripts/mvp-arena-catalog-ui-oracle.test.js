"use strict";
const assert = require("node:assert/strict");
const path = require("node:path");
const SUBJECT = path.join(__dirname, "mvp-arena-catalog-ui-oracle.js");
const cur = require(SUBJECT);
let networkCalls = 0;
globalThis.fetch = () => { networkCalls++; throw Error("NETWORK_FORBIDDEN"); };
let passed = 0;
function test(name, run) { run(); passed++; console.log("PASS " + name); }

const DOCS = `projects/${cur.PROJECT}/databases/(default)/documents`;
const T = "2026-09-07T08:00:00.123456789Z";
const NORM = cur.timestamp(T);
const full = path => `${DOCS}/${path}`;
const doc = path => ({name: full(path), createTime: T, updateTime: T, fields: {a: {stringValue: "x"}}});
const foundEntry = path => ({readTime: T, found: doc(path)});
const missingEntry = path => ({readTime: T, missing: full(path)});
const all = () => cur.FIXED_DOCS.map(foundEntry);

// --- positive ---
test("all three found, returned in requested order", () => {
  const rows = cur.batchEntries(all(), NORM);
  assert.deepEqual(rows.map(r => r[0]), cur.FIXED_DOCS);
  assert.deepEqual(rows[0][1], doc(cur.FIXED_DOCS[0]));
});
test("unspecified server order is normalised to FIXED_DOCS order", () => {
  const rows = cur.batchEntries([...all()].reverse(), NORM);
  assert.deepEqual(rows.map(r => r[0]), cur.FIXED_DOCS);
});
test("missing entry maps to null, which the caller turns into absent()", () => {
  const rows = cur.batchEntries([foundEntry(cur.FIXED_DOCS[0]), missingEntry(cur.FIXED_DOCS[1]), foundEntry(cur.FIXED_DOCS[2])], NORM);
  assert.equal(rows[1][1], null);
  assert.deepEqual(cur.absent(cur.FIXED_DOCS[1]), {path: cur.FIXED_DOCS[1], exists: false, createTime: null, updateTime: null, fieldsSha256: null});
});
test("an optional transaction echo is tolerated", () => {
  const rows = all(); rows[0] = {...rows[0], transaction: "HANDLE"};
  assert.equal(cur.batchEntries(rows, NORM).length, 3);
});

// --- negative: shape ---
test("non-array rejected", () => assert.throws(() => cur.batchEntries({}, NORM), /INVALID_BATCH_RESPONSE/));
test("short array rejected", () => assert.throws(() => cur.batchEntries(all().slice(0, 2), NORM), /INVALID_BATCH_RESPONSE/));
test("long array rejected", () => assert.throws(() => cur.batchEntries([...all(), foundEntry(cur.FIXED_DOCS[0])], NORM), /INVALID_BATCH_RESPONSE/));
test("unknown entry field rejected", () => {
  const rows = all(); rows[0] = {...rows[0], surprise: 1};
  assert.throws(() => cur.batchEntries(rows, NORM), /UNKNOWN_FIELD/);
});
test("absent readTime rejected", () => {
  const rows = all(); delete rows[0].readTime;
  assert.throws(() => cur.batchEntries(rows, NORM), /MISSING_FIELD/);
});
test("both found and missing rejected", () => {
  const rows = all(); rows[0] = {readTime: T, found: doc(cur.FIXED_DOCS[0]), missing: full(cur.FIXED_DOCS[0])};
  assert.throws(() => cur.batchEntries(rows, NORM), /INVALID_BATCH_ENTRY/);
});
test("neither found nor missing rejected", () => {
  const rows = all(); rows[0] = {readTime: T};
  assert.throws(() => cur.batchEntries(rows, NORM), /INVALID_BATCH_ENTRY/);
});
test("non-object found rejected", () => {
  const rows = all(); rows[0] = {readTime: T, found: "SECRET"};
  assert.throws(() => cur.batchEntries(rows, NORM), /INVALID_BATCH_ENTRY/);
});

// --- negative: scope and owner ---
test("a document outside FIXED_DOCS is rejected, so the read scope cannot widen", () => {
  const rows = all(); rows[0] = foundEntry("users/SOMEONE_ELSE");
  assert.throws(() => cur.batchEntries(rows, NORM), /WRONG_BATCH_DOCUMENT/);
});
test("a foreign database prefix is rejected", () => {
  const rows = all(); rows[0] = {readTime: T, found: {name: "projects/other/databases/(default)/documents/users/x", createTime: T, updateTime: T}};
  assert.throws(() => cur.batchEntries(rows, NORM), /WRONG_BATCH_DOCUMENT/);
});
test("duplicate path rejected", () => {
  const rows = [foundEntry(cur.FIXED_DOCS[0]), foundEntry(cur.FIXED_DOCS[0]), foundEntry(cur.FIXED_DOCS[1])];
  assert.throws(() => cur.batchEntries(rows, NORM), /DUPLICATE_BATCH_DOCUMENT/);
});
test("a requested path silently dropped is rejected", () => {
  const rows = [foundEntry(cur.FIXED_DOCS[0]), foundEntry(cur.FIXED_DOCS[1]), missingEntry("users/SOMEONE_ELSE")];
  assert.throws(() => cur.batchEntries(rows, NORM), /WRONG_BATCH_DOCUMENT/);
});

// --- negative: snapshot consistency ---
test("a different readTime is rejected, so a batched read cannot widen the snapshot", () => {
  const rows = all(); rows[1] = {...rows[1], readTime: "2026-09-07T08:00:00.123456788Z"};
  assert.throws(() => cur.batchEntries(rows, NORM), /INCONSISTENT_READ_TIME/);
});
test("a malformed readTime is rejected", () => {
  const rows = all(); rows[0] = {...rows[0], readTime: "not-a-time"};
  assert.throws(() => cur.batchEntries(rows, NORM), /INVALID_TIME/);
});
test("a null snapshot readTime is rejected", () => assert.throws(() => cur.batchEntries(all(), null), /INVALID_BATCH_READ_TIME/));

// --- fixed authority: scopes, owner and version are constants, not inputs ---
test("owner, project, host and version are the pinned constants", () => {
  assert.equal(cur.PROJECT, "demo-schoolquiz-mvp");
  assert.equal(cur.HOST, "schoolquiz-mvp-firebase:8080");
  assert.equal(cur.OWNER, "g6AmO03kszB0XBm9mBRBQOnm1ZmO");
  assert.equal(cur.VERSION, "arena-catalog-ui-oracle-v1");
});
test("the read scope is exactly three fixed documents and nine owner-bound queries", () => {
  assert.deepEqual(cur.FIXED_DOCS, [
    `users/${cur.OWNER}`, `premium_accounts/${cur.OWNER}`, `arena_royalty_accounts/${cur.OWNER}`]);
  assert.equal(cur.QUERIES.length, 9);
  for (const q of cur.QUERIES) {
    assert.ok(q.value === null || q.value === cur.OWNER, "a query escapes the owner: " + q.path);
    assert.ok(Number.isSafeInteger(q.maximum) && q.maximum > 0);
  }
});
test("every generated query body stays inside the owner's database and carries no output", () => {
  for (const q of cur.QUERIES) {
    const built = cur.queryBody(q, "not-output");
    assert.ok(built.endpoint.startsWith(`projects/${cur.PROJECT}/databases/(default)/documents`), built.endpoint);
    assert.ok(built.endpoint.endsWith(":runQuery"));
    assert.equal(built.body.transaction, "not-output");
    assert.equal(built.body.structuredQuery.limit, q.maximum + 1);
    if (q.field) assert.equal(built.body.structuredQuery.where.fieldFilter.value.stringValue, cur.OWNER);
  }
});
test("the query stream parser still refuses a non-boolean or trailing done marker", () => {
  const e = extra => ({readTime: T, ...extra});
  const rows = [e({document: {name: "synthetic"}}), e({done: true})];
  assert.deepEqual([...cur.queryEntries(rows, 100)], rows);
  for (const bad of ["true", 1, null, {}, []]) assert.throws(() => [...cur.queryEntries([e({done: bad})], 100)], /INVALID_QUERY_DONE/);
  assert.throws(() => [...cur.queryEntries([e({done: true}), e({})], 100)], /QUERY_ENTRY_AFTER_DONE/);
  assert.throws(() => [...cur.queryEntries([], 100)], /INVALID_QUERY_RESPONSE/);
});
test("document() records only a field digest, never field values", () => {
  const raw = {name: full(cur.FIXED_DOCS[0]), createTime: T, updateTime: T,
    fields: {SECRET_FIELD_NAME: {stringValue: "SECRET_FIELD_VALUE"}}};
  const row = cur.document(raw, cur.FIXED_DOCS[0]);
  assert.deepEqual(Object.keys(row).sort(), ["createTime", "exists", "fieldsSha256", "path", "updateTime"]);
  assert.match(row.fieldsSha256, /^[a-f0-9]{64}$/);
  const emitted = JSON.stringify(row);
  for (const secret of ["SECRET_FIELD_NAME", "SECRET_FIELD_VALUE"]) {
    assert.ok(!emitted.includes(secret), "a field " + secret + " reached the snapshot row");
  }
  // A different value must produce a different digest, or the digest proves nothing.
  const other = cur.document({...raw, fields: {SECRET_FIELD_NAME: {stringValue: "OTHER"}}}, cur.FIXED_DOCS[0]);
  assert.notEqual(other.fieldsSha256, row.fieldsSha256);
});
test("nullValue rule is NOT relaxed: the wire enum string is still rejected", () => {
  assert.doesNotThrow(() => cur.fields({a: {nullValue: null}}));
  assert.throws(() => cur.fields({a: {nullValue: "NULL_VALUE"}}));
  assert.throws(() => cur.fields({a: {unknownKind: 1}}), /UNKNOWN_WIRE_TYPE/);
});
test("documents.get with a transaction is no longer constructed anywhere", () => {
  const src = require("node:fs").readFileSync(SUBJECT, "utf8");
  assert.ok(!src.includes("?transaction=${encodeURIComponent(transaction)}"), "stale get-with-transaction remains");
  assert.ok(src.includes(":batchGet"), "batchGet read missing");
});
test("no write endpoint appears in the revision-3 source", () => {
  const src = require("node:fs").readFileSync(SUBJECT, "utf8");
  for (const w of [":commit", ":batchWrite", '"PATCH"', '"DELETE"', '"PUT"', ":write"]) assert.ok(!src.includes(w), "write path present: " + w);
});
test("pure tests performed no network calls", () => assert.equal(networkCalls, 0));
console.log(JSON.stringify({passed, failed: 0, networkCalls, sdkCalls: 0, deviceCalls: 0, nodeVersion: process.version}));
