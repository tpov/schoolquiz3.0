"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {createArenaTickets} = require("./arena-tickets");
const {createArenaTicketRecovery} = require("./arena-ticket-recovery");
const {redact} = require("./question-redaction");
const {SINGLE} = require("./_question-fixtures");
const clone = (value) => value === undefined ? undefined : structuredClone(value);
const ticketPath = (ticket) => `arena_entry_tickets/${ticket.ticketId}`;

function world() {
  const rows = new Map([
    ["users/player", {nolics: 10000, pointsNolics: 10000, lifePoints: 50000}],
    ["users/other", {nolics: 10000, pointsNolics: 10000, lifePoints: 50000}],
    ["users/author", {nolics: 0, pointsNolics: 0}],
  ]);
  const clock = {now: Date.parse("2026-09-06T12:00:00Z")};
  const snapshot = (path) => {
    const raw = clone(rows.get(path));
    return {id: path.split("/").at(-1), exists: raw !== undefined, data: () => clone(raw)};
  };
  const issuerDb = {doc: (path) => ({path}), async runTransaction(body) {
    const writes = [];
    const tx = {get: async (ref) => snapshot(ref.path),
      create: (ref, raw) => { assert.equal(rows.has(ref.path), false); writes.push([ref.path, clone(raw)]); },
      set: (ref, raw, options) => writes.push([ref.path, options?.merge ? {...clone(rows.get(ref.path)), ...clone(raw)} : clone(raw)])};
    const result = await body(tx);
    for (const [path, raw] of writes) rows.set(path, raw);
    return result;
  }};
  const service = createArenaTickets({db: issuerDb, nowMs: () => clock.now,
    chooseRandomLesson: async () => "lesson", readRegeneration: async (_, {user}) =>
      ({caughtUp: true, userPatch: {lifePoints: user.lifePoints}}), writeRegeneration: () => {},
    loadEntrySnapshot: async (_, {difficulty}) => {
      const content = {...SINGLE, difficulty}; const split = redact(JSON.stringify(content));
      const payload = difficulty === "HARD" ? split.publicPayload : JSON.stringify(content);
      return {lessonId: "lesson", lessonVersion: 3, authorUid: "author", questType: "REGULAR",
        published: true, reviewApproved: true, shelf: "arena", difficulty, ratingVersion: 5, priceNolics: 10, lifeCost: 50,
        contentContext: {catalogId: "catalog", questId: "quest", sectionId: "section", themeId: "theme"},
        questions: [{questionId: "q1", questionVersion: 2,
          publicQuestion: {id: "q1", lessonId: "lesson", version: 2, difficulty, order: 0, language: "en", payload},
          gradingKey: difficulty === "HARD" ? split.key : {kind: "FULL_CONTENT"}}]};
    }});
  const issue = async (requestId, patch = {}) => (await service.issue({uid: "player", requestId,
    mode: "SELECTED", lessonId: "lesson", difficulty: "EASY", ...patch})).ticket;
  const cancel = async (ticket) => service.cancel({uid: ticket.uid, ticketId: ticket.ticketId, requestId: `cancel-${ticket.requestId}`});
  const settle = async (ticket) => issuerDb.runTransaction(async (tx) => {
    const set = await service.readTicketSettlement(tx, {uid: ticket.uid, ticketId: ticket.ticketId, attemptId: ticket.attemptId});
    const plan = service.planTicketSettlement(set, {...set.ticket, status: "SETTLED", completed: true,
      scoreDigest: "private-score-digest", percentScore: 75.5});
    const payerPatch = service.mergeTicketPayerPatch(plan, set.user);
    const result = service.writeTicketSettlement(tx, plan);
    tx.set(set.userRef, payerPatch, {merge: true});
    return result;
  });
  const stats = {documentReads: [], queries: [], dataReads: 0};
  const hooks = {afterSnapshot: null, queryTransform: null};
  const readSnapshot = (path, fields = null) => {
    const frozen = snapshot(path); const value = frozen.data();
    const projected = fields && value ? Object.fromEntries(fields.filter((field) => Object.hasOwn(value, field))
      .map((field) => [field, clone(value[field])])) : value;
    return {id: frozen.id, exists: frozen.exists, data() { stats.dataReads++; return clone(projected); }};
  };
  function query(shape) {
    return {where: (field, op, value) => query({...shape, filters: [...shape.filters, {field, op, value}]}),
      orderBy: (field, direction) => query({...shape, order: {field, direction}}),
      select: (...fields) => query({...shape, fields}),
      startAfter: (cursor) => query({...shape, cursor}), limit: (limit) => query({...shape, limit}),
      async get() {
        assert.equal(shape.collection, "arena_entry_tickets");
        assert.deepEqual(shape.order, {field: "__name__", direction: "asc"});
        assert.ok(Number.isInteger(shape.limit) && shape.limit <= 21);
        assert.ok(shape.fields && !shape.fields.includes("questions") && !shape.fields.includes("bundleDigest"));
        stats.queries.push(clone(shape));
        const paths = [...rows.keys()].filter((path) => path.startsWith("arena_entry_tickets/") &&
          shape.filters.every(({field, op, value}) => op === "==" && rows.get(path)[field] === value) &&
          (!shape.cursor || path.split("/").at(-1) > shape.cursor)).sort();
        let docs = paths.slice(0, shape.limit).map((path) => readSnapshot(path, shape.fields));
        if (hooks.queryTransform) docs = hooks.queryTransform(docs);
        const result = {docs};
        if (hooks.afterSnapshot) { const fn = hooks.afterSnapshot; hooks.afterSnapshot = null; await fn(); }
        return result;
      }};
  }
  // Recovery gets no write methods, no transaction API and no access to users/quota/lesson banks.
  const readDb = {collection: (collection) => query({collection, filters: []}), doc: (path) => ({async get() {
    assert.match(path, /^arena_entry_tickets\/[a-f0-9]{64}$/); stats.documentReads.push(path);
    const result = readSnapshot(path);
    if (hooks.afterSnapshot) { const fn = hooks.afterSnapshot; hooks.afterSnapshot = null; await fn(); }
    return result;
  }})};
  const recovery = createArenaTicketRecovery({db: readDb});
  const get = (ticket, uid = "player") => recovery.get({uid, payload: {ticketId: ticket.ticketId}});
  const status = (ticket, uid = "player") => recovery.status({uid, payload: {ticketId: ticket.ticketId}});
  const list = (payload = {}, uid = "player") => recovery.list({uid, payload});
  return {rows, clock, issue, cancel, settle, recovery, get, status, list, stats, hooks};
}
const rejects = (fn, code) => assert.rejects(fn, (error) => error.code === code);

for (const variant of ["root", "nested", "duplicate", "metadata", "private-key"]) {
  test(`old HARD ${variant} unsafe content: no public delivery or rewrite, header recovery and full cancellation work`, async () => {
    const f = world(); const ticket = await f.issue(`unsafe-${variant}`, {difficulty: "HARD"});
    const stored = f.rows.get(ticketPath(ticket)); const row = stored.questions[0];
    const content = JSON.parse(row.publicQuestion.payload);
    if (variant === "root") content.correctOptionId = "b";
    if (variant === "nested") content.options[0].privateKey = {answer: "b"};
    if (variant === "metadata") content.id = "foreign-question";
    if (variant === "private-key") row.gradingKey = null;
    row.publicQuestion.payload = variant === "duplicate" ? `{"options":[{"grading":"b"}],${row.publicQuestion.payload.slice(1)}` : JSON.stringify(content);
    const before = clone([...f.rows]);
    await rejects(() => f.get(ticket), "UNAVAILABLE");
    assert.deepEqual(clone([...f.rows]), before);
    assert.equal((await f.list()).tickets[0].ticketId, ticket.ticketId);
    await f.cancel(ticket);
    assert.equal(f.rows.get("users/player").reservedLifePoints, 0);
    assert.equal(f.rows.get("users/player").reservedNolics, 0);
    assert.equal(f.rows.get("users/player").nolics, 10000);
    assert.equal(f.rows.get(ticketPath(ticket)).questions[0].publicQuestion.payload, row.publicQuestion.payload);
  });
}

test("recovery returns the actual issued public protocol including requestId and EASY hints", async () => {
  const f = world(); const issued = await f.issue("lost-local-request"); const before = clone([...f.rows]);
  const {requestHash: omitted, ...expected} = issued;
  assert.equal(typeof omitted, "string");
  assert.deepEqual(await f.get(issued), {status: "ISSUED", ticket: expected});
  assert.equal(JSON.parse((await f.get(issued)).ticket.questions[0].publicQuestion.payload).correctOptionId, "b");
  assert.deepEqual([...f.rows], before); assert.equal(f.stats.documentReads.length, 2);
  assert.equal(f.stats.queries.length, 0); assert.equal(f.stats.dataReads, 2);
});

test("HARD recovery preserves the already-issued redacted payload, never its private grading key", async () => {
  const f = world(); const issued = await f.issue("hard", {difficulty: "HARD"});
  const recovered = (await f.get(issued)).ticket;
  assert.deepEqual(recovered.questions, issued.questions);
  const content = JSON.parse(recovered.questions[0].publicQuestion.payload);
  assert.equal(content.type, "SingleChoiceRedacted"); assert.equal(content.correctOptionId, undefined);
  assert.equal(recovered.questions[0].gradingKey, undefined); assert.equal(recovered.bundleDigest, undefined);
});

test("confirmed-price issuance is recoverable without replaying or guessing the original ceiling", async () => {
  const f = world(); const issued = await f.issue("confirmed-request", {maxPriceNolics: 12});
  const before = clone([...f.rows]); const recovered = (await f.get(issued)).ticket;
  assert.equal(recovered.requestId, "confirmed-request"); assert.equal(recovered.priceNolics, 10);
  assert.equal(recovered.ticketId, issued.ticketId); assert.deepEqual(recovered.questions, issued.questions);
  assert.equal(Object.hasOwn(recovered, "requestHash"), false);
  assert.equal(Object.hasOwn(recovered, "maxPriceNolics"), false);
  assert.deepEqual([...f.rows], before);
});

test("strict projection drops future private fields at every typed stored-object boundary", async () => {
  const f = world(); const issued = await f.issue("future", {difficulty: "HARD"}); const raw = f.rows.get(ticketPath(issued));
  raw.futurePrivate = "secret-root"; raw.contentContext.futurePrivate = "secret-context";
  raw.questions[0].futurePrivate = "secret-wrapper"; raw.questions[0].publicQuestion.futurePrivate = "secret-question";
  const recovered = await f.get(issued); const wire = JSON.stringify(recovered);
  for (const secret of ["secret-root", "secret-context", "secret-wrapper", "secret-question", "gradingKey", "bundleDigest", "requestHash"]) {
    assert.equal(wire.includes(secret), false, secret);
  }
  assert.deepEqual(recovered.ticket.questions, issued.questions);
});

test("get rejects cross-account access like a missing ticket and never reads a wallet", async () => {
  const f = world(); const issued = await f.issue("private");
  await rejects(() => f.get(issued, "other"), "NOT_FOUND");
  await rejects(() => f.get({ticketId: "0".repeat(64)}), "NOT_FOUND");
  assert.equal(f.stats.documentReads.length, 2); assert.equal(f.stats.queries.length, 0);
});

test("payload authority, unknown fields, invalid IDs and invalid page sizes fail before reads", async () => {
  const f = world(); const valid = "a".repeat(64);
  for (const payload of [{uid: "other"}, {ownerUid: "other"}, {status: "SETTLED"}, {mode: "RANDOM"}, {unexpected: true},
    {pageSize: 0}, {pageSize: 21}, {pageSize: -1}, {pageSize: 1.5}, {pageSize: "2"}, {pageSize: null},
    {afterTicketId: "a/b"}, {afterTicketId: "a".repeat(63)}, {afterTicketId: "A".repeat(64)}, {afterTicketId: null}]) {
    await rejects(() => f.list(payload), "INVALID_ARGUMENT");
  }
  for (const payload of [{ticketId: valid, uid: "other"}, {ticketId: valid, requestId: "new"},
    {ticketId: "../users/player"}, {ticketId: "a".repeat(65)}, {}, null, []]) {
    await rejects(() => f.recovery.get({uid: "player", payload}), "INVALID_ARGUMENT");
  }
  await rejects(() => f.list({}, ""), "UNAUTHENTICATED");
  await rejects(() => f.recovery.list({uid: "player", payload: {}, ownerUid: "other"}), "INVALID_ARGUMENT");
  assert.deepEqual(f.stats.documentReads, []); assert.deepEqual(f.stats.queries, []);
});

test("list filters owner and current RESERVED state in its query and returns no banks or balances", async () => {
  const f = world(); const a = await f.issue("held"); const b = await f.issue("cancel"); const c = await f.issue("settle");
  await f.cancel(b); await f.settle(c); await f.issue("other", {uid: "other"});
  const before = clone([...f.rows]); const result = await f.list();
  assert.deepEqual(result.tickets.map((t) => t.ticketId), [a.ticketId]);
  assert.equal(result.tickets[0].requestId, "held"); assert.equal(result.tickets[0].priceNolics, 10);
  assert.equal(result.tickets[0].lifeCost, 50); assert.equal(result.nextTicketId, null);
  for (const field of ["questions", "gradingKey", "bundleDigest", "requestHash", "contentContext", "terminal", "nolics", "lifePoints"]) {
    assert.equal(Object.hasOwn(result.tickets[0], field), false, field);
  }
  assert.deepEqual(f.stats.queries[0].filters, [{field: "uid", op: "==", value: "player"}, {field: "status", op: "==", value: "RESERVED"}]);
  assert.equal(f.stats.queries[0].limit, 21); assert.equal(f.stats.queries.length, 1);
  assert.deepEqual([...f.rows], before); assert.deepEqual(f.stats.documentReads, []);
});

test("exclusive document-ID cursor uses a bounded lookahead without skipping its extra row", async () => {
  const f = world(); const tickets = [];
  for (let i = 0; i < 7; i++) tickets.push(await f.issue(`r-${i}`, {mode: "RANDOM", lessonId: null}));
  const expected = tickets.map((t) => t.ticketId).sort(); const received = []; let afterTicketId;
  do {
    const page = await f.list({pageSize: 2, ...(afterTicketId ? {afterTicketId} : {})});
    assert.ok(page.tickets.length <= 2); received.push(...page.tickets.map((t) => t.ticketId));
    afterTicketId = page.nextTicketId;
  } while (afterTicketId);
  assert.deepEqual(received, expected); assert.equal(new Set(received).size, 7);
  assert.ok(f.stats.queries.every((q) => q.limit === 3)); assert.equal(f.stats.queries.length, 4);
  assert.ok((await f.list({pageSize: 1, afterTicketId: "0".repeat(64)})).tickets.length === 1);
  assert.deepEqual(await f.list({afterTicketId: "f".repeat(64)}), {tickets: [], nextTicketId: null});
});

test("maximum page returns twenty plus a cursor; query overflow or unstable ordering fails closed", async () => {
  const f = world();
  for (let i = 0; i < 20; i++) await f.issue(`random-${i}`, {mode: "RANDOM", lessonId: null});
  await f.issue("selected"); const page = await f.list();
  assert.equal(page.tickets.length, 20); assert.equal(page.nextTicketId, page.tickets[19].ticketId);
  assert.equal((await f.list({afterTicketId: page.nextTicketId})).tickets.length, 1);
  f.hooks.queryTransform = (docs) => [...docs, docs[0]];
  await rejects(() => f.list(), "UNAVAILABLE");
  f.hooks.queryTransform = (docs) => docs.reverse();
  await rejects(() => f.list({pageSize: 2}), "UNAVAILABLE");
});

test("cursor does not depend on a still-RESERVED cursor document and pages are not an atomic inventory", async () => {
  const f = world(); const tickets = [];
  for (let i = 0; i < 5; i++) tickets.push(await f.issue(`held-${i}`));
  const sorted = tickets.sort((a, b) => a.ticketId.localeCompare(b.ticketId));
  const first = await f.list({pageSize: 2}); assert.equal(first.nextTicketId, sorted[1].ticketId);
  await f.cancel(sorted[1]); await f.cancel(sorted[2]);
  const second = await f.list({pageSize: 2, afterTicketId: first.nextTicketId});
  assert.deepEqual(second.tickets.map((t) => t.ticketId), sorted.slice(3).map((t) => t.ticketId));
  assert.equal(second.nextTicketId, null);
});

test("terminal CANCELLED and SETTLED recovery uses frozen questions even when live source disappears", async () => {
  const f = world(); const a = await f.issue("cancelled"); const b = await f.issue("settled");
  await f.cancel(a); await f.settle(b);
  f.rows.set("lesson_placements/lesson", {shelf: "retired"}); f.rows.delete("questions/q1");
  f.rows.set("lessons/lesson", {version: 999});
  for (const [ticket, state] of [[a, "CANCELLED"], [b, "SETTLED"]]) {
    const raw = f.rows.get(ticketPath(ticket)); raw.terminal.futurePrivate = "secret-terminal";
    const recovered = await f.get(ticket);
    assert.equal(recovered.ticket.status, state); assert.deepEqual(recovered.ticket.questions, ticket.questions);
    assert.equal(recovered.ticket.terminal.futurePrivate, undefined);
    assert.equal(recovered.ticket.terminal.scoreDigest, undefined);
  }
  assert.equal((await f.get(b)).ticket.terminal.percentScore, 75.5);
  assert.deepEqual(await f.list(), {tickets: [], nextTicketId: null});
});

test("one get snapshot stays internally consistent when cancellation races with its read", async () => {
  const f = world(); const ticket = await f.issue("race");
  f.hooks.afterSnapshot = () => f.cancel(ticket);
  const first = await f.get(ticket);
  assert.equal(first.ticket.status, "RESERVED"); assert.equal(first.ticket.terminal, null);
  assert.equal(f.stats.documentReads.length, 1); assert.equal(f.stats.dataReads, 1);
  const second = await f.get(ticket);
  assert.equal(second.ticket.status, "CANCELLED"); assert.ok(second.ticket.terminal.cancelledAtMs);
  assert.equal(f.stats.documentReads.length, 2); assert.equal(f.stats.dataReads, 2);
});

test("one list query snapshot stays internally consistent when cancellation races with selection", async () => {
  const f = world(); const ticket = await f.issue("list-race");
  f.hooks.afterSnapshot = () => f.cancel(ticket);
  const first = await f.list(); assert.equal(first.tickets[0].status, "RESERVED");
  assert.equal(f.stats.queries.length, 1); assert.equal(f.stats.dataReads, 1);
  assert.deepEqual(await f.list(), {tickets: [], nextTicketId: null});
});

test("corrupt required headers fail UNAVAILABLE instead of defaulting amounts or pretending empty", async () => {
  const f = world(); const ticket = await f.issue("corrupt"); const original = clone(f.rows.get(ticketPath(ticket)));
  for (const patch of [{schemaVersion: 2}, {requestId: ""}, {attemptId: "forged"}, {ticketId: "a".repeat(64)},
    {priceNolics: null}, {priceNolics: 0}, {priceNolics: 101}, {lifeCost: undefined}, {lifeCost: -1},
    {issuedAtMs: Number.MAX_SAFE_INTEGER}, {dayId: "2026-01-01"}, {lessonVersion: 0}, {ratingVersion: -1},
    {difficulty: "IMPOSSIBLE"}, {terminal: undefined}, {contentContext: {catalogId: "only"}}]) {
    f.rows.set(ticketPath(ticket), {...clone(original), ...patch});
    await rejects(() => f.get(ticket), "UNAVAILABLE"); await rejects(() => f.list(), "UNAVAILABLE");
  }
});

test("corrupt question or terminal structure is unavailable, never a fabricated result", async () => {
  const f = world(); const ticket = await f.issue("bad-question"); const original = clone(f.rows.get(ticketPath(ticket)));
  for (const mutate of [
    (raw) => { raw.questions = []; },
    (raw) => { raw.questions = Array(21).fill(raw.questions[0]); },
    (raw) => { raw.questions.push(clone(raw.questions[0])); },
    (raw) => { raw.questions[0].publicQuestion.version = 999; },
    (raw) => { raw.questions[0].publicQuestion.payload = "broken"; },
    (raw) => { raw.questions[0].publicQuestion.payload = "null"; },
    (raw) => { raw.questions[0].publicQuestion.order = 2; },
    (raw) => { raw.status = "SETTLED"; raw.terminal = {}; },
    (raw) => { raw.status = "CANCELLED"; raw.terminal = {receiptId: "forged"}; },
  ]) {
    const raw = clone(original); mutate(raw); f.rows.set(ticketPath(ticket), raw);
    await rejects(() => f.get(ticket), "UNAVAILABLE");
  }
});

test("recovery never restores random quota, releases holds, creates an intent or starts a transaction", async () => {
  const f = world(); const ticket = await f.issue("random", {mode: "RANDOM", lessonId: null});
  const before = clone([...f.rows]);
  for (let i = 0; i < 3; i++) { await f.list(); await f.get(ticket); }
  assert.deepEqual([...f.rows], before);
  assert.equal((await f.get(ticket)).ticket.priceNolics, 0);
  assert.ok([...f.rows.keys()].some((path) => path.startsWith("arena_ticket_quotas/")));
});

test("store failures are retryable UNAVAILABLE, not an empty list or a missing ticket", async () => {
  const f = world(); const ticket = await f.issue("backend");
  f.hooks.afterSnapshot = () => { throw new Error("sensitive backend detail"); };
  await rejects(() => f.get(ticket), "UNAVAILABLE");
  f.hooks.afterSnapshot = () => { throw new Error("sensitive backend detail"); };
  await rejects(() => f.list(), "UNAVAILABLE");
});

test("status projects actual issued, cancelled and settled headers/receipts, never launch permission or a balance", async () => {
  const f = world(); const ticket = await f.issue("status-lifecycle");
  const {questions: omittedQuestions, requestHash: omittedHash, ...expected} = ticket;
  assert.ok(omittedQuestions.length); assert.equal(typeof omittedHash, "string");
  const before = clone([...f.rows]);
  assert.deepEqual(await f.status(ticket), {ticket: expected});
  assert.deepEqual([...f.rows], before);
  const cancellation = await f.cancel(ticket);
  const afterCancellation = clone([...f.rows]);
  assert.deepEqual(await f.status(ticket), {ticket: {...expected, status: "CANCELLED", terminal: cancellation.terminal}});
  assert.deepEqual([...f.rows], afterCancellation);
  const completed = await f.issue("status-settled"); await f.settle(completed);
  const recovered = await f.get(completed);
  const {questions: ignored, ...settledHeader} = recovered.ticket;
  assert.ok(ignored.length);
  const afterSettlement = clone([...f.rows]);
  assert.deepEqual(await f.status(completed), {ticket: settledHeader});
  assert.deepEqual([...f.rows], afterSettlement);
  assert.equal(settledHeader.terminal.percentScore, 75.5);
  assert.equal(Object.hasOwn(settledHeader.terminal, "scoreDigest"), false);
});

for (const variant of ["missing", "empty", "malformed", "unsafe-hard", "missing-key"]) {
  test(`status keeps a real cancellation readable with a ${variant} frozen bank`, async () => {
    const f = world(); const ticket = await f.issue(`status-bank-${variant}`, {difficulty: "HARD"});
    const raw = f.rows.get(ticketPath(ticket));
    if (variant === "missing") delete raw.questions;
    if (variant === "empty") raw.questions = [];
    if (variant === "malformed") raw.questions[0].publicQuestion.payload = "not-json";
    if (variant === "unsafe-hard") {
      const payload = JSON.parse(raw.questions[0].publicQuestion.payload); payload.correctOptionId = "secret";
      raw.questions[0].publicQuestion.payload = JSON.stringify(payload);
    }
    if (variant === "missing-key") delete raw.questions[0].gradingKey;
    // Neither a malformed bank nor current content is needed to read or cancel this hold.
    assert.equal((await f.status(ticket)).ticket.status, "RESERVED");
    const cancelled = await f.cancel(ticket);
    f.rows.set("lesson_placements/lesson", {shelf: "retired"}); f.rows.delete("questions/q1");
    f.rows.set("lessons/lesson", {version: 999});
    const before = clone([...f.rows]);
    await rejects(() => f.get(ticket), "UNAVAILABLE");
    const result = await f.status(ticket);
    assert.equal(result.ticket.status, "CANCELLED"); assert.deepEqual(result.ticket.terminal, cancelled.terminal);
    assert.equal(Object.hasOwn(result.ticket, "questions"), false);
    assert.deepEqual([...f.rows], before);
  });
}

test("status drops unknown/private fields from header and both terminal variants without reading their bank", async () => {
  const f = world(); const cancelled = await f.issue("status-private-cancel"); await f.cancel(cancelled);
  const settled = await f.issue("status-private-settle"); await f.settle(settled);
  for (const ticket of [cancelled, settled]) {
    const raw = f.rows.get(ticketPath(ticket));
    raw.futurePrivate = "secret-root"; raw.contentContext.futurePrivate = "secret-context";
    raw.terminal.futurePrivate = "secret-terminal"; raw.terminal.uid = "foreign";
    raw.terminal.questions = [{key: "secret-question"}]; raw.questions = null;
    const before = clone([...f.rows]); const result = await f.status(ticket);
    const wire = JSON.stringify(result);
    for (const forbidden of ["secret", "foreign", "questions", "gradingKey", "bundleDigest", "scoreDigest", "requestHash"]) {
      assert.equal(wire.includes(forbidden), false, forbidden);
    }
    for (const forbidden of ["completed", "reward", "codeAnswer", "balance", "wallet", "history"]) {
      assert.equal(Object.hasOwn(result.ticket, forbidden), false, forbidden);
      assert.equal(Object.hasOwn(result.ticket.terminal, forbidden), false, forbidden);
    }
    assert.deepEqual([...f.rows], before);
  }
});

test("status is owner-bound, strict and read-only: caller authority and malformed IDs are refused before reads", async () => {
  const f = world(); const ticket = await f.issue("status-owned"); const before = clone([...f.rows]);
  const valid = {uid: "player", payload: {ticketId: ticket.ticketId}};
  for (const input of [null, [], {...valid, ownerUid: "other"}, {...valid, admin: true},
    {...valid, payload: null}, {...valid, payload: []}, {...valid, payload: {}},
    ...["uid", "ownerUid", "expectedOwnerUid", "status", "terminal", "pageSize", "questions"].map((field) =>
      ({...valid, payload: {...valid.payload, [field]: "forged"}})),
    ...[null, 0, "", "a".repeat(63), "A".repeat(64), "../" + "a".repeat(64)].map((ticketId) =>
      ({...valid, payload: {ticketId}}))]) {
    await rejects(() => f.recovery.status(input), "INVALID_ARGUMENT");
  }
  for (const uid of [null, "", "foreign/uid"]) await rejects(() => f.recovery.status({...valid, uid}), "UNAUTHENTICATED");
  assert.equal(f.stats.documentReads.length, 0);
  await rejects(() => f.status(ticket, "other"), "NOT_FOUND");
  await rejects(() => f.status({ticketId: "f".repeat(64)}), "NOT_FOUND");
  assert.equal(f.stats.documentReads.length, 2); assert.equal(f.stats.queries.length, 0);
  assert.deepEqual([...f.rows], before);
});

test("status rejects corrupt required header identity and amounts even if its terminal looks valid", async () => {
  const f = world(); const ticket = await f.issue("status-header"); await f.cancel(ticket);
  const original = clone(f.rows.get(ticketPath(ticket)));
  for (const patch of [{schemaVersion: 2}, {requestId: "another-request"}, {attemptId: "forged"},
    {ticketId: "a".repeat(64)}, {mode: "FREE"}, {difficulty: "IMPOSSIBLE"}, {lessonId: ""}, {lessonVersion: 0},
    {priceNolics: null}, {priceNolics: 0}, {priceNolics: 101}, {priceNolics: 10.5}, {lifeCost: undefined},
    {lifeCost: Number.MAX_SAFE_INTEGER + 1}, {lifeCost: -1}, {issuedAtMs: -1}, {issuedAtMs: Infinity},
    {dayId: "wrong-day"}, {ratingVersion: -1}, {authorUid: ""}, {contentContext: {catalogId: "only"}}]) {
    f.rows.set(ticketPath(ticket), {...clone(original), ...patch});
    const before = clone([...f.rows]); await rejects(() => f.status(ticket), "UNAVAILABLE");
    assert.deepEqual([...f.rows], before);
  }
});

test("status requires a permanent cancellation receipt with exact identity and full hold conservation", async () => {
  const f = world(); const ticket = await f.issue("status-cancel-proof"); await f.cancel(ticket);
  const original = clone(f.rows.get(ticketPath(ticket)));
  for (const terminal of [null, undefined, {}, [], {...original.terminal, receiptId: "arena-cancel:" + "f".repeat(64)},
    {...original.terminal, receiptId: `arena-settle:${ticket.ticketId}`},
    {...original.terminal, cancelledAtMs: original.issuedAtMs - 1}, {...original.terminal, cancelledAtMs: NaN},
    {...original.terminal, releasedNolics: "10"}, {...original.terminal, releasedNolics: 0},
    {...original.terminal, releasedLifePoints: 0}, {...original.terminal, releasedLifePoints: null}]) {
    f.rows.set(ticketPath(ticket), {...clone(original), terminal});
    const before = clone([...f.rows]); await rejects(() => f.status(ticket), "UNAVAILABLE");
    assert.deepEqual([...f.rows], before);
  }
});

test("status requires a real settled receipt with finite score, exact attempt and conserved spending", async () => {
  const f = world(); const ticket = await f.issue("status-settle-proof"); await f.settle(ticket);
  const original = clone(f.rows.get(ticketPath(ticket)));
  for (const terminal of [null, undefined, {}, [], {...original.terminal, receiptId: `arena-cancel:${ticket.ticketId}`},
    {...original.terminal, attemptId: "foreign-attempt"}, {...original.terminal, settledAtMs: original.issuedAtMs - 1},
    ...[undefined, null, NaN, Infinity, "75", -1, 101].map((percentScore) => ({...original.terminal, percentScore})),
    ...[{consumedNolics: 0}, {consumedLifePoints: 0}, {authorCreditNolics: -1}, {sinkNolics: 1.5},
      {royaltyReserveDeltaNolics: 0.5}, {authorCreditNolics: 9}, {royaltyReserveDeltaNolics: Number.MAX_SAFE_INTEGER + 1}]
      .map((patch) => ({...original.terminal, ...patch}))]) {
    f.rows.set(ticketPath(ticket), {...clone(original), terminal});
    const before = clone([...f.rows]); await rejects(() => f.status(ticket), "UNAVAILABLE");
    assert.deepEqual([...f.rows], before);
  }
});

test("status never fabricates a RESERVED terminal receipt or downgrades an unknown state", async () => {
  const f = world(); const ticket = await f.issue("status-reserved-proof"); const original = clone(f.rows.get(ticketPath(ticket)));
  for (const patch of [{terminal: undefined}, {terminal: {}}, {status: "RETRY_REGEN"}, {status: "CANCELLED"}, {status: "SETTLED"}]) {
    f.rows.set(ticketPath(ticket), {...clone(original), ...patch});
    await rejects(() => f.status(ticket), "UNAVAILABLE");
  }
});

test("status for RANDOM preserves zero payments and spent quota, including actual settle/cancel receipts", async () => {
  const f = world(); const cancelled = await f.issue("status-random-cancel", {mode: "RANDOM", lessonId: null});
  await f.cancel(cancelled);
  const settled = await f.issue("status-random-settle", {mode: "RANDOM", lessonId: null}); await f.settle(settled);
  const before = clone([...f.rows]);
  assert.equal((await f.status(cancelled)).ticket.terminal.releasedNolics, 0);
  const receipt = (await f.status(settled)).ticket.terminal;
  assert.equal(receipt.consumedNolics, 0); assert.equal(receipt.authorCreditNolics, 0);
  assert.equal(receipt.sinkNolics, 0); assert.equal(receipt.royaltyReserveDeltaNolics, 0);
  assert.deepEqual([...f.rows], before);
  assert.ok([...f.rows.keys()].some((path) => path.startsWith("arena_ticket_quotas/")));
  // A zero-sum forged transfer from fractional reserves must not masquerade as a free random run.
  const raw = f.rows.get(ticketPath(settled)); raw.terminal.authorCreditNolics = 1; raw.terminal.royaltyReserveDeltaNolics = -1;
  await rejects(() => f.status(settled), "UNAVAILABLE");
});

for (const transition of ["cancel", "settle"]) {
  test(`status uses one internally consistent snapshot during a racing ${transition}`, async () => {
    const f = world(); const ticket = await f.issue(`status-race-${transition}`);
    f.hooks.afterSnapshot = () => f[transition](ticket);
    const first = await f.status(ticket);
    assert.equal(first.ticket.status, "RESERVED"); assert.equal(first.ticket.terminal, null);
    assert.equal(f.stats.documentReads.length, 1); assert.equal(f.stats.dataReads, 1);
    const second = await f.status(ticket);
    assert.equal(second.ticket.status, transition === "cancel" ? "CANCELLED" : "SETTLED");
    assert.ok(second.ticket.terminal.receiptId);
    assert.equal(f.stats.documentReads.length, 2); assert.equal(f.stats.dataReads, 2);
  });
}

test("status backend failure stays retryable UNAVAILABLE and never returns a synthetic terminal state", async () => {
  const f = world(); const ticket = await f.issue("status-failure"); const before = clone([...f.rows]);
  f.hooks.afterSnapshot = () => { throw new Error("sensitive backend detail"); };
  await rejects(() => f.status(ticket), "UNAVAILABLE");
  assert.deepEqual([...f.rows], before);
});
