"use strict";

const {createHash} = require("node:crypto");
const {
  ArenaMutationReservationError,
  mutationDocumentId,
  createMutationRequest,
  resolveMutation,
  assertMutationIdForWrite,
} = require("./arena-mutation-reservations");
const {
  ArenaSeasonFinalizationError,
  CALCULATION_VERSION,
  createArenaSeasonFinalizer,
  pendingResponse,
} = require("./arena-season-finalization");

const LEDGER_PATH = "audit/currency_ledger/entries";
const AWARD_REASONS = {MILESTONE: "arena_milestone", SEASON_WIN: "arena_season"};

class ArenaServiceError extends Error {
  constructor(code, message) { super(message); this.name = "ArenaServiceError"; this.code = code; }
}
function text(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new ArenaServiceError("INVALID_ARGUMENT", `${name} is required`);
  return value;
}
function docId(value, name) {
  text(value, name);
  if (value.includes("/") || value === "." || value === "..") throw new ArenaServiceError("INVALID_ARGUMENT", `${name} is not a document ID`);
  return value;
}
function finalizationDocumentId(value) {
  docId(value, "finalizationId");
  if (Buffer.byteLength(value) > 1500) {
    throw new ArenaServiceError("SEASON_FINALIZATION_REQUIRES_REPAIR", "finalizationId exceeds the document ID limit");
  }
  return value;
}
function integer(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) throw new ArenaServiceError("INVALID_ARGUMENT", `${name} must be a safe integer >= ${min}`);
  return value;
}
function exactInput(value, fields, name) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).length !== fields.length || fields.some(field => !Object.hasOwn(value, field))) {
    throw new ArenaServiceError("INVALID_ARGUMENT", `${name} has unsupported fields`);
  }
  return value;
}
function hash(value) { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
function awardDocumentId(key) { return hash([key]); }
function voteDocumentId(uid) { return hash([uid]); }
function data(snapshot) { return snapshot.exists ? snapshot.data() : null; }
function add(a, b) {
  const result = BigInt(integer(a, "balance")) + BigInt(integer(b, "amount"));
  if (result > BigInt(Number.MAX_SAFE_INTEGER)) throw new ArenaServiceError("BALANCE_OVERFLOW", "box balance overflow");
  return Number(result);
}
function boxBalance(user) {
  for (const candidate of [user.boxCount, user.countBox]) {
    if (Number.isSafeInteger(candidate) && candidate >= 0) return candidate;
  }
  return 0;
}

/**
 * Handler supplies uid from verified Auth, never request.data.uid. Trusted transaction loaders:
 * loadLessonContext(tx, lessonId) -> {lessonId, authorUid, questType, published, publishedAtMs,
 *   contentVersion, reviewApproved}; it resolves real hierarchy, not declared parent/author IDs.
 * loadCompletion(tx,{uid,lessonId,attemptId}) -> canonical result receipt plus event.activityKind.
 * isCurator(tx,uid) reads server roles. It must not accept a client role/qualification assertion.
 *
 * The policy module is injected in drafts, or resolved alongside this file after integration.
 * All get/query operations precede writes. Mutation receipts, vote aggregates, placement history,
 * award markers, wallet credits and BOXES ledger rows commit in their respective single transaction.
 * Root integration must add AWARD_REASONS to currency-ledger's allow-list and route lesson sync.
 */
function createArenaService({db, policy = require("./arena-policy"), nowMs = Date.now,
  loadLessonContext, loadCompletion, isCurator,
  writeLessonChange = (tx, ctx, fields) => tx.set(db.doc(`lessons/${ctx.lessonId}`), fields, {merge: true})}) {
  for (const [name, value] of Object.entries({loadLessonContext, loadCompletion, isCurator, nowMs})) {
    if (typeof value !== "function") throw new TypeError(`${name} must be a function`);
  }
  if (!db || typeof db.runTransaction !== "function") throw new TypeError("Firestore db required");

  function arenaError(error) {
    if (error instanceof ArenaServiceError) throw error;
    if (error instanceof ArenaMutationReservationError || error instanceof ArenaSeasonFinalizationError) {
      throw new ArenaServiceError(error.code, error.message);
    }
    throw error;
  }
  // Build the request permissively so an old immutable receipt/reservation with a historical
  // long ID can still be found and replayed. New business writes are bounded only after that
  // lookup establishes this is a NEW request.
  function assertWritableMutationId(mutationId) {
    try { return assertMutationIdForWrite(mutationId); }
    catch (error) { return arenaError(error); }
  }
  function request(uid, mutationId, operation, payload) {
    try {
      const record = createMutationRequest({db, uid, mutationId, operation, payload});
      // Existing Arena rows/history use `id`; retain it as a non-authoritative alias while the
      // reservation module owns the canonical document identity.
      return {...record, id: record.documentId};
    } catch (error) {
      return arenaError(error);
    }
  }
  async function replay(tx, req) {
    try {
      const state = await resolveMutation(tx, req);
      return state.kind === "REPLAY" ? state.receipt : state.kind === "PENDING" ? state : null;
    } catch (error) {
      return arenaError(error);
    }
  }
  function decision(tx, req, now, status, result) {
    const receipt = {schemaVersion: 1, mutationId: req.mutationId, uid: req.uid, operation: req.operation,
      payloadHash: req.payloadHash, decidedAtMs: now, status, result};
    tx.create(req.ref, receipt);
    return receipt;
  }
  function reject(tx, req, now, code) { return decision(tx, req, now, "REJECTED", {code}); }
  function context(raw, lessonId) {
    if (!raw || raw.lessonId !== lessonId) return null;
    docId(raw.authorUid, "server authorUid");
    integer(raw.publishedAtMs, "publishedAtMs"); integer(raw.contentVersion, "contentVersion", 1);
    return raw;
  }
  function member(ctx, now) {
    return {lessonId: ctx.lessonId, authorUid: ctx.authorUid, questType: ctx.questType,
      publishedAtMs: ctx.publishedAtMs, contentVersion: ctx.contentVersion, capturedAtMs: now};
  }
  function history(tx, req, now, lessonId, fromShelf, targetShelf, reason, status, code = null) {
    tx.create(db.doc(`lesson_placements/${lessonId}/history/${req.id}`), {
      actorUid: req.uid, mutationId: req.mutationId, fromShelf, targetShelf, reason,
      status, code, decidedAtMs: now,
    });
  }
  function validAwardRecord(existing, reward) {
    const fields = ["key", "kind", "recipientUid", "currency", "amount"];
    if (!existing || fields.some(field => existing[field] !== reward[field]) ||
        (Object.hasOwn(reward, "lessonId") ? existing.lessonId !== reward.lessonId : Object.hasOwn(existing, "lessonId"))) {
      throw new ArenaServiceError("INCONSISTENT_AWARD", "award marker conflicts with the planned reward");
    }
  }
  function validAwardLedger(ledger, {reward, ledgerId, reason, sourceId}) {
    if (!ledger || ledger.id !== ledgerId || ledger.uid !== reward.recipientUid || ledger.currency !== "boxes" ||
        ledger.delta !== reward.amount || ledger.reason !== reason || ledger.sourceId !== sourceId ||
        ledger.details?.awardKey !== reward.key || ledger.details?.kind !== reward.kind) {
      throw new ArenaServiceError("INCONSISTENT_AWARD", "award marker lacks its matching ledger entry");
    }
  }
  async function prepareAward(tx, reward) {
    if (!reward) return null;
    if (reward.currency !== "BOXES" || !AWARD_REASONS[reward.kind]) throw new ArenaServiceError("INVALID_AWARD", "unsupported award");
    const id = awardDocumentId(reward.key);
    const ref = db.doc(`arena_awards/${id}`);
    const userRef = db.doc(`users/${docId(reward.recipientUid, "award recipient")}`);
    const reason = AWARD_REASONS[reward.kind];
    const ledgerId = `${reward.recipientUid}_boxes_${reason}_${id}`;
    const ledgerRef = db.doc(`${LEDGER_PATH}/${ledgerId}`);
    const existing = data(await tx.get(ref));
    const existingLedger = data(await tx.get(ledgerRef));
    if (existing) {
      validAwardRecord(existing, reward);
      validAwardLedger(existingLedger, {reward, ledgerId, reason, sourceId: id});
      return {alreadyAwarded: true, existing, reward, ref};
    }
    if (existingLedger) throw new ArenaServiceError("INCONSISTENT_AWARD", "ledger exists without award marker");
    const user = data(await tx.get(userRef)) || {};
    return {alreadyAwarded: false, ref, reward, userRef, boxCount: add(boxBalance(user), reward.amount), ledgerRef, ledgerId, reason};
  }
  function applyAward(tx, prepared, now) {
    if (!prepared || prepared.alreadyAwarded) return null;
    const {reward} = prepared;
    tx.create(prepared.ref, {...reward, awardedAtMs: now});
    tx.set(prepared.userRef, {boxCount: prepared.boxCount, updatedAtMs: now}, {merge: true});
    tx.create(prepared.ledgerRef, {id: prepared.ledgerId, uid: reward.recipientUid, currency: "boxes",
      delta: reward.amount, reason: prepared.reason, sourceId: awardDocumentId(reward.key), atMs: now,
      details: {awardKey: reward.key, kind: reward.kind}});
    return reward;
  }

  // The long-running season reducer receives the same award and immutable-receipt primitives as
  // the short Arena mutations. It therefore cannot accidentally create a second wallet/ledger
  // path while publishing a resumed job.
  const seasonFinalizer = createArenaSeasonFinalizer({
    db, policy, nowMs, prepareAward, applyAward, createDecision: decision,
  });

  async function submitLessonRating(input) {
    const {uid, mutationId, lessonId, attemptId, rating} = input;
    docId(lessonId, "lessonId"); text(attemptId, "attemptId"); integer(rating, "rating", 1);
    if (rating > 3) throw new ArenaServiceError("INVALID_ARGUMENT", "rating must be in 1..3");
    const req = request(uid, mutationId, "RATE_LESSON", {lessonId, attemptId, rating});
    return db.runTransaction(async (tx) => {
      const previous = await replay(tx, req); if (previous) return previous;
      assertWritableMutationId(mutationId);
      const now = integer(nowMs(), "server now");
      const ctx = context(await loadLessonContext(tx, lessonId), lessonId);
      if (!ctx || ctx.published !== true || ctx.questType !== "REGULAR") return reject(tx, req, now, "LESSON_NOT_ELIGIBLE");
      if (ctx.authorUid === uid) return reject(tx, req, now, "SELF_RATING");
      const raw = await loadCompletion(tx, {uid, lessonId, attemptId});
      if (!raw) throw new ArenaServiceError("SETTLEMENT_PENDING", "Attempt settlement has not arrived yet");
      if (!raw || (raw.uid || raw.userId) !== uid || raw.lessonId !== lessonId || raw.attemptId !== attemptId ||
          raw.status !== "SETTLED" || raw.completed !== true || !Number.isSafeInteger(raw.settledAtMs) ||
          raw.settledAtMs < 0 || raw.settledAtMs > now) {
        return reject(tx, req, now, "COMPLETED_SETTLEMENT_REQUIRED");
      }
      const completion = {attemptId, uid, lessonId, status: "SETTLED", completed: true, settledAtMs: raw.settledAtMs};
      const aggregateRef = db.doc(`lesson_ratings/${lessonId}`);
      const voteRef = db.doc(`lesson_ratings/${lessonId}/votes/${voteDocumentId(uid)}`);
      const oldAggregate = data(await tx.get(aggregateRef)) || {ratingSum: 0, ratingCount: 0, ratingVersion: 0};
      const oldVote = data(await tx.get(voteRef));
      policy.weightedScore(oldAggregate); integer(oldAggregate.ratingVersion, "ratingVersion");
      const vote = {ratingId: `arena-rating:${req.id}`, uid, lessonId, attemptId, rating, acceptedAtMs: now,
        acceptedSequence: add(oldAggregate.ratingVersion, 1)};
      const selected = policy.aggregateLessonRatings({lessonId, authorUid: ctx.authorUid,
        ratings: oldVote ? [oldVote.vote, vote] : [vote],
        completions: oldVote ? [oldVote.completion, completion] : [completion]});
      const replacesLatest = selected.acceptedRatingIds.includes(vote.ratingId);
      const ratingSum = oldAggregate.ratingSum + (replacesLatest ? rating - (oldVote ? oldVote.vote.rating : 0) : 0);
      const ratingCount = oldAggregate.ratingCount + (oldVote ? 0 : 1);
      const weightedScore = policy.weightedScore({ratingSum, ratingCount});
      const ratingVersion = replacesLatest ? add(oldAggregate.ratingVersion, 1) : oldAggregate.ratingVersion;
      const averageRating = ratingCount ? Math.round(ratingSum / ratingCount * 10) / 10 : null;
      const aggregate = {lessonId, authorUid: ctx.authorUid, ratingSum, ratingCount, weightedScore,
        averageRating, ratingVersion, ratingUpdatedAtMs: now};
      // Arena provenance comes from the authenticated result event, not today's shelf or input.
      const quarter = policy.utcQuarterWindow(now);
      const seasonRef = db.doc(`arena_seasons/${quarter.id}`);
      const memberRef = db.doc(`arena_seasons/${quarter.id}/members/${lessonId}`);
      let seasonState = null; let seasonMember = null;
      if (raw.activityKind === "ARENA") {
        seasonState = data(await tx.get(seasonRef));
        seasonMember = data(await tx.get(memberRef));
      }
      const seasonEligible = raw.activityKind === "ARENA" && (!seasonState || seasonState.status === "OPEN");

      if (replacesLatest) {
        tx.set(voteRef, {vote, completion, authorUidAtAcceptance: ctx.authorUid});
        tx.set(aggregateRef, aggregate);
        writeLessonChange(tx, ctx, {averageRating, ratingCount, ratingVersion,
          ratingUpdatedAtMs: now, lastModifiedAt: new Date(now)}, now);
      }
      if (seasonEligible) {
        if (!seasonState) tx.create(seasonRef, {quarter, status: "OPEN", createdAtMs: now});
        if (!seasonMember) tx.create(memberRef, member(ctx, now));
        tx.create(db.doc(`arena_season_votes/${quarter.id}/votes/${req.id}`), {
          vote, completion, authorUidAtAcceptance: ctx.authorUid, activityKind: "ARENA", mutationId,
        });
      }
      return decision(tx, req, now, "APPLIED", {lessonId, ratingId: vote.ratingId, acceptedAtMs: now,
        replacesLatest, seasonId: seasonEligible ? quarter.id : null, aggregate});
    });
  }

  async function transitionLesson(input) {
    const {uid, mutationId, lessonId, targetShelf, reason} = input;
    docId(lessonId, "lessonId"); text(targetShelf, "targetShelf"); text(reason, "reason");
    const req = request(uid, mutationId, "TRANSITION_LESSON", {lessonId, targetShelf, reason});
    return db.runTransaction(async (tx) => {
      const previous = await replay(tx, req); if (previous) return previous;
      assertWritableMutationId(mutationId);
      const now = integer(nowMs(), "server now");
      if (await isCurator(tx, uid) !== true) return reject(tx, req, now, "CURATOR_REQUIRED");
      const ctx = context(await loadLessonContext(tx, lessonId), lessonId);
      if (!ctx || ctx.questType !== "REGULAR") return reject(tx, req, now, "LESSON_NOT_ELIGIBLE");
      const placementRef = db.doc(`lesson_placements/${lessonId}`);
      const old = data(await tx.get(placementRef));
      const fromShelf = old ? old.shelf : "review";
      let rejection = null;
      if (!["arena", "tournament", "home", "retired", "rejected"].includes(targetShelf)) rejection = "UNSUPPORTED_PLACEMENT";
      else if (fromShelf !== targetShelf) {
        const admission = ["review", "retired", "rejected"].includes(fromShelf) && targetShelf === "arena";
        const promotion = (fromShelf === "arena" && targetShelf === "tournament") ||
          (fromShelf === "tournament" && targetShelf === "home");
        const retirement = ["arena", "tournament", "home"].includes(fromShelf) && targetShelf === "retired";
        const rejectionDecision = fromShelf === "review" && targetShelf === "rejected";
        if (!admission && !promotion && !retirement && !rejectionDecision) rejection = "INVALID_TRANSITION";
        else if ((admission || promotion) && ctx.published !== true) rejection = "LESSON_NOT_PUBLISHED";
        else if ((admission || promotion) && ctx.reviewApproved !== true) rejection = "REVIEW_APPROVAL_REQUIRED";
      }
      if (rejection) {
        history(tx, req, now, lessonId, fromShelf, targetShelf, reason, "REJECTED", rejection);
        tx.set(placementRef, {lessonId, shelf: fromShelf, lastRejection: {code: rejection, reason, actorUid: uid, atMs: now}}, {merge: true});
        return reject(tx, req, now, rejection);
      }
      let reward = null;
      if ((fromShelf === "arena" && targetShelf === "tournament") || (fromShelf === "tournament" && targetShelf === "home")) {
        reward = policy.planLessonAdvancement({lesson: {lessonId, authorUid: ctx.authorUid, questType: "REGULAR", shelf: fromShelf},
          targetShelf, curatorAuthorized: true}).reward;
      }
      const prepared = await prepareAward(tx, reward);
      const quarter = policy.utcQuarterWindow(now);
      const seasonRef = db.doc(`arena_seasons/${quarter.id}`);
      const memberRef = db.doc(`arena_seasons/${quarter.id}/members/${lessonId}`);
      let seasonState = null; let seasonMember = null;
      if (targetShelf === "arena") {
        seasonState = data(await tx.get(seasonRef)); seasonMember = data(await tx.get(memberRef));
      }
      const placement = {lessonId, authorUid: ctx.authorUid, questType: "REGULAR", shelf: targetShelf,
        previousShelf: fromShelf, contentVersion: ctx.contentVersion,
        placementVersion: add(old && old.placementVersion || 0, 1), updatedAtMs: now, lastReason: reason, decidedByUid: uid};
      if (targetShelf === "rejected") placement.lastRejection = {code: "REVIEW_REJECTED", reason, actorUid: uid, atMs: now};
      tx.set(placementRef, placement, {merge: true});
      writeLessonChange(tx, ctx, {arenaShelf: targetShelf, placementVersion: placement.placementVersion,
        placementUpdatedAtMs: now}, now);
      history(tx, req, now, lessonId, fromShelf, targetShelf, reason, "APPLIED");
      if (targetShelf === "arena" && (!seasonState || seasonState.status === "OPEN")) {
        if (!seasonState) tx.create(seasonRef, {quarter, status: "OPEN", createdAtMs: now});
        if (!seasonMember) tx.create(memberRef, member(ctx, now));
      }
      const awarded = applyAward(tx, prepared, now);
      return decision(tx, req, now, "APPLIED", {lessonId, fromShelf, targetShelf, awarded,
        alreadyAwarded: Boolean(prepared && prepared.alreadyAwarded)});
    });
  }

  function finalizationRecord(season) {
    const record = season?.finalization;
    if (!record || record.schemaVersion !== 2 || record.calculationVersion !== CALCULATION_VERSION ||
        typeof record.finalizationId !== "string" || !record.finalizationId ||
        !Number.isSafeInteger(record.startedAtMs) || record.startedAtMs < 0) {
      throw new ArenaServiceError("SEASON_FINALIZATION_REQUIRES_REPAIR", "Season finalization metadata requires repair");
    }
    finalizationDocumentId(record.finalizationId);
    return record;
  }
  function optionalFinalizationRecord(season) {
    try { return finalizationRecord(season); } catch { return null; }
  }
  function reviewStatus(quarter, season) {
    const record = optionalFinalizationRecord(season);
    // A damaged checkpoint is deliberately visible as a terminal review state. The curator gets
    // a bounded status projection, never its private job, lease, cursor, or corrupt payload.
    const updatedAtMs = Number.isSafeInteger(season.reviewAtMs) && season.reviewAtMs >= 0 ?
      season.reviewAtMs : record?.startedAtMs || 0;
    return {schemaVersion: 2, operation: "FINALIZE_SEASON", quarterId: quarter.id,
      finalizationId: record?.finalizationId || null, status: "REQUIRES_REVIEW",
      phase: "REQUIRES_REVIEW", updatedAtMs};
  }
  function finalizationStatus(quarter, season, job = null) {
    if (!season || !["FINALIZING", "FINALIZED", "REQUIRES_REVIEW"].includes(season.status)) {
      throw new ArenaServiceError("SEASON_NOT_FOUND", "Season finalization is not available");
    }
    if (season.status === "REQUIRES_REVIEW") return reviewStatus(quarter, season);
    const record = season.status === "FINALIZED" && !season.finalization ? null : finalizationRecord(season);
    const base = {schemaVersion: 2, operation: "FINALIZE_SEASON", quarterId: quarter.id,
      finalizationId: record ? record.finalizationId : null};
    if (season.status === "FINALIZED") {
      return {...base, status: "FINALIZED", updatedAtMs: integer(season.finalizedAtMs, "finalizedAtMs"), result: season.result};
    }
    if (!job || job.finalizationId !== record.finalizationId || !Number.isSafeInteger(job.updatedAtMs) || job.updatedAtMs < 0 ||
        !["SCAN_MEMBERS", "VERIFY_MEMBERS", "SCAN_VOTES", "SELECT", "VERIFY_SELECTION", "COMPARE_SELECTION",
          "AGGREGATE", "VERIFY_AGGREGATES", "COMPARE_AGGREGATES", "PICK_WINNER", "VERIFY_CHECKPOINTS",
          "PUBLISH", "PUBLISHED", "REQUIRES_REVIEW"].includes(job.phase)) {
      throw new ArenaServiceError("SEASON_FINALIZATION_REQUIRES_REPAIR", "Season finalization checkpoint requires repair");
    }
    return {...base, status: "PENDING", phase: job.phase, updatedAtMs: job.updatedAtMs};
  }

  // This transaction is the last-resort liveness fence for an absent or malformed private job.
  // It changes only the exact still-FINALIZING season that names this finalization ID; a stale
  // worker can therefore never quarantine a later job, publish, or completed season.
  async function quarantineFinalizingSeason(quarterId, finalizationId, reviewCode) {
    docId(quarterId, "quarterId"); docId(finalizationId, "finalizationId");
    return db.runTransaction(async tx => {
      const ref = db.doc(`arena_seasons/${quarterId}`);
      const season = data(await tx.get(ref));
      if (!season) throw new ArenaServiceError("INVALID_SOURCE", "Season finalization is absent");
      if (season.status === "FINALIZED") {
        return {status: "PUBLISHED", quarterId, finalizationId: season.finalization?.finalizationId || null};
      }
      if (season.status === "REQUIRES_REVIEW") return reviewStatus({id: quarterId}, season);
      if (season.status !== "FINALIZING") throw new ArenaServiceError("INVALID_SOURCE", "Season is not finalizing");
      const record = optionalFinalizationRecord(season);
      if (!record || record.finalizationId !== finalizationId) {
        return {status: "LEASE_LOST", quarterId, finalizationId};
      }
      const at = integer(nowMs(), "server now");
      tx.set(ref, {status: "REQUIRES_REVIEW", reviewCode, reviewAtMs: at}, {merge: true});
      return {schemaVersion: 2, operation: "FINALIZE_SEASON", status: "REQUIRES_REVIEW",
        quarterId, finalizationId, phase: "REQUIRES_REVIEW", updatedAtMs: at};
    });
  }

  async function finalizeSeason(input) {
    const {uid, mutationId, quarterAtMs} = exactInput(input, ["uid", "mutationId", "quarterAtMs"], "season finalization request");
    const quarter = policy.utcQuarterWindow(integer(quarterAtMs, "quarterAtMs"));
    const req = request(uid, mutationId, "FINALIZE_SEASON", {quarterId: quarter.id});
    return db.runTransaction(async (tx) => {
      let state;
      try { state = await resolveMutation(tx, req); }
      catch (error) {
        // A corrupt private reservation is terminally quarantined by the worker. The original
        // requester may learn only that fixed terminal state — never a raw reservation error —
        // so an idempotent retry cannot remain falsely PENDING forever.
        const season = data(await tx.get(db.doc(`arena_seasons/${quarter.id}`)));
        const record = season?.status === "REQUIRES_REVIEW" ? finalizationRecord(season) : null;
        if (record && record.reservationDocumentId === req.documentId && record.requestedByUid === uid &&
            record.requestedMutationId === mutationId) {
          return {schemaVersion: 2, operation: "FINALIZE_SEASON", status: "REQUIRES_REVIEW", quarterId: quarter.id,
            finalizationId: record.finalizationId, requestedAtMs: record.startedAtMs};
        }
        return arenaError(error);
      }
      if (state.kind === "REPLAY") return state.receipt;
      if (state.kind === "PENDING") {
        const season = data(await tx.get(db.doc(`arena_seasons/${quarter.id}`)));
        if (!season) throw new ArenaServiceError("INCONSISTENT_MUTATION", "Pending reservation has no season");
        if (season.status === "REQUIRES_REVIEW") {
          // The verified reservation is the original request's private authority. Metadata may
          // be exactly what the worker had to quarantine, so do not require it to be parseable
          // before giving that original caller the terminal, side-effect-free outcome.
          if (state.reservation.quarterId !== quarter.id) {
            throw new ArenaServiceError("INCONSISTENT_MUTATION", "Pending reservation has another quarter");
          }
          return {schemaVersion: 2, operation: "FINALIZE_SEASON", status: "REQUIRES_REVIEW", quarterId: quarter.id,
            finalizationId: state.reservation.finalizationId, requestedAtMs: state.reservation.createdAtMs};
        }
        if (season.finalization?.finalizationId !== state.reservation.finalizationId) {
          throw new ArenaServiceError("INCONSISTENT_MUTATION", "Pending reservation no longer matches its season");
        }
        if (season.status !== "FINALIZING") {
          throw new ArenaServiceError("INCONSISTENT_MUTATION", "Pending reservation has no active season");
        }
        return pendingResponse(quarter.id, state.reservation.finalizationId, state.reservation.createdAtMs);
      }
      assertWritableMutationId(mutationId);
      const now = integer(nowMs(), "server now");
      if (await isCurator(tx, uid) !== true) return reject(tx, req, now, "CURATOR_REQUIRED");
      if (now < quarter.endMs) return reject(tx, req, now, "SEASON_NOT_ENDED");
      const ref = db.doc(`arena_seasons/${quarter.id}`);
      const oldSeason = data(await tx.get(ref));
      if (oldSeason && oldSeason.status === "FINALIZED") return decision(tx, req, now, "APPLIED", oldSeason.result);
      if (oldSeason && oldSeason.status === "FINALIZING") {
        // This is a NEW mutation ID, not the reserved initiating request. Persist a terminal
        // rejection so it cannot masquerade as idempotent PENDING and later be reused by RATE.
        finalizationRecord(oldSeason);
        return reject(tx, req, now, "SEASON_ALREADY_FINALIZING");
      }
      if (oldSeason && oldSeason.status === "REQUIRES_REVIEW") {
        return reject(tx, req, now, "SEASON_REQUIRES_REVIEW");
      }
      if (oldSeason && oldSeason.status !== "OPEN") return reject(tx, req, now, "SEASON_FINALIZATION_REQUIRES_REPAIR");
      try {
        return seasonFinalizer.begin(tx, {request: req, quarter, now, oldSeason}).response;
      } catch (error) {
        return arenaError(error);
      }
    });
  }

  async function getSeasonFinalizationStatus(input) {
    const {uid, quarterAtMs} = exactInput(input, ["uid", "quarterAtMs"], "season status request");
    const quarter = policy.utcQuarterWindow(integer(quarterAtMs, "quarterAtMs"));
    return db.runTransaction(async (tx) => {
      if (await isCurator(tx, uid) !== true) throw new ArenaServiceError("CURATOR_REQUIRED", "Curator role required");
      const season = data(await tx.get(db.doc(`arena_seasons/${quarter.id}`)));
      if (!season) throw new ArenaServiceError("SEASON_NOT_FOUND", "Season finalization is not available");
      if (["FINALIZED", "REQUIRES_REVIEW"].includes(season.status)) return finalizationStatus(quarter, season);
      const record = finalizationRecord(season);
      const job = data(await tx.get(db.doc(`arena_seasons/${quarter.id}/finalizations/${record.finalizationId}`)));
      return finalizationStatus(quarter, season, job);
    });
  }

  async function advanceSeasonFinalization({quarterId, finalizationId, deadlineAtMs}) {
    try {
      return await seasonFinalizer.advance({quarterId, finalizationId, deadlineAtMs});
    } catch (error) {
      // `acquire()` cannot call markReview when the job itself is missing or malformed. Quarantine
      // the matching season instead of letting the scheduled sweep rediscover FINALIZING forever.
      if (error instanceof ArenaSeasonFinalizationError && ["INVALID_SOURCE", "INVALID_ARGUMENT"].includes(error.code)) {
        try { return await quarantineFinalizingSeason(quarterId, finalizationId, "FINALIZATION_JOB_REQUIRES_REPAIR"); }
        catch { /* Preserve the original deterministic error if the fence no longer matches. */ }
      }
      return arenaError(error);
    }
  }

  async function advanceSeasonFinalizationForQuarter({quarterId, deadlineAtMs}) {
    docId(quarterId, "quarterId");
    const state = await db.runTransaction(async tx => {
      const season = data(await tx.get(db.doc(`arena_seasons/${quarterId}`)));
      if (!season) throw new ArenaServiceError("INVALID_SOURCE", "Season finalization is absent");
      if (season.status === "FINALIZED") {
        return {status: "PUBLISHED", quarterId, finalizationId: season.finalization?.finalizationId || null};
      }
      if (season.status === "REQUIRES_REVIEW") return reviewStatus({id: quarterId}, season);
      if (season.status !== "FINALIZING") throw new ArenaServiceError("INVALID_SOURCE", "Season is not finalizing");
      let record;
      try { record = finalizationRecord(season); }
      catch {
        // This transaction read the exact current season first. Firestore retries if it changes,
        // so this cannot overwrite a concurrently replaced/published finalization.
        const at = integer(nowMs(), "server now");
        tx.set(db.doc(`arena_seasons/${quarterId}`), {
          status: "REQUIRES_REVIEW", reviewCode: "FINALIZATION_METADATA_REQUIRES_REPAIR", reviewAtMs: at,
        }, {merge: true});
        return {schemaVersion: 2, operation: "FINALIZE_SEASON", status: "REQUIRES_REVIEW",
          quarterId, finalizationId: null, phase: "REQUIRES_REVIEW", updatedAtMs: at};
      }
      return {status: "ADVANCE", finalizationId: record.finalizationId};
    });
    return state.status === "ADVANCE" ?
      advanceSeasonFinalization({quarterId, finalizationId: state.finalizationId, deadlineAtMs}) : state;
  }

  return {submitLessonRating, transitionLesson, finalizeSeason, getSeasonFinalizationStatus,
    advanceSeasonFinalization, advanceSeasonFinalizationForQuarter};
}

module.exports = {createArenaService, ArenaServiceError, mutationDocumentId, awardDocumentId, voteDocumentId,
  AWARD_REASONS, LEDGER_PATH};
