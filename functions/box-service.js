"use strict";

/** Online-authoritative openings and bounded daily/offline accrual. Firebase comes as a
 * dependency. A permanent global operation receipt owns the id, account and original request;
 * retries return that receipt before random selection or premium advancement. No rewarded ads.
 * All monetary effects and both charge clocks share the premium transaction boundary.
 */
const crypto = require("node:crypto");
const {AD_BOX_CLOCK_FIELD, BONUS_CLOCK_FIELD, DAY_MS, readBoxState, advanceStreak, boxAccrualVerdict, boxOverclaimRecord} = require("./gift-boxes");
const {createGiftBoxEntropy, generateGiftBoxReward} = require("./gift-box-rewards");
const {trophyList} = require("./trophies");
const {SOURCE} = require("./premium-grants");
const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy, PREMIUM_CATCHUP_PASSES} = require("./premium-economy");
const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {SHORTFALL_FIELD} = require("./purchase-verification");
const {planGoldCredit} = require("./gold-credit");
const {CURRENCY, REASON, LEDGER_PATH, ledgerEntry} = require("./currency-ledger");

const BOX_OPERATIONS_COLLECTION = "box_operations";
const BOX_OVERCLAIM_PATH = "audit/box_overclaims/entries";
const BOX_KIND = Object.freeze({OPEN: "OPEN", VISIT: "VISIT", OFFLINE_STREAK: "OFFLINE_STREAK", PREMIUM_DAILY: "PREMIUM_DAILY"});

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function exact(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}
function add(a, b) { return exact(a + b, "balance or timestamp"); }
function stored(value, fallback = 0) { return value === undefined || value === null ? fallback : exact(value, "stored balance"); }
function hash(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function canonicalUid(uid) {
  if (typeof uid !== "string" || !uid || uid.length > 128 || uid !== uid.trim() || uid.includes("/")) fail("INVALID_ARGUMENT", "Authenticated uid required");
  return uid;
}

function normalizeBoxRequest(uid, payload) {
  canonicalUid(uid);
  if (!payload || typeof payload !== "object" || !Object.values(BOX_KIND).includes(payload.kind)) fail("INVALID_ARGUMENT", "Unknown box operation");
  if (payload.ownerUid !== uid) fail("PERMISSION_DENIED", "Box operation belongs to another account");
  const operationId = payload.kind === BOX_KIND.OPEN ? payload.openingId : payload.operationId;
  if (typeof operationId !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(operationId)) {
    fail("INVALID_ARGUMENT", "A persistent client operation/opening id is required");
  }
  const request = {kind: payload.kind, ownerUid: uid, operationId};
  if (request.kind === BOX_KIND.OFFLINE_STREAK) {
    if (!payload.base || !payload.claimed) fail("INVALID_ARGUMENT", "Offline accrual requires its saved server base and claim");
    request.base = {boxStreakDays: exact(payload.base.boxStreakDays, "base streak"), nextBoxAtMs: exact(payload.base.nextBoxAtMs, "base clock")};
    request.claimed = {boxStreakDays: exact(payload.claimed.boxStreakDays, "claimed streak"), boxesEarned: exact(payload.claimed.boxesEarned, "claimed boxes")};
  }
  return request;
}

function boxOperationId(operationId) {
  if (typeof operationId !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(operationId)) fail("INVALID_ARGUMENT", "Invalid box operation id");
  return hash(operationId);
}

function replayGate(receipt, uid, request, fingerprint) {
  if (!receipt) return null;
  if (receipt.uid !== uid) fail("PERMISSION_DENIED", "Box operation belongs to another account");
  if (receipt.schemaVersion !== 1 || receipt.operationId !== request.operationId || receipt.kind !== request.kind || receipt.fingerprint !== fingerprint || !receipt.response) {
    fail("CONFLICTING_REPLAY", "Box operation id is already bound to a different request or invalid receipt");
  }
  return receipt.response;
}

/** All reads precede all writes. entropy is trusted server material, NOT part of payload. */
async function readBoxOperation({db, transaction, uid, payload, entropy}) {
  const request = normalizeBoxRequest(uid, payload);
  const id = boxOperationId(request.operationId);
  const fingerprint = hash(JSON.stringify(request));
  const [receiptSnapshot, userSnapshot, configSnapshot] = await transaction.getAll(
    db.collection(BOX_OPERATIONS_COLLECTION).doc(id), db.collection("users").doc(uid), db.doc(ECONOMY_CONSTANTS_DOC));
  const replay = replayGate(receiptSnapshot.exists ? receiptSnapshot.data() : null, uid, request, fingerprint);
  if (replay) return {uid, request, id, fingerprint, replay};
  const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
  const constants = readEconomyConstants(configSnapshot.exists ? configSnapshot.data() : null);
  const reward = request.kind === BOX_KIND.OPEN ? generateGiftBoxReward(userData.trophies, entropy) : null;
  const premiumOperation = reward?.type === "datePremium" ?
    {kind: "GRANT", source: SOURCE.BOX, sourceId: id, days: reward.amount / 86400} : {kind: "ADVANCE"};
  const premiumSnapshot = await readPremiumEconomy({db, transaction, uid, operation: premiumOperation});
  return {uid, request, id, fingerprint, userData, constants, reward, premiumOperation, premiumSnapshot};
}

function strictBoxState(user) {
  const state = readBoxState(user);
  for (const [key, value] of Object.entries(state)) exact(value, key);
  return state;
}

/** Pure state/effect planning over the transaction snapshot and captured server time. */
function planBoxOperation({snapshot, now}) {
  exact(now, "server time");
  if (snapshot.replay) return {caughtUp: true, replayed: true, response: snapshot.replay};
  const {uid, request, id, fingerprint, userData, constants, reward, premiumSnapshot} = snapshot;
  let premiumPlan = planPremiumEconomy({snapshot: premiumSnapshot, userData, constants, now, operation: {kind: "ADVANCE"}});
  if (!premiumPlan.caughtUp) return {uid, caughtUp: false, replayed: false, premiumPlan, userPatch: premiumPlan.userPatch};
  const at = premiumPlan.header.asOfMs;
  const original = strictBoxState(userData);
  let boxes = original;
  let status = "NO_CHANGE";
  let earnedBoxes = 0;
  let openedBoxes = 0;
  let overclaim = null;
  let awardedReward = null;
  const rewardPatch = {};
  const ledger = [];
  const details = {operationId: request.operationId, operationKind: request.kind};
  const pushLedger = (currency, delta, suffix, extra = {}) => {
    const entry = ledgerEntry({uid, currency, delta, reason: REASON.GIFT_BOX, sourceId: `${id}_${suffix}`, atMs: at, details: {...details, ...extra}});
    if (entry) ledger.push(entry);
  };

  if (request.kind === BOX_KIND.VISIT || request.kind === BOX_KIND.OPEN) {
    const advanced = advanceStreak(userData, at);
    boxes = strictBoxState(advanced);
    earnedBoxes = advanced.grantedBoxes;
    status = earnedBoxes ? "CLAIMED" : "NO_CHANGE";
  } else if (request.kind === BOX_KIND.OFFLINE_STREAK) {
    if (request.base.boxStreakDays !== original.boxStreakDays || request.base.nextBoxAtMs !== original.nextBoxAtMs) {
      status = "STALE_BASE";
    } else {
      const verdict = boxAccrualVerdict({stored: userData, claimed: request.claimed, nowMs: at});
      // The original helper's zero-anchor `lastVisitMs=0` makes next.nextBoxAtMs point into 1970.
      // First server word must establish TODAY, not create decades of banked offline visits.
      boxes = original.nextBoxAtMs === 0 ? strictBoxState(advanceStreak(userData, at)) : strictBoxState(verdict.next);
      earnedBoxes = verdict.allowedBoxes;
      if (original.nextBoxAtMs === 0 && earnedBoxes !== 0) fail("INVALID_ARGUMENT", "First offline word cannot grant boxes");
      status = earnedBoxes ? "CLAIMED" : "NO_CHANGE";
      if (verdict.surplusBoxes || verdict.surplusStreak) {
        overclaim = {...boxOverclaimRecord({uid, stored: userData, claimed: request.claimed, verdict, recordedAtMs: at}),
          id: `${uid}_BOXES_${id}`, operationId: request.operationId};
      }
    }
  } else if (request.kind === BOX_KIND.PREMIUM_DAILY) {
    const nextBonusAt = stored(userData[BONUS_CLOCK_FIELD]);
    if (premiumPlan.header.totalRemainingMs <= 0) status = "PREMIUM_REQUIRED";
    else if (at < nextBonusAt) status = "DAILY_LIMIT";
    else {
      boxes = {...original, boxCount: add(original.boxCount, 1)};
      rewardPatch[BONUS_CLOCK_FIELD] = add(at, DAY_MS);
      // Магазинная коробка одна и на кнопку, и на 5 реклам (решение владельца 12.10): забрав её
      // кнопкой, закрываем слот и рекламе — её часы двигаются тем же шагом.
      rewardPatch[AD_BOX_CLOCK_FIELD] = add(at, DAY_MS);
      earnedBoxes = 1;
      status = "CLAIMED";
    }
  }

  if (earnedBoxes) pushLedger(CURRENCY.BOXES, earnedBoxes, "earned", {source: request.kind === BOX_KIND.PREMIUM_DAILY ? "PREMIUM_DAILY" : "STREAK"});

  if (request.kind === BOX_KIND.OPEN) {
    if (boxes.boxCount === 0) status = "NO_BOXES";
    else {
      boxes = {...boxes, boxCount: boxes.boxCount - 1};
      openedBoxes = 1;
      awardedReward = reward;
      status = "OPENED";
      pushLedger(CURRENCY.BOXES, -1, "opened", {reward});
      switch (reward.type) {
        case "addNolics": {
          const before = stored(userData.pointsNolics, stored(userData.nolics));
          const after = add(before, reward.amount);
          rewardPatch.pointsNolics = after;
          rewardPatch.nolics = after;
          pushLedger(CURRENCY.NOLICS, reward.amount, "reward", {reward});
          break;
        }
        case "addGold": {
          const credit = planGoldCredit({gold: stored(userData.gold), shortfall: stored(userData[SHORTFALL_FIELD]), gross: reward.amount});
          rewardPatch.gold = credit.goldAfter;
          rewardPatch[SHORTFALL_FIELD] = credit.shortfallAfter;
          details.goldCredit = credit;
          pushLedger(CURRENCY.GOLD, credit.net, "reward", {reward, shortfallCleared: credit.shortfallCleared, shortfallAfter: credit.shortfallAfter});
          break;
        }
        case "datePremium": {
          premiumPlan = planPremiumEconomy({snapshot: premiumSnapshot, userData, constants, now, operation: snapshot.premiumOperation});
          if (!premiumPlan.caughtUp || premiumPlan.outcome !== "GRANTED") fail("INCONSISTENT_PROVENANCE", "Box premium exists without its opening receipt");
          break;
        }
        case "logo": {
          const owned = Array.isArray(userData.ownedLogos) ? userData.ownedLogos.filter((name) => typeof name === "string" && name) : [];
          rewardPatch.ownedLogos = [...new Set([...owned, reward.itemName])];
          break;
        }
        case "trophy": rewardPatch.trophies = trophyList([...trophyList(userData.trophies), reward.itemName]); break;
        default: fail("INVALID_ARGUMENT", "Unknown server box reward");
      }
    }
  }

  const userPatch = {...premiumPlan.userPatch, ...boxes, ...rewardPatch};
  const response = {schemaVersion: 1, ownerUid: uid, operationId: request.operationId, kind: request.kind, status,
    ...boxes, nextBonusBoxAtMs: stored(userPatch[BONUS_CLOCK_FIELD], stored(userData[BONUS_CLOCK_FIELD])),
    earnedBoxes, openedBoxes, premiumUntilMs: premiumPlan.premiumUntilMs,
    ...(awardedReward ? {reward: awardedReward} : {}), ...(details.goldCredit ? {goldCredit: details.goldCredit} : {})};
  const receipt = {schemaVersion: 1, uid, operationId: request.operationId, kind: request.kind, fingerprint,
    createdAtMs: at, response};
  const userReceipt = {
    kind: openedBoxes ? "BOX_OPENED" : "BOX_ACCRUAL", messageKey: openedBoxes ? "receipt.box_opened" : "receipt.box_accrual",
    params: {status, earnedBoxes, openedBoxes, ...(awardedReward ? {reward: awardedReward} : {}),
      ...(details.goldCredit ? {goldCredit: details.goldCredit} : {})},
    amount: earnedBoxes - openedBoxes, currency: "BOXES", operationId: request.operationId, createdAtMs: at,
  };
  return {uid, id, caughtUp: true, replayed: false, premiumPlan, userPatch, response, receipt, userReceipt, ledger, overclaim};
}

/** Writes premium+box records/ledger, NOT the user; merge plan.userPatch in this transaction. */
function writeBoxOperation({db, transaction, plan}) {
  if (plan.replayed) return;
  writePremiumEconomy({db, transaction, uid: plan.uid, plan: plan.premiumPlan});
  if (!plan.caughtUp) return;
  transaction.create(db.collection(BOX_OPERATIONS_COLLECTION).doc(plan.id), plan.receipt);
  transaction.set(db.collection("users").doc(plan.uid).collection("receipts").doc(`box_${plan.id}`), plan.userReceipt);
  for (const entry of plan.ledger) transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
  if (plan.overclaim) transaction.set(db.doc(`${BOX_OVERCLAIM_PATH}/${plan.overclaim.id}`), plan.overclaim);
}

/** Actual standalone adapter; root callable supplies authenticated uid/server now. No live deps. */
async function applyBoxOperation({db, uid, payload, now, entropy = createGiftBoxEntropy()}) {
  normalizeBoxRequest(uid, payload);
  exact(now, "server time");
  for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
    const plan = await db.runTransaction(async (transaction) => {
      const snapshot = await readBoxOperation({db, transaction, uid, payload, entropy});
      const planned = planBoxOperation({snapshot, now});
      writeBoxOperation({db, transaction, plan: planned});
      if (!planned.replayed) transaction.set(db.collection("users").doc(uid), {uid, ...planned.userPatch}, {merge: true});
      return planned;
    });
    if (plan.caughtUp) return plan.response;
  }
  fail("UNAVAILABLE", "Premium catch-up made durable progress; retry the same box operation id");
}

module.exports = {BOX_OPERATIONS_COLLECTION, BOX_OVERCLAIM_PATH, BONUS_CLOCK_FIELD, BOX_KIND,
  normalizeBoxRequest, boxOperationId, readBoxOperation, planBoxOperation, writeBoxOperation, applyBoxOperation};
