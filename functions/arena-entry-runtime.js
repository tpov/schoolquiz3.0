"use strict";

const {randomBytes, createHmac, createHash} = require("node:crypto");
const {createArenaTickets, ArenaTicketError, nolicBalance} = require("./arena-tickets");
const {assignLessonPrices} = require("./arena-policy");
const {redact, STATUS: REDACTION, CONTENT_TYPE} = require("./question-redaction");
const {scoreAttempt} = require("./attempt-scoring");
const {attemptReceipt, attemptFingerprint, STATUS} = require("./attempt-receipt");
const {readEconomyConstants, activityPrice, ECONOMY_CONSTANTS_DOC, POINTS_PER_CHARGE} = require("./economy-constants");
const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy, PREMIUM_CATCHUP_PASSES} = require("./premium-economy");
const {POOL_SIZE, canonicalQuestionId, questionAllocatedSeconds, attemptReward} = require("./lesson-reward");
const {recomputePercentScore, attemptActivityCounts} = require("./result-verification");
const {settleClaims, noClaims, askedOrder} = require("./charge-claims");
const {CURRENCY, REASON, LEDGER_PATH, ledgerEntries} = require("./currency-ledger");

const MAX_ARENA_LESSONS = 1000;
const MAX_LESSON_ROWS = 1000;
const MAX_RESULT_BYTES = 500_000;
const CHECKPOINT = Symbol("premium checkpoint");
function fail(code, message = code) { throw new ArenaTicketError(code, message); }
function data(snapshot) { return snapshot.exists ? snapshot.data() || {} : null; }
function exact(value, fallback = 0) {
  const n = value == null ? fallback : value;
  if (!Number.isSafeInteger(n) || n < 0) fail("INVALID_STORED_BALANCE");
  return n;
}
function add(a, b) { return exact(exact(a) + exact(b)); }
function compare(a, b) { return a < b ? -1 : a > b ? 1 : 0; }
function eventPath(attemptId) {
  const hash = createHash("sha256").update(attemptId).digest("hex");
  const safe = `${hash.slice(0, 12)}_${attemptId.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80)}`;
  return `result_events_public/${hash.slice(0, 2)}/events/${safe}`;
}
function scoringInput(ticket, answers) {
  return {
    questions: ticket.questions.map((q) => ({...q.publicQuestion, id: q.questionId, lessonId: ticket.lessonId})),
    served: ticket.questions.map((q, codeAnswerIndex) => ({questionId: q.questionId, codeAnswerIndex})),
    keyDocument: {version: 1, lessonId: ticket.lessonId, publicHalfRedacted: true,
      keys: ticket.questions.filter((q) => q.gradingKey.kind !== "PARTICIPATION")
        .map((q) => ({questionId: q.questionId, key: q.gradingKey}))},
    answers,
  };
}
function bundleScorable(ticket) {
  // Empty answers bypass per-question key validation in the legacy scorer. A deliberately
  // malformed answer is a client fault, but first forces every frozen question/key to be read.
  const probes = ticket.questions.map((q, codeAnswerIndex) => ({questionId: q.questionId,
    codeAnswerIndex, answerPayload: null}));
  return scoreAttempt(scoringInput(ticket, probes)).scorable;
}

/** Actual Firestore ticket + E2 settlement boundary. No result is calculated from live content. */
function createArenaEntryRuntime({db, loadLessonContext, nowMs = () => Date.now()}) {
  async function constants(tx) {
    const snapshot = await tx.get(db.doc(ECONOMY_CONSTANTS_DOC));
    return readEconomyConstants(data(snapshot), 0);
  }
  async function regeneration(tx, {uid, user, nowMs: now}) {
    const rules = await constants(tx);
    const operation = {kind: "ADVANCE"};
    const snapshot = await readPremiumEconomy({db, transaction: tx, uid, operation});
    return {...planPremiumEconomy({snapshot, userData: user, constants: rules, now, operation}), uid};
  }
  function writeRegeneration(tx, plan) {
    writePremiumEconomy({db, transaction: tx, uid: plan.uid, plan});
  }
  async function arenaRows(tx) {
    const snapshot = await tx.get(db.collection("lesson_placements").where("shelf", "==", "arena").limit(MAX_ARENA_LESSONS + 1));
    if (snapshot.docs.length > MAX_ARENA_LESSONS) fail("ARENA_INDEX_REBUILD_REQUIRED");
    return snapshot.docs.map((doc) => ({...doc.data(), lessonId: doc.id})).sort((a, b) => compare(a.lessonId, b.lessonId));
  }
  function tickets() {
    // Fixed per callable invocation, so optimistic retries keep the same random ordering.
    const entropy = randomBytes(32);
    const rank = (key) => createHmac("sha256", entropy).update(key).digest("hex");
    const cachedEntries = new WeakMap();
    async function loadEntry(tx, {lessonId, difficulty}) {
      let entries = cachedEntries.get(tx);
      if (!entries) { entries = new Map(); cachedEntries.set(tx, entries); }
      const cacheKey = `${lessonId}:${difficulty}`;
      if (entries.has(cacheKey)) return entries.get(cacheKey);
      const context = await loadLessonContext(tx, lessonId);
      if (!context || !context.reviewApproved || !context.published || context.questType !== "REGULAR") fail("LESSON_NOT_ELIGIBLE");
      const rows = await arenaRows(tx);
      if (!rows.some((row) => row.lessonId === lessonId)) fail("LESSON_NOT_ELIGIBLE");
      const aggregates = await Promise.all(rows.map(async (row) => {
        const rating = data(await tx.get(db.doc(`lesson_ratings/${row.lessonId}`))) || {};
        return {lessonId: row.lessonId, ratingSum: exact(rating.ratingSum), ratingCount: exact(rating.ratingCount),
          ratingVersion: exact(rating.ratingVersion)};
      }));
      const price = assignLessonPrices(aggregates).find((row) => row.lessonId === lessonId);
      const snapshot = await tx.get(db.collection("questions").where("lessonId", "==", lessonId).limit(MAX_LESSON_ROWS + 1));
      if (snapshot.docs.length > MAX_LESSON_ROWS) fail("LESSON_BANK_TOO_LARGE");
      const keyDocument = data(await tx.get(db.doc(`question_keys/${lessonId}`)));
      const candidates = [];
      for (const doc of snapshot.docs) {
        const question = doc.data() || {};
        if (question.archived === true) continue;
        let content;
        try { content = JSON.parse(question.payload); } catch (_) { fail("UNSCORABLE_BANK"); }
        if (String(question.difficulty || content.difficulty || "EASY").toUpperCase() !== difficulty) continue;
        candidates.push({id: doc.id, question, content});
      }
      // Collapse translations before selecting the immutable 20-question bundle.
      candidates.sort((a, b) => compare(a.id, b.id));
      const unique = new Map();
      for (const row of candidates) {
        const canonical = canonicalQuestionId(row.id);
        if (!unique.has(canonical)) unique.set(canonical, row);
      }
      const selected = [...unique.values()].sort((a, b) => compare(rank(a.id), rank(b.id))).slice(0, POOL_SIZE);
      if (!selected.length) fail("NO_QUESTIONS_FOR_DIFFICULTY");
      const questions = selected.map(({id, question, content}, order) => {
        const split = redact(question.payload);
        let payload; let key;
        if (split.status === REDACTION.REDACTED) { payload = split.publicPayload; key = split.key; }
        else if (split.status === REDACTION.NOT_APPLICABLE && content.type === CONTENT_TYPE.SURVEY) {
          // Surveys retain participation scoring on Arena only. Tournament bank excludes them.
          payload = question.payload; key = {kind: "PARTICIPATION"};
        } else if (split.status === REDACTION.ALREADY_REDACTED && keyDocument?.publicHalfRedacted === true) {
          const matching = keyDocument.keys?.filter((entry) => entry.questionId === id) || [];
          if (matching.length !== 1 || keyDocument.lessonId !== lessonId || keyDocument.version !== 1) fail("UNSCORABLE_BANK");
          payload = question.payload; key = matching[0].key;
        } else fail("UNSCORABLE_BANK");
        const version = exact(question.version, context.contentVersion);
        if (!version) fail("UNSCORABLE_BANK");
        return {questionId: id, questionVersion: version, gradingKey: key,
          publicQuestion: {id, lessonId, version, difficulty, order, payload,
            language: typeof question.language === "string" ? question.language : ""}};
      });
      const rules = await constants(tx);
      const result = {lessonId, lessonVersion: context.contentVersion, difficulty, authorUid: context.authorUid,
        questType: "REGULAR", published: true, reviewApproved: true, shelf: "arena", priceNolics: price.priceNolics,
        ratingVersion: aggregates.find((row) => row.lessonId === lessonId).ratingVersion,
        lifeCost: activityPrice(rules, "ARENA"), questions, contentContext: {
          catalogId: context.catalogId, questId: context.questId, sectionId: context.sectionId, themeId: context.themeId,
        }};
      if (!bundleScorable(result)) fail("UNSCORABLE_BANK");
      if (Buffer.byteLength(JSON.stringify(result)) > MAX_RESULT_BYTES) fail("LESSON_BANK_TOO_LARGE");
      entries.set(cacheKey, result);
      return result;
    }
    return createArenaTickets({db, nowMs, readRegeneration: regeneration, writeRegeneration,
      loadEntrySnapshot: loadEntry,
      chooseRandomLesson: async (tx, args) => {
        const rows = (await arenaRows(tx)).sort((a, b) => compare(rank(a.lessonId), rank(b.lessonId)));
        for (const row of rows) {
          try { await loadEntry(tx, {...args, lessonId: row.lessonId}); return row.lessonId; }
          catch (error) { if (!["LESSON_NOT_ELIGIBLE", "NO_QUESTIONS_FOR_DIFFICULTY", "UNSCORABLE_BANK"].includes(error.code)) throw error; }
        }
        fail("NO_ELIGIBLE_ARENA_LESSON");
      },
    });
  }
  async function retryCheckpoints(action) {
    for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
      const result = await action();
      if (result !== CHECKPOINT && result.status !== "RETRY_REGEN") return result;
    }
    fail("RETRY_REGEN");
  }
  async function settle({uid, payload}) {
    if (!payload || typeof payload.ticketId !== "string" || typeof payload.attemptId !== "string" ||
        payload.ownerUid !== uid || !["COMPLETED", "ABORTED"].includes(payload.completionKind) ||
        !Array.isArray(payload.answers) || payload.answers.length > POOL_SIZE ||
        Buffer.byteLength(JSON.stringify(payload)) > MAX_RESULT_BYTES) fail("INVALID_ARGUMENT");
    const fingerprint = attemptFingerprint(uid, payload);
    const service = tickets();
    const ref = db.doc(eventPath(payload.attemptId));
    return retryCheckpoints(() => db.runTransaction(async (tx) => {
      const readset = await service.readTicketSettlement(tx, {uid, ticketId: payload.ticketId, attemptId: payload.attemptId});
      const ticket = readset.ticket;
      for (const field of ["lessonId", "lessonVersion", "difficulty"]) {
        if (payload[field] !== ticket[field]) fail("TICKET_ATTEMPT_MISMATCH");
      }
      const previous = data(await tx.get(ref));
      if (previous) {
        if (previous.userId !== uid || previous.requestFingerprint !== fingerprint) fail("SETTLEMENT_REUSE");
        if (previous.settlementReceipt?.status !== STATUS.SETTLED || ticket.status !== "SETTLED") fail("INCONSISTENT_SETTLEMENT");
        return previous.settlementResponse;
      }
      if (ticket.status === "CANCELLED") fail("TICKET_CANCELLED");
      if (ticket.status === "SETTLED") fail("INCONSISTENT_SETTLEMENT");
      if (payload.completionKind === "ABORTED") fail("EXPLICIT_CANCELLATION_REQUIRED");
      const now = exact(nowMs());
      if (!bundleScorable(ticket)) fail("UNSCORABLE_BANK");
      const scored = scoreAttempt(scoringInput(ticket, payload.answers));
      // Do not finalize a permanent result or release the hold on a server scoring gap.
      if (!scored.scorable) fail("UNSCORABLE_BANK");
      const mask = payload.chargeClaims == null ? noClaims(ticket.questions.length) : payload.chargeClaims;
      const allowed = ticket.difficulty === "HARD" ? /^[.P]*$/ : /^[.S]*$/;
      if (typeof mask !== "string" || mask.length !== ticket.questions.length || !allowed.test(mask)) fail("INVALID_ARGUMENT");
      const premium = await regeneration(tx, {uid, user: readset.user, nowMs: now});
      if (!premium.caughtUp) {
        writeRegeneration(tx, premium); tx.set(readset.userRef, premium.userPatch, {merge: true}); return CHECKPOINT;
      }
      // Full bundle supplies the denominator. An unpaid skip stays a shown wrong answer ('1'),
      // never an omitted question ('0'); paid plasma alone may turn it into a correct answer.
      const beforeClaims = [...scored.codeAnswer].map((digit, i) => mask[i] === "P" ? "1" : digit).join("");
      const claims = settleClaims(mask, beforeClaims, Math.floor(premium.userPatch.lifePoints / POINTS_PER_CHARGE),
        Math.floor(premium.userPatch.plasmaPoints / POINTS_PER_CHARGE), askedOrder(payload.answers));
      const codeAnswer = claims.codeAnswer;
      const percentScore = recomputePercentScore(codeAnswer);
      const event = {userId: uid, scope: "public", ownerUid: null, ...ticket.contentContext,
        attemptId: ticket.attemptId, ticketId: ticket.ticketId, lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion,
        difficulty: ticket.difficulty, completed: true, activityKind: "ARENA", sourceShelf: "arena", codeAnswer, percentScore};
      const bestKey = `${ticket.lessonId}:${ticket.difficulty}`;
      const lessonBest = {...readset.user.lessonBest};
      const allocatedSeconds = ticket.questions.reduce((sum, q) => sum + questionAllocatedSeconds(JSON.parse(q.publicQuestion.payload), ticket.difficulty === "HARD"), 0);
      const reward = attemptReward({previousBestPercent: exact(lessonBest[bestKey]), percent: percentScore,
        isHard: ticket.difficulty === "HARD", allocatedSeconds});
      lessonBest[bestKey] = Math.max(exact(lessonBest[bestKey]), percentScore);
      const plan = service.planTicketSettlement(readset, {...ticket, status: STATUS.SETTLED, completed: true,
        percentScore, scoreDigest: attemptFingerprint(uid, {fingerprint, bundleDigest: ticket.bundleDigest, codeAnswer})});
      const receipt = {...attemptReceipt({event, status: STATUS.SETTLED, settledAtMs: now,
        lifePointsSpent: ticket.lifeCost + claims.standardChargesPaid * POINTS_PER_CHARGE,
        plasmaPointsSpent: claims.plasmaChargesPaid * POINTS_PER_CHARGE, reward}),
        ticketId: ticket.ticketId, nolicsSpent: ticket.priceNolics};
      const counts = attemptActivityCounts(codeAnswer);
      const balance = add(nolicBalance(readset.user), reward.nolics);
      const skill = add(exact(readset.user.pointsSkill ?? readset.user.skillPoints), reward.skillPoints);
      const computedUser = {...readset.user, ...premium.userPatch, pointsNolics: balance, nolics: balance,
        pointsSkill: skill, skillPoints: skill, lessonBest,
        lifePoints: premium.userPatch.lifePoints - claims.standardChargesPaid * POINTS_PER_CHARGE,
        plasmaPoints: premium.userPatch.plasmaPoints - claims.plasmaChargesPaid * POINTS_PER_CHARGE,
        ratingCountQuestions: add(exact(readset.user.ratingCountQuestions), counts.questions),
        ratingCountTrueQuestion: add(exact(readset.user.ratingCountTrueQuestion), counts.correct),
        ratingQuiz: add(exact(readset.user.ratingQuiz), 1)};
      const userPatch = {...computedUser, ...service.mergeTicketPayerPatch(plan, computedUser)};
      const response = {accepted: 1, reward, lifePoints: userPatch.lifePoints, receipts: [receipt]};
      writeRegeneration(tx, premium);
      service.writeTicketSettlement(tx, plan);
      tx.set(readset.userRef, userPatch, {merge: true});
      tx.create(ref, {...event, receivedAtMs: now, requestFingerprint: fingerprint,
        lifeCharged: true, answers: payload.answers, chargeClaims: mask, unscorable: scored.unscorable,
        settlementReceipt: receipt, settlementResponse: response});
      for (const [reason, deltas] of [
        [REASON.ATTEMPT_REWARD, {[CURRENCY.NOLICS]: reward.nolics, [CURRENCY.SKILL_POINTS]: reward.skillPoints}],
        [REASON.CHARGE_CLAIM, {[CURRENCY.STANDARD_CHARGE_POINTS]: -claims.standardChargesPaid * POINTS_PER_CHARGE,
          [CURRENCY.PLASMA_CHARGE_POINTS]: -claims.plasmaChargesPaid * POINTS_PER_CHARGE}],
      ]) for (const entry of ledgerEntries({uid, reason, deltas, sourceId: ticket.attemptId, atMs: now})) tx.create(db.doc(`${LEDGER_PATH}/${entry.id}`), entry);
      return response;
    }));
  }
  return {
    issue: (input) => { const service = tickets(); return retryCheckpoints(() => service.issue(input)); },
    cancel: (input) => { const service = tickets(); return retryCheckpoints(() => service.cancel(input)); },
    settle,
  };
}

module.exports = {createArenaEntryRuntime, scoringInput, eventPath, MAX_ARENA_LESSONS};
