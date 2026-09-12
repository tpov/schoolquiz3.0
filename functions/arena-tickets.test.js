"use strict";

const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const test = require("node:test");
const {existsSync} = require("node:fs");
const {join} = require("node:path");
const adjacentPolicy = join(__dirname, "arena-policy.js");
const policy = require(existsSync(adjacentPolicy) ? adjacentPolicy : join(__dirname, "../../arena/arena-policy.js"));
const {createArenaTickets, ticketDocumentId, LEDGER_PATH} = require("./arena-tickets");
const clone = (value) => value == null ? value : JSON.parse(JSON.stringify(value));

// Strict optimistic transaction double: snapshot conflicts retry, reads after writes refuse,
// create collisions refuse, and injected failures roll every document back together.
function fakeDb(initial = {}) {
  const rows = new Map(); let version = 0; let retries = 0;
  const put = (path, value) => rows.set(path, {version: ++version, value: clone(value)});
  Object.entries(initial).forEach(([path, value]) => put(path, value));
  const db = {put, doc: (path) => ({path}), read: (path) => clone(rows.get(path)?.value),
    paths: (prefix) => [...rows.keys()].filter((path) => path.startsWith(prefix)),
    commits: [], failNextCommit: false, beforeCommitOnce: null,
    get retries() { return retries; },
    async runTransaction(body) {
      for (let i = 0; i < 100; i++) {
        const reads = new Map(); const writes = [];
        const tx = {
          async get(ref) {
            assert.equal(writes.length, 0, "Firestore read after write");
            const row = rows.get(ref.path); reads.set(ref.path, row?.version || 0);
            return {exists: Boolean(row), data: () => clone(row?.value)};
          },
          create(ref, value) { writes.push({path: ref.path, value: clone(value), create: true}); },
          set(ref, value, options) { writes.push({path: ref.path, value: clone(value), merge: options?.merge === true}); },
        };
        const result = await body(tx);
        if (db.beforeCommitOnce) { const fn = db.beforeCommitOnce; db.beforeCommitOnce = null; await fn(db); }
        if ([...reads].some(([path, expected]) => (rows.get(path)?.version || 0) !== expected)) { retries++; continue; }
        if (db.failNextCommit) { db.failNextCommit = false; throw new Error("injected commit failure"); }
        const created = new Set();
        for (const write of writes) {
          if (write.create && (rows.has(write.path) || created.has(write.path))) throw new Error("ALREADY_EXISTS");
          if (write.create) created.add(write.path);
        }
        writes.forEach((write) => put(write.path, write.merge ? {...rows.get(write.path)?.value, ...write.value} : write.value));
        db.commits.push(writes); return result;
      }
      throw new Error("retry limit");
    },
  };
  return db;
}

const DAY = 24 * 60 * 60 * 1000;
const START = Date.parse("2026-09-06T12:00:00Z");
function snapshot(patch = {}) {
  return {lessonId: "lesson", authorUid: "author", questType: "REGULAR", shelf: "arena", published: true,
    reviewApproved: true, lessonVersion: 3, difficulty: "EASY", priceNolics: 10, ratingVersion: 5, lifeCost: 50,
    questions: [{questionId: "q1", questionVersion: 2, publicQuestion: {prompt: "2+2?", choices: ["3", "4"]},
      gradingKey: {correctChoice: 1}}], ...patch};
}
function setup({user = {}, source = {}, author = {}, regeneration = null} = {}) {
  const db = fakeDb({"users/player": {nolics: 1000, pointsNolics: 1000, lifePoints: 5000, ...user},
    "users/author": {nolics: 0, pointsNolics: 0, ...author}, "server_lessons/lesson": snapshot(source),
    "server_random/current": {lessonId: "lesson"}});
  const clock = {now: START};
  const stats = {randomCalls: 0, regenReads: 0, regenWrites: 0, entryContexts: [],
    randomContexts: [], loaderContexts: [], regenContexts: []};
  const service = createArenaTickets({db, policy, nowMs: () => clock.now,
    createEntryReadContext: () => {
      const context = Object.freeze({call: stats.entryContexts.length + 1});
      stats.entryContexts.push(context); return context;
    },
    chooseRandomLesson: async (tx, {entryReadContext}) => {
      stats.randomCalls++; stats.randomContexts.push(entryReadContext);
      return (await tx.get(db.doc("server_random/current"))).data().lessonId;
    },
    loadEntrySnapshot: async (tx, {lessonId, entryReadContext}) => {
      stats.loaderContexts.push(entryReadContext);
      return (await tx.get(db.doc(`server_lessons/${lessonId}`))).data();
    },
    readRegeneration: async (tx, {uid, user: oldUser, nowMs, entryReadContext}) => {
      stats.regenReads++;
      stats.regenContexts.push(entryReadContext);
      const stored = (await tx.get(db.doc(`server_regen/${uid}`))).data();
      if (regeneration) return regeneration({user: oldUser, nowMs, stored});
      return {caughtUp: true, userPatch: {lifePoints: oldUser.lifePoints, lifePointsUpdatedAtMs: nowMs}, uid};
    },
    writeRegeneration: (tx, plan) => { stats.regenWrites++; tx.set(db.doc(`server_regen/${plan.uid || "player"}`), {checkpoint: plan.userPatch.lifePointsUpdatedAtMs}); },
  });
  const issue = (requestId = "issue", patch = {}) => service.issue({uid: "player", requestId, mode: "SELECTED", lessonId: "lesson", difficulty: "EASY", ...patch});
  const cancel = (ticket, requestId = "cancel") => service.cancel({uid: "player", ticketId: ticket.ticketId, requestId});
  const score = (ticket, patch = {}) => ({status: "SETTLED", completed: true, uid: ticket.uid,
    ticketId: ticket.ticketId, attemptId: ticket.attemptId, lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion,
    difficulty: ticket.difficulty, bundleDigest: db.read(`arena_entry_tickets/${ticket.ticketId}`).bundleDigest,
    scoreDigest: "server-score-digest", percentScore: 75, ...patch});
  // Represents the unified E2 handler, not a public ticket-only settle endpoint. All reads first;
  // server scoring uses the PRIVATE frozen bundle; result receipt + wallet + ticket share one tx.
  async function settle(ticket, {scoring = {}, failAfterWrites = false, playerReward = 0} = {}) {
    return db.runTransaction(async (tx) => {
      const readset = await service.readTicketSettlement(tx, {uid: "player", ticketId: ticket.ticketId, attemptId: ticket.attemptId});
      const receiptRef = db.doc(`result_events/${ticket.attemptId}`);
      const receipt = (await tx.get(receiptRef)).data();
      const plan = service.planTicketSettlement(readset, score(ticket, scoring));
      if (plan.replay) { assert.ok(receipt, "settled ticket must have same-transaction E2 receipt"); return plan.terminal; }
      assert.equal(readset.ticket.questions[0].gradingKey.correctChoice, 1);
      const balance = readset.user.pointsNolics + playerReward;
      const rootComputedUser = {...readset.user, pointsNolics: balance, nolics: balance};
      const payerPatch = service.mergeTicketPayerPatch(plan, rootComputedUser);
      const terminal = service.writeTicketSettlement(tx, plan);
      tx.set(readset.userRef, {...rootComputedUser, ...payerPatch}, {merge: true});
      tx.create(receiptRef, {status: "SETTLED", completed: true, uid: "player", attemptId: ticket.attemptId,
        percentScore: 75, ticketReceiptId: terminal.receiptId, lifePointsSpent: ticket.lifeCost});
      if (failAfterWrites) throw new Error("injected scorer adapter failure");
      return terminal;
    });
  }
  return {db, service, clock, stats, issue, cancel, settle, score};
}

test("issue reserves available balances and returns immutable redacted version, price and attempt", async () => {
  const f = setup();
  const {ticket} = await f.issue("one", {priceNolics: 0, lifeCost: 0, correctChoice: 1});
  assert.equal(ticket.priceNolics, 10); assert.equal(ticket.lifeCost, 50); assert.equal(ticket.lessonVersion, 3);
  assert.equal(ticket.questions[0].gradingKey, undefined);
  assert.equal(ticket.bundleDigest, undefined);
  assert.equal(f.db.read(`arena_entry_tickets/${ticket.ticketId}`).questions[0].gradingKey.correctChoice, 1);
  assert.deepEqual(Object.fromEntries(["pointsNolics", "nolics", "lifePoints", "reservedNolics", "reservedLifePoints", "arenaSelectedHeldCount"]
    .map((key) => [key, f.db.read("users/player")[key]])),
  {pointsNolics: 990, nolics: 990, lifePoints: 4950, reservedNolics: 10, reservedLifePoints: 50, arenaSelectedHeldCount: 1});
  assert.equal(f.db.paths(`${LEDGER_PATH}/`).length, 2);
  f.db.put("server_lessons/lesson", snapshot({lessonVersion: 8, priceNolics: 100, questions: []}));
  assert.deepEqual((await f.issue("one")).ticket, ticket);
  assert.equal(f.stats.regenReads, 1);
  await assert.rejects(() => f.issue("one", {difficulty: "HARD"}), (e) => e.code === "REQUEST_REUSE");
});

test("one callback-local entry context is shared by selected/random loading and preparation regeneration", async () => {
  const selected = setup();
  await selected.issue("selected-context");
  assert.equal(selected.stats.entryContexts.length, 1);
  assert.strictEqual(selected.stats.loaderContexts[0], selected.stats.entryContexts[0]);
  assert.strictEqual(selected.stats.regenContexts[0], selected.stats.entryContexts[0]);

  const random = setup();
  await random.service.issue({uid: "player", requestId: "random-context", mode: "RANDOM",
    lessonId: null, difficulty: "EASY"});
  assert.equal(random.stats.entryContexts.length, 1);
  assert.strictEqual(random.stats.randomContexts[0], random.stats.entryContexts[0]);
  assert.strictEqual(random.stats.loaderContexts[0], random.stats.entryContexts[0]);
  assert.strictEqual(random.stats.regenContexts[0], random.stats.entryContexts[0]);

  const cancelled = setup();
  const {ticket} = await cancelled.issue("cancel-context");
  const contextsBeforeCancel = cancelled.stats.entryContexts.length;
  await cancelled.cancel(ticket);
  assert.equal(cancelled.stats.entryContexts.length, contextsBeforeCancel);
  assert.equal(cancelled.stats.regenContexts.at(-1), undefined);
});

test("auth, selected admission and immutable question snapshot are enforced before any hold", async () => {
  for (const source of [{questType: "COURSE"}, {questType: "SURVEY"}, {shelf: "home"}, {published: false}, {reviewApproved: false},
    {questions: []}, {questions: [snapshot().questions[0], snapshot().questions[0]]}, {priceNolics: 101}]) {
    const f = setup({source}); await assert.rejects(() => f.issue());
    assert.equal(f.db.paths("arena_entry_tickets/").length, 0); assert.equal(f.db.read("users/player").nolics, 1000);
  }
  const f = setup();
  await assert.rejects(() => f.issue("x", {uid: ""}), /authenticated uid/);
  await assert.rejects(() => f.issue("x", {mode: "RANDOM"}), /chosen by server/);
});

test("selected ceiling permits the actual price at or below confirmation and does not charge the ceiling", async () => {
  for (const maxPriceNolics of [10, 11, 100]) {
    const f = setup();
    const {ticket} = await f.issue("confirmed", {maxPriceNolics});
    assert.equal(ticket.priceNolics, 10);
    assert.equal(f.db.read("users/player").nolics, 990);
    assert.equal(f.db.read("users/player").reservedNolics, 10);
    assert.equal(f.db.read("users/player").reservedLifePoints, 50);
  }
});

test("a lower or zero selected ceiling refuses without any hold, receipt or regeneration checkpoint", async () => {
  for (const maxPriceNolics of [0, 9]) {
    const f = setup({regeneration: ({user}) => ({caughtUp: false, userPatch: {lifePoints: user.lifePoints}})});
    const before = f.db.paths("").map((path) => [path, f.db.read(path)]);
    await assert.rejects(f.issue("unconfirmed-price", {maxPriceNolics}), {code: "PRICE_CHANGED"});
    assert.deepEqual(f.db.paths("").map((path) => [path, f.db.read(path)]), before);
    assert.equal(f.db.commits.length, 0);
    assert.equal(f.stats.regenReads, 0);
    assert.equal(f.stats.regenWrites, 0);
  }
  const f = setup();
  await assert.rejects(f.issue("old-confirmation", {maxPriceNolics: 9}), {code: "PRICE_CHANGED"});
  assert.equal((await f.issue("new-explicit-confirmation", {maxPriceNolics: 10})).ticket.priceNolics, 10);
});

test("only exact safe integer selected ceilings 0 through 100 are accepted; supplied undefined is not absence", async () => {
  for (const maxPriceNolics of [undefined, null, false, true, "10", 10.5, -1, 101, NaN, Infinity, {}, [], Number.MAX_SAFE_INTEGER + 1]) {
    const f = setup();
    await assert.rejects(f.issue("invalid-ceiling", {maxPriceNolics}), {code: "INVALID_ARGUMENT"});
    assert.equal(f.db.commits.length, 0);
    assert.equal(f.stats.regenReads, 0);
  }
});

test("random entry rejects any supplied ceiling instead of silently ignoring it", async () => {
  for (const maxPriceNolics of [0, 10, 100, null, undefined]) {
    const f = setup();
    await assert.rejects(f.issue("random-ceiling", {mode: "RANDOM", lessonId: null, maxPriceNolics}), {code: "INVALID_ARGUMENT"});
    assert.equal(f.stats.randomCalls, 0);
    assert.equal(f.db.commits.length, 0);
  }
});

test("a confirmed request replays its original ticket after repricing but cannot change or omit its ceiling", async () => {
  const f = setup();
  const first = await f.issue("frozen-ceiling", {maxPriceNolics: 10});
  const before = f.db.read("users/player");
  f.db.put("server_lessons/lesson", snapshot({priceNolics: 100, ratingVersion: 6}));
  assert.deepEqual(await f.issue("frozen-ceiling", {maxPriceNolics: 10}), first);
  for (const patch of [{}, {maxPriceNolics: 9}, {maxPriceNolics: 11}, {maxPriceNolics: 100}]) {
    await assert.rejects(f.issue("frozen-ceiling", patch), {code: "REQUEST_REUSE"});
  }
  assert.deepEqual(f.db.read("users/player"), before);
  assert.equal(f.stats.regenReads, 1);
  assert.equal(f.db.paths("arena_entry_tickets/").length, 1);
});

test("omitted ceiling retains the exact old request hash and old ticket replay is never upgraded", async () => {
  const f = setup();
  const first = await f.issue("legacy");
  // Pre-extension digest: canonical sorted request keys, no maxPriceNolics key or default.
  const oldHash = createHash("sha256").update(JSON.stringify({difficulty: "EASY", lessonId: "lesson",
    mode: "SELECTED", requestId: "legacy", uid: "player"})).digest("hex");
  assert.equal(f.db.read(`arena_entry_tickets/${first.ticket.ticketId}`).requestHash, oldHash);
  assert.equal(Object.hasOwn(first.ticket, "maxPriceNolics"), false);
  f.db.put("server_lessons/lesson", snapshot({priceNolics: 100, ratingVersion: 9}));
  assert.deepEqual(await f.issue("legacy"), first);
  await assert.rejects(f.issue("legacy", {maxPriceNolics: 10}), {code: "REQUEST_REUSE"});
  assert.equal(f.db.read("users/player").reservedNolics, 10);
});

test("competing confirmations on the same request ID create one immutable ticket and hold", async () => {
  const f = setup();
  const outcomes = await Promise.allSettled([f.issue("race-ceiling", {maxPriceNolics: 10}),
    f.issue("race-ceiling", {maxPriceNolics: 100})]);
  assert.equal(outcomes.filter((outcome) => outcome.status === "fulfilled").length, 1);
  assert.equal(outcomes.find((outcome) => outcome.status === "rejected").reason.code, "REQUEST_REUSE");
  assert.equal(f.db.read("users/player").reservedNolics, 10);
  assert.equal(f.db.read("users/player").arenaSelectedHeldCount, 1);
  assert.equal(f.db.paths("arena_entry_tickets/").length, 1);
});

test("two devices cannot overspend available money or charges; insufficient issue is atomic", async () => {
  for (const user of [{nolics: 10, pointsNolics: 10, lifePoints: 100}, {lifePoints: 50}]) {
    const f = setup({user});
    const results = await Promise.allSettled([f.issue("a"), f.issue("b")]);
    assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal(f.db.read("users/player").reservedNolics, 10);
    assert.equal(f.db.read("users/player").reservedLifePoints, 50);
    assert.equal(f.db.paths("arena_entry_tickets/").length, 1); assert.ok(f.db.retries > 0);
  }
});

test("concurrent replay issues one ticket and selected reservations are capped at five until terminal", async () => {
  const f = setup(); const [a, b] = await Promise.all([f.issue("same"), f.issue("same")]);
  assert.deepEqual(a, b); assert.equal(f.db.read("users/player").arenaSelectedHeldCount, 1);
  const results = await Promise.allSettled(Array.from({length: 6}, (_, i) => f.issue(`other-${i}`)));
  assert.equal(results.filter((r) => r.status === "fulfilled").length, 4);
  assert.equal(f.db.read("users/player").arenaSelectedHeldCount, 5);
  await f.cancel(a.ticket); await f.issue("released-slot");
  assert.equal(f.db.read("users/player").arenaSelectedHeldCount, 5);
});

test("random is server-chosen, zero nolics and twenty UTC-day issues; cancellation never returns quota", async () => {
  const f = setup(); const tickets = [];
  for (let i = 0; i < 20; i++) {
    const {ticket} = await f.issue(`r-${i}`, {mode: "RANDOM", lessonId: null});
    tickets.push(ticket); await f.cancel(ticket, `c-${i}`);
  }
  assert.equal(f.db.read("users/player").nolics, 1000); assert.equal(tickets[0].priceNolics, 0);
  assert.equal(f.db.read("users/player").arenaSelectedHeldCount, 0);
  await assert.rejects(() => f.issue("r-21", {mode: "RANDOM", lessonId: null}), (e) => e.code === "RANDOM_DAILY_LIMIT");
  f.clock.now = Date.parse("2026-09-07T00:00:00Z");
  assert.equal((await f.issue("next-day", {mode: "RANDOM", lessonId: null})).status, "ISSUED");
  assert.equal(f.db.paths("arena_ticket_quotas/").length, 2);
});

test("two devices competing for the last random quota slot cannot issue twenty-one", async () => {
  const f = setup();
  for (let i = 0; i < 19; i++) await f.issue(`r-${i}`, {mode: "RANDOM", lessonId: null});
  const results = await Promise.allSettled([f.issue("last-a", {mode: "RANDOM", lessonId: null}),
    f.issue("last-b", {mode: "RANDOM", lessonId: null})]);
  assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
  assert.equal(f.db.paths("arena_entry_tickets/").length, 20);
  assert.equal(f.db.read(f.db.paths("arena_ticket_quotas/")[0]).randomIssued, 20);
});

test("cancel releases only its own hold once, has no expiry and cancelled ticket never awards", async () => {
  const f = setup(); const a = (await f.issue("a")).ticket; const b = (await f.issue("b")).ticket;
  f.clock.now += 400 * DAY;
  assert.equal(f.db.read(`arena_entry_tickets/${a.ticketId}`).status, "RESERVED");
  const [first, second] = await Promise.all([f.cancel(a, "cancel-a"), f.cancel(a, "cancel-b")]);
  assert.deepEqual(first, second);
  assert.equal(f.db.read("users/player").reservedNolics, 10); assert.equal(f.db.read("users/player").reservedLifePoints, 50);
  assert.equal(f.db.read("users/player").nolics, 990); assert.equal(f.db.read("users/player").lifePoints, 4950);
  await assert.rejects(() => f.settle(a), (e) => e.code === "TICKET_CANCELLED");
  await assert.rejects(() => f.cancel(b, "cancel-a"), (e) => e.code === "REQUEST_REUSE");
  assert.equal(f.db.read("users/author").nolics, 0); assert.equal(f.db.paths("result_events/").length, 0);
});

test("cancel advances long-absence regeneration under old holds before returning them, respecting the total cap", async () => {
  const observedHeld = [];
  const f = setup({user: {lifePoints: 50, lifePointsUpdatedAtMs: START}, regeneration: ({user, nowMs}) => {
    const held = user.reservedLifePoints || 0; observedHeld.push(held);
    const earned = Math.floor((nowMs - user.lifePointsUpdatedAtMs) / 1000);
    return {caughtUp: true, uid: "player", userPatch: {
      lifePoints: Math.min(100 - held, user.lifePoints + earned), lifePointsUpdatedAtMs: nowMs}};
  }});
  const {ticket} = await f.issue();
  f.clock.now += 400 * DAY;
  await f.cancel(ticket);
  assert.deepEqual(observedHeld, [0, 50]);
  assert.equal(f.db.read("users/player").lifePoints, 100, "earned available50 plus returned hold50, never150");
  assert.equal(f.db.read("users/player").reservedLifePoints, 0);
  assert.equal(f.db.read("users/player").lifePointsUpdatedAtMs, f.clock.now);
  assert.equal(f.db.read("server_regen/player").checkpoint, f.clock.now);
  const cancelCommit = f.db.commits.at(-1);
  assert.ok(cancelCommit.some((row) => row.path === "server_regen/player"));
  assert.ok(cancelCommit.some((row) => row.path.startsWith("arena_ticket_cancellations/")));
});

test("cancel catch-up checkpoints keep exact holds and omit cancellation receipt until caught up", async () => {
  let caughtUp = true;
  const f = setup({regeneration: ({user, nowMs}) => ({caughtUp, uid: "player",
    userPatch: {lifePoints: user.lifePoints, lifePointsUpdatedAtMs: nowMs}})});
  const {ticket} = await f.issue();
  caughtUp = false; f.clock.now += DAY;
  assert.equal((await f.cancel(ticket, "checkpoint-cancel")).status, "RETRY_REGEN");
  assert.equal(f.db.read(`arena_entry_tickets/${ticket.ticketId}`).status, "RESERVED");
  assert.equal(f.db.read("users/player").reservedNolics, 10);
  assert.equal(f.db.read("users/player").reservedLifePoints, 50);
  assert.equal(f.db.read("users/player").nolics, 990);
  assert.equal(f.db.paths("arena_ticket_cancellations/").length, 0);
  caughtUp = true; f.clock.now += DAY;
  const cancelled = await f.cancel(ticket, "checkpoint-cancel");
  assert.equal(cancelled.status, "CANCELLED");
  assert.equal(f.db.read("users/player").nolics, 1000);
  assert.equal(f.db.read("users/player").reservedLifePoints, 0);
  assert.deepEqual(await f.cancel(ticket, "checkpoint-cancel"), cancelled);
  assert.equal(f.db.paths("arena_ticket_cancellations/").length, 1);
});

test("another account or a second attempt cannot cancel or redeem the ticket", async () => {
  const f = setup(); const {ticket} = await f.issue();
  await assert.rejects(() => f.service.cancel({uid: "other", ticketId: ticket.ticketId, requestId: "steal"}),
    (e) => e.code === "TICKET_OWNER_MISMATCH");
  await assert.rejects(() => f.db.runTransaction((tx) => f.service.readTicketSettlement(tx,
    {uid: "player", ticketId: ticket.ticketId, attemptId: "second-attempt"})), (e) => e.code === "TICKET_ATTEMPT_MISMATCH");
});

test("multiple ticket readsets in one transaction refuse rather than overwrite shared hold or carry state", async () => {
  const f = setup(); const a = (await f.issue("a")).ticket; const b = (await f.issue("b")).ticket;
  await assert.rejects(() => f.db.runTransaction(async (tx) => {
    await f.service.readTicketSettlement(tx, {uid: "player", ticketId: a.ticketId, attemptId: a.attemptId});
    await f.service.readTicketSettlement(tx, {uid: "player", ticketId: b.ticketId, attemptId: b.attemptId});
  }), (e) => e.code === "MULTIPLE_TICKETS_IN_TRANSACTION");
  assert.equal(f.db.read("users/player").reservedNolics, 20);
  assert.equal(f.db.paths("result_events/").length, 0);
});

test("trusted completed scoring, immutable context and digest are required; unscorable remains held", async () => {
  const f = setup(); const {ticket} = await f.issue();
  for (const scoring of [{status: "UNSCORABLE"}, {status: "PENDING"}, {completed: false}, {lessonVersion: 9},
    {difficulty: "HARD"}, {uid: "other"}, {bundleDigest: "forged"}, {percentScore: NaN}, {scoreDigest: ""}]) {
    await assert.rejects(() => f.settle(ticket, {scoring}));
    assert.equal(f.db.read(`arena_entry_tickets/${ticket.ticketId}`).status, "RESERVED");
  }
  assert.equal(f.db.read("users/player").reservedLifePoints, 50); assert.equal(f.db.paths("result_events/").length, 0);
});

test("unified settlement commits result, holds, author, carry and ledgers once without second life debit", async () => {
  const f = setup(); const {ticket} = await f.issue();
  const [first, second] = await Promise.all([f.settle(ticket), f.settle(ticket)]);
  assert.deepEqual(first, second); assert.equal(first.consumedLifePoints, 50);
  assert.equal(f.db.read("users/player").lifePoints, 4950); assert.equal(f.db.read("users/player").nolics, 990);
  assert.equal(f.db.read("users/player").reservedLifePoints, 0); assert.equal(f.db.read("users/player").reservedNolics, 0);
  assert.equal(f.db.read("users/author").nolics, 8); assert.equal(f.db.read("users/author").pointsNolics, 8);
  assert.equal(f.db.read("arena_royalty_accounts/author").totalSinkNolics, 2);
  assert.equal(f.db.paths("result_events/").length, 1);
  const financialCommit = f.db.commits.find((writes) => writes.some((w) => w.path.startsWith("result_events/")));
  for (const path of ["users/player", "users/author", "arena_royalty_accounts/author", `arena_entry_tickets/${ticket.ticketId}`]) {
    assert.ok(financialCommit.some((w) => w.path === path));
  }
  const cancelledLate = await f.cancel(ticket); assert.equal(cancelledLate.status, "SETTLED");
  assert.equal(f.db.read("users/player").nolics, 990);
  await assert.rejects(() => f.settle(ticket, {scoring: {scoreDigest: "changed"}}), (e) => e.code === "SETTLEMENT_REUSE");
});

test("offline ticket settles its frozen form after publication changes; random completion pays no royalty", async () => {
  const f = setup(); const {ticket} = await f.issue("offline", {mode: "RANDOM", lessonId: null});
  f.db.put("server_lessons/lesson", snapshot({published: false, priceNolics: 100, lessonVersion: 10, questions: []}));
  f.clock.now += 400 * DAY;
  const settled = await f.settle(ticket);
  assert.equal(settled.consumedNolics, 0); assert.equal(settled.consumedLifePoints, 50);
  assert.equal(f.db.read("users/author").nolics, 0); assert.equal(f.db.read("users/player").nolics, 1000);
  assert.equal(f.db.paths("arena_royalty_accounts/").length, 0);
});

test("receipt and every financial write roll back together on planner, callback and commit failure", async () => {
  const f = setup(); const {ticket} = await f.issue(); const playerBefore = f.db.read("users/player");
  await assert.rejects(() => f.settle(ticket, {failAfterWrites: true}), /adapter failure/);
  f.db.failNextCommit = true; await assert.rejects(() => f.settle(ticket), /commit failure/);
  assert.deepEqual(f.db.read("users/player"), playerBefore); assert.equal(f.db.read("users/author").nolics, 0);
  assert.equal(f.db.read(`arena_entry_tickets/${ticket.ticketId}`).status, "RESERVED");
  assert.equal(f.db.paths("arena_royalty_accounts/").length, 0); assert.equal(f.db.paths("result_events/").length, 0);
  await f.settle(ticket); assert.equal(f.db.read(`arena_entry_tickets/${ticket.ticketId}`).status, "SETTLED");
});

test("cancel and unified settlement race is exclusive; no refund plus reward outcome", async () => {
  for (const cancelFirst of [true, false]) {
    const f = setup(); const {ticket} = await f.issue();
    const results = await Promise.allSettled(cancelFirst ? [f.cancel(ticket), f.settle(ticket)] : [f.settle(ticket), f.cancel(ticket)]);
    assert.ok(results.some((r) => r.status === "fulfilled"));
    const terminal = f.db.read(`arena_entry_tickets/${ticket.ticketId}`).status;
    assert.equal(f.db.read("users/player").reservedNolics, 0); assert.equal(f.db.read("users/player").reservedLifePoints, 0);
    if (terminal === "CANCELLED") {
      assert.equal(f.db.read("users/player").nolics, 1000); assert.equal(f.db.read("users/author").nolics, 0);
      assert.equal(f.db.paths("result_events/").length, 0);
    } else {
      assert.equal(terminal, "SETTLED"); assert.equal(f.db.read("users/player").nolics, 990);
      assert.equal(f.db.read("users/author").nolics, 8); assert.equal(f.db.paths("result_events/").length, 1);
    }
  }
});

test("five one-nolic payments conserve real reserve: author four, sink one, carry zero", async () => {
  const f = setup({source: {priceNolics: 1}}); const reserves = [];
  for (let i = 0; i < 5; i++) {
    const {ticket} = await f.issue(`payment-${i}`); await f.settle(ticket);
    const account = f.db.read("arena_royalty_accounts/author"); reserves.push(account.reservedNolics);
    assert.equal(account.totalPaymentsNolics, account.totalAuthorCreditNolics + account.totalSinkNolics + account.reservedNolics);
  }
  assert.deepEqual(reserves, [1, 1, 1, 1, 0]);
  assert.equal(f.db.read("users/player").nolics, 995); assert.equal(f.db.read("users/author").nolics, 4);
  assert.equal(f.db.read("arena_royalty_accounts/author").totalSinkNolics, 1);
  const ledger = f.db.paths(`${LEDGER_PATH}/`).map((path) => f.db.read(path)).filter((row) => row.currency === "nolics");
  assert.equal(ledger.reduce((sum, row) => sum + row.delta, 0), 0, "payer debits equal author/sink/escrow ledger credits");
});

test("concurrent separate tickets serialize shared author carry; self-author merges root rewards without overwriting", async () => {
  const f = setup({source: {priceNolics: 1}});
  const tickets = await Promise.all(Array.from({length: 5}, (_, i) => f.issue(`p${i}`)));
  await Promise.all(tickets.map(({ticket}) => f.settle(ticket)));
  assert.equal(f.db.read("users/author").nolics, 4); assert.equal(f.db.read("arena_royalty_accounts/author").totalSinkNolics, 1);
  const self = setup({source: {authorUid: "player"}}); const {ticket} = await self.issue();
  await self.settle(ticket, {playerReward: 7});
  assert.equal(self.db.read("users/player").nolics, 1005, "1000 - entry10 + rootreward7 + royalty8");
  assert.equal(self.db.read("users/player").reservedNolics, 0);
});

test("trusted regen advances with held life in cap, checkpoint retry neither reserves nor consumes random quota", async () => {
  let caughtUp = false;
  const f = setup({user: {lifePoints: 50, reservedLifePoints: 50}, regeneration: ({user, nowMs}) => ({
    caughtUp, userPatch: {lifePoints: Math.min(100 - user.reservedLifePoints, user.lifePoints + 50), lifePointsUpdatedAtMs: nowMs}, uid: "player"})});
  const retry = await f.issue("checkpoint", {mode: "RANDOM", lessonId: null});
  assert.equal(retry.status, "RETRY_REGEN"); assert.equal(f.db.paths("arena_entry_tickets/").length, 0);
  assert.equal(f.db.paths("arena_ticket_quotas/").length, 0); assert.equal(f.db.read("users/player").lifePoints, 50);
  caughtUp = true;
  const {ticket} = await f.issue("checkpoint", {mode: "RANDOM", lessonId: null});
  assert.equal(ticket.lifeCost, 50); assert.equal(f.db.read("users/player").lifePoints, 0);
  assert.equal(f.db.read("users/player").reservedLifePoints, 100);
});

test("unsafe balances, broken own-hold totals, author overflow and reserve commit failure cannot partially write", async () => {
  const overflow = setup({author: {nolics: Number.MAX_SAFE_INTEGER, pointsNolics: Number.MAX_SAFE_INTEGER}});
  const {ticket} = await overflow.issue();
  await assert.rejects(() => overflow.settle(ticket), (e) => e.code === "BALANCE_OVERFLOW");
  assert.equal(overflow.db.read(`arena_entry_tickets/${ticket.ticketId}`).status, "RESERVED");
  const corruptCarry = setup(); const carryTicket = (await corruptCarry.issue()).ticket;
  corruptCarry.db.put("arena_royalty_accounts/author", {authorCarryFifths: 0, sinkCarryFifths: 0, reservedNolics: 0,
    totalAuthorCreditNolics: 1, totalSinkNolics: 0, totalPaymentsNolics: 0});
  await assert.rejects(() => corruptCarry.settle(carryTicket), (e) => e.code === "INCONSISTENT_ROYALTY_ACCOUNT");
  const broken = setup(); const t = (await broken.issue()).ticket;
  broken.db.put("users/player", {...broken.db.read("users/player"), reservedLifePoints: 0});
  await assert.rejects(() => broken.cancel(t), (e) => e.code === "INCONSISTENT_HOLD");
  const failed = setup(); failed.db.failNextCommit = true;
  await assert.rejects(() => failed.issue(), /commit failure/);
  assert.equal(failed.db.read("users/player").nolics, 1000);
  assert.equal(failed.db.read(`arena_entry_tickets/${ticketDocumentId("player", "issue")}`), undefined);
  assert.equal(failed.db.paths(`${LEDGER_PATH}/`).length, 0);
});
