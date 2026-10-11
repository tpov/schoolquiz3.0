"use strict";

/**
 * Firestore-neutral premium persistence/charge integration. All reads precede all writes.
 *
 * premium_accounts/{uid}: O(1) header; lots/{grantId}: one fixed-size provenance/tombstone doc.
 * Active lots are queried by sequence, 64 + one lookahead. An exhausted prefix is committed as
 * a TIME-ONLY checkpoint before the purchase/refund is retried. There is no maximum number of
 * purchases and no read-budget cliff that permanently strands a refund. Only active prefixes,
 * the target receipt and the legacy tombstone are read, never the entire purchase history.
 *
 * Header writes serialize all issuers/refunds for one account. The query requires a Firestore
 * composite index on lots(active ASC, sequence ASC). Deny all client access to premium_accounts.
 * Exhausted lot docs remain forever for provenance/replay; archiving needs equivalent tombstones.
 *
 * The caller writes plan.userPatch with its other user changes, and calls writePremiumEconomy
 * inside the SAME transaction. When !caughtUp, commit that checkpoint only; no purchase receipt,
 * notification acknowledgement or debit may be committed yet. Retry makes durable FIFO progress.
 */

const crypto = require("crypto");
const {LEDGER_PATH, CURRENCY, REASON} = require("./currency-ledger");
const {
  LEGACY_GRANT_ID, SOURCE, createPremiumState, bootstrapLegacyPremium,
  advancePremium, issuePremium, refundPlayPremium, regeneratePremiumPoints,
} = require("./premium-grants");

const PREMIUM_ACCOUNTS_COLLECTION = "premium_accounts";
const PREMIUM_LOT_BATCH = 64;
const PREMIUM_CATCHUP_PASSES = 4;
const PREMIUM_CATCHUP = "PREMIUM_CATCHUP";
// Словарь премиум-строк книги берётся из закрытых списков currency-ledger, а не своим набором
// строк: сверка читает закрытый список, и премия, припишущая себя своим словарём, стала бы
// движением, которого в нём некому объяснить.
const PREMIUM_LEDGER_CURRENCY = CURRENCY.PREMIUM_MS;
const PREMIUM_LEDGER_REASONS = Object.freeze({
  GRANT: REASON.PREMIUM_GRANT, REFUND: REASON.PREMIUM_REFUND, CONSUME: REASON.PREMIUM_TIME,
  LEGACY: REASON.PREMIUM_LEGACY, REGEN: REASON.PREMIUM_REGEN,
});

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function exact(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_PREMIUM_STATE", `${label} must be an exact nonnegative integer`);
  return value;
}
function safeAdd(a, b) { return exact(a + b, "premium total"); }
function storedInt(value, fallback) { return value === undefined || value === null ? fallback : exact(value, "stored balance"); }
function left(lot) { return lot.durationMs - lot.consumedMs - lot.revokedMs; }
function hash(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function premiumGrantId(source, sourceId) {
  if (![SOURCE.PLAY, SOURCE.GOLD, SOURCE.BOX].includes(source) || typeof sourceId !== "string" || !sourceId) {
    fail("INVALID_ARGUMENT", "Premium provenance requires source and a server-derived receipt id");
  }
  return `${source.toLowerCase()}_${hash(sourceId)}`;
}
function targetOf(operation) {
  if (!operation || operation.kind === "ADVANCE") return null;
  if (operation.kind === "GRANT") return premiumGrantId(operation.source, operation.sourceId);
  if (operation.kind === "REFUND" && typeof operation.grantId === "string" && /^[a-z]+_[a-f0-9]{64}$/.test(operation.grantId)) return operation.grantId;
  fail("INVALID_ARGUMENT", "Invalid premium operation");
}

function validateStoredIdentity(id, value) {
  if (!value || value.grantId !== id || typeof value.active !== "boolean" || value.active !== (left(value) > 0)) {
    fail("INVALID_PREMIUM_STATE", "Malformed premium lot identity or active marker");
  }
  if (id === LEGACY_GRANT_ID) {
    if (value.source !== SOURCE.LEGACY || value.sourceId !== LEGACY_GRANT_ID) fail("INVALID_PREMIUM_STATE", "Malformed legacy provenance");
  } else if (premiumGrantId(value.source, value.sourceId) !== id) {
    fail("INVALID_PREMIUM_STATE", "Premium lot id does not match source receipt");
  }
}

async function readPremiumEconomy({db, transaction, uid, operation}) {
  const accountRef = db.collection(PREMIUM_ACCOUNTS_COLLECTION).doc(uid);
  const lotsRef = accountRef.collection("lots");
  const targetId = targetOf(operation);
  const refs = [accountRef, lotsRef.doc(LEGACY_GRANT_ID)];
  if (targetId) refs.push(lotsRef.doc(targetId));
  const docs = await transaction.getAll(...refs);
  const query = await transaction.get(lotsRef.where("active", "==", true).orderBy("sequence").limit(PREMIUM_LOT_BATCH + 1));
  const records = new Map();
  const prefix = query.docs.slice(0, PREMIUM_LOT_BATCH).map((doc) => {
    const value = doc.data();
    validateStoredIdentity(doc.id, value);
    if (!value.active) fail("INVALID_PREMIUM_STATE", "Malformed active premium lot");
    records.set(value.grantId, value);
    return value;
  });
  const legacy = docs[1].exists ? docs[1].data() : null;
  const target = targetId && docs[2].exists ? docs[2].data() : null;
  if (legacy) {
    validateStoredIdentity(LEGACY_GRANT_ID, legacy);
    records.set(LEGACY_GRANT_ID, legacy);
  }
  if (target) {
    validateStoredIdentity(targetId, target);
    records.set(targetId, target);
  }
  return {
    header: docs[0].exists ? docs[0].data() : null,
    prefix, legacy, target, targetId, records, hasMore: query.docs.length > PREMIUM_LOT_BATCH,
  };
}

/** Apply x2 to exact elapsed intervals for both charge types, preserving cap and fractional work. */
function chargeBalances(user, constants, elapsed, migrating, previousRates, activateRates) {
  const patch = {};
  const movements = [];
  const rates = {};
  const sides = [
    {key: "standard", rules: constants.standard, slots: "standardHearts", defaultSlots: 5, reserved: "reservedLifePoints", points: "lifePoints", cursor: "lifePointsUpdatedAtMs", remainder: "lifeRegenRemainderMs", currency: CURRENCY.STANDARD_CHARGE_POINTS},
    {key: "plasma", rules: constants.plasma, slots: "goldHearts", defaultSlots: 0, reserved: "reservedPlasmaPoints", points: "plasmaPoints", cursor: "plasmaPointsUpdatedAtMs", remainder: "plasmaRegenRemainderMs", currency: CURRENCY.PLASMA_CHARGE_POINTS},
  ];
  for (const side of sides) {
    const capacity = Math.min(storedInt(user[side.slots], side.defaultSlots), side.rules.maxOwned) * 100;
    // Reserved offline entry tickets still own their held points: they occupy capacity until
    // spent/refunded. Regenerating the available balance to full capacity would mint them twice.
    const ceiling = Math.max(0, exact(capacity, "charge capacity") - storedInt(user[side.reserved], 0));
    const points = storedInt(user[side.points], ceiling);
    const cursor = storedInt(user[side.cursor], elapsed.fromMs);
    const configuredInterval = exact(Math.floor(side.rules.regenMs / 100), "charge interval");
    const previousInterval = previousRates ? exact(previousRates[side.key], "stored charge interval") : configuredInterval;
    const interval = activateRates ? configuredInterval : previousInterval;
    rates[side.key] = interval;
    const span = migrating ? {fromMs: cursor, toMs: elapsed.toMs, activeIntervals: []} : elapsed;
    const balance = regeneratePremiumPoints({
      points, updatedAtMs: cursor, remainderMs: storedInt(user[side.remainder], 0),
    }, span, exact(ceiling, "charge ceiling"), previousInterval);
    // A config edit takes effect at this server-observed boundary, not retroactively over idle
    // time. Remainder is earned integer work (not a money fraction); spend it at the new point
    // cost. Disabling regen or reaching cap discards banking, as in the pure-core contract.
    if (interval !== previousInterval) {
      if (interval === 0 || balance.points >= ceiling) balance.remainderMs = 0;
      else {
        const gained = Math.floor(balance.remainderMs / interval);
        const room = ceiling - balance.points;
        balance.points += Math.min(gained, room);
        balance.remainderMs = gained >= room ? 0 : balance.remainderMs % interval;
      }
    }
    patch[side.points] = balance.points;
    patch[side.cursor] = balance.updatedAtMs;
    patch[side.remainder] = balance.remainderMs;
    if (balance.points !== points) movements.push({currency: side.currency, delta: balance.points - points, fromMs: cursor});
  }
  return {patch, movements, rates};
}

/**
 * Pure planning. Migration preserves only the future legacy expiry; historic charge idle time
 * receives base-rate regeneration because no trusted historic premium start interval exists.
 * Once migrated, charge cursors MUST equal the header cursor; bypassing this module fails closed.
 */
function planPremiumEconomy({snapshot, userData, constants, now, operation = {kind: "ADVANCE"}}) {
  exact(now, "now");
  const migrating = !snapshot.header;
  let header;
  let state;
  let nextSequence;
  const sequences = new Map();
  if (migrating) {
    if (snapshot.records.size || snapshot.hasMore) fail("INVALID_PREMIUM_STATE", "Premium lots exist without their header");
    const at = Math.max(now, storedInt(userData.lifePointsUpdatedAtMs, now), storedInt(userData.plasmaPointsUpdatedAtMs, now));
    const migrated = bootstrapLegacyPremium(createPremiumState(at), {premiumUntilMs: userData.premiumUntilMs}, at);
    state = migrated.state;
    nextSequence = state.lots.length;
    if (state.lots.length) sequences.set(LEGACY_GRANT_ID, 0);
    header = {schemaVersion: 1, asOfMs: at, totalRemainingMs: migrated.entitlement.remainingMs, nextSequence, legacyBootstrap: state.legacyBootstrap};
  } else {
    header = {...snapshot.header};
    if (header.schemaVersion !== 1 || header.legacyBootstrap === null || !header.legacyBootstrap) fail("INVALID_PREMIUM_STATE", "Invalid premium header version or bootstrap marker");
    exact(header.asOfMs, "premium cursor");
    exact(header.totalRemainingMs, "total remaining");
    nextSequence = exact(header.nextSequence, "next sequence");
    const occupiedSequences = new Set();
    for (const [id, lot] of snapshot.records) {
      if (exact(lot.sequence, "lot sequence") >= nextSequence) fail("INVALID_PREMIUM_STATE", "Lot sequence exceeds header");
      if (occupiedSequences.has(lot.sequence)) fail("INVALID_PREMIUM_STATE", "Duplicate premium FIFO sequence");
      occupiedSequences.add(lot.sequence);
      sequences.set(id, lot.sequence);
    }
    const prefixIds = new Set(snapshot.prefix.map((lot) => lot.grantId));
    const loaded = [...snapshot.prefix];
    // Always include the one legacy tombstone: pure-core validation needs its immutable provenance.
    if (snapshot.legacy && !prefixIds.has(LEGACY_GRANT_ID)) loaded.push(snapshot.legacy);
    loaded.sort((a, b) => a.sequence - b.sequence);
    state = {schemaVersion: 1, asOfMs: header.asOfMs, lots: loaded, legacyBootstrap: header.legacyBootstrap};
    const prefixRemaining = snapshot.prefix.reduce((sum, lot) => safeAdd(sum, left(lot)), 0);
    if (prefixRemaining > header.totalRemainingMs || (!snapshot.hasMore && prefixRemaining !== header.totalRemainingMs)) {
      fail("INVALID_PREMIUM_STATE", "Active lots disagree with premium header");
    }
  }

  const effectiveNow = Math.max(now, header.asOfMs);
  const prefixMs = state.lots.reduce((sum, lot) => safeAdd(sum, left(lot)), 0);
  const advanceTo = snapshot.hasMore && effectiveNow - header.asOfMs > prefixMs ?
    safeAdd(header.asOfMs, prefixMs) : effectiveNow;
  if (snapshot.hasMore && advanceTo < effectiveNow && advanceTo === header.asOfMs) fail("INVALID_PREMIUM_STATE", "Empty premium prefix cannot make catch-up progress");
  const advanced = advancePremium(state, advanceTo);
  const consumedMs = Math.min(prefixMs, advanceTo - header.asOfMs);
  let totalRemainingMs = header.totalRemainingMs - consumedMs;
  const caughtUp = advanceTo === effectiveNow;
  state = advanced.state;
  const charges = chargeBalances(userData, constants, advanced.elapsed, migrating, header.chargeRates, caughtUp);
  let grant = null;
  let revokedNowMs = 0;
  let outcome = caughtUp ? "ADVANCED" : PREMIUM_CATCHUP;

  if (caughtUp && operation.kind !== "ADVANCE") {
    // A queued target beyond the loaded prefix cannot have consumed new time while earlier lots
    // remain. Insert it AFTER elapsed consumption, solely for exact-source replay/refund checks.
    if (snapshot.target && !state.lots.some((lot) => lot.grantId === snapshot.target.grantId)) {
      state.lots.push(snapshot.target);
      state.lots.sort((a, b) => sequences.get(a.grantId) - sequences.get(b.grantId));
    }
    let changed;
    if (operation.kind === "GRANT") {
      changed = issuePremium(state, {
        grantId: snapshot.targetId, source: operation.source, sourceId: operation.sourceId, days: operation.days,
      }, advanceTo);
      if (changed.outcome === "GRANTED") {
        grant = changed.state.lots.find((lot) => lot.grantId === snapshot.targetId);
        sequences.set(grant.grantId, nextSequence);
        nextSequence = safeAdd(nextSequence, 1);
        totalRemainingMs = safeAdd(totalRemainingMs, grant.durationMs);
      }
    } else if (operation.kind === "REFUND") {
      changed = refundPlayPremium(state, operation.grantId, advanceTo);
      revokedNowMs = changed.revokedNowMs;
      totalRemainingMs -= revokedNowMs;
      grant = changed.state.lots.find((lot) => lot.grantId === operation.grantId);
    } else fail("INVALID_ARGUMENT", "Unsupported premium operation");
    state = changed.state;
    outcome = changed.outcome;
  }
  exact(totalRemainingMs, "remaining premium");
  const premiumUntilMs = totalRemainingMs > 0 ? safeAdd(advanceTo, totalRemainingMs) : 0;
  const nextHeader = {
    schemaVersion: 1, asOfMs: advanceTo, totalRemainingMs, nextSequence,
    legacyBootstrap: header.legacyBootstrap,
    chargeRates: charges.rates,
  };
  const lots = state.lots.map((lot) => ({...lot, sequence: sequences.get(lot.grantId), active: left(lot) > 0}))
    .filter((lot) => JSON.stringify(lot) !== JSON.stringify(snapshot.records.get(lot.grantId)));
  return {
    caughtUp, outcome, header: nextHeader, lots, consumedMs, revokedNowMs, grant,
    premiumUntilMs, premiumGrantedMs: outcome === "GRANTED" ? grant.durationMs : 0,
    legacyGrantedMs: migrating ? header.totalRemainingMs : 0,
    elapsed: advanced.elapsed, chargeMovements: charges.movements,
    userPatch: {...charges.patch, hasPremium: totalRemainingMs > 0, premiumUntilMs, updatedAtMs: advanceTo},
  };
}

function row(uid, currency, reason, sourceId, delta, atMs, details) {
  if (!delta) return null;
  return {id: `${uid}_${currency}_${reason}_${sourceId}`, uid, currency, delta, reason, sourceId, atMs, details};
}

/** Writes only premium header/lots/ledger; caller must atomically merge plan.userPatch as well. */
function writePremiumEconomy({db, transaction, uid, plan}) {
  const ref = db.collection(PREMIUM_ACCOUNTS_COLLECTION).doc(uid);
  transaction.set(ref, plan.header);
  for (const lot of plan.lots) transaction.set(ref.collection("lots").doc(lot.grantId), lot);
  const at = plan.header.asOfMs;
  const clockId = `${plan.elapsed.fromMs}_${at}`;
  const rows = [
    row(uid, PREMIUM_LEDGER_CURRENCY, PREMIUM_LEDGER_REASONS.CONSUME, clockId, -plan.consumedMs, at, {elapsed: plan.elapsed}),
    row(uid, PREMIUM_LEDGER_CURRENCY, PREMIUM_LEDGER_REASONS.LEGACY, LEGACY_GRANT_ID, plan.legacyGrantedMs, at, {}),
  ];
  if (plan.grant) rows.push(row(uid, PREMIUM_LEDGER_CURRENCY,
    plan.outcome === "GRANTED" ? PREMIUM_LEDGER_REASONS.GRANT : PREMIUM_LEDGER_REASONS.REFUND,
    plan.grant.grantId, plan.outcome === "GRANTED" ? plan.premiumGrantedMs : -plan.revokedNowMs, at,
    {source: plan.grant.source, sourceId: plan.grant.sourceId, durationMs: plan.grant.durationMs, consumedMs: plan.grant.consumedMs}));
  for (const movement of plan.chargeMovements) rows.push(row(uid, movement.currency, PREMIUM_LEDGER_REASONS.REGEN,
    `${movement.fromMs}_${at}`, movement.delta, at, {premiumClock: clockId}));
  for (const entry of rows.filter(Boolean)) transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
}

module.exports = {
  PREMIUM_ACCOUNTS_COLLECTION, PREMIUM_LOT_BATCH, PREMIUM_CATCHUP_PASSES, PREMIUM_CATCHUP,
  PREMIUM_LEDGER_CURRENCY, PREMIUM_LEDGER_REASONS, premiumGrantId,
  readPremiumEconomy, planPremiumEconomy, writePremiumEconomy,
};
