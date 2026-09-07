"use strict";

const {createHash} = require("node:crypto");
const RANDOM_DAILY_LIMIT = 20;
const SELECTED_HELD_LIMIT = 5;
const LEDGER_PATH = "audit/currency_ledger/entries";
const REASONS = Object.freeze({RESERVE: "arena_ticket_reserve", CANCEL: "arena_ticket_cancel",
  ROYALTY: "arena_author_royalty", SINK: "arena_royalty_sink", CARRY: "arena_royalty_carry"});

class ArenaTicketError extends Error {
  constructor(code, message = code) { super(message); this.name = "ArenaTicketError"; this.code = code; }
}
function fail(code, message) { throw new ArenaTicketError(code, message); }
function id(value, name) {
  if (typeof value !== "string" || !value.trim() || value.includes("/") || value === "." || value === "..") {
    fail("INVALID_ARGUMENT", `${name} must be a nonempty document ID`);
  }
  return value;
}
function integer(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", `${name} must be a safe integer >= ${min}`);
  return value;
}
function add(a, b) {
  const sum = BigInt(integer(a, "balance")) + BigInt(integer(b, "credit"));
  if (sum > BigInt(Number.MAX_SAFE_INTEGER)) fail("BALANCE_OVERFLOW");
  return Number(sum);
}
function sub(a, b) {
  integer(a, "balance"); integer(b, "debit");
  if (a < b) fail("INCONSISTENT_HOLD");
  return a - b;
}
function canonical(value) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return value;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (Array.isArray(value)) return value.map(canonical);
  if (value && Object.getPrototypeOf(value) === Object.prototype) {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
  }
  fail("INVALID_SNAPSHOT", "snapshot must contain JSON values only");
}
function digest(value) { return createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex"); }
function ticketDocumentId(uid, requestId) { return digest(["arena-ticket", id(uid, "uid"), id(requestId, "requestId")]); }
function attemptDocumentId(ticketId) { return digest(["arena-attempt", id(ticketId, "ticketId")]); }
function cancelDocumentId(uid, requestId) { return digest(["arena-cancel", id(uid, "uid"), id(requestId, "requestId")]); }
function data(snapshot) { return snapshot.exists ? snapshot.data() : null; }
function nolicBalance(user) {
  const value = user.pointsNolics == null ? user.nolics == null ? 0 : user.nolics : user.pointsNolics;
  return integer(value, "available nolics");
}
function holdings(user) {
  return {reservedNolics: integer(user.reservedNolics == null ? 0 : user.reservedNolics, "reservedNolics"),
    reservedLifePoints: integer(user.reservedLifePoints == null ? 0 : user.reservedLifePoints, "reservedLifePoints"),
    arenaSelectedHeldCount: integer(user.arenaSelectedHeldCount == null ? 0 : user.arenaSelectedHeldCount, "arenaSelectedHeldCount")};
}
function releasedHold(user, ticket) {
  const held = holdings(user);
  return {reservedNolics: sub(held.reservedNolics, ticket.priceNolics),
    reservedLifePoints: sub(held.reservedLifePoints, ticket.lifeCost),
    arenaSelectedHeldCount: sub(held.arenaSelectedHeldCount, ticket.mode === "SELECTED" ? 1 : 0)};
}
function utcDayId(now) {
  const date = new Date(integer(now, "server now"));
  if (!Number.isFinite(date.getTime())) fail("INVALID_ARGUMENT", "server time outside Date range");
  return date.toISOString().slice(0, 10);
}
function entrySnapshot(raw, lessonId, difficulty) {
  if (!raw || raw.lessonId !== lessonId || raw.difficulty !== difficulty || raw.questType !== "REGULAR" ||
      raw.published !== true || raw.reviewApproved !== true || raw.shelf !== "arena") fail("LESSON_NOT_ELIGIBLE");
  id(raw.authorUid, "server authorUid"); integer(raw.lessonVersion, "lessonVersion", 1);
  integer(raw.priceNolics, "server priceNolics", 1);
  if (raw.priceNolics > 100) fail("INVALID_SNAPSHOT", "selected price must be 1..100");
  integer(raw.ratingVersion, "ratingVersion"); integer(raw.lifeCost, "server lifeCost", 1);
  if (!Array.isArray(raw.questions) || !raw.questions.length) fail("INVALID_SNAPSHOT", "immutable questions required");
  const seen = new Set();
  const questions = raw.questions.map((question) => {
    id(question.questionId, "questionId"); integer(question.questionVersion, "questionVersion", 1);
    if (seen.has(question.questionId)) fail("INVALID_SNAPSHOT", "duplicate question ID");
    seen.add(question.questionId);
    if (!question.publicQuestion || !question.gradingKey) fail("INVALID_SNAPSHOT", "public question and private grading key required");
    return canonical({questionId: question.questionId, questionVersion: question.questionVersion,
      publicQuestion: question.publicQuestion, gradingKey: question.gradingKey});
  });
  // Private gradingKey never appears in issue output. HARD publicQuestion must be E2-redacted;
  // EASY retains the existing keyed payload for offline learning and S hints.
  return {lessonId, lessonVersion: raw.lessonVersion, difficulty, authorUid: raw.authorUid,
    ratingVersion: raw.ratingVersion, priceNolics: raw.priceNolics, lifeCost: raw.lifeCost, questions,
    contentContext: raw.contentContext == null ? null : canonical(raw.contentContext),
    bundleDigest: digest({lessonId, lessonVersion: raw.lessonVersion, difficulty, questions})};
}
function publicTicket(ticket) {
  // The private bundle fingerprint includes small answer keys. Publishing it would let a caller
  // brute-force a short lesson's answers; the authoritative grader already reads it server-side.
  const {questions, bundleDigest, ...rest} = ticket;
  return {...rest, questions: questions.map(({questionId, questionVersion, publicQuestion}) =>
    ({questionId, questionVersion, publicQuestion}))};
}

/**
 * Server-only adapter. uid is supplied from verified Auth, never request.data.uid.
 * chooseRandomLesson(tx,{uid,difficulty,requestId,nowMs}) returns a server-chosen lessonId;
 * it must be retry-safe, use server entropy, and have no nontransactional side effects.
 * loadEntrySnapshot(tx,{lessonId,difficulty,nowMs}) supplies the immutable authoritative shape
 * validated above, including a HARD-redacted/EASY-keyed publicQuestion and a private gradingKey.
 * readRegeneration(tx,{uid,user,nowMs}) returns {caughtUp,userPatch,...privatePlan}; its cap
 * counts available+reserved life. writeRegeneration(tx,plan) writes ONLY premium state/ledger,
 * never users/{uid}; caller merges userPatch. A !caughtUp checkpoint creates no ticket/hold.
 *
 * issue/cancel are complete transactions. Settlement is deliberately NOT a public endpoint:
 * readTicketSettlement -> planTicketSettlement -> writeTicketSettlement compose into the SAME
 * transaction as server-scored E2 receipt, player rewards and plasma. The root must bypass its
 * old activity-life debit, use ticket.lifeCost, and write mergeTicketPayerPatch(plan,rootUser).
 * One ticket per transaction: the caller splits a ticket-event batch into individually atomic
 * E2+ticket transactions. Independent readsets cannot safely overwrite shared carry/hold totals.
 * Advance premium against OLD holds before releasing them, not after, to prevent backdated regen.
 * No query, TTL, automatic expiry, client price, client correctness or client score is used.
 */
function createArenaTickets({db, policy = require("./arena-policy"), nowMs = Date.now,
  chooseRandomLesson, loadEntrySnapshot, readRegeneration, writeRegeneration, validateTicketForDelivery = () => {}}) {
  if (!db || typeof db.runTransaction !== "function") throw new TypeError("Firestore db required");
  for (const [name, value] of Object.entries({nowMs, chooseRandomLesson, loadEntrySnapshot, readRegeneration, writeRegeneration, validateTicketForDelivery})) {
    if (typeof value !== "function") throw new TypeError(`${name} must be a function`);
  }
  const plans = new WeakSet();
  const transactionTickets = new WeakMap();
  function validateRegenerationPlan(regeneration) {
    if (!regeneration || typeof regeneration.caughtUp !== "boolean" || !regeneration.userPatch) fail("INVALID_REGENERATION_PLAN");
    for (const field of ["reservedNolics", "reservedLifePoints", "arenaSelectedHeldCount", "nolics", "pointsNolics"]) {
      if (Object.hasOwn(regeneration.userPatch, field)) fail("INVALID_REGENERATION_PLAN", `regeneration cannot change ${field}`);
    }
  }
  function ledger(tx, {uid, currency, delta, reason, ticketId, atMs, details}) {
    if (!delta) return;
    const ledgerId = digest([ticketId, uid, currency, reason]);
    tx.create(db.doc(`${LEDGER_PATH}/${ledgerId}`), {id: ledgerId, uid, currency, delta, reason,
      sourceId: ticketId, atMs, details});
  }
  async function issue(input) {
    const {uid, requestId, mode, lessonId = null, difficulty, maxPriceNolics} = input;
    id(uid, "authenticated uid"); id(requestId, "requestId");
    if (!["RANDOM", "SELECTED"].includes(mode) || !["EASY", "HARD"].includes(difficulty)) fail("INVALID_ARGUMENT");
    if (mode === "SELECTED") id(lessonId, "selected lessonId");
    else if (lessonId !== null) fail("INVALID_ARGUMENT", "random lesson must be chosen by server");
    const hasPriceCeiling = Object.hasOwn(input, "maxPriceNolics");
    if (hasPriceCeiling) {
      if (mode !== "SELECTED") fail("INVALID_ARGUMENT", "maxPriceNolics is only accepted for selected entry");
      integer(maxPriceNolics, "maxPriceNolics");
      if (maxPriceNolics > 100) fail("INVALID_ARGUMENT", "maxPriceNolics must be at most 100");
    }
    // Omitting the new field must preserve the pre-ceiling request hash byte-for-byte. A retry
    // with a supplied ceiling binds that exact confirmation, not a default price or live rating.
    const requestHash = digest({uid, requestId, mode, lessonId, difficulty,
      ...(hasPriceCeiling ? {maxPriceNolics} : {})});
    const ticketId = ticketDocumentId(uid, requestId); const ticketRef = db.doc(`arena_entry_tickets/${ticketId}`);
    return db.runTransaction(async (tx) => {
      const previous = data(await tx.get(ticketRef));
      if (previous) {
        if (previous.uid !== uid || previous.requestHash !== requestHash) fail("REQUEST_REUSE");
        validateTicketForDelivery(previous);
        return {status: "ISSUED", ticket: publicTicket(previous)};
      }
      const now = integer(nowMs(), "server now"); const userRef = db.doc(`users/${uid}`);
      const user = data(await tx.get(userRef)); if (!user) fail("USER_NOT_FOUND");
      const held = holdings(user);
      if (mode === "SELECTED" && held.arenaSelectedHeldCount >= SELECTED_HELD_LIMIT) fail("SELECTED_LIMIT");
      const dayId = utcDayId(now); const quotaRef = db.doc(`arena_ticket_quotas/${digest([uid, dayId])}`);
      const quota = data(await tx.get(quotaRef));
      const issued = integer(quota ? quota.randomIssued : 0, "randomIssued");
      if (mode === "RANDOM" && issued >= RANDOM_DAILY_LIMIT) fail("RANDOM_DAILY_LIMIT");
      const chosenId = mode === "RANDOM" ? id(await chooseRandomLesson(tx, {uid, difficulty, requestId, nowMs: now}), "server random lessonId") : lessonId;
      const snapshot = entrySnapshot(await loadEntrySnapshot(tx, {lessonId: chosenId, difficulty, nowMs: now}), chosenId, difficulty);
      // Frozen delivery validation also applies before any regeneration checkpoint or hold.
      validateTicketForDelivery(snapshot);
      // Every optimistic retry rereads the authoritative price before any checkpoint, hold or
      // receipt write. PRICE_CHANGED requires fresh explicit confirmation; it spends nothing.
      if (hasPriceCeiling && snapshot.priceNolics > maxPriceNolics) fail("PRICE_CHANGED");
      const regeneration = await readRegeneration(tx, {uid, user, nowMs: now});
      validateRegenerationPlan(regeneration);
      const regenerated = {...user, ...regeneration.userPatch};
      const availableLife = integer(regenerated.lifePoints, "available lifePoints");
      if (!regeneration.caughtUp) {
        writeRegeneration(tx, regeneration); tx.set(userRef, regeneration.userPatch, {merge: true});
        return {status: "RETRY_REGEN", requestId};
      }
      const priceNolics = mode === "RANDOM" ? 0 : snapshot.priceNolics;
      if (nolicBalance(user) < priceNolics) fail("INSUFFICIENT_NOLICS");
      if (availableLife < snapshot.lifeCost) fail("INSUFFICIENT_LIFE");
      const ticket = {...snapshot, schemaVersion: 1, ticketId, attemptId: attemptDocumentId(ticketId), uid,
        requestId, requestHash, mode, priceNolics, issuedAtMs: now, dayId, status: "RESERVED", terminal: null};
      const nolics = sub(nolicBalance(user), priceNolics);
      const patch = {...regeneration.userPatch, nolics, pointsNolics: nolics,
        lifePoints: sub(availableLife, snapshot.lifeCost), reservedNolics: add(held.reservedNolics, priceNolics),
        reservedLifePoints: add(held.reservedLifePoints, snapshot.lifeCost),
        arenaSelectedHeldCount: add(held.arenaSelectedHeldCount, mode === "SELECTED" ? 1 : 0)};
      writeRegeneration(tx, regeneration); tx.set(userRef, patch, {merge: true}); tx.create(ticketRef, ticket);
      if (mode === "RANDOM") tx.set(quotaRef, {uid, dayId, randomIssued: add(issued, 1), updatedAtMs: now});
      const details = {transition: "AVAILABLE_TO_HELD", attemptId: ticket.attemptId};
      ledger(tx, {uid, currency: "nolics", delta: -priceNolics, reason: REASONS.RESERVE, ticketId, atMs: now, details});
      ledger(tx, {uid, currency: "standardChargePoints", delta: -snapshot.lifeCost, reason: REASONS.RESERVE, ticketId, atMs: now, details});
      return {status: "ISSUED", ticket: publicTicket(ticket)};
    });
  }
  function verifyTicket(ticket, uid, ticketId, attemptId) {
    if (!ticket) fail("TICKET_NOT_FOUND");
    if (ticket.uid !== uid) fail("TICKET_OWNER_MISMATCH");
    if (ticket.ticketId !== ticketId || ticket.attemptId !== attemptDocumentId(ticketId) ||
        (attemptId !== undefined && ticket.attemptId !== attemptId)) fail("TICKET_ATTEMPT_MISMATCH");
    if (!["RESERVED", "CANCELLED", "SETTLED"].includes(ticket.status) || !["RANDOM", "SELECTED"].includes(ticket.mode)) fail("CORRUPT_TICKET");
    integer(ticket.priceNolics, "ticket price"); integer(ticket.lifeCost, "ticket lifeCost", 1);
    if ((ticket.mode === "RANDOM" && ticket.priceNolics !== 0) ||
        (ticket.mode === "SELECTED" && (ticket.priceNolics < 1 || ticket.priceNolics > 100))) fail("CORRUPT_TICKET");
  }
  async function cancel({uid, ticketId, requestId}) {
    id(uid, "authenticated uid"); id(ticketId, "ticketId"); id(requestId, "requestId");
    const ticketRef = db.doc(`arena_entry_tickets/${ticketId}`);
    const requestRef = db.doc(`arena_ticket_cancellations/${cancelDocumentId(uid, requestId)}`);
    return db.runTransaction(async (tx) => {
      const oldRequest = data(await tx.get(requestRef));
      if (oldRequest) {
        if (oldRequest.uid !== uid || oldRequest.ticketId !== ticketId) fail("REQUEST_REUSE");
        return oldRequest.result;
      }
      const ticket = data(await tx.get(ticketRef)); verifyTicket(ticket, uid, ticketId);
      if (ticket.status !== "RESERVED") {
        const result = {status: ticket.status, ticketId, terminal: ticket.terminal};
        tx.create(requestRef, {uid, ticketId, requestId, result}); return result;
      }
      const now = integer(nowMs(), "server now"); const userRef = db.doc(`users/${uid}`);
      const user = data(await tx.get(userRef)); if (!user) fail("USER_NOT_FOUND");
      // Advance the authoritative FIFO clock while the old holds still occupy capacity.
      // A catch-up checkpoint must not create a terminal cancellation receipt or release money.
      const regeneration = await readRegeneration(tx, {uid, user, nowMs: now});
      validateRegenerationPlan(regeneration);
      const regenerated = {...user, ...regeneration.userPatch};
      integer(regenerated.lifePoints, "available lifePoints");
      if (!regeneration.caughtUp) {
        writeRegeneration(tx, regeneration); tx.set(userRef, regeneration.userPatch, {merge: true});
        return {status: "RETRY_REGEN", ticketId, requestId};
      }
      const nolics = add(nolicBalance(user), ticket.priceNolics);
      const patch = {...regeneration.userPatch, ...releasedHold(user, ticket), nolics, pointsNolics: nolics,
        lifePoints: add(regenerated.lifePoints, ticket.lifeCost)};
      const terminal = {receiptId: `arena-cancel:${ticketId}`, cancelledAtMs: now,
        releasedNolics: ticket.priceNolics, releasedLifePoints: ticket.lifeCost};
      const result = {status: "CANCELLED", ticketId, terminal};
      writeRegeneration(tx, regeneration);
      tx.set(userRef, patch, {merge: true}); tx.set(ticketRef, {status: "CANCELLED", terminal}, {merge: true});
      tx.create(requestRef, {uid, ticketId, requestId, result});
      const details = {transition: "HELD_TO_AVAILABLE", attemptId: ticket.attemptId};
      ledger(tx, {uid, currency: "nolics", delta: ticket.priceNolics, reason: REASONS.CANCEL, ticketId, atMs: now, details});
      ledger(tx, {uid, currency: "standardChargePoints", delta: ticket.lifeCost, reason: REASONS.CANCEL, ticketId, atMs: now, details});
      return result;
    });
  }
  async function readTicketSettlement(tx, {uid, ticketId, attemptId}) {
    id(uid, "authenticated uid"); id(ticketId, "ticketId"); id(attemptId, "attemptId");
    const existingTicketId = transactionTickets.get(tx);
    if (existingTicketId && existingTicketId !== ticketId) fail("MULTIPLE_TICKETS_IN_TRANSACTION", "one E2+ticket settlement per transaction required");
    transactionTickets.set(tx, ticketId);
    const ticketRef = db.doc(`arena_entry_tickets/${ticketId}`);
    const ticket = data(await tx.get(ticketRef)); verifyTicket(ticket, uid, ticketId, attemptId);
    if (ticket.status !== "RESERVED") return {ticket, ticketRef, tx, now: integer(nowMs(), "server now")};
    const userRef = db.doc(`users/${uid}`); const user = data(await tx.get(userRef));
    if (!user) fail("USER_NOT_FOUND");
    releasedHold(user, ticket);
    let authorRef = null; let author = null; let royaltyRef = null; let royalty = null;
    if (ticket.priceNolics > 0) {
      authorRef = db.doc(`users/${id(ticket.authorUid, "authorUid")}`);
      author = ticket.authorUid === uid ? user : data(await tx.get(authorRef));
      if (!author) fail("AUTHOR_NOT_FOUND");
      royaltyRef = db.doc(`arena_royalty_accounts/${ticket.authorUid}`);
      royalty = data(await tx.get(royaltyRef)) || {authorCarryFifths: 0, sinkCarryFifths: 0, reservedNolics: 0,
        totalAuthorCreditNolics: 0, totalSinkNolics: 0, totalPaymentsNolics: 0};
      policy.splitAuthorRoyalty(0, royalty); // Validate fractional escrow independently of this payment.
      const totalPayments = BigInt(integer(royalty.totalPaymentsNolics, "totalPaymentsNolics"));
      const accounted = BigInt(integer(royalty.totalAuthorCreditNolics, "totalAuthorCreditNolics")) +
        BigInt(integer(royalty.totalSinkNolics, "totalSinkNolics")) + BigInt(royalty.reservedNolics);
      if (totalPayments !== accounted) fail("INCONSISTENT_ROYALTY_ACCOUNT");
    }
    return {ticket, ticketRef, user, userRef, author, authorRef, royalty, royaltyRef, tx, now: integer(nowMs(), "server now")};
  }
  function planTicketSettlement(readset, scoring) {
    const {ticket, now} = readset;
    if (ticket.status === "CANCELLED") fail("TICKET_CANCELLED");
    if (!scoring || scoring.status !== "SETTLED" || scoring.completed !== true) fail("COMPLETED_SCORING_REQUIRED");
    for (const field of ["uid", "ticketId", "attemptId", "lessonId", "lessonVersion", "difficulty", "bundleDigest"]) {
      if (scoring[field] !== ticket[field]) fail("SCORING_CONTEXT_MISMATCH", field);
    }
    id(scoring.scoreDigest, "server scoreDigest");
    if (typeof scoring.percentScore !== "number" || !Number.isFinite(scoring.percentScore) ||
        scoring.percentScore < 0 || scoring.percentScore > 100) fail("INVALID_SCORE");
    if (ticket.status === "SETTLED") {
      if (ticket.terminal.scoreDigest !== scoring.scoreDigest || ticket.terminal.percentScore !== scoring.percentScore) fail("SETTLEMENT_REUSE");
      const replay = {replay: true, terminal: ticket.terminal, readset}; plans.add(replay); return replay;
    }
    const split = ticket.priceNolics ? policy.splitAuthorRoyalty(ticket.priceNolics, readset.royalty) : null;
    const userPatch = releasedHold(readset.user, ticket);
    const payerNolicsCredit = split && ticket.authorUid === ticket.uid ? split.authorCreditNolics : 0;
    const authorBalance = split ? add(nolicBalance(readset.author), split.authorCreditNolics) : null;
    const royalty = split ? {...split.next,
      totalAuthorCreditNolics: add(readset.royalty.totalAuthorCreditNolics, split.authorCreditNolics),
      totalSinkNolics: add(readset.royalty.totalSinkNolics, split.sinkNolics),
      totalPaymentsNolics: add(readset.royalty.totalPaymentsNolics, ticket.priceNolics), updatedAtMs: now} : null;
    const terminal = {receiptId: `arena-settle:${ticket.ticketId}`, attemptId: ticket.attemptId, settledAtMs: now,
      scoreDigest: scoring.scoreDigest, percentScore: scoring.percentScore, consumedNolics: ticket.priceNolics,
      consumedLifePoints: ticket.lifeCost, authorCreditNolics: split ? split.authorCreditNolics : 0,
      sinkNolics: split ? split.sinkNolics : 0, royaltyReserveDeltaNolics: split ? split.reserveDeltaNolics : 0};
    const plan = {replay: false, readset, userPatch, payerNolicsCredit, authorBalance, royalty, split, terminal};
    plans.add(plan); return plan;
  }
  function mergeTicketPayerPatch(plan, rootComputedUser) {
    if (!plans.has(plan)) fail("INVALID_SETTLEMENT_PLAN");
    if (plan.replay) return {};
    const patch = {...plan.userPatch};
    if (plan.payerNolicsCredit) {
      const nolics = add(nolicBalance(rootComputedUser), plan.payerNolicsCredit);
      patch.nolics = nolics; patch.pointsNolics = nolics;
    }
    // Deliberately no lifePoints debit: issue already removed the full activity hold.
    return patch;
  }
  function writeTicketSettlement(tx, plan) {
    if (!plans.has(plan) || plan.readset.tx !== tx) fail("INVALID_SETTLEMENT_PLAN");
    plans.delete(plan);
    if (plan.replay) return plan.terminal;
    const {ticket, ticketRef, authorRef, royaltyRef, now} = plan.readset;
    tx.set(ticketRef, {status: "SETTLED", terminal: plan.terminal}, {merge: true});
    if (plan.split) {
      tx.set(royaltyRef, plan.royalty);
      if (ticket.authorUid !== ticket.uid && plan.split.authorCreditNolics) {
        tx.set(authorRef, {nolics: plan.authorBalance, pointsNolics: plan.authorBalance}, {merge: true});
      }
      const details = {attemptId: ticket.attemptId, payerUid: ticket.uid, authorUid: ticket.authorUid};
      ledger(tx, {uid: ticket.authorUid, currency: "nolics", delta: plan.split.authorCreditNolics,
        reason: REASONS.ROYALTY, ticketId: ticket.ticketId, atMs: now, details});
      ledger(tx, {uid: `arena-sink:${ticket.authorUid}`, currency: "nolics", delta: plan.split.sinkNolics,
        reason: REASONS.SINK, ticketId: ticket.ticketId, atMs: now, details});
      ledger(tx, {uid: `arena-carry:${ticket.authorUid}`, currency: "nolics", delta: plan.split.reserveDeltaNolics,
        reason: REASONS.CARRY, ticketId: ticket.ticketId, atMs: now, details});
    }
    return plan.terminal;
  }
  return {issue, cancel, readTicketSettlement, planTicketSettlement, mergeTicketPayerPatch, writeTicketSettlement};
}

module.exports = {createArenaTickets, ArenaTicketError, ticketDocumentId, attemptDocumentId,
  cancelDocumentId, utcDayId, nolicBalance, RANDOM_DAILY_LIMIT, SELECTED_HELD_LIMIT, LEDGER_PATH, REASONS};
