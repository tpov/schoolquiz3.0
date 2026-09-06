"use strict";

const {createHash} = require("node:crypto");

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
function integer(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) throw new ArenaServiceError("INVALID_ARGUMENT", `${name} must be a safe integer >= ${min}`);
  return value;
}
function hash(value) { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
function mutationDocumentId(uid, mutationId) { return hash([uid, mutationId]); }
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

  function request(uid, mutationId, operation, payload) {
    docId(uid, "authenticated uid"); text(mutationId, "mutationId");
    const id = mutationDocumentId(uid, mutationId);
    return {uid, mutationId, operation, payload, id, payloadHash: hash([operation, payload]),
      ref: db.doc(`arena_mutations/${id}`)};
  }
  async function replay(tx, req) {
    const previous = data(await tx.get(req.ref));
    if (!previous) return null;
    if (previous.uid !== req.uid || previous.payloadHash !== req.payloadHash || previous.operation !== req.operation) {
      throw new ArenaServiceError("MUTATION_REUSE", "mutationId already has another immutable request");
    }
    return previous;
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
  async function prepareAward(tx, reward) {
    if (!reward) return null;
    if (reward.currency !== "BOXES" || !AWARD_REASONS[reward.kind]) throw new ArenaServiceError("INVALID_AWARD", "unsupported award");
    const id = awardDocumentId(reward.key);
    const ref = db.doc(`arena_awards/${id}`);
    const existing = data(await tx.get(ref));
    if (existing) return {alreadyAwarded: true, existing, reward, ref};
    const userRef = db.doc(`users/${docId(reward.recipientUid, "award recipient")}`);
    const user = data(await tx.get(userRef)) || {};
    const reason = AWARD_REASONS[reward.kind];
    const ledgerId = `${reward.recipientUid}_boxes_${reason}_${id}`;
    const ledgerRef = db.doc(`${LEDGER_PATH}/${ledgerId}`);
    if (data(await tx.get(ledgerRef))) throw new ArenaServiceError("INCONSISTENT_AWARD", "ledger exists without award marker");
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

  async function submitLessonRating(input) {
    const {uid, mutationId, lessonId, attemptId, rating} = input;
    docId(lessonId, "lessonId"); text(attemptId, "attemptId"); integer(rating, "rating", 1);
    if (rating > 3) throw new ArenaServiceError("INVALID_ARGUMENT", "rating must be in 1..3");
    const req = request(uid, mutationId, "RATE_LESSON", {lessonId, attemptId, rating});
    return db.runTransaction(async (tx) => {
      const previous = await replay(tx, req); if (previous) return previous;
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

  async function finalizeSeason(input) {
    const {uid, mutationId, quarterAtMs} = input;
    const quarter = policy.utcQuarterWindow(integer(quarterAtMs, "quarterAtMs"));
    const req = request(uid, mutationId, "FINALIZE_SEASON", {quarterId: quarter.id});
    return db.runTransaction(async (tx) => {
      const previous = await replay(tx, req); if (previous) return previous;
      const now = integer(nowMs(), "server now");
      if (await isCurator(tx, uid) !== true) return reject(tx, req, now, "CURATOR_REQUIRED");
      if (now < quarter.endMs) return reject(tx, req, now, "SEASON_NOT_ENDED");
      const seasonRef = db.doc(`arena_seasons/${quarter.id}`);
      const oldSeason = data(await tx.get(seasonRef));
      if (oldSeason && oldSeason.status === "FINALIZED") {
        return decision(tx, req, now, "APPLIED", oldSeason.result);
      }
      const members = await tx.get(db.collection(`arena_seasons/${quarter.id}/members`));
      const votes = await tx.get(db.collection(`arena_season_votes/${quarter.id}/votes`));
      const rows = votes.docs.map((doc) => doc.data());
      const ranked = policy.rankSeasonLessons({lessons: members.docs.map((doc) => doc.data()),
        ratings: rows.map((row) => row.vote), completions: rows.map((row) => row.completion), quarterAtMs: quarter.startMs});
      const reward = policy.planSeasonAward({season: ranked});
      const prepared = await prepareAward(tx, reward);
      const summary = (row) => row ? {lessonId: row.lessonId, authorUid: row.authorUid, publishedAtMs: row.publishedAtMs,
        ratingSum: row.ratingSum, ratingCount: row.ratingCount, weightedScore: row.weightedScore} : null;
      const winner = summary(ranked.winner);
      const result = {quarter, winner, eligibleLessons: ranked.ranked.length,
        rankingDigest: hash(ranked.ranked.map(summary)), awarded: prepared && !prepared.alreadyAwarded ? reward : null,
        alreadyAwarded: Boolean(prepared && prepared.alreadyAwarded)};
      applyAward(tx, prepared, now);
      tx.set(seasonRef, {quarter, status: "FINALIZED", finalizedAtMs: now, finalizedByUid: uid,
        policyVersion: policy.POLICY_VERSION, result}, {merge: true});
      return decision(tx, req, now, "APPLIED", result);
    });
  }
  return {submitLessonRating, transitionLesson, finalizeSeason};
}

module.exports = {createArenaService, ArenaServiceError, mutationDocumentId, awardDocumentId, voteDocumentId,
  AWARD_REASONS, LEDGER_PATH};
