"use strict";

const assert = require("node:assert/strict");
const {attemptDocumentId} = require("../functions/arena-tickets");
const {ArenaE2eEvidenceError} = require("./arena-e2e-evidence");
const {
  MAX_OWNED_TICKETS,
  baselineManifest,
  captureBaseline,
  discoverFreshTicket,
  parseBaselineManifest,
} = require("./arena-e2e-ticket-discovery");

const ownerUid = "retained-player";
const context = {
  runId: "arena-e2b-run-001",
  ownerUid,
  lessonId: "lesson-target",
  lessonVersion: 7,
  mode: "SELECTED",
  difficulty: "HARD",
  frozenPriceNolics: 1,
};

function ticket(ticketId, patch = {}) {
  return {
    schemaVersion: 1,
    uid: ownerUid,
    ticketId,
    attemptId: attemptDocumentId(ticketId),
    requestId: `request-${ticketId.slice(0, 8)}`,
    mode: "SELECTED",
    difficulty: "HARD",
    lessonId: context.lessonId,
    lessonVersion: context.lessonVersion,
    authorUid: "independent-author",
    priceNolics: 1,
    lifeCost: 50,
    issuedAtMs: 1,
    status: "RESERVED",
    terminal: null,
    ...patch,
  };
}

function fakeDb(rows) {
  const calls = [];
  return {
    calls,
    collection(path) {
      assert.equal(path, "arena_entry_tickets");
      return {
        where(field, operator, value) {
          assert.equal(field, "uid"); assert.equal(operator, "=="); assert.equal(value, ownerUid);
          return {
            limit(maximum) {
              return {
                get: async () => {
                  calls.push({kind: "query", path, field, operator, value, maximum});
                  return {docs: rows.map((row) => ({id: row.id, data: () => row.data}))};
                },
              };
            },
          };
        },
      };
    },
  };
}

function rejectsCode(action, code) {
  return assert.rejects(action, (error) => error instanceof ArenaE2eEvidenceError && error.code === code);
}

async function testBaselineCapturesEveryOwnedIdWithBoundedReadOnlyQuery() {
  const oldA = "a".repeat(64); const oldC = "c".repeat(64);
  const db = fakeDb([{id: oldC, data: ticket(oldC)}, {id: oldA, data: ticket(oldA)}]);
  const manifest = await captureBaseline(db, context, {now: () => 42});
  assert.equal(manifest.status, "BASELINE");
  assert.equal(manifest.documentWrites, 0);
  assert.deepEqual(manifest.ownedTicketIds, [oldA, oldC]);
  assert.equal(manifest.capturedAtMs, 42);
  assert.deepEqual(db.calls, [{kind: "query", path: "arena_entry_tickets", field: "uid", operator: "==", value: ownerUid,
    maximum: MAX_OWNED_TICKETS + 1}]);
}

async function testDiscoveryUsesBaselineSetNotTicketRecency() {
  const historicalMatchingId = "c".repeat(64); const freshId = "b".repeat(64);
  const baseline = await captureBaseline(fakeDb([{id: historicalMatchingId, data: ticket(historicalMatchingId, {issuedAtMs: 999_999})}]), context,
    {now: () => 10});
  const db = fakeDb([
    {id: historicalMatchingId, data: ticket(historicalMatchingId, {issuedAtMs: 999_999})},
    {id: freshId, data: ticket(freshId, {issuedAtMs: 1})},
  ]);
  const result = await discoverFreshTicket(db, parseBaselineManifest(baseline));
  assert.equal(result.status, "CANDIDATE");
  assert.equal(result.documentWrites, 0);
  assert.equal(result.ticketId, freshId);
  assert.equal(result.attemptId, attemptDocumentId(freshId));
  assert.deepEqual(result.matchingFreshTicketIds, [freshId]);
  assert.deepEqual(result.freshOwnedTicketIds, [freshId]);
  assert.equal(db.calls.length, 1);
}

async function testDiscoveryRejectsNoOrAmbiguousFreshMatch() {
  const oldId = "a".repeat(64); const freshEasyId = "b".repeat(64);
  const baseline = await captureBaseline(fakeDb([{id: oldId, data: ticket(oldId)}]), context, {now: () => 10});
  await rejectsCode(() => discoverFreshTicket(fakeDb([
    {id: oldId, data: ticket(oldId)},
    {id: freshEasyId, data: ticket(freshEasyId, {difficulty: "EASY"})},
  ]), parseBaselineManifest(baseline)), "FRESH_TICKET_NOT_FOUND");

  const freshB = "b".repeat(64); const freshC = "c".repeat(64);
  await rejectsCode(() => discoverFreshTicket(fakeDb([
    {id: oldId, data: ticket(oldId)},
    {id: freshB, data: ticket(freshB)},
    {id: freshC, data: ticket(freshC)},
  ]), parseBaselineManifest(baseline)), "FRESH_TICKET_AMBIGUITY");
}

async function testDiscoveryRequiresEveryPreparedEntryField() {
  const oldId = "a".repeat(64);
  const baseline = await captureBaseline(fakeDb([{id: oldId, data: ticket(oldId)}]), context, {now: () => 10});
  const mismatches = [
    {uid: "other-owner"},
    {lessonId: "other-lesson"},
    {lessonVersion: context.lessonVersion + 1},
    {mode: "RANDOM", priceNolics: 0},
    {difficulty: "EASY"},
    {priceNolics: 2},
  ];
  const rows = [{id: oldId, data: ticket(oldId)}, ...mismatches.map((patch, index) => {
    const id = String.fromCharCode("b".charCodeAt(0) + index).repeat(64);
    return {id, data: ticket(id, patch)};
  })];
  await rejectsCode(() => discoverFreshTicket(fakeDb(rows), parseBaselineManifest(baseline)), "FRESH_TICKET_NOT_FOUND");
}

async function testDiscoveryRejectsBaselineDriftAndMalformedMatchingCandidate() {
  const oldId = "a".repeat(64); const freshId = "b".repeat(64);
  const baseline = await captureBaseline(fakeDb([{id: oldId, data: ticket(oldId)}]), context, {now: () => 10});
  await rejectsCode(() => discoverFreshTicket(fakeDb([{id: freshId, data: ticket(freshId)}]), parseBaselineManifest(baseline)),
    "BASELINE_DRIFT");
  await rejectsCode(() => discoverFreshTicket(fakeDb([
    {id: oldId, data: ticket(oldId)},
    {id: freshId, data: ticket(freshId, {attemptId: "foreign-attempt"})},
  ]), parseBaselineManifest(baseline)), "TICKET_IDENTITY_MISMATCH");
}

function testManifestCannotLoseOrReorderBaselineIds() {
  const oldA = "a".repeat(64); const oldB = "b".repeat(64);
  const manifest = baselineManifest(context, [oldA, oldB], 10);
  const missing = {...manifest, ownedTicketCount: 1, ownedTicketIds: [oldA]};
  assert.throws(() => parseBaselineManifest(missing), (error) => error instanceof ArenaE2eEvidenceError && error.code === "INVALID_BASELINE");
  const reordered = {...manifest, ownedTicketIds: [oldB, oldA]};
  assert.throws(() => parseBaselineManifest(reordered), (error) => error instanceof ArenaE2eEvidenceError && error.code === "INVALID_BASELINE");
  const tampered = {...manifest, lessonId: "other"};
  assert.throws(() => parseBaselineManifest(tampered), (error) => error instanceof ArenaE2eEvidenceError && error.code === "INVALID_BASELINE");
  const retimed = {...manifest, capturedAtMs: 11};
  assert.throws(() => parseBaselineManifest(retimed), (error) => error instanceof ArenaE2eEvidenceError && error.code === "INVALID_BASELINE");
}

(async () => {
  await testBaselineCapturesEveryOwnedIdWithBoundedReadOnlyQuery();
  await testDiscoveryUsesBaselineSetNotTicketRecency();
  await testDiscoveryRejectsNoOrAmbiguousFreshMatch();
  await testDiscoveryRequiresEveryPreparedEntryField();
  await testDiscoveryRejectsBaselineDriftAndMalformedMatchingCandidate();
  testManifestCannotLoseOrReorderBaselineIds();
  console.log("arena-e2e-ticket-discovery.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
