"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {ticketDocumentId, attemptDocumentId, utcDayId} = require("./arena-tickets");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath; stub.loaded = true; stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const NOW = Date.parse("2026-09-06T12:00:00Z");

// Deliberately no frozen questions/private grading key or live user/lesson documents. The service
// suite exercises real issuance/cancel/settle producers; these cases exercise the real callable.
function storedTicket(status = "RESERVED", uid = "player") {
  const requestId = "lost-device-request"; const ticketId = ticketDocumentId(uid, requestId);
  const attemptId = attemptDocumentId(ticketId);
  let terminal = null;
  if (status === "CANCELLED") terminal = {receiptId: `arena-cancel:${ticketId}`, cancelledAtMs: NOW + 1,
    releasedNolics: 10, releasedLifePoints: 50};
  if (status === "SETTLED") terminal = {receiptId: `arena-settle:${ticketId}`, attemptId, settledAtMs: NOW + 1,
    percentScore: 75.5, consumedNolics: 10, consumedLifePoints: 50,
    authorCreditNolics: 8, sinkNolics: 2, royaltyReserveDeltaNolics: 0};
  return {schemaVersion: 1, uid, requestId, ticketId, attemptId, mode: "SELECTED", difficulty: "HARD",
    lessonId: "lesson", lessonVersion: 3, authorUid: "author", ratingVersion: 5,
    priceNolics: 10, lifeCost: 50, issuedAtMs: NOW, dayId: utcDayId(NOW), status,
    contentContext: {catalogId: "catalog", questId: "quest", sectionId: "section", themeId: "theme"}, terminal};
}
function path(ticket) { return `arena_entry_tickets/${ticket.ticketId}`; }
function seed(ticket) { store.reset({[path(ticket)]: structuredClone(ticket)}); }
function call(uid, data) { return functions.fetchArenaEntryTicketStatus.run({auth: {uid}, data}); }
function recoveryError(code, reason) {
  return (error) => error.code === code && error.details?.reason === reason &&
    error.message === "Arena ticket recovery could not be completed";
}

test("actual status callable requires authenticated identity before reading any document", async () => {
  const ticket = storedTicket("CANCELLED"); seed(ticket);
  for (const auth of [undefined, null, {}, {uid: ""}]) {
    await assert.rejects(functions.fetchArenaEntryTicketStatus.run({auth, data: {ticketId: ticket.ticketId}}),
      {code: "unauthenticated"});
  }
  assert.deepEqual(store.operations, []); assert.deepEqual(store.writes, []);
});

test("actual status callable rejects payload authority, unknown fields and malformed IDs without reads", async () => {
  const ticket = storedTicket(); seed(ticket);
  for (const data of [undefined, null, [], {}, {ticketId: "x"}, {ticketId: "A".repeat(64)},
    {ticketId: "a".repeat(63)}, {ticketId: 1}, {ticketId: "../" + ticket.ticketId},
    ...["uid", "ownerUid", "expectedOwnerUid", "status", "terminal", "requestId", "pageSize", "isAdmin"].map((field) =>
      ({ticketId: ticket.ticketId, [field]: field === "terminal" ? ticket.terminal : "player"}))]) {
    await assert.rejects(call("player", data), recoveryError("invalid-argument", "INVALID_ARGUMENT"));
  }
  assert.deepEqual(store.operations, []); assert.deepEqual(store.writes, []);
});

test("actual status callable does not distinguish a foreign permanent ticket from a missing one", async () => {
  const ticket = storedTicket("CANCELLED", "foreign"); seed(ticket);
  // Even a foreign corrupt terminal must not reveal its existence or corruption to this caller.
  store.documents.get(path(ticket)).terminal = {sensitive: "secret"};
  const before = structuredClone([...store.documents]);
  for (const ticketId of [ticket.ticketId, "f".repeat(64)]) {
    await assert.rejects(call("player", {ticketId}), recoveryError("not-found", "NOT_FOUND"));
  }
  assert.deepEqual(store.operations, [
    {kind: "read", path: path(ticket)}, {kind: "read", path: `arena_entry_tickets/${"f".repeat(64)}`},
  ]);
  assert.deepEqual([...store.documents], before); assert.deepEqual(store.writes, []);
});

for (const status of ["RESERVED", "CANCELLED", "SETTLED"]) {
  test(`actual status callable returns exact ${status} header/receipt without any bank or wallet reads`, async () => {
    const ticket = storedTicket(status); seed(ticket);
    const raw = store.documents.get(path(ticket));
    raw.requestHash = "secret-request"; raw.bundleDigest = "secret-bank";
    raw.questions = {malformed: "secret-answers"}; raw.history = ["secret-history"];
    raw.wallet = {nolics: 999}; raw.contentContext.futurePrivate = "secret-context";
    if (raw.terminal) { raw.terminal.scoreDigest = "secret-score"; raw.terminal.privateKey = "secret-key"; }
    const before = structuredClone([...store.documents]);
    const result = await call("player", {ticketId: ticket.ticketId});
    assert.deepEqual(result, {ticket}); assert.equal(JSON.stringify(result).includes("secret"), false);
    assert.deepEqual(store.operations, [{kind: "read", path: path(ticket)}]);
    assert.deepEqual([...store.documents], before); assert.deepEqual(store.writes, []);
    assert.equal(Object.hasOwn(result, "status"), false); // No misleading ISSUED permission envelope.
    if (status === "CANCELLED") {
      for (const forbidden of ["completed", "percentScore", "reward", "codeAnswer"]) {
        assert.equal(Object.hasOwn(result.ticket.terminal, forbidden), false);
      }
    }
  });
}

test("actual status callable maps invalid stored identity/conservation to retryable unavailable, never fabricated zero", async () => {
  for (const status of ["RESERVED", "CANCELLED", "SETTLED"]) {
    const ticket = storedTicket(status);
    for (const mutate of [
      (raw) => { raw.schemaVersion = 2; }, (raw) => { raw.requestId = "different"; },
      (raw) => { raw.attemptId = "different"; }, (raw) => { raw.priceNolics = null; },
      (raw) => { raw.lifeCost = 0; }, (raw) => { raw.terminal = status === "RESERVED" ? {} : null; },
      (raw) => { if (status === "RESERVED") raw.status = "RETRY_REGEN";
        else if (status === "CANCELLED") raw.terminal.releasedNolics = 0;
        else raw.terminal.authorCreditNolics = 9; },
    ]) {
      seed(ticket); mutate(store.documents.get(path(ticket)));
      const before = structuredClone([...store.documents]);
      await assert.rejects(call("player", {ticketId: ticket.ticketId}), recoveryError("unavailable", "UNAVAILABLE"));
      assert.deepEqual(store.operations, [{kind: "read", path: path(ticket)}]);
      assert.deepEqual([...store.documents], before); assert.deepEqual(store.writes, []);
    }
  }
});

test("actual status callable sanitizes backend failures without reading fallback collections or writing", async () => {
  const ticket = storedTicket("CANCELLED"); seed(ticket); const before = structuredClone([...store.documents]);
  store.fail = (kind, ref) => kind === "read" && ref === path(ticket) ? "private credentials/database details" : null;
  await assert.rejects(call("player", {ticketId: ticket.ticketId}), recoveryError("unavailable", "UNAVAILABLE"));
  assert.deepEqual(store.operations, [{kind: "read", path: path(ticket)}]);
  assert.deepEqual([...store.documents], before); assert.deepEqual(store.writes, []);
});
