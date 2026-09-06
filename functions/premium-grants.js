"use strict";

/**
 * Timed premium, without Firebase, clocks, money arithmetic or sticky flags.
 *
 * Persist `state` inside the same server transaction as its purchase/opening receipt and debit.
 * Grant ids AND source ids must be server-derived (e.g. a purchase-token hash, gold mutation id,
 * box-opening receipt id). This module does not verify receipts or authorise callers.
 *
 * Lots are FIFO by successful issue order, including ties at the same millisecond. Elapsed time
 * is consumed before every issue/refund. A PLAY refund burns only that lot's remaining time;
 * consumed time and benefits already delivered are retained, with no debt or negative balance.
 * A retry after expiry/refund still finds its original lot: do not delete exhausted lots without
 * retaining equivalent immutable replay tombstones. All persisted fields are JSON-safe integers.
 *
 * REGENERATION CONTRACT
 * Every transition returns `elapsed`: the exact half-open wall-clock span advanced and the actual
 * active subinterval(s) BEFORE the mutation. Settle BOTH charge balances using that span in the
 * SAME transaction before persisting premium. `regeneratePremiumPoints` enforces matching cursors,
 * applies x2 only in those intervals, retains integer work remainder and discards excess at cap.
 * Pass the returned balance straight to the next call; do not feed its cursor into the old
 * timestamp-only regeneration function, which cannot represent a premium work remainder.
 *
 * At migration, first settle old balances to the migration instant using whatever trusted history
 * exists, then align both charge cursors to state.asOfMs. This module deliberately does NOT infer
 * historical premium from a sticky bool or apply today's status to the entire old idle gap.
 * Rate/cap changes and spends also require settling first. If storing lots separately, transaction
 * reads must preserve FIFO order and atomicity; this aggregate is not an unbounded Firestore
 * document storage recommendation. Archive receipts only with a permanent replay index.
 */

const DAY_MS = 86_400_000;
const SCHEMA_VERSION = 1;
const PREMIUM_DAYS = Object.freeze([1, 7, 30, 365]);
const SOURCE = Object.freeze({PLAY: "PLAY", GOLD: "GOLD", BOX: "BOX", LEGACY: "LEGACY"});
const LEGACY_GRANT_ID = "legacy-premium-v1";

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function integer(value, label, code = "INVALID_ARGUMENT") {
  if (!Number.isSafeInteger(value) || value < 0) fail(code, `${label} must be a nonnegative safe integer`);
  return value;
}

function identifier(value, label, code = "INVALID_ARGUMENT") {
  if (typeof value !== "string" || !value.trim() || value !== value.trim() || value.length > 1024) {
    fail(code, `${label} must be a nonblank canonical identifier of at most 1024 characters`);
  }
  return value;
}

function add(left, right, label) {
  const result = left + right;
  if (!Number.isSafeInteger(result)) fail("OVERFLOW", `${label} exceeds exact integer range`);
  return result;
}

function remaining(lot) {
  return lot.durationMs - lot.consumedMs - lot.revokedMs;
}

function durationMsForDays(days) {
  if (!PREMIUM_DAYS.includes(days)) fail("INVALID_ARGUMENT", "Premium duration must be 1, 7, 30 or 365 days");
  return days * DAY_MS;
}

function createPremiumState(nowMs) {
  return {schemaVersion: SCHEMA_VERSION, asOfMs: integer(nowMs, "nowMs"), lots: [], legacyBootstrap: null};
}

/** Strict persistence boundary: corrupt data must not silently erase or mint purchased time. */
function copyState(input) {
  if (!input || input.schemaVersion !== SCHEMA_VERSION || !Array.isArray(input.lots)) {
    fail("INVALID_STATE", "Unsupported premium state");
  }
  const state = createPremiumState(integer(input.asOfMs, "asOfMs", "INVALID_STATE"));
  const ids = new Set();
  const sources = new Set();
  let previousIssuedAtMs = 0;
  let pendingEarlier = false;
  for (const entry of input.lots) {
    if (!entry || !Object.values(SOURCE).includes(entry.source)) fail("INVALID_STATE", "Invalid grant source");
    const lot = {
      grantId: identifier(entry.grantId, "grantId", "INVALID_STATE"),
      source: entry.source,
      sourceId: identifier(entry.sourceId, "sourceId", "INVALID_STATE"),
      issuedAtMs: integer(entry.issuedAtMs, "issuedAtMs", "INVALID_STATE"),
      durationMs: integer(entry.durationMs, "durationMs", "INVALID_STATE"),
      consumedMs: integer(entry.consumedMs, "consumedMs", "INVALID_STATE"),
      revokedMs: integer(entry.revokedMs, "revokedMs", "INVALID_STATE"),
      refundedAtMs: entry.refundedAtMs === null ? null : integer(entry.refundedAtMs, "refundedAtMs", "INVALID_STATE"),
    };
    const sourceKey = JSON.stringify([lot.source, lot.sourceId]);
    if (ids.has(lot.grantId) || sources.has(sourceKey)) fail("INVALID_STATE", "Duplicate grant provenance");
    ids.add(lot.grantId);
    sources.add(sourceKey);
    if (lot.durationMs === 0 || remaining(lot) < 0 ||
        lot.issuedAtMs < previousIssuedAtMs || lot.issuedAtMs > state.asOfMs ||
        lot.consumedMs > state.asOfMs - lot.issuedAtMs || (pendingEarlier && lot.consumedMs > 0) ||
        (lot.grantId === LEGACY_GRANT_ID && lot.source !== SOURCE.LEGACY) ||
        (lot.source !== SOURCE.LEGACY && !PREMIUM_DAYS.some((days) => days * DAY_MS === lot.durationMs)) ||
        (lot.refundedAtMs === null && lot.revokedMs !== 0) ||
        (lot.refundedAtMs !== null && (lot.source !== SOURCE.PLAY || remaining(lot) !== 0 ||
          lot.refundedAtMs < lot.issuedAtMs || lot.refundedAtMs > state.asOfMs ||
          lot.consumedMs > lot.refundedAtMs - lot.issuedAtMs))) {
      fail("INVALID_STATE", "Inconsistent grant accounting");
    }
    previousIssuedAtMs = lot.issuedAtMs;
    pendingEarlier = pendingEarlier || remaining(lot) > 0;
    state.lots.push(lot);
  }
  if (input.legacyBootstrap !== null) {
    const marker = input.legacyBootstrap;
    if (!marker || typeof marker !== "object") fail("INVALID_STATE", "Invalid legacy bootstrap marker");
    state.legacyBootstrap = {
      atMs: integer(marker.atMs, "legacyBootstrap.atMs", "INVALID_STATE"),
      premiumUntilMs: integer(marker.premiumUntilMs, "legacyBootstrap.premiumUntilMs", "INVALID_STATE"),
      grantId: marker.grantId,
    };
    const legacy = state.lots.filter((lot) => lot.source === SOURCE.LEGACY);
    const duration = Math.max(0, marker.premiumUntilMs - marker.atMs);
    if (marker.atMs > state.asOfMs || state.lots.some((lot) => lot.issuedAtMs < marker.atMs) ||
        (duration === 0 && (marker.grantId !== null || legacy.length !== 0)) ||
        (duration > 0 && (marker.grantId !== LEGACY_GRANT_ID || legacy.length !== 1 ||
          legacy[0].grantId !== LEGACY_GRANT_ID || legacy[0].sourceId !== LEGACY_GRANT_ID ||
          legacy[0].issuedAtMs !== marker.atMs ||
          legacy[0].durationMs !== duration || state.lots[0] !== legacy[0]))) {
      fail("INVALID_STATE", "Legacy lot does not match its bootstrap marker");
    }
  } else if (state.lots.some((lot) => lot.source === SOURCE.LEGACY || lot.grantId === LEGACY_GRANT_ID)) {
    fail("INVALID_STATE", "Legacy grant has no bootstrap marker");
  }
  entitlement(state); // Also reject overflow before any transition.
  return state;
}

function entitlement(state) {
  const remainingMs = state.lots.reduce((total, lot) => add(total, remaining(lot), "Remaining premium time"), 0);
  return {
    asOfMs: state.asOfMs,
    remainingMs,
    hasPremium: remainingMs > 0,
    premiumUntilMs: remainingMs > 0 ? add(state.asOfMs, remainingMs, "Premium expiry") : 0,
  };
}

function result(state, elapsed, extra = {}) {
  return {state, entitlement: entitlement(state), elapsed, ...extra};
}

function advancePremium(input, nowMs) {
  const state = copyState(input);
  const fromMs = state.asOfMs;
  const toMs = Math.max(fromMs, integer(nowMs, "nowMs"));
  let unspentElapsed = toMs - fromMs;
  let activeMs = 0;
  for (const lot of state.lots) {
    const used = Math.min(remaining(lot), unspentElapsed);
    lot.consumedMs += used;
    unspentElapsed -= used;
    activeMs += used;
    if (unspentElapsed === 0) break;
  }
  state.asOfMs = toMs;
  return result(state, {
    fromMs,
    toMs,
    activeIntervals: activeMs > 0 ? [{fromMs, toMs: fromMs + activeMs}] : [],
  });
}

/** LEGACY grants are deliberately issued only by bootstrapLegacyPremium, never by a purchase. */
function issuePremium(input, request, nowMs) {
  if (!request || ![SOURCE.PLAY, SOURCE.GOLD, SOURCE.BOX].includes(request.source)) {
    fail("INVALID_ARGUMENT", "A new grant must originate from PLAY, GOLD or BOX");
  }
  const grantId = identifier(request.grantId, "grantId");
  const sourceId = identifier(request.sourceId, "sourceId");
  if (grantId === LEGACY_GRANT_ID) fail("INVALID_ARGUMENT", "Legacy grant id is reserved");
  const durationMs = durationMsForDays(request.days);
  const advanced = advancePremium(input, nowMs);
  const {state, elapsed} = advanced;
  const existing = state.lots.find((lot) => lot.grantId === grantId);
  if (existing) {
    if (existing.source !== request.source || existing.sourceId !== sourceId || existing.durationMs !== durationMs) {
      fail("CONFLICTING_REPLAY", "Grant id was already used for a different entitlement");
    }
    return result(state, elapsed, {outcome: "REPLAYED", grantId});
  }
  if (state.lots.some((lot) => lot.source === request.source && lot.sourceId === sourceId)) {
    fail("SOURCE_ALREADY_GRANTED", "The source receipt already issued premium under another grant id");
  }
  state.lots.push({
    grantId, source: request.source, sourceId, issuedAtMs: state.asOfMs, durationMs,
    consumedMs: 0, revokedMs: 0, refundedAtMs: null,
  });
  return result(state, elapsed, {outcome: "GRANTED", grantId});
}

function refundPlayPremium(input, grantId, nowMs) {
  identifier(grantId, "grantId");
  const {state, elapsed} = advancePremium(input, nowMs);
  const lot = state.lots.find((entry) => entry.grantId === grantId);
  if (!lot) fail("GRANT_NOT_FOUND", "Cannot refund an unknown premium grant");
  if (lot.source !== SOURCE.PLAY) fail("NOT_PLAY_GRANT", "Play cannot revoke another source's premium");
  if (lot.refundedAtMs !== null) {
    return result(state, elapsed, {outcome: "REPLAYED", grantId, revokedNowMs: 0});
  }
  const revokedNowMs = remaining(lot);
  lot.revokedMs = revokedNowMs;
  lot.refundedAtMs = state.asOfMs;
  return result(state, elapsed, {outcome: "REFUNDED", grantId, revokedNowMs});
}

/**
 * Call BEFORE issuing the first new-system grant, for old and new accounts alike. The marker is
 * written even when there is no future entitlement. Later legacy snapshots (including an expiry
 * mirror already extended by new purchases) are ignored. `hasPremium` is intentionally unread.
 */
function bootstrapLegacyPremium(input, legacy, nowMs) {
  const {state, elapsed} = advancePremium(input, nowMs);
  if (state.legacyBootstrap !== null) return result(state, elapsed, {outcome: "REPLAYED"});
  if (state.lots.length !== 0) fail("LEGACY_MUST_BE_FIRST", "Bootstrap legacy before issuing new grants");
  const until = legacy && legacy.premiumUntilMs !== undefined && legacy.premiumUntilMs !== null ?
    integer(legacy.premiumUntilMs, "legacy.premiumUntilMs") : 0;
  const durationMs = Math.max(0, until - state.asOfMs);
  state.legacyBootstrap = {atMs: state.asOfMs, premiumUntilMs: until, grantId: durationMs > 0 ? LEGACY_GRANT_ID : null};
  if (durationMs > 0) {
    state.lots.push({
      grantId: LEGACY_GRANT_ID, source: SOURCE.LEGACY, sourceId: LEGACY_GRANT_ID,
      issuedAtMs: state.asOfMs, durationMs, consumedMs: 0, revokedMs: 0, refundedAtMs: null,
    });
  }
  return result(state, elapsed, {outcome: "BOOTSTRAPPED"});
}

/**
 * Return an ordinary/plasma balance from one exact entitlement transition. `remainderMs` is
 * accumulated BASE-RATE work, not wall-clock time; each premium millisecond adds two work units.
 * BigInt is used only internally to prevent intermediate overflow; the output remains plain JSON.
 * Zero baseIntervalMs disables regeneration AND drops idle accrual, avoiding later retroactive gain.
 */
function regeneratePremiumPoints(balance, elapsed, maxPoints, baseIntervalMs) {
  if (!balance || !elapsed || !Array.isArray(elapsed.activeIntervals)) fail("INVALID_ARGUMENT", "Balance and elapsed span are required");
  const points = integer(balance.points, "points");
  const updatedAtMs = integer(balance.updatedAtMs, "updatedAtMs");
  const remainderMs = integer(balance.remainderMs, "remainderMs");
  integer(maxPoints, "maxPoints");
  integer(baseIntervalMs, "baseIntervalMs");
  const fromMs = integer(elapsed.fromMs, "elapsed.fromMs");
  const toMs = integer(elapsed.toMs, "elapsed.toMs");
  if (fromMs !== updatedAtMs || toMs < fromMs) fail("CURSOR_MISMATCH", "Regeneration must consume the exact premium transition span");
  let previousEnd = fromMs;
  let premiumMs = 0n;
  for (const interval of elapsed.activeIntervals) {
    if (!interval) fail("INVALID_ARGUMENT", "Invalid premium interval");
    const start = integer(interval.fromMs, "interval.fromMs");
    const end = integer(interval.toMs, "interval.toMs");
    if (start < previousEnd || end <= start || end > toMs) fail("INVALID_ARGUMENT", "Premium intervals must be ordered, non-overlapping and inside the span");
    premiumMs += BigInt(end - start);
    previousEnd = end;
  }
  if (baseIntervalMs > 0 && remainderMs >= baseIntervalMs) fail("INVALID_ARGUMENT", "Remainder must be smaller than the base interval; settle before a rate change");
  if (points >= maxPoints || baseIntervalMs === 0) return {points, updatedAtMs: toMs, remainderMs: 0};
  const work = BigInt(remainderMs) + BigInt(toMs - fromMs) + premiumMs;
  const gained = work / BigInt(baseIntervalMs);
  const room = BigInt(maxPoints - points);
  if (gained >= room) return {points: maxPoints, updatedAtMs: toMs, remainderMs: 0};
  return {
    points: points + Number(gained),
    updatedAtMs: toMs,
    remainderMs: Number(work % BigInt(baseIntervalMs)),
  };
}

module.exports = {
  DAY_MS, SCHEMA_VERSION, PREMIUM_DAYS, SOURCE, LEGACY_GRANT_ID,
  durationMsForDays, createPremiumState, advancePremium, issuePremium,
  refundPlayPremium, bootstrapLegacyPremium, regeneratePremiumPoints,
};
